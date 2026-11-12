package game.mandate.ui;

import game.mandate.ui.screens.MainMenu;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;

/** The desktop window: the interface in a resizable frame, 1280x720 at the smallest. */
public final class MandateApp {
    private MandateApp() {}

    public static final Dimension MINIMUM = new Dimension(1280, 720);

    public static void launch(Path home) { SwingUtilities.invokeLater(() -> open(new App(new Session(home))).setVisible(true)); }

    /** Builds the window around an interface (on the event thread); the caller shows it. */
    public static JFrame open(App app) {
        {
            JFrame frame = new JFrame("Mandate");
            frame.setContentPane(app.stage);
            frame.setMinimumSize(MINIMUM);
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            frame.setSize(Math.max(MINIMUM.width, Math.min(1600, screen.width - 80)), Math.max(MINIMUM.height, Math.min(900, screen.height - 80)));
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            Runnable quit = () -> { frame.dispose(); System.exit(0); };
            MainMenu.quit = quit;
            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) { quit.run(); }   // every turn is already saved
            });
            Timer settle = new Timer(160, e -> app.refresh());                    // re-flow once a resize settles
            settle.setRepeats(false);
            frame.addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) { settle.restart(); }
            });
            return frame;
        }
    }

    public static boolean canShowWindow() { return !GraphicsEnvironment.isHeadless(); }
}
