// American Grand Strategy: the entry point. Loads the simulation (WebAssembly), the data and the map, lays the board on
// the table, and moves between the title, the setup and the game.
import './ui/style.css';
import { Sim } from './sim';
import { Board } from './board/board';
import { GameUI, store, SAVE_KEY } from './ui/game';
import { titleScreen, colourPair } from './ui/title';
import { setWorld } from './ui/screens';
import { Settings, applySettings, sound } from './ui/settings';
import { active, modStatus } from './mods';
import { toast } from './ui/kit';

const app = document.getElementById('app')!;
async function boot() {
  applySettings();
  app.innerHTML = '<p style="color:#efe6d0;font:16px Georgia,serif;padding:40px">Laying out the board…</p>';
  try { await document.fonts.ready; } catch { /* fonts are optional */ }
  const sim = new Sim(); let data: any, map: any;
  try { ({ data, map } = await sim.init('.', active())); modStatus.error = sim.modError; modStatus.sig = sim.mods; } catch (e) { app.innerHTML = `<p style="color:#efe6d0;padding:40px;font:16px Georgia,serif">The game could not load (${(e as Error).message}). Reload the page to try again.</p>`; return; }
  fetch('worldmap.json').then(r => r.json()).then(setWorld).catch(() => {});
  const boardEl = document.createElement('div'); boardEl.style.cssText = 'position:absolute;inset:0'; app.innerHTML = ''; app.append(boardEl);
  const board = new Board(); await board.init(boardEl, map);
  document.addEventListener('pointerdown', () => { if (Settings.sound) sound.start(); }, { once: true });
  const title = () => {
    // an opening map on the table: a pleasing scatter of leans
    board.set({ lean: map.hexes.map((h: any) => h.s ? Math.sin(h.x / 80) * 7 + Math.cos(h.y / 60) * 6 : 0), you: '#2f5d8c', them: '#a3342b', units: [], hqs: [], offices: [], lens: 'lean', patterns: false });
    board.centerOn(560, 300, Math.max(board.view.minZ * 1.4, 1.5), 0);
    titleScreen(app, data, boardEl, start, load);
  };
  const play = async () => { const ui = new GameUI(app, sim, board, map, data, title); (window as any).__ui = ui; await ui.start(); };
  const start = async (s: any) => { [Settings.you, Settings.them] = colourPair(s.colour); store.set('ags-colour', s.colour);
    sim.newGame({ leader: s.leader, first: s.first || undefined, last: s.last || undefined, state: s.state || undefined, party: s.party, factions: s.factions, difficulty: s.difficulty, seed: s.seed, ironman: s.ironman }); await play(); };
  const load = async (save: any) => { try { [Settings.you, Settings.them] = colourPair(store.get('ags-colour', 'Navy')); sim.restore(save); await play(); } catch (e) { alert((e as Error).message); } };
  (window as any).__sim = sim; (window as any).__board = board;
  title();
  if (sim.modError) toast('close', 'Mods not applied', sim.modError);
}
boot();
export { SAVE_KEY };
