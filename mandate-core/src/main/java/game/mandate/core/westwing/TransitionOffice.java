package game.mandate.core.westwing;

import game.mandate.core.effects.Effect;
import game.mandate.core.news.News;
import game.mandate.core.state.Party;
import game.mandate.core.westwing.TransitionState.Prospect;
import game.mandate.core.westwing.WestWingState.Person;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static game.mandate.core.westwing.WestWingData.*;

/**
 * The transition office for the White House staff (Resolute Desk's {@code transition/Transitions}): shortlists of three
 * to five prospects for each of the nine senior jobs (always an expert and a loyalist, then a politician, an outsider
 * or a crossover pick), the team's rough first read, interviews that show who they really are, background checks that
 * find what the papers would, and a grade for the transition that lengthens or shortens the honeymoon. Whatever is
 * still open on Inauguration Day the team fills itself, with the strongest prospect.
 */
public final class TransitionOffice {
    public static final int K_EXPERT = 0, K_LOYAL = 1, K_POL = 2, K_OUTSIDER = 3, K_CROSS = 4;
    public static final String[] KIND = {"The expert", "The loyalist", "The politician", "The outsider", "The crossover pick"};
    /** Hours an interview and a background check take from the team's week (Resolute Desk's {@code TA_*}). */
    public static final double H_INTERVIEW = 1, H_CHECK = 2;

    static final int D_SECURITY = 0, D_LAW = 1, D_ECON = 2, D_DOMESTIC = 3, D_POLITICS = 4;
    /** The field of each staff role, in role order. */
    static final int[] ROLE_FIELD = {D_POLITICS, D_SECURITY, D_POLITICS, D_LAW, D_ECON, D_DOMESTIC, D_POLITICS, D_POLITICS, D_POLITICS};
    /** Resolute Desk's profiles by kind (expert, loyalist, politician, outsider, crossover) and field. {S} a state, {P} the party, {O} the other. */
    static final String[][][] PROFILE = {
            {{"Retired four-star general", "Former deputy defense secretary", "Twenty-year veteran of the intelligence agencies"},
                    {"Federal appeals judge", "Former U.S. attorney and law professor", "Former deputy attorney general"},
                    {"Former Federal Reserve governor", "Chief economist at a large bank", "Deputy Treasury secretary in the last {P} administration"},
                    {"Ran a department's largest agency for six years", "State commissioner in {S} with a national reputation", "University president and former deputy secretary"},
                    {"Veteran of two administrations", "Former chief of staff to a cabinet secretary", "A Hill veteran who ran a committee staff"}},
            {{"Your campaign's national-security adviser, a retired colonel", "A longtime aide who handled defense on your staff"},
                    {"Your personal lawyer for twenty years", "The campaign's general counsel"},
                    {"Your campaign's chief economic adviser", "The investor who ran your campaign's finances"},
                    {"Your campaign's policy director", "A {P} donor and organizer from {S}"},
                    {"Your campaign manager", "Your longest-serving aide", "Ran your campaign in {S}"}},
            {{"Senator from {S} on the Armed Services Committee", "House member from {S} and former Marine officer"},
                    {"Attorney general of {S}", "Senator from {S} on the Judiciary Committee"},
                    {"Governor of {S} who balanced eight budgets", "Senator from {S} on the Finance Committee"},
                    {"Governor of {S}", "Mayor of the biggest city in {S}", "House member from {S} who chairs a subcommittee"},
                    {"Former House member from {S} with friends on both sides", "Lieutenant governor of {S}"}},
            {{"Retired admiral who now runs a defense company", "Former police commissioner of a big city"},
                    {"Partner at a top law firm", "Civil-rights lawyer with a national practice"},
                    {"Chief executive of a big manufacturer", "Founder of an investment firm"},
                    {"Chief executive of a big hospital system", "Founder of a large company in {S}"},
                    {"Television news anchor", "Founder of a polling firm"}},
            {{"A {O} former defense secretary's deputy", "A retired general who has never voted in a primary"},
                    {"A {O} former prosecutor known for independence", "A retired federal judge appointed by a {O} president"},
                    {"A {O} former budget director known for deficit deals", "An independent economist who advised both parties"},
                    {"A {O} former governor of {S}", "A {O} mayor known for working across the aisle"},
                    {"A {O} veteran of the White House", "An independent strategist who has worked for both parties"}}};
    /** What a background check can turn up (Resolute Desk's {@code BAG_WHAT}, {@code BAG_WEIGHT}). */
    static final String[] BAG_WHAT = {"unpaid taxes on consulting income", "lobbying for a foreign government", "an old essay calling for the department to be abolished",
            "stock trades in companies they would regulate", "a failed company that left investors short", "paid speeches to a Wall Street bank",
            "a household employee paid off the books", "years of inflammatory posts and remarks"};
    static final double[] BAG_WEIGHT = {0.9, 1.4, 0.6, 1.1, 0.6, 0.5, 0.8, 1.0};
    static final String[] STATES = {"Pennsylvania", "Michigan", "Wisconsin", "Arizona", "Georgia", "North Carolina", "Nevada", "Ohio", "Virginia", "Colorado",
            "Florida", "Texas", "Minnesota", "New Hampshire", "Iowa", "Maine"};

    private final WestWingEngine engine;

    public TransitionOffice(WestWingEngine engine) { this.engine = engine; }

    /** The shortlists, drawn the day after the election. */
    public TransitionState begin(Party president, long seed, LocalDate day) {
        TransitionState t = new TransitionState();
        WestWingEngine.Local r = new WestWingEngine.Local(WestWingEngine.mix(seed, 0x7A45L, 0, 0, 0));
        List<String> taken = new ArrayList<>();
        int n = 0;
        for (int role = 0; role < STAFF_TITLE.length; role++) {
            int[] more = {K_POL, K_OUTSIDER, K_CROSS};
            for (int i = more.length - 1; i > 0; i--) { int j = r.n(i + 1); int tmp = more[i]; more[i] = more[j]; more[j] = tmp; }
            int extra = 1 + r.n(3);
            List<Integer> kinds = new ArrayList<>(List.of(K_EXPERT, K_LOYAL));
            for (int i = 0; i < extra; i++) kinds.add(more[i]);
            for (int kind : kinds) t.prospects.add(prospect(r, president, role, kind, taken, "t" + n++));
        }
        t.news.add(day + ": the transition begins. Name your own people, check them, and keep the leaks down.");
        return t;
    }

    Prospect prospect(WestWingEngine.Local r, Party party, int role, int kind, List<String> taken, String id) {
        Person p = engine.make(r, party, kind == K_LOYAL ? 0 : kind == K_EXPERT ? 1 : 2, taken);
        taken.add(p.name.substring(p.name.lastIndexOf(' ') + 1));
        if (kind == K_POL) { p.skill = clamp(p.skill + 2, 0, 100); p.loyalty = clamp(p.loyalty + 10, 0, 100); }
        if (kind == K_OUTSIDER) { p.skill = 55 + r.n(35); p.loyalty = 55 + r.n(25); }
        if (kind == K_CROSS) { p.loyalty = 45 + r.n(20); p.ideo *= 0.3; }
        if (kind == K_LOYAL && p.trait != T_LOYAL && r.u() < 0.5) p.trait = T_LOYAL;
        Prospect q = new Prospect();
        q.id = id; q.name = p.name; q.role = role; q.kind = kind; q.trait = p.trait;
        q.skill = p.skill; q.loyalty = p.loyalty; q.ideo = p.ideo;
        String[] opts = PROFILE[kind][ROLE_FIELD[role]];
        q.profile = opts[r.n(opts.length)].replace("{S}", STATES[r.n(STATES.length)])
                .replace("{P}", party == Party.DEMOCRAT ? "Democratic" : "Republican").replace("{O}", party == Party.DEMOCRAT ? "Republican" : "Democratic");
        // the team's first read is rough: within about ten points
        q.skillSeen = clamp(p.skill + r.g() * 6, 30, 99);
        q.loyaltySeen = clamp(p.loyalty + r.g() * 6, 20, 99);
        double risk = (p.trait == T_RISK ? 0.6 : 0.18) + (kind == K_OUTSIDER ? 0.1 : 0) + (kind == K_LOYAL ? 0.06 : 0);
        if (r.u() < risk) { int b = r.n(BAG_WHAT.length); q.baggage = BAG_WHAT[b]; q.baggageWeight = BAG_WEIGHT[b]; }
        return q;
    }

    /** Why the team cannot do this now, or null. */
    public String cannot(TransitionState t, Prospect q, String action) {
        if (t.done) return "The transition is over";
        boolean open = t.pick[q.role] == null;
        return switch (action) {
            case "interview" -> q.interviewed ? "Already interviewed" : t.hoursLeft() < H_INTERVIEW ? "No hours left" : null;
            case "check" -> q.vetted ? "Already checked" : t.hoursLeft() < H_CHECK ? "No hours left" : null;
            case "announce" -> !open ? "The job is filled" : null;
            default -> "No such step";
        };
    }

