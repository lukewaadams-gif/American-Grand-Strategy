package game.mandate.core.command;

import game.mandate.core.campaign.CampaignStrategy;
import game.mandate.core.campaign.CampaignWeekPlan;
import game.mandate.core.campaign.DebatePosture;
import game.mandate.core.campaign.Issue;
import game.mandate.core.congress.LegislativeStrategy;
import game.mandate.core.diplomacy.HotSpot;
import game.mandate.core.opening.OpeningAction;

import java.util.List;

/**
 * Every player decision is a command. The game state changes only through commands (and the turn
 * resolution that {@link EndTurn} triggers), so a saved game is its setup plus its command log, replays
 * are exact, and the UI, the CLI, the tutorial and the autopilot all drive the same API.
 */
public sealed interface Command {

    /** Campaign: a hand-built week from the original campaign screen (rallies, fundraisers, ad and field money, message). */
    record PlanCampaign(CampaignWeekPlan plan) implements Command {}

    /** Campaign: standing orders (strategy, spending rate, message) the staff turns into each week's plan. */
    record SetCampaignOrders(CampaignStrategy.Orders orders) implements Command {}

    /** Campaign: one change to the candidate's schedule, the air war, the ground game, the staff or the Ad Studio. */
    record Campaign(game.mandate.core.campaign.CampaignOrder order) implements Command {}

    /** Campaign: promise to pass a bill. Promises become the agenda's first priorities; breaking one costs the base. */
    record Pledge(String billId) implements Command {}

    /** Campaign: approach for this week's debate. */
    record ChooseDebatePosture(DebatePosture posture) implements Command {}

    /** Transition: up to three agenda priorities (bill ids). */
    record SetAgenda(List<String> billIds) implements Command {
        public SetAgenda { billIds = List.copyOf(billIds); }
    }

    /** Governing: push a bill this month, committing political capital to the whip operation. */
    record PushBill(String billId, LegislativeStrategy strategy, double capital) implements Command {}

    /** Governing: use the bully pulpit on one issue. */
    record PublicAddress(Issue issue) implements Command {}

    /** Governing: forward posture, exercises and munitions buys in the Pacific. */
    record BolsterDeterrence() implements Command {}

    /** Governing: personal diplomacy to cool a flashpoint. */
    record Diplomacy(HotSpot hotSpot) implements Command {}

    /** First hundred days: one presidential action (draft, negotiate, nominate, decide, end the week...). */
    record Act(OpeningAction action) implements Command {}

    /** Answer a pending event. */
    record ResolveEvent(int instanceId, int optionIndex) implements Command {}

    /** Resolve the turn. Refused while events are pending. */
    record EndTurn() implements Command {}

    /** In office: one change to the president's week or to the watch plan. */
    record Presidency(game.mandate.core.presidency.PresidencyOrder order) implements Command {}
    /** Something the president does in the Roosevelt Room: drafting, deals, the desk, the budget, deadlines. */
    record Hill(game.mandate.core.hill.HillOrder order) implements Command {}
}
