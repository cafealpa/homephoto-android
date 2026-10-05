param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
)
$ErrorActionPreference = 'Stop'
$apk = (Resolve-Path -LiteralPath $ApkPath).Path
$buildTools = Get-ChildItem -LiteralPath (Join-Path $SdkRoot 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildTools) { throw 'Android SDK Build Tools를 찾을 수 없어요. -SdkRoot를 지정해 주세요.' }
$badging = (& (Join-Path $buildTools.FullName 'aapt2.exe') dump badging $apk) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'APK 정보를 읽지 못했어요.' }
$package = [regex]::Match($badging, "package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'")
if (-not $package.Success -or $package.Groups[1].Value -ne 'com.chochocho.homephotoclient') { throw 'HomePhoto APK가 아니에요.' }
if ($badging -match '(?m)^application-debuggable') { throw '정식 릴리즈에는 debug APK를 사용할 수 없어요. 서명한 release APK를 준비해 주세요.' }
& (Join-Path $buildTools.FullName 'apksigner.bat') verify $apk
if ($LASTEXITCODE -ne 0) { throw 'APK 서명 검증에 실패했어요.' }
$code = [long]$package.Groups[2].Value
if ($code -lt 1) { throw 'versionCode는 1 이상이어야 해요.' }
$dest = Join-Path $PSScriptRoot "build\apk-release\$code"
New-Item -ItemType Directory -Force -Path $dest | Out-Null
$target = Join-Path $dest "homephoto-android-$code.apk"
Copy-Item -LiteralPath $apk -Destination $target -Force
$hash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText("$target.sha256", "$hash  homephoto-android-$code.apk`n", [Text.UTF8Encoding]::new($false))
Write-Output "릴리즈 파일: $target"
Write-Output "버전: $($package.Groups[3].Value) ($code)"
Write-Output "SHA-256: $hash"
Write-Output 'cafealpa/homephoto-android의 정식 GitHub Release에 이 APK를 첨부해 주세요. 업로드/공개는 자동 실행하지 않아요.'
