// The whole game, in any of its four modes (docs/MODES.md): a new game (or a saved game, or one of its moments), the
// war room to Election Day, election night, the transition office, the Oval Office through the first hundred days and
// every week after, the 2032 campaign, the second term, and the end: the verdict of history on a presidency, the
// campaign's assessment, or a crisis's after-action report. One page, routed by the game's phase.
import { buildFile, engine, Refused, start } from './core/engine.js';
import { h } from './core/dom.js';
import { host, toast } from './core/dialog.js';
import { openMenu } from './core/menu.js';
import { usePalette } from './campaign/palette.js';
import { mountCampaign, unmountCampaign } from './campaign/app.js';
import { mountPresidency, unmountPresidency } from './presidency/app.js';
import { transitionOffice } from './presidency/transition.js';
import { dateLong } from './presidency/oval-screen.js';
import { startScreen } from './start.js';
import { takeLaunch } from './core/launch.js';
import { taiwanRoom } from './presidency/taiwan.js';
import { electionNight } from './presidency/election-night.js';
import { soundMenuItem } from './core/sound.js';
import { startTutorial, tutorialProgress } from './tutorial/tutorial.js';
const root = document.getElementById('app');
const params = new URLSearchParams(location.search);
const CAMPAIGN = ['GENERAL_CAMPAIGN_2028', 'REELECTION_CAMPAIGN_2032'], OFFICE = ['FIRST_100_DAYS', 'GOVERNING'];
function body(cls) { document.body.className = cls; }
function leave() { unmountCampaign(); unmountPresidency(); host.close(); }
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
/** Shows the screen for where the game stands. */
function route() {
    leave();
    let phase, night = 0, crisis = false;
    try {
        const p = engine.presidency();
        phase = p.phase;
        night = p.electionNight;
        crisis = !!(p.taiwan || p.hormuz) && p.turnUnit !== null && !p.over;
    }
    catch {
        showStart();
        return;
    }
    // a presidential election night not yet watched always comes first, whatever the phase has moved on to
    if (night) {
        body('presidency');
        showElectionNight(night);
        return;
    }
    // a crisis that runs on into the 2032 campaign is fought from the Oval Office; the war room waits for it to end
    if (CAMPAIGN.includes(phase) && crisis) {
        body('presidency');
        mountPresidency({ onLeave: route, restart: showStart, restartLabel: 'Back to the start', menuItems: gameMenu });
        return;
    }
    if (CAMPAIGN.includes(phase)) {
        body('');
        mountCampaign({ onElectionOver: route, onLeave: route, restart: showStart });
    }
    else if (phase === 'TRANSITION') {
        body('presidency');
        showTransition();
    }
    else if (OFFICE.includes(phase)) {
        body('presidency');
        mountPresidency({ onLeave: route, restart: showStart, restartLabel: 'Back to the start', menuItems: gameMenu });
    }
    else {
        body('presidency');
        showEnd();
    }
}
function saveGame() {
    try {
        const blob = new Blob([engine.save()], { type: 'application/json' });
        const a = h('a', { href: URL.createObjectURL(blob), download: 'american-grand-strategy.json' });
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
function gameMenu() {
    return [
        { label: 'Save the game', detail: 'A file you can open again from the start screen', pick: saveGame },
        { label: 'Back to the start', detail: 'A new campaign, or a saved game (save this one first)', pick: showStart },
    ];
}
/** What the start screen's choices do here: begin, open or jump into the game on this page. */
const startActions = {
    options: real => engine.options(real),
    newGame: g => {
        // Governing Only plays the 2028 campaign for both sides first: a moment's wait, said so
        if (g.mode === 'GOVERNING_ONLY')
            root.replaceChildren(h('main', { class: 'start front', id: 'main' }, h('p', { class: 'start-wait', role: 'status' }, 'The staff are playing the 2028 campaign for both sides. Election night comes next.')));
        setTimeout(() => { try {
            engine.newGame(g);
            route();
        }
        catch (e) {
            toast(e instanceof Refused ? e.message : String(e));
            showStart();
        } }, 30);
    },
    loadFile: text => { try {
        engine.load(text);
        route();
    }
    catch (e) {
        toast('That file is not a saved game: ' + String(e));
        showStart();
    } },
    quick: async (save, then) => { try {
        engine.load(await buildFile(save));
        if (then)
            engine.command(then);
        route();
    }
    catch (e) {
        toast(String(e));
        showStart();
    } },
    islandChain: o => { try {
        engine.islandChain.start(o);
        showIslandChain();
    }
    catch (e) {
        toast(e instanceof Refused ? e.message : String(e));
        showStart();
    } },
    tutorial: () => { void startTutorial({ refresh: route, done: keep => { if (keep)
            route();
        else
            showStart(); } }); },
    tutorialChapter: () => tutorialProgress(),
};
function showStart() {
    leave();
    body('presidency start-page');
    root.replaceChildren(startScreen(startActions));
    document.title = 'American Grand Strategy';
}
/** A choice made on the site's front page, carried out here (core/launch.ts). */
function launch(l) {
    body('presidency start-page');
    switch (l.kind) {
        case 'new':
            startActions.newGame(l.game);
            return;
        case 'quick':
            startActions.quick(l.save, l.then);
            return;
        case 'load':
            startActions.loadFile(l.text);
            return;
        case 'islandChain':
            startActions.islandChain(l.options);
            return;
        case 'tutorial':
            startActions.tutorial();
            return;
    }
}
/** Election night, then wherever the game goes from it. */
function showElectionNight(year) {
    root.replaceChildren(electionNight({
        view: () => engine.electionNight(),
        done: () => { if (command({ type: 'ElectionNightSeen', year }))
            route(); },
    }));
    document.title = `Election night ${year} · American Grand Strategy`;
}
/** Island Chain on its own: the Taiwan crisis room, without a presidency around it. */
function showIslandChain() {
    leave();
    body('presidency');
    let room;
    const draw = () => { const next = taiwanRoom(acts); room?.replaceWith(next); room = next; };
    const acts = {
        view: () => engine.islandChain.view(),
        order: (o) => { try {
            engine.islandChain.order(o);
            return true;
        }
        catch (e) {
            toast(e instanceof Refused ? e.message : String(e));
            return false;
        } },
        next: () => { try {
            engine.islandChain.turn();
        }
        catch (e) {
            toast(e instanceof Refused ? e.message : String(e));
        } draw(); },
        back: () => { try {
            engine.islandChain.end();
        }
        catch { /* none */ } showStart(); },
    };
    room = taiwanRoom(acts);
    root.replaceChildren(h('main', { class: 'ic-page', id: 'main' }, room));
    document.title = 'Island Chain · American Grand Strategy';
}
function showTransition() {
    const office = transitionOffice({
        view: () => engine.transition(),
        agenda: ids => command({ type: 'SetAgenda', bills: ids }),
        act: (id, action) => command({ type: 'WestWing', order: { type: 'TransitionStep', prospect: id, action } }),
        inaugurate: () => { if (command({ type: 'EndTurn' }))
            route(); },
        menu: anchor => openMenu(anchor, 'Menu', [soundMenuItem(), ...gameMenu()]),
    });
    root.replaceChildren(office);
    document.title = 'The transition · American Grand Strategy';
}
/** The end of the game: a crisis's after-action report, the campaign's assessment, or the verdict of history on a presidency. */
function showEnd() {
    const vd = engine.verdict();
    const page = vd.crisis ? crisisEnd(vd) : vd.campaign && !vd.served ? campaignEnd(vd) : presidencyEnd(vd);
    root.replaceChildren(h('main', { class: 'room hill verdict end-page', id: 'main' }, h('div', { class: 'eyebrow' }, dateLong(vd.date)), ...page, h('div', { class: 'cab-buttons' }, h('button', { class: 'btn', type: 'button', onclick: saveGame }, 'Save the game'), h('button', { class: 'btn primary', type: 'button', onclick: showStart }, 'Back to the start'))));
    document.title = (vd.crisis ? 'After-action report' : vd.campaign && !vd.served ? 'The campaign' : 'The verdict') + ' · American Grand Strategy';
}
/** The verdict of history: four years, or eight. */
function presidencyEnd(vd) {
    const measures = vd.measures ?? [];
    return [
        h('h1', { class: 'dialog-title' }, measures.length ? `The verdict of history: ${vd.terms === 2 ? 'eight years' : 'four years'}` : 'The game is over'),
        h('p', { class: 'verdict-grade' }, (vd.grade ?? '') + (vd.score !== undefined && measures.length ? ` · ${vd.score} of 1,000` : '')),
        vd.rank ? h('p', { class: 'verdict-rank' }, vd.rank) : null,
        measures.length ? h('dl', { class: 'ww-stats verdict-measures' }, ...measures.flatMap(m => [h('dt', null, m.name),
            h('dd', null, h('span', { class: 'hill-meter ' + (m.score >= 60 ? 'good' : m.score >= 40 ? 'mid' : 'bad'), role: 'meter', 'aria-valuemin': '0', 'aria-valuemax': '100',
                'aria-valuenow': String(m.score), 'aria-label': m.name }, h('span', { style: `width:${Math.max(2, m.score)}%` }), h('span', { class: 'hill-meter-value' }, String(m.score))))])) : null,
        h('ul', { class: 'hill-list' }, ...(vd.lines ?? []).map(l => h('li', null, l))),
        (vd.record ?? []).length ? h('details', null, h('summary', null, 'Your record'), h('ul', { class: 'hill-list small' }, ...(vd.record ?? []).map(l => h('li', null, l)))) : null,
    ];
}
/** The campaign's end (Campaign Only, or a 2028 defeat): the result, the popular vote, every state and the assessment. */
function campaignEnd(vd) {
    const c = vd.campaign;
    const states = [...c.states].sort((a, b) => Math.abs(a.margin) - Math.abs(b.margin));
    const won = states.filter(x => x.margin > 0).length;
    return [
        h('h1', { class: 'dialog-title' }, 'The campaign is over'),
        h('p', { class: 'end-result ' + (c.won ? 'won' : 'lost') }, `${c.won ? 'You won the White House' : 'You lost'}: ${c.grade.toLowerCase()}.`),
        h('dl', { class: 'ww-stats' }, h('dt', null, 'The electoral vote'), h('dd', null, `${c.player} ${c.ev}, ${c.opponent} ${c.evOpp}`), h('dt', null, 'The popular vote'), h('dd', null, `${c.popular >= 0 ? c.player : c.opponent} by ${Math.abs(c.popular).toFixed(1)} points`), h('dt', null, 'The states'), h('dd', null, `${won} won, ${states.length - won} lost`)),
        h('section', { 'aria-labelledby': 'end-as' }, h('h2', { class: 'tw-sub', id: 'end-as' }, 'The assessment'), h('ul', { class: 'hill-list' }, ...c.lines.map(l => h('li', null, l)))),
        h('details', null, h('summary', null, 'Every state, closest first'), h('div', { class: 'en-board-wrap', tabindex: '0', role: 'region', 'aria-label': 'Every state, closest first' }, h('table', { class: 'end-states' }, h('thead', null, h('tr', null, h('th', { scope: 'col' }, 'State'), h('th', { scope: 'col', class: 'num' }, 'Electoral votes'), h('th', { scope: 'col', class: 'num' }, 'Your margin'))), h('tbody', null, ...states.map(x => h('tr', null, h('td', null, x.name), h('td', { class: 'num' }, String(x.ev)), h('td', { class: 'num' }, (x.margin >= 0 ? '+' : '−') + Math.abs(x.margin).toFixed(1)))))))),
        h('div', { class: 'cab-buttons' }, h('button', { class: 'btn', type: 'button', onclick: () => showElectionNight(2028) }, 'Watch election night again'), h('button', { class: 'btn', type: 'button', onclick: () => { try {
                engine.restart();
                route();
            }
            catch (e) {
                toast(String(e));
            } } }, 'Replay the campaign in the same world')),
    ];
}
/** Individual Crisis: the after-action report. */
function crisisEnd(vd) {
    const c = vd.crisis;
    const tone = c.result === 'WON' ? 'won' : c.result === 'LOST' ? 'lost' : 'part';
    return [
        h('h1', { class: 'dialog-title' }, 'After-action report: ' + c.title),
        h('p', { class: 'end-result ' + tone }, `${c.resultLabel ?? 'Not over'}: ${(c.outcome ?? '').toLowerCase()}.`),
        c.verdict ? h('p', { class: 'verdict-rank' }, c.verdict) : null,
        h('dl', { class: 'ww-stats' }, h('dt', null, 'How it started'), h('dd', null, c.preset), h('dt', null, 'Began'), h('dd', null, dateLong(c.began)), c.ended ? h('dt', null, 'Ended') : null, c.ended ? h('dd', null, `${dateLong(c.ended)}, after ${c.days} days`) : null, c.reason ? h('dt', null, 'Why') : null, c.reason ? h('dd', null, c.reason) : null),
        h('section', { 'aria-labelledby': 'end-fig' }, h('h2', { class: 'tw-sub', id: 'end-fig' }, 'The figures'), h('ul', { class: 'hill-list' }, ...(c.figures ?? []).map(l => h('li', null, l)))),
        (c.record ?? []).length ? h('details', null, h('summary', null, 'The crisis, day by day'), h('ul', { class: 'hill-list small' }, ...(c.record ?? []).map(l => h('li', null, l)))) : null,
    ];
}
async function boot() {
    usePalette(params.get('palette') ?? 'standard');
    if (params.get('palette'))
        document.documentElement.dataset['palette'] = params.get('palette');
    if (params.get('motion') === 'reduce')
        document.documentElement.dataset['motion'] = 'reduce';
    if (params.get('size'))
        document.documentElement.style.setProperty('--s', params.get('size'));
    await start();
    const save = params.get('save');
    const handed = takeLaunch();
    if (save) {
        engine.load(await buildFile(save));
        route();
    }
    else if (handed)
        launch(handed);
    else
        showStart();
}
boot().catch(e => {
    root.replaceChildren(h('p', { class: 'boot-error', role: 'alert' }, 'The game could not start: ' + String(e)
        + '. Reloading the page past the cache (Ctrl+Shift+R, or Cmd+Shift+R on a Mac) fetches the latest version.'));
    console.error(e);
});
