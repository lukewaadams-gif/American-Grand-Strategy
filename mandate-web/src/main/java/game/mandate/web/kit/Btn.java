package game.mandate.web.kit;

/**
 * The one button. PRIMARY (gold, the main next step), SECONDARY, QUIET and DANGER. A disabled button is marked
 * {@code aria-disabled} rather than {@code disabled}, so it can still be focused and its reason, the tooltip, read.
 */
public class Btn extends El {
    public enum Kind { PRIMARY, SECONDARY, QUIET, DANGER }

    private final Kind kind;
    private final String label;
    private final ActionListener action;
    private boolean enabled = true;
    private boolean selected;
    private final El hintEl;

    public Btn(String label, Kind kind, ActionListener action) {
        super("button");
        this.kind = kind;
        this.label = label;
        this.action = action;
        attr("type", "button");
        cls("btn " + kind.name().toLowerCase());
        add(El.span("btn-label").text(label));
        hintEl = El.span("btn-hint");
        on("click", ev -> { if (enabled && this.action != null) this.action.actionPerformed(ev); });
    }

    public static Btn primary(String label, ActionListener a) { return new Btn(label, Kind.PRIMARY, a); }
    public static Btn secondary(String label, ActionListener a) { return new Btn(label, Kind.SECONDARY, a); }
    public static Btn quiet(String label, ActionListener a) { return new Btn(label, Kind.QUIET, a); }
    public static Btn danger(String label, ActionListener a) { return new Btn(label, Kind.DANGER, a); }

    @Override public Btn tip(String s) { super.tip(s); return this; }

    /** A keyboard hint at the right ("Ctrl+Enter"), also given to assistive technology. */
    public Btn hint(String s) {
        hintEl.text(s);
        if (!kids.contains(hintEl)) add(hintEl);
        attr("aria-keyshortcuts", s.replace("Ctrl+", "Control+"));
        return this;
    }

    public Btn disabledBecause(String reason) {
        enabled = reason == null;
        attr("aria-disabled", enabled ? null : "true");
        toggle("disabled", !enabled);
        if (reason != null) tip(reason);
        return this;
    }

    public Btn selected(boolean on) {
        selected = on;
        toggle("selected", on);
        attr("aria-pressed", on ? "true" : null);
        return this;
    }

    /** A full-width button with its label at the left (the navigation rail). */
    public Btn left() { cls("left"); return this; }

    public boolean isEnabled() { return enabled; }
    public boolean isSelectedOption() { return selected; }
    public Kind kind() { return kind; }
    public String getText() { return label; }

    /** Presses it, as a click would (used by keyboard shortcuts and tests). */
    public void doClick() { if (enabled && action != null) action.actionPerformed(null); }
}
