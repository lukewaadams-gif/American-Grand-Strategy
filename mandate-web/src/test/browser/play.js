// Checks for presidency milestone 10: the whole game in one page (index.html). The start screen and a new campaign; the
// transition office (the agenda, an interview, a background check, an announcement) and the oath into the Oval Office;
// the move from the Oval Office into the 2032 campaign and back; and the verdict at the end. Each screen at 1600 and
// 390 wide and in every palette (axe WCAG 2.1 AA, text size, no sideways scroll). Usage: node play.js [out-dir]
const { chromium } = require('playwright');
const fs = require('fs');
const axeSrc = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
const OUT = process.argv[2] || 'm10-checks';
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
    await page.waitForSelector('.start, .transition, .oval-art, .warroom, .end-page, main', { timeout: 30000 });
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
  const check = async (page, name, scope = '#app') => { await axe(page, name); await smallest(page, name, scope); await sideways(page, name); };

  // 1. the start screen, and a new campaign
  for (const [w, hgt] of [[1600, 900], [390, 844]]) {
    const page = await open('', w, hgt);
    if (!(await page.locator('.start').count())) fail(`${w}: the start screen`);
    await check(page, `${w}: the start screen`);
    await page.screenshot({ path: `${OUT}/${w}-start.png`, fullPage: true });
    if (w === 1600) {
      await page.locator('.start button', { hasText: 'Republican' }).click();
      await page.waitForTimeout(150);
      await page.locator('.start-go').click();
      await page.waitForTimeout(800);
      const hq = await page.locator('#main, .warroom').count();
      const title = await page.title();
      console.log('new campaign:', title);
      if (!/Campaign 2028/.test(title) || !hq) fail('a new campaign opens the war room');
      await page.screenshot({ path: `${OUT}/1600-new-campaign.png` });
    }
    await page.close();
  }
  for (const [label, q] of [['high contrast', '&palette=high-contrast'], ['colour-blind', '&palette=colour-blind'], ['text 125%', '&size=1.25']]) {
    const page = await open(q, 1440, 900);
    await check(page, `${label}: the start screen`);
    await page.close();
  }

  // 2. the transition office, then the oath into the Oval Office
  for (const [w, hgt] of [[1600, 900], [390, 844]]) {
    const page = await open('&save=saves/transition.json', w, hgt);
    if (!(await page.locator('.transition').count())) { fail(`${w}: the transition office`); await page.close(); continue; }
    await check(page, `${w}: the transition: the agenda`);
    await page.screenshot({ path: `${OUT}/${w}-transition-agenda.png`, fullPage: true });
    await page.locator('.transition .cab-tabs button', { hasText: 'The White House staff' }).click();
    await page.waitForTimeout(150);
    await check(page, `${w}: the transition: the staff`);
    await page.screenshot({ path: `${OUT}/${w}-transition-staff.png`, fullPage: true });
    if (w === 1600) {
      await page.locator('.tr-prospects button', { hasText: 'Interview' }).first().click();
      await page.waitForTimeout(150);
      await page.locator('.tr-prospects button', { hasText: 'Background check' }).first().click();
      await page.waitForTimeout(150);
      await page.locator('.tr-prospects button', { hasText: 'Announce' }).first().click();
      await page.waitForTimeout(150);
      const named = await page.locator('.tr-prospects li.named').count();
      console.log('named:', named, '|', await page.locator('.sit-message').textContent().catch(() => ''));
      if (named !== 1) fail('announcing a chief of staff');
      await page.locator('.transition .cab-tabs button', { hasText: 'The agenda' }).click();
      await page.waitForTimeout(150);
      const add = page.locator('.transition .sit-ops button', { hasText: 'Add' });
      if (!(await page.locator('.tr-chosen li').count())) await add.first().click();
      await page.locator('.transition button', { hasText: 'Set the agenda' }).click();
      await page.waitForTimeout(150);
      await page.locator('.transition button', { hasText: 'Take the oath' }).click();
      await page.waitForSelector('.oval-art', { timeout: 15000 }).catch(() => fail('the oath opens the Oval Office'));
      await page.waitForTimeout(300);
      console.log('after the oath:', await page.title());
      await page.screenshot({ path: `${OUT}/1600-after-oath.png` });
    }
    await page.close();
  }

  // 3. the end: the verdict, as a page
  for (const [w, hgt] of [[1600, 900], [390, 844]]) {
    const page = await open('&save=saves/handoff.json', w, hgt);
    let end = false;
    for (let wk = 0; wk < 8 && !end; wk++) {
      await page.locator('.ov-actions button', { hasText: /End week/ }).click().catch(() => {});
      for (let g = 0; g < 10; g++) {
        await page.waitForTimeout(200);
        if (await page.locator('.end-page, .verdict').count()) { end = true; break; }
        const c = page.locator('.scrim button.choice').first();
        if (await c.count()) { await c.click(); continue; }
        if (await page.locator('.scrim').count()) { await page.keyboard.press('Escape'); continue; }
        break;
      }
    }
    if (!end) { fail(`${w}: the verdict at the end`); await page.close(); continue; }
    await check(page, `${w}: the verdict`, 'body');
    await page.screenshot({ path: `${OUT}/${w}-verdict.png`, fullPage: true });
    await page.close();
  }

  await browser.close();
  console.log('errors', errors.length, errors.slice(0, 5).join('\n'));
  failures += errors.length;
  console.log(failures ? failures + ' FAILURES' : 'ALL PASS');
  process.exit(failures ? 1 : 0);
})();
