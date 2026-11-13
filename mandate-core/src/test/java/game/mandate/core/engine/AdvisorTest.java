package game.mandate.core.engine;

import game.mandate.core.advisor.Advisor;
import game.mandate.core.data.Json;
import game.mandate.core.game.GameSetup;
import game.mandate.core.opening.Types.CrisisOutcome;
import game.mandate.core.time.Phase;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static game.mandate.core.engine.EngineFixtures.playUntil;
import static game.mandate.core.engine.EngineFixtures.setup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The advisor summarises what the player can see; it must never reveal the simulation's hidden state. */
class AdvisorTest {

    /** The accessors that expose hidden truth. The advisor's source may use none of them. */
    static final List<String> HIDDEN = List.of("expectedMargins", "nationalSwing", "localShift", "systematicMiss", "hiddenResolve", "lastEscalationRisk",
            "TestAccess", "random()", "rng", "fingerprint", "result2028", "result2032", "state().election()");

    @Test
    void theAdvisorReadsOnlyWhatThePlayerCanSee() throws Exception {
        String source = Files.readString(Path.of("src/main/java/game/mandate/core/advisor/Advisor.java"));
        for (String h : HIDDEN) assertFalse(source.contains(h), "the advisor must not use " + h);
    }

    @Test
    void theAdversarysHiddenResolveDoesNotChangeTheAdvice() {
        int compared = 0;
        for (long seed = 1; compared < 4 && seed < 300; seed++) {
            GameEngine g = playUntil(new GameEngine(setup(seed).withMode(GameSetup.Mode.FULL_GAME)), x -> x.phase() == Phase.FIRST_100_DAYS);
            if (g.phase() != Phase.FIRST_100_DAYS) continue;
            playUntil(g, x -> x.over() || x.hundredDays().orElseThrow().crisis() == CrisisOutcome.ACTIVE
                    && !Double.isNaN(x.hundredDays().orElseThrow().intelEstimate()));
            if (g.over()) continue;
            String save = SaveManager.save(g);
            Map<String, Object> m = Json.object(Json.parse(save));
            Map<String, Object> hd = Json.object(Json.object(m.get("world")).get("hundredDays"));
            double resolve = Json.num(hd.get("resolve"));
            hd.put("resolve", resolve > 0.5 ? 0.05 : 0.95);                 // the truth changes; nothing the player sees does
            GameEngine other = SaveManager.load(Json.write(m));
            assertNotEquals(g.fingerprint(), other.fingerprint());
            assertEquals(Advisor.brief(g, 20), Advisor.brief(other, 20));
            assertTrue(Advisor.brief(g, 20).stream().anyMatch(a -> a.topic() == Advisor.Topic.SECURITY), "the crisis is in the brief");
            compared++;
        }
        assertTrue(compared >= 3, "enough crises: " + compared);
    }

    @Test
    void everyPhaseGetsAShortRankedBrief() {
        GameEngine g = new GameEngine(setup(2).withMode(GameSetup.Mode.FULL_GAME));
        boolean sawCampaign = false, sawTransition = false, sawDays = false;
        while (!g.over() && g.dayHundredReport().isEmpty()) {
            List<Advisor.Advice> brief = Advisor.brief(g, 5);
            assertTrue(brief.size() <= 5);
            for (int i = 1; i < brief.size(); i++) assertTrue(brief.get(i - 1).tone().ordinal() <= brief.get(i).tone().ordinal(), "most pressing first");
            for (Advisor.Advice a : brief) assertFalse(a.headline().isBlank() || a.detail().isBlank() || a.basis().isBlank());
            if (!brief.isEmpty()) switch (g.phase()) {
                case GENERAL_CAMPAIGN_2028 -> sawCampaign = true;
                case TRANSITION -> sawTransition = true;
                case FIRST_100_DAYS -> sawDays = true;
                default -> { }
            }
            game.mandate.core.ai.AutoPilot.playTurn(g);
        }
        assertTrue(sawCampaign);
        if (g.result2028().orElseThrow().winner() == g.player().party()) assertTrue(sawTransition && sawDays);
    }
}
