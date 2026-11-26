import { listen } from "@tauri-apps/api/event";

const bars = document.getElementById("bars")!;
const label = document.getElementById("label")!;
const timer = document.getElementById("timer")!;
const N = 11;
const MID = (N - 1) / 2;
for (let i = 0; i < N; i++) bars.appendChild(document.createElement("i"));
const els = Array.from(bars.children) as HTMLElement[];
let level = 0;
let smooth = 0;
let startedAt = Date.now();

listen<number>("level", (e) => {
  level = Math.min(1, Math.sqrt(e.payload * 8));
});
listen<{ state: string; message?: string }>("status", (e) => {
  const st = e.payload.state;
  const msg = e.payload.message || "";
  const isCmd = msg.toLowerCase().includes("command") || msg.toLowerCase().includes("rewriting");
  document.body.classList.toggle("busy", st === "transcribing");
  document.body.classList.toggle("command", isCmd);
  if (st === "recording") {
    startedAt = Date.now();
    label.textContent = isCmd ? "Command Mode" : "Listening";
  } else if (st === "transcribing") {
    label.textContent = isCmd ? "Rewriting" : "Transcribing";
  } else {
    label.textContent = "Listening";
  }
});

let t = 0;
function frame() {
  t += 0.16;
  smooth += (level - smooth) * 0.28;
  const busy = document.body.classList.contains("busy");
  const sec = Math.max(0, Math.floor((Date.now() - startedAt) / 1000));
  timer.textContent = `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, "0")}`;
  els.forEach((el, i) => {
    const dist = 1 - Math.abs(i - MID) / (MID + 1.2);
    const wave = 0.32 + 0.68 * Math.abs(Math.sin(t + i * 0.62));
    const h = busy
      ? 0.22 + 0.42 * dist * Math.abs(Math.sin(t * 0.75 + i * 0.55))
      : 0.14 + smooth * wave * (0.45 + 0.55 * dist);
    el.style.transform = `scaleY(${Math.min(1, h).toFixed(3)})`;
  });
  level *= 0.90;
  requestAnimationFrame(frame);
}
frame();
