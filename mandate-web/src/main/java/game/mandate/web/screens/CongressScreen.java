package game.mandate.web.screens;

import game.mandate.core.data.Fmt;

import game.mandate.web.kit.El;

import game.mandate.core.command.Command;
import game.mandate.core.congress.Bill;
import game.mandate.core.congress.LegislativeStrategy;
import game.mandate.core.congress.WhipCount;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.opening.HundredDays;
import game.mandate.core.opening.OpeningAction;
import game.mandate.core.opening.Types.BillSize;
import game.mandate.core.opening.Types.BillStage;
import game.mandate.core.opening.Types.Concession;
import game.mandate.core.opening.Types.PayFor;
import game.mandate.core.opening.WhipRead;
import game.mandate.core.time.Phase;
import game.mandate.web.App;
import game.mandate.web.Glossary;
import game.mandate.web.kit.Btn;
import game.mandate.web.kit.Card;
import game.mandate.web.kit.Choice;
import game.mandate.web.kit.Layouts;
import game.mandate.web.kit.Segmented;
import game.mandate.web.kit.Table;
import game.mandate.web.kit.Text;
import game.mandate.web.kit.Theme;
import game.mandate.web.kit.Widgets;

import java.util.List;

/**
 * Congress and legislation. In the first hundred days: the flagship's path, its design (size and pay-for, with what
 * each choice does to its position and deficit), the vote and its odds, and the whip count bloc by bloc with one
 * "work this bloc" action per row (instead of Resolute Desk's wall of identical buttons). Before the oath: the
 * Congress you face and the bills you could promise. In a full term: every bill by route.
 */
public final class CongressScreen {
    private CongressScreen() {}

    public static El build(App app) {
        GameEngine g = app.session.g();
        if (g.phase() == Phase.FIRST_100_DAYS) return hundredDays(app, g, g.hundredDays().orElseThrow());
        if (g.phase() == Phase.GOVERNING) return monthly(app, g);
        return beforeOffice(app, g);
    }

    // ---- the flagship ---------------------------------------------------------------------------------------------

    static El hundredDays(App app, GameEngine g, HundredDays h) {
        List<El> rows = new java.util.ArrayList<>();
        rows.add(Parts.row(Parts.w(flagship(app, h), 1.3), Parts.w(vote(app, h), 1)));
        if (h.stage() != BillStage.NOT_DRAFTED && h.stage() != BillStage.LAW && h.stage() != BillStage.DEAD) rows.add(whip(app, h));
        rows.add(Parts.row(Views.congressCard(g), funding(h)));
        return Parts.page("Congress", "The " + h.billName() + ": design it, work the votes, call the vote.", rows.toArray(El[]::new));
    }

    static int stageIndex(BillStage s) {
        return switch (s) { case NOT_DRAFTED -> 0; case COMMITTEE -> 1; case HOUSE_FLOOR -> 2; case SENATE_FLOOR -> 3; case LAW -> 4; case DEAD -> 1; };
    }

    static Card flagship(App app, HundredDays h) {
        Theme t = Theme.get();
        Bill b = h.flagship();
        Card c = new Card("Your flagship bill");
        c.with(Text.display(b.name(), Theme.Size.TITLE), Text.muted(b.issue().label() + ". " + (b.requires60() ? "The Byrd rule keeps it out of reconciliation." : "Eligible for reconciliation.")));
        c.with(new Widgets.Stages(List.of("Draft", "Committee", "House", "Senate", "Law"), stageIndex(h.stage()), h.stage() == BillStage.DEAD,
                h.stage() == BillStage.LAW ? t.good : t.accent));
        if (h.stage() == BillStage.LAW) { c.with(Text.strong("Signed into law in week " + h.weekSigned() + ".", Theme.Size.LEAD)); return c; }
        if (h.stage() == BillStage.DEAD) { c.with(Text.strong("The bill is dead.", Theme.Size.LEAD)); return c; }
        BillSize size = (BillSize) app.uiState.getOrDefault("bill.size", h.size().orElse(BillSize.MEDIUM));
        PayFor pay = (PayFor) app.uiState.getOrDefault("bill.payFor", h.payFor().orElse(h.congress().unified(h.party()) == (h.party() == game.mandate.core.state.Party.DEMOCRAT) ? PayFor.REVENUE : PayFor.CUTS));
        BillSize[] sizes = BillSize.values();
        c.with(Text.eyebrow("Size"), new Segmented(List.of("Small", "Medium", "Large"), List.of("Easier votes, less policy, a smaller deficit",
                "The bill as your party drafted it", "More policy, harder votes, a bigger deficit"), size.ordinal(), k -> { app.uiState.put("bill.size", sizes[k]); app.refresh(); }));
        PayFor[] pays = PayFor.values();
        c.with(Text.eyebrow("Pay for it by"), new Segmented(List.of("Borrowing", "Taxing the wealthy", "Cutting elsewhere"),
                List.of("All of the cost is borrowed: bond markets notice", "Moves the bill left; 20% of the cost borrowed", "Moves the bill right; 30% of the cost borrowed"),
                pay.ordinal(), k -> { app.uiState.put("bill.payFor", pays[k]); app.refresh(); }));
        double ideology = HundredDays.designIdeology(b, size, pay), deficit = HundredDays.designDeficit(b, size, pay);
        c.with(Parts.kpis(Parts.kpi("Position", position(ideology), Fmt.format("%+.2f on a -1 to +1 scale", ideology), null, null),
                Parts.kpi("Deficit if enacted", Fmt.format("%+.2f%%", deficit), "of GDP a year", null, "Deficit")));
        boolean same = h.size().orElse(null) == size && h.payFor().orElse(null) == pay;
        String noAttention = h.attention() < 1 ? "No attention left this week" : null;
        if (h.stage() == BillStage.NOT_DRAFTED) {
            c.with(Btn.primary("Send this bill to Congress", e -> { app.session.act(new OpeningAction.DraftBill(size, pay)); clear(app); })
                    .disabledBecause(noAttention).tip("Drafts the bill and sends it to committee (1 attention). Your party's wings react to the design."));
        } else {
            Btn amend = Btn.secondary("Amend the bill to this design", e -> { app.session.act(new OpeningAction.AmendBill(size, pay)); clear(app); })
                    .disabledBecause(same ? "This is the bill you already have" : noAttention);
            if (amend.isEnabled()) amend.tip("Rewrites the bill (1 attention); changing course costs a little credibility with both wings");
            c.with(amend, Text.faint("Now: " + h.size().orElseThrow().name().toLowerCase() + ", paid for by " + h.payFor().orElseThrow().label.toLowerCase() + "."));
        }
        return c;
    }

    static void clear(App app) { app.uiState.remove("bill.size"); app.uiState.remove("bill.payFor"); }

    static String position(double v) {
        double a = Math.abs(v);
        String side = v < 0 ? "left" : "right";
        return a < 0.15 ? "Centre" : a < 0.4 ? "Centre-" + side : a < 0.65 ? "Firmly " + side : "Far " + side;
    }

