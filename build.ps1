# build.ps1 (kok) - ince giris noktasi -> scripts\build.ps1
# Ornek:
#   .\build.ps1                # tam yapi (TUM ozellikler varsayilan ACIK)
#   .\build.ps1 -NoSkin        # modern gorunum olmadan
#   .\build.ps1 -Only package  # sadece paketleme
param(
  [switch]$Icons,
  [switch]$NoIcons,
  [switch]$NoNativeDialogs,
  [switch]$NoLiveToggle,
  [switch]$NoTableDelete,
  [switch]$NoPasteRich,
  [switch]$NoPlainPaste,
  [switch]$NoImgFull,
  [switch]$NoImgResize,
  [switch]$NoAntet,
  [switch]$NoPdfFresh,
  [switch]$NoPasteImg,
  [switch]$NoFopFonts,
  [switch]$NoCaretFix,
  [switch]$NoZoomKeys,
  [switch]$NoFileAssoc,
  [switch]$NoLineSpacing,
  [switch]$Skin,
  [switch]$NoSkin,
  [switch]$Sign,
  [string]$UdeUrl,
  [ValidateSet('deps','download','patch','package','all')]
  [string]$Only = 'all'
)
& "$PSScriptRoot\scripts\build.ps1" @PSBoundParameters
