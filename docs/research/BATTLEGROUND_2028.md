# Battleground 2028: the schedule, the ground game and the air war, from the code

The source is a single file, `/home/user/src/Battleground2028/Battleground2028.java` (19,572 lines, HEAD b678c75). Line numbers below refer to that file. It was only read, never changed.

**The weekly order** (`Week.resolve`, l.3436):
1. The AI campaigns plan (`AI.decide`).
2. The player's staff automation runs (`Staff.automate`).
3. Each campaign's week runs (`Week.campaign`, l.3485): fixed costs, then the 21 schedule slots, stamina, issues, surrogates, fundraising, advertising, field, super PACs, name ID, decay.
4. Then the message war, the world, events, the general election, scandals, polls and the forecast run.
5. The game advances one week, except from week 102 (23 October, `DAILY_START`, l.6650): from then on each turn is one day, and every weekly quantity is scaled by `F = 1/7`.

All money is in dollars. Every effect on the vote is a **logit shift on each voter cell**, multiplied by the cell's elasticity (`Vote.cellLogit`, l.2719):
```java
double camp = campTerm(d, s, c) - campTerm(r, s, c);
return l + e * (cand + camp);
```

## 1. The schedule

**Data** (`Camp`, l.1989):
- `act[21]`, `loc[21]`, `param[21]`: 7 days × morning, afternoon, evening (`NSLOT = 21`, l.1892).
- `location`: the state the candidate is in.
- `stamina` (0–100, starts at 100).
- `prep` (debate preparation).
- Per-state stocks the events feed: `visit[]`, `localImage[]`, `volunteers[]`. Also group image `imageG[]`, `enthusiasm`, `nameIdS[]`, `smallList` and `cash`.

**The 17 activities** (l.1658–1690). Cost is in dollars and fatigue in stamina points. "Local" means the event happens in a state; "remote" means it doesn't.

| Activity | Cost | Fatigue | Where | Effect (l.3559–3685) |
|---|---|---|---|---|
| Rally | 350K | 9 | local | crowd = `Rallies.hold`; visit += 1.1·crowd; volunteers += (180+520·VOL)·crowd (×1.6 in the general); enthusiasm, name ID, small-dollar list +900·crowd·(0.5+VIR), localImage +0.004·crowd |
| Town hall | 90K | 6 | local | visit +0.8; localImage +0.006·(RET+INT)/1.5; favour +0.001; gaffe risk ×1.6 |
| Retail stops | 25K | 7 | local | visit +0.6·(0.5+RET) (1.9× in early primary states); localImage +0.004·(0.4+RET) |
| Fundraiser | 60K | 4 | local | raises (0.35M+2.2M·BIG) × hub (1.0 in a donor hub, else 0.45) × momentum × donor saturation |
| Call time | 0 | 3 | remote | raises (0.12M+0.85M·BIG) × momentum × saturation |
| Podcast | 10K | 4 | remote | a real show, booked by tier (needs 15% or 35% name ID, `TIER_NEED` l.6992) |
| TV interview | 5K | 3 | remote | favour +0.0025·(INT+DIS−1.1); name ID |
| Crossover media | 5K | 5 | remote | image with 5 groups on the other side's turf; gaffe risk ×1.8 |
| Policy speech | 120K | 5 | local | credibility on the focus issue +0.03·(SPE+INT)/1.6; salience +0.015; visit +0.6 |
| Union hall | 40K | 6 | local | union image +0.006·(0.5+AUT); visit +0.6; volunteers +150 |
| Faith community | 20K | 5 | local | one of 5 congregations: that group +0.008·(0.4+AUT) |
| Campus event | 60K | 6 | local | Gen Z and students; volunteers +(120+300·VOL) |
| Community event | 40K | 6 | local | one of 7 communities +0.008·(0.4+RET); volunteers +80 |
| Debate prep | 50K | 5 | remote | prep += 9·(1−prep/130)·(0.6+0.5·INT) |
| Rest | 0 | −14 | remote | stamina +6 as well |
| Endorsement meeting | 0 | 2 | remote | courts an endorser |
| Party dinner | 30K | 5 | local | localImage +0.008·(0.4+SPE) (×2 in early states); volunteers +60 |

- **Event costs by phase** (`eventCost`, l.3545): ×0.35 in the invisible primary, ×0.6 for a candidate in the primaries, full price in the general.
- **Travel** (l.3497–3503): an event in a new state more than 220 map units away is a same-day flight unless it is in the morning slot (which counts as overnight). A same-day flight runs the event at 70% and costs 3 more stamina. Each card warns about this with an amber dot.
- **Stamina** (l.3506–3511):
  - Each slot costs `fatigue × (1.3 − STA/150) × 0.55`.
  - Below 35 stamina, every event runs at 85%.
  - At the end of the week stamina recovers by `38 + 0.12·STA` (clamped 5–100).
- **Gaffes** (l.3681): chance per event `0.0035 × risk × (1 + (100−DIS)/35) × (1.8 if stamina < 40)`, cut by a good communications director.
- **Debate prep** decays ×0.93 a week in the general (l.3942).
- **Effect on the vote** (`campTerm`, l.2710): `0.022·ln(1 + visit[s]) + 0.4·localImage[s]`. Visit decays ×0.55 a week and localImage ×0.93 (l.3916).

**The interface** (`Tabs.schedule`, l.11177). This is tab 1 of 7 on the HQ's left panel.
- A 7-row × 3-column grid of cards. Each card has a colour strip by activity type, the short name, a state button and the parameter.
- Clicking a card opens the activity menu, which lists cost and "@ state" (l.11298).
- The state button arms "click a state on the map" (`pickSlot`).
- Podcasts open a booking menu by show tier.
- Under the grid:
  - **Auto-fill**: the AI scheduler fills only the empty slots (`Staff.fill`, l.6236).
  - **Clear**.
  - **Staff runs schedule: ON/OFF**: rebuilds the week every turn.
  - Projections: stamina after the week, event costs, a debate prep bar, and the next debate.
- Right-clicking a state on the map drops a Rally, Town hall, Retail, Union, Campus or Fundraiser there, into the next free slot (`quickVisit`, l.11359).
- In the final stretch the grid greys out past days and marks TODAY.

**The AI scheduler** (`AI.schedule`, l.5992):
- It ranks states by `stateValue`. In the general that is `ev·exp(−m²/2σ²) + 0.15·ev·exp(−|m−nat|/12)` with σ = 5. **It is computed from the true margins** (`Polls.truth`).
- It walks the 6 best states, one a day.
- Mornings go to prep (6 slots if a debate is within a week) or rest (1–4 slots by stamina).
- Evenings go to money (3 slots in the general, 7 if broke): call time or a fundraiser in a donor hub.
- One midweek afternoon goes to media.
- Everything else is drawn at random from Rally 35%, Town hall 20%, Union 10%, Community 10%, Church 7%, Campus 8%, Retail.

## 2. Field offices and the ground game

(The class named `FieldScreen`, l.13725, is the "Who's running" setup screen, not the ground game.)

**Data, per state:**
- `offices`, `organizers`, `partyOrg` (state-party organisers bought with transfers).
- `volunteers`.
- The week's `shifts`, `doors`, `calls`, `texts` and relational contacts, plus running totals.
- The two stocks that matter: `persuade` and `gotv`.
- Standing orders: `officeOrder`, `organizerOrder`, `transferW`.

**Costs:**

| Item | One-time | Weekly |
|---|---|---|
| Office | $12,000 × cost index | $850 × index + $350 (`officeWeek`, l.3405) |
| Organiser | $2,500 | $1,350 × index (`ORGANIZER_WEEK`) |
| Transfers to the state party | n/a | any amount, step $50K; buys organisers at 85% efficiency with a 25%-a-week lag |

