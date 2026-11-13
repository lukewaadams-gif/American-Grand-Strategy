# The campaign: Battleground 2028's schedule, ground game and air war

This is the port of Battleground 2028's three core campaign mechanics into the engine: the candidate's schedule, field offices and the ground game, and the air war with the Ad Studio. How they work in the original, with formulas and line numbers, is in `docs/research/BATTLEGROUND_2028.md`. This note records what was decided and where the port differs from the original.

## Decisions (approved 13 November 2026)

| Question | Decision |
|---|---|
| Money model | Battleground 2028's: HQ payroll by tier, the small-dollar list, call time, fundraisers that tap out big donors, and its general-election starting money. |
| What the opponent plans from | Its own reading of the **public polls**. BG's opponent reads the true margins; this one never does. |
| The last two weeks | **One day per turn**, as in BG: from the Monday two weeks before Election Day (23 October 2028, 18 October 2032). |
| Order of work | Engine first (this milestone), then the screens: layout and Schedule, Field, ads and the Ad Studio, then the daily stretch and audits. |

## What was ported

| Mechanic | Where | Notes |
|---|---|---|
| The week: 21 slots, 16 activities, travel, stamina, gaffes, debate prep | `campaign/CampaignEngine.execute`, `Activity` | Costs, fatigue and effects from BG l.1658–1690 and l.3559–3685. BG's "Endorsement meeting" is left out (no endorsers here). |
| Fundraising | `CampaignEngine.fundraising`, fundraiser and call-time events | Small-dollar list ×$0.22 a name per week (×5 in the general, ×1.35 in the last 7 weeks), donor saturation. |
| Offices, organisers, volunteers, contacts | `CampaignEngine.field` | Office capacity 8 organisers (extra at 40%), state-party transfers, recruitment and churn, shifts, doors/calls/texts/relational contacts, persuasion and GOTV stocks. |
| TV markets, national TV, digital, super PACs | `CampaignEngine.advertising`, `superPacs`, `AirWar` | 148 markets (103 DMAs and 45 "rest of state" markets) exported by `tools/BgExport.java`; cost per point by size, season, competition and the lowest-unit-rate window; 6 platforms × 5 messages. |
| Ad Studio | `CampaignEngine.produceAd`, `testAd`; `AirWar.Ad` | Hidden quality, $45K focus groups (noisy read, 8 groups, 3 quotes), a library of 8, one ad on the air. |
| The planner (the opponent and the player's staff) | `ai/CampaignAi.planOrders` | BG's `AI.schedule`, `AI.budget` and `Digital.aiPlan`, steered by the existing strategy presets. |
| Commands | `campaign/CampaignOrder` in `Command.Campaign` | Slot, auto-fill, clear, staff toggles, airtime, national TV, digital and its mix, field orders, transfers, red boxes, HQ tier, message, pull ads, and the Ad Studio. |

## How the stocks reach the vote

- BG adds a campaign term (logit) to each voter cell, multiplied by the cell's elasticity. This game's 96-cell electorate (the same cells, exported from BG) applies its local shift in the same way, `(nationalSwing + localShift) × k × elasticity`. So a BG term of x logit becomes a local shift of x / k points.
- **TV, visits, field persuasion, local image and home state** are carried over exactly.
- **Digital persuasion** differs by age group. It is averaged with each state's age weights in the swing (`CellElectorateModel.ageSwingWeights`).
- **Group image** (union halls, churches, communities, campuses, podcasts, crossover media, targeted ads) is national in BG. Each state feels it in proportion to that group's weight in the state's swing, as exported in `campaign_states.csv`.
- **Get-out-the-vote and enthusiasm** change who turns out. `CellElectorateModel.turnoutShift` converts them into a margin shift using BG's turnout formula, at the state's baseline preferences.
- **Favourability, issue credibility and attacks that stick** are national terms (`CampaignEngine.nationalTerm`), added to the campaign's momentum.

## Deviations from the original

- **Money is in $M.** Prices are general-election prices throughout (this game has no primaries).
- **Podcasts:** BG names 28 real shows. Here each show keeps its tier, typical views, audience lean and audience groups, but is described rather than named.
- **Candidate ratings.** BG rates 21 attributes; this game rates five, from 1 to 10. BG's scale is derived as `v = (r − 1) × 100/9`:
  - speeches and long-form = (charisma + policy) / 2;
  - retail, volunteer energy and virality = charisma;
  - authenticity = (charisma + discipline) / 2;
  - stamina = 50 + discipline / 4;
  - crossover 50, establishment 60, home-state pull 60 (BG's typical values).
- **Name recognition** is fixed at BG's summer-start level for nominees (86%). It doesn't grow.
- **BG's momentum** drives crowds and fundraising, but it is not a vote term. It is kept separately as `buzz`; this game's message momentum is unchanged. Attack lines are collapsed into a single stick.
- **Timing of staff plans.** The staff plan at the start of each week (BG plans at resolution), so the player sees the staff's plan and can edit it. Where the player has taken an area back from the staff, their orders stand.
- **Daily turns.**
  - Event odds per turn are scaled to a day.
  - The state party's hiring lag and the four-week spending figure decay per week. BG applies them per turn.
- **Debates.** Preparation adds `min(40, prep) × 0.028` to the debate rating (BG adds `min(40, prep) × 0.28` to each exchange on its 0–100 scale). Preparation falls to 60% after a debate, as in BG.
- **The opponent's HQ tier** compares cash with a standard payroll, so it doesn't flip between tiers week to week.
- **Not ported, as BG's field-director role has no effect:** named staff, surrogates, the running mate's stops, buying internal polls, frames and attack lines. These are the second pass.

## The new interface (preview)

The campaign screen follows Battleground 2028's headquarters layout, in the war-room look.

**Left: the campaign's tabs** (keys 1, 2, 3 and 6). Message and Polls come later.
- **Schedule:**
  - the week's 21 cards, each with its activity menu (costs, and the stamina each costs this candidate);
  - a state button that moves an event (click it, then a state on the map);
  - show, congregation and community choices;
  - long same-day flights marked;
  - auto-fill, clear and the scheduler toggle;
  - stamina after the turn, event costs and debate preparation.
- **Money:**
  - cash and its flow, the donor base and the headquarters tier;
  - national TV and the digital plan (platform shares and messages);
  - this week's budget, the pull-all buttons, the ad-team toggle and the Ad Studio.
- **Field:** the whole programme, contacts, doors turn by turn, the ground game by state, and the field-director toggle.
- **Wire:** the news.

**Centre:**
- **The map**, with layers for Polls, Change, Win chance, Spending, Volunteers and Visits.
- **A right-click on a state**, or the context-menu key, sends the candidate there.
- **The race for 270** and **the path to 270** sit under the map.
- **After each turn**, BG's "Week in review" (or the day's) appears over the map.

**Right:** a picked state's desk, or the national picture.
- **Overview:** polls, chance, the two campaigns' money and visits, and "Send … here".
- **Ads:** every TV market that reaches the state, with its price, reach, buy and tone; the state's digital spending; and the red box for the super PACs.
- **Field:** offices, organisers, transfers, the week's ground game, and persuasion and GOTV against their ceilings.

**The Ad Studio** is a dialog: make an ad, test it with a focus group, and put it on the air.

`mandate-web/src/test/browser/preview.js` checks all of this in Chromium (see its README). The preview also ships `saves/daily.json` (25 October 2028), for the final stretch: `campaign.html?save=saves/daily.json` (the campaign-only page; the preview's front page is now the whole game).

## The older interfaces

- The Swing desktop UI, the current browser site and the CLI keep working until the new interface replaces them.
- `Command.PlanCampaign` (a hand-built week) becomes orders for that week:
  - the rallies go on weekday afternoons and the fundraisers in the nearest donor hub;
  - the ad money becomes digital spending in each state;
  - the field money becomes organisers, plus offices to hold them.
- `Command.SetCampaignOrders` (the strategy presets) is the staff's brief.

## Tests

`CampaignTrailTest` (16 tests) covers:
- costs, stamina and travel;
- donor hubs;
- auto-fill and the staff toggles;
- offices and organisers;
- GOTV only in the last six weeks;
- market reach and cost, and prices rising toward November;
- the Ad Studio;
- the 15 daily turns, and refusals for days already over;
- the opponent's plan;
- command-log round trips for every order;
- saves and exact replays;
- the size of the campaign effects.

The golden fingerprints were regenerated on purpose, because campaign outcomes changed.
