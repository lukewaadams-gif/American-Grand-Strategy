package game.mandate.core.hormuz;

import game.mandate.core.data.Fmt;
import game.mandate.core.effects.Effect;
import game.mandate.core.hormuz.HormuzState.Field;
import game.mandate.core.hormuz.HormuzState.Site;
import game.mandate.core.news.News;
import game.mandate.core.rng.DeterministicRandomManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Strait of Hormuz crisis (docs/HORMUZ.md), day by day. Iran's two decision-makers lean toward escalation or
 * restraint; the ladder rises or falls with them; at each rung the Guards harass, seize, mine, strike or close the
 * strait, within what their arsenal at the strait still allows. America's escorts, minesweepers, strikes, envoys and
 * oil levers answer. The oil that still passes, the bypass pipelines and the reserves set the price the rest of the
 * game feels; the toll in lives and ships reaches approval and the record.
 */
public final class HormuzEngine {
    public record Outcome(List<Effect> effects, List<News> news, List<String> log) {
        public static Outcome empty() { return new Outcome(new ArrayList<>(), new ArrayList<>(), new ArrayList<>()); }
    }

    public static final String[] RUNG = {"Tension", "Harassment", "Seizures", "Mining", "Attacks", "Closure"};
    public static final String[] RUNG_DESC = {"Rhetoric and drills.", "Fast boats swarm tankers; drones overhead.", "The Guards board and seize tankers.",
            "Mines in or near the lanes.", "Missiles and drones on shipping and Gulf oil sites.", "Iran declares the strait closed and fires on all traffic."};
    /** The share of the strait's oil each rung keeps away (owners wait, insurers balk), before escorts and mines. */
    static final double[] RUNG_LOSS = {0, 0.03, 0.10, 0.25, 0.60, 0.90};
    public static final String[] PRESET = {"Base case", "Optimistic", "Pessimistic"};
    public static final String[] INVASION = {"A full invasion of Iran", "Seize the islands in the strait", "Take the coast from Bandar Abbas to Jask"};
    /** Days of build-up, and progress a day once the fighting starts (percent), for each invasion option. */
    static final int[] BUILD_DAYS = {90, 21, 45};
    static final double[] PACE = {1.3, 6.0, 2.6};
    static final double[][] LOSSES = {{15, 40}, {4, 14}, {8, 24}};

    public HormuzState newCrisis(LocalDate date, int startRung, String cause, int preset, DeterministicRandomManager rng) {
        HormuzState s = new HormuzState();
        s.active = true; s.started = date; s.cause = cause;
        s.rung = s.peakRung = Math.max(1, Math.min(5, startRung));
        double arsenal = preset == 1 ? 75 : preset == 2 ? 120 : 100;
        s.arsenalBoats = s.arsenalMines = s.arsenalMissiles = arsenal;
        s.hawk = preset == 2 ? 0.6 : preset == 1 ? 0.3 : 0.45;
        s.guards = s.hawk;
        s.tehran = s.hawk - 0.35;
        site(s, "bandar", "Bandar Abbas naval base", "boats", 56.28, 27.18, arsenal, rng);
        site(s, "larak", "Larak Island", "boats", 56.35, 26.86, arsenal, rng);
        site(s, "tunb", "Greater Tunb", "boats", 55.30, 26.26, arsenal, rng);
        site(s, "qeshm", "Qeshm Island missile batteries", "missiles", 55.90, 26.80, arsenal, rng);
        site(s, "abumusa", "Abu Musa", "missiles", 55.03, 25.88, arsenal, rng);
        site(s, "jask", "Jask", "missiles", 57.77, 25.64, arsenal, rng);
        site(s, "bushehr", "Drone sites near Bushehr", "drones", 50.84, 28.97, arsenal, rng);
        site(s, "chabahar", "Drone sites near Chabahar", "drones", 60.62, 25.30, arsenal, rng);
        site(s, "minelayers", "Mine layers at Bandar Abbas", "mines", 56.45, 27.08, arsenal, rng);
        field(s, "inbound", "The inbound lane", 56.45, 26.55);
        field(s, "outbound", "The outbound lane", 56.62, 26.42);
        field(s, "west", "The approaches west of the Tunbs", 55.45, 26.30);
        field(s, "oman", "The Gulf of Oman approaches", 57.20, 25.90);
        if (s.rung >= 3) for (Field f : s.fields) if (!f.id.equals("west")) f.mines = 6 + rng.nextInt("hormuz.mines", 8);
        flow(s);
        s.log.add(stamp(date) + "the crisis begins: " + cause + ". " + RUNG[s.rung] + ".");
        return s;
    }

    static void site(HormuzState s, String id, String name, String kind, double x, double y, double strength, DeterministicRandomManager rng) {
        Site t = new Site();
        t.id = id; t.name = name; t.kind = kind; t.x = x; t.y = y; t.strength = Math.min(100, strength);
        t.strengthSeen = clamp(t.strength + rng.gaussian("hormuz.intel") * 12, 0, 100);
        s.sites.add(t);
    }

    static void field(HormuzState s, String id, String name, double x, double y) {
        Field f = new Field();
        f.id = id; f.name = name; f.x = x; f.y = y;
        s.fields.add(f);
    }

    double strength(HormuzState s, String kind) {
        double t = 0;
        int n = 0;
        for (Site x : s.sites) if (x.kind.equals(kind)) { t += x.strength; n++; }
        return n == 0 ? 0 : t / n;
    }

    double mines(HormuzState s) { double m = 0; for (Field f : s.fields) m += f.mines; return m; }

    // =========================================================================================
    //  The day
    // =========================================================================================

    public Outcome day(HormuzState s, LocalDate date, DeterministicRandomManager rng) {
        Outcome out = Outcome.empty();
        if (!s.active) return out;
        s.day++;
        // 1. Iran's two decision-makers move toward where the day's pressures put them. The Guards: their appetite for a
        //    fight, their anger at the latest blows and the price they are extracting, less what talks, China and their
        //    own losses at the strait take away. The government: the talks, Beijing, the regime's stability and its own
        //    exports (which pass the same strait).
        double capacity = Math.min(1, (strength(s, "boats") + strength(s, "missiles") + strength(s, "mines")) / 300);
        s.anger *= 0.93;
        double gTarget = s.hawk + s.anger + Math.min(0.15, s.premium / 400) - 0.5 * s.talks / 100 - 0.25 * s.chinaPressure / 100
                - 0.6 * (1 - capacity) - (s.settled ? 0.7 : 0);
        double tTarget = (s.hawk - 0.35) + 0.4 * s.anger - 0.9 * s.talks / 100 - 0.5 * s.chinaPressure / 100
                - 0.6 * Math.max(0, 60 - s.stability) / 60 - (s.flow < 8 ? 0.15 : 0) - (s.settled ? 0.8 : 0);
        s.guards = clamp(s.guards + 0.15 * (gTarget - s.guards) + rng.gaussian("hormuz.iran") * 0.04, -1, 1);
        s.tehran = clamp(s.tehran + 0.12 * (tTarget - s.tehran) + rng.gaussian("hormuz.iran") * 0.04, -1, 1);
        s.chinaPressure = Math.max(0, s.chinaPressure * 0.97);
        if (!s.settled) s.talks = Math.max(0, s.talks - 0.4);
        s.stability = clamp(s.stability - 0.02 * s.rung - (s.flow < 10 ? 0.08 : -0.02), 5, 100);
        // 2. the ladder: climbed when the Guards push and the government does not hold them back (or they defy it);
        //    descended when the Guards cool, the government reins them in, or there is little left to fight with
        if (s.rung < 5 && s.guards > 0.2 + 0.1 * s.rung && (s.tehran > -0.25 || s.guards > 0.85) && rng.uniform("hormuz.ladder") < 0.2) {
            s.rung++;
            s.peakRung = Math.max(s.peakRung, s.rung);
            news(out, date, News.Kind.BAD, ("Iran escalates in the Strait of Hormuz: " + RUNG[s.rung].toLowerCase(Locale.ROOT)).toUpperCase(Locale.ROOT), RUNG_DESC[s.rung], 3.5);
            log(s, date, "Iran climbs to " + RUNG[s.rung].toLowerCase(Locale.ROOT));
        } else if (s.rung > 0 && (s.guards < 0.1 * s.rung - 0.05 || (s.tehran < -0.35 && s.guards < 0.7) || (capacity < 0.3 && s.rung >= 3))
                && rng.uniform("hormuz.ladder") < (s.settled ? 0.4 : 0.2)) {
            s.rung--;
            news(out, date, News.Kind.GOOD, "Iran steps back in the strait", RUNG[s.rung] + ".", 2.5);
            log(s, date, "Iran steps back to " + RUNG[s.rung].toLowerCase(Locale.ROOT));
        }
        // mobile launchers and boats come out of hiding: what was struck partly recovers
        for (Site x : s.sites) if (x.strength > 0 && x.strength < 100) x.strength = Math.min(100, x.strength + (x.kind.equals("missiles") ? 0.3 : 0.15));
        // 3. what the Guards do at their rung, with what they have left
        double cover = Math.min(0.9, s.escorts * 0.22);
        if (s.rung >= 2 && rng.uniform("hormuz.seize") < 0.3 * strength(s, "boats") / 100 * (1 - cover)) {
            s.tankersSeized++;
            s.gulfCalm = clamp(s.gulfCalm - 3, 0, 100);
            news(out, date, News.Kind.BAD, "Revolutionary Guards seize a tanker in the strait", null, 2.4);
        }
        if (s.rung >= 3 && !s.settled) {
            double laid = 5 * strength(s, "mines") / 100 * (0.5 + rng.uniform("hormuz.mines"));
            for (Field f : s.fields) if (!f.id.equals("oman") || s.rung >= 4) {
                f.mines += laid / 3;
                if (rng.uniform("hormuz.mines") < 0.5 + 0.1 * (s.escorts + s.sweepers + s.alliesSweeping)) f.known = true;
            }
        }
        if (s.rung >= 4) {
            int salvos = (int) Math.round((strength(s, "missiles") + strength(s, "drones")) / 100 * (1 + rng.uniform("hormuz.attack") * 2));
            for (int k = 0; k < salvos; k++) attack(s, date, out, rng);
        }
        // 4. America's minesweepers and the allies'
        double sweep = 2.0 * (s.sweepers + s.alliesSweeping) + (s.settled ? 1.5 : 0);
        for (int pass = 0; pass < 2 && sweep > 0; pass++) for (Field f : s.fields) {
            if (sweep <= 0) break;
            if (pass == 0 && !f.known) continue;
            double c = Math.min(f.mines, pass == 0 ? sweep : sweep / 2);
            f.mines -= c; f.cleared += c; sweep -= pass == 0 ? c : 2 * c;
        }
        if (s.rung >= 3 && s.sweepers > 0 && rng.uniform("hormuz.sweep") < 0.005 * s.sweepers) {
            int dead = 1 + rng.nextInt("hormuz.sweep", 6);
            s.usKilled += dead; s.shipsHit++;
            kia(out, date, dead, "A minesweeper strikes a mine in the strait");
        }
        // 5. Iran's proxies: the militias in Iraq and Syria against American bases, the Houthis in the Red Sea
        if (s.rung >= 3 && !s.settled && rng.uniform("hormuz.proxy") < 0.04 * (s.rung - 2) * (1 + s.anger)) {
            if (rng.uniform("hormuz.proxy") < 0.6) {
                int dead = rng.nextInt("hormuz.proxy", 4);
                out.effects().add(new Effect.WorldShift("proxy", 3));
                if (dead > 0) { s.usKilled += dead; kia(out, date, dead, "Militias in Iraq strike an American base"); }
                else news(out, date, News.Kind.BAD, "Militias in Iraq fire rockets at an American base", "No one is hurt.", 1.6);
            } else {
                out.effects().add(new Effect.WorldShift("redsea", 3));
                news(out, date, News.Kind.BAD, "The Houthis fire on shipping in the Red Sea", "Tankers rerouted from the Gulf find no safe road there either.", 1.8);
            }
        }
        // the gas: Qatar's and the UAE's liquefied gas stays in port too, and Japan, Korea and Europe feel it most
        if (s.day % 7 == 0 && s.flow < 0.6 * HormuzState.NORMAL_FLOW) {
            out.effects().add(new Effect.WorldShift("rel:JP", -1));
            out.effects().add(new Effect.WorldShift("rel:KR", -1));
            out.effects().add(new Effect.WorldShift("rel:IN", -1));
            out.effects().add(new Effect.WorldShift("nato", -0.5));
        }
        // 6. the invasion, if one is under way
        invasionDay(s, date, out, rng);
        // 7. the oil
        flow(s);
        if (s.bypass < s.bypassTarget()) s.bypass = Math.min(s.bypassTarget(), s.bypass + 0.25);
        s.sprLeft = Math.max(0, s.sprLeft - s.spr);
        if (s.sprLeft <= 0) s.spr = 0;
        // 8. how it ends
        if (s.rung <= 1 && mines(s) < 5) s.calmDays++; else s.calmDays = 0;
        if (s.settled && s.calmDays >= 7) end(s, date, "A settlement", true, out);
        else if (s.calmDays >= 14) end(s, date, "Shipping restored", true, out);
        else if (s.invasion == 3 && s.invasionOption == 0) end(s, date, "Regime change and occupation", true, out);
        else if (s.day >= 150 && s.invasion != 1 && s.invasion != 2) end(s, date, "A stalemate", false, out);   // not with troops on the move
        return out;
    }

    /** One salvo of missiles or drones: at an escort, a tanker or a Gulf terminal. */
    void attack(HormuzState s, LocalDate date, Outcome out, DeterministicRandomManager rng) {
        for (Site x : s.sites) if (x.kind.equals("missiles") || x.kind.equals("drones")) x.strength = Math.max(0, x.strength - 0.2);
        double r = rng.uniform("hormuz.attack");
        if (s.escorts > 0 && r < 0.4) {
            if (rng.uniform("hormuz.attack") < 0.05) {
                int dead = 3 + rng.nextInt("hormuz.attack", 28);
                s.usKilled += dead; s.shipsHit++;
                kia(out, date, dead, "An Iranian missile hits an American destroyer");
            }
        } else if (r < 0.8) {
            if (rng.uniform("hormuz.attack") < 0.35 * (1 - Math.min(0.8, s.escorts * 0.2))) {
                s.tankersHit++;
                news(out, date, News.Kind.BAD, "A tanker is hit and burning in the strait", null, 2.6);
            }
        } else if (rng.uniform("hormuz.attack") < 0.3) {
            s.gulfCalm = clamp(s.gulfCalm - 10, 0, 100);
            if (s.gulfCalm < 30 && s.hostsConsent) {
                s.hostsConsent = false;
                news(out, date, News.Kind.BAD, "Gulf states bar American strikes from their bases", "Iranian missiles on their oil have frightened the Gulf's rulers.", 3);
            }
            news(out, date, News.Kind.BAD, "Iranian missiles strike a Gulf oil terminal", null, 3);
        }
    }

    void flow(HormuzState s) {
        double escorted = Math.min(0.6, s.escorts * 0.15);
        double ladder = RUNG_LOSS[s.rung] * (1 - escorted * (s.rung >= 4 ? 0.5 : 1));
        double mined = Math.min(0.85, mines(s) / 120);
        double loss = 1 - (1 - ladder) * (1 - mined);
        s.flow = HormuzState.NORMAL_FLOW * (1 - loss);
        double shortfall = Math.max(0, HormuzState.NORMAL_FLOW - s.flow - s.bypass - s.spr - s.iea);
        s.insurance = s.rung >= 2 ? 1.6 * s.rung * (s.reinsured ? 0.35 : 1) : 0;
        s.premium = 3.75 * Math.min(10, shortfall) + 6 * Math.max(0, shortfall - 10) + 2.5 * s.rung + s.insurance;
    }

    void invasionDay(HormuzState s, LocalDate date, Outcome out, DeterministicRandomManager rng) {
        if (s.invasion == 0 || s.invasion == 3) return;
        s.invasionDays++;
        int o = s.invasionOption;
        if (s.invasion == 1) {
            s.guards = clamp(s.guards + 0.01, -1, 1);
            if (s.invasionDays >= BUILD_DAYS[o]) {
                s.invasion = 2; s.invasionDays = 0;
                news(out, date, News.Kind.WORLD, ("American forces move: " + INVASION[o].toLowerCase(Locale.ROOT)).toUpperCase(Locale.ROOT), null, 5);
                log(s, date, INVASION[o].toLowerCase(Locale.ROOT) + " begins");
                out.effects().add(new Effect.Rally(o == 0 ? 4 : 2));
            }
            return;
        }
        s.invasionProgress = Math.min(100, s.invasionProgress + PACE[o] * (0.6 + 0.8 * rng.uniform("hormuz.invasion")));
        int dead = (int) Math.round(LOSSES[o][0] + (LOSSES[o][1] - LOSSES[o][0]) * rng.uniform("hormuz.invasion"));
        if (dead > 0) { s.usKilled += dead; out.effects().add(new Effect.WorldShift("kia", dead)); }
        s.gulfCalm = clamp(s.gulfCalm - (o == 0 ? 0.5 : 0.2), 0, 100);
        if (s.invasionProgress >= 100) {
            s.invasion = 3;
            if (o == 0) {
                news(out, date, News.Kind.WORLD, "TEHRAN FALLS: THE ISLAMIC REPUBLIC'S GOVERNMENT COLLAPSES", "Now comes the occupation.", 6);
                out.effects().add(new Effect.WorldShift("iran", -60));
            } else {
                for (Site x : s.sites) if (!x.kind.equals("drones")) x.strength = 0;
                s.rung = Math.min(s.rung, 1);
                news(out, date, News.Kind.GOOD, ("American forces hold " + (o == 1 ? "the islands in the strait" : "Iran's coast on the strait")).toUpperCase(Locale.ROOT), null, 5);
            }
            log(s, date, INVASION[o].toLowerCase(Locale.ROOT) + " complete");
        }
    }

