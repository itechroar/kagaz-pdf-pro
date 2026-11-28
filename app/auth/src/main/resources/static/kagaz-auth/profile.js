(function () {
  var fmt = window.kagazFormat, msg = document.getElementById('msg');
  function stat(parent, label, value) {
    var d = document.createElement('div'); d.className = 'stat';
    fmt.text(d, 'b', String(value)); fmt.text(d, 'span', label); parent.appendChild(d);
  }
  function row(parent, label, value) { fmt.text(parent, 'dt', label); fmt.text(parent, 'dd', value); }
  function render(p) {
    document.getElementById('title').textContent = p.username;
    var stats = document.getElementById('stats');
    stat(stats, 'Tool runs today', p.usedToday + (p.dailyLimit ? ' / ' + p.dailyLimit : ''));
    stat(stats, 'Tool runs (logged)', p.toolRuns); stat(stats, 'Sign-ins', p.logins);
    var info = document.getElementById('info');
    row(info, 'Role', p.role); row(info, 'Status', p.enabled ? 'Active' : 'Disabled');
    row(info, 'Member since', fmt.when(p.createdAt)); row(info, 'Last sign-in', fmt.when(p.lastLogin));
    row(info, 'API key', p.hasApiKey ? 'Issued' : 'None');
    var tools = document.getElementById('tools');
    p.topTools.forEach(function (t) { var tr = document.createElement('tr'); fmt.text(tr, 'td', t.tool); fmt.text(tr, 'td', t.runs); tools.appendChild(tr); });
    if (!p.topTools.length) { var e = document.createElement('tr'); fmt.text(e, 'td', 'No tools used yet.'); tools.appendChild(e); }
    var recent = document.getElementById('recent');
    p.recent.forEach(function (r) {
      var tr = document.createElement('tr'); fmt.text(tr, 'td', fmt.when(r.time)); fmt.text(tr, 'td', r.action);
      fmt.text(tr, 'td', r.target); fmt.text(tr, 'td', r.success ? 'ok' : 'failed'); recent.appendChild(tr);
    });
    if (p.role === 'ADMIN') { document.getElementById('adm').hidden = false; }
  }
  if (new URLSearchParams(location.search).get('welcome')) { document.getElementById('welcome').hidden = false; }
  fetch('/api/v1/auth/profile', {credentials: 'same-origin'}).then(function (r) {
    if (r.status === 401) { window.kagazSession.expired(); return null; }
    return r.json();
  }).then(function (p) { if (p) { render(p); } }).catch(function (e) { msg.textContent = e.message; });
  document.getElementById('pw').addEventListener('submit', function (e) {
    e.preventDefault(); msg.textContent = '';
    fetch('/api/v1/auth/password', {method: 'POST', credentials: 'same-origin', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({currentPassword: document.getElementById('cur').value, newPassword: document.getElementById('new').value})})
      .then(function (r) { return r.json().catch(function () { return {}; }).then(function (j) {
        msg.textContent = r.ok ? 'Password updated.' : (j.message || 'Could not update the password'); if (r.ok) { e.target.reset(); } }); });
  });
  document.getElementById('out').addEventListener('click', function (e) {
    e.preventDefault();
    fetch('/api/v1/auth/logout', {method: 'POST', credentials: 'same-origin'}).finally(function () { location.href = '/login.html'; });
  });
})();