    static Card vote(App app, HundredDays h) {
        Theme t = Theme.get();
        Card c = new Card("The vote");
        c.setToolTipText(Glossary.of("Whip count"));
        BillStage s = h.stage();
        if (s == BillStage.NOT_DRAFTED) return c.with(Text.muted("Draft the bill first. It goes to committee, then the House (218 votes), then the Senate (60, or 51 in reconciliation)."));
        if (s == BillStage.LAW || s == BillStage.DEAD) return c.with(Text.muted(s == BillStage.LAW ? "Done: the bill is law." : "The bill is dead."));
        String noAttention = h.attention() < 1 ? "No attention left this week" : h.billActedThisWeek() ? "The bill has already moved this week" : null;
        if (s == BillStage.COMMITTEE) {
            c.with(Text.body(h.congress().houseMajority() == h.party() ? "Your party chairs the committee: it will report the bill."
                    : "The committee chair is from the other party; how they treat the bill depends on the opposition's mood."));
            c.with(Btn.primary("Ask the committee to report it", e -> app.session.act(new OpeningAction.CallVote(false))).disabledBecause(noAttention)
                    .tip("Moves the bill to the House floor if the committee agrees (1 attention)"));
            return c;
        }
        boolean house = s == BillStage.HOUSE_FLOOR;
        double chance = h.passChance(s);
        int needed = house ? 218 : h.reconciliation() ? 50 : 60;
        c.with(Text.eyebrow(house ? "House floor" : "Senate floor"),
                new Text(Parts.pct(chance), t.bold(Theme.Size.KPI), t.level(chance)), Text.muted(Fmt.format("chance to pass: about %.0f yes votes expected, %d needed",
                        h.expectedYes(house), needed)));
        boolean refused = chance < 0.35;
        Btn call = Btn.primary(refused ? "Insist on a vote (8 capital)" : "Call the vote", e -> app.session.act(new OpeningAction.CallVote(refused)))
                .disabledBecause(noAttention != null ? noAttention : refused && h.capital() < 8 ? "The leader refuses below 35%, and you lack the 8 capital to insist" : null);
        if (call.isEnabled()) call.tip(refused ? "The leader will not call a vote this likely to fail unless you spend 8 capital" : "Hold the vote now (1 attention). A failure costs capital and support.");
        c.with(call);
        if (!house && h.canReconcile()) c.with(Btn.secondary("Move to budget reconciliation", e -> app.session.act(new OpeningAction.UseReconciliation()))
                .tip(Glossary.of("Reconciliation")));
        Btn pub = Btn.secondary("Take the bill to the public", e -> app.session.act(new OpeningAction.GoPublic())).disabledBecause(h.attention() < 1 ? "No attention left this week" : null);
        if (pub.isEnabled()) pub.tip("Pressure on wavering members rises; opposition goodwill falls (1 attention)");
        c.with(pub);
        return c;
    }

    static Card whip(App app, HundredDays h) {
        Theme t = Theme.get();
        boolean house = h.stage() != BillStage.SENATE_FLOOR;
        boolean expert = app.session.settings.expert(), guided = app.session.settings.guided();
        Card c = new Card((house ? "House" : "Senate") + " whip count, bloc by bloc");
        c.setToolTipText(Glossary.of("Whip count"));
        Table table = guided
                ? new Table(List.of(Table.Col.left("Bloc", 2.2), Table.Col.left("Expected yes", 2.4), Table.Col.left("They want", 3), Table.Col.right("", 1.4)))
                : new Table(List.of(Table.Col.left("Bloc", 2.2), Table.Col.left("Expected yes", 2.4), Table.Col.right("Relationship", 1.5), Table.Col.left("They want", 3), Table.Col.right("", 1.4)));
        for (WhipRead w : h.whip(house)) {
            double share = w.members() == 0 ? 0 : w.expectedYes() / w.members();
            Widgets.Meter m = new Widgets.Meter(Math.round(w.expectedYes()) + " of " + w.members(), w.expectedYes(), Math.max(1, w.members()), Fmt.format("%.0f%%", 100 * share),
                    t.level(share), "Expected yes votes in this bloc if the vote were held now");
            Btn work = Btn.secondary("Work this bloc", e -> concession(app, h, w)).disabledBecause(h.attention() < 1 ? "No attention left this week" : null);
            work.label("Work the " + w.bloc());
            if (work.isEnabled()) work.tip("Offer projects, an appointment, or (your own party only) a primary threat: 1 attention");
            String rel = expert ? Fmt.format("%+.2f", w.relationship()) : w.relationship() > 0.3 ? "Warm" : w.relationship() < -0.1 ? "Cool" : "Neutral";
            String bloc = w.bloc() + " (" + w.party().code() + ")";
            if (guided) table.row(bloc, m, w.wants(), work); else table.row(bloc, m, rel, w.wants(), work);
        }
        c.with(table, Text.faint("Blocs near the middle are where a concession buys the most votes. A floor vote adds some noise: these are expectations, not promises."));
        return c;
    }

