package game.mandate.ui.kit;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
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

/**
 * The one button. Variants: PRIMARY (gold, one per screen: the main next step), SECONDARY, QUIET (borderless, for
 * low-emphasis actions) and DANGER. Every button has a visible focus ring for keyboard users. A disabled button
 * always carries the reason as its tooltip ({@link #disabledBecause}); the automated checks fail a disabled button
 * without one, and a button without an action.
 */
public class Btn extends JButton {
    public enum Kind { PRIMARY, SECONDARY, QUIET, DANGER }

    private Kind kind;
    private boolean selected;
    private String hint;

    public Btn(String label, Kind kind, ActionListener action) {
        super(label);
        this.kind = kind;
        if (action != null) addActionListener(action);
        setUI(new Ui());
        setFont(Theme.get().bold(Theme.Size.BODY));
        setFocusPainted(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        getAccessibleContext().setAccessibleName(label);
        game.mandate.ui.kit.Ui.enterPresses(this);
    }

    public static Btn primary(String label, ActionListener a) { return new Btn(label, Kind.PRIMARY, a); }
    public static Btn secondary(String label, ActionListener a) { return new Btn(label, Kind.SECONDARY, a); }
    public static Btn quiet(String label, ActionListener a) { return new Btn(label, Kind.QUIET, a); }
    public static Btn danger(String label, ActionListener a) { return new Btn(label, Kind.DANGER, a); }

    /** Explains the button (shown as a tooltip and given to assistive technology). */
    public Btn tip(String s) {
        setToolTipText(s);
        getAccessibleContext().setAccessibleDescription(s);
        return this;
    }

    /** A keyboard hint drawn at the right edge ("Ctrl+Enter"). */
    public Btn hint(String s) { hint = s; return this; }

    /** Disables the button and says why; enabled again if {@code reason} is null. */
    public Btn disabledBecause(String reason) {
        setEnabled(reason == null);
        if (reason != null) tip(reason);
        return this;
    }

    public Btn selected(boolean on) {
        selected = on;
        getAccessibleContext().setAccessibleDescription(on ? "selected" : getToolTipText());
        repaint();
        return this;
    }

    public boolean isSelectedOption() { return selected; }
    public Kind kind() { return kind; }

    @Override public Dimension getPreferredSize() {
        Theme t = Theme.get();
        FontMetrics fm = getFontMetrics(getFont());
        int w = fm.stringWidth(getText()) + t.u(kind == Kind.QUIET ? 3 : 6);
        if (hint != null) w += getFontMetrics(t.ui(Theme.Size.CAPTION)).stringWidth(hint) + t.u(3);
        return new Dimension(w, Math.max(t.u(9), fm.getHeight() + t.u(4)));
    }

    /** Paints every button variant; shared by toggles in segmented controls. */
    static final class Ui extends BasicButtonUI {
        @Override public void paint(Graphics g0, JComponent c) {
            Btn b = (Btn) c;
            Theme t = Theme.get();
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean on = b.isEnabled(), hover = on && b.getModel().isRollover(), down = on && b.getModel().isPressed();
            float r = t.radius();
            RoundRectangle2D shape = new RoundRectangle2D.Float(1, 1, b.getWidth() - 2, b.getHeight() - 2, r, r);
            Color fill, stroke, fg;
            switch (b.kind) {
                case PRIMARY -> { fill = on ? (down ? t.accent.darker() : hover ? t.accent.brighter() : t.accent) : t.raised; stroke = fill; fg = on ? t.onAccent : t.faint; }
                case DANGER -> { fill = on ? Theme.mix(t.surface, t.bad, down ? 0.45 : hover ? 0.35 : 0.22) : t.raised; stroke = on ? t.bad : t.border; fg = on ? t.text : t.faint; }
                case QUIET -> { fill = hover ? t.raised : new Color(0, 0, 0, 0); stroke = fill; fg = on ? t.muted : t.faint; }
                default -> {
                    fill = b.selected ? Theme.mix(t.surface, t.info, 0.42) : down ? t.border : hover ? Theme.mix(t.raised, t.borderStrong, 0.35) : t.raised;
                    stroke = b.selected ? t.info : t.border;
                    fg = on ? t.text : t.faint;
                }
            }
            g.setColor(fill);
            g.fill(shape);
            g.setColor(stroke);
            g.setStroke(new BasicStroke((float) t.scale));
            g.draw(shape);
            if (b.isFocusOwner()) {
                g.setColor(t.focus);
                g.setStroke(new BasicStroke(2f * (float) t.scale));
                g.draw(new RoundRectangle2D.Float(2, 2, b.getWidth() - 4, b.getHeight() - 4, r, r));
            }
            g.setFont(b.getFont());
            FontMetrics fm = g.getFontMetrics();
            int textW = fm.stringWidth(b.getText());
            int hintW = b.hint == null ? 0 : g.getFontMetrics(t.ui(Theme.Size.CAPTION)).stringWidth(b.hint) + t.u(3);
            boolean left = b.getHorizontalAlignment() == javax.swing.SwingConstants.LEFT;
            int x = left ? t.u(3) : (b.getWidth() - textW - hintW) / 2, y = (b.getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g.setColor(fg);
            g.drawString(b.getText(), x, y);
            if (b.hint != null) {
                g.setFont(t.ui(Theme.Size.CAPTION));
                g.setColor(b.kind == Kind.PRIMARY && on ? Theme.mix(t.onAccent, t.accent, 0.35) : t.faint);
                int hx = left ? b.getWidth() - t.u(3) - (hintW - t.u(3)) : x + textW + t.u(3);
                g.drawString(b.hint, hx, y);
            }
            g.dispose();
        }

        @Override public Dimension getPreferredSize(JComponent c) { return c.getPreferredSize(); }
    }

    /** True if the component is a button a player can press but that does nothing (for the automated checks). */
    public static boolean dead(AbstractButton b) { return b.isVisible() && b.getActionListeners().length == 0 && b.getAction() == null; }
}
