// The campaign's headquarters, laid out like Battleground 2028's: the campaign's own controls in tabs on the left (the
// schedule first), the map in the middle with the race under it, and on the right the state desk (a state picked on the
// map) or the national picture. Everything on it comes from the engine's campaign view, which holds only what the
// player can see (this week's polls, never the true margins).
import type { CampaignView, NewsItem, StateView } from '../core/types.js';
import { countTo, h, reducedMotion } from '../core/dom.js';
import { chance, day, margin, money, signed } from '../core/format.js';
import { openMenu } from '../core/menu.js';
import { portrait } from '../art/portrait.js';
import { LAYERS, layer, type LayerId, usMap } from './map.js';
import { pathTo270, raceTo270, trendChart } from './charts.js';
import { quickVisit, scheduleTab, type ScheduleActions } from './schedule.js';
import { fieldDesk, fieldTab, type FieldActions } from './field.js';
import { adsDesk, moneyTab, type MoneyActions } from './money.js';

export type TabId = 'schedule' | 'money' | 'field' | 'message' | 'polls' | 'wire';
export type DeskTab = 'overview' | 'ads' | 'field';
/** BG's "Week in review": what the turn just run did, shown over the map until dismissed. */
export interface Digest { title: string; lines: string[]; }
export interface WarRoomState { layer: LayerId; selected: string | null; tab: TabId; picking: number | null; desk: DeskTab; digest: Digest | null; }
export interface WarRoomActions {
  runWeek(): void;
  openDecisions(): void;
  setLayer(id: LayerId): void;
  select(code: string | null): void;
  setTab(id: TabId): void;
  /** Sends a campaign order (Command.Campaign); false if refused. */
  order(order: object): boolean;
  pick(slot: number | null): void;
  toast(text: string): void;
  setDesk(tab: DeskTab): void;
  studio(): void;
  dismissDigest(): void;
}

const TABS: { id: TabId; label: string; ready: boolean }[] = [
  { id: 'schedule', label: 'Schedule', ready: true }, { id: 'money', label: 'Money', ready: true }, { id: 'field', label: 'Field', ready: true },
  { id: 'message', label: 'Message', ready: false }, { id: 'polls', label: 'Polls', ready: false }, { id: 'wire', label: 'Wire', ready: true },
];

/** Figures shown last time, so a new week's numbers count from the old ones and the map's colours cross-fade. */
const previous = { fills: new Map<string, string>(), kpis: new Map<string, number>() };

