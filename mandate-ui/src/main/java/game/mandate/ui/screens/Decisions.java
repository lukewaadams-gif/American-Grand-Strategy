package game.mandate.ui.screens;

import game.mandate.core.campaign.DebatePosture;
import game.mandate.core.command.Command;
import game.mandate.core.effects.Effect;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.events.EventOption;
import game.mandate.core.events.PendingEvent;
import game.mandate.core.opening.ChiefOfStaff;
import game.mandate.core.opening.Decision;
import game.mandate.core.opening.HundredDays;
import game.mandate.core.opening.OpeningAction;
import game.mandate.core.time.Phase;
import game.mandate.ui.App;
import game.mandate.ui.kit.Btn;
import game.mandate.ui.kit.Card;
import game.mandate.ui.kit.Choice;
import game.mandate.ui.kit.Layouts;
import game.mandate.ui.kit.Text;
import game.mandate.ui.kit.Theme;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decision cards. A decision opens over the screen with its situation and numbered options, each with its
 * trade-offs and, when it carries odds, a risk tag; the number keys choose. After the choice, the same card shows
 * what happened, in the game's own words, before the next decision. "Decide later" closes the card so the player can
 * look at other screens first.
 */
public final class Decisions {
    private Decisions() {}

    /** A decision waiting for the player, in one shape for all three sources (the hundred days, events, debates). */
    record Pending(String key, String eyebrow, String title, String situation, List<Opt> options, String lesson) {}

    record Opt(String label, String detail, String tag, Color tagColor, Runnable choose) {}

    static List<Pending> pending(App app) {
        GameEngine g = app.session.g();
        List<Pending> out = new ArrayList<>();
        if (g == null || g.over()) return out;
        Theme t = Theme.get();
        if (g.phase() == Phase.FIRST_100_DAYS) {
            HundredDays h = g.hundredDays().orElseThrow();
            for (Decision d : h.pending()) {
                if (ChiefOfStaff.handles(d.kind(), h.delegated())) continue;
                List<Opt> os = new ArrayList<>();
                for (int i = 0; i < d.options().size(); i++) {
                    int k = i;
                    Decision.Option o = d.options().get(i);
                    Integer pct = percent(o.preview());
                    os.add(new Opt(o.label(), o.preview(), pct == null ? null : "about " + pct + "%", pct == null ? null : t.level(pct / 100.0),
                            () -> app.session.act(new OpeningAction.Decide(d.id(), k))));
                }
                out.add(new Pending("d" + d.id(), kindLabel(d.kind()), d.title(), d.situation(), os, switch (d.kind()) {
                    case CRISIS -> "days.crisis"; case ECONOMY -> "days.economy"; case FUNDING -> "days.funding"; default -> null; }));
            }
            return out;
        }
        for (PendingEvent p : g.pendingEvents()) {
            List<Opt> os = new ArrayList<>();
            List<EventOption> eo = p.definition().options();
            for (int i = 0; i < eo.size(); i++) {
                int k = i;
                EventOption o = eo.get(i);
                String detail = effects(o.effects()) + (o.successChance() < 1 ? "  If it fails: " + effects(o.failureEffects()) : "");
                os.add(new Opt(o.label(), detail, o.successChance() < 1 ? String.format("Gamble: %.0f%%", 100 * o.successChance()) : null,
                        o.successChance() < 1 ? t.warn : null, () -> app.session.apply(new Command.ResolveEvent(p.instanceId(), k))));
            }
            out.add(new Pending("e" + p.instanceId(), "Event", p.definition().title(), p.definition().body(), os, "campaign.event"));
        }
        if (Parts.campaign(g) && g.debateThisWeek().isPresent() && g.election().pendingPosture() == null) {
            DebatePosture theirs = DebatePosture.tendencyOf(g.opponent().candidate());
            List<Opt> os = new ArrayList<>();
            for (DebatePosture d : DebatePosture.values()) {
                boolean counters = d.counters(theirs);
                os.add(new Opt(d.label(), "Your rating with this approach: " + d.rating(g.player().candidate()) + " of 10."
                        + (counters ? " It counters your opponent's usual style." : ""), counters ? "Counters them" : null, counters ? t.good : null,
                        () -> app.session.apply(new Command.ChooseDebatePosture(d))));
            }
            out.add(new Pending("debate" + g.date(), "Debate this week", "How will you debate?",
                    "Prep notes: your opponent tends to \"" + theirs.label().toLowerCase() + "\". A posture that counters theirs scores extra; a steady one limits the risk of a bad night.",
                    os, "campaign.debate"));
        }
        return out;
    }

