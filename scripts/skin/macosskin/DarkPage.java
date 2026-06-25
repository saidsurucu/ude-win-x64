package macosskin;

import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;

/**
 * "Koyu belge arkaplanı" (Word koyu modu sayfası) — SKIN=1.
 * Editör bileşeninin (hj.paint) Graphics'i sarılır ve çizim renkleri
 * HSL açıklık çevirisiyle eşlenir: beyaz sayfa -> #262626, siyah metin ->
 * #E3E3E3; ton/doygunluk korunur (kırmızı metin kırmızı kalır, sarı vurgu
 * koyu sarıya iner — kontrast her bileşimde korunur). Belge modeli ve
 * baskı/PDF çıktısı DEĞİŞMEZ: isPaintingForPrint yolunda sarma yapılmaz,
 * görüntüler (drawImage) filtrelenmez. Kanvas rengi (wp.p.E) sayfalar
 * arası zemin olarak aynen bırakılır.
 * Durum java.util.prefs ile kalıcıdır; varsayılan KAPALI.
 */
public final class DarkPage {

    private static final Preferences PREFS =
        Preferences.userRoot().node("ude-win");
    private static final String KEY = "darkPageBackground";
    private static volatile Boolean on;
    private static final ConcurrentHashMap<Integer, Color> CACHE =
        new ConcurrentHashMap<Integer, Color>();

    private DarkPage() {}

    public static boolean isOn() {
        Boolean v = on;
        if (v == null) {
            v = Boolean.valueOf(PREFS.getBoolean(KEY, false));
            on = v;
        }
        return v.booleanValue();
    }

    public static void setOn(boolean v) {
        on = Boolean.valueOf(v);
        try {
            PREFS.putBoolean(KEY, v);
            PREFS.flush();
        } catch (Throwable ignore) {
        }
    }

    /** hj.paint girişinden çağrılır (SkinPatch). Print yolu sarılmaz. */
    public static Graphics wrap(javax.swing.JComponent c, Graphics g) {
        if (!isOn() || !(g instanceof Graphics2D) || g instanceof RemapG2) return g;
        try {
            if (c.isPaintingForPrint()) return g;
        } catch (Throwable ignore) {
        }
        return new RemapG2((Graphics2D) g);
    }

    /** HSL açıklık çevirisi: L' = 0.89 - 0.74*L (beyaz->0.15, siyah->0.89). */
    static Color remap(Color c) {
        if (c == null) return null;
        int argb = c.getRGB();
        try {
            Color canvas = tr.com.havelsan.uyap.system.swing.wp.p.E;
            if (canvas != null && ((canvas.getRGB() ^ argb) & 0xFFFFFF) == 0) return c;
        } catch (Throwable ignore) {
        }
        Integer key = Integer.valueOf(argb);
        Color hit = CACHE.get(key);
        if (hit != null) return hit;

        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f;
        float h = 0f, s = 0f;
        if (max != min) {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r)      h = ((g - b) / d + (g < b ? 6f : 0f)) / 6f;
            else if (max == g) h = ((b - r) / d + 2f) / 6f;
            else               h = ((r - g) / d + 4f) / 6f;
        }
        float l2 = 0.89f - 0.74f * l;
        Color out = new Color(
            (argb & 0xFF000000) | (hslToRgb(h, s, l2) & 0xFFFFFF), true);
        CACHE.put(key, out);
        return out;
    }

    static Paint remapPaint(Paint p) {
        return (p instanceof Color) ? remap((Color) p) : p;
    }

    private static int hslToRgb(float h, float s, float l) {
        float r, g, b;
        if (s == 0f) {
            r = g = b = l;
        } else {
            float q = l < 0.5f ? l * (1f + s) : l + s - l * s;
            float p = 2f * l - q;
            r = hue(p, q, h + 1f / 3f);
            g = hue(p, q, h);
            b = hue(p, q, h - 1f / 3f);
        }
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static float hue(float p, float q, float t) {
        if (t < 0f) t += 1f;
        if (t > 1f) t -= 1f;
        if (t < 1f / 6f) return p + (q - p) * 6f * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f;
        return p;
    }

    private static int clamp(float v) {
        int i = Math.round(v * 255f);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }

    /** Renk eşlemeli delegeli Graphics2D; create() türevleri de sarılı kalır. */
    private static final class RemapG2 extends Graphics2D {
        private final Graphics2D d;

        RemapG2(Graphics2D d) { this.d = d; }

        @Override public Graphics create() { return new RemapG2((Graphics2D) d.create()); }
        @Override public Graphics create(int x, int y, int w, int h) {
            return new RemapG2((Graphics2D) d.create(x, y, w, h));
        }

        @Override public void setColor(Color c) { d.setColor(remap(c)); }
        @Override public void setPaint(Paint p) { d.setPaint(remapPaint(p)); }
        @Override public void setBackground(Color c) { d.setBackground(remap(c)); }
        @Override public Color getColor() { return d.getColor(); }
        @Override public Paint getPaint() { return d.getPaint(); }
        @Override public Color getBackground() { return d.getBackground(); }

        @Override public void dispose() { d.dispose(); }
        @Override public void translate(int x, int y) { d.translate(x, y); }
        @Override public void translate(double x, double y) { d.translate(x, y); }
        @Override public void rotate(double t) { d.rotate(t); }
        @Override public void rotate(double t, double x, double y) { d.rotate(t, x, y); }
        @Override public void scale(double sx, double sy) { d.scale(sx, sy); }
        @Override public void shear(double sx, double sy) { d.shear(sx, sy); }
        @Override public void transform(AffineTransform t) { d.transform(t); }
        @Override public void setTransform(AffineTransform t) { d.setTransform(t); }
        @Override public AffineTransform getTransform() { return d.getTransform(); }

        @Override public void setPaintMode() { d.setPaintMode(); }
        @Override public void setXORMode(Color c) { d.setXORMode(c); }
        @Override public Font getFont() { return d.getFont(); }
        @Override public void setFont(Font f) { d.setFont(f); }
        @Override public FontMetrics getFontMetrics(Font f) { return d.getFontMetrics(f); }
        @Override public FontRenderContext getFontRenderContext() { return d.getFontRenderContext(); }

        @Override public Rectangle getClipBounds() { return d.getClipBounds(); }
        @Override public void clipRect(int x, int y, int w, int h) { d.clipRect(x, y, w, h); }
        @Override public void setClip(int x, int y, int w, int h) { d.setClip(x, y, w, h); }
        @Override public Shape getClip() { return d.getClip(); }
        @Override public void setClip(Shape s) { d.setClip(s); }
        @Override public void clip(Shape s) { d.clip(s); }

        @Override public void copyArea(int x, int y, int w, int h, int dx, int dy) {
            d.copyArea(x, y, w, h, dx, dy);
        }
        @Override public void drawLine(int x1, int y1, int x2, int y2) { d.drawLine(x1, y1, x2, y2); }
        @Override public void fillRect(int x, int y, int w, int h) { d.fillRect(x, y, w, h); }
        @Override public void clearRect(int x, int y, int w, int h) { d.clearRect(x, y, w, h); }
        @Override public void drawRoundRect(int x, int y, int w, int h, int aw, int ah) {
            d.drawRoundRect(x, y, w, h, aw, ah);
        }
        @Override public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) {
            d.fillRoundRect(x, y, w, h, aw, ah);
        }
        @Override public void drawOval(int x, int y, int w, int h) { d.drawOval(x, y, w, h); }
        @Override public void fillOval(int x, int y, int w, int h) { d.fillOval(x, y, w, h); }
        @Override public void drawArc(int x, int y, int w, int h, int sa, int aa) {
            d.drawArc(x, y, w, h, sa, aa);
        }
        @Override public void fillArc(int x, int y, int w, int h, int sa, int aa) {
            d.fillArc(x, y, w, h, sa, aa);
        }
        @Override public void drawPolyline(int[] xs, int[] ys, int n) { d.drawPolyline(xs, ys, n); }
        @Override public void drawPolygon(int[] xs, int[] ys, int n) { d.drawPolygon(xs, ys, n); }
        @Override public void fillPolygon(int[] xs, int[] ys, int n) { d.fillPolygon(xs, ys, n); }
        @Override public void drawPolygon(Polygon p) { d.drawPolygon(p); }
        @Override public void fillPolygon(Polygon p) { d.fillPolygon(p); }
        @Override public void draw(Shape s) { d.draw(s); }
        @Override public void fill(Shape s) { d.fill(s); }

        @Override public void drawString(String s, int x, int y) { d.drawString(s, x, y); }
        @Override public void drawString(String s, float x, float y) { d.drawString(s, x, y); }
        @Override public void drawString(AttributedCharacterIterator it, int x, int y) {
            d.drawString(it, x, y);
        }
        @Override public void drawString(AttributedCharacterIterator it, float x, float y) {
            d.drawString(it, x, y);
        }
        @Override public void drawGlyphVector(GlyphVector gv, float x, float y) {
            d.drawGlyphVector(gv, x, y);
        }

        @Override public boolean drawImage(Image img, int x, int y, ImageObserver o) {
            return d.drawImage(img, x, y, o);
        }
        @Override public boolean drawImage(Image img, int x, int y, int w, int h, ImageObserver o) {
            return d.drawImage(img, x, y, w, h, o);
        }
        @Override public boolean drawImage(Image img, int x, int y, Color bg, ImageObserver o) {
            return d.drawImage(img, x, y, remap(bg), o);
        }
        @Override public boolean drawImage(Image img, int x, int y, int w, int h, Color bg,
                ImageObserver o) {
            return d.drawImage(img, x, y, w, h, remap(bg), o);
        }
        @Override public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2,
                int sx1, int sy1, int sx2, int sy2, ImageObserver o) {
            return d.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, o);
        }
        @Override public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2,
                int sx1, int sy1, int sx2, int sy2, Color bg, ImageObserver o) {
            return d.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, remap(bg), o);
        }
        @Override public boolean drawImage(Image img, AffineTransform t, ImageObserver o) {
            return d.drawImage(img, t, o);
        }
        @Override public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
            d.drawImage(img, op, x, y);
        }
        @Override public void drawRenderedImage(RenderedImage img, AffineTransform t) {
            d.drawRenderedImage(img, t);
        }
        @Override public void drawRenderableImage(RenderableImage img, AffineTransform t) {
            d.drawRenderableImage(img, t);
        }

        @Override public boolean hit(Rectangle r, Shape s, boolean onStroke) {
            return d.hit(r, s, onStroke);
        }
        @Override public GraphicsConfiguration getDeviceConfiguration() {
            return d.getDeviceConfiguration();
        }
        @Override public void setComposite(Composite c) { d.setComposite(c); }
        @Override public Composite getComposite() { return d.getComposite(); }
        @Override public void setStroke(Stroke s) { d.setStroke(s); }
        @Override public Stroke getStroke() { return d.getStroke(); }
        @Override public void setRenderingHint(RenderingHints.Key k, Object v) {
            d.setRenderingHint(k, v);
        }
        @Override public Object getRenderingHint(RenderingHints.Key k) {
            return d.getRenderingHint(k);
        }
        @Override public void setRenderingHints(java.util.Map<?, ?> hints) {
            d.setRenderingHints(hints);
        }
        @Override public void addRenderingHints(java.util.Map<?, ?> hints) {
            d.addRenderingHints(hints);
        }
        @Override public RenderingHints getRenderingHints() { return d.getRenderingHints(); }
    }
}
