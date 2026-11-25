// Plays tests/parity.rs's script through the WebAssembly build and writes each view's fingerprint, for the parity test;
// then plays it through the JavaScript build (wasm2js) and fails unless every fingerprint matches.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const gen = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../web/gen');
const { instance } = await WebAssembly.instantiate(fs.readFileSync(path.join(gen, 'ags_sim.wasm')), {});
let X = instance.exports; const enc = new TextEncoder(), dec = new TextDecoder();
const raw = req => { const b = enc.encode(req); const p = X.ags_alloc(b.length); new Uint8Array(X.memory.buffer, p, b.length).set(b); const rp = X.ags_call(p, b.length) >>> 0, rl = X.ags_out_len() >>> 0; X.ags_free(p, b.length); const s = dec.decode(new Uint8Array(X.memory.buffer, rp, rl)); X.ags_free(rp, rl); return s; };
const fp = s => { let h = 1469598103934665603n; for (const b of enc.encode(s)) { h ^= BigInt(b); h = (h * 1099511628211n) & 0xffffffffffffffffn; } return h.toString(16).padStart(16, '0'); };
const play = () => {
  raw(JSON.stringify({ op: 'load', data: JSON.parse(fs.readFileSync(path.join(gen, 'data.json'))), map: JSON.parse(fs.readFileSync(path.join(gen, 'map.json'))) }));
const script = [{ op: 'new', setup: { leader: 'mayor', party: 'Union', factions: ['labor', 'suburbs', 'justice'], difficulty: 'operative', seed: 42 } }];
for (let i = 0; i < 4; i++) script.push({ op: 'cmd', c: 'choose', n: 0 }); script.push({ op: 'cmd', c: 'end_turn' });
return script.map(r => { raw(JSON.stringify(r)); return fp(raw(JSON.stringify({ op: 'view' }))); });
};
const out = play();
fs.writeFileSync(path.join(gen, 'parity-wasm.json'), JSON.stringify(out)); console.log('parity fingerprints', out.length);
// a whole game to the end of the slice, every view fingerprinted, on both builds
const whole = () => { const prints = []; raw(JSON.stringify({ op: 'new', setup: { leader: 'governor', party: 'Union', factions: ['labor', 'faith', 'rural'], difficulty: 'staffer', seed: 2000 } }));
  for (let i = 0; i < 400; i++) { const v = JSON.parse(raw(JSON.stringify({ op: 'view' }))); prints.push(fp(JSON.stringify(v))); if (v.over) break;
    const p = v.pending; const c = !p ? { c: 'end_turn' } : p.kind === 'cabinet' ? { c: 'choose', picks: [0, 3, 4] } : { c: 'choose', n: (p.choices && p.choices[0] && p.choices[0].n) || 0 };
    raw(JSON.stringify({ op: 'cmd', ...c })); }
  return prints; };
const wasmWhole = whole();
X = await import(path.join(gen, 'ags_sim.js')); const js = play(), jsWhole = whole();
if (JSON.stringify(js) !== JSON.stringify(out)) { console.error('the JavaScript build plays a different game:', js, out); process.exit(1); }
const at = wasmWhole.findIndex((h, i) => h !== jsWhole[i]);
if (at >= 0 || wasmWhole.length !== jsWhole.length) { console.error(`the JavaScript build drifts at step ${at} of ${wasmWhole.length}`); process.exit(1); }
console.log(`the JavaScript build matches, fingerprint for fingerprint, over a whole game (${wasmWhole.length} views)`);
