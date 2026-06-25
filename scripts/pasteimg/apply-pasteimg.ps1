# apply-pasteimg.ps1 - panodan imaj yapistirma KALITESI (yikici sayfaya-sigdirma kucultmesini atla)
# NOT: Mac cast/Conv kisimlari Windows'ta zararsiz no-op (donus zaten BufferedImage); kalite parcasi platform-notr.
param([Parameter(Mandatory)][string]$Jar)
. "$PSScriptRoot\..\common.ps1"
$JavassistVer='3.30.2-GA'; $JavassistUrl="https://repo1.maven.org/maven2/org/javassist/javassist/$JavassistVer/javassist-$JavassistVer.jar"
function Get-Javassist { $lib=Join-Path $VendorDir 'lib'; New-Dir $lib; $jvs=Join-Path $lib "javassist-$JavassistVer.jar"
  if (-not (Test-Path $jvs)) { & curl.exe -L -s -o $jvs $JavassistUrl; if ($LASTEXITCODE -ne 0) { throw "javassist indirilemedi" } }; return $jvs }
$jdk=Get-Jdk11Home; if (-not $jdk) { throw "JDK 11 yok" }
$javac=Join-Path $jdk 'bin\javac.exe'; $java=Join-Path $jdk 'bin\java.exe'; $jarTool=Join-Path $jdk 'bin\jar.exe'; $jvs=Get-Javassist; $dir=$PSScriptRoot
$work=Join-Path $BuildDir '_pasteimg'; $helper=Join-Path $work 'helper'; $out=Join-Path $work 'out'
if (Test-Path $work){Remove-Item $work -Recurse -Force}; New-Dir $helper; New-Dir $out
Write-Ok "Conv derleniyor"
& $javac --release 11 -encoding UTF-8 -nowarn -cp "$Jar" -d $helper (Join-Path $dir 'macospasteimage\Conv.java')
if ($LASTEXITCODE -ne 0) { throw "Conv derlenemedi" }
& $jarTool uf $Jar -C $helper .
if ($LASTEXITCODE -ne 0) { throw "Conv jar'a eklenemedi" }
Write-Ok "PasteImagePatch derleniyor + calistiriliyor"
& $javac --release 11 -encoding UTF-8 -cp "$jvs;$helper" -d $work (Join-Path $dir 'PasteImagePatch.java')
if ($LASTEXITCODE -ne 0) { throw "PasteImagePatch derlenemedi" }
& $java -cp "$work;$jvs;$helper" PasteImagePatch $Jar $out
if ($LASTEXITCODE -ne 0) { throw "PasteImagePatch calismadi" }
& $jarTool uf $Jar -C $out .
if ($LASTEXITCODE -ne 0) { throw "yamali sinif eklenemedi" }
Write-Ok "PASTEIMG uygulandi"
