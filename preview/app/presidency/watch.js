// Watch mode (Resolute Desk's ui/Watch and sim/WatchPlan): choose how the staff governs (a ready-made plan or six
// priorities by hand), which kinds of decision to stop for, and how fast the weeks go; then watch until you take the
// controls back. The weeks are ordinary commands: the staff plans each week by the plan and answers what comes up
// with the option it recommends, unless the plan says to stop for it.
import { h } from '../core/dom.js';
export const SPEEDS = [{ label: 'Slow', ms: 1600 }, { label: 'Steady', ms: 800 }, { label: 'Fast', ms: 300 }];
/** Which stops arrive with which milestone, for the ones that do not exist yet. */
const STOP_LATER = { 3: 'Court rulings arrive with the West Wing (milestone 5)', 5: 'Storylines arrive with milestone 5', 6: 'The 2032 campaign has its own headquarters' };
export function watchDialog(v, speed, act) {
    const w = v.watch;
    let chosen = speed;
    const speedButtons = SPEEDS.map((sp, i) => {
        const b = h('button', { class: 'btn small toggle' + (i === chosen ? ' on' : ''), type: 'button', 'aria-pressed': String(i === chosen) }, sp.label);
        b.addEventListener('click', () => {
            chosen = i;
            speedButtons.forEach((x, k) => { x.classList.toggle('on', k === i); x.setAttribute('aria-pressed', String(k === i)); });
        });
        return b;
    });
    return h('div', { class: 'dialog-card watch' }, h('div', { class: 'eyebrow' }, 'Watch mode'), h('h2', { class: 'dialog-title' }, 'How should the staff govern?'), h('p', { class: 'muted' }, 'The staff plans each week by this plan and answers what comes up as it thinks best, unless you ask it to stop. Take the controls back at any time.'), h('fieldset', { class: 'watch-presets' }, h('legend', null, 'A ready-made plan'), ...w.presets.map((p, i) => h('button', { class: 'btn small toggle' + (w.preset === i ? ' on' : ''), type: 'button', 'aria-pressed': String(w.preset === i),
        title: p.description, onclick: () => act.order({ type: 'WatchPreset', preset: i }) }, p.label)), h('p', { class: 'watch-desc' }, w.presets[w.preset].description)), h('fieldset', { class: 'watch-priorities' }, h('legend', null, 'Priorities'), ...w.priorities.map((p, i) => {
        const id = 'watch-p-' + i;
        const input = h('input', { type: 'range', id, min: 0, max: 100, step: 5, value: Math.round(p.value * 100), 'aria-describedby': id + '-d',
            'aria-valuetext': `${Math.round(p.value * 100)}: ${p.value < 0.34 ? p.low : p.value > 0.66 ? p.high : 'in between'}` });
        input.addEventListener('change', () => act.order({ type: 'WatchPriority', priority: i, value: Number(input.value) / 100 }));
        return h('div', { class: 'watch-priority' }, h('label', { for: id, class: 'watch-label' }, p.label), h('div', { class: 'watch-range' }, h('span', { class: 'watch-end' }, p.low), input, h('span', { class: 'watch-end' }, p.high)), h('p', { class: 'watch-desc', id: id + '-d' }, p.description));
    }), h('p', { class: 'watch-desc' }, `Travel: a road week every ${w.roadWeeks} weeks. The bills, orders and the hawk's choices take effect as Congress, the West Wing and the world arrive.`)), h('fieldset', { class: 'watch-stops' }, h('legend', null, 'Stop for'), ...w.stops.map((st, i) => {
        const id = 'watch-s-' + i;
        const box = h('input', { type: 'checkbox', id, checked: st.on, disabled: !st.available });
        box.addEventListener('change', () => act.order({ type: 'WatchStop', stop: i, on: box.checked }));
        return h('div', { class: 'watch-stop' + (st.available ? '' : ' later') }, box, h('label', { for: id }, st.label), !st.available && STOP_LATER[i] ? h('span', { class: 'watch-later' }, STOP_LATER[i]) : null);
    })), h('fieldset', { class: 'watch-speed' }, h('legend', null, 'Speed'), ...speedButtons), h('div', { class: 'dialog-foot' }, h('button', { class: 'btn primary', type: 'button', onclick: () => act.start(chosen) }, 'Start watching'), h('button', { class: 'btn quiet', type: 'button', onclick: () => act.close() }, 'Not now')));
}
