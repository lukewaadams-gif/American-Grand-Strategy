package game.mandate.core.engine;

import game.mandate.core.campaign.Issue;
import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.game.GameSetup;
import game.mandate.core.news.News;
import game.mandate.core.opening.Strategy;
import game.mandate.core.politics.PoliticsEngine;
import game.mandate.core.presidency.Activity;
import game.mandate.core.presidency.PresidencyOrder;
import game.mandate.core.presidency.PresidentEngine;
import game.mandate.core.presidency.PresidentState;
import game.mandate.core.time.Phase;
import game.mandate.core.time.SimulationClock.Granularity;
import org.junit.jupiter.api.Test;

import java.time.temporal.ChronoUnit;

import static game.mandate.core.engine.EngineFixtures.answerAll;
import static game.mandate.core.engine.EngineFixtures.playUntil;
import static game.mandate.core.engine.EngineFixtures.setup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The presidency port's milestone 3: Resolute Desk's week, its approval and the weekly turns in office. */
class PresidencyTest {

    static GameEngine inOffice(long fromSeed, GameSetup.Mode length) {
        for (long seed = fromSeed; seed < fromSeed + 200; seed++) {
            GameEngine g = playUntil(new GameEngine(setup(seed).withMode(length)), x -> x.phase() == Phase.FIRST_100_DAYS);
            if (g.phase() == Phase.FIRST_100_DAYS) return g;
        }
        throw new AssertionError("no seed reached the inauguration");
    }

    static void order(GameEngine g, PresidencyOrder o) { g.apply(new Command.Presidency(o)); }

    /** One week of the hundred days, answering what comes up. */
    static void week(GameEngine g) {
        answerAll(g, 0);
        Strategy.STRATEGIST.playWeek(g.hundredDays().orElseThrow());
    }

    @Test
    void airForceOneFliesByResoluteDesksRule() {
        PresidentEngine e = new PresidentEngine(game.mandate.core.electorate.StateCatalog.load());
        assertEquals(0.3, e.flight("MD"), 1e-9, "a short hop");
        assertEquals(10.5, e.flight("HI"), 1e-9);
        assertEquals(8.5, e.flight("AK"), 1e-9);
        double ca = e.flight("CA");
        assertTrue(ca > 4.8 && ca < 5.8, "Washington to California, about five hours: " + ca);
        assertTrue(e.flight("PA") < e.flight("OH") && e.flight("OH") < e.flight("TX"));
    }

    @Test
    void theFirstWeekIsResoluteDesksRoutine() {
        GameEngine g = inOffice(1, GameSetup.Mode.FULL_GAME);
        PresidentState p = g.president().orElseThrow();
        for (int d = 0; d < 5; d++) assertEquals(Activity.POLICY, p.activity(d * 3), "weekday mornings are policy time");
        assertEquals(Activity.REMARKS, p.activity(4));
        assertEquals(Activity.HILL, p.activity(7));
        assertEquals(Activity.TRIP, p.activity(10));
        assertNotNull(p.argument(10), "Thursday's trip goes to a state");
        assertEquals(Activity.FAMILY, p.activity(14));
        assertEquals(Activity.EXERCISE, p.activity(15));
        assertEquals(Activity.FREE, p.activity(20));
        assertEquals(85, p.stamina(), 1e-9);
        assertEquals(61, p.age(), "Diane Marsh is 61 at the inauguration");
    }

    @Test
    void theCalendarKeepsResoluteDesksRules() {
        GameEngine g = inOffice(1, GameSetup.Mode.FULL_GAME);
        String before = SaveManager.save(g);
        assertThrows(IllegalCommandException.class, () -> order(g, new PresidencyOrder.Slot(0, Activity.CAMP_DAVID, null)), "Camp David is for weekends");
        assertThrows(IllegalCommandException.class, () -> order(g, new PresidencyOrder.Slot(15, Activity.HILL, null)), "Congress is away at weekends");
        assertThrows(IllegalCommandException.class, () -> order(g, new PresidencyOrder.Slot(0, Activity.WORK_LATE, null)), "working late is for evenings");
        assertThrows(IllegalCommandException.class, () -> order(g, new PresidencyOrder.Slot(2, Activity.CABINET, null)), "no cabinet in the evening");
        assertThrows(IllegalCommandException.class, () -> order(g, new PresidencyOrder.Slot(1, Activity.CAMPAIGN, "PA")), "no campaign stops out of season");
        assertThrows(IllegalCommandException.class, () -> order(g, new PresidencyOrder.Slot(1, Activity.TRIP, null)), "a trip needs a state");
        assertThrows(IllegalCommandException.class, () -> order(g, new PresidencyOrder.Slot(1, Activity.TRIP, "DC")), "the capital is home");
        assertThrows(IllegalCommandException.class, () -> order(g, new PresidencyOrder.Slot(1, Activity.ABROAD, null)), "trips abroad come with the world");
        assertEquals(before, SaveManager.save(g), "refused orders change nothing");
        order(g, new PresidencyOrder.Slot(16, Activity.CAMP_DAVID, null));
        order(g, new PresidencyOrder.Slot(2, Activity.WORK_LATE, null));
        order(g, new PresidencyOrder.Slot(1, Activity.TRIP, "CA"));
        PresidentState p = g.president().orElseThrow();
        assertEquals(Activity.CAMP_DAVID, p.activity(16));
        assertEquals("CA", p.argument(1));
        // a trip to California costs its slot, plus the flight, made heavier by age
        PresidentEngine e = g.presidentEngine();
        double age = 1 + (61 - 60) * 0.012;
        assertEquals((-5 - Math.min(6, e.flight("CA") * 0.9)) * age, e.slotCost(p, 1), 1e-9);
    }

