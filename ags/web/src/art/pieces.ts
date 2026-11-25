// Board pieces, drawn as if they were physical: painted miniatures on round bases, wooden HQ houses, cardboard counters.
// Each is a small canvas the board turns into a texture; the lamp is up and to the left, so shadows fall down and right.
import { shade, hexA } from './paper';

const S = 2; // drawn at twice the size they appear, for crisp zoom
const cache = new Map<string, HTMLCanvasElement>();
function canvas(w: number, h: number) { const c = document.createElement('canvas'); c.width = w * S; c.height = h * S; const x = c.getContext('2d')!; x.scale(S, S); return [c, x] as const; }

function base(ctx: CanvasRenderingContext2D, cx: number, cy: number, rx: number, col: string) {
  ctx.fillStyle = 'rgba(0,0,0,.38)'; ctx.beginPath(); ctx.ellipse(cx + 3, cy + 4, rx + 2, rx * .36 + 1, 0, 0, 7); ctx.fill(); // shadow on the table
  ctx.fillStyle = '#1e140c'; ctx.beginPath(); ctx.ellipse(cx, cy + 2.4, rx, rx * .35, 0, 0, 7); ctx.fill(); // the base's edge
  const g = ctx.createLinearGradient(cx - rx, 0, cx + rx, 0); g.addColorStop(0, shade(col, .25)); g.addColorStop(1, shade(col, -.35));
  ctx.fillStyle = g; ctx.beginPath(); ctx.ellipse(cx, cy, rx, rx * .35, 0, 0, 7); ctx.fill();
  ctx.strokeStyle = 'rgba(255,240,210,.55)'; ctx.lineWidth = .8; ctx.beginPath(); ctx.ellipse(cx, cy, rx - .8, rx * .35 - .6, 0, Math.PI * 1.05, Math.PI * 1.75); ctx.stroke();
}
function figure(ctx: CanvasRenderingContext2D, cx: number, foot: number, s: number, coat: string, skin: string, hair: string, pose: 'wave' | 'hold' | 'mouth' | 'board' | 'case' | 'still') {
  const lit = (c: string) => { const g = ctx.createLinearGradient(cx - 7 * s, 0, cx + 7 * s, 0); g.addColorStop(0, shade(c, .22)); g.addColorStop(.55, c); g.addColorStop(1, shade(c, -.38)); return g; };
  ctx.lineJoin = 'round';
  // legs
  ctx.fillStyle = lit('#2c2a33'); ctx.fillRect(cx - 4.2 * s, foot - 13 * s, 3.6 * s, 13 * s); ctx.fillRect(cx + .6 * s, foot - 13 * s, 3.6 * s, 13 * s);
  ctx.fillStyle = '#120d0a'; ctx.fillRect(cx - 4.6 * s, foot - 1.4 * s, 4.4 * s, 1.6 * s); ctx.fillRect(cx + .4 * s, foot - 1.4 * s, 4.4 * s, 1.6 * s);
  // coat
  ctx.fillStyle = lit(coat); ctx.beginPath(); ctx.moveTo(cx - 7 * s, foot - 11 * s); ctx.lineTo(cx - 6 * s, foot - 26 * s); ctx.quadraticCurveTo(cx, foot - 29 * s, cx + 6 * s, foot - 26 * s); ctx.lineTo(cx + 7 * s, foot - 11 * s); ctx.closePath(); ctx.fill();
  ctx.strokeStyle = 'rgba(20,12,6,.55)'; ctx.lineWidth = .5; ctx.stroke();
  ctx.fillStyle = '#f3ecdc'; ctx.beginPath(); ctx.moveTo(cx - 1.6 * s, foot - 27 * s); ctx.lineTo(cx, foot - 22 * s); ctx.lineTo(cx + 1.6 * s, foot - 27 * s); ctx.fill();
  // arms
  ctx.strokeStyle = shade(coat, -.15); ctx.lineWidth = 2.8 * s; ctx.lineCap = 'round';
  const arm = (x1: number, y1: number, x2: number, y2: number) => { ctx.beginPath(); ctx.moveTo(cx + x1 * s, foot - y1 * s); ctx.lineTo(cx + x2 * s, foot - y2 * s); ctx.stroke(); ctx.fillStyle = skin; ctx.beginPath(); ctx.arc(cx + x2 * s, foot - y2 * s, 1.5 * s, 0, 7); ctx.fill(); };
  arm(-6, 25, -8, 14);
  if (pose === 'wave') arm(6, 25, 10, 37); else if (pose === 'mouth') arm(6, 25, 3, 30); else if (pose === 'hold' || pose === 'board') arm(6, 25, 2, 19); else if (pose === 'case') arm(6, 25, 8, 14); else arm(6, 25, 8, 14);
  // head
  ctx.fillStyle = lit(skin); ctx.beginPath(); ctx.ellipse(cx, foot - 32 * s, 4.4 * s, 5 * s, 0, 0, 7); ctx.fill();
  ctx.fillStyle = hair; ctx.beginPath(); ctx.ellipse(cx, foot - 34.5 * s, 4.6 * s, 3 * s, 0, Math.PI, 0); ctx.fill();
}

