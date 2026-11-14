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

**Start here:** https://lukewaadams-gif.github.io/American-Grand-Strategy/

The front page is the game's title screen. Choose **New Game** and it walks you through the setup one step at a time, then opens the game (served from `play/`, which you can also open directly: https://lukewaadams-gif.github.io/American-Grand-Strategy/play/).

Nothing to install: the simulation runs in the page. Use a current Chrome, Edge, Firefox or Safari, on a computer or a phone.

**The title screen:** **New Game**, **Continue** (open a game you saved), **Start at a moment** and **Tutorial**. The arrow keys move through it, Enter chooses.

**The tutorial** is a guided game of about fifteen to twenty minutes, through the 2028 campaign and the first year in office (`docs/TUTORIAL.md`).
- A coach narrates, rings the place to click, and waits for your move, or makes it with "Do it for me".
- Routine stretches play themselves at the speed you choose.
- After each move, a card shows what it moved. Twice, the same moment plays two ways.
- A map of the game's systems lights up as you go.
- You can jump between chapters, pick up where you left off, or leave with "Play from here" and keep the game.

**The setup, step by step.** Each step is one choice, shown as cards; **Back** and **Next** (or Escape and Enter) move between them, and the bar at the top shows where you are.
- For a campaign or a presidency: the game, your side (party and cast), your candidate (with their file: charisma, debate, money, policy and discipline), your opponent, the country (the national mood, the difficulty) and a face-off of the two tickets before you begin.
- For a crisis: the game, the crisis, how it starts, and a briefing before you begin.

The candidates are the real 2028 field by default: Josh Shapiro, Gavin Newsom, Pete Buttigieg and the rest for the Democrats; JD Vance, Marco Rubio, Ron DeSantis and the rest for the Republicans.

**Four games** on one engine (`docs/MODES.md`):
- **Full Game.** The 2028 campaign. If you win, you govern:
  - the first hundred days, which open the presidency (day 100 brings a report card, then the presidency goes on);
  - the 2030 midterms;
  - re-election in 2032;
  - if you are re-elected, a second term to January 20, 2037, with the 2034 midterms and the 2036 race to succeed you.

  Lose in 2028, and the game ends on election night.
- **Campaign Only.** The 2028 campaign alone, to election night: the result, the popular vote, every state and an assessment of the campaign.
- **Governing Only.** The presidency alone, from election night on November 7, 2028, with the administration you choose already elected. It is the same presidency as the Full Game.
- **Individual Crisis.** The Taiwan crisis or the Strait of Hormuz, with a preset (base case, optimistic or pessimistic), on the presidency's own systems. It ends when the crisis does: won, lost or ended in part, with an after-action report. Alongside it is **Island Chain**, the Taiwan war game on its own: pick a year from 2029 to 2036 and play America or China.

The world is **real people** by default: the candidates, senators, congressional leaders, cabinet prospects, justices and leaders abroad of 2028. Fictional newcomers appear from 2030. Turn real people off for a fictional cast throughout.

**Also on the title screen:**
- **A saved game.** **Save the game** (in the menu) writes a file you can open again with **Continue**.
- **A moment of the Full Game:**
  - the campaign at week 9;
  - Inauguration week;
  - governing in September 2029;
  - a Taiwan crisis;
  - four more years;
  - the race to succeed you.
- **The build stamp,** at the foot of the screen: the build, its source commit and the day it was built.

**Every presidential election has its election night:** 2028, 2032, and the 2036 race to succeed you. Each is a broadcast of poll closings, the count, race calls and the projection. The result is computed once, on Election Day, and a reload resumes the night where it was.

**Keys:**
- **Ctrl+Enter** (⌘+Enter on a Mac) plays the turn, in the Oval Office or inside a crisis room.
- **Escape** closes a room, or takes the controls back from watch mode.

