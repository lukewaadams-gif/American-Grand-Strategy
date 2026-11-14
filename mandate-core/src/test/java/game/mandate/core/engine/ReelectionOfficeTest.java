package game.mandate.core.engine;

import game.mandate.core.command.Command;
import game.mandate.core.time.Phase;
import org.junit.jupiter.api.Test;

import static game.mandate.core.engine.EngineFixtures.playUntil;
import static game.mandate.core.engine.EngineFixtures.setup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The presidency does not stop for the 2032 campaign (docs/MODES.md, "The 2032 campaign"). */
class ReelectionOfficeTest {

    @Test
    void congressTheWestWingAndTheWorldGoOnThroughTheCampaign() {
        GameEngine g = null;
        for (long seed = 1; seed < 40 && g == null; seed++) {
            GameEngine x = playUntil(new GameEngine(setup(seed)), y -> y.phase() == Phase.REELECTION_CAMPAIGN_2032 || y.over());
            if (x.phase() == Phase.REELECTION_CAMPAIGN_2032 && !x.taiwanActive() && !x.hormuzActive()) g = x;
        }
        assertTrue(g != null, "a game reaches the 2032 campaign");
        assertTrue(g.inOffice(), "the president is in office while campaigning");
        int hill = g.hill().orElseThrow().week(), westWing = g.westWing().orElseThrow().week(), world = g.foreign().orElseThrow().week();
        int indicators = g.state().indicators.size();
        int weeks = 0;
        for (int turn = 0; turn < 40 && weeks < 6 && g.phase() == Phase.REELECTION_CAMPAIGN_2032; turn++) {
            boolean crisis = g.taiwanActive() || g.hormuzActive();
            EngineFixtures.answerAll(g, 0);
            if (g.debateThisWeek().isPresent() && g.election().pendingPosture() == null)
                g.apply(new Command.ChooseDebatePosture(game.mandate.core.campaign.DebatePosture.values()[0]));
            g.apply(new Command.EndTurn());
            if (!crisis) weeks++;
        }
        assertTrue(weeks >= 5, "campaign weeks played: " + weeks);
        assertTrue(g.hill().orElseThrow().week() >= hill + 5, "Congress moved on: " + hill + " -> " + g.hill().orElseThrow().week());
        assertTrue(g.westWing().orElseThrow().week() >= westWing + 5, "the West Wing moved on");
        assertTrue(g.foreign().orElseThrow().week() >= world + 5, "the world moved on");
        assertTrue(g.state().indicators.size() >= indicators + 5, "opinion and the economy are recorded each week");
        // the save replays the campaign's weeks in office
        String saved = SaveManager.save(g);
        assertTrue(SaveManager.consistent(saved));
        assertEquals(g.fingerprint(), SaveManager.load(saved).fingerprint());
    }
}
