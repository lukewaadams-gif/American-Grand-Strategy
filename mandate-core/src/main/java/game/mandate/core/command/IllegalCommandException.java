package game.mandate.core.command;

/** A command that is not legal in the current state. The state is unchanged when this is thrown. */
public final class IllegalCommandException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public IllegalCommandException(String message) { super(message); }
}