The cost index is `0.75 + state income/120,000`.

**The weekly model** (`Week.field`, l.3849):
```java
double cap = offices * 8 + 2;                                  // an office holds ~8 organisers
double effOrg = Math.min(org, cap) + Math.max(0, org - cap) * 0.4;
double urgency = 0.6 + 1.2 * Math.exp(-weeksToVote / 8.0);
recruits = effOrg * (3 + 5*VOL) * (0.5 + enthusiasm) * urgency + volunteers * 0.015 * (0.5 + enthusiasm);
churn    = volunteers * (effOrg < 1 ? 0.12 : 0.06);
active   = Math.min(volunteers * 0.35, effOrg * 45 + volunteers * 0.04);   // ~45 active per organiser
perVol   = 0.7 + 1.6 * Math.exp(-weeksToVote / 2.5);  (x0.6 if more than 30 weeks out; x1.35 on the final weekend)
doors = shifts * 0.55 * density;  calls = shifts * 0.22 * 150;  texts = shifts * 0.13 * 1200;  relational = shifts * 0.10 * 12;
contacts = doors * (GOTV window ? 0.30 : 0.22) + calls * 0.054 + texts * 0.012 + relational * 0.56;
persuade += contacts * 0.30 / adults;    if (weeksToVote <= 6) gotv += contacts * 0.70 / adults;
```
- Density is `22 + 0.33·urban + 0.12·suburban`.
- Rallies, campus events, union halls, community events, party dinners, surrogates, digital spending ($1 per $2,500) and transfers also add volunteers.
- **Decay:** persuade ×0.92 a week, gotv ×0.85.
- **Effect on the vote:**
  - Persuasion: `0.9 × 0.1·tanh(10·persuade)` in `campTerm`.
  - Turnout (`Vote.turnout`, l.2777): `turn += 0.12·tanh(gotv/0.12) × (that side's share) × (1 − turn)`. It builds only in the last six weeks.

**The interface:**
- **Field tab** (l.11525):
  - Four big numbers: offices, organisers (paid + party), volunteers, shifts.
  - This week's voter contact.
  - A doors-per-week chart.
  - A "Ground game by state" table (offices, organisers with any pending order in gold, doors a week), sorted by state value. A click opens that state.
  - **Staff runs field: ON/OFF**.
- **State panel → Field** (l.12498):
  - An office stepper with its prices.
  - An organiser stepper (±5, plus a +25 button), warning "Not enough offices: extra organisers work at 40%".
  - A transfers stepper (±$50K), with "funds ~N organisers".
  - The week's ground-game rows.
  - Persuasion-reach and GOTV bars.
- **Map mode** "Volunteers & doors": shifts this week as colour, doors to date as the label.

**The AI and the "Staff runs field" toggle** (`AI.budget`, l.6051):
- 20% of the weekly budget (30% in the primaries) is spread by state value. That budget is `(cash − 3 weeks of HQ) / weeks left × 1.25` in the general.
- Each state's target is converted into organisers, capped at `adults/18,000` and at 2,200 + 1,200·VOL nationally.
- Hiring moves halfway to the target (at most 40 a week); firing goes 40% of the way.
- It opens one office per 7 organisers (at most 6 a week).
- The Field-director staffer's description promises faster growth, but `Staffers.FIELD` is never read, so the role has no effect in the code.

## 3. The air war

**Markets.** There are 103 real TV markets (`DMA_NAMES`, l.609), each with households and the share of each state it covers. A "Rest of <state>" market is added wherever DMA coverage of a state is below 93% (`Elect.markets`, l.2555). A market that spans state lines (Philadelphia reaches NJ and DE) spends money and moves voters in every state it covers.

