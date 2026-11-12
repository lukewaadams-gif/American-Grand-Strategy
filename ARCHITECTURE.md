# Architecture

## 1. Goals, in priority order

1. **Deterministic and testable.** The same setup and the same commands produce the same game, always. Balance is measured in CI, not by feel.
2. **Simulation separate from presentation.** The core runs headless at about 2 ms per full game, so batches of 1,000 games take seconds.
3. **Content is data.** Bills, events, candidates and states are files, not code paths. Designers and modders add content without touching the engine.
4. **One model per concept.** One electorate, one economy, one event system. The source games had duplicates of each.
5. **Replaceable engines behind narrow contracts.** The electorate model and the war model can each be upgraded independently.

## 2. Modules and packages

```
mandate/                      Maven multi-module, Java 21
├── mandate-core/             the simulation; no AWT/Swing/JavaFX imports (enforced by a test)
│   ├── engine/               GameEngine, WorldState, ConsequenceLedger, SaveManager, CommandCodec
│   ├── time/                 SimulationClock (monthly / weekly / daily / operational), CampaignCalendar, Phase
│   ├── events/               EventScheduler, DecisionResolver, EventCatalog, WorldView (read-only view for conditions)
│   ├── rng/                  DeterministicRandomManager: named SplitMix64 streams with saveable state
│   ├── data/                 Tuning (all balancing constants), Json, Csv
│   ├── effects/              Effect: the sealed transition vocabulary
│   ├── campaign/ electorate/ ELECTION engine: ElectionEngine, ElectionState, CampaignEngine, CampaignStrategy, PollPoint,
│   │                         ElectionCalculator, ElectorateModel (CellElectorateModel: BG's 96 cells; UniformSwingModel)
│   ├── economy/              ECONOMY engine: EconomyEngine, EconomyState, BudgetState, MarketContext
│   ├── congress/             CONGRESS engine: CongressEngine, VoteModel, CongressState, PolicyState, bills and blocs
│   ├── politics/             SHARED POLITICAL SYSTEM: PoliticsEngine, PoliticsState, PartyState, coalitions, opposition
│   ├── executive/            the presidency: ExecutiveEngine, GovernmentState, cabinet posts and archetypes
│   ├── diplomacy/            DIPLOMACY engine: DiplomacyEngine, DiplomacyState, countries, the Taiwan ladder
│   ├── military/             MILITARY engine: MilitaryEngine, MilitaryState, OperationState, theater seam
│   ├── ai/                   CampaignAi (staff planner and adaptive opponent), AutoPilot (batch probes): players, not engines
│   ├── opening/              the first hundred days: HundredDays (over a Host), HundredDaysState, DayHundredReport;
│   │                         OpeningTerm + StandaloneHost = the 12-week prototype on the same code
│   └── tutorial/             Coach: one situational lesson at a time; reads the game, never changes it
└── mandate-cli/              GameShell (the game), Console; designer tools; builds the single mandate.jar
```

## 3. One authoritative world, with owners

`WorldState` is the single persistent state of a game. It holds one domain state per system, and the save file is that state.

| WorldState part | Contents | Owner (the only code that writes it) |
|---|---|---|
| `economy` | output gap, growth, unemployment, inflation, Fed rate and credibility, oil, 10-year yield, confidence, reported data, shocks | `EconomyEngine` |
| `budget` | baseline and policy deficit, debt | `EconomyEngine` |
| `politics` | approval by coalition (your voters, swing, opposition voters), fading and lasting modifiers, honeymoon, issue salience, political capital, opposition goodwill and strategy, parties (wing moods, leaders), politicians' roles | `PoliticsEngine` (the shared political system) |
| `congress` | seats, filibuster, reconciliation years, relationship with each bloc | `CongressEngine` |
| `policy` | laws and executive orders in force, blocked orders, failed bills | `CongressEngine` |
| `government` | president, party, inauguration, term, cabinet seats, agenda, attention | `ExecutiveEngine` |
| `diplomacy` | flashpoint tensions, country relations, deterrence, Japanese basing, Taiwan rung, warning time | `DiplomacyEngine` |
| `military` | forces by type, munitions by type, permanent losses, killed, production rate, wars fought, the war in progress | `MilitaryEngine` |
| `election` | both campaigns, pending plan/posture, hidden polling miss, results 2028 and 2032, midterm | `ElectionEngine` |
| `clock`, `phase`, `scheduler`, `ledger`, `log` | time, turn mode, pending events, the record of decisions and consequences | `GameEngine` |

**Enforced by the compiler.** Each domain state lives in its engine's package and has package-private setters. Code in another package cannot write it, and the `GameEngine` cannot either. `ArchitectureTest` additionally checks two rules:
- no domain state exposes a public mutator;
- no domain package imports another domain's `*State` or `*Engine`.

## 4. State transitions

Systems never modify each other's variables. A consequence that crosses a boundary is an `Effect`: a sealed record type, the same one content (events, bills) uses. `GameEngine.dispatch` routes each effect to the engine that owns the state it changes, and records it in the `ConsequenceLedger` with its cause and its owner.

| Effect | Routed to |
|---|---|
| `Approval`, `CoalitionApproval`, `Capital`, `Salience`, `Goodwill`, `WingMood` | politics (before inauguration, `Approval` becomes campaign swing in the election engine) |
| `EconomyShock`, `Deficit`, `FedPressure`, `OilRelease` | economy (ignored before inauguration, and recorded as such) |
| `CampaignSwing`, `Cash` | election |
| `Tension`, `Deterrence`, `WarEnded` | diplomacy |
| `Munitions` | military |
| `CongressElected` | congress |
| `PresidentInaugurated` | executive and politics |

Reads go the other way, through explicit **contexts** assembled in one place in `GameEngine`. Examples:
- `MarketContext`: oil risk from diplomacy and Treasury competence from the executive, fed to the economy.
- `OpinionInputs`: the economy as voters feel it, fed to politics.
- `LegislativeContext`: approval, opposition strategy and wing moods, fed to Congress.
- `ElectoralEnvironment`: the incumbent's record, fed to the election engine.

An engine never reaches into another engine's state.

**Examples of cross-system chains that now run end to end:**
- **The election shapes the next administration.** The election engine produces `CongressElected`, and later `PresidentInaugurated`. The executive forms a cabinet, with confirmations voted by the vote model. Politics sets the mandate: swing approval and capital scale with the electoral-vote margin.
- **A law changes conditions.** `CongressEngine.push` returns `Approval`, a lasting `CoalitionApproval`, `EconomyShock`, `Deficit`, `Salience`, `Capital`, `Goodwill` and `WingMood`. Each goes to its owner.
- **The economy shapes the next election.** Growth, inflation and unemployment feed approval through `OpinionInputs`, and feed the 2032 environment through `ElectoralEnvironment`.
- **A war leaves permanent losses.** The military engine spends munitions and loses platforms each operational turn. The losses stay in `MilitaryState`; only munitions regenerate, through monthly production. The war's outcome sends `Approval`, `EconomyShock` and `WarEnded`.

## 5. Time: the master clock and the turn

`SimulationClock` has four granularities:
- `MONTHLY` while governing;
- `WEEKLY` in campaigns and in the first hundred days;
- `DAILY` (available; used by operational turns after the first week of a war);
- `OPERATIONAL`, a turn length in hours.

When a war starts, the clock switches to operational turns: 6 hours each in the first week, then 24 hours. Each `EndTurn` fights one turn. When the war ends, monthly governing resumes. Engine rates are expressed per week or per month and compounded for the actual step (for example `1-(1-a)^dt`). One set of equations therefore serves the monthly main game and the weekly prototype.

**Dated milestones** come from `data/calendar.properties`: elections, inauguration, midterms, debates, and 32 FOMC meetings for 2029-2032. Jobs and CPI reports fall on the first Friday and the second Wednesday of each month. The `EventScheduler` holds pending decisions and rolls the event catalogue each turn. Event conditions read a `WorldView`, never the world itself. The `DecisionResolver` validates an answer, rolls the odds the player was shown, and returns effects (plus an engine hook such as starting a war) for the engine to dispatch.

**Phases, in order:** `GENERAL_CAMPAIGN_2028` (weekly) → `TRANSITION` (once) → `FIRST_100_DAYS` (14 weekly turns from the Monday after the inauguration) → `GOVERNING` (monthly, full-term games only) → `REELECTION_CAMPAIGN_2032` (weekly) → `EPILOGUE`. A vertical-slice game (`GameSetup.Length.HUNDRED_DAYS`) ends at day 100. A lost election ends either length at election night.

**A hundred-days week, in order** (inside `HundredDays.endWeek`): the chief of staff's delegated work; cabinet hearings and Senate votes; the economy stepped one week with the crisis and the live bill as market context; FOMC meetings and data releases in the week; the public-opinion step, wing revolts and the opposition's strategy; the briefing; then the next week's crisis, funding and economic-challenge triggers. Random events do not roll in this phase.

**Governing month, in order:**
1. Economy step, with the market context.
2. FOMC meetings in the month.
3. Jobs and CPI releases; public reaction to each.
4. Public-opinion step.
5. Diplomacy step. Ladder rungs become decisions.
6. Munitions production.
7. Event roll.
8. Clock advance and an attention reset.
9. Midterm or re-election milestones, if one has been reached.

## 6. Configuration, data and determinism

- **Balancing constants.** About 300 keys live in `data/tuning.properties`, the single source of every tunable number.
  - Code has no defaults of its own. A missing key is an error, and `GameEngine` validates every scenario and legacy key at start-up.
  - `EngineUnitTest.everyTuningKeyIsUsed` fails on dead keys.
  - Mods and scenarios override keys with `Tuning.withOverrides`, which rejects unknown keys.
  - Each key is tagged with its provenance: [F] foundation, [P] prototype, [RD] Resolute Desk, [BG] Battleground 2028, [IC] First Island Chain.
- **Historical and content data** live in `data/*.csv`: states and Senate classes, candidates, bills, blocs, cabinet archetypes and fictional nominees, countries, forces and munitions. Forces and munitions are taken from IslandChain's 2027 order of battle and its CSIS-cited stockpile and production tables.
- **Randomness.**
  - `DeterministicRandomManager` gives each system its own SplitMix64 stream. A stream's state is one `long`, so the full random state is a small map, and it is saved.
  - Read-only views (the polls a player sees) draw from throwaway generators.
  - Iteration order is deterministic: sorted or insertion-ordered maps only. A per-JVM randomized `Map.copyOf` was removed from campaign plans because it could change floating-point sums between runs.
- **No mutable statics** except immutable content caches. `ArchitectureTest` enforces this, and `GoldenRegressionTest.noStateLeaksBetweenGamesInOneJvm` checks it.

### Saves (`SaveManager`)

- **Version 2** (the default) holds:
  - setup;
  - a full `WorldState` snapshot;
  - every random stream's state, as strings so the longs survive exactly;
  - the command log;
  - a rules fingerprint (a hash of the tuning file).

  Loading restores the snapshot without replaying, and the same future commands then give an identical world. `SaveManager.consistent` checks that a snapshot equals a replay of its own log.
  - The setup includes the game's length, the real-roster opt-in and any custom candidate, so a replay needs nothing else.
  - An optional `ui` section carries the front end's own state (tutorial progress, preferences). The simulation never reads it, so there is still one save file. The shell writes it atomically, through a temporary file and a move, after every turn.