    static Integer percent(String s) {
        Matcher m = Pattern.compile("(\\d{1,3})%").matcher(s);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    static String kindLabel(Decision.Kind k) {
        return switch (k) {
            case CRISIS -> "Foreign crisis"; case AFTERMATH -> "After the clash"; case FUNDING -> "Government funding"; case SHUTDOWN -> "Shutdown";
            case BILL_FAILED -> "Your flagship bill"; case BOTTLED_IN_COMMITTEE -> "Your flagship bill"; case NOMINEE_REJECTED -> "Your cabinet";
            case FACTION_REVOLT -> "Your party"; case ECONOMY -> "Economic challenge"; case LEAK -> "A leak";
        };
    }

    static String effects(List<Effect> es) {
        if (es.isEmpty()) return "No direct effect.";
        return String.join("; ", es.stream().map(Effect::describe).toList()) + ".";
    }

    public static int count(App app) { return pending(app).size(); }

    public static String firstUnseen(App app, Set<String> seen) {
        for (Pending p : pending(app)) if (!seen.contains(p.key())) return p.key();
        return null;
    }

    /** Opens the first waiting decision, if there is one. */
    public static void open(App app) {
        List<Pending> ps = pending(app);
        if (ps.isEmpty()) return;
        show(app, ps.get(0), ps.size());
    }

    static void show(App app, Pending p, int total) {
        Theme t = Theme.get();
        app.markDecisionSeen(p.key());
        Card c = new Card(null).fill(t.surface).stroke(t.borderStrong);
        c.with(new Text((p.eyebrow() + (total > 1 ? "  ·  1 of " + total : "")).toUpperCase(), t.bold(Theme.Size.CAPTION), t.accent),
                Text.display(p.title(), Theme.Size.TITLE), Text.body(p.situation()));
        boolean[] tipShown = {false};
        if (p.lesson() != null && app.session.settings.tips && !app.session.seenLessons().contains(p.lesson()))
            game.mandate.core.tutorial.Coach.available(app.session.g(), app.session.seenLessons()).stream().filter(l -> l.id().equals(p.lesson())).findFirst().ifPresent(l -> {
                Card tip = new Card(null).stripe(t.accent).fill(Theme.mix(t.surface, t.accent, 0.08));
                tip.with(Layouts.stack(t.px(2), new Text("TIP", t.bold(Theme.Size.CAPTION), t.accent), Text.of(l.prompt(), Theme.Face.UI, Theme.Size.SMALL, t.text)));
                tip.getAccessibleContext().setAccessibleName("Tip: " + l.prompt());
                c.with(tip);
                app.session.lessonSeen(l.id());
                tipShown[0] = true;
            });
        List<Choice> choices = new ArrayList<>();
        for (int i = 0; i < p.options().size(); i++) {
            Opt o = p.options().get(i);
            Choice ch = new Choice(String.valueOf(i + 1), o.label(), o.detail(), o.tag(), o.tagColor() == null ? t.info : o.tagColor(), e -> choose(app, o));
            choices.add(ch);
            c.with(ch);
        }
        c.with(Layouts.wrap(t.u(3), t.u(1), Btn.quiet("Decide later", e -> app.stage.closeModal())
                        .tip("Close this card to look at other screens first. The turn cannot end until it is answered (Ctrl+D reopens it)."),
                Text.faint("Press 1-" + p.options().size() + " to choose, Esc to decide later.")));
        for (int i = 0; i < choices.size() && i < 9; i++) {
            Choice ch = choices.get(i);
            String name = "choose" + (i + 1);
            c.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(String.valueOf(i + 1)), name);
            c.getActionMap().put(name, new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { ch.doClick(0); } });
        }
        // The tip now lives here, so the screen behind drops its copy when the card closes.
        app.stage.showModal(c, t.u(180), true, tipShown[0] ? app::refresh : null);
    }

    /** Applies the choice, then shows what it did. */
    static void choose(App app, Opt o) {
        GameEngine g = app.session.g();
        int logBefore = g.log().size();
        int daysBefore = g.hundredDays().map(h -> h.log().size()).orElse(0);
        app.stage.closeModal();
        o.choose().run();
        GameEngine now = app.session.g();
        List<String> happened = new ArrayList<>();
        if (now.hundredDays().isPresent() && now.phase() == Phase.FIRST_100_DAYS) {
            List<String> log = now.hundredDays().get().log();
            for (int i = daysBefore; i < log.size(); i++) happened.add(strip(log.get(i)));
        } else {
            List<String> log = now.log();
            for (int i = logBefore; i < log.size(); i++) happened.add(log.get(i).length() > 12 ? log.get(i).substring(12) : log.get(i));
        }
        happened.removeIf(s -> s.startsWith("Decision: "));
        Theme t = Theme.get();
        Card c = new Card("What happened");
        c.with(Text.strong("You chose: " + o.label(), Theme.Size.LEAD));
        if (happened.isEmpty()) c.with(Text.muted("The choice is recorded; its effects arrive as the week resolves."));
        for (String s : happened) c.with(Text.body(s));
        c.with(Text.faint("Every effect is listed in the Timeline with its cause."));
        List<Pending> next = pending(app);
        Btn go = next.isEmpty() ? Btn.primary("Continue", e -> app.stage.closeModal())
                : Btn.primary("Next decision (" + next.size() + " waiting)", e -> { app.stage.closeModal(); open(app); });
        c.with(Layouts.wrap(t.u(2), t.u(1), go));
        app.stage.showModal(c, t.u(150), true, null);
    }

    static String strip(String line) {
        int colon = line.indexOf(": ");
        return line.startsWith("Week ") && colon > 0 ? line.substring(colon + 2) : line;
    }
}
