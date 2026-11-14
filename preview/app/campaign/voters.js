import { h } from '../core/dom.js';
const lead = (x) => Math.abs(x) < 0.5 ? 'even' : `${x > 0 ? '+' : '\u2212'}${Math.abs(x).toFixed(0)}`;
export function votersDesk(v) {
    const rows = [...v.groups].sort((a, b) => b.share - a.share);
    const swing = (r) => r.margin - r.base;
    const best = [...rows].filter(r => r.share > 0.03).sort((a, b) => (b.movable * b.share) - (a.movable * a.share)).slice(0, 2);
    const turnout = [...rows].filter(r => r.share > 0.03 && r.margin > 0).sort((a, b) => a.turnout - b.turnout).slice(0, 1);
    const out = [
        h('p', { class: 'muted small' }, 'Who votes here, by group. Persuasion works on the movable; turnout work on your own side where it votes least.'),
        h('table', { class: 'mini-table voters' }, h('caption', { class: 'sr' }, 'Voters by group'), h('thead', null, h('tr', null, h('th', { scope: 'col' }, 'Group, share'), h('th', { scope: 'col', title: 'Your lead with the group now: positive is yours' }, 'Your lead'), h('th', { scope: 'col', title: 'The change since 2024: positive has moved your way' }, '± 2024'), h('th', { scope: 'col' }, 'Votes'), h('th', { scope: 'col', title: 'How much a point of swing moves the group, against the state as a whole' }, 'Moves'))), h('tbody', null, ...rows.map(r => h('tr', null, h('th', { scope: 'row' }, r.label, h('span', { class: 'faint' }, ` ${Math.round(r.share * 100)}%`)), h('td', { class: r.margin >= 0 ? 'up' : 'down' }, lead(r.margin)), h('td', { class: swing(r) >= 0 ? 'up' : 'down' }, `${swing(r) >= 0 ? '+' : '−'}${Math.abs(swing(r)).toFixed(1)}`), h('td', null, `${Math.round(r.turnout * 100)}%`), h('td', null, r.movable >= 1.25 ? 'very' : r.movable >= 0.85 ? 'some' : 'little'))))),
        best.length ? h('p', { class: 'hint-line' }, h('strong', null, 'Most to win by persuasion: '), best.map(r => r.label).join(', ') + '.') : null,
        turnout.length ? h('p', { class: 'hint-line' }, h('strong', null, 'Your side’s least likely voters: '), `${turnout[0].label} (${Math.round(turnout[0].turnout * 100)}% turn out).`) : null,
    ];
    return out.filter((x) => x !== null);
}
