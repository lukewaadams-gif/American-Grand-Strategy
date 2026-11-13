// Checks for the Taiwan crisis (docs/ISLAND_CHAIN.md): the Oval Office in crisis days and in war turns, the crisis room
// (both ladders, the Western Pacific, Taiwan by sector, warning, directives, allies, preparations, the economy, nuclear
// risk and the toll), orders from the room (a directive, a request to Taipei, a sanction with its confirmation, handing
// back to the staff), a turn played from the room, and Island Chain on its own from the start screen, as China. Each
// screen at 1600 and 390 wide and in every palette (axe WCAG 2.1 AA, text size, no sideways scroll).
// Usage: node taiwan.js [out-dir]
const { chromium } = require('playwright');
const fs = require('fs');
const axeSrc = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
const OUT = process.argv[2] || 'taiwan-checks';
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
    await page.waitForSelector('.tw', { timeout: 10000 });
    await page.waitForTimeout(200);
  };
  const stamp = async (page) => ((await page.locator('.tw .room-top p.muted').textContent()) || '');
  const settle = async (page) => {
    for (let g = 0; g < 14; g++) {
      await page.waitForTimeout(250);
      if (await page.locator('.tw').count()) return;
      const c = page.locator('.scrim button.choice').first();
      if (await c.count()) { await c.click(); continue; }
      const go = page.locator('.scrim button.primary', { hasText: 'Continue' }).first();
      if (await go.count()) { await go.click(); continue; }
    }
  };

  // 1. the crisis days and the war turns, in the Oval Office and the room, at both widths
  for (const save of ['taiwan', 'taiwan-war']) for (const [w, hgt] of [[1600, 900], [390, 844]]) {
    const page = await open(`&save=saves/${save}.json`, w, hgt);
    const end = (await page.locator('.ov-end').textContent() || '').trim();
    const want = save === 'taiwan' ? /Next day/ : /Next (\d+ hours|hour)/;
    if (!want.test(end)) fail(`${save} ${w}: the turn button reads "${end}"`);
    if (!(await page.locator('.ov-crisis').count())) fail(`${save} ${w}: the crisis card in the Oval Office`);
    await check(page, `${save} ${w}: the Oval Office`);
    await page.screenshot({ path: `${OUT}/${save}-${w}-oval.png` });
    await room(page);
    await check(page, `${save} ${w}: the crisis room`);
    await page.screenshot({ path: `${OUT}/${save}-${w}-room.png`, fullPage: true });
    if (w === 1600) {
      const rungs = await page.locator('.tw-rung').count(), sectors = await page.locator('.tw-sector').count(), chips = await page.locator('.tw-chip').count();
      console.log(save, 'rungs', rungs, 'sectors', sectors, 'chips', chips);
      if (rungs !== 14 || sectors !== 25 || chips < 4) fail(save + ': the ladders and the maps');
      if (save === 'taiwan') {
        // a directive, a request to Taipei, a heavy sanction (two clicks), then back to the staff
        await page.locator('.hz-steps[aria-label^="Fleet posture"] button', { hasText: 'Stand-off' }).click();
        await page.waitForTimeout(150);
        if (!(await page.locator('.hz-steps[aria-label="Fleet posture: Stand-off"] button.on').count())) fail('the fleet stands off');
        const msg = await page.locator('.sit-message').textContent().catch(() => '');
        console.log('fleet:', msg);
        if (!/directive|staff|taken/i.test(await page.locator('#tw-dir + p').textContent())) fail('the directive is held from the staff');
        await page.locator('.tw button', { hasText: 'Ration energy' }).click();
        await page.waitForTimeout(150);
        if (!(await page.locator('.tw button:disabled', { hasText: 'Ration energy' }).count())) fail('Taipei rations');
        const heavy = page.locator('.tw .sit-ops li', { hasText: 'Freeze China’s reserves' });
        await heavy.locator('button', { hasText: 'Impose' }).click();
        await page.waitForTimeout(100);
        await check(page, '1600: a sanction to confirm');
        await heavy.locator('button', { hasText: 'Confirm' }).click();
        await page.waitForTimeout(150);
        if (!(await heavy.textContent()).includes('In force')) fail('the freeze is in force');
        await page.locator('.tw button', { hasText: 'Hand everything back to the staff' }).click();
        await page.waitForTimeout(150);
        console.log('playbook:', await page.locator('.sit-message').textContent().catch(() => ''));
        // a day from the room
        const d0 = await stamp(page);
        await page.locator('.hz-next').click();
        await settle(page);
        const d1 = await stamp(page);
        console.log('turn:', d0, '->', d1);
        if (d0 === d1 || !/day \d+ of the crisis/.test(d1)) fail('Next day plays a day and reopens the room');
        await page.screenshot({ path: `${OUT}/taiwan-1600-room-day2.png`, fullPage: true });
      } else {
        const t0 = await stamp(page);
        await page.locator('.hz-next').click();
        await settle(page);
        const t1 = await stamp(page);
        console.log('war turn:', t0, '->', t1);
        if (t0 === t1) fail('a war turn plays from the room');
      }
    }
    await page.close();
  }
  for (const [label, q] of [['high contrast', '&palette=high-contrast'], ['colour-blind', '&palette=colour-blind'], ['text 125%', '&size=1.25']]) {
    const page = await open('&save=saves/taiwan-war.json' + q, 1440, 900);
    await room(page);
    await check(page, `${label}: the crisis room`);
    if (label === 'text 125%') await page.screenshot({ path: `${OUT}/1440-room-125.png`, fullPage: true });
    await page.close();
  }

  // 2. Island Chain on its own, as China, from the start screen
  {
    const page = await open('', 1600, 900);
    await page.locator('.start-ic button', { hasText: 'China' }).click();
    await page.locator('.start-ic select').selectOption('2032');
    await page.locator('.start-ic button', { hasText: 'Pessimistic' }).click();
    await page.waitForSelector('.tw', { timeout: 30000 }).catch(() => fail('Island Chain from the start screen'));
    const top = await page.locator('.tw .room-top').textContent();
    console.log('standalone:', top.slice(0, 100));
    if (!/China/.test(top) || !/2032/.test(top)) fail('the standalone crisis as China in 2032');
    await check(page, '1600: Island Chain as China');
    // adopt a plan and set D-day, then a day
    await page.locator('.tw .hz-steps[aria-label="The plan"] button', { hasText: 'A blockade' }).click();
    await page.locator('.tw button', { hasText: 'Adopt the plan' }).click();
    await page.waitForTimeout(150);
    console.log('plan:', await page.locator('.sit-message').textContent().catch(() => ''));
    if (!/blockade/i.test(await page.locator('#tw-coa + p').textContent())) fail('the plan is adopted');
    const d0 = await stamp(page);
    await page.locator('.hz-next').click();
    await page.waitForTimeout(400);
    const d1 = await stamp(page);
    console.log('standalone turn:', d0, '->', d1);
    if (d0 === d1) fail('a standalone day');
    await page.screenshot({ path: `${OUT}/ic-prc.png`, fullPage: true });
    await page.locator('.tw button', { hasText: 'Back to the start' }).click();
    await page.waitForSelector('.start', { timeout: 10000 }).catch(() => fail('back to the start'));
    await page.close();
  }
  {
    const page = await open('', 390, 844);
    await page.locator('.start-ic button', { hasText: 'Base case' }).click();
    await page.waitForSelector('.tw', { timeout: 30000 }).catch(() => fail('Island Chain at 390'));
    await check(page, '390: Island Chain as America');
    await page.screenshot({ path: `${OUT}/ic-us-390.png`, fullPage: true });
    await page.close();
  }

  await browser.close();
  console.log('errors', errors.length, errors.slice(0, 5).join('\n'));
  failures += errors.length;
  console.log(failures ? failures + ' FAILURES' : 'ALL PASS');
  process.exit(failures ? 1 : 0);
})();
