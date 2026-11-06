package game.mandate.core;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.congress.Bill;
import game.mandate.core.congress.BillCatalog;
import game.mandate.core.congress.CongressEngine;
import game.mandate.core.congress.CongressState;
import game.mandate.core.congress.LegislativeContext;
import game.mandate.core.data.Tuning;
import game.mandate.core.politics.OppositionStrategy;
import game.mandate.core.congress.LegislativeStrategy;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.state.Party;
import game.mandate.core.time.Phase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesTest {

    @Test
    void governingCommandsAreRefusedDuringTheCampaign() {
        GameEngine g = new GameEngine(DeterminismTest.setup(1));
        int before = g.history().size();
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.BolsterDeterrence()));
        assertEquals(before, g.history().size(), "refused commands are not recorded");
    }

    @Test
    void agendaMustBelongToThePlayersParty() {
        GameEngine g = reachTransition();
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.SetAgenda(List.of("tax_cut"))));
        g.apply(new Command.SetAgenda(List.of("lower_costs", "housing")));
        assertEquals(List.of("lower_costs", "housing"), g.agenda());
    }

    static final CongressEngine E = new CongressEngine(Tuning.defaults());

    static LegislativeContext ctx(double approval) {
        return new LegislativeContext(Party.DEMOCRAT, approval, 50, OppositionStrategy.SELECTIVE, new double[]{60, 60}, false);
    }

    @Test
    void filibusterNeedsSixtyButReconciliationNeedsFifty() {
        CongressState c = E.newCongress(225, 52);
        assertEquals(60, E.senateNeeded(c, LegislativeStrategy.PARTY_LINE));
        assertEquals(50, E.senateNeeded(c, LegislativeStrategy.RECONCILIATION));
    }

    @Test
    void bipartisanDealsWinMoreVotesInADividedCongress() {
        Bill b = BillCatalog.load().get("lower_costs");
        CongressState divided = E.newCongress(205, 47);
        double partyLine = E.whip(divided, b, LegislativeStrategy.PARTY_LINE, ctx(48), 0).passChance();
        double deal = E.whip(divided, b, LegislativeStrategy.BIPARTISAN_DEAL, ctx(48), 0).passChance();
        assertTrue(deal > partyLine, "deal " + deal + " vs party line " + partyLine);
    }

    @Test
    void politicalCapitalRaisesTheWhipCount() {
        Bill b = BillCatalog.load().get("housing");
        CongressState c = E.newCongress(222, 51);
        double none = E.whip(c, b, LegislativeStrategy.PARTY_LINE, ctx(47), 0).senateExpected();
        double lots = E.whip(c, b, LegislativeStrategy.PARTY_LINE, ctx(47), 40).senateExpected();
        assertTrue(lots > none);
    }

    /** Plays seeds until the player wins 2028 and reaches the transition. */
    static GameEngine reachTransition() {
        for (long seed = 1; seed < 200; seed++) {
            GameEngine g = new GameEngine(DeterminismTest.setup(seed));
            while (!g.over() && g.phase() == Phase.GENERAL_CAMPAIGN_2028) AutoPilot.playTurn(g);
            if (g.phase() == Phase.TRANSITION) {
                for (var p : g.pendingEvents()) g.apply(new Command.ResolveEvent(p.instanceId(), 0));
                return g;
            }
        }
        throw new AssertionError("no seed reached the transition");
    }
}
