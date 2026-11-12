package game.mandate.core.engine;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.data.Json;
import game.mandate.core.data.Tuning;
import game.mandate.core.game.GameSetup;
import game.mandate.core.game.Scenario;
import game.mandate.core.opening.OpeningSetup;
import game.mandate.core.opening.OpeningTerm;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.state.Party;
import game.mandate.core.time.CampaignCalendar;
import game.mandate.core.time.SimulationClock;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static game.mandate.core.engine.EngineFixtures.governing;
import static game.mandate.core.engine.EngineFixtures.playUntil;
import static game.mandate.core.engine.EngineFixtures.setup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the engine's infrastructure: random state, JSON, clock, tuning, ledger, scheduler. */
class EngineUnitTest {

    @Test
    void randomStateSnapshotsReproduceTheFuture() {
        DeterministicRandomManager a = new DeterministicRandomManager(42);
        for (int i = 0; i < 100; i++) { a.uniform("x"); a.gaussian("y"); }
        Map<String, Long> snap = a.snapshot();
        double[] expected = {a.uniform("x"), a.gaussian("y"), a.uniform("z"), a.nextInt("x", 7)};
        DeterministicRandomManager b = new DeterministicRandomManager(42);
        b.restore(snap);
        double[] got = {b.uniform("x"), b.gaussian("y"), b.uniform("z"), b.nextInt("x", 7)};
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], got[i]);
    }

    @Test
    void jsonRoundTripsDoublesExactly() {
        DeterministicRandomManager r = new DeterministicRandomManager(1);
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < 200; i++) m.put("k" + i, r.gaussian("j") * Math.pow(10, i % 7 - 3));
        m.put("nested", List.of(1L, "two \"quoted\"\n", true, Map.of("a", -0.0)));
        Map<String, Object> back = Json.object(Json.parse(Json.write(m)));
        for (int i = 0; i < 200; i++) assertEquals((double) m.get("k" + i), Json.num(back.get("k" + i)));
        assertEquals(Json.write(m), Json.write(back));
    }

    @Test
    void theClockSupportsEveryGranularity() {
        SimulationClock c = new SimulationClock(LocalDate.of(2029, 1, 20), SimulationClock.Granularity.MONTHLY);
        c.advance();
        assertEquals(LocalDate.of(2029, 2, 20), c.date());
        c.setMode(SimulationClock.Granularity.WEEKLY);
        c.advance();
        assertEquals(LocalDate.of(2029, 2, 27), c.date());
        c.setMode(SimulationClock.Granularity.DAILY);
        c.advance();
        assertEquals(LocalDate.of(2029, 2, 28), c.date());
        c.setMode(SimulationClock.Granularity.OPERATIONAL);
        assertThrows(IllegalStateException.class, c::advance);
        for (int i = 0; i < 4; i++) c.advanceHours(6);
        assertEquals(LocalDate.of(2029, 3, 1), c.date());
        assertEquals(7, c.turns());
        SimulationClock back = SimulationClock.fromJson(Json.object(Json.parse(Json.write(c.toJson()))));
        assertEquals(c.now(), back.now());
        assertEquals(c.mode(), back.mode());
    }

    @Test
    void theCalendarIsConfiguredDataAndConsistentWithTheRules() {
        CampaignCalendar cal = CampaignCalendar.load();
        assertEquals(CampaignCalendar.electionDay(2028), cal.election2028());
        assertEquals(CampaignCalendar.electionDay(2030), cal.midterms());
        assertEquals(CampaignCalendar.electionDay(2032), cal.election2032());
        for (int y = 2029; y <= 2032; y++) assertEquals(8, cal.fomcMeetings(LocalDate.of(y, 1, 1), LocalDate.of(y + 1, 1, 1)));
        assertEquals(12, CampaignCalendar.releases(LocalDate.of(2030, 1, 1), LocalDate.of(2031, 1, 1), true));
    }

    /** Every constant in the tuning file is used: no dead configuration, and no constant hidden in code. */
    @Test
    void everyTuningKeyIsUsed() {
        Tuning t = Tuning.defaults();
        new GameEngine(setup(1), t);
        new OpeningTerm(new OpeningSetup(1, Party.DEMOCRAT, true));
        Set<String> unused = new TreeSet<>(t.keys());
        unused.removeAll(t.keysRead());
        assertTrue(unused.isEmpty(), "dead tuning keys: " + unused);
    }

    @Test
    void tuningOverridesChangeTheSimulationAndRejectTypos() {
        Tuning blue = Tuning.defaults().withOverrides(Map.of("scenario.TOSS_UP.environment", "12"));
        int demWins = 0;
        for (long seed = 1; seed <= 40; seed++) {
            GameEngine g = new GameEngine(new GameSetup(seed, Party.DEMOCRAT, "d_reyes", "r_marsh", Scenario.TOSS_UP, GameSetup.Difficulty.STANDARD), blue);
            playUntil(g, x -> x.result2028().isPresent());
            if (g.result2028().orElseThrow().winner() == Party.DEMOCRAT) demWins++;
        }
        assertTrue(demWins >= 38, "a D+12 environment should almost always elect the Democrat: " + demWins);
        assertThrows(IllegalArgumentException.class, () -> Tuning.defaults().withOverrides(Map.of("scenario.TOSS_UP.enviroment", "1")));
    }

    /** Every transition is recorded with the engine that applied it; decisions link to their consequences. */
    @Test
    void theLedgerRecordsEveryTransitionAndItsOwner() {
        GameEngine g = playUntil(governing(5), x -> x.laws().size() >= 2 || x.over());
        ConsequenceLedger l = g.state().ledger();
        assertTrue(l.consequences().size() > 10);
        Set<String> owners = new TreeSet<>();
        l.consequences().forEach(c -> owners.add(c.owner()));
        assertTrue(owners.containsAll(Set.of("congress", "executive+politics", "politics")), "owners " + owners);
        var push = l.decisions().stream().filter(d -> d.description().startsWith("Push")).findFirst().orElseThrow();
        assertTrue(l.consequencesOf(push.id()).size() >= 2, "a push decision has consequences");
    }

    @Test
    void refusedCommandsChangeNothing() {
        GameEngine g = governing(9);
        String before = Json.write(g.state().toJson());
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.PushBill("lower_costs", game.mandate.core.congress.LegislativeStrategy.PARTY_LINE, 1e9)));
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.ResolveEvent(999, 0)));
        assertEquals(before, Json.write(g.state().toJson()));
    }

    @Test
    void savesRejectForeignFilesAndUnknownVersions() {
        assertThrows(IllegalArgumentException.class, () -> SaveManager.load("{\"format\": \"other\"}"));
        GameEngine g = new GameEngine(setup(3));
        String s = SaveManager.save(g).replace("\"version\": 2", "\"version\": 9");
        assertThrows(IllegalArgumentException.class, () -> SaveManager.load(s));
    }

    @Test
    void differentSeedsGiveDifferentWorlds() {
        GameEngine a = playUntil(new GameEngine(setup(100)), GameEngine::over), b = playUntil(new GameEngine(setup(102)), GameEngine::over);
        assertNotEquals(a.fingerprint(), b.fingerprint());
        AutoPilot.class.getName();
    }
}
