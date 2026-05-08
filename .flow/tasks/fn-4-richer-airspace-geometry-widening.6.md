# fn-4-richer-airspace-geometry-widening.6 Update delivered registry and FM inventories

## Description
Update the central FM registry and inventories after the geometry-backed theorem package is closed or deliberately held as optional.

If the branch is promoted into the delivered surface, update `GreenfieldDeliveredRefinement.lean`, `parity_inventory.md`, `refinement_inventory.md`, `research/fm/README.md`, `PROJECT_STATUS.md`, `FLOW_NEXT_FRONTIER.md`, and `FLOW_NEXT_GAP_LEDGER.md`. If the branch remains optional, record exactly why and what remains before promotion.

This task is the documentation and drift-control alignment gate, not a place for new theorem work.
## Acceptance
- [ ] The FM docs state whether the richer airspace geometry branch is delivered, optional, or partially closed.
- [ ] `parity_inventory.md` and `refinement_inventory.md` agree with the new branch status and drift gates.
- [ ] `GreenfieldDeliveredRefinement.lean` is updated if and only if the branch is promoted into the delivered registry.
- [ ] README/status/frontier/gap-ledger docs agree on closed/open surfaces and reopening triggers.
- [ ] `flowctl validate --all --json` passes.
- [ ] `cd research/fm/lean && lake build CertifiedAtc` passes.
## Done summary
Aligned the FM registry and inventory docs after the geometry-backed theorem package. The branch is recorded as an optional partial package: root-gated in Lean via `GreenfieldAirspaceGeometryBackedCurrentShape`, but not promoted into `GreenfieldDeliveredRefinement.lean`. Updated the parity/refinement inventories, README/status, Flow-Next frontier, gap ledger, and active scope note so they agree that the delivered airspace registry remains the current point-set + transition branch while declared-profile geometry witnesses are additive and optional.
## Evidence
- Commits:
- Tests: nix --extra-experimental-features 'nix-command flakes' develop path:. -c bash -lc 'cd research/fm/lean && lake build CertifiedAtc', nix --extra-experimental-features 'nix-command flakes' develop path:. -c flowctl validate --all --json, git diff --check -- <tracked touched FM docs and Lean files>; grep trailing-whitespace check for untracked FM docs, grep -R -nE '(^|[^A-Za-z0-9_])(sorry|admit|axiom)([^A-Za-z0-9_]|$)' <touched Lean files> # no matches, git diff --name-only -- research/fm/lean/CertifiedAtc/GreenfieldDeliveredRefinement.lean # no changes
- PRs: