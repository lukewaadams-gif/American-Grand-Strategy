// The campaign's data graphics: the national trend with its error band, the race to 270 by rating, and the
// path to 270 (states lined up from your strongest to theirs, the tipping point marked). Each states its content
// in words for assistive technology, and charts draw at their real width so text stays at true size.
import type { CampaignView, StateView } from '../core/types.js';
import { h, s } from '../core/dom.js';
import { day, margin, signed } from '../core/format.js';
import { inkOn, marginColour, palette } from './palette.js';

const DSIDE = (v: CampaignView) => v.player.party === 'D';

// ---- the national trend ---------------------------------------------------------------------------------------

/** The player's national lead week by week, with a band of one typical polling error either side. */
export function trendChart(v: CampaignView, height = 220): HTMLElement {
  const fig = h('figure', { class: 'chart trend' });
  const weeks = v.polls;
  const last = weeks[weeks.length - 1];
  const summary = weeks.length < 2 ? 'The first national poll arrives after the first week.'
    : `Your national lead from ${signed(weeks[0]!.national)} to ${signed(last!.national)}; polls usually miss by about ${v.errorSd.toFixed(1)} points either way.`;
  const svg = s('svg', { role: 'img', 'aria-label': 'Chart: ' + summary, height, width: '100%' });
  fig.append(svg, h('figcaption', { class: 'chart-caption' }, summary));
  const draw = (width: number) => {
    svg.replaceChildren();
    svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
    if (weeks.length < 2) {
      svg.append(s('text', { x: width / 2, y: height / 2, class: 'chart-empty', 'text-anchor': 'middle' }, 'The first poll arrives after the first week.'));
      return;
    }
    const lo0 = Math.min(...weeks.map(w => w.national - v.errorSd), -1), hi0 = Math.max(...weeks.map(w => w.national + v.errorSd), 1);
    const step = niceStep((hi0 - lo0) / 4), lo = Math.floor(lo0 / step) * step, hi = Math.ceil(hi0 / step) * step;
    const x0 = 44, x1 = width - 56, y0 = 14, y1 = height - 26;
    const X = (i: number) => x0 + ((x1 - x0) * i) / (weeks.length - 1), Y = (val: number) => y1 - ((val - lo) / (hi - lo)) * (y1 - y0);
    for (let t = lo; t <= hi + 1e-9; t += step) {
      svg.append(s('line', { x1: x0, x2: x1, y1: Y(t), y2: Y(t), class: Math.abs(t) < 1e-9 ? 'grid zero' : 'grid' }),
        s('text', { x: x0 - 8, y: Y(t) + 4, class: 'tick', 'text-anchor': 'end' }, Math.abs(t) < 1e-9 ? 'Tied' : signed(t, step < 1 ? 1 : 0)));
    }
    const band = weeks.map((w, i) => `${X(i)},${Y(w.national + v.errorSd)}`).concat(weeks.map((w, i) => `${X(i)},${Y(w.national - v.errorSd)}`).reverse());
    svg.append(s('polygon', { points: band.join(' '), class: 'band', fill: mixParty(v, 0.22) }));
    const line = s('polyline', { points: weeks.map((w, i) => `${X(i)},${Y(w.national)}`).join(' '), class: 'line', stroke: partyMid(v) });
    svg.append(line);
    const lx = X(weeks.length - 1), ly = Y(last!.national);
    svg.append(s('circle', { cx: lx, cy: ly, r: 4, fill: partyMid(v) }),
      s('text', { x: lx + 8, y: ly + 4, class: 'end-label', fill: 'currentColor' }, signed(last!.national)));
    const idx = [0, Math.floor((weeks.length - 1) / 2), weeks.length - 1];
    for (const i of new Set(idx)) svg.append(s('text', { x: X(i), y: height - 6, class: 'tick', 'text-anchor': i === 0 ? 'start' : i === weeks.length - 1 ? 'end' : 'middle' }, day(weeks[i]!.date)));
    weeks.forEach((w, i) => svg.append(s('rect', { x: X(i) - (x1 - x0) / (weeks.length - 1) / 2, y: y0, width: (x1 - x0) / (weeks.length - 1), height: y1 - y0, class: 'hit' },
      s('title', null, `${day(w.date)}: you ${signed(w.national)}, ${w.ev} electoral votes, ${Math.round(w.win * 100)}% to win`))));
  };
  new ResizeObserver(es => { const w = Math.round(es[0]!.contentRect.width); if (w > 0) draw(w); }).observe(fig);
  return fig;
}

