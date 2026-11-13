package game.mandate.core.presidency;

import game.mandate.core.campaign.Issue;
import game.mandate.core.data.Csv;
import game.mandate.core.data.Fmt;
import game.mandate.core.effects.Effect;
import game.mandate.core.electorate.StateCatalog;
import game.mandate.core.electorate.UsState;
import game.mandate.core.news.News;
import game.mandate.core.rng.DeterministicRandomManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The president's week, ported from Resolute Desk Version 2: the calendar's rules ({@code ui/Rooms.choose}), the
 * ready-made weeks ({@code Week.routine}, {@code Rooms.roadWeek} and {@code restWeek}), Air Force One, sleep, stamina,
 * stress, health and illness ({@code Week.resolve} and {@code health}), the chief of staff's hand on the calendar
 * ({@code executive/Delegate}) and the watch plan's weeks ({@code sim/Auto}). What a slot does to opinion leaves as
 * effects for the politics engine; the rest of the week is this engine's own state.
 */
public final class PresidentEngine {
    /** Slots in a week: seven days from Monday, three a day. */
    public static final int SLOTS = PresidentState.SLOTS;
    public static final List<String> DAYS = List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");
    public static final List<String> PARTS = List.of("Morning", "Afternoon", "Evening");

    private final StateCatalog states;
    private final Map<String, double[]> centroid = new TreeMap<>();
    private final Map<String, Integer> ages = new TreeMap<>();

    public PresidentEngine(StateCatalog states) {
        this.states = states;
        for (String[] r : Csv.readResource("/data/state_centroids.csv")) centroid.put(r[0], new double[]{Double.parseDouble(r[1]), Double.parseDouble(r[2])});
        for (String[] r : Csv.readResource("/data/candidate_ages.csv")) ages.put(r[0], Integer.parseInt(r[1]));
    }

    public PresidentState newPresident(String candidateId) {
        PresidentState s = new PresidentState();
        s.age = ages.getOrDefault(candidateId, 60);
        return s;
    }

    // ---- Air Force One ------------------------------------------------------------------------------------------------

    /** Great-circle miles from Washington. */
    public double miles(String state) {
        double[] a = centroid.get("DC"), b = centroid.get(state);
        if (a == null || b == null) return 0;
        double r = Math.PI / 180, dLat = (b[0] - a[0]) * r, dLon = (b[1] - a[1]) * r;
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(a[0] * r) * Math.cos(b[0] * r) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * 3958.8 * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** Flight time from Washington in hours: Resolute Desk's rule (0.3 hours under 60 miles, else 0.6 plus 480 mph), Hawaii 10.5, Alaska 8.5. */
    public double flight(String state) {
        if (state == null) return 0;
        if (state.equals("HI")) return 10.5;
        if (state.equals("AK")) return 8.5;
        double m = miles(state);
        return m < 60 ? 0.3 : 0.6 + m / 480;
    }

    double ageFactor(PresidentState s) { return 1 + Math.max(0, s.age - 60) * 0.012; }

    // ---- the calendar's rules ---------------------------------------------------------------------------------------------

    public static boolean weekend(int slot) { return slot / 3 >= 5; }
    public static boolean evening(int slot) { return slot % 3 == 2; }

    /** Why an activity cannot go in a slot, or null if it can. */
    public String refusal(int slot, Activity a, boolean midtermSeason) {
        if (slot < 0 || slot >= SLOTS) return "No such slot";
        return switch (a) {
            case TRAVEL -> "Travel is set by a trip, not chosen";
            case CAMP_DAVID -> weekend(slot) ? null : "Camp David is for weekends";
            case HILL -> weekend(slot) ? "Congress is away at the weekend" : null;
            case WORK_LATE -> evening(slot) ? null : "Working late is for evenings";
            case CABINET -> weekend(slot) || evening(slot) ? "The cabinet meets on weekday mornings and afternoons" : null;
            case CAMPAIGN -> midtermSeason ? null : "Campaign stops are for the hundred days before the midterms";
            case ABROAD -> "Trips abroad arrive with the world (presidency milestone 7)";
            default -> null;
        };
    }

    void setSlot(PresidentState s, int slot, Activity a, String arg, boolean midtermSeason) {
        String no = refusal(slot, a, midtermSeason);
        if (no != null) throw new IllegalArgumentException(no);
        String v = switch (a.arg) {
            case NONE -> null;
            case ISSUE -> { if (arg == null) throw new IllegalArgumentException("Choose an issue"); yield Issue.valueOf(arg).name(); }
            case STATE -> { if (arg == null || !states.contains(arg) || arg.equals("DC")) throw new IllegalArgumentException("Choose a state"); yield arg; }
            case COUNTRY -> throw new IllegalArgumentException("Choose a country");
        };
        s.act[slot] = a;
        s.arg[slot] = v;
    }

    // ---- ready-made weeks ---------------------------------------------------------------------------------------------------

    /** Resolute Desk's routine week: policy every weekday morning, remarks, the Hill, a trip and ceremonies in the afternoons, rest at the weekend. */
    void routine(PresidentState s, WeekContext c) {
        Arrays.fill(s.act, Activity.FREE);
        Arrays.fill(s.arg, null);
        Issue top = topIssue(c);
        List<Issue> pri = priorities(c);
        for (int d = 0; d < 5; d++) {
            s.act[d * 3] = Activity.POLICY; s.arg[d * 3] = pri.get(d % pri.size()).name();
            s.act[d * 3 + 1] = d == 1 ? Activity.REMARKS : d == 2 ? Activity.HILL : d == 3 ? Activity.TRIP : Activity.CEREMONY;
            if (d == 1) s.arg[d * 3 + 1] = top.name();
            if (d == 3) s.arg[d * 3 + 1] = swingStates(c).get(0);
            s.act[d * 3 + 2] = d == 4 ? Activity.FAMILY : Activity.FREE;
        }
        s.act[15] = Activity.EXERCISE; s.act[16] = Activity.FAMILY; s.act[17] = Activity.REST;
        s.act[18] = Activity.REST; s.act[19] = Activity.FAMILY; s.act[20] = Activity.FREE;
    }

    /** The routine, plus trips on the four weekday afternoons from Monday to Thursday, to the closest big states. */
    void road(PresidentState s, WeekContext c) {
        routine(s, c);
        List<String> sw = roadStates(c);
        int[] slots = {1, 4, 7, 10};
        for (int k = 0; k < 4; k++) { s.act[slots[k]] = Activity.TRIP; s.arg[slots[k]] = sw.get(k); }
    }

    /** The routine, lightened: policy on alternate afternoons, rest every evening, and Camp David at the weekend. */
    void rest(PresidentState s, WeekContext c) {
        routine(s, c);
        Issue top = topIssue(c);
        for (int d = 0; d < 5; d++) {
            s.act[d * 3 + 1] = d % 2 == 0 ? Activity.POLICY : Activity.FREE;
            s.arg[d * 3 + 1] = d % 2 == 0 ? top.name() : null;
            s.act[d * 3 + 2] = Activity.REST;
        }
        for (int i = 15; i < 20; i++) { s.act[i] = Activity.CAMP_DAVID; s.arg[i] = null; }
        s.act[20] = Activity.REST;
    }

    void clear(PresidentState s) { Arrays.fill(s.act, Activity.FREE); Arrays.fill(s.arg, null); }

    /** Issues in order of how much attention they need: salience times the room to improve. */
    List<Issue> priorities(WeekContext c) {
        List<Issue> l = new ArrayList<>(List.of(Issue.values()));
        l.sort(Comparator.comparingDouble((Issue i) -> -c.salience().getOrDefault(i, 0.0) * (62 - c.issueApproval().getOrDefault(i, 50.0))));
        return l.subList(0, 4);
    }

    public Issue topIssue(WeekContext c) {
        Issue best = Issue.values()[0];
        for (Issue i : Issue.values())
            if (c.salience().getOrDefault(i, 0.0) * (60 - c.issueApproval().getOrDefault(i, 50.0))
                    > c.salience().getOrDefault(best, 0.0) * (60 - c.issueApproval().getOrDefault(best, 50.0))) best = i;
        return best;
    }

    /** The swing states worth a trip: electoral votes, discounted the further approval is from 47 (Resolute Desk's {@code swingState}). */
    List<String> swingStates(WeekContext c) {
        List<UsState> l = new ArrayList<>(states.all());
        l.removeIf(u -> u.code().equals("DC"));
        l.sort(Comparator.comparingDouble((UsState u) -> -(u.electoralVotes() * (1 - Math.abs(c.stateApproval().getOrDefault(u.code(), 47.0) - 47) / 25))));
        return l.stream().map(UsState::code).toList();
    }

    /** States for a road week: close to 48% approval and big (Resolute Desk's {@code roadWeek} order). */
    List<String> roadStates(WeekContext c) {
        List<UsState> l = new ArrayList<>(states.all());
        l.removeIf(u -> u.code().equals("DC"));
        l.sort(Comparator.comparingDouble((UsState u) -> Math.abs(c.stateApproval().getOrDefault(u.code(), 48.0) - 48) - u.electoralVotes() * 0.15));
        return l.stream().map(UsState::code).toList();
    }

    // ---- the staff's hand -------------------------------------------------------------------------------------------------

    /** The chief of staff writes next week (Resolute Desk's {@code Delegate.plan}), if the calendar is delegated. */
    void delegatePlan(PresidentState s, WeekContext c) {
        if (!s.delegate) return;
        double q = c.staffQuality();
        routine(s, c);
        if (s.stamina < 35 + (1 - q) * 15) rest(s, c);
        else if (s.week % 5 == 2 && q > 0.5) {
            List<String> sw = roadStates(c);
            s.act[7] = Activity.TRIP; s.arg[7] = sw.get(0);
            if (c.midtermSeason()) { s.act[10] = Activity.CAMPAIGN; s.arg[10] = sw.get(1); }
        }
        if (s.week - s.lastCabinet > 6) { s.act[4] = Activity.CABINET; s.arg[4] = null; }
    }

    /** Watch mode's week (Resolute Desk's {@code Auto.week}): a fresh routine every fourth week, the plan's road weeks, rest when tired. */
    void watchWeek(PresidentState s, WeekContext c) {
        int w = s.week, road = s.plan.roadWeeks();
        if (w % 4 == 0) routine(s, c);
        if (w % road == 3 % road) road(s, c);
        if (s.stamina < 35) rest(s, c);
    }

    // ---- what the week costs ------------------------------------------------------------------------------------------------

    /** Hours of sleep the week's evenings allow, a night on average (long flights and trips abroad take some off). */
    public double sleepOf(PresidentState s) {
        double h = 0;
        for (int d = 0; d < 7; d++) {
            Activity a = s.act[d * 3 + 2], m = s.act[d * 3];
            double v = a.nightSleep;
            if (m.isTrip() && s.arg[d * 3] != null && flight(s.arg[d * 3]) > 3.5) v -= 1;
            if (a == Activity.ABROAD || m == Activity.ABROAD) v -= 1.2;
            h += v;
        }
        return h / 7;
    }

    /** The stamina a slot costs this president (age makes costs heavier), with the flight. */
    public double slotCost(PresidentState s, int i) {
        Activity a = s.act[i];
        double v = a.stamina;
        if (a.isTrip() && s.arg[i] != null) v -= Math.min(6, flight(s.arg[i]) * 0.9);
        return v < 0 ? v * ageFactor(s) : v;
    }

    double cost(PresidentState s) {
        double c = 0;
        for (int i = 0; i < SLOTS; i++) c += slotCost(s, i);
        return c;
    }

    /** Stamina after the week as it stands (Resolute Desk's {@code projectedStamina}). */
    public double projectedStamina(PresidentState s) {
        return clamp(s.stamina + cost(s) + 4 + (sleepOf(s) - 6.5) * 3 - s.stress / 25 + (s.fitness - 50) / 25, 0, 100);
    }

    // ---- the week itself --------------------------------------------------------------------------------------------------

    /** What the week did beyond the president's own state: effects for the politics engine, and the news it made. */
    public record Outcome(List<Effect> effects, List<News> news) {}

    /** Plays out the week's slots, then sleep, stamina, stress and health (Resolute Desk's {@code Week.resolve}, its first half). */
    public Outcome resolve(PresidentState s, WeekContext c, LocalDate weekStart, DeterministicRandomManager rng) {
        List<Effect> fx = new ArrayList<>();
        List<News> news = new ArrayList<>();
        double before = s.stamina, perf = 0, raisedTotal = 0, q = c.staffQuality();
        int trips = 0, funds = 0, policy = 0, hill = 0;
        Map<Issue, Integer> work = new LinkedHashMap<>();
        Map<String, Double> standing = new LinkedHashMap<>();
        List<String> visited = new ArrayList<>();
        s.hillHours = 0;
        s.cabinetMeetings = 0;
        for (int i = 0; i < SLOTS; i++) {
            Activity a = s.act[i];
            String arg = s.arg[i];
            s.stress = clamp(s.stress + a.stress, 0, 100);
            switch (a) {
                case POLICY -> { if (arg != null) { work.merge(Issue.valueOf(arg), 1, Integer::sum); policy++; } }
                case REMARKS -> {
                    if (arg != null) {
                        fx.add(new Effect.Remarks(Issue.valueOf(arg), (0.04 * c.speech() / 70) * (0.85 + 0.3 * c.commsStaff()), rng.gaussian("president.remarks") * 0.02));
                        perf += 0.012;
                    }
                }
                case TRIP -> {
                    if (arg != null) {
                        trips++;
                        standing.merge(arg, 2.2 * c.charisma() / 70, Double::sum);
                        perf += 0.015;
                        s.location = arg;
                        if (!visited.contains(arg)) visited.add(arg);
                        if (rng.chance("president.visit.news", 0.5))
                            news.add(new News(weekStart.plusDays(i / 3), News.Kind.INFO, c.presidentLast() + " visits " + states.get(arg).name(), null, 0.4, null));
                    }
                }
                case FUNDRAISER -> {
                    funds++;
                    double raised = (1.5 + rng.uniform("president.fundraiser") * 2) * c.bigDonors() / 70 * (0.85 + 0.3 * c.politicalStaff());
                    s.warChest += raised;
                    raisedTotal += raised;
                }
                case CEREMONY -> perf += 0.012;
                case HILL -> { s.hill = Math.min(100, s.hill + 1.2 * c.establishment() / 70); s.hillHours += 2.5; hill++; }
                case FAMILY -> perf += 0.004;
                case EXERCISE -> s.fitness = Math.min(100, s.fitness + 1.2);
                case CABINET -> { s.lastCabinet = s.week; s.cabinetMeetings++; }
                case CAMPAIGN -> {
                    if (arg != null) {
                        s.midtermEffort.merge(arg, c.charisma() / 70, Double::sum);
                        standing.merge(arg, 0.8, Double::sum);
                        s.location = arg;
                        trips++;
                        if (!visited.contains(arg)) visited.add(arg);
                    }
                }
                case WORK_LATE -> fx.add(new Effect.IssueCommand(topIssue(c), 1.5));
                default -> { }
            }
        }
        work.forEach((issue, n) -> fx.add(0, new Effect.IssueWork(issue, 0.85 + 0.3 * c.policyStaff(), n)));
        if (funds > 3) {
            perf -= 0.06 * (funds - 3);
            if (funds >= 5) news.add(new News(weekStart, News.Kind.BAD, "Critics count " + funds + " fundraisers in a week for " + c.presidentLast(), null, 0.7, null));
        }
        if (perf != 0) fx.add(new Effect.Performance(perf));
        standing.forEach((st, v) -> fx.add(new Effect.StateStanding(st, v)));
        if (trips == 0) s.location = "DC";
        s.fitness = Math.max(0, s.fitness - 0.4);
        // sleep and stamina
        double sl = sleepOf(s);
        s.sleep = sl;
        s.stamina = clamp(s.stamina + cost(s) + 4 + (sl - 6.5) * 3 - s.stress / 25 + (s.fitness - 50) / 25, 0, 100);
        if (sl < 5.5) s.stress = clamp(s.stress + 3, 0, 100);
        s.stress += 9.5 + (50 - c.approval()) * 0.08 + (s.stamina < 30 ? 3 : 0);       // the job itself
        s.stress = clamp(s.stress * 0.85, 0, 100);
        health(s, c, weekStart, rng, fx, news);

        s.lastWeek.clear();
        s.lastVisited.clear();
        s.lastVisited.addAll(visited);
        if (policy > 0) s.lastWeek.add(policy + (policy == 1 ? " policy meeting" : " policy meetings"));
        if (!visited.isEmpty()) s.lastWeek.add((trips == 1 ? "a trip to " : trips + " stops: ") + String.join(", ", visited.stream().map(v -> states.get(v).name()).toList()));
        if (hill > 0) s.lastWeek.add(Fmt.format("%.1f hours on the Hill", s.hillHours));
        if (raisedTotal > 0) s.lastWeek.add(Fmt.format("$%.1fM raised for the party", raisedTotal));
        s.lastRaised = raisedTotal;
        s.lastStaminaChange = s.stamina - before;
        s.history.add(new double[]{s.stamina, s.stress, s.health, s.sleep, s.fitness});
        if (s.history.size() > 260) s.history.remove(0);
        s.week++;
        return new Outcome(fx, news);
    }

    private void health(PresidentState s, WeekContext c, LocalDate weekStart, DeterministicRandomManager rng, List<Effect> fx, List<News> news) {
        int age = s.age;
        s.health = clamp(s.health + (s.stamina - 50) * 0.01 - (s.stress - 40) * 0.01 + (s.fitness - 50) * 0.005 - Math.max(0, age - 70) * 0.01, 20, 100);
        if (s.sick > 0) { s.sick--; s.stamina = Math.min(s.stamina, 55); return; }
        double risk = 0.004 + 0.0007 * Math.max(0, age - 60) + 0.0006 * Math.max(0, s.stress - 60) + (s.stamina < 20 ? 0.02 : 0) + (100 - s.health) * 0.0004;
        if (rng.chance("president.illness", risk)) {
            if (rng.chance("president.illness.kind", 0.8)) {
                s.sick = 1 + rng.nextInt("president.illness.weeks", 2);
                s.stamina = Math.max(10, s.stamina - 15);
                news.add(new News(weekStart, News.Kind.INFO, "White House: the president has a cold and is working from the residence", null, 0.6, null));
            } else {
                s.sick = 2;
                s.stamina = Math.max(10, s.stamina - 30);
                fx.add(new Effect.Performance(-1.0));
                news.add(new News(weekStart, News.Kind.BAD, "President " + c.presidentLast() + " spends a night at Walter Reed for tests",
                        "The White House physician says the president is doing well and will be back at work within days.", 2.2, null));
            }
        }
    }

    // ---- orders -----------------------------------------------------------------------------------------------------------------

    /** Applies one of the player's orders. Ready-made weeks need the context; plain slot changes do not. */
    /** Transitions from other systems: the strain of a press conference or a crisis, money the vice president raises. */
    public boolean apply(PresidentState s, Effect e) {
        switch (e) {
            case Effect.PresidentStrain x -> { s.stamina = clamp(s.stamina + x.stamina(), 0, 100); s.stress = clamp(s.stress + x.stress(), 0, 100); }
            case Effect.PartyFunds x -> s.warChest = Math.max(0, s.warChest + x.millions());
            default -> { return false; }
        }
        return true;
    }

    public void apply(PresidentState s, PresidencyOrder o, WeekContext c) {
        switch (o) {
            case PresidencyOrder.Slot x -> setSlot(s, x.slot(), x.activity(), x.arg(), c.midtermSeason());
            case PresidencyOrder.Week x -> {
                switch (x.week()) {
                    case ROUTINE -> routine(s, c);
                    case ROAD -> road(s, c);
                    case REST -> rest(s, c);
                    case CLEAR -> clear(s);
                }
            }
            case PresidencyOrder.Delegate x -> { s.delegate = x.on(); if (x.on()) delegatePlan(s, c); }   // the staff writes this week at once
            case PresidencyOrder.WatchPreset x -> s.plan.use(x.preset());
            case PresidencyOrder.WatchPriority x -> {
                if (x.priority() < 0 || x.priority() >= WatchPlan.PRIORITIES) throw new IllegalArgumentException("No such priority");
                s.plan.set(x.priority(), x.value());
            }
            case PresidencyOrder.WatchStop x -> {
                if (x.stop() < 0 || x.stop() >= WatchPlan.STOPS) throw new IllegalArgumentException("No such stop");
                s.plan.stop[x.stop()] = x.on();
            }
            case PresidencyOrder.WatchWeek x -> watchWeek(s, c);
        }
    }

    /** After a week: the chief of staff writes the next one, if asked to. */
    public void afterWeek(PresidentState s, WeekContext c) { delegatePlan(s, c); }

    /** The first week in office: the routine. */
    public void firstWeek(PresidentState s, WeekContext c) { routine(s, c); }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
