# Review research directory

## Overview
Review `research/` as a set of separate assurance surfaces, not as one Gradle-covered code tree. The goal is to produce an evidence-backed review of what is actually trusted, what is generated or operational state, what is historical documentation, and where status or scope claims drift from checked artifacts.

The review covers:

- `research/fm` as the Lean/FM proof project and status-document stack.
- `research/fm/lean` as the tracked Lean build surface, with `CertifiedAtc.lean` as the root of the import graph to audit.
- `research/fm/r1-smoke` as ignored operations workspace, not proof source of truth.
- `research/tools/requirements-spike` as the declared-slice regulatory requirements pipeline.
- `research/tools/r1` and `research/tools/atc-reviewer-spike` as adjacent research tooling.
- `research/pdf`, `research/txt`, and source inventory artifacts only as traceability inputs, not as a request to manually review every source document.

## Scope
In scope:

- Classify tracked proof, generated output, historical/design records, source artifacts, and runnable gates.
- Check FM theorem/status claims against tracked Lean files, direct and transitive import reachability from `CertifiedAtc`, tool versions, and build evidence.
- Check requirements-registry scope claims against `DECLARED_SLICE.md`, source-section ledger artifacts, source inventory artifacts, and existing audit scripts.
- Check research tooling docs for honest boundaries between generation, promotion, review, and proof.
- Produce findings grouped by severity with owner/target surface and follow-up routing.

Out of scope:

- Implementing new proofs, new extraction logic, new registry widening, or new review harnesses.
- Treating ignored local run outputs as authoritative proof without promotion into tracked files.
- Treating dated design decisions as living status documents; they may be superseded by new dated decisions if process or scope changed.

## Stakeholders
End users are not directly affected. Developers and research maintainers are affected because the review can change which `research/` claims are trusted, what gates must be run, and which docs or backlog entries need correction. Operations are affected where local-only workspaces, absolute paths, Ollama/frontier-model access, or Nix/toolchain assumptions determine reproducibility.

## Approach
Start with an inventory task that defines review categories, records its inventory in task evidence and the final review report, and excludes noisy generated/vendor directories. Then split the audit into FM proof/status, requirements registry scope, and research tooling boundaries. Finish by synthesizing findings and routing concrete follow-up into docs, `.plan`, or future Flow work.

Assumptions for the review:

- Tracked files are the primary review target. Ignored/local operations state is inspected only to verify it is not being treated as proof truth.
- A tracked Lean file that is not directly imported by `CertifiedAtc.lean` is not automatically ungated. The audit must distinguish direct top-level imports, transitive imports reachable from the `CertifiedAtc` import graph, tracked support files, historical files, and unreachable tracked modules.
- Missing `.tla`/`.cfg` artifacts behind present-tense TLA claims are must-fix documentation defects unless another checked source is found.
- For requirements registry scope, live contract/ledger files are the operational source of truth; dated design decisions are historical records and should be superseded with a new dated note only if the process or counted state materially changed.
- `audit_quotes.py` requires a per-document ingest output root. The lookup rule is: use an output root explicitly produced during the review or named by tracked run/status evidence and confirmed to exist; do not infer an arbitrary newest temp directory. If no such root exists and the review does not deliberately run the documented ingest flow, the missing reproduction input is itself recorded as a verification blocker for quote/source traceability.

## Quick commands
```bash
git ls-files 'research/fm/lean/**/*.lean'
find research -path 'research/fm/r1-smoke' -prune -o -path '*/.lake' -prune -o -path '*/node_modules' -prune -o -path '*/__pycache__' -prune -o -type f -print | sort
nix-shell -p lean4 --run 'cd research/fm/lean && lean --version && lake --version && lake build CertifiedAtc'
nix-shell -p python3 --run 'python3 research/tools/requirements-spike/test_quality_gates.py && python3 research/tools/requirements-spike/test_override_contracts.py && python3 research/tools/requirements-spike/audit_registry_reproducibility.py --report /tmp/research-registry-reproducibility-report.json'
nix-shell -p python3 --run 'python3 research/tools/requirements-spike/audit_quotes.py --output-root <latest-ingest-output-root>'
cd research/tools/r1 && npm ci && npm test && npm run build
```

If any command cannot run in the local shell, the review must record that loudly as unverified evidence, including the command and environment blocker.

## Risks / Dependencies
- `research/` is outside the Gradle module graph, so `./gradlew build` does not validate these artifacts.
- Long FM status docs can drift from the tracked Lean surface.
- Direct import checks can miss transitive Lean coverage; the audit must reason over import reachability.
- Generated or ignored workspaces can create false confidence if treated as promoted proof.
- Requirements-registry counts can drift between living ledger files and dated design records.
- Regulatory or phraseology claims must stay source-cited; this plan does not authorize uncited ATC-law conclusions.

