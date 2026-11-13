import { h } from '../core/dom.js';
export function decisionCard(d, n, total, host, choose, after) {
    const options = d.options.map((o, i) => h('button', { class: 'choice', type: 'button', 'data-key': String(i + 1),
        'aria-label': `Option ${i + 1}: ${o.label}${o.tag ? ' (' + o.tag + ')' : ''}. ${o.detail}`,
        onclick: () => {
            const news = choose(o.command);
            if (news === null)
                return;
            whatHappened(o.label, news, host, after);
        } }, h('span', { class: 'choice-head' }, h('span', { class: 'choice-key', 'aria-hidden': 'true' }, String(i + 1)), h('span', { class: 'choice-title' }, o.label), o.tag ? h('span', { class: 'pill tone-' + (o.tone ?? 'INFO').toLowerCase() }, o.tag) : null), h('span', { class: 'choice-detail' }, o.detail)));
    const card = h('div', { class: 'dialog-card' }, h('div', { class: 'eyebrow' }, d.eyebrow + (total > 1 ? ` · ${n} of ${total}` : '')), h('h2', { class: 'dialog-title' }, d.title), h('p', null, d.body), h('div', { class: 'choices' }, ...options), h('div', { class: 'dialog-foot' }, h('button', { class: 'btn quiet', type: 'button', onclick: () => host.close(), title: 'Put it aside; the week cannot run until it is answered' }, 'Decide later'), h('span', { class: 'faint' }, `Press 1–${d.options.length} to choose, Esc to decide later.`)));
    card.addEventListener('keydown', e => {
        const k = Number(e.key);
        if (k >= 1 && k <= options.length && !e.ctrlKey && !e.metaKey) {
            e.preventDefault();
            options[k - 1].click();
        }
    });
    host.open(card, d.title);
}
function whatHappened(choice, news, host, after) {
    const lines = news.map(n => n.text).filter(t => !t.startsWith('Decision: '));
    const go = h('button', { class: 'btn primary', type: 'button', onclick: () => { host.close(); after(); } }, 'Continue');
    host.open(h('div', { class: 'dialog-card' }, h('div', { class: 'eyebrow' }, 'What happened'), h('h2', { class: 'dialog-title' }, 'You chose: ' + choice), lines.length ? h('ul', { class: 'happened' }, ...lines.map(l => h('li', null, l))) : h('p', { class: 'muted' }, 'The choice is recorded; its effects arrive as the week resolves.'), h('div', { class: 'dialog-foot' }, go)), 'What happened', after);
}
