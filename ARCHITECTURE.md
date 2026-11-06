# Architecture

## 1. Goals, in priority order

1. **Deterministic and testable.** The same setup and the same commands produce the same game, always. Balance is measured in CI, not by feel.
2. **Simulation separate from presentation.** The core runs headless at about 2 ms per full game, so batches of 1,000 games take seconds.
3. **Content is data.** Bills, events, candidates and states are files, not code paths. Designers and modders add content without touching the engine.
4. **One model per concept.** One electorate, one economy, one event system. The source games had duplicates of each.
5. **Replaceable engines behind narrow contracts.** The electorate model and the war model can each be upgraded independently.

## 2. Modules

```
mandate/                      Maven multi-module, Java 21
├── mandate-core/             pure simulation; no AWT/Swing/JavaFX imports (enforced by a test)
│   ├── game/                 Game (turn order, command dispatch), GameSetup, Scenario, Legacy
│   ├── command/              sealed Command hierarchy, IllegalCommandException
│   ├── time/                 Phase, Calendar2028
│   ├── rng/                  RngStreams: named, independent deterministic streams
│   ├── electorate/           UsState, StateCatalog, ElectorateModel (seam), UniformSwingModel, ElectionCalculator
│   ├── campaign/             Candidate, Issue, CampaignSide, CampaignWeekPlan, CampaignEngine, DebatePosture, Polling
│   ├── congress/             Bill, Bloc, Congress, LegislativeEngine, WhipCount, LegislativeStrategy
│   ├── economy/              Economy (monthly macro), ApprovalModel
│   ├── world/                World (tensions, Taiwan ladder), TheaterScenario/Outcome/Resolver (seam)
│   ├── events/               EventDefinition, EventOption, PendingEvent, EventCatalog
│   ├── effects/              sealed Effect: the only way content changes the simulation
│   ├── ai/                   CampaignAi (opponent), AutoPilot (delegation, batches)
│   └── data/                 resource loaders (CSV today, JSON with a schema in M2)
├── mandate-cli/              text play, autoplay, balance batch (designer tool)
└── (M4) mandate-desktop/     JavaFX client: depends on core, never the reverse
    (EXP-1) mandate-theater-islandchain/   adapter: the IslandChain engine behind TheaterResolver
```

**Dependency rule:** `desktop → core`, `cli → core`, `theater-islandchain → core`. Nothing depends on a front end.

## 3. The turn loop and the command model

```
            ┌──────── UI / CLI / AutoPilot / Tutorial ────────┐
            │  read: game.phase(), polls, whip(), pendingEvents │
            │  write: game.apply(Command)                       │
            └───────────────────────┬──────────────────────────┘
                                    ▼
                      Game.apply(command)   ── validate (throws IllegalCommandException; state untouched)
                                    │          then mutate, append to history
                     EndTurn ───────┤
                                    ▼
   campaign week:  AI plans → CampaignEngine.execute ×2 → debate → events → date+1w → election?
   governing month: Economy.step → Approval.step → World.step (ladder events) → events → capital → date+1m
                     → midterms? → re-election?
```

- **Commands** (`command/Command.java`): `PlanCampaign`, `ChooseDebatePosture`, `SetAgenda`, `PushBill`, `PublicAddress`, `BolsterDeterrence`, `Diplomacy`, `ResolveEvent`, `EndTurn`. This is a sealed interface, so every consumer is checked by the compiler when a command is added.
- **Saves** are `GameSetup` plus `List<Command>`. `Game.replay` re-runs them; `DeterminismTest` proves it. For fast loading of long games, 1.0 adds versioned JSON snapshots every N turns, validated against the replay in debug builds. We do not use Java serialization (see the audit: it broke saves and coupled the two source games).
- **Pending decisions:** events queue as `PendingEvent`s, and `EndTurn` is refused until they are answered. A debate week requires a posture. The UI therefore always knows what the player owes.

## 4. Determinism

- `RngStreams` derives one `SplittableRandom` per named stream ("economy", "congress", "events.<id>" …) from the master seed with a mixing hash. Adding a draw to one system never shifts another (`SystemsTest.rngStreamsAreIndependent`). This removes the IslandChain seed-correlation bug by construction.
- **No mutable statics** anywhere in core. Content catalogs are immutable after load.
- **Read-only views draw from throwaway streams.** Example: `Game.statePolls()` seeds a fresh stream from the seed and history length, so opening the polls screen never changes the outcome.
- **Rules for contributors:**
  - Never call `Math.random()`, `new Random()` or `System.nanoTime()` in core.
  - Never iterate a `HashMap` where the order affects RNG draws. Use `LinkedHashMap`/`EnumMap`; state iteration follows `StateCatalog` order.

