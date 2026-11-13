# Resolute Desk Version 2 (HEAD 2ce2b37): how the presidency works, from the code

All paths below are relative to `/home/user/lukewaadams-gif/resolutedesk/src/main/java/resolutedesk/`. Line numbers refer to HEAD. A list of absolute paths is at the end.

**Architecture in brief:** one `model.Game` holds the state (public, serializable fields). Stateless systems advance it once a week from `sim/Week.resolve` (Week.java:129-212), in this order: schedule slots, then stamina/stress/health, `Economy`, `Policies`, `Approval`, then Congress (`Hill.drift`, `Legis`, `Budget`, `Midterms`, `Reelection`, `Relations`), then the executive (`Cabinet`, `Actions`, `Courts`, `Trouble`, which also runs the VP and impeachment), then `Diplo`, `History` (which runs the wars), `HomeFront`, `Stories`. After that it records the week, increments it, runs `Terms.check`, and runs `Delegate.plan`. Everything random draws from `g.rng`.

**The old single file** (`/home/user/src/ResoluteDesk/ResoluteDesk.java`) has the same schedule constants. The big difference is that it runs on **political capital**: `capital = 30` at start, plus `0.6 + (approval-45)*0.05 (+0.5 in honeymoon)` each week, clamped 0-100 (old 2733). Deals cost capital, `DEAL_COST = {2,5,6,4,4,3}` (old 4311), and ending the filibuster cost 15. It also lacks favours and promises, the Policy/Cabinet Room, the policy model, storylines, statecraft, wars of choice, the WatchPlan, and the `Art.layer` painting (its Oval uses plain `GradientPaint`, old 9056).

---

## 1. The president's week

**Data** (`model/Game.java:38-52`):
- `act[21]` and `arg[21]`: 7 days × 3 slots. `arg` holds an issue, a state or a country index.
- `stamina = 85`, `sleep = 6.5` h, `stress = 25`, `health = 92`, `fitness = 50` (all 0-100). `sick` = weeks of illness left.
- `location` (a state), `funds` (party money, $M), `hill` (legacy relationship score, 0-100).

**Activities** (`sim/Schedule.java:13-39`), with stamina and stress per slot:

| # | Name | Stamina | Stress |
|---|---|---|---|
| 0 | Open time | 0 | 0 |
| 1 | Rest | +6 | -1.2 |
| 2 | Exercise | +2.5 | -1.2 |
| 3 | Family time | +3 | -1.8 |
| 4 | Policy time | -2.5 | +0.6 |
| 5 | White House remarks | -3 | +0.6 |
| 6 | Trip | -5 | +0.8 |
| 7 | Fundraiser | -3.5 | +0.6 |
| 8 | Ceremony | -1.5 | -0.3 |
| 9 | Hill meetings | -3 | +0.8 |
| 10 | Camp David | +8 | -2.5 |
| 11 | Work late | -4.5 | +1.5 |
| 12 | Travel (not selectable) | -2 | +0.5 |
| 13 | Campaign stop | -5 | +0.8 |
| 14 | Cabinet meeting | -2 | +0.3 |
| 15 | Trip abroad | -4 | +1.0 |

**Restrictions** (`ui/Rooms.java:182-213`):
- Camp David: weekends only.
- Hill meetings: weekdays only.
- Work late: evenings only.
- Cabinet meeting: not on weekends or evenings.
- Campaign stop: only in midterm season (`Midterms.season`: the 100 days before Election Day) or re-election season.
- Policy time and Remarks ask for an issue (one of 14). Trip and Campaign stop wait for a click on the US map. Trip abroad asks for a country, listed by flight time, and fills **three consecutive slots**.

**Effects of each slot** (`Week.java:134-173`):
- **Policy time:** `command[i] += 5*dq*(1-command/110)` and `issueAdj[i] += 0.02*dq*(1-adj/1.5)`, where `dq = 0.85+0.3*staffQuality(DPC)`.
- **Remarks:** issue salience +0.08 (cap 2), `issueAdj += 0.04*speech/70*(0.85+0.3*sq(COMMS))…` plus noise, `perf += 0.012`.
- **Trip:** `stateBump[s] += 2.2*cha/70`, `perf += 0.015`, location moves to the state.
- **Fundraiser:** raises `(1.5+U·2)*BIG/70*(0.85+0.3*sq(POL))` $M. More than 3 in a week costs `perf -0.06` per extra, and 5 or more makes a critical headline.
- **Ceremony:** `perf +0.012`. **Family:** `+0.004`. **Exercise:** fitness +1.2.
- **Hill meetings:** `hill += 1.2*EST/70`, plus `Hill.meeting`. Each slot also adds 2.5 Hill hours (see §4).
- **Cabinet meeting:** every secretary's morale +3, every department's readiness +0.6.
- **Trip abroad:** `Diplo.visit` (relations `+9*k`).
- **Campaign stop:** `cong.campaign[s] += cha/70`, re-election effort (in season), `stateBump +0.8`.
- **Work late:** +1.5 command on the top issue.

**Air Force One** (`Week.java:42-51`). Flight time is 0.3 h if the distance is under 60 miles, otherwise `0.6 + miles/480`. Miles are `2.9 ×` the distance between state map centroids. Hawaii is 10.5 h, Alaska 8.5 h.
- A trip slot costs an extra `min(6, flight*0.9)` stamina.
- A trip abroad costs an extra `min(12, flightAbroad*0.8)` on its first slot. `flightAbroad = max(1.5, 1.35·dist + 0.5)` from the map coordinates (WorldData.java:90-94).
- Sleep that night: trips over 3.5 h take off 1 h; a trip abroad takes off 1.2 h.

