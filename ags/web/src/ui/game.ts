// The game screen: the board, the player board along the top, the selected unit's index card, dossiers for cities and
// the White House, the cards the game deals, and the End Turn seal. Everything the player does goes to the simulation
// as a command; its effects come back and are played on the table.
import { Sim, View, Fx } from '../sim';
import { Board, Lens } from '../board/board';
import { MapData, mix } from '../art/paper';
import { miniature, pieceFor } from '../art/pieces';
import { portrait, scene } from '../art/print';
import { $, $$, el, esc, sgn, icon, dealCard, resultCard, newsCard, custom, forecastCard, talentCard, rollDice, toast, showTip, hideTip, announce, sleep } from './kit';
import { openBinder, closeBinder } from './screens';
import { Settings, sound } from './settings';

const CLOSING: [string, string[]][] = [['7:00 PM', ['GA', 'IN', 'KY', 'SC', 'VA', 'VT']], ['7:30 PM', ['NC', 'OH', 'WV']], ['8:00 PM', ['AL', 'CT', 'DE', 'DC', 'FL', 'IL', 'ME', 'MD', 'MA', 'MS', 'MO', 'NH', 'NJ', 'OK', 'PA', 'RI', 'TN']],
  ['8:30 PM', ['AR']], ['9:00 PM', ['AZ', 'CO', 'KS', 'LA', 'MI', 'MN', 'NE', 'NM', 'NY', 'ND', 'SD', 'TX', 'WI', 'WY']], ['10:00 PM', ['IA', 'MT', 'NV', 'UT']], ['11:00 PM', ['CA', 'HI', 'ID', 'OR', 'WA']], ['1:00 AM', ['AK']]];
const ACT_ICON: Record<string, string> = { stump: 'megaphone', rally: 'flag', canvass: 'door', office: 'office', hq: 'house', poll: 'chart', ads: 'tv', attack_ad: 'fist', contest: 'fist', plane: 'plane', charter: 'plane', great: 'laurel', skip: 'skip' };
// actions a unit can repeat every turn on standing orders (the simulation's list)
const REPEATABLE = ['stump', 'rally', 'canvass', 'poll', 'ads'];
const ACT_NAME: Record<string, string> = { stump: 'Stump speech', rally: 'Rally', canvass: 'Canvass', office: 'Field office', hq: 'Open an HQ', poll: 'Poll', ads: 'Air ads · $3', attack_ad: 'Attack ad · $4', contest: 'Contest', plane: 'Campaign plane', charter: 'Charter · $2', great: 'Great move', skip: 'Skip' };
export const SAVE_KEY = 'ags-autosave', SLOTS_KEY = 'ags-saves';
export const store = {
  get(k: string, d: any) { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch { return d; } },
  set(k: string, v: any) { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* storage unavailable */ } },
};

export class GameUI {
  v!: View; fac: Record<string, any> = {};
  sel: number | null = null; reach: Map<number, [number, number]> | null = null; mode: string | null = null; targets: number[] = []; targetUnits: number[] = [];
  lens: Lens = 'lean'; cursor: number | null = null; busy = false; resultsMap: Record<string, string | null> | null = null;
  constructor(public root: HTMLElement, public sim: Sim, public board: Board, public map: MapData, public data: any, public onQuit: () => void) {}

  /* ---------------- setup ---------------- */
  build() {
    this.root.innerHTML = '';
    const node = el(`<main class="game"><div id="board" tabindex="0" aria-label="The board. Arrow keys move the cursor, Enter chooses, N selects the next unit."></div><div class="lamp"></div>
      <header class="strip" aria-label="Your resources"></header>
      <button class="research card paper" type="button" data-open="ideas"></button>
      <button class="tally card paper" type="button" data-open="victory" aria-label="The electoral vote projection"></button>
      <div class="notes" aria-label="Notifications"></div>
      <div class="zoom"><button type="button" data-z="in" aria-label="Zoom in">${icon('plus')}</button><button type="button" data-z="out" aria-label="Zoom out">${icon('minus')}</button><button type="button" data-z="fit" aria-label="See the whole map">${icon('globe')}</button></div>
      <div class="lens" aria-label="Map lens"><button type="button" data-lens="lean" class="on" aria-label="Lean: who leads where">${icon('layers')}</button><button type="button" data-lens="approval" aria-label="Approval of the President">${icon('capitol')}</button></div>
      <div class="unitcard card paper" hidden></div>
      <button class="minimap card paper" type="button" aria-label="Minimap: click to move there"><canvas width="226" height="142"></canvas><i class="frame"></i></button>
      <button class="seal" type="button"><b>End turn</b><span></span></button><button class="anyway" type="button" hidden>End the turn anyway</button></main>`);
    this.root.append(node);
    const holder = $('#board', node); holder.append(this.board.el); this.board.el.style.position = 'absolute'; this.board.el.style.inset = '0';
    window.dispatchEvent(new Event('resize'));
    this.board.hooks = { hex: (i, e) => this.onHex(i, e), unit: id => this.onUnit(id), hover: (i, e) => this.onHover(i, e) };
    holder.addEventListener('keydown', e => this.onKey(e));
    node.addEventListener('click', e => {
      const t = e.target as HTMLElement, o = t.closest('[data-open]') as HTMLElement | null, z = t.closest('[data-z]') as HTMLElement | null, l = t.closest('[data-lens]') as HTMLElement | null;
      if (o) return this.screen(o.dataset.open!);
      if (z) { const r = this.board.el.getBoundingClientRect(); if (z.dataset.z === 'fit') this.board.fit(); else this.board.zoomAt(r.width / 2, r.height / 2, this.board.view.z * (z.dataset.z === 'in' ? 1.35 : 1 / 1.35)); return; }
      if (l) { this.lens = l.dataset.lens as Lens; $$('[data-lens]').forEach(b => b.classList.toggle('on', b === l)); this.paint(); return; }
      if (t.closest('.minimap')) { const c = $('.minimap canvas'), r = c.getBoundingClientRect(); this.board.centerOn((e.clientX - r.left) / r.width * 975, (e.clientY - r.top) / r.height * 610); return; }
      if (t.closest('.seal')) return this.onSeal();
      if (t.closest('.anyway')) return this.endTurn();
    });
    this.data.factions.forEach((f: any) => this.fac[f.id] = f);
  }
  async start() { this.build(); this.refresh(); const cap = this.v.you.hqs[0]; if (cap) this.board.centerHex(cap.hex, 2.3, 0); await this.runPending(); }
  refresh() { this.v = this.sim.view; this.renderAll(); }
  get you() { return this.v.you.leader.state ? (this.v as any).colours?.you : ''; }
  col(side: number) { return side === 0 ? Settings.you : Settings.them; }

  /* ---------------- rendering ---------------- */
  renderAll() { this.renderStrip(); this.renderTally(); this.renderResearch(); this.renderNotes(); this.renderUnit(); this.renderSeal(); this.paint(); this.renderMinimap(); }
  paint() {
    const v = this.v; const off = [...v.you.offices.map((h: number) => ({ hex: h, side: 0 })), ...v.rival.offices.map((h: number) => ({ hex: h, side: 1 }))];
    const hqs = [...v.you.hqs.map((q: any) => ({ hex: q.hex, side: 0, capital: q.capital, city: q.city })), ...v.rival.hqs.map((q: any) => ({ hex: q.hex, side: 1, capital: q.capital, city: q.city }))];
    if (v.office && v.office.holder === 0 && !hqs.some(q => q.hex === v.office.wh)) hqs.push({ hex: v.office.wh, side: 0, capital: true, city: -1 });
    this.board.set({ lean: v.lean, you: Settings.you, them: Settings.them, units: v.units, hqs, offices: off, lens: this.resultsMap ? 'results' : this.lens, approval: v.office?.approval, results: this.resultsMap || undefined, patterns: Settings.patterns });
    this.board.markSelected(this.sel);
    const u = this.unitSel();
    this.board.setHighlights({ reach: this.mode ? [] : this.reach ? [...this.reach.keys()] : [], targets: this.targets, sel: u ? u.hex : null, cursor: this.cursor });
    if (this.lens === 'approval' && !v.office) { this.lens = 'lean'; }
    $$('[data-lens="approval"]').forEach(b => (b as HTMLButtonElement).disabled = !v.office);
  }
  renderStrip() {
    const v = this.v, y = v.you.yields, office = v.office_turn && v.office;
    const res = (k: string, ic: string, col: string, val: string, sub: string, tip: string) => `<button class="res" type="button" data-tip="${tip}"><span class="token" style="background:${col}">${icon(ic)}</span><span><b>${val}</b><small>${sub}</small></span></button>`;
    const turns = (need: number, have: number, per: number) => per > 0 ? Math.max(1, Math.ceil((need - have) / per)) : '—';
    const pip = (on: boolean) => on ? ' dot' : '';
    $('.strip').innerHTML = `<div class="cal"><b>${esc(v.title)}</b><span>${esc(v.label)} · turn ${v.turn + 1}</span></div>
      ${res('money', 'money', '#d9b44a', '$' + Math.round(v.you.money), `${y.net >= 0 ? '+' : ''}${y.net.toFixed(0)} a turn`, 'money')}
      ${res('inf', 'inf', '#9c7cc4', String(Math.round(v.you.influence)), `+${y.inf.toFixed(0)} · doctrine ${v.you.influence >= v.you.doctrine_cost ? 'ready' : 'in ' + turns(v.you.doctrine_cost, v.you.influence, y.inf)}`, 'inf')}
      ${res('ideas', 'ideas', '#5fa3bf', '+' + y.ideas.toFixed(0), v.you.idea_cur ? esc(v.you.idea_cur.name) : 'choose research', 'ideas')}
      ${office && v.office.holder === 0 ? res('mom', 'capitol', '#c96f4a', v.office.approval.toFixed(0) + '%', 'approval', 'approval') : res('mom', 'mom', '#c96f4a', String(Math.round(y.mom)), v.you.surge.left ? `surge · ${v.you.surge.left} turns` : `${Math.round(v.you.surge.pts)}/${v.you.surge.need} to a surge`, 'mom')}
      ${res('gp', 'laurel', '#b8913a', `${Math.round(v.you.gp.pts)}/${v.you.gp.need}`, 'great operative', 'gp')}
      <span class="space"></span>
      ${[['ideas', 'ideas', 'Ideas', !v.you.idea_cur], ['doctrines', 'inf', 'Doctrines', v.you.influence >= v.you.doctrine_cost], ['factions', 'people', 'Factions', false], ['platform', 'scroll', 'Platform', false], ['world', 'globe', 'World', false], ['victory', 'trophy', 'Victory', false], ['century', 'hourglass', 'Century', false]]
        .map(([k, ic, n, p]) => `<button class="tab" type="button" data-open="${k}"><span class="${pip(p as boolean)}">${icon(ic as string)}</span>${n}</button>`).join('')}
      <button class="tab" type="button" data-menu>${icon('menu')}Menu</button>`;
    $('[data-menu]', $('.strip')).onclick = () => this.menu();
    $$('[data-tip]', $('.strip')).forEach(b => { b.onmouseenter = () => { const r = b.getBoundingClientRect(); showTip(this.tip(b.dataset.tip!), r.left, r.bottom - 8); }; b.onmouseleave = hideTip; b.onfocus = b.onmouseenter as any; b.onblur = hideTip; });
  }
  tip(k: string) {
    const v = this.v, y = v.you.yields;
    if (k === 'money') return `<b>Money $${Math.round(v.you.money)}</b><table><tr><td>Income</td><td>+${y.money.toFixed(0)}</td></tr><tr><td>Unit upkeep</td><td>−${y.upkeep.toFixed(1)}</td></tr><tr><td>A turn</td><td>${sgn(y.net, 1)}</td></tr></table><div class="hint2">Buys units and buildings; pays for ads, flights and gifts.</div>`;
    if (k === 'inf') return `<b>Influence ${Math.round(v.you.influence)}</b>Spent on doctrines (next: ${v.you.doctrine_cost}) and on pledges (${v.you.plank_cost} each).`;
    if (k === 'ideas') return `<b>Ideas +${y.ideas.toFixed(0)} a turn</b>Your think tank's output, spent on research. Polls add more.`;
    if (k === 'mom') return `<b>Momentum ${Math.round(y.mom)}</b>Each turn momentum fills the surge track (${Math.round(v.you.surge.pts)}/${v.you.surge.need}). A surge makes every push 50% stronger for two turns.`;
    if (k === 'gp') return `<b>Great Operative ${Math.round(v.you.gp.pts)}/${v.you.gp.need}</b>When the track fills, a legend of the trade joins you.`;
    if (k === 'approval') return `<b>Approval ${v.office.approval.toFixed(0)}%</b>It speeds your laws through Congress, and decides the midterms.`;
    return '';
  }
  renderTally() {
    const v = this.v, t = $('.tally'); if (v.office_turn || this.resultsMap) { t.hidden = true; return; } t.hidden = false;
    const [y, th] = v.projection, left = v.election_in;
    t.innerHTML = `<div class="tally-nums"><span style="color:${Settings.you}">You ${y}</span><span>${left > 0 ? `Election in ${left} turn${left > 1 ? 's' : ''} · 270 to win` : '270 to win'}</span><span style="color:${Settings.them}">${th} ${esc(v.rival.leader.last)}</span></div>
      <div class="tally-bar"><i style="width:${y / 538 * 100}%;background:${Settings.you}"></i><i style="width:${th / 538 * 100}%;background:${Settings.them};margin-left:auto"></i></div>`;
  }
  renderResearch() {
    const c = this.v.you.idea_cur, p = c ? c.prog / c.cost : 0;
    $('.research').innerHTML = `<div class="dial"><svg class="r" viewBox="0 0 44 44"><circle cx="22" cy="22" r="19" fill="none" stroke="rgba(43,33,24,.2)" stroke-width="5"/><circle cx="22" cy="22" r="19" fill="none" stroke="#2b2118" stroke-width="5" stroke-dasharray="${(p * 119.4).toFixed(1)} 200"/></svg><span class="ic2">${icon(c ? c.icon : 'ideas')}</span></div>
      <div><b>${c ? esc(c.name) : 'Choose research'}</b><span>${c ? `${c.turns} turn${c.turns > 1 ? 's' : ''} · ${esc(c.text)}` : 'The think tank is idle.'}</span></div>`;
  }
  renderNotes() {
    const box = $('.notes');
    // newest first, one card per title: a recurring note ("Park's turn") replaces its older copy instead of piling up
    const seen = new Set<string>(), list = this.v.notes.filter((n: any) => !seen.has(n.title) && !!seen.add(n.title)).slice(0, 7);
    box.innerHTML = list.map((n: any, i: number) => `<button class="notecard ${n.tone}" type="button" data-note="${i}" style="--rot:${(i % 3 - 1) * .7}deg"><b>${esc(n.title)}</b><span>${esc(n.text)}</span></button>`).join('');
    box.onclick = e => { const b = (e.target as HTMLElement).closest('[data-note]') as HTMLElement | null; if (!b) return; const n = list[+b.dataset.note!]; b.remove(); this.go(n.go); };
  }
  go(g: any) {
    if (!g) return; if (g.hex != null) this.board.centerHex(g.hex); if (g.city != null) this.openCity(g.city); if (g.unit != null) this.select(g.unit);
    if (g.screen) this.screen(g.screen); if (g.report) this.report(); if (g.rival) this.rival();
  }
  renderMinimap() {
    const c = $('.minimap canvas') as HTMLCanvasElement, ctx = c.getContext('2d')!; ctx.clearRect(0, 0, c.width, c.height); ctx.drawImage(this.board.thumbnail(c.width), 0, 0);
    const v = this.board.view, r = this.board.el.getBoundingClientRect(), s = c.clientWidth / 975, f = $('.minimap .frame');
    f.style.left = (6 + Math.max(0, -v.x / v.z * s)) + 'px'; f.style.top = (6 + Math.max(0, -v.y / (v.z * .9) * s)) + 'px'; f.style.width = Math.min(c.clientWidth, r.width / v.z * s) + 'px'; f.style.height = Math.min(c.clientHeight, r.height / (v.z * .9) * s) + 'px';
  }
  needs(): [string, string, any?] | null {
    const v = this.v; if (v.over) return null;
    if (v.office && v.office.holder === 0 && !v.office.law && v.laws.some((l: any) => !l.passed)) return ['law', 'Choose a law'];
    if (!v.you.idea_cur && v.ideas.some((i: any) => i.state === 'open')) return ['research', 'Choose research'];
    const idle = v.you.hqs.find((q: any) => !q.prod); if (idle) return ['prod', 'Choose production', idle];
    if (v.you.influence >= v.you.doctrine_cost && v.doctrines.some((d: any) => d.policies.some((p: any) => p.state === 'open'))) return ['doct', 'Adopt a doctrine'];
    const u = v.units.find((x: any) => x.side === 0 && !x.acted && !x.skip); if (u) return ['unit', 'Unit needs orders', u];
    return null;
  }
  renderSeal() {
    const s = $('.seal') as HTMLButtonElement, any = $('.anyway'), n = this.needs(), next = this.v.turns[this.v.turn + 1];
    s.disabled = this.busy || !!this.v.pending; s.classList.toggle('block', !!n); s.hidden = !!this.resultsMap || this.v.over;
    s.innerHTML = this.busy ? `<b>The rival moves</b><span>…</span>` : n ? `<b>${n[1]}</b><span>Click to see it</span>` : `<b>End turn</b><span>${esc(this.v.title)} → ${esc(next ? next.title : 'the end')}</span>`;
    any.hidden = !n || this.busy || !!this.resultsMap;
  }
  async onSeal() {
    if (this.busy) return; const n = this.needs(); sound.click();
    if (!n) return this.endTurn();
    if (n[0] === 'research') this.screen('ideas'); else if (n[0] === 'prod') this.openCity(n[2].hex); else if (n[0] === 'doct') this.screen('doctrines');
    else if (n[0] === 'unit') this.select(n[2].id); else if (n[0] === 'law') this.openCity(this.v.office.wh);
  }

  /* ---------------- selection and the unit card ---------------- */
  unitSel() { return this.sel == null ? null : this.v.units.find((u: any) => u.id === this.sel) || null; }
  select(id: number) {
    const u = this.v.units.find((x: any) => x.id === id); if (!u || u.side !== 0) return;
    this.closeSide(); this.sel = id; this.mode = null; this.targets = []; this.targetUnits = [];
    this.reach = null; if (u.mp > 0) { const r = this.sim.reach(id); this.reach = new Map(r.hexes.map(([h, m, p]) => [h, [m, p]])); }
    const h = this.map.hexes[u.hex], v = this.board.view, rr = this.board.el.getBoundingClientRect(), [sx, sy] = this.board.toScreen(h.x, h.y);
    if (sx < 90 || sy < 120 || sx > rr.width - 280 || sy > rr.height - 220) this.board.centerHex(u.hex, Math.max(v.z, 2.2));
    sound.click(); this.renderUnit(); this.paint(); announce(`${u.kind}${u.name ? ', ' + u.name : ''}, at ${this.place(u.hex)}. ${Math.round(u.mp)} moves.`);
  }
  clearSel() { this.sel = null; this.reach = null; this.mode = null; this.targets = []; this.targetUnits = []; this.board.setHighlights({ path: [], area: undefined }); this.renderUnit(); this.paint(); }
  place(hex: number) { const h = this.map.hexes[hex]; if (h.c != null) return this.map.cities[h.c].name; const st = this.v.states.find((s: any) => s.code === h.s); return st ? st.name : 'the sea'; }
  renderUnit() {
    const card = $('.unitcard'), u = this.unitSel(); if (!u) { card.hidden = true; return; } card.hidden = false;
    card.innerHTML = `<div class="fig"></div><div><h3>${esc(u.name || u.kind)}</h3><div class="meta">${u.name ? esc(u.kind) + ' · ' : ''}${esc(this.place(u.hex))} · moves ${Math.max(0, Math.round(u.mp))}/${u.max} · ${u.standing === 'sleep' ? 'asleep' : u.standing ? 'on standing orders' : u.acted ? 'has acted' : 'ready'}${u.upkeep ? ` · upkeep $${u.upkeep}` : ''}</div></div>
      <p>${esc(u.role || '')}</p>
      <div class="acts">${u.actions.map((a: any) => `<button class="act ${this.mode === a.id ? 'on' : ''}" type="button" data-act="${a.id}" ${a.block ? 'disabled' : ''} title="${esc(a.block || a.hint)}">${icon(ACT_ICON[a.id] || 'star')}${ACT_NAME[a.id] || a.id}</button>`).join('')}</div>
      ${this.standingRow(u)}
      <div class="hint">${this.mode ? 'Choose a highlighted hex; Escape cancels.' : u.mp > 0 ? 'Click a lit hex to move. Hover an action to see what it does.' : 'No moves left; it can still act.'}</div>`;
    const fig = miniature(pieceFor(u.line), Settings.you, u.id); const cv = document.createElement('canvas'); cv.width = fig.width; cv.height = fig.height; cv.getContext('2d')!.drawImage(fig, 0, 0); $('.fig', card).append(cv);
    card.onmouseover = e => { const b = (e.target as HTMLElement).closest('[data-act]') as HTMLElement | null; if (!b || this.mode) return; const a = u.actions.find((x: any) => x.id === b.dataset.act);
      $('.hint', card).textContent = a.block || a.hint; this.board.setHighlights({ area: this.area(u, a) }); };
    card.onmouseout = e => { if ((e.target as HTMLElement).closest('[data-act]') && !this.mode) this.board.setHighlights({ area: undefined }); };
    card.onclick = async e => {
      const st = (e.target as HTMLElement).closest('[data-stand]') as HTMLElement | null;
      if (st) { const order = st.dataset.stand || undefined; this.board.setHighlights({ area: undefined });
        if (!(await this.cmd({ c: 'standing', unit: u.id, order }))) return;
        if (order) { announce(order === 'sleep' ? 'Asleep until you wake it.' : `Standing order: ${ACT_NAME[order]} every turn.`); this.nextUnit(); } else this.select(u.id);
        return; }
      const b = (e.target as HTMLElement).closest('[data-act]') as HTMLButtonElement | null; if (!b || b.disabled) return; const a = u.actions.find((x: any) => x.id === b.dataset.act); this.board.setHighlights({ area: undefined });
      if (a.id === 'plane' || a.id === 'charter') { this.mode = a.id; this.targets = a.id === 'plane' ? this.map.cities.map(c => c.h).filter(h => h !== u.hex) : this.v.you.hqs.map((q: any) => q.hex).filter((h: number) => h !== u.hex); this.renderUnit(); this.paint(); if (a.id === 'plane') this.board.fit(); return; }
      if (a.id === 'contest' || a.id === 'attack_ad') { this.mode = a.id; this.targetUnits = a.targets; this.targets = a.targets.map((t: number) => this.v.units.find((x: any) => x.id === t)?.hex).filter((h: any) => h != null); this.renderUnit(); this.paint(); return; }
      await this.cmd({ c: 'act', unit: u.id, action: a.id });
      const still = this.unitSel(); if (!still || still.acted || a.id === 'skip') { if (a.id === 'hq') this.clearSel(); else this.nextUnit(); } else this.select(still.id);
    };
  }
  /** Select the next unit still waiting for orders, or none. */
  nextUnit() { const nx = this.v.units.find((x: any) => x.side === 0 && !x.acted && !x.skip); if (nx) this.select(nx.id); else this.clearSel(); }
  /** Standing orders: repeat an action every turn, or sleep until woken (Civ's fortify and sleep). */
  standingRow(u: any): string {
    if (u.standing) return `<div class="standing on">${icon(u.standing === 'sleep' ? 'sleep' : 'repeat')}<span>${u.standing === 'sleep' ? 'Asleep until you wake it or move it.' : `${esc(ACT_NAME[u.standing].replace(/ ·.*/, ''))} every turn, until you move it or cancel.`}</span><button class="chip" type="button" data-stand="">${u.standing === 'sleep' ? 'Wake' : 'Cancel order'}</button></div>`;
    const rep = u.actions.filter((a: any) => REPEATABLE.includes(a.id));
    return `<div class="standing"><span>Every turn:</span>${rep.map((a: any) => `<button class="chip" type="button" data-stand="${a.id}" title="${esc(`${ACT_NAME[a.id]} now and at the start of every turn, until you move this unit or cancel. ${a.block || ''}`.trim())}">${icon('repeat')}${esc(ACT_NAME[a.id].replace(/ ·.*/, ''))}</button>`).join('')}<button class="chip" type="button" data-stand="sleep" title="Leave this unit be, turn after turn, until you wake it">${icon('sleep')}Sleep</button></div>`;
  }
  area(u: any, a: any) {
    const m = new Map<number, number>(); const rings: number[] = a.rings || []; const h0 = this.map.hexes[u.hex];
    if (rings.length) { const R = rings.length - 1; for (let i = 0; i < this.map.hexes.length; i++) { const h = this.map.hexes[i]; if (!h.s) continue; const dq = h.q - h0.q, dr = h.r - h0.r, d = (Math.abs(dq) + Math.abs(dr) + Math.abs(dq + dr)) / 2; if (d <= R) m.set(i, rings[d] / rings[0]); } }
    if (a.id === 'ads' || (a.id === 'great' && u.great === 'strategist')) this.map.hexes.forEach((h, i) => { if (h.s === h0.s) m.set(i, .6); });
    return m;
  }

