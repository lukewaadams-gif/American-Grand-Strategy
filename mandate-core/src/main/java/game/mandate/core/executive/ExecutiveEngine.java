package game.mandate.core.executive;

import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.congress.Congress;
import game.mandate.core.congress.VoteModel;
import game.mandate.core.data.Tuning;
import game.mandate.core.effects.Effect;
import game.mandate.core.politics.OppositionStrategy;
import game.mandate.core.rng.DeterministicRandomManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the presidency: who holds office, the cabinet, the agenda and the president's attention. Confirmation
 * votes are computed by Congress's {@link VoteModel} (the single confirmation formula), never here.
 */
public final class ExecutiveEngine {
    private final int attentionPerTurn;
    private final double actingCompetence, actingLoyalty;
    private final CabinetPool pool;

    public ExecutiveEngine(Tuning t, CabinetPool pool) {
        this.attentionPerTurn = t.i("executive.attentionPerTurn");
        this.actingCompetence = t.d("executive.actingCompetence");
        this.actingLoyalty = t.d("executive.actingLoyalty");
        this.pool = pool;
    }

    public GovernmentState newGovernment() { return new GovernmentState(); }

    public boolean apply(GovernmentState g, Effect e, LocalDate date) {
        if (e instanceof Effect.PresidentInaugurated x) {
            g.term = x.candidateId().equals(g.presidentId) ? g.term + 1 : 1;
            g.presidentId = x.candidateId();
            g.presidentParty = x.party();
            g.inaugurated = date;
            g.attention = attentionPerTurn;
            return true;
        }
        return false;
    }

    /**
     * Fills empty posts with the transition team's experts and puts each to a Senate vote.
     * @return one line per post for the record
     */
    public List<String> formCabinet(GovernmentState g, Congress congress, VoteModel votes, OppositionStrategy opposition,
                                    double moderateWingMood, DeterministicRandomManager rng) {
        List<String> lines = new ArrayList<>();
        for (Post p : Post.values()) {
            if (g.cabinet.get(p).status() != GovernmentState.SeatStatus.VACANT) continue;
            CabinetPool.Candidate c = pool.pick(g.presidentParty, p, NomineeType.EXPERT);
            double expected = votes.confirmationExpected(g.presidentParty, congress, c.type().controversy(), c.type() == NomineeType.CROSSOVER,
                    opposition, moderateWingMood);
            int yes = (int) Math.round(expected + votes.confirmationNoise() * rng.gaussian("cabinet.vote"));
            boolean ok = yes >= 50;
            g.cabinet.put(p, new GovernmentState.Seat(c.name(), c.type(), ok ? GovernmentState.SeatStatus.CONFIRMED : GovernmentState.SeatStatus.ACTING));
            lines.add((ok ? "Confirmed " : "Rejected ") + c.name() + " for " + p.name().toLowerCase() + ", " + yes + "-" + (100 - yes) + ".");
        }
        return lines;
    }

    public double competence(GovernmentState g, Post p) {
        GovernmentState.Seat s = g.cabinet.get(p);
        return s.status() == GovernmentState.SeatStatus.CONFIRMED ? s.type().competence() : actingCompetence;
    }

    public double loyalty(GovernmentState g, Post p) {
        GovernmentState.Seat s = g.cabinet.get(p);
        return s.status() == GovernmentState.SeatStatus.CONFIRMED ? s.type().loyalty() : actingLoyalty;
    }

    public void setAgenda(GovernmentState g, List<String> ids) {
        g.agenda.clear();
        g.agenda.addAll(ids);
    }

    public void spendAttention(GovernmentState g, int cost) {
        if (g.attention < cost) throw new IllegalCommandException("Not enough attention left this turn (" + g.attention + ")");
        g.attention -= cost;
    }

    public void newTurn(GovernmentState g) { g.attention = attentionPerTurn; }

    public double actingCompetence() { return actingCompetence; }
}
