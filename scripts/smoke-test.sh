#!/usr/bin/env bash
# End-to-end check of a running server with login enabled. Usage: smoke-test.sh [base-url]
set -euo pipefail
BASE="${1:-http://localhost:8080}"
ADMIN_PW="${KAGAZ_AUTH_ADMIN_PASSWORD:?set KAGAZ_AUTH_ADMIN_PASSWORD to the admin password the server started with}"
J='Content-Type: application/json'
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
fail() { echo "FAIL: $*"; exit 1; }
code() { curl -s -o "$T/body" -w '%{http_code}' "$@"; }
expect() { local want="$1"; shift; local got; got=$(code "$@"); [ "$got" = "$want" ] || { cat "$T/body"; echo; fail "expected $want got $got: $*"; }; echo "ok  $want  ${*: -1}"; }

echo "== anonymous access is refused"
expect 401 "$BASE/api/v1/info/status"
expect 200 "$BASE/login.html"
expect 200 "$BASE/api/v1/auth/status"

echo "== login"
expect 401 -X POST -H "$J" -d '{"username":"admin","password":"wrong-password"}' "$BASE/api/v1/auth/login"
expect 200 -c "$T/admin.jar" -X POST -H "$J" -d "{\"username\":\"admin\",\"password\":\"$ADMIN_PW\"}" "$BASE/api/v1/auth/login"
expect 200 -b "$T/admin.jar" "$BASE/api/v1/auth/me"

echo "== admin creates a user, who must change the password"
expect 201 -b "$T/admin.jar" -X POST -H "$J" -d '{"username":"bob","password":"temp-pass-123","role":"USER","mustChangePassword":true}' "$BASE/api/v1/auth/admin/users"
expect 200 -c "$T/bob.jar" -X POST -H "$J" -d '{"username":"bob","password":"temp-pass-123"}' "$BASE/api/v1/auth/login"
expect 403 -b "$T/bob.jar" "$BASE/api/v1/info/status"
expect 200 -b "$T/bob.jar" -X POST -H "$J" -d '{"currentPassword":"temp-pass-123","newPassword":"better-pass-456"}' "$BASE/api/v1/auth/password"
expect 200 -b "$T/bob.jar" "$BASE/api/v1/info/status"
expect 403 -b "$T/bob.jar" "$BASE/api/v1/auth/admin/users"

echo "== API key"
expect 200 -b "$T/bob.jar" -X POST "$BASE/api/v1/auth/apikey"
KEY=$(sed -E 's/.*"apiKey":"([^"]+)".*/\1/' "$T/body")
expect 200 -H "X-API-KEY: $KEY" "$BASE/api/v1/auth/me"
expect 401 -H "X-API-KEY: kpdf_not-a-real-key" "$BASE/api/v1/auth/me"

echo "== a real PDF operation through the key"
python3 - "$T/in.pdf" <<'PY'
import sys
objs=[b"<< /Type /Catalog /Pages 2 0 R >>",b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >>"]
out=b"%PDF-1.4\n"; offs=[]
for i,o in enumerate(objs,1):
    offs.append(len(out)); out+=b"%d 0 obj\n%s\nendobj\n"%(i,o)
x=len(out); out+=b"xref\n0 %d\n0000000000 65535 f \n"%(len(objs)+1)
for o in offs: out+=b"%010d 00000 n \n"%o
out+=b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n"%(len(objs)+1,x)
open(sys.argv[1],"wb").write(out)
PY
got=$(curl -s -o "$T/out.pdf" -w '%{http_code}' -H "X-API-KEY: $KEY" -F fileInput=@"$T/in.pdf" -F angle=90 "$BASE/api/v1/general/rotate-pdf")
[ "$got" = 200 ] && head -c4 "$T/out.pdf" | grep -q '%PDF' || fail "rotate-pdf returned $got"
echo "ok  200  rotate-pdf produced a PDF"

echo "== disabled user is locked out at once"
expect 200 -b "$T/admin.jar" -X PATCH -H "$J" -d '{"enabled":false}' "$BASE/api/v1/auth/admin/users/bob"
expect 401 -H "X-API-KEY: $KEY" "$BASE/api/v1/auth/me"
expect 401 -b "$T/bob.jar" "$BASE/api/v1/auth/me"

echo "== audit log"
# API calls are logged after the response is sent, so allow a moment for the last entry.
for attempt in 1 2 3 4 5 6 7 8 9 10; do
  expect 200 -b "$T/admin.jar" "$BASE/api/v1/auth/admin/audit?limit=100"
  missing=""
  for a in login user-create password-change apikey-issue user-update 'api:POST'; do grep -q "\"$a\"" "$T/body" || missing="$missing $a"; done
  [ -z "$missing" ] && break
  sleep 1
done
[ -z "$missing" ] || { cat "$T/body"; echo; fail "audit log has no entry for:$missing"; }
echo "ok  audit log records login, user, key and API activity"

echo "== lock-out after repeated failures"
for i in 1 2 3 4 5; do code -X POST -H "$J" -d '{"username":"carol","password":"x"}' "$BASE/api/v1/auth/login" >/dev/null; done
expect 429 -X POST -H "$J" -d '{"username":"carol","password":"x"}' "$BASE/api/v1/auth/login"
echo "ALL SMOKE CHECKS PASSED"
