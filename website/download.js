(function () {
  var api = "https://api.github.com/repos/itechroar/kagaz-pdf-pro/releases?per_page=1";
  var ua = navigator.userAgent;
  var os = /Android/i.test(ua) ? "android" : /Windows/i.test(ua) ? "windows" : /Mac/i.test(ua) ? "mac" : "linux";
  var match = {
    android: function (n) { return /\.apk$/i.test(n) && !/admin/i.test(n); },
    windows: function (n) { return /\.exe$/i.test(n) && !/admin/i.test(n); },
    mac: function (n) { return /\.dmg$/i.test(n) && !/admin/i.test(n); },
    linux: function (n) { return /\.AppImage$/i.test(n) && !/admin/i.test(n); }
  };
  fetch(api).then(function (r) { return r.json(); }).then(function (list) {
    var assets = (list[0] && list[0].assets) || [];
    function find(kind) {
      var f = assets.filter(function (a) { return match[kind](a.name); })[0];
      return f && f.browser_download_url;
    }
    var main = find(os);
    var btn = document.getElementById("dl-main");
    if (main && btn) btn.href = main;
    var d = find(os === "android" ? "windows" : os);
    var a = find("android");
    function findAdmin(kind) {
      var ext = { android: /\.apk$/i, windows: /\.exe$/i, mac: /\.dmg$/i, linux: /\.AppImage$/i }[kind];
      var f = assets.filter(function (x) { return ext.test(x.name) && /admin/i.test(x.name); })[0];
      return f && f.browser_download_url;
    }
    var ad = findAdmin(os === "android" ? "windows" : os), aa = findAdmin("android");
    document.querySelectorAll("[data-dl=admin-desktop]").forEach(function (e) { if (ad) e.href = ad; });
    document.querySelectorAll("[data-dl=admin-android]").forEach(function (e) { if (aa) e.href = aa; });
    document.querySelectorAll("[data-dl=desktop]").forEach(function (e) { if (d) e.href = d; });
    document.querySelectorAll("[data-dl=android]").forEach(function (e) { if (a) e.href = a; });
  }).catch(function () {});
})();
