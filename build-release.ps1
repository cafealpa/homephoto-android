param([string]$SigningConfig = "$env:USERPROFILE\.android\homephoto-release\signing.json")
$ErrorActionPreference = 'Stop'
$config = Get-Content -LiteralPath $SigningConfig -Raw | ConvertFrom-Json
foreach ($field in @('keystore','alias','storePassword','keyPassword')) {
    if ([string]::IsNullOrWhiteSpace($config.$field)) { throw "서명 설정에 $field 항목이 없어요." }
}
if (-not (Test-Path -LiteralPath $config.keystore -PathType Leaf)) { throw '서명 키 파일을 찾을 수 없어요.' }
$values = @{
    HOMEPHOTO_KEYSTORE = $config.keystore
    HOMEPHOTO_KEY_ALIAS = $config.alias
    HOMEPHOTO_STORE_PASSWORD = $config.storePassword
    HOMEPHOTO_KEY_PASSWORD = $config.keyPassword
}
$previous = @{}
foreach ($name in $values.Keys) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
}
Push-Location $PSScriptRoot
try {
    & .\gradlew.bat :app:testDebugUnitTest :app:assembleRelease --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw '테스트 또는 release 빌드에 실패했어요.' }
    & .\prepare-apk-release.ps1 -ApkPath .\app\build\outputs\apk\release\app-release.apk
} finally {
    Pop-Location
    foreach ($name in $previous.Keys) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
