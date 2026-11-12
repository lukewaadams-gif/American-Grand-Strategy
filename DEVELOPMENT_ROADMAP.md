# Development roadmap

Estimates are in **person-weeks (pw)** for one experienced Java developer with design support. Calendar time depends on team size; a 3-person core team (design/engineering/art-UI) is assumed in the dates.

## Milestones

| # | Milestone | Content | Exit criteria | Est. |
|---|---|---|---|---|
| **M0** | **Foundation** ✅ *(this commit)* | Multi-module Java 21 project; deterministic core with command model and named RNG streams; full 2028→2032 loop (campaign, debates, transition, governing, midterms, re-election, legacy); text CLI; autopilot; balance batch; 18 tests | `mvn verify` green; `mandate batch 1000` runs in about 2 s; Toss-up Democrat win rate about 51% | done |
| **M0.5** | **Modular engine** ✅ | `WorldState` with owned domain states; `GameEngine`, `SimulationClock`, `EventScheduler`, `DecisionResolver`, `ConsequenceLedger`, `SaveManager` (v2 snapshots, v1 replay), `DeterministicRandomManager`; all constants in `tuning.properties`; operational war turns; prototype re-hosted on the engines | 63 tests green; required-behaviour, architecture and golden regression tests | done |
| M1 | **One electorate** | Port BG's 96-cell model behind `ElectorateModel`; refreshed demographics (ACS 2019-23); approval by group and state feeds midterms and 2032; ME/NE districts; 2030 apportionment for 2032 | Golden test matches BG calibration (2024 within 0.5 points per state); balance gates still pass | 5 pw |
| M2 | **Content pipeline** | JSON content + schema + `mandate lint` (ids, dominated options, real-name check); move tunables to `tuning.json`; 40 events, 30 bills; save/load of the command log; soak test of 10,000 games | Lint clean; designers add an event without engine code; save and restore round-trips | 4 pw |
| M3 | **Governing depth** | Promises → agenda; party factions + opposition strategy; committee chairs and leaders; veto/override; Congress's own bills; filibuster nuclear option; annual budget fight; debt limit; Fed chair; tariffs; a played midterm | Act II playtest: 70%+ of testers rate months 8-20 "engaging" (R-05 gate) | 7 pw |
| M4 | **Desktop vertical slice** | ADR-2 UI spike (JavaFX vs. libGDX), then: home dashboard, US map with polls, campaign-week planner, whip-count screen, event card, election night, legacy screen; settings; tutorial for week 1 and month 1 | A non-developer completes a 2028 campaign unaided | 8 pw |
| M5 | **Crisis calibration** | Fix the IslandChain prerequisites (seed scrambling, statics into `Game`, AI ceasefire, host termination, the landing-region imbalance) in a fork module; `IslandChainOracle` grid runs; fit the `MilitaryEngine` operational coefficients; 5 hot spots; regional posture | Theater outcomes within ±10 points of oracle frequencies across the grid | 4 pw |
| **MVP** | **Playable alpha** | M0-M5 integrated; placeholder art | All MVP boxes in `FEATURE_PRIORITY.md` §4 checked; balance gates green; 20 external playtesters | — |
| M6 | Early Access content | 80 events, 40 bills, 12-candidate fictional cast + creator, portraits, forecast, VP pick, coattails, acceptance speech, Steam achievements and cloud saves | Feature freeze; Steam page live with 20k+ wishlists | 8 pw |
| M7 | **Early Access launch** | Packaging (jpackage, all three operating systems), crash reporting, telemetry (opt-in), legal review, content review | Store approval; crash-free rate of 99%+ sessions | 3 pw |
| M8 | **1.0** | Early Access feedback; accessibility; Steam Deck; balance pass; difficulty modes; epilogue writing | Reviews at least 85% positive in Early Access; gates tightened (party win rate 45-55%) | 10 pw |
| E1 | **Command Mode** (expansion) | IslandChain engine as a `TheaterResolver`, launched from the war decision; IC UI adapted; content review | Opt-in; a war plays in under 60 minutes | 12 pw |
| E1b | The Long Road | Primaries, endorsements, contested convention (exploit fixed) | | 10 pw |
| E2 | Independents | Ballot access, playable third party, House contingent election | | 6 pw |
| E3 | Eras | Selected historical presidencies, re-authored from Resolute Desk content | | 12+ pw |

