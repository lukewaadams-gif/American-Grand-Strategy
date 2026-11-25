// Small pieces of the interface: elements, icons, cards, forecast cards, talent cards, dice, toasts and tooltips.
import { scene, portrait } from '../art/print';

export const $ = <T extends Element = HTMLElement>(s: string, r: ParentNode = document) => r.querySelector(s) as T;
export const $$ = <T extends Element = HTMLElement>(s: string, r: ParentNode = document) => [...r.querySelectorAll(s)] as T[];
export const el = (html: string) => { const t = document.createElement('template'); t.innerHTML = html.trim(); return t.content.firstElementChild as HTMLElement; };
export const esc = (s: unknown) => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]!));
export const sgn = (v: number, d = 1) => (v > 1e-4 ? '+' : v < -1e-4 ? '−' : '±') + Math.abs(v).toFixed(d);
export const clamp = (v: number, a: number, b: number) => Math.max(a, Math.min(b, v));
export const sleep = (ms: number) => new Promise(r => setTimeout(r, document.documentElement.classList.contains('reduce-motion') ? 0 : ms));

const P: Record<string, string> = {
  money: '<circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2"/><path d="M14.6 8.6c-.5-1-1.5-1.5-2.6-1.5-1.5 0-2.6.8-2.6 2s1 1.6 2.6 2 2.6.8 2.6 2.1-1.1 2.1-2.6 2.1c-1.2 0-2.2-.5-2.7-1.6M12 5.5v1.6M12 16.9v1.6" stroke="currentColor" stroke-width="1.8" fill="none" stroke-linecap="round"/>',
  inf: '<path d="M12 2.5l2.6 6 6.4.6-4.9 4.2 1.5 6.3L12 16.3l-5.6 3.3 1.5-6.3L3 9.1l6.4-.6L12 2.5Z" fill="currentColor"/>',
  ideas: '<path d="M12 2.5a6.8 6.8 0 0 0-3.9 12.4V18h7.8v-3.1A6.8 6.8 0 0 0 12 2.5Z" fill="currentColor"/><path d="M9 20h6v2H9z" fill="currentColor"/>',
  mom: '<path d="M12 2c3 4 6 6.5 6 11a6 6 0 0 1-12 0c0-2.5 1.2-4.3 2.6-5.6.2 2 1 3.2 2.4 3.8C10.6 8.6 11 5.4 12 2Z" fill="currentColor"/>',
  gp: '<path d="M12 21V9M12 21c-4-1-7-5-7-10M12 21c4-1 7-5 7-10" stroke="currentColor" stroke-width="2" fill="none"/><circle cx="12" cy="6" r="3" fill="currentColor"/>',
  vol: '<circle cx="8" cy="7" r="3" fill="currentColor"/><circle cx="16" cy="7" r="3" fill="currentColor"/><path d="M2 20c0-4 2.7-6.5 6-6.5s6 2.5 6 6.5M10 20c0-4 2.7-6.5 6-6.5s6 2.5 6 6.5" fill="currentColor"/>',
  star: '<path d="M12 2l2.9 6.5 7.1.6-5.4 4.7 1.6 7L12 17l-6.2 3.8 1.6-7L2 9.1l7.1-.6L12 2Z" fill="currentColor"/>',
  medal: '<circle cx="12" cy="14" r="6" fill="currentColor"/><path d="M8 2h3l1 6-3 1L8 2Zm8 0h-3l-1 6 3 1 1-7Z" fill="currentColor"/>',
  clipboard: '<path d="M8 3h8v3H8z" fill="currentColor"/><path d="M6 5h12v17H6z" fill="none" stroke="currentColor" stroke-width="2"/><path d="M9 11h6M9 15h6M9 19h4" stroke="currentColor" stroke-width="1.8"/>',
  megaphone: '<path d="M3 10v4h3l9 5V5L6 10H3Z" fill="currentColor"/><path d="M18 8a5 5 0 0 1 0 8" stroke="currentColor" stroke-width="2" fill="none"/>',
  chart: '<path d="M4 20V12h4v8H4Zm6 0V6h4v14h-4Zm6 0v-9h4v9h-4Z" fill="currentColor"/>',
  tv: '<rect x="3" y="6" width="18" height="12" rx="1.5" fill="currentColor"/><path d="M8 2l4 4 4-4" stroke="currentColor" stroke-width="2" fill="none"/>',
  scales: '<path d="M12 3v18M7 21h10M4 7h16M6 7l-3 6a3 3 0 0 0 6 0L6 7Zm12 0-3 6a3 3 0 0 0 6 0l-3-6Z" stroke="currentColor" stroke-width="1.7" fill="none" stroke-linejoin="round"/>',
  laurel: '<path d="M12 21V9M12 21c-4-1-7-5-7-10M12 21c4-1 7-5 7-10" stroke="currentColor" stroke-width="2" fill="none"/><circle cx="12" cy="6" r="3" fill="currentColor"/>',
  phone: '<path d="M6 3h4l1.5 5-2.5 1.5a11 11 0 0 0 5.5 5.5L16 12.5l5 1.5v4a2 2 0 0 1-2 2A17 17 0 0 1 4 5a2 2 0 0 1 2-2Z" fill="currentColor"/>',
  coin: '<circle cx="12" cy="12" r="9" fill="currentColor"/>', scroll: '<path d="M6 3h11a3 3 0 0 1 3 3v1h-4v12a2 2 0 0 1-2 2H6a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3Z" fill="currentColor"/>',
  bulb: '<path d="M12 2a7 7 0 0 0-4 12.7V18h8v-3.3A7 7 0 0 0 12 2Z" fill="currentColor"/><path d="M9 20h6v2H9z" fill="currentColor"/>',
  shield: '<path d="M12 2 4 5v6c0 5 3.4 9.4 8 11 4.6-1.6 8-6 8-11V5l-8-3Z" fill="currentColor"/>', eye: '<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z" fill="currentColor"/><circle cx="12" cy="12" r="3.3" fill="#efe6d0"/>',
  plane: '<path d="M21 15v-2l-8-5V3.5a1.5 1.5 0 0 0-3 0V8l-8 5v2l8-2.5V18l-2 1.5V21l3.5-1 3.5 1v-1.5L13 18v-5.5l8 2.5Z" fill="currentColor"/>',
  flag: '<path d="M5 22V3M5 3h12l-2 4 2 4H5" stroke="currentColor" stroke-width="2" fill="currentColor" stroke-linejoin="round"/>',
  door: '<path d="M6 21V3h10v18M6 21h14" stroke="currentColor" stroke-width="2" fill="none"/><circle cx="13" cy="12" r="1.3" fill="currentColor"/>',
  office: '<path d="M3 21V9l9-6 9 6v12h-6v-6H9v6H3Z" fill="currentColor"/>', skip: '<path d="M5 5v14l8-7-8-7Zm9 0v14h3V5h-3Z" fill="currentColor"/>', repeat: '<path d="M4 12a8 8 0 0 1 14-5.3M20 12a8 8 0 0 1-14 5.3" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round"/><path d="M19 2v5h-5M5 22v-5h5" stroke="currentColor" stroke-width="2" fill="none" stroke-linejoin="round"/>', sleep: '<path d="M4 6h7l-7 8h7M13 12h6l-6 7h6" stroke="currentColor" stroke-width="2" fill="none" stroke-linejoin="round" stroke-linecap="round"/>',
  fist: '<path d="M7 11V7a2 2 0 0 1 4 0v3M11 10V6a2 2 0 0 1 4 0v4M15 10V8a2 2 0 0 1 4 0v5c0 4-3 8-7 8-3 0-5-2-6-4l-2-4a2 2 0 0 1 3.5-2L7 13" stroke="currentColor" stroke-width="1.8" fill="none" stroke-linecap="round"/>',
  close: '<path d="M6 6l12 12M18 6 6 18" stroke="currentColor" stroke-width="2.2"/>', menu: '<path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" stroke-width="2.2"/>',
  plus: '<path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2.4"/>', minus: '<path d="M5 12h14" stroke="currentColor" stroke-width="2.4"/>',
  globe: '<circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8" fill="none"/><path d="M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18" stroke="currentColor" stroke-width="1.5" fill="none"/>',
  people: '<circle cx="8" cy="8" r="3" fill="currentColor"/><circle cx="16" cy="8" r="3" fill="currentColor"/><path d="M2 20c0-3.3 2.7-5 6-5s6 1.7 6 5M10 20c0-3.3 2.7-5 6-5s6 1.7 6 5" fill="currentColor"/>',
  capitol: '<path d="M12 3v2M8 10a4 4 0 0 1 8 0M5 10h14v2H5zM6 12v7M9 12v7M12 12v7M15 12v7M18 12v7M4 19h16v2H4z" stroke="currentColor" stroke-width="1.7" fill="none"/>',
  trophy: '<path d="M7 3h10v4a5 5 0 0 1-10 0V3ZM4 4h3v3a3 3 0 0 1-3-3Zm16 0h-3v3a3 3 0 0 0 3-3ZM10 13h4v4h-4zM7 19h10v2H7z" fill="currentColor"/>',
  hourglass: '<path d="M6 2h12M6 22h12M7 2c0 6 5 7 5 10s-5 4-5 10M17 2c0 6-5 7-5 10s5 4 5 10" stroke="currentColor" stroke-width="1.8" fill="none"/>',
  sound: '<path d="M4 9v6h4l5 4V5L8 9H4Z" fill="currentColor"/><path d="M16 8.5a5 5 0 0 1 0 7M18.5 6a8.5 8.5 0 0 1 0 12" stroke="currentColor" stroke-width="1.7" fill="none"/>',
  mute: '<path d="M4 9v6h4l5 4V5L8 9H4Z" fill="currentColor"/><path d="m16 9 5 6M21 9l-5 6" stroke="currentColor" stroke-width="1.7"/>',
  layers: '<path d="M12 3 2 8l10 5 10-5-10-5Zm-10 9 10 5 10-5M2 16l10 5 10-5" stroke="currentColor" stroke-width="1.7" fill="none" stroke-linejoin="round"/>',
  gear: '<path d="M12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7Zm8.4 4.6-1.9.5a6.7 6.7 0 0 1-.7 1.7l1 1.7-1.6 1.6-1.7-1a6.7 6.7 0 0 1-1.7.7l-.5 1.9h-2.4l-.5-1.9a6.7 6.7 0 0 1-1.7-.7l-1.7 1-1.6-1.6 1-1.7a6.7 6.7 0 0 1-.7-1.7l-1.9-.5v-2.2l1.9-.5c.2-.6.4-1.2.7-1.7l-1-1.7 1.6-1.6 1.7 1c.5-.3 1.1-.5 1.7-.7l.5-1.9h2.4l.5 1.9c.6.2 1.2.4 1.7.7l1.7-1 1.6 1.6-1 1.7c.3.5.5 1.1.7 1.7l1.9.5Z" fill="currentColor"/>',
  steeple: '<path d="M12 2 9 8v2H7v11h10V10h-2V8l-3-6Z" fill="currentColor"/>',
  wheat: '<path d="M12 22V9M12 9c-2-1-3-3-3-5 2 1 3 3 3 5Zm0 0c2-1 3-3 3-5-2 1-3 3-3 5Zm0 4c-2-1-3.5-2.5-4-4.5 2 .5 3.5 2 4 4.5Zm0 0c2-1 3.5-2.5 4-4.5-2 .5-3.5 2-4 4.5Zm0 4c-2-1-3.5-2.5-4-4.5 2 .5 3.5 2 4 4.5Zm0 0c2-1 3.5-2.5 4-4.5-2 .5-3.5 2-4 4.5Z" stroke="currentColor" stroke-width="1.7" fill="none" stroke-linecap="round"/>',
  leaf: '<path d="M20 4C10 4 4 9 4 16c0 1.5.4 2.8 1 4 1-4 4-7 8-9-3 3-5 6-5.5 9.5C15 21 20 15 20 4Z" fill="currentColor"/>',
  chip: '<rect x="7" y="7" width="10" height="10" rx="1.5" fill="currentColor"/><path d="M9 3v3M12 3v3M15 3v3M9 18v3M12 18v3M15 18v3M3 9h3M3 12h3M3 15h3M18 9h3M18 12h3M18 15h3" stroke="currentColor" stroke-width="1.7"/>',
  torch: '<path d="M12 2c2 2.5 3 4 3 5.5A3 3 0 0 1 12 10a3 3 0 0 1-3-2.5C9 6 10 4.5 12 2Z" fill="currentColor"/><path d="M8.5 11h7l-1.5 3h-4L8.5 11ZM11 14h2v8h-2z" fill="currentColor"/>',
  house: '<path d="M3 11 12 3l9 8v10h-6v-6H9v6H3V11Z" fill="currentColor"/>',
};
export const icon = (n: string) => `<svg class="ic" viewBox="0 0 24 24" aria-hidden="true">${P[n] || P.star}</svg>`;

