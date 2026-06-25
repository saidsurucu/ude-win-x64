package macospasterich;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import macospasterich.UdeDoc.Block;
import macospasterich.UdeDoc.BorderStyle;
import macospasterich.UdeDoc.Document;
import macospasterich.UdeDoc.ImageRun;
import macospasterich.UdeDoc.ListProps;
import macospasterich.UdeDoc.PageBreak;
import macospasterich.UdeDoc.Paragraph;
import macospasterich.UdeDoc.Table;
import macospasterich.UdeDoc.TableCell;
import macospasterich.UdeDoc.TableRow;
import macospasterich.UdeDoc.TabRun;
import macospasterich.UdeDoc.TextRun;
import macospasterich.UdeDoc.TextStyle;

/**
 * HTML → UDE belge modeli ayrıştırıcı. Hoşgörülü tokenizer'dan (Html) SAX olayları
 * alır, bağlam ve stil yığınlarıyla modeli kurar. Saf java.*; harici bağımlılık yok.
 *
 * Kaynak-bağımsız: Word (inline + &lt;b&gt;/&lt;i&gt; + mso-highlight), Google Docs
 * (her şey &lt;span style="font-weight:700"&gt; ile + &lt;b style="font-weight:normal"&gt;
 * sarmalayıcı), Pages (&lt;style&gt; bloğu + class kuralları), ChatGPT/Claude
 * (&lt;strong&gt;/&lt;em&gt; + inline) — hepsini aynı boru hattıyla çözer.
 */
final class HtmlToUde implements Html.Handler {

    // ---- stil çerçevesi (bold/italic/underline ÜÇ DURUMLU: null=miras) ----
    private static final class Frame {
        Boolean bold, italic, underline;
        String fontFamily;        // null = miras
        Double fontSize;          // null = miras
        Integer color;            // null
        Integer backgroundColor;  // null
    }

    // ---- bağlamlar ----
    private abstract static class Ctx { }
    private static final class RootCtx extends Ctx { final List<Block> blocks = new ArrayList<>(); }
    private static final class ParaCtx extends Ctx { final Paragraph p; ParaCtx(Paragraph p){ this.p = p; } }
    private static final class TableCtx extends Ctx { final List<TableRow> rows = new ArrayList<>(); boolean hasBorder; }
    private static final class RowCtx extends Ctx { final List<TableCell> cells = new ArrayList<>(); }
    private static final class CellCtx extends Ctx {
        final List<Block> blocks = new ArrayList<>();
        Integer colspan; Integer fillColor; String verticalAlign;
        BorderStyle border; Double borderWidth; Integer borderColor;
    }
    private static final class ListCtx extends Ctx {
        final String listType; final int level; final int listId; final List<Block> blocks = new ArrayList<>();
        ListCtx(String t, int l, int id){ listType = t; level = l; listId = id; }
    }
    private static final class DivCtx extends Ctx {
        final List<Block> blocks = new ArrayList<>(); boolean pendingAfter;
    }

    private final List<Frame> styleStack = new ArrayList<>();
    private final List<Ctx> ctxStack = new ArrayList<>();
    private boolean insideTh = false;
    private int listIdSeq = 0;   // her <ul>/<ol> için benzersiz ListId (UDE numara sürekliliği)
    private ListProps pendingList;   // <li>'nin işareti; sonraki paragraf (genelde iç <p>) devralır

    /** &lt;style&gt; bloğundan toplanan CSS sınıf kuralları: className → bildirimler. */
    private final Map<String, Map<String, String>> classRules = new HashMap<>();

    private static final Map<String, Integer> HEADING = new HashMap<>();
    static {
        HEADING.put("h1", 24); HEADING.put("h2", 20); HEADING.put("h3", 16);
        HEADING.put("h4", 14); HEADING.put("h5", 12); HEADING.put("h6", 10);
    }

