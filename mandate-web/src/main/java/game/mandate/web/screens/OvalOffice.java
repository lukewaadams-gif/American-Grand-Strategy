package game.mandate.web.screens;

import game.mandate.core.data.Fmt;

import game.mandate.web.kit.El;

import game.mandate.core.campaign.Issue;
import game.mandate.core.command.Command;
import game.mandate.core.congress.Bill;
import game.mandate.core.congress.LegislativeStrategy;
import game.mandate.core.diplomacy.HotSpot;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.executive.GovernmentState.SeatStatus;
import game.mandate.core.executive.NomineeType;
import game.mandate.core.executive.Post;
import game.mandate.core.opening.Briefing;
import game.mandate.core.opening.HundredDays;
import game.mandate.core.opening.OpeningAction;
import game.mandate.core.opening.Types.Domain;
import game.mandate.core.time.Phase;
import game.mandate.web.App;
import game.mandate.web.Glossary;
import game.mandate.web.kit.Btn;
import game.mandate.web.kit.Card;
import game.mandate.web.kit.Choice;
import game.mandate.web.kit.Layouts;
import game.mandate.web.kit.Paper;
import game.mandate.web.kit.Text;
import game.mandate.web.kit.Theme;
import game.mandate.web.kit.Widgets;

import game.mandate.web.kit.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The Oval Office: the president's desk. In the transition it is where the agenda is set; in the first hundred days
 * it holds the week (attention, decisions, the brief), the cabinet, the party, delegation and the actions that are not
 * about the flagship bill; in a full term, the month's actions.
 */
public final class OvalOffice {
    private OvalOffice() {}

    public static El build(App app) {
        GameEngine g = app.session.g();
        return switch (g.phase()) {
            case TRANSITION -> transition(app, g);
            case FIRST_100_DAYS -> hundredDays(app, g, g.hundredDays().orElseThrow());
            case GOVERNING -> monthly(app, g);
            default -> Parts.page("The Oval Office", "Not yet yours.", Parts.empty("The Oval Office",
                    g.over() ? "The game is over: see Results." : "The Oval Office is yours after Inauguration Day."));
        };
    }

    // ---- the transition: the agenda --------------------------------------------------------------------------------

    static El transition(App app, GameEngine g) {
        Theme t = Theme.get();
        @SuppressWarnings("unchecked")
        List<String> picked = (List<String>) app.uiState.computeIfAbsent("oval.agenda", k -> new ArrayList<>(g.election().pledges()));
        Card choose = new Card("Choose up to three priorities: the first is your flagship bill");
        choose.with(Text.muted("Odds are for each bill as written, by its best route. In office you will resize it, decide how to pay for it and work the votes. "
                + "Promises left off cost your base wing at the inauguration."));
        int i = 1;
        for (Bill b : g.bills().availableTo(g.player().party())) {
            int at = picked.indexOf(b.id());
            boolean promised = g.election().pledges().contains(b.id());
            String tag = at == 0 ? "Flagship" : at > 0 ? "Priority " + (at + 1) : promised ? "Promised" : null;
            Color tagColor = at >= 0 ? t.accent : t.warn;
            choose.with(new Choice(i <= 9 ? String.valueOf(i) : null, b.name(), Fmt.format("%s. %s. %s", b.issue().label(), route(g, b),
                    b.bipartisan() ? "A bipartisan bill" : "Your party's bill"), tag, tagColor, e -> {
                if (picked.contains(b.id())) picked.remove(b.id()); else if (picked.size() < 3) picked.add(b.id());
                else app.stage.toast("Three priorities at most", "Remove one first: click it again.", t.warn);
                app.refresh();
            }).selected(at >= 0));
            i++;
        }
        Card side = new Card("Your agenda");
        if (picked.isEmpty()) side.with(Text.muted("Nothing chosen yet."));
        for (int k = 0; k < picked.size(); k++) side.with(Text.body((k == 0 ? "Flagship: " : (k + 1) + ". ") + g.bill(picked.get(k)).name()));
        List<String> broken = g.election().pledges().stream().filter(p -> !picked.contains(p)).toList();
        if (!broken.isEmpty()) side.with(new Text("Leaves out a promise: " + String.join(", ", broken.stream().map(id -> g.bill(id).name()).toList())
                + ". Your base wing will notice.", t.bold(Theme.Size.SMALL), t.warn));
        side.with(Btn.primary("Set this agenda and take the oath", e -> {
            if (app.session.apply(new Command.SetAgenda(List.copyOf(picked)))) Shell.endTurn(app);
        }).disabledBecause(picked.isEmpty() ? "Choose at least one priority" : null).tip("Inauguration Day: the first hundred days begin"));
        side.with(Views.congressCard(g));
        return Parts.page("The transition", "Seventy-three days to set an agenda. You won " + g.result2028().orElseThrow().electoralVotes(g.player().party())
                + " electoral votes.", Parts.row(Parts.w(choose, 1.6), Parts.w(side, 1)));
    }

