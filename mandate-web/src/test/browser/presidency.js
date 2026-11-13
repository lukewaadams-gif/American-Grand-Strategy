// Checks for the presidency preview (target/preview/presidency.html): the Oval Office. Uses every object and door in the
// room by mouse and by keyboard, opens their dialogs, switches the light from the menu, plays turns (a week in the first
// hundred days, a month while governing) and answers any decisions they raise. Audits each view with axe-core
// (WCAG 2.1 AA) and a minimum text size, in three palettes, with reduced motion and 125% text, and at 1280, 1000 and
// 390 pixels wide. Fails on any console error. Usage: node presidency.js [out-dir] [base-url]
const { chromium } = require('playwright');
const fs = require('fs');
const axeSrc = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
const OUT = process.argv[2] || 'presidency-checks';
const BASE = process.argv[3] || 'http://127.0.0.1:8766/presidency.html';
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
    await page.goto(BASE + q);
    await page.waitForSelector('.oval-art', { timeout: 30000 });
    await page.waitForTimeout(300);
    return page;
  };
  const axe = async (page, name) => {
    await page.addScriptTag({ content: axeSrc });
    const r = await page.evaluate(async () => await axe.run(document, { runOnly: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] }));
    failures += r.violations.length;
    console.log(name.padEnd(30), 'axe:', r.violations.map(v => v.id + '(' + v.nodes.length + ')').join(', ') || 'none');
    for (const v of r.violations) for (const x of v.nodes.slice(0, 3)) console.log('   ', v.id, x.target.join(' '), (x.failureSummary || '').split('\n').slice(1, 2).join(''));
  };
  const smallest = async (page, name) => {
    const r = await page.evaluate(() => {
      let min = 99, where = '';
      for (const el of document.querySelectorAll('body *')) {
        if (!(el instanceof HTMLElement) || el.closest('svg')) continue;
        const own = [...el.childNodes].some(n => n.nodeType === 3 && n.textContent.trim());
        if (!own) continue;
        const r = el.getBoundingClientRect();
        if (!r.width || !r.height || el.closest('.sr')) continue;
        const fs = parseFloat(getComputedStyle(el).fontSize);
        if (fs < min) { min = fs; where = el.className + ' "' + el.textContent.trim().slice(0, 30) + '"'; }
      }
      return { min, where };
    });
    if (r.min < 12) failures++;
    console.log(name.padEnd(30), 'smallest text', r.min + 'px', r.min < 12 ? 'TOO SMALL ' + r.where : '');
  };
  const sideways = async (page, name) => {
    const over = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    if (over > 1) fail(`${name}: the page scrolls sideways by ${over}px`);
  };
  const closeDialog = async (page) => {
    await page.keyboard.press('Escape');
    await page.waitForTimeout(100);
    if (await page.locator('.scrim').count()) fail('Escape did not close the dialog');
  };

  // 1. the room: every object and door, by mouse; each dialog audited once
  let page = await open('?hour=15');
  await page.screenshot({ path: `${OUT}/oval-week1.png` });
  await axe(page, 'oval office (week 1)');
  await smallest(page, 'oval office (week 1)');
  const before = await page.evaluate(() => document.querySelector('.oval-art').innerHTML);
  await page.waitForTimeout(300);
  if (before === await page.evaluate(() => document.querySelector('.oval-art').innerHTML)) fail('the flags and the globe do not move');
  const spots = await page.locator('.ov-spot').evaluateAll(bs => bs.map(b => b.getAttribute('aria-label')));
  console.log('spots:', spots.length, spots.join(' | '));
  if (spots.length !== 12) fail('expected 12 things to use in the room');
  for (const [i, label] of spots.entries()) {
    const b = page.locator('.ov-spot').nth(i);
    await b.hover();
    const tip = await page.locator('.ov-tip.show').textContent();
    if (!label.startsWith(tip)) fail(`hover label "${tip}" for "${label}"`);
    await b.click();
    await page.waitForSelector('.scrim .dialog');
    const title = await page.locator('.dialog .dialog-title').first().textContent();
    console.log('  ', label.padEnd(40), '->', title);
    if (i < 6) await axe(page, '  dialog: ' + title.slice(0, 22));
    await closeDialog(page);
    const back = await page.evaluate(() => document.activeElement?.getAttribute('aria-label'));
    if (back !== label) fail(`focus went back to "${back}", not "${label}"`);
  }
  // the cards' buttons and the top bar's
  await page.click('.ov-hill .ov-card-btn'); await page.waitForSelector('.scrim'); await closeDialog(page);
  await page.click('.ov-week .ov-card-btn'); await page.waitForSelector('.scrim'); await closeDialog(page);
  await page.click('.ov-actions button:has-text("Watch")'); await page.waitForSelector('.scrim'); await closeDialog(page);

  // 2. by keyboard: Tab from the top bar into the room, open the documents tray with Enter
  await page.locator('.ov-actions .btn.primary').focus();
  await page.keyboard.press('Tab');
  const first = await page.evaluate(() => document.activeElement?.getAttribute('aria-label'));
  console.log('Tab after End week reaches:', first);
  if (first !== 'Documents to sign') fail('Tab order: the room should come after the top bar');
  if (!(await page.locator('.ov-tip.show').count())) fail('focus does not show the label');
  await page.keyboard.press('Enter');
  await page.waitForSelector('.scrim');
  await closeDialog(page);

  // 3. the menu: the light at night, then back to the clock
  await page.click('.ov-actions button:has-text("Menu")');
  await page.waitForSelector('.menu');
  await axe(page, 'menu');
  await page.locator('.menu-item', { hasText: 'Light: night' }).click();
  await page.waitForTimeout(300);
  await page.screenshot({ path: `${OUT}/oval-night.png` });
  console.log('night toast:', await page.locator('.toast').textContent());

  // 4. The Ledger pauses
  await page.click('.ov-ledger-pause');
  const paused = await page.evaluate(() => getComputedStyle(document.querySelector('.ov-ledger-belt')).animationPlayState);
  if (paused !== 'paused') fail('The Ledger did not pause');

  // 5. turns: three weeks of the first hundred days (answering decisions), with Ctrl+Enter for one
  const playTurn = async (key) => {
    const before = await page.locator('.ov-date').textContent();
    if (key) await page.keyboard.press('Control+Enter'); else await page.click('.ov-end');
    for (let i = 0; i < 8; i++) {
      await page.waitForTimeout(250);
      if (!(await page.locator('.scrim').count())) break;
      await page.locator('.dialog button.choice, .dialog .btn.primary').first().click();
    }
    await page.waitForTimeout(300);
    const after = await page.locator('.ov-date').textContent();
    console.log('turn:', before, '->', after);
    if (before === after) fail('the turn did not move the date');
  };
  await playTurn(false); await playTurn(true); await playTurn(false);
  await axe(page, 'after three weeks');
  await page.screenshot({ path: `${OUT}/oval-week4.png` });
  await page.close();

  // 6. governing: a month, at dusk
  page = await open('?save=saves/governing.json&hour=19');
  await axe(page, 'governing (dusk)');
  await smallest(page, 'governing (dusk)');
  await playTurn(false);
  await page.screenshot({ path: `${OUT}/oval-governing.png` });
  await page.close();

  // 7. palettes, motion and text size
  for (const [q, name] of [['?palette=high-contrast', 'high contrast'], ['?palette=colour-blind', 'colour-blind'], ['?motion=reduce', 'reduced motion'], ['?size=1.25', 'text 125%']]) {
    page = await open(q + '&hour=11');
    await axe(page, name); await smallest(page, name); await sideways(page, name);
    if (name === 'reduced motion') {
      const art = () => page.evaluate(() => document.querySelector('.oval-art').innerHTML);
      const a = await art();
      const anim = await page.evaluate(() => getComputedStyle(document.querySelector('.ov-ledger-belt')).animationName);
      await page.waitForTimeout(400);
      if (a !== await art()) fail('the room moves under reduced motion');
      if (anim !== 'none') fail('The Ledger runs under reduced motion');
    }
    await page.screenshot({ path: `${OUT}/oval-${name.replace(/\W+/g, '-')}.png` });
    await page.close();
  }

  // 8. narrower windows
  for (const [w, hgt] of [[1280, 720], [1000, 700], [390, 844]]) {
    page = await open('?hour=11', w, hgt);
    await axe(page, `${w} wide`); await smallest(page, `${w} wide`); await sideways(page, `${w} wide`);
    const tooSmall = await page.locator('.ov-spot').evaluateAll(bs => bs.filter(b => { const r = b.getBoundingClientRect(); return r.width < 24 || r.height < 24; }).length);
    if (w >= 1000 && tooSmall) fail(`${tooSmall} things in the room are smaller than 24px at ${w} wide`);
    await page.screenshot({ path: `${OUT}/oval-${w}.png`, fullPage: true });
    await page.close();
  }

  if (errors.length) { failures += errors.length; console.log('console errors:', errors.slice(0, 5)); }
  console.log(failures ? `${failures} FAILURES` : 'all presidency checks passed');
  await browser.close();
  process.exit(failures ? 1 : 0);
})();
