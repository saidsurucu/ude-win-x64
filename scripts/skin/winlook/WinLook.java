package winlook;

/*
 * UDE Windows gorunum javaagent'i (SKIN=1 paketiyle gelir). MacLook'un krom-DISI
 * alt kumesi: renk-modu picker + canli gecis + koyu-sayfa toggle + sekme fontu +
 * cetvel zemini + kapsam-combo kaldirma. Mac-spesifik krom (baslik cubugu, trafik
 * isiklari, Dock adlari) PORT EDILMEZ. macosskin / UDE / Flamingo erisimi tamamen
 * reflection (agent jar'in derleme bagimliligi YOK; siniflar editor-app.jar'dan
 * calisma-aninda cozulur). Agent hicbir kosulda uygulamayi dusurmez.
 */

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.instrument.Instrumentation;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class WinLook {

    private static final boolean DEBUG = "1".equals(System.getProperty("winlook.debug"));
    private static final PrintStream LOG = DEBUG ? openLog() : null;

    private WinLook() {}

    private static PrintStream openLog() {
        try {
            String base = System.getenv("TEMP");
            if (base == null) base = System.getProperty("java.io.tmpdir");
            return new PrintStream(new FileOutputStream(new java.io.File(base, "winlook-agent.log"), true), true, "UTF-8");
        } catch (Exception e) { return System.err; }
    }

    private static void log(String m) {
        if (LOG != null) { try { LOG.println("[winlook] " + m); } catch (Throwable ignore) {} }
    }

    public static void premain(String args, Instrumentation inst) { install(); }
    public static void agentmain(String args, Instrumentation inst) { install(); }

    private static void install() {
        try {
            Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
                @Override public void eventDispatched(AWTEvent e) {
                    if (e.getID() != WindowEvent.WINDOW_OPENED) return;
                    Object src = e.getSource();
                    if (!(src instanceof JFrame)) return;
                    JFrame f = (JFrame) src;
                    SwingUtilities.invokeLater(() -> {
                        try { fixRulerBackground(f); } catch (Throwable t) { log("ruler: " + t); }
                        try { boldTaskTabs(f); } catch (Throwable t) { log("tabfont: " + t); }
                        try { removeScopeCombo(f); } catch (Throwable t) { log("scopecombo: " + t); }
                        try { addDarkPageToggle(f); } catch (Throwable t) { log("darkpage: " + t); }
                        try { addColorModeCombo(f); } catch (Throwable t) { log("colormode: " + t); }
                    });
                }
            }, AWTEvent.WINDOW_EVENT_MASK);
            log("yuklendi");
        } catch (Throwable t) {
            log("kurulamadi: " + t);
        }
    }

    private static void fixRulerBackground(JFrame f) {
        java.awt.Color bg = UIManager.getColor("Panel.background");
        boolean dark = bg != null && (bg.getRed() + bg.getGreen() + bg.getBlue()) / 3 < 100;
        if (!dark) return;
        fixRulerWalk(f);
    }

    private static void fixRulerWalk(Component c) {
        for (Class<?> i : c.getClass().getInterfaces()) {
            if (i.getName().endsWith("IRuler")) {
                c.setBackground(new java.awt.Color(70, 70, 70));
                c.repaint();
                log("cetvel zemini ayarlandi: " + c.getClass().getName());
                break;
            }
        }
        if (c instanceof Container) {
            for (Component k : ((Container) c).getComponents()) fixRulerWalk(k);
        }
    }

    private static void boldTaskTabs(Component c) {
        if (c.getClass().getSimpleName().equals("JRibbonTaskToggleButton")) {
            java.awt.Font fo = c.getFont();
            if (fo != null && !fo.isBold()) {
                c.setFont(fo.deriveFont(java.awt.Font.BOLD));
                log("sekme kalinlasti: " + c);
            }
        }
        if (c instanceof Container) {
            for (Component k : ((Container) c).getComponents()) boldTaskTabs(k);
        }
    }

    private static void removeScopeCombo(JFrame f) {
        Component ribbon = findByClassName(f, "JRibbon");
        if (ribbon == null) return;
        javax.swing.JComboBox<?> combo = findScopeCombo(ribbon);
        if (combo == null) { log("kapsam combo bulunamadi"); return; }
        Component victim = combo;
        Container p = combo.getParent();
        while (p != null && p.getClass().getSimpleName().equals("JRibbonComponent")) {
            victim = p;
            p = p.getParent();
        }
        Container parent = victim.getParent();
        if (parent != null) {
            parent.remove(victim);
            parent.revalidate();
            parent.repaint();
            log("kapsam combo kaldirildi: " + victim.getClass().getName());
        }
    }

    private static void addDarkPageToggle(JFrame f) throws Exception {
        Component ribbon = findByClassName(f, "JRibbon");
        if (ribbon == null) return;
        JComponent r = (JComponent) ribbon;
        if (Boolean.TRUE.equals(r.getClientProperty("winlook.darkpage"))) return;

        Class<?> dp;
        try {
            dp = Class.forName("macosskin.DarkPage", true, f.getClass().getClassLoader());
        } catch (ClassNotFoundException e) { log("darkpage: DarkPage sinifi yok"); return; }

        Object targetBand = null;
        int taskCount = (Integer) ribbon.getClass().getMethod("getTaskCount").invoke(ribbon);
        for (int i = 0; i < taskCount && targetBand == null; i++) {
            Object task = ribbon.getClass().getMethod("getTask", int.class).invoke(ribbon, i);
            java.util.List<?> bands = (java.util.List<?>)
                task.getClass().getMethod("getBands").invoke(task);
            for (Object band : bands) {
                Component cp = (Component)
                    band.getClass().getMethod("getControlPanel").invoke(band);
                if (cp != null && findCheckBox(cp, "Klasik görünüme geç") != null) {
                    targetBand = band;
                    break;
                }
            }
        }
        if (targetBand == null) { log("darkpage: hedef band bulunamadi"); return; }

        final javax.swing.JCheckBox cb = new javax.swing.JCheckBox("Koyu belge arkaplanı");
        cb.setOpaque(false);
        cb.setSelected((Boolean) dp.getMethod("isOn").invoke(null));
        final java.lang.reflect.Method setOn = dp.getMethod("setOn", boolean.class);
        cb.addActionListener(e -> {
            try {
                setOn.invoke(null, cb.isSelected());
                for (java.awt.Window w : java.awt.Window.getWindows()) w.repaint();
            } catch (Throwable t) { log("darkpage toggle: " + t); }
        });

        Class<?> jrcCls = Class.forName("org.pushingpixels.flamingo.api.ribbon.JRibbonComponent", true, f.getClass().getClassLoader());
        Object jrc = jrcCls.getConstructor(JComponent.class).newInstance(cb);
        targetBand.getClass().getMethod("addRibbonComponent", jrcCls).invoke(targetBand, jrc);
        r.putClientProperty("winlook.darkpage", Boolean.TRUE);
        log("darkpage: onay kutusu eklendi");
    }

    private static void addColorModeCombo(JFrame f) throws Exception {
        Component ribbon = findByClassName(f, "JRibbon");
        if (ribbon == null) return;
        JComponent r = (JComponent) ribbon;
        if (Boolean.TRUE.equals(r.getClientProperty("winlook.colormode"))) return;

        final ClassLoader cl = f.getClass().getClassLoader();
        final Class<?> dm;
        try {
            dm = Class.forName("macosskin.DarkMode", true, cl);
        } catch (ClassNotFoundException e) { log("colormode: DarkMode sinifi yok"); return; }

        Object targetBand = null;
        int taskCount = (Integer) ribbon.getClass().getMethod("getTaskCount").invoke(ribbon);
        for (int i = 0; i < taskCount && targetBand == null; i++) {
            Object task = ribbon.getClass().getMethod("getTask", int.class).invoke(ribbon, i);
            java.util.List<?> bands = (java.util.List<?>)
                task.getClass().getMethod("getBands").invoke(task);
            for (Object band : bands) {
                Component cp = (Component)
                    band.getClass().getMethod("getControlPanel").invoke(band);
                if (cp != null && findCheckBox(cp, "Klasik görünüme geç") != null) {
                    targetBand = band;
                    break;
                }
            }
        }
        if (targetBand == null) { log("colormode: hedef band bulunamadi"); return; }

        final String[] modes = { "light", "dark", "system" };
        final String[] labels = { "Açık", "Koyu", "Sistem" };
        final javax.swing.JComboBox<String> combo = new javax.swing.JComboBox<>(labels);
        String cur = (String) dm.getMethod("getMode").invoke(null);
        int sel = 2;
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(cur)) sel = i;
        combo.setSelectedIndex(sel);
        combo.setToolTipText("Açık, koyu ya da sistem görünümü");
        final java.lang.reflect.Method applyMode =
            Class.forName("macosskin.ModeSwitch", true, cl).getMethod("apply", String.class);
        combo.addActionListener(e -> {
            try {
                int i = combo.getSelectedIndex();
                if (i < 0) return;
                String prev = (String) dm.getMethod("getMode").invoke(null);
                if (modes[i].equals(prev)) return;
                applyMode.invoke(null, modes[i]);
            } catch (Throwable t) { log("colormode secim: " + t); }
        });

        javax.swing.JPanel row = new javax.swing.JPanel(
            new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        row.setOpaque(false);
        row.add(new javax.swing.JLabel("Renk modu:"));
        row.add(combo);

        Class<?> jrcCls = Class.forName("org.pushingpixels.flamingo.api.ribbon.JRibbonComponent", true, cl);
        Object jrc = jrcCls.getConstructor(JComponent.class).newInstance(row);
        targetBand.getClass().getMethod("addRibbonComponent", jrcCls).invoke(targetBand, jrc);
        r.putClientProperty("winlook.colormode", Boolean.TRUE);
        log("colormode: acilir liste eklendi (secili=" + cur + ")");
    }

    private static javax.swing.JCheckBox findCheckBox(Component c, String text) {
        if (c instanceof javax.swing.JCheckBox
                && text.equals(((javax.swing.JCheckBox) c).getText())) {
            return (javax.swing.JCheckBox) c;
        }
        if (c instanceof Container) {
            for (Component k : ((Container) c).getComponents()) {
                javax.swing.JCheckBox hit = findCheckBox(k, text);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private static javax.swing.JComboBox<?> findScopeCombo(Component c) {
        if (c instanceof javax.swing.JComboBox) {
            javax.swing.JComboBox<?> cb = (javax.swing.JComboBox<?>) c;
            int n = cb.getItemCount();
            if (n > 0 && n < 10) {
                for (int i = 0; i < n; i++) {
                    if ("Geçerli".equals(String.valueOf(cb.getItemAt(i)))) return cb;
                }
            }
            return null;
        }
        if (c instanceof Container) {
            for (Component k : ((Container) c).getComponents()) {
                javax.swing.JComboBox<?> hit = findScopeCombo(k);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private static Component findByClassName(Component c, String simpleName) {
        if (c.getClass().getSimpleName().equals(simpleName)
                || c.getClass().getName().endsWith("." + simpleName)) return c;
        if (c instanceof Container) {
            for (Component k : ((Container) c).getComponents()) {
                Component hit = findByClassName(k, simpleName);
                if (hit != null) return hit;
            }
        }
        return null;
    }
}
