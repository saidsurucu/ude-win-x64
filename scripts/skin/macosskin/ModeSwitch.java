package macosskin;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Renk modunu CANLI değiştirir (Görünüm > Renk modu; MacLook agent çağırır).
 * Açılıştaki aF.run() kurulum reçetesinin çalışma-anı eşi:
 *  1) tercihi yaz + DarkMode cache'ini sıfırla,
 *  2) FlatUdeSkin/FlatUdeDarkSkin kur (setSkin açık pencereleri tazeler ama
 *     UIDefaults put'larımızı da siler),
 *  3) Aqua scrollbar/slider + Word* delegate'lerini YENİDEN kur,
 *  4) kanvas (wp.p.E), cetvel zemini ve tüm pencere ağaçlarını güncelle.
 * İkonlar ModeAwareImage sayesinde kendiliğinden uyar (paint anında varyant).
 * Font policy'ye DOKUNULMAZ: setSkin onu sıfırlamaz, yeniden sarmak
 * FlatFontPolicy'yi üst üste bindirir. EDT'de çağrılmalıdır.
 */
public final class ModeSwitch {
    private ModeSwitch() {}

    public static void apply(String mode) {
        try {
            DarkMode.setMode(mode);
            DarkMode.resetCache();
            boolean dark = DarkMode.isDark();
            org.jvnet.substance.api.SubstanceSkin skin = dark
                ? new FlatUdeDarkSkin() : new FlatUdeSkin();
            org.jvnet.substance.SubstanceLookAndFeel.setSkin(skin);
            WordTooltip.install();
            WordCombo.install();
            WordCheck.install();
            WordButton.install();
            WordTabs.install();
            WordField.install();
            try {
                tr.com.havelsan.uyap.system.swing.wp.p.E = DarkMode.canvasColor();
                DarkMode.trace("modeswitch kanvas E="
                    + tr.com.havelsan.uyap.system.swing.wp.p.E);
            } catch (Throwable ce) {
                DarkMode.trace("modeswitch kanvas: " + ce);
            }
            java.awt.Color pb = UIManager.getColor("Panel.background");
            for (Window w : Window.getWindows()) {
                updateTreeSafe(w);
                try {
                    fixRulers(w, dark);
                } catch (Throwable rt) {
                    DarkMode.trace("modeswitch cetvel: " + rt);
                }
                try {
                    if (pb != null) {
                        w.setBackground(pb);
                        fixStaleUiBackgrounds(w, pb);
                    }
                } catch (Throwable bt) {
                    DarkMode.trace("modeswitch zemin: " + bt);
                }
                try {
                    w.repaint();
                } catch (Throwable pt) {
                    DarkMode.trace("modeswitch repaint: " + pt);
                }
            }
            DarkMode.trace("modeswitch uygulandı: " + mode + " dark=" + dark);
        } catch (Throwable t) {
            DarkMode.trace("modeswitch HATA: " + t);
        }
    }

    /** UI delegate'i OLMAYAN konteynerler (JRootPane/JLayeredPane; updateUI
     *  arkaplanlarını tazelemez) eski modun UIResource rengiyle kalır —
     *  pencere köşeleri/yeniden boyama anlarında sızar; yeni Panel.background
     *  basılır (yalnız UIResource olanlara: kullanıcı renkleri korunur). */
    private static void fixStaleUiBackgrounds(Component c, java.awt.Color pb) {
        if (c instanceof javax.swing.JRootPane
                || c instanceof javax.swing.JLayeredPane) {
            if (c.getBackground() instanceof javax.swing.plaf.ColorUIResource) {
                c.setBackground(new javax.swing.plaf.ColorUIResource(pb));
            }
        }
        if (c instanceof Container) {
            for (Component k : ((Container) c).getComponents()) {
                fixStaleUiBackgrounds(k, pb);
            }
        }
    }

    /** updateComponentTreeUI'nin hataya dayanıklı eşi: tek bileşenin updateUI
     *  hatası ağacın kalanını (örn. cetvel bölgesi) güncellenmemiş bırakmasın
     *  diye bileşen başına yakalanır ve İZLENİR. */
    private static void updateTreeSafe(Component c) {
        if (c instanceof javax.swing.JComponent) {
            try {
                ((javax.swing.JComponent) c).updateUI();
            } catch (Throwable t) {
                DarkMode.trace("modeswitch updateUI "
                    + c.getClass().getName() + ": " + t);
            }
        }
        if (c instanceof Container) {
            for (Component k : ((Container) c).getComponents()) {
                updateTreeSafe(k);
            }
        }
    }

    /** Kuruluşta donan bileşen renkleri: cetvel zemini (sabit-beyaz
     *  setBackground; koyuda Word tonu, açıkta beyaz — MacLook
     *  fixRulerBackground'un iki yönlü eşi) ve editör kanvası (text.hj
     *  türevleri ctor'da wp.p.E'yi background'a KOPYALAR — canlı geçişte
     *  bayat kalır; DarkPage açıkken bayat koyu zemin griye çevrilip
     *  #C2C2C2 görünümü veriyordu, TreeProbe ile kanıtlandı). */
    private static void fixRulers(Component c, boolean dark) {
        for (Class<?> i : c.getClass().getInterfaces()) {
            if (i.getName().endsWith("IRuler")) {
                c.setBackground(dark ? new Color(70, 70, 70) : Color.WHITE);
                try {
                    c.getClass().getMethod("setColor_unusableregion", Color.class)
                        .invoke(c, DarkMode.canvasColor());
                    c.getClass().getMethod("setColor_border", Color.class)
                        .invoke(c, DarkMode.canvasColor());
                } catch (Throwable ignore) {
                }
                c.repaint();
                break;
            }
        }
        if (c instanceof tr.com.havelsan.uyap.system.editor.common.text.hj) {
            c.setBackground(DarkMode.canvasColor());
        }
        if (c instanceof Container) {
            for (Component k : ((Container) c).getComponents()) {
                fixRulers(k, dark);
            }
        }
    }
}
