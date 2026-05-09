# ICAO 4444 Ollama-First RR-5 Deterministic Override 2026-04-25

This pass closes RR-5 (challenger uses `authority_too_high` for mixed-modality
structural concerns). Two spikes were attempted:

- **Spike 5** — prompt-only fix. Reorder the bundle gate so it runs before the
  challenger; supply its `scopeComplete` and `missingDependencies` to the
  challenger; instruct the challenger to use `wrong_split` / `overbroad` /
  `underspecified` for structural concerns when the bundle gate has already
  confirmed scope.
- **Spike 6** — deterministic post-step. Keep Spike 5's reordering. After the
  challenger runs, examine its verdict alongside the bundle-gate result and
  the candidate's classification; override `authority_too_high` /
  `authority_too_low` to `supported` only when both (a) bundle gate confirms
  scope completeness AND (b) candidate's authority class is consistent with
  its declared modality.

Spike 5 did not move the needle on RR-5 — the model continued to misroute
structural concerns through authority verdicts despite the new prompt rule.
Spike 6 fixes it deterministically with an auditable safety condition.

## Spike 5 outcome — prompt-only fix did not land

After moving the bundle gate to run before the challenger, the challenger
gained access to the bundle gate's `scopeComplete` answer. The new system
prompt added:

> If `bundleGate.scopeComplete` is `true`, the structural fit of the
> candidate's `sourceItemIds` to its parent is already settled — do NOT use
> `authority_too_high`, `authority_too_low`, or `wrong_split` to express
> bundle, scope, or mixed-modality concerns in that case.

Across the readback, transfer, and EGAST runs, all six mixed-modality
candidates that triggered `authority_too_high` in earlier spikes continued
to do so in Spike 5. The challenger ignored the new instruction.

EGAST also regressed (3 → 1 accepted) due to unrelated judge stochasticity:
the judge invented a conservatism rule about best_practice content that is
not in its prompt. That regression is independent of Spike 5's reordering
and is now tracked as `RR-8`.

The Spike 5 reordering is harmless on `ICAO 4444` (same headline numbers as
Spike 3) and produced one principled improvement on readback: the parent-
only candidate `req_flight_crew_readback_safety` was classified
`underspecified` rather than `authority_too_high` (correct routing). It is
preserved in Spike 6.

## Spike 6 — deterministic override

A new helper `apply_bundle_gate_override` runs after the challenger. The
override fires when:

1. Challenger `verdict ∈ {authority_too_high, authority_too_low}`.
2. Bundle gate `scopeComplete` is `true`.
3. The candidate's `authorityClass` is consistent with its declared
   `modality` per the modality-floor mapping
   `_AUTHORITY_CLASS_BY_FLOOR`.

When all three hold, the verdict is downgraded to `supported`, the concerns
and source quotes are cleared, and an audit record is written to
`challenge_override/{candidateId}.json`.

The third condition is the load-bearing safety check. It declines when the
candidate's authority class is genuinely too high for its modality (e.g. a
`should` clause classified as `authoritative_requirement`), preserving real
challenger work.

### Modality-floor mapping

```python
_AUTHORITY_CLASS_BY_FLOOR: dict[str, set[str]] = {
    "authoritative_requirement": {"shall", "mixed"},
    "operational_guidance": {"shall", "should", "may", "note", "example", "none", "mixed"},
    "best_practice": {"shall", "should", "may", "note", "example", "none", "mixed"},
    "background_support": {"shall", "should", "may", "note", "example", "none", "mixed"},
}
```

`authoritative_requirement` is the only strict class — it requires `shall` or
`mixed` (a parent-and-nested bundle whose highest modality is `shall`). The
weaker classes accept any modality.

## Spike 6 results

