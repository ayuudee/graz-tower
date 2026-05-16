---
title: "Compile-time-enforcement KDoc: distinguish projection-construction-site gate fro"
date: "2026-05-16"
track: knowledge
category: conventions
module: pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/world/PilotAviationWorld.kt
tags: [fn-24, pilot-firewall, kdoc-discipline, structural-enforcement, named-arg-wiring, reflection-parity, impl-review, codex]
applies_when: "Compile-time-enforcement KDoc: distinguish projection-construction-site gate fro"
related_to: [knowledge/conventions/rich-world-domain-2026-05-15]
---

## Problem

When writing R4 ("exhaustive named-argument constructor wiring") into the
pilot-firewall projection KDoc + archive entry, claimed that "future field
additions to `:core/Aerodrome` or `:core/Runway` fail to compile at the
projection's constructor call site." Codex round-1 flagged this as a Minor
introduced finding (100% confidence): named-arg wiring catches missing args
on the **projection** constructor, but adding a field to the core entity
alone does not change anything at `PilotAerodrome(...)` call sites — those
calls still type-check. The real bidirectional core/projection drift gate
is the reflection-driven property-set parity assertion in
`FirewallPilotAviationWorldTest`.

## What Didn't Work

The plan-review-SHIP'd spec carried the same conflation between two distinct
enforcement gates: the R4 named-arg-wiring rationale was written assuming
the wiring covered core-field drift, when in fact it only covers projection-
constructor sloppiness. The plan-review round 1 codex finding called out
exactly this gap and added the R8 property-set parity assertion as the
*real* future-field gate, but the load-bearing KDoc/archive prose
inherited the older, imprecise framing. Reviewer-correct in spec doesn't
mean reviewer-correct in code-comments.

## Solution

Distinguish the two gates explicitly in both the type-file KDoc and the
deferment archive entry:

1. **Projection-construction-site gate** (named-arg wiring): catches sloppy
   `toPilotView` calls when a new projection field is added without being
   wired. Compiler-enforced at `PilotAerodrome(...)` / `PilotRunway(...)`
   / `PilotAviationWorld(...)` constructor call sites.
2. **Bidirectional future-field gate** (R8 reflection parity): catches
   core/projection drift in either direction —
   - `:core` adds field, projection forgets: parity test fails
   - projection accumulates pilot-only field: parity test fails

Each gate is necessary; neither is sufficient. The KDoc now says exactly
that.

## Prevention

When writing KDoc/archive prose for compile-time-enforcement contracts,
**name the failure mode the gate catches** rather than gesturing at "future
changes fail to compile." Concretely: *which* call site fails, *which*
field addition triggers it, *which* direction (core→projection vs
projection→core) is covered. If the contract has multiple gates, each gate
gets its own sentence with its own failure-mode pin. A future reader (or
reviewer) will catch the "future X fails to compile" claim every time when
it's actually "future X fails the property-set parity assertion."

Pre-commit checklist for compile-time-enforcement KDoc:
- Does the claim name the specific call site / construct that fails?
- Is the failure mechanism (compile-time vs test-time) accurate?
- If there are multiple enforcement gates, are they distinguished?
