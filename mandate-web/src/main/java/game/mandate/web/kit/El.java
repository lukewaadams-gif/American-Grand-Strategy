package game.mandate.web.kit;

import org.teavm.jso.JSObject;

import java.util.ArrayList;
import java.util.List;

/**
 * An element on the page. Every kit component is one, so the screens compose them as the desktop screens compose
 * Swing components. Kept deliberately small: a tag, classes, styles, attributes, children and events.
 */
public class El {
    public final JSObject node;
    final List<El> kids = new ArrayList<>();
    double weight = 1;
    boolean fill;
    private String tip;

    public El(String tag) { this.node = Dom.create(tag); }

    protected El(JSObject node) { this.node = node; }

    /** An SVG element (charts). */
    public static El svg(String tag) { return new El(Dom.svg(tag)); }

    public static El div(String cls) { return new El("div").cls(cls); }

    public static El span(String cls) { return new El("span").cls(cls); }

    public El cls(String c) {
        if (c != null && !c.isEmpty()) for (String part : c.split(" ")) if (!part.isEmpty()) Dom.addClass(node, part);
        return this;
    }

    public El toggle(String c, boolean on) { Dom.toggleClass(node, c, on); return this; }

    public El text(String s) { Dom.text(node, s == null ? "" : s); return this; }

    public El attr(String k, String v) {
        if (v == null) Dom.removeAttr(node, k); else Dom.attr(node, k, v);
        return this;
    }

    public String attr(String k) { return Dom.getAttr(node, k); }

    public El css(String k, String v) { Dom.style(node, k, v); return this; }

    public El px(String k, int v) { return css(k, v + "px"); }

    public El add(El... cs) {
        for (El c : cs) if (c != null) { Dom.append(node, c.node); kids.add(c); }
        return this;
    }

    public El clear() { Dom.clear(node); kids.clear(); return this; }

    public List<El> children() { return List.copyOf(kids); }

    public El on(String type, Dom.Handler h) { Dom.on(node, type, h); return this; }

    public El onClick(Runnable r) { return on("click", ev -> r.run()); }

    /** The accessible name a screen reader announces. */
    public El label(String s) { return attr("aria-label", s); }

    public El role(String r) { return attr("role", r); }

    /** The explanation shown on hover (and read as the control's description). */
    public El tip(String s) {
        tip = s;
        return attr("title", s == null || s.isBlank() ? null : s);
    }

    public String getToolTipText() { return tip; }

    public void setToolTipText(String s) { tip(s); }

    public El focus() { Dom.focus(node); return this; }

    public El hidden(boolean on) { return toggle("hidden", on); }

    public String textContent() { return Dom.textOf(node); }
}
