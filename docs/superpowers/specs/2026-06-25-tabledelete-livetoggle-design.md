# Alt-proje 1: TABLEDELETE + LIVETOGGLE Windows portu (tasarım)

**Tarih:** 2026-06-25
**Yol haritası:** [2026-06-25-mac-feature-port-roadmap-design.md](2026-06-25-mac-feature-port-roadmap-design.md) — alt-proje #1
**Durum:** Onaylandı; Codex (codex-cli) ile çapraz incelendi.

## Amaç

Mac portundaki iki küçük, düşük-riskli özelliği Windows'a aktarmak ve böylece
**paylaşılan aktarım desenini uçtan uca doğrulamak**:

- **LIVETOGGLE** — "Otomatik Büyük Harf" / "Baş Harfler Büyük" / "Kelime Denetimi"
  toggle'ları, yeniden başlatma gerektirmeden açık belgeye anında uygulanır.
- **TABLEDELETE** — Word benzeri Backspace/Delete ile tablo silme; tablo yoksa
  normal silme davranışına devreder.

Her iki özellik de UDE'nin obfuscate sınıflarını hedefler ve **platformdan
bağımsızdır** (macOS syscall'ı yok, yalnız `log()` yolu ve binding mekanizması
Windows'a uyarlanır).

## Dosya yapısı (yeni)

```
scripts/livetoggle/
  LiveTogglePatch.java                # patcher (neredeyse birebir port)
  winlivetoggle/LiveToggle.java       # yardımcı (paket macoslivetoggle→winlivetoggle)
  apply-livetoggle.ps1                # apply-nativedialog.ps1 desenini izler
scripts/tabledelete/
  TableDeletePatch.java               # patcher: yardımcıları enjekte + installer'ı başlangıca bağlar
  wintabledelete/TableDelete.java     # yardımcı (paket yeniden adlandırılır, log() Windows yolu)
  wintabledelete/TableDeleteInstaller.java   # YENİ ~20 satır focus-listener kurucu
  apply-tabledelete.ps1
```

## LIVETOGGLE — port ayrıntıları

- `LiveTogglePatch.java` + `LiveToggle.java` **birebir** kopyalanır; yalnız paket
  `macoslivetoggle` → `winlivetoggle` yeniden adlandırılır (patcher'daki iki
  `insertBefore/After` string'i de güncellenir). Mantık değişmez.
- Hedefler: `text.dq/dA/db` sınıflarının `a(Ljava/awt/event/ActionEvent;)V` metodu;
  `gui.kP.b(...)` "yeniden başlat" diyalog çağrısı `$_ = 0` ile kaldırılır.
- **Guard (Codex önerisi folded):** `kP.b` çağrı sayısı `!= 1` ise patcher
  **build'i durdurur** ve hedef sınıf/metod + bulunan sayıyı yazdırır (sapma
  teşhisi kolay olsun).
- `apply-livetoggle.ps1` akışı (apply-nativedialog.ps1 aynası):
  1. `winlivetoggle/LiveToggle.java` derle → `jar uf` ile jar'a ekle.
  2. Patcher'ı `javassist;jar;helper` classpath'iyle derle → çalıştır
     (Javassist enjekte ettiği `winlivetoggle.LiveToggle` çağrısını jar
     classpath'inden çözer; helper bu yüzden ÖNCE jar'a girmiş olmalı).
  3. Yamalı sınıfları `jar uf` ile jar'a yaz.

## TABLEDELETE — port ayrıntıları

- `TableDelete.java` kopyalanır, paket `macostextkeys` → `wintabledelete`,
  `log()` yolu `%LOCALAPPDATA%\ude-tabledelete.txt`'e taşınır (env
  `UDE_TABLEDELLOG`, eksikse no-op).
- **YENİ `TableDeleteInstaller.install()`** — `MacTextKeys.install`'daki
  FOCUS_GAINED `AWTEventListener` bloğu, yalnız `TableDelete.bind(tc)` çağıracak
  şekilde sadeleştirilir. (Mac'in ⌘/⌥/dikte/textreplace kodu PORT EDİLMEZ.)
  - **İdempotent (Codex):** statik boolean guard — `main` yeniden girilirse
    listener iki kez kurulmaz.
  - **EDT gerektirmez (Codex):** `addAWTEventListener` doğrudan çağrılır;
    `invokeLater` sarması YOK (listener yalnız gelecekteki focus olaylarına tepki
    verir, kurulum anında Swing bileşenine dokunmaz).
- `TableDeletePatch.java`:
  1. İki yardımcı sınıfı (`TableDelete`, `TableDeleteInstaller`) jar'a enjekte eder.
  2. `WPAppManager.main`'e `insertBefore` ile `TableDeleteInstaller.install()`
     çağrısı ekler. Hedef metod bulunamazsa **istisna fırlatır** (build sesli
     biçimde durur).
- `apply-tabledelete.ps1` akışı LIVETOGGLE ile aynı (yardımcılar önce jar'a,
  sonra patcher).

## Wiring

- `patch.ps1`: `$env:LIVETOGGLE` ve `$env:TABLEDELETE` blokları eklenir —
  **varsayılan açık**, `=0` ile kapanır (`nativedialog` konvansiyonu).
- `build.ps1`: `-NoLiveToggle` / `-NoTableDelete` switch'leri eklenir.
- Her `apply-*.ps1` bağımsız çağrılabilir/kapatılabilir; biri başarısız olursa
  diğerini engellemez.

## Planın ilk adımı: ad doğrulama (KRİTİK)

Herhangi bir yama mantığından ÖNCE, indirilen **Windows** jar'ına karşı şu
obfuscate adların VARLIĞI **ve imza/görünürlüğü** doğrulanır
(`javap -classpath <jar>`; Codex notu: `getMethod` yalnız **public** metod bulur):

- `text.dq` / `text.dA` / `text.db` → `a(java.awt.event.ActionEvent)`
- `gui.kP` → `b(...)` diyalog metodu
- `text.hN` / `text.fY` / `text.im` → dinleyici sınıfları
- prefs `pki.b.l`, ribbon kutu holder `z`, menü holder `gui.ak`
- `wp.model.v.f(int)` → **public** olmalı (TableDelete reflection `getMethod`)
- `WPAppManager.main` → enjeksiyon noktası

Adlar eşleşmezse (Mac↔Windows obfuscation-map sapması): **dur ve araştır**, kendi
araştırma adımı olur.

## Kabul ölçütleri

1. `.\build.ps1` ile `.exe` üretilir; kurulur.
2. **LIVETOGGLE:** belge açıkken "Otomatik Büyük Harf" / "Kelime Denetimi"
   toggle'ı → açık belgeye anında uygulanır, "yeniden başlat" diyaloğu çıkmaz.
3. **TABLEDELETE:** tablo oluştur/yapıştır → doğru imleç/seçim konumunda
   Backspace/Delete tabloyu siler; başka yerde normal düzenleme bozulmaz.
4. **Rollback:** `-NoLiveToggle` / `-NoTableDelete` (veya env `=0`) → ilgili özellik
   üretilen `.exe`'de yok.

## Riskler

- **Obfuscation-map sapması (ana risk):** Windows ve macOS resmi paketleri aynı
  build'den obfuscate edilmemişse adlar farklılaşır. Plan adım-1 doğrulaması bunu
  yamadan önce yakalar.
- **In-place jar mutasyonu (Codex):** Yamalar `InputDir`'deki çıkarılmış jar
  üzerinde çalışır (yeniden-üretilebilir build artefaktı, orijinal indirme değil);
  kısmi başarısızlık `download` tekrarıyla toparlanır. Windows AV/indeksleme
  dosya kilidi riskine karşı orijinal indirme dosyasına dokunulmaz.
- **PowerShell mekaniği (Codex):** classpath ayıracı `;`, tüm yollar tırnaklı,
  patcher derlemesinde helper classpath'te.
