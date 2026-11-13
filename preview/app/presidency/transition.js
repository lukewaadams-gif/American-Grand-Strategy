// The transition office (Resolute Desk's TransitionUI; docs/PRESIDENCY_PORT.md, milestone 10): from the day after the
// election to the oath, the president-elect sets the agenda (one to three priorities; the first is the flagship of the
// first hundred days) and chooses the White House staff from shortlists: the team's first read is rough, an interview
// shows who someone really is, a background check what the papers would find. The grade colours the honeymoon.
import { h } from '../core/dom.js';
import { day } from '../core/format.js';
const TABS = ['The agenda', 'The White House staff'];
function meter(v, label) {
    const tone = v >= 70 ? 'good' : v >= 55 ? 'mid' : 'bad';
    return h('span', { class: 'hill-meter ' + tone, role: 'meter', 'aria-valuemin': '0', 'aria-valuemax': '100', 'aria-valuenow': String(v), 'aria-label': label }, h('span', { style: `width:${Math.max(2, Math.min(100, v))}%` }), h('span', { class: 'hill-meter-value' }, String(v)));
}
export function transitionOffice(act) {
    let tab = 'The agenda';
    let role = 0;
    let picks = null;
    let note = '';
    const root = h('main', { class: 'room hill transition', id: 'main' });
    function draw() {
        const v = act.view();
        if (picks === null)
            picks = [...v.agenda];
        const top = h('div', { class: 'room-top' }, h('div', null, h('div', { class: 'eyebrow' }, 'The transition'), h('h1', { class: 'dialog-title' }, 'President-elect ' + v.president), h('p', { class: 'muted' }, `${v.ev} electoral votes to ${v.evOpp}. ${v.congress}. The oath is taken at noon on ${day(v.inauguration, true)}.`)), h('div', { class: 'cab-buttons' }, h('button', { class: 'btn', type: 'button', onclick: (e) => act.menu(e.currentTarget) }, 'Menu'), h('button', { class: 'btn primary', type: 'button', disabled: !v.agenda.length, onclick: () => act.inaugurate(),
            title: v.agenda.length ? 'Take the oath of office' : 'Set the agenda first' }, 'Take the oath')));
        const nav = h('nav', { class: 'cab-tabs', 'aria-label': 'The transition' }, ...TABS.map(t => h('button', { class: 'btn small toggle' + (t === tab ? ' on' : ''), type: 'button', 'aria-pressed': String(t === tab), onclick: () => { tab = t; note = ''; draw(); } }, t)));
        root.replaceChildren(top, nav, note ? h('p', { class: 'sit-message', role: 'status' }, note) : h('span'), tab === 'The agenda' ? agendaTab(v) : staffTab(v));
    }
    function agendaTab(v) {
        const chosen = picks;
        const toggle = (id) => {
            const i = chosen.indexOf(id);
            if (i >= 0)
                chosen.splice(i, 1);
            else if (chosen.length < 3)
                chosen.push(id);
            draw();
        };
        const pledged = v.bills.filter(b => b.pledged).length;
        return h('div', { class: 'hill-body' }, h('section', { class: 'room-card', 'aria-labelledby': 'tr-ag' }, h('h2', { class: 'room-card-title', id: 'tr-ag' }, 'The agenda'), h('p', null, 'Choose one to three priorities, in order. The first is the flagship of the first hundred days.'
            + (pledged ? ` You promised ${pledged} in the campaign; each one left off costs you with your base.` : '')), h('ol', { class: 'tr-chosen' }, ...chosen.map((id, i) => {
            const b = v.bills.find(x => x.id === id);
            return h('li', null, h('strong', null, b?.name ?? id), i === 0 ? h('span', { class: 'muted small' }, ' · the flagship') : null, h('button', { class: 'btn small', type: 'button', onclick: () => toggle(id), 'aria-label': 'Take ' + (b?.name ?? id) + ' off the agenda' }, 'Remove'));
        })), h('div', { class: 'cab-buttons' }, h('button', { class: 'btn small primary', type: 'button', disabled: !chosen.length, onclick: () => { if (act.agenda(chosen)) {
                note = 'The agenda is set.';
                draw();
            } } }, 'Set the agenda'), v.agenda.length ? h('span', { class: 'small' }, 'Set: ' + v.agenda.map(id => v.bills.find(b => b.id === id)?.name ?? id).join(', ') + '.') : null)), h('section', { class: 'room-card', 'aria-labelledby': 'tr-bills' }, h('h2', { class: 'room-card-title', id: 'tr-bills' }, 'Your party’s bills'), h('ul', { class: 'sit-ops' }, ...v.bills.map(b => h('li', null, h('div', null, h('strong', null, b.name), b.pledged ? h('span', { class: 'tr-pledge' }, 'Promised') : null, h('p', { class: 'muted small' }, `${b.issue} · $${b.cost} billion a year${b.needs60 ? ' · needs 60 votes in the Senate' : ''}`)), h('button', { class: 'btn small toggle' + (chosen.includes(b.id) ? ' on' : ''), type: 'button', 'aria-pressed': String(chosen.includes(b.id)),
            disabled: !chosen.includes(b.id) && chosen.length >= 3, onclick: () => toggle(b.id) }, chosen.includes(b.id) ? 'On the agenda' : 'Add'))))));
    }
    function staffTab(v) {
        const jobs = v.jobs ?? [];
        const job = jobs[role] ?? jobs[0];
        const doIt = (id, a, done) => { if (act.act(id, a)) {
            note = done;
            draw();
        } };
        return h('div', { class: 'hill-body' }, h('section', { class: 'room-card', 'aria-labelledby': 'tr-st' }, h('h2', { class: 'room-card-title', id: 'tr-st' }, 'The White House staff'), h('p', null, 'Name your own people, check them, and keep the surprises down. A good transition lengthens your honeymoon and starts the staff off happy. '
            + 'Whatever is still open on Inauguration Day the transition team fills itself. The cabinet goes before the Senate in the first hundred days.'), h('dl', { class: 'ww-stats' }, h('dt', null, 'The transition so far'), h('dd', null, meter(v.score ?? 0, 'The transition’s score')), h('dt', null, 'Its grade'), h('dd', null, `${v.grade ?? ''} (${v.score ?? 0} of 100)`), h('dt', null, 'The team’s hours'), h('dd', null, `${v.hours ?? 0} of ${v.hoursMax ?? 0} left (an interview takes 1, a background check 2)`))), h('div', { class: 'tr-jobs', role: 'group', 'aria-label': 'Jobs' }, ...jobs.map((j, i) => h('button', { class: 'btn small toggle' + (i === role ? ' on' : ''), type: 'button',
            'aria-pressed': String(i === role), onclick: () => { role = i; note = ''; draw(); } }, j.title + (j.pick ? ' ✓' : '')))), h('section', { class: 'room-card', 'aria-labelledby': 'tr-job' }, h('h2', { class: 'room-card-title', id: 'tr-job' }, job.title), h('p', { class: 'muted small' }, job.does), h('ul', { class: 'tr-prospects' }, ...job.prospects.map(p => {
            const named = job.pick === p.id;
            return h('li', { class: named ? 'named' : '' }, h('div', { class: 'tr-who' }, h('strong', null, p.name), h('span', { class: 'muted small' }, ' · ' + p.kind), named ? h('span', { class: 'tr-pledge' }, 'Named') : null), h('p', { class: 'small' }, p.profile), h('dl', { class: 'ww-stats' }, h('dt', null, p.interviewed ? 'Ability' : 'Ability (first read)'), h('dd', null, meter(p.skill, p.name + ': ability')), h('dt', null, p.interviewed ? 'Loyalty' : 'Loyalty (first read)'), h('dd', null, meter(p.loyalty, p.name + ': loyalty')), ...(p.trait ? [h('dt', null, 'Character'), h('dd', null, p.trait + ': ' + (p.traitDesc ?? ''))] : []), h('dt', null, 'Background check'), h('dd', null, p.baggage === null ? 'Not done' : p.baggage === '' ? 'Clean' : 'Found: ' + p.baggage)), h('div', { class: 'cab-buttons' }, h('button', { class: 'btn small', type: 'button', disabled: !p.canInterview, onclick: () => doIt(p.id, 'interview', 'Interviewed.') }, 'Interview'), h('button', { class: 'btn small', type: 'button', disabled: !p.canCheck, onclick: () => doIt(p.id, 'check', 'Checked.') }, 'Background check'), h('button', { class: 'btn small primary', type: 'button', disabled: !p.canAnnounce, onclick: () => doIt(p.id, 'announce', `${p.name} is your ${job.title.toLowerCase()}.`) }, 'Announce')));
        }))));
    }
    draw();
    return root;
}
