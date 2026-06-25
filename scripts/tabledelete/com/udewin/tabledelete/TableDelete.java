package com.udewin.tabledelete;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;

import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.KeyStroke;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import javax.swing.text.TextAction;

/**
 * Backspace/Delete ile tablo silme. UDE tabloları yalnız araç çubuğundaki
 * "Tablo Sil" (DocumentEx.f(int)) ile kaldırılabiliyordu; bu sınıf düz
 * Backspace/Delete'i Word benzeri tablo silmeye yönlendirir, tablo yoksa
 * orijinal aksiyona devreder. Tespit saf javax.swing.text; yalnız silme
 * çağrısı reflection (agent app-classpath'siz derlenir).
 */
public final class TableDelete {

    private TableDelete() {}

    /** Tespit için minimal doküman görünümü (test edilebilirlik). */
    public interface DocView {
        /** pos'taki yaprak (karakter) eleman; yoksa null. */
        Element charAt(int pos);
        /** [start, start+len) metni; hata olursa "". */
        String text(int start, int len);
    }

    /** e'den yukarı çıkıp adı "table" olan ilk atayı döndürür; yoksa null. */
    static Element tableAncestor(Element e) {
        while (e != null) {
            if ("table".equals(e.getName())) return e;
            e = e.getParentElement();
        }
        return null;
    }

    /** [s,e) aralığını TAM kapsayan ilk tabloyu döndürür; yoksa null. */
    static Element firstTableInRange(DocView dv, int s, int e) {
        int p = s;
        while (p < e) {
            Element el = dv.charAt(p);
            Element t = tableAncestor(el);
            if (t != null && t.getStartOffset() >= s && t.getEndOffset() <= e) return t;
            int next = (el != null) ? el.getEndOffset() : p + 1;
            p = (next > p) ? next : p + 1;
        }
        return null;
    }

    /** Tablo metni boşluk/kontrol dışında boşsa true. */
    static boolean isEmptyTable(DocView dv, Element t) {
        String s = dv.text(t.getStartOffset(), t.getEndOffset() - t.getStartOffset());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isISOControl(c)) return false;
        }
        return true;
    }

    /**
     * Backspace için silinecek tablonun "içi" konumu (f'e verilecek) veya -1.
     * -1 → tablo silme yok, orijinal Backspace'e devret.
     */
    public static int targetForBackspace(DocView dv, int caret, int selStart, int selEnd) {
        if (selStart != selEnd) {
            Element t = firstTableInRange(dv, selStart, selEnd);
            return t != null ? t.getStartOffset() : -1;
        }
        Element inTable = tableAncestor(dv.charAt(caret));
        if (inTable != null) {
            return isEmptyTable(dv, inTable) ? inTable.getStartOffset() : -1;
        }
        if (caret > 0) {
            Element pt = tableAncestor(dv.charAt(caret - 1));
            if (pt != null && caret >= pt.getEndOffset()) return pt.getStartOffset();
        }
        return -1;
    }

    /** Delete için: seçim tam-kapsama veya içeride-boş; adjacency yok. */
    public static int targetForDelete(DocView dv, int caret, int selStart, int selEnd) {
        if (selStart != selEnd) {
            Element t = firstTableInRange(dv, selStart, selEnd);
            return t != null ? t.getStartOffset() : -1;
        }
        Element inTable = tableAncestor(dv.charAt(caret));
        if (inTable != null) {
            return isEmptyTable(dv, inTable) ? inTable.getStartOffset() : -1;
        }
        return -1;
    }

    // ---- Aksiyon + bağlama + reflection (UDE çalışma anı) ----

    private static final String KEY_BS  = "mac-table-aware-backspace";
    private static final String KEY_DEL = "mac-table-aware-delete";
    private static final String ORIG_BS  = "mac.orig.backspace";
    private static final String ORIG_DEL = "mac.orig.delete";

    /** MacTextKeys.applyBindings0'dan her odakta çağrılır (idempotent). */
    public static void bind(JTextComponent tc) {
        try {
            InputMap im = tc.getInputMap();
            ActionMap am = tc.getActionMap();
            if (im == null || am == null) return;
            bindOne(tc, im, am, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), KEY_BS,  ORIG_BS,  BACKSPACE);
            bindOne(tc, im, am, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),     KEY_DEL, ORIG_DEL, DELETE);
        } catch (Throwable t) {
            log("bind hata: " + t);
        }
    }

    private static void bindOne(JTextComponent tc, InputMap im, ActionMap am,
                                KeyStroke ks, String key, String origProp, Action action) {
        Object cur = im.get(ks);
        if (key.equals(cur)) return;               // zaten sarılı
        tc.putClientProperty(origProp, cur);       // orijinal actionMapKey (null olabilir)
        am.put(key, action);
        im.put(ks, key);
    }

    private static final Action BACKSPACE = new TextAction(KEY_BS) {
        @Override public void actionPerformed(ActionEvent e) {
            JTextComponent tc = getTextComponent(e);
            if (tc == null) return;
            if (!tryDelete(tc, false)) delegate(tc, e, ORIG_BS, false);
        }
    };

    private static final Action DELETE = new TextAction(KEY_DEL) {
        @Override public void actionPerformed(ActionEvent e) {
            JTextComponent tc = getTextComponent(e);
            if (tc == null) return;
            if (!tryDelete(tc, true)) delegate(tc, e, ORIG_DEL, true);
        }
    };

    /** Tablo silme uygulanırsa true; aksi halde false (devret). */
    private static boolean tryDelete(JTextComponent tc, boolean forward) {
        if (!tc.isEditable() || !tc.isEnabled()) return false;
        Document d = tc.getDocument();
        if (!(d instanceof StyledDocument)) return false;
        final StyledDocument sd = (StyledDocument) d;
        DocView dv = new DocView() {
            public Element charAt(int p) { return sd.getCharacterElement(p); }
            public String text(int s, int len) {
                try { return sd.getText(s, len); } catch (Throwable t) { return ""; }
            }
        };
        int caret = tc.getCaretPosition();
        int s = tc.getSelectionStart(), en = tc.getSelectionEnd();

        // Seçim: kapsadığı TÜM tabloları f() ile kaldır, sonra kalan seçili
        // metni sil (yoksa devret → normal seçim silme). Tek f() çağrısı yalnız
        // tabloyu kaldırırdı, seçili metnin kalanını bırakırdı.
        if (s != en) {
            if (firstTableInRange(dv, s, en) == null) return false;  // tablo yok → normal sil
            return deleteSelectionWithTables(tc, d, dv, s, en);
        }

        // Seçimsiz (imleç): tek tablo (ard / içeride-boş).
        int target = targetForBackspace(dv, caret, s, en);   // forward fark etmez (adjacency yok)
        if (forward) target = targetForDelete(dv, caret, s, en);
        if (target < 0) return false;
        log("tryDelete forward=" + forward + " caret=" + caret + " target=" + target
                + " docClass=" + d.getClass().getName());
        try {
            Method f = d.getClass().getMethod("f", int.class);  // wp.model.v.f(int) (miras)
            f.invoke(d, target);
            int len = d.getLength();
            tc.setCaretPosition(Math.min(target, len));
            tc.requestFocus();
            return true;
        } catch (Throwable t) {
            log(unwrap("f(int) HATA", t));
            return false;   // reflection başarısız → normal Backspace'e devret
        }
    }

    /**
     * Seçim aralığındaki tüm tabloları f() ile kaldırır, ardından kalan seçili
     * metni siler. Offset kayması Position nesneleriyle otomatik izlenir
     * (f() yapısal değişiklik yapar). En az bir tablo işlenmişse true.
     */
    private static boolean deleteSelectionWithTables(JTextComponent tc, Document d, DocView dv, int selS, int selE) {
        log("deleteSelectionWithTables sel=[" + selS + "," + selE + "] docClass=" + d.getClass().getName());
        try {
            Method f = d.getClass().getMethod("f", int.class);
            Position startPos = d.createPosition(selS);
            Position endPos = d.createPosition(selE);
            int guard = 0;
            Element t;
            while (guard++ < 500
                    && (t = firstTableInRange(dv, startPos.getOffset(), endPos.getOffset())) != null) {
                f.invoke(d, t.getStartOffset());
            }
            int s = startPos.getOffset(), e = endPos.getOffset();
            if (e > s) d.remove(s, e - s);   // kalan seçili metni sil
            tc.setCaretPosition(Math.min(startPos.getOffset(), d.getLength()));
            tc.requestFocus();
            return true;
        } catch (Throwable t) {
            log(unwrap("deleteSelectionWithTables HATA", t));
            return false;   // başarısız → normal seçim silmeye devret
        }
    }

    /** InvocationTargetException'ı açıp ilk 12 stack çerçevesiyle birlikte metne çevirir. */
    private static String unwrap(String prefix, Throwable t) {
        Throwable cause = (t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null)
                ? t.getCause() : t;
        StringBuilder sb = new StringBuilder(prefix).append(": ").append(cause);
        StackTraceElement[] st = cause.getStackTrace();
        for (int i = 0; i < st.length && i < 12; i++) sb.append("\n    at ").append(st[i]);
        return sb.toString();
    }

    /**
     * Orijinal (yakalanmış) Backspace/Delete aksiyonuna devret. Orijinal
     * bulunamazsa (beklenmez) güvenli geri-çekilme: seçim varsa sil, yoksa
     * yön'e göre bir karakter sil.
     */
    private static void delegate(JTextComponent tc, ActionEvent e, String origProp, boolean forward) {
        Object origKey = tc.getClientProperty(origProp);
        Action orig = (origKey != null) ? tc.getActionMap().get(origKey) : null;
        if (orig != null) { orig.actionPerformed(e); return; }
        try {
            int s = tc.getSelectionStart(), en = tc.getSelectionEnd();
            if (s != en) { tc.getDocument().remove(s, en - s); return; }
            int caret = tc.getCaretPosition();
            if (forward && caret < tc.getDocument().getLength()) tc.getDocument().remove(caret, 1);
            else if (!forward && caret > 0) tc.getDocument().remove(caret - 1, 1);
        } catch (Throwable t) {
            log("devir geri-cekilme hata: " + t);
        }
    }

    /** UDE_TABLEDELLOG=1 iken %LOCALAPPDATA% altinda ude-tabledelete.txt (System.err yutulur). */
    private static void log(String msg) {
        if (!"1".equals(System.getenv("UDE_TABLEDELLOG"))) return;
        try {
            String base = System.getenv("LOCALAPPDATA");
            if (base == null) base = System.getProperty("user.home");
            java.io.File f = new java.io.File(base, "ude-tabledelete.txt");
            try (java.io.FileWriter w = new java.io.FileWriter(f, true)) {
                w.write(msg + "\n");
            }
        } catch (Throwable ignore) {}
    }
}
