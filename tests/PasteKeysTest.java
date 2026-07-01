import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JTextPane;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * PasteKeys.dispatch testi (headless). Ctrl+Shift+V basımı: forceRich bayrağı
 * paste() SIRASINDA set, sonrasında temiz; PRESSED+RELEASED yutulur; Ctrl+V ve
 * JTextComponent olmayan bileşenlere dokunulmaz.
 * Derle + çalıştır:
 *   OUT=$(mktemp -d)
 *   javac --release 11 -encoding UTF-8 -d "$OUT" scripts/pasterich/macospasterich/*.java tests/PasteKeysTest.java
 *   java -cp "$OUT" PasteKeysTest
 */
public class PasteKeysTest {
    static final String HTML = "<p><b><span style='font-family:Arial'>Kalin</span></b></p>";

    /** paste() çağrısını kaydeder; o anda forceRich etkin miydi diye insertHtml ile yoklar. */
    static class RecordingPane extends JTextPane {
        boolean pasted, richDuringPaste;
        @Override public void paste() {
            pasted = true;
            try {
                JTextPane probe = new JTextPane();
                probe.setCaretPosition(0);
                if (!macospasterich.PasteMode.insertHtml(probe, HTML))
                    throw new AssertionError("probe insertHtml false döndü");
                richDuringPaste = hasBold(probe);   // bold korunuyorsa forceRich etkin demektir
            } catch (RuntimeException | Error e) { throw e; }
              catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    public static void main(String[] a) throws Exception {
        int mods = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK;

        // 1) Ctrl+Shift+V PRESSED: yutulur, paste çağrılır, paste sırasında forceRich etkin.
        RecordingPane tc = new RecordingPane();
        KeyEvent pressed = new KeyEvent(tc, KeyEvent.KEY_PRESSED, 0L, mods, KeyEvent.VK_V, 'V');
        if (!macospasterich.PasteKeys.dispatch(pressed))
            throw new AssertionError("Ctrl+Shift+V PRESSED yutulmadı");
        if (!tc.pasted) throw new AssertionError("paste() çağrılmadı");
        if (!tc.richDuringPaste) throw new AssertionError("paste sırasında forceRich etkin değildi");

        // 2) RELEASED da yutulur (ama paste tekrar çağrılmaz).
        tc.pasted = false;
        KeyEvent released = new KeyEvent(tc, KeyEvent.KEY_RELEASED, 0L, mods, KeyEvent.VK_V, 'V');
        if (!macospasterich.PasteKeys.dispatch(released))
            throw new AssertionError("Ctrl+Shift+V RELEASED yutulmadı");
        if (tc.pasted) throw new AssertionError("RELEASED paste çağırdı");

        // 3) Bayrak temiz: dispatch sonrası insertHtml formatsız.
        JTextPane p = new JTextPane();
        p.setCaretPosition(0);
        if (!macospasterich.PasteMode.insertHtml(p, HTML))
            throw new AssertionError("insertHtml (dispatch sonrası) false döndü");
        if (hasBold(p)) throw new AssertionError("bayrak temizlenmedi: hâlâ formatlı");

        // 4) Shift'siz Ctrl+V'ye dokunulmaz.
        int cmd = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        KeyEvent plainV = new KeyEvent(tc, KeyEvent.KEY_PRESSED, 0L, cmd, KeyEvent.VK_V, 'v');
        if (macospasterich.PasteKeys.dispatch(plainV))
            throw new AssertionError("Ctrl+V yutuldu (dokunulmamalıydı)");

        // 5) Alt basılıyken (AltGr kombinasyonları) dokunulmaz.
        KeyEvent altCombo = new KeyEvent(tc, KeyEvent.KEY_PRESSED, 0L,
                mods | InputEvent.ALT_DOWN_MASK, KeyEvent.VK_V, 'V');
        if (macospasterich.PasteKeys.dispatch(altCombo))
            throw new AssertionError("Ctrl+Alt+Shift+V yutuldu (AltGr korunmalıydı)");

        // 6) JTextComponent olmayan bileşene dokunulmaz.
        KeyEvent onButton = new KeyEvent(new JButton(), KeyEvent.KEY_PRESSED, 0L, mods, KeyEvent.VK_V, 'V');
        if (macospasterich.PasteKeys.dispatch(onButton))
            throw new AssertionError("JButton üstünde yutuldu");

        // 7) Salt-okunur editörde paste çağrılmaz (event yine yutulur: kısayol bizim).
        RecordingPane ro = new RecordingPane();
        ro.setEditable(false);
        KeyEvent onReadOnly = new KeyEvent(ro, KeyEvent.KEY_PRESSED, 0L, mods, KeyEvent.VK_V, 'V');
        if (!macospasterich.PasteKeys.dispatch(onReadOnly))
            throw new AssertionError("salt-okunurda PRESSED yutulmadı");
        if (ro.pasted) throw new AssertionError("salt-okunurda paste() çağrıldı");

        System.out.println("PasteKeysTest OK");
    }

    static boolean hasBold(JTextPane pane) throws Exception {
        StyledDocument doc = (StyledDocument) pane.getDocument();
        Element root = doc.getDefaultRootElement();
        for (int p = 0; p < root.getElementCount(); p++) {
            Element para = root.getElement(p);
            for (int r = 0; r < para.getElementCount(); r++) {
                Element run = para.getElement(r);
                int s = run.getStartOffset(), e = Math.min(run.getEndOffset(), doc.getLength());
                if (e <= s) continue;
                if (doc.getText(s, e - s).trim().isEmpty()) continue;
                if (StyleConstants.isBold(run.getAttributes())) return true;
            }
        }
        return false;
    }
}
