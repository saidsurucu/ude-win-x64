// BUFREAD=1 doğrulaması: common.ac(InputStream) tamponsuz FileInputStream ile
// doldurulunca baytlar dosyayla BİREBİR aynı olmalı ve hızlı olmalı.
//
//   javac --release 11 -d /tmp/bufread tests/BufReadAcTest.java
//   java -cp /tmp/bufread:<editor-app.jar> BufReadAcTest <dosya> [<dosya>…]
//
// Yamasız jar'da da geçer (yalnız yavaştır) — süre satırı farkı gösterir.
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class BufReadAcTest {
    public static void main(String[] args) throws Exception {
        Class<?> ac = Class.forName("tr.com.havelsan.uyap.system.editor.common.ac");
        int fail = 0;
        for (String p : args) {
            byte[] want = Files.readAllBytes(Paths.get(p));
            long t0 = System.nanoTime();
            ByteArrayInputStream got;
            try (InputStream in = new FileInputStream(p)) {
                got = (ByteArrayInputStream) ac.getConstructor(InputStream.class).newInstance(in);
            }
            long ms = (System.nanoTime() - t0) / 1_000_000;
            byte[] have = got.readAllBytes();
            boolean ok = Arrays.equals(want, have);
            if (!ok) fail++;
            System.out.println((ok ? "OK   " : "FAIL ") + p + "  " + want.length + " B  " + ms + " ms"
                + (ok ? "" : "  (okunan " + have.length + " B)"));
        }
        if (fail > 0) { System.out.println(fail + " başarısız"); System.exit(1); }
    }
}
