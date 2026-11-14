package game.mandate.core.engine;

import game.mandate.core.campaign.CampaignOrder;
import game.mandate.core.campaign.CampaignStaff;
import game.mandate.core.campaign.ElectionState;
import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.game.GameSetup;
import game.mandate.core.game.Scenario;
import game.mandate.core.state.Party;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The campaign's team and surrogates (docs/CAMPAIGN_WAR_ROOM.md, "Team"). */
class CampaignTeamTest {

    static GameSetup real(long seed) {
        return new GameSetup(seed, Party.DEMOCRAT, "bg_shapiro", "bg_vance", Scenario.TOSS_UP, GameSetup.Difficulty.STANDARD, GameSetup.Mode.FULL_GAME, true, null);
    }

    static void week(GameEngine g) { EngineFixtures.answerAll(g, 0); g.apply(new Command.EndTurn()); }

    @Test
    void eachTicketHasARunningMateAndAlliesOfItsOwnPartyAndCast() {
        GameEngine g = new GameEngine(real(3));
        week(g);
        for (var side : new game.mandate.core.campaign.CampaignSide[]{g.election().player(), g.election().opponent()}) {
            assertNotNull(side.runningMate(), "a running mate");
            assertEquals(2, side.allies().size(), "two allies");
            for (var c : java.util.List.of(side.runningMate(), side.allies().get(0), side.allies().get(1))) {
                assertEquals(side.party(), c.party());
                assertTrue(c.isReal(), "the real cast: " + c.id());
                assertNotEquals(side.candidate().id(), c.id());
            }
        }
        // the staff tours them: every surrogate has a week's assignment
        for (int slot = 0; slot < CampaignStaff.SURROGATES; slot++)
            assertNotEquals(CampaignStaff.NONE, g.election().player().orders().surrogate(slot), "slot " + slot);
        // the fictional cast gets fictional surrogates
        GameEngine f = new GameEngine(EngineFixtures.setup(3));
        week(f);
        assertFalse(f.election().player().runningMate().isReal());
    }

    @Test
    void aRunningMateAHireAndASurrogatesWeekAreThePlayers() {
        GameEngine g = new GameEngine(real(4)), twin = new GameEngine(real(4));
        week(g); week(twin);
        String mate = CampaignStaff.shortlist(g.candidates(), g.player().candidate(), g.opponent().candidate()).get(2).id();
        g.apply(new Command.Campaign(new CampaignOrder.RunningMate(mate)));
        assertEquals(mate, g.election().player().runningMate().id());
        for (var a : g.election().player().allies()) assertNotEquals(mate, a.id(), "the running mate is not also an ally");
        // a hire: the signing fee, and the post held
        double cash = g.election().player().cash();
        g.apply(new Command.Campaign(new CampaignOrder.Hire(CampaignStaff.Post.MANAGER, "d_plouffe")));
        assertEquals("d_plouffe", g.election().player().staffAt(CampaignStaff.Post.MANAGER));
        assertEquals(cash - 2 * g.campaignStaff().get("d_plouffe").salary(), g.election().player().cash(), 1e-9);
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Campaign(new CampaignOrder.Hire(CampaignStaff.Post.MANAGER, "r_lacivita"))),
                "the other party's operative");
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Campaign(new CampaignOrder.Hire(CampaignStaff.Post.POLLSTER, "d_plouffe"))),
                "the wrong post");
        GameEngine fictional = new GameEngine(EngineFixtures.setup(3));
        assertThrows(IllegalCommandException.class, () -> fictional.apply(new Command.Campaign(new CampaignOrder.Hire(CampaignStaff.Post.MANAGER, "d_plouffe"))),
                "real people only with the real cast");
        // a surrogate's week, by hand: the staff no longer plans them
        g.apply(new Command.Campaign(new CampaignOrder.Surrogate(0, "PA", "f_adv_1")));
        assertFalse(g.election().staffRuns(ElectionState.StaffArea.SURROGATES));
        int pa = g.campaignEngine().geography().index("PA");
        assertEquals(pa, g.election().player().orders().surrogate(0));
        assertEquals("f_adv_1", g.election().player().orders().tourLead(0));
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Campaign(new CampaignOrder.Surrogate(0, "PA", "d_plouffe"))), "not an advance lead");
        String saved = SaveManager.save(g);
        assertTrue(SaveManager.consistent(saved));
        GameEngine back = SaveManager.load(saved);
        assertEquals(mate, back.election().player().runningMate().id());
        assertEquals("d_plouffe", back.election().player().staffAt(CampaignStaff.Post.MANAGER));
        assertEquals(pa, back.election().player().orders().surrogate(0));
        // the week runs, and the running mate's stop counts in Pennsylvania
        double before = g.election().player().visits("PA");
        week(g);
        assertEquals(pa, g.election().player().orders().surrogate(0), "the player's plan stands");
        assertTrue(g.election().player().visits("PA") > 0 && before >= 0);
        // the hired manager drives the message harder than the default staff over a few weeks
        for (int i = 0; i < 3; i++) { week(g); week(twin); }
        assertNotEquals(twin.election().player().momentum(), g.election().player().momentum());
    }
}
