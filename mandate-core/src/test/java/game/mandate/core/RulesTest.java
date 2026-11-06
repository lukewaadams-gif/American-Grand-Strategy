package game.mandate.core;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.congress.Bill;
import game.mandate.core.congress.BillCatalog;
import game.mandate.core.congress.Congress;
import game.mandate.core.congress.LegislativeEngine;
import game.mandate.core.congress.LegislativeStrategy;
import game.mandate.core.game.Game;
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
        Game g = new Game(DeterminismTest.setup(1));
        int before = g.history().size();
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.BolsterDeterrence()));
        assertEquals(before, g.history().size(), "refused commands are not recorded");
    }

    @Test
    void agendaMustBelongToThePlayersParty() {
        Game g = reachTransition();
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.SetAgenda(List.of("tax_cut"))));
        g.apply(new Command.SetAgenda(List.of("lower_costs", "housing")));
        assertEquals(List.of("lower_costs", "housing"), g.agenda());
    }

    @Test
    void filibusterNeedsSixtyButReconciliationNeedsFifty() {
        LegislativeEngine e = new LegislativeEngine();
        Bill b = BillCatalog.load().get("lower_costs");
        Congress c = new Congress(225, 52);
        assertEquals(60, e.senateThreshold(b, LegislativeStrategy.PARTY_LINE, c));
        assertEquals(50, e.senateThreshold(b, LegislativeStrategy.RECONCILIATION, c));
    }

    @Test
    void bipartisanDealsWinMoreVotesInADividedCongress() {
        LegislativeEngine e = new LegislativeEngine();
        Bill b = BillCatalog.load().get("lower_costs");
        Congress divided = new Congress(205, 47);
        double partyLine = e.whip(b, LegislativeStrategy.PARTY_LINE, divided, Party.DEMOCRAT, 48, 0).passChance();
        double deal = e.whip(b, LegislativeStrategy.BIPARTISAN_DEAL, divided, Party.DEMOCRAT, 48, 0).passChance();
        assertTrue(deal > partyLine, "deal " + deal + " vs party line " + partyLine);
    }

    @Test
    void politicalCapitalRaisesTheWhipCount() {
        LegislativeEngine e = new LegislativeEngine();
        Bill b = BillCatalog.load().get("housing");
        Congress c = new Congress(222, 51);
        double none = e.whip(b, LegislativeStrategy.PARTY_LINE, c, Party.DEMOCRAT, 47, 0).senateExpected();
        double lots = e.whip(b, LegislativeStrategy.PARTY_LINE, c, Party.DEMOCRAT, 47, 40).senateExpected();
        assertTrue(lots > none);
    }

    /** Plays seeds until the player wins 2028 and reaches the transition. */
    static Game reachTransition() {
        for (long seed = 1; seed < 200; seed++) {
            Game g = new Game(DeterminismTest.setup(seed));
            while (!g.over() && g.phase() == Phase.GENERAL_CAMPAIGN_2028) AutoPilot.playTurn(g);
            if (g.phase() == Phase.TRANSITION) {
                for (var p : g.pendingEvents()) g.apply(new Command.ResolveEvent(p.instanceId(), 0));
                return g;
            }
        }
        throw new AssertionError("no seed reached the transition");
    }
}
