package game.mandate.ui.screens;

import game.mandate.ui.App;
import game.mandate.ui.kit.Btn;
import game.mandate.ui.kit.Card;
import game.mandate.ui.kit.Layouts;
import game.mandate.ui.kit.Text;
import game.mandate.ui.kit.Theme;

/** A two-way confirmation: the safe choice is the default focus, the consequential one is named for what it does. */
public final class Confirm {
    private Confirm() {}

    public static void ask(App app, String title, String body, String yes, String no, Runnable onYes) {
        Theme t = Theme.get();
        Card c = new Card(null).stroke(t.borderStrong);
        Btn keep = Btn.secondary(no, e -> app.stage.closeModal());
        Btn go = Btn.primary(yes, e -> { app.stage.closeModal(); onYes.run(); });
        c.with(Text.display(title, Theme.Size.TITLE), Text.body(body), Layouts.wrap(t.u(2), t.u(1), keep, go));
        app.stage.showModal(c, t.u(130), true, null);
    }
}
