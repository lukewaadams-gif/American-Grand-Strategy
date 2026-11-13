// The presidency as a screen the game mounts: the Oval Office, live over the real engine, played week by week (the
// calendar on the desk, The Capital Ledger after every week, watch mode, the decisions that come up) with the rooms
// behind its doors. presidency-main.ts mounts it over a save (the presidency preview); play-main.ts inside the game.
import { engine, Refused } from '../core/engine.js';
import type { Decision, PresidencyView } from '../core/types.js';
import { h, reducedMotion } from '../core/dom.js';
import { day } from '../core/format.js';
import { host, toast } from '../core/dialog.js';
import { closeMenu, menuOpen, openMenu } from '../core/menu.js';
import { decisionCard } from '../campaign/decision.js';
import { currentHour, lightFor } from './lighting.js';
import { congressLine, dateLong, ovalScreen, type SpotId } from './oval-screen.js';
import type { OvalRoom } from './oval.js';
import { calendar, cancelCalendarPick } from './calendar.js';
import { capitalLedger } from './paper.js';
import { SPEEDS, watchDialog } from './watch.js';
import { briefingBook, countryRoom } from './rooms.js';
import { cabinetRoom } from './cabinet.js';
import { scoreView } from './score.js';
import { rooseveltRoom } from './roosevelt.js';
import { westWingRoom } from './westwing.js';
import { pressRoom } from './pressroom.js';
import { sitRoom } from './sitroom.js';
import { residenceRoom } from './residence.js';
import { chime, soundMenuItem } from '../core/sound.js';
import type { MenuItem } from '../core/menu.js';

const root = document.getElementById('app')!;
const params = new URLSearchParams(location.search);
/** A fixed hour for the light (from ?hour= or the menu); null follows the player's clock. */
let hourOverride: number | null = params.has('hour') ? currentHour(params) : null;
const monthOverride = params.has('month') ? Number(params.get('month')) : null;
let room: OvalRoom | null = null;
/** What the game around the Oval Office does: where the game goes when the presidency's weeks stop, and starting again. */
export interface PresidencyHost {
  /** The game moves on (a campaign, the transition): the host shows what comes next. Without it, the preview says so. */
  onLeave?: () => void;
  restart?: () => void;
  /** The game's own menu entries (save, the start screen). */
  menuItems?: () => MenuItem[];
  restartLabel?: string;
}
let hostOpts: PresidencyHost = {};
let active = false, looping = false;

/** Shows the Oval Office and takes the keyboard. */
export function mountPresidency(opts: PresidencyHost = {}): void {
  hostOpts = opts;
  active = true;
  stopWatching();
  render();
  if (!looping) { looping = true; requestAnimationFrame(frame); }
}

/** Gives up the screen and the keyboard. */
export function unmountPresidency(): void { active = false; stopWatching(); room = null; }
/** Watch mode: the speed (an index into SPEEDS) and the timer for the next week; null when the player has the controls. */
let watching: { speed: number; timer: number } | null = null;
let speedChoice = 1;

function lightNow(v: PresidencyView) {
  const month = monthOverride && monthOverride >= 1 && monthOverride <= 12 ? monthOverride : Number(v.date.slice(5, 7));
  return lightFor(hourOverride ?? currentHour(new URLSearchParams()), month);
}

function render(): void {
  let v: PresidencyView;
  try { v = engine.presidency(); } catch (e) { toast(String(e)); return; }
  const focusKey = document.activeElement?.getAttribute('aria-label') ?? document.activeElement?.textContent ?? null;
  const screen = ovalScreen(v, lightNow(v), {
    endTurn, save, menu, spot: id => openSpot(id, v),
    watch: () => { if (watching) { watching.speed = (watching.speed + 1) % SPEEDS.length; render(); } else openWatch(); },
    stopWatching: () => stopWatching(),
    watching: watching ? SPEEDS[watching.speed]!.label : null,
  });
  room = screen.room;
  root.replaceChildren(screen.el);
  room.tick(reducedMotion() ? 0 : performance.now() / 1000);
  if (focusKey) [...root.querySelectorAll<HTMLElement>('button')].find(e => (e.getAttribute('aria-label') ?? e.textContent) === focusKey)?.focus({ preventScroll: true });
  document.title = `The Oval Office · ${day(v.date, true)} · American Grand Strategy`;
}

// ---- the room's moving parts: about 30 frames a second, none under reduced motion ------------------------------------

let last = 0;
function frame(now: number): void {
  if (active && room && !reducedMotion() && now - last > 33) { room.tick(now / 1000); last = now; }
  requestAnimationFrame(frame);
}

// ---- commands ----------------------------------------------------------------------------------------------------------

function command(c: object): boolean {
  try { engine.command(c); return true; } catch (e) { toast(e instanceof Refused ? e.message : String(e)); return false; }
}
const presidency = (order: object) => command({ type: 'Presidency', order });

// ---- the week ----------------------------------------------------------------------------------------------------------

function openDecisions(after: () => void = render): void {
  const ds = engine.decisions();
  if (!ds.length) { after(); return; }
  decisionCard(ds[0]!, 1, ds.length, host, c => {
    try { return engine.command(c); } catch (e) { toast(e instanceof Refused ? e.message : String(e)); return null; }
  }, () => { render(); if (engine.decisions().length) openDecisions(after); else after(); });
}

function endTurn(): void {
  const v = engine.presidency();
  if (!v.turnUnit || watching) return;
  if (v.pendingDecisions > 0) { openDecisions(endTurn); return; }
  if (!command({ type: 'EndTurn' })) return;
  chime();
  render();
  const after = engine.presidency();
  if (!after.turnUnit) { beyondPreview(after); return; }
  // Resolute Desk: the paper first, then what is waiting on the desk
  showPaper(after.pendingDecisions > 0 ? () => openDecisions() : undefined);
}

/** The Capital Ledger, full page; Enter or Escape goes back to the Oval Office. */
function showPaper(after?: () => void): void {
  const v = engine.presidency();
  const page = capitalLedger(engine.paper(), v, () => host.close());
  page.addEventListener('keydown', e => { if (e.key === 'Enter' && (e.target as HTMLElement).tagName !== 'BUTTON') { e.preventDefault(); host.close(); } });
  host.open(page, 'The Capital Ledger', after);
}

/** The calendar on the desk, redrawn after each change with the same control focused. */
function openCalendar(): void {
  const active = document.activeElement as HTMLElement | null;
  const key = active?.closest('.cal') ? active.getAttribute('aria-label') ?? active.textContent : null;
  host.open(calendar(engine.presidency(), {
    order: o => { const ok = presidency(o); openCalendar(); return ok; },
    redraw: () => openCalendar(),
    close: () => host.close(),
  }), 'The calendar', () => { cancelCalendarPick(); render(); });
  const again = key ? [...document.querySelectorAll<HTMLElement>('.cal button, .cal [role=button]')].find(b => (b.getAttribute('aria-label') ?? b.textContent) === key) : null;
  (again ?? document.querySelector<HTMLElement>('.cal-map.picking .cal-state[tabindex="0"]'))?.focus();
}

// ---- watch mode ----------------------------------------------------------------------------------------------------------

function openWatch(): void {
  const v = engine.presidency();
  if (!v.watch) return;
  const active = document.activeElement as HTMLElement | null;
  const key = active?.closest('.watch') ? active.id || active.textContent : null;
  host.open(watchDialog(v, speedChoice, {
    order: o => { const ok = presidency(o); openWatch(); return ok; },
    start: sp => { speedChoice = sp; host.close(); startWatching(sp); },
    close: () => host.close(),
  }), 'Watch mode');
  if (key) [...document.querySelectorAll<HTMLElement>('.watch button, .watch input')].find(b => (b.id || b.textContent) === key)?.focus();
}

function startWatching(speed: number): void {
  watching = { speed, timer: 0 };
  render();
  toast('Watching: the staff governs. Press Escape to take the controls back.');
  watching.timer = window.setTimeout(watchWeek, SPEEDS[speed]!.ms);
}

