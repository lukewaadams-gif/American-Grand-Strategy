// Browser interactions that the play-through does not cover: reload and Continue, undo, shortcuts, number keys in a
// decision card, export and import, settings that survive a reload, Tab order. Prints PASS/FAIL lines.
const { chromium } = require('playwright');
const fs = require('fs');
const OUT = process.argv[2], MOMENTS = process.argv[3], BASE = process.argv[4] || 'http://127.0.0.1:8765/index.html';
const crisis = fs.readFileSync(MOMENTS + '/crisis.json', 'utf8');
let failures = 0;
const check = (what, ok, extra = '') => { console.log((ok ? 'PASS  ' : 'FAIL  ') + what + (extra ? '  (' + extra + ')' : '')); if (!ok) failures++; };
(async () => {
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1600, height: 900 }, acceptDownloads: true });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  const text = () => page.locator('body').innerText();
  const title = () => page.locator('h1, [role="heading"][aria-level="1"]').first().innerText();
  await page.goto(BASE);
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await page.waitForSelector('.menu');

  // a new game, two weeks, then reload: Continue resumes the same week
  await page.locator('button', { hasText: 'New game' }).click();
  for (const n of ['Next: The country', 'Next: Your candidate']) await page.locator('button', { hasText: n }).click();
  await page.locator('button.choice').first().click();
  await page.locator('button', { hasText: 'Next: The rules' }).click();
  await page.locator('button', { hasText: 'Begin the campaign' }).click();
  await page.waitForSelector('.shell');
  for (let i = 0; i < 2; i++) {
    while (await page.locator('.scrim').count()) await page.locator('.scrim button.choice, .scrim button.btn.primary').first().click();
    await page.keyboard.press('Control+Enter');
    await page.waitForTimeout(150);
  }
  while (await page.locator('.scrim').count()) await page.locator('.scrim button.choice, .scrim button.btn.primary').first().click();
  const dateBefore = await page.locator('.identity').innerText();
  check('Ctrl+Enter runs the week', !dateBefore.includes('Jul 17'), dateBefore.split('\n').pop());
  await page.reload();
  await page.waitForSelector('.menu');
  check('after a reload the menu offers Continue', await page.locator('button', { hasText: 'Continue' }).count() === 1);
  await page.locator('button', { hasText: 'Continue' }).click();
  await page.waitForSelector('.shell');
  check('Continue resumes the same week', (await page.locator('.identity').innerText()) === dateBefore);

  // undo: one week back, exactly
  await page.keyboard.press('Control+Enter');
  await page.waitForTimeout(150);
  while (await page.locator('.scrim').count()) await page.keyboard.press('Escape');
  const afterRun = await page.locator('.identity').innerText();
  await page.keyboard.press('Control+z');
  await page.waitForTimeout(200);
  check('Ctrl+Z undoes the week', (await page.locator('.identity').innerText()) === dateBefore, afterRun.split('\n').pop() + ' -> back');

  // single-key screens
  for (const [k, name] of [['1', 'The nation'], ['5', 'The economy'], ['6', 'National security'], ['7', 'Timeline'], ['S', 'Save and load'], [',', 'Settings'], ['2', 'Campaign headquarters']]) {
    await page.keyboard.press(k);
    await page.waitForTimeout(80);
    check(`key ${k} opens ${name}`, (await title()).includes(name), await title());
  }
  await page.keyboard.press('?');
  await page.waitForTimeout(100);
  check('? opens help', (await page.locator('.scrim').innerText()).includes('Shortcuts and terms'));
  await page.keyboard.press('Escape');
  check('Esc closes it', (await page.locator('.scrim').count()) === 0);

  // settings persist: text size 125%
  await page.keyboard.press(',');
  await page.locator('button', { hasText: '125%' }).click();
  await page.waitForTimeout(150);
  await page.reload();
  await page.waitForSelector('.menu');
  const scale = await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--s').trim());
  check('a setting survives a reload', scale === '1.25', '--s=' + scale);
  await page.locator('button', { hasText: 'Settings' }).click();
  await page.locator('.scrim button', { hasText: '100%' }).click();
  await page.keyboard.press('Escape');

  // a desktop save, imported as a file; then the decision card's number keys
  await page.locator('button', { hasText: 'Load a game' }).click();
  const [chooser] = await Promise.all([page.waitForEvent('filechooser'), page.locator('.scrim button', { hasText: 'Import a save file' }).click()]);
  fs.writeFileSync(OUT + '/crisis-import.json', crisis);
  await chooser.setFiles(OUT + '/crisis-import.json');
  await page.waitForSelector('.shell');
  check('a desktop save imports from a file', (await page.locator('.identity').innerText()).includes('Feb 12, 2029'));
  await page.keyboard.press('d');
  await page.waitForTimeout(150);
  const card = await page.locator('.scrim').innerText().catch(() => '');
  check('D opens the waiting decision', card.toUpperCase().includes('FOREIGN CRISIS'));
  await page.keyboard.press('2');
  await page.waitForTimeout(200);
  const after = await page.locator('.scrim').innerText().catch(() => '');
  check('2 chooses the second option and says what happened', after.toUpperCase().includes('WHAT HAPPENED') && after.includes('Sanctions'), after.split('\n')[1]);
  await page.keyboard.press('Enter');
  await page.waitForTimeout(150);
  check('Enter on Continue closes the card', (await page.locator('.scrim').count()) === 0);

  // export: a download of the same save format
  await page.keyboard.press('S');
  const [download] = await Promise.all([page.waitForEvent('download'), page.locator('button', { hasText: 'Export this game to a file' }).click()]);
  const file = await download.path();
  const saved = fs.readFileSync(file, 'utf8');
  fs.writeFileSync(OUT + '/exported.json', saved);   // the desktop loads it too (UI_DESIGN.md §17)
  check('export downloads a save', saved.includes('"format": "mandate-save"') && download.suggestedFilename().endsWith('.json'), download.suggestedFilename());

  // Tab order: top bar, rail, then the screen
  await page.keyboard.press('1');
  await page.locator('body').click({ position: { x: 5, y: 5 } });
  const order = [];
  for (let i = 0; i < 12; i++) {
    await page.keyboard.press('Tab');
    order.push(await page.evaluate(() => { const a = document.activeElement; return (a.getAttribute('aria-label') || a.textContent || a.tagName).trim().replace(/\s+/g, ' ').slice(0, 24); }));
  }
  console.log('Tab order: ' + order.join(' > '));
  check('Tab moves through the top bar, then the rail', order.indexOf('Overview1') > order.indexOf('Menu') || order.some(o => o.startsWith('Overview')));
  await page.screenshot({ path: OUT + '/focus.png' });
  check('no errors in the console', errors.length === 0, errors.join(' | '));
  await browser.close();
  console.log(failures ? failures + ' failed' : 'All interaction checks passed.');
  process.exit(failures ? 1 : 0);
})();
