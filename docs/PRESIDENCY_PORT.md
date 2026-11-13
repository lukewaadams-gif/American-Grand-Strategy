# The presidency: porting Resolute Desk Version 2

This is the plan for porting Resolute Desk Version 2 (`lukewaadams-gif/resolutedesk`, HEAD `2ce2b37`) into the engine and the new interface. How the original works, with formulas and line numbers, is in `docs/research/RESOLUTE_DESK.md`. The campaign's equivalent is `docs/CAMPAIGN_PORT.md`.

## Decisions (approved 13 November 2026)

| Question | Decision |
|---|---|
| Turn length in office | **Weekly**, as in Resolute Desk: the president's 21-slot week, the weekly front page (Q29) and crises week by week (Q44). The chief of staff's delegation and watch mode keep the roughly 180 weeks moving. |
| The currency in Congress | **Version 2's hours on the Hill, favours and promises** replace political capital. |
| The first hundred days | **One loop.** Resolute Desk's systems run from the inauguration. Day 100 still produces its report card, and the 100-day game length still ends there. |
| Order of work | Audit and export Version 2. Then the Oval Office first screen, for approval from screenshots (Q39). Then the engine and the rooms. |
| Defaults | Real people (foreign and congressional leaders, news outlets) are fictional by default, with the real ones as an opt-in, as for candidates. Modern era only (2029–2033). Wars of choice go to the Situation Room world, which owns war (Q32). |
| The economy (13 November 2026) | **One of this version's deep systems, moved realistically by every law.** It will be a new structural model: fiscal multipliers that depend on slack and on the Fed's reaction, crowding out through interest rates, and long-run effects on supply, with Resolute Desk's sectors on top. |
| How laws act on it | Laws move **Resolute Desk's policy table**. Each policy level carries a full score: 10-year cost and how it is paid for, demand effects year by year, long-run effects on labour, productivity and investment, prices in the sectors it touches, and a phase-in. |
| What the player sees | **Scores with ranges** before every vote; the range is wider where the evidence is weak. The actual effect is drawn within the range, and the briefing book shows which laws moved which numbers. |
| Order | **The economy and the policy table come next (milestone 4)**, before Congress, so the laws Congress passes have their real effects from the start. |
| Defaults, revised (13 November 2026) | The game is no longer limited to 2029–2033: a re-elected president serves a second term to January 2037 (below). |
| A second term (13 November 2026) | **Its own milestone, after the world.** Winning in 2032 always carries on: the full-term game becomes "as long as you serve". |
| Between the terms | **A short second transition:** some secretaries leave and are replaced, the second-term agenda is set, and the second inaugural address sets its tone. |
| The second term's pace | **Weekly, as the first term**, with watch mode for quiet stretches. |
| Second-term pressures | **Lame-duck decline** (capital and pull with Congress fade as the end nears, faster after the 2034 midterms), **staff and cabinet turnover** (departures between terms and during the second; replacements less experienced or less loyal), and **legacy and the succession** (legacy goals that count in the verdict; rivals for the 2036 nomination split the party). No extra second-term scandal risk. |
| How a two-term game ends | **The president campaigns for the party's 2036 nominee** from the White House (trips, money, endorsements). The game ends at the January 2037 handoff. |
| A 2032 loss | **The lame-duck weeks are played** to the January 2033 handoff (pardons, last orders and rules, the lame-duck session of Congress), then the epilogue. |
| The verdict | **One verdict on the whole presidency**, across every term served: the economy, the laws and what they did, war and peace, approval, the Congresses faced, and a historians' ranking. |
| Midterms (2030 and 2034) | **Every Senate race, and the House state by state:** the real Senate seats up in each year, each with its candidates and polls; the House's 435 seats moved state by state, with retirements and open seats. |
| Midterm campaigning | **From the Oval Office calendar:** rallies and fundraisers as road trips, endorsements, and the party's war chest. No separate headquarters. |
| A new Congress | **Leaders, blocs and members change:** leaders follow control of each chamber, Resolute Desk's blocs grow or shrink, new members bring their own leanings, and retirements and primaries reshape the caucuses. |
| Divided government | An opposition chamber can **investigate and subpoena, impeach and try, force shutdown and debt-limit fights, and block nominees and judges.** |
| Working overnight (13 November 2026) | Each milestone is **finished fully** (tests and checks) before the next, then **committed, pushed and published to the preview**; the live site waits for approval. Choices not yet discussed are made in favour of Resolute Desk and realism and **listed in `DECISIONS_TO_REVIEW.md`**. Order: the presidency's milestones 5–10, then Island Chain and Hormuz on milestone 8's Situation Room (`CRISES.md`). |
| The Roosevelt Room | **Blocs, with their leaders:** Resolute Desk's blocs, each with a leader to meet, call and bargain with, whip counts by bloc, and the swing members named in each. Names are **fictional by default**, the real ones an option. |
| The West Wing | **Resolute Desk's orders, rules and courts:** orders and rules move the policy table one step with a legal risk; lawsuits, injunctions and Supreme Court rulings can overturn them; judicial nominations fill seats. |
| The Press Briefing Room | **Resolute Desk's press in full:** press conferences with questions, interviews, addresses and the State of the Union; fictional outlets with their own lean and tone; the party's wings and the opposition's strategy. |
| The Residence | **Rest, family and health:** evenings and weekends that restore stamina, family events and strains, the president's health and doctor's visits. |
| Voter groups | **In the country room:** approval by group from the 96-cell electorate beside the coalitions and states, with what moves each group. |
| The Situation Room | **Resolute Desk's world in full:** forty countries with relations, calls and summits, crises week by week, objectives, operations and covert action, trade and tariffs, standing deployments. |
| Sound | **Quiet room sound, off by default** (Web Audio), with a toggle in the menu. |

## How Resolute Desk's systems map onto the engine

| Resolute Desk v2 | Into | Notes |
|---|---|---|
| The president's week: 21 slots and 16 activities, stamina, stress, sleep, health, Air Force One, presets, delegation, watch mode | executive | Turns become weekly through the term. |
| Approval | politics | Resolute Desk's formula. State and group approval come from the game's 96-cell electorate, so there is one electorate. |
| Congress | congress | Hours on the Hill, mandate and honeymoon. 12 blocs plus named leaders, chairs and swing votes, with asks, favours and promises. Drafting, stages, whip deals, veto and override, the filibuster. |
| Budget | congress, economy | The request, 12 appropriations bills, shutdowns, the debt limit. |
| Executive | executive | 9 staff and 21 cabinet posts (skills, traits, morale, resignations, leaks). Confirmations, orders and rules, the courts, scandals and impeachment, the vice president's missions. |
| Press and news | politics, events | Press conferences and interviews, addresses and the State of the Union, posts, polls, outlets' tone, party wings, the opposition's strategy. Storylines and The Capital Ledger. |
| Economy and policies | economy, policy | Version 2's additions, and the 146-policy table with bill, order, rule and budget channels. |
| World | diplomacy | 40 countries, calls, invitations, summits and meetings, theatres, crisis decisions, operations and covert action, trade, alliances, statecraft. |
| Wars of choice | the Situation Room world | Not in the presidency port. |
| Midterms | elections | Resolute Desk's midterm model. The 2032 campaign uses the ported Battleground 2028 mechanics. |

**The rooms (Q25–Q31).** Resolute Desk's nine rooms all hang off the Oval Office:
- the desk opens the calendar, and the papers open The Capital Ledger;
- the windows show the country, and the briefing book shows the economy;
- the doors lead to the Residence, the West Wing, the Roosevelt Room and the Press Briefing Room;
- the globe leads to the Situation Room;
- the Cabinet Room holds policy.

On top of that:
- the desk's objects are the actions (Q28);
- a transition office comes first (Q31);
- the light through the windows follows the time of day and the season (Q30, new: Resolute Desk has none).

**The older interfaces.**
- The desktop interface is retired (Q3), and the old browser interface's code with it once the new presidency replaces it.
- The live site stays as published until the switch (Q38).
- The terminal game gets the new commands.

