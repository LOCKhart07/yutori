# Copilot coding agent

GitHub Copilot coding agent is auto-assigned to eligible bug issues
after triage. It opens a PR; the PR is gated by unit tests and reviewed
by a human before merge.

Scope:

- `.github/copilot-instructions.md` — instructions Copilot reads on
  every PR (spec-first, proof-of-work, unit tests, invariants).
- `.github/workflows/triage-issue.yml` — the triage workflow, extended
  with a final step that assigns Copilot when the gate below holds.
- `.github/workflows/gate.yml` — reusable detekt + unit-test workflow.
- `.github/workflows/pr-checks.yml` — runs `gate.yml` on every PR.
- `.github/workflows/release.yml` — calls `gate.yml` before building
  the release APK.
- `.github/workflows/main.yml` — runs `gate.yml` on pushes to `main` to
  keep default-branch Gradle caches warm.

## Gate — when Copilot is auto-assigned

All of the following must hold on the validated triage output:

| Condition                                                  | Rationale                                                        |
| ---                                                        | ---                                                              |
| `valid == true`                                            | Spam / malformed issues never trigger anything.                  |
| `kind == "bug"`                                            | Features need product judgement Copilot doesn't have.            |
| `blocker == "ready"`                                       | `needs-repro` / `needs-mockup` / `needs-decision` require human. |
| `tier ∈ {P1, P2}`                                          | P0 bugs are money-loss / crash-rate — humans handle those.      |
| `modules ∩ {parser, classifier, budget} == ∅`              | §4.2 dedup and §12 invariants are easy to break silently.       |
| Issue has no existing assignees at triage time             | Don't wipe a human who claimed the issue.                        |

If all hold, the workflow resolves the `copilot-swe-agent` Bot's node id
via the `suggestedActors` GraphQL connection and calls
`replaceActorsForAssignable` to assign it.

## Assignment mechanism

GraphQL, not REST. REST `/issues/{n}/assignees` can reject Bot logins.

```graphql
query($owner: String!, $name: String!) {
  repository(owner: $owner, name: $name) {
    suggestedActors(
      capabilities: [CAN_BE_ASSIGNED]
      first: 100
      loginNames: ["copilot-swe-agent"]
    ) {
      nodes { ... on Bot { id login } }
    }
  }
}
```

```graphql
mutation($assignable: ID!, $actor: ID!) {
  replaceActorsForAssignable(input: {
    assignableId: $assignable
    actorIds: [$actor]
  }) {
    clientMutationId
  }
}
```

Uses the built-in `GITHUB_TOKEN` (`issues: write` is already in
`triage-issue.yml`'s `permissions:` block). No new secret required.

## What Copilot must do (proof-of-work, unit tests)

Full rules live in `.github/copilot-instructions.md`. Highlights:

- **Spec first.** PR description has `## Problem` / `## Approach` /
  `## Files changed` / `## Out of scope` sections before any code is
  written.
- **`## Proof` section is mandatory.** UI → screenshot or link to
  `mockups/` HTML. Logic-only → fenced code block with test output.
- **Unit tests wherever possible.** Added logic needs a test. Pure-JVM
  modules have no excuse; Robolectric tests run for `:app`. Justify any
  skip in the PR description.
- **Invariants.** Save-everything, double-counting, self-transfers,
  §4.2 dedup, alert single-fire, refund month, paise, no PII.

Proof and spec are *not* enforced by CI — the human reviewer enforces
them. That's a deliberate choice: a soft red X buys nothing over a
reviewer who's already going to read the PR.

## Tests as the only hard gate

`.github/workflows/pr-checks.yml` runs the same gradle command that
gates every release:

```
./gradlew :parser:test :classifier:test :budget:test :transactions:test \
          :ingestion:test :database:test :app:testDebugUnitTest \
          --parallel --build-cache --stacktrace
```

To make it required before merge: **Settings → Branches → `main` →
Require status checks before merging → tick `gate / detekt` and
`gate / test`** (from `pr-checks.yml`). Do this after the first green
run so the check names are registered with GitHub.

Instrumentation tests (`:database:connectedAndroidTest`) are excluded
— they need an emulator and are too slow/flaky for every PR. Release
doesn't run them either; they execute locally when a human invokes
`./gradlew :database:connectedAndroidTest` on a device or emulator.

## Failure modes

| Failure                                                    | Behaviour                                                                   |
| ---                                                        | ---                                                                         |
| Tests fail on PR                                           | `pr-checks.yml` red. Blocks merge once branch protection requires `test`.   |
| Copilot bot not in `suggestedActors`                       | `::warning::` in triage run log, triage otherwise succeeds. Assign by hand. |
| `replaceActorsForAssignable` fails (permissions, network)  | `::warning::` in triage log. Labels + triage comment still posted.          |
| Issue already has an assignee at triage time               | Skip with a log message. No mutation.                                       |
| Re-triggered triage on an issue Copilot already handles    | Skip (Copilot is in `assignees`). No-op.                                    |
| Copilot opens a bad PR                                     | Caught by `pr-checks.yml` (tests fail) or by human review. Close / revise.  |

Any failure in the auto-assign step is soft — it warns, exits 0, and
lets the rest of the triage stand. A human assigns manually.

## Out of scope for v1

- **Proof-of-work CI.** A regex-grep over the PR body is trivial but
  was deliberately not added. The human reviewer enforces the
  `## Proof` rule; adding CI theatre on top would only add red X
  noise.
- **Unit-test enforcement beyond the suite running.** The suite must
  pass; "did you add a test for the new logic" is a human-review call.
- **Auto-assignment of non-bug issues.** Features, tech-debt, and
  meta issues need product / architectural judgement up front.
  Copilot can be assigned manually.
- **PRs opened outside the triage flow.** Only newly opened issues
  that clear the gate get Copilot assigned. Existing issues, or
  issues re-triaged after the first run, are manual.

## Changes to the gate

If the gate needs widening (e.g. include `kind/feature`, or drop the
`:parser` / `:classifier` / `:budget` exclusion), edit the step in
`.github/workflows/triage-issue.yml` — the logic is in-line, not a
config file. Keep this doc in sync.
