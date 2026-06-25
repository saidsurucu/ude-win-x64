# SKIN tam port — Faz 7b: Word-stili widget'lar (tasarım)

**Tarih:** 2026-06-25
**Yol haritası:** alt-proje #7, Faz 7b (7a şerit+kanvas TAMAM, main'de).
**Durum:** Onaylandı (Codex incelemeli; otonom yürütme).

## Amaç

Diyalog/kontrol widget'larını (buton, sekme, combo, checkbox/radio, metin alanı,
tooltip, popup, menü işaretleri) Word-stili düz Substance/Basic delegate'leriyle
değiştirmek. Bul/Değiştir gibi diyaloglardaki gri 3B butonlar + kutulu sekmeler düzlenir.

## Bileşenler (9 yardımcı, hepsi PURE — obfuscate UDE referansı YOK, doğrulandı)

`macosskin/` altına: WordButton (düz yuvarlak buton, varsayılan-buton mavi vurgu),
WordTabs (düz sekme, seçili = mavi alt çubuk), WordCombo (yuvarlak combo + **type-ahead
düzeltmesi**), WordCheck (vektör onay işaretli checkbox/radio + menü varyantları),
WordField (Basic text field delegate), WordTooltip (Basic tooltip), PopupRemap,
MenuMarks, FlatEtchedBorder.

Teslim: her Word* statik `install()` → `UIManager.put("XxxUI","macosskin.WordXxx$UI")`.
7a SkinPatch zaten PopupRemap/MenuMarks/FlatEtchedBorder bloklarını içerir (şu an
best-effort "no such class" ile atlanıyor) → sınıflar eklenince OTOMATİK aktifleşir.
6 install() çağrısı (WordTooltip/Combo/Check/Button/Tabs/Field) 7a'da wrap'lerden
çıkarılmıştı → 7b geri ekler.

## Yaklaşım (önce kopyala, sonra uyarla)

9 sınıfı Mac'ten BİREBİR kopyala (obfuscate refs yok → Substance API'ye karşı `--release 11`
derlenmeli, uyarlama beklenmiyor). 6 install() satırını iki wrap'e (setSkin + aF.run)
geri ekle. apply-skin.ps1 javac listesine 9 kaynağı ekle. Kırılırsa uyarla.

## Doğrulama (Codex-güçlendirilmiş — sadece ss DEĞİL, runtime delegate kanıtı)

**A. Delegate kayıt kanıtı (KRİTİK):** skin kurulup install()'lar çağrıldıktan SONRA
`UIManager.get` 6 anahtar için bizim delegate'imizi döndürmeli (Substance defaults'u
ezmediğini kanıtlar):
```
ButtonUI -> macosskin.WordButton$...   ComboBoxUI -> macosskin.WordCombo$...
CheckBoxUI -> macosskin.WordCheck$...   TabbedPaneUI -> macosskin.WordTabs$...
TextFieldUI -> macosskin.WordField$...  ToolTipUI -> ... (Basic + put'lar)
```
Standalone probe (setSkin + install + UIManager.get yazdır) ile kanıtla.
**B. Gerçek-app kanıtı:** Bul/Değiştir diyaloğu ss (düz buton/sekme/field), font combo ss
(yuvarlak + type-ahead), Biçim sekmesi checkbox ss, tooltip — açık + koyu. Diyalog düz
görünüyorsa delegate gerçek-app'te de tuttu.
**C. WordCombo type-ahead davranışı (en yüksek girdi-riski):** popup KAPALI yazma (stok),
popup AÇIK yazma (yalnız vurgu taşır, commit yok), Enter/Esc, editable vs non-editable.
**D. İdempotans:** install() yalnız `UIManager.put` yapar (idempotent churn); sert tek-sefer
guard YOK (7d canlı-geçişte yeniden-kurulum gerekecek) — Codex.
**E. Doğrulama sırası (Codex):** önce 6 put delegate tutuyor mu → sonra WordCombo/WordCheck
→ sonra popup/menü remap.

## Kabul ölçütleri

1. `-Skin` build → diyaloglarda düz Word butonları/sekmeleri/field'ları (açık+koyu).
2. Delegate kayıt probe'u: 6 anahtar bizim delegate'imizi döndürür.
3. Checkbox/radio vektör işaretli (retina'da keskin), combo yuvarlak + type-ahead doğru.
4. PopupRemap/MenuMarks/FlatEtchedBorder blokları artık "atlandı" demez (aktif).
5. `-Skin` olmadan → stok widget'lar (rollback).

## Riskler

- **LAF sırası:** Substance install sonrası UIDefaults'u tazelerse delegate'ler düşebilir →
  doğrulama A yakalar; düşerse install'ı geç noktaya taşı/yeniden çağır.
- **type-ahead girdi davranışı:** doğrulama C ile elle test; bozarsa WordCombo'yu type-ahead'siz
  sürümle ver (paint korunur).
- **Derleme:** beklenmiyor (pure); kırılırsa copy-then-adapt ile düzelt.
