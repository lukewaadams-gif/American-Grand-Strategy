package game.mandate.core.foreign;

import game.mandate.core.data.Csv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resolute Desk's world, as data: the forty countries ({@code rd/countries.tsv}, with made-up stand-ins for real
 * leaders), the crises, operations and covert actions ({@code rd/crises.tsv}, {@code rd/operations.tsv},
 * {@code rd/covert.tsv}), the objectives ({@code rd/objectives.tsv}), and the tables Resolute Desk keeps in code
 * (WorldTables, StatecraftData, Summit.ASKS, WarData, the bases and starting support, rivals and autocracies).
 */
public final class ForeignData {
    // ---- countries -----------------------------------------------------------------------------------------------
    public static final int NC = 40;
    public static final String[] REGION = {"East Asia and the Pacific", "Europe", "The Middle East", "The Americas", "Africa", "South Asia"};
    public static final int R_ASIA = 0, R_EUROPE = 1, R_MIDEAST = 2, R_AMERICAS = 3, R_AFRICA = 4, R_SOUTH_ASIA = 5;
    public static final String[] ALIGN = {"Neutral", "Partner", "Treaty ally", "Adversary"};
    public static final int AL_NEUTRAL = 0, AL_PARTNER = 1, AL_TREATY = 2, AL_ADVERSARY = 3;

    public record Country(String id, String name, int region, int align, double relations, int power, double trade, double balance, boolean nuclear,
                          double mapX, double mapY, String flag, String leader, String title) {
        /** Hours in the air from Washington (Resolute Desk's {@code flightAbroad}). */
        public double flight() {
            double dx = (mapX - 280) * 0.042, dy = (mapY - 125) * 0.06;
            return Math.max(1.5, Math.sqrt(dx * dx + dy * dy) * 1.35 + 0.5);
        }
    }

    // ---- the tables Resolute Desk keeps in code (WorldTables, StatecraftData) ------------------------------------
    public static final String[] TW_STAGE = {"Calm", "Pressure campaign", "Large-scale drills", "Customs quarantine", "Blockade", "War"};
    public static final String[] UA_STAGE = {"Peace agreement", "Ceasefire", "Full-scale war"};
    public static final String[] POSTURE_AREA = {"The Pacific", "Europe", "The Middle East"};
    public static final String[] POSTURE = {"Light", "Normal", "Heavy", "Surge"};
    public static final String[] TARIFF_OPT = {"0%", "10%", "25%", "50%"};
    public static final int[] TARIFF_VAL = {0, 10, 25, 50};
    public static final String[] LEVEL = {"None", "Light", "Heavy", "Maximum"};
    public static final String[] AID_LEVEL = {"None", "Some", "Substantial", "Major"};
    /** The multilateral summits: name, month, day (Resolute Desk's {@code SUMMITS}). */
    public static final String[][] SUMMITS = {{"G7", "6", "14"}, {"NATO", "7", "9"}, {"UN General Assembly", "9", "23"}, {"G20", "11", "18"}};
    public static final String[] MIL_AID = {"None", "Some", "Substantial", "Major"};
    public static final String[] ARMS = {"None", "Approved", "A major package"};
    public static final String[] TIES = {"Severed", "Downgraded", "Full"};
    public static final int[] TROOP_STEP = {0, 2, 5, 20, 60};
    public static final String[] TROOP_NAME = {"None", "Advisers", "A brigade", "A division", "A corps"};
    public static final String[] CV2_NAME = {"An influence campaign", "Cyber sabotage", "Arm the opposition", "Regime change"};
    public static final String[] CV2_DESC = {"Money and messages for friendly parties and press.", "Disable military or nuclear systems from a distance.",
            "Guns and training for armed groups fighting the government.", "Help the opposition and the army bring the government down."};
    public static final double[] CV2_ODDS = {0.60, 0.55, 0.45, 0.25}, CV2_LEAK = {0.20, 0.20, 0.35, 0.45};
    public static final int[] CV2_WEEKS = {8, 6, 12, 16};
    /** Countries with severed ties in 2029 (Resolute Desk's {@code NO_TIES}); Taiwan's ties are downgraded. */
    public static final String[] NO_TIES = {"IR", "KP", "VE"};
    /** American troops based abroad in 2029, thousands (Resolute Desk's {@code BASES}, 2022 on). */
    public static final String[][] BASES = {{"DE", "35"}, {"JP", "54"}, {"GB", "10"}, {"IT", "12"}, {"KR", "28"}, {"TR", "2"}, {"QA", "10"}, {"AE", "3.5"},
            {"SA", "2.7"}, {"PL", "10"}, {"IQ", "2.5"}, {"SY", "2"}, {"AU", "2"}, {"PH", "0.5"}, {"DK", "0.2"}, {"FI", "0.5"}};
    /** Military aid and arms sales in 2029 (Resolute Desk's {@code SUPPORT}, 2022 on): country, military aid, arms. */
    public static final String[][] SUPPORT = {{"UA", "3", "1"}, {"IL", "3", "2"}, {"TW", "1", "1"}, {"EG", "2", "1"}, {"JO", "1", "1"}, {"SA", "0", "1"},
            {"AE", "0", "1"}, {"PL", "1", "1"}};
    /** Starting unrest in 2029 (Resolute Desk's {@code Statecraft.setup}). */
    public static final String[][] UNREST = {{"IQ", "45"}, {"VE", "40"}, {"NG", "35"}};
    public static final String[] AUTOCRACY = {"SA", "AE", "QA", "EG", "VN", "JO"};
    public static final String[] ADVERSARY_BLOC = {"RU", "CN", "IR", "KP", "VE", "CU", "SY"};
    public static final String[] OIL_PRODUCER = {"IR", "IQ", "SA", "QA", "AE", "VE", "RU"};
    public static final String[] BIG_PRODUCER = {"SA", "RU"};
    /** Starting sanctions and foreign aid in 2029 (Resolute Desk's {@code Diplo.setup}). */
    public static final String[][] SANCTIONS = {{"RU", "3"}, {"IR", "3"}, {"KP", "3"}, {"CU", "3"}, {"VE", "2"}, {"SY", "2"}, {"CN", "1"}};
    public static final String[][] AID = {{"UA", "1"}, {"IL", "2"}, {"TW", "1"}, {"EG", "1"}, {"JO", "1"}};

