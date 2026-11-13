package game.mandate.web.kit;

/**
 * The desktop's layouts in CSS. {@link #row} keeps the desktop's rule: cards sit side by side, sized by weight,
 * until the row is narrower than its threshold, and then every card takes the full width (a flex-basis trick:
 * {@code calc((threshold - 100%) * 999)} is hugely negative above the threshold and hugely positive below it).
 */
public final class Layouts {
    private Layouts() {}

    public static <T extends El> T weight(T c, double w) { c.weight = w; return c; }

    /** In a stack, this child takes the remaining height. */
    public static <T extends El> T fill(T c) { c.fill = true; c.css("flex", "1 1 auto"); return c; }

    /** Keeps a component (a tag, a badge) at its own width inside a stack. */
    public static <T extends El> T naturalWidth(T c) { c.css("align-self", "flex-start"); return c; }

    public static El stack(int gap, El... cs) { return El.div("stack").px("gap", gap).add(cs); }

    public static El row(int gap, int collapseBelow, El... cs) {
        El r = El.div("row").px("gap", gap);
        for (El c : cs) {
            if (c == null) continue;
            c.css("flex-grow", String.valueOf(c.weight)).css("flex-shrink", "1")
                    .css("flex-basis", "calc((" + collapseBelow + "px - 100%) * 999)").css("min-width", "0");
            r.add(c);
        }
        return r;
    }

    public static El wrap(int hgap, int vgap, El... cs) { return El.div("wrap").css("gap", vgap + "px " + hgap + "px").add(cs); }

    /** A line of figures with labels: aligned by their tops. */
    public static El wrapTop(int hgap, int vgap, El... cs) { return wrap(hgap, vgap, cs).css("align-items", "flex-start"); }

    /** West, centre and east: the sides keep their width, the centre takes the rest. */
    public static El side(int gap, El west, El center, El east) {
        El s = El.div("side").px("gap", gap);
        if (west != null) s.add(west.css("flex", "0 0 auto"));
        if (center != null) s.add(center.css("flex", "1 1 0").css("min-width", "0"));
        else s.add(El.div("spacer").css("flex", "1 1 0"));
        if (east != null) s.add(east.css("flex", "0 0 auto"));
        return s;
    }

    /** A screen's page: padded, one column. */
    public static El page(int gap) { return El.div("page stack").px("gap", gap); }
}
