# Source audit

What we actually inherited, as verified on 2026-11-06. Every later design decision cites this file.

## 1. Snapshots

The three source repositories were cloned read-only. Nothing was modified, pushed or forked.

| Repository | Branch | Commit SHA | Commit date | Commits | Source size |
|---|---|---|---|---|---|
| lukewaadams-gif/Battleground2028 | main | `b678c752db24a711b78b821af603302b26beeeba` | 2026-11-05 | 15 | 1 file, 19,572 lines, 1.41 MB |
| lukewaadams-gif/ResoluteDesk | main | `3fdbc7aa7c2895f9bb8d9d5c02955774e62eaffd` | 2026-11-05 | 6 | 1 file, 16,000 lines, 1.23 MB |
| lukewaadams-gif/IslandChain | main | `5e0eeb73e7a77363cbda0509a5cbd74dee280b6f` | 2026-10-29 | 1 | 1 file, 12,450 lines, 0.93 MB |

To reproduce:

```bash
git clone https://github.com/lukewaadams-gif/Battleground2028 && git -C Battleground2028 checkout b678c752db24a711b78b821af603302b26beeeba
```

The two other repositories are reproduced the same way.

Each repository is one Java source file. The file is a single top-level class that extends `JPanel` and contains roughly 95 to 225 nested static classes. Each repository also has a README and screenshots. Battleground 2028 and Resolute Desk also ship a prebuilt `.jar` and a browser `index.html`.

## 2. Build and run verification (OpenJDK 21.0.12)

| Check | Battleground 2028 | Resolute Desk | IslandChain |
|---|---|---|---|
| `javac -encoding UTF-8` | OK, 3.0 s, 105 classes | OK, 2.0 s, 98 classes | OK, 1.8 s, 224 classes |
| Headless test entry | `java Battleground2028 test calib\|govern\|batch\|game` | `java ResoluteDesk test sim\|check\|whip\|impeach` | `java IslandChain test <runs> <plan> <preset>` |
| Headless run | OK | OK, 4-year sims in about 1 s each | OK, about 3 s per war |
| Automated assertions | **None.** Tests are diagnostic printouts | **None** | **None** |

The GUIs were not exercised interactively in this audit. The screenshots in each repository show they work.

### Measured behaviour (headless)

**Battleground 2028**
- Calibration reproduces the 2024 two-party vote: D 49.0, actual 49.2.
- **Balance.** 40 of 40 general-election batch games went to the Democrat, with the same Shapiro-Vance pairing in all 40. An independent re-run of 30 baseline games also went 30-0 for the Democrat, and 21 of those 30 ended on exactly 310 electoral votes. `test govern` produces 319-219 for every seed.
- Full campaigns with AI-only play are more varied: the D-R split was 3-3 in our 6 runs, with six different nominees.
- **Cause.** Polling error moves only the polls, not the result. The final outcome carries only a small late swing (SD 1.0, plus 0.6-1.2 per state). The forecast, meanwhile, assumes an SD of 2.0 or more. The forecast is more uncertain than the game it forecasts.

**Resolute Desk**
- Approval stays within about 45-50 for four years on autopilot.
- With an empty calendar, approval ends only about 4 points lower than on autopilot.
- A severe forced recession raises unemployment by only 0.4-0.8 points.
- In 1961-era runs, oil collapses to $1 because oil effects are in 2025 dollars.

**IslandChain**
- The 5 default AI-vs-AI runs were identical: quarantine, then a ceasefire after 75 days. The cause is the test harness, not the design. Adjacent `java.util.Random` seeds give nearly the same first draw, and that first draw picks the plan.
- With well-spread seeds over 24 runs, invasion is chosen once. Deterrence postpones and downgrades most invasions.
- Forced invasions are decided almost entirely by landing region: the north was defended 10 of 10 times, and the south fell 12 of 12 times.
- US GDP damage is about half the Bloomberg estimate the game cites.

## 3. Licensing and redistribution

| Item | Finding | Consequence |
|---|---|---|
| Source code licence | **None of the three repositories has a LICENSE file or licence headers.** By default that means all rights reserved by the author. | The owner (the account holder, who we assume is this project's owner) may reuse the code. Outside contributors and any public fork have no rights. **Action:** the owner should confirm authorship of all three codebases in writing, then choose a licence for the new repository. It is proprietary for now. |
| Third-party code | No dependencies; everything is hand-written. Battleground 2028's `index.html` is machine-translated Java plus a hand-written runtime. **The translator is not in any repository.** | Low code-licence risk. The browser build cannot be regenerated. |
| us-atlas / world-atlas geometry | ISC licence, from Census Bureau data | OK with attribution |
| Natural Earth (IslandChain) | Public domain | OK |
| tonmcg county results (BG) | Scraped from news sites; licence unverified | **Replace** with MIT Election Lab (MEDSL) data, which is CC0/CC-BY, before commercial release |
| Nielsen DMA definitions and AdImpact cost figures (BG) | Nielsen DMA boundaries and names are proprietary | **Replace** with our own "media region" abstraction or a licensed source |
| Pew, AP VoteCast, exit-poll toplines | Facts and statistics; low risk when used as calibration targets | Cite in credits |
| RealClearPolling / Silver Bulletin numbers (BG) | Facts, used for calibration | Low risk. Do not reproduce their branding. |
| Real podcast names (28), news outlets (18) and Senate candidates (BG/RD) | Trademarks or names used to imply endorsement | **Replace** with fictional outlets in the commercial build |
| Real politicians (39 in BG, about 90 in RD) with numeric ratings | BG generates **invented scandals naming real people** (harassment allegations, hospitalisation). RD can impeach a real sitting president. | **Highest legal risk:** false light, defamation and right of publicity. The commercial build uses a fictional cast (RISK_REGISTER R-02). |
| Real US Navy hulls, units and weapons (IC) | Facts. Low IP risk; content sensitivity instead | Keep behind a data file. Generic names are an option. |

## 4. Implemented versus advertised

Short form below. The full classification is in `FEATURE_PRIORITY.md`.

- **Battleground 2028.** The campaign core is real and the strongest code of the three. That includes the 96-cell electorate, field chain, ad markets, money, primaries, debates and ballot access.
  - The version-3 additions are mostly thin, usually a single dice roll each: press, staff, ad studio, contested convention, county election night, midterms, re-election and Govern 2029-32.
  - Staff and ad-studio multipliers apply only to the human player.
  - Govern mode has 28 fixed bills, one vote per quarter, executive orders that always succeed, and no carry-over of campaign promises.
- **Resolute Desk.** Nearly every advertised feature exists. Most are dials that drift toward a target, plus option cards resolved by one roll.
  - The Congress bloc/spatial vote model is the deepest system and is worth porting.
  - The 96-cell approval breakdown is display-only, because national approval is computed top-down.
  - The economy is over-damped. Interest costs cancel out of the deficit, and fiscal impulses never decay.
  - The Taiwan war is one card with odds of 0.55, and those odds ignore deterrence and posture.
  - About 2,700 lines are copied verbatim from Battleground 2028.
- **IslandChain.** A real operational wargame with stochastic salvo combat, amphibious and ground models, munitions, blockade, economy and escalation.
  - The simulation is mostly separable from Swing. `test()` already runs it headless.
  - Two static fields leak state between games (`Econ.chipLag`, `Taiwan.lastKilled`).
  - The "US mobilises late" setting has no effect.
  - AI-vs-AI never negotiates.
  - Non-invasion wars always end at a 75-day hard stop.

## 5. Architectural findings that drive the new design

1. **Monoliths with global state.** All three games keep mutable `static` state: calendars, caches, overwritten "final" arrays and UI labels. Two games cannot coexist in one JVM, and saves miss state. **Decision:** the new core has no mutable statics, and every value lives in `Game`.
2. **Java serialization saves.** These break on any field change. Resolute Desk imports Battleground 2028 by parsing its raw serialization stream by field name. **Decision:** saves are setup plus command log, with versioned JSON snapshots for fast loading.
3. **A single shared RNG.** It couples every system, and the IslandChain seed correlation shows the hazard. **Decision:** named, independent RNG streams (now `DeterministicRandomManager`).
4. **Duplicated models.** There are two copies of the electorate and three separate election formulas (BG general, BG re-election, RD re-election). **Decision:** one `ElectorateModel` serves president, Senate, House and approval-by-state.
5. **No automated tests.** **Decision:** determinism, rules and balance gates run in CI from day one. The foundation ships 18 tests.
6. **Simulation and UI are interleaved, with audio called from the simulation.** **Decision:** the `mandate-core` module may not import AWT, Swing or JavaFX, and a test enforces it.

## 6. Ported: Battleground 2028 cell electorate

**Export** (from the repository root, OpenJDK 21; BG at commit `b678c752db24a711b78b821af603302b26beeeba`, read-only):

```bash
javac -encoding UTF-8 -nowarn -d /tmp/bg-classes /path/to/Battleground2028/Battleground2028.java
java -Djava.awt.headless=true tools/BgExport.java /tmp/bg-classes mandate-core/src/main/resources/data b678c752db24a711b78b821af603302b26beeeba
```

`tools/BgExport.java` loads the compiled classes, calls `Elect.init()` and reads the static arrays by reflection. It writes two files:

- `data/electorate_cells.csv`: 4,896 rows (51 states × 16 blocs × 3 ages × 2 sexes). Each row has the eligible voters under the 2028 and 2024 composition (`cvap`, `cvap24`), base turnout (`turnB`), the calibrated 2024 Democratic logit (`baseL`), elasticity (`elast`) and `swing2026(c)`, with BG's own bloc and age names.
- `data/candidates_bg.csv`: BG's 39 real Democrats (25) and Republicans (14), in the `candidates.csv` format with `bg_` ids.
  - Ratings are mapped by `round(1 + 9 × v / 100)`: charisma from Charisma, debate from Debating, fundraising from max(small-dollar, big-donor), policy from Intellect & policy depth, and discipline from Message discipline.
  - Ideology is the mean of BG's econ and cult positions.
  - The file is opt-in and nothing loads it. It is not for commercial release (R-02).

**What is ported.** Only the calibrated electorate, as `CellElectorateModel` behind `ElectorateModel`.

- A cell's logit is `baseLogit + (swing2026 − its national turnout-weighted mean) + elasticity × (nationalSwing + localShift) × k`.
- A state's margin is its cells' result, weighted by cvap × turnout.
- `k` is derived from the data so that +1 point of national swing gives +1 point of national margin.
- The model also gives per-state margins and national crosstabs by bloc, age and sex.
- Nothing in the game uses it yet: `UniformSwingModel` is still the default.

**What is not ported.** BG's campaign coupling stays behind:

- the per-cell issue ideals and salience, and the issue-distance vote term;
- group images, candidate group appeals and attack lines;
- the overlay fractions (rural, union, gun owner and so on). Their partisan lean is baked into `baseLogit`, but they are not exported as traits;
- ad markets and DMAs (dropped under R-07), and the TV, digital, visit and field terms;
- third-party shares, the primary electorate and BG's polling model.

Campaign effects reach the cell model only through `localShift`. BG calibrates each state to its own blend of the 2024, 2020, 2022 and 2026 results, not to 2024 alone. The 2024 configuration reproduces the 2024 map, but not every 2024 state margin exactly.

**Measured calibration** (`CellElectorateTest`):

| Configuration | National two-party D | National margin | Electoral College |
|---|---|---|---|
| BG `test calib` (2024 composition, no 2026 swing) | 49.0 (actual 49.2) | | |
| Cell model, 2024 composition, no 2026 swing | 0.4895 | R+2.10 | R 312, D 226 (the 2024 map) |
| Cell model, 2028 composition, recentred 2026 swing (game default) | 0.4953 | R+0.93 | R 293, D 245; tipping point WI |

- Moving to the 2028 electorate shifts the national margin by +1.16 points, which is the composition change. Recentring the 2026 swing subtracts 0.048 logit and moves the nation by only 0.10 points.
- Pennsylvania sits on a knife-edge at zero swing (D+0.04). It is the state that moves the map from 2024's 312 to 293.
- Without recentring, BG's own generic 2028 baseline is D+1.0.
- The logit per point is k = 0.0262, against BG's own `env / 50` = 0.020.
- Default crosstabs: Black D+71; white evangelicals R+72 (no degree) and R+53 (degree); women D+9; men R+12; 18-29 D+9.

## 7. Upstream changes after the audit (checked 2026-11-12)

| Repository | Audited | Upstream HEAD | Change |
|---|---|---|---|
| Battleground2028 | `b678c752` | `b678c752` | none |
| IslandChain | `5e0eeb73` | `5e0eeb73` | none |
| ResoluteDesk | `3fdbc7af` | `2ce2b372` | Seven "Version 2" commits: the code is reorganised into a multi-file project (216 Java files, about 27,900 lines, up from one file of 16,000). Stages cover the transition, approval and relationships; policies and their real-world effects; foreign policy and wars of choice; news and runaway storylines; watch-mode priorities and the 2029 setup; and new art. |

Everything ported so far, including the vertical slice's Resolute Desk mechanics, references the audited `3fdbc7a`. Version 2 has **not** been audited; that is backlog item B-54. The audited clones are unchanged.

