// The tutorial (docs/TUTORIAL.md): a guided walk through the campaign and the first year in office, on the real game.
// A coach panel narrates; the game plays itself through routine stretches at the chosen speed and stops at the moments
// that matter, where the player makes the move (or asks the coach to). After each move the coach shows what it moved
// (the engine's own reasons), once it replays a week two ways, and each chapter ends with a recap. Chapters can be
// jumped to and replayed; progress is remembered; and "Play from here" leaves the coach with the game as it stands.
import { buildFile, engine, Refused } from '../core/engine.js';
import { h } from '../core/dom.js';
import { host, toast } from '../core/dialog.js';
import { openMenu } from '../core/menu.js';
import type { CampaignView, PresidencyView } from '../core/types.js';
import { mapCard, systemsMap, type NodeId } from './map.js';

/** What the game page lends the tutorial: redrawing the screen for the game as it stands, and leaving. */
export interface TutorialHooks {
  /** Shows the screen for where the game stands (play-main's route). */
  refresh(): void;
  /** Leaves the tutorial: "Play from here" keeps the game; "Back to the title" does not. */
  done(keepPlaying: boolean): void;
}

type Speed = 'slow' | 'normal' | 'fast';
const DELAY: Record<Speed, number> = { slow: 1400, normal: 650, fast: 120 };

interface Ctx {
  /** Waits (and lets the player pause) between autoplayed turns. */
  turn(): Promise<void>;
  campaign(): CampaignView | null;
  presidency(): PresidencyView | null;
  command(c: object): boolean;
  refresh(): void;
  /** Shows a card in the coach and waits for Next. */
  card(eyebrow: string, title: string, ...body: (HTMLElement | string | null)[]): Promise<void>;
}

/** A step: narration, a spotlight, a move to make (with a predicate and a way to do it for the player), autoplay, or code. */
interface Step {
  say: string;
  /** Elements to ring while the step is shown (CSS selectors). */
  spot?: string[];
  /** The player's move: done when this holds. */
  until?: () => boolean;
  /** What "Do it for me" does. */
  doIt?: (c: Ctx) => void | Promise<void>;
  /** Code that runs when the step opens (autoplay, a comparison...); the step moves on when it finishes, if it has no until. */
  run?: (c: Ctx) => Promise<void>;
  /** Systems map parts this step lights. */
  lights?: NodeId[];
  /** After its run, wait on the narration for Next (otherwise a run step moves straight on). */
  pauseAfter?: boolean;
}

interface Chapter {
  title: string;
  minutes: number;
  /** The moment the chapter starts from when jumped to, or when the game is not where the chapter needs it. */
  moment: string;
  /** Whether the game as it stands can carry on into this chapter. */
  fits: () => boolean;
  steps: Step[];
  recap: string[];
}

const KEY = 'ags-tutorial';
const memory = {
  get(): number { try { const v = localStorage.getItem(KEY); return v ? Math.max(0, Number(JSON.parse(v).chapter) || 0) : 0; } catch { return 0; } },
  set(chapter: number): void { try { localStorage.setItem(KEY, JSON.stringify({ chapter })); } catch { /* no storage: nothing remembered */ } },
  clear(): void { try { localStorage.removeItem(KEY); } catch { /* nothing to clear */ } },
};

/** Where the tutorial last stopped (a chapter index), or 0. */
export function tutorialProgress(): number { return memory.get(); }

const safe = <T>(f: () => T): T | null => { try { return f(); } catch { return null; } };
const phase = () => safe(() => engine.presidency().phase) ?? '';
const inCampaign = () => phase() === 'GENERAL_CAMPAIGN_2028';
const lead = (x: number) => `${x >= 0 ? 'you +' : 'them +'}${Math.abs(x).toFixed(1)}`;
const pts = (x: number) => `${x >= 0 ? '+' : '−'}${Math.abs(x).toFixed(1)}`;
const forMe = (v: CampaignView, nat: number) => nat * (v.player.party === 'D' ? 1 : -1);

