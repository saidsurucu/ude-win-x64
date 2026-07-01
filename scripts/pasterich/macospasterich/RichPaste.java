package macospasterich;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import macospasterich.UdeDoc.Document;

/**
 * Harici stilli yapıştırma köprüsü (saf Java — harici bağımlılık / alt süreç YOK).
 * Panodaki HTML'i (Word/tarayıcı/PDF) UDE'nin kendi .udf (UDF zip: content.xml)
 * formatına çevirir. Boru hattı: Html tokenizer → HtmlToUde (model) → UdeXml
 * (content.xml) → zip. Dönen baytlar build-zamanı kancası (PasteRichPatch)
 * tarafından WPDocumentPanel.a(InputStream)'e beslenir → select-all/copy/paste.
 *
 * Tasarımı gereği SESSIZ BAŞARISIZLIK: hata/boş içerik → null; çağıran mevcut
 * düz-metin yoluna düşer (çökme yok).
 */
public final class RichPaste {

    /**
     * Pano HTML'ini canlı editörün belgesine caret'e YEREL ekler (copy→paste
     * YOK — o yol tabloları düzleştiriyordu). HtmlToUde modeli kurar, NativeInsert
     * tabloları DocumentEx.a ile gerçek tablo olarak, paragrafları StyleConstants
     * ile ekler. Başarıda true; başarısızsa false → çağıran düz-metne düşer.
     */
    public static boolean insertInto(Object editor, String html) {
        return insertInto(editor, html, null);
    }

    /**
     * cursorAttrs != null ise DÜZ-KARAKTER modu (Formatsız Yapıştır): yapı
     * korunur, karakter stili cursorAttrs'a indirgenir.
     */
    public static boolean insertInto(Object editor, String html, javax.swing.text.AttributeSet cursorAttrs) {
        try {
            if (html == null || html.isEmpty()) return false;
            PrLog.dumpHtml(html);
            html = stripCfHtml(html);
            UdeDoc.Document model = HtmlToUde.convert(html);
            if (model.body.isEmpty()) { PrLog.log("boş model"); return false; }
            boolean ok = NativeInsert.insert(editor, model, cursorAttrs);
            PrLog.log(ok ? ("insertInto ok " + model.body.size() + " blok"
                    + (cursorAttrs != null ? " (düz)" : "")) : "insertInto başarısız");
            return ok;
        } catch (Throwable t) {
            PrLog.log("insertInto", t);
            return false;
        }
    }

    /**
     * Windows: pano RTF yolu (Mac pbrich/textutil) PORT EDİLMEZ. Windows zengin
     * kaynakları (Word/Chrome/Edge/Outlook/Excel/LibreOffice) panoya CF_HTML koyar →
     * allHtmlFlavor yolu (insertInto) onları kapsar. RTF-only kaynaklar (WordPad/eski
     * Win32) nadirdir → düz-metne düşer. Debug'da mevcut flavor'ları loglar.
     */
    public static boolean insertRtf(Object editor, java.awt.datatransfer.Transferable t) {
        return insertRtf(editor, t, null);
    }

    /** cursorAttrs != null → düz-karakter modu (Formatsız Yapıştır). */
    public static boolean insertRtf(Object editor, java.awt.datatransfer.Transferable t,
                                    javax.swing.text.AttributeSet cursorAttrs) {
        try {
            if (t != null) {
                StringBuilder sb = new StringBuilder("rtf yolu yok (Windows); flavor'lar:");
                for (java.awt.datatransfer.DataFlavor f : t.getTransferDataFlavors()) {
                    sb.append(' ').append(f.getMimeType());
                }
                PrLog.log(sb.toString());
            }
        } catch (Throwable e) {
            PrLog.log("insertRtf flavor", e);
        }
        return false;
    }

    /**
     * Windows CF_HTML tanım başlığını soyar. JDK'da pano okuma ClipboardTransferable
     * → DataTransferer.translateBytes üzerinden gider; String temsilli metin flavor'ları
     * (allHtmlFlavor dahil) HTMLCodec'e UĞRAMADAN translateBytesToString'e düşer →
     * "Version:0.9\r\nStartHTML:...\r\n...SourceURL:...\r\n" başlığı HTML'in başında
     * String'e dahil gelir (codec yalnız Reader/InputStream temsillerindeki
     * translateStream'e bağlı). Mac NSPasteboard'da bu başlık yok → Mac portunda
     * soyma yoktu; Windows uyarlaması. Başlık offset'leri BAYT cinsinden ve eoln
     * dönüşümü sonrası kaymış olabileceğinden offset yerine satır-anahtar taraması
     * yapılır: ilk satır "Version:" ise bilinen anahtarlı satırlar atlanır.
     */
    static String stripCfHtml(String s) {
        if (s == null || !s.startsWith("Version:")) return s;
        String[] keys = { "Version:", "StartHTML:", "EndHTML:", "StartFragment:",
                "EndFragment:", "StartSelection:", "EndSelection:", "SourceURL:" };
        int i = 0, n = s.length();
        while (i < n) {
            boolean match = false;
            for (String k : keys) {
                if (s.startsWith(k, i)) { match = true; break; }
            }
            if (!match) break;
            int eol = s.indexOf('\n', i);
            if (eol < 0) return "";   /* tamamı başlık */
            i = eol + 1;
        }
        return s.substring(i);
    }

    /** Pano HTML'ini .udf (UDF zip) baytına çevirir; başarısızlıkta null. (Testler için.) */
    public static byte[] fromClipboardHtml(String html) {
        if (html == null || html.isEmpty()) return null;
        html = stripCfHtml(html);
        try {
            Document doc = HtmlToUde.convert(html);
            if (doc.body.isEmpty()) { PrLog.log("boş belge (içerik yok)"); return null; }
            String contentXml = UdeXml.serialize(doc);
            byte[] udf = zip(contentXml);
            PrLog.log("ok " + udf.length + " bayt, " + doc.body.size() + " blok");
            return udf;
        } catch (Throwable t) {
            PrLog.log("fromClipboardHtml", t);
            return null;
        }
    }

    /** content.xml dizesini tek girişli UDF zip'ine (.udf) paketler. */
    private static byte[] zip(String contentXml) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry e = new ZipEntry("content.xml");
            zos.putNextEntry(e);
            zos.write(contentXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    /** PasteRichPatch kancası catch bloğundan çağrılır. */
    public static void logExternal(Throwable t) {
        PrLog.log("inject", t);
    }

    /**
     * WPDocumentPanel.a():fi metodunu reflection ile çağırır. WPDocumentPanel'de
     * 'a()' adıyla İKİ no-arg metot vardır (obfuscate dönüş-tipi overload'ı):
     * biri int, biri fi döndürür. Javassist/javac kaynak düzeyinde dönüş tipiyle
     * ayırt edemez ("bad method") → bu yardımcı fi-döndüren olanı seçer. Dönen
     * Object, kancada tr...text.hj'ye cast edilir (fi extends hj).
     */
    public static Object docOf(Object panel) throws Exception {
        for (java.lang.reflect.Method m : panel.getClass().getMethods()) {
            if (m.getName().equals("a") && m.getParameterCount() == 0
                    && m.getReturnType().getName().endsWith(".text.fi")) {
                return m.invoke(panel);
            }
        }
        throw new NoSuchMethodException("WPDocumentPanel.a():fi bulunamadı");
    }

    private RichPaste() {
    }
}
