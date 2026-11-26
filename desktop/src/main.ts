import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";

type Dict = { spoken: string; written: string; apps?: string[] };
type ToneRule = { apps: string[]; tone: string; instructions: string };
type MeetingSegment = { ts_ms: number; elapsed_s: number; text: string };
type Meeting = { id: number; title: string; started_ms: number; ended_ms: number | null; notes: string; summary: string; template: string; segments: MeetingSegment[] };
type Settings = {
  hotkey: string; command_hotkey: string; mode: string; model: string;
  clean_mode: boolean; remove_fillers: boolean; self_corrections: boolean; voice_commands: boolean; voice_edit: boolean;
  format_lists: boolean; accuracy_mode: boolean; dictionary: Dict[]; tone_rules: ToneRule[];
  insert_method: string; restore_clipboard: boolean;
  paste_delay_ms: number; trailing_space: boolean; input_device: string; meeting_device: string; show_overlay: boolean;
  launch_at_login: boolean; history_enabled: boolean; history_limit: number; max_seconds: number;
  quick_start?: boolean; auto_update: boolean;
  llm_provider: string; llm_endpoint: string; llm_model: string; llm_api_key: string; llm_polish_dictation: boolean;
  local_api_enabled: boolean; local_api_port: number; local_api_token: string;
  schema: number; // settings file format; kept as received so one-time migrations do not run again
};
type UpdateStatus = { state: string; current: string; latest: string | null; notes: string | null; message: string; done: number; total: number; last_checked: number; can_install: boolean };
type Status = { state: string; message: string };
type Model = { id: string; label: string; blurb: string; download_bytes: number; disk_bytes: number; ram_mb: number; wer_pct: number | null; downloaded: boolean; active: boolean };
type Entry = { id: number; ts: number; text: string; raw: string; audio_ms: number; latency_ms: number; model: string; inserted: boolean };
type DictationEvent = { entry: Entry; to_focused_app: boolean; error: string | null };

const $ = <T extends HTMLElement = HTMLElement>(sel: string) => document.querySelector(sel) as T;
const isMac = navigator.userAgent.includes("Mac");
let settings: Settings;
let history: Entry[] = [];
let meetings: Meeting[] = [];
let selectedMeetingId: number | null = null;
let recordingMeetingId: number | null = null;
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
  if (tab === "meetings") loadMeetings();
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
      <div><b>${esc(m.label)}</b> <span class="muted">${mb(m.download_bytes)} download &middot; ${m.wer_pct == null ? "word error rate not measured" : `${m.wer_pct}% word error rate`} &middot; ~${m.ram_mb} MB RAM</span><br/><span class="muted small">${esc(m.blurb)}</span>
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
    <li class="row between"><span>${d.spoken ? `"${esc(d.spoken)}" &rarr; ` : ""}<b>${esc(d.written)}</b>${d.apps && d.apps.length ? ` <span class="muted small">only in ${esc(d.apps.join(", "))}</span>` : ""}</span>
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
  const apps = $<HTMLInputElement>("#dict-apps").value.split(",").map((a) => a.trim()).filter(Boolean);
  settings = await invoke<Settings>("save_settings", { settings: { ...settings, dictionary: [...settings.dictionary, { spoken, written, apps }] } });
  $<HTMLInputElement>("#dict-apps").value = "";
  $<HTMLInputElement>("#dict-spoken").value = "";
  $<HTMLInputElement>("#dict-written").value = "";
  renderDict();
});
$("#clean-btn").addEventListener("click", async () => {
  $("#clean-out").textContent = await invoke<string>("preview_cleanup", { text: $<HTMLTextAreaElement>("#clean-in").value });
});

// ---------------- CSV dictionary import/export & per-app tone rules ----------------
function renderTones() {
  const rules = settings.tone_rules ?? [];
  $("#tone-list").innerHTML = rules.map((r, i) => `
    <li class="row between"><span><b>${esc(r.tone)}</b>${r.apps.length ? ` in <span class="muted">${esc(r.apps.join(", "))}</span>` : ` <span class="muted">(all apps)</span>`}${r.instructions ? ` &middot; <span class="muted small">${esc(r.instructions)}</span>` : ""}</span>
    <button class="link" data-tone="${i}">Remove</button></li>`).join("") || `<li class="muted">No tone rules yet. Example: Slack, Discord &rarr; Casual.</li>`;
  document.querySelectorAll<HTMLButtonElement>("[data-tone]").forEach((b) => b.addEventListener("click", async () => {
    const tone_rules = (settings.tone_rules ?? []).filter((_, i) => i !== Number(b.dataset.tone));
    settings = await invoke<Settings>("save_settings", { settings: { ...settings, tone_rules } });
    renderTones();
  }));
}
$("#tone-form").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  const apps = $<HTMLInputElement>("#tone-apps").value.split(",").map((a) => a.trim()).filter(Boolean);
  const tone = $<HTMLSelectElement>("#tone-style").value;
  const instructions = $<HTMLInputElement>("#tone-inst").value.trim();
  const tone_rules = [...(settings.tone_rules ?? []), { apps, tone, instructions }];
  settings = await invoke<Settings>("save_settings", { settings: { ...settings, tone_rules } });
  $<HTMLInputElement>("#tone-apps").value = "";
  $<HTMLInputElement>("#tone-inst").value = "";
  renderTones();
});
$("#dict-export-btn").addEventListener("click", () => {
  const rows = [["spoken", "written", "apps"], ...settings.dictionary.map((d) => [d.spoken || "", d.written, (d.apps || []).join(";")])];
  const csv = rows.map((r) => r.map((c) => `"${c.replace(/"/g, '""')}"`).join(",")).join("\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = "humming-dictionary.csv";
  a.click();
  URL.revokeObjectURL(a.href);
});
$("#dict-import-btn").addEventListener("click", () => $<HTMLInputElement>("#dict-csv-file").click());
$<HTMLInputElement>("#dict-csv-file").addEventListener("change", async (ev) => {
  const file = (ev.target as HTMLInputElement).files?.[0];
  if (!file) return;
  const text = await file.text();
  const added: Dict[] = [];
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.toLowerCase().startsWith("spoken,") || line.toLowerCase().startsWith('"spoken"')) continue;
    const cols = line.split(",").map((c) => c.trim().replace(/^"|"$/g, "").replace(/""/g, '"'));
    const spoken = cols.length >= 2 ? cols[0] : "";
    const written = cols.length >= 2 ? cols[1] : cols[0];
    const apps = cols[2] ? cols[2].split(";").map((a) => a.trim()).filter(Boolean) : [];
    if (written) added.push({ spoken, written, apps });
  }
  if (added.length) {
    settings = await invoke<Settings>("save_settings", { settings: { ...settings, dictionary: [...settings.dictionary, ...added] } });
    renderDict();
  }
  $("#dict-csv-msg").textContent = `Imported ${added.length} entries.`;
  setTimeout(() => ($("#dict-csv-msg").textContent = ""), 4000);
});

