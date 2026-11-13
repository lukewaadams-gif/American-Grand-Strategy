package game.mandate.web.screens;

import game.mandate.core.data.Fmt;

import game.mandate.web.kit.El;

import game.mandate.core.campaign.PollPoint;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.opening.Briefing;
import game.mandate.core.opening.HundredDays;
import game.mandate.web.App;
import game.mandate.web.Glossary;
import game.mandate.web.kit.Btn;
import game.mandate.web.kit.Card;
import game.mandate.web.kit.Layouts;
import game.mandate.web.kit.Text;
import game.mandate.web.kit.Theme;

import java.util.List;
import java.util.Map;

/** The national overview: where the race or the country stands, what changed this week, and where to go next. */
public final class Overview {
    private Overview() {}

    public static El build(App app) {
        GameEngine g = app.session.g();
        Theme t = Theme.get();
        Map<String, Double> m = Views.mapMargins(g);
        Card mapCard = new Card(Views.mapWhat(g)).with(Views.map(g, t.u(92), null, null));
        if (Parts.campaign(g)) mapCard.with(Views.raceBar(g, m));
        Card trend = new Card(Parts.campaign(g) ? "The race, week by week" : "Approval, turn by turn").with(Layouts.fill(Views.trend(g, t.u(60))));
        return Parts.page("The nation", subtitle(g),
                Parts.row(Parts.w(mapCard, 1.6), Parts.w(thisWeek(app, g), 1)),
                Parts.row(Parts.w(trend, 1.3), Parts.w(Views.congressCard(g), 1), Parts.w(Views.statusCard(app, g), 1)),
                Parts.row(economy(app, g), next(app, g)));
    }

    static String subtitle(GameEngine g) {
        return switch (g.phase()) {
            case GENERAL_CAMPAIGN_2028, REELECTION_CAMPAIGN_2032 -> "The race for the White House: the map, the trend and the week's story.";
            case TRANSITION -> "You won. Before the oath: your agenda.";
            case FIRST_100_DAYS -> "Your first hundred days: the country, Congress and the world at a glance.";
            case GOVERNING -> "Your presidency at a glance.";
            default -> "The game is over: see Results.";
        };
    }

    static Card thisWeek(App app, GameEngine g) {
        Card c = new Card("The latest");
        if (Parts.campaign(g)) {
            List<PollPoint> ps = g.polls();
            if (ps.size() < 2) c.with(Text.muted("The campaign has just begun. Set your standing orders in Campaign HQ, then run the first week."));
            else {
                PollPoint p = ps.get(ps.size() - 1);
                c.with(Text.strong(Fmt.format("You are %s by %.1f; %d electoral votes on the polls.", p.national() >= 0 ? "ahead" : "behind", Math.abs(p.national()),
                        p.electoralVotes()), Theme.Size.LEAD));
                c.with(Text.eyebrow("What moved the race"));
                for (String s : p.movers().subList(0, Math.min(4, p.movers().size()))) c.with(Text.body("• " + s));
            }
            c.with(Btn.quiet("Open Campaign HQ", e -> app.open(App.ScreenId.CAMPAIGN)).tip("Polls, orders, promises (2)"));
            return c;
        }
        if (g.phase() == game.mandate.core.time.Phase.FIRST_100_DAYS) {
            HundredDays h = g.hundredDays().orElseThrow();
            List<Briefing> bs = h.briefings();
            if (bs.isEmpty()) c.with(Text.muted("Week 1. Nominate your cabinet and draft your flagship bill; the first briefing arrives when the week ends."));
            else {
                Briefing b = bs.get(bs.size() - 1);
                c.with(Text.strong("Week " + b.week() + ": " + b.headline(), Theme.Size.LEAD));
                for (String s : b.consequences().subList(0, Math.min(4, b.consequences().size()))) c.with(Text.body("• " + s));
                for (String s : b.reactions().subList(0, Math.min(2, b.reactions().size()))) c.with(Text.muted("› " + s));
            }
            List<String> now = Parts.thisWeek(h);
            if (!now.isEmpty()) {
                c.with(Text.eyebrow("So far this week"));
                for (String s : now.subList(Math.max(0, now.size() - 3), now.size())) c.with(Text.body("• " + s));
            }
            c.with(Btn.quiet("Open the Oval Office", e -> app.open(App.ScreenId.OVAL)).tip("Your desk (3)"));
            return c;
        }
        if (g.result2028().isPresent()) c.with(Text.body(Views.resultLine(g.result2028().get())));
        if (g.phase() == game.mandate.core.time.Phase.TRANSITION) {
            c.with(Text.muted("Choose up to three priorities in the Oval Office, then take the oath."));
            c.with(Btn.quiet("Open the Oval Office", e -> app.open(App.ScreenId.OVAL)).tip("Set your agenda (3)"));
        }
        return c;
    }

    static Card economy(App app, GameEngine g) {
        Theme t = Theme.get();
        var e = g.state().economy();
        Card c = new Card(g.state().politics().governing() ? "The economy" : "The economy you will inherit");
        c.with(Parts.kpis(Parts.kpi("Unemployment", Fmt.format("%.1f%%", e.unemployment()), null, null, null),
                Parts.kpi("Inflation", Fmt.format("%.1f%%", e.inflation()), null, null, null),
                Parts.kpi("Growth", Fmt.format("%.1f%%", e.growth()), null, null, null),
                Parts.kpi("Gas", Fmt.format("$%.2f", g.gas()), "a gallon", null, null),
                Parts.kpi("Deficit", Fmt.format("%.1f%%", g.deficit()), "of GDP", null, "Deficit")));
        c.with(Btn.quiet("Open Economy", ev -> app.open(App.ScreenId.ECONOMY)).tip("Jobs, prices, the Fed and the budget (5)"));
        return c;
    }

    static Card next(App app, GameEngine g) {
        Card c = new Card("Where to next");
        int waiting = Decisions.count(app);
        if (waiting > 0) c.with(Btn.danger(waiting + " decision" + (waiting == 1 ? "" : "s") + " waiting", e -> Decisions.open(app)).hint("D"));
        App.ScreenId home = app.defaultScreen();
        if (home != App.ScreenId.OVERVIEW) c.with(Btn.secondary("Go to " + home.label, e -> app.open(home)).hint(home.keyLabel()).tip(home.purpose));
        c.with(Btn.secondary("Timeline: what happened and why", e -> app.open(App.ScreenId.TIMELINE)).hint("7").tip(Glossary.of("Undo and rewind")));
        c.with(Text.faint("? lists every shortcut and explains each mechanic."));
        return c;
    }
}
