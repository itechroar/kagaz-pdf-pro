# One-step local setup for Windows (PowerShell). Run via setup.bat.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$remote = 'ghcr.io/itechroar/kagaz-pdf-pro:latest'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Write-Host 'Docker is not installed. Get it from https://www.docker.com/products/docker-desktop/'; exit 1 }
docker info *> $null
if ($LASTEXITCODE -ne 0) { Write-Host 'Docker is installed but not running. Start Docker Desktop, then run this again.'; exit 1 }

$newPassword = ''
if (-not (Test-Path .env)) {
  $newPassword = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 14 | ForEach-Object { [char]$_ })
  $lines = Get-Content .env.example | Where-Object { $_ -notmatch '^KAGAZ_AUTH_ADMIN_PASSWORD=' }
  $lines + "KAGAZ_AUTH_ADMIN_PASSWORD=$newPassword" | Set-Content -Encoding ascii .env
}

docker image inspect kagaz-pdf:local *> $null
if ($LASTEXITCODE -ne 0) {
  docker pull $remote *> $null
  if ($LASTEXITCODE -eq 0) {
    docker tag $remote kagaz-pdf:local
  } else {
    Write-Host 'Prebuilt image not available; building locally. This takes a while the first time (30 minutes or more).'
    docker build -f docker/base/Dockerfile -t kagaz-pdf-base:local .
    docker build -f docker/embedded/Dockerfile --build-arg BASE_IMAGE=kagaz-pdf-base:local -t kagaz-pdf:local .
  }
}

docker compose up -d
Write-Host 'Waiting for the server to start...'
for ($i = 0; $i -lt 120; $i++) {
  try {
    Invoke-WebRequest -UseBasicParsing http://localhost:8080/login.html | Out-Null
    Write-Host ''
    Write-Host 'Kagaz PDF is running: http://localhost:8080'
    if ($newPassword) { Write-Host "Sign in as: admin    Password: $newPassword    (saved in .env, change it after signing in)" }
    else { Write-Host 'Sign in as admin with the password in .env (KAGAZ_AUTH_ADMIN_PASSWORD).' }
    Start-Process 'http://localhost:8080'
    exit 0
  } catch { Start-Sleep -Seconds 5 }
}
Write-Host 'The server did not answer in time. See the logs with: docker compose logs'
exit 1
