# Build signed release APK/AAB and stage as screenpulse-<VERSION>-release.*
# Usage:
#   .\scripts\build-release.ps1            # apk (default)
#   .\scripts\build-release.ps1 apk
#   .\scripts\build-release.ps1 aab
#   .\scripts\build-release.ps1 all

param(
  [ValidateSet('apk', 'aab', 'all')]
  [string]$Format = 'apk'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $projectRoot

$envFile = Join-Path $projectRoot '.env.android.local'
if (-not (Test-Path $envFile)) {
  throw "Missing .env.android.local. Generate signing first (.android-signing + env)."
}

Get-Content $envFile | ForEach-Object {
  $line = $_.Trim()
  if (-not $line -or $line.StartsWith('#')) { return }
  $parts = $line.Split('=', 2)
  if ($parts.Length -ne 2) { return }
  Set-Item -Path "Env:$($parts[0].Trim())" -Value $parts[1].Trim()
}

foreach ($name in @('KEYSTORE_PATH', 'STORE_PASSWORD', 'KEY_PASSWORD')) {
  if (-not [string]::IsNullOrWhiteSpace((Get-Item "Env:$name" -ErrorAction SilentlyContinue).Value)) { continue }
  throw "Missing $name in .env.android.local"
}
if (-not (Test-Path $env:KEYSTORE_PATH)) {
  throw "Keystore not found: $env:KEYSTORE_PATH"
}
if ([string]::IsNullOrWhiteSpace($env:KEY_ALIAS)) {
  $env:KEY_ALIAS = 'upload'
}

$version = (Get-Content (Join-Path $projectRoot 'VERSION') -Raw).Trim()
if (-not $version) { throw 'VERSION file is empty.' }
$env:VERSION_NAME = $version

$gradlew = Join-Path $projectRoot 'gradlew.bat'
$tasks = @()
if ($Format -eq 'apk' -or $Format -eq 'all') { $tasks += ':app:assembleRelease' }
if ($Format -eq 'aab' -or $Format -eq 'all') { $tasks += ':app:bundleRelease' }

Write-Host "Building ScreenPulse $version ($Format)..."
& $gradlew @tasks --no-daemon --stacktrace
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$outDir = Join-Path $projectRoot 'artifacts\android'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Copy-NamedArtifact {
  param(
    [string]$SearchRoot,
    [string]$Filter,
    [string]$DestName
  )
  $src = Get-ChildItem -Path $SearchRoot -Filter $Filter -Recurse -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
  if (-not $src) { throw "Build output not found under $SearchRoot ($Filter)" }
  $dest = Join-Path $outDir $DestName
  Copy-Item -Force $src.FullName $dest
  Write-Host "-> $dest"
}

if ($Format -eq 'apk' -or $Format -eq 'all') {
  Copy-NamedArtifact `
    -SearchRoot (Join-Path $projectRoot 'app\build\outputs\apk\release') `
    -Filter '*.apk' `
    -DestName "screenpulse-$version-release.apk"
}
if ($Format -eq 'aab' -or $Format -eq 'all') {
  Copy-NamedArtifact `
    -SearchRoot (Join-Path $projectRoot 'app\build\outputs\bundle\release') `
    -Filter '*.aab' `
    -DestName "screenpulse-$version-release.aab"
}

Write-Host "Done."
