# The campaign war room

The war room is where both campaigns are run, 2028's and the 2032 re-election. The campaign's engine and its port from Battleground 2028 are in `docs/CAMPAIGN_PORT.md`. This page covers the war room's tabs and desks, as built on 14 November 2026.

## The left panel's tabs (keys 1 to 7)

| Key | Tab | What it does |
|---|---|---|
| 1 | Schedule | The candidate's week, three events a day, or a day at a time in the final stretch |
| 2 | Money | Cash, fundraising, headquarters, national TV, digital, the week's budget |
| 3 | Field | Offices, organisers, volunteers, transfers to the state parties |
| 4 | **Message** | The week's national message and its tone; plans and promises |
| 5 | **Polls** | The national trend, internal polling, the closest states, the national polls by group, age and sex |
| 6 | **Team** | The senior staff you hire; the surrogates on tour, and who runs each tour; the running mate |
| 7 | Wire | The news |

### Message

- **The issue.** Each week the campaign carries one national message. It moves the race by three things:
  - how much of voters' attention the issue has;
  - how far voters trust the player's party on it, in points (an issue a party owns, against one it does not);
  - how long the campaign has stayed on it: each week on message adds to its weight, up to a cap.

  The issues are listed by what they are worth to the player. The staff can pick the issue each week instead (the default).
- **The tone** (`CampaignOrder.MessageTone`). It is saved in the campaign's orders, only when it is not contrast.

  | Tone | The message's effect | Also | Risk |
  |---|---|---|---|
  | Positive | 0.8 | Your side's enthusiasm rises a little each week | None |
  | Contrast (the default) | 1.0 | — | None |
  | Attack | 1.3 | Their side's enthusiasm falls a little each week | About one week in seven the attack backfires: the week's message counts against you, twice over |

  The opponent's campaign stays on contrast.
- **Plans and promises** (2028). Unveiling a plan is a campaign promise (`Command.Pledge`):
  - it wins a little support, by the plan's appeal;
  - it raises its issue's salience;
  - if the player wins, it leads the agenda, and dropping it costs the base.

  Three promises at most (`campaign.pledge.max`). In 2032 the tab shows the 2028 promises: a re-election runs on the record.

### Polls

- **The national trend,** and how much public polls miss by.
- **Internal polling** (`CampaignOrder.InternalPoll`):
  - an internal poll is the true margin with an error of about a point (0.8 for the nation, 1.1 for a state). The public polls carry a hidden miss and noise of several points;
  - it costs $120,000 for a state and $300,000 for the nation, from the campaign's cash;
  - each state, and the nation, can be polled once a week;
  - the results are listed beside the public number, newest first, and are saved.
- **The closest states,** with their change since the last poll.
- **The national polls by group, age and sex,** from the electorate model's crosstabs, with the public polls' noise.

### Team

- **The senior staff.** Five posts. Each holder's skill (1 to 10) makes their part of the campaign work harder, by 3% a point from the default staff's 5:

  | Post | What it moves |
  |---|---|
  | Campaign manager | The message's momentum |
  | Pollster | The internal polls' error: 7% smaller a point |
  | Media director | TV and digital |
  | Field director | Volunteers' and organisers' contacts |
  | Finance director | Every fundraiser, call-time session, small-dollar gift and surrogate's donor event |

  - **The cost of a hire:** a weekly salary, and a signing fee of two weeks' pay (`CampaignOrder.Hire`). The default staff cost nothing extra.
  - **The pool** (`data/campaign_staff.csv`) holds well-known senior operatives of recent presidential campaigns (with the real cast only) and fictional hires.
  - **Real people get a skill rating only,** labelled as the game's judgement for play, and never any risk.
- **The surrogates.** The running mate, your spouse, and two allies: the party's most charismatic figures from the same cast, not on either ticket (`CampaignStaff`).
  - **A week's tour** (`CampaignOrder.Surrogate`): a state, raising money, or nothing.
  - **Moderates persuade** (visits and the local image). **Firebrands mobilise** (volunteers and enthusiasm).
  - **Their pull is their charisma.** The running mate's counts a fifth more. It is 1.3× in their home region and 1.8× in their home state.
  - **A tour is run by an advance lead.** The lead's skill multiplies the stop. A lead's risk is the weekly chance of a mishap (a double-booked stop, a bad local story). Only fictional junior staff carry risk.
  - **The staff plans the tours by default** for both campaigns: the most valuable states, a home-region state when it is nearly as good, and an ally raising money when cash is short. An order of the player's own hands the tours over.
- **The running mate** (2028): chosen from the party's strongest eight (`CampaignOrder.RunningMate`). Otherwise the strongest is named on the campaign's first turn. An ally named running mate leaves the allies, and the next of the party joins them.

## The state desk's tabs

When a state is picked on the map, the right column shows its desk: Overview, Ads, Field and **Voters**.

### Voters

The state's electorate by bloc, from the 96-cell electorate model (`CellElectorateModel.stateGroups`):
- each group's share of the vote;
- the player's lead with it now;
- its change since 2024;
- its turnout;
- how movable it is, against the state as a whole.

The groups are shifted together so their sum matches the state's public poll: the desk reveals nothing the polls do not. Two lines under the table name the groups with the most to win by persuasion, and the player's side's least likely voters.

## The national column

When no state is picked, the right column shows the matchup, the national race and what moved it. While running for re-election, a **White House** card comes first (`docs/MODES.md`, "The 2032 campaign"):
- the record (approval, growth, unemployment);
- what the record and the campaign are each worth in the race;
- the White House's questions waiting;
- the doors to Congress, the West Wing, the Situation Room and the press room.

## Tests

- `CampaignTeamTest` (engine):
  - each ticket's running mate and allies: their party, their cast, not on a ticket;
  - the staff's tours;
  - a running mate chosen, a hire (its fee, the party and cast rules), a tour by hand: saved and replayed;
  - a hired manager moving the race.

- `WarRoomDeskTest` (engine):
  - internal polls: close to the truth, paid for, once a week, saved and replayed;
  - the tone: saved, and changing momentum and both sides' enthusiasm;
  - a state's voters: they add up to its poll.
- `warroom.js` (browser):
  - the Message tab: the issue, the tones, plans to the limit;
  - the Polls tab: internal polls, once a week;
  - the Voters desk;
  - keys 4 and 5;
  - at 1600 and 390 pixels wide, in the other palettes and at 125% text (axe-core WCAG 2.1 AA, text size, sideways scroll, nothing wider than its column).
- `office2032.js` (browser): the White House card.