    /** A rival a country's friends anger (Resolute Desk's {@code rivalOf}), or null. */
    public static String rivalOf(String c) {
        return switch (c) {
            case "UA", "PL", "FI" -> "RU";
            case "TW", "PH", "JP", "VN", "IN" -> "CN";
            case "IL", "SA", "AE", "IQ" -> "IR";
            case "KR" -> "KP";
            default -> null;
        };
    }

    /** The great power that will fight for a country that is invaded (Resolute Desk's {@code protector}, 2029). */
    public static String protector(String c) {
        return switch (c) { case "KP" -> "CN"; case "SY" -> "RU"; default -> null; };
    }

    public static boolean in(String[] list, String c) { for (String x : list) if (x.equals(c)) return true; return false; }

    // ---- the summit's asks (Resolute Desk's Summit.ASKS) -----------------------------------------------------------
    public static final String[][] ASKS = {{"A trade deal", "trade"}, {"A deal on fentanyl chemicals", "fent"}, {"Lower tensions over Taiwan", "taiwan"},
            {"A ceasefire in Ukraine", "ukraine"}, {"A new nuclear arms treaty", "arms"}, {"Limits on Iran's nuclear program", "iran"},
            {"A freeze on North Korea's tests", "nk"}, {"More defense spending by allies", "spend"}, {"Help at the border", "border"},
            {"More oil on the market", "oil"}, {"An easing over Berlin", "berlin"}, {"A pledge of friendship and cooperation", "friend"}};

    // ---- wars of choice (Resolute Desk's WarData) ------------------------------------------------------------------
    public static final String[] WAR_STAGE = {"Not begun", "", "Fighting", "Won", "Lost", "Ended by agreement", "Withdrawn on a timetable", "Handed over", "Victory declared"};
    public static final int WS_FIGHTING = 2, WS_WON = 3, WS_LOST = 4, WS_AGREED = 5, WS_TIMETABLE = 6, WS_HANDED = 7, WS_DECLARED = 8;
    public static final String[] STRATEGY = {"Hold the line", "Balanced", "On the offensive", "Seek a settlement"};
    public static final String[] STRATEGY_DESC = {"Fewer casualties, slow losses of ground.", "Steady pressure.",
            "Gains on the front, at a much higher cost in lives.", "Fewer casualties and faster talks, but the enemy gains ground."};
    public static final int WK_STRIKES = 0, WK_BLOCKADE = 1, WK_NOFLY = 2, WK_INVASION = 3, WK_OCCUPATION = 4;
    public static final String[] WK_NAME = {"Air and missile strikes", "A naval blockade", "A no-fly zone", "An invasion", "An occupation"};
    public static final String[] WK_DESC = {"Bombers, missiles and drones against military and leadership targets. No troops on the ground, but it can go on for months.",
            "The Navy closes the country's ports and sea lanes. Slow, and the world's shipping feels it.",
            "Fighters patrol the country's skies to stop its air force attacking its own people or its neighbors.",
            "Ground forces cross the border to take the capital and bring the government down. Fast against the weak; ruinous against the strong.",
            "American forces hold the country while a new government finds its feet. Insurgents fight on."};
    public static final double[] WK_PACE = {4, 2.5, 1.5, 26, -0.06}, WK_LETHAL = {0.01, 0.005, 0.012, 0.08, 0.05}, WK_COST = {0.01, 0.008, 0.01, 0.007, 0.007};
    public static final double[] WK_FRONT = {50, 40, 55, 30, 50};

    // ---- crises, operations, covert actions, objectives ------------------------------------------------------------
    public record CrisisOption(String label, String adviser, String kind, double odds, String okFx, String failFx, String okStory, String failStory) {}
    public record Crisis(String id, String cond, int cool, String title, String body, List<CrisisOption> options) {}
    public record Operation(String name, String desc, String cond, double odds, String okFx, String failFx) {}
    public record Covert(String name, String desc, double odds, double expose, String fx, String exposedFx) {}
    public record Step(String label, String kind, double odds, String need, String cost, String okFx, String failFx) {}
    public record ObjectiveDef(String id, String who, int from, int to, String name, String why, String payoff, List<Step> steps) {}

    private final List<Country> countries = new ArrayList<>();
    private final List<Crisis> crises = new ArrayList<>();
    private final List<Operation> operations = new ArrayList<>();
    private final List<Covert> covert = new ArrayList<>();
    private final List<ObjectiveDef> objectives = new ArrayList<>();

    public ForeignData() {
        for (String[] r : Csv.readTsv("/data/rd/countries.tsv"))
            countries.add(new Country(r[0], r[1], indexOf(REGION, r[2]), indexOf(ALIGN, r[3]), Double.parseDouble(r[4]), Integer.parseInt(r[5]),
                    Double.parseDouble(r[6]), Double.parseDouble(r[7]), Boolean.parseBoolean(r[8]), Double.parseDouble(r[9]), Double.parseDouble(r[10]),
                    r[11], r[12], r[13]));
        for (String[] r : Csv.readTsv("/data/rd/crises.tsv")) {
            List<CrisisOption> opts = new ArrayList<>();
            for (String o : r[5].split(";;")) {
                String[] t = o.split("\\|", -1);
                opts.add(new CrisisOption(t[0], t[1], t[2], Double.parseDouble(t[3]), dash(t[4]), dash(t[5]), t[6], t[7]));
            }
            crises.add(new Crisis(r[0], r[1], Integer.parseInt(r[2]), r[3], r[4], List.copyOf(opts)));
        }
        for (String[] r : Csv.readTsv("/data/rd/operations.tsv")) {
            String[] f = r[3].split("\\|", -1);
            operations.add(new Operation(r[0], r[1], r[2].equals("-") ? "" : r[2], Double.parseDouble(f[0]), dash(f[1]), dash(f[2])));
        }
        for (String[] r : Csv.readTsv("/data/rd/covert.tsv"))
            covert.add(new Covert(r[0], r[1], Double.parseDouble(r[2]), Double.parseDouble(r[3]), dash(r[4]), dash(r[5])));
        for (String[] r : Csv.readTsv("/data/rd/objectives.tsv")) {
            String[] t = r[0].split("\\|", 8);
            List<Step> steps = new ArrayList<>();
            if (t.length > 7) for (String st : t[7].split(";;")) {
                String[] q = st.split("\\|", -1);
                steps.add(new Step(q[0], q[1], Double.parseDouble(q[2]), q.length > 3 ? dash(q[3]) : "", q.length > 4 ? dash(q[4]) : "",
                        q.length > 5 ? dash(q[5]) : "", q.length > 6 ? dash(q[6]) : ""));
            }
            objectives.add(new ObjectiveDef(t[0], t[1], Integer.parseInt(t[2]), Integer.parseInt(t[3]), t[4], t[5], dash(t[6]), List.copyOf(steps)));
        }
    }

    static String dash(String s) { return s == null || s.equals("-") ? "" : s.trim(); }

    static int indexOf(String[] a, String v) {
        for (int i = 0; i < a.length; i++) if (a[i].equalsIgnoreCase(v)) return i;
        throw new IllegalArgumentException("Unknown value " + v);
    }

    public List<Country> countries() { return Collections.unmodifiableList(countries); }
    public Country country(int i) { return countries.get(i); }
    public int index(String id) {
        for (int i = 0; i < countries.size(); i++) if (countries.get(i).id().equals(id)) return i;
        return -1;
    }
    public List<Crisis> crises() { return Collections.unmodifiableList(crises); }
    public List<Operation> operations() { return Collections.unmodifiableList(operations); }
    public List<Covert> covert() { return Collections.unmodifiableList(covert); }
    public List<ObjectiveDef> objectives() { return Collections.unmodifiableList(objectives); }
    /** Two-way goods trade with every country, plus untracked trade (Resolute Desk's 5024.2 = 4124.2 + 900). */
    public double tradeTotal() { double t = 900; for (Country c : countries) t += c.trade(); return t; }
}