**Sleep, stamina and stress** (`Week.java:100-127, 176-183`):
- Each evening slot sets that night's sleep: Rest, Family or Camp David 7.4 h; Open or Exercise 6.8; Work late 4.8; Trip or Fundraiser 5.6; anything else 6.2. `sleepOf` averages the 7 nights.
- Age multiplies the stamina costs: `ageFactor = 1 + max(0, age-60)*0.012`.
```java
// Week.java:180-183
g.stamina = clamp(g.stamina + cost(g) + 4 + (sl - 6.5) * 3 - g.stress / 25 + (g.fitness - 50) / 25, 0, 100);
if (sl < 5.5) g.stress = clamp(g.stress + 3, 0, 100);
g.stress += 9.5 + (50 - g.approval) * 0.08 + (g.stamina < 30 ? 3 : 0);       // the job itself
g.stress = clamp(g.stress * 0.85, 0, 100);
```
- Fitness falls 0.4 a week.
- Health moves by `(stamina-50)*.01 - (stress-40)*.01 + (fitness-50)*.005 - max(0,age-70)*.01`, clamped 20-100.
- Illness risk each week: `0.004 + 0.0007·(age-60)+ + 0.0006·(stress-60)+ + (stamina<20 ? .02 : 0) + (100-health)·.0004`. 80% of the time it is a cold (1-2 weeks, stamina -15, stamina capped at 55 while sick). Otherwise it is a night at Walter Reed (stamina -30, perf -1).
- The chief of staff lowers stress by `2.5*(sq(COS)-0.4)` a week (Cabinet.java:348).

**Presets** (`Week.routine` 56-70; `Rooms.roadWeek`/`restWeek` 215-229):
- **Routine week, Mon-Fri.**
  - Mornings: Policy time, cycling through the 4 issues ranked by `sal*(62-issueApp)`.
  - Afternoons: Mon Ceremony, Tue Remarks (top issue), Wed Hill meetings, Thu Trip (the soonest promised state, otherwise the swing state that maximises `ev*(1-|app-47|/25) - bump*3`), Fri Ceremony.
  - Evenings: Open, except Friday Family.
- **Routine week, weekend:** Sat Exercise / Family / Rest; Sun Rest / Family / Open.
- **Road week:** the routine, plus Trips in slots 1, 4, 7 and 10 (Mon-Thu afternoons) to the 4 states that minimise `|app-48| - 0.15*EV`.
- **Rest week:** the routine, but weekday afternoons are Policy on Mon/Wed/Fri and Open on Tue/Thu, every weekday evening is Rest, Saturday plus Sunday morning and afternoon are Camp David, and Sunday evening is Rest.
- **Clear the week** sets every slot to Open.

**Watch mode and the hands-off president** (`ui/Watch.java`, `sim/Auto.java`, `sim/WatchPlan.java`).
- **The plan:** 6 priorities from 0 to 1 (Ambition, Bipartisanship, Hawk, Orders, Travel, Caution), set by 6 presets or by hand. Derived values:
  - `activeBills = 1+round(3p-0.5)`
  - `orderWeeks = max(3, 17-16p)`
  - `roadWeeks = max(2, 14-12p)`
  - `forcePenalty = 0.12-(p-0.5)*0.5`
  - `giveUpFront = 55-(p-0.5)*60`
  - `surePenalty = 0.45-(p-0.5)*0.6`
- **Each watched week:** a routine week every 4 weeks, a road week on the plan's interval, a rest week if stamina is under 35, campaign stops in season, `autoAgenda` every 6 weeks, then `autoWhip` and `autoExec`.
- **Speed:** a week every 1.6, 0.8 or 0.3 s.
- **Decisions:** chosen by `Decisions.auto`. It stops for the categories ticked in the plan (bills, budget, world, courts, scandal, storylines, campaign). Big calls get a toast.
- **Delegation** (`executive/Delegate.java`): with DG_SCHEDULE on, the chief of staff writes a routine week every week, a rest week if stamina is under `35+(1-q)*15`, a swing-state trip every 5th week, and a cabinet meeting if the last was more than 6 weeks ago.

## 2. Rooms and navigation

- **Screens** (`ui/ScreenIds.java`): HOME, SETUP, PICK, CREATE, OVAL, ROOM, PAPER, VP, HALL, END, NIGHT, LEGACY, IMPORT, TRANS, WATCH.
- **Rooms** (`ui/RoomIds.java`, 9 of them): Desk = "The Resolute Desk", Country, Economy, Residence, West Wing, Congress = "The Roosevelt Room", Situation Room, Press = "The Press Briefing Room", Policy = "The Cabinet Room".
- `Rooms.draw` (63-96) paints the `RoomArt` backdrop, the shared top bar, a "< Oval Office" button, the room title and subtitle, and the 9 tabs on the right.
- **Keys:** 1-9 open a room, Space ends the week, Esc goes back, P opens the paper.
- **Oval hotspots** (`Oval.java:169-181`), as `{x,y,w,h}`:
  - desk → calendar
  - windows → Country
  - briefing book ("CEA" on the side table) → Economy
  - right door → Residence
  - far-left door → West Wing
  - door beside the windows → Roosevelt Room
  - globe → Situation Room
  - left door → Press
  - papers on the coffee table → the newspaper
  - The Cabinet Room has no hotspot; it is reached only by tab or key 9.
- **What each room is:**
  - **Country:** approval with a chart; issue bars sorted by salience; a state map in Approval, Unemployment or Gas mode; group bars.
  - **Economy:** 9 tiles; 6 charts; prices people feel; industries; the Fed.
  - **Residence:** health statistics, the physician's advice, a stamina/stress chart.
  - **West Wing:** tabs Staff / Cabinet / Vice president / Orders and rules / The courts / Oversight.
  - **Roosevelt Room:** The Hill / Bills / Whip count / The budget / Elections / Favors.
  - **Situation Room:** The world / Daily brief / Hot spots / Operations / Trade / Alliances / Wars / Statecraft.
  - **Press:** The press / Speeches / Social media / Your party / The home front / The campaign.
  - **Cabinet Room:** Policies / Outcomes / System map.
- **There is no "Library" room.** "The library" is a tab of the end-of-presidency Legacy screen (`legacy/Legacy.java:368`).

## 3. The Oval Office and the desk

