package game.mandate.core.engine;

import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.hormuz.HormuzOrder;
import game.mandate.core.hormuz.HormuzState;
import game.mandate.core.time.SimulationClock.Granularity;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static game.mandate.core.engine.EngineFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** The Strait of Hormuz crisis (docs/HORMUZ.md). */
class HormuzTest {

    static GameEngine crisis(long seed, int preset) {
        GameEngine g = governing(seed);
        g.apply(new Command.Hormuz(new HormuzOrder.Begin(preset)));
        assertTrue(g.hormuzActive());
        return g;
    }

    static void day(GameEngine g) { answerAll(g, 0); g.apply(new Command.EndTurn()); }

    static void quiet(GameEngine g, HormuzOrder o) { try { g.apply(new Command.Hormuz(o)); } catch (IllegalCommandException e) { /* not yet */ } }

    @Test
    void theCrisisRunsInDays() {
        GameEngine g = crisis(3, 0);
        HormuzState h = g.hormuz().orElseThrow();
        assertEquals(Granularity.DAILY, g.clock().mode());
        assertEquals(9, h.sites().size());
        assertEquals(4, h.fields().size());
        assertTrue(h.rung() >= 1 && h.premium() > 0, "the strait's trouble is in the oil price from the first day");
        LocalDate d0 = g.date();
        day(g);
        assertEquals(d0.plusDays(1), g.date());
        assertEquals(1, h.day());
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Hormuz(new HormuzOrder.Begin(0))), "one crisis at a time");
    }

    @Test
    void leftAloneTheStraitCloses() {
        GameEngine g = crisis(5, 0);
        double oil = g.state().economy().oil(), approval = g.approval();
        while (g.hormuzActive()) day(g);
        HormuzState h = g.hormuz().orElseThrow();
        assertEquals("A stalemate", h.outcome());
        assertEquals(5, h.peakRung(), "the Guards climb to closure when nobody stops them");
        assertTrue(h.tankersSeized() > 10);
        assertTrue(g.state().economy().oil() > oil + 50, "oil soars");
        assertTrue(g.approval() < approval - 5, "and the country blames the president");
        assertEquals(Granularity.WEEKLY, g.clock().mode());
        assertEquals(DayOfWeek.MONDAY, g.date().getDayOfWeek(), "the weeks resume on a Monday");
    }

    @Test
    void escortsAndTheReservesEaseThePrice() {
        GameEngine a = crisis(7, 0), b = crisis(7, 0);
        b.apply(new Command.Hormuz(new HormuzOrder.Escorts(3)));
        b.apply(new Command.Hormuz(new HormuzOrder.Oil("spr", 2)));
        b.apply(new Command.Hormuz(new HormuzOrder.Oil("bypass", 0)));
        b.apply(new Command.Hormuz(new HormuzOrder.Oil("reinsure", 0)));
        for (int i = 0; i < 30; i++) { day(a); day(b); }
        assertTrue(b.hormuz().get().premium() < a.hormuz().get().premium(), "a smaller premium");
        assertTrue(b.hormuz().get().tankersSeized() < a.hormuz().get().tankersSeized(), "fewer seizures under escort");
        assertTrue(b.hormuz().get().sprLeft() < HormuzState.SPR_START, "the reserve is drawn");
        assertTrue(b.hormuz().get().bypass() > 0, "the pipelines run");
    }

    @Test
    void theBackChannelSettlesIt() {
        GameEngine g = crisis(3, 0);
        g.apply(new Command.Hormuz(new HormuzOrder.Sweep(2)));
        g.apply(new Command.Hormuz(new HormuzOrder.Envoy("oman")));
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Hormuz(new HormuzOrder.Envoy("qatar"))), "the envoy takes days");
        while (g.hormuzActive() && g.hormuz().get().day() < 120) { quiet(g, new HormuzOrder.Envoy("oman")); quiet(g, new HormuzOrder.Envoy("china")); day(g); }
        HormuzState h = g.hormuz().orElseThrow();
        assertFalse(h.active());
        assertTrue(h.settled(), "a deal");
        assertTrue(h.day() < 70, "in weeks, not months: " + h.day());
        int ir = g.foreignEngine().data().index("IR");
        assertTrue(g.foreign().get().sanction(ir) <= 1, "the price of the deal: sanctions on Iran eased");
        assertTrue(g.foreign().get().record().stream().anyMatch(r -> r.contains("Strait of Hormuz")), "the world remembers the crisis");
    }

    @Test
    void strikesWearDownTheCoastButAngerTheGuards() {
        GameEngine g = crisis(11, 0);
        HormuzState h = g.hormuz().orElseThrow();
        double seen = h.sites().stream().filter(s -> s.kind().equals("missiles")).mapToDouble(HormuzState.Site::strength).sum();
        g.apply(new Command.Hormuz(new HormuzOrder.Strike("missiles")));
        assertTrue(h.sites().stream().filter(s -> s.kind().equals("missiles")).mapToDouble(HormuzState.Site::strength).sum() < seen - 60);
        assertTrue(h.anger() > 0.2, "the Guards push back");
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Hormuz(new HormuzOrder.Strike("lighthouses"))));
        for (int d = 0; d < 12; d += 3) {
            quiet(g, new HormuzOrder.Strike("boats")); quiet(g, new HormuzOrder.Strike("mines")); quiet(g, new HormuzOrder.Strike("missiles"));
            for (int k = 0; k < 3; k++) day(g);
        }
        quiet(g, new HormuzOrder.Escorts(3)); quiet(g, new HormuzOrder.Sweep(3));
        for (int k = 0; k < 3; k++) quiet(g, new HormuzOrder.Envoy("allies"));
        while (g.hormuzActive()) day(g);
        assertEquals("Shipping restored", h.outcome(), "with little left to fight with, the Guards step back and the lanes are swept");
    }

    @Test
    void seizingTheIslands() {
        GameEngine g = crisis(7, 0);
        g.apply(new Command.Hormuz(new HormuzOrder.Invade(1)));
        HormuzState h = g.hormuz().orElseThrow();
        assertEquals(1, h.invasion());
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Hormuz(new HormuzOrder.Invade(0))), "one operation at a time");
        int killed = 0;
        while (g.hormuzActive()) { day(g); killed = h.usKilled(); }
        assertEquals(3, h.invasion(), "the islands are taken");
        assertTrue(killed > 50, "at a cost in lives: " + killed);
    }

    @Test
    void aFullInvasionEndsInOccupation() {
        GameEngine g = crisis(5, 0);
        g.apply(new Command.Hormuz(new HormuzOrder.Invade(0)));
        HormuzState h = g.hormuz().orElseThrow();
        while (g.hormuzActive()) day(g);
        assertEquals("Regime change and occupation", h.outcome());
        assertTrue(h.day() > 150, "months of build-up and fighting: " + h.day());
        assertTrue(h.usKilled() > 1000, "thousands of lives: " + h.usKilled());
        int ir = g.foreignEngine().data().index("IR");
        assertTrue(g.foreign().get().wars().stream().anyMatch(x -> x.enemy() == ir && x.fighting()), "the occupation goes on, on the Situation Room's war model");
    }

    @Test
    void theCrisisSurvivesASaveAndReplays() {
        GameEngine g = crisis(13, 2);
        g.apply(new Command.Hormuz(new HormuzOrder.Escorts(2)));
        g.apply(new Command.Hormuz(new HormuzOrder.Strike("boats")));
        g.apply(new Command.Hormuz(new HormuzOrder.Envoy("qatar")));
        for (int i = 0; i < 9; i++) day(g);
        GameEngine loaded = SaveManager.load(SaveManager.save(g));
        assertEquals(g.hormuz().get().toJson(), loaded.hormuz().get().toJson());
        assertTrue(loaded.hormuzActive());
        GameEngine replayed = GameEngine.replay(g.setup(), g.history());
        assertEquals(g.hormuz().get().toJson(), replayed.hormuz().get().toJson(), "the crisis replays from the commands");
        day(g); day(loaded);
        assertEquals(g.hormuz().get().toJson(), loaded.hormuz().get().toJson(), "and goes on the same way after loading");
    }
}
