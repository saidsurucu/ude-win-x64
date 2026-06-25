# ICONS Material → Fluent — Uygulama Planı

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Windows yapısındaki Material ikon setini Mac portundaki Fluent setiyle değiştirmek + daha güçlü retina-keskinlik patcher'ını getirmek.

**Architecture:** Fluent PNG override'ları (commit'li) jar'a `jar uf` ile enjekte edilir; `IconLoaderPatch` (Javassist) Utils + Flamingo ikon yollarını retina için yamalar. **Yöntem: önce Mac'ten BİREBİR kopyala, sonra yalnız Windows'ta KIRILAN yeri uyarla** (doğrulama-güdümlü; ön-değişiklik yok).

**Tech Stack:** PowerShell, JDK 11 (`javap`, `javac`, `jar`), Javassist 3.30.2-GA, Java Swing/Flamingo (jar'da gömülü).

## Global Constraints

- **Önce kopyala, sonra uyarla:** Mac dosyaları (Fluent PNG'ler, `IconLoaderPatch.java`, `mapping.tsv`, `generate.py`) BİREBİR kopyalanır; Windows uyarlaması yalnız doğrulama bir kırılma gösterirse yapılır.
- JDK 11: `javac --release 11`. Classpath ayıracı `;`; yollar tırnaklı.
- Javassist gövde string'lerinde `//` yorum YASAK; sınıf başına tek `writeClass`.
- Bayrak DEĞİŞMEZ: `-Icons` / `$env:ICONS=1` (manuel opt-in; `kur.ps1` varsayılan açık).
- `apply-icons.ps1` mantığı DEĞİŞMEZ (patcher'ın hangi metodu yamaladığından bağımsız: PNG enjekte → IconLoaderPatch derle+çalıştır → yamalı sınıfları enjekte).
- IconDarken/ModeAwareImage (koyu ikon) HARİÇ — SKIN #7.
- Referans: `$env:TEMP\ude-mac-ref`.

---

### Task 0: Dal + jar + referans hazır

**Files:** (yok)

- [ ] **Step 1: Doğru daldayız**

Run: `git branch --show-current`
Expected: `feat/icons-fluent`

- [ ] **Step 2: jar + referans mevcut**

Run:
```powershell
. .\scripts\common.ps1
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
if (-not (Test-Path (Join-Path $InputDir $MainJar))) { .\build.ps1 -Only download | Out-Null }
$MAC = "$env:TEMP\ude-mac-ref"
if (-not (Test-Path "$MAC\scripts\icons\IconLoaderPatch.java")) { git clone --depth 1 https://github.com/saidsurucu/ude-mac-arm64 $MAC | Out-Null }
"jar: $(Test-Path (Join-Path $InputDir $MainJar)); ref: $(Test-Path "$MAC\scripts\icons\fluent\mapping.tsv")"
```
Expected: `jar: True; ref: True`

---

### Task 1: Asset parite kontrol scripti

**Files:**
- Create: `scripts\icons\check-icons.ps1`

**Interfaces:**
- Produces: `check-icons.ps1` — override dizinini jar resource adlarıyla karşılaştırır (yetim/eksik/@2x).

- [ ] **Step 1: check-icons.ps1 yaz**

`scripts\icons\check-icons.ps1`:
```powershell
# check-icons.ps1 - override PNG'lerini jar resource adlariyla karsilastir.
# Yetim (jar'da olmayan override), @2x eksik, jar'da override'siz (KEEP/orijinal) raporu.
param(
  [string]$Jar,
  [string]$OverridesResDir = (Join-Path $PSScriptRoot 'overrides\resources')
)
. "$PSScriptRoot\..\common.ps1"
if (-not $Jar) { $Jar = Join-Path $InputDir $MainJar }
$jdk = Get-Jdk11Home

# jar icindeki resources/*.png adlari (non-@2x)
$jarPng = & (Join-Path $jdk 'bin\jar.exe') tf $Jar |
  Where-Object { $_ -match '(^|/)resources/[^/]+\.png$' -and $_ -notmatch '@2x\.png$' } |
  ForEach-Object { ($_ -split '/')[-1] } | Sort-Object -Unique
$jarSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$jarPng)

# override resources *.png (non-@2x)
$ov = Get-ChildItem $OverridesResDir -Filter *.png | Where-Object { $_.Name -notmatch '@2x\.png$' } | Select-Object -ExpandProperty Name | Sort-Object
$ovSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$ov)

# @2x istisnalari (compose ikonlar @2x uretmez)
$noTwoX = @('search.png')

$orphan = $ov | Where-Object { -not $jarSet.Contains($_) }
$missing2x = $ov | Where-Object { ($noTwoX -notcontains $_) -and -not (Test-Path (Join-Path $OverridesResDir ($_ -replace '\.png$','@2x.png'))) }
$notOverridden = $jarPng | Where-Object { -not $ovSet.Contains($_) }

Write-Host "=== override sayisi: $($ov.Count) | jar resources png: $($jarPng.Count) ===" -ForegroundColor Cyan
Write-Host "--- YETIM (override var, jar'da YOK -> bosa churn): $($orphan.Count) ---" -ForegroundColor Yellow
$orphan
Write-Host "--- @2x EKSIK (search haric): $($missing2x.Count) ---" -ForegroundColor Yellow
$missing2x
Write-Host "--- jar'da override'SIZ (KEEP/orijinal; bilgi): $($notOverridden.Count) ---" -ForegroundColor DarkGray
if ($orphan.Count -eq 0 -and $missing2x.Count -eq 0) { Write-Host "PARITE TEMIZ" -ForegroundColor Green }
else { Write-Host "PARITE UYARISI (yukariyi incele)" -ForegroundColor Red }
```

- [ ] **Step 2: Mevcut Material set'i jar'a karşı çalıştır (baz çizgi)**

Run: `.\scripts\icons\check-icons.ps1`
Expected: çalışır; mevcut Material seti için yetim/@2x raporu (baz çizgi — şu anki durumun fotoğrafı).

- [ ] **Step 3: Commit**

```powershell
git add scripts/icons/check-icons.ps1
git commit -m "ICONS: asset parite kontrol scripti (check-icons.ps1)"
```

---

### Task 2: Fluent asset'lerini kopyala (Material'i değiştir) + referans tooling

**Files:**
- Delete: `scripts\icons\overrides\resources\*.png` (647 Material)
- Create: `scripts\icons\overrides\resources\*.png` (653 Fluent, Mac'ten birebir)
- Create: `scripts\icons\fluent\mapping.tsv`, `scripts\icons\fluent\generate.py` (referans)

- [ ] **Step 1: Material→Fluent ad farkını gör (bilgi)**

Run:
```powershell
$MAC = "$env:TEMP\ude-mac-ref\scripts\icons\overrides\resources"
$cur = Get-ChildItem scripts\icons\overrides\resources -Filter *.png | Select-Object -ExpandProperty Name | Sort-Object
$new = Get-ChildItem $MAC -Filter *.png | Select-Object -ExpandProperty Name | Sort-Object
"=== sadece Material'de (Fluent'te YOK -> override kalkacak) ==="
(Compare-Object $cur $new | Where-Object SideIndicator -eq '<=').InputObject
"=== sadece Fluent'te (yeni override) ==="
(Compare-Object $cur $new | Where-Object SideIndicator -eq '=>').InputObject | Measure-Object | % Count
```
Expected: farkları listeler (commit öncesi bilinçli onay — Material'de olup Fluent'te olmayan adlar jar orijinaline döner).

- [ ] **Step 2: Material'i sil, Fluent'i birebir kopyala**

Run:
```powershell
$MAC = "$env:TEMP\ude-mac-ref\scripts\icons\overrides\resources"
Remove-Item scripts\icons\overrides\resources\*.png -Force
Copy-Item "$MAC\*.png" scripts\icons\overrides\resources\
"yeni png: $((Get-ChildItem scripts\icons\overrides\resources -Filter *.png).Count)"
# referans tooling
New-Item -ItemType Directory -Force scripts\icons\fluent | Out-Null
Copy-Item "$env:TEMP\ude-mac-ref\scripts\icons\fluent\mapping.tsv" scripts\icons\fluent\
Copy-Item "$env:TEMP\ude-mac-ref\scripts\icons\fluent\generate.py" scripts\icons\fluent\
```
Expected: `yeni png: 653`

- [ ] **Step 3: Parite kontrolü (Fluent vs jar) — KRİTİK**

Run: `.\scripts\icons\check-icons.ps1`
Expected: `PARITE TEMIZ` (YETIM 0, @2x EKSIK 0). Yetim/eksik çıkarsa: hangi ikon → karar (Mac'te de öyleyse kabul; değilse araştır).

- [ ] **Step 4: Commit (binary churn)**

```powershell
git add scripts/icons/overrides/resources scripts/icons/fluent
git commit -m "ICONS: Material PNG'leri Fluent ile degistir (birebir kopya) + fluent/ referans tooling"
```

---

### Task 3: IconLoaderPatch — önce kopyala, sonra (gerekirse) uyarla

**Files:**
- Modify: `scripts\icons\IconLoaderPatch.java` (Mac sürümüyle değiştir)

**Interfaces:**
- Consumes: jar (Utils.b/Utils.a + Flamingo sınıfları).

- [ ] **Step 1: Patcher imzalarını Windows jar'ında doğrula**

Run:
```powershell
. .\scripts\common.ps1
$jar = Join-Path $InputDir $MainJar
$javap = Join-Path (Get-Jdk11Home) 'bin\javap.exe'
"=== Utils.b / Utils.a overload'lari ==="
& $javap -p -classpath "$jar" tr.com.havelsan.uyap.system.editor.common.Utils 2>&1 | Select-String 'ImageIcon (a|b)\('
"=== Flamingo ikon siniflari ==="
& $javap -classpath "$jar" org.pushingpixels.flamingo.api.common.icon.ImageWrapperIcon 2>&1 | Select-String 'paintIcon'
& $javap -classpath "$jar" org.pushingpixels.flamingo.api.common.icon.FilteredResizableIcon 2>&1 | Select-String 'paintIcon'
& $javap -classpath "$jar" com.alee.global.StyleConstants 2>&1 | Select-String 'disabledIconsTransparency'
```
Expected (not et): `b(java.lang.String)`→ImageIcon VAR (madde 1, şart); `a(ImageIcon,int,int)` VAR mı?; ImageWrapperIcon/FilteredResizableIcon `paintIcon(Component,Graphics,int,int)` VAR mı?; StyleConstants `disabledIconsTransparency` VAR mı? Eksik olanı Step 4'te not et.

- [ ] **Step 2: Mac IconLoaderPatch'i BİREBİR kopyala (Win'inkini değiştir)**

Run:
```powershell
$utf8 = New-Object System.Text.UTF8Encoding $false
$c = [System.IO.File]::ReadAllText("$env:TEMP\ude-mac-ref\scripts\icons\IconLoaderPatch.java")
[System.IO.File]::WriteAllText("scripts\icons\IconLoaderPatch.java", $c, $utf8)
"kopyalandi"
```
(Mac patcher: Utils.b multi-res [şart] + Utils.a scale + StyleConstants 0.38 [best-effort] + Flamingo ImageWrapperIcon bicubic [best-effort] + FilteredResizableIcon alpha 0.38 [best-effort].)

- [ ] **Step 3: Birebir sürümü jar'a karşı çalıştır**

Run:
```powershell
.\build.ps1 -Only download | Out-Null
. .\scripts\common.ps1
$env:ICONS = '1'
.\scripts\icons\apply-icons.ps1 -Jar (Join-Path $InputDir $MainJar)
"exit: $LASTEXITCODE"
```
Expected: çıktıda `[IconLoaderPatch] Utils.b multi-res + a(ImageIcon,int,int) yamaları yazıldı.` + best-effort satırlar (uygulandı VEYA atlandı). Exit 0.

- [ ] **Step 4: Windows uyarlaması — YALNIZ kırılan yer**

Karar:
- **Birebir sürüm exit 0 + Utils.b satırı yazıldıysa → UYARLAMA GEREKMEZ.** Step 5'e geç.
- **Eğer `Utils.a(ImageIcon,int,int)` overload Windows jar'ında YOKSA** (`utils.getMethod("a",...)` fırlatır → tüm patch çöker, Utils.b dahil): `IconLoaderPatch.java`'da Utils.a (madde 1b) bloğunu kendi try/catch'ine al ki kritik Utils.b yine uygulansın. Mac kodunda madde 1b, madde 1 ile aynı `writeClass(utils)` altında ve try/catch'siz; şu yapıya getir:
```java
        // --- 1) Utils.b multi-resolution (KRITIK) ---
        CtClass utils = pool.get("tr.com.havelsan.uyap.system.editor.common.Utils");
        CtMethod b = utils.getMethod("b", "(Ljava/lang/String;)Ljavax/swing/ImageIcon;");
        b.insertAfter( /* ... mevcut govde ... */ );
        // --- 1b) Utils.a scale (best-effort: overload yoksa atla, Utils.b yine yazilir) ---
        try {
            CtMethod scale = utils.getMethod("a", "(Ljavax/swing/ImageIcon;II)Ljavax/swing/ImageIcon;");
            scale.insertBefore( /* ... mevcut govde ... */ );
            System.out.println("[IconLoaderPatch] Utils.a scale yamasi uygulandi.");
        } catch (Throwable t) {
            System.out.println("[IconLoaderPatch] UYARI: Utils.a scale atlandi: " + t);
        }
        writeClass(utils, outDir);
```
  Sonra Step 3'ü tekrar çalıştır → exit 0 + Utils.b yazıldı.
- **Eğer bir Flamingo/StyleConstants sınıfı/imzası farklıysa:** o zaten best-effort try/catch'te → "atlandı" yazar, build çökmez; not et (findings/commit mesajı).

- [ ] **Step 5: Commit**

```powershell
git add scripts/icons/IconLoaderPatch.java
git commit -m "ICONS: Fluent IconLoaderPatch (Mac'ten kopya; Windows uyarlamasi: <yok|Utils.a best-effort>)"
```

---

### Task 4: Tam build (-Icons) + asset hijyeni + SKIN no-overlap

**Files:** (yok — doğrulama)

- [ ] **Step 1: -Icons ile tam build (stderr yönlendirmesi YOK)**

Run:
```powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
.\build.ps1 -Only download | Out-Null
.\build.ps1 -Icons
"LASTEXITCODE: $LASTEXITCODE"
Get-ChildItem dist\*.exe | Select-Object Name, @{n='MB';e={[math]::Round($_.Length/1MB,1)}}, LastWriteTime
```
Expected: patch fazında `ICONS=1 -> modern ikonlar uygulaniyor`; `.exe` üretilir, exit 0. (Eğer dist kilitliyse `UDE_VERSION` ile farklı ad.)

- [ ] **Step 2: Asset hijyeni — jar'da çift entry yok**

Run:
```powershell
. .\scripts\common.ps1
$jar = Join-Path $InputDir $MainJar
$entries = & (Join-Path (Get-Jdk11Home) 'bin\jar.exe') tf $jar | Where-Object { $_ -match 'resources/.+\.png$' }
$dups = $entries | Group-Object | Where-Object Count -gt 1
"toplam png entry: $($entries.Count); CIFT entry: $($dups.Count)"
$dups | Select-Object Name, Count
```
Expected: `CIFT entry: 0`.

- [ ] **Step 3: SKIN no-overlap doğrulama**

Run:
```powershell
"ICONS yamaladigi siniflar: Utils, StyleConstants, flamingo.api.common.icon.ImageWrapperIcon, FilteredResizableIcon"
"SKIN yamaladigi siniflar: SubstanceLookAndFeel, aF, wp.p, SubstanceCoreUtilities, LafWidgetUtilities, flamingo.internal.ui.ribbon.BasicRibbonBandUI(+RoundBorder)"
"-> ORTAK SINIF YOK (flamingo.api.common.icon != flamingo.internal.ui.ribbon). patch.ps1 sirasi: ICONS once, SKIN sonra."
```
Expected: ortak yamalı sınıf olmadığı teyidi (statik analiz; iki patcher farklı paketlerdeki sınıfları yamalar).

- [ ] **Step 4: Commit (varsa)**

Bu task kod değiştirmez (yalnız doğrulama). Değişiklik yoksa commit yok.

---

### Task 5: HiDPI + görsel kabul (elle GUI)

**Files:** (yok)

- [ ] **Step 1: %100'de başlat, ikonları gözle + ss**

Run:
```powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
. .\scripts\common.ps1
$jar = Join-Path $InputDir $MainJar
$env:JAVA_TOOL_OPTIONS = $null
Start-Process -FilePath (Join-Path (Get-Jdk11Home) 'bin\java.exe') -ArgumentList @('-Dsun.java2d.uiScale=1','-cp',"`"$jar`"",'tr.com.havelsan.uyap.system.editor.common.WPAppManager','getNewWPInstance','EDITOR_TYPE_DOCUMENT')
Start-Sleep -Seconds 5
```
Elle/ss: araç çubuğu + şerit ikonları **Fluent** mi (gri gövde, renkli vurgular: sil kırmızı, ekle yeşil); keskin mi (bloklu değil); disabled ikonlar soluk-okunur mu.

- [ ] **Step 2: %200'de başlat — @2x seçimi**

Run (uiScale=2):
```powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Process -FilePath (Join-Path (Get-Jdk11Home) 'bin\java.exe') -ArgumentList @('-Dsun.java2d.uiScale=2','-cp',"`"$jar`"",'tr.com.havelsan.uyap.system.editor.common.WPAppManager','getNewWPInstance','EDITOR_TYPE_DOCUMENT')
Start-Sleep -Seconds 5
```
Elle/ss: ikonlar **keskin** (1x büyütme bulanıklığı YOK → @2x seçiliyor); kırpılma yok.

- [ ] **Step 3: %125 ve %150 (fraksiyonel) — yumuşama kontrolü**

Run (uiScale=1.25, sonra 1.5):
```powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Process -FilePath (Join-Path (Get-Jdk11Home) 'bin\java.exe') -ArgumentList @('-Dsun.java2d.uiScale=1.25','-cp',"`"$jar`"",'tr.com.havelsan.uyap.system.editor.common.WPAppManager','getNewWPInstance','EDITOR_TYPE_DOCUMENT')
Start-Sleep -Seconds 5
```
Elle/ss: fraksiyonel ölçekte ikonlar kabul edilebilir keskinlikte (Windows burada yumuşatır; bloklu/aşırı bulanık olmamalı).

- [ ] **Step 4: Rollback — -Icons olmadan orijinal ikonlar**

Run:
```powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
.\build.ps1 -Only download | Out-Null
.\build.ps1 -Only patch   # ICONS=0 (varsayilan)
Start-Process -FilePath (Join-Path (Get-Jdk11Home) 'bin\java.exe') -ArgumentList @('-cp',"`"$(Join-Path $InputDir $MainJar)`"",'tr.com.havelsan.uyap.system.editor.common.WPAppManager','getNewWPInstance','EDITOR_TYPE_DOCUMENT')
```
Elle: orijinal UDE ikonları görünür (Fluent yok) → bayrak gerçekten kaldırıyor.

- [ ] **Step 5: Branch'i tamamla**

Tüm kabuller geçince `superpowers:finishing-a-development-branch` çağır.

---

## Plan Öz-İncelemesi

**Spec kapsama:**
- Asset değişimi (Material→Fluent birebir) → Task 2 ✓
- Patcher yükseltme (önce kopyala, sonra uyarla) → Task 3 ✓
- Referans tooling (mapping.tsv/generate.py) → Task 2 Step 2 ✓
- Kapsam sınırı (IconDarken hariç) → Global Constraints ✓
- Doğrulama A (parite: yetim/@2x/jar) → Task 1 + Task 2 Step 3 ✓
- Doğrulama B (patcher imza) → Task 3 Step 1 ✓
- Doğrulama C (HiDPI 1/1.25/1.5/2) → Task 5 ✓
- Doğrulama D (hijyen: çift entry) → Task 4 Step 2 ✓
- Doğrulama E (SKIN no-overlap + sıra) → Task 4 Step 3 ✓
- Bayrak değişmez + rollback → Task 4/5 ✓

**Placeholder taraması:** Task 3 Step 4 koşullu uyarlama gerçek kod gösterir; commit mesajındaki `<yok|Utils.a best-effort>` doğrulama sonucuyla doldurulur (placeholder değil, karar çıktısı).

**Tip tutarlılığı:** `check-icons.ps1` jar/override karşılaştırması; IconLoaderPatch hedef imzaları Task 3 Step 1'de doğrulanır; ICONS↔SKIN sınıf kümeleri Task 4 Step 3'te ayrık.
