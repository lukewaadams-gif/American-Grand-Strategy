// Build-time: the simulation to WebAssembly (web/gen/ags_sim.wasm) and the data files into one bundle (web/gen/data.json).
// Needs cargo and the wasm32-unknown-unknown target; no wasm-bindgen.
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const here = path.dirname(fileURLToPath(import.meta.url)), root = path.resolve(here, '..'), gen = path.join(root, 'web/gen');
fs.mkdirSync(gen, { recursive: true });
execFileSync('cargo', ['build', '--release', '--target', 'wasm32-unknown-unknown', '--lib'], { cwd: path.join(root, 'sim'), stdio: 'inherit' });
fs.copyFileSync(path.join(root, 'sim/target/wasm32-unknown-unknown/release/ags_sim.wasm'), path.join(gen, 'ags_sim.wasm'));
const files = ['states', 'factions', 'planks', 'demands', 'doctrines', 'century', 'powers', 'names', 'difficulty', 'units', 'leaders', 'ideas', 'buildings', 'laws', 'terrain', 'calendar', 'events'];
const data = Object.fromEntries(files.map(k => [k, JSON.parse(fs.readFileSync(path.join(root, 'data', k + '.json'), 'utf8'))]));
fs.writeFileSync(path.join(gen, 'data.json'), JSON.stringify(data));
console.log('sim', (fs.statSync(path.join(gen, 'ags_sim.wasm')).size / 1024).toFixed(0), 'KB; data', (fs.statSync(path.join(gen, 'data.json')).size / 1024).toFixed(0), 'KB');
