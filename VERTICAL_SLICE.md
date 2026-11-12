# Vertical slice: the 2028 election and the first hundred days

The first complete playable slice of the game. It is one continuous session: choose a candidate, run the 2028 general election against an AI opponent, win or lose on election night, set an agenda in the transition, take the oath, and govern fourteen weekly turns to day 100, which ends with a graded report and a replay. It is one executable, one world state and one save file. Nothing is transferred by hand, and no other program is launched.

```bash
mvn -B install            # builds and runs the 93 tests, and writes mandate-cli/target/mandate.jar
java -jar mandate-cli/target/mandate.jar
```

`./mandate` does the same and builds first if needed. The game autosaves to `~/.mandate/save.json`, or to `$MANDATE_HOME/save.json` if that is set. `--seed N` fixes the world; `--save FILE` picks another save file.

**Not claimed:** that the slice is fun. Everything below comes from code, tests and scripted probes, not from players. The playtest plan in `GAMEPLAY_DESIGN.md` §7 still applies.

---

## 1. The arc

| Phase | Turns | What the player decides | Where the mechanics come from | What carries forward |
|---|---|---|---|---|
| Setup | once | Party; a fictional candidate, a point-buy custom candidate, or the opt-in real roster; the opponent; the national mood; difficulty; coaching on or off | BG candidates and ratings; BG roster exported by `tools/BgExport.java` | Candidate ratings (charisma for rallies and addresses, debate, fundraising, policy, discipline) |
| 2028 campaign | 16 weekly turns, 17 July to 7 November | Standing orders (strategy, spending rate, message) or a hand-built week; up to three promises; debate postures; events | BG field chain, ads, visits, money, message salience and consistency, debates, events; the 96-cell electorate | Promises become the agenda; the result fixes the mandate and the Congress |
| Election night | once | — (a post-mortem if lost) | BG cell electorate and correlated error | The winner, electoral votes, the House and the Senate class |
| Transition | once | The agenda, up to three bills; the first is the **flagship** | F agenda; promises | The flagship bill; promises left off cost the base |
| Inauguration | once | — | F `PresidentInaugurated` | Capital and swing approval scaled by electoral votes; an empty cabinet |
| First hundred days | 14 weekly turns, 22 January to 30 April 2029 | 3 attention points a week; decisions as they arrive (see §4) | RD bill path, whip blocs, concessions, cabinet confirmation, opposition strategy, factions, Fed, crises; F engines for the economy and opinion | Everything lives in the world state: a full-term game continues into monthly governing |
| Day 100 | once | Replay from the inauguration, replay the campaign with the same seed, or go back to the menu | New | The report is fixed into the save |

A **full-term** game (a menu option) plays the same slice, fills or votes on any cabinet posts still open, then goes on to monthly governing, the 2030 midterms and re-election in 2032. That is the foundation's loop, now behind the hundred days.

## 2. The election phase

