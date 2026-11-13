# Browser checks

Three Playwright scripts test the browser build in Chromium. They are not part of `mvn install`: they need a browser and Node.

| Script | What it does |
|---|---|
| `flow.js` | Plays the game through its own buttons: the new-game wizard, whole campaigns until one is won, election night, the transition, fourteen weeks in office and the 100-day report. Fails on any console error. |
| `checks.js` | Loads real game moments (desktop saves) and checks 41 views: every screen at 1280×720, 1600×900 and 1920×1080, 150% text, three palettes, three complexity levels, and the dialogs behind buttons. Each view gets layout checks (no sideways scrolling, nothing sticking out of its card, nothing cut off without a tooltip, no text under 12 px, every button named, every disabled button explained) and an axe-core WCAG 2.1 AA audit. Writes a PNG per view and `checks.txt`. |
| `preview.js` | The new interface (`target/preview`): the campaign headquarters with Battleground 2028's schedule, ground game and air war. Uses the schedule's menus and map picks by mouse and by keyboard, field orders, TV buys, digital, red boxes and the Ad Studio, and plays a week and a day of the final stretch with their reviews. Every view gets an axe-core WCAG 2.1 AA audit and a minimum text size check, in three palettes, with reduced motion and 125% text, and at 1280 and 1000 pixels wide. Fails on any console error. |
| `presidency.js` | The presidency preview (`target/preview/presidency.html`): the Oval Office and the president's week. Uses all twelve hotspots by mouse (label, dialog, focus coming back) and by keyboard; plans the week on the calendar (a menu by keyboard, an issue, a state from the list and from the map, a road week, the chief of staff); hands the hundred days' flagship bill to the staff; plays weeks with The Capital Ledger after each; runs watch mode and takes the controls back; switches the light; and plays a governing week. Every view gets an axe-core WCAG 2.1 AA audit and a minimum text size check, in three palettes, with reduced motion and 125% text, and at 1280, 1000 and 390 pixels wide; hotspots must be at least 24 px from 1000 px up. Fails on any console error. |
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

For `preview.js` and `presidency.js`, build the new interface and serve it instead:

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
