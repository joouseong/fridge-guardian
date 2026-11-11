$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$portableStart = Join-Path $projectRoot '.local/start-mysql.ps1'
if (Test-Path -LiteralPath (Join-Path $projectRoot '.local/mysql/connection.json')) {
    $state = Get-Content -LiteralPath (Join-Path $projectRoot '.local/mysql/connection.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    & (Join-Path $PSScriptRoot 'start-local-db.ps1') -MySqlBin $state.bin -Port $state.port
} elseif (Test-Path -LiteralPath $portableStart) {
    & $portableStart
} elseif (Get-Command docker -ErrorAction SilentlyContinue) {
    Push-Location $projectRoot
    try { docker compose up -d --wait mysql; $runExitCode = $LASTEXITCODE }
    finally { Pop-Location }
    if ($runExitCode -ne 0) { throw 'MySQL을 시작하지 못했습니다. Docker Desktop 실행 상태를 확인하세요.' }
} else {
    & (Join-Path $PSScriptRoot 'start-local-db.ps1')
}
