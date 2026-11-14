package game.mandate.core.engine;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.command.Command;
import game.mandate.core.electorate.ElectionResult;
import game.mandate.core.game.GameSetup;
import game.mandate.core.game.Scenario;
import game.mandate.core.hormuz.HormuzOrder;
import game.mandate.core.state.Party;
import game.mandate.core.time.Phase;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The presidency from the 2028 night to its end, in both modes that govern (docs/MODES.md, SECOND_TERM_VALIDATION.md):
 * one term ends at noon on January 20, 2033 and two at noon on January 20, 2037; the 2030 and 2034 midterms; no third
 * term, and a 2036 election for the successor; every presidential election night, the successor's included; and a
 * crisis never moves the calendar's fixed dates.
 */
class LifecycleTest {

    /** The lifecycle run's games (docs/SECOND_TERM_VALIDATION.md): the mode, party, cast, mood and difficulty vary with the index. */
    static GameSetup setup(int i, long seed) {
        Scenario[] sc = Scenario.values();
        GameSetup.Difficulty[] df = GameSetup.Difficulty.values();
        GameSetup.Mode mode = i % 2 == 0 ? GameSetup.Mode.FULL_GAME : GameSetup.Mode.GOVERNING_ONLY;
        Party p = (i / 2) % 2 == 0 ? Party.DEMOCRAT : Party.REPUBLICAN;
        boolean real = (i / 4) % 2 == 0;
        String me = real ? (p == Party.DEMOCRAT ? "bg_shapiro" : "bg_vance") : (p == Party.DEMOCRAT ? "d_reyes" : "r_marsh");
        String them = real ? (p == Party.DEMOCRAT ? "bg_vance" : "bg_shapiro") : (p == Party.DEMOCRAT ? "r_harlan" : "d_whitfield");
        return new GameSetup(seed, p, me, them, sc[i % sc.length], df[(i / 3) % df.length], mode, real, null);
    }

    enum Path { LOST_2028, ONE_TERM_DEFEATED, ONE_TERM_SUCCESSOR, TWO_TERMS, REMOVED }

    @Test
    void bothModesRunOnePresidencyToItsEnd() {
        Set<Path> full = EnumSet.noneOf(Path.class), governing = EnumSet.noneOf(Path.class);
        // 16 games in the build; -Dmandate.lifecycleGames=160 is the run in docs/SECOND_TERM_VALIDATION.md
        int games = Integer.getInteger("mandate.lifecycleGames", 16);
        for (int i = 0; i < games; i++) {
            GameSetup s = setup(i, i + 1);
            String tag = s.mode() + " seed " + s.seed();
            GameEngine g = GameEngine.start(s);
            List<Integer> nights = new ArrayList<>();
            LocalDate last = g.date();
            int guard = 0, economyFrom = -1;
            LocalDate governingFrom = null;
            while (!g.over() && guard++ < 6000) {
                if (economyFrom < 0 && g.phase() == Phase.GOVERNING) { economyFrom = g.economy().orElseThrow().week(); governingFrom = g.date(); }
                int night = g.electionNightPending();
                if (night > 0 && !nights.contains(night)) {
                    nights.add(night);
                    ElectionResult r = g.latestPresidentialResult().orElseThrow();
                    assertEquals(night, r.year(), tag);
                    assertEquals(538, r.demElectoralVotes() + r.repElectoralVotes(), tag + ": " + night + " counts every elector");
                    var sides = g.electionSides(night);
                    assertNotEquals(sides.get(0).party(), sides.get(1).party(), tag + ": the night's two sides");
                    assertEquals(g.player().party(), sides.get(0).party(), tag + ": the president's party first");
                }
                AutoPilot.playTurn(g);
                assertFalse(g.date().isBefore(last), tag + ": the clock went back from " + last + " to " + g.date());
                last = g.date();
                assertFalse(g.date().isAfter(LocalDate.of(2037, 1, 20)), tag + ": past the end of a second term");
            }
            assertTrue(g.over(), tag + ": the game ends");
            // the economy is stepped once a week, crises or not: never a week twice, never a week skipped (M8-F1, F2)
            if (governingFrom != null) {
                long weeks = java.time.temporal.ChronoUnit.DAYS.between(governingFrom, g.date()) / 7;
                int stepped = g.economy().orElseThrow().week() - economyFrom;
                assertTrue(Math.abs(stepped - weeks) <= 3, tag + ": the economy stepped " + stepped + " weeks in " + weeks);
            }
            if (g.electionNightPending() > 0 && !nights.contains(g.electionNightPending())) nights.add(g.electionNightPending());   // a loss ends on its night
            List<String> log = g.log();
            boolean removed = log.stream().anyMatch(l -> l.contains("The president is removed from office"));
            boolean won28 = g.result2028().map(r -> r.winner() == s.playerParty()).orElse(false);
            Path path;
            if (!won28) path = Path.LOST_2028;
            else if (removed) path = Path.REMOVED;
            else if (g.term() == 2) path = Path.TWO_TERMS;
            else if (g.result2032().isPresent()) path = Path.ONE_TERM_DEFEATED;
            else path = Path.ONE_TERM_SUCCESSOR;
            (s.mode() == GameSetup.Mode.FULL_GAME ? full : governing).add(path);
            if (s.mode() == GameSetup.Mode.GOVERNING_ONLY) assertTrue(won28, tag + ": Governing Only begins elected");
            assertTrue(nights.contains(2028), tag + ": the 2028 night");
            if (!won28) {
                assertTrue(g.president().isEmpty(), tag);
                // a lost campaign is judged as a campaign (B-07), never as a presidency
                assertEquals(game.mandate.core.campaign.ElectionEngine.grade(g.result2028().orElseThrow().electoralVotes(s.playerParty())), g.legacy().orElseThrow().grade(), tag);
                continue;
            }
            // the midterms come with the turn whose days hold Election Day: November 5, or the Monday after in a weekly turn
            long mid30 = log.stream().filter(l -> l.contains("Midterms:") && l.contains("(Election Day 2030-11-05.)")
                    && l.compareTo("2030-11-05") >= 0 && l.compareTo("2030-11-12") < 0).count();
            if (!(removed && g.date().isBefore(LocalDate.of(2030, 11, 6)))) assertEquals(1, mid30, tag + ": the 2030 midterms, the week of November 5");
            switch (path) {
                case ONE_TERM_DEFEATED, ONE_TERM_SUCCESSOR -> {
                    assertEquals(LocalDate.of(2033, 1, 20), g.date(), tag + ": one term ends at noon on January 20, 2033");
                    assertTrue(nights.contains(2032), tag + ": the 2032 night, the president on the ballot or not");
                    assertEquals(path == Path.ONE_TERM_SUCCESSOR ? "succession" : "reelection", g.electionKind(2032), tag);
                }
                case TWO_TERMS -> {
                    assertEquals(LocalDate.of(2037, 1, 20), g.date(), tag + ": two terms end at noon on January 20, 2037");
                    assertEquals(s.playerParty(), g.result2032().orElseThrow().winner(), tag + ": re-elected");
                    assertEquals(1, log.stream().filter(l -> l.startsWith("2034-11") && l.contains("Midterms:")).count(), tag + ": the 2034 midterms");
                    assertTrue(nights.contains(2036), tag + ": the 2036 night");
                    assertEquals("succession", g.electionKind(2036), tag + ": no third term: 2036 is the successor's race");
                    assertEquals(2, g.term(), tag);
                }
                default -> { }
            }
            if (path != Path.REMOVED) {
                var legacy = g.legacy().orElseThrow();
                assertEquals(10, legacy.measures().size(), tag + ": the verdict of history");
            }
        }
        // each mode serves two terms and one; between them, every way a first term ends (re-elected, defeated, a successor)
        for (Set<Path> m : List.of(full, governing))
            assertTrue(m.contains(Path.TWO_TERMS) && (m.contains(Path.ONE_TERM_DEFEATED) || m.contains(Path.ONE_TERM_SUCCESSOR)), "each mode serves one term and two: " + m);
        Set<Path> both = EnumSet.copyOf(full);
        both.addAll(governing);
        assertTrue(both.containsAll(Set.of(Path.TWO_TERMS, Path.ONE_TERM_DEFEATED, Path.ONE_TERM_SUCCESSOR)), "every ending of a first term: " + both);
        assertTrue(full.contains(Path.LOST_2028), "the Full Game can be lost in 2028");
    }

    /** The first lifecycle game, a Full Game whose president serves two terms, played to a date. */
    static GameEngine twoTermGameTo(LocalDate until) {
        GameEngine g = GameEngine.start(setup(0, 1));
        int guard = 0;
        while (!g.over() && g.date().isBefore(until) && guard++ < 6000) AutoPilot.playTurn(g);
        return g;
    }

    /** A Hormuz crisis begun now, with no orders given: it runs to its day-150 stalemate. */
    static void idleCrisisThrough(GameEngine g, LocalDate until) {
        g.apply(new Command.Hormuz(new HormuzOrder.Begin(0)));
        LocalDate last = g.date();
        int guard = 0;
        while (!g.over() && g.hormuzActive() && g.date().isBefore(until) && guard++ < 400) {
            EngineFixtures.answerAll(g, 0);
            g.apply(new Command.EndTurn());
            assertFalse(g.date().isBefore(last), "the clock went back from " + last + " to " + g.date());
            last = g.date();
        }
    }

    @Test
    void aCrisisDoesNotMoveTheMidterms() {
        GameEngine g = twoTermGameTo(LocalDate.of(2030, 9, 20));
        assertEquals(Phase.GOVERNING, g.phase());
        idleCrisisThrough(g, LocalDate.of(2030, 11, 20));
        assertTrue(g.hormuzActive(), "the crisis runs past Election Day");
        assertTrue(g.log().stream().anyMatch(l -> l.startsWith("2030-11-05  Midterms:")), "the midterms are held on November 5, in the crisis");
    }

    @Test
    void aCrisisDoesNotMoveTheReelectionCampaignOrElectionDay() {
        GameEngine g = twoTermGameTo(LocalDate.of(2032, 7, 5));
        assertEquals(Phase.GOVERNING, g.phase());
        idleCrisisThrough(g, LocalDate.of(2032, 11, 10));
        assertTrue(g.log().stream().anyMatch(l -> l.contains("The re-election campaign begins")), "the campaign begins in the crisis");
        assertTrue(g.result2032().isPresent(), "the 2032 vote is held");
        assertTrue(g.log().stream().anyMatch(l -> l.startsWith("2032-11-02  Election Night 2032")), "on November 2");
        // after the crisis the presidency goes on to its end, on the calendar's dates
        int guard = 0;
        LocalDate last = g.date();
        while (!g.over() && guard++ < 6000) {
            AutoPilot.playTurn(g);
            assertFalse(g.date().isBefore(last));
            last = g.date();
        }
        assertTrue(g.date().equals(LocalDate.of(2033, 1, 20)) || g.date().equals(LocalDate.of(2037, 1, 20))
                || g.log().stream().anyMatch(l -> l.contains("removed from office")), "ends on a term's last day: " + g.date());
        // an ended game takes no more turns (B-03): the last turn closes it, and any after is refused
        String ended = g.fingerprint();
        g.apply(new Command.EndTurn());
        assertEquals(Phase.GAME_OVER, g.phase());
        assertThrows(game.mandate.core.command.IllegalCommandException.class, () -> g.apply(new Command.EndTurn()));
        assertNotEquals(ended, g.fingerprint(), "only the phase moved");
    }

    @Test
    void theSuccessorsNightIsAnElectionNightAndSurvivesAReload() {
        // the first of a few games whose president serves two terms (which games do depends on how their races go)
        GameEngine g = null;
        for (long seed = 1; seed <= 12 && g == null; seed++) {
            GameEngine x = GameEngine.start(setup(0, seed));
            int guard = 0;
            while (!x.over() && x.electionNightPending() != 2036 && guard++ < 6000) AutoPilot.playTurn(x);
            if (x.electionNightPending() == 2036) g = x;
        }
        assertNotNull(g, "a two-term presidency reaches the 2036 night");
        assertEquals(2036, g.electionNightPending(), "the 2036 night waits to be watched");
        assertEquals("succession", g.electionKind(2036));
        assertEquals(LocalDate.of(2036, 11, 4), g.electionDayOf(2036), "the first Tuesday after the first Monday of November");
        assertFalse(g.date().isAfter(g.electionDayOf(2036).plusDays(7)), "the night comes with the week of Election Day, not a week later: " + g.date());
        GameEngine back = SaveManager.load(SaveManager.save(g));
        assertEquals(2036, back.electionNightPending(), "a reload on the night shows it again");
        assertEquals(g.latestPresidentialResult(), back.latestPresidentialResult(), "the same result, never recomputed");
        back.apply(new Command.ElectionNightSeen(2036));
        assertEquals(0, back.electionNightPending());
        assertEquals(0, SaveManager.load(SaveManager.save(back)).electionNightPending(), "watched, it is not shown again");
    }
}
