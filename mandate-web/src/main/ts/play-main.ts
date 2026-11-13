// The whole game: a new campaign (or a saved game, or one of its moments), the war room to Election Day, election
// night, the transition office, the Oval Office through the first hundred days and every week after, the 2032
// campaign, the second term, and the verdict of history. One page, routed by the game's phase.
import { buildFile, engine, Refused, start } from './core/engine.js';
import { h } from './core/dom.js';
import { host, toast } from './core/dialog.js';
import { openMenu, type MenuItem } from './core/menu.js';
import { usePalette, type PaletteName } from './campaign/palette.js';
import { mountCampaign, unmountCampaign } from './campaign/app.js';
import { mountPresidency, unmountPresidency } from './presidency/app.js';
import { transitionOffice } from './presidency/transition.js';
import { dateLong } from './presidency/oval-screen.js';
import { startScreen } from './start.js';
import { soundMenuItem } from './core/sound.js';

const root = document.getElementById('app')!;
const params = new URLSearchParams(location.search);

const CAMPAIGN = ['GENERAL_CAMPAIGN_2028', 'REELECTION_CAMPAIGN_2032'], OFFICE = ['FIRST_100_DAYS', 'GOVERNING'];

function body(cls: string): void { document.body.className = cls; }

function leave(): void { unmountCampaign(); unmountPresidency(); host.close(); }

function command(c: object): boolean {
  try { engine.command(c); return true; } catch (e) { toast(e instanceof Refused ? e.message : String(e)); return false; }
}

/** Shows the screen for where the game stands. */
function route(): void {
  leave();
  let phase: string;
  try { phase = engine.presidency().phase; } catch { showStart(); return; }
  if (CAMPAIGN.includes(phase)) { body(''); mountCampaign({ onElectionOver: route, restart: showStart }); }
  else if (phase === 'TRANSITION') { body('presidency'); showTransition(); }
  else if (OFFICE.includes(phase)) { body('presidency'); mountPresidency({ onLeave: route, restart: showStart, restartLabel: 'Back to the start', menuItems: gameMenu }); }
  else { body('presidency'); showEnd(); }
}

function saveGame(): void {
  try {
    const blob = new Blob([engine.save()], { type: 'application/json' });
    const a = h('a', { href: URL.createObjectURL(blob), download: 'american-grand-strategy.json' });
    document.body.append(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
    toast('Saved: the file is in your downloads.');
  } catch (e) { toast(String(e)); }
}

function gameMenu(): MenuItem[] {
  return [
    { label: 'Save the game', detail: 'A file you can open again from the start screen', pick: saveGame },
    { label: 'Back to the start', detail: 'A new campaign, or a saved game (save this one first)', pick: showStart },
  ];
}

function showStart(): void {
  leave();
  body('presidency start-page');
  root.replaceChildren(startScreen({
    options: real => engine.options(real),
    newGame: g => { try { engine.newGame(g); route(); } catch (e) { toast(e instanceof Refused ? e.message : String(e)); } },
    loadFile: text => { try { engine.load(text); route(); } catch (e) { toast('That file is not a saved game: ' + String(e)); } },
    quick: async (save, then) => { try { engine.load(await buildFile(save)); if (then) engine.command(then); route(); } catch (e) { toast(String(e)); } },
  }));
  document.title = 'American Grand Strategy';
}

function showTransition(): void {
  const office = transitionOffice({
    view: () => engine.transition(),
    agenda: ids => command({ type: 'SetAgenda', bills: ids }),
    act: (id, action) => command({ type: 'WestWing', order: { type: 'TransitionStep', prospect: id, action } }),
    inaugurate: () => { if (command({ type: 'EndTurn' })) route(); },
    menu: anchor => openMenu(anchor, 'Menu', [soundMenuItem(), ...gameMenu()]),
  });
  root.replaceChildren(office);
  document.title = 'The transition · American Grand Strategy';
}

/** The end: the verdict of history on a presidency, or the campaign's result when the White House was not won. */
function showEnd(): void {
  const vd = engine.verdict();
  const measures = vd.measures ?? [];
  root.replaceChildren(h('main', { class: 'room hill verdict end-page', id: 'main' },
    h('div', { class: 'eyebrow' }, dateLong(vd.date)),
    h('h1', { class: 'dialog-title' }, measures.length ? 'The verdict of history' : 'The campaign is over'),
    h('p', { class: 'verdict-grade' }, (vd.grade ?? '') + (vd.score !== undefined && measures.length ? ` · ${vd.score} of 1,000` : '')),
    vd.rank ? h('p', { class: 'verdict-rank' }, vd.rank) : null,
    measures.length ? h('dl', { class: 'ww-stats verdict-measures' }, ...measures.flatMap(m => [h('dt', null, m.name),
      h('dd', null, h('span', { class: 'hill-meter ' + (m.score >= 60 ? 'good' : m.score >= 40 ? 'mid' : 'bad'), role: 'meter', 'aria-valuemin': '0', 'aria-valuemax': '100',
        'aria-valuenow': String(m.score), 'aria-label': m.name }, h('span', { style: `width:${Math.max(2, m.score)}%` }), h('span', { class: 'hill-meter-value' }, String(m.score))))])) : null,
    h('ul', { class: 'hill-list' }, ...(vd.lines ?? []).map(l => h('li', null, l))),
    (vd.record ?? []).length ? h('details', null, h('summary', null, 'Your record'), h('ul', { class: 'hill-list small' }, ...(vd.record ?? []).map(l => h('li', null, l)))) : null,
    h('div', { class: 'cab-buttons' },
      h('button', { class: 'btn', type: 'button', onclick: saveGame }, 'Save the game'),
      h('button', { class: 'btn primary', type: 'button', onclick: showStart }, 'Back to the start'))));
  document.title = 'The verdict · American Grand Strategy';
}

async function boot(): Promise<void> {
  usePalette((params.get('palette') as PaletteName) ?? 'standard');
  if (params.get('palette')) document.documentElement.dataset['palette'] = params.get('palette')!;
  if (params.get('motion') === 'reduce') document.documentElement.dataset['motion'] = 'reduce';
  if (params.get('size')) document.documentElement.style.setProperty('--s', params.get('size')!);
  await start();
  const save = params.get('save');
  if (save) { engine.load(await buildFile(save)); route(); }
  else showStart();
}
boot().catch(e => {
  root.replaceChildren(h('p', { class: 'boot-error', role: 'alert' }, 'The game could not start: ' + String(e)
    + '. Reloading the page past the cache (Ctrl+Shift+R, or Cmd+Shift+R on a Mac) fetches the latest version.'));
  console.error(e);
});
