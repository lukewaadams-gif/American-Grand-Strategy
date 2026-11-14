// The war room's Polls tab (docs/CAMPAIGN_WAR_ROOM.md): the public polling over time and state by state, the groups in
// the national polling, and internal polls the campaign commissions: more accurate, at a price.
import type { CampaignView } from '../core/types.js';
import { h } from '../core/dom.js';
import { trendChart } from './charts.js';
import { cost } from './schedule.js';

export interface PollsActions { order(o: object): boolean; }

const lead = (x: number) => x === 0 ? 'tied' : `${x > 0 ? 'you +' : 'them +'}${Math.abs(x).toFixed(1)}`;

export function pollsTab(v: CampaignView, act: PollsActions): HTMLElement {
  const d = v.pollsDesk!;
  const last = v.polls[v.polls.length - 1];
  const close = [...v.states].filter(s => Math.abs(s.forPlayer) < 8).sort((a, b) => Math.abs(a.forPlayer) - Math.abs(b.forPlayer)).slice(0, 12);
  const groups = d.groups ?? {};
  return h('section', { class: 'tab-panel polls-tab', 'aria-label': 'Polls' },
    h('div', { class: 'panel-head' }, h('h2', null, 'Polls')),
    h('div', { class: 'cash-row' },
      h('div', null, h('span', { class: 'big-value' }, last ? lead(last.national * (v.player.party === 'D' ? 1 : -1)) : 'n/a'), h('span', { class: 'kpi-sub' }, ' nationally')),
      h('div', { class: 'cash-flow' }, h('span', { class: 'faint' }, `Public polls miss by about ${v.errorSd.toFixed(1)} points either way.`))),
    trendChart(v, 150),

    h('h3', { class: 'sub-head' }, 'Internal polling'),
    h('p', { class: 'muted small' }, `Your pollster’s numbers are close to the truth: about a point off, where the public polls can be several. ${cost(d.stateCost)} a state, ${cost(d.nationalCost)} for the nation; each once a week.`),
    h('div', { class: 'send-row' },
      h('button', { class: 'btn small', type: 'button', onclick: () => act.order({ type: 'InternalPoll', state: 'US' }) }, 'Poll the nation'),
      ...close.slice(0, 6).map(s => h('button', { class: 'btn small', type: 'button', title: `${s.name}: public polls ${lead(s.forPlayer)}`,
        onclick: () => act.order({ type: 'InternalPoll', state: s.code }) }, `Poll ${s.code}`))),
    d.internal.length ? h('table', { class: 'mini-table' },
      h('caption', { class: 'sr' }, 'Internal polls, newest first'),
      h('thead', null, h('tr', null, h('th', { scope: 'col' }, 'Where'), h('th', { scope: 'col' }, 'Internal'), h('th', { scope: 'col' }, 'Public'), h('th', { scope: 'col' }, 'When'))),
      h('tbody', null, ...d.internal.map(p => {
        const pub = p.state === 'US' ? (last ? last.national * (v.player.party === 'D' ? 1 : -1) : null) : v.states.find(s => s.code === p.state)?.forPlayer ?? null;
        return h('tr', { class: p.fresh ? '' : 'stale' },
          h('th', { scope: 'row' }, p.state === 'US' ? 'Nation' : p.state),
          h('td', null, `${lead(p.margin)} ±${p.sd.toFixed(1)}`),
          h('td', { class: 'faint' }, pub === null ? '' : lead(pub)),
          h('td', { class: 'faint' }, p.fresh ? 'this week' : p.date.slice(5)));
      }))) : h('p', { class: 'faint small' }, 'No internal polls yet.'),

    h('h3', { class: 'sub-head' }, 'The closest states'),
    h('table', { class: 'mini-table' },
      h('caption', { class: 'sr' }, 'The closest states in the public polls'),
      h('thead', null, h('tr', null, h('th', { scope: 'col' }, 'State'), h('th', { scope: 'col' }, 'Polls'), h('th', { scope: 'col' }, 'Since last'), h('th', { scope: 'col' }, 'Votes'))),
      h('tbody', null, ...close.map(s => h('tr', null, h('th', { scope: 'row' }, s.name), h('td', null, lead(s.forPlayer)),
        h('td', { class: s.change === null ? 'faint' : s.change >= 0 ? 'up' : 'down' }, s.change === null ? '' : `${s.change >= 0 ? '+' : '−'}${Math.abs(s.change).toFixed(1)}`),
        h('td', null, String(s.ev)))))),

    ...Object.entries(groups).map(([title, rows]) => h('div', null,
      h('h3', { class: 'sub-head' }, `The national polls, ${title.toLowerCase()}`),
      h('table', { class: 'mini-table' },
        h('caption', { class: 'sr' }, `The national polls ${title.toLowerCase()}`),
        h('thead', null, h('tr', null, h('th', { scope: 'col' }, 'Voters'), h('th', { scope: 'col' }, 'Share'), h('th', { scope: 'col' }, 'Margin'))),
        h('tbody', null, ...rows.map(r => h('tr', null, h('th', { scope: 'row' }, r.label), h('td', null, `${Math.round(r.share * 100)}%`),
          h('td', { class: r.margin >= 0 ? 'up' : 'down' }, lead(r.margin)))))))));
}
