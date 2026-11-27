## Quick start (Docker)

Run `./setup.sh` (Windows: double-click `setup.bat`). It pulls or builds the image, creates `.env` with a
generated admin password on first run, starts the server and opens http://localhost:8080.

# Deploying Kagaz PDF

## Docker (recommended, works online and offline)

```bash
cp .env.example .env          # set KAGAZ_AUTH_ADMIN_PASSWORD, add AI keys when you have them
./scripts/build-docker.sh     # builds kagaz-pdf-base:local then kagaz-pdf:local (first run is slow)
docker compose up -d          # http://localhost:8080  (login page, then the tools)
```

Data (users, audit log, settings) is in the `kagaz-data` volume at `/configs`. Back it up.

Put a TLS reverse proxy (Caddy, nginx, Traefik) in front for internet use; the session cookie and
SSO callback should be served over HTTPS. Set `KAGAZ_AUTH_PUBLIC_URL` to that public address.

## Without Docker

Needs JDK 25: `./gradlew bootJar -PbuildWithFrontend=true`, then
`SECURITY_ENABLELOGIN=true java -jar app/core/build/libs/<boot jar>`. Install Tesseract
(and Ghostscript, QPDF, LibreOffice for the tools that use them) on the host.

## Modes

| Mode | How |
|---|---|
| Local, single user | Leave login off (`SECURITY_ENABLELOGIN=false`) and bind to localhost. |
| Online / team | Login on, HTTPS proxy in front, optional SSO. |
| Offline | Same image, no internet needed. For the AI assistant set `KAGAZ_OFFLINE=true` and use `ollama` (see the `local-ai` profile in `docker-compose.yml`). |
| Desktop | `desktop/` (Electron) runs the server on a private local port. |

## AI assistant keys

Nothing breaks without a key: `/ai.html` shows "Not ready yet: Add your API key" until you set
`KAGAZ_AI_PROVIDER` and `KAGAZ_AI_API_KEY` (see `.env.example` for every provider and option) and restart.
Test a real key with `scripts/check-ai.sh`.

## Checks

`scripts/verify-build.sh` (frontend + backend), `scripts/smoke-test.sh` (a running server),
`scripts/smoke-sso.sh`, `scripts/smoke-ai.sh`; GitHub Actions runs all of them on every push.
