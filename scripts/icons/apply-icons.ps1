# apply-icons.ps1 - modern Material ikon override + HiDPI yukleyici yamasi
# patch.ps1 -> ICONS=1 oldugunda cagrilir.  Platform-bagimsiz mantik (Mac portuyla ayni):
#   1) overrides\ (resources\*.png + @2x) jar'a enjekte edilir (UDE ikonlarinin uzerine)
#   2) IconLoaderPatch (Javassist) ile Utils.b multi-resolution + disabled-ikon soluklastirma
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
if (-not $jdk) { throw "JDK 11 yok; once deps calistirin" }
$javac   = Join-Path $jdk 'bin\javac.exe'
$java    = Join-Path $jdk 'bin\java.exe'
$jarTool = Join-Path $jdk 'bin\jar.exe'   # NOT: $jar parametre $Jar ile cakisir (PS case-insensitive)
$jvs   = Get-Javassist
$overrides = Join-Path $PSScriptRoot 'overrides'

# --- 1) override asset'lerini enjekte et ---
$pngCount = (Get-ChildItem $overrides -Recurse -Filter *.png).Count
Write-Ok "override ikonlari enjekte ediliyor ($pngCount png)"
& $jarTool uf $Jar -C $overrides .
if ($LASTEXITCODE -ne 0) { throw "ikon override enjeksiyonu basarisiz" }

# --- 2) Utils.b multi-resolution + disabled soluklastirma (Javassist) ---
$work = Join-Path $BuildDir '_iconpatch'
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
New-Dir (Join-Path $work 'out')
Write-Ok "IconLoaderPatch derleniyor"
& $javac --release 11 -cp $jvs -d $work (Join-Path $PSScriptRoot 'IconLoaderPatch.java')
if ($LASTEXITCODE -ne 0) { throw "IconLoaderPatch derlenemedi" }
Write-Ok "IconLoaderPatch calistiriliyor"
& $java -cp "$work;$jvs" IconLoaderPatch $Jar (Join-Path $work 'out')
if ($LASTEXITCODE -ne 0) { throw "IconLoaderPatch calismadi" }
& $jarTool uf $Jar -C (Join-Path $work 'out') .
if ($LASTEXITCODE -ne 0) { throw "yamali siniflar jar'a eklenemedi" }
Write-Ok "modern ikonlar uygulandi (Utils.b multi-res + disabled soluklastirma)"
