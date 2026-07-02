# CLAUDE.md — ude-win-x64 mühendislik notları

Bu depo, resmî **UYAP Doküman Editörü**'nü (UDE) Windows x64'te modern + keskin çalıştırmak
için **build-zamanı bytecode yaması** uygular. Kaynak kod içermez; resmî `editor-app.jar`
build sırasında uyap.gov.tr'den indirilir, yamalanır ve gömülü Java 11 ile `.exe`'ye paketlenir.

Bu, [`ude-mac-arm64`](https://github.com/saidsurucu/ude-mac-arm64) portunun Windows'a aktarımıdır.
Hedef: **Mac yaması ile birebir feature parity** (Mac-spesifik girdi/donanım/pencere-kromu hariç).

## Build hattı

```powershell
.\build.ps1                 # tam yapı (TÜM özellikler varsayılan ACIK) -> dist\*.exe
.\build.ps1 -Only patch     # sadece jar'ı yamala (build\input\editor-app.jar)
.\build.ps1 -Only download  # sadece resmî paketi indir
.\build.ps1 -NoSkin -NoIcons  # bir özelliği kapat
```

Fazlar (`scripts/build.ps1` orkestrasyonu): **deps** (JDK 11+17 + WiX) → **download**
(resmî zip → editor-app.jar) → **patch** (`scripts/patch.ps1`) → **package**
(`jlink` minimal runtime + `jpackage --type exe --input build\input`).

`scripts/common.ps1` ortak yardımcıları/yolları tanımlar (`$InputDir`, `$MainJar`,
`$BuildDir`, `$VendorDir`, `Get-Jdk11Home`, `Write-Ok`, `New-Dir`).

## Apply deseni (her özellik)

Obfuscated jar'a yama şu sırayla yapılır (apply-*.ps1 içinde):
1. Yardımcı sınıfları (`macosX/*.java` veya `com/udewin/X/*.java`) **jar'a karşı** derle.
2. `jar uf $jar -C <helper> .` ile yardımcıları jar'a enjekte et (patcher onları classpath'ten çözer).
3. `*Patch.java` (Javassist) patcher'ı derle (cp: javassist + helper).
4. Patcher'ı çalıştır: `java ... XPatch <jar> <out-dir>` → yamalı `.class`'ları out-dir'e yazar.
5. `jar uf $jar -C <out-dir> .` ile yamalı sınıfları jar'a geri yaz.

Javassist `3.30.2-GA` `$VendorDir\lib`'e indirilir. Bayraklar `scripts/patch.ps1` içinde
bir `foreach` döngüsü (default-on, `$env:X -ne '0'`) veya ayrı bloklarla yönetilir.

## Özellikler (hepsi varsayılan ACIK; `$env:X=0` / `-NoX` kapatır)

