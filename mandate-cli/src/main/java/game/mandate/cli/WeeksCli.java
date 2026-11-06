package game.mandate.cli;

import game.mandate.core.opening.Briefing;
import game.mandate.core.opening.Decision;
import game.mandate.core.opening.OpeningAction;
import game.mandate.core.opening.OpeningSetup;
import game.mandate.core.opening.OpeningTerm;
import game.mandate.core.opening.Report;
import game.mandate.core.opening.Types.BillSize;
import game.mandate.core.opening.Types.BillStage;
import game.mandate.core.opening.Types.Concession;
import game.mandate.core.opening.Types.Domain;
import game.mandate.core.opening.Types.NomineeType;
import game.mandate.core.opening.Types.PayFor;
import game.mandate.core.opening.Types.Post;
import game.mandate.core.opening.WhipRead;
import game.mandate.core.state.Party;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Text front end for the 12-week governing prototype ({@code ./mandate weeks [seed] [D|R] [unified|divided]}). */
final class WeeksCli {
    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    private final OpeningTerm t;

    WeeksCli(long seed, Party party, boolean unified) {
        t = new OpeningTerm(new OpeningSetup(Math.abs(seed), party, unified));
    }

    void run() throws IOException {
        System.out.println("THE FIRST TWELVE WEEKS - governing prototype. You are a " + t.party().adjective() + " president. " + t.congress() + ".");
        System.out.println("Each week you have " + OpeningTerm.ATTENTION_PER_WEEK + " attention points. Delegate any area to your chief of staff to stop managing it.");
        while (!t.over()) {
            header();
            for (Decision d : t.pending()) {
                if (game.mandate.core.opening.ChiefOfStaff.handles(d.kind(), t.delegated())) continue;
                System.out.println("\n== " + d.title() + " ==\n" + d.situation());
                for (int i = 0; i < d.options().size(); i++) System.out.println("  " + (i + 1) + ". " + d.options().get(i).label() + "\n       " + d.options().get(i).preview());
                attempt(new OpeningAction.Decide(d.id(), ask("Your decision", d.options().size()) - 1));
            }
            int c = ask("1 Draft/amend bill  2 Whip count & negotiate  3 Call a vote  4 Go public  5 Nominate  6 Meet opposition  7 Lean on the Fed  8 Release oil reserve  9 Delegate  10 End week", 10);
            switch (c) {
                case 1 -> bill();
                case 2 -> negotiate();
                case 3 -> {
                    if (t.stage() == BillStage.SENATE_FLOOR && t.canReconcile() && ask("Use budget reconciliation first? 1 yes 2 no", 2) == 1)
                        attempt(new OpeningAction.UseReconciliation());
                    attempt(new OpeningAction.CallVote(false));
                }
                case 4 -> attempt(new OpeningAction.GoPublic());
                case 5 -> {
                    Post p = Post.values()[ask("Post: 1 Treasury 2 Defense 3 State", 3) - 1];
                    System.out.println("  1 Loyalist (competence 55, loyal, controversial)  2 Expert (85, confirms readily)  3 Crossover from the other party (72, easy votes, disloyal)");
                    attempt(new OpeningAction.Nominate(p, NomineeType.values()[ask("Nominee", 3) - 1]));
                }
                case 6 -> attempt(new OpeningAction.MeetOpposition());
                case 7 -> attempt(new OpeningAction.LeanOnFed());
                case 8 -> attempt(new OpeningAction.ReleaseOilReserve());
                case 9 -> {
                    Domain d = Domain.values()[ask("1 Legislation 2 Cabinet 3 Crisis 4 Budget", 4) - 1];
                    attempt(new OpeningAction.Delegate(d, !t.delegated().contains(d)));
                    System.out.println("  Delegated: " + t.delegated());
                }
                default -> {
                    if (attempt(new OpeningAction.EndWeek())) printBriefing(t.briefings().get(t.briefings().size() - 1));
                }
            }
        }
        report(t.report());
    }

    private void header() {
        System.out.printf("%n=== Week %d | attention %d | capital %.0f | approval %.0f%% | bill: %s | opposition %s ===%n", t.week(), t.attention(), t.capital(),
                t.approval(), t.stage() == BillStage.NOT_DRAFTED ? "not drafted" : t.size().orElseThrow() + "/" + t.payFor().orElseThrow() + " " + t.stage(),
                t.opposition().name().toLowerCase());
        StringBuilder cab = new StringBuilder("Cabinet:");
        for (Post p : Post.values()) cab.append(' ').append(p).append('=').append(t.seat(p)).append(t.nominee(p).map(n -> "(" + n + ")").orElse(""));
        System.out.println(cab + String.format("  | %s %.0f, %s %.0f", t.wingName(0), t.wingMood(0), t.wingName(1), t.wingMood(1)));
    }

    private void bill() throws IOException {
        BillSize s = BillSize.values()[ask("Size: 1 small 2 medium 3 large", 3) - 1];
        PayFor[] ps = PayFor.values();
        for (int i = 0; i < ps.length; i++) System.out.println("  " + (i + 1) + ". " + ps[i].label);
        PayFor p = ps[ask("Pay-for", ps.length) - 1];
        attempt(t.stage() == BillStage.NOT_DRAFTED ? new OpeningAction.DraftBill(s, p) : new OpeningAction.AmendBill(s, p));
    }

    private void negotiate() throws IOException {
        if (t.stage() == BillStage.NOT_DRAFTED) { System.out.println("  Draft a bill first."); return; }
        boolean house = t.stage() != BillStage.SENATE_FLOOR;
        List<WhipRead> w = t.whip(house);
        System.out.printf("  %s whip count, %.0f%% to pass if called now:%n", house ? "House" : "Senate",
                100 * t.passChance(house ? BillStage.HOUSE_FLOOR : BillStage.SENATE_FLOOR));
        for (int i = 0; i < w.size(); i++) {
            WhipRead r = w.get(i);
            System.out.printf("  %d. %-24s (%s) %3.0f of %3d yes   relationship %+.2f   wants %s%n", i + 1, r.bloc(), r.party().code(), r.expectedYes(), r.members(), r.relationship(), r.wants());
        }
        int b = ask("Work which bloc (" + (w.size() + 1) + " = none)", w.size() + 1) - 1;
        if (b < 0 || b >= w.size()) return;
        int c = ask("1 Projects back home (capital 4)  2 Promise an appointment (capital 6, " + t.appointmentTokens() + " left)  3 Threaten a primary (own party only)", 3);
        attempt(new OpeningAction.Negotiate(w.get(b).bloc(), Concession.values()[c - 1]));
    }

    private void printBriefing(Briefing b) {
        System.out.println("\n--- Week " + b.week() + " briefing: " + b.headline());
        b.consequences().forEach(s -> System.out.println("  * " + s));
        b.reactions().forEach(s -> System.out.println("  > " + s));
        b.intelligence().forEach(s -> System.out.println("  i " + s));
    }

    private void report(Report r) {
        System.out.printf("%nTWELVE-WEEK REPORT%n  Approval %.0f%% (your voters %.0f, swing %.0f, opposition voters %.0f)%n", r.approval(), r.baseApproval(), r.swingApproval(), r.oppositionApproval());
        System.out.printf("  Bill: %s (policy delivered %.2f). Cabinet confirmed %d of 3 (avg competence %.0f).%n", r.billStage(), r.policyDelivered(), r.confirmed(), r.avgCompetence());
        System.out.printf("  Economy: inflation %.1f%%, unemployment %.1f%%, 10-year %.2f%%, gas $%.2f, deficit %+.2f%% of GDP.%n", r.inflation(), r.unemployment(), r.tenYear(), r.gas(), r.deficitChange());
        System.out.printf("  Crisis: %s, deterrence %.0f. Shutdown weeks: %d.%n", r.crisis(), r.deterrence(), r.shutdownWeeks());
        System.out.printf("  Party wings %.0f / %.0f; opposition %s (goodwill %.0f); capital %.0f; midterm outlook %+d House seats.%n",
                r.wingMoodA(), r.wingMoodB(), r.opposition(), r.goodwill(), r.capital(), r.midtermSeatOutlook());
    }

    private boolean attempt(OpeningAction a) {
        try {
            t.apply(a);
            return true;
        } catch (IllegalStateException e) {
            System.out.println("  ! " + e.getMessage());
            return false;
        }
    }

    private int ask(String prompt, int max) throws IOException {
        while (true) {
            System.out.print(prompt + " [1-" + max + "]: ");
            System.out.flush();
            String s = in.readLine();
            if (s == null) throw new IOException("Input closed");
            try {
                int n = Integer.parseInt(s.trim());
                if (n >= 1 && n <= max) return n;
            } catch (NumberFormatException ignored) { }
        }
    }
}