    @Test
    void readyMadeWeeksAndTheChiefOfStaff() {
        GameEngine g = inOffice(1, GameSetup.Mode.FULL_GAME);
        order(g, new PresidencyOrder.Week(PresidencyOrder.Preset.ROAD));
        PresidentState p = g.president().orElseThrow();
        for (int s : new int[]{1, 4, 7, 10}) assertEquals(Activity.TRIP, p.activity(s));
        order(g, new PresidencyOrder.Week(PresidencyOrder.Preset.REST));
        for (int s = 15; s < 20; s++) assertEquals(Activity.CAMP_DAVID, p.activity(s));
        for (int d = 0; d < 5; d++) assertEquals(Activity.REST, p.activity(d * 3 + 2));
        order(g, new PresidencyOrder.Week(PresidencyOrder.Preset.CLEAR));
        for (int s = 0; s < 21; s++) assertEquals(Activity.FREE, p.activity(s));
        double resting = g.presidentEngine().projectedStamina(p);
        order(g, new PresidencyOrder.Delegate(true));
        assertTrue(p.delegated());
        assertEquals(Activity.POLICY, p.activity(0), "the chief of staff writes the routine at once");
        // the routine costs more stamina than an empty week
        assertTrue(g.presidentEngine().projectedStamina(p) < resting);
    }

    @Test
    void aHardWeekTiresThePresidentAndARestWeekRestores() {
        GameEngine hard = inOffice(1, GameSetup.Mode.FULL_GAME), easy = SaveManager.load(SaveManager.save(hard));
        order(hard, new PresidencyOrder.Week(PresidencyOrder.Preset.ROAD));
        for (int d = 0; d < 5; d++) order(hard, new PresidencyOrder.Slot(d * 3 + 2, Activity.WORK_LATE, null));
        order(easy, new PresidencyOrder.Week(PresidencyOrder.Preset.REST));
        double projected = hard.presidentEngine().projectedStamina(hard.president().orElseThrow());
        week(hard);
        week(easy);
        PresidentState h = hard.president().orElseThrow(), e = easy.president().orElseThrow();
        assertTrue(h.stamina() < e.stamina() - 15, "road and late nights " + h.stamina() + " vs rest " + e.stamina());
        assertTrue(h.sleep() < 5.6 && e.sleep() > 7, "sleep " + h.sleep() + " vs " + e.sleep());
        assertTrue(h.stress() > e.stress(), "stress " + h.stress() + " vs " + e.stress());
        // Resolute Desk's projection reads stress before the week's slots add to it, so it is close, not exact
        if (h.sickWeeks() == 0) assertEquals(projected, h.stamina(), 1.0, "the projection is about what happens (when no illness intervenes)");
        assertEquals(1, h.week());
        assertTrue(h.lastWeek().stream().anyMatch(l -> l.contains("stops")), "the week is summed up: " + h.lastWeek());
    }

    @Test
    void theWeeksWorkMovesOpinion() {
        GameEngine g = inOffice(1, GameSetup.Mode.FULL_GAME);
        for (int d = 0; d < 5; d++) order(g, new PresidencyOrder.Slot(d * 3, Activity.POLICY, Issue.HEALTHCARE.name()));
        order(g, new PresidencyOrder.Slot(1, Activity.TRIP, "MI"));
        double command = g.state().politics().command(Issue.HEALTHCARE), standing = g.state().politics().standing(Issue.HEALTHCARE);
        week(g);
        assertTrue(g.state().politics().command(Issue.HEALTHCARE) > command + 12, "five policy meetings build command of health care");
        assertTrue(g.state().politics().standing(Issue.HEALTHCARE) > standing, "and standing on it");
        assertTrue(g.state().politics().stateStanding("MI") > 1, "a visit lifts the president in Michigan");
        var owners = g.state().ledger().byCause("The president's week").stream().map(ConsequenceLedger.Consequence::owner).distinct().toList();
        assertEquals(java.util.List.of("politics"), owners, "the week's effects go through the politics engine");
    }

