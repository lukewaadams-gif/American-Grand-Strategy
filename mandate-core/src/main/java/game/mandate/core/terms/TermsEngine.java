package game.mandate.core.terms;

import game.mandate.core.campaign.Candidate;
import game.mandate.core.data.Csv;
import game.mandate.core.data.Fmt;
import game.mandate.core.effects.Effect;
import game.mandate.core.news.News;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.state.Party;
import game.mandate.core.terms.TermsState.Contender;
import game.mandate.core.terms.TermsState.Question;
import game.mandate.core.terms.TermsState.Race;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Terms of office, after Resolute Desk's {@code Terms} and {@code Reelection}:
 * <ul>
 *   <li>a year before the election, whether the president runs again; a weak president draws a primary challenger,
 *       who can deny the nomination;</li>
 *   <li>a president who does not run (by choice, after two terms, or denied) campaigns for the party's nominee from the
 *       White House: rivals contest the primaries and split the party, the president can endorse one, and the nominee's
 *       race is decided on Resolute Desk's national margin and the states' leans;</li>
 *   <li>after a re-election, the second transition: the theme that sets the legacy goals, the second inaugural
 *       address, and the second term's start;</li>
 *   <li>the lame-duck decline, and the weeks between an election that chose a successor and the handoff.</li>
 * </ul>
 */
public final class TermsEngine {
    /** What the game engine reads in to this domain each week. Ratings are on Resolute Desk's 0-100 scale. */
    public record Context(LocalDate date, Party president, String presidentName, double approval, double polarization,
                          double growth, double unemployment, double unemploymentRef, double scandal, boolean unpopularWar, int difficulty,
                          double warChest, Map<String, Double> campaign, Map<String, Double> lean, Map<String, Integer> electoralVotes,
                          Map<String, Double> voteWeight, List<Candidate> ownField, List<Candidate> otherField, String vicePresident,
                          double vpCharisma, double vpDebate, double vpLoyalty, String presidentHome, LocalDate electionDay, int termsHeld,
                          long seed) {}

