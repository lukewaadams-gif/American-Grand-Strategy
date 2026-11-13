package game.mandate.core.engine;

import game.mandate.core.command.Command;
import game.mandate.core.economy.Program;
import game.mandate.core.hormuz.HormuzOrder;
import game.mandate.core.hormuz.HormuzState;
import game.mandate.core.news.News;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A crisis counts each of its consequences once (docs/BUG_FIX_REPORT.md, E-06): its spending ends with it, the fall of
 * Tehran is reported and rewarded once, and the petroleum reserve it draws is the one the last crisis left.
 */
class CrisisAccountingTest {

    /** A Hormuz crisis begun now in a governing presidency, played with the given first order and nothing else, to its end. */
    static GameEngine strait(String base, HormuzOrder first) {
        GameEngine g = SaveManager.load(base);
        g.apply(new Command.Hormuz(new HormuzOrder.Begin(0)));
        if (first != null) g.apply(new Command.Hormuz(first));
        int guard = 0;
        while (g.hormuzActive() && guard++ < 400) { EngineFixtures.answerAll(g, 0); g.apply(new Command.EndTurn()); }
        return g;
    }

    @Test
    void theStraitsSpendingEndsWithIt() {
        String base = ModesTest.governingSave(2);
        int before = SaveManager.load(base).state().budget().programs().size();
        GameEngine g = strait(base, new HormuzOrder.Escorts(3));
        assertTrue(!g.hormuzActive(), "the crisis ended");
        HormuzState h = g.hormuz().orElseThrow();
        assertEquals(0, h.spending(), 1e-9, "nothing still booked");
        // what the crisis added to the budget, its reversal included, comes to nothing a year once it is over
        List<Program> added = g.state().budget().programs().subList(before, g.state().budget().programs().size());
        double net = added.stream().filter(p -> p.id().startsWith("deficit-")).mapToDouble(Program::fullPctGdp).sum();
        assertEquals(0, net, 1e-9, "the escorts' cost ends with the crisis");
    }

    @Test
    void theFallOfTehranIsReportedAndRewardedOnce() {
        GameEngine g = strait(ModesTest.governingSave(2), new HormuzOrder.Invade(0));
        if (!"Regime change and occupation".equals(g.hormuz().orElseThrow().outcome())) return;   // the strait settled first in this world
        long falls = g.state().news().stream().map(News::head).filter(t -> t.contains("FALLS") && t.toUpperCase().contains("IRAN")).count()
                + g.state().news().stream().map(News::head).filter(t -> t.startsWith("TEHRAN FALLS")).count();
        assertEquals(1, falls, "one story of the fall, not two");
    }

    @Test
    void theReserveIsTheOneTheLastCrisisLeft() {
        String base = ModesTest.governingSave(2);
        GameEngine g = SaveManager.load(base);
        g.apply(new Command.Hormuz(new HormuzOrder.Begin(0)));
        g.apply(new Command.Hormuz(new HormuzOrder.Oil("spr", 4)));
        int guard = 0;
        while (g.hormuzActive() && guard++ < 400) { EngineFixtures.answerAll(g, 0); g.apply(new Command.EndTurn()); }
        double left = g.hormuz().orElseThrow().sprLeft();
        assertTrue(left < HormuzState.SPR_START, "the first crisis drew on the reserve: " + left);
        g.apply(new Command.Hormuz(new HormuzOrder.Begin(0)));
        double now = g.hormuz().orElseThrow().sprLeft();
        assertTrue(now >= left && now < HormuzState.SPR_START, "the next crisis starts with what was left, a little refilled: " + left + " -> " + now);
    }
}
