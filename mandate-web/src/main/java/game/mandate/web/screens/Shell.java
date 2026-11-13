package game.mandate.web.screens;

import game.mandate.core.data.Fmt;

import game.mandate.web.kit.El;

import game.mandate.core.advisor.Advisor;
import game.mandate.core.campaign.PollPoint;
import game.mandate.core.command.Command;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.opening.ChiefOfStaff;
import game.mandate.core.opening.HundredDays;
import game.mandate.core.opening.OpeningAction;
import game.mandate.core.time.Phase;
import game.mandate.core.tutorial.Coach;
import game.mandate.web.App;
import game.mandate.web.App.ScreenId;
import game.mandate.web.Notable;
import game.mandate.web.kit.Btn;
import game.mandate.web.kit.Card;
import game.mandate.web.kit.Layouts;
import game.mandate.web.kit.Text;
import game.mandate.web.kit.Theme;
import game.mandate.web.kit.Ui;
import game.mandate.web.kit.Widgets;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The frame around every game screen. The top bar says who and where you are, shows the numbers that matter in
 * this phase, and holds the one primary action (end the turn); the rail lists the screens with their shortcuts;
 * the advisor sits on the right on wide windows and as a single line on narrow ones.
 */
public final class Shell {
    private Shell() {}

    /** Windows narrower than this show the advisor as a one-line banner instead of a side panel. */
    static final int WIDE = 1440;

    public static El build(App app) {
        Theme t = Theme.get();
        GameEngine g = app.session.g();
        boolean wide = game.mandate.web.kit.Dom.width() >= Theme.get().px(WIDE);
        El root = El.div("shell").toggle("with-advisor", app.advisorOpen() && wide);
        root.add(topBar(app, g), rail(app, g));
        El main = new El("main").cls("main").attr("id", "main").attr("tabindex", "0").label(app.screen().label);
        El above = Layouts.stack(t.u(2)).cls("above");
        if (app.advisorOpen() && !wide) Advisor.brief(g, 1).stream().findFirst().ifPresent(a -> above.add(advisorBanner(app, a)));
        tip(app, g).ifPresent(above::add);
        if (!above.children().isEmpty()) main.add(above);
        main.add(screen(app));
        root.add(main);
        if (app.advisorOpen() && wide) root.add(advisorPanel(app, g));
        return root;
    }

    static El screen(App app) {
        return switch (app.screen()) {
            case OVERVIEW -> Overview.build(app);
            case CAMPAIGN -> CampaignHQ.build(app);
            case OVAL -> OvalOffice.build(app);
            case CONGRESS -> CongressScreen.build(app);
            case ECONOMY -> EconomyScreen.build(app);
            case SECURITY -> SecurityScreen.build(app);
            case TIMELINE -> Timeline.build(app);
            case RESULTS -> Results.build(app);
            case SAVES -> SaveLoad.build(app, true);
            case SETTINGS -> SettingsScreen.build(app, true);
        };
    }

    // ---- top bar ---------------------------------------------------------------------------------------------

    static El topBar(App app, GameEngine g) {
        Theme t = Theme.get();
        String who = g.player().candidate().name() + " (" + g.player().party().code() + ")";
        El id = Layouts.stack(t.px(2), new Text(Notable.phase(g).toUpperCase(), t.bold(Theme.Size.CAPTION), t.accent),
                Text.strong(who, Theme.Size.LEAD), Text.muted(Notable.date(g.date()))).cls("identity");
        El bar = Layouts.side(t.u(6), id, Parts.kpis(kpis(g).toArray(El[]::new)), actions(app, g));
        bar.cls("topbar");
        return new El("header").cls("topbar-wrap").add(bar);
    }

    static List<El> kpis(GameEngine g) {
        Theme t = Theme.get();
        List<El> k = new ArrayList<>();
        switch (g.phase()) {
            case GENERAL_CAMPAIGN_2028, REELECTION_CAMPAIGN_2032 -> {
                LocalDate day = g.phase() == Phase.GENERAL_CAMPAIGN_2028 ? g.calendar().election2028() : g.calendar().election2032();
                List<PollPoint> polls = g.polls();
                PollPoint p = polls.isEmpty() ? null : polls.get(polls.size() - 1);
                k.add(Parts.kpi("Cash on hand", Fmt.format("$%.0fM", g.player().cash()), Fmt.format("theirs about $%.0fM", g.opponent().cash()), null,
                        "Field and ads"));
                if (p != null) {
                    double prev = polls.size() > 1 ? polls.get(polls.size() - 2).national() : p.national();
                    k.add(Parts.kpi("National polls", Parts.signed(p.national(), "%.1f"), Parts.signed(p.national() - prev, "%.1f") + " this week",
                            Parts.change(p.national() - prev, 0.05), "Polls"));
                    k.add(Parts.kpi("Electoral votes", String.valueOf(p.electoralVotes()), "if the state polls are right", null, "Tipping point"));
                    k.add(Parts.kpi("Forecast", Parts.pct(p.winChance()), "polls only", null, "Forecast"));
                }
                k.add(Parts.kpi("Election Day", ChronoUnit.WEEKS.between(g.date(), day) + " weeks", Notable.date(day), null, null));
            }
            case TRANSITION -> {
                int ev = g.result2028().orElseThrow().electoralVotes(g.player().party());
                k.add(Parts.kpi("Mandate", ev + " EV", "electoral votes won", null, "Mandate"));
                k.add(Parts.kpi("Congress", g.unifiedGovernment() ? "Unified" : "Divided", g.congress().toString(), null, null));
            }
            case FIRST_100_DAYS -> g.hundredDays().ifPresent(h -> {
                double start = g.state().hundredDays().startApproval();
                k.add(Parts.kpi("Week", h.week() + " of " + h.weeks(), null, null, null));
                k.add(new Widgets.Kpi("Attention", h.attention() + " of 3", "left this week", null, game.mandate.web.Glossary.of("Attention")));
                k.add(Parts.kpi("Approval", Fmt.format("%.0f%%", h.approval()), Parts.signed(h.approval() - start, "%.1f") + " since the oath",
                        Parts.change(h.approval() - start, 0.05), "Approval"));
                k.add(Parts.kpi("Political capital", Fmt.format("%.0f", h.capital()), null, null, "Political capital"));
                k.add(Parts.kpi("Opposition", h.opposition().name().charAt(0) + h.opposition().name().substring(1).toLowerCase(), Fmt.format("goodwill %.0f", h.goodwill()),
                        null, "Opposition strategy"));
            });
            case GOVERNING -> {
                var e = g.state().economy();
                k.add(Parts.kpi("Approval", Fmt.format("%.0f%%", g.approval()), null, null, "Approval"));
                k.add(Parts.kpi("Political capital", Fmt.format("%.0f", g.capital()), null, null, "Political capital"));
                k.add(Parts.kpi("Attention", g.attention() + " of 3", "this month", null, "Attention"));
                k.add(Parts.kpi("Unemployment", Fmt.format("%.1f%%", e.unemployment()), null, null, null));
                k.add(Parts.kpi("Inflation", Fmt.format("%.1f%%", e.inflation()), null, null, null));
            }
            default -> k.add(Parts.kpi("Game", "Finished", "see Results", null, null));
        }
        return k;
    }

    static El actions(App app, GameEngine g) {
        Theme t = Theme.get();
        El p = Layouts.wrap(t.u(2), t.u(2)).cls("actions");
        int waiting = Decisions.count(app);
        if (waiting > 0) p.add(Btn.danger("Decisions (" + waiting + ")", e -> Decisions.open(app)).hint("D")
                .tip("Decisions waiting for you. The turn cannot end until they are answered."));
        p.add(Btn.secondary("Undo", e -> app.session.undo()).disabledBecause(app.session.canUndo() ? null : "Nothing to undo yet")
                .tip(app.session.canUndo() ? app.session.undoLabel() + " (Ctrl+Z). " + game.mandate.web.Glossary.of("Undo and rewind") : "Nothing to undo yet"));
        if (app.session.settings.advisor)
            p.add(Btn.secondary("Advisor", e -> app.toggleAdvisor()).selected(app.advisorOpen()).tip((app.advisorOpen() ? "Hide" : "Show") + " your chief adviser's brief (A)"));
        p.add(Btn.secondary("Menu", e -> menu(app)).tip("Save, load, settings, help, or return to the main menu"));
        String why = endTurnBlocked(app, g);
        Btn end = Btn.primary(endTurnLabel(g), e -> endTurn(app)).hint("Ctrl+Enter");
        if (why != null) end.tip(why); else end.tip(endTurnTip(g));
        if (g.over()) end.disabledBecause("The game is over: see Results");
        p.add(end);
        return p;
    }

    static String endTurnLabel(GameEngine g) {
        return switch (g.phase()) {
            case GENERAL_CAMPAIGN_2028, REELECTION_CAMPAIGN_2032 -> "Run the week";
            case TRANSITION -> "Take the oath";
            case FIRST_100_DAYS -> "End the week";
            case GOVERNING -> "End the month";
            default -> "Finished";
        };
    }

