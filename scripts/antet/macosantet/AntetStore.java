package macosantet;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/* Antet deposu + contain-fit. Swing'e bağımlılığı YOK (jar'sız test edilir). */
public final class AntetStore {

    /* A4, punto (pt): 21.0x29.7 cm * 28.3464566 */
    public static final double A4_W = 595.0;
    public static final double A4_H = 842.0;

    /* Hedef piksel yoğunluğu: UDE bgImage'i sayfa imageable alanına orana sadık
     * BÜYÜTEREK çizer (wp.b.at, getImageableWidth/Height). Sayfa-pt boyutuna
     * (72 dpi) küçültmek baskıda bulanıklaştırır; 300 dpi eşdeğeri hedef alınır. */
    public static final double DPI_SCALE = 300.0 / 72.0;

    private AntetStore() {}

    /* Orana sadık, sayfanın 300 dpi eşdeğeri kutusunun İÇİNE sığdır (contain).
     * ASLA büyütme (s ≤ 1): düşük çözünürlüklü kaynak doğal kalır, yalnız
     * devasa kaynak 300 dpi'a iner. Sonuç en az 1 px. pwPt/phPt punto cinsinden. */
    public static int[] computeFit(int iw, int ih, double pwPt, double phPt) {
        double tw = pwPt * DPI_SCALE, th = phPt * DPI_SCALE;
        double s = Math.min(Math.min(tw / iw, th / ih), 1.0);
        int w = Math.max(1, (int) Math.round(iw * s));
        int h = Math.max(1, (int) Math.round(ih * s));
        return new int[] { w, h };
    }

    /* Test için system property ile yönlendirilebilir. */
    public static File dir() {
        String o = System.getProperty("macosantet.dir");
        if (o != null && !o.isEmpty()) return new File(o);
        String base = System.getenv("APPDATA");
        if (base == null || base.isEmpty()) base = System.getProperty("user.home");
        return new File(base, "UDE/Antetler");
    }

    public static boolean accepts(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
    }

    /* Ada göre sıralı; klasör yoksa boş dizi (bölüm yine çizilir). */
    public static File[] list() {
        File[] all = dir().listFiles();
        if (all == null) return new File[0];
        List<File> out = new ArrayList<File>();
        for (File f : all) if (f.isFile() && accepts(f.getName())) out.add(f);
        File[] arr = out.toArray(new File[0]);
        Arrays.sort(arr, new Comparator<File>() {
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return arr;
    }

    /* Klasöre kopyalar (ilk eklemede klasörü oluşturur); aynı ad üzerine yazar. */
    public static File add(File src) throws IOException {
        File d = dir();
        if (!d.isDirectory() && !d.mkdirs())
            throw new IOException("klasör oluşturulamadı: " + d);
        File dest = new File(d, src.getName());
        Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    public static boolean delete(File f) { return f.delete(); }

    public static String displayName(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    /* Diskten oku + sayfaya contain-fit (bicubic). Tamam yolu PNG base64
     * yazdığından ARGB güvenli. */
    public static BufferedImage loadFitted(File f, double pw, double ph) throws IOException {
        BufferedImage img = ImageIO.read(f);
        if (img == null) throw new IOException("resim okunamadı: " + f.getName());
        int[] d = computeFit(img.getWidth(), img.getHeight(), pw, ph);
        if (d[0] == img.getWidth() && d[1] == img.getHeight()) return img;
        BufferedImage out = new BufferedImage(d[0], d[1], BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(img, 0, 0, d[0], d[1], null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
