# Remaining issues

What is known and not yet fixed, as of 13 November 2026. Each item names its severity, where it was found, and the document with the detail. Fixed bugs are in `BUG_FIX_REPORT.md`; choices to review are in `DECISIONS_TO_REVIEW.md`.

## Gameplay and simulation

| Issue | Severity | Detail |
|---|---|---|
| **Taiwan never falls in Individual Crisis.** In the sample world the crisis's outcome depends on the preset (China's plan), not on the president's orders: "diplomatic only" gives the same result as fighting. A win and an end in part were reached, a loss was not. | Medium | BUG_FIX_REPORT M-01. Needs a look at the ported war model's presets and how the president's rules of engagement reach it. |
| **The 2032 campaign pauses the presidency.** The re-election campaign runs the 2028 campaign's turns. The economy now moves through it (B-08), but the world, Congress, the West Wing and the president's slots wait for about 15 weeks. | Medium | DECISIONS_TO_REVIEW, "The two terms"; BUG_FIX_REPORT B-08 |
| **Staff-run presidents often step aside in 2032.** Unanswered, the "Another four years?" question takes the chief of staff's advice, which is to step aside below 36% approval. This matters for watch mode; a player is asked. | Low | SECOND_TERM_VALIDATION, observations |
| **Removal from office is frequent in staff-run games:** 19 of the 126 presidencies in the lifecycle run. Whether it is too frequent for a player is a balance question. | Low | SECOND_TERM_VALIDATION |
| **A hostile Senate can reject Supreme Court nominees over and over;** one staff-run game saw ten nominations in two and a half years. | Low | Lifecycle run |
| **Elections on weekly turns are reported the Monday after.** The night itself is dated on Election Day, and in a crisis's daily turns the result comes on the day. | Low | DECISIONS_TO_REVIEW, "The two terms" |
| **A provoked Chinese attack gives both a rally and an approval bonus,** two separate approval terms. | Low | BUG_FIX_REPORT E-06, remaining |
| **A Taiwan crisis deterred without a war counts nowhere in the verdict's crisis record.** | Low | BUG_FIX_REPORT E-06, remaining |
| **The Iran occupation's weekly oil shock runs alongside the fading Hormuz premium.** | Low | BUG_FIX_REPORT E-06, remaining |
| **The crisis's market drawdown (`FinancialShock`) may overlap the stress the markets already felt during the crisis.** | Low | BUG_FIX_REPORT E-06 |

## Modes and interfaces

| Issue | Severity | Detail |
|---|---|---|
| **Individual Crisis starts only in the sample presidency (September 2029).** There is no choice of administration or year, and no custom preset. | Medium | DECISIONS_TO_REVIEW, "The four games"; HORMUZ.md |
| **The older interfaces offer only Full Game and Campaign Only.** That covers the desktop (Swing), the command line and the old browser build at the site's root. The plan is for the desktop to serve the browser game locally; that is not built. | Medium | MODES.md |
| **The site's root still serves the older vertical slice.** It changes only when the preview is approved. Its `version.json` now says what it is. | — | README; BUG_FIX_REPORT E-05 |
| **Island Chain on its own is not saved.** | Low | ISLAND_CHAIN.md |
| **Removal from office is not reached by any browser save.** The engine covers it. | Low | MODE_TEST_MATRIX |

## People and portraits

| Issue | Severity | Detail |
|---|---|---|
| **The real-people data is as of 2025–26.** It needs a refresh before release. | Medium | DECISIONS_TO_REVIEW, "Real people in 2029" |
| **Not represented:** governors, and House members other than the leaders, committee leaders, swing and base members. | Low | DECISIONS_TO_REVIEW |
| **A newcomer's career is the record of the role they hold.** It does not follow them from one office to another. | Low | BUG_FIX_REPORT R-01 |
| **Portraits are correct and distinct, not recognisable caricatures.** Several real men with short dark hair look alike. Recognisable likenesses need commissioned illustration. | Low | BUG_FIX_REPORT U-01 |
| **Only presidential candidates are drawn.** Everyone else appears by name. | — | BUG_FIX_REPORT U-01 |

## Testing and performance

| Issue | Severity | Detail |
|---|---|---|
| **No timed human playtest yet.** The README's playtimes are estimates from turn counts. | Medium | README, "How long a game takes" |
| **The browser's engine is about 4.5 times slower than the JVM's** (the parity run: 89 s against 20 s). Turns are still well under a second, and Governing Only's start (the 2028 campaign played for both sides) takes about a second. | Low | Parity run |
