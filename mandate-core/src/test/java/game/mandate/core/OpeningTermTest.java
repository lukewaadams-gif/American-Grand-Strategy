package game.mandate.core;

import game.mandate.core.opening.Decision;
import game.mandate.core.opening.OpeningAction;
import game.mandate.core.opening.OpeningSetup;
import game.mandate.core.opening.OpeningTerm;
import game.mandate.core.opening.Report;
import game.mandate.core.opening.Strategy;
import game.mandate.core.opening.Types.BillSize;
import game.mandate.core.opening.Types.BillStage;
import game.mandate.core.opening.Types.CrisisKind;
import game.mandate.core.opening.Types.Domain;
import game.mandate.core.opening.Types.FundingOffer;
import game.mandate.core.opening.Types.NomineeType;
import game.mandate.core.opening.Types.PayFor;
import game.mandate.core.opening.Types.Post;
import game.mandate.core.opening.Types.SeatStatus;
import game.mandate.core.opening.TestAccess;
import game.mandate.core.opening.WhipRead;
import game.mandate.core.state.Party;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rules and interconnections of the 12-week prototype: each test names the design principle it guards. */
class OpeningTermTest {

    static OpeningSetup setup(long seed) { return new OpeningSetup(seed, Party.DEMOCRAT, true); }

    /** End weeks, answering any decision with a fixed option, until the given week. */
    static void advanceTo(OpeningTerm t, int week, int option) {
        while (!t.over() && t.week() < week) {
            answerAll(t, option);
            t.apply(new OpeningAction.EndWeek());
        }
    }

    /** Answering one decision can raise another (a clash opens its aftermath), so loop until none are left. */
    static void answerAll(OpeningTerm t, int option) {
        while (!t.pending().isEmpty()) {
            Decision d = t.pending().get(0);
            t.apply(new OpeningAction.Decide(d.id(), Math.min(option, d.options().size() - 1)));
        }
    }

    // ---- determinism ------------------------------------------------------------------------------

    @Test
    void sessionsAreDeterministicAndReplayable() {
        for (Strategy s : Strategy.values()) {
            OpeningTerm a = Strategy.play(setup(77), s), b = Strategy.play(setup(77), s);
            assertEquals(a.log(), b.log(), s + " is not deterministic");
            OpeningTerm r = OpeningTerm.replay(setup(77), a.history());
            assertEquals(a.report(), r.report(), s + " does not replay from its action log");
        }
    }

    @Test
    void refusedActionsLeaveTheStateUntouched() {
        OpeningTerm t = new OpeningTerm(new OpeningSetup(3, Party.DEMOCRAT, false));
        t.apply(new OpeningAction.DraftBill(BillSize.LARGE, PayFor.BORROW));
        int before = t.history().size();
        double capital = t.capital();
        assertThrows(IllegalStateException.class, () -> t.apply(new OpeningAction.Negotiate("Freedom Caucus", game.mandate.core.opening.Types.Concession.THREAT)));
        assertEquals(before, t.history().size());
        assertEquals(capital, t.capital());
    }

    // ---- principle 3: understandable consequences (interconnections) ------------------------------

    @Test
    void borrowingToPayMovesBondYieldsAboveRaisingRevenue() {
        OpeningTerm borrow = new OpeningTerm(setup(11)), revenue = new OpeningTerm(setup(11));
        borrow.apply(new OpeningAction.DraftBill(BillSize.LARGE, PayFor.BORROW));
        revenue.apply(new OpeningAction.DraftBill(BillSize.LARGE, PayFor.REVENUE));
        advanceTo(borrow, 3, 1);
        advanceTo(revenue, 3, 1);
        assertTrue(borrow.tenYear() > revenue.tenYear() + 0.03, borrow.tenYear() + " vs " + revenue.tenYear());
    }

