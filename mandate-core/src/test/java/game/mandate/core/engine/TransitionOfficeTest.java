package game.mandate.core.engine;

import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.time.Phase;
import game.mandate.core.westwing.TransitionState;
import game.mandate.core.westwing.WestWingData;
import game.mandate.core.westwing.WestWingOrder;
import org.junit.jupiter.api.Test;

import static game.mandate.core.engine.EngineFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Milestone 10: the transition office for the White House staff (Resolute Desk's transition). */
class TransitionOfficeTest {

    static GameEngine transition() {
        for (long seed = 1; seed < 80; seed++) {
            GameEngine g = playUntil(new GameEngine(setup(seed)), x -> x.phase() == Phase.TRANSITION);
            if (g.phase() == Phase.TRANSITION) return g;
        }
        throw new AssertionError("no seed reached the transition");
    }

    static Command step(String id, String action) { return new Command.WestWing(new WestWingOrder.TransitionStep(id, action)); }

    @Test
    void shortlistsInterviewsChecksAndAnnouncements() {
        GameEngine g = transition();
        TransitionState t = g.transition().orElseThrow();
        for (int role = 0; role < WestWingData.STAFF_TITLE.length; role++) {
            int n = t.prospects(role).size();
            assertTrue(n >= 3 && n <= 5, "three to five prospects for " + WestWingData.STAFF_TITLE[role]);
        }
        TransitionState.Prospect q = t.prospects(WestWingData.S_COS).get(0);
        double seen = q.skillShown();
        g.apply(step(q.id(), "interview"));
        assertTrue(q.interviewed());
        assertEquals(TransitionState.HOURS - 1, t.hoursLeft(), 1e-9);
        assertNotEquals(-1, q.traitShown(), "an interview shows character");
        assertTrue(Math.abs(q.skillShown() - seen) < 40);
        g.apply(step(q.id(), "check"));
        assertNotNull(q.baggageShown(), "a background check finds what the papers would");
        assertThrows(IllegalCommandException.class, () -> g.apply(step(q.id(), "interview")), "once is enough");
        g.apply(step(q.id(), "announce"));
        assertEquals(q.id(), t.pick(WestWingData.S_COS));
        assertThrows(IllegalCommandException.class, () -> g.apply(step(t.prospects(WestWingData.S_COS).get(1).id(), "announce")), "the job is filled");
        // the oath closes the transition with a grade; day 100 seats the people it chose
        playUntil(g, x -> x.phase() != Phase.TRANSITION);
        assertTrue(t.done());
        assertTrue(t.score() >= 0 && t.score() <= 100);
        playUntil(g, x -> x.westWing().isPresent());
        assertEquals(q.name(), g.westWing().get().staff(WestWingData.S_COS).name(), "the chief of staff you named");
    }

    @Test
    void theOfficeIsOpenOnlyBetweenTheElectionAndTheOath() {
        GameEngine g = governing(3);
        assertThrows(IllegalCommandException.class, () -> g.apply(step("t0", "interview")));
        GameEngine loaded = SaveManager.load(SaveManager.save(g));
        assertEquals(g.transition().orElseThrow().toJson(), loaded.transition().orElseThrow().toJson(), "the transition is in the save");
    }
}
