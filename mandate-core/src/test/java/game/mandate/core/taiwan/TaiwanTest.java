package game.mandate.core.taiwan;

import game.mandate.core.data.Json;
import game.mandate.core.effects.Effect;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.taiwan.TaiwanState.Group;
import game.mandate.core.taiwan.TaiwanState.Ship;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The Taiwan crisis engine (docs/ISLAND_CHAIN.md): its models against hand-computed cases, saves, determinism, the
 *  directives, the playbook, the source's bugs fixed, speed, and the calibration against the source's own harness. */
class TaiwanTest {
    static final TaiwanEngine E = new TaiwanEngine();

    static TaiwanState start(TaiwanScenario sc, DeterministicRandomManager rng) { return E.newCrisis(LocalDate.of(sc.year(), 3, 1), sc, rng); }

    static TaiwanScenario plan(String plan, int preset) { return TaiwanScenario.standalone(2030, preset, "US").withPlan(plan); }

    static void untilWarHours(TaiwanState s, DeterministicRandomManager rng, int hours) {
        int guard = 0;
        while (!s.over() && (!s.atWar() || s.warHours() < hours) && guard++ < 2000) E.turn(s, s.date(), rng);
    }

    static TaiwanState play(TaiwanScenario sc, long seed) { return TaiwanHarness.play(E, sc, seed); }

    static Ctx ctx(TaiwanState s, long seed) { return new Ctx(s, E.data(), new DeterministicRandomManager(seed), TaiwanEngine.Outcome.empty(), s.date()); }

    /** A fresh group of {@code count} ships of one class, not part of the order of battle. */
    static Group group(Ctx c, int side, String cls, int count) {
        Group g = new Group();
        g.id = c.s.nextId++; g.side = side; g.nat = side == TaiwanState.US ? "USA" : "CHN"; g.key = "test"; g.name = "Test group"; g.tag = "test";
        g.mag = new int[c.d.munitions().size()];
        g.place = c.d.zone("PS_W"); g.zone = g.place; g.mission = TaiwanState.M_PATROL;
        TaiwanSea.addShips(c, g, c.d.need("sh:" + cls), count);
        return g;
    }

    // =================================================================================================================
    //  Arithmetic and the combat models against hand-computed cases
    // =================================================================================================================

    @Test
    void theDeterministicMathMatchesTheJdk() {
        for (double x = -30; x <= 30; x += 0.37) assertEquals(Math.exp(x), TMath.exp(x), Math.exp(x) * 1e-13, "exp " + x);
        for (double x = 0.001; x < 5000; x *= 1.7) assertEquals(Math.log(x), TMath.ln(x), 1e-13 * Math.max(1, Math.abs(Math.log(x))), "ln " + x);
        for (double a = 0.05; a < 3; a += 0.13) assertEquals(Math.pow(a, 2.2), TMath.pow(a, 2.2), Math.pow(a, 2.2) * 1e-12, "pow " + a);
        for (double r = 0.1; r < 8; r += 0.3) assertEquals(Math.tanh(Math.log(r)), TMath.tanhLn(r), 1e-12, "tanh(ln) " + r);
    }

    @Test
    void poissonDrawsHaveTheRightMean() {
        TaiwanState s = start(plan("invasion", 0), new DeterministicRandomManager(1));
        Ctx c = ctx(s, 2);
        for (double lam : new double[]{0.4, 3, 45}) {
            double sum = 0;
            int n = 20000;
            for (int i = 0; i < n; i++) sum += c.poisson("taiwan.test", lam);
            assertEquals(lam, sum / n, Math.max(0.03, lam * 0.03), "Poisson mean " + lam);
        }
    }

