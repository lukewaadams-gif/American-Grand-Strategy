// The engine: the Java simulation compiled to JavaScript (target/preview/engine/engine.js), behind one call that
// takes and returns JSON. Everything the interface shows comes from its view models; everything it does is a command.
import type { CampaignView, Decision, NewsItem, PaperView, PresidencyView } from './types.js';

interface EngineModule { call(request: string): string; }
let mod: EngineModule | null = null;

/** Loads the engine module once. */
export async function start(url = new URL('../../engine/engine.js', import.meta.url).href): Promise<void> {
  if (!mod) mod = (await import(url)) as EngineModule;
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
  decisions(): Decision[] { return request<{ decisions: Decision[] }>({ op: 'view', name: 'decisions' }).decisions; },
  /** Applies a command (in the save file's command format); returns the log lines it produced, newest first. */
  command(command: object): NewsItem[] { return request<{ news: NewsItem[] }>({ op: 'command', command }).news; },
};
