// The title screen and the setup: character creation first (your leader, as a printed character card), then the
// party's coalition, then the difficulty. The board lies on the table behind the title, with an opening map on it.
import { el, $, esc, icon, grade, toast } from './kit';
import { portrait } from '../art/print';
import { Settings, settingsCard, setSound } from './settings';
import { SAVE_KEY, SLOTS_KEY, store } from './game';

export interface Setup { leader: string; first?: string; last?: string; state?: string; party: string; factions: string[]; difficulty: string; seed: number; ironman: boolean; colour: string }
const COLOURS = [['Navy', '#2f5d8c', '#a3342b'], ['Brick', '#a3342b', '#2f5d8c'], ['Forest', '#3f6b3a', '#8a3a6b'], ['Ochre', '#b07c1e', '#4a3a8a'], ['Plum', '#6b3a7a', '#b07c1e'], ['Teal', '#1f6f73', '#a3342b']];
const S: Setup & { step: number; custom: Record<string, string>; touched?: boolean } = { step: 0, leader: 'governor', party: 'Union', factions: ['labor', 'suburbs', 'justice'], difficulty: 'operative', seed: 0, ironman: false, colour: 'Navy', custom: {} };
export const colourPair = (name: string) => { const c = COLOURS.find(x => x[0] === name) || COLOURS[0]; return [c[1], c[2]]; };

export function titleScreen(root: HTMLElement, data: any, boardEl: HTMLElement, onStart: (s: Setup) => void, onLoad: (save: any) => void) {
  root.innerHTML = '';
  const auto = store.get(SAVE_KEY, null), slots = store.get(SLOTS_KEY, []);
  const node = el(`<main class="title"><div class="title-board" id="tboard"></div><div class="lamp"></div>
    <section class="title-card card paper"><span class="sc">A board game of American power · vertical slice</span><h1>American Grand Strategy</h1>
      <p class="sub">Lead a party for half a century. Build a coalition out of rival factions, take the map election by election, and govern what you win.</p>
      <nav class="menu" aria-label="Main menu">
        <button class="btn red" type="button" data-go="new">New game <small style="color:#f3d9cf">The Long Game · 2000</small></button>
        <button class="btn" type="button" data-go="continue" ${auto ? '' : 'disabled'}>Continue <small>${auto ? esc(auto.label) : 'No game in progress'}</small></button>
        <button class="btn" type="button" data-go="load" ${slots.length ? '' : 'disabled'}>Load a saved game <small>${slots.length} saved</small></button>
        <button class="btn" type="button" data-go="hall">Hall of Presidents <small>Legacy</small></button>
        <button class="btn" type="button" data-go="settings">Settings <small>Sound, colour, text</small></button>
        <button class="btn" type="button" disabled>One Term <small>2028 · after the slice</small></button>
      </nav>
      <p class="note">A playable vertical slice: Q1 2000 to the end of 2001. The simulation runs in your browser (Rust compiled to WebAssembly); it saves on this device only.</p></section></main>`);
  root.append(node); $('#tboard', node).append(boardEl); boardEl.style.position = 'absolute'; boardEl.style.inset = '0'; window.dispatchEvent(new Event('resize'));
  node.onclick = e => {
    const b = (e.target as HTMLElement).closest('[data-go]') as HTMLButtonElement | null; if (!b || b.disabled) return; const go = b.dataset.go;
    // a first campaign starts on the forgiving level; once you have a win in the Hall, the fair fight
    if (go === 'new') { if (!S.touched) S.difficulty = store.get('ags-hall', []).length ? 'operative' : 'staffer'; setupScreen(root, data, onStart, () => titleScreen(root, data, boardEl, onStart, onLoad)); }
    if (go === 'continue') onLoad(auto);
    if (go === 'load') loadCard(slots, onLoad);
    if (go === 'hall') hallCard();
    if (go === 'settings') settingsCard();
  };
}
function loadCard(slots: any[], onLoad: (s: any) => void) {
  const node = el(`<div class="ecard card paper" style="width:min(520px,100%)"><div class="card-in"><div class="ebody"><div class="kicker">Saved games</div><h2>Load a game</h2>
    <div class="menu">${slots.map((s, i) => `<button class="btn" type="button" data-i="${i}">${esc(s.save.leader)} <small>${esc(s.save.label)} · ${new Date(s.when).toLocaleString()}</small></button>`).join('')}</div>
    <div class="efoot"><span></span><button class="btn" type="button" data-x>Cancel</button></div></div></div></div>`);
  const s = document.createElement('div'); s.className = 'scrim'; s.append(node); document.body.append(s);
  node.onclick = e => { const b = (e.target as HTMLElement).closest('[data-i]') as HTMLElement | null; if (b) { s.remove(); onLoad(slots[+b.dataset.i!].save); } if ((e.target as HTMLElement).closest('[data-x]')) s.remove(); };
}
function hallCard() {
  const hall = store.get('ags-hall', []);
  const node = el(`<div class="ecard card paper wide"><div class="card-in"><div class="ebody"><div class="kicker">Legacy</div><h2>Hall of Presidents</h2><p class="story">Every president you play hangs here, ranked by their legacy score.</p>
    <div class="talents">${hall.length ? hall.sort((a: any, b: any) => b.legacy - a.legacy).map((h: any) => `<div class="tal"><div style="max-width:120px;margin:0 auto">${portrait('lead-' + h.id, { spot: Settings.you, pres: h.pres, age: h.age, title: h.name })}</div><b style="font:400 18px var(--f-title);text-align:center">${esc(h.name)}</b><span class="muted" style="text-align:center">${esc(h.party)} Party · ${esc(h.when)} · legacy ${h.legacy}</span></div>`).join('') : '<p class="muted">The walls are bare. Win an election to hang the first portrait.</p>'}</div>
    <div class="efoot"><span></span><button class="btn red" type="button">Close</button></div></div></div></div>`);
  const s = document.createElement('div'); s.className = 'scrim'; s.append(node); document.body.append(s); $('.btn', node).onclick = () => s.remove();
}

function setupScreen(root: HTMLElement, data: any, onStart: (s: Setup) => void, back: () => void) {
  const L0 = data.leaders.find((l: any) => l.id === S.leader), L = { ...L0, ...Object.fromEntries(Object.entries(S.custom).filter(([, v]) => v)) };
  const [you] = colourPair(S.colour), steps = ['Your leader', 'Your party', 'The table'];
  let body = '';
  if (S.step === 0) body = `<div class="leaders" role="radiogroup" aria-label="Leaders">${data.leaders.map((l: any, i: number) => `<button class="lcard card paper ${l.id === S.leader ? 'on' : ''}" type="button" role="radio" aria-checked="${l.id === S.leader}" data-leader="${l.id}" style="--rot:${(i % 3 - 1) * 1.2}deg"><div class="card-in">
      <div class="pf">${portrait('lead-' + l.id + (l.id === S.leader ? (S.custom.first || l.first) : l.first), { spot: you, pres: l.pres, age: l.age, title: l.first + ' ' + l.last })}</div><b>${esc(l.id === S.leader && S.custom.first ? S.custom.first : l.first)} ${esc(l.id === S.leader && S.custom.last ? S.custom.last : l.last)}</b><span>${esc(l.background)} · ${esc(l.state)}</span></div></button>`).join('')}</div>
    <section class="dossier paper"><div class="dossier-grid"><div>${portrait('lead-' + L.id + L.first, { spot: you, pres: L.pres, age: L.age, title: L.first + ' ' + L.last })}</div>
      <div style="display:grid;gap:14px;min-width:0"><div><span class="sc">${esc(L0.background)} · ${esc(data.states.find((s: any) => s.code === L.state)?.name)}</span><h2 style="font-size:38px">${esc(L.first)} ${esc(L.last)}</h2></div>
      <div class="uniq"><div><span class="sc">Unique ability</span><h4>${esc(L0.ability.name)}</h4><p>${esc(L0.ability.text)}</p></div><div><span class="sc">Unique unit</span><h4>${esc(L0.unit.name)}</h4><p>${esc(L0.unit.text)}</p></div></div>
      <div class="chips">${[['Charisma', 'cha'], ['Policy', 'pol'], ['Deals', 'deal'], ['Integrity', 'integ']].map(([n, k]) => `<span class="chip">${n} <b class="${grade(L0.ratings[k])}">${L0.ratings[k]}</b></span>`).join('')}<span class="chip">Ratings run 20–80; 50 is an average national politician</span></div>
      <div class="row"><div class="field"><label for="c-first">First name</label><input class="input" id="c-first" value="${esc(L.first)}" maxlength="16" style="width:150px"></div><div class="field"><label for="c-last">Surname</label><input class="input" id="c-last" value="${esc(L.last)}" maxlength="18" style="width:170px"></div>
        <div class="field"><label for="c-state">Home state</label><select class="input" id="c-state">${[...data.states].filter((s: any) => s.code !== 'DC').sort((a: any, b: any) => a.name.localeCompare(b.name)).map((s: any) => `<option value="${s.code}" ${s.code === L.state ? 'selected' : ''}>${esc(s.name)}</option>`).join('')}</select></div></div></div></div></section>`;
  else if (S.step === 1) body = `<section class="dossier paper"><div class="row"><div class="field"><label for="c-party">Party name</label><select class="input" id="c-party">${data.names.party_names.map((n: string) => `<option ${n === S.party ? 'selected' : ''}>${esc(n)}</option>`).join('')}</select></div>
      <div class="field"><span class="lab">Colours (yours, then your rival's)</span><div class="row" style="gap:8px">${COLOURS.map(([n, a, b]) => `<button type="button" class="swatch ${n === S.colour ? 'on' : ''}" data-col="${n}" aria-label="${n}" style="background:linear-gradient(135deg, ${a} 60%, ${b} 60%)"></button>`).join('')}</div></div></div>
      <div class="field"><span class="lab">Founding factions · ${S.factions.length} of 3. They start as your allies. The three least like them start with your rival; three are up for grabs, and can split off as third parties if both of you ignore them.</span>
      <div class="facgrid">${data.factions.map((f: any) => `<button type="button" class="fcard ${S.factions.includes(f.id) ? 'on' : ''}" data-fac="${f.id}" aria-pressed="${S.factions.includes(f.id)}"><div class="inner"><b><span style="color:${S.factions.includes(f.id) ? you : 'var(--ink-3)'}">${icon(f.icon)}</span>${esc(f.name)}</b><p>${esc(f.who)}</p><span class="chip" style="justify-self:start">As allies: ${esc(f.bonus.text)}</span></div></button>`).join('')}</div></div></section>`;
  else body = `<section class="dossier paper"><div class="field"><span class="lab">Difficulty</span><div class="diffs">${data.difficulty.map((d: any) => `<button type="button" class="${d.id === S.difficulty ? 'on' : ''}" data-diff="${d.id}"><b>${esc(d.name)}</b><span>${esc(d.note)}</span></button>`).join('')}</div></div>
      <div class="row" style="gap:22px"><label class="check"><input type="checkbox" id="c-iron" ${S.ironman ? 'checked' : ''}> Ironman: one save, kept automatically</label><label class="check"><input type="checkbox" id="c-ranges" ${Settings.ranges ? 'checked' : ''}> Show forecast ranges</label></div>
      <div class="field"><label for="c-seed">Seed (the same seed, the same game; share it as a challenge)</label><div class="row"><input class="input" id="c-seed" value="${S.seed || ''}" placeholder="Random" style="width:180px"><button class="btn sm" type="button" data-weekly>This week's challenge seed</button></div></div>
      <p class="story" style="font-size:15px;margin:0">You start in January 2000 with one HQ in your home state's biggest city, your candidate and four pieces. Real history arrives on schedule; you can change it.</p></section>`;
  root.innerHTML = '';
  const node = el(`<main class="setup"><div class="setup-in"><div class="setup-head"><div><span class="sc" style="color:var(--manila)">New game · The Long Game</span><h1>${['Choose your leader', 'Found your party', 'Set the table'][S.step]}</h1></div>
    <div class="steps">${steps.map((s, i) => `<span class="${i === S.step ? 'on' : ''}">${i + 1}. ${s}</span>`).join('')}</div></div>${body}
    <div class="foot"><button class="btn" type="button" data-back>${S.step ? 'Back' : 'Title'}</button><button class="btn red" type="button" data-next ${S.step === 1 && S.factions.length !== 3 ? 'disabled' : ''}>${S.step < 2 ? 'Next' : 'Begin in 2000'}</button></div></div></main>`);
  root.append(node);
  const redraw = () => setupScreen(root, data, onStart, back);
  node.onclick = e => {
    const t = e.target as HTMLElement, l = t.closest('[data-leader]') as HTMLElement | null, c = t.closest('[data-col]') as HTMLElement | null, f = t.closest('[data-fac]') as HTMLElement | null, d = t.closest('[data-diff]') as HTMLElement | null;
    if (l) { if (S.leader !== l.dataset.leader) S.custom = {}; S.leader = l.dataset.leader!; return redraw(); }
    if (c) { S.colour = c.dataset.col!; return redraw(); }
    if (f) { const id = f.dataset.fac!; S.factions = S.factions.includes(id) ? S.factions.filter(x => x !== id) : S.factions.length < 3 ? [...S.factions, id] : S.factions; return redraw(); }
    if (d) { S.difficulty = d.dataset.diff!; S.touched = true; return redraw(); }
    if (t.closest('[data-weekly]')) { const wk = Math.floor(Date.now() / (7 * 864e5)); S.seed = 100000 + (wk * 7919) % 900000; toast('trophy', 'Weekly seed', `Seed ${S.seed}: everyone playing it this week gets the same game.`); return redraw(); }
    if (t.closest('[data-back]')) { if (S.step) { S.step--; redraw(); } else back(); return; }
    if (t.closest('[data-next]') && !(S.step === 1 && S.factions.length !== 3)) {
      if (S.step < 2) { S.step++; redraw(); node.scrollTop = 0; return; }
      const seed = S.seed || Math.floor(Math.random() * 1e9); S.step = 0;
      onStart({ leader: S.leader, first: S.custom.first, last: S.custom.last, state: S.custom.state, party: S.party, factions: S.factions, difficulty: S.difficulty, seed, ironman: S.ironman, colour: S.colour });
    }
  };
  const on = (id: string, fn: (v: string, el: HTMLInputElement) => void) => { const x = $('#' + id, node) as HTMLInputElement | null; if (x) x.addEventListener('change', () => fn(x.value, x)); };
  on('c-first', v => { S.custom.first = v.trim(); redraw(); }); on('c-last', v => { S.custom.last = v.trim(); redraw(); }); on('c-state', v => { S.custom.state = v; redraw(); });
  on('c-party', v => { S.party = v; }); on('c-iron', (_, x) => { S.ironman = x.checked; }); on('c-ranges', (_, x) => { Settings.ranges = x.checked; });
  on('c-seed', v => { S.seed = Math.abs(parseInt(v, 10)) || 0; });
}
export { setSound };
