// Accessibility: axe-core's WCAG 2.1 A and AA rules on every kind of screen (title, setup, the board, an event card,
// the binder, a city dossier, the mods card). The board itself is a canvas hidden from assistive tech; everything it
// shows is also in the DOM. Fails on any violation. Usage: node test/a11y.cjs [port]
const { chromium } = require('playwright');
const fs = require('fs');
const PORT = process.argv[2] || '8795';
const AXE = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
(async () => {
  const browser = await chromium.launch(); const page = await browser.newPage({ viewport: { width: 1280, height: 860 } });
  await page.goto(`http://127.0.0.1:${PORT}/index.html`); await page.evaluate(() => localStorage.clear()); await page.reload();
  await page.waitForSelector('.title-card');
  let total = 0;
  const check = async name => {
    await page.waitForTimeout(500); await page.addScriptTag({ content: AXE });
    const r = await page.evaluate(async () => (await window.axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } })).violations
      .map(v => ({ id: v.id, impact: v.impact, help: v.help, nodes: v.nodes.slice(0, 3).map(n => n.target.join(' ') + ' :: ' + (n.failureSummary || '').split('\n').slice(1, 2).join('')) })));
    total += r.length; console.log(`${r.length ? 'FAIL' : 'ok  '} ${name}${r.length ? '' : ' (no violations)'}`);
    for (const v of r) { console.log(`     ${v.impact} ${v.id}: ${v.help}`); for (const n of v.nodes) console.log(`       ${n}`); }
  };
  await check('title');
  await page.click('[data-go="mods"]'); await check('mods card'); await page.click('[data-x]');
  await page.click('[data-go="new"]'); await check('setup: leader');
  await page.click('[data-next]'); await check('setup: party'); await page.click('[data-next]'); await check('setup: difficulty'); await page.click('[data-next]');
  await page.waitForSelector('.scrim'); await check('intro card');
  // answer cards until a choice card is up, then check it
  for (let i = 0; i < 12; i++) { const s = page.locator('.scrim').last(); if (await s.locator('.choice').count()) break; await s.locator('[data-ok]:not([disabled]), .btn.red:not([disabled])').last().click(); await page.waitForTimeout(400); }
  await check('event card with choices');
  for (let i = 0; i < 20 && await page.locator('.scrim').count(); i++) { const s = page.locator('.scrim').last(); const c = s.locator('.choice:not([disabled])'); if (await c.count()) await c.first().click(); else await s.locator('[data-ok]:not([disabled]), .btn.red:not([disabled])').last().click(); await page.waitForTimeout(400); }
  await check('the board');
  if (await page.locator('.coach').count()) await check('a first-turn tip');
  await page.evaluate(() => window.__ui.menu()); await check('menu'); await page.click('[data-m="how"]'); await check('how to play'); await page.click('.scrim .btn.red');
  await page.evaluate(() => window.__ui.screen('factions')); await check('binder: factions'); await page.click('.binder [data-close]');
  await page.evaluate(() => window.__ui.screen('ideas')); await check('binder: ideas'); await page.click('.binder [data-close]');
  await page.evaluate(() => window.__ui.openCity(window.__sim.view.you.hqs[0].hex)); await check('city dossier');
  console.log(total ? `${total} violation types` : 'WCAG AA: no violations');
  await browser.close(); process.exit(total ? 1 : 0);
})();
