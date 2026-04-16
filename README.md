# SpendWise

> Package name — will be renamed **Yutori** across user-facing surfaces. Rename tracked in issue #8.

Android-only personal spending tracker that reads SMS from Indian banks and UPI apps, classifies each message, and derives a monthly budget view. All data stays on-device (Room). Side-loaded APK; no Play Store.

## Status

Personal project, single user. Active development. The `plans/` directory captures intent; work-in-progress is tracked in [GitHub issues](../../issues) — `plans/backlog.md` is a thin tiered index.

## Build

JDK 17, Android SDK 34. From `android/`:

```bash
./gradlew :app:assembleDebug                 # debug APK
./gradlew :app:installDebug                  # install on connected device
./gradlew :parser:test :classifier:test \
          :ingestion:test :app:testDebug \
          :database:test :transactions:test \
          :budget:test                       # full test suite
```

See `CLAUDE.md` for module graph, behavioural invariants, and workflow conventions.

## Releasing

Tag-push (`v*`) builds and uploads a signed APK via GitHub Actions. Setup + keystore details: [`docs/RELEASING.md`](docs/RELEASING.md).

`versionCode` and `versionName` are derived at build time — no manual bumps. Cut a release with just `git tag v0.2.0 && git push --tags`.

## Commit conventions

Use conventional-commit prefixes so git-cliff can group them into release notes:

- `feat:` — user-facing feature (incl. copy / help text / labels)
- `fix:` — user-facing bug fix (incl. wrong copy)
- `perf:` — performance improvement
- `refactor:` — non-behavioural code change
- `test:` — test-only change
- `docs:` — repo-internal documentation only (README, CLAUDE.md, plans/, docs/)
- `chore:`, `style:`, `ci:` — internal

`docs:` is reserved for documentation *of the codebase*. User-facing copy (help text, About, onboarding, buttons, errors, notifications) is product surface and ships as `feat:`/`fix:`.

Subject line is short and imperative; body (optional) explains *why*. See `cliff.toml` for the bucketing rules and the fallbacks for pre-2026-04-16 un-prefixed history.

## Privacy

The repo is sanitised — no real SMS bodies, account numbers, names, employer or UPI handles. Test fixtures use synthetic placeholders. Real data lives only in the on-device Room database.
