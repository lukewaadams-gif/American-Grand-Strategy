package game.mandate.ui;

import game.mandate.ui.kit.Btn;
import game.mandate.ui.kit.Text;
import game.mandate.ui.kit.Theme;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Automated interface checks run on a laid-out component tree (headless is fine):
 * <ul>
 * <li>nothing is clipped by its parent, no scrolling content is wider than its scroll area, and no text is cut off
 * without a tooltip carrying it in full;</li>
 * <li>no text is smaller than 12 points at 100% scale;</li>
 * <li>every visible button does something, has an accessible name, and says why when it is disabled;</li>
 * <li>the screen has something to focus, so it can be used from the keyboard.</li>
 * </ul>
 * They catch mechanical faults. They cannot judge whether a screen is clear or attractive; that is listed for human
 * review in {@code UI_DESIGN.md}.
 */
public final class UiChecks {
    private UiChecks() {}

    /** Lays out the whole tree at its current size (twice: wrapped text settles on the second pass). */
    public static void layout(Component c) {
        for (int pass = 0; pass < 2; pass++) layoutOnce(c);
    }

    static void layoutOnce(Component c) {
        if (c instanceof Container ct) {
            ct.doLayout();
            for (Component k : ct.getComponents()) layoutOnce(k);
        }
    }

    public static BufferedImage render(JComponent root, int w, int h) {
        root.setSize(w, h);
        layout(root);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(Theme.get().bg);
        g.fillRect(0, 0, w, h);
        root.printAll(g);
        g.dispose();
        return img;
    }

    public static List<String> check(Component root) {
        List<String> out = new ArrayList<>();
        walk(root, root, out, false);
        if (!hasFocusable(root)) out.add("nothing on the screen can take keyboard focus");
        return out;
    }

    static void walk(Component root, Component c, List<String> out, boolean inViewport) {
        if (!c.isVisible()) return;
        Theme t = Theme.get();
        String where = path(root, c);
        Container parent = c.getParent();
        if (parent != null && parent != root && !(parent instanceof JViewport) && !(parent instanceof JLayeredPane) && !(parent instanceof JScrollPane) && c.getWidth() > 0) {
            Rectangle b = c.getBounds();
            if (b.x < -1 || b.y < -1 || b.x + b.width > parent.getWidth() + 1 || b.y + b.height > parent.getHeight() + 1)
                out.add("clipped by its parent: " + where + " " + b + " in " + parent.getWidth() + "x" + parent.getHeight());
        }
        if (c instanceof JViewport vp && vp.getView() != null && vp.getWidth() > 0 && vp.getView().getWidth() > vp.getWidth() + 1)
            out.add("content wider than its scroll area (" + vp.getView().getWidth() + " in " + vp.getWidth() + ") at " + where);
        if (c instanceof Text tx && c.getWidth() > 0 && tx.truncated() && (tx.getToolTipText() == null || tx.getToolTipText().isBlank()))
            out.add("text cut off without a tooltip: \"" + abbreviate(tx.getText()) + "\" at " + where);
        if (c instanceof JComponent j && j.getFont() != null && (c instanceof Text || c instanceof AbstractButton)) {
            float min = (float) (12 * t.scale) - 0.5f;
            if (j.getFont().getSize2D() < min) out.add("font below 12 pt: " + j.getFont().getSize2D() + " at " + where);
        }
        if (c instanceof AbstractButton b && c.getWidth() > 0) {
            String name = b.getAccessibleContext().getAccessibleName();
            if (Btn.dead(b)) out.add("button does nothing: \"" + b.getText() + "\" at " + where);
            if (name == null || name.isBlank()) out.add("button has no accessible name at " + where);
            if (!b.isEnabled() && (b.getToolTipText() == null || b.getToolTipText().isBlank())) out.add("disabled without a reason: \"" + b.getText() + "\" at " + where);
        }
        if (c instanceof Container ct) for (Component k : ct.getComponents()) walk(root, k, out, inViewport || c instanceof JViewport);
    }

    static boolean hasFocusable(Component c) {
        if (c.isVisible() && c.isFocusable() && (c instanceof AbstractButton || c instanceof javax.swing.text.JTextComponent || c instanceof game.mandate.ui.kit.TileMap)) return true;
        if (c instanceof Container ct) for (Component k : ct.getComponents()) if (hasFocusable(k)) return true;
        return false;
    }

    static String path(Component root, Component c) {
        StringBuilder b = new StringBuilder();
        for (Component x = c; x != null && x != root; x = x.getParent()) {
            String n = x instanceof JComponent j && j.getAccessibleContext().getAccessibleName() != null ? j.getAccessibleContext().getAccessibleName() : x.getClass().getSimpleName();
            b.insert(0, "/" + abbreviate(n));
        }
        return b.toString();
    }

    static String abbreviate(String s) { return s == null ? "" : s.length() > 40 ? s.substring(0, 40) + "…" : s; }
}
