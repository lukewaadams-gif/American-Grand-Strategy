package game.mandate.core.engine;

import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.game.Legacy;
import game.mandate.core.terms.TermsEngine;
import game.mandate.core.terms.TermsOrder;
import game.mandate.core.terms.TermsState;
import game.mandate.core.time.Phase;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static game.mandate.core.engine.EngineFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Milestone 9: the second term, the lame-duck weeks, stepping aside, the succession and one verdict on the presidency. */
class TermsTest {

    /** A game played (by the autopilot) to its 2032 result, from the first seed at or after {@code from} that gets there with the given outcome. */
    static GameEngine to2032(long from, boolean reelected) {
        for (long seed = from; seed < from + 60; seed++) {
            GameEngine g = playUntil(new GameEngine(setup(seed)), x -> x.result2032().isPresent());
            if (g.result2032().isEmpty() || g.over()) continue;
            if ((g.result2032().get().winner() == g.player().party()) == reelected) return g;
        }
        throw new AssertionError("no seed with that 2032 result");
    }

    @Test
    void aReElectedPresidentServesASecondTermToJanuary2037() {
        GameEngine g = to2032(1, true);
        assertEquals(Phase.GOVERNING, g.phase(), "the presidency goes on after a re-election");
        assertEquals(1, g.term());
        TermsState t = g.terms().orElseThrow();
        assertTrue(t.questions().stream().anyMatch(q -> q.kind().equals("theme")), "the second term's theme is asked");
        String theme = t.questions().stream().filter(q -> q.kind().equals("theme")).findFirst().orElseThrow().id();
        g.apply(new Command.Terms(new TermsOrder.Answer(theme, 1)));
        assertEquals(3, g.terms().orElseThrow().goals().size(), "the theme sets three legacy goals");

        playUntil(g, x -> x.term() == 2);
        assertEquals(2, g.term(), "sworn in again");
        assertFalse(g.date().isBefore(LocalDate.of(2033, 1, 20)));
        assertTrue(g.date().isBefore(LocalDate.of(2033, 2, 1)));
        assertEquals(0, g.termWeek(), "the term's week starts again");
        assertTrue(g.terms().orElseThrow().inaugural() >= 0, "the inaugural address was given");
        assertTrue(g.lameDuck() > 0 && g.lameDuck() < 0.4, "a second term starts a little lame: " + g.lameDuck());

        playUntil(g, x -> x.state().midtermsHeld());
        assertTrue(g.date().getYear() == 2034, "the 2034 midterms: " + g.date());
        assertNotNull(g.terms().orElseThrow().midtermHouse());
        assertTrue(g.lameDuck() >= 0.4, "faster decline after the midterms");

        playUntil(g, x -> x.terms().orElseThrow().race() != null);
        TermsState.Race r = g.terms().orElseThrow().race();
        assertNotNull(r, "the race to succeed the president");
        assertEquals(2036, r.year());
        assertTrue(r.rivals().size() >= 2);

        playUntil(g, x -> x.terms().orElseThrow().leaving());
        r = g.terms().orElseThrow().race();
        assertTrue(r.done());
        assertNotNull(r.nomineeContender());
        assertNotNull(r.opponent());
        assertEquals(538, r.evMe() + r.evOpp());
        assertEquals(1.0, g.lameDuck());

        playUntil(g, GameEngine::over);
        assertTrue(g.over());
        assertEquals("served", g.terms().orElseThrow().ending());
        assertFalse(g.date().isBefore(LocalDate.of(2037, 1, 20)));
        Legacy l = g.legacy().orElseThrow();
        assertEquals(10, l.measures().size(), "the historians' ten measures");
        assertEquals(l.score(), l.measures().stream().mapToInt(Integer::intValue).sum());
        assertTrue(l.rank().startsWith("Historians rank you "));
        assertTrue(l.lines().stream().anyMatch(x -> x.startsWith("Legacy goal: ")), "the legacy goals count");
        assertTrue(l.lines().stream().anyMatch(x -> x.startsWith("Your successor: ")));
    }

