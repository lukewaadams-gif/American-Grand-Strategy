// The systems map (docs/TUTORIAL.md): the game's layers and how they move each other, as a diagram the tutorial lights up
// as each part is introduced, and the player can open at any time.
import { h, s } from '../core/dom.js';

export type NodeId = 'message' | 'money' | 'field' | 'team' | 'polls' | 'night' | 'approval' | 'economy' | 'congress' | 'press'
  | 'world' | 'midterms' | 'reelection';

interface Node { id: NodeId; label: string; x: number; y: number; group: 'campaign' | 'office' | 'next'; }

const NODES: Node[] = [
  { id: 'message', label: 'Message', x: 90, y: 60, group: 'campaign' },
  { id: 'money', label: 'Money & ads', x: 90, y: 120, group: 'campaign' },
  { id: 'field', label: 'Field', x: 90, y: 180, group: 'campaign' },
  { id: 'team', label: 'Team & surrogates', x: 90, y: 240, group: 'campaign' },
  { id: 'polls', label: 'The polls', x: 270, y: 150, group: 'campaign' },
  { id: 'night', label: 'Election night', x: 430, y: 150, group: 'campaign' },
  { id: 'approval', label: 'Approval', x: 600, y: 150, group: 'office' },
  { id: 'economy', label: 'The economy', x: 600, y: 60, group: 'office' },
  { id: 'congress', label: 'Congress', x: 760, y: 60, group: 'office' },
  { id: 'press', label: 'West Wing & press', x: 760, y: 150, group: 'office' },
  { id: 'world', label: 'The world & crises', x: 760, y: 240, group: 'office' },
  { id: 'midterms', label: '2030 midterms', x: 920, y: 100, group: 'next' },
  { id: 'reelection', label: '2032 re-election', x: 920, y: 200, group: 'next' },
];

/** Who moves whom: the arrows. */
const EDGES: [NodeId, NodeId][] = [
  ['message', 'polls'], ['money', 'polls'], ['field', 'polls'], ['team', 'polls'], ['money', 'field'], ['polls', 'night'],
  ['night', 'approval'], ['night', 'congress'], ['economy', 'approval'], ['congress', 'economy'], ['congress', 'approval'],
  ['press', 'approval'], ['world', 'economy'], ['world', 'approval'], ['approval', 'congress'], ['approval', 'midterms'],
  ['midterms', 'congress'], ['approval', 'reelection'], ['economy', 'reelection'],
];

/** The map, with the lit parts bright and the named ones ringed. */
export function systemsMap(lit: Set<NodeId>, focus: NodeId[] = []): SVGSVGElement {
  const at = (id: NodeId) => NODES.find(n => n.id === id)!;
  const svg = s('svg', { class: 'tut-map', viewBox: '0 0 1010 300', role: 'img',
    'aria-label': 'How the game fits together: the campaign moves the polls, the polls decide election night, and in office approval, the economy, Congress, the press and the world move each other and the next elections.' });
  for (const [label, x] of [['The campaign', 90], ['In office', 680], ['What comes next', 920]] as const)
    svg.append(s('text', { x, y: 18, class: 'tut-map-head', 'text-anchor': 'middle' }, label));
  for (const [a, b] of EDGES) {
    const p = at(a), q = at(b), on = lit.has(a) && lit.has(b);
    svg.append(s('line', { x1: p.x + 62, y1: p.y, x2: q.x - 62, y2: q.y, class: 'tut-edge' + (on ? ' on' : ''), 'marker-end': on ? 'url(#tut-arrow-on)' : 'url(#tut-arrow)' }));
  }
  const defs = s('defs');
  for (const [id, cls] of [['tut-arrow', 'tut-arrowhead'], ['tut-arrow-on', 'tut-arrowhead on']])
    defs.append(s('marker', { id, viewBox: '0 0 10 10', refX: 9, refY: 5, markerWidth: 7, markerHeight: 7, orient: 'auto-start-reverse' },
      s('path', { d: 'M0,0 L10,5 L0,10 z', class: cls })));
  svg.prepend(defs);
  for (const n of NODES) {
    const on = lit.has(n.id), f = focus.includes(n.id);
    const g = s('g', { class: `tut-node ${n.group}${on ? ' on' : ''}${f ? ' focus' : ''}` });
    g.append(s('rect', { x: n.x - 62, y: n.y - 18, width: 124, height: 36, rx: 8 }));
    g.append(s('text', { x: n.x, y: n.y + 5, 'text-anchor': 'middle' }, n.label));
    svg.append(g);
  }
  return svg;
}

/** The map in a card, for the coach's "The map" button. */
export function mapCard(lit: Set<NodeId>, focus: NodeId[], close: () => void): HTMLElement {
  return h('div', { class: 'dialog-card tut-map-card' },
    h('div', { class: 'eyebrow' }, 'The tutorial'),
    h('h2', { class: 'dialog-title' }, 'How the game fits together'),
    h('p', { class: 'muted' }, 'Each box is a part of the game; each arrow, something that moves another. The parts you have met are lit.'),
    systemsMap(lit, focus),
    h('div', { class: 'dialog-foot' }, h('button', { class: 'btn primary', type: 'button', onclick: close }, 'Back')));
}
