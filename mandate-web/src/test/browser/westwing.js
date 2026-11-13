// Checks for presidency milestone 6: the West Wing and the Press Briefing Room. Each tab at 1600, 1280 and 390 wide and in
// every palette (axe WCAG 2.1 AA, text size, no sideways scroll); hiring, a post, an order, an order on a policy from the
// Cabinet Room, the vice president, a press conference and an address answered on the desk. Usage: node westwing.js [out-dir] [save]
const { chromium } = require('playwright');
const fs = require('fs');
const axeSrc = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
const OUT = process.argv[2] || 'm6-checks';
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

  const tab = async (page, room, t) => { await page.locator(room + ' .cab-tabs button', { hasText: t }).first().click(); await page.waitForTimeout(150); };
  const WW = ['Staff', 'Cabinet', 'Vice president', 'Orders and rules', 'The courts', 'Oversight'];
  const PR = ['The press', 'Speeches', 'Social media', 'Your party'];
  const openRoom = async (page, spot, cls) => { await page.click(spot); await page.waitForSelector(cls, { timeout: 10000 }); };
  const answerAll = async (page, label) => {
    let n = 0;
    for (let guard = 0; guard < 12; guard++) {
      const c = page.locator('.scrim button.choice').first();
      if (!(await c.count())) {
        const cont = page.locator('.scrim button', { hasText: 'Continue' }).first();
        if (await cont.count()) { await cont.click(); await page.waitForTimeout(150); continue; }
        break;
      }
      if (n === 0 && label) { await page.screenshot({ path: `${OUT}/${label}.png` }); await check(page, label); }
      await c.click(); n++;
      await page.waitForTimeout(200);
    }
    return n;
  };

  for (const [w, hgt] of [[1600, 900], [1280, 800], [390, 844]]) {
    const page = await open(w, hgt);
    await openRoom(page, '.ov-spot-westwing', '.ww');
    for (const t of WW) {
      await tab(page, '.ww', t);
      if (t === 'Cabinet') { await page.locator('.ww-row').first().click(); await page.waitForTimeout(150); }
      await check(page, `${w}: West Wing: ${t}`);
      if (w !== 1280) await page.screenshot({ path: `${OUT}/${w}-ww-${t.replace(/ /g, '-').toLowerCase()}.png`, fullPage: w === 390 });
    }
    await page.keyboard.press('Escape');
    await page.waitForTimeout(200);
    await openRoom(page, '.ov-spot-press', '.press');
    for (const t of PR) {
      await tab(page, '.press', t);
      await check(page, `${w}: Press: ${t}`);
      if (w !== 1280) await page.screenshot({ path: `${OUT}/${w}-press-${t.replace(/ /g, '-').toLowerCase()}.png`, fullPage: w === 390 });
    }
    await page.close();
  }

  for (const [label, q] of [['high contrast', '&palette=high-contrast'], ['colour-blind', '&palette=colour-blind'], ['text 125%', '&size=1.25'], ['reduced motion', '&motion=reduce']]) {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 });
    page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    page.on('pageerror', e => errors.push(String(e)));
    await page.goto(BASE + q);
    await page.waitForSelector('.oval-art', { timeout: 30000 });
    await openRoom(page, '.ov-spot-westwing', '.ww');
    for (const t of ['Staff', 'Orders and rules', 'The courts']) { await tab(page, '.ww', t); await check(page, `${label}: West Wing: ${t}`); }
    await page.keyboard.press('Escape');
    await openRoom(page, '.ov-spot-press', '.press');
    await check(page, `${label}: Press`);
    await page.close();
  }

  // ---- interactions ----
  const page = await open(1600, 900);
  await openRoom(page, '.ov-spot-westwing', '.ww');
  // hire a new communications director
  const before = await page.locator('.ww-card', { hasText: 'Communications director' }).locator('strong').first().textContent();
  await page.locator('button[aria-label="Replace the communications director"]').click();
  await page.waitForTimeout(150);
  await page.locator('.ww-card', { hasText: 'Communications director' }).locator('.ww-option button').first().click();
  await page.waitForTimeout(200);
  const after = await page.locator('.ww-card', { hasText: 'Communications director' }).locator('strong').first().textContent();
  console.log('communications director:', before, '->', after);
  if (before === after) fail('hiring replaces the communications director');
  // the vice president goes on tour
  await tab(page, '.ww', 'Vice president');
  await page.locator('button[aria-label="Tour the swing states"]').click();
  await page.waitForTimeout(150);
  const now = await page.locator('.ww .room-card p').first().textContent();
  console.log('the vice president:', now);
  if (!/Tour/.test(now)) fail('the vice president is on tour');
  // sign a library order
  await tab(page, '.ww', 'Orders and rules');
  const rows = page.locator('.ww .cab-list .cab-row');
  let signed = null;
  for (let i = 0; i < await rows.count() && !signed; i++) {
    const level = await rows.nth(i).locator('.cab-row-level').textContent();
    if (!/support/.test(level)) continue;
    await rows.nth(i).click();
    await page.waitForTimeout(100);
    signed = await page.locator('.ww .cab-detail-title').textContent();
    await page.locator('.ww .cab-detail button.primary').click();
    await page.waitForTimeout(250);
  }
  await answerAll(page, null);
  console.log('signed:', signed);
  if (!signed) fail('an order can be signed');
  // the order is in "What you've done"
  if (!(await page.locator('.scrim').count())) await openRoom(page, '.ov-spot-westwing', '.ww');
  await tab(page, '.ww', 'Orders and rules');
  const doneText = await page.locator('.ww').textContent();
  if (signed && !doneText.includes(signed)) fail('the order shows in what you have done');
  // an order on one policy, from the Cabinet Room
  await page.locator('.ww button', { hasText: 'The Cabinet Room' }).click();
  await page.waitForSelector('.cab');
  let policyOrder = false;
  const areas = page.locator('.cab .cab-tabs button');
  for (let a = 0; a < await areas.count() && !policyOrder; a++) {
    await areas.nth(a).click();
    await page.waitForTimeout(80);
    const pol = page.locator('.cab .cab-list .cab-row');
    for (let i = 0; i < await pol.count() && !policyOrder; i++) {
      await pol.nth(i).click();
      await page.waitForTimeout(60);
      const b = page.locator('.cab button', { hasText: /^Sign an order/ }).first();
      if (await b.count()) { await b.click(); await page.waitForTimeout(250); policyOrder = true; }
    }
  }
  await answerAll(page, null);
  console.log('an order on a policy signed from the Cabinet Room:', policyOrder);
  if (!policyOrder) fail('the Cabinet Room signs an order on a policy');
  await page.keyboard.press('Escape');
  await page.waitForTimeout(200);
  // a press conference: three questions on the desk
  await openRoom(page, '.ov-spot-press', '.press');
  await page.locator('.press button', { hasText: 'Hold a press conference' }).click();
  await page.waitForTimeout(250);
  const q = await answerAll(page, 'press-question');
  console.log('press conference questions answered:', q);
  if (q < 3) fail('a press conference is three questions');
  // an address: five steps
  await openRoom(page, '.ov-spot-press', '.press');
  await tab(page, '.press', 'Speeches');
  await page.locator('.press button', { hasText: 'Request airtime for tonight' }).click();
  await page.waitForTimeout(250);
  const sp = await answerAll(page, 'speech-step');
  console.log('address steps answered:', sp);
  if (sp < 5) fail('an address is five steps');
  // play a few weeks: the paper and the desk keep working
  if (await page.locator('.scrim').count()) await page.keyboard.press('Escape');
  for (let wk = 0; wk < 4; wk++) {
    await page.keyboard.press('Control+Enter');
    await page.waitForTimeout(500);
    for (let g = 0; g < 10 && await page.locator('.scrim').count(); g++) {
      if (await page.locator('.scrim button.choice').count()) { await page.locator('.scrim button.choice').first().click(); await page.waitForTimeout(200); }
      else { await page.keyboard.press('Escape'); await page.waitForTimeout(200); }
    }
  }
  await page.click('.ov-spot-paper');
  await page.waitForTimeout(300);
  await page.screenshot({ path: `${OUT}/paper.png` });
  await check(page, 'the paper');
  await page.close();
  await browser.close();
  console.log('errors', errors.length, errors.slice(0, 5).join('\n'));
  failures += errors.length;
  console.log(failures ? failures + ' FAILURES' : 'ALL PASS');
  process.exit(failures ? 1 : 0);
})();
