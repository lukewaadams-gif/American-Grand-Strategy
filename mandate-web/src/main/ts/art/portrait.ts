// Restrained vector portraits: head and shoulders in realistic proportions (the head about two-fifths of the bust),
// muted tones, a neutral expression. No caricature: no big heads, no exaggerated features, no cartoon eyes.
// The fictional cast is drawn deliberately; anyone else gets a stable look derived from their id.
import { s } from '../core/dom.js';

type Hair = 'short' | 'side-part' | 'receding' | 'close' | 'bob' | 'shoulder' | 'pulled-back';
interface Look { skin: string; hair: Hair; hairColour: string; suit: string; neck: 'tie' | 'blouse'; tie?: string; glasses?: boolean; age?: number; }

const CAST: Record<string, Look> = {
  d_reyes: { skin: '#cf9d7a', hair: 'shoulder', hairColour: '#33261d', suit: '#25304a', neck: 'blouse', tie: '#d9d3c7' },
  d_whitfield: { skin: '#eccbb2', hair: 'side-part', hairColour: '#9c9b98', suit: '#2c2f36', neck: 'tie', tie: '#2f4a6e', glasses: true, age: 0.7 },
  d_okafor: { skin: '#6a432f', hair: 'close', hairColour: '#1d1916', suit: '#2a2e3a', neck: 'blouse', tie: '#b9a37a' },
  d_lindqvist: { skin: '#efd2bd', hair: 'receding', hairColour: '#b49a72', suit: '#4a4f58', neck: 'tie', tie: '#35577f', age: 0.5 },
  r_calloway: { skin: '#e6c1a2', hair: 'side-part', hairColour: '#4a3527', suit: '#222b3c', neck: 'tie', tie: '#7d2f33' },
  r_marsh: { skin: '#e5bf9f', hair: 'bob', hairColour: '#b8996b', suit: '#24304a', neck: 'blouse', tie: '#e4ddd2' },
  r_benton: { skin: '#d9ad88', hair: 'short', hairColour: '#a6a5a2', suit: '#2b2e34', neck: 'tie', tie: '#6e2a2c', age: 0.8 },
  r_harlan: { skin: '#eecfb8', hair: 'pulled-back', hairColour: '#6f3a27', suit: '#454a53', neck: 'blouse', tie: '#c7ced9', glasses: true },
};

const SKINS = ['#f0d3be', '#e5bc9a', '#cf9d7a', '#b07656', '#87593d', '#5e3c29'];
const HAIRS = ['#221c18', '#3b2b21', '#5c4130', '#8a6a4a', '#b49a72', '#9c9b98'];
const SUITS = ['#232c3d', '#2b2e35', '#454a53'];
const TIES = ['#6e2a2c', '#2f4a6e', '#5d5546', '#3d5a4c'];
const STYLES: Hair[] = ['short', 'side-part', 'receding', 'close', 'bob', 'shoulder', 'pulled-back'];

function hash(id: string): number { let x = 2166136261; for (const c of id) { x ^= c.charCodeAt(0); x = Math.imul(x, 16777619); } return x >>> 0; }

function lookFor(id: string): Look {
  const cast = CAST[id];
  if (cast) return cast;
  const n = hash(id), pick = <T>(a: T[], k: number) => a[(n >>> k) % a.length]!;
  const hair = pick(STYLES, 3);
  return { skin: pick(SKINS, 0), hair, hairColour: pick(HAIRS, 7), suit: pick(SUITS, 11), neck: ['bob', 'shoulder', 'pulled-back'].includes(hair) ? 'blouse' : 'tie', tie: pick(TIES, 13) };
}

function shade(hex: string, f: number): string {
  const n = parseInt(hex.slice(1), 16), c = [(n >> 16) & 255, (n >> 8) & 255, n & 255].map(v => Math.round(f < 0 ? v * (1 + f) : v + (255 - v) * f));
  return '#' + c.map(v => v.toString(16).padStart(2, '0')).join('');
}

/**
 * A portrait as an SVG (decorative: the name always appears beside it). Without the backdrop, the bust stands on its
 * own, for placing in a scene (the president at the Resolute Desk).
 */
