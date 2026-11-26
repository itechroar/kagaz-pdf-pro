'use strict';
// Minimal OpenID Connect provider for testing SSO: discovery, authorize (auto-approves),
// token (checks PKCE and client secret). Test knobs: GET /_set?email=..&verified=true|false
const http = require('node:http');
const crypto = require('node:crypto');

const PORT = Number(process.env.PORT || 9000);
const ISSUER = process.env.ISSUER || `http://localhost:${PORT}`;
const CLIENT_ID = process.env.CLIENT_ID || 'kagaz-test';
const CLIENT_SECRET = process.env.CLIENT_SECRET || 'test-secret';
const codes = new Map();
let email = 'alice@example.com';
let verified = true;
const b64 = (o) => Buffer.from(typeof o === 'string' ? o : JSON.stringify(o)).toString('base64url');

http.createServer((req, res) => {
  const url = new URL(req.url, ISSUER);
  const send = (status, body, headers = {}) => { res.writeHead(status, { 'Content-Type': 'application/json', ...headers }); res.end(body ? JSON.stringify(body) : ''); };
  if (url.pathname === '/.well-known/openid-configuration') {
    return send(200, { issuer: ISSUER, authorization_endpoint: `${ISSUER}/authorize`, token_endpoint: `${ISSUER}/token` });
  }
  if (url.pathname === '/_set') {
    if (url.searchParams.get('email')) email = url.searchParams.get('email');
    if (url.searchParams.get('verified')) verified = url.searchParams.get('verified') === 'true';
    return send(200, { email, verified });
  }
  if (url.pathname === '/authorize') {
    const q = url.searchParams;
    if (q.get('client_id') !== CLIENT_ID || q.get('code_challenge_method') !== 'S256' || !q.get('state') || !q.get('nonce')) return send(400, { error: 'bad_request' });
    const code = crypto.randomBytes(16).toString('hex');
    codes.set(code, { nonce: q.get('nonce'), challenge: q.get('code_challenge'), redirect: q.get('redirect_uri'), email, verified });
    const back = new URL(q.get('redirect_uri'));
    back.searchParams.set('code', code);
    back.searchParams.set('state', q.get('state'));
    return send(302, null, { Location: back.toString() });
  }
  if (url.pathname === '/token' && req.method === 'POST') {
    let raw = '';
    req.on('data', (c) => (raw += c));
    req.on('end', () => {
      const f = new URLSearchParams(raw);
      const entry = codes.get(f.get('code'));
      codes.delete(f.get('code'));
      const challenge = crypto.createHash('sha256').update(f.get('code_verifier') || '').digest('base64url');
      if (!entry || f.get('client_secret') !== CLIENT_SECRET || f.get('client_id') !== CLIENT_ID ||
          challenge !== entry.challenge || f.get('redirect_uri') !== entry.redirect) return send(400, { error: 'invalid_grant' });
      const payload = { iss: ISSUER, aud: CLIENT_ID, exp: Math.floor(Date.now() / 1000) + 300, nonce: entry.nonce,
        sub: 'sub-' + entry.email, email: entry.email, email_verified: entry.verified, name: 'Test User' };
      return send(200, { access_token: 'unused', token_type: 'Bearer', id_token: `${b64({ alg: 'none' })}.${b64(payload)}.sig` });
    });
    return;
  }
  send(404, { error: 'not_found' });
}).listen(PORT, () => console.log(`mock oidc on ${ISSUER}`));
