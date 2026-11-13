package game.mandate.web;

import game.mandate.core.engine.GameEngine;
import game.mandate.core.time.Phase;
import game.mandate.web.kit.Color;
import game.mandate.web.kit.Dom;
import game.mandate.web.kit.El;
import game.mandate.web.kit.Stage;
import game.mandate.web.kit.Theme;
import game.mandate.web.screens.Decisions;
import game.mandate.web.screens.Help;
import game.mandate.web.screens.MainMenu;
import game.mandate.web.screens.NewGame;
import game.mandate.web.screens.Results;
import game.mandate.web.screens.Shell;
import org.teavm.jso.JSObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The browser interface's controller, the counterpart of the desktop's {@code App}: which view is showing (the main
 * menu, the new-game flow, the game), which screen, and the shortcuts. Screens are rebuilt from the game after every
 * change, and keyboard focus goes back to the same control.
 *
 * <p>Browsers keep Ctrl+1..8 (tabs), Ctrl+D (bookmark) and F1 (help) for themselves, so the web build uses single
 * keys for those, as browser games do; Ctrl+Enter and Ctrl+Z are the same as on the desktop.
 */
public final class App {
    public enum View { MENU, NEW_GAME, GAME }

    public enum ScreenId {
        OVERVIEW("Overview", "The nation at a glance", "1"),
        CAMPAIGN("Campaign HQ", "Polls, money, message and the opponent", "2"),
        OVAL("Oval Office", "Your desk: decisions, cabinet, agenda", "3"),
        CONGRESS("Congress", "Your flagship bill and the votes", "4"),
        ECONOMY("Economy", "Jobs, prices, the Fed and the budget", "5"),
        SECURITY("Security", "Crises, deterrence and the world", "6"),
        TIMELINE("Timeline", "Every decision and what it caused", "7"),
        RESULTS("Results", "Election night and the 100-day report", "8"),
        SAVES("Save and load", "Save, load, export and rewind", "S"),
        SETTINGS("Settings", "Display, accessibility and complexity", ",");

        public final String label, purpose, keys;
        ScreenId(String label, String purpose, String keys) { this.label = label; this.purpose = purpose; this.keys = keys; }

        public String keyLabel() { return keys; }
    }

    public record Shortcut(String keys, String what, Runnable action) {}

    public final Session session;
    public final Stage stage;
    private View view = View.MENU;
    private ScreenId screen = ScreenId.OVERVIEW;
    private NewGame wizard;
    private boolean advisorOpen = true;
    private final Set<String> decisionsSeen = new HashSet<>();
    private boolean reportShown;
    private final List<Shortcut> shortcuts = new ArrayList<>();
    /** Screen-local state that survives a rebuild (the selected state, a draft plan); cleared with each new game. */
    public final java.util.Map<String, Object> uiState = new java.util.HashMap<>();
    public Runnable modalRebuilder;

    public App(Session session, JSObject host) {
        this.session = session;
        this.stage = new Stage(host);
        applyTheme();
        session.onChange(this::refresh);
        session.onNotice(n -> stage.toast(n.title(), n.body(), tone(n.tone())));
        shortcuts();
        Dom.onKeyDown(this::key);
        Dom.onResize(this::refresh);
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
        if (view != View.GAME) { if (session.g() == null) return; view = View.GAME; }
        boolean changedScreen = s != screen;
        screen = s;
        stage.closeModal();
        refresh();
        if (changedScreen) { JSObject main = Dom.byId("main"); if (!Dom.isNull(main)) Dom.scrollTop(main); }
    }

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

