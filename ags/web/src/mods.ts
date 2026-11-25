// Mods: one JSON file that patches the game's data before the simulation loads it. See docs/MODDING.md.
//
// { "name": "Hanging Chads", "version": "1.0", "author": "…", "about": "…",
//   "files": { "events": [ { "id": "debate", "title": "The only debate" }, { "id": "my_card", … } ],
//              "leaders": [ { "id": "mayor", "remove": true } ] } }
//
// Lists of things with an id (or a unit's "line", a state's "code", a calendar turn's "title") merge by that key: a
// matching item is patched field by field, a new one is added (before the item named in "_before", if given), and an
// item with "remove": true is taken out. Objects merge key by key; anything else, including lists without keys,
// is replaced.
import { store } from './store';

export interface Mod { name: string; version?: string; author?: string; about?: string; files: Record<string, any> }
export interface Installed { mod: Mod; on: boolean }
export const MODS_KEY = 'ags-mods';
/** Set at boot: why the active mods were not applied, if the simulation refused them. */
export const modStatus = { error: '', sig: '' };
const KEYS = ['id', 'line', 'code', 'title'];

const isObj = (x: any) => x != null && typeof x === 'object' && !Array.isArray(x);
const keyOf = (arr: any[]) => KEYS.find(k => arr.some(x => isObj(x) && x[k] != null));

function merge(base: any, patch: any): any {
  if (Array.isArray(base) && Array.isArray(patch)) {
    const k = keyOf(base) || keyOf(patch); if (!k) return patch.slice();
    const out = base.map(x => (isObj(x) ? { ...x } : x));
    for (const p of patch) {
      if (!isObj(p) || p[k] == null) continue;
      const i = out.findIndex(x => isObj(x) && x[k] === p[k]);
      if (p.remove === true) { if (i >= 0) out.splice(i, 1); continue; }
      const { _before, ...item } = p;
      if (i >= 0) { out[i] = merge(out[i], item); continue; }
      const at = _before != null ? out.findIndex(x => isObj(x) && x[k] === _before) : -1;
      if (at >= 0) out.splice(at, 0, item); else out.push(item);
    }
    return out;
  }
  if (isObj(base) && isObj(patch)) { const out = { ...base }; for (const [k, v] of Object.entries(patch)) out[k] = k in base ? merge(base[k], v) : v; return out; }
  return patch;
}

/** Checks a file is shaped like a mod; returns the reason it is not, or ''. */
export function problem(m: any, data: any): string {
  if (!isObj(m)) return 'A mod is a JSON object.';
  if (typeof m.name !== 'string' || !m.name.trim()) return 'A mod needs a "name".';
  if (!isObj(m.files)) return 'A mod needs "files": the data files it changes.';
  const unknown = Object.keys(m.files).filter(f => !(f in data)); if (unknown.length) return `No such data file: ${unknown.join(', ')}. The files are ${Object.keys(data).join(', ')}.`;
  return '';
}

export function installed(): Installed[] { const l = store.get(MODS_KEY, []); return Array.isArray(l) ? l.filter((x: any) => isObj(x?.mod)) : []; }
export function setInstalled(l: Installed[]) { store.set(MODS_KEY, l); }
export function active(): Mod[] { return installed().filter(x => x.on).map(x => x.mod); }

/** The data with every active mod applied, in order. */
export function apply(data: any, mods: Mod[]): any { let d = data; for (const m of mods) for (const [f, patch] of Object.entries(m.files)) d = { ...d, [f]: merge(d[f], patch) }; return d; }

/** A short fingerprint of a mod set, kept in saves: a save only loads under the mods it was made with. */
export function signature(mods: Mod[]): string {
  if (!mods.length) return '';
  const s = JSON.stringify(mods.map(m => [m.name, m.version || '', m.files])); let h = 0x811c9dc5;
  for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 0x01000193) >>> 0; }
  return mods.map(m => `${m.name}${m.version ? ' ' + m.version : ''}`).join(' + ') + ' #' + h.toString(16).padStart(8, '0');
}
