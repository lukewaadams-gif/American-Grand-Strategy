package game.mandate.ui.kit;

import javax.swing.JPanel;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A single choice among a few options, shown all at once (size: small / medium / large; spending rate). The arrow
 * keys move the choice when the control has focus. Options wrap onto new lines on narrow windows.
 */
public class Segmented extends JPanel implements Layouts.HeightForWidth {
    private final List<Btn> buttons = new ArrayList<>();
    private int selected;

    public Segmented(List<String> labels, List<String> tips, int selected, IntConsumer onChange) {
        super(new Layouts.Wrap(Theme.get().u(1), Theme.get().u(1)));
        setOpaque(false);
        this.selected = selected;
        for (int i = 0; i < labels.size(); i++) {
            int k = i;
            Btn b = Btn.secondary(labels.get(i), e -> { select(k); onChange.accept(k); });
            if (tips != null && tips.get(i) != null) b.tip(tips.get(i));
            b.selected(i == selected);
            b.addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    int d = e.getKeyCode() == KeyEvent.VK_RIGHT ? 1 : e.getKeyCode() == KeyEvent.VK_LEFT ? -1 : 0;
                    if (d == 0) return;
                    int n = Math.floorMod(k + d, buttons.size());
                    buttons.get(n).requestFocusInWindow();
                    buttons.get(n).doClick(0);
                }
            });
            buttons.add(b);
            add(b);
        }
    }

    public void select(int i) {
        selected = i;
        for (int j = 0; j < buttons.size(); j++) buttons.get(j).selected(j == i);
    }

    public int selected() { return selected; }
    public List<Btn> buttons() { return buttons; }

    @Override public int heightFor(int width) { return ((Layouts.Wrap) getLayout()).heightFor(this, width); }
}
