$ErrorActionPreference = 'Stop'

$projectDirectory = Split-Path -Parent $PSScriptRoot
$signingDirectory = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'OMEGA\Signing'
$keystore = Join-Path $signingDirectory 'omega-attendance-release.p12'
$secretFile = Join-Path $signingDirectory 'signing-password.dpapi'
$apksigner = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'Android\Sdk\build-tools\36.0.0\apksigner.bat'
$aapt = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'Android\Sdk\build-tools\36.0.0\aapt2.exe'
$apk = Join-Path ([IO.Path]::GetTempPath()) 'omega-attendance-build-v2\app\outputs\apk\release\app-release.apk'
$buildScript = [IO.File]::ReadAllText((Join-Path $projectDirectory 'app\build.gradle.kts'))
$versionMatch = [regex]::Match($buildScript, 'versionName\s*=\s*"([^"]+)"')
if (-not $versionMatch.Success) { throw 'Could not read versionName from app/build.gradle.kts' }
$destination = Join-Path $projectDirectory ('dist\Hodoor-' + $versionMatch.Groups[1].Value + '-Release.apk')

if (-not (Test-Path -LiteralPath $keystore) -or -not (Test-Path -LiteralPath $secretFile)) {
    throw 'Release signing material is missing. Run Initialize-OmegaSigning.ps1 first.'
}
if (-not (Test-Path -LiteralPath $apksigner)) { throw "apksigner not found: $apksigner" }
if (-not (Test-Path -LiteralPath $aapt)) { throw "aapt2 not found: $aapt" }
if (Test-Path -LiteralPath $destination) { throw "Release APK already exists and will not be overwritten: $destination" }

$protectedPassword = [IO.File]::ReadAllText($secretFile, [Text.Encoding]::ASCII)
$securePassword = ConvertTo-SecureString $protectedPassword
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
}

$env:OMEGA_RELEASE_STORE_FILE = $keystore
$env:OMEGA_RELEASE_STORE_PASSWORD = $password
$env:OMEGA_RELEASE_KEY_ALIAS = 'omega-attendance'
$env:OMEGA_RELEASE_KEY_PASSWORD = $password
try {
    Push-Location $projectDirectory
    try {
        & (Join-Path $projectDirectory 'gradlew.bat') --no-configuration-cache --no-daemon assembleRelease
        if ($LASTEXITCODE -ne 0) { throw "Release build failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
} finally {
    Remove-Item Env:OMEGA_RELEASE_STORE_FILE -ErrorAction SilentlyContinue
    Remove-Item Env:OMEGA_RELEASE_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:OMEGA_RELEASE_KEY_ALIAS -ErrorAction SilentlyContinue
    Remove-Item Env:OMEGA_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
    $password = $null
    $securePassword = $null
}

if (-not (Test-Path -LiteralPath $apk)) { throw "Release APK was not created: $apk" }
& $apksigner verify --verbose --print-certs $apk
if ($LASTEXITCODE -ne 0) { throw 'Release APK signature verification failed' }
$permissions = & $aapt dump permissions $apk
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect release APK permissions' }
if ($permissions -match 'android\.permission\.(INTERNET|ACCESS_NETWORK_STATE)') {
    throw 'Release APK unexpectedly requests a network permission'
}
Copy-Item -LiteralPath $apk -Destination $destination
if ((Get-Item -LiteralPath $destination).Length -lt 1MB) { throw 'Copied release APK is unexpectedly small' }
Write-Output "Verified signed release APK: $destination"
Write-Output 'Verified offline manifest: no INTERNET or ACCESS_NETWORK_STATE permission'
