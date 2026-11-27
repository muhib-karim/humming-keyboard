---
name: humming-competitors
description: Audit competing keyboards, dictation apps, or meeting notepads and update COMPETITORS.md, README.md, or site comparison tables. Use only for competitive analysis and comparison matrix updates.
---

# Humming Competitor Audit Discipline

1. **Primary sources only**: verify every competitor claim from official product docs, privacy/data-control pages, open-source MIT/Apache repos, or live release manifests/feeds. Record exact URLs and check dates in `COMPETITORS.md`.
2. **Never extrapolate**: if a vendor publishes no WER, latency, or installer size, write `"Not published"` or `"Closed installer"` — never estimate. Mark unverified claims explicitly as `unverified`.
3. **Honest benchmark caveats**: state clearly when published WER numbers come from different test sets (e.g., LibriSpeech `test-clean` vs vendor multi-domain sets) so tables never imply invalid apples-to-apples rankings.
4. **Maintain gap honesty**: keep "Where Humming leads" and "Where Humming still trails" synchronized with actual committed code across `desktop/`, `keyboard/`, and `ios/`.
