// The Residence (Resolute Desk's Rooms.residence; docs/PRESIDENCY_PORT.md, milestone 7): how the president is holding
// up (stamina, sleep, stress, fitness and health), the White House physician's word, the term's stamina and stress, and
// the weekend's plans. Rest, family time, exercise and Camp David go on the calendar: these buttons fill this week's
// weekend or mornings with them.
import { h } from '../core/dom.js';
import { day } from '../core/format.js';
import type { ResidenceView } from '../core/types.js';
import { lineChart } from './charts.js';

export interface ResidenceActions {
  view(): ResidenceView;
  /** One calendar order (a slot's activity, or a preset week); false if refused. */
  presidency(order: object): boolean;
  calendar(): void;
  back(): void;
}

/** Saturday and Sunday: slots 15-20; weekday mornings 0, 3, 6, 9, 12. */
const WEEKEND = [15, 16, 17, 18, 19, 20], MORNINGS = [0, 3, 6, 9, 12];

export function residenceRoom(act: ResidenceActions): HTMLElement {
  const root = h('div', { class: 'room residence' });
  let done = '';
  const plan = (label: string, orders: object[]) => {
    let n = 0;
    for (const o of orders) if (act.presidency(o)) n++;
    done = n ? `${label}: on the calendar.` : 'Nothing changed.';
    draw();
  };

  function draw(): void {
    const v = act.view();
    const top = h('div', { class: 'room-top' },
      h('div', null, h('div', { class: 'eyebrow' }, 'Upstairs'), h('h2', { class: 'dialog-title' }, 'The Residence'), h('p', { class: 'muted' }, 'Week of ' + day(v.date, true))),
      h('button', { class: 'btn primary', type: 'button', onclick: act.back }, 'Back to the Oval Office'));
    if (v.stamina === undefined) { root.replaceChildren(top, h('p', null, v.reason ?? '')); return; }
    const row = (label: string, value: string) => [h('dt', null, label), h('dd', null, value)];
    const hist = v.history!;
    root.replaceChildren(top,
      h('div', { class: 'room-grid two' },
        h('section', { class: 'room-card', 'aria-labelledby': 'res-how' },
          h('h3', { class: 'room-card-title', id: 'res-how' }, 'How you’re holding up'),
          h('p', null, h('strong', null, v.name!), h('span', { class: 'muted' }, `, ${v.age}`)),
          h('dl', { class: 'cab-facts' }, ...row('Stamina', String(Math.round(v.stamina!))), ...row('Sleep', `${v.sleep!.toFixed(1)} hours a night`),
            ...row('Stress', String(Math.round(v.stress!))), ...row('Fitness', String(Math.round(v.fitness!))), ...row('Health', String(Math.round(v.health!))),
            ...(v.sick ? row('Illness', `${v.sick} more ${v.sick === 1 ? 'week' : 'weeks'}`) : [])),
          h('p', { class: 'res-advice ' + (v.warn ? 'warn' : 'good') }, v.advice!),
          h('p', { class: 'muted small' }, 'Long days cost more as presidents age. Exercise builds fitness slowly; sleep and rest restore stamina week to week.')),
        h('section', { class: 'room-card', 'aria-labelledby': 'res-plans' },
          h('h3', { class: 'room-card-title', id: 'res-plans' }, 'This week, away from the desk'),
          h('div', { class: 'res-plans' },
            h('button', { class: 'btn', type: 'button', onclick: () => plan('A family weekend', WEEKEND.map(slot => ({ type: 'Slot', slot, activity: slot % 3 === 2 ? 'REST' : 'FAMILY', arg: null }))) },
              'A family weekend'),
            h('p', { class: 'muted small' }, 'Saturday and Sunday with the family, evenings to rest. Lowers stress a lot; the public likes seeing it.'),
            h('button', { class: 'btn', type: 'button', onclick: () => plan('A weekend at Camp David', WEEKEND.map(slot => ({ type: 'Slot', slot, activity: 'CAMP_DAVID', arg: null }))) },
              'A weekend at Camp David'),
            h('p', { class: 'muted small' }, 'The biggest lift to stamina there is, but bad optics in a crisis.'),
            h('button', { class: 'btn', type: 'button', onclick: () => plan('Exercise every morning', MORNINGS.map(slot => ({ type: 'Slot', slot, activity: 'EXERCISE', arg: null }))) },
              'Exercise every morning'),
            h('p', { class: 'muted small' }, 'Workouts before the day begins: a little stamina now, fitness over the months.'),
            h('button', { class: 'btn', type: 'button', onclick: () => plan('A week of rest', [{ type: 'Week', week: 'REST' }]) }, 'A week of rest'),
            h('p', { class: 'muted small' }, 'The physician’s prescription: a light week with the evenings free.')),
          done ? h('p', { class: 'res-done', role: 'status' }, done) : null,
          v.delegated ? h('p', { class: 'muted small' }, 'The chief of staff writes your calendar; these change this week’s.') : null,
          h('button', { class: 'btn small quiet', type: 'button', onclick: act.calendar }, 'Open the calendar'))),
      h('section', { class: 'room-card', 'aria-labelledby': 'res-chart' },
        h('h3', { class: 'room-card-title', id: 'res-chart' }, 'Stamina and stress'),
        lineChart({ title: 'Stamina and stress week by week', dates: hist.dates, digits: 0, unit: '', height: 220,
          series: [{ label: 'Stamina', values: hist.stamina }, { label: 'Stress', values: hist.stress }] })));
  }

  draw();
  return root;
}