    static Document convert(String html) {
        HtmlToUde p = new HtmlToUde();
        p.ctxStack.add(new RootCtx());
        Html.parse(html, p);
        // kalan açık paragrafları kök'e boşalt
        while (p.ctxStack.size() > 1) {
            Ctx c = p.pop();
            if (c instanceof ParaCtx) p.addBlock(((ParaCtx) c).p);
        }
        Document doc = new Document();
        doc.body = ((RootCtx) p.ctxStack.get(0)).blocks;
        return doc;
    }

    // ---- stil yığını ----
    private Frame currentStyle() {
        return styleStack.isEmpty() ? new Frame() : styleStack.get(styleStack.size() - 1);
    }

    private void pushStyle(Frame o) {
        Frame parent = currentStyle();
        Frame f = new Frame();
        f.bold = (o != null && o.bold != null) ? o.bold : parent.bold;
        f.italic = (o != null && o.italic != null) ? o.italic : parent.italic;
        f.underline = (o != null && o.underline != null) ? o.underline : parent.underline;
        f.fontFamily = (o != null && o.fontFamily != null) ? o.fontFamily : parent.fontFamily;
        f.fontSize = (o != null && o.fontSize != null) ? o.fontSize : parent.fontSize;
        f.color = (o != null && o.color != null) ? o.color : parent.color;
        f.backgroundColor = (o != null && o.backgroundColor != null) ? o.backgroundColor : parent.backgroundColor;
        styleStack.add(f);
    }

    private void popStyle() {
        if (!styleStack.isEmpty()) styleStack.remove(styleStack.size() - 1);
    }

    private TextStyle mergedTextStyle() {
        Frame fr = currentStyle();
        TextStyle s = new TextStyle();
        s.fontFamily = fr.fontFamily != null ? fr.fontFamily : "Times New Roman";
        s.fontSize = fr.fontSize != null ? fr.fontSize : 12;
        s.bold = Boolean.TRUE.equals(fr.bold) || insideTh;
        s.italic = Boolean.TRUE.equals(fr.italic);
        s.underline = Boolean.TRUE.equals(fr.underline);
        s.color = fr.color != null ? fr.color : -16777216;
        s.backgroundColor = fr.backgroundColor != null ? fr.backgroundColor : -1;
        return s;
    }

    // ---- bağlam yığını ----
    private Ctx current() { return ctxStack.get(ctxStack.size() - 1); }
    private void push(Ctx c) { ctxStack.add(c); }
    private Ctx pop() { return ctxStack.remove(ctxStack.size() - 1); }

    private ParaCtx findParagraph() {
        for (int i = ctxStack.size() - 1; i >= 0; i--) {
            if (ctxStack.get(i) instanceof ParaCtx) return (ParaCtx) ctxStack.get(i);
        }
        return null;
    }

    private ParaCtx ensureParagraph() {
        ParaCtx existing = findParagraph();
        if (existing != null) return existing;
        Paragraph p = new Paragraph();
        if (pendingList != null) { p.list = pendingList; pendingList = null; }
        ParaCtx ctx = new ParaCtx(p);
        push(ctx);
        return ctx;
    }

    private void addBlock(Block block) {
        for (int i = ctxStack.size() - 1; i >= 0; i--) {
            Ctx c = ctxStack.get(i);
            if (c instanceof RootCtx) { ((RootCtx) c).blocks.add(block); return; }
            if (c instanceof CellCtx) { ((CellCtx) c).blocks.add(block); return; }
            if (c instanceof ListCtx) { ((ListCtx) c).blocks.add(block); return; }
            if (c instanceof DivCtx) { ((DivCtx) c).blocks.add(block); return; }
        }
    }

    private int currentListLevel() {
        int level = -1;
        for (Ctx c : ctxStack) if (c instanceof ListCtx) level++;
        return Math.max(level, 0);
    }

    private ListCtx findList() {
        for (int i = ctxStack.size() - 1; i >= 0; i--) {
            if (ctxStack.get(i) instanceof ListCtx) return (ListCtx) ctxStack.get(i);
        }
        return null;
    }

    private boolean inCell() {
        for (Ctx c : ctxStack) if (c instanceof CellCtx) return true;
        return false;
    }

    private TableCtx currentTable() {
        for (int i = ctxStack.size() - 1; i >= 0; i--) {
            if (ctxStack.get(i) instanceof TableCtx) return (TableCtx) ctxStack.get(i);
        }
        return null;
    }

    // ---- stil çözümleme (class kuralları + inline; inline kazanır) ----

    /** &lt;style&gt; bloğu: `selector { decls }` kurallarından class → bildirimler topla. */
    @Override
    public void onStyle(String css) {
        if (css == null || css.isEmpty()) return;
        css = css.replaceAll("(?s)/\\*.*?\\*/", "");   // CSS yorumları
        int i = 0, n = css.length();
        while (i < n) {
            int brace = css.indexOf('{', i);
            if (brace < 0) break;
            String sel = css.substring(i, brace).trim();
            int close = css.indexOf('}', brace + 1);
            if (close < 0) break;
            Map<String, String> decls = Css.parseInlineStyle(css.substring(brace + 1, close));
            if (!decls.isEmpty()) {
                Matcher m = CLASS_SEL.matcher(sel);
                while (m.find()) {
                    classRules.computeIfAbsent(m.group(1), k -> new HashMap<>()).putAll(decls);
                }
            }
            i = close + 1;
        }
    }

    private static final Pattern CLASS_SEL = Pattern.compile("\\.([A-Za-z0-9_-]+)");

    /** Bir elemanın class kurallarını + inline style'ını birleşik haritaya çözer (inline kazanır). */
    private Map<String, String> resolved(Map<String, String> attribs) {
        Map<String, String> out = new HashMap<>();
        if (!classRules.isEmpty()) {
            String cls = attribs.get("class");
            if (cls != null) {
                for (String c : cls.trim().split("\\s+")) {
                    Map<String, String> r = classRules.get(c);
                    if (r != null) out.putAll(r);
                }
            }
        }
        String style = attribs.get("style");
        if (style != null) out.putAll(Css.parseInlineStyle(style));
        return out;
    }

    /** Etiket varsayılanları + çözülmüş stilden bir karakter stili çerçevesi kurar. */
    private Frame frameOf(String tag, Map<String, String> attribs) {
        Frame f = new Frame();
        if (tag.equals("b") || tag.equals("strong")) f.bold = Boolean.TRUE;
        else if (tag.equals("i") || tag.equals("em")) f.italic = Boolean.TRUE;
        else if (tag.equals("u") || tag.equals("ins")) f.underline = Boolean.TRUE;
        if (tag.equals("font")) {
            String c = attribs.get("color");
            if (c != null && !c.isEmpty()) f.color = Css.cssToArgb(c);
            String face = attribs.get("face");
            if (face != null && !face.isEmpty()) f.fontFamily = cleanFamily(face);
        }
        applyDecls(f, resolved(attribs));
        return f;
    }

    /** CSS bildirimlerini çerçeveye uygular (font-weight/style/text-decoration dahil). */
    private void applyDecls(Frame f, Map<String, String> ps) {
        if (ps.isEmpty()) return;
        if (ps.containsKey("font")) applyFontShorthand(f, ps.get("font"));
        if (ps.containsKey("fontFamily")) {
            String fam = cleanFamily(ps.get("fontFamily"));
            if (fam != null) f.fontFamily = fam;
        }
        if (ps.containsKey("fontSize")) f.fontSize = Css.parseFontSize(ps.get("fontSize"));
        if (ps.containsKey("fontWeight")) {
            Boolean b = isBoldWeight(ps.get("fontWeight"));
            if (b != null) f.bold = b;
        }
        if (ps.containsKey("fontStyle")) {
            Boolean it = isItalicStyle(ps.get("fontStyle"));
            if (it != null) f.italic = it;
        }
        String td = ps.containsKey("textDecoration") ? ps.get("textDecoration")
                : ps.get("textDecorationLine");
        if (td != null) {
            String t = td.toLowerCase();
            if (t.contains("underline")) f.underline = Boolean.TRUE;
            else if (t.contains("none")) f.underline = Boolean.FALSE;
        }
        if (ps.containsKey("color")) f.color = Css.cssToArgb(ps.get("color"));
        Integer hl = highlight(ps);
        if (hl != null) f.backgroundColor = hl;
    }

