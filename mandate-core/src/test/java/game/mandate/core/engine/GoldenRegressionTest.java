package game.mandate.core.engine;

import game.mandate.core.campaign.Candidate;
import game.mandate.core.game.GameSetup;
import game.mandate.core.opening.OpeningSetup;
import game.mandate.core.opening.Strategy;
import game.mandate.core.state.Party;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import static game.mandate.core.engine.EngineFixtures.playUntil;
import static game.mandate.core.engine.EngineFixtures.setup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic regression: fixed seeds must produce exactly the recorded worlds. Any change to rules, tuning or
 * content changes these fingerprints, deliberately. After an intended change, regenerate with
 * {@code mvn test -Dtest=GoldenRegressionTest -Dmandate.updateGolden=true} and commit the new file with the change.
 */
class GoldenRegressionTest {
    static final Path FILE = Path.of("src/test/resources/golden/fingerprints.properties");

    static Map<String, String> current() {
        Map<String, String> m = new TreeMap<>();
        for (long seed = 1; seed <= 6; seed++) m.put("game." + seed, playUntil(new GameEngine(setup(seed)), GameEngine::over).fingerprint());
        // To day 100: the election, the transition and the first hundred days, to the report (the presidency goes on
        // after it; these stop there). The seeds were chosen to cover each 100-day outcome and a lost election.
        for (long seed : new long[]{1, 2, 6, 8, 12, 21, 24}) m.put("slice." + seed, slice(setup(seed).withMode(GameSetup.Mode.FULL_GAME)));
        for (long seed : new long[]{11, 21})
            m.put("slice.custom." + seed, slice(setup(seed).withMode(GameSetup.Mode.FULL_GAME)
                    .withCustomCandidate(Candidate.custom(Party.REPUBLICAN, "Dana Whitcomb", "OH", 8, 7, 6, 6, 6, 0.25))));
        for (long seed : new long[]{3, 9})
            m.put("slice.realRoster." + seed, slice(new GameSetup(seed, Party.DEMOCRAT, "bg_buttigieg", "bg_vance", game.mandate.core.game.Scenario.TOSS_UP,
                    GameSetup.Difficulty.STANDARD, GameSetup.Mode.FULL_GAME, true, null)));
        for (Strategy s : Strategy.values())
            m.put("prototype." + s, Integer.toHexString(Strategy.play(new OpeningSetup(77, Party.REPUBLICAN, false), s).report().toString().hashCode()));
        return m;
    }

    /** A game to day 100: its world fingerprint and how it stood, the 100-day outcome, or the lost election. */
    static String slice(GameSetup s) {
        GameEngine g = playUntil(new GameEngine(s), x -> x.dayHundredReport().isPresent());
        String ending = g.dayHundredReport().map(r -> r.outcome() + ":" + r.score()).orElse("LOST:" + g.result2028().orElseThrow().electoralVotes(g.player().party()));
        return g.fingerprint() + "|" + ending;
    }

    @Test
    void fixedSeedsReproduceTheRecordedWorlds() throws IOException {
        Map<String, String> now = current();
        if (Boolean.getBoolean("mandate.updateGolden")) {
            Properties p = new Properties();
            p.putAll(now);
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) { p.store(w, "Golden fingerprints; see GoldenRegressionTest"); }
            return;
        }
        assertTrue(Files.exists(FILE), "missing " + FILE + "; generate it with -Dmandate.updateGolden=true");
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) { p.load(in); }
        Map<String, String> recorded = new TreeMap<>();
        p.forEach((k, v) -> recorded.put((String) k, (String) v));
        assertEquals(recorded, now, "simulation output changed; if intended, regenerate the golden file");
    }

    /** The same seed run twice in one JVM gives the same world (no state leaks between games). */
    @Test
    void noStateLeaksBetweenGamesInOneJvm() {
        assertEquals(current(), current());
    }
}
