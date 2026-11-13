package game.mandate.web.screens;

import game.mandate.core.advisor.Advisor;
import game.mandate.web.App;
import game.mandate.web.Glossary;
import game.mandate.web.kit.Btn;
import game.mandate.web.kit.Card;
import game.mandate.web.kit.Table;
import game.mandate.web.kit.Text;
import game.mandate.web.kit.Theme;

import java.util.List;

/** Help: every shortcut and every mechanic in one place (?), and the adviser's full brief on narrow windows. */
public final class Help {
    private Help() {}

    public static void open(App app) {
        Theme t = Theme.get();
        // Help is long, so its Close sits at the top as well as the bottom: a mouse user never has to hunt for it.
        Card c = new Card("Help", Btn.quiet("Close", e -> app.stage.closeModal()).hint("Esc").tip("Close help (Esc)"));
        c.with(Text.display("Shortcuts and terms", Theme.Size.TITLE));
        Table keys = new Table(List.of(Table.Col.left("Keys", 1), Table.Col.left("What it does", 4)));
        for (App.Shortcut s : app.shortcuts()) keys.row(s.keys(), s.what());
        keys.row("1-9", "In a decision card: choose that option");
        keys.row("Arrow keys", "In a segmented choice or the map: move the selection; Enter or Space picks");
        keys.row("Tab, Shift+Tab", "Move between controls; the focused control has a blue outline");
        c.with(keys);
        Table terms = new Table(List.of(Table.Col.left("Term", 1), Table.Col.left("What it means", 4)));
        for (var e : Glossary.TERMS.entrySet()) terms.row(e.getKey(), e.getValue());
        c.with(Text.eyebrow("How the game works"), terms);
        c.with(Btn.secondary("Close", e -> app.stage.closeModal()));
        app.stage.showModal(c, t.u(220), true, null);
    }

    public static void advisorBrief(App app) {
        Theme t = Theme.get();
        Card c = new Card("Your chief adviser", Btn.quiet("Close", e -> app.stage.closeModal()).hint("Esc").tip("Close the brief (Esc)"));
        c.with(Text.faint("A summary of what your screens show. It sees what you see, no more."));
        for (Advisor.Advice a : Advisor.brief(app.session.g(), 8)) c.with(Shell.adviceCard(app, a, true));
        c.with(Btn.secondary("Close", e -> app.stage.closeModal()));
        app.stage.showModal(c, t.u(140), true, null);
    }
}
