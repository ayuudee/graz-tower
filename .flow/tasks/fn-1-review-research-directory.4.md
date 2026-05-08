---
satisfies: [R4]
---

## Description
Audit the adjacent research tooling under `research/tools/r1`, `research/fm/r1-smoke`, and `research/tools/atc-reviewer-spike`. The goal is to verify that each tool is documented at the right maturity level and that generated output is not confused with trusted proof or production behavior.

**Size:** M
**Files:** `research/tools/r1/`, `research/fm/r1-smoke/`, `research/tools/atc-reviewer-spike/`, `.gitignore`

## Approach
- Review `r1` as a formalisation generator/runner whose outputs become trusted only after manual promotion and Lean build.
- Review `r1-smoke` as ignored local operations workspace, checking for boundary clarity rather than treating its run artifacts as authoritative source.
- Review `atc-reviewer-spike` against its README claim as a Phase -1 bounded spike.
- Use the checked-in Node lockfile for reproducible dependency setup before `r1` build/test checks.
- Verify Node/Python commands where local dependencies allow; otherwise record the blocker and avoid claiming coverage.

## Investigation targets
**Required** (read before coding):
- `research/tools/r1/README.md:7` - current runner status.
- `research/tools/r1/AGENTS.md:54` - assistant/prof boundary rules.
- `research/tools/r1/package.json:9` - build and test scripts.
- `research/tools/r1/package-lock.json` - dependency setup source for `npm ci`.
- `.gitignore:29` - ignored `r1-smoke` workspace.
- `research/fm/lean/README.md:23` - `r1-smoke` operations-workspace rule.
- `research/tools/atc-reviewer-spike/README.md:3` - Phase -1 spike scope.

**Optional** (reference as needed):
- `research/tools/r1/docs/nightly-local-rollout-plan.md` - runner rollout context.
- `research/tools/r1/docs/work-generation-process.md` - generated work process.
- `research/tools/atc-reviewer-spike/run_spikes.py` - spike runner behavior.

## Key context
The repo's commandment against silent workarounds applies strongly here: a tool that cannot be validated locally should be recorded as unverified, not silently accepted because it is research-only. Since `research/tools/r1/package-lock.json` exists, the expected setup is `npm ci`, not an implicit pre-existing `node_modules` directory.

## Acceptance
- [ ] `r1` documentation and scripts are checked for alignment with the stated generator/promoter model and Lean-as-judge boundary.
- [ ] `npm ci`, `npm test`, and `npm run build` are run under `research/tools/r1`, or their blockers are recorded explicitly.
- [ ] `research/fm/r1-smoke` is confirmed as ignored operations state and not used as a proof source unless promoted into tracked Lean.
- [ ] `atc-reviewer-spike` is checked against its stated Phase -1 scope and not judged by production-harness expectations.
- [ ] Findings separate reproducibility issues, maturity-label issues, local environment blockers, and actual tool defects.

## Done summary
Research tooling audit complete. The r1 Formalisation Workspace Runner has a package-lock, TypeScript scripts, generated dist output, and local node_modules, but it is entirely untracked and cannot be rebuilt in the current shell because node/npm/npx are absent and nix-shell cannot resolve <nixpkgs>. Its default.nix also imports <nixpkgs>, so the documented shell is currently unavailable here. The r1 operational boundary is clear in docs: it is a code-first Lean backlog executor, with assistant/prof control boundaries and a forced stop rule. The atc-reviewer-spike is also untracked local tooling; its README accurately frames it as a tiny fixed-case Phase -1 spike and writes artifacts under runs/<run-id>. Its CLI help runs with the available Nix-store Python, but documented execution via nix-shell is blocked by the same environment issue and real runs depend on local model endpoints.
## Evidence
- Commits:
- Tests:
- PRs: