# kur.ps1 - tek satirlik bootstrap kurulum
#
#   irm https://raw.githubusercontent.com/saidsurucu/ude-win-x64/main/kur.ps1 | iex
#
# Yaptiklari: repoyu indir -> JDK 11/17 + WiX temin et -> resmi UDE paketini cek ->
# editor-app.jar'i Java 11 runtime ile EXE installer'a paketle -> installer'i baslat.
# Hicbir sey sistem geneline elle kurulmaz; araclar repo altinda vendor/ icinde kalir.

$ErrorActionPreference = 'Stop'
$ProgressPreference    = 'SilentlyContinue'

$Repo    = 'saidsurucu/ude-win-x64'
$Branch  = if ($env:UDE_WIN_BRANCH) { $env:UDE_WIN_BRANCH } else { 'main' }
$WorkDir = if ($env:UDE_WIN_DIR) { $env:UDE_WIN_DIR } else { Join-Path $env:USERPROFILE 'ude-win-x64' }

function Say($m){ Write-Host "==> $m" -ForegroundColor Cyan }

Write-Host ""
Write-Host "  UDE Windows x64 - kurulum bootstrap" -ForegroundColor Magenta
Write-Host ""

# --- 1) repoyu indir/guncelle ---
$haveGit = [bool](Get-Command git -ErrorAction SilentlyContinue)
if ($haveGit -and (Test-Path (Join-Path $WorkDir '.git'))) {
  Say "repo guncelleniyor: $WorkDir"
  git -C $WorkDir pull --ff-only | Out-Null
} elseif ($haveGit) {
  Say "repo klonlaniyor -> $WorkDir"
  if (Test-Path $WorkDir) { Remove-Item $WorkDir -Recurse -Force }
  git clone --depth 1 -b $Branch "https://github.com/$Repo.git" $WorkDir | Out-Null
} else {
  Say "repo zip olarak indiriliyor -> $WorkDir"
  $zip = Join-Path $env:TEMP "ude-win-x64-$Branch.zip"
  & curl.exe -L -s -o $zip "https://github.com/$Repo/archive/refs/heads/$Branch.zip"
  if (Test-Path $WorkDir) { Remove-Item $WorkDir -Recurse -Force }
  $tmp = Join-Path $env:TEMP "ude-win-x64-extract"
  if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
  Expand-Archive -Path $zip -DestinationPath $tmp -Force
  $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
  Move-Item $inner.FullName $WorkDir
}

# --- 2) yapiyi calistir ---
Say "yapi baslatiliyor (araclar + UDE indiriliyor; ilk sefer birkac dakika surebilir)"
$build = Join-Path $WorkDir 'build.ps1'
# TUM ozellikler (ikonlar, modern gorunum, duzenleme) varsayilan ACIK.
# Tek tek kapatmak icin once env verin, orn: $env:SKIN='0' veya $env:ICONS='0'
powershell -NoProfile -ExecutionPolicy Bypass -File $build
if ($LASTEXITCODE -ne 0) { throw "yapi basarisiz oldu (exit $LASTEXITCODE)" }

# --- 3) installer'i baslat ---
$exe = Get-ChildItem (Join-Path $WorkDir 'dist') -Filter *.exe -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $exe) { throw "EXE installer bulunamadi" }
Say "installer baslatiliyor: $($exe.Name)"
Write-Host "    (Windows SmartScreen uyarisi cikarsa: 'Daha fazla bilgi' -> 'Yine de calistir')" -ForegroundColor Yellow
Start-Process -FilePath $exe.FullName

Write-Host ""
Write-Host "  Tamam. Kurulum sihirbazini tamamlayin." -ForegroundColor Green
Write-Host "  EXE: $($exe.FullName)" -ForegroundColor Green
