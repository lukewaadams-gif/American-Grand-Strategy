// Puts the preview site together in target/preview: the page, the stylesheet, the fonts, sample saves, the compiled
// interface (app/, from tsc) and the engine (engine/, from TeaVM via mvn). Serve the folder with any static server.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const out = path.join(root, 'target/preview');
const copy = (from, to) => fs.cpSync(path.join(root, from), path.join(out, to), { recursive: true });
copy('src/main/preview', '.');
copy('src/main/webapp/fonts', 'fonts');
for (const need of ['engine/engine.js', 'app/main.js', 'app/presidency-main.js', 'saves/campaign.json', 'saves/presidency.json'])
  if (!fs.existsSync(path.join(out, need))) console.warn('missing ' + need + (need.startsWith('engine') ? ' (run mvn -pl mandate-web package)' : ''));
// The build's identity: the engine and the sample saves must come from the same build (a save from a newer engine can
// break an older one), so the pages fetch version.json past the cache and ask for both by it.
const hash = crypto.createHash('sha256');
for (const f of ['engine/engine.js', ...fs.readdirSync(path.join(out, 'saves')).sort().map(s => 'saves/' + s)])
  if (fs.existsSync(path.join(out, f))) hash.update(fs.readFileSync(path.join(out, f)));
const build = hash.digest('hex').slice(0, 12);
fs.writeFileSync(path.join(out, 'version.json'), JSON.stringify({ build }) + '\n');
console.log('preview assembled in target/preview, build ' + build);
