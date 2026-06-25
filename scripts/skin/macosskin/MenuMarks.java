package macosskin;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;

/**
 * WebLaF menü onay/radyo işaretleri (WebCheckBoxMenuItemUI.boxCheckIcon vb.)
 * 16px 1x PNG'lerdir ve Retina'da bulanık, Win95 görünümlüdür. SkinPatch bu
 * statik alanları buradaki çok-çözünürlüklü (16+32) ve tema-duyarlı vektör
 * işaretlerle değiştirir: işaretli = düz ✓ / dolu nokta (Word/macOS menü
 * dili), işaretsiz = boş (kutu çizilmez). drawImage(Image,x,y,null) çağrısı
 * BaseMultiResolutionImage'ten Retina varyantını kendiliğinden seçer.
 */
public final class MenuMarks {

    private MenuMarks() {}

    public static ImageIcon check() {
        return new ImageIcon(new BaseMultiResolutionImage(
            paintCheck(1), paintCheck(2)));
    }

    public static ImageIcon radioOn() {
        return new ImageIcon(new BaseMultiResolutionImage(
            paintDot(1), paintDot(2)));
    }

    public static ImageIcon empty() {
        return new ImageIcon(new BaseMultiResolutionImage(
            blank(1), blank(2)));
    }

    private static Color markColor() {
        return DarkMode.isDark()
            ? new Color(228, 228, 228)
            : new Color(68, 68, 68);
    }

    private static BufferedImage blank(int s) {
        return new BufferedImage(16 * s, 16 * s, BufferedImage.TYPE_INT_ARGB);
    }

    private static BufferedImage paintCheck(int s) {
        BufferedImage img = blank(s);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
            RenderingHints.VALUE_STROKE_PURE);
        g.setColor(markColor());
        g.setStroke(new BasicStroke(1.7f * s,
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Float p = new Path2D.Float();
        p.moveTo(3.2f * s, 8.7f * s);
        p.lineTo(6.4f * s, 11.9f * s);
        p.lineTo(12.8f * s, 4.4f * s);
        g.draw(p);
        g.dispose();
        return img;
    }

    private static BufferedImage paintDot(int s) {
        BufferedImage img = blank(s);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(markColor());
        g.fill(new Ellipse2D.Float(4.8f * s, 4.8f * s, 6.4f * s, 6.4f * s));
        g.dispose();
        return img;
    }
}
