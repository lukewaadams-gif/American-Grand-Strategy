package game.mandate.web.kit;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * The few browser calls the interface makes, as plain JavaScript, kept in one place and deliberately small, so the
 * rest of the web code is ordinary Java and does not depend on a particular binding library's API. Local variables
 * in these scripts have long, prefixed names: TeaVM renames parameters to one-letter names when it minifies, and a
 * local called {@code a} or {@code b} would overwrite one (an exported save was once named "[object Blob].json").
 */
public final class Dom {
    private Dom() {}

    @JSFunctor public interface Handler extends JSObject { void handle(JSObject event); }
    @JSFunctor public interface Callback extends JSObject { void run(); }
    @JSFunctor public interface TextCallback extends JSObject { void accept(String text); }
    @JSFunctor public interface NumberCallback extends JSObject { void accept(double value); }

    // ---- elements ---------------------------------------------------------------------------------------------

    @JSBody(params = "tag", script = "return document.createElement(tag);")
    public static native JSObject create(String tag);

    @JSBody(params = "tag", script = "return document.createElementNS('http://www.w3.org/2000/svg', tag);")
    public static native JSObject svg(String tag);

    @JSBody(params = {"e", "k", "v"}, script = "e.setAttribute(k, v);")
    public static native void attr(JSObject e, String k, String v);

    @JSBody(params = {"e", "k"}, script = "e.removeAttribute(k);")
    public static native void removeAttr(JSObject e, String k);

    @JSBody(params = {"e", "k"}, script = "var mValue = e.getAttribute(k); return mValue === null ? null : mValue;")
    public static native String getAttr(JSObject e, String k);

    @JSBody(params = {"e", "c"}, script = "e.classList.add(c);")
    public static native void addClass(JSObject e, String c);

    @JSBody(params = {"e", "c", "on"}, script = "e.classList.toggle(c, on);")
    public static native void toggleClass(JSObject e, String c, boolean on);

    @JSBody(params = {"p", "c"}, script = "p.appendChild(c);")
    public static native void append(JSObject parent, JSObject child);

    @JSBody(params = {"e", "s"}, script = "e.textContent = s;")
    public static native void text(JSObject e, String s);

    @JSBody(params = "e", script = "return e.textContent || '';")
    public static native String textOf(JSObject e);

    @JSBody(params = {"e", "k", "v"}, script = "e.style.setProperty(k, v);")
    public static native void style(JSObject e, String k, String v);

    @JSBody(params = "e", script = "while (e.firstChild) e.removeChild(e.firstChild);")
    public static native void clear(JSObject e);

    @JSBody(params = "e", script = "if (e.parentNode) e.parentNode.removeChild(e);")
    public static native void remove(JSObject e);

    @JSBody(params = {"e", "type", "h"}, script = "e.addEventListener(type, function(ev) { h(ev); });")
    public static native void on(JSObject e, String type, Handler h);

    @JSBody(params = "e", script = "try { e.focus({preventScroll: false}); } catch (x) {}")
    public static native void focus(JSObject e);

    @JSBody(params = {"e", "v"}, script = "e.disabled = v;")
    public static native void disabled(JSObject e, boolean v);

    @JSBody(params = "e", script = "return e.value || '';")
    public static native String value(JSObject e);

    @JSBody(params = {"e", "v"}, script = "e.value = v;")
    public static native void setValue(JSObject e, String v);

    @JSBody(params = "e", script = "e.scrollTop = 0;")
    public static native void scrollTop(JSObject e);

    @JSBody(params = "e", script = "return e.scrollTop;")
    public static native int scrollTopOf(JSObject e);

    @JSBody(params = {"e", "v"}, script = "e.scrollTop = v;")
    public static native void setScrollTop(JSObject e, int v);

    @JSBody(script = "return document.body;")
    public static native JSObject body();

    @JSBody(params = "id", script = "return document.getElementById(id);")
    public static native JSObject byId(String id);

    @JSBody(params = {"root", "sel"}, script = "return root.querySelector(sel);")
    public static native JSObject query(JSObject root, String sel);

    @JSBody(params = "e", script = "return e === null || e === undefined;")
    public static native boolean isNull(JSObject e);

    @JSBody(params = {"root", "e"}, script = "return !!(root && e && root.contains(e));")
    public static native boolean contains(JSObject root, JSObject e);

    /** The focused control's accessible name and role, so a rebuilt screen can give focus back to the same control. */
    @JSBody(script = "var mActive = document.activeElement; if (!mActive || mActive === document.body) return null;"
            + "var mName = mActive.getAttribute('aria-label') || mActive.textContent || ''; return mActive.tagName + '|' + mName.trim();")
    public static native String focusKey();

    @JSBody(params = {"root", "key"}, script = "var mAll = root.querySelectorAll('button, input, [tabindex]');"
            + "for (var mI = 0; mI < mAll.length; mI++) { var mEl = mAll[mI]; var mName = mEl.getAttribute('aria-label') || mEl.textContent || '';"
            + " if (mEl.tagName + '|' + mName.trim() === key && mEl.offsetParent !== null) { mEl.focus({preventScroll: true}); return true; } } return false;")
    public static native boolean restoreFocus(JSObject root, String key);

