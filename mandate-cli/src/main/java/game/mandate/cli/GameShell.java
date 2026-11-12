package game.mandate.cli;

import game.mandate.core.ai.CampaignAi;
import game.mandate.core.campaign.CampaignStrategy;
import game.mandate.core.campaign.CampaignStrategy.Orders;
import game.mandate.core.campaign.CampaignStrategy.SpendRate;
import game.mandate.core.campaign.CampaignWeekPlan;
import game.mandate.core.campaign.Candidate;
import game.mandate.core.campaign.CandidateCatalog;
import game.mandate.core.campaign.DebatePosture;
import game.mandate.core.campaign.Issue;
import game.mandate.core.campaign.PollPoint;
import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.congress.Bill;
import game.mandate.core.congress.LegislativeStrategy;
import game.mandate.core.congress.WhipCount;
import game.mandate.core.data.Json;
import game.mandate.core.diplomacy.HotSpot;
import game.mandate.core.electorate.CellElectorateModel;
import game.mandate.core.electorate.ElectionResult;
import game.mandate.core.electorate.UsState;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.engine.SaveManager;
import game.mandate.core.events.EventOption;
import game.mandate.core.events.PendingEvent;
import game.mandate.core.executive.NomineeType;
import game.mandate.core.executive.Post;
import game.mandate.core.game.GameSetup;
import game.mandate.core.game.Scenario;
import game.mandate.core.opening.Briefing;
import game.mandate.core.opening.ChiefOfStaff;
import game.mandate.core.opening.DayHundredReport;
import game.mandate.core.opening.Decision;
import game.mandate.core.opening.HundredDays;
import game.mandate.core.opening.OpeningAction;
import game.mandate.core.opening.Types.BillSize;
import game.mandate.core.opening.Types.BillStage;
import game.mandate.core.opening.Types.Concession;
import game.mandate.core.opening.Types.Domain;
import game.mandate.core.opening.Types.PayFor;
import game.mandate.core.opening.WhipRead;
import game.mandate.core.state.Party;
import game.mandate.core.time.Phase;
import game.mandate.core.tutorial.Coach;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The game: one continuous session from choosing a candidate to the 100-day report, in the terminal.
 * It drives the core only through commands and read-only views, autosaves the whole game (and the tutorial's
 * progress) to one file after every turn, and offers a replay at the end.
 */
final class GameShell {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private final Console c;
    private final Path saveFile;
    private final long fixedSeed;
    private GameEngine g;
    private boolean tutorial = true;
    private final Set<String> seen = new TreeSet<>();
    private int logShown, daysLogShown, briefingsShown;
    private boolean electionShown, reportShown, toMenu;

    /** @param fixedSeed the seed for new games, or a negative number for a fresh one each time */
    GameShell(BufferedReader in, PrintStream out, Path saveFile, long fixedSeed) {
        this.c = new Console(in, out);
        this.saveFile = saveFile;
        this.fixedSeed = fixedSeed;
    }

    void run() {
        try {
            title();
        } catch (Console.Closed closed) {
            if (g != null && !g.over()) autosave();
            c.out.println();
            c.out.println("Saved to " + saveFile + ". Goodbye.");
        }
    }

    // =============================================================================================
    //  Title, setup, continue
    // =============================================================================================

    private void title() {
        while (true) {
            c.rule("MANDATE");
            c.wrap("", "Win the 2028 presidential election, take the oath, and govern your first hundred days: appoint a cabinet, "
                    + "pass a flagship bill, answer an economic shock and face a foreign crisis. Every choice has consequences you will see.");
            List<String> options = new ArrayList<>();
            String saved = describeSave();
            if (saved != null) options.add("Continue: " + saved);
            options.add("New game: the 2028 election and the first hundred days");
            options.add("New game: the full term, through re-election in 2032");
            options.add("Quit");
            int k = c.menu("", options, 0) - (saved == null ? -1 : 0);
            switch (k) {
                case 0 -> resume();
                case 1 -> newGame(GameSetup.Length.HUNDRED_DAYS);
                case 2 -> newGame(GameSetup.Length.FULL_TERM);
                default -> { c.out.println("Goodbye."); return; }
            }
            play();
        }
    }

    private String describeSave() {
        if (!Files.exists(saveFile)) return null;
        try {
            GameEngine s = SaveManager.load(Files.readString(saveFile, StandardCharsets.UTF_8));
            if (s.over()) return null;     // a finished game offers its replays on its last screen, not here
            return s.player().candidate().name() + " (" + s.player().party().code() + "), " + phaseLabel(s) + ", " + DATE.format(s.date());
        } catch (RuntimeException | java.io.IOException e) {
            return null;
        }
    }

    private void resume() {
        try {
            String text = Files.readString(saveFile, StandardCharsets.UTF_8);
            g = SaveManager.load(text);
            Map<String, Object> ui = SaveManager.ui(text);
            tutorial = !Boolean.FALSE.equals(ui.get("tutorial"));
            seen.clear();
            if (ui.get("seen") != null) for (Object o : Json.array(ui.get("seen"))) seen.add((String) o);
        } catch (java.io.IOException e) { throw new UncheckedIOException(e); }
        logShown = Math.max(0, g.log().size() - 3);
        daysLogShown = g.hundredDays().map(h -> h.log().size()).orElse(0);
        briefingsShown = g.hundredDays().map(h -> Math.max(0, h.briefings().size() - 1)).orElse(0);
        electionShown = g.result2028().isPresent();
        reportShown = false;
        c.out.println("Welcome back.");
    }

    private void newGame(GameSetup.Length length) {
        long seed = fixedSeed >= 0 ? fixedSeed : (System.nanoTime() & 0xFFFFFFL);
        c.rule("A NEW CAMPAIGN");
        Party party = c.menu("Your party:", List.of("Democratic", "Republican"), 0) == 0 ? Party.DEMOCRAT : Party.REPUBLICAN;
        CandidateCatalog fictional = CandidateCatalog.load();
        List<Candidate> cast = fictional.forParty(party);
        List<String> options = new ArrayList<>();
        for (Candidate x : cast) options.add(describe(x));
        options.add("Create your own candidate");
        options.add("Play a real politician (Battleground 2028's roster, opt-in)");
        int pick = c.menu("Your candidate:", options, 0);
        Candidate me, custom = null;
        boolean real = false;
        if (pick < cast.size()) me = cast.get(pick);
        else if (pick == cast.size()) { custom = createCandidate(party); me = custom; }
        else {
            real = true;
            c.wrap("  ", "Real people, rated as in Battleground 2028. The game never invents personal scandals about them, and this roster is not part of a commercial release.");
            List<Candidate> roster = CandidateCatalog.load(true).forParty(party).stream().filter(Candidate::isReal).toList();
            List<String> names = roster.stream().map(GameShell::describe).toList();
            me = roster.get(c.menu("Choose:", names, 0));
        }
        CandidateCatalog pool = CandidateCatalog.load(real);
        List<Candidate> rivals = real ? pool.forParty(party.opponent()).stream().filter(Candidate::isReal).toList() : pool.fictional(party.opponent());
        Candidate suggested = rivals.get((int) Math.floorMod(seed, rivals.size()));
        List<String> rivalOptions = new ArrayList<>();
        rivalOptions.add("Let the game choose (" + suggested.name() + ")");
        for (Candidate x : rivals) rivalOptions.add(describe(x));
        int r = c.menu("Your opponent:", rivalOptions, 0);
        Candidate opponent = r == 0 ? suggested : rivals.get(r - 1);
        Scenario[] sc = Scenario.values();
        List<String> scen = new ArrayList<>();
        for (Scenario s : sc) scen.add(s.label() + ": " + s.description());
        Scenario scenario = sc[c.menu("The national mood:", scen, 0)];
        GameSetup.Difficulty[] ds = GameSetup.Difficulty.values();
        GameSetup.Difficulty difficulty = ds[c.menu("Difficulty (your opponent's resources):", List.of("Story (80%)", "Standard (100%)", "Hard (120%)"), 1)];
        tutorial = c.menu("Coaching tips as you play:", List.of("On (recommended for a first game)", "Off"), 0) == 0;
        seen.clear();
        GameSetup setup = new GameSetup(seed, party, me.id(), opponent.id(), scenario, difficulty, length, real, custom);
        g = new GameEngine(setup);
        logShown = 0;
        daysLogShown = 0;
        briefingsShown = 0;
        electionShown = false;
        reportShown = false;
        c.out.println();
        c.wrap("", String.format("Game %d. You are %s (%s-%s), against %s (%s-%s). Election Day is %s.", seed, me.name(), party.code(), me.homeState(),
                opponent.name(), opponent.party().code(), opponent.homeState(), DATE.format(g.calendar().election2028())));
        autosave();
    }

    private Candidate createCandidate(Party party) {
        c.out.println("  Build your candidate: " + Candidate.POINT_BUDGET + " points across five ratings, each " + Candidate.MIN_RATING + " to " + Candidate.MAX_RATING + ".");
        String name = c.text("Name", party == Party.DEMOCRAT ? "Alex Morgan" : "Jordan Hale");
        String home = c.text("Home state (two letters)", "PA").toUpperCase(Locale.ROOT);
        if (!g0States().contains(home)) { c.out.println("  Unknown state; using PA."); home = "PA"; }
        while (true) {
            int left = Candidate.POINT_BUDGET;
            int cha = c.number("Charisma (rallies, momentum)", Candidate.MIN_RATING, Candidate.MAX_RATING, 7);
            int deb = c.number("Debating", Candidate.MIN_RATING, Candidate.MAX_RATING, 7);
            int fun = c.number("Fundraising", Candidate.MIN_RATING, Candidate.MAX_RATING, 6);
            int pol = c.number("Policy (diplomacy, governing)", Candidate.MIN_RATING, Candidate.MAX_RATING, 7);
            int dis = c.number("Discipline (fewer gaffes)", Candidate.MIN_RATING, Candidate.MAX_RATING, 6);
            left -= cha + deb + fun + pol + dis;
            if (left < 0) { c.out.println("  That is " + (-left) + " points over budget. Try again."); continue; }
            int s = -party.sign();
            double ideology = s * switch (c.menu("Where you stand:", List.of(party == Party.DEMOCRAT ? "Progressive" : "Conservative", "Mainstream", "Moderate"), 1)) {
                case 0 -> 0.5; case 1 -> 0.3; default -> 0.1;
            };
            return Candidate.custom(party, name, home, cha, deb, fun, pol, dis, ideology);
        }
    }

    private static List<String> g0States() {
        return game.mandate.core.electorate.StateCatalog.load().all().stream().map(UsState::code).toList();
    }

    // =============================================================================================
    //  The game loop
    // =============================================================================================

    private void play() {
        toMenu = false;
        while (!g.over() && !toMenu) {
            showNewLog();
            try {
                step();
            } catch (IllegalCommandException e) {
                c.out.println("  ! " + e.getMessage());
            }
        }
        if (toMenu) { autosave(); return; }
        showNewLog();
        if (g.result2028().isPresent() && !electionShown) electionNight();
        ending();
    }

    private void step() {
        if (g.result2028().isPresent() && !electionShown) { electionNight(); return; }
        if (!g.pendingEvents().isEmpty()) { events(); return; }
        switch (g.phase()) {
            case GENERAL_CAMPAIGN_2028, REELECTION_CAMPAIGN_2032 -> campaignWeek();
            case TRANSITION -> transition();
            case FIRST_100_DAYS -> hundredDaysWeek();
            case GOVERNING -> {
                if (!reportShown && g.dayHundredReport().isPresent()) { report(g.dayHundredReport().get()); reportShown = true; c.pause(); }
                governingMonth();
            }
            default -> g.apply(new Command.EndTurn());
        }
    }

    private void endTurn() {
        g.apply(new Command.EndTurn());
        autosave();
    }

    private void showNewLog() {
        List<String> log = g.log();
        for (; logShown < log.size(); logShown++) {
            String line = log.get(logShown);
            String body = line.length() > 12 ? line.substring(12) : line;
            // During the hundred days the briefing tells the story; the log repeats it.
            if (body.startsWith("Week ") || body.startsWith("Decision: ") || body.startsWith("Your opponent's campaign") || body.startsWith("Day 100: ")
                    || body.startsWith("The first hundred days are over")) continue;
            c.wrap("  ", body);
        }
    }

    private void coach() {
        if (!tutorial) return;
        Coach.next(g, seen).ifPresent(l -> {
            seen.add(l.id());
            c.out.println();
            c.wrap("  TIP  ", l.prompt());
            c.wrap("       Try: ", l.suggestion());
        });
    }

    // =============================================================================================
    //  Events
    // =============================================================================================

    private void events() {
        PendingEvent p = g.pendingEvents().get(0);
        c.rule("EVENT: " + p.definition().title());
        c.wrap("", p.definition().body());
        coach();
        List<String> options = new ArrayList<>();
        for (EventOption o : p.definition().options()) {
            StringBuilder b = new StringBuilder(o.label());
            if (o.successChance() < 1) b.append(String.format(" [%d%% chance]", Math.round(o.successChance() * 100)));
            if (!o.effects().isEmpty()) b.append(" -> ").append(String.join("; ", o.effects().stream().map(e -> e.describe()).toList()));
            if (o.successChance() < 1 && !o.failureEffects().isEmpty())
                b.append("; if it fails: ").append(String.join("; ", o.failureEffects().stream().map(e -> e.describe()).toList()));
            options.add(b.toString());
        }
        g.apply(new Command.ResolveEvent(p.instanceId(), c.menu("Your decision:", options, 0)));
    }

    // =============================================================================================
    //  The campaign
    // =============================================================================================

    private void campaignWeek() {
        LocalDate electionDay = g.phase() == Phase.GENERAL_CAMPAIGN_2028 ? g.calendar().election2028() : g.calendar().election2032();
        long weeks = Math.max(0, ChronoUnit.WEEKS.between(g.date(), electionDay));
        c.rule("CAMPAIGN HQ   " + DATE.format(g.date()) + "   " + weeks + " week" + (weeks == 1 ? "" : "s") + " to Election Day");
        Candidate me = g.player().candidate(), them = g.opponent().candidate();
        c.out.printf("  %s (%s) vs %s (%s)   cash $%.0fM (theirs about $%.0fM)%n", me.name(), me.party().code(), them.name(), them.party().code(),
                g.player().cash(), g.opponent().cash());
        c.out.println("  Your orders: " + g.election().playerOrders().describe());
        List<PollPoint> polls = g.polls();
        if (!polls.isEmpty()) {
            PollPoint p = polls.get(polls.size() - 1);
            c.out.printf("  Polls: you %+.1f nationally, %d electoral votes, %.0f%% to win (polls only), tipping point %s%n", p.national(), p.electoralVotes(),
                    100 * p.winChance(), p.tippingPoint());
            List<Double> trend = polls.subList(Math.max(0, polls.size() - 10), polls.size()).stream().map(PollPoint::national).toList();
            if (trend.size() > 1) c.out.printf("  Trend:  %+.1f %s %+.1f%n", trend.get(0), Console.spark(trend), trend.get(trend.size() - 1));
            if (polls.size() > 1) {
                c.out.println("  What moved the race:");
                for (String m : p.movers().subList(0, Math.min(3, p.movers().size()))) c.wrap("    - ", m);
            }
        }
        if (!g.election().opponentReasoning().isEmpty()) c.wrap("  Your opponent: ", g.election().opponentOrders().describe() + ". " + g.election().opponentReasoning());
        if (!g.election().pledges().isEmpty()) c.wrap("  Your promises: ", String.join(", ", g.election().pledges().stream().map(id -> g.bill(id).name()).toList()));
        coach();
        if (g.debateThisWeek().isPresent() && g.election().pendingPosture() == null) debate();
        boolean promises = g.phase() == Phase.GENERAL_CAMPAIGN_2028 && g.election().pledges().size() < 3;
        List<String> options = new ArrayList<>(List.of("Run the week", "Change your standing orders", "Plan this week by hand", "Battleground polls and voter groups"));
        if (promises) options.add("Make a campaign promise (" + (3 - g.election().pledges().size()) + " left)");
        options.add("Save and return to the menu");
        String choice = options.get(c.menu("", options, 0));
        switch (choice) {
            case "Run the week" -> endTurn();
            case "Change your standing orders" -> orders();
            case "Plan this week by hand" -> handPlan();
            case "Battleground polls and voter groups" -> battlegrounds();
            case "Save and return to the menu" -> toMenu = true;
            default -> promise();
        }
    }

    private void debate() {
        Candidate them = g.opponent().candidate();
        c.out.println();
        c.wrap("  DEBATE this week. ", "Prep notes: your opponent tends to \"" + DebatePosture.tendencyOf(them).label().toLowerCase()
                + "\". A posture that counters theirs scores extra.");
        DebatePosture[] ps = DebatePosture.values();
        List<String> options = new ArrayList<>();
        for (DebatePosture p : ps) options.add(p.label() + (p.counters(DebatePosture.tendencyOf(them)) ? "  (counters their style)" : "")
                + String.format("  your rating %d", p.rating(g.player().candidate())));
        int def = 0;
        for (int i = 0; i < ps.length; i++) if (ps[i] == DebatePosture.tendencyOf(g.player().candidate())) def = i;
        g.apply(new Command.ChooseDebatePosture(ps[c.menu("Your approach:", options, def)]));
    }

