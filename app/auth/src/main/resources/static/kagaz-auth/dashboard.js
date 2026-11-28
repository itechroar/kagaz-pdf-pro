(function () {
  var API = '/api/v1/auth/admin/activity';
  var $ = function (id) { return document.getElementById(id); };
  var state = {snap: null, feed: []};
  var tab = 'overview';
  var titles = {overview: 'Overview', live: 'Live activity', users: 'Users', tools: 'Tool usage'};

  function el(tag, text, cls) {
    var e = document.createElement(tag);
    if (text !== undefined && text !== null) { e.textContent = text; }
    if (cls) { e.className = cls; }
    return e;
  }
  function row(tbody, values) {
    var tr = document.createElement('tr');
    values.forEach(function (v) {
      var td = document.createElement('td');
      if (v instanceof Node) { td.appendChild(v); } else { td.textContent = v === null || v === undefined ? '' : String(v); }
      tr.appendChild(td);
    });
    tbody.appendChild(tr);
  }
  function clock(iso) { return iso ? new Date(iso).toLocaleTimeString() : ''; }
  function ago(iso) {
    if (!iso) { return 'never'; }
    var s = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
    if (s < 60) { return s + 's ago'; }
    if (s < 3600) { return Math.round(s / 60) + ' min ago'; }
    if (s < 86400) { return Math.round(s / 3600) + ' h ago'; }
    return Math.round(s / 86400) + ' d ago';
  }
  function describe(e) {
    var who = e.actor || 'anonymous', ok = e.success ? '' : ' (failed)';
    if (e.kind === 'login') { return who + (e.success ? ' signed in' : ' failed to sign in') + ' from ' + e.ip; }
    if (e.kind === 'signup') { return e.action === 'signup' ? (e.success ? who + ' created an account' : 'sign-up refused for ' + e.target) : who + ' created user ' + e.target; }
    if (e.kind === 'tool') { return who + ' ran ' + (e.tool || e.target) + ok; }
    return who + ': ' + e.action + (e.target ? ' → ' + e.target : '') + ok;
  }
  function feedItem(e, fresh) {
    var li = el('li', null, fresh ? 'new' : '');
    li.appendChild(el('time', clock(e.time)));
    li.appendChild(el('span', e.kind, 'tag ' + e.kind));
    li.appendChild(el('span', describe(e), e.success ? '' : 'fail'));
    return li;
  }

  function renderOverview() {
    var s = state.snap; if (!s) { return; }
    var t = s.totals, k = $('kpis'); k.textContent = '';
    [['Active now', t.activeNow, ''], ['Users', t.users, ''], ['Sign-ins', t.loginsOk, ''],
     ['Failed sign-ins', t.loginsFailed, t.loginsFailed ? 'bad' : ''], ['Sign-ups', t.signups, ''],
     ['Tool runs', t.toolCalls, ''], ['Failed runs', t.toolFailures, t.toolFailures ? 'bad' : ''],
     ['Watching live', s.liveViewers, '']].forEach(function (c) {
      var d = el('div', null, 'kpi ' + c[2]); d.appendChild(el('b', c[1])); d.appendChild(el('span', c[0])); k.appendChild(d);
    });
    var sp = $('spark'); sp.textContent = '';
    var max = Math.max.apply(null, s.perMinute.concat([1]));
    s.perMinute.forEach(function (n) { var i = el('i'); i.style.height = Math.round(n / max * 100) + '%'; i.title = n + ' runs'; sp.appendChild(i); });
    var tb = $('topTools'); tb.textContent = '';
    s.tools.slice(0, 8).forEach(function (x) { row(tb, [x.tool, x.calls + ' runs', x.users + ' user(s)']); });
    if (!s.tools.length) { row(tb, ['No tool runs in this window']); }
    var mf = $('miniFeed'); mf.textContent = '';
    state.feed.slice(0, 8).forEach(function (e) { mf.appendChild(feedItem(e, false)); });
  }
  function renderFeed() {
    var f = $('feed'); f.textContent = '';
    var kind = $('kind').value, who = $('who').value.trim().toLowerCase();
    state.feed.filter(function (e) {
      return (kind === 'all' || e.kind === kind) && (!who || (e.actor || '').toLowerCase().indexOf(who) >= 0);
    }).slice(0, 200).forEach(function (e) { f.appendChild(feedItem(e, false)); });
  }
  function renderUsers() {
    var s = state.snap; if (!s) { return; }
    var tb = $('userRows'); tb.textContent = '';
    s.users.forEach(function (u) {
      var name = el('span'); name.appendChild(el('span', '', 'dot' + (u.activeNow ? ' on' : ''))); name.appendChild(document.createTextNode(u.username));
      row(tb, [name, u.role, u.enabled ? 'active' : 'disabled', u.logins, u.failedLogins, u.toolCalls, u.lastTool || '', ago(u.lastActive)]);
    });
  }
  function renderTools() {
    var s = state.snap; if (!s) { return; }
    var tb = $('toolRows'); tb.textContent = '';
    s.tools.forEach(function (x) { row(tb, [x.tool, x.calls, x.failures, x.users]); });
    if (!s.tools.length) { row(tb, ['No tool runs in this window']); }
  }
  function render() { renderOverview(); renderFeed(); renderUsers(); renderTools(); }

  function load() {
    return fetch(API, {credentials: 'same-origin'}).then(function (r) {
      if (r.status === 401) { location.href = '/login.html?next=/admin-dashboard.html'; return null; }
      if (r.status === 403) { throw new Error('Administrator access is required.'); }
      if (!r.ok) { throw new Error('Request failed (' + r.status + ')'); }
      return r.json();
    }).then(function (s) {
      if (!s) { return; }
      state.snap = s; $('msg').textContent = '';
      if (!state.feed.length) { state.feed = s.recent.slice(); }
      render();
    }).catch(function (e) { $('msg').textContent = e.message; });
  }
  var timer = null;
  function soon() { clearTimeout(timer); timer = setTimeout(load, 400); }

  var source = null, poll = null;
  function live(on) {
    var c = $('conn'); c.textContent = on ? '● live' : 'reconnecting…'; c.className = 'pill ' + (on ? 'on' : 'off');
  }
  function connect() {
    if (!window.EventSource) { live(false); poll = poll || setInterval(load, 5000); return; }
    source = new EventSource(API + '/stream');
    source.onopen = function () { live(true); if (poll) { clearInterval(poll); poll = null; } load(); };
    source.addEventListener('activity', function (m) {
      var e; try { e = JSON.parse(m.data); } catch (x) { return; }
      state.feed.unshift(e); if (state.feed.length > 500) { state.feed.pop(); }
      if (tab === 'live') { var f = $('feed'); f.insertBefore(feedItem(e, true), f.firstChild); }
      soon();
    });
    source.onerror = function () { live(false); poll = poll || setInterval(load, 5000); };
  }

  $('tabs').addEventListener('click', function (ev) {
    var b = ev.target.closest('button'); if (!b) { return; }
    tab = b.getAttribute('data-tab');
    Array.prototype.forEach.call($('tabs').children, function (x) { x.className = x === b ? 'on' : ''; });
    ['overview', 'live', 'users', 'tools'].forEach(function (n) { $(n).hidden = n !== tab; });
    $('title').textContent = titles[tab]; render();
  });
  $('kind').addEventListener('change', renderFeed);
  $('who').addEventListener('input', renderFeed);
  $('out').addEventListener('click', function (e) {
    e.preventDefault();
    fetch('/api/v1/auth/logout', {method: 'POST', credentials: 'same-origin'}).finally(function () { location.href = '/login.html'; });
  });
  setInterval(function () { if (tab === 'users') { renderUsers(); } }, 30000);
  load().then(connect);
})();
