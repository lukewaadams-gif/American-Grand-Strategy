// The bridge to the simulation: JSON requests into the WebAssembly module, JSON replies back. No wasm-bindgen.
import { Mod, apply, signature } from './mods';
export type View = any;
export type Fx = { kind: string; [k: string]: any };

export class Sim {
  private x!: WebAssembly.Exports & { memory: WebAssembly.Memory; ags_alloc(n: number): number; ags_free(p: number, n: number): void; ags_call(p: number, n: number): bigint };
  private enc = new TextEncoder();
  private dec = new TextDecoder();
  view: View = null;
  /** The active mods' fingerprint ('' for none); saves carry it. */
  mods = '';
  /** Why the mods were not applied, if the simulation refused the modded data. */
  modError = '';

  async init(base = '.', mods: Mod[] = []) {
    const [wasm, base0, map] = await Promise.all([
      fetch(`${base}/ags_sim.wasm`).then(r => { if (!r.ok) throw new Error('the simulation did not load'); return r.arrayBuffer(); }),
      fetch(`${base}/data.json`).then(r => r.json()), fetch(`${base}/map.json`).then(r => r.json())]);
    const { instance } = await WebAssembly.instantiate(wasm, {});
    this.x = instance.exports as any;
    // modded data first; if the simulation refuses it, say why and start unmodded rather than not at all
    let data = base0;
    if (mods.length) {
      try { data = apply(base0, mods); const r = this.raw({ op: 'load', data, map }); if (!r.ok) throw new Error(r.error); this.mods = signature(mods); return { data, map }; }
      catch (e) { this.modError = (e as Error).message; data = base0; }
    }
    const r = this.raw({ op: 'load', data, map }); if (!r.ok) throw new Error(r.error);
    return { data, map };
  }
  raw(req: object): any {
    const b = this.enc.encode(JSON.stringify(req)); const p = this.x.ags_alloc(b.length);
    new Uint8Array(this.x.memory.buffer, p, b.length).set(b);
    const r = this.x.ags_call(p, b.length); this.x.ags_free(p, b.length);
    const rp = Number(r >> 32n), rl = Number(r & 0xffffffffn);
    const out = JSON.parse(this.dec.decode(new Uint8Array(this.x.memory.buffer, rp, rl))); this.x.ags_free(rp, rl);
    return out;
  }
  newGame(setup: object) { const r = this.raw({ op: 'new', setup }); if (!r.ok) throw new Error(r.error); return this.refresh(); }
  refresh(): View { this.view = this.raw({ op: 'view' }); return this.view; }
  /** A command; returns its effects to animate, or throws the engine's reason. */
  cmd(c: object): Fx[] { const r = this.raw({ op: 'cmd', ...c }); if (!r.ok) throw new Error(r.error); this.refresh(); return r.fx || []; }
  tryCmd(c: object): { fx: Fx[]; error?: string } { try { return { fx: this.cmd(c) }; } catch (e) { return { fx: [], error: (e as Error).message }; } }
  reach(unit: number): { from: number; hexes: [number, number, number][] } { return this.raw({ op: 'reach', unit }); }
  odds(unit: number, target: number, action: string): { p: number; am: number; bm: number } { return this.raw({ op: 'odds', unit, target, action }); }
  save(): any { const s = this.raw({ op: 'save' }).save; if (s && this.mods) s.mods = this.mods; return s; }
  /** Loads a save, refusing one made under different mods: replaying it on other data would quietly corrupt it. */
  restore(save: any) {
    const want = save?.mods || '';
    if (want !== this.mods) throw new Error(want ? `This game was saved with mods (${want}). Turn the same mods on to load it.` : `This game was saved without mods. Turn your mods off to load it.`);
    const r = this.raw({ op: 'restore', save }); if (!r.ok) throw new Error(r.error); return this.refresh();
  }
}
