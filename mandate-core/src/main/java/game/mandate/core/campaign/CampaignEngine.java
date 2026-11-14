package game.mandate.core.campaign;

import game.mandate.core.campaign.AirWar.Tone;

import game.mandate.core.campaign.AirWar.Ad;
import game.mandate.core.campaign.AirWar.DigitalMessage;
import game.mandate.core.campaign.AirWar.Network;
import game.mandate.core.campaign.AirWar.Platform;
import game.mandate.core.data.Fmt;
import game.mandate.core.electorate.CellElectorateModel;
import game.mandate.core.electorate.StateCatalog;
import game.mandate.core.electorate.UsState;
import game.mandate.core.rng.DeterministicRandomManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One campaign's turn, ported from Battleground 2028's {@code Week.campaign} (Battleground2028.java l.3485): payroll, the
 * candidate's itinerary slot by slot (travel, stamina, gaffes), fundraising, the air war (TV markets, national TV,
 * digital, the Ad Studio's ad), the ground game (offices, organisers, volunteers, contacts), allied super PACs and the
 * week's decay. A turn is a week, or a day in the final stretch, when every weekly quantity is scaled by the turn's
 * fraction of a week as in BG ({@code Week.F}).
 *
 * <p>The stocks reach the vote through {@link #localShift} and {@link #nationalTerm}: BG's campaign terms are logit shifts
 * on each voter cell, scaled by the cell's elasticity; this game's electorate applies its local shift the same way
 * ({@code (nationalSwing + localShift) * k * elasticity}), so a BG term of x logit is a local shift of x / k points.
 * Terms BG varies by age (digital) or by voter group (group image) are averaged with the state's swing weights; the
 * turnout effect of get-out-the-vote and enthusiasm is converted to a margin shift by the cell electorate.
 *
 * <p>Constants are BG's, cited by line. Money is in $M. Part of the election engine's domain.
 */
public final class CampaignEngine {
    /** BG ELECTION_WEEK: week numbers below follow BG's calendar, counted back from the week of the election. */
    static final int ELECTION_WEEK = 104;
    static final double ORGANIZER_WEEK = 0.00135, OFFICE_OPEN = 0.012, ORGANIZER_HIRE = 0.0025;
    static final double[] HQ_TIER = {0.5, 1.0, 1.9}, HQ_ADS = {0.88, 1.0, 1.08}, HQ_CROWD = {0.88, 1.0, 1.1};
    public static final String[] HQ_NAME = {"Lean", "Standard", "Juggernaut"};
    static final double CRED_CAP = 0.35, K_CRED = 0.30;

    private final StateCatalog states;
    private final CampaignParams p;
    private final CampaignGeography geo;
    /** The staff a campaign can hire (docs/CAMPAIGN_WAR_ROOM.md, "Team"). */
    private final CampaignStaff staff = CampaignStaff.load();

    public CampaignStaff staff() { return staff; }

    /** How much harder a senior post's work goes for a side: 1 with the default staff. */
    double factor(CampaignSide k, CampaignStaff.Post post) { return staff.get(k.staffAt(post)).factor(); }
    private final CellElectorateModel cells;
    /** Logit per point of margin in the electorate (CellElectorateModel.logitPerPoint). */
    private final double k;
    private final double[] lean, ev;

    public CampaignEngine(StateCatalog states, CampaignParams p) {
        this.states = states;
        this.p = p;
        this.geo = CampaignGeography.load();
        this.cells = CellElectorateModel.load();
        this.k = cells.logitPerPoint();
        lean = new double[geo.size()];
        ev = new double[geo.size()];
        for (int s = 0; s < geo.size(); s++) {
            UsState u = states.get(geo.code(s));
            lean[s] = u.baselineMargin();
            ev[s] = u.electoralVotes();
        }
    }

    public CampaignGeography geography() { return geo; }

    /**
     * What a campaign turn needs to know.
     * @param day        -1 for a weekly turn; 0 (Monday) to 6 (Sunday) for a day of the final stretch
     * @param polls      the state polls the public sees (D minus R), which the super PACs and planners read
     * @param resources  the computer's income multiplier (difficulty); 1 for the player
     */
    public record Turn(LocalDate date, LocalDate electionDay, int day, Map<Issue, Double> salience, Map<String, Double> polls,
                       double nationalPoll) {
        public double fraction() { return day < 0 ? 1.0 : 1.0 / 7; }

        /** BG's week number: 104 is the week of the election, counted in whole weeks from Monday to Monday. */
        public int bgWeek() { return ELECTION_WEEK - weeksToVote(); }

        public int weeksToVote() {
            return (int) Math.max(0, (monday(electionDay).toEpochDay() - monday(date).toEpochDay()) / 7);
        }

        /** The Monday of a date's week (plain arithmetic: the browser build's java.time has no TemporalAdjusters). */
        public static LocalDate monday(LocalDate d) { return d.minusDays(d.getDayOfWeek().getValue() - 1); }
    }

    // =================================================================================================================
    //  The turn
    // =================================================================================================================

    /** Runs one campaign's turn. Lines the player should read are added to {@code news}. */
    public void execute(CampaignSide side, CampaignSide other, Turn t, double resources, boolean human, DeterministicRandomManager rng, List<String> news) {
        double F = t.fraction();
        Arrays2.fill(side.spendTurn, 0);
        double raisedBefore = side.raised, spentBefore = side.spent;
        CampaignSide.TrailRatings r = side.ratings();
        double fatigueMul = 1.3 - r.sta() / 150.0;
        CampaignOrders o = side.orders;
        // fixed costs (l.3490)
        pay(side, hqWeek(side) * F, -1, 0);
        for (int s = 0; s < geo.size(); s++) {
            double idx = geo.state(s).costIndex();
            if (side.offices[s] > 0) pay(side, side.offices[s] * officeWeek(s) * F, s, 2);
            if (side.organizers[s] > 0) pay(side, side.organizers[s] * ORGANIZER_WEEK * idx * F, s, 2);
        }
        // the itinerary (l.3496): a week's 21 slots, or one day's three
        int prev = side.location;
        int s0 = t.day() >= 0 ? t.day() * 3 : 0, s1 = t.day() >= 0 ? s0 + 3 : CampaignOrders.SLOTS;
        for (int i = s0; i < s1; i++) {
            Activity a = o.act[i];
            if (a == Activity.NONE) continue;
            int s = a.local() ? (o.loc[i] >= 0 ? o.loc[i] : side.location) : side.location;
            double eff = 1;
            if (a.local() && s != prev) {
                boolean overnight = i % 3 == 0;
                if (geo.distance(s, prev) > 220 && !overnight) { eff = 0.7; side.stamina -= 3; }
                prev = s;
            }
            if (side.stamina < 35) eff *= 0.85;
            side.location = prev;
            activity(side, other, a, s, o.param[i], eff, t, human, rng, news);
            side.stamina -= a.fatigue() * fatigueMul * 0.55;
        }
        side.location = prev;
        side.stamina = clamp(side.stamina + (38 + 0.12 * r.sta()) * F, 5, 100);
        fundraising(side, t, resources);
        surrogates(side, t, human, rng, news);
        advertising(side, other, t, human, news);
        field(side, t);
        superPacs(side, t);
        decay(side, F);
        // the message: the original campaign layer's national momentum, one step per week
        double total = t.salience().values().stream().mapToDouble(Double::doubleValue).sum();
        Issue msg = o.message != null ? o.message : side.lastMessage != null ? side.lastMessage : Issue.ECONOMY;
        if (t.day() <= 0) side.setMessage(msg);
        double consistency = Math.min(p.consistencyCap(), 1.0 + p.consistencyStep() * (side.weeksOnMessage() - 1));
        double sal = t.salience().getOrDefault(msg, msg.baseSalience()) / Math.max(1e-9, total);
        double message = p.messageWeight() * sal * msg.advantageFor(side.party()) * consistency;
        Tone tone = o.messageTone();
        // the national message's tone (docs/CAMPAIGN_WAR_ROOM.md, "Message"): contrast is the baseline
        if (tone == Tone.POSITIVE) { message *= 0.8; side.enthusiasm = Math.min(0.9, side.enthusiasm + 0.004 * F); }
        else if (tone == Tone.NEGATIVE) {
            message *= 1.3;
            other.enthusiasm = Math.max(0.1, other.enthusiasm - 0.004 * F);
            if (rng.uniform("campaign.tone") < 0.15 * F) {
                message = -2 * Math.abs(message) - 0.05;
                if (human) news.add("Your attacks on " + other.candidate().name() + " backfire: the coverage turns on the tone, and the message is lost this week.");
            }
        }
        side.momentum += F * (message + p.charismaMomentum() * (side.candidate().charisma() - 5.5)) * factor(side, CampaignStaff.Post.MANAGER);
        // books
        double s4 = dk(0.75, F);
        double vol = 0, dr = 0;
        for (int s = 0; s < geo.size(); s++) {
            side.spend4[s] = side.spend4[s] * s4 + side.spendTurn[s];
            vol += side.volunteers[s];
            dr += side.doors[s];
        }
        side.raisedTurn = side.raised - raisedBefore;
        side.spentTurn = side.spent - spentBefore;
        side.history.add(new double[]{side.raisedTurn, side.spentTurn, vol, dr});
        if (side.cash < 0) {
            side.debt -= side.cash;
            side.cash = 0;
            if (human) news.add("The campaign is out of cash and running up debt. Vendors and staff go unpaid; morale suffers.");
            side.enthusiasm -= 0.01;
        }
    }

    /** Spending: category 0 overheads and events, 1 ads, 2 field (for the "ads" and "field" figures). */
    static void pay(CampaignSide side, double amt, int s, int category) {
        side.cash -= amt;
        side.spent += amt;
        if (s >= 0) {
            side.spendTurn[s] += amt;
            if (category == 1) side.adSpend4[s] += amt;
            if (category == 2) side.fieldSpend4[s] += amt;
        }
    }

    static void raise(CampaignSide side, double amt) { side.cash += amt; side.raised += amt; }

    /** Headquarters payroll a week in the general election, by tier (BG hqWeek, l.3408). */
    public static double hqWeek(CampaignSide side) { return 4.2 * HQ_TIER[side.orders.hq]; }

    /** An office's weekly running cost (BG officeWeek, l.3405). */
    public double officeWeek(int s) { return (850 * geo.state(s).costIndex() + 350) / 1e6; }

    public double officeOpenCost(int s) { return OFFICE_OPEN * geo.state(s).costIndex(); }
    public double organizerWeek(int s) { return ORGANIZER_WEEK * geo.state(s).costIndex(); }
    public static double organizerHireCost() { return ORGANIZER_HIRE; }

    // ---- one event (l.3559) -----------------------------------------------------------------------------------------

    private void activity(CampaignSide k, CampaignSide other, Activity a, int s, int param, double eff, Turn t, boolean human,
                          DeterministicRandomManager rng, List<String> news) {
        CampaignSide.TrailRatings r = k.ratings();
        double cha = r.cha() / 100, spe = r.spe() / 100, ret = r.ret() / 100, intl = r.intl() / 100, aut = r.aut() / 100;
        double vol = r.vol() / 100, vir = r.vir() / 100, big = r.big() / 100;
        double risk = 1;
        pay(k, a.cost(), a.local() ? s : -1, 0);
        Issue focus = focus(k);
        switch (a) {
            case RALLY -> {
                double crowd = rally(k, s, eff, t, human, rng, news);
                k.visit[s] += 1.1 * crowd;
                k.volunteers[s] += (180 + 520 * vol) * crowd * 1.6;
                k.enthusiasm += 0.0012 * crowd * (1 - k.enthusiasm);
                k.smallList += 900 * crowd * (0.5 + vir);
                k.localImage[s] += 0.004 * crowd;
                speak(k, focus, 0.6);
            }
            case TOWN_HALL -> {
                speak(k, focus, 0.5);
                k.visit[s] += 0.8 * eff;
                k.localImage[s] += 0.006 * (ret + intl) / 1.5 * eff;
                k.favor += 0.001 * eff;
                risk = 1.6;
            }
            case RETAIL -> {
                k.visit[s] += 0.6 * (0.5 + ret) * eff;
                k.localImage[s] += 0.004 * (0.4 + ret) * eff;
                risk = 0.8;
            }
            case FUNDRAISER -> {
                double hub = geo.state(s).donorHub() ? 1.0 : 0.45;
                double amt = (0.35 + 2.2 * big) * hub * moneyMomentum(k) * saturation(k) * eff * factor(k, CampaignStaff.Post.FINANCE);
                raise(k, amt);
                k.donorsTapped += amt;
                k.localImage[s] -= 0.001;
                risk = 0.6;
            }
            case CALL_TIME -> {
                double amt = (0.12 + 0.85 * big) * moneyMomentum(k) * saturation(k) * factor(k, CampaignStaff.Post.FINANCE);
                raise(k, amt);
                k.donorsTapped += amt;
                risk = 0.1;
            }
            case PODCAST -> {
                boolean declined = podcast(k, param, eff, t, human, rng, news);
                speak(k, focus, 0.5);
                risk = declined ? 0 : 1.3;
            }
            case TV -> {
                k.favor += 0.0025 * (intl + r.dis() / 100 - 1.1);
                speak(k, focus, 0.6);
                risk = 0.9;
            }
            case CROSSOVER -> {
                double q = (r.deb() / 100 + r.xov() / 100 - 1.0) * 0.015 + 0.002;
                int[] gs = k.party() == game.mandate.core.state.Party.DEMOCRAT ? new int[]{8, 21, 25, 2, 13} : new int[]{9, 21, 4, 12, 16};
                for (int g : gs) k.image[g] += q;
                k.cred[Issue.DEMOCRACY.ordinal()] += 0.002;
                risk = 1.8;
            }
            case SPEECH -> {
                build(k, focus, 0.03 * (spe + intl) / 1.6 * eff);
                k.visit[s] += 0.6 * eff;
                risk = 0.7;
            }
            case UNION -> {
                k.image[CampaignGeography.G_UNION] += 0.006 * (0.5 + aut) * eff;
                k.image[CampaignGeography.G_WHITE_NODEG] += 0.001;
                k.visit[s] += 0.6 * eff;
                k.volunteers[s] += 150 * eff;
            }
            case CHURCH -> {
                int g = Activity.CHURCH_GROUPS[Math.max(0, Math.min(Activity.CHURCH_GROUPS.length - 1, param))];
                k.image[g] += 0.008 * (0.4 + aut) * eff;
                k.visit[s] += 0.5 * eff;
            }
            case CAMPUS -> {
                k.image[CampaignGeography.G_GENZ] += 0.004 * (0.5 + cha) * eff;
                k.image[CampaignGeography.G_STUDENTS] += 0.007 * eff;
                k.volunteers[s] += (120 + 300 * vol) * eff;
                k.visit[s] += 0.4 * eff;
                risk = 1.2;
            }
            case COMMUNITY -> {
                int g = Activity.COMMUNITY_GROUPS[Math.max(0, Math.min(Activity.COMMUNITY_GROUPS.length - 1, param))];
                k.image[g] += 0.008 * (0.4 + ret) * eff;
                k.visit[s] += 0.5 * eff;
                k.volunteers[s] += 80 * eff;
            }
            case PREP -> {
                k.prep += 9 * (1 - k.prep / 130) * (0.6 + intl * 0.5);
                risk = 0;
            }
            case REST -> {
                k.stamina += 6;
                risk = 0;
            }
            case DINNER -> {
                k.localImage[s] += 0.008 * (0.4 + spe) * 0.7 * eff;
                k.visit[s] += 0.5 * eff;
                k.volunteers[s] += 60;
            }
            case NONE -> risk = 0;
        }
        // gaffes (l.3681)
        if (risk > 0) {
            double chance = 0.0035 * risk * (1 + (100 - r.dis()) / 35.0) * (k.stamina < 40 ? 1.8 : 1.0);
            if (rng.chance("campaign.gaffe", chance)) gaffe(k, a, s, human, rng, news);
        }
    }

    private static final String[] GAFFES = {"is caught on a hot mic insulting %s", "mangles the price of groceries", "botches a question about %s",
            "makes an off-colour joke that goes viral for the wrong reasons", "mixes up the name of the town", "snaps at a voter's question on camera"};

    /** BG Events.gaffe (l.4574): favourability and local image take a hit, and the attacks find a new line. */
    private void gaffe(CampaignSide k, Activity a, int s, boolean human, DeterministicRandomManager rng, List<String> news) {
        String what = GAFFES[rng.nextInt("campaign.gaffe", GAFFES.length)];
        if (what.contains("insulting")) what = Fmt.format(what, k.party() == game.mandate.core.state.Party.DEMOCRAT ? "rural voters" : "city voters");
        else if (what.contains("about")) what = Fmt.format(what, Issue.values()[rng.nextInt("campaign.gaffe", Issue.values().length)].label().toLowerCase());
        double sev = 0.012 + rng.uniform("campaign.gaffe") * 0.02;
        k.favor -= sev;
        if (s >= 0) k.localImage[s] -= sev;
        k.attackStick += 0.05;
        news.add("GAFFE: " + k.candidate().name() + " " + what + (a.local() ? " in " + states.get(geo.code(s)).name() : "") + ".");
    }

    /** BG Issues.build: credibility on an issue, saturating at CRED_CAP. */
    static void build(CampaignSide k, Issue i, double amt) {
        int x = i.ordinal();
        if (amt > 0) k.cred[x] += amt * Math.max(0, 1 - k.cred[x] / CRED_CAP);
        else k.cred[x] = Math.max(-0.1, k.cred[x] + amt);
    }

    /** BG Issues.speak: any appearance builds a little credibility on the message issue. */
    static void speak(CampaignSide k, Issue focus, double w) { build(k, focus, 0.0013 * w); }

    static Issue focus(CampaignSide k) {
        return k.orders.message != null ? k.orders.message : k.lastMessage != null ? k.lastMessage : Issue.ECONOMY;
    }

    // ---- rallies and shows -------------------------------------------------------------------------------------------

    private static final String[] VENUE = {"a VFW hall", "a high-school gym", "a convention center", "an arena", "a stadium"};

    /** BG Rallies.hold (l.7145): the crowd, relative to a good general-election rally (12,000). */
    private double rally(CampaignSide k, int s, double eff, Turn t, boolean human, DeterministicRandomManager rng, List<String> news) {
        CampaignSide.TrailRatings r = k.ratings();
        double reference = 12000;
        CampaignGeography.StateInfo st = geo.state(s);
        double size = Math.sqrt(st.adults() / 4.0e6);
        double crowd = reference * (0.45 + r.cha() / 100 * 0.6 + r.spe() / 100 * 0.3) * (0.55 + k.enthusiasm) * (0.35 + 0.75 * k.nameId)
                * (0.6 + 0.5 * st.urban() / 100) * clamp(0.6 + 0.4 * size, 0.6, 1.6) * (0.85 + 0.3 * clamp(k.buzz, -0.5, 1))
                * HQ_CROWD[k.orders.hq] * Math.exp(rng.gaussian("campaign.rally") * 0.25) * eff;
        if (geo.code(s).equals(k.candidate().homeState())) crowd *= 1.25;
        crowd = Math.max(60, Math.round(crowd / 10.0) * 10);
        double best = k.bestCrowd;
        k.rallies++;
        k.rallyCrowds += crowd;
        String venue = VENUE[crowd < 600 ? 0 : crowd < 1800 ? 1 : crowd < 6000 ? 2 : crowd < 20000 ? 3 : 4];
        String where = states.get(geo.code(s)).name();
        if (crowd > best) { k.bestCrowd = crowd; k.bestRally = Fmt.format("%,.0f in %s", crowd, where); }
        double rel = crowd / reference;
        if (human) {
            if (crowd > best && k.rallies > 3 && crowd >= 1000) news.add(Fmt.format("RALLY: %,.0f pack %s in %s, your biggest crowd yet.", crowd, venue, where));
            else if (rel < 0.45) news.add(Fmt.format("RALLY: A sparse crowd of %,.0f in %s; reporters notice the empty seats.", crowd, where));
        }
        if (rel < 0.45) k.favor -= 0.002;
        return clamp(rel, 0.3, 2.2);
    }

    /** Why a show will not book the candidate now, or null (BG Podcasts.unavailable, l.7036). */
    public String showRefusal(CampaignSide k, int show, LocalDate date) {
        Show s = Show.ALL.get(Math.max(0, Math.min(Show.ALL.size() - 1, show)));
        if (k.nameId < Show.TIER_NEED[s.tier()])
            return Fmt.format("Needs %.0f%% national name recognition (you have %.0f%%)", Show.TIER_NEED[s.tier()] * 100, k.nameId * 100);
        double opp = k.party().sign() * s.lean();      // BG: -sign(k) * lean, with BG's sign -1 for the left (here D is +1)
        if (opp >= 0.75 && k.ratings().xov() < 70) return "The host won't book you";
        if (show != Show.LOCAL && date.toEpochDay() < k.lastOnShow[show] + 7L * Show.REBOOK_WEEKS[s.tier()]) return "You were just on";
        return null;
    }

    /** BG Podcasts.appear (l.7071). Returns true if the show declined. */
    private boolean podcast(CampaignSide k, int show, double eff, Turn t, boolean human, DeterministicRandomManager rng, List<String> news) {
        show = Math.max(0, Math.min(Show.ALL.size() - 1, show));
        Show s = Show.ALL.get(show);
        String why = showRefusal(k, show, t.date());
        if (why != null) {
            if (human) news.add("PODCAST: " + s.name() + " passes on booking " + k.candidate().name() + ". " + why + ".");
            return true;
        }
        CampaignSide.TrailRatings r = k.ratings();
        double vir = r.vir() / 100, pod = r.pod() / 100, aut = r.aut() / 100;
        boolean cross = k.party().sign() * s.lean() > 0.25;
        double views = s.views() * 1e6 * (0.5 + 0.6 * vir + 0.5 * k.nameId) * Math.exp(rng.gaussian("campaign.show") * 0.45) * (cross ? 1.1 : 1.0);
        if (show == Show.LOCAL) views = (20000 + 60000 * rng.uniform("campaign.show")) * (0.6 + vir);
        double q = (pod - 0.5) * 1.2 + (aut - 0.5) * 0.8 + rng.gaussian("campaign.show") * 0.35 - (k.stamina < 40 ? 0.25 : 0) + (cross ? -0.1 : 0.1);
        q = clamp(q, -1.2, 1.2) * eff;
        double reach = views / 2.5e8;
        double img = reach * (0.6 + q) * 0.35 * (cross ? 1.4 : 1.0);
        for (int i = 0; i < s.groups().length; i++) k.image[s.groups()[i]] += img * s.weights()[i];
        double signups = views * 0.0012 * (0.5 + r.smd() / 100) * Math.max(0.15, 0.5 + q);
        double dollars = signups * 18 * 0.35;
        k.smallList += signups;
        raise(k, dollars / 1e6);
        if (q > 0.3) k.buzz += Math.min(0.06, reach * 3 * q);
        if (q < -0.7) { k.favor -= 0.004; k.attackStick += 0.03; }
        if (show != Show.LOCAL && rng.chance("campaign.show", 0.04 + 0.16 * vir * Math.max(0, q))) {
            k.smallList += 80000 * (0.5 + vir);
            k.favor += 0.006;
            if (human) news.add("VIRAL: a clip from " + s.name().toLowerCase().replaceFirst("^an? ", "the ") + " racks up millions of views.");
        }
        k.lastOnShow[show] = t.date().toEpochDay();
        String verdict = q > 0.6 ? "rave reviews" : q > 0.15 ? "good reviews" : q > -0.3 ? "mixed reviews" : q > -0.7 ? "an awkward hour" : "a rough interview that critics pounce on";
        if (human) news.add(Fmt.format("PODCAST: %s on %s: %s views, %s. %,.0f new donors ($%.2fM).", k.candidate().name(),
                s.name().toLowerCase(), compact(views), verdict, signups, dollars / 1e6));
        return false;
    }

    static String compact(double v) {
        return v >= 1e6 ? Fmt.format("%.1fM", v / 1e6) : v >= 1e3 ? Fmt.format("%.0fK", v / 1e3) : Fmt.format("%.0f", v);
    }

    // ---- money (l.3700-3760) -------------------------------------------------------------------------------------------

    /** Donor fatigue: beyond ~8 million names each new name is worth less (BG effectiveList). */
    static double effectiveList(double list) {
        double b = 8e6;
        return list <= b ? list : b + b * Math.log(1 + (list - b) / b);
    }

    /** BG moneyMomentum for a nominee: fundraising runs hotter with the campaign's buzz. */
    static double moneyMomentum(CampaignSide k) { return clamp(1 + 0.6 * k.buzz, 0.6, 1.4); }

    /** Big donors max out (BG saturation): a general-election cycle of limits. */
    static double saturation(CampaignSide k) { return clamp(1 - k.donorsTapped / k.donorCap(), 0.12, 1); }

    /**
     * The surrogates' week (docs/CAMPAIGN_WAR_ROOM.md, "Team"): each assigned surrogate tours a state or raises money. A
     * moderate persuades (visits and the local image); a firebrand fires up the base (volunteers and enthusiasm). Their pull
     * is their charisma, more in their home region and most at home; the advance lead's skill makes a better stop, and a
     * careless lead (never a real person) can make a bad story. Then the hired staff's pay.
     */
    private void surrogates(CampaignSide k, Turn t, boolean human, DeterministicRandomManager rng, List<String> news) {
        double F = t.fraction();
        CampaignOrders o = k.orders;
        double wages = 0;
        for (CampaignStaff.Post post : CampaignStaff.Post.SENIOR) wages += staff.get(k.staffAt(post)).salary();
        for (int slot = 0; slot < CampaignStaff.SURROGATES; slot++) {
            int target = o.surrogate(slot);
            if (target == CampaignStaff.NONE) continue;
            Candidate c = k.surrogate(slot);
            if (slot != 1 && c == null) continue;
            CampaignStaff.Member lead = staff.get(o.tourLead(slot));
            wages += lead.salary();
            double star = CampaignStaff.star(c) * (slot == 0 ? 1.2 : 1.0);
            String who = c != null ? c.name() : "your spouse";
            if (target == CampaignStaff.FUNDRAISE) {
                double amt = (0.12 + 0.5 * star) * moneyMomentum(k) * saturation(k) * lead.factor() * factor(k, CampaignStaff.Post.FINANCE) * F;
                raise(k, amt);
                k.donorsTapped += amt;
                continue;
            }
            String code = geo.code(target), home = c != null ? c.homeState() : k.candidate().homeState();
            double pull = home.equals(code) ? 1.8 : CampaignStaff.region(home).equals(CampaignStaff.region(code)) ? 1.3 : 1.0;
            double w = star * pull * lead.factor() * F;
            if (CampaignStaff.persuader(c)) { k.visit[target] += 2.0 * w; k.localImage[target] += 0.008 * w; }
            else { k.visit[target] += 1.0 * w; k.volunteers[target] += 1500 * w; k.enthusiasm += 0.002 * w * (1 - k.enthusiasm); }
            if (lead.risk() > 0 && rng.uniform("campaign.surrogate") < lead.risk() * F) {
                k.localImage[target] -= 0.006;
                if (human) news.add("Advance-team trouble in " + states.get(geo.code(target)).name() + ": " + lead.name() + "'s team double-booked "
                        + who + "'s stop, and the local coverage is about the mix-up.");
            }
        }
        if (wages > 0) pay(k, wages * F, -1, 0);
    }

    private void fundraising(CampaignSide k, Turn t, double resources) {
        double F = t.fraction();
        double phaseMul = 5.0 * (t.weeksToVote() < 7 ? 1.35 : 1);
        double small = effectiveList(k.smallList) * 0.22 * (0.4 + 0.6 * k.ratings().smd() / 100) * (0.6 + 0.8 * k.enthusiasm)
                * phaseMul * moneyMomentum(k) * F * resources / 1e6;
        raise(k, small * factor(k, CampaignStaff.Post.FINANCE));
        k.smallList *= Math.pow(1 + 0.0012 * k.ratings().vir() / 50.0 - 0.004, F);
    }

    // ---- the air war (l.3773) ------------------------------------------------------------------------------------------

    /** Cost of one rating point in a market this turn, $M (BG cpp, l.3417): bigger markets are cheaper per home; prices climb toward November. */
    public double costPerPoint(int market, Turn t) {
        CampaignGeography.Market m = geo.market(market);
        double hh = m.households();
        double c = hh * (hh > 1500 ? 0.55 : hh > 600 ? 0.62 : 0.72);
        int w = t.bgWeek();
        double season = w < 60 ? 0.85 : w < 94 ? 1.0 : w < 98 ? 1.25 : w < 102 ? 1.6 : 2.0;
        double hot = 0;
        for (int i = 0; i < m.states().length; i++) if (Math.abs(lean[m.states()[i]] + 1.5) < 8) hot = Math.max(hot, m.shares()[i]);
        if (w >= 94) c *= 1 + 0.35 * hot;
        c *= season;
        if (w >= 96) c *= 0.75;     // the candidates' lowest-unit-rate window
        return c / 1e6;
    }

    /** National TV's cost per point this turn, $M. */
    public static double nationalCost(Network n, Turn t) { return n.costPerPoint * (t.bgWeek() >= 98 ? 1.5 : 1); }

    /** How hard every ad dollar works: discipline, headquarters, and the Ad Studio's ad on the air (BG l.3774). */
    static double adQuality(CampaignSide k, boolean human) {
        return (0.75 + 0.25 * k.ratings().dis() / 100.0) * HQ_ADS[k.orders.hq] * (human ? studioMultiplier(k) : 1);
    }

    private void advertising(CampaignSide k, CampaignSide other, Turn t, boolean human, List<String> news) {
        double F = t.fraction(), quality = adQuality(k, human) * factor(k, CampaignStaff.Post.MEDIA);
        CampaignOrders o = k.orders;
        int n = geo.size();
        double[] addTv = new double[n];
        for (int m = 0; m < geo.marketCount(); m++) {
            double grp = o.tvGrp[m] * F;
            if (grp <= 0) continue;
            double cost = grp * costPerPoint(m, t);
            if (k.cash < cost && k.cash < 0.5) {
                if (human) news.add("Ads in " + geo.market(m).name() + " pulled: not enough cash.");
                continue;
            }
            CampaignGeography.Market mk = geo.market(m);
            double sum = Math.max(0.01, mk.sumShare());
            for (int i = 0; i < mk.states().length; i++) {
                int s = mk.states()[i];
                double sh = mk.shares()[i];
                addTv[s] += grp * sh * o.tvTone[m].effect * quality;
                pay(k, cost * sh / sum, s, 1);
            }
        }
        for (Network net : Network.values()) {
            double grp = o.nationalGrp[net.ordinal()] * F;
            if (grp <= 0) continue;
            pay(k, grp * nationalCost(net, t), -1, 1);
            for (int s = 0; s < n; s++) addTv[s] += grp * (net == Network.SPANISH ? geo.state(s).hispanic() / 30.0 : 1) * quality * net.reach;
        }
        double[] digTot = new double[DigitalMessage.values().length];
        double tvTot = 0;
        for (int s = 0; s < n; s++) {
            k.tvStock[s] += addTv[s];
            tvTot += addTv[s];
            double d = o.digital[s] * F;
            if (d > 0) {
                pay(k, d, s, 1);
                spendDigital(k, s, d, quality, t, digTot);
                k.volunteers[s] += d * 1e6 / 2500.0 * (0.3 + k.ratings().vol() / 200.0);
            }
            double tr = o.transfer[s] * F;
            if (tr > 0) {
                pay(k, tr, s, 2);
                k.volunteers[s] += tr * 1e6 / 4000.0;
            }
        }
        digitalEffects(k, other, digTot, F);
        studioEffects(k, tvTot, F, human);
    }

    /** BG Digital.spend (l.6910): impressions per adult, by platform reach for each age group, weighted by what the ads say. */
    private void spendDigital(CampaignSide k, int s, double dollarsM, double quality, Turn t, double[] tot) {
        double adults = Math.max(1, geo.state(s).adults());
        for (Platform pl : Platform.values()) {
            double d = dollarsM * k.orders.digMix[pl.ordinal()];
            if (d <= 0) continue;
            DigitalMessage msg = k.orders.digMsg[pl.ordinal()];
            double perAdult = d * 1e6 / pl.cpm * 1000 / adults;
            for (int a = 0; a < CampaignGeography.NAGE; a++) k.digital[s][a] += perAdult * pl.ageReach(a) * msg.persuasion * quality * 100;
            if (msg == DigitalMessage.GOTV && t.weeksToVote() <= 6) k.gotv[s] += perAdult * 0.0005 * (pl == Platform.SEARCH ? 1.4 : 1.0);
            tot[msg.ordinal()] += d;
        }
    }

    /** BG Digital.effects (l.6926): what the week's digital messages do nationally. Spending is in $M. */
    private void digitalEffects(CampaignSide k, CampaignSide other, double[] tot, double F) {
        double issue = tot[DigitalMessage.ISSUE.ordinal()], attack = tot[DigitalMessage.ATTACK.ordinal()], bio = tot[DigitalMessage.BIO.ordinal()];
        double fund = tot[DigitalMessage.FUND.ordinal()], gotv = tot[DigitalMessage.GOTV.ordinal()];
        if (issue > 0) build(k, focus(k), 0.004 * Math.log1p(issue / F) * F);
        if (attack > 0) {
            double v = Math.log1p(attack / F);
            other.attackStick += 0.02 * v * F;
            k.negativity += 0.01 * v * F;
        }
        if (bio > 0) k.favor += 0.0006 * Math.log1p(bio / F) * F;
        if (fund > 0) {
            k.smallList += fund * 1e6 / 6.5 * (0.5 + k.ratings().smd() / 100.0);
            raise(k, fund * 0.35 * (0.6 + k.ratings().smd() / 125.0));
        }
        k.smallList += (bio + issue + attack + gotv) * 1e6 / 60.0;
    }

    // ---- the Ad Studio (l.16001) ---------------------------------------------------------------------------------------

    /** The ad on the air multiplies every TV and digital dollar: 0.8 for a poor ad, 1.2 for a great one (BG AdStudio.mult). */
    static double studioMultiplier(CampaignSide k) {
        Ad a = airingAd(k);
        return a == null ? 1 : 0.775 + 0.45 * a.quality();
    }

    static Ad airingAd(CampaignSide k) { return k.airing < 0 || k.airing >= k.ads.size() ? null : k.ads.get(k.airing); }

    private void studioEffects(CampaignSide k, double tvTot, double F, boolean human) {
        Ad a = airingAd(k);
        if (a == null || !human || tvTot <= 0) return;
        double dose = Math.min(1, tvTot / F / 3000.0) * F;
        if (a.group() >= 0) k.image[a.group()] += 0.004 * a.quality() * dose;
        k.cred[a.issue().ordinal()] += 0.004 * a.quality() * dose;
        if (a.tone() == 2) k.negativity += 0.02 * dose;
    }

    /** How good an ad really is for this campaign now, 0-1 (BG AdStudio.quality, l.16024). */
    double adQualityOf(CampaignSide k, int tone, Issue issue, int group, AirWar.Messenger messenger, Map<Issue, Double> salience, double spark) {
        double cha = k.ratings().cha() / 100, aut = k.ratings().aut() / 100;
        double total = salience.values().stream().mapToDouble(Double::doubleValue).sum();
        double sal = salience.getOrDefault(issue, issue.baseSalience()) / Math.max(1e-9, total);
        double q = 0.35 + 0.25 * Math.min(1.2, sal / 0.12) + 0.12 * clamp(k.cred[issue.ordinal()] * 2, -1, 1);
        if (group >= 0) q += 0.05;
        switch (messenger) {
            case CANDIDATE -> q += 0.12 * (cha + aut - 1.2) + (tone == 0 ? 0.03 : 0);
            case VOTERS -> q += 0.06 + (group == CampaignGeography.G_SUBURBAN || group == CampaignGeography.G_WHITE_NODEG ? 0.05 : 0);
            case VALIDATOR -> q += 0.04 + 0.08 * Math.max(0, k.cred[issue.ordinal()]);
            case NARRATOR -> q += tone == 2 ? 0.07 : -0.02;
        }
        if (tone == 2) q += k.negativity > 1.5 ? -0.1 : 0.05;
        return clamp(q + spark, 0.05, 1);
    }

    /** Produces an ad (BG AdStudio.build): it joins the library, which keeps the newest eight. */
    void produceAd(CampaignSide k, int tone, Issue issue, int group, AirWar.Messenger messenger, Map<Issue, Double> salience, LocalDate date,
                   DeterministicRandomManager rng) {
        pay(k, AirWar.PRODUCE_COST, -1, 1);
        double q = adQualityOf(k, tone, issue, group, messenger, salience, rng.gaussian("campaign.ad") * 0.1);
        String name = AirWar.STUDIO_TONE[tone] + ": " + issue.label() + (group >= 0 ? " (" + CampaignGeography.GROUPS.get(group) + ")" : "");
        k.ads.add(new Ad(name, tone, issue, group, messenger, q, -1, null, List.of(), date.toString()));
        while (k.ads.size() > AirWar.LIBRARY) {
            k.ads.remove(0);
            if (k.airing > 0) k.airing--;
            else if (k.airing == 0) k.airing = -1;
        }
    }

    /** A focus group (BG AdStudio.test, l.16058): a noisy read of the ad's quality, eight groups' reactions and three quotes. */
    void testAd(CampaignSide k, int index, DeterministicRandomManager rng) {
        Ad a = k.ads.get(index);
        pay(k, AirWar.TEST_COST, -1, 1);
        double read = clamp(a.quality() + rng.gaussian("campaign.ad") * 0.12, 0, 1);
        double[] scores = new double[AirWar.KEY_GROUPS.length];
        for (int i = 0; i < scores.length; i++) {
            int g = AirWar.KEY_GROUPS[i];
            double v = (a.quality() - 0.5) * 1.6 + (a.group() == g ? 0.3 : 0) + rng.gaussian("campaign.ad") * 0.15;
            if (a.tone() == 2) v -= g == CampaignGeography.G_SUBURBAN || g == CampaignGeography.G_WOMEN || g == CampaignGeography.G_SENIORS ? 0.2 : 0.05;
            scores[i] = clamp(v, -1, 1);
        }
        List<String> quotes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double v = a.quality() + rng.gaussian("campaign.ad") * 0.2;
            String[] pool = v > 0.62 ? AirWar.QUOTE_GOOD : v > 0.4 ? AirWar.QUOTE_MID : AirWar.QUOTE_BAD;
            quotes.add(pool[rng.nextInt("campaign.ad", pool.length)]);
        }
        k.ads.set(index, a.withTest(read, scores, quotes));
    }

    // ---- the ground game (l.3849) --------------------------------------------------------------------------------------

    private void field(CampaignSide k, Turn t) {
        double F = t.fraction();
        CampaignOrders o = k.orders;
        int left = t.weeksToVote();
        double vol = k.ratings().vol() / 100, enth = k.enthusiasm;
        for (int s = 0; s < geo.size(); s++) {
            CampaignGeography.StateInfo st = geo.state(s);
            double idx = st.costIndex();
            if (o.officeOrder[s] != 0) {
                if (o.officeOrder[s] > 0) pay(k, o.officeOrder[s] * OFFICE_OPEN * idx, s, 2);
                k.offices[s] = Math.max(0, k.offices[s] + o.officeOrder[s]);
                o.officeOrder[s] = 0;
            }
            if (o.organizerOrder[s] != 0) {
                if (o.organizerOrder[s] > 0) pay(k, o.organizerOrder[s] * ORGANIZER_HIRE, s, 2);
                k.organizers[s] = Math.max(0, k.organizers[s] + o.organizerOrder[s]);
                o.organizerOrder[s] = 0;
            }
            // the state party's coordinated campaign staffs up with a lag (BG moves 25% a turn; here 25% a week)
            k.partyOrg[s] += (o.transfer[s] / (ORGANIZER_WEEK * idx) * 0.85 - k.partyOrg[s]) * (1 - dk(0.75, F));
            double org = k.organizers[s] + k.partyOrg[s];
            double cap = k.offices[s] * 8 + 2;
            double effOrg = Math.min(org, cap) + Math.max(0, org - cap) * 0.4;
            double urgency = 0.6 + 1.2 * Math.exp(-left / 8.0);
            double recruits = effOrg * (3 + 5 * vol) * (0.5 + enth) * urgency * F + k.volunteers[s] * 0.015 * (0.5 + enth) * F;
            double churn = k.volunteers[s] * (effOrg < 1 ? 0.12 : 0.06) * F;
            k.volunteers[s] = Math.max(0, k.volunteers[s] + recruits - churn);
            double active = Math.min(k.volunteers[s] * 0.35, effOrg * 45 + k.volunteers[s] * 0.04);
            double perVol = 0.7 + 1.6 * Math.exp(-left / 2.5);
            if (left > 30) perVol *= 0.6;
            double sh = active * perVol * F * (t.day() >= 5 ? 1.35 : t.day() >= 0 ? 0.88 : 1);
            double dr = sh * 0.55 * st.doorDensity(), calls = sh * 0.22 * 150, texts = sh * 0.13 * 1200, rel = sh * 0.10 * 12;
            boolean gotvWindow = left <= 5;
            double contacts = (dr * (gotvWindow ? 0.30 : 0.22) + calls * 0.09 * 0.6 + texts * 0.04 * 0.3 + rel * 0.35 * 1.6) * factor(k, CampaignStaff.Post.FIELD);
            double adults = Math.max(1, st.adults());
            k.shifts[s] = sh; k.doors[s] = dr; k.calls[s] = calls; k.texts[s] = texts; k.relational[s] = rel;
            k.doorsTotal[s] += dr;
            k.persuade[s] += contacts * 0.30 / adults;
            if (left <= 6) k.gotv[s] += contacts * 0.70 / adults;
        }
    }

    /** Persuasion from contacts saturates (BG fieldSat): at most 0.1 logit. */
    public static double fieldSat(double x) { return 0.1 * Math.tanh(x * 10); }

    /** Turnout lift from get-out-the-vote contacts saturates (BG gotvSat): at most 12% of non-voters. */
    public static double gotvSat(double x) { return 0.12 * Math.tanh(x / 0.12); }

    // ---- allied super PACs (l.3895) ------------------------------------------------------------------------------------

    private void superPacs(CampaignSide k, Turn t) {
        double F = t.fraction();
        CampaignSide.TrailRatings r = k.ratings();
        double budget = (2.5 + 9 * r.big() / 100 + 4 * r.est() / 100) * (t.bgWeek() >= 98 ? 2.2 : 1.0) * F;
        double[] val = stateValue(t.polls(), t.nationalPoll(), k.party().sign(), 0);
        double tot = 0;
        for (int s = 0; s < val.length; s++) { if (k.orders.redBox[s]) val[s] *= 2.5; tot += val[s]; }
        if (tot <= 0) return;
        for (int s = 0; s < val.length; s++) {
            double amt = budget * val[s] / tot * 1e6;
            if (amt < 1000) continue;
            k.superPac[s] += amt / (Math.max(150, geo.state(s).tvHouseholds() * 0.62) * (t.bgWeek() >= 98 ? 1.8 : 1.2));
        }
    }

    /**
     * How much each state is worth contesting (BG AI.stateValue, l.5921), from the polls the public sees: electoral votes
     * times closeness, plus a little for states near the national margin. {@code reach} (points) values states that lean
     * against the side as if they were that much closer (the "expand the map" brief).
     */
    public double[] stateValue(Map<String, Double> polls, double national, int sign, double reach) {
        double sigma = 5.0;
        double[] v = new double[geo.size()];
        for (int s = 0; s < geo.size(); s++) {
            Double poll = polls == null ? null : polls.get(geo.code(s));
            double m = poll == null ? lean[s] : poll;
            double mine = m * sign + reach;
            v[s] = ev[s] * Math.exp(-(mine * mine) / (2 * sigma * sigma)) + 0.15 * ev[s] * Math.exp(-Math.abs(m - national) / 12);
        }
        return v;
    }

    // ---- decay (l.3916) -------------------------------------------------------------------------------------------------

    static double dk(double rate, double F) { return F >= 1 ? rate : Math.pow(rate, F); }

    private void decay(CampaignSide k, double F) {
        double tv = dk(0.48, F), pac = dk(0.5, F), dg = dk(0.5, F), vis = dk(0.55, F), per = dk(0.92, F), gotv = dk(0.85, F), li = dk(0.93, F);
        double ad4 = dk(0.75, F);
        for (int s = 0; s < geo.size(); s++) {
            k.tvStock[s] *= tv;
            k.superPac[s] *= pac;
            for (int a = 0; a < CampaignGeography.NAGE; a++) k.digital[s][a] *= dg;
            k.visit[s] *= vis;
            k.persuade[s] *= per;
            k.gotv[s] *= gotv;
            k.localImage[s] *= li;
            k.adSpend4[s] *= ad4;
            k.fieldSpend4[s] *= ad4;
        }
        double ig = dk(0.95, F);
        for (int i = 0; i < k.image.length; i++) k.image[i] *= ig;
        k.favor *= dk(0.95, F);
        k.buzz *= dk(0.88, F);
        k.momentum *= dk(p.momentumDecay(), F);
        k.enthusiasm = clamp(k.enthusiasm + (0.5 - k.enthusiasm) * 0.05 * F, 0.15, 0.95);
        k.attackStick *= dk(0.94, F);
        k.negativity *= dk(0.85, F);
        k.prep *= dk(0.93, F);
    }

    // =================================================================================================================
    //  The vote
    // =================================================================================================================

    /** A side's campaign term in a state, logit (BG Vote.campTerm, l.2710, plus its group image and home state from candTerm). */
    double campTerm(CampaignSide c, int s) {
        CampaignGeography.StateInfo st = geo.state(s);
        double tv = 0.050 * Math.log(1 + (c.tvStock[s] + 0.75 * c.superPac[s]) / 350.0);
        double dg = 0;
        for (int a = 0; a < CampaignGeography.NAGE; a++) dg += st.ageWeight(a) * 0.026 * Math.log(1 + c.digital[s][a] / 25.0);
        double vis = 0.022 * Math.log(1 + c.visit[s]);
        double fld = 0.9 * fieldSat(c.persuade[s]);
        double img = 0;
        for (int g = 0; g < CampaignGeography.NG; g++) if (c.image[g] != 0) img += 0.07 * Math.tanh(c.image[g] / 0.07) * st.groupWeight(g);
        double home = geo.code(s).equals(c.candidate().homeState()) ? 0.025 + 0.055 * c.ratings().hom() / 100.0 : 0;
        return tv + dg + vis + fld + 0.4 * c.localImage[s] + img + home;
    }

    /** Local shift in a state, D minus R in points, from both campaigns' stocks and turnout operations. */
    public double localShift(String stateCode, CampaignSide dem, CampaignSide rep) {
        if (!geo.contains(stateCode)) return 0;
        int s = geo.index(stateCode);
        double logit = campTerm(dem, s) - campTerm(rep, s);
        double turnout = cells.turnoutShift(stateCode, gotvSat(dem.gotv[s]), gotvSat(rep.gotv[s]), dem.enthusiasm, rep.enthusiasm);
        return logit / k + turnout;
    }

    /**
     * A side's national term in points: net favourability (BG favSat), credibility on the issues weighted by their salience
     * (BG issueTerm's credibility part, K_CRED = 0.30) and the damage from attacks that have stuck (BG attackDamage).
     */
    public double nationalTerm(CampaignSide c, Map<Issue, Double> salience) {
        double fav = 0.12 * Math.tanh(c.favor / 0.12);
        double total = salience.values().stream().mapToDouble(Double::doubleValue).sum(), cred = 0;
        for (Issue i : Issue.values()) cred += salience.getOrDefault(i, i.baseSalience()) / Math.max(1e-9, total) * c.cred[i.ordinal()] * K_CRED;
        double attacks = 0.05 * Math.tanh(c.attackStick / 1.2);
        return (fav + cred - attacks) / k;
    }

    /** National effect of the two campaigns, D minus R: message momentum plus favourability, credibility and attacks. */
    public double momentumSwing(CampaignSide dem, CampaignSide rep, Map<Issue, Double> salience) {
        return dem.momentum() - rep.momentum() + nationalTerm(dem, salience) - nationalTerm(rep, salience);
    }

    /** Resolve one debate. Returns the national swing toward Democrats. Preparation helps (BG: min(40, prep) in each exchange). */
    public double debate(CampaignSide dem, DebatePosture dp, CampaignSide rep, DebatePosture rp, DeterministicRandomManager rng) {
        double ds = score(dem, dp, rp, rng), rs = score(rep, rp, dp, rng);
        dem.prep *= 0.6;
        rep.prep *= 0.6;
        return Math.max(-p.debateCap(), Math.min(p.debateCap(), p.debateWeight() * (ds - rs)));
    }

    private double score(CampaignSide side, DebatePosture mine, DebatePosture theirs, DeterministicRandomManager rng) {
        return p.debateRating() * (mine.rating(side.candidate()) + Math.min(40, side.prep) * 0.028) + (mine.counters(theirs) ? p.debateCounter() : 0)
                + mine.volatility() * rng.gaussian("debate");
    }

    public static Map<Issue, Double> baseSalience() {
        Map<Issue, Double> m = new EnumMap<>(Issue.class);
        for (Issue i : Issue.values()) m.put(i, i.baseSalience());
        return m;
    }

    // =================================================================================================================
    //  The opening position (BG Setup.generalMoney and summerStart, l.3149, l.3227)
    // =================================================================================================================

    /**
     * A nominee in mid-July: the party's money, a small-dollar list, and offices, organisers and volunteers already in
     * the battlegrounds (states within 9 points of the 2024 national margin).
     */
    void openCampaign(CampaignSide k, double cashScale) {
        CampaignSide.TrailRatings r = k.ratings();
        k.cash = (120 + 220 * r.big() / 100 + 120 * r.smd() / 100) * 0.6 * cashScale;
        k.raised = k.cash;
        k.smallList = 4e6 + 6e6 * r.smd() / 100;
        k.enthusiasm = 0.5;
        k.nameId = 0.86;
        for (int s = 0; s < geo.size(); s++) {
            if (Math.abs(lean[s] + 1.5) >= 9) continue;
            k.offices[s] = Math.round(2 + ev[s] * 0.9);
            k.organizers[s] = Math.round(k.offices[s] * 4.5);
            k.volunteers[s] = k.organizers[s] * 35;
        }
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    /** java.util.Arrays without the import clash in this file's many arrays. */
    private static final class Arrays2 { static void fill(double[] a, double v) { java.util.Arrays.fill(a, v); } }
}