    @Test
    void subsonicMissilesAgainstAnUndefendedTarget() {
        // no interceptors: each missile survives launch (0.85), the attack's gross error (0.95), terminal defence (0.30, or
        // 0.79 when the defence itself fails, 5%) and guidance (0.9): 0.85 x 0.95 x (0.95 x 0.30 + 0.05 x 0.79) x 0.9 = 0.2358 hits each
        TaiwanState s = start(plan("invasion", 0), new DeterministicRandomManager(1));
        Ctx c = ctx(s, 3);
        int harpoon = c.d.munition("HARPOON");
        double sum = 0;
        int trials = 6000;
        for (int i = 0; i < trials; i++) sum += TaiwanFire.shipDefense(c, group(c, TaiwanState.PRC, "LST_072", 40), 10, harpoon, "test", TaiwanState.US);
        assertEquals(10 * 0.85 * 0.95 * (0.95 * 0.30 + 0.05 * 0.79) * 0.9, sum / trials, 0.06);
    }

    @Test
    void subsonicMissilesAgainstADestroyer() {
        // ten Harpoons at one destroyer (5 channels: 10 engagements an opportunity, two opportunities, two SM-2s each at pk 0.70):
        // a missile leaks past both with 0.09^2; then terminal defence 0.70 and guidance 0.9 as above
        TaiwanState s = start(plan("invasion", 0), new DeterministicRandomManager(1));
        Ctx c = ctx(s, 4);
        int harpoon = c.d.munition("HARPOON");
        double perMissile = 0.85 * 0.95 * (0.95 * 0.0081 * 0.30 + 0.05 * (1 - 0.21)) * 0.9;
        double sum = 0;
        int trials = 8000;
        for (int i = 0; i < trials; i++) sum += TaiwanFire.shipDefense(c, group(c, TaiwanState.US, "DDG_US", 1), 10, harpoon, "test", TaiwanState.PRC);
        assertEquals(10 * perMissile, sum / trials, 0.03);
    }

    @Test
    void ballisticMissilesAgainstPatriotsOverLand() {
        // ten short-range ballistic missiles at Kadena: the US Patriot battalion (two launchers, 32 engagements) fires two
        // interceptors at each, pk 0.65, so a missile leaks with 0.35^2; Japan's PAC-3 at Naha (21 km away) defends Japanese
        // soil the same way: expected leakers 10 x 0.1225 x 0.1225 = 0.15
        TaiwanState s = start(plan("invasion", 0), new DeterministicRandomManager(1));
        Ctx c = ctx(s, 5);
        int kadena = c.d.base("b_kadena_ab");
        TaiwanData.Base k = c.d.bases().get(kadena);
        int srbm = c.d.munition("DF11");
        double sum = 0;
        int trials = 6000;
        for (int i = 0; i < trials; i++) {
            for (TaiwanState.Battery b : s.batteries) b.ready = b.launchers * c.d.landSystems().get(b.sys).rounds();
            sum += TaiwanFire.intercept(c, TaiwanState.US, k.x(), k.y(), 10, srbm, "JPN");
        }
        assertEquals(10 * 0.1225 * 0.1225, sum / trials, 0.03);
        // with no Japanese soil to defend and Japan outside the war, only the US battery engages: 10 x 0.1225 = 1.225
        sum = 0;
        for (int i = 0; i < trials; i++) {
            for (TaiwanState.Battery b : s.batteries) b.ready = b.launchers * c.d.landSystems().get(b.sys).rounds();
            sum += TaiwanFire.intercept(c, TaiwanState.US, k.x(), k.y(), 10, srbm, "USA");
        }
        assertEquals(10 * 0.1225, sum / trials, 0.06);
    }

    @Test
    void shipDamageThresholds() {
        TaiwanState s = start(plan("invasion", 0), new DeterministicRandomManager(1));
        Ctx c = ctx(s, 6);
        Group g = group(c, TaiwanState.PRC, "DDG_052D", 1);
        Ship sh = g.ships.get(0);
        assertEquals(0, TaiwanFire.damageShip(c, g, sh, 0.3), "light damage");
        assertFalse(sh.mk);
        assertEquals(1, TaiwanFire.damageShip(c, g, sh, 0.2), "0.45 and above is a mission kill");
        assertTrue(sh.mk);
        int sunk = 0, trials = 4000;
        for (int i = 0; i < trials; i++) {
            Group h = group(c, TaiwanState.PRC, "DDG_052D", 1);
            if (TaiwanFire.damageShip(c, h, h.ships.get(0), 1.2) == 2) sunk++;
            else assertEquals(0.97, h.ships.get(0).dmg, 1e-12, "a ship that survives a killing blow is left at 0.97");
        }
        assertEquals(0.78, sunk / (double) trials, 0.03, "a ship past 1.0 sinks with 78%");
    }