## Milestones

Each milestone goes to the preview.

1. **Audit and export** (done). The audit of Version 2 is in `SOURCE_AUDIT.md` (backlog B-54). `tools/RdExport.java` exports its data, with fictional stand-ins for real people.
2. **The Oval Office first screen** (done, approved). It has:
   - the room in SVG, ported from Resolute Desk's art and restyled;
   - the interactive desk;
   - the top bar, the week and Hill cards, and the ticker;
   - the lighting.
3. **Weekly turns,** with the schedule, health, watch mode and approval. On screen:
   - the calendar and The Capital Ledger;
   - the country through the windows;
   - the briefing book's economy.
4. **The economy and the policy table** (built, for review). A new structural model, with Resolute Desk's sectors on top. Every policy level is scored, with ranges, and the effects are credited to the laws behind them. On screen: the briefing book's sectors and laws, and the Cabinet Room. The design and what was built are in `ECONOMY_MODEL.md`.
5. **Congress and the budget** (built, for review). Bills move the policy table. Each Congress is seated after its election with its own leaders, blocs and members, so the game can face several. The 2030 midterms are race by race, and the president can campaign in them from the calendar. Divided government brings investigations, impeachment, shutdown and debt-limit fights; blocked nominees come with the nominations themselves, in milestone 6. On screen: the Roosevelt Room.
6. **The executive, the press and storylines** (built, for review), with the Senate's confirmations (an opposition Senate blocks nominees). On screen: the West Wing and the Press Briefing Room.
7. **The Residence and the country's groups.** Approval by group from the 96-cell electorate.
8. **The world.** On screen: the presidency's Situation Room.
9. **The second term.**
   - The lame-duck weeks after a 2032 loss.
   - After a win: the second transition and inauguration, then the second term to January 2037, with the 2034 midterms run on milestone 5's model.
   - The pressures: lame-duck decline, turnover, legacy and the succession.
   - The campaign for a successor in 2036, and one verdict on the whole presidency.
10. **The rest.** The transition office, sound (Web Audio), the audits and the final preview.

## The Oval Office (milestone 2)

The first screen of the presidency, for approval from screenshots (Q39). It opens at `preview/presidency.html`, with a save from week 1 of the term. `?save=saves/governing.json` opens September 2029 instead.

**The room** (`presidency/oval.ts`) is Resolute Desk's `OvalArt`, shape for shape in its 1600 × 800 frame.
- The toolbox (`presidency/paint.ts`) ports `ui/Art.java`. It includes Java's own random generator, so the plaster flecks, wood grain, floor planks, clouds, trees and dust fall exactly where Resolute Desk puts them.
- **Q27, the palette:** the creams are greyed a little, and the golds, drapes and rug more.
- **Q28, the desk:** its objects are drawn larger than in the original, because they are the actions. They are a tray of documents to sign, the agenda's folders, the pen set and a multi-line telephone.
- The president at the desk is the game's own portrait, without its backdrop.
- **Movement:** the flags sway, the globe turns and dust drifts through the sunbeams, at about 30 frames a second. All of it stops under reduced motion.

**The light** (`presidency/lighting.ts`, Q30, new):
- The hour comes from the player's clock (or `?hour=`, or the menu). The season comes from the game's date (or `?month=`).
- Sunrise, sunset and the noon sun's height follow Washington's by month.
- The windows face south. So the morning sun comes in from the left, the evening sun from the right, and the low winter sun reaches further into the room.
- **Through the windows:** a night sky with stars and the floodlit Washington Monument (with its red aircraft lights), a warm dawn or dusk, an overcast winter sky, bare trees and snow, April's blossom, and October's colours.
- **The room:** it darkens by a multiply blend, cool at night and warm at the ends of the day. The lamp, the picture lights and the ceiling's cove light then shine through by a screen blend. The outdoor colours are divided by the room's tint first, so the windows keep their own light.