    static void concession(App app, HundredDays h, WhipRead w) {
        Theme t = Theme.get();
        boolean own = w.party() == h.party();
        Card c = new Card("Work the " + w.bloc());
        c.with(Text.display(w.bloc(), Theme.Size.TITLE), Text.muted(Fmt.format("%.0f of %d expected to vote yes. They want %s.", w.expectedYes(), w.members(), w.wants())));
        Choice projects = new Choice("1", "Projects back home", "Capital -4. Their relationship with you improves; a little spending.", h.capital() < 4 ? "Need 4 capital" : null, t.warn,
                e -> { app.stage.closeModal(); app.session.act(new OpeningAction.Negotiate(w.bloc(), Concession.PROJECT)); });
        projects.disabledBecause(h.capital() < 4 ? Fmt.format("Needs 4 political capital; you have %.0f", h.capital()) : null);
        Choice appoint = new Choice("2", "Promise an appointment", "Capital -6. A bigger gain; you have " + h.appointmentTokens() + " to promise.",
                h.appointmentTokens() < 1 ? "None left" : h.capital() < 6 ? "Need 6 capital" : null, t.warn,
                e -> { app.stage.closeModal(); app.session.act(new OpeningAction.Negotiate(w.bloc(), Concession.APPOINTMENT)); });
        appoint.disabledBecause(h.appointmentTokens() < 1 ? "You have no appointments left to promise" : h.capital() < 6 ? Fmt.format("Needs 6 political capital; you have %.0f", h.capital()) : null);
        Choice threat = new Choice("3", "Threaten a primary", "They vote with you this time, and they remember: their relationship and their wing's mood fall.",
                own ? "Risky" : "Own party only", own ? t.bad : t.faint, e -> { app.stage.closeModal(); app.session.act(new OpeningAction.Negotiate(w.bloc(), Concession.THREAT)); });
        threat.disabledBecause(own ? null : "Only your own party's members fear a primary");
        c.with(projects, appoint, threat, Text.faint("Each takes 1 attention."), Btn.quiet("Cancel", e -> app.stage.closeModal()));
        app.stage.showModal(c, t.u(150), true, null);
    }

    static Card funding(HundredDays h) {
        Theme t = Theme.get();
        Card c = new Card("Government funding");
        if (h.shutdown()) c.with(new Widgets.Tag("SHUTDOWN, WEEK " + h.shutdownWeeks(), t.bad), Text.body("The government is shut down. Each week costs approval and the economy."));
        else if (h.week() <= 8) c.with(Text.body("Funding runs out at the end of week 8. In week 6 you choose what stopgap to send."),
                Text.muted(h.fundingOffer().map(o -> "Your offer: " + o.label.toLowerCase() + ".").orElse("No offer yet.")));
        else c.with(Text.body(h.shutdownWeeks() > 0 ? "The shutdown lasted " + h.shutdownWeeks() + " week(s)." : "The government stayed open."));
        return c;
    }

    // ---- before the oath ---------------------------------------------------------------------------------------------

    static El beforeOffice(App app, GameEngine g) {
        Card bills = new Card(Parts.campaign(g) ? "Bills you could promise" : "The bills on the table");
        bills.with(Text.muted("Odds are for each bill as written by its best route today; a president can redesign a bill and work the votes."));
        Table table = new Table(List.of(Table.Col.left("Bill", 3), Table.Col.left("Issue", 1.5), Table.Col.left("Best route today", 2.2), Table.Col.right("", 1.3)));
        for (Bill b : g.bills().availableTo(g.player().party())) {
            Object action = "";
            if (g.phase() == Phase.GENERAL_CAMPAIGN_2028) {
                boolean made = g.election().pledges().contains(b.id());
                Btn p = Btn.secondary(made ? "Promised" : "Promise", e -> app.session.apply(new Command.Pledge(b.id())))
                        .disabledBecause(made ? "You have already promised this" : g.election().pledges().size() >= 3 ? "Three promises is the limit" : null);
                p.label("Promise " + b.name());
                if (p.isEnabled()) p.tip("A little support now; it becomes part of your agenda");
                action = p;
            }
            table.row(b.name(), b.issue().label(), OvalOffice.route(g, b), action);
        }
        bills.with(table);
        return Parts.page("Congress", Parts.campaign(g) ? "The Congress you would face, and what you could promise to pass." : "The Congress you will work with.",
                Parts.row(Parts.w(bills, 1.8), Parts.w(Views.congressCard(g), 1)));
    }

    // ---- a full term ------------------------------------------------------------------------------------------------

    static El monthly(App app, GameEngine g) {
        Card bills = new Card("Push a bill (2 attention)");
        Table table = new Table(List.of(Table.Col.left("Bill", 3), Table.Col.left("Best route", 2.4), Table.Col.right("", 1.2)));
        for (Bill b : g.bills().all()) {
            if (g.laws().contains(b.id())) continue;
            Btn push = Btn.secondary("Push", e -> pushDialog(app, g, b)).disabledBecause(g.attention() < 1 ? "No attention left this month" : null);
            push.label("Push " + b.name());
            if (push.isEnabled()) push.tip("Choose a route and how much capital to commit");
            table.row(b.name() + (g.agenda().contains(b.id()) ? " (agenda)" : ""), OvalOffice.route(g, b), push);
        }
        bills.with(table);
        return Parts.page("Congress", "Monthly governing: one bill a month, by party line, a bipartisan deal, reconciliation or executive order.",
                Parts.row(Parts.w(bills, 1.8), Parts.w(Views.congressCard(g), 1)));
    }

    static void pushDialog(App app, GameEngine g, Bill b) {
        Theme t = Theme.get();
        Card c = new Card("Push the " + b.name());
        int i = 1;
        for (LegislativeStrategy s : LegislativeStrategy.values()) {
            WhipCount w = g.whip(b.id(), s, 0);
            boolean order = s == LegislativeStrategy.EXECUTIVE_ORDER;
            String why = s == LegislativeStrategy.RECONCILIATION && b.requires60() ? "This bill cannot go through reconciliation: the Byrd rule keeps it out"
                    : s == LegislativeStrategy.RECONCILIATION && !g.unifiedGovernment() ? "Reconciliation needs your party to hold the House and the Senate"
                    : !order && g.attention() < 2 ? "A vote takes 2 attention; you have " + g.attention() : null;
            // An order needs no vote: its odds are the courts', and it costs 1 attention instead of 2.
            double chance = order ? g.executiveOrderSurvival() : w.passChance();
            String detail = order ? Fmt.format("No vote. A federal court blocks about %.0f%% of orders. Payoff %.0f%% of the bill; 1 attention; the opposition's goodwill falls.",
                    100 * (1 - chance), 100 * s.payoff())
                    : Fmt.format("House %.0f of %d needed, Senate %.0f of %d. Payoff %.0f%% of the bill; 2 attention.", w.houseExpected(),
                    w.houseNeeded(), w.senateExpected(), w.senateNeeded(), 100 * s.payoff());
            Choice ch = new Choice(String.valueOf(i++), s.label(), detail, why != null ? "Not available" : Parts.chance(chance).toLowerCase(),
                    why != null ? t.faint : t.level(chance), e -> { app.stage.closeModal(); app.session.apply(new Command.PushBill(b.id(), s, Math.min(10, (int) g.capital()))); });
            ch.disabledBecause(why);
            c.with(ch);
        }
        c.with(Text.faint("Commits up to 10 political capital to the whip operation."), Btn.quiet("Cancel", e -> app.stage.closeModal()));
        app.stage.showModal(c, t.u(160), true, null);
    }
}