    static String endTurnTip(GameEngine g) {
        return switch (g.phase()) {
            case GENERAL_CAMPAIGN_2028, REELECTION_CAMPAIGN_2032 -> "Both campaigns carry out their plans, a debate is held if one is due, and the polls update.";
            case TRANSITION -> "Inauguration Day: your agenda's first priority becomes your flagship bill and the first hundred days begin.";
            case FIRST_100_DAYS -> "Hearings and votes happen, the economy and opinion move, and you get the week's briefing.";
            case GOVERNING -> "The month resolves: the economy, opinion, the world and events.";
            default -> "";
        };
    }

    /** Why the turn cannot end right now, or null. */
    static String endTurnBlocked(App app, GameEngine g) {
        if (Decisions.count(app) > 0) return "Answer the decisions waiting for you first (D).";
        if (g.phase() == Phase.TRANSITION && g.agenda().isEmpty()) return "Choose your agenda first (Oval Office).";
        return null;
    }

    /** Ends the turn, after making sure nothing is waiting and asking about unspent attention. */
    public static void endTurn(App app) {
        GameEngine g = app.session.g();
        if (g == null || app.view() != App.View.GAME) return;
        if (g.over()) { app.open(ScreenId.RESULTS); return; }
        if (Decisions.count(app) > 0) { Decisions.open(app); return; }
        if (g.phase() == Phase.TRANSITION && g.agenda().isEmpty()) {
            app.open(ScreenId.OVAL);
            app.stage.toast("Choose your agenda first", "Pick up to three priorities; the first is your flagship.", Theme.get().warn);
            return;
        }
        int unspent = g.phase() == Phase.FIRST_100_DAYS ? g.hundredDays().map(HundredDays::attention).orElse(0)
                : g.phase() == Phase.GOVERNING ? g.attention() : 0;
        Runnable go = () -> {
            if (g.phase() == Phase.FIRST_100_DAYS) app.session.act(new OpeningAction.EndWeek());
            else app.session.apply(new Command.EndTurn());
        };
        if (unspent > 0 && app.session.settings.confirmUnspent) {
            Confirm.ask(app, "End the week with attention left?", "You have " + unspent + " attention point" + (unspent == 1 ? "" : "s")
                    + " left. Unused attention does not carry over.", "End anyway", "Keep working", go);
            return;
        }
        go.run();
    }

    static void menu(App app) {
        Theme t = Theme.get();
        Card c = new Card("Menu");
        c.with(Btn.secondary("Save and load", e -> app.open(ScreenId.SAVES)).hint("S"),
                Btn.secondary("Settings", e -> app.open(ScreenId.SETTINGS)).hint(","),
                Btn.secondary("Help and shortcuts", e -> Help.open(app)).hint("?"),
                Btn.secondary("Return to the main menu", e -> app.showMenu()).tip("Your game is saved automatically every turn; Continue resumes it."),
                Btn.quiet("Close", e -> app.stage.closeModal()));
        app.stage.showModal(c, t.u(100), true, null);
    }

    // ---- navigation rail ----------------------------------------------------------------------------------------

    static El rail(App app, GameEngine g) {
        Theme t = Theme.get();
        El rail = new El("nav").cls("rail stack").px("gap", t.u(1)).label("Screens");
        for (ScreenId s : ScreenId.values()) {
            if (s == ScreenId.SAVES) rail.add(Ui.gap(t.u(4)));
            rail.add(navButton(app, s, g));
        }
        return rail;
    }

    static Btn navButton(App app, ScreenId s, GameEngine g) {
        Btn b = new Btn(s.label, app.screen() == s ? Btn.Kind.SECONDARY : Btn.Kind.QUIET, e -> app.open(s)).left();
        b.selected(app.screen() == s).hint(s.keyLabel()).tip(s.purpose + " (" + s.keyLabel() + ")");
        if (app.screen() == s) b.attr("aria-current", "page");
        if (s == ScreenId.RESULTS && g.result2028().isEmpty()) b.disabledBecause("Results appear on election night");
        if (s == ScreenId.OVAL && Parts.campaign(g) && g.phase() == Phase.GENERAL_CAMPAIGN_2028) b.disabledBecause("The Oval Office is yours after Inauguration Day");
        return b;
    }

    // ---- the advisor ---------------------------------------------------------------------------------------------

    static El advisorPanel(App app, GameEngine g) {
        Theme t = Theme.get();
        El p = new El("aside").cls("advisor stack").px("gap", t.u(3)).label("Your chief adviser").attr("tabindex", "0");
        p.add(Text.eyebrow("Your chief adviser"));
        p.add(Text.faint("A summary of what your screens show. It sees what you see, no more."));
        List<Advisor.Advice> brief = Advisor.brief(g, app.session.settings.guided() ? 4 : 6);
        if (brief.isEmpty()) p.add(Text.muted(g.phase() == game.mandate.core.time.Phase.EPILOGUE || g.phase() == game.mandate.core.time.Phase.GAME_OVER
                ? "The game is over. The report explains each result; Play it differently replays from a point you choose."
                : "Nothing pressing. A quiet moment is a good time to plan."));
        for (Advisor.Advice a : brief) p.add(adviceCard(app, a, true));
        return p;
    }

    static Card adviceCard(App app, Advisor.Advice a, boolean full) {
        Theme t = Theme.get();
        Card c = new Card(null).stripe(Parts.tone(a.tone())).fill(t.surface);
        c.with(Layouts.stack(t.px(2), new Text(Parts.toneWord(a.tone()).toUpperCase(), t.bold(Theme.Size.CAPTION), Parts.tone(a.tone())),
                Text.strong(a.headline(), Theme.Size.BODY)));
        if (full) c.with(Text.muted(a.detail()), Text.faint("Based on: " + a.basis()));
        ScreenId target = Parts.screenFor(a.topic(), app.session.g());
        if (target != app.screen()) c.with(Btn.quiet("Go to " + target.label, e -> app.open(target)).tip("Open " + target.label + " (" + target.keyLabel() + ")"));
        c.label(Parts.toneWord(a.tone()) + ": " + a.headline() + ". " + a.detail());
        return c;
    }

    static El advisorBanner(App app, Advisor.Advice a) {
        Theme t = Theme.get();
        Card c = new Card(null).stripe(Parts.tone(a.tone()));
        c.with(Ui.border(Layouts.stack(t.px(2), Text.strong("Adviser: " + a.headline(), Theme.Size.BODY), Text.muted(a.detail()).lines(2)),
                Btn.quiet("Full brief", e -> Help.advisorBrief(app)).tip("The adviser's full brief (A toggles the panel on wide windows)")));
        return c;
    }

    // ---- the coach -------------------------------------------------------------------------------------------------

    static Optional<El> tip(App app, GameEngine g) {
        if (!app.session.settings.tips) return Optional.empty();
        // A tip shows on the screen it is about, and on the overview; elsewhere it waits, so it does not follow the player around.
        return Coach.available(g, app.session.seenLessons()).stream()
                .filter(l -> app.screen() == ScreenId.OVERVIEW || lessonScreen(l.id()) == null || lessonScreen(l.id()) == app.screen())
                .findFirst().map(l -> {
            Theme t = Theme.get();
            Card c = new Card(null).stripe(t.accent).fill(Theme.mix(t.surface, t.accent, 0.08)).stroke(Theme.mix(t.border, t.accent, 0.4));
            ScreenId where = lessonScreen(l.id());
            El buttons = Layouts.wrap(t.u(2), t.u(1));
            buttons.add(Btn.secondary("Got it", e -> { app.session.lessonSeen(l.id()); app.refresh(); }).tip("Hide this tip; it will not come back"));
            if (where != null && where != app.screen()) buttons.add(Btn.secondary("Show me", e -> { app.session.lessonSeen(l.id()); app.open(where); }).tip("Open " + where.label));
            buttons.add(Btn.quiet("Turn tips off", e -> { app.session.settings.tips = false; app.settingsChanged(); }).tip("You can turn them back on in Settings"));
            c.with(Layouts.stack(t.u(1), new Text("TIP", t.bold(Theme.Size.CAPTION), t.accent), Text.body(l.prompt()),
                    Text.of("Try: " + l.suggestion(), Theme.Face.UI_BOLD, Theme.Size.SMALL, t.text)), buttons);
            c.label("Tip: " + l.prompt() + " Try: " + l.suggestion());
            return c;
        });
    }

    static ScreenId lessonScreen(String id) {
        if (id.startsWith("campaign.")) return ScreenId.CAMPAIGN;
        if (id.equals("days.whip")) return ScreenId.CONGRESS;
        if (id.equals("days.crisis")) return ScreenId.SECURITY;
        if (id.equals("days.economy")) return ScreenId.ECONOMY;
        if (id.equals("days.funding")) return ScreenId.CONGRESS;
        if (id.startsWith("days.") || id.startsWith("transition.")) return ScreenId.OVAL;
        return null;
    }

    static boolean delegatedAll(HundredDays h) { return h.pending().stream().allMatch(d -> ChiefOfStaff.handles(d.kind(), h.delegated())); }
}
