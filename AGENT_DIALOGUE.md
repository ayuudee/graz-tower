# Agent Dialogue

Purpose: shared, abridged communication between agents working in this repo.
Use this file for handoffs, questions, decisions, and current state that another
agent should see quickly.

## Actors

There are exactly two agents collaborating on the ICAO 9432 programme. They
share this file but never the same epic.

- **Test Writer.** Authors source-mapped tests for each chunk. Owns
  test-authoring epics. Does not weaken tests to match current behaviour.
  Hands off to the Test Completer by leaving the chunk's failing tests +
  coverage matrix in a known state and writing an entry below.
- **Test Completer (this agent).** Picks up failing tests from a chunk and
  drives them to green via `flow-next` — planning each repair as its own
  epic, implementing, reviewing, and committing. Never edits the Test
  Writer's tests to make them pass; if a test is wrong, raise a
  `QUESTION:` here and wait.
  - **Chunk-completion scope (refined 2026-05-23).** When the chunk's
    test-authoring phase leaves named expected-gap units without
    matching tests because the supporting primitives don't yet exist,
    the Test Completer's chunk-completion role explicitly covers
    **building those named blocker primitives + authoring the matching
    expected-gap tests**, not solely driving pre-existing failing tests
    to green. The two outcomes (`covered-green` and `covered-red` per
    `docs/deferments-CONVENTION.md`) are both acceptable terminal
    landings; `covered-red` spawns a named flow-next production-repair
    epic, never a silent deferment.

Neither role downgrades scope on the other's behalf. If a repair turns out
to require a regulation or design decision the Test Completer cannot make
alone, raise a `QUESTION:` and stop — do not stub, skip, or defer.

## Write Protocol

- Append new entries under **Dialogue Log**.
- Keep entries short and factual.
- Include date/time if useful.
- Do not paste long command output; summarize and cite the command.
- Mark open questions explicitly with `QUESTION:`.
- Mark resolved items explicitly with `RESOLVED:`.
- If an entry creates deferred work, also add it to `.plan` AND to
  `docs/deferments.md` if it carries a named contract (per
  `docs/deferments-CONVENTION.md`).

## Current Focus

Prime objective: work through all accepted ICAO Doc 9432 source units as a
source-mapped regulatory testing programme.

Current programme state:

- `fn-46` mapped all accepted ICAO 9432 source units.
- `fn-47` through `fn-58` completed the chunk source-mapped coverage wall.
- `fn-60` through `fn-89` have been working down blocker families with
  source-mapped implementation epics.
- Latest completed programme commit before the 2026-09-21 shutdown handoff:
  `176b6d6f fn-89 cover communications phraseology examples`.
- Current durable shutdown note:
  `research/tools/requirements-spike/quality/icao9432_programme/SHUTDOWN_HANDOFF_2026-09-21.md`.
- Chunk 01 artifacts live under:
  `research/tools/requirements-spike/quality/icao9432_programme/chunk_01_comms_readback_transfer/`.
- Central implementation tracking table:
  `research/tools/requirements-spike/quality/icao9432_programme/implementation_blocker_manifest.csv`.

## Workflow

The chunk workflow has two phases with distinct owners:

1. **Test-authoring phase (Test Writer).** Open a chunk epic. Write
   source-mapped tests against the chunk's ICAO 9432 units. Tests are
   correct-by-construction against the regulation; they are not adjusted
   to fit current implementation. Leave the chunk with a coverage matrix
   and a clear list of failing tests + reason codes.
2. **Test-completion phase (Test Completer).** For each failing test or
   coherent group of failing tests, open a **separate** repair epic via
   `flow-next`. Plan, implement, self-assess, review (where the work
   warrants it), commit. Update the chunk's coverage matrix when a
   group moves to `covered-green`.
   - **Expected-gap closure (refined 2026-05-23).** Where the chunk
     enters completion with named `expected-gap` units whose blockers
     are missing primitives (not just missing implementation behind an
     already-written test), the Test Completer also builds the named
     blocker primitives and authors the matching source-mapped
     expected-gap tests within the chunk-closure epic. Each unit lands
     in `covered-green` or `covered-red`; `covered-red` landings spawn
     a named production-repair epic and replace (not delete) the
     `.plan` blocker with a one-line pointer to that epic. PHRASE-1 /
     POLICY-1 cross-chunk infrastructure stays out of scope and
     remains named-visible debt.

The two phases share AGENT_DIALOGUE.md but never share an epic.

## Standing Constraints

Both actors:

- Follow `AGENTS.md` commandments in full. The Test Completer in
  particular owes the principal-agent self-assessment (`AGENTS.md` §
  Self-assessment before review) before any review or commit.
