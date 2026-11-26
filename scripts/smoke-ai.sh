#!/usr/bin/env bash
# Document assistant + daily quota against scripts/mock-llm.js. Server runs with quota 8, chunk size 1000.
set -euo pipefail
BASE="${1:-http://localhost:8080}"
ADMIN_PW="${KAGAZ_AUTH_ADMIN_PASSWORD:?}"
J='Content-Type: application/json'
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
fail() { echo "FAIL: $*"; exit 1; }
code() { curl -s -o "$T/body" -w '%{http_code}' "$@"; }
expect() { local want="$1"; shift; local got; got=$(code "$@"); [ "$got" = "$want" ] || { cat "$T/body"; echo; fail "expected $want got $got: $*"; }; echo "ok  $want  ${*: -1}"; }

python3 - "$T" <<'PY'
import sys
def pdf(path, text):
    stream = (b"BT /F1 12 Tf 72 700 Td (%s) Tj ET" % text.encode()) if text else b""
    objs=[b"<< /Type /Catalog /Pages 2 0 R >>", b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
    b"<< /Length %d >>\nstream\n%s\nendstream" % (len(stream), stream),
    b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"]
    out=b"%PDF-1.4\n"; offs=[]
    for i,o in enumerate(objs,1):
        offs.append(len(out)); out+=b"%d 0 obj\n%s\nendobj\n"%(i,o)
    x=len(out); out+=b"xref\n0 %d\n0000000000 65535 f \n"%(len(objs)+1)
    for o in offs: out+=b"%010d 00000 n \n"%o
    out+=b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n"%(len(objs)+1,x)
    open(path,"wb").write(out)
pdf(sys.argv[1]+"/text.pdf","Quarterly revenue was 42 million")
pdf(sys.argv[1]+"/blank.pdf",None)
long="; ".join("Section %d states that item number %d was reviewed and approved by the committee" % (i,i) for i in range(1,80))
pdf_lines=long
def multi(path):
    words=pdf_lines.split("; ")
    stream=b"BT /F1 9 Tf 20 780 Td 11 TL\n"
    for w in words: stream+=b"(%s) Tj T*\n" % w.encode()
    stream+=b"ET"
    objs=[b"<< /Type /Catalog /Pages 2 0 R >>", b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 700 800] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
    b"<< /Length %d >>\nstream\n%s\nendstream" % (len(stream), stream),
    b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"]
    out=b"%PDF-1.4\n"; offs=[]
    for i,o in enumerate(objs,1):
        offs.append(len(out)); out+=b"%d 0 obj\n%s\nendobj\n"%(i,o)
    x=len(out); out+=b"xref\n0 %d\n0000000000 65535 f \n"%(len(objs)+1)
    for o in offs: out+=b"%010d 00000 n \n"%o
    out+=b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n"%(len(objs)+1,x)
    open(path,"wb").write(out)
multi(sys.argv[1]+"/long.pdf")
try:
    from PIL import Image, ImageDraw, ImageFont
    img=Image.new("RGB",(1200,400),"white"); d=ImageDraw.Draw(img)
    d.text((40,150),"QUARTERLY REVENUE REPORT",fill="black",font=ImageFont.load_default(size=70))
    img.save(sys.argv[1]+"/scan.pdf")
except Exception as e:
    print("no PIL, skipping scan fixture:", e)
PY

echo "== assistant needs a login"
expect 401 -F fileInput=@"$T/text.pdf" "$BASE/api/v1/ai/summarize"
expect 401 "$BASE/api/v1/ai/status"

expect 200 -c "$T/admin.jar" -X POST -H "$J" -d "{\"username\":\"admin\",\"password\":\"$ADMIN_PW\"}" "$BASE/api/v1/auth/login"
expect 201 -b "$T/admin.jar" -X POST -H "$J" -d '{"username":"dave","password":"dave-pass-123","role":"USER","mustChangePassword":false}' "$BASE/api/v1/auth/admin/users"
expect 200 -b "$T/admin.jar" -X POST "$BASE/api/v1/auth/admin/users/dave/apikey"
KEY=$(sed -E 's/.*"apiKey":"([^"]+)".*/\1/' "$T/body")

echo "== summarize and ask"
expect 200 -b "$T/admin.jar" "$BASE/api/v1/ai/status"
grep -q '"ready":true' "$T/body" || { cat "$T/body"; fail "assistant should be ready"; }
expect 200 -H "X-API-KEY: $KEY" -F fileInput=@"$T/text.pdf" "$BASE/api/v1/ai/summarize"
grep -q 'MOCK SUMMARY of: Quarterly revenue was 42 million' "$T/body" || { cat "$T/body"; fail "summary did not carry the document text"; }
expect 200 -H "X-API-KEY: $KEY" -F fileInput=@"$T/text.pdf" -F "question=What was revenue?" "$BASE/api/v1/ai/ask"
grep -q 'ANSWER to \\"What was revenue?\\"' "$T/body" || { cat "$T/body"; fail "answer did not carry the question"; }

echo "== a long document is read in parts, nothing is cut off"
expect 200 -H "X-API-KEY: $KEY" -F fileInput=@"$T/long.pdf" "$BASE/api/v1/ai/summarize"
grep -Eq '"parts":([2-9]|[1-9][0-9])' "$T/body" || { head -c 600 "$T/body"; fail "long document should be split into parts"; }
grep -q 'Section 79' "$T/body" || { head -c 600 "$T/body"; fail "last section should reach the model"; }

echo "== a scan is read with OCR"
if [ -f "$T/scan.pdf" ]; then
  expect 200 -H "X-API-KEY: $KEY" -F fileInput=@"$T/scan.pdf" "$BASE/api/v1/ai/summarize"
  grep -q '"usedOcr":true' "$T/body" && grep -qi 'revenue' "$T/body" || { cat "$T/body"; fail "OCR text did not reach the model"; }
fi
expect 422 -H "X-API-KEY: $KEY" -F fileInput=@"$T/blank.pdf" "$BASE/api/v1/ai/summarize"
grep -q 'no_text' "$T/body" || fail "blank PDF should say no_text"

echo "== bad input never gives a server error"
echo "not a pdf" > "$T/junk.pdf"
expect 400 -H "X-API-KEY: $KEY" -F fileInput=@"$T/junk.pdf" "$BASE/api/v1/ai/summarize"
expect 400 -b "$T/admin.jar" -X POST -H "$J" -d '{bad json' "$BASE/api/v1/auth/login"
expect 400 -b "$T/admin.jar" -F fileInput=@"$T/text.pdf" "$BASE/api/v1/ai/ask"

echo "== quota (limit 8)"
expect 200 -H "X-API-KEY: $KEY" "$BASE/api/v1/ai/status"
expect 200 -H "X-API-KEY: $KEY" -F fileInput=@"$T/text.pdf" "$BASE/api/v1/ai/summarize"
expect 429 -H "X-API-KEY: $KEY" -F fileInput=@"$T/text.pdf" "$BASE/api/v1/ai/summarize"
expect 200 -H "X-API-KEY: $KEY" "$BASE/api/v1/auth/usage"
grep -q '"dailyLimit":8' "$T/body" || fail "usage should report the limit"
for i in 1 2 3 4 5 6 7; do expect 200 -b "$T/admin.jar" -F fileInput=@"$T/text.pdf" "$BASE/api/v1/ai/summarize" >/dev/null; done
echo "ok  administrators are not limited"
echo "ALL AI + QUOTA CHECKS PASSED"
