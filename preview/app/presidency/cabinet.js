// The Cabinet Room (Resolute Desk's policy table; docs/ECONOMY_MODEL.md, "On screen"): every policy by area, where it
// stands against where it stood in 2029, how it can be changed, the evidence and the politics, its history, and the
// budget office's score of a step either way; this game's bills as the steps they make; and what the moves so far
// have done to the outcomes beyond the economy. In this milestone the room only shows: laws move the table from the
// Hill, and orders and rules arrive with the West Wing.
import { h } from '../core/dom.js';
import { day } from '../core/format.js';
import { toast } from '../core/dialog.js';
import { money, scoreView, signed } from './score.js';
/** A policy's levels as a row of steps, the current one filled and the 2029 one marked. Decorative: the text says the level. */
function ladder(p) {
    return h('span', { class: 'cab-ladder', 'aria-hidden': 'true' }, ...p.levels.map((_, i) => h('span', { class: 'cab-rung' + (i === p.level ? ' on' : '') + (i === p.start && i !== p.level ? ' was' : '') })));
}
/** A step's cost a year: "costs $40 billion a year", "saves $60 billion a year", "costs nothing". */
const stepCost = (bn) => bn > 0 ? `costs ${money(bn)} a year` : bn < 0 ? `saves ${money(-bn)} a year` : 'costs nothing';
function detail(p, act) {
    const out = h('div', { class: 'cab-score', 'aria-live': 'polite' });
    const run = (steps) => {
        try {
            out.replaceChildren(scoreView(act.score(p.id, steps), 'h4'));
        }
        catch (e) {
            toast(String(e));
        }
    };
    const up = p.level < p.levels.length - 1, down = p.level > 0;
    const lean = p.lean ? `${p.lean} want it higher` : 'Neither party owns it';
    return h('div', { class: 'cab-detail' }, h('h3', { class: 'cab-detail-title' }, p.name), h('ol', { class: 'cab-levels' }, ...p.levels.map((l, i) => h('li', { class: i === p.level ? 'now' : '' }, l, i === p.level ? h('span', { class: 'pill' }, 'now') : null, i === p.start && i !== p.level ? h('span', { class: 'pill quiet' }, 'in 2029') : null))), h('div', { class: 'cab-buttons' }, h('button', { class: 'btn small', type: 'button', disabled: !up, onclick: () => run(1) }, up ? `Score a step up: ${p.levels[p.level + 1]}` : 'At its highest level'), h('button', { class: 'btn small', type: 'button', disabled: !down, onclick: () => run(-1) }, down ? `Score a step down: ${p.levels[p.level - 1]}` : 'At its lowest level')), out, act.draft && (p.routes.includes('a law') || p.routes.includes('the budget')) ? h('div', { class: 'cab-buttons' }, up ? h('button', { class: 'btn small', type: 'button', onclick: () => act.draft(`pol:${p.id}:${p.level}:${p.level + 1}`) }, 'Draft a bill: a step up') : null, down ? h('button', { class: 'btn small', type: 'button', onclick: () => act.draft(`pol:${p.id}:${p.level}:${p.level - 1}`) }, 'Draft a bill: a step down') : null) : null, h('dl', { class: 'cab-facts' }, h('dt', null, 'How it can change'), h('dd', null, (p.routes.length ? 'By ' + p.routes.join(', ') : 'Only by a law') + '. In this milestone laws move it from the Hill; orders and rules arrive with the West Wing, and the budget with Congress.'), h('dt', null, 'A step costs'), h('dd', null, [p.costUp !== null ? `up: ${stepCost(p.costUp)}` : null, p.costDown !== null ? `down: ${stepCost(p.costDown)}` : null]
        .filter(Boolean).join('; ') || '—'), h('dt', null, 'The public'), h('dd', null, `${Math.round(p.support)}% support a step up. ${lean}.${p.signature ? ' A signature issue: moving it draws a bigger reaction.' : ''}`), h('dt', null, 'The evidence'), h('dd', null, `${p.evidence} (the range is about ${p.spread} either way). Effects start after about ${Math.round(p.lag / 4.3)} months.`), h('dt', null, 'Who gains'), h('dd', null, p.winners), h('dt', null, 'Who pays'), h('dd', null, p.losers), h('dt', null, 'The precedent'), h('dd', null, p.precedent), p.basis ? h('dt', null, 'The economics') : null, p.basis ? h('dd', null, p.basis) : null, p.outcomes.length ? h('dt', null, 'Other outcomes, a step up') : null, p.outcomes.length ? h('dd', null, p.outcomes.map(o => `${o.label.replace(/ \(.*\)/, '')} ${signed(o.value, 1)} ${o.label.match(/\((.*)\)/)?.[1] ?? ''}`).join('; ')) : null), h('h4', { class: 'cab-sub' }, 'Since the inauguration'), p.moves.length ? h('ul', { class: 'cab-moves' }, ...p.moves.map(m => h('li', null, `${day(m.date, true)}: ${m.from} to ${m.to}, by ${m.law}`)))
        : h('p', { class: 'muted small' }, 'Unchanged.'));
}
export function cabinetRoom(c, act) {
    const tabs = [...c.areas.map(a => a.name), 'This game’s bills', 'So far'];
    let at = 0, selected = null;
    const panel = h('div', { class: 'cab-panel', role: 'region', 'aria-live': 'polite' });
    const buttons = tabs.map((t, i) => {
        const b = h('button', { class: 'btn small toggle' + (i === at ? ' on' : ''), type: 'button', 'aria-pressed': String(i === at) }, t);
        b.addEventListener('click', () => { at = i; selected = null; show(); });
        return b;
    });
    const show = () => {
        buttons.forEach((b, i) => { b.classList.toggle('on', i === at); b.setAttribute('aria-pressed', String(i === at)); });
        if (at < c.areas.length) {
            const area = c.areas[at];
            const chosen = area.policies.find(p => p.id === selected) ?? area.policies[0];
            const list = h('ul', { class: 'cab-list', 'aria-label': area.name }, ...area.policies.map(p => h('li', null, h('button', { class: 'cab-row' + (p.id === chosen.id ? ' on' : ''), type: 'button', 'aria-pressed': String(p.id === chosen.id),
                onclick: () => { selected = p.id; show(); } }, h('span', { class: 'cab-row-name' }, p.name), ladder(p), h('span', { class: 'cab-row-level' }, p.levels[p.level], p.level !== p.start ? h('span', { class: 'sr' }, ` (was ${p.levels[p.start]} in 2029)`) : null), !p.scored ? h('span', { class: 'cab-row-note' }, 'no economic effect') : null))));
            panel.replaceChildren(h('div', { class: 'cab-split' }, list, detail(chosen, act)));
        }
        else if (at === c.areas.length) {
            panel.replaceChildren(h('div', { class: 'cab-bills' }, h('p', { class: 'muted' }, 'Each of the game’s bills is the policy steps it makes. Its score is the sum of those steps, from where the table stands now.'), ...c.bills.map(b => {
                const out = h('div', { class: 'cab-score', 'aria-live': 'polite' });
                return h('article', { class: 'room-card cab-bill' }, h('h3', { class: 'room-card-title' }, b.name, b.law ? h('span', { class: 'pill good' }, 'law') : null), h('ul', { class: 'cab-steps' }, ...b.steps.map(s => h('li', null, `${s.name}: ${s.from}${s.steps ? ' to ' + s.to : ' (already there)'}`))), b.law ? h('p', { class: 'muted small' }, 'Already law: its steps are on the table.')
                    : h('button', { class: 'btn small', type: 'button', onclick: () => { try {
                            out.replaceChildren(scoreView(act.billScore(b.id), 'h4'));
                        }
                        catch (e) {
                            toast(String(e));
                        } } }, 'The budget office’s score'), out);
            })));
        }
        else {
            panel.replaceChildren(h('div', { class: 'room-card' }, h('h3', { class: 'room-card-title' }, 'What the moves so far have done'), h('p', { class: 'muted small' }, 'Beyond the economy (the briefing book shows that): Resolute Desk’s estimates of each policy’s other outcomes, phased in after their lags, against the country in 2029.'), c.outcomes.length ? h('dl', { class: 'cab-facts' }, ...c.outcomes.flatMap(o => [h('dt', null, o.label), h('dd', null, signed(o.value, 2))]))
                : h('p', null, 'Nothing yet.')));
        }
    };
    show();
    return h('div', { class: 'room cab' }, h('div', { class: 'room-top' }, h('div', null, h('div', { class: 'eyebrow' }, 'The West Wing'), h('h2', { class: 'dialog-title' }, 'The Cabinet Room'), h('p', { class: 'muted' }, 'The policy table, week of ' + day(c.date, true) + ': where each policy stands, and what the budget office says a step would do.')), h('button', { class: 'btn primary', type: 'button', onclick: act.back }, 'Back to the Oval Office')), h('nav', { class: 'cab-tabs', 'aria-label': 'Areas of policy' }, ...buttons), panel);
}
