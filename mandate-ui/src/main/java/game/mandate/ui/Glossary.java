package game.mandate.ui;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What each mechanic means, in a sentence or two. Every KPI, meter and control that names a mechanic uses these
 * texts for its tooltip, and the help overlay lists them all, so an explanation is never more than a hover away.
 */
public final class Glossary {
    private Glossary() {}

    public static final Map<String, String> TERMS = new LinkedHashMap<>();
    static {
        TERMS.put("Approval", "The share of Americans who approve of the job you are doing, blended from three groups: your voters, swing voters and the other party's voters. It drifts with the economy, events and what you do, and sets your standing at day 100.");
        TERMS.put("Political capital", "Your stock of favours and momentum. Concessions to Congress spend it; passing laws and confirming nominees earn it back. A bigger electoral mandate starts you with more.");
        TERMS.put("Attention", "Your time: 3 points each turn. Most actions take one. Unused attention does not carry over. Delegating an area to your chief of staff costs none, but the staff works at half strength.");
        TERMS.put("Whip count", "The expected yes votes, bloc by bloc, if the vote were held now. A floor vote adds some noise, so the pass chance is a probability, not a promise. The leader will not call a vote under 35% unless you insist (8 capital).");
        TERMS.put("Reconciliation", "A budget procedure: the Senate passes the bill with 51 votes instead of 60. Once a year, only with your party holding both chambers, not for bills the Byrd rule excludes; it strips 15% of the bill and costs opposition goodwill.");
        TERMS.put("Opposition strategy", "How the other party's leaders treat you: cooperate, selective or obstruct. It follows your approval and their goodwill toward you, and it sets committee schedules, stopgap odds and how hard their members are to win.");
        TERMS.put("Goodwill", "How the opposition's leaders feel about you. Meeting them, crossover nominees and clean deals raise it; going public, reconciliation and partisan stopgaps lower it.");
        TERMS.put("Party wings", "The two wings of your party: the base and the moderates. Their mood moves with the bills you write, the nominees you pick and the promises you keep. Below 32 a wing revolts.");
        TERMS.put("Deterrence", "How credible American power looks to rivals, 0 to 100. Shows of force and defence spending raise it; hesitation in a crisis and oil releases lower it. Higher deterrence calms flashpoints over time.");
        TERMS.put("Intelligence estimate", "The intelligence community's read of the other side's resolve, with a margin of error that narrows with an able Secretary of State. It is an estimate: the true resolve is hidden.");
        TERMS.put("Polls", "What a poll of each state shows this week. Polls carry noise and a hidden systematic miss shared by every state, so the polls can all be wrong in the same direction on Election Day.");
        TERMS.put("Forecast", "Your chance of winning if the polls are as accurate as they have been historically. It knows only the polls, not how strong your opponent's closing campaign will be.");
        TERMS.put("Tipping point", "The state that would deliver the 270th electoral vote if every state voted the way its poll says, counting from the safest state down.");
        TERMS.put("Standing orders", "How your staff runs the campaign each week: a strategy, a spending rate and a message. Change them any week, or plan a single week by hand.");
        TERMS.put("Field and ads", "Ads move voters quickly and fade within weeks; field organisation builds slowly and lasts. Both have diminishing returns against your opponent's effort in the same state.");
        TERMS.put("Promises", "Bills you promise to pass. Each wins a little support now; they become your agenda, the first one your flagship bill, and each one you drop costs your base wing.");
        TERMS.put("Mandate", "Your share of the electoral college. Every electoral vote above 270 adds political capital and goodwill among swing voters at the start of your term.");
        TERMS.put("Misery index", "Unemployment plus inflation: the rough measure of economic pain voters feel.");
        TERMS.put("Deficit", "Federal borrowing as a share of the economy. Bond markets price it in, raising the 10-year yield and mortgage rates, and the Fed weighs it when it sets rates.");
        TERMS.put("Undo and rewind", "Undo reverses your last action exactly: the game replays without it, so the same choice will always come out the same way. Rewind returns to the start of any earlier turn.");
    }

    public static String of(String term) { return TERMS.getOrDefault(term, term); }
}
