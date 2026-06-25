import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;

import java.io.File;
import java.io.FileOutputStream;

/**
 * UDE fare ile imaj boyutlandırma (IMGRESIZE=1) build-zamanı bytecode yaması.
 * Hedef: tr…editor.common.text.hj (editör metin bileşeni, JTextPane torunu).
 *   1) processMouseEvent / processMouseMotionEvent: yoksa override EKLENİR,
 *      varsa başına guard girilir — ImageResizeController.intercept true dönerse
 *      olay tüketilir (listener'lar ve caret olayı hiç görmez). KRİTİK.
 *   2) paint sonuna ImageResizeController.paintOverlay çağrısı (tutamaç/çerçeve).
 *
 * ÖN KOŞUL: macosimgresize/ImageResizeController.class jar'a ÖNCEDEN enjekte
 * edilmiş olmalı (javassist çağrı derlemesi sınıfı jar classpath'inden çözer).
 *
 * Argümanlar: <editor-app.jar> <out-dir>
 */
public class ImageResizePatch {
    private static final String HJ = "tr.com.havelsan.uyap.system.editor.common.text.hj";
    private static final String CTRL = "macosimgresize.ImageResizeController";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Kullanım: ImageResizePatch <jar> <out-dir>");
            System.exit(2);
        }
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(args[0]);
        File outDir = new File(args[1]);

        CtClass hj = pool.get(HJ);

        // --- 1) fare olay yakalama (KRİTİK — başarısızsa die) ---
        guardEventMethod(pool, hj, "processMouseEvent");
        guardEventMethod(pool, hj, "processMouseMotionEvent");

        // --- 2) paint overlay (KRİTİK — tutamaçsız özellik anlamsız) ---
        CtMethod paint = hj.getDeclaredMethod("paint",
                new CtClass[]{pool.get("java.awt.Graphics")});
        paint.insertAfter(CTRL + ".paintOverlay(this, $1);");
        System.out.println("[ImageResizePatch] paint overlay enjekte edildi.");

        writeClass(hj, outDir);
        System.out.println("[ImageResizePatch] hj yamalandı.");
    }

    /** Metot hj'de varsa başına guard ekler; yoksa super'e delege eden override ekler. */
    private static void guardEventMethod(ClassPool pool, CtClass hj, String name)
            throws Exception {
        String guard = "{ if (" + CTRL + ".intercept(this, $1)) return; }";
        CtClass me = pool.get("java.awt.event.MouseEvent");
        try {
            CtMethod m = hj.getDeclaredMethod(name, new CtClass[]{me});
            m.insertBefore(guard);
            System.out.println("[ImageResizePatch] " + name + ": mevcut metoda guard eklendi.");
        } catch (javassist.NotFoundException nf) {
            hj.addMethod(CtNewMethod.make(
                "protected void " + name + "(java.awt.event.MouseEvent e) {"
              + "  if (" + CTRL + ".intercept(this, e)) return;"
              + "  super." + name + "(e);"
              + "}", hj));
            System.out.println("[ImageResizePatch] " + name + ": override eklendi.");
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
