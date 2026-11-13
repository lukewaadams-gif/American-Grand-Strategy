# Browser checks

Three Playwright scripts test the browser build in Chromium. They are not part of `mvn install`: they need a browser and Node.

| Script | What it does |
|---|---|
| `flow.js` | Plays the game through its own buttons: the new-game wizard, whole campaigns until one is won, election night, the transition, fourteen weeks in office and the 100-day report. Fails on any console error. |
| `checks.js` | Loads real game moments (desktop saves) and checks 41 views: every screen at 1280×720, 1600×900 and 1920×1080, 150% text, three palettes, three complexity levels, and the dialogs behind buttons. Each view gets layout checks (no sideways scrolling, nothing sticking out of its card, nothing cut off without a tooltip, no text under 12 px, every button named, every disabled button explained) and an axe-core WCAG 2.1 AA audit. Writes a PNG per view and `checks.txt`. |
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
