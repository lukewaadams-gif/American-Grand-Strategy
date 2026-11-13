// The browser build's automated UI checks: every screen, loaded from real game moments (desktop saves), at three
// window sizes, larger text, three palettes and three complexity levels, plus the dialogs behind buttons.
// Each view gets layout and text-size checks of our own and an axe-core WCAG 2.1 AA audit. Writes PNGs + checks.txt.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const OUT = process.argv[2], MOMENTS = process.argv[3], BASE = process.argv[4] || 'http://127.0.0.1:8765/index.html';
const AXE = require.resolve('axe-core/axe.min.js');
const moments = Object.fromEntries(fs.readdirSync(MOMENTS).map(f => [path.basename(f, '.json'), fs.readFileSync(path.join(MOMENTS, f), 'utf8')]));
const KEYS = { overview: '1', campaign: '2', oval: '3', congress: '4', economy: '5', security: '6', timeline: '7', results: '8', saves: 'S', settings: ',' };

const shots = [];
const S = (name, moment, screen, w = 1600, h = 900, o = {}) => shots.push({ name, moment, screen, w, h, scale: o.scale || 1, palette: o.palette || 'STANDARD', complexity: o.complexity || 'STANDARD', extra: o.extra });
for (const [w, h] of [[1280, 720], [1600, 900], [1920, 1080]]) S(`menu-${w}`, null, null, w, h);
S('newgame-1', null, null, 1600, 900, { extra: 'newgame' });
for (const s of ['overview', 'campaign', 'economy', 'security', 'timeline', 'saves', 'settings']) S(`campaign-${s}`, 'campaign', s);
S('campaign-hq-1280', 'campaign', 'campaign', 1280, 720);
S('campaign-hq-1920', 'campaign', 'campaign', 1920, 1080);
S('campaign-hq-high-contrast', 'campaign', 'campaign', 1600, 900, { palette: 'HIGH_CONTRAST' });
S('campaign-hq-colour-blind', 'campaign', 'campaign', 1600, 900, { palette: 'COLOUR_BLIND_SAFE' });
S('campaign-hq-guided', 'campaign', 'campaign', 1600, 900, { complexity: 'GUIDED' });
S('debate-decision', 'debate', 'campaign', 1600, 900, { extra: 'decision' });
S('transition-oval', 'transition', 'oval');
S('transition-oval-1280', 'transition', 'oval', 1280, 720);
S('week1-oval', 'week1', 'oval');
S('week1-congress', 'week1', 'congress');
for (const s of ['overview', 'oval', 'congress', 'economy', 'security', 'timeline']) S(`crisis-${s}`, 'crisis', s);
S('crisis-decision', 'crisis', 'security', 1600, 900, { extra: 'decision' });
S('crisis-congress-expert', 'crisis', 'congress', 1600, 900, { complexity: 'EXPERT' });
S('crisis-oval-150', 'crisis', 'oval', 1920, 1080, { scale: 1.5 });
S('crisis-concession', 'crisis', 'congress', 1600, 900, { extra: 'Work this bloc' });
S('week1-nominee', 'week1', 'oval', 1600, 900, { extra: 'Nominate' });
S('report-results', 'report', 'results');
S('report-results-1280', 'report', 'results', 1280, 720);
S('report-timeline-expert', 'report', 'timeline', 1600, 900, { complexity: 'EXPERT' });
S('lost-results', 'lost', 'results');
for (const s of ['oval', 'congress', 'economy']) S(`governing-${s}`, 'governing', s);
S('governing-push', 'governing', 'congress', 1600, 900, { extra: 'Push' });
S('help', 'crisis', 'overview', 1600, 900, { extra: 'help' });

