// The campaign's map of the states: real shapes (Census, Albers USA with Alaska and Hawaii inset), coloured by the
// chosen layer, labelled with the state and its figure, the small north-eastern states in callout boxes as on a
// broadcast map. One Tab stop: the arrow keys move between neighbouring states, Enter or a click picks one.
import { CALLOUTS, NATION, STATES, VIEWBOX } from '../generated/usStates.js';
import { s } from '../core/dom.js';
import { chance, margin, signed } from '../core/format.js';
import { inkOn, leanColour, legendStops, marginColour, palette } from './palette.js';
const sign = (v) => (v.player.party === 'D' ? 1 : -1);
const you = (v, dSide) => ((v.player.party === 'D') === dSide ? 'You' : 'Them');
function activity(id, label, unit) {
    const me = (st) => st[(id + 'Me')], them = (st) => st[(id + 'Them')];
    const max = (v) => Math.max(0.01, ...v.states.map(st => Math.abs(me(st) - them(st))));
    return {
        id, label, question: `Where each side's ${unit} is going, and who leads it`,
        fill: (st, v) => (me(st) + them(st) < 0.05 ? palette().toss : leanColour((sign(v) * (me(st) - them(st))) / max(v))),
        value: st => (me(st) + them(st) < 0.05 ? '' : signed(me(st) - them(st), 1)),
        spoken: st => `${unit}: you ${me(st).toFixed(1)}, them ${them(st).toFixed(1)}`,
        legend: v => ({ stops: legendStops(), left: `${you(v, false)} lead`, mid: 'Level', right: `${you(v, true)} lead` }),
    };
}
export const LAYERS = [
    {
        id: 'margin', label: 'Polls', question: "This week's state polls",
        fill: st => marginColour(st.margin), value: st => margin(st.margin),
        spoken: st => `polls ${margin(st.margin)}`,
        legend: () => ({ stops: legendStops(), left: 'R+14', mid: 'Even', right: 'D+14' }),
    },
    {
        id: 'change', label: 'Change', question: 'How each state moved since last week',
        fill: (st, v) => (st.change === null ? palette().toss : marginColour(sign(v) * st.change * 4)),
        value: st => (st.change === null ? '' : signed(st.change, 1)),
        spoken: st => (st.change === null ? 'no earlier poll' : `your lead ${signed(st.change, 1)} since last week`),
        legend: v => ({ stops: legendStops(), left: `Toward ${v.player.party === 'D' ? 'them' : 'you'}`, mid: 'No change', right: `Toward ${v.player.party === 'D' ? 'you' : 'them'}` }),
    },
    {
        id: 'win', label: 'Win chance', question: 'Your chance of carrying each state, from the polls and their usual error',
        fill: (st, v) => marginColour(((v.player.party === 'D' ? st.win : 1 - st.win) - 0.5) * 28),
        value: st => chance(st.win),
        spoken: st => `${chance(st.win)} chance for you`,
        legend: v => ({ stops: legendStops(), left: v.player.party === 'D' ? 'Them, certain' : 'You, certain', mid: '50/50', right: v.player.party === 'D' ? 'You, certain' : 'Them, certain' }),
    },
    activity('ads', 'Ads', 'ad pressure'),
    activity('field', 'Field', 'field organisation'),
    activity('visits', 'Visits', 'candidate visits'),
];
export function layer(id) { return LAYERS.find(l => l.id === id) ?? LAYERS[0]; }
const CALLOUT = new Set(CALLOUTS.map(c => c.code));
/** The drawing is wider than the map itself: the callout column sits to the right of New England. */
const MAP_W = 1090;
/** Draws the map; returns the SVG (it keeps its labels at a readable size as it resizes). */
export function usMap(o) {
    const { view, layer: L } = o;
    const byCode = new Map(view.states.map(st => [st.code, st]));
    const svg = s('svg', { class: 'usmap', viewBox: `0 0 ${MAP_W} ${VIEWBOX.height}`, role: 'group',
        'aria-label': `Map of the states: ${L.question}. Arrow keys move between states; Enter picks one.` });
    const shapes = s('g', { class: 'shapes' }), labels = s('g', { class: 'labels', 'aria-hidden': 'true' }), callouts = s('g', { class: 'callouts' });
    const ring = s('path', { class: 'focus-ring', d: '' }), pick = s('path', { class: 'pick-ring', d: '' });
    const paths = new Map();
    let roving = o.selected ?? 'PA';
    for (const shape of STATES) {
        const st = byCode.get(shape.code);
        if (!st)
            continue;
        const fill = L.fill(st, view);
        const p = s('path', {
            class: 'state', d: shape.d, fill, tabindex: shape.code === roving ? 0 : -1, role: 'button', 'data-code': shape.code,
            'aria-label': `${st.name}, ${st.ev} electoral votes: ${L.spoken(st, view)}`, 'aria-pressed': shape.code === o.selected ? 'true' : 'false',
        }, s('title', null, `${st.name} (${st.ev}): ${L.spoken(st, view)}`));
        p.addEventListener('click', () => o.onSelect(shape.code));
        p.addEventListener('mouseenter', () => o.onHover(shape.code));
        p.addEventListener('mouseleave', () => o.onHover(null));
        p.addEventListener('focus', () => { ring.setAttribute('d', shape.d); o.onHover(shape.code); });
        p.addEventListener('blur', () => ring.setAttribute('d', ''));
        p.addEventListener('keydown', e => {
            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                o.onSelect(shape.code);
                return;
            }
            const dir = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };
            const d = dir[e.key];
            if (!d)
                return;
            e.preventDefault();
            const next = neighbour(shape.code, d);
            if (next) {
                paths.get(roving)?.setAttribute('tabindex', '-1');
                roving = next;
                const n = paths.get(next);
                n.setAttribute('tabindex', '0');
                n.focus();
            }
        });
        paths.set(shape.code, p);
        shapes.append(p);
        if (shape.code === o.selected)
            pick.setAttribute('d', shape.d);
        if (CALLOUT.has(shape.code))
            continue;
        const ink = inkOn(fill), value = L.value(st, view);
        const g = s('g', { class: 'label', 'data-area': shape.area, transform: `translate(${shape.label[0]} ${shape.label[1]})` }, s('text', { class: 'code', fill: ink, 'text-anchor': 'middle', y: value ? -1 : 4 }, shape.code));
        if (value)
            g.append(s('text', { class: 'value', fill: ink, 'text-anchor': 'middle', y: 12 }, value));
        labels.append(g);
    }
    // callouts: boxes in a column at the right, with leader lines, in the order of their states from north to south;
    // their size follows the label size (see fit), so a figure never spills out of its box
    const sorted = [...CALLOUTS].sort((a, b) => a.anchor[1] - b.anchor[1]);
    const boxes = [];
    for (const c of sorted) {
        const st = byCode.get(c.code);
        if (!st)
            continue;
        const fill = L.fill(st, view), ink = inkOn(fill), value = L.value(st, view);
        const line = s('line', { x1: c.anchor[0], y1: c.anchor[1], class: 'leader' });
        const rect = s('rect', { rx: 2, fill, class: 'callout-box' });
        const code = s('text', { fill: ink, class: 'code' }, c.code), val = s('text', { fill: ink, class: 'value', 'text-anchor': 'end' }, value);
        const box = s('g', { class: 'callout', 'data-code': c.code, 'aria-hidden': 'true' }, line, rect, code, val);
        box.addEventListener('click', () => o.onSelect(c.code));
        box.addEventListener('mouseenter', () => o.onHover(c.code));
        box.addEventListener('mouseleave', () => o.onHover(null));
        callouts.append(box);
        boxes.push({ c, line, rect, code, value: val });
    }
    const layoutCallouts = (k) => {
        const w = Math.min(MAP_W - 935, Math.max(60, 76 * k)), bh = Math.max(20, 19 * k), gap = Math.max(28, bh + 6);
        const top = Math.max(110, 300 - (gap * boxes.length) / 2);
        boxes.forEach((b, i) => {
            const y = top + i * gap, x = 930;
            b.line.setAttribute('x2', String(x));
            b.line.setAttribute('y2', String(y));
            b.rect.setAttribute('x', String(x));
            b.rect.setAttribute('y', String(y - bh / 2));
            b.rect.setAttribute('width', String(w));
            b.rect.setAttribute('height', String(bh));
            b.code.setAttribute('x', String(x + 4 * k));
            b.code.setAttribute('y', String(y + 4.3 * k));
            b.value.setAttribute('x', String(x + w - 4 * k));
            b.value.setAttribute('y', String(y + 4.3 * k));
        });
    };
    svg.append(s('path', { class: 'nation', d: NATION, 'aria-hidden': 'true' }), shapes, pick, ring, labels, callouts);
    // Labels stay readable: their size in map units follows the map's scale so they draw at 12-13 px on screen,
    // and a state too small for its figure keeps only its code.
    const fit = () => {
        const w = svg.getBoundingClientRect().width || MAP_W;
        const k = MAP_W / w; // map units per screen pixel
        const code = 12.5 * k, val = 12 * k;
        svg.style.setProperty('--code-size', `${code}px`);
        svg.style.setProperty('--value-size', `${val}px`);
        for (const g of labels.querySelectorAll('g.label')) {
            const area = Number(g.dataset['area']);
            const room = Math.sqrt(area) / k; // the state's rough size on screen, in pixels
            g.classList.toggle('code-only', room < 46);
            g.classList.toggle('no-label', room < 22);
            const v = g.querySelector('.value'), c = g.querySelector('.code');
            if (v && c) {
                c.setAttribute('y', String(room < 46 ? 4 * k : -1 * k));
                v.setAttribute('y', String(13 * k));
            }
            else if (c)
                c.setAttribute('y', String(4 * k));
        }
        layoutCallouts(k);
    };
    new ResizeObserver(fit).observe(svg);
    return svg;
}
/** The nearest state in a direction, weighting off-axis distance, from the label points. */
function neighbour(from, [dx, dy]) {
    const a = STATES.find(x => x.code === from);
    if (!a)
        return null;
    let best = null, bestScore = Infinity;
    for (const b of STATES) {
        if (b.code === from)
            continue;
        const ex = b.label[0] - a.label[0], ey = b.label[1] - a.label[1];
        const along = ex * dx + ey * dy, across = Math.abs(ex * dy - ey * dx);
        if (along <= 4)
            continue;
        const score = along + 2.2 * across;
        if (score < bestScore) {
            bestScore = score;
            best = b.code;
        }
    }
    return best;
}
