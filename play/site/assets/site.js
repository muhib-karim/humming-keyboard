// Humming website: turns the download buttons into direct links to the current release's files.
// Reads the update feeds that pages.yml mirrors next to this page (same origin, no third parties). Without them the
// buttons keep pointing at the latest GitHub Release, so the page works with JavaScript off or before a release.
(function () {
  "use strict";
  var REPO = "https://github.com/muhib-karim/humming-keyboard/releases/download/";

  function json(url) {
    return fetch(url, { cache: "no-store" }).then(function (r) {
      if (!r.ok) throw new Error(url + " " + r.status);
      return r.json();
    });
  }

  function base(url) { return String(url || "").split("/").pop(); }

  function set(platform, file, tag) {
    var card = document.querySelector('.dl[data-platform="' + platform + '"]');
    if (!card || !file) return;
    var link = card.querySelector("[data-link]");
    var label = card.querySelector("[data-file]");
    if (link) link.href = REPO + "v" + tag + "/" + encodeURIComponent(file);
    if (label) label.textContent = file;
  }

  Promise.all([json("updates/latest.json"), json("updates/android.json")]).then(function (res) {
    var desk = res[0], android = res[1], p = desk.platforms || {}, v = desk.version;
    if (!v) return;
    set("android", android.apk, android.versionName || v);
    if (p["windows-x86_64-nsis"]) set("windows", base(p["windows-x86_64-nsis"].url), v);
    if (p["linux-x86_64-appimage"]) set("linux", base(p["linux-x86_64-appimage"].url), v);
    var app = p["darwin-aarch64-app"] && base(p["darwin-aarch64-app"].url);
    if (app) set("macos", app.replace(/\.app\.tar\.gz$/, ".dmg"), v);
    var ver = document.getElementById("dl-version");
    if (ver) ver.textContent = "Current version: " + v + ".";
  }).catch(function () { /* keep the links to the latest GitHub Release */ });
})();
