import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Native satır aralığı menüsüne "1.5" ekler (satıcı unutmuş).
 *
 * Giriş>Paragraf bandındaki satır-aralığı popup'ı tr.gov.uyap.system.a.b.a.a.M
 * (JCommandPopupMenu): öğeler "1.0","1.15","2.0","2.5","3.0"; her öğenin
 * dinleyicisi (N/O/P/Q/R) tek satır M.a(this.a).a(görünenDeğer) — D.a(float)
 * satıcının kendi uygulama yolu (display−1 dönüşümü + undo/seçim mantığı orada).
 *
 * KRİTİK Javassist tuzağı: pakette `a` adlı SINIF da var
 * (tr/gov/uyap/system/a/b/a/a.class) → bu paketin sınıfları Javassist KAYNAK
 * dizgisinde FQCN ile ANILAMAZ (çözümleyici a$M dener, CannotCompile).
 * Bu yüzden: (1) LS15 = O'nun getAndRename BYTECODE kopyası (kaynak yok);
 * (2) 1.15f→1.5f değişimi ExprEditor ile `$0.a(1.5f)` (FQCN'siz — $0'ın tipi
 * çağrı yerinden bilinir); (3) M kurucusundaki enjeksiyonda dinleyici,
 * temiz-paketli tr.lsinject.LsInject.make(Object) fabrikasından gelir
 * (Class.forName STRING literal + yansıma ctor — kaynak çözümleyici bypass).
 *
 * Yama: M kurucusunda 3. addMenuButton ("2.0" öğesi) öncesine
 * JCommandMenuButton("1.5", null) + LS15 → menü 1.0, 1.15, 1.5, 2.0, 2.5, 3.0.
 * İdempotans: LS15 jar'da varsa atlanır. UDF formatı değişmez.
 *
 * Argümanlar: <editor-app.jar> <out-dir>
 */
public class LineSpacingPatch {
    static final String M   = "tr.gov.uyap.system.a.b.a.a.M";
    static final String O   = "tr.gov.uyap.system.a.b.a.a.O";
    static final String LS  = "tr.gov.uyap.system.a.b.a.a.LS15";
    static final String INJ = "tr.lsinject.LsInject";
    static final String BTN = "org.pushingpixels.flamingo.api.common.JCommandMenuButton";
    static final String RI  = "org.pushingpixels.flamingo.api.common.icon.ResizableIcon";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Kullanım: LineSpacingPatch <editor-app.jar> <out-dir>");
            System.exit(2);
        }
        String jar = args[0];
        File outDir = new File(args[1]);

        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(jar);

        if (pool.getOrNull(LS) != null) {
            System.out.println("[LineSpacingPatch] zaten yamalı (LS15 mevcut); atlandı.");
            return;
        }

        // 1) "1.15" dinleyicisi O → LS15 (aynı pakette bytecode kopyası;
        //    paket-içi M.a erişimi bozulmaz). Sabit 1.15f → 1.5f: D.a(F)
        //    çağrısı $0 üzerinden yeniden yazılır (kaynakta sınıf adı YOK).
        CtClass ls = pool.getAndRename(O, LS);
        CtMethod ap = ls.getDeclaredMethod("actionPerformed");
        final int[] swapped = {0};
        ap.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall mc) throws javassist.CannotCompileException {
                if ("a".equals(mc.getMethodName()) && "(F)V".equals(mc.getSignature())) {
                    mc.replace("{ $0.a(1.5f); }");
                    swapped[0]++;
                }
            }
        });
        if (swapped[0] != 1) {
            throw new IllegalStateException(
                "O.actionPerformed içinde D.a(F) çağrısı bulunamadı (n=" + swapped[0] + ").");
        }

        // 2) Fabrika: tr.lsinject.LsInject.make(Object) → LS15 örneği.
        //    Obfuscate paket kaynakta anılamadığından yansıma + string literal.
        CtClass inj = pool.makeClass(INJ);
        inj.addMethod(CtNewMethod.make(
              "public static java.awt.event.ActionListener make(Object m) {"
            + "  try {"
            + "    Class c = Class.forName(\"" + LS + "\");"
            + "    java.lang.reflect.Constructor k = c.getDeclaredConstructors()[0];"
            + "    k.setAccessible(true);"
            + "    return (java.awt.event.ActionListener) k.newInstance(new Object[]{ m });"
            + "  } catch (Throwable t) {"
            + "    throw new RuntimeException(t);"
            + "  }"
            + "}", inj));

        // 3) M kurucusu: 3. addMenuButton ("2.0") öncesine "1.5" öğesi.
        CtClass m = pool.get(M);
        CtConstructor ctor = m.getDeclaredConstructors()[0];
        final int[] count = {0};
        final int[] hit = {0};
        ctor.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall mc) throws javassist.CannotCompileException {
                if (!"addMenuButton".equals(mc.getMethodName())) return;
                count[0]++;
                if (count[0] != 3) return;
                mc.replace(
                    "{ " + BTN + " b15 = new " + BTN + "(\"1.5\", (" + RI + ") null);"
                  + "  b15.addActionListener(" + INJ + ".make($0));"
                  + "  $0.addMenuButton(b15);"
                  + "  $proceed($$); }");
                hit[0]++;
            }
        });
        if (hit[0] != 1) {
            throw new IllegalStateException(
                "M kurucusunda 3. addMenuButton bulunamadı (toplam=" + count[0]
                + ") — UDE sürümü değişmiş olabilir.");
        }

        write(outDir, "tr/gov/uyap/system/a/b/a/a/LS15.class", ls.toBytecode());
        write(outDir, "tr/lsinject/LsInject.class", inj.toBytecode());
        write(outDir, "tr/gov/uyap/system/a/b/a/a/M.class", m.toBytecode());
        System.out.println("[LineSpacingPatch] satır aralığı menüsüne 1.5 eklendi (M + LS15 + LsInject yazıldı).");
    }

    static void write(File outDir, String rel, byte[] bytes) throws Exception {
        File f = new File(outDir, rel);
        f.getParentFile().mkdirs();
        try (FileOutputStream fo = new FileOutputStream(f)) {
            fo.write(bytes);
        }
    }
}
