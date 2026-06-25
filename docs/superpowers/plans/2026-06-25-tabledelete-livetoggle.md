# TABLEDELETE + LIVETOGGLE Windows Portu — Uygulama Planı

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mac portundaki LIVETOGGLE (otomatik düzeltme toggle'ları anında etkin) ve TABLEDELETE (Backspace/Delete ile tablo silme) özelliklerini `ude-win-x64`'e build-zamanı Javassist yamaları olarak aktarmak.

**Architecture:** Her özellik bir yardımcı sınıf (jar'a `jar uf` ile enjekte) + bir `*Patch.java` Javassist patcher (UDE obfuscate sınıflarını yeniden yazar) + bir `apply-*.ps1` (derle→enjekte→yama→birleştir) içerir. TABLEDELETE ek olarak küçük bir `TableDeleteInstaller` (global FOCUS_GAINED AWTEventListener) kullanır ve patcher bunu `WPAppManager.main`'e `insertBefore` ile bağlar. Mevcut `nativedialog` deseni birebir izlenir.

**Tech Stack:** PowerShell 5.1, JDK 11 (`javac --release 11`, `javap`, `jar`), Javassist 3.30.2-GA, Java Swing (`javax.swing.text`).

## Global Constraints

- Yardımcı sınıf paketleri (mevcut `com.udewin.nativedialog` konvansiyonu): **`com.udewin.livetoggle`** ve **`com.udewin.tabledelete`** (spec'teki `winlivetoggle`/`wintabledelete` yerine; Windows depo konvansiyonuyla tutarlılık). Mac kaynağındaki `macoslivetoggle`/`macostextkeys` paketleri buna göre yeniden adlandırılır.
- JDK 11 ile derle: `javac --release 11`. JDK 11 kökü `Get-Jdk11Home` ile bulunur.
- Javassist sürümü: `3.30.2-GA` (mevcut `apply-nativedialog.ps1` ile aynı; `vendor\lib\` altına indirilir).
- Classpath ayıracı **`;`** (Windows). Tüm yollar PowerShell'de tırnaklı/`-LiteralPath` ile geçilir.
- Javassist gövde string'lerinde `//` yorum YASAK; sınıf başına tek `writeFile`/`writeClass`.
- Bayraklar **varsayılan açık**, `$env:LIVETOGGLE='0'` / `$env:TABLEDELETE='0'` ile kapanır (`nativedialog` konvansiyonu: yalnız `'0'` kapatır).
- Yamalar `InputDir`'deki çıkarılmış `editor-app.jar` üzerinde çalışır (yeniden-üretilebilir build artefaktı); orijinal `downloads\` dosyasına dokunulmaz.
- Referans kaynak: `https://github.com/saidsurucu/ude-mac-arm64` (aşağıda `$MAC` ile gösterilen geçici klona).

---

### Task 0: Referans kaynağı ve doğrulama ortamını hazırla

**Files:**
- (yok — ortam hazırlığı)

**Interfaces:**
- Produces: `$MAC` referans klonu (mac kaynak dosyaları), `InputDir\editor-app.jar` (doğrulanacak Windows jar'ı), `$JAVAP` yolu.

- [ ] **Step 1: Mac referans deposunu geçici dizine klonla**

```bash
git clone --depth 1 https://github.com/saidsurucu/ude-mac-arm64 "$env:TEMP\ude-mac-ref"
```

PowerShell:
```powershell
$MAC = "$env:TEMP\ude-mac-ref"
if (-not (Test-Path $MAC)) { git clone --depth 1 https://github.com/saidsurucu/ude-mac-arm64 $MAC }
```

- [ ] **Step 2: deps + download fazlarını çalıştır (jar'ı getir)**

Run:
```powershell
.\build.ps1 -Only deps
.\build.ps1 -Only download
```
Expected: `build\input\editor-app.jar` oluşur. (deps ~600MB indirir, ilk sefer yavaş.)

- [ ] **Step 3: javap yolunu çöz**

```powershell
. .\scripts\common.ps1
$JAVAP = Join-Path (Get-Jdk11Home) 'bin\javap.exe'
Test-Path $JAVAP   # True olmalı
```

---

### Task 1: Obfuscate ad doğrulama geçidi (KRİTİK — yamadan önce)

**Files:**
- Create: `scripts\verify-names.ps1`

**Interfaces:**
- Consumes: `InputDir\editor-app.jar`, `$JAVAP` (Task 0).
- Produces: tüm hedef adların VARLIĞI + imza/görünürlük onayı. Eşleşmezse **dur ve araştır**.

- [ ] **Step 1: Doğrulama scriptini yaz**

`scripts\verify-names.ps1`:
```powershell
# verify-names.ps1 - TABLEDELETE+LIVETOGGLE icin obfuscate adlari Windows jar'inda dogrula
. "$PSScriptRoot\common.ps1"
$jar = Join-Path $InputDir $MainJar
if (-not (Test-Path $jar)) { throw "editor-app.jar yok; once download" }
$javap = Join-Path (Get-Jdk11Home) 'bin\javap.exe'

function Show([string]$cls) {
  Write-Host "==> $cls" -ForegroundColor Cyan
  & $javap -p -classpath "$jar" $cls
}
$P = 'tr.com.havelsan.uyap.system.editor.common.'
# LIVETOGGLE hedefleri
Show ($P + 'text.dq'); Show ($P + 'text.dA'); Show ($P + 'text.db')
Show ($P + 'gui.kP')
Show ($P + 'text.hN'); Show ($P + 'text.fY'); Show ($P + 'text.im')
Show 'tr.com.havelsan.uyap.system.pki.b.l'
Show 'tr.gov.uyap.system.a.b.a.a.z'
Show ($P + 'gui.ak')
# TABLEDELETE hedefleri
Show ($P + 'WPAppManager')
Show 'tr.com.havelsan.uyap.system.editor.common.model.v'   # wp.model.v.f(int) miras kaynagi
```

- [ ] **Step 2: Çalıştır ve elle doğrula**

Run: `.\scripts\verify-names.ps1`

Beklenen kontroller (her biri çıktıda görünmeli):
- `text.dq` / `text.dA` / `text.db` → `public void a(java.awt.event.ActionEvent)`
- `gui.kP` → bir `b(...)` metodu (diyalog)
- `text.hN` / `text.fY` / `text.im` → sınıf mevcut (KeyListener türevleri)
- `pki.b.l` → statik no-arg `()l` döndüren metod + no-arg `Properties` döndüren metod
- `z` → statik `JCheckBoxMenuItem` alanları; `gui.ak` → aynı şekilde
- `WPAppManager` → `public static void main(java.lang.String[])`
- `model.v` (veya UDE'nin DocumentEx üst sınıfı) → **`public ... f(int)`** (TableDelete reflection `getMethod` yalnız public bulur). Not: gerçek doküman sınıfı `f(int)`'i miras alır; `model.v` görünmezse jar'da `f(int)` içeren `tr/...` sınıfını `javap` ile arayıp doğru FQN'i not et.

- [ ] **Step 3: Karar**

Tüm adlar eşleşiyorsa → Task 2'ye geç. **Herhangi biri eşleşmiyorsa DUR**: Windows↔Mac obfuscation-map sapması var; bu, planın geri kalanını geçersiz kılar → kullanıcıya bildir, ayrı araştırma başlat.

- [ ] **Step 4: Commit**

```powershell
git add scripts/verify-names.ps1
git commit -m "Obfuscate ad dogrulama scripti ekle (tabledelete+livetoggle)"
```

---

### Task 2: TABLEDELETE tespit çekirdeği + birim testi (TDD)

**Files:**
- Create: `scripts\tabledelete\com\udewin\tabledelete\TableDelete.java`
- Test: `tests\TableDeleteDetectTest.java`

**Interfaces:**
- Produces: `com.udewin.tabledelete.TableDelete` — `public static int targetForBackspace(DocView, int caret, int selStart, int selEnd)`, `public static int targetForDelete(...)`, iç `interface DocView { Element charAt(int); String text(int,int); }`, `public static void bind(JTextComponent)`.

- [ ] **Step 1: Testi yaz (Mac testini porta uyarlanmış paketle)**

`tests\TableDeleteDetectTest.java` — Mac `$MAC\tests\TableDeleteDetectTest.java` içeriğini kopyala, **tek değişiklik**: ilk satır `package macostextkeys;` → `package com.udewin.tabledelete;` ve `import macostextkeys.TableDelete.DocView;` → `import com.udewin.tabledelete.TableDelete.DocView;`. Geri kalan birebir aynı (sahte Element ağacı + 9 `check(...)` senaryosu).

- [ ] **Step 2: Test impl olmadan derlenmeyerek başarısız olsun**

Run:
```powershell
$jdk = (& { . .\scripts\common.ps1; Get-Jdk11Home })
$javac = Join-Path $jdk 'bin\javac.exe'
New-Item -ItemType Directory -Force "$env:TEMP\tabdel" | Out-Null
& $javac -d "$env:TEMP\tabdel" tests\TableDeleteDetectTest.java
```
Expected: FAIL — `package com.udewin.tabledelete does not exist` / `cannot find symbol TableDelete` (impl henüz yok).

- [ ] **Step 3: TableDelete.java'yı porta uyarla**

`scripts\tabledelete\com\udewin\tabledelete\TableDelete.java` — Mac `$MAC\scripts\macos-textkeys\macostextkeys\TableDelete.java` içeriğini kopyala, şu üç düzenleme:
1. `package macostextkeys;` → `package com.udewin.tabledelete;`
2. `log()` metodunu Windows yoluna çevir (mevcut gövdeyi şununla değiştir):
```java
    /** UDE_TABLEDELLOG=1 iken %LOCALAPPDATA%\ude-tabledelete.txt (System.err yutulur). */
    private static void log(String msg) {
        if (!"1".equals(System.getenv("UDE_TABLEDELLOG"))) return;
        try {
            String base = System.getenv("LOCALAPPDATA");
            if (base == null) base = System.getProperty("user.home");
            java.io.File f = new java.io.File(base, "ude-tabledelete.txt");
            try (java.io.FileWriter w = new java.io.FileWriter(f, true)) {
                w.write(msg + "\n");
            }
        } catch (Throwable ignore) {}
    }
```
3. Diğer her şey (DocView, tableAncestor, firstTableInRange, isEmptyTable, targetForBackspace/Delete, bind, tryDelete, deleteSelectionWithTables, delegate, unwrap) **birebir** korunur.

- [ ] **Step 4: Derle + testi çalıştır, GEÇ**

Run:
```powershell
& $javac -d "$env:TEMP\tabdel" scripts\tabledelete\com\udewin\tabledelete\TableDelete.java tests\TableDeleteDetectTest.java
& (Join-Path $jdk 'bin\java.exe') -cp "$env:TEMP\tabdel" com.udewin.tabledelete.TableDeleteDetectTest
```
Expected: PASS — son satır `TUM TESTLER GECTI` (9 `ok [...]` satırı).

- [ ] **Step 5: Commit**

```powershell
git add scripts/tabledelete/com/udewin/tabledelete/TableDelete.java tests/TableDeleteDetectTest.java
git commit -m "TABLEDELETE tespit cekirdegini porta uyarla + birim testi"
```

---

### Task 3: TableDeleteInstaller + TableDeletePatch + apply-tabledelete.ps1

**Files:**
- Create: `scripts\tabledelete\com\udewin\tabledelete\TableDeleteInstaller.java`
- Create: `scripts\tabledelete\TableDeletePatch.java`
- Create: `scripts\tabledelete\apply-tabledelete.ps1`

**Interfaces:**
- Consumes: `com.udewin.tabledelete.TableDelete.bind(JTextComponent)` (Task 2).
- Produces: `com.udewin.tabledelete.TableDeleteInstaller.install()` (idempotent); `apply-tabledelete.ps1` (param `-Jar`).

- [ ] **Step 1: TableDeleteInstaller.java yaz (idempotent global focus listener)**

`scripts\tabledelete\com\udewin\tabledelete\TableDeleteInstaller.java`:
```java
package com.udewin.tabledelete;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;
import javax.swing.text.JTextComponent;

/**
 * TableDelete'i her metin alanina baglar. WPAppManager.main'e build-zamani
 * insertBefore ile cagrilir (Mac'teki -javaagent yerine). Listener yalniz
 * gelecekteki FOCUS_GAINED olaylarina tepki verir; kurulum aninda Swing'e
 * dokunmaz, bu yuzden EDT gerekmez.
 */
public final class TableDeleteInstaller {
    private static boolean installed = false;
    private TableDeleteInstaller() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;
        try {
            Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
                public void eventDispatched(AWTEvent e) {
                    if (e.getID() != FocusEvent.FOCUS_GAINED) return;
                    Object src = e.getSource();
                    if (src instanceof JTextComponent) {
                        try { TableDelete.bind((JTextComponent) src); }
                        catch (Throwable ignore) {}
                    }
                }
            }, AWTEvent.FOCUS_EVENT_MASK);
        } catch (Throwable t) {
            System.err.println("[tabledelete] kurulamadi: " + t);
        }
    }
}
```

- [ ] **Step 2: TableDeletePatch.java yaz**

`scripts\tabledelete\TableDeletePatch.java`:
```java
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.io.File;
import java.io.FileOutputStream;

/**
 * WPAppManager.main basina TableDeleteInstaller.install() cagrisi enjekte eder.
 * Yardimci siniflar (TableDelete, TableDeleteInstaller) apply-tabledelete.ps1
 * tarafindan ONCE jar'a eklenir; bu patcher yalniz WPAppManager'i yamalar.
 *
 * Argumanlar: <editor-app.jar> <helper-classes-dir> <out-dir>
 */
public class TableDeletePatch {
    private static final String MAIN_CLASS =
        "tr.com.havelsan.uyap.system.editor.common.WPAppManager";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Kullanim: TableDeletePatch <jar> <helper-dir> <out-dir>");
            System.exit(2);
        }
        String jar = args[0];
        String helperDir = args[1];
        File outDir = new File(args[2]);

        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(jar);
        pool.insertClassPath(helperDir);

        CtClass cc = pool.get(MAIN_CLASS);
        CtMethod main = cc.getMethod("main", "([Ljava/lang/String;)V");
        main.insertBefore("com.udewin.tabledelete.TableDeleteInstaller.install();");

        byte[] code = cc.toBytecode();
        File f = new File(outDir, cc.getName().replace('.', '/') + ".class");
        f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(code); }
        cc.detach();
        System.out.println("[TableDeletePatch] WPAppManager.main yamalandi.");
    }
}
```
Not: `getMethod` hedef bulunamazsa `NotFoundException` fırlatır → build sesli durur (guard otomatik).

- [ ] **Step 3: apply-tabledelete.ps1 yaz**

`scripts\tabledelete\apply-tabledelete.ps1`:
```powershell
# apply-tabledelete.ps1 - Backspace/Delete ile tablo silme yamasi
param([Parameter(Mandatory)][string]$Jar)
. "$PSScriptRoot\..\common.ps1"

$JavassistVer = '3.30.2-GA'
$JavassistUrl = "https://repo1.maven.org/maven2/org/javassist/javassist/$JavassistVer/javassist-$JavassistVer.jar"
function Get-Javassist {
  $lib = Join-Path $VendorDir 'lib'; New-Dir $lib
  $jvs = Join-Path $lib "javassist-$JavassistVer.jar"
  if (-not (Test-Path $jvs)) {
    Write-Ok "javassist indiriliyor"
    & curl.exe -L -s -o $jvs $JavassistUrl
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $jvs)) { throw "javassist indirilemedi" }
  }
  return $jvs
}

$jdk = Get-Jdk11Home
if (-not $jdk) { throw "JDK 11 yok; once deps" }
$javac   = Join-Path $jdk 'bin\javac.exe'
$java    = Join-Path $jdk 'bin\java.exe'
$jarTool = Join-Path $jdk 'bin\jar.exe'
$jvs     = Get-Javassist

$work   = Join-Path $BuildDir '_tabledelete'
$helper = Join-Path $work 'helper'   # derlenmis com.udewin.tabledelete.*
$out    = Join-Path $work 'out'      # yamali WPAppManager
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
New-Dir $helper; New-Dir $out

Write-Ok "yardimci siniflar derleniyor"
& $javac --release 11 -d $helper `
  (Join-Path $PSScriptRoot 'com\udewin\tabledelete\TableDelete.java') `
  (Join-Path $PSScriptRoot 'com\udewin\tabledelete\TableDeleteInstaller.java')
if ($LASTEXITCODE -ne 0) { throw "yardimci siniflar derlenemedi" }

# Yardimcilari ONCE jar'a ekle (patcher insertBefore bunlari classpath'ten cozer)
& $jarTool uf $Jar -C $helper .
if ($LASTEXITCODE -ne 0) { throw "yardimcilar jar'a eklenemedi" }

Write-Ok "TableDeletePatch derleniyor"
& $javac --release 11 -cp "$jvs;$helper" -d $work (Join-Path $PSScriptRoot 'TableDeletePatch.java')
if ($LASTEXITCODE -ne 0) { throw "patcher derlenemedi" }
Write-Ok "TableDeletePatch calistiriliyor"
& $java -cp "$work;$jvs;$helper" TableDeletePatch $Jar $helper $out
if ($LASTEXITCODE -ne 0) { throw "patcher calismadi" }

& $jarTool uf $Jar -C $out .
if ($LASTEXITCODE -ne 0) { throw "yamali sinif jar'a eklenemedi" }
Write-Ok "TABLEDELETE uygulandi"
```

- [ ] **Step 4: jar'a karşı tek başına çalıştır (doğrulama)**

Run:
```powershell
.\scripts\tabledelete\apply-tabledelete.ps1 -Jar (Join-Path (& { . .\scripts\common.ps1; $InputDir }) 'editor-app.jar')
```
Expected: çıktıda `[TableDeletePatch] WPAppManager.main yamalandi.` + `TABLEDELETE uygulandi`. Hata fırlatmaz. (Bu jar'ı kirletir; Task 6 öncesi `download` ile tazelenir.)

- [ ] **Step 5: Yamalı sınıfı doğrula**

Run:
```powershell
$jdk = (& { . .\scripts\common.ps1; Get-Jdk11Home })
& (Join-Path $jdk 'bin\javap.exe') -c -classpath (Join-Path (& { . .\scripts\common.ps1; $InputDir }) 'editor-app.jar') tr.com.havelsan.uyap.system.editor.common.WPAppManager | Select-String 'TableDeleteInstaller'
```
Expected: `install` çağrısını içeren satır görünür.

- [ ] **Step 6: Commit**

```powershell
git add scripts/tabledelete/
git commit -m "TABLEDELETE: installer + patcher + apply scripti"
```

---

### Task 4: LIVETOGGLE port (helper + patcher + apply scripti)

**Files:**
- Create: `scripts\livetoggle\com\udewin\livetoggle\LiveToggle.java`
- Create: `scripts\livetoggle\LiveTogglePatch.java`
- Create: `scripts\livetoggle\apply-livetoggle.ps1`

**Interfaces:**
- Produces: `com.udewin.livetoggle.LiveToggle.syncSource(String, ActionEvent)`, `com.udewin.livetoggle.LiveToggle.apply(String)`; `apply-livetoggle.ps1` (param `-Jar`).

- [ ] **Step 1: LiveToggle.java'yı porta uyarla**

`scripts\livetoggle\com\udewin\livetoggle\LiveToggle.java` — Mac `$MAC\scripts\macos-livetoggle\macoslivetoggle\LiveToggle.java` içeriğini **birebir** kopyala, tek değişiklik: `package macoslivetoggle;` → `package com.udewin.livetoggle;`. (İçerideki tüm UDE FQN sabitleri, reflection mantığı, Zemberek kontrolü değişmez.)

- [ ] **Step 2: LiveTogglePatch.java'yı porta uyarla**

`scripts\livetoggle\LiveTogglePatch.java` — Mac `$MAC\scripts\macos-livetoggle\LiveTogglePatch.java` içeriğini kopyala, şu iki değişiklik:
1. `m.insertBefore("macoslivetoggle.LiveToggle.syncSource(...` → `com.udewin.livetoggle.LiveToggle.syncSource(...` (string içindeki FQN).
2. `m.insertAfter("macoslivetoggle.LiveToggle.apply(...` → `com.udewin.livetoggle.LiveToggle.apply(...`.
Ek olarak, guard mesajına teşhis ekle (Codex önerisi): `removed[0] != 1` kontrolünde fırlatılan istisna zaten sınıf adını ve bulunan sayıyı içeriyor (`cls + ": beklenen 1 ... bulunan " + removed[0]`) — korunur.
`cc.writeFile(outDir)` deseni Mac'le aynı (her sınıf için tek writeFile).

- [ ] **Step 3: apply-livetoggle.ps1 yaz**

`scripts\livetoggle\apply-livetoggle.ps1`:
```powershell
# apply-livetoggle.ps1 - otomatik duzeltme toggle'larini aninda etkin yapar
param([Parameter(Mandatory)][string]$Jar)
. "$PSScriptRoot\..\common.ps1"

$JavassistVer = '3.30.2-GA'
$JavassistUrl = "https://repo1.maven.org/maven2/org/javassist/javassist/$JavassistVer/javassist-$JavassistVer.jar"
function Get-Javassist {
  $lib = Join-Path $VendorDir 'lib'; New-Dir $lib
  $jvs = Join-Path $lib "javassist-$JavassistVer.jar"
  if (-not (Test-Path $jvs)) {
    Write-Ok "javassist indiriliyor"
    & curl.exe -L -s -o $jvs $JavassistUrl
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $jvs)) { throw "javassist indirilemedi" }
  }
  return $jvs
}

$jdk = Get-Jdk11Home
if (-not $jdk) { throw "JDK 11 yok; once deps" }
$javac   = Join-Path $jdk 'bin\javac.exe'
$java    = Join-Path $jdk 'bin\java.exe'
$jarTool = Join-Path $jdk 'bin\jar.exe'
$jvs     = Get-Javassist

$work   = Join-Path $BuildDir '_livetoggle'
$helper = Join-Path $work 'helper'
$out    = Join-Path $work 'out'
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
New-Dir $helper; New-Dir $out

Write-Ok "LiveToggle yardimcisi derleniyor"
& $javac --release 11 -d $helper (Join-Path $PSScriptRoot 'com\udewin\livetoggle\LiveToggle.java')
if ($LASTEXITCODE -ne 0) { throw "yardimci derlenemedi" }

# Yardimciyi ONCE jar'a ekle (patcher insertBefore/After bunu classpath'ten cozer)
& $jarTool uf $Jar -C $helper .
if ($LASTEXITCODE -ne 0) { throw "yardimci jar'a eklenemedi" }

Write-Ok "LiveTogglePatch derleniyor"
& $javac --release 11 -cp "$jvs;$helper" -d $work (Join-Path $PSScriptRoot 'LiveTogglePatch.java')
if ($LASTEXITCODE -ne 0) { throw "patcher derlenemedi" }
Write-Ok "LiveTogglePatch calistiriliyor"
& $java -cp "$work;$jvs;$helper" LiveTogglePatch $Jar $out
if ($LASTEXITCODE -ne 0) { throw "patcher calismadi" }

& $jarTool uf $Jar -C $out .
if ($LASTEXITCODE -ne 0) { throw "yamali siniflar jar'a eklenemedi" }
Write-Ok "LIVETOGGLE uygulandi"
```
Not: Mac `LiveTogglePatch.main(args)` imzası `<jar> <out-dir>` (iki argüman) — `main`'de `args[0]`=jar, `args[1]`=outDir. ps1 buna uygun çağırır (`$Jar $out`).

- [ ] **Step 4: jar'a karşı tek başına çalıştır (doğrulama)**

Önce jar'ı tazele (Task 3 onu kirletti):
```powershell
.\build.ps1 -Only download
.\scripts\livetoggle\apply-livetoggle.ps1 -Jar (Join-Path (& { . .\scripts\common.ps1; $InputDir }) 'editor-app.jar')
```
Expected: çıktıda `[livetoggle] dq/dA/db yamalandı (...)` + `LIVETOGGLE uygulandi`. Guard tetiklenmez (her sınıfta tam 1 `kP.b`).

- [ ] **Step 5: Commit**

```powershell
git add scripts/livetoggle/
git commit -m "LIVETOGGLE: helper + patcher + apply scripti"
```

---

### Task 5: patch.ps1 ve build.ps1'e bayrakları bağla

**Files:**
- Modify: `scripts\patch.ps1`
- Modify: `scripts\build.ps1`
- Modify: `build.ps1` (kök)

**Interfaces:**
- Consumes: `apply-tabledelete.ps1`, `apply-livetoggle.ps1` (Task 3, 4).

- [ ] **Step 1: patch.ps1'e iki blok ekle**

`scripts\patch.ps1` içinde, native diyalog bloğundan SONRA, `Write-Ok "yama tamam"` satırından ÖNCE şunu ekle:
```powershell
  # --- LIVETOGGLE (varsayilan ACIK, =0 ile kapanir) ---
  if ($env:LIVETOGGLE -eq '0') {
    Write-Ok "livetoggle kapali (LIVETOGGLE=0)"
  } else {
    $ltScript = Join-Path $PSScriptRoot 'livetoggle\apply-livetoggle.ps1'
    if (Test-Path $ltScript) {
      Write-Ok "LIVETOGGLE uygulaniyor"
      & $ltScript -Jar $jar
    } else { Write-Warn2 "livetoggle\apply-livetoggle.ps1 yok; atlaniyor" }
  }

  # --- TABLEDELETE (varsayilan ACIK, =0 ile kapanir) ---
  if ($env:TABLEDELETE -eq '0') {
    Write-Ok "tabledelete kapali (TABLEDELETE=0)"
  } else {
    $tdScript = Join-Path $PSScriptRoot 'tabledelete\apply-tabledelete.ps1'
    if (Test-Path $tdScript) {
      Write-Ok "TABLEDELETE uygulaniyor"
      & $tdScript -Jar $jar
    } else { Write-Warn2 "tabledelete\apply-tabledelete.ps1 yok; atlaniyor" }
  }
```

- [ ] **Step 2: scripts\build.ps1 param + env eşlemesi ekle**

`scripts\build.ps1` `param(...)` bloğuna ekle:
```powershell
  [switch]$NoLiveToggle,
  [switch]$NoTableDelete,
```
Ve `if ($NoNativeDialogs) {...}` satırından sonra:
```powershell
  if ($NoLiveToggle)    { $env:LIVETOGGLE = '0' }
  if ($NoTableDelete)   { $env:TABLEDELETE = '0' }
```

- [ ] **Step 3: kök build.ps1 param ekle (passthrough)**

`build.ps1` (kök) `param(...)` bloğuna ekle:
```powershell
  [switch]$NoLiveToggle,
  [switch]$NoTableDelete,
```
(Gövde zaten `@PSBoundParameters` ile geçiriyor; ek değişiklik gerekmez.)

- [ ] **Step 4: patch fazını çalıştır (tazelenmiş jar üzerinde)**

Run:
```powershell
.\build.ps1 -Only download
.\build.ps1 -Only patch
```
Expected: çıktıda hem `LIVETOGGLE uygulaniyor` hem `TABLEDELETE uygulaniyor` + her ikisinin başarı satırları. Hata yok.

- [ ] **Step 5: Kapatma bayrağını doğrula**

Run:
```powershell
.\build.ps1 -Only download
.\build.ps1 -Only patch -NoTableDelete -NoLiveToggle
```
Expected: `livetoggle kapali (LIVETOGGLE=0)` + `tabledelete kapali (TABLEDELETE=0)`.

- [ ] **Step 6: Commit**

```powershell
git add scripts/patch.ps1 scripts/build.ps1 build.ps1
git commit -m "LIVETOGGLE+TABLEDELETE bayraklarini patch/build hattina bagla"
```

---

### Task 6: Tam build + elle GUI kabul testi

**Files:**
- (yok — uçtan uca doğrulama)

- [ ] **Step 1: Temiz tam build**

Run:
```powershell
.\build.ps1 -Only download
.\build.ps1
```
Expected: `dist\UyapDokumanEditoru-<sürüm>.exe` üretilir; patch fazında her iki özellik uygulanır.

- [ ] **Step 2: Kur**

`dist\` altındaki `.exe`'yi çalıştırıp kur (SmartScreen → "Yine de çalıştır").

- [ ] **Step 3: LIVETOGGLE kabul**

Uygulamayı aç, boş belge oluştur. Görünüm/Biçim sekmesinde "Otomatik Büyük Harf" (veya "Kelime Denetimi") onay kutusunu işaretle.
Expected: **"yeniden başlatın" diyaloğu ÇIKMAZ**; özellik açık belgede anında etkin (büyük harf otomatiği hemen yazıma uygulanır). Kapatınca anında devre dışı.

- [ ] **Step 4: TABLEDELETE kabul**

Belgede bir tablo ekle (veya yapıştır). (a) İmleci tablonun hemen ardına koy, Backspace → tablo silinir. (b) Tabloyu kapsayan seçim yap, Delete → tablo silinir. (c) Düz metinde Backspace/Delete → normal karakter silme (bozulmaz).
Expected: üç senaryo da Word benzeri davranır. Gerekirse `setx UDE_TABLEDELLOG 1` ile `%LOCALAPPDATA%\ude-tabledelete.txt` log'unu incele.

- [ ] **Step 5: Rollback kabul**

Run:
```powershell
.\build.ps1 -Only download
.\build.ps1 -NoTableDelete -NoLiveToggle
```
Yeni `.exe`'yi kur, aç.
Expected: toggle yine "yeniden başlat" diyaloğu gösterir (stok davranış); Backspace tabloyu silmez (stok davranış). Yani bayraklar gerçekten kaldırıyor.

- [ ] **Step 6: Branch'i tamamla**

Tüm kabuller geçince `superpowers:finishing-a-development-branch` skill'ini çağır (merge/PR kararı için).

---

## Plan Öz-İncelemesi

**Spec kapsama:**
- LIVETOGGLE portu → Task 4 ✓; TABLEDELETE portu → Task 2+3 ✓
- Paket yeniden adlandırma → Global Constraints + Task 2/4 ✓ (spec'teki `win*` yerine `com.udewin.*`, gerekçeli)
- log() Windows yolu → Task 2 Step 3 ✓
- TableDeleteInstaller idempotent + EDT yok → Task 3 Step 1 ✓
- WPAppManager.main insertBefore + guard → Task 3 Step 2 ✓
- kP.b guard + teşhis → Task 4 Step 2 ✓
- Ad doğrulama ilk adım (görünürlük dahil) → Task 1 ✓
- Bayraklar default-on + switch'ler → Task 5 ✓
- Kabul ölçütleri (build/livetoggle/tabledelete/rollback) → Task 6 ✓
- In-place jar / InputDir artefaktı → Global Constraints + Task 3/4 tazeleme adımları ✓
- PowerShell mekaniği (`;`, tırnak) → Global Constraints + apply scriptleri ✓

**Placeholder taraması:** Yok — tüm yeni dosyalar tam içerikle; verbatim portlar kesin kaynak yolu + kesin düzenlemelerle.

**Tip tutarlılığı:** `install()`/`bind()`/`syncSource()`/`apply()` imzaları Task 2–4 arası tutarlı; paket adları her yerde `com.udewin.tabledelete` / `com.udewin.livetoggle`.
