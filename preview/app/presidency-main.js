// The preview of the presidency: the Oval Office, live over the real engine. It opens a save in the first hundred
// days (or ?save=, for example saves/governing.json) and plays week by week: the calendar on the desk, The Capital
// Ledger after every week, watch mode, and the decisions that come up. The rooms behind the doors arrive in the
// presidency's later milestones; until then each says which milestone brings it.
import { buildFile, engine, Refused, start } from './core/engine.js';
import { h, reducedMotion } from './core/dom.js';
import { day } from './core/format.js';
import { host, toast } from './core/dialog.js';
import { closeMenu, menuOpen, openMenu } from './core/menu.js';
import { decisionCard } from './campaign/decision.js';
import { usePalette } from './campaign/palette.js';
import { currentHour, lightFor } from './presidency/lighting.js';
import { congressLine, dateLong, ovalScreen } from './presidency/oval-screen.js';
import { calendar, cancelCalendarPick } from './presidency/calendar.js';
import { capitalLedger } from './presidency/paper.js';
import { SPEEDS, watchDialog } from './presidency/watch.js';
import { briefingBook, countryRoom } from './presidency/rooms.js';
import { cabinetRoom } from './presidency/cabinet.js';
import { scoreView } from './presidency/score.js';
import { rooseveltRoom } from './presidency/roosevelt.js';
const root = document.getElementById('app');
const params = new URLSearchParams(location.search);
/** A fixed hour for the light (from ?hour= or the menu); null follows the player's clock. */
let hourOverride = params.has('hour') ? currentHour(params) : null;
const monthOverride = params.has('month') ? Number(params.get('month')) : null;
let room = null;
let saveText = '';
/** Watch mode: the speed (an index into SPEEDS) and the timer for the next week; null when the player has the controls. */
let watching = null;
let speedChoice = 1;
function lightNow(v) {
    const month = monthOverride && monthOverride >= 1 && monthOverride <= 12 ? monthOverride : Number(v.date.slice(5, 7));
    return lightFor(hourOverride ?? currentHour(new URLSearchParams()), month);
}
function render() {
    let v;
    try {
        v = engine.presidency();
    }
    catch (e) {
        toast(String(e));
        return;
    }
    const focusKey = document.activeElement?.getAttribute('aria-label') ?? document.activeElement?.textContent ?? null;
    const screen = ovalScreen(v, lightNow(v), {
        endTurn, save, menu, spot: id => openSpot(id, v),
        watch: () => { if (watching) {
            watching.speed = (watching.speed + 1) % SPEEDS.length;
            render();
        }
        else
            openWatch(); },
        stopWatching: () => stopWatching(),
        watching: watching ? SPEEDS[watching.speed].label : null,
    });
    room = screen.room;
    root.replaceChildren(screen.el);
    room.tick(reducedMotion() ? 0 : performance.now() / 1000);
    if (focusKey)
        [...root.querySelectorAll('button')].find(e => (e.getAttribute('aria-label') ?? e.textContent) === focusKey)?.focus({ preventScroll: true });
    document.title = `The Oval Office · ${day(v.date, true)} · American Grand Strategy`;
}
// ---- the room's moving parts: about 30 frames a second, none under reduced motion ------------------------------------
let last = 0;
function frame(now) {
    if (room && !reducedMotion() && now - last > 33) {
        room.tick(now / 1000);
        last = now;
    }
    requestAnimationFrame(frame);
}
// ---- commands ----------------------------------------------------------------------------------------------------------
function command(c) {
    try {
        engine.command(c);
        return true;
    }
    catch (e) {
        toast(e instanceof Refused ? e.message : String(e));
        return false;
    }
}
const presidency = (order) => command({ type: 'Presidency', order });
// ---- the week ----------------------------------------------------------------------------------------------------------
function openDecisions(after = render) {
    const ds = engine.decisions();
    if (!ds.length) {
        after();
        return;
    }
    decisionCard(ds[0], 1, ds.length, host, c => {
        try {
            return engine.command(c);
        }
        catch (e) {
            toast(e instanceof Refused ? e.message : String(e));
            return null;
        }
    }, () => { render(); if (engine.decisions().length)
        openDecisions(after);
    else
        after(); });
}
function endTurn() {
    const v = engine.presidency();
    if (!v.turnUnit || watching)
        return;
    if (v.pendingDecisions > 0) {
        openDecisions(endTurn);
        return;
    }
    if (!command({ type: 'EndTurn' }))
        return;
    render();
    const after = engine.presidency();
    if (!after.turnUnit) {
        beyondPreview(after);
        return;
    }
    // Resolute Desk: the paper first, then what is waiting on the desk
    showPaper(after.pendingDecisions > 0 ? () => openDecisions() : undefined);
}
/** The Capital Ledger, full page; Enter or Escape goes back to the Oval Office. */
function showPaper(after) {
    const v = engine.presidency();
    const page = capitalLedger(engine.paper(), v, () => host.close());
    page.addEventListener('keydown', e => { if (e.key === 'Enter' && e.target.tagName !== 'BUTTON') {
        e.preventDefault();
        host.close();
    } });
    host.open(page, 'The Capital Ledger', after);
}
/** The calendar on the desk, redrawn after each change with the same control focused. */
function openCalendar() {
    const active = document.activeElement;
    const key = active?.closest('.cal') ? active.getAttribute('aria-label') ?? active.textContent : null;
    host.open(calendar(engine.presidency(), {
        order: o => { const ok = presidency(o); openCalendar(); return ok; },
        redraw: () => openCalendar(),
        close: () => host.close(),
    }), 'The calendar', () => { cancelCalendarPick(); render(); });
    const again = key ? [...document.querySelectorAll('.cal button, .cal [role=button]')].find(b => (b.getAttribute('aria-label') ?? b.textContent) === key) : null;
    (again ?? document.querySelector('.cal-map.picking .cal-state[tabindex="0"]'))?.focus();
}
// ---- watch mode ----------------------------------------------------------------------------------------------------------
function openWatch() {
    const v = engine.presidency();
    if (!v.watch)
        return;
    const active = document.activeElement;
    const key = active?.closest('.watch') ? active.id || active.textContent : null;
    host.open(watchDialog(v, speedChoice, {
        order: o => { const ok = presidency(o); openWatch(); return ok; },
        start: sp => { speedChoice = sp; host.close(); startWatching(sp); },
        close: () => host.close(),
    }), 'Watch mode');
    if (key)
        [...document.querySelectorAll('.watch button, .watch input')].find(b => (b.id || b.textContent) === key)?.focus();
}
function startWatching(speed) {
    watching = { speed, timer: 0 };
    render();
    toast('Watching: the staff governs. Press Escape to take the controls back.');
    watching.timer = window.setTimeout(watchWeek, SPEEDS[speed].ms);
}
function stopWatching(reason = 'You have the controls.') {
    if (!watching)
        return;
    clearTimeout(watching.timer);
    watching = null;
    render();
    toast(reason);
}
/** One watched week: the staff answers what it may, plans the week by the plan, and plays it (Resolute Desk's Auto.week). */
function watchWeek() {
    if (!watching)
        return;
    const v = engine.presidency();
    if (!v.turnUnit) {
        stopWatching();
        beyondPreview(v);
        return;
    }
    for (let guard = 0; guard < 10; guard++) {
        const d = engine.decisions()[0];
        if (!d)
            break;
        const stop = d.stop ?? -1;
        if (stop >= 0 && v.watch?.stops[stop]?.on) {
            stopWatching('A decision needs you: ' + d.title);
            openDecisions();
            return;
        }
        const pick = d.options[d.recommended ?? 0] ?? d.options[0];
        if (!command(pick.command)) {
            stopWatching();
            return;
        }
        toast(`The staff decided: ${d.title}. ${pick.label}.`);
    }
    if (!presidency({ type: 'WatchWeek' }) || !command({ type: 'EndTurn' })) {
        stopWatching();
        return;
    }
    const after = engine.presidency();
    const w = after.week;
    if (w && w.stamina < 20) {
        stopWatching(`The president is exhausted (stamina ${Math.round(w.stamina)}).`);
        return;
    }
    render();
    if (!after.turnUnit) {
        stopWatching();
        beyondPreview(after);
        return;
    }
    if (watching)
        watching.timer = window.setTimeout(watchWeek, SPEEDS[watching.speed].ms);
}
// ---- the game around the room --------------------------------------------------------------------------------------------
/** The preview covers the presidency in office; the day-100 report, the campaign of 2032 and the rest come later. */
function beyondPreview(v) {
    host.open(h('div', { class: 'dialog-card' }, h('div', { class: 'eyebrow' }, dateLong(v.date)), h('h2', { class: 'dialog-title' }, 'Beyond this preview'), h('p', null, 'The game has moved past the part of the presidency this preview shows. The day-100 report, the transition office and the rest of the presidency arrive in its later milestones.'), h('div', { class: 'dialog-foot' }, h('button', { class: 'btn primary', type: 'button', onclick: () => { host.close(); restart(); } }, 'Start again from the save'))), 'Beyond this preview');
}
function save() {
    try {
        const blob = new Blob([engine.save()], { type: 'application/json' });
        const a = h('a', { href: URL.createObjectURL(blob), download: 'american-grand-strategy-presidency.json' });
        document.body.append(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(a.href), 1000);
        toast('Saved: the file is in your downloads.');
    }
    catch (e) {
        toast(String(e));
    }
}
function restart() { stopWatching(); engine.load(saveText); render(); }
function menu(anchor) {
    const v = engine.presidency();
    const month = Number(v.date.slice(5, 7));
    const at = (label, hour, detail) => ({
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
function duskHour(month) {
    for (let hr = 21; hr > 15; hr -= 0.25)
        if (lightFor(hr, month).phase === 'dusk')
            return hr - 0.25;
    return 18;
}
// ---- what the room's objects and doors do in this milestone --------------------------------------------------------
const LATER = {
    westwing: { title: 'The West Wing', body: 'The staff and the cabinet: skills, morale, confirmations, executive orders and rules, the courts, and the vice president’s missions.', milestone: 6, stage: 'the executive, the press and storylines' },
    press: { title: 'The Press Briefing Room', body: 'Press conferences, interviews, addresses and the State of the Union; the outlets’ tone, the party’s wings and the opposition’s strategy.', milestone: 6, stage: 'the executive, the press and storylines' },
    residence: { title: 'The Residence', body: 'Family, rest and health: where stamina comes back and the president’s private life goes on.', milestone: 7, stage: 'the Residence and the voter groups' },
    globe: { title: 'The Situation Room', body: 'Forty countries, calls and summits, crises week by week, operations and covert action, and the standing deployments.', milestone: 8, stage: 'the world' },
};
/** The West Wing: the Cabinet Room opens now; the staff and the cabinet secretaries with milestone 6. */
function westWing() {
    const l = LATER['westwing'];
    host.open(h('div', { class: 'dialog-card' }, h('div', { class: 'eyebrow' }, 'The West Wing'), h('h2', { class: 'dialog-title' }, 'Down the hall'), h('div', { class: 'ww-doors' }, h('button', { class: 'btn primary', type: 'button', onclick: () => openCabinet() }, 'The Cabinet Room: the policy table'), h('p', { class: 'muted small' }, 'Every policy, where it stands, and what the budget office says a step would do. Laws move it from the Hill; orders and rules come with the staff.'), h('button', { class: 'btn', type: 'button', disabled: true }, 'The staff and the cabinet secretaries'), h('p', { class: 'muted small' }, `${l.body} They open with milestone ${l.milestone}: ${l.stage}.`)), h('div', { class: 'dialog-foot' }, h('button', { class: 'btn quiet', type: 'button', onclick: () => host.close() }, 'Back to the Oval Office'))), 'The West Wing');
}
function openCabinet() {
    host.open(cabinetRoom(engine.cabinet(), {
        score: (policy, steps) => engine.score(policy, steps),
        billScore: bill => engine.billScore(bill),
        draft: source => { if (hill({ type: 'Draft', source })) {
            host.close();
            openRoosevelt('Bills');
        } },
        back: () => host.close(),
    }), 'The Cabinet Room');
}
const hill = (order) => command({ type: 'Hill', order });
/** The Roosevelt Room: Congress. The telephone opens it on the members, whose votes are what calls are for at home. */
function openRoosevelt(tab) {
    host.open(rooseveltRoom({
        view: () => engine.hill(),
        order: o => hill(o),
        decide: () => { host.close(); openDecisions(); },
        back: () => host.close(),
    }, tab), 'The Roosevelt Room', render);
}
/** A bill's score behind a disclosure, run when it is opened (the budget office takes a moment). */
function billScoreDisclosure(id, law) {
    if (law)
        return h('span', { class: 'muted small' }, 'Law: its effects build in the briefing book');
    const body = h('div', { class: 'ov-score' });
    const d = h('details', { class: 'ov-score-wrap' }, h('summary', null, 'The budget office’s score'), body);
    d.addEventListener('toggle', () => {
        if (d.open && !body.childElementCount) {
            try {
                body.append(scoreView(engine.billScore(id), 'h4'));
            }
            catch (e) {
                body.append(h('p', { class: 'muted small' }, 'No score: ' + String(e)));
            }
        }
    });
    return d;
}
function soon(id) {
    const l = LATER[id];
    host.open(h('div', { class: 'dialog-card' }, h('div', { class: 'eyebrow' }, 'Coming in presidency milestone ' + l.milestone), h('h2', { class: 'dialog-title' }, l.title), h('p', null, l.body), h('p', { class: 'muted' }, `It opens with milestone ${l.milestone}: ${l.stage}.`), h('div', { class: 'dialog-foot' }, h('button', { class: 'btn primary', type: 'button', onclick: () => host.close() }, 'Back to the Oval Office'))), l.title);
}
function card(eyebrow, title, ...body) {
    return h('div', { class: 'dialog-card' }, h('div', { class: 'eyebrow' }, eyebrow), h('h2', { class: 'dialog-title' }, title), ...body, h('div', { class: 'dialog-foot' }, h('button', { class: 'btn primary', type: 'button', onclick: () => host.close() }, 'Back to the Oval Office')));
}
/** The agenda's folders, and in the first hundred days the business the chief of staff can run until the rooms open. */
function openAgenda() {
    const v = engine.presidency();
    const hd = v.hundredDays;
    host.open(card('Your agenda', v.bills.length ? `${v.bills.length} ${v.bills.length === 1 ? 'bill' : 'bills'} on your agenda` : 'No agenda yet', v.bills.length ? h('ul', { class: 'ov-dialog-list ov-agenda' }, ...v.bills.map(b => h('li', null, h('div', { class: 'ov-agenda-head' }, h('span', null, b.name), h('span', { class: 'pill' }, b.stage)), billScoreDisclosure(b.id, b.stage === 'Law')))) : null, h('p', { class: 'muted' }, congressLine(v.congress) + '. ' + (v.hundredDays ? 'The first hundred days carry the flagship themselves; the Roosevelt Room opens on day 100.' : 'Bills are drafted, whipped and sent from the Roosevelt Room.') + ' Every policy and its score is in the Cabinet Room, in the West Wing.'), hd ? h('section', { class: 'ov-delegate', 'aria-labelledby': 'ov-delegate-title' }, h('h3', { class: 'ov-delegate-title', id: 'ov-delegate-title' }, `The first hundred days, week ${Math.min(hd.week, hd.weeks)} of ${hd.weeks}: hand it to the staff`), h('p', { class: 'muted' }, 'Until Congress and the West Wing open in their rooms, the chief of staff can run the hundred days’ business. The staff is cautious: it trades for votes at half strength and nominates experts.'), ...hd.domains.map(d => h('button', { class: 'btn small toggle' + (d.on ? ' on' : ''), type: 'button', 'aria-pressed': String(d.on), title: d.detail,
        onclick: () => { if (command({ type: 'Act', action: { type: 'Delegate', domain: d.id, on: !d.on } }))
            openAgenda(); } }, d.label + ': ', h('strong', null, d.on ? 'the staff' : 'you')))) : null), 'Your agenda');
}
function openSpot(id, v) {
    switch (id) {
        case 'desk':
            if (v.week)
                openCalendar();
            else
                toast('The calendar opens at the inauguration.');
            return;
        case 'paper':
            showPaper();
            return;
        case 'folders':
            openAgenda();
            return;
        case 'documents': {
            if (v.pendingDecisions > 0) {
                openDecisions();
                return;
            }
            const laws = v.bills.filter(b => b.stage === 'Law');
            host.open(card('The Resolute Desk', 'Nothing waiting for your signature', h('p', null, 'Decisions that need the president, and the bills Congress sends to the desk, wait here to be signed or vetoed.'), laws.length ? h('p', { class: 'muted' }, 'Signed into law from your agenda: ' + laws.map(b => b.name).join(', ') + '.') : null), 'Documents to sign');
            return;
        }
        case 'book':
            if (!v.week) {
                toast('The briefing book opens at the inauguration.');
                return;
            }
            host.open(briefingBook(engine.economy(), () => host.close()), 'The briefing book: the economy');
            return;
        case 'windows':
            if (!v.week) {
                toast('The country\u2019s verdict starts at the inauguration.');
                return;
            }
            host.open(countryRoom(engine.country(), () => host.close()), 'The country');
            return;
        case 'westwing':
            westWing();
            return;
        case 'roosevelt':
            openRoosevelt();
            return;
        case 'phone':
            if (!v.week) {
                toast('The telephone is yours at the inauguration.');
                return;
            }
            openRoosevelt('Members');
            return;
        default:
            soon(id);
    }
}
// ---- keys --------------------------------------------------------------------------------------------------------------
document.addEventListener('keydown', e => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
        e.preventDefault();
        closeMenu(false);
        if (!document.querySelector('.scrim'))
            endTurn();
    }
    else if (e.key === 'Escape' && !menuOpen()) {
        if (document.querySelector('.cal-map.picking') && cancelCalendarPick()) {
            openCalendar();
            return;
        }
        if (document.querySelector('.scrim'))
            host.close();
        else if (watching)
            stopWatching();
    }
});
// the light follows the clock: look again every five minutes
setInterval(() => { if (hourOverride === null && !watching && !document.querySelector('.scrim') && !menuOpen())
    render(); }, 5 * 60 * 1000);
async function boot() {
    usePalette(params.get('palette') ?? 'standard');
    if (params.get('palette'))
        document.documentElement.dataset['palette'] = params.get('palette');
    if (params.get('motion') === 'reduce')
        document.documentElement.dataset['motion'] = 'reduce';
    if (params.get('size'))
        document.documentElement.style.setProperty('--s', params.get('size'));
    await start();
    saveText = await buildFile(params.get('save') ?? 'saves/presidency.json');
    engine.load(saveText);
    render();
    requestAnimationFrame(frame);
}
boot().catch(e => {
    root.replaceChildren(h('p', { class: 'boot-error', role: 'alert' }, 'The game could not start: ' + String(e)
        + '. Reloading the page past the cache (Ctrl+Shift+R, or Cmd+Shift+R on a Mac) fetches the latest version.'));
    console.error(e);
});
