// The table: a walnut tabletop, the paper map lying on it, washes of party colour inked onto the hexes, and painted
// miniatures standing on top. PixiJS draws it; the map is tilted slightly away from you, and the pieces stand upright.
import { Application, Container, Graphics, Sprite, Text, Texture } from 'pixi.js';
// eval-free shader and uniform code, so the board also draws on pages whose security policy forbids eval
import 'pixi.js/unsafe-eval';
import { MW, MH, RES, SQ3, DIRS, EDGE, MapData, corners, hexPath, hexA, paintMap, paintTable, shade, mix } from '../art/paper';
import { miniature, pieceFor, hqHouse, counter, ring } from '../art/pieces';

const TILT = 0.9;
export type Lens = 'lean' | 'approval' | 'results';
export interface BoardState {
  lean: number[]; you: string; them: string; units: any[]; hqs: { hex: number; side: number; capital: boolean; city: number }[]; offices: { hex: number; side: number }[];
  lens: Lens; approval?: number; results?: Record<string, string | null>; patterns: boolean; whHex?: number;
}
type Hooks = { hex?: (i: number, e: PointerEvent) => void; unit?: (id: number) => void; hover?: (i: number | null, e?: PointerEvent) => void; key?: (k: string) => void };

export class Board {
  app = new Application();
  map!: MapData; R = 10.5; HW = SQ3 * 10.5;
  world = new Container(); table!: Sprite; paper!: Sprite; terr!: Sprite; hi = new Graphics(); pieces = new Container(); floats = new Container(); labels = new Container();
  terrCanvas!: HTMLCanvasElement; terrTex!: Texture;
  view = { x: 0, y: 0, z: 1, minZ: .5 };
  hooks: Hooks = {};
  byKey = new Map<string, number>();
  state: BoardState | null = null;
  sprites = new Map<string, Container>();
  hl: { reach?: number[]; area?: Map<number, number>; path?: number[]; hover?: number | null; sel?: number | null; targets?: number[]; cursor?: number | null } = {};
  el!: HTMLElement;
  private tweens: { t0: number; ms: number; f: (k: number) => void; done?: () => void }[] = [];

  async init(el: HTMLElement, map: MapData) {
    this.el = el; this.map = map; this.R = map.R; this.HW = SQ3 * map.R;
    map.hexes.forEach((h, i) => this.byKey.set(h.q + ',' + h.r, i));
    await this.app.init({ resizeTo: el, autoStart: false, background: '#1c120a', antialias: true, resolution: Math.min(2, devicePixelRatio || 1), autoDensity: true });
    el.appendChild(this.app.canvas); this.app.canvas.setAttribute('aria-hidden', 'true');
    this.table = new Sprite(Texture.from(paintTable(1024, 768))); this.app.stage.addChild(this.table);
    // the sheet's shadow on the table, then the sheet
    const sh = document.createElement('canvas'); sh.width = MW / 2 + 40; sh.height = MH / 2 + 40; const sx = sh.getContext('2d')!; sx.shadowColor = 'rgba(0,0,0,.75)'; sx.shadowBlur = 16; sx.shadowOffsetX = 6; sx.shadowOffsetY = 8; sx.fillStyle = '#000'; sx.fillRect(20, 20, MW / 2, MH / 2);
    const shadow = new Sprite(Texture.from(sh)); shadow.scale.set(2); shadow.position.set(-40, -40); this.world.addChild(shadow);
    this.paper = new Sprite(Texture.from(paintMap(map))); this.paper.scale.set(1 / RES); this.world.addChild(this.paper);
    this.terrCanvas = document.createElement('canvas'); this.terrCanvas.width = Math.round(MW * 2); this.terrCanvas.height = Math.round(MH * 2);
    this.terrTex = Texture.from(this.terrCanvas); this.terr = new Sprite(this.terrTex); this.terr.scale.set(1 / 2); this.world.addChild(this.terr);
    this.world.addChild(this.hi); this.world.addChild(this.labels);
    this.app.stage.addChild(this.world); this.app.stage.addChild(this.pieces); this.app.stage.addChild(this.floats);
    // the board draws a frame only when something on it changes: no idle redraws, which spares batteries and slow GPUs
    this.app.ticker.stop();
    this.bindInput(); this.fit(); new ResizeObserver(() => { this.app.resize(); this.layoutTable(); this.clamp(); this.apply(); }).observe(el);
    this.makeLabels(); this.apply(); document.fonts?.ready.then(() => this.apply());
  }
  private raf = 0;
  /** Ask for one frame; tweens and floating numbers keep asking until they finish. */
  invalidate() { if (!this.raf) this.raf = requestAnimationFrame(() => this.frame()); }
  private frame() { this.raf = 0; this.tick(); this.app.render(); if (this.tweens.length || this.floats.children.length) this.invalidate(); }

