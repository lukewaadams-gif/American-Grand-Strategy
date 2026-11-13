package game.mandate.core.westwing;

import game.mandate.core.data.Json;
import game.mandate.core.state.Party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The executive branch as Resolute Desk keeps it ({@code Exec}, {@code Home} and the storylines): the White House staff,
 * the cabinet and the departments, executive orders, rules and emergencies, the courts, the vice president, scandals
 * and pardons, the press and the president's speeches, and the developing stories. Owned by {@link WestWingEngine}.
 */
public final class WestWingState {
    public static final int NSTAFF = 9, NPOST = 21, NOUTLET = 18;

    /** Someone who works for the president: staff, a cabinet officer or a nominee (Resolute Desk's {@code Person}). */
    public static final class Person {
        String id, name, background;
        Party party;
        int role = -1, post = -1, trait, since, nominated = -1;
        double skill = 60, loyalty = 60, morale = 70, ideo;
        boolean confirmed;
        int voteYes, voteNo;

        public String id() { return id; }
        public String name() { return name; }
        public String background() { return background; }
        public Party party() { return party; }
        public int role() { return role; }
        public int post() { return post; }
        public int trait() { return trait; }
        public int since() { return since; }
        public double skill() { return skill; }
        public double loyalty() { return loyalty; }
        public double morale() { return morale; }
        public double ideology() { return ideo; }
        public boolean confirmed() { return confirmed; }
        public int voteYes() { return voteYes; }
        public int voteNo() { return voteNo; }
        /** Resolute Desk's {@code q()}: skill, tempered by morale. */
        public double quality() { return skill / 100 * (0.7 + 0.3 * morale / 100); }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("name", name); m.put("bg", background); m.put("party", party.name());
            m.put("i", List.of(role, post, trait, since, nominated, voteYes, voteNo));
            m.put("v", nums(skill, loyalty, morale, ideo)); m.put("confirmed", confirmed);
            return m;
        }

