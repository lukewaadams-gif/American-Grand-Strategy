// Checks for presidency milestone 5: the Roosevelt Room. Run from a directory with playwright and axe-core, against the
// preview served on 127.0.0.1:8766. Each tab at 1600, 1280 and 390 wide (axe WCAG 2.1 AA, text size, no sideways scroll),
// the palettes, drafting and sending a bill, a deal, a meeting, the telephone, and weeks until the Hill decides. Usage: node congress.js [out-dir] [save]
const { chromium } = require('playwright');
const fs = require('fs');
const axeSrc = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
const OUT = process.argv[2] || 'm5-checks';
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
    console.log(name.padEnd(40), 'axe:', r.violations.map(v => v.id + '(' + v.nodes.length + ')').join(', ') || 'none');
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
    console.log(name.padEnd(40), 'smallest text', r.min + 'px', r.min < 12 ? 'TOO SMALL ' + r.where : '');
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
  const tab = async (page, t) => { await page.locator('.hill .cab-tabs button', { hasText: t }).first().click(); await page.waitForTimeout(150); };
  const TABS = ['The Hill', 'Bills', 'Members', 'The budget', 'Elections', 'Favours and probes'];

  for (const [w, hgt] of [[1600, 900], [1280, 800], [390, 844]]) {
    const page = await open(w, hgt);
    await page.click('.ov-spot-roosevelt');
    await page.waitForSelector('.hill', { timeout: 10000 });
    for (const t of TABS) {
      await tab(page, t);
      await check(page, `${w}: ${t}`);
      if (w !== 1280) await page.screenshot({ path: `${OUT}/${w}-${t.replace(/ /g, '-').toLowerCase()}.png`, fullPage: w === 390 });
    }
    await page.close();
  }

  // ---- the palettes, reduced motion and larger text ----
  for (const [label, q] of [['high contrast', '&palette=high-contrast'], ['colour-blind', '&palette=colour-blind'], ['text 125%', '&size=1.25'], ['reduced motion', '&motion=reduce']]) {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 });
    page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    page.on('pageerror', e => errors.push(String(e)));
    await page.goto(BASE + q);
    await page.waitForSelector('.oval-art', { timeout: 30000 });
    await page.click('.ov-spot-roosevelt');
    await page.waitForSelector('.hill');
    for (const t of ['The Hill', 'Bills', 'Elections']) { await tab(page, t); await check(page, `${label}: ${t}`); }
    if (label === 'colour-blind') await page.screenshot({ path: `${OUT}/colour-blind-elections.png` });
    await page.close();
  }

  // ---- interactions ----
  const page = await open(1600, 900);
  // the telephone opens the room on the members
  await page.click('.ov-spot-phone');
  await page.waitForSelector('.hill');
  const onMembers = await page.locator('.hill .cab-tabs button.on').textContent();
  if (!onMembers.includes('Members')) fail('the telephone should open the members: ' + onMembers);
  const hours = async () => page.evaluate(() => { const dd = [...document.querySelectorAll('.hill .cab-facts dd')].find(d => /left\./.test(d.textContent)); return dd ? Number(dd.textContent.split(' ')[0]) : null; });
  // meet a leader
  await tab(page, 'The Hill');
  const h0 = await hours();
  await tab(page, 'Members');
  await page.locator('.hill-members button.hill-meet').first().click();
  await page.waitForTimeout(150);
  await tab(page, 'The Hill');
  const h1 = await hours();
  console.log('hours before and after a meeting:', h0, h1);
  if (h0 !== null && h0 >= 2 && h1 !== h0 - 2) fail('a meeting takes two hours');
  // draft a bill, shape it, send it
  await tab(page, 'Bills');
  const before = await page.locator('.hill-bill').count();
  const draftButton = page.locator('.hill-drafts button:not([disabled])').first();
  const draftName = (await draftButton.getAttribute('aria-label')).replace('Draft the ', '');
  await draftButton.click();
  await page.waitForTimeout(200);
  const after = await page.locator('.hill-bill').count();
  console.log('drafted', draftName, 'bills', before, '->', after);
  if (after !== before + 1) fail('a draft card appears');
  const card = page.locator('.hill-bill', { hasText: draftName }).first();
  await card.locator('input[type=range]').first().fill('0.8');
  await card.locator('input[type=range]').first().dispatchEvent('change');
  await page.waitForTimeout(150);
  const size = await page.evaluate(n => [...document.querySelectorAll('.hill-bill')].find(c => c.textContent.includes(n))?.querySelector('input[type=range]')?.value, draftName);
  console.log('size after the slider:', size);
  if (Number(size) !== 0.8) fail('the size slider changes the draft');
  await page.screenshot({ path: `${OUT}/draft.png` });
  await page.locator('.hill-bill', { hasText: draftName }).first().locator('button', { hasText: 'Send it to Congress' }).click();
  await page.waitForTimeout(200);
  const stage = await page.locator('.hill-bill', { hasText: draftName }).first().locator('.pill').first().textContent();
  console.log('stage after sending:', stage);
  if (!/committee/i.test(stage)) fail('a sent bill goes to committee: ' + stage);
  // work the votes: a call
  const sent = page.locator('.hill-bill', { hasText: draftName }).first();
  if (await sent.locator('details.hill-work').getAttribute('open') === null) await sent.locator('summary', { hasText: 'Work the votes' }).click();
  const hBefore = await page.evaluate(() => document.querySelector('.hill-work p')?.textContent);
  await sent.locator('button', { hasText: 'Call' }).first().click();
  await page.waitForTimeout(200);
  const hAfter = await page.evaluate(() => document.querySelector('.hill-work p')?.textContent);
  console.log('a call:', hBefore, '->', hAfter);
  if (hBefore === hAfter) fail('a call takes an hour');
  await page.screenshot({ path: `${OUT}/bill-sent.png` });
  // the budget request
  await tab(page, 'The budget');
  const sent0 = await page.locator('button', { hasText: 'Send the request to Congress' }).count();
  console.log('the request can be sent:', sent0 > 0);
  // play weeks until the Hill puts something on the desk
  await page.locator('.hill .room-top button', { hasText: 'Back to the Oval Office' }).click();
  await page.waitForTimeout(200);
  let seen = null;
  for (let wk = 0; wk < 12 && !seen; wk++) {
    await page.keyboard.press('Control+Enter');
    await page.waitForTimeout(400);
    for (let guard = 0; guard < 8; guard++) {
      const eyebrow = await page.locator('.scrim .eyebrow').first().textContent().catch(() => null);
      if (eyebrow && eyebrow.includes('Congress')) {
        seen = await page.locator('.scrim h2').first().textContent();
        await page.screenshot({ path: `${OUT}/hill-decision.png` });
        await check(page, 'a decision from the Hill');
        break;
      }
      if (!(await page.locator('.scrim').count())) break;
      // close the paper or take the first option of an event
      const opt = page.locator('.scrim button.choice').first();
      if (await opt.count()) { await opt.click(); } else { await page.keyboard.press('Escape'); }
      await page.waitForTimeout(250);
    }
  }
  console.log('the Hill put on the desk:', seen);
  if (!seen) fail('in twelve weeks the Hill should put something on the desk');
  await page.close();
  await browser.close();
  console.log('errors', errors.length, errors.slice(0, 5).join('\n'));
  failures += errors.length;
  console.log(failures ? failures + ' FAILURES' : 'ALL PASS');
  process.exit(failures ? 1 : 0);
})();
