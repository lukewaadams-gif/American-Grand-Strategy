// Mods end to end in the browser: the example mod applies (a new difficulty, a new card on its turn), saves carry the
// mod set and refuse to load under another, and a broken mod is reported while the game runs without it.
// Usage: node test/mods.cjs [port]
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const PORT = process.argv[2] || '8795';
const example = JSON.parse(fs.readFileSync(path.join(__dirname, '../../mods/hanging-chads.json'), 'utf8'));
let failed = 0; const ok = (c, m) => { console.log((c ? 'ok   ' : 'FAIL ') + m); if (!c) failed++; };
(async () => {
  const browser = await chromium.launch(); const page = await browser.newPage({ viewport: { width: 1280, height: 860 } });
  const errors = []; page.on('pageerror', e => errors.push(String(e)));
  await page.goto(`http://127.0.0.1:${PORT}/index.html`);
  await page.evaluate(m => { localStorage.clear(); localStorage.setItem('ags-mods', JSON.stringify([{ mod: m, on: true }])); }, example);
  await page.reload(); await page.waitForSelector('.title-card');
  ok(await page.evaluate(() => window.__sim.mods.startsWith('Hanging Chads 1.0')), 'the example mod is active: ' + await page.evaluate(() => window.__sim.mods));
  ok((await page.locator('[data-go="mods"] small').textContent()).includes('Hanging Chads'), 'the title menu names it');
  await page.click('[data-go="new"]'); await page.click('[data-next]'); await page.click('[data-next]');
  ok(await page.locator('[data-diff="canvasser"]').count() === 1, 'its new difficulty is offered');
  // play the simulation directly to its new card's turn
  const seen = await page.evaluate(() => {
    const s = window.__sim; s.newGame({ leader: 'governor', party: 'Union', factions: ['labor', 'suburbs', 'justice'], difficulty: 'canvasser', seed: 5 });
    const ids = [];
    for (let i = 0; i < 200 && !s.view.over; i++) { const p = s.view.pending;
      if (p) { ids.push(p.id); const c = p.kind === 'mate' ? { c: 'choose', n: 0 } : p.kind === 'cabinet' ? { c: 'choose', picks: [0, 3, 4] } : { c: 'choose', n: (p.choices && p.choices[0] && p.choices[0].n) || 0 }; if (p.kind === 'mate') s.tryCmd({ c: 'vet', n: 0 }); s.tryCmd(c); continue; }
      if (s.view.title === 'Oct 2, 2000') break; s.tryCmd({ c: 'end_turn' }); }
    return ids;
  });
  ok(seen.includes('butterfly_ballot'), 'the new card fires on Sep 18: ' + seen.join(' '));
  const save = await page.evaluate(() => window.__sim.save());
  ok(save.mods && save.mods.startsWith('Hanging Chads'), 'a save records its mods');
  const refused = await page.evaluate(sv => { const s = window.__sim, keep = s.mods; s.mods = ''; try { s.restore(sv); return 'loaded'; } catch (e) { return e.message; } finally { s.mods = keep; } }, save);
  ok(/saved with mods/.test(refused), 'a modded save will not load without its mods: ' + refused);
  ok(await page.evaluate(sv => { try { window.__sim.restore(sv); return true; } catch { return false; } }, save), 'and loads with them');
  // a broken mod: reported, and the game runs without it
  await page.evaluate(() => localStorage.setItem('ags-mods', JSON.stringify([{ mod: { name: 'Broken', files: { events: [{ id: 'oops', kind: 'card', choices: 'not a list' }] } }, on: true }])));
  await page.reload(); await page.waitForSelector('.title-card');
  const st = await page.evaluate(() => ({ mods: window.__sim.mods, err: window.__sim.modError }));
  ok(st.mods === '' && st.err.length > 0, 'a broken mod is refused and the game runs unmodded: ' + st.err.slice(0, 120));
  ok(await page.locator('.toast').count() > 0, 'and the player is told');
  await page.click('[data-go="mods"]'); ok(await page.locator('.advice').first().textContent().then(t => t.includes('Not applied')), 'the Mods card explains why');
  ok(errors.length === 0, 'no page errors ' + errors.join(' | '));
  await browser.close(); process.exit(failed ? 1 : 0);
})();
