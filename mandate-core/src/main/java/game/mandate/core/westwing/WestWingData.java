package game.mandate.core.westwing;

import game.mandate.core.data.Csv;
import game.mandate.core.state.Party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Resolute Desk's tables for the West Wing and the press (executive/Posts, Traits, ActionData, CourtData, ScandalData,
 * VicePresData, press/SpeechData, QA, home/PoliticsData and the inline strings of Trouble, Courts and Implement), and the
 * exported data: the order library ({@code rd/actions.tsv}), the outlets ({@code rd/outlets.tsv}), the storylines
 * ({@code rd/storylines.tsv}), the name pools ({@code rd/names.tsv}) and the Court ({@code justices.tsv}).
 */
public final class WestWingData {
    // ---- the staff -------------------------------------------------------------------------------------------------
    public static final int S_COS = 0, S_NSA = 1, S_PRESS = 2, S_COUNSEL = 3, S_NEC = 4, S_DPC = 5, S_LEG = 6, S_POL = 7, S_COMMS = 8;
    public static final String[] STAFF_TITLE = {"Chief of staff", "National security adviser", "Press secretary", "White House counsel", "Economic adviser",
            "Domestic policy adviser", "Legislative director", "Political director", "Communications director"};
    public static final String[] STAFF_DOES = {
            "Runs the building and your schedule. A good one lowers your stress and keeps the staff from leaking.",
            "Coordinates foreign policy and the Situation Room.",
            "Briefs the press every day. A good one takes the sting out of bad stories.",
            "Your lawyer for the office. Keeps orders on solid legal ground and handles investigations.",
            "Runs the National Economic Council. Sharpens your economic message.",
            "Runs the Domestic Policy Council. Makes your policy time count for more.",
            "Your link to Congress. Makes deals stick and keeps relationships warm.",
            "Watches the map and the money. Helps your fundraising and the midterms.",
            "Plans your message. Makes your remarks and speeches land harder."};

    // ---- the cabinet ---------------------------------------------------------------------------------------------------
    public static final int P_STATE = 0, P_TREAS = 1, P_DEF = 2, P_DOJ = 3, P_INT = 4, P_AG = 5, P_COM = 6, P_LAB = 7, P_HHS = 8, P_HUD = 9,
            P_DOT = 10, P_DOE = 11, P_ED = 12, P_VA = 13, P_DHS = 14, P_EPA = 15, P_OMB = 16, P_USTR = 17, P_UN = 18, P_DNI = 19, P_CIA = 20;
    public static final String[] POST_NAME = {"State", "Treasury", "Defense", "Justice", "Interior", "Agriculture", "Commerce", "Labor", "Health and Human Services",
            "Housing and Urban Development", "Transportation", "Energy", "Education", "Veterans Affairs", "Homeland Security",
            "Environmental Protection Agency", "Office of Management and Budget", "Trade Representative", "Ambassador to the UN", "Director of National Intelligence", "CIA"};
    public static final String[] POST_SHORT = {"State", "Treasury", "Defense", "Justice", "Interior", "Agriculture", "Commerce", "Labor", "HHS", "HUD",
            "Transportation", "Energy", "Education", "VA", "Homeland Security", "EPA", "OMB", "USTR", "UN", "DNI", "CIA"};
    public static final String[] POST_TITLE = {"Secretary of State", "Treasury secretary", "Defense secretary", "Attorney general", "Interior secretary", "Agriculture secretary",
            "Commerce secretary", "Labor secretary", "Health secretary", "Housing secretary", "Transportation secretary", "Energy secretary", "Education secretary",
            "Veterans Affairs secretary", "Homeland Security secretary", "EPA administrator", "Budget director", "Trade representative", "UN ambassador",
            "Director of national intelligence", "CIA director"};
    /** Civilian employees, thousands. */
    public static final double[] POST_WF = {77, 100, 770, 115, 70, 100, 47, 15, 90, 8, 55, 15, 4, 470, 260, 16, 0.5, 0.2, 0.2, 2, 21};
    // Resolute Desk's issues: 0 ECON 1 PRICES 2 JOBS 3 HEALTH 4 IMMIG 5 CRIME 6 FOREIGN 7 SECURITY 8 TRADE 9 ENERGY 10 RIGHTS 11 TECH 12 EDU 13 ETHICS
    public static final int[] POST_AREA = {6, 0, 7, 5, 9, 0, 11, 2, 3, 1, 2, 9, 12, 7, 4, 9, 0, 8, 6, 7, 7};
    /** The department that carries out policy on each of Resolute Desk's issues. */
    public static final int[] AREA_POST = {P_TREAS, P_COM, P_LAB, P_HHS, P_DHS, P_DOJ, P_STATE, P_DEF, P_USTR, P_DOE, P_DOJ, P_COM, P_ED, P_DOJ};
    /** The order Resolute Desk schedules the first confirmation votes in. */
    public static final int[] SCHEDULE = {P_STATE, P_TREAS, P_DEF, P_DHS, P_DOJ, P_CIA, P_DNI, P_OMB, P_HHS, P_COM, P_INT, P_DOE, P_AG, P_VA, P_DOT, P_HUD, P_LAB, P_ED, P_EPA, P_USTR, P_UN};
    /** Who speaks for each adviser label in decisions: a staff role, or 100 + a cabinet post. */
    static final Map<String, Integer> ADVISER = new LinkedHashMap<>();
    static {
        String[] label = {"Chief of staff", "National security adviser", "Press secretary", "Counsel", "Economic adviser", "Domestic policy adviser", "Legislative director",
                "Political director", "Communications director", "Budget director", "Treasury secretary", "Attorney general", "Defense secretary", "Secretary of State",
                "Homeland Security secretary", "Director of national intelligence", "Commerce secretary", "Energy secretary", "Labor secretary", "Transportation secretary",
                "EPA administrator", "Education secretary", "Health secretary", "Trade representative"};
        int[] who = {S_COS, S_NSA, S_PRESS, S_COUNSEL, S_NEC, S_DPC, S_LEG, S_POL, S_COMMS, 100 + P_OMB, 100 + P_TREAS, 100 + P_DOJ, 100 + P_DEF, 100 + P_STATE,
                100 + P_DHS, 100 + P_DNI, 100 + P_COM, 100 + P_DOE, 100 + P_LAB, 100 + P_DOT, 100 + P_EPA, 100 + P_ED, 100 + P_HHS, 100 + P_USTR};
        for (int i = 0; i < label.length; i++) ADVISER.put(label[i], who[i]);
    }

    // ---- people ----------------------------------------------------------------------------------------------------------
    public static final int T_STEADY = 0, T_EXPERT = 1, T_LOYAL = 2, T_WORKHORSE = 3, T_DEALMAKER = 4, T_MEDIA = 5, T_LEAKER = 6, T_ABRASIVE = 7, T_AMBITIOUS = 8, T_RISK = 9;
    public static final String[] TRAIT = {"Steady", "Expert", "Loyalist", "Workhorse", "Dealmaker", "Media-savvy", "Talks to reporters", "Abrasive", "Ambitious", "Cuts corners"};
    public static final String[] TRAIT_DESC = {"Calm under pressure; rarely makes news.", "Deep knowledge of the job: better results, easier confirmation.",
            "Loyal to you above all. Rarely leaks, rarely resigns.", "Long hours: the agency runs better.", "Good at cutting deals with Congress.", "Good on television.",
            "Leaks to the press when unhappy.", "Clashes with colleagues: other staff are less happy.", "Has plans of their own; may leave to run for office.",
            "Loose with the rules. Ethics stories are more likely."};
    public static final String[] BACKGROUNDS = {"Former governor", "Senator", "House member", "Business executive", "Retired general", "Law professor", "Career civil servant",
            "Former ambassador", "Mayor", "Campaign veteran", "Think-tank scholar", "State attorney general", "Federal judge", "Economist", "Lobbyist"};

