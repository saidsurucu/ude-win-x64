# Alt-proje 2: SKIN fizibilite spike'ı (tasarım)

**Tarih:** 2026-06-25
**Yol haritası:** [2026-06-25-mac-feature-port-roadmap-design.md](2026-06-25-mac-feature-port-roadmap-design.md) — alt-proje #2
**Durum:** Onaylandı; Codex (codex-cli) ile çapraz incelendi.

## Amaç

En büyük ve en riskli alt-proje olan **SKIN** (modern düz görünüm, light + dark) için
**tam portu yazmadan önce** Windows'ta çalışıp çalışmayacağını kanıtlamak. Spike bir
**araştırmadır, sevk edilen özellik değildir**: atılabilir bir dalda yapılır, çıktısı
git/gitme kararı + tam SKIN portu için taslak plandır.

Kullanıcı kararı: **dark mode kapsamda** (light + dark). Bu yüzden spike koyu-mod
kayıt-defteri tespitini ve koyu palet render'ını da doğrulamalıdır.

## Çözülecek dört risk (öncelik sırasına göre)

1. **Aqua-delegate engeli.** Mac `SkinPatch`, skin'i kurduktan hemen sonra şunu yapar:
   ```
   UIManager.put("ScrollBarUI", "com.apple.laf.AquaScrollBarUI");
   UIManager.put("SliderUI",    "com.apple.laf.AquaSliderUI");
   ```
   Bu `com.apple.laf.*` sınıfları Windows'ta **YOKTUR** → UIManager UI sınıfını
   yükleyemez. **Karar (Codex): put'ları DÜŞÜR** — Substance kendi scrollbar/slider'ını
   çizsin. `WindowsScrollBarUI` bağlamak DAHA riskli (yerel Windows delegate'leri
   Substance'ın sağlamadığı UIDefault/painter/border varsayar — Mac hilesinin tersi).
2. **Düz Substance skin Windows'ta kuruluyor + render ediliyor mu?** `SkinPatch`'in
   skin-kurulum çekirdeğini (`setSkin(String)` + `aF.run` sarması) + `FlatUdeSkin`/
   `FlatUdeDarkSkin` + iki `.colorschemes` + `FlatFontPolicy` portla, derle, başlat,
   ekran görüntüsü al.
3. **Windows kayıt-defteri koyu-mod tespiti.** `DarkMode.java`'nın `defaults read -g
   AppleInterfaceStyle` çağrısını şununla değiştir:
   `HKCU\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize\AppsUseLightTheme`
   (DWORD: 0=koyu, 1=açık; anahtar yoksa açık varsay). Ayrıca `java.util.prefs` düğümü
   `ude-mac-arm` → `ude-win`; trace log `/tmp/...` → `%TEMP%`.
4. **`appearance=system` olmadan koyu palet.** jpackage `-Dapple.awt.application.appearance=
   system` java-option'ı **yalnız Mac** (yerel AWT bileşenlerini koyulaştırır). Windows'ta
   koyu palet TAMAMEN Substance skin'inden gelmeli. Spike koyu modun kabul edilebilir
   render ettiğini doğrulamalı (koyu ekran görüntüsü).

## KRİTİK: PDF dışa aktarımı EDT ihlali (Codex'in yakaladığı, Mac'e özgü DEĞİL)

Mac `SkinPatch`, `SubstanceCoreUtilities.testComponentCreation`/
`testStateChangeThreadingViolation` + `LafWidgetUtilities.testComponentStateChange
ThreadingViolation` gövdelerini boşaltır. Neden: UDE "PDF Olarak Kaydet"i bileşenleri
**EDT-dışı** (SwingWorker) kurar; Substance global LAF iken bu `UiThreadingViolation
Exception` fırlatır → 0-bayt PDF + boş hata penceresi. Bu **Substance+UDE etkileşimi**,
platforma özgü değil → **Windows'ta da SKIN açıkken PDF export'u bozar.** Dolayısıyla
bu nötrleştirme skin-kurulum **çekirdeğinin parçasıdır** (krom değil); spike onu
içermeli VE PDF export'un hâlâ çalıştığını doğrulamalı.

## Spike kapsamı (minimal dikey dilim + Flamingo kanaryası)

**Portlanan (spike çekirdeği):**
- `DarkMode.java` → kayıt defteri (bağımsız, birim-test edilebilir registry okuma).
- `SkinPatch` KIRPILMIŞ: skin-kurulum çekirdeği (`setSkin` + `aF.run` sarması),
  **Aqua put'ları ÇIKARILMIŞ**, **EDT-ihlali nötrleştirmesi EKLENMİŞ**.
- `FlatUdeSkin` + `FlatUdeDarkSkin` + iki `.colorschemes` + `FlatFontPolicy`.
- **Flamingo kanaryası:** tam şerit düzleştirmesi DEĞİL — yalnız BİR bilinen Flamingo
  delegate'ini (ör. `BasicRibbonBandUI.paintBandTitleBackground` → boş) yamala ki
  Flamingo yama yolunun Windows'ta çalıştığı (obfuscate/sürüm sürprizi yok)
  doğrulansın.

**Spike'ta ATLANAN (tam port işi):** Word-stili widget'lar (Button/Tabs/Combo/Check/
Field/Tooltip), PopupRemap, MenuMarks, IconDarken, ModeAwareImage, ModeSwitch canlı
geçiş, DarkPage, tam Flamingo şerit düzleştirmesi ve tüm MacLook/WinLook agent'ı.

## Doğrulama / ölçümler

1. **Skin kurulumu:** uygulama açık skin ile başlar, çöküş yok; light + dark ekran
   görüntüsü.
2. **PDF export:** SKIN açıkken "PDF Olarak Kaydet" geçerli (0-bayt değil) PDF üretir.
3. **Başlangıç sırası:** skin'in ilk frame/ribbon oluşturulmadan ÖNCE kurulduğunu
   doğrulayan log satırı; hem tercih-yok hem `menuTheme`-dolu yollarında.
4. **HiDPI:** %100, %125, %150 ölçeklemede render.
5. **Font metrikleri:** Türkçe metin, şerit etiketleri, menü öğeleri, diyaloglar.
6. **Koyu render kapsamı:** devre dışı kontroller, tablo/ağaç, diyaloglar, dosya
   seçici, popup, tooltip.
7. **UIDefault hataları:** Aqua put'ları düşürüldükten sonra eksik UI-sınıfı/painter
   hatası için log taraması.
8. **Performans:** başlangıç süresi farkı (skin açık/kapalı).

## Çıktı (deliverable)

`docs/superpowers/specs/2026-06-25-skin-spike-findings.md`:
- **Git/Gitme** kararı.
- Her Mac kuplajı → seçilen Windows ikamesi tablosu.
- Light + dark ekran görüntüleri + PDF export sonucu.
- Yukarıdaki ölçümlerin bulguları.
- Tam SKIN portu (alt-proje #7) için taslak görev listesi (hangi widget'lar, agent
  gerekli mi, dark palet kalibrasyonu, vb.).

Spike kodu **atılabilir bir dalda** yaşar (`spike/skin-feasibility`); merge EDİLMEZ.
Tam port (#7) bu bulgularla yeni bir brainstorm→spec→plan döngüsü olarak başlar.

## Vehicle gerekçesi (Codex Q4)

Bilinmeyenler mimari (Substance Windows'ta, koyu palet kalitesi, başlangıç-sırası,
Flamingo yamalanabilirliği). Atılabilir spike doğru araç; "default-off gerçek port"
yarı-port kodu biriktirme riski taşır. Dal atılabilir kalır ama bulgular tam-port
kontrol-listesine dönüşecek biçimde yazılır.

## Riskler

- **Substance Windows'ta görsel regresyon:** LAF metrikleri/font/HiDPI macOS'tan
  ayrışabilir — sadece derleme değil görsel sorun bekle (ölçümler bunu yakalar).
- **Flamingo kanaryası yetersiz sinyal:** tek delegate çalışsa da tam düzleştirme
  başka delegate'lerde sürpriz çıkarabilir; kanarya yalnız "yol açık mı" der, "tamamı
  çalışır" demez (findings'te açıkça belirtilir — sessiz kapsam yok).
- **Başlangıç-sırası farkı:** Windows fallback LAF'ı Aqua değil (Metal/Windows);
  zorla-kurulum hook'unun hâlâ gerekli olduğu doğrulanmalı (spike ölçüm #3).
