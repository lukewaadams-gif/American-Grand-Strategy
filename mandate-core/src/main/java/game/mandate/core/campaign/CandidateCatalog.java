package game.mandate.core.campaign;

import game.mandate.core.data.Csv;
import game.mandate.core.state.Party;

import java.util.ArrayList;
import java.util.List;

public final class CandidateCatalog {
    private final List<Candidate> all;

    private CandidateCatalog(List<Candidate> all) { this.all = List.copyOf(all); }

    public static CandidateCatalog load() {
        List<Candidate> list = new ArrayList<>();
        for (String[] f : Csv.readResource("/data/candidates.csv")) {
            list.add(new Candidate(f[0], Party.valueOf(f[1]), f[2], f[3], Integer.parseInt(f[4]), Integer.parseInt(f[5]),
                    Integer.parseInt(f[6]), Integer.parseInt(f[7]), Integer.parseInt(f[8]), Double.parseDouble(f[9])));
        }
        return new CandidateCatalog(list);
    }

    public List<Candidate> all() { return all; }

    public List<Candidate> forParty(Party p) { return all.stream().filter(c -> c.party() == p).toList(); }

    public Candidate get(String id) {
        return all.stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown candidate " + id));
    }
}
