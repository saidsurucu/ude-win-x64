# check-icons.ps1 - override PNG'lerini jar resource adlariyla karsilastir.
# Yetim (jar'da olmayan override), @2x eksik, jar'da override'siz (KEEP/orijinal) raporu.
param(
  [string]$Jar,
  [string]$OverridesResDir = (Join-Path $PSScriptRoot 'overrides\resources')
)
. "$PSScriptRoot\..\common.ps1"
if (-not $Jar) { $Jar = Join-Path $InputDir $MainJar }
$jdk = Get-Jdk11Home

# jar icindeki resources/*.png adlari (non-@2x)
$jarPng = & (Join-Path $jdk 'bin\jar.exe') tf $Jar |
  Where-Object { $_ -match '(^|/)resources/[^/]+\.png$' -and $_ -notmatch '@2x\.png$' } |
  ForEach-Object { ($_ -split '/')[-1] } | Sort-Object -Unique
$jarSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$jarPng)

# override resources *.png (non-@2x)
$ov = Get-ChildItem $OverridesResDir -Filter *.png | Where-Object { $_.Name -notmatch '@2x\.png$' } | Select-Object -ExpandProperty Name | Sort-Object
$ovSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$ov)

# @2x istisnalari (compose ikonlar @2x uretmez)
$noTwoX = @('search.png')

$orphan = $ov | Where-Object { -not $jarSet.Contains($_) }
$missing2x = $ov | Where-Object { ($noTwoX -notcontains $_) -and -not (Test-Path (Join-Path $OverridesResDir ($_ -replace '\.png$','@2x.png'))) }
$notOverridden = $jarPng | Where-Object { -not $ovSet.Contains($_) }

Write-Host "=== override sayisi: $($ov.Count) | jar resources png: $($jarPng.Count) ===" -ForegroundColor Cyan
Write-Host "--- YETIM (override var, jar'da YOK -> bosa churn): $($orphan.Count) ---" -ForegroundColor Yellow
$orphan
Write-Host "--- @2x EKSIK (search haric): $($missing2x.Count) ---" -ForegroundColor Yellow
$missing2x
Write-Host "--- jar'da override'SIZ (KEEP/orijinal; bilgi): $($notOverridden.Count) ---" -ForegroundColor DarkGray
if ($orphan.Count -eq 0 -and $missing2x.Count -eq 0) { Write-Host "PARITE TEMIZ" -ForegroundColor Green }
else { Write-Host "PARITE UYARISI (yukariyi incele)" -ForegroundColor Red }
