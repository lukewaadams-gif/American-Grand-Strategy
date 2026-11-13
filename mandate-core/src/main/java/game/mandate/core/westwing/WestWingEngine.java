package game.mandate.core.westwing;

import game.mandate.core.campaign.Issue;
import game.mandate.core.data.Fmt;
import game.mandate.core.effects.Effect;
import game.mandate.core.news.News;
import game.mandate.core.news.RdFx;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.state.Party;
import game.mandate.core.westwing.WestWingState.Decision;
import game.mandate.core.westwing.WestWingState.Person;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import static game.mandate.core.westwing.WestWingData.*;

/**
 * Resolute Desk's executive branch (executive/Cabinet, Actions, Courts, Trouble, VicePres, press/QA, Speech, Social,
 * home/HomeFront's press, stories/Storylines): the staff and the cabinet, the Senate's confirmations, orders and rules,
 * the courts, the vice president, scandals and pardons, the press, speeches, and the developing stories. Each week it
 * moves on in Resolute Desk's order; the president's orders arrive as {@link WestWingOrder}s. What it does to the rest of
 * the game comes back as effects for the router.
 */
public final class WestWingEngine {
    /** The president's skills on Resolute Desk's 0-100 scale, by index. */
    public static final int CHA = 0, INT = 1, DIS = 2, DEB = 3, SPE = 4, SMD = 5, SCA = 6, EXE = 7, FPC = 8;

    /** A Senate bloc, as the confirmation votes see it. */
    public record SenateBloc(String name, Party party, int seats, double econ, double cult, double loyalty, double rel) {}

    /** A real politician who can be offered a cabinet post when the real-person roster is on. */
    public record RealPick(String name, Party party, double skill, double ideology) {}

    /** What the West Wing reads from the rest of the game for one step. */
    public record Context(LocalDate date, long seed, int termWeek, Party president, String presidentName, double approval,
                          Map<Issue, Double> issueApproval, Map<Issue, Double> salience, double stamina, double stress, double[] skills,
                          List<SenateBloc> senate, Party houseMajority, Party senateMajority, double polarization, boolean shutdown,
                          double unemployment, double inflation, boolean midtermSeason, String ownBill, String ownBillId, boolean ownBillPublic, int cabinetMeetings,
                          ToDoubleFunction<String> var, boolean realPeople, List<RealPick> realPicks, double[] groups) {
        double skill(int k) { return skills[k]; }
        int side() { return president == Party.REPUBLICAN ? 1 : -1; }
        boolean holdsSenate() { return senateMajority == president; }
        boolean opposesBoth() { return houseMajority != president && senateMajority != president; }
    }

    /** What a step or an order did: transitions for the router, and news. */
    public record Outcome(List<Effect> effects, List<News> news) {
        public static Outcome empty() { return new Outcome(new ArrayList<>(), new ArrayList<>()); }
    }

    final WestWingData data;
    final Actions actions;
    final Courts courts;
    final Trouble trouble;
    final Press press;
    final Stories stories;

    /** Who is appointed: the real roster in 2028-29 and fictional newcomers from 2030, or the fictional cast throughout. */
    private game.mandate.core.people.People people = game.mandate.core.people.People.fictional();

    /** Sets who the West Wing appoints (GameEngine, from the game's setup). */
    public void people(game.mandate.core.people.People p) { this.people = p; }
    game.mandate.core.people.People people() { return people; }

    public WestWingEngine(WestWingData data) {
        this.data = data;
        this.actions = new Actions(this);
        this.courts = new Courts(this);
        this.trouble = new Trouble(this);
        this.press = new Press(this);
        this.stories = new Stories(this);
    }

    public WestWingData data() { return data; }

    // =========================================================================================
    //  Seating
    // =========================================================================================

    /**
     * The West Wing on day 100: each staff job held by its expert, each department by its expert, confirmed (the
     * Senate's votes of the spring are behind it), except the three posts the hundred days filled, which are given as
     * {@code first}: post index to a name and kind (0 loyalist, 1 expert, 2 bipartisan), with null for one still empty.
     */
    public WestWingState seat(Context c, Map<Integer, String[]> first, DeterministicRandomManager rng) {
        WestWingState s = new WestWingState();
        for (int r = 0; r < WestWingState.NSTAFF; r++) {
            Person p = staffOptions(s, c, r, 0).get(0);
            p.role = r; p.since = 0; p.id = "p" + s.nextId++;
            s.staff[r] = p;
        }
        Local init = new Local(c.seed() ^ 0xCAB1L);
        for (int k = 0; k < WestWingState.NPOST; k++) {
            s.wf[k] = POST_WF[k];
            s.ready[k] = 62 + init.g() * 5;
            s.voteAt[k] = -1;
            Person p;
            if (first.containsKey(k)) {
                String[] f = first.get(k);
                if (f == null) continue;
                int kind = Integer.parseInt(f[1]);
                p = make(new Local(mix(c.seed(), 0xF1257L, k, 0, 0)), kind == 2 ? c.president().opponent() : c.president(), kind, taken(s));
                p.name = f[0];
            } else p = options(s, c, k, 0).get(0);
            p.post = k; p.confirmed = true; p.since = 0; p.morale = 75; p.id = "p" + s.nextId++;
            p.voteYes = 51 + init.n(30); p.voteNo = 100 - p.voteYes;
            s.post[k] = p;
        }
        // the vice president: with real people, a running mate from the party's real roster; else made up for the ticket
        Local vp = new Local(mix(c.seed(), 0x7EEL, 0, 0, 0));
        boolean f = vp.u() < 0.4;
        s.vpName = pick(vp, f ? "first_f" : "first_m") + " " + pick(vp, "lasts");
        if (people.real()) {
            List<RealPick> mates = new ArrayList<>();
            if (c.realPicks() != null) for (RealPick p : c.realPicks()) if (p.party() == c.president()) mates.add(p);
            s.vpName = mates.isEmpty() ? "the Vice President" : mates.get(vp.n(mates.size())).name();
        }
        for (int i = 0; i < 4; i++) s.vpSkill[i] = 50 + vp.n(30);
        // the Court
        for (JusticeDef d : data.court()) {
            WestWingState.Justice j = new WestWingState.Justice();
            j.name = c.realPeople() ? d.real() : d.name(); j.born = d.born(); j.party = d.party(); j.ideo = d.ideology();
            s.justices.add(j);
        }
        // the press: each outlet's tone starts where it is heading
        for (int o = 0; o < WestWingState.NOUTLET; o++) s.tone[o] = press.toneTarget(s, c, o);
        s.sotuYear = c.date().getYear();     // the joint session of the first year is behind
        s.record.add(c.date() + ": the West Wing is staffed");
        return s;
    }

    // =========================================================================================
    //  The week (Resolute Desk's Week.resolve, the executive's part)
    // =========================================================================================

    public Outcome week(WestWingState s, Context c, DeterministicRandomManager rng) {
        Outcome out = Outcome.empty();
        s.week++;
        if (c.cabinetMeetings() > 0) for (int i = 0; i < c.cabinetMeetings(); i++) meeting(s, c, out);
        cabinetWeek(s, c, out, rng);
        actions.week(s, c, out, rng);
        courts.week(s, c, out, rng);
        trouble.week(s, c, out, rng);
        vpWeek(s, c, out, rng);
        press.week(s, c, out, rng);
        stories.week(s, c, out, rng);
        overdue(s, c, out, rng);
        return out;
    }

    // ---- quality, as the rest of the game reads it ---------------------------------------------------------------

    /** A staff member's quality (Resolute Desk's {@code sq}): 0.4 if the job is empty. */
    public static double sq(WestWingState s, int role) { return s.staff[role] == null ? 0.4 : s.staff[role].quality(); }

    /** A cabinet officer's quality (Resolute Desk's {@code pq}): 0.45 for an acting career official. */
    public static double pq(WestWingState s, int post) { return s.post[post] == null ? 0.45 : s.post[post].quality(); }

    /** A department's readiness, 5 to 100. */
    public static double rd(WestWingState s, int post) { return s.ready[post]; }

    // ---- the cabinet's week -----------------------------------------------------------------------------------------

    private void meeting(WestWingState s, Context c, Outcome out) {
        for (Person p : s.post) if (p != null) p.morale = Math.min(100, p.morale + 3);
        for (int k = 0; k < WestWingState.NPOST; k++) s.ready[k] = Math.min(100, s.ready[k] + 0.6);
        if (s.week - s.lastCabinet > 6) out.news().add(news(c, News.Kind.INFO, "The president holds a cabinet meeting", null, 0.4, "Executive"));
        s.lastCabinet = s.week;
    }

    private void cabinetWeek(WestWingState s, Context c, Outcome out, DeterministicRandomManager rng) {
        // 1. confirmation votes due; a hostile Senate sits on a weak nominee for up to twenty weeks
        for (int k = 0; k < WestWingState.NPOST; k++) {
            Person n = s.nominee[k];
            if (n == null || s.voteAt[k] < 0 || s.week < s.voteAt[k]) continue;
            if (!c.holdsSenate() && chance(c, n) < 0.2 && s.week - n.nominated < 20) {
                if (s.voteAt[k] == s.week) out.news().add(news(c, News.Kind.BAD, "The Senate majority sits on the nomination of " + n.name, POST_TITLE[k] + " stays vacant for now.", 0.8, "Executive"));
                s.voteAt[k] = s.week + 2;
            } else vote(s, c, k, out, rng);
        }
        // 2. morale
        List<Person> all = s.everyone();
        int ab = 0;
        for (Person p : all) if (p.trait == T_ABRASIVE) ab++;
        for (Person p : all) {
            double target = 58 + (c.approval() - 45) * 0.7 + (p.loyalty - 60) * 0.35 - Math.max(0, c.stress() - 45) * 0.25
                    - (ab - (p.trait == T_ABRASIVE ? 1 : 0)) * 2.5 + (c.shutdown() ? -6 : 0) + (p.trait == T_STEADY ? 4 : 0);
            p.morale = clamp(p.morale + (target - p.morale) * 0.05 + rng.gaussian("ww.morale") * 0.8, 0, 100);
        }
        // 3. the chief of staff takes some of the strain
        double relief = 2.5 * (sq(s, S_COS) - 0.4);
        if (relief != 0) out.effects().add(new Effect.PresidentStrain(0, -relief));
        // 4. departures, at most one a week
        for (Person p : all) {
            if (s.week - p.since < 12) continue;
            double hz = 0.0006 + Math.max(0, 38 - p.morale) / 700 + (p.trait == T_AMBITIOUS && c.termWeek() > 70 ? 0.003 : 0) - (p.trait == T_LOYAL ? 0.0004 : 0);
            if (rng.uniform("ww.depart") < hz) { resign(s, c, p, out); break; }
        }
        // 5. the departments
        for (int k = 0; k < WestWingState.NPOST; k++) {
            double cut = Math.max(0, (POST_WF[k] - s.wf[k]) / POST_WF[k]);
            Person p = s.post[k];
            double target = 40 + 42 * pq(s, k) - 70 * cut + (s.week - s.lastCabinet < 8 ? 3 : 0) - (c.shutdown() ? 12 : 0)
                    + (p != null && p.trait == T_WORKHORSE ? 5 : 0) + (p != null && p.trait == T_EXPERT ? 3 : 0);
            s.ready[k] = clamp(s.ready[k] + (target - s.ready[k]) * 0.04, 5, 100);
        }
        // 6. leaks, 7. ethics: the first to happen this week
        for (Person p : s.everyone()) {
            double pl = (0.0007 + (p.trait == T_LEAKER ? 0.006 : 0) + Math.max(0, 50 - p.morale) / 9000 + (p.loyalty < 50 ? 0.0015 : 0)) * (1.3 - 0.7 * sq(s, S_COS));
            if (rng.uniform("ww.leak") < pl) { trouble.leak(s, c, p, out, rng); break; }
        }
        for (Person p : s.everyone()) {
            if (rng.uniform("ww.ethics") < (p.trait == T_RISK ? 0.0035 : 0.00025)) { trouble.ethics(s, c, p, out, rng); break; }
        }
        if (s.delegPeople) fill(s, c, out);
    }

    /** Resolute Desk's {@code resign}: someone leaves; the big jobs make bigger news. */
    void resign(WestWingState s, Context c, Person p, Outcome out) {
        String why = p.trait == T_AMBITIOUS && c.termWeek() > 70 ? "to run for office" : p.morale < 30 ? "after months of friction" : "to return to private life";
        boolean big = p.role == S_COS || p.role == S_NSA || p.post == P_STATE || p.post == P_TREAS || p.post == P_DEF || p.post == P_DOJ;
        String title = title(p);
        remove(s, p);
        s.departures++;
        out.news().add(news(c, big ? News.Kind.BAD : News.Kind.INFO, title + " " + p.name + " resigns " + why, null, big ? 2.2 : 1.0, big ? null : "Executive"));
        if (big) out.effects().add(new Effect.Performance(-0.4));
        s.record.add(c.date() + ": " + title.toLowerCase() + " " + p.name + " resigned " + why);
        if (!s.delegPeople) info(s, c, "resign", title + " " + p.name + " resigns",
                p.name + " is leaving " + why + ". " + (p.post >= 0 ? "An acting official runs the department until the Senate confirms a successor." : "The job is open until you hire someone."));
    }

    void remove(WestWingState s, Person p) {
        if (p.role >= 0 && s.staff[p.role] == p) s.staff[p.role] = null;
        if (p.post >= 0 && s.post[p.post] == p) s.post[p.post] = null;
    }

    static String title(Person p) { return p.role >= 0 ? STAFF_TITLE[p.role] : p.post >= 0 ? POST_TITLE[p.post] : "Aide"; }

    /** With the people delegated, the chief of staff fills empty jobs: the expert, or a nominee the Senate will take. */
    void fill(WestWingState s, Context c, Outcome out) {
        for (int r = 0; r < WestWingState.NSTAFF; r++) if (s.staff[r] == null) hire(s, c, r, 0, out);
        for (int k = 0; k < WestWingState.NPOST; k++) {
            if (s.post[k] != null || s.nominee[k] != null) continue;
            List<Person> o = options(s, c, k, s.departures);
            int best = 0;
            for (int i = 1; i < o.size(); i++) if (chance(c, o.get(i)) > chance(c, o.get(0)) + 0.15) best = i;
            nominate(s, c, k, best, out);
        }
        // and a seat on the Court: the respected judge from the mainstream of the party
        if (s.justices.size() < 9 && s.nomineeJ == null) courts.nominate(s, c, 1, out, null);
    }

    // ---- people ----------------------------------------------------------------------------------------------------------

    /** Three people for a staff job (expert, loyalist, outsider), the same for four weeks until someone leaves. */
    public List<Person> staffOptions(WestWingState s, Context c, int role, int salt) {
        Local r = new Local(mix(c.seed(), 0x57AFL, role, s.week / 4, salt));
        List<String> taken = taken(s);
        List<Person> out = new ArrayList<>();
        if (people.real()) {
            // the real staff of the last two administrations while any are free; newcomers from 2030; else an acting official
            int year = c.date().getYear(), k = 0;
            for (var x : people.data().staff(WestWingData.STAFF_TITLE[role], c.president())) {
                if (out.size() >= 3 || servingName(s, x.name())) continue;
                Person p = make(r, c.president(), new int[]{1, 0, 2}[k++ % 3], taken);
                p.name = x.name(); p.background = x.background();
                out.add(p);
            }
            while (out.size() < 3 && people.newcomer(year, r.u())) { Person p = make(r, c.president(), out.size() == 0 ? 1 : 0, taken); taken.add(last(p.name)); out.add(p); }
            if (out.isEmpty()) out.add(acting(r, c.president(), "Acting " + WestWingData.STAFF_TITLE[role].toLowerCase(java.util.Locale.ROOT), taken));
            secondTermPool(s, out);
            return out;
        }
        for (int kind : new int[]{1, 0, 2}) {
            Person p = make(r, c.president(), kind, taken);
            taken.add(last(p.name));
            out.add(p);
        }
        out.get(0).background = switch (role) {
            case S_COUNSEL -> "Former federal prosecutor";
            case S_NEC -> "Economist";
            case S_LEG -> "Hill veteran";
            case S_PRESS, S_COMMS -> "Former network correspondent";
            default -> "Veteran of two administrations";
        };
        out.get(1).background = "Campaign veteran";
        secondTermPool(s, out);
        return out;
    }

    /** A department's readiness moves (the world's operations and posture draw on Defense). */
    public boolean readiness(WestWingState s, int post, double delta) {
        if (post < 0 || post >= WestWingState.NPOST) return false;
        s.ready[post] = clamp(s.ready[post] + delta, 5, 100);
        return true;
    }

    /** Second-term hires: the best people have served already, and the ones left owe the president less. */
    static void secondTermPool(WestWingState s, List<Person> out) {
        if (s.term < 2) return;
        for (Person p : out) { p.skill = Math.max(30, p.skill - 6); p.loyalty = Math.max(20, p.loyalty - 8); }
    }

    /**
     * The start of a second term (Resolute Desk's {@code Terms.startTerm}): a few secretaries move on, their deputies
     * acting until successors are confirmed. Returns the posts that changed hands.
     */
    public List<String> secondTerm(WestWingState s, Context c, Outcome out, DeterministicRandomManager rng) {
        s.term = 2;
        List<String> gone = new ArrayList<>();
        for (int k = 0; k < WestWingState.NPOST && gone.size() < 4; k++) {
            Person p = s.post[k];
            if (p == null || rng.uniform("ww.term") > 0.22) continue;
            remove(s, p);
            s.departures++;
            gone.add(POST_TITLE[k]);
            out.news().add(news(c, News.Kind.INFO, p.name + " steps down as " + POST_TITLE[k].toLowerCase() + " at the end of the term", null, 1.0, "Executive"));
            s.record.add(c.date() + ": " + POST_TITLE[k].toLowerCase() + " " + p.name + " left at the end of the term");
        }
        info(s, c, "info", "Four More Years", "You took the oath again today. " + (gone.isEmpty() ? "Your cabinet stays on. "
                : gone.size() + (gone.size() == 1 ? " member" : " members") + " of your cabinet are leaving; their deputies are acting until you name successors. ")
                + "Second terms are hard: the honeymoon is short, the other party smells a lame duck, and history is watching.");
        if (s.delegPeople) fill(s, c, out);
        return gone;
    }

    /** Three nominees for a post: the expert, the loyalist, and a bipartisan pick (or, with the real roster, a known politician). */
    public List<Person> options(WestWingState s, Context c, int post, int salt) {
        Local r = new Local(mix(c.seed(), 0xCAB1L, post, s.week / 4, salt));
        List<String> taken = taken(s);
        List<Person> out = new ArrayList<>();
        if (people.real()) {
            // real prospects of the president's party, and one of the other party for the bipartisan pick; newcomers from
            // 2030; else an acting secretary (never an invented name before 2030)
            int year = c.date().getYear();
            String postName = WestWingData.POST_NAME[post];
            for (var x : people.data().cabinet(postName, c.president())) {
                if (out.size() >= 2 || servingName(s, x.name())) continue;
                Person p = make(r, c.president(), out.isEmpty() ? 1 : 0, taken);
                p.name = x.name(); p.background = x.background();
                out.add(p);
            }
            // the bipartisan pick: a moderate of the other party out of office, as presidents reach across in fact
            List<game.mandate.core.people.RealPeople.Prospect> across = people.data().crossover(c.president().opponent());
            for (int i = 0; i < across.size(); i++) {
                var x = across.get((post + salt + i) % across.size());
                if (servingName(s, x.name())) continue;
                Person p = make(r, c.president().opponent(), 2, taken);
                p.name = x.name(); p.background = x.background();
                out.add(p);
                break;
            }
            while (out.size() < 3 && people.newcomer(year, r.u())) { Person p = make(r, c.president(), out.size() == 0 ? 1 : 0, taken); taken.add(last(p.name)); out.add(p); }
            if (out.isEmpty()) out.add(acting(r, c.president(), "Acting " + WestWingData.POST_TITLE[post].toLowerCase(java.util.Locale.ROOT), taken));
            secondTermPool(s, out);
            return out;
        }
        Person e = make(r, c.president(), 1, taken); taken.add(last(e.name)); out.add(e);
        Person l = make(r, c.president(), 0, taken); taken.add(last(l.name)); out.add(l);
        RealPick big = null;
        if (c.realPeople() && c.realPicks() != null) {
            List<RealPick> free = new ArrayList<>();
            for (RealPick p : c.realPicks()) if (p.party() == c.president() && !servingName(s, p.name())) free.add(p);
            if (!free.isEmpty() && r.u() < 0.5) big = free.get(r.n(free.size()));
        }
        if (big != null) {
            Person p = new Person();
            p.name = big.name(); p.party = big.party(); p.skill = clamp(big.skill(), 50, 90); p.loyalty = 60 + r.n(15);
            p.ideo = clamp(big.ideology(), -1, 1); p.trait = r.u() < 0.5 ? T_MEDIA : T_STEADY; p.background = "A well-known figure in your party";
            out.add(p);
        } else {
            boolean other = r.u() < 0.5;
            Person p = make(r, other ? c.president().opponent() : c.president(), 2, taken);
            if (other) p.background = c.president() == Party.DEMOCRAT ? "A Republican former governor" : "A Democratic former governor";
            out.add(p);
        }
        secondTermPool(s, big == null ? out : out.subList(0, 2));
        return out;
    }

    private static boolean servingName(WestWingState s, String name) {
        for (Person p : s.everyone()) if (p.name.equals(name)) return true;
        for (Person p : s.nominee) if (p != null && p.name.equals(name)) return true;
        return false;
    }

    /** An office-labelled placeholder (an acting official) when the record has no one left and newcomers are not yet due. */
    private Person acting(Local r, Party party, String title, List<String> taken) {
        Person p = make(r, party, 0, taken);
        p.name = Character.toUpperCase(title.charAt(0)) + title.substring(1);
        p.background = "Serving in an acting capacity";
        return p;
    }

    /** Resolute Desk's {@code Cabinet.make}: kind 0 loyalist, 1 expert, 2 bipartisan or outsider. */
    Person make(Local r, Party party, int kind, List<String> taken) {
        boolean female = r.u() < 0.42;
        String last = pick(r, "lasts");
        for (int i = 0; i < 40 && taken.contains(last); i++) last = r.u() < 0.5 ? pick(r, "lasts") : pick(r, "more_lasts");
        String first = pick(r, female ? "first_f" : "first_m");
        int side = party == Party.REPUBLICAN ? 1 : -1;
        Person p = new Person();
        p.name = first + " " + last;
        p.party = party;
        switch (kind) {
            case 0 -> { p.skill = 52 + r.n(20); p.loyalty = 80 + r.n(16); p.ideo = side * (0.55 + r.u() * 0.35); p.trait = r.u() < 0.5 ? T_LOYAL : pickTrait(r); }
            case 1 -> { p.skill = 70 + r.n(22); p.loyalty = 52 + r.n(25); p.ideo = side * (0.2 + r.u() * 0.3); p.trait = r.u() < 0.45 ? T_EXPERT : pickTrait(r); }
            default -> { p.skill = 60 + r.n(22); p.loyalty = 40 + r.n(25); p.ideo = side * r.u() * 0.25; p.trait = pickTrait(r); }
        }
        p.background = BACKGROUNDS[r.n(BACKGROUNDS.length)];
        return p;
    }

