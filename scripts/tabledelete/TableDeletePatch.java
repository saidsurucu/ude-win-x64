import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.io.File;
import java.io.FileOutputStream;

/**
 * WPAppManager.main basina TableDeleteInstaller.install() cagrisi enjekte eder.
 * Yardimci siniflar (TableDelete, TableDeleteInstaller) apply-tabledelete.ps1
 * tarafindan ONCE jar'a eklenir; bu patcher yalniz WPAppManager'i yamalar.
 *
 * Argumanlar: <editor-app.jar> <helper-classes-dir> <out-dir>
 */
public class TableDeletePatch {
    private static final String MAIN_CLASS =
        "tr.com.havelsan.uyap.system.editor.common.WPAppManager";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Kullanim: TableDeletePatch <jar> <helper-dir> <out-dir>");
            System.exit(2);
        }
        String jar = args[0];
        String helperDir = args[1];
        File outDir = new File(args[2]);

        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(jar);
        pool.insertClassPath(helperDir);

        CtClass cc = pool.get(MAIN_CLASS);
        CtMethod main = cc.getMethod("main", "([Ljava/lang/String;)V");
        main.insertBefore("com.udewin.tabledelete.TableDeleteInstaller.install();");

        byte[] code = cc.toBytecode();
        File f = new File(outDir, cc.getName().replace('.', '/') + ".class");
        f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(code); }
        cc.detach();
        System.out.println("[TableDeletePatch] WPAppManager.main yamalandi.");
    }
}