    static String route(GameEngine g, Bill b) {
        List<LegislativeStrategy> rs = new ArrayList<>(List.of(LegislativeStrategy.PARTY_LINE, LegislativeStrategy.BIPARTISAN_DEAL));
        if (g.unifiedGovernment() && !b.requires60()) rs.add(LegislativeStrategy.RECONCILIATION);
        LegislativeStrategy best = rs.stream().max((a, c) -> Double.compare(g.whip(b.id(), a, 0).passChance(), g.whip(b.id(), c, 0).passChance())).orElseThrow();
        return Parts.chance(g.whip(b.id(), best, 0).passChance()) + " as a " + best.label().toLowerCase();
    }

    // ---- the first hundred days ----------------------------------------------------------------------------------------

    static El hundredDays(App app, GameEngine g, HundredDays h) {
        return Parts.page("The Oval Office", "Week " + h.week() + " of " + h.weeks() + ". Three attention points a week; most actions take one.",
                Parts.row(Parts.w(week(app, h), 1), Parts.w(brief(h), 1.3)),
                Parts.row(Parts.w(cabinet(app, h), 1.3), Parts.w(party(h), 1)),
                Parts.row(Parts.w(delegation(app, h), 1.3), Parts.w(otherActions(app, g, h), 1)));
    }

    static Card week(App app, HundredDays h) {
        Theme t = Theme.get();
        Card c = new Card("This week");
        c.with(Layouts.wrap(t.u(3), t.u(1), new Widgets.Pips(h.attention(), 3, Glossary.of("Attention")), Text.body(h.attention() + " of 3 attention points left")));
        int waiting = Decisions.count(app);
        if (waiting > 0) c.with(Btn.danger(waiting + " decision" + (waiting == 1 ? "" : "s") + " waiting", e -> Decisions.open(app)).hint("D")
                .tip("The week cannot end until these are answered"));
        List<String> now = Parts.thisWeek(h);
        if (now.isEmpty()) c.with(Text.muted("Nothing done yet this week."));
        else {
            c.with(Text.eyebrow("Done so far"));
            for (String s : now) c.with(Text.body("• " + s));
        }
        c.with(Text.faint("The week ends with Ctrl+Enter or the gold button: hearings and votes happen, the economy and opinion move, and the brief arrives."));
        return c;
    }

    /** The weekly brief as a paper document (Resolute Desk's daily brief). */
    static El brief(HundredDays h) {
        Theme t = Theme.get();
        List<Briefing> bs = h.briefings();
        if (bs.isEmpty()) {
            Paper p = new Paper("For the President", "The first week", "Your first briefing arrives when the week ends");
            p.item("Nominate a Treasury secretary, a Defense secretary and a Secretary of State: each takes two weeks.", t.info);
            p.item("Draft the " + h.billName() + ": its size and how you pay for it set its odds in Congress.", t.info);
            return p;
        }
        Briefing b = bs.get(bs.size() - 1);
        Paper p = new Paper("For the President", "Week " + b.week() + " briefing", b.headline());
        for (String s : b.consequences()) p.item(s, s.contains("rejects") || s.contains("lapses") || s.contains("fails") ? t.paperRule : t.warn);
        for (String s : b.reactions()) p.item(s, t.paperRule);
        for (String s : b.intelligence()) p.item(s, Theme.rgb(0x3A5F9E));
        p.note("Prepared by the White House staff from public figures and the intelligence community's estimates.");
        return p;
    }

