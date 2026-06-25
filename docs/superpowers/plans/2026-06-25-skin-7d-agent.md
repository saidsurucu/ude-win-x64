# SKIN 7d (Canlı Geçiş + Picker, WinLook agent) — Uygulama Planı

> **REQUIRED SUB-SKILL:** superpowers:executing-plans.

**Goal:** Renk-modu picker + canlı geçiş + koyu-sayfa toggle. Yeni `-javaagent` altyapısı.

**Architecture:** `winlook.WinLook` (standalone agent jar, reflection — MacLook'un krom-dışı alt kümesi) → InputDir'e bundle → package.ps1 `-javaagent`. `macosskin.ModeSwitch` (canlı geçiş, Aqua put'ları çıkarılmış) → editor-app.jar.

## Global Constraints
- Agent paketi `winlook`; paylaşılan mantık `macosskin`. Önce kopyala sonra uyarla.
- WinLook macosskin.*'a DOĞRUDAN ref VERMEZ (Class.forName). Class.forName çökerse frame classloader'a geç.
- Live-switch best-effort + restart fallback. Agent yalnız SKIN=1'de.
- Referans: `$env:TEMP\ude-mac-ref\scripts\skin\`.

---

### Task 0: Dal + jar + referans (feat/skin-7d-agent).

### Task 1: ModeSwitch → editor-app.jar (Aqua çıkar)
- [ ] **Step 1:** `macosskin\ModeSwitch.java`'yı birebir kopyala.
- [ ] **Step 2: Uyarla** — 2 Aqua put satırını SİL:
```
            UIManager.put("ScrollBarUI", "com.apple.laf.AquaScrollBarUI");
            UIManager.put("SliderUI", "com.apple.laf.AquaSliderUI");
```
- [ ] **Step 3:** apply-skin.ps1 derleme listesine `ModeSwitch.java` ekle.
- [ ] **Step 4:** jar'a karşı compile (`javac --release 11 -cp jar macosskin\*.java`) exit 0; `com.apple.laf` referansı KALMAMALI.
- [ ] **Step 5:** Commit.

### Task 2: WinLook agent (MacLook alt kümesi)
- [ ] **Step 1:** `scripts\skin\winlook\WinLook.java` oluştur — MacLook'tan kopyala, sonra:
  - paket `macoslook`→`winlook`, sınıf `MacLook`→`WinLook`.
  - **SİL (krom):** unifyTitleBar, insetMenuBar, syncRibbonTopInset, installMenuBarWatcher, hookTitle, applyTitle, removeMemoryBar + sabitler FWC_MODE/TRAFFIC_INSET/TITLEBAR_TOP_INSET + install()'taki bu 3 çağrı (unifyTitleBar/hookTitle/removeMemoryBar).
  - **KORU:** install (WINDOW_OPENED listener), fixRulerBackground/fixRulerWalk, boldTaskTabs, removeScopeCombo, addDarkPageToggle, addColorModeCombo, findCheckBox, findScopeCombo, findByClassName.
  - **Uyarla:** log yolu `/tmp/macos-look-agent.log`→`%TEMP%\winlook-agent.log` (System.getenv TEMP); debug prop `macoslook.debug`→`winlook.debug`; client property `macoslook.darkpage`/`macoslook.colormode`→`winlook.*`.
- [ ] **Step 2: Standalone compile** (jar'sız, yalnız java.*):
```powershell
& javac --release 11 -encoding UTF-8 -d $tmp scripts\skin\winlook\WinLook.java
```
Expected: exit 0 (tüm UDE/macosskin/Flamingo erişimi reflection → derleme bağımlılığı yok). Kırılırsa: kalan obfuscate/macosskin doğrudan refi reflection'a çevir.
- [ ] **Step 3:** Commit.

### Task 3: winlook.jar üretimi + apply-skin + package wiring
- [ ] **Step 1: apply-skin.ps1 sonuna winlook.jar üretimi ekle** (ModeSwitch jar'a girdikten sonra):
```powershell
# --- winlook agent jar (SKIN ile bundle) ---
$wl = Join-Path $work 'winlook'; New-Dir $wl
& $javac --release 11 -encoding UTF-8 -d $wl (Join-Path $skinDir 'winlook\WinLook.java')
if ($LASTEXITCODE -ne 0) { throw "WinLook derlenemedi" }
$mf = Join-Path $wl 'MANIFEST.MF'
"Premain-Class: winlook.WinLook`r`nAgent-Class: winlook.WinLook`r`n" | Set-Content $mf -Encoding ascii
& $jarTool cfm (Join-Path $InputDir 'winlook.jar') $mf -C $wl winlook
if ($LASTEXITCODE -ne 0) { throw "winlook.jar uretilemedi" }
Write-Ok "winlook.jar uretildi -> InputDir"
```
- [ ] **Step 2: package.ps1 — SKIN=1 iken -javaagent ekle.** `$jpArgs` java-options bloğuna:
```powershell
  if ($env:SKIN -eq '1' -and (Test-Path (Join-Path $InputDir 'winlook.jar'))) {
    $jpArgs += @('--java-options', ('-javaagent:$APPDIR\winlook.jar'))
  }
```
- [ ] **Step 3: Apply + winlook.jar var mı**
```powershell
.\build.ps1 -Only download | Out-Null
$env:ICONS='1'; .\scripts\icons\apply-icons.ps1 -Jar (Join-Path $InputDir $MainJar) | Out-Null
.\scripts\skin\apply-skin.ps1 -Jar (Join-Path $InputDir $MainJar) 2>&1 | Select-String 'winlook.jar|SKIN uygulandi'
Test-Path (Join-Path $InputDir 'winlook.jar')
```
Expected: `winlook.jar uretildi`; True.
- [ ] **Step 4: Premain manifest doğrula**
```powershell
& (Join-Path (Get-Jdk11Home) 'bin\jar.exe') xf (Join-Path $InputDir 'winlook.jar') META-INF/MANIFEST.MF; Get-Content META-INF\MANIFEST.MF; Remove-Item META-INF -Recurse -Force
```
Expected: `Premain-Class: winlook.WinLook`.
- [ ] **Step 5:** Commit.

### Task 4: Build + canlı geçiş + picker + dark-page toggle (GUI)
- [ ] **Step 1: -Icons -Skin build** (`UDE_VERSION='5.4.23'`); cfg'de `-javaagent` satırı:
```powershell
Get-Content (Join-Path $InputDir '..\..\dist\*') -ErrorAction SilentlyContinue  # (veya app cfg'yi kontrol et)
```
Expected: exit 0; winlook.jar app'e dahil.
- [ ] **Step 2: Başlat (winlook.debug=1) + Görünüm sekmesi ss** — Görünüm sekmesinde "Renk modu" combo + "Koyu belge arkaplanı" kutusu görünür. `skin7d-view.png`. Log `%TEMP%\winlook-agent.log`'da "colormode: açılır liste eklendi".
- [ ] **Step 3: Canlı geçiş** — picker'dan Koyu seç → TÜM UI restart'sız koyuya döner (ss `skin7d-live-dark.png`); Açık seç → geri döner. Çökme yok.
- [ ] **Step 4: Dark-page toggle** — "Koyu belge arkaplanı" işaretle → belge anında koyu.
- [ ] **Step 5: Persist + fallback** — Koyu seç, app'i kapat/aç → koyu kalır (pref). Canlı geçiş patlasa bile app açık kalır.
- [ ] **Step 6: SKIN=0 rollback** — agent yok (cfg'de -javaagent yok), normal app.
- [ ] **Step 7: finishing-a-development-branch.**

## Öz-İnceleme
Kapsama: ModeSwitch (Aqua çıkar)→T1; WinLook (krom drop)→T2; winlook.jar+javaagent→T3; picker/live/toggle/persist→T4. Placeholder yok. Tip: WinLook reflection FQN'leri (macosskin.DarkMode/DarkPage/ModeSwitch), Premain-Class winlook.WinLook.
