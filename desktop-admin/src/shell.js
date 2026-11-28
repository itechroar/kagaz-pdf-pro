(function () {
  var $ = function (id) { return document.getElementById(id); };
  var tabsEl = $('tabs');

  function show(part) {
    document.body.className = part === 'tabs' ? '' : 'full';
    $('busy').hidden = part !== 'busy';
    $('setup').hidden = part !== 'setup';
  }

  window.admin.onState(function (s) {
    if (s.screen === 'busy') { $('busyText').textContent = s.message; show('busy'); return; }
    if (s.screen === 'setup') {
      $('err').textContent = s.error || '';
      if (s.url) { $('url').value = s.url; $('remoteForm').hidden = false; }
      show('setup'); return;
    }
    tabsEl.textContent = '';
    s.tabs.forEach(function (t) {
      if (t.id === 'app') { var sep = document.createElement('span'); sep.className = 'sep'; tabsEl.appendChild(sep); }
      var b = document.createElement('button');
      b.textContent = t.label; b.className = t.id === s.active ? 'on' : '';
      b.addEventListener('click', function () { window.admin.select(t.id); });
      tabsEl.appendChild(b);
    });
    $('where').textContent = s.where;
    show('tabs');
  });

  $('local').addEventListener('click', function () {
    window.admin.setup({mode: 'local'}).then(function (r) { if (r && r.error) { $('err').textContent = r.error; } });
  });
  $('remote').addEventListener('click', function () { $('remoteForm').hidden = false; $('url').focus(); });
  $('go').addEventListener('click', function () {
    window.admin.setup({mode: 'remote', url: $('url').value}).then(function (r) { if (r && r.error) { $('err').textContent = r.error; } });
  });
  $('url').addEventListener('keydown', function (e) { if (e.key === 'Enter') { $('go').click(); } });
  $('reload').addEventListener('click', function () { window.admin.reload(); });
  $('change').addEventListener('click', function () { window.admin.changeServer(); });
  window.admin.ready();
})();