    static Card cabinet(App app, HundredDays h) {
        Theme t = Theme.get();
        Card c = new Card("The cabinet");
        for (Post p : Post.values()) {
            SeatStatus s = h.seat(p);
            String status = switch (s) {
                case VACANT -> "Vacant: an acting official (competence 45) runs the department";
                case NOMINATED -> h.nomineeName(p) + ": hearings next week";
                case HEARINGS_DONE -> h.nomineeName(p) + Fmt.format(": Senate vote due, about %.0f votes (50 needed)", h.confirmationExpected(p));
                case CONFIRMED -> h.nomineeName(p) + Fmt.format(": confirmed, competence %.0f", h.competence(p));
                case ACTING -> "Rejected: an acting official is in charge";
            };
            Color tone = switch (s) { case CONFIRMED -> t.good; case VACANT, ACTING -> t.warn; default -> t.info; };
            String word = switch (s) { case CONFIRMED -> "CONFIRMED"; case VACANT -> "VACANT"; case ACTING -> "ACTING"; case NOMINATED -> "NOMINATED"; case HEARINGS_DONE -> "VOTE DUE"; };
            Btn nominate = Btn.secondary("Nominate", e -> nominee(app, h, p));
            nominate.label("Nominate a " + HundredDays.postName(p));
            nominate.disabledBecause(s == SeatStatus.VACANT || s == SeatStatus.ACTING ? (h.attention() < 1 ? "No attention left this week" : null) : "This post already has a nominee or a secretary");
            if (nominate.isEnabled()) nominate.tip("Choose a loyalist, an expert or a crossover (1 attention)");
            c.with(Ui2.line(Layouts.stack(t.px(2), Layouts.wrap(t.u(2), 0, Text.strong(HundredDays.postName(p), Theme.Size.BODY), new Widgets.Tag(word, tone)),
                    Text.muted(status), Text.faint(postRole(p))), nominate));
        }
        return c;
    }

    static String postRole(Post p) {
        return switch (p) {
            case TREASURY -> "Treasury's competence steadies bond markets and how well your bill is carried out.";
            case DEFENSE -> "Defense's competence makes force more effective in a crisis.";
            case STATE -> "State's competence sharpens intelligence and makes diplomacy work.";
        };
    }

    static void nominee(App app, HundredDays h, Post p) {
        Theme t = Theme.get();
        Card c = new Card("Nominate a " + HundredDays.postName(p));
        c.with(Text.display("Who will you send the Senate?", Theme.Size.TITLE));
        c.with(new Choice("1", "A loyalist", "Competence 55, loyal to you, controversial in the Senate. Your base wing approves; the moderates and the opposition do not.",
                "Harder vote", t.warn, e -> { app.stage.closeModal(); app.session.act(new OpeningAction.Nominate(p, NomineeType.LOYALIST)); }));
        c.with(new Choice("2", "An expert", "Competence 85, confirms readily. The moderate wing and the opposition approve.", "Recommended", t.good,
                e -> { app.stage.closeModal(); app.session.act(new OpeningAction.Nominate(p, NomineeType.EXPERT)); }));
        c.with(new Choice("3", "A crossover from the other party", "Competence 72, easy votes and opposition goodwill (+8), less loyal: may leak if overruled. Your base wing dislikes it.",
                "Easy vote", t.info, e -> { app.stage.closeModal(); app.session.act(new OpeningAction.Nominate(p, NomineeType.CROSSOVER)); }));
        c.with(Text.faint("Hearings come next week, the Senate vote the week after. A rejected nominee leaves an acting official in charge."),
                Btn.quiet("Cancel", e -> app.stage.closeModal()));
        app.stage.showModal(c, t.u(160), true, null);
    }

    static Card party(HundredDays h) {
        Theme t = Theme.get();
        Card c = new Card("Your party and the opposition");
        c.setToolTipText(Glossary.of("Party wings"));
        for (int w = 0; w < 2; w++) {
            double m = h.wingMood(w);
            c.with(new Widgets.Meter(h.wingName(w), m, 100, Fmt.format("%.0f", m), m < 32 ? t.bad : m < 45 ? t.warn : t.good,
                    "Mood of the " + h.wingName(w) + ". Below 32 (the tick) they revolt.").marker(32));
        }
        c.with(Text.eyebrow("The opposition"), Text.body(h.opposition().name().charAt(0) + h.opposition().name().substring(1).toLowerCase()
                + Fmt.format(", goodwill %.0f", h.goodwill())), Text.faint(Glossary.of("Opposition strategy")));
        return c;
    }

    static Card delegation(App app, HundredDays h) {
        Theme t = Theme.get();
        Card c = new Card("Delegate to your chief of staff");
        c.with(Text.muted("A delegated area costs no attention: the staff answers its decisions and works it for you, at half strength, never threatening "
                + "or going public, always nominating experts and holding in a crisis."));
        for (Domain d : Domain.values()) {
            boolean on = h.delegated().contains(d);
            String what = switch (d) {
                case LEGISLATION -> "Legislation: drafts and moves the bill, offers projects";
                case CABINET -> "Cabinet: nominates experts to empty posts";
                case CRISIS -> "Crisis: answers crisis decisions cautiously";
                case BUDGET -> "Budget and economy: the stopgap, the shutdown, the economic challenge";
            };
            Btn b = (on ? Btn.secondary("Take it back", e -> app.session.act(new OpeningAction.Delegate(d, false)))
                    : Btn.secondary("Hand it over", e -> app.session.act(new OpeningAction.Delegate(d, true)))).selected(on);
            b.label((on ? "Take back " : "Delegate ") + d.name().toLowerCase());
            b.tip(on ? "You will handle this yourself again" : "The chief of staff handles this from now on");
            c.with(Ui2.line(Layouts.stack(t.px(2), Text.body(what), new Widgets.Tag(on ? "DELEGATED" : "YOU", on ? t.info : t.faint)), b));
        }
        return c;
    }

    static Card otherActions(App app, GameEngine g, HundredDays h) {
        Theme t = Theme.get();
        Card c = new Card("Other actions");
        String noAttention = h.attention() < 1 ? "No attention left this week" : null;
        Btn meet = Btn.secondary("Meet opposition leaders", e -> app.session.act(new OpeningAction.MeetOpposition())).disabledBecause(noAttention);
        if (meet.isEnabled()) meet.tip("Goodwill +5; your base wing notices (1 attention)");
        Btn fed = Btn.secondary("Lean on the Fed", e -> app.session.act(new OpeningAction.LeanOnFed()))
                .disabledBecause(noAttention != null ? noAttention : g.state().economy().fedPressured() ? "You have already leaned on the Fed before this meeting" : null);
        if (fed.isEnabled()) fed.tip("Call publicly for rate cuts: your base cheers, the Fed's credibility falls (1 attention)");
        Btn oil = Btn.secondary("Release oil from the reserve", e -> app.session.act(new OpeningAction.ReleaseOilReserve()))
                .disabledBecause(noAttention != null ? noAttention : h.oil() <= 80 ? Fmt.format("Oil is $%.0f: a release needs it above $80", h.oil()) : null);
        if (oil.isEnabled()) oil.tip("Gas prices ease; deterrence -2 (1 attention)");
        c.with(meet, Text.faint("Goodwill +5; your base wing notices."), fed, Text.faint("Your base cheers; bond traders worry."), oil,
                Text.faint(Fmt.format("Oil is $%.0f a barrel.", h.oil())));
        c.with(Btn.quiet("The flagship bill is in Congress", e -> app.open(App.ScreenId.CONGRESS)).hint("4").tip("Draft, negotiate and vote"));
        return c;
    }

    // ---- monthly governing (a full term) ---------------------------------------------------------------------------------

    static El monthly(App app, GameEngine g) {
        Theme t = Theme.get();
        Card act = new Card("This month: " + g.attention() + " of 3 attention points");
        String none = g.attention() < 1 ? "No attention left this month" : null;
        Issue[] is = Issue.values();
        Card address = new Card("Address the nation (1 attention)");
        for (Issue i : is) {
            Btn b = Btn.secondary(i.label(), e -> app.session.apply(new Command.PublicAddress(i))).disabledBecause(none);
            if (b.isEnabled()) b.tip(Fmt.format("Voters trust your party %+.0f on this issue", i.advantageFor(g.player().party())));
            address.with(b);
        }
        Btn deter = Btn.secondary("Bolster Pacific deterrence", e -> app.session.apply(new Command.BolsterDeterrence())).disabledBecause(none);
        if (deter.isEnabled()) deter.tip("More ships, exercises and munitions: deterrence up, a little deficit (1 attention)");
        act.with(deter);
        for (HotSpot h : HotSpot.values()) {
            Btn b = Btn.secondary("Diplomacy: " + h.label(), e -> app.session.apply(new Command.Diplomacy(h))).disabledBecause(none);
            if (b.isEnabled()) b.tip(Fmt.format("Personal diplomacy; tension now %.0f (1 attention)", g.diplomacy().tension(h)));
            act.with(b);
        }
        act.with(Btn.quiet("Push a bill in Congress", e -> app.open(App.ScreenId.CONGRESS)).hint("4"));
        Card agenda = new Card("Your agenda");
        for (String id : g.agenda()) agenda.with(Text.body((g.laws().contains(id) ? "Law: " : "Unfinished: ") + g.bill(id).name()));
        Card cab = new Card("The cabinet");
        for (Post p : Post.values()) {
            var seat = g.state().government().seat(p);
            cab.with(Text.body(HundredDays.postName(p) + ": " + (seat.name() == null ? "vacant" : seat.name()) + " (" + seat.status().name().toLowerCase().replace('_', ' ') + ")"));
        }
        return Parts.page("The Oval Office", "Monthly governing. Pushing a bill takes 2 attention; everything else 1.",
                Parts.row(Parts.w(act, 1), Parts.w(address, 1)), Parts.row(agenda, cab));
    }

    /** A content block on the left, a control on the right. */
    static final class Ui2 {
        static El line(El left, El right) { return game.mandate.web.kit.Ui.border(left, right); }
    }

    static boolean has(GameEngine g, Phase p) { return g.phase() == p; }
}
