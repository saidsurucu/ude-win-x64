# apply-fopfonts.ps1 - PDF disa aktarimda Turkce harf (gGsSiI) duzeltmesi
# Liberation fontlarini gomer: FOP (b.a) + iText (b.c) + sayfa boyutu (b.b) yamalanir.
# fopfonts/ bundle (8 TTF + 8 metrik) InputDir'e konur -> jpackage --input ile app/'e gelir.
param([Parameter(Mandatory)][string]$Jar)
. "$PSScriptRoot\..\common.ps1"
$JavassistVer='3.30.2-GA'; $JavassistUrl="https://repo1.maven.org/maven2/org/javassist/javassist/$JavassistVer/javassist-$JavassistVer.jar"
function Get-Javassist { $lib=Join-Path $VendorDir 'lib'; New-Dir $lib; $jvs=Join-Path $lib "javassist-$JavassistVer.jar"
  if (-not (Test-Path $jvs)) { & curl.exe -L -s -o $jvs $JavassistUrl; if ($LASTEXITCODE -ne 0) { throw "javassist indirilemedi" } }; return $jvs }
$jdk=Get-Jdk11Home; if (-not $jdk) { throw "JDK 11 yok" }
$javac=Join-Path $jdk 'bin\javac.exe'; $java=Join-Path $jdk 'bin\java.exe'; $jarTool=Join-Path $jdk 'bin\jar.exe'; $jvs=Get-Javassist; $dir=$PSScriptRoot
$work=Join-Path $BuildDir '_fopfonts'; if (Test-Path $work){Remove-Item $work -Recurse -Force}; New-Dir $work
$bundle=Join-Path $InputDir 'fopfonts'; if (Test-Path $bundle){Remove-Item $bundle -Recurse -Force}; New-Dir $bundle

# Liberation TTF -> metrik XML eslemesi (FopFonts'un bekledigi adlar)
$map=@(
  @('LiberationSerif-Regular.ttf','libserif.xml'),
  @('LiberationSerif-Bold.ttf','libserif-bold.xml'),
  @('LiberationSerif-Italic.ttf','libserif-italic.xml'),
  @('LiberationSerif-BoldItalic.ttf','libserif-bolditalic.xml'),
  @('LiberationSans-Regular.ttf','libsans.xml'),
  @('LiberationSans-Bold.ttf','libsans-bold.xml'),
  @('LiberationSans-Italic.ttf','libsans-italic.xml'),
  @('LiberationSans-BoldItalic.ttf','libsans-bolditalic.xml')
)
Write-Ok "Liberation FOP metrikleri uretiliyor (TTFReader)"
foreach ($e in $map) {
  $ttf=Join-Path $dir "fonts\$($e[0])"
  if (-not (Test-Path $ttf)) { throw "FOPFONTS: font eksik: $($e[0])" }
  $outxml=Join-Path $bundle $e[1]
  & $java -cp "$Jar" org.apache.fop.fonts.apps.TTFReader $ttf $outxml | Out-Null
  if (-not (Test-Path $outxml)) { throw "FOPFONTS: metrik uretilemedi: $($e[1])" }
  Copy-Item $ttf (Join-Path $bundle $e[0])   # TTF'yi de bundle'a koy
}
# Build-zamani self-check (Codex): 8 TTF + 8 metrik bundle'da olmali, yoksa GUMBUR cik
$nT=(Get-ChildItem "$bundle\*.ttf"|Measure-Object).Count; $nX=(Get-ChildItem "$bundle\*.xml"|Measure-Object).Count
if ($nT -ne 8 -or $nX -ne 8) { throw "FOPFONTS self-check basarisiz: TTF=$nT XML=$nX (8/8 beklenir)" }
Write-Ok "fopfonts bundle hazir ($nT TTF + $nX metrik) -> $bundle"

# Yardimci siniflar (macosfop.*) jar'a enjekte
Write-Ok "macosfop yardimcilari derleniyor"
$hc=Join-Path $work 'helper'; New-Dir $hc
& $javac --release 11 -encoding UTF-8 -nowarn -cp "$Jar" -d $hc (Get-ChildItem (Join-Path $dir 'macosfop\*.java') | % FullName)
if ($LASTEXITCODE -ne 0) { throw "macosfop derlenemedi" }
& $jarTool uf $Jar -C $hc .
if ($LASTEXITCODE -ne 0) { throw "macosfop jar'a eklenemedi" }

# FopConfigPatch (b.a / b.c / b.b)
Write-Ok "FopConfigPatch derleniyor + calistiriliyor"
$out=Join-Path $work 'out'; New-Dir $out
& $javac --release 11 -encoding UTF-8 -cp "$jvs;$hc" -d $work (Join-Path $dir 'FopConfigPatch.java')
if ($LASTEXITCODE -ne 0) { throw "FopConfigPatch derlenemedi" }
& $java -cp "$work;$jvs;$hc" FopConfigPatch $Jar $out
if ($LASTEXITCODE -ne 0) { throw "FopConfigPatch calismadi" }
& $jarTool uf $Jar -C $out .
if ($LASTEXITCODE -ne 0) { throw "yamali siniflar eklenemedi" }
Write-Ok "FOPFONTS uygulandi (Turkce PDF)"
