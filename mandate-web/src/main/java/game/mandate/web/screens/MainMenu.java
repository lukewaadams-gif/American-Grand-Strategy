package game.mandate.web.screens;

import game.mandate.web.kit.El;

import game.mandate.core.electorate.StateCatalog;
import game.mandate.core.electorate.UsState;
import game.mandate.web.App;
import game.mandate.web.Session;
import game.mandate.web.kit.Btn;
import game.mandate.web.kit.Card;
import game.mandate.web.kit.Layouts;
import game.mandate.web.kit.Text;
import game.mandate.web.kit.Theme;
import game.mandate.web.kit.TileMap;

import java.util.LinkedHashMap;
import java.util.Map;

/** The title screen: the wordmark, the 2024 map as a reminder of the stakes, and the ways in. */
public final class MainMenu {
    private MainMenu() {}

    public static El build(App app) {
        Theme t = Theme.get();
        El root = El.div("menu");
        El left = Layouts.stack(t.u(4)).cls("menu-left");
        left.add(new Text("AMERICAN GRAND STRATEGY", t.bold(Theme.Size.SMALL), t.accent).upper());
        left.add(Text.display("Mandate", Theme.Size.HERO).heading(1));
        left.add(new Text("Win the 2028 election. Take the oath. Govern your first hundred days.", t.font(Theme.Face.SERIF_ITALIC, Theme.Size.LEAD), t.muted));
        left.add(game.mandate.web.kit.Ui.gap(t.u(4)));
        Session.Slot auto = app.session.slots().get(0);
        boolean resumable = auto.exists() && !auto.description().contains("finished") && !auto.description().equals("Unreadable save");
        El buttons = Layouts.stack(t.u(2)).cls("menu-buttons");
        if (resumable) buttons.add(Btn.primary("Continue", e -> { app.session.load(auto); app.startedGame(); }).tip("Resume: " + auto.description()));
        Btn fresh = resumable ? Btn.secondary("New game", e -> app.showNewGame()) : Btn.primary("New game", e -> app.showNewGame());
        buttons.add(fresh.tip("Choose a candidate and run for president"));
        buttons.add(Btn.secondary("Load a game", e -> SaveLoad.openFromMenu(app)).tip("Your autosave and three save slots in this browser, or a save file"));
        buttons.add(Btn.secondary("Settings", e -> SettingsScreen.openFromMenu(app)).hint(",").tip("Text size, contrast, colour-blind palette, complexity, tips"));
        buttons.add(Btn.secondary("Help and shortcuts", e -> Help.open(app)).hint("?"));
        left.add(buttons);
        if (resumable) left.add(Text.faint("Saved game: " + auto.description()));
        left.add(game.mandate.web.kit.Ui.gap(t.u(6)));
        left.add(Text.faint("Your games are saved in this browser, after every turn. Save and load can also export a save to a file and import one."));
        left.add(Text.faint("A fictional cast by default: every candidate, event and outcome is invented. Real politicians are an opt-in roster, "
                + "and the game never invents personal scandals about real people."));
        root.add(left);

        Map<String, Double> margins = new LinkedHashMap<>();
        StateCatalog states = StateCatalog.load();
        for (UsState s : states.all()) margins.put(s.code(), s.baselineMargin());
        TileMap map = new TileMap(states.all(), margins, code -> Parts.margin(margins.get(code)),
                code -> states.get(code).name() + ": " + Parts.margin(margins.get(code)) + " in 2024, " + states.get(code).electoralVotes() + " electoral votes",
                "Republican", "Democratic", t.u(110));
        Card mapCard = new Card("The country you are running for: the 2024 result").with(map,
                Text.faint("Each tile is a state, sized equally so every one can be read. Colour shows the 2024 margin."));
        root.add(El.div("menu-right").add(mapCard));
        return root;
    }
}
