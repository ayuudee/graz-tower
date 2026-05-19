# Source-Unit Conformance Monitor v2 Design

## Goal & Context
Create an isolated v2 design alongside FN37, testing the same five source-unit cases with a different idea. FN37 uses source-unit-owned specs. FN38 explores a monitor/contract model: scenarios produce black-box traces, and independent source-backed monitors observe those traces with antecedent/consequent checks.

The design artifact lives at `research/tools/requirements-spike/quality/source_unit_monitor_v2/fn38_source_unit_monitor_v2_2026-05-19/design.md`.

## Architecture & Data Models
The core model is `ConformanceTrace` plus a sealed `TraceFact` vocabulary, with monitors of the form:

- source ids;
- observed fact type;
- applicability predicate;
- requirement predicate;
- projection requirements;
- adequacy / non-vacuity requirements.

This differs from FN37 by decoupling scenarios from source units. A scenario bank can feed many source-backed monitors, and the monitor report distinguishes pass, fail, vacuous, and projection-gap outcomes.

## API Contracts
This is design-only unless a follow-up spike is started. The proposed API boundary is a black-box trace projection, not direct access to `SimState`, controller BDI, or rule internals.

## Edge Cases & Constraints
The design explicitly treats missing trace concepts as `ProjectionGap`: essential aerodrome information, aircraft-known-information state, pilot intent declaration, and safety-necessity classification are not silently skipped.

## Acceptance Criteria
- [x] Provide a v2 design that is isolated from FN37.
- [x] Test the same five cases conceptually: readback, taxi, touch-and-go, essential aerodrome information, critical-phase radio silence.
- [x] Include deep design review and red-team sections.
- [x] Compare FN38 against FN37 and recommend a next spike shape.

## Boundaries
Do not replace FN37. Do not implement the monitor runner in this task. Do not claim this design is an improvement until a short implementation spike tests it.

## Decision Context
FN37 showed that source-unit specs work, but still place scenario construction inside source-owned tests. FN38 asks whether source-backed runtime monitors over black-box transcripts might be a better long-term collaboration boundary.

## Review considerations
FP / type safety: A clean version needs `TraceFact` as a sealed hierarchy and typed `ProjectionGap`, not nullable fields or stringly parameter maps.

Test architecture: Monitors are trace oracles over high-level runs. Pure protocol readback can use synthetic traces because it has an independent oracle.

Impact: The major cost is a normalized `ConformanceTrace` projection. This may become valuable as a black-box interface, or it may become a second simulator model if overbuilt.

Operational correctness: Applicability predicates make `may` and `should` source units safer than unconditional assertions. Phraseology still requires rendered phrase facts before full coverage.
