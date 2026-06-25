package macosantet;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

/* Teşhis logu: UDE_ANTETLOG=1 iken ~/Library/Logs/ude-antet.txt.
 * System.err'i uygulama yuttuğu için dosyaya yazılır (TrLog deseni). */
public final class AntetLog {

    private static final boolean ON = "1".equals(System.getenv("UDE_ANTETLOG"));
    private static final File FILE = new File(
        winBase(), "ude-antet.txt");

    private static String winBase() {
        String b = System.getenv("LOCALAPPDATA");
        return (b != null && !b.isEmpty()) ? b : System.getProperty("java.io.tmpdir");
    }

    private AntetLog() {}

    public static synchronized void log(String msg) {
        if (!ON) return;
        try (PrintWriter w = new PrintWriter(new FileWriter(FILE, true))) {
            w.println(System.currentTimeMillis() + " " + msg);
        } catch (Exception ignored) {
        }
    }
}
