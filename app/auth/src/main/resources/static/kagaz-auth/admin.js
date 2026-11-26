(function () {
  var rows = document.getElementById('rows'), msg = document.getElementById('msg');
  function api(method, url, body) {
    return fetch(url, {method: method, credentials: 'same-origin',
      headers: body ? {'Content-Type': 'application/json'} : {}, body: body ? JSON.stringify(body) : undefined})
      .then(function (r) { return r.json().catch(function () { return {}; }).then(function (j) {
        if (r.status === 401) { location.href = '/login.html?next=' + encodeURIComponent(location.pathname); }
        if (!r.ok) { throw new Error(j.message || 'Request failed'); } return j; }); });
  }
  function fail(e) { msg.textContent = e.message; }
  function btn(label, fn) {
    var b = document.createElement('button'); b.className = 'sec'; b.type = 'button';
    b.textContent = label; b.addEventListener('click', function () { msg.textContent = ''; fn(); }); return b;
  }
  function cell(tr, text) { var td = document.createElement('td'); td.textContent = text; tr.appendChild(td); return td; }
  var base = '/api/v1/auth/admin/users/';
  function render(list) {
    rows.textContent = '';
    list.forEach(function (u) {
      var tr = document.createElement('tr'), name = encodeURIComponent(u.username);
      cell(tr, u.username); cell(tr, u.role); cell(tr, u.enabled ? 'active' : 'disabled');
      cell(tr, u.hasApiKey ? 'set' : '—');
      var act = document.createElement('td');
      act.appendChild(btn(u.enabled ? 'Disable' : 'Enable', function () {
        api('PATCH', base + name, {enabled: !u.enabled}).then(load, fail); }));
      act.appendChild(btn(u.role === 'ADMIN' ? 'Make user' : 'Make admin', function () {
        api('PATCH', base + name, {role: u.role === 'ADMIN' ? 'USER' : 'ADMIN'}).then(load, fail); }));
      act.appendChild(btn('Reset password', function () {
        var p = prompt('New temporary password (min 8 characters)'); if (!p) { return; }
        api('POST', base + name + '/password', {newPassword: p}).then(function () { msg.textContent = 'Password reset; user must change it at next login.'; }, fail); }));
      act.appendChild(btn('New API key', function () {
        api('POST', base + name + '/apikey').then(function (j) { prompt('Copy this key now; it is not shown again:', j.apiKey); load(); }, fail); }));
      act.appendChild(btn('Delete', function () {
        if (confirm('Delete ' + u.username + '?')) { api('DELETE', base + name).then(load, fail); } }));
      tr.appendChild(act); rows.appendChild(tr);
    });
  }
  function load() { api('GET', '/api/v1/auth/admin/users').then(render, fail); }
  document.getElementById('add').addEventListener('submit', function (e) {
    e.preventDefault(); msg.textContent = '';
    api('POST', '/api/v1/auth/admin/users', {username: document.getElementById('nu').value,
      password: document.getElementById('np').value, role: document.getElementById('nr').value, mustChangePassword: true})
      .then(function () { e.target.reset(); load(); }, fail);
  });
  document.getElementById('out').addEventListener('click', function (e) {
    e.preventDefault(); api('POST', '/api/v1/auth/logout').then(function () { location.href = '/login.html'; }, fail);
  });
  load();
})();
