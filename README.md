# American Grand Strategy

An American political strategy game. Win the 2028 presidential election from a campaign war room, take the oath, and govern week by week for as long as you serve:
- Congress and the budget;
- an economy that every law moves;
- the West Wing and the press;
- the country's groups;
- a world of forty countries.

Two crises can come in depth: the Strait of Hormuz, and a war over Taiwan. Win again in 2032 for a second term. At the end, history gives its verdict.

The game is built from three earlier games, ported rather than copied:
- **Battleground 2028**, the campaign;
- **Resolute Desk** (Version 2), the presidency;
- **First Island Chain**, the war over Taiwan.

Those repositories are kept untouched; see `docs/SOURCE_AUDIT.md`.

## Play in your browser

**The whole game:** https://lukewaadams-gif.github.io/American-Grand-Strategy/preview/

Nothing to install: the simulation runs in the page. Use a current Chrome, Edge, Firefox or Safari, on a computer or a phone.

**The start screen offers:**
- **A new campaign.** Choose your party, your candidate and your opponent, the country's mood, and how hard the other side campaigns. Everyone is fictional unless you turn on real people: real politicians, real leaders abroad and the real Supreme Court.
- **A saved game.** **Save the game** (in the menu) writes a file you can open again here.
- **A moment:**
  - the campaign at week 9;
  - the first hundred days;
  - governing in September 2029;
  - the Taiwan crisis;
  - four more years;
  - the race to succeed you.
- **A crisis at once:** the Strait of Hormuz (base case, optimistic or pessimistic).
- **Island Chain on its own:** the Taiwan Strait war game without a presidency. Pick a year from 2029 to 2036, play America or China, and choose a preset.

**Keys:**
- **Ctrl+Enter** (⌘+Enter on a Mac) plays the turn, in the Oval Office or inside a crisis room.
- **Escape** closes a room, or takes the controls back from watch mode.

The site's front page (https://lukewaadams-gif.github.io/American-Grand-Strategy/) still has the first playable slice: the 2028 election and the first hundred days, in the earlier interface. The preview replaces it once it is approved.

## What a game plays

1. **The 2028 campaign,** in Battleground 2028's war room:
   - the headquarters;
   - the candidate's schedule on the map;
   - the ground game;
   - the money and the air war: TV buys, digital, and an ad studio.

   You play weekly turns, then the final stretch day by day. The polls run on a calibrated 96-cell electorate, with correlated error and a hidden polling miss. The opponent adapts and tells you why.
2. **Election night, and the transition office.** Shortlists, interviews and background checks for the cabinet and the staff, and the agenda you take to Congress.
3. **The Oval Office, week by week,** after Resolute Desk:
   - the president's 21-slot week, stamina and health;
   - the calendar, and The Capital Ledger every week.

   The chief of staff can plan your weeks, and watch mode lets the staff govern through quiet stretches.
4. **The rooms of the presidency:**
   - **The economy:** a structural model with output, jobs, prices, rates, the Fed, the deficit and markets. Every law moves a policy table, each change scored with a range before the vote.
   - **Congress and the budget,** in the Roosevelt Room: hours on the Hill, favours and promises; the blocs and the members; whip counts; drafting and shaping bills; deadlines and shutdowns.
   - **The West Wing and the Press Briefing Room:** the staff and the cabinet, executive orders and the courts, nominations, scandals and developing stories, press conferences and addresses.
   - **The Residence, and the country's groups.**
   - **The world and the Situation Room:** forty countries, the hot spots, calls, trade, trips abroad, operations and wars of choice.
5. **The crises:**
   - **The Strait of Hormuz** (`docs/HORMUZ.md`), day by day:
     - Iran's ladder, mines and missiles;
     - escorts, minesweepers and strikes;
     - envoys and pressure;
     - the oil reserve and the pipelines;
     - three invasion plans.
   - **The Taiwan Strait** (`docs/ISLAND_CHAIN.md`): the First Island Chain wargame, ported. China's escalation opens a crisis of days. If China attacks, the war follows in turns of 1, 6 and 12 hours, with:
     - the war at sea and in the air;
     - the landings and the ground war on Taiwan;
     - the blockade and Taiwan's energy;
     - the chip shock;
     - the allies;
     - nuclear risk.

     You set the rules of engagement and the theatre directives; the staff runs the units.
   - **Their fallout** reaches the whole presidency: the dead, approval, the economy and markets, the deficit, the allies, and the record.
6. **The midterms, the 2032 campaign, and a second term.** The second term brings the lame-duck decline, staff turnover, legacy goals, and campaigning for your party's 2036 nominee.
7. **The verdict of history,** on the whole presidency: the economy, the laws and what they did, war and peace, approval and the Congresses faced, and a historians' ranking.

The documents behind each part:
- the plans and decisions: `docs/CAMPAIGN_PORT.md`, `docs/PRESIDENCY_PORT.md` and `docs/CRISES.md`;
- the economy's model: `docs/ECONOMY_MODEL.md`;
- the choices made without being discussed, for review: `docs/DECISIONS_TO_REVIEW.md`.

## Build it

Requires Java 21, Maven 3.9 and Node 20 or later.

```bash
mvn -B install
```

This builds and tests the simulation (`mandate-core`, with its golden regression fingerprints), the desktop interface and the executable. It also compiles the simulation to JavaScript with TeaVM. The first build downloads TeaVM from Maven Central.

```bash
cd mandate-web && npm install && npm run build
```

This builds the browser game: the map data, the TypeScript interface, and the engine and sample saves. It is assembled into `mandate-web/target/preview`; serve that folder with any static web server.

