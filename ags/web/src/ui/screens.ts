// The binder: full-screen folders for the research tree, doctrines, factions, the platform, the world, victory and the century.
import { el, $, esc, icon, sgn, forecastCard, toast } from './kit';
import { portrait } from '../art/print';
import { Settings, sound } from './settings';
import type { GameUI } from './game';

let open: HTMLElement | null = null;
export function closeBinder() { open?.remove(); open = null; }
const head = (tab: string, title: string, sub: string) => `<div class="folder-head"><div><h1>${title}</h1><div class="sub">${sub}</div></div><button class="btn" type="button" data-close>Back to the table</button></div>`;

export function openBinder(k: string, ui: GameUI) {
  closeBinder();
  open = el(`<div class="binder" role="dialog" aria-modal="true" aria-label="${k}"><div class="binder-in"><section class="folder" data-tab=""></section></div></div>`);
  document.body.append(open);
  const folder = $('.folder', open);
  const draw = () => { const r = RENDER[k](ui); folder.dataset.tab = r.tab; folder.innerHTML = r.html; };
  draw();
  open.onclick = async e => {
    const t = e.target as HTMLElement;
    if (t.closest('[data-close]')) { closeBinder(); ui.refresh(); return; }
    const done = await ACT[k]?.(t, ui); if (done === 'close') { closeBinder(); ui.refresh(); } else if (done) { ui.refresh(); draw(); }
  };
  open.addEventListener('keydown', e => { if (e.key === 'Escape') { closeBinder(); ui.refresh(); } });
  setTimeout(() => ($('[data-close]', open!) as HTMLElement)?.focus(), 30);
}

const RENDER: Record<string, (ui: GameUI) => { tab: string; html: string }> = {
  ideas(ui) {
    const list = ui.v.ideas, depth: Record<string, number> = {};
    const dep = (i: any): number => depth[i.id] ?? (depth[i.id] = i.requires.length ? 1 + Math.max(...i.requires.map((r: string) => dep(list.find((x: any) => x.id === r)))) : 0); list.forEach(dep);
    const cols: Record<number, any[]> = {}; for (const i of list) (cols[depth[i.id]] ||= []).push(i);
    const pos: Record<string, [number, number]> = {}; Object.entries(cols).forEach(([d, arr]) => arr.forEach((i, k) => { pos[i.id] = [10 + +d * 262, 10 + k * 104 + (arr.length < 3 ? 52 * (3 - arr.length) / 2 : 0)]; }));
    const W = 10 + (Math.max(...Object.keys(cols).map(Number)) + 1) * 262, H = 10 + 3 * 104 + 30, y = ui.v.you.yields.ideas;
    const lines = list.flatMap((i: any) => i.requires.map((r: string) => { const [x1, y1] = pos[r], [x2, y2] = pos[i.id]; return `<path d="M${x1 + 220} ${y1 + 32} C${x1 + 245} ${y1 + 32} ${x2 - 25} ${y2 + 32} ${x2} ${y2 + 32}" stroke="${ui.v.you.ideas_done.includes(r) ? '#2b2118' : 'rgba(43,33,24,.35)'}" stroke-width="2" fill="none"/>`; })).join('');
    return { tab: 'Ideas', html: head('Ideas', 'The think tank', `It turns out +${y.toFixed(0)} Ideas a turn. Pick what it studies; finished ideas unlock buildings and strengthen your pieces. Some wait for their year: nobody runs a social-media campaign in 2000.`) +
      `<div class="tree"><div class="tree-in" style="width:${W}px;height:${H}px"><svg width="${W}" height="${H}" style="position:absolute;inset:0">${lines}</svg>${list.map((i: any) => `<button class="tnode ${i.state}" type="button" data-idea="${i.id}" style="left:${pos[i.id][0]}px;top:${pos[i.id][1]}px" ${i.state === 'done' ? 'disabled' : ''}>
        <span class="ti">${icon(i.icon)}</span><span><b>${esc(i.name)}</b><small>${esc(i.text)}</small><span class="cost">${i.state === 'done' ? 'Done' : i.state === 'year' ? `From ${i.year}` : `${i.cost} Ideas · ${Math.max(1, Math.ceil((i.cost - i.prog) / Math.max(1, y)))} turns`}</span></span>${i.prog ? `<span class="pbar"><i style="width:${Math.min(100, i.prog / i.cost * 100)}%"></i></span>` : ''}</button>`).join('')}</div></div>` };
  },
  doctrines(ui) {
    const c = ui.v.you.doctrine_cost, inf = ui.v.you.influence;
    return { tab: 'Doctrines', html: head('Doctrines', 'How your party campaigns', `Influence ${Math.round(inf)}; the next doctrine costs ${c}. Open a tree with its first policy, take the middle two in any order, and finish it for the finisher.`) +
      `<div class="doctrines">${ui.v.doctrines.map((d: any) => `<section class="doct" style="--tint:${d.tint}"><h3>${esc(d.name)}</h3><span class="muted" style="font:400 13px var(--f-text)">${esc(d.blurb)}</span>
        ${d.policies.map((p: any) => `<button class="pol ${p.state}" type="button" data-pol="${p.id}" ${p.state !== 'open' || inf < c ? 'disabled' : ''}><b>${esc(p.name)}</b><small>${esc(p.text)}</small>${p.state === 'open' ? `<small style="color:${inf >= c ? 'var(--stamp)' : 'var(--ink-3)'}">${c} Influence</small>` : ''}</button>`).join('')}</section>`).join('')}</div>` };
  },
  factions(ui) {
    const v = ui.v, rl = v.rival.leader.last;
    return { tab: 'Factions', html: head('Factions', 'The nine blocs', `Both parties court them. Friends (30) give you a bonus; allies (60, and ahead of ${esc(rl)}) double it and turn out their heartlands every turn. A bloc neither party will court can split off as a third party.`) +
      `<div class="facs">${v.factions.map((f: any) => { const lab = ['Allied with ' + rl, 'Leans ' + rl, 'Undecided', 'Friendly', 'Your ally'][f.status + 2];
        return `<section class="fac"><h3><span style="color:${f.status > 0 ? Settings.you : f.status < 0 ? Settings.them : 'var(--ink-2)'}">${icon(f.icon)}</span>${esc(f.name)}</h3><span class="muted" style="font:400 13px var(--f-text)">${esc(f.who)}</span>
          <div class="chips"><span class="chip ${f.status > 0 ? 'good' : f.status < 0 ? 'bad' : ''}">${lab}</span>${f.founding ? '<span class="chip">Founding faction</span>' : ''}${f.third ? `<span class="chip bad">${esc(f.third.name)} on the ballot</span>` : ''}</div>
          <div class="rel"><div class="row" style="justify-content:space-between;font:400 12.5px var(--f-type)"><span>You ${Math.round(f.you)}</span><span>${esc(rl)} ${Math.round(f.rival)}</span></div>
          <div class="relbar"><i style="width:${f.you}%;background:${Settings.you}"></i><span class="m" style="left:30%"></span><span class="m" style="left:60%"></span></div><div class="relbar"><i style="width:${f.rival}%;background:${Settings.them}"></i><span class="m" style="left:30%"></span><span class="m" style="left:60%"></span></div></div>
          <span style="font:400 13px var(--f-text);color:var(--ink-2)">As friends: ${esc(f.bonus)}. As allies, double.</span>
          ${f.quest ? `<div class="quest ${f.quest.done ? 'met' : ''}">${f.quest.done ? 'Done: ' : 'They ask: '}${esc(f.quest.text)}${f.quest.done ? '' : ' (+25)'}</div>` : `<div class="quest">They will not deal with you while they back ${esc(rl)}. Gifts still count.</div>`}
          <div class="row" style="gap:6px"><button class="btn sm" type="button" data-gift="${f.id}" ${v.you.money >= 10 ? '' : 'disabled'}>${icon('money')} Gift $10 (+12)</button><button class="btn sm" type="button" data-heart="${f.heart}">Show their heartland</button></div></section>`; }).join('')}</div>` };
  },
  platform(ui) {
    const v = ui.v, c = v.you.plank_cost;
    return { tab: 'Platform', html: head('Platform', 'What you promise', `A pledge costs ${c} Influence (you have ${Math.round(v.you.influence)}). Each moves the national vote and the factions, for and against, by the ranges shown. In office, a pledge you keep is a law you can pass.`) +
      `<div class="planks">${v.planks.map((p: any) => `<div style="display:grid;gap:7px">${forecastCard({ title: p.name, tag: p.pledged ? 'Pledged' : 'If you pledge it', lines: [{ label: 'Your national vote', lo: p.national[0], mid: p.national[1], hi: p.national[2], unit: 'pts', good: 'up' }, ...p.forecast], factions: p.factions }, ui.fac, Settings.ranges)}
        <p style="margin:0;font:400 14px/1.4 var(--f-text)">${esc(p.blurb)}${p.wanted.length ? ` <b style="color:var(--warn)">Wanted by ${esc(p.wanted.join(', '))}.</b>` : ''}</p>
        <button class="btn ${p.pledged ? '' : 'red'} sm" type="button" data-plank="${p.id}" ${p.pledged || v.you.influence < c ? 'disabled' : ''} style="justify-self:start">${p.pledged ? 'Pledged' : `Pledge · ${c} Influence`}</button></div>`).join('')}</div>` };
  },
  world(ui) {
    const v = ui.v, att = ['Hostile', 'Wary', 'Neutral', 'Friendly', 'Allied'];
    return { tab: 'World', html: head('World', 'The great powers', 'Eight powers, each with a leader, an agenda and a memory. Attitudes move with what you do. In the full game the world board carries its own crises: the Strait of Hormuz, the Taiwan Strait.') +
      `<div class="world"><div class="world-map" id="wmap" aria-label="The world">${worldMap(v)}</div><div class="powers">${v.powers.map((p: any) => `<div class="power"><div style="width:54px">${portrait('w-' + p.who, { spot: NATION[p.look] || '#5a4632', age: 60, title: p.who })}</div><div><b>${esc(p.who)}</b><span>${esc(p.c)} · ${esc(p.agenda)}</span></div><span class="chip ${p.att > 0 ? 'good' : p.att < 0 ? 'bad' : ''}">${att[p.att + 2]}</span></div>`).join('')}</div></div>` };
  },
  victory(ui) {
    const v = ui.v, [y, t] = v.projection, allies = v.factions.filter((f: any) => f.status === 2).length, won = v.election?.won, laws = v.office && v.office.holder === 0 ? v.office.passed.length : 0;
    const vic = [['trophy', 'Dynasty', 'Hold the White House for 6 of the 12 terms to 2048.', won ? 1 / 6 : 0], ['people', 'Realignment', 'Hold 6 factions as allies at once.', allies / 6], ['capitol', 'Reform', 'Pass 12 landmark laws.', laws / 12], ['star', 'Legacy', 'The best record by 2049.', 0]];
    const st = [...v.states].filter((s: any) => s.code !== 'DC').sort((a: any, b: any) => b.ev - a.ev);
    return { tab: 'Victory', html: head('Victory', 'How the Long Game is won', `Four ways to win, most of them decades long. Right now: ${y} electoral votes to ${t}.`) +
      `<div class="vics">${vic.map(([ic, n, d, p]) => `<section class="vic"><h3>${icon(ic as string)} ${n}</h3><span style="font:400 13.5px var(--f-text)">${d}</span><div class="bar"><i style="width:${Math.min(100, (p as number) * 100)}%"></i></div><span class="type muted">${Math.round(Math.min(1, p as number) * 100)}%</span></section>`).join('')}</div>
      <section class="sheet"><h3>The states</h3><span class="muted" style="font:400 13px var(--f-text)">Where you have polled, the truth; elsewhere the public polls, which can miss by three points or more.</span>
      <div class="statelist">${st.map((s: any) => `<button class="srow" type="button" data-state="${s.code}"><span>${esc(s.name)}${s.polled ? '' : ' <span class="muted">· polls</span>'}</span><span class="muted tnum">${s.ev}</span><span class="lean" style="background:${s.seen >= 0 ? Settings.you : Settings.them};opacity:${Math.min(1, .35 + Math.abs(s.seen) / 14)}">${s.seen >= 0 ? 'You' : 'Them'} +${Math.abs(s.seen).toFixed(0)}</span></button>`).join('')}</div></section>` };
  },
  century(ui) {
    const v = ui.v, y0 = 1994, y1 = 2048, now = v.year, W = (y1 - y0) * 34 + 170, laneH = 96, lanes = [['econ', 'Economy & AI'], ['energy', 'Energy & climate'], ['health', 'Health & society'], ['security', 'Security & space']];
    const H = 40 + lanes.length * laneH, X = (y: number) => 150 + (y - y0) * (W - 170) / (y1 - y0);
    let s = `<svg width="${W}" height="${H}" role="img" aria-label="The century: technologies and policies by forecast year">`;
    for (let yy = y0 + 2; yy <= y1; yy += 4) s += `<line x1="${X(yy)}" x2="${X(yy)}" y1="24" y2="${H}" stroke="#2b2118" opacity=".1"/><text x="${X(yy)}" y="15" text-anchor="middle" font-family="Special Elite, monospace" font-size="11" fill="#57452f">${yy}</text>`;
    lanes.forEach(([, n], i) => { s += `<text x="8" y="${60 + i * laneH}" font-family="'IM Fell English SC', Georgia, serif" font-size="14" fill="#2b2118">${n}</text>`; });
    s += `<line x1="${X(now)}" x2="${X(now)}" y1="20" y2="${H}" stroke="#a3342b" stroke-width="2" stroke-dasharray="5 4"/><text x="${X(now) + 5}" y="${H - 6}" font-family="Special Elite, monospace" font-size="12" fill="#a3342b">Now</text>`;
    const used: Record<string, number> = {};
    for (const c of v.century) { if (c.year + c.spread < y0) continue; const li = lanes.findIndex(l => l[0] === c.lane), k = c.lane + Math.round(c.year / 4), row = used[k] = (used[k] || 0) + 1;
      const cy = 38 + li * laneH + (row % 2 ? 20 : 56), x = X(c.year), w = Math.min(160, 18 + c.name.length * 6.6), done = c.year <= now && c.kind === 'tech';
      if (c.kind === 'tech' && c.spread && !done) s += `<line x1="${X(c.year - c.spread)}" x2="${X(c.year + c.spread)}" y1="${cy}" y2="${cy}" stroke="#57452f"/><line x1="${X(c.year - c.spread)}" x2="${X(c.year - c.spread)}" y1="${cy - 5}" y2="${cy + 5}" stroke="#57452f"/><line x1="${X(c.year + c.spread)}" x2="${X(c.year + c.spread)}" y1="${cy - 5}" y2="${cy + 5}" stroke="#57452f"/>`;
      s += `<g><title>${esc(c.name)}: ${esc(c.note)}</title><rect x="${x - w / 2}" y="${cy - 12}" width="${w}" height="24" rx="${c.kind === 'civic' ? 12 : 2}" fill="${done ? '#dfe8cf' : '#f5eddb'}" stroke="${c.kind === 'civic' ? '#7a5aa8' : '#2b2118'}" stroke-width="1.3"/><text x="${x}" y="${cy + 4}" text-anchor="middle" font-family="Alegreya Sans, sans-serif" font-weight="700" font-size="11.5" fill="#2b2118">${esc(c.name)}</text></g>`; }
    return { tab: 'Century', html: head('Century', 'The next fifty years', 'The nation\'s technologies arrive on their own; the whiskers are the forecast\'s 90% range. In office, funding pulls them forward. Rounded nodes are policies whose time comes. Hover a node for its note.') + `<div class="tree sheet"><div style="width:${W}px">${s}</svg></div></div>` };
  },
};
const NATION: Record<string, string> = { cn: '#b8312f', ru: '#2f4f9a', eu: '#1f3f8f', uk: '#23397a', jp: '#b8312f', in: '#e08a2c', mx: '#2f7a4a', br: '#2f8a4a' };
const WHERE: Record<string, [number, number]> = { China: [104, 35], Russia: [90, 60], 'European Union': [10, 50], 'United Kingdom': [-2, 54], Japan: [138, 37], India: [79, 22], Mexico: [-102, 23], Brazil: [-52, -10] };
let WORLD: any = null;
export function setWorld(w: any) { WORLD = w; }
function worldMap(v: any) {
  const W = 1000, H = 500, X = (lon: number) => (lon + 180) / 360 * W, Y = (lat: number) => (90 - lat) / 180 * H;
  const paths = WORLD ? WORLD.map((c: any) => { const p = v.powers.find((x: any) => x.c === c.n || (x.c === 'European Union' && c.eu)); const fill = c.n === 'United States of America' ? Settings.you : p ? (p.att > 0 ? '#b9d1a0' : p.att < 0 ? '#e2a99a' : '#e6d7b0') : '#eee3c8';
    return `<path d="${c.d}" fill="${fill}" stroke="#2b2118" stroke-width=".5"/>`; }).join('') : '';
  const pins = v.powers.map((p: any) => { const [lon, lat] = WHERE[p.c] || [0, 0]; return `<g transform="translate(${X(lon)} ${Y(lat)})"><circle r="11" fill="${NATION[p.look] || '#5a4632'}" stroke="#2b2118" stroke-width="1.5"/><text y="4" text-anchor="middle" font-family="'IM Fell English SC', serif" font-size="11" fill="#fff">${p.c[0]}</text></g>`; }).join('');
  return `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="A map of the world with the eight great powers"><rect width="${W}" height="${H}" fill="#d9e4e2"/>${Array.from({ length: 11 }, (_, i) => `<line x1="0" x2="${W}" y1="${i * 50}" y2="${i * 50}" stroke="#2b2118" stroke-width=".25" opacity=".4"/>`).join('')}${paths}${pins}</svg>`;
}

const ACT: Record<string, (t: HTMLElement, ui: GameUI) => Promise<boolean | 'close' | void>> = {
  async ideas(t, ui) { const b = t.closest('[data-idea]') as HTMLButtonElement | null; if (!b || b.disabled) return; const r = ui.sim.tryCmd({ c: 'research', idea: b.dataset.idea });
    if (r.error) { toast('hourglass', 'Not yet', r.error); return; } sound.click(); return true; },
  async doctrines(t, ui) { const b = t.closest('[data-pol]') as HTMLButtonElement | null; if (!b || b.disabled) return; const r = ui.sim.tryCmd({ c: 'adopt', policy: b.dataset.pol });
    if (r.error) { toast('close', 'Not possible', r.error); return; } sound.good(); await ui.play(r.fx); return true; },
  async factions(t, ui) {
    const g = t.closest('[data-gift]') as HTMLButtonElement | null; if (g && !g.disabled) { const r = ui.sim.tryCmd({ c: 'gift', faction: g.dataset.gift }); if (r.error) toast('close', 'Not possible', r.error); else { sound.click(); await ui.play(r.fx); } return true; }
    const h = t.closest('[data-heart]') as HTMLElement | null; if (h) { const st = h.dataset.heart; const c = ui.map.cities.filter(x => x.s === st).sort((a, b) => b.pop - a.pop)[0]; if (c) setTimeout(() => ui.board.centerHex(c.h, 1.9), 50); return 'close'; }
  },
  async platform(t, ui) { const b = t.closest('[data-plank]') as HTMLButtonElement | null; if (!b || b.disabled) return; const r = ui.sim.tryCmd({ c: 'pledge', plank: b.dataset.plank }); if (r.error) { toast('close', 'Not possible', r.error); return; } sound.good(); await ui.play(r.fx); return true; },
  async victory(t, ui) { const b = t.closest('[data-state]') as HTMLElement | null; if (!b) return; const c = ui.map.cities.filter(x => x.s === b.dataset.state).sort((a, b) => b.pop - a.pop)[0]; if (c) setTimeout(() => ui.board.centerHex(c.h, 2), 50); return 'close'; },
};
export { sgn };
