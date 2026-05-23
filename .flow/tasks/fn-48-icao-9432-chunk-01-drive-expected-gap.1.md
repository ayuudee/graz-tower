---
satisfies: [R10]
---

## Description

Land the doctrinal prep work that must precede primitive builds — so reviewers see the refined two-actor model in context, and so the COMMS-1 / FN33-MODEL-1 source refs are ready when the per-blocker tasks run.

Two pieces:

1. **Refine the Test Completer role description in `AGENT_DIALOGUE.md`** to record that chunk-completion explicitly covers "building named blocker primitives + authoring the matching expected-gap tests", not solely "drive failing tests to green". Additive amendment.
2. **Add two `EvidenceSourceRef` constants** under `EvidenceSourceCatalog.ICAO9432` for the canonical IDs currently missing, using the file-private `source(canonicalId, title, claimScope)` helper at `EvidenceSourceCatalog.kt:76-81`:
   - `icao9432-extracted::communications_2_8_1_en::0a964f42b6100596` (COMMS-1, §2.8.1.4) — under a new `ICAO9432.Communications` sub-object (or extend if one exists).
   - `icao9432-extracted::readback_2_8_3_en::ac9111d240cfd2c2` (FN33-MODEL-1, §2.8.3.2) — under `ICAO9432.Readback` alongside existing readback constants.

**Critical**: both refs MUST also be added to `EvidenceSourceCatalog.All: Set<EvidenceSourceRef>` (line 245). `validateAgainstRegistry()` (line 255-258) walks `All` only — refs declared but not in `All` are silently unvalidated.

The two TransferCommunications IDs (`40382df156ad071e`, `b49ae03cbbb2d538`) at `:152-167` are already wired and already in `All` — do NOT redeclare.

**Size:** S
**Files:**
- `AGENT_DIALOGUE.md` (Actors section, Workflow section — additive)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceSourceCatalog.kt` (add constants under `ICAO9432`, add to `All` Set)

## Approach

- Use the file-private `source(canonicalId, title, claimScope)` helper. The returned `EvidenceSourceRef` carries an `EvidenceSourceRecord(canonicalId, title, claimScope)` (`:11-27, :29-50`). `title` and `claimScope` come from the accepted registry JSON `title` and `claimText` / `exactSourceQuotes[0]`.
- Place new constants alongside related existing ones. Mirror the `TransferCommunications` pattern at `:152-167`.
- **Update `All` Set** at line 245 to include both new refs — this is the validation gate.
- `validateAgainstRegistry()` walks `All`; new refs must resolve to accepted candidate JSON (`lifecycle.state == "accepted"`).
- AGENT_DIALOGUE.md: additive — keep existing Test Completer bullet structure, append the chunk-completion responsibility. Do NOT rewrite.

**Two validation pathways exist** (clarify in code comments if helpful):
- `SourceUnitCitationValidationTest` regex-scans for `SourceUnitRef("…")` string literals in the controller test tree. Typed `EvidenceSourceRef` constants do NOT go through this scanner — they use a different validation pathway.
- `EvidenceSourceCatalog.validateAgainstRegistry()` walks `All` and resolves each typed ref to its candidate JSON. THIS is the validation that applies to the new refs.

## Investigation targets

**Required**:
- `AGENT_DIALOGUE.md:8-30` — Actors section, current Test Completer description.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceSourceCatalog.kt:11-27` — `EvidenceSourceRecord`.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceSourceCatalog.kt:29-50` — `EvidenceSourceRef`.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceSourceCatalog.kt:76-81` — `source(...)` helper.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceSourceCatalog.kt:83-192` — `ICAO9432` object; existing patterns.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceSourceCatalog.kt:245` — `All: Set<EvidenceSourceRef>` (MUST update).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceSourceCatalog.kt:255-258` — `validateAgainstRegistry()`.
- `research/tools/requirements-spike/registry/ollama_first/candidates/icao9432-extracted/communications_2_8_1_en/icao9432-extracted::communications_2_8_1_en::0a964f42b6100596.json` — COMMS-1 candidate JSON (title, claimText, exactSourceQuotes).
- `research/tools/requirements-spike/registry/ollama_first/candidates/icao9432-extracted/readback_2_8_3_en/icao9432-extracted::readback_2_8_3_en::ac9111d240cfd2c2.json` — FN33-MODEL-1 candidate JSON.

**Optional**:
- `research/txt/icao9432-extracted.txt:3667-3668` (§2.8.1.4 quote), `:3909-3914` (§2.8.3.2 quote).

## Acceptance

- [ ] AGENT_DIALOGUE.md Test Completer role description updated to include "building named blocker primitives + authoring expected-gap tests" as part of chunk-completion. Additive diff.
- [ ] Two new `EvidenceSourceRef` constants added under `ICAO9432` in `EvidenceSourceCatalog.kt` using the existing `source(canonicalId, title, claimScope)` helper.
- [ ] Both new constants added to `EvidenceSourceCatalog.All: Set<EvidenceSourceRef>` at line 245.
- [ ] `EvidenceSourceCatalog.requireValid()` / `validateAgainstRegistry()` passes — both new refs resolve to accepted candidate JSON.
- [ ] All existing catalog tests still pass: `./gradlew-nix :sim:jvmTest --tests "*.EvidenceSourceCatalog*"`.
- [ ] Detekt clean on touched files: `./gradlew-nix detekt`.

## Done summary
Refined the Test Completer chunk-completion scope in AGENT_DIALOGUE.md (additive) and added two new typed `EvidenceSourceRef` constants to `EvidenceSourceCatalog` for COMMS-1 (`communications_2_8_1_en::0a964f42b6100596`, in a new `ICAO9432.Communications` sub-object) and FN33-MODEL-1 (`readback_2_8_3_en::ac9111d240cfd2c2`, under `ICAO9432.Readback`); both refs are wired into `EvidenceSourceCatalog.All` so `validateAgainstRegistry()` exercises them, and the pinned canonical-id assertion in `EvidenceSourceCatalogTest` is extended to match.
## Evidence
- Commits: 1d4147920e991c4ef484a1bccc422945e3775122
- Tests: ./gradlew-nix :sim:jvmTest --tests "*.EvidenceSourceCatalog*" --offline (BUILD SUCCESSFUL - 5 tests, 0 failures, 0 errors; includes validateAgainstRegistry().requireValid() and the exact canonical-id set assertion with the two new entries), ./gradlew-nix detekt --offline (BUILD SUCCESSFUL - detekt clean)
- PRs: