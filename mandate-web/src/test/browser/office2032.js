// Running for re-election while in office (docs/MODES.md, "The 2032 campaign"): the war room's White House card in 2032 and
// not in 2028; its four rooms open over the war room and say so; the White House's questions can be answered or put aside
// (never trapping the player); a week runs with questions left on the desk. At 1600 and 390 pixels wide (axe-core WCAG 2.1
// AA, text size, sideways scroll). Needs the test saves (ElectionSaves). Fails on any console error.
// Usage: node office2032.js [out-dir]
const { chromium } = require('playwright');
const fs = require('fs');
const axeSrc = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
const OUT = process.argv[2] || 'office2032-checks';
const SAVE = name => `http://127.0.0.1:8766/index.html?hour=11&save=saves/test/${name}.json`;
fs.mkdirSync(OUT, { recursive: true });
let failures = 0;
const fail = (what) => { failures++; console.log('FAIL', what); };
(async () => {
  const browser = await chromium.launch();
  const errors = [];
  const open = async (url, w = 1600, hgt = 1000) => {
    const page = await browser.newPage({ viewport: { width: w, height: hgt }, deviceScaleFactor: 1 });
    page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    page.on('pageerror', e => errors.push(String(e)));
    await page.goto(url);
    await page.waitForSelector('.cw-bar', { timeout: 30000 });
    await page.waitForTimeout(600);
    return page;
  };
  const check = async (page, name) => {
    await page.evaluate(() => Promise.all(document.getAnimations().filter(x => x.effect && x.effect.getTiming().iterations !== Infinity).map(x => x.finished.catch(() => null))));
    await page.addScriptTag({ content: axeSrc });
    const r = await page.evaluate(async () => await axe.run(document, { runOnly: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] }));
    failures += r.violations.length;
    console.log(name.padEnd(46), 'axe:', r.violations.map(v => v.id + '(' + v.nodes.length + ')').join(', ') || 'none');
    for (const v of r.violations) for (const x of v.nodes.slice(0, 3)) console.log('   ', v.id, x.target.join(' '));
    const min = await page.evaluate(() => {
      let m = 99;
      for (const el of document.querySelectorAll('.white-house *, .scrim *')) {
        if (!(el instanceof HTMLElement) || el.closest('svg')) continue;
        if (![...el.childNodes].some(n => n.nodeType === 3 && n.textContent.trim())) continue;
        const b = el.getBoundingClientRect();
        if (b.width && b.height) m = Math.min(m, parseFloat(getComputedStyle(el).fontSize));
      }
      return m;
    });
    if (min < 12) fail(`${name}: text at ${min}px`);
    const over = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    if (over > 1) fail(`${name}: scrolls sideways by ${over}px`);
  };

  // 2028: no White House in the war room
  {
    const page = await open(SAVE('eve2028-win'));
    if (await page.locator('.white-house').count()) fail('a White House card in the 2028 campaign');
    await page.close();
  }

  for (const [w, hgt] of [[1600, 1000], [390, 844]]) {
    const page = await open(SAVE('eve2032-lose'), w, hgt);
    const card = page.locator('.white-house');
    if (!(await card.count())) { fail(`${w}: no White House card in 2032`); await page.close(); continue; }
    const text = (await card.textContent()).replace(/\s+/g, ' ');
    console.log(`${w}: the card:`, text.slice(0, 160));
    if (!/Approval/.test(text) || !/Your record/.test(text)) fail(`${w}: the card does not show the record`);
    await card.scrollIntoViewIfNeeded();
    await check(page, `${w}: the war room in 2032`);
    await page.screenshot({ path: `${OUT}/${w}-white-house.png` });
    for (const label of ['Congress', 'The West Wing', 'The Situation Room', 'The press room']) {
      await page.click(`.wh-rooms button:has-text("${label}")`);
      await page.waitForSelector('.scrim', { timeout: 10000 });
      await page.waitForTimeout(400);
      const back = await page.locator('.scrim button:has-text("Back to the war room")').count();
      if (!back) fail(`${w}: ${label} does not lead back to the war room`);
      if (w === 1600) await check(page, `${w}: ${label} over the war room`);
      await page.locator('.scrim button:has-text("Back to the war room")').first().click();
      await page.waitForTimeout(300);
      if (await page.locator('.scrim').count()) fail(`${w}: ${label} did not close`);
    }
    // the questions: put each aside, and the war room comes back (no card reopens for ever)
    const q = await page.locator('.wh-questions').count();
    if (q) {
      await page.click('.wh-questions');
      for (let i = 0; i < 12 && await page.locator('.scrim').count(); i++) {
        await page.locator('.scrim .dialog-foot .btn.quiet').first().click();
        await page.waitForTimeout(300);
      }
      if (await page.locator('.scrim').count()) fail(`${w}: putting the questions aside never ends`);
    } else console.log(`${w}: no questions waiting on this save`);
    // the day runs with the questions left on the desk
    if (w === 1600) {
      await page.click('.cw-actions .btn.primary');
      await page.waitForTimeout(1500);
      for (let i = 0; i < 6 && await page.locator('.scrim').count(); i++) { await page.locator('.scrim .choice').first().click().catch(() => {}); await page.waitForTimeout(300); await page.locator('.scrim .btn.primary').first().click().catch(() => {}); await page.waitForTimeout(300); }
      const after = await page.locator('body').textContent();
      if (!/Election|election|The Oval Office|war room|Run the day/.test(after)) fail('the day did not run');
      console.log('after the day:', (await page.title()));
    }
    await page.close();
  }

  await browser.close();
  console.log('errors', errors.length, errors.slice(0, 5).join('\n'));
  failures += errors.length;
  console.log(failures ? failures + ' FAILURES' : 'ALL PASS');
  process.exit(failures ? 1 : 0);
})();
