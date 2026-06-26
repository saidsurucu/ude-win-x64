# Tasarım: .udf çift-tık açma + "Aç" diyaloğu native parite (2026-06-26)

Kullanıcı geri bildirimi:
1. **.udf dosyasına çift tıklayınca uygulama açılmıyor.**
2. **"Aç" (Open) dosya diyaloğu native Windows değil**; "Kaydet" (Save) native çalışıyor.

İki bağımsız hata. Her ikisi de jar disassembly ile kök-neden doğrulandı; Codex ile gözden geçirildi.

---

## Sorun 1 — .udf çift-tık açmıyor

### Kök neden (kanıtlanmış)
`WPAppManager.a(String[])` argümanları şöyle işliyor:
- `"getNewWPInstance"` / `"null"` / `null` argümanı görülürse `flag=1`.
- `"EDITOR_TYPE_DOCUMENT"` eşleşmesi yok → varsayılan `mod=2`.
- Var olan dosya yolu (`new File(arg).exists()`) bir slot'a yazılıyor.
- **Döngü sonrası kapan:** `if (flag==0 && args.length!=0) return;` → hiçbir şey açmadan çıkış.

jpackage `--file-associations` ile **çift tıklamada** launcher `app.exe "C:\yol\dosya.udf"` çağırır.
jpackage `--arguments getNewWPInstance EDITOR_TYPE_DOCUMENT` yalnızca **varsayılan**tır
(launcher'a argüman verilmediğinde kullanılır). Çift-tıkta CLI argümanı (dosya yolu) verildiği
için varsayılanlar **devre dışı** → `args=["...udf"]` → `flag=0` → erken `return`.

Mac'te dosya Apple-event ile `MacFileUtils.getAcilmakIstenenFile()`'tan geldiği ve uygulama
normal (getNewWPInstance ile) başlatıldığı için bu kapana takılmaz → Windows'a özgü hata.

`jpackage --file-associations` properties dosyası launcher argümanı eklemeyi desteklemez
(yalnız extension/mime/icon/description). Registry/WiX custom-action veya wrapper-exe daha kırılgan.
→ **En sağlam çözüm: bytecode arg-normalizasyonu** (Codex de bunu onayladı).

### Çözüm — yeni FILEASSOC yaması (varsayılan AÇIK)
Proje deseni (helper + patcher):
- `scripts/fileassoc/com/udewin/fileassoc/ArgFix.java`: `static String[] normalize(String[])`.
  - `args` null/boş → değiştirmeden döner.
  - Argümanlardan biri `getNewWPInstance`/`"null"`/`null` ise (normal başlatma) → değiştirmeden döner.
  - Aksi halde (yalnız dosya yolu/yolları) → başa `"getNewWPInstance"` ekleyip yeni dizi döner.
  - Idempotent; `EDITOR_TYPE_DOCUMENT` **eklenmez** (default `mod=2` zaten doğru).
- `scripts/fileassoc/FileAssocPatch.java`: `WPAppManager.main` başına
  `$1 = com.udewin.fileassoc.ArgFix.normalize($1);` enjekte eder (ZoomPatch deseni).
- `scripts/fileassoc/apply-fileassoc.ps1`: helper'ı derle → jar'a ekle → patcher'ı çalıştır.
- `scripts/patch.ps1`: `FILEASSOC` bayrağı (default-on foreach listesi).

Sıra önemsiz: ZOOMKEYS/TABLEDELETE de `main`'e `insertBefore` enjekte ediyor; install() çağrıları
argüman okumuyor, normalize ise `a(String[])` çağrılmadan önce (main girişinde) çalışıyor.

---

## Sorun 2 — "Aç" diyaloğu native değil

### Kök neden (kanıtlanmış)
`NativeDialogPatch` aday-sınıf **ön-filtresi** sınıf bytecode'unda `"JFileChooser"` literal'i
arıyor. Ama UDE Aç/Kaydet'i kendi alt sınıfı `gui.dp` (→ `gui.a.p` → `javax.swing.JFileChooser`)
üzerinden çağırıyor. `dp.showOpenDialog` çağıran sınıflar (`fm, iI, nn, op`) bytecode'unda
`"JFileChooser"` literal'i **taşımıyor** (yalnız `gui/dp` geçiyor) → ön-filtre bunları **atlıyor**
→ "Aç" Swing kalıyor. Kaydet'in çağıranları arasında taranan sınıflar (`mz, ej, cD, cA`) olduğu
için Kaydet native görünüyor.

Kanıt: jar tarama (case-sensitive) — atlanan çağıranlar:
- Aç: `fm, iI, nn, op` (hepsi `showOpenDialog (Ljava/awt/Component;)I`)
- Kaydet: `lo, nj, pki/b/c` (hepsi `showSaveDialog (Ljava/awt/Component;)I`)

`gui.a.p extends javax.swing.JFileChooser` doğrulandı → `(JFileChooser)$0` cast'i tip-güvenli.

### Çözüm — NativeDialogPatch ön-filtresini düzelt + matcher'ı sertleştir
- **Ön-filtre:** `"JFileChooser"` literal şartını kaldır; yalnız `tr/*` + `show*` string'i taşıyan
  sınıfları tara.
- **Replace matcher:** ad + imza eşleşmesine **declaring-class JFileChooser alt-tip kontrolü** ekle
  (`m.getMethod().getDeclaringClass().subtypeOf(pool.get("javax.swing.JFileChooser"))`).
  Böylece `showDialog(Component,String):int` imzalı alakasız bir sınıf varsa runtime cast patlamaz.
  (JColorChooser.showDialog farklı imza zaten elenir.)
- Her sınıf instrument/write bloğu `try/catch` ile loglayıp devam etsin.
- `patched == 0` yine fail etsin; yamalanan sınıf sayısı loglansın.

---

## Doğrulama
- `.\build.ps1 -Only download` (cache'den fresh jar) → `.\build.ps1 -Only patch` bayraksız → exit 0
  (tüm yamalar kompoze).
- NativeDialogPatch çıktısında `fm, iI, nn, op, lo, nj` artık "yamalandi" olarak görünmeli.
- Yamalı jar'da `WPAppManager.main` disassembly'sinde `ArgFix.normalize` çağrısı bulunmalı.

## Atlanan / dışı
- Mac Apple-event tabanlı dosya açma (Windows'ta CLI-arg yolu kullanılıyor).