        static Person fromJson(Map<String, Object> m) {
            if (m == null) return null;
            Person p = new Person();
            p.id = (String) m.get("id"); p.name = (String) m.get("name"); p.background = (String) m.get("bg"); p.party = Party.valueOf((String) m.get("party"));
            List<Object> i = Json.array(m.get("i"));
            p.role = Json.integer(i.get(0)); p.post = Json.integer(i.get(1)); p.trait = Json.integer(i.get(2)); p.since = Json.integer(i.get(3));
            p.nominated = Json.integer(i.get(4)); p.voteYes = Json.integer(i.get(5)); p.voteNo = Json.integer(i.get(6));
            double[] v = read(m.get("v"));
            p.skill = v[0]; p.loyalty = v[1]; p.morale = v[2]; p.ideo = v[3];
            p.confirmed = (Boolean) m.get("confirmed");
            return p;
        }
    }

    /** An executive order, an agency rule or a national emergency (Resolute Desk's {@code Action}). */
    public static final class Action {
        public static final int ORDER = 0, RULE = 1, EMERGENCY = 2;
        public static final int LIVE = 0, PENDING = 1, BLOCKED = 2, STRUCK = 3, REVOKED = 4, COMMENT = 5, UNDONE = 6;
        String id, def, name, fx, policy;
        int kind, area, status, issued, finalWeek, statusWeek, needs = -1, polFrom, polTo;
        /** The party whose agenda it is, or null for either. */
        Party lean;
        double support, legal, scope = 1;
        boolean custom, sued, applied, failed;

        public String id() { return id; }
        public String def() { return def; }
        public String name() { return name; }
        public int kind() { return kind; }
        public int area() { return area; }
        public int status() { return status; }
        public int issued() { return issued; }
        public int finalWeek() { return finalWeek; }
        public Party lean() { return lean; }
        public double support() { return support; }
        public double legal() { return legal; }
        public double scope() { return scope; }
        public boolean custom() { return custom; }
        public boolean sued() { return sued; }
        public boolean failed() { return failed; }
        public String policy() { return policy; }
        public int policyFrom() { return polFrom; }
        public int policyTo() { return polTo; }
        public boolean standing() { return status == LIVE || status == COMMENT || status == BLOCKED; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("def", def); m.put("name", name); m.put("fx", fx); m.put("policy", policy);
            m.put("i", List.of(kind, area, status, issued, finalWeek, statusWeek, needs, polFrom, polTo));
            m.put("lean", lean == null ? null : lean.name());
            m.put("v", nums(support, legal, scope));
            m.put("f", List.of(custom, sued, applied, failed));
            return m;
        }

        static Action fromJson(Map<String, Object> m) {
            Action a = new Action();
            a.id = (String) m.get("id"); a.def = (String) m.get("def"); a.name = (String) m.get("name"); a.fx = (String) m.get("fx"); a.policy = (String) m.get("policy");
            List<Object> i = Json.array(m.get("i"));
            a.kind = Json.integer(i.get(0)); a.area = Json.integer(i.get(1)); a.status = Json.integer(i.get(2)); a.issued = Json.integer(i.get(3));
            a.finalWeek = Json.integer(i.get(4)); a.statusWeek = Json.integer(i.get(5)); a.needs = Json.integer(i.get(6)); a.polFrom = Json.integer(i.get(7)); a.polTo = Json.integer(i.get(8));
            a.lean = m.get("lean") == null ? null : Party.valueOf((String) m.get("lean"));
            double[] v = read(m.get("v"));
            a.support = v[0]; a.legal = v[1]; a.scope = v[2];
            List<Object> f = Json.array(m.get("f"));
            a.custom = (Boolean) f.get(0); a.sued = (Boolean) f.get(1); a.applied = (Boolean) f.get(2); a.failed = (Boolean) f.get(3);
            return a;
        }
    }

    /** A lawsuit against an order or rule (Resolute Desk's {@code Case}). */
    public static final class Case {
        public static final int DISTRICT = 0, CIRCUIT = 1, SCOTUS = 2, DONE = 3;
        String action, plaintiff, note = "";
        int stage, decideWeek, votesFor, votesAgainst;
        boolean blocked, done, won;

        public String action() { return action; }
        public String plaintiff() { return plaintiff; }
        public int stage() { return stage; }
        public int decideWeek() { return decideWeek; }
        public int votesFor() { return votesFor; }
        public int votesAgainst() { return votesAgainst; }
        public boolean blocked() { return blocked; }
        public boolean done() { return done; }
        public boolean won() { return won; }
        public String note() { return note; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("action", action); m.put("plaintiff", plaintiff); m.put("note", note);
            m.put("i", List.of(stage, decideWeek, votesFor, votesAgainst)); m.put("f", List.of(blocked, done, won));
            return m;
        }

        static Case fromJson(Map<String, Object> m) {
            Case c = new Case();
            c.action = (String) m.get("action"); c.plaintiff = (String) m.get("plaintiff"); c.note = (String) m.get("note");
            List<Object> i = Json.array(m.get("i"));
            c.stage = Json.integer(i.get(0)); c.decideWeek = Json.integer(i.get(1)); c.votesFor = Json.integer(i.get(2)); c.votesAgainst = Json.integer(i.get(3));
            List<Object> f = Json.array(m.get("f"));
            c.blocked = (Boolean) f.get(0); c.done = (Boolean) f.get(1); c.won = (Boolean) f.get(2);
            return c;
        }
    }

    /** A Supreme Court justice, or a nominee for the Court. */
    public static final class Justice {
        String name;
        int born;
        Party party;
        double ideo;

        public String name() { return name; }
        public int born() { return born; }
        public Party party() { return party; }
        public double ideology() { return ideo; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name); m.put("born", born); m.put("party", party.name()); m.put("ideo", ideo);
            return m;
        }

        static Justice fromJson(Map<String, Object> m) {
            if (m == null) return null;
            Justice j = new Justice();
            j.name = (String) m.get("name"); j.born = Json.integer(m.get("born")); j.party = Party.valueOf((String) m.get("party")); j.ideo = Json.num(m.get("ideo"));
            return j;
        }
    }

    /** A scandal (Resolute Desk's {@code Scandal}): a story, then under investigation, then a special counsel. */
    public static final class Scandal {
        public static final int LEAK = 0, ETHICS = 1, PARDON = 2, DEFY = 3, POWER = 4, PAST = 5, FAILURE = 6;
        String id, title, body, person;
        double severity;
        int started, stage, kind;
        boolean impeachable, over;

        public String id() { return id; }
        public String title() { return title; }
        public String body() { return body; }
        public String person() { return person; }
        public double severity() { return severity; }
        public int started() { return started; }
        public int stage() { return stage; }
        public int kind() { return kind; }
        public boolean impeachable() { return impeachable; }
        public boolean over() { return over; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("title", title); m.put("body", body); m.put("person", person); m.put("sev", severity);
            m.put("i", List.of(started, stage, kind)); m.put("f", List.of(impeachable, over));
            return m;
        }

        static Scandal fromJson(Map<String, Object> m) {
            Scandal s = new Scandal();
            s.id = (String) m.get("id"); s.title = (String) m.get("title"); s.body = (String) m.get("body"); s.person = (String) m.get("person");
            s.severity = Json.num(m.get("sev"));
            List<Object> i = Json.array(m.get("i"));
            s.started = Json.integer(i.get(0)); s.stage = Json.integer(i.get(1)); s.kind = Json.integer(i.get(2));
            List<Object> f = Json.array(m.get("f"));
            s.impeachable = (Boolean) f.get(0); s.over = (Boolean) f.get(1);
            return s;
        }
    }

    /** A developing story (Resolute Desk's {@code Storyline}). */
    public static final class Storyline {
        public static final int ACTIVE = 0, FADED = 1, RUNAWAY = 2, ENDED = 3;
        String def, who, state;
        int stage, status, stageWeek, started, ended = -1, lingers, choices;
        double heat;

        public String def() { return def; }
        public String who() { return who; }
        public String state() { return state; }
        public int stage() { return stage; }
        public int status() { return status; }
        public int started() { return started; }
        public int ended() { return ended; }
        public double heat() { return heat; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("def", def); m.put("who", who); m.put("state", state);
            m.put("i", List.of(stage, status, stageWeek, started, ended, lingers, choices)); m.put("heat", heat);
            return m;
        }

        static Storyline fromJson(Map<String, Object> m) {
            Storyline s = new Storyline();
            s.def = (String) m.get("def"); s.who = (String) m.get("who"); s.state = (String) m.get("state");
            List<Object> i = Json.array(m.get("i"));
            s.stage = Json.integer(i.get(0)); s.status = Json.integer(i.get(1)); s.stageWeek = Json.integer(i.get(2)); s.started = Json.integer(i.get(3));
            s.ended = Json.integer(i.get(4)); s.lingers = Json.integer(i.get(5)); s.choices = Json.integer(i.get(6));
            s.heat = Json.num(m.get("heat"));
            return s;
        }
    }

    /**
     * A question for the president (an entry in Resolute Desk's inbox): its options, each adviser's word, and the
     * week it must be answered by, after which the staff's answer is taken.
     */
    public static final class Decision {
        String id, kind, title, body, ref;
        final List<String> options = new ArrayList<>(), advice = new ArrayList<>();
        final List<Integer> codes = new ArrayList<>();
        int due, recommended, a;

        public String id() { return id; }
        /** "scandal", "leak", "resign", "court", "cra", "emergency", "pardon", "qa", "speech", "story", "scotus", "rejected", "info". */
        public String kind() { return kind; }
        public String title() { return title; }
        public String body() { return body; }
        public List<String> options() { return Collections.unmodifiableList(options); }
        public List<String> advice() { return Collections.unmodifiableList(advice); }
        public int due() { return due; }
        public int recommended() { return recommended; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("kind", kind); m.put("title", title); m.put("body", body); m.put("ref", ref);
            m.put("options", new ArrayList<Object>(options)); m.put("advice", new ArrayList<Object>(advice)); m.put("codes", new ArrayList<Object>(codes));
            m.put("i", List.of(due, recommended, a));
            return m;
        }

        static Decision fromJson(Map<String, Object> m) {
            Decision d = new Decision();
            d.id = (String) m.get("id"); d.kind = (String) m.get("kind"); d.title = (String) m.get("title"); d.body = (String) m.get("body"); d.ref = (String) m.get("ref");
            for (Object o : Json.array(m.get("options"))) d.options.add((String) o);
            for (Object o : Json.array(m.get("advice"))) d.advice.add((String) o);
            for (Object o : Json.array(m.get("codes"))) d.codes.add(Json.integer(o));
            List<Object> i = Json.array(m.get("i"));
            d.due = Json.integer(i.get(0)); d.recommended = Json.integer(i.get(1)); d.a = Json.integer(i.get(2));
            return d;
        }
    }

    int week, nextId;
    final Person[] staff = new Person[NSTAFF], post = new Person[NPOST], nominee = new Person[NPOST];
    final int[] voteAt = new int[NPOST];
    final double[] ready = new double[NPOST], wf = new double[NPOST];
    final List<Action> actions = new ArrayList<>();
    final boolean[] emergency = new boolean[5];
    // the courts
    final List<Case> cases = new ArrayList<>();
    double dDistrict = 0.46, dCircuit = 0.42, vacDistrict = 40, vacCircuit = 6, judgeFrac;
    int judges, judgeStrategy = 1, scotusVoteAt = -1, scotusVacancies, justicesAppointed, scotusRejected;
    final List<Justice> justices = new ArrayList<>();
    Justice nomineeJ;
    // trouble
    final List<Scandal> scandals = new ArrayList<>();
    int defiance, pardons, nextPardon = 8, departures, failures, leaks, lastCabinet = -10;
    /** The term being served: in a second term the people on offer are less seasoned and less loyal. */
    int term = 1;
    // the vice president
    String vpName;
    final double[] vpSkill = new double[4];   // charisma, establishment ties, discipline, fundraising
    int vpMission, vpUntil = -1, vpMissions;
    String vpArg;
    double vpLoyalty = 75;
    boolean delegPeople, delegPardons;
    final List<Decision> decisions = new ArrayList<>();
    // the press
    final double[] tone = new double[NOUTLET];
    double press = 50, followers, qaScore, spScore;
    int lastPresser = -9, lastInterview = -9, lastAddress = -9, pressers, interviews, speeches, postsThisWeek, postWeek = -1;
    int qaType = -1, qaStep, qaOutlet, spStep = -1, spOpening, spAttacks, sotuYear;
    boolean spSotu;
    final String[] qaTopic = new String[3];
    String lastPoll;
    // storylines
    final List<Storyline> storylines = new ArrayList<>();
    final Map<String, Integer> storyCool = new TreeMap<>();
    final List<String> record = new ArrayList<>();
    /** Everyone who has left the administration (resigned, fired, or gone at a term's end): never a prospect again (B-09). */
    final List<String> departed = new ArrayList<>();

    WestWingState() {}

    public int week() { return week; }
    public Person staff(int role) { return staff[role]; }
    public Person post(int p) { return post[p]; }
    public Person nominee(int p) { return nominee[p]; }
    public int voteAt(int p) { return voteAt[p]; }
    public double readiness(int p) { return ready[p]; }
    public double workforce(int p) { return wf[p]; }
    public List<Action> actions() { return Collections.unmodifiableList(actions); }
    public boolean emergency(int e) { return emergency[e]; }
    public List<Case> cases() { return Collections.unmodifiableList(cases); }
    public double districtDemShare() { return dDistrict; }
    public double circuitDemShare() { return dCircuit; }
    public double districtVacancies() { return vacDistrict; }
    public double circuitVacancies() { return vacCircuit; }
    public int judgesAppointed() { return judges; }
    public int judgeStrategy() { return judgeStrategy; }
    public List<Justice> justices() { return Collections.unmodifiableList(justices); }
    public Justice justiceNominee() { return nomineeJ; }
    public int scotusVoteAt() { return scotusVoteAt; }
    public int scotusVacancies() { return scotusVacancies; }
    public int justicesAppointed() { return justicesAppointed; }
    public List<Scandal> scandals() { return Collections.unmodifiableList(scandals); }
    public int defiance() { return defiance; }
    public int pardons() { return pardons; }
    public int departures() { return departures; }
    public int term() { return term; }
    public int failures() { return failures; }
    public int leaks() { return leaks; }
    public int lastCabinet() { return lastCabinet; }
    public String vicePresident() { return vpName; }
    public double vpSkill(int k) { return vpSkill[k]; }
    public int vpMission() { return vpMission; }
    public String vpArgument() { return vpArg; }
    public int vpUntil() { return vpUntil; }
    public double vpLoyalty() { return vpLoyalty; }
    public boolean delegatedPeople() { return delegPeople; }
    public boolean delegatedPardons() { return delegPardons; }
    public List<Decision> decisions() { return Collections.unmodifiableList(decisions); }
    public double tone(int outlet) { return tone[outlet]; }
    public double pressRelations() { return press; }
    public double followers() { return followers; }
    public int lastPresser() { return lastPresser; }
    public int lastInterview() { return lastInterview; }
    public int lastAddress() { return lastAddress; }
    public int pressConferences() { return pressers; }
    public int interviews() { return interviews; }
    public int speeches() { return speeches; }
    public int postsThisWeek() { return postWeek == week ? postsThisWeek : 0; }
    public boolean inQuestions() { return qaType >= 0; }
    public boolean inSpeech() { return spStep >= 0; }
    public String lastPoll() { return lastPoll; }
    public List<Storyline> storylines() { return Collections.unmodifiableList(storylines); }
    public List<String> record() { return Collections.unmodifiableList(record); }

    public Person person(String id) {
        if (id == null) return null;
        for (Person p : staff) if (p != null && p.id.equals(id)) return p;
        for (Person p : post) if (p != null && p.id.equals(id)) return p;
        for (Person p : nominee) if (p != null && p.id.equals(id)) return p;
        return null;
    }

    public Action action(String id) {
        for (Action a : actions) if (a.id.equals(id)) return a;
        return null;
    }

    public Decision decision(String id) {
        for (Decision d : decisions) if (d.id.equals(id)) return d;
        return null;
    }

    /** Every person serving, staff first then the cabinet (Resolute Desk's {@code everyone()}, whose order sets the draws). */
    /** Everyone who has left the administration: never a prospect again. */
    public List<String> departed() { return java.util.Collections.unmodifiableList(departed); }

    public List<Person> everyone() {
        List<Person> out = new ArrayList<>();
        for (Person p : staff) if (p != null) out.add(p);
        for (Person p : post) if (p != null) out.add(p);
        return out;
    }

    // ---- saves --------------------------------------------------------------------------------------------------------

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("week", week); m.put("nextId", nextId);
        m.put("staff", people(staff)); m.put("post", people(post)); m.put("nominee", people(nominee));
        List<Object> va = new ArrayList<>();
        for (int v : voteAt) va.add(v);
        m.put("voteAt", va); m.put("ready", nums(ready)); m.put("wf", nums(wf));
        List<Object> as = new ArrayList<>();
        for (Action a : actions) as.add(a.toJson());
        m.put("actions", as);
        List<Object> em = new ArrayList<>();
        for (boolean e : emergency) em.add(e);
        m.put("emergency", em);
        List<Object> cs = new ArrayList<>();
        for (Case c : cases) cs.add(c.toJson());
        m.put("cases", cs);
        m.put("courts", nums(dDistrict, dCircuit, vacDistrict, vacCircuit, judgeFrac));
        m.put("courtCounts", List.of(judges, judgeStrategy, scotusVoteAt, scotusVacancies, justicesAppointed, scotusRejected));
        List<Object> js = new ArrayList<>();
        for (Justice j : justices) js.add(j.toJson());
        m.put("justices", js); m.put("nomineeJ", nomineeJ == null ? null : nomineeJ.toJson());
        List<Object> sc = new ArrayList<>();
        for (Scandal s : scandals) sc.add(s.toJson());
        m.put("scandals", sc);
        m.put("trouble", List.of(defiance, pardons, nextPardon, departures, failures, leaks, lastCabinet));
        m.put("term", term);
        m.put("vpName", vpName); m.put("vpSkill", nums(vpSkill)); m.put("vp", List.of(vpMission, vpUntil, vpMissions)); m.put("vpArg", vpArg); m.put("vpLoyalty", vpLoyalty);
        m.put("deleg", List.of(delegPeople, delegPardons));
        List<Object> ds = new ArrayList<>();
        for (Decision d : decisions) ds.add(d.toJson());
        m.put("decisions", ds);
        m.put("tone", nums(tone)); m.put("pressNums", nums(press, followers, qaScore, spScore));
        m.put("pressCounts", List.of(lastPresser, lastInterview, lastAddress, pressers, interviews, speeches, postsThisWeek, postWeek, qaType, qaStep, qaOutlet, spStep, spOpening, spAttacks, sotuYear));
        m.put("spSotu", spSotu);
        List<Object> qt = new ArrayList<>();
        for (String t : qaTopic) qt.add(t);
        m.put("qaTopic", qt); m.put("lastPoll", lastPoll);
        List<Object> st = new ArrayList<>();
        for (Storyline s : storylines) st.add(s.toJson());
        m.put("storylines", st);
        m.put("storyCool", new LinkedHashMap<String, Object>(storyCool));
        m.put("record", new ArrayList<Object>(record));
        m.put("departed", new ArrayList<Object>(departed));
        return m;
    }

    public static WestWingState fromJson(Map<String, Object> m) {
        WestWingState s = new WestWingState();
        s.week = Json.integer(m.get("week")); s.nextId = Json.integer(m.get("nextId"));
        readPeople(m.get("staff"), s.staff); readPeople(m.get("post"), s.post); readPeople(m.get("nominee"), s.nominee);
        List<Object> va = Json.array(m.get("voteAt"));
        for (int i = 0; i < NPOST; i++) s.voteAt[i] = Json.integer(va.get(i));
        double[] r = read(m.get("ready")), w = read(m.get("wf"));
        System.arraycopy(r, 0, s.ready, 0, NPOST); System.arraycopy(w, 0, s.wf, 0, NPOST);
        for (Object o : Json.array(m.get("actions"))) s.actions.add(Action.fromJson(Json.object(o)));
        List<Object> em = Json.array(m.get("emergency"));
        for (int i = 0; i < s.emergency.length; i++) s.emergency[i] = (Boolean) em.get(i);
        for (Object o : Json.array(m.get("cases"))) s.cases.add(Case.fromJson(Json.object(o)));
        double[] c = read(m.get("courts"));
        s.dDistrict = c[0]; s.dCircuit = c[1]; s.vacDistrict = c[2]; s.vacCircuit = c[3]; s.judgeFrac = c[4];
        List<Object> cc = Json.array(m.get("courtCounts"));
        s.judges = Json.integer(cc.get(0)); s.judgeStrategy = Json.integer(cc.get(1)); s.scotusVoteAt = Json.integer(cc.get(2)); s.scotusVacancies = Json.integer(cc.get(3));
        s.justicesAppointed = Json.integer(cc.get(4)); s.scotusRejected = Json.integer(cc.get(5));
        for (Object o : Json.array(m.get("justices"))) s.justices.add(Justice.fromJson(Json.object(o)));
        s.nomineeJ = m.get("nomineeJ") == null ? null : Justice.fromJson(Json.object(m.get("nomineeJ")));
        for (Object o : Json.array(m.get("scandals"))) s.scandals.add(Scandal.fromJson(Json.object(o)));
        List<Object> t = Json.array(m.get("trouble"));
        s.defiance = Json.integer(t.get(0)); s.pardons = Json.integer(t.get(1)); s.nextPardon = Json.integer(t.get(2)); s.departures = Json.integer(t.get(3));
        s.term = m.get("term") == null ? 1 : Json.integer(m.get("term"));
        s.failures = Json.integer(t.get(4)); s.leaks = Json.integer(t.get(5)); s.lastCabinet = Json.integer(t.get(6));
        s.vpName = (String) m.get("vpName");
        double[] vs = read(m.get("vpSkill"));
        System.arraycopy(vs, 0, s.vpSkill, 0, 4);
        List<Object> vp = Json.array(m.get("vp"));
        s.vpMission = Json.integer(vp.get(0)); s.vpUntil = Json.integer(vp.get(1)); s.vpMissions = Json.integer(vp.get(2));
        s.vpArg = (String) m.get("vpArg"); s.vpLoyalty = Json.num(m.get("vpLoyalty"));
        List<Object> dg = Json.array(m.get("deleg"));
        s.delegPeople = (Boolean) dg.get(0); s.delegPardons = (Boolean) dg.get(1);
        for (Object o : Json.array(m.get("decisions"))) s.decisions.add(Decision.fromJson(Json.object(o)));
        double[] to = read(m.get("tone"));
        System.arraycopy(to, 0, s.tone, 0, NOUTLET);
        double[] pn = read(m.get("pressNums"));
        s.press = pn[0]; s.followers = pn[1]; s.qaScore = pn[2]; s.spScore = pn[3];
        List<Object> pc = Json.array(m.get("pressCounts"));
        int k = 0;
        s.lastPresser = Json.integer(pc.get(k++)); s.lastInterview = Json.integer(pc.get(k++)); s.lastAddress = Json.integer(pc.get(k++)); s.pressers = Json.integer(pc.get(k++));
        s.interviews = Json.integer(pc.get(k++)); s.speeches = Json.integer(pc.get(k++)); s.postsThisWeek = Json.integer(pc.get(k++)); s.postWeek = Json.integer(pc.get(k++));
        s.qaType = Json.integer(pc.get(k++)); s.qaStep = Json.integer(pc.get(k++)); s.qaOutlet = Json.integer(pc.get(k++)); s.spStep = Json.integer(pc.get(k++));
        s.spOpening = Json.integer(pc.get(k++)); s.spAttacks = Json.integer(pc.get(k++)); s.sotuYear = Json.integer(pc.get(k));
        s.spSotu = (Boolean) m.get("spSotu");
        List<Object> qt = Json.array(m.get("qaTopic"));
        for (int i = 0; i < 3; i++) s.qaTopic[i] = (String) qt.get(i);
        s.lastPoll = (String) m.get("lastPoll");
        for (Object o : Json.array(m.get("storylines"))) s.storylines.add(Storyline.fromJson(Json.object(o)));
        Json.object(m.get("storyCool")).forEach((key, v) -> s.storyCool.put(key, Json.integer(v)));
        for (Object o : Json.array(m.get("record"))) s.record.add((String) o);
        if (m.get("departed") != null) for (Object o : Json.array(m.get("departed"))) s.departed.add((String) o);   // older saves: none recorded
        return s;
    }

    private static List<Object> people(Person[] ps) {
        List<Object> l = new ArrayList<>();
        for (Person p : ps) l.add(p == null ? null : p.toJson());
        return l;
    }

    private static void readPeople(Object o, Person[] into) {
        List<Object> l = Json.array(o);
        for (int i = 0; i < into.length && i < l.size(); i++) into[i] = l.get(i) == null ? null : Person.fromJson(Json.object(l.get(i)));
    }

    static List<Object> nums(double... v) {
        List<Object> l = new ArrayList<>(v.length);
        for (double x : v) l.add(x);
        return l;
    }

    static double[] read(Object o) {
        List<Object> l = Json.array(o);
        double[] v = new double[l.size()];
        for (int i = 0; i < v.length; i++) v[i] = Json.num(l.get(i));
        return v;
    }
}
