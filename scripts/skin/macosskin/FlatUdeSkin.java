package macosskin;

import org.jvnet.substance.painter.border.FlatBorderPainter;
import org.jvnet.substance.painter.gradient.FlatGradientPainter;
import org.jvnet.substance.skin.NebulaSkin;

/**
 * UDE modern düz açık skin (SKIN=1).
 * Nebula tabanlı: super() tüm zorunlu skin alanlarını (buttonShaper,
 * decorationPainter, watermark, geçerli şema bundle'ları) kurar.
 * Biz yalnızca gradient/border painter'ları DÜZ (gradient'siz) yaparız.
 * Şema yükleme/kayıt mantığı parametreli ctor'da: FlatUdeDarkSkin aynı
 * reçeteyi koyu kaynak + ek dekorasyon alanlarıyla kullanır.
 */
public class FlatUdeSkin extends NebulaSkin {
    /** setSkin(String) sarmasında yeniden-giriş koruması için. */
    public static boolean installing = false;

    public FlatUdeSkin() {
        this("/macosskin/flatude.colorschemes", "FlatUde",
             new org.jvnet.substance.painter.decoration.DecorationAreaType[] {
                 org.jvnet.substance.painter.decoration.DecorationAreaType.NONE });
    }

    protected FlatUdeSkin(String resource, String prefix,
            org.jvnet.substance.painter.decoration.DecorationAreaType[] areas) {
        super();
        this.gradientPainter = new FlatGradientPainter();
        this.borderPainter = new FlatBorderPainter();
        this.highlightBorderPainter = new FlatBorderPainter();

        java.net.URL u = FlatUdeSkin.class.getResource(resource);
        if (u != null) {
            java.util.Map schemes = org.jvnet.substance.api.SubstanceSkin.getColorSchemes(u);
            if (schemes != null) {
                org.jvnet.substance.api.SubstanceColorScheme active =
                    (org.jvnet.substance.api.SubstanceColorScheme) schemes.get(prefix + " Active");
                org.jvnet.substance.api.SubstanceColorScheme def =
                    (org.jvnet.substance.api.SubstanceColorScheme) schemes.get(prefix + " Default");
                org.jvnet.substance.api.SubstanceColorScheme dis =
                    (org.jvnet.substance.api.SubstanceColorScheme) schemes.get(prefix + " Disabled");
                org.jvnet.substance.api.SubstanceColorScheme rollUnsel =
                    (org.jvnet.substance.api.SubstanceColorScheme) schemes.get(prefix + " Rollover Unselected");
                org.jvnet.substance.api.SubstanceColorScheme rollSel =
                    (org.jvnet.substance.api.SubstanceColorScheme) schemes.get(prefix + " Rollover Selected");
                org.jvnet.substance.api.SubstanceColorScheme pressed =
                    (org.jvnet.substance.api.SubstanceColorScheme) schemes.get(prefix + " Pressed");
                if (active != null && def != null && dis != null
                        && rollUnsel != null && rollSel != null && pressed != null) {
                    org.jvnet.substance.api.SubstanceColorSchemeBundle bundle =
                        new org.jvnet.substance.api.SubstanceColorSchemeBundle(active, def, dis);
                    bundle.registerColorScheme(rollUnsel,
                        org.jvnet.substance.api.ComponentState.ROLLOVER_UNSELECTED);
                    bundle.registerColorScheme(rollSel,
                        org.jvnet.substance.api.ComponentState.ROLLOVER_SELECTED);
                    bundle.registerColorScheme(pressed,
                        org.jvnet.substance.api.ComponentState.PRESSED_SELECTED,
                        org.jvnet.substance.api.ComponentState.PRESSED_UNSELECTED,
                        org.jvnet.substance.api.ComponentState.ARMED,
                        org.jvnet.substance.api.ComponentState.ROLLOVER_ARMED);
                    bundle.registerHighlightColorScheme(rollUnsel, 0.6f,
                        org.jvnet.substance.api.ComponentState.ROLLOVER_UNSELECTED);
                    bundle.registerHighlightColorScheme(active, 0.8f,
                        org.jvnet.substance.api.ComponentState.SELECTED);
                    bundle.registerHighlightColorScheme(rollSel, 0.95f,
                        org.jvnet.substance.api.ComponentState.ROLLOVER_SELECTED);
                    bundle.registerHighlightColorScheme(pressed, 0.8f,
                        org.jvnet.substance.api.ComponentState.ARMED,
                        org.jvnet.substance.api.ComponentState.ROLLOVER_ARMED);
                    for (int i = 0; i < areas.length; i++) {
                        this.registerDecorationAreaSchemeBundle(bundle, areas[i]);
                    }
                }
            }
        }
    }

    public String getDisplayName() {
        return "FlatUde";
    }
}
