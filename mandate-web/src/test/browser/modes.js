// The four games (docs/MODES.md) through the interface's own buttons:
//   1. the start screen offers exactly four, and each mode's setup, at 1600 and 390 wide;
//   2. Campaign Only, from the eve of 2028, won and lost: the night, then the campaign's end (the assessment, the vote,
//      every state), election night watched again, and a replay in the same world;
//   3. Governing Only from the start screen: election night of November 7, 2028, then the transition;
//   4. Individual Crisis, Hormuz and Taiwan, on its last turn: the after-action report ends the game;
//   5. the first hundred days as governing's opening chapter: day 100's paper, the report card, and the presidency going on.
// Each end screen gets axe-core WCAG 2.1 AA, a text-size check and a sideways-scroll check. Fails on any console error.
// Needs the test saves: java -cp mandate-cli/target/mandate.jar game.mandate.ui.ElectionSaves mandate-web/target/preview/saves/test
// Usage: node modes.js [out-dir]
const { chromium } = require('playwright');
const fs = require('fs');
const axeSrc = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
const OUT = process.argv[2] || 'modes-checks';
const URL = (q = '') => 'http://127.0.0.1:8766/index.html?hour=11' + q;
fs.mkdirSync(OUT, { recursive: true });
let failures = 0;
const fail = (what) => { failures++; console.log('FAIL', what); };
(async () => {
  const browser = await chromium.launch();
  const errors = [];
  const open = async (q = '', w = 1600, hgt = 900) => {
    const page = await browser.newPage({ viewport: { width: w, height: hgt }, deviceScaleFactor: 1 });
    page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    page.on('pageerror', e => errors.push(String(e)));
    await page.goto(URL(q));
    await page.waitForSelector('.start, .cw-bar, .ov, .en, main', { timeout: 30000 });
    await page.waitForTimeout(400);
    return page;
  };
  const axe = async (page, name) => {
    await page.evaluate(() => Promise.all(document.getAnimations().filter(x => x.effect && x.effect.getTiming().iterations !== Infinity).map(x => x.finished.catch(() => null))));
    await page.addScriptTag({ content: axeSrc });
    const r = await page.evaluate(async () => await axe.run(document, { runOnly: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] }));
    failures += r.violations.length;
    console.log(name.padEnd(48), 'axe:', r.violations.map(v => v.id + '(' + v.nodes.length + ')').join(', ') || 'none');
    for (const v of r.violations) for (const x of v.nodes.slice(0, 3)) console.log('   ', v.id, x.target.join(' '), (x.failureSummary || '').split('\n').slice(1, 2).join(''));
  };
  const smallest = async (page, name) => {
    const r = await page.evaluate(() => {
      let min = 99, where = '';
      for (const el of document.querySelectorAll('body *')) {
        if (!(el instanceof HTMLElement) || el.closest('svg')) continue;
        const own = [...el.childNodes].some(n => n.nodeType === 3 && n.textContent.trim());
        if (!own) continue;
        const b = el.getBoundingClientRect();
        if (!b.width || !b.height || el.closest('.sr')) continue;
        const f = parseFloat(getComputedStyle(el).fontSize);
        if (f < min) { min = f; where = el.className + ' "' + el.textContent.trim().slice(0, 30) + '"'; }
      }
      return { min, where };
    });
    if (r.min < 12) failures++;
    console.log(name.padEnd(48), 'smallest text', r.min + 'px', r.min < 12 ? 'TOO SMALL ' + r.where : '');
  };
  const sideways = async (page, name) => {
    const over = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    if (over > 1) fail(`${name}: scrolls sideways by ${over}px`);
  };
  const check = async (page, name) => { await axe(page, name); await smallest(page, name); await sideways(page, name); };
  /** From the title screen: New Game, then the game's card. */
  const mode = async (page, label) => {
    if (await page.locator('[data-action="new"]').count()) await page.click('[data-action="new"]');
    await page.locator('.start-mode').filter({ has: page.locator('strong', { hasText: new RegExp('^' + label + '$') }) }).click();
    await page.waitForTimeout(150);
  };
  /** On to the setup's last step, checking each step on the way; the step titles seen. */
  const toReady = async (page, name) => {
    const seen = [];
    for (let i = 0; i < 8 && !(await page.locator('.setup-begin').count()); i++) {
      seen.push(((await page.locator('.setup-title').textContent()) || '').trim());
      if (name) await check(page, `${name}: ${seen[seen.length - 1]}`);
      await page.click('.setup-next');
      await page.waitForTimeout(200);
    }
    seen.push(((await page.locator('.setup-title').textContent()) || '').trim());
    if (name) await check(page, `${name}: ${seen[seen.length - 1]}`);
    return seen;
  };
  /** Election night: skip to the result, and continue. */
  const night = async (page, name) => {
    await page.waitForSelector('.en', { timeout: 60000 }).catch(() => fail(`${name}: no election night`));
    await page.locator('.en-controls button', { hasText: 'Skip to the result' }).click();
    await page.waitForTimeout(200);
    const head = ((await page.locator('.en-head h1').textContent()) || '').trim();
    const cont = ((await page.locator('.en-continue').textContent()) || '').trim();
    return { head, cont };
  };

  // 1. the start screen: four games, and each one's setup
  for (const [w, hgt] of [[1600, 900], [390, 844]]) {
    const page = await open('', w, hgt);
    await check(page, `${w}: the title screen`);
    await page.click('[data-action="new"]');
    const modes = (await page.locator('.start-mode strong').allTextContents()).map(s => s.trim());
    console.log(`${w}: the modes:`, modes.join(' | '));
    if (modes.join('|') !== 'Full Game|Campaign Only|Governing Only|Individual Crisis') fail(`${w}: the start screen offers ${modes.join(', ')}`);
    if (modes.some(m => /hundred|100/i.test(m))) fail('a separate hundred-days game is still offered');
    for (const m of ['Full Game', 'Campaign Only', 'Governing Only', 'Individual Crisis']) {
      // back to the first step for each game
      while (await page.locator('.setup-back', { hasText: 'Back' }).count()) { await page.click('.setup-back'); await page.waitForTimeout(100); }
      await mode(page, m);
      if (m === 'Individual Crisis') {
        await page.click('.setup-next');
        if ((await page.locator('.crisis-card').count()) !== 2) fail('two crises to choose from');
        await page.click('.setup-back');
      }
      const seen = await toReady(page, `${w}: ${m}`);
      const go = ((await page.locator('.setup-begin').textContent()) || '').trim();
      console.log(`${w}: ${m.padEnd(18)} ->`, seen.join(' / '), '->', go);
      const want = { 'Full Game': /Begin the campaign/, 'Campaign Only': /Begin the campaign/, 'Governing Only': /Begin on election night/, 'Individual Crisis': /Begin the crisis/ }[m];
      if (!want.test(go)) fail(`${w}: ${m}'s button reads "${go}"`);
      if (m === 'Governing Only' && !seen.includes('The president-elect')) fail('Governing Only chooses the president-elect');
      if (w === 390) await page.screenshot({ path: `${OUT}/390-start-${m.replace(/ /g, '-').toLowerCase()}.png`, fullPage: true });
    }
    await page.close();
  }

  // 2. Campaign Only: the eve of 2028, won and lost
  for (const save of ['eve2028-campaign-win', 'eve2028-campaign-lose']) {
    const page = await open(`&save=saves/test/${save}.json`);
    for (let t = 0; t < 40 && !(await page.locator('.en').count()); t++) {
      const choice = page.locator('.scrim button.choice').first();
      if (await choice.count()) { await choice.click(); await page.waitForTimeout(150); continue; }
      const go = page.locator('.scrim button.primary').first();
      if (await go.count()) { await go.click(); await page.waitForTimeout(150); continue; }
      await page.locator('.cw-actions button.primary').click();
      await page.waitForTimeout(250);
    }
    const { head, cont } = await night(page, save);
    console.log(save.padEnd(22), head, '|', cont);
    if (!/To the final report/.test(cont)) fail(`${save}: the night ends with "${cont}", not the final report`);
    if (!(await page.locator('.en-analysis h3', { hasText: 'The campaign:' }).count())) fail(`${save}: the analysis has no campaign assessment`);
    await page.locator('.en-continue').click();
    await page.waitForTimeout(500);
    const title = await page.title();
    const result = ((await page.locator('.end-result').textContent().catch(() => '')) || '').trim();
    console.log('   ->', title, '|', result);
    if (!/The campaign/.test(title)) fail(`${save}: the end page is "${title}"`);
    if (/win/.test(save) !== /You won the White House/.test(result)) fail(`${save}: the end says "${result}"`);
    if ((await page.locator('#end-as + ul li').count()) < 3) fail(`${save}: the assessment is missing`);
    await page.locator('details summary', { hasText: 'Every state' }).click();
    if ((await page.locator('.end-states tbody tr').count()) < 50) fail(`${save}: not every state is listed`);
    await check(page, `${save}: the campaign's end`);
    await page.screenshot({ path: `${OUT}/${save}-end.png`, fullPage: true });
    // the night again, then back to the end; then a replay in the same world
    await page.locator('button', { hasText: 'Watch election night again' }).click();
    await page.waitForSelector('.en', { timeout: 10000 }).catch(() => fail(`${save}: the night does not replay`));
    await page.locator('.en-controls button', { hasText: 'Skip to the result' }).click();
    await page.locator('.en-continue').click();
    await page.waitForTimeout(400);
    if (!(await page.locator('.end-result').count())) fail(`${save}: after the replayed night, not back at the end`);
    await page.locator('button', { hasText: 'Replay the campaign in the same world' }).click();
    await page.waitForSelector('.cw-bar', { timeout: 30000 }).catch(() => fail(`${save}: the replay does not open the war room`));
    await page.close();
  }

  // 3. Governing Only from the start screen
  {
    const page = await open('', 1600, 900);
    await mode(page, 'Governing Only');
    await toReady(page, null);
    await page.click('.setup-begin');
    const t0 = Date.now();
    const { head, cont } = await night(page, 'Governing Only');
    console.log('Governing Only: election night in', Date.now() - t0, 'ms:', head, '|', cont);
    if (!/You win the presidency/.test(head)) fail(`Governing Only opens on "${head}"`);
    if (!/2028/.test(await page.title())) fail('Governing Only opens on the 2028 night');
    if (!/On to the transition/.test(cont)) fail(`Governing Only's night goes to "${cont}"`);
    await page.locator('.en-continue').click();
    await page.waitForTimeout(500);
    if (!/The transition/.test(await page.title())) fail(`after the night: "${await page.title()}"`);
    await page.close();
  }

  // 4. Individual Crisis: the last turn, then the after-action report
  for (const kind of ['hormuz', 'taiwan']) {
    const page = await open(`&save=saves/test/crisis-${kind}-last.json`);
    for (let t = 0; t < 6 && !(await page.locator('.end-result').count()); t++) {
      const room = page.locator('.scrim .btn', { hasText: /Next day|Next \d+ hours|hours/ }).first();
      if (await room.count()) { await room.click(); await page.waitForTimeout(400); continue; }
      const choice = page.locator('.scrim button.choice').first();
      if (await choice.count()) { await choice.click(); await page.waitForTimeout(200); continue; }
      const go = page.locator('.scrim .btn.primary').first();
      if (await go.count()) { await go.click(); await page.waitForTimeout(200); continue; }
      await page.locator('.ov-end').click();
      await page.waitForTimeout(500);
    }
    const title = await page.title();
    const result = ((await page.locator('.end-result').textContent().catch(() => '')) || '').trim();
    console.log(`crisis ${kind}:`.padEnd(22), title, '|', result);
    if (!/After-action report/.test(title)) fail(`${kind}: the crisis's end is "${title}"`);
    if ((await page.locator('#end-fig + ul li').count()) < 5) fail(`${kind}: the report's figures are missing`);
    await check(page, `${kind}: the after-action report`);
    await page.screenshot({ path: `${OUT}/crisis-${kind}-report.png`, fullPage: true });
    await page.close();
  }

  // 5. day 100: the paper, the report card, and the presidency goes on
  {
    const page = await open('&save=saves/test/day100-eve.json');
    await page.waitForSelector('.ov-end', { timeout: 30000 });
    for (let i = 0; i < 6 && !(await page.locator('.dh-report').count()); i++) {
      if (await page.locator('.scrim').count()) {
        const lg = page.locator('.scrim .lg');
        if (await lg.count()) { await page.keyboard.press('Escape'); await page.waitForTimeout(300); continue; }
        await page.locator('.dialog button.choice, .dialog .btn.primary').first().click();
        await page.waitForTimeout(250);
        continue;
      }
      await page.locator('.ov-end').click();
      await page.waitForTimeout(600);
    }
    if (!(await page.locator('.dh-report').count())) fail('day 100 brings no report card');
    else {
      console.log('day 100:', ((await page.locator('.dh-report .verdict-grade').textContent()) || '').trim());
      await check(page, 'the 100-day report');
      await page.screenshot({ path: `${OUT}/day100-report.png` });
      await page.locator('.dh-report button', { hasText: 'Keep governing' }).click();
      await page.waitForTimeout(300);
    }
    while (await page.locator('.scrim').count()) { await page.keyboard.press('Escape'); await page.waitForTimeout(200); }
    const end = ((await page.locator('.ov-end').textContent().catch(() => '')) || '').trim();
    if (!end) fail('after the report, the Oval Office is not there');
    console.log('after day 100, the turn button reads:', end);
    await page.click('.ov-actions button:has-text("Menu")');
    await page.waitForSelector('.menu');
    const item = page.locator('.menu .menu-item', { hasText: 'The 100-day report' });
    if (!(await item.count())) fail('the menu does not offer the 100-day report');
    else {
      await item.click();
      await page.waitForSelector('.dh-report', { timeout: 5000 }).catch(() => fail('the menu does not open the 100-day report'));
    }
    await page.close();
  }

  await browser.close();
  console.log('errors', errors.length, errors.slice(0, 5).join('\n'));
  failures += errors.length;
  console.log(failures ? failures + ' FAILURES' : 'ALL PASS');
  process.exit(failures ? 1 : 0);
})();