    private static final Pattern FONT_SH =
            Pattern.compile("([\\d.]+)(px|pt|em|rem)(?:/[\\d.]+[a-z%]*)?\\s+(.+)$", Pattern.CASE_INSENSITIVE);

    /** `[style] [weight] size[/lh] family` kısayolunu çözer (Pages: `12.0px 'Helvetica'`). */
    private void applyFontShorthand(Frame f, String v) {
        if (v == null) return;
        String low = v.toLowerCase();
        if (low.matches(".*\\b(italic|oblique)\\b.*")) f.italic = Boolean.TRUE;
        if (low.matches(".*\\bbold(er)?\\b.*")) f.bold = Boolean.TRUE;
        Matcher m = FONT_SH.matcher(v.trim());
        if (m.find()) {
            f.fontSize = Css.parseFontSize(m.group(1) + m.group(2));
            String fam = cleanFamily(m.group(3));
            if (fam != null) f.fontFamily = fam;
        }
    }

    private static Boolean isBoldWeight(String v) {
        String s = v.trim().toLowerCase();
        if (s.equals("bold") || s.equals("bolder")) return Boolean.TRUE;
        if (s.equals("normal") || s.equals("lighter")) return Boolean.FALSE;
        try { return Integer.parseInt(s) >= 600 ? Boolean.TRUE : Boolean.FALSE; }
        catch (NumberFormatException e) { return null; }
    }

    private static Boolean isItalicStyle(String v) {
        String s = v.trim().toLowerCase();
        if (s.startsWith("italic") || s.startsWith("oblique")) return Boolean.TRUE;
        if (s.startsWith("normal")) return Boolean.FALSE;
        return null;
    }

    /** İlk font ailesini al, tırnakları soy (UDE tek aile adı bekler). */
    private static String cleanFamily(String raw) {
        if (raw == null) return null;
        String first = raw.split(",")[0].trim();
        if (first.length() >= 2) {
            char a = first.charAt(0), b = first.charAt(first.length() - 1);
            if ((a == '\'' || a == '"') && a == b) first = first.substring(1, first.length() - 1).trim();
        }
        return first.isEmpty() ? null : first;
    }

    /**
     * Vurgu/arka-plan rengini çıkarır. Word highlight'ı `mso-highlight:yellow` ve
     * `background:yellow` (kısayol) ile gelir — `background-color` DEĞİL. Tek-token
     * renk değilse / none/transparent/white ise null (vurgu yok).
     */
    private static Integer highlight(Map<String, String> ps) {
        String v = ps.get("msoHighlight");
        if (v == null || isNoHl(v)) v = ps.get("background");
        if (v == null || isNoHl(v)) v = ps.get("backgroundColor");
        if (v == null || isNoHl(v)) return null;
        v = v.trim();
        // Çok-DEĞERLİ background kısayolunu (resim vb.) atla AMA `rgb(255, 153, 0)`
        // gibi parantez-içi boşluklu tek rengi koru: boşluğu parantez DIŞINDA ara.
        if (v.replaceAll("\\([^)]*\\)", "").trim().indexOf(' ') >= 0) return null;
        int argb = Css.cssToArgb(v);
        if (argb == -1 || argb == -16777216) return null;   // beyaz/siyah = vurgu yok say
        return argb;
    }

    private static boolean isNoHl(String v) {
        if (v == null) return true;
        String s = v.trim().toLowerCase();
        return s.isEmpty() || s.equals("none") || s.equals("transparent")
                || s.equals("white") || s.equals("#fff") || s.equals("#ffffff") || s.equals("windowtext");
    }