`ui/Oval.java` draws the scene, then from top to bottom:
- **The top bar** (`topBar` 90-126): seal, date, APPROVAL with its weekly change, STAMINA and STRESS bars, unemployment, inflation, gas, "TERM n Week x of y". Buttons: Watch / speed, Menu, Save, and the gold **"End week >"**.
- **"THIS WEEK" card**, bottom left: counts of policy slots, trips and recharge slots, projected stamina, sleep, and a "Plan the week" button.
- **Bottom right:** an "ON THE HILL" card (up to 3 of your bills with their stage, plus shutdown / debt-limit / stopgap status), or in election season the "THE 20xx RACE" card.
- **"THE LEDGER" ticker:** scrolls the last 12 headlines.

**On the desk itself:**
- The desk opens the calendar (§1). The calendar is a 7×3 grid of cards. Each card shows a colour strip (`AC_COL`), the slot name, the activity, its argument, and its stamina cost including flight. Beside it: a "Week ahead" panel (stamina now and after, sleep, an age warning, the Air Force One itinerary with hours each way, amber if over 3.5 h) and a trip map.
- The papers open The Capital Ledger.

**Decisions** are the "on your desk" items (`sim/Decisions.java`).
- Systems push `Decision{kind, title, body, opts, advice}` into `g.inbox`. `Decisions.show` pops the first one as a modal, with advisers' quotes under each option. Fictional advisers are named.
- The adviser whose option you take gains morale +2.5; the others lose 1.5.
- You cannot end the week while the inbox has items.
- A bill passed by Congress arrives as "X is on your desk" with Sign / Veto / Let it become law.
- The **daily brief** (`HomeFront.brief`, 245-273) is a list of priority-coded items: wars, Taiwan, Ukraine, Iran, the Red Sea, proxies, North Korea, terror, cyber, pandemic, the grid, fentanyl, protests, and active storylines. It is shown as a cream "TOP SECRET / THE PRESIDENT'S DAILY BRIEF" sheet in the Situation Room, with red, amber or grey bars.

## 4. Congress

**Data:**
- `Bloc{party, chamber, seats, econ, cult, loyalty, hawk, rel=50}`: 12 blocs, 3 per party per chamber, with era-specific tiers (`Hill.java:25-33`).
- `Member{rel, price, state, role: LEADER/CHAIR/SWING, bloc}`: 4 real leaders, 12 committee chairs per chamber from the majority, and 10 named swing votes. A swing vote's `price` is what they want: project, appointment, endorsement or policy.
- `Congress{mandate, hoursUsed, appointments=4, favors, kept/broken, approps[12], debtLimit, xWeek, crUntil, shutdown…}`.
- Starting relationships: your blocs 58, the other side's 38.

**No currency, only time** (`congress/Relations.java`):
```java
public static final double HOURS_BASE = 2, HOURS_PER_MEETING = 2.5;               // :38
return HOURS_BASE + HOURS_PER_MEETING * slots + (Cabinet.sq(g, S_LEG) >= 0.7 ? 1 : 0); // :86
public static double mandateFor(double margin) { return clamp(2 + 0.55 * margin, 0.5, 15); } // :50
public static double honeymoon(Game g) { return clamp(1 - g.tw() / 15.0, 0, 1); }       // :64
```
- The mandate decays ×0.965 a week (:183).
- `pull` (:74-78) adds to the bloc logit for your bills: your blocs `0.9m+0.25h`, the other side's moderates `0.7m+0.35h`, its other blocs `0.2m+0.1h`, where `m = mandate/15` and `h` is the honeymoon.
- Appointments +1 every 4 weeks, up to 6.
- **A Hill meeting slot** (`Hill.meeting` 83-91), scaled by `k = EST/70`: leaders +1.6k (your party) or +0.9k; chairs and swing votes +0.4k; blocs +0.7k or +0.35k.
- **Drift** (95-97): each bloc moves 2% a week toward `(55 or 35) + pull`, and each member 1.5%, where `pull = (approval-45)*0.3 + (sq(LEG)-0.55)*12`.
- **Asks:** every 5-9 weeks a swing vote or chair asks for a visit (a promise to be in their state within 8 weeks), a project (+0.005 deficit, hawk blocs -1) or an appointment.
  - Yes: you earn a favour.
  - No: their relationship -6.
  - Keeping a promise: +6, plus a favour.
  - Breaking one: member -20, bloc -4.
  - Campaigning in a member's state in season earns that member's favour.

**Bills** (`congress/Bill.java`, `BillData.java`, `Legis.java`):
- About 33 named modern bills (`BILLS_MODERN`), each with cost ($B/yr), econ/cult position, public support and macro effects. Era tables add others. Policy bills `pol:id:from:to` are generated from the policy model (§7).
- **Drafting** (HillUI.java:197-246):
  - **Size** 0.5-1.5 and **Scope** 0-1 sliders.
  - **Paid for by:** Borrow / Tax the wealthy / Cut spending.
  - **Reconciliation** toggle: needs your party to hold both chambers, once per fiscal year, and a budget-eligible bill.
  - House or Senate first (revenue bills must start in the House).
  - A live whip read: expected votes, % chance in each chamber, chairs' odds.
  - At most **3 of your bills** before Congress at once.
- **Cost and support:** `netCost = cost*size*(0.7+0.6*scope)` (×0.05 if paid for). `support = def.support - 4*(scope-.5) - 2·unpaid + 2·taxPayFor`.
- **The vote model:**
```java
// Legis.java:64-86 (abridged)
double spatial = 2*(bl.econ*be + 0.8*bl.cult*bc) - (be*be + 0.8*bc*bc);
double l = 0.3 + 2.2*spatial;  if (bip) l += 0.2;
if (bl.party == sp) l += bl.loyalty*(b.ours && mine ? 0.4 + 2.4*agree : 0.5 + 1.3*agree); else l -= 0.9*pol*(bip?0.7:1);
if (b.ours && !mine) l -= 0.35 + (midterm season || tw>170 ? 0.3 : 0);
if (b.ours || mine) l += (bl.rel - 50)/35;   if (b.ours) l += Relations.pull(g, bl);
l += mine ? (approval-45)/18 : (approval-50)/30;  if (cost>0) l -= bl.hawk*cost/180;
l += (support-55)/40*(moderate?1.2:0.4);  if (publicPush) l += moderate?0.25:0.1;
return 1.6*l + 1.2*b.sweet[bi];
```
  - The chamber count sums `seats*logistic` over blocs. `chance = logistic(1.702*(yes-need+0.5)/sd)`.
  - `need`: House 218 (override 290). Senate 60, or 67 before 1975, or a majority if reconciliation or the filibuster is gone; override 67. Before 1971, only civil-rights-type bills (cult ≤ -0.45) face a filibuster.
