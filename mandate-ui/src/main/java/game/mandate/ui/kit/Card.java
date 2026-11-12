package game.mandate.ui.kit;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * A surface: a rounded panel with an optional section label, holding a stack of content. Cards are the unit of
 * every dashboard; the label says what the card is for in the uppercase style the source games established.
 */
public class Card extends JPanel implements Layouts.HeightForWidth {
    private Color fill, stroke, stripe;
    private final JPanel body;

    public Card(String eyebrow) { this(eyebrow, null); }

    /** @param accessory a component on the label's right (a status word, a small button), or null */
    public Card(String eyebrow, JComponent accessory) {
        Theme t = Theme.get();
        setLayout(new Layouts.Stack(t.u(3)));
        setOpaque(false);
        int pad = t.u(4);
        setBorder(BorderFactory.createEmptyBorder(pad, pad, pad, pad));
        fill = t.surface;
        stroke = t.border;
        if (eyebrow != null || accessory != null) {
            JPanel head = new JPanel(new Layouts.Side(t.u(2)));
            head.setOpaque(false);
            if (eyebrow != null) head.add(Text.eyebrow(eyebrow), Layouts.Side.CENTER);
            if (accessory != null) head.add(accessory, Layouts.Side.EAST);
            super.add(head);
            getAccessibleContext().setAccessibleName(eyebrow);
        }
        body = this;
    }

    public Card fill(Color c) { fill = c; return this; }
    public Card stroke(Color c) { stroke = c; return this; }
    /** A coloured bar on the left edge: the tone of an advisory, a warning card. */
    public Card stripe(Color c) { stripe = c; return this; }

    public Card with(JComponent... cs) { for (JComponent c : cs) body.add(c); return this; }

    @Override public int heightFor(int width) { return ((Layouts.Stack) getLayout()).heightFor(this, width); }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Theme t = Theme.get();
        float r = t.radius();
        RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, r, r);
        g.setColor(fill);
        g.fill(shape);
        if (stripe != null) {
            g.setClip(shape);
            g.setColor(stripe);
            g.fillRect(0, 0, t.px(4), getHeight());
            g.setClip(null);
        }
        g.setColor(stroke);
        g.setStroke(new BasicStroke(Math.max(1f, (float) t.scale)));
        g.draw(shape);
        g.dispose();
    }
}
