package game.mandate.web.screens;

import game.mandate.web.kit.El;

import game.mandate.web.App;
import game.mandate.web.Glossary;
import game.mandate.web.Notable;
import game.mandate.web.Session;
import game.mandate.web.kit.Btn;
import game.mandate.web.kit.Card;
import game.mandate.web.kit.Layouts;
import game.mandate.web.kit.Table;
import game.mandate.web.kit.Text;
import game.mandate.web.kit.Theme;

import java.time.LocalDate;
import java.util.List;

/**
 * Save, load and recover. Three slots and the autosave (written every turn); undo for the last action; rewind to the
 * start of any earlier turn. Destructive choices (overwriting, deleting, rewinding) ask first.
 */
public final class SaveLoad {
    private SaveLoad() {}

    public static El build(App app, boolean inGame) {
        return Parts.page("Save and load", "Your game is saved in this browser automatically at the end of every turn.", Parts.row(Parts.w(slots(app, inGame), 1.5), Parts.w(recover(app), 1)));
    }

    static Card slots(App app, boolean inGame) {
        Theme t = Theme.get();
        Card c = new Card("Saved games");
        Table table = new Table(List.of(Table.Col.left("Slot", 0.9), Table.Col.left("Game", 3), Table.Col.right("", 3)));
        for (Session.Slot s : app.session.slots()) {
            String label = Session.label(s.id());
            Btn load = Btn.secondary("Load", e -> Confirm.ask(app, "Load " + label + "?", inGame ? "Your current game is in the autosave, so you can come back to it." : s.description(),
                    "Load", "Cancel", () -> { app.session.load(s); app.startedGame(); })).disabledBecause(s.exists() ? null : "This slot is empty");
            load.label("Load " + label);
            if (load.isEnabled()) load.tip("Load " + s.description());
            El actions;
            if (s.id().equals("autosave")) actions = Layouts.wrap(t.u(1), t.u(1), load);
            else {
                Btn save = Btn.secondary("Save here", e -> {
                    if (s.exists()) Confirm.ask(app, "Overwrite " + label + "?", "It holds " + s.description() + ".", "Overwrite", "Cancel", () -> app.session.saveTo(s.id()));
                    else app.session.saveTo(s.id());
                }).disabledBecause(inGame && app.session.g() != null ? null : "Start or load a game first");
                save.label("Save to " + label);
                if (save.isEnabled()) save.tip("Save the current game to " + label);
                Btn del = Btn.quiet("Delete", e -> Confirm.ask(app, "Delete " + label + "?", "This cannot be undone.", "Delete", "Keep it", () -> app.session.delete(s)))
                        .disabledBecause(s.exists() ? null : "This slot is empty");
                del.label("Delete " + label);
                if (del.isEnabled()) del.tip("Delete " + label);
                actions = Layouts.wrap(t.u(1), t.u(1), save, load, del);
            }
            El game = s.exists() && s.savedAt() != null && !s.savedAt().isBlank()
                    ? Layouts.stack(t.px(2), Text.body(s.description()), Text.faint("Saved " + s.savedAt())) : Text.muted(s.description());
            table.row(label, game, actions);
        }
        c.with(table, Text.faint("Saves are kept in this browser, on this device. To keep a game safe, or to move it to another browser or to the desktop game, export it to a file."));
        c.with(fileButtons(app, inGame));
        return c;
    }

    /** Export the current game to a file, or import one: a save is the same file on the desktop and in the browser. */
    static El fileButtons(App app, boolean inGame) {
        Theme t = Theme.get();
        Btn export = Btn.secondary("Export this game to a file", e -> {
            String text = app.session.exportText();
            if (text == null) return;
            var g = app.session.g();
            String name = "mandate-" + g.player().candidate().name().toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-" + g.date() + ".json";
            game.mandate.web.kit.Dom.download(name, text);
        }).disabledBecause(inGame && app.session.g() != null ? null : "Start or load a game first");
        if (export.isEnabled()) export.tip("Downloads a save file (the same format as the desktop game's)");
        Btn imp = Btn.secondary("Import a save file", e -> game.mandate.web.kit.Dom.pickFile(text -> {
            if (app.session.loadText(text, "the file")) { app.stage.closeModal(); app.startedGame(); }
        })).tip("Opens a save exported from this game, in a browser or on the desktop");
        return Layouts.wrap(t.u(2), t.u(2), export, imp);
    }

    static Card recover(App app) {
        Theme t = Theme.get();
        Card c = new Card("Recover from a mistake");
        c.setToolTipText(Glossary.of("Undo and rewind"));
        if (app.session.g() == null) return c.with(Text.muted("Load a game to undo or rewind."));
        c.with(Text.muted(Glossary.of("Undo and rewind")));
        c.with(Btn.secondary(app.session.undoLabel(), e -> app.session.undo()).hint("Ctrl+Z").disabledBecause(app.session.canUndo() ? null : "Nothing to undo yet"));
        c.with(Text.eyebrow("Rewind to the start of a turn"));
        List<Integer> points = app.session.rewindPoints();
        int shown = 0;
        for (int idx : points) {
            if (shown >= 10) break;
            if (idx >= app.session.g().history().size()) continue;
            String when = app.session.turnDate(idx).map(d -> "Start of the turn of " + Notable.date(LocalDate.parse(d))).orElse("Start of turn " + app.session.turnNumber(idx));
            int k = idx;
            Btn b = Btn.secondary(when, e -> Confirm.ask(app, "Rewind?", when + ". Everything after it is undone; the autosave keeps only the rewound game.", "Rewind", "Cancel",
                    () -> app.session.rewindTo(k)));
            b.tip("Return to " + when.toLowerCase());
            c.with(b);
            shown++;
        }
        if (shown == 0) c.with(Text.muted("No earlier turns yet."));
        return c;
    }

    /** From the main menu: load only. */
    public static void openFromMenu(App app) {
        Theme t = Theme.get();
        Card c = new Card("Load a game", Btn.quiet("Close", e -> app.stage.closeModal()).hint("Esc").tip("Close (Esc)")).with(slots(app, false), Btn.secondary("Close", e -> app.stage.closeModal()));
        app.stage.showModal(c, t.u(200), true, null);
    }
}
