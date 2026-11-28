'use strict';
const test = require('node:test');
const assert = require('node:assert');
const http = require('node:http');
const os = require('node:os');
const fs = require('node:fs');
const path = require('node:path');
const { freePort, javaBinary, buildArgs, waitForServer } = require('../src/server');

test('freePort returns a usable loopback port', async () => {
  const p = await freePort();
  assert.ok(p > 0 && p < 65536);
});

test('javaBinary prefers bundled JRE, falls back to PATH', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'kagaz-'));
  assert.strictEqual(javaBinary(dir), 'java');
  const exe = process.platform === 'win32' ? 'java.exe' : 'java';
  fs.mkdirSync(path.join(dir, 'jre', 'bin'), { recursive: true });
  fs.writeFileSync(path.join(dir, 'jre', 'bin', exe), '');
  assert.strictEqual(javaBinary(dir), path.join(dir, 'jre', 'bin', exe));
});

test('buildArgs binds to loopback on the chosen port', () => {
  const a = buildArgs({ jar: '/x/kagaz-pdf.jar', port: 4321, dataDir: '/d' });
  assert.ok(a.includes('-Dserver.port=4321'));
  assert.ok(a.includes('-Dserver.address=127.0.0.1'));
  assert.ok(a.some((x) => x.startsWith('-Dkagaz.base-path=/d')));
  assert.deepStrictEqual(a.slice(-2), ['-jar', '/x/kagaz-pdf.jar']);
});

test('waitForServer resolves once the server answers below 500', async () => {
  let hits = 0;
  const srv = http.createServer((req, res) => { res.statusCode = ++hits < 3 ? 503 : 200; res.end(); });
  await new Promise((r) => srv.listen(0, '127.0.0.1', r));
  await waitForServer(srv.address().port, 5000, 20);
  assert.ok(hits >= 3);
  srv.close();
});

test('waitForServer accepts a login-protected server answering 401', async () => {
  const srv = http.createServer((req, res) => { res.statusCode = 401; res.end(); });
  await new Promise((r) => srv.listen(0, '127.0.0.1', r));
  await waitForServer(srv.address().port, 2000, 20);
  srv.close();
});

test('buildArgs turns login off for the loopback-only desktop server', () => {
  assert.ok(buildArgs({ jar: '/x/k.jar', port: 1, dataDir: '/d' }).includes('-Dsecurity.enableLogin=false'));
});

test('buildArgs in phone mode listens on all interfaces with login and a password', () => {
  const a = buildArgs({ jar: '/x/k.jar', port: 8080, dataDir: '/d', lan: true, adminPassword: 'pw123' });
  assert.ok(a.includes('-Dserver.address=0.0.0.0'));
  assert.ok(a.includes('-Dsecurity.enableLogin=true'));
  assert.ok(a.includes('-Dkagaz.auth.admin-password=pw123'));
});

test('buildArgs can turn login on while staying on loopback', () => {
  const a = buildArgs({ jar: '/x/k.jar', port: 1, dataDir: '/d', login: true, adminPassword: 'pw' });
  assert.ok(a.includes('-Dserver.address=127.0.0.1'));
  assert.ok(a.includes('-Dsecurity.enableLogin=true'));
  assert.ok(a.includes('-Dkagaz.auth.admin-password=pw'));
});

test('freePort falls back when the preferred port is taken', async () => {
  const busy = http.createServer();
  await new Promise((r) => busy.listen(0, '127.0.0.1', r));
  const taken = busy.address().port;
  const p = await freePort(taken);
  assert.notStrictEqual(p, taken);
  busy.close();
});

test('waitForServer rejects on timeout', async () => {
  const p = await freePort();
  await assert.rejects(waitForServer(p, 300, 50), /did not start/);
});

test('logTail returns the end of the log and tolerates a missing file', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'kagaz-log-'));
  const file = path.join(dir, 'server.log');
  fs.writeFileSync(file, 'a'.repeat(2000) + 'END');
  const tail = require('../src/server').logTail(file, 10);
  assert.equal(tail.endsWith('END'), true);
  assert.equal(tail.length <= 10, true);
  assert.equal(require('../src/server').logTail(path.join(dir, 'missing.log')), '');
});
