package game.mandate.core.electorate;

import game.mandate.core.state.Party;

import java.util.Map;

/**
 * The outcome of one presidential election.
 *
 * @param stateMargins realised margin in each state, D minus R, points
 * @param popularMargin national popular-vote margin, D minus R, points
 * @param tippingPoint the state that delivered the winner's 270th electoral vote
 */
public record ElectionResult(int year, Map<String, Double> stateMargins, int demElectoralVotes,
                             int repElectoralVotes, double popularMargin, String tippingPoint) {

    public Party winner() {
        // A 269-269 tie goes to the House; the foundation resolves it for the House majority's party
        // upstream, so here a tie is reported as the popular-vote winner.
        if (demElectoralVotes == repElectoralVotes) return popularMargin >= 0 ? Party.DEMOCRAT : Party.REPUBLICAN;
        return demElectoralVotes > repElectoralVotes ? Party.DEMOCRAT : Party.REPUBLICAN;
    }

    public int electoralVotes(Party p) { return p == Party.DEMOCRAT ? demElectoralVotes : repElectoralVotes; }

    public double marginFor(Party p) { return popularMargin * p.sign(); }
}
