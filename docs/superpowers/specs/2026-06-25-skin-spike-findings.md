# SKIN Fizibilite Spike — Bulgular

**Tarih:** 2026-06-25  **Dal:** `spike/skin-feasibility` (merge edilmez)
**Spec:** [2026-06-25-skin-feasibility-spike-design.md](2026-06-25-skin-feasibility-spike-design.md)

## Karar: **GİT (GO)** — mimari engel yok; kalan iş kalibrasyon/ek-yama.

Substance düz skin (light + dark) Windows'ta kuruluyor ve modern düz şeridi render
ediyor. Tüm yama hedefleri (Substance `setSkin`, obfuscate `aF.run` açılış kancası,
`wp.p`, EDT denetimleri, Flamingo `BasicRibbonBandUI` delegate'leri) Windows jar'ında
çözüldü ve yamalandı. Aqua-delegate engeli put'ları düşürerek aşıldı; koyu palet
`appearance=system` olmadan Substance'tan geliyor.

## Risk sonuçları

| Risk | Sonuç | Kanıt |
|---|---|---|
| Substance skin Windows'ta kurulur/render eder | **EVET** | `skin-light2.png` — düz şerit, düz Substance butonları |
| Koyu palet (`appearance=system` YOK) | **EVET** | `skin-dark.png` — şerit/krom koyu, Substance'tan |
| Kayıt defteri koyu-mod tespiti | **EVET** | `DarkMode.isDark()` registry `AppsUseLightTheme` ile tutarlı; pref override (dark/light/system) çalışıyor |
| Aqua put'ları düşürüldü → Substance scrollbar | **EVET** | başlangıç trace'inde HATA yok; eksik-UIClass yok (Aqua referansı kalmadı) |
| PDF export (EDT nötrleştirme) | **EVET** | elle doğrulandı — SKIN açıkken "PDF Olarak Kaydet" geçerli PDF üretti (0-bayt/spinner yok) |
| Flamingo kanaryası (yama yolu açık) | **EVET** | build: `FLAMINGO KANARYASI ... (yol ACIK)`; grup başlık kutuları kalktı |
| Başlangıç sırası (skin UI'dan önce) | **EVET** | trace: `ACILIS skin kuruldu dark=...` (aF.run, ilk frame'den önce) |
| Substance API jar'da mevcut | **EVET** | yardımcılar jar'a karşı `--release 11` derlendi (NebulaSkin, painter'lar, bundle, FontSet) |

## Mac kuplajı → Windows ikamesi

| Mac | Windows ikamesi | Durum |
|---|---|---|
| `com.apple.laf.Aqua{ScrollBar,Slider}UI` put | **DÜŞÜRÜLDÜ** (Substance default scrollbar) | çalışıyor |
| `defaults read -g AppleInterfaceStyle` | `reg query ...AppsUseLightTheme` (0=koyu,1=açık) | çalışıyor |
| `-Dapple.awt.application.appearance=system` | (yok; koyu palet tamamen Substance'tan) | çalışıyor |
| `Helvetica Neue` | `Segoe UI` | çalışıyor |
| `Preferences` düğümü `ude-mac-arm` | `ude-win` | çalışıyor |
| trace `/tmp/skinpatch-trace.log` | `%TEMP%\skinpatch-trace.log` | çalışıyor |
| MacLook agent (pencere kromu) | (Windows'ta gerekmez; krom doğal) | n/a |

## Bilinen sınırlar / spike'ta çıkanlar

1. **Teal kanvas/cetvel (kozmetik).** Light modda cetvel + sol marj bandı hâlâ teal
   (eski `wp.p.E` = teal(44,153,174)). Spike'ın tek `wp.p.E` clinit yaması yetmiyor;
   Mac tam-portu ayrıca cetvel (`eV`/IRuler) renkleri + marj remap'i yamalıyor. Tam
   portta gelecek.
2. **Koyu mod belge sayfası beyaz.** Şerit/krom koyu ama belge kanvası beyaz kalıyor —
   `DarkPage` (koyu belge arkaplanı) spike kapsamı dışı. Tam-port işi.
3. **İkonlar koyuda aydınlatılmıyor.** `IconDarken`/`ModeAwareImage` spike'ta yok →
   koyu modda bazı ikonlar sırıtabilir. ICONS alt-projesiyle koordineli tam-port işi.
4. **Build kilidi (ortam, kod değil).** Eski kurulu/installer süreçleri
   `dist\UyapDokumanEditoru-5.4.17.exe`'yi kilitleyince jpackage AccessDenied verir;
   farklı sürümle (`UDE_VERSION=5.4.18`) temiz `.exe` üretildi → jpackage sağlıklı.
5. **jar kilidi.** Doğrudan jar'dan başlatılan app instance'ı `editor-app.jar`'ı açık
   tutar → `download` overwrite edemez. Çözüm: build öncesi `java` süreçlerini kapat.

## Tam SKIN portu (#7) taslak görev listesi

- [ ] `macosskin` → `com.udewin.skin` yeniden adlandırma (resource yolları
  `/com/udewin/skin/*.colorschemes` dahil).
- [ ] Tam Flamingo sadeleştirme: `getBorderColor`, `BasicRibbonUI.paintTaskArea`,
  `BasicRibbonTaskToggleButtonUI` seçili sekme alt çubuğu, `BasicCommandButtonUI`
  buton dolguları (iki overload!), orb (`BasicRibbonApplicationMenuButtonUI`), zengin
  tooltip, hızlı erişim `TaskbarPanel`.
- [ ] Word* widget'ları: `WordButton/WordTabs/WordCombo/WordCheck/WordField/WordTooltip`
  (+ combo type-ahead düzeltmesi).
- [ ] Kanvas/cetvel renkleri: `wp.p.E` (zaten), `eV`/IRuler getter'ları, marj remap
  (teal/LIGHT_GRAY → nötr); koyu/açık palet kalibrasyonu (Word-Windows piksel ölçümü).
- [ ] Koyu mod: `DarkPage` (belge arkaplanı), `IconDarken` + `ModeAwareImage` (koyu
  ikonlar — ICONS ile koordine), `MenuMarks`, `PopupRemap`, `FlatEtchedBorder`.
- [ ] Renk-modu combo + canlı geçiş (`ModeSwitch`): **WinLook runtime agent gerekir**
  (`-javaagent` altyapısı — sub-proje 1'de ertelenen mekanizma; ribbon modelinden combo
  ekleme + canlı setSkin/updateUI). Karar: jpackage `--java-options -javaagent:$APPDIR/...`.
- [ ] Substance versiyon/sürpriz taraması: tam portta her Flamingo/Substance delegate
  imzasını Windows jar'ında `javap` ile doğrula (kanarya yalnız "yol açık" dedi, "tamamı
  çalışır" demedi).

## Ekran görüntüleri

- `scratchpad/skin-light2.png` — light modda düz modern şerit.
- `scratchpad/skin-dark.png` — koyu modda koyu şerit/krom.
(Atılabilir dalda; gerekirse `docs/.../assets/`'e taşınır.)
