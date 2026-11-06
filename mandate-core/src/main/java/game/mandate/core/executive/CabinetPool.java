package game.mandate.core.executive;

import game.mandate.core.data.Csv;
import game.mandate.core.state.Party;

import java.util.ArrayList;
import java.util.List;

/** Fictional cabinet candidates by party, post and archetype ({@code /data/cabinet.csv}). */
public final class CabinetPool {
    public record Candidate(Party party, Post post, NomineeType type, String name) {}

    private final List<Candidate> all;

    private CabinetPool(List<Candidate> all) { this.all = List.copyOf(all); }

    public static CabinetPool load() {
        List<Candidate> l = new ArrayList<>();
        for (String[] f : Csv.readResource("/data/cabinet.csv"))
            l.add(new Candidate(Party.valueOf(f[0]), Post.valueOf(f[1]), NomineeType.valueOf(f[2]), f[3]));
        return new CabinetPool(l);
    }

    public Candidate pick(Party party, Post post, NomineeType type) {
        return all.stream().filter(c -> c.party() == party && c.post() == post && c.type() == type).findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + type + " for " + post + " (" + party + ")"));
    }

    public List<Candidate> all() { return all; }
}
