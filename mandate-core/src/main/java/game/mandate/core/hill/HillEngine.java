package game.mandate.core.hill;

import game.mandate.core.campaign.Issue;
import game.mandate.core.congress.Bill;
import game.mandate.core.congress.BillCatalog;
import game.mandate.core.data.Csv;
import game.mandate.core.data.Fmt;
import game.mandate.core.economy.FiscalClass;
import game.mandate.core.economy.Channel;
import game.mandate.core.effects.Effect;
import game.mandate.core.hill.HillState.Bloc;
import game.mandate.core.hill.HillState.Favor;
import game.mandate.core.hill.HillState.Member;
import game.mandate.core.hill.HillState.Pending;
import game.mandate.core.hill.HillState.Probe;
import game.mandate.core.hill.HillState.Race;
import game.mandate.core.hill.HillState.Seat;
import game.mandate.core.hill.HillState.Stage;
import game.mandate.core.news.News;
import game.mandate.core.policy.PolicyCatalog;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.state.Party;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Congress as Resolute Desk plays it (its {@code congress} package: Hill, Relations, Legis, Budget, Midterms, Leaders;
 * and the probes and impeachment of {@code executive/Trouble}), stepped once a week. It reads the rest of the game
 * through a {@link Context} and answers with effects and news; it never reaches into another system.
 */
public final class HillEngine {
    static final int H = HillState.HOUSE, S = HillState.SENATE;

    /** Resolute Desk's bloc templates (Hill.java): share of the party's seats, economic and cultural position, loyalty, hawkishness. */
    record Template(String name, double share, double econ, double cult, double loyalty, double hawk, boolean moderate) {}

    static final Template[][][] TEMPLATES = {
            {   // House: Democrats, Republicans
                    {new Template("Progressive Caucus", .45, -.65, -.7, .8, .1, false), new Template("Mainstream Democrats", .38, -.4, -.5, .9, .3, false),
                            new Template("New Dems and Blue Dogs", .17, -.1, -.25, .75, .6, true)},
                    {new Template("Freedom Caucus", .17, .8, .8, .55, .9, false), new Template("Mainstream Republicans", .70, .55, .55, .85, .5, false),
                            new Template("Problem Solvers Republicans", .13, .25, .2, .75, .4, true)}},
            {   // Senate
                    {new Template("Progressive senators", .25, -.7, -.7, .85, .1, false), new Template("Mainstream Democrats", .60, -.4, -.45, .9, .3, false),
                            new Template("Moderate Democrats", .15, -.1, -.2, .7, .5, true)},
                    {new Template("Hardliners", .20, .8, .75, .6, .85, false), new Template("Mainstream Republicans", .68, .55, .5, .85, .5, false),
                            new Template("Moderate Republicans", .12, .2, .1, .7, .4, true)}}};

    static final String[][] COMMITTEES = {
            {"Ways and Means", "Appropriations", "Energy and Commerce", "Financial Services", "Judiciary", "Armed Services", "Foreign Affairs",
                    "Homeland Security", "Education and the Workforce", "Agriculture", "Transportation and Infrastructure", "Oversight"},
            {"Finance", "Appropriations", "Commerce", "Banking", "Judiciary", "Armed Services", "Foreign Relations", "Homeland Security",
                    "Health, Education, Labor and Pensions", "Agriculture", "Environment and Public Works", "Budget"}};
    /** The committee (index into each chamber's list) a policy area goes to: taxes, safety net, jobs, health, housing, energy, immigration, crime, education, rights, technology, defense. */
    static final int[] AREA_COMMITTEE = {0, 0, 8, 2, 10, 2, 7, 4, 8, 4, 2, 5};
    static final String[] PRICES = {"a project", "an appointment", "an endorsement", "a change to the bill"};
    static final String[] PAY_FOR = {"BORROW", "REVENUE", "CUTS"};
    /**
     * How far a state's districts spread around its lean, points: a delegation's Democratic share is the normal curve at
     * (lean + swing) over this. 22 reproduces the delegations of the 2020s (California about 44-8, Texas 12-26, New York
     * 19-7, Ohio 5-10); Resolute Desk sets only the national total.
     */
    static final double DISTRICT_SPREAD = 22;

    private final BillCatalog catalog;
    private final PolicyCatalog policies;
    private final Map<String, List<String>> names = new TreeMap<>();
    private final Map<String, String> firstLeaders;
    /** Who is seated: the real roster in 2028-29 and fictional newcomers from 2030, or the fictional cast throughout. */
    private game.mandate.core.people.People people = game.mandate.core.people.People.fictional();

    /** Sets who the Hill seats (GameEngine, from the game's setup). */
    public void people(game.mandate.core.people.People p) { this.people = p; }

    public HillEngine(BillCatalog catalog, PolicyCatalog policies) {
        this.catalog = catalog;
        this.policies = policies;
        for (String[] r : Csv.readTsv("/data/rd/names.tsv")) names.computeIfAbsent(r[0], k -> new ArrayList<>()).add(r[1]);
        Map<String, String> era = new TreeMap<>();
        for (String[] r : Csv.readTsv("/data/rd/era_2029.tsv")) if (r.length > 1) era.put(r[0], r[1]);
        firstLeaders = Map.of("houseD", era.get("speakerD"), "houseR", era.get("speakerR"), "senateD", era.get("leaderD"), "senateR", era.get("leaderR"));
    }

    /**
     * The party leaders of January 2029, by chamber and party (houseD, houseR, senateD, senateR): the real leaders with real
     * people, else Resolute Desk's era file.
     */
    public Map<String, String> firstLeaders() {
        if (!people.real()) return firstLeaders;
        Map<String, String> m = new TreeMap<>();
        for (String k : List.of("houseD", "houseR", "senateD", "senateR")) m.put(k, people.data().leader(k).orElse(firstLeaders.get(k)));
        return m;
    }

    /** What the Hill reads from the rest of the game for one step. */
    public record Context(LocalDate date, int termWeek, Party president, double approval, double charisma, double establishment, double staffQuality,
                          double hillHours, List<String> visited, Map<String, Double> campaign, double warChest, double polarization,
                          int houseDem, int senateDem, double growth, double unemployment, double unemploymentRef, double deficitPct, double debtPct,
                          double nominalGdpTn, Map<String, Double> stateLean, LocalDate electionDay, double scandal, double honeymoon,
                          double politicalQuality, double counselQuality, double scandalPressure, String scandalTitle, double wingBase, double wingOther,
                          int opposition, double lameDuck) {
        /** Without the lame-duck factor (a first-term president). */
        public Context(LocalDate date, int termWeek, Party president, double approval, double charisma, double establishment, double staffQuality,
                       double hillHours, List<String> visited, Map<String, Double> campaign, double warChest, double polarization,
                       int houseDem, int senateDem, double growth, double unemployment, double unemploymentRef, double deficitPct, double debtPct,
                       double nominalGdpTn, Map<String, Double> stateLean, LocalDate electionDay, double scandal, double honeymoon,
                       double politicalQuality, double counselQuality, double scandalPressure, String scandalTitle, double wingBase, double wingOther,
                       int opposition) {
            this(date, termWeek, president, approval, charisma, establishment, staffQuality, hillHours, visited, campaign, warChest, polarization, houseDem, senateDem,
                    growth, unemployment, unemploymentRef, deficitPct, debtPct, nominalGdpTn, stateLean, electionDay, scandal, honeymoon, politicalQuality, counselQuality,
                    scandalPressure, scandalTitle, wingBase, wingOther, opposition, 0);
        }
        /** The context as it was, before the West Wing: staff of middling quality, no executive scandal, the wings content. */
        public Context(LocalDate date, int termWeek, Party president, double approval, double charisma, double establishment, double staffQuality,
                       double hillHours, List<String> visited, Map<String, Double> campaign, double warChest, double polarization,
                       int houseDem, int senateDem, double growth, double unemployment, double unemploymentRef, double deficitPct, double debtPct,
                       double nominalGdpTn, Map<String, Double> stateLean, LocalDate electionDay, double scandal, double honeymoon) {
            this(date, termWeek, president, approval, charisma, establishment, staffQuality, hillHours, visited, campaign, warChest, polarization, houseDem, senateDem,
                    growth, unemployment, unemploymentRef, deficitPct, debtPct, nominalGdpTn, stateLean, electionDay, scandal, honeymoon, staffQuality, staffQuality, 0, null, 55, 55, 1);
        }
    }

    /** What a step or an order did: transitions for the router, and news. */
    public record Outcome(List<Effect> effects, List<News> news) {
        static Outcome empty() { return new Outcome(new ArrayList<>(), new ArrayList<>()); }
    }

    // =========================================================================================
    //  Seating a Congress
    // =========================================================================================

    /**
     * The 121st Congress on inauguration day: every Senate seat by state and class (class I from the data, the rest by
     * each state's lean so the totals match), the House by state, the blocs, the leaders, chairs and swing votes.
     */
    public HillState newHill(Context c, Map<String, Party> classOne, Map<String, Party> classThree, Map<String, Integer> houseSeats,
                             Map<String, String> leaders, double popularMargin, DeterministicRandomManager rng) {
        HillState s = new HillState();
        // the Senate: class I as the data says; classes II and III so the Democrats' total is right, most Democratic states first
        List<String> states = new ArrayList<>(houseSeats.keySet());
        int year = c.date().getYear();
        for (String st : states) if (classOne.containsKey(st)) s.senate.add(seat(st, 1, classOne.get(st), year, rng));
        int dLeft = c.senateDem() - (int) s.senate.stream().filter(x -> x.party == Party.DEMOCRAT).count();
        // each state's other seat (or seats): class III where the data has one, class II for the rest
        List<String[]> open = new ArrayList<>();
        for (String st : states) {
            List<Integer> cls = new ArrayList<>();
            if (classOne.containsKey(st)) cls.add(1);
            if (classThree.containsKey(st)) cls.add(3);
            if (cls.size() < 2) cls.add(2);
            for (int cl : cls) if (cl != 1) open.add(new String[]{st, String.valueOf(cl)});
        }
        open.sort((a, b) -> Double.compare(c.stateLean().getOrDefault(b[0], 0.0), c.stateLean().getOrDefault(a[0], 0.0)));
        for (String[] r : open) {
            Party p = dLeft-- > 0 ? Party.DEMOCRAT : Party.REPUBLICAN;
            s.senate.add(seat(r[0], Integer.parseInt(r[1]), p, year, rng));
        }
        // the House by state, with a national swing that makes the total right
        s.houseSeats.putAll(houseSeats);
        allocateHouse(s, c.stateLean(), c.houseDem(), Map.of());
        s.mandate = s.mandate0 = clamp(2 + 0.55 * popularMargin, 0.5, 15);
        s.debtLimit = Math.round((debtSubject(c) + 0.9) * 10) / 10.0;
        for (int i = 0; i < 12; i++) s.approps[i] = 4;   // the year begins on a stopgap
        s.budgetFy = fiscalYear(c.date());
        seatBlocs(s, c, rng);
        seatMembers(s, c, leaders, rng, true);
        s.record.add(c.date() + ": the " + ordinal(s.congressNumber) + " Congress is seated, " + split(s, c));
        return s;
    }

    private Seat seat(String state, int cl, Party p, int year, DeterministicRandomManager rng) {
        Seat x = new Seat();
        x.state = state; x.seatClass = cl; x.party = p;
        // with real people, the seat's real senator if the record has one of that party still serving; else the office
        x.holder = people.real() ? people.data().senator(state, cl, p, year).orElse(game.mandate.core.people.People.office("Senator", state)) : person(rng);
        return x;
    }

    /**
     * House seats by state: each delegation splits by the state's lean, its own swing ({@code extra}: incumbency and
     * local noise at an election) and a national swing found so the total is {@code houseDem}.
     */
    void allocateHouse(HillState s, Map<String, Double> lean, int houseDem, Map<String, Double> extra) {
        double lo = -60, hi = 60;
        Map<String, Integer> best = new TreeMap<>();
        for (int it = 0; it < 40; it++) {
            double mid = (lo + hi) / 2;
            int total = 0;
            best.clear();
            for (Map.Entry<String, Integer> e : s.houseSeats.entrySet()) {
                int d = (int) Math.round(e.getValue() * phi((lean.getOrDefault(e.getKey(), 0.0) + extra.getOrDefault(e.getKey(), 0.0) + mid) / DISTRICT_SPREAD));
                best.put(e.getKey(), d);
                total += d;
            }
            if (total == houseDem) break;
            if (total < houseDem) lo = mid; else hi = mid;
        }
        // whatever rounding leaves goes to the closest states
        int total = best.values().stream().mapToInt(Integer::intValue).sum();
        List<String> order = new ArrayList<>(best.keySet());
        order.sort((a, b) -> Double.compare(Math.abs(lean.getOrDefault(a, 0.0)), Math.abs(lean.getOrDefault(b, 0.0))));
        for (int i = 0; total != houseDem && i < order.size() * 3; i++) {
            String st = order.get(i % order.size());
            int d = best.get(st), n = s.houseSeats.get(st);
            if (total < houseDem && d < n) { best.put(st, d + 1); total++; }
            else if (total > houseDem && d > 0) { best.put(st, d - 1); total--; }
        }
        s.houseD.clear();
        s.houseD.putAll(best);
    }

    /** Resolute Desk's {@code Hill.seats}: each party's seats split among its three blocs, the relationship carried over. */
    void seatBlocs(HillState s, Context c, DeterministicRandomManager rng) {
        Map<String, Double> rel = new TreeMap<>();
        for (Bloc b : s.blocs) rel.put(b.chamber + b.name, b.rel);
        s.blocs.clear();
        for (int ch = 0; ch < 2; ch++) {
            int dem = ch == H ? c.houseDem() : c.senateDem(), size = ch == H ? 435 : 100;
            for (int pi = 0; pi < 2; pi++) {
                Party p = pi == 0 ? Party.DEMOCRAT : Party.REPUBLICAN;
                int total = pi == 0 ? dem : size - dem, given = 0, biggest = -1;
                Template[] t = TEMPLATES[ch][pi];
                for (int k = 0; k < 3; k++) {
                    Bloc b = new Bloc();
                    b.name = t[k].name(); b.party = p; b.chamber = ch; b.econ = t[k].econ(); b.cult = t[k].cult(); b.loyalty = t[k].loyalty();
                    b.hawk = t[k].hawk(); b.moderate = t[k].moderate();
                    b.seats = (int) Math.round(total * t[k].share());
                    given += b.seats;
                    b.rel = rel.getOrDefault(ch + b.name, p == c.president() ? 58.0 : 38.0);
                    s.blocs.add(b);
                    if (biggest < 0 || b.seats > s.blocs.get(biggest).seats) biggest = s.blocs.size() - 1;
                }
                s.blocs.get(biggest).seats += total - given;
            }
        }
    }

    /**
     * The leaders, twelve committee chairs in each chamber (all from the majority) and ten swing votes. On a new Congress
     * the leaders of a chamber that kept its majority stay; a chamber that flipped gets new leaders and chairs.
     */
    void seatMembers(HillState s, Context c, Map<String, String> leaders, DeterministicRandomManager rng, boolean first) {
        Map<String, Member> old = new TreeMap<>();
        for (Member m : s.members) old.put(m.role + "|" + m.chamber + "|" + m.party + "|" + (m.committee == null ? "" : m.committee), m);
        List<Member> swing = new ArrayList<>();
        for (Member m : s.members) if (m.role.equals("Swing vote")) swing.add(m);
        s.members.clear();
        for (int ch = 0; ch < 2; ch++) {
            Party maj = majority(s, c, ch);
            for (Party p : Party.values()) {
                String role = ch == H ? (p == maj ? "Speaker" : "Minority Leader") : (p == maj ? "Majority Leader" : "Minority Leader");
                String key = (ch == H ? "house" : "senate") + (p == Party.DEMOCRAT ? "D" : "R");
                Member prior = null;
                for (Member m : old.values()) if (m.chamber == ch && m.party == p && m.leader()) prior = m;
                Member m = new Member();
                m.id = "m" + s.nextId++;
                m.name = prior != null ? prior.name : leaders.getOrDefault(key, person(rng));
                m.role = role; m.party = p; m.chamber = ch; m.bloc = blocIndex(s, ch, p, 1);
                m.rel = prior != null ? prior.rel : p == c.president() ? 62 : 35;
                m.econ = s.blocs.get(m.bloc).econ; m.cult = s.blocs.get(m.bloc).cult;
                s.members.add(m);
            }
            for (int k = 0; k < 12; k++) {
                String committee = COMMITTEES[ch][k];
                Member prior = old.get("Chair|" + ch + "|" + maj + "|" + committee);
                Member m = new Member();
                m.id = prior != null ? prior.id : "m" + s.nextId++;
                m.name = prior != null ? prior.name : fill(ch == H ? "H" : "S", committee, maj, c.date().getYear(), taken(s), rng);
                m.role = "Chair"; m.committee = committee; m.party = maj; m.chamber = ch;
                double r = rng.uniform("hill.seat");
                m.bloc = blocIndex(s, ch, maj, r < 0.7 ? 1 : r < 0.85 ? 0 : 2);
                m.rel = prior != null ? prior.rel : maj == c.president() ? 55 : 35;
                m.econ = s.blocs.get(m.bloc).econ + 0.08 * rng.gaussian("hill.seat"); m.cult = s.blocs.get(m.bloc).cult + 0.08 * rng.gaussian("hill.seat");
                s.members.add(m);
            }
        }
        // swing votes: two moderates of each party in each chamber and one of the president's base; those still in office stay
        for (int ch = 0; ch < 2; ch++) {
            for (Party p : Party.values()) {
                int want = p == c.president() ? 3 : 2;
                List<Member> keep = new ArrayList<>();
                for (Member m : swing) if (m.chamber == ch && m.party == p && rng.uniform("hill.retire") > 0.18) keep.add(m);
                for (int k = 0; k < want; k++) {
                    Member m;
                    if (k < keep.size()) m = keep.get(k);
                    else {
                        m = new Member();
                        m.id = "m" + s.nextId++;
                        m.role = "Swing vote"; m.party = p; m.chamber = ch;
                        boolean base = p == c.president() && k == 2;
                        m.bloc = blocIndex(s, ch, p, base ? 0 : 2);
                        m.state = swingState(s, ch, p, rng);
                        // a real moderate of that party while the record has one, a newcomer from 2030, else the office
                        int year = c.date().getYear();
                        game.mandate.core.people.RealPeople.Swing real = null;
                        if (people.real()) for (var x : base ? people.data().base(ch == H ? "H" : "S", p) : people.data().swing(ch == H ? "H" : "S", p, year))
                            if (!taken(s).contains(x.name())) { real = x; break; }
                        if (real != null) { m.name = real.name(); m.state = real.state(); }
                        else if (people.newcomer(year, rng.uniform("hill.people"))) m.name = person(rng);
                        else m.name = game.mandate.core.people.People.office(ch == H ? "Representative" : "Senator", m.state);
                        m.price = PRICES[rng.nextInt("hill.seat", PRICES.length)];
                        m.rel = 45 + 15 * rng.uniform("hill.seat");
                        m.econ = s.blocs.get(m.bloc).econ + 0.08 * rng.gaussian("hill.seat"); m.cult = s.blocs.get(m.bloc).cult + 0.08 * rng.gaussian("hill.seat");
                    }
                    s.members.add(m);
                }
            }
        }
        // favours owed by members who are gone are dropped
        s.favors.removeIf(f -> s.member(f.member) == null);
    }

    private String swingState(HillState s, int ch, Party p, DeterministicRandomManager rng) {
        List<String> pool = new ArrayList<>();
        if (ch == S) { for (Seat x : s.senate) if (x.party == p) pool.add(x.state); }
        else for (Map.Entry<String, Integer> e : s.houseD.entrySet()) {
            int d = e.getValue(), n = s.houseSeats.getOrDefault(e.getKey(), 0);
            if ((p == Party.DEMOCRAT ? d : n - d) > 0) pool.add(e.getKey());
        }
        return pool.isEmpty() ? "OH" : pool.get(rng.nextInt("hill.seat", pool.size()));
    }

    /** The index in {@code s.blocs} of a party's bloc in a chamber (0 left, 1 mainstream, 2 moderate for Democrats; 0 right for Republicans). */
    static int blocIndex(HillState s, int ch, Party p, int k) { return ch * 6 + (p == Party.DEMOCRAT ? 0 : 3) + Math.max(0, Math.min(2, k)); }

    public Party majority(HillState s, Context c, int ch) {
        if (ch == H) return c.houseDem() >= 218 ? Party.DEMOCRAT : Party.REPUBLICAN;
        return c.senateDem() > 50 ? Party.DEMOCRAT : c.senateDem() < 50 ? Party.REPUBLICAN : c.president();
    }

    String split(HillState s, Context c) {
        return "House " + c.houseDem() + "-" + (435 - c.houseDem()) + ", Senate " + c.senateDem() + "-" + (100 - c.senateDem()) + " (Democrats first)";
    }

    // =========================================================================================
    //  The week
    // =========================================================================================

    public Outcome week(HillState s, Context c, DeterministicRandomManager rng) {
        Outcome out = Outcome.empty();
        s.week++;
        relations(s, c, out, rng);
        legislation(s, c, out, rng);
        budget(s, c, out, rng);
        elections(s, c, out, rng);
        trouble(s, c, out, rng);
        overdue(s, c, out, rng);
        return out;
    }

    /** Resolute Desk's {@code Relations.week} and {@code Hill.meeting}: hours, warmth from meetings, drift, the mandate, asks, promises. */
    private void relations(HillState s, Context c, Outcome out, DeterministicRandomManager rng) {
        s.hoursUsed = 0;
        s.hoursMax = 2 + c.hillHours() + (c.staffQuality() >= 0.7 ? 1 : 0);
        double slots = c.hillHours() / 2.5, k = c.establishment() / 70;
        for (int i = 0; i < slots; i++) {
            for (Member m : s.members) {
                if (m.leader()) m.rel = clamp(m.rel + (m.party == c.president() ? 1.6 : 0.9) * k, 0, 100);
                else m.rel = clamp(m.rel + 0.4 * k, 0, 100);
            }
            for (Bloc b : s.blocs) b.rel = clamp(b.rel + (b.party == c.president() ? 0.7 : 0.35) * k, 0, 100);
        }
        double pull = (c.approval() - 45) * 0.3 + (c.staffQuality() - 0.55) * 12;
        for (Bloc b : s.blocs) {
            boolean own = b.party == c.president();
            b.rel += 0.02 * ((own ? 55 : 35) + pull * (own ? 1 : 0.5) - b.rel);
        }
        for (Member m : s.members) m.rel += 0.015 * ((m.party == c.president() ? 55 : 35) + pull * 0.5 - m.rel);
        // the party's wings pull their blocs toward the president or away; the opposition's strategy moves the other side
        for (Bloc b : s.blocs) {
            if (b.party == c.president()) {
                boolean base = b.name.contains("Progressive") || b.name.contains("Freedom") || b.name.contains("Hardliners");
                b.rel = clamp(b.rel + ((base ? c.wingBase() : c.wingOther()) - 55) * 0.006, 0, 100);
            } else b.rel = clamp(b.rel + (1 - c.opposition()) * 0.06, 0, 100);
        }
        s.mandate *= 0.965;
        if (s.week % 4 == 0 && s.appointments < 6) s.appointments++;
        // a visit keeps a promise there; campaigning for an own-party member warms them
        for (String st : c.visited()) {
            for (Favor f : new ArrayList<>(s.favors)) if (f.promise && !f.settled && st.equals(f.state)) {
                f.settled = true;
                s.kept++;
                Member m = s.member(f.member);
                if (m != null) { m.rel = clamp(m.rel + 6, 0, 100); earn(s, m, "a promise kept"); }
                out.news().add(news(c, News.Kind.GOOD, "The president visits " + st + ", keeping a promise to " + (m == null ? "a member" : m.name), null, 0.6, "Congress"));
            }
            for (Member m : s.members) if (st.equals(m.state) && m.party == c.president()) m.rel = clamp(m.rel + 2, 0, 100);
        }
        // promises broken
        for (Favor f : s.favors) if (f.promise && !f.settled && s.week > f.due) {
            f.settled = true;
            s.broken++;
            Member m = s.member(f.member);
            if (m != null) {
                m.rel = clamp(m.rel - 20, 0, 100);
                s.blocs.get(m.bloc).rel = clamp(s.blocs.get(m.bloc).rel - 4, 0, 100);
                out.news().add(news(c, News.Kind.BAD, m.name + " says the president broke a promise", "The White House had promised a visit to " + f.state + ".", 0.9, null));
            }
        }
        s.favors.removeIf(f -> f.settled && s.week - f.week > 26);
        // a member asks for something
        if (s.askMember == null && s.week >= s.nextAsk) {
            List<Member> askers = new ArrayList<>();
            for (Member m : s.members) if (m.role.equals("Swing vote") || m.role.equals("Chair")) askers.add(m);
            if (!askers.isEmpty()) {
                Member m = askers.get(rng.nextInt("hill.ask", askers.size()));
                double r = rng.uniform("hill.ask");
                s.askMember = m.id;
                s.askKind = r < 0.4 && m.state != null ? "visit" : r < 0.75 ? "project" : "appointment";
                s.pending.add(new Pending("ask", m.id, s.week + 2));
                out.news().add(news(c, News.Kind.INFO, m.name + " asks the White House for " + (s.askKind.equals("visit") ? "a visit to " + m.state : s.askKind.equals("project") ? "a project at home" : "an appointment"),
                        null, 0.4, "Congress"));
            }
            s.nextAsk = s.week + 5 + rng.nextInt("hill.ask", 5);
        }
    }

    private void earn(HillState s, Member m, String why) {
        m.rel = clamp(m.rel + 4, 0, 100);
        if (m.owesFavor) return;
        m.owesFavor = true;
        Favor f = new Favor();
        f.member = m.id; f.why = why; f.week = s.week;
        s.favors.add(f);
    }

    /** Raise or lower the president's party's blocs (Resolute Desk's {@code win} and {@code setback}). */
    private static void win(HillState s, Party president, double p) {
        for (Bloc b : s.blocs) if (b.party == president) b.rel = clamp(b.rel + p, 0, 100);
    }

    // =========================================================================================
    //  Legislation (Resolute Desk's Legis)
    // =========================================================================================

    /** A draft of one of the game's bills, or of a policy step ({@code pol:<policy>:<steps>}). */
    public String draft(HillState s, Context c, String source) {
        Bill b = null;
        PolicyCatalog.Def d = null;
        int steps = 0;
        int from = 0;
        if (source.startsWith("pol:")) {
            String[] t = source.split(":");
            d = policies.get(t[1]);
            from = Integer.parseInt(t[2]);
            steps = Integer.parseInt(t[3]) - from;
            if (steps == 0 || from + steps < 0 || from + steps > d.top()) throw new IllegalArgumentException("That is not a step the policy can take");
            if (!d.can(PolicyCatalog.Route.BILL) && !d.can(PolicyCatalog.Route.BUDGET)) throw new IllegalArgumentException("The " + lower(d.name()) + " is changed by order or rule, not by law");
        } else {
            b = catalog.get(source);
            if (lawAlready(s, source)) throw new IllegalArgumentException("The " + b.name() + " is already law");
        }
        for (HillState.Bill x : s.bills) if (x.ours && x.source.equals(source) && (x.stage == Stage.DRAFT || x.stage.active()))
            throw new IllegalArgumentException("That bill is already " + (x.stage == Stage.DRAFT ? "being drafted" : "in Congress"));
        HillState.Bill x = new HillState.Bill();
        x.id = "b" + s.nextId++;
        x.source = source;
        x.ours = true;
        if (b != null) {
            x.name = b.name();
            x.description = issueLine(b);
            boolean cultural = b.issue() == Issue.IMMIGRATION || b.issue() == Issue.DEMOCRACY || b.issue() == Issue.EDUCATION || b.issue() == Issue.ABORTION || b.issue() == Issue.CRIME;
            x.econ = cultural ? b.ideology() * 0.5 : b.ideology();
            x.cult = cultural ? b.ideology() : b.ideology() * 0.3;
            x.cost = b.annualCostBn();
            x.budgetEligible = !b.requires60();
            x.revenue = b.annualCostBn() < 0;
            x.support = billSupport(source);
        } else {
            int to = from + steps;
            x.name = d.name() + ": " + lower(d.levels().get(to));
            x.description = "Changes the " + lower(d.name()) + " from " + lower(d.levels().get(from)) + " to " + lower(d.levels().get(to)) + ".";
            x.econ = clamp(d.econ() * steps, -1, 1);
            x.cult = clamp(d.cult() * steps, -1, 1);
            double cost = 0;
            for (int i = 0; i < Math.abs(steps); i++) cost += steps > 0 ? d.stepCost(from + i) : -d.stepCost(from - i - 1);
            x.cost = cost;
            x.budgetEligible = d.can(PolicyCatalog.Route.BUDGET);
            x.revenue = d.area() == 0;
            x.support = d.supportFor(steps);
        }
        x.first = H;
        s.bills.add(x);
        return x.id;
    }

    private static String issueLine(Bill b) { return "Its issue is " + b.issue().label().toLowerCase() + ". " + (b.requires60() ? "The Byrd rule keeps it out of reconciliation." : "It can go through reconciliation."); }

    /** Public support for a bill: its policies' support for the way it moves them, weighed by how far. */
    double billSupport(String billId) {
        double sum = 0, n = 0;
        for (Map.Entry<String, Integer> e : policies.bill(billId).entrySet()) {
            PolicyCatalog.Def d = policies.get(e.getKey());
            sum += d.supportFor(e.getValue()) * Math.abs(e.getValue());
            n += Math.abs(e.getValue());
        }
        return n == 0 ? 50 : sum / n;
    }

    /** A bill's position after drafting (Resolute Desk's {@code Bill.econ} and {@code cult}): bigger and broader moves it further from the middle. */
    /** A bill's lean: -1 a Democratic bill, +1 a Republican one, 0 bipartisan. */
    int lean(HillState.Bill b) {
        if (b.source.startsWith("pol:")) {
            String[] t = b.source.split(":");
            PolicyCatalog.Def d = policies.get(t[1]);
            int steps = Integer.parseInt(t[3]) - Integer.parseInt(t[2]);
            return d.leanSign() == 0 ? 0 : -d.leanSign() * Integer.signum(steps);
        }
        if (!catalog.has(b.source)) return 0;
        return catalog.get(b.source).sponsor().map(p -> p == Party.DEMOCRAT ? -1 : 1).orElse(0);
    }

    double[] position(HillState.Bill b, Party president) {
        int lean = lean(b);
        Party sp = b.ours ? president : b.author;
        double dir = lean != 0 ? lean : 0.5 * (sp == Party.DEMOCRAT ? -1 : sp == Party.REPUBLICAN ? 1 : 0);
        double e = b.econ + dir * 0.15 * (b.size - 1) + dir * 0.18 * (b.scope - 0.5) + (b.payFor == 1 ? -0.15 : b.payFor == 2 ? 0.15 : 0);
        double cl = b.cult + dir * 0.18 * (b.scope - 0.5);
        return new double[]{clamp(e, -1, 1), clamp(cl, -1, 1)};
    }

    public double support(HillState.Bill b) {
        return b.support - 4 * (b.scope - 0.5) - (b.payFor == 0 && b.cost > 30 ? 2 : 0) + (b.payFor == 1 ? 2 : 0);
    }

    public void edit(HillState s, String billId, double size, double scope, int payFor, boolean recon, int first) {
        HillState.Bill b = need(s, billId);
        if (!b.ours) throw new IllegalArgumentException("Only your own bills can be changed");
        if (b.stage.ordinal() > Stage.FLOOR2.ordinal()) throw new IllegalArgumentException("It is past changing");
        b.size = clamp(size, 0.5, 1.5);
        b.scope = clamp(scope, 0, 1);
        b.payFor = Math.max(0, Math.min(2, payFor));
        if (b.stage == Stage.DRAFT) {
            b.recon = recon && b.budgetEligible;
            b.first = first == S && !b.revenue ? S : H;
        }
    }

    /** Sends a draft to Congress (at most three of the president's bills at once). */
    public Outcome send(HillState s, Context c, String billId) {
        HillState.Bill b = need(s, billId);
        if (b.stage != Stage.DRAFT) throw new IllegalArgumentException("It is already in Congress");
        long active = s.bills.stream().filter(x -> x.ours && x.stage.active()).count();
        if (active >= 3) throw new IllegalArgumentException("Three of your bills are already before Congress");
        if (b.recon) {
            if (!unified(c)) throw new IllegalArgumentException("Reconciliation needs your party to hold both chambers");
            if (s.reconFy == fiscalYear(c.date())) throw new IllegalArgumentException("This fiscal year's reconciliation bill is already used");
            s.reconFy = fiscalYear(c.date());
        }
        b.stage = Stage.COMMITTEE;
        b.stageWeek = s.week;
        b.introduced = s.week;
        Outcome out = Outcome.empty();
        out.news().add(news(c, News.Kind.INFO, "The White House sends the " + b.name + " to Congress", null, 1.0, null));
        s.record.add(c.date() + ": sent the " + b.name + " to Congress");
        return out;
    }

    /**
     * Drafts, shapes and sends a bill in one step (the old Congress screen's "push"): all or nothing, so a refusal leaves
     * the Hill exactly as it was.
     */
    public Outcome propose(HillState s, Context c, String source, double size, double scope, boolean recon) {
        int next = s.nextId, reconFy = s.reconFy;
        String id = draft(s, c, source);
        try {
            HillState.Bill b = need(s, id);
            edit(s, id, size, scope, 0, recon && b.budgetEligible, H);
            return send(s, c, id);
        } catch (IllegalArgumentException e) {
            s.bills.remove(s.bill(id));
            s.nextId = next;
            s.reconFy = reconFy;
            throw e;
        }
    }

    public void withdraw(HillState s, String billId) {
        HillState.Bill b = need(s, billId);
        if (!b.ours || b.stage.ordinal() > Stage.FLOOR2.ordinal()) throw new IllegalArgumentException("It cannot be withdrawn now");
        if (b.recon && b.stage != Stage.DRAFT) s.reconFy = -1;
        s.bills.remove(b);
    }

    public static int stageWeeks(HillState.Bill b) {
        return switch (b.stage) {
            case COMMITTEE, COMMITTEE2 -> b.recon ? 2 : 3;
            case CONFERENCE -> 2;
            default -> 1;
        };
    }

    private void legislation(HillState s, Context c, Outcome out, DeterministicRandomManager rng) {
        boolean[] floorUsed = new boolean[2];
        for (HillState.Bill b : new ArrayList<>(s.bills)) step(s, c, b, floorUsed, out, rng);
        if (s.week % 7 == 3) congressBill(s, c, out, rng);
        s.bills.removeIf(b -> (b.stage == Stage.DEAD || b.stage == Stage.LAW || b.stage == Stage.OVERRIDE) && s.week - b.stageWeek > 26);
    }

    private void step(HillState s, Context c, HillState.Bill b, boolean[] floorUsed, Outcome out, DeterministicRandomManager rng) {
        if (!b.stage.active() || b.stage == Stage.DESK) return;
        if (s.week - b.stageWeek < stageWeeks(b)) return;
        int ch = b.chamberNow();
        switch (b.stage) {
            case COMMITTEE, COMMITTEE2 -> {
                Member chair = chairFor(s, b, ch);
                if (rng.uniform("hill.committee") < chairOk(s, c, b, ch)) {
                    if (chair != null && chair.party != sponsorParty(b, c) && b.size > 0.8) { b.size = Math.max(0.6, b.size - 0.15); b.note = "The committee trimmed it."; }
                    b.stage = b.stage == Stage.COMMITTEE ? Stage.FLOOR : Stage.FLOOR2;
                    b.stageWeek = s.week;
                    b.bottled = false;
                    out.news().add(news(c, News.Kind.INFO, "The " + HillState.CHAMBER[ch] + " " + (chair == null ? "committee" : chair.committee + " Committee") + " approves the " + b.name, null, 0.6, "Congress"));
                } else {
                    if (!b.bottled) out.news().add(news(c, News.Kind.BAD, (chair != null ? chair.name + " (" + chair.party.code() + ")" : "The committee chair") + " bottles up the " + b.name + " in committee",
                            null, b.ours ? 1.0 : 0.5, b.ours ? null : "Congress"));
                    b.bottled = true;
                    b.stageWeek = s.week;
                    if (s.week - b.introduced > 30) kill(s, c, b, "dies in committee", out);
                }
            }
            case FLOOR, FLOOR2 -> {
                if (floorUsed[ch]) return;
                if (hold(s, c, b, ch, out)) return;
                floorUsed[ch] = true;
                if (vote(s, c, b, ch, false, out, rng)) { b.stage = b.stage == Stage.FLOOR ? Stage.COMMITTEE2 : Stage.CONFERENCE; b.stageWeek = s.week; }
                else kill(s, c, b, ch == S && !b.recon && !s.filibusterGone && b.senYes >= 50 ? "is filibustered" : "fails", out);
            }
            case CONFERENCE -> {
                b.stage = Stage.DESK;
                b.stageWeek = s.week;
                s.pending.add(new Pending("desk", b.id, s.week + 2));
                out.news().add(news(c, News.Kind.GOOD, "The " + b.name + " goes to the president's desk", null, b.ours ? 1.4 : 1.0, null));
            }
            default -> { }
        }
    }

    Party sponsorParty(HillState.Bill b, Context c) { return b.ours ? c.president() : b.author != null ? b.author : c.president(); }

    Member chairFor(HillState s, HillState.Bill b, int ch) {
        int k = committeeFor(b);
        for (Member m : s.members) if (m.role.equals("Chair") && m.chamber == ch && COMMITTEES[ch][k].equals(m.committee)) return m;
        return null;
    }

    int committeeFor(HillState.Bill b) {
        if (b.source.startsWith("pol:")) return AREA_COMMITTEE[policies.get(b.source.split(":")[1]).area()];
        Issue i = catalog.has(b.source) ? catalog.get(b.source).issue() : Issue.ECONOMY;
        return switch (i) {
            case ECONOMY, COST_OF_LIVING -> b.revenue || b.cost < 0 ? 0 : 1;
            case HEALTHCARE -> 2;
            case IMMIGRATION -> 7;
            case CRIME, DEMOCRACY, ABORTION -> 4;
            case CLIMATE_ENERGY -> 2;
            case CHINA -> 5;
            case AI_TECH -> 2;
            case EDUCATION -> 8;
        };
    }

    double chairOk(HillState s, Context c, HillState.Bill b, int ch) {
        Member chair = chairFor(s, b, ch);
        if (chair == null) return 0.9;
        return logistic(blocLogit(s, c, b, chair.bloc, false) + (chair.rel - 50) / 30 + (chair.party == sponsorParty(b, c) ? 1.5 : 0) + (support(b) > 65 ? 0.8 : 0));
    }

    /** Leaders do not call votes they expect to lose; the other party's leader will not bring up a bill most of the majority opposes. */
    private boolean hold(HillState s, Context c, HillState.Bill b, int ch, Outcome out) {
        Member lead = leaderOf(s, c, ch);
        Party sp = sponsorParty(b, c);
        int waited = s.week - b.stageWeek;
        double p = chance(s, c, b, ch, false);
        boolean friendly = lead == null || lead.party == sp;
        if (friendly) {
            if (p >= 0.75 || waited >= 12) { b.held = false; return false; }
            for (int i = 0; i < s.blocs.size(); i++) if (s.blocs.get(i).chamber == ch && s.blocs.get(i).party == sp) b.sweet[i] += 0.05;
        } else {
            double maj = 0, seats = 0;
            for (int i = 0; i < s.blocs.size(); i++) {
                Bloc bl = s.blocs.get(i);
                if (bl.chamber == ch && bl.party == lead.party) { maj += bl.seats * logistic(blocLogit(s, c, b, i, false)); seats += bl.seats; }
            }
            if (maj / Math.max(1, seats) >= 0.5 && p >= 0.4) { b.held = false; return false; }
            if (waited >= 14) { kill(s, c, b, "never gets a vote: " + lead.name + " won't bring it up", out); return true; }
        }
        if (!b.held) out.news().add(news(c, News.Kind.INFO, friendly ? "Leaders put off the " + HillState.CHAMBER[ch] + " vote on the " + b.name + " while they hunt for votes"
                : lead.name + " refuses to schedule a vote on the " + b.name, null, b.ours ? 1.0 : 0.5, b.ours ? null : "Congress"));
        b.held = true;
        return true;
    }

    Member leaderOf(HillState s, Context c, int ch) {
        Party maj = majority(s, c, ch);
        for (Member m : s.members) if (m.chamber == ch && m.party == maj && m.leader()) return m;
        return null;
    }

    private boolean vote(HillState s, Context c, HillState.Bill b, int ch, boolean override, Outcome out, DeterministicRandomManager rng) {
        double[] cnt = count(s, c, b, ch, override);
        int total = ch == H ? 435 : 100;
        int yes = (int) Math.round(clamp(cnt[0] + rng.gaussian("hill.vote") * cnt[1], 0, total)), no = total - yes;
        if (ch == H) { b.houseYes = yes; b.houseNo = no; } else { b.senYes = yes; b.senNo = no; }
        boolean pass = yes >= need(s, c, b, ch, override);
        String what = override ? "the veto override on the " + b.name : ch == S && !b.recon && !s.filibusterGone ? "cloture on the " + b.name : "the " + b.name;
        out.news().add(news(c, pass ? News.Kind.GOOD : News.Kind.BAD, "The " + HillState.CHAMBER[ch] + (pass ? " passes " : " rejects ") + what + ", " + yes + "-" + no, null,
                b.ours ? 1.6 : 0.9, b.ours ? null : "Congress"));
        return pass;
    }

    private void kill(HillState s, Context c, HillState.Bill b, String how, Outcome out) {
        b.stage = Stage.DEAD;
        b.stageWeek = s.week;
        if (b.recon && s.reconFy == fiscalYear(c.date())) s.reconFy = -1;
        out.news().add(news(c, News.Kind.BAD, "The " + b.name + " " + how, null, b.ours ? 1.4 : 0.6, b.ours ? null : "Congress"));
        s.record.add(c.date() + ": the " + b.name + " " + how);
        if (b.ours) { win(s, c.president(), -1.5); out.effects().add(new Effect.Performance(-0.3)); }
    }

    /** Votes needed (Resolute Desk's {@code Legis.need}): 218 in the House, 60 in the Senate unless reconciliation or the filibuster is gone; overrides two-thirds. */
    public int need(HillState s, Context c, HillState.Bill b, int ch, boolean override) {
        if (ch == H) return override ? 290 : 218;
        if (override) return 67;
        if (b.recon || s.filibusterGone) return b.ours || sponsorParty(b, c) == c.president() ? 50 : 51;
        return 60;
    }

    /** Resolute Desk's bloc vote: a spatial vote, party, the relationship, approval, the cost, public support and deals. */
    public double blocLogit(HillState s, Context c, HillState.Bill b, int bi, boolean override) {
        Bloc bl = s.blocs.get(bi);
        double[] pos = position(b, c.president());
        double be = pos[0], bc = pos[1];
        double spatial = 2 * (bl.econ * be + 0.8 * bl.cult * bc) - (be * be + 0.8 * bc * bc);
        Party sp = sponsorParty(b, c);
        boolean mine = bl.party == c.president(), bip = lean(b) == 0;
        double l = 0.3 + 2.2 * spatial + (bip ? 0.2 : 0);
        if (bl.party == sp) {
            double a = clamp(0.5 + spatial, 0, 1);
            l += bl.loyalty * (b.ours && mine ? 0.4 + 2.4 * a : 0.5 + 1.3 * a);
        } else l -= 0.9 * c.polarization() * (bip ? 0.7 : 1);
        if (b.ours && !mine) l -= midtermSeason(c) || c.termWeek() > 170 ? 0.65 : 0.35;
        if (b.ours) l -= c.lameDuck() * (mine ? 0.25 : 0.6);            // members look past a lame duck
        if (b.ours || mine) l += (bl.rel - 50) / 35;
        if (b.ours) l += pull(s, c, bl);
        l += mine ? (c.approval() - 45) / 18 : (c.approval() - 50) / 30;
        double cost = b.netCost();
        if (cost > 0) l -= bl.hawk * cost / 180;
        l += (support(b) - 55) / 40 * (bl.moderate ? 1.2 : 0.4);
        if (b.goneP) l += bl.moderate ? 0.25 : 0.1;
        if (override) l += mine ? -1.6 * bl.loyalty : 0.3;
        if (!b.ours && mine && b.vetoed) l -= 0.5;
        return 1.6 * l + 1.2 * b.sweet[bi];
    }

    /** Re-elected (Resolute Desk's {@code Relations.elected}): the mandate is renewed by the new margin. */
    public void reelected(HillState s, double popularMargin) {
        s.mandate = s.mandate0 = clamp(2 + 0.55 * popularMargin, 0.5, 15);
    }

    /** The president will not run again (Resolute Desk's {@code Relations.setback}): the party cools and the mandate is spent. */
    public void steppedAside(HillState s, Party president) {
        for (Bloc b : s.blocs) if (b.party == president) b.rel = Math.max(0, b.rel - 5);
        s.mandate = 0;
    }

    /** The mandate's and the honeymoon's pull on the president's bills (Resolute Desk's {@code Relations.pull}). */
    double pull(HillState s, Context c, Bloc bl) {
        double m = s.mandate / 15 * (1 - 0.6 * c.lameDuck()), h = c.honeymoon();
        if (bl.party == c.president()) return 0.9 * m + 0.25 * h;
        return bl.moderate ? 0.7 * m + 0.35 * h : 0.2 * m + 0.1 * h;
    }

    /** The expected yes votes and their spread in a chamber. */
    public double[] count(HillState s, Context c, HillState.Bill b, int ch, boolean override) {
        double e = 0, var = 0;
        for (int i = 0; i < s.blocs.size(); i++) {
            Bloc bl = s.blocs.get(i);
            if (bl.chamber != ch) continue;
            double p = logistic(blocLogit(s, c, b, i, override));
            e += bl.seats * p;
            double corr = bl.seats * 0.06 * (1 - Math.abs(2 * p - 1));
            var += bl.seats * p * (1 - p) + corr * corr;
        }
        return new double[]{e, Math.sqrt(var) + (ch == H ? 2 : 0.8)};
    }

    public double chance(HillState s, Context c, HillState.Bill b, int ch, boolean override) {
        double[] cnt = count(s, c, b, ch, override);
        return logistic(1.702 * (cnt[0] - need(s, c, b, ch, override) + 0.5) / cnt[1]);
    }

    /**
     * The whip's read on a bill the president might send (the old Congress screen's and the autopilot's question): the
     * president's own bill if it is already drafted or moving, otherwise a draft made and taken back, so nothing changes.
     * The chance is passing both chambers' floors.
     */
    public game.mandate.core.congress.WhipCount whip(HillState s, Context c, String source, double size, double scope, boolean recon) {
        HillState.Bill b = null;
        for (HillState.Bill x : s.bills) if (x.ours && x.source.equals(source) && (x.stage == Stage.DRAFT || x.stage.active())) b = x;
        int next = s.nextId;
        boolean temp = b == null;
        if (temp) {
            b = s.bill(draft(s, c, source));
            b.size = clamp(size, 0.5, 1.5);
            b.scope = clamp(scope, 0, 1);
            b.recon = recon && b.budgetEligible;
        }
        try {
            List<game.mandate.core.congress.WhipCount.BlocRead> house = new ArrayList<>(), senate = new ArrayList<>();
            for (int i = 0; i < s.blocs.size(); i++) {
                Bloc bl = s.blocs.get(i);
                double p = logistic(blocLogit(s, c, b, i, false));
                (bl.chamber == H ? house : senate).add(new game.mandate.core.congress.WhipCount.BlocRead(bl.name, bl.seats, bl.seats * p));
            }
            return new game.mandate.core.congress.WhipCount(house, senate, need(s, c, b, H, false), need(s, c, b, S, false),
                    chance(s, c, b, H, false) * chance(s, c, b, S, false));
        } finally {
            if (temp) { s.bills.remove(b); s.nextId = next; }
        }
    }

    /** What a bloc will do on a bill, as the whip reports it, and its yes probability. */
    public double blocYes(HillState s, Context c, String billId, int bi) { return logistic(blocLogit(s, c, need(s, billId), bi, false)); }

    public double chance(HillState s, Context c, String billId, int ch) { return chance(s, c, need(s, billId), ch, false); }

    public double[] count(HillState s, Context c, String billId, int ch) { return count(s, c, need(s, billId), ch, false); }

    public int need(HillState s, Context c, String billId, int ch) { return need(s, c, need(s, billId), ch, false); }

    /** The majority moves a bill of its own every seven weeks if fewer than two of its bills are moving. */
    private void congressBill(HillState s, Context c, Outcome out, DeterministicRandomManager rng) {
        long theirs = s.bills.stream().filter(b -> !b.ours && b.stage.active()).count();
        if (theirs >= 2) return;
        Party maj = majority(s, c, H);
        if (maj == c.president() && unified(c) && rng.uniform("hill.agenda") < 0.6) return;
        List<Bill> pool = new ArrayList<>();
        for (Bill b : catalog.all()) {
            if (b.sponsor().isPresent() && b.sponsor().get() != maj) continue;
            boolean busy = false;
            for (HillState.Bill x : s.bills) if (x.source.equals(b.id()) && (x.stage.active() || x.stage == Stage.LAW || x.stage == Stage.OVERRIDE)) busy = true;
            if (!busy && !lawAlready(s, b.id())) pool.add(b);
        }
        if (pool.isEmpty()) return;
        Bill b = pool.get(rng.nextInt("hill.agenda", pool.size()));
        String id = draft(s, c, b.id());
        HillState.Bill x = need(s, id);
        x.ours = false;
        x.author = b.sponsor().orElse(null);
        x.stage = Stage.COMMITTEE;
        x.stageWeek = s.week;
        x.introduced = s.week;
        out.news().add(news(c, News.Kind.INFO, "The " + (maj == Party.DEMOCRAT ? "Democratic" : "Republican") + " majority moves the " + x.name, null, 0.7, "Congress"));
    }

    private boolean lawAlready(HillState s, String source) { return s.enacted.contains(source); }

    /** A law made outside the Hill (the hundred days, an executive order): Congress will not take it up again. */
    public void noteLaw(HillState s, String source) { if (!s.enacted.contains(source)) s.enacted.add(source); }

    // ---- deals (Resolute Desk's Legis.deal) ---------------------------------------------------------------------------

    public enum Deal { CALL, PROJECT, APPOINTMENT, ENDORSE, THREAT, FAVOR }

    public Outcome deal(HillState s, Context c, String billId, String memberId, Deal kind, DeterministicRandomManager rng) {
        HillState.Bill b = need(s, billId);
        Member m = s.member(memberId);
        if (m == null) throw new IllegalArgumentException("No such member");
        if (!b.stage.active() || b.stage == Stage.DESK) throw new IllegalArgumentException("The bill is not before Congress");
        if (b.dealt.getOrDefault(m.id, -1) == s.week) throw new IllegalArgumentException(m.name + " has already made a deal on this bill this week");
        double hours = kind == Deal.FAVOR ? 0 : 1;
        if (s.hoursLeft() < hours) throw new IllegalArgumentException("No hours left on the Hill this week");
        boolean own = m.party == c.president();
        double boost;
        Outcome out = Outcome.empty();
        switch (kind) {
            case CALL -> { boost = 0.35 * (c.charisma() + c.establishment()) / 140; m.rel = clamp(m.rel + 4, 0, 100); }
            case PROJECT -> {
                boost = PRICES[0].equals(m.price) ? 1.4 : 0.7;
                out.effects().add(new Effect.PolicyStep("project-" + s.nextId++, "A project for " + m.name, FiscalClass.PURCHASES, 0.01, new double[]{1, 1, 0.5, 0}, 13, 1,
                        new double[Channel.COUNT]));
                for (Bloc bl : s.blocs) if (bl.chamber == m.chamber && bl.hawk >= 0.6) bl.rel = clamp(bl.rel - 1.5, 0, 100);
                if (rng.uniform("hill.deal") < 0.15)
                    out.news().add(news(c, News.Kind.BAD, "Critics call the White House's deal with " + m.name + " pork", null, 0.8, null));
            }
            case APPOINTMENT -> {
                if (s.appointments <= 0) throw new IllegalArgumentException("No appointments left to give");
                s.appointments--;
                boost = PRICES[1].equals(m.price) ? 1.5 : 0.8;
            }
            case ENDORSE -> {
                boost = own ? (PRICES[2].equals(m.price) ? 1.5 : 0.9) : 0.2;
                if (own && m.state != null) {
                    Favor f = new Favor();
                    f.member = m.id; f.why = "an endorsement"; f.promise = true; f.state = m.state; f.week = s.week;
                    f.due = s.week + Math.max(8, (int) ChronoUnit.WEEKS.between(c.date(), c.electionDay()));
                    s.favors.add(f);
                }
            }
            case THREAT -> {
                boost = own ? 0.8 : -0.3;
                m.rel = clamp(m.rel - 15, 0, 100);
                s.blocs.get(m.bloc).rel = clamp(s.blocs.get(m.bloc).rel - 3, 0, 100);
                if (rng.uniform("hill.deal") < 0.3) out.news().add(news(c, News.Kind.BAD, "The White House threatens " + m.name + " over the " + b.name, null, 0.9, null));
            }
            case FAVOR -> {
                Favor f = null;
                for (Favor x : s.favors) if (x.member.equals(m.id) && !x.promise && !x.settled) f = x;
                if (f == null) throw new IllegalArgumentException(m.name + " owes you nothing");
                f.settled = true;
                m.owesFavor = false;
                boost = 1.8;
            }
            default -> throw new IllegalStateException();
        }
        boost *= 0.8 + 0.4 * c.staffQuality();
        double share = m.role.equals("Chair") ? 0.35 : m.role.equals("Swing vote") ? 0.22 : 0.3;
        b.sweet[m.bloc] += boost * share;
        if (kind != Deal.THREAT) m.rel = clamp(m.rel + (m.role.equals("Chair") ? 6 : m.leader() ? 5 : 0) * Math.max(0, boost) / 1.5, 0, 100);
        b.dealt.put(m.id, s.week);
        s.hoursUsed += hours;
        return out;
    }

    /** Going public: moderates warm a little, the issue rises, opposing leaders cool. */
    public void goPublic(HillState s, Context c, String billId) {
        HillState.Bill b = need(s, billId);
        if (b.goneP) throw new IllegalArgumentException("You have already taken it to the country");
        b.goneP = true;
        for (Member m : s.members) if (m.leader() && m.party != c.president()) m.rel = clamp(m.rel - 2, 0, 100);
    }

    /** Relations from outside the Hill: {@code who} 1 the president's party, -1 the other, 0 both; {@code chamber} 0, 1 or -1 for both. */
    public void relations(HillState s, Party president, int who, int chamber, double delta) {
        for (Bloc b : s.blocs) {
            if (chamber >= 0 && b.chamber != chamber) continue;
            if (who > 0 && b.party != president || who < 0 && b.party == president) continue;
            b.rel = clamp(b.rel + delta, 0, 100);
        }
    }

    /** A bill taken to the country from a speech. */
    public void publicPush(HillState s, String billId) {
        HillState.Bill b = s.bill(billId);
        if (b != null) b.goneP = true;
    }

    /** A private meeting with a leader: two hours. */
    public void meet(HillState s, Context c, String memberId) {
        Member m = s.member(memberId);
        if (m == null || !m.leader()) throw new IllegalArgumentException("Meet a leader");
        if (s.hoursLeft() < 2) throw new IllegalArgumentException("A meeting takes two hours on the Hill");
        s.hoursUsed += 2;
        m.rel = clamp(m.rel + 5 + c.establishment() / 30, 0, 100);
    }

    /** Asking the Senate to end the filibuster (three hours). */
    public Outcome nuclear(HillState s, Context c, DeterministicRandomManager rng) {
        if (s.filibusterGone) throw new IllegalArgumentException("The filibuster is already gone");
        int mine = c.president() == Party.DEMOCRAT ? c.senateDem() : 100 - c.senateDem();
        if (mine < 50) throw new IllegalArgumentException("It needs your party's majority in the Senate");
        if (s.hoursLeft() < 3) throw new IllegalArgumentException("It takes three hours on the Hill");
        s.hoursUsed += 3;
        double rel = 0, n = 0;
        for (Bloc b : s.blocs) if (b.chamber == S && b.party == c.president()) { rel += b.rel; n++; }
        double p = clamp(0.06 + 0.06 * (mine - 50) + (rel / Math.max(1, n) - 55) / 120 + (c.approval() - 50) / 100, 0.02, 0.6);
        Outcome out = Outcome.empty();
        if (rng.uniform("hill.nuclear") < p) {
            s.filibusterGone = true;
            for (Bloc b : s.blocs) if (b.chamber == S && b.party != c.president()) b.rel = clamp(b.rel - 15, 0, 100);
            out.news().add(news(c, News.Kind.INFO, "The Senate ends the filibuster", "A simple majority now passes bills; the minority calls it a power grab.", 2.0, null));
            s.record.add(c.date() + ": the Senate ended the filibuster");
        } else {
            for (Bloc b : s.blocs) if (b.chamber == S && b.party == c.president() && b.moderate) b.rel = clamp(b.rel - 8, 0, 100);
            out.effects().add(new Effect.Performance(-0.5));
            out.news().add(news(c, News.Kind.BAD, "Moderates refuse to end the filibuster", null, 1.2, null));
        }
        return out;
    }

    /** Answering a member's request: yes earns a favour (a visit becomes a promise); no costs the relationship. */
    public Outcome answer(HillState s, Context c, boolean yes) {
        if (s.askMember == null) throw new IllegalArgumentException("Nobody is asking");
        Member m = s.member(s.askMember);
        Outcome out = Outcome.empty();
        if (m != null) {
            if (!yes) m.rel = clamp(m.rel - 6, 0, 100);
            else if (s.askKind.equals("visit")) {
                Favor f = new Favor();
                f.member = m.id; f.why = "a visit"; f.promise = true; f.state = m.state; f.week = s.week; f.due = s.week + 8;
                s.favors.add(f);
                earn(s, m, "a visit promised");
            } else if (s.askKind.equals("project")) {
                earn(s, m, "a project");
                out.effects().add(new Effect.PolicyStep("project-" + s.nextId++, "A project for " + m.name, FiscalClass.PURCHASES, 0.005, new double[]{1, 1, 0.5, 0}, 13, 1,
                        new double[Channel.COUNT]));
                for (Bloc bl : s.blocs) if (bl.hawk >= 0.6) bl.rel = clamp(bl.rel - 1, 0, 100);
            } else {
                if (s.appointments <= 0) throw new IllegalArgumentException("No appointments left to give");
                s.appointments--;
                earn(s, m, "an appointment");
            }
        }
        s.askMember = null;
        s.askKind = null;
        s.pending.removeIf(p -> p.kind().equals("ask"));
        return out;
    }

    // ---- the desk ------------------------------------------------------------------------------------------------------

    public enum Desk { SIGN, VETO, UNSIGNED }

    public Outcome desk(HillState s, Context c, String billId, Desk choice, DeterministicRandomManager rng) {
        HillState.Bill b = need(s, billId);
        if (b.stage != Stage.DESK) throw new IllegalArgumentException("It is not on your desk");
        s.pending.removeIf(p -> p.kind().equals("desk") && p.subject().equals(billId));
        Outcome out = Outcome.empty();
        if (choice == Desk.VETO) {
            b.vetoed = true;
            s.vetoes++;
            if (b.ours || sponsorParty(b, c) == c.president()) { out.effects().add(new Effect.Performance(-1.5)); win(s, c.president(), -6); }
            else for (Bloc bl : s.blocs) if (bl.party != c.president()) bl.rel = clamp(bl.rel - 4, 0, 100);
            out.news().add(news(c, News.Kind.INFO, "The president vetoes the " + b.name, null, 1.6, null));
            s.record.add(c.date() + ": vetoed the " + b.name);
            double ph = chance(s, c, b, H, true), ps = chance(s, c, b, S, true);
            if (ph * ps > 0.05 || b.houseYes >= 280 && b.senYes >= 64) {
                boolean house = vote(s, c, b, H, true, out, rng), senate = house && vote(s, c, b, S, true, out, rng);
                if (house && senate) {
                    b.stage = Stage.OVERRIDE;
                    b.stageWeek = s.week;
                    s.overrides++;
                    out.effects().add(new Effect.Performance(-1));
                    win(s, c.president(), -3);
                    enact(s, c, b, false, out);
                    return out;
                }
            }
            b.stage = Stage.DEAD;
            b.stageWeek = s.week;
            return out;
        }
        b.stage = Stage.LAW;
        b.stageWeek = s.week;
        enact(s, c, b, choice == Desk.SIGN, out);
        return out;
    }

    /** A bill becomes law: the policy table makes its steps, scaled by how much of it survived. */
    private void enact(HillState s, Context c, HillState.Bill b, boolean signed, Outcome out) {
        s.laws++;
        double strength = clamp(b.strength(), 0.4, 1.5);
        out.effects().add(new Effect.LawPassed(b.source, b.name, strength, strength, PAY_FOR[b.payFor], signed, signed ? 0.3 : 0));
        double sup = support(b);
        if (signed) {
            out.effects().add(new Effect.Performance(b.ours ? 0.6 + sup * 0.02 : 0.2));
            if (b.ours) win(s, c.president(), 2);
            else { Member chair = chairFor(s, b, H); if (chair != null) earn(s, chair, "your signature on their bill"); }
        }
        out.news().add(news(c, News.Kind.GOOD, (signed ? "The president signs the " : "The " + b.name + " becomes law") + (signed ? b.name + " into law" : ""), null, b.ours ? 2.0 : 1.2, null));
        s.record.add(c.date() + ": " + (signed ? "signed the " + b.name : "the " + b.name + " became law without a signature"));
        if (!s.enacted.contains(b.source)) s.enacted.add(b.source);
    }

    // =========================================================================================
    //  The budget (Resolute Desk's Budget)
    // =========================================================================================

    static int fiscalYear(LocalDate d) { return d.getMonthValue() >= 10 ? d.getYear() + 1 : d.getYear(); }

    /** The debt subject to the limit, $ trillions. */
    public static double debtSubject(Context c) { return c.debtPct() / 100 * c.nominalGdpTn() * 1.25; }

    public boolean unified(Context c) {
        boolean house = (c.houseDem() >= 218) == (c.president() == Party.DEMOCRAT);
        boolean senate = c.president() == Party.DEMOCRAT ? c.senateDem() >= 50 : c.senateDem() <= 50;
        return house && senate;
    }

    /** The president's budget request: defense and other agencies (%), taxes and entitlements (% of GDP). */
    public void request(HillState s, double defense, double other, double tax, double entitlements) {
        if (s.requestSent) throw new IllegalArgumentException("This year's request has gone to Congress");
        s.reqDefense = clamp(defense, -15, 15); s.reqOther = clamp(other, -25, 25); s.reqTax = clamp(tax, -1.5, 1.5); s.reqEntitlements = clamp(entitlements, -1, 1);
    }

    /** The request's change to the deficit, % of GDP. */
    public static double requestDeficit(HillState s) { return s.reqDefense * 0.03 + s.reqOther * 0.033 - s.reqTax + s.reqEntitlements; }

