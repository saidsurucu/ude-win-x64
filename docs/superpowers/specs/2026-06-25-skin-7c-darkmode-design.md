# SKIN tam port — Faz 7c: Koyu-mod tamamlama (koyu ikonlar + koyu sayfa) (tasarım)

**Tarih:** 2026-06-25
**Yol haritası:** alt-proje #7, Faz 7c (7a+7b TAMAM, main'de).
**Durum:** Onaylandı (Codex incelemeli; otonom yürütme).

## Amaç

Koyu modu görsel olarak tamamlamak: (1) **koyu ikonlar** (IconDarken + ModeAwareImage —
renkli/koyu ikonları koyu temada okunur kıl), (2) **koyu belge sayfası** (DarkPage —
opt-in, varsayılan kapalı; Mac varsayılanıyla aynı). Görünür ana kazanım koyu ikonlar.

## Bileşenler (3 sınıf, copy-verbatim-then-adapt)

- **IconDarken.java** (115, PURE): `apply(ImageIcon)` koyu/nötr ikonları açar; `scaleIcon(...)`
  ölçek yolu. Mode-aware (DarkMode.isDark()).
- **ModeAwareImage.java** (77, PURE): AbstractMultiResolutionImage alt sınıfı; aydınlatma
  PAINT anında moda göre (canlı geçiş için 7d hazır).
- **DarkPage.java** (286, 1 obfuscate ref `wp.p.E` = kanvas rengi, 7a'da yamalı): editör
  paint Graphics'ini HSL-açıklık çevirisiyle sarar (beyaz sayfa → #262626). **Varsayılan
  KAPALI** (pref `darkPageBackground`); etkinleştirme onay kutusu 7d agent'ında → 7c'de
  bağlı ama pref elle açılmadıkça atıl.

## Wiring

- SkinPatch (7a'da Mac'ten tam kopya) ZATEN içerir: (a) DarkPage kancası `text.hj.paint`
  insertBefore `$1 = DarkPage.wrap(this,$1)` (şu an "no such class" ile atlıyor → DarkPage
  eklenince aktif). (b) 7a'da ÇIKARDIĞIM IconDarken Utils bloğunu 7c GERİ EKLER: Utils.b/
  a(String)/a(String,int) insertAfter `$_ = IconDarken.apply($_)`; Utils.a(ImageIcon,II)
  insertBefore ModeAwareImage-duyarlı scaleIcon.
- 3 kaynağı apply-skin.ps1 derleme listesine ekle. `text.hj.paint` imzasını jar'da doğrula.

## Coupling (ICONS ↔ IconDarken, Utils.b) — Codex

ICONS IconLoaderPatch Utils.b'yi (multi-res) SkinPatch'ten ÖNCE yamalar (patch.ps1 sırası
ICONS→SKIN). SkinPatch IconDarken aynı Utils.b'ye İKİNCİ insertAfter ekler → zincir:
orijinal → multi-res (ICONS) → IconDarken.apply (SKIN). Tek-atışlı (taze indirme/build →
istif yok). **Doğrulama probe'u:** Utils.b ile bilinen ikon yükle (both flags), dış katman
ModeAwareImage + iç varyant BaseMultiResolutionImage mı; koyu modda piksel değişiyor mu.

## Bayrak matrisi (Codex — `-Skin`'i `-Icons`'a BAĞLAMA)

`Skin+Icons` (tipik), `Skin only`, `Icons only`, `neither` test edilir. `SKIN=1 ICONS=0`:
IconDarken multi-res-OLMAYAN ikonu sarar → retina kaybı, **çökme DEĞİL**. ModeAwareImage
düz ikonu güvenli sarmalı (multi-res VARSAYMAMALI). Çökerse: ModeAwareImage'i düzelt,
bayrakları bağlama.

## DarkPage atıllık + Windows Java2D (Codex)

- **Atıl kanıtı:** pref yok/false iken `DarkPage.wrap` orijinal Graphics'i döndürmeli
  (sıfıra yakın yük). Hep sarıyorsa → 7d'ye ertele. `wrap()` erken-dönüşü doğrulanır.
- **PDF/baskı:** `isPaintingForPrint` SARILMAZ (Mac notu) → PDF/baskı etkilenmez. DarkPage
  AÇIK ve KAPALI iken PDF export geçerli PDF üretmeli.
- **Java2D:** "koyu görünüyor"dan fazlası — chrome'a (şerit/cetvel) renk çevirisi SIZMAMALI
  (yalnız belge kanvası), metin AA/seçim/caret okunur, `-Dsun.java2d.d3d=false` ile de çalış.

## Doğrulama

1. **Probe:** Utils.b ikonu ModeAwareImage(BaseMultiResolutionImage) sarıyor; bilinen ikon
   (yeşil ekle #107C41) koyu modda açılmış (~#30B86D, toleranslı piksel örneği).
2. **Bayrak matrisi:** Skin+Icons, Skin-only çöküşsüz çalışır.
3. **Koyu ikon ss:** koyu modda şerit ikonları okunur (açılmış).
4. **DarkPage:** pref açıkken belge sayfası koyu, chrome'a sızma yok; pref kapalıyken beyaz
   (varsayılan); her iki durumda PDF export geçerli.

## Kabul ölçütleri

1. `-Icons -Skin` koyu modda → ikonlar okunur (açılmış), çöküş yok.
2. Probe: ModeAwareImage(multi-res) + piksel örneği geçer.
3. DarkPage pref açık → koyu sayfa (chrome temiz); kapalı → beyaz; PDF her durumda geçerli.
4. `-Skin` (ICONS'suz) koyu modda çökmez (graceful).

## Riskler

- **Utils.b zincir sırası/istif:** taze build tek-atışlı → istif yok; probe doğrular.
- **Bayrak bağımlılığı:** ModeAwareImage düz ikonu sarmazsa SKIN-only çöker → düzelt.
- **DarkPage chrome sızması/PDF:** wrap yalnız hj.paint'i sarar, isPaintingForPrint hariç →
  doğrulama 4 kontrol eder.
