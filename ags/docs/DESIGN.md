# American Grand Strategy: the design brief

This is the brief for the new game, rebuilt from the simulation engine as a commercial strategy game. It records the sixty answers given on 25 November 2026, after two playable concept demos:
- the first, a scripted mock-up of both modes;
- the second, a Civ-style board version.

Everything in the new tree (`ags/`) follows from it. The previous game stays in the repository as a reference (`mandate-core`, `mandate-web`). It stays at `/play` until the new one replaces it.

## The pitch

**A political drama with real systems.** You lead an American party for half a century, from 2000 to about 2048, on a hex map of the country:
- build a coalition out of rival factions;
- take the map election by election;
- govern what you win.

History arrives on schedule unless you change it. After 2026, the future is a forecast with honest uncertainty.

It sits between Civilization V and Democracy 4:
- the turn-by-turn pull of a 4X game;
- policy outcomes stated as clearly as Democracy 4 states them, but grounded in a real simulation;
- a long career, as in Out of the Park Baseball: politicians are born, rated, rise, fall and retire.

## The market

| | |
|---|---|
| Audience | Players of Civilization V and Democracy 4. Civ-level complexity: about five to seven core actions a turn, deep panels underneath, a codex. |
| Platform | Steam (PC and Mac), at $19.99 premium with DLC. **The first public milestone is a free web demo** (Q44), ahead of Steam. |
| Name | **American Grand Strategy** (Q45). |
| Rating | Teen / PEGI 12. A serious simulation, not satire. |
| People | A fictional cast. Real events, fictional people. The real countries are fictionalised. |
| Language | English first. |

## The two modes

- **The Long Game** (the lead mode, Q46). It starts in 2000 and runs as long as you like, up to the 2048 election.
  - Quarterly turns. Election stretches switch to two-week turns, and the big moments zoom in (Q55).
  - You are a party's leadership across decades. Your leader ages, retires or loses; your bench takes over.
- **One Term** (the on-ramp). The 2028 campaign, four years in office and the 2032 re-election, in about two to three hours.

## The board

- **Geographic hexes** (Q47): about 1,200 land hexes from the Census state shapes. Each hex has:
  - terrain from real longitude and latitude;
  - a population weight;
  - a lean.
- A state's result is the population-weighted lean of its hexes. Rivers and coasts are drawn; 76 metro areas are cities.
- **No fog of war** (Q51). Every lean is visible. The public polls still miss: a state's poll carries noise until you poll it.
- **The map stays the main screen in office** (Q52):
  - laws are built like wonders;
  - approval shows per hex;
  - governors, senators and the cabinet are units and great people;
  - crises appear on the board.
- **A second, world board** (Q53): 8–12 powers whose leaders move, ally and threaten. Crises such as the Taiwan Strait or Hormuz play out there and hit the home map.

## Systems

- **The turn**:
  - a few big actions with your units;
  - production in your HQ cities;
  - research and doctrines;
  - events that interrupt.
- **Factions as city-states**: nine blocs with friendship and quests. Friends give a bonus; allies give a bigger one and turn out their heartlands every turn. You build the coalition, and it can come apart.
- **Units that change by era** (Q48): the same roles in upgraded forms, as in Civ's unit lines. For example:
  - field organizers, then digital organizers, then AI canvassers;
  - TV ads, then streaming, then synthetic media.
- **Rivals** (Q49): one rival party, run by an AI with a personality. Factions you neglect can split off into **third parties** that take votes and can spoil a state, as in 2000.
- **Conflict** (Q50): contested hexes and counter-moves.
  - Opposing units on the same or neighbouring hexes contest each other: rallies clash, attack ads target a rival unit or HQ, and lawyers fight over the rules.
  - Each contest shows its odds first, then rolls the dice.
- **Progression**: four layers.
  - **Ideas**, the party's research, gated by year.
  - **Doctrines**, policy trees bought with Influence.
  - **Institutions**.
  - **The platform**: pledges, each with a forecast card.
