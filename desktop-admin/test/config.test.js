'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { TABS, normalizeServerUrl, isCleartextRemote, loadConfig, saveConfig } = require('../src/config');

test('normalizeServerUrl accepts hosts, ports and schemes and keeps only the origin', () => {
  assert.strictEqual(normalizeServerUrl('192.168.1.20:8080'), 'http://192.168.1.20:8080');
  assert.strictEqual(normalizeServerUrl('  https://pdf.example.com/some/path?x=1 '), 'https://pdf.example.com');
  assert.strictEqual(normalizeServerUrl('localhost:8080/'), 'http://localhost:8080');
});

test('normalizeServerUrl refuses what cannot be a server', () => {
  for (const bad of ['', '   ', 'ftp://x.example', 'file:///etc/passwd', 'javascript:alert(1)', 'http://user:pw@host', null, 42]) {
    assert.strictEqual(normalizeServerUrl(bad), null, String(bad));
  }
});

test('only plain http to a non-local host counts as cleartext', () => {
  assert.strictEqual(isCleartextRemote('http://192.168.1.20:8080'), true);
  assert.strictEqual(isCleartextRemote('https://pdf.example.com'), false);
  assert.strictEqual(isCleartextRemote('http://127.0.0.1:8080'), false);
  assert.strictEqual(isCleartextRemote('http://localhost:8080'), false);
});

test('config round-trips and rejects junk', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'kagaz-admin-'));
  const file = path.join(dir, 'nested', 'server.json');
  assert.strictEqual(loadConfig(file), null);
  assert.deepStrictEqual(saveConfig(file, { mode: 'remote', url: 'pdf.example.com:9000' }), { mode: 'remote', url: 'http://pdf.example.com:9000' });
  assert.deepStrictEqual(loadConfig(file), { mode: 'remote', url: 'http://pdf.example.com:9000' });
  assert.deepStrictEqual(saveConfig(file, { mode: 'local', url: 'ignored' }), { mode: 'local' });
  assert.throws(() => saveConfig(file, { mode: 'remote', url: 'ftp://x' }));
  fs.writeFileSync(file, '{not json');
  assert.strictEqual(loadConfig(file), null);
});

test('the console has the admin sections plus the full user app', () => {
  const ids = TABS.map((t) => t.id);
  assert.deepStrictEqual(ids, ['dashboard', 'users', 'tools', 'audit', 'developer', 'app']);
  assert.ok(TABS.every((t) => t.path.startsWith('/')));
  assert.strictEqual(TABS.find((t) => t.id === 'app').path, '/');
});