  /* ---------------- geometry ---------------- */
  hexAt(mx: number, my: number): number | null {
    const R = this.R, rf = (my - R) / (1.5 * R), qf = (mx - this.HW / 2) / this.HW - rf / 2;
    let x = qf, z = rf, y = -x - z, rx = Math.round(x), ry = Math.round(y), rz = Math.round(z);
    const dx = Math.abs(rx - x), dy = Math.abs(ry - y), dz = Math.abs(rz - z); if (dx > dy && dx > dz) rx = -ry - rz; else if (dy <= dz) rz = -rx - ry;
    const i = this.byKey.get(rx + ',' + rz); return i == null ? null : i;
  }
  toMap(cx: number, cy: number): [number, number] { const r = this.app.canvas.getBoundingClientRect(); return [(cx - r.left - this.view.x) / this.view.z, (cy - r.top - this.view.y) / (this.view.z * TILT)]; }
  toScreen(mx: number, my: number): [number, number] { return [this.view.x + mx * this.view.z, this.view.y + my * this.view.z * TILT]; }
  nb(i: number, k: number): number | null { const h = this.map.hexes[i]; const j = this.byKey.get((h.q + DIRS[k][0]) + ',' + (h.r + DIRS[k][1])); return j == null ? null : j; }

  /* ---------------- camera ---------------- */
  layoutTable() { const r = this.el.getBoundingClientRect(); this.table.width = r.width; this.table.height = r.height; }
  apply() {
    this.world.position.set(this.view.x, this.view.y); this.world.scale.set(this.view.z, this.view.z * TILT);
    this.layoutPieces(); this.layoutFloats();
    this.el.classList.toggle('far', this.view.z < 1.9);
    const ls = Math.min(1.2, 1.6 / this.view.z); for (const t of this.labels.children) t.scale.set(ls, ls / TILT);
    this.declutter();
    this.invalidate();
  }
  /** Show city names biggest first, hiding any that would overprint one already shown at this zoom. */
  private declutter() {
    const shown: { l: number; r: number; t: number; b: number }[] = [];
    for (const t of this.labels.children as any[]) {
      if (this.view.z < (t.minZ || 0)) { t.visible = false; continue; }
      const w = t.width, h = t.height * TILT, box = { l: t.position.x - w / 2 - 1, r: t.position.x + w / 2 + 1, t: t.position.y * TILT, b: t.position.y * TILT + h };
      t.visible = !shown.some(o => box.l < o.r && box.r > o.l && box.t < o.b && box.b > o.t); if (t.visible) shown.push(box);
    }
  }
  clamp() {
    const r = this.el.getBoundingClientRect(), w = MW * this.view.z, h = MH * this.view.z * TILT, m = 60;
    this.view.x = Math.min(m, Math.max(r.width - w - m, this.view.x)); this.view.y = Math.min(m, Math.max(r.height - h - m, this.view.y));
    if (w < r.width) this.view.x = (r.width - w) / 2; if (h < r.height) this.view.y = (r.height - h) / 2;
  }
  zoomAt(cx: number, cy: number, z: number) { const nz = Math.max(this.view.minZ, Math.min(5, z)); this.view.x = cx - (cx - this.view.x) * nz / this.view.z; this.view.y = cy - (cy - this.view.y) * nz / this.view.z; this.view.z = nz; this.clamp(); this.apply(); }
  fit() { const r = this.el.getBoundingClientRect(); this.view.minZ = Math.min(r.width / MW, r.height / (MH * TILT)) * .94; this.view.z = this.view.minZ; this.clamp(); this.layoutTable(); this.apply(); }
  centerOn(mx: number, my: number, z = this.view.z, ms = 450) {
    const r = this.el.getBoundingClientRect(); const to = { z, x: r.width / 2 - mx * z, y: r.height / 2 - my * z * TILT }, from = { ...this.view };
    if (ms <= 0 || document.documentElement.classList.contains('reduce-motion')) { Object.assign(this.view, to); this.clamp(); this.apply(); return; }
    this.tween(ms, k => { const e = k < .5 ? 2 * k * k : 1 - Math.pow(-2 * k + 2, 2) / 2; this.view.x = from.x + (to.x - from.x) * e; this.view.y = from.y + (to.y - from.y) * e; this.view.z = from.z + (to.z - from.z) * e; this.apply(); }, () => { this.clamp(); this.apply(); });
  }
  centerHex(i: number, z?: number, ms?: number) { const h = this.map.hexes[i]; this.centerOn(h.x, h.y, z ?? Math.max(this.view.z, 2.2), ms); }

  /* ---------------- input ---------------- */
  bindInput() {
    const cv = this.app.canvas; let down: any = null, panned = false; cv.style.touchAction = 'none';
    // two fingers pinch to zoom about their midpoint; a pinch never counts as a click
    const pts = new Map<number, { x: number; y: number }>(); let pinch: { d: number; z: number } | null = null;
    const spread = () => { const [a, b] = [...pts.values()]; return { d: Math.hypot(a.x - b.x, a.y - b.y), x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 }; };
    const lift = (e: PointerEvent) => { pts.delete(e.pointerId); if (pts.size < 2) pinch = null; };
    cv.addEventListener('pointercancel', e => { lift(e); down = null; });
    cv.addEventListener('pointerdown', e => { if (e.button > 0) return; pts.set(e.pointerId, { x: e.clientX, y: e.clientY }); cv.setPointerCapture(e.pointerId);
      if (pts.size === 2) { pinch = { d: Math.max(1, spread().d), z: this.view.z }; down = null; panned = true; return; }
      down = { x: e.clientX, y: e.clientY, vx: this.view.x, vy: this.view.y }; panned = false; });
    cv.addEventListener('pointermove', e => {
      if (pts.has(e.pointerId)) pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
      if (pinch && pts.size === 2) { const s = spread(), r = cv.getBoundingClientRect(); this.zoomAt(s.x - r.left, s.y - r.top, pinch.z * s.d / pinch.d); return; }
      if (down) { const dx = e.clientX - down.x, dy = e.clientY - down.y; if (!panned && Math.hypot(dx, dy) > 6) panned = true; if (panned) { this.view.x = down.vx + dx; this.view.y = down.vy + dy; this.clamp(); this.apply(); } return; }
      const [mx, my] = this.toMap(e.clientX, e.clientY); const i = this.hexAt(mx, my); this.hooks.hover?.(i != null && this.map.hexes[i].s ? i : null, e);
    });
    cv.addEventListener('pointerup', e => { lift(e); if (!down) return; const was = panned; down = null; if (was) return;
      const u = this.unitAt(e.clientX, e.clientY); if (u != null) return this.hooks.unit?.(u);
      const [mx, my] = this.toMap(e.clientX, e.clientY); const i = this.hexAt(mx, my); if (i != null) this.hooks.hex?.(i, e); });
    cv.addEventListener('pointerleave', () => this.hooks.hover?.(null));
    cv.addEventListener('wheel', e => { e.preventDefault(); const r = cv.getBoundingClientRect(); this.zoomAt(e.clientX - r.left, e.clientY - r.top, this.view.z * (e.deltaY < 0 ? 1.15 : 1 / 1.15)); }, { passive: false });
  }
  /** Which of your units' miniatures is under the pointer (they stand taller than their hex). */
  unitAt(cx: number, cy: number): number | null {
    const r = this.app.canvas.getBoundingClientRect(), x = cx - r.left, y = cy - r.top;
    let best: number | null = null;
    for (const [k, c] of this.sprites) { if (!k.startsWith('u:') || !c.visible) continue; const b = c.getBounds(); if (x >= b.x + b.width * .2 && x <= b.x + b.width * .8 && y >= b.y && y <= b.y + b.height * .92) best = +k.slice(2); }
    return best;
  }