    /** Stil haritasında görünür (none olmayan) bir kenarlık var mı? */
    private static boolean styleHasVisibleBorder(Map<String, String> ps) {
        String[] keys = {"border", "borderTop", "borderBottom", "borderLeft", "borderRight", "borderStyle"};
        for (String k : keys) {
            String v = ps.get(k);
            if (v == null) continue;
            String lv = v.toLowerCase();
            if (lv.contains("solid") || lv.contains("dashed") || lv.contains("dotted") || lv.contains("double")) {
                return true;
            }
        }
        return false;
    }

    // ---- SAX handler ----
    @Override
    public void onStart(String name, Map<String, String> attribs, boolean selfClosing) {
        String tag = name.toLowerCase();

        if (tag.equals("strong") || tag.equals("b") || tag.equals("em") || tag.equals("i")
                || tag.equals("u") || tag.equals("ins") || tag.equals("span") || tag.equals("font")) {
            pushStyle(frameOf(tag, attribs));
            return;
        }

        if (HEADING.containsKey(tag)) {
            Frame f = frameOf(tag, attribs);
            if (f.bold == null) f.bold = Boolean.TRUE;
            if (f.fontSize == null) f.fontSize = (double) HEADING.get(tag);
            pushStyle(f);
            push(new ParaCtx(new Paragraph()));
            return;
        }

        if (tag.equals("p")) {
            Paragraph para = new Paragraph();
            if (pendingList != null) { para.list = pendingList; pendingList = null; }  // <li><p>… → p liste öğesi olur
            Map<String, String> ps = resolved(attribs);
            applyParagraphStyle(para, ps);
            pushStyle(frameOf("p", attribs));
            push(new ParaCtx(para));
            return;
        }

        if (tag.equals("br")) {
            ParaCtx paraCtx = findParagraph();
            if (paraCtx != null) {
                Ctx ctx = pop();
                if (ctx instanceof ParaCtx) {
                    addBlock(((ParaCtx) ctx).p);
                    push(new ParaCtx(new Paragraph()));
                } else {
                    push(ctx);
                    paraCtx.p.runs.add(new TextRun("", mergedTextStyle()));
                }
            } else {
                addBlock(new Paragraph());
            }
            return;
        }

        if (tag.equals("tab")) {
            ParaCtx paraCtx = ensureParagraph();
            paraCtx.p.runs.add(new TabRun(mergedTextStyle()));
            return;
        }

        if (tag.equals("page-break")) {
            if (inCell()) return;
            while (current() instanceof ParaCtx) addBlock(((ParaCtx) pop()).p);
            addBlock(new PageBreak());
            return;
        }

        if (tag.equals("img")) {
            String src = attribs.getOrDefault("src", "");
            int comma = src.indexOf(";base64,");
            if (src.startsWith("data:image/") && comma >= 0) {
                ImageRun img = new ImageRun();
                img.data = src.substring(comma + ";base64,".length());
                img.width = parseDoubleOr(attribs.get("width"), 100);
                img.height = parseDoubleOr(attribs.get("height"), 100);
                ensureParagraph().p.runs.add(img);
            }
            return;
        }

        if (tag.equals("table")) {
            TableCtx tc = new TableCtx();
            String ba = attribs.get("border");           // HTML <table border="1">
            if (ba != null && !ba.trim().isEmpty() && !ba.trim().equals("0")) tc.hasBorder = true;
            if (styleHasVisibleBorder(resolved(attribs))) tc.hasBorder = true;
            push(tc);
            return;
        }
        if (tag.equals("tr")) { push(new RowCtx()); return; }

        if (tag.equals("td") || tag.equals("th")) {
            if (tag.equals("th")) insideTh = true;
            CellCtx cc = new CellCtx();
            Integer cs = parseIntOrNull(attribs.get("colspan"));
            if (cs != null && cs > 0) cc.colspan = cs;
            Map<String, String> ps = resolved(attribs);
            if (!ps.isEmpty()) {
                if (styleHasVisibleBorder(ps)) {
                    TableCtx t = currentTable();
                    if (t != null) t.hasBorder = true;     // hücre kenarlığı → tablo görünür
                }
                if (ps.containsKey("backgroundColor")) cc.fillColor = Css.cssToArgb(ps.get("backgroundColor"));
                if (ps.containsKey("verticalAlign")) cc.verticalAlign = parseVerticalAlign(ps.get("verticalAlign"));
                if (ps.containsKey("border")) {
                    double[] b = parseBorderCss(ps.get("border"));
                    cc.borderWidth = b[0];
                    cc.borderColor = (int) b[1];
                    cc.border = new BorderStyle("borderCell", borderStyleName((int) b[2]));
                }
                if (ps.containsKey("borderStyle")) {
                    String bs = ps.get("borderStyle");
                    cc.border = new BorderStyle("borderCell",
                            bs.equals("none") ? "borderStyle-none" : "borderStyle-" + bs);
                }
            }
            // hücre içi metin stili (Docs hücre fontunu td/class üzerinde taşıyabilir)
            pushStyle(frameOf("td", attribs));
            push(cc);
            return;
        }

        if (tag.equals("ul")) {
            int level = currentListLevel() + (findList() != null ? 1 : 0);
            push(new ListCtx("bulleted", level, ++listIdSeq));
            return;
        }
        if (tag.equals("ol")) {
            int level = currentListLevel() + (findList() != null ? 1 : 0);
            push(new ListCtx("numbered", level, ++listIdSeq));
            return;
        }
        if (tag.equals("li")) {
            // Liste işaretini BEKLEMEYE al; öğenin İLK paragrafı (genelde iç <p>,
            // bazen doğrudan metin) devralsın — yoksa boş işaretli paragraf + ayrı
            // metin paragrafı oluşup içerik alt satıra düşer (Google Docs yapısı).
            ListCtx lc = findList();
            if (lc != null) {
                ListProps lp = new ListProps();
                lp.type = lc.listType;
                lp.level = lc.level;
                lp.listId = lc.listId;
                if (lc.listType.equals("bulleted")) lp.bulletType = "BULLET_TYPE_ELLIPSE";
                else lp.numberType = "NUMBER_TYPE_NUMBER_DOT";
                pendingList = lp;
            }
            pushStyle(frameOf("li", attribs));
            return;
        }

        if (tag.equals("div")) {
            DivCtx dc = new DivCtx();
            Map<String, String> ps = resolved(attribs);
            if (!ps.isEmpty()) {
                boolean cell = inCell();
                String pbb = lower(ps.get("pageBreakBefore"));
                if (!cell && (pbb.equals("always") || pbb.equals("page"))) {
                    while (current() instanceof ParaCtx) addBlock(((ParaCtx) pop()).p);
                    addBlock(new PageBreak());
                }
                String pba = lower(ps.get("pageBreakAfter"));
                if (!cell && (pba.equals("always") || pba.equals("page"))) dc.pendingAfter = true;
            }
            pushStyle(frameOf("div", attribs));
            push(dc);
            return;
        }
    }

