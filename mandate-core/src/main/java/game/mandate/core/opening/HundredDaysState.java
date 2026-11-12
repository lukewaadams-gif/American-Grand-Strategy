package game.mandate.core.opening;

import game.mandate.core.data.Json;
import game.mandate.core.opening.Types.BillSize;
import game.mandate.core.opening.Types.BillStage;
import game.mandate.core.opening.Types.CrisisKind;
import game.mandate.core.opening.Types.CrisisOutcome;
import game.mandate.core.opening.Types.Domain;
import game.mandate.core.opening.Types.EconomicChallenge;
import game.mandate.core.opening.Types.FundingOffer;
import game.mandate.core.opening.Types.PayFor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The process state of the first hundred days: the flagship bill's journey, the funding deadline, the economic
 * challenge, the foreign crisis, open decisions and the weekly briefings. Everything else the hundred days touch
 * (opinion, economy, Congress, cabinet, deterrence) lives in the domain states it belongs to. Owned by
 * {@link HundredDays}; part of the {@code WorldState} and therefore of the save.
 */
public final class HundredDaysState {
    int week = 1;
    int weeks;
    final List<String> log = new ArrayList<>();
    final List<Briefing> briefings = new ArrayList<>();
    final List<Decision> pending = new ArrayList<>();
    final List<String> weekConsequences = new ArrayList<>();
    final List<String> weekReactions = new ArrayList<>();
    int nextDecisionId = 1;
    final Set<Domain> delegated = EnumSet.noneOf(Domain.class);
    final boolean[] revolted = {false, false};
    double publicPressure, oilShock, stressShock;
    boolean reserveReleased;

    // the flagship bill
    String flagshipId;
    BillStage stage = BillStage.NOT_DRAFTED;
    BillSize size;
    PayFor payFor;
    boolean reconciliation, billActedThisWeek;
    final Map<String, Double> pressured = new LinkedHashMap<>();
    int appointmentTokens = 2;
    double policyDelivered;
    int weekSigned;

    // the foreign crisis
    CrisisKind crisisKind;
    int crisisWeek;
    double resolve;                      // hidden from the player
    CrisisOutcome crisis = CrisisOutcome.NONE;
    int escalation, crisisWeeks;
    double intelEstimate = Double.NaN;
    double lastEscalationRisk;

    // the budget and the economy
    FundingOffer fundingOffer;
    boolean shutdown, fundingResolved;
    int shutdownWeeks;
    EconomicChallenge challenge;
    int challengeWeek;
    int challengeResponse = -1;

    // for the report
    double startApproval, startMisery, startDeficit;
    final List<Double> approvalByWeek = new ArrayList<>();

    HundredDaysState() {}

    public int week() { return week; }
    public String flagshipId() { return flagshipId; }
    public int weeks() { return weeks; }
    public BillStage stage() { return stage; }
    public CrisisOutcome crisis() { return crisis; }
    public List<Double> approvalByWeek() { return List.copyOf(approvalByWeek); }
    public List<String> log() { return java.util.Collections.unmodifiableList(log); }
    public double startApproval() { return startApproval; }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("week", week); m.put("weeks", weeks); m.put("log", new ArrayList<>(log));
        List<Object> b = new ArrayList<>();
        for (Briefing x : briefings) {
            Map<String, Object> j = new LinkedHashMap<>();
            j.put("week", x.week()); j.put("headline", x.headline()); j.put("consequences", x.consequences());
            j.put("reactions", x.reactions()); j.put("intelligence", x.intelligence());
            b.add(j);
        }
        m.put("briefings", b);
        m.put("pending", decisions(pending));
        m.put("weekConsequences", new ArrayList<>(weekConsequences)); m.put("weekReactions", new ArrayList<>(weekReactions));
        m.put("nextDecisionId", nextDecisionId);
        m.put("delegated", delegated.stream().map(Enum::name).toList());
        m.put("revolted", List.of(revolted[0], revolted[1]));
        m.put("publicPressure", publicPressure); m.put("oilShock", oilShock); m.put("stressShock", stressShock); m.put("reserveReleased", reserveReleased);
        m.put("flagshipId", flagshipId); m.put("stage", stage.name()); m.put("size", size == null ? null : size.name());
        m.put("payFor", payFor == null ? null : payFor.name()); m.put("reconciliation", reconciliation); m.put("billActedThisWeek", billActedThisWeek);
        m.put("pressured", new LinkedHashMap<>(pressured)); m.put("appointmentTokens", appointmentTokens); m.put("policyDelivered", policyDelivered);
        m.put("weekSigned", weekSigned);
        m.put("crisisKind", crisisKind.name()); m.put("crisisWeek", crisisWeek); m.put("resolve", resolve); m.put("crisis", crisis.name());
        m.put("escalation", escalation); m.put("crisisWeeks", crisisWeeks); m.put("intelEstimate", Double.isNaN(intelEstimate) ? null : intelEstimate);
        m.put("lastEscalationRisk", lastEscalationRisk);
        m.put("fundingOffer", fundingOffer == null ? null : fundingOffer.name()); m.put("shutdown", shutdown); m.put("fundingResolved", fundingResolved);
        m.put("shutdownWeeks", shutdownWeeks); m.put("challenge", challenge == null ? null : challenge.name()); m.put("challengeWeek", challengeWeek);
        m.put("challengeResponse", challengeResponse);
        m.put("startApproval", startApproval); m.put("startMisery", startMisery); m.put("startDeficit", startDeficit);
        m.put("approvalByWeek", new ArrayList<>(approvalByWeek));
        return m;
    }

    public static HundredDaysState fromJson(Map<String, Object> m) {
        HundredDaysState s = new HundredDaysState();
        s.week = Json.integer(m.get("week")); s.weeks = Json.integer(m.get("weeks"));
        strings(m.get("log"), s.log);
        for (Object o : Json.array(m.get("briefings"))) {
            Map<String, Object> j = Json.object(o);
            List<String> c = new ArrayList<>(), r = new ArrayList<>(), i = new ArrayList<>();
            strings(j.get("consequences"), c); strings(j.get("reactions"), r); strings(j.get("intelligence"), i);
            s.briefings.add(new Briefing(Json.integer(j.get("week")), (String) j.get("headline"), c, r, i));
        }
        s.pending.addAll(decisionsFrom(m.get("pending")));
        strings(m.get("weekConsequences"), s.weekConsequences); strings(m.get("weekReactions"), s.weekReactions);
        s.nextDecisionId = Json.integer(m.get("nextDecisionId"));
        for (Object o : Json.array(m.get("delegated"))) s.delegated.add(Domain.valueOf((String) o));
        List<Object> rv = Json.array(m.get("revolted"));
        s.revolted[0] = (Boolean) rv.get(0); s.revolted[1] = (Boolean) rv.get(1);
        s.publicPressure = Json.num(m.get("publicPressure")); s.oilShock = Json.num(m.get("oilShock")); s.stressShock = Json.num(m.get("stressShock"));
        s.reserveReleased = (Boolean) m.get("reserveReleased");
        s.flagshipId = (String) m.get("flagshipId"); s.stage = BillStage.valueOf((String) m.get("stage"));
        s.size = m.get("size") == null ? null : BillSize.valueOf((String) m.get("size"));
        s.payFor = m.get("payFor") == null ? null : PayFor.valueOf((String) m.get("payFor"));
        s.reconciliation = (Boolean) m.get("reconciliation"); s.billActedThisWeek = (Boolean) m.get("billActedThisWeek");
        Json.object(m.get("pressured")).forEach((k, v) -> s.pressured.put(k, Json.num(v)));
        s.appointmentTokens = Json.integer(m.get("appointmentTokens")); s.policyDelivered = Json.num(m.get("policyDelivered"));
        s.weekSigned = Json.integer(m.get("weekSigned"));
        s.crisisKind = CrisisKind.valueOf((String) m.get("crisisKind")); s.crisisWeek = Json.integer(m.get("crisisWeek"));
        s.resolve = Json.num(m.get("resolve")); s.crisis = CrisisOutcome.valueOf((String) m.get("crisis"));
        s.escalation = Json.integer(m.get("escalation")); s.crisisWeeks = Json.integer(m.get("crisisWeeks"));
        s.intelEstimate = m.get("intelEstimate") == null ? Double.NaN : Json.num(m.get("intelEstimate"));
        s.lastEscalationRisk = Json.num(m.get("lastEscalationRisk"));
        s.fundingOffer = m.get("fundingOffer") == null ? null : FundingOffer.valueOf((String) m.get("fundingOffer"));
        s.shutdown = (Boolean) m.get("shutdown"); s.fundingResolved = (Boolean) m.get("fundingResolved"); s.shutdownWeeks = Json.integer(m.get("shutdownWeeks"));
        s.challenge = m.get("challenge") == null ? null : EconomicChallenge.valueOf((String) m.get("challenge"));
        s.challengeWeek = Json.integer(m.get("challengeWeek")); s.challengeResponse = Json.integer(m.get("challengeResponse"));
        s.startApproval = Json.num(m.get("startApproval")); s.startMisery = Json.num(m.get("startMisery")); s.startDeficit = Json.num(m.get("startDeficit"));
        for (Object o : Json.array(m.get("approvalByWeek"))) s.approvalByWeek.add(Json.num(o));
        return s;
    }

    private static List<Object> decisions(List<Decision> ds) {
        List<Object> l = new ArrayList<>();
        for (Decision d : ds) {
            Map<String, Object> j = new LinkedHashMap<>();
            j.put("id", d.id()); j.put("kind", d.kind().name()); j.put("title", d.title()); j.put("situation", d.situation());
            List<Object> os = new ArrayList<>();
            for (Decision.Option o : d.options()) os.add(List.of(o.label(), o.preview()));
            j.put("options", os);
            l.add(j);
        }
        return l;
    }

    private static List<Decision> decisionsFrom(Object o) {
        List<Decision> l = new ArrayList<>();
        for (Object x : Json.array(o)) {
            Map<String, Object> j = Json.object(x);
            List<Decision.Option> os = new ArrayList<>();
            for (Object y : Json.array(j.get("options"))) {
                List<Object> p = Json.array(y);
                os.add(new Decision.Option((String) p.get(0), (String) p.get(1)));
            }
            l.add(new Decision(Json.integer(j.get("id")), Decision.Kind.valueOf((String) j.get("kind")), (String) j.get("title"), (String) j.get("situation"), os));
        }
        return l;
    }

    private static void strings(Object o, List<String> into) { for (Object x : Json.array(o)) into.add((String) x); }
}
