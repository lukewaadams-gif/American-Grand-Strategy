// The campaign's war room, live over the real engine, as a screen the game mounts: headquarters, the week's run, the
// digest, the decisions and, after Election Day, election night. main.ts mounts it alone (the campaign preview);
// play-main.ts mounts it inside the whole game.
import { engine, Refused } from '../core/engine.js';
import type { CampaignView, NewsItem } from '../core/types.js';
import { h } from '../core/dom.js';
import { day, signed } from '../core/format.js';
import { cost } from './schedule.js';
import { compact } from './field.js';
import { resetWarRoomMemory, warRoom, type Digest, type WarRoomState } from './warroom.js';
import { decisionCard } from './decision.js';
import { host, toast } from '../core/dialog.js';
import { closeMenu, menuOpen } from '../core/menu.js';
import { adStudio } from './studio.js';

const root = document.getElementById('app')!;

/** What the game around the war room does: what comes after election night, and starting again. */
export interface CampaignHost {
  /** After election night: the game goes on (the transition, the second term, or the end). Without it, the preview offers to play again. */
  onElectionOver?: () => void;
  restart?: () => void;
}
let hostOpts: CampaignHost = {};
let active = false;

/** Shows the war room and takes the keyboard. */
export function mountCampaign(opts: CampaignHost = {}): void {
  hostOpts = opts;
  active = true;
  resetWarRoomMemory();
  state.selected = null; state.digest = null; state.picking = null;
  render();
}

/** Gives up the screen and the keyboard. */
export function unmountCampaign(): void { active = false; }
const state: WarRoomState = { layer: 'margin', selected: null, tab: 'schedule', picking: null, desk: 'overview', digest: null };
// keys 1-6 switch the left panel's tabs (as in Battleground 2028), when no dialog or menu is open and focus is not in a field
const TAB_KEYS: Record<string, WarRoomState['tab']> = { '1': 'schedule', '2': 'money', '3': 'field', '6': 'wire' };

function render(): void {
  let v: CampaignView;
  try { v = engine.campaign(); } catch (e) { toast(String(e)); return; }
  // after the vote the war room has nothing left to run: election night takes over (never a war room drawn from a
  // view with no campaign in it, which is what froze the last day of the campaign)
  if (v.phase !== 'GENERAL_CAMPAIGN_2028' && v.phase !== 'REELECTION_CAMPAIGN_2032') { electionOver(v); return; }
  const focusKey = document.activeElement?.getAttribute('aria-label') ?? null;
  const scroll = document.getElementById('main')?.scrollTop ?? 0;
  root.replaceChildren(warRoom(v, state, {
    runWeek, openDecisions, toast, order,
    setLayer: id => { state.layer = id; render(); },
    select: c => { state.selected = c; render(); },
    setTab: id => { state.tab = id; render(); document.getElementById('tab-' + id)?.focus(); },
    setDesk: d => { state.desk = d; render(); },
    studio: () => openStudio(),
    dismissDigest: () => { state.digest = null; render(); },
    pick: slot => {
      state.picking = slot;
      render();
      if (slot !== null) document.querySelector<SVGElement>('.usmap path.state[tabindex="0"]')?.focus({ preventScroll: false });
    },
  }));
  const main = document.getElementById('main');
  if (main) main.scrollTop = scroll;
  if (focusKey) [...root.querySelectorAll<HTMLElement>('[aria-label]')].find(e => e.getAttribute('aria-label') === focusKey)?.focus({ preventScroll: true });
  document.title = `Campaign ${v.electionDay.slice(0, 4)} · ${day(v.date)} · American Grand Strategy`;
}

/** A campaign order (schedule, ads, field, staff): applied at once and redrawn, or refused with the reason shown. */
function order(o: object): boolean {
  try { engine.command({ type: 'Campaign', order: o }); }
  catch (e) { toast(e instanceof Refused ? e.message : String(e)); return false; }
  render();
  return true;
}

/** The Ad Studio, a dialog over headquarters: redrawn in place after each change, keeping the focused control. */
function openStudio(): void {
  const active = document.activeElement as HTMLElement | null;
  const key = active?.getAttribute('aria-label') ?? active?.textContent ?? null;
  const v = engine.campaign();
  host.open(adStudio(v, {
    order: o => {
      try { engine.command({ type: 'Campaign', order: o }); } catch (e) { toast(e instanceof Refused ? e.message : String(e)); return false; }
      openStudio();
      return true;
    },
    refresh: () => openStudio(),
    close: () => host.close(),
  }), 'The Ad Studio', render);
  if (key) [...document.querySelectorAll<HTMLElement>('.studio button')].find(b => (b.getAttribute('aria-label') ?? b.textContent) === key)?.focus();
}

