// The war room's Message and Polls tabs and the Voters desk (docs/CAMPAIGN_WAR_ROOM.md), on the campaign save (week 9 of
// 2028): the week's issue and tone, the staff choosing, a plan unveiled and the limit on promises; internal polls of the
// nation and a state, and once a week; the Team tab (a hire, a surrogate's week and lead, the staff's tours, the running
// mate); a state's voters by group; keys 4, 5 and 6. At 1600 and 390 pixels wide and in the
// other palettes (axe-core WCAG 2.1 AA, text size, sideways scroll). Fails on any console error.
// Usage: node warroom.js [out-dir]
const { chromium } = require('playwright');
const fs = require('fs');
const axeSrc = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
const OUT = process.argv[2] || 'warroom-checks';
const URL = (q = '') => 'http://127.0.0.1:8766/index.html?hour=11&save=saves/campaign.json' + q;
fs.mkdirSync(OUT, { recursive: true });
let failures = 0;
const fail = (what) => { failures++; console.log('FAIL', what); };
(async () => {
  const browser = await chromium.launch();
  const errors = [];
  const open = async (q = '', w = 1600, hgt = 1000) => {
    const page = await browser.newPage({ viewport: { width: w, height: hgt }, deviceScaleFactor: 1 });
    page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    page.on('pageerror', e => errors.push(String(e)));
    await page.goto(URL(q));
    await page.waitForSelector('.cw-bar', { timeout: 30000 });
    await page.waitForTimeout(500);
    return page;
  };
  const check = async (page, name) => {
    await page.evaluate(() => Promise.all(document.getAnimations().filter(x => x.effect && x.effect.getTiming().iterations !== Infinity).map(x => x.finished.catch(() => null))));
    await page.addScriptTag({ content: axeSrc });
    const r = await page.evaluate(async () => await axe.run(document, { runOnly: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] }));
    failures += r.violations.length;
    console.log(name.padEnd(44), 'axe:', r.violations.map(v => v.id + '(' + v.nodes.length + ')').join(', ') || 'none');
    for (const v of r.violations) for (const x of v.nodes.slice(0, 3)) console.log('   ', v.id, x.target.join(' '));
    const min = await page.evaluate(() => {
      let m = 99;
      for (const el of document.querySelectorAll('.side-panel *, .state-desk *')) {
        if (!(el instanceof HTMLElement) || el.closest('svg') || el.closest('.sr')) continue;
        if (![...el.childNodes].some(n => n.nodeType === 3 && n.textContent.trim())) continue;
        const b = el.getBoundingClientRect();
        if (b.width && b.height) m = Math.min(m, parseFloat(getComputedStyle(el).fontSize));
      }
      return m;
    });
    if (min < 12) fail(`${name}: text at ${min}px`);
    const over = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    if (over > 1) fail(`${name}: scrolls sideways by ${over}px`);
    const cut = await page.evaluate(() => [...document.querySelectorAll('.state-desk, .side-panel')].some(p => p.scrollWidth > p.clientWidth + 2));
    if (cut) fail(`${name}: a panel is wider than its column`);
  };

  // 1. the Message tab
  {
    const page = await open();
    await page.keyboard.press('4');
    await page.waitForTimeout(300);
    if (!(await page.locator('.message-tab').count())) fail('key 4 does not open the Message tab');
    await check(page, '1600: Message');
    await page.screenshot({ path: `${OUT}/1600-message.png` });
    const third = page.locator('.msg-issue').nth(2);
    const label = (await third.locator('.msg-issue-name').textContent()).trim();
    await third.click(); await page.waitForTimeout(300);
    const on = (await page.locator('.msg-issue.on .msg-issue-name').textContent()).trim();
    if (on !== label) fail(`choosing ${label} leaves ${on} on`);
    for (const tone of ['Attack', 'Positive', 'Contrast']) {
      await page.click(`.message-tab .seg-btn:has-text("${tone}")`); await page.waitForTimeout(200);
      if ((await page.locator('.message-tab .seg-btn.on').textContent()).trim() !== tone) fail(`the tone does not become ${tone}`);
    }
    // plans: unveil up to the limit, then none offered
    const head = async () => (await page.locator('.message-tab .sub-head').last().textContent()).trim();
    for (let i = 0; i < 5 && await page.locator('.message-tab .plan-list button:has-text("Unveil")').count(); i++) {
      await page.locator('.message-tab .plan-list button:has-text("Unveil")').first().click(); await page.waitForTimeout(300);
    }
    console.log('plans:', await head());
    const m = /(\d+) of (\d+)/.exec(await head());
    if (!m || m[1] !== m[2]) fail(`the promises are not at their limit: ${await head()}`);
    if (await page.locator('.message-tab .plan-list button:has-text("Unveil")').count()) fail('more plans offered past the limit');
    await page.close();
  }

  // 2. the Polls tab: internal polls, once a week
  {
    const page = await open();
    await page.keyboard.press('5');
    await page.waitForTimeout(300);
    if (!(await page.locator('.polls-tab').count())) fail('key 5 does not open the Polls tab');
    const cash = async () => (await page.locator('.kpi').first().locator('.kpi-value').textContent()).trim();
    const before = await cash();
    await page.click('.polls-tab .send-row button:has-text("Poll the nation")'); await page.waitForTimeout(400);
    await page.locator('.polls-tab .send-row button').nth(1).click(); await page.waitForTimeout(400);
    const rows = await page.locator('.polls-tab table').first().locator('tbody tr').count();
    if (rows !== 2) fail(`${rows} internal polls listed, not 2`);
    await page.click('.polls-tab .send-row button:has-text("Poll the nation")'); await page.waitForTimeout(400);
    if ((await page.locator('.polls-tab table').first().locator('tbody tr').count()) !== 2) fail('the nation polled twice in a week');
    console.log('cash before and after two polls:', before, '->', await cash());
    await check(page, '1600: Polls');
    await page.screenshot({ path: `${OUT}/1600-polls.png` });
    await page.close();
  }

  // 3. the Team tab: a hire, a surrogate's week and its lead, the staff's tours, the running mate
  {
    const page = await open();
    await page.keyboard.press('6');
    await page.waitForTimeout(300);
    if (!(await page.locator('.team-tab').count())) fail('key 6 does not open the Team tab');
    const n = await page.locator('.surrogate-row').count();
    if (n !== 4) fail(`${n} surrogates, not four (running mate, spouse, two allies)`);
    await check(page, '1600: Team');
    await page.screenshot({ path: `${OUT}/1600-team.png` });
    await page.click('.team-row button >> nth=0'); await page.waitForTimeout(300);
    const hire = page.locator('[role="menuitem"]').nth(1);
    const hiring = ((await hire.textContent()) || '').split('·')[0].replace(/\(fictional\)/, '').trim();
    await hire.click(); await page.waitForTimeout(400);
    const now = ((await page.locator('.team-row strong').first().textContent()) || '').replace(/\(fictional\)/, '').trim();
    if (now !== hiring) fail(`hired ${hiring}, the post shows ${now}`);
    await page.click('.surrogate-row >> nth=0 >> .surrogate-controls button >> nth=0'); await page.waitForTimeout(300);
    await page.locator('[role="menuitem"]:has-text("Raise money")').click(); await page.waitForTimeout(300);
    if (!/Raising money/.test(await page.locator('.surrogate-row >> nth=0 >> .surrogate-controls button >> nth=0').textContent())) fail('the running mate does not raise money');
    if (!/Let the staff/.test(await page.locator('.team-tab .sched-controls button').textContent())) fail('a tour by hand leaves the staff planning');
    await page.click('.surrogate-row >> nth=1 >> .surrogate-controls button >> nth=1'); await page.waitForTimeout(300);
    await page.locator('[role="menuitem"]').nth(1).click(); await page.waitForTimeout(300);
    console.log('the spouse\'s lead:', (await page.locator('.surrogate-row >> nth=1 >> .surrogate-controls button >> nth=1').textContent()).trim());
    await page.click('.team-tab .sched-controls button'); await page.waitForTimeout(300);
    if (!/Staff plans the tours: on/.test(await page.locator('.team-tab .sched-controls button').textContent())) fail('the staff does not take the tours back');
    const mates = page.locator('.team-tab .send-row button');
    if (await mates.count()) {
      const pick = mates.nth(3), name = (await pick.textContent()).trim();
      await pick.click(); await page.waitForTimeout(400);
      const mate = (await page.locator('.surrogate-row >> nth=0 >> strong').textContent()).trim();
      if (mate !== name) fail(`chose ${name} as running mate, the ticket shows ${mate}`);
    } else fail('no running mate shortlist in 2028');
    await page.close();
  }

  // 4. the Voters desk, at two widths and in the other palettes
  for (const [label, q, w, hgt] of [['1600', '', 1600, 1000], ['390', '', 390, 844], ['high contrast', '&palette=high-contrast', 1280, 800],
    ['colour-blind', '&palette=colour-blind', 1280, 800], ['text 125%', '&size=1.25', 1280, 800]]) {
    const page = await open(q, w, hgt);
    await page.click('.usmap path.state[data-code="PA"]'); await page.waitForTimeout(400);
    await page.click('.desk-tabs button:has-text("Voters")'); await page.waitForTimeout(400);
    const n = await page.locator('.mini-table.voters tbody tr').count();
    if (n < 8) fail(`${label}: ${n} groups in Pennsylvania`);
    await page.locator('.mini-table.voters').scrollIntoViewIfNeeded();
    await check(page, `${label}: Voters (Pennsylvania)`);
    if (label === '1600' || label === '390') await page.screenshot({ path: `${OUT}/${label}-voters.png` });
    if (label !== '1600') {
      await page.click('#tab-message'); await page.waitForTimeout(300);
      await check(page, `${label}: Message`);
      await page.click('#tab-polls'); await page.waitForTimeout(300);
      await check(page, `${label}: Polls`);
      await page.click('#tab-team'); await page.waitForTimeout(300);
      await check(page, `${label}: Team`);
    }
    await page.close();
  }

  await browser.close();
  console.log('errors', errors.length, errors.slice(0, 5).join('\n'));
  failures += errors.length;
  console.log(failures ? failures + ' FAILURES' : 'ALL PASS');
  process.exit(failures ? 1 : 0);
})();
