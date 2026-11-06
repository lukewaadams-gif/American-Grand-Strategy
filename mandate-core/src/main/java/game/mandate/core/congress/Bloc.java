package game.mandate.core.congress;

import game.mandate.core.data.Csv;
import game.mandate.core.state.Party;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * An ideological bloc within a party caucus, loaded from {@code /data/blocs.csv}. Three per party per chamber,
 * as in Resolute Desk: enough to make the whip count a puzzle, few enough to read at a glance.
 *
 * @param share    fraction of the party's caucus
 * @param loyalty  0..1, how much party and president pull against ideology
 * @param deficitHawk whether deficit-increasing bills cost support
 */
public record Bloc(String name, Party party, double ideology, double share, double loyalty, boolean deficitHawk) {

    private static Map<Party, Bloc[]> catalog;

    public static synchronized Bloc[] defaultsFor(Party p) {
        if (catalog == null) {
            Map<Party, List<Bloc>> m = new EnumMap<>(Party.class);
            for (String[] f : Csv.readResource("/data/blocs.csv"))
                m.computeIfAbsent(Party.valueOf(f[0]), k -> new ArrayList<>()).add(new Bloc(f[1], Party.valueOf(f[0]),
                        Double.parseDouble(f[2]), Double.parseDouble(f[3]), Double.parseDouble(f[4]), Boolean.parseBoolean(f[5])));
            Map<Party, Bloc[]> c = new EnumMap<>(Party.class);
            m.forEach((k, v) -> c.put(k, v.toArray(new Bloc[0])));
            catalog = c;
        }
        return catalog.get(p).clone();
    }

    public static Bloc named(String name) {
        for (Party p : Party.values()) for (Bloc b : defaultsFor(p)) if (b.name().equals(name)) return b;
        throw new IllegalArgumentException("No bloc " + name);
    }

    /** 0 = the base wing (the party's most ideological bloc), 1 = the moderate wing, -1 = the mainstream bloc. */
    public int wing() {
        Bloc[] bs = defaultsFor(party);
        Bloc extreme = bs[0], moderate = bs[0];
        for (Bloc x : bs) {
            if (Math.abs(x.ideology()) > Math.abs(extreme.ideology())) extreme = x;
            if (Math.abs(x.ideology()) < Math.abs(moderate.ideology())) moderate = x;
        }
        return name.equals(extreme.name()) ? 0 : name.equals(moderate.name()) ? 1 : -1;
    }
}
