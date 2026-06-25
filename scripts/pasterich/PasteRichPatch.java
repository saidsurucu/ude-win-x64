import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Harici stilli yapıştırma macOS özelliği (build-zamanı bytecode yaması).
 *
 * hj.a(Transferable) ("paste from web") metodu, panodaki HTML içinde UDE'nin
 * kendi base64 işaretini (uyap-web-editor-data) arar; bulamazsa false döner ve
 * paste() düz-metin dalına düşer → harici (Word/tarayıcı/PDF) içeriğinde biçim
 * kaybı.
 *
 * Pages/TextEdit/Mail panoya HTML KOYMAZ (yalnız RTF/RTFD). Bu kaynaklar için
 * dal, RTF flavor'ını yakalayıp macOS `textutil` ile HTML'e çevirir (üretilen HTML
 * zaten &lt;style&gt; class kuralları biçiminde — HtmlToUde çözer), sonra aynı YEREL
 * ekleme yolunu kullanır. paste() EditorDataFlavor yoksa a(Transferable)'ı koşulsuz
 * çağırdığından (bytecode'dan doğrulandı) RTF-only pano da bu kancaya ulaşır.
 *
 * Bu yama, metodun BAŞINA bir dal ekler: işaret YOK ama allHtmlFlavor VARSA,
 * pano HTML'i macospasterich.RichPaste ile (paketli udf-cli ikilisi alt süreci)
 * UDE'nin kendi .udf (UDF zip) formatına çevrilir; başarıda UYAP-web yolunun
 * AYNISI uygulanır (yeni WPDocumentPanel → setCaret → a(InputStream) UDF okuyucu
 * → select-all → copy → this.paste()) ve metot true döner. Böylece UDE'nin tüm
 * liste/tablo/paragraf/karakter işleme makinesi yeniden kullanılır.
 *
 * Başarısızlıkta (ikili yok / dönüşüm / besleme hatası) dal sessizce geçilir ve
 * metodun orijinal gövdesi (UDE-içi/UYAP-web/false) aynen çalışır → düz-metin
 * fallback. UDE-içi ve UYAP-web yapıştırma yolları değişmez (tam geriye uyum).
 *
 * ÖN KOŞUL: macospasterich.RichPaste sınıfı bu patcher çalışmadan ÖNCE jar'a
 * enjekte edilmiş olmalı (apply_pasterich sırası; Javassist insertBefore
 * derlemesi RichPaste'i jar classpath'inden çözer).
 *
 * Argümanlar: <editor-app.jar> <out-dir>
 */
public class PasteRichPatch {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Kullanım: PasteRichPatch <jar> <out-dir>");
            System.exit(2);
        }
        String jar = args[0];
        File outDir = new File(args[1]);

        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(jar);

        CtClass hj = pool.get("tr.com.havelsan.uyap.system.editor.common.text.hj");

        CtMethod a = hj.getDeclaredMethod("a",
                new CtClass[]{ pool.get("java.awt.datatransfer.Transferable") });

        // İşaret yok + allHtmlFlavor varsa: pano HTML'i RichPaste.insertInto ile
        // canlı editöre YEREL eklenir (DocumentEx.a ile gerçek tablo + StyleConstants
        // paragraf/karakter). copy→paste YOK (tabloları düzleştiriyordu); clipboard/
        // özyineleme/NPE riski yok. Başarıda return true (düz-metin fallback çalışmaz).
        String src =
              "{"
            + "  try {"
            + "    java.awt.datatransfer.Transferable __t = $1;"
            + "    if (__t != null && __t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.allHtmlFlavor)) {"
            + "      Object __o = __t.getTransferData(java.awt.datatransfer.DataFlavor.allHtmlFlavor);"
            + "      if (__o instanceof java.lang.String) {"
            + "        java.lang.String __h = (java.lang.String) __o;"
            + "        if (__h.indexOf(\"uyap-web-editor-data\") < 0) {"
            + "          if (macospasterich.RichPaste.insertInto(this, __h)) return true;"
            + "        }"
            + "      }"
            + "    } else if (__t != null) {"
            + "      if (macospasterich.RichPaste.insertRtf(this, __t)) return true;"
            + "    }"
            + "  } catch (java.lang.Throwable __e) { macospasterich.RichPaste.logExternal(__e); }"
            + "}";
        a.insertBefore(src);

        writeClass(hj, outDir);
        System.out.println("[PasteRichPatch] hj.a(Transferable) harici-HTML dalı enjekte edildi.");
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
