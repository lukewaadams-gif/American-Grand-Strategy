package game.mandate.core.campaign;

/**
 * What the candidate does in one slot of the week (morning, afternoon or evening), ported from Battleground 2028
 * (ACT_NAME, ACT_COST, ACT_FATIGUE, ACT_LOCAL, ACT_DESC: Battleground2028.java l.1658-1690). Costs are in $M at
 * general-election prices; fatigue is in stamina points before the candidate's stamina rating is applied. BG's
 * "Endorsement meeting" is left out: this game has no endorsers to court.
 */
public enum Activity {
    NONE("Unscheduled", "-", 0, 0, false, Kind.NONE, "Nothing planned in this slot."),
    RALLY("Rally", "RALLY", 0.35, 9, true, Kind.CROWD,
            "A big rally: fires up the base, recruits volunteers and dominates local news. Scales with charisma, speeches and volunteer energy."),
    TOWN_HALL("Town hall", "TOWN HALL", 0.09, 6, true, Kind.PERSUADE,
            "A town hall with undecided voters: persuasion in the state, but unscripted questions risk a gaffe."),
    RETAIL("Retail stops", "RETAIL", 0.025, 7, true, Kind.PERSUADE,
            "Diners, fairs and handshakes. Small but sticky persuasion."),
    FUNDRAISER("High-dollar fundraiser", "FUNDRAISER", 0.06, 4, true, Kind.MONEY,
            "A high-dollar dinner. Raises the most in donor hubs (New York, California, Texas, Florida, Illinois, DC and others). Optics risk if overused."),
    CALL_TIME("Bundler meetings & call time", "CALL TIME", 0, 3, false, Kind.MONEY,
            "Meetings with bundlers and hours on the phone with donors, from anywhere. Raises money quietly; scales with the big-donor network."),
    PODCAST("Podcast / long-form", "PODCAST", 0.01, 4, false, Kind.MEDIA,
            "A long-form podcast or interview. Reaches young men and the politically disengaged nationwide; rewards authenticity and intellect."),
    TV("TV interview", "TV", 0.005, 3, false, Kind.MEDIA, "Sunday shows, cable and late-night. National exposure; tests discipline."),
    CROSSOVER("Crossover media", "CROSSOVER", 0.005, 5, false, Kind.MEDIA,
            "An interview on the other side's turf. Crossover appeal, real risk."),
    SPEECH("Policy speech", "SPEECH", 0.12, 5, true, Kind.CROWD,
            "A major policy address on your message issue. Builds credibility on that issue and raises its salience."),
    UNION("Union hall", "UNION", 0.04, 6, true, Kind.GROUPS, "Speak at a union hall: union households and working-class voters in the state."),
    CHURCH("Faith community", "CHURCH", 0.02, 5, true, Kind.GROUPS,
            "Visit a church or faith community (Black churches, evangelical, Catholic, synagogues, mosques)."),
    CAMPUS("Campus event", "CAMPUS", 0.06, 6, true, Kind.GROUPS, "A campus event: students and young voters; volunteers."),
    COMMUNITY("Community event", "COMMUNITY", 0.04, 6, true, Kind.GROUPS,
            "An event in Black, Hispanic, Asian American or other communities in the state."),
    PREP("Debate prep", "PREP", 0.05, 5, false, Kind.PREP, "Debate preparation and mock debates. Essential before debates; diminishing returns."),
    REST("Rest", "REST", 0, -14, false, Kind.REST, "Rest. Restores stamina; tired candidates make mistakes."),
    DINNER("Party dinner", "DINNER", 0.03, 5, true, Kind.PERSUADE, "A state party dinner with activists and local leaders.");

    /** Families of activity, for the schedule's colour strip. */
    public enum Kind { NONE, CROWD, PERSUADE, MONEY, MEDIA, GROUPS, PREP, REST }

    /** The congregations a faith-community visit can reach (BG Week.CHURCH_KIND / CHURCH_GROUPS). */
    public static final String[] CHURCH_KIND = {"Black church", "Evangelical", "Catholic parish", "Synagogue", "Mosque"};
    static final int[] CHURCH_GROUPS = {10, 6, 7, 17, 18};
    /** The communities a community event can reach (BG Week.COMMUNITY_KIND / COMMUNITY_GROUPS). */
    public static final String[] COMMUNITY_KIND = {"Black community", "Hispanic community", "Asian American community", "Tribal nation",
            "Arab American community", "LGBTQ community", "Veterans"};
    static final int[] COMMUNITY_GROUPS = {10, 12, 16, 20, 18, 27, 25};

    private final String label, shortLabel, description;
    private final double cost, fatigue;
    private final boolean local;
    private final Kind kind;

    Activity(String label, String shortLabel, double cost, double fatigue, boolean local, Kind kind, String description) {
        this.label = label;
        this.shortLabel = shortLabel;
        this.cost = cost;
        this.fatigue = fatigue;
        this.local = local;
        this.kind = kind;
        this.description = description;
    }

    public String label() { return label; }
    public String shortLabel() { return shortLabel; }
    public String description() { return description; }
    /** Event cost in $M. */
    public double cost() { return cost; }
    /** Stamina cost before the candidate's stamina rating (negative restores). */
    public double fatigue() { return fatigue; }
    /** Does it happen in a state (true) or from wherever the candidate is (false)? */
    public boolean local() { return local; }
    public Kind kind() { return kind; }

    /** How many choices the activity's parameter has (the congregation, the community or the show); 0 if none. */
    public int parameterChoices() {
        return switch (this) {
            case CHURCH -> CHURCH_KIND.length;
            case COMMUNITY -> COMMUNITY_KIND.length;
            case PODCAST -> Show.ALL.size();
            default -> 0;
        };
    }

    /** The name of a parameter choice, or null. */
    public String parameterName(int p) {
        return switch (this) {
            case CHURCH -> CHURCH_KIND[Math.max(0, Math.min(CHURCH_KIND.length - 1, p))];
            case COMMUNITY -> COMMUNITY_KIND[Math.max(0, Math.min(COMMUNITY_KIND.length - 1, p))];
            case PODCAST -> Show.ALL.get(Math.max(0, Math.min(Show.ALL.size() - 1, p))).name();
            default -> null;
        };
    }
}
