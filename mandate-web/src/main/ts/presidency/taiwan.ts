// The Taiwan crisis room (docs/ISLAND_CHAIN.md): the crisis by days and the war by hours. Both sides' ladders, the Western
// Pacific by sea zone (your forces, and the enemy as your side sees it), Taiwan sector by sector, the warning picture,
// Taiwan's lights and chips, the allies, the economy, nuclear risk and the toll. As America, the president gives theatre
// directives and the staff runs the units; as China (the standalone scenario), the player chooses the course of action.
import { h, s } from '../core/dom.js';
import { day } from '../core/format.js';
import type { TaiwanView, TaiwanZone } from '../core/types.js';
import { PACIFIC, PACIFIC_PROJECTION } from '../generated/pacific.js';
import { STRAIT_PROJECTION, TAIWAN_SECTORS } from '../generated/strait.js';

export interface TaiwanActions {
  view(): TaiwanView;
  /** A crisis order; false if refused (the refusal is shown). */
  order(o: object): boolean;
  /** Plays the turn (answering the desk first); the room is redrawn after. */
  next(): void;
  back(): void;
}

const PLAN_LABEL: Record<string, string> = {
  invasion: 'An invasion', blockade: 'A blockade', quarantine: 'A quarantine', islands: 'Seizing the outlying islands', strikes: 'A strike campaign',
};
const PLANS = ['invasion', 'blockade', 'quarantine', 'islands', 'strikes'];
const MEASURES_US: [string, string, boolean][] = [
  ['sanctions1', 'Targeted sanctions', false], ['sanctions2', 'Sectoral sanctions', false], ['sanctions3', 'Full financial sanctions', true],
  ['chips', 'Embargo chips and tools', false], ['freeze', 'Freeze China’s reserves', true], ['swift', 'Cut China’s banks from SWIFT', true],
  ['distant', 'A distant blockade of China’s trade', true],
];
const MEASURES_PRC: [string, string, boolean][] = [
  ['countersanctions', 'Counter-sanctions', false], ['rareearths', 'Ban rare-earth exports', false], ['boycott', 'Boycott American goods', false],
  ['tourism', 'Halt tourism and students', false], ['treasury', 'Sell US Treasuries', true], ['seize', 'Seize American assets', true],
];
const ACTIONS_US: [string, string][] = [['consult', 'Consult and share intelligence'], ['access', 'Ask for access and overflight'], ['basing', 'Ask for basing'],
  ['combat', 'Ask it to fight'], ['assurance', 'Offer economic assurance'], ['sanctions', 'Coordinate sanctions'], ['airports', 'Open civil airports (Japan)'],
  ['warn', 'Warn it off (Russia, North Korea)']];
const ACTIONS_PRC: [string, string][] = [['reassure', 'Reassure and offer trade'], ['coerce', 'Economic coercion'], ['threaten', 'Threaten consequences'],
  ['support', 'Ask for support (Russia, North Korea)'], ['neutrality', 'Propose a neutrality pact'], ['influence', 'Influence operations']];
const ROLES = ['Access', 'Basing', 'Defend itself', 'Fight'];
const AIR: [string, number[]][] = [['The staff’s mix', [0.5, 0, 0.25, 0.25]], ['Sink the fleet', [0.65, 0.05, 0.15, 0.15]], ['Hit the mainland', [0.35, 0.3, 0.2, 0.15]],
  ['Taiwan’s skies', [0.25, 0.05, 0.5, 0.2]], ['Guard the fleet', [0.3, 0.05, 0.2, 0.45]]];
const AIR_PARTS = ['Anti-ship strikes', 'Mainland strikes', 'Over Taiwan', 'Fleet defence'];
const NATION_ADJ: Record<string, string> = { USA: 'American', CHN: 'Chinese', TWN: 'Taiwanese', JPN: 'Japanese', KOR: 'Korean', PHL: 'Philippine', AUS: 'Australian' };

// the Western Pacific: the generated map's projection, cropped to the theatre from the Yellow Sea to Guam
const PV = { x: 160, y: 90, w: 770, h: 640 };
const PP = PACIFIC_PROJECTION;
const pAt = (lon: number, lat: number): [number, number] => [(lon - PP.lon0) * PP.cos * PP.k, (PP.lat1 - lat) * PP.k];
const PX_PER_KM = PP.k / 111.2;
const PLACES: [string, number, number][] = [['CHINA', 113.5, 29.5], ['JAPAN', 133.5, 34.6], ['KOREA', 127.6, 37.2], ['PHILIPPINES', 122.4, 13.4],
  ['Guam', 144.0, 12.6]];
const STRAIT_PLACES: [string, number, number][] = [['CHINA', 118.4, 25.9], ['TAIWAN', 121.05, 23.1]];

// Taiwan, its outlying islands and the waters around it, from Kinmen to the Yonaguni Gap and the Bashi Channel
const SP = STRAIT_PROJECTION;
const sAt = (lon: number, lat: number): [number, number] => [(lon - SP.lon0) * SP.cos * SP.k, (SP.lat1 - lat) * SP.k];
const SV = (() => { const [x0, y0] = sAt(117.9, 26.55), [x1, y1] = sAt(123.9, 21.2); return { x: x0, y: y0, w: x1 - x0, h: y1 - y0 }; })();

const pct = (x: number) => `${Math.round(x * 100)}%`;
const num = (x: number) => Math.round(x).toLocaleString('en-US');

function meter(v: number, max: number, label: string, tone = '', text = String(Math.round(v))): HTMLElement {
  return h('span', { class: 'hill-meter ' + tone, role: 'meter', 'aria-valuemin': '0', 'aria-valuemax': String(max), 'aria-valuenow': String(Math.round(v)), 'aria-label': label },
    h('span', { style: `width:${Math.max(2, Math.min(100, (v / max) * 100))}%` }), h('span', { class: 'hill-meter-value' }, text));
}

export function taiwanRoom(act: TaiwanActions): HTMLElement {
  const root = h('div', { class: 'room hill sitroom hz tw' });
  let arming: string | null = null;
  // China's course of action, as the player as China drafts it (the standalone scenario)
  let coa: { plan: string; strikeUS: boolean; strikeJapan: boolean; cyber: boolean; decapitation: boolean; penghuFirst: boolean; region: number; lethality: number; dDay: number } | null = null;
  let dipCountry = '';
  let dipAction = '';
  const order = (o: object) => { act.order(o); arming = null; draw(); };
  /** A grave order takes two clicks: the first arms it, the second gives it. */
  const grave = (key: string, label: string, o: object, disabled: boolean, cls = 'btn small') => arming === key
    ? h('span', { class: 'sit-confirm' }, h('button', { class: 'btn small danger', type: 'button', onclick: () => order(o) }, 'Confirm: ' + label.toLowerCase()),
      h('button', { class: 'btn small quiet', type: 'button', onclick: () => { arming = null; draw(); } }, 'Cancel'))
    : h('button', { class: cls, type: 'button', disabled, onclick: () => { arming = key; draw(); } }, label);
  /** A row of toggles, one pressed. */
  const steps = (label: string, names: string[], now: number, live: boolean, make: (i: number) => object, graveFrom = 99) =>
    h('div', { class: 'hz-steps', role: 'group', 'aria-label': `${label}: ${names[now] ?? ''}` }, ...names.map((n, i) => i >= graveFrom && i !== now
      ? grave(label + i, n, make(i), !live, 'btn small toggle')
      : h('button', { class: 'btn small toggle' + (i === now ? ' on' : ''), type: 'button', 'aria-pressed': String(i === now), disabled: !live, onclick: () => order(make(i)) }, n)));

  function draw(): void {
    const v = act.view();
    const live = v.open && v.active;
    const us = v.side === 'US';
    const turn = v.phase === 'war' ? (v.turnHours === 1 ? 'Next hour' : `Next ${v.turnHours} hours`) : 'Next day';
    const when = v.open ? `${day(v.date, true)}${v.phase === 'war' ? `, ${String(v.hourOfDay).padStart(2, '0')}:00` : ''} · day ${v.day} of the crisis`
      + (v.phase === 'war' ? ` · ${v.warDays < 1 ? 'the first day' : `day ${Math.floor(v.warDays) + 1}`} of the war` : '') : '';
    const top = h('div', { class: 'room-top' },
      h('div', null, h('div', { class: 'eyebrow' }, v.standalone ? `Island Chain · ${v.side === 'US' ? 'the United States' : 'China'}` : 'The Situation Room'),
        h('h2', { class: 'dialog-title' }, 'The Taiwan Strait'), when ? h('p', { class: 'muted' }, when) : null),
      h('div', { class: 'cab-buttons' },
        live ? h('button', { class: 'btn primary hz-next', type: 'button', onclick: () => act.next(), title: 'Play out the turn (Ctrl+Enter)' }, turn, h('span', { class: 'ov-end-arrow', 'aria-hidden': 'true' }, '›')) : null,
        h('button', { class: live ? 'btn' : 'btn primary', type: 'button', onclick: act.back }, v.standalone ? 'Back to the start' : 'Back to the Oval Office')));
    if (!v.open) { root.replaceChildren(top, h('p', null, v.reason ?? '')); return; }
    root.replaceChildren(...[top,
      v.message ? h('p', { class: 'sit-message', role: 'status' }, v.message) : null,
      v.active ? null : h('div', { class: 'hz-over tw-over', role: 'note' },
        h('p', null, `${v.outcome ?? 'The crisis is over'}. ${v.endReason ?? ''}`),
        v.score !== null ? h('p', { class: 'small' }, `${us ? 'America’s' : 'China’s'} record: ${Math.round(v.score)} of 100 (${(v.grade ?? '').toLowerCase()}), after ${v.day} days${v.warDays > 0 ? `, ${Math.round(v.warDays)} of them at war` : ''}.`) : null),
      offerBanner(v, live),
      phaseLine(v),
      h('div', { class: 'tw-ladders' }, ladder(v, 'prc', live), ladder(v, 'us', live)),
      h('div', { class: 'hz-top' }, mapCard(v), h('div', { class: 'hz-side' }, taiwanCard(v, live))),
      h('div', { class: 'room-grid two hz-tools' },
        us ? warningCard(v) : chinaCard(v, live),
        us ? directivesCard(v, live) : null,
        alliesCard(v, live), programmesCard(v, live), economyCard(v, live), nuclearCard(v, live), tollCard(v)),
    ].filter((x): x is HTMLElement => x !== null));
  }

  function phaseLine(v: TaiwanView): HTMLElement {
    const us = v.side === 'US';
    const text = v.phase === 'crisis'
      ? (us ? `China has not attacked. The intelligence community rates the warning ${v.warning.name.toLowerCase()}: a ${pct(v.warning.chance)} chance of military action within 30 days.`
        : v.china.stoodDown ? 'China has stood down.' : v.china.dDay > 0 ? `D-day is day ${v.china.dDay} of the crisis; H-hour 05:00.` : 'No D-day is set.')
      : v.phase === 'war' ? `War: ${PLAN_LABEL[v.china.plan]?.toLowerCase() ?? 'China’s attack'}. Turns are ${v.turnHours === 1 ? 'an hour' : v.turnHours + ' hours'} long.`
        : 'The crisis is over.';
    return h('p', { class: 'hz-cause tw-phase' }, text);
  }

  function offerBanner(v: TaiwanView, live: boolean): HTMLElement | null {
    if (!v.offer || v.offer.from === v.side) return null;
    const terms = ['a return to the status quo before the war', 'a ceasefire in place', 'a ceasefire and talks on unification'][v.offer.terms] ?? 'a ceasefire';
    return h('div', { class: 'tw-offer', role: 'alert' },
      h('p', null, h('strong', null, v.offer.from === 'PRC' ? 'Beijing proposes ' : 'Washington proposes '), terms + '.'),
      h('span', { class: 'cab-buttons' },
        grave('accept', 'Accept', { type: 'Answer', accept: true }, !live, 'btn small primary'),
        h('button', { class: 'btn small', type: 'button', disabled: !live, onclick: () => order({ type: 'Answer', accept: false }) }, 'Reject')));
  }

  function ladder(v: TaiwanView, who: 'us' | 'prc', live: boolean): HTMLElement {
    const mine = (who === 'us') === (v.side === 'US');
    const names = who === 'us' ? v.rungs.us : v.rungs.prc, now = v.rung[who];
    const id = 'tw-ladder-' + who;
    return h('section', { class: 'room-card hz-ladder-card tw-ladder ' + who, 'aria-labelledby': id },
      h('h3', { class: 'room-card-title', id }, who === 'us' ? 'America’s rules of engagement' : 'China’s ladder'),
      h('ol', { class: 'hz-ladder tw-rungs' }, ...names.map((n, i) => {
        const can = mine && live && i !== now && (who === 'prc' || i <= v.maxRung);
        return h('li', { class: 'hz-rung tw-rung r' + i + (i === now ? ' on' : '') + (i <= now ? ' lit' : ''), 'aria-current': i === now ? 'step' : null },
          h('span', { class: 'hz-rung-name' }, n),
          can ? (i > now && i >= 2 ? grave(who + 'rung' + i, 'Go to ' + i, { type: 'Rung', rung: i }, false) : h('button', { class: 'btn small quiet', type: 'button', onclick: () => order({ type: 'Rung', rung: i }) }, 'Go to ' + i))
            : mine && who === 'us' && i > v.maxRung ? h('span', { class: 'muted small' }, 'Not under your commitment') : null);
      })),
      mine && who === 'us' && v.commitment !== 'INTERVENE' ? h('p', { class: 'muted small' }, v.commitment === 'STAND_ASIDE'
        ? 'You chose to stand aside: America protests, and its forces stay out.' : 'You chose to arm Taiwan and sanction China: presence and escort, no American combat.') : null);
  }

  function mapCard(v: TaiwanView): HTMLElement {
    const us = v.side === 'US', own = us ? 'us' : 'prc', foe = us ? 'prc' : 'us';
    const marks: SVGElement[] = [];
    // the waters around Taiwan, as one marker here: the map of the strait has them zone by zone
    const near = v.zones.filter(z => z.near);
    {
      const [x, y] = pAt(121.4, 23.6);
      const own = near.reduce((a, z) => a + z.own.ships + z.own.subs, 0), foe = near.reduce((a, z) => a + Math.max(z.enemy.ships + z.enemy.subs, z.enemy.contacts), 0);
      const sure = near.every(z => z.enemy.known >= z.enemy.contacts), amph = near.some(z => z.enemy.amphibious > 0), carriers = near.some(z => z.own.carriers > 0);
      marks.push(s('g', { class: 'tw-zone near' + (foe ? ' hot' : '') }, s('circle', { cx: String(x), cy: String(y), r: String(300 * PX_PER_KM) }),
        s('title', null, `Around Taiwan: ${own} of your ships and submarines, ${foe} enemy (see the map of the strait)`)));
      if (own) marks.push(chip(x - 22, y - 14, us ? 'us' : 'prc', String(own) + (carriers ? '★' : '')));
      if (foe) marks.push(chip(x + 22, y + 14, us ? 'prc' : 'us', (sure ? '' : '~') + String(foe) + (amph ? '⚓' : '')));
    }
    for (const z of v.zones) {
      if (z.near) continue;
      const [x, y] = pAt(z.lon, z.lat), r = Math.max(10, Math.min(70, z.km * PX_PER_KM));
      const hasOwn = z.own.ships + z.own.subs > 0, hasFoe = z.enemy.contacts > 0;
      marks.push(s('g', { class: 'tw-zone' + (z.strait ? ' strait' : '') + (hasFoe ? ' hot' : '') },
        s('circle', { cx: String(x), cy: String(y), r: String(r) }),
        z.mines > 0 ? s('circle', { class: 'tw-mines', cx: String(x), cy: String(y), r: String(r * 0.6) }) : null,
        s('title', null, zoneText(z, v))));
      if (hasOwn) marks.push(chip(x - 14, y - 10, own, String(z.own.ships + z.own.subs) + (z.own.carriers ? '★' : '')));
      if (hasFoe) marks.push(chip(x + 14, y + 10, foe, (z.enemy.known < z.enemy.contacts ? '~' : '') + String(Math.max(z.enemy.ships + z.enemy.subs, z.enemy.contacts)) + (z.enemy.amphibious ? '⚓' : '')));
    }
    const atSea = v.zones.filter(z => z.own.ships + z.own.subs + z.enemy.contacts > 0);
    const map = s('svg', { class: 'sit-map hz-map tw-map', viewBox: `${PV.x} ${PV.y} ${PV.w} ${PV.h}`, role: 'img',
      'aria-label': `The Western Pacific: ${atSea.length} sea zones with forces in them. The list below the map gives each.` },
      s('rect', { x: String(PV.x), y: String(PV.y), width: String(PV.w), height: String(PV.h), class: 'hz-sea' }),
      ...PACIFIC.map(c => s('path', { d: c.d, class: 'hz-land' + (c.id === 'CN' ? ' prc' : c.id === 'TW' ? ' roc' : '') })),
      ...marks,
      ...PLACES.map(([n, lo, la]) => { const [x, y] = pAt(lo, la); return s('text', { x: String(x), y: String(y), class: n === n.toUpperCase() ? 'hz-place' : 'hz-small tw-town' }, n); }));
    const key = (cls: string, text: string) => h('li', null, h('span', { class: 'hz-key ' + cls, 'aria-hidden': 'true' }), text);
    return h('section', { class: 'room-card hz-map-card', 'aria-labelledby': 'tw-map-title' },
      h('h3', { class: 'room-card-title', id: 'tw-map-title' }, 'The Western Pacific'),
      map,
      h('ul', { class: 'hz-legend' }, key(own + ' chip', us ? 'American and allied ships and submarines (★ a carrier)' : 'Chinese ships and submarines'),
        key(foe + ' chip', (us ? 'Chinese' : 'American and allied') + ' forces as your intelligence sees them (~ unconfirmed; ⚓ amphibious)'), key('field', 'Mines')),
      atSea.length ? h('ul', { class: 'tw-zones' }, ...atSea.map(z => h('li', null, h('strong', null, z.name), ' · ', zoneText(z, v))))
        : h('p', { class: 'muted small' }, 'No forces at sea in the theatre yet.'));
  }

  function chip(x: number, y: number, cls: string, text: string): SVGElement {
    const w = 10 + text.length * 8;
    return s('g', { class: 'tw-chip ' + cls }, s('rect', { x: String(x - w / 2), y: String(y - 10), width: String(w), height: '20', rx: '4' }),
      s('text', { x: String(x), y: String(y + 5) }, text));
  }

  function zoneText(z: TaiwanZone, v: TaiwanView): string {
    const us = v.side === 'US', parts: string[] = [];
    if (z.own.ships) parts.push(`${z.own.ships} of your ships in ${z.own.groups} ${z.own.groups === 1 ? 'group' : 'groups'}${z.own.carriers ? `, ${z.own.carriers} ${z.own.carriers === 1 ? 'carrier' : 'carriers'}` : ''}${z.own.amphibious ? `, ${z.own.amphibious} amphibious` : ''}`);
    if (z.own.subs) parts.push(`${z.own.subs} of your submarines`);
    if (z.enemy.contacts) parts.push(`${us ? 'Chinese' : 'enemy'} contacts: ${z.enemy.contacts}${z.enemy.ships ? `, about ${z.enemy.ships} ships` : ''}${z.enemy.subs ? `, ${z.enemy.subs} submarines` : ''}${z.enemy.carriers ? `, ${z.enemy.carriers === 1 ? 'a carrier' : z.enemy.carriers + ' carriers'}` : ''}${z.enemy.amphibious ? `, ${z.enemy.amphibious} amphibious` : ''}${z.enemy.known < z.enemy.contacts ? ' (not all identified)' : ''}${z.enemy.age >= 6 ? `, last seen ${z.enemy.age} hours ago` : ''}`);
    if (z.mines > 0) parts.push(`about ${Math.round(z.mines)} mines`);
    return parts.join('; ') + '.';
  }

  function taiwanCard(v: TaiwanView, live: boolean): HTMLElement {
    const t = v.taiwan, us = v.side === 'US';
    const held = v.sectors.filter(x => x.prc >= 0.5), fought = v.sectors.filter(x => x.contested);
    const map = s('svg', { class: 'sit-map tw-island', viewBox: `${SV.x.toFixed(0)} ${SV.y.toFixed(0)} ${SV.w.toFixed(0)} ${SV.h.toFixed(0)}`, role: 'img',
      'aria-label': `Taiwan by sector: ${held.length ? `the PLA holds ${held.map(x => x.name).join(', ')}` : 'Taiwan holds every sector'}${fought.length ? `; fighting in ${fought.map(x => x.name).join(', ')}` : ''}.` },
      s('rect', { x: String(SV.x), y: String(SV.y), width: String(SV.w), height: String(SV.h), class: 'hz-sea' }),
      ...v.zones.filter(z => z.near && z.id !== 'TAIWAN').map(z => { const [x, y] = sAt(z.lon, z.lat); return s('text', { x: String(x), y: String(y - 22), class: 'tw-zone-name' }, z.name.replace('Taiwan Strait ', 'Strait ')); }),
      ...TAIWAN_SECTORS.map(g => {
        const sec = v.sectors.find(x => x.name === g.name);
        const p = sec?.prc ?? 0;
        const band = p >= 0.65 ? 'prc' : p >= 0.35 ? 'half' : p > 0.02 ? 'edge' : 'roc';
        return s('path', { d: g.d, class: 'tw-sector ' + band + (sec?.contested ? ' fight' : ''), 'fill-rule': 'evenodd' },
          s('title', null, `${g.name}: ${p > 0.02 ? `the PLA controls ${pct(p)}` : 'held by Taiwan'}${sec?.contested ? ', contested' : ''}${sec?.lodgment ? ', a lodgement' : ''}`));
      }),
      ...v.sectors.filter(x => x.lodgment).map(x => {
        const g = TAIWAN_SECTORS.find(y => y.name === x.name);
        const n = (g?.d.match(/-?\d+(?:\.\d+)?/g) ?? []).map(Number);
        if (n.length < 2) return null;
        let cx = 0, cy = 0; for (let i = 0; i + 1 < n.length; i += 2) { cx += n[i]!; cy += n[i + 1]!; }
        cx /= n.length / 2; cy /= n.length / 2;
        return s('path', { class: 'tw-lodge', d: `M${cx} ${cy - 9}L${cx + 3} ${cy - 3}L${cx + 9} ${cy - 3}L${cx + 4} ${cy + 2}L${cx + 6} ${cy + 9}L${cx} ${cy + 5}L${cx - 6} ${cy + 9}L${cx - 4} ${cy + 2}L${cx - 9} ${cy - 3}L${cx - 3} ${cy - 3}Z` });
      }).filter((x): x is SVGPathElement => x !== null),
      ...STRAIT_PLACES.map(([n, lo, la]) => { const [x, y] = sAt(lo, la); return s('text', { x: String(x), y: String(y), class: 'hz-place' }, n); }),
      ...v.zones.filter(z => z.near && z.id !== 'TAIWAN').flatMap(z => {
        const [x, y] = sAt(z.lon, z.lat), out: SVGElement[] = [];
        const own = z.own.ships + z.own.subs, foe = Math.max(z.enemy.ships + z.enemy.subs, z.enemy.contacts);
        if (own) out.push(chip(x - 18, y, us ? 'us' : 'prc', String(own) + (z.own.carriers ? '★' : '')));
        if (foe) out.push(chip(x + 18, y, us ? 'prc' : 'us', (z.enemy.known < z.enemy.contacts ? '~' : '') + String(foe) + (z.enemy.amphibious ? '⚓' : '')));
        if (z.mines > 0) out.push(s('circle', { class: 'tw-minefield', cx: String(x), cy: String(y + 22), r: '7' }, s('title', null, `${z.name}: about ${Math.round(z.mines)} mines`)));
        return out;
      }));
    const key = (cls: string, text: string) => h('li', null, h('span', { class: 'hz-key ' + cls, 'aria-hidden': 'true' }), text);
    const ask = (what: 'mobilise' | 'ration' | 'disperse', label: string) => h('button', { class: 'btn small toggle' + (t.asked[what] ? ' on' : ''), type: 'button',
      'aria-pressed': String(t.asked[what]), disabled: !live || t.asked[what], onclick: () => order({ type: 'TaiwanAsk', what }) }, label);
    return h('section', { class: 'room-card', 'aria-labelledby': 'tw-island' },
      h('h3', { class: 'room-card-title', id: 'tw-island' }, 'Taiwan'),
      map,
      h('ul', { class: 'hz-legend' }, key('roc', 'Held by Taiwan'), key('half', 'Contested'), key('prc', 'Held by the PLA'), key('lodge', 'A lodgement'),
        key((us ? 'us' : 'prc') + ' chip', 'Your forces'), key((us ? 'prc' : 'us') + ' chip', 'The enemy, as seen')),
      h('dl', { class: 'ww-stats' },
        h('dt', null, 'Electricity'), h('dd', null, meter(t.electricity * 100, 100, 'Taiwan’s electricity, share of normal', t.electricity < 0.6 ? 'bad' : t.electricity < 0.9 ? 'warn' : '', pct(t.electricity))),
        h('dt', null, 'Chip output'), h('dd', null, meter(t.chips * 100, 100, 'Taiwan’s chip output, share of normal', t.chips < 0.5 ? 'bad' : t.chips < 0.9 ? 'warn' : '', pct(t.chips))),
        h('dt', null, 'Will to resist'), h('dd', null, meter(t.will, 100, 'Taiwan’s will to resist', t.will < 35 ? 'bad' : '')),
        h('dt', null, 'Shipping arriving'), h('dd', null, `${pct(t.throughput)} of normal · ${t.blockadeName}`),
        h('dt', null, 'Fuel'), h('dd', null, `LNG ${t.lng.toFixed(0)} million m³ · coal ${num(t.coal)} thousand t · oil ${t.oil.toFixed(0)} million bbl`),
        t.plaAshore > 0 ? h('dt', null, 'The ground war') : null,
        t.plaAshore > 0 ? h('dd', null, `${t.plaAshore.toFixed(0)} PLA battalions ashore against ${t.rocReady.toFixed(0)} Taiwanese; the PLA holds ${num(t.territory)} km²`) : null,
        t.civilians > 0 ? h('dt', null, 'Civilians killed') : null, t.civilians > 0 ? h('dd', null, num(t.civilians)) : null),
      us ? h('div', { class: 'sit-tool' }, h('span', null, h('strong', null, 'Ask Taipei'), h('br'), h('span', { class: 'muted small' }, 'Call up the reserves, ration power to stretch the fuel, and disperse the navy and missiles before they are caught in port.')),
        h('span', { class: 'cab-buttons' }, ask('mobilise', 'Mobilise'), ask('ration', 'Ration energy'), ask('disperse', 'Disperse'))) : null);
  }

  function warningCard(v: TaiwanView): HTMLElement {
    const w = v.warning;
    return h('section', { class: 'room-card', 'aria-labelledby': 'tw-warn' },
      h('h3', { class: 'room-card-title', id: 'tw-warn' }, 'Warning and intelligence'),
      h('ol', { class: 'tw-warnlevels', 'aria-label': `Warning level: ${w.name}` }, ...w.levels.map((n, i) => h('li', { class: i <= w.level ? 'lit l' + i : '', 'aria-current': i === w.level ? 'step' : null }, n))),
      v.china.known
        ? h('p', null, h('strong', null, 'China’s plan: '), PLAN_LABEL[v.china.plan] ?? v.china.plan, '.')
        : h('div', null, h('p', { class: 'small' }, 'China’s most likely course, as the analysts weigh it:'),
          h('dl', { class: 'ww-stats' }, ...w.plans.flatMap(p => [h('dt', null, PLAN_LABEL[p.plan] ?? p.plan), h('dd', null, meter(p.share * 100, 100, `${PLAN_LABEL[p.plan] ?? p.plan}: ${pct(p.share)}`, '', pct(p.share)))]))),
      w.indicators.length ? h('ul', { class: 'hz-log tw-indicators', tabindex: '0', 'aria-label': 'Indicators of preparations, newest first' }, ...w.indicators.map(x => h('li', null, `Day ${x.day}: ${x.text}`, h('span', { class: 'muted small' }, ` (${x.confidence >= 0.7 ? 'high' : x.confidence >= 0.4 ? 'moderate' : 'low'} confidence${x.cover ? '; an exercise may explain it' : ''})`))))
        : h('p', { class: 'muted small' }, 'No indicators of preparations yet.'),
      h('p', { class: 'muted small' }, 'Indicators come from the preparations China cannot hide: ferries requisitioned, brigades embarking, submarines leaving port. Exercises give cover, and some reports are planted.'));
  }

  function directivesCard(v: TaiwanView, live: boolean): HTMLElement {
    const d = v.directives;
    const tool = (title: string, note: string, ctl: HTMLElement) => h('div', { class: 'sit-tool' }, h('span', null, h('strong', null, title), h('br'), h('span', { class: 'muted small' }, note)), ctl);
    const airNow = AIR.findIndex(([, a]) => a.every((x, i) => Math.abs(x - (d.air[i] ?? 0)) < 0.02));
    return h('section', { class: 'room-card', 'aria-labelledby': 'tw-dir' },
      h('h3', { class: 'room-card-title', id: 'tw-dir' }, 'Theatre directives'),
      h('p', { class: 'muted small' }, d.held.length ? `You have taken ${d.held.length} ${d.held.length === 1 ? 'directive' : 'directives'} from the staff; the staff runs the rest.` : 'The staff runs the theatre by its playbook. A directive you give stays yours until you hand it back.'),
      tool('Strikes on the mainland', 'Limited: ports, embarkation areas, air bases and coastal launchers. Broad: missile forces, radars and command. Each climbs the ladder.',
        steps('Mainland strikes', ['None', 'Limited', 'Broad'], d.mainland, live, i => ({ type: 'Mainland', level: i }), 1)),
      tool('The carriers', 'Forward is within reach of China’s carrier-killer missiles; withdrawn is safe but far.',
        steps('Fleet posture', ['Forward', 'Stand-off', 'Withdrawn', 'Commander’s choice'], d.fleet, live, i => ({ type: 'Fleet', posture: i }))),
      tool('Attack submarines', 'In the strait they hunt the amphibious fleet; farther out they guard the chokepoints or blockade China’s trade.',
        steps('Submarines', ['The strait', 'Chokepoints', 'Distant blockade', 'Commander’s choice'], d.subs, live, i => ({ type: 'Submarines', focus: i }))),
      tool('Air priority', AIR_PARTS.map((p, i) => `${p} ${pct(d.air[i] ?? 0)}`).join(' · '),
        steps('Air priority', AIR.map(([n]) => n), airNow, live, i => { const a = AIR[i]![1]; return { type: 'AirPriority', antiShip: a[0], mainland: a[1], taiwanCap: a[2], fleetDefence: a[3] }; })),
      tool('Munitions', 'Conserving keeps a quarter of the long-range anti-ship missiles and SM-6s back; a surge spends them now.',
        steps('Munitions', ['Conserve', 'Normal', 'Surge'], d.munitions, live, i => ({ type: 'MunitionsPolicy', level: i }))),
      tool('Convoys to Taiwan', 'Escorts for the ships that keep Taiwan’s lights on, at the cost of escorts elsewhere.',
        h('button', { class: 'btn small toggle' + (d.convoys ? ' on' : ''), type: 'button', 'aria-pressed': String(d.convoys), disabled: !live, onclick: () => order({ type: 'Convoys', on: !d.convoys }) }, d.convoys ? 'Escorting' : 'Escort shipping')),
      v.phase === 'crisis' && v.maxRung >= 4 ? tool('A pre-emptive strike', 'Hit the invasion fleet in port before it sails, in twelve hours. America fires first, and the world will see it.',
        d.preempt ? h('button', { class: 'btn small', type: 'button', disabled: !live, onclick: () => order({ type: 'Preempt', strike: false }) }, 'Call it off')
          : grave('preempt', 'Strike first', { type: 'Preempt', strike: true }, !live)) : null,
      h('div', { class: 'cab-buttons' }, h('button', { class: 'btn small', type: 'button', disabled: !live || !d.held.length, onclick: () => order({ type: 'Playbook' }) }, 'Hand everything back to the staff')));
  }

  function chinaCard(v: TaiwanView, live: boolean): HTMLElement {
    const c = v.china;
    if (!coa) coa = { plan: c.plan || 'invasion', ...c.options, region: c.region, lethality: c.lethality, dDay: c.dDay > 0 ? c.dDay : v.day + 30 };
    const f = coa;
    const box = (k: 'strikeUS' | 'strikeJapan' | 'cyber' | 'decapitation' | 'penghuFirst', label: string) => h('label', { class: 'tw-check' },
      h('input', { type: 'checkbox', checked: f[k] ? 'checked' : null, disabled: !live || v.phase !== 'crisis', onchange: (e: Event) => { f[k] = (e.target as HTMLInputElement).checked; } }), ' ' + label);
    const pick = (label: string, names: string[], now: number, set: (i: number) => void) => h('div', { class: 'hz-steps', role: 'group', 'aria-label': label },
      ...names.map((n, i) => h('button', { class: 'btn small toggle' + (i === now ? ' on' : ''), type: 'button', 'aria-pressed': String(i === now), disabled: !live || v.phase !== 'crisis', onclick: () => { set(i); draw(); } }, n)));
    const crisis = v.phase === 'crisis';
    return h('section', { class: 'room-card', 'aria-labelledby': 'tw-coa' },
      h('h3', { class: 'room-card-title', id: 'tw-coa' }, 'The course of action'),
      h('p', null, c.stoodDown ? 'You have stood down.' : c.plan ? `Adopted: ${(PLAN_LABEL[c.plan] ?? c.plan).toLowerCase()}${c.dDay > 0 ? `, D-day on day ${c.dDay}` : ''}.` : 'No plan adopted yet.'),
      crisis ? h('div', null,
        pick('The plan', PLANS.map(p => PLAN_LABEL[p]!), PLANS.indexOf(f.plan), i => { f.plan = PLANS[i]!; }),
        h('div', { class: 'tw-checks' }, box('strikeUS', 'Strike American bases at H-hour'), box('strikeJapan', 'Strike bases in Japan'), box('cyber', 'Cyber and space attacks'),
          box('decapitation', 'Strike Taiwan’s leadership'), box('penghuFirst', 'Take Penghu first')),
        h('div', { class: 'sit-tool' }, h('strong', null, 'Main landings'), pick('Landing region', ['North', 'Centre', 'South'], f.region, i => { f.region = i; })),
        h('div', { class: 'sit-tool' }, h('strong', null, 'The blockade'), pick('Blockade lethality', ['Board and turn back', 'Submarines and mines', 'Sink'], f.lethality, i => { f.lethality = i; })),
        h('div', { class: 'cab-buttons' },
          h('button', { class: 'btn small primary', type: 'button', disabled: !live, onclick: () => order({ type: 'Plan', plan: f.plan, strikeUS: f.strikeUS, strikeJapan: f.strikeJapan, cyber: f.cyber,
            decapitation: f.decapitation, penghuFirst: f.penghuFirst, region: f.region, lethality: f.lethality }) }, 'Adopt the plan'),
          h('button', { class: 'btn small', type: 'button', disabled: !live || !c.plan, onclick: () => order({ type: 'Prepare' }) }, 'Start what the plan needs')),
        h('div', { class: 'sit-tool' }, h('label', { for: 'tw-dday' }, h('strong', null, 'D-day'), h('br'), h('span', { class: 'muted small' }, 'A day of the crisis; H-hour is 05:00.')),
          h('span', { class: 'cab-buttons' },
            h('input', { id: 'tw-dday', class: 'tw-num', type: 'number', min: String(v.day + 1), max: '110', value: String(f.dDay), disabled: !live, oninput: (e: Event) => { f.dDay = Number((e.target as HTMLInputElement).value); } }),
            grave('dday', 'Set D-day', { type: 'DDay', day: f.dDay }, !live || !c.plan))),
        h('div', { class: 'sit-tool' }, h('span', null, h('strong', null, 'Deception')),
          h('span', { class: 'cab-buttons' },
            h('button', { class: 'btn small', type: 'button', disabled: !live, onclick: () => order({ type: 'Deception', what: 'reports' }) }, 'Feed false reports'),
            h('button', { class: 'btn small', type: 'button', disabled: !live, onclick: () => order({ type: 'Deception', what: 'decoy' }) }, 'Send a decoy flotilla'))),
        h('div', { class: 'cab-buttons' }, grave('standdown', 'Stand down', { type: 'StandDown' }, !live || c.stoodDown)),
        h('p', { class: 'muted small' }, `Beijing’s reading of American deterrence: ${Math.round(c.deterrence)} (above 60, the planners advise against war).`))
        : h('p', { class: 'muted small' }, 'The course is set; the staff fights it. Your levers now are the ladder, the economy, the allies, nuclear posture and the terms.'));
  }

  function alliesCard(v: TaiwanView, live: boolean): HTMLElement {
    const us = v.side === 'US';
    const acts = us ? ACTIONS_US : ACTIONS_PRC;
    if (!dipCountry || !v.allies.some(a => a.id === dipCountry)) dipCountry = v.allies[0]?.id ?? '';
    if (!acts.some(([k]) => k === dipAction)) dipAction = acts[0]![0];
    return h('section', { class: 'room-card', 'aria-labelledby': 'tw-allies' },
      h('h3', { class: 'room-card-title', id: 'tw-allies' }, us ? 'Allies and partners' : 'The neighbours'),
      h('ul', { class: 'tw-allies' }, ...v.allies.map(a => h('li', null,
        h('span', { class: 'tw-ally-name' }, h('strong', null, a.name), h('span', { class: 'muted small' }, ` · ${a.levelName}${a.belligerent ? ' · at war' : ''}${a.basing && !a.belligerent ? ' · bases open' : ''}${a.attacked ? ' · attacked' : ''}${a.sanctions ? ' · sanctioning China' : ''}`)),
        us && !a.aligned ? h('span', { class: 'hz-steps', role: 'group', 'aria-label': `${a.name}: what we ask of it, ${ROLES[a.role] ?? ''}` }, ...ROLES.map((r, i) =>
          h('button', { class: 'btn small toggle' + (i === a.role ? ' on' : ''), type: 'button', 'aria-pressed': String(i === a.role), disabled: !live, onclick: () => order({ type: 'AllyRole', country: a.id, role: i }) }, r))) : null))),
      h('div', { class: 'sit-tool tw-dip' },
        h('label', { for: 'tw-dip-country' }, h('strong', null, 'Diplomacy'), h('br'), h('span', { class: 'muted small' }, `Capital ${Math.round(v.capital)} of 60; one approach to each country a day.`)),
        h('span', { class: 'cab-buttons' },
          h('select', { id: 'tw-dip-country', 'aria-label': 'Country', disabled: !live, onchange: (e: Event) => { dipCountry = (e.target as HTMLSelectElement).value; } },
            ...v.allies.map(a => h('option', { value: a.id, selected: a.id === dipCountry ? 'selected' : null }, a.name))),
          h('select', { 'aria-label': 'Approach', disabled: !live, onchange: (e: Event) => { dipAction = (e.target as HTMLSelectElement).value; } },
            ...acts.map(([k, n]) => h('option', { value: k, selected: k === dipAction ? 'selected' : null }, n))),
          h('button', { class: 'btn small', type: 'button', disabled: !live, onclick: () => order({ type: 'Diplomacy', country: dipCountry, action: dipAction }) }, 'Send'))),
      h('p', { class: 'muted small' }, us ? 'Basing and a fight are asked for, not ordered: each capital weighs its fear of China, its trust in America and what it stands to lose. A country attacked usually fights.'
        : 'Reassure the exposed, threaten the fearful, and keep Japan out if you can.'));
  }

  function programmesCard(v: TaiwanView, live: boolean): HTMLElement {
    const done = v.programmes.filter(p => p.pct >= 100);
    return h('section', { class: 'room-card', 'aria-labelledby': 'tw-prog' },
      h('h3', { class: 'room-card-title', id: 'tw-prog' }, 'Preparations'),
      done.length ? h('p', { class: 'small' }, h('strong', null, 'Done: '), done.map(p => p.name).join(' · ')) : null,
      h('ul', { class: 'sit-ops tw-progs' }, ...v.programmes.filter(p => p.pct < 100).map(p => h('li', null,
        h('div', null, h('strong', null, p.name), h('span', { class: 'muted small' }, ` · ${p.days} ${p.days === 1 ? 'day' : 'days'}`),
          p.pct > 0 ? meter(p.pct, 100, `${p.name}: ${Math.round(p.pct)}% done`, p.pct >= 100 ? 'good' : '', p.pct >= 100 ? 'done' : `${Math.round(p.pct)}%`) : null),
        p.pct >= 100 ? h('span', { class: 'muted small' }, 'Done')
          : h('button', { class: 'btn small toggle' + (p.on ? ' on' : ''), type: 'button', 'aria-pressed': String(p.on), disabled: !live, onclick: () => order({ type: 'Programme', id: p.id, run: !p.on }) }, p.on ? 'Under way' : 'Start')))),
      h('p', { class: 'muted small' }, v.side === 'US' ? 'Each preparation deters, and each is seen in Beijing: some calm the crisis, some make China hurry.'
        : 'Each preparation can be seen. Exercise cover hides some of them; what is seen raises America’s warning.'));
  }

  function economyCard(v: TaiwanView, live: boolean): HTMLElement {
    const e = v.economy, us = v.side === 'US';
    return h('section', { class: 'room-card', 'aria-labelledby': 'tw-econ' },
      h('h3', { class: 'room-card-title', id: 'tw-econ' }, 'The economy and sanctions'),
      h('dl', { class: 'ww-stats' },
        h('dt', null, 'Oil (Brent)'), h('dd', null, `$${Math.round(e.brent)} a barrel`),
        h('dt', null, 'Liquefied gas'), h('dd', null, `$${e.lng.toFixed(1)} per million BTU`),
        h('dt', null, 'American stocks'), h('dd', null, e.equity >= 0.995 ? 'Steady' : h('span', { class: e.equity < 0.85 ? 'warn' : '' }, `down ${pct(1 - e.equity)}`)),
        h('dt', null, 'America’s output'), h('dd', null, e.gapUS > 0.001 ? `${(e.gapUS * 100).toFixed(1)}% below its path` : 'On its path'),
        h('dt', null, 'The world’s output'), h('dd', null, e.gapWorld > 0.001 ? `${(e.gapWorld * 100).toFixed(1)}% below its path` : 'On its path'),
        h('dt', null, 'The chip shock'), h('dd', null, meter(e.chipShock * 100, 100, 'The chip shock, as the world feels it', e.chipShock > 0.4 ? 'bad' : '', pct(e.chipShock))),
        h('dt', null, us ? 'Support for defending Taiwan' : 'American support for Taiwan'), h('dd', null, meter(e.support, 100, 'Americans who support defending Taiwan', e.support < 35 ? 'bad' : ''))),
      h('ul', { class: 'sit-ops' }, ...(us ? MEASURES_US : MEASURES_PRC).map(([id, label, heavy]) => {
        const on = e.measures.includes(id);
        return h('li', null, h('strong', null, label), on ? h('span', { class: 'muted small' }, 'In force')
          : heavy ? grave('m-' + id, 'Impose', { type: 'Economic', measure: id }, !live)
            : h('button', { class: 'btn small', type: 'button', disabled: !live, onclick: () => order({ type: 'Economic', measure: id }) }, 'Impose'));
      })),
      h('p', { class: 'muted small' }, 'Sanctions deter before a war and punish during one, but every measure costs America and its allies too.'));
  }

  function nuclearCard(v: TaiwanView, live: boolean): HTMLElement {
    const n = v.nuclear, mine = v.side === 'US' ? n.us : n.prc, other = v.side === 'US' ? n.prc : n.us;
    return h('section', { class: 'room-card', 'aria-labelledby': 'tw-nuke' },
      h('h3', { class: 'room-card-title', id: 'tw-nuke' }, 'Nuclear risk and the terms'),
      n.crisis ? h('p', { class: 'warn', role: 'alert' }, h('strong', null, 'A nuclear crisis. '), 'Every step up the ladder now risks catastrophe.') : null,
      h('dl', { class: 'ww-stats' },
        h('dt', null, 'Nuclear risk'), h('dd', null, meter(n.risk, 100, 'Nuclear risk', n.risk > 60 ? 'bad' : n.risk > 30 ? 'warn' : '')),
        h('dt', null, v.side === 'US' ? 'China’s posture' : 'America’s posture'), h('dd', null, n.postures[other] ?? '')),
      h('div', { class: 'sit-tool' }, h('span', null, h('strong', null, 'Your nuclear posture')),
        steps('Nuclear posture', n.postures.map(x => x.charAt(0) + x.slice(1).toLowerCase()), mine, live, i => ({ type: 'Nuclear', alert: i }), 2)),
      v.phase === 'war' ? h('div', { class: 'sit-tool' }, h('span', null, h('strong', null, 'Propose a ceasefire'), h('br'), h('span', { class: 'muted small' }, 'The other side weighs its war, its losses and its people.')),
        h('span', { class: 'cab-buttons' },
          h('button', { class: 'btn small', type: 'button', disabled: !live || !!v.offer, onclick: () => order({ type: 'Ceasefire', terms: 0 }) }, 'The status quo before the war'),
          grave('cf1', 'A ceasefire in place', { type: 'Ceasefire', terms: 1 }, !live || !!v.offer),
          v.side === 'PRC' ? h('button', { class: 'btn small', type: 'button', disabled: !live || !!v.offer, onclick: () => order({ type: 'Ceasefire', terms: 2 }) }, 'Talks on unification') : null)) : null);
  }

  function tollCard(v: TaiwanView): HTMLElement {
    const rows = v.losses.filter(l => l.killed + l.ships + l.aircraft > 0);
    return h('section', { class: 'room-card', 'aria-labelledby': 'tw-toll' },
      h('h3', { class: 'room-card-title', id: 'tw-toll' }, 'The toll, and the log'),
      rows.length ? h('table', { class: 'tw-toll' },
        h('caption', { class: 'sr' }, 'Losses by nation'),
        h('thead', null, h('tr', null, h('th', { scope: 'col' }, ''), h('th', { scope: 'col' }, 'Killed'), h('th', { scope: 'col' }, 'Ships'), h('th', { scope: 'col' }, 'Aircraft'))),
        h('tbody', null, ...rows.map(l => h('tr', null, h('th', { scope: 'row' }, NATION_ADJ[l.nation] ?? l.label),
          h('td', null, num(l.killed)), h('td', null, l.ships + (l.carriers ? ` (${l.carriers} ${l.carriers === 1 ? 'carrier' : 'carriers'})` : '') + (l.amphibious ? ` (${l.amphibious} amphibious)` : '')),
          h('td', null, String(l.aircraft))))))
        : h('p', { class: 'muted small' }, 'No shots fired.'),
      v.sitrep.length ? h('div', null, h('h4', { class: 'tw-sub' }, 'This turn'), h('ul', { class: 'hz-log', tabindex: '0', 'aria-label': 'This turn' }, ...v.sitrep.slice(0, 10).map(l => h('li', null, l)))) : null,
      h('h4', { class: 'tw-sub' }, 'The log'),
      h('ul', { class: 'hz-log', tabindex: '0', 'aria-label': 'The log, newest first' }, ...v.log.slice(0, 14).map(l => h('li', null, l))));
  }

  draw();
  return root;
}
