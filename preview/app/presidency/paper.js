// The Capital Ledger (Resolute Desk's ui/Paper, Q29: a full front page every week): the masthead and the edition, the
// week's biggest Washington story with a photograph of the president, six more stories in three columns (the paper's
// regular features fill the gaps), In Brief, and By the Numbers beside the page. It opens after every week and from the
// papers on the coffee table.
import { h } from '../core/dom.js';
import { portrait } from '../art/portrait.js';
import { dateLong } from './oval-screen.js';
export function capitalLedger(p, v, back) {
    const cells = p.stories.map(st => h('article', { class: 'lg-story' }, st.section ? h('div', { class: 'lg-kicker' }, st.section) : null, h('h3', { class: 'lg-head' }, st.head), st.body ? h('p', { class: 'lg-text' }, st.body) : null));
    for (const f of p.features) {
        if (cells.length >= 6)
            break;
        cells.push(h('article', { class: 'lg-story feature' }, h('h3', { class: 'lg-head' }, f.head), h('p', { class: 'lg-text' }, f.body)));
    }
    const photo = portrait(v.president.id, v.president.party, 168);
    photo.setAttribute('class', 'portrait lg-photo-img');
    const page = h('div', { class: 'lg-page' }, h('header', { class: 'lg-mast' }, h('h2', { class: 'lg-title' }, 'The Capital Ledger'), h('div', { class: 'lg-dateline' }, h('span', null, dateLong(p.date).toUpperCase()), h('span', null, p.edition), h('span', null, 'WASHINGTON EDITION'))), h('section', { class: 'lg-lead', 'aria-label': 'The lead story' }, h('h3', { class: 'lg-lead-head' }, p.lead.head), h('div', { class: 'lg-lead-body' }, h('figure', { class: 'lg-photo' }, photo, h('figcaption', null, p.photo)), h('div', { class: 'lg-lead-text' }, ...p.lead.body.split('\n\n').map(par => h('p', null, par))))), h('section', { class: 'lg-columns', 'aria-label': 'More stories' }, ...cells), p.brief.length ? h('section', { class: 'lg-brief', 'aria-labelledby': 'lg-brief-title' }, h('h3', { class: 'lg-brief-title', id: 'lg-brief-title' }, 'In Brief'), h('ul', null, ...p.brief.map(b => h('li', null, (b.section ? b.section + ': ' : '') + b.head)))) : null);
    const numbers = h('aside', { class: 'lg-side', 'aria-labelledby': 'lg-numbers-title' }, h('h3', { class: 'lg-side-title', id: 'lg-numbers-title' }, 'By the numbers'), h('dl', { class: 'lg-numbers' }, ...p.numbers.flatMap(n => [h('dt', null, n.label), h('dd', null, n.value)])), p.developing && p.developing.length ? h('h3', { class: 'lg-side-title', id: 'lg-dev-title' }, 'Developing stories') : null, p.developing && p.developing.length ? h('ul', { class: 'lg-dev', 'aria-labelledby': 'lg-dev-title' }, ...p.developing.map(d => h('li', { class: d.active ? '' : 'past' }, h('span', { class: 'lg-dev-name' }, d.name), h('span', { class: 'lg-dev-stage' }, d.active ? `Stage ${d.stage} of ${d.stages} · ${d.trend}` : d.trend), d.active ? h('span', { class: 'lg-dev-heat ' + (d.heat >= 60 ? 'hot' : d.heat >= 45 ? 'warm' : 'cool'), 'aria-hidden': 'true' }, h('span', { style: `width:${d.heat}%` })) : null))) : null, h('button', { class: 'btn primary lg-back', type: 'button', onclick: back }, 'To the Oval Office'), h('p', { class: 'lg-hint' }, 'Enter or Escape'));
    return h('div', { class: 'lg' }, page, numbers);
}