    @Override
    public void onText(String text) {
        if (text == null || text.isEmpty()) return;
        ParaCtx paraCtx = findParagraph();
        if (paraCtx == null && text.trim().isEmpty()) return;
        ParaCtx target = paraCtx != null ? paraCtx : ensureParagraph();
        target.p.runs.add(new TextRun(text, mergedTextStyle()));
    }

    @Override
    public void onEnd(String name) {
        String tag = name.toLowerCase();

        if (tag.equals("strong") || tag.equals("b") || tag.equals("em") || tag.equals("i")
                || tag.equals("u") || tag.equals("ins") || tag.equals("span") || tag.equals("font")) {
            popStyle();
            return;
        }

        if (HEADING.containsKey(tag)) {
            popStyle();
            Ctx ctx = pop();
            if (ctx instanceof ParaCtx) addBlock(((ParaCtx) ctx).p);
            return;
        }

        if (tag.equals("p")) {
            Ctx ctx = pop();
            if (ctx instanceof ParaCtx) addBlock(((ParaCtx) ctx).p);
            popStyle();
            return;
        }

        if (tag.equals("td") || tag.equals("th")) {
            if (tag.equals("th")) insideTh = false;
            while (current() instanceof ParaCtx) addBlock(((ParaCtx) pop()).p);
            Ctx ctx = pop();
            popStyle();
            if (ctx instanceof CellCtx) {
                CellCtx cc = (CellCtx) ctx;
                TableCell cell = new TableCell(cc.blocks);
                if (cc.colspan != null) cell.colspan = cc.colspan;
                if (cc.fillColor != null) cell.fillColor = cc.fillColor;
                if (cc.verticalAlign != null) cell.verticalAlign = cc.verticalAlign;
                if (cc.border != null) cell.border = cc.border;
                if (cc.borderWidth != null) cell.borderWidth = cc.borderWidth;
                if (cc.borderColor != null) cell.borderColor = cc.borderColor;
                if (current() instanceof RowCtx) ((RowCtx) current()).cells.add(cell);
            }
            return;
        }

        if (tag.equals("tr")) {
            Ctx ctx = pop();
            if (ctx instanceof RowCtx) {
                TableRow row = new TableRow(((RowCtx) ctx).cells);
                if (current() instanceof TableCtx) ((TableCtx) current()).rows.add(row);
            }
            return;
        }

        if (tag.equals("table")) {
            Ctx ctx = pop();
            if (ctx instanceof TableCtx && !((TableCtx) ctx).rows.isEmpty()) {
                TableCtx tc = (TableCtx) ctx;
                int maxCols = 0;
                for (TableRow r : tc.rows) maxCols = Math.max(maxCols, r.cells.size());
                Table table = new Table();
                table.rows = tc.rows;
                table.columns = maxCols;
                table.border = new BorderStyle(tc.hasBorder ? "borderCell" : "borderNone",
                        "borderStyle-solid");
                // columnSpans MUTLAK sütun genişliğidir (gerçek UDE: 100/sütun); "1"
                // dejenere tabloya yol açıp kenarlıkları görünmez kılar. Eşit dağıt.
                List<Integer> widths = new ArrayList<>();
                for (int k = 0; k < maxCols; k++) widths.add(100);
                table.columnWidths = widths;
                addBlock(table);
            }
            return;
        }

        if (tag.equals("li")) {
            // li içinde doğrudan metin (iç <p> yok) → açık ParaCtx'i kapat
            while (current() instanceof ParaCtx) addBlock(((ParaCtx) pop()).p);
            if (pendingList != null) {   // hiç içerik yoktu → işaretli boş paragraf
                Paragraph p = new Paragraph();
                p.list = pendingList;
                pendingList = null;
                addBlock(p);
            }
            popStyle();
            return;
        }

        if (tag.equals("ul") || tag.equals("ol")) {
            Ctx ctx = pop();
            if (ctx instanceof ListCtx) for (Block b : ((ListCtx) ctx).blocks) addBlock(b);
            return;
        }

        if (tag.equals("div")) {
            while (current() instanceof ParaCtx) addBlock(((ParaCtx) pop()).p);
            Ctx ctx = pop();
            popStyle();
            if (ctx instanceof DivCtx) {
                DivCtx dc = (DivCtx) ctx;
                for (Block b : dc.blocks) addBlock(b);
                if (dc.pendingAfter) addBlock(new PageBreak());
            }
            return;
        }
    }

