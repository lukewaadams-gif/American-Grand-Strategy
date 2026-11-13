# The economy: design note for presidency milestone 4

**Status:** approved on 13 November 2026, with the recommendations on the three questions at the end: the Cabinet Room only shows the table in this milestone; the baseline is CBO's real projection; and scores show their numbers, with a one-line plain reading above them. The milestone builds it in the steps at the end.

## What was decided (13 November 2026)

| Question | Decision |
|---|---|
| The model | A **new structural model**. Fiscal multipliers depend on slack and on the Fed's reaction. Deficits crowd out investment through interest rates. Policy moves long-run supply: labour, productivity and capital. Resolute Desk's sectors sit on top. |
| How laws act on it | Laws move **Resolute Desk's policy table**. Each level of each policy carries a full score: what it costs over 10 years and how it is paid for, its demand effect year by year, its long-run supply effects, its effects on prices in the sectors it touches, and a phase-in. |
| What the player sees | **A score with a range** before every vote, wider where the evidence is weak. The actual effect is drawn within the range. The briefing book shows which laws moved which numbers. |
| When | This is milestone 4, before Congress, so the laws Congress passes already have their real effects. |

The economy should feel like one of the game's deep systems. A law should do roughly what the evidence says it would, through the channels economists would name, and its effect should depend on when it passes.

## What there is today

**This game's economy** (`EconomyEngine`) is a one-equation-per-variable model, stepped weekly:
- an output gap that closes by itself and responds to real rates and shocks;
- Okun's law;
- a Phillips curve with anchored expectations;
- a Fed that follows a Taylor rule at its meetings;
- oil and gas, the 10-year yield, consumer confidence, and debt dynamics.

**Its bills** (20 of them, `bills.csv`) each carry four numbers: a growth jolt and an inflation jolt that fade over a year, a permanent change in the deficit, and an approval effect. A law cannot change potential growth, unemployment's natural rate, any particular price, or investment. Whether it passes in a slump or a boom makes no difference.

**Resolute Desk Version 2's economy** (`economy/Economy.java`) is richer:
- potential growth and the natural rate of unemployment;
- an output gap moved by real rates, the fiscal stance, tariffs, sentiment and shocks;
- an inertial Phillips curve with credibility;
- a Fed meeting eight times a year;
- a 10-year yield that rises with debt;
- stocks;
- the prices people feel: groceries, rent and insurance;
- industries: housing, manufacturing, AI investment, farms, energy and tourism;
- figures by state;
- monthly and quarterly releases;
- occasional recessions, crashes, oil spikes and booms.

Its weak spots for our purpose:
- **Fiscal policy** enters as one undifferentiated number: a dollar of corporate tax cut and a dollar of unemployment insurance do the same.
- **Policy effects** on growth, unemployment and inflation are added directly, not produced by the economy.
- **No capital stock**, so deficits cannot crowd out investment.
- **Headline inflation** is not built from its parts, so a housing law cannot lower inflation through rents.

**Resolute Desk's policy table** (`data/rd/policies.tsv`) has 146 policies in 12 areas. Each policy has:
- 2 to 5 levels, and the level in force each year;
- the channels that can move it: a bill, an executive order, a rule or the budget;
- a cost per step;
- effects on 12 outcomes;
- effects on voter groups;
- a lag;
- an evidence rating (strong, moderate, weak or contested);
- a historical precedent.

We keep all of that, and give each step a full economic score.

## The model

Everything below steps once a week, using the game's named random streams, so it replays exactly and the browser and Java builds agree. Rates are annual unless they say otherwise. "Gap" is the output gap in percent of potential.

The structure and the published figures (multiplier ranges, pass-throughs, Okun's law) are fixed. The other coefficients below are starting values, to be tuned until the model meets the targets in "Numbers the model must reproduce".

### 1. Supply: what the economy can produce

Potential output is built from **labour**, **capital** and **productivity**:

```
Y*  = A · K^0.33 · L^0.67
L   = working-age population · participation · (1 - u*) · average hours
K'  = K · (1 - depreciation) + investment            (weekly, depreciation about 5% a year)
A'  = A · (1 + TFP growth / 52)                      (about 1.0% a year, plus policy)
```

- **Working-age population** grows with natural increase and net immigration. Immigration policy changes the second.
- **Participation** drifts down with ageing, about 0.2 points a year. Child care, paid leave and work requirements can move it.
- **The natural rate of unemployment (u\*)** starts at 4.3%. Unemployment insurance, the minimum wage, job training and occupational licensing can move it.
- **TFP growth** has a baseline. Research funding, infrastructure, education (with long lags), AI investment and regulation add to it or take away from it.
- **Investment** is a share of output that falls as the cost of capital rises:

```
investment share = base share - 0.8 · (real 10-year rate - 1.5) - user-cost effect of capital taxes + sentiment term
```

This is how deficits cost growth in the long run. More borrowing raises the 10-year yield, which lowers investment, which slows the capital stock. It is also how a corporate tax cut raises growth in the long run, partly offset by the borrowing it adds.

**Calibration:** potential growth near 1.8% a year in 2029, the figure the Congressional Budget Office (CBO) projects for the late 2020s.

### 2. Demand: the business cycle

The output gap moves toward a target that combines interest rates, fiscal policy and everything else that shifts demand:

```
gap target = - 0.6 · (real short rate, lagged a quarter - neutral real rate)
             - 0.4 · (mortgage rate - its normal level)        housing and durables, the most rate-sensitive
             + Σ fiscal impulse · multiplier                   this year's change in each policy's spending or taxes
             + net exports term                                tariffs, the dollar, retaliation
             + wealth term                                     stock and house prices
             + sentiment term + shocks
gap' = gap + (gap target - gap) · 0.03 + noise                 (closing about a third of the way each quarter)
```

**The fiscal impulse** counts only the change: a new program pushes demand while it ramps up, then stops pushing once it is steady.

**Multipliers depend on what is financed and when.** Each policy's money is classed as one of nine kinds, with CBO's published ranges for the effect of a dollar on output:

| Class | Range (CBO) | Central (our choice) |
|---|---|---|
| Federal purchases of goods and services | 0.5 – 2.5 | 1.3 |
| Grants to states for infrastructure | 0.4 – 2.2 | 1.2 |
| Other grants to states | 0.4 – 1.8 | 1.0 |
| Transfers to people (unemployment insurance, food aid) | 0.4 – 2.1 | 1.2 |
| One-time payments to retirees | 0.2 – 1.0 | 0.5 |
| Tax cuts for lower and middle incomes | 0.3 – 1.5 | 0.8 |
| Tax cuts for higher incomes | 0.1 – 0.6 | 0.3 |
| Corporate tax changes | 0.0 – 0.4 | 0.2 |
| Tariffs | Not a multiplier | They work through prices and trade instead |

**The state of the economy** moves the multiplier within its range:
- **In a slump** (gap below zero), it rises by up to half of the way to the top of the range.
- **Near the Fed's floor**, it rises further, because the Fed cannot offset the stimulus.
- **Above capacity**, it falls toward the bottom of the range.

**The Fed then reacts as the model's Fed does.** So a stimulus in a boom also raises rates and crowds out investment. That second effect is not built into the multiplier; it happens in the model.

**Tax increases and spending cuts** use the same classes, with the opposite sign.

### 3. Jobs and wages

```
u'      = u + (u* - 0.5 · gap - u) · 0.035 + structural unemployment (AI disruption) + noise     (Okun's law, about 2 to 1)
payrolls = labour force growth + change in employment              (the monthly jobs report: +110,000 a month in steady state)
wages   = expected inflation + productivity growth + 0.4 · (u* - u)
```

### 4. Prices, built from their parts

Headline inflation is the weighted sum of six parts, as the consumer price index is:

| Part | Weight | What moves it |
|---|---|---|
| Shelter (rent, owners' rent) | 35% | Housing supply against households, with a lag of about a year, and mortgage rates |
| Food | 13% | Farm costs, tariffs, energy |
| Energy | 7% | Gas and utilities: oil, energy policy |
| Medical care | 8% | Insurance and drug prices: health policy |
| Goods | 20% | Import prices (tariffs, the dollar), manufacturing capacity |
| Other services | 17% | Wages |

Each part follows:

```
part inflation = expected inflation + 0.15 · gap (lagged two quarters) + its own driver + policy price effects + noise
```

- **Expectations** are anchored by the Fed's credibility, as today.
- **Gas** follows oil: $1 a barrel is about 2.4 cents a gallon.
- **Tariffs** pass through to import prices almost fully, as the 2018–19 tariffs did. About a third of that reaches goods prices within a year.

So a zoning reform lowers rent inflation over several years. A drug-price law lowers medical inflation. A tariff raises goods inflation at once and food inflation through retaliation.

### 5. The Fed and the markets

**The Fed** meets on its real calendar and moves 25 or 50 basis points toward a Taylor rule on the reported data:

```
Taylor rate = neutral real rate (0.8) + inflation + 0.5 · (inflation - 2) + 0.5 · gap
```

- Its credibility holds expectations: as today, leaning on the Fed costs some.
- It cuts faster in a financial crisis.
- It cannot go below 0.125%.

**The 10-year yield** is the expected average of the Fed's path plus a term premium. The premium rises with debt: about **2.5 basis points for each point of debt to GDP** above 100%, the range in the research CBO relies on. It also rises with large unfunded deficits and with lost credibility. Mortgages are the 10-year plus 1.7 points.

**Stocks** follow Resolute Desk's weekly log return, which:
- rises with growth above potential;
- falls when the 10-year rises;
- falls on financial shocks and policy uncertainty;
- adds noise of about 19% a year.

**Oil** follows its base, crises and the reserve, as today. Energy policy moves the base (drilling and leasing up; demand-side policies like EV credits down).

**The dollar** strengthens with the gap between our rates and abroad and with tariffs, partly offsetting their price effect.

### 6. The sectors, on top

Resolute Desk's indicators, each driven by the model instead of standing alone:

| Sector | Driven by |
|---|---|
| Housing starts (millions a year) and home prices | Mortgage rates, households forming, and the zoning and permitting policies that change supply |
| Groceries | Food inflation |
| Rent | Shelter inflation |
| Insurance | Medical inflation |
| Manufacturing (an index where 50 is no change) | The gap, tariffs, the dollar, industrial policy (chips, clean energy) |
| AI investment | Its trend, AI regulation, the cost of capital; it adds to productivity and to structural unemployment |
| Farm income | Retaliatory tariffs, farm support, energy costs |
| Energy production | Oil, leasing and drilling policy, clean-energy credits |
| Tourism | The gap, the dollar, immigration and visa policy |
| Consumer sentiment | Resolute Desk's formula; it feeds demand |

**By state:**
- **Unemployment** is the nation's, adjusted by each state's mix. Manufacturing, energy and farm states feel those sectors.
- **Gas** is the nation's price plus each state's own difference (its taxes and supply).

Both feed approval in the states, as Resolute Desk's do.

### 7. The budget

Revenue and outlays are kept separately, in % of GDP, starting from CBO's projections for 2029: revenue about 17.8% and outlays about 24.3%, a deficit near 6.5%.
- **Mandatory spending** grows with an ageing population (Social Security, Medicare), so a president who does nothing sees deficits creep up.
- **Interest** is the average rate on the debt times the debt. The average rate moves toward the 10-year as the debt rolls over, about a sixth of the way a year.
- **The cycle:** automatic stabilisers add 0.35 points of deficit for each point of negative gap.
- **Each policy in force** adds its cost or saving by year, and laws with pay-fors carry their offsets.
- **Debt** follows `debt' = debt + deficit - debt · nominal growth`.

Debt limits, appropriations and shutdowns are Congress's: milestone 5.

### 8. Shocks

As in Resolute Desk:
- a recession about once in six years;
- a financial crisis about once in twenty;
- oil spikes;
- an occasional boom;
- each followed by a quiet period.

The game's own events (a hurricane, a crisis abroad) keep their economic effects, now aimed at the channel they belong to: demand, oil, or a particular price.

## The policy table and the scores

### What a score holds

Each step of each policy (one level to the next) gets a score. The data are new tables beside Resolute Desk's export:
- `data/policy_scores.tsv`, one row per policy;
- `data/bill_policies.tsv`, which says which policy steps each of this game's bills makes.

| Field | Meaning |
|---|---|
| `cost` | $ billions a year at full effect, today's dollars (Resolute Desk's figure, checked) |
| `class` | Which of the multiplier classes above the money is (or `none`) |
| `phase` | The share of the full effect in years 1 to 5 (for example `0.3 0.7 1 1 1`) |
| `payfor` | The default offset a bill carries, if any (another policy step, or a named revenue) |
| `labour` | The long-run change in the labour force, in % |
| `participation` | The change in participation, in points |
| `nairu` | The change in the natural rate of unemployment, in points |
| `tfp` | The change in productivity growth, in points a year (or in its level, for one-off reforms) |
| `capital` | The change in the user cost of capital, in % (for tax changes on investment) |
| `prices` | The effect on each price part, in % of its level by year 5 (for example `shelter=-1.5 medical=-0.8`) |
| `oil` | The change in oil's base price, $ a barrel |
| `sectors` | Direct effects on a sector index (for example `housing=+0.15 manufacturing=+1`) |
| `outcomes` | Resolute Desk's other outcomes, kept: uninsured, poverty, crime, overdoses, border crossings, emissions, test scores |
| `lag` | Weeks before the effect starts (Resolute Desk's) |
| `evidence` | S, M, W or C (Resolute Desk's): sets the range |
| `basis` | One line on where the numbers come from |

### The ranges, and the draw

Evidence sets how wide the range is around each central effect:

| Evidence | Range | Meaning |
|---|---|---|
| S, strong | ±25% | |
| M, moderate | ±50% | |
| W, weak | ±80% | |
| C, contested | ±100% | It may do nothing, or twice what is claimed |

These are Resolute Desk's own spreads.

**When a step becomes law**, one number is drawn for it from the game's random stream, between −1 and +1, clustered near 0. Every channel of that step is scaled by `1 + spread · draw`. So a contested law can turn out to have done nothing, and a strong one lands close to its score. The draw is part of the save and is never shown. The player learns it only by watching what the law does.

**Before a vote**, the score shows:
- the central effect and the range for growth, jobs and inflation over 1, 3 and 10 years;
- the deficit;
- the prices and sectors it touches;
- the other outcomes.

These are computed by running the model forward from today, with the law and without it, on a copy, with noise off. So a score reflects today's economy: the same stimulus scores higher in a slump.

### Credit for what laws did

Each law's direct contributions are kept as it plays out:
- its fiscal impulse times the multiplier it got;
- its supply shifts;
- its price effects.

The briefing book then shows what each law has done so far: its effect on growth, on unemployment, on the deficit and on the prices it touches, from its own channels. The Fed's response to everything at once is shown as one line of its own, "the Fed's response", because it cannot be split law by law.

### Laws, orders, rules and budgets

Resolute Desk moves policies through four channels, with strengths of 1, 0.6, 0.85 and 1:
- a bill moves a policy up to two steps;
- an executive order, a rule or the budget moves it one step.

**In this milestone:**
- **Bills** move the table: the hundred days' flagship, and the governing pushes that exist today.
- **This game's 20 bills** are re-expressed as the policy steps they make (`data/bill_policies.tsv`). For example, the Build More Homes Act becomes the housing supply policies, one step up.
- **The Cabinet Room** shows the table, each policy's level and its score for every possible step. It can already be used to look up what a bill would do.
- **Orders and rules** arrive with the West Wing (milestone 6), and **the budget** with Congress (milestone 5).

**Public reaction** when a policy moves is Resolute Desk's:

```
approval points = (support - 50) · (0.2 for a signature policy, 0.06 otherwise) · steps · channel strength      (capped at ±5)
```

It is applied through the record, plus each voter group's own reaction. It replaces the `public_appeal` column of today's bills.

## Numbers the model must reproduce

Each becomes a test (noise and shocks off unless the test is about them).

| Situation | Target | Source of the target |
|---|---|---|
| No new policy, 2029–2033 | Growth averaging 1.7–2.0%, unemployment 4.2–4.7%, inflation settling at 2.2–2.6%, the 10-year at 4.0–4.6%, the deficit 6–7.5% of GDP and rising, debt rising from about 100% to 106–112% | CBO's projections for the period |
| $300 billion of federal purchases over one year, in a slump with the Fed near its floor | Output up 1.0–1.6% at the peak; little crowding out | CBO's multiplier ranges; the 2009 Recovery Act estimates |
| The same at full employment | Output up 0.3–0.6% at the peak, fading within two years as the Fed raises rates; the 10-year up | The Fed's response; the crowding-out research |
| A permanent deficit increase of 1% of GDP | The 10-year 0.2–0.3 points higher after a decade; investment lower; output 0.2–0.5% lower in the long run | CBO's long-term budget analysis |
| Corporate rate from 21% to 28% | Revenue up about $100 billion a year; output 0.2–0.6% lower in the long run through investment | Joint Committee on Taxation and CBO scoring of rate changes |
| Average tariff up 10 points | Consumer prices 0.8–1.5% higher within a year; output 0.3–0.6% lower; farm income down with retaliation | Studies of the 2018–19 tariffs |
| Oil up $10 a barrel | Gas up about 24 cents; inflation up 0.2–0.3 points | The pass-through |
| Mortgage rates up 1 point | Housing starts down 8–12% within a year | The housing literature |
| Net immigration up 1 million a year | The labour force about 0.6% higher each year; output higher by about the same over time; small wage effects | CBO's analyses of immigration |
| A $15 federal minimum wage | Employment 0–3.7 million lower (central about 1.3 million); about a million people out of poverty | CBO's 2019 study and its 2021 update |
| Zoning and permitting reform | Housing starts up 5–15% over five years; rent inflation down 0.5–1.5 points a year by years 3 to 5 | Moderate evidence: the range is wide |
| Recessions | About one in six years; unemployment up 2–4 points; the Fed cutting by 2–4 points | The postwar record; Resolute Desk's shock rates |

## Step 1 as built: the core model (13 November 2026)

The model above is in `mandate-core/.../economy/` (`EconomyEngine`, `EconomyState`, `BudgetState`, `Program`, `FiscalClass`, `Part`), with every coefficient in `tuning.properties` under `economy.*`, `budget.*`, `markets.*` and `sectors.*`. The engine's interface is unchanged, so the hundred days, the governing week and the campaign run on it as before. The calibration tests are `EconomyModelTest`: each target is a with-and-without run on the quiet economy (no noise, no random shocks), with the Fed meeting eight times a year on monthly data.

**Where the build departs from the note, and why:**

| Change | Why |
|---|---|
| **The Fed's rule puts 1.0 on the gap**, not 0.5 (the "balanced approach" rule in the Fed's own Monetary Policy Report), and the neutral real rate is 1.0, not 0.8 | With 0.5 the Fed raised rates a quarter point against a stimulus at full employment, and output rose 0.7%, above the target. A neutral rate of 1.0 puts the long-run Fed rate at 3.0%, near CBO's projection |
| **The Fed's gap is half the jobs report and half its staff's estimate of output against potential** | Unemployment lags the gap by about half a year, so a Fed reading only the jobs report reacted far too late |
| **The Fed looks through three-quarters of what tariffs add to prices**, as it said it would in 2025 | Otherwise it raised rates into a one-time price jump, and a 10-point tariff cost 1.6% of output, three times the target |
| **The 10-year has a fast "news" part** (bills under debate, new borrowing not yet in the debt, the Treasury secretary, the Fed's credibility), moving 40% of the way each week, beside the slow fundamental part (6% a week) | Markets price fiscal news within days. New borrowing is priced at 12 basis points per point of GDP when it is announced, fading over ten years as the debt itself carries it, so a permanent deficit still lands at CBO's 0.2–0.3 points after a decade |
| **Each price part's trend adjusts 6% a week**, not 3% | Headline inflation is measured over the year from the parts' price levels, which adds the year-long averaging the 3% was standing in for |
| **The game's own shocks** (events, wars, a bill's growth impulse) keep their push to the gap over the months they name, and six-tenths of it lingers in demand afterwards, fading as Resolute Desk's shocks do | Without it, demand snapped back within months, and a severe shock barely moved unemployment |
| **A bare deficit from a bill or an event only adds to the debt** (fiscal class "none") until step 4 | This game's bills already carry their demand as a growth impulse; counting the deficit as demand too would count it twice. Step 4 replaces both with the policy steps each bill makes |
| **Recessions** push demand down by 6 to 10 points of the gap's target, and the gap by another 4 to 6 over twenty weeks | Resolute Desk's recessions raise unemployment by about one point; the note's target is two to four |

**What the tests measure** (targets from "Numbers the model must reproduce"):

| Situation | Target | Measured |
|---|---|---|
| No new policy, 2029–2033 | Growth 1.7–2.0% | 1.89% on average; potential growth 1.85% |
| | Unemployment 4.2–4.7% | 4.27–4.42% |
| | Inflation settling at 2.2–2.6% | 2.44% |
| | The 10-year 4.0–4.6% | 4.22% |
| | Deficit 6–7.5% and rising | 6.5% rising to 7.25% |
| | Debt 106–112% | 111% |
| $300 billion of purchases in a slump, the Fed at its floor | Output up 1.0–1.6% at the peak | 1.23% |
| The same at full employment | Output up 0.3–0.6% at the peak, fading within two years; the 10-year up | 0.56%; 0.29% below the baseline two years on; the 10-year up 0.26 |
| A permanent deficit of 1% of GDP | The 10-year 0.2–0.3 points higher after a decade; output 0.2–0.5% lower in the long run | 0.27; potential output 0.25% lower after fifteen years, capital 0.48% lower |
| Corporate rate from 21% to 28% (the user cost of capital 6% higher) | Output 0.2–0.6% lower in the long run | 0.37% lower after fifteen years |
| Average tariff up 10 points | Prices 0.8–1.5% higher within a year; output 0.3–0.6% lower; farm income down | 1.0%; 0.56% at the lowest; farm income index down 7.7 |
| Oil up $10 a barrel | Gas up about 24 cents; inflation up 0.2–0.3 points | 24.8 cents; 0.26 points |
| Mortgage rates up 1 point | Housing starts down 8–12% within a year | 9.7% |
| Net immigration up 1 million a year | The labour force about 0.6% higher each year; output higher over time | 0.6%; potential output 2.1% higher after five years |
| A recession | Unemployment up 2–4 points; the Fed cutting 2–4 points | 2.3 points; 3.0 points |
| How often | About one in six years | One in 5.3 years with Resolute Desk's odds; one in 6.1 over 14 full autopilot terms, counting the calm after each |

The minimum wage and zoning targets need the policy table (step 3) and are tested there.

**Over full terms** (14 autopilot terms): terms without a recession end with unemployment at 4.2–4.7% and approval at 45–51, as in milestone 3. Terms with one end at 33–42 and are lost. The autopilot president was re-elected in 4 of 14 (3 of 8 in milestone 3). It never answers a slump with stimulus; with the policy table a player can.

**Saves:** a save from before the model loads with its gap, jobs, inflation, Fed rate, oil and 10-year, and the economy is rebuilt around them; an old policy deficit becomes a program the economy has already adjusted to. The browser and Java builds agree on every seed and on six full terms.

## Step 2 as built: the states and the releases (13 November 2026)

- **The states** (`StateEconomy`, data `data/rd/state_economy.tsv`, Resolute Desk's tables): each state's unemployment is the nation's, scaled by its usual difference and moved by the industries it leans on (manufacturing, farming, energy, tech, tourism); its gas price is the nation's plus its own difference. Both now feed approval in each state as Resolute Desk's do: a point of unemployment above the nation's costs 0.07 in the state's logit, and a dollar of dearer gas 0.10. This was missing from milestone 3's port, which had no state figures.
- **The releases**, on their real days:
  - the jobs report on the first Friday, with the jobs added (labour-force growth less the change in unemployment; about 55,000 a month in steady state at CBO's immigration);
  - consumer prices on the second Wednesday, with food, shelter and medical care;
  - **GDP**, new: the first estimate on the last Thursday of January, April, July and October, at an annual rate;
  - the Fed's decisions, as a quarter-point range.
- **The news** (`Releases`, Resolute Desk's headlines and weights): routine releases run in the paper's Economy section. A shock, a Fed move, job losses, a shrinking economy or inflation over 4% are Washington news and can lead the paper. Resolute Desk's era stories that depend on the stock market (its 13-week change) now run too.
- **The record:** each week's reading now keeps the economy's detail (core inflation and the six price parts, the sectors, sentiment, stocks, the dollar, oil, mortgages, payrolls, the gap, potential growth, revenue, outlays, interest and debt) for the briefing book's charts. A full term's save is about 560 KB.

## What changes elsewhere

- **The economy engine is rewritten.** Its interface stays: the weekly step, the Fed's meetings, the jobs and inflation reports, gas and the deficit. So the hundred days, the governing week and the campaign keep working.
- **Approval's economy term** reads the same figures as before (growth, unemployment, inflation, gas, sentiment), now produced by the new model. Shelter, food and energy inflation can enter it the way Resolute Desk's issue approvals read groceries and rent.
- **Elections** read growth, inflation and unemployment as before.
- **The game's events** that shock the economy are re-aimed at the right channel.
- **Saves:** the economy's state grows; old saves are upgraded on load, as in milestone 3.
- **Golden fingerprints** change on purpose; parity is checked again on full terms.

## On screen

- **Before a vote** (and in the Cabinet Room), every bill shows its score:
  - the central effect and range on growth, jobs and inflation at 1, 3 and 10 years;
  - the 10-year cost, how it is paid for, and the deficit and debt;
  - the prices and sectors it touches, and the other outcomes;
  - the evidence rating, in words.
- **The briefing book adds:**
  - the sectors (housing, groceries, rent, insurance, manufacturing, AI, farms, energy, tourism) with their trends;
  - inflation by part;
  - the budget (revenue, outlays, interest);
  - **What your laws have done:** each law's effect so far against its score, with the Fed's response as its own line.
- **The Cabinet Room:**
  - the policy table by area, each policy's level and its history;
  - the score of every possible step;
  - the channels that can move it, and why the ones that cannot are unavailable;
  - the evidence and the precedent.
- **The Capital Ledger** reports the scores when bills move ("Budget office: the housing bill would cost $40 billion over ten years and lower rents..."), and later what laws did.

## How it will be built

1. **The core model:** supply, demand, jobs, prices by part, the Fed and markets, the budget and shocks. It replaces today's engine, with the calibration tests above passing.
2. **The sectors and the states,** with the releases and the news they make.
3. **The policy table and the scores:**
   - Resolute Desk's 146 policies are loaded.
   - Each economically relevant policy is scored (about 80 of them; the rest are civil rights, crime and the like, and keep only their non-economic outcomes).
   - Each score gets a one-line basis and Resolute Desk's evidence rating.
   - Ranges, draws and credit are built.
4. **Laws onto the table:** this game's bills, the hundred days' flagship and the governing pushes. Public reaction follows Resolute Desk's rule.
5. **The screens:** the score before votes, the briefing book's sectors and laws, and the Cabinet Room.
6. **Checks:** the calibration tests, balance over full terms, parity, golden fingerprints, the browser audits, the docs and the preview.

## Questions for you

1. **Orders and rules before the West Wing exists.** Should the Cabinet Room already let the president move a policy by executive order or rule in this milestone, with Resolute Desk's legal risk? Or only show the table, with orders and rules arriving with the West Wing as planned (milestone 6)? *My recommendation: show only, as planned.*
2. **The baseline.** Should a president who does nothing face CBO's real projection (deficits creeping up, debt passing 110% of GDP by 2033), even though it makes the game harder? *My recommendation: yes, it is the real constraint a 2029 president faces.*
3. **How exact the scores look.** Should the score before a vote show the range as numbers ("growth +0.1 to +0.4 points in year 3"), or in words with the numbers on request ("a small boost to growth, likely", with a "the numbers" disclosure)? *My recommendation: numbers, with a one-line plain reading above them.*
