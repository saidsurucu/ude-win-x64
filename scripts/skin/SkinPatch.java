import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

import java.io.File;
import java.io.FileOutputStream;

/**
 * UDE modern skin (SKIN=1) build-zamanı bytecode yamaları.
 * Bu aşamada: SubstanceLookAndFeel.setSkin(String) çağrıldığında
 * bizim FlatUdeSkin'i kur (UDE hangi skin adını verirse versin).
 *
 * Argümanlar: <editor-app.jar> <out-dir>
 */
public class SkinPatch {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Kullanım: SkinPatch <jar> <out-dir>");
            System.exit(2);
        }
        String jar = args[0];
        File outDir = new File(args[1]);

        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(jar);

        // setSkin(String) -> bizim skin'i kur (yeniden-giriş korumalı)
        CtClass slaf = pool.get("org.jvnet.substance.SubstanceLookAndFeel");
        CtMethod setSkinStr = slaf.getMethod("setSkin", "(Ljava/lang/String;)Z");
        setSkinStr.insertBefore(
            "{ macosskin.DarkMode.trace(\"setSkin cagrildi arg=\" + $1 + \" installing=\" + macosskin.FlatUdeSkin.installing);"
          + "  if (!macosskin.FlatUdeSkin.installing) {"
          + "    macosskin.FlatUdeSkin.installing = true;"
          + "    try {"
          + "      org.jvnet.substance.api.SubstanceSkin __skin ="
          + "        macosskin.DarkMode.isDark()"
          + "          ? (org.jvnet.substance.api.SubstanceSkin) new macosskin.FlatUdeDarkSkin()"
          + "          : (org.jvnet.substance.api.SubstanceSkin) new macosskin.FlatUdeSkin();"
          + "      boolean __ok = org.jvnet.substance.SubstanceLookAndFeel.setSkin(__skin);"
          + "      macosskin.DarkMode.trace(\"skin kuruldu ok=\" + __ok + \" dark=\" + macosskin.DarkMode.isDark());"
          + "      try {"
          + "        org.jvnet.substance.fonts.FontSet __base ="
          + "          org.jvnet.substance.SubstanceLookAndFeel.getFontPolicy().getFontSet(\"Substance\", null);"
          + "        org.jvnet.substance.SubstanceLookAndFeel.setFontPolicy(new macosskin.FlatFontPolicy(__base));"
          + "      } catch (Throwable __ft) { System.err.println(\"[FlatUdeSkin] font policy failed: \" + __ft); }"
          + "      return __ok;"
          + "    } catch (Throwable __t) {"
          + "      macosskin.DarkMode.trace(\"skin install HATA: \" + __t);"
          + "      System.err.println(\"[FlatUdeSkin] skin install failed, kept original: \" + __t);"
          + "    } finally { macosskin.FlatUdeSkin.installing = false; }"
          + "  } }");
        writeClass(slaf, outDir);
        System.out.println("[SkinPatch] setSkin(String) -> FlatUdeSkin sarması uygulandı.");

        // Açılışta skin GARANTİLİ kurulur. UDE Substance'ı yalnızca
        // initValues/menuTheme tercihi doluysa kurar (au -> an.a(String) -> setSkin);
        // tema ayarındaki "standart" seçeneği bu tercihi SİLER ve uygulama kalıcı
        // olarak Aqua'ya düşer (bizim skin combo'da eşleşmediği için bu kolay
        // tetiklenir). Bu yüzden tercihten bağımsız olarak, UI başlangıcında
        // (WPAppManager.main -> invokeLater(new aF(args)), EDT) skin kurulur.
        // setSkin(SubstanceSkin) LAF Substance değilse UIManager.setLookAndFeel'i
        // kendisi çağırır (bytecode'dan doğrulandı).
        CtClass aF = pool.get("tr.com.havelsan.uyap.system.editor.common.aF");
        aF.getMethod("run", "()V").insertBefore(
            "{ try {"
          + "    if (!(javax.swing.UIManager.getLookAndFeel() instanceof org.jvnet.substance.SubstanceLookAndFeel)) {"
          + "      macosskin.FlatUdeSkin.installing = true;"
          + "      try {"
          + "        org.jvnet.substance.api.SubstanceSkin __skin ="
          + "          macosskin.DarkMode.isDark()"
          + "            ? (org.jvnet.substance.api.SubstanceSkin) new macosskin.FlatUdeDarkSkin()"
          + "            : (org.jvnet.substance.api.SubstanceSkin) new macosskin.FlatUdeSkin();"
          + "        org.jvnet.substance.SubstanceLookAndFeel.setSkin(__skin);"
          + "        try {"
          + "          org.jvnet.substance.fonts.FontSet __base ="
          + "            org.jvnet.substance.SubstanceLookAndFeel.getFontPolicy().getFontSet(\"Substance\", null);"
          + "          org.jvnet.substance.SubstanceLookAndFeel.setFontPolicy(new macosskin.FlatFontPolicy(__base));"
          + "        } catch (Throwable __ft) { macosskin.DarkMode.trace(\"acilis font policy: \" + __ft); }"
          + "        macosskin.DarkMode.trace(\"acilis skin kuruldu dark=\" + macosskin.DarkMode.isDark());"
          + "      } finally { macosskin.FlatUdeSkin.installing = false; }"
          + "    }"
          + "  } catch (Throwable __t) { macosskin.DarkMode.trace(\"acilis skin HATA: \" + __t); } }");
        writeClass(aF, outDir);
        System.out.println("[SkinPatch] aF.run() acilis skin kurulumu eklendi.");

        // Editor masaüstü arka planı: wp.p.E = teal(44,153,174) static sabiti.
        // clinit sonrası DarkMode kanvas rengiyle override (açık: nötr gri,
        // koyu: koyu gri; alan public static, final değil).
        try {
            CtClass wpP = pool.get("tr.com.havelsan.uyap.system.swing.wp.p");
            wpP.makeClassInitializer().insertAfter(
                "E = macosskin.DarkMode.canvasColor();");
            writeClass(wpP, outDir);
            System.out.println("[SkinPatch] wp.p.E teal -> nötr gri yaması uygulandı.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: teal yaması atlandı: " + t);
        }

        // Tercihlerden (tercihler.xml) yüklenen eski teal degerini de nötrle:
        // an sinifi pref'i okuyup E alanina putstatic yapar ve clinit varsayilanini ezer.
        // Yalniz eski teal (-13854290) ve bizim acik-gri kalici degerimiz (-1775637)
        // remap edilir; kullanicinin sectigi baska renkler dokunulmaz.
        try {
            CtClass an = pool.get("tr.com.havelsan.uyap.system.editor.common.an");
            an.instrument(new ExprEditor() {
                public void edit(FieldAccess f) throws javassist.CannotCompileException {
                    try {
                        if (f.isWriter() && "E".equals(f.getFieldName())
                                && "tr.com.havelsan.uyap.system.swing.wp.p".equals(
                                    f.getField().getDeclaringClass().getName())) {
                            f.replace(
                                "{ java.awt.Color __v = $1;"
                              + "  if (__v != null && (__v.getRGB() == -13854290 || __v.getRGB() == -1775637"
                              + "      || __v.getRGB() == -14803426 || __v.getRGB() == -13224394)) {"
                              + "    __v = macosskin.DarkMode.canvasColor();"
                              + "  }"
                              + "  $proceed(__v); }");
                        }
                    } catch (javassist.NotFoundException __nf) {
                    }
                }
            });
            writeClass(an, outDir);
            System.out.println("[SkinPatch] an pref-load teal koruması uygulandı.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: pref-load teal koruması atlandı: " + t);
        }

        // Cetvel (gui.eV) Word koyu paleti: rakamlar a()=siyah, tikler b()/c(),
        // işaretçiler d/e statikleri. Koyu modda Word'den ölçülen tonlara
        // kısa devre; açık modda orijinal gövde/alanlar aynen çalışır.
        // Zemin (beyaz setBackground) MacLook agent'ında düzeltilir.
        try {
            CtClass ruler = pool.get("tr.com.havelsan.uyap.system.editor.common.gui.eV");
            ruler.getMethod("a", "()Ljava/awt/Color;").insertBefore(
                "{ if (macosskin.DarkMode.isDark()) return new java.awt.Color(226, 226, 226); }");
            ruler.getMethod("b", "()Ljava/awt/Color;").insertBefore(
                "{ if (macosskin.DarkMode.isDark()) return new java.awt.Color(152, 152, 158, 250); }");
            ruler.getMethod("c", "()Ljava/awt/Color;").insertBefore(
                "{ if (macosskin.DarkMode.isDark()) return new java.awt.Color(152, 152, 158, 150); }");
            javassist.CtConstructor[] rctors = ruler.getDeclaredConstructors();
            for (int rci = 0; rci < rctors.length; rci++) {
                rctors[rci].insertAfter(
                    "{ this.setColor_border(macosskin.DarkMode.canvasColor());"
                  + "  this.setBackground(macosskin.DarkMode.canvasColor());"
                  + "  this.setOpaque(true); }");
            }
            ruler.instrument(new ExprEditor() {
                public void edit(FieldAccess f) throws javassist.CannotCompileException {
                    try {
                        if (f.isReader() && "Ljava/awt/Color;".equals(f.getSignature())
                                && ("d".equals(f.getFieldName()) || "e".equals(f.getFieldName()))
                                && "tr.com.havelsan.uyap.system.editor.common.gui.eV".equals(
                                    f.getField().getDeclaringClass().getName())) {
                            f.replace(
                                "{ $_ = macosskin.DarkMode.isDark()"
                              + "    ? new java.awt.Color(220, 221, 221)"
                              + "    : ($r) $proceed(); }");
                        }
                        if (f.isReader() && "LIGHT_GRAY".equals(f.getFieldName())
                                && "java.awt.Color".equals(
                                    f.getField().getDeclaringClass().getName())) {
                            f.replace(
                                "{ $_ = macosskin.DarkMode.isDark()"
                              + "    ? new java.awt.Color(38, 38, 38)"
                              + "    : new java.awt.Color(223, 223, 223); }");
                        }
                    } catch (javassist.NotFoundException __nf) {
                    }
                }
            });
            writeClass(ruler, outDir);
            System.out.println("[SkinPatch] cetvel renkleri koyu moda uyarlandı.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: cetvel yaması atlandı: " + t);
        }

        // Flamingo şerit sadeleştirme: grup başlık bandı ve çerçevesi kaldırılır
        // ("Pano/Font/Paragraf" kutuları 2007 Office izi). Flamingo obfuscate
        // değil; metot imzaları javap ile doğrulandı.
        try {
            CtClass bandUi = pool.get(
                "org.pushingpixels.flamingo.internal.ui.ribbon.BasicRibbonBandUI");
            bandUi.getMethod("paintBandTitle",
                "(Ljava/awt/Graphics;Ljava/awt/Rectangle;Ljava/lang/String;)V")
                .setBody("{ }");
            bandUi.getMethod("paintBandTitleBackground",
                "(Ljava/awt/Graphics;Ljava/awt/Rectangle;Ljava/lang/String;)V")
                .setBody("{ }");
            writeClass(bandUi, outDir);
            System.out.println("[SkinPatch] Flamingo band başlığı/çerçevesi kaldırıldı.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: Flamingo band yaması atlandı: " + t);
        }

        // Flamingo kontur rengi: getBorderColor() UIManager
        // TextField.inactiveForeground okur; koyu şemada bu AÇIK renktir ->
        // aktif sekme bembeyaz çerçeveli görünür. Tema-duyarlı grafit döndür.
        try {
            CtClass fu = pool.get(
                "org.pushingpixels.flamingo.internal.utils.FlamingoUtilities");
            fu.getMethod("getBorderColor", "()Ljava/awt/Color;")
                .setBody(
                    "{ return macosskin.DarkMode.isDark()"
                  + "    ? new java.awt.Color(74, 74, 74)"
                  + "    : new java.awt.Color(198, 198, 198); }");
            writeClass(fu, outDir);
            System.out.println("[SkinPatch] Flamingo kontur rengi grafite çekildi.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: kontur rengi yaması atlandı: " + t);
        }

        // Grup kutu KENARLIĞI da kalksın: RoundBorder.paintBorder no-op
        // (insets korunur, yalnız çizim gider; gruplar boşlukla ayrılır).
        try {
            CtClass rb = pool.get(
                "org.pushingpixels.flamingo.internal.ui.ribbon.BasicRibbonBandUI$RoundBorder");
            rb.getMethod("paintBorder",
                "(Ljava/awt/Component;Ljava/awt/Graphics;IIII)V")
                .setBody("{ }");
            writeClass(rb, outDir);
            System.out.println("[SkinPatch] Flamingo grup kenarlığı kaldırıldı.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: grup kenarlık yaması atlandı: " + t);
        }

        // Şerit komut butonları Word tarzı: seçili/hover/basılı durumda yuvarlak
        // köşeli düz dolgu (Word ölçümü: seçili #474747, hover #3D3D3D koyu modda).
        // Normal durumda arka plan YOK. Orb ve sekme butonları kendi override'larını
        // koruduğundan etkilenmez. MENÜ butonları (JCommandMenuButton/Toggle) koyu
        // modda Word menü vurgusu: tam satır köşesiz mavi dolgu (#3B69DA ölçüm).
        try {
            CtClass cmdUi = pool.get(
                "org.pushingpixels.flamingo.internal.ui.common.BasicCommandButtonUI");
            cmdUi.getMethod("paintButtonBackground",
                "(Ljava/awt/Graphics;Ljava/awt/Rectangle;)V")
                .setBody(
                    "{ javax.swing.ButtonModel __m = this.commandButton.getActionModel();"
                  + "  boolean __press = __m.isArmed() || __m.isPressed();"
                  + "  boolean __sel = __m.isSelected();"
                  + "  boolean __roll = __m.isRollover();"
                  + "  if (this.commandButton instanceof"
                  + "      org.pushingpixels.flamingo.api.common.JCommandButton) {"
                  + "    org.pushingpixels.flamingo.api.common.model.PopupButtonModel __pm ="
                  + "        ((org.pushingpixels.flamingo.api.common.JCommandButton)"
                  + "            this.commandButton).getPopupModel();"
                  + "    if (__pm != null) {"
                  + "      if (__pm.isPopupShowing()) __roll = true;"
                  + "      if (__pm.isRollover()) __roll = true;"
                  + "      if (__pm.isArmed() || __pm.isPressed()) __press = true;"
                  + "    }"
                  + "  }"
                  + "  if (__press || __sel || __roll) {"
                  + "    java.awt.Graphics2D __g = (java.awt.Graphics2D) $1.create();"
                  + "    __g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,"
                  + "        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);"
                  + "    boolean __dark = macosskin.DarkMode.isDark();"
                  + "    boolean __menu = (this.commandButton instanceof"
                  + "        org.pushingpixels.flamingo.api.common.JCommandMenuButton)"
                  + "      || (this.commandButton instanceof"
                  + "        org.pushingpixels.flamingo.api.common.JCommandToggleMenuButton);"
                  + "    if (__menu && __dark) {"
                  + "      __g.setColor(new java.awt.Color(59, 105, 218));"
                  + "      __g.fillRect($2.x, $2.y, $2.width, $2.height);"
                  + "    } else {"
                  + "      java.awt.Color __c = __press"
                  + "          ? (__dark ? new java.awt.Color(81, 81, 81) : new java.awt.Color(196, 196, 196))"
                  + "          : (__sel"
                  + "              ? (__dark ? new java.awt.Color(71, 71, 71) : new java.awt.Color(208, 208, 208))"
                  + "              : (__dark ? new java.awt.Color(61, 61, 61) : new java.awt.Color(224, 224, 224)));"
                  + "      __g.setColor(__c);"
                  + "      __g.fillRoundRect($2.x, $2.y, $2.width, $2.height, 8, 8);"
                  + "    }"
                  + "    __g.dispose();"
                  + "  } }");
            cmdUi.getMethod("paintButtonBackground",
                "(Ljava/awt/Graphics;Ljava/awt/Rectangle;[Ljavax/swing/ButtonModel;)V")
                .setBody(
                    "{ boolean __sel = false; boolean __roll = false; boolean __press = false;"
                  + "  for (int __i = 0; __i < $3.length; __i++) {"
                  + "    javax.swing.ButtonModel __m = $3[__i];"
                  + "    if (__m == null) continue;"
                  + "    if (__m.isSelected()) __sel = true;"
                  + "    if (__m.isRollover()) __roll = true;"
                  + "    if (__m.isArmed() || __m.isPressed()) __press = true;"
                  + "  }"
                  + "  if (__press || __sel || __roll) {"
                  + "    java.awt.Graphics2D __g = (java.awt.Graphics2D) $1.create();"
                  + "    __g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,"
                  + "        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);"
                  + "    boolean __dark = macosskin.DarkMode.isDark();"
                  + "    boolean __menu = (this.commandButton instanceof"
                  + "        org.pushingpixels.flamingo.api.common.JCommandMenuButton)"
                  + "      || (this.commandButton instanceof"
                  + "        org.pushingpixels.flamingo.api.common.JCommandToggleMenuButton);"
                  + "    if (__menu && __dark) {"
                  + "      __g.setColor(new java.awt.Color(59, 105, 218));"
                  + "      __g.fillRect($2.x, $2.y, $2.width, $2.height);"
                  + "    } else {"
                  + "      java.awt.Color __c = __press"
                  + "          ? (__dark ? new java.awt.Color(81, 81, 81) : new java.awt.Color(196, 196, 196))"
                  + "          : (__sel"
                  + "              ? (__dark ? new java.awt.Color(71, 71, 71) : new java.awt.Color(208, 208, 208))"
                  + "              : (__dark ? new java.awt.Color(61, 61, 61) : new java.awt.Color(224, 224, 224)));"
                  + "      __g.setColor(__c);"
                  + "      __g.fillRoundRect($2.x, $2.y, $2.width, $2.height, 8, 8);"
                  + "    }"
                  + "    __g.dispose();"
                  + "  } }");
            writeClass(cmdUi, outDir);
            System.out.println("[SkinPatch] komut buton durum dolguları Word tarzı yapıldı.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: komut buton dolgusu atlandı: " + t);
        }

        // (7c: koyu mod ikon aydınlatma — IconDarken/ModeAwareImage — bu fazda YOK.)

        // Sekme alanı Word tarzı: Office-2007'nin tam-genişlik çizgisi ve seçili
        // sekme konturu kalkar (paintTaskArea no-op); seçili sekme, metin altında
        // kısa yuvarlak çubukla vurgulanır (koyu: #E7E7E7, açık: koyu gri).
        try {
            CtClass ribbonUi = pool.get(
                "org.pushingpixels.flamingo.internal.ui.ribbon.BasicRibbonUI");
            ribbonUi.getMethod("paintTaskArea", "(Ljava/awt/Graphics;IIII)V")
                .setBody("{ }");
            writeClass(ribbonUi, outDir);
            System.out.println("[SkinPatch] sekme alanı çizgisi/konturu kaldırıldı.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: sekme alanı yaması atlandı: " + t);
        }

        // Hızlı erişim (taskbar) çubuğu: TaskbarPanel.paintComponent Office-2007
        // "swoosh" dekoru basar — getOutline arc'lı kontur (dolgu + getBorderColor
        // çizimi) ve son bileşenden panel sonuna alt çizgi. Koyu temada bunlar
        // ikon gruplarının arasında saçma eğri/dik ayrım çizgileri olarak görünür.
        // Dekorasyon tamamen kalkar; butonlar paintChildren ile zaten çizilir.
        // Ek görev: MacLook agent'ı yerel macOS başlığını devralıp temizlenmiş
        // halini rootpane "macoslook.title" özelliğine koyar (yerel metin dar
        // pencerede ikonların üstüne kayıyordu) — burada o başlık panelde
        // ortalanır, son bileşenin sağına KLEMPLENIR ve sığmazsa kırpılır;
        // ikonlarla çakışma yapısal olarak imkânsız. Agent yoksa özellik boş
        // kalır, hiçbir şey çizilmez (yerel başlık da gizlenmemiş olur).
        try {
            CtClass tbPanel = pool.get(
                "org.pushingpixels.flamingo.internal.ui.ribbon.BasicRibbonUI$TaskbarPanel");
            tbPanel.getMethod("paintComponent", "(Ljava/awt/Graphics;)V")
                .setBody(
                    "{ javax.swing.JRootPane __rp = javax.swing.SwingUtilities.getRootPane(this);"
                  + "  Object __t = (__rp == null) ? null : __rp.getClientProperty(\"macoslook.title\");"
                  + "  if (__t == null) return;"
                  + "  String __s = __t.toString();"
                  + "  if (__s.length() == 0) return;"
                  + "  int __maxx = 0;"
                  + "  java.awt.Component[] __ks = getComponents();"
                  + "  for (int __i = 0; __i < __ks.length; __i++) {"
                  + "    if (__ks[__i].isVisible()) {"
                  + "      int __r = __ks[__i].getX() + __ks[__i].getWidth();"
                  + "      if (__r > __maxx) __maxx = __r;"
                  + "    }"
                  + "  }"
                  + "  int __pad = 12;"
                  + "  int __avail = getWidth() - __maxx - (2 * __pad);"
                  + "  if (__avail < 30) return;"
                  + "  java.awt.Graphics2D __g = (java.awt.Graphics2D) $1.create();"
                  + "  __g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,"
                  + "      java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);"
                  + "  java.awt.Font __f = javax.swing.UIManager.getFont(\"Label.font\");"
                  + "  if (__f != null) __g.setFont(__f.deriveFont(java.awt.Font.BOLD, 13.0f));"
                  + "  java.awt.Color __fg = javax.swing.UIManager.getColor(\"Label.foreground\");"
                  + "  if (__fg != null) __g.setColor(__fg);"
                  + "  java.awt.FontMetrics __fm = __g.getFontMetrics();"
                  + "  if (__fm.stringWidth(__s) > __avail) {"
                  + "    int __ew = __fm.stringWidth(\"...\");"
                  + "    StringBuilder __sb = new StringBuilder();"
                  + "    int __w = 0;"
                  + "    for (int __j = 0; __j < __s.length(); __j++) {"
                  + "      int __cw = __fm.charWidth(__s.charAt(__j));"
                  + "      if (__w + __cw + __ew > __avail) break;"
                  + "      __w += __cw;"
                  + "      __sb.append(__s.charAt(__j));"
                  + "    }"
                  + "    __s = __sb.append(\"...\").toString();"
                  + "  }"
                  + "  int __tw = __fm.stringWidth(__s);"
                  + "  int __x = (getWidth() - __tw) / 2;"
                  + "  if (__x < __maxx + __pad) __x = __maxx + __pad;"
                  + "  int __y = ((getHeight() - __fm.getHeight()) / 2) + __fm.getAscent();"
                  + "  __g.drawString(__s, __x, __y);"
                  + "  __g.dispose(); }");
            writeClass(tbPanel, outDir);
            System.out.println("[SkinPatch] hızlı erişim: dekor kaldırıldı + başlık çizimi eklendi.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: hızlı erişim kontur yaması atlandı: " + t);
        }
        try {
            CtClass tabUi = pool.get(
                "org.pushingpixels.flamingo.internal.ui.ribbon.BasicRibbonTaskToggleButtonUI");
            tabUi.getMethod("paintButtonBackground",
                "(Ljava/awt/Graphics;Ljava/awt/Rectangle;)V")
                .setBody(
                    "{ if (this.commandButton.getActionModel().isSelected()) {"
                  + "    int __vis = $2.height;"
                  + "    java.awt.Container __par = this.commandButton.getParent();"
                  + "    if (__par != null) {"
                  + "      int __pb = __par.getHeight() - this.commandButton.getY();"
                  + "      if (__pb > 0 && __pb < __vis) __vis = __pb;"
                  + "    }"
                  + "    java.awt.Graphics2D __g = (java.awt.Graphics2D) $1.create();"
                  + "    __g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,"
                  + "        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);"
                  + "    __g.setColor(macosskin.DarkMode.isDark()"
                  + "        ? new java.awt.Color(231, 231, 231)"
                  + "        : new java.awt.Color(60, 60, 60));"
                  + "    int __w = $2.width;"
                  + "    int __bw = Math.max(18, __w - 26);"
                  + "    int __bh = 3;"
                  + "    __g.fillRoundRect($2.x + (__w - __bw) / 2,"
                  + "        $2.y + __vis - __bh, __bw, __bh, __bh, __bh);"
                  + "    __g.dispose();"
                  + "    macosskin.DarkMode.trace(\"tabBar \" + this.commandButton.getText()"
                  + "        + \" vis=\" + __vis + \" h=\" + $2.height);"
                  + "  } }");
            writeClass(tabUi, outDir);
            System.out.println("[SkinPatch] seçili sekme alt çubuğu eklendi.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: sekme alt çubuğu atlandı: " + t);
        }

        // Orb (uygulama menü düğmesi) arka plan efektini düzleştir: görsel asset
        // (resources/ude.png) çizilir, arkasındaki degrade/parlama çizimi kalkar.
        try {
            CtClass orbUi = pool.get(
                "org.pushingpixels.flamingo.internal.ui.ribbon.appmenu.BasicRibbonApplicationMenuButtonUI");
            orbUi.getMethod("paintButtonBackground",
                "(Ljava/awt/Graphics;Ljava/awt/Rectangle;)V")
                .setBody("{ }");
            writeClass(orbUi, outDir);
            System.out.println("[SkinPatch] Orb arka plan efekti düzleştirildi.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: orb arka plan yaması atlandı: " + t);
        }

        // Zengin tooltip (RichTooltip) Word tarzı: paintBackground hardcoded
        // AÇIK gradyan basar (Label.disabledForeground.brighter() 0.9/0.4) ->
        // koyu modda açık metin + açık zemin = okunmaz. Tema-duyarlı düz dolgu
        // (Word ölçümü: koyu #2E3032, açık beyaz); metin renkleri temadan,
        // kontur getBorderColor() yamasından zaten doğru. Substance'ın kendi
        // RichTooltip delegate'i yok, Basic'i yamalamak yeterli.
        try {
            CtClass rtUi = pool.get(
                "org.pushingpixels.flamingo.internal.ui.common.BasicRichTooltipPanelUI");
            rtUi.getMethod("paintBackground", "(Ljava/awt/Graphics;)V")
                .setBody(
                    "{ java.awt.Graphics2D __g = (java.awt.Graphics2D) $1.create();"
                  + "  __g.setColor(macosskin.DarkMode.isDark()"
                  + "      ? new java.awt.Color(46, 48, 50)"
                  + "      : java.awt.Color.WHITE);"
                  + "  __g.fillRect(0, 0, this.richTooltipPanel.getWidth(),"
                  + "      this.richTooltipPanel.getHeight());"
                  + "  __g.dispose(); }");
            writeClass(rtUi, outDir);
            System.out.println("[SkinPatch] zengin tooltip Word tarzı düz dolguya çekildi.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: zengin tooltip yaması atlandı: " + t);
        }

        // Orb uygulama menüsü Word tarzı: popup kenarlıkları anonim Border iç
        // sınıflarından gelir ve Label.disabledForeground (+brighter x2) okur ->
        // koyu temada bembeyaz çift çerçeve. $8 (dış kenarlık) ayrıca üstte 20px
        // Office-2007 bandı (renderSurface) + orb KOPYASI çizer; $9 mainPanel'e
        // ikinci çift çerçeve, $6 sütun ayracı, $7 footer'a gradyan basar.
        // Tek ince tema-duyarlı kontur kalır, gerisi düzlenir.
        try {
            String pp = "org.pushingpixels.flamingo.internal.ui.ribbon.appmenu."
                + "BasicRibbonApplicationMenuPopupPanelUI";
            CtClass outerB = pool.get(pp + "$8");
            outerB.getMethod("getBorderInsets",
                "(Ljava/awt/Component;)Ljava/awt/Insets;")
                .setBody("{ return new java.awt.Insets(6, 4, 6, 4); }");
            outerB.getMethod("paintBorder",
                "(Ljava/awt/Component;Ljava/awt/Graphics;IIII)V")
                .setBody(
                    "{ $2.setColor(macosskin.DarkMode.isDark()"
                  + "    ? new java.awt.Color(90, 90, 90)"
                  + "    : new java.awt.Color(200, 200, 200));"
                  + "  $2.drawRect($3, $4, $5 - 1, $6 - 1); }");
            writeClass(outerB, outDir);

            CtClass mainB = pool.get(pp + "$9");
            mainB.getMethod("paintBorder",
                "(Ljava/awt/Component;Ljava/awt/Graphics;IIII)V")
                .setBody("{ }");
            writeClass(mainB, outDir);

            CtClass divB = pool.get(pp + "$6");
            divB.getMethod("paintBorder",
                "(Ljava/awt/Component;Ljava/awt/Graphics;IIII)V")
                .setBody(
                    "{ $2.setColor(macosskin.DarkMode.isDark()"
                  + "    ? new java.awt.Color(61, 61, 61)"
                  + "    : new java.awt.Color(224, 224, 224));"
                  + "  int __x = $1.getComponentOrientation().isLeftToRight()"
                  + "      ? $3 : $3 + $5 - 1;"
                  + "  $2.drawLine(__x, $4, __x, $4 + $6); }");
            writeClass(divB, outDir);

            CtClass footB = pool.get(pp + "$7");
            footB.getMethod("paintComponent", "(Ljava/awt/Graphics;)V")
                .setBody(
                    "{ $1.setColor(this.getBackground());"
                  + "  $1.fillRect(0, 0, this.getWidth(), this.getHeight()); }");
            writeClass(footB, outDir);
            System.out.println("[SkinPatch] orb menü popup'ı Word tarzına çekildi.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: orb menü popup yaması atlandı: " + t);
        }

        // Komut popup'ları (Bul/Değiştir gibi açılır menüler) Word tarzı:
        // BasicPopupPanelUI.installDefaults LineBorder(getBorderColor) kurar ->
        // koyu temada gri çerçeve. Word ölçümü: popup yüzeyi şeritten KOYU
        // (#1E1E1E), kenarlık siyaha yakın (#050505); açık temada beyaz yüzey +
        // #C8C8C8 kontur (orb menü değerleri). setBackground/setBorder UIResource
        // OLMAYAN değer bıraktığından sonraki installDefaults çağrıları ezmez.
        try {
            CtClass ppUi = pool.get(
                "org.pushingpixels.flamingo.internal.ui.common.popup.BasicPopupPanelUI");
            ppUi.getMethod("installDefaults", "()V")
                .insertAfter(
                    "{ boolean __dark = macosskin.DarkMode.isDark();"
                  + "  this.popupPanel.setBackground(__dark"
                  + "      ? new java.awt.Color(30, 30, 30) : java.awt.Color.WHITE);"
                  + "  this.popupPanel.setBorder("
                  + "      javax.swing.BorderFactory.createCompoundBorder("
                  + "          javax.swing.BorderFactory.createLineBorder(__dark"
                  + "              ? new java.awt.Color(5, 5, 5)"
                  + "              : new java.awt.Color(200, 200, 200)),"
                  + "          javax.swing.BorderFactory.createEmptyBorder(4, 1, 4, 1))); }");
            writeClass(ppUi, outDir);
            System.out.println("[SkinPatch] komut popup paneli Word tarzına çekildi.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: komut popup yaması atlandı: " + t);
        }

        // Şerit butonu odak çerçevesi: UDE'nin FocusListener'ı (a.b.a.a.t)
        // focusGained'de butona BevelBorder(RAISED) basar (Win95 kalıntısı) —
        // popup açılınca buton odaklandığından gri kabartma çerçeve belirir
        // (runtime iz-grafikle kanıtlandı: BevelBorder.paintRaisedBevel).
        // focusGained boşaltılır; focusLost orijinal border'ı geri koyduğundan
        // zararsız no-op kalır.
        try {
            CtClass fl = pool.get("tr.gov.uyap.system.a.b.a.a.t");
            fl.getMethod("focusGained", "(Ljava/awt/event/FocusEvent;)V")
                .setBody("{ }");
            writeClass(fl, outDir);
            System.out.println("[SkinPatch] buton odak BevelBorder çerçevesi kaldırıldı.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: odak çerçeve yaması atlandı: " + t);
        }

        // Galeri popup panelleri (Madde İşareti Kitaplığı vb.): grup zemini
        // gri fillRect, grup başlığı Office renderSurface bandı, başlık etiketi
        // koyu metin — koyu temada okunmaz açık bloklar. Zemin popup yüzeyine
        // (#1E1E1E/beyaz), başlık bandı hafif ayrık tona düzlenir; etiket
        // metni koyu modda açığa çekilir.
        try {
            CtClass pnl = pool.get(
                "org.pushingpixels.flamingo.internal.ui.common.BasicCommandButtonPanelUI");
            pnl.getMethod("paintGroupBackground", "(Ljava/awt/Graphics;IIIII)V")
                .setBody(
                    "{ $1.setColor(macosskin.DarkMode.isDark()"
                  + "    ? new java.awt.Color(30, 30, 30) : java.awt.Color.WHITE);"
                  + "  $1.fillRect($3, $4, $5, $6); }");
            pnl.getMethod("paintGroupTitleBackground", "(Ljava/awt/Graphics;IIIII)V")
                .setBody(
                    "{ $1.setColor(macosskin.DarkMode.isDark()"
                  + "    ? new java.awt.Color(42, 42, 42) : new java.awt.Color(240, 240, 240));"
                  + "  $1.fillRect($3, $4, $5, $6); }");
            pnl.getMethod("recomputeGroupHeaders", "()V")
                .insertAfter(
                    "{ if (this.groupLabels != null && macosskin.DarkMode.isDark()) {"
                  + "    for (int __i = 0; __i < this.groupLabels.length; __i++) {"
                  + "      if (this.groupLabels[__i] != null)"
                  + "        this.groupLabels[__i].setForeground("
                  + "            new java.awt.Color(228, 228, 228));"
                  + "    }"
                  + "  } }");
            writeClass(pnl, outDir);
            System.out.println("[SkinPatch] galeri popup paneli Word tarzına çekildi.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: galeri panel yaması atlandı: " + t);
        }

        // UDE özel popup widget'ları (madde işareti / numaralandırma galerileri
        // vb.): gui.a.t (etiket/karo) ve gui.a.A (panel) Win95 sabit renkleri
        // çalışma anında setBackground/Foreground/Border ile alır (zemin
        // #7A7A7A/LIGHT_GRAY, seçim turuncusu #FFCC99 ailesi, kontur silver —
        // iz-grafik probe ile kanıtlandı). Sınıflara override enjekte edilir,
        // değerler PopupRemap'ten tema-duyarlı eşlenir; seçim değişimlerinde
        // takılan yeni border'lar da böylece yakalanır.
        try {
            String[] widgets = {
                "tr.com.havelsan.uyap.system.editor.common.gui.a.t",
                "tr.com.havelsan.uyap.system.editor.common.gui.a.A",
            };
            for (String w : widgets) {
                CtClass cc = pool.get(w);
                cc.addMethod(CtNewMethod.make(
                    "public void setBackground(java.awt.Color c)"
                  + "{ super.setBackground(macosskin.PopupRemap.bg(c)); }", cc));
                cc.addMethod(CtNewMethod.make(
                    "public void setForeground(java.awt.Color c)"
                  + "{ super.setForeground(macosskin.PopupRemap.fg(c)); }", cc));
                cc.addMethod(CtNewMethod.make(
                    "public void setBorder(javax.swing.border.Border b)"
                  + "{ super.setBorder(macosskin.PopupRemap.border(b)); }", cc));
                writeClass(cc, outDir);
            }
            System.out.println("[SkinPatch] özel popup widget renkleri PopupRemap'e bağlandı.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: popup widget yaması atlandı: " + t);
        }

        // WebLaF menü onay/radyo işaretleri: WebCheckBoxMenuItemUI ve
        // WebRadioButtonMenuItemUI statik 16px 1x PNG'leri drawImage'la basar —
        // Retina'da bulanık Win95 kutuları. Statikler MenuMarks'ın
        // çok-çözünürlüklü tema-duyarlı vektör işaretleriyle değiştirilir
        // (final kaldırılıp clinit sonuna atama eklenir).
        try {
            CtClass cbm = pool.get("com.alee.laf.menu.WebCheckBoxMenuItemUI");
            for (String f : new String[]{"boxIcon", "boxCheckIcon"}) {
                javassist.CtField cf = cbm.getDeclaredField(f);
                cf.setModifiers(cf.getModifiers() & ~javassist.Modifier.FINAL);
            }
            cbm.getClassInitializer().insertAfter(
                "{ boxIcon = macosskin.MenuMarks.empty();"
              + "  boxCheckIcon = macosskin.MenuMarks.check(); }");
            writeClass(cbm, outDir);

            CtClass rbm = pool.get("com.alee.laf.menu.WebRadioButtonMenuItemUI");
            for (String f : new String[]{"radioIcon", "radioCheckIcon"}) {
                javassist.CtField cf = rbm.getDeclaredField(f);
                cf.setModifiers(cf.getModifiers() & ~javassist.Modifier.FINAL);
            }
            rbm.getClassInitializer().insertAfter(
                "{ radioIcon = macosskin.MenuMarks.empty();"
              + "  radioCheckIcon = macosskin.MenuMarks.radioOn(); }");
            writeClass(rbm, outDir);
            System.out.println("[SkinPatch] menü onay/radyo işaretleri Retina vektöre çekildi.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: menü işaret yaması atlandı: " + t);
        }

        // Koyu belge arkaplanı (Görünüm sekmesindeki onay kutusu, MacLook
        // agent'ı ekler): editör bileşeni hj.paint'in Graphics'i DarkPage ile
        // sarılır — beyaz sayfa/siyah metin HSL açıklık çevirisiyle Word koyu
        // moduna eşlenir. Kapalıyken (varsayılan) wrap aynen geri döner;
        // baskı yolu (isPaintingForPrint) hiç sarılmaz.
        try {
            CtClass hj = pool.get("tr.com.havelsan.uyap.system.editor.common.text.hj");
            hj.getMethod("paint", "(Ljava/awt/Graphics;)V").insertBefore(
                "{ $1 = macosskin.DarkPage.wrap(this, $1); }");
            writeClass(hj, outDir);
            System.out.println("[SkinPatch] koyu belge arkaplanı kancası eklendi (hj.paint).");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: koyu belge yaması atlandı: " + t);
        }

        // Popup menü ikon oluğu: MenuPanel Office-2007 renderSurface bandı +
        // ayraç çizgisi basar; Word menülerinde oluk yok — ikisi de boşaltılır.
        try {
            CtClass mp = pool.get(
                "org.pushingpixels.flamingo.internal.ui.common.popup."
                + "BasicCommandPopupMenuUI$MenuPanel");
            mp.getMethod("paintIconGutterBackground", "(Ljava/awt/Graphics;)V")
                .setBody("{ }");
            mp.getMethod("paintIconGutterSeparator", "(Ljava/awt/Graphics;)V")
                .setBody("{ }");
            writeClass(mp, outDir);
            System.out.println("[SkinPatch] popup menü ikon oluğu düzlendi.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: ikon oluğu yaması atlandı: " + t);
        }

        // Bul/Değiştir diyaloğu (gui.jc) "Seçenekler" grubu: TitledBorder'a
        // EtchedBorder (3B oymalı, beyaz vurgu çizgili Win95 izi) sarılıyor —
        // Word düz görünümü için tema-duyarlı tek ince LineBorder'a çevrilir.
        try {
            CtClass jc = pool.get("tr.com.havelsan.uyap.system.editor.common.gui.jc");
            jc.instrument(new ExprEditor() {
                public void edit(javassist.expr.NewExpr e)
                        throws javassist.CannotCompileException {
                    if ("javax.swing.border.EtchedBorder".equals(e.getClassName())) {
                        e.replace("{ $_ = new macosskin.FlatEtchedBorder(); }");
                    }
                }
            });
            writeClass(jc, outDir);
            System.out.println("[SkinPatch] Bul/Değiştir grup çerçevesi düzlendi.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: grup çerçevesi yaması atlandı: " + t);
        }

        // Substance EDT denetimleri no-op: UDE, bileşenleri arka plan iş
        // parçacığında kurmaya yaslanır (PDF dışa aktarımı b.b WPDocumentPanel'i
        // SwingWorker'da üretir; hata diyalogları da worker'dan açılır). Aqua/
        // WebLaF buna ses çıkarmaz; Substance UiThreadingViolationException
        // fırlatır, UIDefaults.getUI bunu yutup null UI bırakır → PDF dönüşümü
        // NPE ile 0 bayt üretir, hata diyaloğu 80x29 boş modal pencere olarak
        // kalır ve spinner sonsuza dek döner. Denetimler boşaltılarak Substance,
        // UDE'nin yazıldığı gevşek LAF davranışına çekilir.
        CtClass scu = pool.get("org.jvnet.substance.utils.SubstanceCoreUtilities");
        scu.getMethod("testComponentCreationThreadingViolation",
            "(Ljava/awt/Component;)V").setBody("{ }");
        scu.getMethod("testComponentStateChangeThreadingViolation",
            "(Ljava/awt/Component;)V").setBody("{ }");
        writeClass(scu, outDir);
        CtClass lwu = pool.get("org.jvnet.lafwidget.LafWidgetUtilities");
        lwu.getMethod("testComponentStateChangeThreadingViolation",
            "(Ljava/awt/Component;)V").setBody("{ }");
        writeClass(lwu, outDir);
        System.out.println("[SkinPatch] Substance EDT denetimleri no-op (PDF dışa aktarım düzeltmesi).");
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
