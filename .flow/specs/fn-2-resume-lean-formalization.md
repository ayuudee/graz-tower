# Resume Lean formalization

## Goal

Use `research/fm` as the first end-to-end flow-next exercise after the research review. The initial scope is deliberately small: re-establish the Lean project frontier from local docs, verify the root Lean build, close one useful theorem-level helper gap, and leave a durable note for the next Lean task.

This is not a restart of `r1` and not a broad semantic widening. `r1` remains an operations tool that can assist later, but this epic is human/pilot-owned Lean work with normal build evidence.

## Current Reading

The FM docs say the scoped core and most current/world-backed delivered branches are closed for their current models. The next valuable Lean work is optional semantic widening beyond those models, or maintenance of the refinement/drift-control surface when a delivered branch changes.

For the first flow-next test, use a small root-gated observation-helper increment. It touches the stalled/r1-adjacent helper surface without depending on overnight automation.

## Plan

1. Record the current Lean frontier and flow-next wrapper gotchas in `research/fm/FLOW_NEXT_FRONTIER.md`.
2. Add missing published-handoff observation helper theorems for `lastContactRole` at boundary-fix and airborne handoff points.
3. Keep the change root-gated through `CertifiedAtc.lean`.
4. Run `lake build CertifiedAtc` through the repo flake shell.
5. Mark the task done in `.flow` with evidence.

## Review Considerations

FP / type safety: Lean remains the type checker. No `sorry`, `admit`, or unproved axioms may be introduced. The helper theorems must reduce by existing definitions, not add new semantics.

Test architecture: root Lean build is the gate. This task does not require Gradle because it changes proof-only Lean/docs, not Kotlin runtime code.

Impact: the first task validates flow-next mechanics against the Lean project and leaves a current frontier note, without reopening closed semantic branches or leaning on `r1` output.

Operational correctness: no ATC procedure or phraseology claim is introduced. The new theorems only pin existing published-handoff completion semantics already encoded in Lean.
