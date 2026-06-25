import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.expr.Cast;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.io.File;
import java.io.FileOutputStream;

/**
 * UDE panodan imaj yapıştırma macOS düzeltmesi (build-zamanı bytecode yaması).
 *
 * hj.paste() imaj dalı, getTransferData(imageFlavor) dönüşünü
 * "instanceof BufferedImage" guard'ıyla sınar; macOS JDK'sı burada
 * MultiResolutionCachedImage döndürdüğünden guard false kalır ve dal sessizce
 * atlanır (istisna yok). Yama iki katmanlı:
 *   1) KRİTİK: paste() içindeki her getTransferData(DataFlavor):Object çağrısı
 *      sarılır — dönüş Image olup BufferedImage DEĞİLSE Conv.toBuffered ile
 *      dönüştürülür. Böylece instanceof guard'ı geçer, dalın kalanı (sayfaya
 *      sığdırma + caret'e ekleme) orijinal haliyle çalışır. Diğer flavor'ların
 *      dönüşleri (String, List, EditorDataFlavor) Image olmadığından koşul hiç
 *      tetiklenmez; Windows/Linux'ta dönüş zaten BufferedImage'dır.
 *   2) Savunma: hedefi BufferedImage olan cast'ler de Conv.toBuffered'a çevrilir
 *      (1. katman sonrası fiilen passthrough).
 *   3) KALİTE: paste() içindeki aa.a(BufferedImage,int,int) sayfaya-sığdırma
 *      çağrısı bitmap'i hedef boyuta YIKICI küçültüyordu (ör. Retina ekran
 *      görüntüsü 510x331'e iner, Retina'da 2x büyütülünce aşırı bulanık).
 *      Passthrough yapılır: görünen boyut (float w,h) aynı kalır, bitmap tam
 *      çözünürlükte gömülür (IMGFULL utils.h.a düzeltmesinin paste-yolu ikizi).
 *   4) KALİTE (best-effort): görünüm wp.b.at'ın drawImage çağrısına BICUBIC
 *      interpolation hint — tam çözünürlüklü bitmap ekranda küçültülürken
 *      keskin çizilir. IMGFULL=1 zaten uyguladıysa (RenderingHints referansı
 *      varsa) atlanır.
 * Kontrol akışı (metin önceliği, görünen boyut hesabı, dosya dalı) değişmez.
 *
 * ÖN KOŞUL: macospasteimage/Conv.class bu patcher çalışmadan ÖNCE jar'a
 * enjekte edilmiş olmalı (apply_pasteimage sırası; Javassist replace
 * derlemesi Conv'u jar classpath'inden çözer).
 *
 * Argümanlar: <editor-app.jar> <out-dir>
 */
public class PasteImagePatch {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Kullanım: PasteImagePatch <jar> <out-dir>");
            System.exit(2);
        }
        String jar = args[0];
        File outDir = new File(args[1]);

        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(jar);

        CtClass hj = pool.get("tr.com.havelsan.uyap.system.editor.common.text.hj");
        CtMethod paste = hj.getDeclaredMethod("paste", new CtClass[0]);

        final int[] wrapped = { 0 };
        final int[] casts = { 0 };
        final int[] fitSkipped = { 0 };
        paste.instrument(new ExprEditor() {
            public void edit(MethodCall m) throws CannotCompileException {
                if ("getTransferData".equals(m.getMethodName())
                        && "(Ljava/awt/datatransfer/DataFlavor;)Ljava/lang/Object;".equals(m.getSignature())) {
                    m.replace("{ $_ = $proceed($$);"
                        + " if ($_ instanceof java.awt.Image && !($_ instanceof java.awt.image.BufferedImage)) {"
                        + " $_ = macospasteimage.Conv.toBuffered($_); } }");
                    wrapped[0]++;
                } else if ("a".equals(m.getMethodName())
                        && "tr.com.havelsan.uyap.system.editor.common.aa".equals(m.getClassName())
                        && "(Ljava/awt/image/BufferedImage;II)Ljava/awt/image/BufferedImage;".equals(m.getSignature())) {
                    // Yıkıcı sayfaya-sığdırma küçültmesini atla: bitmap tam çözünürlükte
                    // gömülür, görünen boyut (sonraki a(img,w,h) float'ları) değişmez.
                    m.replace("{ $_ = $1; }");
                    fitSkipped[0]++;
                }
            }
            public void edit(Cast c) throws CannotCompileException {
                try {
                    if ("java.awt.image.BufferedImage".equals(c.getType().getName())) {
                        c.replace("{ $_ = macospasteimage.Conv.toBuffered($1); }");
                        casts[0]++;
                    }
                } catch (NotFoundException e) {
                    // cast hedef tipi havuzda çözülemedi -> BufferedImage değil, dokunma
                }
            }
        });

        if (wrapped[0] == 0) {
            System.err.println("[PasteImagePatch] HATA: hj.paste() içinde getTransferData çağrısı bulunamadı (UDE sürümü değişmiş olabilir).");
            System.exit(1);
        }
        writeClass(hj, outDir);
        System.out.println("[PasteImagePatch] hj.paste() yamandı: " + wrapped[0]
            + " getTransferData sarması + " + casts[0] + " BufferedImage cast dönüşümü + "
            + fitSkipped[0] + " yıkıcı sığdırma passthrough.");
        if (fitSkipped[0] == 0) {
            System.out.println("[PasteImagePatch] UYARI: aa.a sığdırma çağrısı bulunamadı; imaj küçültülmüş gömülür (bulanıklık).");
        }

        // --- 4) at.drawImage BICUBIC hint (best-effort; IMGFULL uyguladıysa atla) ---
        try {
            CtClass at = pool.get("tr.com.havelsan.uyap.system.swing.wp.b.at");
            if (at.getRefClasses().contains("java.awt.RenderingHints")) {
                System.out.println("[PasteImagePatch] at zaten interpolation hint içeriyor (IMGFULL?); bicubic atlandı.");
            } else {
                final boolean[] hinted = { false };
                at.instrument(new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getMethodName().equals("drawImage")
                                && m.getSignature().equals("(Ljava/awt/Image;IIIILjava/awt/image/ImageObserver;)Z")) {
                            m.replace(
                                "{ if ($0 instanceof java.awt.Graphics2D) {"
                              + "    java.awt.Graphics2D g2 = (java.awt.Graphics2D)$0;"
                              + "    Object __old = g2.getRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION);"
                              + "    g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,"
                              + "        java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);"
                              + "    $_ = $proceed($$);"
                              + "    if (__old != null) g2.setRenderingHint("
                              + "        java.awt.RenderingHints.KEY_INTERPOLATION, __old);"
                              + "  } else { $_ = $proceed($$); } }");
                            hinted[0] = true;
                        }
                    }
                });
                if (hinted[0]) {
                    writeClass(at, outDir);
                    System.out.println("[PasteImagePatch] at.drawImage BICUBIC hint yaması uygulandı.");
                } else {
                    System.out.println("[PasteImagePatch] UYARI: at içinde beklenen drawImage çağrısı bulunamadı; bicubic atlandı.");
                }
            }
        } catch (Throwable t) {
            System.out.println("[PasteImagePatch] UYARI: at bicubic yaması atlandı: " + t);
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
