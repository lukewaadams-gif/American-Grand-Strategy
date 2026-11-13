package game.mandate.web.kit;

import game.mandate.core.state.Party;

/**
 * The design tokens, the same values as the desktop's {@code game.mandate.ui.kit.Theme}: three palettes, the type
 * scale, the 4-px spacing grid, scaled 100-150%. The page's CSS reads them as custom properties ({@link #css()}), so
 * one Java object drives both the stylesheet and the colours code computes (the map's margin ramp).
 */
public final class Theme {
    public enum Palette { STANDARD, HIGH_CONTRAST, COLOUR_BLIND_SAFE }

    /** Type sizes in CSS pixels at 100% (the desktop's points). Nothing is smaller than 12. */
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

    public final Color bg, surface, raised, sunken, border, borderStrong;
    public final Color text, muted, faint;
    public final Color accent, onAccent, focus, info, good, warn, bad;
    public final Color dem, rep, tossUp;
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

    /** The map's ramp, exactly as the desktop's: deepened where a label would otherwise fall below 4.5:1. */
    public Color margin(double dMinusR) {
        double t = Math.min(1, Math.abs(dMinusR) / 15.0);
        Color end = dMinusR >= 0 ? dem : rep;
        Color base = mix(surface, tossUp, 0.55);
        return legible(mix(base, end, 0.25 + 0.75 * Math.sqrt(t)));
    }

    public Color legible(Color fill) {
        Color c = fill;
        for (int i = 0; i < 20 && Math.max(contrast(c, text), contrast(c, paperText)) < 4.6; i++) c = mix(c, bg, 0.06);
        return c;
    }

    /** The opaque fill of a status pill (the desktop's rule): text on it is always {@link #text}. */
    public Color pillFill(Color tone) { return mix(sunken, tone, 0.24); }

    public Color level(double v) { return v >= 0.6 ? good : v >= 0.35 ? warn : bad; }

    // ---- type --------------------------------------------------------------------------------------------

    public Font font(Face face, Size size) { return new Font(face, size.points * scale); }
    public Font font(Face face, double points) { return new Font(face, points * scale); }
    public Font ui(Size s) { return font(Face.UI, s); }
    public Font bold(Size s) { return font(Face.UI_BOLD, s); }
    public Font mono(Size s) { return font(Face.MONO, s); }

    // ---- space -------------------------------------------------------------------------------------------

    public int u(int units) { return (int) Math.round(units * 4 * scale); }
    public int px(double points) { return (int) Math.round(points * scale); }
    public int radius() { return px(8); }

    // ---- colour helpers ----------------------------------------------------------------------------------

    public static Color rgb(int v) { return Color.rgb(v); }

    public static Color mix(Color a, Color b, double t) {
        return new Color((int) Math.round(a.r + (b.r - a.r) * t), (int) Math.round(a.g + (b.g - a.g) * t), (int) Math.round(a.b + (b.b - a.b) * t));
    }

    public static Color alpha(Color c, double a) { return new Color(c.r, c.g, c.b, a); }

    public static double luminance(Color c) {
        double[] v = {c.r / 255.0, c.g / 255.0, c.b / 255.0};
        for (int i = 0; i < 3; i++) v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }

    public static double contrast(Color a, Color b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    public Color textOn(Color fill) { return contrast(fill, text) >= contrast(fill, paperText) ? text : paperText; }

    /** The tokens as CSS custom properties, for the stylesheet. */
    public String css() {
        StringBuilder b = new StringBuilder(":root {\n");
        var(b, "bg", bg); var(b, "surface", surface); var(b, "raised", raised); var(b, "sunken", sunken);
        var(b, "border", border); var(b, "border-strong", borderStrong); var(b, "text", text); var(b, "muted", muted); var(b, "faint", faint);
        var(b, "accent", accent); var(b, "on-accent", onAccent); var(b, "focus", focus); var(b, "info", info);
        var(b, "good", good); var(b, "warn", warn); var(b, "bad", bad); var(b, "dem", dem); var(b, "rep", rep); var(b, "toss-up", tossUp);
        var(b, "paper", paper); var(b, "paper-text", paperText); var(b, "paper-muted", paperMuted); var(b, "paper-rule", paperRule);
        var(b, "accent-hover", mix(accent, rgb(0xFFFFFF), 0.18)); var(b, "accent-down", mix(accent, rgb(0x000000), 0.18));
        var(b, "selected", mix(surface, info, 0.42)); var(b, "hover", mix(raised, borderStrong, 0.35));
        var(b, "danger-fill", mix(surface, bad, 0.22)); var(b, "danger-hover", mix(surface, bad, 0.35));
        var(b, "tip-fill", mix(surface, accent, 0.08)); var(b, "tip-stroke", mix(border, accent, 0.4));
        b.append("  --s: ").append(scale).append(";\n");
        b.append("  --u: ").append(4 * scale).append("px;\n");
        b.append("  --r: ").append(radius()).append("px;\n");
        return b.append("}\n").toString();
    }

    static void var(StringBuilder b, String name, Color c) { b.append("  --").append(name).append(": ").append(c.css()).append(";\n"); }
}
