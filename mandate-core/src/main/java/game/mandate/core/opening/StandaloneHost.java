package game.mandate.core.opening;

import game.mandate.core.campaign.CampaignParams;
import game.mandate.core.campaign.CandidateCatalog;
import game.mandate.core.campaign.ElectionEngine;
import game.mandate.core.campaign.Issue;
import game.mandate.core.congress.Bill;
import game.mandate.core.congress.CongressEngine;
import game.mandate.core.congress.CongressState;
import game.mandate.core.congress.PolicyState;
import game.mandate.core.data.Tuning;
import game.mandate.core.diplomacy.Country;
import game.mandate.core.diplomacy.DiplomacyEngine;
import game.mandate.core.diplomacy.DiplomacyState;
import game.mandate.core.economy.BudgetState;
import game.mandate.core.economy.EconomyEngine;
import game.mandate.core.economy.EconomyParams;
import game.mandate.core.politics.OpinionInputs;
import game.mandate.core.economy.EconomyState;
import game.mandate.core.effects.Effect;
import game.mandate.core.electorate.StateCatalog;
import game.mandate.core.executive.CabinetPool;
import game.mandate.core.executive.ExecutiveEngine;
import game.mandate.core.executive.GovernmentState;
import game.mandate.core.politics.PoliticsEngine;
import game.mandate.core.politics.PoliticsParams;
import game.mandate.core.politics.PoliticsState;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.state.Party;
import game.mandate.core.time.CampaignCalendar;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A self-contained world for the 12-week design prototype and its strategy probes: the real engines over fresh
 * domain states, a generic president and a synthetic flagship bill whose numbers match the original prototype
 * (a medium, borrowed bill: 0.30% of GDP, a 0.2-point demand impulse, ideology 0.45 from the centre).
 */
final class StandaloneHost implements Host {
    /** The prototype has no candidate; addresses use an average president's charisma. */
    static final double PRESIDENT_CHARISMA = 6.0;

    private final Party party;
    private final Bill flagship;
    private final int weeks;
    private final LocalDate start;
    private final Tuning tuning = Tuning.defaults();
    private final DeterministicRandomManager rng;
    private final PoliticsEngine politicsEngine = new PoliticsEngine(PoliticsParams.from(tuning));
    private final EconomyEngine economyEngine = new EconomyEngine(EconomyParams.from(tuning));
    private final DiplomacyEngine diplomacyEngine = new DiplomacyEngine(tuning, Country.load());
    private final CongressEngine congressEngine = new CongressEngine(tuning);
    private final ExecutiveEngine executiveEngine = new ExecutiveEngine(tuning, CabinetPool.load());
    private final ElectionEngine elections = new ElectionEngine(StateCatalog.load(), CandidateCatalog.load(), CampaignParams.from(tuning));
    private final CampaignCalendar calendar = CampaignCalendar.load();
    private final PoliticsState politics;
    private final EconomyState economy;
    private final BudgetState budget;
    private final DiplomacyState diplomacy;
    private final CongressState congress;
    private final PolicyState policy;
    private final game.mandate.core.policy.PolicyTableEngine policyTableEngine = new game.mandate.core.policy.PolicyTableEngine(
            game.mandate.core.policy.PolicyCatalog.shared(), EconomyParams.from(tuning).nominalGdp());
    private final game.mandate.core.policy.PolicyTableState policyTable = policyTableEngine.newTable();
    private final GovernmentState government;

    StandaloneHost(OpeningSetup setup, int weeks, LocalDate start) {
        this.party = setup.party();
        this.weeks = weeks;
        this.start = start;
        this.rng = new DeterministicRandomManager(setup.seed());
        boolean d = party == Party.DEMOCRAT;
        this.congress = setup.unified() ? congressEngine.newCongress(d ? 222 : 213, d ? 51 : 49)
                                        : congressEngine.newCongress(d ? 208 : 227, d ? 48 : 52);
        this.policy = congressEngine.newPolicy();
        this.politics = politicsEngine.newPolitics(CandidateCatalog.load(), 0);
        this.economy = economyEngine.newEconomy();
        this.budget = economyEngine.newBudget();
        this.diplomacy = diplomacyEngine.newDiplomacy();
        this.government = executiveEngine.newGovernment();
        this.flagship = new Bill("prototype_flagship", d ? "Families First Act" : "Working America Act", Optional.of(party),
                -0.45 * party.sign(), Issue.COST_OF_LIVING, 300, 2.0, 0.20, 0.0, 0.30, false);
        Effect oath = new Effect.PresidentInaugurated("prototype-president", party, 270);
        executiveEngine.apply(government, oath, start);
        politicsEngine.apply(politics, oath);
        politicsEngine.setInitialTerms(politics, congress.congress().unified(party));
        // an average president, a narrow win, no state map: approval starts at Resolute Desk's target
        EconomyParams ep = economyEngine.params();
        politicsEngine.beginApproval(politics, new PoliticsEngine.ApprovalStart(0, 0, 1.0, java.util.Map.of(), java.util.Map.of(), 30 + (PRESIDENT_CHARISMA - 1) * 100 / 9 * 0.2),
                new OpinionInputs(economyEngine.gas(economy), economyEngine.normalGas(economy), economy.inflation(), economy.unemployment(), economy.mortgageRate(ep),
                        economy.sentiment(), economy.growth()));
    }

