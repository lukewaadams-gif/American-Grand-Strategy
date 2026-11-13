// The campaign's home: a war room in the style of a network decision desk. Everything on it comes from the engine's
// campaign view, which holds only what the player can see (this week's polls, never the true margins).
import type { CampaignView, NewsItem, StateView } from '../core/types.js';
import { countTo, h, reducedMotion } from '../core/dom.js';
import { chance, day, margin, money, signed } from '../core/format.js';
import { portrait } from '../art/portrait.js';
import { LAYERS, layer, type LayerId, usMap } from './map.js';
import { pathTo270, raceTo270, trendChart } from './charts.js';

export interface WarRoomState { layer: LayerId; selected: string | null; }
export interface WarRoomActions {
  runWeek(): void;
  openDecisions(): void;
  setLayer(id: LayerId): void;
  select(code: string | null): void;
}

/** Figures shown last time, so a new week's numbers count from the old ones and the map's colours cross-fade. */
const previous = { fills: new Map<string, string>(), kpis: new Map<string, number>() };

export function warRoom(v: CampaignView, st: WarRoomState, act: WarRoomActions): HTMLElement {
  const L = layer(st.layer);
  const last = v.polls[v.polls.length - 1], prev = v.polls[v.polls.length - 2];
  const selected = st.selected ? v.states.find(x => x.code === st.selected) ?? null : null;

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
      h('span', { class: 'cw-edition' }, `Campaign ${v.electionDay.slice(0, 4)} · Week ${v.week} of ${v.weeks}`),
      h('span', { class: 'cw-date' }, `${v.dateLabel} · ${v.weeksLeft} weeks to Election Day`)),
    h('div', { class: 'cw-kpis' },
      kpi('cash', 'Cash on hand', v.player.cash, money, `theirs about ${money(v.opponent.cash)}`),
      kpi('nat', 'National polls', nat, x => signed(x), last && prev ? `${signed(natChange)} this week` : 'first poll next week',
        Math.abs(natChange) < 0.05 ? '' : natChange > 0 ? 'up' : 'down'),
      kpi('ev', 'Electoral votes', last?.ev ?? 0, x => String(Math.round(x)), 'if every state poll is right'),
      kpi('win', 'Forecast', last?.win ?? 0.5, chance, 'from the polls alone')),
    h('div', { class: 'cw-actions' },
      v.pendingDecisions > 0 ? h('button', { class: 'btn danger', type: 'button', onclick: () => act.openDecisions(),
        title: 'Decisions waiting: the week cannot run until they are answered' }, `Decisions (${v.pendingDecisions})`) : null,
      h('button', { class: 'btn primary', type: 'button', onclick: () => act.runWeek(), 'aria-keyshortcuts': 'Control+Enter',
        title: 'Both campaigns carry out their plans, a debate is held if one is due, and the polls update' },
        h('span', null, 'Run the week'), h('span', { class: 'hint' }, 'Ctrl+Enter'))));

  // ---- the map ----------------------------------------------------------------------------------------------
  const tabs = h('div', { class: 'layer-tabs', role: 'group', 'aria-label': 'Map layer' },
    ...LAYERS.map(l => h('button', { type: 'button', class: 'tab' + (l.id === st.layer ? ' on' : ''), 'aria-pressed': l.id === st.layer ? 'true' : 'false',
      title: l.question, onclick: () => act.setLayer(l.id) }, l.label)));
  const readout = h('div', { class: 'readout', 'aria-live': 'polite' });
  const showReadout = (code: string | null) => {
    const x = code ? v.states.find(s => s.code === code) : null;
    readout.replaceChildren(...(x ? [h('strong', null, x.name), ` · ${x.ev} votes · ${L.spoken(x, v)}`] : [h('span', { class: 'faint' }, 'Point at a state, or Tab to the map and use the arrow keys.')]));
  };
  showReadout(null);
  const map = usMap({ view: v, layer: L, selected: st.selected, onSelect: c => act.select(c === st.selected ? null : c), onHover: showReadout });
  crossFade(map);
  const lg = L.legend(v);
  const legend = h('div', { class: 'legend', 'aria-hidden': 'true' },
    h('div', { class: 'legend-ramp', style: `background:linear-gradient(to right, ${lg.stops.join(', ')})` }),
    h('div', { class: 'legend-labels' }, h('span', null, lg.left), h('span', null, lg.mid), h('span', null, lg.right)));
  const breaking = latestBreaking(v);
  const chyron = breaking ? h('div', { class: 'chyron', role: 'status' },
    h('span', { class: 'chyron-flag tone-' + (breaking.tone ?? 'INFO').toLowerCase() }, breaking.headline ?? 'News'),
    h('span', { class: 'chyron-text' }, breaking.text)) : null;
  const mapCard = h('section', { class: 'card map-card', 'aria-label': 'The map' },
    h('div', { class: 'card-head' }, h('h2', null, L.question), tabs),
    h('div', { class: 'map-wrap' + (selected ? ' with-detail' : '') }, h('div', { class: 'map-stage' }, map), selected ? stateCard(selected, v, act) : null),
    chyron,
    h('div', { class: 'map-foot' }, legend, readout));

  // ---- the side ---------------------------------------------------------------------------------------------
  const face = (side: typeof v.player, mine: boolean) => h('div', { class: 'face ' + side.party.toLowerCase() },
    portrait(side.id, side.party, 96),
    h('div', { class: 'face-text' },
      h('span', { class: 'face-role' }, mine ? 'You' : 'Your opponent'),
      h('span', { class: 'face-name' }, side.name),
      h('span', { class: 'face-meta' }, `${side.partyName} · ${side.home}`)));
  const matchup = h('section', { class: 'card matchup', 'aria-label': 'The matchup' },
    h('div', { class: 'faces' }, face(v.player, true), h('span', { class: 'vs', 'aria-hidden': 'true' }, 'vs'), face(v.opponent, false)),
    raceTo270(v));
  const trend = h('section', { class: 'card', 'aria-label': 'The national race' }, h('h2', null, 'The national race'), trendChart(v, 200));
  const movers = h('section', { class: 'card', 'aria-label': 'What moved the race' }, h('h2', null, 'What moved the race'),
    last && last.movers.length ? h('ul', { class: 'movers' }, ...last.movers.slice(0, 4).map(m => h('li', null, m))) : h('p', { class: 'faint' }, 'The first poll arrives after the first week.'),
    h('h3', null, 'Your opponent'), h('p', null, h('strong', null, v.opponentPlan)), h('p', { class: 'muted' }, v.opponentReasoning));

  // ---- under the map ----------------------------------------------------------------------------------------
  const path = h('section', { class: 'card path-card', 'aria-label': 'Path to 270' }, h('h2', null, 'Path to 270'), pathTo270(v, st.selected, c => act.select(c)));
  const wire = h('section', { class: 'card wire', 'aria-label': 'The wire' }, h('h2', null, 'The wire'),
    h('ol', { class: 'wire-list' }, ...v.news.slice(0, 14).map(n => h('li', { class: n.headline ? 'notable' : '' },
      h('time', { datetime: n.date ?? '' }, n.date ? day(n.date) : ''),
      h('div', null, n.headline ? h('span', { class: 'wire-tag tone-' + (n.tone ?? 'INFO').toLowerCase() }, n.headline) : null, h('span', null, n.text))))));

  return h('div', { class: 'cw', 'data-world': 'campaign' },
    bar,
    h('main', { class: 'cw-grid', id: 'main', tabindex: -1 },
      h('div', { class: 'cw-left' }, mapCard, path, wire),
      h('div', { class: 'cw-right' }, matchup, trend, movers)));
}

