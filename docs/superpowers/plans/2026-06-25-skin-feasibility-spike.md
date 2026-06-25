# SKIN Fizibilite Spike — Uygulama Planı

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mac SKIN'inin Windows'ta çalışıp çalışmadığını minimal dikey dilim + Flamingo kanaryası ile kanıtlamak; git/gitme + tam-port taslağı üreten bir bulgular dokümanı çıkarmak.

**Architecture:** Trimlenmiş `SkinPatch` (skin-kurulum çekirdeği, Aqua put'ları YOK, EDT-ihlali nötrleştirmesi VAR, Flamingo kanaryası) + Substance skin yardımcıları (`FlatUdeSkin`/`FlatUdeDarkSkin`/`FlatFontPolicy`/`DarkMode`) jar'a enjekte edilir; `apply-skin.ps1` desenli. Koyu-mod tespiti Windows kayıt defterinden. Spike atılabilir `spike/skin-feasibility` dalında; merge edilmez.

**Tech Stack:** PowerShell, JDK 11 (`javac --release 11`, `javap`, `jar`), Javassist 3.30.2-GA, Substance (org.jvnet.substance) + Flamingo (org.pushingpixels.flamingo) — ikisi de jar'da gömülü.

## Global Constraints

- Paket adı **`macosskin` KORUNUR** (spike; resource yolları `/macosskin/*.colorschemes` aynen çalışsın). Findings: tam portta `com.udewin.skin`'e yeniden adlandır.
- JDK 11 ile derle: `javac --release 11 -encoding UTF-8`. Helper'lar gömülü JDK 11'de koşar → 11 bytecode şart.
- Classpath ayıracı `;`; tüm yollar tırnaklı.
- Javassist gövde string'lerinde `//` yorum YASAK; sınıf başına tek `writeClass`.
- **Aqua put'ları PORT EDİLMEZ** (`com.apple.laf.Aqua*` Windows'ta yok). Substance kendi scrollbar/slider'ını çizer.
- **EDT-ihlali nötrleştirmesi ÇEKİRDEKTE** (PDF export bozulmasın).
- Spike kodu `spike/skin-feasibility` dalında; **merge edilmez**.
- Referans kaynak: `$env:TEMP\ude-mac-ref` (sub-proje 1'de klonlandı; yoksa `git clone --depth 1 https://github.com/saidsurucu/ude-mac-arm64`).

---

### Task 0: Dal + jar hazır

**Files:** (yok)

- [ ] **Step 1: Spike dalında olduğunu doğrula**

Run: `git branch --show-current`
Expected: `spike/skin-feasibility`

- [ ] **Step 2: jar mevcut (yoksa download)**

Run:
```powershell
. .\scripts\common.ps1
if (-not (Test-Path (Join-Path $InputDir $MainJar))) { .\build.ps1 -Only download }
Test-Path (Join-Path $InputDir $MainJar)
```
Expected: `True`

- [ ] **Step 3: Referans klon mevcut**

Run:
```powershell
$MAC = "$env:TEMP\ude-mac-ref"
if (-not (Test-Path "$MAC\scripts\skin\SkinPatch.java")) { git clone --depth 1 https://github.com/saidsurucu/ude-mac-arm64 $MAC }
Test-Path "$MAC\scripts\skin\macosskin\FlatUdeSkin.java"
```
Expected: `True`

---

### Task 1: DarkMode → Windows kayıt defteri

**Files:**
- Create: `scripts\skin\macosskin\DarkMode.java`

**Interfaces:**
- Produces: `macosskin.DarkMode.isDark()` (bool), `getMode()/setMode(String)`, `resetCache()`, `canvasColor()`, `trace(String)`.

- [ ] **Step 1: DarkMode.java'yı yaz (registry + Windows yolları)**

`scripts\skin\macosskin\DarkMode.java`:
```java
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
```

- [ ] **Step 2: Derle + registry'ye karşı doğrula**

Run:
```powershell
. .\scripts\common.ps1
$jdk = Get-Jdk11Home
Remove-Item "$env:TEMP\skintest" -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$env:TEMP\skintest" | Out-Null
& (Join-Path $jdk 'bin\javac.exe') --release 11 -encoding UTF-8 -d "$env:TEMP\skintest" scripts\skin\macosskin\DarkMode.java
@'
public class DMProbe { public static void main(String[] a){ System.out.println("isDark=" + macosskin.DarkMode.isDark()); } }
'@ | Set-Content "$env:TEMP\skintest\DMProbe.java" -Encoding ascii
& (Join-Path $jdk 'bin\javac.exe') -cp "$env:TEMP\skintest" -d "$env:TEMP\skintest" "$env:TEMP\skintest\DMProbe.java"
"=== DarkMode.isDark() ==="
& (Join-Path $jdk 'bin\java.exe') -cp "$env:TEMP\skintest" DMProbe
"=== gercek registry ==="
reg query "HKCU\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize" /v AppsUseLightTheme 2>$null
```
Expected: `isDark=` çıktısı registry ile tutarlı (AppsUseLightTheme=0x1 → `isDark=false`; 0x0 → `isDark=true`; anahtar yok → `isDark=false`).

- [ ] **Step 3: Commit**

```powershell
git add scripts/skin/macosskin/DarkMode.java
git commit -m "SKIN spike: DarkMode -> Windows kayit defteri (AppsUseLightTheme)"
```

---

### Task 2: Substance skin yardımcıları (FlatUdeSkin/DarkSkin/FontPolicy + colorschemes)

**Files:**
- Create: `scripts\skin\macosskin\FlatUdeSkin.java` (referanstan birebir)
- Create: `scripts\skin\macosskin\FlatUdeDarkSkin.java` (referanstan birebir)
- Create: `scripts\skin\macosskin\FlatFontPolicy.java` (font ailesi Windows'a uyarlanır)
- Create: `scripts\skin\macosskin\flatude.colorschemes` (referanstan birebir)
- Create: `scripts\skin\macosskin\flatude-dark.colorschemes` (referanstan birebir)

**Interfaces:**
- Produces: `macosskin.FlatUdeSkin` (`public static boolean installing`), `macosskin.FlatUdeDarkSkin extends FlatUdeSkin`, `macosskin.FlatFontPolicy(FontSet)`.

- [ ] **Step 1: Dört dosyayı + iki colorscheme'i referanstan kopyala**

Run (PowerShell, BOM'suz UTF-8):
```powershell
$MAC = "$env:TEMP\ude-mac-ref\scripts\skin\macosskin"
$dst = "scripts\skin\macosskin"
$utf8 = New-Object System.Text.UTF8Encoding $false
foreach ($f in 'FlatUdeSkin.java','FlatUdeDarkSkin.java','FlatFontPolicy.java','flatude.colorschemes','flatude-dark.colorschemes') {
  $c = [System.IO.File]::ReadAllText((Join-Path $MAC $f))
  [System.IO.File]::WriteAllText((Join-Path $dst $f), $c, $utf8)
}
"kopyalandi:"; Get-ChildItem $dst | Select-Object Name
```

- [ ] **Step 2: FlatFontPolicy font ailesini Windows'a çevir**

`scripts\skin\macosskin\FlatFontPolicy.java` içinde değiştir:
```java
    private static final String FAMILY = "Helvetica Neue";
```
→
```java
    private static final String FAMILY = "Segoe UI";
```
(Segoe UI = modern Windows sistem fontu, tam Türkçe glif kapsar.)

- [ ] **Step 3: Yardımcıları jar'a karşı derle (Substance API çözülüyor mu)**

Run:
```powershell
. .\scripts\common.ps1
$jar = Join-Path $InputDir $MainJar
$jdk = Get-Jdk11Home
Remove-Item "$env:TEMP\skinhelper" -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$env:TEMP\skinhelper" | Out-Null
& (Join-Path $jdk 'bin\javac.exe') --release 11 -encoding UTF-8 -cp "$jar" -d "$env:TEMP\skinhelper" `
  scripts\skin\macosskin\DarkMode.java `
  scripts\skin\macosskin\FlatUdeSkin.java `
  scripts\skin\macosskin\FlatUdeDarkSkin.java `
  scripts\skin\macosskin\FlatFontPolicy.java
"exit: $LASTEXITCODE"
```
Expected: exit 0 (Substance API — NebulaSkin, FlatGradientPainter, SubstanceColorSchemeBundle, FontSet — jar'dan çözülür). Hata olursa: Substance sürümü farklı → findings'e NO-GO sinyali olarak yaz.

- [ ] **Step 4: Commit**

```powershell
git add scripts/skin/macosskin/
git commit -m "SKIN spike: FlatUdeSkin/DarkSkin/FontPolicy (Segoe UI) + colorschemes"
```

---

### Task 3: Trimlenmiş SkinPatch + apply-skin.ps1

**Files:**
- Create: `scripts\skin\SkinPatch.java`
- Create: `scripts\skin\apply-skin.ps1`

**Interfaces:**
- Consumes: `macosskin.*` yardımcıları (Task 1-2).
- Produces: `apply-skin.ps1` (param `-Jar`).

- [ ] **Step 1: SkinPatch.java yaz (trimli: skin-kurulum + EDT + Flamingo kanaryası)**

`scripts\skin\SkinPatch.java`:
```java
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.io.File;
import java.io.FileOutputStream;

/**
 * SKIN spike: trimli skin-kurulum cekirdegi.
 *  - setSkin(String) + aF.run() sarmasi -> FlatUdeSkin/DarkSkin + FontPolicy kur.
 *    (Aqua put'lari YOK; Word* widget'lari YOK -> spike kapsami disi.)
 *  - wp.p.E kanvas rengi (teal -> notr/koyu gri).
 *  - Substance EDT denetimleri no-op (PDF export bozulmasin).
 *  - Flamingo kanaryasi: grup baslik bandi/cercevesi kaldirilir.
 * Argumanlar: <editor-app.jar> <out-dir>
 */
public class SkinPatch {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) { System.err.println("Kullanim: SkinPatch <jar> <out-dir>"); System.exit(2); }
        String jar = args[0];
        File outDir = new File(args[1]);
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(jar);

        CtClass slaf = pool.get("org.jvnet.substance.SubstanceLookAndFeel");
        CtMethod setSkinStr = slaf.getMethod("setSkin", "(Ljava/lang/String;)Z");
        setSkinStr.insertBefore(
            "{ macosskin.DarkMode.trace(\"setSkin arg=\" + $1 + \" installing=\" + macosskin.FlatUdeSkin.installing);"
          + "  if (!macosskin.FlatUdeSkin.installing) {"
          + "    macosskin.FlatUdeSkin.installing = true;"
          + "    try {"
          + "      org.jvnet.substance.api.SubstanceSkin __skin = macosskin.DarkMode.isDark()"
          + "        ? (org.jvnet.substance.api.SubstanceSkin) new macosskin.FlatUdeDarkSkin()"
          + "        : (org.jvnet.substance.api.SubstanceSkin) new macosskin.FlatUdeSkin();"
          + "      boolean __ok = org.jvnet.substance.SubstanceLookAndFeel.setSkin(__skin);"
          + "      try {"
          + "        org.jvnet.substance.fonts.FontSet __base ="
          + "          org.jvnet.substance.SubstanceLookAndFeel.getFontPolicy().getFontSet(\"Substance\", null);"
          + "        org.jvnet.substance.SubstanceLookAndFeel.setFontPolicy(new macosskin.FlatFontPolicy(__base));"
          + "      } catch (Throwable __ft) { macosskin.DarkMode.trace(\"font policy: \" + __ft); }"
          + "      macosskin.DarkMode.trace(\"skin kuruldu ok=\" + __ok + \" dark=\" + macosskin.DarkMode.isDark());"
          + "      return __ok;"
          + "    } catch (Throwable __t) { macosskin.DarkMode.trace(\"skin install HATA: \" + __t); }"
          + "    finally { macosskin.FlatUdeSkin.installing = false; }"
          + "  } }");
        writeClass(slaf, outDir);
        System.out.println("[SkinPatch] setSkin(String) sarmasi uygulandi.");

        CtClass aF = pool.get("tr.com.havelsan.uyap.system.editor.common.aF");
        aF.getMethod("run", "()V").insertBefore(
            "{ try {"
          + "    if (!(javax.swing.UIManager.getLookAndFeel() instanceof org.jvnet.substance.SubstanceLookAndFeel)) {"
          + "      macosskin.FlatUdeSkin.installing = true;"
          + "      try {"
          + "        org.jvnet.substance.api.SubstanceSkin __skin = macosskin.DarkMode.isDark()"
          + "          ? (org.jvnet.substance.api.SubstanceSkin) new macosskin.FlatUdeDarkSkin()"
          + "          : (org.jvnet.substance.api.SubstanceSkin) new macosskin.FlatUdeSkin();"
          + "        org.jvnet.substance.SubstanceLookAndFeel.setSkin(__skin);"
          + "        try {"
          + "          org.jvnet.substance.fonts.FontSet __base ="
          + "            org.jvnet.substance.SubstanceLookAndFeel.getFontPolicy().getFontSet(\"Substance\", null);"
          + "          org.jvnet.substance.SubstanceLookAndFeel.setFontPolicy(new macosskin.FlatFontPolicy(__base));"
          + "        } catch (Throwable __ft) { macosskin.DarkMode.trace(\"acilis font: \" + __ft); }"
          + "        macosskin.DarkMode.trace(\"ACILIS skin kuruldu dark=\" + macosskin.DarkMode.isDark());"
          + "      } finally { macosskin.FlatUdeSkin.installing = false; }"
          + "    }"
          + "  } catch (Throwable __t) { macosskin.DarkMode.trace(\"acilis skin HATA: \" + __t); } }");
        writeClass(aF, outDir);
        System.out.println("[SkinPatch] aF.run() acilis skin kurulumu eklendi.");

        try {
            CtClass wpP = pool.get("tr.com.havelsan.uyap.system.swing.wp.p");
            wpP.makeClassInitializer().insertAfter("E = macosskin.DarkMode.canvasColor();");
            writeClass(wpP, outDir);
            System.out.println("[SkinPatch] wp.p.E kanvas rengi yamasi uygulandi.");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] UYARI: kanvas rengi yamasi atlandi: " + t);
        }

        CtClass scu = pool.get("org.jvnet.substance.utils.SubstanceCoreUtilities");
        scu.getMethod("testComponentCreationThreadingViolation", "(Ljava/awt/Component;)V").setBody("{ }");
        scu.getMethod("testComponentStateChangeThreadingViolation", "(Ljava/awt/Component;)V").setBody("{ }");
        writeClass(scu, outDir);
        CtClass lwu = pool.get("org.jvnet.lafwidget.LafWidgetUtilities");
        lwu.getMethod("testComponentStateChangeThreadingViolation", "(Ljava/awt/Component;)V").setBody("{ }");
        writeClass(lwu, outDir);
        System.out.println("[SkinPatch] Substance EDT denetimleri no-op (PDF export).");

        try {
            CtClass bandUi = pool.get("org.pushingpixels.flamingo.internal.ui.ribbon.BasicRibbonBandUI");
            bandUi.getMethod("paintBandTitle", "(Ljava/awt/Graphics;Ljava/awt/Rectangle;Ljava/lang/String;)V").setBody("{ }");
            bandUi.getMethod("paintBandTitleBackground", "(Ljava/awt/Graphics;Ljava/awt/Rectangle;Ljava/lang/String;)V").setBody("{ }");
            writeClass(bandUi, outDir);
            CtClass rb = pool.get("org.pushingpixels.flamingo.internal.ui.ribbon.BasicRibbonBandUI$RoundBorder");
            rb.getMethod("paintBorder", "(Ljava/awt/Component;Ljava/awt/Graphics;IIII)V").setBody("{ }");
            writeClass(rb, outDir);
            System.out.println("[SkinPatch] FLAMINGO KANARYASI: band basligi/cercevesi kaldirildi (yol ACIK).");
        } catch (Throwable t) {
            System.out.println("[SkinPatch] FLAMINGO KANARYASI BASARISIZ: " + t);
        }
    }

    private static void writeClass(CtClass cc, File outDir) throws Exception {
        byte[] code = cc.toBytecode();
        File f = new File(outDir, cc.getName().replace('.', '/') + ".class");
        f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(code); }
    }
}
```

- [ ] **Step 2: apply-skin.ps1 yaz**

`scripts\skin\apply-skin.ps1`:
```powershell
# apply-skin.ps1 - SKIN spike: trimli Substance skin yamasi
param([Parameter(Mandatory)][string]$Jar)
. "$PSScriptRoot\..\common.ps1"

$JavassistVer = '3.30.2-GA'
$JavassistUrl = "https://repo1.maven.org/maven2/org/javassist/javassist/$JavassistVer/javassist-$JavassistVer.jar"
function Get-Javassist {
  $lib = Join-Path $VendorDir 'lib'; New-Dir $lib
  $jvs = Join-Path $lib "javassist-$JavassistVer.jar"
  if (-not (Test-Path $jvs)) { Write-Ok "javassist indiriliyor"; & curl.exe -L -s -o $jvs $JavassistUrl
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $jvs)) { throw "javassist indirilemedi" } }
  return $jvs
}

$jdk = Get-Jdk11Home
if (-not $jdk) { throw "JDK 11 yok; once deps" }
$javac = Join-Path $jdk 'bin\javac.exe'; $java = Join-Path $jdk 'bin\java.exe'; $jarTool = Join-Path $jdk 'bin\jar.exe'
$jvs = Get-Javassist
$skinDir = $PSScriptRoot

$work = Join-Path $BuildDir '_skin'
$helper = Join-Path $work 'helper'; $out = Join-Path $work 'out'
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
New-Dir $helper; New-Dir $out

Write-Ok "skin yardimcilari derleniyor"
& $javac --release 11 -encoding UTF-8 -cp "$Jar" -d $helper `
  (Join-Path $skinDir 'macosskin\DarkMode.java') `
  (Join-Path $skinDir 'macosskin\FlatUdeSkin.java') `
  (Join-Path $skinDir 'macosskin\FlatUdeDarkSkin.java') `
  (Join-Path $skinDir 'macosskin\FlatFontPolicy.java')
if ($LASTEXITCODE -ne 0) { throw "skin yardimcilari derlenemedi (Substance surumu farkli olabilir)" }

# colorschemes resource'larini helper agacina kopyala
Copy-Item (Join-Path $skinDir 'macosskin\flatude.colorschemes') (Join-Path $helper 'macosskin\')
Copy-Item (Join-Path $skinDir 'macosskin\flatude-dark.colorschemes') (Join-Path $helper 'macosskin\')

# yardimcilari + colorschemes jar'a ekle (patcher'dan ONCE)
& $jarTool uf $Jar -C $helper .
if ($LASTEXITCODE -ne 0) { throw "yardimcilar jar'a eklenemedi" }

Write-Ok "SkinPatch derleniyor"
& $javac --release 11 -encoding UTF-8 -cp "$jvs" -d $work (Join-Path $skinDir 'SkinPatch.java')
if ($LASTEXITCODE -ne 0) { throw "SkinPatch derlenemedi" }
Write-Ok "SkinPatch calistiriliyor"
& $java -cp "$work;$jvs" SkinPatch $Jar $out
if ($LASTEXITCODE -ne 0) { throw "SkinPatch calismadi" }

& $jarTool uf $Jar -C $out .
if ($LASTEXITCODE -ne 0) { throw "yamali siniflar jar'a eklenemedi" }
Write-Ok "SKIN uygulandi"
```

- [ ] **Step 3: Taze jar'a uygula + çıktıyı incele**

Run:
```powershell
.\build.ps1 -Only download
. .\scripts\common.ps1
.\scripts\skin\apply-skin.ps1 -Jar (Join-Path $InputDir $MainJar)
```
Expected: çıktıda şu satırlar (HATA/UYARI olmadan):
- `[SkinPatch] setSkin(String) sarmasi uygulandi.`
- `[SkinPatch] aF.run() acilis skin kurulumu eklendi.`
- `[SkinPatch] Substance EDT denetimleri no-op (PDF export).`
- `[SkinPatch] FLAMINGO KANARYASI: ... (yol ACIK).`
**`FLAMINGO KANARYASI BASARISIZ` çıkarsa** → Flamingo sürüm/imza farkı; findings'e yaz (kısmi NO-GO sinyali).

- [ ] **Step 4: Commit**

```powershell
git add scripts/skin/SkinPatch.java scripts/skin/apply-skin.ps1
git commit -m "SKIN spike: trimli SkinPatch + apply-skin (Aqua YOK, EDT VAR, Flamingo kanaryasi)"
```

---

### Task 4: SKIN bayrağı + tam build

**Files:**
- Modify: `scripts\patch.ps1`
- Modify: `scripts\build.ps1`
- Modify: `build.ps1`

- [ ] **Step 1: patch.ps1'e SKIN bloğu ekle**

`scripts\patch.ps1` içinde, `Write-Ok "yama tamam"` satırından ÖNCE ekle:
```powershell
  # --- SKIN (spike; varsayilan KAPALI, SKIN=1 ile acilir) ---
  if ($env:SKIN -eq '1') {
    $skinScript = Join-Path $PSScriptRoot 'skin\apply-skin.ps1'
    if (Test-Path $skinScript) {
      Write-Ok "SKIN uygulaniyor"
      & $skinScript -Jar $jar
    } else { Write-Warn2 "skin\apply-skin.ps1 yok; atlaniyor" }
  } else {
    Write-Ok "SKIN kapali (etkinlestirmek icin SKIN=1)"
  }
```

- [ ] **Step 2: build.ps1 (her ikisi) -Skin switch'i ekle**

`scripts\build.ps1` ve kök `build.ps1` `param(...)` bloklarına ekle:
```powershell
  [switch]$Skin,
```
`scripts\build.ps1`'de env eşlemesine ekle (`if ($Icons) {...}` yanına):
```powershell
  if ($Skin) { $env:SKIN = '1' }
```

- [ ] **Step 3: SKIN=1 ile tam build (stderr yönlendirmesi YOK)**

Run:
```powershell
.\build.ps1 -Only download
.\build.ps1 -Skin
"LASTEXITCODE: $LASTEXITCODE"
Get-ChildItem dist\*.exe | Select-Object Name, @{n='MB';e={[math]::Round($_.Length/1MB,1)}}, LastWriteTime
```
Expected: patch fazında `SKIN uygulaniyor` + Flamingo kanaryası ACIK; `dist\UyapDokumanEditoru-*.exe` üretilir, exit 0.
NOT: jpackage'i `2>&1`/`*>&1`/`Tee` ile SARMA (JAVA_TOOL_OPTIONS stderr satiri Stop ile build'i jpackage'da durdurur).

- [ ] **Step 4: Commit**

```powershell
git add scripts/patch.ps1 scripts/build.ps1 build.ps1
git commit -m "SKIN spike: SKIN bayragi (varsayilan kapali) + -Skin switch"
```

---

### Task 5: Çalıştır + ölçümleri topla (elle GUI + otomatik)

**Files:** (yok — doğrulama)

- [ ] **Step 1: Light modda başlat (trace açık)**

Run:
```powershell
Get-Process | Where-Object { $_.ProcessName -eq 'java' } | Stop-Process -Force -ErrorAction SilentlyContinue
$env:UDE_DUMMY=$null
. .\scripts\common.ps1
$java = Join-Path (Get-Jdk11Home) 'bin\java.exe'
$jar  = Join-Path $InputDir $MainJar
$env:JAVA_TOOL_OPTIONS = $null
Start-Process -FilePath $java -ArgumentList @('-Dmacosskin.debug=1','-Xms512M','-Xmx4096M','-cp',"`"$jar`"",'tr.com.havelsan.uyap.system.editor.common.WPAppManager','getNewWPInstance','EDITOR_TYPE_DOCUMENT')
```
**Elle doğrula:** uygulama AÇILIR (çöküş yok); şerit düz/modern görünür (grup başlık kutuları kalkmış = Flamingo kanaryası çalışıyor). Kullanıcı onayı beklenir.

- [ ] **Step 2: Başlangıç-sırası + hata logunu incele**

Run:
```powershell
$log = Join-Path $env:TEMP 'skinpatch-trace.log'
if (Test-Path $log) { "=== trace ==="; Get-Content $log } else { "trace yok" }
```
Expected: `ACILIS skin kuruldu dark=false` (veya setSkin satırı) görünür → skin UI'dan önce kuruldu. `HATA` satırı OLMAMALI.

- [ ] **Step 3: PDF export testi (KRİTİK)**

**Elle:** açılan belgede birkaç satır yaz → "PDF Olarak Kaydet" → bir konuma kaydet.
Expected: geçerli, **0-bayttan büyük** PDF üretilir; 80x29 boş pencere/sonsuz spinner YOK. (EDT nötrleştirmesi çalışıyor.) Üretilen PDF boyutunu doğrula:
```powershell
Get-ChildItem "$env:USERPROFILE\Desktop\*.pdf","$env:USERPROFILE\Documents\*.pdf" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime | Select-Object -Last 1 Name, Length
```

- [ ] **Step 4: Dark modda başlat**

Run (registry'yi geçici koyuya çek VEYA pref ile zorla):
```powershell
Get-Process | Where-Object { $_.ProcessName -eq 'java' } | Stop-Process -Force -ErrorAction SilentlyContinue
# colorMode pref'ini 'dark' yap (registry'ye dokunmadan)
$java = Join-Path (Get-Jdk11Home) 'bin\java.exe'
& $java -cp "$env:TEMP\skintest" -e 2>$null  # (yoksa atla)
[Microsoft.Win32.Registry]::SetValue("HKEY_CURRENT_USER\Software\JavaSoft\Prefs\ude-win","colorMode","dark") 2>$null
. .\scripts\common.ps1
$jar = Join-Path $InputDir $MainJar
Start-Process -FilePath $java -ArgumentList @('-Dmacosskin.debug=1','-cp',"`"$jar`"",'tr.com.havelsan.uyap.system.editor.common.WPAppManager','getNewWPInstance','EDITOR_TYPE_DOCUMENT')
```
**Elle doğrula:** uygulama KOYU palette açılır (şerit/kanvas koyu gri). Kullanıcı onayı.
NOT: java.util.prefs koyu yolu güvenilmezse, doğrudan Windows temasını Ayarlar > Kişiselleştirme > Renkler > Koyu yapıp `colorMode` pref'ini sil (system mod registry okur).

- [ ] **Step 5: HiDPI + eksik-UIClass taraması**

**Elle:** Görüntü ölçeklemeyi %125/%150 yapıp (Ayarlar > Ekran) uygulamayı yeniden başlat → metin keskin, layout bozulmuyor mu gözle.
Run (konsol stderr'inde eksik UI sınıfı/exception var mı — pencereyi konsoldan başlatıp gözlemle):
```powershell
& $java -Dmacosskin.debug=1 -cp "$jar" tr.com.havelsan.uyap.system.editor.common.WPAppManager getNewWPInstance EDITOR_TYPE_DOCUMENT 2>&1 | Select-String 'Exception|getUI|ClassNotFound|NoClassDef' | Select-Object -First 20
```
Expected: Aqua put'ları düşürüldüğü için `AquaScrollBarUI`/`ClassNotFound` HATASI OLMAMALI (zaten put etmiyoruz). Substance scrollbar'ları render eder.

---

### Task 6: Bulgular dokümanı

**Files:**
- Create: `docs\superpowers\specs\2026-06-25-skin-spike-findings.md`

- [ ] **Step 1: Bulguları yaz**

Şablon (gerçek sonuçlarla DOLDUR — placeholder bırakma):
```markdown
# SKIN Fizibilite Spike — Bulgular

**Tarih:** 2026-06-25  **Dal:** spike/skin-feasibility (merge edilmez)

## Karar: GIT / GITME  (gerçek sonuç)

## Risk sonuçları
| Risk | Sonuç | Not |
|---|---|---|
| Substance skin Windows'ta kurulur/render eder | EVET/HAYIR | ekran görüntüsü light |
| Koyu palet (appearance=system'siz) | EVET/HAYIR | ekran görüntüsü dark |
| Kayit defteri koyu-mod tespiti | EVET/HAYIR | DarkMode.isDark registry ile tutarli |
| Aqua put'lari dusuruldu -> Substance scrollbar | EVET/HAYIR | eksik-UIClass hatasi yok |
| PDF export (EDT notr.) calisiyor | EVET/HAYIR | uretilen PDF boyutu |
| Flamingo kanaryasi (yama yolu acik) | EVET/HAYIR | grup baslik kutulari kalkti |
| Baslangic sirasi (skin UI'dan once) | EVET/HAYIR | trace satiri |
| HiDPI %125/%150 | EVET/HAYIR | gozle |

## Mac kuplaji -> Windows ikamesi
| Mac | Windows ikamesi |
|---|---|
| com.apple.laf.Aqua{ScrollBar,Slider}UI put | DUSURULDU (Substance default) |
| defaults read AppleInterfaceStyle | reg query AppsUseLightTheme |
| appearance=system | (yok; palet Substance'tan) |
| Helvetica Neue | Segoe UI |
| Preferences node ude-mac-arm | ude-win |

## Tam SKIN portu (#7) taslak gorev listesi
- macosskin -> com.udewin.skin yeniden adlandirma (resource yollari dahil)
- Tam Flamingo sadelestirme (paintTaskArea, ToggleButton alt cubuk, CommandButton dolgu, orb, tooltip)
- Word* widget'lari (Button/Tabs/Combo/Check/Field/Tooltip)
- IconDarken + ModeAwareImage (koyu ikonlar) [ICONS alt-projesiyle koordine]
- ModeSwitch canli gecis + renk-modu combo (WinLook agent -> -javaagent altyapisi gerekir)
- DarkPage (koyu belge arkaplani)
- Koyu/acik palet kalibrasyonu (Word-Windows piksel olcumu)
- PopupRemap, MenuMarks, FlatEtchedBorder

## Bilinen sinirlar / surprizler
(spike sirasinda cikan gercek notlar)
```

- [ ] **Step 2: Commit**

```powershell
git add docs/superpowers/specs/2026-06-25-skin-spike-findings.md
git commit -m "SKIN spike: bulgular dokumani (git/gitme + tam-port taslagi)"
```

- [ ] **Step 3: Spike'ı kapat**

`superpowers:finishing-a-development-branch` skill'ini çağır. Spike dalı atılabilir: bulgular + spec/plan main'e cherry-pick edilebilir, spike kodu dalda kalır (merge edilmez). Kullanıcıya GIT/GITME kararını ve sonraki adımı (tam SKIN portu #7) sun.

---

## Plan Öz-İncelemesi

**Spec kapsama:**
- Risk 1 (Aqua) → Task 3 (put'lar yok) + Task 5 Step 5 (eksik-UIClass taraması) ✓
- Risk 2 (skin render) → Task 3 + Task 5 Step 1 ✓
- Risk 3 (registry dark) → Task 1 ✓
- Risk 4 (appearance=system'siz koyu) → Task 5 Step 4 ✓
- PDF export EDT nötrleştirme → Task 3 (scu/lwu) + Task 5 Step 3 ✓
- Flamingo kanaryası → Task 3 + Task 5 Step 1 ✓
- Başlangıç sırası → Task 5 Step 2 ✓
- HiDPI/font → Task 5 Step 5 ✓
- Findings doc (go/no-go + couplings + screenshots + #7 taslağı) → Task 6 ✓
- Atılabilir dal, merge yok → Global Constraints + Task 6 Step 3 ✓

**Placeholder taraması:** Findings şablonu kasıtlı doldurulacak (gerçek sonuç); kod adımları tam içerikli.

**Tip tutarlılığı:** `macosskin.DarkMode/FlatUdeSkin/FlatUdeDarkSkin/FlatFontPolicy` adları Task 1-3 arası tutarlı; SkinPatch enjekte FQN'leri yardımcı paket adıyla (`macosskin`) eşleşir.
