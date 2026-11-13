package game.mandate.core.campaign;

import java.util.List;

/**
 * A podcast or long-form show a candidate can be booked on. Battleground 2028 lists 28 real shows (SHOWS,
 * Battleground2028.java l.6998); this game keeps each show's tier, typical views per candidate episode, audience lean
 * and audience groups, but describes the show instead of naming a real programme or host.
 *
 * @param tier    0 small, 1 mid-size, 2 top tier: bigger shows book only candidates voters know (BG TIER_NEED)
 * @param views   typical views of a candidate episode, millions
 * @param lean    audience lean: negative left, positive right
 * @param groups  the BG voter groups the audience over-represents, with weights
 */
public record Show(String name, String audience, int tier, double views, double lean, int[] groups, double[] weights) {
    public static final String[] TIER_NAME = {"Small", "Mid-size", "Top tier"};
    /** Name recognition needed to be booked, by tier (BG TIER_NEED). */
    static final double[] TIER_NEED = {0, 0.15, 0.35};
    /** Weeks before a show will have the candidate back, by tier (BG REBOOK). */
    static final int[] REBOOK_WEEKS = {3, 8, 20};

    public static final List<Show> ALL = List.of(
            show("A top long-form talk show", "Comedy, fighting and three-hour conversations; young men", 2, 25, 0.35, "2:1 5:0.6 8:0.4 22:0.3"),
            show("A working-class comedy podcast", "Comedy; young working-class men", 2, 12, 0.35, "2:1 8:0.5 22:0.4"),
            show("A comedy roundtable", "Comedy roundtable; young men, many Black and Hispanic", 2, 10, 0.25, "2:1 11:0.5 13:0.5"),
            show("An influencer stunt show", "Pranks, sports and influencers; very young men", 2, 10, 0.5, "2:1 0:0.5"),
            show("A sports and culture interview show", "Sports and culture; Black audiences", 2, 9, 0.0, "10:1 11:0.8 5:0.3"),
            show("An influencer talk show", "Influencer talk; young men", 2, 6, 0.3, "2:1 0:0.5"),
            show("A science and technology marathon", "Science, tech and four-hour interviews", 2, 6, 0.1, "9:0.6 2:0.5 16:0.3"),
            show("A relationships show", "Relationships and culture; young women", 2, 5, -0.4, "3:1 0:0.5 4:0.3"),
            show("A business and self-improvement show", "Business and self-improvement", 2, 5, 0.0, "9:0.4 0:0.4 29:0.3"),
            show("A football and family podcast", "Football and family; sports fans and women", 2, 4, -0.1, "4:0.5 5:0.5 21:0.3"),
            show("A hip-hop morning show", "Hip-hop radio and video; Black listeners", 1, 2.5, -0.3, "10:1 11:0.6 23:0.4"),
            show("A veterans and security podcast", "Veterans, security and survival", 1, 3, 0.5, "25:1 26:0.6 5:0.4"),
            show("A celebrity comedy podcast", "Celebrity comedy; affluent suburbanites", 1, 2, -0.4, "21:0.6 9:0.6 4:0.3"),
            show("A sports and beer show", "Sports and beer; young men", 1, 2, 0.4, "2:0.8 5:0.5 22:0.3"),
            show("A tech investors' podcast", "Tech investors on business and politics", 1, 1.5, 0.45, "9:0.6 16:0.3 5:0.3"),
            show("A conservative talk show", "Conservative news and talk", 1, 1.5, 0.8, "6:0.5 4:0.4 1:0.4"),
            show("A conservative commentary show", "Conservative news and commentary", 1, 1.5, 0.9, "6:0.6 5:0.4 17:0.3"),
            show("A sports and pop-culture podcast", "Sports and pop culture", 1, 1.2, -0.1, "5:0.7 9:0.4"),
            show("A progressive politics podcast", "Progressive politics", 1, 1.0, -0.9, "0:0.4 9:0.6 23:0.4"),
            show("A policy and ideas interview", "Policy and ideas", 1, 0.8, -0.7, "9:0.8 23:0.3"),
            show("A populist news show", "Populist news from the left and the right", 0, 0.6, 0.0, "8:0.5 0:0.4"),
            show("A podcast for disaffected conservatives", "Disaffected conservatives and independents", 0, 0.4, -0.5, "21:0.6 9:0.5"),
            show("A Black culture and politics show", "Black culture and politics", 0, 0.4, -0.4, "10:0.8 0:0.4"),
            show("A progressive humour podcast", "Progressive anger and humour", 0, 0.4, -0.9, "4:0.5 23:0.3"),
            show("A heterodox ideas podcast", "Heterodox ideas", 0, 0.35, 0.2, "9:0.5 17:0.4"),
            show("A sports investigations show", "Sports investigations", 0, 0.3, -0.1, "5:0.5 0:0.3"),
            show("A technology news podcast", "Technology news", 0, 0.25, -0.3, "9:0.5 0:0.3"),
            show("A local politics podcast", "The current state's political junkies", 0, 0.05, 0.0, "21:0.3 9:0.3"));
    /** The local show is always available and reaches only the state the candidate is in (BG LOCAL_SHOW). */
    public static final int LOCAL = 27;

    private static Show show(String name, String audience, int tier, double views, double lean, String groups) {
        String[] t = groups.split(" ");
        int[] g = new int[t.length];
        double[] w = new double[t.length];
        for (int i = 0; i < t.length; i++) { String[] p = t[i].split(":"); g[i] = Integer.parseInt(p[0]); w[i] = Double.parseDouble(p[1]); }
        return new Show(name, audience, tier, views, lean, g, w);
    }
}