    @Test
    void cabinetChoicesTradeTheBaseForTheOpposition() {
        OpeningTerm loyal = new OpeningTerm(setup(5)), cross = new OpeningTerm(setup(5));
        loyal.apply(new OpeningAction.Nominate(Post.STATE, NomineeType.LOYALIST));
        cross.apply(new OpeningAction.Nominate(Post.STATE, NomineeType.CROSSOVER));
        assertTrue(cross.goodwill() > loyal.goodwill() + 8);
        assertTrue(cross.wingMood(0) < loyal.wingMood(0) - 8);
        assertTrue(cross.confirmationExpected(Post.STATE) > loyal.confirmationExpected(Post.STATE) + 5,
                "crossover picks should be easier to confirm");
    }

    @Test
    void anAngryWingVotesLessLoyally() {
        OpeningTerm calm = new OpeningTerm(setup(8)), angry = new OpeningTerm(setup(8));
        for (Post p : Post.values()) angry.apply(new OpeningAction.Nominate(p, NomineeType.CROSSOVER)); // the base wing is snubbed three times
        calm.apply(new OpeningAction.DraftBill(BillSize.LARGE, PayFor.REVENUE));
        calm.apply(new OpeningAction.Nominate(Post.STATE, NomineeType.EXPERT));
        // same bill, same week; only the wing's mood differs
        OpeningTerm angry2 = angry;
        assertThrows(IllegalStateException.class, () -> angry2.apply(new OpeningAction.DraftBill(BillSize.LARGE, PayFor.REVENUE)), "out of attention");
        advanceTo(angry, 2, 1);
        advanceTo(calm, 2, 1);
        angry.apply(new OpeningAction.DraftBill(BillSize.LARGE, PayFor.REVENUE));
        String baseBloc = "Progressives";
        double calmYes = calm.whip(true).stream().filter(w -> w.bloc().equals(baseBloc)).mapToDouble(WhipRead::share).sum();
        double angryYes = angry.whip(true).stream().filter(w -> w.bloc().equals(baseBloc)).mapToDouble(WhipRead::share).sum();
        assertTrue(angry.wingMood(0) < calm.wingMood(0) - 10);
        assertTrue(angryYes <= calmYes, angryYes + " vs " + calmYes);
    }

    @Test
    void leaningOnTheFedCostsCredibilityAndRaisesLongRates() {
        OpeningTerm quiet = new OpeningTerm(setup(21)), loud = new OpeningTerm(setup(21));
        loud.apply(new OpeningAction.LeanOnFed());
        advanceTo(quiet, 5, 1);
        advanceTo(loud, 5, 1);
        assertTrue(loud.fedCredibility() < quiet.fedCredibility());
        assertTrue(loud.tenYear() > quiet.tenYear());
    }

    @Test
    void forceInTheGulfRaisesOilAndOilFeedsGasPrices() {
        long seed = seedWith(CrisisKind.GULF);
        OpeningTerm talk = new OpeningTerm(setup(seed)), force = new OpeningTerm(setup(seed));
        int crisisWeek = firstCrisisWeek(seed);
        advanceTo(talk, crisisWeek, 1);
        advanceTo(force, crisisWeek, 1);
        Decision dt = talk.pending().stream().filter(d -> d.kind() == Decision.Kind.CRISIS).findFirst().orElseThrow();
        Decision df = force.pending().stream().filter(d -> d.kind() == Decision.Kind.CRISIS).findFirst().orElseThrow();
        double before = force.oil();
        talk.apply(new OpeningAction.Decide(dt.id(), 0));
        force.apply(new OpeningAction.Decide(df.id(), 2));
        assertTrue(force.oil() > before, "a show of force in the Gulf should move oil immediately");
        assertTrue(force.oil() > talk.oil());
    }

    @Test
    void theOppositionsMoodSetsTheShutdownOdds() {
        OpeningTerm t = new OpeningTerm(setup(1));
        for (FundingOffer o : FundingOffer.values()) assertTrue(o == FundingOffer.ACCEPT_CUTS || t.fundingPassChance(o) < t.fundingPassChance(FundingOffer.ACCEPT_CUTS));
        assertTrue(t.fundingPassChance(FundingOffer.PRIORITIES) < t.fundingPassChance(FundingOffer.CLEAN));
    }

    // ---- principle 5: information guides decisions -------------------------------------------------

    @Test
    void anExpertSecretaryOfStateGivesSharperIntelligence() {
        double expertError = 0, actingError = 0;
        int n = 0;
        for (long seed = 1; seed <= 60; seed++) {
            int week = firstCrisisWeek(seed);
            OpeningTerm expert = new OpeningTerm(setup(seed)), acting = new OpeningTerm(setup(seed));
            expert.apply(new OpeningAction.Nominate(Post.STATE, NomineeType.EXPERT));
            advanceTo(expert, week, 1);
            advanceTo(acting, week, 1);
            if (expert.seat(Post.STATE) != SeatStatus.CONFIRMED) continue;
            expertError += Math.abs(expert.intelEstimate() - resolveOf(expert));
            actingError += Math.abs(acting.intelEstimate() - resolveOf(acting));
            n++;
        }
        assertTrue(n > 20);
        assertTrue(expertError / n < actingError / n, "expert " + expertError / n + " vs acting " + actingError / n);
    }

    @Test
    void readingTheIntelligenceAvoidsClashes() {
        assertTrue(clashRate(Strategy.STRATEGIST) < clashRate(Strategy.PARTISAN), "acting on intel should beat always showing force");
        assertTrue(clashRate(Strategy.STRATEGIST) < clashRate(Strategy.IDLE), "acting on intel should beat always holding");
    }

    /** Principle 2: no crisis response is always right. Force deters the weak-willed and provokes the determined. */
    @Test
    void theRightCrisisResponseDependsOnTheRivalsResolve() {
        double[] forceClashHigh = new double[2], talkClashHigh = new double[2], forceWinLow = new double[2], talkWinLow = new double[2];
        for (long seed = 1; seed <= 400; seed++) {
            for (int move : new int[]{0, 2}) {
                OpeningTerm t = new OpeningTerm(setup(seed));
                double r = resolveOf(t);
                if (r > 0.45 && r < 0.65) break;
                while (!t.over()) {
                    while (!t.pending().isEmpty()) {
                        Decision d = t.pending().get(0);
                        int o = d.kind() == Decision.Kind.CRISIS ? move : Math.min(1, d.options().size() - 1);
                        t.apply(new OpeningAction.Decide(d.id(), o));
                    }
                    t.apply(new OpeningAction.EndWeek());
                }
                var out = t.crisis();
                boolean clash = out == game.mandate.core.opening.Types.CrisisOutcome.CLASH;
                boolean win = out == game.mandate.core.opening.Types.CrisisOutcome.BACKED_DOWN || out == game.mandate.core.opening.Types.CrisisOutcome.DEAL;
                if (r >= 0.65) { double[] a = move == 2 ? forceClashHigh : talkClashHigh; a[0] += clash ? 1 : 0; a[1]++; }
                else { double[] a = move == 2 ? forceWinLow : talkWinLow; a[0] += win ? 1 : 0; a[1]++; }
            }
        }
        double fch = forceClashHigh[0] / forceClashHigh[1], tch = talkClashHigh[0] / talkClashHigh[1];
        double fwl = forceWinLow[0] / forceWinLow[1], twl = talkWinLow[0] / talkWinLow[1];
        assertTrue(fch > tch + 0.1, "against a determined rival, force should risk more clashes: " + fch + " vs " + tch);
        assertTrue(fwl > twl, "against a weak-willed rival, force should win more often: " + fwl + " vs " + twl);
    }

    @Test
    void theWhipCountPredictsTheVote() {
        // Calibration: votes called at >= 70% pass far more often than votes forced at < 40%.
        int hiCalled = 0, hiPassed = 0, loCalled = 0, loPassed = 0;
        for (long seed = 1; seed <= 150; seed++) {
            for (BillSize size : BillSize.values()) {
                OpeningTerm t = new OpeningTerm(new OpeningSetup(seed, seed % 2 == 0 ? Party.DEMOCRAT : Party.REPUBLICAN, seed % 3 != 0));
                t.apply(new OpeningAction.DraftBill(size, PayFor.REVENUE));
                t.apply(new OpeningAction.CallVote(false));
                if (t.stage() != BillStage.HOUSE_FLOOR) continue;
                advanceTo(t, 2, 1);
                double p = t.passChance(BillStage.HOUSE_FLOOR);
                t.apply(new OpeningAction.CallVote(true));
                boolean passed = t.stage() == BillStage.SENATE_FLOOR;
                if (p >= 0.7) { hiCalled++; if (passed) hiPassed++; }
                if (p < 0.4) { loCalled++; if (passed) loPassed++; }
            }
        }
        assertTrue(hiCalled > 20 && loCalled > 20, hiCalled + " / " + loCalled);
        assertTrue((double) hiPassed / hiCalled > 0.75, "high-odds pass rate " + (double) hiPassed / hiCalled);
        assertTrue((double) loPassed / loCalled < 0.4, "low-odds pass rate " + (double) loPassed / loCalled);
    }

    @Test
    void leadersRefuseToCallLosingVotesUnlessYouInsist() {
        OpeningTerm t = null;
        for (long seed = 1; seed < 100; seed++) {   // find a seed where the opposition chairman lets the bill out of committee
            OpeningTerm c = new OpeningTerm(new OpeningSetup(seed, Party.DEMOCRAT, false));
            c.apply(new OpeningAction.DraftBill(BillSize.LARGE, PayFor.BORROW));
            c.apply(new OpeningAction.CallVote(false));
            if (c.stage() == BillStage.HOUSE_FLOOR) { t = c; break; }
        }
        assertTrue(t != null, "no seed reached the House floor");
        advanceTo(t, 2, 2);
        assertTrue(t.passChance(BillStage.HOUSE_FLOOR) < 0.35);
        OpeningTerm f = t;
        double capital = f.capital();
        assertThrows(IllegalStateException.class, () -> f.apply(new OpeningAction.CallVote(false)));
        f.apply(new OpeningAction.CallVote(true));
        assertTrue(f.capital() < capital, "insisting costs capital");
    }

    // ---- principle 9: failure generates new situations ---------------------------------------------

    @Test
    void failuresOpenFollowUpDecisions() {
        assertTrue(somewhere(Decision.Kind.BILL_FAILED), "a failed vote should open a follow-up");
        assertTrue(somewhere(Decision.Kind.NOMINEE_REJECTED), "a rejected nominee should open a follow-up");
        assertTrue(somewhere(Decision.Kind.AFTERMATH), "a clash should open a follow-up");
        assertTrue(somewhere(Decision.Kind.SHUTDOWN), "a shutdown should open a follow-up");
        assertTrue(somewhere(Decision.Kind.FACTION_REVOLT), "an angry wing should revolt");
    }

    // ---- principle 6 and 10: delegation ------------------------------------------------------------

    @Test
    void aFullyDelegatedPresidencyRunsItselfInEveryCondition() {
        for (Party p : Party.values()) for (boolean unified : new boolean[]{true, false}) for (long seed = 1; seed <= 25; seed++) {
            OpeningTerm t = Strategy.play(new OpeningSetup(seed, p, unified), Strategy.DELEGATOR);
            assertTrue(t.over());
            for (Post post : Post.values()) assertTrue(t.nominee(post).isPresent(), "the chief of staff fills every post");
            assertTrue(t.stage() != BillStage.NOT_DRAFTED, "the chief of staff drafts the bill");
        }
    }

    @Test
    void undelegatedDecisionsMustBeAnswered() {
        long seed = 9;
        OpeningTerm t = new OpeningTerm(setup(seed));
        advanceTo(t, firstCrisisWeek(seed), 1);
        assertFalse(t.pending().isEmpty());
        assertThrows(IllegalStateException.class, () -> t.apply(new OpeningAction.EndWeek()));
        t.apply(new OpeningAction.Delegate(Domain.CRISIS, true));
        t.apply(new OpeningAction.EndWeek()); // now the staff answers it
    }

    // ---- principle 8: earlier choices are felt later -----------------------------------------------

    @Test
    void aWeekOneChoiceIsStillVisibleInWeekTwelve() {
        double gap = 0;
        for (long seed = 1; seed <= 30; seed++) {
            OpeningTerm loyal = new OpeningTerm(setup(seed)), cross = new OpeningTerm(setup(seed));
            loyal.apply(new OpeningAction.Nominate(Post.TREASURY, NomineeType.LOYALIST));
            cross.apply(new OpeningAction.Nominate(Post.TREASURY, NomineeType.EXPERT));
            advanceTo(loyal, 13, 1);
            advanceTo(cross, 13, 1);
            gap += loyal.report().tenYear() - cross.report().tenYear();
        }
        assertTrue(gap / 30 > 0.03, "a weaker Treasury secretary should still cost the bond market in week 12: " + gap / 30);
    }

    @Test
    void outcomesStayInBounds() {
        for (Strategy s : Strategy.values()) for (long seed = 1; seed <= 20; seed++) {
            Report r = Strategy.play(new OpeningSetup(seed, seed % 2 == 0 ? Party.DEMOCRAT : Party.REPUBLICAN, seed % 3 == 0), s).report();
            assertTrue(r.approval() > 20 && r.approval() < 80, s + " approval " + r.approval());
            assertTrue(Double.isFinite(r.tenYear()) && r.tenYear() > 1 && r.tenYear() < 9);
            assertTrue(r.capital() >= 0);
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------

    static long seedWith(CrisisKind kind) {
        for (long s = 1; s < 500; s++) if (new OpeningTerm(setup(s)).crisisKind() == kind) return s;
        throw new AssertionError();
    }

    static int firstCrisisWeek(long seed) {
        OpeningTerm t = new OpeningTerm(setup(seed));
        while (!t.over()) {
            if (t.pending().stream().anyMatch(d -> d.kind() == Decision.Kind.CRISIS)) return t.week();
            answerAll(t, 1);
            t.apply(new OpeningAction.EndWeek());
        }
        throw new AssertionError("no crisis for seed " + seed);
    }

    static double resolveOf(OpeningTerm t) { return TestAccess.resolve(t); }

    static double clashRate(Strategy s) {
        int clashes = 0, n = 200;
        for (long seed = 1; seed <= n; seed++)
            if (Strategy.play(new OpeningSetup(seed, seed % 2 == 0 ? Party.DEMOCRAT : Party.REPUBLICAN, true), s).crisis()
                    == game.mandate.core.opening.Types.CrisisOutcome.CLASH) clashes++;
        return clashes / (double) n;
    }

    /** Whether any scripted session answered a decision of this kind (decisions are logged with their titles). */
    static boolean somewhere(Decision.Kind kind) {
        String prefix = switch (kind) {
            case BILL_FAILED -> "Decision: The ";
            case NOMINEE_REJECTED -> "Decision: Nominee rejected";
            case AFTERMATH -> "Decision: After the clash";
            case SHUTDOWN -> "Decision: The shutdown continues";
            case FACTION_REVOLT -> "Decision: Revolt";
            default -> throw new IllegalArgumentException(kind.name());
        };
        for (Strategy s : Strategy.values()) for (long seed = 1; seed <= 40; seed++) for (boolean unified : new boolean[]{true, false}) {
            OpeningTerm t = Strategy.play(new OpeningSetup(seed, seed % 2 == 0 ? Party.DEMOCRAT : Party.REPUBLICAN, unified), s);
            if (t.log().stream().anyMatch(l -> l.startsWith(prefix) && (kind != Decision.Kind.BILL_FAILED || l.contains("failed ->")))) return true;
        }
        return false;
    }
}
