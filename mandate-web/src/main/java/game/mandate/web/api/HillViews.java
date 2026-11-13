package game.mandate.web.api;

import game.mandate.core.command.Command;
import game.mandate.core.congress.Bill;
import game.mandate.core.data.Fmt;
import game.mandate.core.electorate.UsState;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.hill.HillEngine;
import game.mandate.core.hill.HillOrder;
import game.mandate.core.hill.HillState;
import game.mandate.core.presidency.WatchPlan;
import game.mandate.core.state.Party;
import game.mandate.core.time.Phase;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Roosevelt Room (Resolute Desk's {@code HillUI}): Congress's blocs and the people who lead them, the president's
 * bills and the whip count on each, what can be drafted, the budget, the coming election, favours, investigations, and
 * the decisions the Hill puts on the desk.
 */
final class HillViews {
    private HillViews() {}

    static final String[] PAY_FOR = {"Borrow", "Tax the wealthy", "Cut elsewhere"};

    static boolean open(GameEngine g) { return g.hill().isPresent() && g.phase() == Phase.GOVERNING; }

    static String party(Party p) { return p == null ? null : p.code(); }

    static String stage(HillState.Bill b) {
        String ch = HillState.CHAMBER[b.chamberNow()];
        return switch (b.stage()) {
            case DRAFT -> "Being drafted";
            case COMMITTEE, COMMITTEE2 -> (b.bottled() ? "Bottled up in committee" : "In committee") + " in the " + ch;
            case FLOOR, FLOOR2 -> (b.held() ? "Held from a vote" : "On the floor") + " of the " + ch;
            case CONFERENCE -> "In conference";
            case DESK -> "On your desk";
            case LAW -> "Law";
            case DEAD -> "Dead";
            case OVERRIDE -> "Law over a veto";
        };
    }

    /** The Roosevelt Room. */
    static Map<String, Object> room(GameEngine g) {
        Map<String, Object> v = EngineApi.ok();
        v.put("date", g.date().toString());
        v.put("open", open(g));
        if (g.hill().isEmpty()) {
            v.put("reason", "Congress is seated at the inauguration.");
            return v;
        }
        if (!open(g)) v.put("reason", g.phase() == Phase.FIRST_100_DAYS
                ? "The first hundred days run their own legislative fight (the flagship, in the agenda folders). The Roosevelt Room opens on day 100."
                : "Congress is out of the president's hands in this phase.");
        HillState s = g.hill().get();
        HillEngine e = g.hillEngine();
        HillEngine.Context c = g.hillContext();
        Party me = g.player().party();
        v.put("president", me.code());
        v.put("congress", ordinal(s.congressNumber()) + " Congress");
        v.put("houseDem", c.houseDem()); v.put("senateDem", c.senateDem());
        v.put("houseMajority", e.majority(s, c, HillState.HOUSE).code());
        v.put("senateMajority", e.majority(s, c, HillState.SENATE).code());
        v.put("unified", e.unified(c));
        v.put("hoursLeft", round1(s.hoursLeft())); v.put("hoursMax", round1(s.hoursMax()));
        v.put("appointments", s.appointments());
        v.put("mandate", round1(s.mandate()));
        v.put("filibusterGone", s.filibusterGone());
        v.put("delegated", s.delegated());
        v.put("week", s.week());
        v.put("laws", s.laws()); v.put("vetoes", s.vetoes()); v.put("overrides", s.overrides());
        v.put("promisesKept", s.promisesKept()); v.put("promisesBroken", s.promisesBroken());

        List<Object> blocs = new ArrayList<>();
        for (int i = 0; i < s.blocs().size(); i++) {
            HillState.Bloc b = s.blocs().get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", i); m.put("name", b.name()); m.put("party", b.party().code()); m.put("chamber", HillState.CHAMBER[b.chamber()]);
            m.put("seats", b.seats()); m.put("relationship", round1(b.relationship())); m.put("moderate", b.moderate());
            m.put("loyalty", round2(b.loyalty())); m.put("hawk", round2(b.hawk()));
            m.put("leaning", leaning(b.econ(), b.cult()));
            blocs.add(m);
        }
        v.put("blocs", blocs);

        List<Object> members = new ArrayList<>();
        for (HillState.Member m : s.members()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", m.id()); x.put("name", m.name()); x.put("role", m.role()); x.put("committee", m.committee()); x.put("state", m.state());
            x.put("party", m.party().code()); x.put("chamber", HillState.CHAMBER[m.chamber()]); x.put("bloc", s.blocs().get(m.bloc()).name());
            x.put("relationship", round1(m.relationship())); x.put("price", m.price()); x.put("owesFavor", m.owesFavor()); x.put("leader", m.leader());
            members.add(x);
        }
        v.put("members", members);

        List<Object> bills = new ArrayList<>();
        for (HillState.Bill b : s.bills()) bills.add(bill(g, s, e, c, b));
        v.put("bills", bills);

        // what can be drafted: the game's bills the president's party can sponsor, not law and not already moving
        List<Object> drafts = new ArrayList<>();
        for (Bill b : g.bills().availableTo(me)) {
            if (g.laws().contains(b.id()) || s.enacted().contains(b.id())) continue;
            boolean moving = s.bills().stream().anyMatch(x -> x.ours() && x.source().equals(b.id()) && (x.stage() == HillState.Stage.DRAFT || x.stage().active()));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.id()); m.put("name", b.name()); m.put("issue", b.issue().label());
            m.put("sponsor", b.sponsor().map(Party::code).orElse(null)); m.put("cost", Math.round(b.annualCostBn()));
            m.put("reconciliation", !b.requires60()); m.put("agenda", g.agenda().contains(b.id())); m.put("moving", moving);
            m.put("summary", "On " + b.issue().label().toLowerCase() + ". " + (b.annualCostBn() > 0 ? "Costs about $" + Math.round(b.annualCostBn()) + " billion a year" : b.annualCostBn() < 0 ? "Saves about $" + Math.round(-b.annualCostBn()) + " billion a year" : "Costs little")
                    + (b.requires60() ? "; it needs 60 votes in the Senate." : "; it can go through reconciliation."));
            drafts.add(m);
        }
        v.put("drafts", drafts);

        List<Object> pending = new ArrayList<>();
        for (HillState.Pending p : s.pending()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", p.kind()); m.put("label", pendingLabel(s, p)); m.put("weeksLeft", Math.max(0, p.due() - s.week()));
            pending.add(m);
        }
        v.put("pending", pending);
        if (s.askMember() != null) {
            HillState.Member m = s.member(s.askMember());
            v.put("ask", Map.of("member", m == null ? "A member" : m.name(), "kind", s.askKind()));
        }

        v.put("budget", budget(g, s, c));
        v.put("elections", elections(g, s, e, c));

        List<Object> favors = new ArrayList<>();
        for (HillState.Favor f : s.favors()) {
            if (f.settled()) continue;
            HillState.Member m = s.member(f.member());
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("member", m == null ? "A former member" : m.name()); x.put("memberId", f.member());
            x.put("role", m == null ? "" : m.role() + (m.committee() == null ? "" : ", " + m.committee()));
            x.put("why", f.why()); x.put("promise", f.promise()); x.put("state", f.state());
            x.put("weeksLeft", f.promise() ? Math.max(0, f.due() - s.week()) : null);
            favors.add(x);
        }
        v.put("favors", favors);

        List<Object> probes = new ArrayList<>();
        for (HillState.Probe p : s.probes()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("subject", p.subject()); x.put("status", p.status()); x.put("chamber", HillState.CHAMBER[p.chamber()]);
            x.put("severity", round1(p.severity())); x.put("impeachable", p.impeachable()); x.put("contempt", p.contempt()); x.put("subpoena", p.subpoenaPending());
            probes.add(x);
        }
        v.put("probes", probes);
        Map<String, Object> imp = new LinkedHashMap<>();
        imp.put("status", s.impeachment()); imp.put("houseYes", s.impeachYes()); imp.put("senateYes", s.convictYes());
        v.put("impeachment", imp);

        List<Object> record = new ArrayList<>();
        for (int i = s.record().size() - 1; i >= 0 && record.size() < 40; i--) {
            String r = s.record().get(i);
            int colon = r.indexOf(": ");
            record.add(Map.of("date", colon > 0 ? r.substring(0, colon) : "", "what", colon > 0 ? r.substring(colon + 2) : r));
        }
        v.put("record", record);
        return v;
    }

    static Map<String, Object> bill(GameEngine g, HillState s, HillEngine e, HillEngine.Context c, HillState.Bill b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.id()); m.put("source", b.source()); m.put("name", b.name()); m.put("description", b.description());
        m.put("ours", b.ours()); m.put("author", party(b.author()));
        m.put("stage", stage(b)); m.put("stageKey", b.stage().name());
        m.put("chamber", HillState.CHAMBER[b.chamberNow()]);
        m.put("weeksInStage", s.week() - b.stageWeek()); m.put("stageWeeks", HillEngine.stageWeeks(b));
        m.put("size", round2(b.size())); m.put("scope", round2(b.scope())); m.put("payFor", b.payFor()); m.put("payForLabel", PAY_FOR[b.payFor()]);
        m.put("reconciliation", b.reconciliation()); m.put("budgetEligible", b.budgetEligible()); m.put("firstChamber", b.firstChamber());
        m.put("support", Math.round(e.support(b))); m.put("cost", Math.round(b.cost())); m.put("netCost", Math.round(b.netCost()));
        m.put("strength", round2(b.strength()));
        m.put("held", b.held()); m.put("bottled", b.bottled()); m.put("wentPublic", b.wentPublic()); m.put("vetoed", b.vetoed()); m.put("note", b.note());
        m.put("votes", Map.of("houseYes", b.houseYes(), "houseNo", b.houseNo(), "senateYes", b.senateYes(), "senateNo", b.senateNo()));
        boolean live = b.stage() == HillState.Stage.DRAFT || b.stage().active();
        if (live) {
            List<Object> whip = new ArrayList<>();
            for (int ch = 0; ch < 2; ch++) {
                double[] cnt = e.count(s, c, b.id(), ch);
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("chamber", HillState.CHAMBER[ch]); w.put("expected", Math.round(cnt[0])); w.put("spread", round1(cnt[1]));
                w.put("need", e.need(s, c, b.id(), ch)); w.put("chance", round2(e.chance(s, c, b.id(), ch)));
                w.put("seats", ch == 0 ? 435 : 100);
                List<Object> bl = new ArrayList<>();
                for (int i = 0; i < s.blocs().size(); i++) {
                    HillState.Bloc x = s.blocs().get(i);
                    if (x.chamber() != ch) continue;
                    double p = e.blocYes(s, c, b.id(), i);
                    bl.add(Map.of("name", x.name(), "party", x.party().code(), "seats", x.seats(), "yes", round2(p)));
                }
                w.put("blocs", bl);
                whip.add(w);
            }
            m.put("whip", whip);
            List<Object> dealt = new ArrayList<>();
            for (HillState.Member x : s.members()) if (b.dealtIn(x.id(), s.week())) dealt.add(x.id());
            m.put("dealtThisWeek", dealt);
        }
        if (g.bills().has(b.source())) m.put("sponsorParty", g.bill(b.source()).sponsor().map(Party::code).orElse(null));
        return m;
    }

    static Map<String, Object> budget(GameEngine g, HillState s, HillEngine.Context c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fiscalYear", s.budgetYear());
        double[] r = s.request();
        m.put("request", Map.of("defense", r[0], "other", r[1], "tax", r[2], "entitlements", r[3]));
        m.put("requestDeficit", round2(HillEngine.requestDeficit(s)));
        m.put("requestSent", s.requestSent());
        s.pending().stream().filter(p -> p.kind().equals("budget")).findFirst().ifPresent(p -> m.put("requestDueWeeks", Math.max(0, p.due() - s.week())));
        List<Object> ap = new ArrayList<>();
        for (int i = 0; i < 12; i++) ap.add(Map.of("name", HillState.APPROPRIATIONS[i], "stage", s.appropriation(i), "label", HillState.APPROPRIATION_STAGE[s.appropriation(i)]));
        m.put("appropriations", ap);
        m.put("stopgapWeeks", s.crUntil() < 0 ? null : Math.max(0, s.crUntil() - s.week()));
        m.put("shutdown", s.shutdown()); m.put("blame", round2(s.blame())); m.put("shutdownWeeks", s.shutdownWeeks());
        m.put("debtLimit", round1(s.debtLimit()));
        m.put("debtSubject", round1(HillEngine.debtSubject(c)));
        m.put("xDateWeeks", s.xWeek() < 0 ? null : Math.max(0, s.xWeek() - s.week()));
        LocalDate d = g.date();
        LocalDate deadline = LocalDate.of(d.getMonthValue() >= 10 ? d.getYear() + 1 : d.getYear(), 9, 30);
        m.put("deadline", deadline.toString());
        m.put("deficit", round2(c.deficitPct()));
        return m;
    }

    static Map<String, Object> elections(GameEngine g, HillState s, HillEngine e, HillEngine.Context c) {
        Map<String, Object> m = new LinkedHashMap<>();
        LocalDate eday = c.electionDay();
        m.put("date", eday.toString());
        m.put("label", eday.getYear() % 4 == 0 ? "Election Day " + eday.getYear() : "The midterm elections, " + eday.getYear());
        m.put("days", ChronoUnit.DAYS.between(g.date(), eday));
        m.put("racesOpen", s.raceYear() == eday.getYear());
        double nat = e.national(s, c);
        m.put("national", round1(nat));
        List<Object> races = new ArrayList<>();
        for (HillState.Race r : s.races()) {
            Map<String, Object> x = new LinkedHashMap<>();
            UsState u = state(g, r.state());
            x.put("state", r.state()); x.put("name", u == null ? r.state() : u.name()); x.put("seatClass", r.seatClass());
            x.put("democrat", r.democrat()); x.put("republican", r.republican()); x.put("holder", r.holder().code()); x.put("open", r.open());
            x.put("poll", round1(r.poll())); x.put("result", r.result() == null ? null : round1(r.result()));
            races.add(x);
        }
        m.put("races", races);
        List<Object> house = new ArrayList<>();
        for (Map.Entry<String, Integer> x : s.houseSeats().entrySet()) {
            UsState u = state(g, x.getKey());
            house.add(Map.of("state", x.getKey(), "name", u == null ? x.getKey() : u.name(), "seats", x.getValue(), "dem", s.houseDemocrats().getOrDefault(x.getKey(), 0)));
        }
        m.put("house", house);
        double effort = 0;
        for (double x : c.campaign().values()) effort += x;
        m.put("campaignStops", round1(effort));
        m.put("warChest", round1(c.warChest()));
        return m;
    }

    static String pendingLabel(HillState s, HillState.Pending p) {
        return switch (p.kind()) {
            case "desk" -> { HillState.Bill b = s.bill(p.subject()); yield "Sign or veto the " + (b == null ? "bill" : b.name()); }
            case "budget" -> "Send the budget request to Congress";
            case "cr" -> "A stopgap: " + p.subject();
            case "shutdown" -> "The shutdown: hold or deal";
            case "debt" -> "The debt limit: " + p.subject();
            case "debt1" -> "The debt limit: the last week";
            case "subpoena" -> "A subpoena from the " + probeChamber(s, p.subject());
            case "ask" -> "A member's request";
            default -> p.subject();
        };
    }

    static String probeChamber(HillState s, String index) {
        try { return HillState.CHAMBER[s.probes().get(Integer.parseInt(index)).chamber()]; } catch (RuntimeException e) { return "Congress"; }
    }

    // ---- the decisions on the desk ----------------------------------------------------------------------------------

    /** The Hill's questions for the president, as decision cards, with the staff's answer recommended. */
    static List<Object> decisions(GameEngine g) {
        List<Object> out = new ArrayList<>();
        if (!open(g)) return out;
        HillState s = g.hill().get();
        HillEngine e = g.hillEngine();
        HillEngine.Context c = g.hillContext();
        for (HillState.Pending p : s.pending()) {
            if (p.kind().equals("budget")) continue;       // the request is shaped in the Roosevelt Room; it is sent for you if left
            List<Object> options = new ArrayList<>();
            List<HillOrder> orders = new ArrayList<>();
            String title, body;
            int stop;
            String due = p.due() - s.week() <= 0 ? "This week." : "Within " + (p.due() - s.week()) + " weeks.";
            switch (p.kind()) {
                case "desk" -> {
                    HillState.Bill b = s.bill(p.subject());
                    if (b == null) continue;
                    title = "The " + b.name() + " is on your desk";
                    String who = b.ours() ? "Your bill" : "A bill the " + (b.author() == Party.DEMOCRAT ? "Democratic" : "Republican") + " majority moved";
                    body = who + Fmt.format(", passed by the House %d-%d and the Senate %d-%d. %d%% of the public supports it. ", b.houseYes(), b.houseNo(), b.senateYes(), b.senateNo(), Math.round(e.support(b)))
                            + "Left unsigned, it becomes law in ten days. " + due;
                    int overrideH = e.need(s, c, b.id(), HillState.HOUSE) , overrideS = e.need(s, c, b.id(), HillState.SENATE);
                    add(options, orders, "Sign it", "It becomes law; the bill's steps move the policy table and its money starts after a quarter."
                            + (b.ours() ? " Your party's blocs warm to you." : " Its sponsors owe you."), null, null, new HillOrder.Desk(b.id(), HillEngine.Desk.SIGN));
                    add(options, orders, "Veto it", Fmt.format("Congress can override with two-thirds of each chamber (it needed %d and %d to pass).", overrideH, overrideS)
                            + (b.ours() ? " Vetoing your own bill angers your party." : " The sponsors will try to override."), "Veto", "WARN", new HillOrder.Desk(b.id(), HillEngine.Desk.VETO));
                    add(options, orders, "Let it become law unsigned", "It becomes law without your name on it: no credit and no ceremony.", null, null,
                            new HillOrder.Desk(b.id(), HillEngine.Desk.UNSIGNED));
                    stop = WatchPlan.STOP_SIGN;
                }
                case "cr" -> {
                    boolean minority = p.subject().startsWith("the minority");
                    title = minority ? "The minority blocks a stopgap" : "A stopgap on the majority's terms";
                    body = "The fiscal year ends with the appropriations unfinished. " + (minority ? "The other party's senators are filibustering a stopgap." : "The majority will keep the government open only on its terms.")
                            + " " + due;
                    add(options, orders, "Accept their terms", "The government stays open for eleven weeks; your party's base grumbles.", null, null, new HillOrder.Choose("cr", HillEngine.Choice.ACCEPT));
                    add(options, orders, "Demand a clean stopgap", "A gamble: if they fold, you look strong; if not, the government shuts down.", "Risk of a shutdown", "WARN",
                            new HillOrder.Choose("cr", HillEngine.Choice.CLEAN));
                    stop = WatchPlan.STOP_BUDGET;
                }
                case "shutdown" -> {
                    title = Fmt.format("The government is shut, week %d", s.shutdownWeeks() + 1);
                    body = Fmt.format("Polls put %.0f%% of the blame on the White House. Every week costs growth and your standing. ", s.blame() * 100) + due;
                    add(options, orders, "Hold firm", "The other side may cave; the longer it lasts, the likelier, unless the blame falls on you.", null, null,
                            new HillOrder.Choose("shutdown", HillEngine.Choice.HOLD));
                    add(options, orders, "Make a deal", "The government reopens; your party sees a retreat and spending is trimmed.", null, null,
                            new HillOrder.Choose("shutdown", HillEngine.Choice.DEAL));
                    stop = WatchPlan.STOP_BUDGET;
                }
                case "debt" -> {
                    title = "The debt limit: " + p.subject();
                    body = Fmt.format("The debt has reached its limit of $%.1f trillion; the Treasury's extraordinary measures run out at the X-date. ", s.debtLimit()) + due;
                    add(options, orders, "Ask for a clean increase", e.unified(c) ? "Likely with your party in charge of both chambers." : "Unlikely in a divided Congress; a failure rattles markets.",
                            null, null, new HillOrder.Choose("debt", HillEngine.Choice.CLEAN));
                    add(options, orders, "Trade spending cuts for it", "It passes; the cuts trim the deficit and anger your party.", null, null,
                            new HillOrder.Choose("debt", HillEngine.Choice.CUTS));
                    add(options, orders, "Go to the brink", "Hold out for a better deal: a win if they blink, a market slide if they do not.", "Gamble", "WARN",
                            new HillOrder.Choose("debt", HillEngine.Choice.BRINK));
                    stop = WatchPlan.STOP_BUDGET;
                }
                case "debt1" -> {
                    title = "One week to the X-date";
                    body = "If the limit is not raised, the Treasury misses payments: a financial shock, a downgrade and a blow to your standing.";
                    add(options, orders, "Take the last-minute deal", "Default is averted, on the other side's terms.", null, null, new HillOrder.Choose("debt1", HillEngine.Choice.DEAL));
                    add(options, orders, "Invoke the 14th Amendment", "Keep paying the debt on your own authority: lawsuits, a market scare and a scandal.", "Constitutional fight", "BAD",
                            new HillOrder.Choose("debt1", HillEngine.Choice.FOURTEENTH));
                    add(options, orders, "Let the date pass", "Default: markets fall hard, and so do you.", "Default", "BAD", new HillOrder.Choose("debt1", HillEngine.Choice.BRINK));
                    stop = WatchPlan.STOP_BUDGET;
                }
                case "subpoena" -> {
                    HillState.Probe pr;
                    try { pr = s.probes().get(Integer.parseInt(p.subject())); } catch (RuntimeException x) { continue; }
                    title = "A subpoena: " + pr.subject();
                    body = "The " + HillState.CHAMBER[pr.chamber()] + " investigation into " + pr.subject() + " demands documents and testimony. " + due;
                    add(options, orders, "Comply", "Hand over what they ask for: what it shows decides how bad the report is.", null, null, new HillOrder.Choose("subpoena", HillEngine.Choice.COMPLY));
                    add(options, orders, "Negotiate the scope", "The counsel's office narrows it; a good staff usually keeps the worst out.", null, null,
                            new HillOrder.Choose("subpoena", HillEngine.Choice.NEGOTIATE));
                    add(options, orders, "Claim executive privilege", "The courts take months to decide; if you lose, it comes out worse.", null, null,
                            new HillOrder.Choose("subpoena", HillEngine.Choice.PRIVILEGE));
                    add(options, orders, "Refuse", "Contempt of Congress, and grounds for impeachment.", "Contempt", "BAD", new HillOrder.Choose("subpoena", HillEngine.Choice.REFUSE));
                    stop = WatchPlan.STOP_SCANDAL;
                }
                case "ask" -> {
                    HillState.Member m = s.member(s.askMember());
                    if (m == null || s.askKind() == null) continue;
                    String what = switch (s.askKind()) {
                        case "visit" -> "a visit to " + m.state();
                        case "project" -> "a project at home";
                        default -> "an appointment for an ally";
                    };
                    title = m.name() + " asks for " + what;
                    body = m.role() + (m.committee() == null ? "" : ", " + m.committee()) + Fmt.format(" (%s, %s). Your relationship: %.0f of 100. ", m.party().code(), m.chamber() == 0 ? "House" : "Senate", m.relationship())
                            + (s.askKind().equals("visit") ? "A yes is a promise: visit within eight weeks or it counts as broken. " : "")
                            + "A yes earns a favour you can call in on a vote. " + due;
                    add(options, orders, "Yes", s.askKind().equals("appointment") ? Fmt.format("Uses one of your %d appointments.", s.appointments()) : s.askKind().equals("project") ? "A small cost to the budget; fiscal hawks notice." : "A trip to " + m.state() + " keeps it.",
                            null, null, new HillOrder.Answer(true));
                    add(options, orders, "No", "The member cools toward you.", null, null, new HillOrder.Answer(false));
                    stop = -1;
                }
                default -> { continue; }
            }
            Map<String, Object> d = EngineApi.decision("hill", "Congress", title, body, options);
            HillOrder rec = e.recommend(s, c, p);
            int pick = 0;
            for (int i = 0; i < orders.size(); i++) if (orders.get(i).equals(rec)) pick = i;
            d.put("recommended", pick);
            d.put("stop", stop);
            out.add(d);
        }
        return out;
    }

    private static void add(List<Object> options, List<HillOrder> orders, String label, String detail, String tag, String tone, HillOrder o) {
        options.add(EngineApi.option(label, detail, tag, tone, new Command.Hill(o)));
        orders.add(o);
    }

    /** The count of Hill decisions on the desk. */
    static int count(GameEngine g) { return decisions(g).size(); }

    // ---- small helpers ----------------------------------------------------------------------------------------------

    static String leaning(double econ, double cult) {
        String e = econ < -0.45 ? "progressive" : econ < -0.15 ? "center-left" : econ <= 0.15 ? "centrist" : econ <= 0.45 ? "center-right" : "conservative";
        return e + (Math.abs(cult - econ) > 0.3 ? (cult > econ ? ", socially conservative" : ", socially liberal") : "");
    }

    static String ordinal(int n) {
        int m = n % 100;
        String suf = m >= 11 && m <= 13 ? "th" : switch (n % 10) { case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th"; };
        return n + suf;
    }

    static double round1(double x) { return Math.round(x * 10) / 10.0; }
    static double round2(double x) { return Math.round(x * 100) / 100.0; }

    static UsState state(GameEngine g, String code) {
        try { return g.states().get(code); } catch (IllegalArgumentException e) { return null; }
    }
}