  /* ---------------- the board's events ---------------- */
  async onHex(i: number, _e?: PointerEvent) {
    if (this.busy || this.v.pending || this.resultsMap) return; this.cursor = null;
    const u = this.unitSel(), h = this.map.hexes[i];
    if (this.mode && u) {
      if (!this.targets.includes(i)) { this.mode = null; this.targets = []; this.targetUnits = []; this.renderUnit(); this.paint(); return; }
      if (this.mode === 'plane' || this.mode === 'charter') { await this.cmd({ c: 'act', unit: u.id, action: this.mode, target: i }); this.mode = null; this.targets = []; this.select(u.id); return; }
      const t = this.targetUnits.find(id => this.v.units.find((x: any) => x.id === id)?.hex === i); if (t == null) return;
      await this.cmd({ c: 'act', unit: u.id, action: this.mode, target: t }); this.mode = null; this.targets = []; this.targetUnits = []; const nx = this.v.units.find((x: any) => x.side === 0 && !x.acted && !x.skip); if (nx) this.select(nx.id); else this.clearSel(); return;
    }
    if (u && this.reach && this.reach.has(i)) { await this.cmd({ c: 'move', unit: u.id, to: i }); this.select(u.id); return; }
    const mine = this.v.units.filter((x: any) => x.side === 0 && x.hex === i); if (mine.length) { const k = mine.findIndex((x: any) => x.id === this.sel); return this.select(mine[(k + 1) % mine.length].id); }
    if (h.c != null) return this.openCity(i);
    if (this.v.units.some((x: any) => x.side === 1 && x.hex === i)) return this.rival();
    this.clearSel(); this.closeSide();
  }
  onUnit(id: number) { const u = this.v.units.find((x: any) => x.id === id); if (!u) return; if (u.side === 1) { if (this.mode && this.targetUnits.includes(id)) return this.onHex(u.hex); return this.rival(); } if (this.unitSel() && this.reach?.has(u.hex) && this.unitSel().hex !== u.hex) return this.onHex(u.hex); this.select(id); }
  onHover(i: number | null, e?: PointerEvent) {
    const u = this.unitSel(); let path: number[] = [];
    if (u && this.reach && i != null && this.reach.has(i) && !this.mode) { path = [i]; let p = i; for (let k = 0; k < 40; k++) { const prev = this.reach.get(p)?.[1]; if (prev == null || prev === u.hex || prev > 1e8) break; path.unshift(prev); p = prev; } path.unshift(u.hex); }
    this.board.setHighlights({ hover: i, path });
    if (i != null && e) showTip(this.hexTip(i), e.clientX, e.clientY); else hideTip();
  }
  hexTip(i: number) {
    const h = this.map.hexes[i], st = this.v.states.find((s: any) => s.code === h.s), l = this.v.lean[i], terr = (this.data.terrain.terrain[h.t] || {}).name, feat = h.f ? this.data.terrain.features[h.f].name : null;
    const us = this.v.units.filter((x: any) => x.hex === i); const u = this.unitSel();
    let odds = ''; if (u && this.mode && (this.mode === 'contest' || this.mode === 'attack_ad')) { const t = this.targetUnits.find(id => this.v.units.find((x: any) => x.id === id)?.hex === i); if (t != null) { const o = this.sim.odds(u.id, t, this.mode); odds = `<tr><td><b style="display:inline;font-size:14px">Your odds</b></td><td><b style="display:inline;font-size:14px">${Math.round(o.p * 100)}%</b></td></tr>`; } }
    return `<b>${h.c != null ? esc(this.map.cities[h.c].name) + ', ' : ''}${esc(st?.name)}</b>${feat ? feat + ' · ' : ''}${terr}
      <table>${odds}<tr><td>This hex</td><td>${Math.abs(l) < .5 ? 'Even' : `${l > 0 ? 'You' : 'Them'} +${Math.abs(l).toFixed(1)}`}</td></tr>
      <tr><td>${esc(st?.name)} · ${st?.ev} EV</td><td>${st ? `${st.seen >= 0 ? 'You' : 'Them'} +${Math.abs(st.seen).toFixed(1)}${st.polled ? '' : ' (polls)'}` : ''}</td></tr>
      ${st && st.third ? `<tr><td>Third-party drag</td><td>${sgn(st.third, 1)}</td></tr>` : ''}
      ${us.map((x: any) => `<tr><td>${x.side ? esc(this.v.rival.leader.last) + "'s" : 'Your'} ${esc(x.kind)}</td><td>${x.rattled ? 'rattled' : ''}</td></tr>`).join('')}</table>
      ${st && !st.polled ? '<div class="hint2">The state figure is the public polls: send a Pollster for the truth.</div>' : ''}`;
  }
  onKey(e: KeyboardEvent) {
    const k = e.key; if (this.v.pending) return;
    const move = (dq: number, dr: number) => { const c = this.cursor ?? this.unitSel()?.hex ?? this.v.you.hqs[0]?.hex; const h = this.map.hexes[c]; const j = this.board.byKey.get((h.q + dq) + ',' + (h.r + dr)); if (j != null) { this.cursor = j; this.board.setHighlights({ cursor: j }); const hh = this.map.hexes[j]; const [sx, sy] = this.board.toScreen(hh.x, hh.y); const r = this.board.el.getBoundingClientRect(); if (sx < 60 || sy < 80 || sx > r.width - 60 || sy > r.height - 60) this.board.centerHex(j, undefined, 200);
      const st = this.v.states.find((s: any) => s.code === hh.s); announce(`${hh.c != null ? this.map.cities[hh.c].name + ', ' : ''}${st ? st.name : 'water'}. ${st ? `Lean ${this.v.lean[j] >= 0 ? 'you' : 'them'} ${Math.abs(this.v.lean[j]).toFixed(1)}.` : ''}`); } e.preventDefault(); };
    if (k === 'ArrowLeft') move(-1, 0); else if (k === 'ArrowRight') move(1, 0); else if (k === 'ArrowUp') move(0, -1); else if (k === 'ArrowDown') move(0, 1);
    else if (k === 'Enter' && this.cursor != null) { this.onHex(this.cursor); e.preventDefault(); }
    else if (k === 'n' || k === 'N') this.nextUnit();
    else if (k === 'Escape') { this.clearSel(); this.closeSide(); }
    else if (k === '+' || k === '=') { const r = this.board.el.getBoundingClientRect(); this.board.zoomAt(r.width / 2, r.height / 2, this.board.view.z * 1.3); }
    else if (k === '-') { const r = this.board.el.getBoundingClientRect(); this.board.zoomAt(r.width / 2, r.height / 2, this.board.view.z / 1.3); }
  }

