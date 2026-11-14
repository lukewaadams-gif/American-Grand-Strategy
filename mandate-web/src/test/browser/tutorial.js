// The tutorial (docs/TUTORIAL.md), through the interface's own buttons: the title screen's Tutorial; the first moves made
// by hand (Pennsylvania on the map, a visit, a message, the week run) with the cause-and-effect card that follows; the
// rest with "Do it for me" at fast speed, the before-and-after comparisons included, to the last card; jumping chapters;
// progress remembered across a reload; "Play from here" leaving the coach with a live game; the coach at 390 pixels as a
// bottom sheet. Axe-core WCAG 2.1 AA and text size on the coach. Fails on any console error. Usage: node tutorial.js [out-dir]
const { chromium } = require('playwright');
const fs = require('fs');
const axeSrc = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
const OUT = process.argv[2] || 'tutorial-checks';
const URL = 'http://127.0.0.1:8766/index.html?hour=11';
fs.mkdirSync(OUT, { recursive: true });
let failures = 0;
const fail = (what) => { failures++; console.log('FAIL', what); };
(async () => {
  const browser = await chromium.launch();
  const errors = [];
  const open = async (w = 1600, hgt = 1000) => {
    const page = await browser.newPage({ viewport: { width: w, height: hgt }, deviceScaleFactor: 1 });
    page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    page.on('pageerror', e => errors.push(String(e)));
    await page.goto(URL);
    await page.waitForSelector('.front-title', { timeout: 30000 });
    return page;
  };
  const check = async (page, name) => {
    await page.evaluate(() => Promise.all(document.getAnimations().filter(x => x.effect && x.effect.getTiming().iterations !== Infinity).map(x => x.finished.catch(() => null))));
    await page.addScriptTag({ content: axeSrc });
    const r = await page.evaluate(async () => await axe.run(document.querySelector('.tut-coach') || document, { runOnly: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] }));
    failures += r.violations.length;
    console.log(name.padEnd(46), 'axe:', r.violations.map(v => v.id + '(' + v.nodes.length + ')').join(', ') || 'none');
    for (const v of r.violations) for (const x of v.nodes.slice(0, 3)) console.log('   ', v.id, x.target.join(' '));
    const min = await page.evaluate(() => {
      let m = 99;
      for (const el of document.querySelectorAll('.tut-coach *')) {
        if (!(el instanceof HTMLElement) || el.closest('svg')) continue;
        if (![...el.childNodes].some(n => n.nodeType === 3 && n.textContent.trim())) continue;
        const b = el.getBoundingClientRect();
        if (b.width && b.height) m = Math.min(m, parseFloat(getComputedStyle(el).fontSize));
      }
      return m;
    });
    if (min < 12) fail(`${name}: text at ${min}px`);
  };
  const head = async (page) => ((await page.locator('.tut-head').textContent().catch(() => '')) || '').trim();
  const next = async (page) => { await page.locator('.tut-buttons .btn.primary').click(); await page.waitForTimeout(250); };
  const waitNext = async (page, what) => {
    const b = page.locator('.tut-buttons .btn.primary');
    for (let i = 0; i < 60; i++) { if (await b.count() && await b.isEnabled()) return true; await page.waitForTimeout(250); }
    fail(`${what}: the move was not recognised`); return false;
  };

  // 1. the start, the first moves by hand, and the card that follows
  {
    const page = await open();
    await page.evaluate(() => { try { localStorage.removeItem('ags-tutorial'); } catch { } });
    await page.reload(); await page.waitForSelector('.front-title');
    if (!(await page.locator('[data-action="tutorial"]').count())) fail('no Tutorial on the title screen');
    await page.click('[data-action="tutorial"]');
    await page.waitForSelector('.tut-coach', { timeout: 30000 });
    await check(page, '1600: the coach, chapter 1');
    await page.screenshot({ path: `${OUT}/1600-chapter1.png` });
    for (let i = 0; i < 4; i++) await next(page);        // chapter 1's three steps and its recap
    if (!/Chapter 2/.test(await head(page))) fail(`not in chapter 2: ${await head(page)}`);
    await page.waitForSelector('.cw-bar', { timeout: 30000 });
    await next(page);                                      // the bar along the top
    await page.locator('.usmap path.state[data-code="PA"]').dispatchEvent('click');
    if (await waitNext(page, 'clicking Pennsylvania')) await next(page);
    await page.locator('.state-desk .send-row button').first().click();
    if (await waitNext(page, 'a visit to Pennsylvania')) await next(page);
    await next(page); await next(page);                    // money and field
    await page.click('#tab-message'); await page.waitForTimeout(200);
    await page.locator('.msg-issue').nth(1).click();
    if (await waitNext(page, 'choosing the message')) await next(page);
    await page.click('.cw-actions .btn.primary');
    for (let i = 0; i < 6 && await page.locator('.scrim').count(); i++) { await page.locator('.scrim .choice').first().click().catch(() => {}); await page.waitForTimeout(300); await page.locator('.scrim .btn.primary').first().click().catch(() => {}); await page.waitForTimeout(300); }
    if (await waitNext(page, 'running the week')) await next(page);
    await page.waitForTimeout(400);
    const card = ((await page.locator('.tut-card').textContent().catch(() => '')) || '').replace(/\s+/g, ' ');
    console.log('the card:', card.slice(0, 200));
    if (!/What your week moved/.test(card) || !/Nationally/.test(card)) fail('no cause-and-effect card after the week');
    await check(page, '1600: the cause-and-effect card');
    await page.screenshot({ path: `${OUT}/1600-cause-and-effect.png` });
    // the rest by "Do it for me", at fast speed, to the last card
    await page.click('.tut-speed button:has-text("Fast")');
    const seen = new Set();
    for (let i = 0; i < 500 && await page.locator('.tut-coach').count(); i++) {
      const t = ((await page.locator('.tut-body .tut-title').first().textContent().catch(() => '')) || '').trim();
      if (t) seen.add(t);
      if (/That is the game/.test(t)) break;
      const b = page.locator('.tut-buttons .btn.primary'), d = page.locator('.tut-buttons button:has-text("Do it for me")');
      if (await b.count() && await b.isEnabled()) { await b.click(); await page.waitForTimeout(120); continue; }
      if (await d.count()) { await d.click(); await page.waitForTimeout(400); continue; }
      await page.waitForTimeout(250);
    }
    console.log('cards seen:', [...seen].join(' | '));
    for (const want of [/Contrast or attack/, /with the bill/, /One week in office/, /Two months later/, /That is the game/])
      if (![...seen].some(t => want.test(t))) fail(`no card matching ${want}`);
    await page.close();
  }

  // 2. chapters: jump, remembered across a reload, and "Play from here"
  {
    const page = await open();
    await page.click('[data-action="tutorial"]');
    await page.waitForSelector('.tut-coach');
    await page.click('.tut-tools button:has-text("Chapters")'); await page.waitForTimeout(200);
    await page.locator('[role="menuitem"]:has-text("6. The first hundred days")').click();
    await page.waitForSelector('.ov', { timeout: 30000 }).catch(() => fail('jumping to chapter 6 does not open the Oval Office'));
    await page.waitForTimeout(500);
    if (!/Chapter 6/.test(await head(page))) fail(`the jump lands on ${await head(page)}`);
    await page.reload(); await page.waitForSelector('.front-title');
    const hint = ((await page.locator('[data-action="tutorial"] .front-item-hint').textContent()) || '').trim();
    console.log('after a reload:', hint);
    if (!/chapter 6/.test(hint)) fail('the tutorial does not remember where it stopped');
    await page.click('[data-action="tutorial"]');
    await page.waitForSelector('.tut-coach');
    if (!/Chapter 6/.test(await head(page))) fail(`resuming lands on ${await head(page)}`);
    await page.waitForSelector('.ov', { timeout: 30000 });
    await page.click('.tut-tools button:has-text("Play from here")'); await page.waitForTimeout(400);
    if (await page.locator('.tut-coach').count()) fail('"Play from here" leaves the coach up');
    if (!(await page.locator('.ov').count())) fail('"Play from here" does not keep the game');
    await page.click('.ov-end'); await page.waitForTimeout(1200);
    for (let i = 0; i < 6 && await page.locator('.scrim').count(); i++) { await page.locator('.scrim .choice').first().click().catch(() => {}); await page.waitForTimeout(300); await page.locator('.scrim .btn.primary').first().click().catch(() => {}); await page.waitForTimeout(300); }
    console.log('the game plays on: week', ((await page.locator('.ov-term, .kpi').first().textContent().catch(() => '')) || '').replace(/\s+/g, ' ').slice(0, 40));
    await page.close();
  }

  // 3. the coach on a phone
  {
    const page = await open(390, 844);
    await page.click('[data-action="tutorial"]');
    await page.waitForSelector('.tut-coach');
    const r = await page.locator('.tut-coach').boundingBox();
    if (!r || r.width < 380 || r.y + r.height < 840) fail('the coach is not a bottom sheet on a phone');
    await check(page, '390: the coach');
    const over = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    if (over > 1) fail(`390: scrolls sideways by ${over}px`);
    await page.screenshot({ path: `${OUT}/390-coach.png` });
    await page.click('.tut-tools button[aria-label="Close the tutorial"]'); await page.waitForTimeout(300);
    if (!(await page.locator('.front-title').count())) fail('closing the tutorial does not return to the title');
    await page.close();
  }

  await browser.close();
  console.log('errors', errors.length, errors.slice(0, 5).join('\n'));
  failures += errors.length;
  console.log(failures ? failures + ' FAILURES' : 'ALL PASS');
  process.exit(failures ? 1 : 0);
})();
