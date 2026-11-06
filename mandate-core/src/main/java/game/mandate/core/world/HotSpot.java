package game.mandate.core.world;

/** Foreign flashpoints tracked as tension 0-100. The MVP models three; more are data, not code. */
public enum HotSpot {
    TAIWAN_STRAIT("Taiwan Strait"),
    MIDDLE_EAST("Middle East"),
    EASTERN_EUROPE("Eastern Europe");

    private final String label;

    HotSpot(String label) { this.label = label; }

    public String label() { return label; }
}
