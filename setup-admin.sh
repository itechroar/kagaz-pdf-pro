#!/usr/bin/env bash
# Administrator setup. Makes sure the server is running, then shows where the separate administrator
# site is and how to sign in. Pass --network to let other computers and phones reach the
# administrator site (otherwise it answers on this computer only).
set -euo pipefail
cd "$(dirname "$0")"

command -v docker >/dev/null 2>&1 || { echo "Docker is not installed. Get it from https://www.docker.com/products/docker-desktop/"; exit 1; }

if [ ! -f .env ] || ! docker image inspect kagaz-pdf:local >/dev/null 2>&1; then
  ./setup.sh >/dev/null || true
fi

if [ "${1:-}" = "--network" ]; then
  grep -q '^KAGAZ_ADMIN_HOST_BIND=' .env && sed -i.bak 's/^KAGAZ_ADMIN_HOST_BIND=.*/KAGAZ_ADMIN_HOST_BIND=0.0.0.0/' .env && rm -f .env.bak || echo 'KAGAZ_ADMIN_HOST_BIND=0.0.0.0' >> .env
  docker compose up -d
  echo "The administrator site now accepts connections from your network. Keep it behind a firewall or VPN."
else
  docker compose up -d
fi

PORT=$(grep -E '^KAGAZ_ADMIN_HOST_PORT=' .env | cut -d= -f2 || true); PORT=${PORT:-8081}
for _ in $(seq 1 120); do
  if curl -fs "http://localhost:$PORT/login.html" >/dev/null 2>&1; then
    echo
    echo "Administrator site: http://localhost:$PORT"
    LAN_IP=$( (ipconfig getifaddr en0 || ipconfig getifaddr en1 || ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}') 2>/dev/null | head -1 || true)
    if grep -q '^KAGAZ_ADMIN_HOST_BIND=0.0.0.0' .env && [ -n "${LAN_IP:-}" ]; then echo "From other devices: http://$LAN_IP:$PORT"; fi
    echo "Sign in as: admin    Password: the value of KAGAZ_AUTH_ADMIN_PASSWORD in .env"
    echo
    echo "Administrator apps (download from the Releases page):"
    echo "  Desktop: KagazPDFAdmin-<version>-<windows|mac|linux>  -> choose 'Connect to a server' and enter the address above"
    echo "  Android: KagazPDFAdmin-android.apk                    -> enter the address above"
    (xdg-open "http://localhost:$PORT" || open "http://localhost:$PORT") >/dev/null 2>&1 || true
    exit 0
  fi
  sleep 5
done
echo "The administrator site did not answer in time. See the logs with: docker compose logs"
exit 1
