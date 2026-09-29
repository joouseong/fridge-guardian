$ErrorActionPreference = 'Stop'

function Get-NormalizedPath([string]$Value) {
    $fullPath = [IO.Path]::GetFullPath($Value.Replace('/', '\')).TrimEnd('\')
    if (Test-Path -LiteralPath $fullPath) { return (Get-Item -LiteralPath $fullPath).FullName.TrimEnd('\') }
    return $fullPath
}

function Get-ProcessDefaultsFile([string]$CommandLine) {
    $match = [regex]::Match($CommandLine, '(?i)(?:^|\s)(?:"--defaults-file=(?<outer>[^"]+)"|--defaults-file(?:=|\s+)(?:"(?<quoted>[^"]+)"|(?<plain>[^\s"]+)))')
    if (-not $match.Success) { return $null }
    foreach ($name in @('outer', 'quoted', 'plain')) {
        $value = $match.Groups[$name].Value
        if ($value -and [IO.Path]::IsPathRooted($value)) { return Get-NormalizedPath $value }
    }
    return $null
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$localRoot = Join-Path $projectRoot '.local/mysql'
$stateFile = Join-Path $localRoot 'connection.json'
$configFile = Get-NormalizedPath (Join-Path $localRoot 'my.ini')
$candidates = @(Get-CimInstance Win32_Process -Filter "Name = 'mysqld.exe'" | Where-Object {
    $_.CommandLine -and (Get-ProcessDefaultsFile $_.CommandLine) -eq $configFile
})
if ($candidates.Count -eq 0) { Write-Host 'Project MySQL is already stopped. All data is preserved.'; return }
$candidate = $candidates[0]
if ($candidates.Count -gt 1) {
    # The worker listens on the port; its supervisor has the same defaults-file.
    $workers = @($candidates | Where-Object { $_.ParentProcessId -in $candidates.ProcessId })
    if ($candidates.Count -ne 2 -or $workers.Count -ne 1) {
        throw 'Multiple unrelated MySQL processes use this project configuration. Inspect them before continuing.'
    }
    $candidate = $workers[0]
}
if (-not (Test-Path -LiteralPath $stateFile)) {
    throw 'Project MySQL is running without connection.json. Run scripts/start-local-db.ps1 with its current -MySqlBin and -Port to recover the connection settings first.'
}
$state = Get-Content -LiteralPath $stateFile -Raw -Encoding UTF8 | ConvertFrom-Json
$port = 0
if (-not [int]::TryParse([string]$state.port, [ref]$port) -or $port -lt 1024 -or $port -gt 65535 -or -not $state.bin) {
    throw 'The project connection settings are invalid. No process was stopped.'
}
$expectedExe = Get-NormalizedPath (Join-Path $state.bin 'mysqld.exe')
if (-not $candidate.ExecutablePath -or (Get-NormalizedPath $candidate.ExecutablePath) -ne $expectedExe) {
    throw 'The process using this project configuration has a different or unreadable executable. No process was stopped.'
}
$listener = @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)
if ($listener.Count -gt 0 -and ($listener | Where-Object { $_.OwningProcess -ne $candidate.ProcessId })) {
    throw "Port $port belongs to a different process. No shutdown request was sent."
}
$adminExe = Join-Path $state.bin 'mysqladmin.exe'
$adminConfig = Join-Path $localRoot 'admin.cnf'
if (-not (Test-Path -LiteralPath $adminExe) -or -not (Test-Path -LiteralPath $adminConfig)) {
    throw 'MySQL administrator binary or admin.cnf is missing. No process was stopped.'
}
$servers = @($candidates | ForEach-Object { Get-Process -Id $_.ProcessId -ErrorAction SilentlyContinue })
$previousPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    & $adminExe "--defaults-file=$adminConfig" --protocol=TCP --host=127.0.0.1 "--port=$port" --connect-timeout=5 shutdown 2>$null | Out-Null
    $shutdownExitCode = $LASTEXITCODE
} finally { $ErrorActionPreference = $previousPreference }
if ($shutdownExitCode -ne 0) { throw 'Could not stop project MySQL. Check its readiness and administrator settings; all data is preserved.' }
foreach ($server in $servers) {
    if (-not $server.WaitForExit(15000)) { throw 'MySQL accepted shutdown but is still exiting. Wait before restarting; no process was forcibly terminated.' }
}
Write-Host 'Project MySQL stopped. All data is preserved.'
