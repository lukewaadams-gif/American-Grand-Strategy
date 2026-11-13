package game.mandate.web.screens;

import game.mandate.core.data.Fmt;

import game.mandate.web.kit.El;

import game.mandate.core.advisor.Advisor;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.state.Party;
import game.mandate.web.App;
import game.mandate.web.Glossary;
import game.mandate.web.kit.Card;
import game.mandate.web.kit.Layouts;
import game.mandate.web.kit.Text;
import game.mandate.web.kit.Theme;
import game.mandate.web.kit.Widgets;

import game.mandate.web.kit.Color;
import java.util.List;

/** Pieces every screen uses: the page header, KPI rows, empty states, and the advisor's colours. */
public final class Parts {
    private Parts() {}

    /** A screen page: a serif title, a one-line purpose, then the content, in a page that scrolls. */
    public static El page(String title, String subtitle, El... content) {
        Theme t = Theme.get();
        El p = Layouts.page(t.u(5)).css("padding", t.u(6) + "px " + t.u(8) + "px " + t.u(10) + "px " + t.u(8) + "px");
        El head = Layouts.stack(t.u(1), Text.display(title, Theme.Size.DISPLAY).heading(1), Text.muted(subtitle));
        p.add(head);
        for (El c : content) p.add(c);
        return p;
    }

    /** A row of cards that becomes a stack on narrow windows. */
    public static El row(El... cs) { Theme t = Theme.get(); return Layouts.row(t.u(4), t.u(180), cs); }

    public static El row(int collapseBelow, El... cs) { Theme t = Theme.get(); return Layouts.row(t.u(4), collapseBelow, cs); }

    public static <T extends El> T w(T c, double weight) { return Layouts.weight(c, weight); }

    /** KPIs side by side, wrapping onto new lines when they do not fit. */
    public static El kpis(El... ks) { Theme t = Theme.get(); return Layouts.wrapTop(t.u(7), t.u(3), ks); }

    public static Widgets.Kpi kpi(String label, String value, String sub, Color subColor, String term) {
        return new Widgets.Kpi(label, value, sub, subColor, term == null ? null : Glossary.of(term));
    }

    public static Card empty(String eyebrow, String message) {
        return new Card(eyebrow).with(Text.muted(message));
    }

    public static Color tone(Advisor.Tone tone) {
        Theme t = Theme.get();
        return switch (tone) { case URGENT -> t.bad; case WARNING -> t.warn; case OPPORTUNITY -> t.good; case INFO -> t.info; };
    }

    public static String toneWord(Advisor.Tone tone) {
        return switch (tone) { case URGENT -> "Now"; case WARNING -> "Watch"; case OPPORTUNITY -> "Opening"; case INFO -> "Note"; };
    }

    /** A probability as a player reads it: "About 40%", with the ends said plainly rather than as 0% or 100%. */
    public static String chance(double p) {
        if (p < 0.005) return "Under 1%";
        if (p > 0.995) return "Over 99%";
        return Fmt.format("About %.0f%%", 100 * p);
    }

    /** A probability as a headline figure: "40%", or "<1%" and ">99%" at the ends, since nothing in politics is certain. */
    public static String pct(double p) {
        if (p < 0.005) return "<1%";
        if (p > 0.995) return ">99%";
        return Fmt.format("%.0f%%", 100 * p);
    }

    /** The screen where the player acts on a piece of advice (the agenda is set in the Oval Office before the oath). */
    public static App.ScreenId screenFor(Advisor.Topic topic, GameEngine g) {
        if (topic == Advisor.Topic.AGENDA && g.phase() == game.mandate.core.time.Phase.TRANSITION) return App.ScreenId.OVAL;
        return screenFor(topic);
    }

    public static App.ScreenId screenFor(Advisor.Topic topic) {
        return switch (topic) {
            case CAMPAIGN -> App.ScreenId.CAMPAIGN;
            case CONGRESS, AGENDA -> App.ScreenId.CONGRESS;
            case CABINET, POLITICS -> App.ScreenId.OVAL;
            case ECONOMY -> App.ScreenId.ECONOMY;
            case SECURITY -> App.ScreenId.SECURITY;
        };
    }

    public static String margin(double dMinusR) {
        if (Math.abs(dMinusR) < 0.05) return "Even";
        return (dMinusR > 0 ? "D+" : "R+") + Fmt.format("%.1f", Math.abs(dMinusR));
    }

    /** The colour of a change: good up, bad down, and neutral when it rounds to nothing (no green for "+0.0"). */
    public static Color change(double delta, double noise) {
        Theme t = Theme.get();
        return Math.abs(delta) < noise ? t.faint : delta > 0 ? t.good : t.bad;
    }

    public static String signed(double v, String fmt) { return (v >= 0 ? "+" : "") + Fmt.format(fmt, v); }

    public static String partyName(Party p) { return p == Party.DEMOCRAT ? "Democrat" : "Republican"; }

    /** A short line of a party's colour and code, for labels: "D" in blue. */
    public static Text partyCode(Party p) { Theme t = Theme.get(); return new Text(p.code(), t.bold(Theme.Size.SMALL), t.party(p)); }

    public static boolean campaign(GameEngine g) {
        return g.phase() == game.mandate.core.time.Phase.GENERAL_CAMPAIGN_2028 || g.phase() == game.mandate.core.time.Phase.REELECTION_CAMPAIGN_2032;
    }

    /** Lines from the hundred days' log for the current week, without the "Week n:" prefix. */
    public static List<String> thisWeek(game.mandate.core.opening.HundredDays h) {
        String prefix = "Week " + h.week() + ": ";
        return h.log().stream().filter(l -> l.startsWith(prefix)).map(l -> l.substring(prefix.length())).toList();
    }
}
