package game.mandate.core.opening;

import game.mandate.core.campaign.ElectionEngine;
import game.mandate.core.congress.Bill;
import game.mandate.core.congress.CongressEngine;
import game.mandate.core.congress.CongressState;
import game.mandate.core.congress.PolicyState;
import game.mandate.core.data.Tuning;
import game.mandate.core.diplomacy.DiplomacyEngine;
import game.mandate.core.diplomacy.DiplomacyState;
import game.mandate.core.economy.BudgetState;
import game.mandate.core.economy.EconomyEngine;
import game.mandate.core.economy.EconomyState;
import game.mandate.core.effects.Effect;
import game.mandate.core.executive.ExecutiveEngine;
import game.mandate.core.executive.GovernmentState;
import game.mandate.core.politics.PoliticsEngine;
import game.mandate.core.politics.PoliticsState;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.state.Party;
import game.mandate.core.time.CampaignCalendar;

import java.time.LocalDate;

/**
 * What the first hundred days need from the world they run in. The game engine provides one over its
 * {@code WorldState} (so the hundred days are part of one continuous game and one save); a standalone host provides
 * a self-contained world for the design prototype and its strategy probes. Either way the hundred days own none of
 * the domain formulas: they read domain states and change them through {@link #dispatch} or the owning engines.
 */
public interface Host {
    Party party();
    String presidentName();
    double presidentCharisma();
    /** The catalog bill the flagship is built from: the president's first agenda priority. */
    Bill flagship();
    /** The Monday the first week starts. */
    LocalDate start();
    /** Length of the period, in weeks. */
    int weeks();

    PoliticsState politics();
    PoliticsEngine politicsEngine();
    EconomyState economy();
    BudgetState budget();
    EconomyEngine economyEngine();
    DiplomacyState diplomacy();
    DiplomacyEngine diplomacyEngine();
    CongressState congress();
    PolicyState policy();
    CongressEngine congressEngine();
    GovernmentState government();
    ExecutiveEngine executiveEngine();
    ElectionEngine elections();
    CampaignCalendar calendar();
    DeterministicRandomManager rng();
    Tuning tuning();

    /** Routes one transition to the engine that owns the state it changes (recorded in the ledger when hosted by the game). */
    void dispatch(String cause, Effect effect);
}
