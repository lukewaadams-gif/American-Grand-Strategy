# Gameplay design: decisions first

**Status:** design and mechanical prototype. **Nothing in this document shows that the game is fun.** Every result below comes from scripted strategy probes run against the rules. That evidence can show a design is *broken*, for example through a dominant strategy, a dead path or an arbitrary outcome. It cannot show the design is *good*. Only players can, and the plan for getting that evidence is in §8.

*(This brief calls the project "AmericanGrandStrategy". It is the same project as the repository's working title, **Mandate**.)*

---

## 1. The ten principles, and how each is enforced

| # | Principle | How the prototype applies it | Automated check |
|---|---|---|---|
| 1 | Every major decision involves trade-offs | Each action and option moves at least two of: the two wings of your party, opposition goodwill, markets, deterrence, capital, policy | `noStrategyDominatesAnother` |
| 2 | No option has one obviously optimal answer | Crisis responses depend on the rival's hidden resolve. Cabinet picks trade competence, loyalty, controversy and goodwill. The best bill depends on which Congress you have. | `theRightCrisisResponseDependsOnTheRivalsResolve`, `theRightPathDependsOnTheCongressYouHave` |
| 3 | Consequences are understandable | Every consequence line in the weekly briefing names its cause. Every decision option carries a preview (and its odds) before you choose. | interconnection tests (yields, gas, whip, Fed) |
| 4 | Uncertain but not arbitrary | Noise is bounded and centred on modelled expectations. Strategy explains 23-82% of the variance in every outcome measure. | `outcomesAreUncertainButNotArbitrary` |
| 5 | Information guides decisions | The whip count is calibrated. The quality of intelligence depends on your Secretary of State. Acting on intelligence avoids clashes. | `theWhipCountPredictsTheVote`, `anExpertSecretaryOfStateGivesSharperIntelligence`, `readingTheIntelligenceAvoidsClashes` |
| 6 | Repetitive administration is delegable | Four domains can be handed to the chief of staff | `aFullyDelegatedPresidencyRunsItselfInEveryCondition` |
| 7 | Different strategies are different viable paths | Four hands-on philosophies; each leads on at least one measure | `everyHandsOnPhilosophyLeadsSomewhere` |
| 8 | Earlier choices are felt later | Laws and crisis outcomes enter a lasting record. Cabinet competence works every week. Wing grudges feed later votes. | `aWeekOneChoiceIsStillVisibleInWeekTwelve`, `anAngryWingVotesLessLoyally` |
| 9 | Failure creates new situations | A failed vote, a bottled bill, a rejected nominee, a clash, a shutdown and a faction revolt each open a decision | `failuresOpenFollowUpDecisions` |
| 10 | Casual and expert styles both work | Full delegation is viable under unified government. An attentive player beats it under divided government. | `delegationIsViableButExpertPlayPaysOffWhenItIsHard` |

---

## 2. Audit of the eight loops

Each loop is described as it exists in the source games and in the foundation. The **Redesign** line says what changes.

- **Source abbreviations:** BG = Battleground 2028, RD = Resolute Desk, IC = First Island Chain, F = this repository's foundation (`Game`).
- **Evidence:** "Measured" means observed in headless runs (see `docs/SOURCE_AUDIT.md`).

### 2.1 Electoral campaigning

| | |
|---|---|
| **Objectives** | 270 electoral votes. In BG, survive the primaries first. |
| **Actions** | BG: 21 schedule slots a week, rallies, town halls, podcasts, debate prep, message/frame/attack line, promises. F: 3 stops, ads and field by state, a message, debate posture. |
| **Resources and constraints** | Candidate time, stamina (BG), money, issue salience, the calendar |
| **Short-term consequences** | State persuasion and turnout stocks, momentum, polls |
| **Long-term consequences** | BG: promises move groups but are *dropped at inauguration*. F: margin → mandate → starting capital; agenda → 2032. |
| **Feedback** | Polls with house effects and a hidden miss; forecast; map overlays |
| **Dominant strategies** | **Measured in BG:** outcomes barely vary (the Democrat won 40 of 40 baseline batches; one seed set ended on 310 EV 21 times out of 30), so campaign choices barely matter at the margin. In F, the autopilot's "tipping-point states × 3 rallies" heuristic is stable and probably exploitable (untested). |
| **Automate** | Weekly scheduling (BG's 21 slots); ad flighting; field staffing |
| **Redesign** | Keep F's three stops. Carry BG's promises into governing (pillar 1). Pick the debate posture from prep notes. Keep correlated results error so the campaign margin matters. |

### 2.2 Fundraising and media spending

| | |
|---|---|
| **Objectives** | Enough money at the right time; turn money into persuasion efficiently |
| **Actions** | BG: call time, fundraisers, small-dollar lists, self-funding, TV in about 148 markets, 6 digital platforms, ad studio. F: fundraisers (instead of stops), ads and field by state. |
| **Resources and constraints** | Candidate time, donor fatigue (BG), market prices, diminishing returns |
| **Short-term consequences** | Cash, ad pressure (decays fast), name recognition |
| **Long-term consequences** | Field (decays slowly), list size (BG) |
| **Feedback** | Cash on hand, spend by state, poll movement |
| **Dominant strategies** | BG: super PACs are automatic and the ad studio applies to the human only (a hidden bonus). In F, "front-load field, back-load ads" is near-optimal by construction. That is intended, but it is known. |
| **Automate** | The market-by-market buy, platform split, ad creative (BG) |
| **Redesign** | Spending is a *budget split* decision (ads / field / save) with diminishing returns per state, not a media-buying sim. Super PACs and the ad studio are cut (FEATURE_PRIORITY §1.2). |

### 2.3 Legislative negotiation

| | |
|---|---|
| **Objectives** | Turn the agenda into law at an acceptable political price |
| **Actions** | RD: bill size, scope and pay-for; chamber order; reconciliation; whip named members (calls, projects, appointments, endorsements, threats); go public; amend; withdraw. BG Govern: one vote a quarter by one of 4 approaches. F (monthly): 4 strategies + capital. **Prototype:** draft (size × pay-for), amend, three concession types per bloc, go public, call a vote stage by stage (leaders refuse likely losers unless you pay to insist), reconciliation. |
| **Resources and constraints** | Political capital, attention, appointment tokens (2), seat counts, the filibuster, the once-a-year reconciliation rule, the Byrd rule, the committee chair's party |
| **Short-term consequences** | The vote; capital won or lost; wing moods; opposition goodwill |
| **Long-term consequences** | The law's effects on the economy and deficit; a lasting approval record; opposition strategy; grudges (threatened blocs remember) |
| **Feedback** | Whip count per bloc *with what each bloc wants* (RD's best idea, kept); pass probability before you call the vote; the tally afterwards |
| **Dominant strategies** | **BG:** executive orders always succeed (fixed: courts can block them in F). **F autopilot:** "push the highest-odds bill, then address the nation" every month. **Prototype, first pass:** the partisan large bill passed 1% of the time (a dead path); the dealmaker passed 100% (too safe). After tuning, the partisan path passes about 80% in unified government and 0% in divided. The dealmaker still passes every time, paying for it in its base and deterrence (open issue O-2). |
| **Automate** | Whip operations for routine bills (the chief of staff, at 50% efficiency); conference (cut) |
| **Redesign** | Keep RD's bloc model, chairs and leader gatekeeping. Add the "what they want" line. Add follow-up decisions after a failure. |

### 2.4 Cabinet management

| | |
|---|---|
| **Objectives** | Competent departments without spending your majority to confirm them |
| **Actions** | RD: 15 departments + 6 posts, three candidates each (loyalist / expert / big name or crossover), confirmation votes, readiness, staff morale and leaks, firing. **Prototype:** 3 posts × 3 archetypes; hearings (a stumble raises controversy); a Senate vote 2 weeks after nomination; rejected → expert / crossover / acting. |
| **Resources and constraints** | Senate votes, time (2 weeks a nominee), wing moods, opposition goodwill |
| **Short-term consequences** | Wing moods (loyalist + base wing; crossover − base wing, + goodwill); confirmation risk |
| **Long-term consequences** | **Treasury** competence → bond yields and bill implementation. **State** → diplomacy odds and intelligence accuracy. **Defense** → military options. Loyalty → leaks when you overrule a secretary (RD's "advisers take sides"). |
| **Feedback** | Seat status, hearing reports, roll calls; secretaries recommend crisis options by name |
| **Dominant strategies** | RD: readiness multipliers are small (×0.85-1.15), so picks barely matter. **Prototype, first pass:** expert nominees were rejected in loops under an obstructing opposition, a degenerate failure loop. It was fixed by making own-party defections scale with controversy. Experts are now the safe default; loyalists and crossovers are the strategic choices. |
| **Automate** | Sub-cabinet posts, the remaining 18 departments, routine readiness (cut or delegated) |

### 2.5 Budgeting and economic policy

| | |
|---|---|
| **Objectives** | Low inflation and unemployment, affordable money, fiscal room, a government that stays open |
| **Actions** | RD: annual budget request, 12 appropriations bills, stopgap or shutdown, debt limit, tariffs, sanctions, executive actions. F: bills' fiscal effects. **Prototype:** the pay-for, a stopgap offer (4 options), shutdown hold-or-cave, lean on the Fed, release the oil reserve. |
| **Resources and constraints** | Senate 60 votes on funding; Fed independence (credibility); oil; deficits |
| **Short-term consequences** | Yields react weekly (a borrowed bill is priced while it is debated, weighted by its odds); gas follows oil; a shutdown hits confidence and swing voters |
| **Long-term consequences** | Inflation and unemployment move slowly; the Fed reacts at its meetings (weeks 2 and 8) to *reported* data; lost credibility persists |
| **Feedback** | Jobs reports (weeks 2, 6 and 10) and CPI (weeks 3, 7 and 11) as dated releases; Fed statements; mortgage rates in the briefing |
| **Dominant strategies** | **RD, measured:** fiscal impulses never decay; a severe recession moves unemployment by under a point, so economic policy barely matters. **Prototype:** economic outcomes differ by strategy (η² ≈ 0.8), but the absolute range is small over 12 weeks (index −0.23 to +0.63). Leaning on the Fed is a pure base-versus-markets trade and is never taken by the economy-first probes. |
| **Automate** | The 12 appropriations bills (cut); routine budget requests (delegable to the chief of staff) |

### 2.6 Foreign-policy crises

| | |
|---|---|
| **Objectives** | End the crisis without war, keep deterrence, keep oil and markets calm |
| **Actions** | RD: 20 crisis cards with adviser options and odds, one roll each. IC: a pre-war phase with posture, mobilisation and deceptive intelligence. **Prototype:** each crisis week, choose quiet diplomacy / sanctions / show of force / limited strike (once escalated). The adversary responds every week. |
| **Resources and constraints** | Deterrence, the competence of State and Defense, the adversary's hidden resolve, intelligence uncertainty |
| **Short-term consequences** | Escalation rung; oil (Gulf) or confidence (Taiwan); wing moods (restraint versus hawks); leaks if you overrule a disloyal secretary |
| **Long-term consequences** | Outcome (backed down / deal / stalemate / clash) enters the lasting record; deterrence is carried forward; a clash opens an aftermath decision |
| **Feedback** | Each week an intelligence estimate of resolve with an error band (sharper with an expert at State); option previews with odds |
| **Dominant strategies** | **RD:** war odds are fixed at 0.55 and ignore deterrence and posture. **Prototype, first pass:** a show of force made the rival back down 92-99% of the time (dominant). After adding a security dilemma, force deters a weak-willed rival and provokes a determined one. A mutation test confirms the rule is load-bearing. |
| **Automate** | Low-level crisis management (the chief of staff holds, then talks if it escalates) |

### 2.7 Military escalation

| | |
|---|---|
| **Objectives** | Win or avoid the fight at the lowest cost |
| **Actions** | IC: operational warfare (salvos, amphibious landings, a 25-sector ground war, munitions). RD: 10 one-roll operations. F: an abstract war decision (intervene / arm / stand aside) at the top of a monthly Taiwan ladder. **Prototype:** a 4-rung ladder per crisis (opening rung → … → clash at rung 3); strike available from rung 2; aftermath options after a clash. |
| **Resources and constraints** | Deterrence, munitions (F), Defense competence, the risk of provoking |
| **Short-term consequences** | Rally effects (small); oil and markets; casualties |
| **Long-term consequences** | Deterrence; the record; the war's economic shock (F) |
| **Feedback** | Rung names and the next step on the ladder; the escalation risk computed on each move (`lastEscalationRisk`, available to the UI) |
| **Dominant strategies** | IC (AI vs AI): quarantine always ends at a 75-day stop, and invasions are decided by landing region. Out of scope here. |
| **Automate** | All operational detail in the base game. IC Command Mode is an opt-in expansion. |
| **Change** | Escalation is modelled as *presidential* decisions; the war itself is abstract. This is recorded as C-11. |

### 2.8 Re-election

| | |
|---|---|
| **Objectives** | Win a second term on your record |
| **Actions** | BG: primary challenge, message, budget, 3 battleground states, debates. RD: decide to run, primary challenger, convention, message, money split, campaign stops. F: the 2032 weekly campaign with a record-based environment. |
| **Resources and constraints** | Approval, the economy, unfinished agenda, war outcome, opponent quality |
| **Short-term consequences** | Polls |
| **Long-term consequences** | The legacy score and the epilogue |
| **Feedback** | Record-based environment shown as components (in 1.0) |
| **Dominant strategies** | **F, measured:** re-election was 100% until message-salience inflation was capped. It is now 58-68%. In BG/RD, re-election uses a national formula plus state leans (not the cell model). |
| **Automate** | The same as campaign automation |
| **Prototype link** | The 12-week report ends with a midterm seat outlook computed from approval. The first quarter's choices therefore already point at 2030 and 2032. |

---

## 3. The core loop

```
   ┌──────────── 1. DECISION ────────────┐
   │ 3 attention points + owed decisions  │◄──────────────────────────────┐
   └───────────────────┬──────────────────┘                               │
                       ▼                                                  │
   ┌──────────── 2. CONSEQUENCES ─────────┐                               │
   │ immediate: votes, moods, goodwill,    │                               │
   │ oil, capital (listed with causes)     │                               │
   │ slow: economy, yields, the record     │                               │
   └───────────────────┬──────────────────┘                               │
                       ▼                                                  │
   ┌──────────── 3. NEW INFORMATION ──────┐                               │
   │ weekly briefing: whip odds, polls by  │                               │
   │ group, jobs/CPI releases, Fed, intel  │                               │
   │ estimate with error band              │                               │
   └───────────────────┬──────────────────┘                               │
                       ▼                                                  │
   ┌──────────── 4. POLITICAL REACTION ───┐                               │
   │ wings' moods (and revolts), opposition │                              │
   │ strategy switch, the adversary's move, │                              │
   │ the Fed, markets, leaks                │                              │
   └───────────────────┬──────────────────┘                               │
                       ▼                                                  │
   ┌──────────── 5. REVISED STRATEGY ─────┐                               │
   │ follow-up decisions after failures;   │───────────────────────────────┘
   │ new options (reconciliation, discharge │
   │ petition, aftermath); re-plan          │
   └───────────────────────────────────────┘
```

**In code:** `OpeningTerm.apply` runs step 1. `endWeek` then runs steps 2-4 in the order cabinet → economy → political reaction, and assembles the `Briefing` (step 3). Step 5 is the next week's `pending()` decisions and the new legal actions.

### A trace (seed 512, Democrat, unified government, from the test probes)

These are two scripted probes on the same seed. Nothing in the trace is hand-written.

- **Partisan probe.**
  - Week 1: drafts a *large* bill paid for by taxes and nominates loyalists.
  - Weeks 2-3: leans on the Fed and threatens the Moderate Democrats twice. The Senate rejects the loyalist Treasury and Defense nominees, 47-53 and 45-55. The experts sent instead are confirmed 51-49 in week 6.
  - Week 4: the House passes the bill **218-217**, while a Gulf crisis opens.
  - Weeks 5-6: moves the bill to reconciliation, loses the Senate twice, and blames the opposition. The opposition shifts to *obstruct*.
  - Week 7: the Senate passes the bill **50-50**.
  - Week 8: shows force for the fifth straight week. An **armed clash** follows, and it retaliates.
  - Week 9 onward: its funding offer carries its own priorities, and under an obstructing opposition **the government shuts down**.
  - Result: the most policy delivered (2.55); base approval 92, swing 44; the cabinet is complete only in week 10, after three of its nominees were rejected.
- **Dealmaker probe.**
  - Week 1: drafts a *medium* bill paid for with cuts, nominates an expert, and meets opposition leaders. The opposition shifts to *cooperate*.
  - Week 3: the House passes the bill 262-173.
  - Week 4: the Senate falls one short of 60. The dealmaker scales the bill back.
  - Week 5: the Senate passes it 61-39. Crossover secretaries are confirmed 71-29 and 70-30, and one of them leaks when overruled.
  - Week 8: a back-channel deal ends the Gulf crisis.
  - Result: less policy delivered (1.0); base 82, swing 54; a full cabinet; no shutdown.

---

## 4. The 12-week prototype

**Package:** `game.mandate.core.opening` (in `mandate-core`, no UI).

**Play:**
- `./mandate weeks [seed] [D|R] [unified|divided]` plays the prototype.
- `./mandate compare [seeds]` runs the strategy comparison.

### 4.1 Calendar

| Week | Scheduled |
|---|---|
| 1 | Inauguration; nothing drafted; three empty posts |
| 2 | Fed meeting; jobs report |
| 3 | CPI report |
| 3-5 | The crisis opens (seeded week; Taiwan or Gulf) |
| 6 | Stopgap offer due; jobs report |
| 7 | CPI report |
| 8 | Fed meeting; **funding deadline** at the end of the week |
| 9-12 | Shutdown weeks if it lapsed |
| 10 | Jobs report |
| 11 | CPI report |
| 12 | Report card and midterm outlook |

### 4.2 Actions (3 attention points a week)

| Action | Cost | Trade-off |
|---|---|---|
| Draft / amend the bill: size S/M/L × pay-for (borrow / revenue / cuts) | 1 | Bigger delivers more and is harder to pass. Each pay-for pleases one wing, angers another, and moves yields and the hawks. Amending carries a flip-flop penalty. |
| Negotiate with a bloc: projects (4 capital, +deficit) / appointment (6 capital, 2 tokens) / threat (own party only: a sure vote now, a grudge after) | 1 | Capital and tokens are finite; threats cost wing mood and relationships |
| Go public | 1 | Pressures moderates in both parties; costs opposition goodwill; diminishing returns |
| Call a vote (insist: 8 capital) | 1 | Leaders won't call a likely loser; forcing a vote has its own cost |
| Reconciliation | 0 | Simple majority, once only; −15% policy (Byrd); −10 goodwill |
| Nominate | 1 | Loyalist / expert / crossover (see §2.4) |
| Meet opposition leaders | 1 | +5 goodwill, −1 base wing |
| Lean on the Fed | 1 | Base +3, moderates −2, credibility −0.12 (yields rise, a cut becomes slightly more likely) |
| Release the oil reserve (only when oil > 80) | 1 | Gas eases; hawks object (deterrence −2) |
| Delegate a domain | 0 | Time back; cautious and generic play in that domain |

### 4.3 Systems and how they connect

```
 pay-for ──► deficit ──► 10-year yield ──► mortgages ──► swing voters
    │                        ▲   ▲
    ▼                        │   └── Treasury competence
 wing moods ──► bloc loyalty ──► whip count ──► vote ──► law ──► record, economy
    ▲                                   ▲
 cabinet picks ──► opposition goodwill ──► opposition strategy ──► shutdown odds, opp votes
                                                       ▲
 crisis move ──► escalation ──► oil ──► gas ──► CPI ──► Fed ──► yields
     ▲   └──► deterrence, leaks (secretary loyalty), aftermath
     └── intelligence estimate (State competence)
```

### 4.4 Source authenticity and what changed

The prototype re-implements the following *from the source games' rules*:

- **RD:**
  - bill size and pay-for choices;
  - three blocs per party per chamber;
  - committee chairs who can bottle a bill;
  - leaders who won't call losing votes;
  - the filibuster, reconciliation and the Byrd rule;
  - political capital;
  - whip concessions (projects, appointments, threats);
  - the transition team's loyalist, expert and big-name/crossover candidates;
  - confirmation votes;
  - advisers taking sides;
  - two-wing party factions;
  - the opposition strategy;
  - stopgap and shutdown choices;
  - a Taylor-rule Fed meeting on schedule;
  - monthly jobs and inflation releases.
- **BG:** the flip-flop penalty, group-level approval, and going public.
- **IC:** an escalation ladder with named rungs, and deterrence as the state variable. In IC, deterrence suppresses invasion in most runs.

Every place where a detailed mechanic was simplified or replaced is listed in §5.

---

## 5. Mechanic change log

Source mechanics are **not** deleted from the source repositories. These records say what the integrated design does instead, and why. "Revisit if" names the evidence that would reverse the change.

| ID | Source mechanic | Change | Justification | Revisit if |
|---|---|---|---|---|
| C-01 | RD 21-slot weekly calendar (and BG's) | → 3 attention points a week (prototype) or a month (F) | RD measured: an empty calendar costs only about 4 approval points over 3 years, so most clicks change nothing. Attention keeps the opportunity cost and removes the busywork. | Playtesters say they lack a sense of "being the president day to day" |
| C-02 | RD weekly cadence for 4 years / F monthly cadence | Prototype tests **weekly** for 12 weeks | The loop needs fine-grained feedback to test. The final cadence (weekly for set-piece periods, monthly otherwise) is decided after playtests (O-1). | — |
| C-03 | RD 33 bills, any number in flight | One flagship bill | Isolates the negotiation loop for testing. 1.0 has several bills competing for attention. | — |
| C-04 | RD 15 departments + 6 posts, readiness | 3 posts with competence and loyalty that feed specific systems | RD measured: readiness multipliers are ×0.85-1.15, so picks barely mattered. Three posts with distinct jobs make each pick a real decision. | Players want more cabinet texture (1.0 target: 4-6 posts) |
| C-05 | RD 9 staffers with morale | The chief of staff as delegation, plus "advisers take sides" via secretaries | Morale juggling had little decision value; the leak-when-overruled mechanic is the valuable part, and it is kept | — |
| C-06 | RD named swing members (cosmetic) | Removed; bloc "wants" lines instead | RD measured: individuals never vote, so the names added nothing mechanically | Bring back if individuals cast votes (FEATURE_PRIORITY E-tag) |
| C-07 | RD conference stage | Removed | A 2-week delay that always passes | — |
| C-08 | RD 12 appropriations bills | One stopgap deadline with 4 offers | The 12 bills are anonymous random counters; the decision is the offer | — |
| C-09 | RD opposition strategy re-chosen quarterly | Weekly, with hysteresis | Faster reaction suits a 12-week test; hysteresis prevents flip-flopping | It flips more than about twice per session in playtests |
| C-10 | RD crises: one card, one roll | Multi-week crisis; the adversary responds; hidden resolve with intelligence; a security dilemma | RD: odds ignored deterrence (0.55 fixed). One roll removes "information guides decisions". | Players find multi-week crises tedious |
| C-11 | IC operational war | Not in the prototype; escalation is decided at the presidential level | Pace and scope (see FEATURE_PRIORITY, Command Mode expansion) | — |
| C-12 | RD economy (weekly, many series) | Weekly yields, oil and gas plus slow inflation and jobs; **reported** data releases drive the Fed and voters | Reporting lag creates "new information" moments; RD's damping and never-decaying fiscal impulses are not ported | — |
| C-13 | BG approval by 30 groups / RD 17 groups | 3 groups (your voters, swing, opposition voters) | Enough to show the base-versus-centre trade-off on one screen; the cell model returns in M1 for maps | — |
| C-14 | RD press conferences, social posts | "Go public" with diminishing returns | RD and BG measured: the best answer is obvious from ratings (a quiz); posting 5 times a week is busywork | — |
| C-15 | F `PublicAddress` (monthly) | Same diminishing-returns rule, now tied to a live bill | The F batch showed address-spam as part of the dominant autopilot pattern | — |
| C-16 | RD stamina, health, sleep | Not modelled | No decision value (FEATURE_PRIORITY §1.5) | — |

---

## 6. Evidence from scripted probes (not from players)

### 6.1 The probes

| Probe | Plays |
|---|---|
| PARTISAN | Large bill on its own party's terms; loyalists; threats; reconciliation; leans on the Fed; show of force; priorities-or-shutdown |
| DEALMAKER | Medium centrist bill; expert + crossovers; outreach; concessions; diplomacy; clean stopgap or accept cuts |
| HAWK | Experts; small bill funded by cuts; force, then a strike; defense deal |
| TECHNOCRAT | Experts; medium paid-for bill; no Fed pressure; oil reserve; hold, then talk |
| DELEGATOR | Delegates all four domains and does nothing personally (a casual player) |
| IDLE | Answers decisions minimally |
| STRATEGIST | Reads intelligence, the Congress and the opposition's mood, and adapts (an approximation of an attentive expert) |

### 6.2 Results

These are means over 300 seeds per strategy, with the party alternating. Reproduce with `./mandate compare 300`.

**Unified government**

| Strategy | Approval | Base | Swing | Law % | Policy | Economy | Security | Clash % | Unity | Goodwill | Shutdown weeks |
|---|---|---|---|---|---|---|---|---|---|---|---|
| PARTISAN | 48.8 | **89.6** | 48.0 | 82 | **2.14** | −0.17 | 1.95 | 22 | **65.7** | −26.3 | 2.47 |
| DEALMAKER | **51.2** | 83.7 | **55.0** | 100 | 1.75 | 0.58 | 0.26 | 7 | 55.6 | **18.8** | 0.11 |
| HAWK | 50.5 | 85.1 | 53.2 | 92 | 0.94 | 0.49 | **3.08** | 11 | 61.0 | 4.5 | 0.71 |
| TECHNOCRAT | 51.1 | 87.2 | 53.6 | 100 | 1.68 | **0.62** | 0.92 | 7 | 61.9 | −0.1 | 0.40 |
| DELEGATOR | 50.8 | 87.1 | 53.1 | 100 | 1.69 | 0.54 | 1.21 | 16 | 62.0 | −0.3 | 0.50 |
| IDLE | 48.7 | 82.5 | 51.5 | 0 | 0.00 | 0.39 | 1.02 | 16 | 60.6 | −0.9 | 1.14 |
| STRATEGIST | 51.5 | 87.5 | 54.1 | 100 | 1.68 | 0.55 | 1.20 | 8 | 62.8 | 4.7 | 0.90 |

**Divided government:** partisan law rate 0%, approval 45.8. Dealmaker 100% (policy 1.38). Hawk 73%, technocrat 44%, delegator 13%. Strategist 100%, with the lowest clash rate (7%).

**Reading the tables:**
- Each hands-on philosophy leads on a different measure:
  - partisan: policy, base and unity;
  - dealmaker: approval, swing voters and goodwill;
  - hawk: security;
  - technocrat: the economy.
- No philosophy Pareto-dominates another, in either condition (tested).
- The partisan path is strong in unified government and dead in divided government. The dealmaker path works in both, at the cost of its base and its deterrence.
- Casual delegation matches hands-on play in unified government. In divided government it falls to a 13% law rate against 100% for the attentive probes.

### 6.3 How much strategy explains (η², 5 philosophies × 150 seeds)

| Measure | Unified | Divided | Reading |
|---|---|---|---|
| Approval | 0.27 | 0.73 | In unified government, luck (data releases, crisis draw) matters more than strategy for approval over 12 weeks |
| Policy | 0.46 | 0.61 | |
| Economy | 0.77 | 0.79 | Strategy-driven, but the absolute range is small |
| Security | 0.32 | 0.32 | Crises are mostly luck by design (hidden resolve), but reading intelligence still lowers clashes, from 16-22% to 7-8% |
| Unity | 0.68 | 0.23 | |
| Goodwill | 0.78 | 0.82 | |

The test requires η² ≥ 0.2 on every measure and ≥ 0.5 on at least three. Both hold in both conditions.

### 6.4 Problems the probes found and fixed during development

| Found | Fix |
|---|---|
| Showing force won 92-99% of crises; clashes never happened | A security dilemma (force provokes high-resolve rivals); a shorter ladder; a smaller push. A mutation test now fails if the dilemma is removed. |
| The partisan large bill passed 1% of the time (a dead path) | Gentler ideological slope; stronger party pull; threats as the partisan tool |
| Delegation plus free personal actions nearly dominated the best hands-on probe | The staff works at 50% efficiency and votes only at ≥ 60% odds; the casual probe takes no personal actions |
| Idle play was within 2 points of active play (no lasting consequences) | A lasting *record* for laws and crisis outcomes |
| Diplomacy ended 96% of crises after one success | Crises open one rung up; a deal requires de-escalating all the way down |
| A clash changed nothing (approval flat, no new situation) | A smaller rally, a lasting cost, an aftermath decision |
| Expert nominees were rejected in loops by the president's own senators | Own-party defections scale with controversy above a threshold |
| A refused vote still consumed the bill's weekly move; a refused decision was discarded | Validation before mutation; decisions are removed only after their handler succeeds |

### 6.5 Mutation checks (do the tests bite?)

- Removing the bond market's reaction to a pending bill makes `borrowingToPayMovesBondYieldsAboveRaisingRevenue` fail.
- Removing the security dilemma makes `theRightCrisisResponseDependsOnTheRivalsResolve` fail. This mutation was **not** caught before that test was written, which is why the test exists.

---

## 7. Known limitations (honest list)

1. **No player evidence.** Scripted probes do not get bored, misread screens, or discover exploits creatively. Fun, clarity and pacing are untested.
2. **The probes barely adapt.** Only STRATEGIST revises its plan, and it is a coarse approximation of an expert. Human players will find strategies we did not script, and may find dominant ones.
3. **Approval moves in a narrow band** (45.8-51.5 between strategy means). That is realistic for 12 weeks, but it may feel unresponsive. Playtests will tell.
4. **The economy's 12-week range is small.** Economic choices mostly pay off beyond the prototype's horizon, except through yields and gas.
5. **Every session gets a crisis** at weeks 3-5. In the full game, crises are probabilistic.
6. **The dealmaker's bill always passes** within 12 weeks (O-2). Its costs are elsewhere, but certainty is not ideal.
7. **Each probe makes the same crisis choice every week.** "Clash %" is therefore a property of the script, not a forecast of human outcomes.
8. **The CLI is a text prototype.** Information design (principle 5) will be judged on real screens in M4.

---

## 8. Playtest plan: getting independent player evidence

**Build:** `./mandate weeks`, with a facilitator logging, or a JavaFX vertical slice once it exists.

**Sessions:** 8-12 players. At least 4 are strategy gamers and at least 4 are politics enthusiasts who are not core gamers. Both parties are played. Both Congress types are tested.

| Hypothesis | Measure | Pass | Fail → action |
|---|---|---|---|
| H1. Players perceive real trade-offs | After each decision: "Was there a clearly right answer?" | 70% or more of decisions rated "no" | Find the options rated obvious; rebalance or cut them |
| H2. Consequences are understandable | After each briefing, the player explains *why* one number moved | 75% or more correct | Rewrite the briefing lines; add cause tags |
| H3. Information is used | The share of decisions where the player cites the whip count, intelligence or a data release | 50% or more | Make the information more prominent, or more decisive |
| H4. Different players take different paths | Cluster players' action logs (the probes' strategies serve as centroids) | 3 or more distinct clusters, and no cluster with every top score | Look for an emergent dominant line |
| H5. Failure is interesting, not punishing | Post-session: "When things went wrong, did you have something interesting to do?" | Median 4 of 5 or higher | Add or reshape follow-up options |
| H6. Delegation serves casual players | Casual players delegating 2 or more domains rate their session 4/5 or higher, without confusion | Majority | Better delegation feedback; staff explain their choices |
| H7. The 12 weeks hold attention | Voluntary continuation after week 6; session-length self-report | 80% or more continue | Cut actions or decisions that feel administrative |

**Instruments:**
- action logs (the `history()` command log is already recorded);
- think-aloud;
- a short survey after each session;
- a facilitator note whenever a player asks "what does this do?".

**Do not** report the game as fun or successful from these probes. Report each hypothesis as pass or fail, with the evidence.

---

## 9. Open questions and next steps

- **O-1 Cadence.** Weekly governing turns give a tight loop, but 4 years of them would be 208 turns. Proposal: monthly turns that zoom into weekly turns for set pieces (the first 100 days, crises, deadlines, campaigns). Decide after H7.
- **O-2 Dealmaker certainty.** Bipartisan bills should sometimes die. Candidates: an opposition primary season, outside events, and stricter Senate maths.
- **O-3 Integration.** Fold the prototype's systems into `Game` as the governing layer:
  - the bloc whip model with "wants";
  - cabinet posts;
  - the stopgap;
  - the crisis model replacing `World`'s monthly ladder events;
  - group approval, which feeds the M1 cell model.
- **O-4 More bills at once,** so legislation competes for attention (C-03).
- **O-5 Data-driven content.** Crisis kinds, nominee archetypes and stopgap offers move to the M2 content files.

New backlog items B-39 to B-44 are added in `DEVELOPMENT_ROADMAP.md`.
