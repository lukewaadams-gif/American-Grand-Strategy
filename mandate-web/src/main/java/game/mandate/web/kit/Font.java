package game.mandate.web.kit;

/** A face and a size in CSS pixels (already scaled). */
public record Font(Theme.Face face, double size) {
    public String family() {
        return switch (face) {
            case UI, UI_BOLD -> "Inter, system-ui, sans-serif";
            case DISPLAY -> "'Playfair Display', Georgia, serif";
            case MONO, MONO_BOLD -> "'JetBrains Mono', ui-monospace, monospace";
            case SERIF, SERIF_BOLD, SERIF_ITALIC -> "'Source Serif 4', Georgia, serif";
        };
    }

    public String weight() {
        return switch (face) { case UI_BOLD, DISPLAY, MONO_BOLD, SERIF_BOLD -> "700"; default -> "400"; };
    }

    public boolean italic() { return face == Theme.Face.SERIF_ITALIC; }

    public float getSize2D() { return (float) size; }

    /** Applies the font to an element. */
    public void applyTo(El e) {
        e.css("font-family", family()).css("font-weight", weight()).css("font-size", round(size) + "px");
        if (italic()) e.css("font-style", "italic");
    }

    static String round(double v) { return String.valueOf(Math.round(v * 10) / 10.0); }
}
