param(
    [ValidateSet('google', 'kakao')][string]$Provider,
    [string]$ClientId,
    [Security.SecureString]$ClientSecret
)
# 소셜 로그인 키를 .env에 저장합니다. 비밀 값은 화면에 보이지 않고, 이 Windows 사용자만 .env를 읽을 수 있게 합니다.
# 사용: powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/setup-social-login.ps1 [-Provider google|kakao]
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'

# Git 저장소일 때만 .env가 추적/커밋되지 않는지 확인합니다. (저장소가 아니면 건너뜁니다)
if (Test-Path -LiteralPath (Join-Path $projectRoot '.git')) {
    Push-Location $projectRoot
    try {
        & git check-ignore --quiet -- .env
        if ($LASTEXITCODE -ne 0) { throw '.env must be ignored by Git before saving keys.' }
        if (& git ls-files -- .env) { throw 'Remove .env from Git tracking before saving keys.' }
    } finally { Pop-Location }
}

if (-not $Provider) { $Provider = (Read-Host 'Provider (google or kakao)').Trim().ToLower() }
if ($Provider -notin 'google', 'kakao') { throw 'Provider must be google or kakao.' }
$prefix = $Provider.ToUpper()

if (-not $ClientId) {
    $label = if ($Provider -eq 'google') { 'Google OAuth client ID' } else { 'Kakao REST API key' }
    $ClientId = (Read-Host $label).Trim()
}
if ($ClientId -notmatch '^[A-Za-z0-9._-]{8,}$') { throw 'Invalid client ID format. Paste it without quotes or spaces.' }
if (-not $ClientSecret) { $ClientSecret = Read-Host "$Provider client secret (hidden input)" -AsSecureString }

$pointer = [IntPtr]::Zero
$plainSecret = $null
try {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($ClientSecret)
    $plainSecret = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer).Trim()
    if ($plainSecret -notmatch '^[A-Za-z0-9._-]{8,}$') { throw 'Invalid client secret format. Paste it without quotes or spaces.' }

    $lines = @()
    if (Test-Path -LiteralPath $envFile) {
        $lines = @(Get-Content -LiteralPath $envFile -Encoding UTF8 | Where-Object { $_ -notmatch "^\s*${prefix}_CLIENT_(ID|SECRET)\s*=" })
    } else {
        New-Item -ItemType File -Path $envFile | Out-Null
    }
    # 비밀 값을 쓰기 전에 접근 권한부터 제한합니다. (현재 사용자와 SYSTEM만)
    $acl = New-Object Security.AccessControl.FileSecurity
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $systemSid = New-Object Security.Principal.SecurityIdentifier('S-1-5-18')
    $acl.SetOwner($sid)
    $acl.SetAccessRuleProtection($true, $false)
    $acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($sid, 'FullControl', 'Allow')))
    $acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($systemSid, 'FullControl', 'Allow')))
    Set-Acl -LiteralPath $envFile -AclObject $acl
    $lines += "${prefix}_CLIENT_ID=$ClientId"
    $lines += "${prefix}_CLIENT_SECRET=$plainSecret"
    [IO.File]::WriteAllText($envFile, ($lines -join "`r`n") + "`r`n", (New-Object Text.UTF8Encoding($false)))
    Write-Host "Saved $Provider login keys to .env. Restart the backend (scripts/start-backend.ps1) to apply."
} finally {
    if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    $plainSecret = $null
    $lines = $null
}
