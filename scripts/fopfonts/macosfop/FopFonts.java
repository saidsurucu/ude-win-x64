package macosfop;

import org.apache.fop.apps.FopFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * Windows'ta UDF->PDF disa aktarimda Turkce harf (g G s S i I) kaybini giderir.
 *
 * Sorun (platform-NOTR): UDE'nin FOP surucusu (tr.com.havelsan...editor.b.a) FopFactory'yi
 * font yapilandirmasi OLMADAN kurar. FOP base-14 standart PDF fontlarina (WinAnsi=Cp1252)
 * duser; Cp1252'de c o u vardir ama Turkce'ye ozgu g G s S i I YOKTUR -> bu harfler PDF'te
 * duser. (Mac'te de ayni mekanizma; macOS'a ozgu degildir.)
 *
 * Cozum: FopFactory'ye, tam Unicode kapsamli fontlari GOMEN bir kullanici yapilandirmasi
 * set edilir. Cikti PDF'te font Type0/Identity-H + gomulu FontFile2 olur, tum Turkce harfler
 * dogru cizilir ve PDF kendi kendine yeter (goruntuleyenin fontu olmasi gerekmez).
 *
 * Windows: tek tip LIBERATION (OFL; Times/Arial ile metrik-uyumlu, tam Turkce) gomulur.
 * Sistem Arial/Times'a bagimli degildir -> her makinede ayni dogru cikti. Metrik XML'leri
 * build sirasinda Liberation TTF'lerinden org.apache.fop.fonts.apps.TTFReader ile uretilir.
 *
 * Tumu try/catch ile saridir: font/metrik yoksa hicbir sey yapilmaz, mevcut (base-14)
 * davranis korunur -> disa aktarim asla bu yama yuzunden kirilmaz.
 */
public final class FopFonts {

    // variant: libTtf | libMetric | style | weight
    private static final String[][] SERIF = {
        {"LiberationSerif-Regular.ttf",    "libserif.xml",            "normal", "normal"},
        {"LiberationSerif-Bold.ttf",       "libserif-bold.xml",       "normal", "bold"},
        {"LiberationSerif-Italic.ttf",     "libserif-italic.xml",     "italic", "normal"},
        {"LiberationSerif-BoldItalic.ttf", "libserif-bolditalic.xml", "italic", "bold"},
    };
    private static final String[][] SANS = {
        {"LiberationSans-Regular.ttf",    "libsans.xml",            "normal", "normal"},
        {"LiberationSans-Bold.ttf",       "libsans-bold.xml",       "normal", "bold"},
        {"LiberationSans-Italic.ttf",     "libsans-italic.xml",     "italic", "normal"},
        {"LiberationSans-BoldItalic.ttf", "libsans-bolditalic.xml", "italic", "bold"},
    };
    // Acik sans adlari -> sans; geri kalan (UDE'nin font yazmadigi varsayilan dahil) -> serif
    // (UYAP standardi Times). Mac FopFonts ile ayni mantik.
    private static final String[] SANS_FAMILIES = {
        "SansSerif", "Helvetica", "Arial",
    };
    private static final String[] SERIF_FAMILIES = {
        "serif", "Serif", "sans-serif", "any", "Default", "Times", "TimesNewRoman",
        "monospace", "Courier",
    };

    private static boolean done;
    private static File conf;

    private FopFonts() {}

    /** FOP surucusunden, FopFactory uretildikten hemen sonra cagrilir. */
    public static void apply(FopFactory ff) {
        if (ff == null) return;
        try {
            File c = config();
            if (c != null) ff.setUserConfig(c);
        } catch (Throwable ignore) {
            // Sessizce gec: mevcut davranisi (base-14) bozma.
        }
    }

    private static synchronized File config() throws Exception {
        if (done) return conf;
        done = true;

        File bundle = bundleFopFontsDir();   // app/fopfonts (metrikler + Liberation TTF)
        if (bundle == null || !bundle.isDirectory()) return null;

        // Calisma dizini: kurulum yolu bosluk/Turkce icerebileceginden metrik+TTF'yi buraya
        // kopyalayip file URL'leri buradan uret (yol guvenligi).
        File work = new File(System.getProperty("java.io.tmpdir", "."), "ude-fopfonts");
        work.mkdirs();

        StringBuilder fonts = new StringBuilder();
        for (String fam : SANS_FAMILIES)
            for (String[] v : SANS) addFont(fonts, bundle, work, v, fam);
        for (String fam : SERIF_FAMILIES)
            for (String[] v : SERIF) addFont(fonts, bundle, work, v, fam);

        if (fonts.length() == 0) return null;

        String xml = "<?xml version=\"1.0\"?>\n"
            + "<fop version=\"1.0\"><base>.</base><renderers>"
            + "<renderer mime=\"application/pdf\"><fonts>" + fonts
            + "</fonts></renderer></renderers></fop>";

        File out = new File(work, "fopconf.xml");
        Writer w = new OutputStreamWriter(new FileOutputStream(out), "UTF-8");
        try { w.write(xml); } finally { w.close(); }
        conf = out;
        return conf;
    }

    /** Bir variant icin Liberation font-triplet ekler (metrik gomulen TTF ile eslesir). */
    private static void addFont(StringBuilder sb, File bundle, File work,
                                String[] v, String family) throws Exception {
        File libTtf    = new File(bundle, v[0]);
        File libMetric = new File(bundle, v[1]);
        if (!libTtf.isFile() || !libMetric.isFile()) return;

        File m = new File(work, libMetric.getName());
        if (!m.isFile()) copy(libMetric, m);
        File t = new File(work, libTtf.getName());
        if (!t.isFile()) copy(libTtf, t);

        sb.append("<font metrics-url=\"").append(fileUrl(m))
          .append("\" kerning=\"yes\" embed-url=\"").append(fileUrl(t))
          .append("\"><font-triplet name=\"").append(xmlEsc(family))
          .append("\" style=\"").append(v[2])
          .append("\" weight=\"").append(v[3]).append("\"/></font>");
    }

    /** Calisan editor-app.jar'in yanindaki app/fopfonts dizinini bulur. */
    private static File bundleFopFontsDir() {
        try {
            File self = new File(FopFonts.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File base = self.getParentFile();   // .../app
            if (base == null) return null;
            return new File(base, "fopfonts");
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Windows file URL: ileri-bolu, %-kodlama YOK (FOP 0.92 %20'yi cozmuyor; ham bosluk +
     * ileri-bolu), XML-attribute escape'li. Orn:
     *   file:/C:/Users/ad/AppData/Local/Temp/ude-fopfonts/libserif.xml
     */
    private static String fileUrl(File f) {
        return xmlEsc("file:/" + f.getAbsolutePath().replace('\\', '/'));
    }

    private static String xmlEsc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void copy(File src, File dst) throws Exception {
        InputStream in = new FileInputStream(src);
        try {
            OutputStream o = new FileOutputStream(dst);
            try {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) o.write(b, 0, n);
            } finally { o.close(); }
        } finally { in.close(); }
    }
}
