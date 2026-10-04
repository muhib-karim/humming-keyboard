import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";

type Dict = { spoken: string; written: string };
type Settings = {
  hotkey: string; mode: string; model: string;
  clean_mode: boolean; remove_fillers: boolean; self_corrections: boolean; voice_commands: boolean;
  format_lists: boolean; accuracy_mode: boolean; dictionary: Dict[]; insert_method: string; restore_clipboard: boolean;
  paste_delay_ms: number; trailing_space: boolean; input_device: string; show_overlay: boolean;
  launch_at_login: boolean; history_enabled: boolean; history_limit: number; max_seconds: number;
};
type Status = { state: string; message: string };
type Model = { id: string; label: string; blurb: string; download_bytes: number; disk_bytes: number; ram_mb: number; wer_pct: number; downloaded: boolean; active: boolean };
type Entry = { id: number; ts: number; text: string; raw: string; audio_ms: number; latency_ms: number; model: string; inserted: boolean };
type DictationEvent = { entry: Entry; to_focused_app: boolean; error: string | null };

const $ = <T extends HTMLElement = HTMLElement>(sel: string) => document.querySelector(sel) as T;
const isMac = navigator.userAgent.includes("Mac");
let settings: Settings;
let history: Entry[] = [];
let downloading: string | null = null;
let testing = false;

function esc(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
}
function prettyHotkey(h: string): string {
  return h
    .replace(/CommandOrControl|CmdOrCtrl/g, isMac ? "Cmd" : "Ctrl")
    .replace(/\bAlt\b/g, isMac ? "Option" : "Alt")
    .replace(/\+/g, " + ");
}
function mb(n: number): string { return `${(n / 1e6).toFixed(0)} MB`; }

// ---------------- navigation ----------------
function show(tab: string) {
  document.querySelectorAll<HTMLButtonElement>("nav.side button").forEach((b) => b.classList.toggle("active", b.dataset.tab === tab));
  document.querySelectorAll<HTMLElement>(".tab").forEach((s) => s.classList.toggle("active", s.id === `tab-${tab}`));
  if (tab === "history") loadHistory();
}
document.querySelectorAll<HTMLButtonElement>("nav.side button").forEach((b) => b.addEventListener("click", () => show(b.dataset.tab!)));

// ---------------- status ----------------
const LABELS: Record<string, string> = {
  no_model: "needs a model", loading: "loading", ready: "ready", recording: "listening", transcribing: "transcribing", error: "problem",
};
function renderStatus(s: Status) {
  const pill = $("#status-pill");
  pill.textContent = LABELS[s.state] ?? s.state;
  pill.className = `pill ${s.state}`;
  $("#status-msg").textContent = s.message || (s.state === "ready" ? `Hold ${prettyHotkey(settings?.hotkey ?? "")} anywhere to dictate.` : "");
  const btn = $<HTMLButtonElement>("#try-btn");
  btn.disabled = !(s.state === "ready" || s.state === "recording" || s.state === "error");
  if (s.state !== "recording" && testing && s.state !== "transcribing") testing = false;
  btn.textContent = testing ? "Stop and transcribe" : "Start talking";
}

// ---------------- models ----------------
async function loadModels() {
  const models = await invoke<Model[]>("list_models");
  $("#model-list").innerHTML = models.map((m) => `
    <div class="model ${m.active ? "active" : ""}">
      <div><b>${esc(m.label)}</b> <span class="muted">${mb(m.download_bytes)} download &middot; ${m.wer_pct}% word error rate &middot; ~${m.ram_mb} MB RAM</span><br/><span class="muted small">${esc(m.blurb)}</span>
        <div class="progress" id="prog-${m.id}" hidden><div></div></div></div>
      <div class="row">
        ${m.downloaded
          ? (m.active ? `<span class="pill ready">in use</span>` : `<button data-use="${m.id}">Use</button>`) + ` <button class="link" data-del="${m.id}">Delete</button>`
          : `<button class="primary" data-get="${m.id}" ${downloading ? "disabled" : ""}>Download</button>`}
      </div>
    </div>`).join("");
  document.querySelectorAll<HTMLButtonElement>("[data-get]").forEach((b) => b.addEventListener("click", async () => {
    downloading = b.dataset.get!;
    b.disabled = true;
    b.textContent = "Downloading...";
    $(`#prog-${downloading}`).hidden = false;
    try { await invoke("download_model", { id: downloading }); } catch (e) { alert(String(e)); downloading = null; loadModels(); }
  }));
  document.querySelectorAll<HTMLButtonElement>("[data-use]").forEach((b) => b.addEventListener("click", async () => {
    settings = await invoke<Settings>("save_settings", { settings: { ...settings, model: b.dataset.use! } });
    fillSettings();
    loadModels();
  }));
  document.querySelectorAll<HTMLButtonElement>("[data-del]").forEach((b) => b.addEventListener("click", async () => {
    if (!confirm("Delete this model from disk?")) return;
    await invoke("delete_model", { id: b.dataset.del });
    loadModels();
  }));
}

// ---------------- try it ----------------
$("#try-btn").addEventListener("click", async () => {
  try {
    if (!testing) { testing = true; $("#try-out").textContent = ""; $("#try-raw").textContent = ""; await invoke("test_start"); }
    else { testing = false; await invoke("test_stop"); }
  } catch (e) { testing = false; $("#try-out").textContent = String(e); }
  renderStatus(await invoke<Status>("get_status"));
});

// ---------------- history ----------------
async function loadHistory() {
  history = await invoke<Entry[]>("get_history");
  renderHistory();
}
function renderHistory() {
  const q = $<HTMLInputElement>("#hist-search").value.trim().toLowerCase();
  const rows = q ? history.filter((h) => h.text.toLowerCase().includes(q)) : history;
  $("#hist-count").textContent = `${history.length} dictation${history.length === 1 ? "" : "s"} kept on this computer`;
  $("#hist-list").innerHTML = rows.map((h) => `
    <li>
      <div class="meta">${new Date(h.ts).toLocaleString()} &middot; ${(h.audio_ms / 1000).toFixed(1)} s audio &middot; ready in ${(h.latency_ms / 1000).toFixed(2)} s${h.inserted ? "" : " &middot; not pasted"}</div>
      <div class="text">${esc(h.text)}</div>
      <div class="row"><button data-copy="${h.id}">Copy</button><button class="link" data-rm="${h.id}">Delete</button></div>
    </li>`).join("") || `<li class="muted">Nothing yet. Hold the shortcut and say something.</li>`;
  document.querySelectorAll<HTMLButtonElement>("[data-copy]").forEach((b) => b.addEventListener("click", async () => {
    const h = history.find((x) => x.id === Number(b.dataset.copy));
    if (h) { await invoke("copy_text", { text: h.text }); b.textContent = "Copied"; setTimeout(() => (b.textContent = "Copy"), 1200); }
  }));
  document.querySelectorAll<HTMLButtonElement>("[data-rm]").forEach((b) => b.addEventListener("click", async () => {
    await invoke("delete_history", { id: Number(b.dataset.rm) });
    loadHistory();
  }));
}
$("#hist-search").addEventListener("input", renderHistory);
$("#hist-clear").addEventListener("click", async () => {
  if (confirm("Delete the whole history?")) { await invoke("clear_history"); loadHistory(); }
});

// ---------------- dictionary ----------------
function renderDict() {
  $("#dict-list").innerHTML = settings.dictionary.map((d, i) => `
    <li class="row between"><span>${d.spoken ? `"${esc(d.spoken)}" &rarr; ` : ""}<b>${esc(d.written)}</b></span>
    <button class="link" data-dict="${i}">Remove</button></li>`).join("") || `<li class="muted">No words yet.</li>`;
  document.querySelectorAll<HTMLButtonElement>("[data-dict]").forEach((b) => b.addEventListener("click", async () => {
    const dictionary = settings.dictionary.filter((_, i) => i !== Number(b.dataset.dict));
    settings = await invoke<Settings>("save_settings", { settings: { ...settings, dictionary } });
    renderDict();
  }));
}
$("#dict-form").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  const spoken = $<HTMLInputElement>("#dict-spoken").value.trim();
  const written = $<HTMLInputElement>("#dict-written").value.trim();
  if (!written) return;
  settings = await invoke<Settings>("save_settings", { settings: { ...settings, dictionary: [...settings.dictionary, { spoken, written }] } });
  $<HTMLInputElement>("#dict-spoken").value = "";
  $<HTMLInputElement>("#dict-written").value = "";
  renderDict();
});
$("#clean-btn").addEventListener("click", async () => {
  $("#clean-out").textContent = await invoke<string>("preview_cleanup", { text: $<HTMLTextAreaElement>("#clean-in").value });
});

// ---------------- settings ----------------
const form = $<HTMLFormElement>("#settings-form");
function fillSettings() {
  for (const el of Array.from(form.elements) as HTMLInputElement[]) {
    const k = el.name as keyof Settings;
    if (!k || !(k in settings)) continue;
    const v = settings[k];
    if (el.type === "checkbox") el.checked = Boolean(v);
    else el.value = String(v);
  }
  $("#hk-hint").textContent = prettyHotkey(settings.hotkey);
  renderDict();
}
async function loadDevices() {
  const devices = await invoke<string[]>("list_input_devices");
  const sel = form.elements.namedItem("input_device") as HTMLSelectElement;
  sel.innerHTML = `<option value="">System default</option>` + devices.map((d) => `<option>${esc(d)}</option>`).join("");
  sel.value = settings.input_device;
}
form.addEventListener("submit", async (ev) => {
  ev.preventDefault();
  const next: Settings = { ...settings };
  const rec = next as unknown as Record<string, unknown>;
  for (const el of Array.from(form.elements) as HTMLInputElement[]) {
    const k = el.name;
    if (!k || !(k in next)) continue;
    rec[k] = el.type === "checkbox" ? el.checked : el.type === "number" ? Number(el.value) : el.value;
  }
  try {
    settings = await invoke<Settings>("save_settings", { settings: next });
    fillSettings();
    $("#settings-msg").textContent = "Saved.";
  } catch (e) {
    $("#settings-msg").textContent = String(e);
  }
  setTimeout(() => ($("#settings-msg").textContent = ""), 4000);
});
// Record a shortcut: press the keys you want while the button is armed.
$("#hk-record").addEventListener("click", () => {
  const btn = $<HTMLButtonElement>("#hk-record");
  btn.textContent = "Press the keys now...";
  const onKey = (e: KeyboardEvent) => {
    e.preventDefault();
    if (["Control", "Shift", "Alt", "Meta"].includes(e.key)) return;
    const parts: string[] = [];
    if (e.ctrlKey || e.metaKey) parts.push(isMac && e.metaKey ? "Command" : "Control");
    if (e.altKey) parts.push("Alt");
    if (e.shiftKey) parts.push("Shift");
    parts.push(e.code.replace(/^Key/, "").replace(/^Digit/, ""));
    (form.elements.namedItem("hotkey") as HTMLInputElement).value = parts.join("+");
    btn.textContent = "Record a shortcut";
    window.removeEventListener("keydown", onKey, true);
  };
  window.addEventListener("keydown", onKey, true);
});

const isLinux = !isMac && navigator.userAgent.includes("Linux");
function renderTips() {
  if (isLinux) {
    $("#tips").innerHTML = `<h2>First run on Linux</h2><ul>
        <li>AppImage: make it executable (<code>chmod +x Humming_*.AppImage</code>) and run it. Debian/Ubuntu: <code>sudo apt install ./Humming_*_amd64.deb</code>.</li>
        <li>Pasting into other apps works on X11 sessions and XWayland apps. On a pure Wayland session auto-paste may not land; every dictation is kept in History, one click to copy.</li>
        <li>Allow the microphone if your desktop asks (PipeWire / PulseAudio).</li></ul>`;
    return;
  }
  $("#tips").innerHTML = isMac
    ? `<h2>First run on a Mac</h2><ul>
        <li>Allow the microphone when asked.</li>
        <li>Allow Humming in System Settings &rarr; Privacy &amp; Security &rarr; <b>Accessibility</b>, so it can paste for you.</li>
        <li>This test build is unsigned: right-click the app and choose Open the first time.</li></ul>`
    : `<h2>First run on Windows</h2><ul>
        <li>Allow microphone access in Settings &rarr; Privacy &amp; security &rarr; Microphone ("Let desktop apps access your microphone").</li>
        <li>This test build is unsigned: on the SmartScreen prompt choose More info &rarr; Run anyway.</li>
        <li>Apps running as administrator only accept pasted text from Humming if Humming also runs as administrator.</li></ul>`;
}

// ---------------- boot ----------------
async function boot() {
  settings = await invoke<Settings>("get_settings");
  fillSettings();
  renderTips();
  const info = await invoke<{ version: string; os: string; arch: string; models_dir: string; history_file: string }>("app_info");
  $("#version").textContent = `v${info.version} · ${info.os} ${info.arch}`;
  $("#paths").textContent = `Models: ${info.models_dir} · History: ${info.history_file}`;
  renderStatus(await invoke<Status>("get_status"));
  await loadModels();
  loadDevices();

  await listen<Status>("status", (e) => renderStatus(e.payload));
  await listen<string>("navigate", (e) => show(e.payload));
  await listen<{ id: string; done: number; total: number }>("model-progress", (e) => {
    const bar = document.querySelector<HTMLElement>(`#prog-${e.payload.id}`);
    if (bar) { bar.hidden = false; (bar.firstElementChild as HTMLElement).style.width = `${(100 * e.payload.done) / e.payload.total}%`; }
  });
  await listen<string>("model-downloaded", () => { downloading = null; loadModels(); });
  await listen<string>("model-error", (e) => { downloading = null; alert(e.payload); loadModels(); });
  await listen<DictationEvent>("dictation", (e) => {
    if (!e.payload.to_focused_app) {
      $("#try-out").textContent = e.payload.entry.text || "(heard nothing)";
      $("#try-raw").textContent = e.payload.entry.raw;
    }
    if (document.querySelector("#tab-history.active")) loadHistory();
  });
}
boot();
