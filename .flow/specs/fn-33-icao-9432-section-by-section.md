# ICAO 9432 section-by-section conformance workflow spike

## Goal & Context

Use one source, end to end, to test whether the source-unit registry can drive executable simulator conformance work section by section. The goal is not to finish the whole regulatory corpus. The goal is to learn whether this workflow gives us a reliable map from extracted source units to tests, trace evidence, blockers, and follow-on implementation work.

The chosen source is `icao9432-extracted` from `research/tools/requirements-spike/registry/ollama_first/candidates/`. It currently has 166 accepted source-unit records across 23 sections. That is large enough to include phraseology, readback, aerodrome movement, circuit, takeoff, landing, go-around, after-landing, vehicles, and abnormal/emergency material, but it is materially smaller than `cap413-extracted`, `h01-extracted`, and `icao4444-extracted`.

## Architecture & Data Models

This spike stays test/research first. Production behavior may be touched only when a section exposes a small, defensible implementation gap that can be completed with focused tests.

Core artifacts:

- A generated section inventory for all accepted `icao9432-extracted` source units.
- A progress ledger that records every accepted source unit once, grouped by section.
- A source-unit classification per record: `covered`, `new_case_needed`, `blocked_by_existing_red`, `blocked_by_model_gap`, `not_sim_scope`, `duplicate_support`, or `needs_domain_review`.
- Executable Kotlin conformance cases for the first representative section slice, using the code-only DSL from FN31 where that remains useful.
- A synthesis report that records what scaled, what did not, and what the next workflow spike should change.

The ledger is the important product: every source unit must either point at an executable assertion/test path or have an explicit reason why it does not yet do so.

## Workflow

1. Build the ICAO 9432 inventory and section ledger from the registry JSON. Do this mechanically from the accepted records so the ledger cannot omit a source unit silently.
2. Pick a first representative section or tightly related section pair. Prefer a section that exercises existing simulator behavior and evidence traceability without immediately depending on the known FN31 go-around red baseline.
3. Implement the first section slice as code-only conformance cases. Each case should assert both simulator/controller outcome and the expected source-unit evidence where the current system can represent it. Where it cannot, record the gap loudly in the ledger and `.plan` if it is a real deferred project issue.
4. Continue section-by-section classification for the remaining ICAO 9432 sections. Do not fake executable coverage: mark blockers explicitly.
5. Synthesize the workflow: whether source-unit evidence should replace or augment current regulation refs, how much work is mechanical, where scenario builders help, and what a full production version would require.

## API Contracts

No public runtime API is promised by this spike.

For test code and artifacts, the expected contract is:

- Source-unit references are stable enough to be cited by source id, section id, and record id.
- A conformance case can name its source units independently of prose paragraph labels.
- Evidence assertions should prefer direct source-unit references over lossy regulation-reference proxies.
- Any conversion from source-unit ref to existing `RegulationRef` must be marked as provisional in spike artifacts.

## Edge Cases & Constraints

- The current branch has known unrelated red baseline work around FN31 go-around sequencing and a `:sim` compile issue. This spike must not claim those are solved unless it actually fixes them.
- Some ICAO 9432 source units describe phraseology or operational procedure that is outside current simulator scope. Those must be classified, not ignored.
- Some records will duplicate or support requirements already better covered by CAP 413 / ICAO 4444. Classify them as support rather than creating redundant tests.
- Operational claims must cite the source unit. Do not introduce uncited ATC-law or phraseology assertions.
- This is explicitly allowed to be thrown away. Keep production coupling low until the workflow proves itself.

## Acceptance Criteria

- [ ] Every accepted `icao9432-extracted` source unit appears exactly once in the generated inventory/ledger.
- [ ] Every ICAO 9432 section has a section-level status and notes on simulator relevance.
- [ ] At least one representative section slice has executable Kotlin conformance cases or a documented blocker that explains why executable cases are not currently honest.
- [ ] Cases that run assert behavior and, where representable, expected evidence/source references.
- [ ] Any discovered implementation gap that is not fixed is added to `.plan` with impact/effort.
- [ ] The synthesis report recommends whether to continue with ICAO 9432, switch sources, build a scenario builder, change evidence modeling, or split the work another way.

## Boundaries

In scope:

- ICAO 9432 registry records only.
- Section-by-section classification and first executable slices.
- Test/research artifacts and small supporting test DSL changes.

Out of scope:

- Completing CAP 413, ICAO 4444, H01, EPPLS, or the entire registry.
- Training a model.
- Building a production-grade scenario-generation engine.
- Hiding known red tests or adding skip lists.

## Decision Context

ICAO 9432 is the best first source for this workflow because it is operationally dense but bounded. It gives enough variety to test the idea: communications, readback, taxi, takeoff, circuit, landing, go-around, after-landing, vehicles, and emergency material. Smaller sources are unlikely to teach enough; larger sources would make workflow mistakes expensive.

The main design uncertainty is whether conformance should be expressed as declarative data files, code-only fixtures, or generated code from a ledger. This spike starts with code-only fixtures because they can use normal Kotlin types, compose directly with existing golden/test harness behavior, and fail loudly when the domain model cannot express a requirement.

## Review considerations

### FP / type safety

The spike should avoid raw stringly evidence where practical. If direct `SourceUnitRef` does not exist yet, the report must call that out rather than pretending `RegulationRef` is equivalent. Test helpers should keep classifications total and exhaustive. No catch-all `else` should turn unclassified records into pass states.

### Test architecture

Tests should exercise real simulator/controller behavior or real evidence contracts. They should not assert only registry shape or facts already guaranteed by the type system. A generated inventory check is useful only as a guard that every accepted source unit is accounted for in the ledger.

### Impact

This should initially couple to test code and research artifacts, not production runtime paths. The reversible path is deletion of the spike artifacts and test-only DSL additions. If production source-evidence fields become necessary, that must be planned separately because it affects trace contracts across modules.

### Operational correctness

Every conformance case must cite the underlying ICAO 9432 source unit. If a source unit is converted into an ATC behavior claim, the case/report must preserve the citation and avoid inventing stronger operational rules than the source supports.