    // ---- yardımcılar ----

    /** Paragraf hizalama/girinti/aralık özniteliklerini çözülmüş stilden uygular. */
    private void applyParagraphStyle(Paragraph para, Map<String, String> ps) {
        if (ps.isEmpty()) return;
        if (ps.containsKey("textAlign")) para.alignment = parseAlignment(ps.get("textAlign"));
        if (ps.containsKey("lineHeight")) {
            try { para.lineSpacing = Double.parseDouble(ps.get("lineHeight")) - 1.0; } catch (Exception e) { }
        }
        if (ps.containsKey("marginLeft")) para.leftIndent = Css.parseLengthPt(ps.get("marginLeft"));
        if (ps.containsKey("marginRight")) para.rightIndent = Css.parseLengthPt(ps.get("marginRight"));
        if (ps.containsKey("paddingLeft")) para.leftIndent += Css.parseLengthPt(ps.get("paddingLeft"));
        if (ps.containsKey("paddingRight")) para.rightIndent += Css.parseLengthPt(ps.get("paddingRight"));
        if (ps.containsKey("marginTop")) para.spaceBefore = Css.parseLengthPt(ps.get("marginTop"));
        if (ps.containsKey("marginBottom")) para.spaceAfter = Css.parseLengthPt(ps.get("marginBottom"));
        if (ps.containsKey("paddingTop")) para.spaceBefore += Css.parseLengthPt(ps.get("paddingTop"));
        if (ps.containsKey("paddingBottom")) para.spaceAfter += Css.parseLengthPt(ps.get("paddingBottom"));
        if (ps.containsKey("textIndent")) para.firstLineIndent = Css.parseLengthPt(ps.get("textIndent"));
        if (ps.containsKey("tabStops")) {
            List<Double> stops = parseTabStops(ps.get("tabStops"));
            if (!stops.isEmpty()) para.tabStops = stops;
        }
    }

