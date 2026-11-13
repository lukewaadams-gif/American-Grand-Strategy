// The preview of the new interface: the campaign's war room, live over the real engine. It opens a real
// mid-campaign save (week 9 of 16) and plays from there; the rest of the game's screens follow in later stages.
import { engine, Refused, start } from './core/engine.js';
import { h } from './core/dom.js';
import { day } from './core/format.js';
import { resetWarRoomMemory, warRoom } from './campaign/warroom.js';
import { decisionCard } from './campaign/decision.js';
import { closeMenu, menuOpen } from './core/menu.js';
import { usePalette } from './campaign/palette.js';
const root = document.getElementById('app');
const state = { layer: 'margin', selected: null, tab: 'schedule', picking: null };
let toastTimer = 0;
const host = (() => {
    let scrim = null, onClose, returnTo = null;
    return {
        open(content, label, close) {
            if (scrim)
                scrim.remove();
            else
                returnTo = document.activeElement;
            onClose = close;
            scrim = h('div', { class: 'scrim' }, h('div', { class: 'dialog', role: 'dialog', 'aria-modal': 'true', 'aria-label': label }, content));
            document.body.append(scrim);
            scrim.querySelector('button.choice, button.primary, button')?.focus();
        },
        close() {
            if (!scrim)
                return;
            scrim.remove();
            scrim = null;
            const cb = onClose;
            onClose = undefined;
            if (returnTo instanceof HTMLElement)
                returnTo.focus();
            cb?.();
        },
    };
})();
function toast(text) {
    let t = document.getElementById('toast');
    if (!t) {
        t = h('div', { id: 'toast', class: 'toast', role: 'status', 'aria-live': 'polite' });
        document.body.append(t);
    }
    t.textContent = text;
    t.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => t.classList.remove('show'), 6000);
}
function render() {
    let v;
    try {
        v = engine.campaign();
    }
    catch (e) {
        toast(String(e));
        return;
    }
    const focusKey = document.activeElement?.getAttribute('aria-label') ?? null;
    const scroll = document.getElementById('main')?.scrollTop ?? 0;
    root.replaceChildren(warRoom(v, state, {
        runWeek, openDecisions, toast, order,
        setLayer: id => { state.layer = id; render(); },
        select: c => { state.selected = c; render(); },
        setTab: id => { state.tab = id; render(); document.getElementById('tab-' + id)?.focus(); },
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
    if (v.phase !== 'GENERAL_CAMPAIGN_2028' && v.phase !== 'REELECTION_CAMPAIGN_2032')
        electionOver(v);
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
function runWeek() {
    const v = engine.campaign();
    if (v.phase !== 'GENERAL_CAMPAIGN_2028' && v.phase !== 'REELECTION_CAMPAIGN_2032')
        return;
    if (v.pendingDecisions > 0) {
        openDecisions(runWeek);
        return;
    }
    try {
        engine.command({ type: 'EndTurn' });
    }
    catch (e) {
        toast(e instanceof Refused ? e.message : String(e));
        return;
    }
    render();
}
/** After Election Day the campaign view shows the result; the broadcast election night is the next stage. */
function electionOver(v) {
    const r = v.result;
    if (!r)
        return;
    const won = r.winner === v.player.party;
    host.open(h('div', { class: 'dialog-card' }, h('div', { class: 'eyebrow' }, 'Election night · ' + day(v.electionDay, true)), h('h2', { class: 'dialog-title big' }, won ? 'You win the presidency' : 'You lose'), h('p', null, `Democrats ${r.dem}, Republicans ${r.rep}. Popular vote: ${r.popular >= 0 ? 'Democrats' : 'Republicans'} by ${Math.abs(r.popular).toFixed(1)}.`), h('p', { class: 'muted' }, 'This preview covers the campaign war room. Election night as a broadcast, the transition and the presidency come in the next stages.'), h('div', { class: 'dialog-foot' }, h('button', { class: 'btn primary', type: 'button', onclick: () => { host.close(); restart(); } }, 'Play the campaign again'))), 'Election night');
}
let saveText = '';
function restart() { engine.load(saveText); resetWarRoomMemory(); state.selected = null; render(); }
document.addEventListener('keydown', e => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
        e.preventDefault();
        closeMenu(false);
        runWeek();
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
async function boot() {
    const params = new URLSearchParams(location.search);
    usePalette(params.get('palette') ?? 'standard');
    if (params.get('motion') === 'reduce')
        document.documentElement.dataset['motion'] = 'reduce';
    if (params.get('size'))
        document.documentElement.style.setProperty('--s', params.get('size'));
    await start();
    saveText = await (await fetch(params.get('save') ?? 'saves/campaign.json')).text();
    engine.load(saveText);
    render();
}
boot().catch(e => { root.replaceChildren(h('p', { class: 'boot-error', role: 'alert' }, 'The game could not start: ' + String(e))); console.error(e); });
