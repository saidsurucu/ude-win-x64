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
- 🎨 **Modern ikonlar** — Material Design ikonları, HiDPI keskinliğinde (`@2x` multi-resolution).
  Tek satırlık kurulumda **varsayılan açık**; manuel derlemede `-Icons` ile.
- 📄 **`.udf` çift-tıkla aç** — kurulum dosya ilişkilendirmesini otomatik kaydeder.

## Tek satırlık kurulum

PowerShell'i açın ve şunu yapıştırın:

```powershell
irm https://raw.githubusercontent.com/saidsurucu/ude-win-x64/main/kur.ps1 | iex
```

Bu komut gerekli araçları (JDK 11/17 + WiX) ve resmî UDE paketini indirir, `.exe`
kurulum dosyasını üretir ve kurulum sihirbazını başlatır. **Modern ikonlar varsayılan olarak
açıktır** (devre dışı bırakmak için komuttan önce `$env:ICONS='0'` verin). İlk çalıştırma birkaç
dakika sürer (~600 MB araç indirilir; hepsi depo altındaki `vendor/` içinde kalır, sistem geneline dokunmaz).

> **SmartScreen:** Üretilen `.exe` imzasızdır. Uyarı çıkarsa **"Daha fazla bilgi" → "Yine de
> çalıştır"** deyin. İsterseniz kendi sertifikanızla imzalamak için `-Sign` (bkz. aşağıda).

## Manuel derleme

```powershell
git clone https://github.com/saidsurucu/ude-win-x64
cd ude-win-x64
.\build.ps1                 # tam yapı -> dist\UyapDokumanEditoru-<sürüm>.exe
```

Seçenekler:

| Komut | Açıklama |
|---|---|
| `.\build.ps1` | Tam yapı (araç temini + indirme + paketleme) |
| `.\build.ps1 -Icons` | Modern Material ikonlarla |
| `.\build.ps1 -Sign` | Üretilen EXE'yi `signtool` ile imzala |
| `.\build.ps1 -Only package` | Sadece paketleme fazı (tekrar derleme) |
| `$env:UDE_URL="..."; .\build.ps1` | Resmî paket linkini elle ver |

Çıktı: `dist\UyapDokumanEditoru-<sürüm>.exe`

## Nasıl çalışır?

`editor-app.jar` platformdan bağımsız tek bir "fat jar"dır (tüm bağımlılıklar gömülü).
Yapı onu olduğu gibi alır ve Java 11 runtime'ı ile `jpackage` üzerinden Windows `.exe`
kurulum dosyasına paketler:

1. **deps** — Temurin JDK 17 (jpackage için) + JDK 11 (gömülü runtime için) + WiX Toolset 3.x indirilir.
2. **download** — `uyap.gov.tr/Uyap-Editor` sayfasından resmî paket çekilir, `editor-app.jar` çıkarılır.
3. **patch** — Windows'ta yamaya gerek yoktur (bkz. aşağıda); yalnız `-Icons` verilirse ikonlar uygulanır.
4. **package** — JDK 11'den `jlink` ile minimal runtime üretilir, `jpackage --type exe` ile `.exe` kurulum dosyası oluşturulur.

### macOS portundan farklar

macOS ARM64 portundaki ([`ude-mac-arm64`](https://github.com/saidsurucu/ude-mac-arm64))
yamaların çoğu Windows'ta **gereksizdir**:

| macOS yaması | Windows'ta durum |
|---|---|
| sqlite-jdbc 3.7.2 → 3.46 | Gereksiz — jar zaten `native/Windows/amd64/sqlitejdbc.dll` içerir |
| `com.apple.eawt` strip + eawt-shim | Gereksiz — jar kendi `com.apple.eawt` sınıflarını gömülü getirir; Windows JDK'da çakışma yok, Mac kod yolu tetiklenmez |
| `PCSC.framework` path enjeksiyonu | Gereksiz — Windows yerleşik `winscard.dll` kullanır |
| ⌘-tuş remap, trackpad zoom | Gereksiz — Mac'e özgü |

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
