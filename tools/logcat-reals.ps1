<#
.SYNOPSIS
Streams Logcat for the Reals Android application process only.

.DESCRIPTION
Starts ADB if needed, selects one connected Android device or emulator, resolves
the current process ID for the Reals app package, and streams Logcat with a PID
filter:

  adb -s <serial> logcat --pid=<pid> -v threadtime

This keeps terminal output focused on Reals-owned logs. It does not clear the
global Logcat buffer, restart the app, install the app, modify ADB reverse
mappings, or change firewall rules.

.PARAMETER Serial
Optional ADB serial. Required when more than one usable device or emulator is
connected. The serial must exist in `adb devices` and its state must be exactly
`device`.

.PARAMETER PackageName
Optional Android package name. Defaults to `com.reals.app`.

.EXAMPLE
.\tools\logcat-reals.ps1

Streams Logcat for `com.reals.app` when exactly one usable device or emulator is
connected.

.EXAMPLE
.\tools\logcat-reals.ps1 -Serial emulator-5554

Streams Logcat for `com.reals.app` on the emulator with serial `emulator-5554`.

.EXAMPLE
.\tools\logcat-reals.ps1 -Serial 192.168.1.50:5555

Streams Logcat for a wireless-debugging device with serial `192.168.1.50:5555`.

.NOTES
The script filters by the app process PID. If the app process restarts, stop the
script with Ctrl+C and run it again so it can resolve the new PID.

Android Studio usually provides `adb.exe` through the Android SDK
`platform-tools` directory. The script checks PATH first, then common SDK
locations under LOCALAPPDATA, ANDROID_SDK_ROOT, and ANDROID_HOME.
#>

param(
    [string]$Serial,
    [string]$PackageName = "com.reals.app"
)

$ErrorActionPreference = "Stop"

function Resolve-AdbPath {
    $pathCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($pathCommand) {
        return $pathCommand.Source
    }

    $candidatePaths = @()
    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $candidatePaths += Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
        $candidatePaths += Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe"
    }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
        $candidatePaths += Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
    }

    foreach ($candidatePath in $candidatePaths) {
        if (Test-Path -LiteralPath $candidatePath -PathType Leaf) {
            return $candidatePath
        }
    }

    throw "adb.exe was not found. Checked PATH, LOCALAPPDATA\Android\Sdk\platform-tools, ANDROID_SDK_ROOT\platform-tools, and ANDROID_HOME\platform-tools."
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $output = & $script:adbPath @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        $joinedArguments = $Arguments -join " "
        throw "$script:adbPath $joinedArguments failed with exit code $exitCode.`n$output"
    }
    return $output
}

function Get-AdbDeviceRows {
    $deviceLines = Invoke-Adb -Arguments @("devices") |
        Select-Object -Skip 1 |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

    $rows = @()
    foreach ($line in $deviceLines) {
        $parts = $line -split "\s+"
        if ($parts.Count -ge 2) {
            $rows += [PSCustomObject]@{
                Serial = $parts[0]
                State = $parts[1]
                Raw = $line
            }
        }
    }
    return $rows
}

function Select-AdbDevice {
    param(
        [object[]]$Devices,

        [string]$RequestedSerial
    )

    $usableDevices = @($Devices | Where-Object { $_.State -eq "device" })
    $unusableDevices = @($Devices | Where-Object { $_.State -ne "device" })

    foreach ($device in $unusableDevices) {
        Write-Warning "Ignoring device $($device.Serial) because its state is '$($device.State)'."
    }

    if (-not [string]::IsNullOrWhiteSpace($RequestedSerial)) {
        $matching = @($Devices | Where-Object { $_.Serial -eq $RequestedSerial })
        if ($matching.Count -eq 0) {
            throw "Device '$RequestedSerial' was not found in adb devices."
        }
        if ($matching[0].State -ne "device") {
            throw "Device '$RequestedSerial' is in state '$($matching[0].State)', not 'device'."
        }
        return $matching[0]
    }

    if ($usableDevices.Count -eq 0) {
        throw "No usable Android device or emulator is connected. Connect a device with ADB state 'device' and retry."
    }

    if ($usableDevices.Count -gt 1) {
        Write-Host "More than one usable device is connected. Re-run with -Serial using one of:"
        foreach ($device in $usableDevices) {
            Write-Host "  $($device.Serial)"
        }
        throw "Multiple usable devices require -Serial."
    }

    return $usableDevices[0]
}

function Get-ProcessIdForPackage {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Device,

        [Parameter(Mandatory = $true)]
        [string]$Package
    )

    $pidOutput = & $script:adbPath -s $Device.Serial shell pidof $Package 2>$null
    $exitCode = $LASTEXITCODE
    $pidText = ($pidOutput -join " ").Trim()
    if ($exitCode -ne 0 -or [string]::IsNullOrWhiteSpace($pidText)) {
        throw "Package '$Package' is not currently running on $($Device.Serial). Launch the app, then run this script again."
    }

    return ($pidText -split "\s+")[0]
}

Write-Host "Checking ADB..."
$script:adbPath = Resolve-AdbPath
Write-Host "Using ADB: $script:adbPath"

Write-Host "Starting ADB server..."
Invoke-Adb -Arguments @("start-server") | Out-Null

$devices = @(Get-AdbDeviceRows)
$selectedDevice = Select-AdbDevice -Devices $devices -RequestedSerial $Serial
Write-Host "Using device: $($selectedDevice.Serial)"

Write-Host "Resolving PID for $PackageName..."
$processIdValue = Get-ProcessIdForPackage -Device $selectedDevice -Package $PackageName
Write-Host "Streaming Logcat for $PackageName pid=$processIdValue. Press Ctrl+C to stop."

& $script:adbPath -s $selectedDevice.Serial logcat "--pid=$processIdValue" "-v" "threadtime"
$exitCode = $LASTEXITCODE
if ($exitCode -ne 0) {
    throw "adb logcat failed with exit code $exitCode."
}
