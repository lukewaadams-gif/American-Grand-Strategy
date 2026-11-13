// The start of a game: a new campaign (your party, your candidate, your opponent, the country's mood, how hard), a
// saved game from a file, or a quick start at one of the game's moments. The candidates are the fictional cast unless
// the real-person roster is turned on.
import { h } from './core/dom.js';
import { portrait, type Look } from './art/portrait.js';

export interface StartCandidate { id: string; name: string; home: string; charisma: number; debate: number; fundraising: number; policy: number;
  discipline: number; ideology: number; real: boolean; look?: Look | null }
export interface StartOptions { democrats: StartCandidate[]; republicans: StartCandidate[]; scenarios: { id: string; label: string; description: string }[];
  difficulties: { id: string; label: string; description: string }[] }
export interface NewGame { seed: number; party: 'DEMOCRAT' | 'REPUBLICAN'; player: string; opponent: string; scenario: string; difficulty: string; realRoster: boolean }

export interface StartActions {
  options(realRoster: boolean): StartOptions;
  newGame(g: NewGame): void;
  loadFile(text: string): void;
  /** Opens a save; with a command, gives it first (a crisis begins at once). */
  quick(save: string, then?: object): void;
  /** Island Chain on its own: the Taiwan crisis without a presidency. */
  islandChain(o: { year: number; preset: number; side: 'US' | 'PRC' }): void;
}

/** The in-depth crises, standalone: a governing presidency in September 2029 and the crisis at once (docs/CRISES.md). */
const CRISES = [
  { label: 'The Strait of Hormuz', detail: 'The Guards seize a tanker and Tehran threatens to close the strait: day by day, a fifth of the world’s oil.',
    presets: ['Base case', 'Optimistic', 'Pessimistic'], order: (p: number) => ({ type: 'Hormuz', order: { type: 'Begin', preset: p } }) },
];

const QUICK = [
  { save: 'saves/campaign.json', label: 'The campaign, week 9', detail: 'Battleground 2028’s headquarters at the height of the race.' },
  { save: 'saves/presidency.json', label: 'The first hundred days', detail: 'Inauguration week, with the flagship bill to pass.' },
  { save: 'saves/governing.json', label: 'Governing', detail: 'September 2029: Congress, the West Wing and the world are yours.' },
  { save: 'saves/secondterm.json', label: 'Four more years', detail: 'Just re-elected: the second term’s theme on the desk.' },
  { save: 'saves/succession.json', label: 'The race to succeed you', detail: 'Spring 2036: the primaries to choose your successor.' },
  { save: 'saves/taiwan.json', label: 'The Taiwan crisis', detail: 'September 2029: China’s coast guard closes on Taiwan, and the Situation Room counts the days.' },
];

function rating(c: StartCandidate): string {
  return `Charisma ${c.charisma}, debate ${c.debate}, money ${c.fundraising}, policy ${c.policy}, discipline ${c.discipline}`;
}

