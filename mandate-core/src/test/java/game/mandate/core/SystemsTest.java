package game.mandate.core;

import game.mandate.core.data.Tuning;
import game.mandate.core.economy.BudgetState;
import game.mandate.core.economy.EconomyEngine;
import game.mandate.core.economy.EconomyParams;
import game.mandate.core.economy.EconomyState;
import game.mandate.core.economy.MarketContext;
import game.mandate.core.effects.Effect;
import game.mandate.core.military.MilitaryCatalog;
import game.mandate.core.military.MilitaryEngine;
import game.mandate.core.military.MilitaryParams;
import game.mandate.core.military.OperationalTheaterResolver;
import game.mandate.core.rng.DeterministicRandomManager;

import game.mandate.core.military.TheaterOutcome;
import game.mandate.core.military.TheaterScenario;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemsTest {
    static final EconomyEngine ECON = new EconomyEngine(EconomyParams.from(Tuning.defaults()));
    static final MilitaryEngine MILITARY = new MilitaryEngine(MilitaryParams.from(Tuning.defaults()), MilitaryCatalog.load());

    @Test
    void aRecessionShockIsFeltInJobs() {
        // Resolute Desk's audit found a severe shock moved unemployment < 1 point; ours must bite.
        EconomyState calm = ECON.newEconomy(), hit = ECON.newEconomy();
        BudgetState cb = ECON.newBudget(), hb = ECON.newBudget();
        ECON.apply(hit, hb, new Effect.EconomyShock(-6.0, -0.5, 6));
        DeterministicRandomManager a = new DeterministicRandomManager(1), b = new DeterministicRandomManager(1);
        double peak = 0;
        for (int m = 0; m < 18; m++) {
            ECON.step(calm, cb, 1.0, MarketContext.calm(), a);
            ECON.step(hit, hb, 1.0, MarketContext.calm(), b);
            peak = Math.max(peak, hit.unemployment() - calm.unemployment());
        }
        assertTrue(peak > 1.2, "peak unemployment gap " + peak);
    }

    @Test
    void economyIsStableWithoutShocks() {
        EconomyState e = ECON.newEconomy();
        BudgetState bud = ECON.newBudget();
        DeterministicRandomManager r = new DeterministicRandomManager(3);
        for (int m = 0; m < 48; m++) ECON.step(e, bud, 1.0, MarketContext.calm(), r);
        assertTrue(e.unemployment() > 2.5 && e.unemployment() < 8.5, "u " + e.unemployment());
        assertTrue(e.inflation() > 0 && e.inflation() < 6, "pi " + e.inflation());
    }

    @Test
    void deterrenceImprovesWarOutcomes() {
        OperationalTheaterResolver t = new OperationalTheaterResolver(MILITARY);
        assertTrue(defended(t, 85) > defended(t, 25) + 0.2, defended(t, 85) + " vs " + defended(t, 25));
    }

    private static double defended(OperationalTheaterResolver t, double deterrence) {
        DeterministicRandomManager r = new DeterministicRandomManager(9);
        int ok = 0, n = 400;
        for (int i = 0; i < n; i++) {
            TheaterOutcome o = t.resolve(new TheaterScenario(deterrence, 100, true, 1, TheaterScenario.Commitment.INTERVENE, 70), r);
            if (o.status() == TheaterOutcome.TaiwanStatus.DEFENDED) ok++;
        }
        return ok / (double) n;
    }

    @Test
    void rngStreamsAreIndependent() {
        DeterministicRandomManager a = new DeterministicRandomManager(5), b = new DeterministicRandomManager(5);
        b.uniform("economy"); // an extra draw in one system...
        assertEquals(a.uniform("congress"), b.uniform("congress")); // ...does not disturb another
    }

    @Test
    void coreNeverImportsUiToolkits() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    String s = Files.readString(p);
                    assertTrue(!s.contains("import java.awt") && !s.contains("import javax.swing") && !s.contains("import javafx"),
                            p + " imports a UI toolkit");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
