$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Test-Path .env)) {
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $key = New-Object byte[] 48
    $rng.GetBytes($key)
    $jwt = [Convert]::ToBase64String($key)
    $secret = New-Object byte[] 18
    $rng.GetBytes($secret)
    $dbPassword = ([BitConverter]::ToString($secret).Replace('-',''))
    $rng.GetBytes($secret)
    $adminPassword = 'Ad1!' + ([BitConverter]::ToString($secret).Replace('-',''))
    $lines = @("POSTGRES_PASSWORD=$dbPassword", "JWT_SECRET_BASE64=$jwt", 'BOOTSTRAP_ADMIN_EMAIL=admin@example.org', "BOOTSTRAP_ADMIN_PASSWORD=$adminPassword", 'APP_PORT=8080', 'POSTGRES_PORT=55434')
    [IO.File]::WriteAllLines((Join-Path $PSScriptRoot '.env'), $lines, [Text.UTF8Encoding]::new($false))
    Write-Host 'Создан .env. Логин и временный пароль администратора записаны в этом файле.'
}
docker compose up -d --wait --wait-timeout 600
if ($LASTEXITCODE -ne 0) { throw 'Ошибка запуска. Выполните docker compose logs backend.' }
Write-Host 'Откройте http://localhost:8080/  |  Health: http://localhost:8080/actuator/health'
