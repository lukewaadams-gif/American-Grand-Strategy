// Checks for the presidency preview (target/preview/presidency.html): the Oval Office and Resolute Desk's week. Uses
// every object and door in the room by mouse and by keyboard and opens their dialogs; plans the week on the calendar
// (menus, issues, states from the list and from the map, the ready-made weeks, the chief of staff); plays weeks with
// The Capital Ledger after each; runs watch mode and takes the controls back; hands the hundred days to the staff;
// switches the light from the menu; and answers the decisions that come up. Audits each view with axe-core
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
    const title = await page.locator('.dialog .dialog-title, .dialog .lg-title').first().textContent();
    console.log('  ', label.padEnd(40), '->', title);
    if (i < 7) await axe(page, '  dialog: ' + title.slice(0, 22));
    if (label.startsWith('The windows') || label.startsWith('The briefing book')) await smallest(page, '  room: ' + title.slice(0, 22));
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

  // 5. the calendar: a menu by keyboard, an issue, a state from the list and from the map, a ready-made week, the staff
  await page.click('.ov-spot-desk');
  await page.waitForSelector('.cal');
  await axe(page, 'the calendar');
  await smallest(page, 'the calendar');
  if ((await page.locator('.cal-slot').count()) !== 21) fail('the calendar has 21 slots');
  const slot = (i) => page.locator('.cal-slot').nth(Math.floor(i / 3) + (i % 3) * 7);        // the grid runs by part, then day
  await slot(8).focus(); await page.keyboard.press('Enter');                                   // Wednesday evening
  await page.waitForSelector('.menu');
  await page.locator('.menu-item', { hasText: 'Rest' }).first().click();
  await page.waitForTimeout(150);
  if (!(await slot(8).getAttribute('aria-label')).includes('Rest')) fail('Wednesday evening is now rest: ' + await slot(8).getAttribute('aria-label'));
  await slot(0).click();                                                                       // Monday morning: remarks on an issue
  await page.locator('.menu-item', { hasText: 'White House remarks' }).click();
  await page.waitForSelector('.menu-title:has-text("which issue")');
  await page.locator('.menu-item').nth(1).click();
  await page.waitForTimeout(150);
  console.log('Monday morning:', await slot(0).getAttribute('aria-label'));
  await slot(1).click();                                                                       // Monday afternoon: a trip, from the list
  await page.locator('.menu-item', { hasText: /^Trip/ }).first().click();
  await page.waitForSelector('.menu-title:has-text("where to")');
  await page.locator('.menu-item').nth(1).click();
  await page.waitForTimeout(150);
  console.log('Monday afternoon:', await slot(1).getAttribute('aria-label'));
  if (!(await slot(1).getAttribute('aria-label')).includes('Trip to')) fail('the trip went in');
  await slot(13).click();                                                                      // Friday afternoon: a trip, on the map
  await page.locator('.menu-item', { hasText: /^Trip/ }).first().click();
  await page.locator('.menu-item', { hasText: 'Choose on the map' }).click();
  await page.waitForSelector('.cal-map.picking');
  await axe(page, 'the calendar, picking a state');
  await page.locator('.cal-map.picking path[data-state="CA"]').click();
  await page.waitForTimeout(150);
  if (!(await slot(13).getAttribute('aria-label')).includes('California')) fail('the map pick sends Friday\'s trip to California: ' + await slot(13).getAttribute('aria-label'));
  const trips = await page.locator('.cal-trips li').count();
  console.log('Air Force One legs:', trips, '| after the week:', await page.locator('.cal-meter-value').nth(1).textContent());
  if (trips < 2) fail('the itinerary lists the trips');
  await page.screenshot({ path: `${OUT}/calendar.png` });
  await page.click('.cal-presets button:has-text("Road week")');
  await page.waitForTimeout(150);
  if ((await page.locator('.cal-trips li').count()) !== 4) fail('a road week has four trips');
  await page.click('.cal-presets button.toggle');
  await page.waitForTimeout(150);
  if ((await page.locator('.cal-presets button.toggle').getAttribute('aria-pressed')) !== 'true') fail('the chief of staff takes the calendar');
  await page.click('.cal-presets button.toggle');
  await page.click('.cal-top .btn.primary');
  await page.waitForTimeout(150);
  if (await page.locator('.scrim').count()) fail('the calendar closes');

  // 6. the hundred days: the staff can run the flagship bill until the Roosevelt Room opens
  await page.click('.ov-spot-folders');
  await page.waitForSelector('.ov-delegate');
  await page.locator('.ov-delegate button').first().click();
  await page.waitForTimeout(150);
  if ((await page.locator('.ov-delegate button').first().getAttribute('aria-pressed')) !== 'true') fail('the staff takes the flagship bill');
  await axe(page, 'the agenda');
  await closeDialog(page);

  // 7. turns: three weeks of the first hundred days (answering decisions), with Ctrl+Enter for one; the paper after each
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
  await page.keyboard.press('Control+Enter');
  await page.waitForSelector('.lg');
  await axe(page, 'The Capital Ledger');
  await smallest(page, 'The Capital Ledger');
  console.log('the lead:', await page.locator('.lg-lead-head').textContent());
  await page.screenshot({ path: `${OUT}/ledger.png` });
  await page.keyboard.press('Escape');
  for (let i = 0; i < 8; i++) { await page.waitForTimeout(200); if (!(await page.locator('.scrim').count())) break; await page.locator('.dialog button.choice, .dialog .btn.primary').first().click(); }
  await playTurn(false); await playTurn(false);
  await axe(page, 'after three weeks');
  const stamina = await page.locator('.ov-meter [role=meter]').first().getAttribute('aria-valuenow');
  console.log('stamina after three weeks:', stamina);

  // 8. watch mode: fast, for a few weeks, then the controls back
  await page.click('.ov-actions button:has-text("Watch")');
  await page.waitForSelector('.watch');
  await axe(page, 'watch mode');
  await smallest(page, 'watch mode');
  await page.locator('.watch-presets button', { hasText: 'On the road' }).click();
  await page.waitForTimeout(150);
  await page.locator('.watch-speed button', { hasText: 'Fast' }).click();
  const watchedFrom = await page.locator('.ov-date').textContent();
  await page.locator('.watch .btn.primary').click();
  await page.waitForTimeout(2600);
  for (let i = 0; i < 6 && await page.locator('.scrim').count(); i++) { await page.locator('.dialog button.choice, .dialog .btn.primary').first().click(); await page.waitForTimeout(200); }
  await page.keyboard.press('Escape');
  await page.waitForTimeout(300);
  const watchedTo = await page.locator('.ov-date').textContent();
  console.log('watched:', watchedFrom, '->', watchedTo);
  if (watchedFrom === watchedTo) fail('watch mode plays weeks');
  if (await page.locator('.ov-end:has-text("Watching")').count()) fail('Escape takes the controls back');
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