    private void orders() {
        CampaignStrategy[] ss = CampaignStrategy.values();
        List<String> so = new ArrayList<>();
        for (CampaignStrategy s : ss) so.add(s.label + ": " + s.description);
        Orders now = g.election().playerOrders();
        CampaignStrategy s = ss[c.menu("Strategy:", so, now.strategy().ordinal())];
        SpendRate[] rs = SpendRate.values();
        SpendRate rate = rs[c.menu("Spending:", List.of("Careful: 15% of the war chest a week", "Steady: 30% a week", "All in: 60% a week"), now.rate().ordinal())];
        List<String> mo = new ArrayList<>();
        mo.add("Let the staff pick the best issue each week");
        Issue[] is = Issue.values();
        for (Issue i : is) mo.add(String.format("%s (voters trust your party %+.0f; salience %.2f)", i.label(), i.advantageFor(g.player().party()), g.salience().get(i)));
        int m = c.menu("Message:", mo, now.message() == null ? 0 : now.message().ordinal() + 1);
        g.apply(new Command.SetCampaignOrders(new Orders(s, rate, m == 0 ? null : is[m - 1])));
        c.out.println("  Orders: " + g.election().playerOrders().describe() + ".");
    }

    private void handPlan() {
        CampaignWeekPlan staff = CampaignAi.plan(g, g.player(), 1.0, g.election().playerOrders());
        c.out.println("  Your staff's plan: rallies in " + String.join(", ", staff.rallyStates()) + ", " + staff.fundraisers() + " fundraiser(s), "
                + String.format("$%.0fM on ads and field, message %s.", staff.totalSpend(), staff.message().label().toLowerCase()));
        String r = c.text("Rally states, up to 3, separated by spaces", String.join(" ", staff.rallyStates())).toUpperCase(Locale.ROOT);
        List<String> rallies = new ArrayList<>();
        List<String> known = g0States();
        for (String x : r.split("\\s+")) if (known.contains(x) && rallies.size() < 3) rallies.add(x);
        int fundraisers = CampaignWeekPlan.STOPS - rallies.size();
        int spend = c.number("Money to spend this week, $M", 0, (int) Math.max(0, g.player().cash()), (int) Math.round(staff.totalSpend()));
        int adShare = c.number("Share on ads (the rest goes to field), %", 0, 100, 60);
        double scale = staff.totalSpend() <= 0 ? 0 : spend / staff.totalSpend();
        Map<String, Double> ads = new LinkedHashMap<>(), field = new LinkedHashMap<>();
        for (String st : staff.adSpend().keySet()) {
            double total = (staff.adSpend().get(st) + staff.fieldSpend().getOrDefault(st, 0.0)) * scale;
            ads.put(st, total * adShare / 100.0);
            field.put(st, total * (100 - adShare) / 100.0);
        }
        Issue[] is = Issue.values();
        List<String> mo = new ArrayList<>();
        for (Issue i : is) mo.add(String.format("%s (trust %+.0f)", i.label(), i.advantageFor(g.player().party())));
        Issue message = is[c.menu("Message:", mo, staff.message().ordinal())];
        g.apply(new Command.PlanCampaign(new CampaignWeekPlan(rallies, fundraisers, ads, field, message)));
        c.out.printf("  This week, by hand: rallies %s, %d fundraiser(s), $%dM (%d%% ads), on %s. Your standing orders resume next week.%n",
                rallies.isEmpty() ? "none" : String.join(" ", rallies), fundraisers, spend, adShare, message.label().toLowerCase());
    }

    private void promise() {
        List<Bill> bills = new ArrayList<>(g.bills().availableTo(g.player().party()));
        bills.removeIf(b -> g.election().pledges().contains(b.id()));
        List<String> options = new ArrayList<>();
        for (Bill b : bills) options.add(String.format("%s (%s; public appeal %+.0f; %s)", b.name(), b.issue().label().toLowerCase(), b.publicAppeal(),
                b.bipartisan() ? "bipartisan" : "your party's bill"));
        options.add("Not now");
        int k = c.menu("Promise to pass (your first promise becomes your flagship bill if you win):", options, options.size() - 1);
        if (k < bills.size()) g.apply(new Command.Pledge(bills.get(k).id()));
    }

    private void battlegrounds() {
        Map<String, Double> polls = g.statePolls();
        int sign = g.player().party().sign();
        List<UsState> close = new ArrayList<>(g.states().all());
        close.removeIf(s -> Math.abs(polls.get(s.code())) > 8);
        close.sort(Comparator.comparingInt(UsState::electoralVotes).reversed());
        c.out.println("  State   EV   poll (you)   your ads / field     their ads / field");
        for (UsState s : close)
            c.out.printf("  %-5s %4d   %+6.1f      %6.0f / %-6.0f      %6.0f / %-6.0f%n", s.code(), s.electoralVotes(), polls.get(s.code()) * sign,
                    g.player().ads(s.code()), g.player().field(s.code()), g.opponent().ads(s.code()), g.opponent().field(s.code()));
        g.crosstabs().ifPresent(x -> {
            c.out.println("  Voter groups (your margin):");
            StringBuilder b = new StringBuilder();
            for (CellElectorateModel.GroupMargin m : x.byBloc()) b.append(String.format("%s %+.0f; ", m.label(), m.margin() * sign));
            c.wrap("    ", b.toString());
            StringBuilder a = new StringBuilder();
            for (CellElectorateModel.GroupMargin m : x.byAge()) a.append(String.format("%s %+.0f; ", m.label(), m.margin() * sign));
            for (CellElectorateModel.GroupMargin m : x.bySex()) a.append(String.format("%s %+.0f; ", m.label(), m.margin() * sign));
            c.wrap("    ", a.toString());
        });
        c.pause();
    }

    private void electionNight() {
        electionShown = true;
        ElectionResult r = g.result2028().orElseThrow();
        Party me = g.player().party();
        c.rule("ELECTION NIGHT, " + DATE.format(g.calendar().election2028()));
        c.out.printf("  %s %d, %s %d. Popular vote: %s by %.1f. Tipping point: %s.%n", Party.DEMOCRAT.adjective(), r.demElectoralVotes(),
                Party.REPUBLICAN.adjective(), r.repElectoralVotes(), r.popularMargin() >= 0 ? "Democrats" : "Republicans", Math.abs(r.popularMargin()), r.tippingPoint());
        List<UsState> close = new ArrayList<>(g.states().all());
        close.sort(Comparator.comparingDouble(s -> Math.abs(r.stateMargins().get(s.code()))));
        StringBuilder b = new StringBuilder();
        for (UsState s : close.subList(0, 10)) b.append(String.format("%s %s+%.1f  ", s.code(), r.stateMargins().get(s.code()) >= 0 ? "D" : "R", Math.abs(r.stateMargins().get(s.code()))));
        c.wrap("  Closest states: ", b.toString());
        if (r.winner() == me) c.out.println("  YOU WIN. " + g.congress() + ".");
        else {
            c.out.println("  YOU LOSE.");
            c.out.println("  What happened:");
            for (String l : g.postMortem()) c.wrap("   - ", l);
        }
        c.pause();
    }

    // =============================================================================================
    //  The transition
    // =============================================================================================

    private void transition() {
        c.rule("THE TRANSITION");
        int ev = g.result2028().orElseThrow().electoralVotes(g.player().party());
        c.wrap("  ", String.format("You won %d electoral votes. Congress: %s (%s). A bigger mandate means more political capital and early goodwill.",
                ev, g.congress(), g.unifiedGovernment() ? "your party holds both chambers" : "divided government"));
        coach();
        List<Bill> bills = g.bills().availableTo(g.player().party());
        List<String> defaults = new ArrayList<>(g.election().pledges());
        c.out.println("  The odds below are for the bill as written, by its best route; in office you can resize it, change how it is paid for and work the votes.");
        for (int i = 0; i < bills.size(); i++) {
            Bill b = bills.get(i);
            c.out.printf("  %2d. %-46s %s%s%n", i + 1, b.name(), bestRoute(b), g.election().pledges().contains(b.id()) ? "  [promised]" : "");
        }
        // With no promises, the default flagship is your own party's most passable bill: the agenda you ran on.
        if (defaults.isEmpty()) defaults.add(bills.stream().filter(b -> !b.bipartisan()).max(Comparator.comparingDouble(this::bestOdds)).orElseThrow().id());
        String def = String.join(" ", defaults.stream().map(id -> String.valueOf(indexOf(bills, id) + 1)).toList());
        String typed = c.text("Your agenda: up to three numbers; the first is your flagship", def);
        List<String> ids = new ArrayList<>();
        for (String t : typed.split("\\s+")) {
            try {
                int n = Integer.parseInt(t);
                if (n >= 1 && n <= bills.size() && ids.size() < 3 && !ids.contains(bills.get(n - 1).id())) ids.add(bills.get(n - 1).id());
            } catch (NumberFormatException ignored) { }
        }
        if (ids.isEmpty()) ids = defaults;
        g.apply(new Command.SetAgenda(ids));
        showNewLog();
        c.out.println("  Your flagship: the " + g.bill(ids.get(0)).name() + ". Inauguration Day comes next.");
        c.pause();
        endTurn();
    }

    private List<LegislativeStrategy> routes(Bill b) {
        List<LegislativeStrategy> rs = new ArrayList<>(List.of(LegislativeStrategy.PARTY_LINE, LegislativeStrategy.BIPARTISAN_DEAL));
        if (g.unifiedGovernment() && !b.requires60()) rs.add(LegislativeStrategy.RECONCILIATION);
        return rs;
    }

    private double bestOdds(Bill b) { return routes(b).stream().mapToDouble(s -> g.whip(b.id(), s, 0).passChance()).max().orElse(0); }

    private String bestRoute(Bill b) {
        LegislativeStrategy best = routes(b).stream().max(Comparator.comparingDouble(s -> g.whip(b.id(), s, 0).passChance())).orElseThrow();
        return String.format("%3.0f%% as %s", 100 * g.whip(b.id(), best, 0).passChance(), best.label().toLowerCase());
    }

    private static int indexOf(List<Bill> bills, String id) {
        for (int i = 0; i < bills.size(); i++) if (bills.get(i).id().equals(id)) return i;
        return 0;
    }

    // =============================================================================================
    //  The first hundred days
    // =============================================================================================

