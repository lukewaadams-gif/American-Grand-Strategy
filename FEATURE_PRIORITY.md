# Feature priority

How every mechanic in the three source games was judged, and what the integrated game keeps.

**Classes:** **A** essential to the first commercial release · **B** valuable, but later · **C** redundant with another subsystem · **D** too complicated for its gameplay value · **E** unproven, needs playtesting · **F** unsuitable for the integrated design.

**Release targets:** `MVP` (playable alpha / Early Access) · `1.0` · `EXP` (post-release expansion) · `CUT`.

**Depth column:** what the code actually does, verified in the audit (`docs/SOURCE_AUDIT.md`), not what the README claims.

---

## 1. Full classification

### 1.1 Electorate, polling, elections

| Mechanic | Source | Verified depth | Class | Target | Reasoning |
|---|---|---|---|---|---|
| 96-cell electorate (16 blocs × 3 ages × 2 sexes × 51 states), calibrated to 2024 | BG `Elect`/`Vote` | **Deep and real.** Reproduces 2024 within 0.2 points | **A** | 1.0 (M1) | This is the credibility engine. Ported behind `ElectorateModel`, it serves president, Senate, House and approval, replacing four separate formulas. The MVP ships a transparent uniform-swing model behind the same interface. |
| Issue salience × group-specific views (18 issues) | BG | Real | **A** (simplified) | MVP: 11 issues | Issue choice is the core campaign decision. 11 issues read clearly; 18 is more than players can track. |
| Polling with house effects, sample size and method, ~30 crosstab groups | BG `Polls` | Real | **A** (simplified) | MVP: noisy averages + hidden systematic miss; 1.0: 3-4 pollsters, crosstabs for 8 groups | The hidden systematic miss is the best idea in the polling code. Choosing sample size and method is **D** (busywork). |
| Correlated election error | BG (polls only), new | **Broken in BG:** error moves the polls, not the results, so outcomes barely vary | **A** | MVP ✅ built | It fixes the 40-0 balance and makes election night tense. |
| Forecast / path to 270 (Monte Carlo) | BG `Forecast` | Real, but its static cache can return stale results | **A** | 1.0 | A cheap, high-value view. It must be generated from the *same* error model as the results. |
| Election-night animation, state by state | BG `Night`, RD | Real presentation | **A** | 1.0 | The payoff moment of each act. |
| County-level election night (3,100 counties) | BG `Counties` | **Cosmetic:** a uniform swing over 2024 county results; licence unverified | **D** | CUT for 1.0; EXP maybe | Costly data and rendering that changes no decision. A state-by-state night with key "bellwether counties" as flavour text gets 80% of the drama. |
| Results explorer (groups, swing map) | BG `Results` | Real | **B** | 1.0 (light) | Good for learning and for streamers. Group detail needs the M1 cell model. |
| Run-100 batch simulation (in-game) | BG `Batch` | Real, but mutates the player's settings | **B** | Dev tool now (`mandate batch`); player-facing post-1.0 | As a *dev* tool it is essential: it found the 40-0 bug. |
| Custom conditions (mood, turnout, swings, error, NPV compact, 2030 map) | BG `Custom` | Real (per-cell shifts) | **B** | 1.0: scenarios; EXP: full editor | Scenario presets deliver most of the value. A full editor is a modding feature. |
| National Popular Vote compact, 2030 census map | BG | Real | **B** | 1.0 (2032 should use the projected 2030 apportionment) | Re-electing on a new map is a nice touch. |
| ME/NE congressional-district electors | — (neither game splits them) | Missing | **B** | 1.0 | Small, cheap, and expected by the audience. |

### 1.2 Campaign

| Mechanic | Source | Verified depth | Class | Target | Reasoning |
|---|---|---|---|---|---|
| Weekly campaign turns | BG | Real | **A** | MVP ✅ | |
| 21-slot schedule (7 days × 3), with travel and stamina | BG `Week`, RD calendar | BG: real. RD: barely matters (an empty calendar costs about 4 approval points over 3 years) | **D** | CUT → 3 *stops*/week | Twenty-one placements a week for 16+ weeks is the busywork pillar 2 forbids. Three stops (rally or fundraiser) keep the trade-off and remove the clicking. |
| Ads: TV across ~148 media markets with market prices, plus 6 digital platforms | BG `advertising`, `Digital` | Real | **A** (simplified) → TV by state, one digital line | MVP: per-state ads ✅; 1.0: national digital buy | Market-level pricing is credible but invisible to the player. Nielsen DMA data is licence-restricted. |
| Field chain: offices → organizers → volunteers → doors → persuasion/turnout | BG `field` | Real, deep | **A** (simplified) | MVP: field $ by state that decays slowly ✅; 1.0: offices as a visible, durable investment | Keep the decision ("build early, it compounds"). Drop the chain's middle steps. |
| Money: small-dollar lists, bundlers, donor fatigue, self-funding | BG | Real | **A** (simplified) | MVP: weekly income + fundraisers ✅; 1.0: donor fatigue | |
| Super PACs | BG | Automatic; the player only gets one flag | **C** | CUT | No decision. Fold into the opponent's resources and the difficulty setting. |
| Message, frame, attack line, issue focus with credibility build-up | BG `Message`/`Issues` | Real | **A** | MVP: message + staying-on-message bonus ✅; 1.0: attack line | |
| Policy promises (46) that move groups, donors and flip-flop | BG `Campaign.promise` | Real | **A** | 1.0 | Pillar 1 depends on this: **promises become the governing agenda**. BG drops promises at inauguration; we carry them into Act II. |
| Debates: approach + lines + rebuttals, prep, host bias | BG `Debates` | Deep | **A** (simplified) | MVP: 4 postures with counters + prep reveal ✅; 1.0: 2-3 questions per debate | Line-by-line play is BG's best set-piece but long. Test the compact form first (E). |
| Debate negotiation (count, dates, networks) | BG | Real | **D** | CUT | Low value for the time it takes. |
| VP choice; VP stops; VP debate | BG `VpPlan` | Small but real | **B** | 1.0: VP pick (home state + group appeal); VP debate EXP | |
| Convention (platform fights, speakers, acceptance speech) | BG `Convention` | Mixed logic and UI; one-roll outcomes | **E** | 1.0: one-screen acceptance speech that sets the bounce | Fun once, repetitive after. Keep it short. |
| Press conferences | BG `Press`, RD | **One roll; the best answer is obvious from ratings** | **D** (as built) / **E** (redesigned) | 1.0 only if redesigned as trade-offs between groups | As built, it is a quiz. It needs answers that please one group and anger another. |
| Scandals over weeks (apologise / deny / counterattack / wait) | BG `Scandal`, RD | Branching, but mostly single rolls | **A** (as events) | MVP: event chains ✅ (gaffe, October surprise) | Use the generic event system, not a bespoke subsystem. |
| Named campaign staff (6) with skills, traits, salaries, poaching | BG `Staffers` | **Applies only to the human player** | **D** | CUT for 1.0 | It creates a hidden asymmetry and little decision value. |
| Ad studio (tone, issue, audience, messenger, focus group) | BG `AdStudio` | Human-only; targeting is a flat +0.05 | **D** | CUT | A mini-game with almost no effect. |
| Coattails: joint Senate rallies and money | BG `Senate` | Formula off the presidential margin | **B** | 1.0 | It links the campaign to the governing Congress, which serves pillar 1. |
| Rally crowd sizes, real podcasts in tiers | BG `Rallies`/`Podcasts` | Flavour plus small effects; real trademarks | **F** (real podcasts) / **B** (fictional media tours) | 1.0: "media tour" as a rally variant | Legal risk, and redundant with rallies. |
| Name recognition | BG | Real (matters mostly in primaries) | **B** | With primaries (EXP) | Nearly constant in a general election. |
| Chief of staff weekly note | BG | Text | **A** | 1.0 | Cheap onboarding; doubles as the AutoPilot explanation. |
| Achievements (28/32), Hall of Fame | BG, RD | Real | **A** | 1.0 (Steam achievements) | Commercial must-have. |
| Guided tutorial | BG, RD | Overlay steps | **A** | 1.0 | Essential for accessibility. |
| Campaign-manager role (candidate can refuse or fire you) | BG `Manager` | Thin (71 lines) | **E** | EXP | An interesting fantasy, but it splits the game's identity. |
| Watch mode (AI vs AI) | BG `Watch`, RD | Thin | **B** | Post-1.0 | Useful for attract mode and streamers. |

