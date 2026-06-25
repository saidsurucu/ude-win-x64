import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.io.File;
import java.io.FileOutputStream;

/**
 * UDE modern-ikon (ICONS=1) build-zamanı bytecode yamaları:
 *   1) tr...common.Utils.b(String): yüklenen .png için @2x eşi varsa
 *      BaseMultiResolutionImage'a sarar (Retina keskinlik). KRİTİK.
 *   2) com.alee.global.StyleConstants.disabledIconsTransparency = 0.38f
 *      (WebLaF disabled ikon saydamlığı; orijinal 0.7 monokromda yetersiz). best-effort.
 *   4) org...flamingo...ImageWrapperIcon.paintIcon: kaynak Image varsa onu
 *      hedef boyuta bicubic çizen erken-dönüş. Orijinal yol kaynağı 1x
 *      BufferedImage'e rasterleyip cache'liyordu -> BaseMultiResolutionImage
 *      kayboluyor, Retina'da tüm Flamingo ikonları (hızlı erişim dahil)
 *      bulanık/tırtıklı. Doğrudan drawImage AWT'nin @2x varyant seçimini
 *      korur. best-effort.
 *   3) org...flamingo...FilteredResizableIcon.paintIcon: gövde tamamen
 *      değiştirilir -> delegate doğrudan AlphaComposite 0.38 ile çizilir.
 *      Orijinal yol ikonun 1x rasterini ColorConvertOp(CS_GRAY) ile bozuyordu
 *      (Retina'da tırtık + ince Fluent çizgilerinde kir); delegate çizimi
 *      keskinliği ve soluk renkleri korur. Bu sınıf bu jar'da yalnızca
 *      disabled ikonlarda kullanılır. best-effort.
 *
 * Argümanlar: <editor-app.jar> <out-dir>
 */
