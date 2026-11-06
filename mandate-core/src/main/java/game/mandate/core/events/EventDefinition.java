package game.mandate.core.events;

import game.mandate.core.game.Game;
import game.mandate.core.time.Phase;

import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * A content-defined event. {@code chancePerTurn} reads the game (read-only by convention) and returns
 * the probability the event fires this turn, so events respond to the state the player created:
 * a shutdown threat only under divided government, a market panic more likely when the economy is weak.
 */
public record EventDefinition(String id, String title, String body, Set<Phase> phases, boolean repeatable,
                              ToDoubleFunction<Game> chancePerTurn, List<EventOption> options) {

    public EventDefinition {
        phases = Set.copyOf(phases);
        options = List.copyOf(options);
        if (options.isEmpty()) throw new IllegalArgumentException(id + " has no options");
    }
}
