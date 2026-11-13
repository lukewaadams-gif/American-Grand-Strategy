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

1. **Audit and export.** Audit Version 2 into `SOURCE_AUDIT.md` (backlog B-54). Write `tools/RdExport.java`, which exports policies, bills, blocs and members, staff and cabinet pools, orders, outlets, countries, storylines and crises, with fictional stand-ins for real people.
2. **The Oval Office first screen,** for approval from screenshots. It has:
   - the room in SVG, ported from Resolute Desk's art and restyled;
   - the interactive desk;
   - the top bar, the week and Hill cards, and the ticker;
   - the lighting.
3. **Weekly turns,** with the schedule, health, watch mode and approval. On screen: the calendar and The Capital Ledger.
4. **Congress and the budget.** On screen: the Roosevelt Room.
5. **The executive, the press and storylines.** On screen: the West Wing and the Press Briefing Room.
6. **The economy and the policy table.** On screen: the Economy and Country views, the Cabinet Room and the Residence.
7. **The world.** On screen: the presidency's Situation Room.
8. **The rest.** The transition office, sound (Web Audio), the audits and the final preview.

## Quality bar

These are the same as for the campaign:
- the command log, saves and determinism;
- golden fingerprints, regenerated on purpose when outcomes change;
- the browser and JVM builds playing the same games;
- axe audits, keyboard play, the 12 px minimum, three palettes and text scaling in every room.