  /* ---------------- drawing ---------------- */
  set(s: BoardState) { this.state = s; this.drawTerritory(); this.syncPieces(); this.drawHighlights(); }
  owner(i: number): number { const s = this.state!; if (!this.map.hexes[i]?.s) return 0; const l = s.lean[i]; return l >= 2 ? 1 : l <= -2 ? -1 : 0; }
  drawTerritory() {
    const s = this.state!, c = this.terrCanvas, ctx = c.getContext('2d')!, k = 2, R = this.R;
    ctx.setTransform(1, 0, 0, 1, 0, 0); ctx.clearRect(0, 0, c.width, c.height); ctx.setTransform(k, 0, 0, k, 0, 0);
    if (s.lens === 'results' && s.results) {
      for (const h of this.map.hexes) { if (!h.s) continue; const col = s.results[h.s]; ctx.fillStyle = col ? hexA(col, .78) : 'rgba(43,33,24,.28)'; hexPath(ctx, h.x, h.y, R + .4); ctx.fill(); }
      this.terrTex.source.update(); this.invalidate(); return;
    }
    for (const h of this.map.hexes) {
      if (!h.s) continue; const i = this.byKey.get(h.q + ',' + h.r)!; let l = s.lean[i];
      if (s.lens === 'approval' && s.approval != null) { const a = s.approval - 50 + l * .6; ctx.fillStyle = a >= 0 ? hexA('#4f7d3a', Math.min(.5, .08 + a / 40)) : hexA('#8a4b2a', Math.min(.5, .08 - a / 40)); hexPath(ctx, h.x, h.y, R + .3); ctx.fill(); continue; }
      if (Math.abs(l) < 1) continue; const col = l > 0 ? s.you : s.them, a = Math.min(.5, .08 + Math.abs(l) / 28);
      ctx.fillStyle = hexA(col, a); hexPath(ctx, h.x, h.y, R + .3); ctx.fill();
      if (s.patterns && Math.abs(l) >= 2) { ctx.save(); hexPath(ctx, h.x, h.y, R); ctx.clip(); ctx.strokeStyle = hexA(shade(col, -.45), .5); ctx.lineWidth = .5; ctx.beginPath();
        for (let d = -R * 2; d < R * 2; d += 3) { if (l > 0) { ctx.moveTo(h.x + d - R, h.y - R); ctx.lineTo(h.x + d + R, h.y + R); } else { ctx.moveTo(h.x + d - R, h.y + R); ctx.lineTo(h.x + d + R, h.y - R); } } ctx.stroke(); ctx.restore(); }
    }
    if (s.lens === 'lean') for (const h of this.map.hexes) {
      if (!h.s) continue; const i = this.byKey.get(h.q + ',' + h.r)!; const o = this.owner(i); if (!o) continue; const col = shade(o > 0 ? s.you : s.them, -.25), cs = corners(h.x, h.y, R * .9);
      for (let e = 0; e < 6; e++) { const n = this.nb(i, e); if (n != null && this.owner(n) === o) continue; const [a, b] = EDGE[e]; ctx.strokeStyle = col; ctx.lineWidth = 1.5; ctx.lineCap = 'round'; ctx.beginPath(); ctx.moveTo(cs[a][0], cs[a][1]); ctx.lineTo(cs[b][0], cs[b][1]); ctx.stroke(); }
    }
    this.terrTex.source.update(); this.invalidate();
  }
  drawHighlights() {
    const g = this.hi, R = this.R; g.clear();
    const poly = (i: number, r: number) => corners(this.map.hexes[i].x, this.map.hexes[i].y, r).flat();
    for (const i of this.hl.reach || []) g.poly(poly(i, R - .6)).fill({ color: 0xfff6dc, alpha: .28 }).stroke({ width: .6, color: 0x3a2a18, alpha: .55 });
    for (const i of this.hl.targets || []) g.poly(poly(i, R - .6)).fill({ color: 0xf2c94c, alpha: .32 }).stroke({ width: .8, color: 0x7a5a10, alpha: .8 });
    if (this.hl.area) for (const [i, s] of this.hl.area) g.poly(poly(i, R - .4)).fill({ color: 0xd4762c, alpha: .14 + s * .3 });
    if (this.hl.path && this.hl.path.length > 1) { const p = this.hl.path.map(i => [this.map.hexes[i].x, this.map.hexes[i].y]); g.moveTo(p[0][0], p[0][1]); for (const [x, y] of p.slice(1)) g.lineTo(x, y); g.stroke({ width: 1.6, color: 0x2b2118, alpha: .85 });
      const e = p[p.length - 1]; g.circle(e[0], e[1], 2.4).fill({ color: 0x2b2118 }); }
    if (this.hl.hover != null) g.poly(poly(this.hl.hover, R - .5)).stroke({ width: 1, color: 0x2b2118, alpha: .8 });
    if (this.hl.cursor != null) g.poly(poly(this.hl.cursor, R - .2)).stroke({ width: 2, color: 0xb8322a, alpha: .95 });
    if (this.hl.sel != null) g.poly(poly(this.hl.sel, R - .7)).stroke({ width: 1.8, color: 0xf2c94c, alpha: 1 });
    this.invalidate();
  }
  setHighlights(h: Partial<Board['hl']>) { Object.assign(this.hl, h); this.drawHighlights(); }

