package game.mandate.core.politics;

import game.mandate.core.data.Tuning;

/** Constants of the shared political system ({@code politics.*} keys). Rates are per week; monthly steps compound them. */
public record PoliticsParams(
        double[] weights, double[] start, double[] floor, double[] ceiling, double[] noisePerWeek,
        double honeymoonStart, double honeymoonDecayPerWeek, double modifierDecayPerWeek, double adjustPerWeek,
        double baseLevel, double baseMood, double baseEcon, double swingLevel, double swingGoodwill,
        double oppLevel, double oppGoodwill, double oppEcon,
        double econGas, double econInflation, double econUnemployment, double econMortgage, double econConfidence, double econGrowth,
        double refInflation, double refUnemployment, double refMortgage, double refGrowth,
        double mandatePerElectoralVote, double capitalStart, double capitalPerElectoralVote, double capitalUnifiedBonus,
        double capitalPerApprovalWeek, double capitalBaseWeek, double capitalApprovalPivot, double capitalCap,
        double goodwillDecayPerWeek, double goodwillNoisePerWeek, double goodwillDivided,
        double oppSwingWeight, double oppOffset, double oppHoneymoonWeeks, double oppHoneymoonBonus,
        double coopLine, double coopHold, double obstructLine, double obstructHold,
        double addressBase, double addressCharisma, double addressAdvantage, double addressDecayPerWeek, double addressSalience,
        double salienceFloor, double salienceCeiling, double salienceReversionPerMonth,
        double moodStart, double moodDriftPerWeek, double releaseReaction) {

    public static PoliticsParams from(Tuning t) {
        return new PoliticsParams(
                three(t, "politics.weight"), three(t, "politics.start"), three(t, "politics.floor"), three(t, "politics.ceiling"), three(t, "politics.noisePerWeek"),
                t.d("politics.honeymoonStart"), t.d("politics.honeymoonDecayPerWeek"), t.d("politics.modifierDecayPerWeek"), t.d("politics.adjustPerWeek"),
                t.d("politics.target.baseLevel"), t.d("politics.target.baseMood"), t.d("politics.target.baseEcon"),
                t.d("politics.target.swingLevel"), t.d("politics.target.swingGoodwill"),
                t.d("politics.target.oppLevel"), t.d("politics.target.oppGoodwill"), t.d("politics.target.oppEcon"),
                t.d("politics.econ.gas"), t.d("politics.econ.inflation"), t.d("politics.econ.unemployment"), t.d("politics.econ.mortgage"),
                t.d("politics.econ.confidence"), t.d("politics.econ.growth"),
                t.d("politics.ref.inflation"), t.d("politics.ref.unemployment"), t.d("politics.ref.mortgage"), t.d("politics.ref.growth"),
                t.d("politics.mandatePerElectoralVote"), t.d("politics.capital.start"), t.d("politics.capital.perElectoralVote"), t.d("politics.capital.unifiedBonus"),
                t.d("politics.capital.perApprovalWeek"), t.d("politics.capital.baseWeek"), t.d("politics.capital.approvalPivot"), t.d("politics.capital.cap"),
                t.d("politics.goodwill.decayPerWeek"), t.d("politics.goodwill.noisePerWeek"), t.d("politics.goodwill.divided"),
                t.d("politics.opposition.swingWeight"), t.d("politics.opposition.offset"), t.d("politics.opposition.honeymoonWeeks"), t.d("politics.opposition.honeymoonBonus"),
                t.d("politics.opposition.coopLine"), t.d("politics.opposition.coopHold"), t.d("politics.opposition.obstructLine"), t.d("politics.opposition.obstructHold"),
                t.d("politics.address.base"), t.d("politics.address.charisma"), t.d("politics.address.advantage"), t.d("politics.address.decayPerWeek"),
                t.d("politics.address.salience"),
                t.d("politics.salience.floor"), t.d("politics.salience.ceiling"), t.d("politics.salience.reversionPerMonth"),
                t.d("politics.mood.start"), t.d("politics.mood.driftPerWeek"), t.d("politics.releaseReaction"));
    }

    private static double[] three(Tuning t, String prefix) {
        return new double[]{t.d(prefix + ".base"), t.d(prefix + ".swing"), t.d(prefix + ".opposition")};
    }
}
