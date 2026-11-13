// Election Day and election night (docs/BUG_FIX_REPORT.md, the Election Day softlock): from the eve of each
// presidential election, the campaign is run a day at a time through the war room's own button to election night, and
// on to the next phase (the transition, the final report, or the Oval Office); a game saved on the night opens on it,
// shows the same night after a reload, and does not show it again once watched. Fails on any console error.
// Needs the test saves: java -cp mandate-cli/target/mandate.jar game.mandate.ui.ElectionSaves mandate-web/target/preview/saves/test
// Usage: node election.js
const { chromium } = require('playwright');
const BASE = 'http://127.0.0.1:8766/index.html?hour=11&save=saves/test/';
let failures = 0;
const fail = (what) => { failures++; console.log('FAIL', what); };
(async () => {
  const browser = await chromium.launch();
  const expect = {
    'eve2028-win': { head: /You win the presidency/, next: /The transition/ },
    'eve2028-lose': { head: /You lose/, next: /The campaign/ },
    'eve2032-win': { head: /Four more years/, next: /The Oval Office/ },
    'eve2032-lose': { head: /You lose re-election/, next: /The Oval Office/ },
  };
  for (const [save, want] of Object.entries(expect)) {
    const page = await browser.newPage({ viewport: { width: 1600, height: 900 } });
    const errors = [];
    page.on('pageerror', e => errors.push(String(e)));
    page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    await page.goto(BASE + save + '.json');
    await page.waitForSelector('.cw-bar', { timeout: 30000 });
    let last = '', stuck = 0, night = false;
    for (let t = 0; t < 40 && !night; t++) {
      if (await page.locator('.en').count()) { night = true; break; }
      const choice = page.locator('.scrim button.choice').first();
      if (await choice.count()) { await choice.click(); await page.waitForTimeout(150); continue; }
      const go = page.locator('.scrim button.primary').first();
      if (await go.count()) { await go.click(); await page.waitForTimeout(150); continue; }
      const date = (await page.locator('.cw-date').textContent().catch(() => '')) || '';
      if (date === last && ++stuck > 2) { fail(`${save}: stuck on ${date}`); break; }
      if (date !== last) stuck = 0;
      last = date;
      const label = (await page.locator('.cw-actions button.primary').textContent()).trim();
      if (!/Run the day/.test(label)) fail(`${save}: the final stretch's button reads "${label}"`);
      await page.locator('.cw-actions button.primary').click();
      await page.waitForTimeout(250);
    }
    if (!night) fail(`${save}: election night never came`);
    if (night) {
      // the broadcast: it starts with the polls open and nothing called, plays, pauses, steps, and skips to the result
      const ev0 = (await page.locator('.en-ev').allTextContents()).map(Number);
      if (ev0.some(x => x !== 0)) fail(`${save}: electoral votes before any poll closes: ${ev0}`);
      await page.locator('.en-controls button', { hasText: 'Next report' }).click();
      await page.locator('.en-controls button', { hasText: 'Next report' }).click();
      const clock1 = await page.locator('.en-clock').textContent();
      await page.locator('.en-controls button', { hasText: /Play|Pause/ }).first().click();
      await page.waitForTimeout(300);
      await page.locator('.en-controls button', { hasText: 'Skip to the result' }).click();
      await page.waitForTimeout(200);
      const head = (await page.locator('.en-head h1').textContent()) || '';
      if (!want.head.test(head)) fail(`${save}: election night ends with "${head}"`);
      const ev = (await page.locator('.en-ev').allTextContents()).map(Number);
      const truth = await page.evaluate(() => [...document.querySelectorAll('.en-analysis dd')][0]?.textContent ?? '');
      if (!truth.includes(String(ev[0])) || !truth.includes(String(ev[1]))) fail(`${save}: the scoreboard ${ev} is not the result "${truth}"`);
      if (ev[0] + ev[1] !== 538) fail(`${save}: the final count adds up to ${ev[0] + ev[1]}`);
      console.log(save.padEnd(14), head.trim(), '|', clock1, '->', ev.join('-'));
      await page.locator('.en-continue').click();
      await page.waitForTimeout(600);
      const title = await page.evaluate(() => document.title);
      if (!want.next.test(title)) fail(`${save}: after the night the page is "${title}"`);
      if (await page.locator('.en').count()) fail(`${save}: election night is still showing`);
      console.log('   ->', title);
    }
    if (errors.length) fail(`${save}: ${errors.slice(0, 3).join(' | ')}`);
    await page.close();
  }
  for (const save of ['night2028-win', 'night2032-lose']) {
    const page = await browser.newPage({ viewport: { width: 1600, height: 900 } });
    const errors = []; page.on('pageerror', e => errors.push(String(e)));
    await page.goto(BASE + save + '.json');
    const opened = await page.waitForSelector('.en', { timeout: 30000 }).then(() => true).catch(() => false);
    if (!opened) { fail(`${save}: did not open on election night`); await page.close(); continue; }
    // a few reports in, a reload picks up the broadcast where it was
    await page.locator('.en-controls button', { hasText: /Pause/ }).click().catch(() => {});
    for (let i = 0; i < 6; i++) await page.locator('.en-controls button', { hasText: 'Next report' }).click();
    const where = (await page.locator('.en-now').textContent()) + (await page.locator('.en-clock').textContent());
    await page.reload();
    await page.waitForSelector('.en', { timeout: 30000 }).catch(() => fail(`${save}: the night was skipped after a reload`));
    const back = (await page.locator('.en-now').textContent().catch(() => '')) + (await page.locator('.en-clock').textContent().catch(() => ''));
    if (back !== where) fail(`${save}: after a reload the broadcast is at "${back.slice(0, 60)}", not "${where.slice(0, 60)}"`);
    await page.locator('.en-controls button', { hasText: 'Skip to the result' }).click();
    const head = await page.locator('.en-head h1').textContent();
    await page.locator('.en-continue').click();
    await page.waitForTimeout(600);
    if (await page.locator('.en').count()) fail(`${save}: the night is shown again after it was watched`);
    console.log(save.padEnd(14), 'resumed after a reload at', where.slice(0, 50), '|', head);
    if (errors.length) fail(`${save}: ${errors.slice(0, 3).join(' | ')}`);
    await page.close();
  }
  await browser.close();
  console.log(failures ? failures + ' FAILURES' : 'ALL PASS');
  process.exit(failures ? 1 : 0);
})();
