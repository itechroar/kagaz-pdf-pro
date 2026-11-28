# Administrator setup for Windows (PowerShell). Run via setup-admin.bat. Add -Network to let other
# computers and phones reach the administrator site.
param([switch]$Network)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Write-Host 'Docker is not installed. Get it from https://www.docker.com/products/docker-desktop/'; exit 1 }

docker image inspect kagaz-pdf:local *> $null
if (-not (Test-Path .env) -or $LASTEXITCODE -ne 0) { & "$PSScriptRoot\setup.ps1" | Out-Null }

if ($Network) {
  $lines = Get-Content .env | Where-Object { $_ -notmatch '^KAGAZ_ADMIN_HOST_BIND=' }
  $lines + 'KAGAZ_ADMIN_HOST_BIND=0.0.0.0' | Set-Content -Encoding ascii .env
  Write-Host 'The administrator site now accepts connections from your network. Keep it behind a firewall or VPN.'
}
docker compose up -d

$port = ((Get-Content .env | Where-Object { $_ -match '^KAGAZ_ADMIN_HOST_PORT=' } | Select-Object -First 1) -replace '^.*=', '')
if (-not $port) { $port = '8081' }
for ($i = 0; $i -lt 120; $i++) {
  try {
    Invoke-WebRequest -UseBasicParsing "http://localhost:$port/login.html" | Out-Null
    Write-Host ''
    Write-Host "Administrator site: http://localhost:$port"
    if ($Network) {
      $lan = (Get-NetIPConfiguration | Where-Object { $_.IPv4DefaultGateway } | Select-Object -First 1).IPv4Address.IPAddress
      if ($lan) { Write-Host "From other devices: http://${lan}:$port (allow Docker through the Windows firewall if asked)" }
    }
    Write-Host 'Sign in as: admin    Password: the value of KAGAZ_AUTH_ADMIN_PASSWORD in .env'
    Write-Host ''
    Write-Host 'Administrator apps (download from the Releases page):'
    Write-Host '  Desktop: KagazPDFAdmin-<version>-windows  -> choose Connect to a server and enter the address above'
    Write-Host '  Android: KagazPDFAdmin-android.apk        -> enter the address above'
    Start-Process "http://localhost:$port"
    exit 0
  } catch { Start-Sleep -Seconds 5 }
}
Write-Host 'The administrator site did not answer in time. See the logs with: docker compose logs'
exit 1