    /** One of the team's steps: "interview", "check" or "announce". */
    public String act(TransitionState t, String id, String action) {
        Prospect q = t.prospect(id);
        if (q == null) throw new IllegalArgumentException("No such prospect");
        String no = cannot(t, q, action);
        if (no != null) throw new IllegalArgumentException(no);
        String title = STAFF_TITLE[q.role].toLowerCase();
        switch (action) {
            case "interview" -> {
                t.hoursUsed += H_INTERVIEW; q.interviewed = true;
                return q.name + " sits down with you. " + (q.skill >= 75 ? "Impressive." : q.skill >= 60 ? "Solid." : "Not as strong as the résumé.")
                        + " " + (q.loyalty >= 75 ? "Loyal to you." : q.loyalty < 50 ? "Their own person." : "Loyal enough.");
            }
            case "check" -> {
                t.hoursUsed += H_CHECK; q.vetted = true;
                return q.baggage == null ? "The check on " + q.name + " comes back clean." : "The check on " + q.name + " turns up " + q.baggage + ".";
            }
            default -> {
                t.pick[q.role] = q.id;
                t.news.add("You name " + q.name + " " + title + ".");
                return "You name " + q.name + " " + title + ".";
            }
        }
    }

    /** The team's own choice for a job still open: the strongest prospect. */
    public Prospect teamChoice(TransitionState t, int role) {
        Prospect best = null;
        for (Prospect q : t.prospects(role)) if (best == null || q.skill - (q.baggage != null ? 10 * q.baggageWeight : 0) > best.skill) best = q;
        return best;
    }

    public Prospect chosen(TransitionState t, int role) { return t.pick[role] != null ? t.prospect(t.pick[role]) : teamChoice(t, role); }

    /**
     * How well the transition went, 0 to 100 (Resolute Desk's {@code Transitions.score}): how many jobs you filled
     * yourself, how strong and well checked the picks are; naming a chief of staff yourself is worth five more.
     */
    public double score(TransitionState t) {
        int jobs = STAFF_TITLE.length, named = 0, checked = 0;
        double skill = 0;
        for (int role = 0; role < jobs; role++) {
            Prospect q = chosen(t, role);
            if (t.pick[role] != null) named++;
            if (q.vetted) checked++;
            skill += q.skill;
        }
        double s = 40 + 25.0 * named / jobs + 15.0 * checked / jobs + (skill / jobs - 60) * 1.2 - 3 * t.leaks;
        if (t.pick[S_COS] != null) s += 5;
        return clamp(s, 0, 100);
    }

    public static String grade(double s) { return s >= 85 ? "A" : s >= 72 ? "B" : s >= 58 ? "C" : s >= 45 ? "D" : "F"; }

    /** Inauguration Day: the transition closes with its grade. */
    public double finish(TransitionState t) {
        t.score = score(t);
        t.done = true;
        return t.score;
    }

    /**
     * Day 100: the transition's people take their jobs in the West Wing, their morale set by the transition's grade.
     * A problem nobody checked comes out in the papers.
     */
    public void seat(WestWingState s, TransitionState t, LocalDate date, WestWingEngine.Outcome out) {
        double sc = t.score >= 0 ? t.score : score(t);
        for (int role = 0; role < STAFF_TITLE.length; role++) {
            Prospect q = chosen(t, role);
            Person p = new Person();
            p.name = q.name; p.party = s.staff[role] != null ? s.staff[role].party : null;
            if (p.party == null) p.party = Party.DEMOCRAT;
            p.skill = q.skill; p.loyalty = q.loyalty; p.ideo = q.ideo; p.trait = q.trait; p.background = q.profile;
            p.role = role; p.since = 0; p.confirmed = true; p.morale = clamp(70 + (sc - 60) / 4, 40, 95);
            p.id = "p" + s.nextId++;
            if (s.staff[role] != null) p.party = s.staff[role].party;
            s.staff[role] = p;
            if (q.baggage != null && !q.vetted) {
                out.news().add(new News(date, News.Kind.BAD, "In the papers: " + STAFF_TITLE[role].toLowerCase() + " " + q.name + "'s " + q.baggage, null, 1.8, null));
                out.effects().add(new Effect.Scandal(1.2 * q.baggageWeight));
            }
        }
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
