package game.mandate.core.engine;

import game.mandate.core.campaign.CandidateCatalog;
import game.mandate.core.campaign.ElectionState;
import game.mandate.core.congress.CongressState;
import game.mandate.core.congress.PolicyState;
import game.mandate.core.data.Json;
import game.mandate.core.diplomacy.DiplomacyState;
import game.mandate.core.economy.BudgetState;
import game.mandate.core.economy.EconomyState;
import game.mandate.core.events.EventScheduler;
import game.mandate.core.executive.GovernmentState;
import game.mandate.core.hill.HillState;
import game.mandate.core.westwing.WestWingState;
import game.mandate.core.terms.TermsState;
import game.mandate.core.foreign.ForeignState;
import game.mandate.core.westwing.TransitionState;
import game.mandate.core.hormuz.HormuzState;
import game.mandate.core.taiwan.TaiwanState;
import game.mandate.core.game.Legacy;
import game.mandate.core.military.MilitaryState;
import game.mandate.core.opening.HundredDaysState;
import game.mandate.core.news.News;
import game.mandate.core.politics.PoliticsState;
import game.mandate.core.presidency.PresidentState;
import game.mandate.core.policy.PolicyTableState;
import game.mandate.core.time.Phase;
import game.mandate.core.time.SimulationClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The single authoritative, persistent state of a game. It is a composition of domain states, each owned by one
 * engine (economy and budget by the economy engine, Congress and policy by the Congress engine, and so on); this
 * class only holds them. Everything here is serialised by the save manager, so the state is the save.
 */
public final class WorldState {
    SimulationClock clock;
    Phase phase;
    EconomyState economy;
    BudgetState budget;
    PoliticsState politics;
    CongressState congress;
    PolicyState policy;
    GovernmentState government;
    DiplomacyState diplomacy;
    MilitaryState military;
    ElectionState election;
    /** The first hundred days' process state; null before the inauguration. */
    HundredDaysState hundredDays;
    /** The president's week, health and watch plan; null before the inauguration. */
    PresidentState president;
    /** Resolute Desk's policy table: where each policy stands and every move since the inauguration. */
    PolicyTableState policyTable;
    /** Congress as Resolute Desk models it (blocs, members, bills, the budget, races); null before the inauguration. */
    HillState hill;
    /** The staff, the cabinet, orders, the courts, scandals, the press and the developing stories; null before day 100. */
    WestWingState westWing;
    /** Terms of office: running again, the succession, the second term and the lame-duck weeks; null before the inauguration. */
    TermsState terms;
    /** Resolute Desk's world: forty countries, the hot spots, crises, operations, wars of choice; null before day 100. */
    ForeignState foreign;
    /** The transition office (the White House staff's shortlists); from the 2028 election, kept after the oath. */
    TransitionState transition;
    /** The Strait of Hormuz crisis, while it lasts and after (its record and the fading premium); null if there has been none. */
    HormuzState hormuz;
    /** The Taiwan crisis (docs/ISLAND_CHAIN.md), while it lasts and after (its record); null if there has been none. */
    TaiwanState taiwan;
    /** China escalated around Taiwan this week (a quarantine or a blockade): the week's end may open the crisis. */
    boolean taiwanEscalated;
    /** The weighed news The Capital Ledger is made from (the last 400 stories). */
    final List<News> news = new ArrayList<>();
    /** The era's stories that have already run (indexes into the ambient table). */
    final TreeSet<Integer> ambientUsed = new TreeSet<>();
    EventScheduler scheduler;
    ConsequenceLedger ledger;
    final List<String> log = new ArrayList<>();
    /** The indicators at the end of each governing turn, for charts. */
    final List<IndicatorPoint> indicators = new ArrayList<>();
    boolean midtermsHeld;
    /** Index in the command history of the command that inaugurated the president; -1 before. */
    int inaugurationCommand = -1;
    Legacy legacy;
    int commandsApplied;

    WorldState() {}