/* ---------------- cards ---------------- */
function openScrim(node: HTMLElement, label: string) {
  const s = document.createElement('div'); s.className = 'scrim'; s.setAttribute('role', 'dialog'); s.setAttribute('aria-modal', 'true'); s.setAttribute('aria-label', label); s.append(node); document.body.append(s);
  setTimeout(() => (node.querySelector('.choice:not([disabled]), .btn.red:not([disabled]), button:not([disabled])') as HTMLElement | null)?.focus({ preventScroll: true }), 50);
  return { close() { s.remove(); }, el: s };
}
export function fcLine(l: any, show: boolean) {
  const { label, lo, mid, hi, unit, good, level } = l; let arrow: string, cls: string, verdict: string, a: number, b: number, zero: number, num: string;
  if (level) { const [thr, above, below, near] = level, gap = Math.max(2, (hi - lo) / 4), side = mid >= thr + gap ? 1 : mid <= thr - gap ? -1 : 0, g = side === 0 ? null : (good === 'up') === (side > 0);
    arrow = '●'; cls = g == null ? 'muted' : g ? 'up' : 'down'; verdict = side > 0 ? above : side < 0 ? below : near; a = Math.min(thr, lo); b = Math.max(thr, hi); zero = thr; num = withUnit(mid, unit); }
  else { const dir = mid > 0 ? 1 : mid < 0 ? -1 : 0, crosses = lo < 0 && hi > 0, g = good === 'up' ? dir > 0 : dir < 0, strong = !crosses && Math.min(Math.abs(lo), Math.abs(hi)) / Math.max(Math.abs(lo), Math.abs(hi)) > .35;
    arrow = dir === 0 || (crosses && Math.abs(mid) < (hi - lo) / 5) ? '▬' : dir > 0 ? (strong ? '▲▲' : '▲') : (strong ? '▼▼' : '▼'); cls = arrow === '▬' ? 'muted' : g ? 'up' : 'down';
    verdict = arrow === '▬' ? 'Could go either way' : crosses ? (g ? 'Probably better' : 'Probably worse') : (g ? (strong ? 'Clearly better' : 'Better') : (strong ? 'Clearly worse' : 'Worse'));
    a = Math.min(0, lo); b = Math.max(0, hi); zero = 0; num = withUnit(mid, unit, true); }
  const pad = (b - a) * .12 || 1, A = a - pad, B = b + pad, X = (v: number) => ((v - A) / (B - A) * 100).toFixed(1);
  return `<div class="fc-line"><span class="arrow ${cls}" aria-hidden="true">${arrow}</span><span>${esc(label)}<br><span class="v">${verdict}</span></span><span class="fc-num">${num}</span>
    ${show ? `<div class="fc-detail"><div class="range" aria-hidden="true"><div class="axis"></div><div class="zero" style="left:${X(zero)}%"></div><div class="band" style="left:${X(lo)}%;width:${(+X(hi) - +X(lo)).toFixed(1)}%"></div><div class="mid" style="left:calc(${X(mid)}% - 1.5px)"></div></div><span class="v">90% range: ${withUnit(lo, unit)} to ${withUnit(hi, unit)}</span></div>` : ''}</div>`;
}
function fmtN(v: number) { const a = Math.abs(v); return a >= 100 ? Math.round(a).toLocaleString('en-US') : a >= 10 ? a.toFixed(0) : a.toFixed(1); }
export function withUnit(v: number, unit: string, signed = false) { const s = v < 0 ? '−' : signed && v > 0 ? '+' : '', n = fmtN(v); if (!unit) return s + n; if (unit.startsWith('$')) return `${s}$${n}${unit.slice(1)}`; if (/^[%¢kM×]/.test(unit)) return `${s}${n}${unit}`; return `${s}${n} ${unit}`; }
export function forecastCard(f: { title: string; tag?: string; lines: any[]; factions?: Record<string, number> }, factionsById: Record<string, any>, show = true) {
  const fac = f.factions ? `<div class="chips">${Object.entries(f.factions).filter(([, v]) => v).map(([k, v]) => `<span class="chip ${v > 0 ? 'good' : 'bad'}">${icon(factionsById[k]?.icon || 'people')}${esc((factionsById[k]?.name || k).split(' ')[0])} ${v > 0 ? '▲'.repeat(Math.min(3, v)) : '▼'.repeat(Math.min(3, -v))}</span>`).join('')}</div>` : '';
  return `<div class="fc"><div class="fc-head"><b>${esc(f.title)}</b><span>${esc(f.tag || 'Forecast')}</span></div>${f.lines.map(l => fcLine(l, show)).join('')}${fac ? `<div class="fc-foot">${fac}</div>` : ''}</div>`;
}
export const grade = (v: number) => v >= 70 ? 'g70' : v >= 60 ? 'g60' : v >= 45 ? 'g50' : v >= 35 ? 'g40' : 'g30';
export function talentCard(t: any, spot: string, factionsById: Record<string, any>, { on = false, buttons = '' } = {}) {
  const integ = t.integ == null ? '?' : t.integ;
  return `<div class="tal ${on ? 'on' : ''}"><div class="tal-h"><div>${portrait(t.id, { spot, pres: t.pres, age: t.age, title: t.name })}</div><div><b>${esc(t.name)}</b><span>${esc(t.office)} · ${t.age}</span><div style="color:var(--brass);letter-spacing:2px">${'★'.repeat(t.stars)}${'☆'.repeat(5 - t.stars)}</div></div></div>
    <div class="r4"><div><span>Charisma</span><b class="${grade(t.cha)}">${t.cha}</b></div><div><span>Policy</span><b class="${grade(t.pol)}">${t.pol}</b></div><div><span>Deals</span><b class="${grade(t.deal)}">${t.deal}</b></div><div><span>Integrity</span><b class="${integ === '?' ? '' : grade(integ)}">${integ}</b></div></div>
    <div class="chips"><span class="chip">${icon(factionsById[t.faction]?.icon || 'people')}${esc(factionsById[t.faction]?.name || t.faction)}</span>${t.risk && t.risk !== 'unknown' ? `<span class="chip ${t.risk === 'high' ? 'bad' : 'good'}">Scandal risk ${t.risk}</span>` : ''}</div>
    ${buttons ? `<div class="row" style="gap:6px">${buttons}</div>` : ''}</div>`;
}
/** Deal an event card; resolves with the chosen choice's index. */
export function dealCard(p: any, spot: string, spot2: string, extra = '', wide = false): Promise<number> {
  return new Promise(res => {
    // a spread on wide screens: the story on the left, the choices on the right, so no one scrolls to decide
    const node = el(`<div class="ecard card paper spread ${wide ? 'wide' : ''}"><div class="card-in">${p.art ? scene(p.art, spot, spot2) : ''}<div class="ebody">
      <div class="etext"><div class="kicker">${esc(p.kicker || '')}</div><h2>${esc(p.title)}</h2>${p.body ? `<p class="story">${esc(p.body)}</p>` : ''}${extra}
      ${p.advice ? `<p class="advice"><b>Your adviser · </b>${esc(p.advice)}</p>` : ''}</div><div class="choices"></div></div></div></div>`);
    const m = openScrim(node, p.title), box = $('.choices', node);
    for (const c of p.choices || []) {
      const b = el(`<button class="choice" type="button"><b>${esc(c.label)}</b>${c.odds != null ? `<span class="odds" title="Chance it works">${Math.round(c.odds * 100)}%</span>` : '<span></span>'}${c.text ? `<p>${esc(c.text)}</p>` : ''}</button>`);
      b.onclick = () => { m.close(); res(c.n); }; box.append(b);
    }
  });
}
export function resultCard(r: any, kicker = 'The result'): Promise<void> {
  return new Promise(res => {
    const roll = r.roll ? `<div class="roll"><span>${Math.round(r.roll.p * 100)}% chance</span><div class="rollbar"><i style="width:${r.roll.p * 100}%"></i><u style="left:calc(${r.roll.draw * 100}% - 1.5px)"></u></div><b class="${r.roll.win ? 'up' : 'down'}">${r.roll.win ? 'It worked' : 'It did not'}</b></div>` : '';
    const node = el(`<div class="ecard card paper"><div class="card-in"><div class="ebody"><div class="kicker">${esc(r.choice || kicker)}</div><h2>${esc(r.title)}</h2>${r.body ? `<p class="story">${esc(r.body)}</p>` : ''}${roll ? `<div class="result">${roll}</div>` : ''}
      <div class="efoot"><span>${r.roll ? `<span class="stamp">${r.roll.win ? 'Success' : 'Failure'}</span>` : ''}</span><button class="btn red" type="button">Continue</button></div></div></div></div>`);
    const m = openScrim(node, r.title); $('.btn', node).onclick = () => { m.close(); res(); };
  });
}
export function newsCard(p: any, spot: string, spot2: string): Promise<void> {
  return new Promise(res => {
    const node = el(`<div class="ecard card paper newsprint wide" style="width:min(780px,100%)"><div class="card-in"><div class="mast">The Evening Ledger</div>
      <div class="ebody" style="text-align:center;justify-items:center"><div class="kicker">${esc(p.kicker || '')}</div><h2 class="headline">${esc(p.title)}</h2>${p.art ? `<div style="width:100%;border:1px solid var(--ink)">${scene(p.art, spot, spot2)}</div>` : ''}
      <p class="story" style="text-align:center">${esc(p.sub || p.body || '')}</p><button class="btn red" type="button">Continue</button></div></div></div>`);
    const m = openScrim(node, p.title); $('.btn', node).onclick = () => { m.close(); res(); };
  });
}
export function custom(node: HTMLElement, label: string) { return openScrim(node, label); }