- **Stages:** committee 3 weeks (2 for reconciliation).
  - The chair releases with `logistic(blocLogit + (rel-50)/30 + 1.5·sameParty + 0.8·(support>65))`.
  - An opposing chair trims size by 0.15.
  - A bill bottled up for more than 30 weeks dies.
  - Floor: one vote per chamber per week. A friendly leader holds the vote until the chance is at least 75% or 12 weeks pass, adding +0.05 to each own-party bloc's `sweet` each week it waits. A hostile leader schedules only if most of their members back it and the chance is at least 40%, and kills it after 14 weeks.
  - Then the second chamber, conference (2 weeks), and your desk.
  - A dead bill of yours costs your party's blocs -1.5, issueAdj -0.2 and perf -0.3.
- **Veto:** your own bill: perf -1.5, your blocs -6. The other side's bill: issueAdj +0.1 and their blocs -4. An override vote happens if `chance(H)*chance(S) > 0.05`.
- **Signing:** `perf += 0.6 + (support-50)*0.02` for your bills, your blocs +2, the cheer sound. Implementation risk: `p = clamp((66-readiness)/110, .02, .45)`, and a failure cuts the law's strength ×0.75.
- **The majority's own bills:** one every 7 weeks, at most 2 active.

**Working the votes** (whip tab; `Legis.deal` 344-431). Each deal costs **1 Hill hour** (Go public and Favor cost 0) and is limited to one per member per week. The base boost:
- Call: `0.35*(CHA+EST)/140`, rel +4
- Project: 1.4 if it is their price, else 0.7; deficit +0.01; hawk blocs -1.5; 15% chance of a "pork" story
- Appoint: 1.5 or 0.8; uses one appointment
- Endorse: same party only, 1.5 or 0.9; creates a promise to campaign in their state before the next election
- Threat: +0.8 on your own party, -0.3 on the other; rel -15, bloc -3; 30% chance of a story
- Favor: 1.8, uses up the favour
- Go public: once per bill; salience +0.1, stamina -3, the opposing leaders -2

The boost is multiplied by `0.8+0.4*sq(LEG)` and added to the bloc's `sweet`: ×0.35 for a chair, ×0.22 for a swing vote, ×0.3 for anyone else.
- The UI shows per bloc: lean (Yes, Leans yes, Undecided, Leans no, No) and yes-of-seats. Per person: portrait, "wants a project back home", lean, and the 6 small buttons. Plus Go public, Real-world impacts and Withdraw.
- **Ending the filibuster:** costs 3 hours. Chance `clamp(.06+.06*(seats-50)+(rel-55)/120+(app-50)/100, .02, .6)`. If it fails, your moderates -8.

**Budget** (`Budget.java`):
- **The request:** defense %, non-defense %, tax (% of GDP), mandatory (% of GDP). `deficitChange = def*.03 + nondef*.033 - tax + mand`. A 10-year deficit/debt projection chart.
- **Appropriations:** 12 bills, May-September, each passing at 9% a week (5% under divided government).
- **The 30 September deadline:** a stopgap for 11 weeks, a minority-blocks decision, or a divided-government decision. A shutdown costs `demandShock -0.08`, `perf -0.5*blame` and stress +4 each week, with a hold-firm-or-deal decision.
- **The debt limit:** "extraordinary measures" give a 22-week X-date. Decisions come at 8, 3 and 1 weeks out. A default costs `finShock -4.5`, perf -6.

**Midterms** (`Midterms.java:46-102`):
```java
mP = 0.5*(app-50) - 2.5 + 0.4*(gdp-2) - 0.3*(u-uRef) + min(1.5, funds/40) + min(1, camp*0.05) + (sq(POL)-.55)*1.5 + wings + noise
```
- House: `houseBase + houseSlope*natD + N(0,5)`.
- Senate: per seat, `lean + natD + 3 incumbency + campaign*1.2 + N(0,3.5)`.
- Then the leaders reshuffle, and `funds ×0.3`.

## 5. Executive

**Staff** (9: COS, NSA, Press, Counsel, NEC, DPC, Leg, Political, Comms) and **cabinet** (21 posts).
- `Person{skill, loyalty, morale, ideo, trait}`. Quality is `q = skill/100*(0.7+0.3*morale/100)` (Person.java:26).
- Each pick offers 3 options: an expert (skill 70-91), a loyalist (loyalty 80-95) and a big name or bipartisan pick (Cabinet.java:142-176). Traits include Steady, Expert, Loyalist, Workhorse, Dealmaker, Media-savvy, Leaker, Abrasive, Ambitious, Cuts corners.
- **Confirmation:** a Senate vote per bloc, `logistic(1.5*l)`, where `l = 0.6 + (skill-62)/22 + (rel-50)/40`, plus `3·loyalty - dist` for your own party or `0.5 - 2.2·dist - 1.2·pol` for the other. The vote comes 3+ weeks after nomination.
- **Morale target:** `58 + (app-45)*0.7 + (loyalty-60)*0.35 - (stress-45)+*0.25 - 2.5·abrasive - 6·shutdown + 4·steady`, moving 5% a week.
- **Resignation hazard:** `0.0006 + (38-morale)+/700 (+0.003 ambitious after week 70)`.
- **Department readiness target:** `40 + 42q - 70·workforce cut + 3 (recent cabinet meeting) - 12 (shutdown)`, moving 4% a week.
- **Leak chance:** `(0.0007 + leaker .006 + (50-morale)+/9000 + 0.0015 if loyalty<50) * (1.3-0.7·sq(COS))`.
- Firing someone costs everyone's morale -2.
- **Delegation toggles:** the schedule, whip, budget, pardons, vacancies.

