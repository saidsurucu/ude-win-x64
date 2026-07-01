# Tasarım: Formatsız yapıştırma varsayılan — Windows portu (2026-07-02)

Mac reposundaki (ude-mac-arm64, 8ddbbe8c…23510c7c serisi, 2026-07-01) "formatsız
yapıştırma varsayılan" özelliğinin Windows'a aktarımı. Mac spec'i:
`ude-mac-arm64/docs/superpowers/specs/2026-07-01-plain-paste-default-design.md`.

## Amaç

Harici kaynaklardan (Word, tarayıcı, PDF…) yapıştırmada VARSAYILAN davranış
formatsız olsun; formatlı yapıştırma isteğe bağlı hâle gelsin. Kısayollar yer
değiştirir: **Ctrl+V = formatsız (akıllı)**, **Ctrl+Shift+V = formatlı**.
UDE-İÇİ kopyalar (EditorDataFlavor) Ctrl+V'de FORMATLI kalır — `paste()`
UDE-içi kopyayı kancaya uğramadan başta kendisi işler, bu yol hiç değişmez.

## Davranış tablosu

| Tetikleyici | UDE-içi kopya | Harici içerik |
|---|---|---|
| Ctrl+V / menü "Yapıştır" / sağ tık "Yapıştır" | formatlı (değişmez) | **formatsız** (yapı korunur: tablo/liste/imaj; karakter+paragraf biçimi imleçten) |
| Ctrl+Shift+V | formatlı | **formatlı** (eski Ctrl+V davranışı) |
| Sağ tık "Formatlı Yapıştır" (YENİ) | formatlı | **formatlı** |
| Sağ tık "Formatsız Yapıştır" (kalır) | formatsız | formatsız |
| Editör-dışı metin alanları | düz `tc.paste()` (değişmez) | düz `tc.paste()` (değişmez) |

"Formatsız" = PLAINPASTE'in mevcut anlamı: karakter stili ve paragraf biçimi
imleçten, tablo/imaj/liste YAPISI kaynaktan (`NativeInsert` düz-karakter modu).

## Yaklaşım

Mac'in seçtiği A yaklaşımı aynen: **kanca-düzeyinde varsayılan değişimi**.
Harici içerik `hj.a(Transferable)` kancasına düşer; kancanın varsayılanını
formatsıza çevirmek `paste()`'e giden HER yolu tek noktadan "akıllı" yapar.
Formatlı istek `forceRich` bayrağıyla aynı kancadan eski zengin yola gider.

**Windows farkı (tek):** Mac'te Ctrl(⌘)+Shift+V kısayolu textkeys agent'ındaki
`MacShortcutRemap`'te ele alınır; textkeys Windows'a PORT EDİLMEDİ (native
Ctrl gerekçesiyle atlanmıştı). Windows karşılığı: ZoomKeys deseninde küçük bir
`KeyEventDispatcher` (`PasteKeys`, aşağıda). Sağ tık menü öğesinin
accelerator'ı tek başına yetmez — öğe popup ilk gösterilmeden var olmadığından
(lazy `addMenuItem`) klavye kısayolu uygulamanın açılışından itibaren
dispatcher'la sağlanır.

## Bileşenler

