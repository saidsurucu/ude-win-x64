package macosskin;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicCheckBoxMenuItemUI;
import javax.swing.plaf.basic.BasicCheckBoxUI;
import javax.swing.plaf.basic.BasicRadioButtonMenuItemUI;
import javax.swing.plaf.basic.BasicRadioButtonUI;

/**
 * Onay kutusu ve radyo düğmelerini Word/Fluent diline çeker (SKIN=1).
 * Substance 5'in SubstanceCheckBoxUI'ı işareti 18px 1x BufferedImage
 * cache'inden basar -> Retina'da bulanık Win95 kutusu (iz-grafik probe ile
 * kanıtlandı: ImageIcon.paintIcon <- SubstanceRadioButtonUI.paint).
 * WordTooltip/WordCombo deseni: Basic tabanlı delegate + paint anında
 * vektör çizen Icon — her ölçekte keskin, cache yok. Skin değişimi
 * UIDefaults'u tazelediğinden iki kurulum noktasından da çağrılmalı.
 */
public final class WordCheck {

    private WordCheck() {}

    public static void install() {
        try {
            UIManager.put("CheckBoxUI", "macosskin.WordCheck$CheckUI");
            UIManager.put("RadioButtonUI", "macosskin.WordCheck$RadioUI");
            UIManager.put("CheckBoxMenuItemUI", "macosskin.WordCheck$CheckMenuUI");
            UIManager.put("RadioButtonMenuItemUI", "macosskin.WordCheck$RadioMenuUI");
            DarkMode.trace("WordCheck kuruldu dark=" + DarkMode.isDark());
        } catch (Throwable t) {
            DarkMode.trace("WordCheck HATA: " + t);
        }
    }

    static Color accent() { return new Color(59, 105, 218); }

    static Color boxFill(boolean dark) {
        return dark ? new Color(45, 45, 45) : Color.WHITE;
    }

    static Color boxLine(boolean dark, boolean enabled) {
        if (!enabled) return dark ? new Color(80, 80, 80) : new Color(200, 200, 200);
        return dark ? new Color(140, 140, 140) : new Color(118, 118, 118);
    }

    static Color selFill(boolean dark, boolean enabled) {
        if (!enabled) return dark ? new Color(70, 70, 70) : new Color(190, 190, 190);
        return accent();
    }

    static Graphics2D prep(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
            RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    public static final class CheckUI extends BasicCheckBoxUI {
        private static final Icon ICON = new BoxIcon();

        public static ComponentUI createUI(JComponent c) { return new CheckUI(); }

        @Override public Icon getDefaultIcon() { return ICON; }
    }

    public static final class RadioUI extends BasicRadioButtonUI {
        private static final Icon ICON = new DotIcon();

        public static ComponentUI createUI(JComponent c) { return new RadioUI(); }

        @Override public Icon getDefaultIcon() { return ICON; }
    }

    /** Şeride gömülü JCheckBoxMenuItem'lar (Biçim > Harf Denetimi vb.) ve
     *  popup menülerdeki onaylı öğeler: Substance menü check'i de 1x raster
     *  bastığından Basic tabanlı delegate + vektör checkIcon kullanılır. */
    public static final class CheckMenuUI extends BasicCheckBoxMenuItemUI {
        public static ComponentUI createUI(JComponent c) { return new CheckMenuUI(); }

        @Override protected void installDefaults() {
            super.installDefaults();
            checkIcon = new BoxIcon();
        }
    }

    public static final class RadioMenuUI extends BasicRadioButtonMenuItemUI {
        public static ComponentUI createUI(JComponent c) { return new RadioMenuUI(); }

        @Override protected void installDefaults() {
            super.installDefaults();
            checkIcon = new DotIcon();
        }
    }

    static final class BoxIcon implements Icon {
        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            ButtonModel m = c instanceof AbstractButton
                ? ((AbstractButton) c).getModel() : null;
            boolean sel = m != null && m.isSelected();
            boolean en = m == null || m.isEnabled();
            boolean dark = DarkMode.isDark();
            Graphics2D g2 = prep(g);
            RoundRectangle2D box = new RoundRectangle2D.Float(
                x + 1f, y + 1f, 14f, 14f, 4f, 4f);
            if (sel) {
                g2.setColor(selFill(dark, en));
                g2.fill(box);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.6f,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D.Float p = new Path2D.Float();
                p.moveTo(x + 4.4f, y + 8.4f);
                p.lineTo(x + 7.0f, y + 11.0f);
                p.lineTo(x + 11.8f, y + 5.2f);
                g2.draw(p);
            } else {
                g2.setColor(boxFill(dark));
                g2.fill(box);
                g2.setColor(boxLine(dark, en));
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(box);
            }
            g2.dispose();
        }
    }

    static final class DotIcon implements Icon {
        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            ButtonModel m = c instanceof AbstractButton
                ? ((AbstractButton) c).getModel() : null;
            boolean sel = m != null && m.isSelected();
            boolean en = m == null || m.isEnabled();
            boolean dark = DarkMode.isDark();
            Graphics2D g2 = prep(g);
            Ellipse2D ring = new Ellipse2D.Float(x + 1f, y + 1f, 14f, 14f);
            g2.setColor(boxFill(dark));
            g2.fill(ring);
            if (sel) {
                g2.setColor(selFill(dark, en));
                g2.setStroke(new BasicStroke(1.6f));
                g2.draw(ring);
                g2.fill(new Ellipse2D.Float(x + 5f, y + 5f, 6f, 6f));
            } else {
                g2.setColor(boxLine(dark, en));
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(ring);
            }
            g2.dispose();
        }
    }
}