**Checks:**
- **The browser checks** are Playwright scripts. They cover every room at desktop and phone widths, in three palettes and at 125% text, with axe-core WCAG 2.1 AA audits. They are in `mandate-web/src/test/browser` (see its README).
- **The parity check** shows that the same seed plays the same game in Java and in JavaScript. It covers forty autopilot games, the policy scores, a Hormuz crisis, a Taiwan crisis in the presidency, and an Island Chain invasion. The two outputs must match apart from the timings:

```bash
mvn -B -pl mandate-web package -Pparity -DskipTests
```

```bash
cp mandate-web/target/parity/parity.js /tmp/parity.cjs && node -e "require('/tmp/parity.cjs').main([])" > parity-js.txt
```

```bash
java -cp mandate-web/target/classes:mandate-core/target/classes game.mandate.web.ParityCheck > parity-jvm.txt
```

```bash
diff <(grep -v '^ms ' parity-js.txt) <(grep -v '^ms ' parity-jvm.txt)
```

## Play on the desktop

```bash
java -jar mandate-cli/target/mandate.jar
```

The desktop game (Swing) and the terminal game (`java -jar mandate-cli/target/mandate.jar text`) play the same simulation, with the earlier screens: Overview, Campaign HQ, Oval Office, Congress, Economy, Security, Timeline, Results, Save and load, and Settings. The new rooms are in the browser. `./mandate` builds first if needed. The desktop game autosaves to `~/.mandate/save.json` (or `$MANDATE_HOME/save.json`) after every turn.

## Designer tools

```bash
./mandate batch 1000 TOSS_UP     # balance report over autopilot games (also BLUE_ENVIRONMENT, RED_ENVIRONMENT)
./mandate auto 7 D               # the autopilot plays one full game and prints the log
./mandate weeks 5 D unified      # the 12-week governing prototype on its own
./mandate compare 300            # the prototype's strategy probes side by side
./mandate screens target/screens # render every desktop screen to PNG and run the interface checks
```

```bash
java -cp mandate-core/target/classes game.mandate.core.taiwan.TaiwanHarness 12 2030
```

This is Island Chain's calibration harness: each plan and preset, both sides by the computer. The arguments are the games per cell, the year, and optionally the forces' year.

## Engine

There is one authoritative `WorldState`, and each part of it is owned by one engine. The engines cover:
- the campaign and the elections;
- the economy;
- politics and opinion;
- the president's week;
- Congress (the Hill);
- the West Wing;
- the terms of office;
- the world;
- the military;
- the two crises.

How they work together:
- **Transitions.** Engines change each other only through `Effect` transitions. `GameEngine` routes every one to its owner and records it in the consequence ledger.
- **Commands.** Every player action is a `Command`. The same commands from the same seed give the same world, on the JVM and in the browser.
- **Saves.** A save is a snapshot (the world, the random state and the command log) that replays exactly.
- **Constants.** The balancing constants live in `data/tuning.properties`; the ported games' tables are CSV files under `data/`.

See [ARCHITECTURE.md](ARCHITECTURE.md).

## Layout

```
mandate-core/   the simulation (no UI imports, enforced by a test)
mandate-web/    the browser game: the core compiled to JavaScript by TeaVM, and the TypeScript interface (src/main/ts)
mandate-ui/     the desktop interface (Swing): design system, screens, automated UI checks, screenshot tool
mandate-cli/    the executable (desktop by default), the terminal game and the designer tools; builds mandate.jar
tools/          BgExport: one-off export of Battleground 2028's electorate and roster
docs/           the port plans, the crises, the economy's model, decisions to review, the source audit and research notes
```

## Documents

| | |
|---|---|
| [docs/PRESIDENCY_PORT.md](docs/PRESIDENCY_PORT.md) | The presidency: Resolute Desk's systems, the milestones and what each built |
| [docs/CAMPAIGN_PORT.md](docs/CAMPAIGN_PORT.md) | The campaign: Battleground 2028's war room, ported |
| [docs/CRISES.md](docs/CRISES.md), [docs/HORMUZ.md](docs/HORMUZ.md), [docs/ISLAND_CHAIN.md](docs/ISLAND_CHAIN.md) | The two in-depth crises: design, calibration and how they reach the presidency |
| [docs/ECONOMY_MODEL.md](docs/ECONOMY_MODEL.md) | The structural economy and the policy table |
| [docs/DECISIONS_TO_REVIEW.md](docs/DECISIONS_TO_REVIEW.md) | Choices made without being discussed, for review |
| [PRODUCT_VISION.md](PRODUCT_VISION.md) | What the game is and is not, the pillars, the audience |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Modules, command model, determinism, formulas, ADRs |
| [UI_DESIGN.md](UI_DESIGN.md), [docs/ART_DIRECTION.md](docs/ART_DIRECTION.md) | The interfaces, the design system, accessibility and the look |
| [GAMEPLAY_DESIGN.md](GAMEPLAY_DESIGN.md), [FEATURE_PRIORITY.md](FEATURE_PRIORITY.md) | Loop audits, the core loop, and every source mechanic classified |
| [VERTICAL_SLICE.md](VERTICAL_SLICE.md) | The first playable slice (the 2028 election and the first hundred days) |
| [DEVELOPMENT_ROADMAP.md](DEVELOPMENT_ROADMAP.md), [RISK_REGISTER.md](RISK_REGISTER.md) | Milestones, release gates, risks |
| [docs/SOURCE_AUDIT.md](docs/SOURCE_AUDIT.md) | The source games: commits, licensing, what was ported |