    void end(HormuzState s, LocalDate date, String how, boolean ok, Outcome out) {
        s.active = false;
        s.outcome = how;
        s.spr = 0; s.iea = 0;
        log(s, date, "the crisis ends: " + how.toLowerCase(Locale.ROOT));
        news(out, date, ok ? News.Kind.GOOD : News.Kind.WORLD, ("The Strait of Hormuz crisis ends: " + how.toLowerCase(Locale.ROOT)).toUpperCase(Locale.ROOT),
                Fmt.format("%d days; %d Americans killed; %d tankers seized and %d hit.", s.day, s.usKilled, s.tankersSeized, s.tankersHit), 4.5);
        out.effects().add(new Effect.Performance(ok ? 1.5 : -1));
        out.effects().add(new Effect.WorldShift("iran", how.equals("A settlement") ? -20 : 0));
        out.effects().add(new Effect.WorldShift("rel:IR", how.equals("A settlement") ? 15 : -10));
    }

    // =========================================================================================
    //  The president's orders
    // =========================================================================================

    public Outcome apply(HormuzState s, LocalDate date, HormuzOrder o, DeterministicRandomManager rng) {
        if (!s.active) throw new IllegalArgumentException("There is no crisis in the strait");
        Outcome out = Outcome.empty();
        s.message = switch (o) {
            case HormuzOrder.Escorts x -> {
                int to = Math.max(0, Math.min(4, x.groups()));
                out.effects().add(new Effect.Deficit(0.004 * (to - s.escorts)));
                s.escorts = to;
                flow(s);
                yield to == 0 ? "The escorts stand down." : to + (to == 1 ? " escort group" : " escort groups") + " now shepherd tankers through the lanes.";
            }
            case HormuzOrder.Sweep x -> {
                s.sweepers = Math.max(0, Math.min(3, x.groups()));
                yield s.sweepers == 0 ? "The minesweepers hold back." : "American mine countermeasures go to work: " + s.sweepers + (s.sweepers == 1 ? " group." : " groups.");
            }
            case HormuzOrder.Strike x -> strike(s, date, x.kind(), out, rng);
            case HormuzOrder.Envoy x -> envoy(s, date, x.channel(), out, rng);
            case HormuzOrder.Oil x -> oil(s, x.lever(), x.amount(), out);
            case HormuzOrder.Israel x -> israel(s, date, x.restrain(), out);
            case HormuzOrder.Message x -> {
                if (x.firm()) { s.anger = Math.min(1.2, s.anger + 0.08); out.effects().add(new Effect.Rally(1)); yield "You warn Iran in public: the strait stays open, whatever it takes."; }
                s.tehran = clamp(s.tehran - 0.05, -1, 1);
                s.talks = clamp(s.talks + 2, 0, 100);
                yield "You speak calmly: no one wants a war, and the door to talks is open.";
            }
            case HormuzOrder.Invade x -> invade(s, date, x.option(), out);
            case HormuzOrder.Begin x -> throw new IllegalArgumentException("A crisis is already under way");
            case HormuzOrder.Playbook x -> throw new IllegalArgumentException("The playbook is a list of orders");
        };
        return out;
    }