- **The century tree.** The nation's technologies arrive on their own, sooner if you fund them. The forecasts run to 2048 and include the AI labour shock, fusion and ageing drugs.
- **Outcomes** are forecast cards in the Democracy 4 style: clear arrows up front, with the numbers and 90% ranges one click down (Q42).
- **People** (OOTP-style): procedural politicians rated 20–80. They have careers, age, scandals and great operatives.
- **Elections**: every federal race, decided from the board.
- **Narrative**: authored event chains on top of the simulation (Q37), plus a living news feed with headline moments (Q39).

## Winning (Q54)

| Victory | How |
|---|---|
| Dynasty | Hold the White House for enough of the twelve terms to 2048. |
| Realignment | Hold enough factions as allies to make your coalition the natural majority. |
| Reform | Pass a set of landmark laws, or constitutional amendments. |
| Legacy score | The best record by 2049. |

## Around the game

| Area | Decision |
|---|---|
| Saves | Free saves, plus an Ironman option. |
| Meta | Steam achievements, a Hall of Presidents, scenarios and challenges. |
| Difficulty | Civ-style levels (Intern to Machiavelli) and assists. |
| Online | Seeded challenges and leaderboards (Q56): every game has a seed; a shared weekly seed costs almost nothing. No live multiplayer at launch. |
| Modding | **Full modding at launch** (Q57): every faction, unit, idea, doctrine, law, plank, leader, event chain and the calendar is a documented data file. Steam Workshop and a scenario editor come with the Steam release. |
| Accessibility | WCAG 2.1 AA (Q58):
- colour-blind palettes with patterned borders;
- text scaling;
- full keyboard play, the hex map included;
- reduced motion;
- screen-reader labels on panels.

Automated accessibility checks stay in the test suite. |
| Audio | An original generative score and sound effects; no voice-over. |

## The look: a tabletop diorama (Q60)

The game is a premium board game laid out on a war-room table at night. The table is dark walnut under a desk lamp, its pool of light falling on the map:
- **The map**: printed on heavy paper, a little worn, with an engraved hex grid. Terrain is in watercolour washes, rivers and coasts in ink, cities as printed vignettes.
- **The pieces**:
  - **Units** are painted miniatures on round bases, casting soft shadows.
  - **Territory** is ink washes and inked borders in the party colours.
  - **HQs** are wooden buildings.
  - **Field offices** are cardboard counters.
- **Contests** throw real-looking dice.
- **Events** arrive as illustrated cards.
- **Panels** are paper dossiers, file folders and index cards, with a typewriter for reports and an engraved serif for headings.

Both concept demos' looks are retired: the navy-and-brass state map and the Art Deco hexes.

## Technology (Q29–Q31)

- **The simulation is in Rust, compiled to WebAssembly.**
  - **Speed.** The forecast cards and fifty years of AI turns need thousands of deterministic runs, without garbage-collection pauses.
  - **One codebase.** The same simulation runs in the browser for the web demo, in a desktop wrapper later, and natively for tests.
  - **Reliability.** Its type system suits a large rules engine where saves and seeds must replay exactly.
  - **Determinism rule.** The simulation uses only `+ − × ÷` and `sqrt`, never transcendental functions, so native and WebAssembly builds give identical results.
- **The bridge** is JSON over a few `extern "C"` functions. There is no wasm-bindgen, so the build needs only `cargo` and the `wasm32-unknown-unknown` target.
- **The front end** is TypeScript and PixiJS 8 (WebGL) for the board, with the DOM for panels, so screen readers and keyboard play work.
- **The desktop wrapper** is decided later (Q31).

## The vertical slice (Q43)

**The Long Game, Q1 2000 to Q4 2001**, playable on the web:
- **Setup:** character creation, then the party's coalition, then difficulty.
- **The campaign:**
  - primaries, the platform and the running mate;
  - the two-week-a-turn final stretch;
  - debates and an October surprise;
  - contested hexes and third parties.
- **Election night and the Florida recount.**
- **2001 in office:**
  - laws as wonders and approval per hex;
  - the cabinet;
  - the California energy crisis, the Hainan incident and the tax-cut fight;
  - the September threat, as a forecast you can change.
- **A first world board.**
- **Around it:** the victory screen, saves with Ironman, and seeds.
