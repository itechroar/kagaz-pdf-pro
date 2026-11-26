(function () {
  var f = document.getElementById('f'), q = document.getElementById('q'), go = document.getElementById('go');
  var msg = document.getElementById('msg'), out = document.getElementById('out'), st = document.getElementById('status');
  function toLogin() { location.href = '/login.html?next=/ai.html'; }
  fetch('/api/v1/ai/status', {credentials: 'same-origin'}).then(function (r) {
    if (r.status === 401) { toLogin(); return null; } return r.json();
  }).then(function (s) {
    if (!s) { return; }
    if (!s.enabled) { st.textContent = 'The assistant is off. The administrator can turn it on by setting KAGAZ_AI_PROVIDER.'; go.disabled = true; }
    else if (!s.ready) { st.textContent = 'Not ready yet: ' + s.problem; go.disabled = true; }
    else { st.textContent = 'Ready (' + s.provider + ', ' + s.model + ').'; }
  }).catch(function () { st.textContent = 'Could not check the assistant status.'; });
  go.addEventListener('click', function () {
    msg.textContent = ''; out.textContent = '';
    if (!f.files.length) { msg.textContent = 'Choose a PDF first.'; return; }
    var fd = new FormData(); fd.append('fileInput', f.files[0]);
    var ask = q.value.trim(); if (ask) { fd.append('question', ask); }
    go.disabled = true; st.textContent = 'Working… long documents are read in parts.';
    fetch(ask ? '/api/v1/ai/ask' : '/api/v1/ai/summarize', {method: 'POST', credentials: 'same-origin', body: fd})
      .then(function (r) { return r.json().catch(function () { return {}; }).then(function (j) {
        if (r.status === 401) { toLogin(); return; }
        if (!r.ok) { throw new Error(j.message || 'Request failed'); }
        out.textContent = j.answer;
        st.textContent = 'Done' + (j.usedOcr ? ' (scan read with OCR)' : '') + (j.parts > 1 ? ', ' + j.parts + ' parts' : '') + '.'; }); })
      .catch(function (e) { msg.textContent = e.message; st.textContent = ''; })
      .then(function () { go.disabled = false; });
  });
})();
