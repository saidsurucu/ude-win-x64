# apply-fileassoc.ps1 - .udf cift-tiklayinca acilmasi icin WPAppManager.main arg normalizasyonu
param([Parameter(Mandatory)][string]$Jar)
. "$PSScriptRoot\..\common.ps1"
$JavassistVer='3.30.2-GA'; $JavassistUrl="https://repo1.maven.org/maven2/org/javassist/javassist/$JavassistVer/javassist-$JavassistVer.jar"
function Get-Javassist { $lib=Join-Path $VendorDir 'lib'; New-Dir $lib; $jvs=Join-Path $lib "javassist-$JavassistVer.jar"
  if (-not (Test-Path $jvs)) { & curl.exe -L -s -o $jvs $JavassistUrl; if ($LASTEXITCODE -ne 0) { throw "javassist indirilemedi" } }; return $jvs }
$jdk=Get-Jdk11Home; if (-not $jdk) { throw "JDK 11 yok" }
$javac=Join-Path $jdk 'bin\javac.exe'; $java=Join-Path $jdk 'bin\java.exe'; $jarTool=Join-Path $jdk 'bin\jar.exe'; $jvs=Get-Javassist; $dir=$PSScriptRoot
$work=Join-Path $BuildDir '_fileassoc'; $hc=Join-Path $work 'helper'; $out=Join-Path $work 'out'
if (Test-Path $work){Remove-Item $work -Recurse -Force}; New-Dir $hc; New-Dir $out
Write-Ok "ArgFix yardimcisi derleniyor"
& $javac --release 11 -encoding UTF-8 -nowarn -d $hc (Join-Path $dir 'com\udewin\fileassoc\ArgFix.java')
if ($LASTEXITCODE -ne 0) { throw "ArgFix derlenemedi" }
& $jarTool uf $Jar -C $hc .
if ($LASTEXITCODE -ne 0) { throw "ArgFix jar'a eklenemedi" }
Write-Ok "FileAssocPatch derleniyor + calistiriliyor"
& $javac --release 11 -encoding UTF-8 -cp "$jvs;$hc" -d $work (Join-Path $dir 'FileAssocPatch.java')
if ($LASTEXITCODE -ne 0) { throw "FileAssocPatch derlenemedi" }
& $java -cp "$work;$jvs;$hc" FileAssocPatch $Jar $out
if ($LASTEXITCODE -ne 0) { throw "FileAssocPatch calismadi" }
& $jarTool uf $Jar -C $out .
if ($LASTEXITCODE -ne 0) { throw "yamali sinif eklenemedi" }
Write-Ok "FILEASSOC uygulandi (.udf cift-tik)"
