## Summary

<!-- What does this change, and why? Link the issue it closes, if any (e.g. "Closes #12"). -->

## Platforms affected

- [ ] Android (`keyboard/`)
- [ ] Desktop (`desktop/`, `core/`)
- [ ] iOS (`ios/`)
- [ ] Website, documentation or release tooling

## How it was tested

<!-- Commands run and devices or emulators used. -->

- [ ] Android: `./gradlew :app:testDebugUnitTest`
- [ ] Desktop: `npm run build` and `cargo test`
- [ ] Core: `cargo test --no-default-features --lib`
- [ ] iOS: `swift test` in `ios/HummingCore`

## Screenshots

<!-- For UI changes: before and after. -->

## Checklist

- [ ] No new network use outside the files allowed by `NetworkAuditTest`, or it is opt-in, off by default and
      reflected in the privacy policy
- [ ] Clean-up rule changes are made on every platform they apply to (Kotlin, Rust, Swift)
- [ ] FlorisBoard licence headers kept; notable changes to upstream files listed in `keyboard/NOTICE-HUMMING.md`
- [ ] `CHANGELOG.md` updated for user-visible changes
- [ ] Documentation updated (README, `docs/`)
- [ ] No secrets, keys or personal data in the diff
