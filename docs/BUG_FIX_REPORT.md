# Bug fix report

Each entry gives:
- the severity;
- how it was found: played, an external review, or this audit;
- how to reproduce it;
- the root cause;
- the fix;
- the regression test;
- what remains.

Started 13 November 2026, from commit `09aab1c`.

## Baseline (13 November 2026, before these fixes)

- **Core:** `mvn -B -o install` passes 227 of 227 core tests at `09aab1c`.
- **Other modules:** the last full build of all modules, at `7f0bbfa` (with no changes to those modules since), passed 18 desktop-interface tests and 4 command-line tests.
- **Browser:** the twelve browser suites passed on the preview build of `09aab1c`.

## U-02: the Election Day softlock (played; critical): fixed

**Reproduction:**
- **Setup:** the preview, any campaign. The test used the "campaign, week 9" moment and ran the campaign through the war room's own button (`mandate-web/src/test/browser/election.js`, earlier `campaign-to-election.js`).
- **What happens:** on Monday, November 6, "Run the day" holds the election in the engine. The page then stays on November 6.
- **Afterwards:** the button does nothing, and election night never appears.
- **Console:** `TypeError: Cannot read properties of undefined (reading 'daily')`.

**Root cause:** once the vote is held, the campaign view no longer carries the campaign's turn, staff, schedule or money.
- The war room redrew anyway, from that view.
- Its schedule tab read `turn.daily`, and the redraw threw.
- Election night was opened only at the end of a successful redraw, so it never opened.
- Every later click returned early, because the phase was no longer a campaign phase.

The same path hit the 2032 re-election, in both outcomes.

**Fix:**
- **Engine.**
  - `WorldState.electionNightSeen` records, in the save, the last presidential election night the player has watched.
  - `Command.ElectionNightSeen(year)` records that it has been watched; it has no effect on the world.
  - `GameEngine.electionNightPending()` says which night, if any, is still to be watched.
  - `ElectionEngine.presidential` refuses to compute an election that has already been held, so a result is computed exactly once.
  - The autopilot watches the night like a player would.
- **Interface (preview).**
  - Once the vote is held, the campaign app never draws the war room from a view with no campaign in it. It hands over to election night.
  - The game's router shows any election night not yet watched before anything else, whatever the phase has moved on to.
  - Election night is the game's own screen (`presidency/election-night.ts`). It goes on to the transition, the final report, or the Oval Office.
  - Election Day writes no digest of a day that ended the campaign.
- **Older saves.** A save made before this change has no record. One saved on the night itself (the 2028 transition, or the end after a 2028 loss) shows the night again; any later save counts it as seen.

**Regression tests:**
- **`ElectionDayTest`** (5 tests):
  - 12 campaigns across the three national moods, both parties, four tickets and the three difficulties. They include Democratic and Republican wins, player wins and losses, a landslide and a close race. Each reaches election night with 538 electoral votes, a win going to the transition and a loss to the end.
  - The final stretch runs a day at a time, and the third debate (October 23, the first day of the daily stretch) asks for an approach.
  - A save on the last day of the campaign, loaded or replayed, gives the identical result.
  - The result is computed once; the night survives a reload until watched, and only then.
  - The older-save rule above.
- **`election.js`** (browser), from the eve of each election through the interface's own buttons:
  - 2028 win → the transition;
  - 2028 loss → the final report;
  - 2032 win and 2032 loss → the Oval Office;
  - a game saved on the night opens on it, shows the same night after a reload, and does not show it once watched.

**Remaining:** this milestone only gets election night working. Its broadcast (poll closings, calls, the map) is Milestone 2.

## 2032 result shown as 2028's (this audit; high): fixed

