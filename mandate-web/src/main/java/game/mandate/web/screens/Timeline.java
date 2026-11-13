package game.mandate.web.screens;

import game.mandate.web.kit.El;

import game.mandate.core.engine.ConsequenceLedger;
import game.mandate.core.engine.GameEngine;
import game.mandate.web.App;
import game.mandate.web.Notable;
import game.mandate.web.Session;
import game.mandate.web.kit.Card;
import game.mandate.web.kit.Layouts;
import game.mandate.web.kit.Segmented;
import game.mandate.web.kit.Text;
import game.mandate.web.kit.Theme;
import game.mandate.web.kit.Widgets;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The historical timeline and the decision log. The timeline is the game's own record, newest first, filterable;
 * the decision log is the consequence ledger: every decision and each effect it caused, with the system that
 * carried it out (raw effects at the Expert level). Together they answer "why did that happen?".
 */
public final class Timeline {
    private Timeline() {}

    static final List<String> FILTERS = List.of("Everything", "Notable moments", "Campaign", "Government", "Decisions");

    public static El build(App app) {
        GameEngine g = app.session.g();
        Theme t = Theme.get();
        int filter = (Integer) app.uiState.getOrDefault("timeline.filter", 1);
        Card log = new Card("The record, newest first");
        log.with(new Segmented(FILTERS, List.of("Every line of the record", "Votes, confirmations, crises, events and results", "The campaign's weeks",
                "Your time in office", "Your decisions"), filter, k -> { app.uiState.put("timeline.filter", k); app.refresh(); }));
        List<String> lines = new ArrayList<>(g.log());
        java.util.Collections.reverse(lines);
        LocalDate lastDate = null;
        int shown = 0, limit = 160;
        for (String l : lines) {
            if (shown >= limit) break;
            if (l.length() < 12 || !Character.isDigit(l.charAt(0))) continue;
            LocalDate d = LocalDate.parse(l.substring(0, 10));
            String body = l.substring(12);
            Optional<Session.Notice> n = Notable.classify(l);
            if (!keep(filter, g, d, body, n)) continue;
            if (!d.equals(lastDate)) { log.with(new Text(Notable.date(d), t.bold(Theme.Size.SMALL), t.accent)); lastDate = d; }
            String text = body.startsWith("Week ") && body.indexOf(": ") > 0 ? body.substring(body.indexOf(": ") + 2) : body;
            El row = n.isPresent()
                    ? Layouts.stack(t.px(2), new Widgets.Tag(n.get().title().toUpperCase(), app.tone(n.get().tone())), Text.body(text))
                    : Text.muted(text);
            log.with(row);
            shown++;
        }
        if (shown == 0) log.with(Text.muted("Nothing to show with this filter yet."));
        if (shown >= limit) log.with(Text.faint("Showing the latest " + limit + " entries."));
        return Parts.page("Timeline", "What happened, when, and what each decision caused.", Parts.row(Parts.w(log, 1.4), Parts.w(ledger(app, g), 1)));
    }

    static boolean keep(int filter, GameEngine g, LocalDate d, String body, Optional<Session.Notice> n) {
        boolean campaign = d.isBefore(g.calendar().inauguration()) || (d.isAfter(g.calendar().reelectionStart().minusDays(1)) && d.isBefore(g.calendar().election2032().plusDays(1)));
        return switch (filter) {
            case 1 -> n.isPresent() || body.startsWith("Election Night") || body.startsWith("Day 100") || body.startsWith("Inauguration")
                    || body.startsWith("The general election begins") || body.startsWith("You promise");
            case 2 -> campaign;
            case 3 -> !campaign;
            case 4 -> body.startsWith("Decision: ") || body.startsWith("You ") || body.contains(": You ");
            default -> true;
        };
    }

    static Card ledger(App app, GameEngine g) {
        Theme t = Theme.get();
        boolean expert = app.session.settings.expert(), guided = app.session.settings.guided();
        Card c = new Card("Decisions and what they caused");
        c.with(Text.faint("Every change to the country, Congress or the world is recorded against the decision that caused it."));
        ConsequenceLedger ledger = g.state().ledger();
        List<ConsequenceLedger.Decision> ds = new ArrayList<>(ledger.decisions());
        java.util.Collections.reverse(ds);
        int n = 0, max = expert ? 60 : guided ? 8 : 20;
        for (ConsequenceLedger.Decision d : ds) {
            List<ConsequenceLedger.Consequence> cs = ledger.consequencesOf(d.id());
            if (cs.isEmpty()) continue;
            Card item = new Card(null).fill(t.raised);
            item.with(new Text(Notable.date(d.date()), t.bold(Theme.Size.CAPTION), t.faint), Text.strong(tidy(d.description()), Theme.Size.BODY));
            int k = 0;
            for (ConsequenceLedger.Consequence x : cs) {
                if (!expert && k >= 6) { item.with(Text.faint("and " + (cs.size() - k) + " more (Expert complexity shows all)")); break; }
                item.with(Text.muted("→ " + x.effect() + (expert ? "   [" + x.owner() + "]" : "")));
                k++;
            }
            c.with(item);
            if (++n >= max) break;
        }
        if (n == 0) c.with(Text.muted("No decisions with consequences yet."));
        return c;
    }

    static String tidy(String s) {
        return s.replace("100 days, week ", "Week ").replace(": DraftBill", ": drafting the bill").replace(": Negotiate", ": negotiating")
                .replace(": CallVote", ": the vote").replace(": Nominate", ": a nomination").replace(": GoPublic", ": going public")
                .replace(": EndWeek", ": the week's events").replace(": MeetOpposition", ": meeting the opposition").replace(": LeanOnFed", ": leaning on the Fed");
    }
}
