package macospasterich;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.MutableComboBoxModel;
import javax.swing.SwingUtilities;

import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import macospasterich.UdeDoc.Block;
import macospasterich.UdeDoc.Paragraph;
import macospasterich.UdeDoc.Run;
import macospasterich.UdeDoc.Table;
import macospasterich.UdeDoc.TableCell;
import macospasterich.UdeDoc.TableRow;
import macospasterich.UdeDoc.TextRun;
import macospasterich.UdeDoc.TextStyle;

/**
 * UDE belge modelini canlı editörün belgesine caret'e YEREL ekler — copy→paste
 * (EditorDataFlavor) tabloları düzleştirdiğinden o yol terk edildi. Tablolar
 * UDE'nin kendi tablo-kurma primitifiyle (DocumentEx.a) gerçek tablo olarak
 * oluşturulur, hücreler içerikle doldurulur; paragraflar StyleConstants
 * karakter/paragraf öznitelikleriyle eklenir.
 *
 * Saf java.* + reflection (UDE iç tipleri derleme-zamanı gerekmez): DocumentEx.a,
 * ae.x/w/z, Utils.a(int[]) reflection ile çözülür.
 */
final class NativeInsert {

    /**
     * Düz-karakter modu için imleç öznitelik kümesi. Null değilse charAttrs(...)
     * stil yerine bunu döndürür (Formatsız Yapıştır). EDT tek-iş-parçacıklı;
     * insert() içinde try/finally ile set/temizlenir.
     */
    private static AttributeSet CURSOR_ATTRS;

    /** Düz modda imleç paragraf biçimi (beyaz-liste). null = normal rich paste. */
    private static AttributeSet CURSOR_PARA_ATTRS;

    /** İmleçten kopyalanacak paragraf-düzen anahtarları (char/yapısal sızıntı yok). */
    private static final Object[] PARA_FORMAT_KEYS = {
        StyleConstants.Alignment, StyleConstants.LeftIndent, StyleConstants.RightIndent,
        StyleConstants.FirstLineIndent, StyleConstants.SpaceAbove, StyleConstants.SpaceBelow,
        StyleConstants.LineSpacing, StyleConstants.TabSet
    };

    /** Liste paragrafında imleçten alınan anahtarlar (girinti/tabset DEĞİL → kaynak). */
    private static final Object[] LIST_CURSOR_KEYS = {
        StyleConstants.Alignment, StyleConstants.SpaceAbove,
        StyleConstants.SpaceBelow, StyleConstants.LineSpacing
    };

    /** editor (hj/JTextComponent) belgesine caret'ten itibaren modeli ekler. */
    static boolean insert(Object editor, UdeDoc.Document model) {
        return insert(editor, model, null);
    }

