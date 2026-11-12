package game.mandate.core.campaign;

import game.mandate.core.data.Csv;
import game.mandate.core.state.Party;

import java.util.ArrayList;
import java.util.List;

/**
 * The candidates a game can use: the fictional cast, optionally Battleground 2028's real-person roster
 * ({@code data/candidates_bg.csv}, opt-in, never for the commercial build), and optionally one player-made candidate.
 */
public final class CandidateCatalog {
    private final List<Candidate> all;

    private CandidateCatalog(List<Candidate> all) { this.all = List.copyOf(all); }

    /** The fictional cast. */
    public static CandidateCatalog load() { return load(false); }

    public static CandidateCatalog load(boolean includeRealPeople) {
        List<Candidate> list = new ArrayList<>(read("/data/candidates.csv"));
        if (includeRealPeople) list.addAll(read("/data/candidates_bg.csv"));
        return new CandidateCatalog(list);
    }

    private static List<Candidate> read(String resource) {
        List<Candidate> list = new ArrayList<>();
        for (String[] f : Csv.readResource(resource)) {
            list.add(new Candidate(f[0], Party.valueOf(f[1]), f[2], f[3], Integer.parseInt(f[4]), Integer.parseInt(f[5]),
                    Integer.parseInt(f[6]), Integer.parseInt(f[7]), Integer.parseInt(f[8]), Double.parseDouble(f[9])));
        }
        return list;
    }

    /** This catalog plus a player-made candidate (null = unchanged). */
    public CandidateCatalog with(Candidate custom) {
        if (custom == null) return this;
        if (all.stream().anyMatch(c -> c.id().equals(custom.id()))) throw new IllegalArgumentException("Duplicate candidate " + custom.id());
        List<Candidate> list = new ArrayList<>(all);
        list.add(custom);
        return new CandidateCatalog(list);
    }

    public List<Candidate> all() { return all; }
    public List<Candidate> forParty(Party p) { return all.stream().filter(c -> c.party() == p).toList(); }

    /** Fictional candidates only (the pool for the AI's choices when the player has not opted into real people). */
    public List<Candidate> fictional(Party p) { return forParty(p).stream().filter(c -> !c.isReal() && !c.id().equals(Candidate.CUSTOM_ID)).toList(); }

    public Candidate get(String id) {
        return all.stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown candidate " + id));
    }
}
