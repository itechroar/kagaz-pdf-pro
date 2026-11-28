(function () {
  var f = document.getElementById('reg'), msg = document.getElementById('msg');
  function post(url, body) {
    return fetch(url, {method: 'POST', credentials: 'same-origin', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)})
      .then(function (r) { return r.json().catch(function () { return {}; }).then(function (j) { return {ok: r.ok, body: j}; }); });
  }
  fetch('/api/v1/auth/status').then(function (r) { return r.json(); }).then(function (s) {
    if (!s.signup) { f.hidden = true; document.getElementById('closed').hidden = false; }
  }).catch(function () {});
  f.addEventListener('submit', function (e) {
    e.preventDefault(); msg.textContent = '';
    var u = document.getElementById('u').value, p = document.getElementById('p').value;
    if (p !== document.getElementById('p2').value) { msg.textContent = 'The passwords do not match.'; return; }
    post('/api/v1/auth/signup', {username: u, password: p}).then(function (r) {
      if (!r.ok) { msg.textContent = r.body.message || 'Could not create the account'; return; }
      post('/api/v1/auth/login', {username: u, password: p}).then(function (l) {
        location.href = l.ok ? '/profile.html?welcome=1' : '/login.html';
      });
    });
  });
})();
