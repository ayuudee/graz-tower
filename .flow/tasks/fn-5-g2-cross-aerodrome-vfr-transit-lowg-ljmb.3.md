---
satisfies: [R5, R6, R7, R9, R10]
---

## Description

Phase G of the G2 multi-phase plan + closing doc updates. Three small additions, all architectural-enforcement / docs hygiene:

1. **`FirewallNoCrossAerodromeHandoffTest`** (reflection-based, jvmTest) — pins `HandoffTarget::class.sealedSubclasses.mapNotNull { it.simpleName }.toSet() == setOf("Peer", "Released")`. Adding any leaf (especially `Foreign(aerodromeId)`) will fail this test, forcing a deliberate scope decision.

2. **`FixtureAerodromeStaffingDoctrineSpec`** (cardinal-claim test) — loads `Fixtures.LOWG_LJMB_VFR` and asserts the staffed `(role, aerodromeId)` set is exactly `{(GROUND, LOWG), (TOWER, LOWG), (APPROACH, LOWG), (TOWER, LJMB)}`. Locks the staffing doctrine; any future widening (e.g. adding LJMB_APPROACH) becomes a deliberate scope decision.

3. **Closing doc updates** (per docs-gap-scout):
   - `AGENTS.md` Testing section: add a paragraph naming G0 (single-aerodrome circuit training) and G2 (cross-aerodrome VFR transit) golden tests, their fixture loaders (`Fixtures.LOWG`, `Fixtures.LOWG_LJMB_VFR`), and the pattern (mirror G0 to add new golden tests).
   - `wiki/data-sources/ljmb.md` "Outstanding LJMB-specific authoring decisions" section: confirm LJMB is TOWER-controlled and note D-G2.7 (AFIS/FIS fallback) does not apply to LJMB.
   - `~/.claude/plans/g2-cross-aerodrome-vfr-transit.md`: add `Status: COMPLETE (Phases A–G, <date>, commits 29e064a..<head>)` header at top.

**Size:** M (3 small additions, but the doc updates require reading + editing 3 files, and the architectural tests must follow the existing template-and-no-suppression-rule conventions).

**Files (expected):**
- `sim/src/jvmTest/kotlin/.../FirewallNoCrossAerodromeHandoffTest.kt` (new, ~50 lines) — or `protocol/src/jvmTest` if HandoffTarget lives there
- `sim/src/jvmTest/kotlin/.../testing/FixtureAerodromeStaffingDoctrineSpec.kt` (new, ~50 lines)
- `AGENTS.md` (Testing section addition)
- `wiki/data-sources/ljmb.md` (authoring-decisions section addition)
- `~/.claude/plans/g2-cross-aerodrome-vfr-transit.md` (status header)

## Approach

- **`FirewallNoCrossAerodromeHandoffTest`** — model on `protocol/src/jvmTest/kotlin/.../TaxiToSplitFirewallTest.kt:28-60`. Same shape: cardinality + leaf-set check. `HandoffTarget` lives at `protocol/src/commonMain/kotlin/.../ResponsibilityState.kt:61-66` so the test belongs in `protocol/src/jvmTest`. Include the standard "No-suppression rule" docstring used across firewall tests (see `FirewallFixtureNoDirectResponsibilitiesTest.kt:23-37` for the canonical phrasing).
- **`FixtureAerodromeStaffingDoctrineSpec`** — loads the fixture from fn-5-g2-cross-aerodrome-vfr-transit-lowg-ljmb.1, walks `loaded.controllers.values`, builds `Set<Pair<RoleName, AerodromeId>>` from each controller's role and aerodromeId, asserts equality against the expected set. Use `assertEquals` (kotlin.test) with a rich diagnostic message naming both expected and actual sets so a diff is actionable.
- **Doc updates** — read current AGENTS.md Testing section + wiki/data-sources/ljmb.md "Outstanding ... decisions" subsection + plan file head. Edit each surgically; do not rewrite the surrounding content.

## Investigation targets

**Required** (read before coding):
- `protocol/src/jvmTest/kotlin/xyz/easiersaid/twr/protocol/TaxiToSplitFirewallTest.kt:28-60` — closest template for `FirewallNoCrossAerodromeHandoffTest`. Has `sealedLeavesOf` walker; for HandoffTarget you only need the direct subclasses.
- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/ResponsibilityState.kt:61-66` — `HandoffTarget` definition. Sealed leaves: `Peer(controllerId)`, `Released`.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/FirewallFixtureNoDirectResponsibilitiesTest.kt` — template for "No-suppression rule" docstring + projectRoot helper.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt:73-77` — `projectRoot()` helper to reuse.
- `AGENTS.md` Testing section — current shape to extend.
- `wiki/data-sources/ljmb.md` outstanding-decisions section — current shape to extend.

**Optional** (reference as needed):
- `pilot/src/jvmTest/kotlin/.../FirewallAviationWorldFieldsTest.kt` (Phase C) — recent reflection-allowlist precedent.

## Acceptance

- [ ] `FirewallNoCrossAerodromeHandoffTest` exists in `protocol/src/jvmTest`, asserts `setOf("Peer", "Released")`, includes No-suppression-rule docstring.
- [ ] Test fails (with rich diagnostic) if a third sealed leaf is added to `HandoffTarget`.
- [ ] `FixtureAerodromeStaffingDoctrineSpec` exists in `sim/src/jvmTest/.../testing/`, loads `Fixtures.LOWG_LJMB_VFR`, asserts exact `(role, aerodromeId)` set.
- [ ] Test fails (with rich diagnostic) if any controller is added or removed from the fixture.
- [ ] AGENTS.md Testing section gains a G0/G2 golden-test paragraph naming both tests, fixtures, the mirror pattern.
- [ ] `wiki/data-sources/ljmb.md` Outstanding-Decisions section gains a TOWER-controlled-confirmation entry referencing the AIP citation and noting D-G2.7 does not apply.
- [ ] `~/.claude/plans/g2-cross-aerodrome-vfr-transit.md` head-of-file gains `Status: COMPLETE (Phases A–G, YYYY-MM-DD, commits 29e064a..<head>)` line.
- [ ] G0 + G2 + all firewall tests + new staffing-doctrine spec all green.
- [ ] 3-agent plan review (impact, fp-review, test-review) before implementation; 3-agent post-impl review on the diff. Findings folded.
- [ ] Commit as `G2 Phase G: FirewallNoCrossAerodromeHandoffTest + FixtureAerodromeStaffingDoctrineSpec + closing docs` with `Co-Authored-By` tail.


## Done summary

_Populated at task completion via `flowctl done <id> --summary-file ...`_

## Evidence

_Populated at task completion via `flowctl done <id> --evidence-json ...`_
