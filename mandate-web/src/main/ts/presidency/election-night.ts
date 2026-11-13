// Election night: the result was computed once, on Election Day; this screen only presents it. Watching it to the
// end tells the engine (ElectionNightSeen), so a reload before the end shows the night again and never a different one.
import { h } from '../core/dom.js';
import { day } from '../core/format.js';
import type { ElectionNightView } from '../core/types.js';

export interface ElectionNightActions {
  view(): ElectionNightView;
  /** The night is over: the engine records it, and the game goes on (the transition, governing, or the end). */
  done(): void;
}

const NEXT: Record<ElectionNightView['next'], string> = {
  transition: 'To the transition',
  end: 'To the final report',
  second: 'Back to the Oval Office',
  lameduck: 'Back to the Oval Office',
};

export function electionNight(act: ElectionNightActions): HTMLElement {
  const v = act.view(), r = v.result;
  const second = v.year > 2028;
  const head = v.won ? (second ? 'Four more years' : 'You win the presidency') : (second ? 'You lose re-election' : 'You lose');
  const name = (p: 'D' | 'R') => (p === v.player.party ? v.player.name : v.opponent.name);
  return h('main', { class: 'room hill verdict end-page en-night', id: 'main' },
    h('div', { class: 'eyebrow' }, 'Election night · ' + day(v.electionDay, true)),
    h('h1', { class: 'dialog-title' }, head),
    h('p', { class: 'verdict-grade' }, `${name('D')} (D) ${r.dem} · ${name('R')} (R) ${r.rep}`),
    h('p', null, `The popular vote: ${r.popular >= 0 ? 'Democrats' : 'Republicans'} by ${Math.abs(r.popular).toFixed(1)} points. The tipping-point state: ${r.tipping}.`),
    h('div', { class: 'cab-buttons' },
      h('button', { class: 'btn primary', type: 'button', onclick: () => act.done() }, NEXT[v.next])));
}
