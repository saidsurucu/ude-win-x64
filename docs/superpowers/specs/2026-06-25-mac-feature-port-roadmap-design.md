# UDE Windows portu — Mac özellik aktarım yol haritası (tasarım)

**Tarih:** 2026-06-25
**Durum:** Onaylandı (yol haritası); her alt-proje kendi spec'ini alır.

## Amaç

`ude-mac-arm64` deposundaki **platformdan bağımsız** tüm özellikleri `ude-win-x64`'e
aktarmak; macOS'a özgü düzeltmeleri hariç tutmak. Mevcut Windows yapısı zaten
`deps → download → patch → package` PowerShell hattına, gömülü Java 11 runtime'ına
(jlink + jpackage) ve iki yamaya (native dosya diyaloğu, Material ikonlar) sahiptir.

## Kapsam kararları

**Aktarılacak (taşınabilir):** SKIN (modern görünüm), ICONS (Material→Fluent),
PASTERICH + PLAINPASTE + PASTEIMG, TABLEDELETE, LIVETOGGLE, IMGRESIZE + IMAGEFULL,
FOOTNOTE, ANTET.

**Hariç (macOS'a özgü):** eawt-shim, ⌘/⌥ tuş remap, trackpad zoom, macos-caret,
Cocoa native dosya diyaloğu (Win'in kendi `java.awt.FileDialog` versiyonu var),
dikte düzeltmesi, trafik-ışığı / bütünleşik başlık çubuğu pencere kromu,
`pbrich.swift` & `NativeDialogKeys.m` native kod, `defaults read` koyu-mod tespiti,
sqlite-jdbc swap (Win jar'ı zaten `native/Windows/amd64/sqlitejdbc.dll` içerir).

**Bilerek atlanan:** TEXTREPLACE (macOS sistem metin-değiştirme DB'sini okur; Windows
karşılığı yok), FOPFONTS (Liberation fontları gömer; Windows zaten Arial/Times içerir —
ancak SKIN/PDF işine geçmeden PDF çıktı paritesi doğrulanmalı, bkz. Riskler).

## Paylaşılan aktarım deseni (her özellik için)

Her Mac özelliği `scripts/<feature>/` altında `*Patch.java` patcher + yardımcı sınıf
(+ bazen runtime javaagent) olarak yaşar. Aktarım adımları:

1. `.java` kaynaklarını `scripts/<feature>/` altına kopyala.
2. Az sayıdaki platform çağrısını çevir:
   - Koyu-mod tespiti → Windows kayıt defteri
     `HKCU\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize\AppsUseLightTheme`
     (eksik anahtarı tolere et; çalışma-anı değişimi için canlı senkron iddia etme).
   - Pano HTML'i → Java `DataFlavor.allHtmlFlavor` (Swift köprüsü silinir).
   - `com.apple.eawt`, pencere kromu, tuş remap'leri tamamen at.
3. `apply-<feature>.ps1` ekle (mevcut `apply-nativedialog.ps1` desenini izler:
   bridge derle → patcher derle → çalıştır → `jar uf`).
4. `patch.ps1`'e `$env:<FEATURE>` bayrağı + `build.ps1`'e `-Switch` ekle, Mac'teki gibi
   **varsayılan açık**.
5. `.exe`'yi derleyip özelliği Windows'ta çalışan uygulamada elle dene.

Yeni build altyapısı gerekmez; yalnız `patch.ps1` koşullu bloklarla büyür. Gömülü JRE
ve paketleme değişmez. Javassist yalnız **build-zamanı** aracıdır (`.exe`'ye girmez).

## Ayrıştırma ve sıralama

Her madde bağımsız bir alt-projedir (kendi spec → plan → uygulama → elle GUI doğrulama).
Tüm alt-projeler için ortak kabul ölçütleri: build başarılı, yama bayrakla
açılır/kapanır, özellik kurulu `.exe`'de çalışır, geri-alma yolu mevcut.

| # | Alt-proje | Not |
|---|---|---|
| 0 | **Paylaşılan patch-runner yardımcısı** | `apply-*.ps1` tekrar eden mantığını 2. alt-projeden sonra tek yardımcıya çıkar. Özellik değil, hafif refactor. |
| 1 | **TABLEDELETE + LIVETOGGLE** | Küçük, saf-Java, platform çağrısı yok — aktarım desenini uçtan uca doğrular. |
| 2 | **SKIN fizibilite spike'ı** | Dar kapsam: Substance/Flamingo + kayıt defteri koyu-mod + HiDPI çalışıyor mu kanıtla; Mac kromunun ne kadar dolanık olduğunu haritala. Çıktı: git/gitme + tam SKIN portu için gerçek plan. |
| 3 | **ICONS** (Material → Fluent) | Yüksek görsel değer; retina IconLoaderPatch. |
| 4 | **Paste** (PASTERICH + PLAINPASTE + PASTEIMG) | Tek pano test matrisi; CF_HTML tuhaflıkları. |
| 5 | **Images** (IMGRESIZE + IMAGEFULL) | Tek imaj-akışı spec'i. |
| 6 | **ANTET + FOOTNOTE** | Bağımsız belge özellikleri. |
| 7 | **SKIN tam** | Manşet görsel port; spike ile bilgilendirilir. |

Bu konuşma **yol haritası tasarımını** üretir; ardından **1. alt-proje
(TABLEDELETE + LIVETOGGLE)** ayrıntılı olarak brainstorm edilir. Sonraki her alt-proje
sırası gelince kendi brainstorm → spec → plan döngüsünü alır.

## Windows'a özgü riskler (spec'lere gömülecek)

- **CF_HTML:** Windows pano HTML'i başlık/offset + fragman işaretleri + kodlama
  tuhaflıkları taşır; uygulamalar çok değişir. Word, Chrome/Edge, Acrobat/tarayıcı PDF,
  LibreOffice ayrı ayrı test edilmeli. `allHtmlFlavor` hepsinde aynı çıktı vermeyebilir.
- **Pano kilidi:** Swing pano erişimi başka süreç panoyu kilitlerse geçici başarısız
  olabilir → yeniden-deneme/backoff ekle.
- **Kayıt defteri koyu-mod:** `AppsUseLightTheme` kullanıcı-başına, çalışırken değişebilir;
  eksik anahtarı ele al; açılış-anı tespiti tamam ama "canlı OS senkronu" iddia etme.
- **Substance/Flamingo:** Windows LAF metrikleri, font render, combo yükseklikleri, şerit
  çizimi, HiDPI ölçekleme macOS'tan ayrışabilir — sadece derleme değil görsel regresyon bekle.
- **Türkçe locale:** `toLowerCase()`/`toUpperCase()` `Locale.ROOT` olmadan identifier,
  kaynak adı, sınıf adı, kayıt-defteri string'i, uzantı kontrolünü bozar.
- **ASCII-dışı yollar:** Kullanıcı dizini Türkçe karakter içerir → PowerShell'de
  `-LiteralPath` kullan, kabuk-birleştirilmiş komut string'lerinden kaçın.
- **PDF paritesi (FOPFONTS):** Windows'un Arial/Times içermesi Mac-yamalı çıktının
  düzen paritesini garanti etmez; SKIN/PDF işine geçmeden Türkçe glif çıktısı doğrulanmalı.

## Tasarım gözden geçirme

Tasarım Codex (codex-cli) ile çapraz incelendi. Ana geri bildirimler folded:
(a) tüm SKIN'i sona bırakma — erken dar fizibilite spike'ı ekle (#2);
(b) 3. yamadan sonra paylaşılan patch-runner yardımcısına çık (#0);
(c) yukarıdaki Windows riskleri. ~9 bağımsız spec'e ayrıştırma onaylandı.
