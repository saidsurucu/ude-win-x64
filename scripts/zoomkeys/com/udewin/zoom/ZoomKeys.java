package com.udewin.zoom;

import java.awt.Component;
import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.KeyEventDispatcher;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JSlider;
import javax.swing.SwingUtilities;

/**
 * Klavye ile belge yakinlastirma: Ctrl+ / Ctrl- durum cubugundaki zoom JSlider'ini
 * surer. (Mac MacZoom'un klavye dispatcher'i; trackpad jesti Windows'a ozgu olmadigi
 * icin PORT EDILMEDI.) getMenuShortcutKeyMaskEx() Windows'ta CTRL doner.
 *
 * UDE'de zoom yalniz durum cubugundaki yatay JSlider (min=0 max=100) ile yapilabiliyordu;
 * klavye kisayolu yoktu. Bu sinif zoom mantigini yeniden yazmaz, sadece slider'i surer
 * (slider'in kendi dinleyicisi gercek zoom'u uygular).
 */
public final class ZoomKeys {

    private static final int CMD = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx(); // Windows: CTRL
    private static final int KEY_STEP_DIVISOR = 20;
    private static volatile boolean installed;

    private ZoomKeys() {}

    /** WPAppManager.main basina enjekte edilir (idempotent). */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        try {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(new KeyEventDispatcher() {
                    public boolean dispatchKeyEvent(KeyEvent e) { return dispatch(e); }
                });
        } catch (Throwable ignore) {
        }
    }

    /** Ctrl basiliyken +/= -> yakinlastir, -/_ -> uzaklastir; yalniz bu tuslari yutar. */
    static boolean dispatch(KeyEvent e) {
        try {
            if ((e.getModifiersEx() & CMD) == 0) return false;
            int dir;
            switch (e.getKeyCode()) {
                case KeyEvent.VK_EQUALS: case KeyEvent.VK_PLUS: case KeyEvent.VK_ADD:  dir = +1; break;
                case KeyEvent.VK_MINUS:  case KeyEvent.VK_SUBTRACT:                    dir = -1; break;
                default: return false;
            }
            int id = e.getID();
            if (id != KeyEvent.KEY_PRESSED && id != KeyEvent.KEY_RELEASED) return false;
            if (id == KeyEvent.KEY_PRESSED) {
                JSlider s = findSlider(e.getComponent());
                if (s != null) bump(s, dir);
            }
            return true; // Ctrl+/Ctrl- basma/birakmayi yut
        } catch (Throwable t) {
            return false;
        }
    }

    private static void bump(JSlider s, int dir) {
        int range = s.getMaximum() - s.getMinimum();
        int step = Math.max(1, range / KEY_STEP_DIVISOR);
        // Bu slider'da dusuk deger = yakinlastir; Ctrl+ (dir=+1) degeri DUSURMELI.
        int v = s.getValue() - dir * step;
        v = Math.max(s.getMinimum(), Math.min(s.getMaximum(), v));
        if (v != s.getValue()) s.setValue(v);
    }

    private static JSlider findSlider(Component focus) {
        Window w = (focus instanceof Window) ? (Window) focus
                : (focus != null ? SwingUtilities.getWindowAncestor(focus) : null);
        List<JSlider> sliders = new ArrayList<JSlider>();
        if (w != null) collect(w, sliders);
        if (sliders.isEmpty()) {
            for (Window ww : Window.getWindows()) collect(ww, sliders);
        }
        return pick(sliders);
    }

    private static JSlider pick(List<JSlider> sliders) {
        if (sliders.isEmpty()) return null;
        if (sliders.size() == 1) return sliders.get(0);
        // Birden fazlaysa: en alttaki yatay slider (durum cubugu zoom'u).
        JSlider best = null;
        for (JSlider s : sliders) {
            if (s.getOrientation() != JSlider.HORIZONTAL) continue;
            try {
                if (best == null || s.getLocationOnScreen().y > best.getLocationOnScreen().y) best = s;
            } catch (Throwable ignore) {}
        }
        return best != null ? best : sliders.get(0);
    }

    private static void collect(Component c, List<JSlider> out) {
        if (c instanceof JSlider && c.isShowing()) out.add((JSlider) c);
        if (c instanceof Container) {
            for (Component k : ((Container) c).getComponents()) collect(k, out);
        }
    }
}
