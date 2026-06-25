import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.io.File;
import java.io.FileOutputStream;

/**
 * SKIN spike: trimli skin-kurulum cekirdegi.
 *  - setSkin(String) + aF.run() sarmasi -> FlatUdeSkin/DarkSkin + FontPolicy kur.
 *    (Aqua put'lari YOK; Word* widget'lari YOK -> spike kapsami disi.)
 *  - wp.p.E kanvas rengi (teal -> notr/koyu gri).
 *  - Substance EDT denetimleri no-op (PDF export bozulmasin).
 *  - Flamingo kanaryasi: grup baslik bandi/cercevesi kaldirilir.
 * Argumanlar: <editor-app.jar> <out-dir>
 */
public class SkinPatch {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) { System.err.println("Kullanim: SkinPatch <jar> <out-dir>"); System.exit(2); }
        String jar = args[0];
        File outDir = new File(args[1]);
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(jar);

        CtClass slaf = pool.get("org.jvnet.substance.SubstanceLookAndFeel");
        CtMethod setSkinStr = slaf.getMethod("setSkin", "(Ljava/lang/String;)Z");
        setSkinStr.insertBefore(
            "{ macosskin.DarkMode.trace(\"setSkin arg=\" + $1 + \" installing=\" + macosskin.FlatUdeSkin.installing);"
          + "  if (!macosskin.FlatUdeSkin.installing) {"
          + "    macosskin.FlatUdeSkin.installing = true;"
          + "    try {"
          + "      org.jvnet.substance.api.SubstanceSkin __skin = macosskin.DarkMode.isDark()"
          + "        ? (org.jvnet.substance.api.SubstanceSkin) new macosskin.FlatUdeDarkSkin()"
          + "        : (org.jvnet.substance.api.SubstanceSkin) new macosskin.FlatUdeSkin();"
          + "      boolean __ok = org.jvnet.substance.SubstanceLookAndFeel.setSkin(__skin);"
          + "      try {"
          + "        org.jvnet.substance.fonts.FontSet __base ="
          + "          org.jvnet.substance.SubstanceLookAndFeel.getFontPolicy().getFontSet(\"Substance\", null);"
          + "        org.jvnet.substance.SubstanceLookAndFeel.setFontPolicy(new macosskin.FlatFontPolicy(__base));"
          + "      } catch (Throwable __ft) { macosskin.DarkMode.trace(\"font policy: \" + __ft); }"
          + "      macosskin.DarkMode.trace(\"skin kuruldu ok=\" + __ok + \" dark=\" + macosskin.DarkMode.isDark());"
          + "      return __ok;"
          + "    } catch (Throwable __t) { macosskin.DarkMode.trace(\"skin install HATA: \" + __t); }"
          + "    finally { macosskin.FlatUdeSkin.installing = false; }"
          + "  } }");
        writeClass(slaf, outDir);
        System.out.println("[SkinPatch] setSkin(String) sarmasi uygulandi.");

        CtClass aF = pool.get("tr.com.havelsan.uyap.system.editor.common.aF");
        aF.getMethod("run", "()V").insertBefore(
            "{ try {"
          + "    if (!(javax.swing.UIManager.getLookAndFeel() instanceof org.jvnet.substance.SubstanceLookAndFeel)) {"
          + "      macosskin.FlatUdeSkin.installing = true;"
          + "      try {"
          + "        org.jvnet.substance.api.SubstanceSkin __skin = macosskin.DarkMode.isDark()"
          + "          ? (org.jvnet.substance.api.SubstanceSkin) new macosskin.FlatUdeDarkSkin()"
          + "          : (org.jvnet.substance.api.SubstanceSkin) new macosskin.FlatUdeSkin();"
          + "        org.jvnet.substance.SubstanceLookAndFeel.setSkin(__skin);"
          + "        try {"
          + "          org.jvnet.substance.fonts.FontSet __base ="
          + "            org.jvnet.substance.SubstanceLookAndFeel.getFontPolicy().getFontSet(\"Substance\", null);"
          + "          org.jvnet.substance.SubstanceLookAndFeel.setFontPolicy(new macosskin.FlatFontPolicy(__base));"
          + "        } catch (Throwable __ft) { macosskin.DarkMode.trace(\"acilis font: \" + __ft); }"
          + "        macosskin.DarkMode.trace(\"ACILIS skin kuruldu dark=\" + macosskin.DarkMode.isDark());"
          + "      } finally { macosskin.FlatUdeSkin.installing = false; }"
          + "    }"
          + "  } catch (Throwable __t) { macosskin.DarkMode.trace(\"acilis skin HATA: \" + __t); } }");
        writeClass(aF, outDir);
        System.out.println("[SkinPatch] aF.run() acilis skin kurulumu eklendi.");

        try {
            CtClass wpP = pool.get("tr.com.havelsan.uyap.system.swing.wp.p");
            wpP.makeClassInitializer().insertAfter("E = macosskin.DarkMode.canvasColor();");
            writeClass(wpP, outDir);
            System.out.println("[SkinPatch] wp.p.E kanvas rengi yamasi uygulandi.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: kanvas rengi yamasi atlandi: " + t);
        }

        CtClass scu = pool.get("org.jvnet.substance.utils.SubstanceCoreUtilities");
        scu.getMethod("testComponentCreationThreadingViolation", "(Ljava/awt/Component;)V").setBody("{ }");
        scu.getMethod("testComponentStateChangeThreadingViolation", "(Ljava/awt/Component;)V").setBody("{ }");
        writeClass(scu, outDir);
        CtClass lwu = pool.get("org.jvnet.lafwidget.LafWidgetUtilities");
        lwu.getMethod("testComponentStateChangeThreadingViolation", "(Ljava/awt/Component;)V").setBody("{ }");
        writeClass(lwu, outDir);
        System.out.println("[SkinPatch] Substance EDT denetimleri no-op (PDF export).");

        try {
            CtClass bandUi = pool.get("org.pushingpixels.flamingo.internal.ui.ribbon.BasicRibbonBandUI");
            bandUi.getMethod("paintBandTitle", "(Ljava/awt/Graphics;Ljava/awt/Rectangle;Ljava/lang/String;)V").setBody("{ }");
            bandUi.getMethod("paintBandTitleBackground", "(Ljava/awt/Graphics;Ljava/awt/Rectangle;Ljava/lang/String;)V").setBody("{ }");
            writeClass(bandUi, outDir);
            CtClass rb = pool.get("org.pushingpixels.flamingo.internal.ui.ribbon.BasicRibbonBandUI$RoundBorder");
            rb.getMethod("paintBorder", "(Ljava/awt/Component;Ljava/awt/Graphics;IIII)V").setBody("{ }");
            writeClass(rb, outDir);
            System.out.println("[SkinPatch] FLAMINGO KANARYASI: band basligi/cercevesi kaldirildi (yol ACIK).");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] FLAMINGO KANARYASI BASARISIZ: " + t);
        }
    }

    private static void writeClass(CtClass cc, File outDir) throws Exception {
        byte[] code = cc.toBytecode();
        File f = new File(outDir, cc.getName().replace('.', '/') + ".class");
        f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(code); }
    }
}
