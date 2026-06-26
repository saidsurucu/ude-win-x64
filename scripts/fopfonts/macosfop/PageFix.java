package macosfop;

import java.awt.print.PageFormat;
import java.awt.print.Paper;

/**
 * UDE'nin iText tabanlı PDF dışa aktarımında (editor.b.b) sayfanın alttan kesilmesini
 * (footer çıkmaması) giderir.
 *
 * Sorun: b.b, çıktı sayfa boyutunu  at.getPageFormat(i)  PageFormat'ından
 *   genişlik = imageableWidth + 2*imageableX
 *   yükseklik = imageableHeight + 2*imageableY
 * ile hesaplar; yani ÜST kenar = ALT kenar varsayar. Bazı macOS'larda varsayılan
 * yazıcı/kâğıt ayarı asimetrik kenar verir (alt kenar üstten büyük) → yükseklik
 * eksik hesaplanır (ör. A4'te 841.89 yerine ~771) → çıktı/şablon kısa olur ve
 * sayfanın altı (footer) kırpılır. Belge aslında A4'tür (UYAP standardı).
 *
 * Çözüm: at.getPageFormat sonucu A4'e sabitlenir — kâğıt A4, kullanılabilir alan üst/sol
 * kenar korunarak simetrik (tam A4) yapılır. Böylece hem boyut hesabı hem de
 * Printable.print aynı, tam A4 sayfayı kullanır; footer kesilmez. (Yatay/landscape
 * belgelerde davranış değiştirilmez — UYAP belgeleri dik/portrait'tir.)
 *
 * try/catch ile sarılı: hata olursa orijinal PageFormat döner, davranış bozulmaz.
 */
public final class PageFix {

    // A4, punto (72 dpi): 210mm x 297mm
    private static final double A4W = 595.275591;
    private static final double A4H = 841.889764;

    private PageFix() {}

    /** b.b'deki at.getPageFormat(i) çağrısını sarar; sonucu A4'e sabitler. */
    public static PageFormat a4(PageFormat pf) {
        try {
            if (pf == null) return null;
            // Yalnızca dik (portrait) sayfaları zorla; yatay belgeleri dokunmadan geç.
            if (pf.getOrientation() != PageFormat.PORTRAIT) return pf;

            double ix = pf.getImageableX();
            double iy = pf.getImageableY();
            // Aşırı/anormal kenarları makulleştir (A4 sınırları içinde kalsın).
            if (ix < 0 || ix > A4W / 2) ix = 0;
            if (iy < 0 || iy > A4H / 2) iy = 0;

            Paper paper = new Paper();
            paper.setSize(A4W, A4H);
            // Üst/sol kenarı koru; alt/sağ kenarı simetrik yap → tam A4 kullanılabilir alan.
            paper.setImageableArea(ix, iy, A4W - 2 * ix, A4H - 2 * iy);

            PageFormat np = new PageFormat();
            np.setOrientation(PageFormat.PORTRAIT);
            np.setPaper(paper);
            return np;
        } catch (Throwable t) {
            return pf;
        }
    }
}
