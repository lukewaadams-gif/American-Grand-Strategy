package game.mandate.core.politics;

import game.mandate.core.data.Tuning;

/**
 * Constants of the shared political system ({@code politics.*} keys). Rates are per week; monthly steps compound them.
 * The {@code rd*} values are Resolute Desk's era settings for its approval model.
 */
public record PoliticsParams(
        double[] weights, double[] start, double[] floor, double[] ceiling,
        double eventFadePerWeek,
        double capitalStart, double capitalPerElectoralVote, double capitalUnifiedBonus,
        double capitalPerApprovalWeek, double capitalBaseWeek, double capitalApprovalPivot, double capitalCap,
        double goodwillDecayPerWeek, double goodwillNoisePerWeek, double goodwillDivided,
        double oppSwingWeight, double oppOffset, double oppHoneymoonWeeks, double oppHoneymoonBonus,
        double coopLine, double coopHold, double obstructLine, double obstructHold,
        double addressBase, double addressCharisma, double addressAdvantage, double addressDecayPerWeek, double addressSalience,
        double salienceFloor, double salienceCeiling, double salienceReversionPerMonth,
        double moodStart, double moodDriftPerWeek,
        double rdEraBase, double rdPolarization, double rdURef, double rdPiRef, double rdGasRef, double rdPriceLevel) {

    public static PoliticsParams from(Tuning t) {
        return new PoliticsParams(
                three(t, "politics.weight"), three(t, "politics.start"), three(t, "politics.floor"), three(t, "politics.ceiling"),
                t.d("politics.eventFadePerWeek"),
                t.d("politics.capital.start"), t.d("politics.capital.perElectoralVote"), t.d("politics.capital.unifiedBonus"),
                t.d("politics.capital.perApprovalWeek"), t.d("politics.capital.baseWeek"), t.d("politics.capital.approvalPivot"), t.d("politics.capital.cap"),
                t.d("politics.goodwill.decayPerWeek"), t.d("politics.goodwill.noisePerWeek"), t.d("politics.goodwill.divided"),
                t.d("politics.opposition.swingWeight"), t.d("politics.opposition.offset"), t.d("politics.opposition.honeymoonWeeks"), t.d("politics.opposition.honeymoonBonus"),
                t.d("politics.opposition.coopLine"), t.d("politics.opposition.coopHold"), t.d("politics.opposition.obstructLine"), t.d("politics.opposition.obstructHold"),
                t.d("politics.address.base"), t.d("politics.address.charisma"), t.d("politics.address.advantage"), t.d("politics.address.decayPerWeek"),
                t.d("politics.address.salience"),
                t.d("politics.salience.floor"), t.d("politics.salience.ceiling"), t.d("politics.salience.reversionPerMonth"),
                t.d("politics.mood.start"), t.d("politics.mood.driftPerWeek"),
                t.d("politics.rd.eraBase"), t.d("politics.rd.polarization"), t.d("politics.rd.uRef"), t.d("politics.rd.piRef"), t.d("politics.rd.gasRef"),
                t.d("politics.rd.priceLevel"));
    }

    /** The three coalitions: base, swing, opposition. */
    private static double[] three(Tuning t, String prefix) {
        return new double[]{t.d(prefix + ".base"), t.d(prefix + ".swing"), t.d(prefix + ".opposition")};
    }
}
