package game.mandate.ui.kit;

import game.mandate.core.electorate.UsState;

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
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The United States as an equal-tile cartogram (the "statebins" arrangement). Every state, including the small
 * north-eastern ones, gets a tile large enough to label at any window size, and there is no third-party geometry to
 * license. Tiles are coloured by margin (D minus R) and labelled with the state and its value; arrow keys move the
 * selection, Enter picks it, and a hover tooltip names the state.
 */
public class TileMap extends JComponent {
    /** Column and row of each tile. */
    static final Map<String, int[]> GRID = new LinkedHashMap<>();
    static {
        Object[][] g = {
                {"AK", 0, 0}, {"ME", 11, 0},
                {"WI", 6, 1}, {"VT", 10, 1}, {"NH", 11, 1},
                {"WA", 1, 2}, {"ID", 2, 2}, {"MT", 3, 2}, {"ND", 4, 2}, {"MN", 5, 2}, {"IL", 6, 2}, {"MI", 7, 2}, {"NY", 9, 2}, {"MA", 10, 2},
                {"OR", 1, 3}, {"NV", 2, 3}, {"WY", 3, 3}, {"SD", 4, 3}, {"IA", 5, 3}, {"IN", 6, 3}, {"OH", 7, 3}, {"PA", 8, 3}, {"NJ", 9, 3}, {"CT", 10, 3}, {"RI", 11, 3},
                {"CA", 1, 4}, {"UT", 2, 4}, {"CO", 3, 4}, {"NE", 4, 4}, {"MO", 5, 4}, {"KY", 6, 4}, {"WV", 7, 4}, {"VA", 8, 4}, {"MD", 9, 4}, {"DE", 10, 4},
                {"AZ", 2, 5}, {"NM", 3, 5}, {"KS", 4, 5}, {"AR", 5, 5}, {"TN", 6, 5}, {"NC", 7, 5}, {"SC", 8, 5}, {"DC", 9, 5},
                {"OK", 4, 6}, {"LA", 5, 6}, {"MS", 6, 6}, {"AL", 7, 6}, {"GA", 8, 6},
                {"HI", 0, 7}, {"TX", 4, 7}, {"FL", 9, 7}};
        for (Object[] r : g) GRID.put((String) r[0], new int[]{(Integer) r[1], (Integer) r[2]});
    }
    static final int COLS = 12, ROWS = 8;

    private final Map<String, UsState> states = new LinkedHashMap<>();
    private final Map<String, Double> margins;
    private final Function<String, String> label;
    private final Function<String, String> tooltip;
    private Consumer<String> onSelect;
    private String selected, hovered;
    private final String legendLeft, legendRight;
    private final int preferredHeight;

