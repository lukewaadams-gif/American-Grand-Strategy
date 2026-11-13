// Puts the preview site together in target/preview: the page, the stylesheet, the fonts, sample saves, the compiled
// interface (app/, from tsc) and the engine (engine/, from TeaVM via mvn). Serve the folder with any static server.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const out = path.join(root, 'target/preview');
const copy = (from, to) => fs.cpSync(path.join(root, from), path.join(out, to), { recursive: true });
copy('src/main/preview', '.');
copy('src/main/webapp/fonts', 'fonts');
for (const need of ['engine/engine.js', 'app/main.js', 'app/presidency-main.js', 'app/play-main.js', 'saves/campaign.json', 'saves/presidency.json'])
  if (!fs.existsSync(path.join(out, need))) console.warn('missing ' + need + (need.startsWith('engine') ? ' (run mvn -pl mandate-web package)' : ''));
// The build's identity: the engine and the sample saves must come from the same build (a save from a newer engine can
// break an older one), so the pages fetch version.json past the cache and ask for both by it.
const hash = crypto.createHash('sha256');
// (the sample saves only: files directly in saves/, not the browser checks' test saves under saves/test/)
const saves = fs.readdirSync(path.join(out, 'saves'), { withFileTypes: true }).filter(e => e.isFile()).map(e => 'saves/' + e.name).sort();
for (const f of ['engine/engine.js', ...saves])
  if (fs.existsSync(path.join(out, f))) hash.update(fs.readFileSync(path.join(out, f)));
const build = hash.digest('hex').slice(0, 12);
// where the build came from (E-05): the source commit, whether the tree had uncommitted changes, and the day it was built
let commit = '', dirty = false;
try {
  commit = execSync('git rev-parse --short=12 HEAD', { cwd: root }).toString().trim();
  dirty = execSync('git status --porcelain', { cwd: root }).toString().trim().length > 0;
} catch { /* not a git checkout: the build hash alone */ }
const stamp = { build, commit, dirty, built: new Date().toISOString().slice(0, 10) };
fs.writeFileSync(path.join(out, 'version.json'), JSON.stringify(stamp) + '\n');
// the older vertical slice at the site's root carries the same stamp, so the two are never mistaken for each other
const site = path.join(root, 'target/site');
if (fs.existsSync(site)) fs.writeFileSync(path.join(site, 'version.json'), JSON.stringify({ ...stamp, build: 'vertical-slice', app: 'the older vertical slice (desktop interface port)' }) + '\n');
console.log('preview assembled in target/preview, build ' + build);