**Order rationale.** M1 comes before UI because the electorate is the foundation of credibility and every later screen displays it. M3 is gated on an Act II paper and CLI playtest, because R-05 is the top design risk. M5 can run in parallel with M3 or M4.

## Release gates (the batch tool enforces these)

| Metric (Toss-up, Standard, 1,000 games) | MVP | 1.0 | Today |
|---|---|---|---|
| Democrat 2028 win rate | 40-60% | 45-55% | 51% ✅ |
| Re-election rate, each party | 45-75% | 50-70% | 55% D / 65% R ✅ |
| EV standard deviation | ≥ 45 | ≥ 50 | 34 ❌ (B-04) |
| Taiwan war reached by a *neglectful* president (no deterrence actions, accommodating crisis answers) | 10-25% | 10-20% | 15% ✅ (deterring autopilot: 0%, as intended) |
| Any Taiwan rung reached | 30-60% | 30-50% | 78% ❌ (B-38) |
| Average laws per term | 4-9 | 5-9 | 8.7 ✅ |
| Exceptions in 10,000-game soak | 0 | 0 | not yet run |

## Prioritised backlog

**Priority:** **P0** is required for the next milestone, **P1** for the MVP, **P2** for 1.0, **P3** later.

**Size:** S is 1 day or less, M is 2-4 days, L is about 1-2 weeks.

