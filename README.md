# Yutori

> Was **SpendWise** until 2026-04-17; fully renamed to Yutori across code, packages (`com.yutori.*`), and docs (#8).

Android-only personal spending tracker that reads SMS from Indian banks and UPI apps, classifies each message, and derives a monthly budget view. All data stays on-device (Room). Side-loaded APK; no Play Store.

> **You don't want to spend less. You want to spend confidently.**

## Why "Yutori"

**Yutori** (余裕) is Japanese for "breathing room": financial margin, mental ease, room to spend without friction. The logo is the hiragana **ゆ** ("yu") rendered as negative space; the space itself is the design.

## Principles

Yutori is an opinionated app. A few load-bearing principles decide what ships and what doesn't.

**1. SMS-first, human-assist second.** Bank SMS is the source of truth. ATM withdrawals count as spend at withdrawal time. Manual entry stays light. No OCR, no email scraping.

**2. Margin, not micromanagement.** The central question is *"how much room do I have right now?"* Yutori doesn't moralise about spend. It surfaces impact: threshold crossings and single-transaction alerts. Awareness, not judgment.

**3. On-device by default.** You own your data. Yutori has no backend, no accounts, no analytics, no data collection.

**4. Opinionated about scope.** One question, answered well: personal monthly spend visibility from bank SMS. Features that don't serve "how much room do I have" are out.

**5. Side-loaded, open source.** Distributed via GitHub releases. No Play Store (fees don't fit a no-cost app). Updates land in-app via the built-in autoupdater. Code is public: audit it, fork it.

## Status

Personal project, single user. Active development. The `plans/` directory captures intent; work-in-progress is tracked in [GitHub issues](../../issues), labelled by tier and status.

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

## License

Released under the [GNU General Public License v3.0](LICENSE). You're free to use, modify, and redistribute — any fork or derivative must also be released under GPL-3.0. Commercial use is allowed under the same terms; a separate commercial licence can be negotiated with the author if those terms don't fit your use case.
