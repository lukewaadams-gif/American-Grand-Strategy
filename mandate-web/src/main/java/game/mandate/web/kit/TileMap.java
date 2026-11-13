package game.mandate.web.kit;

import game.mandate.core.electorate.UsState;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Equal-area state tiles on a 12×8 grid, filled by margin and labelled with code and margin. Each tile is a button;
 * the map is one Tab stop (a roving focus): the arrow keys move between states, Enter or a click picks one.
 */
public class TileMap extends El {
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

    private final Map<String, El> tiles = new HashMap<>();
    private Consumer<String> onSelect;
    private String focusCode = "PA";

    public TileMap(List<UsState> states, Map<String, Double> margins, Function<String, String> label, Function<String, String> tooltip,
                   String legendLeft, String legendRight, int preferredHeight) {
        super("div");
        Theme t = Theme.get();
        cls("tilemap");
        Map<String, UsState> byCode = new HashMap<>();
        for (UsState s : states) byCode.put(s.code(), s);
        El grid = El.div("tile-grid").css("max-width", Math.round((preferredHeight - t.u(10)) * 1.5) + "px");
        grid.role("group").label("Map of the states. Arrow keys move between states; Enter picks one.");
        for (var e : GRID.entrySet()) {
            String code = e.getKey();
            if (!byCode.containsKey(code)) continue;
            Double m = margins.get(code);
            Color fill = m == null ? t.raised : t.margin(m);
            El b = new El("button").cls("tile").attr("type", "button").attr("tabindex", code.equals(focusCode) ? "0" : "-1")
                    .css("grid-column", String.valueOf(e.getValue()[0] + 1)).css("grid-row", String.valueOf(e.getValue()[1] + 1))
                    .css("background", fill.css()).css("color", t.textOn(fill).css());
            b.add(El.span("tile-code").text(code));
            String l = label == null ? null : label.apply(code);
            if (l != null) b.add(El.span("tile-label").text(l));
            String tipText = tooltip.apply(code);
            b.tip(tipText).label(tipText);
            b.on("click", ev -> pick(code));
            b.on("keydown", ev -> {
                String k = Dom.key(ev);
                int dx = k.equals("ArrowLeft") ? -1 : k.equals("ArrowRight") ? 1 : 0, dy = k.equals("ArrowUp") ? -1 : k.equals("ArrowDown") ? 1 : 0;
                if (dx == 0 && dy == 0) return;
                Dom.prevent(ev);
                step(code, dx, dy);
            });
            tiles.put(code, b);
            grid.add(b);
        }
        add(grid);
        // the legend: the ramp from R+15 to D+15
        StringBuilder stops = new StringBuilder();
        for (int i = 0; i <= 10; i++) stops.append(i == 0 ? "" : ", ").append(t.margin(-15 + 3 * i).css());
        El legend = El.div("tile-legend").css("max-width", Math.round((preferredHeight - t.u(10)) * 1.0) + "px");
        legend.add(El.div("legend-ramp").css("background", "linear-gradient(to right, " + stops + ")"),
                Layouts.side(0, El.span("").text(legendLeft), El.span("legend-mid").text("Even"), El.span("").text(legendRight)));
        add(legend.attr("aria-hidden", "true"));
    }

    public TileMap onSelect(Consumer<String> c) { onSelect = c; return this; }

    public TileMap selected(String code) {
        if (code == null || !tiles.containsKey(code)) return this;
        tiles.values().forEach(b -> b.toggle("selected", false).attr("aria-pressed", null).attr("tabindex", "-1"));
        tiles.get(code).toggle("selected", true).attr("aria-pressed", "true").attr("tabindex", "0");
        focusCode = code;
        return this;
    }

    void pick(String code) { selected(code); if (onSelect != null) onSelect.accept(code); }

    void step(String from, int dx, int dy) {
        int[] at = GRID.get(from);
        String best = null;
        double bestD = Double.MAX_VALUE;
        for (var e : GRID.entrySet()) {
            if (!tiles.containsKey(e.getKey())) continue;
            int[] p = e.getValue();
            int ddx = p[0] - at[0], ddy = p[1] - at[1];
            if ((dx != 0 && Integer.signum(ddx) != dx) || (dy != 0 && Integer.signum(ddy) != dy)) continue;
            double d = dx != 0 ? Math.abs(ddx) + 2.5 * Math.abs(ddy) : Math.abs(ddy) + 2.5 * Math.abs(ddx);
            if (d < bestD) { bestD = d; best = e.getKey(); }
        }
        if (best == null) return;
        tiles.get(from).attr("tabindex", "-1");
        tiles.get(best).attr("tabindex", "0").focus();
        focusCode = best;
    }
}
