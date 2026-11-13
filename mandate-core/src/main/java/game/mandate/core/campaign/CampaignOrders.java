package game.mandate.core.campaign;

import game.mandate.core.campaign.AirWar.DigitalMessage;
import game.mandate.core.campaign.AirWar.Network;
import game.mandate.core.campaign.AirWar.Platform;
import game.mandate.core.campaign.AirWar.Tone;
import game.mandate.core.data.Json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A campaign's standing orders, as in Battleground 2028: the candidate's week (21 slots), weekly rating points in each
 * TV market and on national TV, digital dollars in each state with a platform mix and a message per platform,
 * transfers to state parties, red-boxed states for allied super PACs, offices and organisers to open or hire, the
 * message issue and the headquarters tier. Orders repeat every week until changed; office and organiser orders are
 * one-off and are cleared once carried out.
 *
 * <p>A plain value the planners build and the election engine copies into a campaign; it is not domain state. All
 * money is in $M; per-state arrays use {@link CampaignGeography}'s order and per-market arrays its market ids.
 */
public final class CampaignOrders {
    public static final int SLOTS = 21, DAYS = 7, PER_DAY = 3;
    public static final String[] DAY = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    public static final String[] SLOT_NAME = {"Morning", "Afternoon", "Evening"};
    /** Default digital platform mix and messages (BG Digital.defaults, l.6865). */
    static final double[] DEFAULT_MIX = {0.28, 0.16, 0.24, 0.10, 0.14, 0.08};
    static final DigitalMessage[] DEFAULT_MSG = {DigitalMessage.ISSUE, DigitalMessage.BIO, DigitalMessage.ISSUE, DigitalMessage.BIO,
            DigitalMessage.BIO, DigitalMessage.FUND};

    final Activity[] act = new Activity[SLOTS];
    final int[] loc = new int[SLOTS];
    final int[] param = new int[SLOTS];
    final double[] tvGrp, nationalGrp = new double[Network.values().length];
    final Tone[] tvTone;
    final double[] digital, transfer;
    final double[] digMix = DEFAULT_MIX.clone();
    final DigitalMessage[] digMsg = DEFAULT_MSG.clone();
    final boolean[] redBox;
    final int[] officeOrder, organizerOrder;
    Issue message;
    int hq = 1;

    public CampaignOrders(int states, int markets) {
        Arrays.fill(act, Activity.NONE);
        Arrays.fill(loc, -1);
        tvGrp = new double[markets];
        tvTone = new Tone[markets];
        Arrays.fill(tvTone, Tone.POSITIVE);
        digital = new double[states];
        transfer = new double[states];
        redBox = new boolean[states];
        officeOrder = new int[states];
        organizerOrder = new int[states];
    }

    public CampaignOrders copy() {
        CampaignOrders c = new CampaignOrders(digital.length, tvGrp.length);
        c.copyFrom(this, true, true, true);
        c.message = message;
        c.hq = hq;
        return c;
    }

    /** Copies the chosen areas from another set of orders: the schedule; the air war (TV, digital, red boxes); the field. */
    void copyFrom(CampaignOrders o, boolean schedule, boolean ads, boolean field) {
        if (schedule) {
            System.arraycopy(o.act, 0, act, 0, SLOTS);
            System.arraycopy(o.loc, 0, loc, 0, SLOTS);
            System.arraycopy(o.param, 0, param, 0, SLOTS);
        }
        if (ads) {
            System.arraycopy(o.tvGrp, 0, tvGrp, 0, tvGrp.length);
            System.arraycopy(o.tvTone, 0, tvTone, 0, tvTone.length);
            System.arraycopy(o.nationalGrp, 0, nationalGrp, 0, nationalGrp.length);
            System.arraycopy(o.digital, 0, digital, 0, digital.length);
            System.arraycopy(o.digMix, 0, digMix, 0, digMix.length);
            System.arraycopy(o.digMsg, 0, digMsg, 0, digMsg.length);
            System.arraycopy(o.redBox, 0, redBox, 0, redBox.length);
        }
        if (field) {
            System.arraycopy(o.officeOrder, 0, officeOrder, 0, officeOrder.length);
            System.arraycopy(o.organizerOrder, 0, organizerOrder, 0, organizerOrder.length);
            System.arraycopy(o.transfer, 0, transfer, 0, transfer.length);
        }
    }

    // ---- the planner's builders ----------------------------------------------------------------------------------

    public void slot(int i, Activity a, int state, int p) { act[i] = a; loc[i] = a.local() ? state : -1; param[i] = p; }
    public void clearSchedule(int from) { for (int i = from; i < SLOTS; i++) { act[i] = Activity.NONE; loc[i] = -1; param[i] = 0; } }
    public void tv(int market, double grps, Tone tone) { tvGrp[market] = Math.max(0, grps); tvTone[market] = tone; }
    public void national(Network n, double grps) { nationalGrp[n.ordinal()] = Math.max(0, grps); }
    public void digital(int state, double perWeek) { digital[state] = Math.max(0, perWeek); }
    public void digitalMix(double[] mix) {
        double t = 0;
        for (double v : mix) t += Math.max(0, v);
        for (int p = 0; p < digMix.length; p++) digMix[p] = t > 0 ? Math.max(0, mix[p]) / t : DEFAULT_MIX[p];
    }
    public void digitalMessage(Platform p, DigitalMessage m) { digMsg[p.ordinal()] = m; }
    public void transfer(int state, double perWeek) { transfer[state] = Math.max(0, perWeek); }
    public void redBox(int state, boolean on) { redBox[state] = on; }
    public void field(int state, int offices, int organizers) { officeOrder[state] = offices; organizerOrder[state] = organizers; }
    public void message(Issue i) { message = i; }
    public void hq(int tier) { hq = Math.max(0, Math.min(2, tier)); }
    public void clearAirtime() { Arrays.fill(tvGrp, 0); Arrays.fill(nationalGrp, 0); }
    public void clearDigital() { Arrays.fill(digital, 0); }

    // ---- readers --------------------------------------------------------------------------------------------------

    public Activity activity(int slot) { return act[slot]; }
    /** The slot's state (geography index), or -1 for a remote activity or an empty slot. */
    public int location(int slot) { return loc[slot]; }
    public int parameter(int slot) { return param[slot]; }
    public double tvGrp(int market) { return tvGrp[market]; }
    public Tone tvTone(int market) { return tvTone[market]; }
    public double nationalGrp(Network n) { return nationalGrp[n.ordinal()]; }
    public double digital(int state) { return digital[state]; }
    public double digitalShare(Platform p) { return digMix[p.ordinal()]; }
    public DigitalMessage digitalMessage(Platform p) { return digMsg[p.ordinal()]; }
    public double transfer(int state) { return transfer[state]; }
    public boolean redBoxed(int state) { return redBox[state]; }
    public int officeOrder(int state) { return officeOrder[state]; }
    public int organizerOrder(int state) { return organizerOrder[state]; }
    public Issue message() { return message; }
    public int hq() { return hq; }

    // ---- save files -----------------------------------------------------------------------------------------------

    Map<String, Object> toJson(CampaignGeography geo) {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Object> sched = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            if (act[i] == Activity.NONE) { sched.add(null); continue; }
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("a", act[i].name()); s.put("s", loc[i] < 0 ? null : geo.code(loc[i])); s.put("p", param[i]);
            sched.add(s);
        }
        m.put("schedule", sched);
        Map<String, Object> tv = new LinkedHashMap<>();
        for (int k = 0; k < tvGrp.length; k++) if (tvGrp[k] > 0) tv.put(Integer.toString(k), List.of(tvGrp[k], tvTone[k].name()));
        m.put("tv", tv);
        m.put("national", list(nationalGrp));
        m.put("digital", byState(geo, digital));
        m.put("mix", list(digMix));
        List<Object> msgs = new ArrayList<>();
        for (DigitalMessage d : digMsg) msgs.add(d.name());
        m.put("messages", msgs);
        m.put("transfer", byState(geo, transfer));
        List<Object> red = new ArrayList<>();
        for (int s = 0; s < redBox.length; s++) if (redBox[s]) red.add(geo.code(s));
        m.put("redBox", red);
        Map<String, Object> f = new LinkedHashMap<>();
        for (int s = 0; s < officeOrder.length; s++)
            if (officeOrder[s] != 0 || organizerOrder[s] != 0) f.put(geo.code(s), List.of(officeOrder[s], organizerOrder[s]));
        m.put("field", f);
        m.put("message", message == null ? null : message.name());
        m.put("hq", hq);
        return m;
    }

    static CampaignOrders fromJson(Map<String, Object> m, CampaignGeography geo) {
        CampaignOrders o = new CampaignOrders(geo.size(), geo.marketCount());
        if (m == null) return o;
        List<Object> sched = Json.array(m.get("schedule"));
        for (int i = 0; i < SLOTS && i < sched.size(); i++) {
            if (sched.get(i) == null) continue;
            Map<String, Object> s = Json.object(sched.get(i));
            o.act[i] = Activity.valueOf((String) s.get("a"));
            o.loc[i] = s.get("s") == null ? -1 : geo.index((String) s.get("s"));
            o.param[i] = Json.integer(s.get("p"));
        }
        Json.object(m.get("tv")).forEach((k, v) -> {
            List<Object> l = Json.array(v);
            int mk = Integer.parseInt(k);
            o.tvGrp[mk] = Json.num(l.get(0));
            o.tvTone[mk] = Tone.valueOf((String) l.get(1));
        });
        read(Json.array(m.get("national")), o.nationalGrp);
        Json.object(m.get("digital")).forEach((k, v) -> o.digital[geo.index(k)] = Json.num(v));
        read(Json.array(m.get("mix")), o.digMix);
        List<Object> msgs = Json.array(m.get("messages"));
        for (int p = 0; p < o.digMsg.length && p < msgs.size(); p++) o.digMsg[p] = DigitalMessage.valueOf((String) msgs.get(p));
        Json.object(m.get("transfer")).forEach((k, v) -> o.transfer[geo.index(k)] = Json.num(v));
        for (Object c : Json.array(m.get("redBox"))) o.redBox[geo.index((String) c)] = true;
        Json.object(m.get("field")).forEach((k, v) -> {
            List<Object> l = Json.array(v);
            int s = geo.index(k);
            o.officeOrder[s] = Json.integer(l.get(0));
            o.organizerOrder[s] = Json.integer(l.get(1));
        });
        o.message = m.get("message") == null ? null : Issue.valueOf((String) m.get("message"));
        o.hq = Json.integer(m.get("hq"));
        return o;
    }

    static List<Object> list(double[] a) { List<Object> l = new ArrayList<>(); for (double v : a) l.add(v); return l; }

    static void read(List<Object> l, double[] into) { for (int i = 0; i < into.length && i < l.size(); i++) into[i] = Json.num(l.get(i)); }

    static Map<String, Object> byState(CampaignGeography geo, double[] a) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int s = 0; s < a.length; s++) if (a[s] != 0) m.put(geo.code(s), a[s]);
        return m;
    }

    static void readByState(Object o, CampaignGeography geo, double[] into) {
        if (o == null) return;
        Json.object(o).forEach((k, v) -> into[geo.index(k)] = Json.num(v));
    }
}
