package game.mandate.ui.screens;

import game.mandate.core.economy.EconomyState;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.engine.IndicatorPoint;
import game.mandate.core.opening.HundredDays;
import game.mandate.core.opening.OpeningAction;
import game.mandate.core.time.Phase;
import game.mandate.ui.App;
import game.mandate.ui.Glossary;
import game.mandate.ui.Notable;
import game.mandate.ui.kit.Btn;
import game.mandate.ui.kit.Card;
import game.mandate.ui.kit.Layouts;
import game.mandate.ui.kit.LineChart;
import game.mandate.ui.kit.Text;
import game.mandate.ui.kit.Theme;
import game.mandate.ui.kit.Widgets;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * The economy and the budget: headline indicators, charts that fit their data (with a reference line, so flat reads
 * as flat), the economic challenge and how it is going, the Fed, and the budget. Market internals (bond yields,
 * Fed credibility, confidence) are shown at the Standard and Expert levels.
 */
public final class EconomyScreen {
    private EconomyScreen() {}

    public static JComponent build(App app) {
        GameEngine g = app.session.g();
        Theme t = Theme.get();
        EconomyState e = g.state().economy();
        boolean governing = g.state().politics().governing(), guided = app.session.settings.guided();
        List<JComponent> kpis = new ArrayList<>(List.of(
                Parts.kpi("Growth", String.format("%.1f%%", e.growth()), "annualised", null, null),
                Parts.kpi("Unemployment", String.format("%.1f%%", e.unemployment()), String.format("reported %.1f%%", e.reportedUnemployment()), null, "Misery index"),
                Parts.kpi("Inflation", String.format("%.1f%%", e.inflation()), String.format("reported %.1f%%", e.reportedInflation()), null, "Misery index"),
                Parts.kpi("Gas", String.format("$%.2f", g.gas()), String.format("oil $%.0f", e.oil()), null, null),
                Parts.kpi("Fed funds", String.format("%.2f%%", e.fedRate()), null, null, null),
                Parts.kpi("Deficit", String.format("%.1f%%", g.deficit()), "of GDP", null, "Deficit")));
        if (!guided) {
            kpis.add(Parts.kpi("10-year", String.format("%.2f%%", e.tenYear()), String.format("mortgages %.2f%%", g.mortgageRate()), null, "Deficit"));
            kpis.add(Parts.kpi("Confidence", String.format("%.0f", e.confidence()), "consumer mood", null, null));
        }
        if (app.session.settings.expert()) {
            kpis.add(Parts.kpi("Fed credibility", String.format("%.0f", e.credibility()), "markets' trust", null, null));
            kpis.add(Parts.kpi("Output gap", Parts.signed(e.outputGap(), "%.1f"), "% of potential", null, null));
            kpis.add(Parts.kpi("Debt", String.format("%.0f%%", g.state().budget().debt()), "of GDP", null, null));
        }
        Card head = new Card(governing ? "Headline indicators" : "The economy you will inherit").with(Parts.kpis(kpis.toArray(JComponent[]::new)));
        List<IndicatorPoint> ind = g.indicators();
        List<String> x = ind.stream().map(p -> Notable.date(p.date()).replaceAll(", \\d{4}", "")).toList();
        String waitText = "Charts begin on Inauguration Day: the economy holds still during the campaign.";
        LineChart jobs = new LineChart(x, v -> String.format("%.1f%%", v), 1.0, t.u(56)).add("Unemployment", t.info, ind.stream().map(IndicatorPoint::unemployment).toList())
                .add("Inflation", t.warn, ind.stream().map(IndicatorPoint::inflation).toList()).reference(2.0, "2% inflation target").emptyText(waitText);
        LineChart gas = new LineChart(x, v -> String.format("$%.2f", v), 0.4, t.u(40)).add("Gas", t.accent, ind.stream().map(IndicatorPoint::gas).toList()).emptyText(waitText);
        LineChart rates = new LineChart(x, v -> String.format("%.2f%%", v), 0.5, t.u(40)).add("10-year", t.info, ind.stream().map(IndicatorPoint::tenYear).toList())
                .add("Fed funds", t.muted, ind.stream().map(IndicatorPoint::fedRate).toList()).emptyText(waitText);
        Card chart = new Card("Jobs and prices").with(Layouts.fill(jobs));
        Card side = new Card("Gas at the pump").with(Layouts.fill(gas));
        List<JComponent> rows = new ArrayList<>(List.of(head, Parts.row(Parts.w(chart, 1.6), Parts.w(side, 1))));
        rows.add(Parts.row(challenge(app, g), fed(app, g), budget(g)));
        if (!guided) rows.add(new Card("Interest rates").with(Layouts.fill(rates)));
        return Parts.page("The economy", governing ? "Jobs, prices, markets and the budget, week by week." : "Before the inauguration the economy holds still; it starts moving on day one.",
                rows.toArray(JComponent[]::new));
    }

    static Card challenge(App app, GameEngine g) {
        Theme t = Theme.get();
        Card c = new Card("The economic challenge");
        if (g.phase() != Phase.FIRST_100_DAYS) return c.with(Text.muted(g.phase() == Phase.GOVERNING || g.over()
                ? g.dayHundredReport().map(r -> r.goals().get(2).why()).orElse("No challenge outstanding.") : "In week 5 of your first hundred days, an economic shock arrives."));
        HundredDays h = g.hundredDays().orElseThrow();
        if (h.challenge() == null || h.week() < 5) return c.with(Text.muted("In week 5 an economic shock arrives: an inflation surge or a hiring slump. Your answer will be judged at day 100."));
        c.with(Text.strong(h.challenge().title, Theme.Size.LEAD), Text.muted(h.challenge().situation));
        if (h.challengeResponse() < 0) {
            c.with(new Widgets.Tag("AWAITING YOUR ANSWER", t.warn), Btn.secondary("Answer it", e -> Decisions.open(app)).hint("Ctrl+D"));
        } else c.with(new Widgets.Tag("ANSWERED", t.good), Text.faint("The day-100 report judges " + (h.challenge() == game.mandate.core.opening.Types.EconomicChallenge.INFLATION_SURGE
                ? "how far inflation has risen" : "how far unemployment has risen") + " since the shock, and what the answer cost in borrowing."));
        return c;
    }

    static Card fed(App app, GameEngine g) {
        Theme t = Theme.get();
        EconomyState e = g.state().economy();
        Card c = new Card("The Federal Reserve");
        c.with(Text.body(String.format("Target rate %.2f%%. %s", e.fedRate(), g.nextFedMeeting().map(d -> "Next meeting " + Notable.date(d) + ".").orElse(""))));
        c.with(Text.muted("The Fed responds to inflation, unemployment and the deficit. Leaning on it publicly may win a cut but costs its credibility, which pushes long rates up."));
        if (g.phase() == Phase.FIRST_100_DAYS) {
            HundredDays h = g.hundredDays().orElseThrow();
            Btn lean = Btn.secondary("Lean on the Fed", ev -> app.session.act(new OpeningAction.LeanOnFed()))
                    .disabledBecause(h.attention() < 1 ? "No attention left this week" : e.fedPressured() ? "You have already leaned on the Fed before this meeting" : null);
            if (lean.isEnabled()) lean.tip("Call publicly for rate cuts (1 attention)");
            c.with(lean);
        }
        return c;
    }

    static Card budget(GameEngine g) {
        Card c = new Card("The budget");
        c.setToolTipText(Glossary.of("Deficit"));
        c.with(Text.body(String.format("Deficit %.1f%% of GDP; debt %.0f%% of GDP.", g.deficit(), g.state().budget().debt())));
        if (g.phase() == Phase.FIRST_100_DAYS) g.hundredDays().ifPresent(h -> {
            if (h.live()) c.with(Text.muted(String.format("Your flagship would add %+.2f%% of GDP if enacted; bond markets already price in part of it.", h.billDeficit())));
        });
        return c;
    }
}