| ID | Pri | Size | Item | Acceptance |
|---|---|---|---|---|
| B-01 | P0 | L | Port the BG 96-cell electorate as `CellElectorateModel` (M1) | Golden calibration test; the swap needs one line in `Game` |
| B-02 | P0 | M | Approval by state from the cell model, and use it for midterm Senate races and 2032 | The midterm results correlate with approval in each state |
| B-03 | P0 | S | Poll **averages**: rolling average of 3-4 weekly draws instead of one fresh draw (state polls currently jump ±3 a week) | Week-to-week poll change SD under 1.2 points |
| B-04 | P0 | M | Increase outcome spread: candidate-specific variance, late-breaking events, turnout shocks | EV SD ≥ 45 without breaking party balance |
| B-05 | P0 | M | JSON content + schema + loader; migrate the CSVs and `EventCatalog` | Old content loads unchanged; `EventCatalog.java` deleted |
| B-06 | P0 | M | `mandate lint`: dominated options, unknown ids, real-person name list | CI fails on a seeded bad file |
| B-07 | P0 | S | AutoPilot strategy variants (no-deterrence, max-legislation, bully-pulpit) for batch probes | The batch prints a per-variant table |
| B-08 | P0 | S | Save/load: write setup + commands to `~/.mandate/saves/*.json`; CLI `load` | Round-trip test |
| B-09 | P1 | M | Campaign promises (from the BG promise list, re-authored) that pre-fill the agenda and move groups | Breaking a promise shows in the 2032 environment and is logged |
| B-10 | P1 | L | Party factions and opposition strategy (port from RD) | A bipartisan deal lowers faction mood; low mood lowers base turnout |
| B-11 | P1 | M | Committee chairs and leaders as gatekeepers in divided government | The whip screen shows "the Chair won't schedule it" with a lever to overcome it |
| B-12 | P1 | M | Veto/override and Congress's own bills under divided government | Opposition bills arrive at least twice a year |
| B-13 | P1 | M | Annual budget fight and debt-limit event chains | Each fires on schedule with 3+ meaningful options |
| B-14 | P1 | M | Played midterm (4-week mini-campaign: money and visits to Senate and House battlegrounds) | Player effort shifts the midterm by up to ±3 points |
| B-15 | P1 | S | Economy: oil price as an explicit variable fed by Middle East tension | An oil spike shows in inflation and approval |
| B-16 | P1 | S | Interest cost = debt × average rate (rolling toward market rates) | The deficit rises with rates |
| B-17 | P1 | M | Filibuster nuclear option with a moderates' backlash risk | Available only when the party controls the Senate; irreversible |
| B-18 | P1 | L | JavaFX spike: whip screen + map (ADR-2 gate) | Decision recorded in ADR-2 |
| B-19 | P1 | L | Desktop vertical slice screens (M4 list) | Unaided playtest success |
| B-20 | P1 | M | IslandChain fixes in an isolated fork module: seed scrambling, statics into `Game`, AI ceasefire, host termination, region balance | IC batch shows variance across plans and regions |
| B-21 | P1 | M | `IslandChainOracle` + coefficient fit for the `MilitaryEngine` operational model | Within ±10 points of oracle frequencies |
| B-22 | P1 | S | ME/NE district electors | 2024 tally is D 226 with splits (ME-2 R, NE-2 D) |
| B-23 | P1 | S | 2030-apportionment electoral votes for the 2032 election | EV table switches at 2032 |
| B-24 | P1 | M | Tutorial for campaign week 1 and governing month 1 | First-time tester completes both without help |
| B-25 | P2 | M | Forecast screen (Monte Carlo from the same error model) + path to 270 | The forecast is calibrated: 70% calls win about 70% of the time in batch |
| B-26 | P2 | M | Fed chair appointment and credibility as a lever | A dovish chair raises 2032 growth and inflation |
| B-27 | P2 | M | 5 hot spots, regional posture (Pacific, Europe, Middle East) | Posture feeds deterrence in each region |
| B-28 | P2 | L | Fictional cast of 12+ with procedural portraits (port BG `Portrait`) and a candidate creator | |
| B-29 | P2 | M | VP pick and coattails (joint rallies for Senate candidates) | The VP's home state shifts; coattails move Senate margins |
| B-30 | P2 | M | Press conferences redesigned as group trade-offs (not a quiz) | No dominated answers (lint) |
| B-31 | P2 | S | Debate question beats (2-3 per debate) *if* playtests show 4 postures are too thin | Playtest decision |
| B-32 | P2 | M | Steam integration (achievements, cloud saves) | |
| B-33 | P2 | S | Approval during the campaign phase keeps moving (economy and events still step monthly during the 2032 campaign) | Approval no longer freezes from July to November 2032 |
| B-34 | P2 | S | Event-writing pass: no near-identical options (e.g. "A weak jobs report" today) | Lint + design review |
| B-38 | P1 | S | Retune Taiwan drills frequency (78% of terms reach a rung; target 30-50%) without losing the war-frequency gate | Batch gates |
| B-39 | P0 | S | Run the 12-week prototype playtest (GAMEPLAY_DESIGN.md §8, H1-H7) with 8-12 players | Each hypothesis reported pass or fail with evidence |
| B-40 | P1 | M | Decide governing cadence (monthly with weekly zoom for set pieces) from H7 results (O-1) | ADR recorded |
| B-41 | P1 | L | Fold prototype systems into `Game`: bloc whip with "wants", cabinet posts, stopgap, multi-week crises, group approval (O-3) | Foundation batch gates still pass |
| B-42 | P1 | S | Bipartisan bills can die: opposition primary season and outside events (O-2) | Dealmaker law rate below 95% in divided government |
| B-43 | P1 | M | Several bills in flight, competing for attention (O-4) | The strategy-probe tests still pass |
| B-44 | P2 | S | Move crisis kinds, nominee archetypes and stopgap offers to content files (O-5) | No engine change to add a crisis kind |
| B-45 | P1 | M | Keep the economy and opinion stepping during a war (weekly steps every 7 operational days) instead of pausing | Approval and markets move during the war |
| B-46 | P2 | S | Warn on loading a v1 save whose rules fingerprint differs; offer the v2 snapshot instead | Load dialog test |
| B-47 | P2 | M | Move the prototype's bill-design and crisis constants into content and tuning (with B-44) | No numeric literals in `opening/` beyond indices |
| B-48 | P3 | M | Optional one-off importer for Battleground 2028 saves (only if a migration path is wanted; the raw Java-serialization format makes it fragile) | Decision recorded |
| B-35 | P3 | L | Command Mode (E1) | |
| B-36 | P3 | L | Primaries (E1b) | |
| B-37 | P3 | M | Web demo via TeaVM (ADR-5) | |

## Immediate next steps (the next two weeks)

1. B-03, B-07, B-08: small items that make the foundation useful for designers right away.
2. Paper/CLI playtest of Act II with 5 people (the R-05 gate), using `mandate play`.
3. Start B-01 (the cell-model port). It is the long pole of M1.
4. Owner decisions needed:
   - confirm the code rights and choose the new repository's licence (R-18);
   - approve the fictional-cast policy (R-02);
   - confirm the working title, or commission a name search.
