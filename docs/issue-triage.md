# Issue triage workflow

A GitHub Actions workflow (`.github/workflows/triage-issue.yml`, not
yet implemented — this doc is the spec) that runs the **GitHub Copilot
CLI** on every newly opened issue, classifies it against a fixed
taxonomy, and posts a structured triage comment plus labels. Designed
for a public repo with an untrusted issue intake.

A human still owns every issue. The workflow only *shapes* the
starting point — it never closes, assigns, or merges.

## Trigger

```yaml
on:
  issues:
    types: [opened]
```

Nothing else — not `edited`, not `labeled`, not `issue_comment`. Comment
triggers are out of scope for v1 (see *Open decisions §1*).

## Step-by-step

1. `issues.opened` fires.
2. `actions/checkout` pulls the repo at HEAD — the workflow needs
   `CLAUDE.md` and `plans/` to ground Copilot's module/spec choices.
3. A short bash step writes **issue metadata** to
   `$RUNNER_TEMP/issue.json`:
   ```json
   { "number": 42, "title": "...", "body": "...", "author": "..." }
   ```
   `body` is truncated to 8 KB. `title` + `body` are written via
   `jq -n --arg ...` so no shell interpolation touches the raw text.
4. A second bash step writes **open-issue context** to
   `$RUNNER_TEMP/open-issues.json` — `[{number, title, labels[]}]` —
   fetched via `gh api /repos/{repo}/issues?state=open&per_page=100`.
   This is the only data Copilot sees for duplicate detection.
5. A third bash step writes **recent release context** to
   `$RUNNER_TEMP/recent-releases.json` — `[{tag_name, name, body}]` —
   fetched via `gh api /repos/{repo}/releases?per_page=5`, with
   release `body` truncated before prompt assembly to keep context
   bounded.
6. `npm install -g @github/copilot@<pinned-version>` installs the CLI.
7. Copilot runs with the **static prompt** at
   `.github/triage-prompt.md` and the three data files, with tools
   gated to the minimum:
   ```
   copilot -p "$(cat .github/triage-prompt.md)" \
           --allow-tool='read(./**)' \
           --allow-tool=write \
           --no-ask-user
   ```
   Output goes to `$RUNNER_TEMP/triage.json`. No shell, no network,
   no `gh` tool exposure. Copilot is a text transformer in this
   pipeline — nothing more.
8. A `jq`-based validator rejects the run if:
   - output is not valid JSON,
   - required fields are missing,
   - any enum value is outside its allow-list,
   - `duplicate_of` points to an issue that doesn't exist.
   Unknown fields are ignored; unknown enum values blank the field
   rather than fail the whole run.
9. A bash step applies labels and posts the comment using `gh api`,
   hard-coded to `github.event.issue.number`. Each label is added
   only if it already exists in the repo (see *Label taxonomy*) —
   unknown labels are never auto-created from Copilot output.
10. The comment carries a hidden marker:
   ```
   <!-- yutori-triage:v1 -->
   ```
   so re-runs delete-and-repost instead of stacking.

## Copilot output shape

Exactly one JSON object. Nothing else — no prose, no markdown fences.
The prompt is explicit about this.

```json
{
  "valid":         true,
  "invalid_reason": null,
  "kind":          "bug",
  "modules":       ["parser", "classifier"],
  "spec_refs":    ["plans/parser-spec.md", "plans/yutori-plan.md#4"],
  "tier":          "P1",
  "blocker":       "needs-repro",
  "duplicate_of":  null,
  "summary":       "Two-sentence restatement of the ask.",
  "next_step":     "One-sentence suggestion for what unblocks this."
}
```

### Enums (strictly validated)

| Field | Allowed values |
| --- | --- |
| `valid` | `true` / `false` |
| `kind` | `bug`, `feature`, `question`, `docs`, `tech-debt`, `meta` |
| `modules` | subset of `parser`, `classifier`, `budget`, `transactions`, `ingestion`, `database`, `app-ui`, `build` |
| `tier` | `P0`, `P1`, `P2`, or `null` |
| `blocker` | `ready`, `needs-repro`, `needs-mockup`, `needs-decision`, `duplicate`, `invalid` |
| `spec_refs` | free-form strings, capped at 5 entries, each ≤ 80 chars |
| `summary` / `next_step` | free-form, each capped at 280 chars; stripped of backticks and HTML |

`kind: meta` covers repo tooling that affects *shipping* — CI,
release, build, signing, autoupdater infra. `kind: tech-debt` is
internal code quality — refactors, test infra, lint setup, dep bumps
— that doesn't change user-visible behaviour. `kind: docs` means
user-facing docs (README, About screen) — not repo-internal.

## Label taxonomy

The workflow applies at most one label from each family. All labels must
already exist in the repo — bootstrap once with:

```bash
# Run locally on first setup. See "Bootstrapping the labels" below.
scripts/bootstrap-triage-labels.sh
```

Families:

| Family | Labels | Applied when |
| --- | --- | --- |
| triage  | `triage/invalid`, `triage/spam` | `valid: false` |
| kind    | `kind/bug`, `kind/feature`, `kind/question`, `kind/docs`, `kind/tech-debt`, `kind/meta` | `valid: true` |
| module  | `module/parser`, `module/classifier`, `module/budget`, `module/transactions`, `module/ingestion`, `module/database`, `module/app-ui`, `module/build` | one per entry in `modules`, max 2 |
| tier    | `tier/P0`, `tier/P1`, `tier/P2` | only for `kind ∈ {bug, feature}` |
| blocker | `blocker/needs-repro`, `blocker/needs-mockup`, `blocker/needs-decision`, `blocker/duplicate`, `status/ready` | from `blocker` |

Rationale for a separate `triage/*` family: `valid: false` short-circuits
everything else. We never want a spam issue to carry `kind/bug +
tier/P0` because Copilot misread it.

## Permissions & secrets

```yaml
permissions:
  contents: read          # checkout, read plans/ and CLAUDE.md
  issues:   write         # label + comment on the triggering issue
  # everything else defaults to none
```

GitHub tokens are repo-scoped, not issue-scoped — there is no way
to constrain `issues: write` to a single issue via the token. The
per-issue guarantee is enforced by **workflow code**, not by the
token: every `gh api` call hard-codes `${{ github.event.issue.number }}`
and never reads an issue number from Copilot output. The
`duplicate_of` field from Copilot is used only to *read* the
referenced issue (to verify it exists before linking in the comment)
— it is never used as a write target.

Secrets:

