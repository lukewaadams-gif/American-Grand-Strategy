// The preview of the presidency: the Oval Office, live over the real engine. It opens a save in the first hundred
// days (or ?save=, for example saves/governing.json) and plays from there with the engine's current turns. The rooms
// behind the doors, the calendar and the weekly front page arrive in the presidency's later milestones; until then the
// room says which milestone brings each one.
import { engine, Refused, start } from './core/engine.js';
import { h, reducedMotion } from './core/dom.js';
import { day } from './core/format.js';
import { host, toast } from './core/dialog.js';
import { closeMenu, menuOpen, openMenu } from './core/menu.js';
import { decisionCard } from './campaign/decision.js';
import { usePalette } from './campaign/palette.js';
import { currentHour, lightFor } from './presidency/lighting.js';
import { congressLine, dateLong, headline, ovalScreen } from './presidency/oval-screen.js';
const root = document.getElementById('app');
const params = new URLSearchParams(location.search);
/** A fixed hour for the light (from ?hour= or the menu); null follows the player's clock. */
let hourOverride = params.has('hour') ? currentHour(params) : null;
const monthOverride = params.has('month') ? Number(params.get('month')) : null;
let room = null;
let saveText = '';
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
    const screen = ovalScreen(v, lightNow(v), { endTurn, save, menu, watch: () => soon('watch'), spot: id => openSpot(id, v) });
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
// ---- turns -------------------------------------------------------------------------------------------------------------
function openDecisions(after = render) {
    const ds = engine.decisions();
    if (!ds.length) {
        after();
        return;
    }
    decisionCard(ds[0], 1, ds.length, host, command => {
        try {
            return engine.command(command);
        }
        catch (e) {
            toast(e instanceof Refused ? e.message : String(e));
            return null;
        }
    }, () => { render(); if (engine.decisions().length)
        openDecisions(after); });
}
function endTurn() {
    const v = engine.presidency();
    if (!v.turnUnit)
        return;
    if (v.pendingDecisions > 0) {
        openDecisions(endTurn);
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
    render();
    const after = engine.presidency();
    const head = news.find(n => n.headline) ?? news[0];
    if (head)
        toast(headline(head));
    if (after.pendingDecisions > 0)
        openDecisions();
    else if (!after.turnUnit)
        beyondPreview(after);
}
/** The preview covers the Oval Office in office; the day-100 report, the campaign of 2032 and the rest come later. */
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
function restart() { engine.load(saveText); render(); }
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
    for (let h = 21; h > 15; h -= 0.25)
        if (lightFor(h, month).phase === 'dusk')
            return h - 0.25;
    return 18;
}
// ---- what the room's objects and doors do in this milestone --------------------------------------------------------
const LATER = {
    desk: { title: 'The calendar', body: 'Plan the week: twenty-one slots for meetings, speeches, travel, rest and family, with stamina, stress and sleep, or hand the week to the chief of staff.', milestone: 3, stage: 'weekly turns and the schedule' },
    watch: { title: 'Watch mode', body: 'Choose how the staff governs, then watch the weeks play out until you take the controls back.', milestone: 3, stage: 'weekly turns and the schedule' },
    phone: { title: 'The telephone', body: 'Call members of Congress for their votes, with favours and promises, and foreign leaders about trade, alliances and crises.', milestone: 4, stage: 'Congress and the budget (calls abroad with milestone 7)' },
    roosevelt: { title: 'The Roosevelt Room', body: 'Congress: the twelve blocs and their leaders, the whip count, drafting and floor votes, hours on the Hill, the budget, shutdowns and the debt limit.', milestone: 4, stage: 'Congress and the budget' },
    westwing: { title: 'The West Wing', body: 'The staff and the cabinet: skills, morale, confirmations, executive orders and rules, the courts, and the vice president’s missions.', milestone: 5, stage: 'the executive, the press and storylines' },
    press: { title: 'The Press Briefing Room', body: 'Press conferences, interviews, addresses and the State of the Union; the outlets’ tone, the party’s wings and the opposition’s strategy.', milestone: 5, stage: 'the executive, the press and storylines' },
    windows: { title: 'The country', body: 'Approval by state and by voter group, from the same electorate that decided the election, and what the country thinks is going wrong.', milestone: 6, stage: 'the economy and the policy table' },
    residence: { title: 'The Residence', body: 'Family, rest and health: where stamina comes back and the president’s private life goes on.', milestone: 6, stage: 'the economy and the policy table' },
    globe: { title: 'The Situation Room', body: 'Forty countries, calls and summits, crises week by week, operations and covert action, and the standing deployments.', milestone: 7, stage: 'the world' },
};
function soon(id) {
    const l = LATER[id];
    host.open(h('div', { class: 'dialog-card' }, h('div', { class: 'eyebrow' }, 'Coming in presidency milestone ' + l.milestone), h('h2', { class: 'dialog-title' }, l.title), h('p', null, l.body), h('p', { class: 'muted' }, `It opens with milestone ${l.milestone}: ${l.stage}.`), h('div', { class: 'dialog-foot' }, h('button', { class: 'btn primary', type: 'button', onclick: () => host.close() }, 'Back to the Oval Office'))), l.title);
}
function card(eyebrow, title, ...body) {
    return h('div', { class: 'dialog-card' }, h('div', { class: 'eyebrow' }, eyebrow), h('h2', { class: 'dialog-title' }, title), ...body, h('div', { class: 'dialog-foot' }, h('button', { class: 'btn primary', type: 'button', onclick: () => host.close() }, 'Back to the Oval Office')));
}
function openSpot(id, v) {
    switch (id) {
        case 'documents': {
            if (v.pendingDecisions > 0) {
                openDecisions();
                return;
            }
            const laws = v.bills.filter(b => b.stage === 'Law');
            host.open(card('The Resolute Desk', 'Nothing waiting for your signature', h('p', null, 'Decisions that need the president, and the bills Congress sends to the desk, wait here to be signed or vetoed.'), laws.length ? h('p', { class: 'muted' }, 'Signed into law from your agenda: ' + laws.map(b => b.name).join(', ') + '.') : null), 'Documents to sign');
            return;
        }
        case 'folders':
            host.open(card('Your agenda', v.bills.length ? `${v.bills.length} ${v.bills.length === 1 ? 'bill' : 'bills'} on your agenda` : 'No agenda yet', v.bills.length ? h('ul', { class: 'ov-dialog-list' }, ...v.bills.map(b => h('li', null, h('span', null, b.name), h('span', { class: 'pill' }, b.stage)))) : null, h('p', { class: 'muted' }, congressLine(v.congress) + '. The whip count and the floor open in the Roosevelt Room with milestone 4.')), 'Your agenda');
            return;
        case 'book':
            host.open(card('The briefing book', 'The economy this month', h('dl', { class: 'ov-figures' }, h('dt', null, 'Unemployment'), h('dd', null, v.unemployment == null ? '—' : v.unemployment.toFixed(1) + '%'), h('dt', null, 'Inflation'), h('dd', null, v.inflation == null ? '—' : v.inflation.toFixed(1) + '%'), h('dt', null, 'Growth'), h('dd', null, v.growth == null ? '—' : v.growth.toFixed(1) + '%'), h('dt', null, 'Gas'), h('dd', null, v.gas == null ? '—' : '$' + v.gas.toFixed(2) + ' a gallon')), h('p', { class: 'muted' }, 'The Council of Economic Advisers’ full briefing, with the policy table, opens with milestone 6.')), 'The briefing book');
            return;
        case 'paper':
            host.open(card('The Capital Ledger', dateLong(v.date), v.news.length ? h('ol', { class: 'ov-paper' }, ...v.news.map(n => h('li', null, h('span', { class: 'ov-paper-date' }, n.date ? day(n.date, true) : ''), h('span', null, n.headline && !n.text.startsWith(n.headline) ? h('strong', null, n.headline + ': ') : null, n.text)))) : h('p', { class: 'muted' }, 'No news yet.'), h('p', { class: 'muted' }, 'A full front page every week (with its photographs, columns and the opposition’s reply) arrives with milestone 3.')), 'The Capital Ledger');
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
    else if (e.key === 'Escape' && !menuOpen() && document.querySelector('.scrim'))
        host.close();
});
// the light follows the clock: look again every five minutes
setInterval(() => { if (hourOverride === null && !document.querySelector('.scrim') && !menuOpen())
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
    saveText = await (await fetch(params.get('save') ?? 'saves/presidency.json')).text();
    engine.load(saveText);
    render();
    requestAnimationFrame(frame);
}
boot().catch(e => { root.replaceChildren(h('p', { class: 'boot-error', role: 'alert' }, 'The game could not start: ' + String(e))); console.error(e); });
