package macospasterich;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.KeyEventDispatcher;

import javax.swing.text.JTextComponent;

/**
 * Ctrl+Shift+V = Formatlı Yapıştır. Mac'te bu iş textkeys agent'ındaki
 * MacShortcutRemap'in Fb.RICH_PASTE dalında; textkeys Windows'a port edilmedi
 * (native Ctrl), karşılığı bu küçük dispatcher (ZoomKeys deseni).
 *
 * Odaktaki UDE editöründe (hj türevi) forceRich bayrağını set edip tc.paste()
 * çağırır: virtual dispatch zengin hj.paste() -> PASTERICH kancası -> PasteMode
 * forceRich gördüğünden FORMATLI yol. paste() SENKRON olduğundan finally bayrağı
 * gerçekten kapsar. Editör-dışı düz alanlarda paste() zaten düz; bayrak onlarda
 * etkisiz (kanca yok) — Mac performLocal ile aynı sonuç.
 *
 * WPAppManager.main başına PasteRichPatch tarafından install() enjekte edilir.
 */
public final class PasteKeys {

    private static final int CMD = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx(); // Windows: CTRL
    private static final int WANT = CMD | InputEvent.SHIFT_DOWN_MASK;
    /** İlgilendiğimiz değiştiriciler: fazladan Alt/Meta/AltGr basılıysa (TR klavye AltGr!) karışma. */
    private static final int REL = CMD | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK
            | InputEvent.META_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;
    private static volatile boolean installed;

    private PasteKeys() { }

    /** WPAppManager.main başına enjekte edilir (idempotent). */
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

    /** Ctrl+Shift+V + odak JTextComponent: PRESSED'de formatlı yapıştır; PRESSED+RELEASED yutulur. */
    public static boolean dispatch(KeyEvent e) {   /* public: test erişimi (PasteKeysTest) */
        try {
            if (e.getKeyCode() != KeyEvent.VK_V) return false;
            if ((e.getModifiersEx() & REL) != WANT) return false;
            Component c = e.getComponent();
            if (!(c instanceof JTextComponent)) return false;
            int id = e.getID();
            if (id != KeyEvent.KEY_PRESSED && id != KeyEvent.KEY_RELEASED) return false;
            if (id == KeyEvent.KEY_PRESSED) {
                JTextComponent tc = (JTextComponent) c;
                if (tc.isEditable() && tc.isEnabled()) {
                    PasteMode.setForceRich(true);
                    try { tc.paste(); } finally { PasteMode.setForceRich(false); }
                }
            }
            return true;   // basma/bırakmayı yut (kısayol bizim)
        } catch (Throwable t) {
            return false;
        }
    }
}
