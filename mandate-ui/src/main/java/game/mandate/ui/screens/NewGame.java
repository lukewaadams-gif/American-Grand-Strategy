package game.mandate.ui.screens;

import game.mandate.core.campaign.Candidate;
import game.mandate.core.campaign.CandidateCatalog;
import game.mandate.core.electorate.StateCatalog;
import game.mandate.core.game.GameSetup;
import game.mandate.core.game.Scenario;
import game.mandate.core.state.Party;
import game.mandate.ui.App;
import game.mandate.ui.Settings;
import game.mandate.ui.kit.Btn;
import game.mandate.ui.kit.Card;
import game.mandate.ui.kit.Choice;
import game.mandate.ui.kit.Layouts;
import game.mandate.ui.kit.Segmented;
import game.mandate.ui.kit.Text;
import game.mandate.ui.kit.Theme;
import game.mandate.ui.kit.Ui;
import game.mandate.ui.kit.Widgets;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.util.List;
import java.util.Locale;

/**
 * The new-game flow, four steps: the game (the slice or the full term), the country (the national mood), your
 * candidate (the fictional cast, your own, or the opt-in real roster), and the rules (opponent, difficulty,
 * complexity, tips). A summary of every choice stays on screen; Back never loses a choice.
 */
public final class NewGame {
    enum Pick { CAST, CUSTOM, REAL }

    final App app;
    int step = 0;
    GameSetup.Length length = GameSetup.Length.HUNDRED_DAYS;
    Scenario scenario = Scenario.TOSS_UP;
    Party party = Party.DEMOCRAT;
    Pick pick = Pick.CAST;
    String candidateId;
    String opponentId;                  // null = let the game choose
    GameSetup.Difficulty difficulty = GameSetup.Difficulty.STANDARD;
    // the custom candidate
    String name = "Alex Morgan", home = "PA";
    int[] ratings = {7, 7, 6, 7, 6};
    int lean = 1;                        // 0 strongest, 1 mainstream, 2 moderate
    final long seed;

    static final String[] RATING = {"Charisma", "Debating", "Fundraising", "Policy", "Discipline"};
    static final String[] RATING_WHY = {"Rallies, addresses and momentum", "Debate nights", "Weekly income and fundraisers", "Diplomacy and governing", "Fewer gaffes"};
    static final List<String> STEPS = List.of("The game", "The country", "Your candidate", "The rules");

    public NewGame(App app) {
        this.app = app;
        this.seed = System.nanoTime() & 0xFFFFFFL;
    }

    public JComponent build() {
        Theme t = Theme.get();
        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) { g.setColor(t.bg); g.fillRect(0, 0, getWidth(), getHeight()); }
        };
        JPanel head = Layouts.stack(t.u(2), new Text("NEW GAME  ·  STEP " + (step + 1) + " OF 4", t.bold(Theme.Size.CAPTION), t.accent),
                Text.display(STEPS.get(step), Theme.Size.DISPLAY), stepper());
        head.setBorder(BorderFactory.createEmptyBorder(t.u(8), t.u(12), t.u(2), t.u(12)));
        root.add(head, BorderLayout.NORTH);
        JComponent body = switch (step) { case 0 -> game(); case 1 -> country(); case 2 -> candidate(); default -> rules(); };
        Layouts.Page page = new Layouts.Page(t.u(4));
        page.setBorder(BorderFactory.createEmptyBorder(t.u(4), t.u(12), t.u(8), t.u(12)));
        page.add(Parts.row(t.u(200), Parts.w(body, 2.2), Parts.w(summary(), 1)));
        root.add(Ui.scroll(page), BorderLayout.CENTER);
        root.add(footer(), BorderLayout.SOUTH);
        return root;
    }

    JComponent stepper() {
        Theme t = Theme.get();
        return new Widgets.Stages(STEPS, step, false, t.accent);
    }

    JComponent footer() {
        Theme t = Theme.get();
        JPanel f = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) { g.setColor(t.sunken); g.fillRect(0, 0, getWidth(), getHeight()); g.setColor(t.border); g.fillRect(0, 0, getWidth(), 1); }
        };
        f.setBorder(BorderFactory.createEmptyBorder(t.u(3), t.u(12), t.u(3), t.u(12)));
        f.add(Btn.secondary(step == 0 ? "Main menu" : "Back", e -> { if (step == 0) app.showMenu(); else { step--; app.refresh(); } })
                .tip(step == 0 ? "Return to the main menu (Esc)" : "Back to " + STEPS.get(step - 1) + "; your choices are kept"), BorderLayout.WEST);
        String problem = problem();
        Btn next = step < 3 ? Btn.primary("Next: " + STEPS.get(step + 1), e -> { step++; app.refresh(); })
                : Btn.primary("Begin the campaign", e -> begin());
        next.disabledBecause(problem);
        if (problem == null) next.tip(step < 3 ? "Continue to " + STEPS.get(step + 1) : "Start the 2028 general election");
        f.add(next, BorderLayout.EAST);
        return f;
    }

    /** What stops the player going on, or null. */
    String problem() {
        if (step == 2) {
            if (pick == Pick.CUSTOM) {
                Candidate c = custom();
                return c == null ? "Fix the candidate first: " + customProblem() : null;
            }
            if (candidateId == null) return "Choose a candidate first";
        }
        return null;
    }

    // ---- step 1: the game ----------------------------------------------------------------------------------------

    JComponent game() {
        Theme t = Theme.get();
        Card c = new Card("How long a game");
        c.with(new Choice("1", "The 2028 election and the first hundred days", "About an hour: the campaign, election night, the transition and fourteen weeks in office, "
                + "ending with the 100-day report. Recommended for a first game.", "Recommended", t.good, e -> { length = GameSetup.Length.HUNDRED_DAYS; app.refresh(); })
                .selected(length == GameSetup.Length.HUNDRED_DAYS));
        c.with(new Choice("2", "The full term, through re-election in 2032", "The same start, then monthly governing, the 2030 midterms and the 2032 campaign on your record.",
                null, null, e -> { length = GameSetup.Length.FULL_TERM; app.refresh(); }).selected(length == GameSetup.Length.FULL_TERM));
        return c;
    }

    // ---- step 2: the country -------------------------------------------------------------------------------------

    JComponent country() {
        Card c = new Card("The national mood");
        int i = 1;
        for (Scenario s : Scenario.values()) {
            String tag = s == Scenario.TOSS_UP ? "Even" : s == Scenario.BLUE_ENVIRONMENT ? "Leans D" : "Leans R";
            c.with(new Choice(String.valueOf(i++), s.label(), s.description(), tag, Theme.get().info, e -> { scenario = s; app.refresh(); }).selected(scenario == s));
        }
        c.with(Text.faint("The mood sets the national environment both campaigns start from. A toss-up is the fairest test of your campaign."));
        return c;
    }

    // ---- step 3: the candidate -----------------------------------------------------------------------------------

    JComponent candidate() {
        Theme t = Theme.get();
        JPanel col = Layouts.stack(t.u(4));
        Card who = new Card("Your party and who you are");
        who.with(new Segmented(List.of("Democratic", "Republican"), null, party == Party.DEMOCRAT ? 0 : 1, k -> {
            party = k == 0 ? Party.DEMOCRAT : Party.REPUBLICAN;
            candidateId = null;
            opponentId = null;
            app.refresh();
        }));
        who.with(new Segmented(List.of("The fictional cast", "Create your own", "Real politicians (opt-in)"),
                List.of("Eight invented candidates", "Spend " + Candidate.POINT_BUDGET + " points on five ratings", "Battleground 2028's real roster; not for a commercial release"),
                pick.ordinal(), k -> { pick = Pick.values()[k]; candidateId = null; opponentId = null; app.refresh(); }));
        col.add(who);
        if (pick == Pick.CUSTOM) col.add(customForm());
        else {
            Card list = new Card(pick == Pick.REAL ? "Real politicians" : "Candidates");
            if (pick == Pick.REAL) list.with(Text.muted("Ratings are Battleground 2028's estimates. The game never invents personal scandals about real people: "
                    + "campaign-level events take their place."));
            int i = 1;
            for (Candidate x : roster(party)) {
                list.with(new Choice(i <= 9 ? String.valueOf(i) : null, x.name() + " (" + party.code() + "-" + x.homeState() + ")", ratingsLine(x), lean(x), t.info,
                        e -> { candidateId = x.id(); app.refresh(); }).selected(x.id().equals(candidateId)));
                i++;
            }
            col.add(list);
        }
        return col;
    }

    List<Candidate> roster(Party p) {
        if (pick == Pick.REAL) return CandidateCatalog.load(true).forParty(p).stream().filter(Candidate::isReal).toList();
        return CandidateCatalog.load().fictional(p);
    }

    static String ratingsLine(Candidate x) {
        return String.format("Charisma %d · debate %d · fundraising %d · policy %d · discipline %d", x.charisma(), x.debate(), x.fundraising(), x.policy(), x.discipline());
    }

    static String lean(Candidate x) {
        double a = Math.abs(x.ideology());
        return a >= 0.4 ? (x.ideology() < 0 ? "Progressive" : "Conservative") : a >= 0.2 ? "Mainstream" : "Moderate";
    }

    JComponent customForm() {
        Theme t = Theme.get();
        Card c = new Card("Your candidate");
        c.with(Text.eyebrow("Name"), Ui.field(name, "Candidate name", s -> { name = s; refreshFooterLater(); }));
        c.with(Text.eyebrow("Home state (two letters)"), Ui.field(home, "Home state", s -> { home = s.trim().toUpperCase(Locale.ROOT); refreshFooterLater(); }));
        int spent = 0;
        for (int r : ratings) spent += r;
        int left = Candidate.POINT_BUDGET - spent;
        c.with(new Widgets.Meter("Points spent", spent, Candidate.POINT_BUDGET, spent + " of " + Candidate.POINT_BUDGET, left < 0 ? t.bad : t.accent,
                "Each rating costs its value in points; " + Candidate.POINT_BUDGET + " is about the fictional cast's average"));
        for (int i = 0; i < 5; i++) {
            int k = i;
            Btn minus = Btn.secondary("−", e -> { ratings[k]--; app.refresh(); }).disabledBecause(ratings[k] <= Candidate.MIN_RATING ? "The lowest rating is " + Candidate.MIN_RATING : null);
            Btn plus = Btn.secondary("+", e -> { ratings[k]++; app.refresh(); }).disabledBecause(ratings[k] >= Candidate.MAX_RATING ? "The highest rating is " + Candidate.MAX_RATING
                    : left <= 0 ? "No points left: lower another rating first" : null);
            minus.getAccessibleContext().setAccessibleName("Lower " + RATING[k]);
            plus.getAccessibleContext().setAccessibleName("Raise " + RATING[k]);
            if (minus.isEnabled()) minus.tip("Lower " + RATING[k]);
            if (plus.isEnabled()) plus.tip("Raise " + RATING[k]);
            JPanel row = Ui.border(Layouts.stack(t.px(2), Text.strong(RATING[k] + ": " + ratings[k], Theme.Size.BODY), Text.faint(RATING_WHY[k])),
                    Layouts.wrap(t.u(1), 0, minus, plus));
            c.with(row);
        }
        String strong = party == Party.DEMOCRAT ? "Progressive" : "Conservative";
        c.with(Text.eyebrow("Where you stand"), new Segmented(List.of(strong, "Mainstream", "Moderate"),
                List.of("Excites your base; costs you with swing voters", "The party's centre", "Appeals to swing voters; your base is cooler"), lean, k -> { lean = k; app.refresh(); }));
        String why = customProblem();
        if (why != null) c.with(new Text(why, t.bold(Theme.Size.SMALL), t.bad));
        return c;
    }

    void refreshFooterLater() { javax.swing.SwingUtilities.invokeLater(this::refreshFooter); }

    /** Updates only the footer while typing, so the text field keeps its focus and caret. */
    void refreshFooter() {
        JComponent base = app.stage.base();
        if (base instanceof JPanel p && p.getLayout() instanceof BorderLayout bl) {
            java.awt.Component old = bl.getLayoutComponent(BorderLayout.SOUTH);
            if (old != null) p.remove(old);
            p.add(footer(), BorderLayout.SOUTH);
            p.revalidate();
            p.repaint();
        }
    }

    double ideology() { return (party == Party.DEMOCRAT ? -1 : 1) * (lean == 0 ? 0.5 : lean == 1 ? 0.3 : 0.1); }

    String customProblem() {
        if (!StateCatalog.load().contains(home)) return "Use a two-letter state code, such as PA or OH";
        Candidate c = new Candidate(Candidate.CUSTOM_ID, party, name, home, ratings[0], ratings[1], ratings[2], ratings[3], ratings[4], ideology());
        return c.customRefusal();
    }

    Candidate custom() {
        if (customProblem() != null) return null;
        return Candidate.custom(party, name.trim(), home, ratings[0], ratings[1], ratings[2], ratings[3], ratings[4], ideology());
    }

    // ---- step 4: the rules ---------------------------------------------------------------------------------------

    JComponent rules() {
        Theme t = Theme.get();
        JPanel col = Layouts.stack(t.u(4));
        Card opp = new Card("Your opponent");
        List<Candidate> rivals = rivals();
        Candidate suggested = rivals.get((int) Math.floorMod(seed, rivals.size()));
        opp.with(new Choice("1", "Let the game choose: " + suggested.name(), ratingsLine(suggested), lean(suggested), t.info, e -> { opponentId = null; app.refresh(); })
                .selected(opponentId == null));
        int i = 2;
        for (Candidate x : rivals) {
            opp.with(new Choice(i <= 9 ? String.valueOf(i) : null, x.name() + " (" + x.party().code() + "-" + x.homeState() + ")", ratingsLine(x), lean(x), t.info,
                    e -> { opponentId = x.id(); app.refresh(); }).selected(x.id().equals(opponentId)));
            i++;
        }
        col.add(opp);
        Card rules = new Card("Difficulty and how much the screens show");
        GameSetup.Difficulty[] ds = GameSetup.Difficulty.values();
        rules.with(Text.eyebrow("Difficulty"), new Segmented(List.of("Story", "Standard", "Hard"),
                List.of("Your opponent has 80% of the money and runs one fixed campaign", "Full money; your opponent adapts to the race", "120% of the money; your opponent adapts"),
                difficulty.ordinal(), k -> { difficulty = ds[k]; app.refresh(); }));
        Settings s = app.session.settings;
        Settings.Complexity[] cs = Settings.Complexity.values();
        rules.with(Text.eyebrow("Complexity"), new Segmented(List.of(cs[0].label, cs[1].label, cs[2].label), List.of(cs[0].description, cs[1].description, cs[2].description),
                s.complexity.ordinal(), k -> { s.complexity = cs[k]; s.tips = cs[k] != Settings.Complexity.EXPERT && s.tips; app.settingsChanged(); }));
        rules.with(Text.faint(s.complexity.description + " Complexity changes what the screens show, never the rules; change it any time in Settings."));
        rules.with(Text.eyebrow("Coaching tips"), new Segmented(List.of("On", "Off"), List.of("A short tip at each new kind of decision", "No tips"), s.tips ? 0 : 1,
                k -> { s.tips = k == 0; app.settingsChanged(); }));
        col.add(rules);
        return col;
    }

    List<Candidate> rivals() {
        Party o = party.opponent();
        return pick == Pick.REAL ? CandidateCatalog.load(true).forParty(o).stream().filter(Candidate::isReal).toList() : CandidateCatalog.load().fictional(o);
    }

    // ---- the summary and the start -------------------------------------------------------------------------------

    JComponent summary() {
        Theme t = Theme.get();
        Card c = new Card("Your game");
        c.with(line("Game", length == GameSetup.Length.HUNDRED_DAYS ? "Election and first 100 days" : "Full term to 2032"),
                line("Mood", scenario.label()), line("Party", Parts.partyName(party)),
                line("Candidate", pick == Pick.CUSTOM ? name + " (your own)" : candidateId == null ? "not chosen yet" : CandidateCatalog.load(true).get(candidateId).name()),
                line("Opponent", opponentId == null ? "the game chooses" : CandidateCatalog.load(true).get(opponentId).name()),
                line("Difficulty", difficulty.name().charAt(0) + difficulty.name().substring(1).toLowerCase()),
                line("Complexity", app.session.settings.complexity.label));
        c.with(Text.faint("Game number " + seed + ". The same number replays the same world."));
        return c;
    }

    static JComponent line(String k, String v) {
        return Layouts.stack(Theme.get().px(1), Text.eyebrow(k), Text.body(v));
    }

    void begin() {
        Candidate me = pick == Pick.CUSTOM ? custom() : CandidateCatalog.load(true).get(candidateId);
        if (me == null) return;
        List<Candidate> rivals = rivals();
        String opp = opponentId != null ? opponentId : rivals.get((int) Math.floorMod(seed, rivals.size())).id();
        GameSetup setup = new GameSetup(seed, party, me.id(), opp, scenario, difficulty, length, pick == Pick.REAL, pick == Pick.CUSTOM ? me : null);
        app.session.newGame(setup);
        app.startedGame();
    }

    // ---- for tests and scripted flows ------------------------------------------------------------------------------

    public void chooseFirstCandidate() { candidateId = roster(party).get(0).id(); }
    public void next() { if (problem() == null && step < 3) step++; }
    public void start() { begin(); }
    public int step() { return step; }
}
