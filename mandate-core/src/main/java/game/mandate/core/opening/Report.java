package game.mandate.core.opening;

import game.mandate.core.opening.Types.BillStage;
import game.mandate.core.opening.Types.CrisisOutcome;
import game.mandate.core.politics.OppositionStrategy;

/**
 * The 12-week report card. There is deliberately no single score: the prototype asks whether different
 * strategies lead to different, defensible profiles, and a single number would hide that.
 */
public record Report(double approval, double baseApproval, double swingApproval, double oppositionApproval,
                     BillStage billStage, double policyDelivered, int confirmed, double avgCompetence,
                     double inflation, double unemployment, double tenYear, double gas, double deficitChange,
                     CrisisOutcome crisis, double deterrence, double wingMoodA, double wingMoodB,
                     OppositionStrategy opposition, double goodwill, double capital, int shutdownWeeks,
                     int midtermSeatOutlook) {

    /** Economy index: higher is better. 0 = the starting economy. */
    public double economyIndex() {
        return -2.0 * (inflation - 2.8) - 2.0 * (unemployment - 4.4) - 1.0 * (tenYear - 4.1) - 1.0 * (gas - 3.2);
    }

    /** Security index: crisis outcome plus the deterrence left for the next one. */
    public double securityIndex() {
        double c = switch (crisis) {
            case BACKED_DOWN -> 2.0;
            case DEAL -> 1.2;
            case STALEMATE -> 0.0;
            case ACTIVE -> -0.5;
            case CLASH -> -2.0;
            case NONE -> 0.5;
        };
        return c + (deterrence - 50) / 20.0;
    }

    public double partyUnity() { return (wingMoodA + wingMoodB) / 2.0; }
}
