# .udf çift-tık hâlâ çalışmıyor: aynı-sürüm yeniden kurulum fix'leri ulaştırmıyor (2026-07-02)

Kullanıcı geri bildirimi (devam): FILEASSOC yaması (f9b3301, 2026-06-26) sonrasında da
kullanıcılar .udf'ye çift tıklayınca uygulamanın açılmadığını bildiriyor.

## Kök neden araştırması (sistematik; kanıtlarla)

1. **Bytecode fix'i doğru.** Yamalı jar (`build\input\editor-app.jar`) disassembly'sinde
   `ArgFix.normalize` main'in ilk komutu; jar yalnız-dosya-yolu argümanıyla başlatılınca
   belge AÇILIYOR (pencere başlığı: "Doküman Editörü v5.4.17 - test.udf (...)").
2. **İlişkilendirme kaydı doğru.** HKLM `.udf` → `progid3436...` →
   `shell\open\command = "...\UyapDokumanEditoru.exe" "%1"`.
3. **Belirti yeniden üretildi.** Fix-ÖNCESİ kurulu build'de (6/25) `Start-Process test.udf`
   → süreç başlıyor, `a(String[])` erken-return kapanına takılıp pencere açmadan çıkıyor.
4. **ASIL KÖK NEDEN — kullanıcılar fix'i hiç ALAMIYOR:**
   - `AppVersion` sabit `5.4.17`; jpackage **ProductCode'u name+version'dan deterministik**
     üretiyor. Taze MSI'nin ProductCode'u `{38C9D543-9443-378C-BF01-EDC285C0C154}` ==
     kurulu ürünün ProductCode'u. → Windows Installer **bakım moduna** düşüyor
     (Onar/Kaldır); **yeni dosyalar kurulmuyor**, eski (bozuk) jar yerinde kalıyor.
   - Ayrıca jpackage `main.wxs` şablonunda upgrade-detect `UpgradeVersion`
     `IncludeMaximum="no"` (Attributes=257): aynı sürüm upgrade kapsamı DIŞINDA →
     `RemoveExistingProducts` tetiklenmiyor.
   - Sonuç: tek-satır kurulumu yeniden çalıştıran kullanıcı sihirbazı görüyor ama fix'li
     dosyalar diske hiç yazılmıyor. (Hiç kurmamış kullanıcıda sorun yok.)

## Çözüm — özel WiX şablonu (`--resource-dir`)

`scripts/wix/main.wxs`: jpackage 17 şablonunun (build\jptmp\config\main.wxs'ten alınan)
kopyası, **iki fark** (Codex ile gözden geçirildi):
1. `Product Id="*"` — her build'de benzersiz ProductCode (WiX3 autogen) → bakım-modu tuzağı yok.
2. Upgrade-detect satırında `IncludeMaximum="yes"` → aynı sürüm de "upgradable" sayılır,
   `RemoveExistingProducts` eski ürünü kaldırıp taze kurar. Downgrade satırı upstream gibi
   exclusive (aynı build "downgrade" sayılmaz). `UpgradeCode` değişmedi.

`scripts/package.ps1`: `--resource-dir scripts\wix` bağlandı; ayrıca dist'teki eski exe
silinemezse (açık sihirbaz / OneDrive kilidi) erken ve anlaşılır hata.

## Doğrulama (yapıldı)

- jpackage config'ine özel şablon geçti (`build\jptmp\config\main.wxs` = bizimki).
- Yeni MSI ProductCode `{CB68BA11-...}` ≠ kurulu `{38C9D543-...}`; ikinci build
  `{0CE2A56C-...}` → her build benzersiz.
- Upgrade tablosu Attributes 257 → **769** (msidbUpgradeAttributesVersionMaxInclusive set).
- Kalan el testi (yükseltilmiş oturum gerekir): yeni exe'yi kur → Program Files'taki
  `editor-app.jar` tarihi güncellenmeli → .udf çift-tık belgeyi açmalı.

## Yan bulgular

- 6/25'ten kalma İKİ askıda `msiexec` oturumu dist exe'sini kilitliyordu (yeniden
  adlandırılarak aşıldı: `*.exe.locked-old`); yeni kurulumdan önce yeniden başlatma
  önerilir (1618 "başka kurulum sürüyor" riski).
- JDK 17 güncellenince `scripts/wix/main.wxs`'i `build\jptmp\config\main.wxs` ile diff'le
  (iki bilinçli fark dışında eşit kalmalı).
