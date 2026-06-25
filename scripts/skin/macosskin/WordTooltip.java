package macosskin;

import java.awt.Color;

import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;

/**
 * Düz JToolTip'leri Word paletine çeker (SKIN=1). Substance'ın
 * SubstanceToolTipUI'ı yerine Basic delegate kurulur (Aqua scrollbar
 * deseninin aynısı); renkler Word ekran ölçümünden: koyu zemin #2E3032,
 * açık zemin beyaz. Skin değişimi UIDefaults'u tazelediğinden hem
 * aF.run() hem setSkin(String) sarmasından çağrılmalı.
 */
public final class WordTooltip {
    private WordTooltip() {}

    public static void install() {
        try {
            boolean dark = DarkMode.isDark();
            Color bg = dark ? new Color(46, 48, 50) : Color.WHITE;
            Color fg = dark ? new Color(234, 234, 234) : new Color(38, 38, 38);
            Color line = dark ? new Color(90, 90, 90) : new Color(198, 198, 198);
            UIManager.put("ToolTipUI", "javax.swing.plaf.basic.BasicToolTipUI");
            UIManager.put("ToolTip.background", new ColorUIResource(bg));
            UIManager.put("ToolTip.foreground", new ColorUIResource(fg));
            UIManager.put("ToolTip.border", new BorderUIResource(
                new CompoundBorder(new LineBorder(line),
                    new EmptyBorder(3, 6, 3, 6))));
            DarkMode.trace("WordTooltip kuruldu dark=" + dark);
        } catch (Throwable t) {
            DarkMode.trace("WordTooltip HATA: " + t);
        }
    }
}
