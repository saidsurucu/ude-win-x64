# apply-linespacing.ps1 - satir araligi menusune "1.5" ekler (satici unutmus)
param([Parameter(Mandatory)][string]$Jar)
. "$PSScriptRoot\..\common.ps1"
$JavassistVer = '3.30.2-GA'
$JavassistUrl = "https://repo1.maven.org/maven2/org/javassist/javassist/$JavassistVer/javassist-$JavassistVer.jar"
function Get-Javassist {
  $lib = Join-Path $VendorDir 'lib'; New-Dir $lib
  $jvs = Join-Path $lib "javassist-$JavassistVer.jar"
  if (-not (Test-Path $jvs)) { & curl.exe -L -s -o $jvs $JavassistUrl
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $jvs)) { throw "javassist indirilemedi" } }
  return $jvs
}
$jdk=Get-Jdk11Home; if (-not $jdk) { throw "JDK 11 yok" }
$javac=Join-Path $jdk 'bin\javac.exe'; $java=Join-Path $jdk 'bin\java.exe'; $jarTool=Join-Path $jdk 'bin\jar.exe'
$jvs=Get-Javassist; $work=Join-Path $BuildDir '_linespacing'; $out=Join-Path $work 'out'
if (Test-Path $work) { Remove-Item $work -Recurse -Force }; New-Dir $out
Write-Ok "LineSpacingPatch derleniyor + calistiriliyor"
& $javac --release 11 -encoding UTF-8 -cp "$jvs" -d $work (Join-Path $PSScriptRoot 'LineSpacingPatch.java')
if ($LASTEXITCODE -ne 0) { throw "LineSpacingPatch derlenemedi" }
& $java -cp "$work;$jvs" LineSpacingPatch $Jar $out
if ($LASTEXITCODE -ne 0) { throw "LineSpacingPatch calismadi" }
& $jarTool uf $Jar -C $out .
if ($LASTEXITCODE -ne 0) { throw "yamali sinif jar'a eklenemedi" }
Write-Ok "LINESPACING uygulandi"
