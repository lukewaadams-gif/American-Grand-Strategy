# The Taiwan crisis's balance

How the Taiwan crisis was made to turn on the president's orders, and how that was measured. It covers the crisis in Individual Crisis and inside the Full Game; Island Chain on its own plays the same model. The model itself is in `docs/ISLAND_CHAIN.md`.

## What was asked

These are the owner's answers of 14 November 2026:
- **The orders dominate.** A skilled president can hold Taiwan even in the pessimistic case. A passive one can lose it even in the optimistic case. The preset sets the conditions.
- **A typical first-time player loses about 1 time in 3** on the base case.
- **Diplomacy alone sometimes holds Taiwan:** in the optimistic case, or when deterrence and allies were built up before the crisis. Otherwise it usually fails.
- **Taiwan is lost** when:
  - an invasion succeeds;
  - a blockade forces Taipei to accept Beijing's terms;
  - America backs down.

## What was wrong

These are from `TaiwanBalanceRun`, before the change.
- **The staff, left alone, never lost on the base case** (15 or 16 won of 18).
- **The pessimistic case ended in part whatever the president did.**
- **Hawkish orders did worse than the staff, and early preparation did too.**
- **"Diplomatic only" was overruled.** When China struck American forces, the US entered the war anyway.
- **"Negotiated ceasefire" counted as an end in part,** even when Washington accepted it because its public support had collapsed. Most wars ran 75 days and were then judged on a knife-edge of territory and electricity, so small differences flipped the result.
- **Individual Crisis started from the same sample save every time.** With the same orders, it was always the same crisis.

## What changed

Each change is in the war model (`mandate-core/.../taiwan`).

1. **America backs down (outcome 10, a loss).** These end with a new outcome, "America backs down":
   - a ceasefire in place agreed while the PLA holds more than 1,500 km² of Taiwan;
   - unification talks;
   - Washington accepting a ceasefire as its public support collapses, while the PLA holds ground or the blockade is unbroken.

   The rule is in `TaiwanWorld.ceasefireOutcome` and `TaiwanWorld.intact`. Strike damage alone is not Beijing's to keep.
2. **A blockade still choking Taiwan at eleven weeks forces terms.** If the plan is a blockade or quarantine, and at the day-75 judgement electricity is below 45% and shipping below half for ten days, Taipei accepts Beijing's terms (outcome 6, a loss). It was "Partial PRC success" (an end in part).
3. **Beijing keeps America out when Washington says it will not fight.** If the president holds the rules of engagement at "Diplomatic only", China does not strike US forces or Japan at H-hour, and its deterrence calculation drops by 8 points. Without American help, Taiwan:
   - gives way within about a week against an invasion;
   - holds about two months against a blockade.
4. **The world's economic front against a blockade.** Sanctions weigh on Beijing's stability during a blockade or quarantine (`TaiwanWorld.coalition`):
   - America's own measures (sanctions at three levels, SWIFT, the asset freeze, the chip embargo, a full embargo);
   - each partner that joins (the EU 7, Japan 5, the rest 3).

   The weight is 0.6, up to 28 points. Below a stability of 40, each day Beijing may lift the blockade ("China stands down", a win) with a chance of (40 − stability) / 500. This is how diplomacy without force can still save Taiwan, against Taiwan's own endurance.
5. **Beijing's resolve varies from crisis to crisis** (`TaiwanAI.resolve`).
   - The go/no-go threshold moves by up to ±9 points, drawn once from the plan's seed.
   - It is 5 points bolder in the pessimistic case, and 3 points less bold in the optimistic case.
   - However well Washington prepares, a determined leadership sometimes goes ahead.
