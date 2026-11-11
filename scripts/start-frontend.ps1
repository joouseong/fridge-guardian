$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $projectRoot 'frontend')
try {
    npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw '프론트엔드 의존성 설치에 실패했습니다.' }
    npm.cmd run dev
    $runExitCode = $LASTEXITCODE
} finally { Pop-Location }
exit $runExitCode
