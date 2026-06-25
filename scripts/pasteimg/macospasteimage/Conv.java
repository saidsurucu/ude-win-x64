package macospasteimage;

import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.util.List;

/**
 * macOS pano imajı dönüştürücüsü. hj.paste() içindeki "checkcast BufferedImage"
 * yerine geçer: macOS JDK'sı imageFlavor için MultiResolutionCachedImage
 * döndürdüğünden orijinal cast ClassCastException ile ölür ve imaj yapıştırma
 * hiç çalışmaz. Bu sınıf her java.awt.Image'ı BufferedImage'a güvenle çevirir;
 * Retina (multi-resolution) imajlarda EN BÜYÜK piksel varyantı seçilir
 * (tam çözünürlük gömme — IMGFULL felsefesiyle tutarlı). İmaj olmayan giriş
 * orijinal checkcast semantiğini korur (null geçer, diğerleri CCE).
 */
public final class Conv {
    private Conv() {}

    public static BufferedImage toBuffered(Object o) {
        if (o instanceof BufferedImage) {
            return (BufferedImage) o;
        }
        if (o instanceof MultiResolutionImage) {
            Image best = null;
            long bestPx = -1L;
            List<Image> variants = ((MultiResolutionImage) o).getResolutionVariants();
            for (Image v : variants) {
                load(v);
                long px = (long) v.getWidth(null) * (long) v.getHeight(null);
                if (px > bestPx) { bestPx = px; best = v; }
            }
            if (best instanceof BufferedImage) return (BufferedImage) best;
            if (best != null) return draw(best);
        }
        if (o instanceof Image) {
            return draw((Image) o);
        }
        return (BufferedImage) o;
    }

    private static void load(Image img) {
        if (img.getWidth(null) > 0 && img.getHeight(null) > 0) return;
        MediaTracker mt = new MediaTracker(new Container());
        mt.addImage(img, 0);
        try {
            mt.waitForID(0);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        mt.removeImage(img);
    }

    private static BufferedImage draw(Image img) {
        load(img);
        int w = img.getWidth(null);
        int h = img.getHeight(null);
        if (w <= 0 || h <= 0) {
            throw new ClassCastException(
                "pano imajının boyutu belirlenemedi: " + img.getClass().getName());
        }
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        try {
            g.drawImage(img, 0, 0, null);
        } finally {
            g.dispose();
        }
        return bi;
    }
}
