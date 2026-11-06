package game.mandate.core.command;

import game.mandate.core.campaign.CampaignWeekPlan;
import game.mandate.core.campaign.DebatePosture;
import game.mandate.core.campaign.Issue;
import game.mandate.core.congress.LegislativeStrategy;
import game.mandate.core.diplomacy.HotSpot;

import java.util.List;

/**
 * Every player decision is a command. The game state changes only through commands (and the turn
 * resolution that {@link EndTurn} triggers), so a saved game is its setup plus its command log, replays
 * are exact, and the UI, the CLI, the tutorial and the autopilot all drive the same API.
 */
public sealed interface Command {

    /** Campaign: this week's schedule, spending and message. */
    record PlanCampaign(CampaignWeekPlan plan) implements Command {}

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

    /** Answer a pending event. */
    record ResolveEvent(int instanceId, int optionIndex) implements Command {}

    /** Resolve the turn. Refused while events are pending. */
    record EndTurn() implements Command {}
}
