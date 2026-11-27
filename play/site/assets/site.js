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

  // Interactive "How Humming transforms voice" preview (pure client-side, zero network).
  var DEMOS = {
    clean: {
      inLabel: "Raw speech (spoken aloud)",
      inText: "\"um so let's meet at 3, no wait, 4pm and uh bring the printed slides\"",
      outLabel: "Typed into your app",
      badge: "100% offline · Clean mode",
      outText: "Let's meet at 4pm and bring the printed slides.",
      meta: "Removes fillers and stutters, applies self-corrections, and punctuates on-device across Android, iOS, and desktop."
    },
    lists: {
      inLabel: "Raw speech (spoken list)",
      inText: "\"groceries bullet point eggs bullet point oat milk bullet point sourdough\"",
      outLabel: "Formatted Markdown list",
      badge: "100% offline · Smart lists",
      outText: "Groceries:\n- Eggs\n- Oat milk\n- Sourdough",
      meta: "Recognizes both \"bullet point\" commands and natural \"first, ... second, ... third, ...\" lists identically on every platform."
    },
    edit: {
      inLabel: "Existing text + voice command",
      inText: "In field: \"See you Tuesday at noon\"\nYou say: \"change Tuesday to Thursday\"",
      outLabel: "Edited in place",
      badge: "100% offline · 36 shared vectors",
      outText: "See you Thursday at noon",
      meta: "Whole-utterance guard ensures normal dictation like \"change the oil to synthetic\" is never misinterpreted as a command."
    },
    command: {
      inLabel: "Selected text + Ctrl/Cmd+Shift+E",
      inText: "Selected: \"hey can we move the call to next week maybe?\"\nYou say: \"make it polite for email\"",
      outLabel: "Rewritten right over selection",
      badge: "Local Ollama or BYOK · Command Mode",
      outText: "Could we reschedule our call to next week? Please let me know what times work best for you.",
      meta: "Works with 100% offline local Ollama (zero API key) or your own OpenAI, Anthropic, or Gemini key — audio never leaves your machine."
    },
    meetings: {
      inLabel: "Dual-track mic + system audio + notes",
      inText: "Notes: \"- confirm WASAPI loopback\"\n[00:14 · You]: \"Does WASAPI capture remote speakers without a bot?\"\n[00:26 · Remote]: \"Yes, send the release notes by Friday.\"",
      outLabel: "Synthesized Markdown + speaker citations",
      badge: "100% offline · Dual-track · No call bot",
      outText: "## Action Items\n- [00:26 · Remote] Send the release notes by Friday.\n- [00:14 · You] Confirm WASAPI loopback capture & tag v2.0.",
      meta: "Records your mic [You] and system output [Remote] simultaneously on Windows WASAPI or Unix/Mac monitor/BlackHole, with Ask Across Meetings."
    },
    mcp: {
      inLabel: "Claude Desktop / Cursor -> POST /mcp",
      inText: "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"humming_search_meetings\",\"arguments\":{\"query\":\"WASAPI Friday\"}}}",
      outLabel: "Loopback MCP JSON-RPC 2.0 response",
      badge: "127.0.0.1 Bearer Auth · Built-in MCP",
      outText: "[v2.0 Sync · 00:26 · Remote] Yes, send the release notes by Friday.",
      meta: "Zero cloud MCP relay: exposes 5 local MCP tools plus /v1/transcribe, /v1/cleanup, /v1/meetings, and /v1/history on 127.0.0.1."
    },
    whisper: {
      inLabel: "Soft voice (-50 dB late-night room)",
      inText: "[faint mic RMS 0.01 -> Whisper Mode auto-gain +4.5x + -54 dB VAD + 1.4s live overlay draft]",
      outLabel: "Crisp punctuation without shouting",
      badge: "100% offline · Whisper Mode + Live Draft",
      outText: "Push the release branch after lunch and close the open issues.",
      meta: "Whisper Mode rescues quiet dictation without clipping normal speech, while Live Draft previews words inside the frosted overlay pill."
    },
    bangla: {
      inLabel: "QWERTY phonetic typing",
      inText: "ami bangla likhi",
      outLabel: "Bangla script (Latin 1 tap away)",
      badge: "100% offline · Avro conventions",
      outText: "আমি বাংলা লিখি",
      meta: "Built into the Android keyboard: pure Kotlin Avro-style transliterator with retroflex capitals (T, D, N, R, Sh) and long vowels (I, U, O)."
    }
  };

  var tabs = document.querySelectorAll(".demo-tab");
  if (tabs.length) {
    function showDemo(key, animate) {
      var d = DEMOS[key];
      if (!d) return;
      tabs.forEach(function (t) {
        var on = t.getAttribute("data-demo") === key;
        t.classList.toggle("active", on);
        t.setAttribute("aria-selected", on ? "true" : "false");
      });
      var setTxt = function (id, v) { var el = document.getElementById(id); if (el) el.textContent = v; };
      setTxt("demo-in-label", d.inLabel);
      setTxt("demo-in-text", d.inText);
      setTxt("demo-out-label", d.outLabel);
      setTxt("demo-badge", d.badge);
      setTxt("demo-out-text", d.outText);
      setTxt("demo-meta", d.meta);
      var outEl = document.getElementById("demo-out-text");
      if (animate && outEl) {
        outEl.classList.remove("swap");
        void outEl.offsetWidth;
        outEl.classList.add("swap");
      }
    }
    tabs.forEach(function (t) {
      t.addEventListener("click", function () { showDemo(t.getAttribute("data-demo"), true); });
    });
    showDemo("clean", false);
  }

  // Progressive scroll reveal for bento cards (skipped under prefers-reduced-motion).
  var reveals = document.querySelectorAll("[data-reveal]");
  var reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (reveals.length && !reduce && "IntersectionObserver" in window) {
    document.documentElement.classList.add("js-anim");
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-revealed");
          io.unobserve(entry.target);
        }
      });
    }, { rootMargin: "0px 0px -24px 0px", threshold: 0.05 });
    reveals.forEach(function (el) {
      var r = el.getBoundingClientRect();
      if (r.top < window.innerHeight * 1.1) {
        el.classList.add("is-revealed");
      } else {
        io.observe(el);
      }
    });
  }
})();
