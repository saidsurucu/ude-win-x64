# deps.ps1 - build araclarini temin eder (JDK 17 jpackage, JDK 11 runtime, WiX 3.x)
. "$PSScriptRoot\common.ps1"

function Get-Archive([string]$url, [string]$outFile) {
  if (Test-Path $outFile) { Write-Ok "zaten var: $(Split-Path $outFile -Leaf)"; return }
  Write-Ok "indiriliyor: $url"
  & curl.exe -L -s -o $outFile $url
  if ($LASTEXITCODE -ne 0 -or -not (Test-Path $outFile)) { throw "indirme basarisiz: $url" }
}

function Invoke-Deps {
  Write-Step "Build araclari temin ediliyor (vendor/)"
  New-Dir $VendorDir

  # --- JDK 11 (gomulu runtime icin) ---
  if (-not (Get-Jdk11Home)) {
    Get-Archive $Jdk11Url (Join-Path $VendorDir 'jdk11.zip')
    Expand-Archive -Path (Join-Path $VendorDir 'jdk11.zip') -DestinationPath $VendorDir -Force
  }
  $jdk11 = Get-Jdk11Home
  if (-not $jdk11) { throw "JDK 11 bulunamadi" }
  Write-Ok "JDK 11: $jdk11"

  # --- JDK 17 (jpackage icin) ---
  if (-not (Get-Jdk17Home)) {
    Get-Archive $Jdk17Url (Join-Path $VendorDir 'jdk17.zip')
    Expand-Archive -Path (Join-Path $VendorDir 'jdk17.zip') -DestinationPath $VendorDir -Force
  }
  $jdk17 = Get-Jdk17Home
  if (-not $jdk17 -or -not (Test-Path (Join-Path $jdk17 'bin\jpackage.exe'))) { throw "JDK 17 (jpackage) bulunamadi" }
  Write-Ok "JDK 17 (jpackage): $jdk17"

  # --- WiX Toolset 3.x (jpackage --type exe icin) ---
  if (-not (Get-WixDir)) {
    Get-Archive $WixUrl (Join-Path $VendorDir 'wix311.zip')
    Expand-Archive -Path (Join-Path $VendorDir 'wix311.zip') -DestinationPath (Join-Path $VendorDir 'wix') -Force
  }
  $wix = Get-WixDir
  if (-not $wix) { throw "WiX bulunamadi" }
  Write-Ok "WiX: $wix"
}

if ($MyInvocation.InvocationName -ne '.') { Invoke-Deps }
