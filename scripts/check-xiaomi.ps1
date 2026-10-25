param(
    [string]$Serial,
    [switch]$Install,
    [switch]$RunTests,
    [string]$AppApk = 'artifacts/android-phone/composeApp-phone-debug.apk',
    [string]$TestApk = 'composeApp/build/outputs/apk/androidTest/debug/composeApp-debug-androidTest.apk'
)
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location $workspace
try {
    $adbCommand = Get-Command adb -ErrorAction Stop
    $adbPath = $adbCommand.Source
    $deviceLines = & $adbPath devices -l
    $physical = @($deviceLines | Where-Object { $_ -match '^(\S+)\s+device\b' -and $_ -notmatch '^emulator-' })
    if (-not $Serial) {
        if ($physical.Count -ne 1) { throw 'Connect one authorized Xiaomi by USB, or specify -Serial for the intended physical device.' }
        $Serial = ($physical[0] -split '\s+')[0]
    }
    if (-not ($physical | Where-Object { ($_ -split '\s+')[0] -eq $Serial })) { throw 'The selected physical device is not connected and authorized.' }
    function Invoke-Device {
        param([string[]]$Arguments)
        $result = & $adbPath -s $Serial @Arguments
        if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Arguments[0])" }
        return $result
    }
    $manufacturer = (Invoke-Device -Arguments @('shell','getprop','ro.product.manufacturer') | Out-String).Trim()
    $model = (Invoke-Device -Arguments @('shell','getprop','ro.product.model') | Out-String).Trim()
    $android = (Invoke-Device -Arguments @('shell','getprop','ro.build.version.release') | Out-String).Trim()
    $api = (Invoke-Device -Arguments @('shell','getprop','ro.build.version.sdk') | Out-String).Trim()
    Write-Output "Connected: $manufacturer $model; Android $android / API $api"
    if ($manufacturer -notmatch '(?i)xiaomi|redmi') { throw 'The selected device is not identified as Xiaomi/Redmi; no app was changed.' }
    $ready = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/readyz' -TimeoutSec 5
    if ($ready.status -ne 'ready') { throw 'Start the local API on port 8080 before device verification.' }
    Invoke-Device -Arguments @('reverse','tcp:8080','tcp:8080') | Out-Null
    if ($Install -or $RunTests) {
        if (-not (Test-Path -LiteralPath $AppApk)) { throw 'Build the phone debug APK with -PmasterCoffeeApiUrl=http://127.0.0.1:8080/api/v1 first.' }
        # Update in place: no uninstall, package-data clear, or changes to the user's AVD.
        Invoke-Device -Arguments @('install','-r',(Resolve-Path -LiteralPath $AppApk).Path) | Write-Output
    }
    $evidencePath = Join-Path $workspace ('artifacts/device-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    New-Item -ItemType Directory -Path $evidencePath -Force | Out-Null
    @{manufacturer=$manufacturer; model=$model; android=$android; api=$api; testsRequested=[bool]$RunTests; time=(Get-Date).ToUniversalTime().ToString('o')} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidencePath 'device.json') -Encoding utf8
    if ($RunTests) {
        if (-not $env:DEMO_PASSWORD) { throw 'Set DEMO_PASSWORD to the local development staff password before the live backend test.' }
        Invoke-Device -Arguments @('install','-r',(Resolve-Path -LiteralPath $TestApk).Path) | Write-Output
        $testOutput = Invoke-Device -Arguments @('shell','am','instrument','-w','-r',
            '-e','masterCoffeeLiveApi','http://127.0.0.1:8080/api/v1',
            '-e','masterCoffeeStaffEmail','staff@mastercoffee.local',
            '-e','masterCoffeeStaffPassword',$env:DEMO_PASSWORD,
            'kz.master_coffee.app.test/androidx.test.runner.AndroidJUnitRunner')
        $testText = $testOutput -join "`n"
        $testText | Set-Content -LiteralPath (Join-Path $evidencePath 'instrumentation.txt') -Encoding utf8
        if ($testText -notmatch 'OK \([1-9]\d* tests?\)' -or $testText -notmatch 'masterCoffeeLiveOrderComplete=true' -or $testText -match 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|Process crashed|shortMsg=') {
            throw "Instrumentation failed; see $evidencePath"
        }
        Write-Output 'PASS: managed Android UI tests and live backend workflow.'
        @{result='PASS';liveBackend=$true;physicalMainActivityJourney=$false} | ConvertTo-Json |
            Set-Content -LiteralPath (Join-Path $evidencePath 'test-result.json') -Encoding utf8
    }
    Invoke-Device -Arguments @('shell','am','start','-n','kz.master_coffee.app/.MainActivity') | Write-Output
    Write-Output "Evidence: $evidencePath"
    Write-Output 'Camera, physical TalkBack, screenshots and manual lifecycle/network checks still need actual observation.'
} finally { Pop-Location }
