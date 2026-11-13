package game.mandate.core.hill;

import game.mandate.core.data.Json;
import game.mandate.core.state.Party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Congress as Resolute Desk plays it (its {@code congress} package): each chamber's blocs and their relationship
 * with the president, the named members (leaders, committee chairs, swing votes), the bills moving through, the
 * president's hours on the Hill, favours, promises and appointments, the budget (the request, the twelve
 * appropriations, stopgaps, shutdowns and the debt limit), every Senate seat by state and class and the House by state,
 * the midterm campaign, and the investigations an opposition chamber runs. Owned by {@link HillEngine}.
 */
public final class HillState {
    public static final int HOUSE = 0, SENATE = 1;
    public static final String[] CHAMBER = {"House", "Senate"};

    /** A bloc: a wing of one party in one chamber (Resolute Desk's twelve). */
    public static final class Bloc {
        String name;
        Party party;
        int chamber, seats;
        double econ, cult, loyalty, hawk, rel;
        boolean moderate;

        public String name() { return name; }
        public Party party() { return party; }
        public int chamber() { return chamber; }
        public int seats() { return seats; }
        public double econ() { return econ; }
        public double cult() { return cult; }
        public double loyalty() { return loyalty; }
        public double hawk() { return hawk; }
        public double relationship() { return rel; }
        public boolean moderate() { return moderate; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name); m.put("party", party.name()); m.put("chamber", chamber); m.put("seats", seats);
            m.put("v", nums(econ, cult, loyalty, hawk, rel)); m.put("moderate", moderate);
            return m;
        }

        static Bloc fromJson(Map<String, Object> m) {
            Bloc b = new Bloc();
            b.name = (String) m.get("name"); b.party = Party.valueOf((String) m.get("party")); b.chamber = Json.integer(m.get("chamber"));
            b.seats = Json.integer(m.get("seats"));
            double[] v = read(m.get("v"));
            b.econ = v[0]; b.cult = v[1]; b.loyalty = v[2]; b.hawk = v[3]; b.rel = v[4];
            b.moderate = (Boolean) m.get("moderate");
            return b;
        }
    }

    /** A named member: a leader, a committee chair or a swing vote. */
    public static final class Member {
        String id, name, role, committee, state, price;
        Party party;
        int chamber, bloc;
        double rel, econ, cult;
        boolean owesFavor;

        public String id() { return id; }
        public String name() { return name; }
        /** "Speaker", "Minority Leader", "Majority Leader", "Chair", "Swing vote". */
        public String role() { return role; }
        public String committee() { return committee; }
        public String state() { return state; }
        /** What a swing vote wants: a project, an appointment, an endorsement or a change to the bill. */
        public String price() { return price; }
        public Party party() { return party; }
        public int chamber() { return chamber; }
        public int bloc() { return bloc; }
        public double relationship() { return rel; }
        public boolean owesFavor() { return owesFavor; }
        public boolean leader() { return role.contains("Leader") || role.equals("Speaker"); }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("name", name); m.put("role", role); m.put("committee", committee); m.put("state", state); m.put("price", price);
            m.put("party", party.name()); m.put("chamber", chamber); m.put("bloc", bloc); m.put("v", nums(rel, econ, cult)); m.put("owes", owesFavor);
            return m;
        }

        static Member fromJson(Map<String, Object> m) {
            Member x = new Member();
            x.id = (String) m.get("id"); x.name = (String) m.get("name"); x.role = (String) m.get("role"); x.committee = (String) m.get("committee");
            x.state = (String) m.get("state"); x.price = (String) m.get("price"); x.party = Party.valueOf((String) m.get("party"));
            x.chamber = Json.integer(m.get("chamber")); x.bloc = Json.integer(m.get("bloc"));
            double[] v = read(m.get("v"));
            x.rel = v[0]; x.econ = v[1]; x.cult = v[2];
            x.owesFavor = (Boolean) m.get("owes");
            return x;
        }
    }

    /** Where a bill is (Resolute Desk's stages). */
    public enum Stage {
        DRAFT("Drafting"), COMMITTEE("In committee"), FLOOR("On the floor"), COMMITTEE2("In the second chamber's committee"),
        FLOOR2("On the second chamber's floor"), CONFERENCE("In conference"), DESK("On your desk"), LAW("Law"), DEAD("Dead"), OVERRIDE("Veto overridden");

        public final String label;

        Stage(String label) { this.label = label; }

        public boolean active() { return ordinal() >= COMMITTEE.ordinal() && ordinal() <= DESK.ordinal(); }
    }

    /** A bill in Congress: the president's, or one the majority moves itself. */
    public static final class Bill {
        String id, source, name, description;
        /** The president's own bill. */
        boolean ours;
        /** The party that wrote a bill Congress moves itself; null for the president's. */
        Party author;
        double econ, cult, support, cost;
        boolean budgetEligible, revenue;
        double size = 1, scope = 0.5;
        /** 0 borrow, 1 tax the wealthy, 2 cuts elsewhere. */
        int payFor;
        boolean recon;
        int first;
        Stage stage = Stage.DRAFT;
        int stageWeek, introduced;
        boolean bottled, held, goneP, vetoed;
        int houseYes, houseNo, senYes, senNo;
        final double[] sweet = new double[12];
        /** The week each member last made a deal on this bill (one a week). */
        final Map<String, Integer> dealt = new TreeMap<>();
        String note = "";

        public String id() { return id; }
        public String source() { return source; }
        public String name() { return name; }
        public String description() { return description; }
        public boolean ours() { return ours; }
        public Party author() { return author; }
        public double support() { return support; }
        /** $ billions a year at full size, before a pay-for. */
        public double cost() { return cost; }
        public boolean budgetEligible() { return budgetEligible; }
        public double size() { return size; }
        public double scope() { return scope; }
        public int payFor() { return payFor; }
        public boolean reconciliation() { return recon; }
        public int firstChamber() { return first; }
        public Stage stage() { return stage; }
        public int stageWeek() { return stageWeek; }
        public int introduced() { return introduced; }
        public boolean bottled() { return bottled; }
        public boolean held() { return held; }
        public boolean wentPublic() { return goneP; }
        public boolean vetoed() { return vetoed; }
        public int houseYes() { return houseYes; }
        public int houseNo() { return houseNo; }
        public int senateYes() { return senYes; }
        public int senateNo() { return senNo; }
        public String note() { return note; }
        /** The chamber the bill is in now (the first chamber until it passes there). */
        public int chamberNow() { return stage.ordinal() >= Stage.COMMITTEE2.ordinal() ? 1 - first : first; }
        /** Net cost a year, $ billions: smaller if paid for. */
        public double netCost() { return cost * size * (0.7 + 0.6 * scope) * (payFor == 0 ? 1 : 0.05); }
        /** How much of the bill survives into law: its size and scope. */
        public double strength() { return size * (0.7 + 0.6 * scope); }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("source", source); m.put("name", name); m.put("description", description); m.put("ours", ours);
            m.put("author", author == null ? null : author.name());
            m.put("v", nums(econ, cult, support, cost, size, scope));
            m.put("flags", List.of(budgetEligible, revenue, recon, bottled, held, goneP, vetoed));
            m.put("payFor", payFor); m.put("first", first); m.put("stage", stage.name()); m.put("stageWeek", stageWeek); m.put("introduced", introduced);
            m.put("votes", List.of(houseYes, houseNo, senYes, senNo));
            m.put("sweet", nums(sweet));
            m.put("dealt", new LinkedHashMap<String, Object>(dealt));
            m.put("note", note);
            return m;
        }

        static Bill fromJson(Map<String, Object> m) {
            Bill b = new Bill();
            b.id = (String) m.get("id"); b.source = (String) m.get("source"); b.name = (String) m.get("name"); b.description = (String) m.get("description");
            b.ours = (Boolean) m.get("ours"); b.author = m.get("author") == null ? null : Party.valueOf((String) m.get("author"));
            double[] v = read(m.get("v"));
            b.econ = v[0]; b.cult = v[1]; b.support = v[2]; b.cost = v[3]; b.size = v[4]; b.scope = v[5];
            List<Object> f = Json.array(m.get("flags"));
            b.budgetEligible = (Boolean) f.get(0); b.revenue = (Boolean) f.get(1); b.recon = (Boolean) f.get(2); b.bottled = (Boolean) f.get(3);
            b.held = (Boolean) f.get(4); b.goneP = (Boolean) f.get(5); b.vetoed = (Boolean) f.get(6);
            b.payFor = Json.integer(m.get("payFor")); b.first = Json.integer(m.get("first")); b.stage = Stage.valueOf((String) m.get("stage"));
            b.stageWeek = Json.integer(m.get("stageWeek")); b.introduced = Json.integer(m.get("introduced"));
            List<Object> vs = Json.array(m.get("votes"));
            b.houseYes = Json.integer(vs.get(0)); b.houseNo = Json.integer(vs.get(1)); b.senYes = Json.integer(vs.get(2)); b.senNo = Json.integer(vs.get(3));
            double[] sw = read(m.get("sweet"));
            System.arraycopy(sw, 0, b.sweet, 0, Math.min(sw.length, b.sweet.length));
            Json.object(m.get("dealt")).forEach((k, x) -> b.dealt.put(k, Json.integer(x)));
            b.note = (String) m.get("note");
            return b;
        }
    }

    /** A favour a member owes, or a promise the president made (Resolute Desk's {@code Favor}). */
    public static final class Favor {
        String member, why, state;
        int week, due;
        boolean promise, settled;

        public String member() { return member; }
        public String why() { return why; }
        public String state() { return state; }
        public int week() { return week; }
        public int due() { return due; }
        public boolean promise() { return promise; }
        public boolean settled() { return settled; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("member", member); m.put("why", why); m.put("state", state); m.put("week", week); m.put("due", due); m.put("promise", promise); m.put("settled", settled);
            return m;
        }

        static Favor fromJson(Map<String, Object> m) {
            Favor f = new Favor();
            f.member = (String) m.get("member"); f.why = (String) m.get("why"); f.state = (String) m.get("state"); f.week = Json.integer(m.get("week"));
            f.due = Json.integer(m.get("due")); f.promise = (Boolean) m.get("promise"); f.settled = (Boolean) m.get("settled");
            return f;
        }
    }

    /** One Senate seat: its state, its class (I up in 2030, II in 2032, III in 2034), its holder and the holder's party. */
    public static final class Seat {
        String state, holder;
        int seatClass;
        Party party;

        public String state() { return state; }
        public int seatClass() { return seatClass; }
        public String holder() { return holder; }
        public Party party() { return party; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("state", state); m.put("class", seatClass); m.put("holder", holder); m.put("party", party.name());
            return m;
        }

        static Seat fromJson(Map<String, Object> m) {
            Seat s = new Seat();
            s.state = (String) m.get("state"); s.seatClass = Json.integer(m.get("class")); s.holder = (String) m.get("holder"); s.party = Party.valueOf((String) m.get("party"));
            return s;
        }
    }

    /** A Senate race in the coming election: the seat, the two candidates, whether the incumbent runs, and the latest poll. */
    public static final class Race {
        String state, democrat, republican;
        int seatClass;
        Party holder;
        boolean open;
        /** The Democrat's expected margin, points. */
        double poll;
        /** After the election: the Democrat's margin. */
        Double result;

        public String state() { return state; }
        public int seatClass() { return seatClass; }
        public String democrat() { return democrat; }
        public String republican() { return republican; }
        public Party holder() { return holder; }
        public boolean open() { return open; }
        public double poll() { return poll; }
        public Double result() { return result; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("state", state); m.put("class", seatClass); m.put("d", democrat); m.put("r", republican); m.put("holder", holder.name()); m.put("open", open);
            m.put("poll", poll); m.put("result", result);
            return m;
        }

        static Race fromJson(Map<String, Object> m) {
            Race r = new Race();
            r.state = (String) m.get("state"); r.seatClass = Json.integer(m.get("class")); r.democrat = (String) m.get("d"); r.republican = (String) m.get("r");
            r.holder = Party.valueOf((String) m.get("holder")); r.open = (Boolean) m.get("open"); r.poll = Json.num(m.get("poll"));
            r.result = m.get("result") == null ? null : Json.num(m.get("result"));
            return r;
        }
    }

    /** An investigation by an opposition chamber (Resolute Desk's probes). */
    public static final class Probe {
        String subject, status;
        int chamber, opened, weeks;
        double severity;
        boolean impeachable, subpoenaPending, contempt;

        public String subject() { return subject; }
        /** "open", "in court", "reported", "closed". */
        public String status() { return status; }
        public int chamber() { return chamber; }
        public int opened() { return opened; }
        public double severity() { return severity; }
        public boolean impeachable() { return impeachable; }
        public boolean subpoenaPending() { return subpoenaPending; }
        public boolean contempt() { return contempt; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("subject", subject); m.put("status", status); m.put("chamber", chamber); m.put("opened", opened); m.put("weeks", weeks); m.put("severity", severity);
            m.put("flags", List.of(impeachable, subpoenaPending, contempt));
            return m;
        }

        static Probe fromJson(Map<String, Object> m) {
            Probe p = new Probe();
            p.subject = (String) m.get("subject"); p.status = (String) m.get("status"); p.chamber = Json.integer(m.get("chamber"));
            p.opened = Json.integer(m.get("opened")); p.weeks = Json.integer(m.get("weeks")); p.severity = Json.num(m.get("severity"));
            List<Object> f = Json.array(m.get("flags"));
            p.impeachable = (Boolean) f.get(0); p.subpoenaPending = (Boolean) f.get(1); p.contempt = (Boolean) f.get(2);
            return p;
        }
    }

    /** A decision the Hill puts to the president, with the week it must be answered by (or the default happens). */
    public record Pending(String kind, String subject, int due) {
        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", kind); m.put("subject", subject); m.put("due", due);
            return m;
        }

        static Pending fromJson(Map<String, Object> m) { return new Pending((String) m.get("kind"), (String) m.get("subject"), Json.integer(m.get("due"))); }
    }

    // ---- the state ----------------------------------------------------------------------------------------------------

    int week;
    /** Which Congress sits (121 from January 2029), and from which week. */
    int congressNumber = 121;
    int seatedWeek;
    final List<Bloc> blocs = new ArrayList<>();
    final List<Member> members = new ArrayList<>();
    final List<Bill> bills = new ArrayList<>();
    final List<Favor> favors = new ArrayList<>();
    int nextId = 1;
    // relations
    double mandate, mandate0, hoursMax, hoursUsed;
    int appointments = 4, nextAsk = 7, kept, broken, vetoes, overrides, laws;
    int reconFy = -1;
    boolean filibusterGone;
    /** The staff run the Hill: whip counts, the desk, the budget and the deadlines (Resolute Desk's delegated Hill desk). */
    boolean delegated;
    /** A member's request awaiting an answer: member id, kind ("visit", "project", "appointment"). */
    String askMember, askKind;
    // the budget
    int budgetFy;
    boolean requestSent;
    double reqDefense, reqOther, reqTax, reqEntitlements;
    final int[] approps = new int[12];
    int crUntil = -1;
    boolean shutdown;
    double blame;
    int shutdownWeeks, shutdowns;
    double debtLimit;
    int xWeek = -1;
    final List<Pending> pending = new ArrayList<>();
    // elections
    final List<Seat> senate = new ArrayList<>();
    final Map<String, Integer> houseD = new TreeMap<>(), houseSeats = new TreeMap<>();
    final Map<String, Double> campaign = new TreeMap<>();
    final List<Race> races = new ArrayList<>();
    int raceYear;
    /** The latest national House margin for the president's party, and the projected seats. */
    double lastNational;
    // investigations and impeachment
    final List<Probe> probes = new ArrayList<>();
    int nextProbe = 12;
    String impeachment = "none";
    int impeachWeek = -1, impeachYes, convictYes;
    /** The Hill's own record of what happened, newest last (for the room's history). */
    final List<String> record = new ArrayList<>();
    /** The bills and policy steps Congress has made law, by source. */
    final List<String> enacted = new ArrayList<>();

    HillState() {}

    public int week() { return week; }
    public int congressNumber() { return congressNumber; }
    public List<Bloc> blocs() { return Collections.unmodifiableList(blocs); }
    public List<Member> members() { return Collections.unmodifiableList(members); }
    public List<Bill> bills() { return Collections.unmodifiableList(bills); }
    public List<Favor> favors() { return Collections.unmodifiableList(favors); }
    public double mandate() { return mandate; }
    public double hoursMax() { return hoursMax; }
    public double hoursUsed() { return hoursUsed; }
    public double hoursLeft() { return Math.max(0, hoursMax - hoursUsed); }
    public int appointments() { return appointments; }
    public int promisesKept() { return kept; }
    public int promisesBroken() { return broken; }
    public int vetoes() { return vetoes; }
    public int overrides() { return overrides; }
    public int laws() { return laws; }
    public boolean filibusterGone() { return filibusterGone; }
    public boolean delegated() { return delegated; }
    public String askMember() { return askMember; }
    public String askKind() { return askKind; }
    public int budgetYear() { return budgetFy; }
    public boolean requestSent() { return requestSent; }
    public double[] request() { return new double[]{reqDefense, reqOther, reqTax, reqEntitlements}; }
    public int appropriation(int i) { return approps[i]; }
    public int crUntil() { return crUntil; }
    public boolean shutdown() { return shutdown; }
    public double blame() { return blame; }
    public int shutdownWeeks() { return shutdownWeeks; }
    public double debtLimit() { return debtLimit; }
    public int xWeek() { return xWeek; }
    public List<Pending> pending() { return Collections.unmodifiableList(pending); }
    public List<Seat> senate() { return Collections.unmodifiableList(senate); }
    public Map<String, Integer> houseDemocrats() { return Collections.unmodifiableMap(houseD); }
    public Map<String, Integer> houseSeats() { return Collections.unmodifiableMap(houseSeats); }
    public Map<String, Double> campaign() { return Collections.unmodifiableMap(campaign); }
    public List<Race> races() { return Collections.unmodifiableList(races); }
    public int raceYear() { return raceYear; }
    public double lastNational() { return lastNational; }
    public List<Probe> probes() { return Collections.unmodifiableList(probes); }
    public String impeachment() { return impeachment; }
    public int impeachYes() { return impeachYes; }
    public int convictYes() { return convictYes; }
    public List<String> record() { return Collections.unmodifiableList(record); }
    public List<String> enacted() { return Collections.unmodifiableList(enacted); }

    public Member member(String id) {
        for (Member m : members) if (m.id.equals(id)) return m;
        return null;
    }

    public Bill bill(String id) {
        for (Bill b : bills) if (b.id.equals(id)) return b;
        return null;
    }

    // ---- saves --------------------------------------------------------------------------------------------------------

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("week", week); m.put("congress", congressNumber); m.put("seated", seatedWeek); m.put("nextId", nextId);
        m.put("blocs", list(blocs, Bloc::toJson)); m.put("members", list(members, Member::toJson)); m.put("bills", list(bills, Bill::toJson));
        m.put("favors", list(favors, Favor::toJson));
        m.put("relations", nums(mandate, mandate0, hoursMax, hoursUsed));
        m.put("counts", List.of(appointments, nextAsk, kept, broken, vetoes, overrides, laws, reconFy));
        m.put("filibusterGone", filibusterGone); m.put("delegated", delegated); m.put("askMember", askMember); m.put("askKind", askKind);
        m.put("budget", List.of(budgetFy, requestSent, reqDefense, reqOther, reqTax, reqEntitlements, crUntil, shutdown, blame, shutdownWeeks, shutdowns, debtLimit, xWeek));
        List<Object> ap = new ArrayList<>();
        for (int a : approps) ap.add(a);
        m.put("approps", ap);
        m.put("pending", list(pending, Pending::toJson));
        m.put("senate", list(senate, Seat::toJson));
        m.put("houseD", new LinkedHashMap<String, Object>(houseD)); m.put("houseSeats", new LinkedHashMap<String, Object>(houseSeats));
        m.put("campaign", new LinkedHashMap<String, Object>(campaign));
        m.put("races", list(races, Race::toJson)); m.put("raceYear", raceYear); m.put("lastNational", lastNational);
        m.put("probes", list(probes, Probe::toJson)); m.put("nextProbe", nextProbe);
        m.put("impeachment", List.of(impeachment, impeachWeek, impeachYes, convictYes));
        m.put("record", new ArrayList<Object>(record));
        m.put("enacted", new ArrayList<Object>(enacted));
        return m;
    }

    public static HillState fromJson(Map<String, Object> m) {
        HillState s = new HillState();
        s.week = Json.integer(m.get("week")); s.congressNumber = Json.integer(m.get("congress")); s.seatedWeek = Json.integer(m.get("seated"));
        s.nextId = Json.integer(m.get("nextId"));
        for (Object o : Json.array(m.get("blocs"))) s.blocs.add(Bloc.fromJson(Json.object(o)));
        for (Object o : Json.array(m.get("members"))) s.members.add(Member.fromJson(Json.object(o)));
        for (Object o : Json.array(m.get("bills"))) s.bills.add(Bill.fromJson(Json.object(o)));
        for (Object o : Json.array(m.get("favors"))) s.favors.add(Favor.fromJson(Json.object(o)));
        double[] r = read(m.get("relations"));
        s.mandate = r[0]; s.mandate0 = r[1]; s.hoursMax = r[2]; s.hoursUsed = r[3];
        List<Object> c = Json.array(m.get("counts"));
        s.appointments = Json.integer(c.get(0)); s.nextAsk = Json.integer(c.get(1)); s.kept = Json.integer(c.get(2)); s.broken = Json.integer(c.get(3));
        s.vetoes = Json.integer(c.get(4)); s.overrides = Json.integer(c.get(5)); s.laws = Json.integer(c.get(6)); s.reconFy = Json.integer(c.get(7));
        s.filibusterGone = (Boolean) m.get("filibusterGone"); s.delegated = Boolean.TRUE.equals(m.get("delegated")); s.askMember = (String) m.get("askMember"); s.askKind = (String) m.get("askKind");
        List<Object> b = Json.array(m.get("budget"));
        s.budgetFy = Json.integer(b.get(0)); s.requestSent = (Boolean) b.get(1); s.reqDefense = Json.num(b.get(2)); s.reqOther = Json.num(b.get(3));
        s.reqTax = Json.num(b.get(4)); s.reqEntitlements = Json.num(b.get(5)); s.crUntil = Json.integer(b.get(6)); s.shutdown = (Boolean) b.get(7);
        s.blame = Json.num(b.get(8)); s.shutdownWeeks = Json.integer(b.get(9)); s.shutdowns = Json.integer(b.get(10)); s.debtLimit = Json.num(b.get(11));
        s.xWeek = Json.integer(b.get(12));
        List<Object> ap = Json.array(m.get("approps"));
        for (int i = 0; i < 12; i++) s.approps[i] = Json.integer(ap.get(i));
        for (Object o : Json.array(m.get("pending"))) s.pending.add(Pending.fromJson(Json.object(o)));
        for (Object o : Json.array(m.get("senate"))) s.senate.add(Seat.fromJson(Json.object(o)));
        Json.object(m.get("houseD")).forEach((k, v) -> s.houseD.put(k, Json.integer(v)));
        Json.object(m.get("houseSeats")).forEach((k, v) -> s.houseSeats.put(k, Json.integer(v)));
        Json.object(m.get("campaign")).forEach((k, v) -> s.campaign.put(k, Json.num(v)));
        for (Object o : Json.array(m.get("races"))) s.races.add(Race.fromJson(Json.object(o)));
        s.raceYear = Json.integer(m.get("raceYear")); s.lastNational = Json.num(m.get("lastNational"));
        for (Object o : Json.array(m.get("probes"))) s.probes.add(Probe.fromJson(Json.object(o)));
        s.nextProbe = Json.integer(m.get("nextProbe"));
        List<Object> im = Json.array(m.get("impeachment"));
        s.impeachment = (String) im.get(0); s.impeachWeek = Json.integer(im.get(1)); s.impeachYes = Json.integer(im.get(2)); s.convictYes = Json.integer(im.get(3));
        for (Object o : Json.array(m.get("record"))) s.record.add((String) o);
        if (m.get("enacted") != null) for (Object o : Json.array(m.get("enacted"))) s.enacted.add((String) o);
        return s;
    }

    private static <T> List<Object> list(List<T> xs, java.util.function.Function<T, Map<String, Object>> f) {
        List<Object> l = new ArrayList<>();
        for (T x : xs) l.add(f.apply(x));
        return l;
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
