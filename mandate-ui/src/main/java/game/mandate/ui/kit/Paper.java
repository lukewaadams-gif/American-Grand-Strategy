package game.mandate.ui.kit;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A paper document on the desk: the weekly brief, the 100-day report. Resolute Desk's daily brief is the model: a
 * cream page, a red classification rule, a serif title, and items marked by a coloured bar for their weight.
 */
public class Paper extends JPanel implements Layouts.HeightForWidth {
    public Paper(String classification, String title, String dateline) {
        super(new Layouts.Stack(Theme.get().u(3)));
        Theme t = Theme.get();
        setOpaque(false);
        int pad = t.u(8);
        setBorder(BorderFactory.createEmptyBorder(t.u(6), pad, t.u(6), pad));
        if (classification != null) add(new Text(classification.toUpperCase(), t.bold(Theme.Size.CAPTION), t.paperRule).centred());
        add(new Text(title, t.font(Theme.Face.SERIF_BOLD, Theme.Size.TITLE), t.paperText).centred());
        if (dateline != null) add(new Text(dateline, t.font(Theme.Face.SERIF_ITALIC, Theme.Size.BODY), t.paperMuted).centred());
        getAccessibleContext().setAccessibleName(title);
    }

    /** An item with a tone bar: red for bad news, amber for warnings, blue for information. */
    public Paper item(String text, Color tone) {
        Theme t = Theme.get();
        JPanel row = new JPanel(new Layouts.Stack(0)) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(tone);
                g.fillRect(0, t.px(3), t.px(5), Math.max(t.px(10), getHeight() - t.px(8)));
            }
        };
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(0, t.u(4), 0, 0));
        row.add(new Text(text, t.font(Theme.Face.SERIF, Theme.Size.LEAD), t.paperText));
        add(row);
        return this;
    }

    public Paper note(String text) {
        Theme t = Theme.get();
        add(new Text(text, t.ui(Theme.Size.CAPTION), t.paperMuted));
        return this;
    }

    public Paper with(JComponent c) { add(c); return this; }

    @Override public int heightFor(int width) { return ((Layouts.Stack) getLayout()).heightFor(this, width); }

    @Override public Dimension getPreferredSize() { int w = getWidth() > 0 ? getWidth() : 700; return new Dimension(w, heightFor(w)); }

    @Override protected void paintComponent(Graphics g0) {
        Theme t = Theme.get();
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(t.paper);
        g.fillRoundRect(0, 0, getWidth(), getHeight(), t.px(4), t.px(4));
        g.setColor(t.paperRule);
        g.fillRect(0, 0, getWidth(), t.px(6));
        g.dispose();
    }
}
