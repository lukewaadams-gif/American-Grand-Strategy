package game.mandate.ui.screens;

import game.mandate.core.electorate.StateCatalog;
import game.mandate.core.electorate.UsState;
import game.mandate.ui.App;
import game.mandate.ui.Session;
import game.mandate.ui.kit.Btn;
import game.mandate.ui.kit.Card;
import game.mandate.ui.kit.Layouts;
import game.mandate.ui.kit.Text;
import game.mandate.ui.kit.Theme;
import game.mandate.ui.kit.TileMap;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;

/** The title screen: the wordmark, the 2024 map as a reminder of the stakes, and the ways in. */
public final class MainMenu {
    private MainMenu() {}

    /** Set by the desktop window; tests leave it as a no-op. */
    public static Runnable quit = () -> { };

    public static JComponent build(App app) {
        Theme t = Theme.get();
        JPanel root = new JPanel(new GridLayout(1, 2)) {
            @Override protected void paintComponent(Graphics g) { g.setColor(t.bg); g.fillRect(0, 0, getWidth(), getHeight()); }
        };
        JPanel left = Layouts.stack(t.u(4));
        left.setBorder(BorderFactory.createEmptyBorder(t.u(24), t.u(20), t.u(10), t.u(10)));
        left.add(new Text("AMERICAN GRAND STRATEGY", t.bold(Theme.Size.SMALL), t.accent).upper());
        left.add(Text.display("Mandate", Theme.Size.HERO));
        left.add(new Text("Win the 2028 election. Take the oath. Govern your first hundred days.", t.font(Theme.Face.SERIF_ITALIC, Theme.Size.LEAD), t.muted));
        left.add(game.mandate.ui.kit.Ui.gap(t.u(4)));
        Session.Slot auto = app.session.slots().get(0);
        boolean resumable = auto.exists() && !auto.description().contains("finished") && !auto.description().equals("Unreadable save");
        JPanel buttons = Layouts.stack(t.u(2));
        if (resumable) buttons.add(Btn.primary("Continue", e -> { app.session.load(auto); app.startedGame(); }).tip("Resume: " + auto.description()));
        Btn fresh = resumable ? Btn.secondary("New game", e -> app.showNewGame()) : Btn.primary("New game", e -> app.showNewGame());
        buttons.add(fresh.tip("Choose a candidate and run for president"));
        buttons.add(Btn.secondary("Load a game", e -> SaveLoad.openFromMenu(app)).tip("Your autosave and three save slots"));
        buttons.add(Btn.secondary("Settings", e -> SettingsScreen.openFromMenu(app)).tip("Text size, contrast, colour-blind palette, complexity, tips"));
        buttons.add(Btn.secondary("Help and shortcuts", e -> Help.open(app)).hint("F1"));
        buttons.add(Btn.quiet("Quit", e -> quit.run()).tip("Your game is saved automatically every turn"));
        for (java.awt.Component b : buttons.getComponents()) ((JComponent) b).setPreferredSize(new Dimension(t.u(90), ((JComponent) b).getPreferredSize().height));
        JPanel narrow = new JPanel(new java.awt.BorderLayout());
        narrow.setOpaque(false);
        narrow.add(buttons, java.awt.BorderLayout.WEST);
        left.add(narrow);
        if (resumable) left.add(Text.faint("Saved game: " + auto.description()));
        left.add(game.mandate.ui.kit.Ui.gap(t.u(6)));
        left.add(Text.faint("A fictional cast by default: every candidate, event and outcome is invented. Real politicians are an opt-in roster, "
                + "and the game never invents personal scandals about real people."));
        root.add(left);

        Map<String, Double> margins = new LinkedHashMap<>();
        StateCatalog states = StateCatalog.load();
        for (UsState s : states.all()) margins.put(s.code(), s.baselineMargin());
        TileMap map = new TileMap(states.all(), margins, code -> Parts.margin(margins.get(code)),
                code -> states.get(code).name() + ": " + Parts.margin(margins.get(code)) + " in 2024, " + states.get(code).electoralVotes() + " electoral votes",
                "Republican", "Democratic", t.u(110));
        map.setFocusable(false);
        Card mapCard = new Card("The country you are running for: the 2024 result").with(map,
                Text.faint("Each tile is a state, sized equally so every one can be read. Colour shows the 2024 margin."));
        JPanel right = new JPanel(new java.awt.BorderLayout());
        right.setOpaque(false);
        right.setBorder(BorderFactory.createEmptyBorder(t.u(24), t.u(6), t.u(10), t.u(16)));
        right.add(mapCard, java.awt.BorderLayout.NORTH);
        root.add(right);
        return root;
    }
}
