# SKIN tam port — Faz 7d: Canlı geçiş + renk-modu picker (WinLook -javaagent) (tasarım)

**Tarih:** 2026-06-25
**Yol haritası:** alt-proje #7, son faz (7a/7b/7c TAMAM). **Hedef:** Mac yamasıyla
birebir feature parity (Mac-spesifik krom hariç).
**Durum:** Onaylandı (Codex incelemeli; otonom yürütme).

## Amaç

Uygulama-içi **renk-modu picker** (Açık/Koyu/Sistem), **canlı (restart'sız) geçiş** ve
**koyu-sayfa onay kutusu** ekle. Bunlar ribbon MODELİNDEN eklendiğinden (bileşen ağacı
açılışta yalnız SEÇİLİ sekmeyi içerir) **runtime -javaagent** gerekir — Windows'ta yeni
altyapı. Mac MacLook'un krom kısımları (başlık çubuğu, trafik ışıkları, Dock adları)
PORT EDİLMEZ.

## Mimari (Codex — classloader güvenliği)

İki parça, sınıf-yükleyici çatışmasını önler:

1. **`winlook.WinLook`** (agent jar, premain) — KÜÇÜK bootstrap: `premain` →
   AWTEventListener (pencere-aktifleşince) → **reflection ile**
   `macosskin.WinLookRuntime.install(frame)` çağırır. `macosskin.*`/obfuscate sınıflara
   DOĞRUDAN statik referans YOK (premain erken yüklenir; doğrudan ref çökme riski).
   `Class.forName("macosskin.WinLookRuntime", false, frame.getClass().getClassLoader())`.
2. **`macosskin.WinLookRuntime`** (editor-app.jar'a apply-skin ile enjekte) — GERÇEK
   mantık; macosskin.* + obfuscate UDE sınıflarına tam erişim: `install(JFrame)` →
   addColorModeCombo + addDarkPageToggle + boldTaskTabs + removeScopeCombo.
3. **`macosskin.ModeSwitch`** (editor-app.jar) — canlı geçiş (EDT).

`macosskin.*` agent jar'ına KOPYALANMAZ (split static state riski — Codex).

## Port edilen MacLook alt kümesi (krom DROP)

**PORT (WinLookRuntime'a):** addColorModeCombo (picker, ribbon modelinden band bul →
JComboBox Açık/Koyu/Sistem → DarkMode.getMode/setMode + ModeSwitch.apply), addDarkPageToggle
("Klasik görünüme geç" kutusunun band'ına JCheckBox → DarkPage.setOn + repaint),
boldTaskTabs, removeScopeCombo (kırpan "Geçerli/Gövde" combo'su), findCheckBox/findScopeCombo
yardımcıları. fixRulerBackground (7a build-zamanı yaptı; canlı geçişte gerekebilir — dahil).
**DROP (Mac krom):** unifyTitleBar, insetMenuBar, syncRibbonTopInset, installMenuBarWatcher,
hookTitle/applyTitle, removeMemoryBar (opsiyonel — WebLaF değil; atla).

## ModeSwitch canlı geçiş (Codex — best-effort + restart fallback)

EDT'de (`invokeLater`): mode'u ÖNCE persist et (restart kurtarır) → FlatUde(Dark)Skin kur →
TÜM Word*.install() yeniden (setSkin UIDefaults'u siler) → wp.p.E güncelle → pencere-başına
`updateComponentTreeUI` (bileşen-başına try) → donan renkleri elle düzelt → repaint.
**Global setSkin patlarsa:** pref'i koru, "uygulamak için yeniden başlatın" mesajı, daha
fazla mutasyon YAPMA. **Tek bileşen patlarsa:** log + devam. Picker HER ZAMAN kalır;
başarısız canlı uygulama → "kaydedildi, yeniden başlatınca tamamlanır".

## Teslim (yeni altyapı)

1. `scripts\skin\winlook\WinLook.java` → derle → `MANIFEST: Premain-Class: winlook.WinLook`
   ile `winlook.jar` → jpackage `--input` dizinine (editor-app.jar yanına) kopyala.
2. `apply-skin.ps1`: WinLookRuntime + ModeSwitch'i editor-app.jar'a enjekte (compile listesi);
   ayrı adım winlook.jar agent'ını üretip InputDir'e koyar.
3. `package.ps1`: SKIN=1 iken `--java-options "-javaagent:`$APPDIR\winlook.jar"` ekle.
4. Agent yalnız SKIN=1'de üretilir/bundle edilir.

## Doğrulama

1. **Build:** `-Skin` ile EXE; winlook.jar InputDir'de + cfg'de -javaagent satırı.
2. **Picker:** Görünüm sekmesinde renk-modu combo'su görünür; Açık/Koyu/Sistem seçimi
   ANINDA tüm UI'yi değiştirir (restart yok).
3. **DarkPage toggle:** Görünüm'de koyu-sayfa kutusu; işaretleyince belge anında koyu.
4. **Persist:** seçim restart'tan sonra korunur (pref).
5. **Fallback:** canlı geçiş patlarsa pref korunur, app çökmez.
6. **PDF/diğer:** canlı geçiş sonrası PDF export + temel işlevler çalışır.
7. Agent yoksa (SKIN=0) app normal (no -javaagent).

## Kabul ölçütleri

1. Renk-modu picker + canlı geçiş çalışır (Açık↔Koyu restart'sız).
2. Koyu-sayfa onay kutusu çalışır.
3. Seçim kalıcı; canlı geçiş best-effort (çökme yok).
4. SKIN=0 → agent yok, normal app.

## Riskler

- **-javaagent classloader:** WinLook macosskin.*'a doğrudan ref VERMEZ (reflection) →
  premain erken-yükleme çökmesi önlenir (Codex). $APPDIR runtime'da çözülür.
- **Canlı setSkin EDT:** best-effort + restart fallback (Codex) → yarım-uygulama app'i
  düşürmez.
- **Ribbon model gezinme:** combo/checkbox SEÇİLİ-sekme-dışı band'larda → tüm task'ların
  getBands() gezilir (Mac deseni).
