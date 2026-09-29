$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$localRoot = Join-Path $projectRoot '.local'
New-Item -ItemType Directory -Force -Path $localRoot | Out-Null
$existing = Get-ChildItem -Path (Join-Path $localRoot 'jdk-21*') -Directory -ErrorAction SilentlyContinue |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/javac.exe') } | Select-Object -First 1
if ($existing) {
    Write-Host "Project JDK 21 is ready: $($existing.FullName)"
    return
}
if (-not (Get-Command tar.exe -ErrorAction SilentlyContinue)) { throw 'tar.exe is required (included in current Windows versions).' }
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$metadata = Invoke-RestMethod -Uri 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
$package = $metadata[0].binary.package
if (-not $package.link -or -not $package.checksum) { throw 'The official JDK download metadata is incomplete.' }
$archive = Join-Path $localRoot 'jdk21.zip'
Write-Host 'Downloading Eclipse Temurin JDK 21 into .local (no system settings are changed)...'
$ProgressPreference = 'SilentlyContinue'
Invoke-WebRequest -UseBasicParsing -Uri $package.link -OutFile $archive
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $package.checksum) {
    throw 'JDK checksum mismatch. Run this script again to download a fresh archive.'
}
& tar.exe -xf $archive -C $localRoot
if ($LASTEXITCODE -ne 0) { throw 'JDK extraction failed.' }
Write-Host 'Project JDK 21 is ready. start-backend.ps1 and verify.ps1 will use it automatically.'
