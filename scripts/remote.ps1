<#
.SYNOPSIS
    View and control the Krelzox TV from this PC over the local network (scrcpy).

.DESCRIPTION
    Finds the Raspberry Pi through ADB mDNS discovery (or uses -Address), connects
    with ADB over the network and starts scrcpy. It always uses the repository's
    ADB (platform-tools\adb.exe) so scrcpy's bundled ADB does not restart the ADB
    server with a different version.

    The device must have network debugging enabled and must have authorized this
    PC's ADB key. Traffic is not encrypted: use it only on a trusted LAN.

.PARAMETER Address
    Device address as IP:PORT (for example 192.168.1.50:5555). Optional when the
    device is discoverable through mDNS.

.PARAMETER ScrcpyArgs
    Extra arguments passed to scrcpy (for example --max-size 1280 or --no-audio).

.EXAMPLE
    .\scripts\remote.ps1
.EXAMPLE
    .\scripts\remote.ps1 -Address 192.168.1.50:5555 --max-size 1280
#>
[CmdletBinding(PositionalBinding = $false)]
param(
    [string]$Address,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ScrcpyArgs
)

# Native tools write progress to stderr; failures are checked explicitly below.
$ErrorActionPreference = 'Continue'
$repo = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $repo 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) {
    throw "ADB not found at $adb. Install Android platform-tools there (see docs/entorno.md)."
}

$scrcpy = Get-Command scrcpy -ErrorAction SilentlyContinue
if (-not $scrcpy) {
    $scrcpy = Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Genymobile.scrcpy*" -Recurse -Filter scrcpy.exe -ErrorAction SilentlyContinue |
        Select-Object -First 1
}
if (-not $scrcpy) {
    throw 'scrcpy not found. Install it with: winget install --id Genymobile.scrcpy -e'
}
$scrcpyPath = if ($scrcpy.Source) { $scrcpy.Source } else { $scrcpy.FullName }

if (-not $Address) {
    $line = & $adb mdns services | Select-String '_adb\._tcp\s+(\S+)' | Select-Object -First 1
    if (-not $line) {
        throw 'No device found through mDNS. Pass -Address IP:PORT (shown in the TV network settings).'
    }
    $Address = $line.Matches[0].Groups[1].Value
}

Write-Host "Connecting to $Address ..."
$result = & $adb connect $Address
Write-Host $result
$state = (& $adb -s $Address get-state 2>&1 | Out-String).Trim()
if ($state -ne 'device') {
    throw "Device $Address is not ready (state: '$state'). Accept the debugging prompt on the TV and try again."
}

$env:ADB = $adb
& $scrcpyPath -s $Address --window-title 'Krelzox' @ScrcpyArgs
