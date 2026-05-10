---
title: STOP-and-report deferment contracts must align across every framing surface
date: "2026-05-09"
track: bug
category: build-errors
module: .flow/tasks/fn-8-g1-two-aircraft-vfr-circuits-at-lowg.3.md
tags: [spec-discipline, deferments, review-feedback, citation-discipline, cross-register-portability, fn-8]
problem_type: build-error
symptoms: Codex review NEEDS_WORK across 5 iterations on a docs-only commit; each iteration flagged a different surface with stale framing
root_cause: Refining headline direction text while leaving deferments-list summaries / historical evidence / off-repo-only pointers / speculative-citation caveats untouched
resolution_type: fix
related_to: [bug/build-errors/state-threading-specs-need-consumption-2026-05-09, bug/build-errors/sticky-witness-guard-swap-can-regress-2026-05-09]
---

## Problem

Plan / spec deferment contracts can carry stale framing that
masquerades as a clean handoff while actually steering the next
implementer back toward a known-bad design path. Phase 3 round 2's
codex review caught five iterations of this pattern in a single
fn-8.3 STOP-and-report deliverable:

- Iteration 1: deferments filed only in off-repo `~/.claude/plans/`
  with no on-repo mirror.
- Iteration 2: B5-α witness sketched as flat per-aircraft surface,
  recreating the stale-belief class an earlier round had closed.
- Iteration 3: B5-β kinematic reset on ClearedToLand receipt
  (the exact known-bad shape Phase 3 round 1's prior attempt
  reverted).
- Iteration 4: only the headline B5-β direction was rewritten;
  the deferments-list summary still carried the stale framing.
- Iteration 5: speculative-citation caveats and off-repo-only
  contract storage.

Each iteration's reviewer feedback was correct. The pattern: a
disciplined reviewer reads the WHOLE document, including
deferment-list summaries / cross-references / historical-evidence
sections, and finds the contract inconsistent with the
"recommended approach" prose if any of them carry the stale shape.

## What Didn't Work

- Refining only the headline direction text (iteration 3) while
  leaving deferment-list summaries with the old framing.
- Pointing at off-repo `~/.claude/plans/pilot-firewall.md` from
  on-repo `.plan` as "see the full contract there" (iteration 5).
  Codex correctly flagged this as a portability/integrity bug
  even though the user-memory rule documents the split.
- Adding "verify before commit" caveats on regulatory citations
  that are actually already verified-in-use in production code
  — codex reads "verify before commit" as a citation-discipline
  violation regardless of the upstream reason.

## Solution

When a STOP-and-report commit refines a deferment contract:

1. **Update every surface.** Search the spec for ALL occurrences
   of the old contract framing (headline direction, Review
   considerations, deferments-filed list, historical evidence
   notes, cross-aware summaries) and either rewrite or annotate
   each. Use `grep -n "<old framing key>" <file>` to enumerate
   before editing.
2. **Carry full four-field contracts on-repo.** `.plan` (or
   another tracked-project file) must carry the
   what-today / why-wrong / real-fix-contract / trigger fields
   directly, not just a summary plus an off-repo pointer. The
   off-repo register can stay as a duplicate or as the long-
   running architectural design home, but the tracked-project
   copy must stand on its own for CI / reviewers / clones.
3. **Reuse production-verified citations.** If the spec cites a
   regulation, audit the production code first
   (`grep -rE "<citation pattern>" controller/src/commonMain` etc.)
   to see whether the citation is already a verified-in-use
   regulation reference. If so, reference the production-code
   provenance in the spec rather than hedging with "verify
   before commit." If not, drop the speculative citation.
4. **Annotate historical-evidence sections that conflict with
   refined contracts.** Don't rewrite history (the original
   framing is the historical record of that round's thinking),
   but add a "round N+1 supersession" annotation pointing to the
   corrected contract so future implementers don't follow the
   reverted shape.

## Prevention

Before committing a STOP-and-report that refines a deferment:

- Enumerate every framing-relevant surface (headline +
  considerations + deferments list + historical evidence +
  cross-references) with a single grep pass.
- Confirm the on-repo .plan / spec carries the full contract,
  not just a pointer.
- Confirm regulatory citations are verified-in-use in production
  code before locking them into the contract; reference the
  production-code site as provenance.
- Plan for 4-6 codex review iterations per non-trivial design-
  refinement commit; each iteration tightens a different surface.
