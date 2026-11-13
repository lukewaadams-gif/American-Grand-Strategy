// The start of a game: one of the four games on the one engine (docs/MODES.md), a saved game from a file, or a quick
// start at one of the game's moments.
//   Full Game        the 2028 campaign, then (if won) the presidency through re-election and a second term to 2037
//   Campaign Only    the 2028 campaign, ending after election night with the campaign's assessment
//   Governing Only   the presidency from election night, November 7, 2028, with the administration the player chooses
//   Individual Crisis Taiwan or the Strait of Hormuz on the presidency's own systems, ending with an after-action report
// The world is real people by default; the fictional cast is the alternative.
import { h } from './core/dom.js';
import { portrait, type Look } from './art/portrait.js';

export interface StartCandidate { id: string; name: string; home: string; charisma: number; debate: number; fundraising: number; policy: number;
  discipline: number; ideology: number; real: boolean; look?: Look | null }
export interface StartOptions { democrats: StartCandidate[]; republicans: StartCandidate[]; scenarios: { id: string; label: string; description: string }[];
  difficulties: { id: string; label: string; description: string }[] }
export type GameMode = 'FULL_GAME' | 'CAMPAIGN_ONLY' | 'GOVERNING_ONLY';
export interface NewGame { seed: number; party: 'DEMOCRAT' | 'REPUBLICAN'; player: string; opponent: string; scenario: string; difficulty: string;
  realRoster: boolean; mode: GameMode }

export interface StartActions {
  options(realRoster: boolean): StartOptions;
  newGame(g: NewGame): void;
  loadFile(text: string): void;
  /** Opens a save; with a command, gives it first (a crisis begins at once). */
  quick(save: string, then?: object): void;
  /** Island Chain on its own: the Taiwan war game without a presidency. */
  islandChain(o: { year: number; preset: number; side: 'US' | 'PRC' }): void;
}

type Mode = GameMode | 'CRISIS';

/** The four games, in the order the start screen offers them. */
export const MODES: { id: Mode; label: string; detail: string }[] = [
  { id: 'FULL_GAME', label: 'Full Game', detail: 'The 2028 campaign. Win, and you govern: the first hundred days, the 2030 midterms, re-election in 2032 and, if re-elected, a second term to January 2037. Lose, and the game ends on election night.' },
  { id: 'CAMPAIGN_ONLY', label: 'Campaign Only', detail: 'The 2028 campaign alone. It ends after election night, won or lost, with the map, the popular vote, the states and the campaign’s assessment.' },
  { id: 'GOVERNING_ONLY', label: 'Governing Only', detail: 'The presidency alone, from election night, November 7, 2028, with the administration you choose already elected. The same presidency as the Full Game, through re-election and a second term.' },
  { id: 'CRISIS', label: 'Individual Crisis', detail: 'One crisis, Taiwan or the Strait of Hormuz, on the presidency’s own systems. It ends when the crisis does, with an after-action report.' },
];

/** The in-depth crises: a governing presidency in September 2029 (the sample administration) and the crisis at once (docs/CRISES.md). */
const CRISES = [
  { id: 'taiwan', label: 'The Taiwan crisis', detail: 'China’s coast guard and navy close in on Taiwan: weeks of warning, then, if it comes, the war by the hour.' },
  { id: 'hormuz', label: 'The Strait of Hormuz', detail: 'The Guards seize a tanker and Tehran threatens to close the strait: day by day, a fifth of the world’s oil.' },
];
const PRESETS = [
  { label: 'Base case', detail: 'The crisis as the intelligence community expects it.' },
  { label: 'Optimistic', detail: 'More warning, steadier allies, a less determined adversary.' },
  { label: 'Pessimistic', detail: 'Less warning, shakier allies, an adversary ready to go further.' },
];

const QUICK = [
  { save: 'saves/campaign.json', label: 'The campaign, week 9', detail: 'Battleground 2028’s headquarters at the height of the race.' },
  { save: 'saves/presidency.json', label: 'Inauguration week', detail: 'Day one: the first hundred days open the presidency, with the flagship bill to pass.' },
  { save: 'saves/governing.json', label: 'Governing', detail: 'September 2029: Congress, the West Wing and the world are yours.' },
  { save: 'saves/secondterm.json', label: 'Four more years', detail: 'Just re-elected: the second term’s theme on the desk.' },
  { save: 'saves/succession.json', label: 'The race to succeed you', detail: 'Spring 2036: the primaries to choose your successor.' },
  { save: 'saves/taiwan.json', label: 'A Taiwan crisis in the Full Game', detail: 'September 2029: China’s coast guard closes on Taiwan, and the Situation Room counts the days.' },
];

