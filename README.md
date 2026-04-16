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

## Privacy

The repo is sanitised — no real SMS bodies, account numbers, names, employer or UPI handles. Test fixtures use synthetic placeholders. Real data lives only in the on-device Room database.
