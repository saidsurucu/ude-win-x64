# apply-livetoggle.ps1 - otomatik duzeltme toggle'larini aninda etkin yapar
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

$work   = Join-Path $BuildDir '_livetoggle'
$helper = Join-Path $work 'helper'
$out    = Join-Path $work 'out'
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
New-Dir $helper; New-Dir $out

Write-Ok "LiveToggle yardimcisi derleniyor"
& $javac --release 11 -encoding UTF-8 -d $helper (Join-Path $PSScriptRoot 'com\udewin\livetoggle\LiveToggle.java')
if ($LASTEXITCODE -ne 0) { throw "yardimci derlenemedi" }

# Yardimciyi ONCE jar'a ekle (patcher insertBefore/After bunu classpath'ten cozer)
& $jarTool uf $Jar -C $helper .
if ($LASTEXITCODE -ne 0) { throw "yardimci jar'a eklenemedi" }

Write-Ok "LiveTogglePatch derleniyor"
& $javac --release 11 -encoding UTF-8 -cp "$jvs;$helper" -d $work (Join-Path $PSScriptRoot 'LiveTogglePatch.java')
if ($LASTEXITCODE -ne 0) { throw "patcher derlenemedi" }
Write-Ok "LiveTogglePatch calistiriliyor"
& $java -cp "$work;$jvs;$helper" LiveTogglePatch $Jar $out
if ($LASTEXITCODE -ne 0) { throw "patcher calismadi" }

& $jarTool uf $Jar -C $out .
if ($LASTEXITCODE -ne 0) { throw "yamali siniflar jar'a eklenemedi" }
Write-Ok "LIVETOGGLE uygulandi"
