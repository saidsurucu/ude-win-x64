package com.udewin.tabledelete;

import javax.swing.text.Element;
import javax.swing.text.AttributeSet;
import java.util.HashMap;
import java.util.Map;

import com.udewin.tabledelete.TableDelete.DocView;

/**
 * TableDelete tespit çekirdeği birim testi. Sahte Element ağacı + DocView ile
 * üç senaryo (seçim / hemen-ard / içeride-boş) ve "devret" (-1) durumları.
 *
 * Elle çalıştır:
 *   javac -d %TEMP%/tabdel scripts\tabledelete\com/udewin/tabledelete\TableDelete.java tests\TableDeleteDetectTest.java
 *   java -cp %TEMP%/tabdel com.udewin.tabledelete.TableDeleteDetectTest
 */
public final class TableDeleteDetectTest {
    private static int failures = 0;

    // --- Sahte Element (yalnız testin kullandığı metotlar) ---
    static final class E implements Element {
        final String name; final int s, e; E parent; final java.util.List<E> kids = new java.util.ArrayList<>();
        E(String name, int s, int e){ this.name=name; this.s=s; this.e=e; }
        E add(E c){ c.parent=this; kids.add(c); return this; }
        public String getName(){ return name; }
        public Element getParentElement(){ return parent; }
        public int getStartOffset(){ return s; }
        public int getEndOffset(){ return e; }
        public int getElementCount(){ return kids.size(); }
        public Element getElement(int i){ return kids.get(i); }
        public boolean isLeaf(){ return kids.isEmpty(); }
        // kullanılmayanlar:
        public javax.swing.text.Document getDocument(){ return null; }
        public AttributeSet getAttributes(){ return null; }
        public int getElementIndex(int o){ return 0; }
    }

    // offset → yaprak eleman eşlemesi + metin
    static DocView view(final Map<Integer,E> at, final String fullText) {
        return new DocView() {
            public Element charAt(int p){ return at.get(p); }
            public String text(int s, int len){ return fullText.substring(s, Math.min(fullText.length(), s+len)); }
        };
    }

    public static void main(String[] args) {
        // Belge: [0..3) tablo (boş "  " metin), [3..8) "after"
        // tablo > row > cell > para(leaf 0..3)
        E para = new E("paragraph", 0, 3);
        E leaf = new E("content", 0, 3); para.add(leaf);
        E cell = new E("cell", 0, 3); cell.add(para);
        E row = new E("row", 0, 3); row.add(cell);
        E table = new E("table", 0, 3); table.add(row);
        E afterLeaf = new E("content", 3, 8);
        Map<Integer,E> at = new HashMap<>();
        for (int p=0;p<3;p++) at.put(p, leaf);
        for (int p=3;p<8;p++) at.put(p, afterLeaf);
        DocView empty = view(at, "   after");          // tablo metni "   " (boş)

        // 1) Caret hemen ardında (caret=3) → hedef = tablo.start (0)
        check("ard-bos", 0, TableDelete.targetForBackspace(empty, 3, 3, 3));
        // 2) Seçim tabloyu kapsıyor (0..8) → hedef = 0
        check("secim", 0, TableDelete.targetForBackspace(empty, 8, 0, 8));
        // 3) Caret boş tablo içinde (caret=1) → hedef = 0
        check("ic-bos", 0, TableDelete.targetForBackspace(empty, 1, 1, 1));
        // 4) Caret düz metinde (caret=6, tablo yok) → -1 (devret)
        check("metin", -1, TableDelete.targetForBackspace(empty, 6, 6, 6));

        // Dolu tablo: metin "Xy after", tablo [0..3) = "Xy" dolu
        DocView full = view(at, "Xy after");
        // 5) Caret dolu tablo içinde (caret=1) → -1 (hücre düzenle, silme)
        check("ic-dolu", -1, TableDelete.targetForBackspace(full, 1, 1, 1));
        // 6) Caret dolu tablonun ardında (caret=3) → hedef 0 (tabloyu sil)
        check("ard-dolu", 0, TableDelete.targetForBackspace(full, 3, 3, 3));

        // Delete: seçim → sil, içeride-boş → sil, ard/önce-adjacency YOK
        check("del-secim", 0, TableDelete.targetForDelete(empty, 8, 0, 8));
        check("del-ic-bos", 0, TableDelete.targetForDelete(empty, 1, 1, 1));
        check("del-metin", -1, TableDelete.targetForDelete(empty, 6, 6, 6));

        if (failures == 0) System.out.println("TUM TESTLER GECTI");
        else { System.out.println(failures + " TEST BASARISIZ"); System.exit(1); }
    }

    static void check(String ad, int beklenen, int gercek) {
        if (beklenen != gercek) { failures++; System.out.println("BASARISIZ ["+ad+"] beklenen="+beklenen+" gercek="+gercek); }
        else System.out.println("ok ["+ad+"]");
    }
}
