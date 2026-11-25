// Bundles the front end with esbuild and assembles dist/: the page, the script, the styles, the simulation and its data.
import * as esbuild from 'esbuild';
import fs from 'node:fs';
const out = 'dist'; fs.rmSync(out, { recursive: true, force: true }); fs.mkdirSync(out, { recursive: true });
await esbuild.build({ entryPoints: ['src/main.ts'], bundle: true, format: 'esm', target: 'es2022', minify: true, sourcemap: false, outdir: out, logLevel: 'warning', legalComments: 'none' });
fs.copyFileSync('index.html', `${out}/index.html`);
for (const f of ['ags_sim.wasm', 'data.json', 'map.json', 'worldmap.json']) fs.copyFileSync(`gen/${f}`, `${out}/${f}`);
const v = { built: new Date().toISOString() }; fs.writeFileSync(`${out}/version.json`, JSON.stringify(v));
let total = 0; for (const f of fs.readdirSync(out)) total += fs.statSync(`${out}/${f}`).size;
console.log('dist', fs.readdirSync(out).join(' '), `${(total / 1024).toFixed(0)} KB`);
