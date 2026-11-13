package game.mandate.core.presidency;

import game.mandate.core.data.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The president as a person, and the president's week (Resolute Desk's {@code model.Game} fields for both): the 21
 * slots of the calendar (seven days, morning, afternoon and evening, from Monday), stamina, sleep, stress, health and
 * fitness, illness, where the president is, the chief of staff's hand on the calendar, the watch plan, and what the
 * week banks for the systems that use it (the party's war chest, hours and goodwill on the Hill, campaign stops before
 * the midterms). Owned by {@link PresidentEngine}.
 */
public final class PresidentState {
    public static final int SLOTS = 21;

    final Activity[] act = new Activity[SLOTS];
    /** Each slot's argument: an issue's name, a state's code or a country's id, or null. */
    final String[] arg = new String[SLOTS];
    double stamina = 85, sleep = 6.5, stress = 25, health = 92, fitness = 50;
    int sick;
    String location = "DC";
    int age;
    /** Weeks in office played so far. */
    int week;
    int lastCabinet = -99;
    /** The chief of staff writes each week's calendar. */
    boolean delegate;
    WatchPlan plan = new WatchPlan();
    /** Money raised for the party, $M (Resolute Desk's {@code funds}). */
    double warChest;
    /** Relationships on the Hill, 0 to 100 (Resolute Desk's {@code hill}); Congress reads it from milestone 4. */
    double hill = 50;
    /** Hours on the Hill this week: 2.5 a meeting; Congress spends them from milestone 4. */
    double hillHours;
    /** Cabinet meetings on last week's calendar (the West Wing reads them). */
    int cabinetMeetings;
    /** Campaign stops for the party before the midterms, by state; the midterm model reads them from milestone 4. */
    final Map<String, Double> midtermEffort = new TreeMap<>();
    /** What last week came to, for the calendar and the paper. */
    final List<String> lastWeek = new ArrayList<>();
    /** The states the president visited last week (trips and campaign stops). */
    final List<String> lastVisited = new ArrayList<>();
    double lastRaised;
    double lastStaminaChange;

    PresidentState() {
        for (int i = 0; i < SLOTS; i++) act[i] = Activity.FREE;
    }

    public Activity activity(int slot) { return act[slot]; }
    public String argument(int slot) { return arg[slot]; }
    public double stamina() { return stamina; }
    public double sleep() { return sleep; }
    public double stress() { return stress; }
    public double health() { return health; }
    public double fitness() { return fitness; }
    public int sickWeeks() { return sick; }
    public String location() { return location; }
    public int age() { return age; }
    public int week() { return week; }
    public int lastCabinet() { return lastCabinet; }
    public boolean delegated() { return delegate; }
    public WatchPlan plan() { return plan; }
    public double warChest() { return warChest; }
    public double hill() { return hill; }
    public double hillHours() { return hillHours; }
    public int cabinetMeetings() { return cabinetMeetings; }
    public Map<String, Double> midtermEffort() { return Collections.unmodifiableMap(midtermEffort); }
    public List<String> lastWeek() { return List.copyOf(lastWeek); }
    public List<String> lastVisited() { return List.copyOf(lastVisited); }
    public double lastRaised() { return lastRaised; }
    public double lastStaminaChange() { return lastStaminaChange; }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Object> a = new ArrayList<>(), g = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) { a.add(act[i].name()); g.add(arg[i]); }
        m.put("act", a); m.put("arg", g);
        m.put("stamina", stamina); m.put("sleep", sleep); m.put("stress", stress); m.put("health", health); m.put("fitness", fitness);
        m.put("sick", sick); m.put("location", location); m.put("age", age); m.put("week", week); m.put("lastCabinet", lastCabinet);
        m.put("delegate", delegate); m.put("plan", plan.toJson()); m.put("warChest", warChest); m.put("hill", hill); m.put("hillHours", hillHours); m.put("cabinetMeetings", cabinetMeetings);
        m.put("midtermEffort", new LinkedHashMap<String, Object>(midtermEffort));
        m.put("lastWeek", new ArrayList<Object>(lastWeek)); m.put("lastVisited", new ArrayList<Object>(lastVisited)); m.put("lastRaised", lastRaised); m.put("lastStaminaChange", lastStaminaChange);
        return m;
    }

    public static PresidentState fromJson(Map<String, Object> m) {
        PresidentState s = new PresidentState();
        List<Object> a = Json.array(m.get("act")), g = Json.array(m.get("arg"));
        for (int i = 0; i < SLOTS; i++) { s.act[i] = Activity.valueOf((String) a.get(i)); s.arg[i] = (String) g.get(i); }
        s.stamina = Json.num(m.get("stamina")); s.sleep = Json.num(m.get("sleep")); s.stress = Json.num(m.get("stress"));
        s.health = Json.num(m.get("health")); s.fitness = Json.num(m.get("fitness"));
        s.sick = Json.integer(m.get("sick")); s.location = (String) m.get("location"); s.age = Json.integer(m.get("age"));
        s.week = Json.integer(m.get("week")); s.lastCabinet = Json.integer(m.get("lastCabinet"));
        s.delegate = (Boolean) m.get("delegate"); s.plan = WatchPlan.fromJson(Json.object(m.get("plan")));
        s.warChest = Json.num(m.get("warChest")); s.hill = Json.num(m.get("hill")); s.hillHours = Json.num(m.get("hillHours")); s.cabinetMeetings = m.get("cabinetMeetings") == null ? 0 : Json.integer(m.get("cabinetMeetings"));
        Json.object(m.get("midtermEffort")).forEach((k, v) -> s.midtermEffort.put(k, Json.num(v)));
        for (Object o : Json.array(m.get("lastWeek"))) s.lastWeek.add((String) o);
        if (m.containsKey("lastVisited")) for (Object o : Json.array(m.get("lastVisited"))) s.lastVisited.add((String) o);
        s.lastRaised = Json.num(m.get("lastRaised")); s.lastStaminaChange = Json.num(m.get("lastStaminaChange"));
        return s;
    }
}
