package game.mandate.core.campaign;

import game.mandate.core.data.Csv;
import game.mandate.core.state.Party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The campaign's team and surrogates (docs/CAMPAIGN_WAR_ROOM.md, "Team"): the staff a candidate can hire for five senior
 * posts and the advance leads who run surrogate tours ({@code data/campaign_staff.csv}), and the surrogates themselves:
 * the running mate, the spouse and two allies from the party.
 */
public final class CampaignStaff {

    /** A senior post, or an advance lead (who runs a surrogate's tour). */
    public enum Post {
        MANAGER("Campaign manager", "The whole operation: every plan works a little harder"),
        POLLSTER("Pollster", "Internal polls: a better pollster misses by less"),
        MEDIA("Media director", "The ads: TV and digital work harder"),
        FIELD("Field director", "The ground game: volunteers and organisers go further"),
        FINANCE("Finance director", "Fundraising: every event and call raises more"),
        ADVANCE("Advance lead", "Runs a surrogate's tour: a better lead makes a better stop, and a careless one a bad story");

        public final String label, effect;

        Post(String label, String effect) { this.label = label; this.effect = effect; }

        public static final List<Post> SENIOR = List.of(MANAGER, POLLSTER, MEDIA, FIELD, FINANCE);
    }

    /**
     * One person on the staff.
     *
     * @param party  D, R or null for either
     * @param salary $M a week, on top of headquarters
     * @param risk   the chance in a tour-week of a mishap on the trail (always 0 for a real person)
     */
    public record Member(String id, Party party, String name, Post post, int skill, double salary, boolean real, double risk, String note) {
        /** How much harder this post's work goes: 1 at skill 5, 3% a point. */
        public double factor() { return 1 + 0.03 * (skill - 5); }
    }

    /** The surrogates' four slots: the running mate, the spouse, and two allies. */
    public static final int SURROGATES = 4;
    public static final String[] SLOT_ROLES = {"Running mate", "Spouse", "Ally", "Ally"};
    /** A surrogate's week: a state (its index), fundraising, or nothing. */
    public static final int NONE = -1, FUNDRAISE = -2;

    private final List<Member> all;

    private CampaignStaff(List<Member> all) { this.all = all; }

    public static CampaignStaff load() {
        List<Member> l = new ArrayList<>();
        for (String[] f : Csv.readResource("/data/campaign_staff.csv")) {
            Party p = f[1].equals("D") ? Party.DEMOCRAT : f[1].equals("R") ? Party.REPUBLICAN : null;
            l.add(new Member(f[0], p, f[2], Post.valueOf(f[3]), Integer.parseInt(f[4]), Double.parseDouble(f[5]), f[6].equals("1"),
                    Double.parseDouble(f[7]), f[8]));
        }
        return new CampaignStaff(Collections.unmodifiableList(l));
    }

    public static String defaultId(Post p) { return "staff_default_" + p.name().toLowerCase(java.util.Locale.ROOT); }

    public Member get(String id) {
        for (Member m : all) if (m.id().equals(id)) return m;
        throw new IllegalArgumentException("No such staff member: " + id);
    }

    /** Who can be hired for a post by a party: the default staff first, then the rest by skill. Real people only with the real cast. */
    public List<Member> pool(Post post, Party party, boolean realRoster) {
        List<Member> l = new ArrayList<>();
        for (Member m : all) if (m.post() == post && (m.party() == null || m.party() == party) && (realRoster || !m.real())) l.add(m);
        l.sort((a, b) -> a.id().equals(defaultId(post)) ? -1 : b.id().equals(defaultId(post)) ? 1 : b.skill() != a.skill() ? b.skill() - a.skill() : a.id().compareTo(b.id()));
        return l;
    }

    // ---- the surrogates ------------------------------------------------------------------------------------------------

    /** The census region of a state, for a surrogate's home-region pull. */
    public static String region(String code) {
        String ne = "CT ME MA NH RI VT NJ NY PA", mw = "IL IN MI OH WI IA KS MN MO NE ND SD",
                s = "DE DC FL GA MD NC SC VA WV AL KY MS TN AR LA OK TX";
        String c = " " + code + " ";
        if ((" " + ne + " ").contains(c)) return "Northeast";
        if ((" " + mw + " ").contains(c)) return "Midwest";
        if ((" " + s + " ").contains(c)) return "South";
        return "West";
    }

    /** The default running mate: the strongest of the party's field, not on either ticket, of the same cast. */
    public static Candidate defaultRunningMate(CandidateCatalog cat, Candidate me, Candidate them) {
        List<Candidate> l = field(cat, me, them, null);
        l.sort((a, b) -> Double.compare(b.quality(), a.quality()) != 0 ? Double.compare(b.quality(), a.quality()) : a.id().compareTo(b.id()));
        return l.isEmpty() ? null : l.get(0);
    }

    /** The two allies: the party's most charismatic figures, not on either ticket. */
    public static List<Candidate> allies(CandidateCatalog cat, Candidate me, Candidate them, Candidate mate) {
        List<Candidate> l = field(cat, me, them, mate);
        l.sort((a, b) -> b.charisma() != a.charisma() ? b.charisma() - a.charisma() : a.id().compareTo(b.id()));
        return l.subList(0, Math.min(2, l.size()));
    }

    /** The running mate's shortlist: the party's field, strongest first. */
    public static List<Candidate> shortlist(CandidateCatalog cat, Candidate me, Candidate them) {
        List<Candidate> l = field(cat, me, them, null);
        l.sort((a, b) -> Double.compare(b.quality(), a.quality()) != 0 ? Double.compare(b.quality(), a.quality()) : a.id().compareTo(b.id()));
        return l.subList(0, Math.min(8, l.size()));
    }

    private static List<Candidate> field(CandidateCatalog cat, Candidate me, Candidate them, Candidate mate) {
        List<Candidate> l = new ArrayList<>();
        for (Candidate c : cat.forParty(me.party())) {
            if (c.id().equals(me.id()) || them != null && c.id().equals(them.id()) || mate != null && c.id().equals(mate.id())) continue;
            if (c.id().equals(Candidate.CUSTOM_ID) || c.isReal() != me.isReal()) continue;
            l.add(c);
        }
        return l;
    }

    /** A surrogate's pull: their charisma, 0.3 to 1 (the spouse a steady 0.6). */
    public static double star(Candidate c) { return c == null ? 0.6 : Math.max(0.3, Math.min(1.0, c.charisma() / 10.0)); }

    /** A moderate persuades the undecided; a firebrand fires up the base. */
    public static boolean persuader(Candidate c) { return c == null || Math.abs(c.ideology()) < 0.4; }
}