export function warRoom(v: CampaignView, st: WarRoomState, act: WarRoomActions): HTMLElement {
  const L = layer(st.layer);
  const last = v.polls[v.polls.length - 1], prev = v.polls[v.polls.length - 2];
  const selected = st.selected ? v.states.find(x => x.code === st.selected) ?? null : null;
  const sched: ScheduleActions = { order: act.order, pick: s => act.pick(s), picking: st.picking, selected: st.selected, toast: act.toast };
  const daily = v.turn?.daily ?? false;

  // ---- the bar along the top --------------------------------------------------------------------------------
  const kpi = (id: string, label: string, value: number, fmt: (x: number) => string, sub: string, subTone = '') => {
    const val = h('span', { class: 'kpi-value' }, fmt(value));
    const old = previous.kpis.get(id);
    if (old !== undefined && old !== value) countTo(val, old, value, fmt);
    previous.kpis.set(id, value);
    return h('div', { class: 'kpi', role: 'group', 'aria-label': `${label}: ${fmt(value)}, ${sub}` },
      h('span', { class: 'kpi-label' }, label), val, h('span', { class: 'kpi-sub ' + subTone }, sub));
  };
  const nat = last?.national ?? 0, natChange = last && prev ? last.national - prev.national : 0;
  const bar = h('header', { class: 'cw-bar' },
    h('div', { class: 'cw-brand' },
      h('span', { class: 'cw-wordmark' }, 'American Grand Strategy'),
      h('span', { class: 'cw-edition' }, daily ? `Campaign ${v.electionDay.slice(0, 4)} · Final stretch` : `Campaign ${v.electionDay.slice(0, 4)} · Week ${v.week} of ${v.weeks}`),
      h('span', { class: 'cw-date' }, daily ? `${v.turn!.label} · ${daysTo(v.date, v.electionDay)} to Election Day`
        : `${v.dateLabel} · ${v.weeksLeft} weeks to Election Day`)),
    h('div', { class: 'cw-kpis' },
      kpi('cash', 'Cash on hand', v.player.cash, money, `theirs about ${money(v.opponent.cash)}`),
      kpi('nat', 'National polls', nat, x => signed(x), last && prev ? `${signed(natChange)} since last poll` : 'first poll next week',
        Math.abs(natChange) < 0.05 ? '' : natChange > 0 ? 'up' : 'down'),
      kpi('ev', 'Electoral votes', last?.ev ?? 0, x => String(Math.round(x)), 'if every state poll is right'),
      kpi('win', 'Forecast', last?.win ?? 0.5, chance, 'from the polls alone')),
    h('div', { class: 'cw-actions' },
      v.pendingDecisions > 0 ? h('button', { class: 'btn danger', type: 'button', onclick: () => act.openDecisions(),
        title: 'Decisions waiting: the turn cannot run until they are answered' }, `Decisions (${v.pendingDecisions})`) : null,
      h('button', { class: 'btn primary', type: 'button', onclick: () => act.runWeek(), 'aria-keyshortcuts': 'Control+Enter',
        title: 'Both campaigns carry out their plans, a debate is held if one is due, and the polls update' },
        h('span', null, daily ? 'Run the day' : 'Run the week'), h('span', { class: 'hint' }, 'Ctrl+Enter'))));

  // ---- the left: the campaign's controls ---------------------------------------------------------------------
  const tabs = h('div', { class: 'side-tabs', role: 'tablist', 'aria-label': 'Campaign' },
    ...TABS.map((t, i) => h('button', { type: 'button', role: 'tab', id: 'tab-' + t.id, class: 'side-tab' + (t.id === st.tab ? ' on' : '') + (t.ready ? '' : ' soon'),
      'aria-selected': t.id === st.tab ? 'true' : 'false', 'aria-controls': 'tab-panel', tabindex: t.id === st.tab ? 0 : -1,
      'aria-disabled': t.ready ? null : 'true', title: t.ready ? `${t.label} (${i + 1})` : `${t.label}: in the next preview`,
      onclick: () => { if (t.ready) act.setTab(t.id); },
      onkeydown: ((e: KeyboardEvent) => {
        const ready = TABS.filter(x => x.ready), k = ready.findIndex(x => x.id === st.tab);
        if (e.key === 'ArrowRight' || e.key === 'ArrowDown') { e.preventDefault(); act.setTab(ready[(k + 1) % ready.length]!.id); }
        if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') { e.preventDefault(); act.setTab(ready[(k - 1 + ready.length) % ready.length]!.id); }
      }) as EventListener }, t.label)));
  const fieldAct: FieldActions = { order: act.order, open: c => { act.setDesk('field'); act.select(c); } };
  const moneyAct: MoneyActions = { order: act.order, studio: act.studio };
  const panel = st.tab === 'wire' ? wireList(v) : st.tab === 'field' && v.field ? fieldTab(v, fieldAct)
    : st.tab === 'money' && v.money ? moneyTab(v, moneyAct) : scheduleTab(v, sched);
  const left = h('aside', { class: 'cw-left' }, tabs,
    h('div', { class: 'side-panel', id: 'tab-panel', role: 'tabpanel', 'aria-labelledby': 'tab-' + st.tab, tabindex: 0 }, panel));

  // ---- the map ----------------------------------------------------------------------------------------------
  const layerTabs = h('div', { class: 'layer-tabs', role: 'group', 'aria-label': 'Map layer' },
    ...LAYERS.map(l => h('button', { type: 'button', class: 'tab' + (l.id === st.layer ? ' on' : ''), 'aria-pressed': l.id === st.layer ? 'true' : 'false',
      title: l.question, onclick: () => act.setLayer(l.id) }, l.label)));
  const readout = h('div', { class: 'readout', 'aria-live': 'polite' });
  const showReadout = (code: string | null) => {
    const x = code ? v.states.find(s => s.code === code) : null;
    readout.replaceChildren(...(x ? [h('strong', null, x.name), ` · ${x.ev} votes · ${L.spoken(x, v)}`]
      : [h('span', { class: 'faint' }, st.picking !== null ? 'Pick a state for the event.' : 'Point at a state, or Tab to the map and use the arrow keys. Right-click to send the candidate.')]));
  };
  showReadout(null);
  const map = usMap({ view: v, layer: L, selected: st.selected,
    onSelect: c => { if (st.picking !== null) { placePicked(v, st.picking, c, act); return; } act.select(c === st.selected ? null : c); },
    onHover: showReadout,
    onContext: (c, x, y) => openMenu({ x, y }, `Send ${v.player.name.split(' ').slice(-1)[0]} to ${v.states.find(s => s.code === c)?.name}`, quickVisit(v, c, sched)) });
  crossFade(map);
  const lg = L.legend(v);
  const legend = h('div', { class: 'legend', 'aria-hidden': 'true' },
    h('div', { class: 'legend-ramp', style: `background:linear-gradient(to right, ${lg.stops.join(', ')})` }),
    h('div', { class: 'legend-labels' }, h('span', null, lg.left), h('span', null, lg.mid), h('span', null, lg.right)));
  const breaking = latestBreaking(v);
  const chyron = breaking ? h('div', { class: 'chyron', role: 'status' },
    h('span', { class: 'chyron-flag tone-' + (breaking.tone ?? 'INFO').toLowerCase() }, breaking.headline ?? 'News'),
    h('span', { class: 'chyron-text' }, breaking.text)) : null;
  const picking = st.picking !== null ? (v.schedule ?? [])[st.picking] : null;
  const banner = picking ? h('div', { class: 'pick-banner', role: 'status' },
    `Click a state for the ${['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'][picking.day]} ${['morning', 'afternoon', 'evening'][picking.part]} event`,
    h('button', { class: 'btn quiet small', type: 'button', onclick: () => act.pick(null) }, 'Cancel (Esc)')) : null;
  const digest = st.digest ? h('div', { class: 'digest', role: 'status' },
    h('div', { class: 'digest-head' }, h('strong', null, st.digest.title),
      h('button', { class: 'btn quiet close', type: 'button', 'aria-label': 'Dismiss the review', onclick: () => act.dismissDigest() }, '\u00d7')),
    ...st.digest.lines.map(l => h('p', null, l))) : null;
  const mapCard = h('section', { class: 'card map-card' + (picking ? ' picking' : ''), 'aria-label': 'The map' },
    h('div', { class: 'card-head' }, h('h2', null, L.question), layerTabs),
    h('div', { class: 'map-stage' }, banner, digest, map),
    chyron,
    h('div', { class: 'map-foot' }, legend, readout));
  const race = h('section', { class: 'card race-card', 'aria-label': 'The race for 270' }, h('h2', null, 'The race for 270'), raceTo270(v));
  const path = h('section', { class: 'card path-card', 'aria-label': 'Path to 270' }, h('h2', null, 'Path to 270'), pathTo270(v, st.selected, c => act.select(c)));

  // ---- the right: a state, or the nation ---------------------------------------------------------------------
  const right = h('aside', { class: 'cw-right', 'aria-label': selected ? `${selected.name} in detail` : 'The national picture' },
    ...(selected ? [stateDesk(selected, v, st, act, sched, fieldAct, moneyAct)] : national(v, last)));

  return h('div', { class: 'cw', 'data-world': 'campaign' },
    bar,
    h('main', { class: 'cw-grid', id: 'main', tabindex: -1 },
      left,
      h('div', { class: 'cw-centre' }, mapCard, race, path),
      right));
}

function daysTo(from: string, to: string): string {
  const n = Math.round((Date.parse(to) - Date.parse(from)) / 86400000);
  return n === 1 ? '1 day' : `${n} days`;
}

function placePicked(v: CampaignView, slot: number, code: string, act: WarRoomActions): void {
  const s = (v.schedule ?? [])[slot];
  act.pick(null);
  if (!s) return;
  if (act.order({ type: 'Slot', slot, activity: s.activity, state: code, param: s.param }))
    act.toast(`Moved to ${v.states.find(x => x.code === code)?.name ?? code}.`);
}

function national(v: CampaignView, last: CampaignView['polls'][number] | undefined): HTMLElement[] {
  const face = (side: typeof v.player, mine: boolean) => h('div', { class: 'face ' + side.party.toLowerCase() },
    portrait(side.id, side.party, 88, true, side.look),
    h('div', { class: 'face-text' },
      h('span', { class: 'face-role' }, mine ? 'You' : 'Your opponent'),
      h('span', { class: 'face-name' }, side.name),
      h('span', { class: 'face-meta' }, `${side.partyName} · ${side.home}`)));
  const matchup = h('section', { class: 'card matchup', 'aria-label': 'The matchup' },
    h('div', { class: 'faces' }, face(v.player, true), h('span', { class: 'vs', 'aria-hidden': 'true' }, 'vs'), face(v.opponent, false)));
  const trend = h('section', { class: 'card', 'aria-label': 'The national race' }, h('h2', null, 'The national race'), trendChart(v, 190));
  const movers = h('section', { class: 'card', 'aria-label': 'What moved the race' }, h('h2', null, 'What moved the race'),
    last && last.movers.length ? h('ul', { class: 'movers' }, ...last.movers.slice(0, 4).map(m => h('li', null, m))) : h('p', { class: 'faint' }, 'The first poll arrives after the first week.'),
    h('h3', null, 'Your opponent'), h('p', null, h('strong', null, v.opponentPlan)), h('p', { class: 'muted' }, v.opponentReasoning));
  return [matchup, trend, movers];
}

function stateDesk(x: StateView, v: CampaignView, st: WarRoomState, act: WarRoomActions, sched: ScheduleActions, fieldAct: FieldActions,
                   moneyAct: MoneyActions): HTMLElement {
  const duel = (label: string, me: number, them: number, unit: string) => {
    const max = Math.max(me, them, 0.01);
    return h('div', { class: 'duel', role: 'img', 'aria-label': `${label}: you ${me.toFixed(1)}${unit}, them ${them.toFixed(1)}${unit}` },
      h('span', { class: 'duel-label' }, label),
      h('div', { class: 'duel-bars' },
        h('div', { class: 'duel-bar me ' + v.player.party.toLowerCase(), style: `width:${(me / max) * 100}%` }),
        h('div', { class: 'duel-bar them ' + v.opponent.party.toLowerCase(), style: `width:${(them / max) * 100}%` })),
      h('span', { class: 'duel-nums' }, `${me.toFixed(1)} · ${them.toFixed(1)}`));
  };
  const deskTab = (id: DeskTab | null, label: string) => h('button', { type: 'button', class: 'tab' + (id === st.desk ? ' on' : '') + (id ? '' : ' soon'),
    'aria-pressed': id === st.desk ? 'true' : 'false', 'aria-disabled': id ? null : 'true', title: id ? null : `${label}: in the next preview`,
    onclick: () => { if (id) act.setDesk(id); } }, label);
  const deskTabs = h('div', { class: 'desk-tabs', role: 'group', 'aria-label': 'State desk' },
    deskTab('overview', 'Overview'), deskTab(v.ads ? 'ads' : null, 'Ads'), deskTab(v.field ? 'field' : null, 'Field'), deskTab(null, 'Voters'));
  const head = h('div', { class: 'state-head' },
    h('div', null, h('h3', null, x.name), h('span', { class: 'faint' }, `${x.ev} electoral votes · 2024: ${margin(x.baseline)}`)),
    h('button', { class: 'btn quiet close', type: 'button', onclick: () => act.select(null), 'aria-label': `Close ${x.name}`, title: 'Close (Esc)' }, '×'));
  if (st.desk === 'field' && v.field) return h('section', { class: 'card state-desk', 'aria-label': `${x.name}: field` }, head, deskTabs, ...fieldDesk(x.code, v, fieldAct));
  if (st.desk === 'ads' && v.ads) return h('section', { class: 'card state-desk', 'aria-label': `${x.name}: ads` }, head, deskTabs, ...adsDesk(x.code, v, moneyAct));
  const send = quickVisit(v, x.code, sched);
  return h('section', { class: 'card state-desk', 'aria-label': `${x.name} in detail` },
    head,
    deskTabs,
    h('div', { class: 'state-figs' },
      fig('Polls', margin(x.margin)), fig('Your chance', chance(x.win)), fig('Since last poll', x.change === null ? 'n/a' : signed(x.change))),
    h('h4', null, 'You · them'),
    duel('Ad money', x.adsMe, x.adsThem, 'M'), duel('Field money', x.fieldMe, x.fieldThem, 'M'), duel('Visits', x.visitsMe, x.visitsThem, ''),
    h('p', { class: 'hint-line' }, 'Money spent here over about the last four weeks ($M), and candidate visits still felt.'),
    h('h4', null, `Send ${v.player.name.split(' ').slice(-1)[0]} here`),
    h('div', { class: 'send-row' }, ...send.map(it => h('button', { class: 'btn small', type: 'button', title: it.disabled ?? `${it.label}, ${it.right}`,
      'aria-disabled': it.disabled ? 'true' : null, onclick: () => { if (!it.disabled) it.pick(); } }, it.label))),
    h('p', { class: 'hint-line' }, 'Each goes into the next free slot of the schedule.'));
}

