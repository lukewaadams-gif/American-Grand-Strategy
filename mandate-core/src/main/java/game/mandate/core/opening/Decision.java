package game.mandate.core.opening;

import java.util.List;

/**
 * A choice the player owes before the week can end: a crisis, a deadline, or the follow-up to a failure.
 * Each option carries a plain-language preview of its known consequences and, where relevant, its odds.
 */
public record Decision(int id, Kind kind, String title, String situation, List<Option> options) {

    public enum Kind { CRISIS, FUNDING, SHUTDOWN, BILL_FAILED, BOTTLED_IN_COMMITTEE, NOMINEE_REJECTED, FACTION_REVOLT, AFTERMATH, LEAK }

    public record Option(String label, String preview) {}

    public Decision {
        options = List.copyOf(options);
    }
}
