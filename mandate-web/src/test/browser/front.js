// The front end (start.ts) and the site's front page (front-main.ts):
//   1. the title screen and every setup step, at 1600 and 390 wide, in the other palettes and with reduced motion
//      (axe-core WCAG 2.1 AA, text size, sideways scroll);
//   2. the keyboard: the title's menu by arrows, Enter on New Game, Escape back, Enter forward;
//   3. the setup: the real 2028 field by default, the fictional cast on request, the chosen candidate's file, the face-off,
//      and Begin opening the war room; the moments;
//   4. the site's front page (the root, served on 8767 with play/ and classic/ beside it): the same title screen on the
//      game's engine, a new game and a moment handed to the game's page, and the earlier slice at classic/.
// Fails on any console error. Usage: node front.js [out-dir]
const { chromium } = require('playwright');
const fs = require('fs');
const axeSrc = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
const OUT = process.argv[2] || 'front-checks';
const GAME = (q = '') => 'http://127.0.0.1:8766/index.html?hour=11' + q;
const ROOT = 'http://127.0.0.1:8767/';
fs.mkdirSync(OUT, { recursive: true });
let failures = 0;
const fail = (what) => { failures++; console.log('FAIL', what); };
(async () => {
  const browser = await chromium.launch();
  const errors = [];
  const open = async (url, w = 1600, hgt = 900) => {
    const page = await browser.newPage({ viewport: { width: w, height: hgt }, deviceScaleFactor: 1 });
    page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    page.on('pageerror', e => errors.push(String(e)));
    await page.goto(url);
    await page.waitForSelector('.front-title, .cw-bar, .ov, .en', { timeout: 30000 });
    await page.waitForTimeout(500);
    return page;
  };
  const axe = async (page, name) => {
    await page.evaluate(() => Promise.all(document.getAnimations().filter(x => x.effect && x.effect.getTiming().iterations !== Infinity).map(x => x.finished.catch(() => null))));
    await page.addScriptTag({ content: axeSrc });
    const r = await page.evaluate(async () => await axe.run(document, { runOnly: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] }));
    failures += r.violations.length;
    console.log(name.padEnd(50), 'axe:', r.violations.map(v => v.id + '(' + v.nodes.length + ')').join(', ') || 'none');
    for (const v of r.violations) for (const x of v.nodes.slice(0, 3)) console.log('   ', v.id, x.target.join(' '), (x.failureSummary || '').split('\n').slice(1, 2).join(''));
  };
  const smallest = async (page, name) => {
    const r = await page.evaluate(() => {
      let min = 99, where = '';
      for (const el of document.querySelectorAll('body *')) {
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
    console.log(name.padEnd(50), 'smallest text', r.min + 'px', r.min < 12 ? 'TOO SMALL ' + r.where : '');
  };
  const sideways = async (page, name) => {
    const over = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    if (over > 1) fail(`${name}: scrolls sideways by ${over}px`);
  };
  const check = async (page, name) => { await axe(page, name); await smallest(page, name); await sideways(page, name); };
  const title = async (page) => ((await page.locator('.setup-title, .front-heading').first().textContent()) || '').trim();

  // 1. the title screen and every step of a Full Game's setup, at two widths
  for (const [w, hgt] of [[1600, 900], [390, 844]]) {
    const page = await open(GAME(), w, hgt);
    const items = (await page.locator('.front-item .front-item-label').allTextContents()).map(s => s.trim());
    console.log(`${w}: the title menu:`, items.join(' | '));
    if (items.join('|') !== 'New Game|Continue|Start at a moment') fail(`${w}: the title menu is ${items.join(', ')}`);
    // the menu sits above the Capitol: nothing of the art under the buttons
    const clear = await page.evaluate(() => {
      const menu = document.querySelector('.front-menu').getBoundingClientRect(), art = document.querySelector('.front-capitol').getBoundingClientRect();
      return menu.bottom <= art.top + 1;
    });
    if (!clear) fail(`${w}: the Capitol runs under the menu`);
    await check(page, `${w}: the title screen`);
    await page.screenshot({ path: `${OUT}/${w}-title.png` });
    await page.click('[data-action="new"]');
    for (let i = 0; i < 6; i++) {
      const t = await title(page);
      await check(page, `${w}: ${t}`);
      await page.screenshot({ path: `${OUT}/${w}-step${i + 1}.png`, fullPage: false });
      if (await page.locator('.setup-begin').count()) break;
      await page.click('.setup-next');
      await page.waitForTimeout(250);
    }
    if (!(await page.locator('.front-versus .versus-side').count())) fail(`${w}: no face-off before the campaign`);
    await page.close();
  }

  // the other palettes, larger text and reduced motion, on the title and the candidate step
  for (const [label, q] of [['high contrast', '&palette=high-contrast'], ['colour-blind', '&palette=colour-blind'], ['text 125%', '&size=1.25'], ['reduced motion', '&motion=reduce']]) {
    const page = await open(GAME(q), 1280, 800);
    await check(page, `${label}: the title screen`);
    await page.click('[data-action="new"]'); await page.click('.setup-next'); await page.click('.setup-next');
    await check(page, `${label}: ${await title(page)}`);
    await page.close();
  }

  // 2. the keyboard
  {
    const page = await open(GAME(), 1600, 900);
    await page.focus('[data-action="new"]');
    await page.keyboard.press('ArrowDown');
    const second = await page.evaluate(() => document.activeElement.dataset.action);
    if (second !== 'continue') fail(`ArrowDown moves to "${second}", not Continue`);
    await page.keyboard.press('ArrowUp');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(300);
    if (!/Choose your game/.test(await title(page))) fail('Enter on New Game opens the setup');
    const focused = await page.evaluate(() => document.activeElement.classList.contains('front-heading'));
    if (!focused) fail('the step\'s heading takes the focus');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(250);
    if (!/Choose your side/.test(await title(page))) fail(`Enter moves on: "${await title(page)}"`);
    await page.keyboard.press('Escape');
    await page.waitForTimeout(250);
    if (!/Choose your game/.test(await title(page))) fail('Escape goes back a step');
    await page.keyboard.press('Escape');
    await page.waitForTimeout(250);
    if (!(await page.locator('.front-title').count())) fail('Escape from the first step goes back to the title');
    await page.close();
  }

  // 3. the setup's choices: real people by default, the fictional cast, the file, the face-off, the war room
  {
    const page = await open(GAME(), 1600, 900);
    await page.click('[data-action="new"]'); await page.click('.setup-next');
    if ((await page.locator('.cast-card[data-cast="real"]').getAttribute('aria-checked')) !== 'true') fail('real people are not the default');
    await page.click('.setup-next');
    const real = await page.locator('.cand-card .front-card-title').allTextContents();
    console.log('the real Democratic field:', real.length, 'candidates, from', real.slice(0, 4).join(', '));
    if (!real.includes('Josh Shapiro') || real.length < 10) fail('the real 2028 field is not offered');
    await page.click('.cand-card[data-id="bg_ossoff"]');
    const file = ((await page.locator('.front-dossier .dossier-name').textContent()) || '').trim();
    if (file !== 'Jon Ossoff') fail(`the file shows "${file}" after choosing Ossoff`);
    if ((await page.locator('.front-dossier .stat-bar').count()) !== 5) fail('the file has five ratings');
    await page.click('.setup-back'); await page.click('.cast-card[data-cast="fictional"]'); await page.click('.setup-next');
    const fictional = await page.locator('.cand-card .front-card-detail').allTextContents();
    if (fictional.some(t => /real/.test(t))) fail('a real candidate in the fictional cast');
    await page.click('.setup-back'); await page.click('.cast-card[data-cast="real"]'); await page.click('.setup-next');
    await page.click('.cand-card[data-id="bg_ossoff"]'); await page.click('.setup-next');
    await page.click('.cand-card[data-id="bg_vance"]'); await page.click('.setup-next'); await page.click('.setup-next');
    const sides = (await page.locator('.versus-name').allTextContents()).map(s => s.trim());
    console.log('the face-off:', sides.join(' vs '));
    if (sides.join(' vs ') !== 'Jon Ossoff vs JD Vance') fail(`the face-off is ${sides.join(' vs ')}`);
    await page.click('.setup-begin');
    await page.waitForSelector('.cw-bar', { timeout: 30000 }).catch(() => fail('Begin does not open the war room'));
    const who = await page.locator('body').textContent();
    if (!/Ossoff/.test(who)) fail('the war room is not Ossoff\'s campaign');
    await page.close();
  }
  {
    const page = await open(GAME(), 1600, 900);
    await page.click('[data-action="moments"]');
    const n = await page.locator('.moment-card').count();
    if (n !== 6) fail(`${n} moments, not six`);
    await check(page, '1600: the moments');
    await page.click('.moment-card[data-save="saves/governing.json"]');
    await page.waitForSelector('.ov', { timeout: 30000 }).catch(() => fail('a moment does not open the Oval Office'));
    await page.close();
  }

  // 4. the site's front page: the same title screen, handing the choice to the game's page
  {
    const page = await open(ROOT, 1600, 900);
    if (!(await page.locator('.front-title').count())) fail('the site\'s front page has no title screen');
    await check(page, 'the site\'s front page');
    await page.screenshot({ path: `${OUT}/root-title.png` });
    const links = await page.locator('.front-foot a').allTextContents();
    if (!links.some(l => /earlier playable slice/.test(l))) fail('the front page does not link the earlier slice');
    await page.click('[data-action="new"]');
    await page.click('.mode-card[data-mode="CAMPAIGN_ONLY"]');
    for (let i = 0; i < 5; i++) { await page.click('.setup-next'); await page.waitForTimeout(150); }
    await page.click('.setup-begin');
    await page.waitForURL(/play\/index\.html/, { timeout: 30000 }).catch(() => fail('the front page does not hand over to the game'));
    await page.waitForSelector('.cw-bar', { timeout: 30000 }).catch(() => fail('the handed-over game does not open the war room'));
    console.log('the front page handed a new game to', page.url());
    await page.reload();
    await page.waitForSelector('.front-title, .cw-bar', { timeout: 30000 });
    if (await page.locator('.cw-bar').count()) fail('a reload repeated the hand-over');
    await page.close();
  }
  {
    const page = await open(ROOT, 390, 844);
    await check(page, '390: the site\'s front page');
    await page.click('[data-action="moments"]');
    await page.click('.moment-card[data-save="saves/campaign.json"]');
    await page.waitForSelector('.cw-bar', { timeout: 30000 }).catch(() => fail('a moment from the front page does not open'));
    await page.close();
  }
  {
    const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
    const r = await page.goto(ROOT + 'classic/');
    if (!r || !r.ok()) fail('the earlier slice is not at classic/');
    console.log('classic/:', r && r.status(), await page.title());
    await page.close();
  }

  await browser.close();
  console.log('errors', errors.length, errors.slice(0, 5).join('\n'));
  failures += errors.length;
  console.log(failures ? failures + ' FAILURES' : 'ALL PASS');
  process.exit(failures ? 1 : 0);
})();
