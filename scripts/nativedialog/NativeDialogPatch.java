import javassist.ClassPool;
import javassist.CtClass;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * editor-app.jar icindeki UDE (tr/*) siniflarinda JFileChooser.showOpenDialog/
 * showSaveDialog/showDialog cagrilarini NativeFileDialogBridge.show(...) ile
 * degistirir. Cagiran kod degismeden calisir (getSelectedFile vb. ayni).
 *
 * Argumanlar: <editor-app.jar> <bridge-classes-dir> <out-dir>
 */
public class NativeDialogPatch {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Kullanim: NativeDialogPatch <jar> <bridge-dir> <out-dir>");
            System.exit(2);
        }
        String jar = args[0];
        String bridgeDir = args[1];
        File outDir = new File(args[2]);

        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(jar);
        pool.insertClassPath(bridgeDir); // NativeFileDialogBridge cozulebilsin

        // Aday siniflari hizli (byte) tarama ile bul: show* string'i gecen tr/* siniflari.
        // ESKI ON-FILTRE "JFileChooser literal'i ICERMELI" idi; ama UDE Ac/Kaydet'i kendi alt
        // sinifi gui.dp (-> gui.a.p -> javax.swing.JFileChooser) uzerinden cagiriyor. dp uzerinden
        // cagiran siniflar bytecode'unda "JFileChooser" literal'i tasimaz -> atlanirdi (Ac diyalogu
        // Swing kalirdi). Literal sartini kaldirdik; gercek guvenligi asagidaki matcher saglar
        // (ad + imza + declaring-class JFileChooser ALT-TIP kontrolu -> cast tip-guvenli).
        List<String> targets = new ArrayList<String>();
        ZipFile zf = new ZipFile(jar);
        try {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                String n = ze.getName();
                if (!n.startsWith("tr/") || !n.endsWith(".class")) continue;
                String s = new String(readAll(zf.getInputStream(ze)), StandardCharsets.ISO_8859_1);
                if (s.contains("showOpenDialog") || s.contains("showSaveDialog") || s.contains("showDialog")) {
                    targets.add(n.substring(0, n.length() - 6).replace('/', '.'));
                }
            }
        } finally {
            zf.close();
        }

        final CtClass jfc = pool.get("javax.swing.JFileChooser");
        int patched = 0;
        int skipped = 0;
        for (String cn : targets) {
            try {
                CtClass cc = pool.get(cn);
                final boolean[] hit = { false };
                cc.instrument(new ExprEditor() {
                    public void edit(MethodCall m) throws javassist.CannotCompileException {
                        // Eslestirme metot ADI + IMZAsina + DECLARING-CLASS alt-tip kontrolune gore.
                        // Imzalar JFileChooser.show*'a ozgu; dahasi cagrinin sahibi gercekten
                        // JFileChooser alt sinifi mi diye dogruluyoruz -> $0 -> JFileChooser cast
                        // tip-guvenli (gui.a.p extends javax.swing.JFileChooser; WebFileChooser da
                        // alt sinif). Alt-tip degilse atla (runtime ClassCast'i onler).
                        String mn = m.getMethodName();
                        String sig = m.getSignature();
                        int mode;
                        if (mn.equals("showOpenDialog") && sig.equals("(Ljava/awt/Component;)I")) mode = 0;
                        else if (mn.equals("showSaveDialog") && sig.equals("(Ljava/awt/Component;)I")) mode = 1;
                        else if (mn.equals("showDialog") && sig.equals("(Ljava/awt/Component;Ljava/lang/String;)I")) mode = 2;
                        else return;
                        try {
                            if (!m.getMethod().getDeclaringClass().subtypeOf(jfc)) return;
                        } catch (Throwable t) {
                            return; // metot/sinif cozulemedi -> guvenli tarafta kal
                        }
                        m.replace("$_ = com.udewin.nativedialog.NativeFileDialogBridge.show((javax.swing.JFileChooser)$0, $1, " + mode + ");");
                        hit[0] = true;
                    }
                });
                if (hit[0]) {
                    writeClass(cc, outDir);
                    patched++;
                    System.out.println("[NativeDialogPatch] yamalandi: " + cn);
                }
                cc.detach();
            } catch (Throwable t) {
                skipped++;
                System.out.println("[NativeDialogPatch] atlandi (hata): " + cn + " -> " + t);
            }
        }
        System.out.println("[NativeDialogPatch] toplam " + patched + " sinif yamalandi (" + skipped + " hata ile atlandi).");
        if (patched == 0) throw new RuntimeException("hicbir JFileChooser cagrisi yamalanmadi");
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] t = new byte[8192];
        int r;
        while ((r = in.read(t)) > 0) b.write(t, 0, r);
        in.close();
        return b.toByteArray();
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
