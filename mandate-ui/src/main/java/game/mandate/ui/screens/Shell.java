package game.mandate.ui.screens;

import game.mandate.core.advisor.Advisor;
import game.mandate.core.campaign.PollPoint;
import game.mandate.core.command.Command;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.opening.ChiefOfStaff;
import game.mandate.core.opening.HundredDays;
import game.mandate.core.opening.OpeningAction;
import game.mandate.core.time.Phase;
import game.mandate.core.tutorial.Coach;
import game.mandate.ui.App;
import game.mandate.ui.App.ScreenId;
import game.mandate.ui.Notable;
import game.mandate.ui.kit.Btn;
import game.mandate.ui.kit.Card;
import game.mandate.ui.kit.Layouts;
import game.mandate.ui.kit.Text;
import game.mandate.ui.kit.Theme;
import game.mandate.ui.kit.Ui;
import game.mandate.ui.kit.Widgets;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
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

    public static JComponent build(App app) {
        Theme t = Theme.get();
        GameEngine g = app.session.g();
        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics gr) { gr.setColor(t.bg); gr.fillRect(0, 0, getWidth(), getHeight()); }
        };
        root.add(topBar(app, g), BorderLayout.NORTH);
        root.add(rail(app, g), BorderLayout.WEST);
        boolean wide = app.stage.getWidth() == 0 || app.stage.getWidth() >= Theme.get().px(WIDE);
        JPanel centre = new JPanel(new BorderLayout());
        centre.setOpaque(false);
        JPanel above = Layouts.stack(t.u(2));
        above.setBorder(BorderFactory.createEmptyBorder(t.u(3), t.u(8), 0, t.u(8)));
        if (app.advisorOpen() && !wide) Advisor.brief(g, 1).stream().findFirst().ifPresent(a -> above.add(advisorBanner(app, a)));
        tip(app, g).ifPresent(above::add);
        if (above.getComponentCount() > 0) centre.add(above, BorderLayout.NORTH);
        centre.add(Ui.scroll(screen(app)), BorderLayout.CENTER);
        root.add(centre, BorderLayout.CENTER);
        if (app.advisorOpen() && wide) root.add(advisorPanel(app, g), BorderLayout.EAST);
        return root;
    }

    static JComponent screen(App app) {
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

    static JComponent topBar(App app, GameEngine g) {
        Theme t = Theme.get();
        JPanel bar = new JPanel(new Layouts.Side(t.u(6))) {
            @Override protected void paintComponent(Graphics gr) {
                gr.setColor(t.sunken);
                gr.fillRect(0, 0, getWidth(), getHeight());
                gr.setColor(t.border);
                gr.fillRect(0, getHeight() - 1, getWidth(), 1);
            }
        };
        bar.setBorder(BorderFactory.createEmptyBorder(t.u(3), t.u(6), t.u(3), t.u(6)));
        String who = g.player().candidate().name() + " (" + g.player().party().code() + ")";
        JPanel id = Layouts.stack(t.px(2), new Text(Notable.phase(g).toUpperCase(), t.bold(Theme.Size.CAPTION), t.accent),
                Text.strong(who, Theme.Size.LEAD), Text.muted(Notable.date(g.date())));
        id.setPreferredSize(new Dimension(t.u(60), id.getPreferredSize().height));
        bar.add(id, Layouts.Side.WEST);
        bar.add(Parts.kpis(kpis(g).toArray(JComponent[]::new)), Layouts.Side.CENTER);
        bar.add(actions(app, g), Layouts.Side.EAST);
        return bar;
    }

    static List<JComponent> kpis(GameEngine g) {
        Theme t = Theme.get();
        List<JComponent> k = new ArrayList<>();
        switch (g.phase()) {
            case GENERAL_CAMPAIGN_2028, REELECTION_CAMPAIGN_2032 -> {
                LocalDate day = g.phase() == Phase.GENERAL_CAMPAIGN_2028 ? g.calendar().election2028() : g.calendar().election2032();
                List<PollPoint> polls = g.polls();
                PollPoint p = polls.isEmpty() ? null : polls.get(polls.size() - 1);
                k.add(Parts.kpi("Cash on hand", String.format("$%.0fM", g.player().cash()), String.format("theirs about $%.0fM", g.opponent().cash()), null,
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
                k.add(new Widgets.Kpi("Attention", h.attention() + " of 3", "left this week", null, game.mandate.ui.Glossary.of("Attention")));
                k.add(Parts.kpi("Approval", String.format("%.0f%%", h.approval()), Parts.signed(h.approval() - start, "%.1f") + " since the oath",
                        Parts.change(h.approval() - start, 0.05), "Approval"));
                k.add(Parts.kpi("Political capital", String.format("%.0f", h.capital()), null, null, "Political capital"));
                k.add(Parts.kpi("Opposition", h.opposition().name().charAt(0) + h.opposition().name().substring(1).toLowerCase(), String.format("goodwill %.0f", h.goodwill()),
                        null, "Opposition strategy"));
            });
            case GOVERNING -> {
                var e = g.state().economy();
                k.add(Parts.kpi("Approval", String.format("%.0f%%", g.approval()), null, null, "Approval"));
                k.add(Parts.kpi("Political capital", String.format("%.0f", g.capital()), null, null, "Political capital"));
                k.add(Parts.kpi("Attention", g.attention() + " of 3", "this month", null, "Attention"));
                k.add(Parts.kpi("Unemployment", String.format("%.1f%%", e.unemployment()), null, null, null));
                k.add(Parts.kpi("Inflation", String.format("%.1f%%", e.inflation()), null, null, null));
            }
            default -> k.add(Parts.kpi("Game", "Finished", "see Results", null, null));
        }
        return k;
    }

    static JComponent actions(App app, GameEngine g) {
        Theme t = Theme.get();
        int cap = t.u(124);
        JPanel p = new JPanel(new Layouts.Wrap(t.u(2), t.u(2))) {
            /** As wide as its buttons up to a cap, and as tall as they need at that width. */
            @Override public Dimension getPreferredSize() {
                Layouts.Wrap w = (Layouts.Wrap) getLayout();
                int width = Math.min(cap, w.natural(this));
                return new Dimension(width, w.heightFor(this, width));
            }
        };
        p.setOpaque(false);
        int waiting = Decisions.count(app);
        if (waiting > 0) p.add(Btn.danger("Decisions (" + waiting + ")", e -> Decisions.open(app)).hint("Ctrl+D")
                .tip("Decisions waiting for you. The turn cannot end until they are answered."));
        p.add(Btn.secondary("Undo", e -> app.session.undo()).disabledBecause(app.session.canUndo() ? null : "Nothing to undo yet")
                .tip(app.session.canUndo() ? app.session.undoLabel() + " (Ctrl+Z). " + game.mandate.ui.Glossary.of("Undo and rewind") : "Nothing to undo yet"));
        if (app.session.settings.advisor)
            p.add(Btn.secondary("Advisor", e -> app.toggleAdvisor()).selected(app.advisorOpen()).tip((app.advisorOpen() ? "Hide" : "Show") + " your chief adviser's brief (F2)"));
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
            // the final two weeks run a day at a time, and the button says so
            case GENERAL_CAMPAIGN_2028, REELECTION_CAMPAIGN_2032 -> g.clock().mode() == game.mandate.core.time.SimulationClock.Granularity.DAILY ? "Run the day" : "Run the week";
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
        if (Decisions.count(app) > 0) return "Answer the decisions waiting for you first (Ctrl+D).";
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
        c.with(Btn.secondary("Save and load", e -> app.open(ScreenId.SAVES)).hint("Ctrl+S"),
                Btn.secondary("Settings", e -> app.open(ScreenId.SETTINGS)).hint("Ctrl+,"),
                Btn.secondary("Help and shortcuts", e -> Help.open(app)).hint("F1"),
                Btn.secondary("Return to the main menu", e -> app.showMenu()).tip("Your game is saved automatically every turn; Continue resumes it."),
                Btn.quiet("Close", e -> app.stage.closeModal()));
        app.stage.showModal(c, t.u(100), true, null);
    }

    // ---- navigation rail ----------------------------------------------------------------------------------------

    static JComponent rail(App app, GameEngine g) {
        Theme t = Theme.get();
        JPanel rail = new JPanel(new Layouts.Stack(t.u(1))) {
            @Override protected void paintComponent(Graphics gr) {
                gr.setColor(t.sunken);
                gr.fillRect(0, 0, getWidth(), getHeight());
                gr.setColor(t.border);
                gr.fillRect(getWidth() - 1, 0, 1, getHeight());
            }
        };
        rail.setBorder(BorderFactory.createEmptyBorder(t.u(4), t.u(3), t.u(4), t.u(3)));
        rail.setPreferredSize(new Dimension(t.u(52), 10));
        for (ScreenId s : ScreenId.values()) {
            if (s == ScreenId.SAVES) rail.add(Ui.gap(t.u(4)));
            Btn b = navButton(app, s, g);
            rail.add(b);
        }
        return rail;
    }

    static Btn navButton(App app, ScreenId s, GameEngine g) {
        Btn b = new Btn(s.label, app.screen() == s ? Btn.Kind.SECONDARY : Btn.Kind.QUIET, e -> app.open(s)) {
            @Override public Dimension getPreferredSize() { Dimension d = super.getPreferredSize(); return new Dimension(Theme.get().u(40), d.height); }
        };
        b.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        b.selected(app.screen() == s).hint(s.keyLabel()).tip(s.purpose + " (" + s.keyLabel() + ")");
        if (s == ScreenId.RESULTS && g.result2028().isEmpty()) b.disabledBecause("Results appear on election night");
        if (s == ScreenId.OVAL && Parts.campaign(g) && g.phase() == Phase.GENERAL_CAMPAIGN_2028) b.disabledBecause("The Oval Office is yours after Inauguration Day");
        return b;
    }

    // ---- the advisor ---------------------------------------------------------------------------------------------

    static JComponent advisorPanel(App app, GameEngine g) {
        Theme t = Theme.get();
        JPanel p = new JPanel(new Layouts.Stack(t.u(3)));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(t.u(6), t.u(4), t.u(6), t.u(4)));
        p.add(Text.eyebrow("Your chief adviser"));
        p.add(Text.faint("A summary of what your screens show. It sees what you see, no more."));
        List<Advisor.Advice> brief = Advisor.brief(g, app.session.settings.guided() ? 4 : 6);
        if (brief.isEmpty()) p.add(Text.muted(g.phase() == game.mandate.core.time.Phase.EPILOGUE || g.phase() == game.mandate.core.time.Phase.GAME_OVER
                ? "The game is over. The report explains each result; Play it differently replays from a point you choose."
                : "Nothing pressing. A quiet moment is a good time to plan."));
        for (Advisor.Advice a : brief) p.add(adviceCard(app, a, true));
        // The panel's surface fills the whole column, however short the brief.
        JPanel wrap = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics gr) {
                gr.setColor(t.sunken);
                gr.fillRect(0, 0, getWidth(), getHeight());
                gr.setColor(t.border);
                gr.fillRect(0, 0, 1, getHeight());
            }
        };
        wrap.setOpaque(true);
        wrap.add(Ui.scroll(p), BorderLayout.CENTER);
        wrap.setPreferredSize(new Dimension(t.u(76), 10));
        return wrap;
    }

    static Card adviceCard(App app, Advisor.Advice a, boolean full) {
        Theme t = Theme.get();
        Card c = new Card(null).stripe(Parts.tone(a.tone())).fill(t.surface);
        c.with(Layouts.stack(t.px(2), new Text(Parts.toneWord(a.tone()).toUpperCase(), t.bold(Theme.Size.CAPTION), Parts.tone(a.tone())),
                Text.strong(a.headline(), Theme.Size.BODY)));
        if (full) c.with(Text.muted(a.detail()), Text.faint("Based on: " + a.basis()));
        ScreenId target = Parts.screenFor(a.topic(), app.session.g());
        if (target != app.screen()) c.with(Btn.quiet("Go to " + target.label, e -> app.open(target)).tip("Open " + target.label + " (" + target.keyLabel() + ")"));
        c.getAccessibleContext().setAccessibleName(Parts.toneWord(a.tone()) + ": " + a.headline() + ". " + a.detail());
        return c;
    }

    static JComponent advisorBanner(App app, Advisor.Advice a) {
        Theme t = Theme.get();
        Card c = new Card(null).stripe(Parts.tone(a.tone()));
        c.with(Ui.border(Layouts.stack(t.px(2), Text.strong("Adviser: " + a.headline(), Theme.Size.BODY), Text.muted(a.detail()).lines(2)),
                Btn.quiet("Full brief", e -> Help.advisorBrief(app)).tip("The adviser's full brief (F2 toggles the panel on wide windows)")));
        return c;
    }

    // ---- the coach -------------------------------------------------------------------------------------------------

    static Optional<JComponent> tip(App app, GameEngine g) {
        if (!app.session.settings.tips) return Optional.empty();
        // A tip shows on the screen it is about, and on the overview; elsewhere it waits, so it does not follow the player around.
        return Coach.available(g, app.session.seenLessons()).stream()
                .filter(l -> app.screen() == ScreenId.OVERVIEW || lessonScreen(l.id()) == null || lessonScreen(l.id()) == app.screen())
                .findFirst().map(l -> {
            Theme t = Theme.get();
            Card c = new Card(null).stripe(t.accent).fill(Theme.mix(t.surface, t.accent, 0.08)).stroke(Theme.mix(t.border, t.accent, 0.4));
            ScreenId where = lessonScreen(l.id());
            JPanel buttons = Layouts.wrap(t.u(2), t.u(1));
            buttons.add(Btn.secondary("Got it", e -> { app.session.lessonSeen(l.id()); app.refresh(); }).tip("Hide this tip; it will not come back"));
            if (where != null && where != app.screen()) buttons.add(Btn.secondary("Show me", e -> { app.session.lessonSeen(l.id()); app.open(where); }).tip("Open " + where.label));
            buttons.add(Btn.quiet("Turn tips off", e -> { app.session.settings.tips = false; app.settingsChanged(); }).tip("You can turn them back on in Settings"));
            c.with(Layouts.stack(t.u(1), new Text("TIP", t.bold(Theme.Size.CAPTION), t.accent), Text.body(l.prompt()),
                    Text.of("Try: " + l.suggestion(), Theme.Face.UI_BOLD, Theme.Size.SMALL, t.text)), buttons);
            c.getAccessibleContext().setAccessibleName("Tip: " + l.prompt() + " Try: " + l.suggestion());
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
