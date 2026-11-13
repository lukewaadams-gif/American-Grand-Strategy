// Checks for presidency milestone 4's screens: the briefing book's new sections, the West Wing and the Cabinet Room,
// a policy's and a bill's score, and the agenda's scores. axe (WCAG 2.1 AA), minimum text size, no sideways scroll at
// 1600, 1280 and 390 wide, no console errors, screenshots. Usage: node economy.js [out-dir] [save]
const { chromium } = require('playwright');
const fs = require('fs');
const axeSrc = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
const OUT = process.argv[2] || 'm4-checks';
const SAVE = process.argv[3] || 'saves/governing.json';
const BASE = 'http://127.0.0.1:8766/presidency.html?hour=11&save=' + SAVE;
fs.mkdirSync(OUT, { recursive: true });
let failures = 0;
const fail = (what) => { failures++; console.log('FAIL', what); };
(async () => {
  const browser = await chromium.launch();
  const errors = [];
  const open = async (w = 1600, hgt = 900) => {
    const page = await browser.newPage({ viewport: { width: w, height: hgt }, deviceScaleFactor: 1 });
    page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    page.on('pageerror', e => errors.push(String(e)));
    await page.goto(BASE);
    await page.waitForSelector('.oval-art', { timeout: 30000 });
    await page.waitForTimeout(300);
    return page;
  };
  const axe = async (page, name) => {
    await page.addScriptTag({ content: axeSrc });
    const r = await page.evaluate(async () => await axe.run(document, { runOnly: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] }));
    failures += r.violations.length;
    console.log(name.padEnd(34), 'axe:', r.violations.map(v => v.id + '(' + v.nodes.length + ')').join(', ') || 'none');
    for (const v of r.violations) for (const x of v.nodes.slice(0, 3)) console.log('   ', v.id, x.target.join(' '), (x.failureSummary || '').split('\n').slice(1, 2).join(''));
  };
  const smallest = async (page, name) => {
    const r = await page.evaluate(() => {
      let min = 99, where = '';
      for (const el of document.querySelectorAll('.scrim *')) {
        if (!(el instanceof HTMLElement) || el.closest('svg')) continue;
        const own = [...el.childNodes].some(n => n.nodeType === 3 && n.textContent.trim());
        if (!own) continue;
        const b = el.getBoundingClientRect();
        if (!b.width || !b.height || el.closest('.sr')) continue;
        const f = parseFloat(getComputedStyle(el).fontSize);
        if (f < min) { min = f; where = el.className + ' "' + el.textContent.trim().slice(0, 30) + '"'; }
      }
      return { min, where };
    });
    if (r.min < 12) failures++;
    console.log(name.padEnd(34), 'smallest text', r.min + 'px', r.min < 12 ? 'TOO SMALL ' + r.where : '');
  };
  const sideways = async (page, name) => {
    const over = await page.evaluate(() => {
      const s = document.querySelector('.scrim .dialog') || document.querySelector('.scrim > *');
      const page = document.documentElement.scrollWidth - window.innerWidth;
      const inner = s ? s.scrollWidth - s.clientWidth : 0;
      return Math.max(page, inner);
    });
    if (over > 1) fail(`${name}: scrolls sideways by ${over}px`);
  };
  const check = async (page, name) => { await axe(page, name); await smallest(page, name); await sideways(page, name); };

  for (const [w, hgt] of [[1600, 900], [1280, 800], [390, 844]]) {
    const page = await open(w, hgt);
    const tag = `${w}`;
    // the briefing book
    await page.click('.ov-spot-book');
    await page.waitForSelector('.room.econ');
    for (const t of ['Prices, part by part', 'The industries', 'The budget', 'What your laws have done'])
      if (!(await page.locator('.room.econ h3', { hasText: t }).count())) fail(`${tag}: the briefing book has no "${t}"`);
    if ((await page.locator('.econ-part').count()) !== 6) fail(`${tag}: six price parts expected`);
    await check(page, `${tag} briefing book`);
    await page.screenshot({ path: `${OUT}/book-${tag}.png`, fullPage: false });
    const laws = await page.locator('.econ-laws tbody tr').count();
    console.log(`${tag} laws shown`, laws);
    if (laws) {
      await page.locator('.room.econ h3', { hasText: 'What your laws have done' }).scrollIntoViewIfNeeded();
      await page.screenshot({ path: `${OUT}/book-laws-${tag}.png`, fullPage: false });
    }
    await page.keyboard.press('Escape');
    await page.waitForTimeout(150);
    // the West Wing and the Cabinet Room
    await page.click('.ov-spot-westwing');
    await page.waitForSelector('.ww-doors');
    await check(page, `${tag} West Wing`);
    await page.click('text=The Cabinet Room: the policy table');
    await page.waitForSelector('.room.cab');
    await check(page, `${tag} Cabinet Room`);
    await page.screenshot({ path: `${OUT}/cabinet-${tag}.png`, fullPage: false });
    await page.click('.cab-tabs button:has-text("Jobs and work")');
    await page.click('.cab-row:has-text("Federal minimum wage")');
    const t0 = Date.now();
    await page.click('.cab-buttons button:has-text("Score a step up")');
    await page.waitForSelector('.cab-detail .score');
    console.log(`${tag} a policy score took`, Date.now() - t0, 'ms');
    const reading = await page.locator('.cab-detail .score-reading').textContent();
    console.log(`${tag} reading:`, reading);
    if (!/jobs/.test(reading)) fail(`${tag}: the minimum wage's reading does not mention jobs`);
    await check(page, `${tag} Cabinet Room, a score`);
    await page.locator('.cab-detail .score').scrollIntoViewIfNeeded();
    await page.screenshot({ path: `${OUT}/cabinet-score-${tag}.png`, fullPage: false });
    await page.click('.cab-tabs button:has-text("This game’s bills")');
    const scoreButtons = page.locator('.cab-bill button:has-text("The budget office’s score")');
    if (await scoreButtons.count()) {
      await scoreButtons.first().click();
      await page.waitForSelector('.cab-bill .score');
      await check(page, `${tag} Cabinet Room, a bill`);
      await page.screenshot({ path: `${OUT}/cabinet-bill-${tag}.png`, fullPage: false });
    } else fail(`${tag}: no bill to score`);
    await page.click('.cab-tabs button:has-text("So far")');
    await check(page, `${tag} Cabinet Room, so far`);
    await page.keyboard.press('Escape');
    await page.waitForTimeout(150);
    // the agenda's scores
    await page.click('.ov-spot-folders');
    await page.waitForSelector('.ov-agenda, .dialog-card');
    const disclosures = page.locator('.ov-score-wrap summary');
    if (await disclosures.count()) {
      await disclosures.first().click();
      await page.waitForSelector('.ov-score .score');
      await check(page, `${tag} agenda, a score`);
      await page.screenshot({ path: `${OUT}/agenda-${tag}.png`, fullPage: false });
    } else console.log(`${tag} agenda: no bill to score (all law or none)`);
    await page.keyboard.press('Escape');
    // a few weeks on: the economy's news
    if (w === 1600) {
      for (let i = 0; i < 6; i++) {
        await page.keyboard.press('Control+Enter');
        await page.waitForTimeout(400);
        for (let k = 0; k < 6 && await page.locator('.scrim').count(); k++) { await page.keyboard.press('Escape'); await page.waitForTimeout(150); }
      }
      await page.click('.ov-spot-paper');
      await page.waitForSelector('.lg');
      await page.screenshot({ path: `${OUT}/ledger.png`, fullPage: false });
      const kickers = await page.locator('.lg-kicker').allTextContents();
      console.log('Ledger kickers', kickers.join(' | '));
    }
    await page.close();
  }
  if (errors.length) { failures += errors.length; console.log('console errors:', errors.slice(0, 5)); }
  console.log(failures ? `${failures} FAILURES` : 'ALL PASS');
  await browser.close();
  process.exit(failures ? 1 : 0);
})();