function openDecisions(after: () => void = render): void {
  const ds = engine.decisions();
  if (!ds.length) { after(); return; }
  decisionCard(ds[0]!, 1, ds.length, host, command => {
    try { return engine.command(command); } catch (e) { toast(e instanceof Refused ? e.message : String(e)); return null; }
  }, () => { render(); if (engine.decisions().length) openDecisions(after); });
}

function runWeek(): void {
  const v = engine.campaign();
  if (v.phase !== 'GENERAL_CAMPAIGN_2028' && v.phase !== 'REELECTION_CAMPAIGN_2032') return;
  if (v.pendingDecisions > 0) { openDecisions(runWeek); return; }
  let news: NewsItem[];
  try { news = engine.command({ type: 'EndTurn' }); } catch (e) { toast(e instanceof Refused ? e.message : String(e)); return; }
  state.picking = null;
  const after = engine.campaign();
  // Election Day: no digest of a day that ended the campaign; election night comes next
  state.digest = after.phase === v.phase ? digestOf(v, after, news) : null;
  render();
}

/** BG's makeDigest: raised and spent, the poll's move, volunteers and doors, the trail, and the turn's headline. */
function digestOf(before: CampaignView, after: CampaignView, news: NewsItem[]): Digest {
  const daily = before.turn?.daily ?? false;
  const title = daily ? `${['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'][before.turn!.day]} in review (${day(before.date)})`
    : `Week ${before.week} in review`;
  const nat = (x: CampaignView) => x.polls[x.polls.length - 1]?.national ?? 0;
  const lines: string[] = [];
  const m = after.money;
  if (m) lines.push(`Raised ${cost(m.raised)}, spent ${cost(m.spent)}  \u00b7  national polls ${signed(nat(after))} (${signed(nat(after) - nat(before))})`);
  const vol = (x: CampaignView) => x.field?.totals.volunteers ?? 0;
  if (after.field) lines.push(`Volunteers ${compact(vol(after))} (${vol(after) >= vol(before) ? '+' : '\u2212'}${compact(Math.abs(vol(after) - vol(before)))})  \u00b7  doors knocked ${compact(after.field.totals.doors)}`);
  const rallies = (after.trail?.rallies ?? 0) - (before.trail?.rallies ?? 0);
  const show = news.find(n => n.text.startsWith('PODCAST:') && !n.text.includes('passes on'));
  const trail = [rallies > 0 ? `${rallies} ${rallies === 1 ? 'rally' : 'rallies'}` : '', show ? show.text.replace(/^PODCAST: /, '').split('. ')[0] ?? '' : ''].filter(Boolean);
  if (trail.length) lines.push(trail.join('  \u00b7  '));
  const head = news.find(n => n.headline && !n.text.startsWith('PODCAST:'));
  if (head) lines.push(`${head.headline}: ${head.text.replace(/^Your opponent's campaign: /, '')}`);
  return { title, lines };
}

/** After Election Day: in the whole game, election night (the game's own screen) and the game goes on from there. */
function electionOver(v: CampaignView): void {
  if (hostOpts.onElectionOver) { const go = hostOpts.onElectionOver; go(); return; }
  const r = (v as CampaignView & { result?: { dem: number; rep: number; winner: 'D' | 'R'; popular: number } }).result;
  if (!r) return;
  // the campaign preview on its own (campaign.html): the night's result, and the campaign again
  const won = r.winner === v.player.party;
  host.open(h('div', { class: 'dialog-card' },
    h('div', { class: 'eyebrow' }, 'Election night · ' + day(v.electionDay, true)),
    h('h2', { class: 'dialog-title big' }, won ? 'You win the presidency' : 'You lose'),
    h('p', null, `Democrats ${r.dem}, Republicans ${r.rep}. Popular vote: ${r.popular >= 0 ? 'Democrats' : 'Republicans'} by ${Math.abs(r.popular).toFixed(1)}.`),
    h('p', { class: 'muted' }, 'This page is the campaign war room on its own. The whole game, from the campaign to the verdict, starts at the game’s title screen.'),
    h('div', { class: 'dialog-foot' },
      h('button', { class: 'btn primary', type: 'button', onclick: () => { host.close(); hostOpts.restart?.(); } }, 'Play the campaign again'))), 'Election night');
}

document.addEventListener('keydown', e => {
  if (!active) return;
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); closeMenu(false); runWeek(); }
  else if (TAB_KEYS[e.key] && !e.ctrlKey && !e.metaKey && !e.altKey && !menuOpen() && !document.querySelector('.scrim')
      && !(document.activeElement instanceof HTMLInputElement)) { state.tab = TAB_KEYS[e.key]!; render(); }
  else if (e.key === 'Escape') {
    if (menuOpen()) return;
    if (document.querySelector('.scrim')) host.close();
    else if (state.picking !== null) { state.picking = null; render(); }
    else if (state.selected) { state.selected = null; render(); }
  }
});