/* ---------------- dice ---------------- */
const PIPS: Record<number, number[]> = { 1: [4], 2: [0, 8], 3: [0, 4, 8], 4: [0, 2, 6, 8], 5: [0, 2, 4, 6, 8], 6: [0, 2, 3, 5, 6, 8] };
const die = (n: number, red: boolean) => `<div class="die ${red ? 'red' : ''}" style="animation-delay:${Math.random() * .12}s"><div class="face">${Array.from({ length: 9 }, (_, i) => PIPS[n].includes(i) ? '<i></i>' : '<span></span>').join('')}</div></div>`;
export function rollDice(f: any, youLabel: string, themLabel: string): Promise<void> {
  return new Promise(res => {
    const youAttack = f.side === 0; const [ya, yb] = youAttack ? [f.a, f.am] : [f.b, f.bm]; const [ta, tb] = youAttack ? [f.b, f.bm] : [f.a, f.am];
    const youWin = youAttack ? f.win : !f.win;
    const node = el(`<div class="dice" role="dialog" aria-modal="true" aria-label="${esc(f.title)}"><div class="dice-box card paper"><h3>${esc(f.title)}</h3>
      <div class="dice-row"><div class="dice-side"><div class="pair">${die(ya[0], false)}${die(ya[1], false)}</div><span>${esc(youLabel)} ${ya[0] + ya[1]} + ${yb.toFixed(1)}</span></div><span class="vs">vs</span>
      <div class="dice-side"><div class="pair">${die(ta[0], true)}${die(ta[1], true)}</div><span>${esc(themLabel)} ${ta[0] + ta[1]} + ${tb.toFixed(1)}</span></div></div>
      <span class="stamp" style="opacity:0;transition:opacity .3s">${youWin ? 'You win' : 'You lose'}</span><span class="muted" style="font:400 12.5px var(--f-type)">The odds were ${Math.round((youAttack ? f.p : 1 - f.p) * 100)}% for you. Click to continue.</span></div></div>`);
    document.body.append(node); setTimeout(() => { ($('.stamp', node) as HTMLElement).style.opacity = '1'; }, 950);
    const done = () => { node.remove(); res(); }; node.onclick = done; setTimeout(done, 3200);
  });
}

/* ---------------- toasts and tooltips ---------------- */
export function toast(ic: string, title: string, text: string) { const t = el(`<div class="toast card paper"><div class="medal">${icon(ic)}</div><div><b>${esc(title)}</b><span>${esc(text)}</span></div></div>`); document.body.append(t); setTimeout(() => t.remove(), 4300); }
const tipEl = () => { let t = $('#tip'); if (!t) { t = el('<div class="tip" id="tip" hidden></div>'); document.body.append(t); } return t; };
export function showTip(html: string, x: number, y: number) { const t = tipEl(); t.innerHTML = html; t.hidden = false; const r = t.getBoundingClientRect(); t.style.left = clamp(x + 16, 6, innerWidth - r.width - 6) + 'px'; t.style.top = clamp(y + 16, 6, innerHeight - r.height - 6) + 'px'; }
export const hideTip = () => { const t = $('#tip'); if (t) t.hidden = true; };
export function announce(text: string) { let l = $('#live'); if (!l) { l = el('<div id="live" class="live" aria-live="polite"></div>'); document.body.append(l); } l.textContent = text; }
