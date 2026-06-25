package macosskin;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicButtonUI;

/**
 * Diyalog butonlarını Word paletine çeker (SKIN=1). Substance'ın
 * SubstanceButtonUI'ı (gri kabartmalı dolgu + koyu kontur, "gölgeli" eski
 * görünüm — Bul/Değiştir diyaloğunda görünür) yerine Basic-tabanlı düz
 * delegate kurulur (WordCombo deseni): yuvarlak köşeli düz dolgu + ince
 * kontur; varsayılan buton (Enter hedefi) Word gibi vurgu mavisi.
 * Açık: beyaz dolgu / #C9C9C9 kontur; koyu: #404040 / #5A5A5A.
 * Skin değişimi UIDefaults'u tazelediğinden hem aF.run() hem
 * setSkin(String) sarmasından çağrılmalı.
 */
public final class WordButton {
    private WordButton() {}

    static Color accent() { return new Color(59, 105, 218); }
    static Color accentHover() { return new Color(52, 96, 200); }
    static Color accentPressed() { return new Color(45, 85, 180); }

    static Color fill(boolean dark) {
        return dark ? new Color(64, 64, 64) : Color.WHITE;
    }

    static Color fillHover(boolean dark) {
        return dark ? new Color(74, 74, 74) : new Color(245, 245, 245);
    }

    static Color fillPressed(boolean dark) {
        return dark ? new Color(84, 84, 84) : new Color(232, 232, 232);
    }

    static Color fillDisabled(boolean dark) {
        return dark ? new Color(51, 51, 51) : new Color(245, 245, 245);
    }

    static Color line(boolean dark) {
        return dark ? new Color(90, 90, 90) : new Color(201, 201, 201);
    }

    static Color lineDisabled(boolean dark) {
        return dark ? new Color(70, 70, 70) : new Color(222, 222, 222);
    }

    static Color text(boolean dark) {
        return dark ? new Color(228, 228, 228) : new Color(38, 38, 38);
    }

    static Color textDisabled(boolean dark) {
        return dark ? new Color(110, 110, 110) : new Color(168, 168, 168);
    }

    public static void install() {
        try {
            boolean dark = DarkMode.isDark();
            UIManager.put("ButtonUI", "macosskin.WordButton$UI");
            UIManager.put("Button.foreground", new ColorUIResource(text(dark)));
            UIManager.put("Button.disabledText", new ColorUIResource(textDisabled(dark)));
            UIManager.put("Button.border",
                new BorderUIResource(new EmptyBorder(5, 14, 5, 14)));
            DarkMode.trace("WordButton kuruldu dark=" + dark);
        } catch (Throwable t) {
            DarkMode.trace("WordButton HATA: " + t);
        }
    }

    public static final class UI extends BasicButtonUI {
        public static ComponentUI createUI(JComponent c) {
            return new UI();
        }

        protected void installDefaults(AbstractButton b) {
            super.installDefaults(b);
            LookAndFeel.installProperty(b, "opaque", Boolean.FALSE);
            b.setRolloverEnabled(true);
        }

        public void update(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            if (b.isContentAreaFilled()) {
                boolean dark = DarkMode.isDark();
                boolean def = (b instanceof JButton)
                    && ((JButton) b).isDefaultButton();
                boolean pressed = b.getModel().isArmed() && b.getModel().isPressed();
                boolean hover = b.getModel().isRollover();
                Color fill;
                Color line;
                if (!b.isEnabled()) {
                    fill = fillDisabled(dark);
                    line = lineDisabled(dark);
                } else if (def) {
                    fill = pressed ? accentPressed()
                         : (hover ? accentHover() : accent());
                    line = fill;
                } else {
                    fill = pressed ? fillPressed(dark)
                         : (hover ? fillHover(dark) : fill(dark));
                    line = line(dark);
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                int w = c.getWidth(), h = c.getHeight();
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
                g2.setColor(line);
                g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
                g2.dispose();
            }
            paint(g, c);
        }

        protected void paintText(Graphics g, AbstractButton b,
                java.awt.Rectangle textRect, String text) {
            boolean dark = DarkMode.isDark();
            boolean def = (b instanceof JButton) && ((JButton) b).isDefaultButton();
            Color fg = !b.isEnabled() ? textDisabled(dark)
                     : (def && b.isContentAreaFilled() ? Color.WHITE : text(dark));
            java.awt.FontMetrics fm = g.getFontMetrics(b.getFont());
            int mnemonicIndex = b.getDisplayedMnemonicIndex();
            g.setColor(fg);
            g.setFont(b.getFont());
            javax.swing.plaf.basic.BasicGraphicsUtils.drawStringUnderlineCharAt(
                g, text, mnemonicIndex,
                textRect.x + getTextShiftOffset(),
                textRect.y + fm.getAscent() + getTextShiftOffset());
        }

        protected void paintButtonPressed(Graphics g, AbstractButton b) {
        }

        protected void paintFocus(Graphics g, AbstractButton b,
                java.awt.Rectangle viewRect, java.awt.Rectangle textRect,
                java.awt.Rectangle iconRect) {
        }
    }
}