## 5. Effects: the content contract

`Effect` is a sealed interface: `Approval`, `Capital`, `EconomyShock`, `CampaignSwing`, `Salience`, `Tension`, `Deterrence`, `Cash`, `Deficit`. Events, bills (soon) and crisis options produce effects only, and `Game.applyEffect` is the single interpreter. This gives three properties:

- **Previewable.** Every option can render "Approval +2; Deficit +0.05" before the player chooses (the CLI does).
- **Analysable.** Balance tools can sum the expected value of every option offline. `AutoPilot.value` is a first version. In M2, a content linter flags options that are strictly dominated, which is how the "obvious answer" problem found in the source games' press conferences gets caught.
- **Extensible.** A new effect type is a compile error everywhere it must be handled.

Engine **hooks** (`EventOption.hook`, e.g. `theater:INTERVENE`) exist for the rare consequence that is a process rather than a number (starting a war).

## 6. The simulation models (current formulas)

| Model | File | Core equations (monthly unless noted) |
|---|---|---|
| Electorate | `UniformSwingModel` | margin_s = baseline2024_s + swing × elasticity_s + local_s. Elasticity is a placeholder heuristic; M1 replaces it with the 96-cell port. |
| Election | `ElectionCalculator` | realised_s = expected_s + (N(0, 2.6) + hidden miss) × elasticity_s + N(0, 2.5). Correlated national error is what makes election night meaningful. |
| National swing | `Game.nationalSwing` | environment (scenario, or the incumbent's record in 2032) + 2.5 × Δquality − 4 × Δextremity + Δmomentum + debates and events − margin2024 |
| Campaign effect | `CampaignEngine` | local = Σ_side ±(1.3·ln(1+ads/ref) + 1.0·ln(1+field/ref) + 0.45·ln(1+visits)·charisma factor), with ref = 0.6 × the state's House seats. Ads decay 0.55 weekly, field 0.97, visits 0.6. |
| Message | `CampaignEngine` | Δmomentum = 0.35 × salience share × issue ownership × consistency (up to 1.5×); momentum decays 0.8 weekly |
| Economy | `Economy` | gap' = 0.95·gap + 0.75·shock − 0.025·(r − r*) + ε · u → u* − 0.5·gap (adjusts 30% a month) · π → anchored expectations + 0.25·gap + supply shocks · Fed = Taylor rule (0.5/0.5), at most ±25 bp a month · debt += deficit/12 − debt·nominal growth/12 |
| Approval | `ApprovalModel` | target = 46 + honeymoon (decays 0.85) + economy (−1.4Δu, −1.6Δπ, +0.8Δg) + modifiers (decay 0.88); compressed outside 40-56; approval adjusts 35% toward target |
| Votes | `LegislativeEngine` | bloc support = 1 − 1.15·\|bill − bloc ideology\| + party pull (loyalty, approval) − deficit-hawk penalty + capital bought at the margin; floor votes add N(0,4) in the House and N(0,1.6) in the Senate |
| Midterms | `Game.holdMidterms` | president's margin = 0.6(approval − 48) + 0.5(growth − 2) − 2.5 + N(0,2); House = 218 + 5.5 × margin; Class I Senate seats through the electorate model with a 3-point incumbency bonus |
| Taiwan | `World` | drift = 0.8 − 0.05(deterrence − 50) − 0.012(tension − 30) + 0.7 × rungs crossed + N(0,3); rungs at 50/65/80/92; war blocked with p = deterrence/100 each month at the brink |
| Theater | `AbstractTheaterResolver` | z = 0.035(deterrence − 50) + 0.02(munitions − 50) ± 0.6 basing + 0.15·warning + commitment + N(0, 0.9) → defended / stalemate / fallen. **Placeholder until M5 calibration.** |

Every constant is a named value or sits in one documented formula. M2 moves the tunables into `data/tuning.json` so balance passes need no code change.

## 7. Porting plan: what comes from where

We **port models, not code**. The source files are monoliths with static state, UI calls inside the simulation, and Java serialization. We re-implement against the core's interfaces, using the source as reference and calibration oracle, and keep the original repositories untouched.

| Source asset | Action | Milestone | Notes |
|---|---|---|---|
| BG `Elect`/`Vote` 96-cell electorate + `STATES` demographics + 2016-2024 returns | **Port** to `CellElectorateModel implements ElectorateModel` | M1 | Golden test: reproduce BG's `test calib` output (2024 D 49.0, state margins within 0.5). Replace ACS 2012-16 inputs with ACS 2019-23. |
| BG campaign effect saturations (`imgSat`, `fieldSat` …) | Reference | M1 | Calibrate `CampaignEngine` curves to BG's response curves. |
| BG `Debates` tactic weights | Reference | 1.0 | For the 1.0 question beats. |
| RD `blocLogit`, `chairOk`, `hold`, filibuster/era rules | **Port** into `LegislativeEngine` | M3 | Keep 3 blocs per party; add chairs and leaders as gatekeepers. |
| RD `Economy` structure | Already re-derived; fix the damping, decay and interest bugs | done / M3 | Interest cost = debt × average rate, with the average rate rolling toward market rates. |
| RD crises, domestic events and modern bills (text + effect tables) | **Re-author** into JSON events and bills | M2 | Convert each fx string to `Effect`s; drop options that are dominated. |
| RD party factions, opposition strategy | Port | 1.0 | |
| IC engine | **Wrap, don't merge** | M5 (calibration), EXP-1 (play) | M5: headless runner `IslandChainOracle` produces outcome tables over a grid of `TheaterScenario` inputs, and `AbstractTheaterResolver` coefficients are fitted to them. EXP-1: an adapter module implements `TheaterResolver` and launches the IC UI. Prerequisites: seed scrambling, moving `chipLag`/`lastKilled` into `Game`, AI-vs-AI ceasefire, host-controlled termination. |
| BG `Portrait` | Port for fictional characters | 1.0 | |
| BG/RD UI toolkit (immediate-mode Java2D) | **Do not port** | | See ADR-2. |

## 8. Architecture decision records

**ADR-1: Java 21, Maven, multi-module.** The team's code and knowledge are Java. We keep it, but drop the single-file and Java 8/BlueJ constraint (records, sealed types and pattern switches cut core code by about 30%). Maven is used because it is installed everywhere and needs no wrapper download. Gradle is acceptable later if build logic grows.

**ADR-2: Desktop UI is JavaFX (decision gate at M4).** It is a UI-dense strategy game: tables, charts, a map, modals, text-heavy events, scalable fonts and accessibility.
- **For JavaFX:** layout, CSS theming, charts, HiDPI and accessibility APIs, and `jpackage` installers; Steam integration via steamworks4j.
- **libGDX** is better for animation-heavy games but makes complex forms costly.
- **The current Java2D immediate-mode toolkit** is proven but hand-rolled: no layout, no accessibility, no text input or IME, and it is tied to a fixed design resolution.
- **Gate:** a 2-week spike builds the whip-count screen and the US map in JavaFX, with libGDX as the fallback.

**ADR-3: Saves = setup + command log (+ JSON snapshots in 1.0).** This rules out Java serialization (brittle) and cross-game raw-stream parsing.

**ADR-4: The theater seam.** The presidential game talks to war only through `TheaterScenario` and `TheaterOutcome`. This keeps the base game shippable and reviewable without the operational wargame, and lets EXP-1 plug in without core changes.

**ADR-5: No browser build before 1.0.** The source games' Java-to-JS translator is not in any repository. If a web demo is needed for marketing, evaluate TeaVM against `mandate-core` (pure Java, no reflection, no threads) in a post-1.0 spike.

**ADR-6: Content as CSV now, JSON + schema in M2.** The CSV loader is intentionally minimal. M2 introduces JSON content with a validator CLI (`mandate lint`): unknown ids, unreachable events, dominated options and missing localisation keys.

## 9. Testing strategy

| Layer | Today | Gate |
|---|---|---|
| Unit (models, rules) | 18 tests: electorate, rules, economy response, theater monotonicity, RNG independence, the no-UI-import rule | every commit |
| Determinism | same seed → same log; replay(history) → same game and legacy | every commit |
| Balance | `BalanceTest`: Toss-up Democratic win rate within 35-65% over 240 games | every commit; tighten to 45-55% at 1.0 |
| Batch report | `mandate batch N SCENARIO`: win rates per party, re-election rate, EV spread, laws, war frequency, tipping points | nightly in CI; results tracked over time |
| Soak | 10,000 autopilot games with no exceptions | nightly (M2) |
| Golden models | M1: the cell model reproduces BG calibration output | M1 onward |
| UI | screenshot tests of key screens (JavaFX headless with Monocle) | M4 onward |

## 10. Performance budget

- **Uniform-swing model:** a full game takes about 2 ms; 1,000 games take about 2 s, measured.
- **Cell model:** 51 × 96 cells × 11 issues = about 54k cell evaluations per projection. That is fine weekly. Monthly approval-by-state is also fine.
- **Forecast:** 1,000 draws × 51 states, re-using the projection, under 50 ms.
- **Command Mode (IC engine):** about 3 s per war headless; acceptable as an opt-in.
