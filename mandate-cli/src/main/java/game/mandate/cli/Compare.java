package game.mandate.cli;

import game.mandate.core.opening.OpeningSetup;
import game.mandate.core.opening.Report;
import game.mandate.core.opening.Strategy;
import game.mandate.core.opening.Types.BillStage;
import game.mandate.core.opening.Types.CrisisOutcome;
import game.mandate.core.state.Party;

import java.util.EnumMap;
import java.util.Map;

/** Plays every scripted strategy over the same seeds and prints the outcome profiles side by side. */
final class Compare {
    private Compare() {}

    static void run(int seeds, boolean unified) {
        System.out.printf("12-week prototype, %d seeds per strategy (alternating parties), %s government%n%n", seeds, unified ? "unified" : "divided");
        System.out.printf("%-11s %6s %5s %5s %5s | %5s %5s | %6s %6s | %5s %5s %5s | %5s %6s %5s | %s%n",
                "strategy", "appr", "base", "swing", "opp", "law%", "policy", "econ", "10yr", "secur", "clash", "deter", "unity", "goodw", "capit", "shutdown wks");
        for (Strategy s : Strategy.values()) {
            double appr = 0, base = 0, sw = 0, op = 0, law = 0, pol = 0, econ = 0, ten = 0, sec = 0, clash = 0, det = 0, unity = 0, gw = 0, cap = 0, shut = 0;
            Map<CrisisOutcome, Integer> crises = new EnumMap<>(CrisisOutcome.class);
            for (int i = 0; i < seeds; i++) {
                Party p = i % 2 == 0 ? Party.DEMOCRAT : Party.REPUBLICAN;
                Report r = Strategy.play(new OpeningSetup(100 + i, p, unified), s).report();
                appr += r.approval(); base += r.baseApproval(); sw += r.swingApproval(); op += r.oppositionApproval();
                law += r.billStage() == BillStage.LAW ? 1 : 0; pol += r.policyDelivered(); econ += r.economyIndex(); ten += r.tenYear();
                sec += r.securityIndex(); clash += r.crisis() == CrisisOutcome.CLASH ? 1 : 0; det += r.deterrence(); unity += r.partyUnity();
                gw += r.goodwill(); cap += r.capital(); shut += r.shutdownWeeks();
                crises.merge(r.crisis(), 1, Integer::sum);
            }
            double n = seeds;
            System.out.printf("%-11s %6.1f %5.1f %5.1f %5.1f | %4.0f%% %5.2f | %6.2f %6.2f | %5.2f %4.0f%% %5.1f | %5.1f %6.1f %5.1f | %.2f  %s%n",
                    s, appr / n, base / n, sw / n, op / n, 100 * law / n, pol / n, econ / n, ten / n, sec / n, 100 * clash / n, det / n,
                    unity / n, gw / n, cap / n, shut / n, crises);
        }
    }
}
