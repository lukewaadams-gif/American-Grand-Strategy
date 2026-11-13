package game.mandate.core.presidency;

import game.mandate.core.data.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the staff governs in watch mode (Resolute Desk's {@code sim/WatchPlan.java}): six priorities from 0 to 1, set
 * by a ready-made plan or by hand, and the kinds of decision watch mode stops for. The middle of every priority is
 * the steady hand. Part of the president's state; changed only through {@link PresidentEngine}.
 */
public final class WatchPlan {
    public static final int AMBITION = 0, BIPARTISAN = 1, HAWK = 2, ORDERS = 3, TRAVEL = 4, CAUTION = 5, PRIORITIES = 6;
    public static final List<String> PRIORITY = List.of("Legislative ambition", "Bipartisanship", "Foreign policy", "Executive orders", "Travel and campaigning", "Caution");
    public static final List<List<String>> PRIORITY_ENDS = List.of(List.of("One bill at a time", "Four bills at once"), List.of("Party-line bills", "Bills across the aisle"),
            List.of("Dove", "Hawk"), List.of("Rarely", "Every few weeks"), List.of("Stay in Washington", "On the road"), List.of("Takes risks", "Plays it safe"));
    public static final List<String> PRIORITY_DESC = List.of("How many of your bills the staff keeps moving in Congress at once.",
            "Whether it leads with your party's bills, sized to pass on party lines, or with bipartisan ones, trimmed to win votes across the aisle.",
            "How readily it reaches for force in a crisis, and how long it keeps fighting a war that's going badly.",
            "How often it signs an executive order from your party's library.",
            "How often it spends a week on the road around the country.",
            "Whether it takes the sure thing or the gamble with the bigger payoff.");
    public static final List<String> PRESET = List.of("Steady hand", "Big agenda", "Deal-maker", "Hawk", "Dove", "On the road", "Your own");
    public static final List<String> PRESET_DESC = List.of("The cautious middle on everything.",
            "Four bills at once, sized for your own party, and plenty of executive orders.",
            "Bipartisan bills trimmed to pass, few orders, and safe choices.",
            "Force is on the table in every crisis, and wars are fought to the end.",
            "Diplomacy first; wars that go badly end early.",
            "A week on the road every few weeks, and the campaign never stops.",
            "Your own settings.");
    static final double[][] PRESET_VALUES = {{0.5, 0.5, 0.5, 0.5, 0.5, 0.5}, {1, 0.2, 0.5, 0.85, 0.5, 0.4}, {0.5, 1, 0.4, 0.2, 0.5, 0.8},
            {0.5, 0.4, 1, 0.6, 0.5, 0.4}, {0.5, 0.6, 0, 0.5, 0.5, 0.6}, {0.5, 0.5, 0.5, 0.5, 1, 0.5}};
    public static final int CUSTOM = PRESET.size() - 1;
    /** The decisions watch mode can stop for. Only the world (events and crises) exists before the later milestones. */
    public static final int STOP_SIGN = 0, STOP_BUDGET = 1, STOP_WORLD = 2, STOP_COURT = 3, STOP_SCANDAL = 4, STOP_STORY = 5, STOP_CAMPAIGN = 6, STOPS = 7;
    public static final List<String> STOP = List.of("Bills to sign or veto", "Shutdowns and the debt limit", "Crises, war and turning points", "Court rulings",
            "Scandals and impeachment", "Storylines", "The campaign");

    int preset;
    final double[] p = {0.5, 0.5, 0.5, 0.5, 0.5, 0.5};
    final boolean[] stop = new boolean[STOPS];

    WatchPlan() { stop[STOP_WORLD] = true; }

    public int preset() { return preset; }
    public double priority(int i) { return p[i]; }
    public boolean stops(int kind) { return stop[kind]; }

    void use(int k) {
        if (k < 0 || k > CUSTOM) throw new IllegalArgumentException("No such plan");
        preset = k;
        if (k < CUSTOM) System.arraycopy(PRESET_VALUES[k], 0, p, 0, PRIORITIES);
    }

    void set(int i, double v) { p[i] = Math.max(0, Math.min(1, v)); preset = CUSTOM; }

    /** Bills kept moving at once: two in the middle, one to four at the ends. */
    public int activeBills() { return 1 + (int) Math.round(p[AMBITION] * 3 - 0.5 + 1e-9); }
    /** Weeks between executive orders: nine in the middle, every three weeks at most. */
    public int orderWeeks() { return Math.max(3, (int) Math.round(17 - 16 * p[ORDERS])); }
    /** Weeks between road trips: eight in the middle, every second week at most. */
    public int roadWeeks() { return Math.max(2, (int) Math.round(14 - 12 * p[TRAVEL])); }
    /** How much a military option is held back in a crisis: 0.12 in the middle, less for a hawk. */
    public double forcePenalty() { return 0.12 - (p[HAWK] - 0.5) * 0.5; }
    /** How much a sure but passive option is held back: 0.45 in the middle, less for a cautious plan. */
    public double surePenalty() { return 0.45 - (p[CAUTION] - 0.5) * 0.6; }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("preset", preset);
        List<Object> ps = new ArrayList<>();
        for (double v : p) ps.add(v);
        m.put("priorities", ps);
        List<Object> st = new ArrayList<>();
        for (boolean b : stop) st.add(b);
        m.put("stops", st);
        return m;
    }

    static WatchPlan fromJson(Map<String, Object> m) {
        WatchPlan w = new WatchPlan();
        w.preset = Json.integer(m.get("preset"));
        List<Object> ps = Json.array(m.get("priorities"));
        for (int i = 0; i < PRIORITIES; i++) w.p[i] = Json.num(ps.get(i));
        List<Object> st = Json.array(m.get("stops"));
        for (int i = 0; i < STOPS; i++) w.stop[i] = (Boolean) st.get(i);
        return w;
    }
}
