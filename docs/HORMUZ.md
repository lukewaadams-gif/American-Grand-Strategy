# The Strait of Hormuz: the crisis design

A new in-depth crisis (`docs/CRISES.md`): a fight over the strait that a fifth of the world's oil and much of its liquefied gas pass through. It runs in daily turns on a flat map of the strait and the Gulf. It starts from the Situation Room's world, and its costs run through the whole presidency. This document is the design the implementation follows. Choices not yet discussed are made in favour of realism and listed in `DECISIONS_TO_REVIEW.md`.

## What is at stake (public figures, rounded)

| | |
|---|---|
| Oil through the strait | About 20 million barrels a day, a fifth of world consumption. Most goes to Asia (China, India, Japan, South Korea). |
| Liquefied gas | About a fifth of world LNG trade (Qatar and the UAE). |
| Pipelines around it | Saudi Arabia's East-West line to Yanbu (about 5 million barrels a day, up to 7 in an emergency); the UAE's line to Fujairah (about 1.5). In all, about 2.5 to 3.5 million barrels a day of unused capacity can bypass the strait. |
| The reserves | The US Strategic Petroleum Reserve: about 400 million barrels, drawn at most about 4.4 million a day. The IEA's members can coordinate releases from their own stocks. |
| Iran's means | Thousands of naval mines; hundreds of fast attack craft and the Revolutionary Guards' small boats; coastal anti-ship missiles and drones; a few midget submarines; the islands at the strait's mouth (Abu Musa and the Tunbs, Larak, Qeshm). |
| America's | The Fifth Fleet in Bahrain; carriers and destroyers in rotation; mine countermeasures (few left, and slow); air bases in Qatar, the UAE and Saudi Arabia, with their hosts' consent. |

## Time and the map

- **Daily turns** once the crisis begins, with the presidency's weeks paused, as for the Taiwan war. The crisis ends, and the weeks resume, when shipping returns to normal or a settlement holds for two weeks.
- **The map:** the Gulf from Kuwait to the Gulf of Oman, in the game's dark flat style. It shows:
  - the shipping lanes (inbound and outbound, two miles each, with a buffer between);
  - the transits made each day;
  - mined areas (estimated, and as cleared);
  - Iran's coastal sites (missile batteries, boat bases at Bandar Abbas, Qeshm and Jask, drone sites), each with a threat ring;
  - American and allied ships, with the islands, the oil terminals (Ras Tanura, Fujairah, Ras Laffan) and the pipelines.

## The ladder, and the ways in

Iran climbs one rung at a time, or jumps when provoked. The rungs:

| Rung | What happens | Daily oil flow |
|---|---|---|
| 0. Tension | Rhetoric and drills. | normal |
| 1. Harassment | Fast boats swarm tankers; drones overhead. | −3% (owners wait) |
| 2. Seizures | The Guards board and seize tankers. | −10% |
| 3. Mining | Mines laid in or near the lanes, declared or not. | −40% until swept |
| 4. Attacks | Missiles and drones on shipping and Gulf oil sites. | −60% and terminals damaged |
| 5. Closure | Iran declares the strait closed and fires on all traffic. | −90% |

