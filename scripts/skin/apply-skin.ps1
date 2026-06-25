# apply-skin.ps1 - SKIN spike: trimli Substance skin yamasi
param([Parameter(Mandatory)][string]$Jar)
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

$jdk = Get-Jdk11Home
if (-not $jdk) { throw "JDK 11 yok; once deps" }
$javac = Join-Path $jdk 'bin\javac.exe'; $java = Join-Path $jdk 'bin\java.exe'; $jarTool = Join-Path $jdk 'bin\jar.exe'
$jvs = Get-Javassist
$skinDir = $PSScriptRoot

$work = Join-Path $BuildDir '_skin'
$helper = Join-Path $work 'helper'; $out = Join-Path $work 'out'
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
New-Dir $helper; New-Dir $out

Write-Ok "skin yardimcilari derleniyor"
& $javac --release 11 -encoding UTF-8 -cp "$Jar" -d $helper `
  (Join-Path $skinDir 'macosskin\DarkMode.java') `
  (Join-Path $skinDir 'macosskin\FlatUdeSkin.java') `
  (Join-Path $skinDir 'macosskin\FlatUdeDarkSkin.java') `
  (Join-Path $skinDir 'macosskin\FlatFontPolicy.java') `
  (Join-Path $skinDir 'macosskin\WordButton.java') `
  (Join-Path $skinDir 'macosskin\WordTabs.java') `
  (Join-Path $skinDir 'macosskin\WordCombo.java') `
  (Join-Path $skinDir 'macosskin\WordCheck.java') `
  (Join-Path $skinDir 'macosskin\WordField.java') `
  (Join-Path $skinDir 'macosskin\WordTooltip.java') `
  (Join-Path $skinDir 'macosskin\PopupRemap.java') `
  (Join-Path $skinDir 'macosskin\MenuMarks.java') `
  (Join-Path $skinDir 'macosskin\FlatEtchedBorder.java')
if ($LASTEXITCODE -ne 0) { throw "skin yardimcilari derlenemedi (Substance surumu farkli olabilir)" }

# colorschemes resource'larini helper agacina kopyala (resource yolu /macosskin/*.colorschemes)
Copy-Item (Join-Path $skinDir 'macosskin\flatude.colorschemes') (Join-Path $helper 'macosskin\')
Copy-Item (Join-Path $skinDir 'macosskin\flatude-dark.colorschemes') (Join-Path $helper 'macosskin\')

# yardimcilari + colorschemes jar'a ekle (patcher'dan ONCE)
& $jarTool uf $Jar -C $helper .
if ($LASTEXITCODE -ne 0) { throw "yardimcilar jar'a eklenemedi" }

Write-Ok "SkinPatch derleniyor"
& $javac --release 11 -encoding UTF-8 -cp "$jvs" -d $work (Join-Path $skinDir 'SkinPatch.java')
if ($LASTEXITCODE -ne 0) { throw "SkinPatch derlenemedi" }
Write-Ok "SkinPatch calistiriliyor"
& $java -cp "$work;$jvs" SkinPatch $Jar $out
if ($LASTEXITCODE -ne 0) { throw "SkinPatch calismadi" }

& $jarTool uf $Jar -C $out .
if ($LASTEXITCODE -ne 0) { throw "yamali siniflar jar'a eklenemedi" }
Write-Ok "SKIN uygulandi"
