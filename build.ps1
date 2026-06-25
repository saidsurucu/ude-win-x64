# build.ps1 (kok) - ince giris noktasi -> scripts\build.ps1
# Ornek:
#   .\build.ps1                # tam yapi
#   .\build.ps1 -Icons         # modern ikonlarla
#   .\build.ps1 -Only package  # sadece paketleme
param(
  [switch]$Icons,
  [switch]$NoNativeDialogs,
  [switch]$NoLiveToggle,
  [switch]$NoTableDelete,
  [switch]$NoPasteRich,
  [switch]$NoPlainPaste,
  [switch]$Skin,
  [switch]$Sign,
  [string]$UdeUrl,
  [ValidateSet('deps','download','patch','package','all')]
  [string]$Only = 'all'
)
& "$PSScriptRoot\scripts\build.ps1" @PSBoundParameters
