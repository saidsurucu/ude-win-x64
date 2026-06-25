package macosskin;

import java.awt.Image;
import java.awt.image.AbstractMultiResolutionImage;
import java.awt.image.MultiResolutionImage;
import java.util.Collections;
import java.util.List;

/**
 * Çizim ANINDA renk moduna göre açık/koyu varyant seçen görüntü (SKIN=1).
 * IconDarken eskiden ikonu yükleme anında kalıcı dönüştürürdü; canlı mod
 * geçişi (Görünüm > Renk modu) için karar paint zamanına taşındı: drawImage
 * her çağrıda getResolutionVariant'a sorar, biz de DarkMode.isDark()'a göre
 * orijinal ya da aydınlatılmış varyantları döndürürüz. Koyu varyantlar ilk
 * koyu çizimde üretilip cache'lenir; boyutlar iki modda da aynıdır
 * (ImageIcon ctor ölçüleri güvenle saklayabilir).
 */
public final class ModeAwareImage extends AbstractMultiResolutionImage {
    private final Image light;
    private volatile Image dark;

    public ModeAwareImage(Image light) {
        this.light = light;
    }

    /** Açık (orijinal) görüntü; ölçekleme yolu bunun üzerinden yeni
     *  ModeAwareImage kurar. */
    public Image lightImage() {
        return light;
    }

    private Image darkImage() {
        Image d = dark;
        if (d == null) {
            synchronized (this) {
                if (dark == null) {
                    dark = IconDarken.lightenImage(light);
                }
                d = dark;
            }
        }
        return d;
    }

    private Image pick() {
        return DarkMode.isDark() ? darkImage() : light;
    }

    @Override
    protected Image getBaseImage() {
        Image p = pick();
        if (p instanceof MultiResolutionImage) {
            List<Image> v = ((MultiResolutionImage) p).getResolutionVariants();
            if (!v.isEmpty()) return v.get(0);
        }
        return p;
    }

    @Override
    public Image getResolutionVariant(double destWidth, double destHeight) {
        Image p = pick();
        if (p instanceof MultiResolutionImage) {
            return ((MultiResolutionImage) p)
                .getResolutionVariant(destWidth, destHeight);
        }
        return p;
    }

    @Override
    public List<Image> getResolutionVariants() {
        Image p = pick();
        if (p instanceof MultiResolutionImage) {
            return ((MultiResolutionImage) p).getResolutionVariants();
        }
        return Collections.singletonList(p);
    }
}
