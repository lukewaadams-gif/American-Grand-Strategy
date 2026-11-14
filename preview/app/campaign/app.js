// The campaign's war room, live over the real engine, as a screen the game mounts: headquarters, the week's run, the
// digest, the decisions and, after Election Day, election night. main.ts mounts it alone (the campaign preview);
// play-main.ts mounts it inside the whole game.
import { engine, Refused } from '../core/engine.js';
import { h } from '../core/dom.js';
import { day, signed } from '../core/format.js';
import { cost } from './schedule.js';
import { compact } from './field.js';
import { resetWarRoomMemory, warRoom } from './warroom.js';
import { decisionCard } from './decision.js';
import { host, toast } from '../core/dialog.js';
import { closeMenu, menuOpen } from '../core/menu.js';
import { adStudio } from './studio.js';
import { rooseveltRoom } from '../presidency/roosevelt.js';
import { westWingRoom } from '../presidency/westwing.js';
import { sitRoom } from '../presidency/sitroom.js';
import { pressRoom } from '../presidency/pressroom.js';
import { ISSUES } from '../core/issues.js';
const root = document.getElementById('app');
let hostOpts = {};
let active = false;
/** Shows the war room and takes the keyboard. */
export function mountCampaign(opts = {}) {
    hostOpts = opts;
    active = true;
    resetWarRoomMemory();
    state.selected = null;
    state.digest = null;
    state.picking = null;
    render();
}
/** Gives up the screen and the keyboard. */
export function unmountCampaign() { active = false; }
const state = { layer: 'margin', selected: null, tab: 'schedule', picking: null, desk: 'overview', digest: null };
// keys 1-6 switch the left panel's tabs (as in Battleground 2028), when no dialog or menu is open and focus is not in a field
const TAB_KEYS = { '1': 'schedule', '2': 'money', '3': 'field', '4': 'message', '5': 'polls', '6': 'wire' };
function render() {
    let v;
    try {
        v = engine.campaign();
    }
    catch (e) {
        toast(String(e));
        return;
    }
    // after the vote the war room has nothing left to run: election night takes over (never a war room drawn from a
    // view with no campaign in it, which is what froze the last day of the campaign)
    if (v.phase !== 'GENERAL_CAMPAIGN_2028' && v.phase !== 'REELECTION_CAMPAIGN_2032') {
        electionOver(v);
        return;
    }
    const focusKey = document.activeElement?.getAttribute('aria-label') ?? null;
    const scroll = document.getElementById('main')?.scrollTop ?? 0;
    root.replaceChildren(warRoom(v, state, {
        runWeek, openDecisions: () => openDecisions(), toast, order, openQuestions, openRoom,
        pledge: id => { try {
            engine.command({ type: 'Pledge', bill: id });
        }
        catch (e) {
            toast(e instanceof Refused ? e.message : String(e));
            return false;
        } render(); return true; },
        voters: code => { try {
            return engine.voters(code);
        }
        catch {
            return null;
        } },
        setLayer: id => { state.layer = id; render(); },
        select: c => { state.selected = c; render(); },
        setTab: id => { state.tab = id; render(); document.getElementById('tab-' + id)?.focus(); },
        setDesk: d => { state.desk = d; render(); },
        studio: () => openStudio(),
        dismissDigest: () => { state.digest = null; render(); },
        pick: slot => {
            state.picking = slot;
            render();
            if (slot !== null)
                document.querySelector('.usmap path.state[tabindex="0"]')?.focus({ preventScroll: false });
        },
    }));
    const main = document.getElementById('main');
    if (main)
        main.scrollTop = scroll;
    if (focusKey)
        [...root.querySelectorAll('[aria-label]')].find(e => e.getAttribute('aria-label') === focusKey)?.focus({ preventScroll: true });
    document.title = `Campaign ${v.electionDay.slice(0, 4)} · ${day(v.date)} · American Grand Strategy`;
}
/** A campaign order (schedule, ads, field, staff): applied at once and redrawn, or refused with the reason shown. */
function order(o) {
    try {
        engine.command({ type: 'Campaign', order: o });
    }
    catch (e) {
        toast(e instanceof Refused ? e.message : String(e));
        return false;
    }
    render();
    return true;
}
/** The Ad Studio, a dialog over headquarters: redrawn in place after each change, keeping the focused control. */
function openStudio() {
    const active = document.activeElement;
    const key = active?.getAttribute('aria-label') ?? active?.textContent ?? null;
    const v = engine.campaign();
    host.open(adStudio(v, {
        order: o => {
            try {
                engine.command({ type: 'Campaign', order: o });
            }
            catch (e) {
                toast(e instanceof Refused ? e.message : String(e));
                return false;
            }
            openStudio();
            return true;
        },
        refresh: () => openStudio(),
        close: () => host.close(),
    }), 'The Ad Studio', render);
    if (key)
        [...document.querySelectorAll('.studio button')].find(b => (b.getAttribute('aria-label') ?? b.textContent) === key)?.focus();
}
/** The questions the week cannot run without (an event, the week's debate), one after another. */
function openDecisions(after = render) {
    const ds = engine.decisions().filter(d => d.blocking !== false);
    if (!ds.length) {
        after();
        return;
    }
    decisionCard(ds[0], 1, ds.length, host, choose, () => { render(); if (engine.decisions().some(d => d.blocking !== false))
        openDecisions(after); });
}
function choose(command) {
    try {
        return engine.command(command);
    }
    catch (e) {
        toast(e instanceof Refused ? e.message : String(e));
        return null;
    }
}
/** Running for re-election: the White House's questions, each with a deadline. "Decide later" moves on to the next, and
 *  what is left the staff answer when it is due. */