    public Outcome sendBudget(HillState s, Context c) {
        if (s.requestSent) throw new IllegalArgumentException("This year's request has gone to Congress");
        s.requestSent = true;
        s.pending.removeIf(p -> p.kind().equals("budget"));
        double d = requestDeficit(s);
        for (Bloc b : s.blocs) if (b.hawk >= 0.6) b.rel = clamp(b.rel + (d > 0.3 ? -3 : d < -0.3 ? 2 : 0), 0, 100);
        Outcome out = Outcome.empty();
        out.news().add(news(c, News.Kind.INFO, "The White House sends its budget to Congress",
                Fmt.format("Defense %+.0f%%, other agencies %+.0f%%, taxes %+.1f%% of GDP; the deficit %+.2f%% of GDP.", s.reqDefense, s.reqOther, s.reqTax, d), 1.0, null));
        return out;
    }

    private void budget(HillState s, Context c, Outcome out, DeterministicRandomManager rng) {
        LocalDate d = c.date();
        int fy = fiscalYear(d.plusMonths(8));
        // the request: due in February, or June in the first year (the hundred days hold the Hill, so Resolute Desk's
        // April is late May here); sent for you a month later
        if (!s.requestSent && s.pending.stream().noneMatch(p -> p.kind().equals("budget"))) {
            LocalDate due = c.termWeek() < 20 ? LocalDate.of(d.getYear(), 6, 1) : LocalDate.of(d.getYear(), 2, 3);
            if (!d.isBefore(due.minusWeeks(6)) && d.getMonthValue() < 6) {
                if (s.reqDefense == 0 && s.reqOther == 0 && s.reqTax == 0 && s.reqEntitlements == 0) {
                    if (c.president() == Party.REPUBLICAN) { s.reqDefense = 4; s.reqOther = -6; s.reqTax = -0.3; }
                    else { s.reqDefense = 1; s.reqOther = 4; s.reqTax = 0.3; }
                }
                s.pending.add(new Pending("budget", "the request", s.week + (int) Math.max(1, ChronoUnit.WEEKS.between(d, due.plusWeeks(4)))));
            }
        }
        // appropriations move from May to September
        if (d.getMonthValue() >= 5 && d.getMonthValue() <= 9) {
            boolean divided = !unified(c);
            for (int i = 0; i < 12; i++) if (s.approps[i] < 3 || s.approps[i] == 4)
                if (rng.uniform("hill.approps") < (divided ? 0.05 : 0.09) * (s.approps[i] == 2 ? 1.3 : 1)) s.approps[i] = s.approps[i] == 4 ? 1 : s.approps[i] + 1;
        }
        // the deadline: the last week of September
        if (d.getMonthValue() == 9 && d.getDayOfMonth() >= 22 && d.getDayOfMonth() <= 28 && s.budgetFy != fiscalYear(d.plusWeeks(2))) {
            s.budgetFy = fiscalYear(d.plusWeeks(2));
            int done = 0;
            for (int a : s.approps) if (a == 3) done++;
            if (done == 12) { out.effects().add(new Effect.Performance(0.5)); fundRequest(s, c, 1.0, out); s.requestSent = false; }
            else {
                int minority = c.president() == Party.DEMOCRAT ? 100 - c.senateDem() : c.senateDem();
                if (unified(c)) {
                    if (minority >= 41 && !s.filibusterGone && rng.uniform("hill.cr") < 0.12 + 0.2 * c.polarization() + Math.max(0, 48 - c.approval()) * 0.015) {
                        s.pending.add(new Pending("cr", "the minority blocks a stopgap", s.week + 1));
                        out.news().add(news(c, News.Kind.BAD, "The minority blocks a stopgap; a shutdown looms", null, 1.6, null));
                    } else { stopgap(s, 11); out.news().add(news(c, News.Kind.INFO, "Congress passes a stopgap to keep the government open", null, 0.8, "Congress")); }
                } else {
                    s.pending.add(new Pending("cr", "the majority's terms for a stopgap", s.week + 1));
                    out.news().add(news(c, News.Kind.BAD, "The government runs out of money at the end of the week", "The majority offers a stopgap on its terms.", 1.6, null));
                }
            }
        }
        // a stopgap runs out: an omnibus, or another deadline
        if (s.crUntil >= 0 && s.week >= s.crUntil && !s.shutdown) {
            s.crUntil = -1;
            if (unified(c) || rng.uniform("hill.omnibus") < 0.7) {
                for (int i = 0; i < 12; i++) s.approps[i] = 3;
                fundRequest(s, c, unified(c) ? 0.5 : 0.2, out);
                s.requestSent = false;
                out.news().add(news(c, News.Kind.INFO, "Congress passes a year's spending in one omnibus bill", null, 1.0, null));
            } else {
                s.pending.add(new Pending("cr", "another deadline", s.week + 1));
                out.news().add(news(c, News.Kind.BAD, "Another funding deadline; a shutdown looms", null, 1.4, null));
            }
        }
        if (d.getMonthValue() == 1 && d.getDayOfMonth() <= 7) s.requestSent = false;
        if (d.getMonthValue() == 2 && d.getDayOfMonth() <= 7 && s.crUntil < 0) for (int i = 0; i < 12; i++) if (s.approps[i] == 3) s.approps[i] = 0;
        // a shutdown, week by week
        if (s.shutdown) {
            s.shutdownWeeks++;
            out.effects().add(new Effect.EconomyShock(-0.1, 0, 1));
            out.effects().add(new Effect.Performance(-0.5 * s.blame));
            if (s.pending.stream().noneMatch(p -> p.kind().equals("shutdown"))) s.pending.add(new Pending("shutdown", "hold or deal", s.week + 1));
        }
        // the debt limit
        double subject = debtSubject(c);
        if (s.xWeek < 0 && subject >= s.debtLimit) {
            s.xWeek = s.week + 22;
            out.news().add(news(c, News.Kind.BAD, "The government hits the debt limit", "The Treasury's extraordinary measures last about five months.", 1.6, null));
        }
        if (s.xWeek >= 0) {
            int left = s.xWeek - s.week;
            if ((left == 8 || left == 3) && s.pending.stream().noneMatch(p -> p.kind().equals("debt"))) s.pending.add(new Pending("debt", left + " weeks to the X-date", s.week + 1));
            if (left == 1 && s.pending.stream().noneMatch(p -> p.kind().equals("debt1"))) s.pending.add(new Pending("debt1", "the last week", s.week + 1));
            if (left <= 0) {
                s.xWeek = -1;
                raise(s, c, 1);
                out.effects().add(new Effect.FinancialShock(-4.5));
                out.effects().add(new Effect.Performance(-6));
                out.news().add(news(c, News.Kind.BAD, "The United States misses payments for the first time", "Markets plunge as the Treasury prioritizes payments; Congress raises the limit in a panic.", 3.0, null));
                s.record.add(c.date() + ": the Treasury missed payments at the debt limit");
            }
        }
    }

    private static void stopgap(HillState s, int weeks) {
        for (int i = 0; i < 12; i++) if (s.approps[i] != 3) s.approps[i] = 4;
        s.crUntil = s.week + weeks;
    }

    private void raise(HillState s, Context c, double years) {
        s.debtLimit = Math.round((debtSubject(c) + years * Math.max(0.8, c.deficitPct() / 100 * c.nominalGdpTn() * 1.25)) * 10) / 10.0;
    }

    /** The request becomes law, in full or in part: its spending and tax changes as programs. */
    private void fundRequest(HillState s, Context c, double share, Outcome out) {
        String fy = "budget-FY" + fiscalYear(c.date().plusWeeks(2));
        addProgram(out, fy + "-defense", "Defense spending in the budget", FiscalClass.PURCHASES, s.reqDefense * 0.03 * share);
        addProgram(out, fy + "-agencies", "Agencies' spending in the budget", FiscalClass.PURCHASES, s.reqOther * 0.033 * share);
        addProgram(out, fy + "-taxes", "Tax changes in the budget", FiscalClass.TAX_MIDDLE, -s.reqTax * share);
        addProgram(out, fy + "-entitlements", "Entitlement changes in the budget", FiscalClass.TRANSFERS, s.reqEntitlements * share);
    }

    private static void addProgram(Outcome out, String id, String label, FiscalClass cls, double pct) {
        if (Math.abs(pct) < 1e-6) return;
        out.effects().add(new Effect.PolicyStep(id, label, cls, pct, new double[]{1}, 0, 1, new double[Channel.COUNT]));
    }

    public enum Choice { ACCEPT, CLEAN, REFUSE, HOLD, DEAL, CUTS, BRINK, FOURTEENTH, COMPLY, NEGOTIATE, PRIVILEGE }

    /** The president's answer to a pending budget, shutdown, debt-limit or subpoena decision. */
    public Outcome choose(HillState s, Context c, String kind, Choice choice, DeterministicRandomManager rng) {
        Pending p = null;
        for (Pending x : s.pending) if (x.kind().equals(kind)) p = x;
        if (p == null) throw new IllegalArgumentException("Nothing is waiting for that decision");
        s.pending.remove(p);
        Outcome out = Outcome.empty();
        boolean divided = !unified(c);
        switch (kind) {
            case "cr" -> {
                boolean minorityBlock = p.subject().startsWith("the minority");
                if (choice == Choice.ACCEPT) {
                    stopgap(s, 11);
                    addProgram(out, "cr-" + s.week, "The stopgap's terms", FiscalClass.PURCHASES, minorityBlock ? 0.04 : -0.05);
                    for (Bloc b : s.blocs) if (b.party == c.president() && !b.moderate) b.rel = clamp(b.rel - 3, 0, 100);
                    out.news().add(news(c, News.Kind.INFO, "The president accepts a stopgap on the other side's terms", null, 1.0, null));
                } else if (choice == Choice.CLEAN && rng.uniform("hill.cr") < (minorityBlock ? 0.45 : 0.55) + (c.approval() - 45) / 40) {
                    stopgap(s, 11);
                    out.effects().add(new Effect.Performance(0.6));
                    win(s, c.president(), 1);
                    out.news().add(news(c, News.Kind.GOOD, "Congress passes a clean stopgap after the president holds firm", null, 1.2, null));
                } else shutdownStarts(s, c, minorityBlock ? (divided ? 0.5 : 0.42) : (divided ? 0.6 : 0.5), out);
            }
            case "shutdown" -> {
                if (choice == Choice.HOLD) {
                    if (rng.uniform("hill.shutdown") < 0.12 + 0.06 * s.shutdownWeeks + (0.5 - s.blame) * 0.6) {
                        reopen(s, c, out, "The other side caves; the government reopens");
                        out.effects().add(new Effect.Performance(1.2));
                        win(s, c.president(), 3);
                    } else s.blame = clamp(s.blame + (c.approval() < 45 ? 0.03 : -0.02), 0.2, 0.8);
                } else {
                    win(s, c.president(), -3);
                    addProgram(out, "shutdown-deal-" + s.week, "The deal that ended the shutdown", FiscalClass.PURCHASES, -0.05);
                    reopen(s, c, out, "A deal ends the shutdown");
                }
            }
            case "debt" -> {
                if (choice == Choice.CUTS) {
                    raise(s, c, 1.5);
                    s.xWeek = -1;
                    addProgram(out, "debt-cuts-" + s.week, "Spending cuts traded for the debt limit", FiscalClass.PURCHASES, -0.15);
                    win(s, c.president(), -2);
                    out.news().add(news(c, News.Kind.INFO, "The debt limit is raised in exchange for spending cuts", null, 1.4, null));
                } else {
                    boolean clean = choice == Choice.CLEAN;
                    double p0 = clean ? (divided ? 0.35 : 0.85) : (divided ? 0.45 : 0.7);
                    if (rng.uniform("hill.debt") < p0) {
                        raise(s, c, clean ? 2 : 1.5);
                        s.xWeek = -1;
                        if (!clean) { out.effects().add(new Effect.Performance(1)); win(s, c.president(), 2); }
                        out.news().add(news(c, News.Kind.GOOD, "Congress raises the debt limit" + (clean ? " with no conditions" : " at the brink"), null, 1.4, null));
                    } else {
                        out.effects().add(new Effect.FinancialShock(clean ? -0.6 : -1.2));
                        out.news().add(news(c, News.Kind.BAD, "Talks on the debt limit collapse; markets slide", null, 1.4, null));
                    }
                }
            }
            case "debt1" -> {
                if (choice == Choice.DEAL) {
                    raise(s, c, 1);
                    s.xWeek = -1;
                    addProgram(out, "debt-deal-" + s.week, "The last-minute debt-limit deal", FiscalClass.PURCHASES, -0.2);
                    out.effects().add(new Effect.Performance(-0.8));
                    win(s, c.president(), -4);
                    out.news().add(news(c, News.Kind.INFO, "A last-minute deal averts default", null, 1.6, null));
                } else if (choice == Choice.FOURTEENTH) {
                    raise(s, c, 1.5);
                    s.xWeek = -1;
                    out.effects().add(new Effect.FinancialShock(-1.6));
                    out.effects().add(new Effect.Performance(-1.5));
                    out.effects().add(new Effect.Scandal(4));
                    out.news().add(news(c, News.Kind.BAD, "The president invokes the 14th Amendment to keep paying the debt", "Lawsuits follow at once; markets are rattled.", 2.2, null));
                }
            }
            case "subpoena" -> subpoena(s, c, p.subject(), choice, out, rng);
            default -> throw new IllegalArgumentException("Unknown decision " + kind);
        }
        return out;
    }

    private void shutdownStarts(HillState s, Context c, double blame, Outcome out) {
        s.shutdown = true;
        s.blame = blame;
        s.shutdownWeeks = 0;
        s.shutdowns++;
        s.pending.add(new Pending("shutdown", "hold or deal", s.week + 1));
        out.news().add(news(c, News.Kind.BAD, "The government shuts down", Fmt.format("Polls put %.0f%% of the blame on the White House.", blame * 100), 2.4, null));
        s.record.add(c.date() + ": the government shut down");
    }

    private void reopen(HillState s, Context c, Outcome out, String head) {
        s.shutdown = false;
        stopgap(s, 10);
        s.pending.removeIf(p -> p.kind().equals("shutdown"));
        out.news().add(news(c, News.Kind.GOOD, head, Fmt.format("It was shut for %d weeks.", s.shutdownWeeks), 1.8, null));
    }

    // =========================================================================================
    //  Elections: the midterms, race by race
    // =========================================================================================

    /** The national House margin for the president's party (Resolute Desk's {@code Midterms.national}), without the noise. */
    public double national(HillState s, Context c) {
        double effort = 0;
        for (double v : c.campaign().values()) effort += v;
        return 0.5 * (c.approval() - 50) - 2.5 + 0.4 * (c.growth() - 2) - 0.3 * (c.unemployment() - c.unemploymentRef()) + Math.min(1.5, c.warChest() / 40)
                + Math.min(1, effort * 0.05) + (c.politicalQuality() - 0.55) * 1.5 + ((c.wingBase() + c.wingOther()) / 2 - 55) * 0.04;
    }

    /** The coming election's Senate races, with polls, from Labor Day of an election year. */
    private void elections(HillState s, Context c, Outcome out, DeterministicRandomManager rng) {
        LocalDate eday = c.electionDay();
        if (eday == null) return;
        long days = ChronoUnit.DAYS.between(c.date(), eday);
        if (days <= 100 && days > 0 && s.raceYear != eday.getYear()) openRaces(s, c, out, rng);
        if (s.raceYear == eday.getYear()) {
            double nat = national(s, c) * (c.president() == Party.DEMOCRAT ? 1 : -1);
            s.lastNational = national(s, c);
            for (Race r : s.races) if (r.result == null)
                r.poll = c.stateLean().getOrDefault(r.state, 0.0) + nat + (r.holder == Party.DEMOCRAT ? 3 : -3) * (r.open ? 0.3 : 1)
                        + c.campaign().getOrDefault(r.state, 0.0) * 1.2 * (c.president() == Party.DEMOCRAT ? 1 : -1);
        }
    }

    /** The coming election's Senate races: the class that is up, the candidates, open seats. */
    private void openRaces(HillState s, Context c, Outcome out, DeterministicRandomManager rng) {
        LocalDate eday = c.electionDay();
        int cl = eday.getYear() % 6 == 2 ? 1 : eday.getYear() % 6 == 4 ? 2 : 3;   // 2030 class I, 2032 class II, 2034 class III
        s.raceYear = eday.getYear();
        s.races.clear();
        for (Seat x : s.senate) if (x.seatClass == cl) {
            Race r = new Race();
            r.state = x.state; r.seatClass = cl; r.holder = x.party;
            r.open = rng.uniform("hill.race") < 0.1;
            String incumbent = x.holder, challenger = nominee(x.party.opponent(), x.state, eday.getYear(), rng);
            String fresh = r.open ? nominee(x.party, x.state, eday.getYear(), rng) : incumbent;
            r.democrat = x.party == Party.DEMOCRAT ? fresh : challenger;
            r.republican = x.party == Party.REPUBLICAN ? fresh : challenger;
            s.races.add(r);
        }
        out.news().add(news(c, News.Kind.INFO, "The campaign for Congress begins", s.races.size() + " Senate seats and all 435 House seats are up.", 1.0, null));
    }

    /**
     * Holds the election: every Senate race and the House state by state, then seats the new Congress. {@code presidential}
     * is the presidential year (2032), when the national margin is the presidential race's.
     * @return the transitions (the new seat counts) and the news
     */
    public Outcome holdElection(HillState s, Context c, Double presidentialMargin, Map<String, String> leaders, DeterministicRandomManager rng) {
        Outcome out = Outcome.empty();
        if (s.raceYear != c.electionDay().getYear()) openRaces(s, c, out, rng);
        if (s.raceYear == c.electionDay().getYear()) {
            double nat = national(s, c) * (c.president() == Party.DEMOCRAT ? 1 : -1);
            for (Race r : s.races) if (r.result == null && r.poll == 0)
                r.poll = c.stateLean().getOrDefault(r.state, 0.0) + nat + (r.holder == Party.DEMOCRAT ? 3 : -3) * (r.open ? 0.3 : 1);
        }
        double sign = c.president() == Party.DEMOCRAT ? 1 : -1;
        // the president's party's national margin: the presidential race's in 2032, the midterm model's otherwise
        double natPres = presidentialMargin != null ? presidentialMargin * 0.8 : national(s, c) + 1.8 * rng.gaussian("hill.election");
        double natD = natPres * sign;
        // the Senate, race by race
        int gained = 0;
        for (Race r : s.races) {
            double m = r.poll - national(s, c) * sign + natD + rng.gaussian("hill.election") * (presidentialMargin != null ? 4 : 3.5);
            r.result = m;
            Party won = m >= 0 ? Party.DEMOCRAT : Party.REPUBLICAN;
            for (Seat x : s.senate) if (x.state.equals(r.state) && x.seatClass == r.seatClass) {
                if (x.party != won) gained += won == c.president() ? 1 : -1;
                x.party = won;
                x.holder = won == Party.DEMOCRAT ? r.democrat : r.republican;
                if (x.holder.startsWith("the ") && x.holder.contains(" nominee in ")) x.holder = game.mandate.core.people.People.office("Senator", x.state);
            }
        }
        int senateD = (int) s.senate.stream().filter(x -> x.party == Party.DEMOCRAT).count();
        // the House: Resolute Desk's total (222 Democrats at an even national vote, 3.8 seats a point), then state by
        // state, each delegation by the state's lean, its incumbents' edge and its own swing
        int houseD = (int) clamp(Math.round(222 + 3.8 * natD + 5 * rng.gaussian("hill.house")), 120, 340);
        Map<String, Double> local = new TreeMap<>();
        for (Map.Entry<String, Integer> e : s.houseSeats.entrySet()) {
            int n = e.getValue(), was = s.houseD.getOrDefault(e.getKey(), 0);
            local.put(e.getKey(), (n == 0 ? 0 : 4 * ((double) was / n - 0.5) * 0.9) + 2.5 * rng.gaussian("hill.house"));
        }
        int houseBefore = c.president() == Party.DEMOCRAT ? c.houseDem() : 435 - c.houseDem();
        allocateHouse(s, c.stateLean(), houseD, local);
        out.effects().add(new Effect.CongressElected(houseD, senateD));
        int houseAfter = c.president() == Party.DEMOCRAT ? houseD : 435 - houseD;
        if (presidentialMargin == null && natPres < -3) out.effects().add(new Effect.Performance(-1));
        out.news().add(news(c, houseAfter >= houseBefore ? News.Kind.GOOD : News.Kind.BAD,
                Fmt.format("Election results: the president's party %+d House seats, %+d Senate seats", houseAfter - houseBefore, gained),
                Fmt.format("The new House: %d Democrats, %d Republicans. The new Senate: %d Democrats, %d Republicans.", houseD, 435 - houseD, senateD, 100 - senateD), 2.6, null));
        // the new Congress is seated in January; its shape is known now
        Context next = new Context(c.date(), c.termWeek(), c.president(), c.approval(), c.charisma(), c.establishment(), c.staffQuality(), c.hillHours(), c.visited(),
                c.campaign(), c.warChest(), c.polarization(), houseD, senateD, c.growth(), c.unemployment(), c.unemploymentRef(), c.deficitPct(), c.debtPct(),
                c.nominalGdpTn(), c.stateLean(), c.electionDay(), c.scandal(), c.honeymoon(), c.politicalQuality(), c.counselQuality(), c.scandalPressure(), c.scandalTitle(),
                c.wingBase(), c.wingOther(), c.opposition(), c.lameDuck());
        s.congressNumber++;
        s.seatedWeek = s.week;
        seatBlocs(s, next, rng);
        seatMembers(s, next, leaders, rng, false);
        s.record.add(c.date() + ": the " + ordinal(s.congressNumber) + " Congress elected, " + split(s, next));
        // bills still moving die with the old Congress
        for (HillState.Bill b : s.bills) if (b.stage.active() && b.stage != Stage.DESK) { b.stage = Stage.DEAD; b.stageWeek = s.week; b.note = "Died with the old Congress."; }
        s.reconFy = -1;
        return out;
    }

    // =========================================================================================
    //  Investigations and impeachment (Resolute Desk's executive/Trouble)
    // =========================================================================================

    private void trouble(HillState s, Context c, Outcome out, DeterministicRandomManager rng) {
        boolean houseOther = majority(s, c, H) != c.president(), senateOther = majority(s, c, S) != c.president();
        if ((houseOther || senateOther) && s.week >= s.nextProbe) {
            Probe p = new Probe();
            p.chamber = houseOther ? H : S;
            p.subject = c.scandalTitle() != null ? lower(c.scandalTitle()) : c.scandal() > 1 ? "the administration's scandal" : PROBE_SUBJECTS[rng.nextInt("hill.probe", PROBE_SUBJECTS.length)];
            p.status = "open"; p.opened = s.week; p.weeks = 10; p.severity = 1 + c.scandal() * 0.3; p.subpoenaPending = true;
            s.probes.add(p);
            s.pending.add(new Pending("subpoena", String.valueOf(s.probes.size() - 1), s.week + 2));
            out.news().add(news(c, News.Kind.BAD, "The " + HillState.CHAMBER[p.chamber] + " opens an investigation into " + p.subject, "Subpoenas are expected within days.", 1.6, null));
            s.nextProbe = s.week + 10 + rng.nextInt("hill.probe", 9);
        }
        for (Probe p : s.probes) {
            if (p.status.equals("in court") && s.week - p.opened >= p.weeks) {
                boolean won = rng.uniform("hill.probe") < 0.35 + 0.3 * c.counselQuality();
                p.status = won ? "closed" : "open";
                if (!won) p.severity += 0.6 + 1.4 * rng.uniform("hill.probe");
                p.opened = s.week;
                p.weeks = 6;
                out.news().add(news(c, won ? News.Kind.GOOD : News.Kind.BAD, won ? "A court upholds executive privilege" : "A court orders the White House to comply with a subpoena", null, 1.2, null));
            }
            if (p.status.equals("open") && !p.subpoenaPending && s.week - p.opened >= p.weeks) {
                p.status = "reported";
                if (p.severity >= 5) p.impeachable = true;
                out.effects().add(new Effect.Scandal(Math.min(6, p.severity)));
                out.news().add(news(c, News.Kind.BAD, "The investigation into " + p.subject + " reports", p.impeachable ? "Its findings give the House grounds for impeachment." : "Its findings embarrass the White House.",
                        1.4 + p.severity * 0.1, null));
            }
        }
        // impeachment
        double pr = 0;
        for (Probe p : s.probes) pr += (p.contempt ? 2 : 0) + (p.impeachable ? p.severity : 0);
        pr += c.scandalPressure();
        if ((s.impeachment.equals("none") || s.impeachment.equals("acquitted")) && houseOther && pr >= 9 && rng.uniform("hill.impeach") < 0.06 + (pr - 9) * 0.01) {
            s.impeachment = "inquiry";
            s.impeachWeek = s.week + 8 + rng.nextInt("hill.impeach", 6);
            out.news().add(news(c, News.Kind.BAD, "The House opens an impeachment inquiry", null, 3.0, null));
            s.record.add(c.date() + ": the House opened an impeachment inquiry");
        } else if (s.impeachment.equals("inquiry") && s.week >= s.impeachWeek) {
            int yes = 0;
            for (Bloc b : s.blocs) if (b.chamber == H) {
                double p = b.party == c.president() ? logistic(-4.5 + 0.18 * pr + (40 - c.approval()) * 0.05) : logistic(-1.2 + 0.28 * pr - (b.moderate ? 0.8 : 0));
                for (int i = 0; i < b.seats; i++) if (rng.uniform("hill.impeach") < p) yes++;
            }
            s.impeachYes = yes;
            if (yes >= 218) {
                s.impeachment = "trial";
                s.impeachWeek = s.week + 3 + rng.nextInt("hill.impeach", 3);
                out.effects().add(new Effect.Scandal(3));
                out.news().add(news(c, News.Kind.BAD, "The House impeaches the president, " + yes + "-" + (435 - yes), "The Senate trial begins within weeks.", 3.5, null));
                s.record.add(c.date() + ": the House impeached the president, " + yes + "-" + (435 - yes));
            } else {
                s.impeachment = "none";
                out.news().add(news(c, News.Kind.GOOD, "The House rejects impeachment, " + yes + "-" + (435 - yes), null, 2.4, null));
            }
        } else if (s.impeachment.equals("trial") && s.week >= s.impeachWeek) {
            int yes = 0;
            for (Bloc b : s.blocs) if (b.chamber == S) {
                double p = b.party == c.president() ? logistic(-4.2 + 0.16 * pr + (40 - c.approval()) * 0.06) : logistic(0.2 + 0.2 * pr - (b.moderate ? 0.4 : 0));
                for (int i = 0; i < b.seats; i++) if (rng.uniform("hill.impeach") < p) yes++;
            }
            s.convictYes = yes;
            if (yes >= 67) {
                s.impeachment = "removed";
                out.news().add(news(c, News.Kind.BAD, "The Senate convicts the president, " + yes + "-" + (100 - yes), "The president is removed from office.", 5.0, null));
                s.record.add(c.date() + ": the Senate convicted and removed the president, " + yes + "-" + (100 - yes));
            } else {
                s.impeachment = "acquitted";
                out.effects().add(new Effect.Rally(2));
                out.news().add(news(c, News.Kind.GOOD, "The Senate acquits the president, " + yes + "-" + (100 - yes), null, 3.0, null));
                s.record.add(c.date() + ": the Senate acquitted the president, " + yes + "-" + (100 - yes));
            }
        }
    }

