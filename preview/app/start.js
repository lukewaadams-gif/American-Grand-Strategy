// The game's front end: a title screen, then a new game set up a step at a time, as a game does it.
//   The title:   New Game, Continue (a saved file), Start at a moment.
//   The setup:   the game (one of the four on the one engine, docs/MODES.md), then for a campaign or a presidency your
//                side and cast, your candidate, the opponent and the country, and a face-off before you begin; for a
//                crisis, which one and how it starts.
// The world is real people by default (the 2028 field); the fictional cast is the alternative. The same screens serve
// the game's own page and the site's front page (front-main.ts), which hands the choice over to the game.
import { h, s } from './core/dom.js';
import { portrait } from './art/portrait.js';
import { buildInfo } from './core/engine.js';
/** The four games, in the order the setup offers them. */
export const MODES = [
    { id: 'FULL_GAME', label: 'Full Game', tag: 'The campaign and the presidency',
        detail: 'Win the 2028 election, then govern: the first hundred days, the midterms, re-election in 2032 and, if you win it, a second term to January 2037.', time: '10–15 hours' },
    { id: 'CAMPAIGN_ONLY', label: 'Campaign Only', tag: '2028, to election night',
        detail: 'Run the 2028 campaign from the war room. It ends on election night, won or lost, with the map, the vote and an assessment of your campaign.', time: '1–2 hours' },
    { id: 'GOVERNING_ONLY', label: 'Governing Only', tag: 'From election night, 2028',
        detail: 'Begin as the president-elect on November 7, 2028, with the administration you choose, and govern through re-election and a second term.', time: '5–15 hours' },
    { id: 'CRISIS', label: 'Individual Crisis', tag: 'Taiwan or the Strait of Hormuz',
        detail: 'One crisis, on the presidency’s own systems. It ends when the crisis does (won, lost or ended in part) with an after-action report.', time: '30–90 minutes' },
];
const CRISES = [
    { id: 'taiwan', label: 'The Taiwan crisis', detail: 'China’s coast guard and navy close in on Taiwan: weeks of warning, then, if it comes, the war by the hour.' },
    { id: 'hormuz', label: 'The Strait of Hormuz', detail: 'The Guards seize a tanker and Tehran threatens to close the strait: day by day, a fifth of the world’s oil.' },
];
const PRESETS = [
    { label: 'Base case', detail: 'The crisis as the intelligence community expects it.' },
    { label: 'Optimistic', detail: 'More warning, steadier allies, a less determined adversary.' },
    { label: 'Pessimistic', detail: 'Less warning, shakier allies, an adversary ready to go further.' },
];
const MOMENTS = [
    { save: 'saves/campaign.json', label: 'The campaign, week 9', detail: 'Battleground 2028’s headquarters at the height of the race.' },
    { save: 'saves/presidency.json', label: 'Inauguration week', detail: 'Day one: the first hundred days open the presidency, with the flagship bill to pass.' },
    { save: 'saves/governing.json', label: 'Governing', detail: 'September 2029: Congress, the West Wing and the world are yours.' },
    { save: 'saves/taiwan.json', label: 'A Taiwan crisis', detail: 'September 2029: China’s coast guard closes on Taiwan, and the Situation Room counts the days.' },
    { save: 'saves/secondterm.json', label: 'Four more years', detail: 'Just re-elected: the second term’s theme on the desk.' },
    { save: 'saves/succession.json', label: 'The race to succeed you', detail: 'Spring 2036: the primaries to choose your successor.' },
];
const STATES = { AL: 'Alabama', AK: 'Alaska', AZ: 'Arizona', AR: 'Arkansas', CA: 'California', CO: 'Colorado', CT: 'Connecticut',
    DE: 'Delaware', FL: 'Florida', GA: 'Georgia', HI: 'Hawaii', ID: 'Idaho', IL: 'Illinois', IN: 'Indiana', IA: 'Iowa', KS: 'Kansas', KY: 'Kentucky',
    LA: 'Louisiana', ME: 'Maine', MD: 'Maryland', MA: 'Massachusetts', MI: 'Michigan', MN: 'Minnesota', MS: 'Mississippi', MO: 'Missouri', MT: 'Montana',
    NE: 'Nebraska', NV: 'Nevada', NH: 'New Hampshire', NJ: 'New Jersey', NM: 'New Mexico', NY: 'New York', NC: 'North Carolina', ND: 'North Dakota',
    OH: 'Ohio', OK: 'Oklahoma', OR: 'Oregon', PA: 'Pennsylvania', RI: 'Rhode Island', SC: 'South Carolina', SD: 'South Dakota', TN: 'Tennessee', TX: 'Texas',
    UT: 'Utah', VT: 'Vermont', VA: 'Virginia', WA: 'Washington', WV: 'West Virginia', WI: 'Wisconsin', WY: 'Wyoming', DC: 'the District of Columbia' };
/** Where a candidate stands, from the ideology score (-1 left .. +1 right). */
function leaning(x) {
    if (x <= -0.6)
        return 'progressive';
    if (x <= -0.35)
        return 'liberal';
    if (x < -0.1)
        return 'center-left';
    if (x <= 0.1)
        return 'centrist';
    if (x < 0.35)
        return 'center-right';
    if (x < 0.6)
        return 'conservative';
    return 'hard-right';
}
const STATS = [
    { key: 'charisma', label: 'Charisma' }, { key: 'debate', label: 'Debate' }, { key: 'fundraising', label: 'Money' },
    { key: 'policy', label: 'Policy' }, { key: 'discipline', label: 'Discipline' },
];
// ---- the art: emblems drawn in the game's own style (no outside imagery) ----------------------------------------------
/** The Capitol at night, for the title screen: a silhouette with a warm rim of light. */
function capitol() {
    const fill = '#060a12', rim = 'rgba(217, 180, 90, 0.55)';
    const cols = (x0, x1, y, hgt, step) => {
        const l = [];
        for (let x = x0; x <= x1; x += step)
            l.push(s('rect', { x, y, width: 3, height: hgt, fill: 'rgba(217, 180, 90, 0.13)' }));
        return l;
    };
    return s('svg', { class: 'front-capitol', viewBox: '0 0 1200 320', preserveAspectRatio: 'xMidYMax meet', 'aria-hidden': 'true' }, s('defs', null, s('radialGradient', { id: 'front-glow', cx: '50%', cy: '70%', r: '50%' }, s('stop', { offset: '0%', 'stop-color': 'rgba(217, 180, 90, 0.35)' }), s('stop', { offset: '100%', 'stop-color': 'rgba(217, 180, 90, 0)' }))), s('ellipse', { cx: 600, cy: 230, rx: 520, ry: 170, fill: 'url(#front-glow)' }), s('g', { fill, stroke: rim, 'stroke-width': 1.2 }, s('rect', { x: 110, y: 214, width: 300, height: 52 }), s('rect', { x: 790, y: 214, width: 300, height: 52 }), s('rect', { x: 400, y: 186, width: 400, height: 80 }), s('polygon', { points: '500,186 600,150 700,186' }), s('rect', { x: 512, y: 112, width: 176, height: 46 }), s('rect', { x: 498, y: 156, width: 204, height: 30 }), s('path', { d: 'M520,114 C520,34 680,34 680,114 Z' }), s('rect', { x: 588, y: 26, width: 24, height: 26 }), s('rect', { x: 596, y: 6, width: 8, height: 22 }), s('rect', { x: 60, y: 264, width: 1080, height: 22 })), ...cols(130, 390, 222, 38, 20), ...cols(810, 1070, 222, 38, 20), ...cols(420, 780, 194, 64, 24), ...cols(524, 676, 118, 36, 12), s('rect', { x: 0, y: 284, width: 1200, height: 40, fill: '#05080e' }));
}
/** Each game's emblem: a dome, a ballot box, the seal's star, the crisis room's scope. */
function emblem(mode) {
    const stroke = { fill: 'none', stroke: 'currentColor', 'stroke-width': 2.2, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' };
    const parts = mode === 'FULL_GAME'
        ? [s('path', { d: 'M12 40h40M16 40V30h32v10M20 30v-6h24v6M24 24c0-8 16-8 16 0M32 10v6', ...stroke }), s('path', { d: 'M18 46h28', ...stroke })]
        : mode === 'CAMPAIGN_ONLY'
            ? [s('rect', { x: 12, y: 26, width: 40, height: 22, rx: 2, ...stroke }), s('path', { d: 'M22 26v-4h20v4M26 30h12', ...stroke }), s('path', { d: 'M28 22l4-12 8 2-3 10', ...stroke })]
            : mode === 'GOVERNING_ONLY'
                ? [s('circle', { cx: 32, cy: 30, r: 18, ...stroke }), s('path', { d: 'M32 18l3.5 7.5 8 .8-6 5.3 1.8 7.9L32 35.4 24.7 39.5l1.8-7.9-6-5.3 8-.8z', ...stroke })]
                : [s('circle', { cx: 32, cy: 30, r: 18, ...stroke }), s('circle', { cx: 32, cy: 30, r: 9, ...stroke }), s('path', { d: 'M32 8v10M32 42v10M10 30h10M44 30h10', ...stroke }),
                    s('path', { d: 'M32 30l9-7', ...stroke })];
    return s('svg', { class: 'front-emblem', viewBox: '0 0 64 58', 'aria-hidden': 'true' }, ...parts);
}
/** A party's emblem: its letter in a ring of its colour. */
function partyMark(p) {
    return h('span', { class: 'front-party-mark ' + (p === 'DEMOCRAT' ? 'dem' : 'rep'), 'aria-hidden': 'true' }, p === 'DEMOCRAT' ? 'D' : 'R');
}
/** The data attributes that name a chosen card, so the focus can stay on it when the screen is drawn again. */
const KEYS = ['mode', 'party', 'cast', 'id', 'scenario', 'difficulty', 'crisis', 'preset'];
// ---- the screens -------------------------------------------------------------------------------------------------------
export function startScreen(act) {
    let screen = 'title';
    let stepIndex = 0;
    let mode = 'FULL_GAME';
    let party = 'DEMOCRAT';
    let real = true;
    let player = '', opponent = '', scenario = 'TOSS_UP', difficulty = 'STANDARD';
    let crisis = 'taiwan', preset = 0;
    let icYear = 2030, icSide = 'US';
    let options = null;
    const root = h('main', { class: 'front start', id: 'main' });
    const opts = () => (options ??= act.options(real));
    const steps = () => mode === 'CRISIS' ? ['game', 'crisis', 'preset', 'ready'] : ['game', 'side', 'candidate', 'opponent', 'country', 'ready'];
    const step = () => steps()[Math.min(stepIndex, steps().length - 1)];
    const governing = () => mode === 'GOVERNING_ONLY';
    const mine = () => (party === 'DEMOCRAT' ? opts().democrats : opts().republicans);
    const theirs = () => (party === 'DEMOCRAT' ? opts().republicans : opts().democrats);
    const find = (list, id) => list.find(c => c.id === id);
    /** Keeps the chosen candidates valid for the side and cast: the first of each list until the player picks. */
    const settle = () => {
        if (!find(mine(), player))
            player = mine()[0]?.id ?? '';
        if (!find(theirs(), opponent))
            opponent = theirs()[0]?.id ?? '';
    };
    const stepLabel = (x) => ({
        game: 'The game', side: 'Your side', candidate: governing() ? 'The president' : 'Your candidate', opponent: governing() ? 'The defeated' : 'Your opponent',
        country: 'The country', crisis: 'The crisis', preset: 'How it starts', ready: 'Ready',
    })[x];
    function go(to, index = 0) {
        screen = to;
        stepIndex = index;
        draw(true);
    }
    function next() {
        if (screen !== 'setup')
            return;
        if (step() === 'ready') {
            begin();
            return;
        }
        if (!valid())
            return;
        stepIndex++;
        draw(true);
    }
    function back() {
        if (screen === 'setup' && stepIndex > 0) {
            stepIndex--;
            draw(true);
        }
        else
            go('title');
    }
    function valid() {
        if (step() === 'candidate')
            return !!find(mine(), player);
        if (step() === 'opponent')
            return !!find(theirs(), opponent);
        return true;
    }
    function begin() {
        if (mode === 'CRISIS') {
            act.quick('saves/governing.json', { type: 'IndividualCrisis', crisis, preset, seed: 1 + Math.floor(Math.random() * 1e9) });
            return;
        }
        act.newGame({ seed: Math.floor(Math.random() * 1e9), party, player, opponent, scenario, difficulty, realRoster: real, mode });
    }
    // a card that is chosen with a click, or chosen and carried forward with a double click (as a game's menus do)
    const choice = (cls, on, data, pick, ...children) => h('button', { class: cls + (on ? ' on' : ''), type: 'button', role: 'radio', 'aria-checked': String(on), ...data,
        onclick: () => { pick(); draw(false); }, ondblclick: () => { pick(); next(); } }, ...children);
    // ---- the title screen ----
    function title() {
        const menu = h('nav', { class: 'front-menu', 'aria-label': 'Main menu' }, h('button', { class: 'front-item primary', type: 'button', 'data-action': 'new', onclick: () => go('setup') }, h('span', { class: 'front-item-label' }, 'New Game'), h('span', { class: 'front-item-hint' }, 'The campaign, the presidency, both, or one crisis')), h('label', { class: 'front-item', 'data-action': 'continue', tabindex: '0',
            onkeydown: (e) => { const k = e.key; if (k === 'Enter' || k === ' ') {
                e.preventDefault();
                e.currentTarget.querySelector('input')?.click();
            } } }, h('span', { class: 'front-item-label' }, 'Continue'), h('span', { class: 'front-item-hint' }, 'Open a game you saved'), h('input', { type: 'file', accept: '.json,application/json', class: 'sr', tabindex: '-1', 'aria-label': 'Open a saved game', onchange: async (e) => {
                const f = e.target.files?.[0];
                if (f)
                    act.loadFile(await f.text());
            } })), h('button', { class: 'front-item', type: 'button', 'data-action': 'moments', onclick: () => go('moments') }, h('span', { class: 'front-item-label' }, 'Start at a moment'), h('span', { class: 'front-item-hint' }, 'Jump into the Full Game at a turning point')));
        menu.addEventListener('keydown', e => {
            if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp')
                return;
            const items = [...menu.querySelectorAll('.front-item')];
            const i = items.indexOf(document.activeElement);
            e.preventDefault();
            items[(i + (e.key === 'ArrowDown' ? 1 : items.length - 1)) % items.length]?.focus();
        });
        return h('section', { class: 'front-title', 'aria-labelledby': 'front-name' }, h('div', { class: 'front-sky', 'aria-hidden': 'true' }), capitol(), h('div', { class: 'front-title-body' }, h('p', { class: 'front-eyebrow' }, '2028 · 2032 · 2036'), h('h1', { class: 'front-name', id: 'front-name' }, h('span', null, 'American'), h('span', null, 'Grand Strategy')), h('p', { class: 'front-tagline' }, 'Win the White House. Govern it. Face what history makes of you.'), menu), h('footer', { class: 'front-foot' }, buildInfo.build ? h('span', null, `Build ${buildInfo.build}` + (buildInfo.commit ? ` · source ${buildInfo.commit}${buildInfo.dirty ? ' (with uncommitted changes)' : ''}` : '')
            + (buildInfo.built ? ` · built ${buildInfo.built}` : '')) : null, ...(act.links ?? []).map(l => h('a', { href: l.href }, l.label))));
    }
    // ---- the moments ----
    function moments() {
        return h('section', { class: 'front-page', 'aria-labelledby': 'front-moments' }, bar(null), h('div', { class: 'front-body' }, h('h2', { class: 'front-heading', id: 'front-moments', tabindex: '-1' }, 'Start at a moment'), h('p', { class: 'front-lede' }, 'Each is a Full Game saved at a turning point, with the real people of the sample presidency.'), h('ul', { class: 'front-moments' }, ...MOMENTS.map(m => h('li', null, h('button', { class: 'front-card moment-card', type: 'button', 'data-save': m.save, onclick: () => act.quick(m.save) }, h('span', { class: 'front-card-title' }, m.label), h('span', { class: 'front-card-detail' }, m.detail), h('span', { class: 'front-card-go', 'aria-hidden': 'true' }, 'Play ›')))))), h('div', { class: 'front-actions' }, h('button', { class: 'btn front-back', type: 'button', onclick: () => go('title') }, '‹ Back')));
    }
    // ---- the setup ----
    /** The top bar: the game's mark, and in the setup the steps so far. */
    function bar(tracker) {
        return h('header', { class: 'front-bar' }, h('button', { class: 'front-mark', type: 'button', onclick: () => go('title'), 'aria-label': 'American Grand Strategy: back to the title screen' }, 'American Grand Strategy'), tracker);
    }
    function tracker() {
        const all = steps();
        return h('ol', { class: 'front-steps', 'aria-label': 'Setup steps' }, ...all.map((x, i) => h('li', { class: i === stepIndex ? 'now' : i < stepIndex ? 'done' : '', 'aria-current': i === stepIndex ? 'step' : null }, h('span', { class: 'front-step-n', 'aria-hidden': 'true' }, String(i + 1)), h('span', { class: 'front-step-label' }, stepLabel(x)))));
    }
    function setup() {
        const x = step();
        const body = x === 'game' ? gameStep() : x === 'side' ? sideStep() : x === 'candidate' ? candidateStep(true) : x === 'opponent' ? candidateStep(false)
            : x === 'country' ? countryStep() : x === 'crisis' ? crisisStep() : x === 'preset' ? presetStep() : readyStep();
        const last = x === 'ready';
        const beginLabel = mode === 'CRISIS' ? 'Begin the crisis' : governing() ? 'Begin on election night' : 'Begin the campaign';
        return h('section', { class: 'front-page setup-' + x, 'aria-labelledby': 'front-step-title' }, bar(tracker()), h('div', { class: 'front-body' }, ...body), h('div', { class: 'front-actions' }, h('button', { class: 'btn front-back setup-back', type: 'button', onclick: back }, stepIndex === 0 ? '‹ Title' : '‹ Back'), h('span', { class: 'front-actions-note' }, `Step ${stepIndex + 1} of ${steps().length}`), h('button', { class: 'btn primary ' + (last ? 'setup-begin' : 'setup-next'), type: 'button', disabled: !valid(), onclick: next }, last ? beginLabel : 'Next ›')));
    }
    const heading = (title, lede) => [
        h('h2', { class: 'front-heading setup-title', id: 'front-step-title', tabindex: '-1' }, title),
        h('p', { class: 'front-lede' }, lede)
    ];
    function gameStep() {
        return [...heading('Choose your game', 'Four ways in, one engine: the same campaign, the same presidency, the same world.'),
            h('div', { class: 'front-grid modes', role: 'radiogroup', 'aria-label': 'Which game' }, ...MODES.map(m => choice('front-card mode-card start-mode', m.id === mode, { 'data-mode': m.id }, () => { mode = m.id; }, h('span', { class: 'mode-emblem' }, emblem(m.id)), h('strong', { class: 'front-card-title' }, m.label), h('span', { class: 'front-card-tag' }, m.tag), h('span', { class: 'front-card-detail' }, m.detail), h('span', { class: 'front-card-time' }, 'About ' + m.time))))];
    }
    function sideStep() {
        const sideCard = (p) => choice('front-card party-card ' + (p === 'DEMOCRAT' ? 'dem' : 'rep'), p === party, { 'data-party': p }, () => { party = p; settle(); }, partyMark(p), h('span', { class: 'front-card-title' }, p === 'DEMOCRAT' ? 'Democrat' : 'Republican'), h('span', { class: 'front-card-detail' }, governing()
            ? (p === 'DEMOCRAT' ? 'A Democratic administration takes office in January 2029.' : 'A Republican administration takes office in January 2029.')
            : (p === 'DEMOCRAT' ? 'Win back the White House for the Democrats.' : 'Keep the White House for the Republicans.')));
        const cast = (on, label, detail) => choice('front-card cast-card', real === on, { 'data-cast': on ? 'real' : 'fictional' }, () => { if (real !== on) {
            real = on;
            options = null;
            player = '';
            opponent = '';
            settle();
        } }, h('span', { class: 'front-card-title' }, label), h('span', { class: 'front-card-detail' }, detail));
        return [...heading(governing() ? 'The party in power' : 'Choose your side', governing()
                ? 'Which party won the White House in 2028, and the world it governs.' : 'Your party, and the world you play in.'),
            h('div', { class: 'front-grid parties', role: 'radiogroup', 'aria-label': 'Your party' }, sideCard('DEMOCRAT'), sideCard('REPUBLICAN')),
            h('h3', { class: 'front-sub' }, 'The cast'),
            h('div', { class: 'front-grid casts', role: 'radiogroup', 'aria-label': 'The cast' }, cast(true, 'Real people', 'The 2028 field: the real candidates, senators, congressional leaders, cabinet prospects, justices and leaders abroad. Fictional newcomers from 2030.'), cast(false, 'Fictional cast', 'An invented field and world, throughout the game.'))];
    }
    function candidateStep(own) {
        settle();
        const list = own ? mine() : theirs();
        const chosenId = own ? player : opponent;
        const chosen = find(list, chosenId) ?? list[0];
        const side = own ? party : party === 'DEMOCRAT' ? 'REPUBLICAN' : 'DEMOCRAT';
        const code = side === 'DEMOCRAT' ? 'D' : 'R';
        const title = own ? (governing() ? 'The president-elect' : 'Choose your candidate') : (governing() ? 'The defeated candidate' : 'Choose your opponent');
        const lede = own ? (governing() ? 'Who won the White House in 2028.' : 'Who you will be, and what they bring to the race.')
            : (governing() ? 'Who lost in 2028: the other party’s leader as your presidency begins.' : 'Who you will face. Their strengths are the race’s.');
        return [...heading(title, lede),
            h('div', { class: 'front-cands' }, h('div', { class: 'front-grid cands', role: 'radiogroup', 'aria-label': title }, ...list.map(c => choice('front-card cand-card', c.id === chosenId, { 'data-id': c.id }, () => { if (own)
                player = c.id;
            else
                opponent = c.id; }, portrait(c.id, code, 56, true, c.look), h('span', { class: 'cand-text' }, h('span', { class: 'front-card-title' }, c.name), h('span', { class: 'front-card-detail' }, (STATES[c.home] ?? c.home) + (c.real ? ' · real' : '')))))), chosen ? dossier(chosen, code) : null)];
    }
    /** The chosen candidate's file: the portrait, where they stand, and their five ratings out of ten. */
    function dossier(c, code) {
        return h('aside', { class: 'front-dossier ' + (code === 'D' ? 'dem' : 'rep'), 'aria-label': c.name + ': the file' }, portrait(c.id, code, 128, true, c.look), h('div', { class: 'dossier-name' }, c.name), h('div', { class: 'dossier-meta' }, `${code === 'D' ? 'Democrat' : 'Republican'} · ${STATES[c.home] ?? c.home} · ${leaning(c.ideology)}`), h('dl', { class: 'dossier-stats' }, ...STATS.flatMap(st => [
            h('dt', null, st.label),
            h('dd', null, h('span', { class: 'stat-bar', role: 'meter', 'aria-valuemin': '0', 'aria-valuemax': '10', 'aria-valuenow': String(c[st.key]), 'aria-label': `${st.label}: ${c[st.key]} of 10` }, h('span', { style: `width:${c[st.key] * 10}%` })), h('span', { class: 'stat-value' }, String(c[st.key])))
        ])), c.real ? h('p', { class: 'dossier-note' }, 'A real politician. The ratings are the game’s judgement for play, not facts.') : null);
    }
    function countryStep() {
        const o = opts();
        return [...heading('The country', 'The national mood the race starts in, and how hard the other side fights.'),
            h('div', { class: 'front-grid moods', role: 'radiogroup', 'aria-label': 'The country' }, ...o.scenarios.map(x => choice('front-card mood-card', x.id === scenario, { 'data-scenario': x.id }, () => { scenario = x.id; }, h('span', { class: 'front-card-title' }, x.label), h('span', { class: 'front-card-detail' }, x.description)))),
            h('h3', { class: 'front-sub' }, governing() ? 'The other party’s campaigns (2032 and after)' : 'Your opponent’s campaign'),
            h('div', { class: 'front-grid levels', role: 'radiogroup', 'aria-label': 'Difficulty' }, ...o.difficulties.map(x => choice('front-card level-card', x.id === difficulty, { 'data-difficulty': x.id }, () => { difficulty = x.id; }, h('span', { class: 'front-card-title' }, x.label), h('span', { class: 'front-card-detail' }, x.description))))];
    }
    function crisisStep() {
        return [...heading('Choose the crisis', 'It begins at once in the sample presidency of September 2029, and the game ends when it does.'),
            h('div', { class: 'front-grid crises', role: 'radiogroup', 'aria-label': 'The crisis' }, ...CRISES.map(c => choice('front-card crisis-card ' + c.id, c.id === crisis, { 'data-crisis': c.id }, () => { crisis = c.id; }, h('span', { class: 'crisis-map', 'aria-hidden': 'true' }, c.id === 'taiwan' ? 'Taiwan Strait' : 'Strait of Hormuz'), h('span', { class: 'front-card-title' }, c.label), h('span', { class: 'front-card-detail' }, c.detail)))),
            islandChain()];
    }
    /** The Taiwan war game on its own, without a presidency: offered beside the crises, not as a fifth game. */
    function islandChain() {
        return h('section', { class: 'front-ic ic-panel start-ic', 'aria-labelledby': 'front-ic' }, h('h3', { class: 'front-sub', id: 'front-ic' }, 'Or the war game alone: Island Chain'), h('p', { class: 'front-card-detail' }, 'The Taiwan Strait without a presidency around it, as America or as China. Not saved.'), h('div', { class: 'front-ic-row' }, h('label', { class: 'front-ic-year' }, 'Year ', h('select', { 'aria-label': 'Island Chain: the year', onchange: (e) => { icYear = Number(e.target.value); } }, ...[2029, 2030, 2031, 2032, 2033, 2034, 2035, 2036].map(y => h('option', { value: String(y), selected: y === icYear ? 'selected' : null }, String(y))))), h('span', { class: 'front-toggle', role: 'group', 'aria-label': 'Island Chain: your side' }, ...['US', 'PRC'].map(sd => h('button', { class: 'btn small toggle' + (sd === icSide ? ' on' : ''), type: 'button', 'aria-pressed': String(sd === icSide), onclick: () => { icSide = sd; draw(false); } }, sd === 'US' ? 'America' : 'China'))), h('span', { class: 'front-toggle', role: 'group', 'aria-label': 'Island Chain: how it starts' }, ...PRESETS.map((p, i) => h('button', { class: 'btn small', type: 'button', onclick: () => act.islandChain({ year: icYear, preset: i, side: icSide }) }, p.label)))));
    }
    function presetStep() {
        return [...heading('How it starts', CRISES.find(c => c.id === crisis).label + ': the intelligence picture on the first day.'),
            h('div', { class: 'front-grid presets', role: 'radiogroup', 'aria-label': 'How it starts' }, ...PRESETS.map((p, i) => choice('front-card preset-card', i === preset, { 'data-preset': String(i) }, () => { preset = i; }, h('span', { class: 'front-card-title' }, p.label), h('span', { class: 'front-card-detail' }, p.detail))))];
    }
    const summary = (rows) => h('dl', { class: 'front-summary' }, ...rows.map(([k, v]) => h('div', { class: 'summary-item' }, h('dt', null, k), h('dd', null, v))));
    function readyStep() {
        const m = MODES.find(x => x.id === mode);
        if (mode === 'CRISIS') {
            const c = CRISES.find(x => x.id === crisis);
            return [...heading('Ready', 'The Situation Room is waiting.'),
                h('div', { class: 'front-versus crisis' }, h('div', { class: 'versus-crisis ' + crisis }, h('span', { class: 'crisis-map', 'aria-hidden': 'true' }, crisis === 'taiwan' ? 'Taiwan Strait' : 'Strait of Hormuz'), h('span', { class: 'versus-name' }, c.label), h('span', { class: 'versus-meta' }, PRESETS[preset].label + ' · September 2029'))),
                summary([['Game', m.label], ['Crisis', c.label], ['How it starts', PRESETS[preset].label], ['The presidency', 'The sample administration (real people)']]),
                h('p', { class: 'front-note' }, 'Congress, the economy, the press and the allies go on around the crisis. It ends won, lost or ended in part, with an after-action report.')];
        }
        const me = find(mine(), player), them = find(theirs(), opponent);
        const code = party === 'DEMOCRAT' ? 'D' : 'R', other = code === 'D' ? 'R' : 'D';
        const o = opts();
        const side = (c, k, role) => h('div', { class: 'versus-side ' + (k === 'D' ? 'dem' : 'rep') }, portrait(c.id, k, 160, true, c.look), h('span', { class: 'versus-role' }, role), h('span', { class: 'versus-name' }, c.name), h('span', { class: 'versus-meta' }, `${k === 'D' ? 'Democrat' : 'Republican'} · ${STATES[c.home] ?? c.home}`));
        return [...heading(governing() ? 'Election night, November 7, 2028' : 'The 2028 race', governing()
                ? 'The staff play the campaign for both sides; the result is computed once, and election night shows it before the transition.'
                : 'Your campaign starts in July, in the war room.'),
            h('div', { class: 'front-versus' }, side(me, code, governing() ? 'The president-elect' : 'You'), h('span', { class: 'versus-vs', 'aria-hidden': 'true' }, 'VS'), side(them, other, governing() ? 'Defeated' : 'Your opponent')),
            summary([['Game', m.label], ['The country', o.scenarios.find(x => x.id === scenario)?.label ?? scenario],
                [governing() ? 'The other party' : 'Your opponent’s campaign', o.difficulties.find(x => x.id === difficulty)?.label ?? difficulty],
                ['The cast', real ? 'Real people' : 'Fictional']])];
    }
    // ---- drawing and the keyboard ----
    function draw(moved) {
        // a choice redraws the screen: the focus stays on the card just chosen
        const active = document.activeElement;
        const key = !moved && active && root.contains(active) ? KEYS.find(k => active.dataset[k] !== undefined) : undefined;
        const keep = key ? `[data-${key}="${active.dataset[key]}"]` : null;
        const view = screen === 'title' ? title() : screen === 'moments' ? moments() : setup();
        if (moved)
            view.classList.add('front-enter');
        root.replaceChildren(view);
        root.dataset['screen'] = screen;
        if (moved) {
            const target = screen === 'title' ? root.querySelector('.front-item') : root.querySelector('.front-heading');
            target?.focus();
            window.scrollTo(0, 0);
        }
        else if (keep)
            root.querySelector(keep)?.focus();
    }
    root.addEventListener('keydown', e => {
        if (screen === 'title')
            return;
        if (e.key === 'Escape') {
            e.preventDefault();
            back();
            return;
        }
        const t = e.target;
        if (e.key === 'Enter' && screen === 'setup' && !(t instanceof HTMLButtonElement) && !(t instanceof HTMLSelectElement) && !(t instanceof HTMLInputElement)) {
            e.preventDefault();
            next();
        }
    });
    settle();
    draw(false);
    return root;
}
