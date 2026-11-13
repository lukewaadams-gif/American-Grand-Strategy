package game.mandate.core.campaign;

import game.mandate.core.data.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The air war's fixed tables, ported from Battleground 2028 (Battleground2028.java l.1835-1858, l.6840-6858, l.15991-16024). */
public final class AirWar {
    private AirWar() {}

    /** A broadcast spot's tone: how hard it works per rating point (BG Week.advertising, l.3784). */
    public enum Tone {
        POSITIVE("Positive", 0.9, "Builds your image. Safe."),
        CONTRAST("Contrast", 1.1, "You against them, side by side. Effective, with moderate risk."),
        NEGATIVE("Negative", 1.25, "Defines your opponent. The most powerful, and the most likely to backfire.");

        public final String label, description;
        public final double effect;

        Tone(String label, double effect, String description) { this.label = label; this.effect = effect; this.description = description; }
    }

    /** National TV buys (BG NATIONAL_TV, NATIONAL_CPP): cost per rating point in $M, and how much of a point each state feels. */
    public enum Network {
        NETWORK("National network (sports and prime time)", 0.052, 0.9),
        CABLE("Cable news", 0.013, 0.6),
        SPANISH("Spanish-language networks", 0.0065, 0.9);

        public final String label;
        public final double costPerPoint, reach;

        Network(String label, double costPerPoint, double reach) { this.label = label; this.costPerPoint = costPerPoint; this.reach = reach; }
    }

    /** Digital platforms (BG PLAT, PLAT_CPM, PLAT_AGE): cost per thousand impressions in dollars, and reach by age (18-29, 30-64, 65+). */
    public enum Platform {
        FACEBOOK("Facebook", 14, 0.45, 1.05, 1.6, "The oldest audience online. Strong with voters over 50 and in rural areas."),
        INSTAGRAM("Instagram", 12, 1.8, 1.05, 0.3, "Millennials and Gen Z, more women than men."),
        YOUTUBE("YouTube", 20, 1.5, 1.1, 0.55, "Under-45s, especially young men; pre-roll before sports, gaming and podcasts."),
        CREATORS("TikTok and creators", 22, 2.7, 0.75, 0.12, "TikTok bans political ads, so campaigns pay creators. Reaches Gen Z like nothing else."),
        STREAMING("Streaming TV", 40, 0.6, 1.1, 1.15, "Cord-cutters aged 30 and up. Pricey, but TV-quality spots."),
        SEARCH("Google Search", 30, 1.0, 1.0, 0.8, "People already looking. Cheap names for fundraising lists and strong for turnout reminders.");

        public final String label, description;
        public final double cpm;
        private final double[] age;

        Platform(String label, double cpm, double young, double middle, double old, String description) {
            this.label = label; this.cpm = cpm; this.age = new double[]{young, middle, old}; this.description = description;
        }

        public double ageReach(int a) { return age[a]; }
    }

    /** What a platform's ads say (BG DMSG, DMSG_PERSUADE). */
    public enum DigitalMessage {
        BIO("Biography", 0.9, "Introduce the candidate: who they are and what they've done. Builds favourability."),
        ISSUE("Issue focus", 1.0, "Your message issue. Persuades voters who care about it and builds your credibility on it."),
        ATTACK("Attack", 1.15, "Hit your opponent. The most persuasive message, and it can backfire."),
        GOTV("Get out the vote", 0.25, "Reminders to vote. Little persuasion, but turns out your supporters in the final six weeks."),
        FUND("Fundraising", 0.15, "Ask for money. Builds your small-dollar list and raises some cash; persuades almost no one.");

        public final String label, description;
        public final double persuasion;

        DigitalMessage(String label, double persuasion, String description) { this.label = label; this.persuasion = persuasion; this.description = description; }
    }

