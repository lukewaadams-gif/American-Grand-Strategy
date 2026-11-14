package game.mandate.core.taiwan;

import game.mandate.core.effects.Effect;

import java.util.List;

/**
 * How the Taiwan crisis ended, for the presidency (docs/ISLAND_CHAIN.md): the status the source's ten outcomes map onto
 * (spec 11.6), the numbers, and the fallout as effects (spec 11.7).
 *
 * @param status        DETERRED, DEFENDED, STALEMATE, FALLEN or NUCLEAR; null while the crisis goes on
 * @param outcome       the source's outcome ("Taiwan defended", "Pyrrhic defence of Taiwan" ...), or null
 * @param code          its code 0-9 ({@link TaiwanEngine#OUTCOMES}), or -1
 * @param reason        one sentence on why it ended
 * @param usKilled      Americans killed
 * @param shipsLost     US ships sunk (carriers included)
 * @param carriersLost  US carriers sunk
 * @param aircraftLost  US aircraft lost, in the air and on the ground
 * @param territoryKm2  km2 of Taiwan the PLA holds
 * @param territoryShare the same as a share of Taiwan's 36,000 km2
 * @param electricity   Taiwan's electricity, share of normal
 * @param chips         Taiwan's chip output, share of normal
 * @param days          days of crisis and war
 * @param warDays       days of war
 * @param score         the US score 0-100 (spec 8.9)
 * @param grade         DECISIVE, SUCCESS, MIXED, SETBACK or DEFEAT
 * @param fallout       the effects on the presidency (already emitted by the turn that ended it)
 */
public record TaiwanResult(Status status, String outcome, int code, String reason, int usKilled, int shipsLost, int carriersLost, int aircraftLost,
                           double territoryKm2, double territoryShare, double electricity, double chips, int days, double warDays, double score, String grade,
                           List<Effect> fallout) {

    public enum Status { DETERRED, DEFENDED, STALEMATE, FALLEN, NUCLEAR }

    /** The source's outcome code mapped onto the presidency's status (spec 11.6). */
    public static Status status(int code) {
        return switch (code) {
            case 0 -> Status.DETERRED;
            case 1, 2, 9 -> Status.DEFENDED;
            case 3, 4, 8 -> Status.STALEMATE;
            case 5, 6, 10 -> Status.FALLEN;
            case 7 -> Status.NUCLEAR;
            default -> null;
        };
    }

    public static String grade(double score) {
        return score >= 80 ? "DECISIVE" : score >= 62 ? "SUCCESS" : score >= 45 ? "MIXED" : score >= 28 ? "SETBACK" : "DEFEAT";
    }
}
