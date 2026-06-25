package macosskin;

import java.awt.Color;
import java.awt.Insets;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;

/**
 * UDE'nin özel popup widget'ları (gui.a.t etiket/karo, gui.a.A panel) Win95
 * dönemi sabit renkler kullanır: zemin LIGHT_GRAY/#7A7A7A, metin DARK_GRAY,
 * seçim kenarlığı Office turuncuları (#FF9933..#FFE5CC), kontur/ayraç
 * LIGHT_GRAY. SkinPatch bu widget'lara setBackground/setForeground/setBorder
 * override'ları enjekte eder ve değerler buradan tema-duyarlı eşlenir —
 * çalışma anındaki seçim border değişimleri de böylece yakalanır.
 * Açık temada hiçbir şey değişmez.
 */
public final class PopupRemap {

    private PopupRemap() {}

    /** Zemin: orta/açık nötr griler ve beyaz Word popup yüzeyine (#1E1E1E) —
     *  koyu modda beyaz zeminli widget zaten Win95 kalıntısıdır. */
    public static Color bg(Color c) {
        if (c == null || !DarkMode.isDark()) return c;
        if (isGray(c) && c.getRed() >= 100) return new Color(30, 30, 30);
        return c;
    }

    /** Metin: koyu nötr renkler (siyah, DARK_GRAY) açık metne. */
    public static Color fg(Color c) {
        if (c == null || !DarkMode.isDark()) return c;
        if (isGray(c) && c.getRed() <= 110) return new Color(228, 228, 228);
        return c;
    }

    /** Kenarlıklar: Line/Matte/Compound içindeki renkler eşlenerek yeniden
     *  kurulur; tanınmayan border türleri olduğu gibi bırakılır. */
    public static Border border(Border b) {
        if (b == null || !DarkMode.isDark()) return b;
        if (b instanceof CompoundBorder) {
            CompoundBorder cb = (CompoundBorder) b;
            Border o = border(cb.getOutsideBorder());
            Border i = border(cb.getInsideBorder());
            if (o != cb.getOutsideBorder() || i != cb.getInsideBorder()) {
                return new CompoundBorder(o, i);
            }
            return b;
        }
        if (b instanceof MatteBorder) {
            MatteBorder mb = (MatteBorder) b;
            Color mc = mb.getMatteColor();
            Color mapped = mc == null ? null : line(mc);
            if (mc != null && mapped != mc) {
                Insets in = mb.getBorderInsets();
                return new MatteBorder(in.top, in.left, in.bottom, in.right, mapped);
            }
            return b;
        }
        if (b instanceof LineBorder) {
            LineBorder lb = (LineBorder) b;
            Color mapped = line(lb.getLineColor());
            if (mapped != lb.getLineColor()) {
                return new LineBorder(mapped, lb.getThickness(), lb.getRoundedCorners());
            }
            return b;
        }
        return b;
    }

    /** Çizgi rengi: Office turuncu ailesi -> Word seçim mavisi (#3B69DA);
     *  açık griler -> kontur grisi (#5A5A5A); orta griler -> hover tonu. */
    static Color line(Color c) {
        if (c == null) return null;
        int r = c.getRed(), g = c.getGreen(), bl = c.getBlue();
        if (r >= 200 && g >= 120 && r > bl && g > bl) return new Color(59, 105, 218);
        if (isGray(c)) {
            int v = r;
            if (v >= 150) return new Color(90, 90, 90);
            if (v >= 90) return new Color(61, 61, 61);
        }
        return c;
    }

    static boolean isGray(Color c) {
        int max = Math.max(c.getRed(), Math.max(c.getGreen(), c.getBlue()));
        int min = Math.min(c.getRed(), Math.min(c.getGreen(), c.getBlue()));
        return max - min <= 12;
    }
}
