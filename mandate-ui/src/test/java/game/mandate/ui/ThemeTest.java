package game.mandate.ui;

import game.mandate.ui.kit.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The palette meets WCAG 2.1 AA in every theme: 4.5:1 for text against every surface it is drawn on, 3:1 for
 * graphics a player must tell apart from their background (party colours, the focus ring). Numbers, not taste:
 * whether the palette is pleasant is listed for human review.
 */
class ThemeTest {
    @AfterEach void reset() { Theme.set(Theme.Palette.STANDARD, 1.0); }

    @Test void textMeetsAaOnEverySurfaceInEveryPalette() {
        List<String> failures = new ArrayList<>();
        for (Theme.Palette p : Theme.Palette.values()) {
            Theme.set(p, 1.0);
            Theme t = Theme.get();
            Map<String, Color> surfaces = Map.of("bg", t.bg, "surface", t.surface, "raised", t.raised, "sunken", t.sunken);
            Map<String, Color> texts = Map.of("text", t.text, "muted", t.muted, "faint", t.faint,
                    "info", t.info, "good", t.good, "warn", t.warn, "bad", t.bad, "accent", t.accent);
            for (var s : surfaces.entrySet())
                for (var x : texts.entrySet()) need(failures, p, x.getKey() + " on " + s.getKey(), x.getValue(), s.getValue(), 4.5);
            need(failures, p, "onAccent on accent (primary buttons)", t.onAccent, t.accent, 4.5);
            need(failures, p, "paperText on paper", t.paperText, t.paper, 4.5);
            need(failures, p, "paperMuted on paper", t.paperMuted, t.paper, 4.5);
            need(failures, p, "paperRule on paper (the eyebrow)", t.paperRule, t.paper, 4.5);
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test void graphicsMeetThreeToOne() {
        List<String> failures = new ArrayList<>();
        for (Theme.Palette p : Theme.Palette.values()) {
            Theme.set(p, 1.0);
            Theme t = Theme.get();
            for (Color s : List.of(t.bg, t.surface, t.sunken)) {
                need(failures, p, "dem against a surface", t.dem, s, 3);
                need(failures, p, "rep against a surface", t.rep, s, 3);
                need(failures, p, "focus ring against a surface", t.focus, s, 3);
            }
            need(failures, p, "dem against rep (adjacent map tiles)", t.dem, t.rep, 1.0);
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    /** Map tiles and tags pick their text colour from the fill; across the whole margin ramp it stays readable. */
    @Test void labelsOnFilledShapesStayReadable() {
        List<String> failures = new ArrayList<>();
        for (Theme.Palette p : Theme.Palette.values()) {
            Theme.set(p, 1.0);
            Theme t = Theme.get();
            for (double m = -40; m <= 40; m += 0.25) {
                Color fill = t.margin(m);   // the map's ramp, exactly as the tiles are filled
                need(failures, p, String.format("tile label on margin %+.2f", m), t.textOn(fill), fill, 4.5);
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures.subList(0, Math.min(20, failures.size()))));
    }

    @Test void noTypeSizeBelowTwelvePoints() {
        for (Theme.Size s : Theme.Size.values()) assertTrue(s.points >= 12, s + " is " + s.points + " pt");
    }

    @Test void scaleGrowsTypeAndSpacing() {
        Theme.set(Theme.Palette.STANDARD, 1.5);
        Theme t = Theme.get();
        assertTrue(t.ui(Theme.Size.BODY).getSize2D() >= 21 - 0.01);
        assertTrue(t.u(4) == 24);
    }

    static void need(List<String> failures, Theme.Palette p, String what, Color a, Color b, double min) {
        double c = Theme.contrast(a, b);
        if (c < min) failures.add(String.format("%s: %s is %.2f:1, needs %.1f:1", p, what, c, min));
    }
}
