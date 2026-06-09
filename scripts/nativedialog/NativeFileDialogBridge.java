package com.udewin.nativedialog;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Window;
import java.io.File;

/**
 * UDE'nin JFileChooser tabanli ac/kaydet diyaloglarini Windows native
 * java.awt.FileDialog'a kopruleyen yardimci (build-zamani Javassist yamasi
 * tarafindan cagrilir).
 *
 *  - FILES_ONLY disindaki secim modlari (klasor secme) -> Swing'e geri dusulur
 *    (native FileDialog Windows'ta klasor secemez).
 *  - Coklu secim, baslangic klasoru, dialog tipi (ac/kaydet) eslenir.
 *  - Uzanti filtresi en iyi-caba (Windows FileDialog FilenameFilter'i cagirmaz).
 *  - Herhangi bir hata -> sessizce orijinal Swing diyaloguna geri dusulur.
 */
public final class NativeFileDialogBridge {
    private NativeFileDialogBridge() {}

    /** mode: 0=showOpenDialog, 1=showSaveDialog, 2=showDialog */
    public static int show(JFileChooser ch, Component parent, int mode) {
        try {
            if (ch == null) return JFileChooser.CANCEL_OPTION;
            if (ch.getFileSelectionMode() != JFileChooser.FILES_ONLY) {
                return fallback(ch, parent, mode);
            }

            int dlgType = (mode == 1) ? JFileChooser.SAVE_DIALOG : ch.getDialogType();
            boolean save = (dlgType == JFileChooser.SAVE_DIALOG);

            Window w = (parent != null) ? SwingUtilities.getWindowAncestor(parent) : null;
            String title = ch.getDialogTitle();
            if (title == null || title.length() == 0) title = save ? "Farkli Kaydet" : "Ac";

            FileDialog fd;
            int fdMode = save ? FileDialog.SAVE : FileDialog.LOAD;
            if (w instanceof Frame)       fd = new FileDialog((Frame) w, title, fdMode);
            else if (w instanceof Dialog) fd = new FileDialog((Dialog) w, title, fdMode);
            else                          fd = new FileDialog((Frame) null, title, fdMode);

            File dir = ch.getCurrentDirectory();
            if (dir != null) fd.setDirectory(dir.getAbsolutePath());
            File sel = ch.getSelectedFile();
            if (sel != null && sel.getName().length() > 0) fd.setFile(sel.getName());

            try { fd.setMultipleMode(ch.isMultiSelectionEnabled()); } catch (Throwable ignore) {}

            // en iyi-caba uzanti filtresi: yalniz ac + onceden secili dosya yokken
            if (!save && (sel == null || sel.getName().length() == 0)) {
                String pat = extPattern(ch);
                if (pat != null) fd.setFile(pat);
            }

            fd.setVisible(true); // modal

            File[] files = fd.getFiles();
            if (files == null || files.length == 0) {
                String f = fd.getFile();
                if (f == null) return JFileChooser.CANCEL_OPTION;
                files = new File[] { new File(fd.getDirectory(), f) };
            }
            if (fd.getDirectory() != null) {
                try { ch.setCurrentDirectory(new File(fd.getDirectory())); } catch (Throwable ignore) {}
            }
            if (ch.isMultiSelectionEnabled()) ch.setSelectedFiles(files);
            else ch.setSelectedFile(files[0]);
            return JFileChooser.APPROVE_OPTION;
        } catch (Throwable t) {
            return fallback(ch, parent, mode);
        }
    }

    private static String extPattern(JFileChooser ch) {
        try {
            javax.swing.filechooser.FileFilter ff = ch.getFileFilter();
            if (ff instanceof javax.swing.filechooser.FileNameExtensionFilter) {
                String[] ex = ((javax.swing.filechooser.FileNameExtensionFilter) ff).getExtensions();
                if (ex != null && ex.length > 0) return "*." + ex[0];
            }
        } catch (Throwable ignore) {}
        return null;
    }

    // NOT: bu cagri bu sinifin icindedir; build-zamani yamasi yalniz UDE (tr/*)
    // siniflarindaki cagrilari degistirir -> burada gercek Swing metodlari calisir.
    private static int fallback(JFileChooser ch, Component parent, int mode) {
        if (mode == 1) return ch.showSaveDialog(parent);
        if (mode == 0) return ch.showOpenDialog(parent);
        return ch.showDialog(parent, null);
    }
}
