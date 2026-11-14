# Mode test matrix

What is tested, for each game and each way it can end, and where. Every row names the test that covers it. Nothing here is claimed from a test that was not run. The "Last run" column gives the date and the code it ran on.

**The paths.** The matrix uses these eight. If they differ from the A–H of the original instructions, this table's definitions are the ones tested.

| Path | Game | Route to the end |
|---|---|---|
| A | Full Game | Lose 2028: election night, then the campaign's end |
| B | Full Game | Win 2028, defeated in 2032: the lame-duck weeks, the end January 20, 2033 |
| C | Full Game | Win 2028, not on the 2032 ballot (stepped aside or denied renomination), a successor runs: the end January 20, 2033 |
| D | Full Game | Win 2028 and 2032: the second term, the 2034 midterms, the 2036 successor's race, the end January 20, 2037 |
| E | Campaign Only | Win 2028: the end on election night, with the assessment |
| F | Campaign Only | Lose 2028: the end on election night, with the assessment |
| G | Governing Only | From the 2028 night: one term (B or C's ending) or two (D's) |
| H | Individual Crisis | Taiwan or Hormuz: won, lost or ended in part, with the after-action report |

Also tested on every path: removal from office (impeached and convicted), which ends the game that day with the verdict on the years served.

## Engine (JVM, `mvn install`)

| Path | Test | Covers | Last run |
|---|---|---|---|
| A | `ModesTest.campaignOnlyEndsOnElectionNightWonOrLost` (loss branch); `LifecycleTest.bothModesRunOnePresidencyToItsEnd`; `ElectionDayTest` | The 2028 night pending; the campaign's verdict, not a presidency's (B-07); no president | 13 Nov 2026, M8 code |
| B, C, D | `LifecycleTest.bothModesRunOnePresidencyToItsEnd` (16 games in the build; `-Dmandate.lifecycleGames=160` for the full run) | End dates; the 2030 and 2034 midterms; no third term; every election night, the successor's included; the economy stepped once a week; the clock never going back; the verdict's ten measures | 13 Nov 2026, M8 code (16 games); the 160-game run on M7 code (docs/SECOND_TERM_VALIDATION.md) |
| B, C, D | `ReelectionOfficeTest`; `RecordBalanceRun` (off by default) | The presidency (Congress, the West Wing, the world, opinion) moving on through the 2032 campaign, the save replaying it; the race following the record | 14 Nov 2026 |
| B, C, D | `LifecycleTest.aCrisisDoesNotMoveTheMidterms`; `aCrisisDoesNotMoveTheReelectionCampaignOrElectionDay`; `theSuccessorsNightIsAnElectionNightAndSurvivesAReload` | The calendar kept in a crisis (B-02); an ended game takes no turns (B-03); the 2036 night (B-04, B-05) | 13 Nov 2026, M8 code |
| E, F | `ModesTest.campaignOnlyEndsOnElectionNightWonOrLost` | Both outcomes end the game; the assessment; the verdict's grade | 13 Nov 2026, M8 code |
| G | `ModesTest.governingOnlyOpensOnElectionNightWithTheChosenAdministration` (both parties; a toss-up and an unfavourable mood); `governingOnlyGoesOnThroughTheSamePresidencyAsTheFullGame`; `LifecycleTest` (every other game is Governing Only) | The chosen president-elect elected; the 2028 night first; exact replay; the same presidency to its end | 13 Nov 2026, M8 code |
| H | `ModesTest.anIndividualCrisisEndsAtItsOutcomeWithAnAfterActionReport`; `anIndividualCrisisBeginsOnlyFromAGoverningPresidency`; `CrisisAccountingTest`; `TaiwanOutcomeTest` | Hormuz won, lost and ended in part; Taiwan won and lost by the president's orders, each crisis seeded and replayed; the report saved; the crisis's costs ended with it (E-06) | 14 Nov 2026 |
| All | `ModesTest.anOlderSaveOfTheHundredDaysSliceLoadsAsTheFullGame`; `OlderSavesTest` | Saves from before this round load, play on and save again (E-08) | 13 Nov 2026, M8 code |
| A, E, F | `WarRoomDeskTest` | Internal polls (paid, once a week, saved), the message's tone, a state's voters adding up to its poll | 14 Nov 2026 |
| All | `DeterminismTest`; `GoldenRegressionTest` | The same seed gives the same world; the golden fingerprints | 13 Nov 2026, M8 code |