function stopWatching(reason = 'You have the controls.'): void {
  if (!watching) return;
  clearTimeout(watching.timer);
  watching = null;
  render();
  toast(reason);
}

/** One watched week: the staff answers what it may, plans the week by the plan, and plays it (Resolute Desk's Auto.week). */
function watchWeek(): void {
  if (!watching) return;
  const v = engine.presidency();
  if (!v.turnUnit) { stopWatching(); beyondPreview(v); return; }
  for (let guard = 0; guard < 10; guard++) {
    const d: Decision | undefined = engine.decisions()[0];
    if (!d) break;
    const stop = d.stop ?? -1;
    if (stop >= 0 && v.watch?.stops[stop]?.on) { stopWatching('A decision needs you: ' + d.title); openDecisions(); return; }
    const pick = d.options[d.recommended ?? 0] ?? d.options[0]!;
    if (!command(pick.command)) { stopWatching(); return; }
    toast(`The staff decided: ${d.title}. ${pick.label}.`);
  }
  if (!presidency({ type: 'WatchWeek' }) || !command({ type: 'EndTurn' })) { stopWatching(); return; }
  const after = engine.presidency();
  const w = after.week;
  if (w && w.stamina < 20) { stopWatching(`The president is exhausted (stamina ${Math.round(w.stamina)}).`); return; }
  render();
  if (!after.turnUnit) { stopWatching(); beyondPreview(after); return; }
  if (watching) watching.timer = window.setTimeout(watchWeek, SPEEDS[watching.speed]!.ms);
}

// ---- the game around the room --------------------------------------------------------------------------------------------

/** The preview covers the presidency in office; the day-100 report, the campaign of 2032 and the rest come later. */
function beyondPreview(v: PresidencyView): void {
  if (v.over) { showVerdict(); return; }
  if (hostOpts.onLeave) { host.close(); hostOpts.onLeave(); return; }
  host.open(h('div', { class: 'dialog-card' },
    h('div', { class: 'eyebrow' }, dateLong(v.date)),
    h('h2', { class: 'dialog-title' }, 'Beyond this preview'),
    h('p', null, 'The game has moved past the part of the presidency this preview shows. The day-100 report, the transition office and the rest of the presidency arrive in its later milestones.'),
    h('div', { class: 'dialog-foot' }, h('button', { class: 'btn primary', type: 'button', onclick: () => { host.close(); restart(); } }, 'Start again from the save'))), 'Beyond this preview');
}

/** The end: one verdict on the whole presidency (Resolute Desk's legacy screen). */
function showVerdict(): void {
  const vd = engine.verdict();
  const measures = vd.measures ?? [];
  host.open(h('div', { class: 'dialog-card verdict' },
    h('div', { class: 'eyebrow' }, dateLong(vd.date)),
    h('h2', { class: 'dialog-title' }, 'The verdict of history'),
    h('p', { class: 'verdict-grade' }, (vd.grade ?? '') + (vd.score !== undefined ? ` · ${vd.score} of 1,000` : '')),
    vd.rank ? h('p', { class: 'verdict-rank' }, vd.rank) : null,
    measures.length ? h('dl', { class: 'ww-stats verdict-measures' }, ...measures.flatMap(m => [h('dt', null, m.name),
      h('dd', null, h('span', { class: 'hill-meter ' + (m.score >= 60 ? 'good' : m.score >= 40 ? 'mid' : 'bad'), role: 'meter', 'aria-valuemin': '0', 'aria-valuemax': '100',
        'aria-valuenow': String(m.score), 'aria-label': m.name }, h('span', { style: `width:${Math.max(2, m.score)}%` }), h('span', { class: 'hill-meter-value' }, String(m.score))))])) : null,
    h('ul', { class: 'hill-list' }, ...(vd.lines ?? []).map(l => h('li', null, l))),
    (vd.record ?? []).length ? h('details', null, h('summary', null, 'Your record'), h('ul', { class: 'hill-list small' }, ...(vd.record ?? []).map(l => h('li', null, l)))) : null,
    h('div', { class: 'dialog-foot' }, h('button', { class: 'btn primary', type: 'button', onclick: () => { host.close(); restart(); } }, hostOpts.restartLabel ?? 'Start again from the save'))), 'The verdict of history');
}

