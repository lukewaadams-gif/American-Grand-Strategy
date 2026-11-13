# Mandate (working title)

An American political strategy game. Win the 2028 presidential election, take the oath, and govern your first hundred days. In a full-term game you go on to govern to 2032 alongside Congress, the economy and foreign crises, then run for re-election on the record you built.

The first complete **vertical slice** is playable: the 2028 election, the transition, the inauguration and the first hundred days in office. It is one continuous game, ending in a graded 100-day report with a replay. See [VERTICAL_SLICE.md](VERTICAL_SLICE.md).

The design draws on three earlier games: Battleground 2028, Resolute Desk and First Island Chain. Those repositories are kept untouched; see `docs/SOURCE_AUDIT.md`.

## Play in your browser

**https://lukewaadams-gif.github.io/American-Grand-Strategy/**

Nothing to install: the whole game runs in the page, in a current Chrome, Edge, Firefox or Safari on a computer (a window of 1280×720 or more). Your games are saved in your browser after every turn. **Save and load** can export a game to a file and import one, including saves from the desktop game.

In the browser, single keys work the screens, since browsers keep Ctrl+1 to 8 for their tabs:
- **1–8** switch screens, **S** opens save and load, and **,** opens settings;
- **D** opens the decisions waiting for you;
- **?** lists every shortcut and mechanic, and **A** shows or hides the adviser;
- **Ctrl+Enter** ends the turn and **Ctrl+Z** undoes, as on the desktop.

The browser build is the same simulation compiled to JavaScript (`mandate-web`; see UI_DESIGN.md §17). The same seed plays the same game as on the desktop: 40 of 40 autopilot games matched exactly.

## Play on the desktop

Requires Java 21 and Maven 3.9.

```bash
mvn -B install
```

This builds the code, runs the 120 tests (including the interface's automated checks), writes the single executable `mandate-cli/target/mandate.jar`, and builds the browser version into `mandate-web/target/site` (open it through any static web server). The first build downloads the TeaVM compiler from Maven Central.

```bash
java -jar mandate-cli/target/mandate.jar
```

This opens the desktop game: a 1280×720 or larger window with a main menu, a four-step new-game setup and the game's screens. These are Overview, Campaign HQ, Oval Office, Congress, Economy, Security, Timeline, Results, Save and load, and Settings. Ctrl+1 to Ctrl+8 switch screens, Ctrl+Enter ends the turn, Ctrl+Z undoes and F1 lists every shortcut and mechanic. Settings has text size (100–150%), high-contrast and colour-blind palettes, reduced motion, and three complexity levels. A chief adviser summarises what your screens show, and tips explain each decision the first time you meet it. See [UI_DESIGN.md](UI_DESIGN.md).

`./mandate` does the same and builds first if needed. The game autosaves to `~/.mandate/save.json`, or to `$MANDATE_HOME/save.json` if that is set, after every turn; **Continue** on the main menu resumes it.

The same game also runs in a terminal: `java -jar mandate-cli/target/mandate.jar text`, which is also what runs where there is no display. Options: `--seed N` fixes the world; `--save FILE` uses another save file. Every prompt has a default: press Enter to take it.

## What a game plays

1. **Setup.** Choose your party and candidate:
   - one of the fictional cast;
   - your own, built from 33 rating points;
   - or, opt-in, Battleground 2028's real roster.

   Then pick the opponent, the national mood, difficulty and coaching.
2. **The 2028 campaign: 16 weekly turns.**
   - Run on standing orders (a strategy preset, a spending rate, a message), or plan a week by hand.
   - Make up to three promises.
   - Choose debate postures and answer events.
   - Each week you see the polls, the trend, the forecast and what moved the race. The opponent adapts and tells you why.
   - The election runs on Battleground 2028's calibrated 96-cell electorate, with correlated error and a hidden polling miss.
3. **Election night.** A defeat comes with a post-mortem.
4. **The transition.** Set your agenda. The first priority becomes your flagship bill.
5. **The first hundred days: 14 weekly turns.** You have 3 attention points a week. You:
   - appoint a Treasury secretary, a Defense secretary and a Secretary of State;
   - design and negotiate the flagship bill through committee, the House and the Senate;
   - answer an economic shock;
   - steer a foreign crisis;
   - meet a funding deadline.

   You watch approval, your party's wings, the opposition and the economy respond. Every action shows its consequence; every week ends with a briefing.
6. **Day 100.** Five goals, each met, partly met or missed with a reason, give a **victory**, **partial success** or **failure**. Then replay from the inauguration, or replay the campaign with the same seed.

## Designer tools

```bash
./mandate batch 1000 TOSS_UP     # balance report over autopilot games (also BLUE_ENVIRONMENT, RED_ENVIRONMENT)
./mandate auto 7 D               # the autopilot plays one full term and prints the log
./mandate weeks 5 D unified      # the 12-week governing prototype on its own
./mandate compare 300            # the prototype's strategy probes side by side
./mandate war 300                # the operational war model's calibration
./mandate screens target/screens  # render every screen to PNG and run the interface checks
```

## Documents

| | |
|---|---|
| [VERTICAL_SLICE.md](VERTICAL_SLICE.md) | The playable slice: the arc, how each requirement is met, evidence, known problems |
| [PRODUCT_VISION.md](PRODUCT_VISION.md) | What the game is and is not, the pillars, the audience |
| [FEATURE_PRIORITY.md](FEATURE_PRIORITY.md) | Every source mechanic classified A-F, feature cards, and MVP / 1.0 / expansion scope |
| [UI_DESIGN.md](UI_DESIGN.md) | The desktop and browser interfaces: the source UIs reviewed, the design system, every screen, accessibility, the advisor and tutorials, automated checks, and what needs human review |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Modules, command model, determinism, formulas, porting plan, ADRs |
| [GAMEPLAY_DESIGN.md](GAMEPLAY_DESIGN.md) | Loop audits, the core loop, the governing prototype, mechanic change log, probe evidence, playtest plan |
| [RISK_REGISTER.md](RISK_REGISTER.md) | Risks, scores, mitigations |
| [DEVELOPMENT_ROADMAP.md](DEVELOPMENT_ROADMAP.md) | Milestones, release gates, prioritised backlog |
| [docs/SOURCE_AUDIT.md](docs/SOURCE_AUDIT.md) | Source commit SHAs, build checks, licensing, what is actually implemented, what was ported |

## Engine

There is one authoritative `WorldState`. Each part of it is owned by one engine: economy, politics, Congress, the executive, diplomacy, military and elections. The first hundred days own only their process state and reach the world through a `Host`.
- **Transitions.** Engines change each other only through `Effect` transitions. `GameEngine` routes every transition to its owner and records it in the consequence ledger.
- **Commands.** Every player action is a `Command`; a hundred-days action is a `Command.Act`.
- **Saves.** A save is a version-2 snapshot (world, random state, command log, plus the front end's `ui` section), and it replays exactly. Version-1 command-log saves still load.
- **Constants.** All balancing constants live in `data/tuning.properties`.

See [ARCHITECTURE.md](ARCHITECTURE.md).

## Layout

```
mandate-core/   simulation (no UI imports, enforced by a test)
mandate-ui/     the desktop interface: design system, screens, automated UI checks, screenshot tool
mandate-web/    the browser version: the same core compiled to JavaScript by TeaVM, with a DOM interface
mandate-cli/    the executable (desktop by default), the terminal game and designer tools; builds mandate.jar
tools/          BgExport: one-off export of Battleground 2028's electorate and roster
docs/           audit and supporting notes
```
