package macospasterich;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hoşgörülü HTML tokenizer (kendi yazımımız — htmlparser2 yerine, harici bağımlılık yok).
 * Word/tarayıcı panosundaki kirli HTML'i tolere eder: yorumları (Word koşullu
 * yorumları dahil), DOCTYPE/PI'leri atlar; &lt;script&gt;/&lt;style&gt; içeriğini
 * yutar (CSS/JS metne sızmaz); varlık (entity) çözer; ad-uzayı/bilinmeyen etiketleri
 * (o:p, w:*) ad olarak geçirir (parser umursamaz, içerikleri yine de akar).
 *
 * Olaylar Handler'a (HtmlToUde) verilir: SAX benzeri onStart/onEnd/onText.
 */
final class Html {

    interface Handler {
        void onStart(String name, Map<String, String> attrs, boolean selfClosing);
        void onEnd(String name);
        void onText(String text);
        /** &lt;style&gt; bloğunun ham CSS içeriği (Pages/Docs sınıf kuralları). */
        void onStyle(String css);
    }

    static void parse(String html, Handler h) {
        int n = html.length();
        int i = 0;
        while (i < n) {
            char c = html.charAt(i);
            if (c == '<') {
                if (html.startsWith("<!--", i)) {
                    int end = html.indexOf("-->", i + 4);
                    i = (end < 0) ? n : end + 3;
                    continue;
                }
                if (html.startsWith("<!", i) || html.startsWith("<?", i)) {
                    int end = html.indexOf('>', i + 2);
                    i = (end < 0) ? n : end + 1;
                    continue;
                }
                if (html.startsWith("</", i)) {
                    int end = html.indexOf('>', i + 2);
                    if (end < 0) { i = n; continue; }
                    String name = tagName(html.substring(i + 2, end));
                    i = end + 1;
                    if (!name.isEmpty()) h.onEnd(name);
                    continue;
                }
                // başlangıç etiketi
                int end = findTagEnd(html, i + 1, n);
                if (end < 0) { // kapanmayan '<' → metin gibi al
                    h.onText("<");
                    i++;
                    continue;
                }
                String inner = html.substring(i + 1, end); // '<' ile '>' arası
                boolean selfClosing = inner.endsWith("/");
                if (selfClosing) inner = inner.substring(0, inner.length() - 1);
                String name = tagName(inner);
                Map<String, String> attrs = parseAttrs(inner, name.length());
                i = end + 1;
                if (name.isEmpty()) continue;
                String lower = name.toLowerCase();
                if (lower.equals("script")) {
                    // içeriği yut → metne sızmasın
                    int close = indexOfCloseTag(html, lower, i);
                    i = (close < 0) ? n : close;
                    continue;
                }
                if (lower.equals("style")) {
                    // içeriği metne sızdırma ama CSS sınıf kuralları için handler'a ver
                    int idx = html.toLowerCase().indexOf("</style", i);
                    if (idx < 0) { h.onStyle(html.substring(i)); i = n; }
                    else {
                        h.onStyle(html.substring(i, idx));
                        int gt = html.indexOf('>', idx);
                        i = (gt < 0) ? n : gt + 1;
                    }
                    continue;
                }
                h.onStart(name, attrs, selfClosing);
                continue;
            }
            // metin
            int next = html.indexOf('<', i);
            if (next < 0) next = n;
            String text = decode(html.substring(i, next));
            if (!text.isEmpty()) h.onText(text);
            i = next;
        }
    }

