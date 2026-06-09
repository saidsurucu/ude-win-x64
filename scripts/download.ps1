# download.ps1 - resmi UDE paketini cek, editor-app.jar + kaynaklarini cikar
# editor-app.jar platformdan bagimsizdir; en guvenilir duz-zip kaynak uyapdokumaneditoru*.zip
. "$PSScriptRoot\common.ps1"

function Resolve-UdeUrl {
  if ($env:UDE_URL) { return $env:UDE_URL }
  Write-Ok "indirme linki cozuluyor: $UdeDownloadPage"
  $html = (Invoke-WebRequest -Uri $UdeDownloadPage -UseBasicParsing).Content
  $rx = '(?:https?:)?//rayp\.adalet\.gov\.tr/[^"''> ]*uyapdokumaneditoru[^"''> ]*\.zip'
  $m = [regex]::Match($html, $rx)
  if (-not $m.Success) { throw "UDE zip linki sayfada bulunamadi. UDE_URL ile elle verin." }
  $u = $m.Value
  if ($u -notmatch '^https?:') { $u = 'https:' + $u }
  return $u
}

function Invoke-Download {
  Write-Step "UDE paketi indiriliyor"
  New-Dir $DownloadDir; New-Dir $InputDir; New-Dir $ResDir
  $url = Resolve-UdeUrl
  Write-Ok "kaynak: $url"
  $zip = Join-Path $DownloadDir 'ude-src.zip'
  if (-not (Test-Path $zip)) {
    & curl.exe -L -s -o $zip $url
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $zip)) { throw "UDE paketi indirilemedi" }
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
    if (-not $jar) { throw "editor-app.jar pakette bulunamadi" }
    Write-Ok "editor-app.jar cikarildi ($([math]::Round((Get-Item $jar).Length/1MB,1)) MB)"
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
