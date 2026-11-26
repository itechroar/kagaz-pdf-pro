#!/usr/bin/env bash
# Builds the base image (Ghostscript, QPDF, LibreOffice, OCR tools) and then the app image.
# The base image rarely changes; pass --skip-base to reuse an existing kagaz-pdf-base:local.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ "${1:-}" != "--skip-base" ]; then
  docker build -f docker/base/Dockerfile -t kagaz-pdf-base:local .
fi
docker build -f docker/embedded/Dockerfile --build-arg BASE_IMAGE=kagaz-pdf-base:local -t kagaz-pdf:local .
echo "Built kagaz-pdf:local. Start it with: docker compose up -d"
