# UDE Windows x64

**Uyap Doküman Editörü (UDE)** için bağımsız, **gayriresmî** bir Windows x64 yapısı.
Resmî x86 UDE paketini alır, **gömülü Java 11** ile yeniden paketler ve modern,
yüksek çözünürlüklü ekranlarda **keskin** çalışan bir `.exe` kurulum dosyası üretir.

> ⚠️ Bu depo UDE'nin kaynak kodunu **içermez**. T.C. Adalet Bakanlığı / UYAP ile
> ilişkili değildir, onlar tarafından geliştirilmemiş/onaylanmamıştır. Resmî UDE paketi
> derleme sırasında **uyap.gov.tr** üzerinden **sizin** bilgisayarınıza indirilir ve
> yama **sizin** tarafınızdan uygulanır. Önceden derlenmiş ikili dağıtılmaz.

## Neden?

Resmî UDE Windows'ta zaten çalışır; ama Java 8 ile gelir ve `-Dsun.java2d.dpiaware=false`
ile DPI ölçeklemeyi kapatır — bu yüzden 4K / yüksek-DPI ekranlarda **bulanık** görünür.
Bu yapı şunları getirir:

- 🔤 **Keskin metin (HiDPI / 4K)** — Java 11'in yerel DPI ölçeklemesi (JEP 263). Bulanıklık biter.
- ☕ **Gömülü Java 11** — ayrıca Java kurmanıza gerek yok, runtime `.exe` içinde gelir.
- 🖋️ **E-imza / akıllı kart** — `javax.smartcardio` Windows'un `winscard.dll`'i üzerinden çalışır
  (ayrıca [AKİS Windows sürücüsü](https://www.kamusm.gov.tr) kurulmalıdır).
- 📂 **Native Windows aç/kaydet diyaloğu** — arkaik Swing `JFileChooser` yerine gerçek Win32
  diyaloğu (`java.awt.FileDialog`). **Varsayılan açık** (`$env:NATIVE_DIALOGS='0'` ile kapatılır;
  klasör-seçme gereken yerlerde otomatik Swing'e geri düşer).
- 🎨 **Modern ikonlar** — Fluent UI System Icons + fonksiyonel renk, HiDPI keskinliğinde.
  Tam-kat ölçek için `@2x`, **kesirli ölçek (%125/150/175) için native `@1.5x` vektör seti**
  (`resvg` ile SVG'den üretilir; `scripts/icons/fluent/gen15x.py`) → cihaz boyutuna ölçeksiz,
  pixel-tam render. **Varsayılan açık** (`-NoIcons` ile kapatılır).
- 📄 **`.udf` çift-tıkla aç** — kurulum dosya ilişkilendirmesini otomatik kaydeder.

### Modern görünüm ve düzenleme (macOS portuyla birebir parite)

[`ude-mac-arm64`](https://github.com/saidsurucu/ude-mac-arm64) portundaki tüm
platform-bağımsız özellikler aktarıldı (Mac-spesifik düzeltmeler hariç). **Hepsi varsayılan
açık** — kapatmak için ilgili `-No…` bayrağı (bkz. *Manuel derleme*):

- 🖌️ **Modern düz görünüm (SKIN)** — düz Substance teması, düzleştirilmiş Flamingo şeridi,
  Word-stili buton/sekme/combo/onay-kutusu/ipucu, nötr cetvel/kanvas. **Açık + koyu mod**
  (Windows sistem temasından, `AppsUseLightTheme`). Şeritte **renk-modu seçici** (Açık/Koyu/
  Sistem) + **canlı geçiş** (restart'sız) + koyu-belge onay kutusu. (`-NoSkin` ile kapatılır.)
- 📋 **Stilli yapıştırma** — Word/tarayıcı/PDF'den kalın/italik/liste/**tablo**/renk biçimiyle
  yapışır (Windows panosu `CF_HTML`). Formatsız Yapıştır (Ctrl+Shift+V).
- 🖼️ **Görseller** — satır-içi imaj tam çözünürlük (bulanıklık yok), fare köşe-tutamağıyla
  boyutlandırma, panodan imajı kalitesini koruyarak yapıştırma.
- ⌨️ **Düzenleme** — Backspace/Delete ile tablo silme, Otomatik Büyük Harf/Kelime Denetimi
  anında etkin (restart'sız).
- 📝 **Antetlerim** — kişisel antet bölümü (`%APPDATA%\UDE\Antetler`).
- 📑 **Taze PDF** — "PDF Olarak Kaydet" canlı belgeden serialize eder ("önce Kaydet" gerekmez).
- 🔤 **PDF'te Türkçe harfler** — "PDF Olarak Kaydet" çıktısında `ğ Ğ ş Ş ı İ` artık doğru çıkar
  (gömülü tam-Unicode font; eski FOP base-14/Cp1252 bu harfleri düşürüyordu — platform-bağımsız hata).
- 🔍 **Klavye/zoom** — `Ctrl +` / `Ctrl −` ile yakınlaştır/uzaklaştır; %100 dışı zoomda imleç ve
  tıklama hizası düzeltildi. Kaydet'te çoklu-format (UDF/RTF/PDF…) seçim penceresi.

## 👩‍⚖️ Kolay kurulum — tek satır

Programcı olmanıza gerek yok. **PowerShell**'i açın: klavyede **Windows tuşu**'na basın,
açılan arama kutusuna **PowerShell** yazın ve **Enter**'a basın. Açılan mavi (ya da siyah)
pencereye aşağıdaki **tek satırı** kopyalayıp yapıştırın (pencerede **sağ tıklamak** =
yapıştırır) ve **Enter**'a basın:

```powershell
irm https://raw.githubusercontent.com/saidsurucu/ude-win-x64/main/kur.ps1 | iex
```

Hepsi bu kadar. Manuel indirme, klasöre girme, Java kurma gibi adımlar **yok**. Bu komut
gerisini sizin için yapar:

- **Kaynak kodu** `C:\Users\<kullanıcı-adınız>\ude-win-x64` klasörüne indirir (zaten varsa
  en güncel sürüme günceller).
- Gereken **Java** sürümlerini ve araçları otomatik indirir — hepsi o klasörün altındaki
  `vendor\` içinde kalır; **bilgisayarınıza sistem geneline hiçbir şey kurmaz**.
- Uygulamayı **tüm modern özelliklerle** derler ve paketler: keskin metin (4K/HiDPI),
  modern ikonlar, düz görünüm + **koyu mod**, native Aç/Kaydet pencereleri, e-imza ve tüm
  düzenleme iyileştirmeleri (hepsi **varsayılan açık**).

İlk derleme internet hızınıza göre birkaç dakika sürebilir.

### Bittiğinde ne olur?

Komut tamamlanınca `...\ude-win-x64\dist\` klasöründe bir **kurulum dosyası**
(`UyapDokumanEditoru-5.4.17.exe`) oluşur ve betik onu **kendiliğinden açar** — karşınıza
tanıdık bir **kurulum sihirbazı** gelir. Sihirbazda **İleri → İleri → Kur** deyip bitirin
(isterseniz kurulacak klasörü seçebilirsiniz). Kurulum bitince UDE; **Başlat menüsü**nde ve
**masaüstünde** kısayol olarak hazır olur. Artık herhangi bir **`.udf` dosyasına çift
tıklayarak** da açabilirsiniz.

> ⚠️ **"Windows bilgisayarınızı korudu" uyarısı çıkarsa** (SmartScreen): kurulum dosyası
> imzasız olduğu için normaldir. Endişelenmeyin — küçük **"Daha fazla bilgi"** yazısına,
> sonra çıkan **"Yine de çalıştır"** düğmesine tıklayın.

**Yeni Editör sürümü çıktığında yukarıdaki tek satırı yeniden çalıştırmanız yeterli** — en
güncel sürüm otomatik inip yeniden paketlenir.

> 💼 **E-imza kullanacaksanız:** Akıllı kart e-imzası için ayrıca **AKİS Windows sürücüsünü**
> kurun ([Kamu SM](https://www.kamusm.gov.tr)). Java tarafında ek ayar gerekmez; bkz.
> *[E-imza notu](#e-imza-notu)*.

> 🔧 Bir özelliği kapatmak isterseniz (örn. koyu/düz görünümü istemiyorsanız) tek satırdan
> **önce** ilgili anahtarı verin: `$env:SKIN='0'` (düz görünüm kapalı) ya da `$env:ICONS='0'`
> (orijinal ikonlar), sonra komutu çalıştırın.

## Manuel derleme

```powershell
git clone https://github.com/saidsurucu/ude-win-x64
cd ude-win-x64
.\build.ps1                 # tam yapı -> dist\UyapDokumanEditoru-<sürüm>.exe
```

**`.\build.ps1` her özelliği açık derler.** Seçenekler:

| Komut | Açıklama |
|---|---|
| `.\build.ps1` | **Tam yapı — tüm özellikler açık** (önerilen) |
| `.\build.ps1 -NoSkin` | Modern düz görünümü kapat (sade Substance) |
| `.\build.ps1 -NoIcons` | Modern Fluent ikonları kapat (orijinal ikonlar) |
| `.\build.ps1 -Sign` | Üretilen EXE'yi `signtool` ile imzala |
| `.\build.ps1 -Only package` | Sadece paketleme fazı (tekrar derleme) |
| `$env:UDE_URL="..."; .\build.ps1` | Resmî paket linkini elle ver |

**Tüm özellikler varsayılan açık** — tek tek kapatmak için ilgili bayrak: modern görünüm
(`-NoSkin`), modern ikonlar (`-NoIcons`), native diyalog (`-NoNativeDialogs`), tablo-silme
(`-NoTableDelete`), canlı otomatik-düzeltme (`-NoLiveToggle`), stilli yapıştırma
(`-NoPasteRich`), formatsız yapıştırma (`-NoPlainPaste`), panodan imaj (`-NoPasteImg`),
satır-içi imaj tam-çöz (`-NoImgFull`), imaj boyutlandırma (`-NoImgResize`), antet
(`-NoAntet`), taze PDF (`-NoPdfFresh`), PDF'te Türkçe harf (`-NoFopFonts`), zoom imleç
hizası (`-NoCaretFix`), Ctrl+/Ctrl− klavye zoom (`-NoZoomKeys`).

> **NOT:** Çıktıyı dosyaya yönlendirmeyin (`>`, `2>&1`, `| Tee`). javac'in bilgi-amaçlı
> "Note: deprecated API" satırı PowerShell 5.1'de yönlendirilince build'i durdurur. Düz
> çalıştırın; normal kullanımda sorun yoktur.

Çıktı: `dist\UyapDokumanEditoru-<sürüm>.exe`

## Nasıl çalışır?

`editor-app.jar` platformdan bağımsız tek bir "fat jar"dır (tüm bağımlılıklar gömülü).
Yapı onu olduğu gibi alır ve Java 11 runtime'ı ile `jpackage` üzerinden Windows `.exe`
kurulum dosyasına paketler:

1. **deps** — Temurin JDK 17 (jpackage için) + JDK 11 (gömülü runtime için) + WiX Toolset 3.x indirilir.
2. **download** — `uyap.gov.tr/Uyap-Editor` sayfasından resmî paket çekilir, `editor-app.jar` çıkarılır.
3. **patch** — native diyalog, modern görünüm ve düzenleme yamaları (Javassist) uygulanır
   (bayraklarla; bkz. *Manuel derleme*). Her özellik build-zamanı `editor-app.jar`'ı yeniden
   yazar; gömülen üçüncü-parti Java yoktur (Javassist yalnız build aracı).
4. **package** — JDK 11'den `jlink` ile minimal runtime üretilir, `jpackage --type exe` ile `.exe` kurulum dosyası oluşturulur.

### macOS portuyla parite

[`ude-mac-arm64`](https://github.com/saidsurucu/ude-mac-arm64) portundaki **platform-bağımsız
tüm özellikler aktarıldı.** Yalnızca aşağıdakiler Windows'ta **gereksiz / Mac-özgü** olduğu için
atlandı:

| Özellik | Neden atlandı |
|---|---|
| sqlite-jdbc 3.7.2 → 3.46 | jar zaten `native/Windows/amd64/sqlitejdbc.dll` içerir |
| `com.apple.eawt` strip + eawt-shim | Windows JDK'da çakışma yok, Mac kod yolu tetiklenmez |
| `PCSC.framework` enjeksiyonu | Windows yerleşik `winscard.dll` kullanır |
| ⌘-remap, ⌥-option, trackpad zoom jesti, dikte | Mac'e özgü girdi/donanım (Ctrl+/Ctrl− klavye zoom **port edildi**) |
| Trafik ışıkları, bütünleşik başlık çubuğu | Mac pencere kromu (Windows yerel başlık çubuğu) |
| CARETFIX Faz-1 (1px imleç) | macOS fractional-render imleç-binmesi kozmetiği (Windows 2px sorunsuz). Faz-2 zoom düzeltmesi **port edildi** |
| TEXTREPLACE | macOS sistem Metin Değiştirme DB'sini okur (Windows karşılığı yok) |

Mac'in native (Swift/Cocoa) parçaları Windows standart API'leriyle değiştirildi: pano HTML'i
`DataFlavor.allHtmlFlavor` (CF_HTML), koyu-mod tespiti kayıt defteri `AppsUseLightTheme`.

Keskin metnin anahtarı: resmî launcher'ın `-Dsun.java2d.dpiaware=false` / `uiScale=1`
ayarları **bilerek atlanır**, böylece Java 11 yerel HiDPI ölçeklemesi devreye girer.

## E-imza notu

Akıllı kart ile e-imza için **AKİS Windows sürücüsünü** ([Kamu SM](https://www.kamusm.gov.tr))
ayrıca kurun. Java tarafında ek ayar gerekmez; `javax.smartcardio` Windows'un
`winscard.dll`'ini otomatik kullanır ve gömülü runtime `java.smartcardio` modülünü içerir.

## Gereksinimler

- Windows 10/11 x64
- PowerShell 5.1+ (Windows'ta yerleşik)
- İnternet bağlantısı (araçlar + UDE paketi indirilir)

## Lisans

Yapı scriptleri, yamalar ve dokümantasyon için **MIT** (bkz. [LICENSE](LICENSE)).
UDE'nin kendisi T.C. Adalet Bakanlığı'na aittir ve bu depo kapsamında değildir.
