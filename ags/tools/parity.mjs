// Plays tests/parity.rs's script through the WebAssembly build and writes each view's fingerprint, for the parity test.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const gen = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../web/gen');
const { instance } = await WebAssembly.instantiate(fs.readFileSync(path.join(gen, 'ags_sim.wasm')), {});
const X = instance.exports, enc = new TextEncoder(), dec = new TextDecoder();
const raw = req => { const b = enc.encode(req); const p = X.ags_alloc(b.length); new Uint8Array(X.memory.buffer, p, b.length).set(b); const r = X.ags_call(p, b.length); X.ags_free(p, b.length);
  const rp = Number(r >> 32n), rl = Number(r & 0xffffffffn); const s = dec.decode(new Uint8Array(X.memory.buffer, rp, rl)); X.ags_free(rp, rl); return s; };
const fp = s => { let h = 1469598103934665603n; for (const b of enc.encode(s)) { h ^= BigInt(b); h = (h * 1099511628211n) & 0xffffffffffffffffn; } return h.toString(16).padStart(16, '0'); };
raw(JSON.stringify({ op: 'load', data: JSON.parse(fs.readFileSync(path.join(gen, 'data.json'))), map: JSON.parse(fs.readFileSync(path.join(gen, 'map.json'))) }));
const script = [{ op: 'new', setup: { leader: 'mayor', party: 'Union', factions: ['labor', 'suburbs', 'justice'], difficulty: 'operative', seed: 42 } }];
for (let i = 0; i < 4; i++) script.push({ op: 'cmd', c: 'choose', n: 0 }); script.push({ op: 'cmd', c: 'end_turn' });
const out = script.map(r => { raw(JSON.stringify(r)); return fp(raw(JSON.stringify({ op: 'view' }))); });
fs.writeFileSync(path.join(gen, 'parity-wasm.json'), JSON.stringify(out)); console.log('parity fingerprints', out.length);
