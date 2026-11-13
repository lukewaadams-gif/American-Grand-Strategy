package game.mandate.web.screens;

import game.mandate.core.data.Fmt;

import game.mandate.web.kit.El;

import game.mandate.core.electorate.ElectionResult;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.opening.DayHundredReport;
import game.mandate.core.state.Party;
import game.mandate.web.App;
import game.mandate.web.kit.Btn;
import game.mandate.web.kit.Card;
import game.mandate.web.kit.Layouts;
import game.mandate.web.kit.LineChart;
import game.mandate.web.kit.Paper;
import game.mandate.web.kit.Text;
import game.mandate.web.kit.Theme;
import game.mandate.web.kit.Widgets;

import game.mandate.web.kit.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Endings: election night (as the moment it is, over the screen), the post-mortem of a defeat, and the 100-day
 * report with its goals and the reasons for each. Every ending offers a way to play again differently without
 * starting from scratch: govern the hundred days again, or replay the campaign in the same world.
 */
public final class Results {
    private Results() {}

    /** Election night: the result, why, and what next. */
    public static void electionNight(App app) {
        GameEngine g = app.session.g();
        Theme t = Theme.get();
        ElectionResult r = g.latestPresidentialResult().orElseThrow();
        int year = r.year();
        boolean second = year > 2028;
        Party me = g.player().party();
        boolean won = r.winner() == me;
        // the night is over when the player moves on: the engine records it (ElectionNightSeen), so it is not shown again
        Runnable seen = () -> { if (g.electionNightPending() == year) app.session.apply(new game.mandate.core.command.Command.ElectionNightSeen(year)); };
        Card c = new Card(null).stroke(won ? t.accent : t.borderStrong);
        c.with(new Text(("Election night  ·  " + game.mandate.web.Notable.date(second ? g.calendar().election2032() : g.calendar().election2028())).toUpperCase(), t.bold(Theme.Size.CAPTION), t.accent),
                Text.display(won ? (second ? "Four more years" : "You win the presidency") : (second ? "You lose re-election" : "You lose"), Theme.Size.DISPLAY), Text.body(Views.resultLine(r)));
        c.with(new Widgets.RaceBar(r.demElectoralVotes(), 0, r.repElectoralVotes(), g.side(Party.DEMOCRAT).candidate().name() + " (D)",
                g.side(Party.REPUBLICAN).candidate().name() + " (R)"));
        Map<String, Double> m = r.stateMargins();
        StringBuilder close = new StringBuilder();
        for (String code : Views.closest(g, m, 8)) close.append(code).append(' ').append(Parts.margin(m.get(code))).append("   ");
        c.with(Text.eyebrow("The closest states"), Text.muted(close.toString().trim()));
        if (second) {
            c.with(Text.body(g.congress() + ". " + (won ? "The second term begins on January 20." : "You are president until the handoff on January 20.")));
            c.with(Layouts.wrap(t.u(2), t.u(1), Btn.primary("Back to the Oval Office", e -> { seen.run(); app.stage.closeModal(); app.open(App.ScreenId.OVAL); })));
        } else if (won && g.mode() == game.mandate.core.game.GameSetup.Mode.CAMPAIGN_ONLY) {
            // Campaign Only ends here: the campaign's assessment, a replay, or the menu
            var a = g.campaignAssessment().orElseThrow();
            c.with(Text.eyebrow("The campaign: " + a.grade()));
            for (String s : a.lines()) c.with(Text.body("• " + s));
            c.with(Layouts.wrap(t.u(2), t.u(1),
                    Btn.primary("Replay the campaign in the same world", e -> { seen.run(); app.stage.closeModal(); app.session.restartSameSeed(); app.startedGame(); })
                            .tip("The same opponent, polling miss and events: try a different campaign"),
                    Btn.secondary("New game", e -> { seen.run(); app.stage.closeModal(); app.showNewGame(); }),
                    Btn.quiet("Main menu", e -> { seen.run(); app.stage.closeModal(); app.showMenu(); })));
        } else if (won) {
            c.with(Text.body(g.congress() + ". " + (g.unifiedGovernment() ? "Your party holds both chambers." : "Divided government.")));
            c.with(Layouts.wrap(t.u(2), t.u(1), Btn.primary("On to the transition", e -> { seen.run(); app.stage.closeModal(); app.open(App.ScreenId.OVAL); })
                    .tip("Choose your agenda before Inauguration Day")));
        } else {
            c.with(Text.eyebrow("What happened"));
            for (String s : g.postMortem()) c.with(Text.body("• " + s));
            c.with(Layouts.wrap(t.u(2), t.u(1),
                    Btn.primary("Replay the campaign in the same world", e -> { app.stage.closeModal(); app.session.restartSameSeed(); app.startedGame(); })
                            .tip("The same opponent, polling miss and events: try a different campaign"),
                    Btn.secondary("See the results", e -> { seen.run(); app.stage.closeModal(); app.open(App.ScreenId.RESULTS); }),
                    Btn.secondary("New game", e -> { seen.run(); app.stage.closeModal(); app.showNewGame(); }),
                    Btn.quiet("Main menu", e -> { seen.run(); app.stage.closeModal(); app.showMenu(); })));
        }
        app.stage.showModal(c, t.u(190), true, null);
    }

    public static El build(App app) {
        GameEngine g = app.session.g();
        if (g.dayHundredReport().isPresent()) return report(app, g, g.dayHundredReport().get());
        if (g.result2028().isEmpty()) return Parts.page("Results", "Nothing to report yet.", Parts.empty("Results", "Results appear on election night."));
        ElectionResult r = g.result2028().get();
        boolean won = r.winner() == g.player().party();
        Card res = new Card("The 2028 election").with(Text.body(Views.resultLine(r)), new Widgets.RaceBar(r.demElectoralVotes(), 0, r.repElectoralVotes(),
                g.side(Party.DEMOCRAT).candidate().name() + " (D)", g.side(Party.REPUBLICAN).candidate().name() + " (R)"), Views.map(g, Theme.get().u(80), null, null));
        if (won) return Parts.page("Results", "You won the presidency.", res);
        Card pm = new Card("What happened");
        for (String s : g.postMortem()) pm.with(Text.body("• " + s));
        pm.with(endButtons(app, g, false));
        return Parts.page("Results", "You lost the 2028 election.", Parts.row(Parts.w(pm, 1), Parts.w(res, 1.3)));
    }

    static El report(App app, GameEngine g, DayHundredReport r) {
        Theme t = Theme.get();
        Color tone = switch (r.outcome()) { case VICTORY -> t.good; case PARTIAL -> t.warn; case FAILURE -> t.bad; };
        Card banner = new Card(null).stripe(tone).stroke(tone);
        banner.with(new Text("DAY 100", t.bold(Theme.Size.CAPTION), t.accent), Text.display(r.outcome().label, Theme.Size.HERO).color(tone),
                Text.strong(r.score() + " of 10.  " + r.headline(), Theme.Size.LEAD), Text.faint(DayHundredReport.RULE));
        Paper paper = new Paper("For the record", "The first hundred days", g.player().candidate().name() + ", " + game.mandate.web.Notable.date(g.calendar().inauguration())
                + " to day 100");
        for (DayHundredReport.Goal goal : r.goals()) {
            String mark = switch (goal.status()) { case MET -> "Met"; case PARTLY -> "Partly met"; case MISSED -> "Missed"; };
            Color c = switch (goal.status()) { case MET -> Theme.rgb(0x2E7D4F); case PARTLY -> Theme.rgb(0xB7791F); case MISSED -> t.paperRule; };
            paper.item(goal.name() + " - " + mark + ". " + goal.why(), c);
        }
        List<Double> ap = r.approvalByWeek();
        List<String> weeks = IntStream.range(0, ap.size()).mapToObj(i -> i == 0 ? "Oath" : "Week " + i).toList();
        LineChart chart = new LineChart(weeks, v -> Fmt.format("%.0f%%", v), 6, t.u(48)).add("Approval", t.accent, ap).reference(r.startApproval(), "Where you started");
        Card trend = new Card("Approval, week by week").with(Layouts.fill(chart));
        Card decisions = new Card("The decisions that shaped it");
        List<String> ds = r.decisions();
        for (String s : ds.subList(Math.max(0, ds.size() - 14), ds.size())) decisions.with(Text.muted(Decisions.strip(s)));
        Card next = new Card("Play it differently").with(endButtons(app, g, true));
        return Parts.page("The 100-day report", "How your first hundred days went, goal by goal, and why.", banner,
                Parts.row(Parts.w(paper, 1.4), Parts.w(Layouts.stack(t.u(4), trend, next), 1)), decisions);
    }

    static El endButtons(App app, GameEngine g, boolean governed) {
        Theme t = Theme.get();
        List<El> bs = new ArrayList<>();
        // the hundred days are the presidency's opening chapter: after the report, it goes on
        boolean goesOn = g.phase() == game.mandate.core.time.Phase.GOVERNING;
        if (goesOn) bs.add(Btn.primary("Keep governing", e -> app.open(App.ScreenId.OVAL)).tip("The presidency goes on: the Hill, the West Wing and the world are open"));
        if (governed) bs.add(new Btn("Govern the hundred days again", goesOn ? Btn.Kind.SECONDARY : Btn.Kind.PRIMARY, e -> { app.session.replayHundredDays(); app.startedGame(); })
                .tip("Back to Inauguration Day: the same election, Congress and mandate"));
        Btn.Kind replayKind = governed || goesOn ? Btn.Kind.SECONDARY : Btn.Kind.PRIMARY;
        bs.add(new Btn("Replay the campaign in the same world", replayKind, e -> { app.session.restartSameSeed(); app.startedGame(); })
                .tip("The same opponent, polling miss and events: try a different campaign"));
        bs.add(Btn.secondary("New game", e -> app.showNewGame()));
        bs.add(Btn.quiet("Main menu", e -> app.showMenu()));
        return Layouts.wrap(t.u(2), t.u(2), bs.toArray(El[]::new));
    }

}
