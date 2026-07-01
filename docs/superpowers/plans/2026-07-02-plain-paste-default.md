# Formatsız Yapıştırma Varsayılan (Windows Portu) — Uygulama Planı

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ctrl+V harici içerikte formatsız (UDE-içi kopyada formatlı) yapıştırsın; Ctrl+Shift+V ve sağ tık "Formatlı Yapıştır" eski formatlı davranışı versin.

**Architecture:** PASTERICH kancasının (`hj.a(Transferable)` enjekte dalı) varsayılanı `macospasterich.PasteMode` üzerinden formatsıza çevrilir; `forceRich` bayrağı zengin yolu zorlar. Ctrl+Shift+V, ZoomKeys deseninde yeni bir `KeyEventDispatcher` (`PasteKeys`) ile bağlanır ve `WPAppManager.main`'e enjekte edilir. Spec: `docs/superpowers/specs/2026-07-02-plain-paste-default-design.md`; Mac kaynağı: `/Users/saidsurucu/Documents/GitHub/ude-mac-arm` (seri 8ddbbe8c…23510c7c).

**Tech Stack:** Java 11 (javac `--release 11`), Javassist 3.30.2-GA, Swing. Geliştirme makinesi macOS (testler burada koşar, headless Swing); build hattı Windows PowerShell (bu planda çalıştırılmaz).

## Global Constraints

- Java dosyaları **BOM'suz UTF-8** (Edit/Write tool'ları güvenli; PowerShell `Set-Content` KULLANMA).
- javac daima `-encoding UTF-8` ve `--release 11`.
- Java YORUMLARINDA `\u` dizisi YASAK (javac unicode-escape çözer); ters-bölü yerine ileri-bölü kullan.
- Yorum/kimlik dili Türkçe, mevcut dosya üslubuna uy.
- Testler elle javac+java ile koşar (JUnit yok); başarı = `... OK` satırı + exit 0.
- Test derleme deseni: `OUT=$(mktemp -d); javac --release 11 -encoding UTF-8 -d "$OUT" scripts/pasterich/macospasterich/*.java tests/<Test>.java && java -cp "$OUT" <Test>`
- Commit mesajları Türkçe, mevcut geçmiş üslubunda; her commit sonuna `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

---

### Task 1: PasteMode.java + cursorAttrs görünürlüğü (+ PasteModeTest)

**Files:**
- Create: `scripts/pasterich/macospasterich/PasteMode.java`
- Modify: `scripts/pasterich/macospasterich/PlainPaste.java:78` (yalnız `cursorAttrs` imzası)
- Test: `tests/PasteModeTest.java`

**Interfaces:**
- Consumes: `macospasterich.RichPaste.insertInto(Object,String,AttributeSet)` ve `insertRtf(Object,Transferable,AttributeSet)` (mevcut, dokunulmaz), `PlainPaste.cursorAttrs(JTextComponent)` (paket-içi yapılır).
- Produces: `macospasterich.PasteMode.setForceRich(boolean)`, `PasteMode.insertHtml(Object,String):boolean`, `PasteMode.insertRtf(Object,Transferable):boolean` — Task 2/3/4 bunları kullanır.

- [ ] **Step 1: Başarısız testi yaz** — `tests/PasteModeTest.java` (Mac'ten birebir; tek fark derleme yolundaki `scripts/pasterich`):

```java
import javax.swing.JTextPane;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * PasteMode birim testi (headless). Varsayılan mod FORMATSIZ: kaynak karakter
 * stili (kalın/Arial) düşer, imleç stili alınır. forceRich modunda kaynak
 * stili korunur; bayrak finally ile temizlenince yeniden formatsız.
 * Derle + çalıştır:
 *   OUT=$(mktemp -d)
 *   javac --release 11 -encoding UTF-8 -d "$OUT" scripts/pasterich/macospasterich/*.java tests/PasteModeTest.java
 *   java -cp "$OUT" PasteModeTest
 */
public class PasteModeTest {
    public static void main(String[] a) throws Exception {
        String html = "<p><b><span style='font-family:Arial;font-size:20pt;"
                + "color:#FF0000'>Kalin kirmizi</span></b></p>";

        // 1) Varsayılan: formatsız -> kalın düşer, kaynak fontu (Arial) alınmaz.
        JTextPane p1 = new JTextPane();
        p1.setCaretPosition(0);
        if (!macospasterich.PasteMode.insertHtml(p1, html))
            throw new AssertionError("insertHtml (varsayılan) false döndü");
        if (hasBold(p1))
            throw new AssertionError("varsayılan mod formatsız değil: kalın korunmuş");
        if ("Arial".equals(familyOfFirstRun(p1)))
            throw new AssertionError("varsayılan mod kaynak fontunu aldı (imleç stili beklenirdi)");

        // 2) forceRich: formatlı -> kalın + Arial korunur.
        JTextPane p2 = new JTextPane();
        p2.setCaretPosition(0);
        macospasterich.PasteMode.setForceRich(true);
        try {
            if (!macospasterich.PasteMode.insertHtml(p2, html))
                throw new AssertionError("insertHtml (forceRich) false döndü");
        } finally {
            macospasterich.PasteMode.setForceRich(false);
        }
        if (!hasBold(p2))
            throw new AssertionError("forceRich modu formatlı değil: kalın düşmüş");
        if (!"Arial".equals(familyOfFirstRun(p2)))
            throw new AssertionError("forceRich modu kaynak fontunu korumadı: " + familyOfFirstRun(p2));

        // 3) Bayrak temizlendi: yeniden varsayılan (formatsız).
        JTextPane p3 = new JTextPane();
        p3.setCaretPosition(0);
        if (!macospasterich.PasteMode.insertHtml(p3, html))
            throw new AssertionError("insertHtml (bayrak sonrası) false döndü");
        if (hasBold(p3))
            throw new AssertionError("bayrak temizlenmedi: hâlâ formatlı");

        System.out.println("PasteModeTest OK");
    }

    private static boolean hasBold(JTextPane pane) throws Exception {
        StyledDocument doc = (StyledDocument) pane.getDocument();
        Element root = doc.getDefaultRootElement();
        for (int p = 0; p < root.getElementCount(); p++) {
            Element para = root.getElement(p);
            for (int r = 0; r < para.getElementCount(); r++) {
                Element run = para.getElement(r);
                int s = run.getStartOffset(), e = Math.min(run.getEndOffset(), doc.getLength());
                if (e <= s) continue;
                if (doc.getText(s, e - s).trim().isEmpty()) continue;
                if (StyleConstants.isBold(run.getAttributes())) return true;
            }
        }
        return false;
    }

    private static String familyOfFirstRun(JTextPane pane) throws Exception {
        StyledDocument doc = (StyledDocument) pane.getDocument();
        Element root = doc.getDefaultRootElement();
        for (int p = 0; p < root.getElementCount(); p++) {
            Element para = root.getElement(p);
            for (int r = 0; r < para.getElementCount(); r++) {
                Element run = para.getElement(r);
                int s = run.getStartOffset(), e = Math.min(run.getEndOffset(), doc.getLength());
                if (e <= s) continue;
                if (doc.getText(s, e - s).trim().isEmpty()) continue;
                return StyleConstants.getFontFamily(run.getAttributes());
            }
        }
        return null;
    }
}
```

- [ ] **Step 2: Testin başarısız olduğunu doğrula**

Çalıştır: `cd /Users/saidsurucu/Documents/GitHub/ude-win-x64 && OUT=$(mktemp -d) && javac --release 11 -encoding UTF-8 -d "$OUT" scripts/pasterich/macospasterich/*.java tests/PasteModeTest.java`
Beklenen: DERLEME HATASI — `macospasterich.PasteMode` bulunamadı.

- [ ] **Step 3: PasteMode.java'yı yaz** (Mac'ten birebir; `scripts/pasterich/macospasterich/PasteMode.java`):

```java
package macospasterich;

import java.awt.datatransfer.Transferable;

import javax.swing.text.AttributeSet;
import javax.swing.text.JTextComponent;

/**
 * Harici yapıştırma modu anahtarı. PasteRichPatch'in hj.a(Transferable) kancası
 * RichPaste'i DOĞRUDAN değil bu sınıf üzerinden çağırır: varsayılan FORMATSIZ
 * (karakter+paragraf biçimi imleçten, tablo/liste/imaj YAPISI kaynaktan —
 * PLAINPASTE anlamı); forceRich bayrağı set edilmişse eski FORMATLI (zengin)
 * yol. Bayrağı Ctrl+Shift+V (PasteKeys dispatcher) ve sağ tık "Formatlı
 * Yapıştır" (PlainPaste.addMenuItem) try/finally ile set/temizler.
 *
 * UDE-İÇİ kopyalar (EditorDataFlavor) paste()'in başında, kancaya hiç
 * uğramadan işlenir -> her zaman formatlı kalır (bu sınıf onlara dokunmaz).
 */
public final class PasteMode {

    private static volatile boolean forceRich;

    /** Çağıranlar try/finally ile set/temizlemeli (yarım bayrak kalmasın). */
    public static void setForceRich(boolean b) { forceRich = b; }

    /** Kancanın HTML dalı: varsayılan formatsız, forceRich'te formatlı. */
    public static boolean insertHtml(Object editor, String html) {
        return RichPaste.insertInto(editor, html, cursorAttrsOrNull(editor));
    }

    /** Kancanın RTF dalı: aynı mod seçimi. */
    public static boolean insertRtf(Object editor, Transferable t) {
        return RichPaste.insertRtf(editor, t, cursorAttrsOrNull(editor));
    }

    /** null = formatlı (zengin yol); değilse formatsızın imleç stili. */
    private static AttributeSet cursorAttrsOrNull(Object editor) {
        if (forceRich) return null;
        if (editor instanceof JTextComponent)
            return PlainPaste.cursorAttrs((JTextComponent) editor);
        return null;   /* editör tipi bilinmiyorsa güvenli taraf: eski (formatlı) davranış */
    }

