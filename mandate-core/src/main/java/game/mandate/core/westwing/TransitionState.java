package game.mandate.core.westwing;

import game.mandate.core.data.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The transition office (Resolute Desk's {@code transition/Transition}, its White House staff): from the day after
 * the election to the inauguration the president-elect chooses the senior staff. Each of the nine jobs has a shortlist
 * of three to five prospects; the team's first read of each is rough, an interview shows who they really are, and a
 * background check what the papers would find. The Senate-confirmed cabinet goes through the first hundred days.
 */
public final class TransitionState {
    public static final int HOURS = 24;

    /** Someone on the shortlist for a staff job. */
    public static final class Prospect {
        String id, name, profile, baggage;
        int role, kind, trait;
        double skill, loyalty, skillSeen, loyaltySeen, ideo, baggageWeight;
        boolean interviewed, vetted;

        public String id() { return id; }
        public String name() { return name; }
        public String profile() { return profile; }
        public int role() { return role; }
        /** 0 the expert, 1 the loyalist, 2 the politician, 3 the outsider, 4 the crossover pick. */
        public int kind() { return kind; }
        public boolean interviewed() { return interviewed; }
        public boolean vetted() { return vetted; }
        /** What the team can see: the true figures once interviewed, its rough read before. */
        public double skillShown() { return interviewed ? skill : skillSeen; }
        public double loyaltyShown() { return interviewed ? loyalty : loyaltySeen; }
        /** The trait, once interviewed; -1 before. */
        public int traitShown() { return interviewed ? trait : -1; }
        /** What a background check turned up: null if not checked, "" if clean. */
        public String baggageShown() { return vetted ? (baggage == null ? "" : baggage) : null; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("name", name); m.put("profile", profile); m.put("baggage", baggage);
            m.put("i", List.of(role, kind, trait));
            m.put("d", List.of(skill, loyalty, skillSeen, loyaltySeen, ideo, baggageWeight));
            m.put("b", List.of(interviewed, vetted));
            return m;
        }

        static Prospect fromJson(Map<String, Object> m) {
            Prospect p = new Prospect();
            p.id = (String) m.get("id"); p.name = (String) m.get("name"); p.profile = (String) m.get("profile"); p.baggage = (String) m.get("baggage");
            List<Object> i = Json.array(m.get("i")), d = Json.array(m.get("d")), b = Json.array(m.get("b"));
            p.role = Json.integer(i.get(0)); p.kind = Json.integer(i.get(1)); p.trait = Json.integer(i.get(2));
            p.skill = Json.num(d.get(0)); p.loyalty = Json.num(d.get(1)); p.skillSeen = Json.num(d.get(2)); p.loyaltySeen = Json.num(d.get(3));
            p.ideo = Json.num(d.get(4)); p.baggageWeight = Json.num(d.get(5));
            p.interviewed = (Boolean) b.get(0); p.vetted = (Boolean) b.get(1);
            return p;
        }
    }

    final List<Prospect> prospects = new ArrayList<>();
    /** The prospect named to each staff job (by id), or null while the job is open. */
    final String[] pick = new String[WestWingData.STAFF_TITLE.length];
    double hoursUsed;
    int leaks;
    final List<String> news = new ArrayList<>();
    boolean done;
    /** The transition's grade, 0-100, once it is over. */
    double score = -1;

    public List<Prospect> prospects() { return Collections.unmodifiableList(prospects); }
    public List<Prospect> prospects(int role) { return prospects.stream().filter(p -> p.role == role).toList(); }
    public Prospect prospect(String id) { for (Prospect p : prospects) if (p.id.equals(id)) return p; return null; }
    public String pick(int role) { return pick[role]; }
    public double hoursLeft() { return Math.max(0, HOURS - hoursUsed); }
    public int leaks() { return leaks; }
    public List<String> news() { return Collections.unmodifiableList(news); }
    public boolean done() { return done; }
    public double score() { return score; }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Object> ps = new ArrayList<>();
        for (Prospect p : prospects) ps.add(p.toJson());
        m.put("prospects", ps);
        List<Object> pk = new ArrayList<>();
        for (String s : pick) pk.add(s);
        m.put("pick", pk);
        m.put("x", List.of(hoursUsed, score));
        m.put("leaks", leaks); m.put("done", done);
        m.put("news", new ArrayList<Object>(news));
        return m;
    }

    public static TransitionState fromJson(Map<String, Object> m) {
        TransitionState t = new TransitionState();
        for (Object o : Json.array(m.get("prospects"))) t.prospects.add(Prospect.fromJson(Json.object(o)));
        List<Object> pk = Json.array(m.get("pick"));
        for (int i = 0; i < t.pick.length && i < pk.size(); i++) t.pick[i] = (String) pk.get(i);
        List<Object> x = Json.array(m.get("x"));
        t.hoursUsed = Json.num(x.get(0)); t.score = Json.num(x.get(1));
        t.leaks = Json.integer(m.get("leaks")); t.done = (Boolean) m.get("done");
        for (Object o : Json.array(m.get("news"))) t.news.add((String) o);
        return t;
    }
}