## Review considerations
FP / type safety: mostly N/A for review work, but the equivalent bar is proof/trust totality: the tracked Lean module list must come from git-tracked files, every tracked Lean/proof claim must be classified by import-graph reachability and gate status, and no unreachable, untracked, or generated artifact may silently count as verified.

Test architecture: use the strongest existing gates first: Lean/Lake build with version capture for tracked FM, requirements-spike quality/override tests plus registry reproducibility and quote/source audits for registry artifacts, and `npm ci` then `npm test`/`npm run build` for `r1`. Do not replace deterministic gates with ad hoc sampling when a local gate exists.

Impact: the main failure mode is false assurance. The review should prefer explicit `unverified`, `historical`, `unreachable`, or `ops-only` classifications over optimistic prose.

Operational correctness: applicable where research artifacts make ATC law or phraseology claims. Those claims must be cited to exact sources per project commandments; otherwise the review should flag them as unverified.

## Acceptance
- **R1:** The review inventory separates tracked proof, generated/ignored operations state, historical design records, source artifacts, and runnable gates for every major `research/` surface, and records the inventory in task evidence plus the final review report.
- **R2:** FM proof/status claims are checked against `research/fm/lean`, the direct and transitive import graph rooted at `CertifiedAtc`, Lean/Lake version and build evidence, and missing-or-present TLA artifacts; unreachable tracked modules are explicitly classified.
- **R3:** Requirements-spike conclusions are grounded in declared-slice, source-ledger, and source-inventory artifacts plus existing quality, override, registry reproducibility, and quote/source audit scripts; the review does not overstate coverage as full-document or full-corpus extraction.
- **R4:** Research tooling is reviewed against its stated maturity: `r1` as a generator/promoter workflow and `atc-reviewer-spike` as a Phase -1 bounded spike, with generated output kept distinct from trusted proof.
- **R5:** Final findings are grouped as must-fix, should-fix, or note, with owner/target surface, evidence path, verification status, and routing to immediate doc correction, `.plan`, or future Flow work.

## Early proof point
Task `fn-1-review-research-directory.1` validates the core approach by producing the inventory and category rules that all later review tasks use. If it cannot separate tracked truth from generated or historical state cleanly, re-scope the later tasks before starting the FM and registry audits.

## Requirement coverage

| Req | Description | Task(s) | Gap justification |
| --- | --- | --- | --- |
| R1 | Research inventory separates truth categories and gates | fn-1-review-research-directory.1 | - |
| R2 | FM proof/status claims audited against import reachability, tracked artifacts, versions, and gates | fn-1-review-research-directory.2 | - |
| R3 | Requirements registry scope audited against declared slice, ledgers, source inventory, and required audit scripts | fn-1-review-research-directory.3 | - |
| R4 | Research tooling maturity and generated-output boundaries audited | fn-1-review-research-directory.4 | - |
| R5 | Findings synthesized and routed | fn-1-review-research-directory.5 | - |

## References
- `settings.gradle.kts:3` and `build.gradle.kts:17` show `research/` is outside Gradle subprojects.
- `AGENTS.md:140` describes `research/` as not built by Gradle; `AGENTS.md:147` requires FM status docs to stay aligned.
- `research/fm/README.md:41`, `research/fm/PROJECT_STATUS.md:3`, and `research/fm/lean/README.md:13` are the living FM status surfaces.
- `research/fm/lean/lean-toolchain:1`, `research/fm/lean/lakefile.lean:1`, and `research/fm/lean/CertifiedAtc.lean:1` define the Lean build surface.
- `.gitignore:29` and `research/fm/lean/README.md:23` identify `research/fm/r1-smoke/` as operations workspace.
- `research/pdf/`, `research/txt/`, and `research/tools/requirements-spike/quality/source_inventory/source_inventory_2026-04-29/source_document_inventory.md:5` define the source-artifact traceability surface.
- `research/tools/requirements-spike/README.md:5`, `RUNBOOK.md:12`, and `registry/ollama_first/DECLARED_SLICE.md:5` define the requirements-registry scope contract.
- `research/tools/requirements-spike/quality/source_section_ledger/source_section_ledger_2026-04-30/source_section_ledger.md:11` and `wiki/design-decisions/2026-04-30-declared-slice-and-section-ledger.md:26` are known count-drift checkpoints.
- `research/tools/requirements-spike/audit_registry_reproducibility.py:1` and `research/tools/requirements-spike/audit_quotes.py:1` define required registry reproducibility and quote/source audit behavior.
- `research/tools/r1/README.md:7`, `research/tools/r1/AGENTS.md:54`, and `research/tools/atc-reviewer-spike/README.md:3` define adjacent tooling maturity.