| Family | v4 | Spike 3 (original) | Spike 3 (rerun) | Spike 4 | Spike 5 | **Spike 6** |
|---|---|---|---|---|---|---|
| transfer | 2 accepted | 3 | 2 | n/a | 3 | **3** |
| readback | 3 accepted | 4 | 4 | n/a | 4 | **4** |
| EGAST | n/a | n/a | n/a | 3 | 1 | **4** |

Spike 6 produces best-ever or matching-best on all three families. EGAST
goes from 1 → 4 accepted; ICAO 4444 holds at the Spike 3 baseline.

### Override audit across all three Spike 6 runs

10 raw `authority_too_high` / `authority_too_low` verdicts were raised by
the challenger across the three runs.

- **9 fired** — all on candidates whose `authorityClass` is consistent with
  declared modality (mixed-modality bundles, should-classified-as-best-
  practice cases, etc.).
- **1 declined** — `req_comm_transfer_timing` had `modality=should` but
  `authorityClass=authoritative_requirement` (genuinely inconsistent — a
  `should` clause cannot be authoritative). The override correctly stood
  aside; the judge demoted to `advisory_only`.

The decline shows the safety condition is doing real work. Without it, the
override would mask legitimate challenger objections.

## RR-7 and RR-8

Both surfaced during this investigation and are tracked separately.

`RR-7`: bundle gate makes asymmetric calls on parallel mutually-exclusive
sibling clauses. On a Spike 3 robustness rerun, VMC departure transfer got
`scopeComplete=false` (arguing it needs the IMC sibling) while IMC got
`scopeComplete=true` (despite the symmetric situation). Readback has no
parallel siblings in the slice; the bug only surfaces on the transfer
family. Spike 6 happened to land on a stochastic outcome where both siblings
got `scopeComplete=true`, so the bug didn't trigger this run. It remains a
real risk.

`RR-8`: judge invents over-conservative rules on best_practice content. On
Spikes 5 and 6 EGAST, the judge has occasionally demoted candidates with
`promotionHint=promote`, `authorityClass=best_practice`, and a clean
challenger verdict to `advisory_only`, citing a rule about non-normative
content that is not in the judge's prompt. Spike 6 EGAST: 4 accepted
instead of the achievable 5.

Both bugs are honest residuals — not Spike 6 regressions. They predate
this pass and are now visible because RR-5 noise is no longer masking
them.

## Architecture After Spike 6

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
                                v   per candidate
                     +------------------------+
                     |  bundle gate            |
                     +------------------------+
                                |
                                v
                     +------------------------+
                     |  challenger             |
                     +------------------------+
                                |
                                v
                     +------------------------+
                     |  apply_bundle_gate_     |  <-- deterministic
                     |  override (Spike 6)     |      post-step
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
```

The override is a small but load-bearing piece of deterministic logic that
sits between two LLM stages. It enforces a rule the model has consistently
refused to follow via prompting, and does so with an auditable safety
condition.

## Review Considerations

- **FP / type safety**: not applicable to domain code. The override helper
  is a pure function from `(challenge, bundle_gate, candidate)` to
  `(challenge', audit | None)`; no side effects beyond writing the audit
  record.
- **Test architecture**: meaningful proof remains artifact-level —
  per-spike `/tmp/icao4444-ollama-first-prototype-spike-N-{family}/`
  outputs, with `challenge/`, `challenge_override/`, `bundle_gate/`,
  `defense/`, and `judge/` directories. Override audit records make the
  intervention point visible to a reviewer.
- **Impact**: closes RR-5. Surfaces RR-7 and RR-8 cleanly. The
  deterministic override is the second time in this thread that a
  prompt-only fix has been replaced with mechanical logic (the first being
  the structure-reconciliation pass that closed RR-3). The pattern is
  becoming clear: where the local model has a deep prior that prompting
  can't displace, deterministic logic is the right answer.
- **Operational correctness**: source claims grounded in
  [icao4444-extracted.txt](research/txt/icao4444-extracted.txt)
  and [egast-vfr-extracted.txt](research/txt/egast-vfr-extracted.txt).