- Keep test-authoring epics separate from implementation repair epics.
- Do not weaken tests to match current implementation.
- Policy-dependent source units require typed policy concepts, not loose
  assertions.
- Phraseology source units require rendered-transmission evidence; typed
  protocol semantics alone are not enough.

Test Completer specifically — **no debt, no surprises**:

- No corners cut. No `@Disabled`, no skip-list, no `@Suppress`, no
  catch-all `else` swallowing a new case, no `TODO` that disables a
  check (Commandment 1).
- No half-baked commits. Every commit leaves the codebase green and
  loudly-failing on anything not yet handled (Commandment 2).
- Throw on the genuinely impossible; type-out the merely-unhandled with
  `Either<NotYetImplemented, T>` (Commandments 3 & 8).
- Tests stay honest. If a test cannot pass without a regulation
  interpretation we don't yet hold, raise a `QUESTION:` — do not invent
  the interpretation (Commandments 4, 6, 7).
- Cite every regulatory claim with edition + section (Commandment 7).
- If anything is genuinely deferred during a repair, it goes to `.plan`
  and — if it carries a named contract — also to `docs/deferments.md`
  before the commit that surfaced it. The default posture is **don't
  defer**; deferment is only acceptable when the gap is named, bucketed,
  and visible.

## Dialogue Log

### 2026-09-21 16:39 CEST - Shutdown handoff after fn-89

Repository was clean and local `main` matched `origin/main` at
`176b6d6f fn-89 cover communications phraseology examples` before the shutdown
docs pass.

Recent programme state:

- `fn-87` covered helicopter air-taxi rendered phraseology.
- `fn-88` covered ICAO 9432 section 4.10 essential aerodrome information
  example phraseology with synthetic rendered example evidence.
- `fn-89` covered ICAO 9432 section 2.8.1 full-callsign initial-contact and
  `ALL STATIONS` communication examples with synthetic rendered example
  evidence.

Current implementation manifest snapshot:

- `implementation_blocker_manifest.csv` has 143 implementation-tracking rows.
  The full accepted source-unit inventory remains 166 units in the programme
  README.
- Largest remaining manifest states: 28 `phraseology-later`, 22
  `policy-blocked`, 8 `covered-structural`, 7 `covered-green`, 6
  `model-gap + policy-blocked`, 4 `model-gap`, 4
  `model-gap + phraseology-later`, 4
  `covered-synthetic rendered example phraseology`, and 4
  `covered-green rendered phraseology`.

Recommended next candidate:

- `icao9432-extracted::readback_2_8_3_en::fe3b04ca9c3384d9`.
- ICAO Doc 9432, Fourth Edition, 2007, section 2.8.3.3: route clearance is not
  takeoff or active-runway-entry clearance.
- Likely implementation shape: structural protocol evidence proving the
  route/runway boundary, only if the existing protocol surface can prove it
  honestly. Do not treat it as rendered phraseology and do not green it if the
  evidence would only restate a compiler fact.

Full resume note:
`research/tools/requirements-spike/quality/icao9432_programme/SHUTDOWN_HANDOFF_2026-09-21.md`.

### 2026-05-20

- Created this file as a shared communication surface between agents.
- Next likely 9432 actions:
  - choose whether to open repair epics for chunk 01 blockers, or
  - proceed to chunk 02: ground movement, pushback, and taxi.
- Clarified the two-actor model: **Test Writer** authors source-mapped
  tests per chunk; **Test Completer** (this agent) drives the failing
  tests to green via `flow-next`, one repair epic at a time, under the
  full `AGENTS.md` commandments and a no-debt / no-surprises posture.
  See the new **Actors**, **Workflow**, and **Standing Constraints**
  sections above.

### 2026-05-26 — Chunk 01 CLOSURE (Test Completer)

`fn-48-icao-9432-chunk-01-drive-expected-gap` is **closed**. The four
chunk-specific expected-gap units land in terminal states; no new
programme-level deferments introduced.

**Verification command**:

```bash
./gradlew-nix :sim:jvmTest --tests "*Icao9432*" :controller:jvmTest --tests "*Icao9432*"
./gradlew-nix detekt
```

**Result**: BUILD SUCCESSFUL for both. All 9 `*Icao9432*` test classes
green (sim: `Icao9432Chunk01ReadbackEvidenceTest`,
`Icao9432Chunk01FrequencyTransferEvidenceTest`,
`Icao9432Chunk01ReceptionDoubtEvidenceTest`,
`Icao9432Chunk01ClearancePacingEvidenceTest`,
`Icao9432TaxiSourceBackedScenarioTest`,
`Icao9432ModelGapSourceUnitSpecTest`,
`Icao9432TouchAndGoSourceBackedScenarioTest`,
`Icao9432ReadbackSourceUnitSpecTest`; controller:
`Icao9432ReadbackConformanceSpec`). Detekt clean.

**Pre-existing sandbox failure — NOT a chunk-01 regression**: running
`./gradlew-nix build` (full suite, unfiltered) surfaces three
`java.nio.file.FileSystemException` failures in
`EvidenceReportWriterTest` (2 cases at lines 24 + 83) and
`EvidencePermanentTwentyCaseTest` (1 case at line 170). All three are
`Files.createTempDirectory(...)` calls that try to use the macOS
system `java.io.tmpdir` from a forked Gradle test JVM and hit the
sandbox write deny. The test files predate fn-48 (last touched in
commits `9e728ce6` and `514c371e`, well upstream of any chunk-01
work); they reproduce on master baseline. Recording as a known
pre-existing finding — sandbox / test-environment concern, not a
chunk-01 closure blocker. Honest close-out per memory
`honest-close-out-dont-assert-green-on-2026-05-17`: chunk-01 evidence
suite is GREEN end-to-end; the build-gate red is bounded to
pre-existing, fn-48-out-of-scope test infrastructure.

**New coverage tally** (full table in
`research/tools/requirements-spike/quality/icao9432_programme/chunk_01_comms_readback_transfer/coverage_report.md`):

| Final state | Units |
|---|---:|
| `covered-green` | 8 |
| `covered-red` | 2 |
| `expected-gap` | 0 |
| `phraseology-later` | 7 |
| `policy-blocked` | 2 |
| `not-applicable` | 1 |

Final landings:

- **FN44-GAP-1** → `covered-green`. Existing `ContactFrequency`
  controller emission in the LOWG circuit + new controller-advised
  adapter projection. `.plan` paragraph DELETED.
- **FN44-GAP-2** → `covered-red`. Typed
  `EvidenceFactPayload.FrequencyTransfer(PilotNotifiedAbsentAdvice)`
  adapter + test asserting on `report.results`. Spawned repair
  epic: **`fn-49-sim-emits-pilot-notified-frequency`**. `.plan`
  paragraph REPLACED with one-line pointer.
- **COMMS-1** → `covered-red`. New
  `EvidenceFactPayload.ReceptionDoubt` sealed leaf + typed
  `SayAgainRef` linkage + `AuditReceptionDoubtSubject` selector +
  chunk-01 source-mapped test asserting on `report.results`.
  Spawned repair epic:
  **`fn-50-sim-models-reception-quality-comms-1`**. `.plan`
  paragraph REPLACED with one-line pointer.
- **FN33-MODEL-1** → `covered-green` *via Advisory*. New
  `EvidenceAuditOutcome.Advisory` sealed audit-outcome leaf + new
  `EvidenceFactPayload.ClearancePacing` payload + `PacingWindow`
  enum + adapter projection +
  `Icao9432Chunk01ClearancePacingEvidenceTest`. §2.8.3.2 ("should")
  semantics surface as Advisory observations, ignored by
  `assertNoFailures()`. `.plan` paragraph **partially rewritten,
  not deleted** — the two other source units it references
  (route-clearance timing `…::36e6ad16cffe8726`, now `policy-blocked`
  against POLICY-1; and TAKE OFF phraseology `…::f06dfa1cefd2d649`,
  now `phraseology-later` against PHRASE-1) remain
  `blocked_by_model_gap` against PHRASE-1 / POLICY-1 cross-chunk
  infrastructure.

**Spawned production-repair epics**:

- `fn-49-sim-emits-pilot-notified-frequency` — closes FN44-GAP-2
  red by adding sim `RequestFrequencyChange` emission so §2.8.2.1
  fallback lands `covered-green`.
- `fn-50-sim-models-reception-quality-comms-1` — closes COMMS-1
  red by adding typed reception-quality input to
  `TransmissionRecord` so §2.8.1.4 lands `covered-green`.

**`.plan` accounting verified**: FN44-GAP-1 deleted; FN44-GAP-2
replaced with pointer to `fn-49`; COMMS-1 replaced with pointer to
`fn-50`; FN33-MODEL-1 partially rewritten with §2.8.3.2 closure note
+ retained reference to the two `blocked_by_model_gap` source units.
PHRASE-1 and POLICY-1 entries UNCHANGED; no new programme-level
deferments introduced (verified by absence of new `D-*` entries in
`docs/deferments.md`).

**STRATEGY.md**: chunk-01 closure sentence ADDED to the
Requirements-registry track (single sentence, no restructure;
`last_updated` bumped to 2026-05-26).

**Chunk 02 (ground movement, pushback, taxi) is READY for the Test
Writer.**

#### Principal-agent self-assessment — `fn-48` close-out

Per `AGENTS.md` §Self-assessment before review, walked against the
8 criteria. Each criterion gets a one-line check-result.

1. **Totality** — PASS. Three sealed-type extensions landed across
   tasks .2/.3/.4 (`EvidenceFactPayload.FrequencyTransfer` variants,
   `EvidenceFactPayload.ReceptionDoubt`,
   `EvidenceFactPayload.ClearancePacing`,
   `EvidenceAuditOutcome.Advisory`). Every `when` consumer was
   grep-walked at introduction; no `else -> Unit` or `else -> null`
   added. `PacingWindow` declared as `enum class` for free `.entries`
   per memory `predicate-guards-over-sealed-types-must-2026-05-16`.
2. **Reversal completeness** — PASS / NA. No production state
   transitions added; primitives are pure observation projections
   over existing sim traces. Reversal of "primitive removal" was
   considered: removing any of the three new payload leaves restores
   the prior `expected-gap` classification without leaking partial
   test references (verified by the partial closure of FN33-MODEL-1
   which keeps the paragraph rather than half-deleting it).
3. **Interaction coverage** — PASS. The new Advisory outcome leaf
   was traced through every consumer
   (`assertNoFailures()`, report formatter, test-DSL helpers,
   `EvidenceAuditCase.toCase` activation guard). The activation
   discipline lesson surfaced in .3's review (entry
   `bug/test-failures/audit-selectors-must-activate-examined-2026-05-26`)
   was applied pre-emptively on the .4 Advisory + Pass selector
   paths and landed Codex SHIP on first pass — concrete evidence the
   captured lesson works as a forward guard. See criterion 4 + 7
   below.
4. **Test coverage for known features** — PASS. Each of the 4
   chunk-01 source units has a paired chunk-01 source-mapped test
   citing the accepted source-unit id verbatim. Property-test matrix
   per R12 (speaker × utterance × payload × boundary) lives
   alongside each primitive. Captured lesson: **selectors must call
   `activate(fact.id)` for every consulted fact on ALL non-empty
   paths** — without this, `AuditEvidenceCaseBuilder.toCase`
   silently overrides the selector's specific outcome with a generic
   "did not activate any evidence facts" `Fail`. Discipline applied
   pre-emptively on Advisory + Pass paths in .4.
5. **New-field audit** — PASS. New fields on new sealed leaves:
   `ReceptionDoubt.transmissionRef / doubtSource / resolvedBy`,
   `ClearancePacing.clearanceRef / issuedDuring`,
   `Advisory.violations / reason`. Each field's mutation surface is
   the projection emit site only — no state-class mutations. Catalog
   refs added to `EvidenceSourceCatalog.All` so
   `validateAgainstRegistry()` walks them (verified by `:sim:jvmTest`
   `*EvidenceSourceCatalog*` green).
6. **Operational correctness** — PASS. Each primitive maps to its
   cited ICAO 9432 §section and edition (4th ed 2007). §2.8.1.4
   "shall" → mandatory, modelled as `Fail`-eligible (COMMS-1).
   §2.8.2.1 "shall" twice → mandatory, modelled as `Fail`-eligible
   (FN44-GAP-1 + FN44-GAP-2). §2.8.3.2 "should" → advisory, modelled
   as `Advisory` (FN33-MODEL-1).
7. **Error handling honesty** — PASS. `error()` reserved for
   provably-impossible states (none added in this epic). The
   covered-red landings use **typed Fail outcomes inside the audit
   report**, not exceptions: tests assert on `report.results` rather
   than calling `assertNoFailures()`, so the JUnit gate is green
   while the audit honestly reports `Fail`. The activation-discipline
   memory captured during .3's NEEDS_WORK → SHIP cycle is exactly
   the "no silent override" guard for this pattern.
8. **Deferment honesty** — PASS. Two named flow-next production-repair
   epics spawned (`fn-49`, `fn-50`) for the `covered-red` landings,
   each with a `.plan` pointer (bucket 3 of the four-bucket model)
   replacing — not deleting — the original blocker paragraph. The
   pre-existing sandbox failure on `EvidenceReportWriterTest` +
   `EvidencePermanentTwentyCaseTest` is recorded explicitly in this
   closure entry as a known pre-existing finding, not silently
   carved out. PHRASE-1 + POLICY-1 stay as named, visible
   cross-chunk debt in `.plan`. No new `D-*` entries in
   `docs/deferments.md`.

No findings deferred to `.plan` from this self-assessment — the
captured lesson from .3 was the only mid-epic discipline finding
and it landed as a memory entry during the .3 fix-loop, then was
applied pre-emptively in .4. Chunk 01 closes honestly.
