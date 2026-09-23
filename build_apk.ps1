# Собирает debug-APK и кладёт его на рабочий стол как MetroEkb.apk.
# Если двойной клик/ "Выполнить с помощью PowerShell" мигает и закрывается - запусти
# вместо него build_apk.bat (двойной клик), он надёжнее. Либо в терминале:
#   powershell -ExecutionPolicy Bypass -File build_apk.ps1
try {
    Set-Location -Path $PSScriptRoot

    if ([string]::IsNullOrEmpty($env:JAVA_HOME)) {
        $jdk = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
        if (Test-Path (Join-Path $jdk "bin\java.exe")) { $env:JAVA_HOME = $jdk }
    }
    Write-Host "JAVA_HOME = $env:JAVA_HOME"
    Write-Host "Собираю APK (первый раз качает Gradle и зависимости - это долго)..." -ForegroundColor Cyan

    & "$PSScriptRoot\gradlew.bat" assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Сборка не удалась (код $LASTEXITCODE). Текст ошибки выше." }

    $apk = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apk)) { throw "APK не найден: $apk" }

    $desktop = [Environment]::GetFolderPath("Desktop")
    $dest = Join-Path $desktop "MetroEkb.apk"
    Copy-Item $apk $dest -Force
    Write-Host "ГОТОВО: $dest" -ForegroundColor Green
}
catch {
    Write-Host "ОШИБКА: $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    Read-Host "Нажми Enter, чтобы закрыть окно"
}
