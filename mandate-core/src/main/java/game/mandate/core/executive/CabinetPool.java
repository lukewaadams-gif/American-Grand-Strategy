package game.mandate.core.executive;

import game.mandate.core.data.Csv;
import game.mandate.core.state.Party;

import java.util.ArrayList;
import java.util.List;

/**
 * Cabinet candidates by party, post and archetype: the fictional cast ({@code /data/cabinet.csv}), or with real people
 * the real prospects of {@code /data/people_2029.tsv} (the expert and the loyalist from the president's party, the
 * crossover a moderate of the other party out of office).
 */
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

    /** The real prospects for the three posts the hundred days fill. */
    public static CabinetPool real(game.mandate.core.people.RealPeople people) {
        List<Candidate> l = new ArrayList<>();
        for (Post post : Post.values()) {
            String name = switch (post) { case TREASURY -> "Treasury"; case DEFENSE -> "Defense"; case STATE -> "State"; };
            for (Party party : Party.values()) {
                List<game.mandate.core.people.RealPeople.Prospect> own = people.cabinet(name, party);
                // the crossover: a moderate of the other party out of office, as presidents reach across in fact
                List<game.mandate.core.people.RealPeople.Prospect> across = people.crossover(party.opponent());
                if (own.size() < 2 || across.isEmpty()) throw new IllegalStateException("people_2029.tsv needs two " + name + " prospects and a moderate of each party");
                l.add(new Candidate(party, post, NomineeType.EXPERT, own.get(0).name()));
                l.add(new Candidate(party, post, NomineeType.LOYALIST, own.get(1).name()));
                l.add(new Candidate(party, post, NomineeType.CROSSOVER, across.get(post.ordinal() % across.size()).name()));
            }
        }
        return new CabinetPool(l);
    }

    public Candidate pick(Party party, Post post, NomineeType type) {
        return all.stream().filter(c -> c.party() == party && c.post() == post && c.type() == type).findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + type + " for " + post + " (" + party + ")"));
    }

    public List<Candidate> all() { return all; }
}