    static int pickTrait(Local r) {
        double x = r.u();
        return x < 0.12 ? T_LEAKER : x < 0.22 ? T_ABRASIVE : x < 0.34 ? T_AMBITIOUS : x < 0.42 ? T_RISK : r.n(6);
    }

    String pick(Local r, String pool) { List<String> l = data.names(pool); return l.get(r.n(l.size())); }

    private static String last(String name) { return name.substring(name.lastIndexOf(' ') + 1); }

    private static List<String> taken(WestWingState s) {
        List<String> t = new ArrayList<>();
        for (Person p : s.everyone()) t.add(last(p.name));
        for (Person p : s.nominee) if (p != null) t.add(last(p.name));
        return t;
    }

    // ---- hiring, firing, nominations and the Senate ------------------------------------------------------------------

    void hire(WestWingState s, Context c, int role, int option, Outcome out) {
        Person p = staffOptions(s, c, role, s.departures).get(option);
        if (s.staff[role] != null) {
            Person old = s.staff[role];
            s.staff[role] = null;
            s.departures++;
            out.news().add(news(c, News.Kind.INFO, old.name + " leaves the White House", null, 0.6, "Executive"));
        }
        p.id = "p" + s.nextId++; p.role = role; p.since = s.week; p.morale = 75;
        s.staff[role] = p;
        out.news().add(news(c, News.Kind.INFO, "The president names " + p.name + " " + STAFF_TITLE[role].toLowerCase(), null, role == S_COS ? 1.4 : 0.7, role == S_COS ? null : "Executive"));
        s.record.add(c.date() + ": hired " + p.name + " as " + STAFF_TITLE[role].toLowerCase());
    }

    void fire(WestWingState s, Context c, Person p, Outcome out) {
        boolean big = p.role == S_COS || p.post == P_STATE || p.post == P_TREAS || p.post == P_DEF || p.post == P_DOJ;
        String title = title(p);
        remove(s, p);
        out.news().add(news(c, big ? News.Kind.BAD : News.Kind.BAD, "The president fires " + title.toLowerCase() + " " + p.name, null, big ? 2.4 : 1.2, big ? null : "Executive"));
        out.effects().add(new Effect.Performance(big ? -0.8 : -0.3));
        for (Person x : s.everyone()) x.morale = clamp(x.morale - 2, 0, 100);
        s.departures++;
        if (p.post == P_DOJ) for (WestWingState.Scandal sc : s.scandals) if (!sc.over && sc.stage == 2) {
            sc.severity += 3;
            sc.impeachable = true;
            out.news().add(news(c, News.Kind.BAD, "Firing the attorney general in the middle of a special counsel's inquiry draws comparisons to Watergate", null, 3, null));
        }
        s.record.add(c.date() + ": fired " + title.toLowerCase() + " " + p.name);
    }

    void nominate(WestWingState s, Context c, int post, int option, Outcome out) {
        Person p = options(s, c, post, s.departures).get(option);
        p.id = "p" + s.nextId++; p.post = post; p.nominated = s.week;
        s.nominee[post] = p;
        int busy = 0;
        for (int k = 0; k < WestWingState.NPOST; k++) if (s.nominee[k] != null && s.voteAt[k] >= s.week) busy++;
        s.voteAt[post] = s.week + 3 + busy / 3;
        out.news().add(news(c, News.Kind.INFO, "The president nominates " + p.name + " as " + POST_TITLE[post].toLowerCase(), null, 0.9, "Executive"));
    }

    void withdraw(WestWingState s, Context c, int post, Outcome out) {
        Person n = s.nominee[post];
        if (n == null) throw new IllegalArgumentException("No nominee for that post");
        s.nominee[post] = null;
        s.voteAt[post] = -1;
        out.effects().add(new Effect.Performance(-0.2));
        out.news().add(news(c, News.Kind.BAD, "The White House withdraws the nomination of " + n.name, null, 1.0, "Executive"));
    }

    /** A Senate bloc's chance of voting for a nominee (Resolute Desk's {@code blocP}). */
    double blocP(Context c, SenateBloc b, Person p) {
        double bIdeo = (b.econ() + b.cult()) / 2, dist = Math.abs(p.ideo - bIdeo);
        double l = 0.6 + (p.skill - 62) / 22 + (b.rel() - 50) / 40 + (p.trait == T_EXPERT ? 0.4 : 0);
        if (b.party() == c.president()) l += 3.0 * b.loyalty() - 1.0 * dist;
        else l += 0.5 - 2.2 * dist - 1.2 * c.polarization();
        if (p.party != c.president()) l += b.party() == p.party ? 1.6 : -0.4;
        return logistic(1.5 * l);
    }

    /** Expected yes votes and their spread. */
    public double[] count(Context c, Person p) {
        double yes = 0, var = 0;
        for (SenateBloc b : c.senate()) { double q = blocP(c, b, p); yes += b.seats() * q; var += b.seats() * q * (1 - q); }
        return new double[]{yes, Math.sqrt(var) + 0.8};
    }

    /** The chance a nominee is confirmed. */
    public double chance(Context c, Person p) {
        double[] k = count(c, p);
        return logistic(1.702 * (k[0] - 50 + 0.5) / k[1]);
    }