    String strike(HormuzState s, LocalDate date, String kind, Outcome out, DeterministicRandomManager rng) {
        int hit = 0;
        for (Site x : s.sites) if (x.kind.equals(kind)) {
            double dmg = (35 + 25 * rng.uniform("hormuz.strike")) * (s.hostsConsent ? 1 : 0.6);
            x.strength = Math.max(0, x.strength - dmg);
            x.strengthSeen = clamp(x.strength + rng.gaussian("hormuz.intel") * 10, 0, 100);
            x.struck++;
            hit++;
        }
        if (hit == 0) throw new IllegalArgumentException("No such targets");
        s.strikes++;
        s.anger = Math.min(1.2, s.anger + 0.25);
        s.talks = Math.max(0, s.talks - (s.settled ? 0 : 8));
        s.stability = clamp(s.stability - 2, 5, 100);
        out.effects().add(new Effect.WorldShift("rel:IR", -8));
        out.effects().add(new Effect.Deficit(0.01));
        if (rng.uniform("hormuz.strike") < 0.04) { s.usKilled += 2; kia(out, date, 2, "An American jet is lost over Iran's coast"); }
        news(out, date, News.Kind.WORLD, "American strikes on Iran's " + kindName(kind) + " along the strait", null, 3);
        log(s, date, "struck the " + kindName(kind));
        return "The strikes hit " + hit + (hit == 1 ? " site" : " sites") + (s.hostsConsent ? "." : ", flown from carriers alone: the Gulf states have closed their bases.");
    }

    static String kindName(String kind) {
        return switch (kind) { case "missiles" -> "anti-ship missile batteries"; case "boats" -> "fast-boat bases"; case "drones" -> "drone sites"; default -> "mine layers"; };
    }

    String envoy(HormuzState s, LocalDate date, String channel, Outcome out, DeterministicRandomManager rng) {
        switch (channel) {
            case "oman", "qatar" -> {
                if (s.settled) throw new IllegalArgumentException("A deal is already struck");
                if (s.day - s.lastTalks < 3) throw new IllegalArgumentException("The envoy is still on the road: back in " + (3 - (s.day - s.lastTalks)) + (s.day - s.lastTalks == 2 ? " day" : " days"));
                s.lastTalks = s.day;
                double gain = (7 + 7 * rng.uniform("hormuz.talks")) * (1 - 0.5 * Math.min(1, s.anger)) * (1 - 0.4 * Math.max(0, s.guards));
                s.talks = clamp(s.talks + gain, 0, 100);
                s.tehran = clamp(s.tehran - 0.05, -1, 1);
                String via = channel.equals("oman") ? "Muscat" : "Doha";
                if (s.talks >= 100) {
                    s.settled = true;
                    for (Field f : s.fields) f.known = true;
                    news(out, date, News.Kind.GOOD, "A DEAL THROUGH " + via.toUpperCase(Locale.ROOT) + ": IRAN AGREES TO REOPEN THE STRAIT",
                            "Sanctions relief for an end to the attacks, and the minefield charts. The Guards grumble.", 5);
                    log(s, date, "a deal is struck through " + via);
                    out.effects().add(new Effect.WorldShift("rel:IR", 10));
                    return "A deal through " + via + ": Iran reopens the strait and hands over its minefield charts, for relief from sanctions.";
                }
                return "An envoy carries your message to Tehran through " + via + ". The talks move (" + Math.round(s.talks) + " of 100).";
            }
            case "china", "india" -> {
                if (s.day - s.lastPress < 4) throw new IllegalArgumentException("Beijing and New Delhi were asked " + (s.day - s.lastPress == 0 ? "today" : "days ago") + "; ask again in "
                        + (4 - (s.day - s.lastPress)) + (s.day - s.lastPress == 3 ? " day" : " days"));
                s.lastPress = s.day;
                boolean cn = channel.equals("china");
                s.chinaPressure = clamp(s.chinaPressure + (cn ? 25 : 12), 0, 100);
                out.effects().add(new Effect.WorldShift(cn ? "rel:CN" : "rel:IN", cn ? 2 : 3));
                return cn ? "Beijing, the strait's biggest customer, leans on Tehran." : "New Delhi adds its voice: the oil must flow.";
            }
            case "gulf" -> {
                s.gulfCalm = clamp(s.gulfCalm + 12, 0, 100);
                if (!s.hostsConsent && s.gulfCalm >= 40) s.hostsConsent = true;
                return s.hostsConsent ? "Riyadh and Abu Dhabi stay with you; the bases are open." : "The Gulf's rulers listen, but keep their bases closed for now.";
            }
            case "allies" -> {
                if (s.alliesSweeping >= 3) throw new IllegalArgumentException("The allies' minesweepers are already there");
                s.alliesSweeping++;
                out.effects().add(new Effect.WorldShift("nato", 1));
                return "Britain, France and Japan send minesweepers (" + s.alliesSweeping + " of 3 groups).";
            }
            default -> throw new IllegalArgumentException("No such channel");
        }
    }

    String oil(HormuzState s, String lever, double amount, Outcome out) {
        switch (lever) {
            case "spr" -> {
                if (s.sprLeft <= 0) throw new IllegalArgumentException("The reserve is empty");
                s.spr = clamp(amount, 0, 4.4);
                flow(s);
                return Fmt.format("The Strategic Petroleum Reserve releases %.1f million barrels a day (%.0f million left).", s.spr, s.sprLeft);
            }
            case "iea" -> {
                s.iea = s.iea > 0 ? 0 : 2.0;
                flow(s);
                return s.iea > 0 ? "The IEA's members release two million barrels a day from their stocks." : "The coordinated release ends.";
            }
            case "bypass" -> {
                if (!s.hostsConsent) throw new IllegalArgumentException("The Gulf states won't open the pipelines while their bases are closed to you");
                s.bypassAsked = true;
                return "Riyadh and Abu Dhabi open the pipelines to Yanbu and Fujairah: up to three million barrels a day, rising over the next days.";
            }
            case "reinsure" -> {
                s.reinsured = !s.reinsured;
                if (s.reinsured) out.effects().add(new Effect.Deficit(0.01));
                flow(s);
                return s.reinsured ? "Washington reinsures tankers against war risk: insurers come back." : "The federal reinsurance ends.";
            }
            default -> throw new IllegalArgumentException("No such lever");
        }
    }

