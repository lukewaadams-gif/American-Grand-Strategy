// Checks for presidency milestone 8: the Situation Room. Every tab at 1600, 1280 and 390 wide and in every palette
// (axe WCAG 2.1 AA, text size, no sideways scroll); a call, a tariff, a war planned and stood down, an objective tried;
// and a trip abroad put on the calendar. Usage: node sitroom.js [out-dir] [save]
const { chromium } = require('playwright');
const fs = require('fs');
const axeSrc = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
const OUT = process.argv[2] || 'm8-checks';
const SAVE = process.argv[3] || 'saves/governing.json';
const BASE = 'http://127.0.0.1:8766/presidency.html?hour=11&save=' + SAVE;
const TABS = ['The world', 'Daily brief', 'Hot spots', 'Operations', 'Trade', 'Alliances', 'Wars', 'Statecraft'];
fs.mkdirSync(OUT, { recursive: true });
let failures = 0;
const fail = (what) => { failures++; console.log('FAIL', what); };
(async () => {
  const browser = await chromium.launch();
  const errors = [];
  const open = async (w = 1600, hgt = 900, q = '') => {
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
    console.log(name.padEnd(44), 'axe:', r.violations.map(v => v.id + '(' + v.nodes.length + ')').join(', ') || 'none');
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
    console.log(name.padEnd(44), 'smallest text', r.min + 'px', r.min < 12 ? 'TOO SMALL ' + r.where : '');
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
  const sit = async (page) => { await page.click('.ov-spot-globe'); await page.waitForSelector('.sitroom', { timeout: 10000 }); };
  const tab = async (page, t) => { await page.locator('.sitroom .cab-tabs button', { hasText: t }).first().click(); await page.waitForTimeout(150); };
  const message = async (page) => (await page.locator('.sit-message').textContent().catch(() => '')) || '';

  for (const [w, hgt] of [[1600, 900], [1280, 800], [390, 844]]) {
    const page = await open(w, hgt);
    await sit(page);
    const lands = await page.locator('.sit-map path.tracked').count();
    console.log(w, 'countries on the map:', lands);
    if (lands < 40) fail(`${w}: the forty countries are on the map`);
    for (const t of TABS) {
      await tab(page, t);
      await check(page, `${w}: ${t}`);
      if (w !== 1280) await page.screenshot({ path: `${OUT}/${w}-${t.replace(/ /g, '-').toLowerCase()}.png`, fullPage: w === 390 });
    }
    await page.close();
  }
  for (const [label, q] of [['high contrast', '&palette=high-contrast'], ['colour-blind', '&palette=colour-blind'], ['text 125%', '&size=1.25'], ['reduced motion', '&motion=reduce']]) {
    const page = await open(1440, 900, q);
    await sit(page);
    for (const t of ['The world', 'Statecraft']) { await tab(page, t); await check(page, `${label}: ${t}`); }
    await page.close();
  }

  // the room's work: a call, a tariff, an objective, a war planned and stood down
  const page = await open(1600, 900);
  await sit(page);
  await page.locator('.sit-cbtn', { hasText: 'Japan' }).click();
  await page.locator('.sitroom button', { hasText: /^Call/ }).click();
  await page.waitForTimeout(150);
  const said = await message(page);
  console.log('call:', said);
  if (!/You speak with/.test(said)) fail('a call to Japan');
  await tab(page, 'Trade');
  await page.selectOption('select[aria-label="Tariff on China"]', '1');
  await page.waitForTimeout(150);
  console.log('tariff:', await message(page));
  if ((await page.locator('select[aria-label="Tariff on China"]').inputValue()) !== '1') fail('a 10% tariff on China');
  await tab(page, 'Statecraft');
  const tryIt = page.locator('.sitroom button:not([disabled])', { hasText: 'Try it' }).first();
  if (await tryIt.count()) { await tryIt.click(); await page.waitForTimeout(150); console.log('objective:', await message(page)); }
  else fail('an objective can be tried');
  await page.selectOption('select[aria-label="The country"]', 'VE');
  await page.waitForTimeout(150);
  await page.locator('.sit-force button', { hasText: 'Plan it' }).first().click();
  await page.waitForTimeout(150);
  if (!(await page.locator('.sit-confirm').count())) fail('a war needs confirming');
  await page.screenshot({ path: `${OUT}/1600-confirm-war.png` });
  await page.locator('.sit-confirm button', { hasText: 'Stand down' }).click();
  await page.waitForTimeout(150);
  if (await page.locator('.sit-confirm').count()) fail('standing down');
  await page.keyboard.press('Escape');
  await page.waitForTimeout(200);

  // a trip abroad on the calendar: three slots to London
  await page.click('.ov-spot-desk');
  await page.waitForSelector('.cal-slot');
  const slot = (i) => page.locator('.cal-slot').nth(Math.floor(i / 3) + (i % 3) * 7);
  await slot(3).click();
  await page.locator('.menu-item', { hasText: 'Trip abroad' }).first().click();
  await page.locator('.menu-item', { hasText: 'United Kingdom' }).first().click();
  await page.waitForTimeout(200);
  let abroad = 0;
  for (const i of [3, 4, 5]) if (/Trip abroad/.test(await slot(i).getAttribute('aria-label'))) abroad++;
  console.log('trip abroad slots:', abroad, '|', await slot(3).getAttribute('aria-label'));
  if (abroad !== 3) fail('a trip abroad fills three slots');
  await page.screenshot({ path: `${OUT}/1600-calendar-abroad.png` });
  await page.close();

  await browser.close();
  console.log('errors', errors.length, errors.slice(0, 5).join('\n'));
  failures += errors.length;
  console.log(failures ? failures + ' FAILURES' : 'ALL PASS');
  process.exit(failures ? 1 : 0);
})();
