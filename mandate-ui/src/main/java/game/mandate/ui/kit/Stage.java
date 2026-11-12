package game.mandate.ui.kit;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.util.ArrayList;
import java.util.List;

/**
 * The window's layers: the screen, toasts above it, and one modal above everything. A modal dims the screen,
 * takes the keyboard (Escape closes it when it may be closed) and returns focus when it goes. Toasts report
 * important events at the corner and fade after a few seconds; every toast is also in the timeline.
 */
public class Stage extends JLayeredPane {
    private JComponent base;
    private final JPanel toasts;
    private JComponent modal;
    private Runnable onModalClosed;
    private boolean reduceMotion;
    private final List<Timer> timers = new ArrayList<>();

    public Stage() {
        toasts = new JPanel(new Layouts.Stack(Theme.get().u(2)));
        toasts.setOpaque(false);
        add(toasts, PALETTE_LAYER);
    }

    public void setBase(JComponent c) {
        if (base != null) remove(base);
        base = c;
        add(c, DEFAULT_LAYER);
        revalidate();
        repaint();
    }

    public JComponent base() { return base; }

    public void reduceMotion(boolean on) { reduceMotion = on; }

    // ---- modal ----------------------------------------------------------------------------------------------

    /**
     * Shows {@code content} centred over a dimmed screen.
     * @param closable whether Escape and the backdrop close it (decisions can be closed and reopened: the player
     *                 may consult other screens before choosing)
     */
    public void showModal(JComponent content, int maxWidth, boolean closable, Runnable onClosed) {
        closeModal();
        Theme t = Theme.get();
        JPanel dim = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(new Color(4, 8, 18, 200));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            @Override public void doLayout() {
                int w = Math.min(maxWidth, getWidth() - t.u(8));
                JScrollPane sp = (JScrollPane) getComponent(0);
                int h = Math.min(getHeight() - t.u(8), Layouts.measure(content, w - t.u(2)) + t.u(2));
                sp.setBounds((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
            }
        };
        dim.setOpaque(false);
        dim.addMouseListener(new MouseAdapter() { });     // the screen below is not clickable while a modal is up
        JScrollPane sp = Ui.scroll(content);
        dim.add(sp);
        if (closable) {
            dim.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "close");
            dim.getActionMap().put("close", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { closeModal(); } });
        }
        modal = dim;
        onModalClosed = onClosed;
        add(dim, MODAL_LAYER);
        doLayout();
        dim.doLayout();
        revalidate();
        repaint();
        SwingUtilities.invokeLater(() -> Ui.focusFirst(content));
    }

    public boolean modalOpen() { return modal != null; }

    public JComponent modal() { return modal; }

    public void closeModal() {
        if (modal == null) return;
        remove(modal);
        modal = null;
        revalidate();
        repaint();
        Runnable r = onModalClosed;
        onModalClosed = null;
        if (r != null) r.run();
    }

    // ---- toasts ---------------------------------------------------------------------------------------------

    public void toast(String title, String body, Color tone) {
        Theme t = Theme.get();
        Card c = new Card(null).stripe(tone).fill(t.raised).stroke(t.borderStrong);
        c.with(Text.strong(title, Theme.Size.BODY));
        if (body != null && !body.isBlank()) c.with(Text.muted(body).lines(3));
        c.getAccessibleContext().setAccessibleName(title + (body == null ? "" : ". " + body));
        toasts.add(c, 0);
        while (toasts.getComponentCount() > 4) toasts.remove(toasts.getComponentCount() - 1);
        doLayout();
        repaint();
        Timer timer = new Timer(reduceMotion ? 9000 : 6500, e -> { toasts.remove(c); doLayout(); repaint(); });
        timer.setRepeats(false);
        timer.start();
        timers.add(timer);
    }

    public List<Component> toastsShown() { return List.of(toasts.getComponents()); }

    @Override public void doLayout() {
        Theme t = Theme.get();
        if (base != null) base.setBounds(0, 0, getWidth(), getHeight());
        int w = Math.min(t.u(96), getWidth() / 3);
        int h = Layouts.measure(toasts, w);
        toasts.setBounds(getWidth() - w - t.u(6), getHeight() - h - t.u(6), w, h);
        toasts.doLayout();
        if (modal != null) { modal.setBounds(0, 0, getWidth(), getHeight()); modal.doLayout(); }
    }

    @Override public Dimension getPreferredSize() { return base == null ? new Dimension(1280, 720) : base.getPreferredSize(); }
}