    /**
     * cursorAttrs != null ise DÜZ-KARAKTER modu: tablo/imaj/liste/paragraf
     * korunur, karakter stili cursorAttrs'a indirgenir (Formatsız Yapıştır).
     */
    static boolean insert(Object editor, UdeDoc.Document model, AttributeSet cursorAttrs) {
        AttributeSet prev = CURSOR_ATTRS;
        AttributeSet prevPara = CURSOR_PARA_ATTRS;
        CURSOR_ATTRS = cursorAttrs;
        try {
            javax.swing.text.JTextComponent tc = (javax.swing.text.JTextComponent) editor;
            StyledDocument doc = (StyledDocument) tc.getDocument();
            // Seçili metnin ÜZERİNE yapıştırma = seçimi DEĞİŞTİR (Word/standart
            // editör semantiği). Aksi halde getCaretPosition() seçim ucunda kalır
            // → metin seçimin yanına EKLENİR (kullanıcının "var olanı silmiyor"
            // şikâyeti). doc.remove güvenli (insertPlainString/TextReplace deseni;
            // moveDot YOK). start seçim başı olur — snapshotParaFormat/offset-0
            // tablo kontrolü doğru paragraftan okunur.
            int selS = Math.min(tc.getSelectionStart(), tc.getSelectionEnd());
            int selE = Math.max(tc.getSelectionStart(), tc.getSelectionEnd());
            int start;
            if (selE > selS) {
                doc.remove(selS, selE - selS);
                start = selS;
            } else {
                start = tc.getCaretPosition();
            }
            // Düz mod: imlecin paragraf biçimini ekleme ÖNCESİ (tablo sentinel'inden
            // de önce) yakala — metnin gerçekten ineceği paragraftan okunmalı.
            if (cursorAttrs != null) CURSOR_PARA_ATTRS = snapshotParaFormat(doc, start);
            List<Block> body = trimEmpties(model.body);
            // Tablo belgenin İLK öğesi olarak eklenirse (offset 0'da, üstünde
            // paragraf yokken) UDE'nin tablo-silme primitifi (DocumentEx.f /
            // "Tablo Sil") onu KALDIRAMAZ: satır içeriğini taşıyacak bir üst
            // paragraf bulamaz, "Nowhere to place the list" fırlatır. Boş belge
            // bağlamında offset 0 normal paragraftır → insertString(0,"\n") temiz
            // bir baş paragraf yaratır (tablo bağlamı değil; hücre bölünmez),
            // tabloyu offset 1'e iter → Backspace ile silinebilir olur.
            if (start == 0 && !body.isEmpty() && body.get(0) instanceof Table) {
                doc.insertString(0, "\n", DEFAULT_BREAK);
                start = 1;
            }
            int delta = insertBlocks(editor, doc, body, start);
            // Yapıştırılan fontları şerit font kutusunda SEÇİLEBİLİR yap: macOS'ta
            // YÜKLÜ OLMAYAN fontlar (Calibri/Aptos/Cambria…) kutu listesinde yoktur →
            // caret bu metne inince UDE'nin setSelectedItem(ad) çağrısı sessizce
            // başarısız olur, kutu eski değerde (Times) takılı kalır. Font adlarını
            // kutu modeline ekleyince UDE'nin mevcut senkronu adı gösterebilir (Word
            // da yüklü olmayan font adlarını gösterir). Render DEĞİŞMEZ (yedek fontla
            // çizim aynı kalır); yalnız kutu metni doğrulanır. setCaretPosition'dan
            // ÖNCE çağrılır ki caret senkronu eklenmiş adı bulsun.
            Set<String> fams = new HashSet<String>();
            collectFamilies(body, fams);
            ensureFontsSelectable(tc, fams);
            // İmleci eklenen içeriğin SONUNA al: resim ekleme (insertImage) caret'i
            // ekleme noktasına taşır, sonraki içerik (tablo/paragraf) doc.insertString
            // ile eklenince caret onları takip etmez → imleç belge ortasında kalırdı.
            try { tc.setCaretPosition(Math.min(start + delta, doc.getLength())); } catch (Throwable ignore) { }
            return true;
        } catch (Throwable t) {
            PrLog.log("NativeInsert.insert", t);
            return false;
        } finally {
            CURSOR_ATTRS = prev;
            CURSOR_PARA_ATTRS = prevPara;
        }
    }

    /**
     * Baştaki/sondaki boş paragrafları atar ve ardışık 2+ boş paragrafı tek'e
     * indirir (Word fazladan boş satır üretiyor). Boş = liste değil + tüm run'lar
     * boş/yalnız-boşluk. Tablo/resimli paragraflar korunur.
     */
    private static List<Block> trimEmpties(List<Block> blocks) {
        List<Block> out = new ArrayList<>();
        boolean prevEmpty = false;
        for (Block b : blocks) {
            boolean empty = isEmptyPara(b);
            if (empty && (out.isEmpty() || prevEmpty)) continue;   // baş + ardışık
            out.add(b);
            prevEmpty = empty;
        }
        while (!out.isEmpty() && isEmptyPara(out.get(out.size() - 1))) {
            out.remove(out.size() - 1);                            // son
        }
        return out;
    }

    private static boolean isEmptyPara(Block b) {
        if (!(b instanceof Paragraph)) return false;
        Paragraph p = (Paragraph) b;
        if (p.list != null) return false;
        for (Run r : p.runs) {
            if (r instanceof UdeDoc.ImageRun) return false;
            if (r instanceof TextRun && !clean(((TextRun) r).text).trim().isEmpty()) return false;
        }
        return true;
    }

