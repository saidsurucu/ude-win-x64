package macosskin;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Windows Acik/Koyu gorunum algilamasi (SKIN).
 * Kayit defteri: HKCU\...\Themes\Personalize\AppsUseLightTheme (DWORD;
 * 0=koyu, 1=acik). Anahtar yoksa/hata = acik mod (guvenli).
 * Substance'a bagimlilik YOK: wp.p clinit'i skin kurulumundan once kosabilir.
 */
public final class DarkMode {
    private static Boolean dark;

    private static final java.util.prefs.Preferences PREFS =
        java.util.prefs.Preferences.userRoot().node("ude-win");
    private static final String MODE_KEY = "colorMode";

    private DarkMode() {}

    public static String getMode() {
        try { return PREFS.get(MODE_KEY, "system"); }
        catch (Throwable t) { return "system"; }
    }

    public static void setMode(String mode) {
        try { PREFS.put(MODE_KEY, mode); PREFS.flush(); }
        catch (Throwable t) { trace("setMode HATA: " + t); }
    }

    public static synchronized void resetCache() { dark = null; }

    public static synchronized boolean isDark() {
        if (dark == null) {
            String mode = getMode();
            if ("dark".equals(mode)) { dark = Boolean.TRUE; return true; }
            if ("light".equals(mode)) { dark = Boolean.FALSE; return false; }
            boolean d = false;
            try {
                Process p = Runtime.getRuntime().exec(new String[]{
                    "reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme"});
                if (p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    try (BufferedReader r = new BufferedReader(
                            new InputStreamReader(p.getInputStream(), "UTF-8"))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            if (line.contains("AppsUseLightTheme")) {
                                int ix = line.indexOf("0x");
                                if (ix >= 0) {
                                    String hex = line.substring(ix + 2).trim().split("\\s+")[0];
                                    d = Integer.parseInt(hex, 16) == 0;
                                }
                            }
                        }
                    }
                } else {
                    p.destroyForcibly();
                }
            } catch (Throwable t) { d = false; }
            dark = Boolean.valueOf(d);
        }
        return dark.booleanValue();
    }

    public static Color canvasColor() {
        return isDark() ? new Color(40, 40, 40) : new Color(236, 236, 236);
    }

    private static final boolean DEBUG = "1".equals(System.getProperty("macosskin.debug"));

    public static void trace(String m) {
        if (!DEBUG) return;
        try {
            String base = System.getenv("TEMP");
            if (base == null) base = System.getProperty("java.io.tmpdir");
            try (java.io.FileWriter w = new java.io.FileWriter(
                    new java.io.File(base, "skinpatch-trace.log"), true)) {
                w.write(System.currentTimeMillis() + " " + m + "\n");
            }
        } catch (Throwable ignore) {}
    }
}