const fig = (label: string, value: string) => h('div', { class: 'state-fig' }, h('span', { class: 'kpi-label' }, label), h('span', { class: 'fig-value' }, value));

function wireList(v: CampaignView): HTMLElement {
  return h('section', { class: 'tab-panel wire', 'aria-label': 'The wire' }, h('div', { class: 'panel-head' }, h('h2', null, 'The wire')),
    h('ol', { class: 'wire-list' }, ...v.news.slice(0, 40).map(n => h('li', { class: n.headline ? 'notable' : '' },
      h('time', { datetime: n.date ?? '' }, n.date ? day(n.date) : ''),
      h('div', null, n.headline ? h('span', { class: 'wire-tag tone-' + (n.tone ?? 'INFO').toLowerCase() }, n.headline) : null, h('span', null, n.text))))));
}

/** The most recent notable item from the latest turn, for the chyron. */
function latestBreaking(v: CampaignView): NewsItem | null {
  const latest = v.news.find(n => n.date)?.date;
  return v.news.find(n => n.headline && n.date === latest) ?? null;
}

/** Map colours fade from last turn's to this turn's (no motion under reduced motion). */
function crossFade(map: SVGSVGElement): void {
  const paths = [...map.querySelectorAll<SVGPathElement>('path.state')];
  const next = new Map(paths.map(p => [p.dataset['code']!, p.getAttribute('fill')!]));
  if (!reducedMotion() && previous.fills.size) {
    for (const p of paths) { const old = previous.fills.get(p.dataset['code']!); if (old) p.style.fill = old; }
    requestAnimationFrame(() => requestAnimationFrame(() => { for (const p of paths) p.style.fill = next.get(p.dataset['code']!)!; }));
  }
  previous.fills = next;
}

/** Forgets last turn's figures (a new game, a load). */
export function resetWarRoomMemory(): void { previous.fills.clear(); previous.kpis.clear(); }
