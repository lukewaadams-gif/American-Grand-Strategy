package game.mandate.ui.screens;

import game.mandate.core.ai.CampaignAi;
import game.mandate.core.campaign.CampaignSide;
import game.mandate.core.campaign.CampaignStrategy;
import game.mandate.core.campaign.CampaignStrategy.Orders;
import game.mandate.core.campaign.CampaignStrategy.SpendRate;
import game.mandate.core.campaign.CampaignWeekPlan;
import game.mandate.core.campaign.Issue;
import game.mandate.core.campaign.PollPoint;
import game.mandate.core.command.Command;
import game.mandate.core.congress.Bill;
import game.mandate.core.electorate.CellElectorateModel;
import game.mandate.core.electorate.UsState;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.time.Phase;
import game.mandate.ui.App;
import game.mandate.ui.Glossary;
import game.mandate.ui.kit.Btn;
import game.mandate.ui.kit.Card;
import game.mandate.ui.kit.Choice;
import game.mandate.ui.kit.Layouts;
import game.mandate.ui.kit.Segmented;
import game.mandate.ui.kit.Table;
import game.mandate.ui.kit.Text;
import game.mandate.ui.kit.Theme;
import game.mandate.ui.kit.Widgets;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Election headquarters. The map and the race to 270 say where you stand; standing orders run the campaign in a
 * few decisions; "plan this week by hand" gives full control for one week; promises, the opponent's stated plan and
 * what moved the race explain the trend. Battleground tables and voter-group crosstabs are there for those who want
 * them (hidden in Guided mode).
 */
public final class CampaignHQ {
    private CampaignHQ() {}

    /** A week planned by hand, before it is committed. */
    static final class Draft {
        final List<String> rallies = new ArrayList<>();
        int money = -1, adShare = 60;
        Issue message;
    }

    static Draft draft(App app) { return (Draft) app.uiState.computeIfAbsent("hq.draft", k -> new Draft()); }

    public static JComponent build(App app) {
        GameEngine g = app.session.g();
        if (!Parts.campaign(g)) return afterwards(app, g);
        Theme t = Theme.get();
        String selected = (String) app.uiState.getOrDefault("hq.state", g.polls().isEmpty() ? "PA" : g.polls().get(g.polls().size() - 1).tippingPoint());
        Map<String, Double> polls = g.statePolls();
        Card map = new Card("State polls this week (click a state, or use the arrow keys)");
        map.with(Views.map(g, t.u(88), selected, code -> { app.uiState.put("hq.state", code); app.refresh(); }), stateDetail(app, g, selected, polls));
        JComponent[] rows = {
                Parts.row(Parts.w(map, 1.5), Parts.w(race(g, polls), 1)),
                Parts.row(Parts.w(orders(app, g), 1.25), Parts.w(story(g), 1)),
                Parts.row(Parts.w(handPlan(app, g, selected), 1.25), Parts.w(promises(app, g), 1))};
        List<JComponent> all = new ArrayList<>(List.of(rows));
        if (!app.session.settings.guided()) all.add(Parts.row(Parts.w(battlegrounds(g, polls), 1.25), Parts.w(groups(g), 1)));
        return Parts.page("Campaign headquarters", "Run the campaign from standing orders, or take the wheel for a week.", all.toArray(JComponent[]::new));
    }

    static JComponent afterwards(App app, GameEngine g) {
        Card c = Parts.empty("Campaign HQ", g.result2028().isPresent()
                ? "The 2028 campaign is over. " + Views.resultLine(g.result2028().get()) + " The final polls, the result and what moved them are in Results and the Timeline."
                : "There is no campaign under way.");
        c.with(Btn.secondary("Open Results", e -> app.open(App.ScreenId.RESULTS)).disabledBecause(g.result2028().isEmpty() ? "No election yet" : null));
        return Parts.page("Campaign headquarters", "Between campaigns.", c);
    }

    // ---- the race ----------------------------------------------------------------------------------------------

