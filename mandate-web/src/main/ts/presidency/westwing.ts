// The West Wing (Resolute Desk's WestWing; docs/PRESIDENCY_PORT.md, milestone 6): the staff, the cabinet and the
// departments, the vice president, orders, rules and emergencies, the courts, and oversight. The Cabinet Room (the
// policy table) is down the hall. Every control issues one West Wing order; the room redraws from the engine after each.
import { h } from '../core/dom.js';
import { day } from '../core/format.js';
import type { WestWingView, WwLibraryItem, WwPerson, WwPost } from '../core/types.js';

export interface WestWingActions {
  view(): WestWingView;
  options(kind: 'staff' | 'post', index: number): { options: WwPerson[] };
  /** Applies one West Wing order; false if refused (the refusal is shown). */
  order(o: object): boolean;
  /** Opens what is waiting on the desk (after an order raises a question). */
  decide(): void;
  cabinetRoom(): void;
  back(): void;
  /** The exit button's words (the Oval Office's by default; the war room says its own). */
  backLabel?: string;
  issues: { id: string; label: string }[];
}

const TABS = ['Staff', 'Cabinet', 'Vice president', 'Orders and rules', 'The courts', 'Oversight'] as const;
type Tab = typeof TABS[number];
const RD_ISSUES = ['The economy', 'Prices', 'Jobs', 'Health care', 'Immigration', 'Crime', 'Foreign policy', 'National security', 'Trade', 'Energy', 'Civil rights', 'Technology', 'Education', 'Ethics'];

function bar(v: number, label: string): HTMLElement {
  const tone = v >= 65 ? 'good' : v >= 45 ? 'mid' : 'bad';
  return h('span', { class: 'hill-meter ' + tone, role: 'meter', 'aria-valuemin': '0', 'aria-valuemax': '100', 'aria-valuenow': String(Math.round(v)), 'aria-label': label },
    h('span', { style: `width:${Math.max(2, Math.min(100, v))}%` }), h('span', { class: 'hill-meter-value' }, String(Math.round(v))));
}

function stats(p: WwPerson, who: string): HTMLElement {
  return h('dl', { class: 'ww-stats' },
    h('dt', null, 'Skill'), h('dd', null, bar(p.skill, `${who}: skill`)),
    h('dt', null, 'Loyalty'), h('dd', null, bar(p.loyalty, `${who}: loyalty`)),
    h('dt', null, 'Morale'), h('dd', null, bar(p.morale, `${who}: morale`)));
}

function chip(p: WwPerson): HTMLElement {
  return h('span', { class: 'pill' + (p.traitRisky ? ' tone-warn' : ''), title: p.traitDesc }, p.trait);
}

