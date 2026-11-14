package game.mandate.core.campaign;

import game.mandate.core.campaign.AirWar.Ad;
import game.mandate.core.data.Json;
import game.mandate.core.state.Party;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One campaign, as Battleground 2028 models it (Battleground2028.Camp, l.1989): money and the donor base, the candidate's
 * stamina, location and debate preparation, its image with voter groups and credibility on issues, and in every state the
 * stocks its schedule, ads and field programme have built (visits, local image, TV and digital persuasion, allied super PAC
 * airtime, offices, organisers, volunteers, persuasion and get-out-the-vote contacts). Standing orders live in
 * {@link #orders()}. Changed only by the election engine. Money is in $M.
 */
public final class CampaignSide {
    private final Party party;
    private final Candidate candidate;
    private final TrailRatings ratings;
    final CampaignGeography geo;

    // money (cash and momentum are the original campaign layer's; the rest is BG's)
    double cash, momentum;
    double smallList, donorsTapped, raised, spent, debt;
    double raisedTurn, spentTurn;
    // the candidate
    double stamina = 100, prep, enthusiasm = 0.5, favor, buzz, negativity, attackStick, nameId = 0.86;
    int location;
    Issue lastMessage;
    int weeksOnMessage;
    final double[] cred = new double[Issue.values().length];
    final double[] image = new double[CampaignGeography.NG];
    // per state
    final double[] visit, localImage, tvStock, superPac, volunteers, offices, organizers, partyOrg, persuade, gotv;
    final double[] shifts, doors, calls, texts, relational, doorsTotal;
    final double[] spendTurn, spend4, adSpend4, fieldSpend4;
    final double[][] digital;
    // orders, the ad library, the trail
    final CampaignOrders orders;
    final List<Ad> ads = new ArrayList<>();
    int airing = -1;
    final long[] lastOnShow = new long[Show.ALL.size()];
    int rallies;
    double rallyCrowds, bestCrowd;
    String bestRally = "";
    /** One row per turn: raised, spent, volunteers, doors knocked (for the money and field charts). */
    final List<double[]> history = new ArrayList<>();

    CampaignSide(Party party, Candidate candidate, double cash, CampaignGeography geo) {
        this.party = party;
        this.candidate = candidate;
        this.ratings = TrailRatings.of(candidate);
        this.geo = geo;
        this.cash = cash;
        int n = geo.size();
        visit = new double[n]; localImage = new double[n]; tvStock = new double[n]; superPac = new double[n]; volunteers = new double[n];
        offices = new double[n]; organizers = new double[n]; partyOrg = new double[n]; persuade = new double[n]; gotv = new double[n];
        shifts = new double[n]; doors = new double[n]; calls = new double[n]; texts = new double[n]; relational = new double[n]; doorsTotal = new double[n];
        spendTurn = new double[n]; spend4 = new double[n]; adSpend4 = new double[n]; fieldSpend4 = new double[n];
        digital = new double[n][CampaignGeography.NAGE];
        orders = new CampaignOrders(n, geo.marketCount());
        location = geo.contains(candidate.homeState()) ? geo.index(candidate.homeState()) : 0;
        Arrays.fill(lastOnShow, Long.MIN_VALUE / 2);
    }

    public Party party() { return party; }
    public Candidate candidate() { return candidate; }
    public TrailRatings ratings() { return ratings; }

    // ---- money ---------------------------------------------------------------------------------------------------
    public double cash() { return cash; }
    public double debt() { return debt; }
    public double raised() { return raised; }
    public double spent() { return spent; }
    public double raisedLastTurn() { return raisedTurn; }
    public double spentLastTurn() { return spentTurn; }
    /** Names on the small-dollar list. */
    public double smallDollarList() { return smallList; }
    /** $M raised from big donors so far; with the cap, how tapped out they are. */
    public double donorsTapped() { return donorsTapped; }
    public double donorCap() { return 260 * (0.4 + ratings.big() / 100.0); }

    // ---- the candidate and the message ----------------------------------------------------------------------------
    public double momentum() { return momentum; }
    public Issue lastMessage() { return lastMessage; }
    public int weeksOnMessage() { return weeksOnMessage; }
    /** 0-100. Below 35 events lose effect; below 40 gaffes are likelier. */
    public double stamina() { return stamina; }
    /** Debate preparation, 0-130 (diminishing returns). */
    public double prep() { return prep; }
    public double enthusiasm() { return enthusiasm; }
    /** Net favourability shift (logit units, before saturation). */
    public double favor() { return favor; }
    /** BG's momentum: crowds and fundraising run with it (not a vote term in its own right). */
    public double buzz() { return buzz; }
    public double nameRecognition() { return nameId; }
    /** The state the candidate is in (CampaignGeography code). */
    public String location() { return geo.code(location); }
    public double credibility(Issue i) { return cred[i.ordinal()]; }
    public double groupImage(int g) { return image[g]; }

    // ---- per state ------------------------------------------------------------------------------------------------
    private double at(double[] a, String state) { return geo.contains(state) ? a[geo.index(state)] : 0; }
    public double visit(String state) { return at(visit, state); }
    public double localImage(String state) { return at(localImage, state); }
    /** TV persuasion stock in rating points (decays about half a week). */
    public double tvStock(String state) { return at(tvStock, state); }
    public double superPacStock(String state) { return at(superPac, state); }
    /** Digital persuasion stock by age group (18-29, 30-64, 65+). */
    public double digitalStock(String state, int age) { return geo.contains(state) ? digital[geo.index(state)][age] : 0; }
    public double offices(String state) { return at(offices, state); }
    public double organizers(String state) { return at(organizers, state); }
    /** Organisers the state party has hired with this campaign's transfers. */
    public double partyOrganizers(String state) { return at(partyOrg, state); }
    public double volunteers(String state) { return at(volunteers, state); }
    public double shifts(String state) { return at(shifts, state); }
    public double doors(String state) { return at(doors, state); }
    public double calls(String state) { return at(calls, state); }
    public double texts(String state) { return at(texts, state); }
    public double relationalContacts(String state) { return at(relational, state); }
    public double doorsToDate(String state) { return at(doorsTotal, state); }
    /** Persuasion contacts as a share of adults (before saturation). */
    public double persuasion(String state) { return at(persuade, state); }
    public double getOutTheVote(String state) { return at(gotv, state); }
    /** Spending in the state over about the last four weeks, $M. */
    public double recentSpending(String state) { return at(spend4, state); }

    /** Recent ad spending in the state, $M (the original campaign layer's "ads" figure). */
    public double ads(String state) { return at(adSpend4, state); }
    /** Recent field spending in the state, $M (the original campaign layer's "field" figure). */
    public double field(String state) { return at(fieldSpend4, state); }
    /** Candidate visits still felt in the state. */
    public double visits(String state) { return visit(state); }

    // ---- orders, ads, the trail -----------------------------------------------------------------------------------
    /** A copy of the standing orders. */
    public CampaignOrders orders() { return orders.copy(); }
    public List<Ad> ads() { return List.copyOf(ads); }
    /** Index of the ad on the air, or -1. */
    public int airing() { return airing; }
    public int rallies() { return rallies; }
    public double rallyCrowds() { return rallyCrowds; }
    public double bestCrowd() { return bestCrowd; }
    public String bestRally() { return bestRally; }
    public List<double[]> history() { List<double[]> l = new ArrayList<>(); for (double[] r : history) l.add(r.clone()); return l; }

    void setMessage(Issue issue) {
        weeksOnMessage = issue == lastMessage ? weeksOnMessage + 1 : 1;
        lastMessage = issue;
    }

    /**
     * The candidate's ratings on Battleground 2028's 0-100 scale, derived from this game's five 1-10 ratings
     * (v = (r - 1) x 100 / 9, the inverse of tools/BgExport.java's mapping). BG rates 21 attributes; the ones its
     * campaign reads that this game does not rate are derived as noted, or held at BG's typical value.
     */
    public record TrailRatings(double cha, double deb, double intl, double dis, double big, double smd, double spe, double ret, double aut,
                               double vol, double vir, double pod, double sta, double xov, double est, double hom) {
        static double v(int r) { return (r - 1) * 100.0 / 9.0; }

        public static TrailRatings of(Candidate c) {
            double cha = v(c.charisma()), deb = v(c.debate()), intl = v(c.policy()), dis = v(c.discipline()), money = v(c.fundraising());
            return new TrailRatings(cha, deb, intl, dis, money, money,
                    (cha + intl) / 2,   // speeches: charisma and substance
                    cha,                // retail politics
                    (cha + dis) / 2,    // authenticity
                    cha,                // volunteer energy
                    cha,                // virality
                    (cha + intl) / 2,   // long-form
                    50 + dis / 4,       // stamina: 50-75
                    50, 60, 60);        // crossover appeal, establishment ties, home-state pull: BG's typical values
        }
    }

    // ---- save files -----------------------------------------------------------------------------------------------

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("party", party); m.put("candidate", candidate.id()); m.put("cash", cash); m.put("momentum", momentum);
        m.put("lastMessage", lastMessage == null ? null : lastMessage.name()); m.put("weeksOnMessage", weeksOnMessage);
        m.put("money", CampaignOrders.list(new double[]{smallList, donorsTapped, raised, spent, debt, raisedTurn, spentTurn}));
        m.put("candidateState", CampaignOrders.list(new double[]{stamina, prep, enthusiasm, favor, buzz, negativity, attackStick, nameId, location}));
        m.put("cred", CampaignOrders.list(cred));
        m.put("image", CampaignOrders.list(image));
        Map<String, Object> st = new LinkedHashMap<>();
        for (int s = 0; s < geo.size(); s++) {
            double[] row = {visit[s], localImage[s], tvStock[s], superPac[s], volunteers[s], offices[s], organizers[s], partyOrg[s], persuade[s],
                    gotv[s], shifts[s], doors[s], calls[s], texts[s], relational[s], doorsTotal[s], spendTurn[s], spend4[s], adSpend4[s],
                    fieldSpend4[s], digital[s][0], digital[s][1], digital[s][2]};
            boolean any = false;
            for (double v : row) if (v != 0) { any = true; break; }
            if (any) st.put(geo.code(s), CampaignOrders.list(row));
        }
        m.put("states", st);
        m.put("orders", orders.toJson(geo));
        List<Object> al = new ArrayList<>();
        for (Ad a : ads) al.add(a.toJson());
        m.put("ads", al); m.put("airing", airing);
        Map<String, Object> shows = new LinkedHashMap<>();
        for (int i = 0; i < lastOnShow.length; i++) if (lastOnShow[i] > Long.MIN_VALUE / 2) shows.put(Integer.toString(i), lastOnShow[i]);
        m.put("shows", shows);
        m.put("trail", List.of(rallies, rallyCrowds, bestCrowd, bestRally));
        List<Object> h = new ArrayList<>();
        for (double[] r : history) h.add(CampaignOrders.list(r));
        m.put("history", h);
        return m;
    }

    static CampaignSide fromJson(Map<String, Object> m, CandidateCatalog catalog, CampaignGeography geo) {
        CampaignSide s = new CampaignSide(Party.valueOf((String) m.get("party")), catalog.get((String) m.get("candidate")), Json.num(m.get("cash")), geo);
        s.momentum = Json.num(m.get("momentum"));
        s.lastMessage = m.get("lastMessage") == null ? null : Issue.valueOf((String) m.get("lastMessage"));
        s.weeksOnMessage = Json.integer(m.get("weeksOnMessage"));
        if (m.get("money") == null) return s;       // a save from before the Battleground 2028 campaign: fresh assets
        double[] money = new double[7], cand = new double[9];
        CampaignOrders.read(Json.array(m.get("money")), money);
        CampaignOrders.read(Json.array(m.get("candidateState")), cand);
        s.smallList = money[0]; s.donorsTapped = money[1]; s.raised = money[2]; s.spent = money[3]; s.debt = money[4]; s.raisedTurn = money[5]; s.spentTurn = money[6];
        s.stamina = cand[0]; s.prep = cand[1]; s.enthusiasm = cand[2]; s.favor = cand[3]; s.buzz = cand[4]; s.negativity = cand[5];
        s.attackStick = cand[6]; s.nameId = cand[7]; s.location = (int) cand[8];
        CampaignOrders.read(Json.array(m.get("cred")), s.cred);
        CampaignOrders.read(Json.array(m.get("image")), s.image);
        Json.object(m.get("states")).forEach((code, v) -> {
            int i = geo.index(code);
            double[] r = new double[23];
            CampaignOrders.read(Json.array(v), r);
            s.visit[i] = r[0]; s.localImage[i] = r[1]; s.tvStock[i] = r[2]; s.superPac[i] = r[3]; s.volunteers[i] = r[4]; s.offices[i] = r[5];
            s.organizers[i] = r[6]; s.partyOrg[i] = r[7]; s.persuade[i] = r[8]; s.gotv[i] = r[9]; s.shifts[i] = r[10]; s.doors[i] = r[11];
            s.calls[i] = r[12]; s.texts[i] = r[13]; s.relational[i] = r[14]; s.doorsTotal[i] = r[15]; s.spendTurn[i] = r[16]; s.spend4[i] = r[17];
            s.adSpend4[i] = r[18]; s.fieldSpend4[i] = r[19]; s.digital[i][0] = r[20]; s.digital[i][1] = r[21]; s.digital[i][2] = r[22];
        });
        CampaignOrders o = CampaignOrders.fromJson(Json.object(m.get("orders")), geo);
        s.orders.copyFrom(o, true, true, true);
        s.orders.message = o.message;
        s.orders.messageTone = o.messageTone;
        s.orders.hq = o.hq;
        for (Object a : Json.array(m.get("ads"))) s.ads.add(Ad.fromJson(Json.object(a)));
        s.airing = Json.integer(m.get("airing"));
        Json.object(m.get("shows")).forEach((k, v) -> s.lastOnShow[Integer.parseInt(k)] = ((Number) v).longValue());
        List<Object> t = Json.array(m.get("trail"));
        s.rallies = Json.integer(t.get(0)); s.rallyCrowds = Json.num(t.get(1)); s.bestCrowd = Json.num(t.get(2)); s.bestRally = (String) t.get(3);
        for (Object r : Json.array(m.get("history"))) {
            List<Object> l = Json.array(r);
            double[] row = new double[l.size()];
            for (int i = 0; i < row.length; i++) row[i] = Json.num(l.get(i));
            s.history.add(row);
        }
        return s;
    }
}