  /* ---------------- commands and effects ---------------- */
  async cmd(c: object): Promise<boolean> {
    const r = this.sim.tryCmd(c); if (r.error) { toast('close', 'Not possible', r.error); sound.bad(); return false; }
    this.refresh(); await this.play(r.fx); await this.runPending(); this.renderAll(); return true;
  }
  async play(fx: Fx[]) {
    for (const f of fx) {
      if (f.kind === 'float') { const v = f.value as number | undefined; this.board.floater(f.hex, f.text || `${v! >= 0 ? '+' : '−'}${Math.abs(v!).toFixed(1)}`, f.text ? '#efe6d0' : v! >= 0 ? mix(Settings.you, '#ffffff', .35) : mix(Settings.them, '#ffffff', .35)); }
      else if (f.kind === 'dice') { sound.dice(); await rollDice(f, 'You', this.v.rival.leader.last); }
      else if (f.kind === 'achievement') { toast('trophy', ACH[f.id] || f.id, 'Achievement'); sound.good(); }
      else if (f.kind === 'toast') toast(f.icon || 'star', f.title, f.text);
      else if (f.kind === 'vote') { toast('capitol', `${f.law}: ${f.pass ? 'passes' : 'fails'}`, `${f.yes}–${100 - f.yes} in the Senate`); }
    }
  }
  async endTurn() {
    if (this.busy) return; this.busy = true; this.clearSel(); this.closeSide(); this.renderSeal(); sound.turn();
    const before = this.v.national, r = this.sim.tryCmd({ c: 'end_turn' }); this.refresh();
    if (r.error) { this.busy = false; toast('close', 'Not yet', r.error); this.renderAll(); return; }
    await this.play(r.fx); await sleep(500); this.busy = false;
    const after = this.v.national; announce(`New turn: ${this.v.title}. Your vote share ${after.toFixed(1)}%, ${sgn(after - before, 1)}.`);
    await this.runPending(); this.renderAll(); this.autosave();
    const nx = this.v.units.find((x: any) => x.side === 0 && !x.acted); if (nx && !this.v.pending && !$('.side') && !$('.binder')) this.select(nx.id);
  }
  autosave() { if (this.v.over || this.v.pending) return; const s = this.sim.save(); store.set(SAVE_KEY, s); }

