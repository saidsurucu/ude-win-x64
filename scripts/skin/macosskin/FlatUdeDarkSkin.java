package macosskin;

import org.jvnet.substance.painter.decoration.DecorationAreaType;

/**
 * UDE koyu skin (SKIN=1 + macOS koyu görünüm).
 * Açık skin yalnız NONE alanını override eder; koyu skinde Nebula'nın açık
 * varsayılanları diğer dekorasyon alanlarında sırıtmasın diye bundle
 * GENERAL/HEADER/FOOTER/TOOLBAR alanlarına da kaydedilir.
 */
public class FlatUdeDarkSkin extends FlatUdeSkin {
    public FlatUdeDarkSkin() {
        super("/macosskin/flatude-dark.colorschemes", "FlatUdeDark",
              new DecorationAreaType[] {
                  DecorationAreaType.NONE,
                  DecorationAreaType.GENERAL,
                  DecorationAreaType.HEADER,
                  DecorationAreaType.FOOTER,
                  DecorationAreaType.TOOLBAR });
    }

    public String getDisplayName() {
        return "FlatUdeDark";
    }
}
