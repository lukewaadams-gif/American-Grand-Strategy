package game.mandate.core.military;

/**
 * What the presidential game receives back from a war, whichever engine fought it.
 *
 * @param usKilled            US military deaths
 * @param growthShock         change in US growth over the following year, points
 * @param inflationShock      change in US inflation over the following year, points
 * @param approvalShift       immediate effect on approval (rally or backlash), points
 * @param deterrenceAfter     Pacific deterrence after the war, 0-100
 */
public record TheaterOutcome(TaiwanStatus status, int durationWeeks, int usKilled, double growthShock,
                             double inflationShock, double approvalShift, double deterrenceAfter, String summary,
                             int shipsLost, int carriersLost, int aircraftLost, int munitionsExpended) {

    public enum TaiwanStatus { DEFENDED, STALEMATE, FALLEN }
}