**Cost per rating point** (`Week.cpp`, l.3417):
```java
c = households(thousands) * (hh > 1500 ? 0.55 : hh > 600 ? 0.62 : 0.72);
season = week < 60 ? 0.85 : < 94 ? 1.0 : < 98 ? 1.25 : < 102 ? 1.6 : 2.0;   // Labor Day 1.25, October 1.6, last two weeks 2.0
if (week >= 94) c *= 1 + 0.35 * battleground share of the market;           // competition for airtime
if (candidate && week >= 96) c *= 0.75;                                      // lowest-unit-rate window
```

**Orders, all standing** (they repeat every week until changed):
- **Per market:** weekly GRPs (stepper ±100, up to 2,000) and a tone of Positive, Contrast or Negative. The tone multiplies effect by ×0.9, ×1.1 or ×1.25. Negative builds a `toneStock` and backlash.
- **National TV:** network ($52K a point), cable ($13K, at 60% weight), Spanish-language ($6.5K, weighted by Hispanic share). Stepper ±25, up to 400. Prices are ×1.5 in the last 6 weeks.
- **Digital:** dollars a week per state (±$25K, a +$100K button, up to $5M), split over 6 platforms by a national mix. Each platform has a CPM and an age reach (l.6840–6851):

  | Platform | CPM | Reach 18–29 / 30–64 / 65+ |
  |---|---|---|
  | Facebook | $14 | 0.45 / 1.05 / 1.6 |
  | Instagram | $12 | 1.8 / 1.05 / 0.3 |
  | YouTube | $20 | 1.5 / 1.1 / 0.55 |
  | TikTok & creators | $22 | 2.7 / 0.75 / 0.12 |
  | Streaming TV | $40 | 0.6 / 1.1 / 1.15 |
  | Google Search | $30 | 1.0 / 1.0 / 0.8 |

  Each platform carries one of five messages, each with a persuasion weight:
  - Bio 0.9: favourability.
  - Issue 1.0: credibility on the focus issue.
  - Attack 1.15: makes the current attack line stick, and builds negativity.
  - GOTV 0.25: adds to `gotv` in the last six weeks.
  - Fund 0.15: builds the list and raises about 35¢ per $1.
- **Transfers** and **red-boxing** a state, which signals super PACs ×2.5.
- **Buttons:** "Pull all TV", "Pull all digital", and **Staff runs ads: ON/OFF**.

**Quality** (l.3774): `(0.75 + 0.25·DIS) × HQ tier (0.88 / 1.0 / 1.08) × media director × Ad Studio multiplier`. If cash is below a market's cost and below $0.5M, that buy is pulled with a notice.

**Stocks and effect:**
- TV adds `GRP × market share × tone × quality` to `tvStock[s]`, which decays ×0.48 a week.
- Super PAC GRPs decay ×0.5.
- Digital builds `digA[s][age] += impressions per adult × age reach × message weight × quality × 100`, decaying ×0.5.
- In `campTerm`:
  - `0.050·ln(1 + (tvStock + 0.75·superPac)/350)`
  - `+ 0.026·ln(1 + digA[cell's age]/25)`
  - Decay is applied after the week's buy, so a sustained 1,000 GRPs a week settles near a 950 stock, worth about 0.065 logit. That is roughly 3 points of margin in a 50/50 state before the other side answers.

**Super PACs** (`superPacs`, l.3895) are automatic for nominees:
- Budget `2.5M + 9M·BIG + 4M·EST` a week, ×2.2 in the last 6 weeks.
- Spread by state value (red-boxed states ×2.5) at their own, higher rates.

**The Ad Studio** (`AdStudio`, l.16001), reached by a button on the map:
- **Making an ad:** tone (Positive, Contrast, Attack), issue, audience (everyone or one group) and messenger (candidate to camera, real voters, a validator, a narrator). Producing it costs $120K.
- **True quality is hidden** (l.16024):
  - It starts at `0.35 + 0.25·min(1.2, salience/0.12) + 0.12·credibility`.
  - It is adjusted by the messenger fit, tone (attacks lose 0.1 once negativity > 1.5) and a random spark.
  - It is scaled by the media director.
