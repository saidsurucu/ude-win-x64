# apply-antet.ps1 - "Antetlerim" (kisisel antet) bolumu (Windows: %APPDATA%\UDE\Antetler)
param([Parameter(Mandatory)][string]$Jar)
. "$PSScriptRoot\..\common.ps1"
$JavassistVer='3.30.2-GA'; $JavassistUrl="https://repo1.maven.org/maven2/org/javassist/javassist/$JavassistVer/javassist-$JavassistVer.jar"
function Get-Javassist { $lib=Join-Path $VendorDir 'lib'; New-Dir $lib; $jvs=Join-Path $lib "javassist-$JavassistVer.jar"
  if (-not (Test-Path $jvs)) { & curl.exe -L -s -o $jvs $JavassistUrl; if ($LASTEXITCODE -ne 0) { throw "javassist indirilemedi" } }; return $jvs }
$jdk=Get-Jdk11Home; if (-not $jdk) { throw "JDK 11 yok" }
$javac=Join-Path $jdk 'bin\javac.exe'; $java=Join-Path $jdk 'bin\java.exe'; $jarTool=Join-Path $jdk 'bin\jar.exe'; $jvs=Get-Javassist; $dir=$PSScriptRoot
$work=Join-Path $BuildDir '_antet'; $helper=Join-Path $work 'helper'; $out=Join-Path $work 'out'
if (Test-Path $work){Remove-Item $work -Recurse -Force}; New-Dir $helper; New-Dir $out
Write-Ok "ANTET yardimcilari derleniyor"
& $javac --release 11 -encoding UTF-8 -nowarn -cp "$Jar" -d $helper (Get-ChildItem (Join-Path $dir 'macosantet\*.java') | % FullName)
if ($LASTEXITCODE -ne 0) { throw "ANTET yardimcilari derlenemedi" }
& $jarTool uf $Jar -C $helper .
if ($LASTEXITCODE -ne 0) { throw "yardimcilar jar'a eklenemedi" }
Write-Ok "AntetPatch derleniyor + calistiriliyor"
& $javac --release 11 -encoding UTF-8 -cp "$jvs;$helper" -d $work (Join-Path $dir 'AntetPatch.java')
if ($LASTEXITCODE -ne 0) { throw "AntetPatch derlenemedi" }
& $java -cp "$work;$jvs;$helper" AntetPatch $Jar $out
if ($LASTEXITCODE -ne 0) { throw "AntetPatch calismadi" }
& $jarTool uf $Jar -C $out .
if ($LASTEXITCODE -ne 0) { throw "yamali sinif eklenemedi" }
Write-Ok "ANTET uygulandi"
