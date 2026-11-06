package game.mandate.core.congress;

import game.mandate.core.data.Tuning;

/** Constants of the vote model ({@code votes.*} keys). */
public record VoteParams(double slope, double ownPull, double oppPullCooperate, double oppPullSelective, double oppPullObstruct,
                         double bipartisanRelief, double approvalOwn, double approvalOwnPivot, double approvalOpp, double approvalOppPivot,
                         double relationshipWeight, double hawkThreshold, double hawkPenalty, double moderateBand, double pressureWeight,
                         double pressureSwingPivot, double pressureSwingScale, double capitalWeight, double houseNoise, double senateNoise,
                         double dealIdeologyFactor, double confirmBase, double confirmControversyThreshold, double confirmControversyBase,
                         double confirmControversyModerate, double confirmMoodLine, double confirmMoodPenalty, double confirmCrossover,
                         double confirmOppBase, double confirmOppSpread, double confirmOppCooperate, double confirmOppSelective,
                         double confirmOppObstruct, double confirmOppModerateFloor, double confirmNoise, double opposingAgendaPull) {

    public static VoteParams from(Tuning t) {
        return new VoteParams(t.d("votes.slope"), t.d("votes.ownPull"), t.d("votes.oppPull.cooperate"), t.d("votes.oppPull.selective"),
                t.d("votes.oppPull.obstruct"), t.d("votes.bipartisanRelief"), t.d("votes.approvalOwn"), t.d("votes.approvalOwnPivot"),
                t.d("votes.approvalOpp"), t.d("votes.approvalOppPivot"), t.d("votes.relationshipWeight"), t.d("votes.hawkThreshold"),
                t.d("votes.hawkPenalty"), t.d("votes.moderateBand"), t.d("votes.pressureWeight"), t.d("votes.pressureSwingPivot"),
                t.d("votes.pressureSwingScale"), t.d("votes.capitalWeight"), t.d("votes.houseNoise"), t.d("votes.senateNoise"),
                t.d("votes.dealIdeologyFactor"), t.d("confirm.base"), t.d("confirm.controversyThreshold"), t.d("confirm.controversyBase"),
                t.d("confirm.controversyModerate"), t.d("confirm.moodLine"), t.d("confirm.moodPenalty"), t.d("confirm.crossover"),
                t.d("confirm.oppBase"), t.d("confirm.oppSpread"), t.d("confirm.opp.cooperate"), t.d("confirm.opp.selective"),
                t.d("confirm.opp.obstruct"), t.d("confirm.oppModerateFloor"), t.d("confirm.noise"), t.d("votes.opposingAgendaPull"));
    }
}
