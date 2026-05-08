# fn-4-richer-airspace-geometry-widening.7 Record geometry branch stopping rule

## Description
Record the branch stopping rule and prepare the epic for completion review.

This task should not add new semantics. It closes the loop by documenting what the geometry branch closed, what remains intentionally out of scope, which runtime/Lean gates passed, and what future event would reopen the branch.

If r1 was used during the branch, record its output as candidate material only and note whether anything was promoted through Flow-Next. If r1 was not used, state that it was unnecessary for this branch.
## Acceptance
- [ ] A final geometry branch frontier/stopping note exists in the active FM docs.
- [ ] The note lists closed theorem surfaces, intentional exclusions, and reopening triggers.
- [ ] Root `CertifiedAtc` build evidence is recorded after all proof/doc updates.
- [ ] Kotlin/Gradle evidence is recorded if runtime behavior changed.
- [ ] Flow-Next validates with `flowctl validate --all --json`.
- [ ] The epic is ready for completion review.
## Done summary
Recorded the geometry branch stopping rule in the active airspace geometry scope note and mirrored it in the Flow-Next frontier. The branch is closed as an optional partial package: finite boundary/profile source facts, extraction/resolution/bridge support, additive declared-profile route touch support, and source-level reachable/authority-gated issuance are root-gated; continuous geometry, operational-sector/published-VFR/InClass semantics, runtime behavior changes, and delivered-registry promotion remain out of scope. Recorded that the r1 capacity queue was background candidate material only and no r1 output was promoted through this branch.
## Evidence
- Commits:
- Tests: nix --extra-experimental-features 'nix-command flakes' develop path:. -c bash -lc 'cd research/fm/lean && lake build CertifiedAtc', nix --extra-experimental-features 'nix-command flakes' develop path:. -c flowctl validate --all --json, git diff --check -- <tracked touched FM docs and Lean files>; grep trailing-whitespace check for untracked FM docs, grep -R -nE '(^|[^A-Za-z0-9_])(sorry|admit|axiom)([^A-Za-z0-9_]|$)' <touched Lean files> # no matches
- PRs: