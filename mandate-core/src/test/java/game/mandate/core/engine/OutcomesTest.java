package game.mandate.core.engine;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.game.GameSetup;
import game.mandate.core.opening.DayHundredReport;
import game.mandate.core.opening.DayHundredReport.Outcome;
import game.mandate.core.opening.Strategy;
import game.mandate.core.opening.Types.BillStage;
import game.mandate.core.time.Phase;
import game.mandate.core.tutorial.Coach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static game.mandate.core.engine.EngineFixtures.playUntil;
import static game.mandate.core.engine.EngineFixtures.setup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Clear endings: the 100-day report and its outcomes, the post-mortem of a defeat, replays, and the coach. */
class OutcomesTest {

    static GameEngine inaugurated(long seed) {
        GameEngine g = playUntil(new GameEngine(setup(seed).withLength(GameSetup.Length.HUNDRED_DAYS)), x -> x.phase() == Phase.FIRST_100_DAYS);
        return g.phase() == Phase.FIRST_100_DAYS ? g : null;
    }

    static DayHundredReport playHundredDays(GameEngine g, Strategy s) {
        while (g.phase() == Phase.FIRST_100_DAYS) s.playWeek(g.hundredDays().orElseThrow());
        return g.dayHundredReport().orElseThrow();
    }

    @Test
    void theReportFollowsItsStatedRuleAndStrategiesReachDifferentOutcomes() {
        Map<Strategy, Map<Outcome, Integer>> byStrategy = new EnumMap<>(Strategy.class);
        int games = 0;
        for (long seed = 1; games < 14 && seed < 300; seed++) {
            GameEngine start = inaugurated(seed);
            if (start == null) continue;
            games++;
            String save = SaveManager.save(start);
            for (Strategy s : new Strategy[]{Strategy.STRATEGIST, Strategy.PARTISAN, Strategy.IDLE}) {
                GameEngine g = SaveManager.load(save);
                DayHundredReport r = playHundredDays(g, s);
                assertEquals(Phase.EPILOGUE, g.phase());
                assertEquals(5, r.goals().size());
                assertEquals(r.goals().stream().mapToInt(DayHundredReport.Goal::points).sum(), r.score());
                if (r.outcome() == Outcome.VICTORY) assertTrue(r.score() >= 9 && g.hundredDays().orElseThrow().stage() == BillStage.LAW);
                if (r.outcome() == Outcome.FAILURE) assertTrue(r.score() <= 4);
                if (r.score() <= 4) assertEquals(Outcome.FAILURE, r.outcome());
                for (DayHundredReport.Goal goal : r.goals()) assertFalse(goal.why().isBlank());
                byStrategy.computeIfAbsent(s, k -> new EnumMap<>(Outcome.class)).merge(r.outcome(), 1, Integer::sum);
                assertEquals(r, SaveManager.load(SaveManager.save(g)).dayHundredReport().orElseThrow(), "the report is part of the save");
            }
        }
        Set<Outcome> seen = new HashSet<>();
        byStrategy.values().forEach(m -> seen.addAll(m.keySet()));
        assertEquals(Set.of(Outcome.values()), seen, "victory, partial success and failure all happen: " + byStrategy);
        int strategistGood = byStrategy.get(Strategy.STRATEGIST).getOrDefault(Outcome.VICTORY, 0) + byStrategy.get(Strategy.STRATEGIST).getOrDefault(Outcome.PARTIAL, 0);
        int idleGood = byStrategy.get(Strategy.IDLE).getOrDefault(Outcome.VICTORY, 0) + byStrategy.get(Strategy.IDLE).getOrDefault(Outcome.PARTIAL, 0);
        assertTrue(strategistGood > idleGood, "governing beats idling: " + byStrategy);
        assertEquals(0, byStrategy.get(Strategy.IDLE).getOrDefault(Outcome.VICTORY, 0), "doing nothing never wins");
    }

    @Test
    void inAFullTermTheDayHundredReportIsFixedAsTheTermGoesOn() {
        GameEngine g = null;
        for (long seed = 1; g == null; seed++) {
            GameEngine x = playUntil(new GameEngine(setup(seed)), y -> y.phase() == Phase.GOVERNING);
            if (x.phase() == Phase.GOVERNING) g = x;
        }
        DayHundredReport atDayHundred = g.dayHundredReport().orElseThrow();
        for (int i = 0; i < 6; i++) AutoPilot.playTurn(g);
        assertEquals(atDayHundred, g.dayHundredReport().orElseThrow());
    }

    @Test
    void aDefeatComesWithAPostMortem() {
        int lost = 0;
        for (long seed = 1; lost < 5 && seed < 100; seed++) {
            GameEngine g = playUntil(new GameEngine(setup(seed).withLength(GameSetup.Length.HUNDRED_DAYS)), x -> x.phase() != Phase.GENERAL_CAMPAIGN_2028);
            if (g.result2028().orElseThrow().winner() == g.player().party()) { assertTrue(g.postMortem().isEmpty()); continue; }
            lost++;
            assertTrue(g.over());
            var lines = g.postMortem();
            assertTrue(lines.size() >= 3, String.valueOf(lines));
            assertTrue(lines.get(1).contains("electoral votes short"));
            assertTrue(lines.stream().anyMatch(l -> l.startsWith("The final polls")));
        }
        assertEquals(5, lost);
    }

    @Test
    void aReplayRewindsToTheInaugurationOrToTheStart() {
        GameEngine g = null;
        long seed = 1;
        for (; g == null; seed++) g = inaugurated(seed);
        String atInauguration = g.fingerprint();
        DayHundredReport first = playHundredDays(g, Strategy.IDLE);
        GameEngine again = g.replayFromInauguration().orElseThrow();
        assertEquals(Phase.FIRST_100_DAYS, again.phase());
        assertEquals(atInauguration, again.fingerprint(), "the same election, Congress and mandate");
        DayHundredReport second = playHundredDays(again, Strategy.STRATEGIST);
        assertNotEquals(first, second, "a different strategy, a different hundred days");
        GameEngine fresh = g.restart();
        assertEquals(Phase.GENERAL_CAMPAIGN_2028, fresh.phase());
        assertEquals(new GameEngine(g.setup()).fingerprint(), fresh.fingerprint());
    }

    @Test
    void theCoachTeachesAtDecisionsAndNeverChangesTheGame() {
        GameEngine g = new GameEngine(setup(2).withLength(GameSetup.Length.HUNDRED_DAYS));
        Set<String> seen = new HashSet<>();
        Set<String> phases = new HashSet<>();
        while (!g.over()) {
            String before = g.fingerprint();
            Optional<Coach.Lesson> l = Coach.next(g, seen);
            assertEquals(before, g.fingerprint());
            l.ifPresent(x -> { assertFalse(x.prompt().isBlank()); assertFalse(x.suggestion().isBlank()); seen.add(x.id()); phases.add(x.id().substring(0, x.id().indexOf('.'))); });
            AutoPilot.playTurn(g);
        }
        assertTrue(seen.contains("campaign.orders") && seen.contains("campaign.pledge"), String.valueOf(seen));
        assertTrue(phases.contains("days") || g.result2028().orElseThrow().winner() != g.player().party(), String.valueOf(seen));
        assertTrue(seen.size() >= 4, String.valueOf(seen));
    }
}
