# American Grand Strategy: the new graphics, the brief

These are the decisions from the 48-question review (November 2026). They drive the browser rewrite. Where a later instruction changes one, edit it here.

## The game and the technology

| # | Decision |
|---|---|
| — | The game is renamed **American Grand Strategy** (was "Mandate"). |
| Q1, Q41 | The browser interface is rewritten in **TypeScript**, compiled to JavaScript at build time. |
| Q42 | The **engine stays Java**, compiled to JavaScript by TeaVM, behind a clean API that the TypeScript interface calls. The simulation's tests and determinism are unchanged. |
| Q2 | **No third-party JavaScript libraries.** Maps, rooms, charts and portraits are hand-drawn in SVG and Canvas. Public-domain geographic data is converted to drawing data at build time. |
| Q3, Q46 | The **desktop (Swing) interface is retired**, code and tests removed. The terminal game and the designer tools stay. |
| Q5 | **New mechanics are allowed** where the art implies them (the day-slot schedule, moving forces). |
| Q6 | Desktop and laptop browsers, 1280×720 and up. |
| Q7 | No download-size limit. |
| Q13, Q43 | **Ambience and music, synthesised live with the Web Audio API** (no audio files). |
| Q14 | **Cinematic motion**, all of it switched off under "reduce motion". |
| Q15 | The **current accessibility bar holds everywhere**: AA contrast, a 12 px minimum, full keyboard play, the high-contrast and colour-blind palettes, text scaling, and passing axe audits. |

## The look

| # | Decision |
|---|---|
| Q8 | **Shared controls, distinct worlds.** The same buttons, type and accessibility rules everywhere; the campaign, the presidency and the Situation Room each have their own backdrop, palette and art. |
| Q45 | Menus, new game, settings, saves, the timeline and the 100-day report use a **neutral American Grand Strategy frame**. |
| Q9 | Battleground 2028 was too cartoonish in three ways: **caricature portraits, bright saturated colours, and emoji-like icons.** Its chrome was fine. |
| Q10 | The campaign aims for a **broadcast and editorial** look: a network decision desk, a newspaper's election map, real geography, precise data graphics, a muted palette. |
| Q11 | **Restrained vector portraits**: realistic proportions, neutral, not caricatures. |
| Q12 | The source games are the owner's own work, so their drawing code (Resolute Desk's rooms, portraits and maps; Island Chain's map style) **may be ported and restyled**. |

## The campaign (after Battleground 2028)

| # | Decision |
|---|---|
| Q16, Q17 | **Real state shapes**, plus Maine's and Nebraska's congressional districts, are the main map. The tile grid stays as an accessible alternative view. |
| Q18 | Map layers: **polls** (margin, week-to-week change, uncertainty), **spending** (ads and field, both sides), **travel** (visits, routes, the opponent's stops), and **turnout and voter groups**. |
| Q19 | **Hybrid layout:** a war-room dashboard as home, with drill-down screens. |
| Q20, Q48 | **A new day-slot schedule:** morning, afternoon and evening, seven days a week. The staff fills every slot from the standing orders, and the player can override any slot. |
| Q21 | **Full forecast graphics:** trend lines with error bands, state win probabilities, a tipping-point chart. |
| Q22 | News as a **dated wire feed, plus TV chyrons** for breaking events. |
| Q23 | Debates stay a **decision card**, with a richer results panel. |
| Q24 | Election night is a **skippable broadcast sequence**: states called through the night, the race to 270. |

## The transition and the presidency (after Resolute Desk)

| # | Decision |
|---|---|
| Q31 | The transition has **its own look**: a transition office between the campaign and the White House. |
| Q25, Q26 | **Navigation by rooms**: the Oval Office (desk, decisions, briefing, economy), the Cabinet Room, the Roosevelt Room (Congress, whip count), the Situation Room, the Press Briefing Room (approval, news). |
| Q27 | **Illustrated vector rooms** in a muted, realistic palette, ported from Resolute Desk's room art and restyled. |
| Q28 | **An interactive Resolute Desk**: the objects on it (documents to sign, the phone, folders, the briefing book) are the actions. |
| Q29 | **A full newspaper front page every week.** |
| Q30 | **Subtle lighting by time of day and season**, through the windows. |

## The Situation Room (after Island Chain 2027)

| # | Decision |
|---|---|
| Q32 | An **operational map where the player deploys and repositions forces**: a new simulation layer that feeds the crisis model. It is **also wired to the full-term Taiwan war model**. |
| Q33 | **Reachable any time** from the presidency, showing standing deployments. It **opens on its own when a crisis starts**. |
| Q34 | Theatres: **the Strait of Hormuz and the Gulf, and the Taiwan Strait and the Western Pacific.** |
| Q35 | **Real coastlines** from public-domain data (Natural Earth), depth shading and a latitude/longitude grid. |
| Q36 | **Simple icons** for units: ship, aircraft and missile silhouettes. |
| Q44 | **Turn-based by week in a crisis; real time with pause once a war begins.** |
| Q37, Q47 | **No ticker.** A monospace status bar (readiness, escalation) and a mission clock along the top. |

## Delivery

| # | Decision |
|---|---|
| Q4 | Order: **the campaign, then the presidency, then the Situation Room.** |
| Q38 | Work in progress goes to a **preview link** (a `preview/` folder on the Pages site). The public link updates when all three worlds are done. |
| Q39 | **The first screen of each world is approved** from screenshots before the rest of that world is built. |
| Q40 | No outside references: work from the three source games and these answers. |
