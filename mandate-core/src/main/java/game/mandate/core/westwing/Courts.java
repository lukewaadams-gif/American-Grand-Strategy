package game.mandate.core.westwing;

import game.mandate.core.effects.Effect;
import game.mandate.core.news.News;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.state.Party;
import game.mandate.core.westwing.WestWingEngine.Context;
import game.mandate.core.westwing.WestWingEngine.Outcome;
import game.mandate.core.westwing.WestWingEngine.SenateBloc;
import game.mandate.core.westwing.WestWingState.Action;
import game.mandate.core.westwing.WestWingState.Case;
import game.mandate.core.westwing.WestWingState.Justice;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static game.mandate.core.westwing.Actions.legal;
import static game.mandate.core.westwing.Actions.lower;
import static game.mandate.core.westwing.WestWingData.*;
import static game.mandate.core.westwing.WestWingEngine.*;

/**
 * The courts (Resolute Desk's executive/Courts): lawsuits against orders and rules through the district court, the
 * court of appeals and the Supreme Court; the president's answer to a ruling; the lower courts' judges; and the Supreme
 * Court's vacancies, nominations and confirmation votes.
 */
final class Courts {
    private final WestWingEngine e;

    Courts(WestWingEngine e) { this.e = e; }

    void sue(WestWingState s, Context c, Action a, Outcome out, DeterministicRandomManager rng) {
        Case k = new Case();
        k.action = a.id; k.stage = Case.DISTRICT;
        k.decideWeek = s.week + 3 + rng.nextInt("ww.court", 5);
        k.plaintiff = PLAINTIFFS[rng.nextInt("ww.court", PLAINTIFFS.length)];
        a.sued = true;
        s.cases.add(k);
        out.news().add(news(c, News.Kind.INFO, k.plaintiff + " sues to block the " + lower(a.name), null, 0.9, "Executive"));
    }

    /** The share of judges at a level appointed by the other party. */
    private static double oppShare(WestWingState s, Context c, int stage) {
        double d = stage == Case.DISTRICT ? s.dDistrict : s.dCircuit;
        return c.president() == Party.DEMOCRAT ? 1 - d : d;
    }

    /** The chance a court blocks an action: its legal risk after the counsel, and how many judges the other side appointed. */
    double pBlock(WestWingState s, Context c, Action a, int stage) {
        double opp = oppShare(s, c, stage);
        if (stage == Case.DISTRICT) opp = 0.5 + 0.5 * opp;      // plaintiffs choose a friendly courthouse
        return clamp(legal(s, a.legal) * (0.5 + 0.9 * opp), 0.02, 0.97);
    }

    void week(WestWingState s, Context c, Outcome out, DeterministicRandomManager rng) {
        // the lower courts
        s.vacDistrict += 1.15;
        s.vacCircuit += 0.22;
        double rate = (c.holdsSenate() ? 1.15 : 0.2) * (s.judgeStrategy == 0 ? 0.8 : s.judgeStrategy == 2 ? 1.1 : 1);
        double dc = Math.min(s.vacDistrict, rate * 0.82), cc = Math.min(s.vacCircuit, rate * 0.18);
        double tilt = s.judgeStrategy == 2 ? 0.5 : 1, mine = c.president() == Party.DEMOCRAT ? 1 : 0;
        s.dDistrict = clamp(s.dDistrict + dc * (mine - s.dDistrict) * tilt / 677, 0, 1);
        s.dCircuit = clamp(s.dCircuit + cc * (mine - s.dCircuit) * tilt / 179, 0, 1);
        s.vacDistrict -= dc; s.vacCircuit -= cc;
        s.judgeFrac += dc + cc;
        s.judges = (int) Math.floor(s.judgeFrac);
        // a vacancy on the Supreme Court, at most one a week
        int year = c.date().getYear();
        for (Justice j : new ArrayList<>(s.justices)) {
            int age = year - j.born;
            double hz = 0.00012 * Math.exp((age - 60) / 7.5);
            if (j.party == c.president() && c.holdsSenate() && age >= 70 && c.termWeek() < 150) hz += 0.0016;   // a strategic retirement
            if (j.party != c.president()) hz *= 0.6;
            if (rng.uniform("ww.scotus") < hz) { vacancy(s, c, j, age, out); break; }
        }
        // the cases
        for (Case k : new ArrayList<>(s.cases)) if (!k.done && s.week >= k.decideWeek) decide(s, c, k, out, rng);
        // the confirmation vote
        if (s.nomineeJ != null && s.scotusVoteAt >= 0 && s.week >= s.scotusVoteAt) vote(s, c, out, rng);
    }

