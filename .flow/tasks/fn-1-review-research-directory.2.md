---
satisfies: [R2]
---

## Description
Audit `research/fm` and `research/fm/lean` for proof/status truthfulness. The task should determine which Lean artifacts are built through the `CertifiedAtc` import graph, which tracked files are direct imports, transitive imports, tracked support, historical, or unreachable, whether TLA claims have backing files, and whether FM living docs match the current tracked proof surface.

**Size:** M
**Files:** `research/fm/`, `research/fm/lean/`, `docs/design/clearance-model-design.md`

## Approach
- Use `git ls-files 'research/fm/lean/**/*.lean'` as the authoritative tracked Lean file inventory. Use filesystem `find` only as a supplemental check for untracked or ignored local artifacts.
- Treat `research/fm/lean/CertifiedAtc.lean` as the root of the tracked Lean import graph, not as a flat list of every gated module.
- Classify tracked Lean modules before judging prose claims: direct top-level import, transitive reachable import, tracked support, historical, or unreachable/accidentally ungated.
- Verify docs that say `proved`, `checked`, `certified`, or equivalent against concrete files, import reachability, tool versions, and gates.
- If local Lean tooling is unavailable, record the exact command and blocker as unverified evidence.

## Investigation targets
**Required** (read before coding):
- `research/fm/README.md:41` - FM current status.
- `research/fm/PROJECT_STATUS.md:3` - current execution status.
- `research/fm/lean/README.md:13` - Lean default next work and build guidance.
- `research/fm/lean/CertifiedAtc.lean:1` - root import registry for the tracked proof surface.
- `research/fm/lean/lean-toolchain:1` - Lean version pin.
- `research/fm/lean/lakefile.lean:1` - Lake package definition.
- `docs/design/clearance-model-design.md:360` - current TLA claim checkpoint.

**Optional** (reference as needed):
- `research/fm/parity_inventory.md:3` - delivered/open branch status.
- `research/fm/refinement_inventory.md:3` - drift-control inventory.
- `research/fm/route_bearing_scope.md:40` - route-bearing widening scope.
- `research/fm/completion_milestones.md:3` - scoped completion milestone status.

## Key context
A tracked Lean file that is not directly imported by `CertifiedAtc.lean` may still be covered transitively. The relevant failure case is a tracked Lean module that is unreachable from the `CertifiedAtc` import graph and is still being treated as proved or current. Do not treat generated `r1-smoke` snapshots as proof unless they were promoted into tracked `research/fm/lean` and built.

Relevant external references for the implementer:
- Lean proof validation: https://lean-lang.org/doc/reference/latest/ValidatingProofs/
- Lake reference: https://lean-lang.org/doc/reference/latest/Build-Tools-and-Distribution/Lake/
- Lean 4 reference for the pinned Lean family: https://lean-lang.org/doc/reference/latest/

## Acceptance
- [ ] The tracked Lean module inventory is derived from `git ls-files 'research/fm/lean/**/*.lean'`, with any extra filesystem-only files classified separately as untracked/generated/local artifacts.
- [ ] Every tracked Lean module under `research/fm/lean/CertifiedAtc/` is classified as direct top-level import, transitive reachable import, tracked support, historical, or unreachable/accidentally ungated.
- [ ] Lean build evidence is captured with `nix-shell -p lean4 --run 'cd research/fm/lean && lean --version && lake --version && lake build CertifiedAtc'`, or the inability to run it is recorded as an explicit verification blocker.
- [ ] Captured `lean --version` and `lake --version` are compared against `research/fm/lean/lean-toolchain`, and mismatches are reported as environment or toolchain findings.
- [ ] FM living docs are checked for stale theorem/status claims, especially `README.md`, `PROJECT_STATUS.md`, `lean/README.md`, `AGENT_GUIDE.md`, `parity_inventory.md`, and `refinement_inventory.md`.
- [ ] Claims about TLA artifacts are backed by actual `.tla`/`.cfg` files or reported as documentation mismatches.
- [ ] Findings distinguish proof defects, documentation defects, historical notes, unreachable-module findings, and unverified local-environment issues.

## Done summary
FM audit complete. The Lean build command could not run in this shell because nix-shell cannot resolve <nixpkgs> and lean/lake/elan are absent from PATH. The corrected tracked Lean import audit must include research/fm/lean/CertifiedAtc.lean explicitly; with that correction there are 99 tracked Lean files, 89 reachable tracked modules including the root, and 10 tracked modules unreachable from CertifiedAtc. Living FM docs cite several of those unreachable files as current optional slices, so the status surface is inconsistent until the files are imported, retired, or marked historical. No TLA or CFG files exist under research, while docs/design/clearance-model-design.md claims ClearanceLifecycle.tla and ClearanceActivation.tla exist.
## Evidence
- Commits:
- Tests:
- PRs: