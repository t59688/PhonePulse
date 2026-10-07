# Install debug build and launch on a device/emulator (npm run dev style).
# Usage:
#   .\dev.bat
#   .\dev.bat -Avd Pixel_10_Pro
#   .\dev.bat -Serial emulator-5554
#   .\dev.bat -List

param(
  [string]$Avd,
  [string]$Serial,
  [switch]$List,
  [switch]$SkipLaunch
)

$ErrorActionPreference = 'Stop'
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $projectRoot

$packageId = 'com.aizeek.phonepulse'
$launchComponent = 'com.aizeek.phonepulse/.MainActivity'
$bootTimeoutSec = 180

function Resolve-AndroidSdk {
  foreach ($candidate in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
    if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path $candidate)) {
      return (Resolve-Path $candidate).Path
    }
  }
  $default = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
  if (Test-Path $default) { return (Resolve-Path $default).Path }
  throw 'Android SDK not found. Set ANDROID_HOME / ANDROID_SDK_ROOT, or install to %LOCALAPPDATA%\Android\Sdk.'
}

function Get-ToolPath {
  param([string]$SdkRoot, [string]$RelativePath)
  $path = Join-Path $SdkRoot $RelativePath
  if (-not (Test-Path $path)) { throw "Missing tool: $path" }
  return $path
}

function Invoke-Adb {
  param(
    [string]$Adb,
    # Do not name this $Args — that is a PowerShell automatic variable.
    [Parameter(Mandatory = $true)]
    [string[]]$AdbArgs
  )
  & $Adb @AdbArgs
  if ($LASTEXITCODE -ne 0) {
    throw "adb failed ($LASTEXITCODE): adb $($AdbArgs -join ' ')"
  }
}

function Get-AdbDevices {
  param([string]$Adb)
  $lines = & $Adb devices
  if ($LASTEXITCODE -ne 0) { throw 'adb devices failed.' }
  $devices = @()
  foreach ($line in $lines) {
    if ($line -match '^\s*$' -or $line -match '^List of devices') { continue }
    if ($line -match '^(\S+)\s+(\S+)$') {
      $devices += [pscustomobject]@{
        Serial = $Matches[1]
        State  = $Matches[2]
      }
    }
  }
  return $devices
}

function Get-AvdNames {
  param([string]$Emulator)
  $names = & $Emulator -list-avds 2>$null
  if ($LASTEXITCODE -ne 0) { throw 'emulator -list-avds failed.' }
  return @($names | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Wait-ForBoot {
  param([string]$Adb, [string]$DeviceSerial, [int]$TimeoutSec)
  Write-Host "Waiting for device $DeviceSerial ..."
  Invoke-Adb -Adb $Adb -AdbArgs @('-s', $DeviceSerial, 'wait-for-device')

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    $boot = (& $Adb -s $DeviceSerial shell getprop sys.boot_completed 2>$null | Out-String).Trim()
    if ($boot -eq '1') {
      Write-Host "Device ready: $DeviceSerial"
      return
    }
    Start-Sleep -Seconds 2
  }
  throw "Timed out waiting for boot_completed on $DeviceSerial (${TimeoutSec}s)."
}

function Start-AvdAndResolveSerial {
  param(
    [string]$Adb,
    [string]$Emulator,
    [string]$AvdName
  )

  $before = @(Get-AdbDevices -Adb $Adb | ForEach-Object { $_.Serial })
  Write-Host "Starting emulator: $AvdName"
  Start-Process -FilePath $Emulator -ArgumentList @('-avd', $AvdName) -WindowStyle Normal | Out-Null

  $deadline = (Get-Date).AddSeconds($bootTimeoutSec)
  $newSerial = $null
  while ((Get-Date) -lt $deadline) {
    $now = Get-AdbDevices -Adb $Adb
    $online = @($now | Where-Object { $_.State -eq 'device' })
    $candidate = $online | Where-Object { $before -notcontains $_.Serial } | Select-Object -First 1
    if (-not $candidate) {
      # Reused / already-running case: prefer an emulator serial.
      $candidate = $online | Where-Object { $_.Serial -like 'emulator-*' } | Select-Object -First 1
    }
    if ($candidate) {
      $newSerial = $candidate.Serial
      break
    }
    Start-Sleep -Seconds 2
  }
  if (-not $newSerial) {
    throw "Timed out waiting for emulator '$AvdName' to appear in adb."
  }
  Wait-ForBoot -Adb $Adb -DeviceSerial $newSerial -TimeoutSec $bootTimeoutSec
  return $newSerial
}

function Show-MenuAndPick {
  param(
    [object[]]$OnlineDevices,
    [string[]]$Avds
  )

  $choices = @()
  foreach ($d in $OnlineDevices) {
    $label = if ($d.Serial -like 'emulator-*') { "device  $($d.Serial)  [emulator online]" }
             else { "device  $($d.Serial)  [$($d.State)]" }
    $choices += [pscustomobject]@{ Kind = 'device'; Serial = $d.Serial; Avd = $null; Label = $label }
  }
  foreach ($name in $Avds) {
    $choices += [pscustomobject]@{ Kind = 'avd'; Serial = $null; Avd = $name; Label = "avd     $name" }
  }

  if ($choices.Count -eq 0) {
    throw 'No online devices and no AVDs found. Create an AVD in Android Studio first.'
  }

  Write-Host ''
  Write-Host 'Select target:'
  for ($i = 0; $i -lt $choices.Count; $i++) {
    Write-Host ("  [{0}] {1}" -f $i, $choices[$i].Label)
  }
  Write-Host ''

  if ($choices.Count -eq 1) {
    Write-Host "Only one target — using [$($choices[0].Label)]"
    return $choices[0]
  }

  while ($true) {
    $raw = Read-Host 'Enter number'
    if ($raw -match '^\d+$') {
      $idx = [int]$raw
      if ($idx -ge 0 -and $idx -lt $choices.Count) { return $choices[$idx] }
    }
    Write-Host "Invalid choice. Enter 0..$($choices.Count - 1)."
  }
}

# --- main ---

$sdk = Resolve-AndroidSdk
$adb = Get-ToolPath -SdkRoot $sdk -RelativePath 'platform-tools\adb.exe'
$emulator = Get-ToolPath -SdkRoot $sdk -RelativePath 'emulator\emulator.exe'
$gradlew = Join-Path $projectRoot 'gradlew.bat'
if (-not (Test-Path $gradlew)) { throw "Missing gradlew.bat at $gradlew" }

$allDevices = @(Get-AdbDevices -Adb $adb)
$onlineDevices = @($allDevices | Where-Object { $_.State -eq 'device' })
$avds = @(Get-AvdNames -Emulator $emulator)

if ($List) {
  Write-Host "SDK: $sdk"
  Write-Host ''
  Write-Host 'adb devices:'
  if ($allDevices.Count -eq 0) { Write-Host '  (none)' }
  else { $allDevices | ForEach-Object { Write-Host "  $($_.Serial)`t$($_.State)" } }
  Write-Host ''
  Write-Host 'AVDs:'
  if ($avds.Count -eq 0) { Write-Host '  (none)' }
  else { $avds | ForEach-Object { Write-Host "  $_" } }
  exit 0
}

$targetSerial = $null

if (-not [string]::IsNullOrWhiteSpace($Serial) -and -not [string]::IsNullOrWhiteSpace($Avd)) {
  throw 'Use either -Serial or -Avd, not both.'
}

if (-not [string]::IsNullOrWhiteSpace($Serial)) {
  $match = $allDevices | Where-Object { $_.Serial -eq $Serial } | Select-Object -First 1
  if (-not $match) { throw "Device not found: $Serial. Run .\dev.bat -List" }
  if ($match.State -ne 'device') { throw "Device $Serial is '$($match.State)', expected 'device'." }
  $targetSerial = $Serial
  Wait-ForBoot -Adb $adb -DeviceSerial $targetSerial -TimeoutSec $bootTimeoutSec
}
elseif (-not [string]::IsNullOrWhiteSpace($Avd)) {
  if ($avds -notcontains $Avd) {
    throw "AVD not found: $Avd`nAvailable:`n  $($avds -join "`n  ")"
  }
  $targetSerial = Start-AvdAndResolveSerial -Adb $adb -Emulator $emulator -AvdName $Avd
}
else {
  $pick = Show-MenuAndPick -OnlineDevices $onlineDevices -Avds $avds
  if ($pick.Kind -eq 'device') {
    $targetSerial = $pick.Serial
    Wait-ForBoot -Adb $adb -DeviceSerial $targetSerial -TimeoutSec $bootTimeoutSec
  }
  else {
    $targetSerial = Start-AvdAndResolveSerial -Adb $adb -Emulator $emulator -AvdName $pick.Avd
  }
}

$env:ANDROID_SERIAL = $targetSerial
Write-Host "Installing debug build to $targetSerial ..."
& $gradlew :app:installDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (-not $SkipLaunch) {
  Write-Host "Launching $launchComponent ..."
  Invoke-Adb -Adb $adb -AdbArgs @(
    '-s', $targetSerial,
    'shell', 'am', 'start',
    '-n', $launchComponent,
    '-a', 'android.intent.action.MAIN',
    '-c', 'android.intent.category.LAUNCHER'
  )
}

Write-Host "Done. ($packageId on $targetSerial)"