function niceStep(raw: number): number {
  const mag = Math.pow(10, Math.floor(Math.log10(Math.max(raw, 1e-6))));
  for (const m of [1, 2, 2.5, 5, 10]) if (m * mag >= raw) return m * mag;
  return 10 * mag;
}
const partyMid = (v: CampaignView) => (DSIDE(v) ? palette().d.mid : palette().r.mid);
const mixParty = (v: CampaignView, a: number) => (DSIDE(v) ? hexA(palette().d.mid, a) : hexA(palette().r.mid, a));
function hexA(hex: string, a: number): string { const n = parseInt(hex.slice(1), 16); return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`; }

// ---- the race to 270 ------------------------------------------------------------------------------------------

type Rating = 'solid' | 'likely' | 'lean' | 'toss';
function rating(lead: number): Rating { const a = Math.abs(lead); return a >= 8 ? 'solid' : a >= 4 ? 'likely' : a >= 1 ? 'lean' : 'toss'; }

/** Electoral votes by rating, Democrats from the left, Republicans from the right, toss-ups in the middle. */
export function raceTo270(v: CampaignView): HTMLElement {
  const sum = (party: 'D' | 'R', r: Rating) => v.states.filter(st => (party === 'D' ? st.margin > 0 : st.margin < 0) && rating(st.margin) === r && r !== 'toss')
    .reduce((a, st) => a + st.ev, 0);
  const toss = v.states.filter(st => rating(st.margin) === 'toss').reduce((a, st) => a + st.ev, 0);
  const p = palette();
  const seg = (ev: number, fill: string, label: string) => (ev > 0 ? h('div', { class: 'seg', style: `flex-basis:${(ev / 538) * 100}%;background:${fill};color:${inkOn(fill)}`, title: `${label}: ${ev}` }, ev >= 30 ? String(ev) : '') : null);
  const d = { solid: sum('D', 'solid'), likely: sum('D', 'likely'), lean: sum('D', 'lean') }, r = { solid: sum('R', 'solid'), likely: sum('R', 'likely'), lean: sum('R', 'lean') };
  const dTotal = d.solid + d.likely + d.lean, rTotal = r.solid + r.likely + r.lean;
  const dName = DSIDE(v) ? v.player.name : v.opponent.name, rName = DSIDE(v) ? v.opponent.name : v.player.name;
  return h('div', { class: 'race', role: 'img', 'aria-label': `By this week's polls: ${dName} ${dTotal} electoral votes, ${rName} ${rTotal}, ${toss} toss-up. 270 to win.` },
    h('div', { class: 'race-head' },
      h('div', { class: 'race-side d' }, h('span', { class: 'race-num' }, String(dTotal)), h('span', { class: 'race-name' }, dName)),
      h('div', { class: 'race-mid' }, '270 to win'),
      h('div', { class: 'race-side r' }, h('span', { class: 'race-name' }, rName), h('span', { class: 'race-num' }, String(rTotal)))),
    h('div', { class: 'race-bar' },
      seg(d.solid, p.d.solid, 'Solid Democratic'), seg(d.likely, p.d.mid, 'Likely Democratic'), seg(d.lean, p.d.light, 'Lean Democratic'),
      seg(toss, p.toss, 'Toss-up'),
      seg(r.lean, p.r.light, 'Lean Republican'), seg(r.likely, p.r.mid, 'Likely Republican'), seg(r.solid, p.r.solid, 'Solid Republican'),
      h('div', { class: 'race-line', 'aria-hidden': 'true' })),
    h('div', { class: 'race-key' }, 'Solid 8+ · Likely 4–8 · Lean 1–4 · Toss-up within a point'));
}

// ---- the path to 270 ------------------------------------------------------------------------------------------

/** States from your strongest to theirs, each as wide as its electoral votes; the 270th vote marks the tipping point. */
export function pathTo270(v: CampaignView, selected: string | null, onSelect: (code: string) => void): HTMLElement {
  const order = [...v.states].sort((a, b) => b.forPlayer - a.forPlayer);
  let acc = 0, tipping: StateView | null = null;
  for (const st of order) { acc += st.ev; if (!tipping && acc >= 270) tipping = st; }
  const row = h('div', { class: 'snake-row' });
  acc = 0;
  for (const st of order) {
    const fill = marginColour(st.margin), isTip = st === tipping;
    acc += st.ev;
    row.append(h('button', {
      class: 'snake-seg' + (isTip ? ' tip' : '') + (st.code === selected ? ' selected' : ''), type: 'button', tabindex: -1,
      style: `flex:${st.ev} 1 0;background:${fill};color:${inkOn(fill)}`, title: `${st.name} (${st.ev}): ${margin(st.margin)}${isTip ? ' — the tipping point' : ''}`,
      'aria-label': `${st.name}, ${st.ev}: ${margin(st.margin)}`, onclick: () => onSelect(st.code),
    }, st.ev >= 9 ? st.code : ''));
  }
  const tipText = tipping ? `${tipping.name} is the tipping point: the state that would give you your 270th vote, where you ${tipping.forPlayer >= 0 ? 'lead' : 'trail'} by ${Math.abs(tipping.forPlayer).toFixed(1)}.` : '';
  return h('div', { class: 'snake', role: 'group', 'aria-label': 'Path to 270, from your strongest state to theirs. ' + tipText },
    h('div', { class: 'snake-ends' }, h('span', null, 'Your strongest'), h('span', null, 'Theirs')),
    row,
    h('div', { class: 'snake-mark', style: `left:${(270 / 538) * 100}%` }, h('span', null, '270')),
    h('p', { class: 'snake-note' }, tipText));
}
