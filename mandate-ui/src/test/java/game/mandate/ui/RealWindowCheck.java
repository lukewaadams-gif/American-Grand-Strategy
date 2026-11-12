package game.mandate.ui;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The real window, under a real display (run it with {@code xvfb-run}): real key presses through {@link Robot},
 * real resizing, real screen captures. It checks what the headless renders cannot: that the frame opens, that the
 * key bindings fire from the window, that Tab moves focus through visible controls, and that a resize re-flows.
 * Not a unit test, since it needs a display; see UI_DESIGN.md for how to run it.
 */
public final class RealWindowCheck {
    static Robot robot;
    static App app;
    static JFrame frame;
    static final List<String> report = new ArrayList<>();
    static int failures;

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "target/real-window");
        Files.createDirectories(out);
        Path home = Files.createTempDirectory("mandate-real");
        robot = new Robot();
        robot.setAutoDelay(40);
        Map<String, String> moments = Fixtures.moments();

        SwingUtilities.invokeAndWait(() -> {
            Session s = new Session(home);
            s.settings.confirmUnspent = false;
            app = new App(s);
            frame = MandateApp.open(app);
            frame.setVisible(true);
        });
        idle(1200);
        shot(out, "real-menu");
        check("the window opened at its default size", frame.isShowing() && frame.getWidth() >= 1280 && frame.getHeight() >= 720);
        check("the menu has keyboard focus somewhere", focusName() != null);

        // Keyboard only: Tab to "New game", Enter, then Esc back.
        tabTo("New game", 12);
        key(KeyEvent.VK_ENTER);
        idle(400);
        check("Enter on New game opens the wizard", app.view() == App.View.NEW_GAME);
        shot(out, "real-newgame");
        key(KeyEvent.VK_ESCAPE);
        idle(300);
        check("Esc leaves the wizard", app.view() == App.View.MENU);

        // A game in progress: the crisis week.
        Path moment = home.resolve("crisis.json");
        Files.writeString(moment, moments.get("crisis"));
        SwingUtilities.invokeAndWait(() -> {
            app.session.settings.autoOpenDecisions = false;
            app.session.load(new Session.Slot("moment", moment, true, "", ""));
            app.startedGame();
        });
        idle(600);
        shot(out, "real-oval");
        ctrl(KeyEvent.VK_6);
        check("Ctrl+6 opens Security", app.screen() == App.ScreenId.SECURITY);
        ctrl(KeyEvent.VK_4);
        check("Ctrl+4 opens Congress", app.screen() == App.ScreenId.CONGRESS);
        key(KeyEvent.VK_F1);
        idle(300);
        check("F1 opens help", app.stage.modalOpen());
        shot(out, "real-help");
        key(KeyEvent.VK_ESCAPE);
        idle(300);
        check("Esc closes help", !app.stage.modalOpen());
        ctrl(KeyEvent.VK_D);
        idle(300);
        check("Ctrl+D opens the waiting decision", app.stage.modalOpen());
        String before = focusName();
        report.add("focus in the decision card: " + before);
        key(KeyEvent.VK_1);
        idle(500);
        check("1 chooses the first option and shows what happened", app.stage.modalOpen() && app.session.g().hundredDays().get().pending().isEmpty());
        shot(out, "real-what-happened");
        key(KeyEvent.VK_ENTER);
        idle(300);
        check("Enter on Continue closes the card", !app.stage.modalOpen());

        // Tab moves through visible controls; record the order.
        List<String> order = new ArrayList<>();
        for (int i = 0; i < 14; i++) { key(KeyEvent.VK_TAB); Component f = focus(); order.add(f == null ? "(none)" : f.isShowing() ? name(f) : "(hidden) " + name(f)); }
        report.add("Tab order: " + String.join(" > ", order));
        check("Tab never lands on a hidden control", order.stream().noneMatch(s -> s.startsWith("(hidden)") || s.equals("(none)")));
        shot(out, "real-focus");

        // Resizing re-flows the screen, and the checks still pass at each size.
        for (int[] size : new int[][] {{1280, 720}, {1920, 1080}, {1440, 900}}) {
            SwingUtilities.invokeAndWait(() -> frame.setSize(size[0], size[1]));
            idle(900);
            AtomicReference<List<String>> problems = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> problems.set(UiChecks.check(app.stage)));
            check("checks pass after resizing to " + size[0] + "x" + size[1] + (problems.get().isEmpty() ? "" : ": " + problems.get()), problems.get().isEmpty());
            shot(out, "real-" + size[0] + "x" + size[1]);
        }
        SwingUtilities.invokeAndWait(() -> frame.setSize(1000, 600));
        idle(600);
        check("the window will not shrink below 1280x720", frame.getWidth() >= 1280 && frame.getHeight() >= 720);

        Files.write(out.resolve("real-window.txt"), report);
        report.forEach(System.out::println);
        System.out.println(failures == 0 ? "Real-window check passed." : failures + " real-window checks failed.");
        System.exit(failures == 0 ? 0 : 1);
    }

    static void check(String what, boolean ok) {
        report.add((ok ? "PASS  " : "FAIL  ") + what);
        if (!ok) failures++;
    }

    static void idle(int ms) throws Exception { robot.waitForIdle(); Thread.sleep(ms); robot.waitForIdle(); }

    static void key(int code) throws Exception { robot.keyPress(code); robot.keyRelease(code); idle(120); }

    static void ctrl(int code) throws Exception {
        robot.keyPress(KeyEvent.VK_CONTROL); robot.keyPress(code); robot.keyRelease(code); robot.keyRelease(KeyEvent.VK_CONTROL);
        idle(400);
    }

    static void tabTo(String accessibleName, int max) throws Exception {
        for (int i = 0; i < max && !accessibleName.equals(focusName()); i++) key(KeyEvent.VK_TAB);
        check("Tab reaches \"" + accessibleName + "\"", accessibleName.equals(focusName()));
    }

    static Component focus() { return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner(); }

    static String focusName() { Component f = focus(); return f == null ? null : name(f); }

    static String name(Component c) {
        return c instanceof JComponent j && j.getAccessibleContext().getAccessibleName() != null ? j.getAccessibleContext().getAccessibleName() : c.getClass().getSimpleName();
    }

    static void shot(Path out, String name) throws Exception {
        idle(200);
        Rectangle r = frame.getBounds();
        ImageIO.write(robot.createScreenCapture(r), "png", out.resolve(name + ".png").toFile());
    }
}
