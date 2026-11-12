package game.mandate.ui;

import game.mandate.ui.kit.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every screen, at every supported window size and display setting in {@link Screenshots#shots()}, passes the
 * automated checks: nothing clipped or overflowing, no text cut off without a tooltip, no text under 12 pt, no
 * button that does nothing or is disabled without a reason, something to focus.
 */
class ScreensTest {
    static Map<String, String> moments;

    @BeforeAll static void fixtures() { moments = Fixtures.moments(); }

    @AfterEach void reset() { Theme.set(Theme.Palette.STANDARD, 1.0); }

    @Test void everyScreenPassesTheAutomatedChecks(@TempDir Path home) throws Exception {
        List<String> problems = new ArrayList<>();
        for (Screenshots.Shot s : Screenshots.shots()) problems.addAll(Screenshots.shoot(s, moments, home.resolve(s.name()), null));
        assertTrue(problems.isEmpty(), problems.size() + " problems:\n" + String.join("\n", problems));
    }

    @Test void theShotsCoverEveryScreenAndEverySize() {
        List<Screenshots.Shot> shots = Screenshots.shots();
        for (App.ScreenId id : App.ScreenId.values())
            assertTrue(shots.stream().anyMatch(s -> s.screen() == id), "no shot of " + id);
        for (int[] size : Screenshots.SIZES)
            assertTrue(shots.stream().anyMatch(s -> s.width() == size[0] && s.height() == size[1]), "no shot at " + size[0] + "x" + size[1]);
        for (Theme.Palette p : Theme.Palette.values()) assertTrue(shots.stream().anyMatch(s -> s.palette() == p), "no shot in " + p);
        for (Settings.Complexity c : Settings.Complexity.values()) assertTrue(shots.stream().anyMatch(s -> s.complexity() == c), "no shot at " + c);
        assertTrue(shots.stream().anyMatch(s -> s.scale() > 1), "no shot at a larger text size");
    }

    /** The checks themselves catch what they claim to: a sanity test on deliberately broken components. */
    @Test void theChecksCatchBrokenComponents() {
        javax.swing.JPanel root = new javax.swing.JPanel(null);
        root.setSize(400, 300);
        javax.swing.JButton dead = new javax.swing.JButton("Dead");
        dead.setBounds(10, 10, 100, 30);
        javax.swing.JButton off = new javax.swing.JButton("Off");
        off.addActionListener(e -> { });
        off.setEnabled(false);
        off.setBounds(10, 50, 100, 30);
        javax.swing.JPanel box = new javax.swing.JPanel(null);
        box.setBounds(10, 100, 100, 50);
        javax.swing.JButton spill = new javax.swing.JButton("Spill");
        spill.addActionListener(e -> { });
        spill.setBounds(50, 10, 200, 30);
        box.add(spill);
        root.add(dead);
        root.add(off);
        root.add(box);
        List<String> found = UiChecks.check(root);
        assertTrue(found.stream().anyMatch(s -> s.startsWith("button does nothing")), found.toString());
        assertTrue(found.stream().anyMatch(s -> s.startsWith("disabled without a reason")), found.toString());
        assertTrue(found.stream().anyMatch(s -> s.startsWith("clipped by its parent")), found.toString());
        assertEquals(3, found.size(), found.toString());
    }
}
