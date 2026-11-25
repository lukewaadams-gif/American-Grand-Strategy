// Builds the game as a hosted page (a Claude artifact): one HTML fragment with the styles and the whole script inline,
// since such pages may only load scripts that are written into them, and the data files published beside it, which the
// page fetches by relative URL. The script carries the JavaScript build of the simulation, used when the page refuses
// WebAssembly. Run after build-sim and build-world. Output: dist-artifact/.
import * as esbuild from 'esbuild';
import fs from 'node:fs';
const out = 'dist-artifact', tmp = `${out}/.tmp`;
fs.rmSync(out, { recursive: true, force: true }); fs.mkdirSync(tmp, { recursive: true });
await esbuild.build({ entryPoints: ['src/main.ts'], bundle: true, splitting: false, format: 'iife', target: 'es2022', minify: true, outdir: tmp, logLevel: 'warning', legalComments: 'none' });
// inline text must not close its own element or open an HTML comment
const js = fs.readFileSync(`${tmp}/main.js`, 'utf8').replace(/<\/(script)/gi, '<\\/$1').replace(/<!--/g, '<\\!--');
const css = fs.readFileSync(`${tmp}/main.css`, 'utf8').replace(/<\/(style)/gi, '<\\/$1');
fs.rmSync(tmp, { recursive: true });
const fonts = 'https://fonts.googleapis.com/css2?family=Alegreya+Sans+SC:wght@500;700&family=Alegreya+Sans:wght@400;500;700&family=IM+Fell+English+SC&family=Libre+Caslon+Text:ital,wght@0,400;0,700;1,400&family=Special+Elite&family=UnifrakturMaguntia&display=swap';
fs.writeFileSync(`${out}/index.html`, `<title>American Grand Strategy</title>
<meta name="description" content="A board game of American power: lead a party for half a century on a hex map of the country.">
<link rel="stylesheet" href="${fonts}">
<style>${css}</style>
<div id="app"></div>
<script>${js}</script>
`);
for (const f of ['ags_sim.wasm', 'data.json', 'map.json', 'worldmap.json']) fs.copyFileSync(`gen/${f}`, `${out}/${f}`);
fs.cpSync('../mods', `${out}/mods`, { recursive: true });
const kb = f => (fs.statSync(`${out}/${f}`).size / 1024).toFixed(0);
console.log(`dist-artifact: index.html ${kb('index.html')} KB (inline), ags_sim.wasm ${kb('ags_sim.wasm')} KB, data ${kb('data.json')} KB, map ${kb('map.json')} KB, world ${kb('worldmap.json')} KB`);
