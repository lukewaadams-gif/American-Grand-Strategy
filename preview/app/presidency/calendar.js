// The Resolute Desk's calendar (Resolute Desk's ui/Rooms desk room): the week as seven days of three slots, each a card
// with its activity, what it is about and the stamina it costs; a menu of what can go in each slot (with why the rest
// cannot); the ready-made weeks; the chief of staff's hand; and the week ahead: stamina and sleep after the week,
// Air Force One's itinerary and a map of the trips. Every change is a command; the dialog is redrawn after each one.
import { h, s } from '../core/dom.js';
import { openMenu } from '../core/menu.js';
import { day } from '../core/format.js';
import { STATES, VIEWBOX } from '../generated/usStates.js';
/** Which family an activity belongs to, for its card's colour strip (the label always says what it is). */
const FAMILY = {
    POLICY: 'work', REMARKS: 'work', WORK_LATE: 'work',
    TRIP: 'travel', CAMPAIGN: 'travel', ABROAD: 'travel', TRAVEL: 'travel',
    HILL: 'politics', FUNDRAISER: 'politics', CABINET: 'politics', CEREMONY: 'politics',
    REST: 'recharge', EXERCISE: 'recharge', FAMILY: 'recharge', CAMP_DAVID: 'recharge',
    FREE: 'open',
};
const FAMILY_LABEL = { work: 'Work', travel: 'Travel', politics: 'Politics', recharge: 'Recharge', open: 'Open' };
const signed = (v) => (v > 0 ? '+' : v < 0 ? '−' : '') + Math.abs(v).toFixed(1);
/** The president's map pick: a slot waiting for a state, and the activity to put there. */
let picking = null;
export function cancelCalendarPick() { const was = picking !== null; picking = null; return was; }
export function calendar(v, act) {
    const w = v.week, acts = v.activities;
    const byId = new Map(acts.map(a => [a.id, a]));
    const first = w.slots[0].date;
    const grid = h('div', { class: 'cal-grid', role: 'group', 'aria-label': 'The week, Monday to Sunday' }, h('span', { class: 'cal-corner', 'aria-hidden': 'true' }), ...w.slots.slice(0, 7 * 3).filter(x => x.slot % 3 === 0).map(x => h('span', { class: 'cal-dayhead' + (x.slot >= 15 ? ' weekend' : '') }, h('span', { class: 'cal-dayname' }, x.day.slice(0, 3)), h('span', { class: 'cal-daydate' }, day(x.date)))), ...['Morning', 'Afternoon', 'Evening'].flatMap((part, p) => [
        h('span', { class: 'cal-parthead' }, part),
        ...[0, 1, 2, 3, 4, 5, 6].map(d => slotCard(w.slots[d * 3 + p], v, byId, act)),
    ]));
    const presets = h('div', { class: 'cal-presets', role: 'group', 'aria-label': 'Ready-made weeks' }, ...[['ROUTINE', 'Routine week', 'Policy every weekday morning; remarks, the Hill, a trip and ceremonies in the afternoons; rest at the weekend'],
        ['ROAD', 'Road week', 'The routine, with trips on four afternoons to the closest big states'],
        ['REST', 'Rest week', 'Lighter afternoons, rest every evening and Camp David at the weekend'],
        ['CLEAR', 'Clear the week', 'Every slot open']].map(([id, label, title]) => h('button', { class: 'btn small', type: 'button', title, onclick: () => act.order({ type: 'Week', week: id }) }, label)), h('button', { class: 'btn small toggle' + (w.delegated ? ' on' : ''), type: 'button', 'aria-pressed': String(w.delegated),
        title: 'The chief of staff writes each week: a routine, a rest week when you are tired, a trip to a swing state every fifth week, a cabinet meeting every six',
        onclick: () => act.order({ type: 'Delegate', on: !w.delegated }) }, 'The chief of staff writes the week: ', h('strong', null, w.delegated ? 'on' : 'off')));
    return h('div', { class: 'cal' }, h('div', { class: 'cal-top' }, h('div', null, h('div', { class: 'eyebrow' }, 'The Resolute Desk'), h('h2', { class: 'dialog-title' }, 'The week of ' + day(first, true))), h('button', { class: 'btn primary', type: 'button', onclick: () => act.close() }, 'Back to the Oval Office')), presets, h('div', { class: 'cal-body' }, h('div', { class: 'cal-main' }, grid, legend(), picking ? h('p', { class: 'cal-picking', role: 'status' }, `Choose a state on the map for ${w.slots[picking.slot].day} ${w.slots[picking.slot].part.toLowerCase()}, or press Escape.`) : null), weekAhead(v, act)));
}
function legend() {
    return h('ul', { class: 'cal-legend', 'aria-label': 'Kinds of activity' }, ...Object.entries(FAMILY_LABEL).filter(([k]) => k !== 'open').map(([k, l]) => h('li', null, h('span', { class: 'cal-swatch fam-' + k, 'aria-hidden': 'true' }), l)));
}
function slotCard(x, v, byId, act) {
    const fam = FAMILY[x.activity] ?? 'open';
    // "on the economy", "to Michigan": issues read in lower case mid-sentence, states keep their capitals
    const isState = x.activity === 'TRIP' || x.activity === 'CAMPAIGN' || x.activity === 'ABROAD';
    const about = x.argLabel ? (isState ? 'to ' + x.argLabel : 'on ' + x.argLabel.charAt(0).toLowerCase() + x.argLabel.slice(1)) : '';
    const label = `${x.day} ${x.part.toLowerCase()}: ${x.label}${about ? ' ' + about : ''}. Stamina ${signed(x.cost)}${x.flight ? `, ${x.flight} hours in the air each way` : ''}.`;
    const b = h('button', { class: `cal-slot fam-${fam}` + (picking?.slot === x.slot ? ' picking' : ''), type: 'button', 'aria-label': label, 'aria-haspopup': 'menu' }, h('span', { class: 'cal-act' }, x.label), about ? h('span', { class: 'cal-arg' }, about) : null, h('span', { class: 'cal-cost' + (x.cost >= 0 ? ' gain' : '') }, signed(x.cost), x.flight && x.flight > 3.5 ? h('span', { class: 'cal-long', title: 'A long flight: under a full night’s sleep' }, ' ✈ long') : null));
    b.addEventListener('click', () => chooser(b, x, v, byId, act));
    return b;
}
/** The menu for a slot: every activity, with why it cannot go here; then its issue or its state. */
function chooser(anchor, x, v, byId, act) {
    const items = [...byId.values()].map(a => ({
        label: a.label,
        detail: a.refusals[x.slot] ?? `${a.description} Stamina ${signed(a.stamina)}, stress ${signed(a.stress)}.`,
        disabled: a.refusals[x.slot],
        swatch: `var(--fam-${FAMILY[a.id] ?? 'open'})`,
        right: a.id === x.activity ? 'Now' : undefined,
        pick: () => {
            if (a.arg === 'ISSUE')
                issueMenu(anchor, x, a, v, act);
            else if (a.arg === 'STATE')
                stateMenu(anchor, x, a, v, act);
            else if (a.arg === 'COUNTRY')
                countryMenu(anchor, x, a, v, act);
            else
                act.order({ type: 'Slot', slot: x.slot, activity: a.id, arg: null });
        },
    }));
    openMenu(anchor, `${x.day} ${x.part.toLowerCase()}`, items);
}
function issueMenu(anchor, x, a, v, act) {
    // in order of how much attention each needs (Resolute Desk's priorities: salience times the room to improve)
    const issues = [...v.issues].sort((p, q) => q.salience * (62 - q.approval) - p.salience * (62 - p.approval));
    openMenu(anchor, `${a.label}: which issue?`, issues.map(i => ({
        label: i.label,
        detail: `${Math.round(i.approval)}% approve · command ${Math.round(i.command)}${i.top ? ' · needs you most' : ''}`,
        right: x.arg === i.id && x.activity === a.id ? 'Now' : undefined,
        pick: () => act.order({ type: 'Slot', slot: x.slot, activity: a.id, arg: i.id }),
    })));
}
function stateMenu(anchor, x, a, v, act) {
    // Resolute Desk's swing order: electoral votes, discounted the further approval is from 47
    const states = [...v.states].sort((p, q) => q.ev * (1 - Math.abs(q.approval - 47) / 25) - p.ev * (1 - Math.abs(p.approval - 47) / 25));
    openMenu(anchor, `${a.label}: where to?`, [
        { label: 'Choose on the map', detail: 'Click a state on the trip map', pick: () => { picking = { slot: x.slot, activity: a.id }; act.redraw(); } },
        ...states.map(st => ({
            label: st.name,
            detail: `${Math.round(st.approval)}% approve · ${st.ev} electoral votes${st.standing > 0.5 ? ' · visited lately' : ''}`,
            right: `${st.flight} h`,
            pick: () => act.order({ type: 'Slot', slot: x.slot, activity: a.id, arg: st.code }),
        })),
    ], 'Swing states first. Hours are each way on Air Force One.');
}
/** A trip abroad fills three slots from this one (Resolute Desk's trip picker): the flight out, the meetings, the way home. */
function countryMenu(anchor, x, a, v, act) {
    const countries = v.abroad ?? [];
    openMenu(anchor, `${a.label}: where to?`, countries.map(c => ({
        label: c.name,
        detail: `${c.leader} · relations ${c.rel > 0 ? '+' : ''}${c.rel}${c.visits ? ` · ${c.visits} ${c.visits === 1 ? 'visit' : 'visits'}` : ''}`,
        right: `${c.flight} h`,
        pick: () => {
            for (let k = 0; k < 3 && x.slot + k < 21; k++)
                if (!act.order({ type: 'Slot', slot: x.slot + k, activity: a.id, arg: c.code }) && k === 0)
                    return;
        },
    })), 'Nearest first. A trip takes three slots; hours are each way.');
}
function weekAhead(v, act) {
    const w = v.week;
    const tone = (x) => (x >= 50 ? 'good' : x >= 25 ? 'warn' : 'bad');
    const ageNote = w.age > 60 ? `At ${w.age}, every tiring slot costs ${Math.round((w.age - 60) * 1.2)}% more stamina.` : null;
    return h('aside', { class: 'cal-side', 'aria-label': 'The week ahead' }, h('h3', { class: 'cal-side-title' }, 'The week ahead'), meterRow('Stamina now', w.stamina, tone(w.stamina)), meterRow('After this week', w.projected, tone(w.projected)), h('p', { class: 'cal-note' }, `Sleep about ${w.sleepAhead.toFixed(1)} hours a night · stress ${Math.round(w.stress)} · health ${Math.round(w.health)}`), w.sick > 0 ? h('p', { class: 'cal-note warn' }, `The president is unwell: stamina is held at 55 for ${w.sick === 1 ? 'another week' : w.sick + ' more weeks'}.`) : null, ageNote ? h('p', { class: 'cal-note' }, ageNote) : null, w.projected < 25 ? h('p', { class: 'cal-note bad' }, 'This week would leave the president exhausted. Try a rest week.') : null, h('h3', { class: 'cal-side-title' }, 'Air Force One'), w.itinerary.length ? h('ul', { class: 'cal-trips' }, ...w.itinerary.map(t => h('li', { class: t.hours > 3.5 ? 'long' : '' }, h('span', null, `${t.day.slice(0, 3)} · ${t.name}`), h('span', { class: 'cal-hours' }, `${t.hours} h each way`))))
        : h('p', { class: 'cal-note' }, 'No travel this week.'), tripMap(v, act), w.lastWeek.length ? h('div', null, h('h3', { class: 'cal-side-title' }, 'Last week'), h('p', { class: 'cal-note' }, w.lastWeek.join(' · ') + `; stamina ${signed(w.lastStaminaChange)}.`)) : null, w.midtermSeason ? h('p', { class: 'cal-note' }, 'Midterm season: campaign stops for your party are open.') : null);
}
function meterRow(label, value, tone) {
    return h('div', { class: 'cal-meter' }, h('span', { class: 'cal-meter-label' }, label), h('span', { class: 'cal-meter-bar', role: 'meter', 'aria-valuemin': 0, 'aria-valuemax': 100, 'aria-valuenow': Math.round(value), 'aria-label': label }, h('span', { class: 'cal-meter-fill ' + tone, style: `width:${Math.max(2, value)}%` })), h('span', { class: 'cal-meter-value' }, String(Math.round(value))));
}
/** Approval by state, the week's trips in gold and where the president is now; in a map pick, the states can be clicked. */
function tripMap(v, act) {
    const w = v.week;
    const app = new Map(v.states.map(st => [st.code, st.approval]));
    const trips = new Set(w.itinerary.map(t => t.state));
    const svg = s('svg', { class: 'cal-map' + (picking ? ' picking' : ''), viewBox: `0 0 ${VIEWBOX.width} ${VIEWBOX.height}`, 'aria-hidden': picking ? null : 'true',
        role: picking ? 'group' : null, 'aria-label': picking ? 'Choose a state' : null, focusable: 'false' });
    for (const st of STATES) {
        const a = app.get(st.code) ?? 50;
        const t = Math.max(0, Math.min(1, (a - 30) / 40));
        const p = s('path', { d: st.d, class: 'cal-state' + (trips.has(st.code) ? ' trip' : '') + (w.location === st.code ? ' here' : ''),
            style: `--a:${t.toFixed(2)}`, 'data-state': st.code });
        if (picking && st.code !== 'DC') {
            const pick = picking;
            p.setAttribute('tabindex', '0');
            p.setAttribute('role', 'button');
            p.setAttribute('aria-label', `${st.name}, ${Math.round(a)}% approve`);
            const go = () => { picking = null; act.order({ type: 'Slot', slot: pick.slot, activity: pick.activity, arg: st.code }); };
            p.addEventListener('click', go);
            p.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                go();
            } });
        }
        svg.append(p);
    }
    return h('figure', { class: 'cal-map-wrap' }, svg, h('figcaption', { class: 'cal-note' }, 'Approval by state: lighter is higher. Gold: this week’s trips.'));
}
