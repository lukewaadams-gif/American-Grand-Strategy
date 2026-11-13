# The Taiwan crisis: Island Chain, ported

The Taiwan crisis is a reduced port of the Island Chain wargame (`docs/research/ISLAND_CHAIN.md`; the full specification is `island-chain-spec.md`, cited here as "spec"). It runs as crisis days, then war turns of 1, 6 and 12 hours, on a map of 22 sea zones. It keeps the models that make the war deep: missile defence, interception, the air war, submarines, landings, the ground war, the blockade, Taiwan's energy and will, escalation, warning and nuclear risk. The decisions in `CRISES.md` apply. Choices not yet discussed were made in favour of the source and realism; they are listed under "Decisions to review" below.

This document covers the headless engine. The presidency's integration (`GameEngine`, commands, saves) and the browser client are not built yet.

## Where it lives

| | |
|---|---|
| Engine | `mandate-core/src/main/java/game/mandate/core/taiwan/`: `TaiwanEngine`, `TaiwanState`, `TaiwanOrder`, `TaiwanScenario`, `TaiwanResult`, `TaiwanData`, `TaiwanHarness` |
| Rules, by subsystem | `TaiwanSea` (movement, ships, submarines, shipping, mines), `TaiwanAir` (coverage, CAP battles, launches), `TaiwanFire` (salvos, ship defence, interception, damage), `TaiwanLand` (landings, airlift, the ground war), `TaiwanWorld` (programmes, Taiwan, the economy, politics, allies, escalation, the end), `TaiwanIntel` (contacts and warning), `TaiwanAI` (the PRC planner and the US and Taiwan staffs) |
| Helpers | `Ctx` (one call's context: state, data, random streams, output), `TMath` (exp, ln and pow in plain arithmetic) |
| Data | `mandate-core/src/main/resources/data/taiwan/*.csv`: 17 files, all registered in `DataResources` |
| Tests | `TaiwanTest` (28 tests) |

## What is kept, simplified and dropped (spec 11.2 to 11.4)

**Kept as written** (the formulas and constants of spec sections 1 to 9):
- turn lengths of 24, 1, 6 and 12 hours, the cut at H-hour, and the phase changes;
- ship missile defence: acquisition, launch failures, gross errors, interceptor pk and opportunities by speed class, fire channels, terminal defence;
- ship damage: a mission kill at 0.45, sinking past 1.0 with 78%, big ships first for ballistic weapons;
- land interception and base damage: duds, craters and runways, shelters, the parked-aircraft model, fuel, ports, command posts, launchers;
- CAP coverage, CAP battles and strike-package attrition;
- submarine detection, attack and counter-attack;
- amphibious lift, loading and unloading, sea state, beach capacity and defence, airborne drops;
- the ground step: movement, supply, combat and captures;
- blockade throughput, Taiwan's energy, chips and will;
- the rules-of-engagement matrix and the rungs;
- the nuclear-risk formula and its crisis threshold;
- the end conditions, the 75-day judgement and the ceasefire terms;
- the PRC plan choice, preparation leads, `deterrence()` and the go/no-go rule;
- indicators and warning.

**Simplified:**
- **Zones, not a water mask.** 22 sea zones plus the airspace over Taiwan, and off-map hubs. Distances between 77 places (zone centres, ports, beaches, hub entry points) come from the source's own router, run once offline (`transit.csv`). Transit time is distance ÷ (knots × 1.852).
- **Positions in kilometres.** Every place has x and y on an azimuthal projection centred on 23.7°N 121.0°E, so distances are plain arithmetic.
- **Strikes resolve in the hour they launch.** The launch interval and the reach rules stay. Acquisition still uses the contact's age × 28 km an hour.
- **Contacts by zone.** Each side holds a contact per enemy unit: the zone, the identification level, an error radius and the hour last seen.
- **Ships as counts.** A group holds lines of ships by class. A ship is tracked alone only once it is hit.
- **Seven economies:** the US, China, Taiwan, Japan, Korea, the EU and the world, with the source's channel model and W rows. Brent, LNG, war-risk insurance and a US equity index stay.
- **Politics.** In the standalone game the source's approval, Congress and US support stay. In the presidency, AGS owns approval and Congress; the crisis keeps "support for defending Taiwan" because the ceasefire end condition reads it.
- **Data, not code.** Ship classes, aircraft, munitions, land systems, zones, bases, sectors, beaches, facilities, countries, programmes and the order of battle are CSV files.
- **Role tags.** The staffs read role tags in the order of battle (`csg`, `sag`, `amph_south`, `cg` …), not unit names.

**Dropped:**
- the A* router, the water mask and the coastline (the client will draw zones and sector outlines);
- animation, sound, hot-seat play, the autosave format, the briefing library and the debug counters;
- everything spec 13.2 lists as dead, including the "War stockpiles" programme;
- real unit designations and ship names (spec 12): every unit and ship name is generic, and a test checks the data;
- hourly price noise;
- fire support from ships (always zero in the source).

## Zones and time

**The zones** (`zones.csv`, radius in km):

| | |
|---|---|
| The strait | Taiwan Strait North (85), Central (85) and South (95); Penghu Channel (45) |
| Around Taiwan | Bashi Channel (100), Luzon Strait (110), East of Taiwan (140), Northeast of Taiwan (120), Yonaguni Gap (70) |
| North | Miyako Strait (100), East China Sea South (200) and North (230), Yellow Sea (230), Okinawa Approaches (150), Sea of Japan and Korea Strait (300) |
| East | Philippine Sea West (280), North (330) and Central (380), Guam Approaches (300) |
| South | South China Sea North (230) and Central (280), West of Luzon (190) |
| Over Taiwan | the island's airspace (170) |

A unit is in a zone, at a port, or in transit between two places with a departure and an arrival hour. A unit patrolling a zone is spread over its radius. The chance that two units meet within a range r is min(1, (r ÷ R)²), falling off beyond the zone's edge.

**Time:**
- **The crisis:** turns of 24 hours. A turn ends just after H-hour (05:00 on D-day) when H-hour falls inside it, or just after a US pre-emptive strike.
- **The war:** 1-hour turns for the first day, 6-hour turns to day 7, then 12-hour turns.
- **Inside a turn:** hourly ticks. Each tick runs, in order: arrivals, movement, the air war, batteries, naval missile fire, submarines, landings, the ground step (every 6 hours), shipping, intelligence and repairs. At midnight the daily update runs: programmes, production, Taiwan, the economy, politics, the allies, warning, nuclear risk and the end conditions.
- **The computer acts at the start of a turn,** as in the source. Its daily routines (sanctions, allies, ceasefire proposals) now run once a day on whatever turn first passes 09:00 (US), 05:00 (China) or noon (proposals).

## The sides and command

- **The side.** The player commands the US or China (`TaiwanScenario.side`). The computer plays the other. In the presidency the player is always the US.
- **Directives by default.** Each turn the player's side runs on its staff's playbook. A directive the player gives is held from the staff until `Playbook()` hands everything back.
- **Unit orders are optional.** A unit given an order is held until released, as in the source. They cover:
  - groups: patrol, screen, return to port;
  - submarines: patrol, return to port;
  - squadrons: CAP, strike, hold;
  - batteries: fire, hold.

  Airlift and airborne drops stay with the staff.

**US directives** (`TaiwanOrder`, spec 11.5):

| Directive | Options | What it does |
|---|---|---|
| `Rung(n)` | 0 to 6 | The rules of engagement (`TaiwanState.BLUE_RUNG`), capped by the commitment |
| `Mainland(n)` | 0 none, 1 limited, 2 broad | Limited: ports, embarkation areas, air bases, coastal launchers (rung 4). Broad: missile forces, radars, command (rung 5) |
| `Fleet(n)` | 0 forward, 1 stand-off, 2 withdrawn, 3 commander's choice | The carriers' zones: Philippine Sea West; North and Central; Guam; or the source's rule (back off from ballistic missiles) |
| `AirPriority(a, m, t, f)` | shares | Strike fighters for anti-ship strikes, mainland strikes, CAP over Taiwan, fleet defence. It replaces the source's `id % 4`. The default is ½, 0, ¼, ¼ |
| `Submarines(n)` | 0 the strait, 1 approaches and chokepoints, 2 distant blockade, 3 commander's choice | The patrol zones of US attack submarines |
| `MunitionsPolicy(n)` | 0 conserve, 1 normal, 2 surge | Conserve halves salvos and keeps a quarter of the starting LRASM and SM-6 stocks back. Surge fires whole squadrons |
| `Convoys(b)` | on or off | Escort shipping to Taiwan |
| `AllyRole(c, n)` | 0 access, 1 basing, 2 defend its own territory, 3 full combat | What the staff asks of each ally |
| `TaiwanAsk(w)` | mobilise, partial, ration, disperse | Requests to Taipei |
| `Nuclear(n)` | 0 normal to 3 threaten; 4 use | Nuclear posture. 4 is use: the game ends in catastrophe |
| `Preempt(b)` | strike or cancel | A pre-emptive strike in 12 hours (INTERVENE only) |
| `Ceasefire(t)`, `Answer(b)` | terms 0 status quo ante, 1 in place | Termination |
| `Programme(id, b)`, `Diplomacy(c, a)`, `Economic(m)` | | Crisis decisions: 15 preparation programmes, actions toward a country, sanctions |

**Chinese directives** (standalone):

| Directive | What it does |
|---|---|
| `Plan(...)` | The course of action, the H-hour options (strike US forces, strike Japan, cyber and space, decapitation, Penghu first), the landing region and the blockade's lethality |
| `DDay(d)` | D-day, from tomorrow to day 110; H-hour is 05:00 |
| `Prepare()` | Starts every programme the plan needs |
| `StandDown()` | No war; programmes halt. The crisis ends seven days later as "Deterrence holds" |
| `Deception(w)` | False reports, or a decoy flotilla dressed as an amphibious group |
| `Rung`, `Nuclear`, `Ceasefire`, `Answer`, `Programme`, `Diplomacy`, `Economic` | As for the US; 14 programmes |

**The commitment** (from the presidency's `TheaterScenario`) caps the US rung:
- STAND_ASIDE: rung 0, no preparation programmes, no sanctions;
- ARM_AND_SANCTION: rung 1, with arms to Taiwan and sanctions;
- INTERVENE: no cap.

If China strikes US forces, America fights whatever the commitment was. Beijing's go/no-go rule reads the commitment: −25 to deterrence for standing aside, −12 for arming and sanctioning.

## The forces by year (spec 10)

The order of battle (`oob.csv`) is anchored on the source's forces of 1 March 2027. Each line has a count, a change per year, a cap and a range of years. The crisis year (2029 to 2036) sets the count: count + per year × (year − from), capped and rounded. The US munition pools grow with production (about 9.6 months of output a year).

Estimates after 2027 (all to review):
- **US:** B-21 bombers from 2029 (one squadron of 8, another every two years, up to 4); a new F-35A wing; newer attack submarines replacing older ones; more Marine anti-ship and Army mid-range batteries.
- **China:** more Type 055 and 052D destroyers and Type 054 frigates; Type 076 amphibious ships from 2028 (fewer in the pessimistic case); a fourth carrier group from 2032; a J-20 brigade a year (to 9 more) and J-35A brigades; J-11B retirements; more DF-17 and DF-26 brigades and missiles; more submarines.
- **Taiwan:** new F-16V deliveries; Harpoon coastal battalions from 2 in 2027, +2 a year to 12 (base; faster when optimistic, slower when pessimistic); corvettes to 12; new submarines from 2029; older frigates retired.
- **Allies:** Japan's Tomahawks (to 400), more F-35A and F-35B, new frigates; the Philippines' coastal missiles; Australia's first nuclear submarine from 2032; Korea's F-35A.

The assumption `forcesYear=2027` keeps the 2027 forces in any crisis year, for calibration.

**Presets** (the source's settings):
- **Base case.**
- **Optimistic:** civil airports open to US aircraft, extra shelters in Japan, Japan in the war at once, a weaker PLA amphibious fleet (× 0.7), fewer PRC missiles (× 0.85), Philippine basing.
- **Pessimistic:** no maritime JASSM, Taiwan reacts late, Taiwan's ground forces × 0.75, a late US mobilisation, PRC missiles × 1.25, US submarines withheld, a simultaneous crisis elsewhere.

**Custom toggles** go in `TaiwanScenario.assumptions` as `key=value` pairs joined with `;`. Keys:
- `japanEntry` (0 to 2), `jsdfDefensiveOnly`, `civilAirports`, `philippinesBasing`;
- `taiwanDelay`, `jassmMaritime`, `shipDefensePoor`, `usMobilizationLate`, `usSubsWithheld`, `simultaneousCrisis`, `extraShelters`;
- `mainlandAllowed`, `strikeJapan`;
- `prcMissiles` (0.5 to 2), `plaAmphib` (0.3 to 1.5), `taiwanGround` (0.5 to 1.5);
- `forcesYear` (2027 to 2036).

## How it ends, and the fallout (spec 11.6, 11.7)

The source's ten outcomes map onto the presidency's statuses:

| Outcome (code) | Status |
|---|---|
| Deterrence holds (0): China stood down, or did not attack by day 110 | DETERRED |
| Taiwan defended (1), Pyrrhic defence (2), China stands down (9) | DEFENDED |
| Stalemate (3), Partial PRC success (4), Negotiated ceasefire (8) | STALEMATE |
| PRC victory (5), Taiwan capitulates (6) | FALLEN |
| Nuclear catastrophe (7) | NUCLEAR |

**During the war, each turn's `Outcome.effects`:**
- `WorldShift("kia", n)`: Americans killed since the last report;
- `Tension(TAIWAN_STRAIT, …)`: +15 at war, ±4 for each Chinese rung;
- `WorldShift("rel:CN", −20)` at war, −10 more at rung 5;
- `Rally(16)` and `Approval(+7)` when China strikes US forces first;
- `Approval(−3)` for each carrier sunk, `Approval(+2)` for a national emergency;
- `Deterrence(Δ)` from US programmes and sanctions in the crisis;
- `WorldShift("rel:XX", Δ)`: half of a daily stance change of 2 points or more, for the allies and others mapped to AGS codes (JP, KR, PH, AU, IN, VN, GB, FR, DE, ID, RU, KP);
- `PresidentStrain(−3, 8)` at a nuclear crisis.

**At the end,** `TaiwanResult.fallout` holds these (they are also in the turn's effects):
- `Performance((score − 50) ÷ 10)`, from the source's 0-to-100 score;
- `EconomyShock(growth, inflation, months)`:
  - growth = −(the average US output gap over the crisis) × 100;
  - inflation = 0.02 × (average Brent − 92) + 1.5 × the worst chip shock;
  - months = war days ÷ 30 + 6;
- `FinancialShock(−min(15, 40 × the worst drawdown of US stocks))`;
- `Deficit(cost ÷ $310bn)`. The cost is in $ million: US munitions spent, US aircraft lost, $2bn a ship and $11bn a carrier, and $300m a day of US combat;
- `Munitions(0.3)` if "Mobilise reserves" was completed;
- by status:
  - DETERRED: `Deterrence(5)`, `Tension(−15)`;
  - DEFENDED: `WarEnded(75)` (65 if pyrrhic), `Tension(−10)`, `rel:TW +15`, allies +5 if America fought;
  - STALEMATE: `WarEnded(45)`, `Tension(+5)`;
  - FALLEN: `WarEnded(15)`, `Tension(+10)`, `rel:TW` −10 (−40 if America never fought, and allies −15);
  - NUCLEAR: `WarEnded(5)`, `Tension(+20)`;
- `Salience(CHINA, 2)` after a war, and `Salience(ECONOMY, …)` with a chip shock;
- `PresidentStrain` for the crisis's length and nuclear crises;
- `Readiness(DEFENSE, −…)` for ships and aircraft lost.

## Calibration against the source's harness (spec Appendix B)

`TaiwanHarness` plays the computer against itself. A fixed plan goes ahead, as the source's harness forced it. Twelve games per row, seeds 1000 + 7919 × n, with the forces of 2027 (`forcesYear=2027`). US losses are averages per game.

| Plan, preset | Source: outcomes / war days / US killed / US ships (carriers) / US aircraft / PRC ships (amphibious) / km² held | Port |
|---|---|---|
| Invasion, base | 8 defended, 4 capitulate / 30 / 5,637 / 18 (1.5) / 417 / 201 (91) / 6,111 | 6 defended, 1 partial, 5 PRC victory / 20 / 6,845 / 20.1 (2.0) / 453 / 183 (84) / 7,609 |
| Invasion, optimistic | 10 defended, 2 capitulate / 23 / 6,342 / 21 (1.9) / 412 / 212 (87) / 4,582 | 10 defended, 1 partial, 1 victory / 16 / 6,506 / 20.5 (2.4) / 408 / 174 (69) / 3,046 |
| Invasion, pessimistic | 5 defended, 4 partial, 3 capitulate / 51 / 5,392 / 16 (0.8) / 369 / 185 (82) / 5,992 | 1 stalemate, 4 partial, 6 victory, 1 capitulates / 28 / 5,438 / 17.3 (2.5) / 366 / 140 (61) / 12,455 |
| Blockade, base | 12 defended / 76 / 1,287 / 4 (0) / 267 / 204 (0) / 0 | 9 partial, 3 ceasefire / 74 / 2,349 / 9.3 (0.4) / 246 / 193 (0) / 0 |
| Islands, base | 12 partial / 31 / 0 / 0 / 0 / 70 (31) / 308 | 12 partial / 31 / 0 / 0 / 0 / 63 (27) / 306 |
| Strikes, base | 12 ceasefire / 61 / 6,055 / 19 (1.3) / 356 / 189 (0) / 0 | 8 ceasefire, 3 pyrrhic, 1 partial / 60 / 7,202 / 21.1 (2.4) / 366 / 182 (0) / 0 |
| Quarantine, base | 11 defended, 1 partial / 76 / 1,356 / 4 (0.2) / 180 / 198 (0) / 0 | 1 defended, 7 partial, 4 ceasefire / 75 / 1,221 / 7.0 (0) / 160 / 190 (0) / 0 |
| Planner's choice, base | (all quarantine, spec 13.1 item 14) | 2 defended, 3 partial, 7 ceasefire |

Other columns match closely. For the base-case invasion:
- PLA amphibious ships sunk: 93% (source 91%);
- PRC killed: 23,905 (source 27,679);
- output gaps US, China, Taiwan and world: 3.3, 7.2, 29.4 and 5.2% (source 4, 7, 32 and 6).

**Why the results shift.** Fixing the source's bugs changes some results, as the spec expected:
- **Ceasefires now happen.** The source's computer never proposed one (13.1, item 1). Now both sides propose from day 5. A stalled invasion ends in "PRC victory" or a ceasefire on current lines, where the source ran on to a capitulation or the judgement. Invasions are shorter (20 days, not 30).
- **The US staff now asks its allies every day.** In a blockade, Japan joins the war. Its flotilla near Yonaguni, which carried most of the escort strength in the source's blockade zone, is then sunk. Shipping to Taiwan falls, coal runs out near day 65, and electricity is below 0.45 at the day-75 judgement, which the source scores as "Partial PRC success". The source's own margin was thin: its electricity at the judgement was 0.48 to 0.73.
- **A late US mobilisation now delays reinforcements by 14 days** (13.2). The pessimistic invasion is much worse for Taiwan.
- **More carriers are lost** (2.0 to 2.5 against the source's 0.8 to 1.9). The US destroyer squadron and surface group escorting shipping in a blockade or quarantine are lost in every game (7 ships).

**With the forces projected** (12 games per row, base case unless stated):

| Plan | 2030 | 2034 |
|---|---|---|
| Invasion | 11 defended, 1 victory; 14 days; 6,781 US killed; 2.3 carriers | 11 defended, 1 partial; 14 days; 7,950 killed; 3.3 carriers |
| Invasion, pessimistic | 6 defended, 4 partial, 2 victory | 10 defended, 2 victory |
| Blockade | 10 partial, 2 ceasefire | 6 defended, 6 partial |
| Islands | 12 partial | 12 partial |
| Strikes | 8 ceasefire, 2 pyrrhic, 2 partial | 8 ceasefire, 4 pyrrhic |
| Quarantine | 4 defended, 6 partial, 2 ceasefire | 5 defended, 4 partial, 3 ceasefire |
| Planner's choice | 2 defended, 6 partial, 4 ceasefire | 8 defended, 4 ceasefire |

Taiwan's projected coastal missile battalions do the most to shift the invasion toward Taiwan. China's growing forces raise the costs on both sides.

**Speed** (on the JVM, once warm): a crisis day takes about 4 ms, a six-hour war turn about 4 ms and a twelve-hour turn about 6 ms. A whole invasion game takes 0.4 to 0.8 seconds. The test's limit is 100 ms a turn. The browser build has not been timed: the client does not call the engine yet.

## The source's bugs, fixed (spec 13)

| Spec | The bug | Now |
|---|---|---|
| 13.1.1 | The US daily routine ran only on turns starting at 09:00, and ceasefire proposals only at 12:00 | Each runs once a day, on the first turn past its hour |
| 13.1.2 | Quickstrike mines were fired at ships as missiles | Mines are not anti-ship weapons. They are never fired at ships |
| 13.1.3 | Air-laid mining never happened | US bombers lay mines in the strait zones the PLA's amphibious groups cross |
| 13.1.5 | "Return to port" for a submarine ignored the chosen port | The home port is kept if it is usable |
| 13.1.7 | Ships out of offensive missiles stayed at sea | A group out of offensive missiles returns to port to rearm |
| 13.1.8 | The chip lag and Taiwan's "last killed" count were static and lost on loading | Both are in the state and saved |
| 13.1.9 | Aircraft dispersal was counted twice | Counted once |
| 13.1.10 | Battery dispersal never recovered | It recovers 0.02 an hour after six hours without firing |
| 13.1.11 | Every allied SAM defended everything | An allied battery defends only its own soil until its country is in the war |
| 13.2 | The PLA amphibious multiplier was never read | It scales the amphibious and landing ships |
| 13.2 | "US mobilises late" did nothing | It adds 14 days to US reinforcements |
| 13.2 | "JSDF defensive only" did nothing | Japan defends its own territory and fights only once attacked |
| 13.2 | "Strike Japanese forces at H-hour" was never read | Japan's air bases join the H-hour fire plan |
| 13.2 | The PLA's mines were counted but never drawn | Covert mining draws on the PLA's stock |
| 13.2 | "War stockpiles" had no effect | Dropped |
| (found in porting) | Strikes on command sites on Taiwan did not touch its command and control | Each hit lowers it by 0.06, so decapitation strikes work |
| 13.1.14 | Neighbouring seeds gave the same random plan | All draws go through `DeterministicRandomManager` with `taiwan.*` streams |

Not changed:
- Convoys still have no merchants to escort (13.1.6). "Escort shipping" adds escort weight and the source's shipping bonus.
- The player's airlift order (13.1.4) is not offered; airlift stays with the staff.
- Contact labels (13.1.12) and the threat rings (13.1.13) are the client's.

## Decisions to review

1. **The projection after 2027** is an estimate from public plans (the list above). It decides the projected results more than anything else.
2. **Fixed plans go ahead.** When the scenario fixes China's plan, the go/no-go rule is skipped, as the source's harness forced it. With no plan, the planner chooses and may stand down.
3. **Beijing reads the commitment:** −25 to its deterrence when America stands aside, −12 when it arms and sanctions. The source had no such setting.
4. **A stand-down ends the crisis seven days later** as "Deterrence holds". The PRC planner cannot attack after day 110, and it does not avoid typhoon months when it picks D-day.
5. **The default air shares are ½ anti-ship, ¼ CAP over Taiwan and ¼ fleet defence.** The source split by `id % 4`. Without land-based fleet cover, this is closest to the source's results.
6. **Allies' relations move at half the crisis's stance change,** and only for daily changes of 2 points or more.
7. **The fallout's factors** (score to performance, the output gap to growth, Brent and chips to inflation, the cost to the deficit, the ally shifts) are proposals for tuning.
8. **Nuclear use** is `Nuclear(4)` and ends the game as "Nuclear catastrophe". The computer never uses nuclear weapons.
9. **Unit orders are basic:** move, patrol, return to port, CAP, strike, hold and fire. The state holds every unit, its mission and its "held" flag, so the client can offer more later.
10. **Convoys are unchanged** from the source: no merchants to escort.
11. **The meeting model** (a unit spread evenly over its zone) replaces the source's point geometry. It sets detection and the chance that a strike finds its target.
12. **Strikes resolve in the hour they launch.** Flight time is not modelled, but range is. At 6- and 12-hour turns the difference cannot be seen.
13. **The forces year** can be set apart from the crisis year (`forcesYear`), for calibration and for "what if" starts.
14. **Price noise is dropped,** and the economy reports smooth series.
15. **The decapitation fix:** each hit on a command site on Taiwan lowers its command by 0.06. The source left such hits without effect on Taiwan's command.
16. **Losses written back** are the presidency's choice. `TaiwanState.losses("USA")` has ships, carriers, aircraft and killed; `usExpended` maps munitions onto `data/munitions.csv` ids.

## Interface for the presidency

All calls are deterministic: the same state, orders and random streams give the same JSON.

```java
TaiwanEngine engine = new TaiwanEngine();                       // loads data/taiwan/*.csv; keep one

TaiwanScenario sc = new TaiwanScenario(
        year,               // 2029-2036: the forces are projected to it
        preset,             // 0 base, 1 optimistic, 2 pessimistic
        "US",               // the player's side ("US" in the presidency)
        plan,               // China's plan if the presidency's ladder fixes it, or null for the planner
        deterrence,         // 0-100, TheaterScenario.deterrence (50 neutral)
        munitions,          // munitionsIndex / 100 / engine.data().usAntiShipProjection(year)
        japanBasing,        // TheaterScenario.japanBasing
        warningMonths,      // US programmes that far along; indicators already showing
        TaiwanScenario.Commitment.INTERVENE,   // or ARM_AND_SANCTION, STAND_ASIDE
        defenseCompetence,  // 0-100: the US staff's skill (<40 low, >70 high)
        null);              // custom assumptions, "key=value;..."
TaiwanState s = engine.newCrisis(date, sc, rng);

TaiwanEngine.Outcome o = engine.turn(s, date, rng);              // one turn of s.turnHours() hours
TaiwanEngine.Outcome o = engine.apply(s, date, order, rng);      // one order; IllegalArgumentException with a readable reason
List<TaiwanOrder> pb = engine.playbook(s);                       // what the staff would do this turn (reads only)
TaiwanResult r = engine.result(s);                               // status null until s.over()
Map<String, Object> json = s.toJson();  TaiwanState t = TaiwanState.fromJson(json);
```

- `Outcome(List<Effect> effects, List<News> news, List<String> log)`. Apply the effects as they come. The fallout arrives with the turn that ends the crisis.
- `date` is the game date. Pass `s.date()` to keep the crisis's own calendar.
- Turns: 24 hours in the crisis, then 1, 6 and 12 hours. `GameEngine` would pause the weeks while `!s.over()` and resume after.
- Random streams: `taiwan.setup`, `taiwan.air`, `taiwan.salvo`, `taiwan.sub`, `taiwan.intel`, `taiwan.ground`, `taiwan.amphib`, `taiwan.sea`, `taiwan.world`, `taiwan.diplo`, `taiwan.staff`, `taiwan.ship`, `taiwan.production`.
- The presidency's ladder: `s.chinaRung()` 0 is drills, 1 quarantine, 2 and 3 blockade, 4 and up war.
- `TaiwanResult.status()` feeds `ForeignEngine.taiwanWarOver` (NUCLEAR is new). `usKilled`, `shipsLost`, `carriersLost` and `aircraftLost` match `TheaterOutcome`'s numbers.

**What `TaiwanState` reports** (read-only; each accessor is documented in the class):

| Group | Accessors |
|---|---|
| Clock | `phase()` "crisis", "war" or "over"; `day()` from 1; `hour()` since the start; `hourOfDay()`; `date()`; `turnHours()` the next turn's length; `warHours()`, `warDays()` |
| Ladders | `chinaRung()`, `usRung()` 0 to 6 (`RED_RUNG`, `BLUE_RUNG`); `nuclearAlert(side)` 0 to 3; `nuclearRisk()` 0 to 100; `nuclearCrisis()`; `usFighting()`; `provoked()` |
| China's plan | `plan()` ("" until decided; the US sees it through `planEstimate()` shares); `dDay()`; H-hour options |
| Directives | `mainlandStrikes()`, `fleetPosture()`, `submarineFocus()`, `munitionsPolicy()`, `airPriority()`, `convoys()`, `heldDirectives()`, `commitment()` |
| Forces | `groups()`, `subs()`, `squadrons()`, `batteries()`, `troops()`, `bases()`; `groupsIn(zone, side)`; each unit has its key, name, nation, zone, mission, hub and held flag |
| What the player sees | `contacts(side)`; `contactsIn(zone)` for the player's side; `contactError(c)` in km (it grows 28 km an hour since last seen) |
| Taiwan | `sectors()` (PLA control 0 to 1, lodgements, area in km²), `facilities()`, `territoryKm2()`, `plaAshore()` and `rocReady()` in battalions, `electricity()` and `chips()` as shares of normal, `chipShock()` 0 to 1, `will()` 0 to 100, `command()` 0 to 1, fuel stocks, `rationing()`, `mobilization()` |
| Blockade | `blockadeLevel()` 0 to 4, `throughput()` share of 26 ships a day, merchants sunk and seized, mines laid and cleared, `mines(side, zone)` |
| Munitions and losses | `stock(pool, munition)`, `expended(side, munition)`, `usExpended(data)`; `losses(nation)`: ships, carriers, major combatants, amphibious, submarines, aircraft in the air and on the ground, killed, wounded |
| Allies | `countries()`: stance −100 to 100, level 0 to 6, basing, belligerent, the role asked |
| Warning | `warning(side)` 0 to 1, `warningLevel(side)` (`WARNING`), `indicators(side)`, `planEstimate()` |
| Economy | `gap(economy)` (output gap of `ECONOMIES`), the channels (`channelStrait()`, `channelChips()`, `channelSanctions()`, `channelEnergy()`), `brent()` $ a barrel, `lngPrice()`, `warRiskInsurance()`, `usEquity()`, sanctions in force |
| Politics | `usSupport()` 5 to 95, `approval()` and `congress()` (standalone), `prcStability()`, offers on the table (`offerFrom()`, `offerTerms()`) |
| Outcome | `outcomeCode()` 0 to 9 or −1, `outcome()`, `endReason()`, `score(side)` |
| Record | `log()` (last 300 lines), `sitrep()` (the last turn), `history()` (a row a day) |

## In the presidency, as built (13 November 2026)

`GameEngine` drives the engine. `mandate-web`'s `TaiwanViews` and `presidency/taiwan.ts` draw it. Tests: `TaiwanInGameTest`, and the war cases in `RequiredBehaviourTest`.

**How it starts:**
- **China escalates to a quarantine or a blockade.** The world's Taiwan stage rises to 3 or 4 (milestone 8's `ForeignEngine.theaters`). The crisis then opens at the end of that week, with China's plan left to its planner, which may stand down.
- **China attacks without a crisis first.** The stage reaches 5, or the old monthly ladder's war event is answered. The crisis opens with the invasion plan fixed. The old war event's three answers become the commitment.
- **From the start screen:**
  - **Individual Crisis** begins the crisis at once in the sample presidency of September 2029, with a preset (`Command.IndividualCrisis`; docs/MODES.md). The game ends when the crisis does, with an after-action report.
  - **The moment "A Taiwan crisis in the Full Game"** opens a Full Game in which the crisis has just begun.
  - `Command.TaiwanBegin(preset)` begins one in a Full Game without ending it.
- **No new crisis** while another (Hormuz or Taiwan) is under way, within a year of the last Taiwan crisis, or once Taiwan has fallen.

**The scenario comes from the presidency:**
- the year (clamped to 2029–2036);
- the world's deterrence (`ForeignState.deter`);
- the anti-ship munitions index;
- Japan's basing and the months of warning (the diplomacy state);
- the Defense secretary's competence;
- the commitment, INTERVENE unless the old war event set it.

**The clock:**
- Crisis days run on the DAILY clock. The war runs on the OPERATIONAL clock, in turns of 1, 6 and 12 hours.
- The presidency's weeks pause. Every seven days of crisis the economy, the Fed and the reports, and opinion take their week, as in the Hormuz crisis.
- Brent above its pre-crisis $92 goes into the structural economy's oil price. The war adds market stress.

**The effects** are dispatched as they come (`Outcome.effects`), each under the cause "The Taiwan Strait". This covers the deaths (`WorldShift("kia")`), the rally, approval, tension, relations, and at the end the fallout.

**When it ends:**
- **The record.** A war is recorded as a `TheaterOutcome`:
  - DEFENDED or DETERRED → DEFENDED;
  - STALEMATE → STALEMATE;
  - FALLEN or NUCLEAR → FALLEN.

  Legacy, the verdict and the 2032 environment read it.
- **The stockpile and the force.** `MilitaryEngine.recordTaiwanWar` subtracts:
  - the American munitions fired (`usExpended`, by `munitions.csv` ids);
  - carriers, other ships (as SURFACE) and aircraft (half FIGHTER, then CARRIER_AIR);
  - the dead (`MilitaryState.killed`).
- **The world.** `ForeignEngine.taiwanWarOver` sets the Strait's stage and relations:
  - DETERRED lowers the stage to 1 and eases the tension;
  - NUCLEAR is a new case, with grave losses of standing.
- **The weeks resume** on the next Monday.

**The crisis room:**
- the two ladders, America's clickable up to the commitment's cap, with a confirmation from rung 2;
- the Western Pacific by sea zone (the waters around Taiwan as one marker), with your forces and the enemy as your intelligence sees them;
- Taiwan by sector, with the waters around it zone by zone;
- warning and intelligence: the five levels, China's likely plan, the indicators with their confidence;
- the theatre directives: mainland strikes, the carriers, submarines, air priority, munitions, convoys, a pre-emptive strike, and handing everything back to the staff;
- requests to Taipei;
- the allies, with the role asked of each, and diplomacy;
- the preparations;
- the economy and sanctions, the heavier ones confirmed;
- nuclear risk, posture and the ceasefire terms, with the other side's offers to answer;
- the toll by nation and the log.

**Island Chain on its own:**
- **Start:** from the start screen, a chosen year (2029–2036), America or China, and a preset.
- **Engine:** it runs in the web engine as a separate session (`op: islandChain`), on 1 March of the year. Both sides' politics are the source's own.
- **As China:** the room offers the course of action (the plan, the H-hour options, the landing region, the blockade's lethality), D-day, preparations, deception and standing down.

**Measured** (whole autoplayed games, seeds 1–12): 8 reached office, and 4 of those had a Taiwan crisis:
- three ended with "Deterrence holds" after about 70 days;
- one ended in a "Partial PRC success" after 124 days, with 9,210 Americans killed.

A crisis adds 70–230 turns to a game.
