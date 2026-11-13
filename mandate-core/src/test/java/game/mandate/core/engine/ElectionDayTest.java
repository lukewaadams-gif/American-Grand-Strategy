package game.mandate.core.engine;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.campaign.DebatePosture;
import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.data.Json;
import game.mandate.core.electorate.ElectionResult;
import game.mandate.core.game.GameSetup;
import game.mandate.core.game.Scenario;
import game.mandate.core.state.Party;
import game.mandate.core.time.Phase;
import game.mandate.core.time.SimulationClock;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Election Day (the softlock found in play, docs/BUG_FIX_REPORT.md): every campaign reaches election night on November 7,
 * the result is computed once and never again, a save on the last day gives the same night, and the night stays to be
 * watched across a reload until it has been.
 */
class ElectionDayTest {
    static final LocalDate ELECTION = LocalDate.of(2028, 11, 7);
    static final String[][] TICKETS = {{"d_reyes", "r_harlan"}, {"d_okafor", "r_calloway"}, {"d_whitfield", "r_marsh"}, {"d_lindqvist", "r_benton"}};

    static GameSetup setup(long seed, boolean dem, int ticket, Scenario scenario, GameSetup.Difficulty difficulty) {
        String[] t = TICKETS[ticket % TICKETS.length];
        return new GameSetup(seed, dem ? Party.DEMOCRAT : Party.REPUBLICAN, dem ? t[0] : t[1], dem ? t[1] : t[0], scenario, difficulty);
    }

    static boolean campaigning(GameEngine g) { return g.phase() == Phase.GENERAL_CAMPAIGN_2028 || g.phase() == Phase.REELECTION_CAMPAIGN_2032; }

    static GameEngine toElection(GameEngine g) {
        int guard = 0;
        while (campaigning(g) && guard++ < 200) AutoPilot.playTurn(g);
        return g;
    }

    @Test
    void everyCampaignReachesElectionNightOnNovemberSeventh() {
        int playerWins = 0, playerLosses = 0, demWins = 0, repWins = 0, landslides = 0, close = 0, games = 0;
        List<String> seen = new ArrayList<>();
        for (Scenario scenario : Scenario.values())
            for (int k = 0; k < 4; k++) {
                long seed = 100 + 17L * k + scenario.ordinal() * 1000;
                boolean dem = k % 2 == 0;
                GameSetup.Difficulty diff = GameSetup.Difficulty.values()[k % 3];
                GameEngine g = toElection(new GameEngine(setup(seed, dem, k, scenario, diff)));
                games++;
                assertFalse(campaigning(g), "the campaign ended (seed " + seed + ", " + scenario + ")");
                ElectionResult r = g.result2028().orElseThrow();
                assertEquals(538, r.demElectoralVotes() + r.repElectoralVotes());
                boolean won = r.winner() == g.player().party();
                // the night is November 7; a win moves the clock on to the transition office, a loss stays on the night
                assertEquals(won ? g.calendar().inauguration().minusWeeks(g.calendar().transitionWeeks()) : ELECTION, g.date());
                assertTrue(g.log().stream().anyMatch(l -> l.contains("Election Night 2028")), "the night is logged");
                assertEquals(won ? Phase.TRANSITION : Phase.EPILOGUE, g.phase(), "a win goes to the transition, a loss to the end");
                assertEquals(2028, g.electionNightPending(), "election night is waiting to be watched");
                if (won) playerWins++; else playerLosses++;
                if (r.winner() == Party.DEMOCRAT) demWins++; else repWins++;
                int gap = Math.abs(r.demElectoralVotes() - r.repElectoralVotes());
                if (gap >= 100) landslides++;
                if (gap <= 40) close++;
                seen.add(scenario + "/" + seed + ": " + r.demElectoralVotes() + "-" + r.repElectoralVotes());
            }
        String all = String.join("; ", seen);
        assertTrue(playerWins > 0 && playerLosses > 0, "the player both wins and loses across seeds: " + all);
        assertTrue(demWins > 0 && repWins > 0, "both parties win: " + all);
        assertTrue(landslides > 0, "at least one landslide: " + all);
        assertTrue(close > 0, "at least one close race: " + all);
        assertEquals(Scenario.values().length * 4, games);
    }

