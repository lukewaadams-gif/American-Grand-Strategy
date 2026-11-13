# Decisions to review

Choices made while building overnight that we had not discussed. Each was made in favour of Resolute Desk and realism, and can be changed. Newest last.

## Milestone 5: Congress and the budget

1. **The hundred days keep their own flagship process.** Resolute Desk's Congress (blocs, hours, the whip, stages) runs every other bill from the inauguration. The hundred days' flagship keeps the approved drafting, committee and floor sequence and its report card, so their tests and balance hold. Folding the flagship into the Roosevelt Room's pipeline can come later.
2. **Political capital stays for the hundred days only.** In governing, the Roosevelt Room's hours on the Hill, favours, promises and appointments replace it, as the port plan says.
3. **The first year's budget request is due in June,** not April as in Resolute Desk: the Hill opens on day 100, so April would come before the president could shape it.
4. **The House by state uses a 22-point spread of districts around each state's lean.** It reproduces the delegations of the 2020s (California about 44-8, Texas 12-26). Resolute Desk sets only the national total, which the game keeps (222 Democrats at an even national vote, 3.8 seats a point).
5. **The Senate is seated seat by seat in 2029:** class I and III seats as the state data says, class II filling the rest (most Democratic states first) so the total matches the 2028 result.
6. **The leaders of January 2029 are Resolute Desk's era file's** (fictional). A new Congress keeps each party's leaders; the majority's leader becomes Speaker or Majority Leader.
7. **Nominal GDP for the debt limit** grows 4.5% a year from the economy's 2029 figure, since the model keeps the debt as a share of GDP.
8. **Pushes from the old screens go to the Hill** after the hundred days, without spending political capital (its role ends with the hundred days). A bipartisan strategy drafts the bill smaller and narrower (size 0.8, scope 0.3); reconciliation ticks the box.
9. **Hill decisions must be answered before the week ends,** like events, rather than waiting on the desk. Each still has its default if the staff handles it, and watch mode takes the staff's answer.
10. **The budget request is not a decision card;** it is shaped and sent in the Roosevelt Room and goes as drafted when its deadline passes.
11. **A policy step can only be drafted as a bill if the policy can change by law or by the budget;** order- and rule-only policies wait for the West Wing.
12. **Blocked nominees move to milestone 6,** where nominations are made: an opposition Senate's confirmations need the West Wing's nominees to exist first.

## Milestone 6: the executive, the press and storylines

1. **Real people only with the real-person roster** (your instruction). By default the Supreme Court is nine fictional justices with the real Court's parties, ages and ideologies, and cabinet picks are fictional; with the roster on, the real 2025 Court sits and real politicians from the roster appear among cabinet picks.
2. **The West Wing is staffed on day 100 with each job's expert** (Resolute Desk without its transition office, which comes with milestone 10), keeping the three posts the hundred days filled; a post the hundred days left empty has an acting official.
3. **Resolute Desk's bugs, fixed:**
   - a ruling for the president lifts an earlier block;
   - a denied appeal to the Supreme Court counts as a win only if the action stands;
   - the counsel's lower legal risk counts once, not twice;
   - emergencies apply their own effects and can be sued;
   - "rollout trouble" is shown;
   - a second impeachment inquiry can start after an acquittal;
   - a rejected justice is not offered again;
   - ending an emergency needs both chambers against the president;
   - rules count in the party's record when they take effect.
4. **A milestone 5 fix:** the Hill squared the legislative director's quality where Resolute Desk uses it directly; relations with Congress now drift as Resolute Desk's do.
5. **Investigations and impeachment stay on the Hill** (milestone 5) and read the executive's pressure (defiance and the impeachable scandals) and its worst scandal, rather than running twice.
6. **Effects on voter groups are recorded but not applied yet:** the groups' model comes with milestone 7.
7. **The library's economics on this game's model:**
   - growth becomes productivity, unemployment the natural rate, inflation goods and services prices;
   - oil is oil, the deficit a program, tariffs the average tariff.
8. **Questions from the West Wing default after their week** to the staff's answer (Resolute Desk's inbox waited forever); a press conference or speech left alone is finished by the staff at once.
9. **The Republicans' populist wing is named "Populists"**, as elsewhere in the game.
10. **The State of the Union is in the week holding February 4** each year; the first year's joint session falls inside the hundred days.
11. **Stories that read the home front** (protests, overdoses, the crime index, cyber attacks, the grid) wait for those measures, which come with milestones 7 and 8.
12. **The vice president is a fictional running mate** made for the ticket, with ratings drawn when the West Wing is staffed.
13. **The housing test now averages several games:** zoning's housing effect phases in over five years, so a single game's two-year comparison was too noisy to judge.

## Milestone 7: the Residence and the country's groups

1. **The groups break the national number down; they do not move it** (as in Resolute Desk). An effect aimed at a group shifts its cells by the effect times their share of members, so groups that cut across the cells (union households, say) move less than whole cells (Black voters).
2. **The cross-cutting groups' state shares** are the campaign's own figures (Battleground 2028's union, veteran, gun-owner and LGBTQ shares), exported by `tools/BgStateTraits.java`.
3. **State approval keeps milestone 3's model** (the 2028 lean, visits and the state's economy). The cells set only the groups; using them for the states too is a later choice.
4. **The groups do not yet feed the 2032 campaign;** the second term (milestone 9) will carry them into the electorate.
5. **The Residence's plans fill this week's weekend or mornings on the calendar,** replacing whatever was there; the calendar stays the place to fine-tune it.
6. **A milestone 3 fix:** the country room's issue table overflowed a phone's width; both tables now fit.

## Milestone 9: the second term

1. **The milestone came before the world (milestone 8),** while the world's specification was being written; nothing in it waits on the world, and the verdict's crisis and international measures read the world's record once milestone 8 fills it (until then they sit at Resolute Desk's no-crisis baseline).
2. **The president can step aside, or be denied renomination,** as in Resolute Desk: fifty weeks before 2032 the desk asks, a weak president draws a primary challenger, and the party's nominee then runs on Resolute Desk's successor model instead of the 2032 campaign headquarters. A question left on the desk takes the staff's advice (Resolute Desk's: run unless approval is below 36%).
3. **A challenger who wins the primaries is the nominee** (Resolute Desk names a different successor, which reads oddly).
4. **The race to succeed the president is decided on Resolute Desk's model** (national margin, states' leans, regional and state swings), not a second Battleground 2028 headquarters: the president campaigns from the White House, as decided. The leans are the latest presidential election's state margins.
5. **The voter groups do not feed the vote,** as in Resolute Desk, where elections read national approval and the states (milestone 7's note 4 is settled this way).
6. **The rivals for the nomination** are the vice president and two of the party's figures from the candidate cast (fictional by default); the primaries' model is new (Resolute Desk has none for an open race): appeal, the vice president's tie to the president, momentum and an endorsement, with a state's own mood.
7. **The second term's legacy goals come from a theme** chosen on the desk (four themes, three goals each), so the choice is a single question; the verdict counts each goal.
8. **The second inaugural address** is a question in the week before the oath, with three tones (unity, unfinished business, history's judgment), each with modest effects on relations, the wings and the record.
9. **The lame-duck decline** is new (Resolute Desk has only the end-of-term week rule): it weakens the president's pull on bills, more after the second term's midterms, fully once a successor is chosen.
10. **The verdict replaces the old legacy score** for any game with a presidency; a game lost in 2028 keeps the campaign's score. The historians' ranking is the C-SPAN 2021 survey Resolute Desk uses, real presidents as a reference table only.
11. **The weeks between Election Day and the handoff** start on the Monday after the election and end on January 20 itself.
12. **The 2032 re-election campaign still pauses the presidency's weekly systems** (Congress, the West Wing, the economy) for its fifteen weeks, as before this milestone; running them alongside the campaign is left for the final audits.

## Milestone 8: the world and the Situation Room

1. **The Taiwan war runs on this game's operational model**, not Resolute Desk's single roll. Its outcome sets the Strait's stage: defended, back to quarantine; stalemate, blockade; fallen, Taiwan's allies estranged.
2. **The older monthly diplomacy model is switched off during the presidency.** Its three tensions, deterrence and the ladder follow the world each week, so the markets, the war and the older screens read one world. Before the presidency, and in games without it, the old model still runs.
3. **Oil goes through this game's markets.** The hot spots set the risk premium as before, and an "oil" effect is a price shock. Resolute Desk's own oil base and its decay toward a theater target are not ported, to avoid counting the world twice.
4. **Resolute Desk's bugs, fixed:**
   - lowering a tariff no longer charges relations as a new tariff;
   - a toppled government's relations no longer drift back to the old regime's hostility (its anchor rises to neutral);
   - the "tw_war" crisis is replaced by the operational war (point 1).
5. **Resolute Desk's quirks, kept:**
   - "tcn" steps the China tariff down one level whatever its value;
   - allied spending only lowers NATO cohesion;
   - Canada reads Pacific cohesion;
   - a War Powers vote comes only once;
   - covert actions in the Operations tab have no conditions;
   - the deficit hawks' votes stand for war hawks on authorizations and treaties.
6. **Farm effects** (retaliation, trade deals, the summit's trade ask) move rural voters' standing, since this game has no farm-income gauge of Resolute Desk's kind.
7. **Foreign policy and security count toward this game's "China" issue,** as the storylines already did. Immigration is its own issue.
8. **A War Powers veto adds scandal** (Resolute Desk's impeachment pressure, which here comes from the West Wing's scandals).
9. **A trip abroad fills three slots** from the one chosen, and later slots can be changed as usual. Its flight costs stamina on the first slot, up to twelve points.
10. **The world opens on day 100,** with the West Wing. During the first hundred days the hundred days' own crisis is the foreign-policy story.
11. **Real leaders only with the real-person roster:** the stand-ins in `countries.tsv` by default, the names in `real_names.tsv` when the roster is on.
12. **The map** is Natural Earth's 1:110m countries from world-atlas, in the same Robinson projection and frame as Resolute Desk's map, so its label points fit.

## Milestone 10: the rest

1. **The transition office covers the White House staff and the agenda.** The Senate-confirmed cabinet stays with the first hundred days' own nominations (State, Treasury, Defense) and the West Wing's on day 100. Resolute Desk's transition also covers the cabinet, but here two systems would compete.
2. **The transition is one turn, with a pool of twenty-four hours** rather than six weeks of eight hours. Background checks report at once; names do not leak. The grade keeps Resolute Desk's formula, minus the leaks and with the chief of staff's bonus for naming one at all.
3. **The front page of the preview is now the whole game.** The campaign-only preview moves to `campaign.html`; `presidency.html` stays.
4. **New games run the full length** (the campaign and on for as long as the president serves). The 100-day game length remains in the engine and the terminal game.
5. **Sound is synthesized** (a filtered-noise room tone, a clock, a chime). No sound files. It is off until a click turns it on, as browsers require.

## The Strait of Hormuz crisis

1. **A deal is the cheapest way out**, as the design's realism implies: Iran wants relief more than a war. Its costs:
   - sanctions on Iran are eased;
   - the envoy takes three days between missions;
   - every strike sets the talks back.
2. **The ladder runs on two dispositions moving toward targets**, not on random escalation. Iran's own losses at sea, Beijing's pressure and the regime's instability restrain it, so a closure can wind down without a deal once little is left to fight with.
3. **The intelligence community's estimates** of the coastal sites are what the room shows. Struck launchers partly recover (mobile launchers come out of hiding), and the estimates lag.
4. **Destroyers are hard to hit**, as the Red Sea campaign of 2023–24 showed: one salvo in twenty aimed at an escort hits. Mines strike minesweepers rarely and kill few.
5. **The full invasion takes about three months of build-up and two and a half of fighting.** It ends in an occupation on the Situation Room's war-of-choice model. The day-150 stalemate waits while troops are on the move.
6. **Not yet built:**
   - Congress's vote on an invasion (the War Powers clock);
   - National Security Council meetings with options papers;
   - the standalone start's year (2029–2036) and custom preset. The start screen offers the three presets in September 2029.
7. **The watch mode and the autopilot follow a playbook:** escorts, minesweepers, the allies, the reserve, the pipelines and reinsurance, then the envoys. They never strike or invade.
8. **The map is Natural Earth's 1:10m countries,** so the strait's islands show, in an equirectangular projection true at 26.5° N.

## The Taiwan crisis (Island Chain) in the presidency

1. **China's escalation to a quarantine or a blockade opens the crisis,** with China's planner free to stand down. "Deterrence holds" ends about half the crises, after some 70 days of watching.
2. **A year must pass between Taiwan crises, and none comes after Taiwan has fallen.** The old monthly ladder's war event now opens the crisis, with its answer as the commitment.
3. **The president's commitment is the rules of engagement.** The crisis starts at INTERVENE: the rung directive is the president's choice to stay out, to arm, or to fight. There is no separate decision at the start.
4. **Losses are written back:**
   - munitions by matching id;
   - carriers and other ships;
   - aircraft split between land-based and carrier aircraft.

   A nuclear catastrophe is recorded as Taiwan fallen, with the gravest loss of standing. The presidency goes on; there is no separate epilogue.
5. **A crisis does not stop the calendar's hand-offs mid-crisis:** if a term ends during a war, the hand-off waits until the crisis ends. This is rare, and to review.
6. **Island Chain on its own is a separate session in the browser and is not saved yet.** It uses the source's own approval and Congress for both sides.
7. **The waters around Taiwan show on the island's map,** zone by zone. The Pacific map shows them as one marker so the strait does not crowd.
8. **Not yet built:**
   - the source's National Security Council meetings and options papers;
   - Congress's War Powers clock and supplemental (the engine's AUMF rule runs, but only inside the crisis);
   - unit orders on the map (the engine supports them; the room offers the directives).

## Election night (13 November 2026)

1. **The broadcast is a script written from the stored result, never a recomputation.**
   - It uses each state's final margin, the real 2024 poll-closing times, and how long each state takes to count (slow mail counts in Arizona, Nevada, California and Pennsylvania).
   - Early returns lean one way and settle on the final margin, so leads can change hands during the night.
   - A race is called only once its count favours the real winner. Safe states are called at the close.
   - The same result always plays the same night.
2. **Its place in the night is kept for the browser session,** so a reload resumes at the same report. A new session starts the night again; it is the same night.
3. **"Leave the broadcast" is always offered,** so the player is never held. Leaving counts the night as watched.
4. **The broadcast ends on the analysis:** the tipping-point state, the closest states, the flips since 2024, Congress, and for a loss the campaign's post-mortem. The full campaign assessment for Campaign Only comes with the four game modes.
