# Son özellik batch'i: parite tamamlama (tasarım)

**Tarih:** 2026-06-25
**Hedef:** Mac yaması ile birebir feature parity (Mac-spesifik hariç).
**Durum:** Onaylandı (Codex kaynak-okumalı incelemeli; otonom yürütme).

## Parite denetimi (Codex-rafine)

**Net portlanacak (apple ref YOK):**
- **PDFFRESH** (PdfFreshPatch 91): "PDF Olarak Kaydet" bayat önbellek içeriği yazıyor →
  `text.J.b(File)` cached `lo.a(out)` → canlı `lo.a(out,true)`. UDE bug'ı, platform-bağımsız.
- **IMGFULL** (ImageInsertPatch 85): satır-içi imaj tam-çözünürlük (bulanıklık fix).
- **IMGRESIZE** (ImageResizePatch 78 + ImageResizeController 443): fare köşe-tutamağıyla imaj
  boyutlandırma. HiDPI/zoom: karışık birim riski (önizleme=bileşen-px, commit=punto;
  zoom≠1'de fallback riskli) → %100/125/150/200 + UDE zoom test.
- **ANTET** (AntetPatch 28 + AntetUI 244 + AntetStore 108 + AntetLog 24): "Antetlerim"
  (page-setup diyaloğunda kişisel antet). **Codex düzeltmesi: java.util.prefs DEĞİL** —
  dosyaları `~/Library/Application Support/UDE/Antetler`, log `~/Library/Logs` altında saklar.
  **Windows uyarlaması:** `%APPDATA%\UDE\Antetler`, log `%LOCALAPPDATA%`/temp. FileDialog
  filtre + ASCII-dışı buton metni kodlaması da uyarlanır.

**Yeniden değerlendir (Codex — platform-NÖTR kısım port edilmeli):**
- **PASTEIMG**: clipboard cast Mac-özgü AMA patch ayrıca paste yolundaki yıkıcı imaj
  küçültmeyi kaldırır (platform-nötr kalite) → o kısım port edilir.
- **CARETFIX**: 1px caret Mac-ish AMA zoom `modelToView/viewToModel` düzeltmesi platform-nötr
  → o kısım değerlendirilir.
- **FOPFONTS**: Türkçe PDF font sorunu platform-bağımsız (Mac impl Mac font yollarını kullansa
  da) → Windows'ta iText/FOP Türkçe glifleri bulamıyorsa port edilir.

**Net SKIP:** FOOTNOTE (Mac build'inde YOK), TEXTREPLACE (macOS DB).

## Yaklaşım

Tek dal (`feat/final-features`), özellik-başına AYRI commit + bayrak (Codex). Sıra: helper'lar
patcher'dan önce; ANTET en izole (en çok Windows varsayımı). Hepsi copy-verbatim-then-adapt;
obfuscate hedefler jar'da doğrulanır.

### Bayraklar (default-on, =0 kapatır)
PDFFRESH, IMGFULL, IMGRESIZE, ANTET. (Yeniden-değerlendirilenler analiz sonrası eklenir.)

## Doğrulama

- **PDFFRESH:** kaydetmeden yaz → PDF; içerik TAZE (eski değil). İmaj/arkaplanla PDF.
- **IMGFULL:** imaj ekle → tam çözünürlük (bulanık değil), HiDPI keskin.
- **IMGRESIZE:** imaja tıkla → köşe tutamakları; sürükle → boyutlanır. %100/125/150/200 + zoom.
- **ANTET:** page-setup diyaloğunda "Antetlerim"; antet ekle/kaydet → `%APPDATA%\UDE\Antetler`'de
  kalıcı; yeniden açılışta görünür.

## Kabul ölçütleri

1. PDFFRESH: kaydetmeden PDF taze içerik verir.
2. IMGFULL/IMGRESIZE: imaj tam-çöz + boyutlandırılabilir.
3. ANTET: antet kalıcı (Windows yolu), diyalogda görünür.
4. Yeniden-değerlendirilen 3'ün portable kısmı analiz edilip karara bağlanır.
5. Tüm bayraklar default-on; =0 ile kapanır (rollback).

## NİHAİ KARARLAR (yürütme sonrası)

- **Portlandı:** PDFFRESH, IMGFULL, IMGRESIZE, ANTET (Windows yolları), **PASTEIMG**
  (kalite parçası: yıkıcı sığdırma-küçültme atlandı; Mac cast/Conv Windows'ta zararsız
  no-op). Tam pipeline `exit 0` (13 yama aşaması kompoze).
- **SKIP (gerekçeli):**
  - **CARETFIX**: 2px→1px imleç, macOS fractional-render harf-binmesi için; Windows 2px
    imleç sorunsuz → Mac-spesifik kozmetik.
  - **FOPFONTS**: kök neden "macOS Arial/Times eksik" (`/System/Library/Fonts`); Windows
    ikisini de tam Türkçe glifle içerir → sürücü Mac-özgü. (Türkçe PDF bozuksa elle
    doğrulanıp tekrar değerlendirilebilir.)
  - **FOOTNOTE** (Mac build'inde yok), **TEXTREPLACE** (macOS sistem DB'si).

## Build sağlamlık notu

apply-*.ps1 javac'leri deprecated/unchecked **Note** üretir; `*>`/`2>&1` ile yönlendirilince
PS-5.1 native-stderr + `Stop` etkileşimi build'i durdurur. **Düz çalıştırın** (`.\build.ps1`
yönlendirmesiz); kullanıcı normal kullanımında sorun yok.

## Riskler

- **IMGRESIZE zoom birim karışımı:** HiDPI'da test; bozarsa zoom-faktör düzeltmesi.
- **ANTET Windows yolları/encoding:** Library→%APPDATA%; FileDialog/ASCII-dışı.
- **PDFFRESH perf:** canlı serialize daha derin; büyük belge test.
