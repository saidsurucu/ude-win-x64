# package.ps1 - Java 11 runtime'i jlink'le ve jpackage ile EXE installer uret
. "$PSScriptRoot\common.ps1"

function New-Runtime {
  $jdk11 = Get-Jdk11Home
  if (-not $jdk11) { throw "JDK 11 yok; once deps calistirin" }
  if (Test-Path (Join-Path $RuntimeDir 'bin\java.exe')) { Write-Ok "runtime zaten var"; return }
  Write-Step "Java 11 runtime jlink'leniyor"
  $jlink = Join-Path $jdk11 'bin\jlink.exe'
  # ALL-MODULE-PATH: tum JDK modulleri (java.smartcardio dahil -> e-imza). Eksik-modul riskini kaldirir.
  & $jlink --add-modules ALL-MODULE-PATH --strip-debug --no-header-files --no-man-pages --compress=2 --output $RuntimeDir
  if ($LASTEXITCODE -ne 0) { throw "jlink basarisiz" }
  Write-Ok "runtime: $([math]::Round((Get-ChildItem $RuntimeDir -Recurse|Measure-Object Length -Sum).Sum/1MB,1)) MB"
}

function Invoke-Package {
  Write-Step "jpackage ile EXE installer uretiliyor"
  $jdk17 = Get-Jdk17Home
  if (-not $jdk17) { throw "JDK 17 (jpackage) yok; once deps calistirin" }
  $wix = Get-WixDir
  if (-not $wix) { throw "WiX yok; once deps calistirin" }

  New-Runtime
  New-Dir $DistDir
  $jpackage = Join-Path $jdk17 'bin\jpackage.exe'
  $tmp = Join-Path $BuildDir 'jptmp'
  if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
  Get-ChildItem $DistDir -Filter *.exe -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue

  # --- ortam ---
  $env:PATH = "$wix;$env:PATH"
  # KRITIK: Turkce locale'de jpackage WiX identifier'larini bozar ("ICON".toLowerCase()->"ıcon").
  # Ingilizce locale ile calistir.
  $env:JAVA_TOOL_OPTIONS = "-Duser.language=en -Duser.country=US"

  $jpArgs = @(
    '--type','exe',
    '--name', $AppName,
    '--app-version', $AppVersion,
    '--input', $InputDir,
    '--main-jar', $MainJar,
    '--main-class', $MainClass,
    '--runtime-image', $RuntimeDir,
    # heap (resmi launcher ile ayni)
    '--java-options','-Xms512M','--java-options','-Xmx4096M',
    # splash
    '--java-options', ('-splash:$APPDIR\' + $SplashGif),
    # NOT: -Dsun.java2d.dpiaware=false / uiScale=1 BILEREK eklenmedi ->
    #      Java 11 native HiDPI olceklemesi -> 4K/yuksek DPI'da KESKIN metin
    '--icon', (Join-Path $ResDir 'uyapicon.ico'),
    '--file-associations', (Join-Path $ResDir 'udf.properties'),
    '--win-menu','--win-shortcut','--win-dir-chooser',
    '--description','Uyap Dokuman Editoru (gayriresmi Windows yapisi)',
    '--vendor', $Vendor,
    '--dest', $DistDir,
    '--temp', $tmp
  )
  # SKIN=1: winlook agent (canli gecis + renk-modu picker). $APPDIR runtime'da app dizinine cozulur.
  if ($env:SKIN -eq '1' -and (Test-Path (Join-Path $InputDir 'winlook.jar'))) {
    $jpArgs += @('--java-options', '-javaagent:$APPDIR\winlook.jar')
    Write-Ok "winlook agent -javaagent olarak baglandi"
  }

  # uygulama argumanlari
  foreach ($a in $AppArgs) { $jpArgs += @('--arguments', $a) }

  & $jpackage @jpArgs
  if ($LASTEXITCODE -ne 0) { throw "jpackage basarisiz (exit $LASTEXITCODE)" }

  $exe = Get-ChildItem $DistDir -Filter *.exe | Select-Object -First 1
  if (-not $exe) { throw "EXE uretilemedi" }
  Write-Ok "EXE: $($exe.FullName) ($([math]::Round($exe.Length/1MB,1)) MB)"

  # --- opsiyonel imzalama ---
  if ($env:SIGN -eq '1') {
    $signtool = (Get-Command signtool.exe -ErrorAction SilentlyContinue).Source
    if ($signtool) {
      Write-Ok "SIGN=1 -> signtool ile imzalaniyor"
      & $signtool sign /a /fd SHA256 /tr http://timestamp.digicert.com /td SHA256 $exe.FullName
    } else {
      Write-Warn2 "SIGN=1 verildi ama signtool.exe yok; imzalama atlandi"
    }
  }
  return $exe.FullName
}

if ($MyInvocation.InvocationName -ne '.') { Invoke-Package }
