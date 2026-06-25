package com.udewin.livetoggle;

/*
 * Otomatik düzeltme seçeneklerini (Otomatik Büyük Harf / Baş Harfler Büyük /
 * Kelime Denetimi) toggle ANINDA açık belgelere uygular. Stok UDE dinleyicileri
 * yalnız editör kurulurken (text.fk.run, tercih "true" ise) taktığından
 * değişiklik bir sonraki açılışa kalıyordu.
 *
 * Obfuscate sınıflarda aynı adda birden çok üye var (z'de üç ayrı `a` alanı,
 * im'de üç ayrı `a` metodu) → kaynak-düzeyi erişim derlenemez; uygulama
 * sınıflarına TÜM erişim reflection iledir ve üyeler TİP + görünen METİN ile
 * seçilir. Bu sınıf jar classpath'i OLMADAN derlenir.
 *
 * Çağrı noktaları (LiveTogglePatch enjekte eder, hep EDT):
 *   syncSource(key, e) — toggle eyleminin başı (insertBefore)
 *   apply(key)         — toggle eyleminin sonu (insertAfter)
 */

import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Properties;

import javax.swing.JCheckBoxMenuItem;

public final class LiveToggle {

    private static final String TEXT =
        "tr.com.havelsan.uyap.system.editor.common.text.";
    private static final String PREFS =
        "tr.com.havelsan.uyap.system.pki.b.l";
    private static final String RIBBON_BOXES =
        "tr.gov.uyap.system.a.b.a.a.z";
    private static final String MENU_BOXES =
        "tr.com.havelsan.uyap.system.editor.common.gui.ak";
    private static final String ZEMBEREK = "net.zemberek.erisim.Zemberek";

    private LiveToggle() {}

    /** key → dinleyici sınıfı (FQN). */
    private static String listenerClass(String key) {
        if ("ToUpperCase".equals(key)) return TEXT + "hN";
        if ("FirstLetterUpperCase".equals(key)) return TEXT + "fY";
        return TEXT + "im";
    }

    /** key → onay kutusunun görünen metni (kutular metinle bulunur). */
    private static String boxText(String key) {
        if ("ToUpperCase".equals(key)) return "Otomatik Büyük Harf";
        if ("FirstLetterUpperCase".equals(key)) return "Baş Harfler Büyük";
        return "Kelime Denetimi";
    }

    /**
     * Toggle eyleminin BAŞI: tıklanan kutu menü kopyasıysa şeritteki kutuyu
     * ona eşitle. Orijinal gövde yeni değeri HEP şeritten okur; bu, menü
     * yolundaki eski-değer-kaydetme (upstream) bug'ını düzeltir.
     */
    public static void syncSource(String key, ActionEvent e) {
        try {
            Object s = (e == null) ? null : e.getSource();
            if (!(s instanceof JCheckBoxMenuItem)) return;
            JCheckBoxMenuItem src = (JCheckBoxMenuItem) s;
            JCheckBoxMenuItem ribbon = findBox(RIBBON_BOXES, boxText(key));
            if (ribbon != null && ribbon != src) {
                ribbon.setSelected(src.isSelected());
            }
        } catch (Throwable t) {
            /* toggle eylemi hiçbir koşulda düşmemeli */
        }
    }

    /**
     * Toggle eyleminin SONU: kalıcılaşan tercihi okur (tek doğruluk kaynağı;
     * orijinal gövde tercihi az önce yazdı), açık tüm editörlerde dinleyiciyi
     * ekler/söker, kutu kopyalarını tercihe eşitler.
     */
    public static void apply(String key) {
        try {
            boolean on = readPref(key);
            if (on && "SpellCheck".equals(key) && !zemberekReady()) {
                /* Zemberek yüklenemedi: stok fk davranışıyla aynı — sessizce
                   dinleyicisiz kal, kutuyu yine de tercihle eşitle. */
                syncBoxes(key, true);
                return;
            }
            Class<?> fiCls = Class.forName(TEXT + "fi");
            Class<?> lCls = Class.forName(listenerClass(key));
            Frame[] frames = Frame.getFrames();
            for (int i = 0; i < frames.length; i++) {
                try {
                    walk(frames[i], fiCls, lCls, on);
                } catch (Throwable perWindow) {
                    /* tek bozuk pencere kalanını engellemesin */
                }
            }
            syncBoxes(key, on);
        } catch (Throwable t) {
            /* sessiz: en kötü ihtimalle stok (restart) davranışına düşülür */
        }
    }

    /* ——— tercih okuma ——— */

    private static boolean readPref(String key) throws Exception {
        Class<?> lCls = Class.forName(PREFS);
        Object inst = null;
        Method[] ms = lCls.getDeclaredMethods();
        for (int i = 0; i < ms.length; i++) {
            Method m = ms[i];
            if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0
                    && m.getReturnType() == lCls) {
                m.setAccessible(true);
                inst = m.invoke(null);
                break;
            }
        }
        if (inst == null) return false;
        for (int i = 0; i < ms.length; i++) {
            Method m = ms[i];
            if (!Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0
                    && m.getReturnType() == Properties.class) {
                m.setAccessible(true);
                Properties p = (Properties) m.invoke(inst);
                String v = (p == null) ? null : p.getProperty(key);
                if (v != null) return Boolean.parseBoolean(v);
            }
        }
        return false;
    }

    /* ——— editör dolaşma ——— */

    private static void walk(Component c, Class<?> fiCls, Class<?> lCls,
                             boolean on) throws Exception {
        if (fiCls.isInstance(c)) setListener(c, lCls, on);
        if (c instanceof Container) {
            Component[] kids = ((Container) c).getComponents();
            for (int i = 0; i < kids.length; i++) walk(kids[i], fiCls, lCls, on);
        }
        if (c instanceof Window) {
            Window[] owned = ((Window) c).getOwnedWindows();
            for (int i = 0; i < owned.length; i++) walk(owned[i], fiCls, lCls, on);
        }
    }

    private static void setListener(Component editor, Class<?> lCls,
                                    boolean on) throws Exception {
        KeyListener[] ls = editor.getKeyListeners();
        if (on) {
            for (int i = 0; i < ls.length; i++) {
                if (ls[i].getClass() == lCls) return; /* zaten takılı */
            }
            editor.addKeyListener(
                (KeyListener) lCls.getDeclaredConstructor().newInstance());
        } else {
            for (int i = 0; i < ls.length; i++) {
                if (ls[i].getClass() == lCls) editor.removeKeyListener(ls[i]);
            }
        }
    }

    /** Zemberek hazır mı? im'in statik getter'ı TEMBEL yükler; hata → null. */
    private static boolean zemberekReady() {
        try {
            Class<?> imCls = Class.forName(TEXT + "im");
            Class<?> zCls = Class.forName(ZEMBEREK);
            Method[] ms = imCls.getDeclaredMethods();
            for (int i = 0; i < ms.length; i++) {
                Method m = ms[i];
                if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0
                        && m.getReturnType() == zCls) {
                    m.setAccessible(true);
                    return m.invoke(null) != null;
                }
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    /* ——— onay kutusu kopyaları ——— */

    private static void syncBoxes(String key, boolean on) {
        String text = boxText(key);
        String[] holders = { RIBBON_BOXES, MENU_BOXES };
        for (int i = 0; i < holders.length; i++) {
            JCheckBoxMenuItem cb = findBox(holders[i], text);
            if (cb != null && cb.isSelected() != on) cb.setSelected(on);
        }
    }

    /** holder sınıfının statik JCheckBoxMenuItem alanlarında metni eşleşeni bul. */
    private static JCheckBoxMenuItem findBox(String holder, String text) {
        try {
            Class<?> cls = Class.forName(holder);
            Field[] fs = cls.getDeclaredFields();
            for (int i = 0; i < fs.length; i++) {
                Field f = fs[i];
                if (!Modifier.isStatic(f.getModifiers())) continue;
                if (!JCheckBoxMenuItem.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                JCheckBoxMenuItem cb = (JCheckBoxMenuItem) f.get(null);
                if (cb != null && text.equals(cb.getText())) return cb;
            }
        } catch (Throwable ignore) {
        }
        return null;
    }
}
