// The preview of the presidency: the Oval Office, live over the real engine. It opens a save in the first hundred
// days (or ?save=, for example saves/governing.json) and plays week by week: the calendar on the desk, The Capital
// Ledger after every week, watch mode, and the decisions that come up. The rooms behind the doors arrive in the
// presidency's later milestones; until then each says which milestone brings it.
import { engine, Refused, start } from './core/engine.js';
import type { Decision, PresidencyView } from './core/types.js';
import { h, reducedMotion } from './core/dom.js';
import { day } from './core/format.js';
import { host, toast } from './core/dialog.js';
import { closeMenu, menuOpen, openMenu } from './core/menu.js';
import { decisionCard } from './campaign/decision.js';
import { usePalette, type PaletteName } from './campaign/palette.js';
import { currentHour, lightFor } from './presidency/lighting.js';
import { congressLine, dateLong, ovalScreen, type SpotId } from './presidency/oval-screen.js';
import type { OvalRoom } from './presidency/oval.js';
import { calendar, cancelCalendarPick } from './presidency/calendar.js';
import { capitalLedger } from './presidency/paper.js';
import { SPEEDS, watchDialog } from './presidency/watch.js';

const root = document.getElementById('app')!;
const params = new URLSearchParams(location.search);
/** A fixed hour for the light (from ?hour= or the menu); null follows the player's clock. */
let hourOverride: number | null = params.has('hour') ? currentHour(params) : null;
const monthOverride = params.has('month') ? Number(params.get('month')) : null;
let room: OvalRoom | null = null;
let saveText = '';
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
  if (room && !reducedMotion() && now - last > 33) { room.tick(now / 1000); last = now; }
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
  host.open(h('div', { class: 'dialog-card' },
    h('div', { class: 'eyebrow' }, dateLong(v.date)),
    h('h2', { class: 'dialog-title' }, 'Beyond this preview'),
    h('p', null, 'The game has moved past the part of the presidency this preview shows. The day-100 report, the transition office and the rest of the presidency arrive in its later milestones.'),
    h('div', { class: 'dialog-foot' }, h('button', { class: 'btn primary', type: 'button', onclick: () => { host.close(); restart(); } }, 'Start again from the save'))), 'Beyond this preview');
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

function restart(): void { stopWatching(); engine.load(saveText); render(); }

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
    { label: 'Start again from the save', detail: 'Back to where this preview began', pick: restart },
    { label: 'The campaign preview', detail: 'Battleground 2028’s headquarters, in the war room', pick: () => { location.href = 'index.html'; } },
  ]);
}

/** Half an hour before sunset in Washington, for the menu's dusk. */
function duskHour(month: number): number {
  for (let hr = 21; hr > 15; hr -= 0.25) if (lightFor(hr, month).phase === 'dusk') return hr - 0.25;
  return 18;
}

// ---- what the room's objects and doors do in this milestone --------------------------------------------------------

