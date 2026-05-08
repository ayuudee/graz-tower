---
satisfies: [R5]
---

## Description
Synthesize the review into actionable findings and route follow-up. This task depends on the FM, requirements, and tooling audits and should produce the final review result in a form that can be acted on without losing evidence or silently deferring known problems.

**Size:** S
**Files:** `.plan`, `.flow/`, affected docs identified by tasks 2-4

## Approach
- Lead with findings, ordered by severity and grounded in file/line references.
- Route each finding to one of: immediate fix, `.plan` item, future Flow task, or accepted historical note.
- If a documentation or status claim is known wrong and not fixed immediately, make the deferral visible in `.plan` per repo policy.
- Include verification commands run and blockers encountered.

## Investigation targets
**Required** (read before coding):
- `.plan` - canonical backlog for known issues and deferred work.
- `.flow/specs/fn-1-review-research-directory.md` - epic acceptance requirements.
- Task done summaries/evidence from `fn-1-review-research-directory.1`, `.2`, `.3`, and `.4`.

**Optional** (reference as needed):
- Docs named by docs-gap findings, especially `research/fm/README.md`, `research/fm/PROJECT_STATUS.md`, `research/fm/lean/README.md`, `research/tools/requirements-spike/README.md`, and `research/tools/requirements-spike/registry/ollama_first/DECLARED_SLICE.md`.

## Key context
A review that discovers known-wrong behavior or claims must not leave them silently accepted. Either fix the affected doc/status artifact in the same work stream or add a visible backlog item with impact and effort.

## Acceptance
- [ ] Final review output groups findings as must-fix, should-fix, or note, with file/line evidence and owner/target surface.
- [ ] Each finding records verification status: verified by command, verified by file inspection, unverified due to environment blocker, or accepted historical note.
- [ ] Immediate documentation corrections are made where low-risk and directly supported by evidence; any deferred known issue is added to `.plan` with impact/effort.
- [ ] The final summary includes commands run, commands not run, why, and the disposition of generated audit reports such as `/tmp/research-registry-reproducibility-report.json`.
- [ ] Requirement coverage for R1-R5 is explicitly confirmed in the task evidence or done summary.

## Done summary
Final review synthesized and routed. Must-fix findings: RRD-1 FM import graph and living docs disagree, and RRD-2 present-tense TLA status claims have no checked artifacts. Should-fix findings: RRD-3 documented research gates are not reproducible from the current shell, RRD-4 the quote audit lacks a durable per-document input artifact, RRD-5 registry scope counts need reconciliation, and RRD-6 adjacent research tooling is local-only rather than repo-reproducible. Notes: the requirements registry core Python gates pass under the available Nix-store Python; current scope docs correctly say declared 22-window slice rather than full-corpus coverage; r1 and atc-reviewer-spike maturity labels are mostly honest but their documented full verification lanes are blocked locally. Added .plan items RRD-1 through RRD-6. No immediate doc corrections were made because each correction requires an owner decision about import versus retire, restore versus amend, or promote versus keep-local. /tmp/research-registry-reproducibility-report.json is temporary review evidence, not a repo artifact.
## Evidence
- Commits:
- Tests:
- PRs: