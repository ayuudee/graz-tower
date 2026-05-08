---
satisfies: [R5, R7]
---

## Description

Tighten `G2CrossAerodromeVfrTest`'s R4 gap-magnitude pin from the relaxed
`> 0` back to the doctrinal `>= 30_000L`. Remove the inline "tentative
band" comment block + the class docstring's "deferred to a future pass"
language, replacing both with concrete spec'd-out text. The change closes
fn-6's deliverable: G2's R4 is now empirically reachable because
`OutsideAerodromeRadius` fires at the physical 12 NM ring rather than at
the OSMOT snap.

**Size:** S
**Files:**
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G2CrossAerodromeVfrTest.kt`
- `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/project_g2_status.md`
  (one-line update reflecting that R4 is no longer relaxed)

## Approach

1. **Tighten the bound** at the R4 gap-magnitude pin (currently around
   `G2CrossAerodromeVfrTest.kt:479`):
   - Change `firstTxToLjmbMs - lastLowgInstrMs > 0L` to
     `firstTxToLjmbMs - lastLowgInstrMs >= 30_000L`.
   - Update the failure message from "must be positive" to "must be ≥ 30 s
     (Class-G transit duration; ICAO Doc 4444 §10.1 release semantics +
     SERA Section 6 Class G airspace)".
   - Run the test. With fn-6.2's kinematic-coords change, the rule
     fires at ~9 min (12 NM @ ~110 KTAS climb-cruise) and the pilot's
     LJMB contact lands at ~17 min (32 NM total). Empirical gap should be
     ~480 s — well above 30 s.
   - **Don't pin past empirical observation.** If the empirical gap is
     ~480 s, leave the bound at 30 s — over-tightening introduces
     brittleness to tick-cadence jitter for no doctrinal gain. Capture
     the actual observed gap in `## Evidence` so future regressions are
     diagnosable.

2. **Remove the deferral comment block** at lines 455-478 (the
   "Gap-magnitude pin (R4 — tentative band, retune on geometric upgrade)"
   block). Replace with a one-line comment:
   ```kotlin
   // R4: doctrine wants several minutes of Class-G transit between
   // LOWG release and pilot's LJMB contact (ICAO Doc 4444 §10.1 +
   // SERA Section 6). Restored to ≥ 30 s by fn-6 (kinematic coords on
   // AircraftObservation; OutsideAerodromeRadius now fires on physical
   // 12 NM crossing, not on OSMOT snap).
   ```

3. **Update the class docstring** at lines 78-148 (the "G2 closure landed"
   block). Spec'd-out replacement for the "R4 gap-magnitude pin relaxed
   (this file)" subsection bullet — replace it with:

   > **R4 gap-magnitude pin restored to ≥ 30 s.** fn-6 lands kinematic
   > coordinates on the firewall at four production sites — (a) `coords`
   > field on `SensorReading` + `AircraftObservation`, (b) the
   > `AircraftObservationFactory.from` factory plus its `fromTestPoint`
   > test helper, (c) `OutsideAerodromeRadius.evaluate` reads `ac.coords`
   > directly, (d) typed `Meters.fromNauticalMiles(Int)` helper at both
   > `TowerDeparture` call sites. The release ring now fires at the
   > physical 12 NM crossing (~9 min into the flight at C172 cruise),
   > not at the OSMOT snap; multi-minute Class-G transit is now
   > empirically reachable (~480 s observed at plan time).
   <!-- Updated by plan-sync: fn-6.2 doctrinal precision — class docstring should enumerate the four production sites that fn-6 touched, not the single "kinematic-coords change" phrase -->

   Also remove any "deferred to a future pass" / "until the geometric
   upgrade lands" language elsewhere in the class docstring. Don't gut
   the docstring — preserve the four-fix narrative for fn-5 (those fixes
   are still load-bearing).

4. **Grep gate**: after the changes, `grep -rn 'fn-6\b' sim/src/jvmTest/`
   should return zero matches. The `\b` word-boundary prevents
   matching e.g. `fn-65-...` in any future epic IDs. Per practice-scout's
   co-location pattern, the relaxation references and the assertion live
   next to each other; removing one removes the other.

5. **Update `project_g2_status.md`** (the user's auto-memory):
   - Find the bullet that mentions R4 being "relaxed to gap > 0" (around
     the four-fix closure narrative).
   - Replace with: "R4 gap-magnitude pin restored to ≥ 30 s by fn-6."
   - Keep the rest of the entry intact.

## Investigation targets

**Required** (read before coding):
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G2CrossAerodromeVfrTest.kt:78-148`
  — class docstring (the "G2 closure landed" block with the four-fix
  closure summary).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G2CrossAerodromeVfrTest.kt:455-495`
  — the R4 gap pin + surrounding "Cross-aerodrome handoff window" block,
  including the relaxation comment.
- `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/project_g2_status.md`
  — the project-memory entry summarising fn-5's closure. Find the R4
  relaxation bullet to update.

**Optional** (reference as needed):
- `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/feedback_no_corners.md`
  — doctrine reference for the "no relaxed pins lingering" principle.

## Key context

- The empirical gap should be much larger than 30 s (~480 s expected).
  The 30 s threshold is the doctrinal floor (the user's R4 spec language
  was "≥ 30 s = transit duration through Class G"). Don't pin past
  empirical observation — leave comfortable headroom for tick-cadence
  jitter.
- Practice-scout flagged: "co-locate the relaxation comment with the
  assertion". The current G2 file has both at lines 455-495. Removing both
  in one diff is the right shape; verify the grep gate.
- Class docstring update should preserve the "G2 closure landed" narrative
  for the four-fix closure (those fixes are still load-bearing; only the
  R4 deferral note needs removing). Don't gut the docstring.
- The grep gate `'fn-6\b'` (word boundary) prevents future false positives
  from epic IDs like `fn-65`. Use `\b`, not bare `fn-6`.
- fn-6.2 landed `OutsideAerodromeRadiusSpec` (3 rows: inside-ring/outside-ring
  /ARP-not-found) at `controller/src/commonTest/kotlin/.../bdi/`. fn-6.3's
  closure narrative may want to cite it as the unit-level pin on the
  kinematic gate: G2 verifies the gate fires in the cross-aerodrome flow,
  the spec verifies the geometric semantics in isolation. Co-cite in
  `## Evidence` if the closure framing benefits.
  <!-- Updated by plan-sync: fn-6.2 evidence references OutsideAerodromeRadiusSpec — fn-6.3 closure narrative may want to cite it -->

## Acceptance

- [ ] `G2CrossAerodromeVfrTest`'s R4 gap-pin assertion reads
      `firstTxToLjmbMs - lastLowgInstrMs >= 30_000L`.
- [ ] The "Gap-magnitude pin (R4 — tentative band, retune on geometric
      upgrade)" comment block is removed; replaced with the one-line
      doctrine reference from §Approach 2.
- [ ] Class docstring's "R4 gap-magnitude pin relaxed" subsection is
      replaced with the spec'd-out "restored to ≥ 30 s" bullet from
      §Approach 3. The four-fix closure narrative for fn-5 is preserved.
- [ ] `grep -rn 'fn-6\b' sim/src/jvmTest/` returns zero matches.
- [ ] `G2CrossAerodromeVfrTest` runs green with the tightened bound. The
      observed gap (in ms) is captured in `## Evidence` for future
      regression diagnosis.
- [ ] `LowgGoldenTest` stays green.
- [ ] Full test suite stays green; `./gradlew detekt` baseline unchanged.
- [ ] `project_g2_status.md` (user auto-memory) updated to reflect that
      R4 is no longer relaxed (one-line edit per §Approach 5).

## Done summary
Tightened G2CrossAerodromeVfrTest's R4 gap-magnitude pin from the relaxed `> 0L` back to the doctrinal `>= 30_000L` with an updated failure message citing ICAO Doc 4444 §10.1 + SERA Section 6; replaced the inline "tentative band, retune on geometric upgrade" comment block (lines 455-478) with a one-paragraph doctrine reference recording the ~374.6 s observed gap; updated the class docstring's "R4 pin relaxed" subsection to "R4 pin restored to >= 30 s" with enumeration of the four production sites where fn-6's kinematic-coordinates lift landed (coords field, factory + fromTestPoint helper, OutsideAerodromeRadius read, typed Meters.fromNauticalMiles helper) and co-citation of OutsideAerodromeRadiusSpec; reworded two fn-6.x comment references in FirewallSensorReadingTest so the grep gate returns zero matches; updated the user's project_g2_status.md memory entry to reflect that R4 is no longer relaxed and that fn-6 has closed the geometric-routing upgrade. All R7 regression suites green; detekt baseline unchanged at 10.
## Evidence
- Commits: cbd8f7c2d8a70338652b77ab25acee752cc715c2
- Tests: ./gradlew :sim:jvmTest --tests xyz.easiersaid.twr.sim.G2CrossAerodromeVfrTest --tests xyz.easiersaid.twr.sim.LowgGoldenTest --tests xyz.easiersaid.twr.sim.FirewallSensorReadingTest, ./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:jvmTest :protocol:jvmTest, ./gradlew detekt
- PRs: