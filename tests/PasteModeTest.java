import javax.swing.JTextPane;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * PasteMode birim testi (headless). Varsayılan mod FORMATSIZ: kaynak karakter
 * stili (kalın/Arial) düşer, imleç stili alınır. forceRich modunda kaynak
 * stili korunur; bayrak finally ile temizlenince yeniden formatsız.
 * Derle + çalıştır:
 *   OUT=$(mktemp -d)
 *   javac --release 11 -encoding UTF-8 -d "$OUT" scripts/pasterich/macospasterich/*.java tests/PasteModeTest.java
 *   java -cp "$OUT" PasteModeTest
 */
public class PasteModeTest {
    public static void main(String[] a) throws Exception {
        String html = "<p><b><span style='font-family:Arial;font-size:20pt;"
                + "color:#FF0000'>Kalin kirmizi</span></b></p>";

        // 1) Varsayılan: formatsız -> kalın düşer, kaynak fontu (Arial) alınmaz.
        JTextPane p1 = new JTextPane();
        p1.setCaretPosition(0);
        if (!macospasterich.PasteMode.insertHtml(p1, html))
            throw new AssertionError("insertHtml (varsayılan) false döndü");
        if (hasBold(p1))
            throw new AssertionError("varsayılan mod formatsız değil: kalın korunmuş");
        if ("Arial".equals(familyOfFirstRun(p1)))
            throw new AssertionError("varsayılan mod kaynak fontunu aldı (imleç stili beklenirdi)");

        // 2) forceRich: formatlı -> kalın + Arial korunur.
        JTextPane p2 = new JTextPane();
        p2.setCaretPosition(0);
        macospasterich.PasteMode.setForceRich(true);
        try {
            if (!macospasterich.PasteMode.insertHtml(p2, html))
                throw new AssertionError("insertHtml (forceRich) false döndü");
        } finally {
            macospasterich.PasteMode.setForceRich(false);
        }
        if (!hasBold(p2))
            throw new AssertionError("forceRich modu formatlı değil: kalın düşmüş");
        if (!"Arial".equals(familyOfFirstRun(p2)))
            throw new AssertionError("forceRich modu kaynak fontunu korumadı: " + familyOfFirstRun(p2));

        // 3) Bayrak temizlendi: yeniden varsayılan (formatsız).
        JTextPane p3 = new JTextPane();
        p3.setCaretPosition(0);
        if (!macospasterich.PasteMode.insertHtml(p3, html))
            throw new AssertionError("insertHtml (bayrak sonrası) false döndü");
        if (hasBold(p3))
            throw new AssertionError("bayrak temizlenmedi: hâlâ formatlı");

        System.out.println("PasteModeTest OK");
    }

    private static boolean hasBold(JTextPane pane) throws Exception {
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

    private static String familyOfFirstRun(JTextPane pane) throws Exception {
        StyledDocument doc = (StyledDocument) pane.getDocument();
        Element root = doc.getDefaultRootElement();
        for (int p = 0; p < root.getElementCount(); p++) {
            Element para = root.getElement(p);
            for (int r = 0; r < para.getElementCount(); r++) {
                Element run = para.getElement(r);
                int s = run.getStartOffset(), e = Math.min(run.getEndOffset(), doc.getLength());
                if (e <= s) continue;
                if (doc.getText(s, e - s).trim().isEmpty()) continue;
                return StyleConstants.getFontFamily(run.getAttributes());
            }
        }
        return null;
    }
}
