# Product vision: *Mandate* (working title)

> Win the White House in 2028. Govern through 2032. Then face the voters again, on a record of your own choices.

## 1. The one-sentence game

A single-player American political strategy game. One connected simulation carries the player through a presidential campaign, a four-year term with a real Congress, economy and foreign crises, and a re-election fight. Every promise, law, deal and crisis response from the first two acts shows up on the map in the third.

## 2. Why this game, and why from these three codebases

The three source games each prove one third of the idea:

| | Battleground 2028 | Resolute Desk | First Island Chain |
|---|---|---|---|
| Proves | The campaign: a credible electorate model and campaign machinery | The job: Congress, the economy, the executive, crises | War: a serious operational model of the most likely great-power crisis |
| Fails at | Governing (a quarterly mini-game) and outcome variance (the Democrat won 40 of 40 batch games) | Consequence (most systems are dials that return to the mean; approval stays between 45 and 50) and focus (25 eras and 108 turning points) | Fitting into a presidential game (hourly turns, a 1,600×900 map UI, a 75-day war) |

The commercial opportunity is the **connection** between them, not the sum. No current game does campaign → governing → re-election with one electorate underneath all three:

- **The Political Machine** covers campaigns only, in an arcade style.
- **Democracy 4** has policy sliders, abstract elections, and no Congress or map.
- **Suzerain** is narrative and fictional, with scripted consequences.
- **Power & Revolution** is broad geopolitics without depth in American institutions.

Our niche: *the American presidency as a full strategic arc, with institutions that push back.*

## 3. Design pillars

Every feature must serve at least one pillar. A feature that serves none gets cut, however much code already exists for it.

1. **Your promises come due.** The campaign creates obligations: an agenda, coalitions, a mandate that is big or thin. Governing either fulfills them or breaks them, and the re-election electorate remembers. *Test: can a player trace their 2032 result back to a decision made in 2028?*
2. **Scarce attention, not busywork.** A president's scarcest resource is attention. Each governing month gives the player three attention points and a handful of high-stakes choices. A 21-slot weekly calendar for 208 weeks does not survive this pillar. *Test: does every turn contain at least one decision the player is unsure about?*
3. **Show the odds, own the gamble.** The whip count, the event odds, the forecast and the polls (with honest error) are always visible before the player commits. Losses feel chosen, not random. *Test: after a defeat, can the player say what they would do differently?*
4. **Preparation decides crises.** Taiwan, recessions and shutdowns are telegraphed through escalation ladders and indicators. Deterrence, munitions, alliances and fiscal room built earlier decide how a crisis ends. (In Resolute Desk, war odds ignored deterrence. Here deterrence is the main input.) *Test: do two players who prepared differently get visibly different wars?*
5. **Real institutions, fictional people.** We simulate the real filibuster, reconciliation, the Senate map and the Electoral College, but the people are invented. This protects us legally, keeps the game playable from either party, and stops the game from feeling like it takes sides on real politicians.

## 4. Target player and product shape

- **Primary audience:** PC strategy players who follow politics. They play Paradox titles, *Democracy*, *Twilight Struggle* and *Political Machine*, and they watch election-night coverage. Typically 25-55.
- **Secondary audience:** politics enthusiasts who are not core strategy gamers. They are served by the Story difficulty, delegation ("let your chief of staff handle it") and a strong tutorial.
- **Platform:** Windows, macOS and Linux desktop through Steam; Steam Deck verified as a stretch goal.
- **Price:** about $24.99, with a 1.0 launch preceded by roughly six months of Early Access.
- **Session:** a full run (2028 to 2032) takes 6-9 hours. The 2028 general election alone is about 60-90 minutes, so a player who loses still had a complete evening.
- **Replayability:**
  - Two parties and a fictional cast of eight or more nominees with distinct strengths.
  - Scenario environments.
  - Congress after the election, which differs every time.
  - Seeded world events, including a Taiwan ladder that may or may not escalate.
  - Difficulty levels and a legacy score with visible components.
  - Post-1.0: a primaries start, user scenarios, modding.

## 5. The arc of a playthrough

