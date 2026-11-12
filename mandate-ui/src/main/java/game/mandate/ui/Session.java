package game.mandate.ui;

import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.data.Json;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.engine.SaveManager;
import game.mandate.core.game.GameSetup;
import game.mandate.core.opening.OpeningAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * The front end's controller: it owns the game and the player's preferences, applies every action as an engine
 * command, autosaves after every turn, and lets the player recover from a mistake without restarting: undo the last
 * action (an exact replay of the history without it: the dice come out the same, so undo cannot re-roll an outcome),
 * or rewind to the start of any earlier turn.
 */
public final class Session {
    public enum Tone { GOOD, BAD, WARN, INFO }

    /** Something the player should notice: a vote, a confirmation, a crisis, an error. */
    public record Notice(String title, String body, Tone tone) {}

    /** One save slot as the load screen lists it. */
    public record Slot(String id, Path file, boolean exists, String description, String savedAt) {}

    public static final List<String> SLOTS = List.of("autosave", "slot1", "slot2", "slot3");

    public final Path home;
    public final Settings settings;
    private GameEngine g;
    private final Set<String> seenLessons = new TreeSet<>();
    /** The game date at each turn start, for labels ("Start of the week of Feb 5, 2029"). */
    private final Map<Integer, String> turnDates = new java.util.TreeMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private Consumer<Notice> noticeSink = n -> { };
    private int logSeen;

    public Session(Path home) {
        this.home = home;
        this.settings = Settings.load(home.resolve("settings.json"));
    }

    public static Path defaultHome() {
        String env = System.getenv("MANDATE_HOME");
        return env != null && !env.isBlank() ? Path.of(env) : Path.of(System.getProperty("user.home"), ".mandate");
    }

    public Optional<GameEngine> game() { return Optional.ofNullable(g); }
    public GameEngine g() { return g; }
    public Set<String> seenLessons() { return seenLessons; }

    public void onChange(Runnable r) { listeners.add(r); }
    public void onNotice(Consumer<Notice> c) { noticeSink = c; }
    void changed() { for (Runnable r : List.copyOf(listeners)) r.run(); }
    void notice(Notice n) { noticeSink.accept(n); }

    public void saveSettings() { settings.save(home.resolve("settings.json")); }

    // ---- starting and loading -------------------------------------------------------------------------------

    public void newGame(GameSetup setup) {
        g = new GameEngine(setup);
        seenLessons.clear();
        turnDates.clear();
        turnDates.put(0, g.date().toString());
        logSeen = g.log().size();
        autosave();
        changed();
    }

    public void load(Slot slot) {
        try {
            String text = Files.readString(slot.file(), StandardCharsets.UTF_8);
            g = SaveManager.load(text);
            Map<String, Object> ui = SaveManager.ui(text);
            seenLessons.clear();
            if (ui.get("seen") != null) for (Object o : Json.array(ui.get("seen"))) seenLessons.add((String) o);
            turnDates.clear();
            if (ui.get("turnDates") != null) Json.object(ui.get("turnDates")).forEach((k, v) -> turnDates.put(Integer.parseInt(k), (String) v));
            logSeen = g.log().size();
            changed();
        } catch (IOException | RuntimeException e) {
            notice(new Notice("Could not load " + slot.id(), e.getMessage(), Tone.BAD));
        }
    }

    public void close() { g = null; changed(); }

    // ---- acting ---------------------------------------------------------------------------------------------

    /** Applies a command. A refusal is reported as a notice and changes nothing. Returns whether it applied. */
    public boolean apply(Command c) {
        if (g == null) return false;
        boolean endsTurn = endsTurn(c);
        try {
            g.apply(c);
        } catch (IllegalCommandException | IllegalStateException | IllegalArgumentException e) {
            notice(new Notice("Not possible", e.getMessage(), Tone.WARN));
            return false;
        }
        if (endsTurn) turnDates.put(g.history().size(), g.date().toString());
        reportNewLog();
        if (endsTurn) autosave();
        changed();
        return true;
    }

    public boolean act(OpeningAction a) { return apply(new Command.Act(a)); }

    void reportNewLog() {
        List<String> log = g.log();
        for (int i = logSeen; i < log.size(); i++) Notable.classify(log.get(i)).ifPresent(this::notice);
        logSeen = log.size();
    }

    // ---- recovering from mistakes ------------------------------------------------------------------------------

    public boolean canUndo() { return g != null && !g.history().isEmpty(); }

    public String undoLabel() {
        if (!canUndo()) return "Nothing to undo";
        return "Undo: " + Notable.describe(g.history().get(g.history().size() - 1));
    }

    /** Replays the game without its last command: exactly the state before it. */
    public void undo() {
        if (!canUndo()) return;
        List<Command> h = g.history();
        GameEngine before = GameEngine.replay(g.setup(), h.subList(0, h.size() - 1));
        g = before;
        forgetDatesAfter(g.history().size());
        logSeen = g.log().size();
        notice(new Notice("Undone", "The game is back to the moment before that action.", Tone.INFO));
        autosave();
        changed();
    }

    static boolean endsTurn(Command c) { return c instanceof Command.EndTurn || (c instanceof Command.Act a && a.action() instanceof OpeningAction.EndWeek); }

    /**
     * Turn starts the player can rewind to, most recent first (indexes into the command history). They are read from
     * the history itself, so a save made without the interface's own records still offers every turn.
     */
    public List<Integer> rewindPoints() {
        List<Integer> l = new ArrayList<>();
        if (g == null) return l;
        l.add(0);
        List<Command> h = g.history();
        for (int i = 0; i < h.size(); i++) if (endsTurn(h.get(i))) l.add(i + 1);
        java.util.Collections.reverse(l);
        return l;
    }

    /** Dates recorded for turns that no longer exist (after an undo or a rewind) are dropped. */
    void forgetDatesAfter(int commandIndex) { turnDates.keySet().removeIf(k -> k > commandIndex); }

    /** The turn number (from 1) that starts at a rewind point. */
    public int turnNumber(int commandIndex) {
        int n = 1;
        List<Command> h = g.history();
        for (int i = 0; i < Math.min(commandIndex, h.size()); i++) if (endsTurn(h.get(i))) n++;
        return n;
    }

    /** The date a turn started on, if known. */
    public Optional<String> turnDate(int commandIndex) { return Optional.ofNullable(turnDates.get(commandIndex)); }

    public void rewindTo(int commandIndex) {
        if (g == null || commandIndex < 0 || commandIndex > g.history().size()) return;
        g = GameEngine.replay(g.setup(), g.history().subList(0, commandIndex));
        forgetDatesAfter(commandIndex);
        logSeen = g.log().size();
        notice(new Notice("Rewound", "Back to " + g.date() + ". The same choices will play out the same way; different ones will not.", Tone.INFO));
        autosave();
        changed();
    }

    public void restartSameSeed() { if (g != null) { GameSetup s = g.setup(); newGame(s); } }

    public void replayHundredDays() {
        if (g == null) return;
        g.replayFromInauguration().ifPresent(r -> {
            g = r;
            forgetDatesAfter(g.history().size());
            logSeen = g.log().size();
            notice(new Notice("Back to Inauguration Day", "The same election, Congress and mandate. Govern differently.", Tone.INFO));
            autosave();
            changed();
        });
    }

    // ---- saving -----------------------------------------------------------------------------------------------

    public Path slotFile(String id) { return id.equals("autosave") ? home.resolve("save.json") : home.resolve("saves").resolve(id + ".json"); }

    public List<Slot> slots() {
        List<Slot> out = new ArrayList<>();
        for (String id : SLOTS) {
            Path f = slotFile(id);
            if (!Files.exists(f)) { out.add(new Slot(id, f, false, "Empty", "")); continue; }
            String desc;
            try {
                GameEngine s = SaveManager.load(Files.readString(f, StandardCharsets.UTF_8));
                desc = s.player().candidate().name() + " (" + s.player().party().code() + "), " + Notable.phase(s) + ", " + Notable.date(s.date());
            } catch (IOException | RuntimeException e) {
                desc = "Unreadable save";
            }
            String at = "";
            try { at = Files.getLastModifiedTime(f).toInstant().atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, HH:mm")); }
            catch (IOException ignored) { }
            out.add(new Slot(id, f, true, desc, at));
        }
        return out;
    }

    public void saveTo(String id) {
        if (g == null) return;
        SaveManager.writeAtomically(slotFile(id), SaveManager.save(g, ui()));
        if (!id.equals("autosave")) notice(new Notice("Saved", "Saved to " + label(id) + ".", Tone.GOOD));
        changed();
    }

    public void delete(Slot s) {
        try { Files.deleteIfExists(s.file()); } catch (IOException ignored) { }
        notice(new Notice("Deleted", label(s.id()) + " is empty.", Tone.INFO));
        changed();
    }

    public static String label(String id) { return id.equals("autosave") ? "Autosave" : "Slot " + id.substring(4); }

    void autosave() { if (g != null) SaveManager.writeAtomically(slotFile("autosave"), SaveManager.save(g, ui())); }

    Map<String, Object> ui() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seen", new ArrayList<>(seenLessons));
        Map<String, Object> dates = new LinkedHashMap<>();
        turnDates.forEach((k, v) -> dates.put(String.valueOf(k), v));
        m.put("turnDates", dates);
        m.put("savedBy", "desktop " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        return m;
    }

    public void lessonSeen(String id) { seenLessons.add(id); }
}
