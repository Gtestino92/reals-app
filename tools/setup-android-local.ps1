<#
.SYNOPSIS
Configures ADB reverse tunnels for Reals Android local development.

.DESCRIPTION
Creates ADB reverse mappings from each connected Android device or emulator to
the Windows localhost services used by Reals local development:

  Android http://127.0.0.1:8080 -> Windows localhost:8080 -> Reals backend
  Android http://127.0.0.1:9000 -> Windows localhost:9000 -> MinIO

The script supports emulators, USB debugging, wireless debugging, and multiple
connected devices. It is safe to run repeatedly and does not require
Administrator privileges.

.PARAMETER Serial
Optional ADB serial. When supplied, only that exact device is configured. The
serial must exist in `adb devices` and its state must be exactly `device`.

.EXAMPLE
.\tools\setup-android-local.ps1

Configures every connected device or emulator whose ADB state is `device`.

.EXAMPLE
.\tools\setup-android-local.ps1 -Serial emulator-5554

Configures only the device or emulator with serial `emulator-5554`.

.NOTES
ADB reverse mappings are not permanent. Re-run this script after restarting the
emulator, rebooting the phone, restarting ADB, disconnecting USB, or switching
between USB and wireless debugging.

Android Studio usually provides `adb.exe` through the Android SDK
`platform-tools` directory. The script checks PATH first, then common SDK
locations under LOCALAPPDATA, ANDROID_SDK_ROOT, and ANDROID_HOME.
#>

param(
    [string]$Serial
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

function Test-HostPort {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port,

        [Parameter(Mandatory = $true)]
        [string]$ServiceName
    )

    Write-Host "Checking host port $Port..."
    $reachable = $false

    if (Get-Command Test-NetConnection -ErrorAction SilentlyContinue) {
        $reachable = Test-NetConnection -ComputerName "127.0.0.1" -Port $Port -InformationLevel Quiet
    } else {
        $client = New-Object System.Net.Sockets.TcpClient
        try {
            $asyncResult = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
            $reachable = $asyncResult.AsyncWaitHandle.WaitOne(1000, $false)
            if ($reachable) {
                $client.EndConnect($asyncResult)
            }
        } catch {
            $reachable = $false
        } finally {
            $client.Close()
        }
    }

    if ($reachable) {
        Write-Host "Host port $Port is reachable for $ServiceName."
    } else {
        Write-Warning "Host port $Port is not reachable. $ServiceName may not be running yet; continuing."
    }
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

Write-Host "Checking ADB..."
$script:adbPath = Resolve-AdbPath
Write-Host "Using ADB: $script:adbPath"

Write-Host "Starting ADB server..."
Invoke-Adb -Arguments @("start-server") | Out-Null

$devices = @(Get-AdbDeviceRows)
$usableDevices = @($devices | Where-Object { $_.State -eq "device" })
$unusableDevices = @($devices | Where-Object { $_.State -ne "device" })

foreach ($device in $unusableDevices) {
    Write-Warning "Skipping device $($device.Serial) because its state is '$($device.State)'."
}

if (-not [string]::IsNullOrWhiteSpace($Serial)) {
    $matching = @($devices | Where-Object { $_.Serial -eq $Serial })
    if ($matching.Count -eq 0) {
        throw "Device '$Serial' was not found in adb devices."
    }
    if ($matching[0].State -ne "device") {
        throw "Device '$Serial' is in state '$($matching[0].State)', not 'device'."
    }
    $usableDevices = @($matching[0])
}

if ($usableDevices.Count -eq 0) {
    throw "No usable Android device or emulator is connected. Connect a device with ADB state 'device' and retry."
}

foreach ($device in $usableDevices) {
    Write-Host "Found device: $($device.Serial)"
}

Test-HostPort -Port 8080 -ServiceName "Reals backend"
Test-HostPort -Port 9000 -ServiceName "MinIO"

foreach ($device in $usableDevices) {
    Write-Host "Configuring device $($device.Serial)..."

    Write-Host "Configuring tcp:8080 -> tcp:8080..."
    Invoke-Adb -Arguments @("-s", $device.Serial, "reverse", "tcp:8080", "tcp:8080") | Out-Null

    Write-Host "Configuring tcp:9000 -> tcp:9000..."
    Invoke-Adb -Arguments @("-s", $device.Serial, "reverse", "tcp:9000", "tcp:9000") | Out-Null

    Write-Host "Configured mappings for $($device.Serial):"
    Invoke-Adb -Arguments @("-s", $device.Serial, "reverse", "--list") | ForEach-Object {
        Write-Host $_
    }
}

Write-Host ""
Write-Host "Use the Android localDebug variant."
Write-Host "Expected backend URL: http://127.0.0.1:8080/"
Write-Host "Expected local photo URL base: http://127.0.0.1:9000/"
Write-Host ""
Write-Host "Re-run this script after restarting the emulator, rebooting the phone, restarting ADB,"
Write-Host "disconnecting USB, or switching between USB and wireless debugging."
