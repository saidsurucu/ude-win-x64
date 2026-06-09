# build.ps1 (scripts/) - ana orkestrator
# Fazlar: deps -> download -> patch -> package
#
# Parametreler (env veya switch):
#   -Icons      / $env:ICONS=1   modern ikonlari uygula
#   -Sign       / $env:SIGN=1    EXE'yi signtool ile imzala
#   -UdeUrl <u> / $env:UDE_URL   resmi paket linkini elle ver
#   -Only <faz> sadece tek faz calistir (deps|download|patch|package)
param(
  [switch]$Icons,
  [switch]$NoNativeDialogs,
  [switch]$Sign,
  [string]$UdeUrl,
  [ValidateSet('deps','download','patch','package','all')]
  [string]$Only = 'all'
)
. "$PSScriptRoot\common.ps1"
. "$PSScriptRoot\deps.ps1"
. "$PSScriptRoot\download.ps1"
. "$PSScriptRoot\patch.ps1"
. "$PSScriptRoot\package.ps1"

if ($Icons)           { $env:ICONS = '1' }
if ($NoNativeDialogs) { $env:NATIVE_DIALOGS = '0' }
if ($Sign)            { $env:SIGN  = '1' }
if ($UdeUrl)          { $env:UDE_URL = $UdeUrl }

$sw = [System.Diagnostics.Stopwatch]::StartNew()
Write-Host ""
Write-Host "  ude-win-x64  |  UDE Windows x64 yapisi  |  $AppName $AppVersion" -ForegroundColor Magenta
Write-Host ""

switch ($Only) {
  'deps'     { Invoke-Deps }
  'download' { Invoke-Download }
  'patch'    { Invoke-Patch }
  'package'  { $exe = Invoke-Package }
  'all' {
    Invoke-Deps
    Invoke-Download
    Invoke-Patch
    $exe = Invoke-Package
  }
}

$sw.Stop()
Write-Host ""
Write-Step ("Bitti ({0:mm\:ss})" -f $sw.Elapsed)
if ($exe) { Write-Host "  -> $exe" -ForegroundColor Green }