function rating(c: StartCandidate): string {
  return `Charisma ${c.charisma}, debate ${c.debate}, money ${c.fundraising}, policy ${c.policy}, discipline ${c.discipline}`;
}

export function startScreen(act: StartActions): HTMLElement {
  // real people by default: the 2028 world is the real roster; the fictional cast is the alternative
  let real = true;
  let mode: Mode = 'FULL_GAME';
  let party: 'DEMOCRAT' | 'REPUBLICAN' = 'DEMOCRAT';
  let player = '', opponent = '', scenario = 'TOSS_UP', difficulty = 'STANDARD';
  let crisis = 'taiwan', preset = 0;
  let icYear = 2030, icSide: 'US' | 'PRC' = 'US';
  const root = h('main', { class: 'start', id: 'main' });

  const radio = (group: string, checked: boolean, label: HTMLElement | string, detail: string, on: () => void, cls = 'start-opt') =>
    h('label', { class: cls + (checked ? ' on' : '') },
      h('input', { type: 'radio', name: group, checked, onchange: () => { on(); draw(); } }),
      h('span', null, h('strong', null, label), h('span', { class: 'muted small' }, ' · ' + detail)));

  function people(o: StartOptions): HTMLElement[] {
    const mine = party === 'DEMOCRAT' ? o.democrats : o.republicans, theirs = party === 'DEMOCRAT' ? o.republicans : o.democrats;
    if (!mine.some(c => c.id === player)) player = mine[0]?.id ?? '';
    if (!theirs.some(c => c.id === opponent)) opponent = theirs[0]?.id ?? '';
    const governing = mode === 'GOVERNING_ONLY';
    const pick = (label: string, list: StartCandidate[], value: string, set: (v: string) => void) => h('fieldset', { class: 'start-field' },
      h('legend', null, label),
      h('div', { class: 'start-cands' }, ...list.map(c => h('label', { class: 'start-cand' + (c.id === value ? ' on' : '') },
        h('input', { type: 'radio', name: label, value: c.id, checked: c.id === value, onchange: () => { set(c.id); draw(); } }),
        portrait(c.id, list === o.democrats ? 'D' : 'R', 40, true, c.look),
        h('span', { class: 'start-cand-name' }, c.name + (c.real ? ' (real)' : '')),
        h('span', { class: 'muted small' }, `${c.home} · ${rating(c)}`)))));
    const choose = (label: string, items: { id: string; label: string; description: string }[], value: string, set: (v: string) => void) =>
      h('fieldset', { class: 'start-field' }, h('legend', null, label), ...items.map(i => radio(label, i.id === value, i.label, i.description, () => set(i.id))));
    return [
      h('fieldset', { class: 'start-field' }, h('legend', null, governing ? 'The party in power' : 'Your party'),
        h('div', { class: 'cab-buttons', role: 'group', 'aria-label': governing ? 'The party in power' : 'Your party' }, ...(['DEMOCRAT', 'REPUBLICAN'] as const).map(p =>
          h('button', { class: 'btn toggle' + (p === party ? ' on' : ''), type: 'button', 'aria-pressed': String(p === party), onclick: () => { party = p; draw(); } },
            p === 'DEMOCRAT' ? 'Democrat' : 'Republican')))),
      pick(governing ? 'The president-elect' : 'Your candidate', mine, player, v => { player = v; }),
      pick(governing ? 'Defeated in 2028' : 'Your opponent', theirs, opponent, v => { opponent = v; }),
      choose('The country', o.scenarios, scenario, v => { scenario = v; }),
      choose(governing ? 'The other party’s campaigns (2032 and after)' : 'Your opponent’s campaign', o.difficulties, difficulty, v => { difficulty = v; }),
      h('label', { class: 'start-opt' }, h('input', { type: 'checkbox', checked: real, onchange: (e: Event) => { real = (e.target as HTMLInputElement).checked; draw(); } }),
        h('span', null, 'Real people', h('span', { class: 'muted small' }, ' · the real candidates, senators, congressional leaders, cabinet prospects, justices and leaders abroad of 2028. Fictional newcomers appear from 2030. Off, a fictional cast throughout.'))),
    ];
  }

  function crisisSetup(): HTMLElement[] {
    return [
      h('fieldset', { class: 'start-field' }, h('legend', null, 'The crisis'), ...CRISES.map(c => radio('The crisis', c.id === crisis, c.label, c.detail, () => { crisis = c.id; }))),
      h('fieldset', { class: 'start-field' }, h('legend', null, 'How it starts'), ...PRESETS.map((p, i) => radio('How it starts', i === preset, p.label, p.detail, () => { preset = i; }))),
      h('p', { class: 'muted small start-note' }, 'It begins in September 2029, in the sample presidency (real people). Congress, the economy, the press and the allies go on around it, as in the Full Game. The game ends at the crisis’s outcome, won, lost or ended in part, with an after-action report.'),
    ];
  }

  function go(): void {
    if (mode === 'CRISIS') { act.quick('saves/governing.json', { type: 'IndividualCrisis', crisis, preset }); return; }
    act.newGame({ seed: Math.floor(Math.random() * 1e9), party, player, opponent, scenario, difficulty, realRoster: real, mode });
  }

  function draw(): void {
    const o = act.options(real);
    const label = mode === 'CRISIS' ? 'Begin the crisis' : mode === 'GOVERNING_ONLY' ? 'Begin on election night, November 7, 2028' : 'Begin the campaign';
    root.replaceChildren(
      h('header', { class: 'start-head' }, h('div', { class: 'eyebrow' }, 'An American political strategy game'), h('h1', { class: 'start-title' }, 'American Grand Strategy'),
        h('p', { class: 'start-lede' }, 'Win the White House in 2028, then govern: Congress, the West Wing, the press, the economy and the world, week by week, for as long as you serve.')),
      h('section', { class: 'start-card', 'aria-labelledby': 'st-new' },
        h('h2', { id: 'st-new' }, 'A new game'),
        h('fieldset', { class: 'start-field' }, h('legend', null, 'Which game'),
          h('div', { class: 'start-modes' }, ...MODES.map(m => radio('Which game', m.id === mode, m.label, m.detail, () => { mode = m.id; }, 'start-opt start-mode')))),
        ...(mode === 'CRISIS' ? crisisSetup() : people(o)),
        mode === 'GOVERNING_ONLY' ? h('p', { class: 'muted small start-note' }, 'The staff play the 2028 campaign for both sides; the result is computed once, and election night shows it before the transition begins.') : null,
        h('button', { class: 'btn primary start-go', type: 'button', disabled: mode !== 'CRISIS' && (!player || !opponent), onclick: go }, label),
        mode === 'CRISIS' ? h('div', { class: 'start-ic' },
          h('strong', null, 'Or the war game alone: Island Chain'), h('span', { class: 'muted small' }, ' · the Taiwan Strait without a presidency around it, as America or as China.'),
          h('span', { class: 'start-presets' },
            h('label', { class: 'small' }, 'Year ', h('select', { 'aria-label': 'Island Chain: the year', onchange: (e: Event) => { icYear = Number((e.target as HTMLSelectElement).value); } },
              ...[2029, 2030, 2031, 2032, 2033, 2034, 2035, 2036].map(y => h('option', { value: String(y), selected: y === icYear ? 'selected' : null }, String(y))))),
            h('span', { class: 'hz-steps', role: 'group', 'aria-label': 'Island Chain: your side' }, ...(['US', 'PRC'] as const).map(sd =>
              h('button', { class: 'btn small toggle' + (sd === icSide ? ' on' : ''), type: 'button', 'aria-pressed': String(sd === icSide), onclick: () => { icSide = sd; draw(); } },
                sd === 'US' ? 'America' : 'China')))),
          h('span', { class: 'start-presets', role: 'group', 'aria-label': 'Island Chain: how it starts' }, ...PRESETS.map((p, i) =>
            h('button', { class: 'btn small', type: 'button', onclick: () => act.islandChain({ year: icYear, preset: i, side: icSide }) }, p.label)))) : null),
      h('section', { class: 'start-card', 'aria-labelledby': 'st-load' },
        h('h2', { id: 'st-load' }, 'Continue'),
        h('label', { class: 'btn start-file' }, 'Open a saved game…',
          h('input', { type: 'file', accept: '.json,application/json', class: 'sr', onchange: async (e: Event) => {
            const f = (e.target as HTMLInputElement).files?.[0];
            if (f) act.loadFile(await f.text());
          } })),
        h('h3', { class: 'start-sub' }, 'Or start the Full Game at a moment'),
        h('ul', { class: 'start-quick' }, ...QUICK.map(q => h('li', null,
          h('button', { class: 'btn link', type: 'button', onclick: () => act.quick(q.save) }, q.label), h('span', { class: 'muted small' }, ' · ' + q.detail))))));
  }

  draw();
  return root;
}