**Variety in `LifecycleTest`'s games.** These vary by the game's index:
- the mode;
- the party;
- the cast: real (Shapiro, Vance) or fictional (Reyes, Marsh, Harlan, Whitfield);
- the national mood (toss-up, Democratic or Republican);
- the difficulty (story, standard or hard).

The seed is the index plus one.

## Browser (Chromium, Playwright, `mandate-web/src/test/browser`)

| Path | Suite | Covers |
|---|---|---|
| A | `election.js` (eve2028-lose) | Run the day to the vote, the broadcast, then the campaign's end page |
| B | `election.js` (eve2032-lose, night2032-lose) | The 2032 loss's night and the Oval Office after it; resuming after a reload |
| C | `election.js` (night2032-successor) | A 2032 night without the president: "The White House changes hands" or "Your party keeps it" |
| A, E, F | `warroom.js` | The war room's Message and Polls tabs and the Voters desk |
| B, D | `office2032.js` | The war room's White House card in 2032: the record, the questions, the four rooms |
| D | `election.js` (eve2032-win, night2036); `terms.js` | Four more years, the second term's theme, the race to succeed, the 2036 night, the verdict at the handoff |
| E, F | `modes.js` (eve2028-campaign-win, eve2028-campaign-lose) | The night, the campaign's end (the assessment, the vote, every state), the night again, the replay |
| G | `modes.js` | Governing Only from the title screen's setup to the 2028 night and the transition |
| H | `modes.js` (crisis-hormuz-last, crisis-taiwan-last); `hormuz.js`; `taiwan.js` | The last turn and the after-action report; the crisis started from the setup; the crisis rooms |
| All | `play.js`, `presidency.js`, `economy.js`, `congress.js`, `westwing.js`, `residence.js`, `sitroom.js`, `preview.js` | The title screen, the setup and a new game; the hundred days and day 100's report (`modes.js`); every room. Each at 1600, 1280 and 390 px, in every palette, at 125% text and with reduced motion (axe-core WCAG 2.1 AA, text size, sideways scroll) |
| All | `front.js` | The title screen and every step of the setup, the real 2028 field and the fictional cast, the face-off, the keyboard; the site's front page handing a new game and a moment to the game's page; the earlier slice at `classic/`. At 1600 and 390 px, in every palette, at 125% text and with reduced motion |
| All | `broadcast-unit.mjs` | 398 synthetic nights: every race called for its winner, the final count the result |
| Older site | `flow.js` | The earlier slice's own flow (now at `classic/`), with no console errors |

Test saves for these come from `game.mandate.ui.ElectionSaves`. They are generated, not shipped.

## Desktop and command line

| Interface | Tests | Covers |
|---|---|---|
| Desktop (Swing) | `FlowTest` and the rest of `mandate-ui` (18 tests) | The new-game wizard (Full Game, Campaign Only), election night, the hundred days and day 100's report, undo and rewind, the end-turn control's label (E-01) |
| Command line | `GameShellTest` (4 tests) | One continuous game to day 100 and its "What next?", governing the hundred days again, a hand-built campaign, Continue |

## Parity (the browser's engine against the JVM's)

`ParityCheck` (`mvn -pl mandate-web package -Pparity`) plays 40 autopilot campaigns, six full terms, a Hormuz crisis and a Taiwan crisis, in TeaVM's JavaScript and on the JVM. The lines compared exclude timings. On 13 November 2026 they were identical on all 53 compared lines, on the M7 code and again on the final M9 code.

## Not covered by an automated test

- Removal from office in the browser. The engine covers it (`LifecycleTest` accepts and checks it); no browser save reaches it.
- The desktop and command-line interfaces' Governing Only and Individual Crisis: they are not offered there.
- A human playtest of the whole game's length: the playtime figures in the README are estimates from turn counts.
