import { defineConfig } from "vite";

// Two pages: the main window (settings, history, dictionary) and the dictation overlay.
export default defineConfig({
  clearScreen: false,
  server: { port: 1420, strictPort: true },
  build: {
    target: "es2021",
    rollupOptions: {
      input: { main: "index.html", overlay: "overlay.html" },
    },
  },
});