  /* ---------------- the cards ---------------- */
  async runPending() {
    for (let guard = 0; guard < 30 && this.v.pending; guard++) {
      const p = this.v.pending; const spot = Settings.you, spot2 = Settings.them;
      if (p.focus != null) this.board.centerHex(p.focus, 2.2);
      let c: any = null;
      if (p.stage === 'result' && p.result) { await resultCard(p.result); c = { c: 'choose', n: 0 }; }
      else if (p.kind === 'intro') { await this.intro(p); c = { c: 'choose', n: 0 }; }
      else if (p.kind === 'headline') { sound.boom(); await newsCard(p, spot, spot2); c = { c: 'choose', n: 0 }; }
      else if (p.kind === 'mate') { await this.matePick(p); continue; }
      else if (p.kind === 'cabinet') { const picks = await this.cabinetPick(p); c = { c: 'choose', picks }; }
      else if (p.kind === 'gp') { const n = await dealCard({ art: 'convention', kicker: 'A Great Operative', title: 'A legend joins the campaign', body: 'One of the best in the business wants to work for you. Each kind does one great thing, then retires to write the book.',
        choices: [{ n: 0, label: 'Great Strategist', text: 'Masterstroke: +2.6 on every hex of the state it stands in.' }, { n: 1, label: 'Great Orator', text: 'Barnstorm: +3 for two rings around it, then +2 and +1.' }, { n: 2, label: 'Great Fundraiser', text: '$40, at once.' }] }, spot, spot2); c = { c: 'choose', n }; }
      else if (p.kind === 'election') { await this.night(p); c = { c: 'choose', n: 0 }; }
      else if (p.kind === 'end') { this.endScreen(p); return; }
      else { // a card, or the recount
        let extra = ''; if (p.forecast) extra += forecastCard(p.forecast, this.fac, Settings.ranges); if (p.challenger) extra += `<div style="max-width:320px">${talentCard(p.challenger, spot, this.fac)}</div>`;
        const n = await dealCard(p, spot, spot2, extra); c = { c: 'choose', n };
      }
      const r = this.sim.tryCmd(c!); if (r.error) { toast('close', 'Not possible', r.error); break; } this.refresh(); await this.play(r.fx);
    }
  }
  intro(p: any): Promise<void> {
    const L = this.v.you.leader, d = this.data.leaders.find((x: any) => x.id === L.id);
    return new Promise(res => {
      const node = el(`<div class="ecard card paper wide"><div class="card-in"><div style="display:grid;grid-template-columns:minmax(0,260px) minmax(0,1fr);gap:0">
        <div style="border-right:1.5px solid var(--ink);padding:16px;display:grid;place-items:center;background:var(--paper-2)">${portrait('lead-' + L.id + L.first, { spot: Settings.you, pres: L.pres, age: L.age, title: L.name })}</div>
        <div class="ebody"><div class="kicker">The Long Game · 2000</div><h2 style="font-size:40px">${esc(L.name)}</h2><div class="kicker">${esc(L.background)} · ${esc(this.v.you.party)} Party · ${esc(this.v.states.find((s: any) => s.code === L.state)?.name)}</div>
        <p class="story">${esc(p.body)}</p>
        <div class="uniq"><div><span class="sc">Unique ability</span><h4>${esc(d.ability.name)}</h4><p>${esc(d.ability.text)}</p></div><div><span class="sc">Unique unit</span><h4>${esc(d.unit.name)}</h4><p>${esc(d.unit.text)}</p></div></div>
        <p class="note">Your rival is ${esc(this.v.rival.leader.name)} of the ${esc(this.v.rival.party)} Party, "${esc(this.v.rival.persona)}". Move your pieces on the hexes, act with them, choose what your HQ builds and what your think tank studies, then press the red seal.</p>
        <div class="efoot"><span class="type muted">Seed ${this.v.seed}</span><button class="btn red" type="button">Begin</button></div></div></div></div></div>`);
      const m = custom(node, 'The beginning'); sound.boom(); $('.btn', node).onclick = () => { m.close(); res(); };
    });
  }
  matePick(p: any): Promise<void> {
    return new Promise(res => {
      let chosen: number | null = null;
      const draw = () => this.sim.view.pending.talents.map((t: any, i: number) => talentCard(t, Settings.you, this.fac, { on: chosen === i, buttons: `<button class="btn sm" type="button" data-vet="${i}" ${t.vetted || this.sim.view.you.money < 5 ? 'disabled' : ''}>${t.vetted ? 'Vetted' : 'Vet · $5'}</button><button class="btn sm ${chosen === i ? 'red' : ''}" type="button" data-pick="${i}">${chosen === i ? 'Chosen' : 'Choose'}</button>` })).join('');
      const node = el(`<div class="ecard card paper wide"><div class="card-in">${scene(p.art || 'capitol', Settings.you, Settings.them)}<div class="ebody"><div class="kicker">${esc(p.kicker)}</div><h2>${esc(p.title)}</h2><p class="story">${esc(p.body)}</p>
        <div class="talents" id="mates">${draw()}</div><div class="efoot"><span class="type muted">Cash $<b id="cash">${Math.round(this.v.you.money)}</b></span><button class="btn red" type="button" data-ok disabled>Put them on the ticket</button></div></div></div></div>`);
      const m = custom(node, p.title);
      node.addEventListener('click', e => { const t = e.target as HTMLElement, v = t.closest('[data-vet]') as HTMLButtonElement | null, k = t.closest('[data-pick]') as HTMLElement | null;
        if (v && !v.disabled) { this.sim.tryCmd({ c: 'vet', n: +v.dataset.vet! }); sound.click(); } if (k) { chosen = +k.dataset.pick!; sound.click(); }
        if (v || k) { $('#mates', node).innerHTML = draw(); $('#cash', node).textContent = String(Math.round(this.sim.view.you.money)); ($('[data-ok]', node) as HTMLButtonElement).disabled = chosen == null; }
        if (t.closest('[data-ok]') && chosen != null) { m.close(); const r = this.sim.tryCmd({ c: 'choose', n: chosen }); this.refresh(); this.play(r.fx).then(() => res()); } });
    });
  }
  cabinetPick(p: any): Promise<number[]> {
    return new Promise(res => {
      const posts = ['Treasury', 'State', 'Defense'], chosen: (number | null)[] = [null, null, null]; const T = p.talents;
      const draw = () => posts.map((post, i) => `<div style="display:grid;gap:6px"><span class="sc">Secretary of ${post}</span><div class="talents">${[0, 1].map(j => { const k = i * 2 + j, t = T[k]; const mine = this.v.factions.find((f: any) => f.id === t.faction)?.status === 2;
        return talentCard(t, Settings.you, this.fac, { on: chosen[i] === k, buttons: `<button class="btn sm ${chosen[i] === k ? 'red' : ''}" type="button" data-k="${k}">${chosen[i] === k ? 'Nominated' : 'Nominate'}</button><span class="chip ${mine ? '' : 'warn'}">${mine ? 'Your coalition' : 'Unity pick'}</span>` }); }).join('')}</div></div>`).join('');
      const node = el(`<div class="ecard card paper wide"><div class="card-in">${scene('oval', Settings.you, Settings.them)}<div class="ebody"><div class="kicker">${esc(p.kicker)}</div><h2>${esc(p.title)}</h2><p class="story">${esc(p.body)}</p>
        <div id="cab" style="display:grid;gap:14px">${draw()}</div><div class="efoot"><span></span><button class="btn red" type="button" data-ok disabled>Send to the Senate</button></div></div></div></div>`);
      const m = custom(node, p.title);
      node.addEventListener('click', e => { const b = (e.target as HTMLElement).closest('[data-k]') as HTMLElement | null; if (b) { const k = +b.dataset.k!; chosen[Math.floor(k / 2)] = k; $('#cab', node).innerHTML = draw(); ($('[data-ok]', node) as HTMLButtonElement).disabled = chosen.some(x => x == null); sound.click(); }
        if ((e.target as HTMLElement).closest('[data-ok]') && !chosen.some(x => x == null)) { m.close(); res(chosen as number[]); } });
    });
  }
  async night(p: any) {
    this.clearSel(); this.closeSide(); this.board.fit(); const called: Record<string, string | null> = {}; this.resultsMap = called;
    const margins: Record<string, { margin: number; ev: number }> = {}; for (const s of p.margins) margins[s.code] = s;
    const panel = el(`<div class="night card paper"><h2>Election night <span class="clock">6:00 PM</span></h2><div class="bigev"><b style="color:${Settings.you}" id="ny">0</b><div class="tally-bar"><i id="nyb" style="width:0;background:${Settings.you}"></i><i id="ntb" style="width:0;background:${Settings.them};margin-left:auto"></i></div><b style="color:${Settings.them};text-align:right" id="nt">0</b></div>
      <div class="row" style="justify-content:space-between"><span class="calls" id="calls" aria-live="polite">Polls are closing in the East.</span><span class="row" style="gap:6px"><button class="btn sm" type="button" id="nskip">Skip</button><button class="btn sm red" type="button" id="ndone" hidden>Continue</button></span></div></div>`);
    this.root.querySelector('.game')!.append(panel); this.renderAll();
    let skip = false, y = 0, t = 0; $('#nskip', panel).onclick = () => { skip = true; };
    const upd = () => { $('#ny', panel).textContent = String(y); $('#nt', panel).textContent = String(t); $('#nyb', panel).style.width = (y / 538 * 100) + '%'; $('#ntb', panel).style.width = (t / 538 * 100) + '%'; };
    const late: string[] = [];
    const call = (code: string, time: string, final: boolean) => { const m = margins[code]; if (!m) return;
      if (p.recount && code === 'FL') { called[code] = '#c9a24f'; return; }
      if (!final && Math.abs(m.margin) < 1.2) { called[code] = '#9a8f7c'; late.push(code); return; }
      called[code] = m.margin > 0 ? Settings.you : Settings.them; if (m.margin > 0) y += m.ev; else t += m.ev;
      $('#calls', panel).textContent = `${time} · ${this.v.states.find((s: any) => s.code === code)?.name} (${m.ev}) projected for ${m.margin > 0 ? 'you' : this.v.rival.leader.last}`; };
    for (const [time, list] of CLOSING) { $('.clock', panel).textContent = time; for (const c of list) call(c, time, false); upd(); this.paint(); sound.click(); if (!skip) await sleep(600); }
    for (const c of late) { call(c, 'Late', true); upd(); this.paint(); if (!skip) await sleep(450); }
    $('#calls', panel).innerHTML = p.recount ? `Florida (25) is <b style="color:var(--stamp)">too close to call</b>. Senate ${p.senate}, House ${p.house}.` : `${p.ev[0] >= 270 ? 'You win' : this.v.rival.leader.last + ' wins'}. Your party: Senate ${p.senate}, House ${p.house}.`;
    $('#nskip', panel).hidden = true; const d = $('#ndone', panel); d.hidden = false; d.focus(); sound.boom();
    await new Promise<void>(r => { d.onclick = () => r(); });
    panel.remove(); this.resultsMap = null; this.paint();
  }
  endScreen(p: any) {
    const v = this.v, won = p.won; store.set(SAVE_KEY, null);
    const hall = store.get('ags-hall', []); if (won) { hall.unshift({ name: v.you.leader.name, id: v.you.leader.id + v.you.leader.first, pres: v.you.leader.pres, age: v.you.leader.age, party: v.you.party, legacy: p.legacy, when: 'Elected 2000' }); store.set('ags-hall', hall.slice(0, 12)); }
    const ahead = [['2002', 'The midterms', false], ['2003', 'The Iraq question', false], ['2005', 'Hurricane Katrina', false], ['2008', 'The financial crisis', false], ['2020', 'The pandemic', false], ['2028', 'The AI labour shock begins (forecast)', true], ['2030s', 'The Taiwan Strait (forecast)', true], ['2048', 'The last election of the half-century', true]];
    const node = el(`<div class="ecard card paper wide"><div class="card-in">${scene('dawn', Settings.you, Settings.them)}<div class="ebody"><div class="kicker">The end of the slice · December 2001</div>
      <h2>${won ? `President ${esc(v.you.leader.last)}, and forty-seven years to go` : 'The opposition, and the long road back'}</h2>
      <div class="statgrid"><div class="stat"><span>Electoral votes</span><b>${v.election ? v.election.ev.join('–') : '—'}</b></div><div class="stat"><span>Legacy score</span><b>${p.legacy}</b></div>
        <div class="stat"><span>Laws signed</span><b>${v.office && v.office.holder === 0 ? v.office.passed.length : 0}</b></div><div class="stat"><span>Allied factions</span><b>${v.factions.filter((f: any) => f.status === 2).length}</b></div>
        <div class="stat"><span>September</span><b style="font-size:20px">${v.flags.includes('foiled') ? 'Plot foiled' : v.flags.includes('attacked') ? 'Attacked' : '—'}</b></div><div class="stat"><span>Achievements</span><b>${v.achievements.length}</b></div></div>
      <p class="story">This is where the vertical slice stops. In the full game the clock keeps running, a quarter at a time, to the 2048 election: new eras, new units, new ideas, and new leaders from your bench when this one retires.</p>
      <div class="timeline">${ahead.map(([yr, txt, fog]) => `<div class="tl ${fog ? 'fog' : ''}"><b>${yr}</b> · ${txt}</div>`).join('')}</div>
      <div class="efoot"><span class="type muted">Seed ${v.seed}</span><button class="btn red" type="button">Back to the title</button></div></div></div></div>`);
    const m = custom(node, 'The end of the slice'); $('.btn', node).onclick = () => { m.close(); this.onQuit(); };
  }

  /* ---------------- dossiers ---------------- */
  closeSide() { $('.side')?.remove(); }
  openCity(hex: number) {
    this.closeSide(); this.sel = null; this.reach = null; this.renderUnit();
    const v = this.v, hq = v.you.hqs.find((q: any) => q.hex === hex), rhq = v.rival.hqs.find((q: any) => q.hex === hex), h = this.map.hexes[hex], c = h.c != null ? this.map.cities[h.c] : null, st = v.states.find((s: any) => s.code === h.s);
    const panel = el(`<aside class="side" aria-label="${esc(c?.name || 'Dossier')}"></aside>`); this.root.querySelector('.game')!.append(panel);
    const draw = () => {
      const v = this.v; const hq = v.you.hqs.find((q: any) => q.hex === hex);
      if (v.office && v.office.wh === hex && v.office.holder === 0) { panel.innerHTML = this.whPanel(); return; }
      if (!hq) { panel.innerHTML = `<button class="btn sm x" type="button" data-x>${icon('close')}</button><div><span class="sc">${esc(st?.name)} · ${st?.ev} electoral votes</span><h2>${esc(c?.name)}</h2></div>
        <div class="statgrid"><div class="stat"><span>Metro</span><b>${c!.pop >= 1 ? c!.pop.toFixed(1) + 'M' : (c!.pop * 1000).toFixed(0) + 'k'}</b></div><div class="stat"><span>${esc(st?.name)}</span><b style="font-size:20px">${st.seen >= 0 ? 'You' : 'Them'} +${Math.abs(st.seen).toFixed(1)}</b></div></div>
        <div class="sheet"><p class="story" style="font-size:15px">${rhq ? `${esc(v.rival.leader.last)} runs a state HQ here: it pushes this whole area every turn. Contest it with your units, or out-campaign it.` : 'No HQ here. Move an Organizer onto this city and open a state HQ: it will build for you, and push three rings around it every turn.'}</p></div>`; return; }
      const cur = hq.prod;
      const row = (kind: string, o: any) => { const cost = o.cost, on = cur && cur.id === (o.line || o.id); const lock = kind === 'building' && (!o.open || hq.buildings.includes(o.id));
        return `<button class="prod ${on ? 'on' : ''}" type="button" data-pick="${kind}:${o.line || o.id}" ${lock ? 'disabled' : ''}><span class="pi">${icon(o.icon)}</span><span><b>${esc(o.name)}</b><small>${kind === 'building' ? (hq.buildings.includes(o.id) ? 'Built.' : o.open ? esc(o.text) : 'Needs ' + esc(o.requires)) : esc((o.role || '').split('.')[0] + '.')}</small></span><span class="t">${cost} vol<br>${Math.max(1, Math.ceil(cost / hq.vol))} turn${Math.ceil(cost / hq.vol) > 1 ? 's' : ''}</span></button>`; };
      panel.innerHTML = `<button class="btn sm x" type="button" data-x>${icon('close')}</button><div><span class="sc">${hq.capital ? 'Your capital' : 'State HQ'} · ${esc(st?.name)}</span><h2>${esc(hq.name)}</h2></div>
        <div class="yields4"><div>${icon('vol')}<b>${hq.vol}</b><span>volunteers</span></div><div>${icon('money')}<b>+${Math.round(Math.sqrt(hq.pop))}</b><span>money</span></div><div>${icon('ideas')}<b>+1</b><span>ideas</span></div><div>${icon('scroll')}<b>${hq.buildings.length}</b><span>buildings</span></div></div>
        <div class="sheet"><span class="sc">Producing</span>${cur ? `<div class="row" style="justify-content:space-between;align-items:center"><span><b>${esc(cur.name)}</b> · ${Math.floor(cur.got)}/${cur.cost} · ${cur.turns} turn${cur.turns > 1 ? 's' : ''}</span><button class="btn sm red" type="button" data-buy ${v.you.money >= hq.buy ? '' : 'disabled'}>Buy $${hq.buy}</button></div><div class="bar"><i style="width:${Math.min(100, cur.got / cur.cost * 100)}%"></i></div>` : '<span class="muted">Nothing. Choose below.</span>'}</div>
        <div class="sheet"><span class="sc">Units</span>${v.build.units.map((o: any) => row('unit', o)).join('')}<span class="sc" style="margin-top:6px">Buildings</span>${v.build.buildings.map((o: any) => row('building', o)).join('')}</div>`;
    };
    panel.onclick = async e => {
      const t = e.target as HTMLElement; if (t.closest('[data-x]')) return this.closeSide();
      const p = t.closest('[data-pick]') as HTMLButtonElement | null; if (p && !p.disabled) { const [kind, id] = p.dataset.pick!.split(':'); await this.cmd({ c: 'prod', hq: hex, kind, id }); draw(); return; }
      if (t.closest('[data-buy]')) { await this.cmd({ c: 'buy', hq: hex }); sound.good(); draw(); return; }
      const l = t.closest('[data-law]') as HTMLElement | null; if (l) { await this.cmd({ c: 'law', law: l.dataset.law }); draw(); }
    };
    draw(); this.board.centerHex(hex, Math.max(this.board.view.z, 2), 300);
  }
  whPanel() {
    const v = this.v, o = v.office;
    return `<button class="btn sm x" type="button" data-x>${icon('close')}</button><div><span class="sc">Washington · your seat of power</span><h2>The White House</h2></div>
      <div class="statgrid"><div class="stat"><span>Approval</span><b>${o.approval.toFixed(0)}%</b></div><div class="stat"><span>Capital a turn</span><b>${o.rate.toFixed(0)}</b></div><div class="stat"><span>Senate</span><b>${o.senate}</b></div></div>
      <div class="sheet"><p class="story" style="font-size:15px;margin:0">Laws are this game's wonders: the White House builds them with political capital over several turns, one at a time. When one is ready, the Senate votes; approval and your seats set the odds.</p></div>
      ${o.law ? `<div class="sheet"><span class="sc">Building</span><b>${esc(o.law.name)}</b><div class="bar"><i style="width:${Math.min(100, o.law.progress / o.law.cost * 100)}%"></i></div><span class="type muted">${Math.floor(o.law.progress)}/${o.law.cost} · ${Math.max(1, Math.ceil((o.law.cost - o.law.progress) / o.rate))} turns</span></div>` : ''}
      <div class="sheet"><span class="sc">Laws</span>${v.laws.map((l: any) => `<button class="prod ${o.law && o.law.id === l.id ? 'on' : ''}" type="button" data-law="${l.id}" ${l.passed ? 'disabled' : ''}><span class="pi">${icon('capitol')}</span><span><b>${esc(l.name)}</b><small>${l.passed ? 'Signed into law.' : esc(l.text)}${l.promised ? ' · you promised this' : ''}</small></span><span class="t">${l.cost}<br>${Math.ceil(l.cost / o.rate)} turns</span></button>
        ${o.law && o.law.id === l.id && l.forecast ? forecastCard({ title: l.name, tag: 'If it becomes law', lines: [{ label: 'Your national vote', lo: l.forecast.national[0], mid: l.forecast.national[1], hi: l.forecast.national[2], unit: 'pts', good: 'up' }, ...l.forecast.lines], factions: l.forecast.factions }, this.fac, Settings.ranges) : ''}`).join('')}</div>
      ${o.cabinet.length ? `<div class="sheet"><span class="sc">Cabinet</span>${o.cabinet.map(([post, t]: any) => `<div>${esc(post)}: <b>${esc(t.name)}</b> <span class="muted">(${t.cha}/${t.pol}/${t.deal})</span></div>`).join('')}</div>` : ''}`;
  }
  rival() {
    const R = this.v.rival;
    const node = el(`<div class="ecard card paper wide" style="width:min(820px,100%)"><div class="card-in"><div style="display:grid;grid-template-columns:minmax(0,230px) minmax(0,1fr)">
      <div style="border-right:1.5px solid var(--ink);padding:16px;background:var(--paper-2);display:grid;place-items:center">${portrait('rival-' + R.leader.name, { spot: Settings.them, pres: R.leader.pres, age: R.leader.age, title: R.leader.name })}</div>
      <div class="ebody"><div class="kicker">Your rival · ${esc(R.persona)}</div><h2>${esc(R.leader.name)}</h2><div class="kicker">${esc(R.leader.title)} · ${esc(R.party)} Party</div>
      <p class="story">"${esc('The boom was built by the people, not by Washington. I mean to give them their money back.')}"</p>
      <div class="uniq"><div><span class="sc">Unique ability</span><h4>${esc(R.ability)}</h4><p>${esc(R.ability_text)}</p></div><div><span class="sc">Their HQs</span><p>${R.hqs.map((q: any) => esc(q.name)).join(', ')}</p><span class="sc">Doctrines</span><p>${R.policies.length || 'None yet'}</p></div></div>
      <div class="efoot"><span class="type muted">Cash about $${R.money}</span><button class="btn red" type="button">Back to the table</button></div></div></div></div></div>`);
    const m = custom(node, R.leader.name); $('.btn', node).onclick = () => m.close();
  }
  report() {
    // the same cause twice (two canvasses of one city) reads as one line with a count
    const by = new Map<string, any>();
    for (const x of this.v.report) { const g = by.get(x.why); if (!g) { by.set(x.why, { why: x.why, nat: x.nat, n: 1, top: new Map(x.top) }); continue; }
      g.nat += x.nat; g.n++; for (const [c, d] of x.top) g.top.set(c, (g.top.get(c) || 0) + d); }
    const items = [...by.values()].map(g => ({ why: g.n > 1 ? `${g.why} ×${g.n}` : g.why, nat: g.nat, top: [...g.top.entries()].sort((a: any, b: any) => Math.abs(b[1]) - Math.abs(a[1])).slice(0, 3) })).sort((a, b) => Math.abs(b.nat) - Math.abs(a.nat));
    const node = el(`<div class="ecard card paper"><div class="card-in"><div class="ebody"><div class="kicker">Turn report · ${esc(this.v.history[this.v.history.length - 1]?.[0] || '')}</div><h2>What moved the race</h2>
      <div style="display:grid">${items.map((x: any) => `<div class="row" style="justify-content:space-between;border-bottom:1px dotted var(--rule);padding:4px 0;flex-wrap:nowrap"><span>${esc(x.why)}${x.top.length ? ` <span class="muted">(${x.top.map(([c, d]: any) => `${c} ${sgn(d, 1)}`).join(', ')})</span>` : ''}</span><b class="${x.nat >= 0 ? 'up' : 'down'} tnum">${sgn(x.nat / 2, 2)}%</b></div>`).join('') || '<span class="muted">A quiet turn.</span>'}</div>
      <div class="efoot"><span class="type muted">Shares of the two-party vote, nationally; in brackets, the states that moved most.</span><button class="btn red" type="button">Back to the table</button></div></div></div></div>`);
    const m = custom(node, 'Turn report'); $('.btn', node).onclick = () => m.close();
  }
  screen(k: string) { this.closeSide(); hideTip(); openBinder(k, this); }
  async afterBinder() { closeBinder(); this.refresh(); }
  menu() {
    const iron = !!this.sim.save()?.ironman;
    const node = el(`<div class="ecard card paper" style="width:min(440px,100%)"><div class="card-in"><div class="ebody"><div class="kicker">Paused</div><h2>Menu</h2>
      <div class="menu"><button class="btn" type="button" data-m="resume">Resume</button>${iron ? '' : '<button class="btn" type="button" data-m="save">Save the game</button>'}<button class="btn" type="button" data-m="settings">Settings</button><button class="btn" type="button" data-m="quit">Quit to the title</button></div>
      <p class="note">${iron ? 'Ironman: one save, kept automatically at the start of every turn.' : 'The game also saves itself at the start of every turn.'}</p></div></div></div>`);
    const m = custom(node, 'Menu');
    node.onclick = e => { const b = (e.target as HTMLElement).closest('[data-m]') as HTMLElement | null; if (!b) return; const k = b.dataset.m; m.close();
      if (k === 'save') { const s = this.sim.save(); const slots = store.get(SLOTS_KEY, []); slots.unshift({ when: new Date().toISOString(), save: s }); store.set(SLOTS_KEY, slots.slice(0, 6)); toast('scroll', 'Saved', s.label); }
      if (k === 'settings') import('./settings').then(x => x.settingsCard(() => this.renderAll()));
      if (k === 'quit') { this.autosave(); this.onQuit(); } };
  }
}
const ACH: Record<string, string> = { ally: 'Coalition builder', expansion: 'Expansion', doctrine: 'A creed', surge: 'Surge', contest: 'First blood', legend: 'A legend at work', great: 'A legend joins', chads: 'Hanging chads', landslide: 'Landslide', first_law: 'The pen', slice: 'The end of the beginning' };
