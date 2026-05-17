# Cited Rule-to-Test Exploration Spike

## Goal & Context

The FN20/FN23/FN30 source-unit work produced a curated, quote-audited registry of accepted source units, but that corpus is not yet connected to simulator behavior, tests, adversarial scenario generation, or developer workflow.

This epic is a spike to explore what is possible and worthwhile. It is not intended to build the final production system. The goal is to learn which approaches turn cited source units into useful simulator confidence, what fails in practice, and what follow-on epics should be created with realistic scope.

The central question is: how can a cited source unit become actionable evidence about simulator behavior?

## Architecture & Data Models

Use the registry as an evidence source, not as runtime product state. The spike should operate on a small selected slice of accepted source units and produce reviewable artifacts under `research/tools/requirements-spike/quality/` or another clearly named research path.

The exploratory lanes are:

1. Rule-to-sim coverage mapping: map source units to domain concepts, current implementation paths, existing tests, missing tests, and known design gaps.
2. Cited vertical slice: pick a narrow operational family and attempt an end-to-end path from source units to structured rule claims to executable or manually reviewable tests.
3. Adversarial probing: ask a model to generate scenarios that try to violate a small number of cited rules, then assess whether those scenarios can be translated into simulator tests or useful defect reports.
4. Retrieval/grounding assessment: test whether lightweight retrieval over source units is enough to support rule explanation and scenario generation, before considering any model training.
5. Synthesis: produce a recommendation report that names which follow-on epics should exist, which approaches should be discarded, and what infrastructure is required.

Any structured rule shape produced by the spike should be treated as a research artifact unless and until it is reviewed separately for production typing, totality, and simulator integration.

## API Contracts

No production API contract is expected from this spike.

Research artifacts should be plain, inspectable files with stable schemas where practical:

- `coverage_matrix.*`: source unit id, citation/provenance, normalized rule claim, operational family, simulator concept, code path, existing test, missing behavior, confidence, notes.
- `vertical_slice_report.*`: selected rule family, selected source units, generated/hand-authored rule claims, attempted tests, pass/fail/manual-review outcomes, cited evidence.
- `adversarial_probe_report.*`: prompt shape, model/backend used, generated scenario, cited target rule, simulator translatability, result, defect signal.
- `recommendations.md`: proposed follow-on epics, rejected approaches, unresolved questions, and suggested sequencing.

If any script is added, it must fail loudly on malformed registry records or missing source citations. It must not silently skip invalid records.

## Edge Cases & Constraints

The spike should deliberately avoid pretending that all accepted source units are immediately testable. Some source units are definitions, phraseology examples, broad obligations, or process descriptions. The output should classify those honestly instead of forcing everything into a test.

Model-generated claims are untrusted. They require citation checks and human-reviewable evidence. If a model cannot preserve source-unit identity or cites the wrong unit, that is a finding, not something to patch over silently.

Adversarial scenarios may be valuable even when they cannot yet run in the simulator. The report should separate executable tests, manually translatable scenarios, unsupported simulator feature gaps, and invalid model outputs.

Training/fine-tuning is out of scope except as a recommendation topic. The spike should first test retrieval and prompting because they are cheaper, easier to audit, and easier to reverse.

## Acceptance Criteria

- [ ] A representative source-unit slice is selected and justified, with citations and operational family labels.
- [ ] A coverage matrix maps selected source units to simulator concepts, code paths, existing tests, missing tests, non-testable categories, and uncertainty.
- [ ] One narrow cited vertical slice is attempted end-to-end from source units to rule claims to simulator test or executable/manual test plan.
- [ ] One adversarial probing pass is attempted against a small set of cited rules, with generated scenarios classified by usefulness and translatability.
- [ ] Retrieval/grounding is assessed before recommending any model training or fine-tuning.
- [ ] The spike closes with a recommendation report that proposes concrete follow-on epics and explicitly states which ideas did not work.
- [ ] Any generated rule/test artifact keeps source-unit identity and citation evidence attached.
- [ ] Any automation added fails loudly on missing citations, malformed records, or unverifiable quote provenance.

## Boundaries

Out of scope:

- Exhaustively processing all accepted source units.
- Training or fine-tuning a model.
- Wiring source units directly into production runtime behavior.
- Large-scale generated test commits without a prior vertical-slice review.
- Treating model output as authoritative regulatory interpretation.

In scope:

- Small, evidence-rich experiments.
- Scripts or prompts that make the experiments reproducible.
- Manual review notes where automation is not yet trustworthy.
- Concrete recommendations for what to build next.

## Decision Context

The strongest near-term value is likely not “generate tests for everything.” It is learning which categories of source unit can produce reliable simulator checks, which require design work, and which are better used as explanation or documentation evidence.

The spike should therefore prefer breadth across approaches but depth within one vertical rule family. A good outcome may be a small number of high-confidence tests plus a much clearer map of future work.

## Review Considerations

FP / type safety: Do not introduce loosely typed production rule execution. Research artifacts may be JSON/CSV/Markdown, but any code should validate required fields and fail loudly on malformed records. If a future executable rule model is recommended, it should be typed, total, and citation-carrying.

Test architecture: The spike should distinguish integration tests that exercise simulator behavior from unit tests that only check parsing or structural properties. Any proposed tests must have a real behavioral oracle tied to cited source units.

Impact: Keep the simulator decoupled from the research registry during the spike. The registry should inform analysis and generated artifacts, not become a runtime dependency. Follow-on epics can decide whether a stable contract is warranted.

Operational correctness: Every ATC rule claim, phraseology claim, or scenario objective must carry source-unit citation/provenance. Regulatory or phraseology interpretations remain suspect until checked against the cited source text.

Reversibility: All spike artifacts should be easy to remove or archive. Any generated tests should be small and reviewed before promotion into normal test suites.
