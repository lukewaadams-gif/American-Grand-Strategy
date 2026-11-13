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
    { id: 'westwing', x: 50, y: 200, w: 110, h: 300, label: 'To the West Wing: the Cabinet Room and the staff' },
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
/** Stamina and stress: the president's own meters (Resolute Desk's bars: stamina good above 50, stress bad above 75). */
function meter(label, value, tone, title) {
    if (value === undefined)
        return h('div', { class: 'ov-stat ov-meter pending', title: 'From the inauguration' }, h('span', { class: 'ov-label' }, label), h('span', { class: 'ov-value-row' }, h('span', { class: 'ov-meter-bar', 'aria-hidden': 'true' }), h('span', { class: 'ov-value small' }, '—')));
    return h('div', { class: 'ov-stat ov-meter', title }, h('span', { class: 'ov-label', id: 'ov-m-' + label }, label), h('span', { class: 'ov-value-row' }, h('span', { class: 'ov-meter-bar', role: 'meter', 'aria-labelledby': 'ov-m-' + label, 'aria-valuemin': 0, 'aria-valuemax': 100, 'aria-valuenow': Math.round(value) }, h('span', { class: 'ov-meter-fill ' + tone, style: `width:${Math.max(2, value)}%` })), h('span', { class: 'ov-value small' }, String(Math.round(value)))));
}
function topBar(v, hd) {
    const ap = v.approval, ch = v.approvalChange ?? 0;
    const apTone = ap === null ? '' : ap >= 50 ? 'good' : ap >= 42 ? '' : 'bad';
    const term = v.termWeek > 0 ? `Week ${v.termWeek}` : 'Before the oath';
    const turn = v.turnUnit === 'war' ? 'Next turn' : v.turnUnit === 'transition' ? 'To the inauguration' : 'End week';
    const wk = v.week;
    return h('header', { class: 'ov-bar' }, h('div', { class: 'ov-brand' }, sealIcon(40), h('div', { class: 'ov-brand-text' }, h('span', { class: 'ov-house' }, 'The White House'), h('span', { class: 'ov-president' }, 'President ' + v.president.name), h('span', { class: 'ov-date' }, dateLong(v.date)))), h('div', { class: 'ov-stats', role: 'group', 'aria-label': 'The presidency at a glance' }, stat('Approval', ap === null ? '—' : Math.round(ap) + '%', ap === null ? null : { text: (ch >= 0 ? '+' : '−') + Math.abs(ch).toFixed(1), tone: ch > 0.05 ? 'up' : ch < -0.05 ? 'down' : 'flat' }, apTone, ap === null ? 'Approval is first measured after the inauguration' : `Approval ${ap.toFixed(1)}%, ${ch >= 0 ? 'up' : 'down'} ${Math.abs(ch).toFixed(1)} since the last reading`), meter('Stamina', wk?.stamina, wk ? (wk.stamina >= 50 ? 'good' : wk.stamina >= 25 ? 'warn' : 'bad') : '', `Stamina ${Math.round(wk?.stamina ?? 0)} of 100; after this week ${Math.round(wk?.projected ?? 0)}`), meter('Stress', wk?.stress, wk ? (wk.stress < 50 ? 'calm' : wk.stress < 75 ? 'warn' : 'bad') : '', `Stress ${Math.round(wk?.stress ?? 0)} of 100`), stat('Unemployment', pct(v.unemployment)), stat('Inflation', pct(v.inflation), null, (v.inflation ?? 0) > 4 ? 'warn' : ''), stat('Gas', v.gas === null ? '—' : '$' + v.gas.toFixed(2)), stat('Term 1', term, v.termWeek > 0 ? { text: `of ${v.termWeeks}`, tone: 'flat' } : null)), h('div', { class: 'ov-actions' }, hd.watching
        ? h('button', { class: 'btn toggle on', type: 'button', onclick: () => hd.watch(), title: 'Watch speed: click to change' }, 'Speed: ' + hd.watching)
        : h('button', { class: 'btn quiet', type: 'button', onclick: () => hd.watch(), title: 'Watch mode: choose how the staff governs, then watch the weeks go by' }, 'Watch'), h('button', { class: 'btn quiet', type: 'button', 'aria-haspopup': 'menu', onclick: (e) => hd.menu(e.currentTarget) }, 'Menu'), h('button', { class: 'btn quiet', type: 'button', onclick: () => hd.save(), title: 'Download this game as a save file' }, 'Save'), hd.watching ? h('button', { class: 'btn primary ov-end', type: 'button', onclick: () => hd.stopWatching(), title: 'Take the controls back (Escape)' }, 'Watching… stop')
        : v.turnUnit ? h('button', { class: 'btn primary ov-end', type: 'button', onclick: () => hd.endTurn(), title: `Play out the ${v.turnUnit === 'transition' ? 'transition' : 'week'} (Ctrl+Enter)` }, turn, h('span', { class: 'ov-end-arrow', 'aria-hidden': 'true' }, '›')) : null));
}
/** Resolute Desk's THIS WEEK card: what the week holds, stamina after it, sleep, and the calendar; then what is coming. */
function weekCard(v, hd) {
    const waiting = v.pendingDecisions, w = v.week;
    const thisYear = v.date.slice(0, 4);
    const next = v.upcoming[0];
    const tone = (x) => (x >= 50 ? 'good' : x >= 25 ? 'warn' : 'bad');
    return h('section', { class: 'ov-card ov-week', 'aria-labelledby': 'ov-week-title' }, h('h2', { class: 'ov-card-title', id: 'ov-week-title' }, 'This week'), w ? h('p', { class: 'ov-card-line' }, `${w.counts.policy} policy ${w.counts.policy === 1 ? 'slot' : 'slots'}, ${w.counts.trips} ${w.counts.trips === 1 ? 'trip' : 'trips'}, ${w.counts.recharge} to recharge`
        + (w.delegated ? ' · planned by the chief of staff' : '')) : null, w ? h('div', { class: 'ov-card-meter' }, h('span', { class: 'ov-card-meter-label', id: 'ov-after' }, 'Stamina after the week'), h('span', { class: 'ov-meter-bar wide', role: 'meter', 'aria-labelledby': 'ov-after', 'aria-valuemin': 0, 'aria-valuemax': 100, 'aria-valuenow': Math.round(w.projected) }, h('span', { class: 'ov-meter-fill ' + tone(w.projected), style: `width:${Math.max(2, w.projected)}%` })), h('span', { class: 'ov-card-meter-value' }, String(Math.round(w.projected)))) : null, w ? h('p', { class: 'ov-card-line quiet' }, `Sleep about ${w.sleepAhead.toFixed(1)} hours a night`) : null, h('p', { class: 'ov-card-line ' + (waiting ? 'warn' : 'quiet') }, waiting ? `${waiting} ${waiting === 1 ? 'decision' : 'decisions'} waiting on the desk`
        : next ? `Next: ${SHORT[next.what] ?? next.what}, ${weeksBetween(v.date, next.date) < 26 ? day(next.date) : day(next.date, next.date.slice(0, 4) !== thisYear)}` : 'Nothing waiting on the desk'), h('button', { class: 'btn small ov-card-btn', type: 'button', onclick: () => hd.spot('desk') }, 'Plan the week'));
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
    return n.headline && !n.text.toLowerCase().startsWith(n.headline.toLowerCase()) ? n.headline + ': ' + n.text : n.text;
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
