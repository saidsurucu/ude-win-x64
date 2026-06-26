import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.io.File;
import java.io.FileOutputStream;

/**
 * WPAppManager.main basina com.udewin.zoom.ZoomKeys.install() cagrisi enjekte eder
 * (Ctrl+/Ctrl- klavye zoom). ZoomKeys helper'i bu patcher'dan ONCE jar'a eklenir.
 *
 * Argumanlar: <editor-app.jar> <out-dir>
 */
public class ZoomPatch {
    static final String MAIN_CLASS = "tr.com.havelsan.uyap.system.editor.common.WPAppManager";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Kullanim: ZoomPatch <jar> <out-dir>");
            System.exit(2);
        }
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(args[0]);

        CtClass cc = pool.get(MAIN_CLASS);
        CtMethod main = cc.getMethod("main", "([Ljava/lang/String;)V");
        main.insertBefore("com.udewin.zoom.ZoomKeys.install();");

        byte[] code = cc.toBytecode();
        File f = new File(args[1], MAIN_CLASS.replace('.', '/') + ".class");
        f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(code); }
        System.out.println("[ZoomPatch] WPAppManager.main yamalandi (Ctrl+/Ctrl- zoom).");
    }
}
