'use strict';

/** Sections of the console. The last one is the ordinary user app, so an admin keeps every user feature. */
const TABS = [
  { id: 'dashboard', label: 'Dashboard', path: '/admin-dashboard.html' },
  { id: 'users', label: 'Users', path: '/admin-users.html' },
  { id: 'tools', label: 'Tools', path: '/admin-features.html' },
  { id: 'audit', label: 'Audit log', path: '/admin-audit.html' },
  { id: 'developer', label: 'Developer', path: '/developer.html' },
  { id: 'app', label: 'User app', path: '/' },
];

module.exports = { TABS };
