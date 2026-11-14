package game.mandate.core.engine;

import game.mandate.core.campaign.AirWar;
import game.mandate.core.campaign.CampaignOrder;
import game.mandate.core.campaign.ElectionEngine;
import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.time.Phase;
import org.junit.jupiter.api.Test;

import static game.mandate.core.engine.EngineFixtures.setup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The war room's Message and Polls tabs and the Voters desk (docs/CAMPAIGN_WAR_ROOM.md). */
class WarRoomDeskTest {

    @Test
    void anInternalPollIsCloseToTheTruthCostsMoneyAndIsSaved() {
        GameEngine g = new GameEngine(setup(3));
        assertEquals(Phase.GENERAL_CAMPAIGN_2028, g.phase());
        double cash = g.election().player().cash();
        int sign = g.player().party().sign();
        g.apply(new Command.Campaign(new CampaignOrder.InternalPoll("PA")));
        g.apply(new Command.Campaign(new CampaignOrder.InternalPoll("US")));
        var polls = g.election().internalPolls();
        assertEquals(2, polls.size());
        assertEquals(cash - ElectionEngine.STATE_POLL_COST - ElectionEngine.NATIONAL_POLL_COST, g.election().player().cash(), 1e-9, "paid from the campaign's cash");
        double truth = g.expectedMargins().get("PA") * sign;
        assertTrue(Math.abs(polls.get(0).margin() - truth) < 4 * polls.get(0).sd(), "close to the truth: " + polls.get(0).margin() + " vs " + truth);
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Campaign(new CampaignOrder.InternalPoll("PA"))), "once a week");
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Campaign(new CampaignOrder.InternalPoll("XX"))), "a real state");
        String saved = SaveManager.save(g);
        assertTrue(SaveManager.consistent(saved), "the poll replays to the same number");
        assertEquals(polls, SaveManager.load(saved).election().internalPolls());
        // a week later the state can be polled again
        EngineFixtures.answerAll(g, 0);
        g.apply(new Command.EndTurn());
        g.apply(new Command.Campaign(new CampaignOrder.InternalPoll("PA")));
        assertEquals(3, g.election().internalPolls().size());
    }

    @Test
    void theMessagesToneIsSavedAndChangesTheRace() {
        GameEngine contrast = new GameEngine(setup(5)), attack = new GameEngine(setup(5)), positive = new GameEngine(setup(5));
        attack.apply(new Command.Campaign(new CampaignOrder.MessageTone(AirWar.Tone.NEGATIVE)));
        positive.apply(new Command.Campaign(new CampaignOrder.MessageTone(AirWar.Tone.POSITIVE)));
        assertEquals(AirWar.Tone.NEGATIVE, attack.election().player().orders().messageTone(), "the tone is set");
        assertEquals(AirWar.Tone.NEGATIVE, SaveManager.load(SaveManager.save(attack)).election().player().orders().messageTone(), "the tone is saved");
        assertEquals(AirWar.Tone.CONTRAST, contrast.election().player().orders().messageTone(), "contrast is the default");
        double enthusiasm = contrast.election().opponent().enthusiasm();
        for (GameEngine g : new GameEngine[]{contrast, attack, positive})
            for (int i = 0; i < 4; i++) { EngineFixtures.answerAll(g, 0); g.apply(new Command.EndTurn()); }
        assertNotEquals(contrast.election().player().momentum(), attack.election().player().momentum(), "an attack moves the race differently");
        assertTrue(attack.election().opponent().enthusiasm() < contrast.election().opponent().enthusiasm(), "an attack dampens their side");
        assertTrue(positive.election().player().enthusiasm() > contrast.election().player().enthusiasm(), "a positive message lifts yours");
        assertTrue(enthusiasm > 0);
    }

    @Test
    void aStatesVotersAddUpToItsPoll() {
        GameEngine g = new GameEngine(setup(3));
        EngineFixtures.answerAll(g, 0);
        g.apply(new Command.EndTurn());
        var groups = g.stateGroups("PA");
        assertTrue(groups.size() >= 8, "the blocs: " + groups.size());
        double share = 0, margin = 0;
        for (var x : groups) { share += x.share(); margin += x.share() * x.margin(); assertTrue(x.turnout() > 0 && x.turnout() <= 1 && x.movable() > 0); }
        assertEquals(1.0, share, 1e-6, "the groups are the whole electorate");
        // weighted by votes cast, the groups come to the state's poll (to within the turnout weighting's rounding)
        assertEquals(g.statePolls().get("PA"), margin, 3.0, "the groups agree with the poll");
    }
}
