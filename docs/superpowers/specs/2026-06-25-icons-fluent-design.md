# Alt-proje 3: ICONS — Material → Fluent (tasarım)

**Tarih:** 2026-06-25
**Yol haritası:** [2026-06-25-mac-feature-port-roadmap-design.md](2026-06-25-mac-feature-port-roadmap-design.md) — alt-proje #3
**Durum:** Onaylandı; Codex (codex-cli) ile çapraz incelendi.

## Amaç

Windows yapısındaki mevcut **Material** ikon setini Mac portundaki **Fluent UI**
setiyle değiştirmek (fonksiyonel renk) ve daha güçlü retina-keskinlik yamasını
getirmek. Mac PNG'leri commit'li → doğrudan kopyalanır (regenerate gerekmez).

## Mevcut durum

- **Windows (Material):** `scripts/icons/overrides/resources/` altında 647 PNG
  (`resource.png` + `resource@2x.png`), 97-satır `IconLoaderPatch.java` (yalnız
  `Utils.b` multi-res + WebLaF disabled saydamlık), `apply-icons.ps1`. Bayrak:
  `-Icons` (manuel build'de opt-in; `kur.ps1`'de varsayılan açık).
- **Mac (Fluent):** 653 PNG (Fluent + fonksiyonel renk: gövde `#444444`, sil
  `#D13438`, ekle `#107C41`, belge `#2B7CD3`, vurgu `#D9A21B`), **daha zengin**
  133-satır `IconLoaderPatch.java`, `fluent/generate.py` + `fluent/mapping.tsv`.

## Mac IconLoaderPatch (Win'den fazlası; hepsi platform-bağımsız Swing/Flamingo)

1. **`Utils.b(String)`** → `@2x` eşi varsa `BaseMultiResolutionImage` (retina). **Sert
   gereksinim** (hata olursa fırlat) — Win'de zaten var, kanıtlı.
2. **`Utils.a(ImageIcon,int,int)`** ölçek düzeltmesi: `getScaledInstance` multi-res'i
   öldürüyor → hızlı erişim ikonları bloklu; varyant-başına ölçekleme retina'yı
   korur. (Win'de YOK.)
3. **Flamingo `ImageWrapperIcon.paintIcon`**: 1x raster+cache yerine doğrudan bicubic
   `drawImage` (@2x varyant seçimi korunur). (Win'de YOK.)
4. **Flamingo `FilteredResizableIcon.paintIcon`**: 1x raster + `ColorConvertOp(CS_GRAY)`
   yerine delegate doğrudan `AlphaComposite 0.38` ile çizilir (keskin soluk disabled
   ikon). (Win'de YOK.)

**Codex notu (folded):** 2–4 maddeleri **best-effort** (try/catch) olmalı; ilgili
overload/sınıf Windows jar'ında farklıysa ICONS yaması TÜMüyle çökmesin — yalnız o
madde atlansın. Madde 1 sert kalır.

## Tasarım

1. **Asset değişimi:** 647 Material PNG SİLİNİR, 653 Fluent PNG Mac deposundan birebir
   kopyalanır. Mac overrides "KEEP" kararlarını içerir (UYAP `certificate*` kırmızı
   kurdeleli rozetler + orb/logo overrides'ta YOK → jar orijinali görünür).
2. **Patcher yükseltme:** `IconLoaderPatch.java` Mac 133-satır sürümüyle değiştirilir
   (2–4 best-effort). `apply-icons.ps1` DEĞİŞMEZ (patcher'ın hangi metodu yamaladığından
   bağımsız).
3. **Kaynak-doğruluk:** `fluent/mapping.tsv` + `generate.py` referans olarak getirilir
   (commit'li; **çevrimdışı build'in parçası DEĞİL**).
4. **Kapsam sınırı:** koyu-mod ikon aydınlatma (`IconDarken`/`ModeAwareImage`) HARİÇ —
   SKIN'e bağlı, SKIN #7 kontrol-listesinde. ICONS yalnız açık Fluent set + retina.
5. **Bayrak:** `-Icons` (değişmez).

## Doğrulama (Codex-güçlendirilmiş)

**A. Asset parite kontrolü (commit'ten ÖNCE):**
- Material vs Fluent **dosya adı kümeleri** karşılaştırılır (sadece sayı değil):
  `Compare-Object (eski basenames) (yeni basenames)`.
- Her `@2x`-olmayan PNG'nin `@2x` eşi var mı (bilinen istisnalar hariç: `search`).
- Fluent adları gerçek **jar resource adları** + `mapping.tsv` `KEEP` girdileriyle
  çapraz kontrol (override'sız bırakılan ya da yetim olan ikon yok).

**B. Patcher imza doğrulama (yamadan ÖNCE):** Windows jar'ında `javap` ile:
- `Utils.b(String)` → `ImageIcon` (madde 1, sert)
- `Utils.a(ImageIcon,int,int)` overload (madde 2)
- Flamingo `ImageWrapperIcon` / `FilteredResizableIcon` (madde 3–4)
Eksik olan madde best-effort try/catch ile atlanır (build çökmez).

**C. HiDPI ölçümü:** `-Dsun.java2d.uiScale=` 1 / 1.25 / 1.5 / 2 ile başlat; araç
çubuğu, hızlı erişim, Flamingo şerit, disabled ikonlar yakala. Kontrol: 200%'de `@2x`
GERÇEKTEN seçiliyor, 1x-büyütme bulanıklığı yok, kırpılma yok, disabled okunur.
Fraksiyonel (125/150%) dahil — Windows yumuşamayı orada gösterir.

**D. Asset hijyeni:** jar boyut farkı makul; `jar uf` sonrası **çift entry yok**; PNG
boyutları önceki mantıksal boyutlarla uyumlu.

**E. SKIN etkileşimi:** ICONS Flamingo `ImageWrapperIcon`/`FilteredResizableIcon`
yamalar; SKIN `BasicRibbonBandUI` vb. → **ayrı sınıflar, çakışma yok**. patch.ps1'de
ICONS, SKIN'den ÖNCE çalışır; ortak sınıf yamalanmadığı doğrulanır. **Bilinen
sınır:** `-Icons` + **koyu** SKIN → ikonlar aydınlatılmadığından kontrast düşük;
SKIN #7'ye ertelenir (dokümante edilir).

## Kabul ölçütleri

1. `-Icons` ile build → Fluent ikonlar üretilen `.exe`'de görünür (araç çubuğu,
   şerit, menüler).
2. Asset parite kontrolü temiz (yetim/eksik override yok).
3. HiDPI %100/%125/%150/%200'de ikonlar keskin (bloklu değil), disabled okunur.
4. Patcher imza doğrulaması: madde 1 uygulanır; 2–4 ya uygulanır ya da best-effort
   atlanır (build çökmez).
5. `-Icons` olmadan build → orijinal UDE ikonları (rollback).

## Riskler

- **Dosya adı sapması:** Material↔Fluent ad kümeleri farklıysa bazı ikonlar
  override'sız kalır (jar orijinali sırıtır) ya da yetim PNG churn'ü olur →
  doğrulama A bunu commit öncesi yakalar.
- **`Utils.a` overload farkı:** Windows jar'ında imza farklıysa madde 2 best-effort
  atlanır (madde 1 retina'yı yine sağlar).
- **Koyu SKIN kontrast:** ICONS açık ikonlar koyu temada düşük kontrast — bilinen,
  SKIN #7'ye ertelendi.
