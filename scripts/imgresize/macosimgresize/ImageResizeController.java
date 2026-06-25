package macosimgresize;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.awt.print.PageFormat;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Satır-içi imajları köşe tutamaçlarıyla boyutlandırma denetçisi (serbest;
 * Shift basılıyken en/boy oranı kilitli).
 * hj.processMouseEvent / processMouseMotionEvent başından intercept(), hj.paint
 * sonundan paintOverlay() çağrılır (build-time Javassist enjeksiyonu).
 *
 * UDE sınıflarına YANSIMA ile erişilir (hj.a() dönüş-tipi aşırı yüklemesi javac'ı
 * engeller; ayrıca bağımsız derleme sürüm kaymasında derleme kırılmasını önler):
 *   - wp.model.T.d/g(MutableAttributeSet,float): width/height attribute yazımı
 *   - hj üzerinde parametresiz, PageFormat döndüren public metot: sayfa formatı
 * Yansıma çözülemezse özellik sessizce devre dışı kalır (yarım-yama felsefesi).
 */
public final class ImageResizeController {

    private static final int HANDLE = 7;      // tutamaç karesi kenarı (px)
    private static final int HIT = 10;        // tutamaç vuruş yarıçapı (px)
    private static final float MIN_PT = 10f;  // stok diyalogla aynı alt sınır

    private static final boolean DEBUG = "1".equals(System.getenv("UDE_IMGRESIZE_DEBUG"));

    private static final Map<JTextComponent, State> STATES = new WeakHashMap<JTextComponent, State>();

    private static Method mSetWidth;   // T.d(MutableAttributeSet,float)
    private static Method mSetHeight;  // T.g(MutableAttributeSet,float)
    private static Method mGetWidth;   // T.c(AttributeSet)float
    private static Method mGetHeight;  // T.f(AttributeSet)float
    private static boolean reflectionOk;

    static {
        try {
            Class<?> t = Class.forName("tr.com.havelsan.uyap.system.swing.wp.model.T");
            mSetWidth = t.getMethod("d", MutableAttributeSet.class, float.class);
            mSetHeight = t.getMethod("g", MutableAttributeSet.class, float.class);
            mGetWidth = staticMethod(t, "c", float.class, AttributeSet.class);
            mGetHeight = staticMethod(t, "f", float.class, AttributeSet.class);
            reflectionOk = true;
        } catch (Throwable ex) {
            reflectionOk = false;
            log("yansıma kurulamadı, özellik kapalı: " + ex);
        }
    }

    /** İsim + parametre + dönüş tipi eşleyen statik metot (dönüş-tipi aşırı yüklemeleri için). */
    private static Method staticMethod(Class<?> owner, String name, Class<?> ret, Class<?>... params) throws NoSuchMethodException {
        for (Method m : owner.getMethods()) {
            if (m.getName().equals(name) && m.getReturnType() == ret
                    && java.util.Arrays.equals(m.getParameterTypes(), params)) {
                return m;
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name + " -> " + ret.getName());
    }

    private ImageResizeController() {}

    /** Bileşen başına durum: boşta -> seçili -> sürüklüyor. */
    private static final class State {
        Element selected;          // seçili imaj elementi (belge değişiminde sıfırlanır)
        boolean listenersInstalled;
        boolean cursorOverridden;
        javax.swing.text.Document doc;  // listener'ların takılı olduğu belge
        // sürükleme durumu:
        int handle = -1;           // 0=NW 1=NE 2=SW 3=SE
        boolean swallowNextClick;  // onReleased sonrası gelen CLICKED olayını yut
        Rectangle base;            // sürükleme başındaki görünen sınırlar (px)
        Rectangle preview;         // kesikli çerçeve (px)
        double scaleX = 1.0;       // yatay önizleme ölçeği (commit'te kullanılır)
        double scaleY = 1.0;       // dikey önizleme ölçeği
    }

    // ---- enjeksiyon noktaları -------------------------------------------------

    /** true dönerse hj olayı tüketir (super.processMouse*Event çağrılmaz). */
    public static boolean intercept(Object component, MouseEvent e) {
        try {
            if (!reflectionOk || !(component instanceof JTextComponent)) return false;
            JTextComponent c = (JTextComponent) component;
            if (!c.isEditable() || !c.isEnabled()) return false;
            State s = state(c);
            switch (e.getID()) {
                case MouseEvent.MOUSE_PRESSED:  return onPressed(c, s, e);
                case MouseEvent.MOUSE_DRAGGED:  return onDragged(c, s, e);
                case MouseEvent.MOUSE_RELEASED: return onReleased(c, s, e);
                case MouseEvent.MOUSE_MOVED:    return onMoved(c, s, e);
                case MouseEvent.MOUSE_CLICKED:  // drag bitişindeki click'i yut
                    if (s.swallowNextClick) { s.swallowNextClick = false; return true; }
                    return false;
                default: return false;
            }
        } catch (Throwable ex) {
            log("intercept hata: " + ex);
            return false;
        }
    }

    /** hj.paint sonunda çağrılır: tutamaçlar + sürükleme çerçevesi. */
    public static void paintOverlay(Object component, Graphics g) {
        try {
            if (!reflectionOk || !(component instanceof JTextComponent)) return;
            JTextComponent c = (JTextComponent) component;
            State s = STATES.get(c);
            if (s == null || s.selected == null || !c.isEditable()) return;
            if (!elementCurrent(c, s.selected)) { s.selected = null; s.handle = -1; return; }
            Rectangle b = boundsOf(c, s.selected);
            if (b == null) { s.selected = null; s.handle = -1; return; }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                // 4 köşe tutamacı: beyaz dolgu + koyu çerçeve
                for (Point p : corners(b)) {
                    g2.setColor(Color.WHITE);
                    g2.fillRect(p.x - HANDLE / 2, p.y - HANDLE / 2, HANDLE, HANDLE);
                    g2.setColor(new Color(0x33, 0x33, 0x33));
                    g2.drawRect(p.x - HANDLE / 2, p.y - HANDLE / 2, HANDLE, HANDLE);
                }
                if (s.handle >= 0 && s.preview != null) {
                    g2.setColor(new Color(0x33, 0x33, 0x33));
                    g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT,
                            BasicStroke.JOIN_MITER, 10f, new float[]{4f, 4f}, 0f));
                    g2.drawRect(s.preview.x, s.preview.y, s.preview.width, s.preview.height);
                }
            } finally {
                g2.dispose();
            }
        } catch (Throwable ex) {
            log("paintOverlay hata: " + ex);
        }
    }

    // ---- olay işleyiciler -----------------------------------------------------

    private static boolean onPressed(JTextComponent c, State s, MouseEvent e) {
        s.swallowNextClick = false;
        if (e.getButton() != MouseEvent.BUTTON1 || e.isPopupTrigger()) return false;
        // 1) tutamaç üzerinde mi? -> sürükleme başlat, olayı tüket (caret oynamasın)
        if (s.selected != null) {
            Rectangle b = boundsOf(c, s.selected);
            if (b != null) {
                int h = handleAt(b, e.getPoint());
                if (h >= 0) {
                    s.handle = h;
                    s.base = b;
                    s.preview = new Rectangle(b);
                    s.scaleX = 1.0;
                    s.scaleY = 1.0;
                    return true;
                }
            }
        }
        // 2) imaja tıklama mı? -> seç (olay tüketilmez: caret normal davranır)
        Element img = imageElementAt(c, e.getPoint());
        if (img != s.selected) {
            s.selected = img;
            if (img != null) installListeners(c, s);
            c.repaint();
        }
        return false;
    }

    private static boolean onDragged(JTextComponent c, State s, MouseEvent e) {
        if (s.handle < 0) return false;
        Rectangle b = s.base;
        // sabit köşe = tutulan köşenin karşısı
        Point fixed = corners(b)[3 - s.handle];
        double sx = Math.abs(e.getX() - fixed.x) / (double) b.width;
        double sy = Math.abs(e.getY() - fixed.y) / (double) b.height;
        if (e.isShiftDown()) {
            // oran kilidi: baskın eksen, iki eksenin sınırına birlikte kırpılır
            double scale = clampScale(c, s, Math.max(sx, sy));
            sx = scale;
            sy = scale;
        } else {
            sx = clampAxis(c, s, sx, true);
            sy = clampAxis(c, s, sy, false);
        }
        int w = Math.max(1, (int) Math.round(b.width * sx));
        int h = Math.max(1, (int) Math.round(b.height * sy));
        // imaj yön değiştirmez: çerçeve, sabit köşeden orijinal yöne uzanır
        int x = (s.handle == 1 || s.handle == 3) ? b.x : b.x + b.width - w;   // NE/SE: sol sabit
        int y = (s.handle == 2 || s.handle == 3) ? b.y : b.y + b.height - h;  // SW/SE: üst sabit
        s.scaleX = sx;
        s.scaleY = sy;
        s.preview = new Rectangle(x, y, w, h);
        c.repaint();
        return true;
    }

    private static boolean onReleased(JTextComponent c, State s, MouseEvent e) {
        if (s.handle < 0) return false;
        s.handle = -1;
        s.swallowNextClick = true;
        boolean hadPreview = s.preview != null;
        s.preview = null;
        if (hadPreview && s.selected != null
                && (Math.abs(s.scaleX - 1.0) > 0.005 || Math.abs(s.scaleY - 1.0) > 0.005)) {
            commit(c, s.selected, s.scaleX, s.scaleY);
        }
        c.repaint();
        return true;
    }

    private static boolean onMoved(JTextComponent c, State s, MouseEvent e) {
        if (s.selected == null) return false;
        Rectangle b = boundsOf(c, s.selected);
        int h = (b == null) ? -1 : handleAt(b, e.getPoint());
        if (h >= 0) {
            int[] cur = {Cursor.NW_RESIZE_CURSOR, Cursor.NE_RESIZE_CURSOR,
                         Cursor.SW_RESIZE_CURSOR, Cursor.SE_RESIZE_CURSOR};
            c.setCursor(Cursor.getPredefinedCursor(cur[h]));
            s.cursorOverridden = true;
            return true;   // tüket: başka kod imleci geri çevirmesin
        }
        if (s.cursorOverridden) {
            c.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
            s.cursorOverridden = false;
        }
        return false;
    }

    // ---- yardımcılar ----------------------------------------------------------

    /** Seçili element hâlâ bileşenin GÜNCEL belgesine mi ait? (setDocument swap guard'ı) */
    private static boolean elementCurrent(JTextComponent c, Element el) {
        return el != null && el.getDocument() == c.getDocument();
    }

    private static State state(JTextComponent c) {
        State s = STATES.get(c);
        if (s == null) { s = new State(); STATES.put(c, s); }
        return s;
    }

    /** Belge yapısı değişince / odak kaybında seçimi bırak; belge swap'ında yeniden kur. */
    private static void installListeners(final JTextComponent c, final State s) {
        javax.swing.text.Document d = c.getDocument();
        if (s.doc != d) {
            s.doc = d;
            d.addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { clear(); }
                public void removeUpdate(DocumentEvent e) { clear(); }
                public void changedUpdate(DocumentEvent e) { c.repaint(); } // attribute değişimi: seçim kalsın
                private void clear() {
                    if (s.selected != null) { s.selected = null; s.handle = -1; c.repaint(); }
                }
            });
        }
        if (s.listenersInstalled) return;
        s.listenersInstalled = true;
        c.addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) {
                if (s.selected != null) { s.selected = null; s.handle = -1; c.repaint(); }
            }
        });
    }

    /** Noktadaki yaprak elementi imajsa döndürür (sağ yarıya tıklamada pos-1'e de bakar). */
    private static Element imageElementAt(JTextComponent c, Point p) {
        try {
            int pos = c.viewToModel(p);
            if (pos < 0) return null;
            DefaultStyledDocument doc = (DefaultStyledDocument) c.getDocument();
            Element el = doc.getCharacterElement(pos);
            if (!isImage(el) && pos > 0) el = doc.getCharacterElement(pos - 1);
            if (!isImage(el)) return null;
            Rectangle b = boundsOf(c, el);
            return (b != null && b.contains(p)) ? el : null;
        } catch (Throwable ex) {
            log("imageElementAt hata: " + ex);
            return null;
        }
    }

    private static boolean isImage(Element el) {
        if (el == null) return false;
        Object en = el.getAttributes().getAttribute("$ename");
        return en != null && "image".equals(en.toString());
    }

    /**
     * İmajın görünen sınırları (bileşen koordinatı, px).
     * Birincil strateji: modelToView(start)/(end) dikdörtgenleri (end aynı görsel
     * satırdaysa — tolerant kontrol). end sonraki satıra sarmışsa width/height
     * attribute'larından genişlik türetilir; o da yoksa null
     * (tutamaç gösterilmez — güvenli geri çekilme).
     */
    private static Rectangle boundsOf(JTextComponent c, Element el) {
        try {
            Rectangle r0 = c.modelToView(el.getStartOffset());
            Rectangle r1 = c.modelToView(el.getEndOffset());
            if (r0 == null || r1 == null) return null;
            int h = r0.height;
            int w;
            if (r1.y >= r0.y && r1.y < r0.y + r0.height) {
                w = r1.x - r0.x;
            } else {
                float aw = getFloat(mGetWidth, el.getAttributes());
                float ah = getFloat(mGetHeight, el.getAttributes());
                if (aw > 0 && ah > 0) w = Math.round(h * (aw / ah));
                else return null;
            }
            if (w <= 0 || h <= 0) return null;
            return new Rectangle(r0.x, r0.y, w, h);
        } catch (Throwable ex) {
            return null;
        }
    }

    private static Point[] corners(Rectangle b) {
        return new Point[]{
            new Point(b.x, b.y),                          // 0 NW
            new Point(b.x + b.width, b.y),                // 1 NE
            new Point(b.x, b.y + b.height),               // 2 SW
            new Point(b.x + b.width, b.y + b.height)};    // 3 SE
    }

    private static int handleAt(Rectangle b, Point p) {
        Point[] cs = corners(b);
        for (int i = 0; i < 4; i++) {
            if (Math.abs(p.x - cs[i].x) <= HIT && Math.abs(p.y - cs[i].y) <= HIT) return i;
        }
        return -1;
    }

    /** Ortak ölçeği [min 10pt, sayfa basılabilir alanı] aralığına kırpar (oran kilitliyken). */
    private static double clampScale(JTextComponent c, State s, double scale) {
        double baseWpt = basePt(s, true), baseHpt = basePt(s, false);
        PageFormat pf = pageFormat(c);
        if (pf != null) {
            scale = Math.min(scale, Math.min(pf.getImageableWidth() / baseWpt,
                                             pf.getImageableHeight() / baseHpt));
        }
        scale = Math.max(scale, Math.max(MIN_PT / baseWpt, MIN_PT / baseHpt));
        return scale;
    }

    /** Tek eksenin ölçeğini [min 10pt, sayfanın o eksendeki basılabilir alanı] aralığına kırpar. */
    private static double clampAxis(JTextComponent c, State s, double scale, boolean width) {
        double basePt = basePt(s, width);
        PageFormat pf = pageFormat(c);
        if (pf != null) {
            scale = Math.min(scale, (width ? pf.getImageableWidth() : pf.getImageableHeight()) / basePt);
        }
        return Math.max(scale, MIN_PT / basePt);
    }

    /**
     * Taban boyutun punto karşılığı: attribute varsa o (zoom'dan bağımsız, kesin);
     * yoksa px ölçüsü punto varsayılır (zoom=1'de 1px≈1pt — spec'teki bilinen risk).
     */
    private static double basePt(State s, boolean width) {
        Element el = s.selected;
        if (el != null) {
            float v = getFloat(width ? mGetWidth : mGetHeight, el.getAttributes());
            if (v > 0) return v;
        }
        return width ? s.base.width : s.base.height;
    }

    private static float getFloat(Method getter, AttributeSet attrs) {
        try {
            Object v = getter.invoke(null, attrs);
            return (v instanceof Number) ? ((Number) v).floatValue() : Float.NEGATIVE_INFINITY;
        } catch (Throwable ex) {
            return Float.NEGATIVE_INFINITY;
        }
    }

    /** Yeni width/height attribute'larını tek undo adımıyla uygular (gui.jD yolu). */
    private static void commit(JTextComponent c, Element el, double scaleX, double scaleY) {
        try {
            if (!elementCurrent(c, el)) return;
            State s = STATES.get(c);
            double wPt = basePt(s, true) * scaleX;
            double hPt = basePt(s, false) * scaleY;
            PageFormat pf = pageFormat(c);
            if (pf != null) {
                wPt = Math.min(wPt, pf.getImageableWidth());
                hPt = Math.min(hPt, pf.getImageableHeight());
            }
            wPt = Math.max(wPt, MIN_PT);
            hPt = Math.max(hPt, MIN_PT);
            SimpleAttributeSet sas = new SimpleAttributeSet();
            mSetWidth.invoke(null, sas, (float) wPt);
            mSetHeight.invoke(null, sas, (float) hPt);
            DefaultStyledDocument doc = (DefaultStyledDocument) c.getDocument();
            int start = el.getStartOffset();
            doc.setCharacterAttributes(start, el.getEndOffset() - start, sas, false);
            log("commit: " + (int) wPt + "x" + (int) hPt + "pt (scaleX=" + scaleX + " scaleY=" + scaleY + ")");
        } catch (Throwable ex) {
            log("commit hata: " + ex);
        }
    }

    /** hj üzerindeki parametresiz PageFormat metodu (a() — yansımayla; bulunamazsa null). */
    private static PageFormat pageFormat(JTextComponent c) {
        try {
            for (Method m : c.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == PageFormat.class) {
                    return (PageFormat) m.invoke(c);
                }
            }
        } catch (Throwable ex) {
            log("pageFormat hata: " + ex);
        }
        return null;
    }

    /** System.err UDE'de yutulur (bilinen tuzak) — dosyaya logla. */
    private static void log(String msg) {
        if (!DEBUG) return;
        try (PrintWriter w = new PrintWriter(new FileWriter(
                System.getProperty("user.home") + "/Library/Logs/ude-imgresize.log", true))) {
            w.println(new java.util.Date() + " " + msg);
        } catch (Throwable ignored) {
        }
    }
}