    static Card race(GameEngine g, Map<String, Double> polls) {
        Theme t = Theme.get();
        Card c = new Card("The race for 270");
        c.with(Views.raceBar(g, polls));
        List<PollPoint> ps = g.polls();
        if (!ps.isEmpty()) {
            PollPoint p = ps.get(ps.size() - 1);
            c.with(Parts.kpis(Parts.kpi("You, nationally", Parts.signed(p.national(), "%.1f"), "this week's polls", null, "Polls"),
                    Parts.kpi("Tipping point", p.tippingPoint(), Parts.margin(polls.getOrDefault(p.tippingPoint(), 0.0)), null, "Tipping point"),
                    Parts.kpi("Forecast", Parts.pct(p.winChance()), "polls only", null, "Forecast")));
        }
        c.with(Layouts.fill(Views.trend(g, t.u(44))));
        return c;
    }

    static JComponent stateDetail(App app, GameEngine g, String code, Map<String, Double> polls) {
        Theme t = Theme.get();
        UsState s = g.states().get(code);
        CampaignSide me = g.player(), them = g.opponent();
        double mine = polls.getOrDefault(code, 0.0) * me.party().sign();
        Draft d = draft(app);
        boolean inPlan = d.rallies.contains(code);
        Btn rally = Btn.secondary(inPlan ? "Remove from this week's rallies" : "Add a rally here this week", e -> {
            if (inPlan) d.rallies.remove(code); else d.rallies.add(code);
            app.refresh();
        }).disabledBecause(!inPlan && d.rallies.size() >= CampaignWeekPlan.STOPS ? "Three stops a week: remove one first" : null);
        if (rally.isEnabled()) rally.tip("Adds to the hand-built plan below; commit it there");
        return Layouts.stack(t.u(2), Text.strong(s.name() + "  ·  " + s.electoralVotes() + " electoral votes", Theme.Size.LEAD),
                Text.muted(String.format("Poll: you %s.  Your ads %.0f, field %.0f.  Theirs: ads %.0f, field %.0f.", Parts.signed(mine, "%.1f"),
                        me.ads(code), me.field(code), them.ads(code), them.field(code))), rally);
    }

    // ---- standing orders -----------------------------------------------------------------------------------------

    static Card orders(App app, GameEngine g) {
        Theme t = Theme.get();
        Orders now = g.election().playerOrders();
        Card c = new Card("Standing orders: how your staff runs every week");
        c.setToolTipText(Glossary.of("Standing orders"));
        int i = 1;
        for (CampaignStrategy s : CampaignStrategy.values()) {
            c.with(new Choice(String.valueOf(i++), s.label, s.description, s == now.strategy() ? "Current" : null, t.info,
                    e -> setOrders(app, new Orders(s, now.rate(), now.message()))).selected(s == now.strategy()));
        }
        SpendRate[] rates = SpendRate.values();
        c.with(Text.eyebrow("Spending each week"), new Segmented(List.of("Careful (15%)", "Steady (30%)", "All in (60%)"),
                List.of("Spend 15% of the war chest a week", "Spend 30% a week", "Spend 60% a week: for the final stretch"), now.rate().ordinal(),
                k -> setOrders(app, new Orders(now.strategy(), rates[k], now.message()))));
        List<String> labels = new ArrayList<>(List.of("Staff picks weekly"));
        List<String> tips = new ArrayList<>(List.of("The staff drives the most favourable issue each week"));
        Issue[] is = Issue.values();
        for (Issue x : is) {
            labels.add(x.label());
            tips.add(String.format("Voters trust your party %+.0f on this; salience %.2f. Staying on one message builds a bonus.", x.advantageFor(g.player().party()), g.salience().get(x)));
        }
        c.with(Text.eyebrow("Message"), new Segmented(labels, tips, now.message() == null ? 0 : now.message().ordinal() + 1,
                k -> setOrders(app, new Orders(now.strategy(), now.rate(), k == 0 ? null : is[k - 1]))));
        return c;
    }

    static void setOrders(App app, Orders o) {
        if (app.session.apply(new Command.SetCampaignOrders(o))) app.stage.toast("Orders changed", o.describe() + ". They take effect when you run the week.", Theme.get().info);
    }

    // ---- what moved, the opponent -------------------------------------------------------------------------------------

