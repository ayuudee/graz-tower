---
satisfies: [R1]
---

## Description
Establish the review inventory for `research/` before judging any individual claim. Define the truth categories, major surfaces, in-scope paths, out-of-scope generated/vendor paths, source-artifact paths, and verification commands that later tasks must use.

**Size:** M
**Files:** `research/`, `research/pdf/`, `research/txt/`, `research/tools/requirements-spike/quality/source_inventory/`, `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `AGENTS.md`

## Approach
- Treat `research/` as multiple surfaces with different maturity levels, not as a single Gradle-covered module.
- Classify every major surface as tracked proof/status, generated or ignored operations state, regulatory/source artifact, or research tooling.
- Include source artifacts explicitly: `research/pdf`, `research/txt`, and the source inventory, while avoiding manual corpus-wide content review.
- Reuse the repo's existing boundary statements rather than inventing new scope rules.
- Make unverified evidence explicit when a command cannot run locally.
- Record the inventory in this task's `## Evidence` section and carry a summarized version into the final review report.

## Investigation targets
**Required** (read before coding):
- `AGENTS.md:140` - project-level statement that `research/` is not built by Gradle.
- `settings.gradle.kts:3` - included Gradle modules.
- `build.gradle.kts:17` - subproject task wiring.
- `.gitignore:29` - ignored FM operations workspace.
- `research/fm/AGENT_GUIDE.md:23` - FM reading order and current-truth guidance.
- `research/tools/requirements-spike/README.md:5` - requirements-spike current direction.
- `research/tools/requirements-spike/quality/source_inventory/source_inventory_2026-04-29/source_document_inventory.md:5` - source text/PDF inventory and coverage boundary.
- `research/pdf/` - regulatory/source PDFs to classify as source artifacts.
- `research/txt/` - text extracts to classify as source artifacts.
- `research/tools/r1/README.md:7` - runner current status.

**Optional** (reference as needed):
- `research/tools/atc-reviewer-spike/README.md:3` - reviewer spike scope.
- `wiki/design-decisions/2026-04-21-fm-overnight-proof-queue-promotion.md:12` - promotion boundary precedent.

## Key context
Use category labels consistently in later tasks: `tracked-proof`, `tracked-status`, `generated-ops`, `historical-design`, `source-artifact`, `research-tool`, `unreachable`, and `unverified`. The review should inspect ignored/generated paths only enough to ensure they are not being treated as trusted source of truth.

## Acceptance
- [ ] The review inventory lists each major `research/` surface with category, owner/target surface, primary files, and expected gate or evidence standard.
- [ ] `research/pdf`, `research/txt`, and the source inventory are explicitly classified as source-artifact surfaces, with the inventory used as the control surface for traceability rather than manual corpus-wide review.
- [ ] Exclusions are explicit for `.lake`, `node_modules`, `__pycache__`, and `research/fm/r1-smoke` run artifacts, with `r1-smoke` retained only for operations-boundary checks.
- [ ] The inventory records that Gradle does not validate `research/`, and names the separate Lean, Python, and Node checks expected later.
- [ ] The inventory is written into this task's evidence and summarized in the final review report so later tasks do not depend on chat-only context.
- [ ] Ambiguous sources of truth are recorded for later tasks rather than resolved silently.

## Done summary
Established the research review inventory and category rules. Major surfaces: tracked FM proof/status (`research/fm`, 98 tracked Lean files under `research/fm/lean`), source artifacts (`research/pdf` 9 PDFs, `research/txt` 12 extracts, source inventory), requirements-spike scope docs and scripts, adjacent research tools (`research/tools/r1`, `research/tools/atc-reviewer-spike`), generated/ignored operations state (`research/fm/r1-smoke`, .lake, node_modules, __pycache__, run outputs), and historical design/wiki records. Confirmed Gradle does not cover `research/`; later tasks must use Lean/Lake, Python, and Node checks. Ambiguity carried forward: much of `research/tools/*` and requirements registry state is currently untracked local worktree content, so later audits must distinguish committed baseline from local research artifacts.
## Evidence
- Commits:
- Tests:
- PRs: