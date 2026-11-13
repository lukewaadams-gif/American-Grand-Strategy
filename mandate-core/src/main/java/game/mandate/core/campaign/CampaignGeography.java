package game.mandate.core.campaign;

import game.mandate.core.data.Csv;
import game.mandate.core.electorate.CellElectorateModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the campaign happens: Battleground 2028's per-state campaign inputs and its broadcast TV markets, as exported by
 * {@code tools/BgExport.java} into {@value #STATES} and {@value #MARKETS}. Every per-state array in the campaign uses this
 * class's state order ({@link #index}); every per-market array uses the market ids.
 *
 * <p>Immutable content, parsed once.
 */
public final class CampaignGeography {
    public static final String STATES = "/data/campaign_states.csv", MARKETS = "/data/media_markets.csv";

    /** BG's 30 voter groups (Battleground2028.GROUP), in the order of the group weights. */
    public static final List<String> GROUPS = List.of("Gen Z (18-29)", "Seniors (65+)", "Young men", "Young women", "Women", "Men",
            "White evangelicals", "White Catholics", "White, no degree", "White, college degree", "Black voters", "Black men",
            "Hispanic voters", "Hispanic men", "Cuban & Venezuelan", "Puerto Rican & Dominican", "Asian Americans",
            "Jewish voters", "Arab & Muslim Americans", "Latter-day Saints", "Native Americans", "Suburban voters",
            "Rural voters", "Urban voters", "Union households", "Veterans & families", "Gun owners", "LGBTQ voters",
            "College students", "Parents");
    public static final int NG = 30, NAGE = 3;
    /** BG's group indices used by the campaign's activities (Battleground2028.java comments at l.1245). */
    public static final int G_GENZ = 0, G_SENIORS = 1, G_WOMEN = 4, G_WHITE_NODEG = 8, G_WHITE_DEG = 9, G_BLACK = 10, G_HISPANIC = 12,
            G_ASIAN = 16, G_SUBURBAN = 21, G_UNION = 24, G_STUDENTS = 28;

    /**
     * One state as the campaign sees it.
     * @param income        median household income, dollars (BG: event and staff costs)
     * @param urban         percent urban; {@code rural} percent rural (door-knocking density, rally crowds)
     * @param mapX          BG's map position (its travel rule: more than 220 units apart is a long flight)
     * @param tvHouseholds  TV households, thousands
     * @param adults        eligible voters
     * @param groupWeight   each BG voter group's weight in the state's swing (see the CSV header)
     * @param ageWeight     each age group's weight in the state's swing, from the cell electorate
     */
    public record StateInfo(int index, String code, double income, double urban, double rural, double hispanic, double mapX, double mapY,
                            double tvHouseholds, double adults, boolean donorHub, double[] groupWeight, double[] ageWeight) {
        /** BG's cost index: dearer states cost more to staff (Week.stateCostIndex, l.3403). */
        public double costIndex() { return 0.75 + income / 120000.0; }

        /** Doors a volunteer shift reaches, by density (Week.field, l.3870). */
        public double doorDensity() { return 22 + 0.33 * urban + 0.12 * (100 - rural - urban); }

        public double groupWeight(int g) { return groupWeight[g]; }
        public double ageWeight(int a) { return ageWeight[a]; }
    }

    /** One broadcast market: its TV households (thousands) and the share of each state's TV households it reaches. */
    public record Market(int id, String name, double households, int[] states, double[] shares) {
        public double sumShare() { double t = 0; for (double s : shares) t += s; return t; }
        public double share(int state) {
            for (int i = 0; i < states.length; i++) if (states[i] == state) return shares[i];
            return 0;
        }
    }

    private final List<StateInfo> states;
    private final Map<String, Integer> index;
    private final List<Market> markets;
    private final List<List<Integer>> marketsIn;

    private CampaignGeography(List<StateInfo> states, List<Market> markets) {
        this.states = List.copyOf(states);
        Map<String, Integer> ix = new HashMap<>();
        for (StateInfo s : states) ix.put(s.code(), s.index());
        this.index = Map.copyOf(ix);
        this.markets = List.copyOf(markets);
        List<List<Integer>> in = new ArrayList<>();
        for (int s = 0; s < states.size(); s++) in.add(new ArrayList<>());
        for (Market m : markets) for (int s : m.states()) in.get(s).add(m.id());
        List<List<Integer>> frozen = new ArrayList<>();
        for (List<Integer> l : in) frozen.add(List.copyOf(l));
        this.marketsIn = List.copyOf(frozen);
    }

    public static CampaignGeography load() { return Cache.GEO; }

    public int size() { return states.size(); }
    public List<StateInfo> states() { return states; }
    public StateInfo state(int i) { return states.get(i); }
    public StateInfo state(String code) { return states.get(index(code)); }
    public boolean contains(String code) { return index.containsKey(code); }

    public int index(String code) {
        Integer i = index.get(code);
        if (i == null) throw new IllegalArgumentException("Unknown state " + code);
        return i;
    }

    public String code(int i) { return states.get(i).code(); }
    public int marketCount() { return markets.size(); }
    public List<Market> markets() { return markets; }
    public Market market(int id) { return markets.get(id); }
    /** The markets that reach a state, largest first as in the source. */
    public List<Integer> marketsIn(int state) { return marketsIn.get(state); }

    /** Map distance between two states in BG's map units. */
    public double distance(int a, int b) {
        StateInfo x = states.get(a), y = states.get(b);
        return Math.hypot(x.mapX() - y.mapX(), x.mapY() - y.mapY());
    }

    private static final class Cache {
        static final CampaignGeography GEO = parse(Csv.readResource(STATES), Csv.readResource(MARKETS));
    }

    static CampaignGeography parse(List<String[]> stateRows, List<String[]> marketRows) {
        CellElectorateModel cells = CellElectorateModel.load();
        List<StateInfo> states = new ArrayList<>();
        Map<String, Integer> ix = new HashMap<>();
        for (String[] f : stateRows) {
            if (f.length != 10 + NG) throw new IllegalStateException(STATES + ": expected " + (10 + NG) + " columns, got " + f.length);
            double[] gw = new double[NG];
            for (int g = 0; g < NG; g++) gw[g] = Double.parseDouble(f[10 + g]);
            int i = states.size();
            ix.put(f[0], i);
            states.add(new StateInfo(i, f[0], Double.parseDouble(f[1]), Double.parseDouble(f[2]), Double.parseDouble(f[3]), Double.parseDouble(f[4]),
                    Double.parseDouble(f[5]), Double.parseDouble(f[6]), Double.parseDouble(f[7]), Double.parseDouble(f[8]), "1".equals(f[9]),
                    gw, cells.ageSwingWeights(f[0])));
        }
        List<Market> markets = new ArrayList<>();
        for (String[] f : marketRows) {
            if (f.length != 4) throw new IllegalStateException(MARKETS + ": expected 4 columns, got " + f.length);
            int id = Integer.parseInt(f[0]);
            if (id != markets.size()) throw new IllegalStateException(MARKETS + ": market ids must run 0.." + " in order, found " + id);
            String[] pairs = f[3].trim().split(" ");
            int[] st = new int[pairs.length];
            double[] sh = new double[pairs.length];
            for (int i = 0; i < pairs.length; i++) {
                String[] kv = pairs[i].split(":");
                Integer s = ix.get(kv[0]);
                if (s == null) throw new IllegalStateException(MARKETS + ": unknown state " + kv[0]);
                st[i] = s;
                sh[i] = Double.parseDouble(kv[1]);
            }
            markets.add(new Market(id, f[1], Double.parseDouble(f[2]), st, sh));
        }
        return new CampaignGeography(states, markets);
    }
}