    static Card story(GameEngine g) {
        Theme t = Theme.get();
        Card c = new Card("What moved the race");
        List<PollPoint> ps = g.polls();
        if (ps.size() < 2) c.with(Text.muted("Run the first week to see what moves the polls."));
        else for (String s : ps.get(ps.size() - 1).movers()) c.with(Text.body("• " + s));
        c.with(Text.eyebrow("Your opponent"));
        String reason = g.election().opponentReasoning();
        c.with(Text.strong(g.opponent().candidate().name() + ": " + g.election().opponentOrders().strategy().label, Theme.Size.BODY));
        c.with(Text.muted(reason.isEmpty() ? "Their plan becomes clear after the first week." : reason));
        if (!g.election().opponentPledges().isEmpty())
            c.with(Text.muted("They promise: " + String.join(", ", g.election().opponentPledges().stream().map(id -> g.bill(id).name()).toList()) + "."));
        if (g.debateThisWeek().isPresent()) c.with(new Widgets.Tag("DEBATE THIS WEEK", t.warn));
        return c;
    }

    // ---- promises ----------------------------------------------------------------------------------------------

    static Card promises(App app, GameEngine g) {
        Theme t = Theme.get();
        Card c = new Card("Your promises");
        c.setToolTipText(Glossary.of("Promises"));
        List<String> made = g.election().pledges();
        if (made.isEmpty()) c.with(Text.muted("None yet. Your first promise becomes your flagship bill if you win."));
        for (int i = 0; i < made.size(); i++) c.with(Text.body((i == 0 ? "Flagship: " : "Promise: ") + g.bill(made.get(i)).name()));
        if (g.phase() != Phase.GENERAL_CAMPAIGN_2028) { c.with(Text.faint("Promises are made in the 2028 campaign.")); return c; }
        Table table = new Table(List.of(Table.Col.left("Bill", 2.6), Table.Col.left("Issue", 1.4), Table.Col.right("Appeal", 1.1), Table.Col.right("", 1.5)));
        for (Bill b : g.bills().availableTo(g.player().party())) {
            if (made.contains(b.id())) continue;
            Btn p = Btn.secondary("Promise", e -> app.session.apply(new Command.Pledge(b.id())))
                    .disabledBecause(made.size() >= 3 ? "Three promises is the limit: more would not be believed" : null);
            if (p.isEnabled()) p.tip("Promise to pass the " + b.name() + ": a little support now, a commitment later");
            p.getAccessibleContext().setAccessibleName("Promise " + b.name());
            table.row(b.name() + (b.bipartisan() ? " (bipartisan)" : ""), b.issue().label(), Parts.signed(b.publicAppeal(), "%.0f"), p);
        }
        c.with(table);
        return c;
    }

    // ---- this week by hand -----------------------------------------------------------------------------------------

    static Card handPlan(App app, GameEngine g, String selected) {
        Theme t = Theme.get();
        Draft d = draft(app);
        CampaignWeekPlan staff = CampaignAi.plan(g, g.player(), 1.0, g.election().playerOrders());
        int staffMoney = (int) Math.round(staff.totalSpend());
        if (d.money < 0) d.money = staffMoney;
        if (d.message == null) d.message = staff.message();
        Card c = new Card("Plan this week by hand (optional)");
        c.with(Text.muted("Your staff would rally in " + (staff.rallyStates().isEmpty() ? "no state" : String.join(", ", staff.rallyStates())) + ", hold "
                + staff.fundraisers() + " fundraiser" + (staff.fundraisers() == 1 ? "" : "s") + " and spend $" + staffMoney + "M. Take over for one week:"));
        c.with(Text.eyebrow("Rallies (pick states on the map)"), Text.body(d.rallies.isEmpty() ? "None: three fundraisers instead" : String.join(", ", d.rallies)
                + (d.rallies.size() < 3 ? " + " + (3 - d.rallies.size()) + " fundraiser" + (3 - d.rallies.size() == 1 ? "" : "s") : "")));
        int cash = (int) Math.max(0, g.player().cash());
        List<Integer> amounts = new ArrayList<>(List.of(0, Math.min(cash, staffMoney / 2), Math.min(cash, staffMoney), Math.min(cash, staffMoney * 2), cash));
        List<String> labels = amounts.stream().map(a -> "$" + a + "M").toList();
        int idx = amounts.indexOf(d.money);
        c.with(Text.eyebrow("Money this week"), new Segmented(labels, List.of("Spend nothing: bank it", "Half the staff's plan", "The staff's amount", "Twice the staff's plan",
                "Everything in the bank"), Math.max(0, idx), k -> { d.money = amounts.get(k); app.refresh(); }));
        List<Integer> shares = List.of(25, 50, 75, 100);
        c.with(Text.eyebrow("Ads or field"), new Segmented(List.of("25% ads", "50% ads", "75% ads", "All ads"),
                List.of("Mostly field: slow, lasting", "Half and half", "Mostly ads: fast, fading", "Only ads"), Math.max(0, shares.indexOf(d.adShare)),
                k -> { d.adShare = shares.get(k); app.refresh(); }));
        Btn commit = Btn.primary("Use this plan this week", e -> {
            double scale = staff.totalSpend() <= 0 ? 0 : d.money / staff.totalSpend();
            Map<String, Double> ads = new LinkedHashMap<>(), field = new LinkedHashMap<>();
            for (String st : staff.adSpend().keySet()) {
                double total = (staff.adSpend().get(st) + staff.fieldSpend().getOrDefault(st, 0.0)) * scale;
                ads.put(st, total * d.adShare / 100.0);
                field.put(st, total * (100 - d.adShare) / 100.0);
            }
            if (app.session.apply(new Command.PlanCampaign(new CampaignWeekPlan(List.copyOf(d.rallies), CampaignWeekPlan.STOPS - d.rallies.size(), ads, field, d.message))))
                app.stage.toast("This week is planned", "Your plan replaces the staff's for one week; standing orders resume after.", t.info);
        }).tip("Replaces the staff's plan for this week only; run the week to carry it out");
        Btn reset = Btn.quiet("Reset to the staff's plan", e -> { app.uiState.remove("hq.draft"); app.refresh(); });
        c.with(Layouts.wrap(t.u(2), t.u(1), commit, reset));
        if (g.election().pendingPlan() != null) c.with(new Widgets.Tag("A HAND-BUILT PLAN IS SET FOR THIS WEEK", t.good));
        return c;
    }

    // ---- detail tables -----------------------------------------------------------------------------------------------

    static Card battlegrounds(GameEngine g, Map<String, Double> polls) {
        Card c = new Card("Battlegrounds (within 8 points)");
        int sign = g.player().party().sign();
        Table table = new Table(List.of(Table.Col.left("State", 2), Table.Col.right("EV", 0.6), Table.Col.right("Poll (you)", 1), Table.Col.right("Your ads / field", 1.5),
                Table.Col.right("Theirs", 1.5)));
        List<UsState> ss = new ArrayList<>(g.states().all());
        ss.removeIf(s -> Math.abs(polls.getOrDefault(s.code(), 99.0)) > 8);
        ss.sort((a, b) -> Integer.compare(b.electoralVotes(), a.electoralVotes()));
        for (UsState s : ss)
            table.row(s.name(), String.valueOf(s.electoralVotes()), Parts.signed(polls.get(s.code()) * sign, "%.1f"),
                    String.format("%.0f / %.0f", g.player().ads(s.code()), g.player().field(s.code())),
                    String.format("%.0f / %.0f", g.opponent().ads(s.code()), g.opponent().field(s.code())));
        c.with(table, Text.faint("Ads and field are accumulated effort; ads fade within weeks, field lasts."));
        return c;
    }

    static Card groups(GameEngine g) {
        Card c = new Card("Voter groups (your margin, this week's poll)");
        var x = g.crosstabs();
        if (x.isEmpty()) return c.with(Text.muted("Crosstabs need the cell electorate."));
        int sign = g.player().party().sign();
        Table table = new Table(List.of(Table.Col.left("Group", 3), Table.Col.right("Share", 1), Table.Col.right("You", 1)));
        for (CellElectorateModel.GroupMargin m : x.get().bySex()) table.row(m.label(), String.format("%.0f%%", 100 * m.share()), Parts.signed(m.margin() * sign, "%.0f"));
        for (CellElectorateModel.GroupMargin m : x.get().byAge()) table.row(m.label(), String.format("%.0f%%", 100 * m.share()), Parts.signed(m.margin() * sign, "%.0f"));
        for (CellElectorateModel.GroupMargin m : x.get().byBloc()) table.row(m.label(), String.format("%.0f%%", 100 * m.share()), Parts.signed(m.margin() * sign, "%.0f"));
        c.with(table, Text.faint("Groups from Battleground 2028's 96-cell electorate; the same noise as the state polls."));
        return c;
    }
}