    private void hundredDaysWeek() {
        HundredDays h = g.hundredDays().orElseThrow();
        for (; briefingsShown < h.briefings().size(); briefingsShown++) briefing(h.briefings().get(briefingsShown));
        daysLogShown = h.log().size();
        c.rule(String.format("THE FIRST HUNDRED DAYS   Week %d of %d   %s   Attention %d", h.week(), h.weeks(), DATE.format(h.weekStart(h.week())), h.attention()));
        var rep = h.report();
        c.out.printf("  Approval %.0f%% (your voters %.0f, swing %.0f, opposition voters %.0f)   capital %.0f   opposition: %s (goodwill %.0f)%n",
                rep.approval(), rep.baseApproval(), rep.swingApproval(), rep.oppositionApproval(), h.capital(), h.opposition().name().toLowerCase(), h.goodwill());
        c.out.printf("  %s   your party: %s %.0f, %s %.0f%n", h.congress(), h.wingName(0), h.wingMood(0), h.wingName(1), h.wingMood(1));
        c.out.println("  Flagship: the " + h.billName() + " - " + billStatus(h));
        StringBuilder cab = new StringBuilder();
        for (Post p : Post.values()) cab.append(HundredDays.postName(p)).append(": ").append(seatStatus(h, p)).append(".  ");
        c.wrap("  Cabinet: ", cab.toString().strip());
        c.out.printf("  Economy: unemployment %.1f%%, inflation %.1f%%, gas $%.2f, Fed %.2f%%%s%n", h.unemployment(), h.inflation(), h.gas(), h.fedRate(),
                h.crisis() == game.mandate.core.opening.Types.CrisisOutcome.ACTIVE ? String.format("   CRISIS in the %s (deterrence %.0f)", h.crisisKind().label, h.deterrence()) : "");
        if (!h.delegated().isEmpty()) c.out.println("  Your chief of staff handles: " + h.delegated().toString().toLowerCase());
        coach();
        for (Decision d : h.pending()) {
            if (ChiefOfStaff.handles(d.kind(), h.delegated())) continue;
            decide(h, d);
            return;
        }
        List<String> options = List.of(
                h.stage() == BillStage.NOT_DRAFTED ? "Draft the " + h.billName() + "  (1 attention)" : "Amend the bill  (1)",
                "Whip count and negotiate  (1)",
                h.stage() == BillStage.COMMITTEE ? "Ask the committee to report the bill  (1)" : "Call a vote  (1)" + (h.live() && h.stage() != BillStage.COMMITTEE
                        ? String.format(", %.0f%% to pass", 100 * h.passChance(h.stage())) : ""),
                "Take the bill to the public  (1)",
                "Nominate a cabinet secretary  (1)",
                "Meet opposition leaders  (1)",
                "Lean on the Fed  (1)",
                "Release oil from the strategic reserve  (1)",
                "Delegate to your chief of staff  (free)",
                "End the week",
                "Save and return to the menu");
        int k = c.menu("", options, suggested(h));
        switch (k) {
            case 0 -> draft(h);
            case 1 -> negotiate(h);
            case 2 -> callVote(h);
            case 3 -> act(h, new OpeningAction.GoPublic());
            case 4 -> nominate(h);
            case 5 -> act(h, new OpeningAction.MeetOpposition());
            case 6 -> act(h, new OpeningAction.LeanOnFed());
            case 7 -> act(h, new OpeningAction.ReleaseOilReserve());
            case 8 -> delegate(h);
            case 9 -> { act(h, new OpeningAction.EndWeek()); autosave(); }
            default -> toMenu = true;
        }
    }

    /**
     * The menu's default: the next sensible step, chosen so that taking every default always makes progress (it spends
     * attention, changes the bill, or ends the week). Draft, then fill the cabinet, then move the bill or work the votes.
     */
    private static int suggested(HundredDays h) {
        if (h.attention() == 0) return 9;
        if (h.stage() == BillStage.NOT_DRAFTED) return 0;
        for (Post p : Post.values()) {
            var s = h.seat(p);
            if (s == game.mandate.core.executive.GovernmentState.SeatStatus.VACANT || s == game.mandate.core.executive.GovernmentState.SeatStatus.ACTING) return 4;
        }
        if (h.live() && !h.billActedThisWeek()) {
            if (h.stage() == BillStage.COMMITTEE || h.passChance(h.stage()) >= 0.5) return 2;
            if (h.stage() == BillStage.SENATE_FLOOR && h.canReconcile()) return 2;
            if (h.capital() >= 4 && ChiefOfStaff.bestTarget(h, h.stage() == BillStage.HOUSE_FLOOR) != null) return 1;
        }
        return 9;
    }

    private static String billStatus(HundredDays h) {
        if (h.stage() == BillStage.NOT_DRAFTED) return "not yet drafted";
        String design = h.size().orElseThrow().name().toLowerCase() + ", paid for by " + h.payFor().orElseThrow().label.toLowerCase()
                + (h.reconciliation() ? ", in reconciliation" : "");
        return switch (h.stage()) {
            case COMMITTEE -> "in committee (" + design + ")";
            case HOUSE_FLOOR -> "on the House floor (" + design + ")";
            case SENATE_FLOOR -> "passed the House; on the Senate floor (" + design + ")";
            case LAW -> "signed into law (" + design + ")";
            case DEAD -> "dead";
            default -> design;
        };
    }

    private static String seatStatus(HundredDays h, Post p) {
        return switch (h.seat(p)) {
            case VACANT -> "vacant";
            case NOMINATED -> h.nomineeName(p) + " (hearings next week)";
            case HEARINGS_DONE -> h.nomineeName(p) + String.format(" (Senate vote due, about %.0f votes)", h.confirmationExpected(p));
            case CONFIRMED -> h.nomineeName(p);
            case ACTING -> "an acting official";
        };
    }

    private void briefing(Briefing b) {
        c.rule("WEEK " + b.week() + " BRIEFING");
        c.wrap("  ", b.headline());
        for (String s : b.consequences()) c.wrap("   * ", s);
        for (String s : b.reactions()) c.wrap("   > ", s);
        for (String s : b.intelligence()) c.wrap("   i ", s);
    }

    /** Applies an action and shows what it did at once: the consequence, in the game's words. */
    private void act(HundredDays h, OpeningAction a) {
        try {
            h.act(a);
        } catch (IllegalCommandException e) {
            c.out.println("  ! " + e.getMessage());
            return;
        }
        List<String> log = g.hundredDays().orElseThrow().log();
        if (a instanceof OpeningAction.EndWeek) { daysLogShown = log.size(); return; }
        for (; daysLogShown < log.size(); daysLogShown++) {
            String l = log.get(daysLogShown);
            int colon = l.indexOf(": ");
            if (l.startsWith("Week ") && colon > 0) l = l.substring(colon + 2);
            c.wrap("   -> ", l);
        }
    }

    private void decide(HundredDays h, Decision d) {
        c.rule(d.title().toUpperCase(Locale.ROOT));
        c.wrap("  ", d.situation());
        List<String> options = new ArrayList<>();
        for (Decision.Option o : d.options()) options.add(o.label() + "\n        " + String.join("\n        ", Console.wrapLines(o.preview(), Console.WIDTH - 8)));
        act(h, new OpeningAction.Decide(d.id(), c.menu("", options, 0)));
    }

    private void draft(HundredDays h) {
        Bill b = h.flagship();
        c.wrap("  ", String.format("The %s (%s): as drafted by your party, about %.2f%% of GDP a year if borrowed. Bigger delivers more and is harder to pass; "
                + "how you pay for it moves the bill left or right and changes the deficit.", b.name(), b.issue().label().toLowerCase(), b.deficitPctGdp()));
        BillSize size = BillSize.values()[c.menu("Size:", List.of("Small: easier votes, less policy", "Medium", "Large: more policy, harder votes, a bigger deficit"),
                h.size().map(Enum::ordinal).orElse(1))];
        PayFor[] ps = PayFor.values();
        List<String> po = new ArrayList<>();
        for (PayFor p : ps) po.add(p.label + String.format(" (%.0f%% of the cost borrowed)", 100 * p.deficitShare));
        // Default: what your own party wants with a Congress of your own, what moves the bill to the centre without one.
        boolean unified = h.congress().unified(h.party());
        PayFor lean = (h.party() == Party.DEMOCRAT) == unified ? PayFor.REVENUE : PayFor.CUTS;
        PayFor payFor = ps[c.menu("Pay for it by:", po, h.payFor().map(Enum::ordinal).orElse(lean.ordinal()))];
        act(h, h.stage() == BillStage.NOT_DRAFTED ? new OpeningAction.DraftBill(size, payFor) : new OpeningAction.AmendBill(size, payFor));
    }

    private void negotiate(HundredDays h) {
        if (h.stage() == BillStage.NOT_DRAFTED) { c.out.println("  Draft the bill first."); return; }
        boolean house = h.stage() != BillStage.SENATE_FLOOR;
        List<WhipRead> w = h.whip(house);
        c.out.printf("  %s whip count: %.0f%% to pass if called now.%n", house ? "House" : "Senate", 100 * h.passChance(house ? BillStage.HOUSE_FLOOR : BillStage.SENATE_FLOOR));
        List<String> options = new ArrayList<>();
        for (WhipRead r : w) options.add(String.format("%-24s %s  %3.0f of %3d yes   relationship %+.2f   wants %s", r.bloc(), r.party().code(), r.expectedYes(),
                r.members(), r.relationship(), r.wants()));
        options.add("Done");
        WhipRead best = ChiefOfStaff.bestTarget(h, house);
        int def = best == null || h.capital() < 4 ? w.size() : w.indexOf(best);
        int k = c.menu("Work which bloc?", options, def);
        if (k >= w.size()) return;
        int how = c.menu("Offer:", List.of("Projects back home (capital 4)", "An appointment (capital 6, " + h.appointmentTokens() + " left)",
                "Threaten a primary challenge (your own party only)"), 0);
        act(h, new OpeningAction.Negotiate(w.get(k).bloc(), Concession.values()[how]));
    }

    private void callVote(HundredDays h) {
        if (h.stage() == BillStage.SENATE_FLOOR && h.canReconcile()
                && c.menu(String.format("Move the bill to budget reconciliation first (51 votes instead of 60, the Byrd rule strips 15%%)? Now %.0f%% to pass.",
                        100 * h.passChance(BillStage.SENATE_FLOOR)), List.of("No", "Yes"), h.passChance(BillStage.SENATE_FLOOR) < 0.5 ? 1 : 0) == 1)
            act(h, new OpeningAction.UseReconciliation());
        boolean insist = false;
        if (h.live() && h.stage() != BillStage.COMMITTEE && h.passChance(h.stage()) < 0.35)
            insist = c.menu(String.format("Only %.0f%% to pass: the leader will refuse unless you insist (8 capital).", 100 * h.passChance(h.stage())),
                    List.of("Work the votes first", "Insist"), 0) == 1;
        if (h.live() && h.stage() != BillStage.COMMITTEE && h.passChance(h.stage()) < 0.35 && !insist) return;
        act(h, new OpeningAction.CallVote(insist));
    }

    private void nominate(HundredDays h) {
        List<Post> open = new ArrayList<>();
        for (Post p : Post.values()) {
            var s = h.seat(p);
            if (s == game.mandate.core.executive.GovernmentState.SeatStatus.VACANT || s == game.mandate.core.executive.GovernmentState.SeatStatus.ACTING) open.add(p);
        }
        if (open.isEmpty()) { c.out.println("  Every post has a secretary or a nominee."); return; }
        Post p = open.get(c.menu("Which post?", open.stream().map(HundredDays::postName).toList(), 0));
        NomineeType t = NomineeType.values()[c.menu("Which kind of nominee?", List.of(
                "A loyalist: competence 55, loyal, controversial in the Senate; your base wing likes it",
                "An expert: competence 85, confirms readily",
                "A crossover from the other party: competence 72, easy votes and goodwill, less loyal; your base wing dislikes it"), 1)];
        act(h, new OpeningAction.Nominate(p, t));
    }

    private void delegate(HundredDays h) {
        Domain[] ds = Domain.values();
        List<String> options = new ArrayList<>();
        for (Domain d : ds) options.add((h.delegated().contains(d) ? "Take back " : "Hand over ") + d.name().toLowerCase());
        options.add("Done");
        c.wrap("  ", "Your chief of staff spends no attention, but works at half strength, never threatens or goes public, nominates experts and holds in a crisis.");
        int k = c.menu("", options, ds.length);
        if (k < ds.length) act(h, new OpeningAction.Delegate(ds[k], !h.delegated().contains(ds[k])));
    }

    // =============================================================================================
    //  Monthly governing (the full term)
    // =============================================================================================

    private void governingMonth() {
        var e = g.economy().orElseThrow();
        c.rule(String.format("GOVERNING   %s   attention %d", DATE.format(g.date()), g.attention()));
        c.out.printf("  Approval %.0f%%   capital %.0f   growth %.1f%%   unemployment %.1f%%   inflation %.1f%%   Taiwan tension %.0f%n",
                g.approval(), g.capital(), e.growth(), e.unemployment(), e.inflation(), g.diplomacy().tension(HotSpot.TAIWAN_STRAIT));
        c.out.println("  Laws: " + (g.laws().isEmpty() ? "none" : String.join(", ", g.laws().stream().map(id -> g.bill(id).name()).toList())));
        int k = c.menu("", List.of("Push a bill (2 attention)", "Address the nation (1)", "Bolster Pacific deterrence (1)", "Diplomacy (1)", "End the month",
                "Save and return to the menu"), 4);
        switch (k) {
            case 0 -> {
                List<Bill> open = g.bills().all().stream().filter(b -> !g.laws().contains(b.id())).toList();
                Bill b = open.get(c.menu("Which bill?", open.stream().map(x -> x.name() + (g.agenda().contains(x.id()) ? "  [agenda]" : "")).toList(), 0));
                LegislativeStrategy[] ss = LegislativeStrategy.values();
                List<String> so = new ArrayList<>();
                for (LegislativeStrategy s : ss) {
                    WhipCount w = g.whip(b.id(), s, 0);
                    so.add(String.format("%-22s House %3.0f/%d  Senate %3.0f/%d  about %3.0f%%", s.label(), w.houseExpected(), w.houseNeeded(), w.senateExpected(),
                            w.senateNeeded(), 100 * w.passChance()));
                }
                LegislativeStrategy s = ss[c.menu("How?", so, 0)];
                int capital = c.number("Political capital to commit", 0, (int) g.capital(), Math.min(10, (int) g.capital()));
                g.apply(new Command.PushBill(b.id(), s, capital));
            }
            case 1 -> {
                Issue[] is = Issue.values();
                g.apply(new Command.PublicAddress(is[c.menu("On which issue?", java.util.Arrays.stream(is).map(Issue::label).toList(), 0)]));
            }
            case 2 -> g.apply(new Command.BolsterDeterrence());
            case 3 -> {
                HotSpot[] hs = HotSpot.values();
                g.apply(new Command.Diplomacy(hs[c.menu("Where?", java.util.Arrays.stream(hs).map(x -> String.format("%s (tension %.0f)", x.label(),
                        g.diplomacy().tension(x))).toList(), 0)]));
            }
            case 4 -> endTurn();
            default -> toMenu = true;
        }
    }

    // =============================================================================================
    //  Endings and replays
    // =============================================================================================

    private void ending() {
        autosave();
        g.hundredDays().ifPresent(h -> { for (; briefingsShown < h.briefings().size(); briefingsShown++) briefing(h.briefings().get(briefingsShown)); });
        g.dayHundredReport().ifPresent(r -> { if (!reportShown) { report(r); reportShown = true; } });
        boolean governed = g.state().inaugurationCommand() >= 0;
        if (governed && g.setup().length() == GameSetup.Length.FULL_TERM) g.legacy().ifPresent(l -> {
            c.rule(l.headline().toUpperCase(Locale.ROOT));
            for (String s : l.lines()) c.wrap("  ", s);
        });
        List<String> options = new ArrayList<>();
        boolean inaugurated = g.state().inaugurationCommand() >= 0;
        if (inaugurated) options.add("Govern the first hundred days again, from the inauguration (the same election)");
        options.add("Replay from the start with the same seed (the same opponent and world, a different campaign)");
        options.add("Return to the menu");
        int k = c.menu("What next?", options, options.size() - 1);
        String choice = options.get(k);
        if (choice.startsWith("Govern")) {
            g = g.replayFromInauguration().orElseThrow();
            rewound();
            c.out.println("  Back to Inauguration Day.");
            play();
        } else if (choice.startsWith("Replay")) {
            g = g.restart();
            rewound();
            c.out.println("  The campaign begins again.");
            play();
        }
    }

    private void rewound() {
        logShown = g.log().size();
        daysLogShown = g.hundredDays().map(h -> h.log().size()).orElse(0);
        briefingsShown = 0;
        electionShown = g.result2028().isPresent();
        reportShown = false;
        autosave();
    }

    private void report(DayHundredReport r) {
        c.rule("DAY 100: " + r.outcome().label.toUpperCase(Locale.ROOT) + "   " + r.score() + " of 10");
        c.wrap("  ", r.headline());
        c.out.println();
        for (DayHundredReport.Goal goal : r.goals()) {
            String mark = switch (goal.status()) { case MET -> "[MET] "; case PARTLY -> "[PART]"; case MISSED -> "[MISS]"; };
            c.wrap(String.format("  %s %-15s", mark, goal.name()), goal.why());
        }
        c.out.println();
        c.wrap("  ", DayHundredReport.RULE);
        c.out.printf("  Approval, week by week: %.0f %s %.0f%n", r.startApproval(), Console.spark(r.approvalByWeek()), r.approval());
        c.out.println("  The decisions that shaped it:");
        List<String> ds = r.decisions();
        for (String d : ds.subList(Math.max(0, ds.size() - 12), ds.size())) c.wrap("   - ", d);
    }

    // =============================================================================================
    //  Saving
    // =============================================================================================

    private void autosave() {
        if (g == null) return;
        Map<String, Object> ui = new LinkedHashMap<>();
        ui.put("tutorial", tutorial);
        ui.put("seen", new ArrayList<>(seen));
        SaveManager.writeAtomically(saveFile, SaveManager.save(g, ui));
    }

    private static String phaseLabel(GameEngine s) {
        return switch (s.phase()) {
            case GENERAL_CAMPAIGN_2028 -> "the 2028 campaign";
            case TRANSITION -> "the transition";
            case FIRST_100_DAYS -> "the first hundred days, week " + s.state().hundredDays().week();
            case GOVERNING -> "governing";
            case REELECTION_CAMPAIGN_2032 -> "the 2032 campaign";
            case EPILOGUE, GAME_OVER -> "finished";
        };
    }

    static String describe(Candidate x) {
        return String.format("%-20s (%s)  charisma %d, debate %d, fundraising %d, policy %d, discipline %d, %s", x.name(), x.homeState(), x.charisma(),
                x.debate(), x.fundraising(), x.policy(), x.discipline(), Math.abs(x.ideology()) >= 0.4 ? (x.ideology() < 0 ? "progressive" : "conservative")
                        : Math.abs(x.ideology()) >= 0.2 ? "mainstream" : "moderate");
    }
}
