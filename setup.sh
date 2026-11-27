#!/usr/bin/env bash
# One-step local setup: pulls (or builds) the Docker image, creates .env with a generated admin
# password on first run, starts the server and opens it in your browser.
set -euo pipefail
cd "$(dirname "$0")"
IMAGE_REMOTE="ghcr.io/itechroar/kagaz-pdf-pro:latest"

command -v docker >/dev/null 2>&1 || { echo "Docker is not installed. Get it from https://www.docker.com/products/docker-desktop/"; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker is installed but not running. Start Docker Desktop, then run this again."; exit 1; }

NEW_PASSWORD=""
if [ ! -f .env ]; then
  NEW_PASSWORD=$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 14 || true)
  { grep -v '^KAGAZ_AUTH_ADMIN_PASSWORD=' .env.example; echo "KAGAZ_AUTH_ADMIN_PASSWORD=$NEW_PASSWORD"; } > .env
  chmod 600 .env 2>/dev/null || true
fi

if ! docker image inspect kagaz-pdf:local >/dev/null 2>&1; then
  if docker pull "$IMAGE_REMOTE" >/dev/null 2>&1; then
    docker tag "$IMAGE_REMOTE" kagaz-pdf:local
  else
    echo "Prebuilt image not available; building locally. This takes a while the first time (30 minutes or more)."
    ./scripts/build-docker.sh
  fi
fi

docker compose up -d

echo "Waiting for the server to start..."
for _ in $(seq 1 120); do
  if curl -fs http://localhost:8080/login.html >/dev/null 2>&1; then
    echo
    echo "Kagaz PDF is running: http://localhost:8080"
    LAN_IP=$( (ipconfig getifaddr en0 || ipconfig getifaddr en1 || ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}') 2>/dev/null | head -1 || true)
    if [ -n "${LAN_IP:-}" ]; then echo "For the Android app on the same Wi-Fi, enter: $LAN_IP:8080"; fi
    if [ -n "$NEW_PASSWORD" ]; then
      echo "Sign in as: admin    Password: $NEW_PASSWORD    (saved in .env, change it after signing in)"
    else
      echo "Sign in as admin with the password in .env (KAGAZ_AUTH_ADMIN_PASSWORD)."
    fi
    (xdg-open http://localhost:8080 || open http://localhost:8080) >/dev/null 2>&1 || true
    exit 0
  fi
  sleep 5
done
echo "The server did not answer in time. See the logs with: docker compose logs"
exit 1