    private void vote(WestWingState s, Context c, int post, Outcome out, DeterministicRandomManager rng) {
        Person p = s.nominee[post];
        double[] k = count(c, p);
        int yes = (int) Math.round(clamp(k[0] + rng.gaussian("ww.confirm") * k[1], 0, 100));
        boolean tie = yes == 50 && c.holdsSenate();
        s.nominee[post] = null;
        s.voteAt[post] = -1;
        p.voteYes = yes; p.voteNo = 100 - yes;
        if (yes > 50 || tie) {
            p.confirmed = true; p.since = s.week; p.morale = 75;
            s.post[post] = p;
            out.news().add(news(c, News.Kind.GOOD, "The Senate confirms " + p.name + " as " + POST_TITLE[post].toLowerCase() + ", " + yes + "-" + (100 - yes) + (tie ? " with the vice president's vote" : ""),
                    null, post <= P_DEF || tie ? 1.4 : 0.8, post <= P_DHS || post == P_OMB ? null : "Executive"));
            s.record.add(c.date() + ": the Senate confirmed " + p.name + " as " + POST_TITLE[post].toLowerCase() + ", " + yes + "-" + (100 - yes));
        } else {
            out.news().add(news(c, News.Kind.BAD, "The Senate rejects " + p.name + " for " + POST_TITLE[post].toLowerCase() + ", " + yes + "-" + (100 - yes), null, 2.6, null));
            out.effects().add(new Effect.Performance(-1.0));
            out.effects().add(new Effect.BlocRelations(true, -1));
            s.record.add(c.date() + ": the Senate rejected " + p.name + " for " + POST_TITLE[post].toLowerCase());
            info(s, c, "rejected", "The Senate rejects " + p.name, "The nomination failed " + yes + "-" + (100 - yes) + ". An acting official runs the department until you choose someone else.");
        }
    }

    // ---- the vice president ------------------------------------------------------------------------------------------

    void vpStart(WestWingState s, Context c, int mission, String arg, Outcome out) {
        s.vpMission = mission;
        s.vpArg = mission == VM_TASK ? arg : null;
        s.vpUntil = VM_WEEKS[mission] > 0 ? s.week + VM_WEEKS[mission] : -1;
        if (mission != VM_NONE) {
            s.vpMissions++;
            out.news().add(news(c, News.Kind.INFO, "Vice President " + s.vpName + ": " + VM_NAME[mission].toLowerCase()
                    + (mission == VM_TASK && arg != null ? " on " + Issue.valueOf(arg).label().toLowerCase() : ""), null, 0.8, "Executive"));
        }
    }

    private void vpWeek(WestWingState s, Context c, Outcome out, DeterministicRandomManager rng) {
        double k = (s.vpSkill[0] + s.vpSkill[1]) / 140;
        switch (s.vpMission) {
            case VM_TOUR -> { out.effects().add(new Effect.StateStanding(SWING[s.week % SWING.length], 1.2 * k)); out.effects().add(new Effect.Performance(0.01)); }
            case VM_TASK -> {
                if (s.vpArg != null) {
                    Issue i = Issue.valueOf(s.vpArg);
                    out.effects().add(new Effect.IssueStanding(i, 0.015 * k));
                    out.effects().add(new Effect.IssueCommand(i, 0.6));
                }
            }
            case VM_SENATE -> out.effects().add(new Effect.BlocRelations(0, 1, 0.25 * k));
            case VM_MONEY -> out.effects().add(new Effect.PartyFunds(1.2 * s.vpSkill[3] / 70));
            case VM_ABROAD -> out.effects().add(new Effect.IssueStanding(Issue.CHINA, 0.012 * k));
            default -> { }
        }
        if (s.vpMission != VM_NONE && rng.uniform("ww.vp") < (100 - s.vpSkill[2]) / 2600) {
            out.news().add(news(c, News.Kind.BAD, "The vice president's gaffe on the road becomes the story of the day", null, 1.0, null));
            out.effects().add(new Effect.Performance(-0.25));
        }
        s.vpLoyalty = clamp(s.vpLoyalty + (s.vpMission == VM_NONE ? -0.15 : 0.1) + (c.approval() - 45) * 0.01, 0, 100);
        if (s.vpUntil >= 0 && s.week >= s.vpUntil) {
            out.news().add(news(c, News.Kind.INFO, "The vice president is back in Washington after " + VM_NAME[s.vpMission].toLowerCase().replace("lead ", "leading ").replace("tour ", "touring ")
                    .replace("raise ", "raising ").replace("represent you", "representing you"), null, 0.5, "Executive"));
            s.vpMission = VM_NONE; s.vpArg = null; s.vpUntil = -1;
        }
    }

    // =========================================================================================
    //  Decisions
    // =========================================================================================

    /** A question for the president, answered by {@code due} or the staff's answer ({@code recommended}) is taken. */
    Decision ask(WestWingState s, String kind, String title, String body, String[] options, String[] advice, int[] codes, String ref, int recommended, int dueWeeks) {
        Decision d = new Decision();
        d.id = "d" + s.nextId++; d.kind = kind; d.title = title; d.body = body; d.ref = ref;
        for (int i = 0; i < options.length; i++) {
            d.options.add(options[i]);
            d.advice.add(advice != null && i < advice.length ? advice[i] : "");
            d.codes.add(codes == null ? i : codes[i]);
        }
        d.recommended = Math.max(0, Math.min(options.length - 1, recommended));
        d.due = s.week + dueWeeks;
        s.decisions.add(d);
        return d;
    }

    /** News to read, with one way to close it. */
    void info(WestWingState s, Context c, String kind, String title, String body) {
        ask(s, "info", title, body, new String[]{"Understood"}, null, null, kind, 0, 1);
    }

    public Outcome answer(WestWingState s, Context c, String id, int option, DeterministicRandomManager rng) {
        Decision d = s.decision(id);
        if (d == null) throw new IllegalArgumentException("That question has been answered");
        if (option < 0 || option >= d.options.size()) throw new IllegalArgumentException("No such answer");
        s.decisions.remove(d);
        heard(s, d, option);
        Outcome out = Outcome.empty();
        int code = d.codes.get(option);
        switch (d.kind) {
            case "info" -> { }
            case "scandal" -> trouble.respond(s, c, d.ref, code, out, rng);
            case "leak" -> trouble.leakAnswer(s, c, d.ref, code, out, rng);
            case "pardon" -> trouble.pardonAnswer(s, c, d.a, code, out);
            case "court" -> courts.answer(s, c, d.ref, code, out, rng);
            case "cra" -> actions.craAnswer(s, c, d.ref, code, out, rng);
            case "emergency" -> actions.emergencyAnswer(s, c, d.ref, code, out);
            case "qa" -> press.answer(s, c, code, out, rng);
            case "speech" -> press.speechStep(s, c, code, out, rng);
            case "story" -> stories.decide(s, c, d.ref, code, out, rng);
            default -> { }
        }
        return out;
    }

    /** Resolute Desk's {@code heard()}: the adviser whose advice was taken is pleased; the others named are not. */
    private void heard(WestWingState s, Decision d, int option) {
        for (int i = 0; i < d.advice.size(); i++) {
            String a = d.advice.get(i);
            int colon = a.indexOf(':');
            if (colon <= 0) continue;
            int who = adviser(a.substring(0, colon));
            Person p = who < 0 ? null : who >= 100 ? s.post[who - 100] : s.staff[who];
            if (p != null) p.morale = clamp(p.morale + (i == option ? 2.5 : -1.5), 0, 100);
        }
    }

    /** Questions past their week take the staff's answer; a press conference or a speech left half done is finished the same way. */
    private void overdue(WestWingState s, Context c, Outcome out, DeterministicRandomManager rng) {
        for (int guard = 0; guard < 40; guard++) {
            Decision late = null;
            for (Decision d : s.decisions) if (d.due < s.week) { late = d; break; }
            if (late == null) return;
            boolean chain = late.kind.equals("qa") || late.kind.equals("speech");
            Outcome o = answer(s, c, late.id, late.recommended, rng);
            out.effects().addAll(o.effects());
            out.news().addAll(o.news());
            // a press conference or a speech left to the staff is finished at once, not a question a week
            if (chain) for (Decision d : s.decisions) if (d.kind.equals(late.kind)) d.due = Math.min(d.due, s.week - 1);
        }
    }

    // =========================================================================================
    //  The president's orders
    // =========================================================================================

    /** Carries out an order from the West Wing or the Press Briefing Room. Throws IllegalArgumentException if it cannot be done. */
    public Outcome apply(WestWingState s, Context c, WestWingOrder o, DeterministicRandomManager rng) {
        Outcome out = Outcome.empty();
        switch (o) {
            case WestWingOrder.TransitionStep x -> throw new IllegalArgumentException("The transition office is closed");
            case WestWingOrder.Hire x -> { role(x.role()); option(x.option()); hire(s, c, x.role(), x.option(), out); }
            case WestWingOrder.Fire x -> {
                Person p = s.person(x.person());
                if (p == null || (s.staff[Math.max(0, p.role)] != p && (p.post < 0 || s.post[p.post] != p))) throw new IllegalArgumentException("Nobody by that name works for you");
                fire(s, c, p, out);
            }
            case WestWingOrder.Nominate x -> {
                post(x.post()); option(x.option());
                if (s.post[x.post()] != null) throw new IllegalArgumentException(POST_TITLE[x.post()] + " is filled");
                if (s.nominee[x.post()] != null) throw new IllegalArgumentException("A nomination is already before the Senate");
                nominate(s, c, x.post(), x.option(), out);
            }
            case WestWingOrder.Withdraw x -> { post(x.post()); withdraw(s, c, x.post(), out); }
            case WestWingOrder.Delegate x -> {
                if (x.what().equals("people")) s.delegPeople = x.on();
                else if (x.what().equals("pardons")) s.delegPardons = x.on();
                else throw new IllegalArgumentException("Nothing to delegate by that name");
                if (s.delegPeople) fill(s, c, out);
            }
            case WestWingOrder.Issue x -> actions.issue(s, c, x.action(), out, rng);
            case WestWingOrder.Custom x -> actions.custom(s, c, x.area(), x.lean(), x.scope(), x.basis(), out, rng);
            case WestWingOrder.PolicyAction x -> throw new IllegalArgumentException("A policy order goes through the game engine");
            case WestWingOrder.Revoke x -> actions.revoke(s, c, x.action(), out);
            case WestWingOrder.JudgeStrategy x -> {
                if (x.strategy() < 0 || x.strategy() > 2) throw new IllegalArgumentException("No such strategy");
                s.judgeStrategy = x.strategy();
            }
            case WestWingOrder.NominateJustice x -> courts.nominate(s, c, x.option(), out, rng);
            case WestWingOrder.ViceMission x -> {
                if (x.mission() < 0 || x.mission() > VM_ABROAD) throw new IllegalArgumentException("No such mission");
                if (x.mission() == VM_TASK && x.issue() == null) throw new IllegalArgumentException("A task force needs an issue");
                vpStart(s, c, x.mission(), x.issue(), out);
            }
            case WestWingOrder.PressConference x -> press.startQA(s, c, 0, 0, out);
            case WestWingOrder.Interview x -> press.startQA(s, c, 1, x.outlet(), out);
            case WestWingOrder.Address x -> press.address(s, c, x.issue(), out);
            case WestWingOrder.Post x -> press.post(s, c, x.issue(), x.tone(), out, rng);
            case WestWingOrder.Poll x -> press.poll(s, c, x.issue(), out);
            case WestWingOrder.Answer x -> { return answer(s, c, x.decision(), x.option(), rng); }
        }
        return out;
    }

    private static void role(int r) { if (r < 0 || r >= WestWingState.NSTAFF) throw new IllegalArgumentException("No such job"); }
    private static void post(int p) { if (p < 0 || p >= WestWingState.NPOST) throw new IllegalArgumentException("No such post"); }
    private static void option(int o) { if (o < 0 || o > 2) throw new IllegalArgumentException("No such choice"); }

    /**
     * An order or rule on one policy (Resolute Desk's {@code Policies.propose}, channels O and R), checked by the caller:
     * the policy's area as Resolute Desk's issue, its legal risk, the level's name and the party a step that way belongs to.
     */
    public Outcome policyAction(WestWingState s, Context c, String policy, int from, int to, boolean rule, int area, double legal, String name, Party lean,
                                DeterministicRandomManager rng) {
        for (WestWingState.Action a : s.actions) if (policy.equals(a.policy) && a.status == WestWingState.Action.COMMENT)
            throw new IllegalArgumentException("A rule on it is already open for comment");
        Outcome out = Outcome.empty();
        actions.policy(s, c, policy, from, to, rule, area, legal, name, lean, out, rng);
        return out;
    }

