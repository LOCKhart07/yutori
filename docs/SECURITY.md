# Security

Operational notes for keeping the Yutori repo, its signing keys, and
user data safe. Yutori is an on-device Android app — all spending data
lives in the local Room database and never leaves the phone. The
security surface that matters here is the **release signing chain** and
the **public source tree** (no real PII in commits, issues, or
fixtures).

## Reporting a vulnerability

Found a security issue — a signing/update weakness, a PII leak in
history, or a data-exposure bug in the app? Report it privately through
GitHub: **Security → Report a vulnerability**
([new advisory](https://github.com/LOCKhart07/yutori/security/advisories/new)).
This opens a private advisory visible only to you and the maintainer.

Please don't open a public issue for security problems — that discloses
the details before a fix can ship.

## Release signing chain

A leaked signing key is the worst case: anyone holding it can ship a
signed update that Android installs as a trusted upgrade over an
existing install.

- **The keystore is never in the repo.** `*.jks` / `*.keystore` are
  gitignored, and the release keystore is kept offline in secure
  storage — never committed, never shared. See `docs/RELEASING.md` for
  the signing setup.
- **Signing secrets live only in GitHub Actions secrets.**
  `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`,
  `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` are configured under
  Settings → Secrets and never appear in source. The same values exist
  locally in `~/.gradle/gradle.properties` — keep that file `chmod 600`
  and outside the repo.
- **Watch CI logs for accidental echoes.** Review `release.yml` run logs
  after changes to the signing steps. If a secret value was ever printed
  (even once), treat it as compromised: rotate per *Signing key
  rotation* below and consider deleting the affected log runs.

### Signing key rotation

If the keystore is ever compromised:

1. Generate a new keystore with a new alias (e.g. `yutori2`).
2. Update both `~/.gradle/gradle.properties` and the four `SIGNING_*`
   GitHub secrets.
3. Cut a new release under the new cert. Users must uninstall and
   reinstall — same appId + different cert is a fatal
   `INSTALL_FAILED_UPDATE_INCOMPATIBLE` on Android.
4. Record the rotation date + the new cert's SHA-256 fingerprint here.

## PII discipline

The source tree is public. Real personal data must never land in it —
not in code, tests, fixtures, plans, docs, mockups, issues, or commit
messages. This means real amounts, last-4s, UPI handles, merchant
strings, spending totals, account labels, employer names, phone
numbers, and real SMS bodies. Use synthetic placeholders only; follow
the patterns in `android/parser/src/test/kotlin/.../*Test.kt`.

- **Local-only files stay gitignored.** `PII_AUDIT_SIGNOFF.md` (the
  audit signoff that names scrubbed patterns) and `local.properties`
  (per-machine SDK paths) must never be committed. Re-check `.gitignore`
  still covers them when adding tooling.
- **Issues are public.** Don't paste real accounts or SMS into issue
  bodies or comments. Sanitise before filing.
- **Auto-memory is out of tree.** The project's Claude auto-memory
  (`~/.claude/projects/…/memory/`) is local-only and never committed —
  but any `plans/` or `docs/` file that *references* memorised facts is
  fair game for a PII grep. Audit those when in doubt.

## Scrubbing PII from history

Git preserves the `-` side of every diff forever, so deleting a leaked
value in a later commit does **not** remove it — the whole history must
be rewritten. If a real value ever lands in a committed blob:

1. Build a replacements file in `git filter-repo` format
   (`old==>new` or `regex:pattern==>replacement`, one per line). Swap
   real values for synthetic equivalents that preserve their force
   (keep order of magnitude, change the specifics).
2. Back up first: `git bundle create /tmp/pre-scrub.bundle --all`.
   filter-repo rewrites in-repo backup branches too, so only an
   out-of-repo bundle is a real safety net.
3. Run `git filter-repo --replace-text <file> --force` in a fresh clone
   (or with the bundle on hand).
4. Verify no residuals: `git log --all -p | grep -iE '<patterns>'`.
   Also delete any stale remote `claude/*` PR branches that may still
   carry pre-scrub content (`gh api -X GET repos/:owner/:repo/branches`).
5. Force-push `main` + affected tags, and regenerate any GitHub release
   bodies via `git-cliff` so they don't link dead SHAs.

### GitHub orphaned-commit retention

A history rewrite leaves the old SHAs **unreachable but still
addressable** on GitHub — anyone with an old SHA can view the
pre-rewrite content via `/commit/<old-sha>` until GitHub garbage-collects
the orphans. GC runs opportunistically (typically ~14 days, up to ~90).
To force it: email GitHub Support and ask them to run GC + clear the
repo cache; verify a known orphan SHA then 404s via
`gh api -X GET repos/:owner/:repo/commits/<old-sha>`. The nuclear
alternative — delete and re-create the repo — loses issues, stars, and
CI history.

Never paste orphan SHAs into a committed file — that re-creates the
exact pointer a scrub was meant to remove.
