package game.mandate.ui.kit;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.util.List;

/**
 * A data table: a header of uppercase labels and rows of cells, columns sized by weight, numbers right-aligned,
 * text wrapping inside its cell. Cells may be components (a meter, a tag, a button). Every row reads as one line to
 * assistive technology ("Pennsylvania, 19, D+1.2 ...").
 */
public class Table extends JPanel implements Layouts.HeightForWidth {
    public record Col(String header, double weight, boolean right) {
        public static Col left(String h, double w) { return new Col(h, w, false); }
        public static Col right(String h, double w) { return new Col(h, w, true); }
    }

    private final List<Col> cols;
    private int rows;

    public Table(List<Col> cols) {
        super(new Layouts.Stack(0));
        this.cols = cols;
        setOpaque(false);
        JPanel head = line(true);
        for (Col c : cols) {
            Text t = Text.eyebrow(c.header());
            if (c.right()) t.alignRight();
            head.add(t);
        }
        add(head);
    }

    JPanel line(boolean header) {
        Theme t = Theme.get();
        boolean stripe = !header && rows % 2 == 1;
        JPanel p = new JPanel(new Cols()) {
            @Override protected void paintComponent(Graphics g) {
                if (stripe) { g.setColor(t.sunken); g.fillRect(0, 0, getWidth(), getHeight()); }
                if (header) { g.setColor(t.border); g.fillRect(0, getHeight() - 1, getWidth(), 1); }
            }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(t.u(header ? 1 : 2), t.u(2), t.u(header ? 2 : 2), t.u(2)));
        return p;
    }

    /** Adds a row. Strings become text (the first column bold-free body text, the rest muted); components are used as given. */
    public Table row(Object... cells) {
        rows++;
        JPanel r = line(false);
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            Object cell = i < cells.length ? cells[i] : "";
            JComponent c;
            if (cell instanceof JComponent j) c = j;
            else {
                Text t = i == 0 ? Text.body(String.valueOf(cell)) : Text.of(String.valueOf(cell), Theme.Face.UI, Theme.Size.BODY, Theme.get().muted);
                if (cols.get(i).right()) t.alignRight();
                c = t;
            }
            if (name.length() > 0) name.append(", ");
            name.append(cell instanceof JComponent j ? j.getAccessibleContext().getAccessibleName() : String.valueOf(cell));
            r.add(c);
        }
        r.getAccessibleContext().setAccessibleName(name.toString());
        add(r);
        return this;
    }

    public int rowCount() { return rows; }

    @Override public int heightFor(int width) { return ((Layouts.Stack) getLayout()).heightFor(this, width); }

    /** Columns by weight; each cell as tall as it needs; the row as tall as its tallest cell, cells centred. */
    final class Cols implements Layouts.HfwLayout {
        int[] widths(int w) {
            double total = cols.stream().mapToDouble(Col::weight).sum();
            int gap = Theme.get().u(3), avail = w - gap * (cols.size() - 1), used = 0;
            int[] ws = new int[cols.size()];
            for (int i = 0; i < ws.length; i++) { ws[i] = i == ws.length - 1 ? avail - used : (int) (avail * cols.get(i).weight() / total); used += ws[i]; }
            return ws;
        }

        @Override public int heightFor(Container p, int width) {
            Insets in = p.getInsets();
            int[] ws = widths(width - in.left - in.right);
            int h = 0;
            for (int i = 0; i < p.getComponentCount() && i < ws.length; i++) h = Math.max(h, Layouts.measure(p.getComponent(i), ws[i]));
            return h + in.top + in.bottom;
        }

        @Override public void layoutContainer(Container p) {
            Insets in = p.getInsets();
            int[] ws = widths(p.getWidth() - in.left - in.right);
            int x = in.left, gap = Theme.get().u(3), rowH = p.getHeight() - in.top - in.bottom;
            for (int i = 0; i < p.getComponentCount() && i < ws.length; i++) {
                Component c = p.getComponent(i);
                int h = Math.min(rowH, Layouts.measure(c, ws[i]));
                int w = ws[i];
                if (!(c instanceof Text) && !(c instanceof Layouts.HeightForWidth) && c.getPreferredSize().width < ws[i]) {
                    w = c.getPreferredSize().width;
                    if (cols.get(i).right()) { c.setBounds(x + ws[i] - w, in.top + (rowH - h) / 2, w, h); x += ws[i] + gap; continue; }
                }
                c.setBounds(x, in.top + (rowH - h) / 2, w, h);
                x += ws[i] + gap;
            }
        }

        @Override public Dimension preferredLayoutSize(Container p) { int w = p.getWidth() > 0 ? p.getWidth() : 600; return new Dimension(w, heightFor(p, w)); }
        @Override public Dimension minimumLayoutSize(Container p) { return new Dimension(0, 0); }
        @Override public void addLayoutComponent(String n, Component c) { }
        @Override public void removeLayoutComponent(Component c) { }
    }

}
