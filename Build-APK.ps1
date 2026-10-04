$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$buildCache = Join-Path $env:LOCALAPPDATA 'NayaPothaBuild'
New-Item -ItemType Directory -Force $buildCache | Out-Null
if (-not $env:JAVA_HOME) {
    $studioJdk = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path "$studioJdk\bin\java.exe") { $env:JAVA_HOME = $studioJdk }
}
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) { throw 'Install Android Studio or Java 17+, then set JAVA_HOME and try again.' }
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$sdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not (Test-Path "$sdkPath\platforms\android-35\android.jar")) { throw 'In Android Studio > SDK Manager install Android SDK Platform 35 and Build Tools 35.0.0, then retry.' }
$env:ANDROID_HOME = $sdkPath
$gradleHome = Join-Path $buildCache 'gradle-8.11.1'
if (-not (Test-Path "$gradleHome\bin\gradle.bat")) {
    $zipPath = Join-Path $buildCache 'gradle.zip'
    Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' -OutFile $zipPath
    Expand-Archive $zipPath -DestinationPath $buildCache -Force
}
& "$gradleHome\bin\gradle.bat" --no-daemon assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'APK build failed. Read the error above.' }
Copy-Item 'app\build\outputs\apk\debug\app-debug.apk' 'NayaPotha.apk' -Force
Write-Host "APK ready: $PSScriptRoot\NayaPotha.apk"