const LATER: Record<string, { title: string; body: string; milestone: number; stage: string }> = {
  phone: { title: 'The telephone', body: 'Call members of Congress for their votes, with favours and promises, and foreign leaders about trade, alliances and crises.', milestone: 4, stage: 'Congress and the budget (calls abroad with milestone 7)' },
  roosevelt: { title: 'The Roosevelt Room', body: 'Congress: the twelve blocs and their leaders, the whip count, drafting and floor votes, hours on the Hill, the budget, shutdowns and the debt limit.', milestone: 4, stage: 'Congress and the budget' },
  westwing: { title: 'The West Wing', body: 'The staff and the cabinet: skills, morale, confirmations, executive orders and rules, the courts, and the vice president’s missions.', milestone: 5, stage: 'the executive, the press and storylines' },
  press: { title: 'The Press Briefing Room', body: 'Press conferences, interviews, addresses and the State of the Union; the outlets’ tone, the party’s wings and the opposition’s strategy.', milestone: 5, stage: 'the executive, the press and storylines' },
  windows: { title: 'The country', body: 'Approval by state and by voter group, from the same electorate that decided the election, and what the country thinks is going wrong.', milestone: 6, stage: 'the economy and the policy table' },
  residence: { title: 'The Residence', body: 'Family, rest and health: where stamina comes back and the president’s private life goes on.', milestone: 6, stage: 'the economy and the policy table' },
  globe: { title: 'The Situation Room', body: 'Forty countries, calls and summits, crises week by week, operations and covert action, and the standing deployments.', milestone: 7, stage: 'the world' },
};

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
    v.bills.length ? h('ul', { class: 'ov-dialog-list' }, ...v.bills.map(b => h('li', null, h('span', null, b.name), h('span', { class: 'pill' }, b.stage)))) : null,
    h('p', { class: 'muted' }, congressLine(v.congress) + '. The whip count and the floor open in the Roosevelt Room with milestone 4.'),
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
      host.open(card('The briefing book', 'The economy this week',
        h('dl', { class: 'ov-figures' },
          h('dt', null, 'Unemployment'), h('dd', null, v.unemployment == null ? '—' : v.unemployment.toFixed(1) + '%'),
          h('dt', null, 'Inflation'), h('dd', null, v.inflation == null ? '—' : v.inflation.toFixed(1) + '%'),
          h('dt', null, 'Growth'), h('dd', null, v.growth == null ? '—' : v.growth.toFixed(1) + '%'),
          h('dt', null, 'Gas'), h('dd', null, v.gas == null ? '—' : '$' + v.gas.toFixed(2) + ' a gallon')),
        v.issues ? h('table', { class: 'ov-issues' },
          h('caption', { class: 'muted' }, 'Approval on the issues'),
          h('thead', null, h('tr', null, h('th', { scope: 'col' }, 'Issue'), h('th', { scope: 'col' }, 'Approve'), h('th', { scope: 'col' }, 'Your command'))),
          h('tbody', null, ...[...v.issues].sort((a, b) => b.salience - a.salience).map(i => h('tr', null, h('th', { scope: 'row' }, i.label),
            h('td', null, Math.round(i.approval) + '%'), h('td', null, String(Math.round(i.command))))))) : null,
        h('p', { class: 'muted' }, 'The Council of Economic Advisers’ full briefing, with the policy table, opens with milestone 6.')), 'The briefing book');
      return;
    default:
      soon(id);
  }
}

// ---- keys --------------------------------------------------------------------------------------------------------------

document.addEventListener('keydown', e => {
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); closeMenu(false); if (!document.querySelector('.scrim')) endTurn(); }
  else if (e.key === 'Escape' && !menuOpen()) {
    if (document.querySelector('.cal-map.picking') && cancelCalendarPick()) { openCalendar(); return; }
    if (document.querySelector('.scrim')) host.close();
    else if (watching) stopWatching();
  }
});

// the light follows the clock: look again every five minutes
setInterval(() => { if (hourOverride === null && !watching && !document.querySelector('.scrim') && !menuOpen()) render(); }, 5 * 60 * 1000);

async function boot(): Promise<void> {
  usePalette((params.get('palette') as PaletteName) ?? 'standard');
  if (params.get('palette')) document.documentElement.dataset['palette'] = params.get('palette')!;
  if (params.get('motion') === 'reduce') document.documentElement.dataset['motion'] = 'reduce';
  if (params.get('size')) document.documentElement.style.setProperty('--s', params.get('size')!);
  await start();
  saveText = await (await fetch(params.get('save') ?? 'saves/presidency.json')).text();
  engine.load(saveText);
  render();
  requestAnimationFrame(frame);
}
boot().catch(e => { root.replaceChildren(h('p', { class: 'boot-error', role: 'alert' }, 'The game could not start: ' + String(e))); console.error(e); });