function stateCard(x: StateView, v: CampaignView, act: WarRoomActions): HTMLElement {
  const duel = (label: string, me: number, them: number) => {
    const max = Math.max(me, them, 0.01);
    return h('div', { class: 'duel', role: 'img', 'aria-label': `${label}: you ${me.toFixed(1)}, them ${them.toFixed(1)}` },
      h('span', { class: 'duel-label' }, label),
      h('div', { class: 'duel-bars' },
        h('div', { class: 'duel-bar me ' + v.player.party.toLowerCase(), style: `width:${(me / max) * 100}%` }),
        h('div', { class: 'duel-bar them ' + v.opponent.party.toLowerCase(), style: `width:${(them / max) * 100}%` })),
      h('span', { class: 'duel-nums' }, `${me.toFixed(1)} · ${them.toFixed(1)}`));
  };
  return h('aside', { class: 'state-card', 'aria-label': `${x.name} in detail` },
    h('div', { class: 'state-head' },
      h('div', null, h('h3', null, x.name), h('span', { class: 'faint' }, `${x.ev} electoral votes · 2024: ${margin(x.baseline)}`)),
      h('button', { class: 'btn quiet close', type: 'button', onclick: () => act.select(null), 'aria-label': `Close ${x.name}`, title: 'Close (Esc)' }, '×')),
    h('div', { class: 'state-figs' },
      fig('Polls', margin(x.margin)), fig('Your chance', chance(x.win)), fig('Since last week', x.change === null ? 'n/a' : signed(x.change))),
    h('h4', null, 'You · them'),
    duel('Ad pressure', x.adsMe, x.adsThem), duel('Field', x.fieldMe, x.fieldThem), duel('Visits', x.visitsMe, x.visitsThem));
}

const fig = (label: string, value: string) => h('div', { class: 'state-fig' }, h('span', { class: 'kpi-label' }, label), h('span', { class: 'fig-value' }, value));

/** The most recent notable item from the latest week, for the chyron. */
function latestBreaking(v: CampaignView): NewsItem | null {
  const latest = v.news.find(n => n.date)?.date;
  return v.news.find(n => n.headline && n.date === latest) ?? null;
}

/** Map colours fade from last week's to this week's (no motion under reduced motion). */
function crossFade(map: SVGSVGElement): void {
  const paths = [...map.querySelectorAll<SVGPathElement>('path.state')];
  const next = new Map(paths.map(p => [p.dataset['code']!, p.getAttribute('fill')!]));
  if (!reducedMotion() && previous.fills.size) {
    for (const p of paths) { const old = previous.fills.get(p.dataset['code']!); if (old) p.style.fill = old; }
    requestAnimationFrame(() => requestAnimationFrame(() => { for (const p of paths) p.style.fill = next.get(p.dataset['code']!)!; }));
  }
  previous.fills = next;
}

/** Forgets last week's figures (a new game, a load). */
export function resetWarRoomMemory(): void { previous.fills.clear(); previous.kpis.clear(); }