    String israel(HormuzState s, LocalDate date, boolean restrain, Outcome out) {
        s.israelRestrained = restrain;
        if (restrain) return "You ask Israel to hold its fire. It agrees, for now.";
        s.anger = Math.min(1.2, s.anger + 0.5);
        s.talks = Math.max(0, s.talks - 30);
        out.effects().add(new Effect.WorldShift("iran", -35));
        out.effects().add(new Effect.WorldShift("rel:IL", 8));
        news(out, date, News.Kind.WORLD, "ISRAEL STRIKES IRAN'S NUCLEAR SITES", "Tehran blames Washington.", 5);
        log(s, date, "Israel struck Iran's nuclear sites");
        return "Israel strikes Iran's nuclear sites. The Guards are furious.";
    }

    String invade(HormuzState s, LocalDate date, int option, Outcome out) {
        if (s.invasion != 0) throw new IllegalArgumentException("An operation is already under way");
        if (option < 0 || option > 2) throw new IllegalArgumentException("No such plan");
        s.invasion = 1; s.invasionOption = option; s.invasionDays = 0; s.invasionProgress = 0;
        out.effects().add(new Effect.Deficit(option == 0 ? 0.8 : option == 1 ? 0.1 : 0.3));
        news(out, date, News.Kind.WORLD, ("The Pentagon builds up forces in the Gulf").toUpperCase(Locale.ROOT), INVASION[option] + " is being prepared.", 4);
        log(s, date, "ordered the build-up: " + INVASION[option].toLowerCase(Locale.ROOT));
        return INVASION[option] + ": the build-up takes about " + BUILD_DAYS[option] + " days.";
    }

    /** The day's playbook (see {@link HormuzOrder.Playbook}): the orders to try, in order; a refused one is skipped. */
    public List<HormuzOrder> playbook(HormuzState s) {
        List<HormuzOrder> l = new ArrayList<>();
        if (s.escorts == 0) {
            l.add(new HormuzOrder.Escorts(2));
            l.add(new HormuzOrder.Sweep(2));
            if (s.alliesSweeping == 0) l.add(new HormuzOrder.Envoy("allies"));
            if (s.spr == 0 && s.sprLeft > 0) l.add(new HormuzOrder.Oil("spr", 1.5));
            if (!s.bypassAsked) l.add(new HormuzOrder.Oil("bypass", 0));
            if (!s.reinsured) l.add(new HormuzOrder.Oil("reinsure", 0));
        }
        if (!s.hostsConsent) l.add(new HormuzOrder.Envoy("gulf"));
        if (!s.settled && s.day - s.lastTalks >= 3) l.add(new HormuzOrder.Envoy("oman"));
        if (!s.settled && s.day - s.lastPress >= 4) l.add(new HormuzOrder.Envoy("china"));
        return l;
    }

    /** The line the crisis room shows for what was just done. */
    public void note(HormuzState s, String message) { s.message = message; }

    /** The economy and opinion have had their week of the crisis. */
    public void ticked(HormuzState s) { s.lastTick = s.day; }

    /** After the crisis: the fear in the oil price fades, more slowly after a stalemate. */
    public void afterWeek(HormuzState s) { s.premium *= "A stalemate".equals(s.outcome) ? 0.92 : 0.75; if (s.premium < 0.05) s.premium = 0; }

    // =========================================================================================
    //  Helpers
    // =========================================================================================

    /** Americans killed: the world counts them (and approval feels them); the story says how. */
    void kia(Outcome out, LocalDate date, int dead, String head) {
        out.effects().add(new Effect.WorldShift("kia", dead));
        news(out, date, News.Kind.BAD, head, dead + " Americans are killed.", 2.0);
    }

    static void news(Outcome out, LocalDate date, News.Kind kind, String head, String body, double weight) {
        out.news().add(new News(date, kind, head, body, weight, null));
    }

    static void log(HormuzState s, LocalDate date, String what) {
        s.log.add(stamp(date) + what);
        if (s.log.size() > 60) s.log.remove(0);
    }

    static String stamp(LocalDate d) {
        String m = d.getMonth().name();
        return m.charAt(0) + m.substring(1).toLowerCase(Locale.ROOT) + " " + d.getDayOfMonth() + ": ";
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
