# SKIN tam port — Faz 7a: Şerit sadeleştirme + cetvel/kanvas renkleri (tasarım)

**Tarih:** 2026-06-25
**Yol haritası:** alt-proje #7 (tam SKIN), Faz 7a. Spike: [2026-06-25-skin-spike-findings.md](2026-06-25-skin-spike-findings.md).
**Durum:** Onaylandı (Codex incelemeli; otonom yürütme — kullanıcı her fazda Codex onayına devretti).

## Tam SKIN 4-faz ayrıştırması

- **7a (bu):** tam Flamingo şerit sadeleştirme + cetvel/marj/kanvas renkleri (açık+koyu, build-zamanı).
- **7b:** Word-stili widget'lar (Button/Tabs/Combo/Check/Field/Tooltip, PopupRemap, MenuMarks).
- **7c:** koyu-mod tamamlama (DarkPage koyu belge + IconDarken/ModeAwareImage koyu ikonlar).
- **7d:** canlı geçiş + renk-modu combo (WinLook `-javaagent`; ruler ZEMİN + tab font da burada).

## Amaç (7a)

Spike'ın trimli SkinPatch'ini, Mac'in tam şerit-sadeleştirme + cetvel/kanvas renk
yamalarıyla genişletmek. Office-2007 izlerini (grup kutuları, orb parıltısı, sekme-satırı
çizgisi, buton 3B dolguları, hızlı-erişim swoosh'u) kaldırıp düz modern şerit; teal
kanvas/cetvel → nötr (açık) / koyu gri. **Word widget'ları, DarkPage, agent HARİÇ.**
Paket adı `macosskin` KORUNUR (iç ad; yeniden adlandırma churn).

## Kapsam ve sertlik (Codex-güçlendirilmiş)

### A. Çekirdek Flamingo sadeleştirme — SERT (hata olursa build durur)
Görsel sözleşme; yarım uygulanırsa şerit spike'tan kötü görünür → fail-fast:
- `FlamingoUtilities.getBorderColor()` → tema grafiti (açık `#C6C6C6`, koyu `#4A4A4A`).
- `BasicRibbonUI.paintTaskArea` → boş (sekme-satırı tam-genişlik çizgisi).
- `BasicCommandButtonUI.paintButtonBackground` **İKİ overload** `(G,R)` ve
  `(G,R,ButtonModel...)` → durum dolguları (hover/seçili/basılı). **Codex: ikisini AYNI
  CtClass'tan al, ikisini de yamala, SONRA tek writeClass; gerekirse `defrost()`; alt
  sınıftan getMethod YAPMA (class-frozen tuzağı).**
- `BasicRibbonTaskToggleButtonUI.paintButtonBackground` → seçili sekme alt çubuğu
  (görünür yükseklik hizalaması: `parent.getHeight() - button.getY()`).
- `BasicRibbonApplicationMenuButtonUI.paintButtonBackground` → boş (orb parıltısı).
- `BasicRibbonBandUI.paintBandTitle` / `paintBandTitleBackground` + `$RoundBorder.paintBorder`
  → boş (spike'ta zaten var; çekirdeğe taşınır).

### B. Çevresel cila — BEST-EFFORT (try/catch, atlanırsa uyar)
- Orb menü popup kenarlıkları (`BasicRibbonApplicationMenuPopupPanelUI` anonim Border iç
  sınıfları $8/$9/$6/$7) → tek ince kontur / düz.
- `BasicRichTooltipPanelUI.paintBackground` → düz dolgu (tema).
- `BasicRibbonUI$TaskbarPanel.paintComponent` → boş (hızlı-erişim swoosh).
- Odak dikdörtgeni: obfuscate `a.b.a.a.t` FocusListener `focusGained` → boş (Win95 halkası).

### C. Cetvel/kanvas renkleri — AYRI KONTROL NOKTASI, BEST-EFFORT (obfuscate ad riski)
Codex: obfuscate ad/alan riski yüksek (`a/b/c`, statik `d/e`, ctor, FieldAccess) →
Flamingo'dan ayrı doğrula. Kanvas rengi BİRİNCİL mekanizma `wp.p.E` clinit force
(spike'ta var); `an` remap yalnız İKİNCİL göç-temizliği.
- `gui.eV` (IRuler): `a()/b()/c()` → koyu renk insertBefore; ctor'larda
  `setColor_border(canvasColor())`; FieldAccess statik `d/e` Color + `java.awt.Color.LIGHT_GRAY`
  → tema renkleri.
- `an` sınıfı: `wp.p.E`'ye FieldAccess writer → eski teal/gri RGB (-13854290 vb.) `canvasColor()`'a
  remap (best-effort; Windows taze kurulumda no-op olabilir — zararsız).
- `wp.p.E` clinit force → `canvasColor()` (spike'ta var; BİRİNCİL).
- Cetvel renkleri Mac ölçümlü değerlerle KALIR (Codex: yeniden kalibrasyon 7a kapsamı
  değil; doğrulama = "teal gitti, kontrast kabul edilebilir").

## Yaklaşım (önce kopyala, sonra uyarla)

Mac SkinPatch bloklarını mevcut `SkinPatch.java`'ya BİREBİR taşı (spike'ın setSkin/aF.run
install sarmaları korunur — zaten Word install'ları içermiyor). **Plan ilk adımı:** Windows
jar'ında `javap` ile obfuscate hedefleri doğrula: `an`, `gui.eV` (a/b/c + setColor_border +
statik d/e), odak sınıfı `a.b.a.a.t`, Flamingo sınıfları/overload'ları. Eksik/farklı olanı
çekirdekse araştır (sert), çevreselse best-effort atla.

## Doğrulama

1. Build (`-Skin`); çöküş yok; açık + koyu ekran görüntüsü.
2. **Şerit düz:** grup kutuları yok, orb parıltısı yok, buton hover'ı düz, sekme-satırı
   çizgisi yok, seçili sekmede mavi alt çubuk.
3. **Kanvas/cetvel:** teal gitti → açıkta nötr gri, koyuda koyu gri; cetvel okunur.
4. **PDF export** hâlâ geçerli PDF üretir (EDT nötrleştirme korunur).
5. Eksik-UIClass/exception taraması temiz (best-effort atlamalar log'da görünür).

## Kabul ölçütleri

1. `-Skin` build → düz modern şerit (açık+koyu), çöküş yok.
2. Çekirdek Flamingo yamaları uygulanır (sert; biri patlarsa build durur).
3. Cetvel/kanvas teal → nötr; PDF export çalışır.
4. `-Skin` olmadan → orijinal görünüm (rollback).

## Riskler

- **Obfuscate ad sapması (eV/an/odak):** Windows jar'ında farklıysa → best-effort atlanır
  (kontrol noktası C), çekirdek şerit yine düzleşir. Plan adım-1 yakalar.
- **İki-overload command button:** yanlış yamalama "class frozen" → tek CtClass + tek
  writeClass + defrost (Codex deseni).
- **Yarım sadeleştirme:** çekirdek sert olduğu için yarım kalmaz; yalnız çevresel cila
  (orb menü/tooltip/swoosh/focus) atlanabilir — tutarlı çekirdek korunur.
