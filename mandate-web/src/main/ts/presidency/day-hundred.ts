// The 100-day report: the opening chapter's report card, goal by goal and why (opening.DayHundredReport). The presidency
// goes on after it; the report is offered once, the week day 100 comes, and stays in the menu for as long as the game
// lasts. Reading it is optional.
import { h } from '../core/dom.js';
import type { DayHundredView } from '../core/types.js';
import { dateLong } from './oval-screen.js';

const MARK: Record<DayHundredView['goals'][number]['status'], string> = { MET: 'Met', PARTLY: 'Partly met', MISSED: 'Missed' };

export function dayHundredReport(r: DayHundredView, close: () => void): HTMLElement {
  const first = r.approval[0] ?? 0, last = r.approval[r.approval.length - 1] ?? first;
  return h('div', { class: 'dialog-card dh-report' },
    h('div', { class: 'eyebrow' }, 'Day 100 · ' + dateLong(r.date)),
    h('h2', { class: 'dialog-title' }, 'The 100-day report'),
    h('p', { class: 'verdict-grade' }, `${r.outcome} · ${r.score} of 10`),
    h('p', null, r.headline),
    h('ul', { class: 'dh-goals' }, ...r.goals.map(g => h('li', null, h('strong', null, `${g.name}: ${MARK[g.status]}. `), g.why))),
    h('p', { class: 'muted small' }, r.rule),
    h('p', null, `Approval: ${first.toFixed(0)}% at the oath, ${last.toFixed(0)}% on day 100.`),
    r.decisions.length ? h('details', null, h('summary', null, 'The decisions that shaped it'),
      h('ul', { class: 'hill-list small' }, ...r.decisions.map(d => h('li', null, d)))) : null,
    h('p', { class: 'muted small' }, 'The presidency goes on: the Roosevelt Room, the West Wing and the Situation Room are open. The report stays in the menu.'),
    h('div', { class: 'dialog-foot' }, h('button', { class: 'btn primary', type: 'button', onclick: close }, 'Keep governing')));
}
