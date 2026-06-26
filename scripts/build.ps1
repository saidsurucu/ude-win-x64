# build.ps1 (scripts/) - ana orkestrator
# Fazlar: deps -> download -> patch -> package
#
# Parametreler (env veya switch):
#   TUM ozellikler varsayilan ACIK; -No<Ozellik> ile kapatilir (orn. -NoSkin, -NoIcons).
#   -Sign       / $env:SIGN=1    EXE'yi signtool ile imzala
#   -UdeUrl <u> / $env:UDE_URL   resmi paket linkini elle ver
#   -Only <faz> sadece tek faz calistir (deps|download|patch|package)
param(
  [switch]$Icons,
  [switch]$NoIcons,
  [switch]$NoNativeDialogs,
  [switch]$NoLiveToggle,
  [switch]$NoTableDelete,
  [switch]$NoPasteRich,
  [switch]$NoPlainPaste,
  [switch]$NoImgFull,
  [switch]$NoImgResize,
  [switch]$NoAntet,
  [switch]$NoPdfFresh,
  [switch]$NoPasteImg,
  [switch]$NoFopFonts,
  [switch]$NoCaretFix,
  [switch]$NoZoomKeys,
  [switch]$Skin,
  [switch]$NoSkin,
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
if ($NoIcons)         { $env:ICONS = '0' }
if ($NoNativeDialogs) { $env:NATIVE_DIALOGS = '0' }
if ($NoLiveToggle)    { $env:LIVETOGGLE = '0' }
if ($NoTableDelete)   { $env:TABLEDELETE = '0' }
if ($NoPasteRich)     { $env:PASTERICH = '0' }
if ($NoPlainPaste)    { $env:PLAINPASTE = '0' }
if ($NoImgFull)       { $env:IMGFULL = '0' }
if ($NoImgResize)     { $env:IMGRESIZE = '0' }
if ($NoAntet)         { $env:ANTET = '0' }
if ($NoPdfFresh)      { $env:PDFFRESH = '0' }
if ($NoPasteImg)      { $env:PASTEIMG = '0' }
if ($NoFopFonts)      { $env:FOPFONTS = '0' }
if ($NoCaretFix)      { $env:CARETFIX = '0' }
if ($NoZoomKeys)      { $env:ZOOMKEYS = '0' }
if ($Skin)            { $env:SKIN = '1' }
if ($NoSkin)          { $env:SKIN = '0' }
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
