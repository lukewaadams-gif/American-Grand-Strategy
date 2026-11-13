// The engine: the Java simulation compiled to JavaScript (target/preview/engine/engine.js), behind one call that
// takes and returns JSON. Everything the interface shows comes from its view models; everything it does is a command.
import type { CabinetView, HillView, WestWingView, WwPerson, PressView, ResidenceView, TermsView, VerdictView, SitRoomView, ForeignCountryView, HormuzView, TaiwanView, ElectionNightView, TransitionView, CampaignView, CountryView, Decision, EconomyView, NewsItem, PaperView, PresidencyView, ScoreView } from './types.js';
import type { NewGame, StartOptions } from '../start.js';

interface EngineModule { call(request: string): string; }
let mod: EngineModule | null = null;
/** The site's build (version.json), so the engine and the saves always come from the same one; empty if there is none. */
let build = '';

/** A URL for this build's copy of a file, so a browser's cached copy from an earlier build is never used with this one. */
function versioned(url: string): string { return build ? url + (url.includes('?') ? '&' : '?') + 'v=' + build : url; }

/**
 * Loads the engine module once. It asks the server which build it has (past the browser's cache) and loads that
 * build's engine: an engine kept from an earlier visit cannot read a newer build's saves.
 */
export async function start(url = new URL('../../engine/engine.js', import.meta.url).href): Promise<void> {
  if (mod) return;
  try {
    const r = await fetch(new URL('../../version.json', import.meta.url).href, { cache: 'no-cache' });
    if (r.ok) build = ((await r.json()) as { build?: string }).build ?? '';
  } catch { /* a build without version.json: load as before */ }
  mod = (await import(versioned(url))) as EngineModule;
}

/** A file of this build (a sample save), fetched past any stale copy in the browser's cache. */
export async function buildFile(path: string): Promise<string> {
  const r = await fetch(versioned(path), { cache: 'no-cache' });
  if (!r.ok) throw new Error(`Could not load ${path} (${r.status})`);
  return r.text();
}

export class Refused extends Error {}

function request<T>(req: object): T {
  if (!mod) throw new Error('The engine has not started');
  const res = JSON.parse(mod.call(JSON.stringify(req))) as { ok: boolean; error?: string } & T;
  if (!res.ok) throw new Refused(res.error ?? 'Not possible');
  return res;
}

export const engine = {
  load(text: string): void { request({ op: 'load', text }); },
  save(ui: object | null = null): string { return request<{ text: string }>({ op: 'save', ui }).text; },
  campaign(): CampaignView { return request<CampaignView>({ op: 'view', name: 'campaign' }); },
  presidency(): PresidencyView { return request<PresidencyView>({ op: 'view', name: 'presidency' }); },
  paper(): PaperView { return request<PaperView>({ op: 'view', name: 'paper' }); },
  country(): CountryView { return request<CountryView>({ op: 'view', name: 'country' }); },
  economy(): EconomyView { return request<EconomyView>({ op: 'view', name: 'economy' }); },
  cabinet(): CabinetView { return request<CabinetView>({ op: 'view', name: 'cabinet' }); },
  hill(): HillView { return request<HillView>({ op: 'view', name: 'hill' }); },
  westWing(): WestWingView { return request<WestWingView>({ op: 'view', name: 'westwing' }); },
  westWingOptions(kind: 'staff' | 'post', index: number): { options: WwPerson[] } { return request<{ options: WwPerson[] }>({ op: 'view', name: 'westwingOptions', kind, index }); },
  press(): PressView { return request<PressView>({ op: 'view', name: 'press' }); },
  terms(): TermsView { return request<TermsView>({ op: 'view', name: 'terms' }); },
  sitRoom(): SitRoomView { return request<SitRoomView>({ op: 'view', name: 'sitroom' }); },
  hormuz(): HormuzView { return request<HormuzView>({ op: 'view', name: 'hormuz' }); },
  taiwan(): TaiwanView { return request<TaiwanView>({ op: 'view', name: 'taiwan' }); },
  electionNight(): ElectionNightView { return request<ElectionNightView>({ op: 'view', name: 'electionNight' }); },
  /** Island Chain on its own (docs/ISLAND_CHAIN.md): a crisis without a presidency, as America or as China. */
  islandChain: {
    start(o: { year: number; preset: number; side: 'US' | 'PRC' }): void { request({ op: 'islandChain', action: 'start', seed: Math.floor(Math.random() * 1e9), ...o }); },
    view(): TaiwanView { return request<TaiwanView>({ op: 'islandChain', action: 'view' }); },
    order(order: object): void { request({ op: 'islandChain', action: 'order', order }); },
    turn(): void { request({ op: 'islandChain', action: 'turn' }); },
    end(): void { request({ op: 'islandChain', action: 'end' }); },
  },
  foreignCountry(country: string): ForeignCountryView { return request<ForeignCountryView>({ op: 'view', name: 'foreignCountry', country }); },
  verdict(): VerdictView { return request<VerdictView>({ op: 'view', name: 'verdict' }); },
  transition(): TransitionView { return request<TransitionView>({ op: 'view', name: 'transition' }); },
  /** What a new game can be: the candidates, scenarios and difficulties. */
  options(realRoster: boolean): StartOptions { return request<StartOptions>({ op: 'options', realRoster }); },
  newGame(g: NewGame): void { request({ op: 'newGame', ...g }); },
  residence(): ResidenceView { return request<ResidenceView>({ op: 'view', name: 'residence' }); },
  /** The budget office's score of moving a policy some steps from where it stands. */
  score(policy: string, steps: number): ScoreView { return request<ScoreView>({ op: 'view', name: 'score', policy, steps }); },
  /** The budget office's score of one of the game's bills. */
  billScore(bill: string): ScoreView { return request<ScoreView>({ op: 'view', name: 'billScore', bill }); },
  decisions(): Decision[] { return request<{ decisions: Decision[] }>({ op: 'view', name: 'decisions' }).decisions; },
  /** Applies a command (in the save file's command format); returns the log lines it produced, newest first. */
  command(command: object): NewsItem[] { return request<{ news: NewsItem[] }>({ op: 'command', command }).news; },
};
