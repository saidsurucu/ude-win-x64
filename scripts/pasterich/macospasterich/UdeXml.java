package macospasterich;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import macospasterich.UdeDoc.Block;
import macospasterich.UdeDoc.Document;
import macospasterich.UdeDoc.ImageRun;
import macospasterich.UdeDoc.PageFormat;
import macospasterich.UdeDoc.Paragraph;
import macospasterich.UdeDoc.Run;
import macospasterich.UdeDoc.Table;
import macospasterich.UdeDoc.TableCell;
import macospasterich.UdeDoc.TableRow;
import macospasterich.UdeDoc.TabRun;
import macospasterich.UdeDoc.TextRun;
import macospasterich.UdeDoc.TextStyle;

/**
 * UDE belge modeli → content.xml (udf-cli cdata-builder.ts + serializer.ts Java
 * portu). Global CDATA metni + offset'li &lt;elements&gt; üretir. Saf java.*.
 *
 * KRİTİK: tüm ondalık biçimleme Locale.US (Türkçe locale virgül kullanır,
 * content.xml ondalık nokta bekler).
 */
final class UdeXml {

    // ---- offset girişi ----
    private static final class Entry {
        final Run element; final int startOffset; final int length; final int blockId;
        Entry(Run e, int s, int l, int b) { element = e; startOffset = s; length = l; blockId = b; }
    }

    private final StringBuilder cdata = new StringBuilder();
    private final List<Entry> entries = new ArrayList<>();
    private int offset = 0;
    private int blockId = 0;

    static String serialize(Document doc) {
        UdeXml u = new UdeXml();
        u.buildCdataBlocks(doc.body);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
        xml.append("<template format_id=\"1.8\">\n");
        xml.append("<content><![CDATA[").append(u.cdata).append("]]></content>");
        xml.append(pageFormat(doc.pages));
        xml.append("\n");
        xml.append("<elements resolver=\"hvl-default\">\n");

        int[] blockIdCounter = {0};
        int[] offsetCursor = {0};
        xml.append(u.serializeBlocks(doc.body, blockIdCounter, offsetCursor));

        xml.append("</elements>\n");
        xml.append("<styles>");
        xml.append("<style name=\"default\" description=\"Geçerli\" family=\"Dialog\" size=\"12\" bold=\"false\" italic=\"false\" foreground=\"-13421773\" FONT_ATTRIBUTE_KEY=\"javax.swing.plaf.FontUIResource[family=Dialog,name=Dialog,style=plain,size=12]\" />");
        xml.append("<style name=\"hvl-default\" family=\"Times New Roman\" size=\"12\" description=\"Gövde\" />");
        xml.append("</styles>\n");
        xml.append("</template>");
        return xml.toString();
    }

    // ---- CDATA + offset girişleri ----
    private void buildCdataBlocks(List<Block> blocks) {
        for (Block block : blocks) {
            if (block instanceof Paragraph) buildParagraph((Paragraph) block);
            else if (block instanceof Table) buildTable((Table) block);
            else if (block instanceof UdeDoc.PageBreak) {
                cdata.append('​').append('\n');
                offset += 2;
                blockId++;
            }
        }
    }

    private void buildParagraph(Paragraph para) {
        int currentBlockId = blockId++;
        if (para.runs.isEmpty()) {
            cdata.append('​');
            offset += 1;
        } else {
            for (Run run : para.runs) {
                if (run instanceof TextRun) {
                    String t = ((TextRun) run).text;
                    int len = t.codePointCount(0, t.length());
                    entries.add(new Entry(run, offset, len, currentBlockId));
                    cdata.append(t);
                    offset += len;
                } else if (run instanceof ImageRun) {
                    entries.add(new Entry(run, offset, 1, currentBlockId));
                    cdata.append('￼');
                    offset += 1;
                } else if (run instanceof TabRun) {
                    entries.add(new Entry(run, offset, 1, currentBlockId));
                    cdata.append('\t');
                    offset += 1;
                }
            }
        }
        cdata.append('\n');
        offset += 1;
    }

    private void buildTable(Table table) {
        for (TableRow row : table.rows) {
            for (TableCell cell : row.cells) {
                buildCdataBlocks(cell.content);
            }
        }
    }

