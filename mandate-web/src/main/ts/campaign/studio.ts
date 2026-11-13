// The Ad Studio, as in Battleground 2028: make an ad (tone, issue, audience, messenger), show it to a focus group for a
// noisy read of how good it really is, and put the best on the air: every TV and digital dollar then works for that ad.
import type { CampaignView, StudioAd } from '../core/types.js';
import { h } from '../core/dom.js';
import { openMenu } from '../core/menu.js';
import { cost } from './schedule.js';

export interface StudioActions { order(order: object): boolean; close(): void; /** Redraws after a change to the form. */ refresh(): void; }

/** The form and the selected ad persist while the studio is open. */
const form = { tone: 0, issue: 'COST_OF_LIVING', group: -1, messenger: 'CANDIDATE', selected: -1 };

const TONE_HELP = ['About you: biography and vision.', 'You against them, side by side.', 'A straight hit on your opponent.'];

export function adStudio(v: CampaignView, act: StudioActions): HTMLElement {
  const ads = v.ads!, st = ads.studio, lib = ads.library;
  if (form.selected >= lib.length) form.selected = lib.length - 1;
  const issueLabel = st.issues.find(i => i.id === form.issue)?.label ?? form.issue;
  const make = h('section', { class: 'studio-col', 'aria-label': 'Make an ad' },
    h('h3', { class: 'sub-head' }, 'Make an ad'),
    h('span', { class: 'field-label' }, 'Tone'),
    h('div', { class: 'seg', role: 'group', 'aria-label': 'Tone' }, ...st.tones.map((t, i) => h('button', { type: 'button', class: 'seg-btn' + (i === form.tone ? ' on' : ''),
      'aria-pressed': i === form.tone ? 'true' : 'false', title: TONE_HELP[i] ?? '', onclick: () => { form.tone = i; act.refresh(); } }, t))),
    h('span', { class: 'field-label' }, 'Issue'),
    h('button', { type: 'button', class: 'btn wide-btn', 'aria-label': `The ad's issue: ${issueLabel}. Change`,
      onclick: (e: Event) => openMenu(e.currentTarget as HTMLElement, 'The ad’s issue', st.issues.map(i => ({ label: i.label, pick: () => { form.issue = i.id; act.refresh(); } }))) }, issueLabel),
    h('span', { class: 'field-label' }, 'Audience'),
    h('button', { type: 'button', class: 'btn wide-btn', 'aria-label': `The ad's audience: ${form.group < 0 ? 'Everyone' : st.groups[form.group]}. Change`,
      title: 'Targeted ads move their group more; general ads reach everyone a little.',
      onclick: (e: Event) => openMenu(e.currentTarget as HTMLElement, 'Who is it for?', [{ label: 'Everyone', pick: () => { form.group = -1; act.refresh(); } },
        ...st.groups.map((g, i) => ({ label: g, pick: () => { form.group = i; act.refresh(); } }))]) }, form.group < 0 ? 'Everyone' : st.groups[form.group]!),
    h('span', { class: 'field-label' }, 'Messenger'),
    h('div', { class: 'messengers', role: 'group', 'aria-label': 'Messenger' }, ...st.messengers.map(m => h('button', { type: 'button',
      class: 'messenger' + (m.id === form.messenger ? ' on' : ''), 'aria-pressed': m.id === form.messenger ? 'true' : 'false',
      onclick: () => { form.messenger = m.id; act.refresh(); } }, h('strong', null, m.label), h('span', null, m.description)))),
    h('button', { type: 'button', class: 'btn primary wide-btn', onclick: () => {
      // select the new ad before the studio redraws (the library keeps the newest eight)
      const before = form.selected;
      form.selected = Math.min(lib.length, 7);
      if (!act.order({ type: 'ProduceAd', tone: form.tone, issue: form.issue, group: form.group, messenger: form.messenger })) { form.selected = before; act.refresh(); }
    } }, `Produce the ad (${cost(st.produceCost)})`),
    h('p', { class: 'hint-line' }, 'Your credibility on the issue, and how much voters care about it, shape how good the ad really is. You won’t know until you test it, or air it.'));

  const list = h('section', { class: 'studio-col', 'aria-label': 'Your ads' },
    h('h3', { class: 'sub-head' }, 'Your ads'),
    lib.length ? h('div', { class: 'ad-list' }, ...lib.map((a, i) => ({ a, i })).reverse().map(({ a, i }) => h('button', { type: 'button',
      class: 'ad-card' + (i === form.selected ? ' on' : ''), 'aria-pressed': i === form.selected ? 'true' : 'false',
      onclick: () => { form.selected = i; act.refresh(); } },
      h('span', { class: 'ad-name' }, a.name),
      h('span', { class: 'faint' }, st.messengers.find(m => m.id === a.messenger)?.label ?? a.messenger),
      h('span', { class: 'ad-meta' }, i === ads.airing ? h('span', { class: 'pill tone-good' }, 'On the air') : null,
        h('span', { class: score(a) }, a.tested === null ? 'Not tested' : `Focus group: ${Math.round(a.tested * 100)}/100`)))))
      : h('p', { class: 'muted' }, 'No ads yet.'),
    h('p', { class: 'hint-line' }, 'The library keeps your eight newest ads.'));

  const a = form.selected >= 0 ? lib[form.selected] : ads.airing >= 0 ? lib[ads.airing] : undefined;
  const ai = a ? lib.indexOf(a) : -1;
  const detail = h('section', { class: 'studio-col', 'aria-label': 'Focus group' }, h('h3', { class: 'sub-head' }, 'Focus group'),
    ...(a ? focusGroup(a, ai, v, act) : [h('p', { class: 'muted' }, 'Pick an ad to test or air.')]));

  return h('div', { class: 'dialog-card studio' },
    h('div', { class: 'studio-head' }, h('div', null, h('div', { class: 'eyebrow' }, 'The Ad Studio'),
      h('h2', { class: 'dialog-title' }, 'Make your own ads'),
      h('p', { class: 'muted' }, 'Pick the tone, the issue, the audience and the messenger. Test with a focus group, then put the best on the air.')),
      h('button', { class: 'btn quiet close', type: 'button', 'aria-label': 'Close the Ad Studio', onclick: () => act.close() }, '×')),
    h('div', { class: 'studio-cols' }, make, list, detail));
}

function score(a: StudioAd): string { return a.tested === null ? 'faint' : a.tested > 0.6 ? 'good' : a.tested > 0.4 ? 'warn' : 'bad'; }

function focusGroup(a: StudioAd, index: number, v: CampaignView, act: StudioActions): HTMLElement[] {
  const ads = v.ads!, st = ads.studio, onAir = index === ads.airing;
  const out: HTMLElement[] = [h('p', { class: 'ad-title' }, a.name)];
  if (a.tested === null) {
    out.push(h('p', { class: 'hint-line' }, 'Show it to a dozen persuadable voters behind the glass and see how they react.'),
      h('button', { type: 'button', class: 'btn wide-btn', onclick: () => act.order({ type: 'TestAd', index }) }, `Run a focus group (${cost(st.testCost)})`));
  } else {
    const t = a.tested;
    out.push(h('div', { class: 'proj-meter' }, h('span', { class: 'proj-label' }, 'Overall score'),
      h('span', { class: 'proj-bar', role: 'meter', 'aria-label': 'Overall score', 'aria-valuemin': 0, 'aria-valuemax': 100, 'aria-valuenow': Math.round(t * 100) },
        h('span', { class: 'proj-fill ' + (t > 0.6 ? 'ok' : t > 0.4 ? 'prep' : 'bad'), style: `width:${t * 100}%` })),
      h('strong', { class: 'proj-value' }, String(Math.round(t * 100)))));
    out.push(h('div', { class: 'group-scores' }, ...st.keyGroups.map((g, i) => {
      const sc = a.groupScores[i] ?? 0;
      return h('div', { class: 'gs-row', role: 'img', 'aria-label': `${g}: ${sc >= 0 ? 'liked it' : 'disliked it'} (${Math.round(sc * 100)})` },
        h('span', { class: 'gs-name' }, g),
        h('span', { class: 'gs-track' }, h('span', { class: 'gs-fill ' + (sc >= 0 ? 'pos' : 'neg'), style: sc >= 0 ? `left:50%;width:${sc * 50}%` : `left:${50 + sc * 50}%;width:${-sc * 50}%` })));
    })));
    out.push(h('div', { class: 'quotes' }, ...a.quotes.map(q => h('blockquote', null, q))));
  }
  out.push(h('button', { type: 'button', class: 'btn ' + (onAir ? '' : 'primary ') + 'wide-btn', 'aria-disabled': onAir ? 'true' : null,
    title: 'Your TV and digital buys run this ad from now on. Good ads make every dollar work harder.',
    onclick: () => { if (!onAir) act.order({ type: 'AirAd', index }); } }, onAir ? 'On the air now' : 'Put it on the air'));
  return out;
}
