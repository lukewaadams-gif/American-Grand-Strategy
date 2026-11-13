package game.mandate.web.api;

import game.mandate.core.engine.GameEngine;
import game.mandate.core.hormuz.HormuzEngine;
import game.mandate.core.hormuz.HormuzState;
import game.mandate.core.time.Phase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Strait of Hormuz crisis room (docs/HORMUZ.md): the ladder, Iran's two decision-makers, the map of the strait (the
 * coastal sites as the intelligence community sees them, the minefields, the lanes and the ships), the oil that still
 * passes and what makes up the shortfall, the president's tools with what each is waiting on, the toll, and the log.
 */
final class HormuzViews {
    private HormuzViews() {}

    static double r1(double v) { return Math.round(v * 10) / 10.0; }

    /** The Oval Office's line on the crisis, while it lasts. */
    static Map<String, Object> summary(GameEngine g) {
        if (!g.hormuzActive()) return null;
        HormuzState h = g.hormuz().get();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("day", h.day() + 1); m.put("rung", h.rung()); m.put("rungName", HormuzEngine.RUNG[h.rung()]);
        m.put("flow", r1(h.flow())); m.put("normal", HormuzState.NORMAL_FLOW); m.put("premium", Math.round(h.premium()));
        m.put("killed", h.usKilled()); m.put("settled", h.settled());
        return m;
    }

    static Map<String, Object> room(GameEngine g) {
        Map<String, Object> v = EngineApi.ok();
        v.put("date", g.date().toString());
        if (g.hormuz().isEmpty() || g.phase() != Phase.GOVERNING) {
            v.put("open", false);
            v.put("reason", "There is no crisis in the Strait of Hormuz.");
            return v;
        }
        HormuzState h = g.hormuz().get();
        v.put("open", true);
        v.put("active", h.active());
        v.put("day", h.day() + 1);
        v.put("started", h.started().toString());
        v.put("cause", h.cause());
        v.put("outcome", h.outcome());
        v.put("message", h.message());
        List<Object> rungs = new ArrayList<>();
        for (int i = 0; i < HormuzEngine.RUNG.length; i++) rungs.add(Map.of("name", HormuzEngine.RUNG[i], "desc", HormuzEngine.RUNG_DESC[i]));
        v.put("rungs", rungs);
        v.put("rung", h.rung());
        v.put("peakRung", h.peakRung());

        // Iran, as the intelligence community reads it
        Map<String, Object> iran = new LinkedHashMap<>();
        iran.put("tehran", r1(h.tehran() * 100)); iran.put("guards", r1(h.guards() * 100)); iran.put("stability", Math.round(h.stability()));
        iran.put("read", read(h));
        v.put("iran", iran);
        List<Object> sites = new ArrayList<>();
        for (HormuzState.Site s : h.sites()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.id()); m.put("name", s.name()); m.put("kind", s.kind()); m.put("lon", s.x()); m.put("lat", s.y());
            m.put("seen", Math.round(s.strengthSeen())); m.put("struck", s.struck());
            sites.add(m);
        }
        v.put("sites", sites);
        List<Object> fields = new ArrayList<>();
        for (HormuzState.Field f : h.fields()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.id()); m.put("name", f.name()); m.put("lon", f.x()); m.put("lat", f.y()); m.put("known", f.known());
            m.put("mines", f.known() ? Math.round(f.mines()) : null);
            fields.add(m);
        }
        v.put("fields", fields);

        // the oil
        Map<String, Object> oil = new LinkedHashMap<>();
        double shortfall = Math.max(0, HormuzState.NORMAL_FLOW - h.flow() - h.bypass() - h.spr() - h.iea());
        oil.put("normal", HormuzState.NORMAL_FLOW); oil.put("flow", r1(h.flow())); oil.put("bypass", r1(h.bypass())); oil.put("bypassTarget", h.bypassTarget());
        oil.put("spr", r1(h.spr())); oil.put("sprLeft", Math.round(h.sprLeft())); oil.put("sprStart", HormuzState.SPR_START); oil.put("iea", r1(h.iea()));
        oil.put("shortfall", r1(shortfall)); oil.put("premium", Math.round(h.premium())); oil.put("insurance", r1(h.insurance()));
        oil.put("reinsured", h.reinsured()); oil.put("bypassAsked", h.bypassAsked());
        oil.put("price", EngineApi.n2(g.state().economy().oil())); oil.put("gas", EngineApi.n2(g.gas()));
        v.put("oil", oil);

        // America, its allies and the region
        Map<String, Object> us = new LinkedHashMap<>();
        us.put("escorts", h.escorts()); us.put("sweepers", h.sweepers()); us.put("allies", h.alliesSweeping());
        us.put("hostsConsent", h.hostsConsent()); us.put("israelRestrained", h.israelRestrained());
        us.put("gulfCalm", Math.round(h.gulfCalm())); us.put("talks", Math.round(h.talks())); us.put("pressure", Math.round(h.chinaPressure()));
        us.put("settled", h.settled());
        us.put("envoyIn", Math.max(0, 3 - (h.day() - h.lastTalks())));
        us.put("pressIn", Math.max(0, 4 - (h.day() - h.lastPress())));
        v.put("us", us);
        Map<String, Object> toll = new LinkedHashMap<>();
        toll.put("killed", h.usKilled()); toll.put("shipsHit", h.shipsHit()); toll.put("seized", h.tankersSeized()); toll.put("tankersHit", h.tankersHit());
        toll.put("strikes", h.strikes());
        v.put("toll", toll);
        Map<String, Object> inv = new LinkedHashMap<>();
        inv.put("stage", h.invasion()); inv.put("option", h.invasionOption()); inv.put("days", h.invasionDays()); inv.put("progress", Math.round(h.invasionProgress()));
        List<Object> plans = new ArrayList<>();
        String[] what = {"Months of build-up, then a campaign to Tehran and years of occupation: thousands of American lives, a trillion dollars, and the regime.",
                "Three weeks to prepare; weeks of fighting for Abu Musa, the Tunbs and Larak. Iran loses the narrows; hundreds may die.",
                "Six weeks to prepare; months to hold the coast from Bandar Abbas to Jask. The coastal threat ends, at a heavy price."};
        for (int i = 0; i < HormuzEngine.INVASION.length; i++) plans.add(Map.of("label", HormuzEngine.INVASION[i], "detail", what[i]));
        inv.put("plans", plans);
        v.put("invasion", inv);
        List<Object> log = new ArrayList<>();
        for (int i = h.log().size() - 1; i >= 0 && log.size() < 30; i--) log.add(h.log().get(i));
        v.put("log", log);
        return v;
    }

    /** The intelligence community's one-line read of Tehran and the Guards. */
    static String read(HormuzState h) {
        String guards = h.guards() > 0.6 ? "The Guards are spoiling for a fight" : h.guards() > 0.3 ? "The Guards are pushing" : h.guards() > 0 ? "The Guards are holding"
                : "The Guards are pulling back";
        String tehran = h.tehran() < -0.35 ? "the government is reining them in" : h.tehran() < -0.1 ? "the government wants a way out"
                : h.tehran() < 0.2 ? "the government is undecided" : "the government is behind them";
        return guards + "; " + tehran + (h.settled() ? ". The deal is holding, for now." : ".");
    }
}
