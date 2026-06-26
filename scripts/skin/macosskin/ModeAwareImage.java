package macosskin;

import java.awt.Image;
import java.awt.image.AbstractMultiResolutionImage;
import java.awt.image.MultiResolutionImage;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Çizim ANINDA renk moduna göre açık/koyu varyant seçen + Windows KESİRLİ ekran
 * ölçeğinde (örn. %150) KESKİN render eden görüntü (SKIN=1).
 *
 * Sorun: Mac Retina = 2x (tam kat) → @2x birebir kullanılır. Windows %125/150/175
 * kesirlidir; klasik multi-res, en yakın varyantı (1x ya da 2x) seçip AWT ile
 * yeniden ölçekler (çift ölçek + denetimsiz interpolasyon) → ince Fluent çizgileri
 * bulanık/bloklu. Çözüm: getResolutionVariant CİHAZ piksel boyutuyla çağrılır; biz
 * EN YÜKSEK çözünürlüklü kaynaktan TAM o boyuta tek bicubic render edip döneriz
 * (boyuta göre cache). AWT bunu 1:1 çizer → her ölçekte keskin. Mantıksal boyut
 * (getBaseImage) değişmez → yerleşim bozulmaz.
 */
public final class ModeAwareImage extends AbstractMultiResolutionImage {
    private final Image light;
    private volatile Image dark;
    private final ConcurrentHashMap<Long, Image> cache = new ConcurrentHashMap<Long, Image>();

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

    /** Kaynağın EN YÜKSEK çözünürlüklü varyantı (genelde @2x). */
    private static Image best(Image p) {
        if (p instanceof MultiResolutionImage) {
            List<Image> v = ((MultiResolutionImage) p).getResolutionVariants();
            if (!v.isEmpty()) return v.get(v.size() - 1);
        }
        return p;
    }

    /** Cihaz boyutuna (w,h) TAM uyan varyant (ölçeksiz çizim = keskin); yoksa null. */
    private static Image exactVariant(Image p, int w, int h) {
        if (p instanceof MultiResolutionImage) {
            for (Image v : ((MultiResolutionImage) p).getResolutionVariants()) {
                if (v.getWidth(null) == w && v.getHeight(null) == h) return v;
            }
        }
        return null;
    }

    /** Mantıksal (en küçük/1x) varyant — ikon boyutunu bildirir. */
    private static Image smallest(Image p) {
        if (p instanceof MultiResolutionImage) {
            List<Image> v = ((MultiResolutionImage) p).getResolutionVariants();
            if (!v.isEmpty()) return v.get(0);
        }
        return p;
    }

    @Override
    protected Image getBaseImage() {
        return smallest(pick());
    }

    @Override
    public Image getResolutionVariant(double destWidth, double destHeight) {
        Image p = pick();
        int w = (int) Math.round(destWidth);
        int h = (int) Math.round(destHeight);
        if (w <= 0 || h <= 0) return smallest(p);
        // Cihaz boyutuna TAM uyan varyant varsa onu kullan (ölçeksiz = pixel-keskin);
        // yoksa en yüksek kaynaktan tek bicubic. @1.5x asset seti, kesirli ölçekte
        // (örn. %150 → 30px) tam-uyan varyant sağlayıp keskinlik verir.
        Image exact = exactVariant(p, w, h);
        if (exact != null) return exact;
        Image b = best(p);
        if (b.getWidth(null) == w && b.getHeight(null) == h) return b;
        boolean isDark = (p != light);
        long key = ((isDark ? 1L : 0L) << 42) | ((long) w << 21) | (h & 0x1FFFFFL);
        Image cached = cache.get(key);
        if (cached != null) return cached;
        if (cache.size() > 64) cache.clear();   // sınırlı cache (birkaç ölçek/boyut)
        Image scaled = IconDarken.bicubic(b, w, h);   // @2x kaynaktan TAM cihaz boyutuna tek bicubic
        cache.put(key, scaled);
        return scaled;
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
