package macosskin;

import java.awt.Image;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Koyu modda ikon aydınlatma (SKIN=1).
 * İkon seti açık tema için tasarlandı; koyu zeminde görünmez oluyorlar.
 * Düşük doygunluk + düşük parlaklık pikselleri açık griye çekilir;
 * doygun-koyu vurgular (Office açık paleti: #107C41 yeşili gibi) ton
 * korunarak aydınlatılır; parlak/orta renkler ve alpha aynen korunur.
 * Yalnız macOS koyu görünümde devreye girer.
 */
public final class IconDarken {
    private IconDarken() {}

    /** İkonu mod-duyarlı dinamik görüntüyle sarar (çift sarmaya karşı korumalı).
     *  Aydınlatma artık yükleme anında değil, ModeAwareImage içinde ilk koyu
     *  çizimde yapılır — Görünüm > Renk modu canlı geçişi için. */
    public static javax.swing.ImageIcon apply(javax.swing.ImageIcon icon) {
        if (icon == null) return icon;
        try {
            Image img = icon.getImage();
            if (img instanceof ModeAwareImage) return icon;
            return new javax.swing.ImageIcon(new ModeAwareImage(img));
        } catch (Throwable t) {
            return icon;
        }
    }

    /** Görüntünün koyu-mod (aydınlatılmış) kopyası; multi-res ise varyant
     *  varyant dönüştürülür. ModeAwareImage'in koyu cache'i bunu çağırır. */
    static Image lightenImage(Image img) {
        if (img instanceof BaseMultiResolutionImage) {
            BaseMultiResolutionImage mr = (BaseMultiResolutionImage) img;
            List<Image> out = new ArrayList<>();
            for (Image v : mr.getResolutionVariants()) {
                out.add(lighten(v));
            }
            return new BaseMultiResolutionImage(out.toArray(new Image[0]));
        }
        return lighten(img);
    }

    /** Utils.a(ImageIcon,int,int) ölçekleme yolunun ModeAwareImage dalı
     *  (SkinPatch insertBefore ile bağlanır): açık varyantlar ölçeklenir,
     *  sonuç yine ModeAwareImage'e sarılır ki canlı mod geçişi hızlı erişim
     *  ikonlarında da işlesin. Kaynak ModeAwareImage değilse null döner
     *  (çağıran orijinal yola düşer). */
    public static javax.swing.ImageIcon scaleIcon(javax.swing.ImageIcon icon,
            int w, int h) {
        try {
            if (icon == null || w <= 0 || h <= 0) return null;
            Image img = icon.getImage();
            if (!(img instanceof ModeAwareImage)) return null;
            Image light = ((ModeAwareImage) img).lightImage();
            Image scaled;
            if (light instanceof BaseMultiResolutionImage) {
                BaseMultiResolutionImage mr = (BaseMultiResolutionImage) light;
                Image lo = mr.getResolutionVariant(w, h)
                    .getScaledInstance(w, h, Image.SCALE_SMOOTH);
                Image hi = mr.getResolutionVariant(w * 2, h * 2)
                    .getScaledInstance(w * 2, h * 2, Image.SCALE_SMOOTH);
                scaled = new BaseMultiResolutionImage(new Image[] { lo, hi });
            } else {
                scaled = light.getScaledInstance(w, h, Image.SCALE_SMOOTH);
            }
            return new javax.swing.ImageIcon(new ModeAwareImage(scaled));
        } catch (Throwable t) {
            return null;
        }
    }

    private static Image lighten(Image src) {
        int w = src.getWidth(null), h = src.getHeight(null);
        if (w <= 0 || h <= 0) return src;
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = bi.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = bi.getRGB(x, y);
                int out = lightenPixel(argb);
                if (out != argb) bi.setRGB(x, y, out);
            }
        }
        return bi;
    }

    /** Tek pikselin koyu-mod dönüşümü. Nötr koyu -> açık gri;
     *  doygun-koyu vurgu -> ton korunarak aydınlatılır; gerisi aynen. */
    static int lightenPixel(int argb) {
        int a = (argb >>> 24);
        if (a == 0) return argb;
        int r = (argb >> 16) & 0xFF, gg = (argb >> 8) & 0xFF, b2 = argb & 0xFF;
        float[] hsb = java.awt.Color.RGBtoHSB(r, gg, b2, null);
        if (hsb[1] < 0.35f && hsb[2] < 0.6f) {
            // koyu nötr glif -> açık gri (parlaklık ters çevrilir, hafif kısılır)
            int v = Math.round((1f - hsb[2]) * 205f) + 50; // 50..255
            if (v > 235) v = 235;
            int rgb = (v << 16) | (v << 8) | v;
            return (a << 24) | rgb;
        }
        if (hsb[1] >= 0.35f && hsb[2] < 0.55f) {
            // doygun-koyu vurgu (Office açık paleti) -> koyu zemin için aydınlat
            int rgb = java.awt.Color.HSBtoRGB(hsb[0], hsb[1] * 0.85f, 0.72f) & 0xFFFFFF;
            return (a << 24) | rgb;
        }
        return argb;
    }
}
