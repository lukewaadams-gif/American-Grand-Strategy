// Build-time: the simulation to WebAssembly (web/gen/ags_sim.wasm) and the data files into one bundle (web/gen/data.json).
// Needs cargo and the wasm32-unknown-unknown target; no wasm-bindgen.
// Also the same simulation as plain JavaScript (web/gen/ags_sim.js, via binaryen's wasm2js) for pages that may not run
// WebAssembly: built for the baseline feature set, so the converter can lower everything it contains.
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const here = path.dirname(fileURLToPath(import.meta.url)), root = path.resolve(here, '..'), gen = path.join(root, 'web/gen');
fs.mkdirSync(gen, { recursive: true });
execFileSync('cargo', ['build', '--release', '--target', 'wasm32-unknown-unknown', '--lib'], { cwd: path.join(root, 'sim'), stdio: 'inherit' });
fs.copyFileSync(path.join(root, 'sim/target/wasm32-unknown-unknown/release/ags_sim.wasm'), path.join(gen, 'ags_sim.wasm'));
execFileSync('cargo', ['build', '--release', '--target', 'wasm32-unknown-unknown', '--lib', '--target-dir', 'target-mvp'], { cwd: path.join(root, 'sim'), stdio: 'inherit', env: { ...process.env, RUSTFLAGS: '-C target-cpu=mvp' } });
const bin = path.join(root, 'web/node_modules/.bin');
execFileSync(path.join(bin, 'wasm2js'), [path.join(root, 'sim/target-mvp/wasm32-unknown-unknown/release/ags_sim.wasm'), '--enable-bulk-memory', '-O2', '-o', path.join(gen, 'ags_sim.raw.js')], { stdio: ['ignore', 'ignore', 'inherit'] });
execFileSync(path.join(bin, 'esbuild'), [path.join(gen, 'ags_sim.raw.js'), '--minify', '--format=esm', '--log-level=warning', `--outfile=${path.join(gen, 'ags_sim.js')}`], { stdio: 'inherit' });
fs.rmSync(path.join(gen, 'ags_sim.raw.js'));
const files = ['states', 'factions', 'planks', 'demands', 'doctrines', 'century', 'powers', 'names', 'difficulty', 'units', 'leaders', 'ideas', 'buildings', 'laws', 'terrain', 'calendar', 'events'];
const data = Object.fromEntries(files.map(k => [k, JSON.parse(fs.readFileSync(path.join(root, 'data', k + '.json'), 'utf8'))]));
fs.writeFileSync(path.join(gen, 'data.json'), JSON.stringify(data));
console.log('sim', (fs.statSync(path.join(gen, 'ags_sim.wasm')).size / 1024).toFixed(0), 'KB; as JavaScript', (fs.statSync(path.join(gen, 'ags_sim.js')).size / 1024).toFixed(0), 'KB; data', (fs.statSync(path.join(gen, 'data.json')).size / 1024).toFixed(0), 'KB');
