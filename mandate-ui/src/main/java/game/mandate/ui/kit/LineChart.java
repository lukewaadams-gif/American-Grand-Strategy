package game.mandate.ui.kit;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

/**
 * A line chart for weekly series. The vertical axis fits the data with a minimum span, so a flat series reads as
 * flat rather than as noise, and an optional reference line (50% approval, a tied race) anchors it. Every series
 * is labelled at its end with its latest value; hovering shows the values for a week.
 */
public class LineChart extends JComponent {
    public record Series(String name, Color color, List<Double> values) {}

    private final List<Series> series = new ArrayList<>();
    private final List<String> xLabels;
    private final DoubleFunction<String> format;
    private final double minSpan;
    private Double reference;
    private String referenceLabel, empty = "No data yet: this chart fills in as the weeks pass.";
    private int hover = -1;
    private final int preferredHeight;

    public LineChart(List<String> xLabels, DoubleFunction<String> format, double minSpan, int preferredHeight) {
        this.xLabels = xLabels;
        this.format = format;
        this.minSpan = minSpan;
        this.preferredHeight = preferredHeight;
        setOpaque(false);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { hover = indexAt(e.getX()); setToolTipText(tooltip()); repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
        };
        addMouseMotionListener(m);
        addMouseListener(m);
    }

    public LineChart add(String name, Color color, List<Double> values) { series.add(new Series(name, color, values)); return this; }
    public LineChart reference(double v, String label) { reference = v; referenceLabel = label; return this; }
    public LineChart emptyText(String s) { empty = s; return this; }

    int count() { return series.stream().mapToInt(s -> s.values.size()).max().orElse(0); }

    int left() { return Theme.get().u(12); }
    int right() { return Theme.get().u(16); }
    int top() { return Theme.get().u(7); }
    int bottom() { return Theme.get().u(6); }

    int indexAt(int x) {
        int n = count();
        if (n < 2) return -1;
        double step = (getWidth() - left() - right()) / (double) (n - 1);
        int i = (int) Math.round((x - left()) / step);
        return i < 0 || i >= n ? -1 : i;
    }

    String tooltip() {
        if (hover < 0) return null;
        StringBuilder b = new StringBuilder("<html><b>" + (hover < xLabels.size() ? xLabels.get(hover) : "") + "</b>");
        for (Series s : series) if (hover < s.values.size()) b.append("<br>").append(s.name).append(": ").append(format.apply(s.values.get(hover)));
        return b.append("</html>").toString();
    }

    /** {lo, hi, step}: a padded, rounded axis range. */
    double[] axis() {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (Series s : series) for (double v : s.values) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        if (reference != null) { lo = Math.min(lo, reference); hi = Math.max(hi, reference); }
        if (lo > hi) { lo = 0; hi = 1; }
        double span = Math.max(minSpan, hi - lo), mid = (lo + hi) / 2;
        lo = mid - span * 0.6;
        hi = mid + span * 0.6;
        double raw = (hi - lo) / 4, mag = Math.pow(10, Math.floor(Math.log10(raw))), step = mag;
        for (double m : new double[]{1, 2, 2.5, 5, 10}) if (m * mag >= raw) { step = m * mag; break; }
        return new double[]{Math.floor(lo / step) * step, Math.ceil(hi / step) * step, step};
    }

    @Override public Dimension getPreferredSize() { return new Dimension(Theme.get().u(80), preferredHeight); }

