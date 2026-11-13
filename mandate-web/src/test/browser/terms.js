// Checks for presidency milestone 9: the second term. The second term's theme on the desk after a re-election and the
// weeks to the second inauguration; the Press Briefing Room's "Your term" tab in the race to succeed a two-term
// president, with an endorsement; and the verdict of history at the handoff. At 1600, 1280 and 390 wide and in every
// palette (axe WCAG 2.1 AA, text size, no sideways scroll). Usage: node terms.js [out-dir]
const { chromium } = require('playwright');
const fs = require('fs');
const axeSrc = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
const OUT = process.argv[2] || 'm9-checks';
const URL = (save, q = '') => 'http://127.0.0.1:8766/presidency.html?hour=11&save=saves/' + save + q;
fs.mkdirSync(OUT, { recursive: true });
let failures = 0;
const fail = (what) => { failures++; console.log('FAIL', what); };
(async () => {
  const browser = await chromium.launch();
  const errors = [];
  const open = async (save, w = 1600, hgt = 900, q = '') => {
    const page = await browser.newPage({ viewport: { width: w, height: hgt }, deviceScaleFactor: 1 });
    page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    page.on('pageerror', e => errors.push(String(e)));
    await page.goto(URL(save, q));
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
  const termTab = async (page) => {
    await page.click('.ov-spot-press');
    await page.waitForSelector('.press', { timeout: 10000 });
    await page.locator('.press .cab-tabs button', { hasText: 'Your term' }).click();
    await page.waitForTimeout(200);
  };
  const termLabel = async (page) => (await page.locator('.ov-stats').textContent()).match(/Term (\d)/)?.[1];
  /** End the week: answer what is on the desk (the advised answer), then close the paper. */
  const endWeek = async (page) => {
    await page.locator('.ov-actions button', { hasText: /End week/ }).click();
    for (let guard = 0; guard < 12; guard++) {
      await page.waitForTimeout(250);
      if (await page.locator('.verdict').count()) return 'verdict';
      const advised = page.locator('.scrim button.choice:has-text("Advised")').first();
      const any = page.locator('.scrim button.choice').first();
      if (await advised.count()) { await advised.click(); continue; }
      if (await any.count()) { await any.click(); continue; }
      const cont = page.locator('.scrim button', { hasText: 'Continue' }).first();
      if (await cont.count()) { await cont.click(); continue; }
      if (await page.locator('.scrim').count()) { await page.keyboard.press('Escape'); continue; }
      break;
    }
    return 'week';
  };

  // 1. Re-elected: the second term's theme on the desk, then the weeks to the second inauguration
  {
    const page = await open('secondterm.json');
    console.log('re-elected: top bar term', await termLabel(page));
    await page.locator('.ov-actions button', { hasText: /End week/ }).click();
    // the theme waits on the desk (it has a deadline, so the week runs: E-04): the paper first, then the desk's questions
    for (let i = 0; i < 20 && !(await page.locator('.scrim button.choice').count()); i++) {
      if (await page.locator('.scrim .lg').count()) await page.keyboard.press('Escape');
      await page.waitForTimeout(250);
    }
    await page.waitForSelector('.scrim button.choice', { timeout: 10000 });
    const title = await page.locator('.scrim .dialog-title').first().textContent();
    console.log('on the desk:', title);
    if (!/Second Term/.test(title)) fail('the second term\'s theme is asked first: ' + title);
    await page.screenshot({ path: `${OUT}/1600-theme.png` });
    await check(page, '1600: the second term\'s theme');
    await page.locator('.scrim button.choice').nth(1).click();
    await page.waitForTimeout(300);
    while (await page.locator('.scrim').count()) { await page.keyboard.press('Escape'); await page.waitForTimeout(150); }
    await termTab(page);
    const goals = await page.locator('.press .ww-goal').count();
    console.log('legacy goals shown:', goals);
    if (goals !== 3) fail('three legacy goals after the theme');
    await page.keyboard.press('Escape');
    let term = await termLabel(page);
    for (let wk = 0; wk < 14 && term !== '2'; wk++) { await endWeek(page); term = await termLabel(page); }
    console.log('after the weeks to January 20: Term', term);
    if (term !== '2') fail('the second inauguration in the browser');
    await page.screenshot({ path: `${OUT}/1600-second-term.png` });
    await page.close();
  }

  // 2. The race to succeed a two-term president, in the Press Briefing Room
  for (const [w, hgt] of [[1600, 900], [1280, 800], [390, 844]]) {
    const page = await open('succession.json', w, hgt);
    if (await termLabel(page) !== '2') fail(`${w}: the top bar shows the second term`);
    await termTab(page);
    const rivals = await page.locator('#pr-race ~ table tbody tr, .press table.hill-members tbody tr').count();
    if (!rivals) fail(`${w}: the race lists its candidates`);
    await check(page, `${w}: the race to succeed you`);
    if (w !== 1280) await page.screenshot({ path: `${OUT}/${w}-succession.png`, fullPage: w === 390 });
    if (w === 1600) {
      const endorse = page.locator('.press button', { hasText: 'Endorse' }).first();
      if (await endorse.count()) {
        await endorse.click();
        await page.waitForTimeout(200);
        const marked = await page.locator('.press', { hasText: '(endorsed)' }).count();
        console.log('endorsed:', marked > 0);
        if (!marked) fail('an endorsement shows on the race');
        if (await page.locator('.press button', { hasText: 'Endorse' }).count()) fail('one endorsement only');
      } else console.log('the primaries are over: no endorsement to give');
    }
    await page.close();
  }
  for (const [label, q] of [['high contrast', '&palette=high-contrast'], ['colour-blind', '&palette=colour-blind'], ['text 125%', '&size=1.25'], ['reduced motion', '&motion=reduce']]) {
    const page = await open('succession.json', 1440, 900, q);
    await termTab(page);
    await check(page, `${label}: the race to succeed you`);
    await page.close();
  }

  // 3. The handoff and the verdict of history
  for (const [w, hgt] of [[1600, 900], [390, 844]]) {
    const page = await open('handoff.json', w, hgt);
    let state = 'week';
    for (let wk = 0; wk < 6 && state !== 'verdict'; wk++) state = await endWeek(page);
    if (state !== 'verdict') { fail(`${w}: the verdict at the handoff`); await page.close(); continue; }
    const grade = await page.locator('.verdict-grade').textContent();
    const measures = await page.locator('.verdict-measures dt').count();
    console.log(w, 'verdict:', grade, '|', await page.locator('.verdict-rank').textContent(), '| measures', measures);
    if (measures !== 10) fail('the historians\' ten measures');
    await check(page, `${w}: the verdict`);
    await page.screenshot({ path: `${OUT}/${w}-verdict.png`, fullPage: true });
    await page.close();
  }

  await browser.close();
  console.log('errors', errors.length, errors.slice(0, 5).join('\n'));
  failures += errors.length;
  console.log(failures ? failures + ' FAILURES' : 'ALL PASS');
  process.exit(failures ? 1 : 0);
})();