    @Test
    void theFinalStretchRunsADayAtATimeAndTheLastDebateIsAsked() {
        GameEngine g = new GameEngine(setup(7, true, 0, Scenario.TOSS_UP, GameSetup.Difficulty.STANDARD));
        EngineFixtures.playUntil(g, x -> !x.date().isBefore(LocalDate.of(2028, 10, 23)));
        assertEquals(LocalDate.of(2028, 10, 23), g.date());
        assertEquals(SimulationClock.Granularity.DAILY, g.clock().mode(), "two weeks out the campaign runs in days");
        assertTrue(g.debateThisWeek().isPresent(), "the third debate falls on the first day of the final stretch");
        EngineFixtures.answerAll(g, 0);
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.EndTurn()), "the debate needs an approach");
        g.apply(new Command.ChooseDebatePosture(DebatePosture.values()[0]));
        g.apply(new Command.EndTurn());
        assertEquals(LocalDate.of(2028, 10, 24), g.date(), "one day");
        assertTrue(g.debateThisWeek().isEmpty(), "and no debate the next day");
    }

    @Test
    void aSaveOnTheLastDayOfTheCampaignGivesTheSameNight() {
        GameEngine g = new GameEngine(setup(11, false, 1, Scenario.TOSS_UP, GameSetup.Difficulty.STANDARD));
        EngineFixtures.playUntil(g, x -> !x.date().isBefore(ELECTION.minusDays(1)));
        assertEquals(ELECTION.minusDays(1), g.date());
        assertTrue(g.result2028().isEmpty());
        String save = SaveManager.save(g);
        GameEngine loaded = SaveManager.load(save), replayed = SaveManager.load(SaveManager.saveReplayOnly(g));
        for (GameEngine x : List.of(g, loaded, replayed)) { EngineFixtures.answerAll(x, 0); AutoPilot.playTurn(x); }
        ElectionResult a = g.result2028().orElseThrow(), b = loaded.result2028().orElseThrow(), c = replayed.result2028().orElseThrow();
        assertEquals(a, b, "the same night from the saved game");
        assertEquals(a, c, "and from its replay");
        assertEquals(g.date(), loaded.date());
    }

    @Test
    void theResultIsComputedOnceAndElectionNightSurvivesAReload() {
        GameEngine g = toElection(new GameEngine(setup(3, true, 2, Scenario.TOSS_UP, GameSetup.Difficulty.STANDARD)));
        ElectionResult r = g.result2028().orElseThrow();
        // a reload on the night: still to be watched, the same result
        GameEngine night = SaveManager.load(SaveManager.save(g));
        assertEquals(2028, night.electionNightPending());
        assertEquals(r, night.result2028().orElseThrow());
        assertThrows(IllegalCommandException.class, () -> night.apply(new Command.ElectionNightSeen(2032)), "no 2032 night yet");
        night.apply(new Command.ElectionNightSeen(2028));
        assertEquals(0, night.electionNightPending());
        GameEngine after = SaveManager.load(SaveManager.save(night));
        assertEquals(0, after.electionNightPending(), "once watched it stays watched");
        assertTrue(SaveManager.consistent(SaveManager.save(night)), "the command replays");
        // the game going on never draws the election again
        if (after.phase() == Phase.TRANSITION) {
            for (int i = 0; i < 30 && after.phase() != Phase.GOVERNING && !after.over(); i++) AutoPilot.playTurn(after);
            assertEquals(r, after.result2028().orElseThrow(), "the result is never recomputed");
        }
    }

    @Test
    void anOlderSaveWithoutTheRecordShowsTheNightOnlyIfSavedOnIt() {
        GameEngine g = toElection(new GameEngine(setup(3, true, 2, Scenario.TOSS_UP, GameSetup.Difficulty.STANDARD)));
        assertEquals(Phase.TRANSITION, g.phase(), "this seed wins (see the test above)");
        GameEngine onTheNight = SaveManager.load(withoutRecord(SaveManager.save(g)));
        assertEquals(2028, onTheNight.electionNightPending(), "saved on the night: the night is shown");
        GameEngine gov = EngineFixtures.governing(5);
        GameEngine later = SaveManager.load(withoutRecord(SaveManager.save(gov)));
        assertEquals(0, later.electionNightPending(), "saved later: the night counts as seen");
    }

    /** The same save as an older version wrote it: no record of election nights watched. */
    static String withoutRecord(String save) {
        Map<String, Object> m = Json.object(Json.parse(save));
        Json.object(m.get("world")).remove("electionNightSeen");
        return Json.write(m);
    }
}