- **Version 1** (the foundation's ADR-3 format: setup plus commands) still loads, by replay. That reproduces the game only under the same rules; the fingerprint is stored so a loader can warn. **Compatibility with the source games' saves is not feasible:** they are Java-serialized object graphs of different classes. The Battleground 2028 → Resolute Desk import parsed raw serialization streams and had a field-name bug. A one-off import tool is possible if needed; it is not built.

### Current formulas

These are compact. Every constant is in `tuning.properties`.

| Model | Owner | Core equations |
|---|---|---|
| Electorate | election | margin_s = baseline2024_s + swing × elasticity_s + local_s (the 96-cell port is M1, behind `ElectorateModel`) |
| Incumbent record | election | 0.55(approval − 50) + 0.4(growth − 2) − 0.5(π − 2.5) − 1.2(u − 4.4) − war loss − 0.4 × unfinished agenda |
| Economy | economy | gap' = 0.95^dt·gap + 0.75·shocks − 0.025·dt·(r − r*) · u → 4.3 − 0.5·gap · π → anchored expectations + 0.25·gap + supply + gas pass-through · Fed moves ±25 bp at FOMC meetings when the Taylor target differs by more than 0.3 |
| Markets | economy | oil → 75 + crisis premium − SPR release; gas = 1.30 + 0.0255·oil; 10-year → Fed, expectations, term premium, deficit, pending bills, Treasury competence, credibility |
| Opinion | politics | three coalitions toward targets set by economic sentiment (gas, π, u, mortgages, confidence, growth), wing moods, goodwill, a honeymoon, and fading plus lasting modifiers; national = weighted sum |
| Votes | congress (`VoteModel`) | fit + party pull (loyalty × wing mood; opposition by its strategy; resisting the other party's agenda) + relationship + deficit hawks + public pressure + capital at the margin |
| Confirmations | congress (`VoteModel`) | own party defects only above a controversy threshold; the other party follows its strategy; crossover picks confirm easily |
| Ladder | diplomacy | Taiwan drift = 0.8 − 0.05(deterrence − 50) − 0.012(tension − 30) + 0.7 × rungs crossed; war blocked with p = deterrence/100 at the brink |
| War | military | per operational turn: US fires anti-ship and land-attack munitions in priority order (scaled by surviving aircraft); the PLA salvoes in proportion to its strength; interceptors are spent (scaled by surviving ships); leakers sink ships, carriers and aircraft; the invasion advances against Taiwan's defence; ends defended (PLA strength ≤ 35), fallen (progress 100) or stalemate (60 days) |

**Calibration check of the war model** (300 runs each, Japanese basing, one month's warning; reproduce with `./mandate war 300`):

| Commitment | Deterrence 50 | Deterrence 25 / 80 |
|---|---|---|
| Intervene | defended 89%; about 4,700 US killed, 12 ships, 1.9 carriers and 224 aircraft lost over about 2 weeks (CSIS base case: about 3,200 killed in 3 weeks, 2 carriers, 7-20 major ships, 90-270 aircraft) | 72% / 96% |
| Arm and sanction | defended 31%; no US combat losses | 16% / 52% |
| Stand aside | Taiwan falls | Taiwan falls |

### Mechanic change log (this refactor)

| Replaced or changed | Now | Why |
|---|---|---|
| `Game` monolith holding every variable | `GameEngine` (orchestration) + `WorldState` (domain states with owners) | Ownership boundaries; a snapshot save becomes possible |
| `RngStreams` (SplittableRandom) | `DeterministicRandomManager` (SplitMix64, saveable) | Random state must be saved and restored exactly |
| National `ApprovalModel` | Coalition model from the prototype, as `PoliticsEngine` | The prototype's model is richer (base, swing and opposition voters), and two approval formulas would duplicate an authority. National approval is the weighted sum. |
| `Economy` (monthly only, Taylor rule every month) | `EconomyEngine` with a dt step, markets, FOMC meetings on calendar dates, dated data releases | One economy for monthly and weekly play; the prototype's weekly market model becomes authoritative |
| `LegislativeEngine` + the prototype's support formula | `CongressEngine` + `VoteModel` (one formula, merged terms) | They were duplicates |
| `WhipCount.passChance` (its own noise formula) | Carried from `VoteModel` | Duplicate |
| `World` | `DiplomacyEngine` + `DiplomacyState` (+ country relations); munitions moved to the military | Ownership |
| `AbstractTheaterResolver` (one roll) | `MilitaryEngine` operational turns over real inventories; `OperationalTheaterResolver` keeps the `TheaterResolver` seam | Losses must persist and depend on what is left |
| `Polling` helper (a second poll formula) | Removed; polls come from `ElectionEngine.statePolls` | Duplicate |
| `Calendar2028` constants | `data/calendar.properties` | Configurable dates |
| Prototype: its own opinion, economy, Fed, releases, votes, confirmations, midterm outlook | Calls the authoritative engines | No duplicated formulas. The prototype keeps only its orchestration and its multi-week crisis mechanic, which exists nowhere else. |
| Incumbency: growth only | Adds unemployment; growth weight 0.7 → 0.4 | `RequiredBehaviourTest` showed a recession *helped* the incumbent (its rebound read as growth) |
| Pivot of approval in incumbency and midterms: 48 | 50 | The coalition model centres approval near 51; the old pivot over-rewarded incumbents (re-election 71-80%) |
| Opposition pull and bipartisan relief | Retuned (−0.18 / −0.42 / −0.62; 0.14); own members resist the other party's agenda (×0.3 pull, base wing −6) | After merging the vote models, the autopilot passed 14-16 laws a term, including the other party's agenda. It now passes about 9 of its own. |
| Fed threshold 0.2 | 0.3 (0.2 under pressure) | Release noise made the Fed flip between cuts and hikes |

**Known limitations:**
- Monthly governing steps pause during a war, which lasts 2-8 weeks.
- The prototype's crisis model and its bill-design and crisis constants are still inline. They are prototype-only and move to content in B-44.
- `DAILY` is used only inside operations.
- `Map.copyOf` in content records whose order matters must be avoided. This is a convention, checked by review and golden tests.

## 7. Porting plan: what comes from where

We **port models, not code**. The source files are monoliths with static state, UI calls inside the simulation, and Java serialization. We re-implement against the core's interfaces, using the source as reference and calibration oracle, and keep the original repositories untouched.

| Source asset | Action | Milestone | Notes |
|---|---|---|---|
| BG `Elect`/`Vote` 96-cell electorate + `STATES` demographics + 2016-2024 returns | **Ported** (`CellElectorateModel`, exported by `tools/BgExport.java`); the game's default since the vertical slice | M1 | Reproduces BG's calibration (2024 composition: D 0.4895 and the actual 312-226 map). Senate races use it too. Still to do: ACS 2019-23 inputs, approval by group feeding midterms. |
| BG campaign effect saturations (`imgSat`, `fieldSat` …) | Reference | M1 | Calibrate `CampaignEngine` curves to BG's response curves. |
| BG `Debates` tactic weights | Reference | 1.0 | For the 1.0 question beats. |
| RD `blocLogit`, `chairOk`, `hold`, filibuster/era rules | **Port** into `CongressEngine`/`VoteModel` (bloc model done) | M3 | Keep 3 blocs per party; add chairs and leaders as gatekeepers. |
| RD `Economy` structure | Already re-derived; fix the damping, decay and interest bugs | done / M3 | Interest cost = debt × average rate, with the average rate rolling toward market rates. |
| RD crises, domestic events and modern bills (text + effect tables) | **Re-author** into JSON events and bills | M2 | Convert each fx string to `Effect`s; drop options that are dominated. |
| RD party factions, opposition strategy | Port | 1.0 | |
| IC engine | **Wrap, don't merge** | M5 (calibration), EXP-1 (play) | Forces and munitions baselines are already taken from IC's tables. M5: a headless `IslandChainOracle` produces outcome tables over a grid of `TheaterScenario` inputs, and the `MilitaryEngine` operational coefficients are fitted to them. EXP-1: an adapter module implements `TheaterResolver` and launches the IC UI. Prerequisites: seed scrambling, moving `chipLag`/`lastKilled` into `Game`, AI-vs-AI ceasefire, host-controlled termination. |
| BG `Portrait` | Port for fictional characters | 1.0 | |
| BG/RD UI toolkit (immediate-mode Java2D) | **Do not port** | | See ADR-2. |

## 8. Architecture decision records

**ADR-1: Java 21, Maven, multi-module.** The team's code and knowledge are Java. We keep it, but drop the single-file and Java 8/BlueJ constraint (records, sealed types and pattern switches cut core code by about 30%). Maven is used because it is installed everywhere and needs no wrapper download. Gradle is acceptable later if build logic grows.

**ADR-2: Desktop UI is JavaFX (decision gate at M4).** It is a UI-dense strategy game: tables, charts, a map, modals, text-heavy events, scalable fonts and accessibility.
- **For JavaFX:** layout, CSS theming, charts, HiDPI and accessibility APIs, and `jpackage` installers; Steam integration via steamworks4j.
- **libGDX** is better for animation-heavy games but makes complex forms costly.
- **The current Java2D immediate-mode toolkit** is proven but hand-rolled: no layout, no accessibility, no text input or IME, and it is tied to a fixed design resolution.
- **Gate:** a 2-week spike builds the whip-count screen and the US map in JavaFX, with libGDX as the fallback.

**ADR-3: Saves.** Version 2 is a JSON snapshot of the world and the random state plus the command log. Version 1 (setup plus command log, by replay) is still readable. This rules out Java serialization (brittle) and cross-game raw-stream parsing.

**ADR-4: The theater seam.** The presidential game talks to war only through `TheaterScenario` and `TheaterOutcome`. This keeps the base game shippable and reviewable without the operational wargame, and lets EXP-1 plug in without core changes.

**ADR-5: No browser build before 1.0.** The source games' Java-to-JS translator is not in any repository. If a web demo is needed for marketing, evaluate TeaVM against `mandate-core` (pure Java, no reflection, no threads) in a post-1.0 spike.

**ADR-6: Content as CSV and properties now, JSON + schema in M2.** Balancing constants are in `tuning.properties` (done). The CSV loader is intentionally minimal. M2 introduces JSON content with a validator CLI (`mandate lint`): unknown ids, unreachable events, dominated options and missing localisation keys.

## 9. Testing strategy

There are 63 tests, all run on every build (`mvn verify`).

| Layer | Tests | What they protect |
|---|---|---|
| Required behaviours | `RequiredBehaviourTest` (6) | Elections shape the next administration (president, Congress, cabinet; mandate correlates with starting approval and capital, r > 0.5). Laws move the deficit, output and lasting opinion, routed to their owners. A recession before 2032 costs the incumbent votes and re-elections. Military losses persist through time and saves, in operational turns. Saves reproduce identical futures (mid-campaign, early and late term, mid-war), v1 still loads, and snapshot = replay. |
| Architecture | `ArchitectureTest` (3) | No public mutators on domain state; no cross-domain state or engine imports; no mutable statics |
| Engine units | `EngineUnitTest` (10) | Random snapshot and restore; exact JSON doubles; clock granularities; calendar rules; no dead tuning keys; overrides work and reject typos; the ledger records owners and links decisions; refused commands change nothing; saves reject bad input |
| Regression | `GoldenRegressionTest` (2) | Fixed seeds reproduce recorded fingerprints (6 full games, 7 prototype strategies); no leaks between games in one JVM. Regenerate with `-Dmandate.updateGolden=true` after an intended change. |
| Models and rules | `ElectorateTest`, `RulesTest`, `SystemsTest`, `DeterminismTest`, `BalanceTest` (18) | 2024 reproduction, Senate classes, legislative thresholds, recession bite, war monotonic in deterrence, determinism and replay, party balance |
| Prototype | `OpeningTermTest`, `OpeningStrategyTest` (24) | The 12-week loop's interconnections, information value, failure follow-ups, delegation and strategy non-dominance, now over the authoritative engines |
| Batch report | `mandate batch`, `mandate compare` | Balance tracking. Today: Toss-up D 51%, re-election 55% / 65%, 8.7 laws per term |

## 10. Performance budget

- **Uniform-swing model:** a full game takes about 3 ms; 1,000 games take about 3.4 s, measured. A war runs about 20-60 operational turns.
- **Cell model:** 51 × 96 cells × 11 issues = about 54k cell evaluations per projection. That is fine weekly. Monthly approval-by-state is also fine.
- **Forecast:** 1,000 draws × 51 states, re-using the projection, under 50 ms.
- **Command Mode (IC engine):** about 3 s per war headless; acceptable as an opt-in.

## 11. The first hundred days inside the engine

The vertical slice put the governing prototype inside the game without a second world:

- **`Host`** is everything the hundred days need: the domain states, their engines, the calendar, the random streams, and a `dispatch(cause, effect)` router. `GameEngine.EngineHost` implements it over the `WorldState`, so transitions go through the engine's router into the ledger. `StandaloneHost` implements it over fresh states for the 12-week prototype, which keeps the design probes on the same code.
- **`HundredDaysState`** holds only process state: the bill's stage, the crisis's hidden resolve, pending decisions, briefings and the final report. Cabinet seats live in `GovernmentState`, with the nomination pipeline in `ExecutiveEngine`. Bloc relationships live in `CongressState`, and laws in `PolicyState` (`CongressEngine.recordLaw`). Opinion, wings, goodwill and capital live in `PoliticsState`; the economy in `EconomyState` and `BudgetState`; deterrence and tension in `DiplomacyState`.
- **`Command.Act(OpeningAction)`** is the only way in. `GameEngine.hundredDays()` returns a view whose `act` and `apply` become commands, so the UI, the autopilot and the probes are all logged and replayable.
- **A refused action leaves no trace.** Handlers validate before they change anything; a decision's log line is written only after its handler succeeds; and a ledger decision opens on the first transition. `HundredDaysInGameTest` checks this by fingerprint.
- **Day 100.** `HundredDays.finish` fixes a `DayHundredReport` into the state. A slice game then ends. A full-term game fills or votes on any open cabinet posts (`formCabinet`), resumes monthly turns on the first of the next month, and lets the flashpoints drift for the months the hundred days covered.
- **Replays.** `restart()` re-creates the game from its setup. `replayFromInauguration()` replays the command log up to the inauguration (`WorldState.inaugurationCommand`).

