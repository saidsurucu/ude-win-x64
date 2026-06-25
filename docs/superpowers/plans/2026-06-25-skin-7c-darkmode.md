# SKIN 7c (Koyu İkon + Koyu Sayfa) — Uygulama Planı

> **REQUIRED SUB-SKILL:** superpowers:executing-plans.

**Goal:** Koyu ikonlar (IconDarken+ModeAwareImage) + koyu belge sayfası (DarkPage, opt-in).

**Architecture:** 3 sınıfı Mac'ten birebir kopyala; tek uyarlama: DarkPage prefs node `ude-mac-arm`→`ude-win`. IconDarken Utils bloğunu SkinPatch'e geri ekle. DarkPage hj.paint kancası (SkinPatch'te zaten var) aktifleşir.

## Global Constraints
- Paket `macosskin`. ICONS→SKIN patch sırası. `-Skin`'i `-Icons`'a BAĞLAMA (graceful).
- Önce kopyala sonra uyarla. Referans: `$env:TEMP\ude-mac-ref\scripts\skin\macosskin`.

---

### Task 0: Dal + jar + referans
- [ ] `git branch --show-current` → `feat/skin-7c-darkmode`; jar+ref hazır (download/clone gerekirse).

### Task 1: 3 sınıf + uyarlama + Utils bloğu + compile

- [ ] **Step 1: 3 sınıfı kopyala**
```powershell
$MAC="$env:TEMP\ude-mac-ref\scripts\skin\macosskin"; $dst="scripts\skin\macosskin"; $utf8=New-Object System.Text.UTF8Encoding $false
foreach($f in 'IconDarken.java','ModeAwareImage.java','DarkPage.java'){ [System.IO.File]::WriteAllText((Join-Path $dst $f),[System.IO.File]::ReadAllText((Join-Path $MAC $f)),$utf8) }
```
- [ ] **Step 2: DarkPage prefs node uyarla** — `DarkPage.java`'da `node("ude-mac-arm")` → `node("ude-win")`.
- [ ] **Step 3: apply-skin.ps1 derleme listesine 3 kaynağı ekle** (WordField sonrası): `IconDarken.java`, `ModeAwareImage.java`, `DarkPage.java`.
- [ ] **Step 4: IconDarken Utils bloğunu SkinPatch'e geri ekle** — `// (7c: koyu mod ikon aydınlatma — ... — bu fazda YOK.)` satırını şu blokla DEĞİŞTİR:
```java
        try {
            CtClass utils = pool.get("tr.com.havelsan.uyap.system.editor.common.Utils");
            utils.getMethod("b", "(Ljava/lang/String;)Ljavax/swing/ImageIcon;")
                .insertAfter("{ $_ = macosskin.IconDarken.apply($_); }");
            utils.getMethod("a", "(Ljava/lang/String;)Ljavax/swing/ImageIcon;")
                .insertAfter("{ $_ = macosskin.IconDarken.apply($_); }");
            utils.getMethod("a", "(Ljava/lang/String;I)Ljavax/swing/ImageIcon;")
                .insertAfter("{ $_ = macosskin.IconDarken.apply($_); }");
            utils.getMethod("a", "(Ljavax/swing/ImageIcon;II)Ljavax/swing/ImageIcon;")
                .insertBefore(
                    "{ if ($1 != null && $1.getImage() instanceof macosskin.ModeAwareImage) {"
                  + "    javax.swing.ImageIcon __r = macosskin.IconDarken.scaleIcon($1, $2, $3);"
                  + "    if (__r != null) return __r;"
                  + "  } }");
            writeClass(utils, outDir);
            System.out.println("[SkinPatch] koyu mod ikon aydinlatma eklendi.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: ikon aydinlatma atlandi: " + t);
        }
```
- [ ] **Step 5: text.hj.paint doğrula + compile**
```powershell
. .\scripts\common.ps1; $jdk=Get-Jdk11Home; $jar=Join-Path $InputDir $MainJar
& (Join-Path $jdk 'bin\javap.exe') -classpath "$jar" tr.com.havelsan.uyap.system.editor.common.text.hj 2>&1 | Select-String 'void paint\('
Remove-Item "$env:TEMP\ctest" -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force "$env:TEMP\ctest" | Out-Null
& (Join-Path $jdk 'bin\javac.exe') --release 11 -encoding UTF-8 -cp "$jar" -d "$env:TEMP\ctest" (Get-ChildItem scripts\skin\macosskin\*.java | % FullName); "javac: $LASTEXITCODE"
```
Expected: `paint(Graphics)` var; javac exit 0.
- [ ] **Step 6: Commit** `SKIN 7c: IconDarken/ModeAwareImage/DarkPage (Mac kopya; node ude-win) + Utils blogu`.

### Task 2: Apply + pixel probe + flag matrix

- [ ] **Step 1: Apply (ICONS sonra SKIN)** — `.\build.ps1 -Only download`; `$env:ICONS='1'`; apply-icons; sonra apply-skin. Çıktıda `koyu mod ikon aydinlatma eklendi` + `koyu belge arkaplani kancasi` (DarkPage artık "atlandı" DEMEZ).
- [ ] **Step 2: Pixel probe yaz** `scripts\skin\DarkIconProbe.java`:
```java
import java.awt.*; import java.awt.image.*; import javax.swing.*;
public class DarkIconProbe {
    public static void main(String[] a) throws Exception {
        macosskin.DarkMode.setMode("dark");
        BufferedImage g = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        Graphics2D gg=g.createGraphics(); gg.setColor(new Color(16,124,65)); gg.fillRect(0,0,16,16); gg.dispose();
        ImageIcon icon = new ImageIcon(g);
        ImageIcon out = macosskin.IconDarken.apply(icon);
        System.out.println("wrapped=" + (out.getImage() instanceof macosskin.ModeAwareImage));
        BufferedImage canvas = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        Graphics2D cg=canvas.createGraphics(); out.paintIcon(null,cg,0,0); cg.dispose();
        Color c = new Color(canvas.getRGB(8,8));
        System.out.println("orig=(16,124,65) dark=(" + c.getRed()+","+c.getGreen()+","+c.getBlue()+")");
        macosskin.DarkMode.setMode("system");
    }
}
```
- [ ] **Step 3: Probe çalıştır** (jar yamalı):
```powershell
$jdk=Get-Jdk11Home; $jar=Join-Path $InputDir $MainJar
Remove-Item "$env:TEMP\diprobe" -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force "$env:TEMP\diprobe" | Out-Null
& (Join-Path $jdk 'bin\javac.exe') -cp "$jar" -d "$env:TEMP\diprobe" scripts\skin\DarkIconProbe.java
& (Join-Path $jdk 'bin\java.exe') -cp "$env:TEMP\diprobe;$jar" DarkIconProbe
```
Expected: `wrapped=true`; `dark=` yeşil ama daha AÇIK (G kanalı ~184'e yakın, parlaklık artmış; #107C41→~#30B86D).
- [ ] **Step 4: Flag matrix — SKIN-only çökmez** — `-Skin` (ICONS'suz) build/başlat → çöküş yok (IconDarken düz ikonu sarar). Probe zaten plain-image testi.
- [ ] **Step 5: Commit** `SKIN 7c: dark-icon pixel probe + Utils zincir aktif`.

### Task 3: Build + koyu ikon ss + DarkPage + PDF

- [ ] **Step 1: -Icons -Skin build** (`UDE_VERSION='5.4.22'`).
- [ ] **Step 2: Koyu modda başlat + ss** — colorMode=dark → şerit ikonları okunur (açılmış, ölü-koyu değil). `skin7c-dark.png`.
- [ ] **Step 3: DarkPage pref AÇIK + ss** — `darkPageBackground=true` (java.util.prefs node ude-win) ayarla, yeniden başlat → belge sayfası KOYU, **chrome'a sızma yok** (şerit/cetvel ayrı). `skin7c-darkpage.png`. Sonra pref'i sil/false.
- [ ] **Step 4: PDF export — DarkPage açık VE kapalı** — her iki durumda geçerli PDF (sayfa baskıda BEYAZ; isPaintingForPrint sarılmaz).
- [ ] **Step 5: Rollback + colorMode=system.**
- [ ] **Step 6: finishing-a-development-branch.**

## Öz-İnceleme
Kapsama: 3 sınıf→T1; node uyarlama→T1S2; Utils blok→T1S4; probe(ModeAwareImage+pixel)→T2; flag matrix→T2S4; DarkPage(chrome no-leak+PDF)→T3S3-4. Placeholder yok. Tip: IconDarken.apply/scaleIcon, ModeAwareImage, DarkPage.wrap/isOn tutarlı.
