(function () {
  var fmt = window.kagazFormat, msg = document.getElementById('msg');
  var name = new URLSearchParams(location.search).get('u');
  var base = '/api/v1/auth/admin/users/' + encodeURIComponent(name || '');
  function api(method, url, body) {
    return fetch(url, {method: method, credentials: 'same-origin', headers: body ? {'Content-Type': 'application/json'} : {},
      body: body ? JSON.stringify(body) : undefined}).then(function (r) {
      if (r.status === 401) { window.kagazSession.expired(); }
      return r.json().catch(function () { return {}; }).then(function (j) { if (!r.ok) { throw new Error(j.message || 'Request failed'); } return j; }); });
  }
  function fail(e) { msg.textContent = e.message; }
  function clear(id) { var e = document.getElementById(id); e.textContent = ''; return e; }
  function stat(parent, label, value) {
    var d = document.createElement('div'); d.className = 'stat'; fmt.text(d, 'b', String(value)); fmt.text(d, 'span', label); parent.appendChild(d);
  }
  function row(parent, label, value) { fmt.text(parent, 'dt', label); fmt.text(parent, 'dd', value); }
  function action(parent, label, fn) {
    var b = document.createElement('button'); b.type = 'button'; b.className = 'sec'; b.textContent = label;
    b.addEventListener('click', function () { msg.textContent = ''; fn(); }); parent.appendChild(b);
  }
  function render(p) {
    document.getElementById('title').textContent = p.username;
    var stats = clear('stats');
    stat(stats, 'Sign-ins', p.logins); stat(stats, 'Failed sign-ins', p.failedLogins);
    stat(stats, 'Tool runs', p.toolRuns); stat(stats, 'Failed runs', p.toolFailures);
    stat(stats, 'Runs today', p.usedToday + (p.dailyLimit ? ' / ' + p.dailyLimit : ''));
    var info = clear('info');
    row(info, 'Role', p.role); row(info, 'Status', p.enabled ? 'Active' : 'Disabled');
    row(info, 'Registered', fmt.when(p.createdAt)); row(info, 'Last sign-in', fmt.when(p.lastLogin));
    row(info, 'Last activity', fmt.when(p.lastActive)); row(info, 'API key', p.hasApiKey ? 'Issued' : 'None');
    row(info, 'Must change password', p.mustChangePassword ? 'Yes' : 'No');
    row(info, 'Addresses seen', p.addresses.length ? p.addresses.join(', ') : '—');
    var act = clear('actions');
    action(act, p.enabled ? 'Disable' : 'Enable', function () { api('PATCH', base, {enabled: !p.enabled}).then(load, fail); });
    action(act, p.role === 'ADMIN' ? 'Make user' : 'Make admin', function () { api('PATCH', base, {role: p.role === 'ADMIN' ? 'USER' : 'ADMIN'}).then(load, fail); });
    action(act, 'Reset password', function () {
      var v = prompt('New temporary password (min 8 characters)'); if (!v) { return; }
      api('POST', base + '/password', {newPassword: v}).then(function () { msg.textContent = 'Password reset; the user must change it at next sign-in.'; }, fail); });
    action(act, 'New API key', function () { api('POST', base + '/apikey').then(function (j) { prompt('Copy this key now; it is not shown again:', j.apiKey); load(); }, fail); });
    action(act, 'Delete', function () { if (confirm('Delete ' + p.username + '?')) { api('DELETE', base).then(function () { location.href = '/admin-users.html'; }, fail); } });
    var tools = clear('tools');
    p.topTools.forEach(function (t) { var tr = document.createElement('tr'); fmt.text(tr, 'td', t.tool); fmt.text(tr, 'td', t.runs); tools.appendChild(tr); });
    var recent = clear('recent');
    p.recent.forEach(function (r) {
      var tr = document.createElement('tr');
      [fmt.when(r.time), r.action, r.actor, r.target, r.ip, r.success ? 'ok' : 'failed'].forEach(function (v) { fmt.text(tr, 'td', v); });
      recent.appendChild(tr);
    });
  }
  function load() { return api('GET', base + '/profile').then(render, fail); }
  if (!name) { msg.textContent = 'No user selected.'; } else { load(); }
})();
