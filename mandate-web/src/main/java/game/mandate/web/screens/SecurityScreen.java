package game.mandate.web.screens;

import game.mandate.core.data.Fmt;

import game.mandate.web.kit.El;

import game.mandate.core.diplomacy.Country;
import game.mandate.core.diplomacy.HotSpot;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.executive.Post;
import game.mandate.core.opening.Decision;
import game.mandate.core.opening.HundredDays;
import game.mandate.core.opening.Types.CrisisOutcome;
import game.mandate.core.time.Phase;
import game.mandate.web.App;
import game.mandate.web.Glossary;
import game.mandate.web.kit.Btn;
import game.mandate.web.kit.Card;
import game.mandate.web.kit.Layouts;
import game.mandate.web.kit.Text;
import game.mandate.web.kit.Theme;
import game.mandate.web.kit.Widgets;

import java.util.Arrays;
import java.util.List;

/**
 * Diplomacy and national security, in First Island Chain's idiom: a one-word status in its colour, the crisis ladder,
 * the intelligence estimate drawn as a range (never the hidden truth), deterrence, the flashpoints' tension and
 * relations as diverging bars.
 */
public final class SecurityScreen {
    private SecurityScreen() {}

    public static El build(App app) {
        GameEngine g = app.session.g();
        Theme t = Theme.get();
        String[] s = Views.status(g);
        Card status = new Card("Status");
        status.with(new Text(s[0], t.font(Theme.Face.MONO_BOLD, Theme.Size.HERO), Views.statusColor(s[0])), Text.body(s[1]));
        status.with(new Widgets.Meter("Deterrence", g.diplomacy().deterrence(), 100, Fmt.format("%.0f of 100", g.diplomacy().deterrence()), t.info, Glossary.of("Deterrence")));
        return Parts.page("National security", "Crises, deterrence and America's relations.",
                Parts.row(Parts.w(status, 1), Parts.w(crisis(app, g), 1.6)),
                Parts.row(Parts.w(flashpoints(g), 1), Parts.w(relations(g), 1.4)));
    }

    static Card crisis(App app, GameEngine g) {
        Theme t = Theme.get();
        Card c = new Card("The crisis");
        if (g.phase() != Phase.FIRST_100_DAYS) {
            String ladder = g.diplomacy().rung() == null ? "No rung of the Taiwan ladder has been reached." : "The Taiwan ladder stands at " + g.diplomacy().rung().name().toLowerCase() + ".";
            return c.with(Text.muted(Parts.campaign(g) ? "As president you will face crises. For now, the world's flashpoints are below." : ladder));
        }
        HundredDays h = g.hundredDays().orElseThrow();
        if (h.crisis() == CrisisOutcome.NONE) return c.with(Text.muted("No crisis yet. One is likely in your first weeks; an able Secretary of State and Defense secretary will matter when it comes."));
        String[] rungs = h.crisisKind().rungs;
        int at = h.crisis() == CrisisOutcome.CLASH ? rungs.length - 1 : Math.min(h.escalation(), rungs.length - 1);
        c.with(Text.display(h.crisisKind().label, Theme.Size.TITLE));
        c.with(new Widgets.Stages(Arrays.asList(rungs), at, h.crisis() == CrisisOutcome.CLASH, h.crisis() == CrisisOutcome.ACTIVE ? t.warn : t.good));
        c.with(Text.body(switch (h.crisis()) {
            case ACTIVE -> "The crisis is live: each week brings a decision.";
            case BACKED_DOWN -> "The other side backed down.";
            case DEAL -> "Diplomacy ended it with a deal.";
            case STALEMATE -> "It settled into a tense stalemate.";
            case CLASH -> "It ended in an armed clash.";
            default -> "";
        }));
        if (!Double.isNaN(h.intelEstimate())) {
            c.with(new Estimate(h.intelEstimate(), h.intelNoise()));
            c.with(Text.faint(Glossary.of("Intelligence estimate")));
        }
        c.with(Text.muted(Fmt.format("Defense secretary competence %.0f; Secretary of State %.0f.", h.competence(Post.DEFENSE), h.competence(Post.STATE))));
        boolean waiting = h.pending().stream().anyMatch(d -> d.kind() == Decision.Kind.CRISIS || d.kind() == Decision.Kind.AFTERMATH);
        if (waiting) c.with(Btn.danger("Decide this week's move", e -> Decisions.open(app)).hint("D"));
        return c;
    }

    /** The intelligence estimate as a point and a range on a 0-100% resolve scale. */
    static final class Estimate extends El {
        Estimate(double value, double noise) {
            super("div");
            Theme t = Theme.get();
            cls("estimate");
            add(Text.strong(Fmt.format("Their resolve, as intelligence reads it: %.0f%%  (\u00b1 %.0f)", 100 * value, 100 * noise), Theme.Size.SMALL));
            El track = El.div("estimate-track").css("background", "linear-gradient(to right, " + t.good.css() + ", " + t.bad.css() + ")");
            double lo = Math.max(0, value - noise), hi = Math.min(1, value + noise);
            track.add(El.div("estimate-band").css("left", pct(lo)).css("width", pct(hi - lo)), El.div("estimate-point").css("left", pct(value)));
            add(track, Layouts.side(0, Text.faint("Low resolve: force deters"), null, Text.faint("High resolve: force provokes")));
            role("img").label(Fmt.format("Intelligence estimate of their resolve: %.0f%%, plus or minus %.0f", 100 * value, 100 * noise));
            tip("Their resolve as intelligence reads it. The true value is hidden; the band is the estimate's typical error.");
        }

        static String pct(double f) { return (Math.round(Math.max(0, Math.min(1, f)) * 10000) / 100.0) + "%"; }
    }

    static Card flashpoints(GameEngine g) {
        Theme t = Theme.get();
        Card c = new Card("Flashpoints: tension, 0 to 100");
        for (HotSpot h : HotSpot.values()) {
            double v = g.diplomacy().tension(h);
            c.with(new Widgets.Meter(h.label(), v, 100, Fmt.format("%.0f", v), v > 65 ? t.bad : v > 45 ? t.warn : t.good,
                    "Tension in the " + h.label() + ". Above 65 crises become likely."));
        }
        return c;
    }

    static Card relations(GameEngine g) {
        Card c = new Card("Relations, -100 to +100");
        List<Country> cs = g.countries();
        for (Country k : cs) {
            double v = g.diplomacy().relation(k.id());
            String word = v >= 60 ? "Ally" : v >= 25 ? "Friendly" : v > -25 ? "Neutral" : v > -60 ? "Rival" : "Hostile";
            c.with(new Widgets.Diverging(k.name(), v, word, k.name() + ": " + word + Fmt.format(" (%+.0f)", v)));
        }
        return c;
    }

    static El spacer() { return Layouts.stack(0); }
}