    @JSBody(params = "root", script = "var mFirst = root.querySelector('button:not([aria-disabled=\"true\"]), input, [tabindex=\"0\"]'); if (mFirst) mFirst.focus();")
    public static native void focusFirst(JSObject root);

    /** Calls back with the element's width whenever it changes (charts draw at their real size). */
    @JSBody(params = {"e", "cb"}, script = "var mLast = -1; var mObserver = new ResizeObserver(function(mEntries) { var mWidth = Math.round(mEntries[0].contentRect.width);"
            + " if (mWidth > 0 && mWidth !== mLast) { mLast = mWidth; cb(mWidth); } }); mObserver.observe(e);")
    public static native void observeWidth(JSObject e, NumberCallback cb);

    // ---- events -----------------------------------------------------------------------------------------------

    @JSBody(params = "ev", script = "return ev.key || '';")
    public static native String key(JSObject ev);

    @JSBody(params = "ev", script = "return !!(ev.ctrlKey || ev.metaKey);")
    public static native boolean ctrl(JSObject ev);

    @JSBody(params = "ev", script = "return !!ev.shiftKey;")
    public static native boolean shift(JSObject ev);

    @JSBody(params = "ev", script = "return !!ev.altKey;")
    public static native boolean alt(JSObject ev);

    @JSBody(params = "ev", script = "ev.preventDefault();")
    public static native void prevent(JSObject ev);

    @JSBody(params = "ev", script = "ev.stopPropagation();")
    public static native void stop(JSObject ev);

    /** True when the event comes from a text field, where letters and Enter belong to the field. */
    @JSBody(params = "ev", script = "var mTarget = ev.target; return !!(mTarget && (mTarget.tagName === 'INPUT' || mTarget.tagName === 'TEXTAREA' || mTarget.tagName === 'SELECT'));")
    public static native boolean inField(JSObject ev);

    @JSBody(params = "h", script = "document.addEventListener('keydown', function(ev) { h(ev); });")
    public static native void onKeyDown(Handler h);

    @JSBody(params = "h", script = "var mTimer = null; window.addEventListener('resize', function() { clearTimeout(mTimer); mTimer = setTimeout(h, 160); });")
    public static native void onResize(Callback h);

    @JSBody(params = {"h", "ms"}, script = "return setTimeout(h, ms);")
    public static native int later(Callback h, int ms);

    @JSBody(script = "return window.innerWidth;")
    public static native int width();

    @JSBody(script = "return window.innerHeight;")
    public static native int height();

    @JSBody(params = "s", script = "document.title = s;")
    public static native void title(String s);

    @JSBody(params = {"k", "v"}, script = "document.documentElement.setAttribute(k, v);")
    public static native void rootAttr(String k, String v);

    @JSBody(params = "css", script = "var mStyle = document.getElementById('theme'); if (!mStyle) { mStyle = document.createElement('style'); mStyle.id = 'theme';"
            + " document.head.appendChild(mStyle); } mStyle.textContent = css;")
    public static native void themeCss(String css);

    // ---- storage and files ------------------------------------------------------------------------------------

    @JSBody(params = "k", script = "try { var mStored = window.localStorage.getItem(k); return mStored === null ? null : mStored; } catch (mErr) { return null; }")
    public static native String load(String k);

    @JSBody(params = {"k", "v"}, script = "try { window.localStorage.setItem(k, v); return true; } catch (mErr) { return false; }")
    public static native boolean store(String k, String v);

    @JSBody(params = "k", script = "try { window.localStorage.removeItem(k); } catch (mErr) {}")
    public static native void forget(String k);

    @JSBody(script = "try { var mKey = '__mandate_test'; window.localStorage.setItem(mKey, mKey); window.localStorage.removeItem(mKey); return true; } catch (mErr) { return false; }")
    public static native boolean storageWorks();

    /** Offers a text file for download (a save the player keeps outside the browser). */
    @JSBody(params = {"name", "text"}, script = "var mBlob = new Blob([text], {type: 'application/json'}); var mLink = document.createElement('a');"
            + " mLink.href = URL.createObjectURL(mBlob); mLink.download = name; document.body.appendChild(mLink); mLink.click();"
            + " setTimeout(function() { URL.revokeObjectURL(mLink.href); mLink.remove(); }, 1000);")
    public static native void download(String name, String text);

    /** Asks the player for a file and hands back its text. */
    @JSBody(params = "cb", script = "var mInput = document.createElement('input'); mInput.type = 'file'; mInput.accept = '.json,application/json';"
            + " mInput.onchange = function() { var mFile = mInput.files && mInput.files[0]; if (!mFile) return; var mReader = new FileReader();"
            + " mReader.onload = function() { cb(String(mReader.result)); }; mReader.readAsText(mFile); }; mInput.click();")
    public static native void pickFile(TextCallback cb);

    @JSBody(params = "s", script = "console.log(s);")
    public static native void log(String s);

    @JSBody(script = "return Date.now();")
    public static native double now();

    @JSBody(script = "return new Date().toISOString().replace('T', ' ').substring(0, 16) + ' UTC';")
    public static native String stamp();

    @JSBody(script = "return Math.floor(Math.random() * 1e9);")
    public static native int randomSeed();
}
