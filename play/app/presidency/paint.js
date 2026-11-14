// The painter's toolbox for the presidency's rooms: Resolute Desk's Java2D helpers (ui/Art.java) as SVG. Gradients go
// into one <defs>; textures that Resolute Desk draws as thousands of tiny shapes (speckle, wood grain, stripes) become
// one path per colour, so a room stays a few hundred elements. Seeded textures use Java's own random generator, so they
// fall where Resolute Desk's do.
import { s } from '../core/dom.js';
/** java.util.Random, bit for bit (48-bit linear congruential), for textures placed as in Resolute Desk. */
export class JavaRandom {
    seed;
    static MULT = 0x5deece66dn;
    static MASK = (1n << 48n) - 1n;
    constructor(seed) { this.seed = (BigInt(seed) ^ JavaRandom.MULT) & JavaRandom.MASK; }
    next(bits) {
        this.seed = (this.seed * JavaRandom.MULT + 0xbn) & JavaRandom.MASK;
        const v = Number(this.seed >> BigInt(48 - bits));
        return bits === 32 ? v | 0 : v;
    }
    nextDouble() { return (this.next(26) * 134217728 + this.next(27)) / 9007199254740992; }
    nextBoolean() { return this.next(1) !== 0; }
}
const rgbOf = (hex) => { const n = parseInt(hex.slice(1), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; };
const hexOf = ([r, g, b]) => '#' + [r, g, b].map(v => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, '0')).join('');
/** Resolute Desk's shade(c, k): brighter above 1, darker below. */
export function shade(hex, k) { const [r, g, b] = rgbOf(hex); return hexOf([r * k, g * k, b * k]); }
export function mixHex(a, b, t) {
    const x = rgbOf(a), y = rgbOf(b);
    return hexOf([x[0] + (y[0] - x[0]) * t, x[1] + (y[1] - x[1]) * t, x[2] + (y[2] - x[2]) * t]);
}
/**
 * The presidency's muted, realistic palette (Q27): Resolute Desk's colours with a fifth of their saturation taken out
 * and a touch of grey, so the room reads as a room rather than a poster.
 */
export function mute(hex, amount = 0.2) {
    const [r, g, b] = rgbOf(hex), grey = 0.299 * r + 0.587 * g + 0.114 * b;
    return hexOf([r + (grey - r) * amount, g + (grey - g) * amount, b + (grey - b) * amount]);
}
export class Painter {
    svg;
    defs;
    n = 0;
    prefix;
    constructor(svg, prefix) {
        this.svg = svg;
        this.prefix = prefix;
        this.defs = s('defs');
        svg.append(this.defs);
    }
    id(kind) { return `${this.prefix}-${kind}-${this.n++}`; }
    /** A linear gradient in the room's coordinates (Resolute Desk's lin()). Returns a fill value. */
    lin(x1, y1, x2, y2, stops) {
        const id = this.id('lg');
        this.defs.append(s('linearGradient', { id, gradientUnits: 'userSpaceOnUse', x1, y1, x2, y2 }, ...stops.map(([o, c, a]) => s('stop', { offset: o, 'stop-color': c, 'stop-opacity': a ?? 1 }))));
        return `url(#${id})`;
    }
    /** A radial gradient in the room's coordinates, scaled vertically by {@code sy} about its centre. */
    radial(cx, cy, r, stops, sy = 1, fx, fy) {
        const id = this.id('rg');
        this.defs.append(s('radialGradient', { id, gradientUnits: 'userSpaceOnUse', cx, cy, r, fx: fx ?? cx, fy: fy ?? cy,
            gradientTransform: sy === 1 ? null : `translate(0 ${cy}) scale(1 ${sy}) translate(0 ${-cy})` }, ...stops.map(([o, c, a]) => s('stop', { offset: o, 'stop-color': c, 'stop-opacity': a ?? 1 }))));
        return `url(#${id})`;
    }
    // ---- shapes, appended to a group ------------------------------------------------------------------------------
    rect(g, x, y, w, h, fill, opacity = 1, rx = 0) {
        const r = s('rect', { x, y, width: w, height: h, fill, 'fill-opacity': opacity === 1 ? null : opacity, rx: rx || null });
        g.append(r);
        return r;
    }
    path(g, d, fill, opacity = 1) {
        const p = s('path', { d, fill, 'fill-opacity': opacity === 1 ? null : opacity });
        g.append(p);
        return p;
    }
    ellipse(g, cx, cy, rx, ry, fill, opacity = 1) {
        const e = s('ellipse', { cx, cy, rx, ry, fill, 'fill-opacity': opacity === 1 ? null : opacity });
        g.append(e);
        return e;
    }
    line(g, x1, y1, x2, y2, stroke, width, opacity = 1) {
        g.append(s('line', { x1, y1, x2, y2, stroke, 'stroke-width': width, 'stroke-opacity': opacity === 1 ? null : opacity }));
    }
    /** A top-to-bottom fill (Resolute Desk's vfill). */
    vfill(g, x, y, w, h, top, bottom) {
        this.rect(g, x, y, w, h, this.lin(0, y, 0, y + h, [[0, top], [1, bottom]]));
    }
    /** A soft pool of light (Resolute Desk's glow): a radial falloff at 0, 0.45 and 1 of the radius. */
    glow(g, cx, cy, rx, ry, colour, strength) {
        if (rx <= 0 || ry <= 0 || strength <= 0)
            return;
        const fill = this.radial(cx, cy, rx, [[0, colour, strength], [0.45, colour, strength * 0.45], [1, colour, 0]], ry / rx);
        this.ellipse(g, cx, cy, rx, ry, fill);
    }
    floorShadow(g, cx, cy, w, h, strength) { this.glow(g, cx, cy, w / 2, h / 2, '#000000', strength); }
    /** Darkens the edges as a lens does (Resolute Desk's vignette). */
    vignette(g, x, y, w, h, strength) {
        const cx = x + w / 2, cy = y + h / 2, r = w * 0.62;
        const fill = this.radial(cx, cy, r, [[0, '#000000', 0], [0.62, '#000000', 0], [1, '#000000', strength]], (h / w) * 1.15);
        this.rect(g, x, y, w, h, fill);
    }
    /** A raised edge: light top-left, dark bottom-right. */
    bevel(g, x, y, w, h, hi, lo) {
        g.append(s('path', { d: `M${x} ${y + h}V${y}H${x + w}`, fill: 'none', stroke: '#ffffff', 'stroke-opacity': hi, 'stroke-width': 1.2 }), s('path', { d: `M${x + w} ${y}V${y + h}H${x}`, fill: 'none', stroke: '#000000', 'stroke-opacity': lo, 'stroke-width': 1.2 }));
    }
    /** A sunken edge: dark top-left, light bottom-right. */
    inset(g, x, y, w, h, hi, lo) {
        g.append(s('path', { d: `M${x} ${y + h}V${y}H${x + w}`, fill: 'none', stroke: '#000000', 'stroke-opacity': lo, 'stroke-width': 1.2 }), s('path', { d: `M${x + w} ${y}V${y + h}H${x}`, fill: 'none', stroke: '#ffffff', 'stroke-opacity': hi, 'stroke-width': 1.2 }));
    }
    /** A diagonal streak of polish or glass (Resolute Desk's gloss). */
    gloss(g, x, y, w, h, strength) {
        this.rect(g, x, y, w, h, this.lin(x, y, x + w, y + h, [[0, '#ffffff', 0], [0.45, '#ffffff', strength], [0.55, '#ffffff', strength * 0.6], [1, '#ffffff', 0]]));
    }
    /** Tiny flecks of plaster or canvas, seeded as in Resolute Desk; one path per colour. */
    speckle(g, x, y, w, h, seed, count, light, dark) {
        const r = new JavaRandom(seed);
        let a = '', b = '';
        for (let i = 0; i < count; i++) {
            const px = x + r.nextDouble() * w, py = y + r.nextDouble() * h, sz = 0.6 + r.nextDouble() * 1.4;
            const sq = `M${px.toFixed(1)} ${py.toFixed(1)}h${sz.toFixed(2)}v${sz.toFixed(2)}h${(-sz).toFixed(2)}z`;
            if (r.nextBoolean())
                a += sq;
            else
                b += sq;
        }
        this.path(g, a, light[0], light[1]);
        this.path(g, b, dark[0], dark[1]);
    }
    /** Wood grain: long wavering lines along a board (Resolute Desk's grain), one path per colour. */
    grain(g, x, y, w, h, seed, lines, dark, light) {
        const r = new JavaRandom(seed), across = w >= h, len = across ? w : h, depth = across ? h : w;
        let a = '', b = '';
        for (let i = 0; i < lines; i++) {
            const off = r.nextDouble() * depth, amp = 0.6 + r.nextDouble() * 2.2, freq = 0.006 + r.nextDouble() * 0.02, ph = r.nextDouble() * 6.3;
            let d = '';
            for (let k = 0; k <= 24; k++) {
                const t = (len * k) / 24;
                let v = off + Math.sin(t * freq + ph) * amp + Math.sin(t * freq * 3.1 + ph * 2) * amp * 0.3;
                v = Math.max(0, Math.min(depth, v));
                const px = across ? x + t : x + v, py = across ? y + v : y + t;
                d += (k === 0 ? 'M' : 'L') + px.toFixed(1) + ' ' + py.toFixed(1);
            }
            if (r.nextDouble() < 0.7)
                a += d;
            else
                b += d;
        }
        g.append(s('path', { d: a, fill: 'none', stroke: dark[0], 'stroke-opacity': dark[1], 'stroke-width': 0.8 }), s('path', { d: b, fill: 'none', stroke: light[0], 'stroke-opacity': light[1], 'stroke-width': 0.8 }));
    }
    /** Vertical stripes (the wall's faint paper). */
    stripes(g, x, y, w, h, gap, colour) {
        let d = '';
        for (let sx = x; sx < x + w; sx += gap)
            d += `M${sx.toFixed(1)} ${y}h${(gap * 0.35).toFixed(2)}v${h}h${(-gap * 0.35).toFixed(2)}z`;
        this.path(g, d, colour[0], colour[1]);
    }
    /** A soft shadow under a rectangle, as Resolute Desk's seven widening strokes give it. */
    shadow(g, x, y, w, h, spread, strength) {
        const id = this.id('blur');
        this.defs.append(s('filter', { id, x: '-20%', y: '-20%', width: '140%', height: '140%' }, s('feGaussianBlur', { stdDeviation: spread * 0.6 })));
        g.append(s('rect', { x, y, width: w, height: h, fill: '#000000', 'fill-opacity': strength, filter: `url(#${id})` }));
    }
}