### 1.3 Primaries and nomination

| Mechanic | Source | Verified depth | Class | Target | Reasoning |
|---|---|---|---|---|---|
| Invisible primary → primaries → delegates, DNC 2028 calendar | BG `Primary` | Real; delegate math is approximate (no superdelegates) | **B** | EXP-1 ("The Long Road") | High value for the fan base, but it doubles campaign length, the 2028 calendar will age, and it needs a large fictional cast. Ship the general first. |
| Dropouts and endorsement bidding | BG `Endorse` | Real odds; cabinet promises are cosmetic | **B** | EXP-1 | Make the promises binding (they feed the cabinet) when built. |
| Contested convention | BG `Contested` | ~200 lines; a cabinet offer always moves 25-40% of a rival's delegates | **E** | EXP-1 | Exploitable as built. |
| Independent run with state-by-state ballot access | BG `Ballot` | Real | **B** | EXP-2 | A distinctive mode, but a separate design problem: third-party math and House contingent elections. |
| Third parties (Green, Forward, Libertarian, America Party) | BG | Heuristic multipliers; real candidates | **B** | 1.0: one fictional third party as spoiler; EXP: playable | |
| 39 real politicians with ~21 ratings, caricature portraits | BG `Cand`, `Portrait` | Real data | **F** | CUT (fictional cast) | Legal risk (R-02); it also dates the game. Procedural portraits are reusable for fictional characters (**B**). |
| Candidate creator (point-buy, god mode) | BG `Creator`, RD | Real | **A** | 1.0 | Lets players put themselves in the game without real-person risk. |

### 1.4 Governing: Congress and law

| Mechanic | Source | Verified depth | Class | Target | Reasoning |
|---|---|---|---|---|---|
| Bloc-level spatial vote model (3 blocs per party per chamber, loyalty, deficit hawks) | RD `blocLogit` | **Deep, the best governing code** | **A** | MVP ✅ (compact version) → M3 full port | It creates the central governing decision: who do I need, and what does it cost? |
| Whip count shown before committing | RD | Real | **A** | MVP ✅ | Pillar 3. |
| Bill design (size, scope, pay-fors) | RD | Real | **A** | 1.0 | Two sliders (size, pay-for) on top of strategy. Gives the base-versus-deficit-hawk trade-off. |
| Strategies: party line / bipartisan deal / reconciliation / executive order | BG Govern (+RD) | BG: executive orders always succeed. RD: the Byrd rule is a ×0.85 multiplier | **A** | MVP ✅ (orders can be blocked in court; reconciliation once per year; Byrd rule enforced via `requires60`) | |
| Committee chairs bottling bills, leaders holding votes | RD `chairOk`, `hold` | Real | **A** | 1.0 (M3) | Divided government should *feel* different. |
| Named swing members to work individually | RD | **Cosmetic** (they only add to their bloc's "sweet") | **E** | 1.0 if individuals actually vote | Characters make Congress memorable. They must matter mechanically or be cut. |
| Conference, second chamber | RD | A 2-week delay that auto-passes | **C** | CUT | No decision. |
| Filibuster / nuclear option | BG, RD | Real | **A** | 1.0 | A big, irreversible decision with a backlash risk: ideal strategy content. |
| Veto / override; Congress's own bills | RD | Real | **A** | 1.0 | The opposition must act, not only react. |
| Political capital | BG, RD | Real | **A** | MVP ✅ | The currency of the governing game. |
| Budget request, 12 appropriations bills, shutdowns | RD `Budget` | The 12 bills are anonymous random counters; the request is often never applied (bug) | **A** (as annual set-pieces) / **D** (12 bills) | MVP: shutdown event ✅; 1.0: an annual budget fight (one decision chain) | Keep the drama, drop the bookkeeping. |
| Debt limit (clean / trade cuts / brinkmanship / 14th Amendment) | RD | Real decision cards | **A** | 1.0 | An excellent dilemma, and it ties to the economy. |
| 33 modern bills (RD) / 28 (BG) | both | Data | **A** (merge) | MVP: 16 ✅; 1.0: about 40 | |
| 94 historical bills | RD | Era-gated | **F** | CUT (EXP-3 history pack) | |
| Agenda priorities that carry from the campaign | BG Govern (3 priorities) | Shallow | **A** | MVP ✅ (whip bonus; unfulfilled items cost you in 2032) | Pillar 1. |

### 1.5 Governing: the executive, courts, scandal

| Mechanic | Source | Verified depth | Class | Target | Reasoning |
|---|---|---|---|---|---|
| 9 senior aides with skill, loyalty, morale and leaks | RD `Exec` | Scattered ×0.85-1.15 multipliers | **D** | CUT → 3 key posts in 1.0 (chief of staff, NSA, Treasury) | Nine staffers × morale is management texture with little decision value. |
| Cabinet (21 posts), confirmations, readiness | RD | Confirmations are bloc votes; readiness gates implementation | **B** | 1.0: 4 key secretaries with a confirmation fight; EXP: full cabinet | The confirmation fight is good. Twenty-one posts is a spreadsheet. |
| Delegation (plan week, whip, budget, pardons) | RD `Delegate` | Small per-week increments | **A** (as AutoPilot) | MVP ✅ (AutoPilot) | Accessibility for the secondary audience. |
| VP missions | RD | Small per-week increments | **C** | CUT (fold into attention: "send the VP" = +1 attention for low-stakes actions) | |
| 49 executive actions + regulations + emergencies (CRA, comment periods) | RD `Actions` | Table-driven; a revoke bug | **A** (as bill strategy) / **B** (library) | MVP: executive order as a strategy ✅; 1.0: about 15 standalone actions | |
| Lawsuits: district → appeals → Supreme Court, 9 justices voting by philosophy | RD | District/circuit courts are scalars; SCOTUS is a real per-justice model | **B** | 1.0: abstract court risk + vacancy event ✅; EXP: named fictional court | Real justices are **F** (legal risk). |
| Lower-court judges | RD | Deterministic (239 every run) | **D** | CUT | No decision. |
| Oversight, investigations, privilege, contempt | RD | Decision cards | **B** | 1.0 under divided government | Gives the midterm loss a gameplay consequence. |
| Impeachment and Senate trial | RD | Seat-by-seat rolls | **E** | 1.0 if scandal pressure is tuned | A great late-term threat; must stay rare. |
| Pardons/clemency | RD | Cards | **F** | CUT | Real-world sensitivity, little strategic value. |
| Personal health, stamina, sleep, stress (RD) | RD | Linear formulas; one illness roll | **D** | CUT | Busywork without agency. |

### 1.6 Economy and approval

| Mechanic | Source | Verified depth | Class | Target | Reasoning |
|---|---|---|---|---|---|
| Macro model: output gap, Okun, Phillips with anchored expectations, Taylor rule, debt | RD `Economy` | Textbook structure, **over-damped**; fiscal impulses never decay; interest cancels | **A** (rebuilt) | MVP ✅ (monthly, decaying shocks, tested so a recession raises unemployment by more than 1.2 points) | Keep the structure, fix the dynamics. |
| Treasury yields, mortgages, stocks, oil | RD | AR(1) series | **B** | 1.0: oil and the 10-year; stocks as flavour | Oil matters (world → economy). |
| Kitchen-table prices (gas, groceries, rent, insurance) | RD | AR(1) | **B** | 1.0 (feeds approval through cost of living) | Very legible to players. |
| Industry indices (housing, manufacturing, AI, farms, energy) | RD | AR(1), no decisions | **D** | CUT | |
| Fed chair appointment / Fed credibility | RD (credibility fixed) | Missing lever | **E** | 1.0 | An interesting long-term dilemma: a dovish chair helps in 2032 and costs on inflation later. |
| Tariffs by country, sanctions, aid | RD `Diplo` | Real, with retaliation | **B** | 1.0: tariff level against China + allies (not 40 countries) | |
| National approval (economy, honeymoon, events, polarisation band) | RD, BG | Real | **A** | MVP ✅ | |
| Approval by group and state from cells | RD | **Display-only** (zero-sum redistribution) | **A** (made real) | 1.0 (M1) | Approval by state *must* feed midterms and 2032 through the shared electorate, or it is decoration. |
| Party factions' mood (two wings) | RD | Distance-based moods | **A** | 1.0 | It turns "your base" into a constraint: bipartisan deals cost faction mood and lower turnout. |
| Opposition strategy (cooperate / pick fights / obstruct) | RD | A threshold on approval | **A** | 1.0 | Makes divided government feel different. |
| Capital Ledger newspaper / news ticker | RD | Real presentation | **A** | 1.0 | The main feedback channel: consequences shown as headlines. |
| Oval Office as a clickable home screen | RD | UI | **B** | 1.0 if art budget allows | Strong identity; the layout is a UI decision. |
| Social media posts, interviews, Oval addresses, State of the Union built line by line | RD | Small nudges | **C/D** | MVP: "Address the nation" ✅ (diminishing returns); 1.0: State of the Union as an annual set-piece | Five posts a week is busywork. |
| Commissioned polls on any issue | RD | Real | **B** | 1.0 | |
| Press corps (18 outlets with a tone) | RD | Simulated numbers, real outlets | **F** (real outlets) / **B** (fictional) | 1.0: 4-5 fictional outlets with leanings | |

### 1.7 The world, crises, war

| Mechanic | Source | Verified depth | Class | Target | Reasoning |
|---|---|---|---|---|---|
| Hot spots as tension dials (Taiwan, Ukraine, Middle East, Korea, terror/cyber, border/cartels) | RD `theaters` | Scalar random walks | **A** (fewer) | MVP: 3 ✅; 1.0: 5 | |
| Taiwan escalation ladder (calm → drills → quarantine → blockade → war) | RD (stages), IC (prewar) | RD: war odds **ignore deterrence**. IC: deterrence suppresses invasion in most runs | **A** | MVP ✅ (deterrence lowers drift and can stop war at the brink) | The single best "preparation decides crises" system (pillar 4). |
| 20 crises / 28 domestic events / disasters / pandemic | RD | One-roll cards | **A** (as content on the generic event system) | MVP: 9 events + 4 ladder events ✅; 1.0: about 80 | Content, not code. Every event must pose a real trade-off; the MVP set is reviewed for "obvious answer" options. |
| 40 countries with relationships, calls, state visits, summits (3 rounds), G7/NATO/UN cycle | RD `Diplo` | Flat increments; summits are real | **D** (40 countries) / **B** (summits) | 1.0: 6 key relationships + an annual summit | Breadth with no decisions behind it. |
| Military operations (10), covert programs (7), force posture by region | RD | One-roll tables | **B** | 1.0: posture in 3 regions (it feeds deterrence) | |
| Historical wars (time-stepped front, troops, support) | RD `War` | Real, but only for 8 historical wars | **F** | CUT | |
| **Detailed Taiwan wargame** (salvos, magazines, amphibious, 25 ground sectors, blockade energy, 13 economies, escalation, hourly turns) | IC (all) | **Deep and real**; separable headless; seed, static-state and AI-negotiation bugs | **B** for the integrated game; **D** as default play | EXP-1 "Command Mode" (opt-in when war breaks out); **1.0 uses it offline to calibrate the abstract resolver** | The best simulation in the portfolio, but at hourly turns it is a different game. Making the presidential game depend on it would multiply UI, QA and sensitivity cost. Contract: `TheaterScenario` → `TheaterOutcome` (already in the core). |
| Pre-war crisis phase (posture, mobilisation, intel with deception) | IC | Real | **B** | 1.0: abstracted as the ladder; EXP: full | |
| Munitions stockpiles | IC | Real | **A** (abstract) | MVP ✅ (one munitions dial built by deterrence spending) | A long-lead investment decision for the president. |
| Allied basing decisions (Japan, the Philippines…) | IC `Diplo` | Real thresholds | **A** (abstract) | MVP: Japan basing flag ✅; 1.0: earned through diplomacy | |
| Nuclear-risk meter / nuclear use | IC | A weighted index; nuclear use only via a player button | **F** (nuclear use) / **B** (risk meter) | CUT nuclear use; keep risk as an escalation constraint in EXP-1 | Sensitivity, and a "lose button" with no strategy behind it. |
| Decapitation strikes on Taipei; real hull and unit names | IC | Real | **F** for the base game | EXP-1 behind content review | |
| Two-player hot-seat | IC | Real | **F** | CUT | Not applicable to a single-president game. |
| After-action review vs. the CSIS base case | IC | Real | **B** | EXP-1 | |

### 1.8 Meta and technical

| Mechanic | Source | Verified depth | Class | Target | Reasoning |
|---|---|---|---|---|---|
| 25 historical eras (1933-2025), 108 turning points | RD | Real content | **F** | CUT (possible EXP-3 standalone) | Product focus and testing cost. |
| Cross-game save import (RD reads BG's raw serialized save) | RD `JSer` | Brittle; the `evI`/`evO` bug | **C** | CUT (single game, single save) | |
| Browser version (`index.html`, machine-translated) | BG, RD | **The generator is missing from the repositories** | **E** | Post-1.0 (TeaVM or similar) / demo | Can't be rebuilt today. A web demo is valuable for marketing, but not on the critical path. |
| Autosave / continue | all | Java serialization | **A** | MVP: command-log saves; 1.0: JSON snapshots | |
| Synthesised audio | BG, RD | Code-generated | **B** | Replace with licensed audio in 1.0 | |
| Code-drawn portraits | BG | 693 lines | **B** | 1.0 (fictional faces) | Saves the art budget; needs a style pass. |
| Legacy score with visible parts | BG epilogue, RD C-SPAN verdict | Hand-set weights | **A** | MVP ✅ | |
| Epilogue: "one year later" | BG | Text | **A** | 1.0 | Cheap, and emotionally strong. |
| Presidential library | RD | UI | **D** | CUT | |

---

## 2. Scoring of the important features

The scale runs 1-5, and 5 is always good. So for development cost, 5 means *cheap*. "Comm." is commercial differentiation.

| Feature | Strategic depth | Agency | Accessibility | Replayability | Technical quality of source | Credibility | Dev cost | Comm. | Σ | Verdict |
|---|---|---|---|---|---|---|---|---|---|---|
| Shared electorate (cell model) | 4 | 3 | 4 | 4 | 5 | 5 | 3 | 5 | 33 | A, 1.0 |
| Campaign week (stops, ads, field, message) | 4 | 5 | 4 | 4 | 4 | 4 | 4 | 4 | 33 | A, MVP |
| Debates (posture read) | 3 | 4 | 5 | 3 | 4 | 3 | 5 | 3 | 30 | A, MVP |
| Whip count + strategies + capital | 5 | 5 | 3 | 4 | 4 | 5 | 4 | 5 | 35 | A, MVP |
| Promises → agenda → 2032 record | 5 | 4 | 4 | 5 | 2 | 4 | 4 | 5 | 33 | A, MVP |
| Monthly attention economy | 4 | 5 | 5 | 3 | n/a (new) | 3 | 5 | 4 | 29+ | A, MVP (E: tune) |
| Economy (monthly, legible) | 3 | 3 | 4 | 4 | 2 | 4 | 4 | 3 | 27 | A, MVP |
| Taiwan ladder + abstract theater | 4 | 4 | 4 | 4 | 2 (RD) | 4 | 4 | 5 | 31 | A, MVP |
| Event system (data-driven) | 3 | 4 | 5 | 5 | 3 | 3 | 5 | 3 | 31 | A, MVP |
| Midterms through the shared model | 3 | 2 → 4 with a pre-midterm campaign | 5 | 4 | 3 | 5 | 4 | 4 | 30 | A, MVP (auto) / 1.0 (played) |
| Detailed Taiwan wargame (Command Mode) | 5 | 5 | 1 | 4 | 4 | 5 | 1 | 4 | 29 | B, EXP |
| Primaries | 4 | 4 | 3 | 5 | 4 | 4 | 2 | 4 | 30 | B, EXP |
| 9 staffers / 21 cabinet posts | 2 | 2 | 2 | 2 | 3 | 3 | 3 | 2 | 19 | D, cut |
| 21-slot calendar | 2 | 3 | 1 | 2 | 3 | 3 | 3 | 2 | 19 | D, cut |
| County election night | 1 | 1 | 4 | 2 | 3 | 3 | 2 | 3 | 19 | D, cut |
| Historical eras | 3 | 3 | 3 | 5 | 3 | 4 | 1 | 3 | 25 | F (product focus) |

---

## 3. Feature cards (the important features)

Each card answers four questions: what decision the feature creates, why that decision matters, how the player sees the consequences, and what makes it interesting.

### 3.1 The campaign week · MVP ✅
- **Decision:** three stops per week (rally in a state or fundraise), money split between ads and field across states, and one message.
- **Why it matters:** money spent on ads fades in weeks; field persists. Rallies are cheap but limited. Fundraising buys future ads at the cost of presence today. Effects show diminishing returns and are measured against the opponent's spending.
- **Consequences visible:** weekly state polls (noisy, with a hidden systematic miss), cash, and the electoral-vote count implied by the polls. The 1.0 forecast adds win probability and the tipping-point state.
- **Why it's interesting:** the classic *defend versus expand* tension, plus an intertemporal trade (early field compounds, late ads hit hardest), against an AI that reads the same polls and is predictable enough to counter.

### 3.2 Debates · MVP ✅
- **Decision:** one of four postures (attack, substance, relatable, guarded). Each draws on a different rating, carries different variance, and counters one other posture. Prep notes reveal the opponent's tendency.
- **Why it matters:** a swing of up to ±3 points, three times per campaign. Often the biggest single moves of the race.
- **Consequences visible:** the post-debate verdict, with the swing shown; a poll bump the following week.
- **Why it's interesting:** a *read*, not a quiz. Guarded is safe and low-variance; attack is a gamble. The AI sometimes counters you instead of playing to its strength. (E: playtest whether four postures are enough or whether 2-3 question beats are needed.)

### 3.3 The whip count and legislative strategy · MVP ✅
- **Decision:** which bill to push this month (2 of 3 attention points), by which route (party line, bipartisan deal at 60% payoff, reconciliation once a year with a unified Congress, or an executive order at 40% payoff and 35% court risk), and how much political capital to commit.
- **Why it matters:** laws move approval and the economy, and the agenda you promised is judged in 2032. A failed vote costs capital and approval. Capital is finite and rebuilds with approval.
- **Consequences visible:** the whip count by bloc and chamber with a pass probability *before* committing; the vote tally after; the law's economic effect in the monthly numbers.
- **Why it's interesting:** a different puzzle under every Congress. A unified government tempts you to overreach. A divided one forces deals that anger your base (1.0: faction mood). The midterm reshapes the puzzle halfway through.

### 3.4 Promises come due (agenda continuity) · MVP ✅, deepened in 1.0
- **Decision:** at transition, pick three priorities from your party's agenda. In 1.0, campaign promises pre-fill and constrain the list.
- **Why it matters:** agenda bills get whip help, a passage bonus and legacy points. Each unfinished one lowers your 2032 environment.
- **Consequences visible:** an agenda tracker on the home screen, and lines in the 2032 campaign coverage ("broken promise on housing").
- **Why it's interesting:** it turns the whole game into one story. Over-promising wins 2028 and haunts 2032.

### 3.5 Monthly attention · MVP ✅ (E: tune)
- **Decision:** three attention points a month across legislation (2), the bully pulpit (1, diminishing returns), Pacific deterrence (1) and diplomacy (1).
- **Why it matters:** every month you choose between the domestic agenda, approval and foreign preparation. Neglect is invisible until it isn't.
- **Consequences visible:** the monthly dashboard (approval, capital, economy, tensions) and a news line per action.
- **Why it's interesting:** opportunity cost is the strategic core. The MVP batch shows the dominant pattern is "deter, push the best-odds bill, then address the nation". That is too formulaic, so 1.0 adds more distinct actions and event-driven demands on attention.

### 3.6 The economy · MVP ✅
- **Decision:** indirect. The fiscal size of the laws you pass, responses to shocks (stimulus versus restraint) and, in 1.0, the Fed chair.
- **Why it matters:** growth, unemployment and inflation drive approval and the 2032 environment.
- **Consequences visible:** monthly numbers with one-line explanations ("unemployment rose: the recession shock and higher rates"). Every equation is legible (see `ARCHITECTURE.md` §6).
- **Why it's interesting:** stimulus helps now and can cost later through inflation, the Fed and the deficit. Recessions arrive about once a decade, so some terms face one and some don't.

### 3.7 The Taiwan ladder and the war decision · MVP ✅
- **Decision:** at each rung (drills, quarantine, blockade), choose an escalating or de-escalating response. Every month, choose whether to spend attention on deterrence or diplomacy. If war comes: intervene, arm and sanction, or stand aside.
- **Why it matters:** deterrence lowers the tension drift and can stop the war at the brink. Deterrence, munitions, Japanese basing and warning time set the war's odds. The outcome moves approval, the economy and the legacy score.
- **Consequences visible:** a tension gauge, the current rung, and deterrence and munitions dials. The war result is a short after-action summary.
- **Why it's interesting:** a long-horizon insurance problem. Paying for deterrence early is invisible when it works. (Batch result: a president who never invests in deterrence and accommodates every escalation reaches war in about 15% of terms, and Taiwan falls in about two-thirds of those wars. A diligent, deterring president avoids war entirely. Crises themselves are too common: 78% of terms reach at least the drills rung, see backlog B-38.)

### 3.8 Midterms · MVP (automatic) → 1.0 (played)
- **Decision (1.0):** a short campaign (where to send money and the president's time, and what to run on), plus the month-by-month record that precedes it.
- **Why it matters:** the House majority and the Senate map for the last two years. It decides which legislative strategies remain.
- **Consequences visible:** a midterm election night; the new whip counts.
- **Why it's interesting:** the real Class I Senate map of 2030 (33 seats with their 2024 holders) runs through the same electorate as everything else.

### 3.9 Re-election · MVP ✅
- **Decision:** as in the 2028 campaign, plus the weight of your record (approval, growth, inflation, unfinished agenda, the war outcome).
- **Why it's interesting:** the same mechanics in a new context. You are now defending a record against a fresh challenger, and players find out which of their 2029-31 choices mattered.

---

## 4. Scope by release

### MVP: playable alpha (target: Early Access candidate, without art)
Everything marked ✅ exists today in the foundation. The MVP is complete when this list is also done:
- [x] General election 2028 (weekly): stops, ads, field, message, debates, events, polls with hidden error, correlated results
- [x] Transition: agenda
- [x] Governing (monthly): attention, four bill strategies, capital, the bloc whip model, the economy, approval, three hot spots, the Taiwan ladder and abstract war, events
- [x] Automatic midterms through the shared electorate model (real 2030 Senate class)
- [x] Re-election 2032 and legacy score
- [x] Deterministic engine, command-log replay, autopilot, balance batch, tests
- [ ] Desktop UI vertical slice: home dashboard, US map, whip-count screen, event cards, election night (M4)
- [ ] Save/load (command log on disk) and settings
- [ ] Data-driven content files with a validator (M2); 40 events, 30 bills
- [ ] Tutorial for the first campaign week and the first governing month
- [ ] Balance gates in CI (§5 of the roadmap)

### Commercial 1.0
- The ported 96-cell electorate (M1): approval by group and state that actually drives midterms and 2032
- Campaign promises carried into governing; party factions and an opposition strategy
- A full legislative path (committee chairs, leaders, veto/override, Congress's own bills, the filibuster nuclear option); an annual budget fight; the debt limit
- The economy with oil, yields and prices people feel; a Fed chair appointment; tariffs on China
- 5 hot spots, about 80 events, about 40 bills, an annual summit, regional force posture
- A played midterm; VP pick; coattails; an acceptance speech; a forecast with path to 270; a state-by-state election night
- Candidate creator, a fictional cast of 12 or more, procedural portraits
- An abstract theater calibrated offline against the First Island Chain engine (M5)
- Steam: achievements, cloud saves, Steam Deck; Story, Standard and Hard difficulty; accessibility (colour-blind map palettes, scalable UI, full keyboard control)

### Post-release expansions
- **EXP-1 Command Mode:** play the Taiwan war at operational scale with the First Island Chain engine, launched from the war decision. Includes the pre-war crisis phase and the after-action review. *Prerequisites: fix IC's seeding, static state, AI negotiation and 75-day stop; content review.*
- **EXP-1b The Long Road:** primaries, dropouts and endorsements, a contested convention (exploit fixed), name recognition.
- **EXP-2 Independents and third parties:** ballot access, a playable third party, House contingent elections.
- **EXP-3 Eras (standalone or DLC):** selected historical presidencies, using Resolute Desk content re-authored onto the event and bill system.
- **Free updates:** a scenario editor, modding (data files), a weekly challenge seed, a web demo.
