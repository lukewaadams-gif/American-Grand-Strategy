package game.mandate.core.foreign;

import game.mandate.core.data.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static game.mandate.core.foreign.ForeignData.NC;

/**
 * The world as Resolute Desk keeps it ({@code world/World}): relations with forty countries and the tools that move
 * them (tariffs, sanctions, aid, military aid, arms, troops, ties, peacekeepers, pacts, treaties, covert programs),
 * the hot spots (Taiwan, Ukraine, the Middle East, Korea, terror, cyber, migration, cartels), the alliances and the
 * force posture, DEFCON, crises and their cooldowns, operations and covert action, objectives, summits, wars of
 * choice, the record, and the questions the world puts on the desk.
 */
public final class ForeignState {
    // per country
    final double[] rel = new double[NC], base = new double[NC], unrest = new double[NC], troopsIn = new double[NC];
    final int[] align = new int[NC], tariff = new int[NC], sanction = new int[NC], aid = new int[NC], lastCall = new int[NC], visits = new int[NC],
            visitAt = new int[NC], milAid = new int[NC], arms = new int[NC], ties = new int[NC], pact = new int[NC], peace = new int[NC],
            cvProg = new int[NC], cvDue = new int[NC], authorized = new int[NC];
    final boolean[] toppled = new boolean[NC];
    final String[] leader = new String[NC], title = new String[NC];
    int week, callsThisWeek, callWeek = -1;
    double tariffAdd;
    /** The war budget already in the deficit, % of GDP a year. */
    double warBudget;
    // hot spots
    double twt = 55, deter = 55, ukr = 45, ruGain = 30, talks = 10, gaza = 40, iran = 45, redsea = 35, proxy = 30, nk = 55, terror = 35, cyber = 45, migr = 40, cartel = 55;
    int twStage = 1, uaStage = 2, defcon = 5;
    double nato = 50, pacific = 65, allySpend = 2.6, refugees = 20;
    final int[] posture = {1, 1, 1};
    final int[] cool = new int[20], opCool = new int[10], cvCool = new int[7];
    int kia, ops, covert, exposed, crises, deals, okCount, failCount;
    // a bilateral summit in progress
    int summitCountry = -1, summitWeek = -1, summitRound, summitAsk = -1;
    double summitMood, summitLeverage;
    int art5Week = -99, pressWeek = -99, drillWeek = -99;
    /** The Taiwan war ran on the operational model: the world waits for its outcome. */
    boolean taiwanWar;
    final List<Objective> objectives = new ArrayList<>();
    int objectivesYear = -1, done;
    final List<War> wars = new ArrayList<>();
    final List<Question> questions = new ArrayList<>();
    final List<String> record = new ArrayList<>();
    /** The President's Daily Brief: "priority|text" items, rebuilt every week. */
    final List<String> brief = new ArrayList<>();
    int nextId = 1;
    /** What the last thing done from the Situation Room came to. */
    String message;

    public double relations(int c) { return rel[c]; }
    public double baseRelations(int c) { return base[c]; }
    public int alignment(int c) { return align[c]; }
    public int tariff(int c) { return tariff[c]; }
    public int sanction(int c) { return sanction[c]; }
    public int aid(int c) { return aid[c]; }
    public int visits(int c) { return visits[c]; }
    public int visitAt(int c) { return visitAt[c]; }
    public int lastCall(int c) { return lastCall[c]; }
    public int milAid(int c) { return milAid[c]; }
    public int arms(int c) { return arms[c]; }
    public int ties(int c) { return ties[c]; }
    public double troopsIn(int c) { return troopsIn[c]; }
    public boolean peacekeepers(int c) { return peace[c] > 0; }
    public boolean pact(int c) { return pact[c] > 0; }
    public int covertProgram(int c) { return cvProg[c] - 1; }
    public int covertDue(int c) { return cvDue[c]; }
    public double unrest(int c) { return unrest[c]; }
    public boolean toppled(int c) { return toppled[c]; }
    public int authorizedWeek(int c) { return authorized[c]; }
    public String leader(int c) { return leader[c]; }
    public String leaderTitle(int c) { return title[c]; }
    public int week() { return week; }
    public int callsLeft() { return callWeek == week ? Math.max(0, 3 - callsThisWeek) : 3; }
    public double twt() { return twt; }
    public double deter() { return deter; }
    public double ukr() { return ukr; }
    public double ruGain() { return ruGain; }
    public double talks() { return talks; }
    public double gaza() { return gaza; }
    public double iran() { return iran; }
    public double redsea() { return redsea; }
    public double proxy() { return proxy; }
    public double nk() { return nk; }
    public double terror() { return terror; }
    public double cyber() { return cyber; }
    public double migr() { return migr; }
    public double cartel() { return cartel; }
    public int twStage() { return twStage; }
    public int uaStage() { return uaStage; }
    public int defcon() { return defcon; }
    public double nato() { return nato; }
    public double pacific() { return pacific; }
    public double allySpend() { return allySpend; }
    public double refugees() { return refugees; }
    public int posture(int area) { return posture[area]; }
    public int crisisCooldown(int i) { return cool[i]; }
    public int operationCooldown(int i) { return opCool[i]; }
    public int covertCooldown(int i) { return cvCool[i]; }
    public int kia() { return kia; }
    public int operations() { return ops; }
    public int covertActions() { return covert; }
    public int exposed() { return exposed; }
    public int crisesFaced() { return crises; }
    public int deals() { return deals; }
    public int okCount() { return okCount; }
    public int failCount() { return failCount; }
    public int summitCountry() { return summitCountry; }
    public int summitWeek() { return summitWeek; }
    public int summitRound() { return summitRound; }
    public int art5Week() { return art5Week; }
    public int pressWeek() { return pressWeek; }
    public int drillWeek() { return drillWeek; }
    public boolean taiwanWar() { return taiwanWar; }
    public List<Objective> objectives() { return Collections.unmodifiableList(objectives); }
    public int objectivesDone() { return done; }
    public List<War> wars() { return Collections.unmodifiableList(wars); }
    public List<Question> questions() { return Collections.unmodifiableList(questions); }
    public List<String> record() { return Collections.unmodifiableList(record); }
    public List<String> brief() { return Collections.unmodifiableList(brief); }
    public String message() { return message; }
    public double warBudget() { return warBudget; }
    public int totalVisits() { int v = 0; for (int x : visits) v += x; return v; }

    public Question question(String id) {
        for (Question q : questions) if (q.id.equals(id)) return q;
        return null;
    }

    public War war(String id) {
        for (War x : wars) if (x.id.equals(id)) return x;
        return null;
    }

    /** An objective with one country (Resolute Desk's {@code Objective}): its steps, tried one by one. */
    public static final class Objective {
        String id, def;
        int country, step, status, fails, tryAfter;

        public String id() { return id; }
        /** The row in {@code objectives.tsv} it was made from (a template's id for a generic one). */
        public String definition() { return def; }
        public int country() { return country; }
        public int step() { return step; }
        /** 0 open, 1 achieved, 2 stalled. */
        public int status() { return status; }
        public int fails() { return fails; }
        public int tryAfter() { return tryAfter; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("def", def); m.put("i", List.of(country, step, status, fails, tryAfter));
            return m;
        }

        static Objective fromJson(Map<String, Object> m) {
            Objective o = new Objective();
            o.id = (String) m.get("id"); o.def = (String) m.get("def");
            List<Object> i = Json.array(m.get("i"));
            o.country = Json.integer(i.get(0)); o.step = Json.integer(i.get(1)); o.status = Json.integer(i.get(2)); o.fails = Json.integer(i.get(3));
            o.tryAfter = Json.integer(i.get(4));
            return o;
        }
    }

    /** A war of choice (Resolute Desk's {@code War}). */
    public static final class War {
        String id, name;
        int enemy, kind, stage, strategy = 1, weeks, started, ended = -1, timetable = -1, timetableStart, handoff = -1;
        double front = 50, troops, kia, support = 60, talks, need, drift, lethal, cost, stakes, troops0, legit;
        boolean authorized, wpVoted;

        public String id() { return id; }
        public String name() { return name; }
        public int enemy() { return enemy; }
        public int kind() { return kind; }
        public int stage() { return stage; }
        public boolean fighting() { return stage == ForeignData.WS_FIGHTING; }
        public int strategy() { return strategy; }
        public int weeks() { return weeks; }
        public int started() { return started; }
        public int ended() { return ended; }
        public int timetable() { return timetable; }
        public int handoff() { return handoff; }
        public double front() { return front; }
        public double troops() { return troops; }
        public double kia() { return kia; }
        public double support() { return support; }
        public double talks() { return talks; }
        public double need() { return need; }
        public double cost() { return cost; }
        public double stakes() { return stakes; }
        public boolean authorized() { return authorized; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("name", name);
            m.put("i", List.of(enemy, kind, stage, strategy, weeks, started, ended, timetable, timetableStart, handoff));
            m.put("d", List.of(front, troops, kia, support, talks, need, drift, lethal, cost, stakes, troops0, legit));
            m.put("b", List.of(authorized, wpVoted));
            return m;
        }

        static War fromJson(Map<String, Object> m) {
            War x = new War();
            x.id = (String) m.get("id"); x.name = (String) m.get("name");
            List<Object> i = Json.array(m.get("i")), d = Json.array(m.get("d")), b = Json.array(m.get("b"));
            x.enemy = Json.integer(i.get(0)); x.kind = Json.integer(i.get(1)); x.stage = Json.integer(i.get(2)); x.strategy = Json.integer(i.get(3));
            x.weeks = Json.integer(i.get(4)); x.started = Json.integer(i.get(5)); x.ended = Json.integer(i.get(6)); x.timetable = Json.integer(i.get(7));
            x.timetableStart = Json.integer(i.get(8)); x.handoff = Json.integer(i.get(9));
            x.front = Json.num(d.get(0)); x.troops = Json.num(d.get(1)); x.kia = Json.num(d.get(2)); x.support = Json.num(d.get(3)); x.talks = Json.num(d.get(4));
            x.need = Json.num(d.get(5)); x.drift = Json.num(d.get(6)); x.lethal = Json.num(d.get(7)); x.cost = Json.num(d.get(8)); x.stakes = Json.num(d.get(9));
            x.troops0 = Json.num(d.get(10)); x.legit = Json.num(d.get(11));
            x.authorized = (Boolean) b.get(0); x.wpVoted = (Boolean) b.get(1);
            return x;
        }
    }

    /**
     * A question from the world on the desk: a crisis ("crisis"), a summit's rounds ("summit"), a multilateral summit
     * ("gsummit"), a War Powers vote ("warpowers"), or news to read ("info").
     */
    public static final class Question {
        String id, kind, title, body, ref;
        final List<String> options = new ArrayList<>(), advice = new ArrayList<>();
        final List<Integer> codes = new ArrayList<>();
        int a, due, recommended;

        public String id() { return id; }
        public String kind() { return kind; }
        public String title() { return title; }
        public String body() { return body; }
        public String ref() { return ref; }
        public List<String> options() { return Collections.unmodifiableList(options); }
        public List<String> advice() { return Collections.unmodifiableList(advice); }
        public int recommended() { return recommended; }
        public int due() { return due; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("kind", kind); m.put("title", title); m.put("body", body); m.put("ref", ref);
            m.put("options", new ArrayList<Object>(options)); m.put("advice", new ArrayList<Object>(advice)); m.put("codes", new ArrayList<Object>(codes));
            m.put("i", List.of(a, due, recommended));
            return m;
        }

        static Question fromJson(Map<String, Object> m) {
            Question q = new Question();
            q.id = (String) m.get("id"); q.kind = (String) m.get("kind"); q.title = (String) m.get("title"); q.body = (String) m.get("body"); q.ref = (String) m.get("ref");
            for (Object o : Json.array(m.get("options"))) q.options.add((String) o);
            for (Object o : Json.array(m.get("advice"))) q.advice.add((String) o);
            for (Object o : Json.array(m.get("codes"))) q.codes.add(Json.integer(o));
            List<Object> i = Json.array(m.get("i"));
            q.a = Json.integer(i.get(0)); q.due = Json.integer(i.get(1)); q.recommended = Json.integer(i.get(2));
            return q;
        }
    }