function save(): void {
  try {
    const blob = new Blob([engine.save()], { type: 'application/json' });
    const a = h('a', { href: URL.createObjectURL(blob), download: 'american-grand-strategy-presidency.json' });
    document.body.append(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
    toast('Saved: the file is in your downloads.');
  } catch (e) { toast(String(e)); }
}

function restart(): void { stopWatching(); hostOpts.restart?.(); }

function menu(anchor: HTMLElement): void {
  const v = engine.presidency();
  const month = Number(v.date.slice(5, 7));
  const at = (label: string, hour: number | null, detail: string) => ({
    label, detail: (hourOverride === hour ? 'Now · ' : '') + detail,
    pick: () => { hourOverride = hour; render(); toast('The light: ' + lightNow(v).label.toLowerCase() + '.'); },
  });
  openMenu(anchor, 'Menu', [
    at('Light: follow my clock', null, 'The windows show your own time of day'),
    at('Light: morning', 9, 'Sun from the east, through the left of the windows'),
    at('Light: afternoon', 15, 'Sun from the west'),
    at('Light: dusk', duskHour(month), 'The last of the light'),
    at('Light: night', 22, 'Lamps lit, the monument floodlit'),
    soundMenuItem(),
    ...(hostOpts.menuItems ? hostOpts.menuItems() : [
      { label: 'Start again from the save', detail: 'Back to where this preview began', pick: restart },
      { label: 'The whole game', detail: 'From a new campaign to the verdict on your presidency', pick: () => { location.href = 'index.html'; } },
      { label: 'The campaign preview', detail: 'Battleground 2028’s headquarters, in the war room', pick: () => { location.href = 'campaign.html'; } },
    ]),
  ]);
}

/** Half an hour before sunset in Washington, for the menu's dusk. */
function duskHour(month: number): number {
  for (let hr = 21; hr > 15; hr -= 0.25) if (lightFor(hr, month).phase === 'dusk') return hr - 0.25;
  return 18;
}

// ---- what the room's objects and doors do in this milestone --------------------------------------------------------

const LATER: Record<string, { title: string; body: string; milestone: number; stage: string }> = {};

const west = (order: object) => command({ type: 'WestWing', order });
const ISSUES = [
  { id: 'ECONOMY', label: 'The economy' }, { id: 'COST_OF_LIVING', label: 'Cost of living' }, { id: 'HEALTHCARE', label: 'Health care' },
  { id: 'IMMIGRATION', label: 'Immigration' }, { id: 'ABORTION', label: 'Abortion' }, { id: 'CRIME', label: 'Crime and safety' },
  { id: 'CLIMATE_ENERGY', label: 'Climate and energy' }, { id: 'CHINA', label: 'China' }, { id: 'AI_TECH', label: 'AI and jobs' },
  { id: 'DEMOCRACY', label: 'Democracy and corruption' }, { id: 'EDUCATION', label: 'Education' }];

/** The West Wing: the staff, the cabinet, the vice president, orders and rules, the courts and oversight; the Cabinet Room down the hall. */
function westWing(tab?: 'Staff' | 'Cabinet' | 'Orders and rules'): void {
  host.open(westWingRoom({
    view: () => engine.westWing(),
    options: (kind, index) => engine.westWingOptions(kind, index),
    order: o => west(o),
    decide: () => { if (engine.decisions().length) { host.close(); openDecisions(); } },
    cabinetRoom: () => { host.close(); openCabinet(); },
    back: () => host.close(),
    issues: ISSUES,
  }, tab), 'The West Wing', render);
}

/** The Press Briefing Room: the press, speeches, posts and the party. */
function openPress(first?: 'The press' | 'Speeches' | 'Social media' | 'Your party' | 'Your term'): void {
  host.open(pressRoom({
    view: () => engine.press(),
    order: o => west(o),
    decide: () => { if (engine.decisions().length) { host.close(); openDecisions(); } },
    back: () => host.close(),
    terms: () => engine.terms(),
    termsOrder: o => command({ type: 'Terms', order: o }),
  }, first), 'The Press Briefing Room', render);
}

/** The Situation Room: the world, the brief, hot spots, operations, trade, alliances, wars and statecraft. */
function openSitRoom(): void {
  host.open(sitRoom({
    view: () => engine.sitRoom(),
    country: code => engine.foreignCountry(code),
    order: o => command({ type: 'Foreign', order: o }),
    decide: () => { if (engine.decisions().length) { host.close(); openDecisions(); } },
    back: () => host.close(),
  }), 'The Situation Room', render);
}

function openCabinet(): void {
  host.open(cabinetRoom(engine.cabinet(), {
    score: (policy, steps) => engine.score(policy, steps),
    billScore: bill => engine.billScore(bill),
    draft: source => { if (hill({ type: 'Draft', source })) { host.close(); openRoosevelt('Bills'); } },
    policyAction: (policy, to, rule) => { if (west({ type: 'PolicyAction', policy, to, rule })) { host.close(); westWing('Orders and rules'); } },
    back: () => host.close(),
  }), 'The Cabinet Room');
}

const hill = (order: object) => command({ type: 'Hill', order });

/** The Roosevelt Room: Congress. The telephone opens it on the members, whose votes are what calls are for at home. */
function openRoosevelt(tab?: 'The Hill' | 'Bills' | 'Members'): void {
  host.open(rooseveltRoom({
    view: () => engine.hill(),
    order: o => hill(o),
    decide: () => { host.close(); openDecisions(); },
    back: () => host.close(),
  }, tab), 'The Roosevelt Room', render);
}

/** A bill's score behind a disclosure, run when it is opened (the budget office takes a moment). */
function billScoreDisclosure(id: string, law: boolean): HTMLElement {
  if (law) return h('span', { class: 'muted small' }, 'Law: its effects build in the briefing book');
  const body = h('div', { class: 'ov-score' });
  const d = h('details', { class: 'ov-score-wrap' }, h('summary', null, 'The budget office’s score'), body);
  d.addEventListener('toggle', () => {
    if (d.open && !body.childElementCount) {
      try { body.append(scoreView(engine.billScore(id), 'h4')); } catch (e) { body.append(h('p', { class: 'muted small' }, 'No score: ' + String(e))); }
    }
  });
  return d;
}

function soon(id: string): void {
  const l = LATER[id]!;
  host.open(h('div', { class: 'dialog-card' },
    h('div', { class: 'eyebrow' }, 'Coming in presidency milestone ' + l.milestone),
    h('h2', { class: 'dialog-title' }, l.title),
    h('p', null, l.body),
    h('p', { class: 'muted' }, `It opens with milestone ${l.milestone}: ${l.stage}.`),
    h('div', { class: 'dialog-foot' }, h('button', { class: 'btn primary', type: 'button', onclick: () => host.close() }, 'Back to the Oval Office'))), l.title);
}

function card(eyebrow: string, title: string, ...body: (HTMLElement | null)[]): HTMLElement {
  return h('div', { class: 'dialog-card' }, h('div', { class: 'eyebrow' }, eyebrow), h('h2', { class: 'dialog-title' }, title), ...body,
    h('div', { class: 'dialog-foot' }, h('button', { class: 'btn primary', type: 'button', onclick: () => host.close() }, 'Back to the Oval Office')));
}

/** The agenda's folders, and in the first hundred days the business the chief of staff can run until the rooms open. */
function openAgenda(): void {
  const v = engine.presidency();
  const hd = v.hundredDays;
  host.open(card('Your agenda', v.bills.length ? `${v.bills.length} ${v.bills.length === 1 ? 'bill' : 'bills'} on your agenda` : 'No agenda yet',
    v.bills.length ? h('ul', { class: 'ov-dialog-list ov-agenda' }, ...v.bills.map(b => h('li', null,
      h('div', { class: 'ov-agenda-head' }, h('span', null, b.name), h('span', { class: 'pill' }, b.stage)),
      billScoreDisclosure(b.id, b.stage === 'Law')))) : null,
    h('p', { class: 'muted' }, congressLine(v.congress) + '. ' + (v.hundredDays ? 'The first hundred days carry the flagship themselves; the Roosevelt Room opens on day 100.' : 'Bills are drafted, whipped and sent from the Roosevelt Room.') + ' Every policy and its score is in the Cabinet Room, in the West Wing.'),
    hd ? h('section', { class: 'ov-delegate', 'aria-labelledby': 'ov-delegate-title' },
      h('h3', { class: 'ov-delegate-title', id: 'ov-delegate-title' }, `The first hundred days, week ${Math.min(hd.week, hd.weeks)} of ${hd.weeks}: hand it to the staff`),
      h('p', { class: 'muted' }, 'Until Congress and the West Wing open in their rooms, the chief of staff can run the hundred days’ business. The staff is cautious: it trades for votes at half strength and nominates experts.'),
      ...hd.domains.map(d => h('button', { class: 'btn small toggle' + (d.on ? ' on' : ''), type: 'button', 'aria-pressed': String(d.on), title: d.detail,
        onclick: () => { if (command({ type: 'Act', action: { type: 'Delegate', domain: d.id, on: !d.on } })) openAgenda(); } },
        d.label + ': ', h('strong', null, d.on ? 'the staff' : 'you')))) : null), 'Your agenda');
}

function openSpot(id: SpotId, v: PresidencyView): void {
  switch (id) {
    case 'desk': if (v.week) openCalendar(); else toast('The calendar opens at the inauguration.'); return;
    case 'paper': showPaper(); return;
    case 'folders': openAgenda(); return;
    case 'documents': {
      if (v.pendingDecisions > 0) { openDecisions(); return; }
      const laws = v.bills.filter(b => b.stage === 'Law');
      host.open(card('The Resolute Desk', 'Nothing waiting for your signature',
        h('p', null, 'Decisions that need the president, and the bills Congress sends to the desk, wait here to be signed or vetoed.'),
        laws.length ? h('p', { class: 'muted' }, 'Signed into law from your agenda: ' + laws.map(b => b.name).join(', ') + '.') : null), 'Documents to sign');
      return;
    }
    case 'book':
      if (!v.week) { toast('The briefing book opens at the inauguration.'); return; }
      host.open(briefingBook(engine.economy(), () => host.close()), 'The briefing book: the economy');
      return;
    case 'windows':
      if (!v.week) { toast('The country\u2019s verdict starts at the inauguration.'); return; }
      host.open(countryRoom(engine.country(), () => host.close()), 'The country');
      return;
    case 'westwing':
      if (!v.week) { toast('The West Wing is staffed on day 100.'); return; }
      westWing();
      return;
    case 'press':
      openPress();
      return;
    case 'globe':
      openSitRoom();
      return;
    case 'residence':
      if (!v.week) { toast('The Residence is yours at the inauguration.'); return; }
      host.open(residenceRoom({
        view: () => engine.residence(),
        presidency: o => { try { engine.command({ type: 'Presidency', order: o }); return true; } catch { return false; } },
        calendar: () => { host.close(); openCalendar(); },
        back: () => host.close(),
      }), 'The Residence', render);
      return;
    case 'roosevelt':
      openRoosevelt();
      return;
    case 'phone':
      if (!v.week) { toast('The telephone is yours at the inauguration.'); return; }
      openRoosevelt('Members');
      return;
    default:
      soon(id);
  }
}

// ---- keys --------------------------------------------------------------------------------------------------------------

document.addEventListener('keydown', e => {
  if (!active) return;
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); closeMenu(false); if (!document.querySelector('.scrim')) endTurn(); }
  else if (e.key === 'Escape' && !menuOpen()) {
    if (document.querySelector('.cal-map.picking') && cancelCalendarPick()) { openCalendar(); return; }
    if (document.querySelector('.scrim')) host.close();
    else if (watching) stopWatching();
  }
});

// the light follows the clock: look again every five minutes
setInterval(() => { if (active && hourOverride === null && !watching && !document.querySelector('.scrim') && !menuOpen()) render(); }, 5 * 60 * 1000);
