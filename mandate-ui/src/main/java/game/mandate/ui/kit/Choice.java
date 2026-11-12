package game.mandate.ui.kit;

import javax.swing.JButton;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A large option button for a decision: a number key, a label, and the trade-offs it carries, wrapped to fit, with
 * an optional risk tag ("Gamble: 40%"). Used for decision cards, events, strategies and candidates.
 */
public class Choice extends JButton implements Layouts.HeightForWidth {
    private final String title, detail, tag, key;
    private final Color tagColor;
    private boolean selected;

    public Choice(String key, String title, String detail, String tag, Color tagColor, ActionListener a) {
        super(title);
        this.key = key;
        this.title = title;
        this.detail = detail == null ? "" : detail;
        this.tag = tag;
        this.tagColor = tagColor;
        if (a != null) addActionListener(a);
        setUI(new BasicButtonUI());
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        getAccessibleContext().setAccessibleName((key == null ? "" : "Option " + key + ": ") + title);
        getAccessibleContext().setAccessibleDescription(this.detail + (tag == null ? "" : " (" + tag + ")"));
        Ui.enterPresses(this);
    }

    public Choice selected(boolean on) { selected = on; repaint(); return this; }

    /** Disables the option and says why (as its tooltip and to assistive technology); enabled again if null. */
    public Choice disabledBecause(String reason) {
        setEnabled(reason == null);
        if (reason != null) { setToolTipText(reason); getAccessibleContext().setAccessibleDescription(reason); }
        return this;
    }

    int pad() { return Theme.get().u(4); }

    int textLeft() { return pad() + (key == null ? 0 : Theme.get().u(8)); }

    List<String> lines(FontMetrics fm, String s, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : s.split(" ")) {
            String trial = line.length() == 0 ? w : line + " " + w;
            if (line.length() > 0 && fm.stringWidth(trial) > width) { out.add(line.toString()); line = new StringBuilder(w); }
            else line = new StringBuilder(trial);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    @Override public int heightFor(int width) {
        Theme t = Theme.get();
        int inner = width - textLeft() - pad() - (tag == null ? 0 : tagWidth() + t.u(2));
        FontMetrics tf = getFontMetrics(t.bold(Theme.Size.LEAD)), df = getFontMetrics(t.ui(Theme.Size.SMALL));
        int h = pad() * 2 + lines(tf, title, Math.max(1, inner)).size() * tf.getHeight();
        if (!detail.isEmpty()) h += t.u(1) + lines(df, detail, Math.max(1, width - textLeft() - pad())).size() * (int) (df.getHeight() * 1.1);
        return h;
    }

    int tagWidth() { return tag == null ? 0 : getFontMetrics(Theme.get().bold(Theme.Size.CAPTION)).stringWidth(tag) + Theme.get().u(4); }

    @Override public Dimension getPreferredSize() { int w = getWidth() > 0 ? getWidth() : 480; return new Dimension(w, heightFor(w)); }

    @Override protected void paintComponent(Graphics g0) {
        Theme t = Theme.get();
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        boolean hover = isEnabled() && getModel().isRollover();
        float r = t.radius();
        RoundRectangle2D shape = new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, r, r);
        g.setColor(selected ? Theme.mix(t.surface, t.info, 0.30) : hover ? t.raised : Theme.mix(t.surface, t.raised, 0.5));
        g.fill(shape);
        g.setColor(selected ? t.info : hover ? t.borderStrong : t.border);
        g.setStroke(new BasicStroke((float) t.scale));
        g.draw(shape);
        if (isFocusOwner()) {
            g.setColor(t.focus);
            g.setStroke(new BasicStroke(2f * (float) t.scale));
            g.draw(new RoundRectangle2D.Float(2, 2, getWidth() - 4, getHeight() - 4, r, r));
        }
        int x = textLeft(), y = pad();
        if (key != null) {
            int d = t.u(6);
            g.setColor(t.raised);
            g.fillRoundRect(pad(), y, d, d, d / 2, d / 2);
            g.setColor(t.borderStrong);
            g.drawRoundRect(pad(), y, d, d, d / 2, d / 2);
            g.setFont(t.mono(Theme.Size.SMALL));
            FontMetrics km = g.getFontMetrics();
            g.setColor(t.text);
            g.drawString(key, pad() + (d - km.stringWidth(key)) / 2, y + (d - km.getHeight()) / 2 + km.getAscent());
        }
        if (tag != null) {
            g.setFont(t.bold(Theme.Size.CAPTION));
            FontMetrics m = g.getFontMetrics();
            int tw = tagWidth(), th = m.getHeight() + t.u(1), tx = getWidth() - pad() - tw;
            g.setColor(Theme.alpha(tagColor, 0.18));
            g.fillRoundRect(tx, y, tw, th, th, th);
            g.setColor(tagColor);
            g.drawString(tag, tx + t.u(2), y + (th - m.getHeight()) / 2 + m.getAscent());
        }
        g.setFont(t.bold(Theme.Size.LEAD));
        FontMetrics tf = g.getFontMetrics();
        g.setColor(isEnabled() ? t.text : t.faint);
        int inner = getWidth() - x - pad() - (tag == null ? 0 : tagWidth() + t.u(2));
        for (String l : lines(tf, title, Math.max(1, inner))) { g.drawString(l, x, y + tf.getAscent()); y += tf.getHeight(); }
        if (!detail.isEmpty()) {
            y += t.u(1);
            g.setFont(t.ui(Theme.Size.SMALL));
            FontMetrics df = g.getFontMetrics();
            g.setColor(t.muted);
            for (String l : lines(df, detail, Math.max(1, getWidth() - x - pad()))) { g.drawString(l, x, y + df.getAscent()); y += (int) (df.getHeight() * 1.1); }
        }
        g.dispose();
    }
}