    private static int parseAlignment(String v) {
        switch (v.toLowerCase()) {
            case "center": return UdeDoc.ALIGN_CENTER;
            case "right": return UdeDoc.ALIGN_RIGHT;
            case "justify": return UdeDoc.ALIGN_JUSTIFY;
            default: return UdeDoc.ALIGN_LEFT;
        }
    }

    private static String parseVerticalAlign(String v) {
        switch (v.toLowerCase()) {
            case "middle": return "middle";
            case "bottom": return "bottom";
            default: return "top";
        }
    }

    private static List<Double> parseTabStops(String value) {
        List<Double> out = new ArrayList<>();
        for (String s : value.split("[\\s,]+")) {
            s = s.trim();
            if (s.isEmpty()) continue;
            if (!s.matches("-?[\\d.]+\\s*(px|pt|em|rem|cm|mm|in)?")) continue;
            double n = Css.parseLengthPt(s);
            if (n >= 0) out.add(n);
        }
        return out;
    }

    /** {width, color(int), styleCode} döndürür; styleCode: 0=solid,1=dashed,2=dotted,3=double,4=none. */
    private static double[] parseBorderCss(String value) {
        double width = 0.5; int styleCode = 0; int color = 0;
        for (String part : value.trim().split("\\s+")) {
            if (part.matches("\\d+(\\.\\d+)?(px|pt)?")) {
                width = Double.parseDouble(part.replaceAll("(px|pt)$", ""));
            } else if (part.equals("solid")) styleCode = 0;
            else if (part.equals("dashed")) styleCode = 1;
            else if (part.equals("dotted")) styleCode = 2;
            else if (part.equals("double")) styleCode = 3;
            else if (part.equals("none")) styleCode = 4;
            else if (part.startsWith("#") || part.startsWith("rgb")) color = Css.cssToArgb(part);
        }
        return new double[]{width, color, styleCode};
    }

    private static String borderStyleName(int code) {
        switch (code) {
            case 1: return "borderStyle-dashed";
            case 2: return "borderStyle-dotted";
            case 3: return "borderStyle-double";
            case 4: return "borderStyle-none";
            default: return "borderStyle-solid";
        }
    }

    private static String lower(String s) { return s == null ? "" : s.toLowerCase(); }

    private static Integer parseIntOrNull(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; }
    }

    private static double parseDoubleOr(String s, double def) {
        if (s == null) return def;
        try { return Double.parseDouble(s.trim().replaceAll("[^0-9.\\-].*$", "")); } catch (Exception e) { return def; }
    }
}
