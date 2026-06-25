package macosskin;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.border.EtchedBorder;

/**
 * EtchedBorder yerine geçen düz çerçeve (SKIN=1). Javassist NewExpr
 * değişiminde $_ EtchedBorder tipinde olduğundan LineBorder atanamaz;
 * bu alt sınıf tip uyumunu korur, 3B oyma yerine tema-duyarlı tek ince
 * yuvarlak kontur çizer (Word grup ayracı görünümü).
 */
public final class FlatEtchedBorder extends EtchedBorder {

    static Color line() {
        return DarkMode.isDark() ? new Color(90, 90, 90) : new Color(222, 222, 222);
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y,
            int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(line());
        g2.drawRoundRect(x, y, width - 1, height - 1, 8, 8);
        g2.dispose();
    }
}
