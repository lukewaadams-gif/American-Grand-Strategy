// The ground game, as in Battleground 2028: offices hold organisers, organisers recruit and keep volunteers working,
// volunteers knock doors, call and text. The Field tab shows the whole programme; the state desk's Field tab opens
// offices, hires organisers and funds the state party in one state. Orders take effect when the turn runs.
import type { CampaignView, FieldState } from '../core/types.js';
import { h, s } from '../core/dom.js';
import { cost } from './schedule.js';

export interface FieldActions {
  order(order: object): boolean;
  /** Opens a state's field desk. */ open(code: string): void;
}

export function compact(n: number): string {
  if (n >= 1e6) return (n / 1e6).toFixed(n >= 1e7 ? 0 : 1) + 'M';
  if (n >= 1e3) return (n / 1e3).toFixed(n >= 1e4 ? 0 : 1) + 'K';
  return String(Math.round(n));
}

export function fieldTab(v: CampaignView, act: FieldActions): HTMLElement {
  const f = v.field!, t = f.totals, staffOn = v.staff?.field ?? false;
  const big = (label: string, value: string, title?: string) => h('div', { class: 'big-num', title: title ?? null },
    h('span', { class: 'kpi-label' }, label), h('span', { class: 'big-value' }, value));
  const names = new Map(v.states.map(x => [x.code, x.name]));
  const rows = [...f.states].filter(x => x.value > 0.3 || x.organizers > 0 || x.offices > 0)
    .sort((a, b) => b.value + b.organizers * 1e-3 - (a.value + a.organizers * 1e-3)).slice(0, 18);
  const table = h('table', { class: 'field-table' },
    h('caption', { class: 'sr' }, 'Ground game by state, most valuable states first'),
    h('thead', null, h('tr', null, h('th', { scope: 'col' }, 'State'), h('th', { scope: 'col', class: 'num' }, 'Offices'),
      h('th', { scope: 'col', class: 'num' }, 'Organisers'), h('th', { scope: 'col', class: 'num' }, 'Doors/wk'))),
    h('tbody', null, ...rows.map(x => h('tr', null,
      h('th', { scope: 'row' }, h('button', { type: 'button', class: 'link', onclick: () => act.open(x.code),
        'aria-label': `${names.get(x.code)}: open its field plan` }, names.get(x.code) ?? x.code)),
      h('td', { class: 'num' }, String(x.offices)),
      h('td', { class: 'num' + (x.organizerOrder ? ' pending' : '') }, String(x.organizers) + (x.organizerOrder ? ` ${x.organizerOrder > 0 ? '+' : ''}${x.organizerOrder}` : '')),
      h('td', { class: 'num' }, compact(x.doors))))));
  return h('section', { class: 'tab-panel', 'aria-label': 'Field' },
    h('div', { class: 'panel-head' }, h('h2', null, 'The ground game')),
    h('div', { class: 'big-nums' },
      big('Field offices', String(t.offices)),
      big('Organisers', compact(t.organizers) + (t.party >= 1 ? ' + ' + compact(t.party) : ''), 'Paid organisers, plus organisers your state-party transfers fund'),
      big('Volunteers', compact(t.volunteers)), big('Shifts this week', compact(t.shifts))),
    h('h3', { class: 'sub-head' }, 'Voter contact this week'),
    rowsOf([['Doors knocked', compact(t.doors)], ['Phone calls', compact(t.calls)], ['Texts sent', compact(t.texts)],
      ['Relational contacts (friends and family)', compact(t.relational)], ['Doors knocked to date', compact(t.doorsToDate)]]),
    doorsChart(f.doorsByTurn),
    h('h3', { class: 'sub-head' }, 'Ground game by state'), table,
    h('p', { class: 'hint-line' }, 'Organisers recruit and manage volunteers (about 45 active each); offices hold about 8 organisers. Volunteer shifts ramp up toward Election Day: the last weekend is the busiest of the campaign.'),
    h('div', { class: 'sched-controls' }, h('button', { class: 'btn toggle' + (staffOn ? ' on' : ''), type: 'button', 'aria-pressed': staffOn ? 'true' : 'false',
      title: 'When on, your field director hires, lays off and opens offices every Monday',
      onclick: () => act.order({ type: 'Staff', area: 'FIELD', on: !staffOn }) }, 'Staff runs the field: ', h('strong', null, staffOn ? 'on' : 'off'))),
    staffOn ? h('p', { class: 'hint-line' }, 'Your field director replans every Monday; turn it off to keep your own orders.') : null);
}

function rowsOf(rows: [string, string][]): HTMLElement {
  return h('dl', { class: 'fig-rows' }, ...rows.flatMap(([k, v]) => [h('dt', null, k), h('dd', null, v)]));
}

function doorsChart(series: number[]): HTMLElement {
  const n = series.length, max = Math.max(1000, ...series), W = 360, H = 70;
  const svg = s('svg', { viewBox: `0 0 ${W} ${H}`, class: 'mini-bars', role: 'img',
    'aria-label': n ? `Doors knocked per turn: from ${compact(series[0]!)} to ${compact(series[n - 1]!)}, most ${compact(max)}` : 'No doors knocked yet' });
  const bw = n ? Math.min(18, (W - 4) / n) : 0;
  series.forEach((d, i) => svg.append(s('rect', { x: 2 + i * bw, y: H - (d / max) * (H - 4), width: Math.max(1, bw - 2), height: (d / max) * (H - 4), class: 'bar' })));
  return h('figure', { class: 'chart mini' }, svg, h('figcaption', { class: 'chart-caption' }, 'Doors knocked, turn by turn'));
}

