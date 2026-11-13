// Restrained vector portraits: head and shoulders in realistic proportions (the head about two-fifths of the bust),
// muted tones, a neutral expression. Every face is drawn from the person's look, which the engine sends with them
// (data/portraits.csv, or a fictional newcomer's own recorded look): presentation, skin, hair and its colour, facial
// hair, glasses, the shape of the face and age. Nothing is derived from a list position or a hash of the name, so a
// person always looks the same on every screen and in every save; with no look, a neutral placeholder is drawn,
// never somebody else. No photographs or outside imagery: everything here is drawn in vectors.
import { s } from '../core/dom.js';
const SKINS = ['#f0d3be', '#e5bc9a', '#cf9d7a', '#b07656', '#87593d', '#5e3c29'];
const HAIR_COLOURS = {
    black: '#1d1916', dark: '#33261d', brown: '#5c4130', 'light-brown': '#8a6a4a', blond: '#b8996b', salt: '#6f6a66', grey: '#9c9b98',
    white: '#d6d3cd', red: '#7d3f26', none: '#000000',
};
const SUITS = ['#232c3d', '#2b2e35', '#454a53', '#25304a'];
const TIES = ['#6e2a2c', '#2f4a6e', '#5d5546', '#3d5a4c', '#7d2f33'];
const BLOUSES = ['#d9d3c7', '#e4ddd2', '#b9a37a', '#c7ced9'];
/** Clothing only (never identity) varies by id, and stably: the same person always wears the same suit. */
function hash(id) { let x = 2166136261; for (const c of id) {
    x ^= c.charCodeAt(0);
    x = Math.imul(x, 16777619);
} return x >>> 0; }
function shade(hex, f) {
    const n = parseInt(hex.slice(1), 16), c = [(n >> 16) & 255, (n >> 8) & 255, n & 255].map(v => Math.round(f < 0 ? v * (1 + f) : v + (255 - v) * f));
    return '#' + c.map(v => v.toString(16).padStart(2, '0')).join('');
}
// faces: an oval by default; round is wider, long is narrower with a longer chin, square has a broad jaw
const FACE = {
    oval: 'M41 52 C41 36 50 30 60 30 C70 30 79 36 79 52 C79 67 74 78 66 82 C62 84.5 58 84.5 54 82 C46 78 41 67 41 52 Z',
    round: 'M39.5 53 C39.5 36 49 30 60 30 C71 30 80.5 36 80.5 53 C80.5 68 75 79 66 82.5 C62 84 58 84 54 82.5 C45 79 39.5 68 39.5 53 Z',
    long: 'M42 51 C42 35 50.5 29 60 29 C69.5 29 78 35 78 51 C78 68 73 80 65.5 84.5 C62 86.5 58 86.5 54.5 84.5 C47 80 42 68 42 51 Z',
    square: 'M40.5 52 C40.5 36 50 30 60 30 C70 30 79.5 36 79.5 52 C79.5 66 77.5 75 70 81 C65.5 84 54.5 84 50 81 C42.5 75 40.5 66 40.5 52 Z',
};
const TOP = {
    'short': 'M40 54 C38 38 47 27 60 27 C73 27 82 38 80 54 C79 46 76 40 70 37.5 C64 35.5 55 35.5 49 38 C44 40.5 41 46 40 54 Z',
    'side-part': 'M40 55 C37 38 46 26.5 60 26.5 C74 26.5 83 38 80 55 C79 46 75 39 66 36.5 C60 35 54 35.5 50 37.5 C46 39.5 42 46 40 55 Z M50 37.5 C54 33.5 58 32 62 31.5',
    'receding': 'M40 55 C38 44 42 34 50 30.5 C47 36 46 40 44 46 C43 49 41.5 52 40 55 Z M80 55 C82 44 78 34 70 30.5 C73 36 74 40 76 46 C77 49 78.5 52 80 55 Z',
    'close': 'M40.5 53 C38.5 37 47.5 27.5 60 27.5 C72.5 27.5 81.5 37 79.5 53 C78.5 44 74 37.8 60 36.8 C46 37.8 41.5 44 40.5 53 Z',
    'slicked': 'M40 54 C37.5 38 46 25.5 60 25.5 C74 25.5 82.5 38 80 54 C79 45 77 39 72 36 C66 33 54 33 48 36 C43 39 41 45 40 54 Z',
    'swept': 'M40 55 C37 38 46 26 59 26 C73 26 83 37 80 55 C79 45 75 38 68 35.5 C62 33.5 52 34 46 38.5 C43 42 41 48 40 55 Z M46 38.5 C52 31 62 29.5 72 32',
    'bob': 'M38 58 C36 38 46 27 60 27 C74 27 84 38 82 58 C80 48 76 40 64 37 C60 41 52 43 44 43 C41 47 39 52 38 58 Z',
    'shoulder': 'M38 60 C35 38 46 26.5 60 26.5 C74 26.5 85 38 82 60 C80 48 75 39 66 36 C60 40 51 42.5 44 42.5 C41 47 39 53 38 60 Z',
    'long': 'M37.5 62 C35 38 46 26.5 60 26.5 C74 26.5 85 38 82.5 62 C80.5 48 75 39 66 36 C60 40 51 42.5 44 42.5 C40.5 47 38.5 54 37.5 62 Z',
    'pulled-back': 'M40 54 C38 37 47 27 60 27 C73 27 82 37 80 54 C79 45 75 38.5 60 37 C45 38.5 41 45 40 54 Z M72 30 C80 30 84 36 82 42 C80 38 76 34 72 30 Z',
};
/** A neutral placeholder for anyone without a portrait: a plain silhouette, no features, never another person's face. */
function placeholder(svg, uid) {
    svg.append(s('path', { d: 'M6 140 C8 122 16 112 34 106 L50 100 L70 100 L86 106 C104 112 112 122 114 140 Z', fill: '#3a4250' }), s('ellipse', { cx: 60, cy: 58, rx: 20, ry: 25, fill: '#4b5463' }), s('text', { x: 60, y: 64, 'text-anchor': 'middle', fill: '#9aa4b2', 'font-size': '18', 'font-family': 'Inter, sans-serif', 'font-weight': '700' }, '?'));
    svg.dataset['placeholder'] = uid;
    return svg;
}
/**
 * A portrait as an SVG (decorative: the name always appears beside it). Without the backdrop, the bust stands on its
 * own, for placing in a scene (the president at the Resolute Desk). With no look, the neutral placeholder.
 */
