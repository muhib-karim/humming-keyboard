# Handoff: v1.7.0-rc1 premium pass

Branch `feat/v1.7-premium`. Read [TASKS.md](TASKS.md) first; this file lists only what is left and how to continue.
Nothing was built locally (see CLAUDE.md); every Kotlin, Rust and Swift change is unverified until CI runs.

## Left to do (in order)

1. README.md premium rewrite (task 5).
2. Release plumbing for v1.7.0-rc1 (task 6): versions, CHANGELOG, assemble_release.py (-rc tags, per-platform report,
   loud failures, dry-run test), release.yml (one prerelease with every platform), desktop.yml review, YAML parse.
3. docs/ci/REPO_METADATA.md (task 7).
4. SKILLS_USED.md at the repo root, gitleaks + forbidden-strings scan, final report.

## Open items found on the way (not blocking)

- Smartbar mic button is 38 dp by default (a user setting); a 48 dp target needs a device check.
- Deferred review minors: see the "Final: minor (deferred)" lines summarised in SKILLS_USED.md.
- `docs/ci/ANDROID_REVIEW.md`: AGP 9 blockers (KSP version, `libraryVariants` in lib/snygg), recomposition from the
  voice level/timer, Play Console microphone FGS declaration.
