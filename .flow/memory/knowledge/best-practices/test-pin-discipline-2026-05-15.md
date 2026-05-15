---
title: "Test-pin discipline: post-state + mint-id timestamps + trust-boundary keys"
date: "2026-05-15"
track: knowledge
category: best-practices
module: sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim
tags: [test-discipline, sim-test, decision-cycle, mint-id, observation-vs-intent, allowlist, compound-predicate, fn-8, fn-12, fn-14, fn-15]
applies_when: Authoring or reviewing a sim-level golden test that pins multi-cycle ordering, a state transition, a tree rewrite, or an R9-style filesystem allowlist guard — especially when mirroring an existing test pattern into a sibling axis (crosswind → tailwind, runway-A → runway-B, fn-N.X → fn-N.Y).
related_to: [bug/test-failures/sim-test-pins-must-compare-against-2026-05-10, bug/test-failures/tests-must-anchor-on-observed-post-2026-05-09, bug/test-failures/r9-style-allowlist-guards-must-key-by-2026-05-09, bug/test-failures/compound-predicate-test-assertions-2026-05-11]
---

## The rule

Test pins must witness the **observed post-state of the property under test**,
keyed by a **stable identifier on the correct trust boundary**, asserted
with **direct predicates that fail loudly under every failure mode** — not
upstream intent, not transient transport timestamps, not content-derived
keys, not compound predicates that short-circuit vacuously.

This is one discipline with four faces. Each face was found independently in
4 separate test-failure captures (fn-8, fn-12.3, fn-9 R9-allowlist, fn-15.1)
and they share the same generator: the test author anchors on whatever is
easy to reach from the failure message instead of the property being
defended.

## The four faces

### 1. Mint-id timestamps over transport timestamps for same-cycle ordering

**Captured:** `bug/test-failures/sim-test-pins-must-compare-against-2026-05-10.md`

When ordering events on the same controller decision cycle, anchor on the
**mint-id walk** (`findEmittingCycleMs(trace, controller, txId)` keyed off
`SimState.nextTransmissionId`), not `txStart`. Transmission start times can
straddle the next `CONTROLLER_CYCLE_INTERVAL` because
`applyControllerOutputs` serializes multiple cycle outputs on the same
frequency — a transmission queued at the END of cycle N starts ON the air
AFTER cycle N+1 has already begun. A pin like
`Detected.decisionTime <= GoAround.txStart` then false-passes for a
regression where the GoAround decision was actually emitted in cycle N-1.

The canonical helper pattern (`extractTransmissionId(trace, record)` +
`findEmittingCycleMs(trace, controller, txId)`) lives in
`G3aRunwayObstructionTest.kt` and should be the default for "same-cycle"
sim pins.

**Cross-cycle architectural property** can make `txStart` sufficient — but
only when the test spec documents WHY (e.g. fn-15.2's note that the property
under test is strictly cross-cycle by construction). Default to mint-id;
take `txStart` only with an explicit architectural justification in the
test KDoc.

### 2. Observe real post-state, not controller-emitted intent

**Captured:** `bug/test-failures/tests-must-anchor-on-observed-post-2026-05-09.md`

A pin like `firstControllerInstructionOf<AfterLandingVacateVia>` proves the
controller TOLD the aircraft to vacate, not that the aircraft actually
vacated. A regression in the runway-duty machine, the pilot mission tree,
or the kinematic-vacate path would still satisfy the instruction-time pin.

The right anchor is the **pilot's report** transmission — `Report(RunwayVacated)`
only fires after `pilotMission` advances to `REPORT_RUNWAY_VACATED`, which
itself only advances after the aircraft is physically off the runway entity.
Pilot reports are post-state observations; controller instructions are
upstream intent.

The generalization: when the test message says "X happened before Y",
identify the property being defended. If the property is "the controller
issued the instruction", anchor on the controller record. If the property
is "the aircraft transitioned state", anchor on the pilot/sensor-side
post-state observation that ONLY fires after the transition.

This is a sibling of "radio observables over commitment-stage observables"
from inherited-gate-semantics — both are post-state-vs-intent discipline.

### 3. Allowlists key by stable filesystem contract, not by file content

**Captured:** `bug/test-failures/r9-style-allowlist-guards-must-key-by-2026-05-09.md`

R9-style guardrails ("every X under directory Y must be in allowlist Z")
must key the allowlist on the filesystem path component (directory or
filename), not on a field inside the file's content. The directory layout
is the authoritative contract; the JSON payload is the data being
validated against it.

The fn-9.1 bug: `cad/airports/rendered/abcd/world-candidate.json` containing
copy-pasted JSON that still declared `"icao": "LOWG"` impersonated the
allowlisted LOWG entry. Keying on `document.aerodrome.icao` (untrusted
content) instead of `dir.uppercase()` (authoritative path) defeated the
guard.

The fix shape:
1. Iterate the filesystem layout, deriving `dirIcao` from the trust boundary.
2. Assert `document.world.aerodrome.icao == dirIcao` BEFORE the allowlist
   lookup — fails fast on copy-paste with a directive error message.
3. Use `dirIcao` (trusted) in all error message interpolations, never
   `icao` (untrusted).

Generalizes to any test that scans the filesystem for an allowlist match:
the path is the contract, the content is the data; cross-check before
lookup. Same shape as "don't trust user-controlled input as a SQL lookup
key when your trust boundary is the URL routing."

### 4. Direct absence assertions over compound predicates with `count` side-channels

**Captured:** `bug/test-failures/compound-predicate-test-assertions-2026-05-11.md`

A predicate like
`compounds.any { it is TaskName.TouchAndGo && compounds.count { it is TaskName.CircuitAfterGoAround } == 0 }`
passes **vacuously** when BOTH the old `TouchAndGo` AND a new
`CircuitAfterGoAround` remain in the tree — the `count CAGA == 0` clause
short-circuits when the rewrite added a CAGA. The test "passes" but doesn't
actually prove the rewrite happened. fn-15.1's tailwind sibling test
inherited this from fn-14.1's crosswind test verbatim.

The fix: split the contract into independent positive + absence assertions.

```kotlin
assertTrue(TaskName.CircuitAfterGoAround in compounds)
assertFalse(compounds.any { it is TaskName.TouchAndGo })
```

Now both halves fail loudly under independent regressions.

The audit step that catches this: for each assertion, ask "what is the
minimal mutation that makes this pass while the code is broken?" If the
answer is "the compound predicate trivially short-circuits when X holds,"
split the assertion.

## When this applies

- **Authoring a sim-level golden test** that pins a multi-cycle ordering,
  a state transition, or a tree rewrite.
- **Mirroring an existing test pattern** into a sibling test for a new axis
  or epic — re-read every assertion's failure mode; do not copy-paste the
  trust boundary or the predicate shape without re-deriving.
- **Adding an R9-style filesystem allowlist guard** for any "every file
  under directory X must be in registered list Y" semantic.

## Forward-applicable checklist

Before SHIP on a test pin:

1. **Witness:** does this assertion observe the post-state of the property
   under test, or upstream intent? If upstream, find the post-state
   observable.
2. **Timestamp:** does this pin use the decision-cycle timestamp
   (mint-id walk) for same-cycle ordering, or `txStart`? If `txStart`,
   document the cross-cycle architectural property that makes it sufficient.
3. **Trust boundary:** if this test keys an allowlist or lookup, does the
   key come from the trust boundary (filesystem path, URL routing, etc.)
   or from the data being validated (file content, request body)? If the
   latter, swap.
4. **Failure modes:** for each compound predicate, what is the minimal
   mutation that makes the test pass while the code is broken? If the
   answer is "X clause short-circuits," split into independent assertions.
5. **Sibling axis:** if this test was mirrored from a sibling, did you
   re-derive each assertion against the new axis's semantics, or was it
   copy-paste-with-rename? Per-diff review will hold the new sibling to
   the higher bar regardless of what landed in the original — bug-fix the
   new copy at minimum.

## Cross-references

- Inherited gate semantics: `knowledge/best-practices/inherited-gate-semantics.md`
  (sibling discipline: copy-pasted gates need semantic re-validation per
  axis, not just syntactic transfer)
- Source captures (kept as authoritative event records):
  - `.flow/memory/bug/test-failures/sim-test-pins-must-compare-against-2026-05-10.md`
  - `.flow/memory/bug/test-failures/tests-must-anchor-on-observed-post-2026-05-09.md`
  - `.flow/memory/bug/test-failures/r9-style-allowlist-guards-must-key-by-2026-05-09.md`
  - `.flow/memory/bug/test-failures/compound-predicate-test-assertions-2026-05-11.md`
