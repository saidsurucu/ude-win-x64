# apply-zemberek.ps1 - Kelime Denetimi kapaliyken acilista Zemberek sozlugunu yukleme (ZEMBEREKLAZY, Mac portu)
# PASTERICH/PLAINPASTE'ten SONRA calismali: ikisi de text.fK yazar.
param([Parameter(Mandatory)][string]$Jar)
. "$PSScriptRoot\..\common.ps1"
$JavassistVer='3.30.2-GA'; $JavassistUrl="https://repo1.maven.org/maven2/org/javassist/javassist/$JavassistVer/javassist-$JavassistVer.jar"
function Get-Javassist { $lib=Join-Path $VendorDir 'lib'; New-Dir $lib; $jvs=Join-Path $lib "javassist-$JavassistVer.jar"
  if (-not (Test-Path $jvs)) { & curl.exe -L -s -o $jvs $JavassistUrl; if ($LASTEXITCODE -ne 0) { throw "javassist indirilemedi" } }; return $jvs }
$jdk=Get-Jdk11Home; if (-not $jdk) { throw "JDK 11 yok" }
$javac=Join-Path $jdk 'bin\javac.exe'; $java=Join-Path $jdk 'bin\java.exe'; $jarTool=Join-Path $jdk 'bin\jar.exe'; $jvs=Get-Javassist
$work=Join-Path $BuildDir '_zemberek'; $out=Join-Path $work 'out'; if (Test-Path $work){Remove-Item $work -Recurse -Force}; New-Dir $out
Write-Ok "ZemberekLazyPatch derleniyor + calistiriliyor"
& $javac --release 11 -encoding UTF-8 -cp "$jvs" -d $work (Join-Path $PSScriptRoot 'ZemberekLazyPatch.java')
if ($LASTEXITCODE -ne 0) { throw "ZemberekLazyPatch derlenemedi" }
& $java -cp "$work;$jvs" ZemberekLazyPatch $Jar $out
if ($LASTEXITCODE -ne 0) { throw "ZemberekLazyPatch calismadi (UDE surumu degismis olabilir)" }
# fk.class / fK.class NTFS'te harf-duyarsiz cakisir -> patcher ayri alt agaclara (out\fk, out\fK_) yazar
$n=0
foreach ($d in Get-ChildItem -Path $out -Directory) {
  if (-not (Test-Path (Join-Path $d.FullName 'tr'))) { continue }
  & $jarTool uf $Jar -C $d.FullName tr
  if ($LASTEXITCODE -ne 0) { throw "yamali sinif eklenemedi ($($d.Name))" }
  $n++
}
if ($n -gt 0) { Write-Ok "ZEMBEREKLAZY (tembel Zemberek) uygulandi ($n sinif)" }
else { Write-Ok "ZEMBEREKLAZY zaten yamali, atlandi" }
