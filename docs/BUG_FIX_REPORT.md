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
