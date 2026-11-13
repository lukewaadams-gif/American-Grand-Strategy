package game.mandate.core.engine;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.executive.GovernmentState.SeatStatus;
import game.mandate.core.executive.NomineeType;
import game.mandate.core.executive.Post;
import game.mandate.core.game.GameSetup;
import game.mandate.core.opening.HundredDays;
import game.mandate.core.opening.OpeningAction;
import game.mandate.core.opening.Types.BillSize;
import game.mandate.core.opening.Types.PayFor;
import game.mandate.core.time.Phase;
import game.mandate.core.time.SimulationClock.Granularity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static game.mandate.core.engine.EngineFixtures.playUntil;
import static game.mandate.core.engine.EngineFixtures.setup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The first hundred days as a phase of one continuous game: one state, one command log, one save. */
class HundredDaysInGameTest {

    /** A game of the given length whose player won 2028 and has just been inaugurated. */
    static GameEngine inaugurated(long fromSeed, GameSetup.Mode length) {
        for (long seed = fromSeed; seed < fromSeed + 200; seed++) {
            GameEngine g = playUntil(new GameEngine(setup(seed).withMode(length)), x -> x.phase() == Phase.FIRST_100_DAYS);
            if (g.phase() == Phase.FIRST_100_DAYS) return g;
        }
        throw new AssertionError("no seed reached the inauguration");
    }

    @Test
    void theHundredDaysRunFromTheCampaignToTheReportAndTheTermGoesOn() {
        GameEngine g = inaugurated(1, GameSetup.Mode.FULL_GAME);
        // The election's winner governs, with the Congress the election produced and no cabinet yet.
        assertEquals(g.player().candidate().id(), g.state().government().presidentId());
        assertEquals(g.agenda().get(0), g.state().hundredDays().flagshipId());
        for (Post p : Post.values()) assertEquals(SeatStatus.VACANT, g.state().government().seat(p).status());
        assertEquals(LocalDate.of(2029, 1, 22), g.date());

        playUntil(g, x -> x.phase() != Phase.FIRST_100_DAYS);
        // day 100 brings the report, and the presidency goes on: the hundred days are governing's opening chapter
        assertEquals(Phase.GOVERNING, g.phase());
        assertFalse(g.over());
        assertTrue(g.dayHundredReport().isPresent(), "the 100-day report is there to read");
        assertEquals(15, g.state().hundredDays().week(), "fourteen weeks played");
        assertEquals(LocalDate.of(2029, 4, 30), g.date());
        // The hundred days' consequences were recorded in the engine's ledger, and their actions in its history.
        assertFalse(g.state().ledger().byCause("100 days, week ").isEmpty());
        assertTrue(g.state().ledger().byCause("100 days, week ").stream().anyMatch(c -> c.owner().equals("economy")));
        assertTrue(g.history().stream().anyMatch(c -> c instanceof Command.Act));
        assertTrue(SaveManager.consistent(SaveManager.save(g)), "the save replays to the same world");
    }

    @Test
    void aRefusedActionChangesNothing() {
        GameEngine g = inaugurated(1, GameSetup.Mode.FULL_GAME);
        String before = g.fingerprint();
        int commands = g.history().size();
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Act(new OpeningAction.CallVote(false))));        // no bill yet
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Act(new OpeningAction.Decide(99, 0))));          // no such decision
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Act(new OpeningAction.Negotiate("No Such Bloc",
                game.mandate.core.opening.Types.Concession.PROJECT))));
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.PushBill(g.agenda().get(0),
                game.mandate.core.congress.LegislativeStrategy.PARTY_LINE, 0)));                                                   // monthly commands wait
        g.apply(new Command.Act(new OpeningAction.Nominate(Post.TREASURY, NomineeType.EXPERT)));
        String afterNomination = g.fingerprint();
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Act(new OpeningAction.Nominate(Post.TREASURY, NomineeType.LOYALIST))));
        assertEquals(afterNomination, g.fingerprint());
        assertEquals(commands + 1, g.history().size());
        assertFalse(before.equals(afterNomination));
    }

    @Test
    void theViewRoutesActionsThroughTheCommandLog() {
        GameEngine g = inaugurated(1, GameSetup.Mode.FULL_GAME);
        HundredDays view = g.hundredDays().orElseThrow();
        view.act(new OpeningAction.DraftBill(BillSize.MEDIUM, PayFor.BORROW));
        assertEquals(new Command.Act(new OpeningAction.DraftBill(BillSize.MEDIUM, PayFor.BORROW)), g.history().get(g.history().size() - 1));
        assertEquals(2, g.attention());
    }

    @Test
    void aSaveInTheMiddleOfTheHundredDaysReproducesTheSameFuture() {
        GameEngine g = inaugurated(3, GameSetup.Mode.FULL_GAME);
        playUntil(g, x -> x.state().hundredDays().week() >= 6);
        String save = SaveManager.save(g);
        GameEngine loaded = SaveManager.load(save), replayed = SaveManager.load(SaveManager.saveReplayOnly(g));
        assertEquals(g.fingerprint(), loaded.fingerprint());
        assertEquals(g.fingerprint(), replayed.fingerprint());
        for (GameEngine x : new GameEngine[]{g, loaded, replayed}) playUntil(x, y -> y.phase() != Phase.FIRST_100_DAYS);
        assertEquals(g.fingerprint(), loaded.fingerprint());
        assertEquals(g.fingerprint(), replayed.fingerprint());
    }

    @Test
    void aFullTermGoesOnInWeeklyTurnsWithTheCabinetFilled() {
        GameEngine g = inaugurated(5, GameSetup.Mode.FULL_GAME);
        playUntil(g, x -> x.phase() != Phase.FIRST_100_DAYS);
        assertEquals(Phase.GOVERNING, g.phase());
        assertEquals(LocalDate.of(2029, 4, 30), g.date());          // the Monday after the fourteenth week
        assertEquals(Granularity.WEEKLY, g.clock().mode());
        for (Post p : Post.values()) {
            SeatStatus s = g.state().government().seat(p).status();
            assertTrue(s == SeatStatus.CONFIRMED || s == SeatStatus.ACTING, p + " " + s);
            assertNotNull(g.state().government().seat(p).name());
        }
        AutoPilot.playTurn(g);
        assertEquals(LocalDate.of(2029, 5, 7), g.date());
    }
}
