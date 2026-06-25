import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.io.File;
import java.io.FileOutputStream;

/**
 * UDE satır-içi imaj tam-çözünürlük (IMGFULL=1) build-zamanı bytecode yamaları:
 *   1) tr…editor.utils.h.a(hj, BufferedImage): gövde = "return $2;" →
 *      ekleme anındaki yıkıcı ~600x790 küçültme kaldırılır, imaj tam
 *      çözünürlükte (kayıpsız PNG) gömülür. KRİTİK.
 *   2) tr…swing.wp.b.at paint: drawImage(Image,x,y,w,h,obs) çağrısından önce
 *      Graphics2D'ye BICUBIC interpolation hint (ekran/print ölçekleme keskinliği).
 *      best-effort.
 *
 * Argümanlar: <editor-app.jar> <out-dir>
 */
public class ImageInsertPatch {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Kullanım: ImageInsertPatch <jar> <out-dir>");
            System.exit(2);
        }
        String jar = args[0];
        File outDir = new File(args[1]);

        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(jar);

        // --- 1) h.a(hj, BufferedImage): yıkıcı küçültmeyi kaldır (KRİTİK) ---
        CtClass h = pool.get("tr.com.havelsan.uyap.system.editor.utils.h");
        CtMethod fit = h.getMethod(
            "a",
            "(Ltr/com/havelsan/uyap/system/editor/common/text/hj;Ljava/awt/image/BufferedImage;)Ljava/awt/image/BufferedImage;");
        fit.setBody("{ return $2; }");   // static → $2 = BufferedImage parametresi
        writeClass(h, outDir);
        System.out.println("[ImageInsertPatch] h.a(hj,BufferedImage) tam-çözünürlük yaması uygulandı (return $2).");

        // --- 2) at paint: drawImage öncesi BICUBIC hint (best-effort) ---
        try {
            CtClass at = pool.get("tr.com.havelsan.uyap.system.swing.wp.b.at");
            final boolean[] patched = { false };
            at.instrument(new ExprEditor() {
                public void edit(MethodCall m) throws javassist.CannotCompileException {
                    if (m.getMethodName().equals("drawImage")
                            && m.getSignature().equals("(Ljava/awt/Image;IIIILjava/awt/image/ImageObserver;)Z")) {
                        // __old==null ise önceki interpolation hint yoktu → BICUBIC bu
                        // paint çağrısının kalanında bilerek set kalır (yalnızca image ops'u etkiler).
                        m.replace(
                            "{ if ($0 instanceof java.awt.Graphics2D) {"
                          + "    java.awt.Graphics2D g2 = (java.awt.Graphics2D)$0;"
                          + "    Object __old = g2.getRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION);"
                          + "    g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,"
                          + "        java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);"
                          + "    $_ = $proceed($$);"
                          + "    if (__old != null) g2.setRenderingHint("
                          + "        java.awt.RenderingHints.KEY_INTERPOLATION, __old);"
                          + "  } else { $_ = $proceed($$); } }");
                        patched[0] = true;
                    }
                }
            });
            if (patched[0]) {
                writeClass(at, outDir);
                System.out.println("[ImageInsertPatch] at.drawImage BICUBIC hint yaması uygulandı.");
            } else {
                System.out.println("[ImageInsertPatch] UYARI: at içinde beklenen drawImage çağrısı bulunamadı; bicubic yaması atlandı.");
            }
        } catch (Throwable t) {
            System.out.println("[ImageInsertPatch] UYARI: at bicubic yaması atlandı: " + t);
        }
    }

    private static void writeClass(CtClass cc, File outDir) throws Exception {
        byte[] code = cc.toBytecode();
        File f = new File(outDir, cc.getName().replace('.', '/') + ".class");
        f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(code);
        }
    }
}
