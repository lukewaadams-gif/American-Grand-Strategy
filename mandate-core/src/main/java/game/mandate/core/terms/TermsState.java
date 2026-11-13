package game.mandate.core.terms;

import game.mandate.core.data.Json;
import game.mandate.core.state.Party;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Terms of office (Resolute Desk's {@code Terms}, {@code Reelect} and {@code Reelection}): which term the president is
 * serving, the question of running again, a primary challenge, the second transition and its legacy goals, the race to
 * succeed a president who does not run, and the lame-duck weeks before the office changes hands.
 */
public final class TermsState {
    /** Whether the president runs again: not asked yet, asked, running, stepped aside, denied the nomination. */
    public static final int RUN_NOT_ASKED = 0, RUN_ASKED = 1, RUN_RUNNING = 2, RUN_STEPPED_ASIDE = 3, RUN_DENIED = 4;

    int term = 1;
    /** The president's week (from the first inauguration) when the current term began. */
    int termStartWeek;
    int runStage;
    /** A challenger in the president's own primaries (Resolute Desk's {@code re.challenger}); null if none. */
    String challenger;
    double challengerStrength, challengerShare;
    int primariesHeld;
    /** A successor has been elected: the lame-duck weeks run to the handoff. */
    boolean leaving;
    LocalDate handoff;
    String successor;
    Party successorParty;
    /** "lost", "served", "retired", "denied" or "removed" once the presidency's end is known; null before. */
    String ending;
    /** The second term's theme (an index into {@link TermsEngine#THEMES}); -1 before it is chosen. */
    int theme = -1;
    /** The legacy goals the theme set, with what each measured at the start of the term. */
    final List<String> goals = new ArrayList<>();
    final Map<String, Double> goalBase = new TreeMap<>();
    int inaugural = -1;
    /** After the second term's midterms: whether the president's party held (or won) the House; null before. */
    Boolean midtermHouse;
    /** Cabinet posts that changed hands at the start of the second term. */
    final List<String> departures = new ArrayList<>();
    /** The race to succeed the president, when the president does not run; null otherwise. */
    Race race;
    final List<Question> questions = new ArrayList<>();
    final List<String> record = new ArrayList<>();
    int nextId = 1;

    public int term() { return term; }
    public int termStartWeek() { return termStartWeek; }
    public int runStage() { return runStage; }
    public String challenger() { return challenger; }
    public double challengerShare() { return challengerShare; }
    public boolean leaving() { return leaving; }
    public LocalDate handoff() { return handoff; }
    public String successor() { return successor; }
    public Party successorParty() { return successorParty; }
    public String ending() { return ending; }
    public int theme() { return theme; }
    public List<String> goals() { return Collections.unmodifiableList(goals); }
    public Map<String, Double> goalBase() { return Collections.unmodifiableMap(goalBase); }
    public int inaugural() { return inaugural; }
    public Boolean midtermHouse() { return midtermHouse; }
    public List<String> departures() { return Collections.unmodifiableList(departures); }
    public Race race() { return race; }
    public List<Question> questions() { return Collections.unmodifiableList(questions); }
    public List<String> record() { return Collections.unmodifiableList(record); }

    public Question question(String id) {
        for (Question q : questions) if (q.id.equals(id)) return q;
        return null;
    }

    /** One of the people in the race to succeed the president, or the other party's nominee. */
    public static final class Contender {
        String id, name, home, role;
        Party party;
        /** Resolute Desk's 0-100 ratings: charisma, debating, speaking, crossover appeal, scandal. */
        double charisma, debate, speech, crossover, scandal;
        double ideology;
        /** Share of the primary vote so far, percent, and the delegates won. */
        double share;
        int delegates;
        boolean vicePresident;

        public String id() { return id; }
        public String name() { return name; }
        public String home() { return home; }
        public String role() { return role; }
        public Party party() { return party; }
        public double charisma() { return charisma; }
        public double debate() { return debate; }
        public double speech() { return speech; }
        public double crossover() { return crossover; }
        public double scandal() { return scandal; }
        public double ideology() { return ideology; }
        public double share() { return share; }
        public int delegates() { return delegates; }
        public boolean vicePresident() { return vicePresident; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("name", name); m.put("home", home); m.put("role", role); m.put("party", party.name());
            m.put("r", List.of(charisma, debate, speech, crossover, scandal, ideology, share));
            m.put("delegates", delegates); m.put("vp", vicePresident);
            return m;
        }

        static Contender fromJson(Map<String, Object> m) {
            Contender c = new Contender();
            c.id = (String) m.get("id"); c.name = (String) m.get("name"); c.home = (String) m.get("home"); c.role = (String) m.get("role");
            c.party = Party.valueOf((String) m.get("party"));
            List<Object> r = Json.array(m.get("r"));
            c.charisma = Json.num(r.get(0)); c.debate = Json.num(r.get(1)); c.speech = Json.num(r.get(2)); c.crossover = Json.num(r.get(3));
            c.scandal = Json.num(r.get(4)); c.ideology = Json.num(r.get(5)); c.share = Json.num(r.get(6));
            c.delegates = Json.integer(m.get("delegates")); c.vicePresident = (Boolean) m.get("vp");
            return c;
        }
    }

    /** The race for the White House when the president is not on the ballot (Resolute Desk's {@code Reelect}, stage 3). */
    public static final class Race {
        int year;
        LocalDate electionDay;
        final List<Contender> rivals = new ArrayList<>();
        /** The rival the president endorsed, or -1. */
        int endorsed = -1;
        /** The party's nominee (an index into the rivals), -1 until the primaries are over. */
        int nominee = -1;
        Contender opponent;
        int contests;
        /** The party's split: how bitter the primaries were, 0-100. */
        double split;
        double convention, oppConvention, mood, national, money;
        final double[] debate = new double[3];
        int debatesHeld;
        boolean done, won;
        int evMe, evOpp;
        double popMe, popOpp;
        final Map<String, Double> margins = new TreeMap<>();
        final List<String> primaryLog = new ArrayList<>();

        public int year() { return year; }
        public LocalDate electionDay() { return electionDay; }
        public List<Contender> rivals() { return Collections.unmodifiableList(rivals); }
        public int endorsed() { return endorsed; }
        public int nominee() { return nominee; }
        public Contender nomineeContender() { return nominee < 0 ? null : rivals.get(nominee); }
        public Contender opponent() { return opponent; }
        public int contests() { return contests; }
        public double split() { return split; }
        public double convention() { return convention; }
        public double oppConvention() { return oppConvention; }
        public double national() { return national; }
        public double money() { return money; }
        public double[] debate() { return debate.clone(); }
        public int debatesHeld() { return debatesHeld; }
        public boolean done() { return done; }
        public boolean won() { return won; }
        public int evMe() { return evMe; }
        public int evOpp() { return evOpp; }
        public double popMe() { return popMe; }
        public double popOpp() { return popOpp; }
        public Map<String, Double> margins() { return Collections.unmodifiableMap(margins); }
        public List<String> primaryLog() { return Collections.unmodifiableList(primaryLog); }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("year", year); m.put("electionDay", electionDay.toString());
            List<Object> rs = new ArrayList<>();
            for (Contender c : rivals) rs.add(c.toJson());
            m.put("rivals", rs);
            m.put("endorsed", endorsed); m.put("nominee", nominee); m.put("opponent", opponent == null ? null : opponent.toJson());
            m.put("contests", contests);
            m.put("x", List.of(split, convention, oppConvention, mood, national, money, debate[0], debate[1], debate[2], popMe, popOpp));
            m.put("debatesHeld", debatesHeld); m.put("done", done); m.put("won", won); m.put("ev", List.of(evMe, evOpp));
            m.put("margins", new LinkedHashMap<String, Object>(margins));
            m.put("primaryLog", new ArrayList<Object>(primaryLog));
            return m;
        }

        static Race fromJson(Map<String, Object> m) {
            Race r = new Race();
            r.year = Json.integer(m.get("year")); r.electionDay = LocalDate.parse((String) m.get("electionDay"));
            for (Object o : Json.array(m.get("rivals"))) r.rivals.add(Contender.fromJson(Json.object(o)));
            r.endorsed = Json.integer(m.get("endorsed")); r.nominee = Json.integer(m.get("nominee"));
            r.opponent = m.get("opponent") == null ? null : Contender.fromJson(Json.object(m.get("opponent")));
            r.contests = Json.integer(m.get("contests"));
            List<Object> x = Json.array(m.get("x"));
            r.split = Json.num(x.get(0)); r.convention = Json.num(x.get(1)); r.oppConvention = Json.num(x.get(2)); r.mood = Json.num(x.get(3));
            r.national = Json.num(x.get(4)); r.money = Json.num(x.get(5));
            for (int i = 0; i < 3; i++) r.debate[i] = Json.num(x.get(6 + i));
            r.popMe = Json.num(x.get(9)); r.popOpp = Json.num(x.get(10));
            r.debatesHeld = Json.integer(m.get("debatesHeld")); r.done = (Boolean) m.get("done"); r.won = (Boolean) m.get("won");
            List<Object> ev = Json.array(m.get("ev"));
            r.evMe = Json.integer(ev.get(0)); r.evOpp = Json.integer(ev.get(1));
            Json.object(m.get("margins")).forEach((k, v) -> r.margins.put(k, Json.num(v)));
            for (Object o : Json.array(m.get("primaryLog"))) r.primaryLog.add((String) o);
            return r;
        }
    }

    /** A question on the desk: whether to run, the second term's theme, the second inaugural address. */
    public static final class Question {
        String id, kind, title, body;
        final List<String> options = new ArrayList<>(), advice = new ArrayList<>();
        int recommended;
        LocalDate due;

        public String id() { return id; }
        /** "run", "theme", "inaugural" or "info". */
        public String kind() { return kind; }
        public String title() { return title; }
        public String body() { return body; }
        public List<String> options() { return Collections.unmodifiableList(options); }
        public List<String> advice() { return Collections.unmodifiableList(advice); }
        public int recommended() { return recommended; }
        public LocalDate due() { return due; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("kind", kind); m.put("title", title); m.put("body", body);
            m.put("options", new ArrayList<Object>(options)); m.put("advice", new ArrayList<Object>(advice));
            m.put("recommended", recommended); m.put("due", due.toString());
            return m;
        }

        static Question fromJson(Map<String, Object> m) {
            Question q = new Question();
            q.id = (String) m.get("id"); q.kind = (String) m.get("kind"); q.title = (String) m.get("title"); q.body = (String) m.get("body");
            for (Object o : Json.array(m.get("options"))) q.options.add((String) o);
            for (Object o : Json.array(m.get("advice"))) q.advice.add((String) o);
            q.recommended = Json.integer(m.get("recommended")); q.due = LocalDate.parse((String) m.get("due"));
            return q;
        }
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("term", term); m.put("termStartWeek", termStartWeek); m.put("runStage", runStage);
        m.put("challenger", challenger); m.put("chal", List.of(challengerStrength, challengerShare)); m.put("primariesHeld", primariesHeld);
        m.put("leaving", leaving); m.put("handoff", handoff == null ? null : handoff.toString());
        m.put("successor", successor); m.put("successorParty", successorParty == null ? null : successorParty.name());
        m.put("ending", ending); m.put("theme", theme);
        m.put("goals", new ArrayList<Object>(goals)); m.put("goalBase", new LinkedHashMap<String, Object>(goalBase));
        m.put("inaugural", inaugural); m.put("midtermHouse", midtermHouse); m.put("departures", new ArrayList<Object>(departures));
        m.put("race", race == null ? null : race.toJson());
        List<Object> qs = new ArrayList<>();
        for (Question q : questions) qs.add(q.toJson());
        m.put("questions", qs);
        m.put("record", new ArrayList<Object>(record)); m.put("nextId", nextId);
        return m;
    }

    public static TermsState fromJson(Map<String, Object> m) {
        TermsState s = new TermsState();
        s.term = Json.integer(m.get("term")); s.termStartWeek = Json.integer(m.get("termStartWeek")); s.runStage = Json.integer(m.get("runStage"));
        s.challenger = (String) m.get("challenger");
        List<Object> ch = Json.array(m.get("chal"));
        s.challengerStrength = Json.num(ch.get(0)); s.challengerShare = Json.num(ch.get(1));
        s.primariesHeld = Json.integer(m.get("primariesHeld"));
        s.leaving = (Boolean) m.get("leaving");
        s.handoff = m.get("handoff") == null ? null : LocalDate.parse((String) m.get("handoff"));
        s.successor = (String) m.get("successor");
        s.successorParty = m.get("successorParty") == null ? null : Party.valueOf((String) m.get("successorParty"));
        s.ending = (String) m.get("ending"); s.theme = Json.integer(m.get("theme"));
        for (Object o : Json.array(m.get("goals"))) s.goals.add((String) o);
        Json.object(m.get("goalBase")).forEach((k, v) -> s.goalBase.put(k, Json.num(v)));
        s.inaugural = Json.integer(m.get("inaugural"));
        s.midtermHouse = (Boolean) m.get("midtermHouse");
        for (Object o : Json.array(m.get("departures"))) s.departures.add((String) o);
        s.race = m.get("race") == null ? null : Race.fromJson(Json.object(m.get("race")));
        for (Object o : Json.array(m.get("questions"))) s.questions.add(Question.fromJson(Json.object(o)));
        for (Object o : Json.array(m.get("record"))) s.record.add((String) o);
        s.nextId = Json.integer(m.get("nextId"));
        return s;
    }
}
