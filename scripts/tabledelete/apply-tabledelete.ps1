# apply-tabledelete.ps1 - Backspace/Delete ile tablo silme yamasi
param([Parameter(Mandatory)][string]$Jar)
. "$PSScriptRoot\..\common.ps1"

$JavassistVer = '3.30.2-GA'
$JavassistUrl = "https://repo1.maven.org/maven2/org/javassist/javassist/$JavassistVer/javassist-$JavassistVer.jar"
function Get-Javassist {
  $lib = Join-Path $VendorDir 'lib'; New-Dir $lib
  $jvs = Join-Path $lib "javassist-$JavassistVer.jar"
  if (-not (Test-Path $jvs)) {
    Write-Ok "javassist indiriliyor"
    & curl.exe -L -s -o $jvs $JavassistUrl
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $jvs)) { throw "javassist indirilemedi" }
  }
  return $jvs
}

$jdk = Get-Jdk11Home
if (-not $jdk) { throw "JDK 11 yok; once deps" }
$javac   = Join-Path $jdk 'bin\javac.exe'
$java    = Join-Path $jdk 'bin\java.exe'
$jarTool = Join-Path $jdk 'bin\jar.exe'
$jvs     = Get-Javassist

$work   = Join-Path $BuildDir '_tabledelete'
$helper = Join-Path $work 'helper'   # derlenmis com.udewin.tabledelete.*
$out    = Join-Path $work 'out'      # yamali WPAppManager
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
New-Dir $helper; New-Dir $out

Write-Ok "yardimci siniflar derleniyor"
& $javac --release 11 -encoding UTF-8 -d $helper `
  (Join-Path $PSScriptRoot 'com\udewin\tabledelete\TableDelete.java') `
  (Join-Path $PSScriptRoot 'com\udewin\tabledelete\TableDeleteInstaller.java')
if ($LASTEXITCODE -ne 0) { throw "yardimci siniflar derlenemedi" }

# Yardimcilari ONCE jar'a ekle (patcher insertBefore bunlari classpath'ten cozer)
& $jarTool uf $Jar -C $helper .
if ($LASTEXITCODE -ne 0) { throw "yardimcilar jar'a eklenemedi" }

Write-Ok "TableDeletePatch derleniyor"
& $javac --release 11 -encoding UTF-8 -cp "$jvs;$helper" -d $work (Join-Path $PSScriptRoot 'TableDeletePatch.java')
if ($LASTEXITCODE -ne 0) { throw "patcher derlenemedi" }
Write-Ok "TableDeletePatch calistiriliyor"
& $java -cp "$work;$jvs;$helper" TableDeletePatch $Jar $helper $out
if ($LASTEXITCODE -ne 0) { throw "patcher calismadi" }

& $jarTool uf $Jar -C $out .
if ($LASTEXITCODE -ne 0) { throw "yamali sinif jar'a eklenemedi" }
Write-Ok "TABLEDELETE uygulandi"