/** Answers whatever blocks the turn with the staff's recommendation, then plays it. */
function playTurn(): boolean {
  for (let i = 0; i < 12; i++) {
    const d = safe(() => engine.decisions().find(x => x.blocking !== false));
    if (!d) break;
    const o = d.options[d.recommended ?? 0] ?? d.options[0];
    if (!o) break;
    try { engine.command(o.command); } catch { break; }
  }
  const night = safe(() => engine.presidency().electionNight) ?? 0;
  if (night) { try { engine.command({ type: 'ElectionNightSeen', year: night }); } catch { /* shown already */ } return true; }
  try { engine.command({ type: 'EndTurn' }); return true; } catch (e) { toast(e instanceof Refused ? e.message : String(e)); return false; }
}

/** Every part of the campaign back to the staff, so autoplay runs a competent campaign. */
function staffEverything(): void {
  for (const area of ['SCHEDULE', 'ADS', 'FIELD', 'SURROGATES'])
    try { engine.command({ type: 'Campaign', order: { type: 'Staff', area, on: true } }); } catch { /* not campaigning */ }
}

// ---- the chapters -----------------------------------------------------------------------------------------------------

const CHAPTERS: Chapter[] = [
  {
    title: 'How the game fits together', minutes: 1, moment: 'saves/campaign.json', fits: () => inCampaign(),
    steps: [
      { say: 'Welcome to American Grand Strategy. You win the White House in a campaign war room, then govern from the Oval Office, week by week. This tutorial plays a real game with you: about fifteen minutes, from the 2028 campaign through the first year in office.' },
      { say: 'The game is built in layers, and every layer moves the others. Your campaign moves the polls; the polls decide election night; in office, approval, the economy, Congress, the press and the world move each other, and all of them decide the next election. The map shows it: it lights up as we go, and the "Map" button opens it at any time.',
        lights: ['message', 'money', 'field', 'team', 'polls', 'night', 'approval', 'economy', 'congress', 'press', 'world', 'midterms', 'reelection'] },
      { say: 'How the tutorial works: where a move matters, the coach rings the place on screen and waits for you to make it. "Do it for me" makes it instead. Routine stretches play themselves at the speed you choose (the buttons above). "Chapters" jumps around; "Play from here" leaves the coach and keeps the game.' },
    ],
    recap: ['The campaign moves the polls; the polls decide election night.', 'In office, approval, the economy, Congress, the press and the world move each other.', 'Everything you do in office is judged at the next election.'],
  },
  {
    title: 'The war room', minutes: 3, moment: 'saves/campaign.json', fits: () => inCampaign(),
    steps: [
      { say: 'This is the war room, nine weeks into the 2028 campaign: Josh Shapiro against JD Vance. Along the top: your cash, the national polls, the electoral votes if every state poll is right, and the forecast.', spot: ['.cw-kpis'], lights: ['polls'] },
      { say: 'The map is the latest state polls: blue leans Democratic, red Republican. Click Pennsylvania: the race may turn on it.', spot: ['.usmap path.state[data-code="PA"]'],
        until: () => !!document.querySelector('.state-desk h3')?.textContent?.includes('Pennsylvania'),
        doIt: () => { (document.querySelector('.usmap path.state[data-code="PA"]') as SVGElement | null)?.dispatchEvent(new MouseEvent('click', { bubbles: true })); } },
      { say: 'The state desk: the poll here, your chance of winning it, and the money and visits both sides have put in. A visit from the candidate moves a state for weeks. The staff has filled the week, so the coach has freed its last evening: press any of the "Send" buttons to put Shapiro in Pennsylvania then.', spot: ['.send-row'],
        run: async c => {
          c.command({ type: 'Campaign', order: { type: 'Slot', slot: 20, activity: 'NONE', state: null, param: 0 } });
          c.refresh();
          (document.querySelector('.usmap path.state[data-code="PA"]') as SVGElement | null)?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
          paSlots = (safe(() => engine.campaign().schedule) ?? []).filter(sl => sl.state === 'PA').length;
        },
        until: () => (safe(() => engine.campaign().schedule) ?? []).filter(sl => sl.state === 'PA').length > paSlots,
        doIt: () => (document.querySelector('.state-desk .send-row button:not([aria-disabled="true"])') as HTMLButtonElement | null)?.click(), lights: ['field'] },
      { say: 'Money pays for everything. The Money tab buys TV market by market (the air war), digital by platform, and sets the headquarters; the Ad Studio makes and tests the ads. Fundraising is a race of its own: big donors run dry, small donors grow with enthusiasm.', spot: ['#tab-money'], lights: ['money'] },
      { say: 'The Field tab is the ground game: offices and organisers recruit volunteers, and volunteers knock doors. It is slow to build and it matters most at the end, when it turns out your voters.', spot: ['#tab-field'], lights: ['field'] },
      { say: 'The message: one national issue a week. It works by how much voters care about it and by how far they trust your party on it. Open the Message tab and choose an issue.', spot: ['#tab-message'],
        until: () => { const m = safe(() => engine.campaign().message); return !!m && !m.staffChooses; },
        doIt: c => { c.command({ type: 'Campaign', order: { type: 'Message', issue: 'HEALTHCARE' } }); c.refresh(); }, lights: ['message'] },
      { say: 'Now run the week: both campaigns carry out their plans, and new polls come in. Press "Run the week".', spot: ['.cw-actions .btn.primary'],
        run: async c => { before = c.campaign(); },
        until: () => !!before && (safe(() => engine.campaign().week) ?? 0) > before.week,
        doIt: c => { playTurn(); c.refresh(); } },
      { say: '', run: async c => {
        const a = before, b = c.campaign();
        if (!a || !b) return;
        const last = b.polls[b.polls.length - 1], prev = a.polls[a.polls.length - 1];
        const pa = (v: CampaignView) => v.states.find(s => s.code === 'PA')?.forPlayer ?? 0;
        await c.card('Cause and effect', 'What your week moved',
          h('ul', { class: 'tut-deltas' },
            h('li', null, h('strong', null, 'Nationally: '), `${lead(forMe(a, prev?.national ?? 0))} → ${lead(forMe(b, last?.national ?? 0))}`),
            h('li', null, h('strong', null, 'Pennsylvania: '), `${lead(pa(a))} → ${lead(pa(b))}`)),
          h('p', { class: 'muted small' }, 'Why, in the engine’s own words:'),
          h('ul', { class: 'tut-why' }, ...(last?.movers ?? []).slice(0, 4).map(m => h('li', null, m))),
          'Your opponent adapts too: the right column says what their campaign is doing and why.');
      }, lights: ['money', 'polls'] },
    ],
    recap: ['The map is the state polls; the race is won state by state, to 270.', 'Visits, ads and the field move a state; the message moves the nation.', 'Each week the polls say what moved them, and your opponent changes course.'],
  },
  {
    title: 'Polls, voters and your team', minutes: 3, moment: 'saves/campaign.json', fits: () => inCampaign(),
    steps: [
      { say: 'Public polls miss, and all in the same direction. Your own pollster is closer to the truth, at a price. Open the Polls tab and commission a poll of a state.', spot: ['#tab-polls'],
        until: () => (safe(() => engine.campaign().pollsDesk?.internal) ?? []).length > 0,
        doIt: c => { c.command({ type: 'Campaign', order: { type: 'InternalPoll', state: 'PA' } }); c.refresh(); }, lights: ['polls'] },
      { say: 'Who votes in a state? Pick a state on the map, then its "Voters" tab: each group’s share, how it leans, how many turn out, and how movable it is. Persuade the movable; turn out your own side.', spot: ['.desk-tabs'],
        until: () => !!document.querySelector('.mini-table.voters'),
        doIt: async () => {
          (document.querySelector('.usmap path.state[data-code="PA"]') as SVGElement | null)?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
          await new Promise(r => setTimeout(r, 150));
          ([...document.querySelectorAll('.desk-tabs button')].find(b => b.textContent === 'Voters') as HTMLButtonElement | undefined)?.click();
        } },
      { say: 'Your team: the senior staff you can hire, and your surrogates (the running mate, your spouse and two allies) on tour. Open the Team tab and send a surrogate somewhere this week.', spot: ['#tab-team'],
        until: () => safe(() => engine.campaign().team?.staffRuns) === false,
        doIt: c => { c.command({ type: 'Campaign', order: { type: 'Surrogate', slot: 0, state: 'PA', lead: null } }); c.refresh(); }, lights: ['team'] },
      { say: 'One choice, two futures. The coach plays next week twice from this moment: once with a contrast message, once with an attack.', run: async c => {
        const save = engine.save();
        const try1 = (tone: string) => {
          engine.load(save);
          c.command({ type: 'Campaign', order: { type: 'MessageTone', tone } });
          playTurn();
          const v = c.campaign()!;
          return forMe(v, v.polls[v.polls.length - 1]?.national ?? 0);
        };
        const contrast = try1('CONTRAST'), attack = try1('NEGATIVE');
        engine.load(save);
        c.refresh();
        await c.card('Before and after', 'Contrast or attack: the same week, twice',
          h('table', { class: 'mini-table tut-compare' },
            h('thead', null, h('tr', null, h('th', { scope: 'col' }, 'Tone'), h('th', { scope: 'col' }, 'National poll after the week'))),
            h('tbody', null, h('tr', null, h('th', { scope: 'row' }, 'Contrast'), h('td', null, lead(contrast))),
              h('tr', null, h('th', { scope: 'row' }, 'Attack'), h('td', null, lead(attack))))),
          `An attack works harder and dampens their side, but about one week in seven it backfires. The difference here: ${pts(attack - contrast)} points. Nothing was kept: the game is as it was.`);
      } },
    ],
    recap: ['Internal polls cost money and miss by less.', 'The Voters desk says whom to persuade and whom to turn out.', 'Surrogates and hires extend the candidate; the tone of the message is a risk you choose.'],
  },
  {
    title: 'To Election Day', minutes: 2, moment: 'saves/daily.json', fits: () => inCampaign(),
    steps: [
      { say: 'The staff can run every part of the campaign. Let them take it from here to Election Day; the last two weeks go a day at a time. Change the speed or pause above.', run: async c => {
        staffEverything();
        for (let i = 0; i < 60 && inCampaign(); i++) { playTurn(); c.refresh(); await c.turn(); }
      } },
      { say: 'Election night: the polls close coast to coast and the races are called as the count comes in. The broadcast is the game’s own; the result was decided on Election Day and is never recomputed.', lights: ['night'],
        run: async c => { c.refresh(); }, pauseAfter: true },
      { say: '', run: async c => {
        const night = safe(() => engine.presidency().electionNight) ?? 0;
        if (night) c.command({ type: 'ElectionNightSeen', year: night });
        if (phase() !== 'TRANSITION') {
          await c.card('Election night', 'This time, the other side won',
            'In the game, that is the end: the campaign gets its assessment. The tutorial goes on with a presidency that won, to show you the rest.');
        }
      } },
    ],
    recap: ['The staff can run the campaign; you can take any part back.', 'The final stretch is played a day at a time.', 'The election is decided once, on Election Day.'],
  },
  {
    title: 'The transition', minutes: 2, moment: 'saves/transition.json', fits: () => phase() === 'TRANSITION',
    steps: [
      { say: 'The transition office, between election night and the oath: you set the agenda you take to Congress, and fill the cabinet and the staff. The first priority becomes the flagship of the first hundred days.', spot: ['.cab-tabs'], lights: ['congress'] },
      { say: 'Promises made in the campaign belong on the agenda: each one left off costs you with your base. When the agenda is set, take the oath of office.', spot: ['.transition .btn.primary'],
        until: () => phase() === 'FIRST_100_DAYS',
        doIt: c => {
          const t = safe(() => engine.transition());
          if (t && !t.agenda.length && t.bills.length) c.command({ type: 'SetAgenda', bills: t.bills.slice(0, 2).map(b => b.id) });
          c.command({ type: 'EndTurn' });
          c.refresh();
        } },
    ],
    recap: ['The agenda is what you take to Congress; the first priority is the flagship.', 'Campaign promises are expected on it.'],
  },
  {
    title: 'The first hundred days', minutes: 3, moment: 'saves/presidency.json', fits: () => phase() === 'FIRST_100_DAYS',
    steps: [
      { say: 'The Oval Office. Everything is here: the calendar on the desk plans the president’s week, the paper is the week’s news, the doors lead to Congress, the West Wing, the press and the Situation Room.', spot: ['.ov-spots'], lights: ['approval'] },
      { say: 'The calendar on the desk is the president’s week: 21 slots for meetings, travel, speeches and rest. Stamina is real: a president worn out makes worse decisions. The chief of staff can fill it for you.', spot: ['.ov-spot-desk'] },
      { say: 'The paper, The Capital Ledger, comes after every week: what happened, and why approval moved. It is the best place to see one thing moving another.', spot: ['.ov-spot-paper'], lights: ['press'] },
      { say: 'End the week: your schedule, Congress and the world all move, and The Capital Ledger reports. Press the button.', spot: ['.ov-end'],
        run: async c => { pres = c.presidency(); },
        until: () => !!pres && (safe(() => engine.presidency().termWeek) ?? 0) > pres.termWeek,
        doIt: c => { playTurn(); c.refresh(); } },
      { say: '', run: async c => {
        const a = pres, b = c.presidency();
        if (!a || !b) return;
        await c.card('Cause and effect', 'One week in office',
          h('ul', { class: 'tut-deltas' },
            h('li', null, h('strong', null, 'Approval: '), `${Math.round(a.approval ?? 0)}% → ${Math.round(b.approval ?? 0)}%`),
            b.unemployment != null ? h('li', null, h('strong', null, 'Unemployment: '), `${(a.unemployment ?? 0).toFixed(1)}% → ${b.unemployment.toFixed(1)}%`) : null,
            b.inflation != null ? h('li', null, h('strong', null, 'Inflation: '), `${(a.inflation ?? 0).toFixed(1)}% → ${b.inflation.toFixed(1)}%`) : null),
          'Approval moves with the economy, with what Congress does, with the press, and with events abroad: watch The Capital Ledger each week for why.');
      }, lights: ['economy', 'press'] },
      { say: 'The rest of the hundred days play themselves now; day 100 brings a report card.', run: async c => {
        for (let i = 0; i < 20 && phase() === 'FIRST_100_DAYS'; i++) { playTurn(); c.refresh(); await c.turn(); }
      } },
    ],
    recap: ['The week is the turn in office: your calendar, then Congress, the economy and the world.', 'Approval is the sum of everything; the paper says why it moved.', 'Day 100 grades the opening; the presidency goes on.'],
  },
  {
    title: 'Congress and the economy', minutes: 3, moment: 'saves/governing.json', fits: () => phase() === 'GOVERNING',
    steps: [
      { say: 'Laws need votes. Open the Roosevelt Room (the door marked for it), where Congress is worked: bills, the whip count, the members you can call.', spot: ['.ov-spot-roosevelt'],
        until: () => !!document.querySelector('.scrim')?.textContent?.includes('Roosevelt Room'),
        doIt: () => (document.querySelector('.ov-spot-roosevelt') as HTMLButtonElement | null)?.click(), lights: ['congress'] },
      { say: 'Every bill has a budget office score: what it does to growth, prices and the deficit, with it and without it. That is the economy’s cause and effect.', run: async c => {
        host.close();
        const bill = safe(() => engine.presidency().bills.find(b => b.stage !== 'Law')?.id ?? engine.presidency().bills[0]?.id) ?? null;
        const score = bill ? safe(() => engine.billScore(bill)) : null;
        if (!score) return;
        const row = (label: string) => score.rows.find(r => r.label.toLowerCase().includes(label));
        const fmt = (r: ReturnType<typeof row>) => r ? r.central.slice(0, 4).map(v => pts(v)).join(' · ') : 'n/a';
        await c.card('Before and after', `${score.title}: with the bill, against without it`,
          h('table', { class: 'mini-table tut-compare' },
            h('thead', null, h('tr', null, h('th', { scope: 'col' }, 'Difference'), h('th', { scope: 'col' }, `Years ${score.years.slice(0, 4).join(', ')}`))),
            h('tbody', null, ...['growth', 'inflation', 'unemployment'].map(k => h('tr', null, h('th', { scope: 'row' }, k[0]!.toUpperCase() + k.slice(1)), h('td', null, fmt(row(k))))))),
          `${score.reading} The deficit: ${pts(score.budget.deficit)}% of GDP a year.`);
      }, lights: ['economy'] },
      { say: 'Down the hall, the West Wing: your staff and cabinet, executive orders and rules (which need no vote, but can be struck down in court), and the vice president. Next door, the Press Briefing Room: press conferences, speeches and your party. A good week in the press is approval; a bad story can run for weeks.', spot: ['.ov-spot-westwing', '.ov-spot-press'], lights: ['press'] },
      { say: 'Let the weeks run: watch approval and the economy move together.', run: async c => {
        const a = c.presidency();
        for (let i = 0; i < 8 && phase() === 'GOVERNING'; i++) { playTurn(); c.refresh(); await c.turn(); }
        const b = c.presidency();
        if (a && b) await c.card('Cause and effect', 'Two months later',
          h('ul', { class: 'tut-deltas' },
            h('li', null, h('strong', null, 'Approval: '), `${Math.round(a.approval ?? 0)}% → ${Math.round(b.approval ?? 0)}%`),
            b.unemployment != null ? h('li', null, h('strong', null, 'Unemployment: '), `${(a.unemployment ?? 0).toFixed(1)}% → ${b.unemployment.toFixed(1)}%`) : null,
            b.inflation != null ? h('li', null, h('strong', null, 'Inflation: '), `${(a.inflation ?? 0).toFixed(1)}% → ${b.inflation.toFixed(1)}%`) : null),
          'The 2030 midterms are a verdict on exactly these numbers: a weak president loses Congress, and then bills stop passing.');
      }, lights: ['midterms'] },
    ],
    recap: ['Congress passes laws; the Roosevelt Room is where you work it.', 'Every law moves the economy, and the economy moves approval.', 'Approval decides the midterms, and the midterms decide Congress.'],
  },
  {
    title: 'The world, and the years ahead', minutes: 2, moment: 'saves/governing.json', fits: () => phase() === 'GOVERNING',
    steps: [
      { say: 'The Situation Room: hot spots, alliances, trade and war. A crisis over Taiwan or in the Strait of Hormuz takes over the clock, day by day or hour by hour, and its costs reach the economy and approval.', spot: ['.ov-spot-globe'],
        until: () => !!document.querySelector('.scrim')?.textContent?.includes('Situation Room'),
        doIt: () => (document.querySelector('.ov-spot-globe') as HTMLButtonElement | null)?.click(), lights: ['world'] },
      { say: 'Ahead: the 2030 midterms; the 2032 campaign, run from the war room while you govern, with the White House’s questions coming to you there; and, if you win, a second term to January 2037 and the verdict of history.', run: async () => { host.close(); }, lights: ['reelection'] },
    ],
    recap: ['Crises abroad move the economy and approval at home.', 'The 2032 campaign is run while you govern: a referendum on your record.', 'The game ends with the verdict of history on four years or eight.'],
  },
];