    /**
     * @param margins D minus R margin per state (null value or missing = no data, drawn neutral)
     * @param label   the small second line in a tile ("D+2.1"), or null
     */
    public TileMap(List<UsState> states, Map<String, Double> margins, Function<String, String> label, Function<String, String> tooltip,
                   String legendLeft, String legendRight, int preferredHeight) {
        for (UsState s : states) this.states.put(s.code(), s);
        this.margins = margins;
        this.label = label;
        this.tooltip = tooltip;
        this.legendLeft = legendLeft;
        this.legendRight = legendRight;
        this.preferredHeight = preferredHeight;
        setOpaque(false);
        setFocusable(true);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                String s = at(e.getX(), e.getY());
                if (!java.util.Objects.equals(s, hovered)) { hovered = s; setToolTipText(s == null ? null : tooltip.apply(s)); repaint(); }
            }
            @Override public void mouseExited(MouseEvent e) { hovered = null; repaint(); }
            @Override public void mouseClicked(MouseEvent e) { String s = at(e.getX(), e.getY()); if (s != null) select(s); requestFocusInWindow(); }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                int dx = 0, dy = 0;
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT -> dx = -1; case KeyEvent.VK_RIGHT -> dx = 1; case KeyEvent.VK_UP -> dy = -1; case KeyEvent.VK_DOWN -> dy = 1;
                    case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> { if (selected != null && onSelect != null) onSelect.accept(selected); return; }
                    default -> { return; }
                }
                step(dx, dy);
                e.consume();
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { if (selected == null) selected = "PA"; repaint(); }
            @Override public void focusLost(FocusEvent e) { repaint(); }
        });
    }

    public TileMap onSelect(Consumer<String> c) { onSelect = c; return this; }
    public TileMap selected(String code) { selected = code; return this; }

    void select(String s) { selected = s; repaint(); if (onSelect != null) onSelect.accept(s); }

    void step(int dx, int dy) {
        int[] at = GRID.get(selected == null ? "PA" : selected);
        String best = null;
        double bestD = Double.MAX_VALUE;
        for (var e : GRID.entrySet()) {
            int[] p = e.getValue();
            int ddx = p[0] - at[0], ddy = p[1] - at[1];
            if ((dx != 0 && Integer.signum(ddx) != dx) || (dy != 0 && Integer.signum(ddy) != dy)) continue;
            double d = dx != 0 ? Math.abs(ddx) + 2.5 * Math.abs(ddy) : Math.abs(ddy) + 2.5 * Math.abs(ddx);
            if (d < bestD) { bestD = d; best = e.getKey(); }
        }
        if (best != null) { selected = best; setToolTipText(tooltip.apply(best)); repaint(); }
    }

    int tile() { return Math.max(8, Math.min(getWidth() / COLS, (getHeight() - legendHeight()) / ROWS)); }
    int legendHeight() { return Theme.get().u(9); }
    int originX() { return (getWidth() - tile() * COLS) / 2; }

    String at(int x, int y) {
        int s = tile(), ox = originX();
        for (var e : GRID.entrySet()) {
            int tx = ox + e.getValue()[0] * s, ty = e.getValue()[1] * s;
            if (x >= tx && x < tx + s && y >= ty && y < ty + s) return e.getKey();
        }
        return null;
    }

    @Override public Dimension getPreferredSize() { return new Dimension(Theme.get().u(120), preferredHeight); }

    @Override protected void paintComponent(Graphics g0) {
        Theme t = Theme.get();
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int s = tile(), ox = originX(), gap = Math.max(2, s / 14), r = Math.max(3, s / 7);
        float codeSize = (float) Math.max(9, Math.min(16, s * 0.27)), valSize = (float) Math.max(8, Math.min(13, s * 0.2));
        for (var e : GRID.entrySet()) {
            String code = e.getKey();
            int x = ox + e.getValue()[0] * s, y = e.getValue()[1] * s;
            Double m = margins.get(code);
            Color fill = m == null ? t.raised : t.margin(m);
            g.setColor(fill);
            g.fillRoundRect(x + gap / 2, y + gap / 2, s - gap, s - gap, r, r);
            boolean sel = code.equals(selected), hov = code.equals(hovered);
            if (sel || hov) {
                g.setColor(sel ? t.focus : t.text);
                g.setStroke(new BasicStroke((float) Math.max(2, 2 * t.scale)));
                g.drawRoundRect(x + gap / 2, y + gap / 2, s - gap, s - gap, r, r);
            }
            Color ink = t.textOn(fill);
            g.setColor(ink);
            g.setFont(t.font(Theme.Face.UI_BOLD, (float) (codeSize / t.scale)));
            FontMetrics cm = g.getFontMetrics();
            String lab = label == null ? null : label.apply(code);
            boolean two = lab != null && s >= 34;
            int cy = two ? y + s / 2 - t.px(1) : y + (s + cm.getAscent()) / 2 - t.px(1);
            g.drawString(code, x + (s - cm.stringWidth(code)) / 2, cy);
            if (two) {
                g.setFont(t.font(Theme.Face.UI, (float) (valSize / t.scale)));
                FontMetrics vm = g.getFontMetrics();
                g.drawString(lab, x + (s - vm.stringWidth(lab)) / 2, cy + vm.getHeight());
            }
        }
        // legend: a diverging ramp
        int ly = ROWS * s + t.u(2), lw = Math.min(getWidth() - t.u(4), t.u(80)), lx = (getWidth() - lw) / 2, lh = t.px(8);
        for (int i = 0; i < lw; i++) {
            double v = -15 + 30.0 * i / lw;
            g.setColor(t.margin(v));
            g.fillRect(lx + i, ly, 1, lh);
        }
        g.setFont(t.ui(Theme.Size.CAPTION));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(t.muted);
        g.drawString(legendLeft, lx, ly + lh + fm.getAscent() + t.px(3));
        g.drawString(legendRight, lx + lw - fm.stringWidth(legendRight), ly + lh + fm.getAscent() + t.px(3));
        String mid = "Even";
        g.drawString(mid, lx + (lw - fm.stringWidth(mid)) / 2, ly + lh + fm.getAscent() + t.px(3));
        if (isFocusOwner()) {
            g.setColor(t.focus);
            g.setStroke(new BasicStroke(2f));
            g.drawRect(1, 1, getWidth() - 2, getHeight() - 2);
        }
        g.dispose();
    }

    @Override public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
            @Override public AccessibleRole getAccessibleRole() { return AccessibleRole.PANEL; }
            @Override public String getAccessibleName() {
                return "Map of the states" + (selected == null ? "" : ", selected " + tooltip.apply(selected)) + ". Arrow keys move between states; the table nearby lists the same figures.";
            }
        };
        return accessibleContext;
    }
}
