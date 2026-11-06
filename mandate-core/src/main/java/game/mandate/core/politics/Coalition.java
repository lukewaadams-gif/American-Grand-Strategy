package game.mandate.core.politics;

/** Voter coalitions, relative to the president's party. National approval is their weighted sum. */
public enum Coalition {
    BASE("Your voters"), SWING("Swing voters"), OPPOSITION("Opposition voters");

    private final String label;

    Coalition(String label) { this.label = label; }

    public String label() { return label; }
}
