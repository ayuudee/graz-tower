# fn-3-complete-lean-formalization-campaign.3 Build current-model proof gap ledger

## Description
Convert the reconciled frontier into an explicit current-model gap ledger. This is the gate between documentation work and proof work: only genuine current-model gaps become proof tasks.

The ledger may live in `research/fm/FLOW_NEXT_FRONTIER.md` or a dedicated `research/fm/FLOW_NEXT_GAP_LEDGER.md`, but it must be durable and easy for later Flow-Next tasks to re-anchor against.

## Acceptance
- [ ] The ledger distinguishes current-model proof gaps from historical atomic/legacy bridge work and optional future widening.
- [ ] Each confirmed current-model gap has a concrete Lean module or doc surface, expected proof gate, and acceptance evidence.
- [ ] If there are no current-model proof gaps, the ledger says that explicitly and explains what would reopen the formalization.
- [ ] If gaps exist, one Flow-Next task is created for each highest-priority bounded gap before this task is marked done.

## Done summary
Built the current-model proof gap ledger at `research/fm/FLOW_NEXT_GAP_LEDGER.md` and linked it from the FM frontier, README, and agent guide.

The ledger records no confirmed current-model proof gaps after the May 1, 2026 reconciliation. It separates the closed current delivered surfaces from historical/legacy work and optional future widening branches, and it defines the promotion rules for turning a future candidate into a Flow-Next proof task.

Because no current-model proof gaps were confirmed, no additional proof tasks were created from this ledger task.
## Evidence
- Commits:
- Tests: git diff --check -- research/fm/FLOW_NEXT_GAP_LEDGER.md research/fm/FLOW_NEXT_FRONTIER.md research/fm/README.md research/fm/AGENT_GUIDE.md research/fm/parity_inventory.md research/fm/refinement_inventory.md research/fm/lean/README.md, grep -n -E 'No current-model proof gaps|Not Current-Model Gaps|INTENTIONALLY_OPEN|Promotion Rules|Every promoted task' research/fm/FLOW_NEXT_GAP_LEDGER.md, nix --extra-experimental-features 'nix-command flakes' develop path:. -c flowctl validate --all --json, nix --extra-experimental-features 'nix-command flakes' develop path:. -c bash -lc 'cd research/fm/lean && lake build CertifiedAtc'
- PRs: