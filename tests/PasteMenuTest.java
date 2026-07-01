import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;

/**
 * PlainPaste.addMenuItem testi (headless): "Formatsız Yapıştır" (hızlandırıcısız)
 * + "Formatlı Yapıştır" (Ctrl+Shift+V) doğru sırada ve idempotent eklenir.
 * Derle + çalıştır:
 *   OUT=$(mktemp -d)
 *   javac --release 11 -encoding UTF-8 -d "$OUT" scripts/pasterich/macospasterich/*.java tests/PasteMenuTest.java
 *   java -cp "$OUT" PasteMenuTest
 */
public class PasteMenuTest {
    public static void main(String[] a) throws Exception {
        JPopupMenu popup = new JPopupMenu();
        popup.add(new JMenuItem("Kes"));
        popup.add(new JMenuItem("Kopyala"));
        JMenuItem yapistir = new JMenuItem("Yapıştır");
        yapistir.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK));
        popup.add(yapistir);

        JTextPane editor = new JTextPane();
        macospasterich.PlainPaste.addMenuItem(popup, editor);
        macospasterich.PlainPaste.addMenuItem(popup, editor);   // idempotans: ikinci çağrı çift eklememeli

        int pasteIdx = indexOf(popup, "Yapıştır");
        int plainIdx = indexOf(popup, "Formatsız Yapıştır");
        int richIdx  = indexOf(popup, "Formatlı Yapıştır");
        if (plainIdx < 0) throw new AssertionError("Formatsız Yapıştır eklenmedi");
        if (richIdx < 0) throw new AssertionError("Formatlı Yapıştır eklenmedi");
        if (count(popup, "Formatsız Yapıştır") != 1 || count(popup, "Formatlı Yapıştır") != 1)
            throw new AssertionError("idempotans bozuk: çift öğe var");
        if (!(pasteIdx < plainIdx && plainIdx < richIdx))
            throw new AssertionError("sıra bozuk: Yapıştır(" + pasteIdx + ") -> Formatsız("
                    + plainIdx + ") -> Formatlı(" + richIdx + ") bekleniyordu");

        JMenuItem plain = (JMenuItem) popup.getComponent(plainIdx);
        if (plain.getAccelerator() != null)
            throw new AssertionError("Formatsız Yapıştır hızlandırıcı göstermemeli: " + plain.getAccelerator());

        int meta = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenuItem rich = (JMenuItem) popup.getComponent(richIdx);
        KeyStroke want = KeyStroke.getKeyStroke(KeyEvent.VK_V, meta | InputEvent.SHIFT_DOWN_MASK);
        if (!want.equals(rich.getAccelerator()))
            throw new AssertionError("Formatlı Yapıştır hızlandırıcısı Ctrl(⌘)+Shift+V değil: " + rich.getAccelerator());

        // fixAccelerators: "Yapıştır"ın hızlandırıcısı platformun menü kısayol
        // maskesini taşımalı (Windows'ta Ctrl kalır = no-op; macOS'ta ⌘'ye döner).
        KeyStroke fixed = ((JMenuItem) popup.getComponent(pasteIdx)).getAccelerator();
        if (fixed == null || (fixed.getModifiers() & meta) == 0)
            throw new AssertionError("Yapıştır hızlandırıcısı menü kısayol maskesini taşımıyor: " + fixed);
        if (meta != InputEvent.CTRL_DOWN_MASK && (fixed.getModifiers() & InputEvent.CTRL_DOWN_MASK) != 0)
            throw new AssertionError("Yapıştır hızlandırıcısında Ctrl kalmış: " + fixed);

        System.out.println("PasteMenuTest OK");
    }

    private static int indexOf(JPopupMenu popup, String text) {
        Component[] cs = popup.getComponents();
        for (int i = 0; i < cs.length; i++) {
            if (cs[i] instanceof JMenuItem
                    && text.equals(((JMenuItem) cs[i]).getText().trim())) return i;
        }
        return -1;
    }

    private static int count(JPopupMenu popup, String text) {
        int n = 0;
        for (Component c : popup.getComponents()) {
            if (c instanceof JMenuItem && text.equals(((JMenuItem) c).getText().trim())) n++;
        }
        return n;
    }
}