| Bayrak | Ne | Obfuscated hedefler |
|---|---|---|
| ICONS | Fluent ikonlar + HiDPI (1x/@1.5x/@2x multi-res) | `Utils.b`, `Utils.a`, Flamingo `ImageWrapperIcon`/`FilteredResizableIcon.paintIcon` |
| NATIVE_DIALOGS | Win32 Aç/Kaydet (`java.awt.FileDialog`) | `tr/*` `show*` çağrıları; matcher = **ad+imza+declaring-class JFileChooser alt-tip** (sınıf-bağımsız). UDE diyalogları `gui.dp`→`gui.a.p`→`JFileChooser` üzerinden; tarama "JFileChooser literal" şartı YOK (yoksa `fm/iI/nn/op` Aç çağıranları atlanırdı) |
| **FILEASSOC** | .udf çift-tık açma | `WPAppManager.main`'e `$1=ArgFix.normalize($1)` inject; kontrol-jetonu yoksa (yalnız dosya yolu) başa `getNewWPInstance` ekle (yoksa `a(String[])` erken `return` ediyor) |
| LIVETOGGLE | Otomatik-düzeltme anında etkin | `...pki.b.l` reflection |
| TABLEDELETE | Backspace/Delete tablo sil | `WPAppManager.main` inject; `DocumentEx` **void f(int)** overload |
| IMGFULL | Satır-içi imaj tam-çöz | `editor.utils.h`, `swing.wp.b.at.drawImage` (bicubic) |
| IMGRESIZE | Fare köşe-tutamağıyla boyutlandırma | `text.hj` paint/mouse + `macosimgresize.ImageResizeController` |
| ANTET | "Antetlerim" (`%APPDATA%\UDE\Antetler`) | `gui.gR.c()` → `AntetUI.install` |
| PDFFRESH | "PDF Kaydet" canlı serialize | `text.J.b(File)`: `lo.a(out)` → `lo.a(out,true)` |
| PASTEIMG | Panodan imaj kalitesi | `text.hj.paste`: `aa.a` yıkıcı küçültme atla + `Conv` |
| **FOPFONTS** | PDF'te Türkçe harf (ğĞşŞıİ) | `editor.b.a` (FopFactory.newInstance→FopFonts.apply), `b.c` (awtToPdf→ITextFonts.map), `b.b` (getPageFormat→PageFix.a4) |
| **CARETFIX** | Zoom imleç/tıklama hizası (sadece Faz-2) | `wp.prof.d.O` modelToView/viewToModel + `wp.textUtils.p.c` |
| **ZOOMKEYS** | Ctrl+/Ctrl− klavye zoom | `WPAppManager.main` inject; zoom JSlider sür |
| **LINESPACING** | Satır aralığı menüsüne "1.5" ekle (satıcı unutmuş) | `a.b.a.a.M` kurucu 3. `addMenuButton` ("2.0") öncesine `JCommandMenuButton("1.5")`; dinleyici `O`→`LS15` bytecode kopya (1.15f→1.5f `$0.a(F)V` ExprEditor); `LsInject.make` yansıma fabrikası (obfuscate paket kaynakta FQCN ile anılamaz) |
| PASTERICH+PLAINPASTE | Stilli/formatsız yapıştırma; **Ctrl+V akıllı: UDE-içi formatlı, harici FORMATSIZ (varsayılan, 2026-07); Ctrl+Shift+V + sağ tık "Formatlı Yapıştır" = formatlı** | `text.hj` paste yolu; CF_HTML `DataFlavor.allHtmlFlavor`; `PasteMode` bayrağı; `PasteKeys` dispatcher (`WPAppManager.main` inject) |
| SKIN | Düz Substance + Word widgets + açık/koyu + canlı geçiş | `SkinPatch` (~750 satır); `FlatUdeSkin`/`Dark`, `Word*`, `DarkMode`, `ModeSwitch`; **winlook.jar** -javaagent (renk-modu picker + canlı geçiş) |