    @Override protected void paintComponent(Graphics g0) {
        Theme t = Theme.get();
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int n = count();
        g.setFont(t.ui(Theme.Size.CAPTION));
        FontMetrics fm = g.getFontMetrics();
        // legend
        int lx = 0;
        for (Series s : series) {
            g.setColor(s.color);
            g.fillRoundRect(lx, t.px(3), t.u(3), t.px(4), 2, 2);
            g.setColor(t.muted);
            g.drawString(s.name, lx + t.u(4), fm.getAscent());
            lx += t.u(4) + fm.stringWidth(s.name) + t.u(4);
        }
        if (n < 2) {
            g.setColor(t.faint);
            g.setFont(t.ui(Theme.Size.SMALL));
            FontMetrics em = g.getFontMetrics();
            g.drawString(empty, Math.max(0, (getWidth() - em.stringWidth(empty)) / 2), getHeight() / 2);
            g.dispose();
            return;
        }
        double[] ax = axis();
        int x0 = left(), x1 = getWidth() - right(), y0 = top(), y1 = getHeight() - bottom();
        java.util.function.DoubleUnaryOperator Y = v -> y1 - (v - ax[0]) / (ax[1] - ax[0]) * (y1 - y0);
        double step = (x1 - x0) / (double) (n - 1);
        // grid
        for (double v = ax[0]; v <= ax[1] + 1e-9; v += ax[2]) {
            int y = (int) Math.round(Y.applyAsDouble(v));
            g.setColor(Theme.alpha(t.border, 0.7));
            g.drawLine(x0, y, x1, y);
            g.setColor(t.faint);
            String s = format.apply(v);
            g.drawString(s, x0 - fm.stringWidth(s) - t.u(2), y + fm.getAscent() / 2 - 1);
        }
        if (reference != null) {
            int y = (int) Math.round(Y.applyAsDouble(reference));
            g.setColor(t.borderStrong);
            g.setStroke(new BasicStroke((float) t.scale, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 4}, 0));
            g.drawLine(x0, y, x1, y);
            if (referenceLabel != null) { g.setColor(t.faint); g.drawString(referenceLabel, x0 + t.u(1), y - t.px(3)); }
        }
        // x labels: first, middle, last
        g.setColor(t.faint);
        for (int i : new int[]{0, (n - 1) / 2, n - 1}) {
            if (i >= xLabels.size()) continue;
            String s = xLabels.get(i);
            int x = (int) Math.round(x0 + i * step) - fm.stringWidth(s) / 2;
            g.drawString(s, Math.max(0, Math.min(getWidth() - fm.stringWidth(s), x)), getHeight() - t.px(2));
        }
        // series
        g.setStroke(new BasicStroke((float) (2.25 * t.scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        List<int[]> ends = new ArrayList<>();
        for (Series s : series) {
            if (s.values.isEmpty()) continue;
            Path2D p = new Path2D.Double();
            for (int i = 0; i < s.values.size(); i++) {
                double x = x0 + i * step, y = Y.applyAsDouble(s.values.get(i));
                if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
            }
            g.setColor(s.color);
            g.draw(p);
            int last = s.values.size() - 1;
            int ex = (int) Math.round(x0 + last * step), ey = (int) Math.round(Y.applyAsDouble(s.values.get(last)));
            g.fillOval(ex - t.px(3), ey - t.px(3), t.px(6), t.px(6));
            ends.add(new int[]{ex, ey, series.indexOf(s)});
        }
        // end labels, nudged apart
        ends.sort((a, b) -> Integer.compare(a[1], b[1]));
        int prev = Integer.MIN_VALUE;
        g.setFont(t.bold(Theme.Size.CAPTION));
        fm = g.getFontMetrics();
        for (int[] e : ends) {
            Series s = series.get(e[2]);
            int y = Math.max(e[1] + fm.getAscent() / 2, prev + fm.getHeight());
            g.setColor(s.color);
            g.drawString(format.apply(s.values.get(s.values.size() - 1)), e[0] + t.u(2), y);
            prev = y;
        }
        if (hover >= 0) {
            int x = (int) Math.round(x0 + hover * step);
            g.setColor(Theme.alpha(t.text, 0.35));
            g.setStroke(new BasicStroke(1f));
            g.drawLine(x, y0, x, y1);
        }
        g.dispose();
    }

    /** What the chart shows, in words: each series' first and latest value. */
    public String summary() {
        StringBuilder b = new StringBuilder();
        for (Series s : series) {
            if (s.values.isEmpty()) continue;
            if (b.length() > 0) b.append("; ");
            b.append(s.name).append(" from ").append(format.apply(s.values.get(0))).append(" to ").append(format.apply(s.values.get(s.values.size() - 1)));
        }
        return b.length() == 0 ? empty : b.toString();
    }

    @Override public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
            @Override public AccessibleRole getAccessibleRole() { return AccessibleRole.LABEL; }
            @Override public String getAccessibleName() { return "Chart: " + summary(); }
        };
        return accessibleContext;
    }
}
