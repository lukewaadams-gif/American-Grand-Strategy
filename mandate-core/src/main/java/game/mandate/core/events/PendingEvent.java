package game.mandate.core.events;

/** An event awaiting the player's choice. {@code instanceId} is unique within a game. */
public record PendingEvent(int instanceId, EventDefinition definition) {}
