package game.mandate.core;

import game.mandate.core.economy.Economy;
import game.mandate.core.rng.RngStreams;
import game.mandate.core.world.AbstractTheaterResolver;
import game.mandate.core.world.TheaterOutcome;
import game.mandate.core.world.TheaterScenario;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemsTest {

    @Test
    void aRecessionShockIsFeltInJobs() {
        // Resolute Desk's audit found a severe shock moved unemployment < 1 point; ours must bite.
        Economy calm = Economy.baseline2029(), hit = Economy.baseline2029();
        hit.addShock(-6.0, -0.5, 6);
        RngStreams a = new RngStreams(1), b = new RngStreams(1);
        double peak = 0;
        for (int m = 0; m < 18; m++) {
            calm.stepMonth(a);
            hit.stepMonth(b);
            peak = Math.max(peak, hit.unemployment() - calm.unemployment());
        }
        assertTrue(peak > 1.2, "peak unemployment gap " + peak);
    }

    @Test
    void economyIsStableWithoutShocks() {
        Economy e = Economy.baseline2029();
        RngStreams r = new RngStreams(3);
        for (int m = 0; m < 48; m++) e.stepMonth(r);
        assertTrue(e.unemployment() > 2.5 && e.unemployment() < 8.5, "u " + e.unemployment());
        assertTrue(e.inflation() > 0 && e.inflation() < 6, "pi " + e.inflation());
    }

    @Test
    void deterrenceImprovesWarOutcomes() {
        AbstractTheaterResolver t = new AbstractTheaterResolver();
        assertTrue(defended(t, 85) > defended(t, 25) + 0.2);
    }

    private static double defended(AbstractTheaterResolver t, double deterrence) {
        RngStreams r = new RngStreams(9);
        int ok = 0, n = 2000;
        for (int i = 0; i < n; i++) {
            TheaterOutcome o = t.resolve(new TheaterScenario(deterrence, 50, true, 1, TheaterScenario.Commitment.INTERVENE), r);
            if (o.status() == TheaterOutcome.TaiwanStatus.DEFENDED) ok++;
        }
        return ok / (double) n;
    }

    @Test
    void rngStreamsAreIndependent() {
        RngStreams a = new RngStreams(5), b = new RngStreams(5);
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
