package game.mandate.ui.kit;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Height-for-width layouts: text wraps, cards grow to fit it, rows collapse into stacks on narrow windows, and a
 * scrolling page tracks the window's width. This is what makes the interface resize without clipping.
 */
public final class Layouts {
    private Layouts() {}

    /** A component whose height depends on the width it is given (wrapped text, cards containing it). */
    public interface HeightForWidth { int heightFor(int width); }

    /** A layout that can say how tall it needs to be at a given width. */
    public interface HfwLayout extends LayoutManager { int heightFor(Container parent, int width); }

    public static final String WEIGHT = "mandate.weight", FILL = "mandate.fill", NATURAL = "mandate.natural";

    /** Marks a component (a tag, a badge) to keep its own width in a stack instead of stretching across it. */
    public static <T extends JComponent> T naturalWidth(T c) { c.putClientProperty(NATURAL, Boolean.TRUE); return c; }

    static boolean natural(Component c) { return c instanceof JComponent j && Boolean.TRUE.equals(j.getClientProperty(NATURAL)); }

    public static int measure(Component c, int width) {
        if (!c.isVisible()) return 0;
        if (c instanceof HeightForWidth h) return h.heightFor(width);
        if (c instanceof Container ct && ct.getLayout() instanceof HfwLayout l) return l.heightFor(ct, width);
        return c.getPreferredSize().height;
    }

    static double weight(Component c) {
        Object w = c instanceof JComponent j ? j.getClientProperty(WEIGHT) : null;
        return w instanceof Number n ? n.doubleValue() : 1.0;
    }

    static boolean fills(Component c) { return c instanceof JComponent j && Boolean.TRUE.equals(j.getClientProperty(FILL)); }

    public static <T extends JComponent> T weight(T c, double w) { c.putClientProperty(WEIGHT, w); return c; }

    /** Marks a stack child that takes the stack's remaining height (a chart in a card). */
    public static <T extends JComponent> T fill(T c) { c.putClientProperty(FILL, Boolean.TRUE); return c; }

    /** The width to plan for: this container's, or its nearest sized ancestor's (a new screen is laid out top down). */
    static int widthHint(Container p) {
        for (Container c = p; c != null; c = c.getParent()) if (c.getWidth() > 0) return c.getWidth() - (c == p ? 0 : 0);
        return 1200;
    }

    static List<Component> visible(Container p) {
        List<Component> l = new ArrayList<>();
        for (Component c : p.getComponents()) if (c.isVisible()) l.add(c);
        return l;
    }

    // ---- stack -----------------------------------------------------------------------------------------------

    /** Children top to bottom, each the full width. One child marked {@link #fill} takes the remaining height. */
    public static final class Stack implements HfwLayout {
        final int gap;
        public Stack(int gap) { this.gap = gap; }

        @Override public int heightFor(Container p, int width) {
            Insets in = p.getInsets();
            int w = width - in.left - in.right, h = in.top + in.bottom;
            List<Component> cs = visible(p);
            for (int i = 0; i < cs.size(); i++) h += measure(cs.get(i), w) + (i > 0 ? gap : 0);
            return h;
        }

        @Override public void layoutContainer(Container p) {
            Insets in = p.getInsets();
            int w = p.getWidth() - in.left - in.right, y = in.top;
            List<Component> cs = visible(p);
            int natural = heightFor(p, p.getWidth()), extra = Math.max(0, p.getHeight() - natural);
            for (int i = 0; i < cs.size(); i++) {
                Component c = cs.get(i);
                if (i > 0) y += gap;
                int h = measure(c, w) + (fills(c) ? extra : 0);
                c.setBounds(in.left, y, natural(c) ? Math.min(w, c.getPreferredSize().width) : w, h);
                y += h;
            }
        }

        @Override public Dimension preferredLayoutSize(Container p) {
            Insets in = p.getInsets();
            int w = 0;
            for (Component c : visible(p)) w = Math.max(w, c.getPreferredSize().width);
            int width = p.getWidth() > 0 ? p.getWidth() : Math.min(widthHint(p), Math.max(w + in.left + in.right, 1));
            return new Dimension(w + in.left + in.right, heightFor(p, width));
        }

        @Override public Dimension minimumLayoutSize(Container p) { return new Dimension(0, 0); }
        @Override public void addLayoutComponent(String n, Component c) { }
        @Override public void removeLayoutComponent(Component c) { }
    }

    // ---- row -------------------------------------------------------------------------------------------------

    /**
     * Children side by side, widths by weight ({@link #weight}), all as tall as the tallest. Below
     * {@code collapseBelow} pixels the row becomes a stack, so dashboards reflow on small windows.
     */
    public static final class Row implements HfwLayout {
        final int gap, collapseBelow;
        public Row(int gap, int collapseBelow) { this.gap = gap; this.collapseBelow = collapseBelow; }

        boolean stacked(int w) { return w < collapseBelow; }

        int[] widths(List<Component> cs, int w) {
            double total = 0;
            for (Component c : cs) total += weight(c);
            int avail = w - gap * Math.max(0, cs.size() - 1), used = 0;
            int[] ws = new int[cs.size()];
            for (int i = 0; i < cs.size(); i++) {
                ws[i] = i == cs.size() - 1 ? avail - used : (int) Math.floor(avail * weight(cs.get(i)) / total);
                used += ws[i];
            }
            return ws;
        }

        @Override public int heightFor(Container p, int width) {
            Insets in = p.getInsets();
            int w = width - in.left - in.right;
            List<Component> cs = visible(p);
            if (stacked(w)) {
                int h = 0;
                for (int i = 0; i < cs.size(); i++) h += measure(cs.get(i), w) + (i > 0 ? gap : 0);
                return h + in.top + in.bottom;
            }
            int[] ws = widths(cs, w);
            int h = 0;
            for (int i = 0; i < cs.size(); i++) h = Math.max(h, measure(cs.get(i), ws[i]));
            return h + in.top + in.bottom;
        }

        @Override public void layoutContainer(Container p) {
            Insets in = p.getInsets();
            int w = p.getWidth() - in.left - in.right;
            List<Component> cs = visible(p);
            if (stacked(w)) {
                int y = in.top;
                for (Component c : cs) { int h = measure(c, w); c.setBounds(in.left, y, w, h); y += h + gap; }
                return;
            }
            int[] ws = widths(cs, w);
            int h = p.getHeight() - in.top - in.bottom, x = in.left;
            for (int i = 0; i < cs.size(); i++) { cs.get(i).setBounds(x, in.top, ws[i], h); x += ws[i] + gap; }
        }

        @Override public Dimension preferredLayoutSize(Container p) {
            int width = widthHint(p);
            return new Dimension(width, heightFor(p, width));
        }

        @Override public Dimension minimumLayoutSize(Container p) { return new Dimension(0, 0); }
        @Override public void addLayoutComponent(String n, Component c) { }
        @Override public void removeLayoutComponent(Component c) { }
    }

    // ---- wrap ------------------------------------------------------------------------------------------------

    /** Children left to right at their preferred sizes, wrapping onto new lines (chips, option groups). */
    public static final class Wrap implements HfwLayout {
        final int hgap, vgap;
        boolean top;
        public Wrap(int hgap, int vgap) { this.hgap = hgap; this.vgap = vgap; }

        /** Aligns items on a line by their tops instead of their centres (for rows of figures with labels). */
        public Wrap top() { top = true; return this; }

        @Override public int heightFor(Container p, int width) { return place(p, width, false); }

        /** Places items in lines; within a line, shorter items are centred on the tallest. */
        int place(Container p, int width, boolean apply) {
            Insets in = p.getInsets();
            int maxX = width - in.right, x = in.left, y = in.top, line = 0;
            List<Component> row = new ArrayList<>();
            List<Rectangle> at = new ArrayList<>();
            for (Component c : visible(p)) {
                Dimension d = c.getPreferredSize();
                int cw = Math.min(d.width, width - in.left - in.right);
                if (x > in.left && x + cw > maxX) {
                    if (apply) settle(row, at, top ? 0 : line);
                    row.clear(); at.clear();
                    x = in.left; y += line + vgap; line = 0;
                }
                row.add(c);
                at.add(new Rectangle(x, y, cw, d.height));
                x += cw + hgap;
                line = Math.max(line, d.height);
            }
            if (apply) settle(row, at, top ? 0 : line);
            return y + line + in.bottom;
        }

        /** {@code line} is the line's height, to centre on; 0 keeps every item at the line's top. */
        static void settle(List<Component> row, List<Rectangle> at, int line) {
            for (int i = 0; i < row.size(); i++) {
                Rectangle r = at.get(i);
                row.get(i).setBounds(r.x, r.y + Math.max(0, (line - r.height) / 2), r.width, r.height);
            }
        }

        @Override public void layoutContainer(Container p) { place(p, p.getWidth(), true); }

        @Override public Dimension preferredLayoutSize(Container p) {
            int width = p.getWidth() > 0 ? p.getWidth() : Math.min(widthHint(p), natural(p));
            return new Dimension(width, heightFor(p, width));
        }

        public int natural(Container p) {
            Insets in = p.getInsets();
            int w = in.left + in.right;
            for (Component c : visible(p)) w += c.getPreferredSize().width + hgap;
            return Math.max(1, w);
        }

        @Override public Dimension minimumLayoutSize(Container p) { return new Dimension(0, 0); }
        @Override public void addLayoutComponent(String n, Component c) { }
        @Override public void removeLayoutComponent(Component c) { }
    }

    // ---- side by side: a growing middle and fixed sides ------------------------------------------------------

    /**
     * West, centre and east (any may be absent): the sides take their preferred widths, the centre the rest; the
     * height is the tallest of the three at those widths. Used for a line of content with a control at its end.
     */
    public static final class Side implements HfwLayout {
        public static final String WEST = "West", CENTER = "Center", EAST = "East";
        final int gap;
        Component west, center, east;
        public Side(int gap) { this.gap = gap; }

        @Override public void addLayoutComponent(String n, Component c) {
            if (WEST.equals(n)) west = c; else if (EAST.equals(n)) east = c; else center = c;
        }
        @Override public void removeLayoutComponent(Component c) { if (c == west) west = null; if (c == east) east = null; if (c == center) center = null; }

        int w(Component c) { return c == null || !c.isVisible() ? 0 : c.getPreferredSize().width; }

        @Override public int heightFor(Container p, int width) {
            Insets in = p.getInsets();
            int ww = w(west), ew = w(east), cw = width - in.left - in.right - ww - ew - (ww > 0 ? gap : 0) - (ew > 0 ? gap : 0);
            int h = 0;
            if (west != null && west.isVisible()) h = Math.max(h, measure(west, ww));
            if (east != null && east.isVisible()) h = Math.max(h, east.getPreferredSize().height);
            if (center != null && center.isVisible()) h = Math.max(h, measure(center, Math.max(1, cw)));
            return h + in.top + in.bottom;
        }

        @Override public void layoutContainer(Container p) {
            Insets in = p.getInsets();
            int x = in.left, y = in.top, h = p.getHeight() - in.top - in.bottom, ww = w(west), ew = w(east);
            int cw = p.getWidth() - in.left - in.right - ww - ew - (ww > 0 ? gap : 0) - (ew > 0 ? gap : 0);
            if (west != null && west.isVisible()) { west.setBounds(x, y, ww, h); x += ww + gap; }
            if (center != null && center.isVisible()) center.setBounds(x, y, Math.max(1, cw), h);
            if (east != null && east.isVisible()) {
                int eh = Math.min(h, east.getPreferredSize().height);
                east.setBounds(p.getWidth() - in.right - ew, y + (h - eh) / 2, ew, eh);
            }
        }

        @Override public Dimension preferredLayoutSize(Container p) { int width = widthHint(p); return new Dimension(width, heightFor(p, width)); }
        @Override public Dimension minimumLayoutSize(Container p) { return new Dimension(0, 0); }
    }

    // ---- a page that scrolls vertically and tracks the window's width -----------------------------------------

    public static final class Page extends JPanel implements Scrollable {
        public Page(int gap) {
            super(new Stack(gap));
            setOpaque(false);
        }

        @Override public Dimension getPreferredSize() {
            int w = getParent() instanceof JViewport v ? v.getWidth() : getWidth();
            if (w <= 0) w = 1200;
            return new Dimension(w, ((Stack) getLayout()).heightFor(this, w));
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return Theme.get().u(6); }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height - Theme.get().u(10); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport v && v.getHeight() > ((Stack) getLayout()).heightFor(this, v.getWidth());
        }
    }

    public static JPanel stack(int gap, Component... cs) {
        JPanel p = new JPanel(new Stack(gap));
        p.setOpaque(false);
        for (Component c : cs) p.add(c);
        return p;
    }

    public static JPanel row(int gap, int collapseBelow, Component... cs) {
        JPanel p = new JPanel(new Row(gap, collapseBelow));
        p.setOpaque(false);
        for (Component c : cs) p.add(c);
        return p;
    }

    public static JPanel wrap(int hgap, int vgap, Component... cs) {
        JPanel p = new JPanel(new Wrap(hgap, vgap));
        p.setOpaque(false);
        for (Component c : cs) p.add(c);
        return p;
    }
}
