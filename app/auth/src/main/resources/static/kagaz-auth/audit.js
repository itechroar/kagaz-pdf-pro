(function () {
  var rows = document.getElementById('rows'), msg = document.getElementById('msg'), lim = document.getElementById('lim');
  function load() {
    fetch('/api/v1/auth/admin/audit?limit=' + encodeURIComponent(lim.value), {credentials: 'same-origin'})
      .then(function (r) {
        if (r.status === 401) { location.href = '/login.html?next=/admin-audit.html'; return null; }
        if (!r.ok) { throw new Error('Request failed (' + r.status + ')'); }
        return r.json();
      })
      .then(function (list) {
        if (!list) { return; }
        rows.textContent = '';
        list.forEach(function (e) {
          var tr = document.createElement('tr');
          [e.time.replace('T', ' ').replace(/\.\d+Z$/, ''), e.action, e.actor, e.target, e.ip, e.success ? 'ok' : 'FAILED']
            .forEach(function (v) { var td = document.createElement('td'); td.textContent = v; tr.appendChild(td); });
          rows.appendChild(tr);
        });
      })
      .catch(function (e) { msg.textContent = e.message; });
  }
  lim.addEventListener('change', load); load();
})();
