(function () {
  var go = document.getElementById('go'), url = document.getElementById('url'), err = document.getElementById('err');
  function submit() {
    err.textContent = '';
    window.connectApi.submit(url.value).then(function (r) { if (r && r.error) { err.textContent = r.error; } });
  }
  go.addEventListener('click', submit);
  url.addEventListener('keydown', function (e) { if (e.key === 'Enter') { submit(); } });
})();
