// The candidate's week, as in Battleground 2028: seven days of morning, afternoon and evening events. A slot opens the
// activity menu; its state button moves the event (pick a state on the map); auto-fill, clear and the scheduler toggle
// sit under the grid with the week's stamina and costs. In the final stretch the week is played a day at a time.
import type { ActivityInfo, ActivityKind, CampaignView, Slot } from '../core/types.js';
import { h } from '../core/dom.js';
import { day } from '../core/format.js';
import { openMenu, type MenuItem } from '../core/menu.js';

export interface ScheduleActions {
  /** Sends a campaign order; returns false if the engine refused it (the reason is shown). */
  order(order: object): boolean;
  /** Arms the map: the next state clicked becomes this slot's location. */
  pick(slot: number): void;
  picking: number | null;
  /** The state chosen on the map, if any: where a new local event goes. */
  selected: string | null;
  toast(text: string): void;
}

export const DAY = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
export const PART = ['Morning', 'Afternoon', 'Evening'];

/** The activity families' colours (a strip on each card; muted for the desk, all 4.5:1 on the panels). */
export const KIND_COLOUR: Record<ActivityKind, string> = {
  none: '#3a4554', crowd: '#d9a441', persuade: '#3fb8a5', money: '#57c48f', media: '#a88be0', groups: '#6b9fe0', prep: '#e08a5c', rest: '#8a94a8',
};

/** Money in $M as a person reads it: "$2.5K", "$350K", "$1.2M"; nothing is "free" that costs anything. */
export function cost(m: number): string {
  if (m <= 0) return 'free';
  if (m < 0.001) return '$' + Math.round(m * 1e6);
  if (m < 0.001) return '$' + Math.round(m * 1e6);
  if (m < 0.01) return '$' + (m * 1000).toFixed(1).replace(/\.0$/, '') + 'K';
  return m < 1 ? '$' + Math.round(m * 1000) + 'K' : '$' + m.toFixed(1) + 'M';
}

const where = (s: Slot) => `${DAY[s.day]} ${PART[s.part]!.toLowerCase()}`;

export function scheduleTab(v: CampaignView, act: ScheduleActions): HTMLElement {
  const t = v.turn!, trail = v.trail!, staff = v.staff!, acts = new Map((v.activities ?? []).map(a => [a.id, a]));
  const grid = h('div', { class: 'sched-grid', role: 'table', 'aria-label': t.daily ? 'This week, played a day at a time' : 'The candidate’s week' },
    h('div', { class: 'sched-row head', role: 'row' }, h('span', { role: 'columnheader' }, h('span', { class: 'sr' }, 'Day')),
      ...PART.map(p => h('span', { role: 'columnheader', class: 'sched-part' }, p))));
  for (let d = 0; d < 7; d++) {
    const slots = (v.schedule ?? []).slice(d * 3, d * 3 + 3);
    const today = t.daily && d === t.day, past = slots[0]?.past ?? false;
    const date = slots[0]?.date ?? v.date;
    const vote = date === v.electionDay;
    grid.append(h('div', { class: 'sched-row' + (today ? ' today' : '') + (past ? ' past' : ''), role: 'row' },
      h('span', { class: 'sched-day', role: 'rowheader' },
        h('span', { class: 'sched-dayname' }, vote ? 'VOTE' : DAY[d]!.slice(0, 3)),
        h('span', { class: 'sched-date' }, today ? 'Today' : day(date))),
      ...slots.map(s => card(s, acts, v, act))));
  }

  const after = trail.staminaAfter, low = after < 40;
  const proj = h('div', { class: 'sched-proj' },
    meter(t.daily ? 'Stamina after today' : 'Stamina after this week', after / 100, `${Math.round(after)}%`, low ? 'bad' : 'ok',
      `Now ${Math.round(trail.stamina)}%. Below 35 every event loses effect; below 40 gaffes are likelier. Rest restores it.`),
    h('div', { class: 'proj-row' }, h('span', null, t.daily ? 'Event costs today' : 'Event costs'), h('strong', null, cost(trail.eventCosts))),
    meter('Debate preparation', Math.min(1, trail.prep / 100), String(Math.round(trail.prep)), 'prep',
      'Prep sessions build it with diminishing returns; it fades between debates and drops after one.'),
    trail.nextDebate ? h('p', { class: 'proj-note' }, `Next debate: ${day(trail.nextDebate)}`) : null);

  const controls = h('div', { class: 'sched-controls' },
    h('button', { class: 'btn', type: 'button', title: 'Your scheduler fills the empty slots', onclick: () => act.order({ type: 'FillSchedule' }) }, 'Auto-fill'),
    h('button', { class: 'btn', type: 'button', title: t.daily ? 'Clear today and the rest of the week' : 'Clear the whole week',
      onclick: () => act.order({ type: 'ClearSchedule' }) }, 'Clear'),
    h('button', { class: 'btn toggle' + (staff.schedule ? ' on' : ''), type: 'button', 'aria-pressed': staff.schedule ? 'true' : 'false',
      title: 'When on, your scheduler rebuilds the week every Monday',
      onclick: () => act.order({ type: 'Staff', area: 'SCHEDULE', on: !staff.schedule }) },
      'Staff runs the schedule: ', h('strong', null, staff.schedule ? 'on' : 'off')));

  return h('section', { class: 'tab-panel', 'aria-label': 'Schedule' },
    h('div', { class: 'panel-head' },
      h('h2', null, t.daily ? 'The final stretch: one day at a time' : 'The candidate’s week'),
      h('span', { class: 'panel-sub' }, 'Now in ', h('strong', null, trail.locationName))),
    grid, controls,
    staff.schedule ? h('p', { class: 'hint-line' }, 'Your scheduler rebuilds the week every Monday; turn it off to keep your own plan.') : null,
    proj);
}

