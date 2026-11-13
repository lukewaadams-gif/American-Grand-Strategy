// Checks for the Strait of Hormuz crisis (docs/HORMUZ.md): the Oval Office in daily turns, the crisis room (the
// ladder, the map, the oil, Iran and the tools), orders from the room (escorts, an envoy, the reserve, a strike with its
// confirmation), a day played from the room, and the crisis started from the start screen. Each screen at 1600 and 390
// wide and in every palette (axe WCAG 2.1 AA, text size, no sideways scroll). Usage: node hormuz.js [out-dir]
const { chromium } = require('playwright');
const fs = require('fs');
const axeSrc = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
const OUT = process.argv[2] || 'hormuz-checks';
const URL = (q = '') => 'http://127.0.0.1:8766/index.html?hour=11' + q;
fs.mkdirSync(OUT, { recursive: true });
let failures = 0;
const fail = (what) => { failures++; console.log('FAIL', what); };
(async () => {
  const browser = await chromium.launch();
  const errors = [];
  const open = async (q = '', w = 1600, hgt = 900) => {
    const page = await browser.newPage({ viewport: { width: w, height: hgt }, deviceScaleFactor: 1 });
    page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    page.on('pageerror', e => errors.push(String(e)));
    await page.goto(URL(q));
    await page.waitForSelector('.start, .oval-art, .ov, main', { timeout: 30000 });
    await page.waitForTimeout(400);
    return page;
  };
  const axe = async (page, name) => {
    await page.addScriptTag({ content: axeSrc });
    const r = await page.evaluate(async () => await axe.run(document, { runOnly: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] }));
    failures += r.violations.length;
    console.log(name.padEnd(44), 'axe:', r.violations.map(v => v.id + '(' + v.nodes.length + ')').join(', ') || 'none');
    for (const v of r.violations) for (const x of v.nodes.slice(0, 3)) console.log('   ', v.id, x.target.join(' '), (x.failureSummary || '').split('\n').slice(1, 2).join(''));
  };
  const smallest = async (page, name, scope) => {
    const r = await page.evaluate((scope) => {
      let min = 99, where = '';
      for (const el of document.querySelectorAll(scope + ' *')) {
        if (!(el instanceof HTMLElement) || el.closest('svg')) continue;
        const own = [...el.childNodes].some(n => n.nodeType === 3 && n.textContent.trim());
        if (!own) continue;
        const b = el.getBoundingClientRect();
        if (!b.width || !b.height || el.closest('.sr')) continue;
        const f = parseFloat(getComputedStyle(el).fontSize);
        if (f < min) { min = f; where = el.className + ' "' + el.textContent.trim().slice(0, 30) + '"'; }
      }
      return { min, where };
    }, scope);
    if (r.min < 12) failures++;
    console.log(name.padEnd(44), 'smallest text', r.min + 'px', r.min < 12 ? 'TOO SMALL ' + r.where : '');
  };
  const sideways = async (page, name) => {
    const over = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    if (over > 1) fail(`${name}: scrolls sideways by ${over}px`);
  };
  const check = async (page, name, scope = 'body') => { await axe(page, name); await smallest(page, name, scope); await sideways(page, name); };
  const room = async (page) => {
    await page.locator('.ov-crisis button', { hasText: 'To the crisis room' }).click();
    await page.waitForSelector('.hz', { timeout: 10000 });
    await page.waitForTimeout(200);
  };
  const dayOf = async (page) => Number(((await page.locator('.hz .room-top p.muted').textContent()) || '').match(/day (\d+)/)?.[1] ?? -1);

  // 1. the Oval Office in the crisis, and the crisis room, at both widths
  for (const [w, hgt] of [[1600, 900], [390, 844]]) {
    const page = await open('&save=saves/hormuz.json', w, hgt);
    const end = (await page.locator('.ov-end').textContent() || '').trim();
    if (!/Next day/.test(end)) fail(`${w}: the turn button reads "${end}", not Next day`);
    if (!(await page.locator('.ov-crisis').count())) fail(`${w}: the crisis card in the Oval Office`);
    await check(page, `${w}: the Oval Office in the crisis`);
    await page.screenshot({ path: `${OUT}/${w}-oval.png` });
    await room(page);
    await check(page, `${w}: the crisis room`);
    await page.screenshot({ path: `${OUT}/${w}-room.png`, fullPage: true });
    if (w === 1600) {
      const rungs = await page.locator('.hz-rung').count();
      const sites = await page.locator('.hz-site').count();
      const fields = await page.locator('.hz-field').count();
      console.log('rungs', rungs, 'sites', sites, 'fields', fields);
      if (rungs !== 6 || sites !== 9 || fields !== 4) fail('the ladder and the map');
      // orders from the room
      await page.locator('.hz-steps[aria-label^="Escort groups"] button', { hasText: '3' }).click();
      await page.waitForTimeout(150);
      if (!(await page.locator('.hz-steps[aria-label="Escort groups: 3"] button.on').count())) fail('three escort groups');
      await page.locator('.hz button', { hasText: 'Envoy through Muscat' }).click();
      await page.waitForTimeout(150);
      const msg = await page.locator('.sit-message').textContent().catch(() => '');
      console.log('envoy:', msg);
      if (!/Muscat/.test(msg || '')) fail('the envoy goes through Muscat');
      if (!(await page.locator('.hz button:disabled', { hasText: /Envoy through Muscat \(in \d/ }).count())) fail('the envoy is away for days');
      await page.locator('.hz-steps[aria-label^="Reserve"] button', { hasText: '2' }).click();
      await page.waitForTimeout(150);
      // a strike takes two clicks
      const strike = page.locator('.hz-tools .sit-ops li', { hasText: 'Anti-ship missile batteries' });
      await strike.locator('button', { hasText: 'Strike' }).click();
      await page.waitForTimeout(100);
      await check(page, '1600: a strike to confirm');
      await strike.locator('button', { hasText: 'Confirm' }).click();
      await page.waitForTimeout(150);
      const after = await page.locator('.sit-message').textContent().catch(() => '');
      console.log('strike:', after);
      if (!/strikes hit/i.test(after || '')) fail('the strike is flown');
      // a day from the room
      const d0 = await dayOf(page);
      await page.locator('.hz-next').click();
      for (let g = 0; g < 12; g++) {
        await page.waitForTimeout(250);
        if (await page.locator('.hz').count()) break;
        const c = page.locator('.scrim button.choice').first();
        if (await c.count()) { await c.click(); continue; }
        const go = page.locator('.scrim button.primary', { hasText: 'Continue' }).first();
        if (await go.count()) { await go.click(); continue; }
      }
      const d1 = await dayOf(page);
      console.log('day', d0, '->', d1);
      if (d1 !== d0 + 1) fail('Next day plays a day and reopens the room');
      await page.screenshot({ path: `${OUT}/1600-room-day2.png`, fullPage: true });
    }
    await page.close();
  }
  for (const [label, q] of [['high contrast', '&palette=high-contrast'], ['colour-blind', '&palette=colour-blind'], ['text 125%', '&size=1.25']]) {
    const page = await open('&save=saves/hormuz.json' + q, 1440, 900);
    await room(page);
    await check(page, `${label}: the crisis room`);
    if (label === 'text 125%') await page.screenshot({ path: `${OUT}/1440-room-125.png`, fullPage: true });
    await page.close();
  }

  // 2. the crisis from the start screen
  {
    const page = await open('', 1600, 900);
    // Individual Crisis: the strait, pessimistic
    await page.click('[data-action="new"]');
    await page.click('.mode-card[data-mode="CRISIS"]');
    await page.click('.setup-next');
    await page.click('.crisis-card[data-crisis="hormuz"]');
    await page.click('.setup-next');
    const presets = page.locator('.preset-card');
    if ((await presets.count()) !== 3) fail('three ways to start the crisis');
    await presets.filter({ hasText: 'Pessimistic' }).click();
    await page.click('.setup-next');
    await page.locator('.setup-begin', { hasText: 'Begin the crisis' }).click();
    await page.waitForSelector('.ov-crisis', { timeout: 30000 }).catch(() => fail('the crisis from the start screen'));
    const end = (await page.locator('.ov-end').textContent().catch(() => '') || '').trim();
    console.log('from the start screen:', end, '|', (await page.locator('.ov-crisis').textContent().catch(() => '')).slice(0, 90));
    if (!/Next day/.test(end)) fail('the crisis from the start screen runs in days');
    await page.close();
  }

  await browser.close();
  console.log('errors', errors.length, errors.slice(0, 5).join('\n'));
  failures += errors.length;
  console.log(failures ? failures + ' FAILURES' : 'ALL PASS');
  process.exit(failures ? 1 : 0);
})();
