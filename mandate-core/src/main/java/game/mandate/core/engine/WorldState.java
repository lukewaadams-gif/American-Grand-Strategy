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
import game.mandate.core.game.Legacy;
import game.mandate.core.military.MilitaryState;
import game.mandate.core.politics.PoliticsState;
import game.mandate.core.time.Phase;
import game.mandate.core.time.SimulationClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    EventScheduler scheduler;
    ConsequenceLedger ledger;
    final List<String> log = new ArrayList<>();
    boolean midtermsHeld;
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
    public EventScheduler scheduler() { return scheduler; }
    public ConsequenceLedger ledger() { return ledger; }
    public List<String> log() { return Collections.unmodifiableList(log); }
    public boolean midtermsHeld() { return midtermsHeld; }
    public Legacy legacy() { return legacy; }
    public int commandsApplied() { return commandsApplied; }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clock", clock.toJson()); m.put("phase", phase.name());
        m.put("economy", economy.toJson()); m.put("budget", budget.toJson()); m.put("politics", politics.toJson());
        m.put("congress", congress.toJson()); m.put("policy", policy.toJson()); m.put("government", government.toJson());
        m.put("diplomacy", diplomacy.toJson()); m.put("military", military.toJson()); m.put("election", election.toJson());
        m.put("scheduler", scheduler.toJson()); m.put("ledger", ledger.toJson()); m.put("log", new ArrayList<>(log));
        m.put("midtermsHeld", midtermsHeld); m.put("legacy", legacy == null ? null : legacy.toJson()); m.put("commandsApplied", commandsApplied);
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
        w.scheduler = EventScheduler.fromJson(Json.object(m.get("scheduler")));
        w.ledger = ConsequenceLedger.fromJson(Json.object(m.get("ledger")));
        for (Object o : Json.array(m.get("log"))) w.log.add((String) o);
        w.midtermsHeld = (Boolean) m.get("midtermsHeld");
        w.legacy = m.get("legacy") == null ? null : Legacy.fromJson(Json.object(m.get("legacy")));
        w.commandsApplied = Json.integer(m.get("commandsApplied"));
        return w;
    }
}
