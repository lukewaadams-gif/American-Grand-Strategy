# Browser checks

Playwright scripts test the browser build in Chromium. They are not part of `mvn install`: they need a browser and Node.

| Script | What it does |
|---|---|
| `flow.js` | Plays the game through its own buttons: the new-game wizard, whole campaigns until one is won, election night, the transition, fourteen weeks in office and the 100-day report. Fails on any console error. |
| `checks.js` | Loads real game moments (desktop saves) and checks 41 views: every screen at 1280×720, 1600×900 and 1920×1080, 150% text, three palettes, three complexity levels, and the dialogs behind buttons. Each view gets layout checks (no sideways scrolling, nothing sticking out of its card, nothing cut off without a tooltip, no text under 12 px, every button named, every disabled button explained) and an axe-core WCAG 2.1 AA audit. Writes a PNG per view and `checks.txt`. |
| `preview.js` | The new interface's campaign preview (`target/preview/campaign.html`): the campaign headquarters with Battleground 2028's schedule, ground game and air war. Uses the schedule's menus and map picks by mouse and by keyboard, field orders, TV buys, digital, red boxes and the Ad Studio, and plays a week and a day of the final stretch with their reviews. Every view gets an axe-core WCAG 2.1 AA audit and a minimum text size check, in three palettes, with reduced motion and 125% text, and at 1280 and 1000 pixels wide. Fails on any console error. |
| `presidency.js` | The presidency preview (`target/preview/presidency.html`): the Oval Office and the president's week. Uses all twelve hotspots by mouse (label, dialog, focus coming back) and by keyboard; plans the week on the calendar (a menu by keyboard, an issue, a state from the list and from the map, a road week, the chief of staff); hands the hundred days' flagship bill to the staff; plays weeks with The Capital Ledger after each; runs watch mode and takes the controls back; switches the light; and plays a governing week. Every view gets an axe-core WCAG 2.1 AA audit and a minimum text size check, in three palettes, with reduced motion and 125% text, and at 1280, 1000 and 390 pixels wide; hotspots must be at least 24 px from 1000 px up. Fails on any console error. |
| `economy.js` | The presidency's economy (milestone 4), on the governing save: the briefing book's prices by part, industries, budget and laws; the West Wing and the Cabinet Room, a policy's score and a bill's score, and the agenda's scores. Every view gets an axe-core WCAG 2.1 AA audit, a minimum text size check and a sideways-scroll check at 1600, 1280 and 390 pixels wide; times a score; fails on any console error. |
| `congress.js` | Congress (milestone 5), on the governing save: the Roosevelt Room's six tabs at 1600, 1280 and 390 pixels wide and in the high-contrast and colour-blind palettes, at 125% text and with reduced motion (axe-core WCAG 2.1 AA, text size, sideways scroll); the telephone opening on the members; a meeting with a leader; drafting, shaping and sending a bill; a call on its votes; weeks played until the Hill puts a decision on the desk. Fails on any console error. |
| `westwing.js` | The executive (milestone 6), on the governing save: the West Wing's six tabs and the Press Briefing Room's four at 1600, 1280 and 390 pixels wide and in the other palettes, at 125% text and with reduced motion (axe-core WCAG 2.1 AA, text size, sideways scroll); hiring a staff member, the vice president's tour, signing a library order, an order on one policy from the Cabinet Room, a press conference and an address answered on the desk, and weeks played with the paper's developing stories. Fails on any console error. |
| `residence.js` | The Residence and the groups (milestone 7), on the governing save: the country room with its groups and the Residence at 1600, 1280 and 390 pixels wide and in the other palettes, at 125% text and with reduced motion (axe-core WCAG 2.1 AA, text size, sideways scroll); a family weekend going onto the calendar. Fails on any console error. |
| `play.js` | The whole game (milestone 10, the preview's front page): the start screen and a new campaign; the transition office (an interview, a background check, an announcement, the agenda) and the oath into the Oval Office; the verdict as the last page; at 1600 and 390 pixels wide and in the other palettes (axe-core WCAG 2.1 AA, text size, sideways scroll). Fails on any console error. |
| `sitroom.js` | The Situation Room (milestone 8), on the governing save: every tab at 1600, 1280 and 390 pixels wide and in the other palettes, at 125% text and with reduced motion (axe-core WCAG 2.1 AA, text size, sideways scroll); a call, a tariff, an objective tried, a war planned and stood down; a trip abroad filling three slots on the calendar. Fails on any console error. |
| `terms.js` | The second term (milestone 9), on the second-term, succession and handoff saves: the second term's theme on the desk and the weeks to the second inauguration; the Press Briefing Room's "Your term" tab in the race to succeed the president, with an endorsement; the verdict of history at the handoff; at 1600, 1280 and 390 pixels wide and in the other palettes (axe-core WCAG 2.1 AA, text size, sideways scroll). Fails on any console error. |
| `hormuz.js` | The Strait of Hormuz crisis, on the Hormuz save: the Oval Office in daily turns, the crisis room (the ladder, the map, the oil, Iran and the tools), escorts, an envoy, the reserve, a strike with its confirmation, a day played from the room, and the crisis started from the start screen; at 1600 and 390 pixels wide and in the other palettes (axe-core WCAG 2.1 AA, text size, sideways scroll). Fails on any console error. |
| `taiwan.js` | The Taiwan crisis, on the crisis-day and war saves: the Oval Office in crisis days and war turns; the crisis room (both ladders, the Western Pacific, Taiwan by sector, warning, directives, allies, preparations, the economy, nuclear risk and the toll); a directive, a request to Taipei, a sanction with its confirmation, handing back to the staff, and a turn played from the room; Island Chain on its own from the start screen, as China (a plan adopted, a day played) and as America at 390 pixels; at 1600 and 390 pixels wide and in the other palettes (axe-core WCAG 2.1 AA, text size, sideways scroll). Fails on any console error. |
| `election.js` | Election Day and election night, from test saves on the eve of the 2028 and 2032 elections (one won and one lost each; make them with `java -cp mandate-cli/target/mandate.jar game.mandate.ui.ElectionSaves mandate-web/target/preview/saves/test`). It runs the final days through the war room's own button and checks it reads "Run the day". It checks that the broadcast starts with nothing called, steps, plays, pauses, and skips to a result whose scoreboard equals the engine's. It then continues to the transition, the final report or the Oval Office. From saves on the night itself: it checks that a reload resumes the broadcast at the same report, and that a night once watched is not shown again. Fails on any console error. |
| `modes.js` | The four games (docs/MODES.md), through the interface's own buttons. The start screen offers exactly Full Game, Campaign Only, Governing Only and Individual Crisis, with each one's setup at 1600 and 390 pixels wide. It checks Campaign Only from the eve of 2028, won and lost: the night, the campaign's end (the assessment, the vote, every state), the night watched again, and a replay in the same world. It checks Governing Only from the start screen to the 2028 night and the transition. It plays Individual Crisis's last turn, for Hormuz and for Taiwan, to the after-action report. And it reaches day 100: the paper, the report card, "Keep governing" and the report in the menu. Every end screen gets an axe-core WCAG 2.1 AA audit, a text-size check and a sideways-scroll check. Needs the test saves (`ElectionSaves`). Fails on any console error. |
| `broadcast-unit.mjs` | The election-night script (`presidency/broadcast.ts`) against 400 synthetic results. Every race is called for its real winner, never before its polls close and never while the other side leads the count. The projection comes exactly at 270. The final count and popular vote are the result, and the same result always gives the same night. Usage: `node broadcast-unit.mjs mandate-web/target/preview`. |
| `interactions.js` | Reload and Continue, undo (Ctrl+Z), the single-key shortcuts, the number keys in a decision card, importing a desktop save from a file, exporting a save, a setting surviving a reload, and the Tab order. |

## Running them

Build the site and the game moments, serve the site, then run a script.

```bash
mvn -B install
```

```bash
java -cp mandate-cli/target/mandate.jar game.mandate.ui.Fixtures target/moments
```

```bash
(cd mandate-web/target/site && python3 -m http.server 8765 --bind 127.0.0.1) &
```

```bash
cd mandate-web/src/test/browser && npm install
```

```bash
node checks.js ../../../target/browser-checks ../../../../target/moments
```

```bash
node interactions.js ../../../target/browser-checks ../../../../target/moments
```

```bash
node flow.js ../../../target/browser-checks
```

If Playwright's own browser is not installed on the machine, `npx playwright install chromium` fetches it.

The paths are relative to `mandate-web/src/test/browser`. Each script takes the output directory first; `checks.js` and `interactions.js` then take the directory of moments.

For `preview.js`, `presidency.js`, `economy.js`, `congress.js`, `westwing.js`, `residence.js`, `sitroom.js`, `terms.js` and `play.js`, build the new interface and serve it instead:

```bash
(cd mandate-web && npm install && npm run build)
```

```bash
(cd mandate-web/target/preview && python3 -m http.server 8766 --bind 127.0.0.1) &
```

```bash
cd mandate-web/src/test/browser && node preview.js /tmp/preview-checks
```

```bash
cd mandate-web/src/test/browser && node presidency.js /tmp/presidency-checks
```

```bash
cd mandate-web/src/test/browser && node economy.js /tmp/economy-checks
cd mandate-web/src/test/browser && node congress.js /tmp/congress-checks
cd mandate-web/src/test/browser && node westwing.js /tmp/westwing-checks
cd mandate-web/src/test/browser && node residence.js /tmp/residence-checks
cd mandate-web/src/test/browser && node sitroom.js /tmp/sitroom-checks
cd mandate-web/src/test/browser && node terms.js /tmp/terms-checks
cd mandate-web/src/test/browser && node play.js /tmp/play-checks
```
