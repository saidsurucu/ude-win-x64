# Alt-proje 4: Paste (PASTERICH + PLAINPASTE + PASTEIMG) (tasarım)

**Tarih:** 2026-06-25
**Yol haritası:** #4. **Hedef:** Mac ile birebir parite (Mac-spesifik hariç).
**Durum:** Onaylandı (Codex incelemeli, web-araştırmalı; otonom yürütme).

## Amaç

Word/tarayıcı/PDF'den stilli yapıştırma (PASTERICH), formatsız yapıştırma (PLAINPASTE,
Ctrl+Shift+V), panodan imaj yapıştırma (PASTEIMG).

## Kritik bulgu — zaten Windows-uyumlu

PasteRichPatch obfuscate `text.hj.a(Transferable)` BAŞINA dal enjekte eder: pano
`DataFlavor.allHtmlFlavor` içeriyorsa (UDE işareti "uyap-web-editor-data" yoksa) →
`RichPaste.insertInto(this, html)`. `allHtmlFlavor` STANDART Java — Windows CF_HTML'i
soyutlar (CF_HTML başlığını soyup HTML String döndürür). **Birincil yol platformdan
bağımsız.** Saf-Java makinesi (HtmlToUde 721 / NativeInsert 607 / Css/Html/UdeDoc/UdeXml/
PrLog) obfuscate/native ref YOK → verbatim.

**Tek Mac kuplajı:** `RichPaste.insertRtf` → gömülü `pbrich.swift` (NSPasteboard imaj-gömülü
HTML) + `textutil` (RTF→HTML). Pages/TextEdit/Mail için (Windows'ta yok; Windows zengin
kaynakları CF_HTML verir).

## Yaklaşım (önce kopyala, sonra uyarla)

1. **PASTERICH verbatim:** HtmlToUde, NativeInsert, Css, Html, UdeDoc, UdeXml, PrLog,
   RichPaste + PasteRichPatch. Paket `macospasterich` KORUNUR (enjekte FQN'lerle eşleşir).
2. **RichPaste uyarla (Codex):** insertInto KORUNUR (saf); **insertRtf → `return false`**
   (pbrich/textutil DROP; RTF-only kaynaklar düz-metne düşer — Windows'ta nadir: WordPad/
   eski Win32). Debug'da mevcut flavor'ları logla.
3. **allHtmlFlavor vs fragmentHtmlFlavor (Codex):** `allHtmlFlavor` TAM HTML dokümanı
   döndürür (`<html><head>...<!--StartFragment-->...`). HtmlToUde "kaynak-bağımsız" → tam-doc
   sarmalayıcıyı çözmeli. Test: Word/Chrome yapıştır; HtmlToUde bozarsa patch'i
   `fragmentHtmlFlavor`'a çevir.
4. **PLAINPASTE verbatim:** PlainPaste + PlainPastePatch (PASTERICH makinesine bağlı).
5. **PASTEIMG EMPİRİK (Codex):** ÖNCE yamasız UDE'de ekran-görüntüsü yapıştır. Çalışıyorsa
   ATLA; çalışmıyorsa PasteImagePatch + Conv portla (UDE davranış yaması, platform değil).
6. apply-*.ps1 (pasterich/plainpaste/pasteimg); bayraklar PASTERICH/PLAINPASTE/PASTEIMG
   default-on; `hj.a(Transferable)` + PASTEIMG hedeflerini jar'da doğrula.

## İmaj parite boşluğu (Codex — kabul edilebilir)

Word inline imajları: Mac'te pbrich/NSPasteboard'dan gelirdi; Windows CF_HTML'de imaj
ayrı CF_DIB'tedir / `file:///` referansıdır. **İlk portta inline-imaj kaybı kabul** —
zengin HTML (metin/tablo/liste/stil) öncelikli; doğrudan imaj PASTEIMG ile. Word-inline
imaj köprüsü gerekirse sonra.

## Doğrulama (Codex regresyon matrisi)

- **Kaynak matrisi:** Word, Chrome/Edge, Acrobat/tarayıcı-PDF, LibreOffice, Excel →
  kalın/italik/liste/**TABLO**/renk korunur.
- **Kenar durumlar:** iç-içe listeler, birleşik hücreler, hücreye yapıştır, seçimi
  değiştirerek yapıştır, RTL/Türkçe, büyük çok-sayfa yapıştır, undo/redo.
- **PLAINPASTE:** Ctrl+Shift+V stilleri soyar.
- **PASTEIMG:** (port edilirse) imaj yapışır.

## Kabul ölçütleri

1. Word'den stilli içerik (tablo dahil) UDE'ye biçimiyle yapışır.
2. PLAINPASTE düz metin verir.
3. PASTEIMG kararı empirik; gerekiyorsa imaj yapışır.
4. Bayraksız → stok yapıştırma (rollback).

## Riskler

- **HtmlToUde tam-doc HTML:** Windows allHtmlFlavor full-doc; bozarsa fragmentHtmlFlavor.
- **Inline imaj kaybı:** kabul edilen parite boşluğu (Mac-spesifik path).
- **insertRtf drop:** RTF-only kaynaklar düz-metne düşer (nadir).
