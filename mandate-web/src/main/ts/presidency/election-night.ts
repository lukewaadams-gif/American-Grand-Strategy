// Election night: a broadcast of the result the engine computed once, on Election Day (broadcast.ts writes the night's
// script from it; nothing here recomputes it). Play, pause, the next report, speed and skip to the result; the race
// calls, the electoral count, the map and the battlegrounds follow the broadcast's clock. Its place in the night is
// kept for the session, so a reload picks up where it was; watching it to the end tells the engine
// (ElectionNightSeen), and only then does the game go on.
import { h, s } from '../core/dom.js';
import { day } from '../core/format.js';
import type { ElectionNightView } from '../core/types.js';
import { portrait } from '../art/portrait.js';
import { inkOn, legible, mix, palette } from '../campaign/palette.js';
import { CALLOUTS, STATES, VIEWBOX } from '../generated/usStates.js';
import { clock, counted, isCalled, plan, shown, tally, type Line } from './broadcast.js';

export interface ElectionNightActions {
  view(): ElectionNightView;
  /** The night is over: the engine records it, and the game goes on (the transition, governing, or the end). */
  done(): void;
}

const NEXT: Record<ElectionNightView['next'], string> = {
  transition: 'On to the transition',
  end: 'To the final report',
  second: 'Back to the Oval Office',
  lameduck: 'Back to the Oval Office',
};
const SPEEDS = [{ label: '1×', ms: 2400 }, { label: '2×', ms: 1200 }, { label: '4×', ms: 550 }];

export function electionNight(act: ElectionNightActions): HTMLElement {
  const v = act.view(), n = plan(v), last = n.beats.length - 1;
  const key = `ags-night:${v.year}:${v.electionDay}:${v.result.dem}-${v.result.rep}:${v.player.id}`;
  let at = 0;
  try { at = Math.max(0, Math.min(last, Number(sessionStorage.getItem(key) ?? '0') || 0)); } catch { /* no storage: from the top */ }
  let playing = at < last, speed = 0, timer = 0;
  const root = h('main', { class: 'en', id: 'main', 'aria-label': `Election night ${v.year}` });
  const p = palette();
  const name = (q: 'D' | 'R') => (q === v.player.party ? v.player.name : v.opponent.name);
  const id = (q: 'D' | 'R') => (q === v.player.party ? v.player.id : v.opponent.id);
  const lookOf = (q: 'D' | 'R') => (q === v.player.party ? v.player.look : v.opponent.look);

  const keep = () => { try { sessionStorage.setItem(key, String(at)); } catch { /* fine */ } };
  const stop = () => { if (timer) { window.clearInterval(timer); timer = 0; } };
  const start = () => {
    stop();
    if (!playing) return;
    timer = window.setInterval(() => {
      if (!root.isConnected) { stop(); return; }
      if (at >= last) { playing = false; stop(); draw(); return; }
      at++; keep(); draw();
    }, SPEEDS[speed]!.ms);
  };
  const go = (to: number) => { at = Math.max(0, Math.min(last, to)); if (at >= last) playing = false; keep(); draw(); start(); };

  function fill(l: Line, t: number): string {
    const s = shown(l, t);
    if (isCalled(l, t)) return l.margin >= 0 ? p.d.solid : p.r.solid;
    if (s === null) return t >= l.close ? '#3a4352' : '#262d38';
    return legible(mix(p.toss, s >= 0 ? p.d.light : p.r.light, Math.min(1, 0.35 + Math.abs(s) / 10)));
  }

  function map(t: number): SVGSVGElement {
    const byCode = new Map(n.lines.map(l => [l.code, l]));
    const called = n.lines.filter(l => isCalled(l, t));
    const svg = s('svg', { class: 'en-map usmap', viewBox: `0 0 1090 ${VIEWBOX.height}`, role: 'img',
      'aria-label': `The map at ${clock(t)}: ${called.length} of ${n.lines.length} states called. The battleground table below lists the closest.` });
    const callout = new Set(CALLOUTS.map(c => c.code));
    for (const shape of STATES) {
      const l = byCode.get(shape.code);
      if (!l) continue;
      const f = fill(l, t), sh = shown(l, t);
      svg.append(s('path', { class: 'state', d: shape.d, fill: f, stroke: '#0b1018', 'stroke-width': '0.8' },
        s('title', null, `${l.name} (${l.ev}): ${isCalled(l, t) ? `called for ${name(l.margin >= 0 ? 'D' : 'R')}` : sh === null ? (t >= l.close ? 'polls closed, no votes counted' : 'polls open') : `${name(sh >= 0 ? 'D' : 'R')} leads by ${Math.abs(sh).toFixed(1)}, ${Math.round(counted(l, t) * 100)}% counted`}`)));
      if (!callout.has(shape.code) && shape.area > 900)
        svg.append(s('text', { class: 'en-code', x: String(shape.label[0]), y: String(shape.label[1] + 4), fill: inkOn(f), 'text-anchor': 'middle' }, shape.code));
    }
    CALLOUTS.forEach((c, i) => {
      const l = byCode.get(c.code);
      if (!l) return;
      const f = fill(l, t), y = 150 + i * 30;
      svg.append(s('line', { x1: String(c.anchor[0]), y1: String(c.anchor[1]), x2: '1004', y2: String(y), class: 'leader' }),
        s('rect', { x: '1004', y: String(y - 11), width: '56', height: '22', rx: '2', fill: f }),
        s('text', { class: 'en-code', x: '1032', y: String(y + 5), fill: inkOn(f), 'text-anchor': 'middle' }, c.code));
    });
    return svg;
  }

  function side(q: 'D' | 'R', votes: number, share: number): HTMLElement {
    const proj = n.beats.findIndex(b => b.kind === 'projection');
    const winner = n.winner === q && (proj >= 0 ? at >= proj : at >= last);
    return h('div', { class: 'en-side ' + q.toLowerCase() + (winner ? ' won' : '') },
      portrait(id(q), q, 72, true, lookOf(q)),
      h('div', { class: 'en-ev', 'aria-label': `${name(q)}: ${votes} electoral votes` }, String(votes)),
      h('div', { class: 'en-who' }, h('span', { class: 'en-name' }, name(q) + (id(q) === v.player.id ? ' (you)' : '')),
        h('span', { class: 'muted small' }, q === 'D' ? 'Democrat' : 'Republican'), h('span', { class: 'muted small' }, `${share.toFixed(1)}% of the vote`)));
  }

  function draw(): void {
    const beat = n.beats[at]!, t = beat.t, tl = tally(n, v, t), final = at >= last;
    const bar = h('div', { class: 'en-bar', role: 'img', 'aria-label': `${name('D')} ${tl.dem}, ${name('R')} ${tl.rep} electoral votes called; 270 to win` },
      h('span', { class: 'en-bar-d', style: `width:${(tl.dem / 538) * 100}%` }), h('span', { class: 'en-bar-r', style: `width:${(tl.rep / 538) * 100}%` }),
      h('span', { class: 'en-270', 'aria-hidden': 'true' }, '270'));
    const controls = h('div', { class: 'en-controls', role: 'group', 'aria-label': 'The broadcast' },
      h('button', { class: 'btn', type: 'button', disabled: final, 'aria-pressed': String(playing), onclick: () => { playing = !playing; draw(); start(); } }, playing ? 'Pause' : 'Play'),
      h('button', { class: 'btn', type: 'button', disabled: final, onclick: () => { playing = false; stop(); go(at + 1); } }, 'Next report'),
      h('button', { class: 'btn', type: 'button', disabled: final, title: 'How fast the reports come', onclick: () => { speed = (speed + 1) % SPEEDS.length; draw(); start(); } }, 'Speed ' + SPEEDS[speed]!.label),
      h('button', { class: 'btn', type: 'button', disabled: final, onclick: () => { playing = false; stop(); go(last); } }, 'Skip to the result'),
      h('button', { class: final ? 'btn primary en-continue' : 'btn en-continue', type: 'button', onclick: () => { stop(); try { sessionStorage.removeItem(key); } catch { /* fine */ } act.done(); } },
        final ? NEXT[v.next] : 'Leave the broadcast'));
    const feed = h('ol', { class: 'en-feed', 'aria-label': 'Reports, newest first', reversed: 'reversed', tabindex: '0' },
      ...n.beats.slice(0, at + 1).reverse().slice(0, 14).map((b, i) => h('li', { class: 'en-beat ' + b.kind + (i === 0 ? ' now' : '') },
        h('span', { class: 'en-time' }, b.t >= 0 && b.kind !== 'final' && b.kind !== 'popular' && b.kind !== 'speech' && b.kind !== 'analysis' ? clock(b.t) : 'Final'),
        h('strong', null, b.title), b.text ? h('span', null, b.text) : null)));
    const battle = n.lines.filter(l => l.battleground).sort((a, b) => b.ev - a.ev);
    const board = h('table', { class: 'en-board' },
      h('caption', null, 'The battlegrounds'),
      h('thead', null, h('tr', null, ...['State', 'Electoral votes', 'Counted', 'Margin', 'Status'].map(x => h('th', { scope: 'col' }, x)))),
      h('tbody', null, ...battle.map(l => {
        const sh = shown(l, t), c = counted(l, t);
        return h('tr', { class: isCalled(l, t) ? 'called' : '' },
          h('th', { scope: 'row' }, l.name), h('td', null, String(l.ev)), h('td', null, t < l.close ? 'Polls open' : `${Math.round(c * 100)}%`),
          h('td', { class: sh === null ? '' : sh >= 0 ? 'd' : 'r' }, sh === null ? '—' : `${sh >= 0 ? 'D' : 'R'} +${Math.abs(sh).toFixed(1)}`),
          h('td', null, isCalled(l, t) ? `Called: ${name(l.margin >= 0 ? 'D' : 'R')}` : sh === null ? '' : Math.abs(sh) < 2 ? 'Too close to call' : `${name(sh >= 0 ? 'D' : 'R')} leads`));
      })));
    root.replaceChildren(
      h('header', { class: 'en-head' },
        h('div', null, h('div', { class: 'eyebrow' }, `Election night · ${day(v.electionDay, true)}`), h('h1', { class: 'dialog-title' }, final ? (v.won ? (v.year > 2028 ? 'Four more years' : 'You win the presidency') : (v.year > 2028 ? 'You lose re-election' : 'You lose')) : `America decides ${v.year}`)),
        h('div', { class: 'en-clock', 'aria-live': 'off' }, final ? 'Final' : clock(t), !final ? h('span', { class: 'en-live' }, 'LIVE') : null)),
      h('section', { class: 'en-score', 'aria-label': 'The electoral count' }, side('D', tl.dem, tl.demShare), h('div', { class: 'en-mid' }, bar,
        h('p', { class: 'muted small' }, `${Math.round(tl.counted * 100)}% of the expected vote counted · ${tl.votes.toFixed(1)} million votes`)), side('R', tl.rep, tl.repShare)),
      controls,
      h('div', { class: 'en-now', role: 'status', 'aria-live': 'polite' }, h('strong', null, beat.title), beat.text ? ' ' + beat.text : ''),
      h('div', { class: 'en-grid' }, h('div', { class: 'en-mapcard' }, map(t), legend()), feed),
      final ? analysis() : h('div', { class: 'en-board-wrap', role: 'region', 'aria-label': 'The battlegrounds', tabindex: '0' }, board));
  }

  function legend(): HTMLElement {
    const key = (c: string, t: string) => h('li', null, h('span', { class: 'en-key', style: `background:${c}`, 'aria-hidden': 'true' }), t);
    return h('ul', { class: 'en-legend' }, key(p.d.solid, `Called for ${name('D')}`), key(p.r.solid, `Called for ${name('R')}`),
      key(mix(p.toss, p.d.light, 0.7), 'Leading in the count'), key('#3a4352', 'Polls closed, not yet counted'), key('#262d38', 'Polls open'));
  }

  function analysis(): HTMLElement {
    const closest = [...n.lines].sort((a, b) => Math.abs(a.margin) - Math.abs(b.margin)).slice(0, 6);
    const flipsD = n.lines.filter(l => l.margin >= 0 && l.baseline < 0), flipsR = n.lines.filter(l => l.margin < 0 && l.baseline >= 0);
    const tip = n.lines.find(l => l.code === v.result.tipping);
    return h('section', { class: 'room-card en-analysis', 'aria-labelledby': 'en-an' },
      h('h2', { class: 'room-card-title', id: 'en-an' }, 'The analysis'),
      h('dl', { class: 'ww-stats' },
        h('dt', null, 'The electoral vote'), h('dd', null, `${name('D')} ${v.result.dem}, ${name('R')} ${v.result.rep}`),
        h('dt', null, 'The popular vote'), h('dd', null, `${v.result.popular >= 0 ? name('D') : name('R')} by ${Math.abs(v.result.popular).toFixed(1)} points`),
        h('dt', null, 'The tipping-point state'), h('dd', null, tip ? `${tip.name}, ${tip.margin >= 0 ? 'D' : 'R'} +${Math.abs(tip.margin).toFixed(1)}` : v.result.tipping),
        h('dt', null, 'The closest states'), h('dd', null, closest.map(l => `${l.name} ${l.margin >= 0 ? 'D' : 'R'} +${Math.abs(l.margin).toFixed(1)}`).join(' · ')),
        h('dt', null, 'Flipped since 2024'), h('dd', null, [flipsD.length ? `to the Democrats: ${flipsD.map(l => l.name).join(', ')}` : '', flipsR.length ? `to the Republicans: ${flipsR.map(l => l.name).join(', ')}` : ''].filter(Boolean).join('; ') || 'No state changed hands'),
        h('dt', null, 'Congress'), h('dd', null, v.congress)),
      v.postMortem.length ? h('div', null, h('h3', { class: 'tw-sub' }, 'What happened'), h('ul', { class: 'hill-list' }, ...v.postMortem.map(x => h('li', null, x)))) : null);
  }

  root.addEventListener('keydown', e => {
    if (e.target instanceof HTMLButtonElement && (e.key === ' ' || e.key === 'Enter')) return;
    if (e.key === ' ') { e.preventDefault(); playing = !playing && at < last; draw(); start(); }
    else if (e.key === 'ArrowRight') { e.preventDefault(); playing = false; stop(); go(at + 1); }
    else if (e.key === 'End') { e.preventDefault(); playing = false; stop(); go(last); }
  });
  draw();
  start();
  return root;
}
