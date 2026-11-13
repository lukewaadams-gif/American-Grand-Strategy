package game.mandate.web.kit;

/** Text that wraps to its column. {@link #lines} limits it, with the full text in the tooltip. */
public class Text extends El {
    private String text;

    public Text(String text, Font font, Color color) {
        super("div");
        cls("text");
        this.text = text == null ? "" : text;
        text(this.text);
        font.applyTo(this);
        if (color != null) css("color", color.css());
    }

    public static Text of(String s, Theme.Face face, Theme.Size size, Color c) { return new Text(s, Theme.get().font(face, size), c); }
    public static Text body(String s) { Theme t = Theme.get(); return new Text(s, t.ui(Theme.Size.BODY), t.text); }
    public static Text muted(String s) { Theme t = Theme.get(); return new Text(s, t.ui(Theme.Size.SMALL), t.muted); }
    public static Text faint(String s) { Theme t = Theme.get(); return new Text(s, t.ui(Theme.Size.CAPTION), t.faint); }
    public static Text strong(String s, Theme.Size size) { Theme t = Theme.get(); return new Text(s, t.bold(size), t.text); }
    public static Text eyebrow(String s) { Theme t = Theme.get(); return new Text(s, t.bold(Theme.Size.CAPTION), t.faint).upper(); }
    public static Text display(String s, Theme.Size size) { Theme t = Theme.get(); return new Text(s, t.font(Theme.Face.DISPLAY, size), t.text); }

    public Text upper() { cls("upper"); return this; }

    public Text lines(int n) {
        cls("clamp");
        css("-webkit-line-clamp", String.valueOf(n));
        if (getToolTipText() == null) tip(text);
        return this;
    }

    public Text centred() { css("text-align", "center"); return this; }
    public Text alignRight() { css("text-align", "right"); return this; }
    public Text color(Color c) { css("color", c.css()); return this; }

    public void setText(String s) { text = s == null ? "" : s; text(text); }
    public String getText() { return text; }

    /** A heading for assistive technology (screen titles, card titles). */
    public Text heading(int level) { attr("role", "heading"); attr("aria-level", String.valueOf(level)); return this; }
}
