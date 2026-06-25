package macospasterich;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/**
 * Harici stilli yapıştırma teşhis günlüğü. UDE_PASTERICHLOG=1 ile etkin.
 * System.err uygulama tarafından yutulduğundan dosyaya yazar
 * (~/Library/Logs/ude-pasterich.txt). TrLog deseni.
 */
final class PrLog {
    private static final boolean ON = "1".equals(System.getenv("UDE_PASTERICHLOG"));

    private static Path file() {
        return Paths.get(System.getProperty("user.home"), "Library", "Logs", "ude-pasterich.txt");
    }

    static void log(String msg) {
        if (!ON) return;
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.write(f, (LocalDateTime.now() + " " + msg + "\n").getBytes("UTF-8"),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignore) {
        }
    }

    static void log(String ctx, Throwable t) {
        if (!ON) return;
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        log(ctx + ": " + sw);
    }

    /**
     * Ham pano HTML'ini ayrı bir dosyaya yazar (her yapıştırmada üzerine yazar) —
     * Google Docs/Pages/AI kaynaklarının gerçek HTML'ini yakalayıp parser'ı buna
     * göre düzeltmek için. ~/Library/Logs/ude-pasterich-last.html
     */
    static void dumpHtml(String html) {
        if (!ON || html == null) return;
        try {
            Path f = Paths.get(System.getProperty("user.home"), "Library", "Logs", "ude-pasterich-last.html");
            Files.createDirectories(f.getParent());
            Files.write(f, html.getBytes("UTF-8"),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Throwable ignore) {
        }
    }

    private PrLog() {
    }
}
