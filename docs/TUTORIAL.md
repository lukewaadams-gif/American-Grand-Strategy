# The tutorial

**Tutorial** on the title screen (and on the site's front page) starts a guided game. It takes about fifteen to twenty minutes and covers the 2028 campaign and the first year in office. It is played on the real game, not a mock-up: "Play from here" leaves the coach and keeps the game as it stands.

## How it plays

A **coach** panel narrates, docked at the bottom left; it moves to the right when what it asks you to click is behind it, and it becomes a bottom sheet on a phone. Each step is one of:

- **Narration,** with **Next**.
- **A move to make.** The coach rings the place on screen and waits until the game shows the move made. "Do it for me" makes it instead, so the tutorial never stalls.
- **Autoplay.** Routine stretches play themselves: the campaign to Election Day, the rest of the hundred days, two months of governing. The staff's recommendation answers each question. The coach's speed buttons (slow, normal, fast) and **Pause** control it.
- **Cause and effect.** After a move, a card shows what it moved. It uses the engine's own reasons: each week's poll lists what moved the race, and the paper explains approval.
- **Before and after.** Twice, the same moment plays two ways:
  - next week with a contrast message and with an attack, from the same save (the game is put back as it was);
  - a bill with and without it, by the budget office's score.
- **A recap card** at the end of each chapter.

**The tools:**
- **Chapters** jumps to any of the eight, or replays one.
- **Map** opens the systems map. It shows the game's parts (the campaign's message, money, field, team and polls; election night; approval, the economy, Congress, the West Wing and press, the world; the midterms and the re-election) and the arrows between them. The parts met so far are lit.
- **Glossary.**
- **Play from here.**
- **×** goes back to the title screen.

**Progress is remembered** (the chapter, in the browser's storage). The title screen then says where it will pick up. Each chapter starts from a known moment when it is jumped to or resumed. Otherwise it goes on in the game as it stands.

## The chapters

| # | Chapter | Minutes | Starts from | The player's moves | Connections shown |
|---|---|---|---|---|---|
| 1 | How the game fits together | 1 | The campaign, week 9 | — | The systems map, every part |
| 2 | The war room | 3 | The campaign, week 9 | Pennsylvania on the map; the candidate sent there; the message chosen; the week run | What the week moved, in the engine's words |
| 3 | Polls, voters and your team | 3 | (continues) | An internal poll; a state's voters; a surrogate's tour | Contrast against attack, the same week twice |
| 4 | To Election Day | 2 | The final stretch | — (the staff plays it) | Election night. A defeat continues from a presidency that won |
| 5 | The transition | 2 | The day after the election | The agenda, then the oath | — |
| 6 | The first hundred days | 3 | The first week in office | The week ended | One week: approval, unemployment, inflation |
| 7 | Congress and the economy | 3 | Governing, September 2029 | The Roosevelt Room opened | A bill with and without it; two months later |
| 8 | The world, and the years ahead | 2 | (continues) | The Situation Room opened | The rest of the term, on the map |

## Code

- `mandate-web/src/main/ts/tutorial/tutorial.ts`: the chapters, the runner and the coach.
- `mandate-web/src/main/ts/tutorial/map.ts`: the systems map.
- `start.ts`: the title screen's Tutorial.
- `play-main.ts` and `front-main.ts`: starting it on the game's page, or handing it over from the site's front page.

## Tests

`tutorial.js` (browser):
- the title's Tutorial;
- the first moves by hand, and the cause-and-effect card after the week;
- every chapter to the last card with "Do it for me" at fast speed, the two comparisons included;
- jumping to a chapter;
- progress remembered across a reload;
- "Play from here" leaving a live game;
- the coach as a bottom sheet at 390 pixels;
- axe-core WCAG 2.1 AA and text size on the coach.