    private PasteMode() { }
}
```

- [ ] **Step 4: `PlainPaste.cursorAttrs`'ı paket-içi yap** — `scripts/pasterich/macospasterich/PlainPaste.java` satır 78'deki

```java
    private static AttributeSet cursorAttrs(JTextComponent editor) {
```

satırını şuna çevir (Edit tool):

```java
    static AttributeSet cursorAttrs(JTextComponent editor) {   /* paket-içi: PasteMode da kullanır */
```

**ÖNEMLİ ÖN-KONTROL:** Task 1'e başlamadan önce `RichPaste.insertInto`/`insertRtf`'nin 3-argümanlı (AttributeSet alan) overload'larının Windows kopyasında var olduğunu doğrula: `grep -n "insertInto\|insertRtf" scripts/pasterich/macospasterich/RichPaste.java`. (PLAINPASTE zaten bu overload'ları kullanıyor; yoklarsa dur ve rapor et.)

- [ ] **Step 5: Testin geçtiğini doğrula**

Çalıştır: `OUT=$(mktemp -d) && javac --release 11 -encoding UTF-8 -d "$OUT" scripts/pasterich/macospasterich/*.java tests/PasteModeTest.java && java -cp "$OUT" PasteModeTest`
Beklenen: `PasteModeTest OK`, exit 0.

- [ ] **Step 6: Commit**

```bash
git add scripts/pasterich/macospasterich/PasteMode.java scripts/pasterich/macospasterich/PlainPaste.java tests/PasteModeTest.java
git commit -m "feat(pastemode): harici yapıştırma modu anahtarı — varsayılan formatsız, forceRich bayrağı (Mac portu)"
```

---

### Task 2: PlainPaste sağ tık menüsü — "Formatlı Yapıştır" (+ PasteMenuTest)

**Files:**
- Modify: `scripts/pasterich/macospasterich/PlainPaste.java:109-147` (`addMenuItem` javadoc'u + gövdesi; `insertAfter` yardımcısı eklenir)
- Test: `tests/PasteMenuTest.java`

**Interfaces:**
- Consumes: `PasteMode.setForceRich(boolean)` (Task 1).
- Produces: sağ tık menüsünde "Formatsız Yapıştır" (hızlandırıcısız) + "Formatlı Yapıştır" (Ctrl+Shift+V göstergesi) öğeleri. `paste(JTextComponent)`, `fixAccelerators`, log yardımcıları DEĞİŞMEZ.

- [ ] **Step 1: Başarısız testi yaz** — `tests/PasteMenuTest.java` (Mac'ten port; SON blok platforma-duyarlı — Windows'ta `meta==CTRL` olduğundan fixAccelerators no-op'tur, Mac geliştirme makinesinde ⌘'ye çevirir; assertion iki platformda da geçer):

```java
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;

/**
 * PlainPaste.addMenuItem testi (headless): "Formatsız Yapıştır" (hızlandırıcısız)
 * + "Formatlı Yapıştır" (Ctrl+Shift+V) doğru sırada ve idempotent eklenir.
 * Derle + çalıştır:
 *   OUT=$(mktemp -d)
 *   javac --release 11 -encoding UTF-8 -d "$OUT" scripts/pasterich/macospasterich/*.java tests/PasteMenuTest.java
 *   java -cp "$OUT" PasteMenuTest
 */
public class PasteMenuTest {
    public static void main(String[] a) throws Exception {
        JPopupMenu popup = new JPopupMenu();
        popup.add(new JMenuItem("Kes"));
        popup.add(new JMenuItem("Kopyala"));
        JMenuItem yapistir = new JMenuItem("Yapıştır");
        yapistir.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK));
        popup.add(yapistir);

        JTextPane editor = new JTextPane();
        macospasterich.PlainPaste.addMenuItem(popup, editor);
        macospasterich.PlainPaste.addMenuItem(popup, editor);   // idempotans: ikinci çağrı çift eklememeli

        int pasteIdx = indexOf(popup, "Yapıştır");
        int plainIdx = indexOf(popup, "Formatsız Yapıştır");
        int richIdx  = indexOf(popup, "Formatlı Yapıştır");
        if (plainIdx < 0) throw new AssertionError("Formatsız Yapıştır eklenmedi");
        if (richIdx < 0) throw new AssertionError("Formatlı Yapıştır eklenmedi");
        if (count(popup, "Formatsız Yapıştır") != 1 || count(popup, "Formatlı Yapıştır") != 1)
            throw new AssertionError("idempotans bozuk: çift öğe var");
        if (!(pasteIdx < plainIdx && plainIdx < richIdx))
            throw new AssertionError("sıra bozuk: Yapıştır(" + pasteIdx + ") -> Formatsız("
                    + plainIdx + ") -> Formatlı(" + richIdx + ") bekleniyordu");

        JMenuItem plain = (JMenuItem) popup.getComponent(plainIdx);
        if (plain.getAccelerator() != null)
            throw new AssertionError("Formatsız Yapıştır hızlandırıcı göstermemeli: " + plain.getAccelerator());

        int meta = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenuItem rich = (JMenuItem) popup.getComponent(richIdx);
        KeyStroke want = KeyStroke.getKeyStroke(KeyEvent.VK_V, meta | InputEvent.SHIFT_DOWN_MASK);
        if (!want.equals(rich.getAccelerator()))
            throw new AssertionError("Formatlı Yapıştır hızlandırıcısı Ctrl(⌘)+Shift+V değil: " + rich.getAccelerator());

        // fixAccelerators: "Yapıştır"ın hızlandırıcısı platformun menü kısayol
        // maskesini taşımalı (Windows'ta Ctrl kalır = no-op; macOS'ta ⌘'ye döner).
        KeyStroke fixed = ((JMenuItem) popup.getComponent(pasteIdx)).getAccelerator();
        if (fixed == null || (fixed.getModifiers() & meta) == 0)
            throw new AssertionError("Yapıştır hızlandırıcısı menü kısayol maskesini taşımıyor: " + fixed);
        if (meta != InputEvent.CTRL_DOWN_MASK && (fixed.getModifiers() & InputEvent.CTRL_DOWN_MASK) != 0)
            throw new AssertionError("Yapıştır hızlandırıcısında Ctrl kalmış: " + fixed);

        System.out.println("PasteMenuTest OK");
    }

    private static int indexOf(JPopupMenu popup, String text) {
        Component[] cs = popup.getComponents();
        for (int i = 0; i < cs.length; i++) {
            if (cs[i] instanceof JMenuItem
                    && text.equals(((JMenuItem) cs[i]).getText().trim())) return i;
        }
        return -1;
    }

    private static int count(JPopupMenu popup, String text) {
        int n = 0;
        for (Component c : popup.getComponents()) {
            if (c instanceof JMenuItem && text.equals(((JMenuItem) c).getText().trim())) n++;
        }
        return n;
    }
}
```

- [ ] **Step 2: Testin başarısız olduğunu doğrula**

Çalıştır: `OUT=$(mktemp -d) && javac --release 11 -encoding UTF-8 -d "$OUT" scripts/pasterich/macospasterich/*.java tests/PasteMenuTest.java && java -cp "$OUT" PasteMenuTest`
Beklenen: `AssertionError: Formatlı Yapıştır eklenmedi` (derleme geçer, koşum düşer).

- [ ] **Step 3: `addMenuItem`'ı yeniden yaz** — `scripts/pasterich/macospasterich/PlainPaste.java` içinde `addMenuItem`'ın javadoc'u + gövdesi (mevcut satır 109-147, `/**` … metodun kapanış `}`) şu blokla DEĞİŞTİRİLİR ve hemen ardından `insertAfter` yardımcısı EKLENİR (`fixAccelerators`'tan önce):

```java
    /**
     * UDE'nin kendi sağ tık menüsüne (text.fK → gui.dx.getPopupMenu()) yapıştırma
     * öğelerini ekler: "Formatsız Yapıştır" (hızlandırıcısız — Ctrl+V zaten "akıllı":
     * UDE-içi formatlı, harici formatsız) ve "Formatlı Yapıştır" (Ctrl+Shift+V;
     * forceRich bayrağıyla zengin yolu zorlar). PlainPastePatch tarafından
     * JPopupMenu.show çağrısından ÖNCE çağrılır ($0=popup, $1=editör). Sıra:
     * Yapıştır → Formatsız → Formatlı. İdempotent (popup yeniden gösterilse de
     * tek kopya). fixAccelerators Windows'ta no-op (menü kısayol maskesi zaten Ctrl).
     */
    public static void addMenuItem(JPopupMenu popup, Component invoker) {
        try {
            if (popup == null || !(invoker instanceof JTextComponent)) return;
            final JTextComponent tc = (JTextComponent) invoker;
            boolean hasPlain = false, hasRich = false;
            for (Component c : popup.getComponents()) {
                if (!(c instanceof JMenuItem)) continue;
                String txt = ((JMenuItem) c).getText();
                if ("Formatsız Yapıştır".equals(txt)) hasPlain = true;
                if ("Formatlı Yapıştır".equals(txt)) hasRich = true;
            }
            boolean enabled = tc.isEditable() && tc.isEnabled();
            if (!hasPlain) {
                JMenuItem mi = new JMenuItem("Formatsız Yapıştır");
                mi.setEnabled(enabled);
                // Hızlandırıcı YOK: Ctrl+Shift+V artık Formatlı Yapıştır'ın.
                mi.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) { paste(tc); }
                });
                insertAfter(popup, mi, "Yapıştır");
            }
            if (!hasRich) {
                JMenuItem mi = new JMenuItem("Formatlı Yapıştır");
                mi.setEnabled(enabled);
                int meta = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();   // Windows: CTRL
                mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, meta | InputEvent.SHIFT_DOWN_MASK));
                mi.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        // tc = hj türevi editör → virtual dispatch zengin hj.paste();
                        // forceRich kancaya "formatlı" dedirtir (UDE-içi zaten formatlı).
                        PasteMode.setForceRich(true);
                        try { tc.paste(); } finally { PasteMode.setForceRich(false); }
                    }
                });
                insertAfter(popup, mi, "Formatsız Yapıştır");
            }
            fixAccelerators(popup);
        } catch (Throwable e) {
            log("addMenuItem", e);
        }
    }

    /** mi'yi metni `after` olan öğeden hemen sonra ekler (bulunamazsa sona). */
    private static void insertAfter(JPopupMenu popup, JMenuItem mi, String after) {
        int idx = -1;
        Component[] cs = popup.getComponents();
        for (int i = 0; i < cs.length; i++) {
            if (cs[i] instanceof JMenuItem) {
                String t = ((JMenuItem) cs[i]).getText();
                if (t != null && after.equals(t.trim())) { idx = i + 1; break; }
            }
        }
        if (idx >= 0 && idx <= popup.getComponentCount()) popup.insert(mi, idx);
        else popup.add(mi);
    }
```

Sınıf-üstü javadoc'taki (satır 26-35) "İki çağıran: ⌘⇧V (MacShortcutRemap, reflection) ve sağ tık menüsü" cümlesi şuna güncellenir: "Çağıranlar: sağ tık menüsü (EditContextMenuWidget Javassist yaması → addMenuItem) ve — formatlı yol için — Ctrl+Shift+V (PasteKeys dispatcher)."

- [ ] **Step 4: Testlerin geçtiğini doğrula (Task 1 testi dahil, regresyon)**

Çalıştır: `OUT=$(mktemp -d) && javac --release 11 -encoding UTF-8 -d "$OUT" scripts/pasterich/macospasterich/*.java tests/PasteMenuTest.java tests/PasteModeTest.java && java -cp "$OUT" PasteMenuTest && java -cp "$OUT" PasteModeTest`
Beklenen: `PasteMenuTest OK` + `PasteModeTest OK`.

- [ ] **Step 5: Commit**

```bash
git add scripts/pasterich/macospasterich/PlainPaste.java tests/PasteMenuTest.java
git commit -m "feat(plainpaste): sağ tık menüsüne Formatlı Yapıştır (Ctrl+Shift+V); Formatsız hızlandırıcısız"
```

---

### Task 3: PasteKeys — Ctrl+Shift+V dispatcher'ı (+ PasteKeysTest)

**Files:**
- Create: `scripts/pasterich/macospasterich/PasteKeys.java`
- Test: `tests/PasteKeysTest.java`

**Interfaces:**
- Consumes: `PasteMode.setForceRich(boolean)`, `PasteMode.insertHtml` (yalnız testte gözlem için).
- Produces: `macospasterich.PasteKeys.install()` (Task 4'te WPAppManager.main'e enjekte edilir), `public static boolean dispatch(KeyEvent)` (test erişimi).

- [ ] **Step 1: Başarısız testi yaz** — `tests/PasteKeysTest.java`:

```java
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JTextPane;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * PasteKeys.dispatch testi (headless). Ctrl+Shift+V basımı: forceRich bayrağı
 * paste() SIRASINDA set, sonrasında temiz; PRESSED+RELEASED yutulur; Ctrl+V ve
 * JTextComponent olmayan bileşenlere dokunulmaz.
 * Derle + çalıştır:
 *   OUT=$(mktemp -d)
 *   javac --release 11 -encoding UTF-8 -d "$OUT" scripts/pasterich/macospasterich/*.java tests/PasteKeysTest.java
 *   java -cp "$OUT" PasteKeysTest
 */
public class PasteKeysTest {
    static final String HTML = "<p><b><span style='font-family:Arial'>Kalin</span></b></p>";

    /** paste() çağrısını kaydeder; o anda forceRich etkin miydi diye insertHtml ile yoklar. */
    static class RecordingPane extends JTextPane {
        boolean pasted, richDuringPaste;
        @Override public void paste() {
            pasted = true;
            try {
                JTextPane probe = new JTextPane();
                probe.setCaretPosition(0);
                if (!macospasterich.PasteMode.insertHtml(probe, HTML))
                    throw new AssertionError("probe insertHtml false döndü");
                richDuringPaste = hasBold(probe);   // bold korunuyorsa forceRich etkin demektir
            } catch (RuntimeException | Error e) { throw e; }
              catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    public static void main(String[] a) throws Exception {
        int mods = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK;

        // 1) Ctrl+Shift+V PRESSED: yutulur, paste çağrılır, paste sırasında forceRich etkin.
        RecordingPane tc = new RecordingPane();
        KeyEvent pressed = new KeyEvent(tc, KeyEvent.KEY_PRESSED, 0L, mods, KeyEvent.VK_V, 'V');
        if (!macospasterich.PasteKeys.dispatch(pressed))
            throw new AssertionError("Ctrl+Shift+V PRESSED yutulmadı");
        if (!tc.pasted) throw new AssertionError("paste() çağrılmadı");
        if (!tc.richDuringPaste) throw new AssertionError("paste sırasında forceRich etkin değildi");

        // 2) RELEASED da yutulur (ama paste tekrar çağrılmaz).
        tc.pasted = false;
        KeyEvent released = new KeyEvent(tc, KeyEvent.KEY_RELEASED, 0L, mods, KeyEvent.VK_V, 'V');
        if (!macospasterich.PasteKeys.dispatch(released))
            throw new AssertionError("Ctrl+Shift+V RELEASED yutulmadı");
        if (tc.pasted) throw new AssertionError("RELEASED paste çağırdı");

        // 3) Bayrak temiz: dispatch sonrası insertHtml formatsız.
        JTextPane p = new JTextPane();
        p.setCaretPosition(0);
        if (!macospasterich.PasteMode.insertHtml(p, HTML))
            throw new AssertionError("insertHtml (dispatch sonrası) false döndü");
        if (hasBold(p)) throw new AssertionError("bayrak temizlenmedi: hâlâ formatlı");

        // 4) Shift'siz Ctrl+V'ye dokunulmaz.
        int cmd = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        KeyEvent plainV = new KeyEvent(tc, KeyEvent.KEY_PRESSED, 0L, cmd, KeyEvent.VK_V, 'v');
        if (macospasterich.PasteKeys.dispatch(plainV))
            throw new AssertionError("Ctrl+V yutuldu (dokunulmamalıydı)");

        // 5) Alt basılıyken (AltGr kombinasyonları) dokunulmaz.
        KeyEvent altCombo = new KeyEvent(tc, KeyEvent.KEY_PRESSED, 0L,
                mods | InputEvent.ALT_DOWN_MASK, KeyEvent.VK_V, 'V');
        if (macospasterich.PasteKeys.dispatch(altCombo))
            throw new AssertionError("Ctrl+Alt+Shift+V yutuldu (AltGr korunmalıydı)");

        // 6) JTextComponent olmayan bileşene dokunulmaz.
        KeyEvent onButton = new KeyEvent(new JButton(), KeyEvent.KEY_PRESSED, 0L, mods, KeyEvent.VK_V, 'V');
        if (macospasterich.PasteKeys.dispatch(onButton))
            throw new AssertionError("JButton üstünde yutuldu");

        // 7) Salt-okunur editörde paste çağrılmaz (event yine yutulur: kısayol bizim).
        RecordingPane ro = new RecordingPane();
        ro.setEditable(false);
        KeyEvent onReadOnly = new KeyEvent(ro, KeyEvent.KEY_PRESSED, 0L, mods, KeyEvent.VK_V, 'V');
        if (!macospasterich.PasteKeys.dispatch(onReadOnly))
            throw new AssertionError("salt-okunurda PRESSED yutulmadı");
        if (ro.pasted) throw new AssertionError("salt-okunurda paste() çağrıldı");

        System.out.println("PasteKeysTest OK");
    }

    static boolean hasBold(JTextPane pane) throws Exception {
        StyledDocument doc = (StyledDocument) pane.getDocument();
        Element root = doc.getDefaultRootElement();
        for (int p = 0; p < root.getElementCount(); p++) {
            Element para = root.getElement(p);
            for (int r = 0; r < para.getElementCount(); r++) {
                Element run = para.getElement(r);
                int s = run.getStartOffset(), e = Math.min(run.getEndOffset(), doc.getLength());
                if (e <= s) continue;
                if (doc.getText(s, e - s).trim().isEmpty()) continue;
                if (StyleConstants.isBold(run.getAttributes())) return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 2: Testin başarısız olduğunu doğrula**

Çalıştır: `OUT=$(mktemp -d) && javac --release 11 -encoding UTF-8 -d "$OUT" scripts/pasterich/macospasterich/*.java tests/PasteKeysTest.java`
Beklenen: DERLEME HATASI — `macospasterich.PasteKeys` bulunamadı.

- [ ] **Step 3: PasteKeys.java'yı yaz** (`scripts/pasterich/macospasterich/PasteKeys.java`; ZoomKeys deseni — bkz. `scripts/zoomkeys/com/udewin/zoom/ZoomKeys.java`):

```java
package macospasterich;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.KeyEventDispatcher;

import javax.swing.text.JTextComponent;

/**
 * Ctrl+Shift+V = Formatlı Yapıştır. Mac'te bu iş textkeys agent'ındaki
 * MacShortcutRemap'in Fb.RICH_PASTE dalında; textkeys Windows'a port edilmedi
 * (native Ctrl), karşılığı bu küçük dispatcher (ZoomKeys deseni).
 *
 * Odaktaki UDE editöründe (hj türevi) forceRich bayrağını set edip tc.paste()
 * çağırır: virtual dispatch zengin hj.paste() -> PASTERICH kancası -> PasteMode
 * forceRich gördüğünden FORMATLI yol. paste() SENKRON olduğundan finally bayrağı
 * gerçekten kapsar. Editör-dışı düz alanlarda paste() zaten düz; bayrak onlarda
 * etkisiz (kanca yok) — Mac performLocal ile aynı sonuç.
 *
 * WPAppManager.main başına PasteRichPatch tarafından install() enjekte edilir.
 */
public final class PasteKeys {

    private static final int CMD = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx(); // Windows: CTRL
    private static final int WANT = CMD | InputEvent.SHIFT_DOWN_MASK;
    /** İlgilendiğimiz değiştiriciler: fazladan Alt/Meta/AltGr basılıysa (TR klavye AltGr!) karışma. */
    private static final int REL = CMD | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK
            | InputEvent.META_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;
    private static volatile boolean installed;

    private PasteKeys() { }

    /** WPAppManager.main başına enjekte edilir (idempotent). */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        try {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(new KeyEventDispatcher() {
                    public boolean dispatchKeyEvent(KeyEvent e) { return dispatch(e); }
                });
        } catch (Throwable ignore) {
        }
    }

    /** Ctrl+Shift+V + odak JTextComponent: PRESSED'de formatlı yapıştır; PRESSED+RELEASED yutulur. */
    public static boolean dispatch(KeyEvent e) {   /* public: test erişimi (PasteKeysTest) */
        try {
            if (e.getKeyCode() != KeyEvent.VK_V) return false;
            if ((e.getModifiersEx() & REL) != WANT) return false;
            Component c = e.getComponent();
            if (!(c instanceof JTextComponent)) return false;
            int id = e.getID();
            if (id != KeyEvent.KEY_PRESSED && id != KeyEvent.KEY_RELEASED) return false;
            if (id == KeyEvent.KEY_PRESSED) {
                JTextComponent tc = (JTextComponent) c;
                if (tc.isEditable() && tc.isEnabled()) {
                    PasteMode.setForceRich(true);
                    try { tc.paste(); } finally { PasteMode.setForceRich(false); }
                }
            }
            return true;   // basma/bırakmayı yut (kısayol bizim)
        } catch (Throwable t) {
            return false;
        }
    }
}
```

NOT: `WANT` içinde `CMD` Windows'ta `CTRL_DOWN_MASK`tır; `REL` maskesindeki `CTRL_DOWN_MASK` teriminin Windows'ta `CMD` ile çakışması sorun değil (bit zaten WANT'ta). macOS geliştirme makinesinde `CMD=META` olur, `REL` Ctrl'yi de dışlar — test iki platformda da tutarlıdır.

- [ ] **Step 4: Testlerin geçtiğini doğrula (üçü birden)**

Çalıştır: `OUT=$(mktemp -d) && javac --release 11 -encoding UTF-8 -d "$OUT" scripts/pasterich/macospasterich/*.java tests/PasteKeysTest.java tests/PasteMenuTest.java tests/PasteModeTest.java && java -cp "$OUT" PasteKeysTest && java -cp "$OUT" PasteMenuTest && java -cp "$OUT" PasteModeTest`
Beklenen: `PasteKeysTest OK`, `PasteMenuTest OK`, `PasteModeTest OK`.

- [ ] **Step 5: Commit**

```bash
git add scripts/pasterich/macospasterich/PasteKeys.java tests/PasteKeysTest.java
git commit -m "feat(pastekeys): Ctrl+Shift+V formatlı yapıştır dispatcher'ı (MacShortcutRemap RICH_PASTE'in Windows karşılığı)"
```

---

### Task 4: PasteRichPatch — kanca PasteMode'a + WPAppManager'a PasteKeys.install()

**Files:**
- Modify: `scripts/pasterich/PasteRichPatch.java`

**Interfaces:**
- Consumes: `macospasterich.PasteMode.insertHtml/insertRtf` (Task 1), `macospasterich.PasteKeys.install()` (Task 3).
- Produces: yamalı `hj` + `WPAppManager` sınıfları (build'de `jar uf` ile geri yazılır). `apply-pasterich.ps1` DEĞİŞMEZ (yeni .java dosyaları `macospasterich\*.java` glob'una zaten düşer).

- [ ] **Step 1: Enjekte dalı PasteMode'a çevir** — `scripts/pasterich/PasteRichPatch.java:70` ve `:74` (Edit tool, iki ayrı replace):

`"          if (macospasterich.RichPaste.insertInto(this, __h)) return true;"` → `"          if (macospasterich.PasteMode.insertHtml(this, __h)) return true;"`

`"      if (macospasterich.RichPaste.insertRtf(this, __t)) return true;"` → `"      if (macospasterich.PasteMode.insertRtf(this, __t)) return true;"`

(`logExternal` çağrısı RichPaste'te KALIR — dokunma.)

- [ ] **Step 2: Sınıf javadoc'una not ekle** — satır 36 (`* derlemesi RichPaste'i jar classpath'inden çözer).`) ile satır 37 (`*`) arasına:

```java
 *
 * 2026-07: Enjekte dal RichPaste'i doğrudan değil macospasterich.PasteMode
 * üzerinden çağırır — varsayılan formatsız, Ctrl+Shift+V/"Formatlı Yapıştır"
 * forceRich bayrağıyla zengin yolu zorlar. Ayrıca WPAppManager.main başına
 * PasteKeys.install() enjekte edilir (Ctrl+Shift+V dispatcher'ı). Spec:
 * docs/superpowers/specs/2026-07-02-plain-paste-default-design.md
```

- [ ] **Step 3: WPAppManager yamasını ekle** — `main` metodunda `writeClass(hj, outDir);` satırından ÖNCE şu blok eklenir; ayrıca dosyanın import'larına `javassist.expr.ExprEditor` ve `javassist.expr.MethodCall` eklenir:

```java
        // Ctrl+Shift+V dispatcher'ı: WPAppManager.main başına PasteKeys.install().
        // Idempotans: cagri zaten varsa atla (ZOOMKEYS/FILEASSOC ayni metoda dokunur;
        // her patcher jar'i taze okur -> insertBefore'lar kompoze olur).
        CtClass wp = pool.get("tr.com.havelsan.uyap.system.editor.common.WPAppManager");
        CtMethod mainM = wp.getDeclaredMethod("main");
        final boolean[] already = { false };
        mainM.instrument(new ExprEditor() {
            public void edit(MethodCall mc) {
                if (mc.getClassName().equals("macospasterich.PasteKeys")) already[0] = true;
            }
        });
        if (!already[0]) {
            mainM.insertBefore("macospasterich.PasteKeys.install();");
            writeClass(wp, outDir);
            System.out.println("[PasteRichPatch] WPAppManager.main yamalandi (Ctrl+Shift+V formatli yapistir).");
        } else {
            System.out.println("[PasteRichPatch] WPAppManager.main zaten yamali; atlandi.");
        }
```

Son `System.out.println` satırı (satır 81) şuna güncellenir:
`System.out.println("[PasteRichPatch] hj.a(Transferable) harici-HTML dalı enjekte edildi (varsayılan formatsız, PasteMode).");`

- [ ] **Step 4: Derleme kontrolü (jar'sız, yalnız sözdizimi + tip)** — macOS'ta editor-app.jar yok; Javassist'i indirip helper'larla birlikte derle:

```bash
SCRATCH=/private/tmp/claude-501/-Users-saidsurucu-Documents-GitHub-ude-win-x64/ab2f1466-9273-40db-a60c-2c40aef164a9/scratchpad
JVS=$SCRATCH/javassist-3.30.2-GA.jar
[ -f "$JVS" ] || curl -Ls -o "$JVS" https://repo1.maven.org/maven2/org/javassist/javassist/3.30.2-GA/javassist-3.30.2-GA.jar
OUT=$(mktemp -d)
javac --release 11 -encoding UTF-8 -d "$OUT" scripts/pasterich/macospasterich/*.java
javac --release 11 -encoding UTF-8 -cp "$JVS:$OUT" -d "$OUT" scripts/pasterich/PasteRichPatch.java scripts/pasterich/PlainPastePatch.java
```

Beklenen: her iki javac exit 0 (PlainPastePatch regresyon kontrolü için dahil). Patcher'ın jar'a karşı ÇALIŞTIRILMASI Windows build makinesinde `.\build.ps1 -Only patch` ile doğrulanacak (kullanıcıya bırakılır).

- [ ] **Step 5: Commit**

```bash
git add scripts/pasterich/PasteRichPatch.java
git commit -m "feat(pasterich): kanca varsayılanı formatsız (PasteMode) + WPAppManager'a PasteKeys.install() enjeksiyonu"
```

---

### Task 5: Dokümantasyon — CLAUDE.md + README

**Files:**
- Modify: `CLAUDE.md:55` (özellik tablosu satırı)
- Modify: `README.md:42-43` (Stilli yapıştırma maddesi)

**Interfaces:**
- Consumes: —. Produces: — (yalnız docs).

- [ ] **Step 1: CLAUDE.md tablo satırını güncelle** — satır 55:

```
| PASTERICH+PLAINPASTE | Stilli/formatsız yapıştırma; **Ctrl+V akıllı: UDE-içi formatlı, harici FORMATSIZ (varsayılan, 2026-07); Ctrl+Shift+V + sağ tık "Formatlı Yapıştır" = formatlı** | `text.hj` paste yolu; CF_HTML `DataFlavor.allHtmlFlavor`; `PasteMode` bayrağı; `PasteKeys` dispatcher (`WPAppManager.main` inject) |
```

- [ ] **Step 2: README maddesini güncelle** — satır 42-43'teki

```
- 📋 **Stilli yapıştırma** — Word/tarayıcı/PDF'den kalın/italik/liste/**tablo**/renk biçimiyle
  yapışır (Windows panosu `CF_HTML`). Formatsız Yapıştır (Ctrl+Shift+V).
```

şuna çevrilir:

```
- 📋 **Akıllı yapıştırma** — Ctrl+V: UDE-içi kopya formatlı, harici içerik (Word/tarayıcı/PDF)
  **formatsız** yapışır (tablo/liste/imaj yapısı korunur, biçim imleçten; Windows panosu `CF_HTML`).
  Ctrl+Shift+V veya sağ tık "Formatlı Yapıştır": kaynak biçimiyle (kalın/italik/renk/**tablo**) yapıştırır.
```

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md README.md
git commit -m "docs: formatsız yapıştırma varsayılan — Ctrl+V akıllı, Ctrl+Shift+V formatlı notu"
```

---

## Kalan doğrulama (plan dışı, kullanıcıda)

- Windows build makinesinde: `.\build.ps1 -Only patch` → exit 0 (tüm yamalar kompoze; PasteRichPatch'in iki println'i görünmeli).
- GUI: Word'den Ctrl+V → formatsız; Ctrl+Shift+V → formatlı; UDE-içi kopya Ctrl+V → formatlı; sağ tık üç öğe (Yapıştır → Formatsız → Formatlı).