  /* ---------------- pieces ---------------- */
  private place(c: Container, mx: number, my: number, dx = 0) { const [x, y] = this.toScreen(mx, my); c.position.set(x + dx * this.view.z, y); const z = this.pieceScale(); c.scale.set(z, z); }
  pieceScale() { return Math.max(.5, Math.min(1.4, this.view.z * .34)); }
  syncPieces() {
    const s = this.state!; const want = new Set<string>();
    const mk = (key: string, build: () => Container) => { let c = this.sprites.get(key); if (!c) { c = build(); this.sprites.set(key, c); this.pieces.addChild(c); } want.add(key); return c; };
    for (const o of s.offices) { const c = mk(`o:${o.side}:${o.hex}`, () => { const ct = new Container(); const sp = new Sprite(Texture.from(counter(o.side ? s.them : s.you))); sp.anchor.set(.5); sp.scale.set(.5); ct.addChild(sp); return ct; }); (c as any).hex = o.hex; (c as any).dx = 7; }
    for (const q of s.hqs) { const c = mk(`h:${q.side}:${q.hex}`, () => { const ct = new Container(); const sp = new Sprite(Texture.from(hqHouse(q.side ? s.them : s.you, q.capital))); sp.anchor.set(.5, .86); sp.scale.set(.5); ct.addChild(sp); return ct; }); (c as any).hex = q.hex; (c as any).dx = -6; }
    const stacks = new Map<number, any[]>(); for (const u of s.units) { (stacks.get(u.hex) || stacks.set(u.hex, []).get(u.hex)!).push(u); }
    for (const [hex, us] of stacks) us.forEach((u, n) => {
      const col = u.side ? s.them : s.you, key = `u:${u.id}`;
      const c = mk(key, () => { const ct = new Container(); const rg = new Sprite(Texture.from(ring('#f2c94c'))); rg.anchor.set(.5); rg.scale.set(.5); rg.position.set(0, -1); rg.visible = false; (ct as any).ring = rg; ct.addChild(rg);
        const sp = new Sprite(Texture.from(miniature(pieceFor(u.line), col, u.id))); sp.anchor.set(.5, 57 / 64); sp.scale.set(.5); ct.addChild(sp); (ct as any).fig = sp;
        const badge = new Text({ text: '', style: { fontFamily: '"Alegreya Sans SC", sans-serif', fontSize: 11, fontWeight: '700', fill: '#2b2118', stroke: { color: '#efe6d0', width: 3 } }, resolution: 3 }); badge.anchor.set(.5); badge.position.set(9, -4); ct.addChild(badge); (ct as any).badge = badge; return ct; });
      const prevHex = (c as any).hex; (c as any).hex = hex; (c as any).dx = (n - (us.length - 1) / 2) * Math.min(9, 24 / us.length);
      (c as any).ring.visible = this.hl.sel === hex && (c as any).selId === u.id;
      (c as any).fig.alpha = u.side === 0 && (u.acted || u.skip) ? .72 : 1; (c as any).fig.tint = u.rattled ? 0x999999 : 0xffffff;
      (c as any).badge.text = u.side === 0 ? (u.standing === 'sleep' ? 'z' : u.standing ? '↻' : u.acted ? '✓' : String(Math.max(0, Math.round(u.mp)))) : '';
      if (prevHex != null && prevHex !== hex && (c as any).px != null) this.slide(c, prevHex, hex);
    });
    for (const [k, c] of this.sprites) if (!want.has(k)) { this.pieces.removeChild(c); c.destroy({ children: true }); this.sprites.delete(k); }
    this.layoutPieces();
  }
  markSelected(id: number | null) { for (const [k, c] of this.sprites) if (k.startsWith('u:')) { (c as any).selId = id; (c as any).ring.visible = +k.slice(2) === id; } this.invalidate(); }
  private slide(c: Container, from: number, to: number) {
    const a = this.map.hexes[from], b = this.map.hexes[to]; const far = Math.hypot(a.x - b.x, a.y - b.y) > 60; (c as any).anim = { x: a.x, y: a.y };
    this.tween(far ? 700 : 320, k => { const e = 1 - Math.pow(1 - k, 3); (c as any).anim = { x: a.x + (b.x - a.x) * e, y: a.y + (b.y - a.y) * e - (far ? Math.sin(Math.PI * k) * 40 : Math.sin(Math.PI * k) * 4) }; this.layoutPieces(); }, () => { (c as any).anim = null; this.layoutPieces(); });
  }
  layoutPieces() {
    for (const c of this.sprites.values()) { const h = this.map.hexes[(c as any).hex]; if (!h) continue; const p = (c as any).anim || h; this.place(c, p.x, p.y, (c as any).dx || 0); (c as any).px = 1; }
    this.pieces.children.sort((a, b) => a.position.y - b.position.y); this.invalidate();
  }
  makeLabels() {
    for (const c of [...this.map.cities].sort((a, b) => b.pop - a.pop)) {
      const t = new Text({ text: c.name.toUpperCase(), style: { fontFamily: '"Alegreya Sans SC", "Alegreya Sans", sans-serif', fontSize: 9, fontWeight: '700', letterSpacing: .6, fill: '#2b2118', stroke: { color: '#efe6d0', width: 3, join: 'round' } }, resolution: 4 });
      t.anchor.set(.5, 0); const h = this.map.hexes[c.h]; t.position.set(h.x, h.y + 4.5); (t as any).minZ = c.pop >= 4 ? 0 : c.pop >= 1.5 ? 1.7 : 2.6; this.labels.addChild(t);
    }
  }

  /* ---------------- floating numbers and tweens ---------------- */
  floater(hex: number, text: string, col: string) {
    const t = new Text({ text, style: { fontFamily: '"IM Fell English SC", Georgia, serif', fontSize: 17, fill: col, stroke: { color: '#1c120a', width: 4, join: 'round' } }, resolution: 2 });
    t.anchor.set(.5); (t as any).hex = hex; (t as any).t0 = performance.now(); this.floats.addChild(t); this.layoutFloats(); this.invalidate();
  }
  layoutFloats() { const now = performance.now(); for (const t of [...this.floats.children]) { const k = (now - (t as any).t0) / 1900; if (k >= 1) { this.floats.removeChild(t); t.destroy(); continue; }
    const h = this.map.hexes[(t as any).hex]; const [x, y] = this.toScreen(h.x, h.y); t.position.set(x, y - 18 - k * 34); t.alpha = k < .12 ? k / .12 : k > .75 ? (1 - k) / .25 : 1; } }
  tween(ms: number, f: (k: number) => void, done?: () => void) { this.tweens.push({ t0: performance.now(), ms, f, done }); this.invalidate(); }
  private tick() {
    const now = performance.now();
    for (const tw of [...this.tweens]) { const k = Math.min(1, (now - tw.t0) / tw.ms); tw.f(k); if (k >= 1) { this.tweens.splice(this.tweens.indexOf(tw), 1); tw.done?.(); } }
    if (this.floats.children.length) this.layoutFloats();
  }
  /** A small image of the whole board for the minimap. */
  thumbnail(w: number): HTMLCanvasElement { const c = document.createElement('canvas'); c.width = w; c.height = Math.round(w * MH / MW); const x = c.getContext('2d')!; x.drawImage((this.paper.texture.source as any).resource, 0, 0, c.width, c.height); x.drawImage(this.terrCanvas, 0, 0, c.width, c.height); return c; }
}
export { mix };
