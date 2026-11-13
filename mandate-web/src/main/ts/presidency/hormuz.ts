// The Strait of Hormuz crisis room (docs/HORMUZ.md): day by day, the ladder Iran climbs, a map of the strait (Iran's
// coastal sites as the intelligence community sees them, the minefields, the lanes, the terminals and pipelines, and
// the ships), the oil that still passes and what makes up for it, Iran's two decision-makers, and the president's
// tools: escorts and minesweepers, strikes on the coast, envoys, the oil levers, Israel, the public message and force.
import { h, s } from '../core/dom.js';
import { day } from '../core/format.js';
import type { HormuzSite, HormuzView } from '../core/types.js';
import { GULF, GULF_PROJECTION } from '../generated/gulf.js';

export interface HormuzActions {
  view(): HormuzView;
  /** A crisis order; false if refused (the refusal is shown). */
  order(o: object): boolean;
  /** Plays the day (answering the desk first); the room is redrawn after. */
  next(): void;
  back(): void;
}

const KIND: Record<HormuzSite['kind'], string> = {
  missiles: 'Anti-ship missile batteries', boats: 'Fast-boat bases', drones: 'Drone sites', mines: 'Mine layers',
};

// the map: the projection of the generated Gulf map, cropped to the strait and its approaches
const VIEW = { x: 200, y: 90, w: 800, h: 480 };
const P = GULF_PROJECTION;
const at = (lon: number, lat: number): [number, number] => [(lon - P.lon0) * P.cos * P.k, (P.lat1 - lat) * P.k];
const pts = (ll: [number, number][]) => ll.map(([lo, la]) => at(lo, la).map(v => v.toFixed(1)).join(',')).join(' ');
// the lanes from the Gulf of Oman round the Musandam peninsula into the Gulf (inbound north, outbound south)
const LANE: [number, number][] = [[58.6, 24.95], [57.5, 25.62], [56.95, 26.05], [56.62, 26.42], [56.3, 26.58], [55.8, 26.48], [55.0, 26.3], [53.6, 26.35], [52.2, 26.6], [50.9, 26.85]];
const shift = (l: [number, number][], d: number): [number, number][] => l.map(([lo, la]) => [lo + d * 0.6, la + d]);
const TERMINALS: [string, number, number][] = [['Ras Tanura', 50.16, 26.64], ['Ras Laffan', 51.54, 25.91], ['Fujairah', 56.36, 25.12], ['Kharg Island', 50.32, 29.23]];
const PLACES: [string, number, number][] = [['IRAN', 55.2, 28.4], ['OMAN', 57.4, 23.6], ['UNITED ARAB EMIRATES', 54.0, 23.9], ['QATAR', 51.2, 25.2], ['SAUDI ARABIA', 51.0, 23.75]];

/** -100 (restraint) .. 100 (escalation), as a bar either side of the middle. */
function lean(v: number, label: string): HTMLElement {
  const w = Math.min(50, Math.abs(v) / 2);
  return h('span', { class: 'sit-rel hz-lean', role: 'meter', 'aria-valuemin': '-100', 'aria-valuemax': '100', 'aria-valuenow': String(Math.round(v)), 'aria-label': label },
    h('span', { class: 'sit-rel-fill ' + (v > 0 ? 'neg' : 'pos'), style: v >= 0 ? `left:50%;width:${w}%` : `left:${50 - w}%;width:${w}%` }),
    h('span', { class: 'sit-rel-value' }, v > 15 ? 'escalate' : v < -15 ? 'de-escalate' : 'hold'));
}

function meter(v: number, max: number, label: string, tone = '', text = String(Math.round(v))): HTMLElement {
  return h('span', { class: 'hill-meter ' + tone, role: 'meter', 'aria-valuemin': '0', 'aria-valuemax': String(max), 'aria-valuenow': String(Math.round(v)), 'aria-label': label },
    h('span', { style: `width:${Math.max(2, Math.min(100, (v / max) * 100))}%` }), h('span', { class: 'hill-meter-value' }, text));
}

export function hormuzRoom(act: HormuzActions): HTMLElement {
  const root = h('div', { class: 'room hill sitroom hz' });
  let arming: string | null = null;
  const order = (o: object) => { act.order(o); arming = null; draw(); };
  /** A grave order takes two clicks: the first arms it, the second gives it. */
  const grave = (key: string, label: string, o: object, disabled: boolean) => arming === key
    ? h('span', { class: 'sit-confirm' }, h('button', { class: 'btn small danger', type: 'button', onclick: () => order(o) }, 'Confirm: ' + label.toLowerCase()),
      h('button', { class: 'btn small quiet', type: 'button', onclick: () => { arming = null; draw(); } }, 'Cancel'))
    : h('button', { class: 'btn small', type: 'button', disabled, onclick: () => { arming = key; draw(); } }, label);

  function draw(): void {
    const v = act.view();
    const live = v.open && v.active;
    const top = h('div', { class: 'room-top' },
      h('div', null, h('div', { class: 'eyebrow' }, 'The Situation Room'), h('h2', { class: 'dialog-title' }, 'The Strait of Hormuz'),
        v.open ? h('p', { class: 'muted' }, `${day(v.date, true)} · day ${v.day} of the crisis`) : null),
      h('div', { class: 'cab-buttons' },
        live ? h('button', { class: 'btn primary hz-next', type: 'button', onclick: () => act.next(), title: 'Play out the day (Ctrl+Enter)' }, 'Next day', h('span', { class: 'ov-end-arrow', 'aria-hidden': 'true' }, '›')) : null,
        h('button', { class: live ? 'btn' : 'btn primary', type: 'button', onclick: act.back }, 'Back to the Oval Office')));
    if (!v.open) { root.replaceChildren(top, h('p', null, v.reason ?? '')); return; }
    root.replaceChildren(...[top,
      h('p', { class: 'hz-cause' }, 'How it began: ' + v.cause + '.'),
      v.message ? h('p', { class: 'sit-message', role: 'status' }, v.message) : null,
      v.active ? null : h('p', { class: 'hz-over', role: 'note' }, `The crisis is over: ${(v.outcome ?? '').toLowerCase()}, after ${v.day - 1} days.`),
      ladder(v),
      h('div', { class: 'hz-top' }, mapCard(v), h('div', { class: 'hz-side' }, oilCard(v), iranCard(v))),
      h('div', { class: 'room-grid two hz-tools' }, seaCard(v, live), diplomacyCard(v, live), strikeCard(v, live), oilLevers(v, live), forceCard(v, live), tollCard(v)),
    ].filter((x): x is HTMLElement => x !== null));
  }

  function ladder(v: HormuzView): HTMLElement {
    return h('section', { class: 'room-card hz-ladder-card', 'aria-labelledby': 'hz-ladder' },
      h('h3', { class: 'room-card-title', id: 'hz-ladder' }, 'The ladder'),
      h('ol', { class: 'hz-ladder' }, ...v.rungs.map((r, i) => h('li', {
        class: 'hz-rung r' + i + (i === v.rung ? ' on' : '') + (i <= v.rung ? ' lit' : ''), 'aria-current': i === v.rung ? 'step' : null,
      }, h('span', { class: 'hz-rung-name' }, r.name), h('span', { class: 'hz-rung-desc' }, r.desc), i === v.peakRung && i !== v.rung ? h('span', { class: 'hz-peak' }, 'the worst so far') : null))));
  }

  function mapCard(v: HormuzView): HTMLElement {
    const lanes = [shift(LANE, 0.05), shift(LANE, -0.05)];
    const ring = (x: HormuzSite) => x.kind === 'missiles' ? 62 : x.kind === 'drones' ? 48 : 30;
    const mark = (x: HormuzSite) => {
      const [cx, cy] = at(x.lon, x.lat), live = x.seen >= 15, r = 7;
      const shape = x.kind === 'missiles' ? s('path', { d: `M${cx} ${cy - r}L${cx + r} ${cy + r * 0.8}L${cx - r} ${cy + r * 0.8}Z` })
        : x.kind === 'boats' ? s('path', { d: `M${cx} ${cy - r}L${cx + r} ${cy}L${cx} ${cy + r}L${cx - r} ${cy}Z` })
          : x.kind === 'drones' ? s('rect', { x: String(cx - r * 0.8), y: String(cy - r * 0.8), width: String(r * 1.6), height: String(r * 1.6) })
            : s('circle', { cx: String(cx), cy: String(cy), r: String(r * 0.85) });
      return s('g', { class: 'hz-site ' + x.kind + (live ? '' : ' out') },
        live && x.kind !== 'mines' && x.kind !== 'boats' ? s('circle', { class: 'hz-threat', cx: String(cx), cy: String(cy), r: String(ring(x) * x.seen / 100 + 8) }) : null,
        shape, s('title', null, `${x.name}: ${KIND[x.kind].toLowerCase()}, about ${x.seen}% left${x.struck ? `, struck ${x.struck} ${x.struck === 1 ? 'time' : 'times'}` : ''}`));
    };
    const [hx, hy] = at(53.6, 23.8), [ax, ay] = at(50.0, 25.9), [bx, by] = at(50.6, 26.2);
    const escorts = v.us.escorts, sweepers = v.us.sweepers + v.us.allies;
    const ships: SVGElement[] = [];
    for (let i = 0; i < escorts * 2; i++) {
      const k = 1 + i * (LANE.length - 3) / Math.max(1, escorts * 2);
      const a = LANE[Math.floor(k)]!, b = LANE[Math.min(LANE.length - 1, Math.floor(k) + 1)]!, t = k - Math.floor(k);
      const [x, y] = at(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t - 0.12);
      ships.push(s('path', { class: 'hz-ship', d: `M${x - 6} ${y + 3}L${x} ${y - 4}L${x + 6} ${y + 3}Z` }));
    }
    v.fields.slice(0, sweepers).forEach(f => { const [x, y] = at(f.lon + 0.12, f.lat + 0.1); ships.push(s('rect', { class: 'hz-sweeper', x: String(x - 4), y: String(y - 3), width: '8', height: '6' })); });
    const map = s('svg', { class: 'sit-map hz-map', viewBox: `${VIEW.x} ${VIEW.y} ${VIEW.w} ${VIEW.h}`, role: 'img',
      'aria-label': `The Strait of Hormuz on day ${v.day}: ${v.sites.filter(x => x.seen >= 15).length} of Iran's ${v.sites.length} coastal sites still active, `
        + `${v.fields.filter(f => f.known && (f.mines ?? 0) > 0).length} known minefields, ${escorts} escort ${escorts === 1 ? 'group' : 'groups'} in the lanes. The lists below give the details.` },
      s('rect', { x: String(VIEW.x), y: String(VIEW.y), width: String(VIEW.w), height: String(VIEW.h), class: 'hz-sea' }),
      ...GULF.map(c => s('path', { d: c.d, class: 'hz-land' + (c.id === 'IR' ? ' iran' : '') })),
      s('polyline', { points: pts([[53.6, 23.8], [55.2, 24.4], [56.36, 25.12]]), class: 'hz-pipe' + (v.oil.bypass > 0 ? ' on' : '') }),
      s('polyline', { points: pts([[50.05, 26.05], [49.6, 25.85]]), class: 'hz-pipe' + (v.oil.bypass > 0 ? ' on' : '') }),
      s('text', { x: String(ax + 4), y: String(ay + 26), class: 'hz-small' }, '← East-West to Yanbu'),
      s('text', { x: String(hx - 10), y: String(hy + 18), class: 'hz-small' }, 'pipeline'),
      ...lanes.map(l => s('polyline', { points: pts(l), class: 'hz-lane' })),
      ...v.fields.map(f => {
        const [x, y] = at(f.lon, f.lat), m = f.mines ?? 0;
        return s('g', { class: 'hz-field' + (f.known ? '' : ' unknown') },
          s('circle', { cx: String(x), cy: String(y), r: String(f.known ? 8 + Math.min(26, Math.sqrt(m) * 4) : 18) }),
          !f.known ? s('text', { x: String(x), y: String(y + 5), class: 'hz-q' }, '?') : null,
          s('title', null, f.known ? `${f.name}: about ${Math.round(m)} mines` : `${f.name}: mines may lie here`));
      }),
      ...v.sites.map(mark),
      ...TERMINALS.map(([n, lo, la]) => { const [x, y] = at(lo, la); return s('g', { class: 'hz-term' }, s('circle', { cx: String(x), cy: String(y), r: '4.5' }), s('text', { x: String(x + 8), y: String(y + 4), class: 'hz-small' }, n)); }),
      s('path', { class: 'hz-fleet', d: `M${bx} ${by - 7}L${bx + 2} ${by - 2}L${bx + 7} ${by - 2}L${bx + 3} ${by + 1}L${bx + 5} ${by + 7}L${bx} ${by + 3}L${bx - 5} ${by + 7}L${bx - 3} ${by + 1}L${bx - 7} ${by - 2}L${bx - 2} ${by - 2}Z` }),
      s('text', { x: String(bx + 10), y: String(by + 5), class: 'hz-small' }, 'Fifth Fleet'),
      ...ships,
      ...PLACES.map(([n, lo, la]) => { const [x, y] = at(lo, la); return s('text', { x: String(x), y: String(y), class: 'hz-place' }, n); }),
      s('text', { x: String(at(56.9, 26.95)[0]), y: String(at(56.9, 26.95)[1]), class: 'hz-strait' }, 'Strait of Hormuz'));
    const key = (cls: string, text: string) => h('li', null, h('span', { class: 'hz-key ' + cls, 'aria-hidden': 'true' }), text);
    return h('section', { class: 'room-card hz-map-card', 'aria-labelledby': 'hz-map-title' },
      h('h3', { class: 'room-card-title', id: 'hz-map-title' }, 'The strait'),
      map,
      h('ul', { class: 'hz-legend' }, key('missiles', 'Missile batteries (and their reach)'), key('boats', 'Boat bases'), key('drones', 'Drone sites'), key('mines', 'Mine layers'),
        key('field', 'Minefields (known; ? if not)'), key('ship', 'Escorts and minesweepers'), key('pipe', 'Bypass pipelines')),
      h('p', { class: 'muted small' }, 'Iran’s sites as the intelligence community sees them: fainter when little is left. Struck sites partly recover as launchers and boats come out of hiding.'));
  }

  function oilCard(v: HormuzView): HTMLElement {
    const o = v.oil, n = o.normal;
    const parts: [string, number, string][] = [['Through the strait', o.flow, 'flow'], ['Pipelines', o.bypass, 'bypass'], ['Reserves', o.spr + o.iea, 'reserve'], ['Missing', o.shortfall, 'short']];
    return h('section', { class: 'room-card', 'aria-labelledby': 'hz-oil' },
      h('h3', { class: 'room-card-title', id: 'hz-oil' }, 'The oil'),
      h('div', { class: 'hz-flow', role: 'img', 'aria-label': parts.map(([l, x]) => `${l} ${x.toFixed(1)}`).join(', ') + ` million barrels a day, of ${n} normally` },
        ...parts.filter(([, x]) => x > 0.05).map(([l, x, c]) => h('span', { class: 'hz-flow-part ' + c, style: `width:${(x / n) * 100}%`, title: `${l}: ${x.toFixed(1)} million barrels a day` }))),
      h('ul', { class: 'hz-flow-key' }, ...parts.map(([l, x, c]) => h('li', null, h('span', { class: 'hz-key ' + c, 'aria-hidden': 'true' }), `${l}: ${x.toFixed(1)}`))),
      h('p', { class: 'muted small' }, `Million barrels a day; ${n} pass the strait in normal times, a fifth of the world’s oil.`),
      h('dl', { class: 'ww-stats' },
        h('dt', null, 'Oil'), h('dd', null, o.price === null ? '—' : `$${Math.round(o.price)} a barrel (the crisis adds $${o.premium})`),
        h('dt', null, 'Gas'), h('dd', null, o.gas === null ? '—' : `$${o.gas.toFixed(2)} a gallon`),
        h('dt', null, 'War-risk insurance'), h('dd', null, o.insurance > 0 ? `$${o.insurance.toFixed(1)} a barrel${o.reinsured ? ' (federal reinsurance)' : ''}` : 'Normal'),
        h('dt', null, 'The reserve'), h('dd', null, meter(o.sprLeft, o.sprStart, 'Strategic Petroleum Reserve, million barrels left', o.sprLeft < 100 ? 'bad' : '', `${o.sprLeft} million barrels`))));
  }

  function iranCard(v: HormuzView): HTMLElement {
    return h('section', { class: 'room-card', 'aria-labelledby': 'hz-iran' },
      h('h3', { class: 'room-card-title', id: 'hz-iran' }, 'Iran'),
      h('p', null, v.iran.read),
      h('dl', { class: 'ww-stats' },
        h('dt', null, 'The Revolutionary Guards'), h('dd', null, lean(v.iran.guards, 'The Guards: de-escalate to escalate')),
        h('dt', null, 'The government in Tehran'), h('dd', null, lean(v.iran.tehran, 'The government: de-escalate to escalate')),
        h('dt', null, 'The regime’s stability'), h('dd', null, meter(v.iran.stability, 100, 'The regime’s stability', v.iran.stability < 35 ? 'bad' : ''))),
      h('p', { class: 'muted small' }, 'Iran climbs a rung when the Guards push and Tehran does not hold them back. Strikes anger the Guards; talks, Beijing and the regime’s own losses at sea restrain them.'));
  }

  function seaCard(v: HormuzView, live: boolean): HTMLElement {
    const steps = (now: number, max: number, type: string, label: (n: number) => string) =>
      h('div', { class: 'hz-steps', role: 'group', 'aria-label': label(now) },
        ...Array.from({ length: max + 1 }, (_, n) => h('button', { class: 'btn small toggle' + (n === now ? ' on' : ''), type: 'button', 'aria-pressed': String(n === now), disabled: !live,
          onclick: () => order({ type, groups: n }) }, String(n))));
    return h('section', { class: 'room-card', 'aria-labelledby': 'hz-sea' },
      h('h3', { class: 'room-card-title', id: 'hz-sea' }, 'At sea'),
      h('div', { class: 'sit-tool' }, h('span', null, h('strong', null, 'Escort groups'), h('br'), h('span', { class: 'muted small' }, 'Destroyers shepherd tankers through the lanes: fewer seizures, more oil, but the ships are within missile range.')),
        steps(v.us.escorts, 4, 'Escorts', n => `Escort groups: ${n}`)),
      h('div', { class: 'sit-tool' }, h('span', null, h('strong', null, 'Mine countermeasures'), h('br'), h('span', { class: 'muted small' }, 'Each group clears about two mines a day, known fields first. Slow, and the ships are exposed.')),
        steps(v.us.sweepers, 3, 'Sweep', n => `Minesweeping groups: ${n}`)),
      h('div', { class: 'sit-tool' }, h('span', null, h('strong', null, 'The allies’ minesweepers'), h('br'), h('span', { class: 'muted small' }, `Britain, France and Japan: ${v.us.allies} of 3 groups.`)),
        h('button', { class: 'btn small', type: 'button', disabled: !live || v.us.allies >= 3, onclick: () => order({ type: 'Envoy', channel: 'allies' }) }, 'Ask for more')));
  }

  function diplomacyCard(v: HormuzView, live: boolean): HTMLElement {
    const u = v.us, wait = (d: number) => d > 0 ? ` (in ${d} ${d === 1 ? 'day' : 'days'})` : '';
    return h('section', { class: 'room-card', 'aria-labelledby': 'hz-dip' },
      h('h3', { class: 'room-card-title', id: 'hz-dip' }, 'Diplomacy'),
      h('dl', { class: 'ww-stats' },
        h('dt', null, 'The back channel'), h('dd', null, meter(u.talks, 100, 'Talks with Tehran, toward a deal', u.settled ? 'good' : '', u.settled ? 'a deal' : `${u.talks} of 100`)),
        h('dt', null, 'Beijing’s pressure'), h('dd', null, meter(u.pressure, 100, 'Pressure from China and India on Tehran')),
        h('dt', null, 'The Gulf states'), h('dd', null, u.hostsConsent ? 'The bases are open to American strikes' : h('span', { class: 'warn' }, 'The bases are closed to American strikes'))),
      h('div', { class: 'cab-buttons' },
        h('button', { class: 'btn small primary', type: 'button', disabled: !live || u.settled || u.envoyIn > 0, onclick: () => order({ type: 'Envoy', channel: 'oman' }) }, 'Envoy through Muscat' + wait(u.envoyIn)),
        h('button', { class: 'btn small', type: 'button', disabled: !live || u.settled || u.envoyIn > 0, onclick: () => order({ type: 'Envoy', channel: 'qatar' }) }, 'Through Doha' + wait(u.envoyIn)),
        h('button', { class: 'btn small', type: 'button', disabled: !live || u.pressIn > 0, onclick: () => order({ type: 'Envoy', channel: 'china' }) }, 'Press Beijing' + wait(u.pressIn)),
        h('button', { class: 'btn small', type: 'button', disabled: !live || u.pressIn > 0, onclick: () => order({ type: 'Envoy', channel: 'india' }) }, 'Press New Delhi' + wait(u.pressIn)),
        h('button', { class: 'btn small', type: 'button', disabled: !live, onclick: () => order({ type: 'Envoy', channel: 'gulf' }) }, 'Reassure Riyadh and Abu Dhabi')),
      h('div', { class: 'sit-tool' }, h('span', null, h('strong', null, 'The public message')),
        h('span', { class: 'cab-buttons' },
          h('button', { class: 'btn small', type: 'button', disabled: !live, onclick: () => order({ type: 'Message', firm: true }) }, 'Firm: the strait stays open'),
          h('button', { class: 'btn small', type: 'button', disabled: !live, onclick: () => order({ type: 'Message', firm: false }) }, 'Calm: the door to talks is open'))),
      h('div', { class: 'sit-tool' }, h('span', null, h('strong', null, 'Israel'), h('br'), h('span', { class: 'muted small' }, u.israelRestrained ? 'Holding its fire at your request.' : 'Has struck Iran’s nuclear sites.')),
        u.israelRestrained ? grave('israel', 'Let Israel strike', { type: 'Israel', restrain: false }, !live) : h('span', { class: 'muted small' }, 'Done')),
      h('p', { class: 'muted small' }, 'An envoy takes days to go and come back. A deal costs relief from sanctions; the Guards may grumble, but the ladder falls and Iran hands over its minefield charts.'));
  }

  function strikeCard(v: HormuzView, live: boolean): HTMLElement {
    const kinds: HormuzSite['kind'][] = ['missiles', 'boats', 'mines', 'drones'];
    return h('section', { class: 'room-card', 'aria-labelledby': 'hz-strike' },
      h('h3', { class: 'room-card-title', id: 'hz-strike' }, 'Strikes on the coast'),
      h('ul', { class: 'sit-ops' }, ...kinds.map(k => {
        const xs = v.sites.filter(x => x.kind === k), left = Math.round(xs.reduce((a, x) => a + x.seen, 0) / Math.max(1, xs.length));
        return h('li', null, h('div', null, h('strong', null, KIND[k]),
          h('p', { class: 'small' }, xs.map(x => `${x.name.replace(/ (missile batteries|naval base)$/, '')} ${x.seen}%`).join(' · ')),
          meter(left, 100, `${KIND[k]}: about ${left}% left`, left < 30 ? 'good' : '', `${left}% left`)),
          grave('strike-' + k, 'Strike', { type: 'Strike', kind: k }, !live));
      })),
      h('p', { class: 'muted small' }, `Strikes so far: ${v.toll.strikes}. Each wears down what Iran can throw at the strait, angers the Guards and sets back the talks.`
        + (v.us.hostsConsent ? '' : ' With the Gulf bases closed, only the carriers fly.')));
  }

  function oilLevers(v: HormuzView, live: boolean): HTMLElement {
    const o = v.oil;
    return h('section', { class: 'room-card', 'aria-labelledby': 'hz-levers' },
      h('h3', { class: 'room-card-title', id: 'hz-levers' }, 'The oil levers'),
      h('div', { class: 'sit-tool' }, h('span', null, h('strong', null, 'The Strategic Petroleum Reserve'), h('br'), h('span', { class: 'muted small' }, `Releasing ${o.spr.toFixed(1)} million barrels a day; ${o.sprLeft} million left.`)),
        h('span', { class: 'hz-steps', role: 'group', 'aria-label': 'Reserve release, million barrels a day' }, ...[0, 1, 2, 3, 4.4].map(x =>
          h('button', { class: 'btn small toggle' + (Math.abs(o.spr - x) < 0.05 ? ' on' : ''), type: 'button', 'aria-pressed': String(Math.abs(o.spr - x) < 0.05), disabled: !live || (x > 0 && o.sprLeft <= 0),
            onclick: () => order({ type: 'Oil', lever: 'spr', amount: x }) }, String(x))))),
      h('div', { class: 'sit-tool' }, h('span', null, h('strong', null, 'A coordinated IEA release'), h('br'), h('span', { class: 'muted small' }, 'Two million barrels a day from the allies’ stocks.')),
        h('button', { class: 'btn small toggle' + (o.iea > 0 ? ' on' : ''), type: 'button', 'aria-pressed': String(o.iea > 0), disabled: !live, onclick: () => order({ type: 'Oil', lever: 'iea', amount: 0 }) }, o.iea > 0 ? 'Releasing' : 'Ask the IEA')),
      h('div', { class: 'sit-tool' }, h('span', null, h('strong', null, 'The bypass pipelines'), h('br'), h('span', { class: 'muted small' }, o.bypassAsked ? `Carrying ${o.bypass.toFixed(1)} of ${o.bypassTarget.toFixed(1)} million barrels a day.` : 'East-West to Yanbu and Habshan-Fujairah: about three million barrels a day.')),
        h('button', { class: 'btn small', type: 'button', disabled: !live || o.bypassAsked, onclick: () => order({ type: 'Oil', lever: 'bypass', amount: 0 }) }, o.bypassAsked ? 'Open' : 'Ask to open them')),
      h('div', { class: 'sit-tool' }, h('span', null, h('strong', null, 'Federal war-risk reinsurance'), h('br'), h('span', { class: 'muted small' }, 'Brings insurers back and cuts the cost of every barrel shipped.')),
        h('button', { class: 'btn small toggle' + (o.reinsured ? ' on' : ''), type: 'button', 'aria-pressed': String(o.reinsured), disabled: !live, onclick: () => order({ type: 'Oil', lever: 'reinsure', amount: 0 }) }, o.reinsured ? 'In force' : 'Reinsure tankers')));
  }

  function forceCard(v: HormuzView, live: boolean): HTMLElement {
    const inv = v.invasion;
    const stage = ['', 'The build-up', 'The fighting', 'Done'][inv.stage] ?? '';
    return h('section', { class: 'room-card', 'aria-labelledby': 'hz-force' },
      h('h3', { class: 'room-card-title', id: 'hz-force' }, 'Ground forces'),
      inv.stage > 0
        ? h('div', null, h('p', { class: 'sit-stage' }, `${inv.plans[inv.option]!.label}: ${stage.toLowerCase()}`),
          inv.stage === 2 ? meter(inv.progress, 100, 'Progress of the campaign', '', `${inv.progress}%`) : h('p', { class: 'small' }, inv.stage === 1 ? `${inv.days} days into the build-up.` : 'The objective is held.'))
        : h('ul', { class: 'sit-ops' }, ...inv.plans.map((p, i) => h('li', null, h('div', null, h('strong', null, p.label), h('p', { class: 'small' }, p.detail)),
          grave('invade-' + i, 'Order it', { type: 'Invade', option: i }, !live)))));
  }

  function tollCard(v: HormuzView): HTMLElement {
    const t = v.toll;
    return h('section', { class: 'room-card', 'aria-labelledby': 'hz-toll' },
      h('h3', { class: 'room-card-title', id: 'hz-toll' }, 'The toll, and the log'),
      h('dl', { class: 'ww-stats' },
        h('dt', null, 'Americans killed'), h('dd', null, t.killed ? h('strong', { class: 'warn' }, String(t.killed)) : 'None'),
        h('dt', null, 'American ships hit'), h('dd', null, String(t.shipsHit)),
        h('dt', null, 'Tankers seized'), h('dd', null, String(t.seized)),
        h('dt', null, 'Tankers hit'), h('dd', null, String(t.tankersHit))),
      h('ul', { class: 'hz-log' }, ...v.log.slice(0, 12).map(l => h('li', null, l))));
  }

  draw();
  return root;
}
