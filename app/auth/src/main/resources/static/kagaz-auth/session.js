(function () {
  var here = location.pathname + location.search;
  function expired() { location.href = '/login.html?next=' + encodeURIComponent(here); }
  function ping() {
    fetch('/api/v1/auth/me', {credentials: 'same-origin'}).then(function (r) {
      if (r.status === 401) { expired(); }
    }).catch(function () {});
  }
  setInterval(ping, 4 * 60 * 1000);
  document.addEventListener('visibilitychange', function () { if (!document.hidden) { ping(); } });
  window.kagazSession = {expired: expired};
})();