    /** Rebuilds whatever is showing from the current state of the game, keeping focus and the scroll position. */
    public void refresh() {
        String focused = Dom.focusKey();
        JSObject oldMain = Dom.byId("main");
        int scroll = Dom.isNull(oldMain) ? 0 : Dom.scrollTopOf(oldMain);
        stage.reduceMotion(session.settings.reduceMotion);
        GameEngine g = session.g();
        if (view == View.GAME && g == null) view = View.MENU;
        El base = switch (view) {
            case MENU -> MainMenu.build(this);
            case NEW_GAME -> wizard.build();
            case GAME -> Shell.build(this);
        };
        stage.setBase(base);
        JSObject main = Dom.byId("main");
        if (!Dom.isNull(main)) Dom.setScrollTop(main, scroll);
        if (focused != null && !stage.modalOpen()) Dom.restoreFocus(stage.base().node, focused);
        Dom.title(view == View.GAME && g != null ? screen.label + " · Mandate" : "Mandate");
        if (view == View.GAME && g != null && !stage.modalOpen()) announce(g);
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
        Dom.themeCss(Theme.get().css());
        Dom.rootAttr("data-palette", session.settings.palette.name().toLowerCase());
    }

    public void settingsChanged() {
        session.saveSettings();
        applyTheme();
        refresh();
        if (modalRebuilder != null) modalRebuilder.run();
    }

    public void endTurn() { Shell.endTurn(this); }

    public Color tone(Session.Tone t) {
        Theme th = Theme.get();
        return switch (t) { case GOOD -> th.good; case BAD -> th.bad; case WARN -> th.warn; case INFO -> th.info; };
    }

    public List<Shortcut> shortcuts() {
        if (!shortcuts.isEmpty()) return List.copyOf(shortcuts);
        for (ScreenId s : ScreenId.values()) shortcuts.add(new Shortcut(s.keyLabel(), "Open " + s.label, () -> open(s)));
        shortcuts.add(new Shortcut("Ctrl+Enter", "End the turn (run the week, end the week or month)", this::endTurn));
        shortcuts.add(new Shortcut("D", "Open the decisions waiting for you", () -> { if (view == View.GAME) Decisions.open(this); }));
        shortcuts.add(new Shortcut("Ctrl+Z", "Undo the last action", () -> { if (view == View.GAME && session.canUndo()) session.undo(); }));
        shortcuts.add(new Shortcut("?", "Help: shortcuts and what each mechanic means", () -> Help.open(this)));
        shortcuts.add(new Shortcut("A", "Show or hide the advisor", this::toggleAdvisor));
        shortcuts.add(new Shortcut("Esc", "Close a window, or return to the main menu from the setup", () -> {
            if (stage.modalOpen()) { if (stage.modalClosable()) stage.closeModal(); } else if (view == View.NEW_GAME) showMenu();
        }));
        return List.copyOf(shortcuts);
    }

    Runnable action(String keys) {
        for (Shortcut s : shortcuts) if (s.keys().equals(keys)) return s.action();
        return null;
    }

    /** One key handler for the page. Letters and digits never fire while typing in a field or with a card open. */
    void key(JSObject ev) {
        String k = Dom.key(ev);
        boolean ctrl = Dom.ctrl(ev), field = Dom.inField(ev);
        Runnable a = null;
        if (k.equals("Escape")) a = action("Esc");
        else if (ctrl && k.equals("Enter")) a = stage.modalOpen() ? null : action("Ctrl+Enter");
        else if (ctrl && (k.equals("z") || k.equals("Z")) && !field) a = stage.modalOpen() ? null : action("Ctrl+Z");
        else if (!ctrl && !Dom.alt(ev) && !field && !stage.modalOpen() && k.length() == 1) {
            String key = k.toUpperCase();
            if (key.equals("/") && Dom.shift(ev)) key = "?";
            if (key.equals("H")) key = "?";
            if (view == View.GAME || key.equals("?") || key.equals(",") || key.equals("S")) a = action(key);
        }
        if (a != null) { Dom.prevent(ev); a.run(); }
    }

    public boolean governing() {
        GameEngine g = session.g();
        return g != null && (g.phase() == Phase.FIRST_100_DAYS || g.phase() == Phase.GOVERNING);
    }
}
