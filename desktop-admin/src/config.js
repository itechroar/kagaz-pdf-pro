'use strict';
const fs = require('node:fs');
const path = require('node:path');

/** Sections of the console. The last one is the ordinary user app, so an admin keeps every user feature. */
const TABS = [
  { id: 'dashboard', label: 'Dashboard', path: '/admin-dashboard.html' },
  { id: 'users', label: 'Users', path: '/admin-users.html' },
  { id: 'tools', label: 'Tools', path: '/admin-features.html' },
  { id: 'audit', label: 'Audit log', path: '/admin-audit.html' },
  { id: 'developer', label: 'Developer', path: '/developer.html' },
  { id: 'app', label: 'User app', path: '/' },
];

/**
 * Reduces what a person typed to a server origin such as `https://pdf.example.com:8443`, or null
 * when it cannot be one. A bare `host:port` means http; credentials, paths and queries are dropped.
 */
function normalizeServerUrl(input) {
  if (typeof input !== 'string') return null;
  let text = input.trim();
  if (!text) return null;
  if (!/^[a-z][a-z0-9+.-]*:\/\//i.test(text)) text = `http://${text}`;
  let url;
  try {
    url = new URL(text);
  } catch {
    return null;
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') return null;
  if (url.username || url.password || !url.hostname) return null;
  return url.origin;
}

/** True when sign-in details would cross a network in the clear. */
function isCleartextRemote(origin) {
  const url = new URL(origin);
  return url.protocol === 'http:' && !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
}

function parseConfig(value) {
  if (!value || typeof value !== 'object') return null;
  if (value.mode === 'local') return { mode: 'local' };
  if (value.mode === 'remote') {
    const url = normalizeServerUrl(value.url);
    return url ? { mode: 'remote', url } : null;
  }
  return null;
}

function loadConfig(file) {
  try {
    return parseConfig(JSON.parse(fs.readFileSync(file, 'utf8')));
  } catch {
    return null;
  }
}

function saveConfig(file, config) {
  const clean = parseConfig(config);
  if (!clean) throw new Error('Invalid server settings');
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(clean));
  return clean;
}

module.exports = { TABS, normalizeServerUrl, isCleartextRemote, parseConfig, loadConfig, saveConfig };
