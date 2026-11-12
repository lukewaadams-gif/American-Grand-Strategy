package game.mandate.ui.screens;

import game.mandate.core.diplomacy.Country;
import game.mandate.core.diplomacy.HotSpot;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.executive.Post;
import game.mandate.core.opening.Decision;
import game.mandate.core.opening.HundredDays;
import game.mandate.core.opening.Types.CrisisOutcome;
import game.mandate.core.time.Phase;
import game.mandate.ui.App;
import game.mandate.ui.Glossary;
import game.mandate.ui.kit.Btn;
import game.mandate.ui.kit.Card;
import game.mandate.ui.kit.Layouts;
import game.mandate.ui.kit.Text;
import game.mandate.ui.kit.Theme;
import game.mandate.ui.kit.Widgets;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Arrays;
import java.util.List;

/**
 * Diplomacy and national security, in First Island Chain's idiom: a one-word status in its colour, the crisis ladder,
 * the intelligence estimate drawn as a range (never the hidden truth), deterrence, the flashpoints' tension and
 * relations as diverging bars.
 */
public final class SecurityScreen {
    private SecurityScreen() {}

    public static JComponent build(App app) {
        GameEngine g = app.session.g();
        Theme t = Theme.get();
        String[] s = Views.status(g);
        Card status = new Card("Status");
        status.with(new Text(s[0], t.font(Theme.Face.MONO_BOLD, Theme.Size.HERO), Views.statusColor(s[0])), Text.body(s[1]));
        status.with(new Widgets.Meter("Deterrence", g.diplomacy().deterrence(), 100, String.format("%.0f of 100", g.diplomacy().deterrence()), t.info, Glossary.of("Deterrence")));
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
        c.with(Text.muted(String.format("Defense secretary competence %.0f; Secretary of State %.0f.", h.competence(Post.DEFENSE), h.competence(Post.STATE))));
        boolean waiting = h.pending().stream().anyMatch(d -> d.kind() == Decision.Kind.CRISIS || d.kind() == Decision.Kind.AFTERMATH);
        if (waiting) c.with(Btn.danger("Decide this week's move", e -> Decisions.open(app)).hint("Ctrl+D"));
        return c;
    }

    /** The intelligence estimate as a point and a range on a 0-100% resolve scale. */
    static final class Estimate extends javax.swing.JPanel {
        final double value, noise;
        Estimate(double value, double noise) {
            setOpaque(false);
            this.value = value;
            this.noise = noise;
            getAccessibleContext().setAccessibleName(String.format("Intelligence estimate of their resolve: %.0f%%, plus or minus %.0f", 100 * value, 100 * noise));
            setToolTipText("Their resolve as intelligence reads it. The true value is hidden; the band is the estimate's typical error.");
        }

        @Override public Dimension getPreferredSize() { Theme t = Theme.get(); return new Dimension(t.u(60), t.u(14)); }

        @Override protected void paintComponent(Graphics g0) {
            Theme t = Theme.get();
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), y = t.u(6), h = t.px(10);
            g.setFont(t.bold(Theme.Size.SMALL));
            g.setColor(t.text);
            g.drawString(String.format("Their resolve, as intelligence reads it: %.0f%%  (± %.0f)", 100 * value, 100 * noise), 0, t.u(4));
            for (int i = 0; i < w; i++) { g.setColor(Theme.mix(t.good, t.bad, i / (double) w)); g.fillRect(i, y, 1, h); }
            int lo = (int) (w * Math.max(0, value - noise)), hi = (int) (w * Math.min(1, value + noise)), x = (int) (w * value);
            g.setColor(Theme.alpha(t.text, 0.35));
            g.fillRect(lo, y - t.px(3), hi - lo, h + t.px(6));
            g.setColor(t.text);
            g.fillRect(x - 1, y - t.px(5), 3, h + t.px(10));
            g.setFont(t.ui(Theme.Size.CAPTION));
            g.setColor(t.muted);
            g.drawString("Low resolve: force deters", 0, y + h + t.u(4));
            String r = "High resolve: force provokes";
            g.drawString(r, w - g.getFontMetrics().stringWidth(r), y + h + t.u(4));
            g.dispose();
        }
    }

    static Card flashpoints(GameEngine g) {
        Theme t = Theme.get();
        Card c = new Card("Flashpoints: tension, 0 to 100");
        for (HotSpot h : HotSpot.values()) {
            double v = g.diplomacy().tension(h);
            c.with(new Widgets.Meter(h.label(), v, 100, String.format("%.0f", v), v > 65 ? t.bad : v > 45 ? t.warn : t.good,
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
            c.with(new Widgets.Diverging(k.name(), v, word, k.name() + ": " + word + String.format(" (%+.0f)", v)));
        }
        return c;
    }

    static JComponent spacer() { return Layouts.stack(0); }
}