**Orders and rules** (`ActionData`, `Actions.java`):
- 49 predefined actions: 5 emergencies, orders by party, and 15 regulations. There are also custom orders (issue, direction, scope, legal basis) and policy orders/rules.
- Legal risk is `base*(1.15-0.3*sq(Counsel))`.
- An order takes effect immediately.
- A rule goes through public comment for `14-6*q(OMB)` weeks, then is exposed to a Congressional Review Act vote.
- On issue: `perf += k*(support-50)*0.012`; issueAdj and salience move; your blocs +0.6 (or -2 if it goes against them).
- Chance of being sued: `legal*1.3 + 0.1·unpopular`.

**Courts:**
- Chance a court blocks you: `legal' * (0.5+0.9*opp)`. At district level, `opp = 0.5+0.5*oppShare` (plaintiffs pick their court).
- A Supreme Court justice votes `logistic(2*ideo*side + 3*(0.5-legal) + 0.2)`.
- After a block you choose: Appeal / Rewrite (scope ×0.7, legal ×0.55) / Let go / Slow-walk, which adds a "Defying the courts" scandal (severity 4.5).
- Justice vacancy hazard: `0.00012*e^((age-60)/7.5)`.
- Lower-court vacancies fill at a pace set by Senate control.

**Scandals** (`Trouble.java`):
- When a scandal starts, `g.scandal += 0.35*sev`. Each week `g.scandal += 0.009*sev`.
- Severity decays ×(0.975 - 0.02·press), ×0.965 under investigation, ×0.995 while a probe is open.
- At severity 3 or more you choose: release everything, deny, ask the person to resign, or order an independent review.
- Opposition-run probes bring subpoena decisions.
- **Impeachment pressure** is `2·contempt + 3·defiance + Σsev(impeachable)`. With the other party holding the House and pressure of 9 or more, an inquiry opens at `.06+(pr-9)*.01` a week. The House vote is per seat by bloc logistic; conviction needs 67.

**Vice president** (`VicePres.java`): missions are Tour (6 weeks of state bumps), Task force (8 weeks on an issue), Work the Senate (Senate blocs +0.25k a week), Fundraise (6 weeks), Abroad (4 weeks). A gaffe happens at `(100-DIS)/2600` a week. Loyalty drifts down -0.15 a week while idle.

## 6. Opinion and the press

**Approval** (`opinion/Approval.java`):
```java
PERF_WEIGHT=1.6, SCANDAL_WEIGHT=1.2, RALLY_WEIGHT=1.6; HONEYMOON_FADE=.975, RALLY_FADE=.965, PERF_FADE=.96, SCANDAL_FADE=.975; MOOD_FADE=.97, MOOD_STEP=.45; PULL=.15, NOISE=.55 // :33-38
economy = 1.2*(gdp-2) - 2.4*(u-uRef) - 2.0*(piEff-piRef) - 3.0*(gas/pl - gasRef) + 0.08*(sentiment-80)       // :77
target = squash(eraBase + economy + 1.5*issueWeighted + personal ± difficulty + honeymoon + 1.6*perf - 1.2*scandal - wear + mood, 1.6*rally)
approval += (target-approval)*0.15 + N(0,0.55)                                                               // :146
```
- **Bounds:** none before 1990 (12-82). After 1990: `{30+6s, 72-8s}`, where s is sortedness.
- **Squash:** above the ceiling, points count ×0.35. Below the floor, an exponential floor bottoms out 16 points below it. A rally adds after the squeeze, `rally*(1-(x-50)+/100)`.
- **Wear:** `min(9(1-.45s), 0.045*(tw-26)+)`, plus a third of that cap in a second term.
- **Honeymoon:** `4 + 0.6*mandate`.
- **Decay:** issueAdj ×0.97 a week, stateBump ×0.9.
- **Issue approval:** `app + 4*(adj+policyResult) + (command-50)*0.04 + issue-specific economy terms`.
- **States and groups:** a cell model (state × demographic cell logits), with the national offset solved by bisection so the cells add up to the headline number.

**Press** (`ui/PressRoom.java`, `press/*`):
- 18 outlets per era, each with lean, audience, and whether it does sit-down interviews.
- Tone target per outlet: `50 + lean*side*32 + (app-45)*0.4 + (press-50)*0.3`, moving 5% a week.
- **Press conference** (stamina -3, every 2 weeks) and **interview** (stamina -2, every 2 weeks): 3 questions on topics such as a scandal, inflation, Taiwan, your weakest issue. Answers:
  - Straight (uses INT), Pivot (DIS), Push back (DEB), Make news (CHA).
  - `pOk = clamp(.35+(skill-55)/80-.2·hostile, .1, .92)`.
  - If the president's discipline is under 55, a failed answer can become a gaffe.
  - The result: `perf += clamp(score,-2,2.5)*.35*min(1.6, audience/8 or 1.2)`.
- **Oval address:** every 8 weeks. You build it: an opening (4 choices), 3 lines each chosen from record / plan / attack / push a bill, and a close (3 choices).
  - Score = `spScore*SPE/70 + (SPE-60)/25 + noise`; `perf += clamp(score,-2,3)*.5*(1.4 if SOTU)`.
  - The **State of the Union** fires automatically in late January or early February.
- **Posts:** 5 a week, in 5 tones (celebrate a win, explain a policy, hit the other side, something personal, a joke).
  - `reach = SMD/70*(1-.15*(n-1))` (before 2009, speaking ability instead).
  - Backfire risk: `.02` (`.08` for attack or joke) `+ (60-DIS)+/300`.
- **Commissioned poll:** costs $0.5M of party funds and gives a group breakdown on one issue.
- **Party wings:** satisfaction target `68 - 34·dist(record, wing) + (app-45)*.5`.
- **Opposition strategy:** chosen quarterly. Cooperate if approval > 55, obstruct if < 43, and one step harder near an election.

