import { listen } from "@tauri-apps/api/event";

const bars = document.getElementById("bars")!;
const label = document.getElementById("label")!;
const N = 9;
for (let i = 0; i < N; i++) bars.appendChild(document.createElement("i"));
const els = Array.from(bars.children) as HTMLElement[];
let level = 0;

listen<number>("level", (e) => {
  // RMS of speech is ~0.02-0.2; map it to 0..1 on a log-ish curve.
  level = Math.min(1, Math.sqrt(e.payload * 8));
});
listen<{ state: string }>("status", (e) => {
  label.textContent = e.payload.state === "transcribing" ? "Transcribing" : "Listening";
  document.body.classList.toggle("busy", e.payload.state === "transcribing");
});

let t = 0;
function frame() {
  t += 0.18;
  els.forEach((el, i) => {
    const wave = 0.35 + 0.65 * Math.abs(Math.sin(t + i * 0.7));
    const h = document.body.classList.contains("busy") ? 0.25 + 0.2 * Math.abs(Math.sin(t * 0.6 + i)) : 0.12 + level * wave;
    el.style.transform = `scaleY(${Math.min(1, h).toFixed(3)})`;
  });
  level *= 0.92;
  requestAnimationFrame(frame);
}
frame();
