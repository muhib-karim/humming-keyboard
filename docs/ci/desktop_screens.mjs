
// Screenshots of the desktop app's own interface (desktop/dist, the exact UI the Tauri window loads), with the
// native side answered by fixed sample data through Tauri's IPC entry point. Run by .github/workflows/screens.yml.
import { chromium } from "playwright";
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, join } from "node:path";

const DIST = process.argv[2] ?? "desktop/dist";
const OUT = process.argv[3] ?? "screens";
const TYPES = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".svg": "image/svg+xml", ".png": "image/png" };
const server = createServer(async (req, res) => {
  const p = join(DIST, decodeURIComponent(new URL(req.url, "http://x").pathname).replace(/\/$/, "/index.html"));
  try { res.writeHead(200, { "content-type": TYPES[extname(p)] ?? "application/octet-stream" }); res.end(await readFile(p)); }
  catch { res.writeHead(404); res.end(); }
}).listen(4173);

const mock = () => {
  const now = Date.now();
  const settings = {
    hotkey: "CommandOrControl+Shift+Space", mode: "hold", model: "fast", clean_mode: true, remove_fillers: true,
    self_corrections: true, voice_commands: true, format_lists: true, accuracy_mode: true,
    dictionary: [{ spoken: "hum ming", written: "Humming" }, { spoken: "", written: "Okonkwo" }, { spoken: "", written: "GitHub" }],
    insert_method: "paste", restore_clipboard: true, paste_delay_ms: 150, trailing_space: true, input_device: "",
    show_overlay: true, launch_at_login: false, history_enabled: true, history_limit: 500, max_seconds: 0, auto_update: true,
  };
  const history = [
    ["Please send the quarterly report to the whole team by Friday afternoon.", 4.8, 0.21],
    ["Meet at 4pm on Thursday in the small room, and bring the printed slides.", 5.6, 0.24],
    ["Thanks for the quick turnaround. The new build fixes the crash on start-up.", 5.1, 0.22],
    ["Reminder: book the train for the 9th and ask Okonkwo about the venue.", 4.2, 0.19],
    ["Humming types what I say in any app, and nothing leaves this computer.", 4.4, 0.2],
  ].map(([text, a, l], i) => ({ id: 5 - i, ts: now - i * 3600e3 - 600e3, text, raw: text, audio_ms: a * 1000, latency_ms: l * 1000, model: "fast", inserted: true }));
  let status = { state: "ready", message: "" };
  const cbs = {};
  const emit = (event, payload) => (cbs[event] ?? []).forEach((f) => f({ event, id: 0, payload }));
  window.__demo = { emit };
  const handlers = {
    get_settings: () => settings,
    save_settings: (a) => Object.assign(settings, a.settings),
    get_status: () => status,
    update_status: () => ({ state: "up_to_date", current: "1.6.0", latest: null, notes: null, message: "Humming is up to date", done: 0, total: 0, last_checked: 1790000000, can_install: true }),
    check_for_updates: () => ({ state: "up_to_date", current: "1.6.0", latest: null, notes: null, message: "Humming is up to date", done: 0, total: 0, last_checked: 1790000000, can_install: true }),
    app_info: () => ({ version: "1.6.0", os: "linux", arch: "x86_64", models_dir: "~/.local/share/humming/models", history_file: "~/.local/share/humming/history.jsonl" }),
    list_models: () => [
      { id: "fast", label: "Fast", blurb: "Parakeet 110M. Instant and very accurate. Recommended.", download_bytes: 104337827, disk_bytes: 126e6, ram_mb: 500, wer_pct: 3.7, downloaded: true, active: true },
      { id: "best", label: "Best", blurb: "Parakeet 0.6B. The most accurate; wants 8 GB of RAM or more.", download_bytes: 482468385, disk_bytes: 640e6, ram_mb: 1300, wer_pct: 2.6, downloaded: false, active: false },
    ],
    list_input_devices: () => ["Built-in Microphone", "USB Headset"],
    get_history: () => history,
    preview_cleanup: () => "I was thinking we could meet at 4pm.",
    test_start: () => { status = { state: "recording", message: "Listening. Click again to stop." }; emit("status", status); },
    test_stop: () => {
      status = { state: "transcribing", message: "Transcribing on this computer..." }; emit("status", status);
      setTimeout(() => {
        status = { state: "ready", message: "" }; emit("status", status);
        emit("dictation", { entry: { id: 6, ts: Date.now(), text: "I was thinking we could meet at 4pm on Friday.", raw: "Um, so, I was, like, thinking we could meet at 3, no, 4pm on Friday", audio_ms: 4300, latency_ms: 230, model: "fast", inserted: false }, to_focused_app: false, error: null });
      }, 700);
    },
  };
  let next = 1;
  window.__TAURI_INTERNALS__ = {
    metadata: { currentWindow: { label: "main" }, currentWebview: { windowLabel: "main", label: "main" } },
    transformCallback: (cb) => { const id = next++; window[`_${id}`] = cb; return id; },
    convertFileSrc: (s) => s,
    invoke: async (cmd, args = {}) => {
      if (cmd === "plugin:event|listen") { (cbs[args.event] ??= []).push(window[`_${args.handler}`]); return next++; }
      if (cmd.startsWith("plugin:")) return null;
      const h = handlers[cmd];
      return h ? h(args) : null;
    },
  };
};

const browser = await chromium.launch();
const shoot = async (scheme, tab, file, extra) => {
  const page = await browser.newPage({ viewport: { width: 1100, height: 760 }, deviceScaleFactor: 1, colorScheme: scheme, userAgent: "Mozilla/5.0 (X11; Linux x86_64) Humming" });
  await page.addInitScript(mock);
  await page.goto("http://localhost:4173/index.html");
  await page.waitForTimeout(600);
  if (tab) { await page.click(`nav.side button[data-tab="${tab}"]`); await page.waitForTimeout(400); }
  if (extra) await extra(page);
  await page.screenshot({ path: `${OUT}/${file}.png` });
  await page.close();
};
await shoot("light", null, "desktop_home");
await shoot("dark", null, "desktop_home_dark");
await shoot("light", "history", "desktop_history");
await shoot("light", "dictionary", "desktop_dictionary", async (p) => {
  await p.fill("#clean-in", "Um, so, I was, like, thinking we could meet at 3, no, 4pm").catch(() => {});
  await p.click("#clean-btn").catch(() => {});
  await p.waitForTimeout(300);
});
await shoot("light", "settings", "desktop_settings");
await shoot("dark", "settings", "desktop_settings_dark");

// Walkthrough recording: Try it here -> talk -> stop -> clean text.
const ctx = await browser.newContext({ viewport: { width: 1100, height: 760 }, colorScheme: "light", recordVideo: { dir: `${OUT}/video`, size: { width: 1100, height: 760 } } });
const page = await ctx.newPage();
await page.addInitScript(mock);
await page.goto("http://localhost:4173/index.html");
await page.waitForTimeout(1200);
await page.locator("#try-btn").scrollIntoViewIfNeeded();
await page.waitForTimeout(800);
await page.click("#try-btn");
await page.waitForTimeout(2500);
await page.click("#try-btn");
await page.waitForTimeout(2200);
await page.click("details summary").catch(() => {});
await page.waitForTimeout(1800);
await ctx.close();
await browser.close();
server.close();
