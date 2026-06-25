package macospasterich;

import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.JEditorPane;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;

/**
 * Formatsız Yapıştır (Word "Yalnızca Metni Koru"). Pano içeriğini KARAKTER STİLİ
 * olmadan (imlecin stilini alarak) ama tablo/imaj/liste/hizalama korunarak
 * editöre ekler. PASTERICH boru hattını (HtmlToUde → NativeInsert) düz-karakter
 * modunda yeniden kullanır.
 *
 * İki çağıran: ⌘⇧V (MacShortcutRemap, reflection) ve sağ tık menüsü
 * (EditContextMenuWidget Javassist yaması → addMenuItem). Teşhis:
 * UDE_PLAINPASTELOG=1 → ~/Library/Logs/ude-plainpaste.txt.
 */
public final class PlainPaste {

    /** Pano içeriğini editöre formatsız ekler. Başarıda true. */
    public static boolean paste(JTextComponent editor) {
        try {
            if (editor == null || !editor.isEditable() || !editor.isEnabled()) return false;
            AttributeSet cursor = cursorAttrs(editor);
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable t = cb.getContents(null);
            if (t == null) { log("pano boş"); return false; }

            // 1) HTML (Word/tarayıcı/PDF) — tablo/imaj/liste buradan gelir.
            if (t.isDataFlavorSupported(DataFlavor.allHtmlFlavor)) {
                Object o = t.getTransferData(DataFlavor.allHtmlFlavor);
                if (o instanceof String && RichPaste.insertInto(editor, (String) o, cursor)) {
                    log("html düz ok"); return true;
                }
            }
            // 2) RTF (Pages/TextEdit/Mail) — insertRtf flavor'ı içeride çözer,
            //    yoksa false döner.
            if (RichPaste.insertRtf(editor, t, cursor)) { log("rtf düz ok"); return true; }

            // 3) Düz metin yedeği.
            if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String s = (String) t.getTransferData(DataFlavor.stringFlavor);
                insertPlainString(editor, s, cursor);
                log("düz metin ok"); return true;
            }
            log("yapıştırılacak içerik yok");
            return false;
        } catch (Throwable e) {
            log("paste", e);
            return false;
        }
    }

    /**
     * İmlecin karakter stilini çözer (Formatsız metin bunu alır). Katmanlama:
     * taban (TNR 12) → caret'teki karakter elementi (hedef paragraf stili) →
     * kit giriş öznitelikleri (yazım stili). Böylece FontFamily HER ZAMAN tanımlı
     * (Swing'in "Monospaced"a düşmesi engellenir).
     */
    private static AttributeSet cursorAttrs(JTextComponent editor) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setFontFamily(a, "Times New Roman");
        StyleConstants.setFontSize(a, 12);
        try {
            Document doc = editor.getDocument();
            if (doc instanceof StyledDocument) {
                Element ce = ((StyledDocument) doc).getCharacterElement(editor.getCaretPosition());
                if (ce != null) a.addAttributes(ce.getAttributes());
            }
            if (editor instanceof JEditorPane) {
                Object kit = ((JEditorPane) editor).getEditorKit();
                if (kit instanceof StyledEditorKit) {
                    a.addAttributes(((StyledEditorKit) kit).getInputAttributes());
                }
            }
        } catch (Throwable ignore) { }
        return a;
    }

    /** Düz metni seçim-değiştirmeli olarak imleç stilinde ekler (cast YOK). */
    private static void insertPlainString(JTextComponent editor, String s, AttributeSet attrs) throws Exception {
        if (s == null) return;
        Document doc = editor.getDocument();
        int start = Math.min(editor.getSelectionStart(), editor.getSelectionEnd());
        int end = Math.max(editor.getSelectionStart(), editor.getSelectionEnd());
        if (end > start) doc.remove(start, end - start);
        doc.insertString(start, s, attrs);   // Document arayüzü → PlainDocument'te attrs yok sayılır
        try { editor.setCaretPosition(Math.min(start + s.length(), doc.getLength())); } catch (Throwable ignore) { }
    }

    /**
     * UDE'nin kendi sağ tık menüsüne (text.fK → gui.dx.getPopupMenu()) "Formatsız
     * Yapıştır" öğesini ekler. PlainPastePatch tarafından JPopupMenu.show çağrısından
     * ÖNCE çağrılır ($0=popup, $1=editör). Öğe "Yapıştır"dan hemen sonra eklenir;
     * idempotans (popup yeniden gösterilse de tek kopya). Ayrıca UDE'nin Windows
     * kökenli ^X/^C/^V hızlandırıcıları macOS ⌘ ile gösterilir.
     */
    public static void addMenuItem(JPopupMenu popup, Component invoker) {
        try {
            if (popup == null || !(invoker instanceof JTextComponent)) return;
            // İdempotans: zaten varsa yalnız hızlandırıcıları düzelt.
            for (Component c : popup.getComponents()) {
                if (c instanceof JMenuItem && "Formatsız Yapıştır".equals(((JMenuItem) c).getText())) {
                    fixAccelerators(popup);
                    return;
                }
            }
            final JTextComponent tc = (JTextComponent) invoker;
            JMenuItem mi = new JMenuItem("Formatsız Yapıştır");
            mi.setEnabled(tc.isEditable() && tc.isEnabled());
            int meta = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();   // macOS = ⌘
            mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, meta | InputEvent.SHIFT_DOWN_MASK));
            mi.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) { paste(tc); }
            });
            // "Yapıştır"ın hemen ardına ekle (yoksa sona).
            int idx = -1;
            Component[] cs = popup.getComponents();
            for (int i = 0; i < cs.length; i++) {
                if (cs[i] instanceof JMenuItem
                        && "Yapıştır".equals(((JMenuItem) cs[i]).getText().trim())) { idx = i + 1; break; }
            }
            if (idx >= 0 && idx <= popup.getComponentCount()) popup.insert(mi, idx);
            else popup.add(mi);
            fixAccelerators(popup);
        } catch (Throwable e) {
            log("addMenuItem", e);
        }
    }

    /** UDE'nin ^X/^C/^V (Ctrl) hızlandırıcılarını macOS ⌘ ile göster. İdempotent. */
    private static void fixAccelerators(JPopupMenu popup) {
        try {
            int meta = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            int ctrl = InputEvent.CTRL_DOWN_MASK | InputEvent.CTRL_MASK;
            for (Component c : popup.getComponents()) {
                if (!(c instanceof JMenuItem)) continue;
                JMenuItem mi = (JMenuItem) c;
                KeyStroke ks = mi.getAccelerator();
                if (ks == null || (ks.getModifiers() & ctrl) == 0) continue;
                int nm = (ks.getModifiers() & ~ctrl) | meta;
                mi.setAccelerator(KeyStroke.getKeyStroke(ks.getKeyCode(), nm));
            }
        } catch (Throwable ignore) { }
    }

    // ---- log (UDE_PLAINPASTELOG=1) ----
    private static void log(String msg) {
        if (!"1".equals(System.getenv("UDE_PLAINPASTELOG"))) return;
        write(msg);
    }
    private static void log(String msg, Throwable t) {
        if (!"1".equals(System.getenv("UDE_PLAINPASTELOG"))) return;
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        write(msg + "\n" + sw);
    }
    private static void write(String s) {
        try {
            java.io.File f = new java.io.File(System.getProperty("user.home"),
                    "Library/Logs/ude-plainpaste.txt");
            try (java.io.FileWriter w = new java.io.FileWriter(f, true)) {
                w.write(s + "\n");
            }
        } catch (Throwable ignore) { }
    }

    private PlainPaste() { }
}
