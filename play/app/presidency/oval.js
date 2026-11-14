// The Oval Office, painted in SVG: Resolute Desk's room (ui/OvalArt.java), shape for shape in its 1600 x 800 frame
// (y from 56 to 856), restyled in a muted, realistic palette (Q27) and lit by the hour and the season (Q30). The curved
// cream wall with pilasters and mouldings, three south windows with drapes, four doors, two paintings under picture
// lights, the plank floor, the oval rug with the seal, the sofas, the side table with the briefing book, and the
// Resolute Desk with the objects on it (Q28: documents to sign, folders, the telephone). The flags, the globe and the
// dust in the sunbeams move; everything else is painted once.
import { s } from '../core/dom.js';
import { portrait } from '../art/portrait.js';
import { JavaRandom, Painter, mixHex, mute, shade } from './paint.js';
import { seal, SEAL } from './seal.js';
// Resolute Desk's palette (OvalArt), muted: a little grey in the creams, more in the golds and the rug
const M = (hex, k = 0.18) => mute(hex, k);
const WALL = M('#f1e6cc'), WALL_LO = M('#d6c39b'), TRIM = M('#f7f0dd', 0.1), TRIM_LO = M('#c9b387'), GOLD = M('#d4a945', 0.32), GOLD_LO = M('#8f6a1f', 0.3);
const WOOD = M('#5b3a22', 0.1), WOOD_LO = M('#2f1c10', 0.1), WOOD_HI = M('#8a5a35', 0.12);
const BRIGHT_GOLD = M('#f1d17a', 0.35), DEEP_GOLD = M('#7a5718', 0.3), FRINGE = M('#f6d77f', 0.35), BORDER_GOLD = M('#f2c14e', 0.35);
const ROOM_SEAL = { ...SEAL, gold: M('#d4a945', 0.3) };
export function paintOval(party, presidentId, light, look) {
    const svg = s('svg', { class: 'oval-art', viewBox: '0 56 1600 800', preserveAspectRatio: 'xMidYMid meet', 'aria-hidden': 'true', focusable: 'false' });
    const P = new Painter(svg, 'ov');
    const room = s('g'), live = s('g'), lamps = s('g'), top = s('g');
    svg.append(room);
    const ticks = [];
    wall(P, room);
    windows(P, room, light, lamps);
    doors(P, room);
    landscape(P, room, lamps, light, 330, 150, 120, 150);
    oilPortrait(P, room, lamps, light, 1290, 160, 110, 140);
    floor(P, room, light);
    sunlight(P, room, light);
    rug(P, room, party === 'R', light);
    sofa(P, room, 330, 610, 250, true);
    sofa(P, room, 1020, 610, 250, false);
    coffeeTable(P, room);
    globeStand(P, room);
    sideTable(P, room, lamps, light);
    deskBody(P, room);
    // the president, seated behind the desk
    const bust = portrait(presidentId, party, 60, false, look);
    bust.setAttribute('x', '770');
    bust.setAttribute('y', '362');
    bust.setAttribute('width', '60');
    bust.setAttribute('height', '70');
    room.append(bust);
    deskFront(P, room);
    // the room's light: warm from above
    P.glow(room, 800, 300, 900, 420, '#fff1cf', 0.18 * (0.45 + 0.55 * light.day));
    svg.append(live);
    ticks.push(flag(P, live, 520, 180, true), flag(P, live, 1080, 180, false), globe(P, live));
    // the hour's light over the room, then the lamps, which shine through it
    if (light.tint !== '#ffffff')
        svg.append(s('rect', { x: 0, y: 56, width: 1600, height: 800, fill: light.tint, style: 'mix-blend-mode:multiply' }));
    lamps.setAttribute('style', 'mix-blend-mode:screen');
    if (light.lamps > 0.5) {
        // the ceiling's cove lights wash the top of the wall
        const k = (light.lamps - 0.5) * 2;
        P.glow(lamps, 800, 70, 1000, 180, '#ffdcaa', 0.22 * k);
        P.glow(lamps, 800, 470, 260, 90, '#ffe7bf', 0.12 * k);
    }
    svg.append(lamps, top);
    if (light.sun > 0.12)
        ticks.push(dust(top, light.sun));
    P.vignette(top, 0, 56, 1600, 800, 0.45 + 0.15 * (1 - light.day));
    return { svg, tick: t => { for (const f of ticks)
            f(t); } };
}
// ---- the walls ------------------------------------------------------------------------------------------------------
function wall(P, g) {
    P.vfill(g, 0, 56, 1600, 480, WALL, WALL_LO);
    P.rect(g, 0, 56, 1600, 480, P.lin(0, 0, 1600, 0, [[0, '#000', 0.22], [0.3, '#000', 0], [0.7, '#000', 0], [1, '#000', 0.22]]));
    P.stripes(g, 0, 80, 1600, 360, 26, ['#ffffff', 0.05]);
    P.speckle(g, 0, 80, 1600, 440, 11, 2600, ['#ffffff', 0.05], ['#000000', 0.035]);
    // crown moulding with dentils
    P.vfill(g, 0, 56, 1600, 26, TRIM, TRIM_LO);
    P.rect(g, 0, 82, 1600, 3, '#000', 0.18);
    let dent = '', gap = '';
    for (let x = 0; x < 1600; x += 14) {
        dent += `M${x} 70h8v8h-8z`;
        gap += `M${x + 8} 70h2v8h-2z`;
    }
    P.path(g, dent, TRIM, 0.9);
    P.path(g, gap, '#000', 0.12);
    // the dado rail and the panels below it
    P.vfill(g, 0, 436, 1600, 10, TRIM, TRIM_LO);
    P.rect(g, 0, 446, 1600, 2, '#000', 0.16);
    for (const [px, pw] of [[6, 160], [300, 150], [470, 80], [1100, 70], [1272, 160], [1560, 40]]) {
        P.inset(g, px + 8, 458, pw - 16, 52, 0.4, 0.18);
        P.inset(g, px + 14, 464, pw - 28, 40, 0.25, 0.1);
    }
    // the baseboard
    P.vfill(g, 0, 516, 1600, 18, M('#e9dcbc'), M('#b59f75'));
    P.rect(g, 0, 532, 1600, 2, '#000', 0.3);
    // fluted pilasters between the doors and the windows
    for (const px of [172, 478, 1132, 1576, 4])
        pilaster(P, g, px, 86, 22, 430);
}
function pilaster(P, g, x, y, w, h) {
    P.vfill(g, x, y, w, h, TRIM, M('#e2d3ae'));
    let dark = '', lit = '';
    for (let k = 1; k < 4; k++) {
        dark += `M${x + (w * k) / 4 - 1} ${y + 18}h2v${h - 36}h-2z`;
        lit += `M${x + (w * k) / 4 + 1} ${y + 18}h1v${h - 36}h-1z`;
    }
    P.path(g, dark, '#000', 0.1);
    P.path(g, lit, '#fff', 0.35);
    P.vfill(g, x - 4, y, w + 8, 14, TRIM, TRIM_LO);
    P.vfill(g, x - 4, y + h - 12, w + 8, 12, TRIM, TRIM_LO);
    P.bevel(g, x, y, w, h, 0.5, 0.2);
    P.rect(g, x + w, y, 4, h, '#000', 0.08);
}
// ---- the windows, with the South Lawn through them --------------------------------------------------------------------
function windows(P, g, L, lamps) {
    P.rect(g, 548, 96, 504, 316, '#000', 0.12, 5);
    P.vfill(g, 556, 102, 488, 300, M('#e8dab9'), M('#cbb68b'));
    const skyFill = (wx, wy, wh) => P.lin(wx, wy, wx, wy + wh * 0.72, [[0, L.sky[0]], [0.6, L.sky[1]], [1, L.sky[2]]]);
    for (let i = 0; i < 3; i++) {
        const wx = 600 + i * 140, wy = 130, ww = 110, wh = 240, horizon = wy + wh * 0.72;
        const clip = P.id('win');
        P.defs.append(s('clipPath', { id: clip }, s('rect', { x: wx, y: wy, width: ww, height: wh })));
        const pane = s('g', { 'clip-path': `url(#${clip})` });
        g.append(pane);
        P.rect(pane, wx, wy, ww, wh * 0.72, skyFill(wx, wy, wh));
        if (L.stars) {
            // stars, in the lamps' layer so that the room's darkness does not dim them
            const r = new JavaRandom(90 + i);
            let d = '';
            for (let k = 0; k < 9; k++) {
                const x = wx + 8 + r.nextDouble() * (ww - 16), y = wy + 4 + r.nextDouble() * 90, z = 0.6 + r.nextDouble() * 0.7;
                d += `M${x.toFixed(1)} ${y.toFixed(1)}h${z.toFixed(2)}v${z.toFixed(2)}h${(-z).toFixed(2)}z`;
            }
            P.path(lamps, d, '#f2f0e6', 0.7);
        }
        // the clouds and the tree line, placed as Resolute Desk places them (its random stream, seed 40 + i)
        const r = new JavaRandom(40 + i);
        for (let k = 0; k < 4; k++)
            P.glow(pane, wx + r.nextDouble() * ww, wy + 30 + r.nextDouble() * 70, 26 + r.nextDouble() * 20, 9, L.clouds.colour, L.clouds.alpha);
        if (L.clouds.count > 4) {
            const r2 = new JavaRandom(140 + i);
            for (let k = 4; k < L.clouds.count; k++)
                P.glow(pane, wx + r2.nextDouble() * ww, wy + 12 + r2.nextDouble() * 90, 30 + r2.nextDouble() * 24, 11, L.clouds.colour, L.clouds.alpha * 0.9);
        }
        const pick = new JavaRandom(60 + i);
        const tops = [];
        for (let k = 0; k < 9; k++)
            tops.push(r.nextDouble());
        if (L.bare) {
            // winter: bare crowns (a haze of twigs) on dark trunks
            let trunks = '';
            for (let k = 0; k < 9; k += 2) {
                const tx = wx - 8 + k * 14 + 11;
                trunks += `M${tx} ${horizon}L${tx + 0.5} ${horizon - 16}M${tx} ${horizon - 9}L${tx - 5} ${horizon - 17}M${tx + 0.4} ${horizon - 12}L${tx + 6} ${horizon - 19}`;
            }
            for (let k = 0; k < 9; k++)
                P.ellipse(pane, wx - 8 + k * 14 + 11, wy + wh * 0.66 - tops[k] * 10 + 11, 11, 11, L.canopy[0], 0.45);
            pane.append(s('path', { d: trunks, stroke: mixHex('#3c342d', L.sky[0], 0.15), 'stroke-width': 1.2, fill: 'none', opacity: 0.7 }));
        }
        else {
            for (let k = 0; k < 9; k++) {
                const c = L.canopy[Math.floor(pick.nextDouble() * L.canopy.length)];
                P.ellipse(pane, wx - 8 + k * 14 + 11, wy + wh * 0.66 - tops[k] * 10 + 11, 11, 11, c);
            }
            // a darker underside to the canopy
            P.rect(pane, wx, horizon - 8, ww, 8, P.lin(0, horizon - 8, 0, horizon, [[0, '#000', 0], [1, '#000', 0.18]]));
        }
        P.rect(pane, wx, horizon, ww, wh * 0.28, P.lin(wx, horizon, wx, wy + wh, [[0, L.lawn[0]], [1, L.lawn[1]]]));
        if (L.snow)
            P.rect(pane, wx, horizon, ww, 3, '#ffffff', 0.35 * (0.3 + 0.7 * L.day));
        if (i === 1)
            monument(P, pane, lamps, wx + ww / 2, wy + 30, horizon, L);
        // the glass: a sheen by day, the room's own reflection by night
        P.gloss(pane, wx, wy, ww, wh, 0.12 * (0.35 + 0.65 * L.day));
        if (L.day < 0.5)
            P.rect(pane, wx, wy, ww, wh, '#d8b47a', 0.07 * (1 - L.day * 2));
        // sash and mullions
        const sash = M('#fbf7ec', 0.1);
        g.append(s('rect', { x: wx, y: wy, width: ww, height: wh, fill: 'none', stroke: sash, 'stroke-width': 6 }));
        let mull = '', edge = '';
        for (let k = 1; k < 3; k++) {
            mull += `M${wx + (ww * k) / 3} ${wy}V${wy + wh}`;
            edge += `M${wx + (ww * k) / 3 + 1.5} ${wy}V${wy + wh}`;
        }
        for (let k = 1; k < 5; k++)
            mull += `M${wx} ${wy + (wh * k) / 5}H${wx + ww}`;
        g.append(s('path', { d: mull, stroke: sash, 'stroke-width': 2.6, fill: 'none' }), s('path', { d: edge + `M${wx + 3} ${wy + 3}h${ww - 6}v${wh - 6}h${-(ww - 6)}z`, stroke: '#000', 'stroke-opacity': 0.12, 'stroke-width': 1, fill: 'none' }));
        P.vfill(g, wx - 10, wy + wh + 2, ww + 20, 10, TRIM, TRIM_LO);
        drapes(P, g, wx, wy, ww, wh);
    }
    valance(P, g, 560, 104, 480);
}
function monument(P, g, lamps, cx, top, base, L) {
    const lit = L.monumentLit ? '#fbf7ee' : M('#fbf8f0', 0.1), dark = L.monumentLit ? mixHex('#a9a49a', '#d9d3c4', L.day) : M('#d9d3c4', 0.1);
    P.path(g, `M${cx - 6} ${base}L${cx - 4} ${top + 10}L${cx} ${top}L${cx} ${base}Z`, mixHex(mixHex('#5d6170', lit, 0.35), lit, Math.max(L.day, L.monumentLit ? 1 : 0)));
    P.path(g, `M${cx} ${base}L${cx} ${top}L${cx + 4} ${top + 10}L${cx + 6} ${base}Z`, dark);
    if (L.monumentLit && L.day < 0.6) {
        // floodlit at night, with the red aircraft lights at the top (in the lamps' layer, above the room's darkness)
        const k = 1 - L.day / 0.6;
        P.path(lamps, `M${cx - 6} ${base}L${cx - 4} ${top + 10}L${cx} ${top}L${cx + 4} ${top + 10}L${cx + 6} ${base}Z`, '#fff1d6', 0.55 * k);
        P.path(lamps, `M${cx - 6} ${base}L${cx - 4} ${top + 10}L${cx} ${top}L${cx} ${base}Z`, '#fff6e6', 0.3 * k);
        P.glow(lamps, cx, (top + base) / 2, 12, (base - top) * 0.6, '#fff4dc', 0.28 * k);
        P.ellipse(lamps, cx - 1.6, top + 7, 0.9, 0.9, '#ff5a4a', 0.9 * k);
        P.ellipse(lamps, cx + 1.6, top + 7, 0.9, 0.9, '#ff5a4a', 0.9 * k);
    }
}
const DRAPE = ['#9c7022', '#f3cf6c', '#b5862c', '#f0c75e', '#a57826', '#d9ad48'].map((c, i) => [[0, 0.18, 0.36, 0.55, 0.75, 1][i], M(c, 0.4)]);
function drapes(P, g, wx, wy, ww, wh) {
    for (let side = 0; side < 2; side++) {
        const dx = side === 0 ? wx - 28 : wx + ww - 6;
        const d = `M${dx} ${wy - 18}L${dx + 34} ${wy - 18}C${dx + 30} ${wy + wh * 0.5} ${dx + 38} ${wy + wh * 0.8} ${dx + 36} ${wy + wh + 34}`
            + `L${dx - 4} ${wy + wh + 34}C${dx + 4} ${wy + wh * 0.7} ${dx - 2} ${wy + wh * 0.4} ${dx} ${wy - 18}Z`;
        P.path(g, d, P.lin(dx, 0, dx + 36, 0, DRAPE));
        P.path(g, d, P.lin(0, wy, 0, wy + wh + 34, [[0, '#fff', 0.12], [0.5, '#fff', 0], [1, '#000', 0.25]]));
        const ty = wy + wh * 0.62;
        P.rect(g, dx + 2, ty, 32, 7, GOLD_LO, 1, 3);
        P.rect(g, dx + 4, ty + 1, 28, 2, M('#fff0b3', 0.3), 0.6, 1);
    }
}
function valance(P, g, x, y, w) {
    P.vfill(g, x, y, w, 14, M('#e2b552', 0.4), GOLD_LO);
    const fill = P.lin(0, y + 12, 0, y + 46, [[0, M('#f2cb63', 0.4)], [0.6, M('#c4932f', 0.4)], [1, M('#8c6420', 0.35)]]);
    let swags = '', fringe = '';
    for (let i = 0; i < 6; i++) {
        const sx = x + (w * i) / 6, sw = w / 6;
        swags += `M${sx} ${y + 12}C${sx + sw * 0.3} ${y + 40} ${sx + sw * 0.7} ${y + 40} ${sx + sw} ${y + 12}L${sx + sw} ${y + 18}C${sx + sw * 0.7} ${y + 46} ${sx + sw * 0.3} ${y + 46} ${sx} ${y + 18}Z`;
        for (let k = 0; k < 6; k++) {
            const fx = sx + (sw * (k + 0.5)) / 6;
            fringe += `M${(fx - 0.6).toFixed(1)} ${(y + 40 + Math.sin((k / 5) * Math.PI) * 4).toFixed(1)}h1.2v6h-1.2z`;
        }
    }
    P.path(g, swags, fill);
    P.path(g, fringe, FRINGE);
    P.rect(g, x, y + 1, w, 2, '#fff', 0.4);
}
// ---- the doors ------------------------------------------------------------------------------------------------------------
function doors(P, g) {
    door(P, g, 60, 210, 90, 290);
    door(P, g, 215, 160, 70, 260);
    door(P, g, 1190, 170, 70, 240);
    door(P, g, 1450, 210, 90, 290);
}
function door(P, g, x, y, w, h) {
    P.rect(g, x - 8, y - 20, w + 24, h + 20, '#000', 0.12);
    P.vfill(g, x - 12, y - 22, w + 24, h + 22, TRIM, M('#ddcca5'));
    P.path(g, `M${x - 20} ${y - 22}L${x + w / 2} ${y - 54}L${x + w + 20} ${y - 22}Z`, P.lin(0, y - 54, 0, y - 22, [[0, TRIM], [1, TRIM_LO]]));
    P.line(g, x - 20, y - 22, x + w + 20, y - 22, '#000', 1.4, 0.18);
    P.bevel(g, x - 12, y - 22, w + 24, h + 22, 0.5, 0.2);
    P.rect(g, x, y, w, h, P.lin(x, y, x + w, y + h, [[0, M('#fbf5e5', 0.1)], [1, M('#e0d2b0')]]));
    const pw = w - 18;
    for (const [py, ph] of [[y + 10, h * 0.38], [y + h * 0.46, h * 0.48]]) {
        P.inset(g, x + 9, py, pw, ph, 0.6, 0.22);
        P.bevel(g, x + 15, py + 6, pw - 12, ph - 12, 0.5, 0.15);
    }
    P.rect(g, x + w - 2, y, 2, h, '#000', 0.25);
    const kx = x + w - 16, ky = y + h * 0.5;
    g.append(s('circle', { cx: kx + 4, cy: ky + 4, r: 5, fill: GOLD_LO }), s('circle', { cx: kx + 4, cy: ky + 4, r: 4, fill: GOLD }), s('circle', { cx: kx + 3, cy: ky + 3, r: 1.5, fill: '#fff', 'fill-opacity': 0.7 }));
}
// ---- the paintings ----------------------------------------------------------------------------------------------------------
function giltFrame(P, g, x, y, w, h) {
    P.shadow(g, x - 6, y - 4, w + 12, h + 16, 8, 0.25);
    for (let k = 0; k < 3; k++) {
        const m = 12 - k * 4;
        P.rect(g, x - m, y - m, w + m * 2, h + m * 2, P.lin(x - m, y - m, x + w + m, y + h + m, [[0, BRIGHT_GOLD], [0.5, k === 1 ? GOLD_LO : GOLD], [1, DEEP_GOLD]]));
    }
}
function pictureLight(P, g, lamps, L, x, y, w, h) {
    const cx = x + w / 2;
    P.rect(g, cx - 2, y - 22, 4, 12, GOLD_LO);
    P.rect(g, cx - w * 0.3, y - 14, w * 0.6, 9, P.lin(0, y - 14, 0, y - 6, [[0, BRIGHT_GOLD], [1, GOLD_LO]]), 1, 3);
    P.glow(lamps, cx, y + h * 0.15, w * 0.7, h * 0.35, '#fff3d6', 0.22 * (0.55 + 0.6 * L.lamps));
}
/** A Hudson River landscape: mountains, a lake and a stormy sky clearing. */
function landscape(P, g, lamps, L, x, y, w, h) {
    giltFrame(P, g, x, y, w, h);
    P.rect(g, x, y, w, h * 0.6, P.lin(x, y, x, y + h * 0.6, [[0, '#56606a'], [0.55, M('#c9b688')], [1, M('#e8d6a6')]]));
    P.glow(g, x + w * 0.62, y + h * 0.42, w * 0.4, h * 0.2, M('#fff0c0'), 0.6);
    P.path(g, `M${x} ${y + h * 0.58}L${x + w * 0.2} ${y + h * 0.42}L${x + w * 0.38} ${y + h * 0.5}L${x + w * 0.55} ${y + h * 0.36}L${x + w * 0.75} ${y + h * 0.52}`
        + `L${x + w} ${y + h * 0.46}L${x + w} ${y + h * 0.62}L${x} ${y + h * 0.62}Z`, '#7c8478');
    P.rect(g, x, y + h * 0.6, w, h * 0.18, P.lin(x, y + h * 0.6, x, y + h * 0.78, [[0, M('#d8c79c')], [1, '#8f8a70']]));
    P.path(g, `M${x} ${y + h * 0.7}C${x + w * 0.2} ${y + h * 0.62} ${x + w * 0.35} ${y + h * 0.8} ${x + w * 0.5} ${y + h * 0.78}`
        + `C${x + w * 0.7} ${y + h * 0.76} ${x + w * 0.85} ${y + h * 0.86} ${x + w} ${y + h * 0.82}L${x + w} ${y + h}L${x} ${y + h}Z`, '#3e3a24');
    P.rect(g, x + w * 0.14, y + h * 0.36, 3, h * 0.4, '#2c2a18');
    P.ellipse(g, x + w * 0.16, y + h * 0.36, w * 0.12, h * 0.08, '#2c2a18');
    P.speckle(g, x, y, w, h, 71, 520, ['#fff', 0.05], ['#000', 0.07]);
    P.rect(g, x, y, w, h, P.lin(x, y, x, y + h, [[0, '#c9a35a', 0.12], [1, '#3a2a10', 0.22]]));
    pictureLight(P, g, lamps, L, x, y, w, h);
}
/** An oil portrait of a statesman of long ago, lit from the left against a dark ground (Resolute Desk's second sitter). */
function oilPortrait(P, g, lamps, L, x, y, w, h) {
    giltFrame(P, g, x, y, w, h);
    const ground = '#3d2f26';
    P.rect(g, x, y, w, h, P.lin(x, y, x + w, y + h, [[0, shade(ground, 1.35)], [1, shade(ground, 0.65)]]));
    P.glow(g, x + w * 0.38, y + h * 0.35, w * 0.6, h * 0.5, '#d9b27a', 0.25);
    const cx = x + w * 0.5, cy = y + h * 0.4;
    P.path(g, `M${x + w * 0.1} ${y + h}C${x + w * 0.15} ${y + h * 0.66} ${x + w * 0.3} ${y + h * 0.6} ${cx} ${y + h * 0.62}`
        + `C${x + w * 0.7} ${y + h * 0.6} ${x + w * 0.85} ${y + h * 0.66} ${x + w * 0.9} ${y + h}Z`, P.lin(x, 0, x + w, 0, [[0, '#2a2a30'], [1, '#101014']]));
    P.path(g, `M${cx - w * 0.08} ${y + h * 0.62}L${cx} ${y + h * 0.78}L${cx + w * 0.08} ${y + h * 0.62}Z`, '#e9e2d0');
    P.ellipse(g, cx, cy + h * 0.02, w * 0.15, h * 0.18, P.lin(cx - w * 0.16, 0, cx + w * 0.16, 0, [[0, '#e3b892'], [1, '#8c6248']]));
    P.ellipse(g, cx - w * 0.06, cy + h * 0.0025, w * 0.03, h * 0.0125, '#4a2c1c', 0.55);
    P.ellipse(g, cx + w * 0.065, cy + h * 0.0025, w * 0.03, h * 0.0125, '#4a2c1c', 0.55);
    P.path(g, `M${cx + w * 0.005} ${cy}L${cx + w * 0.03} ${cy + h * 0.08}L${cx - w * 0.005} ${cy + h * 0.085}Z`, '#4a2c1c', 0.35);
    P.rect(g, cx - w * 0.045, cy + h * 0.115, w * 0.09, h * 0.012, '#5a2e22', 0.6, 1);
    // the hair: the top half of an ellipse (Resolute Desk's chord arc)
    const hx = w * 0.16, hy = h * 0.11, hcy = cy - h * 0.18 + hy;
    P.path(g, `M${cx - hx} ${hcy}A${hx} ${hy} 0 0 1 ${cx + hx} ${hcy}Z`, '#4a3526');
    P.speckle(g, x, y, w, h, 71, 520, ['#fff', 0.05], ['#000', 0.07]);
    P.rect(g, x, y, w, h, P.lin(x, y, x, y + h, [[0, '#c9a35a', 0.1], [1, '#3a2a10', 0.2]]));
    pictureLight(P, g, lamps, L, x, y, w, h);
}
// ---- the floor, the sunlight and the rug ---------------------------------------------------------------------------------
function floor(P, g, L) {
    P.vfill(g, 0, 534, 1600, 322, M('#6e4728', 0.12), M('#3e2716', 0.1));
    // planks running toward the windows, each its own shade (Resolute Desk's random stream, seed 5)
    const r = new JavaRandom(5), vx = 800, vy = 300, k = (534 - vy) / (856 - vy);
    let seams = '';
    for (let i = -56; i <= 56; i++) {
        const x0 = vx + i * 36 * k, x1 = vx + i * 36, x0b = vx + (i + 1) * 36 * k, x1b = vx + (i + 1) * 36;
        const fx1 = x1 + (x1 - vx) * 0.9, fx1b = x1b + (x1b - vx) * 0.9;
        const white = r.nextBoolean(), a = 0.02 + r.nextDouble() * 0.05;
        P.path(g, `M${x0.toFixed(1)} 534L${x0b.toFixed(1)} 534L${fx1b.toFixed(1)} 856L${fx1.toFixed(1)} 856Z`, white ? '#fff' : '#000', a);
        seams += `M${x0.toFixed(1)} 534L${fx1.toFixed(1)} 856`;
    }
    g.append(s('path', { d: seams, stroke: '#000', 'stroke-opacity': 0.3, 'stroke-width': 1, fill: 'none' }));
    P.grain(g, 0, 540, 1600, 316, 9, 260, ['#1f120a', 0.2], ['#b07a4c', 0.12]);
    // the polish reflects the windows
    for (let i = 0; i < 3; i++)
        P.glow(g, 655 + i * 140, 600, 48, 70, mixHex('#9fb3d0', '#fff2d8', L.day), 0.16 * (0.25 + 0.75 * L.day));
    P.rect(g, 0, 534, 1600, 4, '#000', 0.35);
}
/** Beams from the windows and the patches they lay on the floor: leaning with the sun's bearing, long when it is low. */
function sunlight(P, g, L) {
    if (L.sun < 0.02)
        return;
    const { colour, lean, reach } = L.beam;
    for (let i = 0; i < 3; i++) {
        const top = 655 + i * 140, bottom = top + (top - 800) * 0.35 + lean;
        const d = `M${top - 50} 536L${top + 50} 536L${bottom + 110} ${reach}L${bottom - 110} ${reach}Z`;
        P.path(g, d, P.lin(0, 536, 0, reach, [[0, colour, 0.2 * L.sun], [1, colour, 0]]));
        const t = 0.7, px = top + (bottom - top) * t;
        P.glow(g, px, 536 + (reach - 536) * t, 120, 40, mixHex(colour, '#ffefc4', 0.5), 0.18 * L.sun);
    }
}
function rug(P, g, rep, L) {
    const field = rep ? M('#b98a2e', 0.32) : M('#1f3a7a', 0.25), deep = shade(field, 0.62), light = shade(field, 1.25);
    P.floorShadow(g, 800, 718, 1210, 330, 0.45);
    P.ellipse(g, 800, 710, 580, 152, deep);
    P.ellipse(g, 800, 710, 560, 557 * 0.255, P.radial(800, 710 - 180 * 0.255, 640, [[0, light], [0.7, field], [1, shade(field, 0.82)]], 0.255));
    g.append(s('ellipse', { cx: 800, cy: 710, rx: 534, ry: 132, fill: 'none', stroke: BORDER_GOLD, 'stroke-opacity': 0.75, 'stroke-width': 3 }), s('ellipse', { cx: 800, cy: 710, rx: 516, ry: 126, fill: 'none', stroke: BORDER_GOLD, 'stroke-opacity': 0.45, 'stroke-width': 1.2 }));
    let stars = '';
    for (let i = 0; i < 50; i++) {
        const a = (i * Math.PI * 2) / 50;
        stars += star(800 + Math.cos(a) * 524, 710 + Math.sin(a) * 129, 4.2, 0.4);
    }
    P.path(g, stars, FRINGE, 0.8);
    // the sunburst
    let lit = '', dark = '';
    for (let i = 0; i < 32; i++) {
        const a0 = (i * Math.PI * 2) / 32, a1 = a0 + Math.PI / 32;
        const pt = (a, rx, ry) => `${(800 + Math.cos(a) * rx).toFixed(1)} ${(710 + Math.sin(a) * ry).toFixed(1)}`;
        const ray = `M${pt(a0, 150, 40)}L${pt(a0, 470, 116)}L${pt(a1, 470, 116)}L${pt(a1, 150, 40)}Z`;
        if (i % 2 === 0)
            lit += ray;
        else
            dark += ray;
    }
    P.path(g, lit, FRINGE, 0.16);
    P.path(g, dark, '#000', 0.06);
    const clip = P.id('rug');
    P.defs.append(s('clipPath', { id: clip }, s('ellipse', { cx: 800, cy: 710, rx: 580, ry: 152 })));
    const weave = s('g', { 'clip-path': `url(#${clip})` });
    g.append(weave);
    P.speckle(weave, 240, 570, 1120, 280, 21, 5200, ['#fff', 0.04], ['#000', 0.05]);
    // the seal at the centre, laid flat
    const flat = s('g', { transform: 'translate(800 710) scale(1 0.3)' });
    flat.append(seal(0, 0, 124, ROOM_SEAL));
    g.append(flat);
    // light on the rug from the windows
    for (let i = 0; i < 3; i++)
        P.glow(g, 690 + i * 120 + L.beam.lean * 0.55, 680, 120, 34, '#fff1c8', 0.12 * L.sun);
}
function star(cx, cy, r, flat) {
    let d = '';
    for (let k = 0; k < 10; k++) {
        const a = -Math.PI / 2 + (k * Math.PI) / 5, rr = k % 2 === 0 ? r : r * 0.45;
        d += (k === 0 ? 'M' : 'L') + (cx + Math.cos(a) * rr).toFixed(2) + ' ' + (cy + Math.sin(a) * rr * (1 - flat)).toFixed(2);
    }
    return d + 'Z';
}
// ---- the furniture ------------------------------------------------------------------------------------------------------------
function sofa(P, g, x, y, w, left) {
    const c = M('#ece0c4', 0.15), lo = M('#b5a27c', 0.15);
    P.floorShadow(g, x + w / 2, y + 96, w + 60, 34, 0.45);
    P.rect(g, x, y, w, 52, P.lin(0, y, 0, y + 52, [[0, shade(c, 1.06)], [1, shade(c, 0.86)]]), 1, 11);
    P.rect(g, x + 12, y + 4, w - 24, 4, '#fff', 0.35, 2);
    P.rect(g, x - 2, y + 40, w + 4, 52, P.lin(0, y + 40, 0, y + 92, [[0, shade(c, 0.88)], [1, lo]]), 1, 8);
    const cw = (w - 28) / 3, cushion = P.lin(0, y + 34, 0, y + 70, [[0, shade(c, 1.04)], [1, shade(c, 0.84)]]);
    for (let k = 0; k < 3; k++) {
        const cx = x + 14 + k * cw;
        g.append(s('rect', { x: cx + 1, y: y + 34, width: cw - 2, height: 36, rx: 7, fill: cushion, stroke: lo, 'stroke-opacity': 0.7, 'stroke-width': 1 }));
        P.rect(g, cx + 8, y + 37, cw - 16, 4, '#fff', 0.25, 2);
    }
    for (let sd = 0; sd < 2; sd++) {
        const ax = (sd === 0) === left ? x - 10 : x + w - 22;
        P.rect(g, ax, y + 16, 32, 72, P.lin(ax, 0, ax + 32, 0, [[0, shade(c, 0.82)], [0.5, shade(c, 1.04)], [1, shade(c, 0.8)]]), 1, 7);
    }
    for (const fx of [x + 8, x + w - 14])
        P.rect(g, fx, y + 90, 7, 10, WOOD_LO, 1, 1.5);
    P.speckle(g, x, y, w, 92, x, 700, ['#fff', 0.05], ['#000', 0.04]);
}
function coffeeTable(P, g) {
    P.floorShadow(g, 800, 826, 260, 30, 0.5);
    P.rect(g, 690, 780, 220, 40, P.lin(0, 776, 0, 820, [[0, WOOD], [1, WOOD_LO]]), 1, 4);
    P.rect(g, 684, 768, 232, 18, P.lin(690, 0, 910, 0, [[0, WOOD], [0.5, WOOD_HI], [1, WOOD]]), 1, 4);
    P.grain(g, 690, 770, 220, 14, 33, 30, [WOOD_LO, 0.4], ['#c08a5a', 0.25]);
    P.gloss(g, 684, 768, 232, 18, 0.2);
    // today's papers: The Capital Ledger folded on top, a second paper beside it
    P.rect(g, 732, 763, 70, 14, '#000', 0.25);
    P.rect(g, 730, 760, 70, 14, '#f2eee2');
    P.rect(g, 812, 762, 64, 12, '#ece6d6');
    P.rect(g, 736, 763, 56, 2, '#333');
    let lines = '';
    for (let k = 0; k < 3; k++)
        lines += `M736 ${767 + k * 2}h${30 + k * 6}v1h${-(30 + k * 6)}z`;
    P.path(g, lines, '#000', 0.35);
    P.path(g, 'M818 765h40v1.6h-40zM818 769h28v1h-28z', '#000', 0.3);
}
function globeStand(P, g) {
    P.floorShadow(g, 356, 568, 86, 16, 0.5);
    P.rect(g, 350, 512, 8, 52, P.lin(348, 0, 360, 0, [[0, WOOD_LO], [0.5, WOOD_HI], [1, WOOD_LO]]));
    P.rect(g, 326, 560, 58, 9, P.lin(0, 560, 0, 570, [[0, WOOD_HI], [1, WOOD_LO]]), 1, 2);
}
function sideTable(P, g, lamps, L) {
    P.floorShadow(g, 1350, 568, 150, 22, 0.5);
    P.rect(g, 1296, 500, 108, 64, P.lin(0, 500, 0, 564, [[0, WOOD], [1, WOOD_LO]]));
    P.inset(g, 1304, 508, 92, 48, 0.12, 0.3);
    P.rect(g, 1288, 490, 124, 13, P.lin(1290, 0, 1410, 0, [[0, WOOD], [0.5, WOOD_HI], [1, WOOD]]));
    P.gloss(g, 1288, 490, 124, 13, 0.25);
    // a lamp: lit more brightly as the daylight goes
    P.rect(g, 1392, 442, 4, 48, GOLD_LO);
    P.rect(g, 1382, 486, 24, 5, GOLD_LO, 1, 1.5);
    P.path(g, 'M1378 444L1410 444L1402 418L1386 418Z', P.lin(1378, 0, 1410, 0, [[0, mixHex('#e9dfc6', '#fff3d2', L.lamps)], [1, mixHex('#c9b68c', '#e8cf9a', L.lamps)]]));
    P.glow(lamps, 1394, 450, 60, 40, '#fff0c4', 0.3 * (0.6 + 0.7 * L.lamps));
    if (L.lamps > 0.5)
        P.glow(lamps, 1394, 420, 110, 90, '#ffe3a8', 0.2 * (L.lamps - 0.5) * 2);
    // the briefing book from the Council of Economic Advisers, gold-stamped
    P.rect(g, 1314, 474, 76, 18, '#000', 0.3);
    P.rect(g, 1312, 470, 76, 22, P.lin(0, 470, 0, 492, [[0, M('#24407e', 0.3)], [1, M('#142650', 0.3)]]));
    P.rect(g, 1312, 470, 4, 22, GOLD);
    P.path(g, 'M1334 478h30v2h-30zM1338 483h22v1.4h-22z', GOLD, 0.85);
}
/** The chair behind the Resolute Desk. */
function deskBody(P, g) {
    P.rect(g, 758, 394, 84, 86, P.lin(762, 0, 838, 0, [[0, '#1e130c'], [0.45, '#4a3020'], [1, '#1a100a']]), 1, 12);
    P.rect(g, 772, 400, 18, 70, '#fff', 0.12, 5);
    P.floorShadow(g, 800, 600, 360, 40, 0.6);
}
/**
 * The front of the desk (oak timbers from HMS Resolute, carved panels, the kneehole door with the seal) and what is
 * on it. The objects are the desk's actions (Q28), so they are drawn larger than in Resolute Desk: a tray of documents
 * to sign, the agenda's folders, the pen set and the telephone.
 */