    static final String[] PROBE_SUBJECTS = {"the administration's contracts", "a cabinet secretary's travel", "the handling of classified documents",
            "pressure on an independent agency", "a donor's access to the White House", "the use of emergency powers"};

    private void subpoena(HillState s, Context c, String probe, Choice choice, Outcome out, DeterministicRandomManager rng) {
        Probe p = s.probes.get(Integer.parseInt(probe));
        p.subpoenaPending = false;
        switch (choice) {
            case COMPLY -> p.severity += -0.6 + 2.0 * rng.uniform("hill.probe");
            case NEGOTIATE -> { if (rng.uniform("hill.probe") >= 0.35 + 0.5 * c.counselQuality()) p.severity += -0.6 + 2.0 * rng.uniform("hill.probe"); }
            case PRIVILEGE -> { p.status = "in court"; p.opened = s.week; p.weeks = 14 + rng.nextInt("hill.probe", 10); }
            default -> {
                p.contempt = true;
                p.severity += 2;
                p.impeachable = true;
                out.news().add(news(c, News.Kind.BAD, "The " + HillState.CHAMBER[p.chamber] + " votes to hold White House officials in contempt", null, 1.8, null));
            }
        }
    }

    // =========================================================================================
    //  The president's orders, and the staff's
    // =========================================================================================

    /** Carries out an order from the Roosevelt Room. Throws IllegalArgumentException if it cannot be done. */
    public Outcome apply(HillState s, Context c, HillOrder o, DeterministicRandomManager rng) {
        return switch (o) {
            case HillOrder.Draft x -> { draft(s, c, x.source()); yield Outcome.empty(); }
            case HillOrder.Edit x -> { edit(s, x.bill(), x.size(), x.scope(), x.payFor(), x.reconciliation(), x.firstChamber()); yield Outcome.empty(); }
            case HillOrder.Send x -> send(s, c, x.bill());
            case HillOrder.Withdraw x -> { withdraw(s, x.bill()); yield Outcome.empty(); }
            case HillOrder.Deal x -> deal(s, c, x.bill(), x.member(), x.kind(), rng);
            case HillOrder.GoPublic x -> { goPublic(s, c, x.bill()); yield Outcome.empty(); }
            case HillOrder.Meet x -> { meet(s, c, x.member()); yield Outcome.empty(); }
            case HillOrder.Desk x -> desk(s, c, x.bill(), x.choice(), rng);
            case HillOrder.EndFilibuster x -> nuclear(s, c, rng);
            case HillOrder.Answer x -> answer(s, c, x.yes());
            case HillOrder.Budget x -> { request(s, x.defense(), x.other(), x.tax(), x.entitlements()); yield Outcome.empty(); }
            case HillOrder.SendBudget x -> sendBudget(s, c);
            case HillOrder.Choose x -> choose(s, c, x.kind(), x.choice(), rng);
            case HillOrder.Delegate x -> { s.delegated = x.on(); yield Outcome.empty(); }
        };
    }

    /** The newest draft's id (after a Draft order). */
    public String lastDraft(HillState s) {
        for (int i = s.bills.size() - 1; i >= 0; i--) if (s.bills.get(i).ours && s.bills.get(i).stage == Stage.DRAFT) return s.bills.get(i).id;
        return null;
    }

    /**
     * The staff on the Hill (Resolute Desk's {@code autoWhip} and the delegated desk): for a bill stuck short of a majority,
     * call in favours, then spend up to two hours on the swing votes with the deal each wants, and take a popular bill to
     * the country; sign the president's bills and the other side's popular ones, veto the rest; send the budget; take the
     * safe road at deadlines.
     */
    public Outcome staff(HillState s, Context c, DeterministicRandomManager rng) {
        Outcome out = Outcome.empty();
        int made = 0;
        for (HillState.Bill b : new ArrayList<>(s.bills)) {
            if (!b.ours || !b.stage.active() || b.stage == Stage.DESK) continue;
            int ch = b.chamberNow();
            double p = chance(s, c, b, ch, false);
            if (p >= 0.6) continue;
            for (Member m : s.members) {
                if (m.chamber != ch || m.leader()) continue;
                if (p < 0.45 && m.owesFavor) { merge(out, tryDeal(s, c, b, m, Deal.FAVOR, rng)); continue; }
                if (made >= 2 || !m.role.equals("Swing vote")) continue;
                Deal kind = PRICES[0].equals(m.price) ? Deal.PROJECT : PRICES[1].equals(m.price) && s.appointments > 0 ? Deal.APPOINTMENT
                        : PRICES[2].equals(m.price) && m.party == c.president() ? Deal.ENDORSE : Deal.CALL;
                Outcome o = tryDeal(s, c, b, m, kind, rng);
                if (o == null) o = tryDeal(s, c, b, m, Deal.CALL, rng);
                if (o != null) { merge(out, o); made++; }
            }
            if (!b.goneP && support(b) > 55) goPublic(s, c, b.id);
        }
        for (Pending p : new ArrayList<>(s.pending)) {
            if (!s.pending.contains(p)) continue;     // settled by an earlier answer
            HillOrder o = recommend(s, c, p);
            if (o != null) merge(out, apply(s, c, o, rng));
        }
        return out;
    }

    /**
     * What the staff would do with a decision the Hill puts to the president (and do, when the Hill is delegated or the
     * president is watching): sign the president's bills, the president's party's and popular ones, veto the rest; send
     * the budget; take the safe road at deadlines; grant a member's request unless appointments run short.
     * @return the order, or null if there is nothing to do
     */
    public HillOrder recommend(HillState s, Context c, Pending p) {
        return switch (p.kind()) {
            case "desk" -> {
                HillState.Bill b = s.bill(p.subject());
                if (b == null) yield null;
                boolean sign = b.ours || sponsorParty(b, c) == c.president() || support(b) >= 55;
                yield new HillOrder.Desk(b.id, sign ? Desk.SIGN : Desk.VETO);
            }
            case "budget" -> new HillOrder.SendBudget();
            case "cr" -> new HillOrder.Choose("cr", c.approval() >= 50 ? Choice.CLEAN : Choice.ACCEPT);
            case "shutdown" -> new HillOrder.Choose("shutdown", s.blame > 0.55 || s.shutdownWeeks >= 3 ? Choice.DEAL : Choice.HOLD);
            case "debt" -> new HillOrder.Choose("debt", unified(c) ? Choice.CLEAN : Choice.CUTS);
            case "debt1" -> new HillOrder.Choose("debt1", Choice.DEAL);
            case "subpoena" -> new HillOrder.Choose("subpoena", Choice.NEGOTIATE);
            case "ask" -> s.askMember == null ? null : new HillOrder.Answer(!"appointment".equals(s.askKind) || s.appointments > 2);
            default -> null;
        };
    }

    private Outcome tryDeal(HillState s, Context c, HillState.Bill b, Member m, Deal kind, DeterministicRandomManager rng) {
        try { return deal(s, c, b.id, m.id, kind, rng); } catch (IllegalArgumentException e) { return null; }
    }

    private static void merge(Outcome into, Outcome from) {
        if (from == null) return;
        into.effects().addAll(from.effects());
        into.news().addAll(from.news());
    }

    // =========================================================================================
    //  Decisions left too long
    // =========================================================================================

    /** A decision past its week gets Resolute Desk's default: the request is sent, a bill becomes law unsigned, the stopgap is accepted. */
    private void overdue(HillState s, Context c, Outcome out, DeterministicRandomManager rng) {
        for (Pending p : new ArrayList<>(s.pending)) {
            if (s.week <= p.due()) continue;
            Outcome o = switch (p.kind()) {
                case "budget" -> sendBudget(s, c);
                case "desk" -> desk(s, c, p.subject(), Desk.UNSIGNED, rng);
                case "cr" -> choose(s, c, "cr", Choice.ACCEPT, rng);
                case "shutdown" -> choose(s, c, "shutdown", Choice.HOLD, rng);
                case "debt" -> choose(s, c, "debt", Choice.CLEAN, rng);
                case "debt1" -> choose(s, c, "debt1", Choice.DEAL, rng);
                case "subpoena" -> choose(s, c, "subpoena", Choice.NEGOTIATE, rng);
                case "ask" -> answer(s, c, false);
                default -> Outcome.empty();
            };
            out.effects().addAll(o.effects());
            out.news().addAll(o.news());
        }
    }

    // =========================================================================================
    //  Helpers
    // =========================================================================================

    private HillState.Bill need(HillState s, String id) {
        HillState.Bill b = s.bill(id);
        if (b == null) throw new IllegalArgumentException("No such bill");
        return b;
    }

    boolean midtermSeason(Context c) {
        return c.electionDay() != null && ChronoUnit.DAYS.between(c.date(), c.electionDay()) <= 100 && c.electionDay().getYear() % 4 == 2;
    }

    /** The names already serving (so a real person is never seated twice). */
    private static java.util.Set<String> taken(HillState s) {
        java.util.Set<String> t = new java.util.TreeSet<>();
        for (Member m : s.members) t.add(m.name);
        return t;
    }

    /**
     * A committee's leader for a new Congress: the real chair or ranking member while the record has one serving and not
     * already seated, a newcomer from 2030 (at the year's rate), else the office.
     */
    private String fill(String chamber, String committee, Party party, int year, java.util.Set<String> taken, DeterministicRandomManager rng) {
        if (!people.real()) return person(rng);
        var real = people.data().committeeLeader(chamber, committee, party, year);
        if (real.isPresent() && !taken.contains(real.get())) return real.get();
        if (people.newcomer(year, rng.uniform("hill.people"))) return person(rng);
        return "the " + (chamber.equals("H") ? "House " : "Senate ") + committee + " chair";
    }

    /** A party's Senate nominee: a fictional newcomer at the year's rate, else the office ("the Republican nominee in Ohio"). */
    private String nominee(Party party, String state, int year, DeterministicRandomManager rng) {
        if (!people.real()) return person(rng);
        if (people.newcomer(year, rng.uniform("hill.people"))) return person(rng);
        return "the " + (party == Party.DEMOCRAT ? "Democratic" : "Republican") + " nominee in " + game.mandate.core.people.People.stateName(state);
    }

    String person(DeterministicRandomManager rng) {
        List<String> first = rng.uniform("hill.names") < 0.45 ? names.get("first_f") : names.get("first_m");
        List<String> last = rng.uniform("hill.names") < 0.6 ? names.get("lasts") : names.get("more_lasts");
        return first.get(rng.nextInt("hill.names", first.size())) + " " + last.get(rng.nextInt("hill.names", last.size()));
    }

    private static News news(Context c, News.Kind kind, String head, String body, double weight, String section) {
        return new News(c.date(), kind, head, body, weight, section);
    }

    static String ordinal(int n) {
        int m = n % 100;
        String suf = m >= 11 && m <= 13 ? "th" : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
        return n + suf;
    }

    static String lower(String s) { return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1); }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    static double logistic(double x) { return 1 / (1 + Math.exp(-x)); }

    /** The standard normal distribution function (Abramowitz and Stegun 7.1.26 via erf). */
    static double phi(double z) {
        double t = 1 / (1 + 0.3275911 * Math.abs(z) / Math.sqrt(2));
        double y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-z * z / 2);
        return z >= 0 ? 0.5 * (1 + y) : 0.5 * (1 - y);
    }
}
