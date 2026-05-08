# fn-2-resume-lean-formalization.1 Pin Lean frontier and add handoff observation helpers

## Description
Re-establish the Lean project frontier as a flow-next-owned task and close one small theorem-helper gap.

Context gathered from the FM docs:

- the scoped core and delivered current/world-backed branches are closed for their current models;
- `r1` is an operations/labor-multiplier experiment, not the owner of the Lean plan;
- the current next work should be deliberately small semantic widening or refinement-maintenance work, not broad reopening of closed families.

This task should leave a durable `research/fm/FLOW_NEXT_FRONTIER.md` note and add two missing published-handoff observation helper theorems for the `lastContactRole` completion path.
## Acceptance
- [ ] `research/fm/FLOW_NEXT_FRONTIER.md` records the current Lean frontier, the `r1` relationship, and the flow-next command wrapper needed in this environment.
- [ ] `GreenfieldObservationHelpers.lean` contains root-gated helper theorems for published-handoff contact-frequency completion using `lastContactRole` at boundary-fix and airborne handoff locations.
- [ ] No `sorry`, `admit`, or new axioms are introduced.
- [ ] `nix --extra-experimental-features 'nix-command flakes' develop path:. -c bash -lc 'cd research/fm/lean && lake build CertifiedAtc'` passes.


## Done summary
Documented the current Lean flow-next frontier in `research/fm/FLOW_NEXT_FRONTIER.md`, including the `r1` relationship and the shell wrapper needed to run bundled `flowctl` in this environment.

Added two root-gated helper theorems in `GreenfieldObservationHelpers.lean` for published-handoff contact-frequency completion using `lastContactRole` at boundary-fix and airborne handoff locations. Updated FM README/status docs to keep the helper layer visible.
## Evidence
- Commits:
- Tests: nix --extra-experimental-features 'nix-command flakes' develop path:. -c bash -lc 'cd research/fm/lean && lake build CertifiedAtc', grep -n -E '\b(sorry|admit|axiom)\b' research/fm/lean/CertifiedAtc/GreenfieldObservationHelpers.lean || true, nix --extra-experimental-features 'nix-command flakes' develop path:. -c bash -lc 'bash $FLOWCTL validate --epic fn-2-resume-lean-formalization --json'
- PRs: