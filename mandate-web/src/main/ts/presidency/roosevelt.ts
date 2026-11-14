// The Roosevelt Room (Resolute Desk's HillUI; docs/PRESIDENCY_PORT.md, milestone 5): Congress as the president sees it
// from across the hall. The Hill: the two chambers by bloc, the relationships, the week's hours on the Hill and what is
// waiting. Bills: the president's drafts and bills with the whip count on each, the deals that move votes, Congress's
// own bills, and what can be drafted. Members: the leaders, the chairs and the swing votes. The budget: the request,
// the twelve appropriations, stopgaps, shutdowns and the debt limit. Elections: the Senate races and the House by state.
// Favours and investigations. Every control issues one Hill order; the room redraws from the engine after each.
import { h } from '../core/dom.js';
import { day } from '../core/format.js';
import type { HillBill, HillBloc, HillMember, HillView, HillWhip } from '../core/types.js';
import { palette } from '../campaign/palette.js';

export interface HillActions {
  view(): HillView;
  /** Applies one Hill order; false if it was refused (the refusal is shown). */
  order(o: object): boolean;
  /** Opens what is waiting on the desk. */
  decide(): void;
  back(): void;
  /** The exit button's words (the Oval Office's by default; the war room says its own). */
  backLabel?: string;
}

const TABS = ['The Hill', 'Bills', 'Members', 'The budget', 'Elections', 'Favours and probes'] as const;
type Tab = typeof TABS[number];

const pct = (x: number) => Math.round(x * 100) + '%';
const partyName = (p: string) => (p === 'D' ? 'Democrats' : 'Republicans');
const DEALS: { kind: string; label: string; detail: string }[] = [
  { kind: 'CALL', label: 'Call', detail: 'A call from the president: a small push, and warmer relations. One hour.' },
  { kind: 'PROJECT', label: 'A project', detail: 'Money for something at home. Worth most to members who want a project; fiscal hawks notice. One hour.' },
  { kind: 'APPOINTMENT', label: 'An appointment', detail: 'A job for an ally. Uses one of your appointments. One hour.' },
  { kind: 'ENDORSE', label: 'An endorsement', detail: 'Your support at the next election: strong with your own party, and a promise to visit their state. One hour.' },
  { kind: 'THREAT', label: 'Lean on them', detail: 'Pressure: it can move your own party, it costs the relationship and it may leak. One hour.' },
  { kind: 'FAVOR', label: 'Call in a favour', detail: 'They owe you: the strongest push there is, and it costs no time.' },
];

/** The Roosevelt Room. It keeps its tab and the bill in focus as it redraws after each order. */
export function rooseveltRoom(act: HillActions, first: Tab = 'The Hill'): HTMLElement {
  let tab: Tab = first;
  let focus: string | null = null;
  const root = h('div', { class: 'room hill' });
  const order = (o: object) => { if (act.order(o)) draw(); };

  function draw(): void {
    const v = act.view();
    const keep = document.activeElement instanceof HTMLElement && root.contains(document.activeElement)
      ? document.activeElement.getAttribute('aria-label') ?? document.activeElement.textContent : null;
    const top = h('div', { class: 'room-top' },
      h('div', null, h('div', { class: 'eyebrow' }, 'Across the hall'), h('h2', { class: 'dialog-title' }, 'The Roosevelt Room'),
        h('p', { class: 'muted' }, (v.congress ? v.congress + ', week of ' : 'Week of ') + day(v.date, true))),
      h('button', { class: 'btn primary', type: 'button', onclick: act.back }, act.backLabel ?? 'Back to the Oval Office'));
    if (!v.blocs) {
      root.replaceChildren(top, h('p', null, v.reason ?? 'Congress is not in session.'));
      return;
    }
    const nav = h('nav', { class: 'cab-tabs', 'aria-label': 'The Roosevelt Room' }, ...TABS.map(t =>
      h('button', { class: 'btn small toggle' + (t === tab ? ' on' : ''), type: 'button', 'aria-pressed': String(t === tab), onclick: () => { tab = t; draw(); } },
        t, t === 'Bills' && (v.bills ?? []).some(b => b.stageKey === 'DESK') ? h('span', { class: 'pill tone-warn' }, 'on your desk') : null)));
    const closed = !v.open ? h('p', { class: 'hill-closed', role: 'note' }, v.reason ?? '') : null;
    const body = tab === 'The Hill' ? overview(v) : tab === 'Bills' ? billsTab(v) : tab === 'Members' ? membersTab(v)
      : tab === 'The budget' ? budgetTab(v) : tab === 'Elections' ? electionsTab(v) : troubleTab(v);
    root.replaceChildren(top, nav, ...(closed ? [closed] : []), body);
    if (keep) [...root.querySelectorAll<HTMLElement>('button, input, select')].find(e => (e.getAttribute('aria-label') ?? e.textContent) === keep)?.focus({ preventScroll: true });
  }

  // ---- The Hill ------------------------------------------------------------------------------------------------------

  function overview(v: HillView): HTMLElement {
    const blocs = v.blocs!;
    const mine = v.president!;
    const myParty = mine === 'D' ? 'Democratic' : 'Republican';
    const canNuke = v.open && !v.filibusterGone && (mine === 'D' ? v.senateDem! >= 50 : 100 - v.senateDem! >= 50);
    return h('div', { class: 'hill-body' },
      h('div', { class: 'room-grid two' },
        h('section', { class: 'room-card', 'aria-labelledby': 'hill-ch' },
          h('h3', { class: 'room-card-title', id: 'hill-ch' }, 'The two chambers'),
          chamber('House', blocs.filter(b => b.chamber === 'House'), 435, v.houseMajority!, 218),
          chamber('Senate', blocs.filter(b => b.chamber === 'Senate'), 100, v.senateMajority!, v.filibusterGone ? 51 : 60),
          h('p', { class: 'muted small' }, (v.unified ? `Unified government: your party holds both chambers.` : `Divided government: the other party holds ${v.houseMajority !== mine && v.senateMajority !== mine ? 'both chambers' : v.houseMajority !== mine ? 'the House' : 'the Senate'}.`)
            + (v.filibusterGone ? ' The filibuster is gone: a simple majority passes bills in the Senate.' : ' The filibuster stands: most bills need 60 votes in the Senate; reconciliation needs 50.'))),
        h('section', { class: 'room-card', 'aria-labelledby': 'hill-wk' },
          h('h3', { class: 'room-card-title', id: 'hill-wk' }, 'This week on the Hill'),
          h('dl', { class: 'cab-facts' },
            h('dt', null, 'Hours on the Hill'), h('dd', null, `${v.hoursLeft} of ${v.hoursMax} left. Each Hill meeting on your calendar adds 2.5; a call or a deal takes one, a meeting with a leader two.`),
            h('dt', null, 'Appointments to give'), h('dd', null, `${v.appointments}. One comes back every four weeks.`),
            h('dt', null, 'The mandate'), h('dd', null, `${v.mandate} of 15. Your win's pull on Congress; it fades week by week.`),
            h('dt', null, 'So far'), h('dd', null, `${v.laws} ${v.laws === 1 ? 'law' : 'laws'}, ${v.vetoes} ${v.vetoes === 1 ? 'veto' : 'vetoes'}, ${v.overrides} overridden. Promises: ${v.promisesKept} kept, ${v.promisesBroken} broken.`)),
          h('div', { class: 'cab-buttons' },
            h('button', { class: 'btn small toggle' + (v.delegated ? ' on' : ''), type: 'button', 'aria-pressed': String(!!v.delegated), disabled: !v.open,
              title: 'The legislative affairs staff whip your bills with the deals each member wants, answer what comes to the desk the safe way, and send the budget.',
              onclick: () => order({ type: 'Delegate', on: !v.delegated }) }, 'The Hill: ', h('strong', null, v.delegated ? 'the staff' : 'you')),
            canNuke ? h('button', { class: 'btn small', type: 'button', title: 'Three hours. Your party’s senators must agree; moderates resist, and a failure costs you.',
              onclick: () => order({ type: 'EndFilibuster' }) }, 'Ask the Senate to end the filibuster') : null),
          h('p', { class: 'muted small' }, `Relationships are 0 to 100. Meetings on the Hill warm every bloc a little; your approval pulls the ${myParty} blocs toward you and the others half as much.`))),
      h('div', { class: 'room-grid two' },
        h('section', { class: 'room-card', 'aria-labelledby': 'hill-wait' },
          h('h3', { class: 'room-card-title', id: 'hill-wait' }, 'Waiting on you'),
          (v.pending ?? []).length ? h('ul', { class: 'hill-list' }, ...(v.pending ?? []).map(p => h('li', null,
            h('span', null, p.label), h('span', { class: 'pill' + (p.weeksLeft <= 0 ? ' tone-warn' : '') }, p.weeksLeft <= 0 ? 'this week' : `${p.weeksLeft} ${p.weeksLeft === 1 ? 'week' : 'weeks'}`))))
            : h('p', { class: 'muted' }, 'Nothing from the Hill.'),
          (v.pending ?? []).some(p => p.kind !== 'budget') ? h('button', { class: 'btn small primary', type: 'button', onclick: act.decide }, 'Decide now') : null,
          (v.pending ?? []).some(p => p.kind === 'budget') ? h('button', { class: 'btn small', type: 'button', onclick: () => { tab = 'The budget'; draw(); } }, 'The budget request') : null,
          h('p', { class: 'muted small' }, 'Left past its week, each gets the default: a bill becomes law unsigned, a stopgap is accepted, the request goes as drafted.')),
        h('section', { class: 'room-card', 'aria-labelledby': 'hill-rec' },
          h('h3', { class: 'room-card-title', id: 'hill-rec' }, 'The record'),
          (v.record ?? []).length ? h('ol', { class: 'hill-record' }, ...(v.record ?? []).slice(0, 12).map(r => h('li', null, h('span', { class: 'muted' }, day(r.date, true)), ' ', r.what)))
            : h('p', { class: 'muted' }, 'Nothing yet.'))));
  }

  /** A chamber as one bar of its blocs, majority line marked, then each bloc with its relationship. */
  function chamber(name: string, blocs: HillBloc[], seats: number, majority: string, line: number): HTMLElement {
    const p = palette();
    const ordered = [...blocs].sort((a, b) => (a.party === b.party ? 0 : a.party === 'D' ? -1 : 1));
    const dem = blocs.filter(b => b.party === 'D').reduce((s, b) => s + b.seats, 0);
    const bar = h('div', { class: 'hill-bar', role: 'img', 'aria-label': `${name}: ${dem} Democrats, ${seats - dem} Republicans` },
      ...ordered.map((b, i) => h('span', { class: 'hill-seg', title: `${b.name}: ${b.seats}`,
        style: `flex:${b.seats};background:${b.party === 'D' ? (b.moderate ? p.d.light : i % 2 ? p.d.mid : p.d.solid) : (b.moderate ? p.r.light : i % 2 ? p.r.mid : p.r.solid)}` })),
      h('span', { class: 'hill-line', style: `left:${(line / seats) * 100}%`, title: `${line} needed` }));
    return h('div', { class: 'hill-chamber' },
      h('div', { class: 'hill-chamber-head' }, h('strong', null, name), h('span', null, `${dem} D, ${seats - dem} R · ${partyName(majority)} in the majority`)),
      bar,
      h('ul', { class: 'hill-blocs' }, ...ordered.map(b => h('li', null,
        h('span', { class: 'hill-bloc-name ' + (b.party === 'D' ? 'd' : 'r') }, b.name),
        h('span', { class: 'hill-bloc-seats' }, String(b.seats)),
        meter(b.relationship, `Relationship with the ${b.name}`),
        h('span', { class: 'muted small hill-bloc-lean' }, b.leaning + (b.moderate ? ', dealmakers' : ''))))));
  }

  function meter(v: number, label: string): HTMLElement {
    const tone = v >= 60 ? 'good' : v < 40 ? 'bad' : 'mid';
    return h('span', { class: 'hill-meter ' + tone, role: 'meter', 'aria-valuemin': '0', 'aria-valuemax': '100', 'aria-valuenow': String(Math.round(v)), 'aria-label': label },
      h('span', { style: `width:${Math.max(2, Math.min(100, v))}%` }), h('span', { class: 'hill-meter-value' }, String(Math.round(v))));
  }

  // ---- Bills ----------------------------------------------------------------------------------------------------------

  function billsTab(v: HillView): HTMLElement {
    const bills = v.bills ?? [];
    const ours = bills.filter(b => b.ours), theirs = bills.filter(b => !b.ours);
    const active = ours.filter(b => b.stageKey !== 'DRAFT' && !['LAW', 'DEAD', 'OVERRIDE'].includes(b.stageKey)).length;
    return h('div', { class: 'hill-body' },
      h('p', { class: 'muted' }, `Draft a bill, shape it, and send it: at most three of yours before Congress at once (${active} now). A bill spends a few weeks in committee in each chamber, waits for floor time, and goes to conference if the chambers differ. Leaders hold a vote they would lose.`),
      ours.length ? h('div', { class: 'hill-bills' }, ...ours.map(b => billCard(v, b))) : h('p', null, 'You have no bills before Congress.'),
      h('section', { class: 'room-card', 'aria-labelledby': 'hill-draft' },
        h('h3', { class: 'room-card-title', id: 'hill-draft' }, 'Draft a bill'),
        h('p', { class: 'muted small' }, 'The game’s bills your party can carry. A step on any single policy can be drafted from the Cabinet Room, in the West Wing.'),
        h('ul', { class: 'hill-drafts' }, ...(v.drafts ?? []).map(d => h('li', null,
          h('div', null, h('strong', null, d.name), d.agenda ? h('span', { class: 'pill' }, 'your agenda') : null, d.sponsor ? null : h('span', { class: 'pill quiet' }, 'bipartisan'),
            h('div', { class: 'muted small' }, d.summary)),
          h('button', { class: 'btn small', type: 'button', disabled: !v.open || d.moving, 'aria-label': 'Draft the ' + d.name,
            onclick: () => { if (act.order({ type: 'Draft', source: d.id })) { focus = null; draw(); } } }, d.moving ? 'Drafted' : 'Draft'))))),
      theirs.length ? h('section', { class: 'room-card', 'aria-labelledby': 'hill-theirs' },
        h('h3', { class: 'room-card-title', id: 'hill-theirs' }, 'Congress’s own bills'),
        h('p', { class: 'muted small' }, 'The majority moves bills of its own. If one reaches your desk, you sign it, veto it, or let it become law.'),
        h('div', { class: 'hill-bills' }, ...theirs.map(b => billCard(v, b)))) : null);
  }

  function billCard(v: HillView, b: HillBill): HTMLElement {
    const live = b.whip !== undefined;
    const isDraft = b.stageKey === 'DRAFT';
    const tone = b.stageKey === 'LAW' || b.stageKey === 'OVERRIDE' ? ' tone-good' : b.stageKey === 'DEAD' ? ' tone-bad' : b.stageKey === 'DESK' ? ' tone-warn' : '';
    const card = h('article', { class: 'room-card hill-bill' + (focus === b.id ? ' focus' : '') },
      h('div', { class: 'hill-bill-head' }, h('h4', { class: 'hill-bill-name' }, b.name), h('span', { class: 'pill' + tone }, b.stage)),
      h('p', { class: 'muted small' }, b.description + (b.ours ? '' : ` Moved by the ${b.author === 'D' ? 'Democratic' : 'Republican'} majority.`)),
      h('p', { class: 'small' }, `Public support ${b.support}%. ${b.netCost > 0 ? `Costs about $${b.netCost} billion a year` : b.netCost < 0 ? `Saves about $${-b.netCost} billion a year` : 'Costs little'}`
        + (b.payFor ? `, ${b.payForLabel.toLowerCase()} to pay for it` : '') + `. ${b.reconciliation ? 'Through reconciliation.' : ''}`
        + (b.note ? ' ' + b.note : '')),
      live ? whipView(b.whip!, b) : null,
      b.ours && isDraft && v.open ? draftControls(b) : null,
      b.ours && live && !isDraft && b.stageKey !== 'DESK' && v.open ? workVotes(v, b) : null,
      b.stageKey === 'DESK' && v.open ? h('button', { class: 'btn small primary', type: 'button', onclick: act.decide }, 'Sign or veto') : null,
      !live && (b.votes.houseYes || b.votes.senateYes) ? h('p', { class: 'muted small' }, `House ${b.votes.houseYes}-${b.votes.houseNo}, Senate ${b.votes.senateYes}-${b.votes.senateNo}.`) : null);
    return card;
  }

  function whipView(ws: HillWhip[], b: HillBill): HTMLElement {
    return h('div', { class: 'hill-whip' }, ...ws.map(w => {
      const here = w.chamber === b.chamber && b.stageKey !== 'DRAFT';
      const short = w.need - w.expected;
      return h('div', { class: 'hill-whip-ch' + (here ? ' here' : '') },
        h('div', { class: 'hill-whip-head' }, h('strong', null, w.chamber + (here ? ' (now)' : '')),
          h('span', null, `${w.expected} likely yes of ${w.need} needed · ${pct(w.chance)} to pass`)),
        h('div', { class: 'hill-count', role: 'img', 'aria-label': `${w.chamber}: about ${w.expected} yes, ${w.need} needed` },
          h('span', { class: 'hill-count-fill ' + (short <= 0 ? 'good' : short <= 5 ? 'mid' : 'bad'), style: `width:${(w.expected / w.seats) * 100}%` }),
          h('span', { class: 'hill-line', style: `left:${(w.need / w.seats) * 100}%` })),
        h('details', { class: 'pv-table' }, h('summary', null, 'By bloc'),
          h('table', { class: 'hill-whip-table' },
            h('thead', null, h('tr', null, h('th', { scope: 'col' }, 'Bloc'), h('th', { scope: 'col' }, 'Seats'), h('th', { scope: 'col' }, 'Likely yes'))),
            h('tbody', null, ...w.blocs.map(x => h('tr', null, h('th', { scope: 'row', class: x.party === 'D' ? 'd' : 'r' }, x.name), h('td', null, String(x.seats)),
              h('td', null, `${Math.round(x.seats * x.yes)} (${pct(x.yes)})`)))))));
    }));
  }

  function draftControls(b: HillBill): HTMLElement {
    const send = (patch: Partial<{ size: number; scope: number; payFor: number; recon: boolean; first: number }>) => {
      const o = { type: 'Edit', bill: b.id, size: b.size, scope: b.scope, payFor: b.payFor, recon: b.reconciliation, first: b.firstChamber, ...patch };
      focus = b.id;
      order(o);
    };
    const slider = (label: string, min: number, max: number, step: number, value: number, left: string, right: string, key: 'size' | 'scope') =>
      h('label', { class: 'hill-slider' }, h('span', null, label),
        h('input', { type: 'range', min: String(min), max: String(max), step: String(step), value: String(value), 'aria-label': label,
          onchange: (e: Event) => send({ [key]: Number((e.target as HTMLInputElement).value) }) }),
        h('span', { class: 'muted small' }, `${left} · ${right}`));
    return h('div', { class: 'hill-draft-controls' },
      slider('Size', 0.5, 1.5, 0.1, b.size, 'smaller: easier to pass, less done', 'bigger: more done, harder', 'size'),
      slider('Scope', 0, 1, 0.1, b.scope, 'narrow: easier', 'broad: more ambitious', 'scope'),
      h('label', { class: 'hill-field' }, h('span', null, 'Pay for it'),
        h('select', { 'aria-label': 'Pay for it', onchange: (e: Event) => send({ payFor: Number((e.target as HTMLSelectElement).value) }) },
          ...['Borrow', 'Tax the wealthy', 'Cut elsewhere'].map((l, i) => h('option', { value: String(i), selected: i === b.payFor }, l)))),
      b.budgetEligible ? h('label', { class: 'hill-check' }, h('input', { type: 'checkbox', checked: b.reconciliation, 'aria-label': 'Use reconciliation',
        onchange: (e: Event) => send({ recon: (e.target as HTMLInputElement).checked }) }), ' Use reconciliation: 50 votes in the Senate, once a fiscal year, with your party holding both chambers')
        : h('p', { class: 'muted small' }, 'The Byrd rule keeps it out of reconciliation.'),
      h('label', { class: 'hill-field' }, h('span', null, 'First to'),
        h('select', { 'aria-label': 'First to', onchange: (e: Event) => send({ first: Number((e.target as HTMLSelectElement).value) }) },
          h('option', { value: '0', selected: b.firstChamber === 0 }, 'the House'), h('option', { value: '1', selected: b.firstChamber === 1 }, 'the Senate'))),
      h('div', { class: 'cab-buttons' },
        h('button', { class: 'btn small primary', type: 'button', onclick: () => { focus = b.id; order({ type: 'Send', bill: b.id }); } }, 'Send it to Congress'),
        h('button', { class: 'btn small quiet', type: 'button', onclick: () => order({ type: 'Withdraw', bill: b.id }) }, 'Discard the draft')));
  }

  /** Deals with the members in the chamber the bill is in: chairs and swing votes first, then the leaders. */
  function workVotes(v: HillView, b: HillBill): HTMLElement {
    const here = (v.members ?? []).filter(m => m.chamber === b.chamber && !m.leader);
    here.sort((x, y) => (x.role === 'Swing vote' ? 0 : 1) - (y.role === 'Swing vote' ? 0 : 1) || y.relationship - x.relationship);
    const done = new Set(b.dealtThisWeek ?? []);
    let pick = here.find(m => !done.has(m.id))?.id ?? here[0]?.id ?? '';
    const who = h('select', { 'aria-label': 'The member', onchange: (e: Event) => { pick = (e.target as HTMLSelectElement).value; } },
      ...here.map(m => h('option', { value: m.id, selected: m.id === pick, disabled: done.has(m.id) },
        `${m.name} (${m.party}-${m.state ?? ''}), ${m.role === 'Chair' ? m.committee + ' chair' : 'swing vote'}${m.price ? ', wants ' + m.price : ''}${m.owesFavor ? ', owes you' : ''}${done.has(m.id) ? ', dealt this week' : ''}`)));
    return h('details', { class: 'hill-work', open: focus === b.id },
      h('summary', null, 'Work the votes'),
      h('p', { class: 'muted small' }, `${v.hoursLeft} hours left this week. A deal lasts for this bill; each member makes one a week.`),
      h('label', { class: 'hill-field' }, h('span', null, 'With'), who),
      h('div', { class: 'cab-buttons' }, ...DEALS.map(d => h('button', { class: 'btn small', type: 'button', title: d.detail,
        onclick: () => { focus = b.id; order({ type: 'Deal', bill: b.id, member: pick, kind: d.kind }); } }, d.label))),
      h('div', { class: 'cab-buttons' },
        h('button', { class: 'btn small', type: 'button', disabled: b.wentPublic, title: 'Speeches and interviews for the bill: moderates warm to it, the other side’s leaders cool.',
          onclick: () => { focus = b.id; order({ type: 'GoPublic', bill: b.id }); } }, b.wentPublic ? 'Taken to the country' : 'Take it to the country'),
        h('button', { class: 'btn small quiet', type: 'button', onclick: () => order({ type: 'Withdraw', bill: b.id }) }, 'Withdraw the bill')));
  }

  // ---- Members --------------------------------------------------------------------------------------------------------

  function membersTab(v: HillView): HTMLElement {
    const ms = v.members ?? [];
    const group = (ch: 'House' | 'Senate') => {
      const list = ms.filter(m => m.chamber === ch);
      const order_ = (m: HillMember) => (m.leader ? 0 : m.role === 'Chair' ? 1 : 2);
      list.sort((a, b) => order_(a) - order_(b));
      return h('section', { class: 'room-card', 'aria-labelledby': 'hill-m-' + ch },
        h('h3', { class: 'room-card-title', id: 'hill-m-' + ch }, 'The ' + ch),
        h('table', { class: 'hill-members' },
          h('thead', null, h('tr', null, h('th', { scope: 'col' }, 'Member'), h('th', { scope: 'col' }, 'Role'), h('th', { scope: 'col' }, 'Relationship'))),
          h('tbody', null, ...list.map(m => h('tr', null,
            h('th', { scope: 'row' }, h('span', { class: m.party === 'D' ? 'd' : 'r' }, m.name), h('span', { class: 'muted small' }, ` ${m.party}${m.state ? '-' + m.state : ''}`),
              m.owesFavor ? h('span', { class: 'pill tone-good' }, 'owes you') : null),
            h('td', null, m.role === 'Chair' ? m.committee + ' chair' : m.role, h('div', { class: 'muted small' }, m.bloc + (m.price ? ', wants ' + m.price : '')),
              m.leader && v.open ? h('button', { class: 'btn small hill-meet', type: 'button', 'aria-label': 'Meet ' + m.name, title: 'A private meeting: two hours on the Hill.',
                onclick: () => order({ type: 'Meet', member: m.id }) }, 'Meet') : null),
            h('td', null, meter(m.relationship, 'Relationship with ' + m.name)))))));
    };
    return h('div', { class: 'hill-body' },
      h('p', { class: 'muted' }, 'The leaders schedule the floor; a chair decides whether a bill leaves committee; swing votes decide close counts. Each wants something: a project, an appointment, an endorsement, or a change to the bill.'),
      h('div', { class: 'room-grid two' }, group('House'), group('Senate')));
  }

  // ---- The budget -----------------------------------------------------------------------------------------------------

  function budgetTab(v: HillView): HTMLElement {
    const bu = v.budget!;
    const r = bu.request;
    const req = { ...r };
    const send = () => order({ type: 'Budget', defense: req.defense, other: req.other, tax: req.tax, entitlements: req.entitlements });
    const slider = (label: string, key: keyof typeof r, min: number, max: number, step: number, unit: string) =>
      h('label', { class: 'hill-slider' }, h('span', null, label),
        h('input', { type: 'range', min: String(min), max: String(max), step: String(step), value: String(r[key]), 'aria-label': label, disabled: bu.requestSent || !v.open,
          onchange: (e: Event) => { req[key] = Number((e.target as HTMLInputElement).value); send(); } }),
        h('span', { class: 'hill-slider-value' }, `${r[key] > 0 ? '+' : ''}${r[key]}${unit}`));
    const stages = ['Not started', 'In committee', 'Passed one chamber', 'Enacted', 'On a stopgap'];
    return h('div', { class: 'hill-body' },
      h('div', { class: 'room-grid two' },
        h('section', { class: 'room-card', 'aria-labelledby': 'hill-req' },
          h('h3', { class: 'room-card-title', id: 'hill-req' }, `The request for fiscal ${bu.fiscalYear + (bu.requestSent ? 0 : 1)}`),
          bu.requestSent ? h('p', null, 'This year’s request has gone to Congress. The appropriations committees write the twelve spending bills from it.')
            : h('p', null, 'What you ask Congress for: the starting point for the year’s spending bills.' + (bu.requestDueWeeks !== undefined ? ` Due within ${bu.requestDueWeeks} weeks, or it goes as drafted.` : '')),
          slider('Defense', 'defense', -15, 15, 1, '%'),
          slider('Other agencies', 'other', -25, 25, 1, '%'),
          slider('Taxes', 'tax', -1.5, 1.5, 0.1, '% of GDP'),
          slider('Entitlements', 'entitlements', -1, 1, 0.1, '% of GDP'),
          h('p', { class: 'small' }, `It would change the deficit by ${bu.requestDeficit > 0 ? '+' : ''}${bu.requestDeficit.toFixed(2)}% of GDP (the deficit is ${bu.deficit.toFixed(1)}% now). Fiscal hawks cool to a request that adds to it.`),
          !bu.requestSent && v.open ? h('button', { class: 'btn small primary', type: 'button', onclick: () => order({ type: 'SendBudget' }) }, 'Send the request to Congress') : null),
        h('section', { class: 'room-card', 'aria-labelledby': 'hill-approps' },
          h('h3', { class: 'room-card-title', id: 'hill-approps' }, 'The twelve spending bills'),
          h('p', { class: 'muted small' }, `They move from May to September. The fiscal year ends on ${day(bu.deadline, true)}: whatever is not enacted needs a stopgap, or the government shuts down.`),
          h('ul', { class: 'hill-approps' }, ...bu.appropriations.map(a => h('li', null, h('span', null, a.name),
            h('span', { class: 'pill' + (a.stage === 3 ? ' tone-good' : a.stage === 4 ? ' tone-warn' : '') }, stages[a.stage] ?? a.label)))))),
      h('div', { class: 'room-grid two' },
        h('section', { class: 'room-card', 'aria-labelledby': 'hill-cr' },
          h('h3', { class: 'room-card-title', id: 'hill-cr' }, 'Keeping the government open'),
          bu.shutdown ? h('p', { class: 'hill-alert' }, `The government is shut, week ${bu.shutdownWeeks}. ${Math.round(bu.blame * 100)}% of the blame falls on the White House.`)
            : bu.stopgapWeeks !== null ? h('p', null, `Running on a stopgap for ${bu.stopgapWeeks} more ${bu.stopgapWeeks === 1 ? 'week' : 'weeks'}; then an omnibus, or another deadline.`)
            : h('p', null, 'Funded for now.')),
        h('section', { class: 'room-card', 'aria-labelledby': 'hill-debt' },
          h('h3', { class: 'room-card-title', id: 'hill-debt' }, 'The debt limit'),
          h('p', null, `The limit is $${bu.debtLimit.toFixed(1)} trillion; the debt subject to it is $${bu.debtSubject.toFixed(1)} trillion.`),
          bu.xDateWeeks !== null ? h('p', { class: 'hill-alert' }, `The limit is reached. The X-date, when the Treasury runs out of room, is ${bu.xDateWeeks} ${bu.xDateWeeks === 1 ? 'week' : 'weeks'} away.`)
            : h('p', { class: 'muted small' }, `About $${Math.max(0, bu.debtLimit - bu.debtSubject).toFixed(1)} trillion of room. When it runs out, the Treasury has about five months of extraordinary measures.`))));
  }

  // ---- Elections ------------------------------------------------------------------------------------------------------

  function electionsTab(v: HillView): HTMLElement {
    const e = v.elections!;
    const p = palette();
    const races = [...e.races].sort((a, b) => Math.abs(a.poll) - Math.abs(b.poll));
    const lean = (m: number) => (Math.abs(m) < 0.5 ? 'Even' : (m > 0 ? 'D+' : 'R+') + Math.abs(m).toFixed(1));
    const dTotal = e.house.reduce((s, x) => s + x.dem, 0);
    return h('div', { class: 'hill-body' },
      h('section', { class: 'room-card', 'aria-labelledby': 'hill-el' },
        h('h3', { class: 'room-card-title', id: 'hill-el' }, e.label),
        h('p', null, `${day(e.date, true)}, ${e.days > 0 ? e.days + ' days away' : 'today'}. The national environment for your party: ${e.national >= 0 ? '+' : ''}${e.national.toFixed(1)} points `
          + '(approval, the economy, the money and the campaign). Campaign stops for your party’s candidates are scheduled from the calendar in the hundred days before the vote; '
          + `${e.campaignStops} so far, and $${e.warChest}M raised for the party.`)),
      h('div', { class: 'room-grid two' },
        h('section', { class: 'room-card', 'aria-labelledby': 'hill-sen' },
          h('h3', { class: 'room-card-title', id: 'hill-sen' }, 'The Senate races'),
          e.racesOpen && races.length ? h('table', { class: 'hill-races' },
            h('thead', null, h('tr', null, h('th', { scope: 'col' }, 'State'), h('th', { scope: 'col' }, 'Democrat'), h('th', { scope: 'col' }, 'Republican'), h('th', { scope: 'col' }, e.days > 0 ? 'Poll' : 'Result'))),
            h('tbody', null, ...races.map(r => h('tr', null,
              h('th', { scope: 'row' }, r.name, h('span', { class: 'muted small' }, ` ${r.holder} seat${r.open ? ', open' : ''}`)),
              h('td', null, r.democrat), h('td', null, r.republican),
              h('td', { class: (r.result ?? r.poll) >= 0 ? 'd' : 'r' }, lean(r.result ?? r.poll))))))
            : h('p', { class: 'muted' }, 'The races take shape a hundred days before the election.')),
        h('section', { class: 'room-card', 'aria-labelledby': 'hill-house' },
          h('h3', { class: 'room-card-title', id: 'hill-house' }, 'The House by state'),
          h('p', { class: 'muted small' }, `${dTotal} Democrats and ${435 - dTotal} Republicans now. Each delegation splits by the state’s lean, its incumbents and the national swing.`),
          h('div', { class: 'hill-house-grid', role: 'list' }, ...e.house.filter(x => x.seats > 0).sort((a, b) => b.seats - a.seats).map(x => {
            // solid fills carry white text, light ones dark ink: both read at 4.5:1 or better
            const share = x.dem / x.seats;
            const [fill, ink] = share === 0.5 ? [p.toss, p.ink] : share > 0.5 ? (share >= 0.75 ? [p.d.solid, '#ffffff'] : [p.d.light, p.ink])
              : (share <= 0.25 ? [p.r.solid, '#ffffff'] : [p.r.light, p.ink]);
            return h('span', { class: 'hill-house-cell', role: 'listitem', style: `--fill:${fill};--ink:${ink}`, title: `${x.name}: ${x.dem} D, ${x.seats - x.dem} R` },
              h('span', { class: 'hill-house-code' }, x.state), h('span', { class: 'hill-house-n' }, `${x.dem}-${x.seats - x.dem}`));
          })))));
  }

  // ---- Favours and probes ---------------------------------------------------------------------------------------------

  function troubleTab(v: HillView): HTMLElement {
    const fs = v.favors ?? [], ps = v.probes ?? [];
    const imp = v.impeachment!;
    const impLine = imp.status === 'inquiry' ? 'The House has opened an impeachment inquiry.' : imp.status === 'trial' ? `The House impeached the president, ${imp.houseYes} votes; the Senate trial is under way.`
      : imp.status === 'acquitted' ? `The Senate acquitted the president: ${imp.senateYes} voted to convict, 67 were needed.` : imp.status === 'removed' ? 'The Senate convicted and removed the president.' : null;
    return h('div', { class: 'hill-body' },
      h('div', { class: 'room-grid two' },
        h('section', { class: 'room-card', 'aria-labelledby': 'hill-fav' },
          h('h3', { class: 'room-card-title', id: 'hill-fav' }, 'Favours and promises'),
          fs.length ? h('ul', { class: 'hill-list' }, ...fs.map(f => h('li', null,
            h('span', null, h('strong', null, f.member), ` (${f.role}): `, f.promise ? `you promised ${f.why}` + (f.state ? ` in ${f.state}` : '') : `owes you for ${f.why}`),
            f.promise ? h('span', { class: 'pill' + ((f.weeksLeft ?? 0) <= 2 ? ' tone-warn' : '') }, `${f.weeksLeft} weeks to keep it`) : h('span', { class: 'pill tone-good' }, 'call it in'))))
            : h('p', { class: 'muted' }, 'Nobody owes you anything, and you owe nobody.'),
          h('p', { class: 'muted small' }, 'A favour is the strongest push on a vote, called in from a bill’s card. A promised visit is kept by a trip to the state on your calendar; broken, it costs the member and the bloc.')),
        h('section', { class: 'room-card', 'aria-labelledby': 'hill-prb' },
          h('h3', { class: 'room-card-title', id: 'hill-prb' }, 'Investigations'),
          impLine ? h('p', { class: 'hill-alert' }, impLine) : null,
          ps.length ? h('ul', { class: 'hill-list' }, ...ps.map(p => h('li', null,
            h('span', null, `The ${p.chamber} on ${p.subject}`, p.contempt ? ', with officials held in contempt' : ''),
            h('span', { class: 'pill' + (p.impeachable ? ' tone-bad' : p.status === 'closed' ? ' tone-good' : '') }, p.impeachable ? 'grounds for impeachment' : p.status))))
            : h('p', { class: 'muted' }, v.unified ? 'With your party running both chambers, there are no investigations.' : 'None open.'),
          h('p', { class: 'muted small' }, 'An opposition chamber opens investigations every few months. How you answer its subpoenas decides how bad the report is; refusing gives grounds for impeachment.'))));
  }

  draw();
  return root;
}
