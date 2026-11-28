window.kagazFormat = {
  when: function (v) { var d = v ? new Date(typeof v === 'number' ? v : v) : null; return d && !isNaN(d) ? d.toLocaleString() : '—'; },
  text: function (parent, tag, value) { var e = document.createElement(tag); e.textContent = value; parent.appendChild(e); return e; }
};