| Act | Time span | Turn | Core decisions | Hand-off to the next act |
|---|---|---|---|---|
| I. The general election | Jul 17 → Nov 7, 2028 | Weekly (16 turns) | Where the candidate goes; fundraising vs. spending; ads (fast, they fade) vs. field (slow, it lasts); the message; three debates (read the opponent, choose a posture) | The EV margin sets the mandate (starting capital and approval). Coattails set Congress. The issues you ran on set public expectations. |
| Transition | Nov 2028 → Jan 2029 | One turn | Three agenda priorities. Post-MVP: cabinet picks. | Agenda bills get whip help; unfulfilled agenda items cost you in 2032 |
| II. Governing | Jan 2029 → Jul 2032 | Monthly (~42 turns) | Three attention points a month: push a bill (party line, bipartisan deal, reconciliation or executive order) with political capital; the bully pulpit; Pacific deterrence; diplomacy; respond to events and crises | The economy, approval, laws, the Taiwan outcome and the midterm Congress |
| Midterms | Nov 2030 | Automatic, with a pre-midterm decision in 1.0 | Post-MVP: where to campaign and what to run on | A divided Congress changes which strategies work |
| III. Re-election | Jul → Nov 2032 | Weekly | As in Act I, but the national environment is now your record: approval, growth, inflation, promises kept, wars | Election Night 2032 |
| Epilogue | | | | Legacy score with its components shown, plus a history of the term |

## 6. What we are deliberately *not* building

These are design decisions, not missing features:

- **Not a history game.** Resolute Desk's 25 eras, 108 turning points and 94 historical bills are excellent content for a different product, and they multiply testing cost. The commercial game is 2028-2032. A historical mode is a possible standalone expansion (see roadmap E3).
- **Not a real-person satire.** No real politicians in the shipped game. A user-modding path may come later, with clear terms.
- **Not an operational wargame by default.** Most players want to decide *whether and how* America fights, not to manage carrier air wings hour by hour. The detailed First Island Chain engine becomes an optional **Command Mode** expansion (E1), behind a clean contract (`TheaterScenario` → `TheaterOutcome`).
- **Not maximal simulation.** We cut detail that does not change a decision, such as 21 calendar slots, nine individually rated staffers, and 3,100 counties rendered on election night. Simulation credibility comes from correct *institutions* and honest *uncertainty*, not from row counts.

## 7. Challenges to our own design (to resolve through playtesting)

1. **The middle of the term is the riskiest stretch.** Campaigns have a built-in climax; months 8-20 of a presidency may not. Mitigations: the Taiwan ladder, the midterm, scheduled flashpoints (the budget, the debt limit, Supreme Court vacancies) and agenda pressure. *This is the top design risk (RISK_REGISTER R-06).* Playtest it before building anything else in Act II.
2. **Losing in 2028 ends the run.** That is honest, and roguelike-adjacent players accept it, but some players will feel cheated after 90 minutes. Options to test: an "Opposition leader" mini-act, or a quick-restart with the same seed. Not MVP.
3. **"Even" balance versus realism.** A real 2028 environment may not be 50/50. We default to a Toss-up scenario so either party can win, and offer realistic environments as scenarios.
4. **Political sensitivity.** Players across the spectrum must feel the simulation is fair to their side. Issue-ownership numbers, bill effects and event framing get a bipartisan review pass. Both parties' agendas must be capable of producing a good economy.
5. **Does the war belong in a presidency game at all?** We think yes, as a *preparation and decision* problem. The detailed fighting is optional. If testers find the Taiwan ladder dominates every playthrough, we lower its frequency (the batch tool reports it).

## 8. Success criteria

- **Design, measured with the balance batch (`mandate batch`) on the Toss-up scenario:**
  - 2028 win rate per party within 45-55%.
  - Re-election rate 50-70%.
  - The Taiwan war reached in 5-15% of terms.
  - No single strategy dominates. The test for that: no AutoPilot variant beats the baseline by more than 10 points.
- **Players:** a median Early Access session of 2 hours or more; at least 40% of players who start a term finish it; Steam reviews at least 85% positive.
- **Commercial:** 40,000 wishlists by Early Access launch; recoup the development budget within 12 months of 1.0.
