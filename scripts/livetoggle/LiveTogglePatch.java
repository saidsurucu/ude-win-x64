import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

/*
 * Otomatik düzeltme toggle eylemlerini anında-etkin yapar.
 *
 * Hedefler (üçü de aynı şekil): a(Ljava/awt/event/ActionEvent;)V
 *   text.dq — "To-Uppercase"        → pref ToUpperCase          (kutu z.a)
 *   text.dA — "first Letter Upper"  → pref FirstLetterUpperCase (kutu z.b)
 *   text.db — "spell-check"         → pref SpellCheck           (kutu z.c)
 *
 * Üç dokunuş:
 *   1) insertBefore: LiveToggle.syncSource(key, $1) — menü kopyasından
 *      tıklanınca şerit kutusu kaynağa eşitlenir (orijinal gövde değeri hep
 *      şeritteki z statiklerinden okur; upstream eski-değer-kaydetme bug'ı
 *      kapanır).
 *   2) ExprEditor: gui.kP.b(...) "yeniden başlatılmadığı sürece..." diyaloğu
 *      → $_ = 0; (tam olarak 1 çağrı beklenir; değilse UDE sürümü değişmiştir
 *      → İSTİSNA, build geri alır).
 *   3) insertAfter: LiveToggle.apply(key) — tercihi okuyup açık editörlere
 *      uygular.
 *
 * Javassist kuralları: gövde string'lerinde // yorum YASAK; sınıf başına tek
 * writeFile. Helper (com/udewin/livetoggle/LiveToggle) bu patcher koşmadan ÖNCE
 * jar'a enjekte edilmiş olmalı (insertBefore/After derlemesi onu jar
 * classpath'inden çözer).
 *
 * Kullanım: java LiveTogglePatch <editor-app.jar> <çıktı-dizini>
 */
public class LiveTogglePatch {

    private static final String PKG =
        "tr.com.havelsan.uyap.system.editor.common.text.";

    public static void main(String[] args) throws Exception {
        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(args[0]);
        patch(cp, args[1], PKG + "dq", "ToUpperCase");
        patch(cp, args[1], PKG + "dA", "FirstLetterUpperCase");
        patch(cp, args[1], PKG + "db", "SpellCheck");
        System.out.println("[livetoggle] dq/dA/db yamalandı (anında etkinleşme + diyalog kaldırıldı).");
    }

    private static void patch(ClassPool cp, String outDir, String cls,
                              String key) throws Exception {
        CtClass cc = cp.get(cls);
        CtMethod m = cc.getMethod("a", "(Ljava/awt/event/ActionEvent;)V");

        m.insertBefore(
            "com.udewin.livetoggle.LiveToggle.syncSource(\"" + key + "\", $1);");

        final int[] removed = { 0 };
        m.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall c) throws javassist.CannotCompileException {
                if ("b".equals(c.getMethodName())
                        && c.getClassName().endsWith(".kP")) {
                    c.replace("$_ = 0;");
                    removed[0]++;
                }
            }
        });
        if (removed[0] != 1) {
            throw new IllegalStateException(cls + ": beklenen 1 kP.b diyalog çağrısı, bulunan " + removed[0]);
        }

        m.insertAfter("com.udewin.livetoggle.LiveToggle.apply(\"" + key + "\");");

        cc.writeFile(outDir);
        cc.detach();
    }
}
