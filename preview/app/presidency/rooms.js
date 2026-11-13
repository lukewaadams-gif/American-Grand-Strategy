// Two rooms reached from the Oval Office. Through the windows, the country: approval and where it is heading, what
// its target is made of (Resolute Desk's model, part by part), approval on each issue, the coalitions and the states.
// In the briefing book, the economy: the figures with their change over the year and their trend, more figures, the
// next releases, and each figure's line over the term.
import { h, s } from '../core/dom.js';
import { day } from '../core/format.js';
import { STATES, VIEWBOX } from '../generated/usStates.js';
import { divergingBars, lineChart, sparkline } from './charts.js';
import { signed } from './score.js';
const PARTS = [
    ['economy', 'The economy', 'Growth, jobs, prices and gas against what people expect, and how they feel about it'],
    ['issues', 'The issues', 'Your standing on the issues people care about, weighted by how much they care'],
    ['appeal', 'Your appeal', 'The president’s charm, and the difficulty you chose'],
    ['honeymoon', 'The honeymoon', 'A new president’s grace period, bigger after a bigger win; it fades week by week'],
    ['record', 'Your week’s work', 'Ceremonies, remarks, trips and time with family add up; it fades slowly'],
    ['events', 'Events and laws', 'What happened and what you signed, fading over a few months'],
    ['lasting', 'The lasting record', 'The part of your laws’ effect that stays'],
    ['scandal', 'Scandal', 'Trouble weighs on approval until it fades'],
    ['wear', 'The wear of office', 'Nothing for six months, then a slow slide'],
    ['mood', 'The public mood', 'A slow drift nobody controls, about two points either way'],
];
export function countryRoom(c, back) {
    const p = c.parts;
    const parts = PARTS.map(([k, label, detail]) => ({ label, value: p[k] ?? 0, detail }));
    const squeeze = (p['target'] ?? 0) - (p['raw'] ?? 0) - (p['rally'] ?? 0);
    const prev = c.history.length >= 2 ? c.history[c.history.length - 2].value : c.approval;
    const change = c.approval - prev;
    const issues = [...c.issues].sort((a, b) => b.salience - a.salience);
    const top = Math.max(...issues.map(i => i.salience));
    const ranked = [...c.states].sort((a, b) => b.approval - a.approval);
    return h('div', { class: 'room ctry' }, h('div', { class: 'room-top' }, h('div', null, h('div', { class: 'eyebrow' }, 'Through the windows'), h('h2', { class: 'dialog-title' }, 'The country'), h('p', { class: 'muted' }, 'Week of ' + day(c.date, true))), h('button', { class: 'btn primary', type: 'button', onclick: back }, 'Back to the Oval Office')), h('div', { class: 'room-grid two' }, h('section', { class: 'room-card', 'aria-labelledby': 'ctry-app' }, h('h3', { class: 'room-card-title', id: 'ctry-app' }, 'Approval'), h('div', { class: 'ctry-hero' }, h('span', { class: 'ctry-hero-value' }, Math.round(c.approval) + '%'), h('span', { class: 'ctry-hero-sub' }, `${change >= 0 ? 'Up' : 'Down'} ${Math.abs(change).toFixed(1)} this week · heading for ${Math.round(p['target'] ?? c.approval)}%`)), lineChart({ title: 'Approval week by week', dates: c.history.map(x => x.date), series: [{ label: 'Approval', values: c.history.map(x => x.value) }],
        digits: 1, unit: '%', height: 190, band: { lo: c.floor, hi: c.ceiling, label: `Partisanship keeps it between ${Math.round(c.floor)} and ${Math.round(c.ceiling)}` } })), h('section', { class: 'room-card', 'aria-labelledby': 'ctry-parts' }, h('h3', { class: 'room-card-title', id: 'ctry-parts' }, 'What moves approval'), h('p', { class: 'muted small' }, `Approval moves a sixth of the way each week toward a target. The era starts it at ${Math.round(p['base'] ?? 45)}; these push it up or down.`), divergingBars('Points above or below the era’s baseline', parts), h('p', { class: 'ctry-sum' }, `${Math.round(p['base'] ?? 45)} + these = ${(p['raw'] ?? 0).toFixed(1)}`
        + (Math.abs(squeeze) >= 0.05 ? `; partisanship ${squeeze > 0 ? 'lifts' : 'trims'} it by ${Math.abs(squeeze).toFixed(1)}` : '')
        + ((p['rally'] ?? 0) >= 0.05 ? `; a rally adds ${(p['rally'] ?? 0).toFixed(1)}` : '')
        + `. Target: ${(p['target'] ?? 0).toFixed(1)}%.`))), h('div', { class: 'room-grid two' }, h('section', { class: 'room-card', 'aria-labelledby': 'ctry-issues' }, h('h3', { class: 'room-card-title', id: 'ctry-issues' }, 'Approval on the issues'), h('p', { class: 'muted small' }, 'Ordered by how much voters care. Policy time builds your command of an issue; remarks and policy time build your standing on it.'), h('table', { class: 'ctry-issues' }, h('thead', null, h('tr', null, h('th', { scope: 'col' }, 'Issue'), h('th', { scope: 'col' }, 'How much it matters'), h('th', { scope: 'col' }, 'Approve'), h('th', { scope: 'col' }, 'Command'))), h('tbody', null, ...issues.map(i => h('tr', null, h('th', { scope: 'row' }, i.label, i.top ? h('span', { class: 'pill' }, 'needs you most') : null), h('td', null, h('span', { class: 'ctry-meter', 'aria-hidden': 'true' }, h('span', { style: `width:${(i.salience / top) * 100}%` })), h('span', { class: 'sr' }, `${Math.round(i.salience * 100)}% of what voters weigh`)), h('td', null, h('span', { class: 'ctry-bar', 'aria-hidden': 'true' }, h('span', { style: `width:${i.approval}%` }), h('span', { class: 'ctry-bar-mid' })), h('span', { class: 'ctry-bar-value' }, Math.round(i.approval) + '%')), h('td', null, String(Math.round(i.command)))))))), h('section', { class: 'room-card', 'aria-labelledby': 'ctry-who' }, h('h3', { class: 'room-card-title', id: 'ctry-who' }, 'Who approves'), h('dl', { class: 'ctry-coalitions' }, ...c.coalitions.flatMap(k => [h('dt', null, k.label), h('dd', null, Math.round(k.approval) + '%')])), stateMap(c), h('div', { class: 'ctry-ends' }, h('div', null, h('h4', null, 'Warmest'), h('ol', null, ...ranked.slice(0, 5).map(st => h('li', null, `${st.name} ${Math.round(st.approval)}%`)))), h('div', null, h('h4', null, 'Coolest'), h('ol', null, ...ranked.slice(-5).reverse().map(st => h('li', null, `${st.name} ${Math.round(st.approval)}%`))))), h('details', { class: 'pv-table' }, h('summary', null, 'Every state'), h('table', null, h('thead', null, h('tr', null, h('th', { scope: 'col' }, 'State'), h('th', { scope: 'col' }, 'Approve'), h('th', { scope: 'col' }, 'Electoral votes'))), h('tbody', null, ...ranked.map(st => h('tr', null, h('th', { scope: 'row' }, st.name), h('td', null, Math.round(st.approval) + '%'), h('td', null, String(st.ev))))))))));
}
/** Approval by state as a shaded map (one hue, lighter is higher); the lists and the table carry the numbers. */
function stateMap(c) {
    const app = new Map(c.states.map(st => [st.code, st]));
    const svg = s('svg', { class: 'ctry-map', viewBox: `0 0 ${VIEWBOX.width} ${VIEWBOX.height}`, role: 'img',
        'aria-label': 'A map of approval by state; the lists beside it and the table below give the numbers.' });
    for (const st of STATES) {
        const a = app.get(st.code)?.approval ?? c.approval;
        const p = s('path', { d: st.d, class: 'cal-state', style: `--a:${Math.max(0, Math.min(1, (a - 30) / 40)).toFixed(2)}` }, s('title', null, `${st.name}: ${Math.round(a)}%`));
        svg.append(p);
    }
    return h('figure', { class: 'ctry-map-wrap' }, svg, h('figcaption', { class: 'muted small' }, 'Lighter is higher. Each state leans as it voted in 2028; visits lift it for a few weeks.'));
}
// ---- the briefing book ----------------------------------------------------------------------------------------------
/** A series without the weeks a save from before the structural economy did not record. */
const known = (v) => (v ?? []).filter((x) => x !== null);
function tileView(t, series) {
    const unit = t.unit.startsWith('$') ? '$' : t.unit.startsWith('%') ? '%' : '';
    const val = (v) => (unit === '$' ? '$' + v.toFixed(t.digits) : v.toFixed(t.digits) + (unit === '%' ? '%' : ''));
    const base = t.yearAgo ?? t.atOath;
    const since = t.yearAgo !== undefined ? 'on a year ago' : 'since the oath';
    let delta = null;
    if (base !== undefined) {
        const d = t.value - base, flat = Math.abs(d) < Math.pow(10, -t.digits) / 2;
        const good = t.upIsGood === null || flat ? 'flat' : (d > 0) === t.upIsGood ? 'good' : 'bad';
        delta = h('span', { class: 'econ-delta ' + good }, flat ? `Unchanged ${since}` : `${d > 0 ? '▲ up' : '▼ down'} ${unit === '$' ? '$' + Math.abs(d).toFixed(t.digits) : Math.abs(d).toFixed(t.digits)} ${since}`);
    }
    return h('div', { class: 'econ-tile' }, h('span', { class: 'econ-label' }, t.label), h('span', { class: 'econ-value' }, val(t.value)), h('span', { class: 'econ-unit' }, t.unit.replace(/^[%$] ?/, '') || ' '), delta, series && series.length > 1 ? sparkline(series, 160, 26) : h('span', { class: 'pv-spark-none' }));
}
export function briefingBook(e, back) {
    const hs = {}, d = e.history.dates;
    for (const [k, v] of Object.entries(e.history.series))
        hs[k] = (v ?? []).map(x => x ?? NaN);
    const chart = (title, key, digits, unit, extra = {}) => lineChart({ title, dates: d, series: [{ label: title, values: hs[key] ?? [] }], digits, unit, height: 150, ...extra });
    return h('div', { class: 'room econ' }, h('div', { class: 'room-top' }, h('div', null, h('div', { class: 'eyebrow' }, 'The briefing book'), h('h2', { class: 'dialog-title' }, 'The economy'), h('p', { class: 'muted' }, 'From the Council of Economic Advisers, week of ' + day(e.date, true))), h('button', { class: 'btn primary', type: 'button', onclick: back }, 'Back to the Oval Office')), h('section', { class: 'econ-tiles', 'aria-label': 'The figures' }, ...e.tiles.map(t => tileView(t, known(e.history.series[t.id])))), h('div', { class: 'room-grid three' }, chart('Growth', 'growth', 1, '%', { zero: true }), chart('Unemployment', 'unemployment', 1, '%'), chart('Inflation', 'inflation', 1, '%', { reference: { value: 2, label: 'The Fed’s 2% target' } }), chart('Gas', 'gas', 2, '$'), lineChart({ title: 'Interest rates', dates: d, series: [{ label: 'Fed funds', values: hs['fed'] ?? [] }, { label: '10-year Treasury', values: hs['tenYear'] ?? [] }], digits: 2, unit: '%', height: 150 }), chart('Deficit, % of GDP', 'deficit', 1, '%')), h('div', { class: 'room-grid two' }, h('section', { class: 'room-card', 'aria-labelledby': 'econ-next' }, h('h3', { class: 'room-card-title', id: 'econ-next' }, 'Coming up'), h('ul', { class: 'econ-list' }, ...e.next.map(n => h('li', null, h('span', null, n.what), h('span', { class: 'muted' }, day(n.date, true)))))), h('section', { class: 'room-card', 'aria-labelledby': 'econ-more' }, h('h3', { class: 'room-card-title', id: 'econ-more' }, 'More figures'), h('dl', { class: 'econ-more' }, ...e.more.flatMap(m => [h('dt', null, m.label), h('dd', null, m.value)])))), pricesByPart(e), h('div', { class: 'room-grid two' }, sectorsCard(e), budgetCard(e)), lawsCard(e));
}
/** Inflation by part (the consumer price index's six parts, by weight), each with its year so far. */
function pricesByPart(e) {
    return h('section', { class: 'room-card', 'aria-labelledby': 'econ-parts' }, h('h3', { class: 'room-card-title', id: 'econ-parts' }, 'Prices, part by part'), h('p', { class: 'muted small' }, `Headline inflation is these six parts by their weight in the index. Without food and energy (core), prices are up ${e.core.toFixed(1)}% over the year.`), h('div', { class: 'econ-parts' }, ...e.parts.map(p => {
        const series = known(e.history.series[p.key]);
        return h('div', { class: 'econ-part' }, h('span', { class: 'econ-label' }, p.label), h('span', { class: 'econ-value' }, p.value.toFixed(1) + '%'), h('span', { class: 'econ-unit' }, `${Math.round(p.weight * 100)}% of the index · ${p.detail.toLowerCase()}`), series.length > 1 ? sparkline(series, 160, 26) : h('span', { class: 'pv-spark-none' }));
    })));
}
function sectorsCard(e) {
    return h('section', { class: 'room-card', 'aria-labelledby': 'econ-sectors' }, h('h3', { class: 'room-card-title', id: 'econ-sectors' }, 'The industries'), h('ul', { class: 'econ-sectors' }, ...e.sectors.map(s => {
        const series = known(e.history.series[s.id]);
        return h('li', null, h('span', { class: 'econ-sector-name' }, s.label, h('span', { class: 'econ-unit' }, s.unit)), h('span', { class: 'econ-sector-value' }, s.value.toFixed(s.digits)), series.length > 1 ? sparkline(series, 110, 22) : h('span', { class: 'pv-spark-none' }), h('span', { class: 'econ-sector-drivers muted small' }, s.drivers));
    })));
}
/** The budget as CBO keeps it: revenue against each kind of outlay, as bars of one hue on a shared scale. */
function budgetCard(e) {
    const b = e.budget;
    const rows = [['Revenue', b.revenue], ['Mandatory spending', b.mandatory], ['Discretionary spending', b.discretionary],
        ['Interest on the debt', b.interest], ['Your laws', b.laws]];
    const max = Math.max(...rows.map(r => Math.abs(r[1])), 1);
    return h('section', { class: 'room-card', 'aria-labelledby': 'econ-budget' }, h('h3', { class: 'room-card-title', id: 'econ-budget' }, 'The budget'), h('p', { class: 'muted small' }, 'Percent of GDP this year. Mandatory spending grows as the country ages; interest grows as the debt rolls over at today’s rates.'), h('ul', { class: 'econ-budget' }, ...rows.map(([label, v]) => h('li', null, h('span', { class: 'econ-budget-label' }, label), h('span', { class: 'econ-budget-track', 'aria-hidden': 'true' }, h('span', { class: 'econ-budget-bar' + (label === 'Revenue' ? ' in' : ''), style: `width:${(Math.abs(v) / max) * 100}%` })), h('span', { class: 'econ-budget-value' }, (v < 0 ? '\u2212' : '') + Math.abs(v).toFixed(1) + '%')))), h('dl', { class: 'econ-more' }, h('dt', null, 'Deficit'), h('dd', null, b.deficit.toFixed(1) + '% of GDP'), h('dt', null, 'Debt'), h('dd', null, b.debt.toFixed(1) + '% of GDP'), h('dt', null, 'Average rate on the debt'), h('dd', null, b.averageRate.toFixed(2) + '%')));
}
/** A law whose effects so far all round to nothing. */
const tooSmall = (l) => [l.output, l.unemployment, l.prices, l.deficit].every(v => Math.abs(v) < 0.005);
/** What each law has done so far, from its own channels; the Fed's response to all of them is its own line. */
function lawsCard(e) {
    const rows = e.laws.rows;
    const cell = (v, unit) => h('td', null, Math.abs(v) < 0.005 ? '0' : signed(v) + unit);
    return h('section', { class: 'room-card', 'aria-labelledby': 'econ-laws' }, h('h3', { class: 'room-card-title', id: 'econ-laws' }, 'What your laws have done'), rows.length ? h('div', null, h('p', { class: 'muted small' }, 'So far, from each law’s own channels: its money (through demand) and its effects on work, productivity and prices. Each law’s luck is part of it: the score said what it would do within a range; this is what it has done.'), h('div', { class: 'econ-laws-wrap' }, h('table', { class: 'econ-laws' }, h('thead', null, h('tr', null, h('th', { scope: 'col' }, 'Law'), h('th', { scope: 'col' }, 'Output'), h('th', { scope: 'col' }, 'Unemployment'), h('th', { scope: 'col' }, 'Prices'), h('th', { scope: 'col' }, 'Deficit this year'))), h('tbody', null, ...rows.map((l) => h('tr', null, h('th', { scope: 'row' }, l.label, h('span', { class: 'econ-law-since muted small' }, (l.since ? 'Since ' + day(l.since, true) : '') + (l.started ? '' : ' · not yet in effect')), l.fullEffect.length ? h('details', { class: 'econ-law-full' }, h('summary', null, 'What it does at full effect'), h('ul', null, ...l.fullEffect.map(f => h('li', null, f)))) : null), ...(tooSmall(l) ? [h('td', { colspan: 4, class: 'muted' }, l.started ? 'Too early to show: its effects are still building' : 'Not yet in effect')]
        : [cell(l.output, '%'), cell(l.unemployment, ' pts'), cell(l.prices, '%'), cell(l.deficit, '% of GDP')])))), h('tfoot', null, h('tr', null, h('th', { scope: 'row' }, 'The Fed’s response', h('span', { class: 'muted small econ-law-since' }, 'to all of them at once: an estimate')), h('td', { colspan: 4 }, Math.abs(e.laws.fed) < 0.05 ? 'Too small to move rates' : `Rates about ${Math.abs(e.laws.fed).toFixed(2)} points ${e.laws.fed > 0 ? 'higher' : 'lower'} than they would be, ${e.laws.fed > 0 ? 'holding back' : 'adding to'} part of what the laws do`))))))
        : h('p', { class: 'muted' }, 'No law has passed yet. When one does, the budget office’s score runs in The Capital Ledger, and its effects build here week by week.'));
}