export function portrait(id, party, size = 96, backdrop = true, look) {
    const uid = 'p' + hash(id + party).toString(36);
    const bgTop = party === 'D' ? '#22314a' : '#3a2629', bgBottom = '#121820';
    const svg = s('svg', { class: 'portrait', viewBox: '0 0 120 140', width: size, height: Math.round(size * 140 / 120), 'aria-hidden': 'true', focusable: 'false',
        'data-person': id, 'data-presentation': look?.presentation ?? 'none' });
    svg.append(s('defs', null, s('linearGradient', { id: uid + 'bg', x1: 0, y1: 0, x2: 0, y2: 1 }, s('stop', { offset: '0', 'stop-color': bgTop }), s('stop', { offset: '1', 'stop-color': bgBottom }))));
    if (backdrop)
        svg.append(s('rect', { width: 120, height: 140, fill: `url(#${uid}bg)` }));
    if (!look)
        return placeholder(svg, uid);
    const L = look, n = hash(id), masc = L.presentation === 'm';
    const skin = SKINS[Math.max(0, Math.min(5, L.skin - 1))], skinShadow = shade(skin, -0.16), skinDeep = shade(skin, -0.3);
    const hair = HAIR_COLOURS[L.hairColour] ?? HAIR_COLOURS['dark'], hairLight = shade(hair, 0.18);
    const suit = SUITS[n % SUITS.length], accent = masc ? TIES[(n >>> 5) % TIES.length] : BLOUSES[(n >>> 5) % BLOUSES.length];
    const face = FACE[L.face] ?? FACE['oval'], bald = L.hair === 'bald';
    svg.querySelector('defs').append(s('radialGradient', { id: uid + 'face', cx: '0.42', cy: '0.38', r: '0.75' }, s('stop', { offset: '0', 'stop-color': shade(skin, 0.06) }), s('stop', { offset: '1', 'stop-color': skinShadow })), s('linearGradient', { id: uid + 'suit', x1: 0, y1: 0, x2: 1, y2: 1 }, s('stop', { offset: '0', 'stop-color': shade(suit, 0.12) }), s('stop', { offset: '1', 'stop-color': shade(suit, -0.2) })));
    // hair that falls behind the shoulders goes first
    if (L.hair === 'shoulder')
        svg.append(s('path', { d: 'M37 52 C35 76 34 96 40 108 L80 108 C86 96 85 76 83 52 Z', fill: hair }));
    if (L.hair === 'long')
        svg.append(s('path', { d: 'M36.5 52 C34 80 33 104 38 118 L82 118 C87 104 86 80 83.5 52 Z', fill: hair }));
    if (L.hair === 'bob')
        svg.append(s('path', { d: 'M37 50 C35 66 35 80 39 88 C46 92 74 92 81 88 C85 80 85 66 83 50 Z', fill: hair }));
    // shoulders and jacket
    svg.append(s('path', { d: 'M6 140 C8 122 16 112 34 106 L50 100 L70 100 L86 106 C104 112 112 122 114 140 Z', fill: `url(#${uid}suit)` }));
    svg.append(s('path', { d: 'M51 80 L69 80 L70 101 C64 105 56 105 50 101 Z', fill: skinShadow }));
    if (masc) {
        svg.append(s('path', { d: 'M50 100 L60 126 L70 100 L66 99 L60 106 L54 99 Z', fill: '#e9e6e0' }), s('path', { d: 'M57.5 104 L62.5 104 L64 110 L61.5 132 L58.5 132 L56 110 Z', fill: accent }), s('path', { d: 'M57.5 104 L62.5 104 L61.8 107.5 L58.2 107.5 Z', fill: shade(accent, -0.2) }));
    }
    else {
        svg.append(s('path', { d: 'M49 100 C53 112 67 112 71 100 L66 99 C63 104 57 104 54 99 Z', fill: accent }));
    }
    svg.append(s('path', { d: 'M50 100 L44 104 L52 124 L58 128 Z', fill: shade(suit, -0.25) }), s('path', { d: 'M70 100 L76 104 L68 124 L62 128 Z', fill: shade(suit, -0.25) }));
    svg.append(s('ellipse', { cx: 40.5, cy: 58, rx: 3.2, ry: 6, fill: skinShadow }), s('ellipse', { cx: 79.5, cy: 58, rx: 3.2, ry: 6, fill: skinShadow }));
    svg.append(s('path', { d: face, fill: `url(#${uid}face)` }));
    svg.append(s('path', { d: 'M72 40 C77 48 78 62 72 74 C70 78 67 81 64 82.5 C70 76 74 64 72 40 Z', fill: skinDeep, opacity: '0.28' }));
    // facial hair, under the features
    const beardColour = L.hairColour === 'none' ? '#3b302a' : L.hairColour === 'white' ? '#c9c5be' : shade(hair, 0.05);
    if (L.facial === 'beard')
        svg.append(s('path', { d: 'M41.5 58 C42 70 47 79.5 54 82.5 C58 84.8 62 84.8 66 82.5 C73 79.5 78 70 78.5 58 C76 66 73 70 68 71.5 C64 70.5 56 70.5 52 71.5 C47 70 44 66 41.5 58 Z', fill: beardColour, opacity: '0.92' }));
    if (L.facial === 'goatee')
        svg.append(s('path', { d: 'M54 77.5 C56 82 64 82 66 77.5 C63.5 79 56.5 79 54 77.5 Z M55 72.6 C58 71.4 62 71.4 65 72.6 C63 73.6 57 73.6 55 72.6 Z', fill: beardColour }));
    if (L.facial === 'moustache')
        svg.append(s('path', { d: 'M53.5 72.6 C56.5 70.8 63.5 70.8 66.5 72.6 C64 73.9 56 73.9 53.5 72.6 Z', fill: beardColour }));
    if (L.facial === 'stubble')
        svg.append(s('path', { d: 'M43 62 C45 74 51 81 57 83 C59 83.6 61 83.6 63 83 C69 81 75 74 77 62 C74 70 68 74 60 74.5 C52 74 46 70 43 62 Z', fill: beardColour, opacity: '0.25' }));
    // brows, eyes, nose, mouth: drawn small and level
    const brow = L.hairColour === 'none' ? '#3b302a' : shade(hair, -0.1);
    svg.append(s('path', { d: 'M47.5 51.2 C50 49.6 53.4 49.6 56 50.6', stroke: brow, 'stroke-width': masc ? 1.7 : 1.4, fill: 'none', 'stroke-linecap': 'round' }), s('path', { d: 'M64 50.6 C66.6 49.6 70 49.6 72.5 51.2', stroke: brow, 'stroke-width': masc ? 1.7 : 1.4, fill: 'none', 'stroke-linecap': 'round' }));
    for (const cx of [52, 68]) {
        svg.append(s('ellipse', { cx, cy: 57, rx: 3.3, ry: 1.8, fill: '#f4efe8' }), s('circle', { cx, cy: 57, r: 1.45, fill: '#3a2c24' }), s('path', { d: `M${cx - 3.6} 56.6 C${cx - 1.5} 54.8 ${cx + 1.5} 54.8 ${cx + 3.6} 56.6`, stroke: skinDeep, 'stroke-width': masc ? 0.8 : 1.1, fill: 'none' }));
    }
    svg.append(s('path', { d: 'M60.5 59 C59.5 63 58 66.5 58.6 68.4 C59.6 69.4 61.6 69.4 62.6 68.6', stroke: skinDeep, 'stroke-width': 0.9, fill: 'none', 'stroke-linecap': 'round', opacity: '0.8' }));
    if (L.facial !== 'beard')
        svg.append(s('path', { d: 'M54.5 74.6 C57.5 76 62.5 76 65.5 74.6', stroke: masc ? shade(skin, -0.38) : '#9a5a55', 'stroke-width': 1.3, fill: 'none', 'stroke-linecap': 'round' }), s('path', { d: 'M55.5 75.4 C58 77.3 62 77.3 64.5 75.4', stroke: shade(skin, -0.2), 'stroke-width': 0.8, fill: 'none', opacity: '0.6' }));
    if (L.age > 0.4)
        svg.append(s('path', { d: 'M49 66 C50 69 52 71 54 72 M71 66 C70 69 68 71 66 72', stroke: skinDeep, 'stroke-width': 0.6, fill: 'none', opacity: String(0.25 + 0.3 * L.age) }));
    if (L.age > 0.6)
        svg.append(s('path', { d: 'M51 44.5 C56 43.2 64 43.2 69 44.5', stroke: skinDeep, 'stroke-width': 0.5, fill: 'none', opacity: '0.35' }));
    // hair on top (none for a bald head: a soft highlight instead)
    if (bald)
        svg.append(s('path', { d: 'M47 36 C52 32.5 64 32.5 71 35', stroke: shade(skin, 0.25), 'stroke-width': 2.2, fill: 'none', opacity: '0.5', 'stroke-linecap': 'round' }));
    else {
        svg.append(s('path', { d: TOP[L.hair] ?? TOP['short'], fill: hair, 'fill-rule': 'evenodd' }));
        svg.append(s('path', { d: 'M48 34 C54 31 64 31 70 33.5', stroke: hairLight, 'stroke-width': 1.2, fill: 'none', opacity: '0.45', 'stroke-linecap': 'round' }));
    }
    if (L.glasses) {
        const frame = '#2a2522';
        svg.append(s('rect', { x: 46.5, y: 53.6, width: 11, height: 7.4, rx: 2.6, fill: 'none', stroke: frame, 'stroke-width': 1.1 }), s('rect', { x: 62.5, y: 53.6, width: 11, height: 7.4, rx: 2.6, fill: 'none', stroke: frame, 'stroke-width': 1.1 }), s('path', { d: 'M57.5 56.4 C59 55.4 61 55.4 62.5 56.4 M46.5 56 L42 55 M73.5 56 L78 55', stroke: frame, 'stroke-width': 1, fill: 'none' }));
    }
    svg.append(s('rect', { x: 44.5, y: 114, width: 5, height: 3.4, rx: 0.6, fill: '#c9cdd4' }), s('rect', { x: 44.5, y: 114, width: 2.2, height: 1.7, fill: '#3b5b8f' }));
    return svg;
}
