package game.mandate.web.kit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** One of a few (bill size, spending rate, a filter): a row of toggles; the arrow keys move the choice. */
public class Segmented extends El {
    private final List<Btn> buttons = new ArrayList<>();
    private int selected;

    public Segmented(List<String> labels, List<String> tips, int selected, IntConsumer onChange) {
        super("div");
        Theme t = Theme.get();
        cls("segmented wrap");
        css("gap", t.u(1) + "px " + t.u(1) + "px");
        role("group");
        this.selected = selected;
        for (int i = 0; i < labels.size(); i++) {
            int k = i;
            Btn b = Btn.secondary(labels.get(i), e -> { if (k != this.selected) { this.selected = k; onChange.accept(k); } });
            if (tips != null && i < tips.size() && tips.get(i) != null) b.tip(tips.get(i));
            b.selected(i == selected);
            b.on("keydown", ev -> {
                String key = Dom.key(ev);
                int step = key.equals("ArrowRight") || key.equals("ArrowDown") ? 1 : key.equals("ArrowLeft") || key.equals("ArrowUp") ? -1 : 0;
                if (step == 0) return;
                Dom.prevent(ev);
                int n = Math.floorMod(k + step, buttons.size());
                buttons.get(n).focus();
                buttons.get(n).doClick();
            });
            buttons.add(b);
            add(b);
        }
    }

    public int selected() { return selected; }
    public List<Btn> buttons() { return List.copyOf(buttons); }
}
