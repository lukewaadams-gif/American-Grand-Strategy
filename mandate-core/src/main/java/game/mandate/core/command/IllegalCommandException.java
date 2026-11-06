package game.mandate.core.command;

/** A command that is not legal in the current state. The state is unchanged when this is thrown. */
public final class IllegalCommandException extends RuntimeException {
    public IllegalCommandException(String message) { super(message); }
}
