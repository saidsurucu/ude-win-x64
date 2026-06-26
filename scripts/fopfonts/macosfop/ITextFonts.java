package macosfop;

import com.lowagie.text.pdf.BaseFont;

import java.awt.Font;
import java.io.File;

/**
 * UDE'nin iText (com.lowagie) tabanli PDF disa aktarim yolunu Turkce icin duzeltir.
 *
 * Bu yol GUI "PDF'e cevir" menusu DEGIL; entegrasyon/headless donusturmedir
 * (tr.com.havelsan...editor.utils.tray.TrayUtils.udfToPdf -> editor.b.b -> iText).
 * b.b, sayfalari PdfTemplate.createGraphics(w, h, FontMapper) ile cizer; FontMapper
 * olarak editor.b.c kullanilir ve onun awtToPdf(...) metodu base-14 (Times/Helvetica/
 * Courier - GOMULU DEGIL) BaseFont dondurur. Sonuc: Turkce harfler (ozellikle buyuk I)
 * /Differences ile glif adiyla yazilir; kati goruntuleyiciler (Acrobat) gostermez.
 *
 * Cozum: awtToPdf bu sinifin map(...)'ine yonlendirilir; map, AWT fontuna gore
 * (serif/sans + bold/italic) GOMULU, Identity-H kodlamali, tam Unicode bir BaseFont
 * dondurur -> tum Turkce harfler PDF'e gomulur.
 *
 * Windows: tek tip LIBERATION (Times/Arial metrik-uyumlu, tam Turkce) gomulur; sistem
 * fontuna bagimli degil. Tumu try/catch ile sarili: hata olursa iText'in eski (base-14)
 * davranisina dusulur, donusturme kirilmaz.
 */
public final class ITextFonts {

    private ITextFonts() {}

    /** editor.b.c.awtToPdf(...) yerine: AWT font -> gomulu Identity-H BaseFont. */
    public static BaseFont map(Font f) {
        try {
            boolean bold   = f != null && f.isBold();
            boolean italic = f != null && f.isItalic();
            boolean sans   = isSans(f);
            File ttf = pick(sans, bold, italic);
            if (ttf != null) {
                return BaseFont.createFont(ttf.getAbsolutePath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }
        } catch (Throwable ignore) {
            // dus -> fallback
        }
        return fallback();
    }

    /** Font bulunamazsa iText'in eski (base-14) davranisi. */
    private static BaseFont fallback() {
        try {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (Throwable t) {
            return null;
        }
    }

    /** AWT font sans mi? (acik sans adlari -> sans; aksi halde serif = UYAP standardi Times). */
    private static boolean isSans(Font f) {
        if (f == null) return false;
        String n = (f.getFamily() + " " + f.getName() + " " + f.getFontName()).toLowerCase();
        return n.contains("arial") || n.contains("helvetica") || n.contains("sans")
            || n.contains("tahoma") || n.contains("verdana") || n.contains("calibri")
            || n.contains("dialog") || n.contains("segoe");
    }

    /** Pakete gomulu Liberation Serif/Sans'tan uygun stili secer. */
    private static File pick(boolean sans, boolean bold, boolean italic) {
        File dir = bundleFopFontsDir();
        if (dir == null) return null;
        String libName = (sans ? "LiberationSans-" : "LiberationSerif-")
            + libStyle(bold, italic) + ".ttf";
        File lib = new File(dir, libName);
        return lib.isFile() ? lib : null;
    }

    private static String libStyle(boolean bold, boolean italic) {
        if (bold && italic) return "BoldItalic";
        if (bold)           return "Bold";
        if (italic)         return "Italic";
        return "Regular";
    }

    /** Calisan editor-app.jar'in yanindaki app/fopfonts dizini. */
    private static File bundleFopFontsDir() {
        try {
            File self = new File(ITextFonts.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File base = self.getParentFile();   // .../app
            if (base == null) return null;
            return new File(base, "fopfonts");
        } catch (Throwable t) {
            return null;
        }
    }
}