function openQuestions(skip = 0) {
    const ds = engine.decisions().filter(d => d.blocking === false);
    if (skip >= ds.length) {
        render();
        return;
    }
    let answered = false;
    decisionCard(ds[skip], skip + 1, ds.length, host, command => {
        const r = choose(command);
        if (r !== null)
            answered = true;
        return r;
    }, () => { render(); openQuestions(answered ? skip : skip + 1); });
}
/** The White House's rooms, opened over the war room while running for re-election. */
function openRoom(room) {
    const send = (type) => (order) => {
        try {
            engine.command({ type, order });
        }
        catch (e) {
            toast(e instanceof Refused ? e.message : String(e));
            return false;
        }
        return true;
    };
    const decide = () => { host.close(); openQuestions(); };
    const back = () => host.close(), backLabel = 'Back to the war room';
    if (room === 'congress')
        host.open(rooseveltRoom({ view: () => engine.hill(), order: send('Hill'), decide, back, backLabel }), 'The Roosevelt Room', render);
    else if (room === 'westwing')
        host.open(westWingRoom({ view: () => engine.westWing(), options: (kind, index) => engine.westWingOptions(kind, index),
            order: send('WestWing'), decide, cabinetRoom: () => toast('The Cabinet Room is in the Oval Office’s West Wing, after the campaign.'), back, backLabel, issues: ISSUES }), 'The West Wing', render);
    else if (room === 'sitroom')
        host.open(sitRoom({ view: () => engine.sitRoom(), country: code => engine.foreignCountry(code), order: send('Foreign'), decide, back, backLabel }), 'The Situation Room', render);
    else
        host.open(pressRoom({ view: () => engine.press(), order: send('WestWing'), decide, back, backLabel, terms: () => engine.terms(),
            termsOrder: o => send('Terms')(o) }, 'The press'), 'The Press Briefing Room', render);
}
function runWeek() {
    const v = engine.campaign();
    if (v.phase !== 'GENERAL_CAMPAIGN_2028' && v.phase !== 'REELECTION_CAMPAIGN_2032')
        return;
    if (v.pendingDecisions > 0) {
        openDecisions(runWeek);
        return;
    }
    let news;
    try {
        news = engine.command({ type: 'EndTurn' });
    }
    catch (e) {
        toast(e instanceof Refused ? e.message : String(e));
        return;
    }
    state.picking = null;
    const after = engine.campaign();
    // a crisis began in the week: it is fought from the Oval Office, and the war room waits for it
    if (after.crisis && hostOpts.onLeave) {
        const go = hostOpts.onLeave;
        go();
        return;
    }
    // Election Day: no digest of a day that ended the campaign; election night comes next
    state.digest = after.phase === v.phase ? digestOf(v, after, news) : null;
    render();
}
/** BG's makeDigest: raised and spent, the poll's move, volunteers and doors, the trail, and the turn's headline. */
function digestOf(before, after, news) {
    const daily = before.turn?.daily ?? false;
    const title = daily ? `${['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'][before.turn.day]} in review (${day(before.date)})`
        : `Week ${before.week} in review`;
    const nat = (x) => x.polls[x.polls.length - 1]?.national ?? 0;
    const lines = [];
    const m = after.money;
    if (m)
        lines.push(`Raised ${cost(m.raised)}, spent ${cost(m.spent)}  \u00b7  national polls ${signed(nat(after))} (${signed(nat(after) - nat(before))})`);
    const vol = (x) => x.field?.totals.volunteers ?? 0;
    if (after.field)
        lines.push(`Volunteers ${compact(vol(after))} (${vol(after) >= vol(before) ? '+' : '\u2212'}${compact(Math.abs(vol(after) - vol(before)))})  \u00b7  doors knocked ${compact(after.field.totals.doors)}`);
    const rallies = (after.trail?.rallies ?? 0) - (before.trail?.rallies ?? 0);
    const show = news.find(n => n.text.startsWith('PODCAST:') && !n.text.includes('passes on'));
    const trail = [rallies > 0 ? `${rallies} ${rallies === 1 ? 'rally' : 'rallies'}` : '', show ? show.text.replace(/^PODCAST: /, '').split('. ')[0] ?? '' : ''].filter(Boolean);
    if (trail.length)
        lines.push(trail.join('  \u00b7  '));
    const head = news.find(n => n.headline && !n.text.startsWith('PODCAST:'));
    if (head)
        lines.push(`${head.headline}: ${head.text.replace(/^Your opponent's campaign: /, '')}`);
    return { title, lines };
}
/** After Election Day: in the whole game, election night (the game's own screen) and the game goes on from there. */
function electionOver(v) {
    if (hostOpts.onElectionOver) {
        const go = hostOpts.onElectionOver;
        go();
        return;
    }
    const r = v.result;
    if (!r)
        return;
    // the campaign preview on its own (campaign.html): the night's result, and the campaign again
    const won = r.winner === v.player.party;
    host.open(h('div', { class: 'dialog-card' }, h('div', { class: 'eyebrow' }, 'Election night · ' + day(v.electionDay, true)), h('h2', { class: 'dialog-title big' }, won ? 'You win the presidency' : 'You lose'), h('p', null, `Democrats ${r.dem}, Republicans ${r.rep}. Popular vote: ${r.popular >= 0 ? 'Democrats' : 'Republicans'} by ${Math.abs(r.popular).toFixed(1)}.`), h('p', { class: 'muted' }, 'This page is the campaign war room on its own. The whole game, from the campaign to the verdict, starts at the game’s title screen.'), h('div', { class: 'dialog-foot' }, h('button', { class: 'btn primary', type: 'button', onclick: () => { host.close(); hostOpts.restart?.(); } }, 'Play the campaign again'))), 'Election night');
}
document.addEventListener('keydown', e => {
    if (!active)
        return;
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
        e.preventDefault();
        closeMenu(false);
        runWeek();
    }
    else if (TAB_KEYS[e.key] && !e.ctrlKey && !e.metaKey && !e.altKey && !menuOpen() && !document.querySelector('.scrim')
        && !(document.activeElement instanceof HTMLInputElement)) {
        state.tab = TAB_KEYS[e.key];
        render();
    }
    else if (e.key === 'Escape') {
        if (menuOpen())
            return;
        if (document.querySelector('.scrim'))
            host.close();
        else if (state.picking !== null) {
            state.picking = null;
            render();
        }
        else if (state.selected) {
            state.selected = null;
            render();
        }
    }
});
