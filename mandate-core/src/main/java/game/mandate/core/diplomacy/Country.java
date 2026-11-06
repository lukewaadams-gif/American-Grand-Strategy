package game.mandate.core.diplomacy;

import game.mandate.core.data.Csv;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A country Washington has a relationship with ({@code /data/countries.csv}). */
public record Country(String id, String name, Optional<HotSpot> hotSpot, double startRelation, double tensionLink) {
    public static List<Country> load() {
        List<Country> l = new ArrayList<>();
        for (String[] f : Csv.readResource("/data/countries.csv"))
            l.add(new Country(f[0], f[1], f[2].equals("-") ? Optional.empty() : Optional.of(HotSpot.valueOf(f[2])),
                    Double.parseDouble(f[3]), Double.parseDouble(f[4])));
        return List.copyOf(l);
    }
}