**The Capital Ledger** (`ui/Paper.java`). It is shown after every week.
- Cream page (`#f4efe4`) with a Playfair 56pt masthead and rules.
- The lead is the heaviest Washington story, in a 40pt headline, with a portrait "photo" and a serif body.
- Six stories in 3 columns, with section kickers for the era's ambient news. Empty slots are filled with features: "The week ahead", "Poll watch", "Markets" and an opinion column.
- An "IN BRIEF" list.
- Side panels: "BY THE NUMBERS" and "DEVELOPING STORIES" (each storyline's stage pips and a heat bar: growing, holding or cooling).

**Storylines** (`stories/Storylines.java`, `StoryData.java`):
- Heat starts at 52±4. Each week `heat += fuel - 0.5 + N(0,.8)`.
- At the end of a stage: heat ≥ 60 grows the story to its next stage (or a "runaway" at the last stage); heat ≤ 30, or two lingers, fades it.
- Each stage is a decision. An option's heat delta applies on success; on failure, the failure delta plus a 6-point backfire.
- At most 2 storylines run at once, and one cannot recur for 104 weeks.
- Ambient news: 1-2 era stories a week, with stories tied to current conditions weighted ×3.

## 7. The economy and policy

**Economy** (`economy/Economy.java:117-189`), weekly:
```java
ogTarget = -0.6*(realRate - rNeutral) + 0.55*fiscal + demandShock + finShock - 0.07*(tariff-2.5) + 0.03*(sentiment-80) - 0.3*aiShock + slump + 0.45*(war-war0);
og += (ogTarget - og)*0.022 + N(0,.05);
gdp = potential + polGdp + (og - og52weeksAgo) (+AI term);
unemp += (nairu + polUnemp + structural - 0.5*og - unemp)*0.035;
piT = expect + polInfl + 0.28*og' + supplyShock + 0.012*(oil-oilBase) + 0.05*(tariff-eraTariff); infl += (piT-infl)*0.03;
taylor = rNeutral + infl + 0.5*(infl-2) + 0.5*og + (fedHawk-0.5)
```
- The Fed meets 8 times a year and moves 25 or 50 basis points.
- 10-year yield: `.55 fed + .45(rN+1+expect) + .015(debt-90)+`.
- Stocks: a log-return model.
- Gas: `(1.25+adj)*pl + 0.0245*oil`.
- Also modelled: groceries, rent, insurance, housing, manufacturing (PMI), AI investment, farm, energy, tourism, sentiment.
- Deficit: moves toward primary + cyclical (`-0.35og`) + interest. Debt: `+= (deficit - debt·nominalGrowth)/52`.
- Shocks each week: a 0.35% chance of a recession, 0.1% a financial crash, 0.3% an oil spike, 0.2% a boom, each followed by a calm period.
- State unemployment and gas are derived for each state.
- Monthly jobs report, CPI on the 12th, quarterly GDP.

**Policy** (`policy/*`):
- **146 rows** in `PolicyTable.ROWS`, in 12 areas. Each has 2-5 levels, the level in force in each year, channels (B bill, O order, R rule, $ budget), legal risk, cost per step, econ/cult shift, support, lean, a signature flag, effects on 12 outcomes, voter-group effects, a lag in weeks, an evidence rating (S/M/W/C) and a precedent.
- Channel strength `CH_STRENGTH = {1, .6, .85, 1}`. Orders, rules and budgets move one step at a time; a bill moves up to two.
- Each week the effect closes `min(1, 2.3/lag)` of its gap to the level. The economic part (gdp, unemp, infl, oil, deficit) feeds `polGdp/polUnemp/polInfl/oilBase/fiscal` as deltas (Policies.java:124-132). The other outcomes (uninsured, poverty, crime, overdoses, border, emissions, scores) feed results that the issues read, e.g. `health = clamp(-0.12*ΔUninsured, ±1.2)`.
- **Public reaction** when a policy moves: `national = clamp((sup-50)*(signature?0.2:0.06)*min(2,|steps|)*str, ±5)` approval points, applied through `perf`, plus group bumps.
- **Cabinet Room UI:** a policy list with level pips; detail with a choice of target level; channel buttons (bill / order / rule / budget) with the reason a channel is unavailable; ranges from the evidence spread `{.25,.5,.8,1.0}`; outcome sparklines; and a "System map" diagram of the links.

## 8. The world

**Data:** 40 countries (`WorldData`), each with relations (-100..100), alignment (none, partner, treaty ally, adversary), power (1-10), nuclear status, trade and balance, and leaders as of 2025 or 2029.
- `World` holds per-country tariff, sanction, aid, military aid, arms, ties, pact, troops, unrest and covert programmes. Theatre indices: Taiwan tension and deterrence, Ukraine front and talks, Gaza, Iran, the Red Sea, proxies, North Korea, terror, cyber, migration, cartels. Also NATO and Pacific alliance strength, DEFCON, and wars.

**Weekly** (`Diplo.week` 135-194):
- Relations drift 1.2% a week toward `base + 4·aid - 5·sanction - tariff/2 + fpc + alliance terms`.
- Big trading partners retaliate against tariffs (1.5% a week).
- Theatre models, e.g. Taiwan: `deter → 30+9·posture+6·aid+.25·pacific+10·dq`, and `twt += .10 - (deter-55)*.006 - (relCN+30)*.002`, with stage changes.
- Effects on oil and supply, and on the foreign / security / immigration issues.

**Player actions:**
- **Call:** 3 a week; gain `(3+4k)`, ×0.4 if repeated within 4 weeks.
- **Invite:** a state visit in 3-6 weeks.
- **Summit:** set 4-8 weeks out, costs stamina -6, played in 3 rounds: opening (warm, firm or charm), the ask (country-specific), the give. Success chance `logistic((mood+lev/2+give-20)/18+(FPC-60)/40)`.
- **G7, NATO, UN, G20** by date: one choice, stamina -7.
- **Crisis decisions** (`CrisisData`), built from condition strings. Each option has an agency type, and its odds come from `Diplo.p` (307-318): base plus president's foreign-policy skill plus that agency's quality and readiness.
- **Operations** and **covert action:** influence, cyber, arm the opposition, regime change, with odds `.6/.55/.45/.25` and leak chances `.2/.2/.35/.45`.
- **Trade tab:** tariff, sanction and aid levels per country.
- **Alliances tab:** reaffirm Article 5, press allies to spend more, Pacific exercises, posture in 3 theatres.
- **Statecraft:** military aid, arms, troops (0 / 2k / 5k / 20k / 60k), ties, a trade deal (a majority of Congress, odds from `yes()`), a defense pact (two-thirds of the Senate).

**Wars of choice** (`Wars.java`): strikes, blockade, no-fly zone, invasion, occupation.
- Troops needed: invasion `40+30·power`.
- Pace `WK_PACE/(1+.6p)`, halved for great powers, ×0.3 for a nuclear enemy, near zero with a protector.
- `legit = 38 + 14·authorized + (enemy rel) - 30·ally - 15·nuclear`.
- `supportTarget = legit - 6·log10(1+kia/10) - .05·(weeks-26)+`.
- Each week `perf -= .007*(55-support)+`.
- The War Powers vote comes at week 9 if the war is not authorized.
- An invasion that reaches 100 topples the government and becomes an occupation.
- The opening shock covers rally, markets, oil, alliances, refugees, terror and DEFCON.

**Situation Room UI:** the world map (relations colours green or red; the US `#1f4fa8`; pulsing red dots on hot spots) with a country panel; PDB; gauges; operations; trade; alliances; wars (troop steps, strategy, end options); statecraft.

## 9. The art

- **Method:** all Java2D at a 1600×900 design resolution, scaled to the window. A 16 ms Swing timer redraws every frame.
- **`Art.layer`** (Art.java:43-59) paints a static scene once into an ARGB image at the device scale (rounded to 0.5×) and caches it under `key@scale` (the cache clears after 40 entries). Only moving parts are drawn live.
- **Toolbox** (Art.java):
  - `lin`: multi-stop gradients.
  - `vfill`.
  - `glow`: a radial gradient `{a, .45a, 0}` at stops `{0,.45,1}` on an ellipse.
  - `vignette`.
  - `shadow`: 7 round strokes of falling width.
  - `floorShadow`: a black glow.
  - `bevel` / `inset`: 1.2 px light top-left and dark bottom-right lines, or the reverse.
  - `speckle`: seeded tiny squares.
  - `grain`: 24-segment sine-wobbled polylines.
  - `stripes`.
  - `gloss`: a diagonal white streak at stops `{0,.45,.55,1}`.
- **OvalArt** (paint order 52-71): wall (cream `#f1e6cc` → `#d6c39b`, edge darkening, stripes, speckle, dentil cornice, dado panels, baseboard, fluted pilasters), 3 windows (sky gradient, cloud glows, tree ellipses, lawn, the Washington Monument as a two-tone spire, gloss, mullions, gold drapes made of 6-stop fold gradients, a swag valance), 4 doors with pediments and brass knobs, a gilt-framed Hudson River landscape and an oil portrait under picture lights, perspective planks (vanishing point 800,300) with grain and window reflections, sunbeams, the rug, sofas, coffee table with papers, globe stand, side table with lamp and "CEA" book, chair, and the desk front with seal. It finishes with a warm glow and a 0.5 vignette.
  - **Rug:** navy `#1f3a7a` for Democrats or gold `#b98a2e` for Republicans; a radial gradient in a 0.255-scaled transform; gold rings; 50 flattened stars; a 32-ray sunburst; the seal at y-scale 0.3.
  - **Live layers:** two flags (7 swaying fold strips, US stripes and canton), a spinning globe (land ellipses clipped, moving meridians), the president's portrait at 770,360, 26 dust motes, and a door glow on hover.
- **RoomArt:** one backdrop per room, each dimmed with a navy gradient (0.74 → 0.5 → 0.62 alpha) and a 0.6 vignette: walnut panels by lamplight, night windows (Country), a green study with bookshelves (Economy), the Yellow Oval Room with a fire, a cream corridor (West Wing), the red Roosevelt Room with a fireplace, glowing blue screens (Situation Room), a blue curtain with seal and lectern (Press), coral drapes and a table (Cabinet).
- **HomeArt:** the North Portico at dusk. Sky `#040817` → `#5a4a6a`, moon, floodlight glows, lit windows with pediments, columns, fountain, lamps. Live: 160 twinkling stars, window flicker, flag, 40 fountain droplets.
- **Time of day:** there is no time-of-day system. The Oval is always daylight, Country is always night, the title screen is always dusk.
- **Portrait** (743 lines): drawn in a 200×240 canvas from a `Look` record (skin 0-7, hair, style, gray, facial hair, glasses, faceW, jaw, suit, tie, age, smile, earrings, lipstick).
  - Bézier face with a radial skin gradient, cheek blush, iris radial gradient with catchlights, lids, brows, a nose with a cast-shadow glow, hair front and back with sheen, body, collar.
  - Optional party-coloured card with stripes and stars.
  - Animation: breathing, head tilt, blinks, gaze, talking mouth. A "still" mode is cached at 2× size.
- **Maps:** US and world outlines are packed base-90 delta strings (`USMap.num/dec`), drawn even-odd. Each map caches a drop-shadow layer and a light-gradient layer above live state colours. US state colours ease 18% a frame toward their target; the selected state's outline pulses. `marginColor` is the diverging blue/red scale. The world map adds an ocean layer with a graticule, a coastal halo stroke and an offset land shadow.
- **Flags:** a mini-language (`h:` stripes across, `v:` stripes down, `d:` disc, `x:` Nordic cross, `s:` corner star, `c:` centred star, plus `|t:` hoist triangle, `|k:` canton, `|o:` small disc), with historical variants.
- **UI chrome:** "war room" palette (`Theme.java`): bg `#060a13`, text `#e7edf7`, dim `#93a4bf`, gold `#f2c14e`, accent `#7cc4ff`, good `#45d483`, bad `#ff5d5d`, amber `#ffb347`. Panels are translucent navy glass with a soft shadow and top sheen. Fonts are Inter, Playfair Display, Source Serif 4 and JetBrains Mono.
- **What ports to SVG directly:** `lin`/`vfill`/`glow` (a radialGradient on a scaled ellipse), vignette, bevel/inset, gloss, the flag mini-language, the seal, map paths (decode once to path data), the rooms' geometry, and most of the portrait (paths plus gradients). Speckle, grain and the 7-stroke shadow are procedural loops; in SVG, replace them with `feTurbulence` / `feGaussianBlur` filters or pre-generate them with a seed. The layer cache matches the idea of a static SVG group plus a few animated elements.

## 10. Sound (`ui/Sfx.java`, `Sound.java`)

- Everything is synthesised at start-up into float buffers: 22,050 Hz, mono, 16-bit, in a 256-sample mixer thread with up to 16 voices, linear-interpolated playback with a pitch factor, and a soft clip `s/(1+.55|s|)`. Master volume 0.6.
- Generators: `tone(secs, f0→f1 exponential sweep, wave sin / tri / tanh-soft-square / square, exp decay, attack)` and `noise` (two-pole low-pass with a swept cutoff).
- **CLICK:** a 1900→1500 Hz triangle plus a noise tick. Played on every hit, at 0.6 volume.
- **HOVER:** a 2600 Hz blip at 0.35 volume, on button hover.
- **OPEN:** noise sweep plus a 520→780 Hz triangle. Played on a modal or decision (popups at 0.5 volume, pitch 1.3).
- **CHIME:** C6 + G6 sines. Played when a trip state is picked (0.5 volume).
- **TURN:** E5 then B5. Played at end of week.
- **CHEER:** a 2.2 s noise swell plus 14 claps. Played at the oath, a bill signing, an election win and the legacy screen.
- Defined but unused: CASH, ALERT, ERROR, CALL, GAVEL, WHOOSH, BELL, STING.

---

## A typical week, click by click

1. The week opens on **The Capital Ledger** (after the previous week). Read the lead and the developing stories, then click "To the Oval Office" (or press Space).
2. Any **decision modals** pop up: answer them, reading the advisers' odds.
3. Click the **desk**. Press **Routine week**, then adjust. Click a slot and pick from the menu: e.g. swap Thursday afternoon for a Trip, then click a swing state on the map. Add Hill meetings (+2.5 h each). Check "After this week" stamina, sleep and flight hours. If the projection is under 25, try **Rest week**.
4. Press **Esc** to return to the Oval. Through the **Roosevelt Room door**:
   - **Bills:** draft one (size, scope, pay-for, reconciliation) and press Send.
   - **Whip count:** spend the week's hours on Call, Project or Favor with swing votes; Go public.
   - **Favors:** check promises and their due weeks.
5. **West Wing:** fill vacancies, sign an order, set the VP's mission. **Cabinet Room:** move a policy by bill, order, rule or budget.
6. **Situation Room (globe):** read the PDB, make up to 3 calls, set tariffs or sanctions, propose a summit, manage any war.
7. **Press door:** a press conference or interview every 2 weeks, an address every 8, up to 5 posts, a $0.5M poll.
8. Check the **windows** (Country) and the **briefing book** (Economy); the **Residence** if stress is high.
9. Click **End week** (or press Space). Inbox items appear first. Then `Week.resolve` runs, the game autosaves, the TURN sound plays, and the next Capital Ledger appears. Election nights, removal and the end of term divert to their own screens.

---

**Key files** (absolute paths):
- `/home/user/lukewaadams-gif/resolutedesk/docs/ARCHITECTURE.md`
- `/home/user/lukewaadams-gif/resolutedesk/src/main/java/resolutedesk/sim/Week.java`, `Schedule.java`, `Decisions.java`, `WatchPlan.java`, `Auto.java`
- `/home/user/lukewaadams-gif/resolutedesk/src/main/java/resolutedesk/ui/Rooms.java`, `RoomIds.java`, `ScreenIds.java`, `Screens.java`, `Oval.java`, `OvalArt.java`, `Art.java`, `RoomArt.java`, `HomeArt.java`, `Portrait.java`, `USMap.java`, `WorldMap.java`, `Flag.java`, `Night.java`, `Theme.java`, `Paper.java`, `HillUI.java`, `PressRoom.java`, `SitRoom.java`, `PolicyUI.java`, `WestWing.java`, `Watch.java`, `Sfx.java`, `Sound.java`
- `/home/user/lukewaadams-gif/resolutedesk/src/main/java/resolutedesk/congress/Relations.java`, `Legis.java`, `Hill.java`, `Budget.java`, `Midterms.java`, `Bill.java`, `BillData.java`
- `/home/user/lukewaadams-gif/resolutedesk/src/main/java/resolutedesk/executive/Cabinet.java`, `Actions.java`, `ActionData.java`, `Courts.java`, `Trouble.java`, `VicePres.java`, `Posts.java`, `Delegate.java`
- `/home/user/lukewaadams-gif/resolutedesk/src/main/java/resolutedesk/opinion/Approval.java`
- `/home/user/lukewaadams-gif/resolutedesk/src/main/java/resolutedesk/press/QA.java`, `Speech.java`, `Social.java`, `Polls.java`, `Outlets.java`
- `/home/user/lukewaadams-gif/resolutedesk/src/main/java/resolutedesk/economy/Economy.java`
- `/home/user/lukewaadams-gif/resolutedesk/src/main/java/resolutedesk/policy/Policies.java`, `PolicyTable.java`, `PolicyDef.java`, `PolicyData.java`, `Outcomes.java`
- `/home/user/lukewaadams-gif/resolutedesk/src/main/java/resolutedesk/world/Diplo.java`, `Summit.java`, `Statecraft.java`, `Wars.java`, `WorldData.java`
- `/home/user/lukewaadams-gif/resolutedesk/src/main/java/resolutedesk/stories/Storylines.java`, `Stories.java`, `StoryData.java`
- `/home/user/lukewaadams-gif/resolutedesk/src/main/java/resolutedesk/home/HomeFront.java`
- `/home/user/src/ResoluteDesk/ResoluteDesk.java` (old version)
