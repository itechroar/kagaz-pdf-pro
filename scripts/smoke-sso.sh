#!/usr/bin/env bash
# SSO end to end against scripts/mock-oidc.js. Server must run with KAGAZ_AUTH_OIDC_* pointing at the mock,
# ALLOWED_EMAIL_DOMAINS=example.com and ADMIN_EMAILS=boss@example.com.
set -euo pipefail
BASE="${1:-http://localhost:8080}"; MOCK="${2:-http://localhost:9000}"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
fail() { echo "FAIL: $*"; exit 1; }
sso_login() { : > "$T/jar"; curl -s -L -o /dev/null -c "$T/jar" -b "$T/jar" "$BASE/api/v1/auth/sso/login"; }
me() { curl -s -o "$T/body" -w '%{http_code}' -b "$T/jar" "$BASE/api/v1/auth/me"; }
mock() { curl -s "$MOCK/_set?email=$1&verified=${2:-true}" >/dev/null; }

curl -s "$BASE/api/v1/auth/status" | grep -q '"sso":true' || fail "status does not advertise SSO"
echo "ok  status advertises SSO"

mock alice@example.com; sso_login
[ "$(me)" = 200 ] || fail "alice not signed in"
grep -q '"username":"alice@example.com"' "$T/body" && grep -q '"role":"USER"' "$T/body" || { cat "$T/body"; fail "alice should be a USER"; }
echo "ok  new SSO user created as USER and signed in"

mock boss@example.com; sso_login
[ "$(me)" = 200 ] && grep -q '"role":"ADMIN"' "$T/body" || fail "boss should be ADMIN"
echo "ok  admin-emails grants ADMIN"

mock alice@example.com; sso_login
[ "$(me)" = 200 ] && grep -q '"username":"alice@example.com"' "$T/body" || fail "second login should reuse alice"
echo "ok  repeat login reuses the account"

mock mallory@other.com; sso_login
[ "$(me)" = 401 ] || fail "domain outside the allow-list was admitted"
echo "ok  email domain allow-list enforced"

mock carol@example.com false; sso_login
[ "$(me)" = 401 ] || fail "unverified email was admitted"
echo "ok  unverified email refused"

: > "$T/jar"
loc=$(curl -s -o /dev/null -w '%{redirect_url}' -c "$T/jar" -b "$T/jar" "$BASE/api/v1/auth/sso/callback?code=forged&state=forged")
echo "$loc" | grep -q 'sso=failed' || fail "forged callback not rejected ($loc)"
[ "$(me)" = 401 ] || fail "forged callback created a session"
echo "ok  forged callback rejected"

: > "$T/jar"; mock alice@example.com
curl -s -o /dev/null -c "$T/jar" -b "$T/jar" "$BASE/api/v1/auth/sso/login"   # starts a flow, state stored in session
loc=$(curl -s -o /dev/null -w '%{redirect_url}' -c "$T/jar" -b "$T/jar" "$BASE/api/v1/auth/sso/login")
curl -s -o /dev/null -c "$T/jar" -b "$T/jar" "$loc"   # approve at the provider but never follow back
echo "ALL SSO CHECKS PASSED"
