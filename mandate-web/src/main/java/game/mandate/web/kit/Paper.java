package game.mandate.web.kit;

/** Resolute Desk's paper: a cream document with a red rule, a serif title and dateline, and toned items. */
public class Paper extends El {
    public Paper(String classification, String title, String dateline) {
        super("article");
        cls("paper stack");
        Theme t = Theme.get();
        px("gap", t.u(3));
        if (classification != null) add(El.div("paper-class").text(classification));
        add(El.div("paper-title").text(title).role("heading").attr("aria-level", "3"));
        if (dateline != null) add(El.div("paper-dateline").text(dateline));
        label(title);
    }

    public Paper item(String text, Color tone) {
        add(El.div("paper-item").text(text).css("--tone", (tone == null ? Theme.get().paperRule : tone).css()));
        return this;
    }

    public Paper note(String text) { add(El.div("paper-note").text(text)); return this; }

    public Paper with(El c) { add(c); return this; }
}
