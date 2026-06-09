# apply-nativedialog.ps1 - JFileChooser cagrilarini native FileDialog'a kopruler
# patch.ps1 tarafindan (varsayilan acik) cagrilir.
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
$jarTool = Join-Path $jdk 'bin\jar.exe'
$jvs     = Get-Javassist

$work   = Join-Path $BuildDir '_nativedialog'
$bridge = Join-Path $work 'bridge'   # derlenmis NativeFileDialogBridge
$out    = Join-Path $work 'out'      # yamali UDE siniflari
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
New-Dir $bridge; New-Dir $out

# --- 1) kopru sinifini derle ---
Write-Ok "NativeFileDialogBridge derleniyor"
& $javac --release 11 -d $bridge (Join-Path $PSScriptRoot 'NativeFileDialogBridge.java')
if ($LASTEXITCODE -ne 0) { throw "kopru sinifi derlenemedi" }

# --- 2) patcher'i derle + calistir ---
Write-Ok "NativeDialogPatch derleniyor"
& $javac --release 11 -cp "$jvs;$bridge" -d $work (Join-Path $PSScriptRoot 'NativeDialogPatch.java')
if ($LASTEXITCODE -ne 0) { throw "patcher derlenemedi" }
Write-Ok "NativeDialogPatch calistiriliyor"
& $java -cp "$work;$jvs;$bridge" NativeDialogPatch $Jar $bridge $out
if ($LASTEXITCODE -ne 0) { throw "patcher calismadi" }

# --- 3) kopru + yamali siniflari jar'a ekle ---
& $jarTool uf $Jar -C $bridge .
if ($LASTEXITCODE -ne 0) { throw "kopru sinifi jar'a eklenemedi" }
& $jarTool uf $Jar -C $out .
if ($LASTEXITCODE -ne 0) { throw "yamali siniflar jar'a eklenemedi" }
Write-Ok "native dosya diyalogu kopruleri uygulandi"
