param(
    [string]$MySqlBin = '',
    [ValidateRange(1024, 65535)][int]$Port = 3307
)
$ErrorActionPreference = 'Stop'

function Get-NormalizedPath([string]$Value) {
    $fullPath = [IO.Path]::GetFullPath($Value.Replace('/', '\')).TrimEnd('\')
    if (Test-Path -LiteralPath $fullPath) { return (Get-Item -LiteralPath $fullPath).FullName.TrimEnd('\') }
    return $fullPath
}

function Get-ProcessDefaultsFile([string]$CommandLine) {
    # Accept --defaults-file="C:\a b\my.ini" and "--defaults-file=C:\a b\my.ini".
    $match = [regex]::Match($CommandLine, '(?i)(?:^|\s)(?:"--defaults-file=(?<outer>[^"]+)"|--defaults-file(?:=|\s+)(?:"(?<quoted>[^"]+)"|(?<plain>[^\s"]+)))')
    if (-not $match.Success) { return $null }
    foreach ($name in @('outer', 'quoted', 'plain')) {
        $value = $match.Groups[$name].Value
        if ($value -and [IO.Path]::IsPathRooted($value)) { return Get-NormalizedPath $value }
    }
    return $null
}

function Find-ProjectServer([string]$ExpectedExe, [string]$ExpectedConfig) {
    $expectedPath = Get-NormalizedPath $ExpectedExe
    $expectedDefaults = Get-NormalizedPath $ExpectedConfig
    $matches = @(Get-CimInstance Win32_Process -Filter "Name = 'mysqld.exe'" | Where-Object {
        $_.CommandLine -and (Get-ProcessDefaultsFile $_.CommandLine) -eq $expectedDefaults
    })
    foreach ($candidate in $matches) {
        if (-not $candidate.ExecutablePath -or (Get-NormalizedPath $candidate.ExecutablePath) -ne $expectedPath) {
            throw 'A MySQL process uses this project configuration with a different or unreadable executable. Verify -MySqlBin before continuing.'
        }
    }
    # MySQL on Windows can run a supervisor and its worker with the same arguments.
    if ($matches.Count -eq 2) {
        $workers = @($matches | Where-Object { $_.ParentProcessId -in $matches.ProcessId })
        if ($workers.Count -eq 1) { return $workers[0] }
    }
    if ($matches.Count -gt 1) { throw 'Multiple unrelated MySQL processes use this project configuration. Inspect them before continuing.' }
    return $matches | Select-Object -First 1
}

function Write-Utf8File([string]$Path, [string]$Content) {
    [IO.File]::WriteAllText($Path, $Content, (New-Object Text.UTF8Encoding($false)))
}

function Get-MySqlDirectory([string]$Path) {
    # Some Windows MySQL builds cannot create data files under Unicode paths.
    # An existing NTFS short alias points to the same directory without moving data.
    if ($Path -match '[^\x00-\x7F]') {
        $fileSystem = New-Object -ComObject Scripting.FileSystemObject
        try { $Path = $fileSystem.GetFolder($Path).ShortPath }
        finally { [void][Runtime.InteropServices.Marshal]::ReleaseComObject($fileSystem) }
        if ($Path -match '[^\x00-\x7F]') {
            throw 'This MySQL build needs an ASCII directory path. NTFS short aliases are unavailable; use an ASCII project path or Docker.'
        }
    }
    return $Path.Replace('\', '/')
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$localRoot = Join-Path $projectRoot '.local/mysql'
$dataDir = Join-Path $localRoot 'data'
$configFile = Join-Path $localRoot 'my.ini'
$stateFile = Join-Path $localRoot 'connection.json'
$adminConfig = Join-Path $localRoot 'admin.cnf'
$initFile = Join-Path $localRoot 'initialize.sql'
if (-not $MySqlBin -and (Test-Path -LiteralPath $stateFile)) {
    $MySqlBin = (Get-Content -LiteralPath $stateFile -Raw -Encoding UTF8 | ConvertFrom-Json).bin
}
if (-not $MySqlBin) {
    $candidates = @('C:/Program Files/MySQL/MySQL Server 8.4/bin', 'C:/Program Files/MySQL/MySQL Server 8.0/bin')
    $MySqlBin = $candidates | Where-Object { Test-Path -LiteralPath (Join-Path $_ 'mysqld.exe') } | Select-Object -First 1
}
if (-not $MySqlBin) { throw 'MySQL 8 binaries were not found. Install MySQL 8 or use Docker Compose. You may supply -MySqlBin.' }
$MySqlBin = Get-NormalizedPath $MySqlBin
foreach ($binary in @('mysqld.exe', 'mysql.exe', 'mysqladmin.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $MySqlBin $binary))) { throw "Required MySQL binary was not found: $binary. Check -MySqlBin." }
}
$serverExe = Join-Path $MySqlBin 'mysqld.exe'
$clientExe = Join-Path $MySqlBin 'mysql.exe'
$existing = Find-ProjectServer $serverExe $configFile
if ($existing) {
    if (-not (Test-Path -LiteralPath $configFile)) { throw 'The running project MySQL configuration is missing. Inspect it before continuing.' }
    $portMatch = [regex]::Match((Get-Content -LiteralPath $configFile -Raw -Encoding UTF8), '(?m)^\s*port\s*=\s*(\d+)\s*$')
    if (-not $portMatch.Success) { throw 'The running project MySQL port could not be read from my.ini.' }
    $configuredPort = [int]$portMatch.Groups[1].Value
    $boundPorts = @(Get-NetTCPConnection -State Listen -OwningProcess $existing.ProcessId -ErrorAction SilentlyContinue | Select-Object -ExpandProperty LocalPort -Unique)
    if ($configuredPort -ne $Port -or ($boundPorts.Count -gt 0 -and $Port -notin $boundPorts)) {
        throw "Project MySQL is already running with port $configuredPort. Stop it with scripts/stop-local-db.ps1 before selecting -Port $Port."
    }
    $server = Get-Process -Id $existing.ProcessId
} else {
    $listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
    if ($listener) { throw "Port $Port is in use. Select a different -Port; existing databases will not be changed." }
    $firstRun = -not (Test-Path -LiteralPath (Join-Path $dataDir 'mysql'))
    if ($firstRun -and (Test-Path -LiteralPath $dataDir) -and (Get-ChildItem -Force -LiteralPath $dataDir | Select-Object -First 1)) {
        throw "The project data directory contains an incomplete initialization: $dataDir. Inspect it before retrying."
    }
    if (-not $firstRun -and -not (Test-Path -LiteralPath $adminConfig)) {
        throw 'Existing project data has no admin.cnf. Restore its administrator configuration; existing data will not be initialized or reset.'
    }
    New-Item -ItemType Directory -Force -Path $localRoot | Out-Null
    $mysqlPath = Get-MySqlDirectory $localRoot
    $mysqlBase = Get-MySqlDirectory (Split-Path -Parent $MySqlBin)
    Write-Utf8File $configFile @"
[mysqld]
basedir="$mysqlBase"
datadir="$mysqlPath/data"
bind-address=127.0.0.1
port=$Port
mysqlx=0
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
default-time-zone=+09:00
pid-file="$mysqlPath/mysqld.pid"
log-error="$mysqlPath/server.log"
"@
    if ($firstRun) {
        # Save bootstrap credentials before initializing data so an interrupted start can recover.
        $rootPassword = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
        Write-Utf8File $adminConfig @"
[client]
user=root
password=$rootPassword
host=127.0.0.1
port=$Port
protocol=TCP
"@
        Write-Utf8File $initFile @"
CREATE DATABASE IF NOT EXISTS fridge_gatekeeper CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'fridge_app'@'127.0.0.1' IDENTIFIED BY 'fridge_dev_password';
GRANT ALL PRIVILEGES ON fridge_gatekeeper.* TO 'fridge_app'@'127.0.0.1';
CREATE USER IF NOT EXISTS 'fridge_app'@'localhost' IDENTIFIED BY 'fridge_dev_password';
GRANT ALL PRIVILEGES ON fridge_gatekeeper.* TO 'fridge_app'@'localhost';
ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';
"@
        $previousPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & $serverExe "--defaults-file=$mysqlPath/my.ini" --initialize-insecure 2>$null | Out-Null
            $initializeExitCode = $LASTEXITCODE
        } finally { $ErrorActionPreference = $previousPreference }
        if ($initializeExitCode -ne 0) { throw "MySQL initialization failed. Check $localRoot/server.log. Existing files have been preserved." }
    }
}
if (-not (Test-Path -LiteralPath $adminConfig)) { throw 'Project administrator configuration is missing. Restore admin.cnf before continuing.' }
# Persist the chosen port before launching/probing, including recovery of an already running server.
# The stop script uses this port explicitly, even when admin.cnf was created on another port.
$connection = @{ port = $Port; bin = $MySqlBin; url = "jdbc:mysql://127.0.0.1:$Port/fridge_gatekeeper?serverTimezone=Asia/Seoul&characterEncoding=UTF-8" }
Write-Utf8File $stateFile ($connection | ConvertTo-Json)
if (-not $existing) {
    $serverArgs = @("--defaults-file=`"$mysqlPath/my.ini`"")
    if (Test-Path -LiteralPath $initFile) { $serverArgs += "--init-file=`"$mysqlPath/initialize.sql`"" }
    $server = Start-Process -FilePath $serverExe -ArgumentList $serverArgs -WindowStyle Hidden -PassThru
}
$ready = $false
$previousPassword = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = 'fridge_dev_password'
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $server.Refresh()
        if ($server.HasExited) { throw "MySQL exited. Check $localRoot/server.log." }
        $previousPreference = $ErrorActionPreference
        try {
            # Windows PowerShell 5 treats native stderr as an error even with 2>$null.
            $ErrorActionPreference = 'Continue'
            & $clientExe --protocol=TCP --host=127.0.0.1 "--port=$Port" --connect-timeout=1 --user=fridge_app --database=fridge_gatekeeper --execute='SELECT 1' 2>$null | Out-Null
            $probeExitCode = $LASTEXITCODE
        } finally { $ErrorActionPreference = $previousPreference }
        if ($probeExitCode -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
} finally { $env:MYSQL_PWD = $previousPassword }
if (-not $ready) { throw "MySQL did not become ready. Check $localRoot/server.log. Connection settings are saved; scripts/stop-local-db.ps1 can stop this project instance." }
if (Test-Path -LiteralPath $initFile) { Remove-Item -LiteralPath $initFile }
Write-Host "Project MySQL is ready at 127.0.0.1:$Port. Data: $dataDir"
