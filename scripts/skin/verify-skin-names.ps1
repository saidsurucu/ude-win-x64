# verify-skin-names.ps1 - SKIN 7a obfuscate hedeflerini Windows jar'inda dogrula
. "$PSScriptRoot\..\common.ps1"
$jar = Join-Path $InputDir $MainJar
$javap = Join-Path (Get-Jdk11Home) 'bin\javap.exe'
function S($c,$pat){ Write-Host "== $c" -ForegroundColor Cyan; & $javap -p -classpath "$jar" $c 2>&1 | Select-String $pat }
$P='tr.com.havelsan.uyap.system.editor.common.'
S ($P+'an') 'class|void.*\(|public'
S ($P+'gui.eV') '\b(a|b|c)\(\)|setColor_border|Color (d|e);'
S 'tr.gov.uyap.system.a.b.a.a.t' 'focusGained'
S 'org.pushingpixels.flamingo.internal.utils.FlamingoUtilities' 'getBorderColor'
S 'org.pushingpixels.flamingo.internal.ui.ribbon.BasicRibbonUI' 'paintTaskArea'
S 'org.pushingpixels.flamingo.internal.ui.common.BasicCommandButtonUI' 'paintButtonBackground'
S 'org.pushingpixels.flamingo.internal.ui.ribbon.BasicRibbonTaskToggleButtonUI' 'paintButtonBackground'
S 'org.pushingpixels.flamingo.internal.ui.ribbon.appmenu.BasicRibbonApplicationMenuButtonUI' 'paintButtonBackground'
S 'org.pushingpixels.flamingo.internal.ui.common.BasicRichTooltipPanelUI' 'paintBackground'
