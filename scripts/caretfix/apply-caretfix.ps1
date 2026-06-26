# apply-caretfix.ps1 - zoom imlec kaymasi duzeltmesi (Faz 2; %100 disi zoom'da imlec/tiklama hizalamasi)
param([Parameter(Mandatory)][string]$Jar)
. "$PSScriptRoot\..\common.ps1"
$JavassistVer='3.30.2-GA'; $JavassistUrl="https://repo1.maven.org/maven2/org/javassist/javassist/$JavassistVer/javassist-$JavassistVer.jar"
function Get-Javassist { $lib=Join-Path $VendorDir 'lib'; New-Dir $lib; $jvs=Join-Path $lib "javassist-$JavassistVer.jar"
  if (-not (Test-Path $jvs)) { & curl.exe -L -s -o $jvs $JavassistUrl; if ($LASTEXITCODE -ne 0) { throw "javassist indirilemedi" } }; return $jvs }
$jdk=Get-Jdk11Home; if (-not $jdk) { throw "JDK 11 yok" }
$javac=Join-Path $jdk 'bin\javac.exe'; $java=Join-Path $jdk 'bin\java.exe'; $jarTool=Join-Path $jdk 'bin\jar.exe'; $jvs=Get-Javassist
$work=Join-Path $BuildDir '_caretfix'; $out=Join-Path $work 'out'; if (Test-Path $work){Remove-Item $work -Recurse -Force}; New-Dir $out
Write-Ok "CaretPatch derleniyor + calistiriliyor"
& $javac --release 11 -encoding UTF-8 -cp "$jvs" -d $work (Join-Path $PSScriptRoot 'CaretPatch.java')
if ($LASTEXITCODE -ne 0) { throw "CaretPatch derlenemedi" }
& $java -cp "$work;$jvs" CaretPatch $Jar $out
if ($LASTEXITCODE -ne 0) { throw "CaretPatch calismadi" }
& $jarTool uf $Jar -C $out .
if ($LASTEXITCODE -ne 0) { throw "yamali sinif eklenemedi" }
Write-Ok "CARETFIX (Faz 2 zoom) uygulandi"
