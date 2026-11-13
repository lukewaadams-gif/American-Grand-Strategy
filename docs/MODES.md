# The four games

American Grand Strategy is one game engine (`mandate-core`'s `GameEngine`) with four ways in. They share the same systems: the campaign, the economy, the budget and debt, Congress, the West Wing, the press, the world and its crises. They also share the same saves. The start screen offers exactly these four (`mandate-web/src/main/ts/start.ts`). The engine names them `GameSetup.Mode`.

| Mode | Where it starts | Where it ends |
|---|---|---|
| **Full Game** | The 2028 campaign, at the start of the general election | **A 2028 loss:** election night and the post-mortem. **A 2028 win:** the presidency (below). |
| **Campaign Only** | The 2028 campaign | Election night 2028, won or lost, then the campaign's end: the result, the popular vote, every state and the assessment. |
| **Governing Only** | Election night, November 7, 2028, with the administration the player chose already elected | The same presidency as the Full Game. |
| **Individual Crisis** | The Taiwan or Hormuz crisis, begun at once in a governing presidency (September 2029) | The crisis's outcome (won, lost or ended in part), then the after-action report. |

## The presidency (Full Game and Governing Only)

Both modes run the same code from the transition on. Nothing in the presidency asks which mode it is in. A second-term bug fixed in one is therefore fixed in both.

> **Status:** verified end to end.
> - **The hundred days:** `HundredDaysInGameTest`, `ModesTest` and `modes.js`.
> - **The lifecycle to January 20, 2037:** 160 headless whole games across both modes with no broken rule (`LifecycleTest`; docs/SECOND_TERM_VALIDATION.md).
> - **Every election night**, the successor's in 2036 included: `election.js`.

1. **The transition:**
   - the agenda;
   - the White House staff;
   - the inauguration on January 20, 2029.
2. **The first hundred days, as governing's opening chapter:**
   - the cabinet confirmed;
   - the flagship bill;
   - the economic shock;
   - the foreign crisis.

   Day 100 brings the **100-day report**. Reading it is optional: it is offered once, after day 100's paper, and stays in the menu. The presidency then goes on, in the same weekly turns, with the Roosevelt Room, the West Wing and the Situation Room open. There is no longer a separate hundred-days game.
3. **Governing,** week by week, then the **2030 midterms**.
4. **Re-election in 2032.** A weak president can draw a primary challenge; a president can also choose not to run.
   - **Lost or not run:** the lame-duck weeks to January 20, 2033, then the verdict of history on four years.
   - **Won:** the second inauguration and a second term with its own theme and legacy goals, the lame-duck decline and the **2034 midterms**. There is no third term: the **2036 election** is for the party's successor, with primaries, the president's endorsement and the campaign from the White House. The game ends on January 20, 2037, with the verdict of history on eight years.
   - **Removed from office** (impeached and convicted): the game ends that day, with the verdict on the years served.
5. **Every presidential election has its election night,** computed once on Election Day and shown before anything else:
   - 2028;
   - 2032, the president's own race, or the successor's if the president is not on the ballot;
   - 2036, the successor's.

   Its last word is the player's ("Four more years") or the party's ("Your party keeps the White House"). Weekly turns report a Tuesday election with the week that holds it, on the Monday after. The night itself is dated on Election Day.
6. **A crisis never moves the calendar.** The midterms, the re-election campaign, Election Day, the second oath and the handoff keep their dates during a crisis's days and hours. The rest of the presidency's week (Congress, the West Wing, the president's slots) pauses until it ends.

Saves carry the whole state across the terms: the economy, the budget and the debt, Congress, the appointments, the laws and policies, diplomacy and the military's inventories.

## Governing Only's start

The player chooses:
- the party in power;
- the president-elect;
- the candidate they defeated;
- the national mood;
- how hard the other party's campaigns are;
- real people or the fictional cast.

The staff play the 2028 campaign for both sides, so Congress, the polls and every state's margin come from a real campaign. If the player's ticket loses, the engine tries the next world of the same seed, until one elects it (`GameEngine.start`, at most 48 tries). The game's setup records the seed that did, so the save replays exactly. The 2028 result is computed once. Election night shows it before the transition begins.

## Individual Crisis

The start screen offers both crises with three presets: the base case, optimistic and pessimistic.
- **Where it begins:** in the sample presidency's world of September 2029 (`saves/governing.json`, real people).
- **How:** with `Command.IndividualCrisis`. From then on the game's mode is `CRISIS`.
- **While it lasts:** Congress, the economy, the press and the allies go on around it, as they do in the Full Game.
- **When it ends:** the game ends with an after-action report (`CrisisRecord.Report`), which gives:
  - the result;
  - what happened and why;
  - the days;
  - the president's approval, the misery index and the munitions stockpile, at the start and at the end;
  - the crisis's own losses;
  - the crisis's record, day by day.

How each outcome counts:

| Crisis | Won | Ended in part | Lost |
|---|---|---|---|
| Taiwan | Deterrence held, or Taiwan defended | A stalemate (such as China holding the outlying islands) | Taiwan fallen, or a nuclear catastrophe |
| Hormuz | A settlement, or shipping restored | Regime change and occupation; or a day-150 stalemate with the oil flowing at 60% of normal or more | A day-150 stalemate with the strait still mostly shut |

**The war game alone.** Island Chain, the Taiwan war game without a presidency (as America or as China), is offered alongside Individual Crisis. It is not a fifth mode: it is the same war model's own interface, and it is not saved.

## Older saves

A save made before the four modes records a *length* instead of a mode:
- **"HUNDRED_DAYS" (the old hundred-days game) and "FULL_TERM"** both load as the Full Game.
- **One saved during the hundred days** now goes on past day 100.
- **One that had already ended at day 100** keeps its ending.

Nothing else in the save changes. `ModesTest.anOlderSaveOfTheHundredDaysSliceLoadsAsTheFullGame` checks this.

## The older interfaces

The desktop (Swing) interface, the command-line shell and the old browser build at the site's root are earlier vertical slices.
- **What they offer:** Full Game and Campaign Only.
- **What they lack:** Governing Only and Individual Crisis are only in the browser game.
- **Day 100** in each brings the report, and the presidency goes on: "Keep governing" in the desktop and old browser interfaces, and the "What next?" menu in the command-line shell.

## Tests

- **`ModesTest`** (core):
  - Campaign Only ends on election night, won and lost.
  - Governing Only opens on the 2028 night with the chosen administration, for both parties and in an unfavourable mood, and replays exactly.
  - Governing Only goes on into the same presidency.
  - An Individual Crisis ends at its outcome, with a report that survives a save. The strait reached all three results, and Taiwan reached a win and an end in part.
  - A crisis begins only from a governing presidency.
  - Older saves load as above.
- **`modes.js`** (browser), through the interface's own buttons:
  - the four modes, and each one's setup, at 1600 and 390 pixels wide;
  - Campaign Only's night and end, the night watched again, and a replay;
  - Governing Only from the start screen to election night and the transition;
  - each crisis's last turn and after-action report;
  - day 100's report card, and the presidency after it.