    @Test
    void minesAreNeverFiredAsMissiles() {
        TaiwanState s = start(plan("invasion", 0), new DeterministicRandomManager(1));
        Ctx c = ctx(s, 7);
        int qs = c.d.munition("QUICKSTRIKE");
        assertFalse(TaiwanFire.weaponFits(c, qs, TaiwanState.TK_GROUP), "mines do not fit a ship target (the source fired them, spec 13.1)");
        assertTrue(TaiwanFire.weaponFits(c, qs, TaiwanState.TK_ZONE));
        for (TaiwanState.Squadron q : s.squadrons) if (c.d.aircraft().get(q.type).id().equals("B52") || c.d.aircraft().get(q.type).id().equals("P8"))
            assertNotEquals(qs, TaiwanFire.autoWeapon(c, q, TaiwanState.TK_GROUP));
    }

    // =================================================================================================================
    //  Saves and determinism
    // =================================================================================================================

    @Test
    void aSaveMidWarLoadsAndPlaysOnIdentically() {
        DeterministicRandomManager rng = new DeterministicRandomManager(42);
        TaiwanState s = start(plan("invasion", 0), rng);
        untilWarHours(s, rng, 30);
        assertTrue(s.atWar(), "mid-war");
        String saved = Json.write(s.toJson());
        Map<String, Long> snap = rng.snapshot();
        TaiwanState t = TaiwanState.fromJson(Json.object(Json.parse(saved)));
        assertEquals(saved, Json.write(t.toJson()), "a loaded save writes the same text");
        DeterministicRandomManager rng2 = new DeterministicRandomManager(42);
        rng2.restore(snap);
        for (int i = 0; i < 12 && !s.over(); i++) {
            E.turn(s, s.date(), rng);
            E.turn(t, t.date(), rng2);
        }
        assertEquals(Json.write(s.toJson()), Json.write(t.toJson()), "the loaded game plays on exactly as the original");
    }

    @Test
    void aCrisisSaveRoundTripsBeforeTheWar() {
        DeterministicRandomManager rng = new DeterministicRandomManager(9);
        TaiwanState s = start(TaiwanScenario.standalone(2033, 2, "PRC"), rng);
        for (int i = 0; i < 10; i++) E.turn(s, s.date(), rng);
        String saved = Json.write(s.toJson());
        assertEquals(saved, Json.write(TaiwanState.fromJson(Json.object(Json.parse(saved))).toJson()));
    }

    @Test
    void theSameSeedPlaysTheSameGame() {
        TaiwanState a = play(plan("invasion", 0), 77), b = play(plan("invasion", 0), 77);
        assertEquals(Json.write(a.toJson()), Json.write(b.toJson()));
        TaiwanState c = play(plan("invasion", 0), 78);
        assertNotEquals(Json.write(a.toJson()), Json.write(c.toJson()), "another seed, another war");
    }

    // =================================================================================================================
    //  Every plan and preset finishes
    // =================================================================================================================

    @Test
    void everyPlanAndPresetFinishes() {
        String[] plans = {"invasion", "blockade", "quarantine", "islands", "strikes", null};
        for (String p : plans)
            for (int preset = 0; preset < 3; preset++) {
                TaiwanState s = play(TaiwanScenario.standalone(2029 + preset * 3, preset, "US").withPlan(p), 100 + preset);
                assertTrue(s.over(), p + "/" + preset + " finishes");
                TaiwanResult r = E.result(s);
                assertNotNull(r.status(), p + "/" + preset + " has a status");
                assertTrue(s.day() <= 230, "within the source's horizon");
                if (p != null) assertTrue(s.dDay > 0 && s.warHours() >= 0, p + " was fought (a fixed plan goes ahead)");
                assertFalse(r.fallout().isEmpty(), "fallout for the presidency");
            }
    }

    @Test
    void outcomesMapOntoThePresidencysStatuses() {
        assertEquals(TaiwanResult.Status.DETERRED, TaiwanResult.status(0));
        for (int code : new int[]{1, 2, 9}) assertEquals(TaiwanResult.Status.DEFENDED, TaiwanResult.status(code));
        for (int code : new int[]{3, 4, 8}) assertEquals(TaiwanResult.Status.STALEMATE, TaiwanResult.status(code));
        for (int code : new int[]{5, 6}) assertEquals(TaiwanResult.Status.FALLEN, TaiwanResult.status(code));
        assertEquals(TaiwanResult.Status.NUCLEAR, TaiwanResult.status(7));
    }

    // =================================================================================================================
    //  The directives and the playbook
    // =================================================================================================================

    @Test
    void standingAsideKeepsAmericaOutAndChangesTheOutcome() {
        int fallenAside = 0, fallenIn = 0;
        for (long seed = 1; seed <= 4; seed++) {
            TaiwanScenario base = plan("invasion", 0);
            TaiwanScenario aside = new TaiwanScenario(2030, 0, "US", "invasion", 50, 1, true, 0, TaiwanScenario.Commitment.STAND_ASIDE, 55, null);
            TaiwanState a = play(aside, seed), b = play(base, seed);
            assertFalse(a.usFighting(), "standing aside, America never fights");
            assertEquals(0, a.usKilled());
            assertTrue(b.usFighting(), "intervening, it does");
            if (E.result(a).status() == TaiwanResult.Status.FALLEN) fallenAside++;
            if (E.result(b).status() == TaiwanResult.Status.FALLEN) fallenIn++;
            assertTrue(a.territoryKm2() >= b.territoryKm2() || E.result(a).status() == TaiwanResult.Status.FALLEN, "alone, Taiwan loses more ground");
        }
        assertTrue(fallenAside > fallenIn, "Taiwan falls more often alone: " + fallenAside + " against " + fallenIn);
    }

    @Test
    void mainlandStrikesAreTheDirectivesToOrder() {
        DeterministicRandomManager r1 = new DeterministicRandomManager(5), r2 = new DeterministicRandomManager(5);
        TaiwanState a = start(plan("invasion", 0), r1), b = start(plan("invasion", 0), r2);
        untilWarHours(a, r1, 2);
        untilWarHours(b, r2, 2);
        E.apply(b, b.date(), new TaiwanOrder.Mainland(1), r2);
        assertEquals(4, b.usRung(), "limited mainland strikes raise the rules of engagement to rung 4");
        assertTrue(b.heldDirectives().contains("mainland"));
        for (int i = 0; i < 40 && !a.over(); i++) E.turn(a, a.date(), r1);
        for (int i = 0; i < 40 && !b.over(); i++) E.turn(b, b.date(), r2);
        assertEquals(0, a.mainlandHits(), "the staff alone never strikes the mainland (medium skill)");
        assertTrue(b.mainlandHits() > 0, "ordered, the bombers and batteries strike ports and air bases");
    }

    @Test
    void theFleetPostureMovesTheCarriers() {
        DeterministicRandomManager rng = new DeterministicRandomManager(8);
        TaiwanState s = start(plan("blockade", 0), rng);
        untilWarHours(s, rng, 1);
        E.apply(s, s.date(), new TaiwanOrder.Fleet(2), rng);
        E.turn(s, s.date(), rng);
        int guam = E.data().zone("GUAM");
        for (Group g : s.groups)
            if (g.nat.equals("USA") && g.tag.startsWith("csg") && g.hub < 0 && !g.dead() && !g.gone && g.mission != TaiwanState.M_RETURN && g.mission != TaiwanState.M_IDLE)
                assertEquals(guam, g.targetZone(), g.name + " withdraws to Guam");
    }

