import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Sağ tık menüsüne "Formatsız Yapıştır" ekler (build-zamanı bytecode yaması).
 *
 * Editörün sağ tık menüsü UDE'NİN KENDİ menüsüdür (lafwidget DEĞİL):
 * tr...editor.common.text.fK (MouseListener, editör fi'ye takılı) popup'ı
 * gui.dx.getPopupMenu() ile kurar ve fK.a(MouseEvent) içinde
 * popup.show(fi, x, y) ile gösterir. Menü: Kes/Kopyala/Yapıştır/——/Sil/Tümünü Seç
 * (Windows kökenli Ctrl hızlandırıcılarla). Sınıf/metot OBFUSCATE → metot ADIYLA
 * DEĞİL, JPopupMenu.show ÇAĞRI YERİYLE hedeflenir (sürüm değişimine dayanıklı).
 *
 * Yama: fK içindeki JPopupMenu.show(comp,x,y) çağrısı, önce
 * macospasterich.PlainPaste.addMenuItem(popup, comp) çağrılacak şekilde sarılır
 * ($0=popup, $1=editör). addMenuItem öğeyi "Yapıştır"ın ardına ekler + Ctrl
 * hızlandırıcıları ⌘'ya çevirir (idempotent).
 *
 * ÖN KOŞUL: macospasterich.PlainPaste sınıfı ÖNCE jar'a enjekte edilmiş olmalı
 * (apply_pasterich → apply_plainpaste sırası).
 *
 * Argümanlar: <editor-app.jar> <out-dir>
 */
public class PlainPastePatch {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Kullanım: PlainPastePatch <jar> <out-dir>");
            System.exit(2);
        }
        String jar = args[0];
        File outDir = new File(args[1]);

        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(jar);

        CtClass fk = pool.get("tr.com.havelsan.uyap.system.editor.common.text.fK");

        // İdempotans: herhangi bir metotta PlainPaste.addMenuItem çağrısı varsa atla.
        final boolean[] already = { false };
        for (CtMethod m : fk.getDeclaredMethods()) {
            m.instrument(new ExprEditor() {
                public void edit(MethodCall mc) {
                    if (mc.getClassName().equals("macospasterich.PlainPaste")
                            && mc.getMethodName().equals("addMenuItem")) already[0] = true;
                }
            });
        }
        if (already[0]) {
            System.out.println("[PlainPastePatch] zaten yamalı, atlandı.");
            return;
        }

        // JPopupMenu.show çağrı yerini sar (hangi metotta olursa olsun).
        final boolean[] wrapped = { false };
        for (CtMethod m : fk.getDeclaredMethods()) {
            m.instrument(new ExprEditor() {
                public void edit(MethodCall mc) throws CannotCompileException {
                    if (mc.getClassName().equals("javax.swing.JPopupMenu")
                            && mc.getMethodName().equals("show")) {
                        mc.replace("{ macospasterich.PlainPaste.addMenuItem($0, $1); $proceed($$); }");
                        wrapped[0] = true;
                    }
                }
            });
        }
        if (!wrapped[0]) {
            System.err.println("[PlainPastePatch] fK içinde JPopupMenu.show bulunamadı; UDE sürümü değişmiş olabilir.");
            System.exit(3);
        }

        writeClass(fk, outDir);
        System.out.println("[PlainPastePatch] sağ tık 'Formatsız Yapıştır' öğesi enjekte edildi.");
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