    /** '>' bul ama tırnak içindeki '>'leri atla. */
    private static int findTagEnd(String s, int from, int n) {
        char quote = 0;
        for (int j = from; j < n; j++) {
            char c = s.charAt(j);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return j;
            }
        }
        return -1;
    }

    private static String tagName(String inner) {
        int j = 0;
        int n = inner.length();
        while (j < n && Character.isWhitespace(inner.charAt(j))) j++;
        int start = j;
        while (j < n) {
            char c = inner.charAt(j);
            if (Character.isWhitespace(c) || c == '/' || c == '>') break;
            j++;
        }
        return inner.substring(start, j);
    }

    /** inner dizesinde isimden sonraki öznitelikleri ayrıştırır. */
    private static Map<String, String> parseAttrs(String inner, int afterName) {
        Map<String, String> out = new LinkedHashMap<>();
        int n = inner.length();
        int j = afterName;
        // ismin başındaki boşlukları geç (tagName boşluk atlamış olabilir)
        while (j < n && (Character.isWhitespace(inner.charAt(j)) || inner.charAt(j) == '/')) j++;
        while (j < n) {
            while (j < n && (Character.isWhitespace(inner.charAt(j)) || inner.charAt(j) == '/')) j++;
            if (j >= n) break;
            int nameStart = j;
            while (j < n) {
                char c = inner.charAt(j);
                if (Character.isWhitespace(c) || c == '=' || c == '/') break;
                j++;
            }
            String aname = inner.substring(nameStart, j).trim();
            while (j < n && Character.isWhitespace(inner.charAt(j))) j++;
            String aval = "";
            if (j < n && inner.charAt(j) == '=') {
                j++;
                while (j < n && Character.isWhitespace(inner.charAt(j))) j++;
                if (j < n && (inner.charAt(j) == '"' || inner.charAt(j) == '\'')) {
                    char q = inner.charAt(j);
                    j++;
                    int vs = j;
                    while (j < n && inner.charAt(j) != q) j++;
                    aval = inner.substring(vs, Math.min(j, n));
                    if (j < n) j++;
                } else {
                    int vs = j;
                    while (j < n && !Character.isWhitespace(inner.charAt(j)) && inner.charAt(j) != '/') j++;
                    aval = inner.substring(vs, j);
                }
            }
            if (!aname.isEmpty()) out.put(aname.toLowerCase(), decode(aval));
        }
        return out;
    }

    private static int indexOfCloseTag(String html, String lowerName, int from) {
        String lower = html.toLowerCase();
        String needle = "</" + lowerName;
        int idx = lower.indexOf(needle, from);
        if (idx < 0) return -1;
        int gt = html.indexOf('>', idx);
        return (gt < 0) ? html.length() : gt + 1;
    }

    // ---- varlık (entity) çözme ----

    private static final Map<String, String> ENT = new HashMap<>();
    static {
        ENT.put("amp", "&"); ENT.put("lt", "<"); ENT.put("gt", ">");
        ENT.put("quot", "\""); ENT.put("apos", "'"); ENT.put("nbsp", " ");
        ENT.put("copy", "©"); ENT.put("reg", "®"); ENT.put("trade", "™");
        ENT.put("hellip", "…"); ENT.put("mdash", "—"); ENT.put("ndash", "–");
        ENT.put("lsquo", "‘"); ENT.put("rsquo", "’");
        ENT.put("ldquo", "“"); ENT.put("rdquo", "”");
        ENT.put("bull", "•"); ENT.put("middot", "·"); ENT.put("deg", "°");
        ENT.put("euro", "€"); ENT.put("pound", "£"); ENT.put("sect", "§");
        ENT.put("laquo", "«"); ENT.put("raquo", "»");
        ENT.put("times", "×"); ENT.put("divide", "÷");
    }

    static String decode(String s) {
        if (s.indexOf('&') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        int n = s.length();
        for (int i = 0; i < n; ) {
            char c = s.charAt(i);
            if (c != '&') { sb.append(c); i++; continue; }
            int semi = s.indexOf(';', i + 1);
            if (semi < 0 || semi - i > 12) { sb.append(c); i++; continue; }
            String body = s.substring(i + 1, semi);
            String rep = null;
            if (body.startsWith("#x") || body.startsWith("#X")) {
                try { rep = new String(Character.toChars(Integer.parseInt(body.substring(2), 16))); }
                catch (Exception e) { rep = null; }
            } else if (body.startsWith("#")) {
                try { rep = new String(Character.toChars(Integer.parseInt(body.substring(1)))); }
                catch (Exception e) { rep = null; }
            } else {
                rep = ENT.get(body);
            }
            if (rep == null) { sb.append(c); i++; }
            else { sb.append(rep); i = semi + 1; }
        }
        return sb.toString();
    }

    private Html() {
    }
}
