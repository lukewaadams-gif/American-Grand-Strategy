package game.mandate.core.people;

import game.mandate.core.data.Tuning;

import java.util.Map;

/**
 * Who a game seats (docs/DECISIONS_TO_REVIEW.md, the real-person start): with real people, the 2028-29 world is the real
 * roster (RealPeople), or an office-labelled placeholder where the record has no one; fictional newcomers fill vacancies
 * only from 2030, at the rate tuning.properties sets for each year (people.newcomers.*). With the fictional cast (the
 * opt-in alternative), every vacancy is filled by a made-up figure, as before. Immutable configuration, not state.
 */
public final class People {
    static final int FIRST = 2028, LAST = 2036;

    private final boolean real;
    private final RealPeople data;
    private final double[] rate = new double[LAST - FIRST + 1];

    private People(boolean real, RealPeople data, Tuning t) {
        this.real = real;
        this.data = data;
        for (int y = FIRST; y <= LAST; y++) {
            String k = "people.newcomers." + y;
            rate[y - FIRST] = t.keys().contains(k) ? Math.max(0, Math.min(1, t.d(k))) : (y < 2030 ? 0 : 1);
        }
    }

    /** The fictional cast: every vacancy goes to a made-up figure (the engines' tests and the opt-in fictional world). */
    public static People fictional() { return new People(false, null, Tuning.defaults()); }

    public static People of(boolean real, Tuning t) { return new People(real, real ? RealPeople.load() : null, t); }

    public boolean real() { return real; }
    public RealPeople data() { return data; }

    /** The configured chance that a vacancy this year goes to a fictional newcomer (0 before 2030). */
    public double newcomerRate(int year) {
        if (year < FIRST) return 0;
        return rate[Math.min(year, LAST) - FIRST];
    }

    /** Whether a vacancy this year is filled by a fictional newcomer; {@code u} is a draw from the engine's own stream. */
    public boolean newcomer(int year, double u) { return !real || u < newcomerRate(year); }

    /** An office's placeholder: "Senator from Ohio", "Representative from Texas". */
    public static String office(String title, String state) { return title + " from " + stateName(state); }

    public static String stateName(String code) { return STATES.getOrDefault(code, code); }

    static final Map<String, String> STATES = Map.ofEntries(
            Map.entry("AL", "Alabama"), Map.entry("AK", "Alaska"), Map.entry("AZ", "Arizona"), Map.entry("AR", "Arkansas"), Map.entry("CA", "California"),
            Map.entry("CO", "Colorado"), Map.entry("CT", "Connecticut"), Map.entry("DE", "Delaware"), Map.entry("DC", "the District of Columbia"),
            Map.entry("FL", "Florida"), Map.entry("GA", "Georgia"), Map.entry("HI", "Hawaii"), Map.entry("ID", "Idaho"), Map.entry("IL", "Illinois"),
            Map.entry("IN", "Indiana"), Map.entry("IA", "Iowa"), Map.entry("KS", "Kansas"), Map.entry("KY", "Kentucky"), Map.entry("LA", "Louisiana"),
            Map.entry("ME", "Maine"), Map.entry("MD", "Maryland"), Map.entry("MA", "Massachusetts"), Map.entry("MI", "Michigan"), Map.entry("MN", "Minnesota"),
            Map.entry("MS", "Mississippi"), Map.entry("MO", "Missouri"), Map.entry("MT", "Montana"), Map.entry("NE", "Nebraska"), Map.entry("NV", "Nevada"),
            Map.entry("NH", "New Hampshire"), Map.entry("NJ", "New Jersey"), Map.entry("NM", "New Mexico"), Map.entry("NY", "New York"),
            Map.entry("NC", "North Carolina"), Map.entry("ND", "North Dakota"), Map.entry("OH", "Ohio"), Map.entry("OK", "Oklahoma"), Map.entry("OR", "Oregon"),
            Map.entry("PA", "Pennsylvania"), Map.entry("RI", "Rhode Island"), Map.entry("SC", "South Carolina"), Map.entry("SD", "South Dakota"),
            Map.entry("TN", "Tennessee"), Map.entry("TX", "Texas"), Map.entry("UT", "Utah"), Map.entry("VT", "Vermont"), Map.entry("VA", "Virginia"),
            Map.entry("WA", "Washington"), Map.entry("WV", "West Virginia"), Map.entry("WI", "Wisconsin"), Map.entry("WY", "Wyoming"));
}
