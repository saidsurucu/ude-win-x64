import javassist.ClassPool;
import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import javassist.expr.NewExpr;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.Bytecode;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Açılışta gereksiz Zemberek sözlük yüklemesi — kök neden + düzeltme.
 *
 * (1) text.fk.run() (belge kurulumunda EDT'de) yazım denetimi dinleyicisini takmadan
 *     önce şunu sınar:
 *         if (im.a() != null && "true".equals(new im().a())) fi.addKeyListener(new im());
 *     Statik getter im.a():Zemberek ÖNCE çalışır ve tembel olarak
 *     new Zemberek(new TurkiyeTurkcesi()) kurar → initValues/SpellCheck=false iken bile
 *     sözlük yüklenir (Zulu 11: ~380 ms EDT + ~8 MB kalıcı yığın, boşa).
 *     Yama: o çağrı yeri yalnız SpellCheck tercihi "true" iken getter'ı çağırır, aksi
 *     halde null döner (kısa devre → dinleyici takılmaz, stok "kapalı" davranışı).
 *     Tercih im'in KENDİ a():String okuyucusundan okunur (orijinal ikinci koşulun
 *     aynısı; yan etkisi — z.c onay kutusunu tercihe eşitlemek — idempotent).
 *     Sonradan açma (LIVETOGGLE / dE işçisi) getter'ı kendisi tembel çağırır.
 *
 * (2) text.fK (sağ tık menüsü) her öneri aramasında new Zemberek(...) kurar
 *     (~0,37 s + ~8 MB / tık). Yama: paylaşılan im.a() örneği kullanılır; o null ise
 *     (sözlük yüklenemedi) orijinal kurulum yedek olarak çalışır.
 *
 * Argümanlar: <editor-app.jar> <out-dir>
 * Çıktı: <out-dir>/fk/tr/... ve <out-dir>/fK_/tr/... AYRI ağaçlar — fk.class ile fK.class
 * Windows (NTFS) / macOS'un harf-duyarsız dosya sisteminde aynı dizinde birbirini EZER.
 * apply-zemberek.ps1 her alt ağacı jar'a ayrı ekler.
 */
public class ZemberekLazyPatch {
    static final String PKG = "tr.com.havelsan.uyap.system.editor.common.text.";
    static final String IM = PKG + "im";
    static final String ZEMBEREK = "net.zemberek.erisim.Zemberek";
    static final String ZGET_SIG = "()L" + ZEMBEREK.replace('.', '/') + ";";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Kullanım: ZemberekLazyPatch <editor-app.jar> <out-dir>");
            System.exit(2);
        }
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(args[0]);
        File outDir = new File(args[1]);

        patchFk(pool, outDir);
        patchFK(pool, outDir);
    }

    /** fk.run(): im.a():Zemberek çağrısını SpellCheck tercihine bağla. */
    static void patchFk(ClassPool pool, File outDir) throws Exception {
        CtClass cls = pool.get(PKG + "fk");
        CtMethod run = cls.getDeclaredMethod("run", new CtClass[0]);

        final boolean[] already = {false};
        run.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall m) {
                if ("java.lang.Boolean".equals(m.getClassName())
                        && "parseBoolean".equals(m.getMethodName())) already[0] = true;
            }
        });
        if (already[0]) {
            System.out.println("[ZemberekLazyPatch] fk.run zaten yamalı; atlandı.");
            return;
        }

        addHelper(cls, "zlazy$pref", true);
        addHelper(cls, "zlazy$shared", false);
        final int[] hits = {0};
        run.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall m) throws CannotCompileException {
                if (IM.equals(m.getClassName()) && "a".equals(m.getMethodName())
                        && ZGET_SIG.equals(m.getSignature())) {
                    hits[0]++;
                    m.replace("{ if (java.lang.Boolean.parseBoolean(zlazy$pref()))"
                            + " { $_ = zlazy$shared(); } else { $_ = null; } }");
                }
            }
        });
        if (hits[0] != 1) {
            throw new IllegalStateException("fk.run: beklenen 1 im.a():Zemberek çağrısı, bulunan " + hits[0]);
        }
        write(new File(outDir, "fk"), PKG.replace('.', '/') + "fk.class", cls.toBytecode());
        System.out.println("[ZemberekLazyPatch] fk.run: Zemberek yüklemesi SpellCheck=true koşuluna bağlandı.");
    }

    /** fK: öneri başına new Zemberek(...) → paylaşılan im.a() (null ise orijinal). */
    static void patchFK(ClassPool pool, File outDir) throws Exception {
        CtClass cls = pool.get(PKG + "fK");
        try {
            cls.getDeclaredMethod("zlazy$shared");
            System.out.println("[ZemberekLazyPatch] fK zaten yamalı; atlandı.");
            return;
        } catch (javassist.NotFoundException nf) {
            addHelper(cls, "zlazy$shared", false);
        }
        final int[] hits = {0};
        cls.instrument(new ExprEditor() {
            @Override
            public void edit(NewExpr e) throws CannotCompileException {
                if (ZEMBEREK.equals(e.getClassName())) {
                    hits[0]++;
                    e.replace("{ $_ = zlazy$shared(); if ($_ == null) { $_ = $proceed($$); } }");
                }
            }
        });
        if (hits[0] == 0) {
            throw new IllegalStateException("fK: new Zemberek çağrısı bulunamadı (UDE sürümü değişmiş olabilir)");
        }
        write(new File(outDir, "fK_"), PKG.replace('.', '/') + "fK.class", cls.toBytecode());
        System.out.println("[ZemberekLazyPatch] fK: " + hits[0] + " öneri yerinde paylaşılan Zemberek örneği kullanılıyor.");
    }

    /**
     * im'de üç argümansız a() var (static Zemberek, static void, instance String);
     * Javassist kaynak derleyicisi dönüş tipine bakmadan seçer — $proceed bile instance
     * a():String'e çözüldü ("a is not static"); yanlışlıkla önbelleği silen void a()
     * de seçilebilirdi. Bu yüzden çağrı tam tanımlayıcıyla Bytecode API'si
     * üzerinden üretilen özel statik yardımcıya konur.
     * pref=true: return new im().a():String   |   pref=false: return im.a():Zemberek
     */
    static void addHelper(CtClass cls, String name, boolean pref) throws Exception {
        ConstPool cp = cls.getClassFile().getConstPool();
        Bytecode b = new Bytecode(cp, 2, 0);
        if (pref) {
            b.addNew(IM);
            b.addOpcode(Bytecode.DUP);
            b.addInvokespecial(IM, "<init>", "()V");
            b.addInvokevirtual(IM, "a", "()Ljava/lang/String;");
        } else {
            b.addInvokestatic(IM, "a", ZGET_SIG);
        }
        b.addOpcode(Bytecode.ARETURN);
        String ret = pref ? "java.lang.String" : ZEMBEREK;
        CtMethod m = javassist.CtNewMethod.make(
            "private static " + ret + " " + name + "() { return null; }", cls);
        cls.addMethod(m);
        MethodInfo mi = m.getMethodInfo();
        mi.setAccessFlags(AccessFlag.PRIVATE | AccessFlag.STATIC | AccessFlag.SYNTHETIC);
        mi.setCodeAttribute(b.toCodeAttribute());
        mi.rebuildStackMapIf6(cls.getClassPool(), cls.getClassFile());
    }

    private static void write(File outDir, String relPath, byte[] code) throws Exception {
        File f = new File(outDir, relPath);
        f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(code);
        }
    }
}
