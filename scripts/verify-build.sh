#!/usr/bin/env bash
# Verifies the core build end to end. Requires JDK 25 and Node 22+.
set -euo pipefail
cd "$(dirname "$0")/.."

java -version 2>&1 | head -1
node -v

echo "== frontend: install, typecheck, test, build"
(cd frontend && npm ci --no-audit --no-fund \
  && npx tsc --noEmit --project src/editor/core/tsconfig.json \
  && npx vitest run \
  && npx vite build --mode core)

echo "== desktop shell: tests"
(cd desktop && npm test)

echo "== backend: compile and test (includes app/auth tests)"
./gradlew clean build -PbuildWithFrontend=true -x spotlessCheck

echo "== done: start with  ./gradlew bootRun -PbuildWithFrontend=true  then open http://localhost:8080"