6. **The president's staff waits for clearer warning** (`TaiwanAI.STAFF_CAUTION`). When the US is the player's side, the staff starts its preparations one warning level later than a computer-run America does. The major preparations are the president's to order. The crisis room says so.
7. **Each Individual Crisis is its own.**
   - The start screen sends a seed with `Command.IndividualCrisis` (crisis, preset, seed).
   - The seed varies China's plan, timing and options, and every random stream of the war (`TaiwanEngine.vary`, `DeterministicRandomManager.perturb`).
   - The seed is in the command, so the save replays the same crisis.
   - Seed 0 (older saves, the tests' old calls) is the crisis the save itself gives.

## How it was measured

`TaiwanBalanceRun` (a test, off by default) plays Individual Crisis many times:
- from governing presidencies of several worlds;
- on each preset;
- with a number of seeds;
- by a style of play each time.

It counts won, lost and ended in part. The styles are these:

| Style | What the president does |
|---|---|
| Staff | Leaves the crisis room to the staff, and takes the staff's recommendation on every question (the typical first-time player) |
| Patient | Starts the major preparations on day one; asks Taiwan to mobilise and disperse; courts allies for basing; imposes sanctions early. At war: no mainland strikes, convoys, submarines, anti-ship air priority, rationing in a blockade, and partners asked to sanction. The staff keeps the rules of engagement |
| Hawk | Prepares everything and goes to war at rung 4 with mainland strikes |
| Diplomacy only | Holds the rules at "Diplomatic only". Courts allies; at war, every economic measure, partners asked to sanction, Taiwan asked to ration |
| Diplomacy with preparation | The same, after starting the deterring preparations (submarines, bombers, munitions, arms, advisers, reserves) |
| Back down | The staff, but proposes a ceasefire in place after four days of war, and accepts any offer |

Run it with `-Dmandate.taiwanBalance=N -Dmandate.taiwanVariants=M` (N worlds, M seeds each). `-Dmandate.taiwanStyles=` and `-Dmandate.taiwanPresets=` narrow it. `TaiwanBalanceRun#trace` (`-Dmandate.taiwanTrace=true`) prints one crisis day by day.

## The results (14 November 2026, the final code)

Six worlds, six seeds each: 36 crises per cell. The figures are won / lost / ended in part.

| Style | Base case | Optimistic | Pessimistic |
|---|---|---|---|
| Patient | 35 / 1 / 0 | 36 / 0 / 0 | 32 / 1 / 3 |
| Staff | 19 / 13 / 4 | 25 / 9 / 2 | 11 / 21 / 4 |
| Hawk | 23 / 13 / 0 | 29 / 7 / 0 | 20 / 15 / 1 |
| Diplomacy with preparation | 10 / 23 / 3 | 16 / 18 / 2 | 7 / 25 / 4 |
| Diplomacy only | 7 / 25 / 4 | 16 / 19 / 1 | 4 / 28 / 4 |
| Back down | 22 / 1 / 13 | 28 / 0 / 8 | 4 / 6 / 26 |

**Against the targets:**
- **The orders dominate.** On the base case, the patient president holds Taiwan 97% of the time; the president who will not fight holds it 19%.
- **The patient president holds 89% even in the pessimistic case.** The staff, left alone, loses 25% even in the optimistic case.
- **The staff loses 36% of the time on the base case:** about 1 in 3.
- **Diplomacy alone holds 19% on the base case, 44% in the optimistic case and 11% in the pessimistic case.** Prepared diplomacy does better on the base and pessimistic cases (28% and 19%).
- **Escalating to mainland strikes costs public support.** A long war fought on American initiative wears it down after three weeks. The hawk loses as often as the staff on the base case.
- **"Back down" rarely loses, because Beijing rarely accepts its offer.** Its war then runs on under the staff, ending in part more often (36% on the base case). When Beijing does accept with the PLA holding ground, that is a loss: the unit test pins the rule.

Before the change, for comparison (18 crises per cell, no seeds), on the base case:
- the staff won 15 of 18;
- the hawk ended in part 15 of 18;
- diplomacy only lost 15 of 18 (and lost every optimistic case).

## The tests that keep it

- `TaiwanOutcomeTest.theOrdersDecideTheOutcome`: on the same seeds, the patient president holds Taiwan and diplomacy alone mostly loses.
- `TaiwanOutcomeTest.theSeedVariesTheCrisisAndTheSaveReplaysIt`: seeds give different crises; each save replays; seed 0 is unchanged.
- `TaiwanTest.aCeasefireThatLeavesBeijingItsGainsIsAmericaBackingDown`: the ceasefire rules.
- `TaiwanTest.calibrationAgainstTheSourcesHarness`: unchanged ranges. The computer-against-computer runs do not use the president's staff caution.
