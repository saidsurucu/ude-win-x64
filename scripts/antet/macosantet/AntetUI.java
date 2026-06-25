package macosantet;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

/*
 * "Arka Plan Resmi Düzenleme" (gui.gR) diyaloğuna "Antetlerim" bölümü.
 * AntetPatch, gR.c() sonuna install(this) ekler. gR'nin private üyelerine
 * REFLECTION + TİP (+ad) ile erişilir (obfuscate ad çakışmaları: üç 'a' alanı).
 * Her giriş noktası try/catch'li: hata → log + stok diyalog bozulmadan açılır.
 */
public final class AntetUI {

    private AntetUI() {}

    /* gR.c() sonundan çağrılır (EDT). */
    public static void install(Object dlg) {
        try {
            JComponent modul = findModulPanel(dlg);
            if (modul == null) {
                AntetLog.log("install: GÖZAT'lı modül paneli bulunamadı");
                return;
            }
            JPanel section = new JPanel();
            section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
            section.setBorder(BorderFactory.createTitledBorder("Antetlerim"));
            section.setOpaque(false);
            rebuild(section, dlg);
            modul.add(section, "cell 0 9, growx");
            modul.revalidate();
            AntetLog.log("install: bölüm eklendi, " + AntetStore.list().length + " antet");
        } catch (Throwable t) {
            AntetLog.log("install: " + t);
        }
    }

    /* Modüller paneli = içinde "GÖZAT" yazılı JButton bulunan Container alanı. */
    private static JComponent findModulPanel(Object dlg) throws Exception {
        for (Field f : dlg.getClass().getDeclaredFields()) {
            if (!Container.class.isAssignableFrom(f.getType())) continue;
            f.setAccessible(true);
            Object v = f.get(dlg);
            if (v instanceof JComponent && hasGozat((Container) v)) return (JComponent) v;
        }
        return null;
    }

    private static boolean hasGozat(Container c) {
        for (Component k : c.getComponents()) {
            if (k instanceof JButton && "GÖZAT".equals(((JButton) k).getText())) return true;
        }
        return false;
    }

    private static void rebuild(final JPanel section, final Object dlg) {
        section.removeAll();
        for (final File f : AntetStore.list()) {
            JPanel row = new JPanel(new BorderLayout(4, 0));
            row.setOpaque(false);

            JButton pick = new JButton(AntetStore.displayName(f));
            pick.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    select(dlg, f, section);
                }
            });
            row.add(pick, BorderLayout.CENTER);

            JButton del = new JButton("×");
            del.setToolTipText("Anteti sil");
            del.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    int r = JOptionPane.showConfirmDialog(section,
                        "\"" + AntetStore.displayName(f) + "\" silinsin mi?",
                        "Antet Sil", JOptionPane.YES_NO_OPTION);
                    if (r != JOptionPane.YES_OPTION) return;
                    if (!AntetStore.delete(f)) {
                        JOptionPane.showMessageDialog(section,
                            "Antet silinemedi.", "Uyarı", JOptionPane.WARNING_MESSAGE);
                    }
                    rebuild(section, dlg);
                }
            });
            row.add(del, BorderLayout.EAST);

            row.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                row.getPreferredSize().height));
            section.add(row);
        }

        JButton add = new JButton("Antet Ekle…");
        add.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                addFlow(dlg, section);
            }
        });
        JPanel addRow = new JPanel(new BorderLayout());
        addRow.setOpaque(false);
        addRow.add(add, BorderLayout.CENTER);
        addRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,
            addRow.getPreferredSize().height));
        section.add(addRow);

        section.revalidate();
        section.repaint();
    }

    /* Native dosya penceresiyle seç → klasöre kopyala → listeyi tazele → seç. */
    private static void addFlow(Object dlg, JPanel section) {
        try {
            Window w = SwingUtilities.getWindowAncestor(section);
            FileDialog fd;
            if (w instanceof Dialog) fd = new FileDialog((Dialog) w, "Antet Seç", FileDialog.LOAD);
            else if (w instanceof Frame) fd = new FileDialog((Frame) w, "Antet Seç", FileDialog.LOAD);
            else fd = new FileDialog((Frame) null, "Antet Seç", FileDialog.LOAD);
            fd.setFilenameFilter(new FilenameFilter() {
                public boolean accept(File d, String name) { return AntetStore.accepts(name); }
            });
            fd.setVisible(true);
            if (fd.getFile() == null) return;
            File src = new File(fd.getDirectory(), fd.getFile());
            if (!AntetStore.accepts(src.getName())) {
                JOptionPane.showMessageDialog(section,
                    "Yalnız PNG ve JPG dosyaları eklenebilir.",
                    "Uyarı", JOptionPane.WARNING_MESSAGE);
                return;
            }
            File dest = AntetStore.add(src);
            rebuild(section, dlg);
            select(dlg, dest, section);
        } catch (Throwable t) {
            AntetLog.log("addFlow: " + t);
            JOptionPane.showMessageDialog(section,
                "Antet eklenemedi: " + t.getMessage(),
                "Uyarı", JOptionPane.WARNING_MESSAGE);
        }
    }

    /* Resmi sayfaya sığdırıp gR alanlarına bas, stok önizlemeyi tetikle. */
    private static void select(Object dlg, File f, JPanel section) {
        try {
            double[] p = pageSize(dlg);
            BufferedImage img = AntetStore.loadFitted(f, p[0], p[1]);
            AntetLog.log("select: " + f.getName() + " -> " + img.getWidth()
                + "x" + img.getHeight() + " (sayfa " + p[0] + "x" + p[1] + ")");
            for (Field fd : dlg.getClass().getDeclaredFields()) {
                if (fd.getType() == BufferedImage.class) {
                    fd.setAccessible(true);
                    fd.set(dlg, img);
                } else if (fd.getType() == String.class && "b".equals(fd.getName())
                        && !Modifier.isFinal(fd.getModifiers())) {
                    fd.setAccessible(true);
                    fd.set(dlg, null);
                }
            }
            Method e = dlg.getClass().getDeclaredMethod("e");
            e.setAccessible(true);
            e.invoke(dlg);
        } catch (Throwable t) {
            AntetLog.log("select: " + t);
            JOptionPane.showMessageDialog(section,
                "Antet okunamadı: " + AntetStore.displayName(f),
                "Uyarı", JOptionPane.WARNING_MESSAGE);
        }
    }

    /* Belgenin paperWidth/paperHeight'ı (pt); bulunamazsa A4. */
    private static double[] pageSize(Object dlg) {
        try {
            Object fi = null;
            for (Field f : dlg.getClass().getDeclaredFields()) {
                if (f.getType().getName().endsWith(".fi")) {
                    f.setAccessible(true);
                    fi = f.get(dlg);
                    break;
                }
            }
            Document doc = findDocument(fi);
            if (doc != null) {
                AttributeSet as = doc.getDefaultRootElement().getAttributes();
                double w = attr(as, "paperWidth");
                double h = attr(as, "paperHeight");
                if (w > 100 && h > 100) return new double[] { w, h };
            }
        } catch (Throwable t) {
            AntetLog.log("pageSize: " + t);
        }
        return new double[] { AntetStore.A4_W, AntetStore.A4_H };
    }

    private static Document findDocument(Object fi) throws Exception {
        if (fi == null) return null;
        if (fi instanceof JTextComponent) return ((JTextComponent) fi).getDocument();
        for (Class<?> c = fi.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Document.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object v = f.get(fi);
                    if (v != null) return (Document) v;
                }
            }
        }
        return null;
    }

    private static double attr(AttributeSet as, String name) {
        for (Enumeration<?> e = as.getAttributeNames(); e.hasMoreElements();) {
            Object k = e.nextElement();
            if (name.equals(String.valueOf(k))) {
                Object v = as.getAttribute(k);
                if (v instanceof Number) return ((Number) v).doubleValue();
                try {
                    return Double.parseDouble(String.valueOf(v));
                } catch (Exception ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }
}