    private void vacancy(WestWingState s, Context c, Justice j, int age, Outcome out) {
        s.justices.remove(j);
        s.scotusVacancies++;
        String why = age >= 80 ? "retires at " + age : "announces retirement";
        out.news().add(news(c, News.Kind.BAD, "Justice " + j.name + " " + why + ": a vacancy on the Supreme Court", "The Court is split "
                + count(s, Party.REPUBLICAN) + " to " + count(s, Party.DEMOCRAT) + " between justices appointed by Republicans and Democrats.", 4, null));
        s.record.add(c.date() + ": Justice " + j.name + " left the Supreme Court");
        e.info(s, c, "scotus", "A vacancy on the Supreme Court", "Justice " + j.name + " is leaving the Court. Your shortlist is in the West Wing, under the courts.");
    }

    private static int count(WestWingState s, Party p) {
        int n = 0;
        for (Justice j : s.justices) if (j.party == p) n++;
        return n;
    }

    private void decide(WestWingState s, Context c, Case k, Outcome out, DeterministicRandomManager rng) {
        Action a = s.action(k.action);
        if (a == null || a.status == Action.REVOKED || a.status == Action.UNDONE) { k.done = true; k.stage = Case.DONE; k.note = "Moot"; return; }
        if (k.stage == Case.SCOTUS) {
            int side = a.lean == Party.REPUBLICAN ? 1 : a.lean == Party.DEMOCRAT ? -1 : 0, yes = 0;
            for (Justice j : s.justices) if (rng.uniform("ww.court") < logistic(2.0 * j.ideo * side + 3.0 * (0.5 - legal(s, a.legal)) + 0.2)) yes++;
            int n = s.justices.size();
            k.votesFor = yes; k.votesAgainst = n - yes;
            k.done = true; k.stage = Case.DONE;
            if (yes * 2 > n) {
                k.won = true;
                if (a.status == Action.BLOCKED) { a.status = Action.LIVE; a.statusWeek = s.week; e.actions.apply(s, c, a, true, out); }
                out.news().add(news(c, News.Kind.GOOD, "The Supreme Court upholds the " + lower(a.name) + ", " + yes + "-" + (n - yes), null, 3.2, null));
                out.effects().add(new Effect.Performance(0.6));
            } else {
                e.actions.apply(s, c, a, false, out);
                a.status = Action.STRUCK; a.statusWeek = s.week;
                out.news().add(news(c, News.Kind.BAD, "The Supreme Court strikes down the " + lower(a.name) + ", " + (n - yes) + "-" + yes, null, 3.2, null));
                out.effects().add(new Effect.Performance(-0.8));
            }
            s.record.add(c.date() + ": the Supreme Court " + (k.won ? "upheld" : "struck down") + " the " + lower(a.name) + ", " + yes + "-" + (n - yes));
            return;
        }
        boolean against = rng.uniform("ww.court") < pBlock(s, c, a, k.stage);
        if (against) {
            if (a.status == Action.LIVE) { e.actions.apply(s, c, a, false, out); a.status = Action.BLOCKED; a.statusWeek = s.week; }
            k.blocked = true;
            out.news().add(news(c, News.Kind.BAD, "A " + (k.stage == Case.DISTRICT ? "federal judge" : "court of appeals") + " blocks the " + lower(a.name), null, 1.8, null));
            out.effects().add(new Effect.Performance(-0.3));
            double appeal = k.stage == Case.DISTRICT ? 1 - pBlock(s, c, a, Case.CIRCUIT) : 0.5;
            e.ask(s, "court", "A court blocks the " + lower(a.name),
                    k.plaintiff + " won in " + CASE_STAGE[k.stage].toLowerCase() + ". On appeal, the counsel puts our chances at about " + Math.round(appeal * 100) + "%.",
                    new String[]{"Appeal", "Rewrite it to fix the legal problems", "Let it go", "Slow-walk compliance while you appeal"},
                    new String[]{"Counsel: \"We have good arguments. Take it up.\"", "Attorney general: \"Narrow it and we win next time.\"", "Chief of staff: \"It's not worth the oxygen.\"",
                            "Domestic policy adviser: \"Comply on paper and keep going. They'll have to come after us.\""}, null, s.cases.indexOf(k) + "", a.legal > 0.6 ? 1 : 0, 1);
        } else {
            // a ruling for the president lifts an earlier block (Resolute Desk left it in place)
            if (a.status == Action.BLOCKED) { a.status = Action.LIVE; a.statusWeek = s.week; e.actions.apply(s, c, a, true, out); }
            k.blocked = false;
            out.news().add(news(c, News.Kind.GOOD, "A " + (k.stage == Case.DISTRICT ? "federal judge" : "court of appeals") + " rules for the administration on the " + lower(a.name), null, 1.2, "Executive"));
            if (rng.uniform("ww.court") < (k.stage == Case.DISTRICT ? 0.6 : 0.5)) advance(s, c, k, a, rng);
            else { k.done = true; k.won = true; k.stage = Case.DONE; }
        }
    }