export type PieceKind = 'candidate' | 'mate' | 'organizer' | 'surrogate' | 'pollster' | 'ads' | 'lawyer' | 'operative';
/** A painted miniature. Returns a canvas 44 × 64 (logical), its base centred at (22, 56). */
export function miniature(kind: PieceKind, col: string, seed = 0): HTMLCanvasElement {
  const key = `m:${kind}:${col}:${seed % 4}`; const hit = cache.get(key); if (hit) return hit;
  const [c, ctx] = canvas(44, 64); const cx = 22, foot = 55;
  base(ctx, cx, foot + 1, kind === 'candidate' ? 15 : 13, col);
  const skins = ['#e9c8a8', '#c9966f', '#8f5f3e', '#f0d4bc'], hairs = ['#2a1d14', '#4b3423', '#7a6a5a', '#1a1410'];
  const skin = skins[seed % 4], hair = hairs[(seed >> 2) % 4], s = kind === 'candidate' ? 1.12 : 1;
  if (kind === 'ads') {
    // a TV camera on a tripod
    ctx.strokeStyle = '#2a2018'; ctx.lineWidth = 1.6; ctx.beginPath(); ctx.moveTo(cx, foot - 18); ctx.lineTo(cx - 8, foot); ctx.moveTo(cx, foot - 18); ctx.lineTo(cx + 8, foot); ctx.moveTo(cx, foot - 18); ctx.lineTo(cx + 1, foot + 1); ctx.stroke();
    const g = ctx.createLinearGradient(cx - 9, 0, cx + 9, 0); g.addColorStop(0, shade(col, .2)); g.addColorStop(1, shade(col, -.4)); ctx.fillStyle = g; ctx.fillRect(cx - 9, foot - 30, 16, 12);
    ctx.fillStyle = '#18120d'; ctx.fillRect(cx + 7, foot - 27, 6, 6); ctx.fillStyle = '#6aa6c0'; ctx.beginPath(); ctx.arc(cx + 13, foot - 24, 2.6, 0, 7); ctx.fill();
    ctx.fillStyle = '#c83a2c'; ctx.beginPath(); ctx.arc(cx - 6, foot - 27.5, 1.1, 0, 7); ctx.fill();
  } else {
    const pose = kind === 'candidate' ? 'wave' : kind === 'mate' ? 'wave' : kind === 'surrogate' ? 'mouth' : kind === 'organizer' ? 'hold' : kind === 'pollster' ? 'board' : kind === 'lawyer' ? 'case' : 'still';
    figure(ctx, cx, foot, s, col, skin, hair, pose as any);
    if (kind === 'organizer') { ctx.fillStyle = '#c9a56b'; ctx.fillRect(cx - 1, foot - 24, 7, 9); ctx.fillStyle = '#f4efe2'; ctx.fillRect(cx, foot - 23, 5, 7); ctx.fillStyle = '#6d6d6d'; ctx.fillRect(cx + 1.5, foot - 25, 2, 1.5); }
    if (kind === 'surrogate') { ctx.fillStyle = '#e6e0d0'; ctx.beginPath(); ctx.moveTo(cx + 3, foot - 31); ctx.lineTo(cx + 12, foot - 35); ctx.lineTo(cx + 12, foot - 26); ctx.closePath(); ctx.fill(); ctx.strokeStyle = '#2a2018'; ctx.lineWidth = .6; ctx.stroke(); }
    if (kind === 'pollster') { ctx.fillStyle = '#f4efe2'; ctx.fillRect(cx - 2, foot - 27, 11, 9); ctx.strokeStyle = '#2a2018'; ctx.lineWidth = .5; ctx.strokeRect(cx - 2, foot - 27, 11, 9);
      ctx.fillStyle = shade(col, -.2); for (let k = 0; k < 3; k++) { const hh = [3, 6, 4.5][k]; ctx.fillRect(cx + k * 3, foot - 19 - hh, 2, hh); } }
    if (kind === 'lawyer') { ctx.fillStyle = '#5a3a22'; ctx.fillRect(cx + 5, foot - 15, 8, 6); ctx.fillStyle = '#c9a56b'; ctx.fillRect(cx + 8, foot - 16.5, 2, 1.5); }
    if (kind === 'candidate' || kind === 'mate') { ctx.fillStyle = kind === 'candidate' ? '#f2c94c' : '#e8e0d0'; ctx.beginPath(); const sx = cx - 3, sy = foot - 23 * s;
      for (let k = 0; k < 10; k++) { const a = -Math.PI / 2 + k * Math.PI / 5, r = k % 2 ? 1.2 : 2.8; k ? ctx.lineTo(sx + Math.cos(a) * r, sy + Math.sin(a) * r) : ctx.moveTo(sx + Math.cos(a) * r, sy + Math.sin(a) * r); } ctx.closePath(); ctx.fill(); }
    if (kind === 'operative') { ctx.strokeStyle = '#d4af37'; ctx.lineWidth = 1.2; ctx.beginPath(); ctx.arc(cx, foot - 34, 5.4, Math.PI * .95, Math.PI * 2.05); ctx.stroke();
      for (let k = 0; k < 6; k++) { const a = Math.PI + k * Math.PI / 5; ctx.fillStyle = '#d4af37'; ctx.beginPath(); ctx.ellipse(cx + Math.cos(a) * 5.4, foot - 34 + Math.sin(a) * 5.4, 1.4, .7, a, 0, 7); ctx.fill(); } }
  }
  cache.set(key, c); return c;
}
export function pieceFor(line: string): PieceKind {
  return (({ candidate: 'candidate', mate: 'mate', organizer: 'organizer', surrogate: 'surrogate', pollster: 'pollster', ads: 'ads', lawyer: 'lawyer', operative: 'operative' } as Record<string, PieceKind>)[line]) || 'organizer';
}

