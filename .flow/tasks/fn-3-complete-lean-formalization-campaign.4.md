# fn-3-complete-lean-formalization-campaign.4 Smoke the r1 background candidate lane

## Description
Set up `research/tools/r1` as a background candidate generator for Lean chores, without putting it on the critical path. This is an infrastructure smoke, not an overnight proof run.

Use `research/fm/r1-smoke/` as the local ignored operations workspace. Refresh the seed snapshot from the current Lean tree, regenerate queue artifacts instead of hand-editing stale queue files, and run only enough to catch setup failures or repeated early failures.

## Acceptance
- [ ] The `r1` flake shell has the tools needed for the smoke path: Node, elan, Python, jq, and `flowctl`.
- [ ] The smoke workspace is refreshed from `research/fm/lean/` without deleting historical `runs/` unless there is a documented reason.
- [ ] A small smoke run or dry run completes far enough to prove queue generation and early execution wiring.
- [ ] Any useful `r1` output is treated as candidate material only; it is not promoted without review and a Flow-Next task.
- [ ] The operating loop is documented in the active FM frontier note or existing r1 docs.

## Done summary
Smoked the r1 background lane as an operations-only candidate generator. Refreshed the ignored seed snapshot from current Lean, regenerated fresh smoke queue artifacts, ran a deterministic one-item queue through early execution wiring, documented the operating loop, and verified r1/Lean/Flow-Next checks.
## Evidence
- Commits:
- Tests:
- PRs: