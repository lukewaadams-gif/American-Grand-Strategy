import { h } from '../core/dom.js';
const pct = (x) => `${Math.round(x * 100)}%`;
const pts = (x) => `${x >= 0 ? '+' : '−'}${Math.abs(x).toFixed(1)}`;
export function messageTab(v, act) {
    const m = v.message;
    const issues = [...m.issues].sort((a, b) => b.weight * Math.max(0.1, 1 + b.advantage / 10) - a.weight * Math.max(0.1, 1 + a.advantage / 10));
    const current = m.issue ?? m.lastMessage;
    const pledged = m.plans.filter(p => p.pledged);
    return h('section', { class: 'tab-panel message-tab', 'aria-label': 'Message' }, h('div', { class: 'panel-head' }, h('h2', null, 'Message')), h('p', { class: 'muted small' }, 'One issue a week, nationally. It moves the race by how much voters care about it, by how far they trust your party on it, and by how long you stay on it.'), h('h3', { class: 'sub-head' }, 'This week’s message'), h('div', { class: 'msg-issues', role: 'radiogroup', 'aria-label': 'The week’s issue' }, ...issues.map(i => {
        const on = i.id === current;
        return h('button', { type: 'button', class: 'msg-issue' + (on ? ' on' : ''), role: 'radio', 'aria-checked': on ? 'true' : 'false',
            'aria-label': `${i.label}: ${pct(i.weight)} of voters’ attention, your party ${i.advantage >= 0 ? 'leads' : 'trails'} on it by ${Math.abs(i.advantage).toFixed(1)}`,
            onclick: () => act.order({ type: 'Message', issue: i.id }) }, h('span', { class: 'msg-issue-name' }, i.label), h('span', { class: 'msg-bar', 'aria-hidden': 'true' }, h('span', { class: 'msg-bar-fill', style: `width:${Math.min(100, i.weight * 400)}%` })), h('span', { class: 'msg-issue-fig' }, pct(i.weight)), h('span', { class: 'msg-issue-fig ' + (i.advantage >= 0 ? 'up' : 'down') }, pts(i.advantage)));
    })), h('p', { class: 'hint-line' }, `Attention, then your party’s edge on the issue in points. ${m.weeksOnMessage > 1 && current
        ? `On ${m.issues.find(i => i.id === current)?.label.toLowerCase()} for ${m.weeksOnMessage} weeks: staying on message makes it work harder.`
        : 'Staying on one message week after week makes it work harder.'}`), h('div', { class: 'sched-controls' }, h('button', { class: 'btn small' + (m.staffChooses ? ' on' : ''), type: 'button', 'aria-pressed': m.staffChooses ? 'true' : 'false',
        onclick: () => act.order({ type: 'Message', issue: null }) }, m.staffChooses ? 'The staff picks the issue each week: on' : 'Let the staff pick the issue each week')), h('h3', { class: 'sub-head' }, 'Tone'), h('div', { class: 'seg', role: 'radiogroup', 'aria-label': 'The message’s tone' }, ...m.tones.map(t => h('button', { type: 'button', class: 'seg-btn' + (t.id === m.tone ? ' on' : ''), role: 'radio', 'aria-checked': t.id === m.tone ? 'true' : 'false',
        title: t.detail, onclick: () => act.order({ type: 'MessageTone', tone: t.id }) }, t.label))), h('p', { class: 'hint-line' }, m.tones.find(t => t.id === m.tone)?.detail ?? ''), h('h3', { class: 'sub-head' }, m.canPledge ? `Plans and promises (${pledged.length} of ${m.pledgeMax})` : 'Your promises'), m.canPledge
        ? h('p', { class: 'muted small' }, 'Unveiling a plan wins a little support and makes its issue matter more. It is also a promise: if you win, it leads your agenda, and dropping it costs you with your base.')
        : h('p', { class: 'muted small' }, 'A re-election runs on the record: the promises of 2028 are judged by what became law.'), pledged.length ? h('ul', { class: 'plan-list pledged' }, ...pledged.map(p => h('li', null, h('strong', null, p.name), h('span', { class: 'faint' }, ` · ${p.issue}`)))) : null, m.canPledge && pledged.length < m.pledgeMax ? h('ul', { class: 'plan-list' }, ...m.plans.filter(p => !p.pledged).sort((a, b) => b.appeal - a.appeal).slice(0, 12).map(p => h('li', null, h('div', { class: 'plan-text' }, h('strong', null, p.name), h('span', { class: 'faint' }, ` · ${p.issue} · appeal ${pts(p.appeal)}${p.costBn ? ` · $${Math.round(p.costBn)}bn a year` : ''}`)), h('button', { class: 'btn small', type: 'button', onclick: () => act.pledge(p.id) }, 'Unveil')))) : null);
}
