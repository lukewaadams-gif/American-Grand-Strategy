// Puts the preview site together in target/preview: the page, the stylesheet, the fonts, sample saves, the compiled
// interface (app/, from tsc) and the engine (engine/, from TeaVM via mvn). Serve the folder with any static server.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const out = path.join(root, 'target/preview');
const copy = (from, to) => fs.cpSync(path.join(root, from), path.join(out, to), { recursive: true });
copy('src/main/preview', '.');
copy('src/main/webapp/fonts', 'fonts');
for (const need of ['engine/engine.js', 'app/main.js', 'saves/campaign.json'])
  if (!fs.existsSync(path.join(out, need))) console.warn('missing ' + need + (need.startsWith('engine') ? ' (run mvn -pl mandate-web package)' : ''));
console.log('preview assembled in target/preview');
