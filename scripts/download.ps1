# download.ps1 - resmi UDE paketini cek, editor-app.jar + kaynaklarini cikar
# Java siniflari platformdan bagimsizdir; en guvenilir duz-zip kaynak MAC paketidir.
# 2026-08 / UDE 5.4.19: satici hem link adlandirmasini hem de jar duzenini degistirdi
#   link : uyapdokumaneditoru*.zip -> UyapDokumanEditoru-AppleSilicon-<surum>.zip (buyuk harf!)
#   jar  : tek editor-app.jar -> editor_laf/editor_lib/editor_lib2/editor_utility/
#          jai_hvl/jdom/updater (7 jar). Tum yamalarimiz tek jar varsayar -> birlestiririz.
. "$PSScriptRoot\common.ps1"

function Resolve-UdeUrl {
  if ($env:UDE_URL) { return $env:UDE_URL }
  Write-Ok "indirme linki cozuluyor: $UdeDownloadPage"
  $html = (Invoke-WebRequest -Uri $UdeDownloadPage -UseBasicParsing).Content
  # Buyuk/kucuk harf DUYARSIZ ara (yeni adlandirma "UyapDokumanEditoru-...").
  $rx = '(?i)(?:https?:)?//rayp\.adalet\.gov\.tr/[^"''> ]*uyapdokumaneditoru[^"''> ]*\.zip'
  $all = [regex]::Matches($html, $rx) | ForEach-Object { $_.Value } | Select-Object -Unique
  if (-not $all) { throw "UDE zip linki sayfada bulunamadi. UDE_URL ile elle verin." }
  # Intel/ARM ayrimi jar icerigini degistirmez; tek paket duzeninde de calissin diye
  # once Apple Silicon, yoksa ilk MAC paketi secilir.
  $u = @($all | Where-Object { $_ -match '(?i)applesilicon|arm64|aarch64' })[0]
  if (-not $u) { $u = $all[0] }
  if ($u -notmatch '^https?:') { $u = 'https:' + $u }
  return $u
}

# 5.4.19+ : bolunmus jar'lari sinif yolu SIRASIYLA tek editor-app.jar'a birlestirir.
# Ayni adli giris birden fazla jar'da varsa ILK gelen kazanir (JVM sinif-yolu davranisi).
# KRITIK: birlestirme zip->zip yapilir, diske ACILMAZ. Dosya sistemi buyuk/kucuk harf
# duyarsiz oldugu icin (Windows da, macOS da) obfuscate sinif adlari (kx / kX gibi)
# birbirini ezer; olculdu: diske acan yol 846 sinifi sessizce yiyor.
function Merge-EditorJars($zipArchive, [string]$destJar, [string[]]$order) {
  $jars = @{}
  foreach ($e in $zipArchive.Entries) {
    # macOS AppleDouble artiklari (._editor_lib.jar, __MACOSX/...) jar DEGILDIR;
    # zip olarak acmaya calisirsak "End of Central Directory record could not be found".
    if ($e.Name -like '._*' -or $e.FullName -like '*__MACOSX/*') { continue }
    if ($e.FullName -match '(?i)Contents/Java/([^/]+\.jar)$') { $jars[$Matches[1]] = $e }
  }
  # Info.plist'te anilmayan jar kalirsa (satici yeni jar eklerse) sona ekle.
  $list = @($order | Where-Object { $jars.ContainsKey($_) })
  $list += @($jars.Keys | Where-Object { $order -notcontains $_ } | Sort-Object)
  if (-not $list) { throw "pakette jar bulunamadi" }
  Write-Ok "birlestiriliyor: $($list -join ', ')"

  if (Test-Path $destJar) { Remove-Item $destJar -Force }
  $out  = [System.IO.Compression.ZipFile]::Open($destJar, 'Create')
  $seen = New-Object 'System.Collections.Generic.HashSet[string]'   # ordinal = harf duyarli
  try {
    $mf = $out.CreateEntry('META-INF/MANIFEST.MF')
    $sw = New-Object System.IO.StreamWriter($mf.Open())
    $sw.Write("Manifest-Version: 1.0`nMain-Class: $MainClass`n`n"); $sw.Close()
    [void]$seen.Add('META-INF/MANIFEST.MF')

    foreach ($name in $list) {
      $ms = New-Object System.IO.MemoryStream
      $src = $jars[$name].Open(); $src.CopyTo($ms); $src.Close(); $ms.Position = 0
      $inner = New-Object System.IO.Compression.ZipArchive($ms, [System.IO.Compression.ZipArchiveMode]::Read)
      try {
        foreach ($ie in $inner.Entries) {
          $n = $ie.FullName
          if ($n.EndsWith('/')) { continue }
          if ($n -eq 'META-INF/MANIFEST.MF') { continue }
          if ($n -match '(?i)^META-INF/.*\.(SF|RSA|DSA)$') { continue }
          if (-not $seen.Add($n)) { continue }     # ilk gelen kazanir
          $oe = $out.CreateEntry($n)
          $i = $ie.Open(); $o = $oe.Open(); $i.CopyTo($o); $o.Close(); $i.Close()
        }
      } finally { $inner.Dispose(); $ms.Dispose() }
    }
  } finally { $out.Dispose() }
  return $seen.Count
}

function Get-PlistText($zipArchive) {
  # Sadece UYGULAMANIN Info.plist'i (gomulu zulu-8.jre'ninki ve AppleDouble ._ artigi degil).
  $e = $zipArchive.Entries |
         Where-Object { $_.Name -notlike '._*' -and $_.FullName -match '(?i)\.app/Contents/Info\.plist$' } |
         Select-Object -First 1
  if (-not $e) { return $null }
  $s = $e.Open(); $r = New-Object System.IO.StreamReader($s)
  $t = $r.ReadToEnd(); $r.Close(); $s.Close()
  return $t
}

function Get-JvmClassPath($zipArchive) {
  $fallback = @('editor_laf.jar','editor_lib.jar','editor_lib2.jar','editor_utility.jar','jai_hvl.jar','jdom.jar','updater.jar')
  $t = Get-PlistText $zipArchive
  if (-not $t) { return $fallback }
  $m = [regex]::Match($t, '<key>JVMClassPath</key>\s*<array>(.*?)</array>', 'Singleline')
  if (-not $m.Success) { return $fallback }
  $r = @([regex]::Matches($m.Groups[1].Value, '<string>[^<]*/Contents/Java/([^<]+\.jar)</string>') |
          ForEach-Object { $_.Groups[1].Value })
  if ($r.Count -gt 0) { return $r } else { return $fallback }
}

function Get-UdeVersion($zipArchive) {
  $t = Get-PlistText $zipArchive
  if (-not $t) { return $null }
  $m = [regex]::Match($t, '<key>CFBundleVersion</key>\s*<string>([^<]+)</string>', 'Singleline')
  if ($m.Success) { return $m.Groups[1].Value.Trim() }
  return $null
}

function Invoke-Download {
  Write-Step "UDE paketi indiriliyor"
  New-Dir $DownloadDir; New-Dir $InputDir; New-Dir $ResDir
  $url = Resolve-UdeUrl
  Write-Ok "kaynak: $url"
  $zip = Join-Path $DownloadDir 'ude-src.zip'
  # Onbellek SURUM-duyarli: indirilen paketin linki damgalanir. Sayfadaki guncel link
  # degistiyse (yeni UDE surumu) eski zip atilir; yoksa "guncelle" diye komutu tekrar
  # calistiran kullanici sessizce eski surumu yeniden paketlerdi.
  $stamp = Join-Path $DownloadDir 'ude-src.url'
  $cached = if (Test-Path $stamp) { (Get-Content $stamp -Raw).Trim() } else { '' }
  if ((Test-Path $zip) -and $cached -ne $url) {
    Write-Ok "yeni UDE paketi bulundu; onbellek yenileniyor"
    Remove-Item $zip -Force
  }
  if (-not (Test-Path $zip)) {
    & curl.exe -L -s -o $zip $url
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $zip)) { throw "UDE paketi indirilemedi" }
    Set-Content -Path $stamp -Value $url -Encoding ascii
  }
  Write-Ok "indirildi: $([math]::Round((Get-Item $zip).Length/1MB,1)) MB"

  # --- zip icinden kaynaklari cikar ---
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $z = [System.IO.Compression.ZipFile]::OpenRead($zip)
  try {
    function Extract-One($matchRx, $destDir) {
      $e = $z.Entries | Where-Object { $_.FullName -match $matchRx } | Select-Object -First 1
      if (-not $e) { return $null }
      $name = Split-Path ($e.FullName -replace '/','\') -Leaf
      $dest = Join-Path $destDir $name
      $fs = $e.Open(); $out = [System.IO.File]::Create($dest); $fs.CopyTo($out); $out.Close(); $fs.Close()
      return $dest
    }
    $jar = Extract-One 'Contents/Java/editor-app\.jar$' $InputDir
    if (-not $jar) {
      # 5.4.19+ duzeni: tek jar yok, sinif yolu sirasiyla birlestir.
      $order = Get-JvmClassPath $z
      $jar   = Join-Path $InputDir $MainJar
      $n     = Merge-EditorJars $z $jar $order
      Write-Ok "editor-app.jar birlestirildi ($n giris)"
    }
    if (-not (Test-Path $jar)) { throw "editor-app.jar pakette bulunamadi" }
    Write-Ok "editor-app.jar hazir ($([math]::Round((Get-Item $jar).Length/1MB,1)) MB)"
    $ver = Get-UdeVersion $z
    if ($ver) {
      Set-Content -Path (Join-Path $DownloadDir 'ude-version.txt') -Value $ver -Encoding ascii
      # common.ps1 surumu dot-source aninda (indirmeden ONCE) hesaplar; ilk yapida
      # dosya henuz yoktu -> paketleme oncesi guncelle.
      if (-not $env:UDE_VERSION) { $script:AppVersion = $ver }
      Write-Ok "UDE surumu: $ver"
    }
    Extract-One ([regex]::Escape("Contents/Java/$SplashGif") + '$') $InputDir | Out-Null
    Extract-One 'Contents/Java/sablon_editor_splash_screen_animated\.gif$' $InputDir | Out-Null
    Extract-One 'Contents/Java/BENIOKU\.txt$' $InputDir | Out-Null
    Extract-One 'Contents/Java/uyapicon\.ico$'    $ResDir | Out-Null
    Extract-One 'Contents/Java/uyap_ki_icon\.ico$' $ResDir | Out-Null
  } finally { $z.Dispose() }

  # --- .udf dosya iliskilendirme properties (ileri-slash; Java properties escape sorunu icin) ---
  $kiIcon = (Join-Path $ResDir 'uyap_ki_icon.ico') -replace '\\','/'
  @"
extension=udf
mime-type=application/x-uyap-udf
description=Uyap Dokuman Editoru Belgesi
icon=$kiIcon
"@ | Set-Content -Path (Join-Path $ResDir 'udf.properties') -Encoding ascii
  Write-Ok "kaynaklar hazir: $InputDir"
}

if ($MyInvocation.InvocationName -ne '.') { Invoke-Download }
