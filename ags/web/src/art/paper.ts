// The printed map: heavy cream paper, watercolour terrain washes, an engraved hex grid and ink symbols, a cartouche and a
// compass rose. Drawn once on a canvas; the board uses it as a texture. Everything is procedural; no images.
export const MW = 975, MH = 610, RES = 2.6;
export const SQ3 = Math.sqrt(3);
export type Hex = { q: number; r: number; x: number; y: number; t: string; s?: string; p?: number; f?: string; c?: number };
export type MapData = { R: number; hexes: Hex[]; cities: { name: string; s: string; pop: number; h: number; x: number; y: number }[]; rivers: { name: string; pts: [number, number][] }[];
  outlines: { c: string; rings: [number, number][][]; l: [number, number] }[] };

export const INK = '#2b2118', SEPIA = '#5a4632', PAPER = '#e8dcc0';
const WASH: Record<string, string> = { grass: '#a9bb86', plains: '#d6c58f', desert: '#e6cf9c', tundra: '#bfc2a8', snow: '#f1efe6', coast: '#a8c4c6', ocean: '#94b4ba', lake: '#a3c3c6' };

export function hash(a: number, b: number) { let h = (a * 374761393 + b * 668265263) | 0; h = (h ^ (h >>> 13)) * 1274126177 | 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967296; }
export function corners(x: number, y: number, r: number): [number, number][] { const c: [number, number][] = []; for (let k = 0; k < 6; k++) { const a = Math.PI / 180 * (60 * k - 30); c.push([x + r * Math.cos(a), y + r * Math.sin(a)]); } return c; }
export function hexPath(ctx: CanvasRenderingContext2D, x: number, y: number, r: number) { const c = corners(x, y, r); ctx.beginPath(); ctx.moveTo(c[0][0], c[0][1]); for (let k = 1; k < 6; k++) ctx.lineTo(c[k][0], c[k][1]); ctx.closePath(); }
/** Edge k of a pointy-top hex faces neighbour direction DIRS[k]. */
export const DIRS: [number, number][] = [[1, 0], [1, -1], [0, -1], [-1, 0], [-1, 1], [0, 1]];
export const EDGE: [number, number][] = [[0, 1], [5, 0], [4, 5], [3, 4], [2, 3], [1, 2]];

function paperGrain(ctx: CanvasRenderingContext2D, w: number, h: number) {
  const g = ctx.createRadialGradient(w * .5, h * .45, w * .1, w * .5, h * .5, w * .75); g.addColorStop(0, '#efe5cd'); g.addColorStop(.7, '#e6d8b8'); g.addColorStop(1, '#d2bf97');
  ctx.fillStyle = g; ctx.fillRect(0, 0, w, h);
  for (let i = 0; i < 9000; i++) { const x = hash(i, 3) * w, y = hash(7, i) * h, a = hash(i, i) * .06; ctx.fillStyle = `rgba(90,70,40,${a})`; ctx.fillRect(x, y, .6 + hash(i, 9), .6); }
  for (let i = 0; i < 260; i++) { const x = hash(i, 11) * w, y = hash(13, i) * h, l = 3 + hash(i, 17) * 9, a = hash(i, 19) * Math.PI; ctx.strokeStyle = `rgba(120,95,60,${.05 + hash(i, 23) * .06})`; ctx.lineWidth = .3; ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x + Math.cos(a) * l, y + Math.sin(a) * l); ctx.stroke(); }
}
function blot(ctx: CanvasRenderingContext2D, x: number, y: number, r: number, col: string, a: number) { const g = ctx.createRadialGradient(x, y, 0, x, y, r); g.addColorStop(0, hexA(col, a)); g.addColorStop(1, hexA(col, 0)); ctx.fillStyle = g; ctx.fillRect(x - r, y - r, r * 2, r * 2); }
export function hexA(col: string, a: number) { const n = parseInt(col.slice(1), 16); return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`; }
export function shade(hex: string, f: number) { const n = parseInt(hex.slice(1), 16), c = [(n >> 16) & 255, (n >> 8) & 255, n & 255].map(v => Math.round(f < 0 ? v * (1 + f) : v + (255 - v) * f)); return '#' + c.map(v => Math.max(0, Math.min(255, v)).toString(16).padStart(2, '0')).join(''); }
export function mix(a: string, b: string, t: number) { const p = parseInt(a.slice(1), 16), q = parseInt(b.slice(1), 16); return '#' + [16, 8, 0].map(s => Math.round(((p >> s) & 255) * (1 - t) + ((q >> s) & 255) * t).toString(16).padStart(2, '0')).join(''); }

function inkTree(ctx: CanvasRenderingContext2D, x: number, y: number, s: number, dark = false) {
  ctx.fillStyle = dark ? '#53663f' : '#6f8352'; ctx.strokeStyle = INK; ctx.lineWidth = .35;
  ctx.beginPath(); ctx.moveTo(x, y - 3.6 * s); ctx.lineTo(x + 1.9 * s, y + .6 * s); ctx.lineTo(x - 1.9 * s, y + .6 * s); ctx.closePath(); ctx.fill(); ctx.stroke();
  ctx.beginPath(); ctx.moveTo(x, y + .6 * s); ctx.lineTo(x, y + 1.6 * s); ctx.stroke();
}
function inkPeak(ctx: CanvasRenderingContext2D, x: number, y: number, s: number) {
  ctx.fillStyle = '#c9bca2'; ctx.strokeStyle = INK; ctx.lineWidth = .45;
  ctx.beginPath(); ctx.moveTo(x - s, y + s * .6); ctx.lineTo(x - s * .1, y - s); ctx.lineTo(x + s, y + s * .6); ctx.closePath(); ctx.fill(); ctx.stroke();
  ctx.lineWidth = .3; ctx.beginPath(); for (let k = 1; k < 6; k++) { const t = k / 6; ctx.moveTo(x - s * .1 + (s * 1.1) * t * .55, y - s + s * 1.6 * t * .55); ctx.lineTo(x - s * .1 + s * .15 * t, y + s * .6); } ctx.stroke();
  ctx.fillStyle = '#f6f2e8'; ctx.beginPath(); ctx.moveTo(x - s * .34, y - s * .45); ctx.lineTo(x - s * .1, y - s); ctx.lineTo(x + s * .22, y - s * .5); ctx.closePath(); ctx.fill();
}
function hachure(ctx: CanvasRenderingContext2D, x: number, y: number, s: number) {
  ctx.strokeStyle = SEPIA; ctx.lineWidth = .4; ctx.beginPath(); ctx.arc(x, y + s * .5, s, Math.PI * 1.08, Math.PI * 1.92); ctx.stroke();
  ctx.lineWidth = .25; ctx.beginPath(); for (let k = -3; k <= 3; k++) { const a = Math.PI * 1.5 + k * .17; ctx.moveTo(x + Math.cos(a) * s, y + s * .5 + Math.sin(a) * s); ctx.lineTo(x + Math.cos(a) * s * .7, y + s * .5 + Math.sin(a) * s * .7); } ctx.stroke();
}

export function paintMap(m: MapData): HTMLCanvasElement {
  const R = m.R, c = document.createElement('canvas'); c.width = Math.round(MW * RES); c.height = Math.round(MH * RES);
  const ctx = c.getContext('2d')!; ctx.setTransform(RES, 0, 0, RES, 0, 0);
  paperGrain(ctx, MW, MH);
  const byKey = new Map<string, Hex>(); m.hexes.forEach(h => byKey.set(h.q + ',' + h.r, h));
  const nb = (h: Hex, k: number) => byKey.get((h.q + DIRS[k][0]) + ',' + (h.r + DIRS[k][1]));
  // water: a printed blue wash with engraved ripple lines
  for (const h of m.hexes) { if (h.s) continue; ctx.fillStyle = hexA(WASH[h.t] || WASH.ocean, h.t === 'ocean' ? .55 : .6); hexPath(ctx, h.x, h.y, R + .4); ctx.fill(); }
  ctx.strokeStyle = 'rgba(52,78,88,.32)'; ctx.lineWidth = .35;
  for (const h of m.hexes) { if (h.s || h.t === 'lake') continue; const n = h.t === 'coast' ? 3 : 2; for (let k = 0; k < n; k++) { const yy = h.y - 4 + k * 4 + hash(h.q, k) * 1.5, xx = h.x - 5 + hash(k, h.r) * 2; ctx.beginPath(); ctx.moveTo(xx, yy); ctx.quadraticCurveTo(xx + 2.5, yy - 1.2, xx + 5, yy); ctx.quadraticCurveTo(xx + 7.5, yy + 1.2, xx + 10, yy); ctx.stroke(); } }
  // land: watercolour washes, pigment pooling at random
  for (const h of m.hexes) { if (!h.s) continue; const col = WASH[h.t] || WASH.grass; ctx.fillStyle = hexA(col, .62); hexPath(ctx, h.x, h.y, R + .45); ctx.fill(); }
  for (const h of m.hexes) { if (!h.s) continue; const col = WASH[h.t] || WASH.grass; if (hash(h.q + 5, h.r) > .55) blot(ctx, h.x + (hash(h.q, h.r) - .5) * 8, h.y + (hash(h.r, h.q) - .5) * 8, 8 + hash(h.q, 3) * 8, shade(col, -.25), .22);
    if (h.f === 'forest' || h.f === 'jungle') blot(ctx, h.x, h.y, 10, '#6f8a55', .28); if (h.f === 'mountain') blot(ctx, h.x, h.y, 10, '#9b8c72', .3); if (h.f === 'hills') blot(ctx, h.x, h.y, 9, '#b39e74', .22); }
  // the engraved grid
  ctx.strokeStyle = 'rgba(70,52,34,.28)'; ctx.lineWidth = .3; for (const h of m.hexes) if (h.s) { hexPath(ctx, h.x, h.y, R); ctx.stroke(); }
  // ink symbols
  for (const h of m.hexes) {
    if (!h.s || h.c != null) continue; const j = (a: number, b: number) => hash(h.q * 31 + a, h.r * 17 + b) - .5;
    if (h.f === 'forest' || h.f === 'jungle') { for (const [ox, oy] of [[-4, -1.5], [2.5, -3], [-.5, 3], [4.6, 2.2]]) inkTree(ctx, h.x + ox + j(ox, 1), h.y + oy + j(oy, 2), .95, h.t === 'tundra'); }
    else if (h.f === 'mountain') { inkPeak(ctx, h.x + 3.4, h.y - .6, 4.2); inkPeak(ctx, h.x - 2.6, h.y + .8, 5.6); }
    else if (h.f === 'hills') { hachure(ctx, h.x - 2.6, h.y + 1, 3.6); hachure(ctx, h.x + 3, h.y - 1.6, 3); }
    else if (h.f === 'marsh') { ctx.strokeStyle = SEPIA; ctx.lineWidth = .35; ctx.beginPath(); for (let k = 0; k < 4; k++) { const yy = h.y - 3 + k * 2.2, xx = h.x - 4 + j(k, 5) * 3; ctx.moveTo(xx, yy); ctx.lineTo(xx + 3.5, yy); ctx.moveTo(xx + 1.5, yy); ctx.lineTo(xx + 1.2, yy - 1.6); } ctx.stroke(); }
    else if (h.t === 'desert') { ctx.fillStyle = 'rgba(110,80,40,.5)'; for (let k = 0; k < 10; k++) ctx.fillRect(h.x + j(k, 7) * 14, h.y + j(7, k) * 13, .45, .45); }
    else if (h.t === 'plains' && hash(h.q, h.r) > .5) { ctx.strokeStyle = 'rgba(110,90,40,.5)'; ctx.lineWidth = .3; ctx.beginPath(); for (let k = 0; k < 3; k++) { const xx = h.x + j(k, 9) * 12, yy = h.y + j(9, k) * 11; ctx.moveTo(xx, yy); ctx.lineTo(xx - .5, yy - 1.5); ctx.moveTo(xx + .7, yy); ctx.lineTo(xx + 1.1, yy - 1.3); } ctx.stroke(); }
  }
  // coastlines: an engraved double line where land meets water
  for (const h of m.hexes) { if (!h.s) continue; const cs = corners(h.x, h.y, R), ci = corners(h.x, h.y, R + 1.6);
    for (let k = 0; k < 6; k++) { const n = nb(h, k); if (n && n.s) continue; const [a, b] = EDGE[k];
      ctx.strokeStyle = INK; ctx.lineWidth = .7; ctx.beginPath(); ctx.moveTo(cs[a][0], cs[a][1]); ctx.lineTo(cs[b][0], cs[b][1]); ctx.stroke();
      ctx.strokeStyle = 'rgba(43,33,24,.4)'; ctx.lineWidth = .3; ctx.beginPath(); ctx.moveTo(ci[a][0], ci[a][1]); ctx.lineTo(ci[b][0], ci[b][1]); ctx.stroke(); } }
  // rivers in blue ink, tapering toward the source
  for (const rv of m.rivers) { const p = rv.pts; for (let i = 0; i < p.length - 1; i++) { const t = i / (p.length - 1); ctx.strokeStyle = '#3b5f74'; ctx.lineWidth = .7 + t * 1.3; ctx.lineCap = 'round';
    ctx.beginPath(); const a = i ? [(p[i - 1][0] + p[i][0]) / 2, (p[i - 1][1] + p[i][1]) / 2] : p[0], b = [(p[i][0] + p[i + 1][0]) / 2, (p[i][1] + p[i + 1][1]) / 2]; ctx.moveTo(a[0], a[1]); ctx.quadraticCurveTo(p[i][0], p[i][1], b[0], b[1]); ctx.stroke(); } }
  // state lines: dash-dot, as on an old survey map
  ctx.setLineDash([2.2, .9, .5, .9]); ctx.strokeStyle = 'rgba(43,33,24,.7)'; ctx.lineWidth = .55;
  for (const o of m.outlines) for (const r of o.rings) { ctx.beginPath(); r.forEach(([x, y], i) => (i ? ctx.lineTo(x, y) : ctx.moveTo(x, y))); ctx.closePath(); ctx.stroke(); }
  ctx.setLineDash([]);
  // cities: an ink ring for each, a little skyline for the big ones
  for (const c of m.cities) { const h = m.hexes[c.h], s = Math.min(1.3, .55 + c.pop / 14);
    if (c.pop >= 2.5) { ctx.fillStyle = '#efe6d0'; ctx.strokeStyle = INK; ctx.lineWidth = .4; for (let k = -2; k <= 2; k++) { const bh = (3 + hash(c.h, k) * 4) * s, bx = h.x + k * 1.9 * s; ctx.fillRect(bx - .8 * s, h.y + 1 - bh, 1.6 * s, bh); ctx.strokeRect(bx - .8 * s, h.y + 1 - bh, 1.6 * s, bh); } }
    ctx.fillStyle = c.pop >= 2.5 ? 'rgba(0,0,0,0)' : INK; ctx.strokeStyle = INK; ctx.lineWidth = .5; ctx.beginPath(); ctx.arc(h.x, h.y + (c.pop >= 2.5 ? 2.4 : 0), c.pop >= 2.5 ? 1.2 : .9, 0, 7); ctx.fill(); ctx.stroke(); }
  // state names in engraved capitals
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  for (const o of m.outlines) { const area = o.rings.reduce((a, r) => a + r.length, 0); if (area < 40 || ['DC', 'RI', 'DE', 'CT', 'NJ', 'MD', 'MA', 'VT', 'NH', 'HI'].includes(o.c)) continue;
    ctx.font = `italic 600 ${area > 250 ? 8.5 : 6.5}px "IM Fell English SC", Georgia, serif`; ctx.fillStyle = 'rgba(70,48,30,.62)'; ctx.fillText(o.c, o.l[0], o.l[1] + 6); }
  // the cartouche and the compass rose
  cartouche(ctx, 744, 548); compass(ctx, 900, 470, 26);
  // the edge of the sheet
  ctx.strokeStyle = INK; ctx.lineWidth = 1.4; ctx.strokeRect(6, 6, MW - 12, MH - 12); ctx.lineWidth = .4; ctx.strokeRect(9, 9, MW - 18, MH - 18);
  return c;
}
function cartouche(ctx: CanvasRenderingContext2D, x: number, y: number) {
  const w = 180, h = 40; ctx.fillStyle = 'rgba(239,230,208,.92)'; ctx.strokeStyle = INK; ctx.lineWidth = .9; ctx.beginPath();
  ctx.moveTo(x - w / 2 + 8, y - h / 2); ctx.lineTo(x + w / 2 - 8, y - h / 2); ctx.quadraticCurveTo(x + w / 2, y - h / 2, x + w / 2, y - h / 2 + 8); ctx.lineTo(x + w / 2, y + h / 2 - 8); ctx.quadraticCurveTo(x + w / 2, y + h / 2, x + w / 2 - 8, y + h / 2);
  ctx.lineTo(x - w / 2 + 8, y + h / 2); ctx.quadraticCurveTo(x - w / 2, y + h / 2, x - w / 2, y + h / 2 - 8); ctx.lineTo(x - w / 2, y - h / 2 + 8); ctx.quadraticCurveTo(x - w / 2, y - h / 2, x - w / 2 + 8, y - h / 2); ctx.fill(); ctx.stroke();
  ctx.lineWidth = .35; ctx.strokeRect(x - w / 2 + 4, y - h / 2 + 4, w - 8, h - 8);
  ctx.fillStyle = INK; ctx.textAlign = 'center'; ctx.font = '12px "IM Fell English SC", Georgia, serif'; ctx.fillText('The United States of America', x, y - 6);
  ctx.font = 'italic 7px "Libre Caslon Text", Georgia, serif'; ctx.fillText('A political survey in hexagons · One hex ≈ forty miles', x, y + 9);
}
function compass(ctx: CanvasRenderingContext2D, x: number, y: number, r: number) {
  ctx.strokeStyle = INK; ctx.lineWidth = .5; ctx.beginPath(); ctx.arc(x, y, r, 0, 7); ctx.stroke(); ctx.beginPath(); ctx.arc(x, y, r * .8, 0, 7); ctx.stroke();
  for (let k = 0; k < 16; k++) { const a = k * Math.PI / 8, l = k % 4 === 0 ? r * 1.05 : k % 2 === 0 ? r * .7 : r * .45, w = k % 4 === 0 ? 3 : 1.8;
    const tip: [number, number] = [x + Math.cos(a) * l, y + Math.sin(a) * l], s1: [number, number] = [x + Math.cos(a + Math.PI / 2) * w, y + Math.sin(a + Math.PI / 2) * w], s2: [number, number] = [x + Math.cos(a - Math.PI / 2) * w, y + Math.sin(a - Math.PI / 2) * w];
    ctx.fillStyle = k % 2 ? '#efe6d0' : INK; ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(s1[0], s1[1]); ctx.lineTo(tip[0], tip[1]); ctx.closePath(); ctx.fill(); ctx.stroke();
    ctx.fillStyle = k % 2 ? INK : '#efe6d0'; ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(s2[0], s2[1]); ctx.lineTo(tip[0], tip[1]); ctx.closePath(); ctx.fill(); ctx.stroke(); }
  ctx.fillStyle = INK; ctx.font = '9px "IM Fell English SC", Georgia, serif'; ctx.textAlign = 'center'; ctx.fillText('N', x, y - r - 5);
}

/** The table under the map: dark walnut with grain. */
export function paintTable(w: number, h: number): HTMLCanvasElement {
  const c = document.createElement('canvas'); c.width = w; c.height = h; const ctx = c.getContext('2d')!;
  const g = ctx.createLinearGradient(0, 0, 0, h); g.addColorStop(0, '#2d1c10'); g.addColorStop(1, '#22150b'); ctx.fillStyle = g; ctx.fillRect(0, 0, w, h);
  for (let i = 0; i < 420; i++) { const y = hash(i, 1) * h, a = .03 + hash(i, 2) * .07; ctx.strokeStyle = hash(i, 4) > .5 ? `rgba(90,60,35,${a})` : `rgba(10,5,2,${a})`; ctx.lineWidth = .5 + hash(i, 3) * 2.5;
    ctx.beginPath(); ctx.moveTo(0, y); for (let x = 0; x <= w; x += 40) ctx.lineTo(x, y + Math.sin(x / (120 + hash(i, 5) * 200) + i) * (2 + hash(i, 6) * 5)); ctx.stroke(); }
  for (let i = 0; i < 6; i++) { const x = hash(i, 9) * w, y = hash(9, i) * h, rr = 8 + hash(i, 8) * 14; ctx.strokeStyle = 'rgba(15,8,3,.25)'; ctx.lineWidth = 1; for (let k = 0; k < 4; k++) { ctx.beginPath(); ctx.ellipse(x, y, rr + k * 4, (rr + k * 4) * .45, .2, 0, 7); ctx.stroke(); } }
  return c;
}
