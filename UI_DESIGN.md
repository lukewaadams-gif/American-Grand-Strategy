# Interface design

This document covers the desktop interface of the vertical slice: what it borrows from the three source games, the design system, each screen, and how it meets the gameplay requirements. It also states what has been verified and what still needs a person to judge.

**Status, plainly.** The interface is complete for the slice. Every screen the brief asked for exists and is wired to the real game; nothing is a mock-up. Automated checks pass on 51 rendered screens and in nine interaction flows played through the interface's own buttons. A real window under a virtual display passes 17 keyboard, focus and resize checks. I looked at 31 of the 51 renders by eye, covering every screen and every kind of dialog at least once, plus the real-window captures, and fixed what I found (section 14).

That is evidence the interface **works and is mechanically sound**. It is not evidence that it is **polished**. Rendering correctly is not the same as being clear, attractive or pleasant for hours. Nobody has yet played it with a mouse on a real monitor, used it with a screen reader, or tested it with players. Section 15 lists what needs human review.

Run it:

```bash
mvn -B install
```

```bash
java -jar mandate-cli/target/mandate.jar
```

The desktop window opens by default. On a machine with no display the terminal game runs instead, and `text` asks for it explicitly. `screens DIR` renders every screen to PNG files and runs the checks (section 13).

---

## 1. The three source interfaces

I looked at all three running games' screenshots and read their UI code (`Battleground2028.java`, `ResoluteDesk.java`, `IslandChain.java`), plus the current Resolute Desk at `2ce2b37`. All three are hand-painted Java2D in Swing: one `JPanel`, immediate-mode drawing, a fixed design resolution scaled to the window.

| | Strong ideas | Problems |
|---|---|---|
| **Battleground 2028** (war room) | A top strip of the numbers that matter (cash, polls, electoral votes, enthusiasm), each with a change line. The state map labelled with margins ("D+1.5"). The **race to 270** bar. A gold END WEEK button that is always in the same place. Week-tagged news. | Everything is on one screen at once: schedule, map, state panel, race and news. There are three levels of tabs (7 sub-tabs, 5 map modes, 4 state tabs). Text goes down to 10–11 pt. The code has no tooltips and no accessible names (0 `setToolTipText` calls), and colour alone separates the parties. |
| **Resolute Desk** (rooms) | The **room metaphor** ("The Oval Office", "The Roosevelt Room") with a serif display title. **The paper**: a cream broadsheet that turns the week's consequences into headlines. The **whip count by bloc**, saying exactly "about 67 yes, 60 needed · 94% to pass". A slot planner for the week. | A row of 9 small text tabs plus sub-tabs. Button walls: Call / Project / Appoint / Endorse / Threat for each of 8 senators is 40 buttons. 10–12 pt captions. Photographic backgrounds cut contrast. No tooltips or accessible names, in the original or in the 216-file rewrite. |
| **First Island Chain** (operations) | The operational map. The **monospace status line** (WARNING · WATCH, ESCALATION R0/B0) that reads like a terminal. The escalation ladder. A "How to play" panel on the main screen. | Text from 9 pt. Ten tabs in two rows, some abbreviated past meaning (STAFF/L, SND OFF). Long unit lists. A news ticker that crawls, which suits nobody with a reading or vestibular difficulty. Alert levels shown by colour. |

**What Mandate keeps:**
- Battleground's top strip, race bar, margin-labelled map and fixed gold end-turn button.
- Resolute Desk's room titles, the paper (now the weekly **briefing** and the **100-day report**), and the exact whip count.
- First Island Chain's monospace status word (**CALM / WATCH / CRISIS / DANGER / CLASH**) and the escalation ladder (the crisis stages).

**What it changes:**
- **Screens:** one job per screen instead of one screen per game. One level of navigation (a rail with shortcuts), no tab-in-tab.
- **Text:** never below 12 pt, and scalable to 150%.
- **Explanation:** every mechanic has a tooltip, and every disabled button says why.
- **Colour:** never the only signal (margins are written "D+2.1", status is a word, odds are numbers).
- **Decisions:** offered as a few large, numbered options, not button walls.
- **Motion:** no crawling text; toasts can be made static.

## 2. Principles

1. **One screen, one question.** Overview: how am I doing? Campaign HQ: how do I win? Congress: will the bill pass? Each screen opens with a serif title and one line saying what it is for.
2. **The next step is always visible.** The gold primary button in the top bar ends the turn. Its label says what it does ("Run the week", "Take the oath", "End the week"). A red Decisions (n) button appears when something blocks it.
3. **Show the odds before, explain the outcome after.** Each option shows its odds and costs. After each choice, a "What happened" card lists the consequences, and the Timeline records each against its cause.
4. **Depth on demand.** Guided, Standard and Expert change what is shown, never what happens (tested).
5. **Nothing decorative is interactive, and nothing interactive is dead.** This is enforced by an automated check.
6. **Recoverable.** Undo is exact; rewind reaches any turn start; destructive actions ask first.

## 3. Design system

All tokens live in `mandate-ui/…/kit/Theme.java`. Components read them through `Theme.get()`; no screen picks its own colours or sizes.

### Colour

Three palettes: **Standard** (navy), **High contrast** (black, white and yellow focus) and **Colour-blind safe** (blue and orange in place of green/red and blue/red).

| Role | Standard | Used for |
|---|---|---|
| `bg` / `surface` / `raised` / `sunken` | #0A1020 / #111A2C / #1A263D / #0D1526 | page, cards, buttons and table rows, side panels |
| `text` / `muted` / `faint` | #EAF0F8 / #B3BED1 / #8E9AB2 | body, secondary, captions |
| `accent` / `onAccent` | #F2B84B / #1B1406 | the one primary action per view; eyebrows |
| `focus` | #7FB2FF | the 2 px focus ring |
| `good` / `warn` / `bad` / `info` | #45D68F / #F5B547 / #FF6F6F / #7FB2FF | tones: odds, tags, advisor cards, toasts |
| `dem` / `rep` / `tossUp` | #5596FF / #FF6262 / #9A8FB8 | parties and the map ramp |
| `paper` / `paperText` / `paperMuted` / `paperRule` | #F2EDDF / #1F1B14 / #5C5548 / #8E2F2A | briefings and the report |

**Verified by `ThemeTest`, in all three palettes:**
- every text colour on every surface it is drawn on reaches **4.5:1** (WCAG 2.1 AA);
- party colours and the focus ring reach **3:1** against their backgrounds (non-text contrast);
- the map's label colour reaches 4.5:1 at every margin from R+40 to D+40.

The last check found real failures: labels on close Republican-leaning tiles were 3.9–4.5:1. `Theme.legible()` now deepens just those mid-tone fills until the label passes.

### Type

Four open-licensed families, bundled with their OFL licences in `mandate-ui/src/main/resources/fonts/`:

| Face | Family | Used for |
|---|---|---|
| UI | Inter | everything by default |
| DISPLAY | Playfair Display Bold | screen titles, decision titles, the big result |
| MONO | JetBrains Mono | the security status word |
| SERIF | Source Serif 4 | briefings and the report (the paper) |

Sizes, in points before scaling:

| CAPTION | SMALL | BODY | LEAD | TITLE | KPI | DISPLAY | HERO |
|---|---|---|---|---|---|---|---|
| 12 | 13 | 14 | 16 | 20 | 24 | 32 | 56 |

Nothing is smaller than 12 pt; the checks fail any text or button below 12 pt × scale. Text size is 100%, 125% or 150% (Settings), and spacing scales with it.

### Space and shape

- A 4-px grid (`u(n)` = 4n px × scale).
- Page margins `u(8)`, card padding `u(4)`, gaps `u(4)` between cards and `u(1)`–`u(3)` inside them.
- One corner radius.

Hierarchy comes from:
- size and the serif face for titles;
- uppercase tracked **eyebrows** for card labels;
- `muted` and `faint` for secondary text;
- a coloured left **stripe** for tone (advisor cards, tips, toasts, the paper's items).

## 4. Components (`kit/`)

| Component | What it is | Accessibility built in |
|---|---|---|
| `Btn` | Primary (gold), secondary, quiet, danger. Optional key hint ("Ctrl+Enter"). | Focus ring. Enter and Space press it. Accessible name. `disabledBecause(reason)` puts the reason in the tooltip. |
| `Choice` | A large numbered option: title, wrapped detail, a risk tag ("about 53%", "Gamble: 40%"). | Name "Option n: title", description with the detail and tag. Number keys in decision cards. `disabledBecause`. |
| `Segmented` | One of a few (bill size, spending rate, filter). | Arrow keys move the choice. Each segment is a focusable button. |
| `Card` | Rounded surface with eyebrow, optional header control, optional tone stripe. | Height-for-width, so text never clips. |
| `Text` | Wrapping text with a line limit. | Truncation puts the full text in a tooltip; the checks fail truncation without one. |
| `Widgets` | `Kpi`, `Meter` (with a marker such as "218 for a majority"), `Pips`, `Tag`, `Diverging` (relations), `Stages` (bill or crisis progress), `RaceBar`. | Each states its value in text and names itself to assistive technology. |
| `LineChart` | Series, padded axis with round ticks, a reference line ("2% inflation target"), end labels, hover read-out. | A text summary as its accessible name ("Approval: 51% to 48%"). |
| `TileMap` | Equal-area state tiles on a 12×8 grid, filled by margin, labelled with code and margin. | Arrow keys move between states; Enter or Space picks. Focus outline. Hover tooltip. |
| `Table` | Weighted columns, eyebrow headers, striped rows; cells may hold controls. | Columns wrap instead of clipping. |
| `Paper` | The cream document: rule, serif title, dateline, toned items. | Serif body at 16 pt. |
| `Stage` | The layer stack: screen, toasts, one modal. | Esc closes a closable modal. The first control takes focus. The screen behind is not clickable. |
| `Layouts` | `Stack`, `Row` (becomes a stack below a width), `Wrap`, `Side`, `Page`. | Height-for-width throughout: content wraps to the window rather than clipping. |

## 5. Layout and resizing

- **Supported windows:** 1280×720 up to 1920×1080 and beyond. The frame refuses to shrink below 1280×720.
- **Scaling:** text scales to 125% and 150%; at 150% on a 1920×1080 window the layout behaves like a 1280-wide one.
- **The shell:**
  - **top bar:** identity and date, the phase's four to five key numbers, and actions;
  - **left:** the navigation rail;
  - **centre:** the screen, scrolling vertically;
  - **right:** the advisor panel when the window is at least 1440 px × scale wide; otherwise a one-line advisor banner with "Full brief".
- **Re-flow:**
  - card rows collapse to a single column below a breakpoint;
  - KPI rows and button rows wrap;
  - text always wraps to its column (height-for-width layout);
  - so no screen scrolls sideways (a check fails if content is wider than its scroll area).
- **Rebuilding:** a screen is rebuilt from the game state after every change and after a resize settles (160 ms). Screen-local choices (the selected state, the bill draft, the agenda draft, the Timeline filter) survive rebuilds, and keyboard focus is restored to the same control.

## 6. Screens

Every screen below is in `mandate-ui/…/screens/` and renders in `screens` output under the file name given.

| # | Brief item | Screen | What it holds |
|---|---|---|---|
| 1 | Main menu and new-game flow | `MainMenu`, `NewGame` (`menu-*`, `newgame-1..4`) | Menu: Continue (only with an unfinished autosave), New game, Load, Settings, Help, Quit. Wizard: four steps (*The game · The country · Your candidate · The rules*) with a step tracker, a running "Your game" summary, and Back/Next. Next says why it is disabled. |
| 2 | Scenario and candidate selection | `NewGame` steps 2–3 (`newgame-2`, `newgame-3`, `newgame-custom`) | National mood as large options with what each means. Party. Then the fictional cast (ratings, lean), your own candidate (name, home state, five ratings from a 33-point budget with a live meter), or Battleground's real roster (opt-in). Step 4: opponent, difficulty, complexity, tips. |
| 3 | National overview | `Overview` (`*-overview`) | The map (2028 result or this week's polls), the latest week (polls and movers, or the last briefing and this week so far), the approval trend, Congress, security status, the economy, and "Where to next". |
| 4 | Election headquarters | `CampaignHQ` (`campaign-campaign`, `campaign-hq-*`) | A selectable state map with state detail and "Add a rally here". The race for 270 with tipping point and forecast. Standing orders: strategy, spending rate and message. What moved the race, the opponent's plan and why. Promises made, and the bills you could still promise with their public appeal. Optional plan-by-hand. Battlegrounds table and voter-group crosstabs (not in Guided). |
| 5 | Oval Office and executive dashboard | `OvalOffice` (`transition-oval*`, `week1-oval`, `crisis-oval*`, `governing-oval`) | *Transition:* the agenda picker with each bill's odds by its best route. *Hundred days:* the week (attention pips, decisions waiting, done so far), the paper brief, the cabinet with nominee choices, party wings and the opposition, delegation to the chief of staff, and other actions (meeting the opposition's leaders, leaning on the Fed, releasing oil from the reserve). *Governing:* monthly actions, the agenda, the cabinet. |
| 6 | Congress and legislation | `CongressScreen` (`*-congress*`, `crisis-concession`, `governing-push`) | The flagship bill's stages. Size and pay-for, with a live preview of position and deficit before you amend. The vote: pass chance, expected and needed votes, Call or Insist. Reconciliation where allowed, going public. The whip table by bloc with "Work this bloc" (projects, appointments, threats, each with its cost). The funding deadline. Monthly pushes by route. |
| 7 | Economy and budget | `EconomyScreen` (`*-economy`) | Headline indicators (reported and actual where they differ), jobs and prices against the 2% target, gas, interest rates (not in Guided), the economic challenge and your answer, the Fed and its next meeting, the budget. |
| 8 | Diplomacy and national security | `SecurityScreen` (`*-security`, `crisis-decision`) | The status word, deterrence, the crisis ladder, and the **intelligence estimate** of the other side's resolve: a band, not a number, whose width depends on your Secretary of State. Flashpoint tension and relations. Diplomacy in the slice lives here: crisis diplomacy and relations. There is no separate diplomacy screen because the slice has no diplomatic actions outside crises and monthly governing. |
| 9 | Historical timeline and decision log | `Timeline` (`*-timeline*`) | The game's record, newest first, filterable (everything, notable moments, campaign, office, your decisions), notable moments tagged. Beside it, the consequence ledger: each decision and every effect it caused (all effects and their owning engine at Expert). |
| 10 | Save/load and settings | `SaveLoad`, `SettingsScreen` (`campaign-saves`, `crisis-rewind`, `menu-load`, `campaign-settings`, `menu-settings`) | Autosave plus three slots, with Save here / Load / Delete (overwrite, load and delete ask first). Recover: Undo and every earlier turn start. Settings: text size, palette, reduced motion, complexity, tips (and reset), advisor, auto-open decisions, the unspent-attention warning, a live preview, and the controls. |
| + | Results | `Results` (`report-results*`, `lost-*`) | Election night as a card that comes forward on its own. A loss comes with a post-mortem and a replay. The 100-day report: the grade, each goal met, partly met or missed **with the reason**, approval week by week, the decisions that mattered, and "Play it differently". |
| + | Decisions, Help | `Decisions`, `Help` (`*-decision`, `help`) | Decision cards (crises, the economic challenge, funding, events, debates) with numbered options, odds and effects, then "What happened". Help: every shortcut and every glossary term. |

## 7. Gameplay UX against the brief

| Requirement | How it is met |
|---|---|
| Tooltips explain mechanics | A glossary of 20 mechanics (`Glossary.java`) appears on the KPIs and cards that use them, and in Help. Every button explains itself; every disabled button says why ("Needs 6 political capital; you have 4", "Reconciliation needs your party to hold the House and the Senate"). |
| Major decisions show risks and tradeoffs | Each option states its cost and its odds before you choose: "about 53%" for diplomacy, "Gamble: 40%" for a risky event answer, "Harder vote" for a loyalist nominee, the capital each concession takes. The bill designer previews position and deficit before you amend. The vote card gives the pass chance with expected and needed votes before you call it. |
| Results explain why | "What happened" follows every decision ("The back channel goes quiet. Tehran reads hesitation."). The weekly briefing lists consequences and reactions. The ledger ties each effect to its decision. Election night names the closest states and, on a loss, why ("The polls flattered you by 2.0 points"). The report grades each goal with its reason. |
| Important information is discoverable | The advisor's cards each carry "Go to …" for where to act. Decisions (n) sits in the top bar and blocking decisions open on their own. Tips have "Show me". Every screen has a shortcut shown beside its name. Toasts announce votes, crises, shutdowns and the Fed. |
| Advanced detail without overwhelming beginners | Three complexity levels (section 10). Secondary detail is muted and below the fold. Big numbers first, explanations under them, raw figures at Expert. |
| Repetitive decisions can be delegated | The campaign runs on **standing orders**; planning a week by hand is optional. In office, the **chief of staff** can take over areas (legislation, the cabinet, crises, the budget): each costs no attention and the staff answers its decisions at half strength. Decisions can be set not to open on their own. |
| Recover from mistakes without restarting | **Undo** (Ctrl+Z) replays the game without the last command, so the state is exact and a random outcome cannot be re-rolled. **Rewind** to the start of any earlier turn. Replay the hundred days from Inauguration Day; replay the campaign in the same world. The game autosaves every turn, with three manual slots. Overwrite, delete, rewind, load and ending a week with attention unspent all ask first. |

## 8. Visual feedback for important events

- **Toasts** (top right, toned stripe) for each notable log line: votes passed or failed, laws signed, nominees confirmed or rejected, a crisis beginning or escalating, an armed clash, a shutdown and reopening, a party revolt, the opposition changing course, the Fed moving, the opponent changing plan, debate results. `Notable.classify` decides what counts.
- **Election night** and the **100-day report** come forward on their own, once.
- **Status** is written in words, never colour alone: CRISIS on Security; the red Decisions (n) button; "Leans no" in the whip table; signed changes ("−0.8 this week") that are neutral when they round to zero.
- **Reduced motion** (Settings) keeps toasts static.

## 9. The advisor

`mandate-core/…/advisor/Advisor.java` produces up to six ranked cards (Now, Watch, Opening, Note). Each has a headline, a sentence of detail and a **"Based on:"** line naming its source: "the last four weekly polls", "Senate whip count", "Intelligence estimate 60%, deterrence 50".

**It reads only what the player's own screens show:**
- the noisy polls, not the true margins;
- the whip count, not the vote;
- the intelligence estimate, not the other side's resolve;
- reported indicators.

Its odds are written "over 99%" or "under 1%", never "100%".

**Two tests guard this (`AdvisorTest`):**
- **Source scan.** The advisor's source may not mention the hidden state: the true margins, the national swing, the polling miss, the hidden resolve, the escalation risk, the random streams, the save fingerprint or the election result before it happens.
- **Invariance.** Changing the other side's hidden resolve in a save leaves the advice identical.

## 10. Tutorials and complexity

**Coach** (`mandate-core/…/tutorial/Coach.java`) offers one situational lesson at a time. Each has a sentence about the decision in front of you and something to try now.
- **Where it shows.** A lesson shows on the screen it is about and on the Overview; it does not follow you elsewhere. The lessons for crises, the economic challenge, funding, events and debates appear inside the decision card itself.
- **Controls.** "Got it" retires a lesson. "Show me" goes to its screen. "Turn tips off" stops them, and Settings can turn them back on and reset them.
- **Persistence.** Seen lessons are stored in the save.

**Complexity changes presentation only.** A test confirms the game state is identical whichever level is set.

| | Guided | Standard | Expert |
|---|---|---|---|
| Advisor | 4 cards | 6 | 6 |
| Campaign HQ | map, race, orders, movers, promises | + battlegrounds table, voter-group crosstabs | same |
| Congress whip table | bloc, votes, wants | + relationship (Warm / Cool) | relationship as a score |
| Economy | 6 headline indicators, jobs and prices, gas | + 10-year yield, confidence, the rates chart | + Fed credibility, output gap, debt |
| Timeline ledger | 8 decisions, 6 effects each | 20 decisions | 60 decisions, every effect, owning engine |

## 11. Keyboard

| Keys | Action |
|---|---|
| Ctrl+1 … Ctrl+8 | Overview, Campaign HQ, Oval Office, Congress, Economy, Security, Timeline, Results |
| Ctrl+S, Ctrl+, | Save and load, Settings |
| Ctrl+Enter | End the turn |
| Ctrl+D | Open the decisions waiting |
| Ctrl+Z | Undo |
| F1, F2 | Help; show or hide the advisor |
| Esc | Close a card; leave the setup |
| 1–9 | Choose an option in a decision card |
| Arrow keys | Move within a segmented choice or the map |
| Tab, Shift+Tab, Enter, Space | Move between controls; press the focused one |

**Tab order:** top bar, navigation rail, then the screen, in reading order. Under a real window it ran: *Advisor › Menu › End the week › Overview › Campaign HQ › … › Settings › Got it*.

## 12. Accessibility

**Done:**
- 12 pt minimum and 150% scaling.
- AA contrast in three palettes (tested).
- High-contrast and colour-blind palettes.
- Colour never the only signal.
- Visible focus everywhere.
- Full keyboard play: the flow tests and the real-window check play by keys and buttons.
- Accessible names and descriptions on every button, option, chart, meter and map.
- Reduced motion.
- No time pressure anywhere.

**Not done, or not verifiable here:**
- **Screen readers.** Java's accessibility bridge works on Windows (JAWS, NVDA) and macOS (VoiceOver); there is no bridge on Linux, so no screen reader has been run against it.
- **Toasts** are not announced to assistive technology; their text is also in the Timeline.
- **The map** is navigable by keyboard, but a screen-reader user would be better served by a table view of the same states.

## 13. Automated checks

`UiChecks` runs on any laid-out component tree (headless is fine) and fails:
- a component clipped by its parent;
- content wider than its scroll area;
- text cut off without a tooltip carrying it;
- text or a button below 12 pt × scale;
- a visible button with no action, with no accessible name, or disabled with no reason;
- a screen with nothing to focus.

| Suite | What it covers | Result |
|---|---|---|
| `ScreensTest` | **51 screens**: every screen; 1280×720, 1600×900 and 1920×1080; 150% text; three palettes; three complexity levels; and the dialogs behind buttons (decisions, concessions, nominees, pushes, rewind, load, help, election night), opened by pressing the button. It also checks that the checks catch deliberately broken components. | 0 problems |
| `FlowTest` | Nine flows played only through visible buttons:<br>• the wizard to a running campaign;<br>• a whole campaign to election night (win or lose);<br>• the transition to the 100-day report, with the checks on every week's screen;<br>• undo is exact;<br>• rewind is exact;<br>• every screen has a bound shortcut and Help lists them all;<br>• advisor panel vs banner by width;<br>• complexity changes only presentation;<br>• tips show where they belong and stay dismissed. | 9 of 9 pass |
| `ThemeTest` | Contrast of every token pair, map labels across the ramp, minimum sizes, scaling. | 5 of 5 pass |
| `RealWindowCheck` | The real frame under Xvfb with `java.awt.Robot`:<br>• real key presses: Tab to New game, Enter, Esc, Ctrl+6, Ctrl+4, F1, Ctrl+D, 1, Enter;<br>• Tab never lands on a hidden control;<br>• resize to 1280×720, 1920×1080 and 1440×900, with the checks after each;<br>• the minimum size holds. | 17 of 17 pass |

**Running them.** The first three run with `mvn install`. The screenshots and the real-window check run on demand:

```bash
java -jar mandate-cli/target/mandate.jar screens target/screens
```

```bash
xvfb-run -a -s "-screen 0 1920x1080x24" java -cp mandate-ui/target/test-classes:mandate-ui/target/classes:mandate-core/target/classes game.mandate.ui.RealWindowCheck target/real-window
```

**What the checks cannot tell you:**
- whether a screen is clear;
- whether the hierarchy guides the eye;
- whether the copy is right;
- whether the colours are pleasant;
- whether anyone enjoys it.

## 14. What inspection found and fixed

Looking at the rendered screens, not just the check results, found these. All are fixed. Most are things no automated check here would have caught: they look wrong, or say something untrue, while passing every rule.

- The advisor panel's text ran off its right edge. Scroll areas now track their viewport's width, and a check for horizontal overflow was added.
- Navigation labels were centred; they are now left-aligned with the shortcut at the right.
- "Esc to decide later" was cut off by a font-metrics mismatch (fractional metrics were removed).
- The top bar was crowded: shorter KPI captions, and Advisor became a toggle.
- Crisis stage labels overlapped ("Harassment of tankTanker seized"). Labels now wrap inside the room between their neighbours.
- A tip about the crisis followed the player onto every screen. Tips now show only where they belong, and inside the decision card.
- The Overview's "This week" card showed last week's briefing; it is now "The latest", with this week labelled separately.
- In the transition, the advisor sent the player to Congress to set the agenda; it now goes to the Oval Office.
- Odds read "0%" and "100%". They are now "under 1%" and "over 99%", in the interface, the advisor and the tutorial.
- The campaign's Timeline filter "Notable moments" was empty; the campaign's own moments are now recognised.
- Save slots wrapped their buttons onto two lines; the columns were rebalanced.
- Rewind offered one meaningless "An earlier turn" for saves without the interface's records. Rewind points now come from the command history itself.
- Help and Settings had their only Close button at the bottom of a long card; each now has one at the top too.
- Disabled concession and push options had no explanation; each now says why.
- KPIs without a subtitle sat lower than their neighbours (found in the real-window capture); KPI rows are top-aligned.
- A zero change ("+0.0 since the oath") was green; it is now neutral.
- Labels on close-race map tiles failed AA contrast (found by `ThemeTest`; see section 3).
- **A misleading figure.** The monthly push dialog showed an executive order as "under 1%" with House and Senate counts. An order needs no vote; it fails only if a court blocks it (35%). The dialog now says so and shows about 65%. It also needed 2 attention to open though an order costs 1; it now opens with 1, and the vote routes say why they need 2.
- Tags (VACANT, ARMED CLASH, AWAITING YOUR ANSWER) stretched across their whole card; they now keep their own width.

## 15. Needs human review

1. **Visual quality.** The palette's harmony, the density of the top bar at 1280 and at 150%, whether the gold primary is overused, and whether the serif titles and the paper feel of a piece with the rest.
2. **Hierarchy in use.** Whether a first-time player finds the next step without the advisor. This needs watching players, not screenshots.
3. **Copy.** All microcopy was written in one pass. It needs an editor and, for the politics, a subject-matter read.
4. **Screen readers.** NVDA or JAWS on Windows and VoiceOver on macOS: names, reading order, live regions for toasts.
5. **Colour-blind simulation.** The colour-blind palette passes contrast tests. Nobody has viewed it through a deuteranopia or protanopia simulation, or asked colour-blind players.
6. **HiDPI.** macOS Retina and Windows at 150% and 200% system scaling, where Java's own scaling multiplies ours.
7. **Window managers.** Maximise, snap, multiple monitors, and moving between monitors with different DPI.
8. **Mouse feel.** Hover states, cursor changes, scroll speed, tooltip delays (350 ms to appear, 15 s to dismiss).
9. **Performance.** Measured here, headless, a rebuild plus layout at 1920×1080 takes 6–23 ms (the campaign is the heaviest). It needs checking with real painting on a low-end laptop at 150%.
10. **Focus after rebuilds.** Focus is restored by the control's accessible name. Where names repeat (two "Go to Security" cards) it lands on the first.
11. **Long sessions.** Toast frequency, and whether the weekly rhythm of decisions, briefings and toasts tires over a full term.

## 16. Known limits

- The interface is Swing with its own components; see ADR-7 in ARCHITECTURE.md. Text input is limited to the custom-candidate form.
- The tile map is a cartogram, not geography; the source games' painted maps were not ported.
- There is no sound. The source games' audio was tied into their simulation code; see ADR-2.
- The REELECTION_CAMPAIGN_2032 phase and the epilogue reuse the campaign and results screens. Their screenshots are not in the checked set because the slice ends at day 100.