    @Test
    void aDefeatedPresidentPlaysTheLameDuckWeeks() {
        GameEngine g = to2032(1, false);
        assertEquals(Phase.GOVERNING, g.phase(), "the lame-duck weeks are played");
        TermsState t = g.terms().orElseThrow();
        assertTrue(t.leaving());
        assertEquals("lost", t.ending());
        assertEquals(LocalDate.of(2033, 1, 20), t.handoff());
        assertEquals(1.0, g.lameDuck());
        int weeks = 0;
        while (!g.over() && weeks++ < 20) {
            answerAll(g, 0);
            g.apply(new Command.EndTurn());
        }
        assertTrue(g.over(), "the handoff ends the game");
        assertTrue(weeks >= 8 && weeks <= 13, "about eleven weeks from Election Day to the handoff: " + weeks);
        assertTrue(g.legacy().orElseThrow().lines().get(0).startsWith("You lost your bid for a second term"));
    }

    @Test
    void aPresidentWhoStepsAsideCampaignsForASuccessor() {
        GameEngine g = governing(3);
        playUntil(g, x -> x.terms().orElseThrow().questions().stream().anyMatch(q -> q.kind().equals("run")));
        TermsState t = g.terms().orElseThrow();
        String run = t.questions().stream().filter(q -> q.kind().equals("run")).findFirst().orElseThrow().id();
        assertTrue(g.date().getYear() == 2031, "asked a year before the election: " + g.date());
        g.apply(new Command.Terms(new TermsOrder.Answer(run, 1)));
        assertEquals(TermsState.RUN_STEPPED_ASIDE, g.terms().orElseThrow().runStage());
        assertNotNull(g.terms().orElseThrow().race(), "the race to succeed the president begins");
        assertEquals(0.5, g.lameDuck());
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Terms(new TermsOrder.Endorse(9))));
        g.apply(new Command.Terms(new TermsOrder.Endorse(0)));
        assertEquals(0, g.terms().orElseThrow().race().endorsed());
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Terms(new TermsOrder.Endorse(1))), "one endorsement");
        playUntil(g, x -> x.terms().orElseThrow().leaving());
        assertNotEquals(Phase.REELECTION_CAMPAIGN_2032, g.phase(), "no re-election campaign");
        assertTrue(g.result2032().isEmpty());
        assertEquals("retired", g.terms().orElseThrow().ending());
        assertTrue(g.terms().orElseThrow().race().done());
        playUntil(g, GameEngine::over);
        assertTrue(g.over());
        assertEquals(LocalDate.of(2033, 1, 20).getYear(), g.date().getYear());
    }

    @Test
    void theSuccessorsMarginFollowsApproval() {
        TermsEngine e = new TermsEngine();
        TermsState s = e.newTerms();
        var low = context(38);
        var high = context(58);
        var out = e.week(s, low, new game.mandate.core.rng.DeterministicRandomManager(1));
        assertNotNull(out);
        TermsState.Race r = new TermsState.Race();
        assertTrue(e.national(s, high, r) > e.national(s, low, r) + 8, "approval rubs off on the nominee");
    }

    @Test
    void lameDuckDeclinesThroughASecondTerm() {
        TermsState s = new TermsEngine().newTerms();
        assertEquals(0, TermsEngine.lameDuck(s, LocalDate.of(2030, 1, 1), LocalDate.of(2029, 1, 20), LocalDate.of(2030, 11, 5), LocalDate.of(2033, 1, 20), false));
        assertEquals(0, TermsEngine.lameDuck(null, LocalDate.of(2030, 1, 1), LocalDate.of(2029, 1, 20), LocalDate.of(2030, 11, 5), LocalDate.of(2033, 1, 20), false));
    }

    static TermsEngine.Context context(double approval) {
        return new TermsEngine.Context(LocalDate.of(2036, 9, 1), game.mandate.core.state.Party.DEMOCRAT, "Elena Reyes", approval, 0.8, 2, 4.5, 4.5, 0, false, 1, 20,
                java.util.Map.of(), java.util.Map.of("PA", 0.0), java.util.Map.of("PA", 19), java.util.Map.of("PA", 17.0), java.util.List.of(), java.util.List.of(),
                "Sam Lee", 60, 60, 60, "AZ", LocalDate.of(2036, 11, 4), 2, 1);
    }
}
