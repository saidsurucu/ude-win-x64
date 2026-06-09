# UDE Windows x64 Port — Design Spec

**Tarih:** 2026-06-09
**Durum:** Onaylandı (brainstorming)
**Kaynak:** [`saidsurucu/ude-mac-arm64`](https://github.com/saidsurucu/ude-mac-arm64) macOS ARM64 build sistemi

## Amaç

UDE (Uyap Doküman Editörü — T.C. Adalet Bakanlığı Java 8 masaüstü uygulaması) için
**gayriresmî**, bağımsız bir Windows x64 build sistemi. Repo UDE kaynak kodunu içermez;
resmî x86_64 paketi build sırasında indirilir ve üzerine yama uygulanır.

macOS sürümüyle **aynı 4 değer özelliğini** Windows'a taşır:

1. **Gömülü Java 11 runtime** — ayrıca Java kurmaya gerek yok
2. **Keskin metin (HiDPI/4K)** — Java 8'in bulanık render'ı yerine Java 11 (JEP 263)
3. **Modern Material Design ikonlar**
4. **E-imza / akıllı kart** desteği (AKİS Windows sürücüsü ile)

macOS'a özgü hack'ler **düşürülür**: `eawt-shim`, `PCSC.framework` path enjeksiyonu,
⌘-tuş remap'i, trackpad zoom. Windows portu bu sayede Mac portundan **daha basittir**.

## Kararlar

| Konu | Karar |
|---|---|
| Build dili | PowerShell native (bash yok) |
| Çıktı | `jpackage --type exe` installer (WiX Toolset 3.x gerektirir) |
| Dosya ilişkilendirme | `.udf` → installer otomatik kaydeder |
| Kurulum | Tek satırlık `kur.ps1` bootstrap (`irm ...` &#124; `iex`) |
| Kod imzalama | Varsayılan kapalı; opsiyonel `signtool` hook'u |

## Mimari

### Repo yapısı

```
ude-win-x64/
├─ kur.ps1                  # tek satırlık bootstrap (irm | iex)
├─ build.ps1               # ince giriş → scripts/build.ps1
├─ scripts/
│  ├─ build.ps1            # ana orkestratör (build.sh analoğu)
│  ├─ deps.ps1            # Zulu 11 (win-x64) + JDK 17 (jpackage) + WiX 3.x + sqlite-jdbc indir
│  ├─ download.ps1        # resmî UDE win paketini çek/aç (UDE_URL env override)
│  ├─ patch.ps1           # editor-app.jar patch (sqlite swap, ikonlar)
│  ├─ package.ps1         # jpackage --type exe, .udf ilişkilendirme
│  └─ icons/              # Material ikon override'ları + Javassist patcher
├─ .github/workflows/     # CI: windows-latest build doğrulama
├─ README.md
└─ LICENSE
```

### Build fazları (PowerShell fonksiyonları — Makefile target paritesi)

1. **check-deps** — java / jpackage / WiX (`candle`+`light`) bul/doğrula
2. **provision** — Zulu JDK 11 win-x64 (runtime) + JDK 17+ (jpackage) + WiX 3.x indir
3. **download** — resmî UDE Windows paketini `UDE_URL` ile çek, `editor-app.jar` + lib'leri bul
4. **deps** — sqlite-jdbc 3.46.x indir (win-x64 native doğrula)
5. **patch** — sqlite swap + (`ICONS=1` ise) Javassist `BaseMultiResolutionImage` ikon köprüsü
6. **package** — `jpackage --type exe --file-associations udf.properties --win-menu --win-shortcut --icon ude.ico` + jlink'lenmiş Zulu 11 runtime
7. **sign** (opsiyonel, varsayılan kapalı) — `signtool`
8. **çıktı** — `dist\UDE-<sürüm>-windows-x64.exe`

## Özellik implementasyonu

| Özellik | Windows implementasyonu | Mac'ten fark |
|---|---|---|
| Gömülü Java 11 | `jlink` ile Zulu 11 win-x64 minimal runtime, `jpackage --runtime-image` | Sadece win-x64 binary |
| Keskin metin (HiDPI) | Java 9+ Windows per-monitor DPI ölçeklemeyi otomatik yapar; launcher exe `dpiAware`. Fallback: `--java-options -Dsun.java2d.uiScale` | Java 8→11 geçişi çözer, flag genelde gerekmez |
| Modern ikonlar | Javassist patch platform-bağımsız, aynen taşınır (`ICONS=1`) | Fark yok |
| E-imza / akıllı kart | `javax.smartcardio` → `winscard.dll` (System32, hep PATH'te) otomatik. **Build hack YOK.** Kullanıcı AKİS Windows sürücüsünü kurar | PCSC.framework path enjeksiyonu tamamen düşer |
| .udf çift-tık | `jpackage --file-associations`; çift-tıkta dosya yolu exe'ye argv olarak gelir, UDE main `args[0]`'ı açar | eawt-shim düşer |
| SQLite swap | **Opsiyonel** — eski 3.7.2 zaten win-x64 native içerir. Paritede 3.46.x'e güncellenir, sorun çıkarsa atlanabilir | ARM64'te zorunluydu, Windows'ta değil |

## Doğrulanacak riskler

Gerçek paket görülünce netleşecek açık noktalar:

1. **UDE main argv okuyor mu?** — main class'ı bul, çift-tıkta `args[0]`'ı `.udf` olarak açtığını doğrula (orijinal Windows UDE çift-tığı desteklediği için muhtemelen hazır).
2. **`com.apple.eawt` referansı** — jar'ı tara; varsa strip et (Java 11'de `NoClassDefFound` olmasın).
3. **Java 11 + Java 8 bytecode uyumu** — smoke testte doğrula (Mac tarafı kanıtladı).
4. **HiDPI gerçek 4K ekranda** — auto-scaling çalışmazsa `uiScale` flag'i ekle.
5. **WiX 3.x temini** — `kur.ps1` otomatik indirir; kapalı ortam senaryosunu not et.

## Test

- **CI (GitHub Actions, windows-latest):** build EXE üretiyor mu, jar patch'leri uygulanıyor mu, jpackage başarılı mı (GUI'siz smoke).
- **Manuel:** kur → `.udf` çift-tık açılıyor mu, metin net mi, ikonlar modern mi, kart takılınca e-imza okuyor mu.

## Kapsam dışı (YAGNI)

- ⌘/Ctrl tuş remap'i (Ctrl Windows'ta zaten varsayılan)
- Trackpad/gesture zoom (Windows'ta Ctrl+wheel konvansiyonu yeterli, eklenmez)
- Kod imzalama sertifikası (self-build modeli, SmartScreen uyarısı kabul edilir)
- Taşınabilir/MSI çıktı (v1 yalnızca EXE installer)
