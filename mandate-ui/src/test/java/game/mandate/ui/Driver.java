package game.mandate.ui;

import game.mandate.ui.kit.Btn;
import game.mandate.ui.kit.Choice;
import game.mandate.ui.kit.Text;

import javax.swing.AbstractButton;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Plays the interface the way a player does: by pressing visible, enabled buttons. Nothing calls the engine directly. */
final class Driver {
    final App app;

    Driver(App app, int width, int height) {
        this.app = app;
        app.stage.setSize(width, height);
        settle();
    }

    void settle() { UiChecks.layout(app.stage); }

    void press(String label) { settle(); Screenshots.press(app, label); settle(); }

    void pressStarting(String prefix) { settle(); Screenshots.pressFirst(app, prefix); settle(); }

    /** Picks the first of a screen's large options (a candidate, a strategy) outside any open card. */
    void pickFirstOption() {
        settle();
        buttons(app.stage).stream().filter(x -> x instanceof Choice && x.isEnabled()).findFirst()
                .orElseThrow(() -> new AssertionError("no option to pick")).doClick(0);
        settle();
    }

    boolean modalOpen() { return app.stage.modalOpen(); }

    /** Answers whatever card is open: the first option if it offers options, otherwise its main button. */
    void answerModal() {
        settle();
        Component m = app.stage.modal();
        Optional<AbstractButton> b = buttons(m).stream().filter(x -> x instanceof Choice && x.isEnabled()).findFirst();
        if (b.isEmpty()) b = buttons(m).stream().filter(x -> x instanceof Btn k && k.kind() == Btn.Kind.PRIMARY && x.isEnabled()).findFirst();
        if (b.isEmpty()) b = buttons(m).stream().filter(AbstractButton::isEnabled).findFirst();
        b.orElseThrow(() -> new AssertionError("an open card has no button to press")).doClick(0);
        settle();
    }

    /** The automated checks on what is on screen now. */
    List<String> problems() { settle(); return UiChecks.check(app.stage); }

    boolean shows(String text) { settle(); return texts(app.stage).stream().anyMatch(s -> s.toLowerCase().contains(text.toLowerCase())); }

    boolean modalShows(String text) { settle(); return modalOpen() && texts(app.stage.modal()).stream().anyMatch(s -> s.toLowerCase().contains(text.toLowerCase())); }

    static List<AbstractButton> buttons(Component c) {
        List<AbstractButton> out = new ArrayList<>();
        collect(c, out, AbstractButton.class);
        return out.stream().filter(Component::isVisible).toList();
    }

    static List<String> texts(Component c) {
        List<String> out = new ArrayList<>();
        walk(c, out);
        return out;
    }

    static void walk(Component c, List<String> out) {
        if (!c.isVisible()) return;
        if (c instanceof Text t && t.getText() != null) out.add(t.getText());
        if (c instanceof AbstractButton b && b.getText() != null) out.add(b.getText());
        if (c instanceof Container ct) for (Component k : ct.getComponents()) walk(k, out);
    }

    static <T> void collect(Component c, List<T> out, Class<T> type) {
        if (type.isInstance(c)) out.add(type.cast(c));
        if (c instanceof Container ct) for (Component k : ct.getComponents()) collect(k, out, type);
    }
}