/** A wooden HQ: a painted house with a flag. 40 × 44, the door at (20, 38). */
export function hqHouse(col: string, capital: boolean): HTMLCanvasElement {
  const key = `h:${col}:${capital}`; const hit = cache.get(key); if (hit) return hit;
  const [c, ctx] = canvas(40, 44);
  ctx.fillStyle = 'rgba(0,0,0,.4)'; ctx.beginPath(); ctx.moveTo(8, 40); ctx.lineTo(34, 40); ctx.lineTo(40, 44); ctx.lineTo(14, 44); ctx.fill();
  ctx.fillStyle = shade(col, -.35); ctx.beginPath(); ctx.moveTo(28, 22); ctx.lineTo(36, 18); ctx.lineTo(36, 36); ctx.lineTo(28, 40); ctx.fill();
  const g = ctx.createLinearGradient(6, 0, 28, 0); g.addColorStop(0, shade(col, .2)); g.addColorStop(1, shade(col, -.1)); ctx.fillStyle = g; ctx.fillRect(6, 22, 22, 18);
  ctx.fillStyle = '#7a2e22'; ctx.beginPath(); ctx.moveTo(4, 23); ctx.lineTo(17, 10); ctx.lineTo(30, 23); ctx.fill();
  ctx.fillStyle = '#5a2018'; ctx.beginPath(); ctx.moveTo(17, 10); ctx.lineTo(26, 6); ctx.lineTo(38, 18); ctx.lineTo(30, 23); ctx.fill();
  ctx.fillStyle = '#f3ecdc'; ctx.fillRect(14, 30, 6, 10); ctx.fillRect(8, 26, 4, 4); ctx.fillRect(22, 26, 4, 4);
  ctx.strokeStyle = 'rgba(20,12,6,.6)'; ctx.lineWidth = .6; ctx.strokeRect(6, 22, 22, 18);
  ctx.strokeStyle = '#2a2018'; ctx.lineWidth = .9; ctx.beginPath(); ctx.moveTo(17, 10); ctx.lineTo(17, capital ? -0 : 2); ctx.stroke();
  ctx.fillStyle = capital ? '#f2c94c' : col; ctx.beginPath(); ctx.moveTo(17, capital ? 0 : 2); ctx.lineTo(25, capital ? 2.5 : 4.5); ctx.lineTo(17, capital ? 5 : 7); ctx.fill();
  cache.set(key, c); return c;
}
/** A cardboard counter for a field office. 22 × 22. */
export function counter(col: string): HTMLCanvasElement {
  const key = `c:${col}`; const hit = cache.get(key); if (hit) return hit;
  const [c, ctx] = canvas(22, 22);
  ctx.fillStyle = 'rgba(0,0,0,.4)'; ctx.fillRect(3, 3, 18, 18);
  ctx.fillStyle = '#b39a6c'; ctx.fillRect(1, 1, 18, 18); ctx.fillStyle = '#d8c39a'; ctx.fillRect(1, 1, 18, 1.2); ctx.fillRect(1, 1, 1.2, 18);
  ctx.fillStyle = col; ctx.fillRect(3, 3, 14, 14);
  ctx.fillStyle = '#f3ecdc'; ctx.beginPath(); ctx.moveTo(6, 10); ctx.lineTo(10, 6); ctx.lineTo(14, 10); ctx.lineTo(14, 14.5); ctx.lineTo(6, 14.5); ctx.closePath(); ctx.fill();
  ctx.fillStyle = col; ctx.fillRect(9, 11.5, 2, 3);
  cache.set(key, c); return c;
}
export function ring(col: string): HTMLCanvasElement {
  const key = `r:${col}`; const hit = cache.get(key); if (hit) return hit;
  const [c, ctx] = canvas(40, 18); ctx.strokeStyle = col; ctx.lineWidth = 2.2; ctx.beginPath(); ctx.ellipse(20, 9, 17, 6.5, 0, 0, 7); ctx.stroke();
  ctx.strokeStyle = hexA('#ffffff', .6); ctx.lineWidth = .8; ctx.beginPath(); ctx.ellipse(20, 9, 17, 6.5, 0, 0, 7); ctx.stroke(); cache.set(key, c); return c;
}
