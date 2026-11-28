'use strict';
// Brings in the server launcher and helper-tool logic from the user desktop app, so the two apps
// cannot drift. Copies are build products and are git-ignored.
const fs = require('node:fs');
const path = require('node:path');

const from = path.join(__dirname, '..', '..', 'desktop');
const to = path.join(__dirname, '..');
for (const file of ['server.js', 'tools.js']) {
  fs.copyFileSync(path.join(from, 'src', file), path.join(to, 'src', file));
}
fs.cpSync(path.join(from, 'shims'), path.join(to, 'shims'), { recursive: true });