    /** Whether a rule on a policy is open for comment (something already in motion). */
    public static boolean ruleInMotion(WestWingState s, String policy) {
        for (WestWingState.Action a : s.actions) if (policy.equals(a.policy) && a.status == WestWingState.Action.COMMENT) return true;
        return false;
    }

    /** A law taking effect (Resolute Desk's {@code Implement.roll} for laws): did the department botch it? */
    public Outcome rolloutLaw(WestWingState s, Context c, String name, int area, DeterministicRandomManager rng, boolean[] failed) {
        Outcome out = Outcome.empty();
        failed[0] = actions.roll(s, c, name, area, 1.0, out, rng);
        return out;
    }

    /** What the staff would answer (and do, in watch mode): the recommended option of each question. */
    public int recommend(WestWingState s, String decisionId) {
        Decision d = s.decision(decisionId);
        return d == null ? 0 : d.recommended;
    }

    /** The Court's nominee's expected votes, their spread, and the chance of confirmation. */
    public double[] scotusCount(WestWingState s, Context c) { return courts.count(s, c); }

    /** The shortlist for an open seat on the Court. */
    public List<WestWingState.Justice> shortlist(WestWingState s, Context c) { return courts.shortlist(s, c); }

    /** Why a library action cannot be taken now, or null. */
    public String refusal(WestWingState s, Context c, ActionDef d) { return actions.refusal(s, c, d); }

    /** The pressure the executive puts toward impeachment: defying the courts and the impeachable scandals. */
    public static double impeachmentPressure(WestWingState s) {
        double pr = 3 * s.defiance;
        for (WestWingState.Scandal x : s.scandals) if (!x.over && x.impeachable) pr += x.severity;
        return pr;
    }

    /** The worst live scandal's title, for Congress's investigators, or null. */
    public static String worstScandal(WestWingState s) {
        WestWingState.Scandal w = null;
        for (WestWingState.Scandal x : s.scandals) if (!x.over && x.kind != WestWingState.Scandal.LEAK && (w == null || x.severity > w.severity)) w = x;
        return w == null ? null : w.title;
    }

    // =========================================================================================
    //  Helpers
    // =========================================================================================

    static News news(Context c, News.Kind kind, String head, String body, double weight, String section) {
        return new News(c.date(), kind, head, body, weight, section);
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    static double logistic(double x) { return 1 / (1 + Math.exp(-x)); }
    static String fmt0(double v) { return Fmt.format("%.0f", v); }
    static Issue issue(int rd) { return RdFx.issue(rd); }

    /** Odds by who does the work (Resolute Desk's {@code Diplo.p}). */
    double odds(WestWingState s, Context c, String kind, double base) {
        if (base >= 1) return 1;
        double fpc = kind.equals("m") || kind.equals("d") || kind.equals("i") ? (c.skill(FPC) - 60) / 250 : 0;
        double m = switch (kind) {
            case "m" -> (rd(s, P_DEF) - 60) / 300 + (pq(s, P_DEF) - 0.6) * 0.2;
            case "d" -> (pq(s, P_STATE) - 0.6) * 0.3 + (sq(s, S_NSA) - 0.6) * 0.15;
            case "i" -> ((pq(s, P_CIA) + pq(s, P_DNI)) / 2 - 0.6) * 0.35;
            case "h" -> (rd(s, P_DHS) - 60) / 250 + (pq(s, P_DHS) - 0.6) * 0.25;
            case "j" -> (pq(s, P_DOJ) - 0.6) * 0.3 + (rd(s, P_DOJ) - 60) / 400;
            case "x" -> (rd(s, P_HHS) - 60) / 250 + (pq(s, P_HHS) - 0.6) * 0.25;
            case "c" -> (sq(s, S_COMMS) + sq(s, S_PRESS) - 1.2) * 0.15 + (c.skill(SPE) - 60) / 250;
            default -> (sq(s, S_COS) - 0.6) * 0.15;
        };
        return clamp(base + fpc + m, 0.05, 0.97);
    }

    /** The adviser's word on the odds (Resolute Desk's {@code odds()}). */
    static String oddsText(double p) {
        if (p >= 0.99) return "This one is in our hands.";
        long n = Math.round(p * 100);
        return p >= 0.75 ? "I think it works: maybe " + n + "%." : p >= 0.5 ? "Better than even, about " + n + "%." : "A long shot, maybe " + n + "%.";
    }

    /** The adviser label with the person's name, as Resolute Desk shows it ("Chief of staff Jane Doe"). */
    public String adviserName(WestWingState s, String label) {
        int who = adviser(label);
        Person p = who < 0 ? null : who >= 100 ? s.post[who - 100] : s.staff[who];
        return p == null ? label : (label.equals("Counsel") ? "White House counsel" : label) + " " + p.name;
    }

    static long mix(long seed, long salt, long a, long b, long c) {
        long x = seed * 0x9E3779B97F4A7C15L + salt;
        x = (x ^ (a + 0x632BE59BD9B4E019L)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (b + 0x85157AF5L)) * 0x94D049BB133111EBL;
        x = (x ^ (c + 0x2545F4914F6CDD1DL)) * 0xBF58476D1CE4E5B9L;
        return x ^ (x >>> 31);
    }

    /**
     * A small generator for choices that must not touch the game's random streams (the options shown for a job stay the
     * same however often the screen asks): SplitMix64, identical in every runtime.
     */
    static final class Local {
        private long x;
        Local(long seed) { x = seed; }
        long next() {
            long z = (x += 0x9E3779B97F4A7C15L);
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }
        double u() { return (next() >>> 11) * 0x1.0p-53; }
        int n(int bound) { return (int) Math.floor(u() * bound); }
        double g() {
            double a = Math.max(1e-12, u()), b = u();
            return Math.sqrt(-2 * Math.log(a)) * Math.cos(2 * Math.PI * b);
        }
    }
}