    // ---- orders, rules and emergencies -------------------------------------------------------------------------------------
    public static final String[] EMERG_NAME = {"the border", "trade", "energy", "the climate", "crime"};
    public static final String[] ACTION_KIND = {"Executive order", "Agency rule", "National emergency"};
    public static final String[] ACTION_STATUS = {"In effect", "Proposed", "Blocked by a court", "Struck down", "Revoked", "Open for comment", "Overturned by Congress"};
    public static final String[] ROLLOUT = {"a botched rollout", "computer systems that crash on day one", "confusing guidance and long lines", "missed deadlines and lost paperwork"};

    // ---- the courts ------------------------------------------------------------------------------------------------------------
    public static final String[] CASE_STAGE = {"District court", "Court of appeals", "Supreme Court", "Decided"};
    public static final String[] PLAINTIFFS = {"A coalition of states", "The ACLU", "A business group", "Twenty state attorneys general", "A labor union", "An advocacy group",
            "A trade association", "Three cities"};
    public static final String[] SHORTLIST_WHAT = {"A young appeals judge with a strong record for your side", "A respected appeals judge in the mainstream of your party",
            "A moderate with friends in both parties"};
    public static final String[] JUDGE_STRATEGY = {"Ideological", "Mainstream", "Consensus"};
    public static final String[] JUDGE_STRATEGY_DESC = {"Judges as far to your side as you can confirm. Home-state senators slow them down.",
            "Qualified judges in your party's mainstream.", "Picks the other party can live with: faster confirmations, a smaller shift."};

    // ---- scandals and pardons ---------------------------------------------------------------------------------------------------
    public static final String[] ETHICS = {"billed taxpayers for first-class travel and a $40,000 office renovation", "met with lobbyists for a former client while writing rules that affect it",
            "used government staff to run personal errands", "kept trading stocks in industries the department regulates", "steered a contract to a friend's company"};
    public static final String[] PAST = {"Old business deals with a foreign investor resurface", "A former employee sues over the president's past business",
            "Questions about the president's taxes return", "A book by a former aide paints an unflattering picture"};
    /** title|body|kind: 0 mercy, 1 ally, 2 donor, 3 soldier, 4 whistleblower, 5 protesters, 6 celebrity. */
    public static final String[] PARDONS = {
            "A grandmother serving life for a drug offense|She has served 28 years for a nonviolent cocaine conspiracy. Her case has bipartisan support from reform groups.|0",
            "A former campaign aide|He was convicted of lying to federal investigators about contacts during your campaign. Your allies call the case political.|1",
            "A major donor convicted of fraud|A businessman who gave millions to your party was convicted of defrauding investors. His lawyers say he has repaid them.|2",
            "A soldier convicted of a war crime|A decorated special-forces soldier was convicted of killing a detainee. Veterans' groups are split.|3",
            "A whistleblower|A former contractor who leaked documents about domestic surveillance has lived abroad for years. Civil libertarians want him home.|4",
            "Protesters convicted after a riot|Dozens of people convicted after a protest turned violent. Your base sees them as political prisoners; others see rioters.|5",
            "A singer serving time on gun charges|A famous musician's sentence for a gun charge. Celebrities are lobbying the White House.|6",
            "A former governor convicted of corruption|A former governor from your party was convicted of selling state contracts. He says it was a witch hunt.|1",
            "Nonviolent drug offenders, all at once|Commute the sentences of about 1,500 people serving long terms for nonviolent drug crimes.|0"};

    // ---- the vice president ------------------------------------------------------------------------------------------------------
    public static final int VM_NONE = 0, VM_TOUR = 1, VM_TASK = 2, VM_SENATE = 3, VM_MONEY = 4, VM_ABROAD = 5;
    public static final String[] VM_NAME = {"Stay in Washington", "Tour the swing states", "Lead a task force", "Work the Senate", "Raise money for the party", "Represent you abroad"};
    public static final String[] VM_DESC = {"Sits in on meetings and stands by to break ties in the Senate.", "Six weeks of rallies and town halls in the battleground states.",
            "Eight weeks leading the administration's work on one issue.", "Lobbies senators personally. Improves your relationships in the Senate.",
            "Six weeks of fundraisers for the party's candidates.", "Four weeks of foreign trips to reassure allies."};
    public static final int[] VM_WEEKS = {0, 6, 8, 0, 6, 4};
    public static final String[] SWING = {"PA", "MI", "WI", "AZ", "GA", "NC", "NV"};

    // ---- the press, speeches and the party -------------------------------------------------------------------------------------------
    public static final String[] ANSWERS = {"Answer it straight", "Pivot to your message", "Push back on the premise", "Make news: announce something"};
    public static final String[] ANSWER_ADVICE = {"Press secretary: \"Simple and honest. They respect that.\"", "Communications director: \"Get back to what we want to talk about.\"",
            "Political director: \"Don't accept their framing.\"", "Chief of staff: \"Give them a headline of our choosing.\""};
    public static final int[] PRESSER_OUTLETS = {3, 7, 0, 5, 2, 11, 9};
    public static final String[] POST_TONE = {"Celebrate a win", "Explain a policy", "Hit the other side", "Something personal", "A joke"};
    public static final String[] SPEECH_OPEN = {"Speak to every American", "Fire up your base", "Level with the country", "Open with a joke"};
    public static final String[] SPEECH_CLOSE = {"An optimistic vision", "A call to action", "A warning about the stakes"};
    /** The wings in 2029 and where each stands (economics, culture). The Republicans' populist wing is named plainly. */
    public static final String[][] WINGS = {{"Progressives", "Moderates"}, {"Populists", "Traditionalists"}};
    public static final double[][][] WING_AT = {{{-0.7, -0.6}, {-0.2, -0.2}}, {{0.3, 0.8}, {0.7, 0.4}}};
    public static final String[] OPP_STRAT = {"Cooperate where they can", "Pick their fights", "Obstruct everything"};

    // ---- the states, for stories set somewhere ------------------------------------------------------------------------------
    public static final String[] STATE_CODE = {"AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA", "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
            "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT",
            "VA", "WA", "WV", "WI", "WY"};
    public static final String[] STATE_NAME = {"Alabama", "Alaska", "Arizona", "Arkansas", "California", "Colorado", "Connecticut", "Delaware", "Florida", "Georgia",
            "Hawaii", "Idaho", "Illinois", "Indiana", "Iowa", "Kansas", "Kentucky", "Louisiana", "Maine", "Maryland", "Massachusetts", "Michigan", "Minnesota",
            "Mississippi", "Missouri", "Montana", "Nebraska", "Nevada", "New Hampshire", "New Jersey", "New Mexico", "New York", "North Carolina", "North Dakota",
            "Ohio", "Oklahoma", "Oregon", "Pennsylvania", "Rhode Island", "South Carolina", "South Dakota", "Tennessee", "Texas", "Utah", "Vermont", "Virginia",
            "Washington", "West Virginia", "Wisconsin", "Wyoming"};

    /** A policy area (the policy table's 0-11) as Resolute Desk's issue ({@code AREA_ISSUE}). */
    public static final int[] AREA_ISSUE = {0, 1, 2, 3, 1, 9, 4, 5, 12, 10, 11, 7};

    // ---- the data files --------------------------------------------------------------------------------------------------------------

    /** One of the library's orders, rules and emergencies. {@code area} is Resolute Desk's issue; {@code needs} an emergency or -1. */
    public record ActionDef(String id, int kind, int min, String name, String desc, int area, Party lean, double support, double legal, int needs, String fx) {}

    public record Outlet(String name, double lean, double audience, boolean interviews) {}

    public record StoryOption(String label, String adviser, String kind, double base, double heatOk, double heatFail, String fxOk, String fxFail, String newsOk, String newsFail) {}

    public record StoryStage(String headline, String story, int weeks, String fx, List<StoryOption> options) {}

