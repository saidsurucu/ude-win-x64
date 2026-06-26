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

        // Aday siniflari hizli (byte) tarama ile bul: hem JFileChooser hem show* gecen tr/* siniflari
        List<String> targets = new ArrayList<String>();
        ZipFile zf = new ZipFile(jar);
        try {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                String n = ze.getName();
                if (!n.startsWith("tr/") || !n.endsWith(".class")) continue;
                String s = new String(readAll(zf.getInputStream(ze)), StandardCharsets.ISO_8859_1);
                if (s.contains("JFileChooser")
                        && (s.contains("showOpenDialog") || s.contains("showSaveDialog") || s.contains("showDialog"))) {
                    targets.add(n.substring(0, n.length() - 6).replace('/', '.'));
                }
            }
        } finally {
            zf.close();
        }

        int patched = 0;
        for (String cn : targets) {
            CtClass cc = pool.get(cn);
            final boolean[] hit = { false };
            cc.instrument(new ExprEditor() {
                public void edit(MethodCall m) throws javassist.CannotCompileException {
                    // Eslestirme metot ADI + IMZAsina gore (className'e DEGIL): UDE cagriyi
                    // bir com.alee WebFileChooser (JFileChooser alt sinifi) uzerinden yaparsa
                    // getClassName() alt sinif adini doner ve sinif-bazli eslesme kacar.
                    // Bu imzalar JFileChooser'a ozgudur; $0 -> JFileChooser cast tip-guvenli
                    // (WebFileChooser de JFileChooser'a atanabilir).
                    String mn = m.getMethodName();
                    String sig = m.getSignature();
                    if (mn.equals("showOpenDialog") && sig.equals("(Ljava/awt/Component;)I")) {
                        m.replace("$_ = com.udewin.nativedialog.NativeFileDialogBridge.show((javax.swing.JFileChooser)$0, $1, 0);");
                        hit[0] = true;
                    } else if (mn.equals("showSaveDialog") && sig.equals("(Ljava/awt/Component;)I")) {
                        m.replace("$_ = com.udewin.nativedialog.NativeFileDialogBridge.show((javax.swing.JFileChooser)$0, $1, 1);");
                        hit[0] = true;
                    } else if (mn.equals("showDialog") && sig.equals("(Ljava/awt/Component;Ljava/lang/String;)I")) {
                        m.replace("$_ = com.udewin.nativedialog.NativeFileDialogBridge.show((javax.swing.JFileChooser)$0, $1, 2);");
                        hit[0] = true;
                    }
                }
            });
            if (hit[0]) {
                writeClass(cc, outDir);
                patched++;
                System.out.println("[NativeDialogPatch] yamalandi: " + cn);
            }
            cc.detach();
        }
        System.out.println("[NativeDialogPatch] toplam " + patched + " sinif yamalandi.");
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