    public record Outcome(List<Effect> effects, List<News> news, List<String> log, boolean electionHeld) {
        static Outcome empty() { return new Outcome(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), false); }
        Outcome held() { return new Outcome(effects, news, log, true); }
    }

    /** The second term's theme: its name, what it promises, and the three legacy goals it sets. */
    public record Theme(String name, String promise, List<String> goals) {}

    public static final List<Theme> THEMES = List.of(
            new Theme("A strong economy", "Full employment, stable prices and the books in better order.", List.of("jobs", "prices", "deficit")),
            new Theme("Unfinished business", "The laws the first term could not pass, and a Court that outlasts you.", List.of("laws", "court", "midterms")),
            new Theme("Keep the White House", "Win the midterms, leave popular, and hand the office to your party.", List.of("midterms", "approval", "successor")),
            new Theme("Peace and standing", "No new war, and a president the country trusts at the end.", List.of("peace", "approval", "laws")));

    /** A legacy goal's label (what the verdict checks). */
    public static String goalLabel(String goal) {
        return switch (goal) {
            case "jobs" -> "Leave unemployment below 4.5%";
            case "prices" -> "Leave inflation below 2.5%";
            case "deficit" -> "Leave a smaller deficit than you inherited at the second inauguration";
            case "laws" -> "Sign five more laws in the second term";
            case "court" -> "Seat a Supreme Court justice in the second term";
            case "midterms" -> "Keep or win a House majority in the 2034 midterms";
            case "approval" -> "Leave office with approval of 50% or more";
            case "successor" -> "Keep the White House for your party in 2036";
            case "peace" -> "Start no war in the second term";
            default -> goal;
        };
    }

    /** What the legacy goals are judged on, at the end of the presidency (changes since the second inauguration where it says so). */
    public record GoalInputs(double lawsSince, double justicesSince, double deficitChange, double warsSince, double unemployment, double inflation,
                             double approval, Boolean midtermHouse, boolean successorWon) {}

    public static boolean goalMet(String goal, GoalInputs in) {
        return switch (goal) {
            case "jobs" -> in.unemployment() < 4.5;
            case "prices" -> in.inflation() < 2.5;
            case "deficit" -> in.deficitChange() < 0;
            case "laws" -> in.lawsSince() >= 5;
            case "court" -> in.justicesSince() >= 1;
            case "midterms" -> Boolean.TRUE.equals(in.midtermHouse());
            case "approval" -> in.approval() >= 50;
            case "successor" -> in.successorWon();
            case "peace" -> in.warsSince() <= 0;
            default -> false;
        };
    }

    /** The second term's midterms are in: did the president's party hold the House? */
    public void noteMidterms(TermsState s, boolean houseHeld) { if (s.term >= 2) s.midtermHouse = houseHeld; }

    public static final String[] INAUGURAL = {"A call to unity", "Unfinished business", "History's judgment"};

    /** The nine census divisions, for the regional swings on election night (Resolute Desk's {@code regionOf}). */
    static final String[][] REGIONS = {
            {"CT", "ME", "MA", "NH", "RI", "VT"}, {"NJ", "NY", "PA"}, {"IL", "IN", "MI", "OH", "WI"}, {"IA", "KS", "MN", "MO", "NE", "ND", "SD"},
            {"DE", "DC", "FL", "GA", "MD", "NC", "SC", "VA", "WV"}, {"AL", "KY", "MS", "TN"}, {"AR", "LA", "OK", "TX"},
            {"AZ", "CO", "ID", "MT", "NV", "NM", "UT", "WY"}, {"AK", "CA", "HI", "OR", "WA"}};

    static final String[] PRIMARY_STATES = {"Iowa", "New Hampshire", "Nevada", "South Carolina", "Michigan", "Super Tuesday", "Georgia", "Arizona",
            "Wisconsin", "Pennsylvania", "Indiana", "Oregon", "California"};

    private final Map<String, List<String>> names = new TreeMap<>();

    /** Who runs: the real roster, real senators and fictional newcomers from 2030; or the fictional cast throughout. */
    private game.mandate.core.people.People people = game.mandate.core.people.People.fictional();

    /** Sets who the races draw on (GameEngine, from the game's setup). */
    public void people(game.mandate.core.people.People p) { this.people = p; }

    public TermsEngine() {
        for (String[] r : Csv.readTsv("/data/rd/names.tsv")) names.computeIfAbsent(r[0], k -> new ArrayList<>()).add(r[1]);
    }

    public TermsState newTerms() { return new TermsState(); }

    /** Weeks to Election Day, rounded down (Resolute Desk's {@code weeksTo}). */
    static int weeksTo(Context c) { return (int) Math.floor(ChronoUnit.DAYS.between(c.date(), c.electionDay()) / 7.0); }

    // =========================================================================================
    //  The week
    // =========================================================================================

    public Outcome week(TermsState s, Context c, DeterministicRandomManager rng) {
        Outcome out = Outcome.empty();
        // questions left too long: the chief of staff takes the advice
        for (Question q : new ArrayList<>(s.questions)) if (!c.date().isBefore(q.due)) answer(s, c, q.id, q.recommended, out, rng);
        if (s.leaving || s.ending != null) return out;
        int wt = weeksTo(c);
        if (s.term == 1 && s.runStage == TermsState.RUN_NOT_ASKED && wt <= 50 && wt > 0) askToRun(s, c);
        if (s.term >= 2 && s.race == null && wt <= 50 && wt > 0) {
            beginRace(s, c, rng, out);
            question(s, c, "info", "The Race to Succeed You", "The Constitution allows two terms, and you have served them. Your party's race to succeed you begins in earnest. "
                    + "You can still help: your approval rubs off on the nominee, your endorsement can settle the primaries, and from Labor Day your campaign stops and the party's money count.",
                    List.of("Understood"), List.of("Political director: \"The primaries start in January. Watch who you're seen with.\""), 0, 4);
        }
        if (s.runStage == TermsState.RUN_RUNNING && s.challenger != null) challenge(s, c, wt, out, rng);
        Race r = s.race;
        if (r != null && !r.done) {
            raceWeek(s, c, r, wt, out, rng);
            if (!c.date().isBefore(r.electionDay)) return election(s, c, r, out, rng).held();
        }
        return out;
    }

    // ---- another four years? ----

    void askToRun(TermsState s, Context c) {
        s.runStage = TermsState.RUN_ASKED;
        int year = c.electionDay().getYear();
        question(s, c, "run", "Another Four Years?", "The " + year + " election is a year away. Your party wants to know whether you will run again. Your approval is "
                        + Math.round(c.approval()) + "%." + (c.approval() < 42 ? " At this level, expect a challenge from inside your own party." : ""),
                List.of("Announce that you're running", "Step aside: let the party choose a successor"),
                List.of("Political director: \"" + (c.approval() >= 48 ? "We're in good shape. Let's go." : c.approval() >= 42 ? "It'll be close, but incumbents usually win." : "It will be hard. But the alternative is worse.") + "\"",
                        "Chief of staff: \"" + (c.approval() < 40 ? "There's no shame in it. Some of the greats did it." : "You'd be walking away from a winnable race.") + "\""),
                c.approval() >= 36 ? 0 : 1, 6);
    }

    /** The primary challenge: every other week from February to June, a state votes (Resolute Desk's {@code Reelection.week}). */
    void challenge(TermsState s, Context c, int wt, Outcome out, DeterministicRandomManager rng) {
        if (wt > 40 || wt < 20 || wt % 2 != 0 || s.primariesHeld > 10) return;
        double share = clamp(s.challengerStrength - (c.approval() - 40) * 0.9 + rng.gaussian("terms.primary") * 4, 5, 75);
        s.challengerShare = s.challengerShare <= 0 ? share : s.challengerShare * 0.7 + share * 0.3;
        String st = PRIMARY_STATES[s.primariesHeld % PRIMARY_STATES.length];
        s.primariesHeld++;
        boolean lost = share > 50;
        out.news().add(new News(c.date(), lost ? News.Kind.BAD : News.Kind.INFO, (lost ? s.challenger + " beats the president in " : "The president wins ") + st
                + ", " + Math.round(lost ? share : 100 - share) + "-" + Math.round(lost ? 100 - share : share), null, lost ? 2.2 : 1.0, null));
        if (lost) { out.effects().add(new Effect.Performance(-0.6)); out.effects().add(new Effect.WingMood(0, -2)); }
        if (wt <= 21) {
            if (s.challengerShare > 50) {
                s.runStage = TermsState.RUN_DENIED;
                Race r = newRace(s, c);
                Contender ch = generated(c, c.president(), "chal", s.challenger, rng);
                ch.share = s.challengerShare; ch.delegates = 1;
                r.rivals.add(ch);
                r.nominee = 0;
                r.split = 60;
                s.race = r;
                out.news().add(new News(c.date(), News.Kind.BAD, ("Your party denies President " + last(c.presidentName()) + " a second nomination").toUpperCase(),
                        s.challenger + " won the primaries and will be the nominee.", 5, null));
                out.effects().add(new Effect.Performance(-2));
                s.record.add(c.date() + ": denied renomination by " + s.challenger);
                question(s, c, "info", "Denied Renomination", s.challenger + " has beaten you in the primaries and will carry the party in November. You are a lame duck for the rest of your term.",
                        List.of("Understood"), List.of("Chief of staff: \"We still have a country to run.\""), 0, 2);
            } else {
                out.news().add(new News(c.date(), News.Kind.GOOD, "The president clinches renomination over " + s.challenger, null, 2, null));
                out.effects().add(new Effect.Performance(0.5));
                s.challenger = null;
            }
        }
    }

    // ---- the race to succeed the president ----

    Race newRace(TermsState s, Context c) {
        Race r = new Race();
        r.year = c.electionDay().getYear();
        r.electionDay = c.electionDay();
        return r;
    }

    /** The party's field: the vice president, and its leading figures who are not the president (Resolute Desk's {@code successor}). */
    void beginRace(TermsState s, Context c, DeterministicRandomManager rng, Outcome out) {
        Race r = newRace(s, c);
        r.mood = rng.gaussian("terms.mood") * 1.8;
        if (c.vicePresident() != null) {
            Contender vp = new Contender();
            vp.id = "vp"; vp.name = c.vicePresident(); vp.party = c.president(); vp.role = "Vice President"; vp.home = c.presidentHome();
            vp.charisma = c.vpCharisma(); vp.debate = c.vpDebate(); vp.speech = (c.vpCharisma() + c.vpDebate()) / 2; vp.crossover = 55; vp.scandal = 30;
            vp.ideology = 0; vp.vicePresident = true;
            r.rivals.add(vp);
        }
        List<Candidate> field = new ArrayList<>(c.ownField());
        for (int i = 0; i < 2 && !field.isEmpty(); i++) {
            Candidate k = field.remove(rng.nextInt("terms.field", field.size()));
            r.rivals.add(fromCandidate(k));
        }
        while (r.rivals.size() < 3) r.rivals.add(generated(c, c.president(), "rival" + r.rivals.size(), null, rng));
        for (Contender k : r.rivals) k.share = 100.0 / r.rivals.size();
        s.race = r;
        out.news().add(new News(c.date(), News.Kind.INFO, "The race to succeed the president begins",
                String.join(", ", r.rivals.stream().map(k -> k.name).toList()) + " are expected to run.", 2.5, null));
        s.record.add(c.date() + ": the race to succeed you began");
    }

    static Contender fromCandidate(Candidate k) {
        Contender x = new Contender();
        x.id = k.id(); x.name = k.name(); x.party = k.party(); x.home = k.homeState(); x.role = "";
        x.charisma = rd(k.charisma()); x.debate = rd(k.debate()); x.speech = rd((k.charisma() + k.debate()) / 2.0);
        x.crossover = rd((k.policy() + k.discipline()) / 2.0); x.scandal = rd(11 - k.discipline()) * 0.6; x.ideology = k.ideology();
        return x;
    }

    /** A 1-10 rating on Resolute Desk's 0-100 scale. */
    static double rd(double r) { return (r - 1) * 100 / 9; }

    /** A made-up figure when the roster runs out (Resolute Desk's {@code makeCand}). */
    Contender generated(Context c, Party party, String salt, String name, DeterministicRandomManager rng) {
        Contender x = new Contender();
        boolean f = rng.uniform("terms.names") < 0.45;
        x.id = "gen_" + salt; x.party = party;
        x.name = name != null ? strip(name) : pick(f ? "first_f" : "first_m", rng) + " " + pick("lasts", rng);
        List<String> states = new ArrayList<>(c.lean().keySet());
        x.home = states.isEmpty() ? "" : states.get(rng.nextInt("terms.names", states.size()));
        x.role = (rng.uniform("terms.names") < 0.5 ? "Senator from " : "Governor of ") + x.home;
        x.charisma = 50 + rng.nextInt("terms.names", 25); x.debate = 50 + rng.nextInt("terms.names", 25); x.speech = 50 + rng.nextInt("terms.names", 25);
        x.crossover = 50 + rng.nextInt("terms.names", 25); x.scandal = 20 + rng.nextInt("terms.names", 30);
        x.ideology = (party == Party.DEMOCRAT ? -0.4 : 0.4) * (0.5 + rng.uniform("terms.names"));
        // with real people: a sitting senator of the party unless a fictional newcomer is due this year (2030 on)
        if (people.real() && name == null && !people.newcomer(c.date().getYear(), rng.uniform("terms.people"))) {
            List<game.mandate.core.people.RealPeople.Senator> pool = new ArrayList<>();
            for (var sen : people.data().senators()) if (sen.party() == party && sen.until() >= c.date().getYear()) pool.add(sen);
            if (!pool.isEmpty()) {
                var sen = pool.get(rng.nextInt("terms.people", pool.size()));
                x.name = sen.name(); x.home = sen.state(); x.role = "Senator from " + game.mandate.core.people.People.stateName(sen.state());
                x.id = "real_" + salt;
            }
        }
        return x;
    }

    /** The wing a rival speaks for: 0 the base (well to the party's side), 1 the moderates. */
    static int wing(Contender k, Party p) { return (p == Party.DEMOCRAT ? -k.ideology : k.ideology) > 0.3 ? 0 : 1; }

    static String strip(String n) { return n.replaceFirst("^(Senator|Governor) ", ""); }

    String pick(String pool, DeterministicRandomManager rng) {
        List<String> l = names.getOrDefault(pool, List.of("Alex"));
        return l.get(rng.nextInt("terms.names", l.size()));
    }

    static String last(String name) { int i = name.lastIndexOf(' '); return i < 0 ? name : name.substring(i + 1); }

    void raceWeek(TermsState s, Context c, Race r, int wt, Outcome out, DeterministicRandomManager rng) {
        if (r.nominee < 0 && wt <= 40 && wt >= 22 && wt % 2 == 0) primary(s, c, r, wt, out, rng);
        if (r.nominee < 0 && wt < 22) clinch(s, c, r, out);
        if (r.opponent == null && wt <= 24) pickOpponent(s, c, r, out, rng);
        Contender me = r.nomineeContender();
        if (wt == 15 && r.opponent != null && r.oppConvention == 0) {
            r.oppConvention = 2 + (r.opponent.speech - 60) / 20.0 + rng.gaussian("terms.convention");
            out.news().add(new News(c.date(), News.Kind.INFO, "The " + (c.president().opponent() == Party.DEMOCRAT ? "Democrats" : "Republicans") + " nominate " + r.opponent.name, null, 2.2, null));
        }
        if (wt == 11 && me != null && r.convention == 0) {
            r.convention = 2 + (me.speech - 60) / 20.0 + rng.gaussian("terms.convention") + (r.endorsed == r.nominee ? 0.5 : 0) - r.split / 60;
            out.news().add(new News(c.date(), News.Kind.INFO, "Your party nominates " + me.name + " for president", null, 2, null));
        }
        int[] at = {6, 4, 2};
        for (int i = 0; i < 3; i++) if (wt == at[i] && r.debatesHeld == i && me != null && r.opponent != null) {
            r.debatesHeld++;
            double d = clamp((me.debate - r.opponent.debate) / 25.0 + rng.gaussian("terms.debate") * 0.8, -2.5, 2.5);
            r.debate[i] = d;
            out.news().add(new News(c.date(), d >= 0 ? News.Kind.GOOD : News.Kind.BAD, (d >= 0 ? me.name : r.opponent.name) + " wins the "
                    + (i == 0 ? "first" : i == 1 ? "second" : "final") + " debate, polls say", null, 1.6, null));
        }
        r.money = clamp((c.warChest() - 20) / 40.0, -1, 2.5);
        r.convention *= 0.93; r.oppConvention *= 0.93;
        if (me != null) {
            double nat = national(s, c, r) + rng.gaussian("terms.poll") * 1.2;
            r.national = r.national == 0 ? nat : r.national * 0.6 + nat * 0.4;
        }
    }

    /** One primary night: each rival's vote by appeal, the president's endorsement and luck; the bitterness splits the party. */
    void primary(TermsState s, Context c, Race r, int wt, Outcome out, DeterministicRandomManager rng) {
        String st = PRIMARY_STATES[r.contests % PRIMARY_STATES.length];
        r.contests++;
        double[] v = new double[r.rivals.size()];
        double sum = 0;
        for (int i = 0; i < v.length; i++) {
            Contender k = r.rivals.get(i);
            // a crowded primary: appeal, the vice president's tie to the president, an endorsement, momentum and the state's own mood
            double appeal = 0.018 * (k.charisma - 60) + 0.012 * (k.debate - 60) - 0.008 * (k.scandal - 30)
                    + (k.vicePresident ? 0.015 * (c.approval() - 45) + 0.005 * (c.vpLoyalty() - 60) : 0)
                    + (r.endorsed == i ? 0.3 + 0.01 * (c.approval() - 45) : 0) + 0.15 * Math.log(Math.max(1, k.share)) + rng.gaussian("terms.primary") * 0.3;
            v[i] = Math.exp(appeal);
            sum += v[i];
        }
        int win = 0;
        for (int i = 0; i < v.length; i++) {
            v[i] = v[i] / sum * 100;
            if (v[i] > v[win]) win = i;
        }
        for (int i = 0; i < v.length; i++) {
            Contender k = r.rivals.get(i);
            k.share = k.share * (r.contests - 1) / r.contests + v[i] / r.contests;
            k.delegates += (int) Math.round(v[i] * (st.equals("Super Tuesday") ? 9 : st.equals("California") ? 4 : 1.2));
        }
        double second = 0;
        for (int i = 0; i < v.length; i++) if (i != win) second = Math.max(second, v[i]);
        if (v[win] - second < 10) { r.split = Math.min(100, r.split + 6); out.effects().add(new Effect.WingMood(1 - wing(r.rivals.get(win), c.president()), -1)); }
        else r.split = Math.max(0, r.split - 2);
        String line = r.rivals.get(win).name + " wins " + st + Fmt.format(" with %.0f%%", v[win]);
        r.primaryLog.add(c.date() + ": " + line);
        out.news().add(new News(c.date(), News.Kind.INFO, line, "The race to succeed the president.", 1.2, null));
    }

    void clinch(TermsState s, Context c, Race r, Outcome out) {
        int best = 0;
        for (int i = 0; i < r.rivals.size(); i++) if (r.rivals.get(i).delegates > r.rivals.get(best).delegates) best = i;
        r.nominee = best;
        Contender k = r.rivals.get(best);
        out.news().add(new News(c.date(), News.Kind.INFO, (k.name + " clinches the nomination").toUpperCase(),
                r.split > 50 ? "After a bitter primary, the party has work to do." : "The party unites behind its nominee.", 3, null));
        if (r.endorsed >= 0) out.effects().add(new Effect.Performance(r.endorsed == best ? 0.3 : -0.6));
        s.record.add(c.date() + ": " + k.name + " won the nomination to succeed you");
    }

    /** The other party's nominee: its strongest figure not yet beaten (Resolute Desk's {@code pickOpp}). */
    void pickOpponent(TermsState s, Context c, Race r, Outcome out, DeterministicRandomManager rng) {
        List<Candidate> field = new ArrayList<>(c.otherField());
        if (!field.isEmpty()) {
            Candidate best = null;
            double top = -1e9;
            for (Candidate k : field) {
                double q = k.quality() + rng.gaussian("terms.opp") * 0.25;
                if (q > top) { top = q; best = k; }
            }
            r.opponent = fromCandidate(best);
        } else r.opponent = generated(c, c.president().opponent(), "opp" + r.year, null, rng);
        out.news().add(new News(c.date(), News.Kind.WORLD, (r.opponent.name + " clinches the " + (c.president().opponent() == Party.DEMOCRAT ? "Democratic" : "Republican") + " nomination").toUpperCase(), null, 3, null));
    }

    /**
     * The national margin for the president's party in a race the president is not in (Resolute Desk's
     * {@code Reelection.national} with {@code running} false), before the day's noise.
     */
    public double national(TermsState s, Context c, Race r) {
        Contender me = r.nomineeContender(), op = r.opponent;
        double appK = 0.6;
        double m = appK * (c.approval() - 50) * (1.25 - 0.35 * c.polarization()) - 0.5 + 0.5 * (c.growth() - 2) - 0.3 * (c.unemployment() - c.unemploymentRef());
        if (me != null && op != null) m += 0.06 * (me.charisma - op.charisma) + 0.05 * (me.crossover - op.crossover) - 0.03 * (me.scandal - op.scandal);
        m += r.convention - r.oppConvention;
        for (double d : r.debate) m += d;
        m += r.money;
        m += (c.approval() - 48) * 0.08 * 0.5;                    // the record, half as much as when the president runs
        if (r.split > 30) m -= (r.split - 30) * 0.04;             // a divided party
        if (c.termsHeld() >= 2) m -= 2 + 1.2 * (c.termsHeld() - 2);   // time for a change
        if (c.unpopularWar()) m -= 1.5;
        m -= Math.min(4, c.scandal() * 0.08);
        if (c.difficulty() == 0) m += 1;
        if (c.difficulty() == 2) m -= 1;
        m += r.mood;
        double a = Math.abs(m);
        if (a > 12) m = (m > 0 ? 1 : -1) * (12 + (a - 12) * 0.6);
        return m;
    }

    /** A state's margin for the president's party (Resolute Desk's {@code stateMargin}). */
    public double stateMargin(Context c, Race r, String state, double nat) {
        double sign = c.president() == Party.DEMOCRAT ? 1 : -1;
        double m = sign * c.lean().getOrDefault(state, 0.0) + nat;
        Contender me = r.nomineeContender();
        if (me != null && state.equals(me.home)) m += 3;
        if (r.opponent != null && state.equals(r.opponent.home)) m -= 3;
        m += Math.min(4, c.campaign().getOrDefault(state, 0.0) * 0.9);
        return m;
    }

    /** The electoral votes the polls point to. */
    public int forecast(Context c, Race r) {
        int ev = 0;
        for (Map.Entry<String, Integer> e : c.electoralVotes().entrySet()) if (stateMargin(c, r, e.getKey(), r.national) > 0) ev += e.getValue();
        return ev;
    }

    /** Election night (Resolute Desk's {@code Reelection.election}): the national margin, regional and state swings. */
    Outcome election(TermsState s, Context c, Race r, Outcome out, DeterministicRandomManager rng) {
        if (r.nominee < 0) clinch(s, c, r, out);
        if (r.opponent == null) pickOpponent(s, c, r, out, rng);
        double nat = national(s, c, r) + rng.gaussian("terms.election") * 1.6;
        double[] reg = new double[REGIONS.length];
        for (int i = 0; i < reg.length; i++) reg[i] = rng.gaussian("terms.election") * 1.6;
        r.evMe = 0; r.evOpp = 0;
        double vMe = 0, vOpp = 0;
        for (Map.Entry<String, Integer> e : c.electoralVotes().entrySet()) {
            String st = e.getKey();
            double m = stateMargin(c, r, st, nat) + reg[region(st)] + rng.gaussian("terms.election") * 2.4;
            r.margins.put(st, Math.round(m * 10) / 10.0);
            if (m > 0) r.evMe += e.getValue(); else r.evOpp += e.getValue();
            double w = c.voteWeight().getOrDefault(st, 1.0), share = clamp(50 + m / 2, 5, 95);
            vMe += w * share; vOpp += w * (100 - share);
        }
        r.popMe = vMe / (vMe + vOpp) * 100; r.popOpp = 100 - r.popMe;
        int total = r.evMe + r.evOpp;
        r.won = r.evMe * 2 > total || r.evMe * 2 == total && nat > 0;
        r.done = true;
        Contender me = r.nomineeContender();
        String head = r.won ? me.name.toUpperCase() + " WINS THE PRESIDENCY" : r.opponent.name.toUpperCase() + " IS ELECTED PRESIDENT";
        String body = last(me.name) + " " + r.evMe + ", " + last(r.opponent.name) + " " + r.evOpp + " in the Electoral College; the popular vote "
                + Fmt.format("%.1f%% to %.1f%%.", r.popMe, r.popOpp);
        out.news().add(new News(c.date(), r.won ? News.Kind.GOOD : News.Kind.BAD, head, body, 6, null));
        out.log().add("Election Night " + r.year + ": " + head.charAt(0) + head.substring(1).toLowerCase() + ". " + body);
        s.record.add(c.date() + ": " + (r.won ? me.name + " elected to succeed you, " : r.opponent.name + " elected over " + me.name + ", ") + r.evMe + "-" + r.evOpp);
        return out;
    }

    static int region(String st) {
        for (int i = 0; i < REGIONS.length; i++) for (String x : REGIONS[i]) if (x.equals(st)) return i;
        return 0;
    }

    /** The popular-vote margin of the race just decided, for the president's party (the House and Senate's coattails). */
    public double margin(TermsState s) { return s.race == null ? 0 : s.race.popMe - s.race.popOpp; }

    // =========================================================================================
    //  Orders and questions
    // =========================================================================================

    public Outcome apply(TermsState s, Context c, TermsOrder o, DeterministicRandomManager rng) {
        Outcome out = Outcome.empty();
        switch (o) {
            case TermsOrder.Answer x -> {
                Question q = s.question(x.question());
                if (q == null) throw new IllegalArgumentException("That question is no longer on the desk");
                if (x.option() < 0 || x.option() >= q.options.size()) throw new IllegalArgumentException("No such answer");
                answer(s, c, x.question(), x.option(), out, rng);
            }
            case TermsOrder.Endorse x -> {
                String no = endorseRefusal(s, x.rival());
                if (no != null) throw new IllegalArgumentException(no);
                Race r = s.race;
                r.endorsed = x.rival();
                Contender k = r.rivals.get(x.rival());
                out.news().add(new News(c.date(), News.Kind.INFO, "The president endorses " + k.name, "The endorsement could settle the primaries; the other campaigns are furious.", 2.5, null));
                out.effects().add(new Effect.WingMood(1 - wing(k, c.president()), -3));
                r.split = Math.min(100, r.split + 5);
                s.record.add(c.date() + ": endorsed " + k.name);
            }
        }
        return out;
    }

    /** Why the president cannot endorse this rival now, or null. */
    public String endorseRefusal(TermsState s, int rival) {
        Race r = s.race;
        if (r == null) return "There is no race for the nomination";
        if (r.nominee >= 0) return "The primaries are over";
        if (r.endorsed >= 0) return "You have already endorsed " + r.rivals.get(r.endorsed).name;
        if (rival < 0 || rival >= r.rivals.size()) return "No such candidate";
        return null;
    }

    void answer(TermsState s, Context c, String id, int k, Outcome out, DeterministicRandomManager rng) {
        Question q = s.question(id);
        if (q == null) return;
        s.questions.remove(q);
        switch (q.kind) {
            case "run" -> {
                if (k == 0) {
                    s.runStage = TermsState.RUN_RUNNING;
                    out.news().add(new News(c.date(), News.Kind.INFO, ("President " + last(c.presidentName()) + " announces a campaign for a second term").toUpperCase(), null, 3, null));
                    if (c.approval() < 42 && rng.uniform("terms.challenge") < 0.4 + (42 - c.approval()) * 0.04) {
                        boolean f = rng.uniform("terms.names") < 0.4;
                        s.challenger = (rng.uniform("terms.names") < 0.5 ? "Senator " : "Governor ") + pick(f ? "first_f" : "first_m", rng) + " " + pick("lasts", rng);
                        // with real people, a sitting senator of the president's party unless a newcomer is due
                        if (people.real() && !people.newcomer(c.date().getYear(), rng.uniform("terms.people"))) {
                            List<String> pool = new ArrayList<>();
                            for (var sen : people.data().senators()) if (sen.party() == c.president() && sen.until() >= c.date().getYear()) pool.add(sen.name());
                            if (!pool.isEmpty()) s.challenger = "Senator " + pool.get(rng.nextInt("terms.people", pool.size()));
                        }
                        s.challengerStrength = 38 + (42 - c.approval()) * 1.2;
                        out.news().add(new News(c.date(), News.Kind.BAD, (s.challenger + " launches a primary challenge to the president").toUpperCase(),
                                "The challenger says the party can't win with " + last(c.presidentName()) + " at the top of the ticket.", 3, null));
                    }
                } else {
                    s.runStage = TermsState.RUN_STEPPED_ASIDE;
                    out.news().add(new News(c.date(), News.Kind.INFO, ("President " + last(c.presidentName()) + " will not seek another term").toUpperCase(),
                            "The race to succeed the president begins.", 4.5, null));
                    out.effects().add(new Effect.Performance(1));
                    s.record.add(c.date() + ": announced you would not seek a second term");
                    beginRace(s, c, rng, out);
                }
            }
            case "theme" -> {
                s.theme = k;
                s.goals.clear();
                s.goals.addAll(THEMES.get(k).goals());
                out.news().add(new News(c.date(), News.Kind.INFO, "The White House sets out the second term: " + THEMES.get(k).name().toLowerCase(), THEMES.get(k).promise(), 1.6, null));
            }
            case "inaugural" -> {
                s.inaugural = k;
                switch (k) {
                    case 0 -> { out.effects().add(new Effect.BlocRelations(-1, -1, 4)); out.effects().add(new Effect.Performance(0.6)); }
                    case 1 -> { out.effects().add(new Effect.BlocRelations(1, -1, 4)); out.effects().add(new Effect.WingMood(0, 4)); }
                    default -> { out.effects().add(new Effect.Performance(0.9)); out.effects().add(new Effect.WingMood(1, 2)); }
                }
            }
            default -> { }
        }
    }

    Question question(TermsState s, Context c, String kind, String title, String body, List<String> options, List<String> advice, int recommended, int weeks) {
        Question q = new Question();
        q.id = "t" + s.nextId++; q.kind = kind; q.title = title; q.body = body;
        q.options.addAll(options); q.advice.addAll(advice); q.recommended = recommended;
        q.due = c.date().plusWeeks(weeks);
        s.questions.add(q);
        return q;
    }

    // =========================================================================================
    //  Between the terms, and the end
    // =========================================================================================

    /** Re-elected: the second transition's questions (the theme now; the inaugural address before the oath). */
    public void reelected(TermsState s, Context c) {
        s.runStage = TermsState.RUN_RUNNING;
        List<String> advice = new ArrayList<>();
        for (Theme t : THEMES) advice.add("Chief of staff: \"" + t.promise() + " Goals: " + String.join("; ", t.goals().stream().map(g -> goalLabel(g).toLowerCase()).toList()) + ".\"");
        question(s, c, "theme", "The Second Term", "You have won four more years. History judges second terms by what they finish. What will yours be about? "
                + "The theme sets three legacy goals, and the verdict on your presidency counts each one.", THEMES.stream().map(Theme::name).toList(), advice,
                c.approval() >= 50 ? 2 : c.unemployment() > 5 || c.growth() < 1 ? 0 : 1, 6);
    }

    /** The week before the second inauguration: the address. */
    public void askInaugural(TermsState s, Context c) {
        if (s.inaugural >= 0 || s.questions.stream().anyMatch(q -> q.kind.equals("inaugural"))) return;
        question(s, c, "inaugural", "The Second Inaugural Address", "On the Capitol steps, before the oath, you will speak to the country. What is the heart of it?",
                List.of(INAUGURAL),
                List.of("Communications director: \"Reach across. The other side will have to answer it.\"", "Political director: \"Remind your party what we're here to finish.\"",
                        "Chief of staff: \"Speak to the history books. It's the one speech they'll read.\""),
                c.approval() < 47 ? 0 : 1, 1);
    }

    /** Noon on January 20: the second term begins (Resolute Desk's {@code Terms.startTerm}). */
    public Outcome secondInauguration(TermsState s, Context c, int presidentWeek, Map<String, Double> goalBase, DeterministicRandomManager rng) {
        Outcome out = Outcome.empty();
        Question pending = null;
        for (Question q : s.questions) if (q.kind.equals("inaugural") || q.kind.equals("theme")) pending = q;
        while (pending != null) {
            answer(s, c, pending.id, pending.recommended, out, rng);
            pending = null;
            for (Question q : s.questions) if (q.kind.equals("inaugural") || q.kind.equals("theme")) pending = q;
        }
        s.term++;
        s.termStartWeek = presidentWeek;
        s.runStage = TermsState.RUN_NOT_ASKED;
        s.challenger = null; s.challengerShare = 0; s.primariesHeld = 0;
        s.goalBase.clear();
        s.goalBase.putAll(goalBase);
        out.effects().add(new Effect.Performance(0.8));
        s.record.add(c.date() + ": sworn in for a second term");
        return out;
    }

    /** The cabinet posts that changed hands at the start of the second term. */
    public void departures(TermsState s, List<String> posts) { s.departures.clear(); s.departures.addAll(posts); }

    /** An election has chosen who comes next: the lame-duck weeks begin. */
    public void leave(TermsState s, String successor, Party party, LocalDate handoff, String ending) {
        s.leaving = true;
        s.successor = successor;
        s.successorParty = party;
        s.handoff = handoff;
        s.ending = ending;
    }

    /** Removed from office: the presidency ends today. */
    public void removed(TermsState s) { s.ending = "removed"; s.leaving = false; }

    /**
     * How much the president has become a lame duck, 0 to 1: a little through a second term, more after its midterms,
     * entirely once a successor is chosen. Members look past the president; nominees wait for the next one.
     */
    public static double lameDuck(TermsState s, LocalDate date, LocalDate termStart, LocalDate midterms, LocalDate termEnd, boolean midtermsHeld) {
        if (s == null) return 0;
        if (s.leaving) return 1;
        if (s.runStage == TermsState.RUN_STEPPED_ASIDE || s.runStage == TermsState.RUN_DENIED) return 0.5;
        if (s.term < 2) return 0;
        if (!midtermsHeld) return 0.1 + 0.25 * clamp(ChronoUnit.DAYS.between(termStart, date) / (double) Math.max(1, ChronoUnit.DAYS.between(termStart, midterms)), 0, 1);
        return 0.4 + 0.5 * clamp(ChronoUnit.DAYS.between(midterms, date) / (double) Math.max(1, ChronoUnit.DAYS.between(midterms, termEnd)), 0, 1);
    }

    /** Whether the president will be on the ballot at this term's presidential election. */
    public static boolean running(TermsState s) {
        return s != null && s.term == 1 && s.runStage != TermsState.RUN_STEPPED_ASIDE && s.runStage != TermsState.RUN_DENIED && s.race == null;
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