    private void advance(WestWingState s, Context c, Case k, Action a, DeterministicRandomManager rng) {
        k.stage++;
        k.blocked = false;
        if (k.stage == Case.SCOTUS) {
            if (rng.uniform("ww.court") < 0.5) {         // certiorari denied: the lower court's ruling stands
                k.done = true; k.stage = Case.DONE; k.won = a.status != Action.BLOCKED; k.note = "The Supreme Court declined the case";
                return;
            }
            LocalDate d = c.date(), june = LocalDate.of(d.getYear() + (d.getMonthValue() >= 10 ? 1 : 0), 6, 28);
            k.decideWeek = (int) Math.max(s.week + 10, s.week + ChronoUnit.WEEKS.between(d, june));
        } else k.decideWeek = s.week + 8 + rng.nextInt("ww.court", 8);
    }

    void answer(WestWingState s, Context c, String ref, int code, Outcome out, DeterministicRandomManager rng) {
        int i = Integer.parseInt(ref);
        if (i < 0 || i >= s.cases.size()) return;
        Case k = s.cases.get(i);
        Action a = s.action(k.action);
        if (k.done || a == null) return;
        switch (code) {
            case 0 -> advance(s, c, k, a, rng);
            case 1 -> {
                k.done = true; k.stage = Case.DONE; k.note = "Rewritten";
                e.actions.apply(s, c, a, false, out);
                a.status = Action.REVOKED; a.statusWeek = s.week;
                Action b = new Action();
                b.id = "a" + s.nextId++; b.def = a.def; b.kind = a.kind; b.area = a.area; b.lean = a.lean; b.fx = a.fx; b.custom = a.custom; b.needs = a.needs;
                b.scope = a.scope * 0.7; b.support = a.support + 2; b.legal = a.legal * 0.55; b.name = a.name + " (revised)";
                b.policy = a.policy; b.polFrom = a.polFrom; b.polTo = a.polTo;
                b.status = Action.LIVE; b.issued = s.week; b.statusWeek = s.week;
                s.actions.add(b);
                out.news().add(news(c, News.Kind.INFO, "The administration issues a narrower " + lower(a.name), null, 1.0, "Executive"));
                e.actions.takeEffect(s, c, b, out, rng);
            }
            case 2 -> {
                k.done = true; k.stage = Case.DONE; k.note = "The administration let it go";
                a.status = Action.STRUCK; a.statusWeek = s.week;
            }
            default -> {
                advance(s, c, k, a, rng);
                s.defiance++;
                e.trouble.add(s, c, WestWingState.Scandal.DEFY, "Defying the courts", "The administration keeps enforcing the " + lower(a.name) + " despite a court's order.", 4.5, null, true, out);
                if (a.status == Action.BLOCKED) { a.status = Action.LIVE; a.statusWeek = s.week; e.actions.apply(s, c, a, true, out); }
            }
        }
    }

    // ---- the Supreme Court ---------------------------------------------------------------------------------------------

    /** The shortlist for an open seat: three judges, the same until the next vacancy or a rejection. */
    List<Justice> shortlist(WestWingState s, Context c) {
        Local r = new Local(mix(c.seed(), 0x5C07L, s.scotusVacancies, s.scotusRejected, 0));
        int side = c.side(), year = c.date().getYear();
        double[] ideo = {0.85, 0.5, 0.2};
        int[] age = {46, 53, 60};
        List<Justice> out = new ArrayList<>();
        // with real people: federal appellate judges of the president's party while any are free; newcomers from 2030
        List<game.mandate.core.people.RealPeople.Judge> real = new ArrayList<>();
        if (e.people().real()) for (var x : e.people().data().judges(c.president())) {
            boolean serving = false;
            for (Justice k : s.justices) if (k.name.equals(x.name())) serving = true;
            if (!serving) real.add(x);
        }
        for (int i = 0; i < 3; i++) {
            Justice j = new Justice();
            boolean f = r.u() < 0.5;
            j.name = "Judge " + e.pick(r, f ? "first_f" : "first_m") + " " + e.pick(r, "more_lasts");
            j.ideo = clamp(side * ideo[i] + r.g() * 0.05, -1, 1);
            j.born = year - (age[i] + r.n(4));
            if (e.people().real()) {
                if (i < real.size()) { j.name = real.get(i).name(); j.born = real.get(i).born(); }
                else if (!e.people().newcomer(year, r.u())) { if (i > 0) break; j.name = "A federal appeals judge"; }
            }
            j.party = c.president();
            out.add(j);
        }
        return out;
    }

