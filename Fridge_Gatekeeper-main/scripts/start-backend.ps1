param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$localJdk = Get-ChildItem -Path (Join-Path $projectRoot '.local/jdk-21*') -Directory -ErrorAction SilentlyContinue |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/javac.exe') } | Select-Object -First 1
if ($localJdk) {
    $env:JAVA_HOME = $localJdk.FullName
    $env:Path = "$env:JAVA_HOME/bin;$env:Path"
}
$localConnection = Join-Path $projectRoot '.local/mysql/connection.json'
if (-not $env:DB_URL -and (Test-Path -LiteralPath $localConnection)) {
    $env:DB_URL = (Get-Content -LiteralPath $localConnection -Raw -Encoding UTF8 | ConvertFrom-Json).url
}
$envFile = Join-Path $projectRoot '.env'
# .env는 Spring Boot가 자동으로 읽지 않으므로 이 실행 스크립트에서 읽습니다.
# 비밀 값을 화면에 출력하거나 문자열을 명령으로 실행하지 않습니다.
$allowed = @('DB_URL','DB_USERNAME','DB_PASSWORD','SERVER_PORT','SERVER_ADDRESS','SESSION_COOKIE_SECURE','OPENAI_API_KEY','OPENAI_MODEL','OPENAI_BASE_URL','AI_USER_REQUESTS_PER_MINUTE','AI_REQUESTS_PER_MINUTE','AI_REQUESTS_PER_DAY','AI_CONCURRENT_REQUESTS')
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in (Get-Content -Encoding UTF8 -LiteralPath $envFile)) {
        $entry = $line.Trim()
        if (-not $entry -or $entry.StartsWith('#')) { continue }
        $parts = $entry -split '=', 2
        if ($parts.Length -ne 2) { continue }
        $key = $parts[0].Trim()
        if ($key -notin $allowed) { continue }
        $value = $parts[1].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($key, $value, 'Process')
    }
}
Push-Location (Join-Path $projectRoot 'backend')
try { & .\mvnw.cmd spring-boot:run; $runExitCode = $LASTEXITCODE }
finally { Pop-Location }
exit $runExitCode
