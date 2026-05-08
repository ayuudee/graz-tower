# fn-3-complete-lean-formalization-campaign.2 Reconcile FM frontier docs and inventories

## Description
Re-read the FM source-of-truth documents and make their frontier claims agree with the root-gated Lean modules. The point is to remove stale status drift before choosing any new theorem work.

Read at least:

- `research/fm/README.md`
- `research/fm/PROJECT_STATUS.md`
- `research/fm/AGENT_GUIDE.md`
- `research/fm/FLOW_NEXT_FRONTIER.md`
- `research/fm/parity_inventory.md`
- `research/fm/refinement_inventory.md`
- `research/fm/lean/README.md`
- `research/fm/lean/CertifiedAtc.lean`

## Acceptance
- [ ] The active FM frontier note records the current Flow-Next/Nix command shape, not the pre-setup wrapper workaround.
- [ ] README, project status, agent guide, and inventories agree on which theorem families are closed, open, historical, or opt-in.
- [ ] Any stale claim found during reconciliation is corrected in the same task.
- [ ] No Lean semantics are changed unless needed to keep docs truthful; if Lean changes, the root `CertifiedAtc` build is included as evidence.

## Done summary
Reconciled the FM docs and inventories against the root-gated Lean surface for the current Flow-Next campaign.

Updated the active Flow-Next frontier note to May 1, 2026 and recorded the reconciled root-gated status points: `GreenfieldDeliveredRefinement.lean`, `ObservationReconciliation.lean`, live world-backed route-adjacent surfaces, and `r1` as reviewed candidate material only.

Removed the stale wrapper wording from the FM README directory guide, updated parity/refinement inventory dates and route-bearing / route-adjacent / route-control boundaries, and updated the Lean guide so every module imported by `CertifiedAtc.lean` is mentioned there. No Lean semantics changed.
## Evidence
- Commits:
- Tests: grep -R -n -E 'wrapper needed|needed to run bundled `flowctl`|pre-setup wrapper|Flow-Next Wrapper' research/fm/*.md research/fm/lean/README.md || true, grep '^import CertifiedAtc\.' research/fm/lean/CertifiedAtc.lean | sed 's/import CertifiedAtc\.//' | while read m; do grep -q "$m" research/fm/lean/README.md || echo "$m"; done, git diff --check -- research/fm/README.md research/fm/FLOW_NEXT_FRONTIER.md research/fm/parity_inventory.md research/fm/refinement_inventory.md research/fm/lean/README.md, nix --extra-experimental-features 'nix-command flakes' develop path:. -c bash -lc 'cd research/fm/lean && lake build CertifiedAtc', nix --extra-experimental-features 'nix-command flakes' develop path:. -c flowctl validate --all --json
- PRs: