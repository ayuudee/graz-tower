---
satisfies: [R4, R5, R6]
---

## Description

Close-out task: full verify suite GREEN, commit + push the engine-pass branch, update `.plan` FN31-TEST-1 to mark all sub-issues green. Depends on .1 (kotest cache), .2 (controller fixture), .3 (pilot tests).

**Reviewer Round 1**: original spec had push BEFORE `.plan` update + omitted the commit step. Re-ordered below.

**Size:** S (run tests, commit, push, edit .plan)
**Files:**
- `.plan` — mark FN31-TEST-1 sub-issues complete (or move per user's `.plan` convention)

## Approach (re-ordered per reviewer R1)

1. **Apply fixes from .2 + .3** — they should be committed within their own task close-out.
2. **Update `.plan`** — mark FN31-TEST-1's 3 sub-issues green / move to completed section per the `.plan` convention. This MUST be done BEFORE final push so it ships in the same push.
3. **Targeted re-verify per fix** (smoke):
   ```bash
   gradle :controller:jvmTest --tests "*GoAroundSequencingSpec*" --offline --no-daemon
   gradle :pilot:jvmTest --tests "*PilotEvent*Test*" --offline --no-daemon
   ```
4. **Commit `.plan` update + any final cleanup** — single commit, no `git add -A` (stage explicitly).
5. **Full verify** (final gate):
   ```bash
   GRADLE_USER_HOME=$HOME/.cache/gradle GRADLE_RO_DEP_CACHE=$HOME/.gradle/caches \
   TMPDIR=$TMPDIR _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
   nix --extra-experimental-features 'nix-command flakes' develop --no-write-lock-file -c bash -c '
     gradle :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt --offline --no-daemon
     gradle :migration:allTests --offline --no-daemon
   '
   ```
   Confirm: 13 sim goldens GREEN, all unit/property tests GREEN, detekt clean.
6. **Confirm branch + remote** before push:
   ```bash
   git rev-parse --abbrev-ref HEAD  # confirm branch name (DO NOT assume `main`)
   git remote -v                     # confirm origin URL
   git log --oneline @{u}..HEAD | head  # final preview of commits-to-push
   ```
7. **Push** — `git push origin <confirmed-branch>`. If push hits new pre-receive hooks / branch protection / non-fast-forward, surface to the user — DO NOT force-push.

## Investigation targets

**Required**:
- `.plan` — FN31-TEST-1 entry (currently in `## Active items`) + reading the file's top to learn the convention for marking complete
- Commit `d32b8b8` message — nix-shell + offline formula
- Tasks .1, .2, .3 closure summaries (all green required)

**Optional**:
- `git remote -v` — confirm origin URL is what's expected
- Any branch protection / pre-receive hook surfacing on first push attempt

## Key context

- **Ordering matters** (reviewer R1 fix): `.plan` update + commit BEFORE final verify + push. Otherwise the push leaves `.plan` stale or requires a second push.
- **Confirm branch name**: do NOT assume `main`. Run `git rev-parse --abbrev-ref HEAD` first.
- **No `git add -A`**: stage explicitly to avoid sweeping unrelated working-tree changes.
- The already-fixed work from this session (commit `d32b8b8` + the verify-pass cleanup commit) is baseline; not re-applied.
- `.plan` is the user's manual notebook — read its structure before editing. The convention for "completed" vs in-place strikethrough may vary.

## Acceptance

- [ ] `.plan` FN31-TEST-1 sub-issues marked complete (per the file's convention — read it first)
- [ ] Targeted verifies green (controller + pilot smoke runs)
- [ ] `.plan` + final cleanup committed in a single named commit (no `git add -A`)
- [ ] Full verify GREEN: `:pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt :migration:allTests` (offline) — all targets pass
- [ ] 13 sim goldens GREEN; detekt clean; 9 pre-fn-28 + 4 fn-28 anchors don't regress
- [ ] Branch + remote confirmed via `git rev-parse --abbrev-ref HEAD` + `git remote -v` BEFORE push
- [ ] `git push origin <confirmed-branch>` succeeds; ~45+ commits land on origin
- [ ] `## Resolved during implementation` captures: branch name pushed to, full-verify wall-clock, any surprise findings (3rd test failure, push rejection, etc.)

## Review considerations

- **Impact**: doc-only (`.plan`) + git push; no source / test changes in this task
- **Test architecture**: validates green status only; no test changes
- **Operational ATC correctness**: N/A
- **Risk**: only via push (non-fast-forward / branch protection). Mitigated by branch + remote confirmation steps + explicit no-force-push rule.

## Done summary

_(filled by `flowctl done` at task close)_

## Evidence

_(filled by `flowctl done` at task close)_
