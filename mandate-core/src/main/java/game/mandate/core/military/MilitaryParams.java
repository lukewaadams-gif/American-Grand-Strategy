package game.mandate.core.military;

import game.mandate.core.data.Tuning;

/** Constants of the operational war model and of force regeneration ({@code military.*} keys). */
public record MilitaryParams(
        double redStart, double redDeterrenceReduction, double redSalvoPerDay, double redBreak,
        double antishipPerDay, double landattackPerDay, double pkAntiship, double pkLandattack,
        double basingFactor, double noBasingFactor, double postureBase, double posturePerDeterrence, double warningBonus,
        double defenseCompetenceWeight, double fogSd, double turnNoise,
        double interceptRatio, double interceptPk, double shipPerLeaker, double carrierPerLeaker, double aircraftPerLeaker,
        double carrierAirLostWithCarrier, double killedPerDayBases,
        double advancePerDay, double defenseBase, double defensePerWarningMonth, double defenseArmed,
        double attritionStandAside, double attritionArmed, double armedTransferPerDay, double maxDays,
        double earlyHours, double earlyDays, double lateHours,
        double[] growth, double[] inflation, double[] approvalFighting, double[] approvalAside, double[] deterrenceAfter,
        double surgeCap) {

    public static MilitaryParams from(Tuning t) {
        return new MilitaryParams(
                t.d("military.red.start"), t.d("military.red.deterrenceReduction"), t.d("military.red.salvoPerDay"), t.d("military.red.break"),
                t.d("military.blue.antishipPerDay"), t.d("military.blue.landattackPerDay"), t.d("military.blue.pkAntiship"), t.d("military.blue.pkLandattack"),
                t.d("military.blue.basingFactor"), t.d("military.blue.noBasingFactor"), t.d("military.blue.postureBase"), t.d("military.blue.posturePerDeterrence"),
                t.d("military.blue.warningBonus"), t.d("military.blue.defenseCompetence"), t.d("military.fogSd"), t.d("military.turnNoise"),
                t.d("military.ad.interceptRatio"), t.d("military.ad.pk"), t.d("military.loss.shipPerLeaker"), t.d("military.loss.carrierPerLeaker"),
                t.d("military.loss.aircraftPerLeaker"), t.d("military.loss.carrierAirWithCarrier"), t.d("military.loss.killedPerDayBases"),
                t.d("military.taiwan.advancePerDay"), t.d("military.taiwan.defenseBase"), t.d("military.taiwan.defensePerWarningMonth"),
                t.d("military.taiwan.defenseArmed"), t.d("military.taiwan.attritionStandAside"), t.d("military.taiwan.attritionArmed"),
                t.d("military.taiwan.armedTransferPerDay"), t.d("military.maxDays"),
                t.d("military.turn.earlyHours"), t.d("military.turn.earlyDays"), t.d("military.turn.lateHours"),
                byStatus(t, "military.outcome.growth"), byStatus(t, "military.outcome.inflation"), byStatus(t, "military.outcome.approvalFighting"),
                byStatus(t, "military.outcome.approvalAside"), byStatus(t, "military.outcome.deterrenceAfter"),
                t.d("military.production.surgeCap"));
    }

    private static double[] byStatus(Tuning t, String prefix) {
        return new double[]{t.d(prefix + ".defended"), t.d(prefix + ".stalemate"), t.d(prefix + ".fallen")};
    }
}
