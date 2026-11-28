(function () {
  var API = '/api/v1/auth/admin/features';
  var toolRows = document.getElementById('tools'), groupRows = document.getElementById('groups');
  var msg = document.getElementById('msg'), q = document.getElementById('q'), f = document.getElementById('f');
  var data = {tools: [], groups: []};

  function call(method, body) {
    var opts = {method: method, credentials: 'same-origin', headers: {}};
    if (body) { opts.headers['Content-Type'] = 'application/json'; opts.body = JSON.stringify(body); }
    return fetch(API, opts).then(function (r) {
      if (r.status === 401) { location.href = '/login.html?next=/admin-features.html'; return null; }
      if (r.status === 409) { throw new Error('This needs a program or module that is not installed on the server.'); }
      if (r.status === 403) { throw new Error('Administrator access is required.'); }
      if (!r.ok) { throw new Error('Request failed (' + r.status + ')'); }
      return r.json();
    });
  }

  function status(item, kind) {
    if (item.enabled) { return 'Enabled'; }
    if (item.reason === 'DEPENDENCY') { return 'Disabled: missing dependency'; }
    return item.switchedOff ? 'Disabled by administrator' : 'Disabled by configuration';
  }

  function button(item, kind) {
    var b = document.createElement('button');
    b.type = 'button';
    b.textContent = item.enabled ? 'Turn off' : 'Turn on';
    b.disabled = !item.enabled && item.reason === 'DEPENDENCY';
    b.addEventListener('click', function () {
      msg.textContent = '';
      call('PUT', {kind: kind, name: item.name, enabled: !item.enabled}).then(load)
        .catch(function (e) { msg.textContent = e.message; });
    });
    return b;
  }

  function cell(tr, content) {
    var td = document.createElement('td');
    if (typeof content === 'string') { td.textContent = content; } else { td.appendChild(content); }
    tr.appendChild(td);
  }

  function visible(item) {
    var text = q.value.trim().toLowerCase();
    if (text && item.name.toLowerCase().indexOf(text) < 0) { return false; }
    if (f.value === 'on') { return item.enabled; }
    if (f.value === 'off') { return !item.enabled; }
    if (f.value === 'mine') { return item.switchedOff; }
    return true;
  }

  function render() {
    groupRows.textContent = '';
    data.groups.filter(visible).forEach(function (g) {
      var tr = document.createElement('tr');
      cell(tr, g.name + (g.dependency ? ' (program)' : ''));
      cell(tr, String(g.tools));
      cell(tr, status(g, 'group'));
      cell(tr, button(g, 'group'));
      groupRows.appendChild(tr);
    });
    toolRows.textContent = '';
    data.tools.filter(visible).forEach(function (t) {
      var tr = document.createElement('tr');
      cell(tr, t.name);
      cell(tr, status(t, 'endpoint'));
      cell(tr, button(t, 'endpoint'));
      toolRows.appendChild(tr);
    });
  }

  function load() {
    return call('GET').then(function (d) { if (d) { data = d; render(); } })
      .catch(function (e) { msg.textContent = e.message; });
  }

  document.getElementById('reset').addEventListener('click', function () {
    if (!confirm('Switch every tool back to its default?')) { return; }
    call('DELETE').then(load).catch(function (e) { msg.textContent = e.message; });
  });
  q.addEventListener('input', render);
  f.addEventListener('change', render);
  load();
})();
