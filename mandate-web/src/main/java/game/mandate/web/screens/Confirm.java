package game.mandate.web.screens;

import game.mandate.web.App;
import game.mandate.web.kit.Btn;
import game.mandate.web.kit.Card;
import game.mandate.web.kit.Layouts;
import game.mandate.web.kit.Text;
import game.mandate.web.kit.Theme;

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