- **Focus group:** $45K for a noisy read (σ 0.12), reactions from 8 key groups, and 3 voter quotes.
- **The library** holds 8 ads, one of them on the air.
- **Effect:**
  - The ad on the air multiplies every TV and digital dollar by `0.775 + 0.45·quality`, from 0.8 to 1.2.
  - Its targeted group and issue credibility grow with the week's airtime.
  - Only the player's ads vary in quality; computer campaigns always count as average.

**The AI and the "Staff runs ads" toggle** (`AI.budget`):
- 55% of the weekly budget goes to TV in the general.
- Market value is `Σ(state value × share)^1.4 / cost^0.3`. A buy needs at least 60 GRPs and is capped at 1,500.
- Tone is set by aggression.
- 20% goes to digital by value. The platform mix depends on the faction; the message is Bio early, Issue by default, Attack in the last 9 weeks, GOTV in the last 3.
- In the last 6 weeks it buys cable nationally and red-boxes any state worth more than 8% of the total value.

## 4. The other weekly controls (one line each)

- **Money tab** (l.11372):
  - Cash, raised and spent, and a 26-week chart.
  - Sources of money.
  - Donor saturation: the cap is `(70M or 260M in the general) × (0.4+BIG)`.
  - HQ tier: Lean, Standard or Juggernaut. In the general that is $2.1M, $4.2M or $8.0M a week.
  - Self-funding.
  - National TV and the digital mix.
  - "This week's budget" (TV, digital, field payroll, transfers, events, HQ), flagged red if it exceeds cash.
- **Message tab:** a weekly frame (14, with a fit bar and a narrative bar), an attack line (11), issue focus (one main and two secondary), and policy promises.
- **Polls tab:** buy internal polls. Choose state or national, sample size 400–1,500, and method: online $28, text-to-web $45, live phone $95 an interview, plus $6K. A "Staff runs polling" level (Light, Standard, Heavy) is also available.
- **Campaign tab:** the trail record (rallies and crowds, shows and views), rivals, debates.
- **News tab:** the news feed.
- **Surrogates:** 8 named figures, each sent to one state a week. The **running mate** has 4 stop types.
- **Named staff:** 6 roles, each with skill and a trait; they work as multipliers.
- **Map modes:** Blank, Spending (last 4 weeks), Internal polls (hatched where unpolled), Public polls, Volunteers & doors.
- **Buttons on the map:** Staff, Ad studio, Forecast, Chief of staff.
- **Keys:** 1–7 switch tabs; Space ends the week; Esc cancels a state pick and then deselects.

## 5. The screen and a typical week

**The HQ is 1600×900** (l.10356):
- Top bar.
- Left panel (420 px) with the 7 tabs.
- The map in the centre (720 px), with mode toggles above it and a dashboard under it: the race for 270, the EV panel, the news.
- A right-hand state panel (412 px) with Overview, Ads, Field and Voters tabs. When no state is chosen it shows the national picture.

**A week:**
1. **Schedule:** press Auto-fill, or click slots. To move an event, click its state button, then the state on the map. Right-click a swing state to add a rally there.
2. **Money:** check the HQ tier, national TV and the digital mix. "This week's budget" must stay under cash.
3. **Ads:** click each battleground on the map and open the **Ads** tab in the state panel. Set GRPs and tone for each of its markets, and the state's digital dollars. Optionally make or test an ad in the Ad Studio and put it on the air.
4. **Field:** in the state panel, open offices, hire organisers in steps of 5 or 25, and set transfers. The Field tab shows doors and volunteers overall.
5. **Message:** pick a frame, an attack line and an issue focus.
6. **Polls:** buy polls in the states that matter.
7. **End week** (Space):
   - Decisions waiting in the inbox are answered first, then the convention or debates, which are played live.
   - Then the week resolves and a "Week n in review" digest appears: raised and spent, poll change, volunteers and doors, the biggest rally, the top show, the headline.
   - From 23 October each turn is a day.
