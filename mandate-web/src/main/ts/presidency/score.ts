// The budget office's score (docs/ECONOMY_MODEL.md, "On screen"): the plain reading first, then the numbers. Output,
// growth, jobs, unemployment and inflation at one, three and ten years, each with the range the evidence allows; the
// ten-year cost as the budget office scores it, and with interest and the economy's response; the prices, sectors
// and other outcomes it touches; and where the numbers come from. Values wear text colours; nothing is colour alone.
import { h } from '../core/dom.js';
import type { ScoreView } from '../core/types.js';

const MINUS = '−';

/** A signed figure: "+0.32", "−1.4", or "0" when it rounds to nothing. */
export function signed(v: number, digits = 2): string {
  const r = Number(v.toFixed(digits));
  if (r === 0) return '0';
  return (r > 0 ? '+' : MINUS) + Math.abs(r).toFixed(digits);
}

/** $ billions as "$40 billion" or "$1.2 trillion", signed by whether it adds to the deficit. */
export function money(bn: number): string {
  const a = Math.abs(bn);
  const text = a >= 995 ? '$' + (a / 1000).toFixed(1) + ' trillion' : '$' + Math.round(a >= 100 ? Math.round(a / 10) * 10 : a) + ' billion';
  return a < 5 ? 'almost nothing' : text;
}

function range(lo: number, hi: number, digits: number): string {
  return lo.toFixed(digits) === hi.toFixed(digits) ? '' : `${signed(lo, digits)} to ${signed(hi, digits)}`;
}

/** The score as a block: the reading, the numbers in a table, the budget, prices, sectors, outcomes and the basis. */
export function scoreView(sc: ScoreView, headingLevel: 'h3' | 'h4' = 'h3'): HTMLElement {
  const digits = (label: string) => (label === 'Jobs' ? 2 : 2);
  const b = sc.budget;
  const cost = b.direct > 0 ? `Costs ${money(b.direct)} over ten years` : b.direct < 0 ? `Saves ${money(-b.direct)} over ten years` : 'Costs almost nothing';
  const costRange = b.directLow !== b.directHigh ? ` (${money(Math.min(b.directLow, b.directHigh))} to ${money(Math.max(b.directLow, b.directHigh))} as its effects turn out)` : '';
  return h('section', { class: 'score', 'aria-label': 'The budget office’s score: ' + sc.title },
    h(headingLevel, { class: 'score-title' }, sc.title),
    h('p', { class: 'score-reading' }, sc.reading),
    h('p', { class: 'score-evidence muted small' }, `${sc.evidence} evidence: ${EVIDENCE_WORDS[sc.evidence] ?? ''}`),
    h('table', { class: 'score-table' },
      h('caption', { class: 'sr' }, 'The central estimate, with the range below it, after one, three and ten years'),
      h('thead', null, h('tr', null, h('th', { scope: 'col' }, 'Against no change'), ...sc.years.map(y => h('th', { scope: 'col' }, y === 1 ? 'After a year' : `After ${y} years`)))),
      h('tbody', null, ...sc.rows.map(r => h('tr', null,
        h('th', { scope: 'row' }, r.label, h('span', { class: 'score-unit' }, r.unit.trim())),
        ...r.central.map((c, i) => h('td', null,
          h('span', { class: 'score-central' }, signed(c, digits(r.label))),
          h('span', { class: 'score-range' }, range(r.low[i]!, r.high[i]!, digits(r.label))))))))),
    h('dl', { class: 'score-budget' },
      h('dt', null, 'Ten-year cost'), h('dd', null, cost + costRange),
      h('dt', null, 'With interest and the economy’s response'), h('dd', null, `${b.total >= 0 ? 'adds' : 'cuts'} ${money(b.total)} ${b.total >= 0 ? 'to' : 'from'} the deficit`),
      h('dt', null, 'Debt after ten years'), h('dd', null, `${signed(b.debt, 1)} points of GDP`),
      h('dt', null, 'The 10-year yield after five years'), h('dd', null, `${signed(b.tenYear, 2)} points`)),
    sc.prices.length || sc.sectors.length ? h('div', { class: 'score-touches' },
      sc.prices.length ? h('div', null, h('h5', null, 'Prices after five years'),
        h('ul', null, ...sc.prices.map(p => h('li', null, `${p.label} ${signed(p.value)}%`, range(p.low, p.high, 2) ? h('span', { class: 'score-range' }, ` (${range(p.low, p.high, 2)})`) : null)))) : null,
      sc.sectors.length ? h('div', null, h('h5', null, 'Industries after five years'),
        h('ul', null, ...sc.sectors.map(s => h('li', null, `${s.label} ${signed(s.value, 1)}${s.unit}`)))) : null) : null,
    sc.outcomes.length ? h('div', { class: 'score-touches' }, h('div', null, h('h5', null, 'Other outcomes at full effect (Resolute Desk’s estimates)'),
      h('ul', null, ...sc.outcomes.map(o => h('li', null, `${o.label.replace(/ \(.*\)/, '')} ${signed(o.value, 1)} ${o.label.match(/\((.*)\)/)?.[1] ?? ''}`))))) : null,
    sc.basis.length ? h('details', { class: 'score-basis' }, h('summary', null, 'Where the numbers come from'),
      h('ul', null, ...sc.basis.map(x => h('li', null, h('strong', null, x.policy + ' '), h('span', { class: 'muted' }, `(${x.evidence.toLowerCase()} evidence) `), x.basis)))) : null);
}

const EVIDENCE_WORDS: Record<string, string> = {
  Strong: 'the effects are well measured; the range is about a quarter either way.',
  Moderate: 'the studies agree on the direction; the range is about half either way.',
  Weak: 'few good studies; the range is wide.',
  Contested: 'economists disagree; it may do nothing, or twice what is claimed.',
};