    @Override public Party party() { return party; }
    @Override public String presidentName() { return "The president"; }
    @Override public double presidentCharisma() { return PRESIDENT_CHARISMA; }
    @Override public Bill flagship() { return flagship; }
    @Override public LocalDate start() { return start; }
    @Override public int weeks() { return weeks; }
    @Override public PoliticsState politics() { return politics; }
    @Override public PoliticsEngine politicsEngine() { return politicsEngine; }
    @Override public EconomyState economy() { return economy; }
    @Override public BudgetState budget() { return budget; }
    @Override public EconomyEngine economyEngine() { return economyEngine; }
    @Override public DiplomacyState diplomacy() { return diplomacy; }
    @Override public DiplomacyEngine diplomacyEngine() { return diplomacyEngine; }
    @Override public CongressState congress() { return congress; }
    @Override public PolicyState policy() { return policy; }
    @Override public CongressEngine congressEngine() { return congressEngine; }
    @Override public GovernmentState government() { return government; }
    @Override public ExecutiveEngine executiveEngine() { return executiveEngine; }
    @Override public ElectionEngine elections() { return elections; }
    @Override public CampaignCalendar calendar() { return calendar; }
    @Override public DeterministicRandomManager rng() { return rng; }
    @Override public Tuning tuning() { return tuning; }

    /** The same ownership as the game engine's router, without a ledger. */
    @Override
    public void dispatch(String cause, Effect e) {
        switch (e) {
            case Effect.Approval x -> politicsEngine.apply(politics, x);
            case Effect.CoalitionApproval x -> politicsEngine.apply(politics, x);
            case Effect.Capital x -> politicsEngine.apply(politics, x);
            case Effect.Salience x -> politicsEngine.apply(politics, x);
            case Effect.Goodwill x -> politicsEngine.apply(politics, x);
            case Effect.WingMood x -> politicsEngine.apply(politics, x);
            case Effect.IssueWork x -> politicsEngine.apply(politics, x);
            case Effect.Remarks x -> politicsEngine.apply(politics, x);
            case Effect.IssueCommand x -> politicsEngine.apply(politics, x);
            case Effect.Performance x -> politicsEngine.apply(politics, x);
            case Effect.StateStanding x -> politicsEngine.apply(politics, x);
            case Effect.Rally x -> politicsEngine.apply(politics, x);
            case Effect.Scandal x -> politicsEngine.apply(politics, x);
            case Effect.EconomyShock x -> economyEngine.apply(economy, budget, x);
            case Effect.Deficit x -> economyEngine.apply(economy, budget, x);
            case Effect.FedPressure x -> economyEngine.apply(economy, budget, x);
            case Effect.OilRelease x -> economyEngine.apply(economy, budget, x);
            case Effect.PolicyStep x -> economyEngine.apply(economy, budget, x);
            case Effect.FinancialShock x -> economyEngine.apply(economy, budget, x);
            case Effect.LawPassed x -> {
                var passed = policyTableEngine.enactBill(policyTable, x, start, rng());
                for (Effect y : passed.effects()) dispatch(cause, y);
                if (x.react() && passed.reaction() != 0) dispatch(cause, new Effect.Approval(passed.reaction()));
            }
            case Effect.Tension x -> diplomacyEngine.apply(diplomacy, x);
            case Effect.Deterrence x -> diplomacyEngine.apply(diplomacy, x);
            case Effect.WarEnded x -> diplomacyEngine.apply(diplomacy, x);
            case Effect.CongressElected x -> congressEngine.apply(congress, x);
            case Effect.CampaignSwing x -> throw new IllegalStateException("No campaign in the governing prototype");
            case Effect.Cash x -> throw new IllegalStateException("No campaign in the governing prototype");
            case Effect.Munitions x -> throw new IllegalStateException("No military in the governing prototype");
            case Effect.PresidentInaugurated x -> throw new IllegalStateException("The prototype president is already in office");
        }
    }
}
