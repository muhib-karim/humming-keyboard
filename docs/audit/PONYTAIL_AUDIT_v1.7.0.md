# Ponytail audit of v1.7.0

Run 2026-10-17 on `main` at `e1bc3bb` (the v1.7.0 release commit) with the `ponytail-audit` skill, then the
`ponytail-debt` skill (section "Debt"). Nothing was built locally (see [CLAUDE.md](../../CLAUDE.md)); every fix below is
verified by GitHub Actions on the PR. Status column: **fixed** (commit in [SKILLS_USED.md](../../SKILLS_USED.md),
"v1.7.1 pass"), **owner** (needs access this repository does not give), **deferred** (one line of reasoning).

**What this repo does:** Humming is an offline-first keyboard and dictation app: an Android keyboard (a FlorisBoard
fork with on-device speech recognition), a Tauri desktop dictation app, and an iPhone app with a keyboard extension.
Releases are built, signed and published by GitHub Actions, and the apps update themselves from a feed mirrored to a
public website.

**Load assumed:** one person per device. The phone and desktop apps serve one user each; the only shared parts are
the release workflows (one tag at a time) and the static update feed (many readers, read-only).

## Must fix

1. **Android keeps every dictation's raw audio forever, with no way to see or delete it**
   (`keyboard/app/src/main/kotlin/com/humming/keyboard/History.kt:69-78`, `HummingVoice.kt:514`) — **fixed**
   - **What this is:** after each dictation (unless incognito), `HistoryStore.add` saves the text to `history.json` and
     the recorded audio (up to 300 s, about 9.6 MB of 16 kHz PCM) to `history_audio/<id>.pcm` in app storage.
   - **Problem:** nothing in the app ever reads this store: there is no history screen, no delete, and `prune()` (the
     retention rule) is never called, so audio piles up until the app is uninstalled or its data is cleared. The
     500-entry cap drops old rows from `history.json` but never their `.pcm` files, so those become unreachable
     orphans. `play/DATA_SAFETY.md` says history is "pruned at app and keyboard start", and Settings › Voice privacy
     promises "dictation history retention"; neither exists.
   - **Fix:** stop writing audio and delete the legacy `history_audio/` folder when the app starts; keep the small
     text log (500 rows, off in incognito) and make the Data safety text and the Settings summary say what really
     happens. Drop the unused `update`, `delete`, `audio` and `prune` methods.
   - **If we skip it:** a heavy dictation user carries gigabytes of private voice recordings they cannot see or
     remove, and the Play Data safety form is wrong.

2. **The release's live update-feed check fails on every final release** (`.github/workflows/pages.yml:70-83`) —
   **fixed**
   - **What this is:** after deploying the website, the workflow downloads `/updates/latest.json` and
     `/updates/android.json` from the live site and verifies the signatures.
   - **Problem:** it polls 6 times, 20 s apart, starting 2 s after the deploy. The first poll gets a 404 before
     the new files are live, and the Pages CDN keeps that 404 for its `cache-control: max-age=600` (the 404 was still
     served with `x-cache: HIT`, `age=414` seven minutes later, also with a query string added). All later polls get
     the cached 404, so the job fails. This is why the v1.7.0 release run is red
     ([run 39053406679](https://github.com/muhib-karim/humming-keyboard/actions/runs/39053406679), job
     `pages / deploy`, `HTTP Error 404`) although the deploy itself reported success.
   - **Fix:** poll for longer than the CDN's 10 minutes (40 × 20 s) and stop with a clear error if the file never
     appears. Loop count only; nothing else changes.
   - **If we skip it:** every release ends red, so a real feed failure looks the same as this false alarm.

3. **Installed apps are not offered 1.7.0** (feed host `humming-keyboard.pages.dev`, read by `AppUpdate.kt:96` and
   `tauri.conf.json` `plugins.updater.endpoints`) — **owner**
   - **What this is:** the Android app and the desktop app read their update feeds from Cloudflare Pages
     (`humming-keyboard.pages.dev/updates/`), not from the GitHub Pages site this repository deploys.
   - **Problem:** on 2026-10-17 at 17:44 UTC that host served desktop `latest.json` version 1.6.0 and Android
     `android.json` versionCode 1602 (1.6.2), checked with `curl`. v1.7.0 is not mirrored there, so no installed app
     will update. How pages.dev is synced is not in this repository (docs/RELEASING.md section 6 describes a manual
     mirror; also in [HANDOFF.md](../../HANDOFF.md)).
   - **Fix:** the release owner copies the v1.7.0 `updates/` files to the Cloudflare Pages project (or connects it
     to this repository's site). No code change.
   - **If we skip it:** every 1.6.x user stays on 1.6.x.

## Should fix

4. **A failed microphone start leaves the Bluetooth call-audio route on**
   (`keyboard/app/src/main/kotlin/com/humming/keyboard/WavRecorder.kt:118-125`) — **fixed**
   - **What this is:** with a Bluetooth headset and "Bluetooth headset mic" on, `start()` switches the phone to the
     headset's call-audio path (`setCommunicationDevice` or Bluetooth SCO) before it starts recording.
   - **Problem:** if `startRecording()` then throws, `start()` releases the recorder and returns false but never
     calls `bt.end()`, so the phone stays in call-audio mode (music plays through the low-quality call profile)
     until the next successful dictation ends.
   - **Fix:** end the route in that `catch` (two lines).
   - **If we skip it:** after a mic error the headset sounds broken until the user dictates again or reconnects it.

## Nice to have (deferred)

5. **Desktop clipboard restore loses a copied image** (`desktop/src-tauri/src/insert.rs:38`) — **deferred**: the
   restore only ever promised text (`get_text`); restoring images needs `get_image`/`set_image` per platform and
   cannot be tested on CI runners without a display. Not a regression.
6. **`wer-bench.yml` puts `workflow_dispatch` inputs straight into a shell line** (`wer-bench.yml:62`) —
   **deferred**: only people with write access can dispatch it, and they can already edit the workflow; no secrets
   are in scope (`permissions: contents: read`).
7. **Streaming session could reject a late segment** (`StreamingSession.kt:52-55`) — **deferred**: needs the
   recorder's `read()` to block for more than the 1 s `join` in `WavRecorder.stop()`; the default Silero
   segmenter already ignores audio after `release()`. No observed crash.
8. **Release hardening** (`release.yml`: `secrets: inherit`, actions pinned by tag, not SHA) — **deferred**:
   already in HANDOFF.md as an owner decision; no exploit path without write access.
9. **Restoring a backup keeps the old keyboard background file** (`HummingBackup.kt:47`) — **deferred**: a few
   hundred KB of disk; the restored setting points at the restored file, so behaviour is right.

Smaller items left out: 2 (Node 20 deprecation warnings on `actions/*@v4`, already forced to Node 24 by the runner;
`dataExtractionRules` reuses the `full-backup-content` file, which could not be checked without a device).

**Verdict:** fix 1 (privacy) and 2 (red release) first; 3 is the release owner's job today.

**Lean:** about -40 lines possible (finding 1 deletes the unused history methods and the audio path).

**Not checked:** the FlorisBoard upstream code (`dev.patrickgold.florisboard`, `org.florisboard.lib`) beyond the
Humming hooks; `core/` (the shared Rust library, covered by its own tests and earlier audits); the prediction
engine (`predict/`); the Compose UI files beyond the voice panel's state machine; the site HTML. Nothing was run
locally: no Gradle, cargo or Swift.

## Debt

`ponytail-debt` scan, 2026-10-17, `main` at `e1bc3bb`:
`git grep --no-index --exclude-standard -nE '(#|//|/[*]|<!--) ?(shortcut|ponytail):'` over the whole tree.

No shortcut debt. Clean ledger.

0 markers, 0 with no trigger. A wider scan for `TODO`/`FIXME`/`HACK`/`XXX` comments in Humming's own code (`core/`,
`desktop/src`, `desktop/src-tauri/src`, `ios/`, `scripts/`, `.github/`, `keyboard/.../com/humming`) also found none.
The FlorisBoard upstream code carries its own TODOs; they are upstream's and were not ledgered here. Deferred audit
findings 5-9 above are this pass's only recorded debt.