let before: CampaignView | null = null;
let paSlots = 0;
let pres: PresidencyView | null = null;

// ---- the runner and the coach ------------------------------------------------------------------------------------------

/** Starts the tutorial at a chapter (by default where it last stopped). */
export async function startTutorial(hooks: TutorialHooks, from = memory.get()): Promise<void> {
  const lit = new Set<NodeId>();
  let speed: Speed = 'normal', paused = false, stop = false, chapter = Math.min(from, CHAPTERS.length - 1);
  let advance: (() => void) | null = null;
  const panel = h('aside', { class: 'tut-coach', role: 'region', 'aria-label': 'The tutorial' });
  document.body.append(panel);
  const spotTimer = window.setInterval(ring, 250);
  let spots: string[] = [];

  function ring(): void {
    document.querySelectorAll('.tut-spot').forEach(e => { if (!spots.some(sel => e.matches(sel))) e.classList.remove('tut-spot'); });
    for (const sel of spots) document.querySelectorAll(sel).forEach(e => e.classList.add('tut-spot'));
    // never sit on what the player is asked to click: the coach moves to the other side
    const target = spots.length ? document.querySelector(spots[0]!) : null;
    if (target && window.innerWidth > 720) {
      const t = target.getBoundingClientRect(), left = panel.classList.contains('right') ? window.innerWidth - 16 - panel.offsetWidth : 16;
      const overlaps = t.left < left + panel.offsetWidth && t.right > left && t.bottom > window.innerHeight - 16 - panel.offsetHeight;
      if (overlaps) panel.classList.toggle('right');
    }
  }

  const ctx: Ctx = {
    turn: async () => {
      await new Promise(r => setTimeout(r, DELAY[speed]));
      while (paused && !stop) await new Promise(r => setTimeout(r, 150));
    },
    campaign: () => safe(() => engine.campaign()),
    presidency: () => safe(() => engine.presidency()),
    command: c => { try { engine.command(c); return true; } catch (e) { toast(e instanceof Refused ? e.message : String(e)); return false; } },
    refresh: () => hooks.refresh(),
    card: (eyebrow, title, ...body) => new Promise<void>(resolve => {
      draw(h('div', { class: 'tut-card' }, h('div', { class: 'eyebrow' }, eyebrow), h('h2', { class: 'tut-title' }, title),
        ...body.map(b => typeof b === 'string' ? h('p', null, b) : b)), [h('button', { class: 'btn primary', type: 'button', onclick: () => resolve() }, 'Next')]);
    }),
  };

  const toolbar = () => h('div', { class: 'tut-tools', role: 'toolbar', 'aria-label': 'Tutorial controls' },
    h('button', { class: 'btn small', type: 'button', 'aria-pressed': String(paused), onclick: () => { paused = !paused; redrawTools(); } }, paused ? 'Resume' : 'Pause'),
    h('div', { class: 'seg tut-speed', role: 'group', 'aria-label': 'Speed' }, ...(['slow', 'normal', 'fast'] as Speed[]).map(sp =>
      h('button', { type: 'button', class: 'seg-btn' + (sp === speed ? ' on' : ''), 'aria-pressed': String(sp === speed), onclick: () => { speed = sp; redrawTools(); } },
        sp[0]!.toUpperCase() + sp.slice(1)))),
    h('button', { class: 'btn small', type: 'button', 'aria-haspopup': 'menu', onclick: (e: Event) => openMenu(e.currentTarget as HTMLElement, 'Chapters',
      CHAPTERS.map((c, i) => ({ label: `${i + 1}. ${c.title}`, detail: `About ${c.minutes} minute${c.minutes === 1 ? '' : 's'}${i === chapter ? ' · now' : ''}`, pick: () => jump(i) }))) }, 'Chapters'),
    h('button', { class: 'btn small', type: 'button', onclick: () => host.open(mapCard(lit, [], () => host.close()), 'How the game fits together') }, 'Map'),
    h('button', { class: 'btn small', type: 'button', onclick: () => host.open(glossary(() => host.close()), 'Glossary') }, 'Glossary'),
    h('button', { class: 'btn small primary', type: 'button', title: 'Leave the tutorial and keep playing this game', onclick: () => leave(true) }, 'Play from here'),
    h('button', { class: 'btn small quiet', type: 'button', 'aria-label': 'Close the tutorial', title: 'Back to the title screen', onclick: () => leave(false) }, '×'));
  let tools = toolbar();
  function redrawTools(): void { const t = toolbar(); tools.replaceWith(t); tools = t; }

  let body: HTMLElement = h('div');
  function draw(content: HTMLElement, buttons: HTMLElement[]): void {
    const c = CHAPTERS[chapter];
    const next = h('div', { class: 'tut-body' },
      h('div', { class: 'tut-head' }, h('span', { class: 'eyebrow' }, c ? `Chapter ${chapter + 1} of ${CHAPTERS.length} · ${c.title}` : 'The end of the tutorial')),
      content, h('div', { class: 'tut-buttons' }, ...buttons));
    body.replaceWith(next);
    body = next;
    (next.querySelector('.tut-buttons .btn.primary') as HTMLElement | null)?.focus({ preventScroll: true });
  }
  panel.append(tools, body);

  async function moment(m: string): Promise<void> {
    try { engine.load(await buildFile(m)); } catch (e) { toast(String(e)); }
    hooks.refresh();
  }

  function leave(keep: boolean): void {
    stop = true; paused = false; spots = []; ring();
    window.clearInterval(spotTimer);
    panel.remove();
    if (advance) advance();
    if (!keep) memory.clear();
    hooks.done(keep);
  }

  let jumpTo = -1;
  function jump(i: number): void { jumpTo = i; if (advance) advance(); }

  /** Shows a step and waits until it is done (or Next, or a jump). */
  async function step(st: Step): Promise<void> {
    for (const n of st.lights ?? []) lit.add(n);
    spots = st.spot ?? [];
    ring();
    if (st.run) {
      if (st.say) draw(h('div', null, h('p', null, st.say), st.lights?.length ? systemsMap(lit, st.lights) : null), [h('span', { class: 'faint small', role: 'status' }, st.until ? '' : 'Playing…')]);
      await st.run(ctx);
      if (stop || jumpTo >= 0) return;
      if (!st.until && !st.pauseAfter) return;
    }
    if (!st.say) return;
    await new Promise<void>(resolve => {
      advance = resolve;
      let helped = false;
      const doIt = st.doIt ? h('button', { class: 'btn small', type: 'button', onclick: async () => { helped = true; await st.doIt!(ctx); } }, 'Do it for me') : null;
      const next = h('button', { class: 'btn primary', type: 'button', disabled: !!st.until, onclick: () => resolve() }, st.until ? 'Waiting for your move…' : 'Next');
      draw(h('div', null, h('p', null, st.say), st.lights?.length && !st.run ? systemsMap(lit, st.lights) : null), [doIt, next].filter((x): x is HTMLButtonElement => !!x));
      if (st.until) {
        const t = window.setInterval(() => {
          if (stop || jumpTo >= 0) { window.clearInterval(t); resolve(); return; }
          if (st.until!()) { window.clearInterval(t); next.disabled = false; next.textContent = 'Done: next'; next.focus(); if (!helped) toast('Well done.'); }
        }, 250);
      }
    });
    advance = null;
  }

  while (!stop && chapter < CHAPTERS.length) {
    const c = CHAPTERS[chapter]!;
    memory.set(chapter);
    if (!c.fits()) await moment(c.moment);
    for (const st of c.steps) {
      if (stop || jumpTo >= 0) break;
      await step(st);
    }
    if (stop) return;
    if (jumpTo >= 0) { chapter = jumpTo; jumpTo = -1; await moment(CHAPTERS[chapter]!.moment); continue; }
    spots = []; ring();
    await ctx.card(`Chapter ${chapter + 1} recap`, c.title, h('ul', { class: 'tut-recap' }, ...c.recap.map(r => h('li', null, r))));
    if (jumpTo >= 0) { chapter = jumpTo; jumpTo = -1; await moment(CHAPTERS[chapter]!.moment); continue; }
    chapter++;
  }
  if (stop) return;
  memory.clear();
  draw(h('div', null, h('h2', { class: 'tut-title' }, 'That is the game'), h('p', null, 'You have seen the campaign, election night, the transition, the first hundred days and a year in office. Keep playing this presidency, or start a game of your own from the title screen.'),
    systemsMap(lit)), [h('button', { class: 'btn', type: 'button', onclick: () => leave(false) }, 'Back to the title'), h('button', { class: 'btn primary', type: 'button', onclick: () => leave(true) }, 'Play from here')]);
}