    public SimulationClock clock() { return clock; }
    public Phase phase() { return phase; }
    public EconomyState economy() { return economy; }
    public BudgetState budget() { return budget; }
    public PoliticsState politics() { return politics; }
    public CongressState congress() { return congress; }
    public PolicyState policy() { return policy; }
    public GovernmentState government() { return government; }
    public DiplomacyState diplomacy() { return diplomacy; }
    public MilitaryState military() { return military; }
    public ElectionState election() { return election; }
    public HundredDaysState hundredDays() { return hundredDays; }
    public PresidentState president() { return president; }
    public PolicyTableState policyTable() { return policyTable; }
    public HillState hill() { return hill; }
    public WestWingState westWing() { return westWing; }
    public TermsState terms() { return terms; }
    public ForeignState foreign() { return foreign; }
    public TransitionState transition() { return transition; }
    public HormuzState hormuz() { return hormuz; }
    public TaiwanState taiwan() { return taiwan; }
    public List<News> news() { return Collections.unmodifiableList(news); }
    public Set<Integer> ambientUsed() { return Collections.unmodifiableSet(ambientUsed); }
    public EventScheduler scheduler() { return scheduler; }
    public ConsequenceLedger ledger() { return ledger; }
    public List<String> log() { return Collections.unmodifiableList(log); }
    public List<IndicatorPoint> indicators() { return List.copyOf(indicators); }
    public boolean midtermsHeld() { return midtermsHeld; }
    public int inaugurationCommand() { return inaugurationCommand; }
    public Legacy legacy() { return legacy; }
    public int commandsApplied() { return commandsApplied; }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clock", clock.toJson()); m.put("phase", phase.name());
        m.put("economy", economy.toJson()); m.put("budget", budget.toJson()); m.put("politics", politics.toJson());
        m.put("congress", congress.toJson()); m.put("policy", policy.toJson()); m.put("government", government.toJson());
        m.put("diplomacy", diplomacy.toJson()); m.put("military", military.toJson()); m.put("election", election.toJson());
        m.put("hundredDays", hundredDays == null ? null : hundredDays.toJson());
        m.put("president", president == null ? null : president.toJson());
        m.put("policyTable", policyTable == null ? null : policyTable.toJson());
        m.put("hill", hill == null ? null : hill.toJson());
        m.put("westWing", westWing == null ? null : westWing.toJson());
        m.put("terms", terms == null ? null : terms.toJson());
        m.put("foreign", foreign == null ? null : foreign.toJson());
        m.put("transition", transition == null ? null : transition.toJson());
        m.put("hormuz", hormuz == null ? null : hormuz.toJson());
        m.put("taiwan", taiwan == null ? null : taiwan.toJson());
        m.put("taiwanEscalated", taiwanEscalated);
        List<Object> ns = new ArrayList<>();
        for (News n : news) ns.add(n.toJson());
        m.put("news", ns);
        m.put("ambientUsed", new ArrayList<Object>(ambientUsed));
        m.put("scheduler", scheduler.toJson()); m.put("ledger", ledger.toJson()); m.put("log", new ArrayList<>(log));
        List<Object> ind = new ArrayList<>();
        for (IndicatorPoint p : indicators) ind.add(p.toJson());
        m.put("indicators", ind);
        m.put("midtermsHeld", midtermsHeld); m.put("inaugurationCommand", inaugurationCommand); m.put("legacy", legacy == null ? null : legacy.toJson()); m.put("commandsApplied", commandsApplied);
        return m;
    }

    static WorldState fromJson(Map<String, Object> m, CandidateCatalog candidates) {
        WorldState w = new WorldState();
        w.clock = SimulationClock.fromJson(Json.object(m.get("clock")));
        w.phase = Phase.valueOf((String) m.get("phase"));
        w.economy = EconomyState.fromJson(Json.object(m.get("economy")));
        w.budget = BudgetState.fromJson(Json.object(m.get("budget")));
        w.politics = PoliticsState.fromJson(Json.object(m.get("politics")));
        w.congress = CongressState.fromJson(Json.object(m.get("congress")));
        w.policy = PolicyState.fromJson(Json.object(m.get("policy")));
        w.government = GovernmentState.fromJson(Json.object(m.get("government")));
        w.diplomacy = DiplomacyState.fromJson(Json.object(m.get("diplomacy")));
        w.military = MilitaryState.fromJson(Json.object(m.get("military")));
        w.election = ElectionState.fromJson(Json.object(m.get("election")), candidates);
        w.hundredDays = m.get("hundredDays") == null ? null : HundredDaysState.fromJson(Json.object(m.get("hundredDays")));
        w.president = m.get("president") == null ? null : PresidentState.fromJson(Json.object(m.get("president")));
        w.policyTable = m.get("policyTable") == null ? null : PolicyTableState.fromJson(Json.object(m.get("policyTable")));
        w.hill = m.get("hill") == null ? null : HillState.fromJson(Json.object(m.get("hill")));
        w.westWing = m.get("westWing") == null ? null : WestWingState.fromJson(Json.object(m.get("westWing")));
        w.terms = m.get("terms") == null ? null : TermsState.fromJson(Json.object(m.get("terms")));
        w.foreign = m.get("foreign") == null ? null : ForeignState.fromJson(Json.object(m.get("foreign")));
        w.transition = m.get("transition") == null ? null : TransitionState.fromJson(Json.object(m.get("transition")));
        w.hormuz = m.get("hormuz") == null ? null : HormuzState.fromJson(Json.object(m.get("hormuz")));
        w.taiwan = m.get("taiwan") == null ? null : TaiwanState.fromJson(Json.object(m.get("taiwan")));
        w.taiwanEscalated = Boolean.TRUE.equals(m.get("taiwanEscalated"));
        if (m.get("news") != null) for (Object o : Json.array(m.get("news"))) w.news.add(News.fromJson(Json.object(o)));
        if (m.get("ambientUsed") != null) for (Object o : Json.array(m.get("ambientUsed"))) w.ambientUsed.add(Json.integer(o));
        w.scheduler = EventScheduler.fromJson(Json.object(m.get("scheduler")));
        w.ledger = ConsequenceLedger.fromJson(Json.object(m.get("ledger")));
        for (Object o : Json.array(m.get("log"))) w.log.add((String) o);
        if (m.get("indicators") != null) for (Object o : Json.array(m.get("indicators"))) w.indicators.add(IndicatorPoint.fromJson(Json.object(o)));
        w.midtermsHeld = (Boolean) m.get("midtermsHeld");
        w.inaugurationCommand = m.get("inaugurationCommand") == null ? -1 : Json.integer(m.get("inaugurationCommand"));
        w.legacy = m.get("legacy") == null ? null : Legacy.fromJson(Json.object(m.get("legacy")));
        w.commandsApplied = Json.integer(m.get("commandsApplied"));
        return w;
    }
}
