#!/usr/bin/env bash
# Checks the split between the user site (:8080) and the administrator site (:8081), sign-up,
# and that the accessibility and form-detection tools answer. Needs the server running with
# KAGAZ_ADMIN_PORT=8081, KAGAZ_AUTH_SIGNUP_ENABLED=true and KAGAZ_AUTH_ADMIN_PASSWORD=smoke-admin-pass-1.
set -euo pipefail
U=http://localhost:8080
A=http://localhost:8081
PW=smoke-admin-pass-1
TMP=$(mktemp -d)
fail() { echo "FAIL: $*"; exit 1; }
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
json() { python3 -c "import sys,json; d=json.load(sys.stdin); print(eval(sys.argv[1]))" "$1"; }

[ "$(code $U/login.html)" = 200 ] || fail "user site sign-in page"
[ "$(code $A/login.html)" = 200 ] || fail "admin site sign-in page"

loc=$(curl -s -H 'Accept: text/html' -o /dev/null -w '%{http_code} %{redirect_url}' $U/)
case "$loc" in 302*login.html*) ;; *) fail "unauthenticated visit must redirect to sign-in, got: $loc";; esac

[ "$(curl -s $U/api/v1/auth/status | json "d['signup'] and not d['adminSite']")" = True ] || fail "user site status"
[ "$(curl -s $A/api/v1/auth/status | json "d['adminSite'] and not d['signup']")" = True ] || fail "admin site status"

curl -s -c $TMP/admin -H 'Content-Type: application/json' -d "{\"username\":\"admin\",\"password\":\"$PW\"}" $A/api/v1/auth/login >/dev/null
[ "$(code -b $TMP/admin $A/api/v1/auth/admin/activity)" = 200 ] || fail "admin activity on admin site"
[ "$(code -b $TMP/admin $U/api/v1/auth/admin/activity)" = 404 ] || fail "admin API must be hidden on the user site"
[ "$(code -b $TMP/admin $U/admin-dashboard.html)" = 404 ] || fail "dashboard must be hidden on the user site"

[ "$(code -H 'Content-Type: application/json' -d '{"username":"smokeuser","password":"smoke-user-pass-1"}' $U/api/v1/auth/signup)" = 201 ] || fail "sign-up"
[ "$(code -H 'Content-Type: application/json' -d '{"username":"smokeuser","password":"smoke-user-pass-1"}' $A/api/v1/auth/signup)" = 404 ] || fail "no sign-up on the admin site"
curl -s -c $TMP/user -H 'Content-Type: application/json' -d '{"username":"smokeuser","password":"smoke-user-pass-1"}' $U/api/v1/auth/login >/dev/null
[ "$(code -b $TMP/user -H "Accept: text/html" $U/)" = 200 ] || fail "user reaches the tools"
[ "$(code -b $TMP/user -H "Accept: text/html" $A/)" = 403 ] || fail "user must be refused on the admin site"

sleep 1
[ "$(curl -s -b $TMP/admin $A/api/v1/auth/admin/activity | json "d['totals']['signups']>=1 and d['totals']['loginsOk']>=2")" = True ] || fail "dashboard did not record the sign-up and sign-ins"

python3 - "$TMP/t.pdf" <<'PY'
import sys
stream = b"BT /F1 12 Tf 50 700 Td (Name: ______________________) Tj ET"
objs = [
 b"<< /Type /Catalog /Pages 2 0 R >>",
 b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
 b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
 b"<< /Length %d >>\nstream\n" % len(stream) + stream + b"\nendstream",
 b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
]
out = b"%PDF-1.4\n"; offs = []
for i, o in enumerate(objs, 1):
    offs.append(len(out)); out += b"%d 0 obj\n" % i + o + b"\nendobj\n"
x = len(out)
out += b"xref\n0 %d\n0000000000 65535 f \n" % (len(objs) + 1)
for o in offs: out += b"%010d 00000 n \n" % o
out += b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF" % (len(objs) + 1, x)
open(sys.argv[1], "wb").write(out)
PY

curl -s -b $TMP/admin -F fileInput=@$TMP/t.pdf -F profile=ua1 $A/api/v1/security/accessibility-report > $TMP/acc.json
[ "$(json "d['profile']=='PDF/UA-1' and d['tagged'] is False and len(d['issues'])>=2" < $TMP/acc.json)" = True ] || { cat $TMP/acc.json; fail "accessibility report"; }

curl -s -b $TMP/admin -D $TMP/h -o $TMP/out.pdf -F file=@$TMP/t.pdf -F applyToPdf=true $A/api/v1/form/form-detection/detect
grep -i '^x-kagaz-detected-fields:' $TMP/h | grep -q '"total":1' || { cat $TMP/h; fail "form detection summary"; }
head -c 4 $TMP/out.pdf | grep -q '%PDF' || fail "form detection output is not a PDF"

echo "sites, sign-up, dashboard, accessibility and form detection OK"