**The screen** (`presidency/oval-screen.ts`) is Resolute Desk's `Oval` screen.
- **The top bar:** the seal, the president's name, the date, approval and its change, unemployment, inflation, gas and the week of the term. Stamina and stress have shown as meters since milestone 3.
- **The room's twelve hotspots:** the desk's three objects, the desk, the briefing book, today's paper, the windows, the globe and the four doors. Each is a real button laid over the picture, with Resolute Desk's gold label on hover and on focus.
- **The cards:** "This week" (decisions waiting and what is coming: the Fed, day 100, the midterms, Election Day) and "On the Hill" (the agenda's bills and their stages, and the House and Senate).
- **The Ledger** runs along the bottom. It has a pause button and stands still under reduced motion.

**What works now.**
- The documents open any waiting decisions.
- The folders, the briefing book and the paper show the engine's real agenda, economy and news.
- The other doors and objects say which milestone opens them.
- The End button plays the engine's current turn (a week, since milestone 3).

The engine's `presidency` view adds the turn's length and the dates that are coming.

`mandate-web/src/test/browser/presidency.js` checks the screen in Chromium:
- every hotspot by mouse, with its label, its dialog and focus coming back;
- the Tab order, the menu's light, and The Ledger's pause;
- three weeks and a month of turns;
- axe audits and the 12 px minimum in three palettes, with reduced motion and 125% text, at 1600, 1280, 1000 and 390 pixels wide;
- every hotspot at least 24 pixels from 1000 pixels up.

## The president's week, weekly turns and approval (milestone 3)

### The engine

**The president's week** is a new `presidency` domain (`PresidentEngine`, `PresidentState`), ported from Resolute Desk's `sim/Week`, `Schedule`, `Rooms.choose` and `executive/Delegate`.
- **The calendar:** 21 slots (seven days from Monday, morning, afternoon and evening) and the 16 activities, each with its stamina and stress.
- **The calendar's rules:** Camp David only at weekends, the Hill only on weekdays, working late only in the evening, no cabinet meetings at weekends or in the evening, campaign stops only in the hundred days before the midterms.
- **The ready-made weeks:** routine, road and rest, and clearing the week.
- **Air Force One:** Resolute Desk's flight rule (0.3 hours under 60 miles, otherwise 0.6 hours plus 480 mph; Hawaii 10.5 and Alaska 8.5). The cost of a trip, and the sleep lost on long flights, follow from it.
- **The president's condition:**
  - sleep from the evening slots;
  - stamina, stress, health and fitness, by Resolute Desk's weekly formulas;
  - age, which makes stamina costs heavier past 60 (`candidate_ages.csv`);
  - illness: a cold, or a night at Walter Reed.
- **The chief of staff** can write each week: a routine, a rest week when stamina is low, a swing-state trip every fifth week, a cabinet meeting every six.
- **The watch plan:** six priorities, six ready-made plans and seven kinds of stop, with Resolute Desk's watched week.

What a slot does to opinion leaves as effects for the politics engine:
- policy time builds command of an issue and standing on it;
- remarks make the issue matter more and move standing on it;
- trips and campaign stops raise standing in the state;
- ceremonies, family time and remarks add to the record;
- more than three fundraisers in a week cost some of it.

**Weekly turns.** Every turn in office is now a week. The first hundred days were already weekly, and the president's week runs in them too.
- The economy steps a week, with the Fed's meetings and the data releases that fall in it.
- The world and the military move on the first week of each month.
- Events keep their monthly odds, spread over the weeks.
- At the end of a war, governing resumes in weeks.

**Approval** is Resolute Desk's (`opinion/Approval.java`), in the politics engine.
- **The target** is built from:
  - the era's base (45);
  - the economy against what people expect (growth, unemployment, inflation, real gas and sentiment);
  - standing on the issues, weighted by salience;
  - the president's appeal and the difficulty;
  - the honeymoon (4 + 0.6 × the mandate, and the mandate is 2 + 0.55 × the popular-vote margin);
  - the record, scandal and a rally;
  - the wear of office after six months, and a slow random mood.
- **The squeeze:** the target is held within 2029's partisan floor and ceiling (34 and 66; a rally passes through the ceiling).
- **The weekly move:** approval moves 15% of the way to its target each week, plus noise.
- **The breakdowns:**
  - **The coalitions:** the prototype's three (base, swing and opposition) are its breakdown, solved by bisection so they add up to the national number. The rest of the game still reads them.
  - **The states:** their approval comes from each state's 2028 margin, the president's standing there and one national offset, as Resolute Desk does for its cells.

**The era's news.** Resolute Desk's ambient stories (`data/rd/ambient.tsv`) run once each: one or two a week, fewer on a busy week. A story can make an issue matter more, or rally the country. They go into a weighed news list (`News`) with the president's week's own news, and The Capital Ledger is made from both.

**Saves.** A game saved in office before this milestone is upgraded when it loads:
- it gets the routine week;
- it moves to weekly turns from the next Monday;
- it switches to Resolute Desk's approval, carrying on from where approval stood.

**Deviations from the original:**
- **Distances** are great-circle miles between state centroids, computed from the map data (`state_centroids.csv`); Resolute Desk measures its own map ×2.9.
- **Ratings.** Resolute Desk's attributes come from this game's five ratings:
  - speeches = (charisma + policy) / 2;
  - charm = charisma;
  - big donors = fundraising;
  - command at the start = 30 + policy × 0.2.
  - Establishment, crossover appeal and scandal baggage take Resolute Desk's neutral values (60, 60, 40).
- **Staff quality** counts as a steady 0.6 for every post until the West Wing's staff arrive (milestone 6).
- **Trips abroad** are listed but closed until the world arrives (milestone 8).
- **Banked for later milestones.** These are recorded now, and their systems arrive later:
  - **For Congress (milestone 5):** hours on the Hill, the Hill relationship, and campaign stops before the midterms.
  - **For the West Wing (milestone 6):** cabinet meetings.
  - **The party's war chest** is kept as well.
- **The issues** are this game's eleven. Resolute Desk's issue codes map onto them (ECON and JOBS go to the economy, PRICES to the cost of living, and so on).
  - The policy table's results on each issue wait for milestone 6.
  - Sentiment leaves out the stock market, which this game does not model.
  - The price level for real gas prices is kept by the politics engine.
- **This game's own events and laws** keep the size and lifetime they were tuned for. They enter the target as a separate term that fades 15% a week. Resolute Desk's record fades only 4% a week, and carrying our effects there made several laws in a row stack the target 9 to 15 points high. Lasting coalition effects stay lasting.
- **Data releases** no longer move approval on their own; the economy term reads the economy every week.
- **Attention** for the old governing commands (push a bill, an address) still refreshes monthly, until Congress and the West Wing replace those commands.
- **Removed:** the coalition model's own target, adjustment, noise and economic weights.

**Calibration.**
- **Approval:** twelve full terms played by the autopilot run from about 52 at the oath to a range of 41 to 61, and end the term in the high 40s and low 50s. Resolute Desk's own five runs gave 41 to 57.
- **Re-election:** the president won 3 of the 8 terms that reached 2032.
- **The hundred days' strategies** explain less of day-100 approval than under the old model, because Resolute Desk's approval moves gradually and carries its mood. `OpeningStrategyTest` now asks for 10% rather than 20%, and for two measures dominated by strategy rather than three.
- **Golden fingerprints** were regenerated on purpose.
- **Parity:** the parity check now plays six full terms, and the browser and the JVM agree to six decimals.

### The screens

- **The calendar.** The desk opens Resolute Desk's calendar: the week as a 7 × 3 grid of cards, each with its activity, what it is about, and its stamina cost including the flight.
  - **Each card's menu** lists every activity, with the reason it cannot go in that slot where it cannot.
  - **For an issue**, a second menu puts the issues that need attention first.
  - **For a state**, a second menu puts the swing states first, or the player picks on the trip map.
  - **Around the grid:** the ready-made weeks and the chief of staff's toggle.
  - **The week ahead panel:**
    - stamina now and after the week, sleep, stress and health;
    - a note on the cost of age, and a warning when the week would exhaust the president;
    - Air Force One's itinerary (long flights marked), and a map of approval by state with the week's trips;
    - last week's summary.
- **The Capital Ledger** opens after every week, and from the papers on the coffee table.
  - **The front page:**
    - the masthead and the edition ("Week 6 of the Marsh administration");
    - the week's heaviest Washington story, with the president's photograph and Resolute Desk's lead paragraph;
    - six more stories in three columns, with the paper's features filling the gaps: the week ahead, poll watch, markets (bonds and oil) and an opinion column;
    - In Brief.
  - **Beside the page:** By the Numbers.
  - **The log in the third person.** The game's log speaks to the player, so the paper rewrites it: "You sign the …" becomes "Marsh signs the …".
  - **Not yet:** the storylines' Developing Stories panel waits for milestone 5.
- **The country (the windows):**
  - approval, with its weekly line inside the partisan floor and ceiling;
  - what moves approval: its target taken apart, part by part (the economy, the issues, the honeymoon, the record, events and laws, wear, mood);
  - approval on each issue, by how much it matters, with command of it;
  - the three coalitions;
  - approval by state, as a map, the warmest and coolest states, and a table of every state.
- **The briefing book (the economy):**
  - nine figures, each with its change on a year ago (or since the oath) and its trend over the term: growth, unemployment, inflation, gas, the Fed funds rate, the 10-year Treasury, the 30-year mortgage, the deficit and consumer sentiment;
  - six charts over the term;
  - the next Fed meeting, jobs report and inflation report;
  - more figures: oil, debt, the output gap, the misery index, the last reports and the Fed's credibility.
  - Every chart has a crosshair readout, by mouse and by arrow keys, and its numbers in a table.
- **The top bar** shows stamina and stress as meters.
- **The week card** shows Resolute Desk's counts (policy slots, trips, recharge), stamina after the week, sleep, and what is waiting or coming.
- **Watch mode:**
  - **The plan:** a ready-made plan or six priorities, the kinds of decision to stop for, and three speeds.
  - **Each watched week,** the staff answers what the plan does not stop for (with the option it recommends), plans the week by the plan and plays it.
  - **It stops** when a decision needs the player, when the president is exhausted, or on Escape.
- **The first hundred days** can be handed to the staff from the agenda's folders, until Congress and the West Wing open in their rooms: the flagship bill, the cabinet, crises and the budget. Their decisions now appear with the events' decisions, each with the staff's recommended option.

## The economy and the policy table (milestone 4)

The full design, the calibration targets and what each step built are in `ECONOMY_MODEL.md`. In short:
- **The economy** is a structural model stepped weekly:
  - supply from productivity, capital and labour;
  - an output gap moved by rates, fiscal policy (CBO's multiplier ranges), trade, wealth and mood;
  - prices from six parts;
  - a Taylor-rule Fed;
  - a 10-year with a debt premium;
  - the budget as CBO keeps it;
  - Resolute Desk's markets, sentiment, sectors and shocks.
  It reproduces CBO's projection with no new policy and the note's other targets, each a test.
- **The states** have their own unemployment and gas, and both feed approval there.
- **The releases** run on their real days (jobs, prices, quarterly GDP, the Fed) and make the paper's economic news.
- **The policy table** is Resolute Desk's 146 policies. 110 of them have an economic score through the model's channels, with ranges by evidence; each law draws its luck when it passes, and every law keeps its credit.
- **This game's bills** are the policy steps they make: a law moves the table, and the public reacts by Resolute Desk's rule.

**On screen:**
- **The West Wing door** leads to the Cabinet Room: the table by area, each policy's levels, routes, evidence, politics and history, the score of a step either way, the game's bills as their steps, and what the moves have done beyond the economy. It only shows in this milestone: orders and rules arrive with the West Wing's staff (milestone 6), the budget with Congress (milestone 5).
- **The briefing book adds:** prices by part, the industries, the budget, and what each law has done (with the Fed's response as its own line).
- **The agenda's folders** show each bill's score before it moves.
- **The Capital Ledger** prints the budget office's verdict when a law passes.

## Congress and the budget (milestone 5)

**The engine** is a new domain, `hill`: Resolute Desk's Congress, beside the old seat-count model (which keeps the totals, the hundred days and the campaign's promises).
- **Seating.** The 121st Congress is seated at the inauguration from the 2028 result:
  - the Senate seat by seat (class I and III from the state data, class II filling the rest so the total is right);
  - the House by state (each delegation split by the state's lean, with a national swing found so the total is right);
  - Resolute Desk's twelve blocs, the era's four leaders, twelve chairs a chamber from the majority, and swing votes who each want something.
- **Bills.** The president drafts one of the game's bills or a single policy step (from the Cabinet Room), sets its size, scope, pay-for, reconciliation and first chamber, and sends it (three at a time). It moves stage by stage: committee (a hostile chair can bottle it), the floor (leaders hold a vote they would lose), the other chamber, conference, the desk. Votes are Resolute Desk's bloc model: a spatial vote, party loyalty, the relationship, approval, the cost, public support and the deals made on that bill.
- **Deals** cost hours on the Hill (from the calendar's Hill meetings): a call, a project, an appointment, an endorsement (a promise to visit), pressure, or a favour called in. Members ask for things; a promise not kept by a visit is broken.
- **The desk.** Sign, veto or let it become law; Congress can override. Every law moves the policy table and is recorded in the game's laws.
- **The majority's own bills** move every seven weeks.
- **The budget.** The request (due in June the first year, February after); twelve appropriations from May to September; at the end of September a stopgap, a stopgap on the other side's terms, or a shutdown, week by week until someone gives; the debt limit with an X-date about five months after it binds, and default as a financial shock.
- **Divided government.** An opposition chamber opens investigations and sends subpoenas; refusing is contempt; reports carry scandal and, if bad enough, grounds for impeachment; the House votes, the Senate tries. Removal ends the game.
- **Elections.** A hundred days out, the Senate class that is up gets candidates and polls (campaign stops from the calendar move them). On election day every Senate race is decided and the House is set state by state, with Resolute Desk's national total (222 Democrats at an even vote, 3.8 seats a point); the new Congress is seated with its leaders, blocs and members. 2032's Congress follows the presidential race.
- **The staff** can run the Hill (whip with the deals each member wants, answer the desk the safe way, send the budget). The autopilot and watch mode use the same rule, which is also the recommended answer on every card.
- **Pushes** from the old screens and the autopilot go to the Hill after the hundred days: a draft shaped by the strategy, then sent. Executive orders keep their one-step path until the West Wing (milestone 6).

**On screen:** the Roosevelt Room, through its door or the telephone (which opens on the members):
- **The Hill:** both chambers as bars of their blocs with the majority line, each bloc's relationship and leaning; the week's hours, appointments and mandate; handing the Hill to the staff; ending the filibuster; what is waiting; the record.
- **Bills:** each bill with the whip count in both chambers (expected yes against what is needed, the chance, by bloc), the draft's controls, working the votes, Congress's own bills, and what can be drafted.
- **Members:** the leaders (to meet), the chairs and the swing votes, with what each wants.
- **The budget:** the request's four dials and their effect on the deficit, the twelve spending bills, stopgaps and shutdowns, the debt limit.
- **Elections:** the Senate races with their polls, the House by state.
- **Favours and probes.**
- **The desk:** bills to sign, stopgaps, shutdowns, the debt limit, subpoenas and members' requests come as decision cards with the staff's answer marked.

## The executive, the press and storylines (milestone 6)

**The engine** is a new domain, `westwing`: Resolute Desk's executive, from day 100. The full specification it was built from is Resolute Desk Version 2's executive, press and stories packages.
- **The staff:** nine jobs, each held by a person with skill, loyalty, morale and a trait. Each job sharpens its own part of the game:
  - the chief of staff takes strain off the president and keeps leaks down;
  - the press secretary shortens scandals;
  - the counsel lowers legal risk and handles investigations;
  - the domestic policy, communications and political staff sharpen policy time, remarks and fundraising;
  - the legislative director brings hours on the Hill and makes deals stick.
  Morale moves with approval and stress. People resign, leak, and get caught in ethics stories. Hiring is from three people, firing has a cost, and the chief of staff can be handed the people.
- **The cabinet:** twenty-one departments, each with a secretary, readiness and a workforce.
  - Nominations go to a Senate vote by bloc. An opposition Senate sits on a weak nominee for up to twenty weeks; a rejection costs the president.
  - Readiness follows the secretary and cabinet meetings. A department below about 66 botches rollouts of orders, rules and laws; a botched law keeps three-quarters of its strength.
  - The Treasury, Defense and State secretaries now set markets', the military's and diplomacy's competence.
- **Orders, rules and emergencies:** Resolute Desk's forty-nine library actions; an order of the president's own (issue, side, reach, legal basis); and an order or rule moving one policy a step, from the Cabinet Room (at 60% and 85% of a law's strength). How they work:
  - Rules wait out a comment period.
  - Each draws the public's and the Hill's reaction.
  - The risky ones are sued.
  - A Congress the president's party holds neither chamber of can overturn rules and end emergencies.
- **The courts:** lawsuits go through the district court, the court of appeals and the Supreme Court. At each ruling the president can appeal, rewrite, let it go or slow-walk (defiance is impeachable).
  - The lower courts' balance shifts as judges are confirmed.
  - The Supreme Court has vacancies by age, a shortlist, and a confirmation vote (stalled in an election year by an opposition Senate).
  - The Court is fictional by default; the real 2025 Court comes with the real-person roster.
- **The vice president:** missions to tour the swing states, lead a task force, work the Senate, raise money or go abroad.
- **Scandals and pardons:** leaks, ethics stories, the president's past, botched rollouts and defiance. Each scandal is answered (release, deny, a resignation, a review), grows or fades, and may bring a special counsel. Pardon requests come every couple of months.
  - The impeachable ones push the Hill's impeachment, and Congress's investigations take up the worst of them.
- **The press:** eighteen outlets with their tone; press relations; press conferences and interviews, three questions each, scored by the president's skills against the outlet; addresses and the State of the Union (an opening, three passages, a close); posts; polls.
- **The party and the opposition:** each wing judges how close the record sits to it. The opposition picks a strategy every quarter (cooperate, pick its fights, obstruct), which moves relations on the Hill and the midterms.
- **Developing stories:** Resolute Desk's fourteen storylines for 2029-33. Each starts when its conditions hold, gathers heat, and grows or fades by stage, with a response at each stage whose odds depend on who carries it out. One that outgrows its last stage takes over the presidency.

**On screen:**
- **The West Wing** has six tabs:
  - **Staff:** nine cards, hiring from three, and handing people and pardons to the staff;
  - **Cabinet:** each department's secretary, readiness and workforce, with nominations and the Senate's count;
  - **Vice president**;
  - **Orders and rules:** the library, an order of your own, and what you've done, with rescinding;
  - **The courts:** the Supreme Court and its shortlist, the lower courts and the cases;
  - **Oversight:** scandals, Congress's investigations, impeachment pressure and the record.
  The Cabinet Room is down the hall; its policies can now be moved by an order or a rule.
- **The Press Briefing Room** has four tabs:
  - **The press:** press conferences, interviews, polls and how the outlets cover you;
  - **Speeches:** an address, and the State of the Union;
  - **Social media**;
  - **Your party:** the wings and the opposition.
- **The desk:** questions from scandals, leaks, courts, Congress's resolutions, pardons, press conferences, speeches and stories come as decision cards, with the staff's answer marked. Left past their week, the staff's answer is taken.
- **The Capital Ledger** gains Resolute Desk's "Developing stories" panel.

## Quality bar

These are the same as for the campaign:
- the command log, saves and determinism;
- golden fingerprints, regenerated on purpose when outcomes change;
- the browser and JVM builds playing the same games;
- axe audits, keyboard play, the 12 px minimum, three palettes and text scaling in every room.
