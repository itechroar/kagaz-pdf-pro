#!/usr/bin/env bash
# Try the assistant against YOUR real model once keys are configured.
# Usage: KAGAZ_API_KEY=kpdf_... scripts/check-ai.sh path/to/file.pdf [base-url]
set -euo pipefail
PDF="${1:?usage: check-ai.sh file.pdf [base-url]}"; BASE="${2:-http://localhost:8080}"
KEY="${KAGAZ_API_KEY:?set KAGAZ_API_KEY to a key from /developer.html}"
echo "-- status"; curl -s -H "X-API-KEY: $KEY" "$BASE/api/v1/ai/status"; echo
echo "-- summary"; curl -s -H "X-API-KEY: $KEY" -F fileInput=@"$PDF" "$BASE/api/v1/ai/summarize"; echo
