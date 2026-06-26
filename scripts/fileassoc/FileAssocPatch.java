import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.io.File;
import java.io.FileOutputStream;

/**
 * WPAppManager.main basina arguman normalizasyonu enjekte eder:
 *   $1 = com.udewin.fileassoc.ArgFix.normalize($1);
 * Boylece .udf cift-tikinda (args=["...udf"]) basa "getNewWPInstance" eklenir ve dosya acilir.
 * ArgFix helper'i bu patcher'dan ONCE jar'a eklenir (pool jar'dan cozer).
 *
 * Argumanlar: <editor-app.jar> <out-dir>
 */
public class FileAssocPatch {
    static final String MAIN_CLASS = "tr.com.havelsan.uyap.system.editor.common.WPAppManager";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Kullanim: FileAssocPatch <jar> <out-dir>");
            System.exit(2);
        }
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(args[0]);

        CtClass cc = pool.get(MAIN_CLASS);
        CtMethod main = cc.getMethod("main", "([Ljava/lang/String;)V");
        main.insertBefore("$1 = com.udewin.fileassoc.ArgFix.normalize($1);");

        byte[] code = cc.toBytecode();
        File f = new File(args[1], MAIN_CLASS.replace('.', '/') + ".class");
        f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(code); }
        System.out.println("[FileAssocPatch] WPAppManager.main yamalandi (.udf cift-tik arg normalizasyonu).");
    }
}
