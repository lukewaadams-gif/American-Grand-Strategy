package game.mandate.web.kit;

/** A colour as the browser takes it. Values come from {@link Theme}, which holds the same palette as the desktop. */
public final class Color {
    public final int r, g, b;
    public final double a;

    public Color(int r, int g, int b) { this(r, g, b, 1); }

    public Color(int r, int g, int b, double a) {
        this.r = clamp(r);
        this.g = clamp(g);
        this.b = clamp(b);
        this.a = Math.max(0, Math.min(1, a));
    }

    static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    public static Color rgb(int v) { return new Color((v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF); }

    public int getRed() { return r; }
    public int getGreen() { return g; }
    public int getBlue() { return b; }

    public String css() {
        if (a >= 1) return "#" + hex(r) + hex(g) + hex(b);
        return "rgba(" + r + "," + g + "," + b + "," + (Math.round(a * 1000) / 1000.0) + ")";
    }

    static String hex(int v) { String s = Integer.toHexString(v); return s.length() == 1 ? "0" + s : s; }

    @Override public String toString() { return css(); }
}