Obfuscated adlar platformlar arası **AYNI** (aynı UDE build'i) → Mac patcher'ları çoğunlukla
verbatim çalışır. Hedefleri yamadan önce `javap`/`jar tf` ile DOĞRULA.

## Windows uyarlamaları (Mac'ten farklar)

- **Koyu mod tespiti**: kayıt defteri `HKCU\...\Themes\Personalize\AppsUseLightTheme`
  (Mac `defaults read` yerine). `macosskin/DarkMode.java`.
- **Pano HTML**: `DataFlavor.allHtmlFlavor` (Windows CF_HTML soyutlar; Mac NSPasteboard yerine).
  DİKKAT: JDK pano yolu (ClipboardTransferable→`translateBytes`) String temsilinde HTMLCodec'i
  ATLAR → CF_HTML başlığı (`Version:0.9 StartHTML:...`) String'e dahil gelir;
  `RichPaste.stripCfHtml` soyar (codec yalnız Reader/InputStream `translateStream` yolunda).
- **Yollar**: `%APPDATA%\UDE\Antetler`, log `%LOCALAPPDATA%` (Mac `~/Library/...` yerine).
- **Font**: arayüz `Segoe UI` (Mac `Helvetica Neue`); FOP/iText için **Liberation** gömülü
  (Mac sistem Arial/Times yerine — Windows'ta da sistem fontuna güvenmiyoruz).
- **FOP file URL**: `file:/C:/...` ileri-bölü, **%-kodlama YOK** (FOP 0.92 %20'yi çözmüyor).
- **winlook.jar agent** (Mac MacLook'un eşi): `WinLook.java`; sadece platform-nötr metotlar
  (fixRulerBackground/boldTaskTabs/removeScopeCombo/addDarkPageToggle/addColorModeCombo).
  Mac chrome metotları (unifyTitleBar/hookTitle/removeMemoryBar) ATLANDI.

## Mac-spesifik ATLANANLAR (port edilmedi; gerekçeli)

eawt-shim (Windows JDK'da çakışma yok), macos-textkeys (Cmd→Ctrl remap, Emacs-binding bypass,
Option-chars, dikte fix, tooltip remap — Windows native Ctrl), trackpad zoom **jesti**
(Ctrl+/Ctrl− klavye zoom PORT EDİLDİ), sqlite swap (Windows DLL zaten var), PCSC
(winscard.dll otomatik), traffic-lights/title-chrome, **CARETFIX Faz-1** (1px imleç kozmetiği;
Faz-2 zoom düzeltmesi port edildi), TEXTREPLACE (macOS sistem DB'si).

## KRİTİK TUZAKLAR (hepsi bu projede yaşandı)

1. **Java dosyaları BOM'suz UTF-8 yazılmalı.** PS `Set-Content -Encoding UTF8` BOM ekler,
   javac reddeder. Kullan: `[System.IO.File]::WriteAllText($f, $c, (New-Object System.Text.UTF8Encoding $false))`.
   (Edit/Write tool'ları BOM eklemez; sorun PowerShell ile yazınca.)
2. **`\u` yorumlarda bile unicode-escape.** javac `\u` dizisini yorumda DA çözer; `\ude`,
   `\udewin` "illegal unicode escape" verir. Yorumlarda ters-bölü yerine **ileri-bölü** kullan
   (örn. `%LOCALAPPDATA%/ude-antet.txt`).
3. **javac `-encoding UTF-8` şart.** Varsayılan Türkçe windows-1254; UTF-8 `Ş` (0x9E) çöker.
4. **Build çıktısını YÖNLENDİRME** (`>`, `2>&1`, `*>`, `| Tee`). javac'in bilgi-amaçlı
   "Note: deprecated API" satırı PS-5.1 native-stderr + `$ErrorActionPreference='Stop'`
   etkileşimiyle build'i DURDURUR. **Düz çalıştır** (`| Out-Null` OK, stderr'i sarmaz).
   Aynısı `jpackage`'ın "Picked up JAVA_TOOL_OPTIONS" stderr'i için de geçerli.
5. **jar kilidi**: çalışan java editor-app.jar'ı kilitler → indirme/yama öncesi
   `Get-Process java | Stop-Process -Force`.
6. **TABLEDELETE overload**: `model.v`'de 3 `f(int)` overload var (Element/boolean/void);
   `getMethod("f", int.class)` JDK-build'e göre KEYFİ seçer (Temurin→boolean=tek hücre,
   void=tüm tablo). Açıkça **void f(int)** seç (`tableRemoveMethod`).
7. **git push / jpackage "exit 255/1"**: PS git stderr'ini sarar; `local==remote` ile
   gerçek sonucu doğrula.
8. **jpackage ProductCode deterministik** (name+version hash'i): aynı sürümü yeniden
   derleyip kuran kullanıcıda MSI **bakım moduna** düşer, yeni dosyalar KURULMAZ (yamalar
   kullanıcıya ulaşmaz). Çözüm: `scripts/wix/main.wxs` (`--resource-dir`) → `Product Id="*"`
   + upgrade `IncludeMaximum="yes"`. JDK 17 güncellenince şablonu
   `build\jptmp\config\main.wxs` ile diff'le (iki bilinçli fark dışında eşit kalmalı).

## Doğrulama yöntemleri

- **Ekran görüntüsü**: fiziksel piksel için `SetProcessDPIAware()` (DPI-unaware capture 150%
  ekranı küçültüp pixelation'ı gizler). HWND foreground + `CopyFromScreen`.
- **FOPFONTS**: `FopRenderTest` — FO→PDF render edip çıktıda `LiberationSerif/Sans`+`/FontFile2`
  +`Identity-H` var mı, base-14 fallback yok mu kontrol et.
- **Reflection probe**'ları: delegate kaydı, pixel rengi, ModeSwitch no-throw.
- **Tam pipeline**: `.\build.ps1 -Only patch` bayraksız → exit 0 = tüm yamalar kompoze.

## Çalışma akışı (kullanıcı direktifi)

Her plan/spec yazmadan ÖNCE bir kez **Codex'e danış** (`codex exec --skip-git-repo-check`),
sonra otonom ilerle (spec→plan→uygula→doğrula→merge→push). Mac'ten **önce verbatim kopyala,
sonra** Windows için uyarla. Spec'ler `docs/superpowers/specs/`'e.

## Referanslar

- Mac referans klonu (port kaynağı): `git clone https://github.com/saidsurucu/ude-mac-arm64`
  (Mac CLAUDE.md çok daha ayrıntılı; obfuscated hedef gerekçeleri orada).
- İkon üretimi: `scripts/icons/fluent/generate.py` (1x/@2x) + `gen15x.py` (@1.5x, resvg ile).