    @Test
    void approvalFollowsResoluteDesksModel() {
        GameEngine g = inOffice(1, GameSetup.Mode.FULL_GAME);
        var s = g.state().politics();
        double margin = g.result2028().orElseThrow().marginFor(g.player().party());
        assertEquals(Math.max(0.5, Math.min(15, 2 + 0.55 * margin)), s.mandate(), 1e-9, "the mandate comes from the popular vote");
        PoliticsEngine pe = new PoliticsEngine(game.mandate.core.politics.PoliticsParams.from(g.tuning()));
        double[] b = pe.bounds();
        assertEquals(34.2, b[0], 1e-9, "2029's partisan floor");
        assertEquals(66.4, b[1], 1e-9, "and ceiling");
        double a0 = g.approval(), h0 = s.honeymoon();
        assertTrue(a0 > 40 && a0 < 62, "a new president starts near the target: " + a0);
        for (int i = 0; i < 6; i++) week(g);
        assertTrue(s.honeymoon() < h0, "the honeymoon fades");
        // the breakdowns add up to the national number
        double sum = 0;
        for (var c : game.mandate.core.politics.Coalition.values()) sum += g.tuning().d("politics.weight." + c.name().toLowerCase()) * s.approval(c);
        assertEquals(g.approval(), sum, 0.05, "the coalitions add up");
        assertEquals(51, s.stateApproval().size());
        assertTrue(s.issueApproval().values().stream().allMatch(v -> v >= 10 && v <= 88));
    }

    @Test
    void inOfficeEveryTurnIsAWeek() {
        GameEngine g = inOffice(5, GameSetup.Mode.FULL_GAME);
        playUntil(g, x -> x.phase() == Phase.GOVERNING);
        assertEquals(Granularity.WEEKLY, g.clock().mode());
        var d0 = g.date();
        for (int i = 0; i < 9; i++) { answerAll(g, 0); g.apply(new Command.EndTurn()); }
        assertEquals(63, ChronoUnit.DAYS.between(d0, g.date()));
        assertEquals(java.time.DayOfWeek.MONDAY, g.date().getDayOfWeek());
        assertTrue(g.president().orElseThrow().week() >= 23, "the president's weeks go on: " + g.president().orElseThrow().week());
    }

    @Test
    void theEraNewsAndTheWeeksNewsAreWeighed() {
        GameEngine g = inOffice(1, GameSetup.Mode.FULL_GAME);
        playUntil(g, x -> x.president().map(p -> p.week() >= 20).orElse(false));
        long era = g.news().stream().filter(n -> n.section() != null).count();
        assertTrue(era >= 10, "one or two of the era's stories a week: " + era);
        for (News n : g.news()) assertTrue(n.weight() > 0 && !n.head().contains("{state}"), n.head());
    }

    @Test
    void presidencyOrdersSaveAndReplay() {
        GameEngine g = inOffice(3, GameSetup.Mode.FULL_GAME);
        order(g, new PresidencyOrder.Slot(2, Activity.WORK_LATE, null));
        order(g, new PresidencyOrder.WatchPreset(5));
        order(g, new PresidencyOrder.WatchPriority(0, 0.9));
        order(g, new PresidencyOrder.WatchStop(2, false));
        order(g, new PresidencyOrder.WatchWeek());
        for (int i = 0; i < 4; i++) week(g);
        order(g, new PresidencyOrder.Delegate(true));
        week(g);
        assertEquals(6, g.president().orElseThrow().plan().preset(), "a priority set by hand makes the plan the player's own");
        assertFalse(g.president().orElseThrow().plan().stops(2));
        GameEngine loaded = SaveManager.load(SaveManager.save(g)), replayed = SaveManager.load(SaveManager.saveReplayOnly(g));
        assertEquals(g.fingerprint(), loaded.fingerprint());
        assertEquals(g.fingerprint(), replayed.fingerprint());
        for (GameEngine x : new GameEngine[]{g, loaded, replayed}) for (int i = 0; i < 3; i++) week(x);
        assertEquals(g.fingerprint(), loaded.fingerprint());
        assertEquals(g.fingerprint(), replayed.fingerprint());
    }
}
