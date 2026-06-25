package macospasterich;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CSS renk ve inline-stil ayrıştırma yardımcıları (udf-cli color.ts + style.ts
 * Java portu). Saf java.*; harici bağımlılık yok.
 *
 * KRİTİK: tüm sayı biçimleme Locale.US ile yapılmalı — Türkçe locale'de ondalık
 * ayraç virgüldür, content.xml ondalık nokta bekler.
 */
final class Css {

    private static final Map<String, int[]> NAMED = new HashMap<>();
    static {
        NAMED.put("black", new int[]{0, 0, 0});
        NAMED.put("white", new int[]{255, 255, 255});
        NAMED.put("red", new int[]{255, 0, 0});
        NAMED.put("green", new int[]{0, 128, 0});
        NAMED.put("blue", new int[]{0, 0, 255});
        NAMED.put("yellow", new int[]{255, 255, 0});
        NAMED.put("cyan", new int[]{0, 255, 255});
        NAMED.put("magenta", new int[]{255, 0, 255});
        NAMED.put("orange", new int[]{255, 165, 0});
        NAMED.put("purple", new int[]{128, 0, 128});
        NAMED.put("gray", new int[]{128, 128, 128});
        NAMED.put("grey", new int[]{128, 128, 128});
        NAMED.put("navy", new int[]{0, 0, 128});
        NAMED.put("teal", new int[]{0, 128, 128});
        NAMED.put("maroon", new int[]{128, 0, 0});
        NAMED.put("olive", new int[]{128, 128, 0});
        NAMED.put("silver", new int[]{192, 192, 192});
        NAMED.put("lime", new int[]{0, 255, 0});
        NAMED.put("aqua", new int[]{0, 255, 255});
        NAMED.put("fuchsia", new int[]{255, 0, 255});
        NAMED.put("transparent", new int[]{0, 0, 0});
    }

    /** ARGB (alpha=0xFF), Java int zaten işaretli 32-bit. */
    static int rgbToArgb(int r, int g, int b) {
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    private static final Pattern RGB = Pattern.compile("rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)");

    /** CSS renk dizesi → işaretli ARGB int32. Çözülemezse siyah (-16777216). */
    static int cssToArgb(String color) {
        if (color == null) return -16777216;
        color = color.trim().toLowerCase();
        if (color.startsWith("#")) {
            int[] rgb = parseHex(color);
            if (rgb != null) return rgbToArgb(rgb[0], rgb[1], rgb[2]);
        }
        if (color.startsWith("rgb")) {
            Matcher m = RGB.matcher(color);
            if (m.find()) {
                return rgbToArgb(Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            }
        }
        int[] named = NAMED.get(color);
        if (named != null) return rgbToArgb(named[0], named[1], named[2]);
        return -16777216;
    }

    private static int[] parseHex(String hex) {
        hex = hex.replace("#", "");
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1)
                    + hex.charAt(2) + hex.charAt(2);
        }
        if (hex.length() != 6) return null;
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return new int[]{r, g, b};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Inline style dizesini camelCase anahtarlı haritaya çevirir. */
    static Map<String, String> parseInlineStyle(String style) {
        Map<String, String> out = new HashMap<>();
        if (style == null || style.isEmpty()) return out;
        for (String decl : style.split(";")) {
            int colon = decl.indexOf(':');
            if (colon < 0) continue;
            String prop = decl.substring(0, colon).trim();
            String value = decl.substring(colon + 1).trim();
            if (prop.isEmpty() || value.isEmpty()) continue;
            out.put(camel(prop), value);
        }
        return out;
    }

    private static String camel(String prop) {
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (int i = 0; i < prop.length(); i++) {
            char c = prop.charAt(i);
            if (c == '-') { up = true; continue; }
            if (up && c >= 'a' && c <= 'z') { sb.append(Character.toUpperCase(c)); up = false; }
            else { sb.append(c); up = false; }
        }
        return sb.toString();
    }

    private static final Pattern FONT_SIZE = Pattern.compile("^([\\d.]+)\\s*(px|pt|em|rem)?$");

    /** font-size CSS değeri → punto. */
    static double parseFontSize(String value) {
        Matcher m = FONT_SIZE.matcher(value.trim());
        if (!m.matches()) return 12;
        double num = Double.parseDouble(m.group(1));
        String unit = m.group(2) == null ? "pt" : m.group(2);
        switch (unit) {
            case "px": return num * 0.75;
            case "em":
            case "rem": return num * 12;
            default: return num;
        }
    }

    private static final Pattern LEN = Pattern.compile("^(-?[\\d.]+)\\s*(px|pt|em|rem|cm|mm|in)?$");

    /** CSS uzunluk değeri → punto. Çözülemezse 0. */
    static double parseLengthPt(String value) {
        if (value == null) return 0;
        Matcher m = LEN.matcher(value.trim());
        if (!m.matches()) return 0;
        double n;
        try { n = Double.parseDouble(m.group(1)); } catch (NumberFormatException e) { return 0; }
        String unit = m.group(2) == null ? "pt" : m.group(2);
        switch (unit) {
            case "px": return n * 0.75;
            case "em":
            case "rem": return n * 12;
            case "cm": return n * 28.3465;
            case "mm": return n * 2.83465;
            case "in": return n * 72;
            default: return n;
        }
    }

    private Css() {
    }
}