    /** Who delivers an Ad Studio ad (BG AdStudio.MESSENGER). */
    public enum Messenger {
        CANDIDATE("The candidate, to camera", "Plays on charisma and authenticity."),
        VOTERS("Real voters", "Testimonials: authentic, works best with persuadable groups."),
        VALIDATOR("A validator", "A veteran, nurse, farmer or sheriff vouches for you. Lends credibility on the issue."),
        NARRATOR("A narrator", "Polished and safe; best for attack ads.");

        public final String label, description;

        Messenger(String label, String description) { this.label = label; this.description = description; }
    }

    /** The Ad Studio's tones: positive, contrast, attack (the same three as broadcast). */
    public static final String[] STUDIO_TONE = {"Positive", "Contrast", "Attack"};
    /** The eight groups a focus group reports on (BG AdStudio.KEY_GROUPS). */
    public static final int[] KEY_GROUPS = {21, 8, 9, 12, 10, 0, 1, 4};
    /** The ad library keeps the eight newest (BG AdStudio.build). */
    public static final int LIBRARY = 8;
    /** $M to produce an ad, and to show it to a focus group (BG l.16162, TEST_COST). */
    public static final double PRODUCE_COST = 0.12, TEST_COST = 0.045;

    static final String[] QUOTE_GOOD = {"\"That's the first ad this year that actually talked to me.\"", "\"I'd vote for that person.\"",
            "\"It felt real. Not like a politician.\"", "\"Finally someone's talking about what it costs to live.\"", "\"I'll remember that one.\""};
    static final String[] QUOTE_MID = {"\"It was fine. They all say that.\"", "\"I zoned out halfway through.\"",
            "\"OK, but what would they actually do?\"", "\"Didn't change my mind either way.\""};
    static final String[] QUOTE_BAD = {"\"That was nasty. It makes me like them less.\"", "\"I don't believe a word of it.\"",
            "\"Who is this ad even for?\"", "\"Too slick. It felt like a sales pitch.\""};

    /**
     * An ad made in the Ad Studio. Its true quality is hidden; a focus group gives a noisy read ({@code tested}, -1 until
     * tested), the reactions of eight key groups and three quotes.
     *
     * @param tone  0 positive, 1 contrast, 2 attack; {@code group} is a BG voter group, or -1 for everyone
     */
    public record Ad(String name, int tone, Issue issue, int group, Messenger messenger, double quality, double tested,
                     double[] groupScores, List<String> quotes, String made) {
        public Ad {
            groupScores = groupScores == null ? null : groupScores.clone();
            quotes = quotes == null ? List.of() : List.copyOf(quotes);
        }

        public boolean isTested() { return tested >= 0; }
        public double groupScore(int i) { return groupScores == null ? 0 : groupScores[i]; }

        Ad withTest(double read, double[] scores, List<String> q) { return new Ad(name, tone, issue, group, messenger, quality, read, scores, q, made); }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name); m.put("tone", tone); m.put("issue", issue.name()); m.put("group", group); m.put("messenger", messenger.name());
            m.put("quality", quality); m.put("tested", tested);
            List<Object> g = new ArrayList<>();
            if (groupScores != null) for (double v : groupScores) g.add(v);
            m.put("groups", groupScores == null ? null : g);
            m.put("quotes", new ArrayList<>(quotes)); m.put("made", made);
            return m;
        }

        static Ad fromJson(Map<String, Object> m) {
            double[] g = null;
            if (m.get("groups") != null) {
                List<Object> l = Json.array(m.get("groups"));
                g = new double[l.size()];
                for (int i = 0; i < g.length; i++) g[i] = Json.num(l.get(i));
            }
            List<String> q = new ArrayList<>();
            for (Object o : Json.array(m.get("quotes"))) q.add((String) o);
            return new Ad((String) m.get("name"), Json.integer(m.get("tone")), Issue.valueOf((String) m.get("issue")), Json.integer(m.get("group")),
                    Messenger.valueOf((String) m.get("messenger")), Json.num(m.get("quality")), Json.num(m.get("tested")), g, q, (String) m.get("made"));
        }
    }
}