    public record StoryDef(String id, int from, int to, String trigger, String fuel, String name, String endFx, String endHead, String endBody, String fadeText,
                           List<StoryStage> stages) {}

    public record JusticeDef(String name, int born, Party party, double ideology, String real) {}

    final List<ActionDef> library = new ArrayList<>();
    final List<Outlet> outlets = new ArrayList<>();
    final List<StoryDef> stories = new ArrayList<>();
    final List<JusticeDef> court = new ArrayList<>();
    final Map<String, List<String>> names = new TreeMap<>();

    public WestWingData() {
        for (String[] r : Csv.readTsv("/data/rd/actions.tsv"))
            library.add(new ActionDef(r[0], Integer.parseInt(r[1]), Integer.parseInt(r[2]), r[3], r[4], Integer.parseInt(r[5]),
                    r[6].equals("0") ? Party.DEMOCRAT : r[6].equals("1") ? Party.REPUBLICAN : null, Double.parseDouble(r[7]), Double.parseDouble(r[8]),
                    Integer.parseInt(r[9]), r.length > 10 ? r[10] : ""));
        for (String[] r : Csv.readTsv("/data/rd/outlets.tsv"))
            outlets.add(new Outlet(r[0], Double.parseDouble(r[1]), Double.parseDouble(r[2]), Boolean.parseBoolean(r[3])));
        Map<String, String[]> heads = new LinkedHashMap<>();
        Map<String, List<StoryStage>> stages = new LinkedHashMap<>();
        for (String[] r : Csv.readTsv("/data/rd/storylines.tsv")) {
            if (r[1].equals("0")) { heads.put(r[0], r[2].split("\\|", -1)); stages.put(r[0], new ArrayList<>()); continue; }
            String[] t = r[2].split("\\|", 5);
            List<StoryOption> opts = new ArrayList<>();
            if (t.length > 4) for (String o : t[4].split(";;")) {
                String[] f = o.split("\\|", -1);
                if (f.length < 10) continue;
                opts.add(new StoryOption(f[0], f[1], f[2], Double.parseDouble(f[3]), Double.parseDouble(f[4]), Double.parseDouble(f[5]), dash(f[6]), dash(f[7]), f[8], f[9]));
            }
            stages.get(r[0]).add(new StoryStage(t[0], t[1], Integer.parseInt(t[2]), dash(t[3]), opts));
        }
        heads.forEach((id, h) -> stories.add(new StoryDef(id, Integer.parseInt(h[1]), Integer.parseInt(h[2]), h[3], h[4], h[5], h[6], h[7], h[8], h[9], stages.get(id))));
        for (String[] r : Csv.readTsv("/data/justices.tsv"))
            court.add(new JusticeDef(r[0], Integer.parseInt(r[1]), r[2].equals("D") ? Party.DEMOCRAT : Party.REPUBLICAN, Double.parseDouble(r[3]), r[4]));
        for (String[] r : Csv.readTsv("/data/rd/names.tsv")) names.computeIfAbsent(r[0], k -> new ArrayList<>()).add(r[1]);
    }

    private static String dash(String s) { return s.equals("-") ? "" : s; }

    public List<ActionDef> library() { return Collections.unmodifiableList(library); }
    public ActionDef action(String id) {
        for (ActionDef d : library) if (d.id().equals(id)) return d;
        throw new IllegalArgumentException("No such order or rule: " + id);
    }
    public List<Outlet> outlets() { return Collections.unmodifiableList(outlets); }
    public List<StoryDef> stories() { return Collections.unmodifiableList(stories); }
    public StoryDef story(String id) {
        for (StoryDef d : stories) if (d.id().equals(id)) return d;
        return null;
    }
    public List<JusticeDef> court() { return Collections.unmodifiableList(court); }
    List<String> names(String pool) { return names.get(pool); }

    /** The staff role or cabinet post an adviser label names (a role, or 100 + a post), or -1. */
    static int adviser(String label) { return ADVISER.getOrDefault(label, -1); }
}