export function westWingRoom(act: WestWingActions, first: Tab = 'Staff'): HTMLElement {
  let tab: Tab = first;
  let role: number | null = null, post: number | null = null;
  let libKind = 0, libPick: string | null = null;
  const root = h('div', { class: 'room hill ww' });
  const order = (o: object, thenDecide = false) => { if (act.order({ ...o })) { draw(); if (thenDecide) act.decide(); } };

  function draw(): void {
    const v = act.view();
    const keep = document.activeElement instanceof HTMLElement && root.contains(document.activeElement)
      ? document.activeElement.getAttribute('aria-label') ?? document.activeElement.textContent : null;
    const top = h('div', { class: 'room-top' },
      h('div', null, h('div', { class: 'eyebrow' }, 'Down the hall'), h('h2', { class: 'dialog-title' }, 'The West Wing'),
        h('p', { class: 'muted' }, 'Week of ' + day(v.date, true))),
      h('div', { class: 'cab-buttons' },
        h('button', { class: 'btn', type: 'button', onclick: act.cabinetRoom }, 'The Cabinet Room: the policy table'),
        h('button', { class: 'btn primary', type: 'button', onclick: act.back }, act.backLabel ?? 'Back to the Oval Office')));
    if (!v.staff) { root.replaceChildren(top, h('p', null, v.reason ?? '')); return; }
    const nav = h('nav', { class: 'cab-tabs', 'aria-label': 'The West Wing' }, ...TABS.map(t =>
      h('button', { class: 'btn small toggle' + (t === tab ? ' on' : ''), type: 'button', 'aria-pressed': String(t === tab), onclick: () => { tab = t; draw(); } }, t)));
    const closed = !v.open ? h('p', { class: 'hill-closed', role: 'note' }, 'The West Wing is in the staff’s hands in this phase.') : null;
    const body = tab === 'Staff' ? staffTab(v) : tab === 'Cabinet' ? cabinetTab(v) : tab === 'Vice president' ? vpTab(v)
      : tab === 'Orders and rules' ? ordersTab(v) : tab === 'The courts' ? courtsTab(v) : oversightTab(v);
    root.replaceChildren(top, nav, ...(closed ? [closed] : []), body);
    if (keep) [...root.querySelectorAll<HTMLElement>('button, input, select')].find(e => (e.getAttribute('aria-label') ?? e.textContent) === keep)?.focus({ preventScroll: true });
  }

  // ---- the staff ------------------------------------------------------------------------------------------------------

  function staffTab(v: WestWingView): HTMLElement {
    const cards = v.staff!.map(s => {
      const p = s.person;
      return h('article', { class: 'room-card ww-card' + (role === s.role ? ' focus' : '') },
        h('h3', { class: 'room-card-title' }, s.title),
        p ? h('div', { class: 'ww-who' }, h('strong', null, p.name), ' ', chip(p), h('div', { class: 'muted small' }, `${p.background} · ${p.weeks} weeks in the job`)) : h('p', { class: 'muted' }, 'Vacant'),
        p ? stats(p, s.title) : null,
        h('p', { class: 'muted small' }, s.does),
        h('div', { class: 'cab-buttons' },
          h('button', { class: 'btn small', type: 'button', 'aria-label': (p ? 'Replace the ' : 'Choose a ') + s.title.toLowerCase(), disabled: !v.open,
            onclick: () => { role = role === s.role ? null : s.role; draw(); } }, p ? 'Replace' : 'Choose someone'),
          p ? h('button', { class: 'btn small quiet', type: 'button', 'aria-label': 'Fire ' + p.name, disabled: !v.open, onclick: () => order({ type: 'Fire', person: p.id }) }, 'Fire') : null),
        role === s.role ? optionCards('staff', s.role, v) : null);
    });
    return h('div', { class: 'hill-body' },
      h('div', { class: 'room-grid two' },
        h('section', { class: 'room-card', 'aria-labelledby': 'ww-mood' },
          h('h3', { class: 'room-card-title', id: 'ww-mood' }, 'The mood in the West Wing'),
          bar(v.mood!, 'The mood in the West Wing'),
          h('p', { class: 'muted small' }, `${v.leaks} leaks, ${v.departures} departures, ${v.failures} botched rollouts so far. Morale follows your approval, your stress and the people around them.`)),
        h('section', { class: 'room-card', 'aria-labelledby': 'ww-deleg' },
          h('h3', { class: 'room-card-title', id: 'ww-deleg' }, 'Hand it to the staff'),
          h('div', { class: 'cab-buttons' },
            toggle('People', 'The chief of staff fills empty jobs with the expert and nominates who the Senate will confirm.', !!v.delegatedPeople, on => order({ type: 'Delegate', what: 'people', on }), v.open),
            toggle('Pardons', 'The counsel grants mercy and fame, commutes the soldiers’ cases and turns down the rest.', !!v.delegatedPardons, on => order({ type: 'Delegate', what: 'pardons', on }), v.open)))),
      h('div', { class: 'ww-grid' }, ...cards));
  }

  function toggle(label: string, title: string, on: boolean, set: (on: boolean) => void, enabled: boolean | undefined): HTMLElement {
    return h('button', { class: 'btn small toggle' + (on ? ' on' : ''), type: 'button', 'aria-pressed': String(on), title, disabled: !enabled, onclick: () => set(!on) },
      label + ': ', h('strong', null, on ? 'the staff' : 'you'));
  }

  function optionCards(kind: 'staff' | 'post', index: number, v: WestWingView): HTMLElement {
    const os = act.options(kind, index).options;
    const labels = ['The expert', 'The loyalist', kind === 'staff' ? 'The outsider' : 'Across the aisle'];
    return h('div', { class: 'ww-options' }, ...os.map((p, i) => h('div', { class: 'ww-option' },
      h('div', { class: 'eyebrow' }, labels[i]),
      h('strong', null, p.name), ' ', chip(p), h('span', { class: 'muted small' }, ` ${p.party}`),
      h('div', { class: 'muted small' }, p.background),
      stats(p, p.name),
      p.chance !== undefined ? h('p', { class: 'small' }, `${Math.round(p.chance * 100)}% to be confirmed`) : null,
      h('button', { class: 'btn small primary', type: 'button', disabled: !v.open, 'aria-label': (kind === 'staff' ? 'Hire ' : 'Nominate ') + p.name,
        onclick: () => { if (act.order(kind === 'staff' ? { type: 'Hire', role: index, option: i } : { type: 'Nominate', post: index, option: i })) { role = null; post = null; draw(); } } },
        kind === 'staff' ? 'Hire' : 'Nominate'))));
  }

  // ---- the cabinet ----------------------------------------------------------------------------------------------------

  function cabinetTab(v: WestWingView): HTMLElement {
    const rows = v.posts!.map(p => h('tr', { class: post === p.post ? 'on' : '' },
      h('th', { scope: 'row' }, h('button', { class: 'ww-row', type: 'button', 'aria-pressed': String(post === p.post), onclick: () => { post = post === p.post ? null : p.post; draw(); } }, p.short)),
      h('td', null, p.person ? p.person.name : p.nominee ? `${p.nominee.name}: Senate vote in ${p.nominee.voteIn} wk, ${Math.round((p.nominee.chance ?? 0) * 100)}%` : h('span', { class: 'muted' }, 'Acting official')),
      h('td', null, p.person ? bar(p.person.skill, `${p.title}: skill`) : ''),
      h('td', null, bar(p.readiness, `${p.name}: readiness`)),
      h('td', { class: 'num' }, p.workforce >= 1 ? `${Math.round(p.workforce)}k` : `${Math.round(p.workforce * 1000)}`)));
    const chosen = post === null ? null : v.posts!.find(p => p.post === post) ?? null;
    return h('div', { class: 'hill-body' },
      h('div', { class: 'ww-split' },
        h('section', { class: 'room-card', 'aria-labelledby': 'ww-cab' },
          h('h3', { class: 'room-card-title', id: 'ww-cab' }, 'The cabinet and the departments'),
          h('table', { class: 'hill-members ww-cabinet' },
            h('thead', null, h('tr', null, h('th', { scope: 'col' }, 'Post'), h('th', { scope: 'col' }, 'Who'), h('th', { scope: 'col' }, 'Skill'), h('th', { scope: 'col' }, 'Readiness'), h('th', { scope: 'col' }, 'Staff'))),
            h('tbody', null, ...rows)),
          h('p', { class: 'muted small' }, `${v.workforce}k federal civilian employees. A department’s readiness follows its secretary, cabinet meetings and its workforce; below 66, rollouts start to fail.`)),
        chosen ? postDetail(v, chosen) : h('section', { class: 'room-card' }, h('p', { class: 'muted' }, 'Choose a post to see who holds it, or to nominate someone.'))));
  }

  function postDetail(v: WestWingView, p: WwPost): HTMLElement {
    const head = h('h3', { class: 'room-card-title' }, p.title);
    if (p.person) return h('section', { class: 'room-card' }, head,
      h('div', { class: 'ww-who' }, h('strong', null, p.person.name), ' ', chip(p.person), h('div', { class: 'muted small' }, `${p.person.background}${p.person.vote ? ' · confirmed ' + p.person.vote : ''} · ${p.person.weeks} weeks`)),
      stats(p.person, p.title),
      h('p', { class: 'small' }, `Readiness ${p.readiness} of 100.`),
      h('button', { class: 'btn small quiet', type: 'button', disabled: !v.open, 'aria-label': 'Fire ' + p.person.name, onclick: () => order({ type: 'Fire', person: p.person!.id }) }, 'Fire'));
    if (p.nominee) return h('section', { class: 'room-card' }, head,
      h('p', null, `${p.nominee.name} is before the Senate: about ${p.nominee.expected} votes, ${Math.round((p.nominee.chance ?? 0) * 100)}% to be confirmed; 50 plus the vice president’s vote confirms. The vote is in ${p.nominee.voteIn} weeks.`),
      stats(p.nominee, p.nominee.name),
      h('button', { class: 'btn small quiet', type: 'button', disabled: !v.open, onclick: () => order({ type: 'Withdraw', post: p.post }) }, 'Withdraw the nomination'));
    return h('section', { class: 'room-card' }, head, h('p', { class: 'muted' }, 'Vacant: an acting career official runs the department. Nominate someone:'), optionCards('post', p.post, v));
  }

  // ---- the vice president -----------------------------------------------------------------------------------------------

  function vpTab(v: WestWingView): HTMLElement {
    const vp = v.vp!;
    let issue = act.issues[0]!.id;
    const pickIssue = h('select', { 'aria-label': 'The task force’s issue', onchange: (e: Event) => { issue = (e.target as HTMLSelectElement).value; } },
      ...act.issues.map(i => h('option', { value: i.id }, i.label)));
    const names = ['Charisma', 'Establishment ties', 'Discipline', 'Fundraising'];
    return h('div', { class: 'hill-body' },
      h('div', { class: 'room-grid two' },
        h('section', { class: 'room-card', 'aria-labelledby': 'ww-vp' },
          h('h3', { class: 'room-card-title', id: 'ww-vp' }, 'Vice President ' + vp.name),
          h('p', null, 'Now: ' + vp.missionName + (vp.issue ? ' (' + vp.issue.toLowerCase() + ')' : '') + (vp.weeksLeft !== null ? `, ${vp.weeksLeft} more weeks` : '') + '.'),
          h('dl', { class: 'ww-stats' }, ...vp.skills.flatMap((x, i) => [h('dt', null, names[i]!), h('dd', null, bar(x, 'The vice president’s ' + names[i]!.toLowerCase()))]),
            h('dt', null, 'Loyalty'), h('dd', null, bar(vp.loyalty, 'The vice president’s loyalty'))),
          h('p', { class: 'muted small' }, 'The vice president breaks ties in the Senate for your party. A vice president with nothing to do grows restless.')),
        h('section', { class: 'room-card', 'aria-labelledby': 'ww-vpm' },
          h('h3', { class: 'room-card-title', id: 'ww-vpm' }, 'Missions'),
          h('ul', { class: 'hill-drafts' }, ...vp.missions.map(m => h('li', null,
            h('div', null, h('strong', null, m.name), h('div', { class: 'muted small' }, m.desc)),
            h('div', { class: 'cab-buttons' },
              m.id === 2 ? pickIssue : null,
              h('button', { class: 'btn small', type: 'button', disabled: !v.open || vp.mission === m.id && m.id !== 0, 'aria-label': m.name,
                onclick: () => order({ type: 'ViceMission', mission: m.id, issue: m.id === 2 ? issue : null }) }, vp.mission === m.id ? 'Under way' : m.id === 0 ? 'Bring them home' : 'Send'))))))));
  }

  // ---- orders, rules and emergencies -----------------------------------------------------------------------------------

  function ordersTab(v: WestWingView): HTMLElement {
    const kinds = ['Orders', 'Rules', 'Emergencies', 'Your own order'];
    const mine = v.president!;
    const list = (v.library ?? []).filter(a => a.kind === libKind);
    list.sort((a, b) => (a.lean === mine || a.lean === null ? 0 : 1) - (b.lean === mine || b.lean === null ? 0 : 1));
    const pick = list.find(a => a.id === libPick) ?? null;
    const left = libKind === 3 ? customForm(v) : h('ul', { class: 'cab-list', 'aria-label': kinds[libKind] }, ...list.map(a => h('li', null,
      h('button', { class: 'cab-row' + (a.id === libPick ? ' on' : ''), type: 'button', 'aria-pressed': String(a.id === libPick), onclick: () => { libPick = a.id; draw(); } },
        h('span', { class: 'cab-row-name ' + (a.lean === 'D' ? 'd' : a.lean === 'R' ? 'r' : '') }, a.name),
        h('span', { class: 'cab-row-level' }, a.refusal ?? `${a.support}% support`)))));
    return h('div', { class: 'hill-body' },
      h('nav', { class: 'cab-tabs', 'aria-label': 'Kinds of executive action' }, ...kinds.map((k, i) =>
        h('button', { class: 'btn small toggle' + (i === libKind ? ' on' : ''), type: 'button', 'aria-pressed': String(i === libKind), onclick: () => { libKind = i; libPick = null; draw(); } }, k))),
      h('div', { class: 'cab-split' }, left, libKind === 3 ? done(v) : pick ? actionDetail(v, pick) : done(v)),
      libKind !== 3 && pick ? done(v) : null);
  }

  function actionDetail(v: WestWingView, a: WwLibraryItem): HTMLElement {
    const verb = a.kind === 1 ? 'Propose the rule' : a.kind === 2 ? 'Declare the emergency' : 'Sign the order';
    return h('section', { class: 'cab-detail' },
      h('h3', { class: 'cab-detail-title' }, a.name),
      h('p', null, a.desc),
      h('dl', { class: 'cab-facts' },
        h('dt', null, 'Issue'), h('dd', null, a.issue + (a.lean ? ` · ${a.lean === 'D' ? 'Democrats' : 'Republicans'} want it` : ' · either party')),
        h('dt', null, 'The public'), h('dd', null, `${a.support}% support`),
        h('dt', null, 'A court blocks it'), h('dd', null, `about ${Math.round(a.legal * 100)}% (after the counsel’s work)`),
        a.kind === 1 ? h('dt', null, 'Comment period') : null, a.kind === 1 ? h('dd', null, `about ${v.commentWeeks} weeks`) : null,
        h('dt', null, 'Carried out by'), h('dd', null, `${a.department} (readiness ${a.readiness})`),
        a.needs ? h('dt', null, 'Needs') : null, a.needs ? h('dd', null, a.needs) : null),
      h('button', { class: 'btn small primary', type: 'button', disabled: !v.open || !!a.refusal, onclick: () => order({ type: 'Issue', action: a.id }, true) }, a.refusal ?? verb));
  }

  function customForm(v: WestWingView): HTMLElement {
    let area = 0, lean = v.president!, scope = 0.8, basis = 0;
    const reach = h('span', { class: 'hill-slider-value' }, 'An order on the issue');
    return h('section', { class: 'cab-detail' },
      h('h3', { class: 'cab-detail-title' }, 'An order of your own'),
      h('label', { class: 'hill-field' }, h('span', null, 'Issue'), h('select', { 'aria-label': 'Issue', onchange: (e: Event) => { area = Number((e.target as HTMLSelectElement).value); } },
        ...RD_ISSUES.map((l, i) => h('option', { value: String(i) }, l)))),
      h('label', { class: 'hill-field' }, h('span', null, 'Direction'), h('select', { 'aria-label': 'Direction', onchange: (e: Event) => { lean = (e.target as HTMLSelectElement).value as 'D' | 'R'; } },
        h('option', { value: 'D', selected: lean === 'D' }, 'Progressive'), h('option', { value: 'R', selected: lean === 'R' }, 'Conservative'))),
      h('label', { class: 'hill-slider' }, h('span', null, 'Reach'),
        h('input', { type: 'range', min: '0', max: '1', step: '0.05', value: '0.42', 'aria-label': 'Reach',
          oninput: (e: Event) => { scope = 0.3 + Number((e.target as HTMLInputElement).value) * 1.2; reach.textContent = scope > 1.1 ? 'Sweeping' : scope < 0.7 ? 'Narrow' : 'An order on the issue'; } }), reach),
      h('label', { class: 'hill-field' }, h('span', null, 'Legal basis'), h('select', { 'aria-label': 'Legal basis', onchange: (e: Event) => { basis = Number((e.target as HTMLSelectElement).value); } },
        h('option', { value: '0' }, 'Clear statute'), h('option', { value: '1' }, 'A stretch'), v.emergencyDeclared ? h('option', { value: '2' }, 'The emergency') : null)),
      h('p', { class: 'muted small' }, 'A broader order does more and draws more lawsuits; a stretch of the law is likely to be blocked.'),
      h('button', { class: 'btn small primary', type: 'button', disabled: !v.open, onclick: () => order({ type: 'Custom', area, lean: lean === 'D' ? 'DEMOCRAT' : 'REPUBLICAN', scope, basis }, true) }, 'Sign the order'));
  }

  function done(v: WestWingView): HTMLElement {
    const acts = v.actions ?? [];
    return h('section', { class: 'room-card', 'aria-labelledby': 'ww-done' },
      h('h3', { class: 'room-card-title', id: 'ww-done' }, 'What you’ve done'),
      acts.length ? h('ul', { class: 'hill-list' }, ...acts.slice(0, 20).map(a => h('li', null,
        h('span', null, h('strong', null, a.name), h('span', { class: 'muted small' }, ` · ${a.kind.toLowerCase()} · ${day(a.issued, true)}`),
          a.inCourt ? h('span', { class: 'pill tone-warn' }, 'in court') : null, a.failed ? h('span', { class: 'pill tone-bad' }, 'rollout trouble') : null),
        h('span', { class: 'cab-buttons' },
          h('span', { class: 'pill' + (a.statusKey === 0 ? ' tone-good' : a.statusKey === 2 || a.statusKey === 3 || a.statusKey === 6 ? ' tone-bad' : '') }, a.status + (a.finalIn !== undefined ? `, ${a.finalIn} wk` : '')),
          a.standing && v.open ? h('button', { class: 'btn small quiet', type: 'button', 'aria-label': 'Revoke ' + a.name, onclick: () => order({ type: 'Revoke', action: a.id }) }, a.statusKey === 5 ? 'Withdraw' : 'Rescind') : null))))
        : h('p', { class: 'muted' }, 'No executive actions yet. Orders and rules on single policies are signed from the Cabinet Room.'));
  }

  // ---- the courts --------------------------------------------------------------------------------------------------------

  function courtsTab(v: WestWingView): HTMLElement {
    const c = v.courts!;
    const seats = [...c.justices.map(j => h('div', { class: 'ww-justice ' + (j.party === 'D' ? 'd' : 'r') },
      h('strong', null, j.name), h('span', { class: 'muted small' }, `${j.age}, appointed by a ${j.party === 'D' ? 'Democrat' : 'Republican'}, ${j.lean}`))),
      ...Array.from({ length: c.vacancies }, () => h('div', { class: 'ww-justice vacant' }, h('strong', null, c.nominee ? 'Nominee pending' : 'Vacant')))];
    return h('div', { class: 'hill-body' },
      h('div', { class: 'room-grid two' },
        h('section', { class: 'room-card', 'aria-labelledby': 'ww-scotus' },
          h('h3', { class: 'room-card-title', id: 'ww-scotus' }, 'The Supreme Court'),
          h('div', { class: 'ww-court' }, ...seats),
          c.nominee ? h('p', null, `${c.nominee.name} is before the Senate: about ${c.nominee.expected} votes, ${Math.round(c.nominee.chance * 100)}% to be confirmed. The vote is in ${c.nominee.voteIn} weeks.`)
            : c.shortlist ? h('div', { class: 'ww-options' }, ...c.shortlist.map((j, i) => h('div', { class: 'ww-option' },
              h('strong', null, j.name), h('div', { class: 'muted small' }, `${j.what}, ${j.age}`),
              h('button', { class: 'btn small primary', type: 'button', disabled: !v.open, 'aria-label': 'Nominate ' + j.name, onclick: () => order({ type: 'NominateJustice', option: i }) }, 'Nominate'))))
            : h('p', { class: 'muted small' }, 'No vacancy. Justices retire, or die in office; the older they are, the likelier.')),
        h('section', { class: 'room-card', 'aria-labelledby': 'ww-lower' },
          h('h3', { class: 'room-card-title', id: 'ww-lower' }, 'The lower courts'),
          h('dl', { class: 'ww-stats' },
            h('dt', null, 'District judges your party appointed'), h('dd', null, bar(c.district * 100, 'District judges your party appointed, %')),
            h('dt', null, 'Appeals judges your party appointed'), h('dd', null, bar(c.circuit * 100, 'Appeals judges your party appointed, %'))),
          h('p', { class: 'small' }, `${c.judges} judges confirmed so far; ${c.districtVacancies} district and ${c.circuitVacancies} appeals seats open. Plaintiffs pick friendly courtrooms, so the district courts lean to whoever sues.`),
          h('div', { class: 'cab-buttons' }, ...c.strategies.map((name, i) => h('button', { class: 'btn small toggle' + (i === c.strategy ? ' on' : ''), type: 'button',
            'aria-pressed': String(i === c.strategy), title: c.strategyDesc[i], disabled: !v.open, onclick: () => order({ type: 'JudgeStrategy', strategy: i }) }, name))))),
      h('section', { class: 'room-card', 'aria-labelledby': 'ww-cases' },
        h('h3', { class: 'room-card-title', id: 'ww-cases' }, 'In court'),
        c.cases.length ? h('ul', { class: 'hill-list' }, ...c.cases.map(k => h('li', null,
          h('span', null, h('strong', null, k.title), h('span', { class: 'muted small' }, ` · ${k.action}`)),
          h('span', { class: 'pill' + (k.done ? (k.won ? ' tone-good' : ' tone-bad') : k.blocked ? ' tone-warn' : '') },
            k.done ? (k.votes ? (k.won ? 'Upheld ' : 'Struck down ') + k.votes : k.note || (k.won ? 'Won' : 'Lost')) : `${k.stage}${k.blocked ? ', blocked' : ''}${k.rulingIn !== undefined ? `, ruling in ${k.rulingIn} wk` : ''}`))))
          : h('p', { class: 'muted' }, 'No lawsuits against your orders and rules.')));
  }

  // ---- oversight --------------------------------------------------------------------------------------------------------

  function oversightTab(v: WestWingView): HTMLElement {
    const sc = v.scandals ?? [];
    return h('div', { class: 'hill-body' },
      h('div', { class: 'room-grid two' },
        h('section', { class: 'room-card', 'aria-labelledby': 'ww-sc' },
          h('h3', { class: 'room-card-title', id: 'ww-sc' }, 'Scandals'),
          sc.length ? h('ul', { class: 'hill-list' }, ...sc.map(x => h('li', null,
            h('span', null, h('strong', null, x.title), h('span', { class: 'muted small' }, ` · ${x.over ? 'over' : x.stage.toLowerCase()}${x.impeachable ? ' · could feed impeachment' : ''}`)),
            h('span', { class: 'ww-sev' }, bar(Math.min(100, x.severity * 10), x.title + ': severity')))))
            : h('p', { class: 'muted' }, 'Nothing is breaking.'),
          h('p', { class: 'muted small' }, 'Severity fades week by week; a good press secretary speeds it, new revelations set it back. A special counsel keeps it alive.')),
        h('section', { class: 'room-card', 'aria-labelledby': 'ww-ov' },
          h('h3', { class: 'room-card-title', id: 'ww-ov' }, 'Congress and impeachment'),
          (v.probes ?? []).length ? h('ul', { class: 'hill-list' }, ...(v.probes ?? []).map(p => h('li', null, h('span', null, `The ${p.chamber} on ${p.subject}`), h('span', { class: 'pill' }, p.status))))
            : h('p', { class: 'muted' }, 'No investigations.'),
          h('p', { class: 'small' }, `Impeachment: ${v.impeachment === 'none' || !v.impeachment ? 'no inquiry' : v.impeachment}. Pressure from the executive ${v.pressure} (defying the courts and the impeachable scandals; the House needs about 9 with its own investigations).`),
          h('p', { class: 'muted small' }, `${v.pardons} pardons and commutations; ${v.defiance} court orders slow-walked.`))),
      h('section', { class: 'room-card', 'aria-labelledby': 'ww-rec' },
        h('h3', { class: 'room-card-title', id: 'ww-rec' }, 'The record'),
        h('ol', { class: 'hill-record' }, ...(v.record ?? []).slice(0, 16).map(r => h('li', null, h('span', { class: 'muted' }, day(r.date, true)), ' ', r.what)))));
  }

  draw();
  return root;
}
