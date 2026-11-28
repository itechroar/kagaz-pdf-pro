'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { TABS } = require('../src/tabs');

test('the console has the admin sections plus the full user app', () => {
  assert.deepStrictEqual(TABS.map((t) => t.id), ['dashboard', 'users', 'tools', 'audit', 'developer', 'app']);
  assert.ok(TABS.every((t) => t.path.startsWith('/')));
  assert.strictEqual(TABS.find((t) => t.id === 'app').path, '/');
});
