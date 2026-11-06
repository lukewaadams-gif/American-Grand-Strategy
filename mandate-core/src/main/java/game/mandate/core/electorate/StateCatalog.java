package game.mandate.core.electorate;

import game.mandate.core.data.Csv;
import game.mandate.core.state.Party;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loads the 51 electoral units from {@code /data/states.csv}. */
public final class StateCatalog {
    private final Map<String, UsState> byCode;

    private StateCatalog(Map<String, UsState> byCode) { this.byCode = Collections.unmodifiableMap(byCode); }

    public static StateCatalog load() {
        Map<String, UsState> m = new LinkedHashMap<>();
        for (String[] f : Csv.readResource("/data/states.csv")) {
            double margin = Double.parseDouble(f[3]);
            m.put(f[0], new UsState(f[0], f[1], Integer.parseInt(f[2]), margin,
                    elasticityFor(margin), holder(f[5]), holder(f[4])));
        }
        return new StateCatalog(m);
    }

    /**
     * Placeholder heuristic: lopsided states move a little less than competitive ones.
     * The ported cell model derives elasticity from demographics instead (milestone M1).
     */
    static double elasticityFor(double margin) {
        return Math.max(0.75, Math.min(1.10, 1.10 - 0.006 * Math.abs(margin)));
    }

    private static Optional<Party> holder(String code) {
        return switch (code) {
            case "D", "I" -> Optional.of(Party.DEMOCRAT);
            case "R" -> Optional.of(Party.REPUBLICAN);
            default -> Optional.empty();
        };
    }

    public List<UsState> all() { return List.copyOf(byCode.values()); }

    public UsState get(String code) {
        UsState s = byCode.get(code);
        if (s == null) throw new IllegalArgumentException("Unknown state " + code);
        return s;
    }

    public boolean contains(String code) { return byCode.containsKey(code); }

    public int totalElectoralVotes() { return byCode.values().stream().mapToInt(UsState::electoralVotes).sum(); }
}
