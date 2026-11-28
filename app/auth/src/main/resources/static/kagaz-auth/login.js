(function () {
  var f = document.getElementById('login'), msg = document.getElementById('msg');
  var cp = document.getElementById('cp'), go = document.getElementById('go');
  var step = 'login';
  var signup = false;
  var consoleHere = true;
  function post(url, body) {
    return fetch(url, {method: 'POST', credentials: 'same-origin',
      headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)})
      .then(function (r) { return r.json().catch(function () { return {}; }).then(function (j) { return {ok: r.ok, body: j}; }); });
  }
  function next() {
    var n = new URLSearchParams(location.search).get('next');
    return n && n.charAt(0) === '/' && n.charAt(1) !== '/' && n.charAt(1) !== '\\' ? n : '/';
  }
  function needChange() {
    step = 'change'; cp.hidden = false; go.textContent = 'Change password';
    document.getElementById('np').required = true; document.getElementById('np').focus();
    msg.textContent = 'You must set a new password before continuing.';
  }
  f.addEventListener('submit', function (e) {
    e.preventDefault(); msg.textContent = '';
    var u = document.getElementById('u').value, p = document.getElementById('p').value;
    if (signup) {
      post('/api/v1/auth/signup', {username: u, password: p}).then(function (r) {
        if (!r.ok) { msg.textContent = r.body.message || 'Could not create the account'; return; }
        post('/api/v1/auth/login', {username: u, password: p}).then(function (l) {
          if (l.ok) { location.href = next(); } else { toggle(false); msg.textContent = 'Account created. Sign in to continue.'; }
        });
      });
      return;
    }
    if (step === 'change') {
      post('/api/v1/auth/password', {currentPassword: p, newPassword: document.getElementById('np').value})
        .then(function (r) { if (r.ok) { location.href = next(); } else { msg.textContent = r.body.message || 'Failed'; } });
      return;
    }
    post('/api/v1/auth/login', {username: u, password: p}).then(function (r) {
      if (!r.ok) { msg.textContent = r.body.message || 'Sign-in failed'; return; }
      if (r.body.mustChangePassword) { needChange(); return; }
      var wanted = new URLSearchParams(location.search).get('next');
      location.href = wanted ? next() : (r.body.role === 'ADMIN' && consoleHere ? '/admin-dashboard.html' : '/');
    });
  });
  function toggle(on) {
    signup = on; go.textContent = on ? 'Create account' : 'Sign in';
    document.getElementById('tog').textContent = on ? 'I already have an account' : 'Create an account';
    document.getElementById('p').autocomplete = on ? 'new-password' : 'current-password';
    msg.textContent = '';
  }
  document.getElementById('tog').addEventListener('click', function () { toggle(!signup); });
  fetch('/api/v1/auth/status').then(function (r) { return r.json(); }).then(function (s) {
    consoleHere = s.adminConsole !== false;
    if (s.signup) { document.getElementById('su').hidden = false; }
    if (s.sso) { var a = document.getElementById('sso'); a.hidden = false; a.firstChild.textContent = 'Sign in with ' + (s.ssoLabel || 'SSO'); }
  }).catch(function () {});
  if (new URLSearchParams(location.search).get('sso')) { msg.textContent = 'Single sign-on failed or is not permitted for this account.'; }
  if (new URLSearchParams(location.search).get('change')) { msg.textContent = 'Sign in to change your password.'; }
})();
