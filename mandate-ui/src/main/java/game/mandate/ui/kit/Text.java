package game.mandate.ui.kit;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Text that wraps to its width and reports the height it needs (so cards grow instead of clipping). It is a
 * read-only label to assistive technology, with its full text as the accessible name.
 */
public class Text extends JComponent implements Layouts.HeightForWidth {
    private String text;
    private Color color;
    private int maxLines = Integer.MAX_VALUE;
    private boolean uppercase, center, right;
    private float tracking;

    public Text(String text, Font font, Color color) {
        this.text = text == null ? "" : text;
        this.color = color;
        setFont(font);
        setOpaque(false);
    }

    public static Text of(String s, Theme.Face face, Theme.Size size, Color c) { return new Text(s, Theme.get().font(face, size), c); }
    public static Text body(String s) { Theme t = Theme.get(); return new Text(s, t.ui(Theme.Size.BODY), t.text); }
    public static Text muted(String s) { Theme t = Theme.get(); return new Text(s, t.ui(Theme.Size.SMALL), t.muted); }
    public static Text faint(String s) { Theme t = Theme.get(); return new Text(s, t.ui(Theme.Size.CAPTION), t.faint); }
    public static Text strong(String s, Theme.Size size) { Theme t = Theme.get(); return new Text(s, t.bold(size), t.text); }
    /** The small uppercase label used over KPIs and card sections (Battleground 2028 / Resolute Desk). */
    public static Text eyebrow(String s) { Theme t = Theme.get(); return new Text(s, t.bold(Theme.Size.CAPTION), t.faint).upper(); }
    public static Text display(String s, Theme.Size size) { Theme t = Theme.get(); return new Text(s, t.font(Theme.Face.DISPLAY, size), t.text); }

    public Text upper() {
        uppercase = true;
        tracking = 0.08f;
        setFont(getFont().deriveFont(Map.of(TextAttribute.TRACKING, tracking)));
        return this;
    }

    public Text lines(int n) { maxLines = n; return this; }
    public Text centred() { center = true; return this; }
    public Text alignRight() { right = true; return this; }
    public Text color(Color c) { color = c; repaint(); return this; }

    public void setText(String s) { text = s == null ? "" : s; revalidate(); repaint(); }
    public String getText() { return text; }
    String shown() { return uppercase ? text.toUpperCase() : text; }

    List<String> wrap(FontMetrics fm, int width) {
        List<String> out = new ArrayList<>();
        for (String para : shown().split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String trial = line.length() == 0 ? word : line + " " + word;
                if (line.length() > 0 && fm.stringWidth(trial) > width) {
                    out.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                } else {
                    line.setLength(0);
                    line.append(trial);
                }
            }
            out.add(line.toString());
        }
        if (out.size() > maxLines) {
            List<String> cut = new ArrayList<>(out.subList(0, maxLines));
            String last = cut.get(maxLines - 1);
            while (!last.isEmpty() && fm.stringWidth(last + "…") > width) last = last.substring(0, last.length() - 1);
            cut.set(maxLines - 1, last + "…");
            if (getToolTipText() == null) setToolTipText(text);
            return cut;
        }
        return out;
    }

    /** Whether the text had to be cut to fit (used by the automated clipping check). */
    public boolean truncated() {
        if (getWidth() <= 0) return false;
        FontMetrics fm = getFontMetrics(getFont());
        List<String> all = new ArrayList<>();
        int saved = maxLines;
        maxLines = Integer.MAX_VALUE;
        all.addAll(wrap(fm, getWidth()));
        maxLines = saved;
        if (all.size() > maxLines) return true;
        for (String l : all) if (fm.stringWidth(l) > getWidth() + 1 && !l.contains(" ")) return true;
        return false;
    }

    @Override public int heightFor(int width) {
        FontMetrics fm = getFontMetrics(getFont());
        int lines = Math.max(1, wrap(fm, Math.max(1, width)).size());
        return lines * lineHeight(fm);
    }

    int lineHeight(FontMetrics fm) { return (int) Math.ceil(fm.getHeight() * 1.12); }

    @Override public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int w = 0;
        for (String p : shown().split("\n")) w = Math.max(w, fm.stringWidth(p));
        int width = getWidth() > 0 ? getWidth() : w + 1;
        return new Dimension(w + 1, heightFor(width));
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(getFont());
        g.setColor(isEnabled() ? color : Theme.get().faint);
        FontMetrics fm = g.getFontMetrics();
        int y = fm.getAscent(), lh = lineHeight(fm);
        for (String l : wrap(fm, getWidth())) {
            int x = center ? (getWidth() - fm.stringWidth(l)) / 2 : right ? getWidth() - fm.stringWidth(l) : 0;
            g.drawString(l, x, y);
            y += lh;
        }
        g.dispose();
    }

    @Override public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
            @Override public AccessibleRole getAccessibleRole() { return AccessibleRole.LABEL; }
            @Override public String getAccessibleName() { return text; }
        };
        return accessibleContext;
    }
}
