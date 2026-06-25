import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

/*
 * gR.c() (Modüller panelini kuran metot) sonuna Antetlerim bölümünü ekler.
 *
 * Javassist kuralları: gövde string'inde // yorum YASAK; tek writeFile.
 * macosantet helper'ları bu patcher koşmadan ÖNCE jar'a enjekte edilmiş
 * olmalı (insertAfter derlemesi AntetUI'yi jar classpath'inden çözer).
 *
 * Kullanım: java AntetPatch <editor-app.jar> <çıktı-dizini>
 */
public class AntetPatch {

    public static void main(String[] args) throws Exception {
        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(args[0]);

        CtClass cc = cp.get("tr.com.havelsan.uyap.system.editor.common.gui.gR");
        CtMethod m = cc.getDeclaredMethod("c", new CtClass[0]);
        m.insertAfter("{ macosantet.AntetUI.install(this); }");
        cc.writeFile(args[1]);
        cc.detach();

        System.out.println("[antet] gR.c() yamalandı (Antetlerim bölümü).");
    }
}
