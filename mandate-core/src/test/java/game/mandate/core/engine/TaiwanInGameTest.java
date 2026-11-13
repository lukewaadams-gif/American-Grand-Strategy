package game.mandate.core.engine;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.command.Command;
import game.mandate.core.taiwan.TaiwanOrder;
import game.mandate.core.time.SimulationClock;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;

import static game.mandate.core.engine.EngineFixtures.governing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Taiwan crisis inside the presidency (docs/ISLAND_CHAIN.md): its clock, its orders, its end, and saves taken during it. */
class TaiwanInGameTest {

    @Test
    void aCrisisRunsInDaysAndHoursAndTheWeeksResumeAfterIt() {
        GameEngine g = governing(5);
        double oil0 = g.state().economy().oil();
        g.apply(new Command.TaiwanBegin(2));
        assertTrue(g.taiwanActive());
        assertEquals(SimulationClock.Granularity.DAILY, g.clock().mode(), "the crisis is played a day at a time");
        assertThrows(game.mandate.core.command.IllegalCommandException.class, () -> g.apply(new Command.TaiwanBegin(0)), "one crisis at a time");
        var start = g.clock().now();
        g.apply(new Command.EndTurn());
        assertEquals(24, java.time.Duration.between(start, g.clock().now()).toHours());
        boolean sawWar = false;
        int guard = 0;
        while (g.taiwanActive() && guard++ < 1500) {
            if (g.taiwanAtWar()) {
                sawWar = true;
                assertEquals(SimulationClock.Granularity.OPERATIONAL, g.clock().mode(), "the war is played in hours");
            }
            AutoPilot.playTurn(g);
        }
        assertFalse(g.taiwanActive(), "the crisis ends");
        var t = g.taiwan().orElseThrow();
        assertNotNull(t.outcome());
        assertEquals(SimulationClock.Granularity.WEEKLY, g.clock().mode(), "the weeks resume");
        assertEquals(DayOfWeek.MONDAY, g.date().getDayOfWeek());
        if (sawWar) {
            assertFalse(g.wars().isEmpty(), "the war is on the record");
            assertEquals(t.usKilled(), g.wars().get(g.wars().size() - 1).usKilled());
        }
        assertTrue(g.state().economy().oil() != oil0, "the crisis reached the economy");
        AutoPilot.playTurn(g);
        assertFalse(g.taiwanActive());
    }

    @Test
    void ordersAreHeldFromTheStaffAndSavesTakenDuringTheCrisisReplay() {
        GameEngine g = governing(7);
        g.apply(new Command.TaiwanBegin(0));
        g.apply(new Command.Taiwan(new TaiwanOrder.Fleet(1)));
        g.apply(new Command.Taiwan(new TaiwanOrder.TaiwanAsk("ration")));
        var t = g.taiwan().orElseThrow();
        assertEquals(1, t.fleetPosture());
        assertTrue(t.heldDirectives().contains("fleet"));
        assertNotNull(t.message(), "the room is told what the order did");
        assertThrows(game.mandate.core.command.IllegalCommandException.class, () -> g.apply(new Command.Taiwan(new TaiwanOrder.TaiwanAsk("ration"))));
        for (int i = 0; i < 12 && g.taiwanActive(); i++) AutoPilot.playTurn(g);
        String save = SaveManager.save(g);
        assertTrue(SaveManager.consistent(save), "the snapshot equals a replay of its commands");
        GameEngine loaded = SaveManager.load(save);
        int from = g.history().size();
        for (int i = 0; i < 25 && g.taiwanActive(); i++) AutoPilot.playTurn(g);
        for (Command c : g.history().subList(from, g.history().size())) loaded.apply(c);
        assertEquals(game.mandate.core.data.Json.write(g.state().toJson()), game.mandate.core.data.Json.write(loaded.state().toJson()));
        g.apply(new Command.Taiwan(new TaiwanOrder.Playbook()));
        assertTrue(g.taiwan().orElseThrow().heldDirectives().isEmpty(), "the playbook hands everything back to the staff");
    }
}
