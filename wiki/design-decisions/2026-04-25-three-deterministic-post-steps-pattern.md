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
shapes where the model's output might be correct. Together they close
the four most material prompt-following bugs the prototype has
encountered.

## Why

Across this session's spike sequence (Spike 1 through Spike 7), every
attempt to fix a prompt-following bug with a prompt change failed. Spike 1,
Spike 2, and Spike 5 each tightened the prompt for a specific class of
challenger or judge misroute; the model continued to misroute. Spike 3,
Spike 6, and Spike 7 each added a deterministic post-step; the
post-steps eliminated the bugs from the registry surface.

Three failures and three successes of the same flavour are enough
evidence that the local model has deep priors that prompting cannot
displace, and that mechanical logic with auditable safety conditions is
the right layer to fix them at.

## Consequence

- `RR-5`, `RR-7`, and `RR-8` close in this session.
- Future prompt-following bugs of the same shape default to the
  deterministic post-step pattern after one prompt-only attempt fails.
- The prototype's separation of concerns is now clean: structure /
  extraction / classification stay LLM-driven; structure-vs-classification
  consistency, scope completeness across siblings, and judge faithfulness
  to its own prompt are all enforced mechanically.

## Evidence

- [icao4444-ollama-first-rr5-deterministic-override-2026-04-25.md](/home/andrew/dev/projects/twr2/docs/design/icao4444-ollama-first-rr5-deterministic-override-2026-04-25.md)
- [icao4444-ollama-first-rr7-rr8-deterministic-post-steps-2026-04-25.md](/home/andrew/dev/projects/twr2/docs/design/icao4444-ollama-first-rr7-rr8-deterministic-post-steps-2026-04-25.md)
- [Spike 7 readback summary](/tmp/icao4444-ollama-first-prototype-spike-7-readback/summary.md)
- [Spike 7 transfer summary](/tmp/icao4444-ollama-first-prototype-spike-7-transfer/summary.md)
- [Spike 7 EGAST summary](/tmp/icao4444-ollama-first-prototype-spike-7-egast/summary.md)
