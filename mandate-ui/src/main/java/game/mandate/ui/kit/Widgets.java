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
import java.util.List;

/** Small data displays. Each states its value in text as well as shape, and names itself to assistive technology. */
public final class Widgets {
    private Widgets() {}

    static Graphics2D smooth(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    /** A painted component with a fixed preferred height and an accessible description of what it shows. */
    abstract static class Painted extends JComponent {
        String description = "";
        Painted() { setOpaque(false); }
        void describe(String s) { description = s; getAccessibleContext().setAccessibleDescription(s); }

        @Override public AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
                @Override public AccessibleRole getAccessibleRole() { return AccessibleRole.LABEL; }
                @Override public String getAccessibleName() { return description; }
            };
            return accessibleContext;
        }
    }

    // ---- KPI ------------------------------------------------------------------------------------------------

    /** A headline number: an uppercase label, the value, and a coloured line under it (the change, the context). */
    public static final class Kpi extends Painted {
        final String label, value, sub;
        final Color subColor;

        public Kpi(String label, String value, String sub, Color subColor, String explain) {
            this.label = label;
            this.value = value;
            this.sub = sub;
            this.subColor = subColor == null ? Theme.get().faint : subColor;
            if (explain != null) setToolTipText(explain);
            describe(label + ": " + value + (sub == null ? "" : ", " + sub));
        }

        @Override public Dimension getPreferredSize() {
            Theme t = Theme.get();
            FontMetrics l = getFontMetrics(t.bold(Theme.Size.CAPTION)), v = getFontMetrics(t.bold(Theme.Size.KPI)), s = getFontMetrics(t.ui(Theme.Size.CAPTION));
            int w = Math.max(l.stringWidth(label.toUpperCase()) + t.u(2), Math.max(v.stringWidth(value), sub == null ? 0 : s.stringWidth(sub)));
            return new Dimension(w, l.getHeight() + v.getHeight() + (sub == null ? 0 : s.getHeight()));
        }

        @Override protected void paintComponent(Graphics g0) {
            Theme t = Theme.get();
            Graphics2D g = smooth(g0);
            g.setFont(t.bold(Theme.Size.CAPTION));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(t.faint);
            g.drawString(label.toUpperCase(), 0, fm.getAscent());
            int y = fm.getHeight();
            g.setFont(t.bold(Theme.Size.KPI));
            fm = g.getFontMetrics();
            g.setColor(t.text);
            g.drawString(value, 0, y + fm.getAscent());
            y += fm.getHeight();
            if (sub != null) {
                g.setFont(t.ui(Theme.Size.CAPTION));
                fm = g.getFontMetrics();
                g.setColor(subColor);
                g.drawString(sub, 0, y + fm.getAscent() - t.px(2));
            }
            g.dispose();
        }
    }

    // ---- meter -----------------------------------------------------------------------------------------------

    /** A horizontal bar for a 0..max reading, with the label left and the value right. */
    public static final class Meter extends Painted {
        final String label, valueText;
        final double value, max;
        final Color color;
        Double marker;

        public Meter(String label, double value, double max, String valueText, Color color, String explain) {
            this.label = label;
            this.value = value;
            this.max = max;
            this.valueText = valueText;
            this.color = color;
            if (explain != null) setToolTipText(explain);
            describe(label + ": " + valueText);
        }

        /** A tick at a threshold (a revolt line, the votes needed). */
        public Meter marker(double v) { marker = v; return this; }

        @Override public Dimension getPreferredSize() {
            Theme t = Theme.get();
            return new Dimension(t.u(40), getFontMetrics(t.ui(Theme.Size.SMALL)).getHeight() + t.u(3));
        }

        @Override protected void paintComponent(Graphics g0) {
            Theme t = Theme.get();
            Graphics2D g = smooth(g0);
            g.setFont(t.ui(Theme.Size.SMALL));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(t.muted);
            g.drawString(label, 0, fm.getAscent());
            g.setFont(t.bold(Theme.Size.SMALL));
            FontMetrics vm = g.getFontMetrics();
            g.setColor(t.text);
            g.drawString(valueText, getWidth() - vm.stringWidth(valueText), fm.getAscent());
            int y = fm.getHeight() + t.px(4), h = t.px(6), w = getWidth();
            g.setColor(t.sunken);
            g.fillRoundRect(0, y, w, h, h, h);
            int fillW = (int) Math.round(w * Math.max(0, Math.min(1, value / max)));
            g.setColor(color);
            g.fillRoundRect(0, y, Math.max(h, fillW), h, h, h);
            if (marker != null) {
                int mx = (int) Math.round(w * marker / max);
                g.setColor(t.text);
                g.fillRect(mx - 1, y - t.px(2), 2, h + t.px(4));
            }
            g.dispose();
        }
    }

    // ---- pips ------------------------------------------------------------------------------------------------

    /** Attention points: filled for each one left this turn. */
    public static final class Pips extends Painted {
        final int filled, total;
        public Pips(int filled, int total, String explain) {
            this.filled = filled;
            this.total = total;
            if (explain != null) setToolTipText(explain);
            describe(filled + " of " + total + " attention points left");
        }

        @Override public Dimension getPreferredSize() { Theme t = Theme.get(); return new Dimension(total * t.u(5), t.u(4)); }

        @Override protected void paintComponent(Graphics g0) {
            Theme t = Theme.get();
            Graphics2D g = smooth(g0);
            int d = t.u(3);
            for (int i = 0; i < total; i++) {
                int x = i * t.u(5), y = (getHeight() - d) / 2;
                if (i < filled) { g.setColor(t.accent); g.fillOval(x, y, d, d); }
                else { g.setColor(t.borderStrong); g.setStroke(new BasicStroke((float) (1.5 * t.scale))); g.drawOval(x, y, d, d); }
            }
            g.dispose();
        }
    }

    // ---- tag -------------------------------------------------------------------------------------------------

    /** A status pill: a word in a tinted capsule (CONFIRMED, CRISIS, D+2.1). */
    public static final class Tag extends Painted {
        final String text;
        final Color color;
        public Tag(String text, Color color) { this.text = text; this.color = color; describe(text); Layouts.naturalWidth(this); }

        @Override public Dimension getPreferredSize() {
            Theme t = Theme.get();
            FontMetrics fm = getFontMetrics(t.bold(Theme.Size.CAPTION));
            return new Dimension(fm.stringWidth(text) + t.u(4), fm.getHeight() + t.u(1));
        }

        @Override protected void paintComponent(Graphics g0) {
            Theme t = Theme.get();
            Graphics2D g = smooth(g0);
            int h = getHeight();
            g.setColor(t.pillFill(color));
            g.fillRoundRect(0, 0, getWidth() - 1, h - 1, h, h);
            g.setColor(Theme.alpha(color, 0.75));
            g.drawRoundRect(0, 0, getWidth() - 1, h - 1, h, h);
            g.setFont(t.bold(Theme.Size.CAPTION));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(t.text);
            g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    // ---- diverging bar ---------------------------------------------------------------------------------------

    /** A -100..100 reading from a centre line (relations, First Island Chain's diplomacy bars). */
    public static final class Diverging extends Painted {
        final String label, word;
        final double value;
        public Diverging(String label, double value, String word, String explain) {
            this.label = label;
            this.value = value;
            this.word = word;
            if (explain != null) setToolTipText(explain);
            describe(label + ": " + word + String.format(" (%+.0f)", value));
        }

        @Override public Dimension getPreferredSize() {
            Theme t = Theme.get();
            return new Dimension(t.u(60), getFontMetrics(t.ui(Theme.Size.SMALL)).getHeight() + t.u(1));
        }

        @Override protected void paintComponent(Graphics g0) {
            Theme t = Theme.get();
            Graphics2D g = smooth(g0);
            g.setFont(t.ui(Theme.Size.SMALL));
            FontMetrics fm = g.getFontMetrics();
            int labelW = Math.min(getWidth() / 3, t.u(36)), wordW = t.u(24);
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g.setColor(t.text);
            g.drawString(label, 0, y);
            int bx = labelW, bw = getWidth() - labelW - wordW - t.u(2), mid = bx + bw / 2, h = t.px(8), by = (getHeight() - h) / 2;
            g.setColor(t.sunken);
            g.fillRoundRect(bx, by, bw, h, h, h);
            int len = (int) Math.round(bw / 2.0 * Math.min(1, Math.abs(value) / 100.0));
            g.setColor(value >= 0 ? t.info : t.bad);
            if (value >= 0) g.fillRoundRect(mid, by, Math.max(2, len), h, h, h); else g.fillRoundRect(mid - len, by, Math.max(2, len), h, h, h);
            g.setColor(t.borderStrong);
            g.fillRect(mid, by - t.px(2), 1, h + t.px(4));
            g.setColor(value >= 0 ? t.muted : t.bad);
            g.drawString(word, getWidth() - fm.stringWidth(word), y);
            g.dispose();
        }
    }

    // ---- stage tracker ---------------------------------------------------------------------------------------

    /** A path of named stages with the current one marked: a bill through Congress, a crisis up its ladder. */
    public static final class Stages extends Painted implements Layouts.HeightForWidth {
        final List<String> stages;
        final int current;
        final boolean failed;
        final Color doneColor;

        public Stages(List<String> stages, int current, boolean failed, Color doneColor) {
            this.stages = stages;
            this.current = current;
            this.failed = failed;
            this.doneColor = doneColor;
            describe("Stage " + (current + 1) + " of " + stages.size() + ": " + (current >= 0 && current < stages.size() ? stages.get(current) : "not started"));
            setToolTipText(String.join(" \u2192 ", stages));
        }

        /** Each label wraps inside the room between its neighbours, so labels never overlap. */
        List<List<String>> labels(int width, FontMetrics fm) {
            Theme t = Theme.get();
            int n = stages.size(), step = Math.max(t.u(10), (width - t.u(4)) / Math.max(1, n - 1));
            List<List<String>> out = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                int max = n == 1 ? width : i == 0 || i == n - 1 ? step / 2 + t.u(1) : step - t.u(2);
                out.add(wrap(stages.get(i), fm, max));
            }
            return out;
        }

        static List<String> wrap(String s, FontMetrics fm, int max) {
            List<String> lines = new java.util.ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : s.split(" ")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (fm.stringWidth(next) <= max || line.isEmpty()) { line.setLength(0); line.append(next); }
                else { lines.add(line.toString()); line.setLength(0); line.append(word); }
            }
            if (!line.isEmpty()) lines.add(line.toString());
            return lines;
        }

        @Override public int heightFor(int width) {
            Theme t = Theme.get();
            FontMetrics fm = getFontMetrics(t.ui(Theme.Size.CAPTION));
            int lines = labels(Math.max(width, t.u(40)), fm).stream().mapToInt(List::size).max().orElse(1);
            return t.px(2) + t.u(4) + t.u(1) + lines * fm.getHeight() + t.u(1);
        }

        @Override public Dimension getPreferredSize() {
            Theme t = Theme.get();
            int w = getWidth() > 0 ? getWidth() : t.u(80);
            return new Dimension(t.u(80), heightFor(w));
        }

        @Override protected void paintComponent(Graphics g0) {
            Theme t = Theme.get();
            Graphics2D g = smooth(g0);
            int n = stages.size(), d = t.u(4), y = t.px(2), step = (getWidth() - d) / Math.max(1, n - 1);
            g.setStroke(new BasicStroke((float) (2 * t.scale)));
            for (int i = 0; i < n - 1; i++) {
                g.setColor(i < current ? doneColor : t.border);
                g.drawLine(d / 2 + i * step + d / 2, y + d / 2, d / 2 + (i + 1) * step - d / 2, y + d / 2);
            }
            g.setFont(t.ui(Theme.Size.CAPTION));
            FontMetrics fm = g.getFontMetrics();
            List<List<String>> labels = labels(getWidth(), fm);
            for (int i = 0; i < n; i++) {
                int cx = i * step;
                boolean done = i < current, here = i == current;
                Color c = here && failed ? t.bad : done || here ? doneColor : t.border;
                if (done) { g.setColor(c); g.fillOval(cx, y, d, d); }
                else { g.setColor(t.surface); g.fillOval(cx, y, d, d); g.setColor(c); g.drawOval(cx, y, d, d); if (here) g.fillOval(cx + d / 4, y + d / 4, d / 2, d / 2); }
                g.setColor(here ? t.text : t.muted);
                int ly = y + d + t.u(1) + fm.getAscent();
                for (String s : labels.get(i)) {
                    int sw = fm.stringWidth(s);
                    int tx = i == 0 ? 0 : i == n - 1 ? getWidth() - sw : cx + d / 2 - sw / 2;
                    tx = Math.max(0, Math.min(getWidth() - sw, tx));
                    g.drawString(s, tx, ly);
                    ly += fm.getHeight();
                }
            }
            g.dispose();
        }
    }

    // ---- the race to 270 -------------------------------------------------------------------------------------

    /** Battleground 2028's bar: one side's electoral votes, the toss-ups, the other side's, and the 270 line. */
    public static final class RaceBar extends Painted {
        final int dem, toss, rep;
        final String demLabel, repLabel;

        public RaceBar(int dem, int toss, int rep, String demLabel, String repLabel) {
            this.dem = dem;
            this.toss = toss;
            this.rep = rep;
            this.demLabel = demLabel;
            this.repLabel = repLabel;
            describe(demLabel + " " + dem + ", toss-up " + toss + ", " + repLabel + " " + rep + "; 270 to win");
            setToolTipText("Electoral votes by state poll: solid or leaning (more than 2 points), and toss-ups within 2 points. 270 wins.");
        }

        @Override public Dimension getPreferredSize() { Theme t = Theme.get(); return new Dimension(t.u(60), t.u(12)); }

        @Override protected void paintComponent(Graphics g0) {
            Theme t = Theme.get();
            Graphics2D g = smooth(g0);
            int w = getWidth(), h = t.u(7), y = t.u(1);
            double unit = w / 538.0;
            int dw = (int) Math.round(dem * unit), tw = (int) Math.round(toss * unit);
            g.setColor(t.dem);
            g.fillRect(0, y, dw, h);
            g.setColor(t.tossUp);
            g.fillRect(dw, y, tw, h);
            g.setColor(t.rep);
            g.fillRect(dw + tw, y, w - dw - tw, h);
            int mid = (int) Math.round(270 * unit);
            g.setColor(t.text);
            g.fillRect(mid - 1, y - t.px(4), 2, h + t.px(8));
            g.setFont(t.bold(Theme.Size.SMALL));
            FontMetrics fm = g.getFontMetrics();
            int ty = y + (h - fm.getHeight()) / 2 + fm.getAscent();
            g.setColor(t.textOn(t.dem));
            if (dw > fm.stringWidth(dem + "") + t.u(2)) g.drawString(String.valueOf(dem), t.u(1), ty);
            g.setColor(t.textOn(t.rep));
            if (w - dw - tw > fm.stringWidth(rep + "") + t.u(2)) g.drawString(String.valueOf(rep), w - fm.stringWidth(rep + "") - t.u(1), ty);
            g.setFont(t.ui(Theme.Size.CAPTION));
            fm = g.getFontMetrics();
            int ly = y + h + t.u(1) + fm.getAscent();
            g.setColor(t.muted);
            g.drawString(demLabel, 0, ly);
            g.drawString(repLabel, w - fm.stringWidth(repLabel), ly);
            String s = "270 to win";
            g.drawString(s, mid - fm.stringWidth(s) / 2, ly);
            g.dispose();
        }
    }
}
