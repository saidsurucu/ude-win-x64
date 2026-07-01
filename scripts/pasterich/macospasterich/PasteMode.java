package macospasterich;

import java.awt.datatransfer.Transferable;

import javax.swing.text.AttributeSet;
import javax.swing.text.JTextComponent;

/**
 * Harici yapıştırma modu anahtarı. PasteRichPatch'in hj.a(Transferable) kancası
 * RichPaste'i DOĞRUDAN değil bu sınıf üzerinden çağırır: varsayılan FORMATSIZ
 * (karakter+paragraf biçimi imleçten, tablo/liste/imaj YAPISI kaynaktan —
 * PLAINPASTE anlamı); forceRich bayrağı set edilmişse eski FORMATLI (zengin)
 * yol. Bayrağı Ctrl+Shift+V (PasteKeys dispatcher) ve sağ tık "Formatlı
 * Yapıştır" (PlainPaste.addMenuItem) try/finally ile set/temizler.
 *
 * UDE-İÇİ kopyalar (EditorDataFlavor) paste()'in başında, kancaya hiç
 * uğramadan işlenir -> her zaman formatlı kalır (bu sınıf onlara dokunmaz).
 */
public final class PasteMode {

    private static volatile boolean forceRich;

    /** Çağıranlar try/finally ile set/temizlemeli (yarım bayrak kalmasın). */
    public static void setForceRich(boolean b) { forceRich = b; }

    /** Kancanın HTML dalı: varsayılan formatsız, forceRich'te formatlı. */
    public static boolean insertHtml(Object editor, String html) {
        return RichPaste.insertInto(editor, html, cursorAttrsOrNull(editor));
    }

    /** Kancanın RTF dalı: aynı mod seçimi. */
    public static boolean insertRtf(Object editor, Transferable t) {
        return RichPaste.insertRtf(editor, t, cursorAttrsOrNull(editor));
    }

    /** null = formatlı (zengin yol); değilse formatsızın imleç stili. */
    private static AttributeSet cursorAttrsOrNull(Object editor) {
        if (forceRich) return null;
        if (editor instanceof JTextComponent)
            return PlainPaste.cursorAttrs((JTextComponent) editor);
        return null;   /* editör tipi bilinmiyorsa güvenli taraf: eski (formatlı) davranış */
    }

    private PasteMode() { }
}
