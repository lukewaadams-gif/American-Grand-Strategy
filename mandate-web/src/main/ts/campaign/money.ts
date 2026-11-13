// Money and the air war, as in Battleground 2028: the Money tab holds cash, the donor base, the headquarters, national TV
// and the digital plan, with this week's budget; a state's Ads desk buys rating points in each of its TV markets, sets the
// state's digital spending and signals the allied super PACs. Buys are standing orders: they repeat every week.
import type { CampaignView } from '../core/types.js';
import { h, s } from '../core/dom.js';
import { openMenu } from '../core/menu.js';
import { cost } from './schedule.js';
import { compact, rowsOf, stepper } from './field.js';

export interface MoneyActions {
  order(order: object): boolean;
  studio(): void;
}

export function moneyTab(v: CampaignView, act: MoneyActions): HTMLElement {
  const m = v.money!, staffOn = v.staff?.ads ?? false, ads = v.ads!;
  const tapped = m.donorsTapped / Math.max(1, m.donorCap);
  const airing = ads.airing >= 0 ? ads.library[ads.airing] : undefined;
  const over = m.budgetTotal > m.cash;
  return h('section', { class: 'tab-panel', 'aria-label': 'Money' },
    h('div', { class: 'panel-head' }, h('h2', null, 'Money')),
    h('div', { class: 'cash-row' },
      h('div', null, h('span', { class: 'big-value' }, cost(m.cash)), h('span', { class: 'kpi-sub' }, ' cash on hand')),
      h('div', { class: 'cash-flow' }, h('span', { class: 'up' }, `+${cost(m.raised)} raised`), h('span', { class: 'down' }, `−${cost(m.spent)} spent`),
        h('span', { class: 'faint' }, 'last turn'))),
    flowChart(m.history),
    h('div', { class: 'proj-meter', title: 'Big donors max out: the more you have tapped, the less each fundraiser and call-time session raises.' },
      h('span', { class: 'proj-label' }, 'Big donors tapped'),
      h('span', { class: 'proj-bar', role: 'meter', 'aria-label': 'Big donors tapped', 'aria-valuemin': 0, 'aria-valuemax': 100, 'aria-valuenow': Math.round(tapped * 100) },
        h('span', { class: 'proj-fill ' + (tapped > 0.8 ? 'bad' : 'prep'), style: `width:${Math.min(1, tapped) * 100}%` })),
      h('strong', { class: 'proj-value' }, Math.round(Math.min(1, tapped) * 100) + '%')),
    rowsOf([['Small-dollar list', compact(m.smallList) + ' names'], ['Raised so far', cost(m.raisedTotal)], ['Spent so far', cost(m.spentTotal)]]),

    h('h3', { class: 'sub-head' }, 'Headquarters and staff'),
    h('div', { class: 'seg', role: 'group', 'aria-label': 'Headquarters' }, ...m.tiers.map((t, i) => h('button', { type: 'button', class: 'seg-btn' + (i === m.hq ? ' on' : ''),
      'aria-pressed': i === m.hq ? 'true' : 'false', title: `${t.name}: ${cost(t.perWeek)} a week. ` + (i === 0 ? 'Cheap, but ads and events are a little less effective.'
        : i === 2 ? 'The best data, ad-makers and advance teams money can buy.' : 'A professional operation.'),
      onclick: () => act.order({ type: 'Headquarters', tier: i }) }, t.name, h('span', { class: 'seg-sub' }, cost(t.perWeek) + '/wk')))),

    h('h3', { class: 'sub-head' }, 'National TV (weekly rating points)'),
    ...m.national.map(n => h('div', { class: 'buy-row' },
      h('div', { class: 'buy-text' }, h('span', null, n.label), h('span', { class: 'faint' }, `${n.perWeek > 0 ? cost(n.perWeek) + ' a week' : 'not on the air'} · ${cost(n.costPerPoint)} a point`)),
      stepper(`National TV: ${n.label}`, String(n.grps),
        () => act.order({ type: 'NationalTv', network: n.id, grps: Math.max(0, n.grps - 25) }),
        () => act.order({ type: 'NationalTv', network: n.id, grps: Math.min(400, n.grps + 25) }), null))),

    h('h3', { class: 'sub-head' }, 'Digital: platform mix and message'),
    ...m.platforms.map((p, i) => h('div', { class: 'plat-row', title: `${p.description} About $${p.cpm} per thousand impressions.` },
      h('span', { class: 'plat-name' }, p.label, h('span', { class: 'faint' }, ' ' + p.skew)),
      stepper(`${p.label} share of digital`, Math.round(p.share * 100) + '%', () => mix(v, i, -0.05, act), () => mix(v, i, 0.05, act), null),
      h('button', { type: 'button', class: 'btn small msg', 'aria-label': `${p.label} ads say: ${label(v, p.message)}. Change`,
        onclick: (e: Event) => openMenu(e.currentTarget as HTMLElement, `${p.label} ads say…`, m.messages.map(x => ({
          label: x.label, detail: x.description, pick: () => act.order({ type: 'PlatformMessage', platform: p.id, message: x.id }) }))) }, label(v, p.message)))),
    h('p', { class: 'hint-line' }, `Audience: ${Math.round(m.audience[0] * 100)}% under 30, ${Math.round(m.audience[1] * 100)}% 30–64, ${Math.round(m.audience[2] * 100)}% 65+ · $${m.cpm.toFixed(0)} per 1,000`),
    h('div', { class: 'sched-controls' }, h('button', { class: 'btn small', type: 'button', onclick: () => act.order({ type: 'DigitalMix', shares: m.platforms.map(() => 1) }) }, 'Even mix')),

    h('h3', { class: 'sub-head' }, 'This week’s budget'),
    rowsOf(m.budget.map(b => [b.label, cost(b.amount)] as [string, string])),
    h('div', { class: 'budget-total' + (over ? ' over' : '') }, h('span', null, 'Total'), h('strong', null, cost(m.budgetTotal))),
    over ? h('p', { class: 'warn-line' }, 'More than you have: some ads will be pulled.') : null,
    h('div', { class: 'sched-controls' },
      h('button', { class: 'btn small', type: 'button', onclick: () => act.order({ type: 'PullAds', tv: true, digital: false }) }, 'Pull all TV'),
      h('button', { class: 'btn small', type: 'button', onclick: () => act.order({ type: 'PullAds', tv: false, digital: true }) }, 'Pull all digital')),
    h('div', { class: 'sched-controls' }, h('button', { class: 'btn toggle' + (staffOn ? ' on' : ''), type: 'button', 'aria-pressed': staffOn ? 'true' : 'false',
      title: 'When on, your ad team places TV and digital every Monday', onclick: () => act.order({ type: 'Staff', area: 'ADS', on: !staffOn }) },
      'Staff runs the ads: ', h('strong', null, staffOn ? 'on' : 'off'))),
    staffOn ? h('p', { class: 'hint-line' }, 'Your ad team replans every Monday; turn it off to keep your own buys.') : null,
    h('h3', { class: 'sub-head' }, 'The Ad Studio'),
    h('p', { class: 'hint-line' }, airing ? `On the air: ${airing.name}.` : 'No ad of your own on the air.'),
    h('div', { class: 'sched-controls' }, h('button', { class: 'btn', type: 'button', onclick: () => act.studio() }, 'Open the Ad Studio')));
}

function label(v: CampaignView, id: string): string { return v.money!.messages.find(x => x.id === id)?.label ?? id; }

/** BG Digital.adjust: one platform's share moves by step; the others make room in proportion. */
function mix(v: CampaignView, i: number, step: number, act: MoneyActions): void {
  const cur = v.money!.platforms.map(p => p.share);
  const nv = Math.max(0, Math.min(1, cur[i]! + step));
  const others = cur.reduce((a, b, k) => a + (k === i ? 0 : b), 0), left = 1 - nv;
  const next = cur.map((x, k) => k === i ? nv : others > 0 ? x / others * left : left / (cur.length - 1));
  act.order({ type: 'DigitalMix', shares: next.map(x => Math.round(x * 1000) / 1000) });
}

function flowChart(history: [number, number][]): HTMLElement {
  const rows = history.slice(-26), n = rows.length, W = 360, H = 70;
  const max = Math.max(1, ...rows.flat());
  const svg = s('svg', { viewBox: `0 0 ${W} ${H}`, class: 'mini-bars', role: 'img',
    'aria-label': n ? `Raised and spent, turn by turn: last turn raised ${cost(rows[n - 1]![0])}, spent ${cost(rows[n - 1]![1])}` : 'No turns yet' });
  const bw = n ? Math.min(14, (W - 4) / n) : 0;
  rows.forEach(([r, sp], i) => {
    const x = 2 + i * bw;
    svg.append(s('rect', { x, y: H - (r / max) * (H - 4), width: Math.max(1, bw / 2 - 1), height: (r / max) * (H - 4), class: 'bar raised' }),
      s('rect', { x: x + bw / 2, y: H - (sp / max) * (H - 4), width: Math.max(1, bw / 2 - 1), height: (sp / max) * (H - 4), class: 'bar spent' }));
  });
  return h('figure', { class: 'chart mini' }, svg, h('figcaption', { class: 'chart-caption' },
    h('span', { class: 'key raised' }, 'raised'), ' and ', h('span', { class: 'key spent' }, 'spent'), ', turn by turn'));
}

const TONES: [string, string, string][] = [['POSITIVE', 'Pos', 'Positive: builds your image. Safe.'],
  ['CONTRAST', 'Contrast', 'Contrast: you against them. Effective, with moderate risk.'],
  ['NEGATIVE', 'Neg', 'Negative: defines your opponent. The most powerful, and backlash if overused.']];

/** The state desk's Ads tab. */
export function adsDesk(code: string, v: CampaignView, act: MoneyActions): (HTMLElement | null)[] {
  const ads = v.ads!, st = ads.states.find(x => x.code === code);
  const name = v.states.find(x => x.code === code)?.name ?? code;
  if (!st) return [h('p', { class: 'muted' }, 'No ad data for this state.')];
  const markets = st.markets.map(id => ads.markets[id]!);
  return [
    h('h4', null, 'Broadcast TV by media market'),
    h('p', { class: 'hint-line' }, 'Weekly gross rating points: 100 means the average viewer sees the ad once. 600 to 1,000 a week is a heavy buy; 1,500 is saturation. Prices rise toward November.'),
    ...markets.map(mk => {
      const reach = mk.reach.filter(([, sh]) => sh > 0.02).map(([c, sh]) => `${c} ${Math.round(sh * 100)}%`).join('  ');
      return h('div', { class: 'market' + (mk.grps > 0 ? ' on' : '') },
        h('div', { class: 'market-head' }, h('strong', null, mk.name), h('span', { class: 'faint' }, cost(mk.costPerPoint) + ' a point')),
        h('div', { class: 'market-reach faint' }, `${compact(mk.households * 1000)} homes · ${reach}`),
        h('div', { class: 'market-buy' },
          stepper(`Weekly rating points in ${mk.name}`, String(Math.round(mk.grps)),
            () => act.order({ type: 'Airtime', market: mk.id, grps: Math.max(0, Math.round(mk.grps) - 100), tone: mk.tone }),
            () => act.order({ type: 'Airtime', market: mk.id, grps: Math.min(2000, Math.round(mk.grps) + 100), tone: mk.tone }), null),
          h('div', { class: 'seg small', role: 'group', 'aria-label': `Tone in ${mk.name}` }, ...TONES.map(([id, short, title]) => h('button', { type: 'button',
            class: 'seg-btn' + (mk.tone === id ? ' on' : ''), 'aria-pressed': mk.tone === id ? 'true' : 'false', title,
            onclick: () => act.order({ type: 'Airtime', market: mk.id, grps: mk.grps, tone: id }) }, short))),
          h('strong', { class: 'market-cost' + (mk.grps > 0 ? ' on' : '') }, mk.grps > 0 ? cost(mk.perWeek) + '/wk' : 'not on the air')));
    }),
    h('h4', null, `Digital in ${name}`),
    h('p', { class: 'hint-line' }, `${st.impressions.toFixed(1)} impressions per adult per week. Platforms and messages: the Money tab.`),
    h('div', { class: 'stepper-row' },
      stepper(`Weekly digital spending in ${name}`, cost(st.digital) + '/wk',
        () => act.order({ type: 'Digital', state: code, perWeek: Math.max(0, Math.round((st.digital - 0.025) * 1000) / 1000) }),
        () => act.order({ type: 'Digital', state: code, perWeek: Math.min(5, Math.round((st.digital + 0.025) * 1000) / 1000) }), null,
        h('button', { class: 'btn small', type: 'button', 'aria-label': `Add $100K a week of digital in ${name}`,
          onclick: () => act.order({ type: 'Digital', state: code, perWeek: Math.min(5, Math.round((st.digital + 0.1) * 1000) / 1000) }) }, '+$100K'))),
    h('h4', null, 'Allied super PACs'),
    h('p', { class: 'hint-line' }, st.superPac > 1 ? `Your allies are on the air here: about ${st.superPac} rating points in the pipeline.` : 'No allied spending here yet. Red-box the state to signal them.'),
    h('button', { class: 'btn small toggle' + (st.redBox ? ' on' : ''), type: 'button', 'aria-pressed': st.redBox ? 'true' : 'false',
      title: 'A "red box" on the campaign website: allied super PACs read it and spend more here',
      onclick: () => act.order({ type: 'RedBox', state: code, on: !st.redBox }) }, st.redBox ? 'Red-boxed' : 'Red-box this state'),
  ];
}
