param([Security.SecureString]$ApiKey)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'
Push-Location $projectRoot
try {
    & git check-ignore --quiet -- .env
    if ($LASTEXITCODE -ne 0) { throw '.env must be ignored by Git before saving an API key.' }
    $tracked = & git ls-files -- .env
    if ($tracked) { throw 'Remove .env from Git tracking before saving an API key.' }
} finally { Pop-Location }

if (-not $ApiKey) { $ApiKey = Read-Host 'OpenAI API key (hidden input)' -AsSecureString }
$pointer = [IntPtr]::Zero
$plainKey = $null
try {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($ApiKey)
    $plainKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer).Trim()
    if ($plainKey -notmatch '^sk-[A-Za-z0-9_-]{20,}$') { throw 'Invalid key format. Paste the key without quotes or spaces.' }
    $lines = @()
    if (Test-Path -LiteralPath $envFile) {
        $lines = @(Get-Content -LiteralPath $envFile -Encoding UTF8 | Where-Object { $_ -notmatch '^\s*OPENAI_(API_KEY|MODEL)\s*=' })
    } else {
        New-Item -ItemType File -Path $envFile | Out-Null
    }
    # Restrict access BEFORE writing the key. Only this Windows user and SYSTEM can read it.
    $acl = New-Object Security.AccessControl.FileSecurity
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $systemSid = New-Object Security.Principal.SecurityIdentifier('S-1-5-18')
    $acl.SetOwner($sid)
    $acl.SetAccessRuleProtection($true, $false)
    $acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($sid, 'FullControl', 'Allow')))
    $acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($systemSid, 'FullControl', 'Allow')))
    Set-Acl -LiteralPath $envFile -AclObject $acl
    $lines += "OPENAI_API_KEY=$plainKey"
    $lines += 'OPENAI_MODEL=gpt-5-mini'
    [IO.File]::WriteAllText($envFile, ($lines -join "`r`n") + "`r`n", (New-Object Text.UTF8Encoding($false)))
    Write-Host 'Saved server-only OpenAI settings (gpt-5-mini). Restart the backend to apply.'
} finally {
    if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    $plainKey = $null
    $lines = $null
}