    // ---- content.xml element seri hale getirme ----
    private List<Entry> entriesForBlock(int id) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) if (e.blockId == id) out.add(e);
        return out;
    }

    private String serializeBlocks(List<Block> blocks, int[] blockIdCounter, int[] offsetCursor) {
        StringBuilder xml = new StringBuilder();
        for (Block block : blocks) {
            if (block instanceof Paragraph) {
                int id = blockIdCounter[0]++;
                List<Entry> be = entriesForBlock(id);
                xml.append(serializeParagraph((Paragraph) block, be, offsetCursor[0]));
                if (be.isEmpty()) {
                    offsetCursor[0] += 2;
                } else {
                    Entry last = be.get(be.size() - 1);
                    offsetCursor[0] = last.startOffset + last.length + 1;
                }
            } else if (block instanceof Table) {
                xml.append(serializeTable((Table) block, blockIdCounter, offsetCursor));
            } else if (block instanceof UdeDoc.PageBreak) {
                blockIdCounter[0]++;
                String inner = serializeParagraph(new Paragraph(), new ArrayList<Entry>(), offsetCursor[0]);
                if (inner.endsWith("\n")) inner = inner.substring(0, inner.length() - 1);
                xml.append("<page-break>").append(inner).append("</page-break>\n");
                offsetCursor[0] += 2;
            }
        }
        return xml.toString();
    }

    private String serializeParagraph(Paragraph para, List<Entry> be, int emptyOffset) {
        StringBuilder xml = new StringBuilder();
        xml.append("<paragraph");
        xml.append(" Alignment=\"").append(para.alignment).append("\"");
        xml.append(" LeftIndent=\"").append(f1(para.leftIndent)).append("\"");
        xml.append(" RightIndent=\"").append(f1(para.rightIndent)).append("\"");
        if (para.firstLineIndent != 0) xml.append(" FirstLineIndent=\"").append(f2(para.firstLineIndent)).append("\"");
        if (para.lineSpacing != 0) xml.append(" LineSpacing=\"").append(f2(para.lineSpacing)).append("\"");
        if (para.spaceBefore != 0) xml.append(" SpaceAbove=\"").append(f2(para.spaceBefore)).append("\"");
        if (para.spaceAfter != 0) xml.append(" SpaceBelow=\"").append(f2(para.spaceAfter)).append("\"");
        if (para.tabStops != null && !para.tabStops.isEmpty()) {
            StringBuilder ts = new StringBuilder();
            for (int i = 0; i < para.tabStops.size(); i++) {
                if (i > 0) ts.append(",");
                ts.append(f2(para.tabStops.get(i))).append(":0:0");
            }
            xml.append(" TabSet=\"").append(ts).append("\"");
        }
        if (para.list != null) {
            if (para.list.type.equals("numbered")) {
                xml.append(" Numbered=\"true\"");
                if (para.list.numberType != null) xml.append(" NumberType=\"").append(esc(para.list.numberType)).append("\"");
                if (para.list.listId != null) xml.append(" ListId=\"").append(para.list.listId).append("\"");
                xml.append(" ListLevel=\"").append(para.list.level).append("\"");
            } else if (para.list.type.equals("bulleted")) {
                xml.append(" Bulleted=\"true\"");
                if (para.list.bulletType != null) xml.append(" BulletType=\"").append(esc(para.list.bulletType)).append("\"");
                xml.append(" ListLevel=\"").append(para.list.level).append("\"");
            }
        }
        xml.append(">");

        if (be.isEmpty()) {
            xml.append("<content startOffset=\"").append(emptyOffset)
               .append("\" length=\"2\" family=\"Times New Roman\" size=\"10\" />");
        } else {
            int lastIdx = be.size() - 1;
            for (int i = 0; i < be.size(); i++) {
                Entry entry = be.get(i);
                boolean extendsNewline = i == lastIdx && entry.element instanceof TextRun;
                if (entry.element instanceof TextRun) {
                    int len = entry.length + (extendsNewline ? 1 : 0);
                    xml.append("<content startOffset=\"").append(entry.startOffset)
                       .append("\" length=\"").append(len).append("\"")
                       .append(fontAttrs(((TextRun) entry.element).style)).append(" />");
                } else if (entry.element instanceof ImageRun) {
                    ImageRun img = (ImageRun) entry.element;
                    xml.append("<image imageData=\"").append(img.data)
                       .append("\" startOffset=\"").append(entry.startOffset)
                       .append("\" length=\"1\" width=\"").append(f1(img.width))
                       .append("\" height=\"").append(f1(img.height)).append("\" />");
                } else if (entry.element instanceof TabRun) {
                    xml.append("<tab startOffset=\"").append(entry.startOffset)
                       .append("\" length=\"1\"").append(fontAttrs(((TabRun) entry.element).style)).append(" />");
                }
            }
        }
        xml.append("</paragraph>\n");
        return xml.toString();
    }

    private String serializeTable(Table table, int[] blockIdCounter, int[] offsetCursor) {
        StringBuilder xml = new StringBuilder();
        xml.append("<table tableName=\"").append(esc(table.name)).append("\"");
        xml.append(" columnCount=\"").append(table.columns).append("\"");
        StringBuilder spans = new StringBuilder();
        for (int i = 0; i < table.columnWidths.size(); i++) {
            if (i > 0) spans.append(",");
            spans.append(Math.round(table.columnWidths.get(i)));
        }
        xml.append(" columnSpans=\"").append(spans).append("\"");
        xml.append(" border=\"").append(esc(table.border.name)).append("\">");
        for (TableRow row : table.rows) xml.append(serializeRow(row, blockIdCounter, offsetCursor));
        xml.append("</table>\n");
        return xml.toString();
    }

    private String serializeRow(TableRow row, int[] blockIdCounter, int[] offsetCursor) {
        StringBuilder xml = new StringBuilder();
        xml.append("<row rowName=\"").append(esc(row.name)).append("\" rowType=\"").append(esc(row.rowType)).append("\">");
        for (TableCell cell : row.cells) xml.append(serializeCell(cell, blockIdCounter, offsetCursor));
        xml.append("</row>");
        return xml.toString();
    }

    private String serializeCell(TableCell cell, int[] blockIdCounter, int[] offsetCursor) {
        StringBuilder xml = new StringBuilder();
        xml.append("<cell");
        if (cell.colspan > 1) xml.append(" colspan=\"").append(cell.colspan).append("\"");
        if (!cell.verticalAlign.equals("top")) xml.append(" align=\"").append(cellAlign(cell.verticalAlign)).append("\"");
        if (cell.fillColor != 16777215 && cell.fillColor != -1) xml.append(" fillColor=\"").append(cell.fillColor).append("\"");
        // Hücreye kenarlık özniteliği YOK: UDE kenarlığı YALNIZ tablo-düzeyi
        // border="borderCell"/"borderNone" ile çizer (gerçek UDE dosyalarında hücreler
        // daima çıplak — per-cell borderColor="0" tablo çizimini ezip görünmez yapıyordu).
        xml.append(">");
        xml.append(serializeBlocks(cell.content, blockIdCounter, offsetCursor));
        xml.append("</cell>");
        return xml.toString();
    }

    // ---- yardımcılar ----
    private static String pageFormat(PageFormat pf) {
        return "<properties><pageFormat"
                + " mediaSizeName=\"" + pf.mediaSizeName + "\""
                + " leftMargin=\"" + f2(pf.leftMargin) + "\""
                + " rightMargin=\"" + f2(pf.rightMargin) + "\""
                + " topMargin=\"" + f2(pf.topMargin) + "\""
                + " bottomMargin=\"" + f2(pf.bottomMargin) + "\""
                + " paperOrientation=\"" + pf.paperOrientation + "\""
                + " headerFOffset=\"" + f1(pf.headerFOffset) + "\""
                + " footerFOffset=\"" + f1(pf.footerFOffset) + "\""
                + " /></properties>";
    }

    private static String fontAttrs(TextStyle s) {
        StringBuilder b = new StringBuilder();
        b.append(" family=\"").append(esc(s.fontFamily)).append("\"");
        b.append(" size=\"").append(numStr(s.fontSize)).append("\"");
        if (s.bold) b.append(" bold=\"true\"");
        if (s.italic) b.append(" italic=\"true\"");
        if (s.underline) b.append(" underline=\"true\"");
        if (s.color != -16777216) b.append(" foreground=\"").append(s.color).append("\"");
        if (s.backgroundColor != -1) b.append(" background=\"").append(s.backgroundColor).append("\"");
        return b.toString();
    }

    private static String cellAlign(String a) { return a.equals("middle") ? "vcenter" : a; }

    private static String esc(String v) {
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String f1(double d) { return String.format(Locale.US, "%.1f", d); }
    private static String f2(double d) { return String.format(Locale.US, "%.2f", d); }

    /** Tam sayıysa ondalıksız (JS Number.toString gibi): 12 → "12", 8.25 → "8.25". */
    private static String numStr(double d) {
        if (d == Math.rint(d) && !Double.isInfinite(d)) return Long.toString((long) d);
        return String.valueOf(d);
    }

    private UdeXml() {
    }
}
