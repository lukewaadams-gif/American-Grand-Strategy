package game.mandate.ui.kit;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.function.Consumer;

/** Shared helpers: scroll panes, text fields, focus, global Swing defaults for the theme. */
public final class Ui {
    private Ui() {}

    /** Applies the theme to Swing's built-in pieces (tooltips, scroll bars, text fields). Call after {@link Theme#set}. */
    public static void installDefaults() {
        Theme t = Theme.get();
        UIManager.put("ToolTip.background", t.raised);
        UIManager.put("ToolTip.foreground", t.text);
        UIManager.put("ToolTip.font", t.ui(Theme.Size.SMALL));
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(t.borderStrong),
                BorderFactory.createEmptyBorder(t.u(2), t.u(3), t.u(2), t.u(3))));
        UIManager.put("Panel.background", t.bg);
        UIManager.put("Viewport.background", t.bg);
        UIManager.put("ScrollPane.background", t.bg);
        UIManager.put("TextField.background", t.sunken);
        UIManager.put("TextField.foreground", t.text);
        UIManager.put("TextField.caretForeground", t.text);
        UIManager.put("TextField.selectionBackground", Theme.mix(t.surface, t.info, 0.5));
        UIManager.put("TextField.font", t.ui(Theme.Size.BODY));
        javax.swing.ToolTipManager.sharedInstance().setInitialDelay(350);
        javax.swing.ToolTipManager.sharedInstance().setDismissDelay(15000);
    }

    /** A vertical scroll pane whose content always takes the viewport's width (wrapped in a page if it is not one). */
    public static JScrollPane scroll(Component view) {
        if (!(view instanceof javax.swing.Scrollable)) {
            Layouts.Page page = new Layouts.Page(0);
            page.add(view);
            view = page;
        }
        JScrollPane sp = new JScrollPane(view, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(Theme.get().u(6));
        sp.getVerticalScrollBar().setUI(new ThinBar());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(Theme.get().u(3), 0));
        sp.getVerticalScrollBar().setOpaque(false);
        return sp;
    }

    /** A slim themed scroll bar. */
    static final class ThinBar extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() { thumbColor = Theme.get().borderStrong; trackColor = new Color(0, 0, 0, 0); }
        @Override protected javax.swing.JButton createDecreaseButton(int o) { return zero(); }
        @Override protected javax.swing.JButton createIncreaseButton(int o) { return zero(); }
        /** The scroll bar's arrow buttons: none (scrolling is by wheel, drag, keyboard and the thumb). */
        static javax.swing.JButton zero() {
            javax.swing.JButton b = new javax.swing.JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setFocusable(false);
            b.setVisible(false);
            return b;
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }
        @Override protected void paintThumb(Graphics g0, JComponent c, Rectangle r) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(thumbColor);
            g.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, r.width - 4, r.width - 4);
            g.dispose();
        }
    }

    /** A themed single-line text field that reports every change. */
    public static JTextField field(String value, String accessibleName, Consumer<String> onChange) {
        Theme t = Theme.get();
        JTextField f = new JTextField(value);
        f.setFont(t.ui(Theme.Size.BODY));
        f.setForeground(t.text);
        f.setBackground(t.sunken);
        f.setCaretColor(t.text);
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(t.borderStrong), BorderFactory.createEmptyBorder(t.u(2), t.u(2), t.u(2), t.u(2))));
        f.getAccessibleContext().setAccessibleName(accessibleName);
        f.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void fire() { onChange.accept(f.getText()); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { fire(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { fire(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { fire(); }
        });
        return f;
    }

    /** A line of content with a control at its right end; the content wraps, the control keeps its size. */
    public static JPanel border(Component center, Component east) {
        JPanel p = new JPanel(new Layouts.Side(Theme.get().u(3)));
        p.setOpaque(false);
        if (center != null) p.add(center, Layouts.Side.CENTER);
        if (east != null) p.add(east, Layouts.Side.EAST);
        return p;
    }

    /** Enter presses a focused button as Space does (Swing's own default is Space only). */
    public static void enterPresses(javax.swing.AbstractButton b) {
        javax.swing.InputMap m = b.getInputMap(javax.swing.JComponent.WHEN_FOCUSED);
        m.put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "pressed");
        m.put(javax.swing.KeyStroke.getKeyStroke("released ENTER"), "released");
    }

    public static void focusFirst(Component c) {
        if (c.isFocusable() && c.isShowing() && c instanceof javax.swing.AbstractButton) { c.requestFocusInWindow(); return; }
        if (c instanceof Container ct) for (Component k : ct.getComponents()) {
            if (k.isFocusable() && k.isShowing() && (k instanceof javax.swing.AbstractButton || k instanceof JTextField)) { k.requestFocusInWindow(); return; }
            focusFirst(k);
            if (k instanceof Container && javax.swing.FocusManager.getCurrentManager().getFocusOwner() != null
                    && SwingUtilitiesIsDescendant(k)) return;
        }
    }

    private static boolean SwingUtilitiesIsDescendant(Component k) {
        Component f = javax.swing.FocusManager.getCurrentManager().getFocusOwner();
        return f != null && javax.swing.SwingUtilities.isDescendingFrom(f, k);
    }

    /** A spacer of fixed height. */
    public static JComponent gap(int h) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(1, h));
        return p;
    }
}
