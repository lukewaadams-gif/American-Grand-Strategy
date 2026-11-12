package game.mandate.ui.screens;

import game.mandate.ui.App;
import game.mandate.ui.Glossary;
import game.mandate.ui.Notable;
import game.mandate.ui.Session;
import game.mandate.ui.kit.Btn;
import game.mandate.ui.kit.Card;
import game.mandate.ui.kit.Layouts;
import game.mandate.ui.kit.Table;
import game.mandate.ui.kit.Text;
import game.mandate.ui.kit.Theme;

import javax.swing.JComponent;
import java.time.LocalDate;
import java.util.List;

/**
 * Save, load and recover. Three slots and the autosave (written every turn); undo for the last action; rewind to the
 * start of any earlier turn. Destructive choices (overwriting, deleting, rewinding) ask first.
 */
public final class SaveLoad {
    private SaveLoad() {}

    public static JComponent build(App app, boolean inGame) {
        return Parts.page("Save and load", "Your game is saved automatically at the end of every turn.", Parts.row(Parts.w(slots(app, inGame), 1.5), Parts.w(recover(app), 1)));
    }

    static Card slots(App app, boolean inGame) {
        Theme t = Theme.get();
        Card c = new Card("Saved games");
        Table table = new Table(List.of(Table.Col.left("Slot", 0.9), Table.Col.left("Game", 3), Table.Col.right("", 3)));
        for (Session.Slot s : app.session.slots()) {
            String label = Session.label(s.id());
            Btn load = Btn.secondary("Load", e -> Confirm.ask(app, "Load " + label + "?", inGame ? "Your current game is in the autosave, so you can come back to it." : s.description(),
                    "Load", "Cancel", () -> { app.session.load(s); app.startedGame(); })).disabledBecause(s.exists() ? null : "This slot is empty");
            load.getAccessibleContext().setAccessibleName("Load " + label);
            if (load.isEnabled()) load.tip("Load " + s.description());
            JComponent actions;
            if (s.id().equals("autosave")) actions = Layouts.wrap(t.u(1), t.u(1), load);
            else {
                Btn save = Btn.secondary("Save here", e -> {
                    if (s.exists()) Confirm.ask(app, "Overwrite " + label + "?", "It holds " + s.description() + ".", "Overwrite", "Cancel", () -> app.session.saveTo(s.id()));
                    else app.session.saveTo(s.id());
                }).disabledBecause(inGame && app.session.g() != null ? null : "Start or load a game first");
                save.getAccessibleContext().setAccessibleName("Save to " + label);
                if (save.isEnabled()) save.tip("Save the current game to " + label);
                Btn del = Btn.quiet("Delete", e -> Confirm.ask(app, "Delete " + label + "?", "This cannot be undone.", "Delete", "Keep it", () -> app.session.delete(s)))
                        .disabledBecause(s.exists() ? null : "This slot is empty");
                del.getAccessibleContext().setAccessibleName("Delete " + label);
                if (del.isEnabled()) del.tip("Delete " + label);
                actions = Layouts.wrap(t.u(1), t.u(1), save, load, del);
            }
            JComponent game = s.exists() && s.savedAt() != null && !s.savedAt().isBlank()
                    ? Layouts.stack(t.px(2), Text.body(s.description()), Text.faint("Saved " + s.savedAt())) : Text.muted(s.description());
            table.row(label, game, actions);
        }
        c.with(table, Text.faint("Saves are files in " + app.session.home + "."));
        return c;
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