**The site's folders:**
- the root: the front page, the title screen;
- `play/`: the approved game (approved 14 November 2026; its build stamp, at the foot of the title screen, names the commit);
- `preview/`: a build under review, before it is approved. Today it is the same build as `play/`;
- `classic/`: the first playable slice, in the earlier interface (the 2028 election and the presidency's first years, with Full Game and Campaign Only), kept unchanged. The title screen links it at its foot.

## What a game plays

1. **The 2028 campaign,** in Battleground 2028's war room:
   - the headquarters;
   - the candidate's schedule on the map;
   - the ground game;
   - the money and the air war: TV buys, digital, and an ad studio;
   - the message: the week's issue and its tone (positive, contrast or attack), and plans unveiled as campaign promises;
   - the polls: the trend, internal polls you commission, the closest states, and the groups;
   - each state's voters by group: who they are, how they lean, who turns out and who can be moved;
   - the team: senior staff you hire (well-known operatives of recent campaigns, with the real cast), and surrogates on tour (the running mate you choose, your spouse and two allies), each tour run by an advance lead.

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
6. **The midterms, the 2032 campaign, and a second term.**
   - **The 2032 campaign** runs in the war room while you govern. Congress, the West Wing and the world go on, and their questions come to the war room's White House card. The race is a referendum on your record: approval and the economy set it, and the campaign moves it a few points.
   - **A lost 2032:** the lame-duck weeks to January 20, 2033.
   - **A second term** has its own theme and legacy goals, staff turnover at the oath, the lame-duck decline and the 2034 midterms. It ends with the race to succeed you in 2036, with its own election night, and the handoff on January 20, 2037.
   - **No third term.**

   The economy, the budget and debt, Congress, the appointments, the laws, the world and the military's stocks carry across the terms (`docs/SECOND_TERM_VALIDATION.md`).
7. **The verdict of history,** on four years or eight: the economy, the laws and what they did, war and peace, approval and the Congresses faced, and a historians' ranking. A campaign that ends on election night gets the campaign's assessment instead, and an Individual Crisis an after-action report.

### How long a game takes

These are estimates from the number of turns. No timed human playtest has been run yet.

| Game | Turns | At a deliberate pace | Mostly delegated, with watch mode |
|---|---|---|---|
| Campaign Only | About 16 weekly turns, then 14 days | 1–2 hours | 30–45 minutes |
| A first term (Full Game or Governing Only) | The transition, the 14-week hundred days, about 165 weeks of governing, then the 2032 campaign | 5–8 hours | 1.5–3 hours |
| Two terms | The above, and 208 weeks of a second term | 10–15 hours | 3–5 hours |
| Individual Crisis | 20 to 170 days of the crisis (hours, at war) | 30–90 minutes | 15–30 minutes |

**What keeps a long game moving:**
- **The chief of staff can take over** the calendar, the Hill, hiring and pardons.
- **Watch mode** plays quiet weeks itself, and stops for what matters: a crisis, the budget, a bill to sign, a scandal.
- **The desk sorts decisions.** Only events and the hundred days' questions hold up the week. The rooms' questions wait to their deadline, and the staff answer them if they are left.
- **The top bar** shows the week of the term ("Week 15 of 208") and what is next.
- **Reports along the way:** the weekly paper, the 100-day report, each election night, and the end's verdict or report.

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
                src/main/site-root/index.html is the site's front page: the title screen, run from play/
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
| [docs/TUTORIAL.md](docs/TUTORIAL.md) | The tutorial: how it plays, its eight chapters, and its tests |
| [docs/CAMPAIGN_WAR_ROOM.md](docs/CAMPAIGN_WAR_ROOM.md) | The war room's tabs and desks: the message and its tone, plans and promises, internal polls, voters by group, and the White House card in 2032 |
| [docs/BALANCE_TAIWAN.md](docs/BALANCE_TAIWAN.md) | How the Taiwan crisis turns on the president's orders, and the runs that measured it |
| [docs/ECONOMY_MODEL.md](docs/ECONOMY_MODEL.md) | The structural economy and the policy table |
| [docs/MODES.md](docs/MODES.md) | The four games on one engine: Full Game, Campaign Only, Governing Only, Individual Crisis |
| [docs/SECOND_TERM_VALIDATION.md](docs/SECOND_TERM_VALIDATION.md) | The presidency to its end in both modes that govern: whole games simulated, what carries across terms |
| [docs/MODE_TEST_MATRIX.md](docs/MODE_TEST_MATRIX.md) | What is tested for each game and each ending, and where |
| [docs/BUG_FIX_REPORT.md](docs/BUG_FIX_REPORT.md) | Every bug found and fixed in this round: reproduction, cause, fix, test, what remains |
| [docs/REMAINING_ISSUES.md](docs/REMAINING_ISSUES.md) | What is known and not yet fixed |
| [docs/DECISIONS_TO_REVIEW.md](docs/DECISIONS_TO_REVIEW.md) | Choices made without being discussed, for review |
| [PRODUCT_VISION.md](PRODUCT_VISION.md) | What the game is and is not, the pillars, the audience |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Modules, command model, determinism, formulas, ADRs |
| [UI_DESIGN.md](UI_DESIGN.md), [docs/ART_DIRECTION.md](docs/ART_DIRECTION.md) | The interfaces, the design system, accessibility and the look |
| [GAMEPLAY_DESIGN.md](GAMEPLAY_DESIGN.md), [FEATURE_PRIORITY.md](FEATURE_PRIORITY.md) | Loop audits, the core loop, and every source mechanic classified |
| [VERTICAL_SLICE.md](VERTICAL_SLICE.md) | The first playable slice (the 2028 election and the first hundred days) |
| [DEVELOPMENT_ROADMAP.md](DEVELOPMENT_ROADMAP.md), [RISK_REGISTER.md](RISK_REGISTER.md) | Milestones, release gates, risks |
| [docs/SOURCE_AUDIT.md](docs/SOURCE_AUDIT.md) | The source games: commits, licensing, what was ported |
