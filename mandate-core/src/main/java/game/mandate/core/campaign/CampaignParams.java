package game.mandate.core.campaign;

import game.mandate.core.data.Tuning;

/** Constants of campaigning and elections ({@code campaign.*}, {@code election.*} keys). */
public record CampaignParams(
        double baseWeeklyIncome, double incomeFloor, double incomePerFundraising, double fundraiserYield, double fundraiserFloor,
        double fundraiserPerRating, double momentumIncome,
        double momentumDecay, double adDecay, double fieldDecay, double visitDecay,
        double adWeight, double fieldWeight, double visitWeight, double visitCharismaFloor, double visitCharisma, double refPerSeat,
        double homeState, double messageWeight, double consistencyStep, double consistencyCap, double charismaMomentum,
        double debateWeight, double debateCap, double debateRating, double debateCounter,
        double margin2024, double nationalErrorSd, double stateErrorSd, double pollMissSd, double statePollNoise,
        double qualityWeight, double extremityWeight, double campaignSwingDecay, double approvalToSwing,
        double incApproval, double incApprovalPivot, double incGrowth, double incGrowthPivot, double incInflation, double incInflationPivot,
        double incFallen, double incUnfinished, double incUnemployment, double incUnemploymentPivot, double incCampaign,
        double houseBase, double housePerPoint, double houseNoise, double houseMin, double houseMax,
        double senateIncumbency, double senateNoise,
        double midApproval, double midApprovalPivot, double midGrowth, double midGrowthPivot, double midPenalty, double midNoise,
        double midHouseMin, double midHouseMax,
        double startCash, double reelectionBase, double reelectionPerCapital, double challengerCash,
        boolean cellElectorate, double pledgeSwingPerAppeal, double pledgeSalience, double pledgeBrokenMood, int pledgeMax,
        int forecastGrid) {

    public static CampaignParams from(Tuning t) {
        return new CampaignParams(
                t.d("campaign.income.base"), t.d("campaign.income.floor"), t.d("campaign.income.perFundraising"), t.d("campaign.fundraiser.yield"),
                t.d("campaign.fundraiser.floor"), t.d("campaign.fundraiser.perRating"), t.d("campaign.income.momentum"),
                t.d("campaign.decay.momentum"), t.d("campaign.decay.ads"), t.d("campaign.decay.field"), t.d("campaign.decay.visits"),
                t.d("campaign.effect.ads"), t.d("campaign.effect.field"), t.d("campaign.effect.visits"), t.d("campaign.effect.visitCharismaFloor"),
                t.d("campaign.effect.visitCharisma"), t.d("campaign.effect.refPerSeat"), t.d("campaign.effect.homeState"),
                t.d("campaign.message.weight"), t.d("campaign.message.consistencyStep"), t.d("campaign.message.consistencyCap"), t.d("campaign.message.charisma"),
                t.d("campaign.debate.weight"), t.d("campaign.debate.cap"), t.d("campaign.debate.rating"), t.d("campaign.debate.counter"),
                t.d("election.margin2024"), t.d("election.nationalErrorSd"), t.d("election.stateErrorSd"), t.d("election.pollMissSd"), t.d("election.statePollNoise"),
                t.d("election.qualityWeight"), t.d("election.extremityWeight"), t.d("election.campaignSwingDecay"), t.d("election.approvalToSwing"),
                t.d("election.incumbent.approval"), t.d("election.incumbent.approvalPivot"), t.d("election.incumbent.growth"), t.d("election.incumbent.growthPivot"),
                t.d("election.incumbent.inflation"), t.d("election.incumbent.inflationPivot"), t.d("election.incumbent.fallen"), t.d("election.incumbent.unfinished"),
                t.d("election.incumbent.unemployment"), t.d("election.incumbent.unemploymentPivot"), t.d("election.incumbent.campaign"),
                t.d("election.house.base"), t.d("election.house.perPoint"), t.d("election.house.noise"), t.d("election.house.min"), t.d("election.house.max"),
                t.d("election.senate.incumbency"), t.d("election.senate.noise"),
                t.d("election.midterm.approval"), t.d("election.midterm.approvalPivot"), t.d("election.midterm.growth"), t.d("election.midterm.growthPivot"),
                t.d("election.midterm.penalty"), t.d("election.midterm.noise"), t.d("election.midterm.houseMin"), t.d("election.midterm.houseMax"),
                t.d("campaign.startCash"), t.d("campaign.reelection.base"), t.d("campaign.reelection.perCapital"), t.d("campaign.reelection.challengerCash"),
                t.d("election.cellElectorate") >= 0.5, t.d("campaign.pledge.swingPerAppeal"), t.d("campaign.pledge.salience"),
                t.d("campaign.pledge.brokenMood"), t.i("campaign.pledge.max"), t.i("election.forecastGrid"));
    }
}
