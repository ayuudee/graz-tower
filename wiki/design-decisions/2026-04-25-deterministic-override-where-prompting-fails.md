# 2026-04-25 Deterministic Override Where Prompting Fails

## Decision

When the local Ollama model has a deep prior that no amount of prompt
instruction will displace, replace the prompt fix with a deterministic
post-step that overrides the model's output, gated by an explicit safety
condition.

This is now the standard Ollama-first prototype pattern when:

1. A challenger / judge / structure / extraction stage produces
   reproducibly wrong output of a specific shape.
2. Multiple prompt iterations have failed to fix it.
3. The "wrong" pattern can be detected by reading the candidate's own
   classification fields plus the outputs of other stages.
4. The override has a safety condition that declines when the model's
   output might be correct after all.

Spike 6 instantiates this pattern for RR-5: the deterministic helper
`apply_bundle_gate_override` overrides the challenger's
`authority_too_high` / `authority_too_low` verdicts to `supported` when
the bundle gate confirms scope completeness AND the candidate's
`authorityClass` is consistent with its declared `modality`. Otherwise
the challenger's verdict stands.

## Why

The Spike 5 prompt-only fix did not land. Across three runs (readback,
transfer, EGAST), all six previously-misfiring mixed-modality candidates
continued to receive `authority_too_high` despite the new system-prompt
rule explicitly forbidding it.

Spike 6's deterministic override moves all three families to their
best-ever judged outcomes:

- transfer: 3 accepted (matches Spike 3 best)
- readback: 4 accepted (stable)
- EGAST: 4 accepted (up from 1 in Spike 5, 3 in Spike 4)

9 of 10 raw authority verdicts were overridden. The 1 decline was correct
— a `should` clause genuinely classified as `authoritative_requirement`
that the challenger correctly objected to.

This is the second time in the Ollama-first thread that a prompt-only
approach has yielded to mechanical logic. The first was structure
reconciliation in Spike 0 (closed RR-3). The pattern is general: where
the model's prior is robust to prompt instruction, deterministic logic
with an auditable safety condition is the right answer.

## Consequence

- `RR-5` closes.
- `RR-7` (bundle gate asymmetric reasoning on parallel mutually-exclusive
  siblings) and `RR-8` (judge invents over-conservative rules on
  best_practice content) become visible. They predate this work and are
  now visible because RR-5 noise no longer masks them.
- Future prompt-following bugs of the same shape should default to the
  deterministic-override pattern after one prompt-only attempt fails,
  rather than spiking it three more ways.

## Evidence

- [icao4444-ollama-first-rr5-deterministic-override-2026-04-25.md](docs/design/icao4444-ollama-first-rr5-deterministic-override-2026-04-25.md)
- [Spike 6 readback summary](/tmp/icao4444-ollama-first-prototype-spike-6-readback/summary.md)
- [Spike 6 transfer summary](/tmp/icao4444-ollama-first-prototype-spike-6-transfer/summary.md)
- [Spike 6 EGAST summary](/tmp/icao4444-ollama-first-prototype-spike-6-egast/summary.md)
