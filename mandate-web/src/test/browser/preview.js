// Checks for the new interface (target/preview): the campaign headquarters with Battleground 2028's schedule, ground
// game and air war. Plays through its controls (the schedule's menus and map picks, by mouse and by keyboard; field
// orders; TV buys, digital and red boxes; the Ad Studio; a week and a day of the final stretch with their reviews) and
// audits each view with axe-core (WCAG 2.1 AA), plus text size, palettes, text scaling and narrow windows. Fails on any
// console error. Usage: node preview.js [out-dir] [base-url]
const { chromium } = require('playwright');
const fs = require('fs');
const axeSrc = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
const OUT = process.argv[2] || 'preview-checks';
const BASE = process.argv[3] || 'http://127.0.0.1:8766/index.html';
fs.mkdirSync(OUT, { recursive: true });
let failures = 0;
(async () => {
  const browser = await chromium.launch();
  const errors = [];
  const open = async (q = '', w = 1600, hgt = 900) => {
    const page = await browser.newPage({ viewport: { width: w, height: hgt }, deviceScaleFactor: 1 });
    page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    page.on('pageerror', e => errors.push(String(e)));
    await page.goto(BASE + q);
    await page.waitForSelector('.sched-grid', { timeout: 30000 });
    await page.waitForTimeout(300);
    return page;
  };
  const axe = async (page, name) => {
    await page.addScriptTag({ content: axeSrc });
    const r = await page.evaluate(async () => await axe.run(document, { runOnly: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] }));
    const n = r.violations.length; failures += n;
    console.log(name.padEnd(28), 'axe:', r.violations.map(v => v.id + '(' + v.nodes.length + ')').join(', ') || 'none');
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
    console.log(name.padEnd(28), 'smallest text', r.min + 'px', r.min < 12 ? 'TOO SMALL ' + r.where : '');
  };
  const runTurn = async (page) => {
    await page.click('.cw-actions .btn.primary');
    for (let i = 0; i < 6; i++) {
      await page.waitForTimeout(300);
      if (!(await page.locator('.scrim').count())) break;
      await page.locator('.dialog button.choice, .dialog .btn.primary').first().click();
    }
    await page.waitForTimeout(400);
  };

  // 1. a week, then its review
  let page = await open();
  await runTurn(page);
  console.log('weekly digest:', (await page.locator('.digest').innerText()).replace(/\n+/g, ' | '));
  await page.screenshot({ path: OUT + '/1-digest.png' });
  await axe(page, 'week + digest');
  await smallest(page, 'week + digest');
  // keyboard only: open the Wednesday evening slot, choose the third activity
  await page.locator('.slot-main').nth(8).focus();
  await page.keyboard.press('Enter');
  await page.waitForSelector('.menu');
  await page.keyboard.press('ArrowDown'); await page.keyboard.press('ArrowDown');
  const chosen = await page.evaluate(() => document.activeElement.querySelector('.menu-label')?.textContent);
  await page.keyboard.press('Enter');
  await page.waitForTimeout(300);
  const label = await page.locator('.slot-main').nth(8).getAttribute('aria-label');
  console.log('keyboard pick:', chosen, '->', label);
  if (!label.includes(chosen.split(' ')[0])) { failures++; console.log('KEYBOARD PICK FAILED'); }
  // keyboard: move it with the map
  const stateBtn = page.locator('.slot-state').nth(0);
  await stateBtn.focus(); await page.keyboard.press('Enter');
  await page.waitForSelector('.pick-banner');
  await page.keyboard.press('ArrowRight'); await page.keyboard.press('Enter');
  await page.waitForTimeout(300);
  console.log('keyboard move toast:', await page.locator('.toast').textContent());
  await page.close();

  // 2. the final stretch, a day at a time
  page = await open('?save=saves/daily.json');
  console.log('daily edition:', await page.locator('.cw-edition').textContent(), '|', await page.locator('.cw-date').textContent());
  console.log('run button:', await page.locator('.cw-actions .btn.primary').innerText());
  console.log('past slots:', await page.locator('.slot.past').count(), '| today row:', await page.locator('.sched-row.today .sched-dayname').textContent());
  await page.screenshot({ path: OUT + '/2-daily.png' });
  await axe(page, 'daily');
  await smallest(page, 'daily');
  await runTurn(page);
  console.log('daily digest:', (await page.locator('.digest').innerText()).replace(/\n+/g, ' | '));
  console.log('after a day, past slots:', await page.locator('.slot.past').count());
  await page.screenshot({ path: OUT + '/3-daily-after.png' });
  await page.close();


  // 4. the ground game: orders from the field desk carry out when the week runs
  page = await open();
  await page.click('#tab-field');
  await page.waitForSelector('.field-table');
  await axe(page, 'field tab');
  await page.click('.field-table button:has-text("Wisconsin")');
  await page.waitForSelector('.state-desk .stepper');
  const toggle = page.locator('button.toggle:has-text("Staff runs the field")');
  if ((await toggle.getAttribute('aria-pressed')) === 'true') await toggle.click();
  const orgBefore = Number(await page.locator('.state-desk .step-value').nth(1).textContent());
  await page.click('button[aria-label="Hire 25 more organisers in Wisconsin"]');
  await axe(page, 'field desk');
  await runTurn(page);
  const orgAfter = Number(await page.locator('.state-desk .step-value').nth(1).textContent());
  console.log('organisers in WI:', orgBefore, '->', orgAfter);
  if (orgAfter !== orgBefore + 25) { failures++; console.log('FIELD ORDER NOT CARRIED OUT'); }
  await page.close();

  // 5. the air war: a market buy and its tone, digital, a red box, and the Ad Studio
  page = await open();
  await page.keyboard.press('2');
  await page.waitForSelector('.cash-row');
  await axe(page, 'money tab');
  await smallest(page, 'money tab');
  await page.locator('path.state[data-code="PA"]').click();
  await page.click('.desk-tabs button:has-text("Ads")');
  const mk = page.locator('.market').first();
  const g0 = Number(await mk.locator('.step-value').textContent());
  await mk.locator('button[aria-label^="More: Weekly rating points"]').click();
  await page.locator('.market').first().locator('.seg-btn:has-text("Neg")').click();
  const g1 = Number(await page.locator('.market').first().locator('.step-value').textContent());
  if (g1 !== Math.min(2000, g0 + 100)) { failures++; console.log('AIRTIME NOT SET', g0, g1); }
  await page.click('button[aria-label^="Add $100K a week of digital"]');
  await page.click('button:has-text("Red-box this state")');
  await axe(page, 'ads desk');
  await page.click('button:has-text("Open the Ad Studio")');
  await page.waitForSelector('.studio');
  await page.click('.studio button:has-text("Produce the ad")');
  await page.click('.studio button:has-text("Run a focus group")');
  await axe(page, 'ad studio');
  await page.click('.studio button:has-text("Put it on the air")');
  const onAir = await page.locator('.studio .ad-card .pill').count();
  console.log('ads on the air:', onAir);
  if (onAir !== 1) failures++;
  await page.keyboard.press('Escape');
  await page.close();

  // 3. palettes, sizes, motion
  for (const [q, name] of [['?palette=high-contrast', 'high contrast'], ['?palette=colour-blind', 'colour-blind'], ['?motion=reduce', 'reduced motion'],
                           ['?size=1.25', 'text 125%']]) {
    page = await open(q);
    await axe(page, name);
    if (name === 'text 125%') await page.screenshot({ path: OUT + '/4-text125.png' });
    await page.close();
  }
  for (const w of [1280, 1000]) {
    page = await open('', w, 800);
    await axe(page, 'width ' + w);
    await smallest(page, 'width ' + w);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1);
    if (overflow) failures++;
    console.log('width ' + w, 'horizontal overflow:', overflow);
    await page.screenshot({ path: OUT + `/5-w${w}.png` });
    await page.close();
  }
  console.log('console errors:', errors.length ? errors : 'none');
  console.log('FAILURES:', failures + errors.length);
  await browser.close();
  process.exit(failures + errors.length ? 1 : 0);
})();
