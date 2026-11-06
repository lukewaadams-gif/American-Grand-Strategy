package game.mandate.cli;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.ai.CampaignAi;
import game.mandate.core.campaign.CampaignWeekPlan;
import game.mandate.core.campaign.Candidate;
import game.mandate.core.campaign.CandidateCatalog;
import game.mandate.core.campaign.DebatePosture;
import game.mandate.core.campaign.Issue;
import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.congress.Bill;
import game.mandate.core.congress.LegislativeStrategy;
import game.mandate.core.congress.WhipCount;
import game.mandate.core.events.EventOption;
import game.mandate.core.events.PendingEvent;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.game.GameSetup;
import game.mandate.core.game.Scenario;
import game.mandate.core.state.Party;
import game.mandate.core.diplomacy.HotSpot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Text front end. Three modes:
 * <pre>
 *   play  [seed]                interactive game in the terminal
 *   auto  [seed] [D|R]          autopilot plays one full game and prints the log
 *   batch [runs] [scenario]     balance report over many autopilot games
 * </pre>
 * The desktop UI (milestone M4) drives the same core API; this front end exists so designers can
 * play and balance the simulation before any art exists.
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        String mode = args.length > 0 ? args[0] : "play";
        switch (mode) {
            case "auto" -> auto(longArg(args, 1, 2028), args.length > 2 ? Party.valueOf(args[2].equals("R") ? "REPUBLICAN" : "DEMOCRAT") : Party.DEMOCRAT);
            case "compare" -> {
                Compare.run((int) longArg(args, 1, 200), true);
                System.out.println();
                Compare.run((int) longArg(args, 1, 200), false);
            }
            case "weeks" -> new WeeksCli(longArg(args, 1, System.nanoTime() % 100000),
                    args.length > 2 && args[2].equals("R") ? Party.REPUBLICAN : Party.DEMOCRAT,
                    !(args.length > 3 && args[3].equals("divided"))).run();
            case "batch" -> Batch.run((int) longArg(args, 1, 200), args.length > 2 ? Scenario.valueOf(args[2]) : Scenario.TOSS_UP);
            case "play" -> new Main.Interactive(longArg(args, 1, System.nanoTime() % 100000)).run();
            default -> System.out.println("usage: mandate [play [seed] | auto [seed] [D|R] | batch [runs] [SCENARIO] | weeks [seed] [D|R] [unified|divided] | compare [seeds]]");
        }
    }

    static long longArg(String[] a, int i, long def) { return a.length > i ? Long.parseLong(a[i]) : def; }

    static GameSetup defaultSetup(long seed, Party party, Scenario scenario) {
        CandidateCatalog c = CandidateCatalog.load();
        List<Candidate> mine = c.forParty(party), theirs = c.forParty(party.opponent());
        int i = (int) Math.floorMod(seed, mine.size()), j = (int) Math.floorMod(seed / 7, theirs.size());
        return new GameSetup(seed, party, mine.get(i).id(), theirs.get(j).id(), scenario, GameSetup.Difficulty.STANDARD);
    }

    static void auto(long seed, Party party) {
        GameEngine g = new GameEngine(defaultSetup(seed, party, Scenario.TOSS_UP));
        while (!g.over()) AutoPilot.playTurn(g);
        g.log().forEach(System.out::println);
        g.legacy().ifPresent(l -> l.lines().forEach(s -> System.out.println("  " + s)));
    }

    // =========================================================================================

    static final class Interactive {
        private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        private final long seed;
        private GameEngine g;
        private int logShown;

        Interactive(long seed) { this.seed = Math.abs(seed); }

        void run() throws IOException {
            System.out.println("MANDATE (working title) - text prototype. Seed " + seed + ".");
            Party party = ask("Play as (1) Democrat or (2) Republican", 2) == 1 ? Party.DEMOCRAT : Party.REPUBLICAN;
            CandidateCatalog cat = CandidateCatalog.load();
            List<Candidate> mine = cat.forParty(party);
            for (int i = 0; i < mine.size(); i++) System.out.println("  " + (i + 1) + ". " + describe(mine.get(i)));
            Candidate me = mine.get(ask("Choose your candidate", mine.size()) - 1);
            List<Candidate> theirs = cat.forParty(party.opponent());
            Candidate opp = theirs.get((int) (seed % theirs.size()));
            Scenario[] sc = Scenario.values();
            for (int i = 0; i < sc.length; i++) System.out.println("  " + (i + 1) + ". " + sc[i].label() + " - " + sc[i].description());
            Scenario scenario = sc[ask("Scenario", sc.length) - 1];
            g = new GameEngine(new GameSetup(seed, party, me.id(), opp.id(), scenario, GameSetup.Difficulty.STANDARD));

            while (!g.over()) {
                printNewLog();
                try {
                    turn();
                } catch (IllegalCommandException e) {
                    System.out.println("  ! " + e.getMessage());
                }
            }
            printNewLog();
            g.legacy().ifPresent(l -> l.lines().forEach(s -> System.out.println("  " + s)));
        }

        private void turn() throws IOException {
            for (PendingEvent p : g.pendingEvents()) {
                System.out.println("\n== " + p.definition().title() + " ==\n" + p.definition().body());
                List<EventOption> os = p.definition().options();
                for (int i = 0; i < os.size(); i++) {
                    EventOption o = os.get(i);
                    StringBuilder b = new StringBuilder("  " + (i + 1) + ". " + o.label());
                    if (o.successChance() < 1) b.append(String.format(" [%d%% chance]", Math.round(o.successChance() * 100)));
                    if (!o.effects().isEmpty()) b.append("  -> ").append(String.join("; ", o.effects().stream().map(e -> e.describe()).toList()));
                    System.out.println(b);
                }
                g.apply(new Command.ResolveEvent(p.instanceId(), ask("Your decision", os.size()) - 1));
            }
            switch (g.phase()) {
                case GENERAL_CAMPAIGN_2028, REELECTION_CAMPAIGN_2032 -> campaignTurn();
                case TRANSITION -> transition();
                case GOVERNING -> governingTurn();
                default -> g.apply(new Command.EndTurn());
            }
        }

        private void campaignTurn() throws IOException {
            Map<String, Double> polls = g.statePolls();
            int sign = g.player().party().sign();
            List<String> close = new ArrayList<>(polls.keySet());
            close.removeIf(s -> Math.abs(polls.get(s)) > 8);
            close.sort(Comparator.comparingDouble(s -> Math.abs(polls.get(s))));
            int evLead = 0;
            for (var e : polls.entrySet()) if (e.getValue() * sign > 0) evLead += g.states().get(e.getKey()).electoralVotes();
            System.out.printf("%n--- Week of %s | cash $%.0fM | polls give you %d electoral votes ---%n", g.date(), g.player().cash(), evLead);
            StringBuilder b = new StringBuilder("Battlegrounds (your margin): ");
            for (String s : close) b.append(s).append(String.format(" %+.1f  ", polls.get(s) * sign));
            System.out.println(b);
            if (g.debateThisWeek().isPresent()) {
                Candidate opp = g.opponent().candidate();
                System.out.println("DEBATE this week. Prep notes: your opponent favours \"" + DebatePosture.tendencyOf(opp).label() + "\".");
                DebatePosture[] ps = DebatePosture.values();
                for (int i = 0; i < ps.length; i++) System.out.println("  " + (i + 1) + ". " + ps[i].label());
                g.apply(new Command.ChooseDebatePosture(ps[ask("Debate approach", ps.length) - 1]));
            }
            int c = ask("1. Let your campaign manager plan the week  2. Plan it yourself", 2);
            if (c == 2) {
                CampaignWeekPlan suggested = CampaignAi.plan(g, g.player(), 1.0);
                System.out.println("  Rally states, up to 3, space separated (manager suggests: " + String.join(" ", suggested.rallyStates()) + "):");
                List<String> rallies = new ArrayList<>(List.of(line().toUpperCase().split("\\s+")));
                rallies.removeIf(String::isBlank);
                if (rallies.size() > 3) rallies = rallies.subList(0, 3);
                Issue[] is = Issue.values();
                for (int i = 0; i < is.length; i++) System.out.printf("  %d. %s (trust %+.0f, salience %.2f)%n", i + 1, is[i].label(), is[i].advantageFor(g.player().party()), g.salience().get(is[i]));
                Issue msg = is[ask("Message", is.length) - 1];
                g.apply(new Command.PlanCampaign(new CampaignWeekPlan(rallies, 3 - rallies.size(), suggested.adSpend(), suggested.fieldSpend(), msg)));
            }
            g.apply(new Command.EndTurn());
        }

        private void transition() throws IOException {
            List<Bill> options = g.bills().availableTo(g.player().party());
            System.out.println("\nCongress: " + g.congress() + ". Choose up to three agenda priorities (numbers, space separated):");
            for (int i = 0; i < options.size(); i++) {
                Bill b = options.get(i);
                System.out.printf("  %2d. %-50s whip read %3.0f%% (party line)%n", i + 1, b.name(), g.whip(b.id(), LegislativeStrategy.PARTY_LINE, 0).passChance() * 100);
            }
            List<String> ids = new ArrayList<>();
            for (String t : line().trim().split("\\s+")) {
                try { int n = Integer.parseInt(t); if (n >= 1 && n <= options.size() && ids.size() < 3) ids.add(options.get(n - 1).id()); }
                catch (NumberFormatException ignored) { }
            }
            g.apply(new Command.SetAgenda(ids));
            g.apply(new Command.EndTurn());
        }

        private void governingTurn() throws IOException {
            var e = g.economy().orElseThrow();
            System.out.printf("%n--- %s | approval %.0f%% | capital %.0f | attention %d | growth %.1f%% unemployment %.1f%% inflation %.1f%% | Taiwan tension %.0f ---%n",
                    g.date(), g.approval(), g.capital(), g.attention(), e.growth(), e.unemployment(), e.inflation(), g.diplomacy().tension(HotSpot.TAIWAN_STRAIT));
            int c = ask("1. Push a bill (2 attention)  2. Address the nation (1)  3. Bolster Pacific deterrence (1)  4. Diplomacy (1)  5. End the month", 5);
            switch (c) {
                case 1 -> {
                    List<Bill> open = g.bills().all().stream().filter(b -> !g.laws().contains(b.id())).toList();
                    for (int i = 0; i < open.size(); i++) System.out.printf("  %2d. %s%s%n", i + 1, open.get(i).name(), g.agenda().contains(open.get(i).id()) ? "  [agenda]" : "");
                    Bill b = open.get(ask("Bill", open.size()) - 1);
                    LegislativeStrategy[] ss = LegislativeStrategy.values();
                    for (int i = 0; i < ss.length; i++) {
                        WhipCount w = g.whip(b.id(), ss[i], 0);
                        System.out.printf("  %d. %-22s House %3.0f/%d  Senate %3.0f/%d  ~%3.0f%%  (payoff %.0f%%)%n", i + 1, ss[i].label(),
                                w.houseExpected(), w.houseNeeded(), w.senateExpected(), w.senateNeeded(), w.passChance() * 100, ss[i].payoff() * 100);
                    }
                    LegislativeStrategy s = ss[ask("Strategy", ss.length) - 1];
                    int cap = ask("Political capital to commit (0-" + (int) g.capital() + "), enter 1 + amount", (int) g.capital() + 1) - 1;
                    g.apply(new Command.PushBill(b.id(), s, cap));
                }
                case 2 -> {
                    Issue[] is = Issue.values();
                    for (int i = 0; i < is.length; i++) System.out.println("  " + (i + 1) + ". " + is[i].label());
                    g.apply(new Command.PublicAddress(is[ask("Issue", is.length) - 1]));
                }
                case 3 -> g.apply(new Command.BolsterDeterrence());
                case 4 -> {
                    HotSpot[] hs = HotSpot.values();
                    for (int i = 0; i < hs.length; i++) System.out.printf("  %d. %s (tension %.0f)%n", i + 1, hs[i].label(), g.diplomacy().tension(hs[i]));
                    g.apply(new Command.Diplomacy(hs[ask("Where", hs.length) - 1]));
                }
                default -> g.apply(new Command.EndTurn());
            }
        }

        private void printNewLog() {
            List<String> log = g.log();
            for (; logShown < log.size(); logShown++) System.out.println(log.get(logShown));
        }

        private int ask(String prompt, int max) throws IOException {
            while (true) {
                System.out.print(prompt + " [1-" + max + "]: ");
                System.out.flush();
                String s = line();
                try {
                    int n = Integer.parseInt(s.trim());
                    if (n >= 1 && n <= max) return n;
                } catch (NumberFormatException ignored) { }
            }
        }

        private String line() throws IOException {
            String s = in.readLine();
            if (s == null) throw new IOException("Input closed");
            return s;
        }

        private static String describe(Candidate c) {
            return String.format("%-18s (%s)  charisma %d, debate %d, fundraising %d, policy %d, discipline %d", c.name(), c.homeState(),
                    c.charisma(), c.debate(), c.fundraising(), c.policy(), c.discipline());
        }
    }
}
