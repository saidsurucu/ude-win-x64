# SKIN 7b (Word Widget'ları) — Uygulama Planı

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Steps use `- [ ]`.

**Goal:** Word-stili düz widget delegate'lerini (buton/sekme/combo/check/field/tooltip + popup/menü/etched) ekle. Diyaloglar düzlenir.

**Architecture:** 9 PURE yardımcı sınıfı Mac'ten birebir kopyala (obfuscate refs yok), 6 install() çağrısını SkinPatch wrap'lerine geri ekle, apply-skin.ps1 derleme listesine ekle. 7a SkinPatch'teki PopupRemap/MenuMarks/FlatEtchedBorder blokları otomatik aktifleşir.

**Tech Stack:** PowerShell, JDK 11, Javassist, Substance/Flamingo (jar'da).

## Global Constraints

- Paket `macosskin`. `javac --release 11 -encoding UTF-8`. Önce kopyala sonra uyarla.
- install() = `UIManager.put` (idempotent; sert guard YOK — 7d canlı geçiş için).
- Doğrulama: runtime delegate kanıtı (UIManager.get) + gerçek-app diyalog ss.
- Referans: `$env:TEMP\ude-mac-ref\scripts\skin\macosskin\`.

---

### Task 0: Dal + jar + referans
- [ ] **Step 1:** `git branch --show-current` → `feat/skin-7b-widgets`.
- [ ] **Step 2:**
```powershell
. .\scripts\common.ps1
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
if (-not (Test-Path (Join-Path $InputDir $MainJar))) { .\build.ps1 -Only download | Out-Null }
$MAC="$env:TEMP\ude-mac-ref"; if (-not (Test-Path "$MAC\scripts\skin\macosskin\WordButton.java")) { git clone --depth 1 https://github.com/saidsurucu/ude-mac-arm64 $MAC | Out-Null }
"ok: $((Test-Path (Join-Path $InputDir $MainJar)) -and (Test-Path "$MAC\scripts\skin\macosskin\WordButton.java"))"
```

---

### Task 1: 9 Word sınıfını kopyala + derleme listesine ekle + wrap install'ları

**Files:** Create 9 `scripts\skin\macosskin\Word*.java`+PopupRemap/MenuMarks/FlatEtchedBorder; Modify `apply-skin.ps1`, `SkinPatch.java`

- [ ] **Step 1: 9 sınıfı birebir kopyala**
```powershell
$MAC="$env:TEMP\ude-mac-ref\scripts\skin\macosskin"; $dst="scripts\skin\macosskin"
$utf8=New-Object System.Text.UTF8Encoding $false
foreach($f in 'WordButton.java','WordTabs.java','WordCombo.java','WordCheck.java','WordField.java','WordTooltip.java','PopupRemap.java','MenuMarks.java','FlatEtchedBorder.java'){
  [System.IO.File]::WriteAllText((Join-Path $dst $f), [System.IO.File]::ReadAllText((Join-Path $MAC $f)), $utf8)
}
"=== obfuscate ref kontrol (bos olmali) ==="
Select-String -Path "$dst\Word*.java","$dst\PopupRemap.java","$dst\MenuMarks.java","$dst\FlatEtchedBorder.java" -Pattern 'tr\.com\.havelsan|tr\.gov\.uyap'
```
Expected: BOŞ (obfuscate ref yok).

- [ ] **Step 2: apply-skin.ps1 javac listesine 9 kaynağı ekle**

`apply-skin.ps1`'de helper derleme `& $javac ...` çağrısına 9 dosyayı ekle (DarkMode/FlatUdeSkin/... yanına): `macosskin\WordButton.java` ... `macosskin\FlatEtchedBorder.java`.

- [ ] **Step 3: 6 install() çağrısını iki wrap'e geri ekle**

`SkinPatch.java` setSkin(String) wrap'inde `boolean __ok = ...setSkin(__skin);` SONRASI, font policy ÖNCESİ ekle:
```
          + "      macosskin.WordTooltip.install();"
          + "      macosskin.WordCombo.install();"
          + "      macosskin.WordCheck.install();"
          + "      macosskin.WordButton.install();"
          + "      macosskin.WordTabs.install();"
          + "      macosskin.WordField.install();"
```
Aynısını aF.run() wrap'inde `...setSkin(__skin);` sonrasına ekle.

- [ ] **Step 4: Yardımcılar jar'a karşı derleniyor mu (compile-first)**
```powershell
. .\scripts\common.ps1; $jar=Join-Path $InputDir $MainJar; $jdk=Get-Jdk11Home
Remove-Item "$env:TEMP\wtest" -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force "$env:TEMP\wtest" | Out-Null
& (Join-Path $jdk 'bin\javac.exe') --release 11 -encoding UTF-8 -cp "$jar" -d "$env:TEMP\wtest" (Get-ChildItem scripts\skin\macosskin\*.java | % FullName)
"javac exit: $LASTEXITCODE"
```
Expected: exit 0. Kırılırsa: hatayı oku, copy-then-adapt ile düzelt (beklenmedik).

- [ ] **Step 5: Commit**
```powershell
git add scripts/skin/macosskin scripts/skin/apply-skin.ps1 scripts/skin/SkinPatch.java
git commit -m "SKIN 7b: 9 Word widget sinifi (Mac birebir) + install cagrilari + derleme listesi"
```

---

### Task 2: Uygula + delegate kayıt kanıtı

**Files:** Create `scripts\skin\SkinProbe.java`

- [ ] **Step 1: Apply + blok aktivasyonu**
```powershell
.\build.ps1 -Only download | Out-Null
. .\scripts\common.ps1
$out = .\scripts\skin\apply-skin.ps1 -Jar (Join-Path $InputDir $MainJar) 2>&1; $out
"=== eskiden atlanan 3 blok artik aktif mi (atlandi GORMEMELI) ==="
$out | Select-String 'PopupRemap|MenuMarks|FlatEtchedBorder|popup widget|menu isaret|grup cer'
```
Expected: `PopupRemap`/`MenuMarks`/`FlatEtchedBorder` "no such class" UYARISI YOK; ilgili bloklar "uygulandı" yazar.

- [ ] **Step 2: Delegate kayıt probe'u yaz**

`scripts\skin\SkinProbe.java`:
```java
import javax.swing.UIManager;
public class SkinProbe {
    public static void main(String[] a) throws Exception {
        org.jvnet.substance.SubstanceLookAndFeel.setSkin(new macosskin.FlatUdeSkin());
        macosskin.WordTooltip.install(); macosskin.WordCombo.install(); macosskin.WordCheck.install();
        macosskin.WordButton.install(); macosskin.WordTabs.install(); macosskin.WordField.install();
        String[] keys = {"ButtonUI","ComboBoxUI","CheckBoxUI","TabbedPaneUI","TextFieldUI","ToolTipUI"};
        for (String k : keys) System.out.println(k + " -> " + UIManager.get(k));
    }
}
```

- [ ] **Step 3: Probe'u jar'a (yamalı) karşı çalıştır**
```powershell
$jdk=Get-Jdk11Home; $jar=Join-Path $InputDir $MainJar
Remove-Item "$env:TEMP\sprobe" -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force "$env:TEMP\sprobe" | Out-Null
& (Join-Path $jdk 'bin\javac.exe') -cp "$jar" -d "$env:TEMP\sprobe" scripts\skin\SkinProbe.java
& (Join-Path $jdk 'bin\java.exe') -cp "$env:TEMP\sprobe;$jar" SkinProbe
```
Expected: ButtonUI→`macosskin.WordButton...`, ComboBoxUI→`macosskin.WordCombo...`, CheckBoxUI→`macosskin.WordCheck...`, TabbedPaneUI→`macosskin.WordTabs...`, TextFieldUI→`macosskin.WordField...`, ToolTipUI→(Basic veya WordTooltip). Bizim delegate'lerimiz görünmeli (Substance ezmedi). Görünmezse: install'ı geç noktaya taşı/araştır.

- [ ] **Step 4: Commit**
```powershell
git add scripts/skin/SkinProbe.java
git commit -m "SKIN 7b: delegate kayit probe'u (UIManager.get kaniti)"
```

---

### Task 3: Build + gerçek-app görsel + type-ahead + rollback

**Files:** (yok)

- [ ] **Step 1: -Skin build**
```powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
.\build.ps1 -Only download | Out-Null
$env:UDE_VERSION='5.4.21'; .\build.ps1 -Skin; "exit: $LASTEXITCODE"; $env:UDE_VERSION=$null
```

- [ ] **Step 2: Açık modda diyalog ss (Bul/Değiştir + combo + checkbox)**

Uygulamayı başlat (light), Ctrl+F (Bul) diyaloğunu aç, font combo'sunu aç, Biçim sekmesine git. ss al → `scratchpad\skin7b-light.png`. Kontrol: butonlar düz, sekmeler kutusuz (mavi alt çubuk), combo yuvarlak, checkbox vektör işaretli.

- [ ] **Step 3: Koyu modda aynı ss**

colorMode=dark, yeniden başlat, aynı diyaloglar → `skin7b-dark.png`. Kontrol: koyu düz widget'lar, çift-beyaz çerçeve yok, okunur.

- [ ] **Step 4: type-ahead davranışı**

Font combo'da: popup KAPALIYKEN harf yaz (stok seçim), popup AÇIKKEN harf yaz (yalnız liste vurgusu taşınır, combo kapanmaz/commit etmez), Enter ile commit, Esc ile iptal. Bozarsa: WordCombo type-ahead'i araştır.

- [ ] **Step 5: Rollback**

`-Skin` olmadan build/başlat → stok gri 3B butonlar. colorMode=system'e al.

- [ ] **Step 6: Branch'i tamamla** — `superpowers:finishing-a-development-branch`.

---

## Plan Öz-İncelemesi

**Spec kapsama:** 9 sınıf kopya → Task 1 ✓; install çağrıları → Task 1 Step 3 ✓; derleme listesi → Task 1 Step 2 ✓; delegate kanıtı (A) → Task 2 ✓; gerçek-app ss (B) → Task 3 ✓; type-ahead (C) → Task 3 Step 4 ✓; idempotans (D) → install verbatim (sert guard yok) ✓; doğrulama sırası (E) → Task 2 sonra Task 3 ✓; blok aktivasyonu → Task 2 Step 1 ✓.
**Placeholder:** Yok.
**Tip tutarlılığı:** install() FQN'leri `macosskin.WordXxx`; probe UI anahtarları spec'le aynı.