1. **`macospasterich/PasteMode.java` (YENİ — Mac'ten verbatim, platform-nötr):**
   - `private static volatile boolean forceRich` + `setForceRich(boolean)`.
   - `insertHtml(Object,String)` / `insertRtf(Object,Transferable)`: `forceRich`
     ise `cursorAttrs=null` (zengin), değilse `PlainPaste.cursorAttrs` ile
     formatsız. Editör tipi bilinmiyorsa güvenli taraf: formatlı.
   - Bayrak EDT'de set/temizlenir (tek kullanıcı); her kullanım `try/finally`.

2. **`PasteRichPatch.java` (enjekte dal değişir — Mac diff'i verbatim):**
   `RichPaste.insertInto(this,__h)` → `macospasterich.PasteMode.insertHtml(this,__h)`;
   `RichPaste.insertRtf(this,__t)` → `PasteMode.insertRtf(this,__t)`. Kalan her
   şey aynı (uyap-web işaret koruması, düz-metin fallback, `logExternal`).

3. **`PlainPaste.java` (Mac diff'i verbatim):**
   - `cursorAttrs` private → paket-içi (PasteMode kullanır).
   - `addMenuItem` yeniden: YENİ "Formatlı Yapıştır" öğesi ("Formatsız
     Yapıştır"dan sonra; eylem = `PasteMode.setForceRich(true); tc.paste();
     finally setForceRich(false)`; accelerator göstergesi Ctrl+Shift+V —
     `getMenuShortcutKeyMaskEx()` Windows'ta CTRL döner). "Formatsız
     Yapıştır"ın accelerator göstergesi KALKAR. `insertAfter` yardımcısı;
     iki öğe için ada bakan idempotans.
   - `fixAccelerators` Windows'ta fiilen no-op (Ctrl→Ctrl) — verbatim ilkesi
     gereği dokunulmaz.

4. **`macospasterich/PasteKeys.java` (YENİ — Windows'a özgü,
   MacShortcutRemap `Fb.RICH_PASTE` dalının karşılığı; ZoomKeys deseni):**
   - `install()` idempotent (`volatile boolean installed`), `Throwable` yutar;
     `KeyboardFocusManager`'a `KeyEventDispatcher` ekler.
   - Eşleşme: Ctrl+Shift+V (`getMenuShortcutKeyMaskEx() | SHIFT_DOWN_MASK`,
     Alt/Meta basılı DEĞİLKEN). KEY_PRESSED'de: odak `JTextComponent` &&
     editable && enabled → `PasteMode.setForceRich(true)` → `tc.paste()` →
     `finally` temizle. Eşleşen PRESSED+RELEASED yutulur; eşleşmeyen hiçbir
     event'e dokunulmaz.
   - `tc.paste()` SENKRON (JTextComponent.paste doğrudan
     TransferHandler/importData çağırır) → `finally` gerçekten paste'i kapsar.
   - Editör (`hj` türevi) için sanal dispatch zengin `hj.paste()` + kanca →
     formatlı. Düz metin alanlarında `paste()` zaten düz; `forceRich` bayrağı
     onlarda etkisiz (kanca yok) — Mac `performLocal` ile aynı sonuç.
   - `PasteMode`'a reflection DEĞİL doğrudan çağrı (aynı pakette, aynı build
     biriminde derlenir — PASTERICH=0 ise PasteKeys de hiç enjekte edilmez).
   - Kurulum: `PasteRichPatch`, ZoomPatch'teki gibi `WPAppManager.main` başına
     `macospasterich.PasteKeys.install();` enjekte eder (idempotans:
     mevcut çağrı `instrument`/`ExprEditor` ile aranır, varsa atlanır).
     Kompozisyon güvenli: `patch.ps1` sırası tabledelete → zoomkeys →
     fileassoc → **pasterich**; her patcher jar'ı taze okur.

## Hata durumu

- Formatsız ekleme başarısız → kancadaki dal false/istisna → mevcut düz-metin
  fallback aynen çalışır.
- Bayrak `try/finally` ile hep temizlenir; yarıda kalan bayrak sonraki
  yapıştırmayı bozamaz.
- Dispatcher istisnası yutulur (`return false` — event'e dokunma), UDE kırılmaz.

## Test

- `tests/PasteModeTest.java` (Mac'ten port, headless): forceRich kapalıyken
  `insertHtml` karakter stilini düşürür (imleç stili), açıkken kaynak stili
  korunur; finally sonrası bayrak temiz.
- `tests/PasteMenuTest.java` (Mac'ten port, headless): sağ tık menüsünde üç
  öğe, sıra (Yapıştır → Formatsız → Formatlı), idempotans, accelerator
  göstergeleri (Formatlı=Ctrl+Shift+V, Formatsız=yok).
- YENİ `tests/PasteKeysTest.java` (headless): sentetik Ctrl+Shift+V
  KeyEvent'i `PasteKeys.dispatch`'e verilir → bayrak paste sırasında set,
  sonrasında temiz; PRESSED+RELEASED yutulur, başka tuşlara dokunulmaz.
- Mevcut davranış testleri değişmez (RichPaste API'sine dokunulmuyor).
- GUI doğrulaması kullanıcıda: Word'den Ctrl+V → formatsız, Ctrl+Shift+V →
  formatlı; UDE-içi kopya Ctrl+V → formatlı; sağ tık üç öğe.

## Build notları

- `apply-pasterich.ps1` `macospasterich\*.java`'yı topluca derler → PasteMode +
  PasteKeys otomatik dahil; PasteRichPatch aynı adımda WPAppManager'ı da yamalar.
  apply script'inde değişiklik GEREKMEZ (yeni dosyalar glob'a düşer).
- Doğrulama: `.\build.ps1 -Only patch` → exit 0 = tüm yamalar kompoze.
- Dokümantasyon: CLAUDE.md özellik tablosunda PASTERICH+PLAINPASTE satırı;
  README'deki "Formatsız Yapıştır (Ctrl+Shift+V)" satırı yeni davranışa göre
  güncellenir.