- **Candidates.**
  - **Fictional cast:** 8 candidates, 4 per party. This is the default and the only option for a commercial build.
  - **Custom candidate:** 33 points spread over five ratings, each from 2 to 9 (about the fictional cast's average). Ideology sits on the party's side: progressive or conservative, mainstream, or moderate.
  - **Real roster:** Battleground 2028's 39 real politicians, exported from BG's own data (`data/candidates_bg.csv`). It is opt-in and stored in the setup, so saves carry it.
- **Real people and RISK_REGISTER R-02.** If the player's candidate is real, the two personal-scandal events (the hot-mic gaffe and the "business dealings" October surprise) never fire. Campaign-level stand-ins replace them: a leaked strategy memo and a dossier on campaign consultants. `CampaignLayerTest` checks both halves.
- **Strategy, streamlined.** Standing orders are a preset, a spending rate and a message. The staff turns them into a weekly plan using the same planner the opponent uses.
  - Presets: Battleground blitz, Expand the map, Ground game, Air war, Bank it.
  - Spending: careful (15% of the war chest a week), steady (30%) or all in (60%).
  - Message: a fixed issue, or the staff picks the most favourable one each week.
  - The player can keep the same orders for the whole campaign.
- **Strategy, deep.** "Plan this week by hand" sets rally stops, the money, the split between ads and field, and the message for one week. The source game's full plan (`Command.PlanCampaign`: stops, ads and field state by state) remains in the API.
- **Resources and messaging.** These use BG's model as ported in the foundation:
  - cash, income and fundraisers;
  - ad pressure that fades within weeks, field organisation that lasts, and candidate visits;
  - diminishing returns relative to the opponent's effort in each state;
  - message salience, and a growing reward for staying on message.
- **The opponent.** `CampaignAi.adapt` reads the same polls the player sees, never the truth. It also reads its own cash and the calendar.
  - It picks a preset, and its reasoning is shown every week ("Behind with weeks to go, it floods the air with ads"). It answers the player's offensives in close states, and makes its own promises.
  - **Difficulty** sets the opponent's money and whether it adapts. On Story it runs one fixed, predictable campaign.
- **Polls.** One poll is recorded each week: the national margin, the electoral-vote projection, the tipping-point state and a win probability.
  - **What moved the race** lists the week's largest changes: each side's momentum and message; news, debates and promises; the national environment; and ads, field and visits in the close states.
  - Crosstabs show the margin by BG's 16 voter blocs, by age and by sex.
  - Polls carry a hidden systematic miss, so the player sees the truth minus the miss, plus noise.
  - The forecast is labelled "polls only". It integrates the polls' historical error analytically, without random draws.
- **The election.** The model is BG's calibrated 96-cell electorate (§6 of `docs/SOURCE_AUDIT.md`), not a substitute: 51 states × 16 blocs × 3 ages × 2 sexes, with 2028 composition and the recentred 2026 group swing.
  - The result adds correlated national and state error.
  - Senate races now go through the same model.
  - `election.cellElectorate=0` switches back to uniform swing.
- **Promises.** Up to three bills, each worth a little swing (its public appeal) and a little salience for its issue. They pre-fill the agenda, and each one left off costs the base wing 6 points of mood at the inauguration.

## 3. The inauguration: what transfers

Nothing is transferred by hand, because the whole game is one `WorldState`. At the inauguration, `GameEngine.beginTerm`:
1. dispatches `PresidentInaugurated` with the winner and their electoral votes. Political capital and swing-voter approval scale with the mandate (tested in `RequiredBehaviourTest`).
2. keeps the Congress elected with the president: the same `CongressElected` transition from election night.
3. sets the opposition's opening strategy and the honeymoon from that Congress.
4. applies the promises-versus-agenda check.
5. creates the hundred days' process state over the same economy, budget, politics, diplomacy, Congress and government states the campaign used.

The national conditions (economy, deterrence, tensions) are the world's own; nothing is reset.

## 4. The first hundred days

The weekly loop is the prototype's core loop: Decision → Consequences → New information → Political reaction → Revised strategy. It now runs against the authoritative engines through a `Host` (`opening/HundredDays.java`).

| Requirement | In the slice |
|---|---|
| Appoint key administration officials | Treasury, Defense and State. Each nominee is a loyalist (competence 55), an expert (85) or a crossover from the other party (72). Hearings come the next week (a controversial pick can stumble) and a Senate vote the week after; the vote uses the shared `VoteModel`. A rejected nominee raises a decision. Competence drives markets (Treasury), crisis odds (Defense) and intelligence and diplomacy (State). |
| Set legislative priorities | The transition agenda. The first priority is the flagship, and the rest wait for monthly governing in a full term. |
| Negotiate one consequential bill | The flagship is a catalog bill, resized (small, medium, large) and paid for by borrowing, revenue or cuts. That moves its ideology and deficit. It goes through committee, the House floor and the Senate floor (60 votes, or 51 through reconciliation). The whip count is bloc by bloc, with what each bloc wants; concessions are projects, appointments or primary threats; the player can also go public; and the leader will not call a vote below 35% unless the player insists. |
| Respond to an economic challenge | Week 5: an inflation surge or a hiring slump (seeded). There are four responses, each trading prices, jobs, the deficit and the wings. Previews give the size of each effect. The shocks are front-loaded, so they bite before day 100. |
| Make a foreign-policy decision | A crisis in the Taiwan Strait or the Strait of Hormuz, arriving in weeks 3 to 5, with a hidden adversary resolve and a noisy intelligence estimate (sharper with an expert at State). There are four moves, and force works against a weak-willed rival but provokes a determined one. The crisis can escalate to a clash with a three-option aftermath. The outcome carries into the world's standing tension. |
| Changes in approval, Congress and opinion | Weekly opinion step for three coalitions (your voters, swing, opposition voters); two wings of your party with revolts below 32; an opposition that picks cooperate, selective or obstruct from your approval and goodwill. Also a stopgap funding deadline (week 8) that can end in a shutdown, two FOMC meetings, and jobs and CPI releases. |

**No manual scheduling.** Each week has 3 attention points and about ten actions. Decisions arrive as cards with previews. Any domain (legislation, cabinet, crisis, budget) can be handed to the chief of staff: no attention spent, but the staff works at half strength.

## 5. Design requirements

| Requirement | How it is met |
|---|---|
| Streamlined campaign, optional deep management | Standing orders run the campaign; a hand-built week is one menu away; the full per-state plan remains in the command API |
| Explain the consequences of important decisions | Every action in the hundred days prints its consequence at once. A weekly briefing gives consequences, political reactions and intelligence. In the campaign, "what moved the race" decomposes each week's polling change. Decision previews state the trade-offs, and the ledger records every transition with its cause. |
| Clear victory, failure and partial success | `DayHundredReport`: five goals (legislation, administration, economy, security, standing), each met, partly met or missed, with a one-line reason. **Victory** is 9 or more of 10 with the flagship signed; **failure** is 4 or fewer; **partial success** is anything between. The rule is printed on the report. A lost election gets a post-mortem instead. |
| A final 100-day report | The report adds the weekly approval trend and the decisions that shaped the hundred days. It is fixed into the save on day 100. |
| A replay with a different strategy | From the end menu: govern again from the inauguration (`replayFromInauguration`: the same election, Congress and mandate), or replay the campaign with the same seed (`restart`: the same opponent, polling miss and world) |
| Repetitive scheduling must not dominate | Attention points instead of calendar slots (C-01); standing orders instead of weekly plans; delegation; and every prompt has a sensible default on Enter, chosen so that taking it always makes progress |
| A tutorial that teaches through decisions | `tutorial.Coach` gives one tip at a time, at the decision it is about, using the game's own numbers, with something to try ("You have $140M in the bank and 3 weeks left. Money left on Election Day buys nothing. Try: change your standing orders to spend all in."). It never changes the game (tested). It can be turned off, and its progress lives in the save. |
| The original deeper mechanics stay accessible | The cell electorate exposes every cell; the uniform-swing model is a tuning switch. The monthly governing loop (bills by route, addresses, deterrence, diplomacy, events, midterms, the Taiwan ladder and the operational war) continues in full-term games. The 12-week prototype and its probes remain (`mandate weeks`, `mandate compare`). All constants are in `tuning.properties`. |

## 6. Technical requirements

| Requirement | How it is met |
|---|---|
| One executable application | `mandate-cli/target/mandate.jar` (maven-shade). `java -jar` starts the game; designer tools are subcommands. |
| One unified save file | `SaveManager` version 2: setup, world snapshot (including the hundred days and the report), random state and command log. The front end's tutorial progress goes in a `ui` section of the same file. Saves are written atomically after every turn. |
| No manual state transfer | One `WorldState` from the first campaign week to day 100. The hundred days own only their process state (`HundredDaysState`). |
| No external program launches | None; the source games are not invoked. Their data is exported once, at build time, by `tools/BgExport.java`. |
| Working AI opposition | The adaptive campaign opponent (§2); the opposition party in Congress (strategy, committee chairs, votes); the crisis adversary |
| Deterministic regression tests | `GoldenRegressionTest` fingerprints 6 full games, 11 slice games (each ending: loss, partial success, victory; custom and real-roster candidates) and 7 prototype probes, and checks no state leaks between games. Save and replay consistency is tested mid-campaign and mid-hundred-days. |

**Tests (93):**
- `HundredDaysInGameTest` (5): the slice end to end, refused actions leave no trace, the view routes through the command log, a mid-hundred-days save reproduces the future, full-term continuation.
- `CampaignLayerTest` (8): custom candidate and save, the real roster and its events, promises, broken promises, standing orders, the opponent adapts, a credible opponent and campaign choices matter, the polls explain and the forecast discriminates.
- `OutcomesTest` (5): the report follows its rule and strategies reach all three outcomes, the report is fixed in full-term games, post-mortems, replays, and the coach.
- `GameShellTest` (4, in `mandate-cli`): scripted keyboard input through the real shell from the campaign to the day-100 report, Continue, the custom candidate with orders, a promise and a hand-built week, and governing again.
- The 63 earlier tests, plus `CellElectorateTest` (8) from the electorate port. The earlier tests were adjusted only where the slice changed behaviour (§8).

## 7. Evidence from probes (not from players)

All figures come from autopilot or scripted probes on the toss-up scenario at Standard difficulty, unless stated.

- **2028 balance**, 1,000 autopilot games: Democrats win 51%. By scenario:
  - The 2026 wave carries on: Democrats win 90%.
  - The wave recedes: Democrats win 20%.
- **The opponent beats the default campaign.** The autopilot player runs the default orders and wins 39% (D) and 38% (R) against the adaptive opponent.
- **Difficulty**, 300 seeds: the player wins 115 games on Story, 101 on Standard and 95 on Hard.
- **Campaign orders matter**, 300 seeds:
  - an all-in ground game: 122 wins;
  - an all-in air war: 66;
  - the default orders: 101.
- **Polls.** The final poll misses the result by an SD of 3.2 points, matching the forecast's assumption.
- **Forecast calibration** is close in lopsided scenarios. In toss-ups it runs high, because a polls-only forecast cannot know the opponent is the stronger campaigner.
- **The hundred days**: 150 won elections, each governed by every strategy probe.

  | Probe | Victory / partial / failure | Mean score | Goals met: legislation, administration, economy, security, standing |
  |---|---|---|---|
  | STRATEGIST (reads the information) | 43 / 57 / 0% | 8.2 | 81, 100, 17, 53, 82% |
  | DEALMAKER | 55 / 45 / 0% | 8.4 | 88, 100, 14, 60, 84% |
  | TECHNOCRAT | 40 / 60 / 0% | 8.1 | 79, 100, 30, 35, 79% |
  | DELEGATOR (the chief of staff runs everything) | 39 / 60 / 1% | 8.0 | 81, 100, 29, 40, 73% |
  | PARTISAN | 27 / 55 / 18% | 6.8 | 73, 63, 36, 65, 51% |
  | HAWK | 15 / 77 / 7% | 6.8 | 0 (a small bill), 100, 29, 84, 30% |
  | IDLE | 0 / 16 / 84% | 2.8 | 0, 0, 29, 40, 3% |

  Each philosophy leads on something different: HAWK on security, DEALMAKER on standing, PARTISAN on the economy goal (its answer to a slump is stimulus). Doing nothing never wins.
- **Performance**: 1,000 full-term autopilot games take about 12 seconds; a full build with the 93 tests takes about 20 seconds.

## 8. Changes made for the slice, and why

| Change | Why |
|---|---|
| The prototype's logic now runs on a `Host` over the domain states. `OpeningTerm` is a thin standalone facade. | One implementation serves the game and the probes. `BillSize` became factors on a catalog bill, as exact fractions, so the prototype's numbers did not move. |
| Cabinet seats moved to `GovernmentState`, with a nomination pipeline in `ExecutiveEngine`; bloc relationships moved to `CongressState` | Ownership: the hundred days own no domain variables |
| **Fix:** the hidden polling miss was subtracted from the polls *and* added to the result | Polls were wrong by twice the miss (error SD 4.3 against the 3.0 assumed). The miss now lives only in the polls. |
| The election now uses the cell electorate, with scenario environments shifted by −0.5 | The 2028 composition is about a point bluer than 2024. The shift restores the toss-up to 50%. |
| Ad and field effects ×1.5 (`campaign.effect.ads` 2.0, `field` 1.5); Story opponents do not adapt | Difficulty had no measurable effect (104 / 103 / 102 wins of 300 on Story, Standard and Hard) |
| The economic challenge is front-loaded, judged by what it threatened, and the responses were sharpened | At first the challenge barely moved the economy before day 100, and the do-nothing probe "met" the goal |
| The crisis outcome changes world tension; full-term games catch up the flashpoints' drift for the months the hundred days covered | Continuity: the world does not stand still. Taiwan crises in full terms fell from 137 to about 80 of 173, because the hundred days usually raise deterrence. |
| The mandate test now measures at the inauguration | The mandate is set there; by the start of monthly governing it has been played through for 100 days |

## 9. Known limitations and open problems

- **Hoarding money is not punished enough.** "Bank it, careful" wins about as often as the default (107 against 101 of 300), and an air war badly trails a ground game. That is because field lasts about 16 times longer than ads and returns are logarithmic. It needs a campaign-economy pass, with targets for what money should buy.
- **Difficulty is modest.** There are 20 wins between Story and Hard, out of 300.
- **Full-term balance drifted.** Re-election is about 71% for each party, above the 50-70% gate; the EV standard deviation is 33, below the gate of 45 or more.
- **The hundred days pause the event catalogue.** Random events do not fire in that phase; its own crisis, shock and deadline are the events.
- **The hundred days' constants** (the shocks, response sizes and goal thresholds) are still in code, as in the prototype, rather than in `tuning.properties`. They should move there before more balancing.
- **The interface is text only.** The desktop UI (M4) will drive the same core.
- **Military integration was not extended**, as asked. A clash in the hundred days is resolved at the presidential level. The operational war is reachable only in full-term games, through the foundation's Taiwan ladder.
- **The real roster is opt-in and for private use.** It is not part of a commercial build (R-02). Its ratings are BG's and run higher than the fictional cast's.
- **ResoluteDesk moved upstream after the audit**, to `2ce2b37` ("Version 2", seven commits, 216 Java files, about 27,900 lines). The ports here reference the audited `3fdbc7a`. The new version has not been audited.
- **No player evidence yet.** The next step is the playtest plan: five first-time players on the slice, then the questions in `GAMEPLAY_DESIGN.md` §7.

## 10. Where the code is

| Area | Files |
|---|---|
| The hundred days | `opening/HundredDays.java` (logic), `HundredDaysState.java` (saved state), `Host.java`, `DayHundredReport.java`, `ChiefOfStaff.java`, `Strategy.java` (probes), `StandaloneHost.java` and `OpeningTerm.java` (prototype) |
| The phase in the engine | `engine/GameEngine.java`: `beginTerm`, `act`, `endHundredDays`, `EngineHost`, `hundredDays()`, `replayFromInauguration()`, `restart()`, `postMortem()` |
| Campaign | `campaign/CampaignStrategy.java`, `PollPoint.java`, `ElectionEngine.java` (polls, forecast, movers, promises, post-mortem), `Candidate.java`, `CandidateCatalog.java`; `ai/CampaignAi.java` (planner and adaptive opponent) |
| Electorate | `electorate/CellElectorateModel.java`, `data/electorate_cells.csv`, `tools/BgExport.java` |
| Tutorial | `tutorial/Coach.java` |
| Front end | `mandate-cli/.../GameShell.java`, `Console.java`, `Main.java` |
| Saves | `engine/SaveManager.java` (version 2 with a `ui` section), `CommandCodec.java` (`Act`, `Pledge`, `SetCampaignOrders`) |
