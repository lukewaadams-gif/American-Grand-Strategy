package game.mandate.ui.screens;

import game.mandate.core.state.Party;
import game.mandate.ui.App;
import game.mandate.ui.Settings;
import game.mandate.ui.kit.Btn;
import game.mandate.ui.kit.Card;
import game.mandate.ui.kit.Layouts;
import game.mandate.ui.kit.Segmented;
import game.mandate.ui.kit.Text;
import game.mandate.ui.kit.Theme;
import game.mandate.ui.kit.Widgets;

import javax.swing.JComponent;
import java.util.List;

/**
 * Settings: text size, contrast and a colour-blind-safe palette; complexity (what the screens show, never the rules);
 * tips, the advisor, confirmations and motion. Every change applies at once and is saved for next time, and a
 * preview shows its effect.
 */
public final class SettingsScreen {
    private SettingsScreen() {}

    public static JComponent build(App app, boolean inGame) {
        return Parts.page("Settings", "Display, accessibility and how much the screens show.", body(app));
    }

    static JComponent body(App app) {
        Theme t = Theme.get();
        Settings s = app.session.settings;
        Card display = new Card("Display and accessibility");
        List<Double> scales = List.of(1.0, 1.25, 1.5);
        int si = scales.indexOf(s.scale);
        display.with(Text.eyebrow("Text and interface size"), new Segmented(List.of("100%", "125%", "150%"),
                List.of("For 1280x720 and up", "For 1600x900 and up, or reading at a distance", "For 1920x1080 and up, or low vision"),
                Math.max(0, si), k -> { s.scale = scales.get(k); app.settingsChanged(); }));
        Theme.Palette[] ps = Theme.Palette.values();
        display.with(Text.eyebrow("Colours"), new Segmented(List.of("Standard", "High contrast", "Colour-blind safe"),
                List.of("The situation-room palette", "Black surfaces, white text and borders", "Blue and orange in place of blue and red, and of green and red"),
                s.palette.ordinal(), k -> { s.palette = ps[k]; app.settingsChanged(); }));
        display.with(Text.eyebrow("Motion"), new Segmented(List.of("Standard", "Reduce"), List.of("Notifications stay about 6 seconds", "Notifications stay longer; nothing animates"),
                s.reduceMotion ? 1 : 0, k -> { s.reduceMotion = k == 1; app.settingsChanged(); }));
        Card play = new Card("How much the screens show");
        Settings.Complexity[] cs = Settings.Complexity.values();
        play.with(new Segmented(List.of(cs[0].label, cs[1].label, cs[2].label), List.of(cs[0].description, cs[1].description, cs[2].description), s.complexity.ordinal(),
                k -> { s.complexity = cs[k]; app.settingsChanged(); }), Text.muted(s.complexity.description), Text.faint("Complexity changes presentation only; the rules are the same."));
        play.with(Text.eyebrow("Coaching tips"), new Segmented(List.of("On", "Off"), List.of("A tip at each new kind of decision", "No tips"), s.tips ? 0 : 1,
                k -> { s.tips = k == 0; app.settingsChanged(); }));
        Btn again = Btn.secondary("Show every tip again", e -> { app.session.seenLessons().clear(); s.tips = true; app.settingsChanged(); })
                .tip("Forget which tips you have seen");
        play.with(again);
        play.with(Text.eyebrow("The advisor"), new Segmented(List.of("On", "Off"), List.of("A summary of what your screens show, ranked", "No advisor"), s.advisor ? 0 : 1,
                k -> { s.advisor = k == 0; app.settingsChanged(); }));
        play.with(Text.eyebrow("Decisions"), new Segmented(List.of("Open them for me", "I will open them"), List.of("A new decision opens as soon as it arrives",
                "Decisions wait behind the red button (Ctrl+D)"), s.autoOpenDecisions ? 0 : 1, k -> { s.autoOpenDecisions = k == 0; app.settingsChanged(); }));
        play.with(Text.eyebrow("Ending a turn with attention left"), new Segmented(List.of("Ask me", "Just end it"), List.of("Confirm before wasting attention", "No confirmation"),
                s.confirmUnspent ? 0 : 1, k -> { s.confirmUnspent = k == 0; app.settingsChanged(); }));
        Card preview = new Card("Preview");
        preview.with(Text.display("The Oval Office", Theme.Size.TITLE), Text.body("Body text reads like this; secondary text like this:"), Text.muted("Secondary text."),
                Parts.kpis(Parts.kpi("Approval", "51%", "+1.2 since the oath", t.good, "Approval"), Parts.kpi("Forecast", "47%", "polls only", null, "Forecast")),
                Layouts.wrap(t.u(2), t.u(1), new Widgets.Tag("D+2.1", t.party(Party.DEMOCRAT)), new Widgets.Tag("R+1.4", t.party(Party.REPUBLICAN)),
                        new Widgets.Tag("CONFIRMED", t.good), new Widgets.Tag("WATCH", t.warn), new Widgets.Tag("REJECTED", t.bad)));
        Card keys = new Card("Controls").with(Text.muted("Every screen and action has a shortcut; F1 lists them all. Tab moves between controls, and the focused control has an outline."),
                Btn.secondary("Show shortcuts and terms", e -> Help.open(app)).hint("F1"));
        return Layouts.stack(t.u(4), Parts.row(Parts.w(display, 1), Parts.w(play, 1.2)), Parts.row(Parts.w(preview, 1), Parts.w(keys, 1)));
    }

    /** From the main menu, as a window over it. */
    public static void openFromMenu(App app) {
        Theme t = Theme.get();
        Card c = new Card("Options", Btn.quiet("Close", e -> app.stage.closeModal()).hint("Esc").tip("Close settings (Esc)"))
                .with(Text.display("Settings", Theme.Size.TITLE), body(app), Btn.secondary("Close", e -> app.stage.closeModal()));
        app.modalRebuilder = () -> openFromMenu(app);
        app.stage.showModal(c, t.u(260), true, () -> app.modalRebuilder = null);
    }
}
