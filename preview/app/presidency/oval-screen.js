// The Oval Office screen (Resolute Desk's ui/Oval.java): the White House top bar, the room with everything in it that
// can be used, the week's card and the Hill's card in the lower corners, and The Ledger along the bottom. The room is
// a picture; what can be used in it is a row of real buttons laid over the picture, so a keyboard or a screen reader
// reaches every door and every object on the desk.
import { h } from '../core/dom.js';
import { day } from '../core/format.js';
import { sealIcon } from './seal.js';
import { paintOval } from './oval.js';
/**
 * What can be used in the room, in the order Tab reaches it: the desk's objects first (Q28), then the rest of the room,
 * then the doors from left to right. Rectangles are in the room's frame (Resolute Desk's Oval.SPOT, with the desk's top
 * split into its objects).
 */
export const SPOTS = [
    { id: 'documents', x: 656, y: 404, w: 86, h: 46, label: 'Documents to sign' },
    { id: 'folders', x: 818, y: 404, w: 56, h: 46, label: 'Folders: your agenda' },
    { id: 'phone', x: 874, y: 404, w: 64, h: 46, label: 'The telephone' },
    { id: 'desk', x: 650, y: 452, w: 300, h: 146, label: 'The Resolute Desk: plan your week' },
    { id: 'book', x: 1290, y: 455, w: 120, h: 110, label: 'The briefing book: the economy' },
    { id: 'paper', x: 690, y: 752, w: 220, h: 70, label: "Today's paper: The Capital Ledger" },
    { id: 'windows', x: 585, y: 120, w: 430, h: 255, label: 'The windows: the country' },
    { id: 'globe', x: 300, y: 420, w: 110, h: 150, label: 'The globe: the Situation Room' },
    { id: 'westwing', x: 50, y: 200, w: 110, h: 300, label: 'To the West Wing: staff and cabinet' },
    { id: 'press', x: 205, y: 150, w: 90, h: 270, label: 'To the Press Briefing Room' },
    { id: 'roosevelt', x: 1180, y: 160, w: 90, h: 250, label: 'To the Roosevelt Room: Congress' },
    { id: 'residence', x: 1440, y: 200, w: 110, h: 300, label: 'To the Residence' },
];
const WEEKDAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
/** "2029-09-01" as "Saturday, September 1, 2029". */
export function dateLong(iso) {
    const [y, m, d] = iso.split('-').map(Number);
    return `${WEEKDAYS[new Date(Date.UTC(y, m - 1, d)).getUTCDay()]}, ${MONTHS[m - 1]} ${d}, ${y}`;
}
function weeksBetween(a, b) {
    const t = (iso) => { const [y, m, d] = iso.split('-').map(Number); return Date.UTC(y, m - 1, d); };
    return Math.round((t(b) - t(a)) / (7 * 86400000));
}
/** "House D 212-R 223, Senate D 50-R 50" as the majority first: "House R 223–212 · Senate 50–50". */
export function congressLine(c) {
    const m = /House D (\d+)-R (\d+), Senate D (\d+)-R (\d+)/.exec(c);
    if (!m)
        return c;
    const [hd, hr, sd, sr] = m.slice(1).map(Number);
    const side = (d, r) => d === r ? `${d}–${r}` : d > r ? `D ${d}–${r}` : `R ${r}–${d}`;
    return `House ${side(hd, hr)} · Senate ${side(sd, sr)}`;
}
const pct = (v, digits = 1) => v === null || v === undefined ? '—' : v.toFixed(digits) + '%';
function stat(label, value, sub = null, tone = '', title = null) {
    return h('div', { class: 'ov-stat', title }, h('span', { class: 'ov-label' }, label), h('span', { class: 'ov-value-row' }, h('span', { class: 'ov-value ' + tone }, value), sub ? h('span', { class: 'ov-sub ' + sub.tone }, sub.text) : null));
}
/** Stamina and stress: the president's own meters, which arrive with the weekly schedule. */
function meter(label, title) {
    return h('div', { class: 'ov-stat ov-meter pending', title }, h('span', { class: 'ov-label' }, label), h('span', { class: 'ov-value-row' }, h('span', { class: 'ov-meter-bar', 'aria-hidden': 'true' }), h('span', { class: 'ov-value small' }, '—')), h('span', { class: 'sr' }, title));
}
function topBar(v, hd) {
    const ap = v.approval, ch = v.approvalChange ?? 0;
    const apTone = ap === null ? '' : ap >= 50 ? 'good' : ap >= 42 ? '' : 'bad';
    const term = v.termWeek > 0 ? `Week ${v.termWeek}` : 'Before the oath';
    const turn = v.turnUnit === 'month' ? 'End month' : v.turnUnit === 'transition' ? 'To the inauguration' : 'End week';
    return h('header', { class: 'ov-bar' }, h('div', { class: 'ov-brand' }, sealIcon(40), h('div', { class: 'ov-brand-text' }, h('span', { class: 'ov-house' }, 'The White House'), h('span', { class: 'ov-president' }, 'President ' + v.president.name), h('span', { class: 'ov-date' }, dateLong(v.date)))), h('div', { class: 'ov-stats', role: 'group', 'aria-label': 'The presidency at a glance' }, stat('Approval', ap === null ? '—' : Math.round(ap) + '%', ap === null ? null : { text: (ch >= 0 ? '+' : '−') + Math.abs(ch).toFixed(1), tone: ch > 0.05 ? 'up' : ch < -0.05 ? 'down' : 'flat' }, apTone, ap === null ? 'Approval is first measured after the inauguration' : `Approval ${ap.toFixed(1)}%, ${ch >= 0 ? 'up' : 'down'} ${Math.abs(ch).toFixed(1)} since the last reading`), meter('Stamina', 'Stamina arrives with the weekly schedule (presidency milestone 3)'), meter('Stress', 'Stress arrives with the weekly schedule (presidency milestone 3)'), stat('Unemployment', pct(v.unemployment)), stat('Inflation', pct(v.inflation), null, (v.inflation ?? 0) > 4 ? 'warn' : ''), stat('Gas', v.gas === null ? '—' : '$' + v.gas.toFixed(2)), stat('Term 1', term, v.termWeek > 0 ? { text: `of ${v.termWeeks}`, tone: 'flat' } : null)), h('div', { class: 'ov-actions' }, h('button', { class: 'btn quiet', type: 'button', onclick: () => hd.watch(), title: 'Watch mode: let the staff govern while you watch (arrives with the weekly schedule)' }, 'Watch'), h('button', { class: 'btn quiet', type: 'button', 'aria-haspopup': 'menu', onclick: (e) => hd.menu(e.currentTarget) }, 'Menu'), h('button', { class: 'btn quiet', type: 'button', onclick: () => hd.save(), title: 'Download this game as a save file' }, 'Save'), v.turnUnit ? h('button', { class: 'btn primary ov-end', type: 'button', onclick: () => hd.endTurn(), title: `Play out the ${v.turnUnit === 'transition' ? 'transition' : v.turnUnit} (Ctrl+Enter)` }, turn, h('span', { class: 'ov-end-arrow', 'aria-hidden': 'true' }, '›')) : null));
}
function weekCard(v, hd) {
    const waiting = v.pendingDecisions;
    const thisYear = v.date.slice(0, 4);
    return h('section', { class: 'ov-card ov-week', 'aria-labelledby': 'ov-week-title' }, h('h2', { class: 'ov-card-title', id: 'ov-week-title' }, v.turnUnit === 'month' ? 'This month' : 'This week'), h('p', { class: 'ov-card-line ' + (waiting ? 'warn' : 'quiet') }, waiting ? `${waiting} ${waiting === 1 ? 'decision' : 'decisions'} waiting on the desk` : 'Nothing waiting on the desk'), v.upcoming.length ? h('ul', { class: 'ov-upcoming', 'aria-label': 'Coming up' }, ...v.upcoming.slice(0, 3).map(u => {
        const w = weeksBetween(v.date, u.date);
        return h('li', null, h('span', null, SHORT[u.what] ?? u.what), h('span', { class: 'ov-when' }, w < 2 ? day(u.date) : w < 26 ? `${day(u.date)} \u00b7 ${w} wks` : day(u.date, u.date.slice(0, 4) !== thisYear)));
    })) : null, h('button', { class: 'btn small ov-card-btn', type: 'button', onclick: () => hd.spot('desk') }, 'Plan the week'));
}
const SHORT = { 'The midterm elections': 'Midterms', 'Election Day 2032': 'Election Day' };
const STAGE_TONE = { 'Law': 'good', 'Dead': 'bad', 'Not yet moving': 'quiet' };
function hillCard(v, hd) {
    const bills = v.bills.slice(0, 3);
    return h('section', { class: 'ov-card ov-hill', 'aria-labelledby': 'ov-hill-title' }, h('h2', { class: 'ov-card-title', id: 'ov-hill-title' }, 'On the Hill'), bills.length ? h('ul', { class: 'ov-bills' }, ...bills.map(b => h('li', null, h('span', { class: 'ov-bill' }, b.name), h('span', { class: 'ov-bill-stage ' + (STAGE_TONE[b.stage] ?? 'moving') }, b.stage))))
        : h('p', { class: 'ov-card-line quiet' }, v.turnUnit === 'transition' ? 'Your agenda goes to Congress after the oath.' : 'None of your bills is moving.'), h('p', { class: 'ov-card-line quiet' }, congressLine(v.congress)), h('button', { class: 'btn small ov-card-btn', type: 'button', onclick: () => hd.spot('roosevelt') }, 'The Roosevelt Room'));
}
/** A news item as a line: its headline first, unless the text already starts with it. */
export function headline(n) {
    return n.headline && !n.text.startsWith(n.headline) ? n.headline + ': ' + n.text : n.text;
}
/** The Ledger: the latest headlines running along the bottom, with a pause button; still under reduced motion. */
function ledger(v) {
    const heads = v.news.map(headline);
    const run = () => h('div', { class: 'ov-ledger-run' }, ...heads.map(t => h('span', { class: 'ov-ledger-item' }, t)));
    const track = h('div', { class: 'ov-ledger-track', 'aria-hidden': 'true' }, h('div', { class: 'ov-ledger-belt' }, run(), run()));
    const pause = h('button', { class: 'ov-ledger-pause', type: 'button', 'aria-pressed': 'false', title: 'Pause The Ledger' }, 'Pause');
    pause.addEventListener('click', () => {
        const on = pause.getAttribute('aria-pressed') !== 'true';
        pause.setAttribute('aria-pressed', String(on));
        pause.textContent = on ? 'Play' : 'Pause';
        pause.title = on ? 'Run The Ledger' : 'Pause The Ledger';
        track.classList.toggle('paused', on);
    });
    // the belt runs at about 60 pixels a second, whatever the number of headlines
    const chars = heads.join('').length + heads.length * 8;
    track.style.setProperty('--ledger-time', Math.max(30, Math.round(chars * 0.13)) + 's');
    return h('footer', { class: 'ov-ledger' }, h('span', { class: 'ov-ledger-label' }, 'The Ledger'), track, h('ul', { class: 'sr', 'aria-label': 'The Ledger: latest headlines' }, ...heads.map(t => h('li', null, t))), pause);
}
/** The hotspots: real buttons over the picture, with a label that appears on hover and on focus. */
function spots(hd, v) {
    const layer = h('div', { class: 'ov-spots' });
    const tip = h('div', { class: 'ov-tip', 'aria-hidden': 'true' });
    const show = (sp) => {
        tip.textContent = sp.label;
        tip.style.left = ((sp.x + sp.w / 2) / 16) + '%';
        const below = sp.y + sp.h + 12 <= 790;
        tip.style.top = (((below ? sp.y + sp.h + 8 : sp.y - 8) - 56) / 8) + '%';
        tip.classList.toggle('above', !below);
        tip.classList.add('show');
    };
    const hide = () => tip.classList.remove('show');
    for (const sp of SPOTS) {
        const badge = sp.id === 'documents' && v.pendingDecisions > 0 ? v.pendingDecisions : 0;
        const b = h('button', { class: 'ov-spot ov-spot-' + sp.id, type: 'button', 'aria-label': sp.label + (badge ? ` (${badge} waiting)` : ''),
            style: `left:${sp.x / 16}%;top:${(sp.y - 56) / 8}%;width:${sp.w / 16}%;height:${sp.h / 8}%` }, badge ? h('span', { class: 'ov-badge', 'aria-hidden': 'true' }, String(badge)) : null);
        b.addEventListener('click', () => hd.spot(sp.id));
        b.addEventListener('pointerenter', () => show(sp));
        b.addEventListener('pointerleave', hide);
        b.addEventListener('focus', () => show(sp));
        b.addEventListener('blur', hide);
        layer.append(b);
    }
    layer.append(tip);
    return layer;
}
/** The whole screen. The room is returned too, so its flags, globe and dust can be animated. */
export function ovalScreen(v, light, hd) {
    const room = paintOval(v.president.party, v.president.id, light);
    const stage = h('div', { class: 'ov-stage' }, room.svg, spots(hd, v));
    const el = h('div', { class: 'ov' }, topBar(v, hd), h('main', { class: 'ov-main', id: 'main' }, h('h1', { class: 'sr' }, `The Oval Office, ${dateLong(v.date)}. President ${v.president.name}.`), h('div', { class: 'ov-frame', 'data-light': light.label }, stage, h('div', { class: 'ov-cards' }, weekCard(v, hd), hillCard(v, hd)))), ledger(v));
    return { el, room };
}
