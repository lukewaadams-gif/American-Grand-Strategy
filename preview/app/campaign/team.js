import { h } from '../core/dom.js';
import { openMenu } from '../core/menu.js';
import { portrait } from '../art/portrait.js';
import { cost } from './schedule.js';
const skillBar = (m) => h('span', { class: 'proj-bar team-skill', role: 'meter', 'aria-label': `${m.name}: skill ${m.skill} of 10`,
    'aria-valuemin': 0, 'aria-valuemax': 10, 'aria-valuenow': m.skill }, h('span', { class: 'proj-fill prep', style: `width:${m.skill * 10}%` }));
const pay = (m) => m.salary > 0 ? `${cost(m.salary)}/wk` : 'no extra cost';
const who = (m) => `${m.name}${m.real ? '' : m.default ? '' : ' (fictional)'}`;
export function teamTab(v, act) {
    const t = v.team;
    const states = [...v.states].filter(s => Math.abs(s.forPlayer) < 10).sort((a, b) => Math.abs(a.forPlayer) - Math.abs(b.forPlayer));
    const leadOf = (id) => t.leads.find(l => l.id === id) ?? t.leads[0];
    return h('section', { class: 'tab-panel team-tab', 'aria-label': 'Team' }, h('div', { class: 'panel-head' }, h('h2', null, 'Team')), h('h3', { class: 'sub-head' }, 'The senior staff'), h('p', { class: 'muted small' }, 'A better hire makes their part of the campaign work harder, for a weekly salary and a signing fee of two weeks’ pay. Real operatives’ ratings are the game’s judgement for play.'), ...t.posts.map(p => h('div', { class: 'team-row' }, h('div', { class: 'team-text' }, h('span', { class: 'kpi-label' }, p.label), h('strong', null, who(p.current)), h('span', { class: 'faint small' }, `${p.current.note} · ${pay(p.current)}`)), skillBar(p.current), h('button', { class: 'btn small', type: 'button', 'aria-haspopup': 'menu', 'aria-label': `${p.label}: ${p.current.name}. Change`,
        onclick: (e) => openMenu(e.currentTarget, `${p.label}: ${p.effect}`, p.options.map(o => ({
            label: `${who(o)} · skill ${o.skill}`, detail: `${o.note}. ${pay(o)}${o.salary > 0 ? `; ${cost(2 * o.salary)} to sign` : ''}.`,
            pick: () => act.order({ type: 'Hire', post: p.post, staff: o.id })
        }))) }, 'Change'))), h('h3', { class: 'sub-head' }, 'Surrogates on tour'), h('p', { class: 'muted small' }, 'Each week a surrogate tours a state or raises money. Moderates persuade; firebrands fire up the base. They pull more in their home region, most at home. A careless advance lead can turn a stop into a bad story.'), h('div', { class: 'sched-controls' }, h('button', { class: 'btn small' + (t.staffRuns ? ' on' : ''), type: 'button', 'aria-pressed': t.staffRuns ? 'true' : 'false',
        onclick: () => act.order({ type: 'Staff', area: 'SURROGATES', on: !t.staffRuns }) }, t.staffRuns ? 'Staff plans the tours: on' : 'Let the staff plan the tours')), ...t.surrogates.map(s => {
        const lead = leadOf(s.lead);
        const target = s.target === 'FUNDRAISE' ? 'Raising money' : s.target ? (v.states.find(x => x.code === s.target)?.name ?? s.target) : 'Off this week';
        return h('div', { class: 'surrogate-row' }, h('div', { class: 'surrogate-face' }, s.id ? portrait(s.id, v.player.party, 44, true, s.look ?? null) : h('span', { class: 'surrogate-initial', 'aria-hidden': 'true' }, '♥')), h('div', { class: 'team-text' }, h('span', { class: 'kpi-label' }, s.role), h('strong', null, s.name), h('span', { class: 'faint small' }, `${s.kind === 'persuade' ? 'Persuades' : 'Mobilises'} · pull ${s.pull.toFixed(1)} · ${s.region} (${s.home})`)), h('div', { class: 'surrogate-controls' }, h('button', { class: 'btn small', type: 'button', 'aria-haspopup': 'menu', 'aria-label': `${s.name} this week: ${target}. Change`,
            onclick: (e) => openMenu(e.currentTarget, `${s.name} this week`, [
                { label: 'Raise money', detail: 'Donor events: cash, as their pull allows', pick: () => act.order({ type: 'Surrogate', slot: s.slot, state: 'FUNDRAISE', lead: null }) },
                { label: 'Off this week', detail: 'No tour', pick: () => act.order({ type: 'Surrogate', slot: s.slot, state: null, lead: null }) },
                ...states.slice(0, 14).map(x => ({ label: `${x.name}`, detail: `Polls: ${x.forPlayer >= 0 ? 'you +' : 'them +'}${Math.abs(x.forPlayer).toFixed(1)} · ${x.ev} votes`,
                    pick: () => act.order({ type: 'Surrogate', slot: s.slot, state: x.code, lead: null }) }))
            ]) }, target), h('button', { class: 'btn small quiet', type: 'button', 'aria-haspopup': 'menu', 'aria-label': `${s.name}'s advance lead: ${lead.name}. Change`,
            onclick: (e) => openMenu(e.currentTarget, `Who runs ${s.name}'s tour`, t.leads.map(l => ({
                label: `${l.name} · skill ${l.skill}`, detail: `${l.note}. ${pay(l)}; ${l.risk >= 0.2 ? 'a real risk of a mishap' : l.risk >= 0.08 ? 'some risk of a mishap' : 'little risk'}.`,
                pick: () => act.order({ type: 'Surrogate', slot: s.slot, state: s.target ?? null, lead: l.id })
            }))) }, `Lead: ${lead.name}`)));
    }), t.canChooseMate && t.mates ? h('div', null, h('h3', { class: 'sub-head' }, 'The running mate'), h('p', { class: 'muted small' }, 'Your choice of running mate tours with the most pull, and joins you on the ticket.'), h('div', { class: 'send-row' }, ...t.mates.map(m => {
        const on = t.surrogates.find(s => s.slot === 0)?.id === m.id;
        return h('button', { class: 'btn small' + (on ? ' on' : ''), type: 'button', 'aria-pressed': on ? 'true' : 'false',
            title: `${m.kind === 'persuade' ? 'Persuades' : 'Mobilises'} · pull ${m.pull.toFixed(1)} · ${m.home}`,
            onclick: () => act.order({ type: 'RunningMate', candidate: m.id }) }, m.name);
    }))) : null);
}
