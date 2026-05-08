# fn-3-complete-lean-formalization-campaign.1 Baseline Flow-Next and Lean gates

## Description
Establish the operational baseline before any new Lean proof work. This task proves that Flow-Next is usable from the Nix shell and that the current Lean root remains green before frontier reconciliation begins.

## Acceptance
- [ ] `flowctl validate --all --json` passes inside the default flake dev shell.
- [ ] The default flake shell exposes `.flow/bin/flowctl` on `PATH`, and `flowctl detect --json` reports a valid `.flow` directory.
- [ ] The `r1` flake shell exposes `.flow/bin/flowctl` on `PATH`, and `flowctl detect --json` reports a valid `.flow` directory.
- [ ] `nix --extra-experimental-features 'nix-command flakes' develop path:. -c bash -lc "cd research/fm/lean && lake build CertifiedAtc"` passes.
- [ ] The current Lean tree is scanned for `sorry` and `admit`; any `axiom` occurrences are either absent or explicitly inventoried before further proof work starts.

## Done summary
Validated the Flow-Next and Lean baseline for the campaign. The default flake shell exposes `.flow/bin/flowctl`, validates all Flow-Next state, and detects the local `.flow` directory. The `r1` flake shell also exposes `flowctl` and detects `.flow`.

Ran the root Lean build for `CertifiedAtc` successfully. A broad text scan finds existing non-hole uses of `admit` as a constructor/string, but the targeted proof-hole scan found no standalone `sorry`/`admit`, no `by sorry`/`by admit`, and no `axiom` declarations under the root-gated Lean tree.
## Evidence
- Commits:
- Tests: nix --extra-experimental-features 'nix-command flakes' develop path:. -c flowctl validate --all --json, nix --extra-experimental-features 'nix-command flakes' develop path:. -c flowctl detect --json, nix --extra-experimental-features 'nix-command flakes' develop path:.#r1 -c flowctl detect --json, nix --extra-experimental-features 'nix-command flakes' develop path:. -c bash -lc 'cd research/fm/lean && lake build CertifiedAtc', grep -R -n -E '^[[:space:]]*(sorry|admit)[[:space:]]*$|by[[:space:]]+(sorry|admit)\b|:=[[:space:]]*(sorry|admit)\b' research/fm/lean/CertifiedAtc research/fm/lean/CertifiedAtc.lean || true, grep -R -n -E '\baxiom\b' research/fm/lean/CertifiedAtc research/fm/lean/CertifiedAtc.lean || true
- PRs: