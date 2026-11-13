package game.mandate.web.kit;

import java.util.function.Consumer;

/** Small helpers the screens share. */
public final class Ui {
    private Ui() {}

    /** A region that scrolls vertically on its own (the page, the advisor panel). */
    public static El scroll(El view) { return El.div("scroll").add(view); }

    /** A labelled text field that reports each change. */
    public static El field(String value, String accessibleName, Consumer<String> onChange) {
        El in = new El("input").cls("field").attr("type", "text").label(accessibleName);
        Dom.setValue(in.node, value == null ? "" : value);
        in.on("input", ev -> onChange.accept(Dom.value(in.node)));
        return in;
    }

    /** A line of content with a control at its end. */
    public static El border(El center, El east) { return Layouts.side(Theme.get().u(3), null, center, east); }

    public static El gap(int h) { return El.div("gap").px("height", h); }

    public static void focusFirst(El c) { Dom.focusFirst(c.node); }
}
