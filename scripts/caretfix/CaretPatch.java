import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.io.File;
import java.io.FileOutputStream;

/**
 * UDE zoom imlec kaymasi duzeltmesi (CARETFIX=1) - SADECE Faz 2 (platform-NOTR).
 *
 * Mac yamasinin Faz 1'i (common.s.a 2px->1px imlec) macOS fractional-render'a ozgu
 * KOZMETIK bir degisiklikti; Windows tam-piksel render'da 2px imlec sorunsuz -> PORT EDILMEDI.
 *
 * Faz 2 (buradaki): Bolum gorunumu wp.prof.d.O, device<->logical kopru p.a/p.c kullanir.
 * O.paint ve O.modelToView(5-arg, secim) girisi p.c ile device->logical cevirir; ama
 * O.modelToView(3-arg, IMLEC) bu p.c'yi ATLAR -> caret_x ile paint_x arasinda
 * kayma = (s-1)*D-1 olusur (zoom faktoru s ile buyur). viewToModel de ayni eksiklikten
 * tiklamayi yanlis karaktere oturtur. Duzeltme: 3-arg modelToView'a ve viewToModel'e
 * p.c giris cevirimini ekle (5-arg/paint kardesini yansitarak). Koordinat matematigi
 * tutarliligi; macOS'a ozgu degil -> %100 disi zoom'da Windows kullanicisini da etkiler.
 *
 * p.c verilen Rectangle'i yerinde degistirir -> getBounds() klon verir, cagiranin sekli korunur.
 *
 * Argumanlar: <editor-app.jar> <out-dir>
 */
public class CaretPatch {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Kullanim: CaretPatch <jar> <out-dir>");
            System.exit(2);
        }
        String jar = args[0];
        File outDir = new File(args[1]);

        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(jar);

        CtClass o = pool.get("tr.com.havelsan.uyap.system.swing.wp.prof.d.O");
        CtMethod m2v = o.getMethod("modelToView",
            "(ILjava/awt/Shape;Ljavax/swing/text/Position$Bias;)Ljava/awt/Shape;");
        m2v.insertBefore(
            "{ if ($2 != null) { java.awt.Rectangle _r = $2.getBounds();"
          + " _r = tr.com.havelsan.uyap.system.swing.wp.textUtils.p.c(_r, this.a());"
          + " $2 = _r; } }");
        CtMethod v2m = o.getMethod("viewToModel",
            "(FFLjava/awt/Shape;[Ljavax/swing/text/Position$Bias;)I");
        v2m.insertBefore(
            "{ if ($3 != null) { java.awt.Rectangle _r = $3.getBounds();"
          + " _r = tr.com.havelsan.uyap.system.swing.wp.textUtils.p.c(_r, this.a());"
          + " $3 = _r; } }");
        writeClass(o, outDir);
        System.out.println("[CaretPatch] wp.prof.d.O modelToView/viewToModel p.c cevirimi eklendi (zoom kaymasi - Faz 2).");
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
