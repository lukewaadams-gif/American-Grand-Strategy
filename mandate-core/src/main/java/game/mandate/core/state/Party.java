package game.mandate.core.state;

/** The two major parties. Third parties are a post-MVP feature (see FEATURE_PRIORITY.md). */
public enum Party {
    DEMOCRAT("D", "Democratic"),
    REPUBLICAN("R", "Republican");

    private final String code;
    private final String adjective;

    Party(String code, String adjective) {
        this.code = code;
        this.adjective = adjective;
    }

    public String code() { return code; }
    public String adjective() { return adjective; }

    public Party opponent() { return this == DEMOCRAT ? REPUBLICAN : DEMOCRAT; }

    /** +1 for Democrats, -1 for Republicans: margins in the core are always stored as D minus R. */
    public int sign() { return this == DEMOCRAT ? 1 : -1; }
}
