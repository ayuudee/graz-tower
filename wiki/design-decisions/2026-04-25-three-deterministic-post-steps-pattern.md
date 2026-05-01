# 2026-04-25 Three Deterministic Post-Steps Establish A Pattern

## Decision

The Ollama-first prototype now sits three deterministic post-steps between
its LLM stages, each gated by an explicit safety condition:

1. `apply_sibling_symmetry_resolution` (RR-7) — runs after the bundle-gate
   batch; forces consistency on sibling candidates whose `scopeComplete`
   verdicts disagree.
2. `apply_bundle_gate_override` (RR-5) — runs after the challenger;
   downgrades mis-routed authority verdicts to `supported` when bundle
   gate confirms scope and candidate authority/modality are consistent.
3. `apply_judge_conservatism_override` (RR-8) — runs after the judge;
   restores `accepted` when the judge demoted a `promote` candidate
   despite a clean challenger and bundle-gate verdict.

Each post-step writes an audit record when it fires. Each declines on
shapes where the model's output might be correct. Together they target
three prompt-following bugs (`RR-5`, `RR-7`, `RR-8`) that prompt-only
fixes had failed to displace in earlier spikes. See "Why" below for the
honest evidence-strength breakdown per bug.

## Why

Across this session's spike sequence (Spike 1 through Spike 7), every
attempt to fix a prompt-following bug with a prompt change failed. Spike 1,
Spike 2, and Spike 5 each tightened the prompt for a specific class of
challenger or judge misroute; the model continued to misroute. Spike 3,
Spike 6, and Spike 7 each added a deterministic post-step; the
post-steps removed the bugs from the registry surface.

The evidence strength varies across the three closures and should be
recorded honestly:

- **RR-5 (n=10)**: ten authority verdicts across three families in Spike 6,
  nine fired the override and one correctly declined. Strong empirical
  closure.
- **RR-7 (n=0 firings in verification)**: the asymmetric-sibling pattern
  surfaced in a Spike 3 robustness rerun but did not recur in Spike 7. The
  resolution was wired and audit-recorded but never activated. Closure
  rests on the safety condition being correct in code, not on observed
  override behaviour.
- **RR-8 (n=0 firings in verification)**: the judge-conservatism pattern
  surfaced on Spikes 5 and 6 EGAST but did not recur in Spike 7. Same
  caveat as RR-7.

So the pattern claim is well-grounded for RR-5 and code-validated for
RR-7/RR-8. The wired-but-unfired guards may not match the next surfacing
of the bug if the bug shape mutates (e.g. a sibling group of three
branches; a judge demotion citing a different invented rule that still
passes the three-condition check).

## Consequence

- `RR-5` is empirically closed; `RR-7` and `RR-8` are code-closed pending
  recurrence verification.
- Future prompt-following bugs of the same shape should be considered for
  the deterministic post-step pattern after **two** prompt-only attempts
  have failed. One failure is not yet enough evidence — the prompt search
  space has many remaining moves.
- The prototype's separation of concerns is now clean: structure /
  extraction / classification stay LLM-driven; structure-vs-classification
  consistency, scope completeness across siblings, and judge faithfulness
  to its own prompt are all enforced mechanically.
- The post-steps form a chain: the judge override reads the bundle-gate-
  override output, which reads the sibling-resolution output. The
  ordering invariant is documented in the prototype's main loop; future
  reorders must preserve it.

## Evidence

- [icao4444-ollama-first-rr5-deterministic-override-2026-04-25.md](/home/andrew/dev/projects/twr2/docs/design/icao4444-ollama-first-rr5-deterministic-override-2026-04-25.md)
- [icao4444-ollama-first-rr7-rr8-deterministic-post-steps-2026-04-25.md](/home/andrew/dev/projects/twr2/docs/design/icao4444-ollama-first-rr7-rr8-deterministic-post-steps-2026-04-25.md)
- [Spike 7 readback summary](/tmp/icao4444-ollama-first-prototype-spike-7-readback/summary.md)
- [Spike 7 transfer summary](/tmp/icao4444-ollama-first-prototype-spike-7-transfer/summary.md)
- [Spike 7 EGAST summary](/tmp/icao4444-ollama-first-prototype-spike-7-egast/summary.md)