const pageChecks = () => {
  const problems = [];
  const s = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--s')) || 1;
  const visible = el => { const st = getComputedStyle(el); return st.display !== 'none' && st.visibility !== 'hidden' && el.getClientRects().length > 0; };
  const where = el => (el.getAttribute('aria-label') || el.textContent || el.className || el.tagName).trim().replace(/\s+/g, ' ').slice(0, 50);
  if (document.documentElement.scrollWidth > window.innerWidth + 1) problems.push(`the page scrolls sideways (${document.documentElement.scrollWidth} in ${window.innerWidth})`);
  for (const el of document.querySelectorAll('.main, .advisor, .rail, .dialog')) {
    if (!visible(el)) continue;
    const st = getComputedStyle(el);
    if (st.overflowX !== 'visible' && el.scrollWidth > el.clientWidth + 1 && !el.classList.contains('rail')) problems.push(`content wider than its scroll area: ${el.className} (${el.scrollWidth} in ${el.clientWidth})`);
  }
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  const small = new Set();
  while (walker.nextNode()) {
    const n = walker.currentNode, el = n.parentElement;
    if (!n.textContent.trim() || !el || el.closest('svg, script, style, title') || !visible(el)) continue;
    const fsz = parseFloat(getComputedStyle(el).fontSize);
    if (fsz < 12 * s - 0.5) small.add(`text below 12 px: ${fsz}px "${n.textContent.trim().slice(0, 30)}"`);
  }
  for (const t of document.querySelectorAll('svg text')) {
    const fsz = parseFloat(t.getAttribute('font-size'));
    if (fsz < 12 * s - 0.5) small.add(`chart text below 12 px: ${fsz}px "${t.textContent.slice(0, 30)}"`);
  }
  problems.push(...small);
  for (const b of document.querySelectorAll('button')) {
    if (!visible(b)) continue;
    const name = (b.getAttribute('aria-label') || b.textContent || '').trim();
    if (!name) problems.push('a button with no accessible name');
    if (b.getAttribute('aria-disabled') === 'true' && !(b.getAttribute('title') || '').trim()) problems.push(`disabled without a reason: "${name.slice(0, 40)}"`);
  }
  for (const el of document.querySelectorAll('.clamp')) if (visible(el) && el.scrollHeight > el.clientHeight + 2 && !el.getAttribute('title')) problems.push(`cut off without a tooltip: "${where(el)}"`);
  for (const card of document.querySelectorAll('.card, .paper, .choice')) {
    if (!visible(card)) continue;
    const r = card.getBoundingClientRect();
    for (const k of card.querySelectorAll('*')) {
      if (k.closest('svg') || !visible(k) || k.closest('.toast')) continue;
      const kr = k.getBoundingClientRect();
      if (kr.width > 0 && (kr.right > r.right + 1 || kr.left < r.left - 1)) { problems.push(`sticks out of its card: "${where(k)}" in "${where(card)}"`); break; }
    }
  }
  if (!document.querySelector('button, input, [tabindex="0"]')) problems.push('nothing on the screen can take keyboard focus');
  return problems;
};

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  const all = [];
  const axeSeen = new Map();
  for (const shot of shots) {
    const context = await browser.newContext({ viewport: { width: shot.w, height: shot.h } });
    const page = await context.newPage();
    const errors = [];
    page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    page.on('pageerror', e => errors.push(e.message));
    await page.goto(BASE);
    await page.evaluate(([shot, save]) => {
      localStorage.clear();
      localStorage.setItem('mandate.settings', JSON.stringify({ palette: shot.palette, scale: shot.scale, complexity: shot.complexity, tips: true, advisor: true,
        confirmUnspent: true, reduceMotion: true, autoOpenDecisions: false }));
      if (save) localStorage.setItem('mandate.save.slot1', save);
    }, [shot, shot.moment ? moments[shot.moment] : null]);
    await page.reload();
    await page.waitForSelector('.menu');
    if (shot.moment) {
      await page.locator('button', { hasText: 'Load a game' }).click();
      await page.locator('button[aria-label="Load Slot 1"]').click();
      await page.locator('.scrim button.btn.primary', { hasText: 'Load' }).click();
      await page.waitForSelector('.shell');
      await page.keyboard.press(KEYS[shot.screen]);
    }
    if (shot.extra === 'newgame') await page.locator('button', { hasText: 'New game' }).click();
    else if (shot.extra === 'decision') await page.keyboard.press('d');
    else if (shot.extra === 'help') await page.keyboard.press('?');
    else if (shot.extra) await page.locator('button:not([aria-disabled="true"])', { hasText: shot.extra }).first().click();
    await page.waitForTimeout(350);
    await page.screenshot({ path: path.join(OUT, shot.name + '.png') });
    const problems = await page.evaluate(pageChecks);
    await page.addScriptTag({ path: AXE });
    const axe = await page.evaluate(async () => {
      const r = await axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } });
      return r.violations.map(v => ({ id: v.id, impact: v.impact, help: v.help, nodes: v.nodes.slice(0, 3).map(n => n.target.join(' ') + ' :: ' + (n.failureSummary || '').split('\n').slice(1, 2).join(' ')) }));
    });
    for (const p of problems) all.push(`${shot.name}: ${p}`);
    for (const v of axe) { all.push(`${shot.name}: axe ${v.id} (${v.impact}): ${v.help} -> ${v.nodes.join(' | ')}`); axeSeen.set(v.id, (axeSeen.get(v.id) || 0) + 1); }
    for (const e of errors) all.push(`${shot.name}: console error: ${e}`);
    await context.close();
  }
  await browser.close();
  fs.writeFileSync(path.join(OUT, 'checks.txt'), all.length ? all.join('\n') + '\n' : `All checks passed for ${shots.length} views.\n`);
  console.log(`${shots.length} views checked; ${all.length} problems.`);
  if (axeSeen.size) console.log('axe rules failing:', [...axeSeen.entries()].map(([k, v]) => `${k} x${v}`).join(', '));
  all.slice(0, 40).forEach(p => console.log(p));
})();
