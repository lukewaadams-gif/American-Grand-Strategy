# American Grand Strategy

A turn-based strategy game about American power, played on a paper map of hexes on a walnut table. Lead a party for half a century: build a coalition out of rival factions, take the map election by election, and govern what you win. This folder holds the **vertical slice**: the Long Game from Q1 2000 to the end of 2001, as a free web demo.

- **[docs/DESIGN.md](docs/DESIGN.md)**: the design brief (modes, board, systems, victories, look, technology).
- **[docs/MODDING.md](docs/MODDING.md)**: how to write a mod.

## How it is built

| Part | What | Where |
|---|---|---|
| Simulation | Rust, compiled to WebAssembly with no wasm-bindgen; a JSON request goes in and a JSON reply comes out. It is deterministic: SplitMix64, and only `+ − × ÷` and `sqrt`, so the browser and native builds play the same game bit for bit. | `sim/` |
| Data | Every leader, faction, unit, idea, doctrine, law, calendar turn and event card, as JSON. | `data/` |
| Map | Hexes built from real geography (us-atlas, Albers USA): terrain from latitude and longitude, 76 cities, rivers, state lines. The world board comes from world-atlas. | `tools/build-world.mjs` → `web/gen/` |
| Front end | TypeScript and PixiJS 8 for the board; HTML for the paper cards, binder and dossiers. Bundled with esbuild. | `web/src/` |
| Mods | JSON files that patch the data at load time. | `mods/`, `web/src/mods.ts` |

Saves are the setup plus the command log, replayed exactly. A save records its mods and refuses to load under a different set.

## Build and run

You need Rust with the `wasm32-unknown-unknown` target, and Node 20 or later.

```bash
rustup target add wasm32-unknown-unknown
```

```bash
cd ags/web && npm ci && npm run build
```

```bash
python3 -m http.server 8795 --directory ags/web/dist
```

Then open http://127.0.0.1:8795/.

## Tests

```bash
cd ags/web && npm test
```

Run that after a build, because the tests read `web/gen/map.json`. It runs the Rust suite:

- A bot plays the slice to the end with every leader.
- The same seed gives the same game, and a save replays exactly.
- Native and WebAssembly builds agree.
- Standing orders act and lapse.
- A competent bot wins most games at the easiest level, few at the hardest, and never more at a harder level.

```bash
cd ags/web && npm run play
```

That plays the whole slice through the real interface in Chromium (Playwright), takes screenshots, and fails on any console error. Pass a width, height and leader to try other screens: `node test/playthrough.cjs <out> 8795 390 844 3`.

```bash
cd ags/web && npm run mods
```

That installs the example mod, plays to its card, and checks that saves keep their mods and that a broken mod is refused without breaking the game.

```bash
cd ags/web && npm run a11y
```

That runs axe-core's WCAG 2.1 A and AA rules on every kind of screen: the title, setup, the board, event cards, the binder, the dossiers and the Mods card.
