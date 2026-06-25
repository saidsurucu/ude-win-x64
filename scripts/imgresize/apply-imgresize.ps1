# apply-imgresize.ps1 - fare kose-tutamagiyla imaj boyutlandirma
param([Parameter(Mandatory)][string]$Jar)
. "$PSScriptRoot\..\common.ps1"
$JavassistVer='3.30.2-GA'; $JavassistUrl="https://repo1.maven.org/maven2/org/javassist/javassist/$JavassistVer/javassist-$JavassistVer.jar"
function Get-Javassist { $lib=Join-Path $VendorDir 'lib'; New-Dir $lib; $jvs=Join-Path $lib "javassist-$JavassistVer.jar"
  if (-not (Test-Path $jvs)) { & curl.exe -L -s -o $jvs $JavassistUrl; if ($LASTEXITCODE -ne 0) { throw "javassist indirilemedi" } }; return $jvs }
$jdk=Get-Jdk11Home; if (-not $jdk) { throw "JDK 11 yok" }
$javac=Join-Path $jdk 'bin\javac.exe'; $java=Join-Path $jdk 'bin\java.exe'; $jarTool=Join-Path $jdk 'bin\jar.exe'; $jvs=Get-Javassist; $dir=$PSScriptRoot
$work=Join-Path $BuildDir '_imgresize'; $helper=Join-Path $work 'helper'; $out=Join-Path $work 'out'
if (Test-Path $work){Remove-Item $work -Recurse -Force}; New-Dir $helper; New-Dir $out
Write-Ok "ImageResizeController derleniyor"
& $javac --release 11 -encoding UTF-8 -nowarn -cp "$Jar" -d $helper (Join-Path $dir 'macosimgresize\ImageResizeController.java')
if ($LASTEXITCODE -ne 0) { throw "Controller derlenemedi" }
& $jarTool uf $Jar -C $helper .   # patcher insertBefore Controller'i classpath'ten cozer
if ($LASTEXITCODE -ne 0) { throw "Controller jar'a eklenemedi" }
Write-Ok "ImageResizePatch derleniyor + calistiriliyor"
& $javac --release 11 -encoding UTF-8 -cp "$jvs;$helper" -d $work (Join-Path $dir 'ImageResizePatch.java')
if ($LASTEXITCODE -ne 0) { throw "ImageResizePatch derlenemedi" }
& $java -cp "$work;$jvs;$helper" ImageResizePatch $Jar $out
if ($LASTEXITCODE -ne 0) { throw "ImageResizePatch calismadi" }
& $jarTool uf $Jar -C $out .
if ($LASTEXITCODE -ne 0) { throw "yamali siniflar eklenemedi" }
Write-Ok "IMGRESIZE uygulandi"
