package macosskin;

import java.awt.Color;

import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTextFieldUI;

/**
 * Metin kutularını Word paletine çeker (SKIN=1). SubstanceTextFieldUI
 * kenarlığını UIDefaults'tan değil kendi içinden kurduğundan
 * "TextField.border" override'ı işlemez; Basic-tabanlı delegate kurulur
 * (WordCombo deseni) — Basic, kenarlık/renkleri defaults'tan okur.
 * Açık: beyaz dolgu / #C9C9C9 ince kontur; koyu: #333333 / #5A5A5A.
 * Skin değişimi UIDefaults'u tazelediğinden hem aF.run() hem
 * setSkin(String) sarmasından çağrılmalı.
 */
public final class WordField {
    private WordField() {}

    static Color fill(boolean dark) {
        return dark ? new Color(51, 51, 51) : Color.WHITE;
    }

    static Color line(boolean dark) {
        return dark ? new Color(90, 90, 90) : new Color(201, 201, 201);
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
            UIManager.put("TextFieldUI", "macosskin.WordField$UI");
            UIManager.put("TextField.border", new BorderUIResource(
                new CompoundBorder(new LineBorder(line(dark), 1, true),
                    new EmptyBorder(3, 6, 3, 6))));
            UIManager.put("TextField.background", new ColorUIResource(fill(dark)));
            UIManager.put("TextField.foreground", new ColorUIResource(text(dark)));
            UIManager.put("TextField.caretForeground", new ColorUIResource(text(dark)));
            UIManager.put("TextField.inactiveForeground",
                new ColorUIResource(textDisabled(dark)));
            DarkMode.trace("WordField kuruldu dark=" + dark);
        } catch (Throwable t) {
            DarkMode.trace("WordField HATA: " + t);
        }
    }

    public static final class UI extends BasicTextFieldUI {
        public static ComponentUI createUI(JComponent c) {
            return new UI();
        }
    }
}