    @Test
    void airPriorityDecidesWhatTheFightersDo() {
        DeterministicRandomManager r1 = new DeterministicRandomManager(11), r2 = new DeterministicRandomManager(11);
        TaiwanState a = start(plan("invasion", 0), r1), b = start(plan("invasion", 0), r2);
        E.apply(b, b.date(), new TaiwanOrder.AirPriority(0, 0, 2, 0), r2);
        assertArrayEquals(new double[]{0, 0, 1, 0}, b.airPriority(), 1e-12, "the shares are normalised");
        untilWarHours(a, r1, 12);
        untilWarHours(b, r2, 12);
        int capA = capOverTaiwan(a), capB = capOverTaiwan(b);
        assertTrue(capB > capA * 1.5, "with every fighter over Taiwan, more fly CAP there: " + capB + " against " + capA);
    }

    static int capOverTaiwan(TaiwanState s) {
        int n = 0;
        for (TaiwanState.Squadron q : s.squadrons) if (q.side == TaiwanState.US && !q.nat.equals("TWN") && q.mission == TaiwanState.AM_CAP && q.zone == E.data().taiwanZone()) n += q.n;
        return n;
    }

    @Test
    void ordersRefusedSayWhy() {
        DeterministicRandomManager rng = new DeterministicRandomManager(3);
        TaiwanState s = start(new TaiwanScenario(2031, 0, "US", null, 50, 1, true, 0, TaiwanScenario.Commitment.ARM_AND_SANCTION, 55, null), rng);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> E.apply(s, s.date(), new TaiwanOrder.DDay(40), rng));
        assertTrue(e.getMessage().contains("Beijing"), e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> E.apply(s, s.date(), new TaiwanOrder.Rung(3), rng));
        assertTrue(e.getMessage().contains("commitment"), e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> E.apply(s, s.date(), new TaiwanOrder.Diplomacy("JPN", "combat"), rng));
        assertTrue(e.getMessage().contains("fighting"), e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> E.apply(s, s.date(), new TaiwanOrder.Ceasefire(0), rng));
        assertTrue(e.getMessage().contains("war"), e.getMessage());
    }

    @Test
    void thePlaybookIsTheStaffsAndChangesNothing() {
        DeterministicRandomManager rng = new DeterministicRandomManager(21);
        TaiwanState s = start(TaiwanScenario.standalone(2032, 0, "PRC"), rng);
        String before = Json.write(s.toJson());
        List<TaiwanOrder> pb = E.playbook(s);
        assertEquals(before, Json.write(s.toJson()), "reading the playbook changes nothing");
        assertTrue(pb.stream().anyMatch(o -> o instanceof TaiwanOrder.Plan), "on day 1 the PRC staff proposes a course of action");
        E.apply(s, s.date(), new TaiwanOrder.Plan("blockade", false, false, true, false, false, 1, 2), rng);
        E.apply(s, s.date(), new TaiwanOrder.DDay(20), rng);
        E.apply(s, s.date(), new TaiwanOrder.Prepare(), rng);
        assertEquals("blockade", s.plan());
        assertTrue(s.heldDirectives().contains("plan"));
        for (int i = 0; i < 25 && !s.atWar(); i++) E.turn(s, s.date(), rng);
        assertTrue(s.atWar(), "the player's D-day holds: the staff does not overrule it");
        assertEquals(4, s.blockadeLevel(), "a lethal blockade");
        E.apply(s, s.date(), new TaiwanOrder.Playbook(), rng);
        assertTrue(s.heldDirectives().isEmpty(), "the playbook hands everything back to the staff");
    }

    @Test
    void unitOrdersHoldAUnitUntilReleased() {
        DeterministicRandomManager rng = new DeterministicRandomManager(4);
        TaiwanState s = start(plan("invasion", 0), rng);
        untilWarHours(s, rng, 2);
        E.apply(s, s.date(), new TaiwanOrder.Unit("us_f15ex_kad_1", "cap", "TAIWAN"), rng);
        TaiwanState.Squadron q = s.squadrons().stream().filter(x -> x.key().equals("us_f15ex_kad_1")).findFirst().orElseThrow();
        assertTrue(q.held());
        for (int i = 0; i < 6; i++) E.turn(s, s.date(), rng);
        assertEquals(TaiwanState.AM_CAP, q.mission(), "the staff leaves a held squadron alone");
        assertEquals(E.data().taiwanZone(), q.zone());
        E.apply(s, s.date(), new TaiwanOrder.Unit("us_f15ex_kad_1", "release", null), rng);
        assertFalse(q.held());
        assertThrows(IllegalArgumentException.class, () -> E.apply(s, s.date(), new TaiwanOrder.Unit("cn_cv1", "patrol", "EAST"), rng), "not your unit");
    }

    // =================================================================================================================
    //  The source's bugs, fixed (spec 13.1)
    // =================================================================================================================

    @Test
    void theUsDailyRoutineRunsOnEveryKindOfTurn() {
        DeterministicRandomManager rng = new DeterministicRandomManager(13);
        TaiwanState s = start(plan("strikes", 0), rng);
        untilWarHours(s, rng, 24 * 9);
        assertTrue(s.chipEmbargo(), "the chip embargo comes after 48 hours (the source's 09:00-only routine never imposed it)");
        assertEquals(3, s.usSanctions(), "and the full embargo after a week");
    }

    @Test
    void stateTheSourceKeptInStaticsIsSaved() {
        DeterministicRandomManager rng = new DeterministicRandomManager(14);
        TaiwanState s = start(plan("blockade", 0), rng);
        untilWarHours(s, rng, 24 * 20);
        assertTrue(s.chipShock() > 0.05, "the chip shock builds through inventories");
        TaiwanState t = TaiwanState.fromJson(Json.object(Json.parse(Json.write(s.toJson()))));
        assertEquals(s.chipShock(), t.chipShock(), 0, "and survives a save (the source's static chipLag did not)");
        assertEquals(s.lastKilled, t.lastKilled, 0, "as does the count behind Taiwan's will");
    }

    @Test
    void deadAssumptionsNowWork() {
        TaiwanState base = start(plan("invasion", 0), new DeterministicRandomManager(1));
        TaiwanState amphib = start(plan("invasion", 0).withAssumptions("plaAmphib=0.7"), new DeterministicRandomManager(1));
        assertTrue(amphibShips(amphib) < amphibShips(base) * 0.8, "the PLA amphibious multiplier thins the fleet (the source never read it)");
        DeterministicRandomManager r1 = new DeterministicRandomManager(2), r2 = new DeterministicRandomManager(2);
        TaiwanState a = start(plan("invasion", 0), r1), b = start(plan("invasion", 0).withAssumptions("usMobilizationLate=true"), r2);
        for (int i = 0; i < 200 && a.blueMobilized < 0 && !a.over(); i++) E.turn(a, a.date(), r1);
        for (int i = 0; i < 200 && b.blueMobilized < 0 && !b.over(); i++) E.turn(b, b.date(), r2);
        assertTrue(a.blueMobilized >= 0 && b.blueMobilized >= 0, "both mobilise");
        assertEquals(firstHubArrival(a) - a.blueMobilized + 336, firstHubArrival(b) - b.blueMobilized, 1e-9,
                "a late US mobilisation delays the reinforcements by 14 days (the source never applied it)");
    }

    static int amphibShips(TaiwanState s) {
        int n = 0;
        for (Group g : s.groups) if (g.side == TaiwanState.PRC) for (Ship sh : g.ships) { String r = E.data().shipClasses().get(sh.cls).role(); if (r.equals("amphib") || r.equals("lst")) n += sh.n; }
        return n;
    }

    static double firstHubArrival(TaiwanState s) {
        double h = 1e9;
        for (Group g : s.groups) if (g.nat.equals("USA") && g.hubArrive >= 0) h = Math.min(h, g.hubArrive);
        return h;
    }

    // =================================================================================================================
    //  Effects for the presidency
    // =================================================================================================================

    @Test
    void theWarSpeaksToThePresidencyInEffects() {
        DeterministicRandomManager rng = new DeterministicRandomManager(31);
        TaiwanState s = start(plan("invasion", 0), rng);
        boolean kia = false, rally = false, tension = false, rel = false;
        int guard = 0;
        while (!s.over() && guard++ < 400) {
            for (Effect e : E.turn(s, s.date(), rng).effects()) {
                if (e instanceof Effect.WorldShift w && w.key().equals("kia")) kia = true;
                if (e instanceof Effect.WorldShift w && w.key().startsWith("rel:")) rel = true;
                if (e instanceof Effect.Rally) rally = true;
                if (e instanceof Effect.Tension) tension = true;
            }
        }
        assertTrue(kia && tension && rel, "Americans killed, the strait's tension and the allies");
        assertTrue(rally || !s.provoked(), "a rally when China strikes first");
        TaiwanResult r = E.result(s);
        assertTrue(r.fallout().stream().anyMatch(e -> e instanceof Effect.Performance));
        assertTrue(r.fallout().stream().anyMatch(e -> e instanceof Effect.WarEnded));
        assertTrue(r.fallout().stream().anyMatch(e -> e instanceof Effect.Deficit));
        assertEquals(s.usKilled(), r.usKilled());
    }

    @Test
    void chinaCanBeThePlayer() {
        DeterministicRandomManager rng = new DeterministicRandomManager(17);
        TaiwanState s = start(TaiwanScenario.standalone(2034, 1, "PRC"), rng);
        assertEquals("PRC", s.side());
        E.apply(s, s.date(), new TaiwanOrder.Plan("islands", false, false, true, false, false, 0, 1), rng);
        E.apply(s, s.date(), new TaiwanOrder.DDay(15), rng);
        E.apply(s, s.date(), new TaiwanOrder.Prepare(), rng);
        int guard = 0;
        while (!s.over() && guard++ < 600) E.turn(s, s.date(), rng);
        assertTrue(s.over());
        assertTrue(s.warHours() > 0, "the islands were attacked");
    }

    // =================================================================================================================
    //  Speed and the data
    // =================================================================================================================

    @Test
    void turnsAreFast() {
        DeterministicRandomManager rng = new DeterministicRandomManager(19);
        TaiwanState s = start(plan("invasion", 0), rng);
        for (int i = 0; i < 5; i++) E.turn(s, s.date(), rng);   // warm up
        long t0 = System.nanoTime();
        int days = 0;
        while (!s.atWar() && days < 60) { E.turn(s, s.date(), rng); days++; }
        double crisisMs = (System.nanoTime() - t0) / 1e6 / Math.max(1, days);
        untilWarHours(s, rng, 25);
        long t1 = System.nanoTime();
        int turns = 0;
        while (!s.over() && turns < 20 && s.turnHours() == 6) { E.turn(s, s.date(), rng); turns++; }
        double warMs = (System.nanoTime() - t1) / 1e6 / Math.max(1, turns);
        assertTrue(crisisMs < 100, "a crisis day takes " + crisisMs + " ms");
        assertTrue(warMs < 100, "a six-hour war turn takes " + warMs + " ms");
    }

    @Test
    void theYearProjectsTheForces() {
        TaiwanState early = start(TaiwanScenario.standalone(2029, 0, "US"), new DeterministicRandomManager(1));
        TaiwanState late = start(TaiwanScenario.standalone(2036, 0, "US"), new DeterministicRandomManager(1));
        assertTrue(count(late, "J20") > count(early, "J20"), "more J-20s");
        assertTrue(count(late, "B21") > count(early, "B21"), "more B-21s");
        assertTrue(late.stock(TaiwanData.P_PRC, E.data().munition("DF26")) > early.stock(TaiwanData.P_PRC, E.data().munition("DF26")), "more DF-26s");
        assertTrue(late.groups.stream().anyMatch(g -> g.key.equals("cn_cv4")) && early.groups.stream().noneMatch(g -> g.key.equals("cn_cv4")), "a fourth carrier from 2032");
        assertTrue(E.data().usAntiShipProjection(2033) > E.data().usAntiShipProjection(2029));
    }

    static int count(TaiwanState s, String type) {
        int n = 0, t = E.data().need("a:" + type);
        for (TaiwanState.Squadron q : s.squadrons) if (q.type == t) n += q.n;
        return n;
    }

    @Test
    void theDataNamesNoRealShipsOrUnits() throws Exception {
        String[] banned = {"USS ", "CNS ", "ROCS ", "JS ", "HMS ", "HMAS ", "FS ", "ROKS ", "Nimitz", "Arleigh", "Ticonderoga", "Liaoning", "Shandong CSG", "Kidd",
                "Cheng Kung", "Sejong", "Charles de Gaulle", "Queen Elizabeth", "MLR)", "MEU)", "CVW-", "VMFA", "PLARF", "Presidential Office", "Hengshan"};
        for (String f : new String[]{"ships", "subs", "aircraft", "land", "oob", "bases"}) {
            try (InputStream in = TaiwanData.class.getResourceAsStream("/data/taiwan/" + f + ".csv")) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                for (String b : banned) for (String line : text.split("\n")) if (!line.startsWith("#")) assertFalse(line.contains(b), f + ".csv names \"" + b + "\": " + line);
            }
        }
    }

    // =================================================================================================================
    //  Calibration against the source's own harness (spec Appendix B), with the forces of 2027
    // =================================================================================================================

    @Test
    void calibrationAgainstTheSourcesHarness() {
        int games = 8;
        TaiwanHarness.Row inv = TaiwanHarness.batch(E, "invasion", plan("invasion", 0).withAssumptions("forcesYear=2027"), 1000, games);
        // source: 30 war days, 5,637 US killed, 18 US ships (1.5 carriers), 417 aircraft, 201 PRC ships (91 amphibious), 91% of the
        // amphibious fleet sunk, 6,111 km2 held; 8 Taiwan defended and 4 capitulations
        assertTrue(inv.usKilled() > 2500 && inv.usKilled() < 12000, inv.line());
        assertTrue(inv.usShips() > 8 && inv.usShips() < 35, inv.line());
        assertTrue(inv.usAircraft() > 200 && inv.usAircraft() < 700, inv.line());
        assertTrue(inv.prcShips() > 120 && inv.prcShips() < 280, inv.line());
        assertTrue(inv.amphibSunkShare() > 0.7, inv.line());
        assertTrue(inv.km2() < 16000, inv.line());
        assertTrue(inv.outcomes().contains("Taiwan defended"), inv.line());
        TaiwanHarness.Row isl = TaiwanHarness.batch(E, "islands", plan("islands", 0).withAssumptions("forcesYear=2027"), 1000, games);
        // source: 12 of 12 partial PRC success, no US losses, 70 PRC ships lost (31 amphibious), 308 km2
        assertEquals(games + " Partial PRC success", isl.outcomes(), isl.line());
        assertEquals(0, isl.usKilled(), 1, isl.line());
        assertTrue(isl.km2() > 250 && isl.km2() < 360, isl.line());
        assertTrue(isl.prcShips() > 40 && isl.prcShips() < 100, isl.line());
        TaiwanHarness.Row str = TaiwanHarness.batch(E, "strikes", plan("strikes", 0).withAssumptions("forcesYear=2027"), 1000, games);
        // source: 12 negotiated ceasefires (US support collapses) after 61 days, 6,055 US killed, 19 ships (1.3 carriers), 189 PRC ships
        assertTrue(str.outcomes().contains("Negotiated ceasefire"), str.line());
        assertTrue(str.warDays() > 35 && str.warDays() < 76, str.line());
        assertTrue(str.usKilled() > 3000 && str.usKilled() < 12000, str.line());
        assertTrue(str.prcShips() > 120 && str.prcShips() < 260, str.line());
        TaiwanHarness.Row blk = TaiwanHarness.batch(E, "blockade", plan("blockade", 0).withAssumptions("forcesYear=2027"), 1000, games);
        // source: the day-75 judgement every time (Taiwan defended), 1,287 US killed, 204 PRC ships, no territory taken
        assertTrue(blk.warDays() > 50, blk.line());
        assertTrue(blk.km2() < 1, blk.line());
        assertTrue(blk.usKilled() > 300 && blk.usKilled() < 5000, blk.line());
        assertTrue(blk.prcShips() > 120 && blk.prcShips() < 260, blk.line());
    }
}
