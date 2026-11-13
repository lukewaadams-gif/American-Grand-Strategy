package game.mandate.ui;

import game.mandate.core.engine.GameEngine;
import game.mandate.core.engine.SaveManager;
import game.mandate.core.time.Phase;
import game.mandate.ui.kit.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Interaction flows, played through the interface's own buttons, with the automated checks run along the way. */
class FlowTest {
    static Map<String, String> moments;

    @BeforeAll static void fixtures() { moments = Fixtures.moments(); }

    @AfterEach void reset() { Theme.set(Theme.Palette.STANDARD, 1.0); }

    static Session fresh(Path home) {
        Session s = new Session(home);
        s.settings.confirmUnspent = false;
        return s;
    }

    @Test void theWizardStartsACampaignAndTheWeeksRun(@TempDir Path home) {
        App app = new App(fresh(home));
        Driver d = new Driver(app, 1600, 900);
        assertEquals(App.View.MENU, app.view());
        assertTrue(d.problems().isEmpty(), d.problems().toString());
        d.press("New game");
        assertEquals(App.View.NEW_GAME, app.view());
        for (int i = 0; i < 3; i++) {
            assertTrue(d.problems().isEmpty(), "wizard step " + (i + 1) + ": " + d.problems());
            if (i == 2) d.pickFirstOption();           // the candidate step needs a choice
            d.pressStarting("Next: ");
        }
        d.press("Begin the campaign");
        assertEquals(App.View.GAME, app.view());
        assertEquals(App.ScreenId.CAMPAIGN, app.screen());
        GameEngine g = app.session.g();
        assertEquals(Phase.GENERAL_CAMPAIGN_2028, g.phase());
        int polls = g.polls().size();
        for (int week = 0; week < 3; week++) runTurn(d);
        assertTrue(app.session.g().polls().size() >= polls + 3, "three weeks ran");
        assertTrue(Files.exists(home.resolve("save.json")), "the game autosaves at the end of a turn");
        assertTrue(d.problems().isEmpty(), d.problems().toString());
    }

    @Test void theCampaignEndsOnElectionNight(@TempDir Path home) {
        App app = new App(fresh(home));
        Driver d = new Driver(app, 1600, 900);
        d.press("New game");
        for (int i = 0; i < 3; i++) { if (i == 2) d.pickFirstOption(); d.pressStarting("Next: "); }
        d.press("Begin the campaign");
        for (int guard = 0; guard < 200 && app.session.g().result2028().isEmpty(); guard++) {
            if (d.modalOpen()) d.answerModal();
            else d.press(runLabel(app));
        }
        assertTrue(app.session.g().result2028().isPresent(), "the election was held");
        assertTrue(d.modalShows("Election night"), "election night comes forward on its own");
        assertTrue(d.modalShows("You win the presidency") || d.modalShows("You lose"));
        boolean won = app.session.g().result2028().get().winner() == app.session.g().player().party();
        // A loss explains itself and offers a replay; a win leads to the transition.
        if (won) assertTrue(d.modalShows("On to the transition"));
        else assertTrue(d.modalShows("What happened") && d.modalShows("Replay the campaign in the same world"));
        assertTrue(d.problems().isEmpty(), d.problems().toString());
    }

    @Test void fromTheTransitionToTheHundredDayReport(@TempDir Path home) {
        Session s = Fixtures.session(home, moments.get("transition"));
        s.settings.confirmUnspent = false;
        App app = new App(s);
        app.startedGame();
        Driver d = new Driver(app, 1600, 900);
        assertEquals(App.ScreenId.OVAL, app.screen());
        d.press("Set this agenda and take the oath");
        assertEquals(Phase.FIRST_100_DAYS, app.session.g().phase());
        int turns = 0;
        for (int guard = 0; guard < 120 && app.session.g().dayHundredReport().isEmpty(); guard++) {
            if (d.modalOpen()) { d.answerModal(); continue; }
            List<String> p = d.problems();
            assertTrue(p.isEmpty(), "week " + app.session.g().hundredDays().map(h -> h.week()).orElse(0) + " on " + app.screen() + ": " + p);
            d.press("End the week");
            turns++;
        }
        assertTrue(app.session.g().dayHundredReport().isPresent(), "day 100 was reached");
        assertTrue(turns >= 14, "fourteen weekly turns at least, was " + turns);
        while (d.modalOpen()) d.answerModal();
        assertEquals(App.ScreenId.RESULTS, app.screen(), "the report comes forward on its own");
        assertTrue(d.shows("The 100-day report"));
        assertTrue(d.problems().isEmpty(), d.problems().toString());
    }

    @Test void undoReturnsToExactlyTheStateBefore(@TempDir Path home) {
        Session s = Fixtures.session(home, moments.get("campaign"));
        s.settings.autoOpenDecisions = false;
        App app = new App(s);
        app.startedGame();
        Driver d = new Driver(app, 1600, 900);
        String before = SaveManager.save(app.session.g());
        d.press("Run the week");
        assertNotEquals(before, SaveManager.save(app.session.g()));
        d.press("Undo");
        assertEquals(before, SaveManager.save(app.session.g()), "undo replays the history without the last command");
    }

    @Test void rewindReturnsToTheStartOfATurn(@TempDir Path home) {
        Session s = Fixtures.session(home, moments.get("campaign"));
        s.settings.autoOpenDecisions = false;
        App app = new App(s);
        app.startedGame();
        Driver d = new Driver(app, 1600, 900);
        runTurn(d);
        int turnStart = app.session.g().history().size();
        String startOfTurn = SaveManager.save(app.session.g());
        String label = "Start of the turn of " + Notable.date(java.time.LocalDate.parse(app.session.turnDate(turnStart).orElseThrow()));
        runTurn(d);
        runTurn(d);
        assertTrue(app.session.g().history().size() > turnStart + 1);
        app.open(App.ScreenId.SAVES);
        d.press(label);
        assertTrue(d.modalShows("Rewind?"), "rewinding asks first");
        d.press("Rewind");
        assertEquals(startOfTurn, SaveManager.save(app.session.g()), "the game is exactly as it was at the start of that turn");
        assertTrue(d.problems().isEmpty(), d.problems().toString());
    }

    /** Ends one turn, answering any decision that blocks it as a player would. */
    static void runTurn(Driver d) {
        int before = d.app.session.g().history().size();
        for (int guard = 0; guard < 20 && d.app.session.g().history().size() == before; guard++) {
            if (d.modalOpen()) d.answerModal();
            else d.press(runLabel(d.app));
        }
        while (d.modalOpen()) d.answerModal();
        // answering a decision is itself a command; run until a turn has actually ended
        if (d.app.session.g().history().stream().skip(before).noneMatch(c -> c instanceof game.mandate.core.command.Command.EndTurn)) runTurn(d);
    }

    @Test void everyScreenHasAShortcutAndHelpListsThemAll(@TempDir Path home) {
        App app = new App(fresh(home));
        Driver d = new Driver(app, 1600, 900);
        for (App.ScreenId id : App.ScreenId.values()) {
            KeyStroke k = KeyStroke.getKeyStroke(id.keys);
            assertNotNull(k, id + " has a valid key");
            assertNotNull(app.stage.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(k), id + " is bound");
        }
        game.mandate.ui.screens.Help.open(app);
        d.settle();
        for (App.Shortcut sc : app.shortcuts()) assertTrue(d.modalShows(sc.what()), "help lists " + sc.keys());
        assertTrue(d.problems().isEmpty(), d.problems().toString());
    }

    @Test void theAdvisorIsAPanelOnWideWindowsAndABannerOnNarrowOnes(@TempDir Path home) {
        for (int[] size : new int[][] {{1600, 900}, {1280, 720}}) {
            App app = new App(Fixtures.session(home.resolve("w" + size[0]), moments.get("crisis")));
            app.session.settings.autoOpenDecisions = false;
            app.startedGame();
            Driver d = new Driver(app, size[0], size[1]);
            app.refresh();
            d.settle();
            if (size[0] >= 1440) assertTrue(d.shows("Your chief adviser"), "panel at " + size[0]);
            else assertTrue(d.shows("Adviser: ") && d.shows("Full brief"), "banner at " + size[0]);
        }
    }

    @Test void complexityChangesWhatIsShownNotWhatHappens(@TempDir Path home) {
        String moment = moments.get("campaign");
        App guided = new App(Fixtures.session(home.resolve("g"), moment));
        guided.session.settings.complexity = Settings.Complexity.GUIDED;
        guided.startedGame();
        Driver g = new Driver(guided, 1600, 900);
        App standard = new App(Fixtures.session(home.resolve("s"), moment));
        standard.startedGame();
        Driver s = new Driver(standard, 1600, 900);
        assertFalse(g.shows("Voter groups"), "guided hides the crosstabs");
        assertTrue(s.shows("Voter groups"), "standard shows them");
        guided.session.settings.autoOpenDecisions = false;
        standard.session.settings.autoOpenDecisions = false;
        runTurn(g);
        runTurn(s);
        assertEquals(SaveManager.save(guided.session.g()), SaveManager.save(standard.session.g()), "the same turn plays out the same at either level");
    }

    @Test void tipsShowWhereTheyBelongAndGoAwayWhenDismissed(@TempDir Path home) {
        Session s = Fixtures.session(home, moments.get("crisis"));
        s.settings.autoOpenDecisions = false;
        App app = new App(s);
        app.startedGame();
        Driver d = new Driver(app, 1600, 900);
        app.open(App.ScreenId.SECURITY);
        assertTrue(d.shows("Intelligence estimates the other side's resolve"), "the crisis tip is on Security");
        app.open(App.ScreenId.ECONOMY);
        assertFalse(d.shows("Intelligence estimates the other side's resolve"), "but not on Economy");
        app.open(App.ScreenId.SECURITY);
        d.press("Got it");
        assertFalse(d.shows("Intelligence estimates the other side's resolve"), "dismissed tips stay dismissed");
        assertTrue(app.session.seenLessons().contains("days.crisis"));
    }

    /** The campaign's end-turn button: "Run the day" in the final stretch, "Run the week" before it. */
    static String runLabel(App app) {
        return app.session.g().clock().mode() == game.mandate.core.time.SimulationClock.Granularity.DAILY ? "Run the day" : "Run the week";
    }
}
