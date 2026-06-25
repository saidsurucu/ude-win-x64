# verify-names.ps1 - TABLEDELETE+LIVETOGGLE icin obfuscate adlari Windows jar'inda dogrula
. "$PSScriptRoot\common.ps1"
$jar = Join-Path $InputDir $MainJar
if (-not (Test-Path $jar)) { throw "editor-app.jar yok; once download" }
$javap = Join-Path (Get-Jdk11Home) 'bin\javap.exe'

function Show([string]$cls) {
  Write-Host "==> $cls" -ForegroundColor Cyan
  & $javap -p -classpath "$jar" $cls
}
$P = 'tr.com.havelsan.uyap.system.editor.common.'
# LIVETOGGLE hedefleri
Show ($P + 'text.dq'); Show ($P + 'text.dA'); Show ($P + 'text.db')
Show ($P + 'gui.kP')
Show ($P + 'text.hN'); Show ($P + 'text.fY'); Show ($P + 'text.im')
Show 'tr.com.havelsan.uyap.system.pki.b.l'
Show 'tr.gov.uyap.system.a.b.a.a.z'
Show ($P + 'gui.ak')
# TABLEDELETE hedefleri
Show ($P + 'WPAppManager')   # public static void main(String[]) -> enjeksiyon noktasi
# Tablo-silme primitifi: DocumentEx ust sinifi. f(int) UC overload icerir
# (Element/boolean/void); TableDelete calisma-aninda d.getClass().getMethod("f",int)
# ile cozer, basarisizsa normal Backspace'e devreder.
Show 'tr.com.havelsan.uyap.system.swing.wp.model.v'
