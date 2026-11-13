package game.mandate.core.people;

import game.mandate.core.data.Csv;
import game.mandate.core.state.Party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The real people of January 2029 (data/people_2029.tsv): the senators by seat, the party leaders, each committee's
 * leaders, the moderates the whips count on, cabinet prospects and Supreme Court prospects. Engines seat them when the
 * game uses real people; where the record has no one (a seat that changed hands, someone known to have left office), the
 * office's placeholder is used, never an invented name.
 */
public final class RealPeople {
    public record Senator(String state, int seatClass, Party party, String name, int until) {}
    public record Committee(String chamber, String committee, Party party, String name, int until) {}
    public record Swing(String chamber, Party party, String state, String name, int until) {}
    public record Prospect(String post, Party party, String name, String background) {}
    public record Judge(Party party, String name, int born) {}
    public record Staffer(String role, Party party, String name, String background) {}

    private final List<Senator> senators = new ArrayList<>();
    private final Map<String, String> leaders = new LinkedHashMap<>();
    private final List<Committee> committees = new ArrayList<>();
    private final List<Swing> swing = new ArrayList<>();
    private final List<Swing> base = new ArrayList<>();
    private final List<Prospect> cabinet = new ArrayList<>();
    private final List<Prospect> crossover = new ArrayList<>();
    private final List<Judge> judges = new ArrayList<>();
    private final List<Staffer> staff = new ArrayList<>();
    private final java.util.Set<String> names = new java.util.HashSet<>();

    private RealPeople() {}

    public static RealPeople load() {
        RealPeople p = new RealPeople();
        for (String[] r : Csv.readTsv("/data/people_2029.tsv")) {
            switch (r[0]) {
                case "senator" -> p.senators.add(new Senator(r[1], Integer.parseInt(r[2]), party(r[3]), r[4], until(r, 5)));
                case "leader" -> p.leaders.put(r[1], r[2]);
                case "chair" -> p.committees.add(new Committee(r[1], r[2], party(r[3]), r[4], until(r, 5)));
                case "swing" -> p.swing.add(new Swing(r[1], party(r[2]), r[3], r[4], until(r, 5)));
                case "base" -> p.base.add(new Swing(r[1], party(r[2]), r[3], r[4], 9999));
                case "cabinet" -> p.cabinet.add(new Prospect(r[1], party(r[2]), r[3], r[4]));
                case "crossover" -> p.crossover.add(new Prospect("", party(r[1]), r[2], r[3]));
                case "judge" -> p.judges.add(new Judge(party(r[1]), r[2], Integer.parseInt(r[3])));
                case "staff" -> p.staff.add(new Staffer(r[1], party(r[2]), r[3], r[4]));
                default -> throw new IllegalStateException("people_2029.tsv: unknown kind " + r[0]);
            }
        }
        for (Senator x : p.senators) p.names.add(x.name());
        p.names.addAll(p.leaders.values());
        for (Committee x : p.committees) p.names.add(x.name());
        for (Swing x : p.swing) p.names.add(x.name());
        for (Swing x : p.base) p.names.add(x.name());
        for (Prospect x : p.cabinet) p.names.add(x.name());
        for (Prospect x : p.crossover) p.names.add(x.name());
        for (Judge x : p.judges) p.names.add(x.name());
        for (Staffer x : p.staff) p.names.add(x.name());
        for (String[] f : Csv.readResource("/data/candidates_bg.csv")) p.names.add(f[2]);
        return p;
    }

    static Party party(String s) { return s.equals("D") ? Party.DEMOCRAT : Party.REPUBLICAN; }
    static int until(String[] r, int i) { return r.length > i && !r[i].isBlank() ? Integer.parseInt(r[i].trim()) : 9999; }

    /** The senator holding a seat in the given year, if the record has one of that party still serving; else empty. */
    public Optional<String> senator(String state, int seatClass, Party party, int year) {
        for (Senator s : senators)
            if (s.state().equals(state) && s.seatClass() == seatClass && s.party() == party && s.until() >= year) return Optional.of(s.name());
        return Optional.empty();
    }

    public Optional<String> leader(String key) { return Optional.ofNullable(leaders.get(key)); }

    public Optional<String> committeeLeader(String chamber, String committee, Party party, int year) {
        for (Committee c : committees)
            if (c.chamber().equals(chamber) && c.committee().equals(committee) && c.party() == party && c.until() >= year) return Optional.of(c.name());
        return Optional.empty();
    }

    public List<Swing> swing(String chamber, Party party, int year) {
        List<Swing> l = new ArrayList<>();
        for (Swing s : swing) if (s.chamber().equals(chamber) && s.party() == party && s.until() >= year) l.add(s);
        return l;
    }

    /** Stalwarts of a party's base in a chamber (the whip's base member). */
    public List<Swing> base(String chamber, Party party) {
        List<Swing> l = new ArrayList<>();
        for (Swing s : base) if (s.chamber().equals(chamber) && s.party() == party) l.add(s);
        return l;
    }

    /** A party's moderates out of office: the other party's president's bipartisan picks. */
    public List<Prospect> crossover(Party party) {
        List<Prospect> l = new ArrayList<>();
        for (Prospect p : crossover) if (p.party() == party) l.add(p);
        return l;
    }

    public List<Prospect> cabinet(String post, Party party) {
        List<Prospect> l = new ArrayList<>();
        for (Prospect p : cabinet) if (p.post().equals(post) && p.party() == party) l.add(p);
        return l;
    }

    public List<Judge> judges(Party party) {
        List<Judge> l = new ArrayList<>();
        for (Judge j : judges) if (j.party() == party) l.add(j);
        return l;
    }

    public List<Staffer> staff(String role, Party party) {
        List<Staffer> l = new ArrayList<>();
        for (Staffer x : staff) if (x.role().equals(role) && x.party() == party) l.add(x);
        return l;
    }

    /** Whether a name belongs to a real person the game seats (this file, or the real candidate roster). */
    public boolean isReal(String name) { return names.contains(name); }

    public List<Senator> senators() { return Collections.unmodifiableList(senators); }
    public List<Committee> allCommittees() { return Collections.unmodifiableList(committees); }
    public List<Staffer> allStaff() { return Collections.unmodifiableList(staff); }
    public List<Prospect> allCabinet() { return Collections.unmodifiableList(cabinet); }
}
