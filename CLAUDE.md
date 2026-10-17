# Working on Humming

- **Continue from TASKS.md / HANDOFF.md.** At the start of a session, read [TASKS.md](TASKS.md) and, if it exists,
  [HANDOFF.md](HANDOFF.md); pick up the first unchecked item and keep both files up to date as you go.
- No local builds on the maintainer's low-memory machine (no gradle, cargo build/test, Xcode, big npm installs):
  CI on GitHub Actions builds and tests everything. Say which changes are unverified until CI runs.
- Never commit secrets, signing keys, personal emails or phone numbers. Keep the Android applicationId, package
  names and upload signing key unchanged.
- Every number in the README or on the site must come from the repo (bench results, tests); competitor facts must be
  publicly documented or marked "unverified".
