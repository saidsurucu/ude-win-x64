# common.ps1 - paylasilan ayarlar ve yardimcilar
# Tum build scriptleri bunu dot-source eder: . "$PSScriptRoot\common.ps1"

$ErrorActionPreference = 'Stop'
$ProgressPreference    = 'SilentlyContinue'

# --- Proje koku ---
$script:RepoRoot = Split-Path -Parent $PSScriptRoot

# --- Dizinler ---
$script:DownloadDir = Join-Path $RepoRoot 'downloads'
$script:VendorDir   = Join-Path $RepoRoot 'vendor'
$script:BuildDir    = Join-Path $RepoRoot 'build'
$script:InputDir    = Join-Path $BuildDir 'input'      # jpackage --input (uygulama dosyalari)
$script:RuntimeDir  = Join-Path $BuildDir 'runtime'    # jlink ile uretilen Java 11 runtime
$script:ResDir      = Join-Path $BuildDir 'res'        # ikon, .udf properties vb.
$script:DistDir     = Join-Path $RepoRoot 'dist'

# --- UDE sabitleri (editor-app.jar incelemesinden) ---
$script:AppName     = 'UyapDokumanEditoru'   # ASCII; Start menu / installer adi
$script:AppDisplay  = 'Uyap Dokuman Editoru'
$script:AppVersion  = if ($env:UDE_VERSION) { $env:UDE_VERSION } else { '5.4.17' }
$script:MainJar     = 'editor-app.jar'
$script:MainClass   = 'tr.com.havelsan.uyap.system.editor.common.WPAppManager'
$script:AppArgs     = @('getNewWPInstance','EDITOR_TYPE_DOCUMENT')
$script:Vendor      = 'ude-win-x64 (gayriresmi)'
$script:SplashGif   = 'dokuman_editor_splash_screen_animated.gif'

# UDE indirme sayfasi ve Windows/jar kaynagi
# editor-app.jar platformdan bagimsizdir; en guvenilir duz-zip kaynak uyapdokumaneditoru*.zip
$script:UdeDownloadPage = if ($env:UDE_DOWNLOAD_PAGE) { $env:UDE_DOWNLOAD_PAGE } else { 'https://www.uyap.gov.tr/Uyap-Editor' }

# --- Arac surumleri ---
$script:Jdk11Url = 'https://api.adoptium.net/v3/binary/latest/11/ga/windows/x64/jdk/hotspot/normal/eclipse'
$script:Jdk17Url = 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse'
$script:WixUrl   = 'https://github.com/wixtoolset/wix3/releases/download/wix3112rtm/wix311-binaries.zip'

function Write-Step([string]$msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok  ([string]$msg) { Write-Host "    $msg" -ForegroundColor Green }
function Write-Warn2([string]$msg){ Write-Host "    $msg" -ForegroundColor Yellow }

function New-Dir([string]$p) { if (-not (Test-Path $p)) { New-Item -ItemType Directory -Force -Path $p | Out-Null } }

# vendor altindaki ilk eslesen JDK kokunu bulur (icinde bin\java.exe olan)
function Find-JdkHome([string]$pattern) {
  $cand = Get-ChildItem -Path $VendorDir -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like $pattern }
  foreach ($c in $cand) {
    if (Test-Path (Join-Path $c.FullName 'bin\java.exe')) { return $c.FullName }
  }
  return $null
}

function Get-Jdk11Home { Find-JdkHome 'jdk-11*' }
function Get-Jdk17Home { Find-JdkHome 'jdk-17*' }
function Get-WixDir    { if (Test-Path (Join-Path $VendorDir 'wix\candle.exe')) { Join-Path $VendorDir 'wix' } else { $null } }
