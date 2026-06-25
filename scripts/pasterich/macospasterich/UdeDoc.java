package macospasterich;

import java.util.ArrayList;
import java.util.List;

/**
 * UDE belge modeli (udf-cli model/document.ts Java portu). Saf veri sınıfları;
 * harici bağımlılık yok. Parser (HtmlToUde) bu modeli kurar, serializer (UdeXml)
 * content.xml'e çevirir.
 */
final class UdeDoc {

    // Hizalama
    static final int ALIGN_LEFT = 0, ALIGN_CENTER = 1, ALIGN_RIGHT = 2, ALIGN_JUSTIFY = 3;

    static final class TextStyle {
        String fontFamily = "Times New Roman";
        double fontSize = 12;
        boolean bold, italic, underline;
        int color = -16777216;          // siyah 0xFF000000
        int backgroundColor = -1;       // beyaz 0xFFFFFFFF

        TextStyle copy() {
            TextStyle s = new TextStyle();
            s.fontFamily = fontFamily; s.fontSize = fontSize;
            s.bold = bold; s.italic = italic; s.underline = underline;
            s.color = color; s.backgroundColor = backgroundColor;
            return s;
        }
    }

    // ---- inline run'lar ----
    abstract static class Run { }

    static final class TextRun extends Run {
        String text;
        TextStyle style;
        TextRun(String text, TextStyle style) { this.text = text; this.style = style; }
    }

    static final class ImageRun extends Run {
        String data;     // base64
        double width = 100, height = 100;
    }

    static final class TabRun extends Run {
        TextStyle style;
        TabRun(TextStyle style) { this.style = style; }
    }

    static final class ListProps {
        String type;        // "numbered" | "bulleted"
        String numberType;  // ör. NUMBER_TYPE_NUMBER_DOT
        String bulletType;  // ör. BULLET_TYPE_ELLIPSE
        int level;
        Integer listId;
    }

    // ---- blok'lar ----
    abstract static class Block { }

    static final class Paragraph extends Block {
        int alignment = ALIGN_LEFT;
        double leftIndent, rightIndent, firstLineIndent, lineSpacing, spaceBefore, spaceAfter;
        List<Run> runs = new ArrayList<>();
        ListProps list;            // null = liste değil
        List<Double> tabStops;     // null = yok
    }

    static final class PageBreak extends Block { }

    static final class BorderStyle {
        String name = "borderCell";
        String style = "borderStyle-solid";
        BorderStyle() { }
        BorderStyle(String name, String style) { this.name = name; this.style = style; }
    }

    static final class TableCell {
        int colspan = 1, rowspan = 1;
        String verticalAlign = "top";
        int fillColor = 16777215;   // beyaz (unsigned)
        BorderStyle border = new BorderStyle();
        double borderWidth = 0.5;
        int borderColor = 0;
        int borderSpec = 15;        // tüm kenarlar
        List<Block> content;
        TableCell(List<Block> content) { this.content = content; }
    }

    static final class TableRow {
        String name = "row1";
        String rowType = "dataRow";
        double height;
        BorderStyle border = new BorderStyle();
        List<TableCell> cells;
        TableRow(List<TableCell> cells) { this.cells = cells; }
    }

    static final class Table extends Block {
        String name = "Sabit";
        int columns;
        List<Integer> columnWidths;
        BorderStyle border = new BorderStyle();
        List<TableRow> rows;
    }

    static final class PageFormat {
        int mediaSizeName = 1;
        double leftMargin = 42.52, rightMargin = 28.35, topMargin = 14.17, bottomMargin = 14.17;
        int paperOrientation = 1;
        double headerFOffset = 20.0, footerFOffset = 20.0;
    }

    static final class Document {
        PageFormat pages = new PageFormat();
        List<Block> body = new ArrayList<>();
    }

    private UdeDoc() {
    }
}
