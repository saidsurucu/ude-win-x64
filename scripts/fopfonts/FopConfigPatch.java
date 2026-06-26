import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.io.File;
import java.io.FileOutputStream;

/**
 * UDE'nin FOP sürücüsü
 *   tr.com.havelsan.uyap.system.editor.b.a
 * içinde FOP yapılandırması (font kaydı) HİÇ yapılmadan FopFactory kurulur:
 *   FopFactory.newInstance()  →  newFop(mime, out)  →  getDefaultHandler()
 * Bu yüzden FOP base-14 (Cp1252) fontlarına düşer ve Türkçe ğ/ş/ı/İ harfleri
 * PDF'te kaybolur.
 *
 * Bu yama, FopFactory.newInstance() çağrısından HEMEN SONRA üretilen factory'ye
 *   macosfop.FopFonts.apply(<factory>)
 * köprüsünü ekler. apply(...), macOS Arial/Times fontlarını gömen bir kullanıcı
 * yapılandırmasını setUserConfig ile uygular (ayrıntı: macosfop.FopFonts).
 *
 * Sınıfın tüm metotları taranır; newInstance çağrısı nerede olursa olsun yakalanır.
 * macosfop.FopFonts sınıfı bu yamadan ÖNCE jar'a eklenmiş olmalıdır (build.sh
 * sırası bunu garanti eder), aksi halde Javassist köprü ifadesini derleyemez.
 *
 * Ayrıca iText (com.lowagie) tabanlı dışa aktarım yolunun font eşleyicisini de yamalar:
 *   tr.com.havelsan.uyap.system.editor.b.c  (com.lowagie.text.pdf.FontMapper)
 *     public BaseFont awtToPdf(java.awt.Font)
 * gövdesi  return macosfop.ITextFonts.map($1);  ile değiştirilir → AWT fontu gömülü,
 * Identity-H, tam Unicode bir BaseFont'a eşlenir (entegrasyon/TrayUtils.udfToPdf yolu;
 * base-14 yüzünden büyük İ'nin Acrobat'ta görünmemesi giderilir).
 *
 * macosfop.FopFonts ve macosfop.ITextFonts bu yamadan ÖNCE jar'a eklenmiş olmalıdır.
 *
 * Argümanlar: <editor-app.jar> <out-dir>
 */
public class FopConfigPatch {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Kullanım: FopConfigPatch <editor-app.jar> <out-dir>");
            System.exit(2);
        }
        String jar = args[0];
        File outDir = new File(args[1]);

        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(jar);

        // --- 1) FOP yolu (b/a): newInstance → FopFonts.apply ---
        CtClass drv = pool.get("tr.com.havelsan.uyap.system.editor.b.a");
        final boolean[] hit = {false};
        drv.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall mc) throws javassist.CannotCompileException {
                if ("org.apache.fop.apps.FopFactory".equals(mc.getClassName())
                        && "newInstance".equals(mc.getMethodName())) {
                    mc.replace("{ $_ = $proceed($$); macosfop.FopFonts.apply($_); }");
                    hit[0] = true;
                }
            }
        });
        if (!hit[0]) {
            throw new IllegalStateException(
                "FopFactory.newInstance() çağrısı bulunamadı — UDE sürümü değişmiş olabilir.");
        }
        write(outDir, "tr/com/havelsan/uyap/system/editor/b/a.class", drv.toBytecode());
        System.out.println("[FopConfigPatch] b/a: newInstance → FopFonts.apply köprüsü yazıldı.");

        // --- 2) iText yolu (b/c FontMapper): awtToPdf → ITextFonts.map (gömülü font) ---
        CtClass mapper = pool.get("tr.com.havelsan.uyap.system.editor.b.c");
        CtMethod awtToPdf = mapper.getMethod(
            "awtToPdf", "(Ljava/awt/Font;)Lcom/lowagie/text/pdf/BaseFont;");
        awtToPdf.setBody("{ return macosfop.ITextFonts.map($1); }");
        write(outDir, "tr/com/havelsan/uyap/system/editor/b/c.class", mapper.toBytecode());
        System.out.println("[FopConfigPatch] b/c: awtToPdf → ITextFonts.map (gömülü) yazıldı.");

        // --- 3) iText yolu sayfa boyutu (b/b): at.getPageFormat sonucunu A4'e sabitle ---
        //     (footer'ın alttan kesilmesi = asimetrik kenar yüzünden kısa sayfa hesabı)
        CtClass conv = pool.get("tr.com.havelsan.uyap.system.editor.b.b");
        final boolean[] pfHit = {false};
        conv.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall mc) throws javassist.CannotCompileException {
                if ("tr.com.havelsan.uyap.system.swing.wp.b.at".equals(mc.getClassName())
                        && "getPageFormat".equals(mc.getMethodName())) {
                    mc.replace("{ $_ = macosfop.PageFix.a4($proceed($$)); }");
                    pfHit[0] = true;
                }
            }
        });
        if (!pfHit[0]) {
            throw new IllegalStateException(
                "b/b'de at.getPageFormat çağrısı bulunamadı — UDE sürümü değişmiş olabilir.");
        }
        write(outDir, "tr/com/havelsan/uyap/system/editor/b/b.class", conv.toBytecode());
        System.out.println("[FopConfigPatch] b/b: at.getPageFormat → PageFix.a4 (A4 zorla) yazıldı.");
    }

    private static void write(File outDir, String relPath, byte[] code) throws Exception {
        File f = new File(outDir, relPath);
        f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(code);
        }
    }
}
