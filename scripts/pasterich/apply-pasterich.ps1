# apply-pasterich.ps1 - harici stilli yapistirma (PASTERICH) + formatsiz yapistirma (PLAINPASTE)
param([Parameter(Mandatory)][string]$Jar, [switch]$PlainPaste)
. "$PSScriptRoot\..\common.ps1"

$JavassistVer = '3.30.2-GA'
$JavassistUrl = "https://repo1.maven.org/maven2/org/javassist/javassist/$JavassistVer/javassist-$JavassistVer.jar"
function Get-Javassist {
  $lib = Join-Path $VendorDir 'lib'; New-Dir $lib
  $jvs = Join-Path $lib "javassist-$JavassistVer.jar"
  if (-not (Test-Path $jvs)) { Write-Ok "javassist indiriliyor"; & curl.exe -L -s -o $jvs $JavassistUrl
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $jvs)) { throw "javassist indirilemedi" } }
  return $jvs
}
$jdk = Get-Jdk11Home; if (-not $jdk) { throw "JDK 11 yok" }
$javac=Join-Path $jdk 'bin\javac.exe'; $java=Join-Path $jdk 'bin\java.exe'; $jarTool=Join-Path $jdk 'bin\jar.exe'
$jvs=Get-Javassist; $dir=$PSScriptRoot
$work=Join-Path $BuildDir '_pasterich'; $helper=Join-Path $work 'helper'; $out=Join-Path $work 'out'
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
New-Dir $helper; New-Dir $out

Write-Ok "PASTERICH yardimcilari derleniyor"
& $javac --release 11 -encoding UTF-8 -nowarn -cp "$Jar" -d $helper (Get-ChildItem (Join-Path $dir 'macospasterich\*.java') | % FullName)
if ($LASTEXITCODE -ne 0) { throw "PASTERICH yardimcilari derlenemedi" }
# yardimcilari ONCE jar'a ekle (patcher insertBefore bunlari classpath'ten cozer)
& $jarTool uf $Jar -C $helper .
if ($LASTEXITCODE -ne 0) { throw "yardimcilar jar'a eklenemedi" }

Write-Ok "PasteRichPatch derleniyor + calistiriliyor"
& $javac --release 11 -encoding UTF-8 -cp "$jvs;$helper" -d $work (Join-Path $dir 'PasteRichPatch.java')
if ($LASTEXITCODE -ne 0) { throw "PasteRichPatch derlenemedi" }
& $java -cp "$work;$jvs;$helper" PasteRichPatch $Jar $out
if ($LASTEXITCODE -ne 0) { throw "PasteRichPatch calismadi" }

if ($PlainPaste) {
  Write-Ok "PlainPastePatch derleniyor + calistiriliyor"
  & $javac --release 11 -encoding UTF-8 -cp "$jvs;$helper" -d $work (Join-Path $dir 'PlainPastePatch.java')
  if ($LASTEXITCODE -ne 0) { throw "PlainPastePatch derlenemedi" }
  & $java -cp "$work;$jvs;$helper" PlainPastePatch $Jar $out
  if ($LASTEXITCODE -ne 0) { throw "PlainPastePatch calismadi" }
}

& $jarTool uf $Jar -C $out .
if ($LASTEXITCODE -ne 0) { throw "yamali siniflar jar'a eklenemedi" }
Write-Ok "PASTERICH uygulandi$(if($PlainPaste){' + PLAINPASTE'})"