    static List<Object> nums(double[] a) { List<Object> l = new ArrayList<>(); for (double v : a) l.add(v); return l; }
    static List<Object> ints(int[] a) { List<Object> l = new ArrayList<>(); for (int v : a) l.add(v); return l; }
    static void read(List<Object> l, double[] a) { for (int i = 0; i < a.length && i < l.size(); i++) a[i] = Json.num(l.get(i)); }
    static void read(List<Object> l, int[] a) { for (int i = 0; i < a.length && i < l.size(); i++) a[i] = Json.integer(l.get(i)); }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rel", nums(rel)); m.put("base", nums(base)); m.put("unrest", nums(unrest)); m.put("troopsIn", nums(troopsIn));
        m.put("align", ints(align)); m.put("tariff", ints(tariff)); m.put("sanction", ints(sanction)); m.put("aid", ints(aid)); m.put("lastCall", ints(lastCall));
        m.put("visits", ints(visits)); m.put("visitAt", ints(visitAt)); m.put("milAid", ints(milAid)); m.put("arms", ints(arms)); m.put("ties", ints(ties));
        m.put("pact", ints(pact)); m.put("peace", ints(peace)); m.put("cvProg", ints(cvProg)); m.put("cvDue", ints(cvDue)); m.put("authorized", ints(authorized));
        List<Object> tp = new ArrayList<>();
        for (boolean b : toppled) tp.add(b);
        m.put("toppled", tp);
        List<Object> ld = new ArrayList<>();
        for (int c = 0; c < NC; c++) ld.add(leader[c] + "|" + title[c]);
        m.put("leaders", ld);
        m.put("w", List.of(week, callsThisWeek, callWeek, twStage, uaStage, defcon, kia, ops, covert, exposed, crises, deals, okCount, failCount,
                summitCountry, summitWeek, summitRound, summitAsk, art5Week, pressWeek, drillWeek, objectivesYear, done, nextId));
        m.put("x", List.of(tariffAdd, twt, deter, ukr, ruGain, talks, gaza, iran, redsea, proxy, nk, terror, cyber, migr, cartel, nato, pacific, allySpend,
                refugees, summitMood, summitLeverage, warBudget));
        m.put("posture", ints(posture)); m.put("cool", ints(cool)); m.put("opCool", ints(opCool)); m.put("cvCool", ints(cvCool));
        m.put("taiwanWar", taiwanWar);
        List<Object> os = new ArrayList<>();
        for (Objective o : objectives) os.add(o.toJson());
        m.put("objectives", os);
        List<Object> ws = new ArrayList<>();
        for (War x : wars) ws.add(x.toJson());
        m.put("wars", ws);
        List<Object> qs = new ArrayList<>();
        for (Question q : questions) qs.add(q.toJson());
        m.put("questions", qs);
        m.put("record", new ArrayList<Object>(record)); m.put("brief", new ArrayList<Object>(brief)); m.put("message", message);
        return m;
    }

    public static ForeignState fromJson(Map<String, Object> m) {
        ForeignState s = new ForeignState();
        read(Json.array(m.get("rel")), s.rel); read(Json.array(m.get("base")), s.base); read(Json.array(m.get("unrest")), s.unrest); read(Json.array(m.get("troopsIn")), s.troopsIn);
        read(Json.array(m.get("align")), s.align); read(Json.array(m.get("tariff")), s.tariff); read(Json.array(m.get("sanction")), s.sanction);
        read(Json.array(m.get("aid")), s.aid); read(Json.array(m.get("lastCall")), s.lastCall); read(Json.array(m.get("visits")), s.visits);
        read(Json.array(m.get("visitAt")), s.visitAt); read(Json.array(m.get("milAid")), s.milAid); read(Json.array(m.get("arms")), s.arms);
        read(Json.array(m.get("ties")), s.ties); read(Json.array(m.get("pact")), s.pact); read(Json.array(m.get("peace")), s.peace);
        read(Json.array(m.get("cvProg")), s.cvProg); read(Json.array(m.get("cvDue")), s.cvDue); read(Json.array(m.get("authorized")), s.authorized);
        List<Object> tp = Json.array(m.get("toppled"));
        for (int c = 0; c < NC && c < tp.size(); c++) s.toppled[c] = (Boolean) tp.get(c);
        List<Object> ld = Json.array(m.get("leaders"));
        for (int c = 0; c < NC && c < ld.size(); c++) { String[] t = ((String) ld.get(c)).split("\\|", 2); s.leader[c] = t[0]; s.title[c] = t.length > 1 ? t[1] : ""; }
        List<Object> w = Json.array(m.get("w"));
        int k = 0;
        s.week = Json.integer(w.get(k++)); s.callsThisWeek = Json.integer(w.get(k++)); s.callWeek = Json.integer(w.get(k++)); s.twStage = Json.integer(w.get(k++));
        s.uaStage = Json.integer(w.get(k++)); s.defcon = Json.integer(w.get(k++)); s.kia = Json.integer(w.get(k++)); s.ops = Json.integer(w.get(k++));
        s.covert = Json.integer(w.get(k++)); s.exposed = Json.integer(w.get(k++)); s.crises = Json.integer(w.get(k++)); s.deals = Json.integer(w.get(k++));
        s.okCount = Json.integer(w.get(k++)); s.failCount = Json.integer(w.get(k++)); s.summitCountry = Json.integer(w.get(k++)); s.summitWeek = Json.integer(w.get(k++));
        s.summitRound = Json.integer(w.get(k++)); s.summitAsk = Json.integer(w.get(k++)); s.art5Week = Json.integer(w.get(k++)); s.pressWeek = Json.integer(w.get(k++));
        s.drillWeek = Json.integer(w.get(k++)); s.objectivesYear = Json.integer(w.get(k++)); s.done = Json.integer(w.get(k++)); s.nextId = Json.integer(w.get(k));
        List<Object> x = Json.array(m.get("x"));
        k = 0;
        s.tariffAdd = Json.num(x.get(k++)); s.twt = Json.num(x.get(k++)); s.deter = Json.num(x.get(k++)); s.ukr = Json.num(x.get(k++)); s.ruGain = Json.num(x.get(k++));
        s.talks = Json.num(x.get(k++)); s.gaza = Json.num(x.get(k++)); s.iran = Json.num(x.get(k++)); s.redsea = Json.num(x.get(k++)); s.proxy = Json.num(x.get(k++));
        s.nk = Json.num(x.get(k++)); s.terror = Json.num(x.get(k++)); s.cyber = Json.num(x.get(k++)); s.migr = Json.num(x.get(k++)); s.cartel = Json.num(x.get(k++));
        s.nato = Json.num(x.get(k++)); s.pacific = Json.num(x.get(k++)); s.allySpend = Json.num(x.get(k++)); s.refugees = Json.num(x.get(k++));
        s.summitMood = Json.num(x.get(k++)); s.summitLeverage = Json.num(x.get(k++)); s.warBudget = Json.num(x.get(k));
        read(Json.array(m.get("posture")), s.posture); read(Json.array(m.get("cool")), s.cool); read(Json.array(m.get("opCool")), s.opCool); read(Json.array(m.get("cvCool")), s.cvCool);
        s.taiwanWar = (Boolean) m.get("taiwanWar");
        for (Object o : Json.array(m.get("objectives"))) s.objectives.add(Objective.fromJson(Json.object(o)));
        for (Object o : Json.array(m.get("wars"))) s.wars.add(War.fromJson(Json.object(o)));
        for (Object o : Json.array(m.get("questions"))) s.questions.add(Question.fromJson(Json.object(o)));
        for (Object o : Json.array(m.get("record"))) s.record.add((String) o);
        for (Object o : Json.array(m.get("brief"))) s.brief.add((String) o);
        s.message = (String) m.get("message");
        return s;
    }
}