    void nominate(WestWingState s, Context c, int option, Outcome out, DeterministicRandomManager rng) {
        if (s.justices.size() >= 9) throw new IllegalArgumentException("There is no vacancy on the Court");
        if (s.nomineeJ != null) throw new IllegalArgumentException("A nominee is already before the Senate");
        List<Justice> list = shortlist(s, c);
        if (option < 0 || option > 2 || option >= list.size()) throw new IllegalArgumentException("No such choice");
        Justice j = list.get(option);
        s.nomineeJ = j;
        s.scotusVoteAt = s.week + 7 + (rng == null ? 2 : rng.nextInt("ww.scotus", 4));
        out.news().add(news(c, News.Kind.INFO, "The president nominates " + j.name + " to the Supreme Court", SHORTLIST_WHAT[option] + ".", 3.2, null));
        if (!c.holdsSenate() && (c.midtermSeason() || c.termWeek() > 150)) {
            s.scotusVoteAt = s.week + 40;
            out.news().add(news(c, News.Kind.BAD, "The Senate majority won't consider a Supreme Court nominee in an election year", null, 2.0, null));
        }
    }

    private double blocP(Context c, SenateBloc b, Justice j) {
        double bIdeo = (b.econ() + b.cult()) / 2;
        double l = 0.6 - 2.4 * Math.abs(j.ideo - bIdeo) + (b.party() == c.president() ? 2.4 * b.loyalty() : -1.5 * c.polarization());
        return logistic(1.5 * l);
    }

    /** Expected votes for the Court's nominee, and the chance of confirmation. */
    double[] count(WestWingState s, Context c) {
        if (s.nomineeJ == null) return new double[]{0, 0, 0};
        double yes = 0, var = 0;
        for (SenateBloc b : c.senate()) { double q = blocP(c, b, s.nomineeJ); yes += b.seats() * q; var += b.seats() * q * (1 - q); }
        double sd = Math.sqrt(var) + 0.8;
        return new double[]{yes, sd, logistic(1.702 * (yes - 50 + 0.5) / sd)};
    }

    private void vote(WestWingState s, Context c, Outcome out, DeterministicRandomManager rng) {
        double[] k = count(s, c);
        int yes = (int) Math.round(clamp(k[0] + rng.gaussian("ww.scotus") * k[1], 0, 100));
        Justice j = s.nomineeJ;
        s.nomineeJ = null;
        s.scotusVoteAt = -1;
        if (yes > 50 || (yes == 50 && c.holdsSenate())) {
            j.name = j.name.replace("Judge ", "");
            s.justices.add(j);
            s.justicesAppointed++;
            out.news().add(news(c, News.Kind.GOOD, "The Senate confirms " + j.name + " to the Supreme Court, " + yes + "-" + (100 - yes), null, 4, null));
            out.effects().add(new Effect.Performance(0.8));
            out.effects().add(new Effect.BlocRelations(true, 3));
            s.record.add(c.date() + ": the Senate confirmed " + j.name + " to the Supreme Court, " + yes + "-" + (100 - yes));
        } else {
            s.scotusRejected++;
            out.news().add(news(c, News.Kind.BAD, "The Senate rejects " + j.name + " for the Supreme Court, " + yes + "-" + (100 - yes), null, 4, null));
            out.effects().add(new Effect.Performance(-1.5));
            out.effects().add(new Effect.BlocRelations(true, -2));
            s.record.add(c.date() + ": the Senate rejected " + j.name + " for the Supreme Court");
            e.info(s, c, "rejected", "The Senate rejects your nominee", j.name + " failed " + yes + "-" + (100 - yes) + ". The seat stays open; the shortlist has new names.");
        }
    }
}