    /** Blokları sırayla pos'a ekler; eklenen toplam uzunluğu döndürür. */
    private static int insertBlocks(Object editor, StyledDocument doc, List<Block> blocks, int pos) throws Exception {
        int start = pos;
        for (Block b : blocks) {
            if (b instanceof Paragraph) {
                pos = insertParagraph(editor, doc, (Paragraph) b, pos);
            } else if (b instanceof Table) {
                pos = insertTable(editor, doc, (Table) b, pos);
            } else if (b instanceof UdeDoc.PageBreak) {
                doc.insertString(pos, "\n", DEFAULT_BREAK);
                pos += 1;
            }
        }
        return pos - start;
    }

    private static int insertParagraph(Object editor, StyledDocument doc, Paragraph para, int pos) throws Exception {
        int paraStart = pos;
        for (Run r : para.runs) {
            if (r instanceof TextRun) {
                TextRun tr = (TextRun) r;
                String t = clean(tr.text);
                doc.insertString(pos, t, charAttrs(tr.style));
                pos += t.length();
            } else if (r instanceof UdeDoc.ImageRun) {
                pos += insertImage(editor, doc, (UdeDoc.ImageRun) r, pos);
            }
            // TabRun şimdilik atlanır
        }
        // paragraf sonlandırıcı ÖNCE eklenir ki paragraf \n ile sınırlansın;
        // aksi halde liste/hizalama öznitelikleri bir sonraki paragrafa sızar
        // (bullet+number aynı paragrafa binip yanlış işaret çizilir). "\n" GERÇEK
        // bir font taşır (breakAttrs) — fontsuz "\n" Monospaced'e düşürür (bkz.
        // DEFAULT_BREAK notu): caret bu satıra indiğinde yazım daktilo fontuyla çıkardı.
        doc.insertString(pos, "\n", breakAttrs(para));
        pos += 1;
        // paragraf öznitelikleri — replace=TRUE: UDE her yeni paragrafı bir öncekinin
        // özniteliklerini MİRAS alarak kurar; replace=false yalnız EKLER → bullet bir
        // sonraki (numara/düz) paragrafa sızar (numara yerine bullet, "bitti." de
        // madde işaretli çıkar). TRUE ile her paragrafın işareti tam kontrol edilir.
        AttributeSet pAttrs = (CURSOR_PARA_ATTRS != null) ? paraAttrsPlain(para) : paraAttrs(para);
        doc.setParagraphAttributes(paraStart, pos - paraStart, pAttrs, true);
        return pos;
    }

    private static int insertTable(Object editor, StyledDocument doc, Table table, int pos) throws Exception {
        int before = doc.getLength();
        int rows = table.rows.size();
        int cols = table.columns;
        int[] colW = new int[cols];                  // 5. param: uzunluk = SÜTUN sayısı
        for (int i = 0; i < cols; i++) {
            colW[i] = (i < table.columnWidths.size()) ? table.columnWidths.get(i) : 100;
        }
        // 6. param PER-SATIR dizidir (uzunluk = SATIR sayısı), "rowN" adları.
        // DocumentEx.a gövdesi bunu p3(rows) ile karşılaştırır ve eşleşmezse p4(cols)
        // uzunlukta YENİDEN kurar (vendor bug) → rows>cols ise satır döngüsü
        // index=rows-1'i length=cols dizide arar → AIOOBE. Uzunluğu rows vererek
        // yeniden-kurmayı engelle (kare tablolar bu hatayı gizliyordu).
        String[] rowStyles = new String[rows];
        for (int i = 0; i < rows; i++) rowStyles[i] = "row" + (i + 1);

        // tablo öznitelikleri (ae.x=ad, ae.w=genişlikler, ae.z=kenarlık)
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        Object widthsStr = utilsWidths(colW);
        aeSet("x", attrs, "Sabit");
        if (widthsStr != null) aeSet("w", attrs, (String) widthsStr);
        aeSet("z", attrs, table.border.name);   // borderCell / borderNone

        // YEREL tablo kur (imza: pos, ad, rows, cols, colWidths[cols], rowStyles[rows], attrs)
        tableBuild(doc, pos, "Sabit", rows, cols, colW, rowStyles, attrs);

        // hücreleri bul (table→row→cell→ilk paragraf offset) ve içerikle doldur.
        // Tablo, pos'tan itibaren eklendi; tablo elementini bul.
        Element root = doc.getDefaultRootElement();
        Element tableEl = findTableAt(root, pos);
        if (tableEl != null) {
            // hücre içeriklerini SONDAN başa yaz (offset kayması olmasın)
            List<int[]> jobs = new ArrayList<>();   // {cellParaOffset, cellIndex}
            List<TableCell> cells = new ArrayList<>();
            collectCells(tableEl, table, jobs, cells);
            for (int i = jobs.size() - 1; i >= 0; i--) {
                int off = jobs.get(i)[0];
                fillCell(editor, doc, cells.get(i), off);
            }
        }
        // tablo + sonrası: yeni belge uzunluğu farkı kadar ilerle
        int added = doc.getLength() - before;
        return pos + added;
    }

    /** Hücre içeriğini (paragraflar) hücrenin ilk-paragraf offset'ine yazar. */
    private static void fillCell(Object editor, StyledDocument doc, TableCell cell, int offset) throws Exception {
        int pos = offset;
        boolean first = true;
        for (Block b : cell.content) {
            if (!(b instanceof Paragraph)) continue;
            Paragraph p = (Paragraph) b;
            if (!first) { doc.insertString(pos, "\n", breakAttrs(p)); pos += 1; }
            first = false;
            int ps = pos;
            for (Run r : p.runs) {
                if (r instanceof TextRun) {
                    TextRun tr = (TextRun) r;
                    String t = clean(tr.text);
                    doc.insertString(pos, t, charAttrs(tr.style));
                    pos += t.length();
                } else if (r instanceof UdeDoc.ImageRun) {
                    pos += insertImage(editor, doc, (UdeDoc.ImageRun) r, pos);
                }
            }
            if (pos > ps) doc.setParagraphAttributes(ps, pos - ps, paraAttrs(p), false);
        }
    }

    /** pos'u kapsayan en içteki 'table' elementini bulur. */
    private static Element findTableAt(Element e, int pos) {
        if ("table".equals(e.getName()) && e.getStartOffset() <= pos && pos <= e.getEndOffset()) {
            return e;
        }
        for (int i = 0; i < e.getElementCount(); i++) {
            Element c = e.getElement(i);
            if (c.getStartOffset() <= pos && pos <= c.getEndOffset()) {
                Element r = findTableAt(c, pos);
                if (r != null) return r;
            }
        }
        return null;
    }

    /** Tablo elementindeki hücreleri model sırasıyla eşleyip ilk-paragraf offset'lerini toplar. */
    private static void collectCells(Element tableEl, Table model, List<int[]> jobs, List<TableCell> cells) {
        List<Element> domCells = new ArrayList<>();
        collectCellElements(tableEl, domCells);
        List<TableCell> modelCells = new ArrayList<>();
        for (TableRow r : model.rows) modelCells.addAll(r.cells);
        int n = Math.min(domCells.size(), modelCells.size());
        for (int i = 0; i < n; i++) {
            Element ce = domCells.get(i);
            if (ce.getElementCount() > 0) {
                jobs.add(new int[]{ ce.getElement(0).getStartOffset() });
                cells.add(modelCells.get(i));
            }
        }
    }

    private static void collectCellElements(Element e, List<Element> out) {
        if ("cell".equals(e.getName())) { out.add(e); return; }
        for (int i = 0; i < e.getElementCount(); i++) collectCellElements(e.getElement(i), out);
    }

    /**
     * Resim ekler: base64 → BufferedImage → sayfaya sığdır → editor.a(img,w,h)
     * (UDE'nin caret'e resim-ekleme primitifi; canlı editör caret'i text.l olduğundan
     * çalışır). Eklenen karakter sayısını (genelde 1) döndürür; başarısızsa 0.
     */
    private static int insertImage(Object editor, StyledDocument doc, UdeDoc.ImageRun ir, int pos) {
        try {
            if (ir.data == null || ir.data.isEmpty()) return 0;
            byte[] bytes = java.util.Base64.getDecoder().decode(ir.data.trim());
            java.awt.image.BufferedImage img =
                    javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (img == null) { PrLog.log("resim decode edilemedi"); return 0; }
            // Word'ün görüntü boyutunu (HTML width/height) onurlandır; yoksa doğal boyut.
            double w, h;
            if (ir.width > 1 && ir.height > 1) { w = ir.width; h = ir.height; }
            else { w = img.getWidth(); h = img.getHeight(); }
            double maxW = 480;   // sayfa yazılabilir genişliğine kaba sığdırma (pt)
            if (w > maxW) { h = h * maxW / w; w = maxW; }
            // caret'i ekleme noktasına al; UDE resmi caret'e ekler
            ((javax.swing.text.JTextComponent) editor).setCaretPosition(pos);
            int before = doc.getLength();
            if (imageInsertM == null) {
                imageInsertM = editor.getClass().getMethod("a",
                        java.awt.image.BufferedImage.class, float.class, float.class);
            }
            imageInsertM.invoke(editor, img, (float) w, (float) h);
            int delta = doc.getLength() - before;
            return delta > 0 ? delta : 0;
        } catch (Throwable t) {
            PrLog.log("insertImage", t);
            return 0;
        }
    }

    private static Method imageInsertM;

    /**
     * HTML metin kuralı: paragraf-içi satır sonları (\r\n) BOŞLUKTUR, satır sonu
     * değil (yalnız <br>/blok sınırları satır kırar). insertString \n'i yeni
     * paragraf sayar → Word liste itemlerini ("1.\nAd") ikiye böler. Bu yüzden
     * run metnindeki \r\n dizilerini tek boşluğa indir.
     */
    private static String clean(String t) {
        if (t == null) return "";
        return t.replaceAll("[\\r\\n]+", " ");
    }

    /** Paragraf öznitelikleri: hizalama + girintiler + aralık + liste işareti. */
    /**
     * İmlecin paragraf biçimini beyaz-liste ile yakalar. Task 1: yalnız LOKAL
     * (isDefined) attr. Task 3 mirası (getAttribute) ekler.
     */
    private static AttributeSet snapshotParaFormat(StyledDocument doc, int offset) {
        SimpleAttributeSet out = new SimpleAttributeSet();
        try {
            Element pe = doc.getParagraphElement(offset);
            AttributeSet as = pe.getAttributes();
            for (Object key : PARA_FORMAT_KEYS) {
                Object v = as.getAttribute(key);   // resolver zincirini izler (miras dahil)
                if (v != null) out.addAttribute(key, v);
            }
        } catch (Throwable ignore) { }
        return out;
    }

    /** src'de varsa anahtarı dst'ye kopyalar. */
    private static void copyIfPresent(AttributeSet src, MutableAttributeSet dst, Object key) {
        Object v = src.getAttribute(key);
        if (v != null) dst.addAttribute(key, v);
    }

    /**
     * Düz mod paragraf öznitelikleri. Task 1: TÜM paragraflara imleç biçimi
     * (CURSOR_PARA_ATTRS) + liste paragraflarında kaynak liste işaretleri.
     * (Task 2 liste girintisini kaynaktan korur — hanging indent.)
     */
    private static SimpleAttributeSet paraAttrsPlain(Paragraph p) {
        SimpleAttributeSet pa = new SimpleAttributeSet();
        AttributeSet cur = CURSOR_PARA_ATTRS;
        if (p.list == null) {
            if (cur != null) pa.addAttributes(cur);   // 8 anahtarın hepsi imleçten
            return pa;
        }
        // Liste: hizalama/aralık imleçten, girinti/tabset + işaretler KAYNAKTAN.
        if (cur != null) {
            for (Object key : LIST_CURSOR_KEYS) copyIfPresent(cur, pa, key);
        }
        SimpleAttributeSet src = paraAttrs(p);
        copyIfPresent(src, pa, StyleConstants.LeftIndent);
        copyIfPresent(src, pa, StyleConstants.RightIndent);
        copyIfPresent(src, pa, StyleConstants.FirstLineIndent);
        copyIfPresent(src, pa, StyleConstants.TabSet);
        copyIfPresent(src, pa, "Bulleted");
        copyIfPresent(src, pa, "Numbered");
        copyIfPresent(src, pa, "BulletType");
        copyIfPresent(src, pa, "NumberType");
        copyIfPresent(src, pa, "ListLevel");
        copyIfPresent(src, pa, "ListId");
        return pa;
    }

    private static SimpleAttributeSet paraAttrs(Paragraph p) {
        SimpleAttributeSet pa = new SimpleAttributeSet();
        StyleConstants.setAlignment(pa, p.alignment);
        if (p.leftIndent != 0) StyleConstants.setLeftIndent(pa, (float) p.leftIndent);
        if (p.rightIndent != 0) StyleConstants.setRightIndent(pa, (float) p.rightIndent);
        if (p.firstLineIndent != 0) StyleConstants.setFirstLineIndent(pa, (float) p.firstLineIndent);
        if (p.spaceBefore != 0) StyleConstants.setSpaceAbove(pa, (float) p.spaceBefore);
        if (p.spaceAfter != 0) StyleConstants.setSpaceBelow(pa, (float) p.spaceAfter);
        // GERÇEK UDE liste işareti (bullet/numara). Anahtarlar String (wp.model.ad
        // sabitleri), DEĞER TİPLERİ kritik: ListLevel=Integer, ListId=Long, aksi
        // halde UDE view yeniden-kurulumunda ClassCastException (bytecode'dan ölçüldü).
        if (p.list != null) {
            int level = Math.max(0, p.list.level);
            pa.addAttribute("ListLevel", Integer.valueOf(level));
            if ("numbered".equals(p.list.type)) {
                pa.addAttribute("Numbered", Boolean.TRUE);
                pa.addAttribute("NumberType", p.list.numberType != null ? p.list.numberType : "NUMBER_TYPE_NUMBER_DOT");
                long id = p.list.listId != null ? p.list.listId.longValue() : 1L;
                pa.addAttribute("ListId", Long.valueOf(id));
            } else {
                pa.addAttribute("Bulleted", Boolean.TRUE);
                pa.addAttribute("BulletType", p.list.bulletType != null ? p.list.bulletType : "BULLET_TYPE_ELLIPSE");
            }
        }
        return pa;
    }

    /**
     * Paragraf-sonu / yapısal "\n" karakterleri için öznitelik. ASLA null/boş
     * BIRAKMA: Swing'de FontFamily özniteliği OLMAYAN içeriği StyleConstants
     * "Monospaced"a düşürür (StyleConstants.getFontFamily varsayılanı) → caret bu
     * "\n"e/boş paragrafa indiğinde editörün giriş öznitelikleri fontsuz kalır,
     * sonraki TÜM yazım daktilo (Monospaced) fontuyla çıkar ve "düzelmez"
     * (her Enter yeni fontsuz "\n" ekler → kaskat). Bu yüzden her "\n" gerçek
     * bir font taşır: paragrafın son metin run'ının fontu, yoksa makul varsayılan.
     */
    private static final AttributeSet DEFAULT_BREAK;
    static {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setFontFamily(a, "Times New Roman");
        StyleConstants.setFontSize(a, 12);
        DEFAULT_BREAK = a;
    }

    /** Paragraf sonlandırıcı "\n" için: son metin run'ının fontu (yoksa varsayılan). */
    private static AttributeSet breakAttrs(Paragraph p) {
        for (int i = p.runs.size() - 1; i >= 0; i--) {
            Run r = p.runs.get(i);
            if (r instanceof TextRun) return charAttrs(((TextRun) r).style);
        }
        return DEFAULT_BREAK;
    }

    // ---- karakter öznitelikleri (StyleConstants) ----
    private static AttributeSet charAttrs(TextStyle s) {
        if (CURSOR_ATTRS != null) return CURSOR_ATTRS;   // düz-karakter modu
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setFontFamily(a, s.fontFamily);
        StyleConstants.setFontSize(a, (int) Math.round(s.fontSize));
        if (s.bold) StyleConstants.setBold(a, true);
        if (s.italic) StyleConstants.setItalic(a, true);
        if (s.underline) StyleConstants.setUnderline(a, true);
        if (s.color != -16777216) StyleConstants.setForeground(a, new Color(s.color, true));
        if (s.backgroundColor != -1) StyleConstants.setBackground(a, new Color(s.backgroundColor, true));
        return a;
    }

    // ---- şerit font kutusu: yüklü olmayan yapıştırma fontlarını seçilebilir yap ----

    /** Modeldeki tüm metin run'larının (tablo hücreleri dahil) font ailelerini toplar. */
    private static void collectFamilies(List<Block> blocks, Set<String> out) {
        for (Block b : blocks) {
            if (b instanceof Paragraph) {
                for (Run r : ((Paragraph) b).runs) {
                    if (r instanceof TextRun) out.add(((TextRun) r).style.fontFamily);
                }
            } else if (b instanceof Table) {
                for (TableRow row : ((Table) b).rows) {
                    for (TableCell c : row.cells) collectFamilies(c.content, out);
                }
            }
        }
    }

