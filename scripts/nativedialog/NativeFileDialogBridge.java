package com.udewin.nativedialog;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Window;
import java.io.File;

/**
 * UDE'nin JFileChooser tabanli ac/kaydet diyaloglarini Windows native
 * java.awt.FileDialog'a kopruleyen yardimci (build-zamani Javassist yamasi cagirir).
 *
 *  - FILES_ONLY disindaki secim modlari (klasor secme) -> Swing'e geri dusulur
 *    (native FileDialog Windows'ta klasor secemez).
 *  - SAVE: native panelde format acilir listesi YOK. 2+ gercek filtre varsa once
 *    bir format-secim penceresi gosterilir (Mac MacFileDialog paritesi); tek filtre
 *    varsessizce uzanti belirlenir. Hedef uzanti ada zorlanir.
 *  - SAVE: UDE varsayilan ad vermezse (isimsiz kaydet) ad pencere basligindan alinir.
 *  - LOAD: secim sonrasi eslesen choosable filtre fc'ye yazilir.
 *  - Herhangi bir hata -> sessizce orijinal Swing diyaloguna geri dusulur.
 */
public final class NativeFileDialogBridge {
    private NativeFileDialogBridge() {}

    /** Format/uzanti zorlamada taninan UDE uzantilari (sira = probe onceligi). */
    private static final String[] KNOWN_EXTS = {"udf", "rtf", "pdf", "xml", "usf"};

    /** mode: 0=showOpenDialog, 1=showSaveDialog, 2=showDialog */
    public static int show(JFileChooser ch, Component parent, int mode) {
        try {
            if (ch == null) return JFileChooser.CANCEL_OPTION;
            if (ch.getFileSelectionMode() != JFileChooser.FILES_ONLY) {
                return fallback(ch, parent, mode);
            }

            int dlgType = (mode == 1) ? JFileChooser.SAVE_DIALOG
                        : (mode == 0) ? JFileChooser.OPEN_DIALOG : ch.getDialogType();
            boolean save = (dlgType == JFileChooser.SAVE_DIALOG);

            Window owner = (parent instanceof Window) ? (Window) parent
                         : (parent != null ? SwingUtilities.getWindowAncestor(parent) : null);
            String title = ch.getDialogTitle();
            if (title == null || title.length() == 0) title = save ? "Farkli Kaydet" : "Ac";

            FileDialog fd;
            int fdMode = save ? FileDialog.SAVE : FileDialog.LOAD;
            if (owner instanceof Frame)       fd = new FileDialog((Frame) owner, title, fdMode);
            else if (owner instanceof Dialog) fd = new FileDialog((Dialog) owner, title, fdMode);
            else                              fd = new FileDialog((Frame) null, title, fdMode);

            File dir = ch.getCurrentDirectory();
            if (dir != null) fd.setDirectory(dir.getAbsolutePath());

            File sel = ch.getSelectedFile();
            if (sel != null && sel.getName() != null && sel.getName().length() > 0) {
                fd.setFile(sel.getName());
            } else if (save) {
                // "Farkli Kaydet"/"PDF olarak kaydet": UDE varsayilan ad vermez (sel=null).
                // Acik belgenin adini pencere basligindan al (orn. "isimsiz.UDF").
                String docName = docNameFromTitle(owner);
                if (docName != null && !docName.isEmpty() && !isPlaceholderName(docName)) {
                    fd.setFile(docName);
                }
            }

            // SAVE: format-secim penceresi + uzanti zorlama
            String targetExt = null;
            FileFilter chosenFilter = null;   // onaylaninca fc'ye yazilir (iptalde degil)
            if (save) {
                java.util.List<FileFilter> real = new java.util.ArrayList<FileFilter>();
                FileFilter acceptAll = ch.getAcceptAllFileFilter();
                FileFilter[] choosable = ch.getChoosableFileFilters();
                if (choosable != null) {
                    for (FileFilter ff : choosable) {
                        if (ff != null && !ff.equals(acceptAll)) real.add(ff);
                    }
                }
                if (real.size() >= 2) {
                    chosenFilter = promptFormat(owner, real, ch.getFileFilter(), fd.getFile());
                    if (chosenFilter == null) return JFileChooser.CANCEL_OPTION; // format iptal
                    targetExt = probeExtension(chosenFilter);
                } else if (real.size() == 1) {
                    targetExt = probeExtension(real.get(0));
                }
                if (targetExt != null) {
                    String pre = fd.getFile();
                    if (pre != null && !pre.isEmpty()) fd.setFile(forceExtension(pre, targetExt));
                }
            }

            try { fd.setMultipleMode(ch.isMultiSelectionEnabled()); } catch (Throwable ignore) {}

            // en iyi-caba uzanti filtresi: yalniz ac + onceden secili dosya yokken
            if (!save && (sel == null || sel.getName() == null || sel.getName().length() == 0)) {
                String pat = extPattern(ch);
                if (pat != null) fd.setFile(pat);
            }

            fd.setVisible(true); // modal

            String name = fd.getFile();
            if (name == null) return JFileChooser.CANCEL_OPTION;
            if (save) {
                if (chosenFilter != null) ch.setFileFilter(chosenFilter);
                if (targetExt != null) name = forceExtension(name, targetExt);
            }
            String d = fd.getDirectory();
            File[] multi = fd.getFiles();
            File chosen;
            if (ch.isMultiSelectionEnabled() && multi != null && multi.length > 0) {
                ch.setSelectedFiles(multi);
                ch.setSelectedFile(multi[0]);
                chosen = multi[0];
            } else {
                chosen = new File(d, name);
                ch.setSelectedFile(chosen);
            }
            if (d != null) {
                try { ch.setCurrentDirectory(new File(d)); } catch (Throwable ignore) {}
            }
            if (!save) {
                FileFilter mf = matchChoosableFilter(ch, chosen);
                if (mf != null) ch.setFileFilter(mf);
            }
            return JFileChooser.APPROVE_OPTION;
        } catch (Throwable t) {
            return fallback(ch, parent, mode);
        }
    }

    // ---- format / uzanti yardimcilari (Mac MacFileDialog ile ayni; platform-notr) ----

    /** Ad sonundaki bilinen UDE uzantisini (case-insensitive) dondurur; yoksa null. */
    static String knownExtOf(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        String ext = name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        for (String k : KNOWN_EXTS) if (k.equals(ext)) return ext;
        return null;
    }

    /** Ad sonundaki bilinen UDE uzantisini sokup ".ext" ekler (cift uzantiyi onler). */
    static String forceExtension(String name, String ext) {
        if (name == null || ext == null || ext.isEmpty()) return name;
        String known = knownExtOf(name);
        String base = (known != null) ? name.substring(0, name.length() - known.length() - 1) : name;
        return base + "." + ext;
    }

    /** Filtrenin uzantisini accept() probe ile bulur (obfuscated alt siniflarda da). */
    static String probeExtension(FileFilter ff) {
        if (ff == null) return null;
        for (String ext : KNOWN_EXTS) {
            if (ff.accept(new File("p." + ext))) return ext;
        }
        return null;
    }

    /** "isimsiz" yer tutucu ad mi? (UDE isimsiz.UDF kaydini yutar -> veri kaybini onle). */
    static boolean isPlaceholderName(String name) {
        if (name == null) return false;
        String known = knownExtOf(name);
        String base = (known != null) ? name.substring(0, name.length() - known.length() - 1) : name;
        return base.equalsIgnoreCase("isimsiz");
    }

    /** Uzanti icin dostca etiket; bilinmeyen/null -> null. */
    static String friendlyLabel(String ext) {
        if (ext == null) return null;
        ext = ext.toLowerCase(java.util.Locale.ROOT);
        switch (ext) {
            case "udf": return "UDF Belgesi (.udf)";
            case "rtf": return "Word / RTF (.rtf)";
            case "pdf": return "PDF (.pdf)";
            case "xml": return "XML (.xml)";
            case "usf": return "USF (.usf)";
            default: return null;
        }
    }

    private static final class FormatItem {
        final String label; final FileFilter filter; final String ext;
        FormatItem(String label, FileFilter filter, String ext) {
            this.label = label; this.filter = filter; this.ext = ext;
        }
        @Override public String toString() { return label; }
    }

    /** Native panel acilmadan once format sectiren modal pencere; iptal -> null. */
    private static FileFilter promptFormat(Window owner, java.util.List<FileFilter> filters,
                                           FileFilter current, String currentFileName) {
        FormatItem[] items = new FormatItem[filters.size()];
        int defaultIdx = 0;
        for (int i = 0; i < filters.size(); i++) {
            FileFilter ff = filters.get(i);
            String ext = probeExtension(ff);
            String label = friendlyLabel(ext);
            if (label == null) {
                String desc = ff.getDescription();
                label = (desc != null && !desc.isEmpty()) ? desc : "Bilinmeyen";
            }
            items[i] = new FormatItem(label, ff, ext);
            if (current != null && ff.equals(current)) defaultIdx = i;
        }
        String curExt = knownExtOf(currentFileName);
        if (curExt != null) {
            for (int i = 0; i < items.length; i++) {
                if (curExt.equals(items[i].ext)) { defaultIdx = i; break; }
            }
        }
        javax.swing.JComboBox<FormatItem> combo = new javax.swing.JComboBox<FormatItem>(items);
        combo.setSelectedIndex(defaultIdx);
        int res = javax.swing.JOptionPane.showOptionDialog(
            owner, combo, "Kaydetme Bicimi",
            javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE,
            null, new Object[]{"Tamam", "Iptal"}, "Tamam");
        if (res != 0) return null;
        FormatItem sel = (FormatItem) combo.getSelectedItem();
        return (sel != null) ? sel.filter : null;
    }

    /** Secilen dosyayi kabul eden ilk accept-all-olmayan choosable filtre; yoksa null. */
    static FileFilter matchChoosableFilter(JFileChooser fc, File f) {
        if (fc == null || f == null) return null;
        FileFilter acceptAll = fc.getAcceptAllFileFilter();
        FileFilter[] all = fc.getChoosableFileFilters();
        if (all == null) return null;
        for (FileFilter ff : all) {
            if (ff == null || ff.equals(acceptAll)) continue;
            if (ff.accept(f)) return ff;
        }
        return null;
    }

    /**
     * Pencere basligindan acik belgenin dosya adini cikarir; yoksa null.
     * Baslik: "Dokuman Editoru vX - <ad> (<tam yol>)". Once son parantez icindeki
     * tam yolun taban adi (/ veya \\ ayraci), yedek olarak " - " sonrasi alinir.
     */
    private static String docNameFromTitle(Window owner) {
        try {
            String t = (owner instanceof Frame) ? ((Frame) owner).getTitle() : null;
            if (t == null) return null;
            t = t.trim();
            if (t.isEmpty()) return null;
            int close = t.lastIndexOf(')');
            int open = (close > 0) ? t.lastIndexOf('(', close) : -1;
            if (open >= 0 && close > open) {
                String inside = t.substring(open + 1, close).trim();
                if (inside.indexOf('/') >= 0 || inside.indexOf('\\') >= 0) {
                    int slash = Math.max(inside.lastIndexOf('/'), inside.lastIndexOf('\\'));
                    String base = inside.substring(slash + 1).trim();
                    if (!base.isEmpty()) return base;
                }
            }
            int dash = t.indexOf(" - ");
            if (dash >= 0) {
                String after = t.substring(dash + 3).trim();
                int paren = after.indexOf(" (");
                if (paren > 0) after = after.substring(0, paren).trim();
                if (!after.isEmpty()) return after;
            }
            return null;
        } catch (Throwable x) {
            return null;
        }
    }

    private static String extPattern(JFileChooser ch) {
        try {
            FileFilter ff = ch.getFileFilter();
            if (ff instanceof javax.swing.filechooser.FileNameExtensionFilter) {
                String[] ex = ((javax.swing.filechooser.FileNameExtensionFilter) ff).getExtensions();
                if (ex != null && ex.length > 0) return "*." + ex[0];
            }
        } catch (Throwable ignore) {}
        return null;
    }

    // NOT: build-zamani yamasi yalniz UDE (tr/*) siniflarindaki cagrilari degistirir
    // -> burada gercek Swing metodlari calisir.
    private static int fallback(JFileChooser ch, Component parent, int mode) {
        if (mode == 1) return ch.showSaveDialog(parent);
        if (mode == 0) return ch.showOpenDialog(parent);
        return ch.showDialog(parent, null);
    }
}