function deskFront(P, g) {
    P.rect(g, 660, 460, 280, 132, P.lin(0, 460, 0, 592, [[0, '#6a4026'], [1, '#34200f']]));
    P.grain(g, 660, 462, 280, 128, 3, 70, ['#1c1008', 0.35], ['#a8764a', 0.18]);
    for (let k = 0; k < 2; k++) {
        const px = 672 + k * 186;
        P.inset(g, px, 478, 70, 102, 0.12, 0.45);
        P.bevel(g, px + 6, 484, 58, 90, 0.18, 0.4);
        g.append(s('circle', { cx: px + 35, cy: 528, r: 13, fill: 'none', stroke: GOLD, 'stroke-opacity': 0.35 }));
    }
    P.rect(g, 752, 480, 96, 108, P.lin(0, 480, 0, 588, [[0, '#553320'], [1, '#2c190c']]));
    g.append(s('rect', { x: 756, y: 484, width: 88, height: 100, fill: 'none', stroke: GOLD, 'stroke-opacity': 0.55 }));
    g.append(seal(800, 530, 27, ROOM_SEAL));
    // the top, with a polished edge
    P.rect(g, 648, 446, 304, 20, P.lin(648, 0, 952, 0, [[0, '#6e442a'], [0.5, '#9a6640'], [1, '#6e442a']]));
    P.gloss(g, 648, 446, 304, 20, 0.22);
    P.rect(g, 648, 466, 304, 3, '#000', 0.45);
    // documents to sign: a leather tray with papers, a red flag where the signature goes
    P.rect(g, 664, 441, 70, 6, '#000', 0.25);
    P.rect(g, 666, 430, 64, 12, '#f4f0e4');
    P.rect(g, 668, 427, 60, 4, '#ebe5d4');
    P.path(g, 'M672 433h40v1h-40zM672 436h30v1h-30z', '#000', 0.25);
    P.rect(g, 662, 437, 74, 10, P.lin(0, 437, 0, 447, [[0, '#5a3322'], [1, '#2e180e']]), 1, 2);
    P.rect(g, 728, 425, 7, 7, M('#b22234', 0.25), 1, 1);
    // the agenda: folders, a blue one with the seal on top of a manila stack
    P.rect(g, 826, 440, 44, 7, M('#c9b07a', 0.2), 1, 1);
    P.rect(g, 828, 434, 42, 7, M('#d8c38f', 0.2), 1, 1);
    P.rect(g, 824, 428, 44, 8, M('#24365e', 0.2), 1, 1.5);
    g.append(s('circle', { cx: 846, cy: 432, r: 2.4, fill: GOLD }));
    // the pen set
    P.rect(g, 786, 440, 28, 7, GOLD, 1, 2);
    P.path(g, 'M792 440L788 424L790 423.6L795 440ZM808 440L812 424L810 423.6L805 440Z', '#1e1e22');
    // the telephone: a base with its lines and the handset in its cradle
    P.rect(g, 880, 432, 48, 15, '#1e1e22', 1, 3);
    P.rect(g, 884, 434, 40, 3, '#fff', 0.18, 1.5);
    let keys = '';
    for (let r = 0; r < 2; r++)
        for (let c = 0; c < 6; c++)
            keys += `M${902 + c * 4} ${438 + r * 4}h2.4v2h-2.4z`;
    P.path(g, keys, '#b9bcc4', 0.75);
    P.path(g, 'M878 434C878 426 882 424 888 424L898 424C900 424 900 427 898 427L889 427C886 427 884 429 884 432L884 434Z', '#2a2a30');
    P.ellipse(g, 896, 425.6, 4, 2.6, '#2a2a30');
    P.rect(g, 926, 435, 3, 3, M('#3fbf6f', 0.3), 1, 1);
}
// ---- what moves ------------------------------------------------------------------------------------------------------------------
function flag(P, g, x, y, us) {
    P.rect(g, x - 2.5, y - 40, 5, 362, P.lin(x - 3, 0, x + 3, 0, [[0, GOLD_LO], [0.5, BRIGHT_GOLD], [1, GOLD_LO]]));
    g.append(s('circle', { cx: x, cy: y - 46, r: 6, fill: GOLD }), s('circle', { cx: x - 1, cy: y - 47, r: 2, fill: '#fff', 'fill-opacity': 0.6 }));
    P.floorShadow(g, x, y + 324, 40, 8, 0.4);
    const strips = [];
    for (let k = 0; k < 7; k++) {
        const c = us ? (k % 2 === 0 ? M('#b22234', 0.25) : '#efece4') : (k < 4 ? M('#1a2f63', 0.2) : M('#223c78', 0.2));
        strips.push(P.path(g, '', P.lin(x, 0, x + 28, 0, [[0, shade(c, 0.7)], [0.4, shade(c, 1.08)], [0.7, shade(c, 0.82)], [1, shade(c, 0.95)]])));
    }
    if (us) {
        P.rect(g, x + 2, y - 30, 14, 60, M('#3c3b6e', 0.2));
        let d = '';
        for (let k = 0; k < 6; k++) {
            const cx = x + 6.1 + (k % 2) * 5, cy = y - 22.9 + k * 9;
            d += `M${cx - 1.1} ${cy}a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0`;
        }
        P.path(g, d, '#fff', 0.7);
    }
    else {
        g.append(s('circle', { cx: x + 13, cy: y + 37, r: 7, fill: GOLD }));
    }
    P.rect(g, x + 2, y + 152, 24, 4, GOLD, 0.85);
    return t => {
        for (let k = 0; k < 7; k++) {
            const sway = Math.sin(t * 0.8 + k * 0.4) * 2, o = k * 26;
            strips[k].setAttribute('d', `M${x + 2} ${y - 30 + o}C${x + 18} ${y - 26 + o} ${(x + 10 + sway).toFixed(2)} ${y - 10 + o} ${(x + 26 + sway).toFixed(2)} ${y - 4 + o}`
                + `L${(x + 26 + sway).toFixed(2)} ${y + 22 + o}C${(x + 10 + sway).toFixed(2)} ${y + 16 + o} ${x + 18} ${y + 2 + o} ${x + 2} ${y - 4 + o}Z`);
        }
    };
}
function globe(P, g) {
    const cx = 356, cy = 476, r = 38;
    P.ellipse(g, cx, cy, r, r, P.radial(cx, cy, r * 1.4, [[0, M('#8cc3e0', 0.3)], [1, M('#1f4a68', 0.25)]], 1, cx - 12, cy - 14));
    const clip = P.id('globe');
    P.defs.append(s('clipPath', { id: clip }, s('circle', { cx, cy, r })));
    const ball = s('g', { 'clip-path': `url(#${clip})` });
    g.append(ball);
    const land = [0, 1, 2, 3].map(() => P.ellipse(ball, 0, 0, 1, 1, M('#b7c98a', 0.3)));
    const meridians = [-2, -1, 0, 1, 2].map(() => { const e = s('ellipse', { cx, cy, rx: r, ry: r, fill: 'none', stroke: '#fff', 'stroke-opacity': 0.18 }); ball.append(e); return e; });
    P.glow(g, cx - 13, cy - 15, 16, 12, '#ffffff', 0.5);
    const arc = (R, start, extent) => {
        const p = (a) => `${(cx + R * Math.cos((a * Math.PI) / 180)).toFixed(2)} ${(cy - R * Math.sin((a * Math.PI) / 180)).toFixed(2)}`;
        return `M${p(start)}A${R} ${R} 0 ${extent > 180 ? 1 : 0} 0 ${p(start + extent)}`;
    };
    g.append(s('path', { d: arc(r, 250, 140), fill: 'none', stroke: '#000', 'stroke-opacity': 0.25, 'stroke-width': 2 }), s('path', { d: arc(r + 6, 200, 320), fill: 'none', stroke: GOLD, 'stroke-width': 2.4 }));
    return t => {
        const spin = (t * 6) % 120;
        land.forEach((e, k) => {
            const lx = cx - r - 20 + ((spin + k * 30) % 120), w = 18 + k * 4, h = 10 + (k % 2) * 4;
            e.setAttribute('cx', (lx - 9 + w / 2).toFixed(2));
            e.setAttribute('cy', String(cy - 22 + k * 13 + h / 2));
            e.setAttribute('rx', String(w / 2));
            e.setAttribute('ry', String(h / 2));
        });
        meridians.forEach((e, i) => e.setAttribute('rx', (r * Math.abs(Math.cos((i - 2) * 0.5 + spin * 0.05))).toFixed(2)));
    };
}
/** Motes of dust drifting through the sunbeams (Resolute Desk's, seed 77), as bright as the sun is strong. */
function dust(g, sun) {
    const r = new JavaRandom(77);
    const motes = Array.from({ length: 26 }, () => {
        const m = { bx: 600 + r.nextDouble() * 380, by: 380 + r.nextDouble() * 300, sp: 0.2 + r.nextDouble() * 0.5, ph: r.nextDouble() * 6.28, el: s('rect', { width: 2, height: 2, rx: 1, fill: '#fff5dc' }) };
        g.append(m.el);
        return m;
    });
    return t => {
        for (const m of motes) {
            m.el.setAttribute('x', (m.bx + Math.sin(t * m.sp + m.ph) * 14 + ((t * 3 * m.sp) % 30)).toFixed(1));
            m.el.setAttribute('y', (m.by + Math.cos(t * m.sp * 0.7 + m.ph) * 10).toFixed(1));
            m.el.setAttribute('fill-opacity', Math.max(0, (0.12 + 0.12 * Math.sin(t * m.sp * 2 + m.ph)) * Math.min(1, sun * 1.3)).toFixed(3));
        }
    };
}
