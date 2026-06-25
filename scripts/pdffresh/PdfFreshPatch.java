import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.io.File;
import java.io.FileOutputStream;

/**
 * "PDF Olarak Kaydet" eski içeriği yazıyor — kök neden + düzeltme.
 *
 * UDE'nin editör paneli (gui.lo) iki modlu serialize eder:
 *   lo.a(OutputStream)            → lo.a(out, false)  → ÖNBELLEKLİ (alan: common.ac)
 *   lo.a(OutputStream, boolean)   → param true        → canlı DocumentEx'ten TAZE serialize
 *
 * Önbellekli mod (param=false), 'ac' alanı doluysa ve hO.c() false ise, en son TAM
 * serialize'ın zip'ini (content.xml) yeniden kullanır — canlı belgeyi yeniden
 * serialize ETMEZ. Düz metin düzenlemeleri 'ac'yi geçersizleştirmediğinden önbellek
 * bayatlar.
 *
 * PDF dışa aktarımı (text.J.b(java.io.File) → iText editor.b.b) içeriği tam olarak bu
 * önbellekli yoldan (lo.a(out)) alır → kullanıcı bir değişiklik yapıp doğrudan
 * "PDF Olarak Kaydet" derse PDF, değişiklikten ÖNCEKİ içeriği taşır. Önce "Kaydet"
 * demek tam serialize'ı tetikleyip 'ac'yi tazelediği için workaround işe yarar.
 *
 * Bu yama PDF yolundaki tek lo.a(out) çağrısını lo.a(out, true) ile değiştirir →
 * PDF her zaman canlı belgeden TAZE serialize edilir (Word davranışı; "önce Kaydet"
 * gereksinimi kalkar). Yalnız text.J.b(File) (yalnızca PDF dışa aktarımına bağlı)
 * yamalanır → diğer kaydetme/dışa aktarma yolları (UDF/RTF/imza) dokunulmaz.
 *
 * Argümanlar: <editor-app.jar> <out-dir>
 */
public class PdfFreshPatch {
    static final String J = "tr.com.havelsan.uyap.system.editor.common.text.J";
    static final String LO = "tr.com.havelsan.uyap.system.editor.common.gui.lo";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Kullanım: PdfFreshPatch <editor-app.jar> <out-dir>");
            System.exit(2);
        }
        String jar = args[0];
        File outDir = new File(args[1]);

        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(jar);

        CtClass cls = pool.get(J);
        // PDF dışa aktarımı: private Boolean b(java.io.File)
        CtMethod m = cls.getDeclaredMethod("b", new CtClass[]{ pool.get("java.io.File") });

        final int[] hit = {0};
        final int[] already = {0};
        m.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall mc) throws javassist.CannotCompileException {
                if (!LO.equals(mc.getClassName()) || !"a".equals(mc.getMethodName())) return;
                if ("(Ljava/io/OutputStream;)V".equals(mc.getSignature())) {
                    // tek-arg (önbellekli) → iki-arg (taze) zorla
                    mc.replace("{ $0.a($1, true); }");
                    hit[0]++;
                } else if ("(Ljava/io/OutputStream;Z)V".equals(mc.getSignature())) {
                    // zaten yamalı (iki-arg çağrı mevcut)
                    already[0]++;
                }
            }
        });

        if (hit[0] == 0) {
            if (already[0] > 0) {
                System.out.println("[PdfFreshPatch] zaten yamalı (lo.a(out,true)); atlandı.");
                return; // idempotans: yeniden yazma yok
            }
            throw new IllegalStateException(
                "text.J.b(File) içinde lo.a(OutputStream) çağrısı bulunamadı — UDE sürümü değişmiş olabilir.");
        }

        write(outDir, "tr/com/havelsan/uyap/system/editor/common/text/J.class", cls.toBytecode());
        System.out.println("[PdfFreshPatch] text.J.b(File): lo.a(out) → lo.a(out, true) (PDF taze serialize) yazıldı. ("
            + hit[0] + " çağrı)");
    }

    private static void write(File outDir, String relPath, byte[] code) throws Exception {
        File f = new File(outDir, relPath);
        f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(code);
        }
    }
}
