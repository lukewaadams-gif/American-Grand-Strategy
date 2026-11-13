package game.mandate.ui.kit;

import game.mandate.core.state.Party;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * The design system's tokens: colour, type, spacing and scale. Every component reads them from here, so the whole
 * interface changes together when the player picks a larger text size, high contrast or a colour-blind-safe palette.
 *
 * <p>Heritage: the deep-navy "situation room", the uppercase KPI labels and the gold primary action come from
 * Battleground 2028 and Resolute Desk; the serif display face from Resolute Desk; the monospaced status lines and
 * threat colours of the security screens from First Island Chain. Colour pairs are chosen to meet WCAG 2.1 AA
 * contrast (4.5:1 for text), checked by {@code ThemeTest}.
 */
public final class Theme {

    public enum Palette { STANDARD, HIGH_CONTRAST, COLOUR_BLIND_SAFE }

    /** Named text sizes, in points at 100% scale. The smallest is 12. */
    public enum Size {
        CAPTION(12), SMALL(13), BODY(14), LEAD(16), TITLE(20), DISPLAY(32), KPI(24), HERO(56);
        public final int points;
        Size(int points) { this.points = points; }
    }

    public enum Face { UI, UI_BOLD, DISPLAY, MONO, MONO_BOLD, SERIF, SERIF_BOLD, SERIF_ITALIC }

    private static Theme current = new Theme(Palette.STANDARD, 1.0);

    public static Theme get() { return current; }

    public static void set(Palette p, double scale) { current = new Theme(p, scale); }

    public final Palette palette;
    public final double scale;

    // surfaces
    public final Color bg, surface, raised, sunken, border, borderStrong;
    // text
    public final Color text, muted, faint;
    // roles
    public final Color accent, onAccent, focus, info, good, warn, bad;
    public final Color dem, rep, tossUp;
    // paper documents (the daily brief, the report)
    public final Color paper, paperText, paperMuted, paperRule;

    private Theme(Palette p, double scale) {
        this.palette = p;
        this.scale = scale;
        boolean hc = p == Palette.HIGH_CONTRAST;
        bg = hc ? rgb(0x000000) : rgb(0x0A1020);
        surface = hc ? rgb(0x0B0B0F) : rgb(0x111A2C);
        raised = hc ? rgb(0x1A1A22) : rgb(0x1A263D);
        sunken = hc ? rgb(0x000000) : rgb(0x0D1526);
        border = hc ? rgb(0x8C96A8) : rgb(0x2A3956);
        borderStrong = hc ? rgb(0xFFFFFF) : rgb(0x40537A);
        text = hc ? rgb(0xFFFFFF) : rgb(0xEAF0F8);
        muted = hc ? rgb(0xE6EBF2) : rgb(0xB3BED1);
        faint = hc ? rgb(0xCCD3DE) : rgb(0x8E9AB2);
        accent = rgb(0xF2B84B);
        onAccent = rgb(0x1B1406);
        focus = hc ? rgb(0xFFFF66) : rgb(0x7FB2FF);
        info = rgb(0x7FB2FF);
        boolean cb = p == Palette.COLOUR_BLIND_SAFE;
        good = cb ? rgb(0x5FB4FF) : rgb(0x45D68F);
        warn = rgb(0xF5B547);
        bad = cb ? rgb(0xFF9F45) : rgb(0xFF6F6F);
        dem = cb ? rgb(0x5FA0FF) : rgb(0x5596FF);
        rep = cb ? rgb(0xFF9F45) : rgb(0xFF6262);
        tossUp = rgb(0x9A8FB8);
        paper = rgb(0xF2EDDF);
        paperText = rgb(0x1F1B14);
        paperMuted = rgb(0x5C5548);
        paperRule = rgb(0x8E2F2A);
    }

    public Color party(Party p) { return p == Party.DEMOCRAT ? dem : rep; }

    /** A colour for a margin (D minus R, in points), fading to the toss-up colour near zero. */
    public Color margin(double dMinusR) {
        double t = Math.min(1, Math.abs(dMinusR) / 15.0);
        Color end = dMinusR >= 0 ? dem : rep;
        Color base = mix(surface, tossUp, 0.55);
        return legible(mix(base, end, 0.25 + 0.75 * Math.sqrt(t)));
    }

    /**
     * A fill dark enough (or light enough) for a label on it to reach 4.5:1. Mid-tone fills, where neither light nor
     * dark text is readable, are deepened toward the background a step at a time; other fills are unchanged.
     */
    public Color legible(Color fill) {
        Color c = fill;
        for (int i = 0; i < 20 && Math.max(contrast(c, text), contrast(c, paperText)) < 4.6; i++) c = mix(c, bg, 0.06);
        return c;
    }

    /**
     * The opaque fill of a status pill in a tone. Pill text is always {@link #text}: the word carries the meaning and
     * the tint and outline carry the tone, so the text reads at 4.5:1 on any background the pill sits on (tested).
     */
    public Color pillFill(Color tone) { return mix(sunken, tone, 0.24); }

    /** Status colour for a 0..1 level where higher is better. */
    public Color level(double v) { return v >= 0.6 ? good : v >= 0.35 ? warn : bad; }

    // ---- type --------------------------------------------------------------------------------------------

    private static final Map<Face, Font> BASE = new EnumMap<>(Face.class);

    static {
        load(Face.UI, "Inter-Regular.ttf", "SansSerif", Font.PLAIN);
        load(Face.UI_BOLD, "Inter-Bold.ttf", "SansSerif", Font.BOLD);
        load(Face.DISPLAY, "PlayfairDisplay-Bold.ttf", "Serif", Font.BOLD);
        load(Face.MONO, "JetBrainsMono-Regular.ttf", "Monospaced", Font.PLAIN);
        load(Face.MONO_BOLD, "JetBrainsMono-Bold.ttf", "Monospaced", Font.BOLD);
        load(Face.SERIF, "SourceSerif4-Regular.ttf", "Serif", Font.PLAIN);
        load(Face.SERIF_BOLD, "SourceSerif4-Bold.ttf", "Serif", Font.BOLD);
        load(Face.SERIF_ITALIC, "SourceSerif4-Italic.ttf", "Serif", Font.ITALIC);
    }

    private static void load(Face face, String file, String fallback, int style) {
        try (InputStream in = Theme.class.getResourceAsStream("/fonts/" + file)) {
            if (in == null) throw new IOException("missing " + file);
            Font f = Font.createFont(Font.TRUETYPE_FONT, in);
            if (!GraphicsEnvironment.isHeadless()) GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
            BASE.put(face, f);
        } catch (IOException | FontFormatException e) {
            BASE.put(face, new Font(fallback, style, 14));
        }
    }

    public Font font(Face face, Size size) { return font(face, size.points); }

    public Font font(Face face, float points) { return BASE.get(face).deriveFont((float) (points * scale)); }

    public Font ui(Size s) { return font(Face.UI, s); }
    public Font bold(Size s) { return font(Face.UI_BOLD, s); }
    public Font mono(Size s) { return font(Face.MONO, s); }

    // ---- spacing -------------------------------------------------------------------------------------------

    /** Spacing on a 4-point grid, scaled: {@code u(4)} is 16 px at 100%. */
    public int u(int units) { return (int) Math.round(units * 4 * scale); }

    public int px(double points) { return (int) Math.round(points * scale); }

    public int radius() { return px(8); }

    // ---- colour helpers ------------------------------------------------------------------------------------

    public static Color rgb(int v) { return new Color(v); }

    public static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    public static Color alpha(Color c, double a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(255 * a)); }

    /** WCAG relative luminance. */
    public static double luminance(Color c) {
        double[] v = {c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0};
        for (int i = 0; i < 3; i++) v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }

    /** WCAG contrast ratio, 1..21. */
    public static double contrast(Color a, Color b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** Readable text colour for a filled background (map tiles, tags). */
    public Color textOn(Color fill) { return contrast(fill, text) >= contrast(fill, paperText) ? text : paperText; }
}
