package game.mandate.core.game;

import game.mandate.core.economy.Economy;
import game.mandate.core.electorate.ElectionResult;
import game.mandate.core.world.TheaterOutcome;

import java.util.ArrayList;
import java.util.List;

/**
 * The end-of-game verdict. Components are listed separately so the player can see exactly what their
 * score is made of; a single opaque number is the most common complaint about strategy-game endings.
 */
public record Legacy(int score, String grade, List<String> lines) {

    public String headline() { return "Legacy: " + grade + " (" + score + ")"; }

    static Legacy score(Game g) {
        List<String> lines = new ArrayList<>();
        int score = 0;
        ElectionResult r28 = g.result2028().orElseThrow();
        boolean won28 = r28.winner() == g.player().party();
        if (!won28) {
            lines.add("Lost the 2028 election, " + r28.electoralVotes(g.player().party()) + " electoral votes.");
            score += Math.max(0, r28.electoralVotes(g.player().party()) - 150);
            return new Legacy(score, grade(score), lines);
        }
        score += 300;
        lines.add("Won the presidency with " + r28.electoralVotes(g.player().party()) + " electoral votes (+300).");

        int laws = g.laws().size();
        long agendaDone = g.agenda().stream().filter(g.laws()::contains).count();
        score += laws * 40 + (int) agendaDone * 60;
        lines.add(laws + " laws signed (+" + laws * 40 + "); " + agendaDone + " of " + g.agenda().size() + " agenda priorities (+" + agendaDone * 60 + ").");

        Economy e = g.economy().orElseThrow();
        int econ = (int) Math.round((g.startMisery() - e.misery()) * 40);
        score += econ;
        lines.add(String.format("Misery index %.1f -> %.1f (%+d).", g.startMisery(), e.misery(), econ));

        int app = (int) Math.round((g.approval() - 45) * 8);
        score += app;
        lines.add(String.format("Final approval %.0f%% (%+d).", g.approval(), app));

        if (g.war().isPresent()) {
            TheaterOutcome w = g.war().get();
            int pts = switch (w.status()) { case DEFENDED -> 150; case STALEMATE -> -20; case FALLEN -> -200; };
            score += pts;
            lines.add("Taiwan: " + w.status().name().toLowerCase() + " (" + (pts >= 0 ? "+" : "") + pts + ").");
        } else if (g.world().rung() != null) {
            score += 80;
            lines.add("Kept the peace through a Taiwan crisis (+80).");
        }

        g.result2032().ifPresent(r -> {
            boolean re = r.winner() == g.player().party();
            lines.add((re ? "Re-elected" : "Defeated") + " in 2032, " + r.electoralVotes(g.player().party()) + " electoral votes" + (re ? " (+250)." : "."));
        });
        if (g.result2032().map(r -> r.winner() == g.player().party()).orElse(false)) score += 250;
        return new Legacy(score, grade(score), lines);
    }

    private static String grade(int s) {
        if (s >= 1100) return "Historic presidency";
        if (s >= 850) return "Consequential presidency";
        if (s >= 600) return "Solid presidency";
        if (s >= 350) return "Troubled presidency";
        if (s >= 100) return "Failed presidency";
        return "A campaign remembered by few";
    }
}
