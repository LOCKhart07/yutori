# Security

Operational notes for keeping the repo + signing keys safe. Most of this
matters only if the repo is ever flipped from private to public.

## Pre-public-flip checklist

Before changing the repo visibility from private to public, walk every
item in this list. Skipping any of them can leak PII or grant an
attacker update access to devices that have the app installed.

### 1. Audit history for PII

Grep every historical diff, not just `HEAD`. Anything that was real
(amounts, last-4s, UPI handles, merchant strings, spending totals,
account labels, SMS IDs) must be gone from every commit, not just from
the tip. Git preserves the `-` side of every diff forever.

Run:

```bash
# Adjust the pattern list for whatever you're worried about.
git log --all -p | grep -iE '<real-pattern-1>|<real-pattern-2>|…'
```

If anything shows up, rewrite history with `git filter-repo` before
flipping the repo public. See *"How the 2026-04-16 scrub was done"*
below for the pattern.

### 2. Verify `.gitignore` covers private artifacts

The repo intentionally keeps a few files local-only. Re-check each is
still excluded:

- `PII_AUDIT_SIGNOFF.md` — audit signoff naming every scrubbed pattern.
  Must never be committed (it documents exactly what to look for).
- `*.jks` / `*.keystore` — release signing keystores.
- `local.properties` — per-machine SDK paths, sometimes sensitive.

### 3. Keystore is NOT in the repo (never has been)

The Yutori release keystore lives in `~/keys/yutori-release.jks` and is
backed up in Bitwarden + personal email. It is **never** committed. A
leaked keystore means anyone can ship a signed update that Android will
install as a trusted upgrade. See `docs/RELEASING.md`.

### 4. No GitHub secrets are accidentally committed

GitHub repo secrets (`SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`,
`SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`) live in Settings → Secrets.
They never appear in source. `~/.gradle/gradle.properties` holds the
same values locally — confirm it is `chmod 600` and outside the repo.

### 5. Check CI logs for secret leakage

Review recent `release.yml` run logs. If anything echoed a secret value
(even once, even accidentally), the value must be treated as
compromised: rotate the keystore password + GitHub secrets, regenerate
the base64 secret, and the leak is noted in CI history — consider
deleting those log runs via the API.

### 6. Personal info in commits

The commit author line shows `Jenslee Dsouza <dsouzajenslee@gmail.com>`
on every commit. If you want a different identity for the public repo,
set it *before* flipping public. Rewriting author history is messy.

### 7. Close or sanitise internal-only issues

Open issues may contain context that's fine in private but
inappropriate public (e.g. draft feature notes referencing real
accounts). Walk `gh issue list --state all` and either close or edit
any that need it.

### 8. Re-run PII grep against any memory references

The project's auto-memory (`~/.claude/projects/…/memory/`) is
local-only — not in the repo — so it isn't affected by repo visibility.
But any file inside `plans/` or `docs/` that references memory content
is fair game. Audit `plans/` once more before flipping public.

## How the 2026-04-16 scrub was done

Pattern-based rewrite of all historical blobs via `git filter-repo
--replace-text`. Real amounts / dates / account labels / UPI handles
were swapped for synthetic equivalents that preserve the arguments'
force (order of magnitude kept, specific numbers changed). 62 commits
rewrote in-place; no commits were squashed. Force push, retag
`v0.1.0` at the new SHA, regenerate the release body via `git-cliff`.

Future scrubs should follow the same pattern:

1. Build a replacements file in `filter-repo` format
   (`old==>new` or `regex:pattern==>replacement`, one per line).
2. Take a backup: `git bundle create /tmp/pre-scrub.bundle --all`
   before filter-repo runs (filter-repo rewrites backup branches too,
   so they're not a safety net — a bundle outside the repo is).
3. Run `git-filter-repo --replace-text <file> --force` inside a fresh
   clone or with the bundle backup on hand.
4. Verify no residuals: `git log --all -p | grep -iE '<patterns>'`.
5. Force-push main + affected tags. Regenerate any GitHub release
   bodies that linked to the old SHAs.

## Signing key rotation

If the keystore is ever compromised:

1. Generate a new keystore with a new alias (e.g. `yutori2`).
2. Update both `~/.gradle/gradle.properties` and the four
   `SIGNING_*` GitHub secrets.
3. Cut a new release under the new cert. Users will need to uninstall
   and reinstall — same-appId + different-cert is a fatal
   `INSTALL_FAILED_UPDATE_INCOMPATIBLE` on Android.
4. Document the rotation date + SHA-256 fingerprint of the new cert
   here in this file.

This has not happened — see the `keytool -list -v` command in
`docs/RELEASING.md` for the current cert fingerprint.
