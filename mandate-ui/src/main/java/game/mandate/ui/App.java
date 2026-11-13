package game.mandate.ui;

import game.mandate.core.engine.GameEngine;
import game.mandate.core.time.Phase;
import game.mandate.ui.kit.Stage;
import game.mandate.ui.kit.Theme;
import game.mandate.ui.kit.Ui;
import game.mandate.ui.screens.Decisions;
import game.mandate.ui.screens.Help;
import game.mandate.ui.screens.MainMenu;
import game.mandate.ui.screens.NewGame;
import game.mandate.ui.screens.Results;
import game.mandate.ui.screens.Shell;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The interface's controller: which view is showing (the main menu, the new-game flow, the game), which screen,
 * and the global shortcuts. It does not need a window, so tests and the screenshot tool can build and render it
 * headless; {@link MandateApp} puts it in a frame.
 */
public final class App {
    public enum View { MENU, NEW_GAME, GAME }

    /** The game's screens, in navigation order, with their shortcuts. */
    public enum ScreenId {
        OVERVIEW("Overview", "The nation at a glance", "ctrl 1"),
        CAMPAIGN("Campaign HQ", "Polls, money, message and the opponent", "ctrl 2"),
        OVAL("Oval Office", "Your desk: decisions, cabinet, agenda", "ctrl 3"),
        CONGRESS("Congress", "Your flagship bill and the votes", "ctrl 4"),
        ECONOMY("Economy", "Jobs, prices, the Fed and the budget", "ctrl 5"),
        SECURITY("Security", "Crises, deterrence and the world", "ctrl 6"),
        TIMELINE("Timeline", "Every decision and what it caused", "ctrl 7"),
        RESULTS("Results", "Election night and the 100-day report", "ctrl 8"),
        SAVES("Save and load", "Save, load and rewind", "ctrl S"),
        SETTINGS("Settings", "Display, accessibility and complexity", "ctrl COMMA");

        public final String label, purpose, keys;
        ScreenId(String label, String purpose, String keys) { this.label = label; this.purpose = purpose; this.keys = keys; }

        public String keyLabel() { return keys.replace("ctrl ", "Ctrl+").replace("COMMA", ","); }
    }

    public record Shortcut(String keys, String what, Runnable action) {}

    public final Session session;
    public final Stage stage = new Stage();
    private View view = View.MENU;
    private ScreenId screen = ScreenId.OVERVIEW;
    private NewGame wizard;
    private boolean advisorOpen = true;
    private final Set<String> decisionsSeen = new HashSet<>();
    private boolean reportShown;
    private final List<Shortcut> shortcuts = new ArrayList<>();
    /** Screen-local state that survives a rebuild (the selected state, a draft plan); cleared with each new game. */
    public final java.util.Map<String, Object> uiState = new java.util.HashMap<>();

    public App(Session session) {
        this.session = session;
        applyTheme();
        session.onChange(this::refresh);
        session.onNotice(n -> stage.toast(n.title(), n.body(), tone(n.tone())));
        bindShortcuts();
        refresh();
    }

    // ---- views and screens -------------------------------------------------------------------------------------

    public View view() { return view; }
    public ScreenId screen() { return screen; }
    public NewGame wizard() { return wizard; }

    public void showMenu() { view = View.MENU; stage.closeModal(); refresh(); }

    public void showNewGame() { wizard = new NewGame(this); view = View.NEW_GAME; refresh(); }

    public void startedGame() {
        decisionsSeen.clear();
        uiState.clear();
        reportShown = session.g() != null && session.g().dayHundredReport().isPresent();
        view = View.GAME;
        screen = defaultScreen();
        refresh();
    }

    public void open(ScreenId s) {
        if (view != View.GAME && s != ScreenId.SETTINGS && s != ScreenId.SAVES) return;
        if (view != View.GAME) { view = View.GAME; }
        screen = s;
        stage.closeModal();
        refresh();
    }

    /** The screen that fits the phase: the campaign's HQ, the president's desk. */
    public ScreenId defaultScreen() {
        GameEngine g = session.g();
        if (g == null) return ScreenId.OVERVIEW;
        return switch (g.phase()) {
            case GENERAL_CAMPAIGN_2028, REELECTION_CAMPAIGN_2032 -> ScreenId.CAMPAIGN;
            case TRANSITION, FIRST_100_DAYS, GOVERNING -> ScreenId.OVAL;
            default -> ScreenId.RESULTS;
        };
    }

    public boolean advisorOpen() { return advisorOpen && session.settings.advisor; }
    public void toggleAdvisor() { advisorOpen = !advisorOpen; refresh(); }

    /** Rebuilds whatever is showing from the current state of the game. */
    public void refresh() {
        String focused = focusKey();
        stage.reduceMotion(session.settings.reduceMotion);
        GameEngine g = session.g();
        if (view == View.GAME && g == null) view = View.MENU;
        JComponent base = switch (view) {
            case MENU -> MainMenu.build(this);
            case NEW_GAME -> wizard.build();
            case GAME -> Shell.build(this);
        };
        stage.setBase(base);
        stage.doLayout();
        base.revalidate();
        if (focused != null) javax.swing.SwingUtilities.invokeLater(() -> restoreFocus(base, focused));
        if (view == View.GAME && g != null && !stage.modalOpen()) announce(g);
    }

    /** The focused control's identity (its class and accessible name), so a rebuilt screen can give it focus back. */
    String focusKey() {
        java.awt.Component f = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (!(f instanceof JComponent j) || !javax.swing.SwingUtilities.isDescendingFrom(f, stage) || stage.modalOpen()) return null;
        String name = j.getAccessibleContext().getAccessibleName();
        return name == null ? null : j.getClass().getName() + "|" + name;
    }

    static boolean restoreFocus(java.awt.Component c, String key) {
        if (c instanceof JComponent j && c.isShowing() && c.isFocusable()
                && key.equals(j.getClass().getName() + "|" + j.getAccessibleContext().getAccessibleName())) {
            c.requestFocusInWindow();
            return true;
        }
        if (c instanceof java.awt.Container ct) for (java.awt.Component k : ct.getComponents()) if (restoreFocus(k, key)) return true;
        return false;
    }

    /** Election night, day 100 and new decisions come forward on their own. */
    void announce(GameEngine g) {
        // election night comes forward until it has been watched: the engine keeps that record in the save, so a reload
        // on the night shows it again (it used to be skipped whenever a loaded game already had a result)
        if (g.electionNightPending() > 0) {
            Results.electionNight(this);
            return;
        }
        if (!reportShown && g.dayHundredReport().isPresent()) {
            reportShown = true;
            screen = ScreenId.RESULTS;
            refresh();
            return;
        }
        if (session.settings.autoOpenDecisions) {
            String next = Decisions.firstUnseen(this, decisionsSeen);
            if (next != null) { decisionsSeen.add(next); Decisions.open(this); }
        }
    }

    public void markDecisionSeen(String key) { decisionsSeen.add(key); }

    public void applyTheme() {
        Theme.set(session.settings.palette, session.settings.scale);
        Ui.installDefaults();
    }

    /** Rebuilds an open modal after a change of settings (the settings window over the main menu). */
    public Runnable modalRebuilder;

    public void settingsChanged() {
        session.saveSettings();
        applyTheme();
        Runnable again = stage.modalOpen() ? modalRebuilder : null;
        refresh();
        if (again != null) again.run();
    }

    // ---- actions shared by screens -----------------------------------------------------------------------------

    public void endTurn() { Shell.endTurn(this); }

    public java.awt.Color tone(Session.Tone t) {
        Theme th = Theme.get();
        return switch (t) { case GOOD -> th.good; case BAD -> th.bad; case WARN -> th.warn; case INFO -> th.info; };
    }

    // ---- shortcuts ---------------------------------------------------------------------------------------------

    public List<Shortcut> shortcuts() { return List.copyOf(shortcuts); }

    void bindShortcuts() {
        for (ScreenId s : ScreenId.values()) bind(s.keys, s.keyLabel(), "Open " + s.label, () -> open(s));
        bind("ctrl ENTER", "Ctrl+Enter", "End the turn (run the week, end the week or month)", this::endTurn);
        bind("ctrl D", "Ctrl+D", "Open the decisions waiting for you", () -> { if (view == View.GAME) Decisions.open(this); });
        bind("ctrl Z", "Ctrl+Z", "Undo the last action", () -> { if (view == View.GAME && session.canUndo()) session.undo(); });
        bind("F1", "F1", "Help: shortcuts and what each mechanic means", () -> Help.open(this));
        bind("F2", "F2", "Show or hide the advisor", this::toggleAdvisor);
        bind("ESCAPE", "Esc", "Close a window, or return to the main menu from the setup", () -> {
            if (stage.modalOpen()) stage.closeModal(); else if (view == View.NEW_GAME) showMenu();
        });
    }

    void bind(String stroke, String keys, String what, Runnable action) {
        shortcuts.add(new Shortcut(keys, what, action));
        String name = "mandate." + stroke;
        stage.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(stroke), name);
        stage.getActionMap().put(name, new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { action.run(); } });
    }

    public boolean governing() {
        GameEngine g = session.g();
        return g != null && (g.phase() == Phase.FIRST_100_DAYS || g.phase() == Phase.GOVERNING);
    }
}
