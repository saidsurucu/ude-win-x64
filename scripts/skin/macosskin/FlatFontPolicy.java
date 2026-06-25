package macosskin;

import org.jvnet.substance.fonts.FontPolicy;
import org.jvnet.substance.fonts.FontSet;

import javax.swing.UIDefaults;
import javax.swing.plaf.FontUIResource;
import java.awt.Font;

/**
 * Mevcut FontSet'i sarar; her fontun yalnız AİLESİNİ modern bir sistem
 * fontuna çevirir, boyut ve stili korur (Flamingo layout güvenliği).
 * "Segoe UI" Windows'ta var ve tam Türkçe glif kapsar.
 */
public class FlatFontPolicy implements FontPolicy {
    private static final String FAMILY = "Segoe UI";
    private final FontSet base;

    public FlatFontPolicy(FontSet base) {
        this.base = base;
    }

    private FontUIResource re(FontUIResource f) {
        if (f == null) return null;
        return new FontUIResource(new Font(FAMILY, f.getStyle(), f.getSize()));
    }

    public FontSet getFontSet(String lafName, UIDefaults table) {
        final FontSet b = base;
        return new FontSet() {
            public FontUIResource getControlFont()     { return re(b.getControlFont()); }
            public FontUIResource getMenuFont()        { return re(b.getMenuFont()); }
            public FontUIResource getTitleFont()       { return re(b.getTitleFont()); }
            public FontUIResource getWindowTitleFont() { return re(b.getWindowTitleFont()); }
            public FontUIResource getSmallFont()       { return re(b.getSmallFont()); }
            public FontUIResource getMessageFont()     { return re(b.getMessageFont()); }
        };
    }
}