    /**
     * Editörün penceresindeki font kutularını (Times New Roman imzalı) bulur ve
     * verilen ailelerden modelde OLMAYANLARI ekler → caret senkronu o adı seçebilir.
     * Render'a/belgeye dokunmaz (yalnız kutu modeli). EDT'de çağrılmalı (paste EDT'de).
     */
    private static void ensureFontsSelectable(javax.swing.text.JTextComponent editor, Set<String> families) {
        try {
            if (families.isEmpty()) return;
            Window w = SwingUtilities.getWindowAncestor(editor);
            if (w == null) return;
            List<JComboBox> combos = new ArrayList<JComboBox>();
            findFontCombos(w, combos);
            for (JComboBox cb : combos) {
                ComboBoxModel m = cb.getModel();
                if (!(m instanceof MutableComboBoxModel)) continue;
                Set<String> have = new HashSet<String>();
                for (int i = 0; i < m.getSize(); i++) {
                    Object it = m.getElementAt(i);
                    if (it != null) have.add(it.toString());
                }
                for (String f : families) {
                    if (f != null && !f.isEmpty() && !have.contains(f)) {
                        ((MutableComboBoxModel) m).addElement(f);
                    }
                }
            }
        } catch (Throwable t) {
            PrLog.log("ensureFontsSelectable", t);
        }
    }

    /**
     * Font kutusu imzası: modelinde "Times New Roman" geçen JComboBox. Model
     * alfabetik sıralı (~192 öğe) → "Times New Roman" 100. indeksin ÖTESİNDE (T),
     * bu yüzden TÜM model taranır (ilk-N kısıtlaması kutuyu kaçırıyordu).
     */
    private static void findFontCombos(Component c, List<JComboBox> out) {
        if (c instanceof JComboBox) {
            ComboBoxModel m = ((JComboBox) c).getModel();
            for (int i = 0; i < m.getSize(); i++) {
                Object it = m.getElementAt(i);
                if (it != null && "Times New Roman".equals(it.toString())) { out.add((JComboBox) c); break; }
            }
        }
        if (c instanceof Container) {
            for (Component k : ((Container) c).getComponents()) findFontCombos(k, out);
        }
    }

    // ---- reflection köprüleri (UDE iç tipleri) ----
    private static Method tableBuildM;
    private static Method aeXM, aeWM, aeZM;
    private static Method utilsWidthsM;

    private static void tableBuild(StyledDocument doc, int pos, String name, int rows, int cols,
                                   int[] colW, String[] rowStyles, SimpleAttributeSet attrs) throws Exception {
        if (tableBuildM == null) {
            for (Method m : doc.getClass().getMethods()) {
                if (!m.getName().equals("a")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 7 && p[0] == int.class && p[1] == String.class && p[2] == int.class
                        && p[3] == int.class && p[4] == int[].class && p[5] == String[].class
                        && SimpleAttributeSet.class.isAssignableFrom(p[6])) {
                    tableBuildM = m; break;
                }
            }
            if (tableBuildM == null) throw new NoSuchMethodException("DocumentEx tablo-kurma metodu yok");
        }
        tableBuildM.invoke(doc, pos, name, rows, cols, colW, rowStyles, attrs);
    }

    private static void aeSet(String which, MutableAttributeSet attrs, String val) {
        try {
            Method m = which.equals("x") ? aeXM : which.equals("w") ? aeWM : aeZM;
            if (m == null) {
                Class<?> ae = Class.forName("tr.com.havelsan.uyap.system.swing.wp.model.ae");
                m = ae.getMethod(which, MutableAttributeSet.class, String.class);
                if (which.equals("x")) aeXM = m; else if (which.equals("w")) aeWM = m; else aeZM = m;
            }
            m.invoke(null, attrs, val);
        } catch (Throwable t) {
            PrLog.log("aeSet " + which, t);
        }
    }

    private static Object utilsWidths(int[] colW) {
        try {
            if (utilsWidthsM == null) {
                Class<?> u = Class.forName("tr.com.havelsan.uyap.system.editor.common.Utils");
                utilsWidthsM = u.getMethod("a", int[].class);
            }
            return utilsWidthsM.invoke(null, (Object) colW);
        } catch (Throwable t) {
            PrLog.log("utilsWidths", t);
            return null;
        }
    }

    private NativeInsert() {
    }
}