**Ways in** (the world's measures from milestone 8):
- the "Iran is weeks from a bomb" crisis answered with strikes, or the "Strike Iran's nuclear sites" operation;
- maximum sanctions on Iran for many months;
- a seizure that escalates (a chance each week while Iran's program, proxy attacks and Red Sea attacks run high);
- a war of choice against Iran.

Each week the chance is `0.002 × the world's tension around Iran`, more after strikes. The first rung reached depends on how it started: strikes lead to attacks (rung 4); sanctions to seizures (rung 2).

## Iran's side: factions and proxies

- **Two decision-makers.** The government in Tehran weighs survival, the economy and talks. The Revolutionary Guards' navy weighs prestige and deterrence, and acts partly on its own. Each day each has a disposition (escalate, hold, de-escalate), driven by:
  - American strikes (the Guards escalate, the government wavers);
  - the oil price (high prices reward Iran, but its own exports suffer);
  - back channels (the government de-escalates);
  - casualties (both harden);
  - the regime's stability.

  A rung is climbed when the Guards push and the government does not restrain them.
- **Proxies.** The Houthis attack in the Red Sea (the world's `redsea`); militias in Iraq and Syria attack American bases (the world's `proxy`); each can kill Americans.
- **Others with their own aims:**
  - Israel may strike Iran's nuclear sites at the worst moment, or hold back if asked;
  - Saudi Arabia and the UAE want protection but fear Iran's missiles on their oil, and decide whether American planes fly from their bases;
  - Qatar and Oman carry messages.

## The president's tools

| Tool | What it does | Costs and risks |
|---|---|---|
| **Escorts** | Convoys through the lanes with destroyers. Each escort group protects a share of transits from seizures and boats. | Ships within range of the coastal missiles; a hit kills sailors and can sink a ship. |
| **Mine clearing** | Clears a share of a mined area a day, faster with allies (Britain, France, Japan). | Slow: a mined lane takes weeks. Minesweepers are vulnerable. |
| **Strikes on the coast** | Anti-ship missile sites, boat bases, drone sites and mine layers, each a target set. They reduce Iran's capacity for good. | Iran climbs a rung (the Guards push). Gulf hosts may refuse their bases. Casualties if pilots are lost. |
| **Diplomacy** | Envoys through Oman or Qatar to Tehran's government; pressing China and India (the strait's biggest customers) to lean on Iran; the Gulf states; Europe; Japan. | Takes days. Can fail, and the Guards may defy a deal. |
| **Oil** | The SPR (up to 4.4 million barrels a day), a coordinated IEA release, opening the bypass pipelines (Saudi and UAE consent), and war-risk insurance (federal reinsurance for tankers). | Draws down the reserve; insurance costs money. |
| **Invasion** | A campaign in phases (below), or a limited option: seizing the islands in the strait, or a coastal strip. | Large, long and bloody. |

## The invasion, in phases

1. **Build-up** (two to four months): basing talks with the Gulf states and Iraq; carriers, divisions and air wings move. Iran sees it coming. Congress votes on authorization (the War Powers clock).
2. **The invasion,** at theatre level:
   - the axes of advance (from the Gulf coast, from Iraq);
   - the air campaign against air defence, missiles and command;
   - casualties and munitions;
   - Iran's missiles on the Gulf states and Israel;
   - the proxies everywhere.
3. **Occupation and insurgency:** years of costs at home in lives, money and approval, on the world's war-of-choice model (milestone 8's occupation).

**Limited options:**
- **Seizing the islands** (Abu Musa, the Tunbs, Larak): weeks of fighting, and Iran loses the strait's narrows;
- **A coastal strip** (Bandar Abbas to Jask): months, a large force, and the coastal threat ends.

## The economy

The strait's flow sets the oil market each day. The physical shortfall is the flow lost, less the bypass pipelines and the reserves released.
- **The price.** The shortfall moves the price through an inelastic market: about 5% for each million barrels a day missing, and more past ten. Fear of worse adds a risk premium.
- **War-risk insurance and shipping costs** add to the delivered price. Federal reinsurance caps them.
- **Where it goes:** gas prices, inflation, growth and the Fed in the structural economy (milestone 4) through the oil premium; the allies' economies (Japan and South Korea depend most on Gulf oil); and approval.
- **Liquefied gas** shortages hit Europe and Asia (allies' relations) more than the United States.

## How it ends

- **Shipping restored:** the lanes cleared and no attacks for two weeks. A win, with the price falling back over months.
- **A settlement:** talks give Iran something (sanctions relief, a nuclear deal, a pause). The ladder falls, and the Guards may defy it.
- **Stalemate:** shipping limps on, escorted, with a high price.
- **Escalation into war:** strikes and attacks spiral into the invasion, or Israel strikes.

The verdict counts it as a crisis (crisis leadership) and, if it becomes a war, as a war.

## Standalone

As planned: from the start screen, with presets (base case, optimistic, pessimistic, custom) and a year (2029–2036), the crisis beginning weeks before the first seizure, with the American forces and Iran's arsenal of that year.

As built (13 November 2026, docs/MODES.md): **Individual Crisis** on the start screen begins the crisis at once in the sample presidency of September 2029, with the base case, optimistic or pessimistic preset (`Command.IndividualCrisis`). The game ends when the crisis does, with an after-action report. The custom preset and the choice of year are not built.

## As built (13 November 2026)

The engine is `mandate-core/.../hormuz` (`HormuzState`, `HormuzEngine`, `HormuzOrder`), driven by `GameEngine` and drawn by `mandate-web`'s `HormuzViews` and `presidency/hormuz.ts`. Tests: `HormuzTest`.

**How it starts.** `ForeignEngine.hormuzChance` runs each governing week: `0.0004 × (Iran's tension − 40) × (1 + heat/20) + 0.004 × heat/30`, at most 25%. Tension is the mean of the world's `iran`, `proxy` and `redsea` measures. The heat rises with the provocations: strikes on Iran's program (+30), a proxy answered with force (+20), the "Strike Iran's nuclear sites" operation (+30), a war on Iran (+60), and each week of maximum sanctions (+0.25). It decays by 3% a week. Strikes open the crisis on rung 4, sanctions on rung 2, a seizure on rung 1 or 2. In autoplayed full games, about a quarter of presidencies face one.

**Each day:**
1. **Iran's two decision-makers** move toward a target: 15% of the way for the Guards, 12% for Tehran, with noise. On a scale of −1 (de-escalate) to +1 (escalate):
   - the Guards' target = appetite (0.30, 0.45 or 0.60 by preset) + anger (+0.25 a strike, +0.5 an Israeli strike, +0.08 a firm message, fading 7% a day) + the price Iran extracts (premium/400, at most 0.15) − half the talks − a quarter of Beijing's pressure − 0.6 × what Iran has lost at the strait − 0.7 once a deal is struck;
   - Tehran's target = appetite − 0.35 + 0.4 × anger − 0.9 × talks − 0.5 × pressure − the regime's instability − 0.15 while its own exports are cut off − 0.8 once a deal is struck.
2. **The ladder:**
   - up a rung (a 20% chance) when the Guards push past 0.2 + 0.1 × rung and Tehran does not hold them back (Tehran below −0.25), unless the Guards defy it (above 0.85);
   - down a rung (20%, or 40% under a deal) when the Guards cool, when Tehran reins them in, or when the strait's arsenal falls below 30%.
3. **The rung's actions:**
   - seizures from rung 2, fewer under escort;
   - mines laid from rung 3, five a day at full strength;
   - missile and drone salvos from rung 4, at escorts, tankers or Gulf terminals. A destroyer is hit by one salvo in twenty aimed at it. Attacks on terminals can close the Gulf hosts' bases.
4. **Minesweeping:** two mines a day per group, known fields first.
5. **The proxies:** militias in Iraq, and the Houthis in the Red Sea, act from rung 3.
6. **Liquefied gas:** a weekly cut in relations with Japan, Korea, India and NATO while less than 60% of the oil gets through.
7. **The oil:**
   - The ladder keeps oil away: 0, 3, 10, 25, 60 and 90% by rung, less under escort.
   - Mines keep more away: the total over 120, at most 85%.
   - The premium is $3.75 a barrel for each million barrels a day missing (after the pipelines, the reserve and the IEA), $6 past ten million, plus $2.50 a rung and war-risk insurance (cut by two-thirds under federal reinsurance).
   - The premium goes into the structural economy's oil price every seventh day, when the economy, the Fed and opinion take their week.

**How it ends:**
- a deal (talks reach 100, then a week of calm);
- shipping restored (two weeks with the ladder at harassment or below and the lanes nearly clear);
- regime change (the full invasion takes Tehran; the occupation goes on as a war of choice in the Situation Room);
- or a stalemate on day 150, unless troops are on the move.

The verdict counts it as a crisis faced. A deal eases sanctions on Iran.

**Probes** (each with several seeds; in days, Americans killed and the peak price of oil, which starts at about $70):

| Strategy | Result |
|---|---|
| Nothing | A stalemate at 150 days with the strait closed; oil at $180–250; approval down about 17 points |
| Escorts, sweepers, the reserve, pipelines and reinsurance | A stalemate; oil at $130–170; about 100 Americans killed |
| The same and the back channel | A deal in 31–40 days; oil barely moves |
| Strikes on the coast, then escorts and sweeping | Shipping restored in 50–90 days; a few Americans killed, up to 25 |
| Seizing the islands | Restored in about 55 days; 165–210 killed |
| The coast from Bandar Abbas to Jask | Restored in about 105 days; 640–715 killed |
| The full invasion | Regime change after about six months; more than 1,000 killed, then the occupation |
