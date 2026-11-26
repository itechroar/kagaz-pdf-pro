(function () {
  var msg = document.getElementById('msg'), key = document.getElementById('key');
  function api(method, url) {
    return fetch(url, {method: method, credentials: 'same-origin'}).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (j) {
        if (r.status === 401) { location.href = '/login.html?next=/developer.html'; }
        if (!r.ok) { throw new Error(j.message || 'Request failed'); } return j; }); });
  }
  function td(tr, t) { var c = document.createElement('td'); c.textContent = t; tr.appendChild(c); }
  function example(k) {
    document.getElementById('ex').textContent = 'curl -H "X-API-KEY: ' + (k || 'kpdf_…') + '" \\\n  -F fileInput=@in.pdf -F angle=90 \\\n  ' + location.origin + '/api/v1/general/rotate-pdf -o out.pdf';
  }
  function load() {
    api('GET', '/api/v1/auth/me').then(function (m) { document.getElementById('who').textContent = m.username; });
    api('GET', '/api/v1/auth/usage').then(function (u) {
      document.getElementById('used').textContent = u.usedToday;
      document.getElementById('lim').textContent = u.dailyLimit > 0 ? ' of ' + u.dailyLimit : ' (no limit)';
      var rows = document.getElementById('rows'); rows.textContent = '';
      u.recent.forEach(function (e) { var tr = document.createElement('tr');
        td(tr, e.time.replace('T', ' ').replace(/\.\d+Z$/, '')); td(tr, e.action.replace('api:', '')); td(tr, e.target); td(tr, e.success ? 'ok' : 'FAILED'); rows.appendChild(tr); });
    }).catch(function (e) { msg.textContent = e.message; });
  }
  document.getElementById('new').addEventListener('click', function () {
    msg.textContent = '';
    api('POST', '/api/v1/auth/apikey').then(function (j) { key.textContent = j.apiKey; example(j.apiKey); }, function (e) { msg.textContent = e.message; });
  });
  document.getElementById('rev').addEventListener('click', function () {
    msg.textContent = ''; api('DELETE', '/api/v1/auth/apikey').then(function () { key.textContent = 'Key revoked.'; example(); }, function (e) { msg.textContent = e.message; });
  });
  document.getElementById('out').addEventListener('click', function (e) {
    e.preventDefault(); api('POST', '/api/v1/auth/logout').then(function () { location.href = '/login.html'; });
  });
  example(); load();
})();
