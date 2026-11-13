// Plays the browser build through its own buttons: new game -> election night -> transition -> day 100.
const { chromium } = require('playwright');
const OUT = process.argv[2];
const BASE = process.argv[3] || 'http://127.0.0.1:8765/index.html';
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1600, height: 900 } });
  const errors = [];
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  const shot = async name => { await page.waitForTimeout(250); await page.screenshot({ path: `${OUT}/${name}.png` }); };
  const btn = name => page.locator('button', { hasText: name }).filter({ hasNot: page.locator('[aria-disabled="true"]') });
  const click = async name => { const b = page.locator(`button:not([aria-disabled="true"])`, { hasText: name }).first(); await b.click(); await page.waitForTimeout(60); };
  const modalOpen = async () => (await page.locator('.scrim').count()) > 0;
  const answerModal = async () => {
    const choice = page.locator('.scrim button.choice:not([aria-disabled="true"])').first();
    if (await choice.count()) { await choice.click(); return 'choice'; }
    const primary = page.locator('.scrim button.btn.primary:not([aria-disabled="true"])').first();
    if (await primary.count()) { const t = await primary.textContent(); await primary.click(); return 'primary:' + t; }
    const any = page.locator('.scrim button:not([aria-disabled="true"])').first();
    await any.click(); return 'any';
  };
  const t0 = Date.now();
  await page.goto(BASE);
  await page.waitForTimeout(800);
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await page.waitForTimeout(800);
  let won = false, attempt = 0, night = '';
  while (!won && attempt++ < 8) {
    await click('New game');
    if (attempt === 1) await shot('w-newgame-1');
    await click('Next: The country');
    if (attempt === 1) await shot('w-newgame-2');
    await click('Next: Your candidate');
    await page.locator('button.choice').first().click();
    if (attempt === 1) await shot('w-newgame-3');
    await click('Next: The rules');
    await click('Story');
    if (attempt === 1) await shot('w-newgame-4');
    await click('Begin the campaign');
    await page.waitForTimeout(300);
    if (attempt === 1) await shot('w-campaign-hq');
    let weeks = 0, modals = 0, guard = 0;
    while (guard++ < 300) {
      if (await modalOpen()) {
        const m = await page.locator('.scrim').innerText();
        if (m.toUpperCase().includes('ELECTION NIGHT')) break;
        if (modals === 0 && attempt === 1) await shot('w-campaign-decision');
        await answerModal(); modals++; continue;
      }
      if (weeks === 3 && attempt === 1) { await page.keyboard.press('1'); await shot('w-campaign-overview'); await page.keyboard.press('2'); }
      const run = page.locator('button:not([aria-disabled="true"])', { hasText: 'Run the week' }).first();
      if (!(await run.count())) break;
      await run.click(); weeks++;
      await page.waitForTimeout(40);
    }
    night = await page.locator('.scrim').innerText().catch(() => '');
    won = night.includes('You win');
    console.log(`attempt ${attempt}: campaign ${weeks} weeks, ${modals} cards; ${won ? 'WON' : 'LOST'}; post-mortem ${night.toUpperCase().includes('WHAT HAPPENED')}; ${Date.now() - t0} ms`);
    if (attempt === 1 || won) await shot(won ? 'w-election-night' : 'w-election-lost');
    if (!won) { await click('New game'); await page.waitForTimeout(200); await page.locator('button', { hasText: 'Main menu' }).first().click(); await page.waitForTimeout(200); }
  }
  if (!won) { console.log('no win in 8 attempts'); console.log(errors.join('\n')); await browser.close(); process.exit(3); }
  await click('On to the transition');
  await shot('w-transition');
  // with no campaign promises the agenda starts empty: pick the first priority, as a player would
  if (!(await page.locator('button:not([aria-disabled="true"])', { hasText: 'Set this agenda and take the oath' }).count())) {
    await page.locator('button.choice:not([aria-disabled="true"])').first().click();
    await page.waitForTimeout(100);
    await shot('w-transition-picked');
  }
  await click('Set this agenda and take the oath');
  await page.waitForTimeout(300);
  await shot('w-week1-oval');
  let turns = 0; guard = 0; const seen = new Set();
  while (guard++ < 400) {
    if (await modalOpen()) {
      const m = await page.locator('.scrim').innerText();
      const kind = m.split('\n')[0];
      if (!seen.has(kind)) { seen.add(kind); await shot('w-card-' + seen.size); }
      await answerModal(); continue;
    }
    const body = await page.locator('body').innerText();
    if (body.includes('The 100-day report')) break;
    if (turns === 3) {
      for (const [k, n] of [['4', 'congress'], ['5', 'economy'], ['6', 'security'], ['7', 'timeline'], ['1', 'overview'], ['S', 'saves'], [',', 'settings']]) {
        await page.keyboard.press(k); await shot('w-days-' + n);
      }
      await page.keyboard.press('3');
    }
    const end = page.locator('button:not([aria-disabled="true"])', { hasText: 'End the week' }).first();
    if (!(await end.count())) { console.log('no End the week button'); break; }
    await end.click(); turns++;
    await page.waitForTimeout(40);
  }
  await page.waitForTimeout(300);
  await shot('w-report');
  const body = await page.locator('body').innerText();
  console.log(`hundred days: ${turns} weeks; report shown: ${body.includes('The 100-day report')}; total ${Date.now() - t0} ms`);
  // saves survive a reload: Continue appears on the menu
  await page.reload(); await page.waitForTimeout(800);
  console.log('after reload, Continue offered:', await page.locator('button', { hasText: 'Continue' }).count() > 0);
  await shot('w-menu-after');
  console.log(errors.length ? 'ERRORS\n' + errors.join('\n') : 'no console errors');
  await browser.close();
})();
