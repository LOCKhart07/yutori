# Contributing to Yutori

## Commit messages

Yutori uses conventional-commit prefixes on every commit. The prefix
decides how the commit shows up in the release body (`cliff.toml` bucketing) and whether it drives a version bump (`docs/RELEASING.md`). Short imperative subject line; optional body for the *why*.

### Allowed prefixes

| Prefix      | For |
| ----------- | --- |
| `feat:`     | New user-visible feature, screen, flow, or copy. |
| `fix:`      | Bug fix in user-visible behaviour or copy. |
| `perf:`     | Performance improvement with no behaviour change. |
| `refactor:` | Internal reshape with no behaviour change. |
| `test:`     | Test-only changes — add, strengthen, or fix tests. |
| `docs:`     | Documentation *of the codebase* — `README.md`, `CLAUDE.md`, `docs/**`, `plans/**`, `mockups/**`, any `*.md`. |
| `ci:`       | CI workflows, release tooling, repo automation — `.github/**`, `cliff.toml`, `scripts/ci*`. |
| `chore:`    | Dotfiles, dependency bumps, misc. housekeeping that doesn't fit elsewhere. |
| `style:`    | Formatting, whitespace, import re-ordering — no semantic change. |

### Path → prefix guide

A commit's prefix should reflect **what files it touches**, not what
the work *feels* like. If the files are 100% in one bucket, the
prefix is forced:

| Files touched are 100% in …                                                       | Prefix must be |
| --------------------------------------------------------------------------------- | -------------- |
| `.github/**`, `cliff.toml`, `scripts/ci*`                                         | `ci:`          |
| `README.md`, `CLAUDE.md`, `docs/**`, `plans/**`, `mockups/**`, any `*.md`         | `docs:`        |
| `**/src/test/**`, `**/src/androidTest/**`, `**/src/testDebug/**`                  | `test:`        |
| Kotlin / XML under `android/**/src/main/**`                                       | `feat:` / `fix:` / `perf:` / `refactor:` (pick by intent) |

Mixed commits are fine: a `feat:` that also bumps `plans/backlog.md`
is correct — the doc edit is incidental to the feature. Prefix the
commit for the **app-code change**; the trailing doc/CI edits ride
along. Don't split unless the two halves are independently shippable.

### `docs:` is repo-internal only

`docs:` is reserved for documentation *of the codebase* — README,
CLAUDE.md, specs, design notes. **User-facing copy** (help text,
button labels, error messages, notification strings, onboarding
prompts, About screen) is product surface and ships as `feat:` (new
copy) or `fix:` (wrong copy). Release notes always collapse the
`Documentation` bucket because — by this rule — it's repo noise the
end user doesn't care about.

### Why this matters

1. **Version bumps** — `feat:` drives a minor bump per
   `docs/RELEASING.md`; every other prefix is a patch. A misprefixed
   `feat:` on a CI-only change ships the wrong version number. See
   `git log v0.6.1..v0.6.2` for the rebase that corrected this once.
2. **Release notes** — `git-cliff` reads the prefix on every commit
   to bucket entries in the release body. A `feat:` on a CI change
   surfaces that line as a user-visible feature in the next release.
3. **Signal integrity** — the prefix *is* the commit's self-description.
   When the prefix lies, `git log --oneline` lies, the release body
   lies, and semver lies downstream of that.

## Other conventions

- **Stage by path, not `git add -A`.** A pre-commit hook blocks `-A`
  in this repo; stage the files you mean to commit.
- **PII in commits.** Real names, account last-4s, employer names,
  UPI handles, phone numbers, and verbatim SMS bodies must never land
  in committed source (tests, fixtures, plans, mockups). Use
  synthetic placeholders — see existing patterns in
  `android/parser/src/test/kotlin/.../*Test.kt`.
- **UI changes need a mockup first.** Any visible-to-user UI change
  (new screen, new banner, restyle of an existing surface) should be
  presented as a mockup — HTML in `mockups/`, screenshot, or
  annotated description — and approved before code is written.
  Pure-logic changes (DAO, classifier rules, ViewModel internals)
  don't need mockup approval.

For release-time steps (picking the version number, cutting a tag,
token management) see `docs/RELEASING.md`.
