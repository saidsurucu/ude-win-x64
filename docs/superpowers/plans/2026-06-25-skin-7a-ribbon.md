# SKIN 7a (Şerit + Kanvas) — Uygulama Planı

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Steps use `- [ ]`.

**Goal:** Spike'ın trimli SkinPatch'ini Mac'in tam şerit-sadeleştirme + cetvel/kanvas renk yamalarıyla genişletmek (açık+koyu, build-zamanı). Word widget'ları/DarkPage/agent HARİÇ.

**Architecture:** Mac `SkinPatch.java`'yı BİREBİR kopyala, 3 cerrahi uyarlama yap (Aqua put'ları çıkar, Word install'ları çıkar, IconDarken bloğunu çıkar), Windows jar'ında obfuscate adları doğrula, uygula, çekirdek yamaları zorunlu kıl.

**Tech Stack:** PowerShell, JDK 11 (`javap`/`javac`/`jar`), Javassist 3.30.2-GA, Substance + Flamingo (jar'da).

## Global Constraints

- Paket `macosskin` KORUNUR. JDK 11 `--release 11 -encoding UTF-8`. Classpath `;`, yollar tırnaklı.
- **Önce kopyala, sonra uyarla:** Mac SkinPatch birebir; yalnız 3 uyarlama + Windows-kırılması.
- **Çekirdek SERT** (verify'de zorunlu — log'da "atlandı" çıkarsa task BAŞARISIZ): `FlamingoUtilities.getBorderColor`, `BasicRibbonUI.paintTaskArea`, `BasicCommandButtonUI` (2 overload), `BasicRibbonBandUI` (title/bg + RoundBorder), orb `BasicRibbonApplicationMenuButtonUI`, `BasicRibbonTaskToggleButtonUI`.
- **Çevresel BEST-EFFORT:** orb menü ($8/$9/$6/$7), rich tooltip, BasicPopupPanelUI, BasicCommandButtonPanelUI, TaskbarPanel, focus `a.b.a.a.t`, ruler `eV`, `an`.
- EDT nötrleştirme + wp.p.E clinit KORUNUR (Mac SkinPatch'te zaten var). SKIN=1 bayrağı.
- Referans: `$env:TEMP\ude-mac-ref\scripts\skin\SkinPatch.java`.

---

### Task 0: Dal + jar + referans
- [ ] **Step 1:** `git branch --show-current` → `feat/skin-7a-ribbon`.
- [ ] **Step 2:**
```powershell
. .\scripts\common.ps1
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
if (-not (Test-Path (Join-Path $InputDir $MainJar))) { .\build.ps1 -Only download | Out-Null }
$MAC="$env:TEMP\ude-mac-ref"; if (-not (Test-Path "$MAC\scripts\skin\SkinPatch.java")) { git clone --depth 1 https://github.com/saidsurucu/ude-mac-arm64 $MAC | Out-Null }
"jar+ref: $((Test-Path (Join-Path $InputDir $MainJar)) -and (Test-Path "$MAC\scripts\skin\SkinPatch.java"))"
```
Expected: `jar+ref: True`

---

### Task 1: Obfuscate ad doğrulama (Windows jar)

**Files:** Create `scripts\skin\verify-skin-names.ps1`

- [ ] **Step 1: Doğrulama scriptini yaz**

`scripts\skin\verify-skin-names.ps1`:
```powershell
. "$PSScriptRoot\..\common.ps1"
$jar = Join-Path $InputDir $MainJar
$javap = Join-Path (Get-Jdk11Home) 'bin\javap.exe'
function S($c,$pat){ Write-Host "== $c" -ForegroundColor Cyan; & $javap -p -classpath "$jar" $c 2>&1 | Select-String $pat }
$P='tr.com.havelsan.uyap.system.editor.common.'
S ($P+'an') 'class|void|public'                          # an: wp.p.E writer (FieldAccess)
S ($P+'gui.eV') '\b(a|b|c)\(\)|setColor_border|Color (d|e);'  # ruler getters + setter + statik d/e
S 'tr.gov.uyap.system.a.b.a.a.t' 'focusGained'           # focus listener
S 'org.pushingpixels.flamingo.internal.utils.FlamingoUtilities' 'getBorderColor'
S 'org.pushingpixels.flamingo.internal.ui.ribbon.BasicRibbonUI' 'paintTaskArea'
S 'org.pushingpixels.flamingo.internal.ui.common.BasicCommandButtonUI' 'paintButtonBackground'
S 'org.pushingpixels.flamingo.internal.ui.ribbon.BasicRibbonTaskToggleButtonUI' 'paintButtonBackground'
S 'org.pushingpixels.flamingo.internal.ui.ribbon.appmenu.BasicRibbonApplicationMenuButtonUI' 'paintButtonBackground'
S 'org.pushingpixels.flamingo.internal.ui.common.BasicRichTooltipPanelUI' 'paintBackground'
```

- [ ] **Step 2: Çalıştır + çekirdek hedefleri doğrula**

Run: `.\scripts\skin\verify-skin-names.ps1`
Expected: ÇEKİRDEK hedefler var: `getBorderColor()`, `paintTaskArea(Graphics,int,int,int,int)`, `BasicCommandButtonUI` İKİ `paintButtonBackground` overload (`(G,R)` ve `(G,R,ButtonModel[])`), `BasicRibbonTaskToggleButtonUI.paintButtonBackground`, orb `paintButtonBackground`. ÇEVRESEL (eV a/b/c, setColor_border, d/e, an, focus, tooltip) — not et; eksikse o blok best-effort atlanır. Çekirdek eksikse: DUR, araştır.

- [ ] **Step 3: Commit**
```powershell
git add scripts/skin/verify-skin-names.ps1
git commit -m "SKIN 7a: obfuscate ad dogrulama scripti"
```

---

### Task 2: SkinPatch'i tam Mac sürümüne genişlet (kopyala + 3 uyarlama)

**Files:** Modify `scripts\skin\SkinPatch.java`

- [ ] **Step 1: Mac SkinPatch'i birebir kopyala**
```powershell
$utf8 = New-Object System.Text.UTF8Encoding $false
$c = [System.IO.File]::ReadAllText("$env:TEMP\ude-mac-ref\scripts\skin\SkinPatch.java")
[System.IO.File]::WriteAllText("$PWD\scripts\skin\SkinPatch.java", $c, $utf8)
"kopyalandi: $((Get-Content scripts\skin\SkinPatch.java | Measure-Object -Line).Lines) satir"
```

- [ ] **Step 2: Uyarlama 1 — setSkin(String) sarmasından Aqua put + Word install satırlarını sil**

`scripts\skin\SkinPatch.java`, setSkin wrap içinde şu satırları SİL (string olarak):
```
          + "      javax.swing.UIManager.put(\"ScrollBarUI\", \"com.apple.laf.AquaScrollBarUI\");"
          + "      javax.swing.UIManager.put(\"SliderUI\", \"com.apple.laf.AquaSliderUI\");"
          + "      macosskin.WordTooltip.install();"
          + "      macosskin.WordCombo.install();"
          + "      macosskin.WordCheck.install();"
          + "      macosskin.WordButton.install();"
          + "      macosskin.WordTabs.install();"
          + "      macosskin.WordField.install();"
```
(Skin install + font policy KALIR.)

- [ ] **Step 3: Uyarlama 2 — aF.run() sarmasından Aqua put + Word install satırlarını sil**

aF.run wrap içinde aynı 8 satırı SİL:
```
          + "        javax.swing.UIManager.put(\"ScrollBarUI\", \"com.apple.laf.AquaScrollBarUI\");"
          + "      javax.swing.UIManager.put(\"SliderUI\", \"com.apple.laf.AquaSliderUI\");"
          + "        macosskin.WordTooltip.install();"
          + "        macosskin.WordCombo.install();"
          + "      macosskin.WordCheck.install();"
          + "      macosskin.WordButton.install();"
          + "      macosskin.WordTabs.install();"
          + "      macosskin.WordField.install();"
```

- [ ] **Step 4: Uyarlama 3 — IconDarken/ModeAwareImage Utils bloğunu sil (7c)**

`try { ... macosskin.IconDarken.apply ... macosskin.ModeAwareImage ... }` bloğunu (yorum: "İkon mod-duyarlılığı" / "koyu mod ikon aydınlatma") TAMAMEN SİL. Bu blok `Utils.b/a` + `IconDarken.scaleIcon` + `ModeAwareImage` referansları içerir (hepsi 7c). Silindikten sonra dosyada `IconDarken`, `ModeAwareImage`, `Word` referansı KALMAMALI:
```powershell
Select-String -Path scripts\skin\SkinPatch.java -Pattern 'IconDarken|ModeAwareImage|WordTooltip|WordCombo|WordCheck|WordButton|WordTabs|WordField|com\.apple\.laf'
```
Expected: BOŞ (hiç eşleşme yok).

- [ ] **Step 5: Taze jar'a uygula + çekirdek zorunluluğu**
```powershell
.\build.ps1 -Only download | Out-Null
. .\scripts\common.ps1
$out = .\scripts\skin\apply-skin.ps1 -Jar (Join-Path $InputDir $MainJar) 2>&1
$out
$core = 'getBorderColor|sekme alan|komut buton|band|Orb arka|secili sekme alt'
$skipped = $out | Select-String 'UYARI' | Select-String $core
if ($skipped) { Write-Host "CEKIRDEK ATLANDI - DUR:" -ForegroundColor Red; $skipped }
else { Write-Host "CEKIRDEK TAMAM" -ForegroundColor Green }
```
Expected: `SKIN uygulandi`; çekirdek patch satırları "uygulandı" yazar; `CEKIRDEK TAMAM`. Eğer çekirdek "atlandı" → DUR, Windows imzası farklı, araştır/uyarla.

- [ ] **Step 6: Commit**
```powershell
git add scripts/skin/SkinPatch.java
git commit -m "SKIN 7a: tam Flamingo sadelestirme + cetvel/an (Mac kopya; Aqua/Word/IconDarken cikarildi)"
```

---

### Task 3: Build + açık/koyu görsel + PDF + teal-gone

**Files:** (yok)

- [ ] **Step 1: -Skin tam build**
```powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
.\build.ps1 -Only download | Out-Null
$env:UDE_VERSION='5.4.20'; .\build.ps1 -Skin; "exit: $LASTEXITCODE"; $env:UDE_VERSION=$null
```
Expected: exit 0; `.exe` üretilir; çekirdek + çevresel patch satırları.

- [ ] **Step 2: Açık modda başlat + ss (teal gitti, şerit düz)**
```powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
. .\scripts\common.ps1; $jar=Join-Path $InputDir $MainJar; $env:JAVA_TOOL_OPTIONS=$null
$log=Join-Path $env:TEMP 'skinpatch-trace.log'; if(Test-Path $log){Remove-Item $log -Force}
Start-Process (Join-Path (Get-Jdk11Home) 'bin\java.exe') -ArgumentList @('-Dmacosskin.debug=1','-cp',"`"$jar`"",'tr.com.havelsan.uyap.system.editor.common.WPAppManager','getNewWPInstance','EDITOR_TYPE_DOCUMENT')
Start-Sleep 6
# ss al (CopyFromScreen) -> scratchpad\skin7a-light.png ; UDE penceresini AppActivate ile one getir
```
Görsel/ss kontrol: grup kutuları YOK, orb parıltısı YOK, buton hover düz, sekme-satırı çizgisi YOK, **kanvas/cetvel nötr gri (teal GİTTİ)**. trace'te `HATA` yok.

- [ ] **Step 3: Koyu modda başlat + ss**
```powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
$jdk=Get-Jdk11Home
'public class SD{public static void main(String[] a){macosskin.DarkMode.setMode("dark");}}' | Set-Content "$env:TEMP\skintest\SD.java" -Encoding ascii
& (Join-Path $jdk 'bin\javac.exe') -cp "$env:TEMP\skintest" -d "$env:TEMP\skintest" "$env:TEMP\skintest\SD.java"; & (Join-Path $jdk 'bin\java.exe') -cp "$env:TEMP\skintest" SD
Start-Process (Join-Path $jdk 'bin\java.exe') -ArgumentList @('-Dmacosskin.debug=1','-cp',"`"$jar`"",'tr.com.havelsan.uyap.system.editor.common.WPAppManager','getNewWPInstance','EDITOR_TYPE_DOCUMENT')
Start-Sleep 6  # ss -> skin7a-dark.png ; sonra setMode("system")
```
Görsel/ss: şerit/kanvas koyu gri, kontrast okunur, çift-beyaz çerçeve yok.

- [ ] **Step 4: PDF export + missing-class taraması**

Elle/ss: belgeye yaz → "PDF Olarak Kaydet" → geçerli PDF. Konsol stderr'inde `ClassNotFound`/`NoClassDef`/Substance exception YOK (Aqua çıkarıldı). `colorMode` pref'ini `system`'e geri al.

- [ ] **Step 5: Branch'i tamamla**

Kabuller geçince `superpowers:finishing-a-development-branch`.

---

## Plan Öz-İncelemesi

**Spec kapsama:** Çekirdek Flamingo (sert, verify grep) → Task 2 Step 5 ✓; çevresel best-effort → kopya verbatim ✓; ruler/an checkpoint C → Task 1+2 ✓; 3 uyarlama (Aqua/Word/IconDarken) → Task 2 Step 2-4 ✓; obfuscate doğrulama → Task 1 ✓; açık+koyu+PDF+teal → Task 3 ✓; paket macosskin korunur → Constraints ✓.
**Placeholder:** Yok — silinecek satırlar tam string; verify grep desenleri somut.
**Tip tutarlılığı:** macosskin.DarkMode referansları her blokta; iki command-button overload tek CtClass + tek writeClass (Mac verbatim).
