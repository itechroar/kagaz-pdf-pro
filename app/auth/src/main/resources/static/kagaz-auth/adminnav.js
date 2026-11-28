(function () {
  var slot = document.getElementById('adminnav');
  if (!slot) { return; }
  var links = [['Dashboard', '/admin-dashboard.html'], ['Users', '/admin-users.html'],
    ['Tools', '/admin-features.html'], ['Audit log', '/admin-audit.html'], ['Developer', '/developer.html']];
  var b = document.createElement('b'); b.textContent = 'Kagaz PDF Admin'; slot.appendChild(b);
  links.forEach(function (l) {
    var a = document.createElement('a'); a.href = l[1]; a.textContent = l[0];
    if (location.pathname === l[1] || (l[1] === '/admin-users.html' && location.pathname === '/admin-user.html')) { a.className = 'on'; }
    slot.appendChild(a);
  });
  var sp = document.createElement('span'); sp.className = 'sp'; slot.appendChild(sp);
  var app = document.createElement('a'); app.href = '/'; app.target = '_blank'; app.rel = 'noopener';
  app.textContent = 'Open PDF tools ↗'; slot.appendChild(app);
  var me = document.createElement('a'); me.href = '/profile.html'; me.textContent = 'My profile'; slot.appendChild(me);
  var out = document.createElement('a'); out.href = '#'; out.textContent = 'Sign out';
  out.addEventListener('click', function (e) {
    e.preventDefault();
    fetch('/api/v1/auth/logout', {method: 'POST', credentials: 'same-origin'}).finally(function () { location.href = '/login.html'; });
  });
  slot.appendChild(out);
})();
