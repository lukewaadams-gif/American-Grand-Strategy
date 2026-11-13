package game.mandate.core.engine;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.game.GameSetup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Saves made before this round of changes (the shipped sample saves at commit 09aab1c: a length, not a mode; no record
 * of election nights watched; no individual crisis; no crisis spending) load, play on, and save again, never silently
 * changed (docs/BUG_FIX_REPORT.md, E-08).
 */
class OlderSavesTest {

    static String fixture(String name) throws IOException {
        try (InputStream in = OlderSavesTest.class.getResourceAsStream("/older-saves/" + name + "-09aab1c.json")) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void olderSavesLoadPlayOnAndSaveAgain() throws IOException {
        for (String name : new String[]{"campaign", "presidency", "governing"}) {
            String text = fixture(name);
            assertTrue(text.contains("\"length\""), name + " is an older save");
            GameEngine g = SaveManager.load(text);
            assertEquals(GameSetup.Mode.FULL_GAME, g.mode(), name + ": a length loads as the Full Game");
            assertTrue(g.individualCrisis().isEmpty(), name);
            String date = g.date().toString(), phase = g.phase().name();
            // what the save held is what the game holds: the date, the phase, the history
            assertEquals(date, savedDate(text), name);
            assertFalse(g.over(), name);
            // it plays on
            for (int i = 0; i < 6 && !g.over(); i++) AutoPilot.playTurn(g);
            assertTrue(g.date().isAfter(java.time.LocalDate.parse(date)) || !g.phase().name().equals(phase), name + " plays on from " + date);
            // and saves in the new format, which loads to the same world
            String again = SaveManager.save(g);
            assertTrue(again.contains("\"mode\": \"FULL_GAME\""), name + " saves its mode");
            assertEquals(g.fingerprint(), SaveManager.load(again).fingerprint(), name + ": the new save reloads to the same world");
        }
    }

    /** The date in a save's clock, read from the text as saved. */
    static String savedDate(String text) {
        var m = game.mandate.core.data.Json.object(game.mandate.core.data.Json.parse(text));
        var clock = game.mandate.core.data.Json.object(game.mandate.core.data.Json.object(m.get("world")).get("clock"));
        return ((String) clock.get("now")).substring(0, 10);
    }
}