export function startScreen(act: StartActions): HTMLElement {
  // real people by default: the 2028 world is the real roster; the fictional cast is the alternative
  let real = true;
  let party: 'DEMOCRAT' | 'REPUBLICAN' = 'DEMOCRAT';
  let player = '', opponent = '', scenario = 'TOSS_UP', difficulty = 'STANDARD';
  let icYear = 2030, icSide: 'US' | 'PRC' = 'US';
  const root = h('main', { class: 'start', id: 'main' });

  function draw(): void {
    const o = act.options(real);
    const mine = party === 'DEMOCRAT' ? o.democrats : o.republicans, theirs = party === 'DEMOCRAT' ? o.republicans : o.democrats;
    if (!mine.some(c => c.id === player)) player = mine[0]?.id ?? '';
    if (!theirs.some(c => c.id === opponent)) opponent = theirs[0]?.id ?? '';
    const pick = (label: string, list: StartCandidate[], value: string, set: (v: string) => void) => h('fieldset', { class: 'start-field' },
      h('legend', null, label),
      h('div', { class: 'start-cands' }, ...list.map(c => h('label', { class: 'start-cand' + (c.id === value ? ' on' : '') },
        h('input', { type: 'radio', name: label, value: c.id, checked: c.id === value, onchange: () => { set(c.id); draw(); } }),
        portrait(c.id, list === o.democrats ? 'D' : 'R', 40, true, c.look),
        h('span', { class: 'start-cand-name' }, c.name + (c.real ? ' (real)' : '')),
        h('span', { class: 'muted small' }, `${c.home} · ${rating(c)}`)))));
    const choose = (label: string, items: { id: string; label: string; description: string }[], value: string, set: (v: string) => void) =>
      h('fieldset', { class: 'start-field' }, h('legend', null, label), ...items.map(i => h('label', { class: 'start-opt' },
        h('input', { type: 'radio', name: label, value: i.id, checked: i.id === value, onchange: () => { set(i.id); draw(); } }),
        h('span', null, h('strong', null, i.label), h('span', { class: 'muted small' }, ' · ' + i.description)))));
    root.replaceChildren(
      h('header', { class: 'start-head' }, h('div', { class: 'eyebrow' }, 'An American political strategy game'), h('h1', { class: 'start-title' }, 'American Grand Strategy'),
        h('p', { class: 'start-lede' }, 'Win the White House in 2028, then govern: Congress, the West Wing, the press, the economy and the world, week by week, for as long as you serve.')),
      h('section', { class: 'start-card', 'aria-labelledby': 'st-new' },
        h('h2', { id: 'st-new' }, 'A new campaign'),
        h('fieldset', { class: 'start-field' }, h('legend', null, 'Your party'),
          h('div', { class: 'cab-buttons', role: 'group', 'aria-label': 'Your party' }, ...(['DEMOCRAT', 'REPUBLICAN'] as const).map(p =>
            h('button', { class: 'btn toggle' + (p === party ? ' on' : ''), type: 'button', 'aria-pressed': String(p === party), onclick: () => { party = p; draw(); } },
              p === 'DEMOCRAT' ? 'Democrat' : 'Republican')))),
        pick('Your candidate', mine, player, v => { player = v; }),
        pick('Your opponent', theirs, opponent, v => { opponent = v; }),
        choose('The country', o.scenarios, scenario, v => { scenario = v; }),
        choose('Your opponent’s campaign', o.difficulties, difficulty, v => { difficulty = v; }),
        h('label', { class: 'start-opt' }, h('input', { type: 'checkbox', checked: real, onchange: (e: Event) => { real = (e.target as HTMLInputElement).checked; draw(); } }),
          h('span', null, 'Real people', h('span', { class: 'muted small' }, ' · the real candidates, senators, congressional leaders, cabinet prospects, justices and leaders abroad of 2028. Fictional newcomers appear from 2030. Off, a fictional cast throughout.'))),
        h('button', { class: 'btn primary start-go', type: 'button', disabled: !player || !opponent,
          onclick: () => act.newGame({ seed: Math.floor(Math.random() * 1e9), party, player, opponent, scenario, difficulty, realRoster: real }) }, 'Begin the campaign')),
      h('section', { class: 'start-card', 'aria-labelledby': 'st-load' },
        h('h2', { id: 'st-load' }, 'Continue'),
        h('label', { class: 'btn start-file' }, 'Open a saved game…',
          h('input', { type: 'file', accept: '.json,application/json', class: 'sr', onchange: async (e: Event) => {
            const f = (e.target as HTMLInputElement).files?.[0];
            if (f) act.loadFile(await f.text());
          } })),
        h('h3', { class: 'start-sub' }, 'Or start at a moment'),
        h('ul', { class: 'start-quick' }, ...QUICK.map(q => h('li', null,
          h('button', { class: 'btn link', type: 'button', onclick: () => act.quick(q.save) }, q.label), h('span', { class: 'muted small' }, ' · ' + q.detail)))),
        h('h3', { class: 'start-sub' }, 'Or face a crisis'),
        h('ul', { class: 'start-quick start-crises' }, ...CRISES.map(c => h('li', null,
          h('strong', null, c.label), h('span', { class: 'muted small' }, ' · ' + c.detail),
          h('span', { class: 'start-presets', role: 'group', 'aria-label': c.label + ': how it starts' }, ...c.presets.map((p, i) =>
            h('button', { class: 'btn small', type: 'button', onclick: () => act.quick('saves/governing.json', c.order(i)) }, p))))),
          h('li', { class: 'start-ic' },
            h('strong', null, 'Island Chain'), h('span', { class: 'muted small' }, ' · The Taiwan Strait on its own: weeks of warning, then the war by the hour. Command America, or China.'),
            h('span', { class: 'start-presets' },
              h('label', { class: 'small' }, 'Year ', h('select', { 'aria-label': 'Island Chain: the year', onchange: (e: Event) => { icYear = Number((e.target as HTMLSelectElement).value); } },
                ...[2029, 2030, 2031, 2032, 2033, 2034, 2035, 2036].map(y => h('option', { value: String(y), selected: y === icYear ? 'selected' : null }, String(y))))),
              h('span', { class: 'hz-steps', role: 'group', 'aria-label': 'Island Chain: your side' }, ...(['US', 'PRC'] as const).map(sd =>
                h('button', { class: 'btn small toggle' + (sd === icSide ? ' on' : ''), type: 'button', 'aria-pressed': String(sd === icSide), onclick: () => { icSide = sd; draw(); } },
                  sd === 'US' ? 'America' : 'China')))),
            h('span', { class: 'start-presets', role: 'group', 'aria-label': 'Island Chain: how it starts' }, ...['Base case', 'Optimistic', 'Pessimistic'].map((p, i) =>
              h('button', { class: 'btn small', type: 'button', onclick: () => act.islandChain({ year: icYear, preset: i, side: icSide }) }, p)))))));
  }

  draw();
  return root;
}
