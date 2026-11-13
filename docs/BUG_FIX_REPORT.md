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
- **Remaining:** no line of play found in the tests makes Taiwan fall in Individual Crisis. In the sample world, Taiwan's outcome depends on the preset and not on the president's orders (staying "diplomatic only" gives the same result as fighting). This is to be examined in the Milestone 8 crisis audit.

## U-03: status labels pushed phone screens sideways (found in this audit's browser run; low): fixed

- **What happened:** at 390 pixels wide, the Roosevelt Room's Bills tab scrolled sideways by 36 px, and the agenda with a score open by 12 px. The cause was a status pill, "Bottled up in committee in the House", that could not wrap.
- **Fix:** below 640 pixels, pills wrap inside their card (`preview.css`).
- **Test:** `congress.js` and `economy.js` at 390 pixels.

## T-01: the campaign preview's audit caught the ticker mid-fade (this audit; test only): fixed

- **What happened:** `preview.js` reported low contrast on the news ticker's flag and text. The colours it measured were the ticker's real ones, blended at partial opacity during its 0.6 s fade-in.
- **Fix:** the suites' audits now wait for running animations to finish (`preview.js`, `modes.js`). The ticker's real colours pass.