const GLOSSARY: [string, string][] = [
  ['Approval', 'The share of Americans who approve of the president. It moves with the economy, Congress, the press and the world, and it decides the midterms and the re-election.'],
  ['Electoral votes', 'Each state’s votes for president, won by its popular vote (Maine and Nebraska split by district). 270 wins.'],
  ['Flagship', 'The first priority on the agenda: the bill the first hundred days are judged by.'],
  ['Internal poll', 'A poll your campaign pays for: closer to the truth than the public polls.'],
  ['Message', 'The campaign’s one national issue each week, and its tone.'],
  ['Midterms', 'The congressional elections of 2030 and 2034: a verdict on the president that decides Congress.'],
  ['Momentum', 'The national drift of the race from the campaigns’ messages and news.'],
  ['Polling miss', 'The error all the public polls share: hidden until election night.'],
  ['Running mate', 'The vice-presidential candidate: your strongest surrogate on the trail.'],
  ['Score', 'The budget office’s estimate of what a bill does to growth, prices and the deficit, with it against without it.'],
  ['Surrogate', 'Someone who campaigns for you: the running mate, your spouse, or an ally.'],
  ['Tipping point', 'The state that would give the winner the 270th electoral vote.'],
  ['Whip count', 'The tally of how Congress would vote on a bill today.'],
];

function glossary(close: () => void): HTMLElement {
  return h('div', { class: 'dialog-card' },
    h('div', { class: 'eyebrow' }, 'The tutorial'),
    h('h2', { class: 'dialog-title' }, 'Glossary'),
    h('dl', { class: 'tut-glossary' }, ...GLOSSARY.flatMap(([t, d]) => [h('dt', null, t), h('dd', null, d)])),
    h('div', { class: 'dialog-foot' }, h('button', { class: 'btn primary', type: 'button', onclick: close }, 'Back')));
}
