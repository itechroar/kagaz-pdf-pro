'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { serverEnv, missingTools, extraPathDirs, installCommand } = require('../src/tools');

function toolDir(files) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'kagaz-tools-'));
  for (const f of files) fs.writeFileSync(path.join(dir, f), '');
  return dir;
}

test('missingTools lists tools that are not on PATH', () => {
  const dir = toolDir(['gs', 'qpdf']);
  const ids = missingTools({ PATH: dir }, 'linux').map((t) => t.id);
  assert.deepStrictEqual(ids, ['tesseract', 'ocrmypdf', 'unpaper']);
});

test('missingTools finds Windows executables by extension and skips unpaper', () => {
  const dir = toolDir(['gswin64c.exe', 'qpdf.exe', 'tesseract.exe', 'ocrmypdf.exe']);
  assert.deepStrictEqual(missingTools({ Path: dir }, 'win32'), []);
});

test('serverEnv appends the shims and Homebrew directories to PATH', () => {
  const env = serverEnv('darwin', { PATH: '/usr/bin' }, '/srv');
  assert.ok(env.PATH.startsWith('/usr/bin'));
  assert.ok(env.PATH.includes(path.join('/srv', 'shims')));
  assert.ok(env.PATH.includes('/opt/homebrew/bin'));
});

test('extraPathDirs on Linux only adds the shims directory', () => {
  assert.deepStrictEqual(extraPathDirs('linux', {}, '/srv'), [path.join('/srv', 'shims')]);
});

test('installCommand exists for every platform', () => {
  for (const p of ['win32', 'darwin', 'linux']) assert.ok(installCommand(p).length > 10);
});
