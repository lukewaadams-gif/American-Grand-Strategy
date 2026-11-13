package game.mandate.web.kit;

/** A rounded surface with an optional label, a control at the label's right, and an optional tone stripe. */
public class Card extends El {
    public Card(String eyebrow) { this(eyebrow, null); }

    public Card(String eyebrow, El accessory) {
        super("section");
        Theme t = Theme.get();
        cls("card stack");
        px("gap", t.u(3));
        if (eyebrow != null || accessory != null) {
            El head = Layouts.side(t.u(2), null, eyebrow == null ? null : Text.eyebrow(eyebrow).heading(3), accessory);
            head.cls("card-head");
            add(head);
            if (eyebrow != null) label(eyebrow);
        }
    }

    public Card fill(Color c) { css("background", c.css()); return this; }
    public Card stroke(Color c) { css("border-color", c.css()); return this; }
    public Card stripe(Color c) { cls("striped"); css("--stripe", c.css()); return this; }

    public Card with(El... cs) { add(cs); return this; }
}