// ---------------- offline botless meetings (1.9) ----------------
function fmtElapsed(sec: number): string {
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = sec % 60;
  const pad = (n: number) => String(n).padStart(2, "0");
  return h > 0 ? `${pad(h)}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}
async function loadMeetings() {
  meetings = (await invoke<Meeting[]>("list_meetings")) ?? [];
  recordingMeetingId = (await invoke<number | null>("active_meeting_id")) ?? null;
  if (selectedMeetingId == null && meetings.length) selectedMeetingId = meetings[0].id;
  if (selectedMeetingId != null && !meetings.some((m) => m.id === selectedMeetingId)) {
    selectedMeetingId = meetings[0]?.id ?? null;
  }
  renderMeetings();
}
function renderMeetings() {
  const rec = recordingMeetingId != null;
  const pill = $("#mtg-pill");
  pill.textContent = rec ? "recording offline · no bot" : "offline · no bot";
  pill.className = `pill ${rec ? "recording" : "ready"}`;
  const toggle = $<HTMLButtonElement>("#mtg-toggle");
  toggle.textContent = rec ? "Stop meeting" : "Start meeting";
  $("#mtg-list").innerHTML = meetings.map((m) => `
    <li><button data-mtg="${m.id}" class="${m.id === selectedMeetingId ? "active" : ""}">
      <span class="mtg-name">${m.id === recordingMeetingId ? "● " : ""}${esc(m.title)}</span>
      <span class="mtg-date">${new Date(m.started_ms).toLocaleDateString()} &middot; ${m.segments.length} segs</span>
    </button></li>`).join("") || `<li class="muted small">No meetings yet.</li>`;
  document.querySelectorAll<HTMLButtonElement>("[data-mtg]").forEach((b) => b.addEventListener("click", () => {
    selectedMeetingId = Number(b.dataset.mtg);
    renderMeetings();
  }));
  const cur = meetings.find((m) => m.id === selectedMeetingId) ?? null;
  const notesEl = $<HTMLTextAreaElement>("#mtg-notes");
  const txEl = $("#mtg-transcript");
  const sumEl = $("#mtg-summary");
  if (!cur) {
    notesEl.value = "";
    notesEl.disabled = true;
    txEl.innerHTML = `<div class="muted small">Start a meeting to record and transcribe locally. Choose a mic or system loopback device (PipeWire monitor / BlackHole / Stereo Mix) above.</div>`;
    $("#mtg-seg-count").textContent = "0 segments";
    sumEl.textContent = "";
    return;
  }
  notesEl.disabled = false;
  if (document.activeElement !== notesEl) notesEl.value = cur.notes;
  $<HTMLSelectElement>("#mtg-template").value = cur.template || "summary";
  $("#mtg-seg-count").textContent = `${cur.segments.length} segment${cur.segments.length === 1 ? "" : "s"}`;
  txEl.innerHTML = cur.segments.map((s) => `<div class="mtg-seg"><span class="ts">[${fmtElapsed(s.elapsed_s)}]</span>${esc(s.text)}</div>`).join("")
    || `<div class="muted small">Listening… transcript chunks land here automatically every 12 seconds.</div>`;
  sumEl.textContent = cur.summary || "Click Synthesize to merge your notes and transcript into structured Markdown (uses local Ollama / BYOK AI if configured, or deterministic offline digest).";
}
let notesTimer: number | null = null;
$<HTMLTextAreaElement>("#mtg-notes").addEventListener("input", () => {
  if (selectedMeetingId == null) return;
  const id = selectedMeetingId;
  const notes = $<HTMLTextAreaElement>("#mtg-notes").value;
  $("#mtg-save-state").textContent = "Saving…";
  if (notesTimer != null) window.clearTimeout(notesTimer);
  notesTimer = window.setTimeout(async () => {
    const updated = await invoke<Meeting | null>("update_meeting_notes", { id, title: null, notes });
    if (updated) {
      meetings = meetings.map((m) => (m.id === updated.id ? updated : m));
      $("#mtg-save-state").textContent = "Saved";
    }
  }, 400);
});
$("#mtg-toggle").addEventListener("click", async () => {
  try {
    if (recordingMeetingId != null) {
      const m = await invoke<Meeting | null>("stop_meeting");
      recordingMeetingId = null;
      if (m) { meetings = [m, ...meetings.filter((x) => x.id !== m.id)]; selectedMeetingId = m.id; }
    } else {
      const title = $<HTMLInputElement>("#mtg-title").value.trim();
      const device = $<HTMLSelectElement>("#mtg-device").value || null;
      const m = await invoke<Meeting>("start_meeting", { title, device });
      recordingMeetingId = m.id;
      selectedMeetingId = m.id;
      meetings = [m, ...meetings.filter((x) => x.id !== m.id)];
      $<HTMLInputElement>("#mtg-title").value = "";
    }
    renderMeetings();
  } catch (e) { alert(String(e)); }
});
$("#mtg-synth").addEventListener("click", async () => {
  if (selectedMeetingId == null) return;
  const btn = $<HTMLButtonElement>("#mtg-synth");
  btn.disabled = true; btn.textContent = "Synthesizing…";
  try {
    const template = $<HTMLSelectElement>("#mtg-template").value;
    const updated = await invoke<Meeting>("synthesize_meeting", { id: selectedMeetingId, template });
    meetings = meetings.map((m) => (m.id === updated.id ? updated : m));
    renderMeetings();
  } catch (e) { alert(String(e)); }
  finally { btn.disabled = false; btn.textContent = "Synthesize"; }
});
$("#mtg-export").addEventListener("click", async () => {
  if (selectedMeetingId == null) return;
  const md = await invoke<string>("export_meeting_markdown", { id: selectedMeetingId });
  await invoke("copy_text", { text: md });
  const btn = $<HTMLButtonElement>("#mtg-export");
  btn.textContent = "Copied Markdown";
  setTimeout(() => (btn.textContent = "Copy Markdown"), 1500);
});
$("#mtg-del").addEventListener("click", async () => {
  if (selectedMeetingId == null || !confirm("Delete this meeting?")) return;
  await invoke("delete_meeting", { id: selectedMeetingId });
  selectedMeetingId = null;
  loadMeetings();
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
  const cmdHint = document.querySelector("#cmd-hk-hint");
  if (cmdHint) cmdHint.textContent = settings.command_hotkey ? prettyHotkey(settings.command_hotkey) : "off";
  const tokView = document.querySelector<HTMLInputElement>("#api-token-view");
  if (tokView) tokView.value = settings.local_api_token || "";
  renderDict();
  renderTones();
}
async function loadDevices() {
  const devices = await invoke<string[]>("list_input_devices");
  const opts = `<option value="">System default</option>` + devices.map((d) => `<option>${esc(d)}</option>`).join("");
  const sel = form.elements.namedItem("input_device") as HTMLSelectElement;
  sel.innerHTML = opts;
  sel.value = settings.input_device;
  const mtgSel = document.querySelector<HTMLSelectElement>("#mtg-device");
  if (mtgSel) { mtgSel.innerHTML = opts; mtgSel.value = settings.meeting_device || ""; }
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

// ---------------- updates (1.6) ----------------
function renderUpdate(u: UpdateStatus) {
  let msg = u.message || "";
  if (u.state === "downloading" && u.total > 0) msg = `${msg} (${Math.round((100 * u.done) / u.total)}%)`;
  if (u.state === "idle") msg = `Version ${u.current}. Humming checks for updates on its own every few hours.`;
  if (u.state === "up_to_date" && u.last_checked) msg = `${msg} (${u.current}, checked ${new Date(u.last_checked * 1000).toLocaleTimeString()})`;
  $("#upd-msg").textContent = msg;
  $<HTMLButtonElement>("#upd-install").hidden = !(u.state === "available" && u.can_install);
  const busy = ["checking", "downloading", "installing", "restarting"].includes(u.state);
  $<HTMLButtonElement>("#upd-check").disabled = busy;
  $<HTMLButtonElement>("#upd-install").disabled = busy;
}
$("#upd-check").addEventListener("click", async () => {
  try { renderUpdate(await invoke<UpdateStatus>("check_for_updates", { install: false })); } catch (e) { $("#upd-msg").textContent = String(e); }
});
$("#upd-install").addEventListener("click", async () => {
  try { renderUpdate(await invoke<UpdateStatus>("check_for_updates", { install: true })); } catch (e) { $("#upd-msg").textContent = String(e); }
});
$("#llm-test").addEventListener("click", async () => {
  $("#llm-msg").textContent = "Testing…";
  try {
    const out = await invoke<string>("test_llm");
    $("#llm-msg").textContent = `OK: "${out}"`;
  } catch (e) { $("#llm-msg").textContent = String(e); }
});
$("#api-copy-curl").addEventListener("click", async () => {
  const port = settings.local_api_port || 17831;
  const tok = settings.local_api_token || "<token>";
  const cmd = `curl -sS -X POST http://127.0.0.1:${port}/v1/transcribe -H "Authorization: Bearer ${tok}" --data-binary @clip.wav`;
  await invoke("copy_text", { text: cmd });
  const btn = $<HTMLButtonElement>("#api-copy-curl");
  btn.textContent = "Copied";
  setTimeout(() => (btn.textContent = "Copy curl"), 1400);
});
$("#api-regen").addEventListener("click", async () => {
  try { settings = await invoke<Settings>("regenerate_api_token"); fillSettings(); } catch (e) { alert(String(e)); }
});

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
  await listen<UpdateStatus>("update-status", (e) => renderUpdate(e.payload));
  renderUpdate(await invoke<UpdateStatus>("update_status"));
  await listen<string>("navigate", (e) => show(e.payload));
  await listen<{ id: string; done: number; total: number }>("model-progress", (e) => {
    const bar = document.querySelector<HTMLElement>(`#prog-${e.payload.id}`);
    if (bar) { bar.hidden = false; (bar.firstElementChild as HTMLElement).style.width = `${(100 * e.payload.done) / e.payload.total}%`; }
  });
  await listen<string>("model-downloaded", () => { downloading = null; loadModels(); });
  await listen<string>("model-error", (e) => { downloading = null; alert(e.payload); loadModels(); });
  await listen<Meeting>("meeting-updated", (e) => {
    const m = e.payload;
    meetings = [m, ...meetings.filter((x) => x.id !== m.id)];
    if (selectedMeetingId == null) selectedMeetingId = m.id;
    if (document.querySelector("#tab-meetings.active")) renderMeetings();
  });
  await listen<DictationEvent>("dictation", (e) => {
    if (!e.payload.to_focused_app) {
      $("#try-out").textContent = e.payload.entry.text || "(heard nothing)";
      $("#try-raw").textContent = e.payload.entry.raw;
    }
    if (document.querySelector("#tab-history.active")) loadHistory();
  });
}
boot();