| Secret | Contents | Purpose |
| --- | --- | --- |
| `COPILOT_GITHUB_TOKEN` | **Fine-grained** PAT, generated on a **personal account** (not an organisation), with **Copilot Requests** permission enabled. The PAT owner must hold a valid Copilot license (Pro / Business / Enterprise — docs don't narrow further). | Authenticates Copilot CLI to a Copilot license. A GitHub App token does *not* work — the CLI requires a user PAT. |

Three gotchas that bit people before:

1. **Precedence.** Copilot CLI reads `COPILOT_GITHUB_TOKEN`, then
   `GH_TOKEN`, then `GITHUB_TOKEN`. If we only set `GITHUB_TOKEN`
   (the built-in one), the CLI silently picks it up and fails with
   an auth error — `GITHUB_TOKEN` has no Copilot access. The
   workflow must set `COPILOT_GITHUB_TOKEN` *explicitly* on the
   Copilot step only.
2. **Fine-grained only.** Classic PATs do not expose the "Copilot
   Requests" scope. Must be a fine-grained token.
3. **Personal account only.** The "Copilot Requests" permission
   is not available on org-owned fine-grained tokens at time of
   writing. Since `LOCKhart07/yutori` is a personal repo, the PAT
   is generated under `LOCKhart07` — the same account the repo sits
   under.
4. **UI gating on Repository access.** The "Copilot Requests"
   checkbox only appears in the PAT editor once *Repository access*
   is set to `Public repositories` (or `All repositories`). This is
   a UI quirk, not a functional limit — `copilot_requests` is a
   user-scoped permission (authorises calls to Copilot's API on
   behalf of the PAT owner, counts against the owner's premium-request
   allowance), and the token never hits `yutori`'s repo API in our
   workflow. `GITHUB_TOKEN` handles everything repo-scoped;
   `COPILOT_GITHUB_TOKEN` exists solely to authenticate the Copilot
   CLI. Setting *Repository access* = `Public repositories`,
   *Repository permissions* = none, and *Account permissions* →
   `Copilot Requests` = Read+Write yields a minimal-blast-radius
   token that works against a private repo.

`GITHUB_TOKEN` (built-in, per-run) is still used for all `gh api`
calls against the repo (comments, labels). `COPILOT_GITHUB_TOKEN` is
scoped exclusively to the Copilot CLI step and never passed to any
other step.

## Prompt-injection hardening

The repo is (going) public. Every issue body is hostile input. The
baseline assumption: a submitter *will* try to get the workflow to
close every issue, slap a `tier/P0` on a cosmetic change, or mis-flag
an issue as spam.

Mitigations, layered:

1. **Prompt is static.** `.github/triage-prompt.md` is committed and
   reviewed. It is never interpolated with issue content. Issue data
   reaches Copilot only as a *separate file* the prompt refers to by
   path — clearly framed as untrusted input.
2. **Copilot's output is advisory.** The workflow ignores anything
   outside the JSON shape. No enum value → no label. Prose in the
   "summary" and "next_step" fields is sanitised (backticks + HTML
   stripped) before being embedded in the comment.
3. **No tool surface beyond text.** `--allow-tool` grants read on
   `$RUNNER_TEMP/*.json` and write on its output file. No `shell`,
   no `gh`, no network. Copilot cannot exfiltrate or mutate.
4. **No write uses Copilot-supplied identifiers.** All `gh api`
   writes hard-code `github.event.issue.number`. `duplicate_of` is
   only used for *read*/verify and for rendering a link in the
   comment body.
5. **Body size cap.** 8 KB ceiling on `body` field. A 1 MB issue body
   cannot drain quota or bury the prompt.
6. **Author filter.** Job-level `if:
   github.event.issue.user.type != 'Bot'`. Bot-posted issues skip
   triage entirely; a human can re-trigger via `/triage` if the
   command trigger ships.
7. **Concurrency lock.** `concurrency: group: triage-${{
   github.event.issue.number }}, cancel-in-progress: false` — one
   triage per issue at a time.
8. **Rate limit guard.** If more than N triage runs fire within a
   rolling window (e.g. 20 in 1 h), the workflow short-circuits with
   a notice instead of burning Copilot quota. Implementation TBD —
   see *Open decisions §3*.

## Pinning

- `actions/checkout@<40-char-SHA>` — pinned, not `@v4`.
- `actions/setup-node@<40-char-SHA>` — pinned.
- `npm install -g @github/copilot@X.Y.Z` — pin to a specific npm
  version. `@github/copilot` is preview / unstable; a new minor can
  change CLI flag names. Bump the version in a dedicated commit so a
  regression bisects cleanly.
- No third-party action for Copilot — it's an npm install, so there's
  no marketplace SHA to pin.

## Failure modes

| Failure | Behaviour |
| --- | --- |
| Copilot CLI non-zero exit | Post fallback comment with the `<!-- yutori-triage:v1 -->` marker: *"Automated triage failed — a human will look at this shortly."* No labels applied. |
| Output not valid JSON | Same as above. |
| JSON valid but schema-invalid | Same as above. CI log records which field failed. |
| Label doesn't exist in repo | Skip that one label, apply the rest. Emit a `::warning` — bootstrap script needs to be re-run. |
| `duplicate_of` resolves to a closed or missing issue | Drop the field. Fall back to the non-duplicate blocker. |
| Quota exhausted | Copilot CLI surfaces a specific error — workflow posts: *"Triage skipped — Copilot quota exhausted."* |

Every failure still posts *something* on the issue, so a human seeing
a just-opened issue with no triage comment knows the workflow didn't
even start (bad secret, permissions missing, etc.).

## Bootstrapping the labels

A one-shot script at `scripts/bootstrap-triage-labels.sh` creates every
label in the *Label taxonomy* table. Run once after the workflow lands;
re-run after adding a new label to the taxonomy. Script is idempotent
(`gh api PUT` upsert; colour + description declared inline).

## Re-run and idempotency

Re-triggering on the same issue (via `workflow: rerun` or future
`/triage` command): the workflow finds the previous comment via the
`<!-- yutori-triage:v1 -->` marker, deletes it, removes *only* the
labels from families this new run will set (so stale labels don't
linger), then posts fresh.

The marker is versioned — `v1` today. A breaking change to the shape
bumps to `v2`; old comments remain untouched so history is legible.

## Out of scope for v1

All five were considered and deliberately left out. Revisit each if
real-world behaviour demands it.

1. **`/triage` re-run on comments.** No `issue_comment` trigger. If
   a submitter adds a repro later, a maintainer re-runs the workflow
   manually. Revisit after a month of v1 in the wild.
2. **Auto-close on `valid: false`.** Spam/invalid issues get labelled
   and commented, never auto-closed. A human eyeballs every one to
   catch false positives.
3. **Rate-limit circuit breaker.** No "N triages per hour" guard. The
   `concurrency` lock + the Copilot CLI's own quota error are the
   only brakes. Revisit if a spam flood actually burns through quota.
4. **Spec-refs lint.** The validator does not check that
   `spec_refs` entries resolve to real files or anchors. A typo from
   Copilot renders as a broken link in the comment; the human reading
   the triage catches it on first glance.
5. **Failure-notification escalation.** Each Copilot failure posts a
   "triage failed" comment on its own issue, nothing else. No meta-issue
   is opened after N consecutive failures. On a low-traffic repo a
   broken PAT or CLI regression surfaces within a day anyway.

## Implementation checklist

- [ ] `scripts/bootstrap-triage-labels.sh` — label upsert.
- [ ] `.github/triage-prompt.md` — the static prompt; defines the
      JSON shape and the taxonomy Copilot must map to.
- [ ] `.github/workflows/triage-issue.yml` — the workflow itself.
- [ ] `COPILOT_GITHUB_TOKEN` secret added to the repo. It is now the
      only PAT the project uses; rotate annually (see
      `docs/RELEASING.md` → *CI tokens*).
- [ ] Run the label bootstrap.
- [ ] Open a synthetic test issue and a deliberately adversarial one
      (prompt-injection attempt, 1 MB body, UTF-8 edge cases) and
      confirm behaviour.
- [ ] Document in `CLAUDE.md` that new issues get auto-triaged, so
      future-Claude doesn't duplicate the work.
