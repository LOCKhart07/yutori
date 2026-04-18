#!/usr/bin/env bash
# Bootstrap all labels used by .github/workflows/triage-issue.yml.
#
# Idempotent — safe to re-run. Requires `gh` authenticated with
# write access to the target repo.
#
# Usage:  scripts/bootstrap-triage-labels.sh
#         GH_REPO=other-owner/other-repo scripts/bootstrap-triage-labels.sh

set -euo pipefail

REPO="${GH_REPO:-LOCKhart07/yutori}"

upsert() {
  local name=$1 color=$2 desc=$3
  gh label create "$name" \
      --repo "$REPO" \
      --color "$color" \
      --description "$desc" \
      --force
}

# Triage family — only applied when Copilot flags the issue as
# invalid/spam. Never combined with a kind/* label.
upsert "triage/invalid"         "b60205" "Issue flagged as invalid by automated triage."
upsert "triage/spam"            "b60205" "Spam — promotional, gibberish, or off-topic."

# Kind — exactly one applied per valid issue.
upsert "kind/bug"               "d73a4a" "Something is broken."
upsert "kind/feature"           "a2eeef" "New user-visible functionality."
upsert "kind/question"          "cc317c" "A question about Yutori."
upsert "kind/docs"              "0075ca" "User-facing docs — About screen, onboarding copy, README."
upsert "kind/tech-debt"         "fef2c0" "Internal code quality — refactor, tests, lint, dep bumps."
upsert "kind/meta"              "c5def5" "Repo tooling that affects shipping: CI, release, build, signing."

# Module — up to two applied per valid issue.
upsert "module/parser"          "1d76db" "SMS -> ParseResult regex rules."
upsert "module/classifier"      "1d76db" "Classification + dedup logic."
upsert "module/budget"          "1d76db" "Monthly budget math + alerts."
upsert "module/transactions"    "1d76db" "sms_log -> transactions mapping + FX."
upsert "module/ingestion"       "1d76db" "Live SMS receiver + historical import worker."
upsert "module/database"        "1d76db" "Room entities, DAOs, migrations."
upsert "module/app-ui"          "1d76db" "Compose UI, ViewModels, notifications, backup."
upsert "module/build"           "1d76db" "Gradle, CI, release, signing, autoupdater."

# Tier — only on kind/bug or kind/feature.
upsert "tier/P0"                "b60205" "Blocker: data loss, crash, wrong totals."
upsert "tier/P1"                "fbca04" "User-visible regression or high-value feature."
upsert "tier/P2"                "0e8a16" "Nice to have."

# Blocker — one of these, or status/ready.
upsert "blocker/needs-repro"    "d4c5f9" "Bug report lacks reproduction steps."
upsert "blocker/needs-mockup"   "d4c5f9" "UI change — needs a mockup before coding."
upsert "blocker/needs-decision" "d4c5f9" "Hinges on an unresolved product question."
upsert "blocker/duplicate"      "cfd3d7" "Duplicates an existing open issue."
upsert "status/ready"           "0e8a16" "Well-specified — ready for a maintainer to pick up."

echo
echo "Done. Verify: gh label list --repo $REPO --limit 200"
