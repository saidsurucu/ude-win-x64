import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.NewExpr;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Büyük belgelerde açılış donması — kök neden + düzeltme.
 *
 * common.ac (ByteArrayInputStream türevi; belgenin disk bayt önbelleği) kendini
 * ac.a(InputStream) ile doldurur: InputStream.read() BAYT BAYT döngüsü. gui.lo
 * bunu (lo.k() + 5 çağrı yeri daha) ham, tamponsuz FileInputStream ile çağırır →
 * her bayt bir read(2) sistem çağrısı, EDT'de (pencere gösterimi / belge açılışı).
 * Ölçüm (Zulu 11): 717 KB = ~560 ms tamponsuz / 2 ms tamponlu; 10 MB = 8,7 s / 41 ms.
 *
 * Yama ac.a(InputStream) başına: $1 null değilse ve zaten BufferedInputStream /
 * ByteArrayInputStream (ac'nin kendisi dahil — orijinal 'instanceof ac' kısa yolu
 * korunur) değilse new BufferedInputStream($1, 65536) ile sarılır. Metot akışı
 * EOF'a kadar okuduğundan önden okuma çağıranın akışından bayt çalmaz; kapatma
 * çağıranın orijinal akışında kalır. Tüm çağrı yerleri tek noktadan kapsanır.
 *
 * Argümanlar: <editor-app.jar> <out-dir>
 */
public class BufReadPatch {
    static final String AC = "tr.com.havelsan.uyap.system.editor.common.ac";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Kullanım: BufReadPatch <editor-app.jar> <out-dir>");
            System.exit(2);
        }
        String jar = args[0];
        File outDir = new File(args[1]);

        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(jar);

        CtClass cls = pool.get(AC);
        CtMethod m = cls.getDeclaredMethod("a", new CtClass[]{ pool.get("java.io.InputStream") });

        final boolean[] already = {false};
        m.instrument(new ExprEditor() {
            @Override
            public void edit(NewExpr e) {
                if ("java.io.BufferedInputStream".equals(e.getClassName())) already[0] = true;
            }
        });
        if (already[0]) {
            System.out.println("[BufReadPatch] zaten yamalı (BufferedInputStream sarması); atlandı.");
            return;
        }

        m.insertBefore(
            "if ($1 != null && !($1 instanceof java.io.BufferedInputStream)"
            + " && !($1 instanceof java.io.ByteArrayInputStream)) {"
            + " $1 = new java.io.BufferedInputStream($1, 65536); }");

        write(outDir, "tr/com/havelsan/uyap/system/editor/common/ac.class", cls.toBytecode());
        System.out.println("[BufReadPatch] common.ac.a(InputStream): BufferedInputStream(64K) sarması yazıldı.");
    }

    private static void write(File outDir, String relPath, byte[] code) throws Exception {
        File f = new File(outDir, relPath);
        f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(code);
        }
    }
}
