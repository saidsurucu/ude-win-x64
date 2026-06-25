# patch.ps1 - editor-app.jar uzerinde Windows yamalari
#
# NOT: macOS portundaki yamalarin cogu Windows'ta GEREKSIZ:
#  - sqlite-jdbc swap: jar zaten native/Windows/amd64/sqlitejdbc.dll iceriyor -> swap yok
#  - com.apple.eawt strip / eawt-shim: jar kendi eawt siniflarini gomulu getiriyor;
#    Windows JDK'da java.desktop icinde com.apple.eawt YOK -> cakisma yok, Mac kod yolu
#    Windows'ta tetiklenmez (inert) -> strip/shim yok
#  - Cmd remap / trackpad zoom: Mac'e ozel -> yok
#
# Geriye yalniz OPSIYONEL ikon modernizasyonu kaliyor (ICONS=1).
. "$PSScriptRoot\common.ps1"

function Invoke-Patch {
  Write-Step "Yama asamasi"
  $jar = Join-Path $InputDir $MainJar
  if (-not (Test-Path $jar)) { throw "editor-app.jar bulunamadi; once download calistirin" }

  # --- modern ikonlar (opsiyonel, ICONS=1) ---
  if ($env:ICONS -eq '1') {
    $iconScript = Join-Path $PSScriptRoot 'icons\apply-icons.ps1'
    if (Test-Path $iconScript) {
      Write-Ok "ICONS=1 -> modern ikonlar uygulaniyor"
      & $iconScript -Jar $jar
    } else {
      Write-Warn2 "ICONS=1 verildi ama icons\apply-icons.ps1 yok; atlaniyor"
    }
  } else {
    Write-Ok "ikon modernizasyonu kapali (etkinlestirmek icin ICONS=1)"
  }

  # --- native Windows dosya diyalogu (varsayilan ACIK, NATIVE_DIALOGS=0 ile kapanir) ---
  if ($env:NATIVE_DIALOGS -eq '0') {
    Write-Ok "native dosya diyalogu kapali (NATIVE_DIALOGS=0)"
  } else {
    $ndScript = Join-Path $PSScriptRoot 'nativedialog\apply-nativedialog.ps1'
    if (Test-Path $ndScript) {
      Write-Ok "native Windows ac/kaydet diyalogu uygulaniyor"
      & $ndScript -Jar $jar
    } else {
      Write-Warn2 "nativedialog\apply-nativedialog.ps1 yok; atlaniyor"
    }
  }

  # --- LIVETOGGLE (varsayilan ACIK, =0 ile kapanir) ---
  if ($env:LIVETOGGLE -eq '0') {
    Write-Ok "livetoggle kapali (LIVETOGGLE=0)"
  } else {
    $ltScript = Join-Path $PSScriptRoot 'livetoggle\apply-livetoggle.ps1'
    if (Test-Path $ltScript) {
      Write-Ok "LIVETOGGLE uygulaniyor"
      & $ltScript -Jar $jar
    } else { Write-Warn2 "livetoggle\apply-livetoggle.ps1 yok; atlaniyor" }
  }

  # --- TABLEDELETE (varsayilan ACIK, =0 ile kapanir) ---
  if ($env:TABLEDELETE -eq '0') {
    Write-Ok "tabledelete kapali (TABLEDELETE=0)"
  } else {
    $tdScript = Join-Path $PSScriptRoot 'tabledelete\apply-tabledelete.ps1'
    if (Test-Path $tdScript) {
      Write-Ok "TABLEDELETE uygulaniyor"
      & $tdScript -Jar $jar
    } else { Write-Warn2 "tabledelete\apply-tabledelete.ps1 yok; atlaniyor" }
  }

  # --- IMGFULL / IMGRESIZE / ANTET / PDFFRESH (varsayilan ACIK, =0 kapatir) ---
  foreach ($feat in @(
    @{ env='IMGFULL';   name='IMGFULL';   script='imagefull\apply-imagefull.ps1' },
    @{ env='IMGRESIZE'; name='IMGRESIZE'; script='imgresize\apply-imgresize.ps1' },
    @{ env='ANTET';     name='ANTET';     script='antet\apply-antet.ps1' },
    @{ env='PDFFRESH';  name='PDFFRESH';  script='pdffresh\apply-pdffresh.ps1' },
    @{ env='PASTEIMG';  name='PASTEIMG';  script='pasteimg\apply-pasteimg.ps1' }
  )) {
    if ([Environment]::GetEnvironmentVariable($feat.env) -eq '0') {
      Write-Ok "$($feat.name) kapali ($($feat.env)=0)"
    } else {
      $fs = Join-Path $PSScriptRoot $feat.script
      if (Test-Path $fs) { Write-Ok "$($feat.name) uygulaniyor"; & $fs -Jar $jar }
      else { Write-Warn2 "$($feat.script) yok; atlaniyor" }
    }
  }

  # --- PASTERICH (harici stilli yapistirma) + PLAINPASTE (varsayilan ACIK, =0 kapatir) ---
  # NOT: PASTEIMG port edilmedi - Mac'e ozgu (Windows'ta panodan imaj zaten BufferedImage doner, sorunsuz).
  if ($env:PASTERICH -eq '0') {
    Write-Ok "pasterich kapali (PASTERICH=0)"
  } else {
    $prScript = Join-Path $PSScriptRoot 'pasterich\apply-pasterich.ps1'
    if (Test-Path $prScript) {
      $pp = ($env:PLAINPASTE -ne '0')
      Write-Ok ("PASTERICH uygulaniyor" + $(if($pp){' + PLAINPASTE'}))
      & $prScript -Jar $jar -PlainPaste:$pp
    } else { Write-Warn2 "pasterich\apply-pasterich.ps1 yok; atlaniyor" }
  }

  # --- SKIN (spike; varsayilan KAPALI, SKIN=1 ile acilir) ---
  if ($env:SKIN -eq '1') {
    $skinScript = Join-Path $PSScriptRoot 'skin\apply-skin.ps1'
    if (Test-Path $skinScript) {
      Write-Ok "SKIN uygulaniyor"
      & $skinScript -Jar $jar
    } else { Write-Warn2 "skin\apply-skin.ps1 yok; atlaniyor" }
  } else {
    Write-Ok "SKIN kapali (etkinlestirmek icin SKIN=1)"
  }

  Write-Ok "yama tamam"
}

if ($MyInvocation.InvocationName -ne '.') { Invoke-Patch }
