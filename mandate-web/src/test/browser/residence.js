// Checks for presidency milestone 7: the Residence and the country's voter groups. The country room's groups and the
// Residence at 1600, 1280 and 390 wide and in every palette (axe WCAG 2.1 AA, text size, no sideways scroll), and the
// Residence's weekend plans going onto the calendar. Usage: node residence.js [out-dir] [save]
const { chromium } = require('playwright');
const fs = require('fs');
const axeSrc = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
const OUT = process.argv[2] || 'm7-checks';
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


  for (const [w, hgt] of [[1600, 900], [1280, 800], [390, 844]]) {
    const page = await open(w, hgt);
    await page.click('.ov-spot-windows');
    await page.waitForSelector('.ctry');
    const groups = await page.locator('#ctry-groups').count();
    if (!groups) fail('the country room shows the groups');
    const rows = await page.locator('.room-card:has(#ctry-groups) tbody tr').count();
    console.log(w, 'groups shown', rows);
    await check(page, `${w}: the country, with the groups`);
    if (w !== 1280) await page.screenshot({ path: `${OUT}/${w}-country.png`, fullPage: true });
    await page.keyboard.press('Escape');
    await page.waitForTimeout(200);
    await page.click('.ov-spot-residence');
    await page.waitForSelector('.residence');
    await check(page, `${w}: the Residence`);
    if (w !== 1280) await page.screenshot({ path: `${OUT}/${w}-residence.png`, fullPage: w === 390 });
    await page.close();
  }
  for (const [label, q] of [['high contrast', '&palette=high-contrast'], ['colour-blind', '&palette=colour-blind'], ['text 125%', '&size=1.25'], ['reduced motion', '&motion=reduce']]) {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 });
    page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    page.on('pageerror', e => errors.push(String(e)));
    await page.goto(BASE + q);
    await page.waitForSelector('.oval-art', { timeout: 30000 });
    await page.click('.ov-spot-residence');
    await page.waitForSelector('.residence');
    await check(page, `${label}: the Residence`);
    await page.keyboard.press('Escape');
    await page.click('.ov-spot-windows');
    await page.waitForSelector('.ctry');
    await check(page, `${label}: the country`);
    await page.close();
  }
  // the weekend plans go onto the calendar
  const page = await open(1600, 900);
  await page.click('.ov-spot-residence');
  await page.waitForSelector('.residence');
  await page.locator('.residence button', { hasText: 'A family weekend' }).click();
  await page.waitForTimeout(200);
  const status = await page.locator('.res-done').textContent().catch(() => '');
  console.log('plan:', status);
  if (!/calendar/.test(status)) fail('a family weekend goes on the calendar');
  await page.locator('.residence button', { hasText: 'Open the calendar' }).click();
  await page.waitForSelector('.cal');
  const sat = await page.locator('.cal [aria-label*="Saturday"]').first().getAttribute('aria-label').catch(() => null);
  console.log('Saturday on the calendar:', sat);
  if (sat && !/Family/i.test(sat)) fail('Saturday is family time: ' + sat);
  await page.close();
  await browser.close();
  console.log('errors', errors.length, errors.slice(0, 5).join('\n'));
  failures += errors.length;
  console.log(failures ? failures + ' FAILURES' : 'ALL PASS');
  process.exit(failures ? 1 : 0);
})();
