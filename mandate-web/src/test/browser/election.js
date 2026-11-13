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
    'eve2028-lose': { head: /You lose/, next: /The verdict/ },
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
      if (await page.locator('.en-night').count()) { night = true; break; }
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
    const text = night ? (await page.locator('.en-night').textContent()).replace(/\s+/g, ' ') : '';
    if (!night) fail(`${save}: election night never came`);
    else if (!want.head.test(text)) fail(`${save}: election night says "${text.slice(0, 80)}"`);
    if (night) {
      await page.locator('.en-night button.primary').click();
      await page.waitForTimeout(600);
      const title = await page.evaluate(() => document.title);
      if (!want.next.test(title)) fail(`${save}: after the night the page is "${title}"`);
      if (await page.locator('.en-night').count()) fail(`${save}: election night is still showing`);
      console.log(save.padEnd(14), text.slice(0, 70), '->', title);
    }
    if (errors.length) fail(`${save}: ${errors.slice(0, 3).join(' | ')}`);
    await page.close();
  }
  for (const save of ['night2028-win', 'night2032-lose']) {
    const page = await browser.newPage({ viewport: { width: 1600, height: 900 } });
    const errors = []; page.on('pageerror', e => errors.push(String(e)));
    await page.goto(BASE + save + '.json');
    const opened = await page.waitForSelector('.en-night', { timeout: 30000 }).then(() => true).catch(() => false);
    if (!opened) { fail(`${save}: did not open on election night`); await page.close(); continue; }
    const first = (await page.locator('.en-night').textContent()).replace(/\s+/g, ' ');
    await page.reload();
    await page.waitForSelector('.en-night', { timeout: 30000 }).catch(() => fail(`${save}: the night was skipped after a reload`));
    if ((await page.locator('.en-night').textContent().catch(() => '')).replace(/\s+/g, ' ') !== first) fail(`${save}: a different night after a reload`);
    await page.locator('.en-night button.primary').click();
    await page.waitForTimeout(600);
    if (await page.locator('.en-night').count()) fail(`${save}: the night is shown again after it was watched`);
    if (errors.length) fail(`${save}: ${errors.slice(0, 3).join(' | ')}`);
    console.log(save.padEnd(14), 'reload kept the night; watched once');
    await page.close();
  }
  await browser.close();
  console.log(failures ? failures + ' FAILURES' : 'ALL PASS');
  process.exit(failures ? 1 : 0);
})();