- **Reproduction:** read `EngineApi.campaign`. After the 2032 vote the phase is GOVERNING, so the view reported `result2028` and the 2028 election day.
- **Effect:** the campaign app's dialog would have said "Four more years", with 2028's electoral votes, even after a 2032 defeat.
- **Fix:** the view reports the latest presidential election once the vote is held (`GameEngine.latestPresidentialResult`).
- **Test:** `election.js` (the 2032 nights show 2032's result and headline).

## E-02: older interfaces skip election night on a loaded save (external review; medium): confirmed, fixed

- **Root cause:** in both the old browser build (`mandate-web/.../App.java`) and the desktop shell (`mandate-ui/.../App.java`), `electionShown` was a transient flag. Loading any game that already had a 2028 result set it, so a game saved on the night never showed it. 2032 was never shown at all.
- **Fix:** both now follow `electionNightPending()` and record the night once it has been watched. They also show the 2032 night: "Four more years" or "You lose re-election", then back to the Oval Office.
- **Test:** the engine side is covered by `ElectionDayTest`. The old site's own flow (`flow.js`) still plays a campaign through election night with no errors.

## E-03: "Run the week" in the daily final stretch (external review; low): confirmed in the older interfaces, fixed

- **Root cause:** in both older interfaces, the end-turn label depended only on the phase.
- **Fix:** it now reads "Run the day" when the clock is daily.
- **Preview:** it already said "Run the day"; `election.js` checks the label on each of the last days.

## U-01: real men drawn with women's hair and blouses (played; high): fixed

**Reproduction:**
- **Steps:** start a campaign with "Use real people" on, as or against Jon Ossoff or JD Vance (among others).
- **What happens:** the candidate's portrait has a bob, shoulder-length or pulled-back hair, and a blouse.

**Root cause:** `art/portrait.ts` defined looks only for the eight fictional candidates. Everyone else got a look derived from a hash of their ID.
- That hash picked among seven hairstyles, three of them feminine.
- A feminine hairstyle brought a blouse with it.
- So about three in seven real men were drawn as women.
- Nothing tied a portrait to the person it depicted: no presentation, and no features of any kind.

**Fix:** one source of truth.
- **The data.** `data/portraits.csv` gives each of the 47 candidates (8 fictional, 39 real) a look, keyed by their stable ID. A look has a presentation, skin tone, hair and its colour, facial hair, glasses, face shape and age, taken from each real person's public appearance. Ossoff, for example, is masculine with dark side-parted hair; Vance is masculine with dark hair and a beard.
- **Validation.** `people.Portraits` refuses an invalid look: a beard or moustache with a feminine presentation, feminine hair with a masculine one, an unknown feature, or two looks for one ID.
- **Delivery.** The engine's views send each person's look with them: the campaign's two sides, the president (the Oval Office and The Capital Ledger), election night, and the start screen's candidate list.
- **Drawing.** `art/portrait.ts` now draws only from a look. It no longer derives anything from a hash: only the suit and tie colour still vary by ID, stably. It gained bald and slicked-back hair, long hair, beards, goatees, moustaches and stubble, and face shapes.
- **No look.** A person with no look is drawn as a neutral placeholder silhouette, never as someone else.
- **Newcomers.** Fictional newcomers (Milestone 4) get a look made from their own ID and recorded presentation (`Portraits.generated`).
- **Imagery.** No photographs or outside imagery are used. Every portrait is drawn in vectors in the game's own style, so the art raises no licensing question.

**Regression test:** `PortraitsTest` (5 tests).
- Every candidate has their own look, whose presentation matches an independent list written in the test from the public record.
- Ossoff and Vance are masculine, and Vance has a beard.
- No two real people have identical looks. The test caught Shapiro and DeSantis, and Whitmer and Greene, sharing one; both pairs are now distinct.
- Invalid looks are refused.
- A newcomer's look is the same every time, and fits their presentation.

**Manual inspection:** a contact sheet of all 47 portraits plus the placeholder was rendered and checked by eye.

**Remaining:**
- **Likeness.** The vector style gives correct, consistent and distinguishable likenesses, but not recognisable caricatures. Several real men with short dark hair still look alike. Recognisable caricatures need commissioned illustration, with commercial rights; this is listed in the remaining issues.
- **Who has portraits.** Only presidential candidates are drawn anywhere in the game. Cabinet members, justices, members of Congress and foreign leaders appear by name only, so they cannot be drawn wrongly. If they are ever drawn, they take a row in `portraits.csv`.
- **Desktop.** The desktop (Swing) and older browser interfaces draw no portraits.

## R-01: the 2028–29 world filled with invented politicians (user request; high): fixed

**Reproduction:**
- **Steps:** start any game with "Use real people" on, win in 2028, and open the transition or the Hill.
- **What happened:** the presidential candidates were real, but almost everyone else was invented, from the Senate and the House leaders to the committee chairs, the cabinet shortlists, the White House staff and the Supreme Court shortlist. The vice president was also invented when they were not one of the candidates.
- **Worse:** the invented people came with invented scandals and baggage. With real names on the ticket, the ethics and leak storylines could attach them to real officials.

**Root cause:** each system made up its own people from a name generator, whatever the setting. Only the candidate catalogue knew about real people.

**Fix:**
- **One source.** `people.People` and `people.RealPeople`, backed by `data/people_2029.tsv`, are now the only source. Each of these asks them for its people:
  - the Hill: senators, leaders, committee leaders, swing and base members;
  - the transition and the West Wing: staff, cabinet prospects and the bipartisan pick;
  - the courts: the Supreme Court shortlist;
  - the hundred days' cabinet pool;
  - terms and elections: the 2032 challenger and open seats.
- **What fills a seat** (see DECISIONS_TO_REVIEW, "Real people in 2029"):
  - a real person;
  - or, where the record has none, a labelled office placeholder;
  - and, from 2030 on, a fictional newcomer at the configured rate.
- **Real people come with no invented profile.** The transition's vetting finds nothing on them, and the storylines name a "senior aide" instead.
- **The bipartisan pick** is now a moderate of the other party out of office, never an official of the other party's administration.
- **The sample saves** in the preview were regenerated with the real roster: Shapiro against Vance.

**Regression test:** `RealPeopleTest` (4 tests).
- The data is whole: 100 senators, two to a state; all four leaders; and a real prospect for every cabinet post and staff role in both parties.
- In a 2029 game played for each party, every senator, member, official and the vice president is in the real data or is an office placeholder. The test fails listing any name that is neither.
- No newcomer appears before 2030, and newcomers have appeared by 2033.
- The newcomer rate is 0 in 2028–29, rises each year from 2030 to 2036, holds after 2036, and is configurable.

**Remaining:**
- Governors are not represented.
- House members other than the leaders, committee leaders, swing and base members are not represented.
- The data is as of 2025–26.
- A newcomer's career history is the record of the role they hold. It does not yet follow a newcomer from one office to another.

## R-02: the first hundred days as a separate game (user request; medium): fixed

**What was wrong:**
- The desktop, command-line and old browser New Game screens offered "the 2028 election and the first hundred days" as its own game. It ended at the 100-day report. Its mechanics were also the first chapter of the full term, so the same weeks were two different games depending on a setting.
- The browser game had no 100-day report at all.

**Fix:**
- The setting is gone (`GameSetup.Length` is replaced by `GameSetup.Mode`), and day 100 never ends the game. The report comes, and the presidency goes on in the same weekly turns.
- **Browser game:** it offers the report once, after day 100's paper. It is in the menu from then on.
- **Older interfaces:** they show it with "Keep governing" first. The command line asks "What next?", with keeping on governing as the default.
- **Older saves:** they load as the Full Game. One saved before day 100 goes on past it; one that ended at day 100 keeps its ending.

**Tests:**
- `HundredDaysInGameTest` and `OutcomesTest`: day 100 leaves the game in GOVERNING with the report.
- `ModesTest.anOlderSaveOfTheHundredDaysSliceLoadsAsTheFullGame`.
- `GameShellTest`: the command-line shell's day-100 choice, and governing the hundred days again from it.
- `modes.js`: the paper, the report card, "Keep governing", and the report in the menu.

**Goldens:** the "slice" fingerprints now stop at the 100-day report (`GoldenRegressionTest.slice`) instead of at the old slice's end, so they were regenerated.

## M-01: a crisis started from the start screen never ended the game (this audit; medium): fixed

- **Reproduction:** on the start screen, choose "The Strait of Hormuz", then any preset. The crisis runs, ends, and the presidency simply goes on. No outcome is declared and no report is shown.
- **Fix:** Individual Crisis (`Command.IndividualCrisis`).
  - The game ends when the crisis does, with an after-action report.
  - The report gives the result (won, lost or ended in part), what happened, the figures and the crisis's own record.
  - Taiwan is offered as well as Hormuz, both with three presets.
- **Tests:**
  - `ModesTest.anIndividualCrisisEndsAtItsOutcomeWithAnAfterActionReport`: Hormuz reached all three results; Taiwan reached a win and an end in part.
  - `modes.js`.
- **Remaining (fixed 14 November 2026, T-02 below):** no line of play found in the tests made Taiwan fall in Individual Crisis. Taiwan's outcome depended on the preset and not on the president's orders.

## T-03: the 2032 campaign paused the presidency (owner's request, 14 November 2026; medium): fixed

- **What happened:** for the fifteen weeks of the re-election campaign, Congress, the West Wing, the world and the president's approval stood still. Only the economy moved (B-08). The race therefore read an approval frozen at the campaign's start.
- **Fix:** each campaign week runs the presidency's weekly systems (`GameEngine.campaignOffice`). The rooms' orders are accepted while running for re-election (`GameEngine.inOffice`). The war room shows a White House card: the record, its weight and the campaign's, the questions waiting, and the rooms. A re-election counts the campaign at 0.6 (`election.incumbent.campaign`).
- **Found on the way:** the war room reopened the first question after "Decide later". With non-blocking questions this trapped the player. It now asks only the blocking ones before a turn, and steps through the rest.
- **Tests:**
  - `ReelectionOfficeTest`;
  - `LifecycleTest` (160 games);
  - `office2032.js`;
  - `RecordBalanceRun`: the record explains 69% of the race.

## T-02: Taiwan's outcome did not turn on the president's orders (owner's request, 14 November 2026; medium): fixed

- **What happened:** in Individual Crisis, the staff left alone never lost on the base case. The pessimistic case ended in part whatever was done. Staying "diplomatic only" was overruled when China struck American forces. A ceasefire Washington accepted as its support collapsed counted as an end in part. Every crisis from the sample save was the same crisis.
- **Fix:** seven changes, in docs/BALANCE_TAIWAN.md:
  - America backing down is a loss;
  - a blockade still choking Taiwan at eleven weeks forces terms;
  - Beijing keeps America out when Washington says it will not fight;
  - sanctions shared with partners weigh on Beijing during a blockade;
  - Beijing's resolve varies;
  - the president's staff waits for clearer warning before the major preparations;
  - each Individual Crisis has its own seed.
- **Measured:** `TaiwanBalanceRun`, 36 crises per cell. On the base case:
  - the patient president holds Taiwan 97% of the time;
  - the staff left alone loses 36%;
  - diplomacy alone holds 19% (44% in the optimistic case).
- **Tests:** `TaiwanOutcomeTest` (2), `TaiwanTest.aCeasefireThatLeavesBeijingItsGainsIsAmericaBackingDown`.

## U-03: status labels pushed phone screens sideways (found in this audit's browser run; low): fixed

- **What happened:** at 390 pixels wide, the Roosevelt Room's Bills tab scrolled sideways by 36 px, and the agenda with a score open by 12 px. The cause was a status pill, "Bottled up in committee in the House", that could not wrap.
- **Fix:** below 640 pixels, pills wrap inside their card (`preview.css`).
- **Test:** `congress.js` and `economy.js` at 390 pixels.

## T-01: the campaign preview's audit caught the ticker mid-fade (this audit; test only): fixed

- **What happened:** `preview.js` reported low contrast on the news ticker's flag and text. The colours it measured were the ticker's real ones, blended at partial opacity during its 0.6 s fade-in.
- **Fix:** the suites' audits now wait for running animations to finish (`preview.js`, `modes.js`). The ticker's real colours pass.

## B-02: a crisis postponed the calendar's fixed dates (headless simulation; high): fixed

**Reproduction:** the lifecycle run (`LifecycleTest`; the 40-game run in SECOND_TERM_VALIDATION.md), Full Game seed 29.
- China's coast guard closed on Taiwan on October 7, 2030, and the crisis ran in daily turns to December 16.
- The November 5 midterms were held on December 23, the first ordinary week after the crisis.

**Root cause:**
- A crisis's turns are its days and hours, and its weekly tick (`crisisWeek`) ran only the economy, the Fed and opinion.
- Every fixed date was checked only in the ordinary governing week. That covers the midterms, the start of the 2032 re-election campaign, the 2032 vote, the race to succeed the president, the second oath and the January 20 handoffs.
- So all of them waited for the crisis to end.

**Worse cases found by reading the code:**
- A crisis spanning July 19, 2032 would have begun the re-election campaign afterwards by setting the clock back to July 19.
- One spanning November 2, 2032 would have held the vote weeks late.

**Fix:**
- **Every crisis turn** checks the fixed dates (`crisisCalendar`): the midterms, the campaign's start, Election Day, the race to succeed the president, the second oath and the handoff.
- **The crisis week** runs the terms of office: the question of running again, the primaries and the race.
- **The clock:** the re-election campaign never moves it back, and neither the campaign nor the vote changes a crisis's clock.
- **Orders:** a crisis that runs into the campaign is still fought from the Oval Office. Its orders are accepted in the campaign phase, and the browser game shows the Oval Office instead of the war room until the crisis ends.
- **What still pauses:** Congress, the West Wing and the president's slots, as before.

**Tests:**
- `LifecycleTest.aCrisisDoesNotMoveTheMidterms`: the midterms are held on November 5 inside a crisis.
- `LifecycleTest.aCrisisDoesNotMoveTheReelectionCampaignOrElectionDay`: the campaign begins and the vote is held on November 2 inside a crisis, the clock never goes back, and the game then goes on to the end of a term.

## B-03: a game that ended mid-crisis could be played on (code reading; medium): fixed

- **Root cause:** `endTurn` checked for an active crisis before the game's phase. A game whose presidency ended during a crisis would have played the crisis on after its end, if asked for another turn.
- **Fix:** an ended game takes no more turns.
- **Test:** `LifecycleTest.aCrisisDoesNotMoveTheReelectionCampaignOrElectionDay`: after the end, one more turn closes the game, and any turn after that is refused.

## B-04: no election night for the races to succeed the president (lifecycle run; high): fixed

**Root cause:**
- The 2036 election and the 2032 election without the president on the ballot are decided by the terms of office (`TermsEngine.election`), not as an `ElectionResult`.
- Election night knew only 2028 and the president's own 2032 race, so neither night was ever shown.

**Fix:**
- `GameEngine.successionResult` presents the decided race as an election: every state's margin, the electoral and popular votes, and the tipping point. It reads what Election Day computed, once.
- Election night shows it like any other night, through the same broadcast and the same pending-night record:
  - the latest presidential night is now whichever came last;
  - `electionKind`, `electionDayOf` and `electionSides` give its kind, its day and its two sides;
  - its last word is "Your party keeps the White House" or "The White House changes hands";
  - it goes back to the Oval Office for the lame-duck weeks.
- The older interfaces show it too.

**Tests:**
- `LifecycleTest`: every night of every game has 538 electors and two parties, and the 2036 night survives a reload until it is watched.
- `election.js`: the 2036 night and a 2032 night without the president.

## B-05: the race to succeed the president was decided a week late (lifecycle run; low): fixed

- **Root cause:** the race was checked at the start of a weekly turn, so a turn that began before Election Day left it to the next. The 2032 race came with the clock at November 15.
- **Fix:** it is held on the turn whose days reach Election Day, as the midterms are (`GameEngine.successionDue`).
  - In ordinary weekly turns the results come with that week, on the Monday after.
  - In a crisis's days they come on the day itself.
- **Test:** `LifecycleTest.theSuccessorsNightIsAnElectionNightAndSurvivesAReload` (within the week of November 4, 2036).

## B-06: the successor's name lower-cased in the record (lifecycle run; low): fixed

- The election-night log line lower-cased its headline, giving "Marco rubio is elected president".
- It is now written from the names themselves.

## B-07: a lost 2028 campaign graded as a "Failed presidency" (lifecycle run; low): fixed

- **What happened:** a Full Game lost in 2028 scored its legacy on the presidency's scale. A high-enough score was graded "Failed presidency", in the record and in the older interfaces, for a candidate who was never president.
- **Fix:** a lost campaign is judged as a campaign: the same grade and assessment as Campaign Only ("A narrow loss", "A clear loss" and so on).
- **Test:** `ModesTest.campaignOnlyEndsOnElectionNightWonOrLost` checks the verdict's grade against the campaign's assessment. `LifecycleTest.bothModesRunOnePresidencyToItsEnd` checks every lost 2028 game.

## B-08: the economy stood still through the 2032 re-election campaign (lifecycle run; high): fixed

- **Reproduction:** `LifecycleTest` counts the economy's weekly steps against the calendar. A two-term Full Game stepped 388 weeks in 403. The fifteen missing were the re-election campaign, from July 19 to Election Day 2032.
- **Root cause:** the re-election campaign runs the 2028 campaign's turns. With a president in office, those turns never stepped the economy, the Fed or the data releases.
- **Fix:**
  - A weekly campaign turn steps the economy's week.
  - In the daily final stretch, each Monday steps the week just past.
  - After the vote, the week that holds it is stepped before the governing weeks resume.
- **Test:** `LifecycleTest.bothModesRunOnePresidencyToItsEnd`: in every game the economy is stepped once a calendar week from day 100 to the end, within three weeks.
- **Remaining, by design for now:** the world, Congress, the West Wing and the president's slots still pause during the campaign's weeks, as they did. A campaign that runs the presidency alongside is to review.

## E-01: the end-turn control said one thing, its tooltip another, and it did a third (external review; low): confirmed, fixed

- **What happened:** in the older interfaces (desktop and old browser), the end-turn button kept its label ("End the month") while decisions waited. Its tooltip said "Answer the decisions waiting for you first", and pressing it opened the decisions.
- **Also:** governing turns have been weeks since the presidency port, but the button still said "End the month", and its tooltip said the month resolves.
- **Fix:**
  - While the turn is blocked, the control is labelled with what it will do: "Answer 2 decisions first", or "Choose your agenda first". Its tooltip and its action match.
  - Governing turns read "End the week".
- **Test:** `FlowTest` (desktop) drives the turns by the control's own label. `flow.js` (old browser) plays its whole flow with no console errors.

## E-04: the preview blocked the week on decisions the engine does not require (external review; medium): confirmed, fixed

**What happened:**
- In the preview's Oval Office, ending the week opened every pending decision first, and "Decide later" said "the week cannot run until it is answered".
- But the engine refuses to end a turn only while an event, one of the hundred days' questions, or the week's debate waits.
- The Hill's, the West Wing's, the terms' and the world's questions have deadlines, and the staff answer them when due.
- So a question due in four weeks held up every week until it was answered.
- In a crisis's days, "Decide later" also left the player outside the crisis room.

**Fix:**
- **The decisions view** marks each decision `blocking`, and the presidency view counts the blocking ones (`blockingDecisions`).
- **Only those stop the week.** The rest wait on the desk, with their badge, and come up after the paper.
- **"Decide later"** says what it does for each kind.

**Test:** `terms.js`: after re-election the week runs, then the second term's theme question comes up from the desk. `presidency.js`, `westwing.js`, `congress.js` and `sitroom.js` play weeks with questions on the desk.

## E-06: crises counted their consequences twice (external review; high): confirmed, fixed

A read of every path from the two crises into the rest of the game found these:

| ID | Double count or conflict | Fix |
|---|---|---|
| F1 | A Taiwan crisis ending within a day of its weekly tick stepped that week twice: once in its closing tick, then again in the ordinary week from the same Monday. | The closing tick steps only if a day has passed since the last. |
| F2 | A war China began during the world's weekly step switched the clock to days mid-week. The week was stepped as seven days while the clock moved one, so the crisis's first tick stepped those days again. | The attack opens the crisis at the week's end (`WorldState.taiwanAttack`). |
| F3 | The oil price counted Taiwan twice: the crisis's own Brent and the old escalation ladder's premium and market stress. At the end the crisis added an inflation shock from the same Brent. | With the world model, the old ladder adds no premium or stress. The crisis's fallout adds the chip shortage's inflation, not the oil's. |
| F4 | Before any crisis, a quarantine or blockade had two supply-shock models: the world's weekly shock and the ladder's premium. | As F3: the world's shock is the one. |
| F5 | When Taiwan fell, the crisis's fallout and the world both cut Taiwan's, Japan's and Korea's relations. Taiwan lost up to 90 points. | The world owns those shifts. The fallout keeps the Philippines and Australia, and a defence's gains. |
| F6 | American dead in a Taiwan war were counted twice in the "Americans killed" measure: in the world's count, which the crisis reports as they die, and on the war's record. | With the world model, its count alone. |
| F7 | The fall of Tehran was reported and rewarded twice. The strait's ending gave a performance bonus; the world's invasion victory added a rally, more performance and a second "government falls" story. | The occupation takes over quietly: no second rally, bonus or story. |
| F8 | Crisis costs were booked as permanent annual deficits: escorts, strikes, reinsurance, a build-up, and a Taiwan war's one-time bill. Turning reinsurance off never ended its cost. | The strait's spending is tracked and reversed when the crisis ends. A war's bill is paid over one year (`Effect.Deficit` takes a duration). |
| F9 | The Strategic Petroleum Reserve was full again for every Hormuz crisis. | A crisis starts with what the last left, refilled by about three million barrels a month. |
| F10 | The world could declare a Taiwan war that no engine fought, when a crisis could not begin within a year of the last or after Taiwan fell. The theater then stayed frozen at war. | The world steps back from it (`taiwanWarRefused`). |
| F11 | The first hundred days' crisis outcome was thrown away at day 100: the world was seated from defaults. | The world starts from the shifts the hundred days left in the strait's tension, deterrence and the Middle East. |

**Tests:**
- **`CrisisAccountingTest`:**
  - the strait's spending comes to nothing once the crisis ends;
  - the fall of Tehran is one story;
  - the reserve carries over.
- **`LifecycleTest`:** the economy is stepped once a calendar week in every game (F1, F2).
- **The rest are fixes to code paths read in the audit,** with the crisis tests (`HormuzTest`, `TaiwanInGameTest`, `TaiwanTest`, `ForeignTest`) still passing.

**Also fixed:** a crisis week now runs the economy's whole week, the jobs, prices and GDP releases included, and the military's monthly production. Before, it ran only the economy's step and the Fed.

**Remaining (to review):**
- **A provoked attack** dispatches both a rally and an approval bonus.
- **A Taiwan crisis deterred without a war** counts nowhere in the verdict's crisis record.

## E-05: the main site's and the preview's builds could not be told apart (external review; medium): confirmed, fixed

**What was wrong:**
- **The root:** the site's root serves the older vertical slice, the desktop interface ported to the browser. It had no build stamp.
- **The preview:** its `version.json` held only a hash of the engine and the sample saves. Nothing said which source commit either page came from, or whether it was built from uncommitted changes.
- **Its own engine is current, not stale:** `mandate.js` is rebuilt by every `mvn install`.

**Fix:**
- **What `version.json` holds:** `mandate-web/tools/assemble.mjs` writes into both sites the build, the source commit, whether the tree had uncommitted changes, and the day.
- **The root says what it is:** its `version.json` names itself "the older vertical slice".
- **The start screen** shows the preview's stamp at its foot.
- **The README** says which page is which.

**Publishing:** the preview is published to `preview/` on gh-pages, and the root changes only once the preview is approved (see the README).

## E-07: documentation out of date (external review; low): confirmed, fixed

Several documents had fallen behind:
- The README described a start screen with a separate crisis start and a hundred-days moment, and the front page as "the 2028 election and the first hundred days".
- `docs/HORMUZ.md` described a standalone start with a year and a custom preset that was never built.
- `docs/ISLAND_CHAIN.md` described starting the crisis from the moments list.

The README, `docs/HORMUZ.md` and `docs/ISLAND_CHAIN.md` now describe the four games and how each crisis begins. The new `docs/MODES.md` and `docs/SECOND_TERM_VALIDATION.md` cover the modes and the two terms. `docs/PRESIDENCY_PORT.md` is kept as the port's historical record.

## E-08: older saves (external review; medium): confirmed partly, fixed

**What was wrong:**
- Saves before this round had no record of which election nights were watched (fixed in U-02).
- They named a game *length*: the hundred-days slice was its own game (fixed in R-02).
- They had none of this round's other fields: the mode, the individual crisis, China's pending attack, the strait's spending.

**Fix:** every new field has a default for an older save.
- `electionNightSeen` is settled by the older-save rule in U-02.
- A length loads as the Full Game.
- No crisis, attack or spending is pending.

Nothing an older save holds is changed.

**Test:** `OlderSavesTest`. The shipped sample saves from before this round (campaign, inauguration week and governing, from commit `09aab1c`) load as the Full Game at the same date. They play six turns on, save in the new format, and that save reloads to the same world. `ModesTest` covers a hundred-days slice save; `ElectionDayTest` covers the night record.

## B-09: a secretary who resigned was hired back (lifecycle run; low): fixed

- **Reproduction:** in a Governing Only game (seed 4, real people), "Secretary of State Mike Pompeo resigns to return to private life" appears twice: in July 2030, then again in December 2031.
- **Root cause:** a prospect was skipped only while serving or nominated. Anyone who had left the administration could be named again, and with real people the shortlist is the same few names.
- **Fix:** the West Wing keeps everyone who has left (resigned, fired, or gone at a term's end) and never offers them again (`WestWingState.departed`). Older saves start with an empty list.
- **Test:** `RealPeopleTest.noOneIsHiredBackAfterLeaving`, over whole real-people presidencies: no one resigns twice, and no one who left is serving.

## B-10: the clock went back after a crisis that ended just before a handoff (160-game lifecycle run; low): fixed

- **Reproduction:** `LifecycleTest -Dmandate.lifecycleGames=160`, Governing Only seed 82.
  - A Taiwan crisis that began in late 2032 ended on January 17–18, 2033.
  - The end of a crisis jumps the clock to the next Monday, January 24, which put the January 20 handoff behind it.
  - The next turn's handoff then set the clock back to January 20.
- **Fix:** after a crisis, if the term's handoff comes before the next Monday, the clock stops on its day and the handoff is held at once (`GameEngine.resumeWeeks`).
- **Test:** `LifecycleTest`, whose check that the clock never goes back from one turn to the next caught it.