function card(s: Slot, acts: Map<string, ActivityInfo>, v: CampaignView, act: ScheduleActions): HTMLElement {
  const a = acts.get(s.activity), kind: ActivityKind = a?.kind ?? 'none', colour = KIND_COLOUR[kind];
  const label = a ? a.short : '';
  const desc = !a ? 'nothing planned' : `${a.label}${s.state ? ' in ' + s.stateName : ''}${s.paramName ? ' (' + s.paramName + ')' : ''}`;
  const el = h('div', { class: 'slot' + (s.past ? ' past' : '') + (act.picking === s.slot ? ' picking' : '') + (a ? '' : ' empty') + (s.travel ? ' travel' : ''), role: 'cell',
    style: `--kind:${colour}` });
  if (s.past) {
    el.append(h('span', { class: 'slot-short' }, label || '–'), h('span', { class: 'slot-sub' }, a ? 'done' : ''));
    el.setAttribute('aria-label', `${where(s)}: ${desc}, done`);
    return el;
  }
  const main = h('button', { type: 'button', class: 'slot-main', 'aria-label': `${where(s)}: ${desc}. Change`,
    title: a ? `${a.label}\n${a.description}` : 'Schedule an event', onclick: () => activityMenu(main, s, v, act) },
    a ? h('span', { class: 'slot-short' }, label) : h('span', { class: 'slot-plus', 'aria-hidden': 'true' }, '+'));
  el.append(main);
  if (a && s.activity === 'PODCAST') {
    el.append(h('button', { type: 'button', class: 'slot-param', 'aria-label': `${where(s)} show: ${s.paramName}. Choose the show`,
      onclick: (e: Event) => choiceMenu(e.currentTarget as HTMLElement, s, a, s.state, act) }, s.paramName ?? ''));
  } else if (a && a.local && s.state) {
    el.append(h('button', { type: 'button', class: 'slot-state', 'aria-label': `Move the ${where(s)} ${a.label.toLowerCase()}: now in ${s.stateName}. Pick a state on the map`,
      title: `Where: ${s.stateName}. Click, then click a state on the map, to move this event.`, onclick: () => act.pick(s.slot) }, s.state));
    if (s.paramName) el.append(h('span', { class: 'slot-sub param' }, s.paramName));
  } else if (a) {
    el.append(h('span', { class: 'slot-sub' }, s.paramName ?? 'remote'));
  }
  if (s.travel) el.append(h('span', { class: 'slot-travel', role: 'img', 'aria-label': 'Long same-day flight',
    title: 'Long same-day flight: this event is less effective and tiring. Travel overnight (in the morning) instead.' }));
  return el;
}