/** The state desk's Field tab: offices, organisers and transfers in one state, and what its volunteers did this week. */
export function fieldDesk(code: string, v: CampaignView, act: FieldActions): (HTMLElement | null)[] {
  const f = v.field!, x = f.states.find(s => s.code === code);
  const name = v.states.find(s => s.code === code)?.name ?? code;
  if (!x) return [h('p', { class: 'muted' }, 'No field data for this state.')];
  const send = (offices: number, organizers: number) => act.order({ type: 'Field', state: code, offices, organizers });
  const officesNow = x.offices + x.officeOrder, orgNow = x.organizers + x.organizerOrder;
  const capacity = officesNow * 8 + 2;
  return [
    h('h4', null, 'Field offices'), h('p', { class: 'cost-line' }, `${cost(x.openCost)} to open, ${cost(x.officeWeek)} a week`),
    stepper(`Field offices in ${name}`, String(officesNow),
      () => send(Math.max(-x.offices, x.officeOrder - 1), x.organizerOrder), () => send(x.officeOrder + 1, x.organizerOrder),
      x.officeOrder ? (x.officeOrder > 0 ? `opening ${x.officeOrder}` : `closing ${-x.officeOrder}`) : null),
    h('h4', null, 'Paid organisers'), h('p', { class: 'cost-line' }, `${cost(f.hireCost)} to hire, ${cost(x.organizerWeek)} a week each`),
    stepper(`Paid organisers in ${name}`, String(orgNow),
      () => send(x.officeOrder, Math.max(-x.organizers, x.organizerOrder - 5)), () => send(x.officeOrder, x.organizerOrder + 5),
      x.organizerOrder ? (x.organizerOrder > 0 ? `hiring ${x.organizerOrder}` : `laying off ${-x.organizerOrder}`) : null,
      h('button', { class: 'btn small', type: 'button', 'aria-label': `Hire 25 more organisers in ${name}`, onclick: () => send(x.officeOrder, x.organizerOrder + 25) }, '+25')),
    orgNow > capacity ? h('p', { class: 'warn-line' }, 'Not enough offices: organisers beyond about 8 an office work at 40%.') : null,
    h('h4', null, 'Transfers to the state party'),
    stepper(`Weekly transfer to the ${name} party`, cost(x.transfer) + ' a week',
      () => act.order({ type: 'Transfer', state: code, perWeek: Math.max(0, Math.round((x.transfer - 0.05) * 1000) / 1000) }),
      () => act.order({ type: 'Transfer', state: code, perWeek: Math.min(5, Math.round((x.transfer + 0.05) * 1000) / 1000) }),
      x.party >= 0.5 ? `funds about ${Math.round(x.party)} organisers` : null),
    h('p', { class: 'hint-line' }, 'The state party hires organisers with your money, with a lag, and helps candidates down the ballot.'),
    h('h4', null, 'This week’s ground game'),
    rowsOf([['Volunteers on the list', compact(x.volunteers)], ['Volunteer shifts', compact(x.shifts)], ['Doors knocked', compact(x.doors)],
      ['Phone calls', compact(x.calls)], ['Texts', compact(x.texts)], ['Relational contacts', compact(x.relational)], ['Doors to date', compact(x.doorsToDate)]]),
    reach('Persuasion reach', x.persuasion, 'How much of the electorate your volunteers have reached recently, against the most contact can do.'),
    reach('Get out the vote', x.gotv, 'Builds only in the last six weeks before the vote.'),
  ];
}

function stepper(label: string, value: string, minus: () => void, plus: () => void, note: string | null, extra?: HTMLElement): HTMLElement {
  return h('div', { class: 'stepper', role: 'group', 'aria-label': label },
    h('button', { class: 'btn small step', type: 'button', 'aria-label': 'Less: ' + label, onclick: minus }, '−'),
    h('span', { class: 'step-value', 'aria-live': 'polite' }, value),
    h('button', { class: 'btn small step', type: 'button', 'aria-label': 'More: ' + label, onclick: plus }, '+'),
    extra ?? null,
    note ? h('span', { class: 'step-note' }, note) : null);
}

function reach(label: string, frac: number, title: string): HTMLElement {
  return h('div', { class: 'proj-meter', title },
    h('span', { class: 'proj-label' }, label),
    h('span', { class: 'proj-bar', role: 'meter', 'aria-label': label, 'aria-valuemin': 0, 'aria-valuemax': 100, 'aria-valuenow': Math.round(frac * 100) },
      h('span', { class: 'proj-fill ok', style: `width:${Math.max(0, Math.min(1, frac)) * 100}%` })),
    h('strong', { class: 'proj-value' }, Math.round(frac * 100) + '%'));
}

/** Map layer: the player's volunteer shifts this week (colour) and doors knocked to date (label). */
export function shiftsOf(v: CampaignView, code: string): FieldState | undefined { return v.field?.states.find(s => s.code === code); }
