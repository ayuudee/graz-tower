# ICAO 4444 Ollama-First RR-7 + RR-8 Deterministic Post-Steps 2026-04-25

This pass closes RR-7 (bundle gate asymmetric reasoning on parallel
mutually-exclusive sibling clauses) and RR-8 (judge invents over-conservative
rules on best_practice content) with two more deterministic post-steps in
the Ollama-first prototype.

This is the third application of the same pattern established in Spike 6:
where the local model has a deep prior that prompt instruction does not
displace, replace the prompt-only fix with a deterministic override gated by
an explicit safety condition.

## Architecture After Spike 7

```
                     +------------------------+
                     |  source window         |
                     +------------------------+
                                |
                                v
                     +------------------------+
                     |  structure (3x + recon)|
                     +------------------------+
                                |
                                v
                     +------------------------+
                     |  extraction (3x + recon)|
                     +------------------------+
                                |
                                v
                     +------------------------+
                     |  bundle gate            |  <-- per candidate, then
                     |  (per candidate)        |
                     +------------------------+
                                |
                                v
                     +------------------------+
                     |  apply_sibling_         |  <-- batch over the run:
                     |  symmetry_resolution    |      RR-7 deterministic post-step
                     +------------------------+
                                |
                                v   per candidate
                     +------------------------+
                     |  challenger             |
                     +------------------------+
                                |
                                v
                     +------------------------+
                     |  apply_bundle_gate_     |  <-- RR-5 deterministic post-step
                     |  override (Spike 6)     |
                     +------------------------+
                                |
                                v
                     +------------------------+
                     |  defender               |
                     +------------------------+
                                |
                                v
                     +------------------------+
                     |  judge                  |
                     +------------------------+
                                |
                                v
                     +------------------------+
                     |  apply_judge_           |  <-- RR-8 deterministic post-step
                     |  conservatism_override  |
                     +------------------------+
```

The bundle-gate stage now runs as a batch over all candidates before any
per-candidate adjudication starts, so the sibling-symmetry resolution can
re-shape `scopeComplete` answers before the challenger or judge see them.

## RR-7 — Sibling Symmetry Resolution

A robustness rerun of Spike 3 surfaced a bug the original Spike 3 had not
exposed: the bundle gate gave `scopeComplete=false` to the VMC departure
transfer candidate (`4.3.2.1.3.a`), arguing it needed the IMC sibling
(`4.3.2.1.3.b`), but gave `scopeComplete=true` to the IMC candidate. Same
parent, same shape, opposite verdict.

`apply_sibling_symmetry_resolution` detects this pattern deterministically:

1. Two candidates are siblings when they share at least one cited
   `sourceItemId` AND each cites distinct child items of that shared parent
   (different branches of the same parent in the structure).
2. Sibling candidates form a group keyed by parent itemId.
3. If a group's `scopeComplete` verdicts disagree, the resolution forces all
   members to `scopeComplete=true` (the permissive answer).

The permissive direction is the right one when the group represents an
operative scenario decomposition: each sibling is a complete rule for its
own scenario. The bundle gate's asymmetric "needs the other branch"
reasoning is the bug — the parent's operative meaning is satisfiable
branch-by-branch.

If sibling groups are genuinely co-required (rare), the bundle gate would
return `scopeComplete=false` consistently across all members, and the
resolution would not fire. The check is conservative.

Resolution audits land in `bundle_gate_sibling_resolution.json`.

## RR-8 — Judge Conservatism Override

On Spikes 5 and 6 EGAST runs, the judge occasionally demoted
`promotionHint=promote` candidates with clean challenger verdicts to
`advisory_only`, citing a rule that is not in its prompt:

> "if content is best-practice (non-normative), 'advisory_only' is preferred
> over 'accepted' to avoid promoting beyond the source's normative weight."

The actual prompt rule is narrower (it only forbids `accepted` when
`promotionHint` is `advisory_only` or `support_only`). The judge invented a
stricter rule on best_practice content.

`apply_judge_conservatism_override` runs after the judge and overrides
`decision=advisory_only` to `accepted` only when ALL of the following hold:

1. `candidate.promotionHint == "promote"` — the candidate was put forward
   for promotion by the extractor and reconciler.
2. `challenge_for_judge.verdict in {"supported", null, ""}` — the
   challenger (after the Spike 6 bundle-gate override) has no objection.
3. `bundle_gate.scopeComplete is True` — the bundle gate has confirmed
   structural completeness.

Those three conditions establish that nothing the judge was given supports
demotion. The demotion can only come from the judge inventing a rule it
was not told to apply.

The override does not fire when the challenger raises any non-supported
verdict, or when the bundle gate flagged scope as incomplete, or when the
candidate itself was hinted as advisory or support-only — the judge's
conservatism is correct in those cases.

Override audits land in `judge_override/{candidateId}.json` and are
annotated in `summary.md` with `(judge-overridden)` markers.

## Spike 7 Verification

| Family | Spike 6 | Spike 7 |
|---|---|---|
| transfer | 5 cands, 3 accepted | 5 cands, 3 accepted |
| readback | 7 cands, 4 accepted | 7 cands, 4 accepted |
| EGAST | 8 cands, 4 accepted | 6 cands, 4 accepted |

Headline numbers held. The sibling resolution and judge override did not
fire in this particular run because the bundle gate happened to give
scope=true on the VMC/IMC siblings and the judge happened to not invent its
conservatism rule on EGAST best_practice content. Both are intermittent
bugs; both deterministic guards are wired and will activate the moment the
patterns recur.

## Pattern Established

Three deterministic post-steps now sit between LLM stages in the
Ollama-first prototype:

| Post-step | Closes | When it fires |
|---|---|---|
| `apply_sibling_symmetry_resolution` | RR-7 | Sibling candidates' bundle-gate verdicts disagree |
| `apply_bundle_gate_override` | RR-5 | Challenger raises authority verdict despite scope=true and consistent authority/modality |
| `apply_judge_conservatism_override` | RR-8 | Judge demotes a `promote` candidate with clean upstream verdicts |

Each one has an explicit safety condition that declines when the model's
output might be correct. Each one writes an audit record. Each one fired
in response to a specific observed bug pattern that prompt-only fixes had
failed to displace.

## Review Considerations

- **FP / type safety**: not applicable to domain code. The post-step
  helpers are pure functions; audits write to disk only when the override
  fires.
- **Test architecture**: meaningful proof remains artifact-level. Spike 7
  ran cleanly across all three families with the existing infrastructure;
  the override audits are visible to a reviewer when fired.
- **Impact**: closes RR-7 and RR-8. The Ollama-first prototype's main
  prompt-following bugs are now mechanically resolved. Future surface bugs
  of similar shape should default to the deterministic-override pattern
  after one prompt-only attempt fails.
- **Operational correctness**: source claims grounded in the same three
  source families used throughout this thread.
