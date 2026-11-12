package game.mandate.ui;

import game.mandate.ui.App.ScreenId;
import game.mandate.ui.kit.Theme;
import game.mandate.ui.screens.Decisions;
import game.mandate.ui.screens.Help;
import game.mandate.ui.screens.SaveLoad;
import game.mandate.ui.screens.Results;
import game.mandate.ui.screens.SettingsScreen;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Renders every screen, at the supported window sizes and in the accessibility modes, to PNG files, and runs the
 * automated checks on each. {@code mandate screens [out-dir]}; the tests call {@link #shoot} without writing files.
 */
public final class Screenshots {
    private Screenshots() {}

    /** One picture: a moment of play, a view, a window size, a display setting, and anything to open on top. */
    public record Shot(String name, String moment, ScreenId screen, int width, int height, double scale, Theme.Palette palette,
                       Settings.Complexity complexity, Consumer<App> extra) {}

    public static final int[][] SIZES = {{1280, 720}, {1600, 900}, {1920, 1080}};

    public static List<Shot> shots() {
        List<Shot> l = new ArrayList<>();
        Theme.Palette std = Theme.Palette.STANDARD;
        Settings.Complexity st = Settings.Complexity.STANDARD;
        for (int[] s : SIZES) l.add(new Shot("menu-" + s[0], null, null, s[0], s[1], 1.0, std, st, null));
        for (int step = 0; step < 4; step++) {
            int k = step;
            l.add(new Shot("newgame-" + (step + 1), null, null, 1600, 900, 1.0, std, st, app -> { app.showNewGame(); for (int i = 0; i < k; i++) { app.wizard().chooseFirstCandidate(); app.wizard().next(); } app.refresh(); }));
        }
        for (ScreenId s : List.of(ScreenId.OVERVIEW, ScreenId.CAMPAIGN, ScreenId.CONGRESS, ScreenId.ECONOMY, ScreenId.SECURITY, ScreenId.TIMELINE, ScreenId.SAVES, ScreenId.SETTINGS))
            l.add(new Shot("campaign-" + s.name().toLowerCase(), "campaign", s, 1600, 900, 1.0, std, st, null));
        l.add(new Shot("campaign-hq-1280", "campaign", ScreenId.CAMPAIGN, 1280, 720, 1.0, std, st, null));
        l.add(new Shot("campaign-hq-1920", "campaign", ScreenId.CAMPAIGN, 1920, 1080, 1.0, std, st, null));
        l.add(new Shot("campaign-hq-high-contrast", "campaign", ScreenId.CAMPAIGN, 1600, 900, 1.0, Theme.Palette.HIGH_CONTRAST, st, null));
        l.add(new Shot("campaign-hq-colour-blind", "campaign", ScreenId.CAMPAIGN, 1600, 900, 1.0, Theme.Palette.COLOUR_BLIND_SAFE, st, null));
        l.add(new Shot("campaign-hq-guided", "campaign", ScreenId.CAMPAIGN, 1600, 900, 1.0, std, Settings.Complexity.GUIDED, null));
        l.add(new Shot("debate-decision", "debate", ScreenId.CAMPAIGN, 1600, 900, 1.0, std, st, Decisions::open));
        l.add(new Shot("transition-oval", "transition", ScreenId.OVAL, 1600, 900, 1.0, std, st, null));
        l.add(new Shot("transition-oval-1280", "transition", ScreenId.OVAL, 1280, 720, 1.0, std, st, null));
        l.add(new Shot("week1-oval", "week1", ScreenId.OVAL, 1600, 900, 1.0, std, st, null));
        l.add(new Shot("week1-congress", "week1", ScreenId.CONGRESS, 1600, 900, 1.0, std, st, null));
        for (ScreenId s : List.of(ScreenId.OVERVIEW, ScreenId.OVAL, ScreenId.CONGRESS, ScreenId.ECONOMY, ScreenId.SECURITY, ScreenId.TIMELINE))
            l.add(new Shot("crisis-" + s.name().toLowerCase(), "crisis", s, 1600, 900, 1.0, std, st, null));
        l.add(new Shot("crisis-decision", "crisis", ScreenId.SECURITY, 1600, 900, 1.0, std, st, Decisions::open));
        l.add(new Shot("crisis-congress-1280", "crisis", ScreenId.CONGRESS, 1280, 720, 1.0, std, st, null));
        l.add(new Shot("crisis-congress-expert", "crisis", ScreenId.CONGRESS, 1600, 900, 1.0, std, Settings.Complexity.EXPERT, null));
        l.add(new Shot("crisis-oval-150", "crisis", ScreenId.OVAL, 1920, 1080, 1.5, std, st, null));
        l.add(new Shot("report-results", "report", ScreenId.RESULTS, 1600, 900, 1.0, std, st, null));
        l.add(new Shot("report-results-1280", "report", ScreenId.RESULTS, 1280, 720, 1.0, std, st, null));
        l.add(new Shot("report-timeline-expert", "report", ScreenId.TIMELINE, 1600, 900, 1.0, std, Settings.Complexity.EXPERT, null));
        l.add(new Shot("lost-election-night", "lost", ScreenId.RESULTS, 1600, 900, 1.0, std, st, Results::electionNight));
        l.add(new Shot("lost-results", "lost", ScreenId.RESULTS, 1600, 900, 1.0, std, st, null));
        for (ScreenId s : List.of(ScreenId.OVAL, ScreenId.CONGRESS, ScreenId.ECONOMY))
            l.add(new Shot("governing-" + s.name().toLowerCase(), "governing", s, 1600, 900, 1.0, std, st, null));
        l.add(new Shot("help", "crisis", ScreenId.OVERVIEW, 1600, 900, 1.0, std, st, Help::open));
        // The dialogs behind buttons, opened the way a player opens them.
        l.add(new Shot("crisis-concession", "crisis", ScreenId.CONGRESS, 1600, 900, 1.0, std, st, app -> press(app, "Work this bloc")));
        l.add(new Shot("week1-nominee", "week1", ScreenId.OVAL, 1600, 900, 1.0, std, st, app -> press(app, "Nominate")));
        l.add(new Shot("governing-push", "governing", ScreenId.CONGRESS, 1600, 900, 1.0, std, st, app -> press(app, "Push")));
        l.add(new Shot("crisis-rewind", "crisis", ScreenId.SAVES, 1600, 900, 1.0, std, st, app -> pressFirst(app, "Start of ")));
        l.add(new Shot("menu-load", null, null, 1600, 900, 1.0, std, st, SaveLoad::openFromMenu));
        l.add(new Shot("newgame-custom", null, null, 1600, 900, 1.0, std, st, app -> {
            app.showNewGame(); app.wizard().next(); app.wizard().next(); app.refresh(); press(app, "Create your own"); }));
        l.add(new Shot("menu-settings", null, null, 1600, 900, 1.0, std, st, SettingsScreen::openFromMenu));
        return l;
    }

    /** Presses the first enabled, visible button labelled {@code text}, as a player would; fails if there is none. */
    public static void press(App app, String text) {
        app.stage.setSize(Math.max(app.stage.getWidth(), 1600), Math.max(app.stage.getHeight(), 900));
        UiChecks.layout(app.stage);
        javax.swing.AbstractButton b = find(app.stage, text);
        if (b == null) throw new IllegalStateException("no enabled button labelled \"" + text + "\"");
        b.doClick(0);
    }

    /** Presses the first enabled button whose label starts with {@code prefix}. */
    public static void pressFirst(App app, String prefix) {
        UiChecks.layout(app.stage);
        javax.swing.AbstractButton b = findPrefix(app.stage, prefix);
        if (b == null) throw new IllegalStateException("no enabled button starting \"" + prefix + "\"");
        b.doClick(0);
    }

    static javax.swing.AbstractButton findPrefix(java.awt.Component c, String prefix) {
        if (c instanceof javax.swing.AbstractButton b && b.isVisible() && b.isEnabled() && b.getText() != null && b.getText().startsWith(prefix)) return b;
        if (c instanceof java.awt.Container ct) for (java.awt.Component k : ct.getComponents()) { var f = findPrefix(k, prefix); if (f != null) return f; }
        return null;
    }

    static javax.swing.AbstractButton find(java.awt.Component c, String text) {
        if (c instanceof javax.swing.AbstractButton b && b.isVisible() && b.isEnabled() && text.equals(b.getText())) return b;
        if (c instanceof java.awt.Container ct) for (java.awt.Component k : ct.getComponents()) { var f = find(k, text); if (f != null) return f; }
        return null;
    }

    /** Renders one shot; returns its problems (prefixed with the shot's name). Writes a PNG if {@code out} is not null. */
    public static List<String> shoot(Shot s, Map<String, String> moments, Path home, Path out) throws IOException {
        Session session = s.moment() == null ? new Session(home) : Fixtures.session(home, moments.get(s.moment()));
        session.settings.palette = s.palette();
        session.settings.scale = s.scale();
        session.settings.complexity = s.complexity();
        session.settings.autoOpenDecisions = false;
        App app = new App(session);
        app.stage.setSize(s.width(), s.height());
        if (s.moment() != null) { app.startedGame(); if (s.screen() != null) app.open(s.screen()); }
        else app.showMenu();
        if (s.extra() != null) s.extra().accept(app);
        app.stage.setSize(s.width(), s.height());
        java.awt.image.BufferedImage img = UiChecks.render(app.stage, s.width(), s.height());
        List<String> problems = new ArrayList<>();
        for (String p : UiChecks.check(app.stage)) problems.add(s.name() + ": " + p);
        if (out != null) {
            Files.createDirectories(out);
            ImageIO.write(img, "png", out.resolve(s.name() + ".png").toFile());
        }
        Theme.set(Theme.Palette.STANDARD, 1.0);
        return problems;
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", System.getProperty("java.awt.headless", "true"));
        Path out = Path.of(args.length > 0 ? args[0] : "target/screens");
        Path home = Files.createTempDirectory("mandate-screens");
        Map<String, String> moments = Fixtures.moments();
        List<String> all = new ArrayList<>();
        for (Shot s : shots()) all.addAll(shoot(s, moments, home.resolve(s.name()), out));
        Files.write(out.resolve("checks.txt"), all.isEmpty() ? List.of("All automated checks passed for " + shots().size() + " screens.") : all);
        System.out.println(shots().size() + " screens rendered to " + out.toAbsolutePath() + "; " + all.size() + " problems" + (all.isEmpty() ? "." : ":"));
        all.stream().limit(60).forEach(System.out::println);
    }
}