function activityMenu(anchor: HTMLElement, s: Slot, v: CampaignView, act: ScheduleActions): void {
  const here = act.selected ?? s.state ?? v.trail!.location;
  const hereName = v.states.find(x => x.code === here)?.name ?? here;
  const items: MenuItem[] = (v.activities ?? []).map(a => ({
    label: a.label, swatch: KIND_COLOUR[a.kind],
    right: cost(a.cost),
    detail: `${a.stamina > 0 ? '−' + a.stamina.toFixed(1) : '+' + (-a.stamina).toFixed(1)} stamina · ${a.local ? 'in ' + hereName : 'from anywhere'}`,
    pick: () => {
      if (a.choices.length) choiceMenu(anchor, s, a, a.local ? here : null, act);
      else act.order({ type: 'Slot', slot: s.slot, activity: a.id, state: a.local ? here : null, param: 0 });
    },
  }));
  if (s.activity !== 'NONE') items.push({ label: 'Clear this slot', swatch: KIND_COLOUR.none, pick: () => act.order({ type: 'Slot', slot: s.slot, activity: 'NONE', state: null, param: 0 }) });
  openMenu(anchor, `${where(s)}`, items, act.selected ? `Events in a state go to ${hereName} (picked on the map).` : 'Events in a state go where the candidate is; pick a state on the map first to send them elsewhere.');
}

function choiceMenu(anchor: HTMLElement, s: Slot, a: ActivityInfo, state: string | null, act: ScheduleActions): void {
  const items: MenuItem[] = a.choices.map((c, i) => ({
    label: c.name,
    detail: c.audience ? `${c.tier} · ${c.audience}` : undefined,
    right: c.views !== undefined ? (c.views < 0.1 ? '~50K' : '~' + (c.views >= 1 ? c.views.toFixed(0) + 'M' : Math.round(c.views * 1000) + 'K')) : undefined,
    disabled: c.refusal ?? null,
    pick: () => act.order({ type: 'Slot', slot: s.slot, activity: a.id, state, param: i }),
  }));
  openMenu(anchor, a.id === 'PODCAST' ? 'Book a show' : a.id === 'CHURCH' ? 'Which congregation?' : 'Which community?', items,
    a.id === 'PODCAST' ? 'Typical views of a candidate episode. Crossover audiences are harder, but count for more.' : undefined);
}

function meter(label: string, frac: number, value: string, tone: string, title: string): HTMLElement {
  return h('div', { class: 'proj-meter', title },
    h('span', { class: 'proj-label' }, label),
    h('span', { class: 'proj-bar', role: 'meter', 'aria-label': label, 'aria-valuemin': 0, 'aria-valuemax': 100, 'aria-valuenow': Math.round(frac * 100) },
      h('span', { class: 'proj-fill ' + tone, style: `width:${Math.max(0, Math.min(1, frac)) * 100}%` })),
    h('strong', { class: 'proj-value' }, value));
}

/** "Send the candidate" from a state (a right-click on the map, or the state desk): the next free slot, from today. */
export function quickVisit(v: CampaignView, code: string, act: ScheduleActions): MenuItem[] {
  const name = v.states.find(s => s.code === code)?.name ?? code;
  const free = (v.schedule ?? []).find(s => !s.past && s.activity === 'NONE');
  const kinds: [string, string][] = [['RALLY', 'Rally'], ['TOWN_HALL', 'Town hall'], ['RETAIL', 'Retail stops'], ['UNION', 'Union hall'],
    ['CAMPUS', 'Campus event'], ['FUNDRAISER', 'Fundraiser']];
  const acts = new Map((v.activities ?? []).map(a => [a.id, a]));
  return kinds.map(([id, label]) => ({
    label, swatch: KIND_COLOUR[acts.get(id)?.kind ?? 'none'], right: cost(acts.get(id)?.cost ?? 0),
    disabled: free ? null : 'The schedule is full: clear a slot first',
    pick: () => {
      if (!free) return;
      if (act.order({ type: 'Slot', slot: free.slot, activity: id, state: code, param: 0 }))
        act.toast(`${label} in ${name} on ${DAY[free.day]} ${PART[free.part]!.toLowerCase()}.`);
    },
  }));
}
