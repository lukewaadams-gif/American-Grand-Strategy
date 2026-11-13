// The presidency's data graphics: a line over the term with a crosshair readout (pointer and keyboard), a sparkline
// for a figure's tile, and diverging bars for what approval is made of. Thin marks, hairline axes, values in text
// colours, one series per chart unless two share a unit (then a legend and end labels). Every chart's numbers are
// also in a table, so nothing is reachable only by hovering. Charts draw at their real width so text keeps its size.
import { h, s } from '../core/dom.js';
import { day } from '../core/format.js';
const COLOURS = ['var(--viz-1)', 'var(--viz-2)'];
function niceStep(raw) {
    const mag = Math.pow(10, Math.floor(Math.log10(Math.max(raw, 1e-6))));
    for (const m of [1, 2, 2.5, 5, 10])
        if (m * mag >= raw)
            return m * mag;
    return 10 * mag;
}
const fmt = (v, digits, unit) => (unit === '$' ? '$' + v.toFixed(digits) : v.toFixed(digits) + unit);
/** A line chart over the term, with a crosshair that follows the pointer or the arrow keys. */
export function lineChart(o) {
    const height = o.height ?? 170;
    const n = o.dates.length;
    const fig = h('figure', { class: 'pv-chart' });
    const head = h('figcaption', { class: 'pv-chart-title' }, o.title);
    const svg = s('svg', { height, width: '100%', class: 'pv-plot', tabindex: n > 1 ? 0 : null, role: 'img',
        'aria-label': `${o.title}. ${n > 1 ? o.series.map(sr => `${sr.label} from ${fmt(sr.values[0], o.digits, o.unit)} to ${fmt(sr.values[n - 1], o.digits, o.unit)}`).join('; ') + '. Arrow keys read week by week.' : 'Not enough weeks yet.'}` });
    const tip = h('div', { class: 'pv-tip', 'aria-live': 'polite' });
    const legend = o.series.length > 1 ? h('ul', { class: 'pv-legend' }, ...o.series.map((sr, i) => h('li', null, h('span', { class: 'pv-key', style: `background:${sr.colour ?? COLOURS[i]}`, 'aria-hidden': 'true' }), sr.label))) : null;
    fig.append(head, legend ?? '', h('div', { class: 'pv-plot-wrap' }, svg, tip), table(o));
    let at = n - 1, X = (_) => 0, Y = (_) => 0, cross = null, dots = [];
    const show = (i) => {
        if (!cross || n < 2)
            return;
        at = Math.max(0, Math.min(n - 1, i));
        cross.setAttribute('x1', String(X(at)));
        cross.setAttribute('x2', String(X(at)));
        cross.style.opacity = '1';
        dots.forEach((d, k) => { d.setAttribute('cx', String(X(at))); d.setAttribute('cy', String(Y(o.series[k].values[at]))); d.style.opacity = '1'; });
        tip.replaceChildren(h('span', { class: 'pv-tip-date' }, day(o.dates[at], true)), ...o.series.map((sr, k) => h('span', { class: 'pv-tip-row' }, h('span', { class: 'pv-tip-key', style: `background:${sr.colour ?? COLOURS[k]}`, 'aria-hidden': 'true' }), h('strong', null, fmt(sr.values[at], o.digits, o.unit)), o.series.length > 1 ? ' ' + sr.label : '')));
        // inside the plot, beside the crosshair (on its left near the right edge)
        const left = X(at), w = svg.getBoundingClientRect().width, tw = tip.offsetWidth || 110;
        tip.style.left = (left + 12 + tw > w ? left - 12 - tw : left + 12) + 'px';
        tip.classList.add('show');
    };
    const hide = () => { if (cross)
        cross.style.opacity = '0'; dots.forEach(d => (d.style.opacity = '0')); tip.classList.remove('show'); };
    const draw = (width) => {
        svg.replaceChildren();
        svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
        if (n < 2) {
            svg.append(s('text', { x: width / 2, y: height / 2, class: 'pv-empty', 'text-anchor': 'middle' }, 'The line starts after a few weeks.'));
            return;
        }
        const all = o.series.flatMap(sr => sr.values).concat(o.band ? [o.band.lo, o.band.hi] : []).concat(o.reference ? [o.reference.value] : []).concat(o.zero ? [0] : []);
        const lo0 = Math.min(...all), hi0 = Math.max(...all), pad = Math.max((hi0 - lo0) * 0.08, 0.1);
        const step = niceStep((hi0 - lo0 + 2 * pad) / 4), lo = Math.floor((lo0 - pad) / step) * step, hi = Math.ceil((hi0 + pad) / step) * step;
        const x0 = 46, x1 = width - 64, y0 = 10, y1 = height - 24;
        X = i => x0 + ((x1 - x0) * i) / (n - 1);
        Y = v => y1 - ((v - lo) / (hi - lo)) * (y1 - y0);
        if (o.band)
            svg.append(s('rect', { x: x0, width: x1 - x0, y: Y(o.band.hi), height: Y(o.band.lo) - Y(o.band.hi), class: 'pv-band' }));
        for (let t = lo; t <= hi + 1e-9; t += step)
            svg.append(s('line', { x1: x0, x2: x1, y1: Y(t), y2: Y(t), class: 'pv-grid' }), s('text', { x: x0 - 8, y: Y(t) + 4, class: 'pv-tick', 'text-anchor': 'end' }, fmt(t, step < 1 ? (step < 0.1 ? 2 : 1) : 0, o.unit)));
        if (o.reference)
            svg.append(s('line', { x1: x0, x2: x1, y1: Y(o.reference.value), y2: Y(o.reference.value), class: 'pv-ref' }), s('text', { x: x0 + 4, y: Y(o.reference.value) - 4, class: 'pv-tick' }, o.reference.label));
        if (o.band)
            svg.append(s('text', { x: x1 - 4, y: Y(o.band.hi) + 14, class: 'pv-tick', 'text-anchor': 'end' }, o.band.label));
        o.series.forEach((sr, k) => {
            const c = sr.colour ?? COLOURS[k];
            if (o.series.length === 1)
                svg.append(s('polygon', { class: 'pv-area', fill: c,
                    points: `${X(0)},${y1} ` + sr.values.map((v, i) => `${X(i)},${Y(v)}`).join(' ') + ` ${X(n - 1)},${y1}` }));
            svg.append(s('polyline', { class: 'pv-line', stroke: c, points: sr.values.map((v, i) => `${X(i)},${Y(v)}`).join(' ') }), s('circle', { cx: X(n - 1), cy: Y(sr.values[n - 1]), r: 4, fill: c, class: 'pv-end' }), s('text', { x: X(n - 1) + 8, y: Y(sr.values[n - 1]) + 4, class: 'pv-end-label' }, fmt(sr.values[n - 1], o.digits, o.unit)));
        });
        const ticks = [...new Set([0, Math.floor((n - 1) / 2), n - 1])];
        for (const i of ticks)
            svg.append(s('text', { x: X(i), y: height - 6, class: 'pv-tick', 'text-anchor': i === 0 ? 'start' : i === n - 1 ? 'end' : 'middle' }, day(o.dates[i], true)));
        cross = s('line', { y1: y0, y2: y1, class: 'pv-cross', style: 'opacity:0' });
        dots = o.series.map((sr, k) => s('circle', { r: 5, class: 'pv-dot', fill: sr.colour ?? COLOURS[k], style: 'opacity:0' }));
        svg.append(cross, ...dots);
    };
    svg.addEventListener('pointermove', e => {
        const r = svg.getBoundingClientRect(), x = e.clientX - r.left;
        let best = 0;
        for (let i = 1; i < n; i++)
            if (Math.abs(X(i) - x) < Math.abs(X(best) - x))
                best = i;
        show(best);
    });
    svg.addEventListener('pointerleave', hide);
    svg.addEventListener('focus', () => show(at));
    svg.addEventListener('blur', hide);
    svg.addEventListener('keydown', e => {
        const step = e.shiftKey ? 4 : 1;
        if (e.key === 'ArrowLeft') {
            e.preventDefault();
            show(at - step);
        }
        else if (e.key === 'ArrowRight') {
            e.preventDefault();
            show(at + step);
        }
        else if (e.key === 'Home') {
            e.preventDefault();
            show(0);
        }
        else if (e.key === 'End') {
            e.preventDefault();
            show(n - 1);
        }
    });
    new ResizeObserver(es => { const w = Math.round(es[0].contentRect.width); if (w > 0)
        draw(w); }).observe(svg);
    return fig;
}
/** The chart's numbers, every fourth week, in a table behind a disclosure. */
function table(o) {
    const rows = [];
    for (let i = o.dates.length - 1; i >= 0; i -= 4)
        rows.push(i);
    return h('details', { class: 'pv-table' }, h('summary', null, 'The numbers'), h('table', null, h('thead', null, h('tr', null, h('th', { scope: 'col' }, 'Week of'), ...o.series.map(sr => h('th', { scope: 'col' }, sr.label)))), h('tbody', null, ...rows.map(i => h('tr', null, h('th', { scope: 'row' }, day(o.dates[i], true)), ...o.series.map(sr => h('td', null, fmt(sr.values[i], o.digits, o.unit))))))));
}
/** A tile's trend: the term so far, the latest week marked. Decorative: the tile states the figure in words. */
export function sparkline(values, width = 120, height = 32) {
    const svg = s('svg', { class: 'pv-spark', viewBox: `0 0 ${width} ${height}`, preserveAspectRatio: 'none', 'aria-hidden': 'true', focusable: 'false' });
    if (values.length < 2)
        return svg;
    const lo = Math.min(...values), hi = Math.max(...values), span = hi - lo || 1;
    const X = (i) => 2 + ((width - 8) * i) / (values.length - 1), Y = (v) => height - 4 - ((v - lo) / span) * (height - 8);
    svg.append(s('polyline', { class: 'pv-spark-line', points: values.map((v, i) => `${X(i)},${Y(v)}`).join(' ') }), s('circle', { class: 'pv-spark-end', cx: X(values.length - 1), cy: Y(values[values.length - 1]), r: 3 }));
    return svg;
}
/** Diverging bars from zero: what lifts approval in blue to the right, what holds it down in orange to the left, each labelled. */
export function divergingBars(title, parts, unit = ' pts') {
    const max = Math.max(1, ...parts.map(p => Math.abs(p.value)));
    return h('figure', { class: 'pv-diverge' }, h('figcaption', { class: 'pv-chart-title' }, title), h('ul', { class: 'pv-diverge-rows' }, ...parts.map(p => {
        const w = (Math.abs(p.value) / max) * 50, pos = p.value >= 0;
        const text = (p.value >= 0 ? '+' : '−') + Math.abs(p.value).toFixed(1) + unit;
        return h('li', { class: 'pv-diverge-row', tabindex: 0, title: `${p.label}: ${text}. ${p.detail}`, 'aria-label': `${p.label}: ${text}. ${p.detail}` }, h('span', { class: 'pv-diverge-label' }, p.label), h('span', { class: 'pv-diverge-track', 'aria-hidden': 'true' }, h('span', { class: 'pv-diverge-zero' }), Math.abs(p.value) >= 0.05 ? h('span', { class: 'pv-diverge-bar ' + (pos ? 'up' : 'down'), style: pos ? `left:50%;width:${w}%` : `right:50%;width:${w}%` }) : null), h('span', { class: 'pv-diverge-value' }, text));
    })));
}
