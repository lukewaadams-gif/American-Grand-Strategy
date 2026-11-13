# Second-term validation

**What this checks:** that the presidency runs the same way, from the 2028 night to its end, in both modes that govern: the Full Game and Governing Only (docs/MODES.md).

**The rules checked:**
- One term ends at noon on January 20, 2033, and two terms at noon on January 20, 2037.
- The midterms of 2030 and 2034 are held.
- There is no third term: the 2036 election is for the successor.
- Every presidential election night is shown, the successor's included.
- A save in the second term reloads to the same world.
- A crisis never moves the calendar's fixed dates.

Run on 13 November 2026, on the code of Milestone 7.

After that run, Milestone 8 fixed the crises' double counting (E-06), the economy standing still in the 2032 campaign (B-08) and rehiring (B-09). `LifecycleTest` now also checks that the economy is stepped once a calendar week. The 16-game run in the build passes on the new code, and the 160-game run was repeated on it (below).

## How

**The lifecycle run** is a headless harness that plays whole games with the staff playing (`AutoPilot`), and checks every rule above in every game.
- **What varies, by the game's index:**
  - the mode, alternating Full Game and Governing Only;
  - the party;
  - the cast, real people or fictional;
  - the national mood;
  - the difficulty.
- **The seed** is the index plus one.
- **The second-term reload:** in the second term, after March 2034, each game is saved, reloaded and checked for the same fingerprint, then played on from the reload.

The same checks, over the run's first 16 games, are a unit test: `LifecycleTest.bothModesRunOnePresidencyToItsEnd`, part of `mvn install`.

## Results: 160 games, 0 broken rules (112 seconds; the longest game 4.6 s)

| Path | Full Game | Governing Only |
|---|---|---|
| Lost 2028: ends on election night | 34 | — (Governing Only starts elected) |
| One term, defeated in 2032: ends January 20, 2033 | 14 | 16 |
| One term, not on the 2032 ballot (stepped aside or denied renomination); a successor ran: ends January 20, 2033 | 8 | 29 |
| Two terms: the 2034 midterms, the 2036 succession election, ends January 20, 2037 | 17 | 23 |
| Removed from office (impeached and convicted) | 7 | 12 |
| **Total** | **80** | **80** |

**In every game:**
- **The nights:** the 2028 night was shown. Every presidential election after it had its night, with 538 electors and both parties:
  - the 2032 night, whether the president was on the ballot or not;
  - the 2036 night in every two-term game.
- **The clock** never went back.
- **The end:** no game ran past January 20, 2037, and no president served a third term.
- **The verdict:** every presidency ended with the historians' ten measures.
- **The reload:** every second-term save reloaded to the same world.

## Results after Milestone 8: 160 games again

The same 160 games on the Milestone 8 code: the crises counted once, the economy stepped through the 2032 campaign, and no rehiring.

| Path | Full Game | Governing Only |
|---|---|---|
| Lost 2028 | 34 | — |
| One term, defeated in 2032 | 13 | 18 |
| One term, not on the 2032 ballot; a successor ran | 6 | 27 |
| Two terms | 20 | 25 |
| Removed from office | 7 | 10 |

**The harness:** 0 broken rules, 135 seconds; the longest game took 4.9 s.

**`LifecycleTest` over the same 160 games** caught one more bug, B-10. The clock went back from January 24 to January 20, 2033, after a Taiwan crisis ended just before a handoff. With B-10 fixed, the 160-game `LifecycleTest` passes: every rule, in every game, in 110 seconds.

## What carries across the second oath

A probe, `SecondTerm` in the run's harness, compared each two-term game in the week before the second oath with the week after. These four games are the ones with two terms among the run's first 16:

| Game | Debt (% of GDP) | Laws | Justices appointed | Munitions (% of 2027) | Congress | Cabinet and staff |
|---|---|---|---|---|---|---|
| Full Game, seed 1 | 112 → 112 | 9 → 9 | 1 → 1 | 242 → 242 | unchanged | 30 → 26 (4 departures) |
| Governing Only, seed 2 | 113 → 114 | 9 → 9 | 1 → 1 | 261 → 261 | unchanged | 30 → 28 (2 departures) |
| Governing Only, seed 6 | 114 → 114 | 10 → 10 | 0 → 0 | 278 → 278 | unchanged | 30 → 26 (4 departures) |
| Full Game, seed 9 | 112 → 112 | 9 → 9 | 2 → 2 | 217 → 217 | unchanged | 30 → 26 (4 departures) |

The economy, the budget and debt, Congress, the laws, the Court and the military's inventories all carry across. The second oath changes only what it should: the departures, a renewed mandate, and a short honeymoon. Diplomacy is kept in the same world state, which the reload check covers.

## What makes the second term its own

- **A theme, chosen at the second transition.** It sets three legacy goals, which are judged at the end. The run saw, for example:
  - "Keep the White House": the midterms, approval of 50% or more, and the successor's win;
  - "Unfinished business": five more laws, a justice, and the midterms.
- **Departures at the second oath:** 2 to 4 of the cabinet and staff leave, and their posts are refilled.
- **The lame-duck decline:** 0 throughout the first term, 0.15 in June 2033 and 0.76 by June 2036. It weighs on Congress, the party and the press.
- **No third term:** the party's race to succeed the president runs the last year.
  - the primaries, with rivals splitting the party;
  - the president's endorsement;
  - the president campaigning from the White House;
  - a convention and debates;
  - the 2036 election night ("Your party keeps the White House" or "The White House changes hands");
  - the lame-duck weeks to the handoff.
- **The verdict of history judges all eight years.** The end page names "four years" or "eight years".

## Bugs this found (docs/BUG_FIX_REPORT.md)

| ID | Bug | Severity |
|---|---|---|
| B-02 | A crisis postponed the calendar's fixed dates: the 2030 midterms were held on December 23, and a crisis over July 19, 2032 would have moved the clock back | High |
| B-03 | A game that ended mid-crisis could be played on | Medium |
| B-04 | No election night for the races to succeed the president (2036, and 2032 without the president) | High |
| B-05 | The race to succeed the president was decided a week late | Low |
| B-06 | The successor's name lower-cased in the record ("Marco rubio") | Low |
| B-07 | A lost 2028 campaign graded as a "Failed presidency" | Low |

## Observations, not fixed (to review)

- **Many staff-run presidents step aside.**
  - **The cause:** with nobody answering it, the "Another four years?" question is answered by the chief of staff's advice, which is to step aside below 36% approval. The staff's presidencies hover around that.
  - **For a player,** the question waits on the desk for six weeks.
  - **The effect on the run:** the run sees fewer re-election campaigns than a player would.
- **Removal from office is common in staff-run games:** 19 of the 126 presidencies.
  - **The cause:** the staff do not fight an impeachment's politics the way a player can.
  - **Whether it is too frequent** for a player is a balance question for Milestone 9.
- **Weekly turns report a Tuesday election on the Monday after.** The midterms and the race to succeed the president are held on the turn whose days reach Election Day, so a weekly turn reports them the Monday after. The night itself is dated on Election Day. In a crisis's daily turns they come on the day.
- **The second oath on a weekly turn** also comes the Monday after January 20, 2033. The handoffs at the end are dated January 20 exactly.

## Reproducing

```bash
mvn -B -o test -pl mandate-core -Dtest=LifecycleTest
```

The build runs the first 16 games. The full run:

```bash
mvn -B -o test -pl mandate-core -Dtest=LifecycleTest -Dmandate.lifecycleGames=160
```

The counts by path in the table above, and the second-oath probe, came from the harness the test was written from, which prints each game's path. The test checks the same rules and fails on any game that breaks one.
