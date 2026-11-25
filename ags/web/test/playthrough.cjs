// Plays the vertical slice to the end through the interface: setup, every card, units moved and acting, production,
// research, doctrines, election night, the recount, office. Screenshots along the way; fails on any console error.
// Usage: node test/playthrough.cjs <out-dir> [port] [width] [height] [leaderIndex]
const { chromium } = require('playwright');
const fs = require('fs');
const [OUT = 'shots', PORT = '8795', W = '1440', H = '900', LI = '0'] = process.argv.slice(2);
fs.mkdirSync(OUT, { recursive: true });
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: +W, height: +H }, deviceScaleFactor: 1 });
  page.setDefaultTimeout(8000); const TRACE = !!process.env.TRACE; const log = (...a) => { if (TRACE) console.log(((Date.now() - T0) / 1000).toFixed(1), ...a); }; const T0 = Date.now();
  const errors = []; page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); }); page.on('pageerror', e => errors.push('PAGE ' + e));
  await page.goto(`http://127.0.0.1:${PORT}/index.html`); await page.evaluate(() => localStorage.clear()); await page.reload();
  await page.waitForSelector('.title-card', { timeout: 30000 });
  const shot = async n => page.screenshot({ path: `${OUT}/${W}-${n}.png` });
  const seen = new Set(); const once = async (k, n) => { if (seen.has(k)) return; seen.add(k); await page.waitForTimeout(300); await shot(n); };
  await page.click('[data-go="new"]'); await page.locator('[data-leader]').nth(+LI).click(); await page.click('[data-next]'); await page.click('[data-next]');
  await page.fill('#c-seed', '20001'); await page.locator('#c-seed').dispatchEvent('change'); await page.click('[data-next]');
  const t0 = Date.now(); let turns = 0, step = 0, cards = [];
  const LIMIT = +(process.env.LIMIT || 600000);
  while (Date.now() - t0 < LIMIT && step++ < 4000) {
    try {
    await page.waitForTimeout(60);
    if (await page.locator('text=The end of the slice').count()) { await page.waitForTimeout(500); await shot('99-end'); break; }
    if (await page.locator('.dice').count()) { await once('dice', '70-dice'); await page.click('.dice'); continue; }
    if (await page.locator('.night').count() && !(await page.locator('.scrim').count())) {
      if (await page.locator('#ndone:visible').count()) { await once('night-end', '61-night-end'); await page.click('#ndone'); continue; }
      await page.waitForTimeout(1500); await once('night-mid', '60-night-mid'); if (await page.locator('#nskip:visible').count()) await page.click('#nskip'); continue;
    }
    const scrim = page.locator('.scrim').last();
    if (await scrim.count()) {
      const title = ((await scrim.locator('h2').first().textContent().catch(() => '')) || '').trim(); if (!cards.includes(title)) cards.push(title); log('card', title);
      await once('c:' + title, 'c' + String(seen.size).padStart(2, '0') + '-' + title.replace(/[^a-z0-9]+/gi, '_').slice(0, 24));
      if (await scrim.locator('[data-vet]:not([disabled])').count() && !(await scrim.locator('[data-vet][disabled]').count())) { await scrim.locator('[data-vet]:not([disabled])').first().click(); continue; }
      if (await scrim.locator('[data-pick]').count() && await scrim.locator('[data-ok][disabled]').count()) { await scrim.locator('[data-pick]').first().click(); continue; }
      if (await scrim.locator('[data-k]').count() && await scrim.locator('[data-ok][disabled]').count()) { for (const k of [0, 3, 4]) await scrim.locator(`[data-k="${k}"]`).click(); continue; }
      if (await scrim.locator('[data-ok]:not([disabled])').count()) { await scrim.locator('[data-ok]').click(); continue; }
      const ch = scrim.locator('.choice:not([disabled])'); if (await ch.count()) { await ch.first().click(); continue; }
      const b = scrim.locator('.btn.red:not([disabled])'); if (await b.count()) { await b.last().click(); continue; }
      console.log('stuck in a card:', title); break;
    }
    if (await page.locator('.binder').count()) { await page.click('.binder [data-close]'); continue; }
    if (await page.locator('.side').count() && !(await page.locator('.seal.block').count())) { await page.click('.side [data-x]'); continue; }
    const seal = page.locator('.seal:not([disabled])'); if (!(await seal.count())) continue;
    const label = ((await seal.locator('b').textContent()) || '').trim(); log('seal', label, 'turn', turns);
    if (/research/i.test(label)) { await seal.click(); await page.waitForSelector('.binder'); await once('ideas', '40-ideas'); await page.locator('.tnode.open').first().click(); await page.click('.binder [data-close]'); continue; }
    if (/production|law/i.test(label)) { await seal.click(); await page.waitForSelector('.side'); await once('side:' + label, label.includes('law') ? '42-white-house' : '41-city'); await page.locator('.side [data-pick]:not([disabled]), .side [data-law]:not([disabled])').nth(turns % 2).click(); await page.waitForTimeout(150); await page.click('.side [data-x]'); continue; }
    if (/doctrine/i.test(label)) { await seal.click(); await page.waitForSelector('.binder'); await once('doct', '43-doctrines'); await page.locator('.pol.open:not([disabled])').first().click(); await page.click('.binder [data-close]'); continue; }
    if (/Unit needs orders/i.test(label)) {
      await seal.click(); await page.waitForTimeout(150);
      const moved = await page.evaluate(async () => { const ui = window.__ui; const u = ui.unitSel(); if (!u || !ui.reach || Math.random() < .35) return false; const ks = [...ui.reach.keys()]; if (!ks.length) return false; await ui.onHex(ks[Math.floor(ks.length * .6)]); return true; });
      if (moved) await page.waitForTimeout(200);
      if (turns === 1) { await once('unit', '50-unit'); const a = page.locator('.act:not([disabled])').first(); await a.hover(); await page.waitForTimeout(150); await once('area', '51-area'); }
      // on even turns, put units that can repeat an action on standing orders through the card's chips
      const chip = page.locator('.unitcard [data-stand]:not([data-stand="sleep"]):not([data-stand=""])');
      if (turns % 2 === 0 && await chip.count()) { await once('stand', '52-standing'); await chip.first().click(); await page.waitForTimeout(120); continue; }
      const acts = page.locator('.unitcard .act:not([disabled])'); const n = await acts.count(); let done = false;
      for (let i = 0; i < n; i++) { const t = (await acts.nth(i).textContent()) || ''; if (/plane|Charter|Skip|Field office|Open an HQ|Contest|Attack/i.test(t) && i < n - 1) continue; await acts.nth(i).click(); done = true; break; }
      if (!done) await page.locator('.unitcard [data-act="skip"]').click();
      await page.waitForTimeout(120); continue;
    }
    turns++;
    if (turns <= 3 || turns === 9) { await page.evaluate(() => window.__ui.clearSel()); await page.waitForTimeout(250); await shot(`30-turn${turns}`); }
    if (turns === 2) for (const k of ['factions', 'platform', 'world', 'victory', 'century', 'doctrines', 'ideas']) { await page.evaluate(x => window.__ui.screen(x), k); await page.waitForTimeout(800); await shot(`45-${k}`); await page.click('.binder [data-close]'); }
    if (turns === 3) { const rv = page.locator('.notecard').first(); if (await rv.count()) { await rv.click(); await page.waitForTimeout(300); await shot('46-note'); } }
    await seal.click(); await page.waitForTimeout(250);
    } catch (e) { console.log('step error:', String(e).split('\n')[0]); await shot('err-' + step); }
  }
  const v = await page.evaluate(() => window.__sim.view);
  console.log(`${W} leader ${LI}: turns ${turns}, over ${v.over}, election ${JSON.stringify(v.election)}, legacy ${v.pending?.legacy}`);
  console.log('cards:', cards.join(' | '));
  console.log('errors', errors.length, errors.slice(0, 6).join('\n'));
  await browser.close(); process.exit(errors.length || !v.over ? 1 : 0);
})();
