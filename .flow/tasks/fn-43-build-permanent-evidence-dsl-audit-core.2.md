# fn-43-build-permanent-evidence-dsl-audit-core.2 Implement provenance-bearing evidence facts

## Description
Define the evidence fact layer that replaces the spike's thin `SimObservation`
surface for permanent-facing tests.

Build:
- stable fact ids;
- provenance with scenario id, origin, sequence, sim time where applicable,
  source event/transmission id where available, and extraction path;
- typed fact payloads for instructions, pilot reports, pilot transmissions,
  final aircraft summary, and sample/domain facts;
- adapters for synthetic protocol evidence and the LOWG circuit observation
  path needed by the 20-case suite.
- a documented stability contract: global order comes from trace/event sequence
  where available, source transmission/event id is carried where available, and
  deterministic adapter-local order is only a fallback.

Facts must be observations only. They must not encode conformance conclusions.

## Acceptance
- [ ] Every projected fact has a stable id and provenance.
- [ ] Fact ordering is stable even for equal timestamps.
- [ ] Equal-time ordering is proven by a focused test.
- [ ] Fact ids and order are deterministic across reruns.
- [ ] Synthetic protocol and sim origins are represented distinctly.
- [ ] Evidence assertions do not consume raw `SimState`.
- [ ] Tests prove facts are emitted for the LOWG instruction/report paths used by FN41.

## Done summary
Implemented provenance-bearing evidence facts for synthetic protocol and LOWG sim evidence. Facts now carry stable ids, origin, scenario id, deterministic sequence, sim time, source transmission id where available, extraction path, and typed payloads for instructions, pilot transmissions, reports, aircraft summaries, and samples. The LOWG fact adapter shares the existing observation trace runner, and TransmissionRecord now preserves TransmissionId for provenance.
## Evidence
- Commits: this commit
- Tests: nix-shell --run './gradlew :sim:jvmTest --tests "*.EvidenceFactsTest" --tests "*.EvidenceSourceCatalogTest"', nix-shell --run './gradlew detekt', git diff --check
- PRs:
