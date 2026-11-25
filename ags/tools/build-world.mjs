// Build-time: the hex world for the board -> web/gen/map.json.
// Input: us-atlas 3.0.1 states-albers-10m (US Census cartographic boundaries, already projected Albers USA with Alaska and
// Hawaii inset, 975 x 610; ISC licence). Terrain comes from real longitude and latitude, recovered by inverting the projection.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const here = path.dirname(fileURLToPath(import.meta.url));
const web = path.resolve(here, '../web');
const topo = JSON.parse(fs.readFileSync(path.join(web, 'node_modules/us-atlas/states-albers-10m.json'), 'utf8'));
const STATES = JSON.parse(fs.readFileSync(path.resolve(here, '../data/states.json'), 'utf8'));
const CODE = Object.fromEntries(STATES.map(s => [s.name, s.code]));
const rad = Math.PI / 180;
function conic(phi0, phi1) {
  const sy0 = Math.sin(phi0 * rad), n = (sy0 + Math.sin(phi1 * rad)) / 2, c = 1 + sy0 * (2 * n - sy0), r0 = Math.sqrt(c) / n;
  return {
    f(l, p) { const r = Math.sqrt(c - 2 * n * Math.sin(p)) / n, x = l * n; return [r * Math.sin(x), r0 - r * Math.cos(x)]; },
    inv(x, y) { const r0y = r0 - y; let l = Math.atan2(x, Math.abs(r0y)) * Math.sign(r0y); if (r0y * n < 0) l -= Math.PI * Math.sign(x) * Math.sign(r0y); return [l / n, Math.asin(Math.max(-1, Math.min(1, (c - (x * x + r0y * r0y) * n * n) / (2 * n))))]; },
  };
}
function proj({ parallels, rotate, center, scale, translate }) {
  const raw = conic(...parallels), [cx, cy] = raw.f(center[0] * rad, center[1] * rad);
  const dx = translate[0] - scale * cx, dy = translate[1] + scale * cy;
  return {
    fwd(lon, lat) { let l = (lon + rotate) * rad; if (l > Math.PI) l -= 2 * Math.PI; if (l < -Math.PI) l += 2 * Math.PI; const [x, y] = raw.f(l, lat * rad); return [dx + scale * x, dy - scale * y]; },
    inv(X, Y) { const [l, p] = raw.inv((X - dx) / scale, (dy - Y) / scale); return [l / rad - rotate, p / rad]; },
  };
}
const K = 1300, T = [487.5, 305];
const L48 = proj({ parallels: [29.5, 45.5], rotate: 96, center: [-0.6, 38.7], scale: K, translate: T });
const AK = proj({ parallels: [55, 65], rotate: 154, center: [-2, 58.5], scale: K * 0.35, translate: [T[0] - 0.307 * K, T[1] + 0.201 * K] });
const HI = proj({ parallels: [8, 18], rotate: 157, center: [-3, 19.9], scale: K, translate: [T[0] - 0.205 * K, T[1] + 0.212 * K] });
const project = (lon, lat, st) => (st === 'AK' ? AK : st === 'HI' ? HI : L48).fwd(lon, lat);

// TopoJSON: delta-encoded, quantized arcs; a ring is a list of arc indexes (~i = arc i reversed)
function decode(t) {
  const [sx, sy] = t.transform.scale, [tx, ty] = t.transform.translate;
  const arcs = t.arcs.map(a => { let x = 0, y = 0; return a.map(([dx, dy]) => { x += dx; y += dy; return [x * sx + tx, y * sy + ty]; }); });
  const ring = idx => { const pts = []; for (const i of idx) { const a = i >= 0 ? arcs[i] : arcs[~i].slice().reverse(); a.forEach((p, k) => { if (k > 0 || pts.length === 0) pts.push(p); }); } return pts; };
  return t.objects.states.geometries.map(g => ({ code: CODE[g.properties.name], rings: (g.type === 'Polygon' ? [g.arcs] : g.arcs).flatMap(p => p.map(ring)) })).filter(s => s.code);
}
const polys = decode(topo).map(s => {
  let minx = 1e9, miny = 1e9, maxx = -1e9, maxy = -1e9, big = null, bigA = 0;
  for (const r of s.rings) { let a = 0; for (let i = 0, j = r.length - 1; i < r.length; j = i++) a += (r[j][0] + r[i][0]) * (r[j][1] - r[i][1]); a = Math.abs(a / 2); if (a > bigA) { bigA = a; big = r; }
    for (const [x, y] of r) { minx = Math.min(minx, x); miny = Math.min(miny, y); maxx = Math.max(maxx, x); maxy = Math.max(maxy, y); } }
  let cx = 0, cy = 0; for (const [x, y] of big) { cx += x; cy += y; }
  return { c: s.code, rings: s.rings, box: [minx, miny, maxx, maxy], l: [cx / big.length, cy / big.length] };
});
function inRing(x, y, r) { let ins = false; for (let i = 0, j = r.length - 1; i < r.length; j = i++) { const [xi, yi] = r[i], [xj, yj] = r[j]; if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) ins = !ins; } return ins; }
function stateAt(x, y) { for (const p of polys) { if (x < p.box[0] || x > p.box[2] || y < p.box[1] || y > p.box[3]) continue; let ins = false; for (const r of p.rings) if (inRing(x, y, r)) ins = !ins; if (ins) return p.c; } return null; }

// check the projection against the shapes' own label points
for (const [c, lon, lat] of [['KS', -98.4, 38.5], ['PA', -77.6, 40.9], ['CA', -119.6, 37.2], ['FL', -81.6, 28.1], ['AK', -152, 64], ['HI', -155.5, 19.6]]) {
  const [x, y] = project(lon, lat, c); console.log('check', c, x.toFixed(0), y.toFixed(0), 'state at point:', stateAt(x, y));
}

// the hex grid (pointy-top, axial q,r)
const R = 10.5, W = Math.sqrt(3) * R, Hh = 1.5 * R;
const hexes = [], index = {};
for (let r = -1; r * Hh < 610 + R; r++) {
  for (let col = -1; col * W < 975 + W; col++) {
    const q = col - Math.floor(r / 2), x = W * (q + r / 2) + W / 2, y = Hh * r + R;
    const pts = [[x, y]]; for (let k = 0; k < 6; k++) { const a = rad * (60 * k - 30); pts.push([x + R * .72 * Math.cos(a), y + R * .72 * Math.sin(a)]); }
    const votes = {}; let land = 0; for (const [px, py] of pts) { const s = stateAt(px, py); if (s) { land++; votes[s] = (votes[s] || 0) + (px === x && py === y ? 1.5 : 1); } }
    const st = land >= 3 || (land && stateAt(x, y)) ? Object.entries(votes).sort((a, b) => b[1] - a[1])[0][0] : null;
    const h = { q, r, x: +x.toFixed(2), y: +y.toFixed(2), s: st };
    index[q + ',' + r] = hexes.length; hexes.push(h);
  }
}
const DIRS = [[1, 0], [1, -1], [0, -1], [-1, 0], [-1, 1], [0, 1]];
const nb = h => DIRS.map(([dq, dr]) => index[(h.q + dq) + ',' + (h.r + dr)]).filter(i => i != null).map(i => hexes[i]);
// every state gets at least one hex (DC, RI, DE...): the hex nearest its label point
for (const p of polys) {
  const mine = hexes.filter(h => h.s === p.c).length;
  if (mine >= (p.c === 'DC' ? 1 : 1)) continue;
  let best = null, bd = 1e9; for (const h of hexes) { const d = (h.x - p.l[0]) ** 2 + (h.y - p.l[1]) ** 2; if (d < bd && (h.s || p.c === 'RI')) { bd = d; best = h; } }
  console.log('forced', p.c, 'onto a hex of', best.s); best.s = p.c;
}

// noise
function hash(a, b) { let h = (a * 374761393 + b * 668265263) | 0; h = (h ^ (h >>> 13)) * 1274126177 | 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967296; }
function vnoise(x, y) { const xi = Math.floor(x), yi = Math.floor(y), xf = x - xi, yf = y - yi, s = t => t * t * (3 - 2 * t);
  const a = hash(xi, yi), b = hash(xi + 1, yi), c = hash(xi, yi + 1), d = hash(xi + 1, yi + 1); return a + (b - a) * s(xf) + (c - a) * s(yf) + (a - b - c + d) * s(xf) * s(yf); }
const fbm = (x, y) => vnoise(x, y) * .6 + vnoise(x * 2.3 + 7, y * 2.3 + 3) * .3 + vnoise(x * 5 + 11, y * 5 + 5) * .1;

// terrain from longitude and latitude
function distToSeg(px, py, ax, ay, bx, by) { const dx = bx - ax, dy = by - ay, t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy))); return Math.hypot(px - ax - t * dx, py - ay - t * dy); }
function terrain(h) {
  const n = fbm(h.x / 38, h.y / 38), n2 = hash(h.q * 7 + 3, h.r * 13 + 1);
  if (h.s === 'AK') return n > .62 ? ['snow', 'mountain'] : n > .45 ? ['tundra', n2 > .5 ? 'hills' : ''] : ['tundra', n2 > .4 ? 'forest' : ''];
  if (h.s === 'HI') return ['grass', n2 > .45 ? 'jungle' : 'hills'];
  const [lon, lat] = L48.inv(h.x, h.y);
  h.lon = +lon.toFixed(2); h.lat = +lat.toFixed(2);
  const rockies = lon > -114.5 && lon < -104.6 && lat > 35.5 && lat < 49.2 && !(lon > -110 && lat > 41.2 && lat < 43.8 && lon < -106.5);
  const sierra = lon > -121.2 && lon < -118 && lat > 35.3 && lat < 40.5 && (lon + 121.2) * 1.2 + (lat - 35.3) * .55 > 1.4 && (lon + 121.2) * 1.2 - (lat - 40) < 4.2;
  const cascades = lon > -122.4 && lon < -120.8 && lat > 41.8 && lat < 49.2;
  const appal = distToSeg(lon, lat, -85.6, 34.2, -77.8, 39.6) < 1.25 || distToSeg(lon, lat, -77.8, 39.6, -73.6, 44.4) < 1.0;
  if ((rockies && n > .38) || (sierra && n > .3) || (cascades && n > .42)) return [lon < -110 && lat < 37.5 ? 'desert' : 'plains', 'mountain'];
  if (rockies || sierra || cascades) return [lat > 44 ? 'grass' : 'plains', n2 > .4 ? 'forest' : 'hills'];
  if (lon < -109.5 && lat < 37.2) return ['desert', n2 > .82 ? 'hills' : ''];
  if (lon < -112 && lat < 42.3 && lon > -120.2) return ['desert', n > .55 ? 'hills' : ''];
  if (lon < -101.5 && lat < 33.2) return ['desert', n2 > .85 ? 'hills' : ''];
  if (lon < -121.4 && lat > 41.5) return ['grass', 'forest'];
  if (lon < -116.5 && lat > 42) return ['plains', n > .5 ? 'hills' : ''];
  if (lon < -117.5) return ['plains', n2 > .7 ? 'hills' : ''];
  if (lat < 27.3 && lon > -82.5) return ['grass', 'marsh'];
  if (lat < 30.35 && lon > -94 && lon < -88.8) return ['grass', 'marsh'];
  if (appal) return ['grass', n2 > .45 ? 'forest' : 'hills'];
  if (lon > -104.8 && lon < -97.2) return ['plains', n2 > .9 ? 'hills' : ''];
  if (lat > 45.4 && lon > -95.5 && lon < -83.5) return ['grass', n2 > .3 ? 'forest' : ''];
  if (lat > 41.5 && lon > -80.5) return ['grass', n2 > .35 ? 'forest' : n2 > .25 ? 'hills' : ''];
  if (lat < 36.4 && lon > -97.2) return ['grass', n2 > .5 ? 'forest' : ''];
  if (lon > -97.2 && lon < -80.5 && lat > 36.4) return ['grass', n2 > .86 ? 'forest' : ''];
  return ['grass', n2 > .7 ? 'forest' : ''];
}
for (const h of hexes) { if (h.s) { const [t, f] = terrain(h); h.t = t; if (f) h.f = f; } }
// water: lakes (the Great Lakes are holes in the land), coast (next to land) and ocean
for (const h of hexes) {
  if (h.s) continue;
  const [lon, lat] = L48.inv(h.x, h.y);
  const nearLand = nb(h).some(o => o.s);
  h.t = lon > -92.6 && lon < -75.8 && lat > 41.2 && lat < 49.3 && h.x > 520 && h.x < 900 && h.y < 260 ? 'lake' : nearLand ? 'coast' : 'ocean';
}

// rivers (longitude, latitude)
const RIVERS = {
  Mississippi: [[-95.2, 47.2], [-93.2, 44.9], [-91.3, 43.8], [-91.1, 42.5], [-90.6, 41.5], [-91.4, 40.3], [-90.2, 38.6], [-89.4, 37], [-89.6, 36], [-90.1, 35.1], [-91.1, 33.4], [-91.4, 31.6], [-91.2, 30.5], [-90.1, 29.95], [-89.3, 29.2]],
  Missouri: [[-111.5, 47.5], [-109, 47.8], [-104, 48], [-101, 47.3], [-100.4, 46], [-100.3, 44.4], [-98.5, 43], [-96.4, 42.5], [-95.9, 41.3], [-95, 39.8], [-94.6, 39.1], [-92.2, 38.6], [-90.2, 38.8]],
  Ohio: [[-80, 40.4], [-81.6, 39.3], [-82.6, 38.6], [-84.5, 39.1], [-85.8, 38.2], [-87.6, 37.9], [-88.6, 37.1], [-89.1, 37]],
  Colorado: [[-106, 40.1], [-108.5, 39.1], [-110, 38.2], [-111.5, 36.9], [-112.5, 36.2], [-114, 36.1], [-114.6, 35], [-114.6, 33], [-114.8, 32.5]],
  'Rio Grande': [[-106.6, 37.8], [-106.7, 35.1], [-106.9, 33], [-106.5, 31.8], [-104.5, 29.7], [-103.2, 29], [-101.4, 29.8], [-99.5, 27.5], [-97.4, 25.95]],
  Columbia: [[-117.5, 49], [-119.3, 46.2], [-121.2, 45.6], [-122.8, 45.6], [-124, 46.25]],
  Arkansas: [[-106.2, 39.2], [-104.6, 38.3], [-101, 37.9], [-97.3, 37.7], [-95.4, 35.8], [-92.3, 34.7], [-91.1, 33.9]],
  Tennessee: [[-83.9, 35.95], [-85.3, 35.05], [-86.6, 34.6], [-88.1, 34.9], [-88.2, 36.6], [-88.3, 37.05]],
  Hudson: [[-73.9, 43.3], [-73.7, 42.6], [-73.95, 41.3], [-74, 40.7]],
  Snake: [[-110.7, 44], [-112.4, 43.3], [-114.4, 42.6], [-116.9, 43.7], [-117, 46.4], [-119, 46.2]],
  'Red River': [[-101.5, 34.6], [-98.5, 34.1], [-96, 33.8], [-94, 33.5], [-93.5, 32], [-91.7, 31]],
};
const rivers = Object.entries(RIVERS).map(([name, pts]) => ({ name, pts: pts.map(([lo, la]) => L48.fwd(lo, la).map(v => +v.toFixed(1))) }));

// cities: [name, state, lat, lon, metro population in millions]
const CITIES = [
  ['New York', 'NY', 40.71, -74.01, 19.5], ['Los Angeles', 'CA', 34.05, -118.24, 13], ['Chicago', 'IL', 41.88, -87.63, 9.4], ['Dallas', 'TX', 32.78, -96.8, 7.9],
  ['Houston', 'TX', 29.76, -95.37, 7.3], ['Washington', 'DC', 38.9, -77.04, 1.5], ['Philadelphia', 'PA', 39.95, -75.17, 6.2], ['Miami', 'FL', 25.76, -80.19, 6.1],
  ['Atlanta', 'GA', 33.75, -84.39, 6.2], ['Boston', 'MA', 42.36, -71.06, 4.9], ['Phoenix', 'AZ', 33.45, -112.07, 5], ['San Francisco', 'CA', 37.77, -122.42, 4.6],
  ['Detroit', 'MI', 42.33, -83.05, 4.3], ['Seattle', 'WA', 47.61, -122.33, 4], ['Minneapolis', 'MN', 44.98, -93.27, 3.7], ['San Diego', 'CA', 32.72, -117.16, 3.3],
  ['Tampa', 'FL', 27.95, -82.46, 3.3], ['Denver', 'CO', 39.74, -104.99, 3], ['St. Louis', 'MO', 38.63, -90.2, 2.8], ['Baltimore', 'MD', 39.29, -76.61, 2.8],
  ['Charlotte', 'NC', 35.23, -80.84, 2.7], ['Orlando', 'FL', 28.54, -81.38, 2.7], ['San Antonio', 'TX', 29.42, -98.49, 2.6], ['Portland', 'OR', 45.52, -122.68, 2.5],
  ['Pittsburgh', 'PA', 40.44, -79.99, 2.4], ['Las Vegas', 'NV', 36.17, -115.14, 2.3], ['Austin', 'TX', 30.27, -97.74, 2.4], ['Cincinnati', 'OH', 39.1, -84.51, 2.3],
  ['Kansas City', 'MO', 39.1, -94.58, 2.2], ['Columbus', 'OH', 39.96, -83, 2.1], ['Indianapolis', 'IN', 39.77, -86.16, 2.1], ['Cleveland', 'OH', 41.5, -81.69, 2.1],
  ['Nashville', 'TN', 36.16, -86.78, 2], ['Milwaukee', 'WI', 43.04, -87.91, 1.6], ['Raleigh', 'NC', 35.78, -78.64, 1.5], ['Salt Lake City', 'UT', 40.76, -111.89, 1.3],
  ['New Orleans', 'LA', 29.95, -90.07, 1.3], ['Oklahoma City', 'OK', 35.47, -97.52, 1.4], ['Louisville', 'KY', 38.25, -85.76, 1.3], ['Richmond', 'VA', 37.54, -77.44, 1.3],
  ['Virginia Beach', 'VA', 36.85, -75.98, 1.8], ['Jacksonville', 'FL', 30.33, -81.66, 1.6], ['Memphis', 'TN', 35.15, -90.05, 1.3], ['Birmingham', 'AL', 33.52, -86.8, 1.1],
  ['Albuquerque', 'NM', 35.08, -106.65, .9], ['Tucson', 'AZ', 32.22, -110.97, 1], ['El Paso', 'TX', 31.76, -106.49, .9], ['Omaha', 'NE', 41.26, -95.93, 1],
  ['Des Moines', 'IA', 41.59, -93.62, .7], ['Boise', 'ID', 43.62, -116.2, .8], ['Little Rock', 'AR', 34.75, -92.29, .75], ['Jackson', 'MS', 32.3, -90.18, .6],
  ['Charleston', 'SC', 32.78, -79.93, .8], ['Columbia', 'SC', 34, -81.03, .8], ['Manchester', 'NH', 42.99, -71.46, .45], ['Portland', 'ME', 43.66, -70.26, .55],
  ['Burlington', 'VT', 44.48, -73.21, .22], ['Providence', 'RI', 41.82, -71.41, 1.6], ['Hartford', 'CT', 41.76, -72.67, 1.2], ['Wilmington', 'DE', 39.74, -75.55, .7],
  ['Newark', 'NJ', 40.74, -74.17, 4], ['Charleston', 'WV', 38.35, -81.63, .25], ['Billings', 'MT', 45.78, -108.5, .2], ['Fargo', 'ND', 46.88, -96.79, .25],
  ['Sioux Falls', 'SD', 43.55, -96.73, .28], ['Cheyenne', 'WY', 41.14, -104.82, .1], ['Wichita', 'KS', 37.69, -97.34, .65], ['Anchorage', 'AK', 61.22, -149.9, .4],
  ['Honolulu', 'HI', 21.31, -157.86, 1], ['Grand Rapids', 'MI', 42.96, -85.67, 1.1], ['Madison', 'WI', 43.07, -89.4, .7], ['Spokane', 'WA', 47.66, -117.43, .6],
  ['Reno', 'NV', 39.53, -119.81, .5], ['Sacramento', 'CA', 38.58, -121.49, 2.4], ['Buffalo', 'NY', 42.89, -78.88, 1.1], ['Albany', 'NY', 42.65, -73.75, .9],
];
const cities = [];
for (const [name, st, lat, lon, pop] of CITIES) {
  const [x, y] = project(lon, lat, st);
  let best = null, bd = 1e9;
  for (const h of hexes) { if (h.s !== st || h.city != null) continue; const d = (h.x - x) ** 2 + (h.y - y) ** 2; if (d < bd) { bd = d; best = h; } }
  if (!best) { console.log('no hex for', name, st); continue; }
  best.city = cities.length; best.t = best.t === 'desert' ? 'desert' : best.t; delete best.f;
  cities.push({ name, s: st, pop, h: index[best.q + ',' + best.r], x: +x.toFixed(1), y: +y.toFixed(1) });
}
// population of each hex: land 1, cities and their ring by metro size
for (const h of hexes) if (h.s) h.p = 1;
for (const c of cities) { const h = hexes[c.h]; h.p = +(1 + c.pop * 3).toFixed(1); for (const o of nb(h)) if (o.s === h.s) o.p = +((o.p || 1) + c.pop * .45).toFixed(1); }

const counts = {}; for (const h of hexes) if (h.s) counts[h.s] = (counts[h.s] || 0) + 1;
console.log('hexes', hexes.length, 'land', hexes.filter(h => h.s).length, 'cities', cities.length);
console.log(Object.entries(counts).sort((a, b) => a[1] - b[1]).slice(0, 10).map(e => e.join(':')).join(' '));
const ter = {}; for (const h of hexes) { const k = h.t + (h.f ? '/' + h.f : ''); ter[k] = (ter[k] || 0) + 1; } console.log(ter);
const outPath = path.join(web, 'gen/map.json'); fs.mkdirSync(path.dirname(outPath), { recursive: true });
const simplify = r => { const o = []; let last = null; for (const [x, y] of r) { if (last && Math.hypot(x - last[0], y - last[1]) < .9) continue; o.push([+x.toFixed(1), +y.toFixed(1)]); last = [x, y]; } return o; };
fs.writeFileSync(outPath, JSON.stringify({ R, w: 975, h: 610, hexes: hexes.map(h => { const o = { q: h.q, r: h.r, x: h.x, y: h.y, t: h.t }; if (h.s) { o.s = h.s; o.p = h.p; } if (h.f) o.f = h.f; if (h.city != null) o.c = h.city; return o; }),
  cities, rivers, outlines: polys.map(p => ({ c: p.c, rings: p.rings.map(simplify).filter(r => r.length > 3), l: p.l.map(v => +v.toFixed(1)) })) }));
console.log('wrote', path.relative(process.cwd(), outPath), (fs.statSync(outPath).size / 1024).toFixed(0), 'KB');

// ---------------------------------------------------------------------------------------------------------------
// The world board: world-atlas 2.0.2 countries-110m (Natural Earth, public domain), equirectangular, 1000 × 500.
{
  const w = JSON.parse(fs.readFileSync(path.join(web, 'node_modules/world-atlas/countries-110m.json'), 'utf8'));
  const [sx, sy] = w.transform.scale, [tx, ty] = w.transform.translate;
  const arcs = w.arcs.map(a => { let x = 0, y = 0; return a.map(([dx, dy]) => { x += dx; y += dy; return [x * sx + tx, y * sy + ty]; }); });
  const ring = idx => { const pts = []; for (const i of idx) { const a = i >= 0 ? arcs[i] : arcs[~i].slice().reverse(); a.forEach((p, k) => { if (k > 0 || pts.length === 0) pts.push(p); }); } return pts; };
  const X = lon => (lon + 180) / 360 * 1000, Y = lat => (90 - lat) / 180 * 500;
  const EU15 = ['France', 'Germany', 'Italy', 'Spain', 'Portugal', 'Netherlands', 'Belgium', 'Luxembourg', 'Austria', 'Denmark', 'Sweden', 'Finland', 'Ireland', 'Greece'];
  const out = [];
  for (const g of w.objects.countries.geometries) {
    const polys = g.type === 'Polygon' ? [g.arcs] : g.type === 'MultiPolygon' ? g.arcs : [];
    let d = '';
    for (const p of polys) for (const r of p.map(ring)) {
      let last = null;
      r.forEach(([lon, lat], k) => { const x = X(lon), y = Y(lat); d += (k === 0 || (last && Math.abs(x - last) > 500) ? 'M' : 'L') + x.toFixed(1) + ' ' + y.toFixed(1); last = x; });
      d += 'Z';
    }
    const n = g.properties.name; if (d && n !== 'Antarctica') out.push({ n, d, eu: EU15.includes(n) });
  }
  fs.writeFileSync(path.join(web, 'gen/worldmap.json'), JSON.stringify(out));
  console.log('wrote gen/worldmap.json', out.length, 'countries', (fs.statSync(path.join(web, 'gen/worldmap.json')).size / 1024).toFixed(0), 'KB');
}