public class IconLoaderPatch {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Kullanım: IconLoaderPatch <jar> <out-dir>");
            System.exit(2);
        }
        String jar = args[0];
        File outDir = new File(args[1]);

        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(jar);

        // --- 1) Utils.b multi-resolution (KRİTİK: hata olursa fırlat) ---
        CtClass utils = pool.get("tr.com.havelsan.uyap.system.editor.common.Utils");
        CtMethod b = utils.getMethod("b", "(Ljava/lang/String;)Ljavax/swing/ImageIcon;");
        b.insertAfter(
            "{ if ($_ != null && $1 != null && $1.endsWith(\".png\")"
          + "     && !($_.getImage() instanceof java.awt.image.BaseMultiResolutionImage)) {"
          + "   String __h = $1.substring(0, $1.length() - 4) + \"@2x.png\";"
          + "   java.net.URL __u = tr.com.havelsan.uyap.system.editor.common.Utils.class.getResource(__h);"
          + "   if (__u != null) {"
          + "     javax.swing.ImageIcon __hi = new javax.swing.ImageIcon(__u);"
          + "     java.awt.Image __m = new java.awt.image.BaseMultiResolutionImage("
          + "         new java.awt.Image[]{ $_.getImage(), __hi.getImage() });"
          + "     $_ = new javax.swing.ImageIcon(__m);"
          + "   }"
          + " } }");
        // --- 1b) Utils.a(ImageIcon,int,int): getScaledInstance multi-res'i öldürüyor
        //     (1x bitmap -> Retina'da bloklu; hızlı erişim bu yoldan ölçekleniyor).
        //     Kaynak BaseMultiResolutionImage ise her varyant ayrı ölçeklenir. ---
        CtMethod scale = utils.getMethod("a",
            "(Ljavax/swing/ImageIcon;II)Ljavax/swing/ImageIcon;");
        scale.insertBefore(
            "{ if ($1 != null && $2 > 0 && $3 > 0"
          + "     && $1.getImage() instanceof java.awt.image.BaseMultiResolutionImage) {"
          + "   java.awt.image.BaseMultiResolutionImage __mr ="
          + "       (java.awt.image.BaseMultiResolutionImage) $1.getImage();"
          + "   java.awt.Image __lo = __mr.getResolutionVariant((double)$2, (double)$3);"
          + "   java.awt.Image __hi = __mr.getResolutionVariant((double)($2*2), (double)($3*2));"
          + "   java.awt.Image __slo = __lo.getScaledInstance($2, $3, java.awt.Image.SCALE_SMOOTH);"
          + "   java.awt.Image __shi = __hi.getScaledInstance($2*2, $3*2, java.awt.Image.SCALE_SMOOTH);"
          + "   return new javax.swing.ImageIcon(new java.awt.image.BaseMultiResolutionImage("
          + "       new java.awt.Image[]{ __slo, __shi }));"
          + " } }");
        writeClass(utils, outDir);
        System.out.println("[IconLoaderPatch] Utils.b multi-res + a(ImageIcon,int,int) yamaları yazıldı.");

        // --- 2) WebLaF disabled saydamlık 0.7 -> 0.38 (best-effort) ---
        try {
            CtClass sc = pool.get("com.alee.global.StyleConstants");
            sc.makeClassInitializer().insertAfter("disabledIconsTransparency = 0.38f;");
            writeClass(sc, outDir);
            System.out.println("[IconLoaderPatch] WebLaF disabled transparency yaması uygulandı (0.38).");
        } catch (Throwable t) {
            System.out.println("[IconLoaderPatch] UYARI: WebLaF disabled transparency yaması atlandı: " + t);
        }

        // --- 4) Flamingo ImageWrapperIcon: multi-res korunarak çiz (best-effort) ---
        try {
            CtClass iwi = pool.get("org.pushingpixels.flamingo.api.common.icon.ImageWrapperIcon");
            CtMethod paint = iwi.getMethod("paintIcon", "(Ljava/awt/Component;Ljava/awt/Graphics;II)V");
            paint.insertBefore(
                "{ if (this.image != null && this.width > 0 && this.height > 0) {"
              + "    java.awt.Graphics2D g2 = (java.awt.Graphics2D) $2.create();"
              + "    g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,"
              + "        java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);"
              + "    g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,"
              + "        java.awt.RenderingHints.VALUE_RENDER_QUALITY);"
              + "    g2.drawImage(this.image, $3, $4, this.width, this.height,"
              + "        (java.awt.image.ImageObserver) null);"
              + "    g2.dispose();"
              + "    return;"
              + "  } }");
            writeClass(iwi, outDir);
            System.out.println("[IconLoaderPatch] Flamingo ImageWrapperIcon multi-res yaması uygulandı.");
        } catch (Throwable t) {
            System.out.println("[IconLoaderPatch] UYARI: ImageWrapperIcon yaması atlandı: " + t);
        }

        // --- 3) Flamingo disabled ikon: delegate + alpha 0.38 (best-effort) ---
        try {
            CtClass fri = pool.get("org.pushingpixels.flamingo.api.common.icon.FilteredResizableIcon");
            CtMethod paint = fri.getMethod("paintIcon", "(Ljava/awt/Component;Ljava/awt/Graphics;II)V");
            paint.setBody(
                "{ java.awt.Graphics2D g2 = (java.awt.Graphics2D) $2.create();"
              + "  g2.setComposite(java.awt.AlphaComposite.getInstance("
              + "      java.awt.AlphaComposite.SRC_OVER, 0.38f));"
              + "  this.delegate.paintIcon($1, g2, $3, $4);"
              + "  g2.dispose(); }");
            writeClass(fri, outDir);
            System.out.println("[IconLoaderPatch] Flamingo disabled: delegate+alpha 0.38 yaması uygulandı.");
        } catch (Throwable t) {
            System.out.println("[IconLoaderPatch] UYARI: Flamingo disabled yaması atlandı: " + t);
        }
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