export function portrait(id: string, party: 'D' | 'R', size = 96, backdrop = true): SVGSVGElement {
  const L = lookFor(id), uid = 'p' + hash(id + party).toString(36);
  const skin = L.skin, skinShadow = shade(skin, -0.16), skinDeep = shade(skin, -0.3), hair = L.hairColour, hairLight = shade(hair, 0.18);
  const bgTop = party === 'D' ? '#22314a' : '#3a2629', bgBottom = '#121820';
  const svg = s('svg', { class: 'portrait', viewBox: '0 0 120 140', width: size, height: Math.round(size * 140 / 120), 'aria-hidden': 'true', focusable: 'false' });
  svg.append(s('defs', null,
    s('linearGradient', { id: uid + 'bg', x1: 0, y1: 0, x2: 0, y2: 1 }, s('stop', { offset: '0', 'stop-color': bgTop }), s('stop', { offset: '1', 'stop-color': bgBottom })),
    s('radialGradient', { id: uid + 'face', cx: '0.42', cy: '0.38', r: '0.75' }, s('stop', { offset: '0', 'stop-color': shade(skin, 0.06) }), s('stop', { offset: '1', 'stop-color': skinShadow })),
    s('linearGradient', { id: uid + 'suit', x1: 0, y1: 0, x2: 1, y2: 1 }, s('stop', { offset: '0', 'stop-color': shade(L.suit, 0.12) }), s('stop', { offset: '1', 'stop-color': shade(L.suit, -0.2) }))));
  if (backdrop) svg.append(s('rect', { width: 120, height: 140, fill: `url(#${uid}bg)` }));

  // hair that falls behind the shoulders goes first
  if (L.hair === 'shoulder') svg.append(s('path', { d: 'M37 52 C35 76 34 96 40 108 L80 108 C86 96 85 76 83 52 Z', fill: hair }));
  if (L.hair === 'bob') svg.append(s('path', { d: 'M37 50 C35 66 35 80 39 88 C46 92 74 92 81 88 C85 80 85 66 83 50 Z', fill: hair }));

  // shoulders and jacket
  svg.append(s('path', { d: 'M6 140 C8 122 16 112 34 106 L50 100 L70 100 L86 106 C104 112 112 122 114 140 Z', fill: `url(#${uid}suit)` }));
  // neck
  svg.append(s('path', { d: 'M51 80 L69 80 L70 101 C64 105 56 105 50 101 Z', fill: skinShadow }));
  if (L.neck === 'tie') {
    svg.append(s('path', { d: 'M50 100 L60 126 L70 100 L66 99 L60 106 L54 99 Z', fill: '#e9e6e0' }),
      s('path', { d: 'M57.5 104 L62.5 104 L64 110 L61.5 132 L58.5 132 L56 110 Z', fill: L.tie ?? '#6e2a2c' }),
      s('path', { d: 'M57.5 104 L62.5 104 L61.8 107.5 L58.2 107.5 Z', fill: shade(L.tie ?? '#6e2a2c', -0.2) }));
  } else {
    svg.append(s('path', { d: 'M49 100 C53 112 67 112 71 100 L66 99 C63 104 57 104 54 99 Z', fill: L.tie ?? '#d9d3c7' }));
  }
  // lapels
  svg.append(s('path', { d: 'M50 100 L44 104 L52 124 L58 128 Z', fill: shade(L.suit, -0.25) }),
    s('path', { d: 'M70 100 L76 104 L68 124 L62 128 Z', fill: shade(L.suit, -0.25) }));
  // ears
  svg.append(s('ellipse', { cx: 40.5, cy: 58, rx: 3.2, ry: 6, fill: skinShadow }), s('ellipse', { cx: 79.5, cy: 58, rx: 3.2, ry: 6, fill: skinShadow }));
  // face: an oval with a defined jaw, about 54 units tall in a 140-unit bust
  svg.append(s('path', { d: 'M41 52 C41 36 50 30 60 30 C70 30 79 36 79 52 C79 67 74 78 66 82 C62 84.5 58 84.5 54 82 C46 78 41 67 41 52 Z', fill: `url(#${uid}face)` }));
  // a soft shadow on the far side of the face, and under the jaw
  svg.append(s('path', { d: 'M72 40 C77 48 78 62 72 74 C70 78 67 81 64 82.5 C70 76 74 64 72 40 Z', fill: skinDeep, opacity: '0.28' }));
  // brows, eyes, nose, mouth: drawn small and level
  const brow = shade(hair, -0.1);
  svg.append(s('path', { d: 'M47.5 51.2 C50 49.6 53.4 49.6 56 50.6', stroke: brow, 'stroke-width': 1.5, fill: 'none', 'stroke-linecap': 'round' }),
    s('path', { d: 'M64 50.6 C66.6 49.6 70 49.6 72.5 51.2', stroke: brow, 'stroke-width': 1.5, fill: 'none', 'stroke-linecap': 'round' }));
  for (const cx of [52, 68]) {
    svg.append(s('ellipse', { cx, cy: 57, rx: 3.3, ry: 1.8, fill: '#f4efe8' }), s('circle', { cx, cy: 57, r: 1.45, fill: '#3a2c24' }),
      s('path', { d: `M${cx - 3.6} 56.6 C${cx - 1.5} 54.8 ${cx + 1.5} 54.8 ${cx + 3.6} 56.6`, stroke: skinDeep, 'stroke-width': 0.8, fill: 'none' }));
  }
  svg.append(s('path', { d: 'M60.5 59 C59.5 63 58 66.5 58.6 68.4 C59.6 69.4 61.6 69.4 62.6 68.6', stroke: skinDeep, 'stroke-width': 0.9, fill: 'none', 'stroke-linecap': 'round', opacity: '0.8' }),
    s('path', { d: 'M54.5 74.6 C57.5 76 62.5 76 65.5 74.6', stroke: shade(skin, -0.38), 'stroke-width': 1.3, fill: 'none', 'stroke-linecap': 'round' }),
    s('path', { d: 'M55.5 75.4 C58 77.3 62 77.3 64.5 75.4', stroke: shade(skin, -0.2), 'stroke-width': 0.8, fill: 'none', opacity: '0.6' }));
  if ((L.age ?? 0) > 0.4) svg.append(s('path', { d: 'M49 66 C50 69 52 71 54 72 M71 66 C70 69 68 71 66 72', stroke: skinDeep, 'stroke-width': 0.6, fill: 'none', opacity: String(0.25 + 0.3 * (L.age ?? 0)) }));
  // hair on top
  const top: Record<Hair, string> = {
    'short': 'M40 54 C38 38 47 27 60 27 C73 27 82 38 80 54 C79 46 76 40 70 37.5 C64 35.5 55 35.5 49 38 C44 40.5 41 46 40 54 Z',
    'side-part': 'M40 55 C37 38 46 26.5 60 26.5 C74 26.5 83 38 80 55 C79 46 75 39 66 36.5 C60 35 54 35.5 50 37.5 C46 39.5 42 46 40 55 Z M50 37.5 C54 33.5 58 32 62 31.5',
    'receding': 'M40 55 C38 44 42 34 50 30.5 C47 36 46 40 44 46 C43 49 41.5 52 40 55 Z M80 55 C82 44 78 34 70 30.5 C73 36 74 40 76 46 C77 49 78.5 52 80 55 Z',
    'close': 'M40.5 53 C38.5 37 47.5 27.5 60 27.5 C72.5 27.5 81.5 37 79.5 53 C78.5 44 74 37.8 60 36.8 C46 37.8 41.5 44 40.5 53 Z',
    'bob': 'M38 58 C36 38 46 27 60 27 C74 27 84 38 82 58 C80 48 76 40 64 37 C60 41 52 43 44 43 C41 47 39 52 38 58 Z',
    'shoulder': 'M38 60 C35 38 46 26.5 60 26.5 C74 26.5 85 38 82 60 C80 48 75 39 66 36 C60 40 51 42.5 44 42.5 C41 47 39 53 38 60 Z',
    'pulled-back': 'M40 54 C38 37 47 27 60 27 C73 27 82 37 80 54 C79 45 75 38.5 60 37 C45 38.5 41 45 40 54 Z M72 30 C80 30 84 36 82 42 C80 38 76 34 72 30 Z',
  };
  svg.append(s('path', { d: top[L.hair], fill: hair, 'fill-rule': 'evenodd' }));
  svg.append(s('path', { d: 'M48 34 C54 31 64 31 70 33.5', stroke: hairLight, 'stroke-width': 1.2, fill: 'none', opacity: '0.45', 'stroke-linecap': 'round' }));
  if (L.glasses) {
    const frame = '#2a2522';
    svg.append(s('rect', { x: 46.5, y: 53.6, width: 11, height: 7.4, rx: 2.6, fill: 'none', stroke: frame, 'stroke-width': 1.1 }),
      s('rect', { x: 62.5, y: 53.6, width: 11, height: 7.4, rx: 2.6, fill: 'none', stroke: frame, 'stroke-width': 1.1 }),
      s('path', { d: 'M57.5 56.4 C59 55.4 61 55.4 62.5 56.4 M46.5 56 L42 55 M73.5 56 L78 55', stroke: frame, 'stroke-width': 1, fill: 'none' }));
  }
  // a small flag pin on the left lapel
  svg.append(s('rect', { x: 44.5, y: 114, width: 5, height: 3.4, rx: 0.6, fill: '#c9cdd4' }), s('rect', { x: 44.5, y: 114, width: 2.2, height: 1.7, fill: '#3b5b8f' }));
  return svg;
}
