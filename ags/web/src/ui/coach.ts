// Guidance for a first campaign: short tips pinned to the thing they explain, shown once each and never in the way of a
// card, plus the How to play card. Tips can be switched off, and back on from Settings.
import { el, esc, custom, icon } from './kit';
import { store } from '../store';

const KEY = 'ags-coach';
type State = { off: boolean; seen: string[] };
const state = (): State => { const s = store.get(KEY, null); return s && Array.isArray(s.seen) ? s : { off: false, seen: [] }; };
const save = (s: State) => store.set(KEY, s);
export function tipsOn(): boolean { return !state().off; }
export function setTips(on: boolean) { const s = state(); s.off = !on; if (on) s.seen = []; save(s); }

interface Tip { id: string; at: string; title: string; text: string; when: (v: any) => boolean }
const TIPS: Tip[] = [
  { id: 'goal', at: '.tally', title: 'Win 270 electoral votes', when: v => v.turn === 0,
    text: 'This bar is the race as it stands: the states leaning your way on the left, the rival\'s on the right. Election night is November 7, 2000. After it you govern, or lead the opposition, to the end of 2001.' },
  { id: 'seal', at: '.seal', title: 'The seal runs your turn', when: v => v.turn === 0,
    text: 'It names what is waiting: research to choose, what an HQ should build, a piece that needs orders. Click it to go there. When nothing is waiting, it ends the turn. You can also end early with the link beneath it.' },
  { id: 'unit', at: '.unitcard:not([hidden])', title: 'Move, then act', when: () => true,
    text: 'The lit hexes are where this piece can move: click one. Then choose an action; hover it first to see which hexes it will move. "Every turn" repeats an action so you need not give the same order twice.' },
  { id: 'map', at: '.lens', title: 'Read the map', when: v => v.turn >= 1 && !v.office_turn,
    text: 'The wash on each hex is how it leans: your colour or the rival\'s, stronger the safer. Elections are won in the close states with many votes. Click any hex for its state, and use these buttons to switch the map\'s view.' },
  { id: 'factions', at: '.tab[data-open="factions"]', title: 'Keep the blocs on side', when: v => v.turn >= 1,
    text: 'Nine voting blocs back one party or the other. Do what they ask and they turn out for you; ignore one for too long and it may run a candidate of its own.' },
  { id: 'notes', at: '.notes', title: 'What just happened', when: v => v.turn >= 2,
    text: 'Each turn\'s news lands here: the rival\'s moves, finished research, blocs changing sides. Click a note to go to it. The full turn report is in the menu.' },
];

let card: HTMLElement | null = null, shown = '';
const visible = (e: Element | null) => { if (!e) return false; const r = (e as HTMLElement).getBoundingClientRect(); return r.width > 0 && r.height > 0 && getComputedStyle(e as HTMLElement).visibility !== 'hidden'; };

/** Shows the next tip that applies, if nothing more important is on screen. Cheap enough to call on every refresh. */
export function coach(v: any) {
  const s = state(); const blocked = !v || v.over || v.pending || document.querySelector('.scrim, .binder, .side, .night, .toast');
  const tip = s.off || blocked ? null : TIPS.find(t => !s.seen.includes(t.id) && t.when(v) && visible(document.querySelector(t.at)));
  if (!tip) { if (card) { card.remove(); card = null; shown = ''; } return; }
  if (shown !== tip.id) {
    card?.remove(); shown = tip.id;
    card = el(`<aside class="coach card paper" role="note" aria-live="polite"><b>${esc(tip.title)}</b><p>${esc(tip.text)}</p>
      <div class="row"><button class="btn red" type="button" data-ok>Got it</button><button class="btn" type="button" data-off>Hide tips</button></div></aside>`);
    card.onclick = e => { const t = e.target as HTMLElement; if (!t.closest('button')) return; const st = state();
      if (t.closest('[data-off]')) st.off = true; else st.seen.push(shown); save(st); card?.remove(); card = null; shown = ''; coach(v); };
    document.body.append(card);
  }
  place(document.querySelector(tip.at) as HTMLElement);
}
function place(target: HTMLElement) {
  if (!card || !target) return;
  const r = target.getBoundingClientRect(), W = innerWidth, H = innerHeight, cw = Math.min(330, W - 24), ch = card.offsetHeight || 170;
  card.style.width = cw + 'px';
  const below = r.bottom + 12 + ch < H - 8, above = r.top - 12 - ch > 8;
  let x = r.left + r.width / 2 - cw / 2, y = below ? r.bottom + 12 : above ? r.top - 12 - ch : Math.max(8, Math.min(H - ch - 8, r.top));
  if (!below && !above) x = r.left - cw - 14 > 8 ? r.left - cw - 14 : r.right + 14;
  card.style.left = Math.max(12, Math.min(W - cw - 12, x)) + 'px'; card.style.top = Math.max(8, y) + 'px';
  card.dataset.side = below ? 'below' : above ? 'above' : 'beside';
}
addEventListener('resize', () => { if (card && shown) place(document.querySelector(TIPS.find(t => t.id === shown)!.at) as HTMLElement); });

/** The rules on one card, from the title screen and the in-game menu. */
export function howToPlay() {
  const sec = (ic: string, h: string, body: string) => `<section class="how"><span class="token">${icon(ic)}</span><div><h3>${h}</h3>${body}</div></section>`;
  const node = el(`<div class="ecard card paper" style="width:min(760px,100%)"><div class="card-in"><div class="ebody"><div class="kicker">The rules in brief</div><h2>How to play</h2>
    ${sec('trophy', 'The goal', '<p>Lead your party from January 2000. Win 270 electoral votes on November 7, 2000, then govern (or oppose) through 2001. The end card scores your legacy, which ranks you in the Hall of Presidents.</p>')}
    ${sec('flag', 'A turn', '<p>Move your pieces and act with them, choose what your HQs build and what your think tank studies, and answer the event cards. The red seal shows what is still waiting; when nothing is, it ends the turn. Turns are quarters, then fortnights in the autumn of the election.</p>')}
    ${sec('star', 'Your pieces', `<ul>
      <li><b>Candidate</b>: a stump speech moves a whole region; the campaign plane flies anywhere once a turn.</li>
      <li><b>Running mate and surrogates</b>: rallies move the hexes around them.</li>
      <li><b>Organizers</b>: canvass, open field offices that push every turn, and open new HQs in cities.</li>
      <li><b>Pollster</b>: shows a state's true numbers and feeds the think tank.</li>
      <li><b>Ad team</b>: buys a whole state, or an attack ad on a rival piece.</li>
      <li><b>Lawyers</b>: win contests, and decide recounts.</li></ul>`)}
    ${sec('people', 'Map and blocs', '<p>Every hex leans one way; a state goes to whoever leads it on election night. The public polls are fuzzy until you poll a state. Nine voting blocs pick sides: do what they ask to make them allies, and watch for one splitting off as a third party.</p>')}
    ${sec('money', 'What you spend', '<p><b>Money</b> buys pieces and ads. <b>Influence</b> buys doctrines and platform pledges. <b>Ideas</b> finish research. <b>Momentum</b> fills toward a surge that makes everything hit harder. Great moves build toward a legendary operative.</p>')}
    ${sec('gear', 'Keys', '<p>Arrow keys move the cursor and Enter chooses; N selects the next piece; Escape cancels; + and − zoom. Drag to pan; scroll or pinch to zoom.</p>')}
    <div class="efoot"><span class="note">Tips on the first turns point at each of these as you meet them.</span><button class="btn red" type="button">Back</button></div></div></div></div>`);
  const m = custom(node, 'How to play'); (node.querySelector('.btn.red') as HTMLElement).onclick = () => m.close();
}
